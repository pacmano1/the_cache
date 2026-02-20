/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

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
                new CacheEntry("k1", "v1", 1000L, 0L),
                new CacheEntry("k2", "v2", 2000L, 0L));

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
        var entries = List.of(new CacheEntry("k", "v", 500L, 0L));

        var snapshot = new CacheSnapshot(stats, entries);

        assertSame(stats, snapshot.getStatistics());
        assertEquals(1, snapshot.getEntries().size());
        assertEquals(0, snapshot.getTotalEntries());
        assertEquals(0, snapshot.getMatchedEntries());
    }

    @Test
    void fullConstructor_setsCounts() {
        var stats = new CacheStatistics();
        var entries = List.of(new CacheEntry("k", "v", 500L, 0L));

        var snapshot = new CacheSnapshot(stats, entries, 5000, 200);

        assertEquals(1, snapshot.getEntries().size());
        assertEquals(5000, snapshot.getTotalEntries());
        assertEquals(200, snapshot.getMatchedEntries());
    }

    @Test
    void totalAndMatchedEntries_settersRoundTrip() {
        var snapshot = new CacheSnapshot();
        snapshot.setTotalEntries(42000);
        snapshot.setMatchedEntries(1500);

        assertEquals(42000, snapshot.getTotalEntries());
        assertEquals(1500, snapshot.getMatchedEntries());
    }

    @Test
    void defaultConstructor_entriesAreEmpty() {
        var snapshot = new CacheSnapshot();

        assertNull(snapshot.getStatistics());
        assertNotNull(snapshot.getEntries());
        assertTrue(snapshot.getEntries().isEmpty());
    }

    @Test
    void getEntries_returnsUnmodifiableList() {
        var entries = new java.util.ArrayList<>(List.of(
                new CacheEntry("k1", "v1", 1000L, 0L)));
        var snapshot = new CacheSnapshot(new CacheStatistics(), entries);

        assertThrows(UnsupportedOperationException.class,
                () -> snapshot.getEntries().add(new CacheEntry("k2", "v2", 2000L, 0L)));
    }

    @Test
    void setEntries_makesDefensiveCopy() {
        var entries = new java.util.ArrayList<>(List.of(
                new CacheEntry("k1", "v1", 1000L, 0L)));
        var snapshot = new CacheSnapshot();
        snapshot.setEntries(entries);

        entries.add(new CacheEntry("k2", "v2", 2000L, 0L));
        assertEquals(1, snapshot.getEntries().size(), "Modifying original list should not affect snapshot");
    }
}
