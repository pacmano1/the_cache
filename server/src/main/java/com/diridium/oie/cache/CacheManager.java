/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.CacheStats;
import com.google.common.cache.LoadingCache;
import com.mirth.connect.server.util.GlobalVariableStore;
import com.zaxxer.hikari.HikariDataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Manages Guava LoadingCache instances keyed by cache definition ID.
 * Each cache lazily loads values from an external database via JDBC.
 */
public class CacheManager {

    private static final Logger log = LoggerFactory.getLogger(CacheManager.class);

    private static volatile CacheManager instance;

    private final ConcurrentHashMap<String, LoadingCache<String, String>> caches = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, CacheDefinition> definitions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Long>> loadTimestamps = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, LongAdder>> accessCounts = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> nameToId = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HikariDataSource> connectionPools = new ConcurrentHashMap<>();

    public static CacheManager getInstance() {
        var result = instance;
        if (result == null) {
            synchronized (CacheManager.class) {
                result = instance;
                if (result == null) {
                    instance = result = new CacheManager();
                }
            }
        }
        return result;
    }

    public static void shutdown() {
        synchronized (CacheManager.class) {
            if (instance != null) {
                instance.invalidateAll();
                instance = null;
            }
        }
    }

    /**
     * Build (or rebuild) a Guava LoadingCache for the given definition.
     * Takes a defensive copy so the caller cannot mutate the stored definition.
     * Constructs the cache and connection pool before touching shared state,
     * so a pool creation failure leaves the maps unchanged.
     */
    public void registerCache(CacheDefinition definition) {
        var def = definition.copy();

        var timestamps = new ConcurrentHashMap<String, Long>();
        var loader = new CacheLoader<String, String>() {
            @Override
            public String load(String key) throws Exception {
                var result = queryExternalDatabase(def, key);
                timestamps.put(key, System.currentTimeMillis());
                return result;
            }
        };

        var builder = CacheBuilder.newBuilder()
                .recordStats();

        if (def.getMaxSize() > 0) {
            builder.maximumSize(def.getMaxSize());
        }
        if (def.getEvictionDurationMinutes() > 0) {
            builder.expireAfterAccess(def.getEvictionDurationMinutes(), TimeUnit.MINUTES);
        }

        var cache = builder.build(loader);

        // Create pool before mutating shared state — this is the only call that can throw
        var pool = createPool(def);

        // All local construction succeeded — now atomically update shared maps
        closePoolQuietly(def.getId());
        caches.put(def.getId(), cache);
        definitions.put(def.getId(), def);
        loadTimestamps.put(def.getId(), timestamps);
        accessCounts.put(def.getId(), new ConcurrentHashMap<>());
        nameToId.put(def.getName(), def.getId());
        connectionPools.put(def.getId(), pool);
        GlobalVariableStore.getInstance().put(def.getName(), new CacheLookup(def.getName()));
        log.info("Registered cache '{}'", def.getName());
    }

    /**
     * Remove a cache and its definition.
     */
    public void unregisterCache(String definitionId) {
        var def = definitions.remove(definitionId);
        if (def != null) {
            nameToId.remove(def.getName());
            GlobalVariableStore.getInstance().remove(def.getName());
        }
        var cache = caches.remove(definitionId);
        if (cache != null) {
            cache.invalidateAll();
        }
        loadTimestamps.remove(definitionId);
        accessCounts.remove(definitionId);
        closePoolQuietly(definitionId);
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
            var result = cache.get(key);
            return result;
        } catch (CacheLoader.InvalidCacheLoadException e) {
            // Guava throws this when CacheLoader returns null — means key not found
            return null;
        } finally {
            incrementAccessCount(definitionId, key);
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
        var def = definitions.get(definitionId);
        if (def != null) {
            log.info("Refreshed cache '{}'", def.getName());
        }
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

        // Estimate memory: Java String uses ~2 bytes per char + object overhead
        long memoryEstimate = 0;
        for (var entry : cache.asMap().entrySet()) {
            var k = entry.getKey();
            var v = entry.getValue();
            memoryEstimate += (k != null ? k.length() * 2L : 0)
                    + (v != null ? v.length() * 2L : 0);
        }
        result.setEstimatedMemoryBytes(memoryEstimate);

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
        var counts = accessCounts.getOrDefault(definitionId, new ConcurrentHashMap<>());
        var entries = new ArrayList<CacheEntry>();

        for (var entry : cache.asMap().entrySet()) {
            var loadedAt = timestamps.getOrDefault(entry.getKey(), 0L);
            var adder = counts.get(entry.getKey());
            var accessCount = adder != null ? adder.sum() : 0L;
            entries.add(new CacheEntry(entry.getKey(), entry.getValue(), loadedAt, accessCount));
        }

        return new CacheSnapshot(stats, entries);
    }

