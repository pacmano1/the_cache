/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for CacheDefinition model — serialization round-trip, defaults, and validation.
 */
class CacheDefinitionTest {

    @Test
    void newDefinition_hasNullId() {
        var def = new CacheDefinition();
        assertNull(def.getId());
    }

    @Test
    void newDefinition_isEnabledByDefault() {
        var def = new CacheDefinition();
        assertTrue(def.isEnabled());
    }

    @Test
    void settersAndGetters_roundTrip() {
        var def = new CacheDefinition();
        def.setId("abc-123");
        def.setName("facility-config");
        def.setEnabled(false);
        def.setDriver("org.postgresql.Driver");
        def.setUrl("jdbc:postgresql://db.example.com/crosswalk");
        def.setUsername("reader");
        def.setPassword("secret");
        def.setQuery("SELECT config FROM facilities WHERE site_code = ?");
        def.setKeyColumn("site_code");
        def.setValueColumn("config");
        def.setMaxSize(5000);
        def.setEvictionDurationMinutes(120);

        assertEquals("abc-123", def.getId());
        assertEquals("facility-config", def.getName());
        assertFalse(def.isEnabled());
        assertEquals("org.postgresql.Driver", def.getDriver());
        assertEquals("jdbc:postgresql://db.example.com/crosswalk", def.getUrl());
        assertEquals("reader", def.getUsername());
        assertEquals("secret", def.getPassword());
        assertEquals("SELECT config FROM facilities WHERE site_code = ?", def.getQuery());
        assertEquals("site_code", def.getKeyColumn());
        assertEquals("config", def.getValueColumn());
        assertEquals(5000, def.getMaxSize());
        assertEquals(120, def.getEvictionDurationMinutes());
    }

    @Test
    void copyWithoutId_copiesAllFieldsExceptId() {
        var original = new CacheDefinition();
        original.setId("original-id");
        original.setName("facility-config");
        original.setEnabled(false);
        original.setDriver("org.postgresql.Driver");
        original.setUrl("jdbc:postgresql://db.example.com/crosswalk");
        original.setUsername("reader");
        original.setPassword("secret");
        original.setQuery("SELECT config FROM facilities WHERE site_code = ?");
        original.setKeyColumn("site_code");
        original.setValueColumn("config");
        original.setMaxSize(5000);
        original.setEvictionDurationMinutes(120);

        var copy = original.copyWithoutId();

        assertNull(copy.getId());
        assertEquals("facility-config", copy.getName());
        assertFalse(copy.isEnabled());
        assertEquals("org.postgresql.Driver", copy.getDriver());
        assertEquals("jdbc:postgresql://db.example.com/crosswalk", copy.getUrl());
        assertEquals("reader", copy.getUsername());
        assertEquals("secret", copy.getPassword());
        assertEquals("SELECT config FROM facilities WHERE site_code = ?", copy.getQuery());
        assertEquals("site_code", copy.getKeyColumn());
        assertEquals("config", copy.getValueColumn());
        assertEquals(5000, copy.getMaxSize());
        assertEquals(120, copy.getEvictionDurationMinutes());
    }
}
