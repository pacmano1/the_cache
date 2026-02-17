/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for CacheEntry DTO.
 */
class CacheEntryTest {

    @Test
    void settersAndGetters_roundTrip() {
        var entry = new CacheEntry();
        entry.setKey("SITE_001");
        entry.setValue("{\"tz\":\"America/New_York\"}");
        entry.setLoadedAtMillis(1739700000000L);

        assertEquals("SITE_001", entry.getKey());
        assertEquals("{\"tz\":\"America/New_York\"}", entry.getValue());
        assertEquals(1739700000000L, entry.getLoadedAtMillis());
    }

    @Test
    void convenienceConstructor_setsAllFields() {
        var entry = new CacheEntry("KEY", "VALUE", 12345L, 42L);

        assertEquals("KEY", entry.getKey());
        assertEquals("VALUE", entry.getValue());
        assertEquals(12345L, entry.getLoadedAtMillis());
        assertEquals(42L, entry.getHitCount());
    }

    @Test
    void defaultConstructor_fieldsAreDefaults() {
        var entry = new CacheEntry();

        assertNull(entry.getKey());
        assertNull(entry.getValue());
        assertEquals(0L, entry.getLoadedAtMillis());
        assertEquals(0L, entry.getHitCount());
    }

    @Test
    void setHitCount_roundTrip() {
        var entry = new CacheEntry();
        entry.setHitCount(99L);
        assertEquals(99L, entry.getHitCount());
    }
}
