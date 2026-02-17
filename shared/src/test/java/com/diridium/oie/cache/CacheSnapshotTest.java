/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Tests for CacheSnapshot DTO.
 */
class CacheSnapshotTest {

    @Test
    void settersAndGetters_roundTrip() {
        var stats = new CacheStatistics();
        stats.setSize(2);

        var entries = List.of(
                new CacheEntry("k1", "v1", 1000L),
                new CacheEntry("k2", "v2", 2000L));

        var snapshot = new CacheSnapshot();
        snapshot.setStatistics(stats);
        snapshot.setEntries(entries);

        assertSame(stats, snapshot.getStatistics());
        assertEquals(2, snapshot.getEntries().size());
        assertEquals("k1", snapshot.getEntries().get(0).getKey());
    }

    @Test
    void convenienceConstructor_setsAllFields() {
        var stats = new CacheStatistics();
        var entries = List.of(new CacheEntry("k", "v", 500L));

        var snapshot = new CacheSnapshot(stats, entries);

        assertSame(stats, snapshot.getStatistics());
        assertEquals(1, snapshot.getEntries().size());
    }

    @Test
    void defaultConstructor_fieldsAreNull() {
        var snapshot = new CacheSnapshot();

        assertNull(snapshot.getStatistics());
        assertNull(snapshot.getEntries());
    }
}
