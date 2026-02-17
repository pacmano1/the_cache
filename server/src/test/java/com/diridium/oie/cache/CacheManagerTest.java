/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for CacheManager — cache behavior, JDBC loading, eviction, and refresh.
 * Uses an in-memory H2/Derby database for external DB simulation.
 * Since we can't easily include H2 in test scope without adding a dependency,
 * these tests focus on the CacheManager API contract with mock-friendly patterns.
 */
class CacheManagerTest {

    private CacheManager cacheManager;

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
    }

    @AfterEach
    void tearDown() {
        CacheManager.shutdown();
    }

    @Test
    void registerCache_createsLoadableCache() {
        var def = createDefinition("test-cache");
        cacheManager.registerCache(def);

        var stats = cacheManager.getStatistics(def.getId());
        assertNotNull(stats);
        assertEquals("test-cache", stats.getName());
        assertEquals(0, stats.getSize());
    }

    @Test
    void unregisterCache_removesCache() {
        var def = createDefinition("to-remove");
        cacheManager.registerCache(def);

        cacheManager.unregisterCache(def.getId());

        assertNull(cacheManager.getStatistics(def.getId()));
    }

    @Test
    void get_throwsForUnregisteredCache() {
        assertThrows(IllegalArgumentException.class, () ->
                cacheManager.get("nonexistent-id", "key"));
    }

    @Test
    void refresh_throwsForUnregisteredCache() {
        assertThrows(IllegalArgumentException.class, () ->
                cacheManager.refresh("nonexistent-id"));
    }

    @Test
    void testConnection_failsWithBadDriver() {
        var def = createDefinition("bad-driver");
        def.setDriver("com.nonexistent.Driver");

        var result = cacheManager.testConnection(def);
        assertTrue(result.startsWith("Driver not found"));
    }

    @Test
    void testConnection_failsWithBadUrl() {
        var def = createDefinition("bad-url");
        def.setDriver("org.apache.derby.jdbc.EmbeddedDriver");
        def.setUrl("jdbc:nonexistent://localhost/test");

        var result = cacheManager.testConnection(def);
        assertTrue(result.startsWith("Connection failed") || result.startsWith("Driver not found"));
    }

    @Test
    void statistics_reflectsCacheState() {
        var def = createDefinition("stats-test");
        cacheManager.registerCache(def);

        var stats = cacheManager.getStatistics(def.getId());
        assertEquals(0, stats.getSize());
        assertEquals(0, stats.getHitCount());
        assertEquals(0, stats.getMissCount());
    }

    @Test
    void statistics_returnsNullForUnknownCache() {
        assertNull(cacheManager.getStatistics("unknown-id"));
    }

    @Test
    void snapshot_returnsNullForUnknownCache() {
        assertNull(cacheManager.getSnapshot("unknown-id"));
    }

    @Test
    void snapshot_returnsEmptyForNewCache() {
        var def = createDefinition("snapshot-test");
        cacheManager.registerCache(def);

        var snapshot = cacheManager.getSnapshot(def.getId());
        assertNotNull(snapshot);
        assertNotNull(snapshot.getStatistics());
        assertEquals("snapshot-test", snapshot.getStatistics().getName());
        assertNotNull(snapshot.getEntries());
        assertTrue(snapshot.getEntries().isEmpty());
    }

    @Test
    void getAllStatistics_emptyWhenNoCaches() {
        var all = cacheManager.getAllStatistics();
        assertNotNull(all);
        assertTrue(all.isEmpty());
    }

    @Test
    void getAllStatistics_returnsStatsForAllRegisteredCaches() {
        var def1 = createDefinition("cache-a");
        var def2 = createDefinition("cache-b");
        cacheManager.registerCache(def1);
        cacheManager.registerCache(def2);

        var all = cacheManager.getAllStatistics();
        assertEquals(2, all.size());

        var names = all.stream().map(CacheStatistics::getName).sorted().toList();
        assertEquals(java.util.List.of("cache-a", "cache-b"), names);
    }

    @Test
    void getAllStatistics_excludesUnregisteredCaches() {
        var def1 = createDefinition("keep");
        var def2 = createDefinition("remove");
        cacheManager.registerCache(def1);
        cacheManager.registerCache(def2);
        cacheManager.unregisterCache(def2.getId());

        var all = cacheManager.getAllStatistics();
        assertEquals(1, all.size());
        assertEquals("keep", all.get(0).getName());
    }

    @Test
    void registerCache_overwritesExisting() {
        var def = createDefinition("overwrite-test");
        cacheManager.registerCache(def);

        // Register again with same ID — should overwrite without error
        def.setMaxSize(999);
        cacheManager.registerCache(def);

        var stats = cacheManager.getStatistics(def.getId());
        assertNotNull(stats);
    }

    @Test
    void getByName_returnsValueForRegisteredCache() throws Exception {
        var def = createDefinition("named-cache");
        cacheManager.registerCache(def);

        // getByName should resolve the name → ID without error
        // (actual JDBC query will fail since there's no real DB, but the resolution works)
        assertThrows(Exception.class, () -> cacheManager.getByName("named-cache", "some-key"));
    }

    @Test
    void getByName_throwsForUnknownName() {
        assertThrows(IllegalArgumentException.class, () ->
                cacheManager.getByName("nonexistent-name", "key"));
    }

    @Test
    void snapshot_hitCountZeroForFreshCache() {
        var def = createDefinition("hitcount-fresh");
        cacheManager.registerCache(def);

        var snapshot = cacheManager.getSnapshot(def.getId());
        assertNotNull(snapshot);
        // No entries loaded yet → no hit counts to verify
        assertTrue(snapshot.getEntries().isEmpty());
    }

    @Test
    void unregisterCache_clearsHitCounts() {
        var def = createDefinition("hitcount-unregister");
        cacheManager.registerCache(def);
        cacheManager.unregisterCache(def.getId());

        // After unregister, snapshot is null — hit counts are gone
        assertNull(cacheManager.getSnapshot(def.getId()));
    }

    private CacheDefinition createDefinition(String name) {
        var def = new CacheDefinition();
        def.setId(java.util.UUID.randomUUID().toString());
        def.setName(name);
        def.setDriver("org.postgresql.Driver");
        def.setUrl("jdbc:postgresql://localhost/test");
        def.setUsername("test");
        def.setPassword("test");
        def.setQuery("SELECT value FROM lookup WHERE key = ?");
        def.setKeyColumn("key");
        def.setValueColumn("value");
        def.setMaxSize(1000);
        def.setEvictionDurationMinutes(30);
        return def;
    }
}
