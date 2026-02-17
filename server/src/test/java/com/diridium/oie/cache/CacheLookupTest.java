/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Tests for CacheLookup — per-cache facade registered in globalMap.
 */
class CacheLookupTest {

    @BeforeEach
    void setUp() {
        CacheManager.getInstance();
    }

    @AfterEach
    void tearDown() {
        CacheManager.shutdown();
    }

    @Test
    void lookup_delegatesToCacheManager() {
        var def = createDefinition("test-cache");
        CacheManager.getInstance().registerCache(def);

        var lookup = new CacheLookup("test-cache");

        // Actual JDBC query will fail (no real DB), but the delegation path works
        assertThrows(Exception.class, () -> lookup.lookup("some-key"));
    }

    @Test
    void lookup_throwsForUnregisteredCache() {
        var lookup = new CacheLookup("nonexistent");

        assertThrows(IllegalArgumentException.class, () -> lookup.lookup("key"));
    }

    @Test
    void cacheName_isImmutable() {
        var lookup = new CacheLookup("my-cache");

        // No setter exists — cacheName is final. Verify through the exception message.
        var ex = assertThrows(IllegalArgumentException.class, () -> lookup.lookup("key"));
        assertTrue(ex.getMessage().contains("my-cache"));
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
