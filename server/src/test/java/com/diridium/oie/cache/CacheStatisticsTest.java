/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for CacheStatistics DTO.
 */
class CacheStatisticsTest {

    @Test
    void settersAndGetters_roundTrip() {
        var stats = new CacheStatistics();
        stats.setCacheDefinitionId("def-1");
        stats.setName("my-cache");
        stats.setSize(42);
        stats.setHitCount(100);
        stats.setMissCount(10);
        stats.setLoadSuccessCount(10);
        stats.setLoadExceptionCount(0);
        stats.setHitRate(0.909);
        stats.setEvictionCount(5);
        stats.setRequestCount(110);
        stats.setTotalLoadTimeNanos(50_000_000L);
        stats.setAverageLoadPenaltyNanos(5_000_000.0);

        assertEquals("def-1", stats.getCacheDefinitionId());
        assertEquals("my-cache", stats.getName());
        assertEquals(42, stats.getSize());
        assertEquals(100, stats.getHitCount());
        assertEquals(10, stats.getMissCount());
        assertEquals(10, stats.getLoadSuccessCount());
        assertEquals(0, stats.getLoadExceptionCount());
        assertEquals(0.909, stats.getHitRate(), 0.001);
        assertEquals(5, stats.getEvictionCount());
        assertEquals(110, stats.getRequestCount());
        assertEquals(50_000_000L, stats.getTotalLoadTimeNanos());
        assertEquals(5_000_000.0, stats.getAverageLoadPenaltyNanos(), 0.001);
    }
}
