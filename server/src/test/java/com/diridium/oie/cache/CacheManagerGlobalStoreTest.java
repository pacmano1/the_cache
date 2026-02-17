/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import com.mirth.connect.server.util.GlobalVariableStore;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests that CacheManager correctly registers/unregisters CacheLookup
 * instances in GlobalVariableStore when caches are registered/unregistered.
 */
class CacheManagerGlobalStoreTest {

    private CacheManager cacheManager;
    private GlobalVariableStore globalStore;

    @BeforeEach
    void setUp() {
        cacheManager = CacheManager.getInstance();
        globalStore = GlobalVariableStore.getInstance();
    }

    @AfterEach
    void tearDown() {
        CacheManager.shutdown();
    }

    @Test
    void registerCache_addsLookupToGlobalStore() {
        var def = createDefinition("zip");
        cacheManager.registerCache(def);

        var lookup = globalStore.get("zip");
        assertNotNull(lookup, "GlobalVariableStore should contain entry for 'zip'");
        assertInstanceOf(CacheLookup.class, lookup);
    }

    @Test
    void unregisterCache_removesLookupFromGlobalStore() {
        var def = createDefinition("zip");
        cacheManager.registerCache(def);
        cacheManager.unregisterCache(def.getId());

        assertNull(globalStore.get("zip"), "GlobalVariableStore should not contain entry after unregister");
    }

    @Test
    void shutdown_removesAllLookupsFromGlobalStore() {
        cacheManager.registerCache(createDefinition("cache-a"));
        cacheManager.registerCache(createDefinition("cache-b"));

        CacheManager.shutdown();

        assertNull(globalStore.get("cache-a"));
        assertNull(globalStore.get("cache-b"));
    }

    @Test
    void registerCache_replacesExistingLookup() {
        var def = createDefinition("zip");
        cacheManager.registerCache(def);
        var first = globalStore.get("zip");

        cacheManager.registerCache(def);
        var second = globalStore.get("zip");

        assertNotNull(second);
        assertNotSame(first, second, "Re-registration should create a new CacheLookup instance");
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
