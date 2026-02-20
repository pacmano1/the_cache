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
    void columnCount_includesAllColumns() {
        assertEquals(9, model.getColumnCount());
        assertEquals("Name", model.getColumnName(0));
        assertEquals("Enabled", model.getColumnName(1));
        assertEquals("Max Size", model.getColumnName(2));
        assertEquals("Eviction (min)", model.getColumnName(3));
        assertEquals("Size", model.getColumnName(4));
        assertEquals("Hit Rate", model.getColumnName(5));
        assertEquals("Evictions", model.getColumnName(6));
        assertEquals("Memory", model.getColumnName(7));
        assertEquals("Lookups", model.getColumnName(8));
    }

    @Test
    void statsColumns_defaultToZeroOrDash() {
        model.setDefinitions(List.of(createDefinition("id-1", "cache-a")));

        assertEquals(0L, model.getValueAt(0, 4));   // Size
        assertEquals("—", model.getValueAt(0, 5));   // Hit Rate
        assertEquals(0L, model.getValueAt(0, 6));     // Evictions
        assertEquals("0 B", model.getValueAt(0, 7));  // Memory
        assertEquals(0L, model.getValueAt(0, 8));      // Lookups
    }

    @Test
    void setData_populatesStatsColumns() {
        var stats1 = createStats("id-1", 42, 0.95, 3, 2048, 100);
        var stats2 = createStats("id-2", 10, 0.5, 0, 1048576, 25);

        model.setData(
                List.of(createDefinition("id-1", "cache-a"), createDefinition("id-2", "cache-b")),
                Map.of("id-1", stats1, "id-2", stats2));

        // cache-a
        assertEquals(42L, model.getValueAt(0, 4));       // Size
        assertEquals("95.0%", model.getValueAt(0, 5));    // Hit Rate
        assertEquals(3L, model.getValueAt(0, 6));          // Evictions
        assertEquals("2.0 KB", model.getValueAt(0, 7));   // Memory
        assertEquals(100L, model.getValueAt(0, 8));        // Lookups

        // cache-b
        assertEquals(10L, model.getValueAt(1, 4));
        assertEquals("50.0%", model.getValueAt(1, 5));
        assertEquals(0L, model.getValueAt(1, 6));
        assertEquals("1.0 MB", model.getValueAt(1, 7));
        assertEquals(25L, model.getValueAt(1, 8));
    }

    @Test
    void setData_withNullStats_defaultsToZero() {
        model.setData(List.of(createDefinition("id-1", "cache-a")), null);

        assertEquals(1, model.getRowCount());
        assertEquals(0L, model.getValueAt(0, 4));
        assertEquals("—", model.getValueAt(0, 5));
        assertEquals(0L, model.getValueAt(0, 8));
    }

    @Test
    void setData_withMissingId_defaultsToZero() {
        var stats = createStats("id-other", 5, 0.8, 1, 512, 99);
        model.setData(List.of(createDefinition("id-1", "cache-a")), Map.of("id-other", stats));

        assertEquals(0L, model.getValueAt(0, 4));
        assertEquals("—", model.getValueAt(0, 5));
        assertEquals("0 B", model.getValueAt(0, 7));
        assertEquals(0L, model.getValueAt(0, 8));
    }

    @Test
    void existingColumns_stillWork() {
        var def = createDefinition("id-1", "test");
        def.setEnabled(true);
        def.setMaxSize(500);
        def.setEvictionDurationMinutes(15);

        model.setDefinitions(List.of(def));

        assertEquals("test", model.getValueAt(0, 0));
        assertEquals("Yes", model.getValueAt(0, 1));
        assertEquals(500L, model.getValueAt(0, 2));
        assertEquals(15L, model.getValueAt(0, 3));
    }

    @Test
    void enabledColumn_rendersAsText() {
        var enabled = createDefinition("id-1", "cache-a");
        enabled.setEnabled(true);
        var disabled = createDefinition("id-2", "cache-b");
        disabled.setEnabled(false);

        model.setDefinitions(List.of(enabled, disabled));

        assertEquals(String.class, model.getColumnClass(1));
        assertEquals("Yes", model.getValueAt(0, 1));
        assertEquals("No", model.getValueAt(1, 1));
    }

    @Test
    void hitRate_nanRendersAsDash() {
        var stats = createStats("id-1", 0, Double.NaN, 0, 0, 0);
        model.setData(List.of(createDefinition("id-1", "cache-a")), Map.of("id-1", stats));

        assertEquals("—", model.getValueAt(0, 5));
    }

    @Test
    void getDefinitionAt_returnsCorrectDefinition() {
        var def = createDefinition("id-1", "cache-a");
        model.setDefinitions(List.of(def));

        assertEquals(def, model.getDefinitionAt(0));
        assertNull(model.getDefinitionAt(-1));
        assertNull(model.getDefinitionAt(1));
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

    private CacheStatistics createStats(String id, long size, double hitRate,
                                        long evictions, long memoryBytes, long requestCount) {
        var stats = new CacheStatistics();
        stats.setCacheDefinitionId(id);
        stats.setSize(size);
        stats.setHitRate(hitRate);
        stats.setEvictionCount(evictions);
        stats.setEstimatedMemoryBytes(memoryBytes);
        stats.setRequestCount(requestCount);
        return stats;
    }
}