    /**
     * Test the parameterized query with a sample key and return a user-friendly result.
     */
    public String testQuery(CacheDefinition definition, String sampleKey) {
        var driverError = loadDriver(definition.getDriver());
        if (driverError != null) return driverError;

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

            // Validate that configured columns exist in the result set
            var meta = rs.getMetaData();
            var columnNames = new ArrayList<String>();
            for (int i = 1; i <= meta.getColumnCount(); i++) {
                columnNames.add(meta.getColumnLabel(i));
            }

            var missing = new ArrayList<String>();
            if (definition.getKeyColumn() != null && !definition.getKeyColumn().isEmpty()
                    && columnNames.stream().noneMatch(c -> c.equalsIgnoreCase(definition.getKeyColumn()))) {
                missing.add("Key Column '" + definition.getKeyColumn() + "'");
            }
            if (definition.getValueColumn() != null && !definition.getValueColumn().isEmpty()
                    && columnNames.stream().noneMatch(c -> c.equalsIgnoreCase(definition.getValueColumn()))) {
                missing.add("Value Column '" + definition.getValueColumn() + "'");
            }
            if (!missing.isEmpty()) {
                return String.join(" and ", missing)
                        + " not found in result set. Available columns: "
                        + String.join(", ", columnNames);
            }

            var actualKeyCol = findColumnName(columnNames, definition.getKeyColumn());
            var actualValCol = findColumnName(columnNames, definition.getValueColumn());

            if (rs.next()) {
                return "Key: " + rs.getString(actualKeyCol)
                        + "\nValue: " + rs.getString(actualValCol);
            }
            return "No rows returned for key: " + sampleKey;
        } catch (Exception e) {
            return "Query failed: " + e.getMessage();
        } finally {
            closeStatementAndResultSet(rs, stmt);
            if (conn != null) {
                try { conn.close(); } catch (Exception e) { log.error("Failed to close database connection", e); }
            }
        }
    }

    /**
     * Test the JDBC connection for a cache definition.
     */
    public String testConnection(CacheDefinition definition) {
        var driverError = loadDriver(definition.getDriver());
        if (driverError != null) return driverError;

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
        connectionPools.keySet().forEach(this::closePoolQuietly);
        caches.values().forEach(LoadingCache::invalidateAll);
        caches.clear();
        definitions.clear();
        loadTimestamps.clear();
        accessCounts.clear();
        var globalStore = GlobalVariableStore.getInstance();
        nameToId.keySet().forEach(globalStore::remove);
        nameToId.clear();
    }

    private void incrementAccessCount(String definitionId, String key) {
        var counters = accessCounts.get(definitionId);
        if (counters != null) {
            counters.computeIfAbsent(key, k -> new LongAdder()).increment();
        }
    }

    /**
     * Execute the parameterized query against the external database.
     * Borrows a connection from the per-cache HikariCP pool and returns it after.
     * Returns null if no row matches (Guava will translate this to InvalidCacheLoadException).
     */
    private String queryExternalDatabase(CacheDefinition definition, String key) throws Exception {
        var pool = connectionPools.get(definition.getId());
        if (pool == null) {
            throw new IllegalStateException("No connection pool for cache: " + definition.getName());
        }

        try (var conn = pool.getConnection()) {
            PreparedStatement stmt = null;
            ResultSet rs = null;
            try {
                stmt = conn.prepareStatement(definition.getQuery());
                stmt.setString(1, key);

                rs = stmt.executeQuery();
                if (rs.next()) {
                    // Resolve column name case-insensitively (some DBs return uppercase labels)
                    var meta = rs.getMetaData();
                    var columnNames = new ArrayList<String>();
                    for (int i = 1; i <= meta.getColumnCount(); i++) {
                        columnNames.add(meta.getColumnLabel(i));
                    }
                    var actualValCol = findColumnName(columnNames, definition.getValueColumn());
                    return rs.getString(actualValCol);
                }
                return null;
            } finally {
                closeStatementAndResultSet(rs, stmt);
            }
        }
    }

    private static HikariDataSource createPool(CacheDefinition definition) {
        // Pre-register the driver with DriverManager (needed for non-JDBC4 drivers)
        try {
            Class.forName(definition.getDriver());
        } catch (ClassNotFoundException e) {
            log.warn("JDBC driver class '{}' not found for cache '{}' — will rely on URL auto-detection",
                    definition.getDriver(), definition.getName());
        }

        var maxConn = definition.getMaxConnections() > 0 ? definition.getMaxConnections() : 5;

        // Use no-arg constructor for lazy initialization — pool starts on first getConnection(),
        // not at construction time. This avoids eagerly validating the driver/URL at registration.
        var ds = new HikariDataSource();
        ds.setJdbcUrl(definition.getUrl());
        ds.setUsername(definition.getUsername());
        ds.setPassword(definition.getPassword());
        ds.setMaximumPoolSize(maxConn);
        ds.setMinimumIdle(0);
        ds.setAutoCommit(true);
        ds.setPoolName("oie-cache-" + definition.getName());
        return ds;
    }

    private void closePoolQuietly(String definitionId) {
        var pool = connectionPools.remove(definitionId);
        if (pool != null) {
            try {
                pool.close();
            } catch (Exception e) {
                log.debug("Failed to close connection pool for cache '{}'", definitionId, e);
            }
        }
    }

    private static String loadDriver(String driverClassName) {
        try {
            Class.forName(driverClassName);
            return null;
        } catch (ClassNotFoundException e) {
            return "Driver not found: " + driverClassName;
        }
    }

    private static String findColumnName(List<String> availableColumns, String requestedColumn) {
        return availableColumns.stream()
                .filter(c -> c.equalsIgnoreCase(requestedColumn))
                .findFirst().orElse(requestedColumn);
    }

    private void closeStatementAndResultSet(ResultSet rs, PreparedStatement stmt) {
        if (rs != null) {
            try { rs.close(); } catch (Exception e) { log.debug("Failed to close ResultSet", e); }
        }
        if (stmt != null) {
            try { stmt.close(); } catch (Exception e) { log.debug("Failed to close PreparedStatement", e); }
        }
    }

}
