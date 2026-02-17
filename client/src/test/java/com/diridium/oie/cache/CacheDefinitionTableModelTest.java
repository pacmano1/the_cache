/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CacheDefinitionTableModelTest {

    private CacheDefinitionTableModel model;

    @BeforeEach
    void setUp() {
        model = new CacheDefinitionTableModel();
    }

    @Test
    void columnCount_includesLookupsColumn() {
        assertEquals(6, model.getColumnCount());
        assertEquals("Memory", model.getColumnName(4));
        assertEquals("Lookups", model.getColumnName(5));
        assertEquals(Long.class, model.getColumnClass(5));
    }

    @Test
    void lookupsColumn_defaultsToZero() {
        model.setDefinitions(List.of(createDefinition("id-1", "cache-a")));

        assertEquals(0L, model.getValueAt(0, 5));
    }

    @Test
    void setLookupCounts_updatesLookupsColumn() {
        model.setDefinitions(List.of(createDefinition("id-1", "cache-a")));
        model.setLookupCounts(Map.of("id-1", 42L));

        assertEquals(42L, model.getValueAt(0, 5));
    }

    @Test
    void setData_setsBothDefinitionsAndCounts() {
        var defs = List.of(
                createDefinition("id-1", "cache-a"),
                createDefinition("id-2", "cache-b"));
        var counts = Map.of("id-1", 10L, "id-2", 25L);
        var memory = Map.of("id-1", 2048L, "id-2", 1048576L);

        model.setData(defs, counts, memory);

        assertEquals(2, model.getRowCount());
        assertEquals(10L, model.getValueAt(0, 5));
        assertEquals(25L, model.getValueAt(1, 5));
        assertEquals("2.0 KB", model.getValueAt(0, 4));
        assertEquals("1.0 MB", model.getValueAt(1, 4));
    }

    @Test
    void setData_withNullCounts_defaultsToZero() {
        model.setData(List.of(createDefinition("id-1", "cache-a")), null, null);

        assertEquals(1, model.getRowCount());
        assertEquals(0L, model.getValueAt(0, 5));
        assertEquals("0 B", model.getValueAt(0, 4));
    }

    @Test
    void setData_withMissingId_defaultsToZero() {
        model.setData(
                List.of(createDefinition("id-1", "cache-a")),
                Map.of("id-other", 99L),
                Map.of("id-other", 512L));

        assertEquals(0L, model.getValueAt(0, 5));
        assertEquals("0 B", model.getValueAt(0, 4));
    }

    @Test
    void existingColumns_stillWork() {
        var def = createDefinition("id-1", "test");
        def.setEnabled(true);
        def.setMaxSize(500);
        def.setEvictionDurationMinutes(15);

        model.setDefinitions(List.of(def));

        assertEquals("test", model.getValueAt(0, 0));
        assertEquals(true, model.getValueAt(0, 1));
        assertEquals(500L, model.getValueAt(0, 2));
        assertEquals(15L, model.getValueAt(0, 3));
    }

    @Test
    void setDefinitions_clearsPreviousLookupCounts() {
        model.setData(
                List.of(createDefinition("id-1", "cache-a")),
                Map.of("id-1", 50L),
                Map.of("id-1", 1024L));

        // setDefinitions doesn't touch lookupCounts — existing counts remain
        model.setDefinitions(List.of(createDefinition("id-1", "cache-a")));
        assertEquals(50L, model.getValueAt(0, 5));
    }

    private CacheDefinition createDefinition(String id, String name) {
        var def = new CacheDefinition();
        def.setId(id);
        def.setName(name);
        def.setEnabled(true);
        def.setMaxSize(1000);
        def.setEvictionDurationMinutes(30);
        def.setDriver("org.postgresql.Driver");
        return def;
    }
}
