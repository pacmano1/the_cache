/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Guava LoadingCache instances keyed by cache definition ID.
 * Each cache lazily loads values from an external database via JDBC.
 */
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private static CacheManager instance;

    private final ConcurrentHashMap<String, LoadingCache<String, String>> caches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> loadTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nameToId = new ConcurrentHashMap<>();

    public static synchronized CacheManager getInstance() {
        if (instance == null) {
            instance = new CacheManager();
        }
        return instance;
    }

    public static synchronized void shutdown() {
        if (instance != null) {
            instance.invalidateAll();
            instance = null;
        }
    }

    /**
     * Build (or rebuild) a Guava LoadingCache for the given definition.
     */
    public void registerCache(CacheDefinition definition) {
        var timestamps = new ConcurrentHashMap<String, Long>();
        var loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                var result = queryExternalDatabase(definition, key);
                timestamps.put(key, System.currentTimeMillis());
                return result;
            }
        };

        var builder = CacheBuilder.newBuilder()
                .recordStats();

        if (definition.getMaxSize() > 0) {
            builder.maximumSize(definition.getMaxSize());
        }
        if (definition.getEvictionDurationMinutes() > 0) {
            builder.expireAfterAccess(definition.getEvictionDurationMinutes(), TimeUnit.MINUTES);
        }

        var cache = builder.build(loader);
        caches.put(definition.getId(), cache);
        definitions.put(definition.getId(), definition);
        loadTimestamps.put(definition.getId(), timestamps);
        nameToId.put(definition.getName(), definition.getId());
        log.info("Registered cache '{}'", definition.getName());
    }

    /**
     * Remove a cache and its definition.
     */
    public void unregisterCache(String definitionId) {
        var def = definitions.remove(definitionId);
        if (def != null) {
            nameToId.remove(def.getName());
        }
        var cache = caches.remove(definitionId);
        if (cache != null) {
            cache.invalidateAll();
        }
        loadTimestamps.remove(definitionId);
    }

    /**
     * Look up a value by cache name. Resolves name → ID, then delegates to {@link #get}.
     */
    public String getByName(String name, String key) throws Exception {
        var id = nameToId.get(name);
        if (id == null) {
            throw new IllegalArgumentException("No cache registered with name: " + name);
        }
        return get(id, key);
    }

    /**
     * Look up a value from the cache. Returns null if the key produces no result.
     */
    public String get(String definitionId, String key) throws Exception {
        var cache = caches.get(definitionId);
        if (cache == null) {
            throw new IllegalArgumentException("No cache registered for definition: " + definitionId);
        }
        try {
            return cache.get(key);
        } catch (CacheLoader.InvalidCacheLoadException e) {
            // Guava throws this when CacheLoader returns null — means key not found
            return null;
        }
    }

    /**
     * Refresh all entries currently in the cache (does NOT load new keys).
     */
    public void refresh(String definitionId) {
        var cache = caches.get(definitionId);
        if (cache == null) {
            throw new IllegalArgumentException("No cache registered for definition: " + definitionId);
        }
        for (var key : cache.asMap().keySet()) {
            cache.refresh(key);
        }
        log.info("Refreshed cache '{}'", definitions.get(definitionId).getName());
    }

    /**
     * Get runtime statistics for all registered caches.
     */
    public List<CacheStatistics> getAllStatistics() {
        var result = new ArrayList<CacheStatistics>();
        for (var id : definitions.keySet()) {
            var stats = getStatistics(id);
            if (stats != null) {
                result.add(stats);
            }
        }
        return result;
    }

    /**
     * Get runtime statistics for a cache.
     */
    public CacheStatistics getStatistics(String definitionId) {
        var cache = caches.get(definitionId);
        var def = definitions.get(definitionId);
        if (cache == null || def == null) {
            return null;
        }

        CacheStats stats = cache.stats();
        var result = new CacheStatistics();
        result.setCacheDefinitionId(definitionId);
        result.setName(def.getName());
        result.setSize(cache.size());
        result.setHitCount(stats.hitCount());
        result.setMissCount(stats.missCount());
        result.setLoadSuccessCount(stats.loadSuccessCount());
        result.setLoadExceptionCount(stats.loadExceptionCount());
        result.setHitRate(stats.hitRate());
        result.setEvictionCount(stats.evictionCount());
        result.setRequestCount(stats.requestCount());
        result.setTotalLoadTimeNanos(stats.totalLoadTime());
        result.setAverageLoadPenaltyNanos(stats.averageLoadPenalty());
        return result;
    }

    /**
     * Get a point-in-time snapshot of a cache: statistics plus all current entries.
     */
    public CacheSnapshot getSnapshot(String definitionId) {
        var stats = getStatistics(definitionId);
        if (stats == null) {
            return null;
        }

        var cache = caches.get(definitionId);
        var timestamps = loadTimestamps.getOrDefault(definitionId, new ConcurrentHashMap<>());
        var entries = new ArrayList<CacheEntry>();

        for (var entry : cache.asMap().entrySet()) {
            var loadedAt = timestamps.getOrDefault(entry.getKey(), 0L);
            entries.add(new CacheEntry(entry.getKey(), entry.getValue(), loadedAt));
        }

        return new CacheSnapshot(stats, entries);
    }

    /**
     * Test the parameterized query with a sample key and return a user-friendly result.
     */
    public String testQuery(CacheDefinition definition, String sampleKey) {
        try {
            Class.forName(definition.getDriver());
        } catch (ClassNotFoundException e) {
            return "Driver not found: " + definition.getDriver();
        }

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(
                    definition.getUrl(), definition.getUsername(), definition.getPassword());
            conn.setAutoCommit(true);

            stmt = conn.prepareStatement(definition.getQuery());
            stmt.setString(1, sampleKey);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return "Result: " + rs.getString(definition.getValueColumn());
            }
            return "No rows returned for key: " + sampleKey;
        } catch (Exception e) {
            return "Query failed: " + e.getMessage();
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (Exception e) { log.debug("Failed to close ResultSet", e); }
            }
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) { log.debug("Failed to close PreparedStatement", e); }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { log.error("Failed to close database connection", e); }
            }
        }
    }

    /**
     * Test the JDBC connection for a cache definition.
     */
    public String testConnection(CacheDefinition definition) {
        try {
            Class.forName(definition.getDriver());
        } catch (ClassNotFoundException e) {
            return "Driver not found: " + definition.getDriver();
        }

        try (Connection conn = DriverManager.getConnection(
                definition.getUrl(), definition.getUsername(), definition.getPassword())) {
            if (conn.isValid(10)) {
                return "Connection successful";
            } else {
                return "Connection returned but is not valid";
            }
        } catch (Exception e) {
            return "Connection failed: " + e.getMessage();
        }
    }

    private void invalidateAll() {
        caches.values().forEach(LoadingCache::invalidateAll);
        caches.clear();
        definitions.clear();
        loadTimestamps.clear();
        nameToId.clear();
    }

    /**
     * Execute the parameterized query against the external database.
     * Opens a connection, runs the query, closes the connection.
     * Returns null if no row matches (Guava will translate this to InvalidCacheLoadException).
     */
    private String queryExternalDatabase(CacheDefinition definition, String key) throws Exception {
        Class.forName(definition.getDriver());

        Connection conn = null;
        PreparedStatement stmt = null;
        ResultSet rs = null;
        try {
            conn = DriverManager.getConnection(
                    definition.getUrl(), definition.getUsername(), definition.getPassword());
            conn.setAutoCommit(true);

            stmt = conn.prepareStatement(definition.getQuery());
            stmt.setString(1, key);

            rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString(definition.getValueColumn());
            }
            return null;
        } finally {
            if (rs != null) {
                try { rs.close(); } catch (Exception e) { log.debug("Failed to close ResultSet", e); }
            }
            if (stmt != null) {
                try { stmt.close(); } catch (Exception e) { log.debug("Failed to close PreparedStatement", e); }
            }
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { log.error("Failed to close database connection", e); }
            }
        }
    }
}
