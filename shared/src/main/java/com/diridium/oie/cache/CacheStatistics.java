/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.io.Serializable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Runtime statistics for a single cache instance.
 */
@XStreamAlias("cacheStatistics")
public class CacheStatistics implements Serializable {

    private static final long serialVersionUID = 3L;

    private String cacheDefinitionId;
    private String name;
    private long size;
    private long hitCount;
    private long missCount;
    private long loadSuccessCount;
    private long loadExceptionCount;
    private double hitRate;
    private long evictionCount;
    private long requestCount;
    private long totalLoadTimeNanos;
    private double averageLoadPenaltyNanos;
    private long estimatedMemoryBytes;

    public CacheStatistics() {
    }

    public String getCacheDefinitionId() {
        return cacheDefinitionId;
    }

    public void setCacheDefinitionId(String cacheDefinitionId) {
        this.cacheDefinitionId = cacheDefinitionId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }

    public long getMissCount() {
        return missCount;
    }

    public void setMissCount(long missCount) {
        this.missCount = missCount;
    }

    public long getLoadSuccessCount() {
        return loadSuccessCount;
    }

    public void setLoadSuccessCount(long loadSuccessCount) {
        this.loadSuccessCount = loadSuccessCount;
    }

    public long getLoadExceptionCount() {
        return loadExceptionCount;
    }

    public void setLoadExceptionCount(long loadExceptionCount) {
        this.loadExceptionCount = loadExceptionCount;
    }

    public double getHitRate() {
        return hitRate;
    }

    public void setHitRate(double hitRate) {
        this.hitRate = hitRate;
    }

    public long getEvictionCount() {
        return evictionCount;
    }

    public void setEvictionCount(long evictionCount) {
        this.evictionCount = evictionCount;
    }

    public long getRequestCount() {
        return requestCount;
    }

    public void setRequestCount(long requestCount) {
        this.requestCount = requestCount;
    }

    public long getTotalLoadTimeNanos() {
        return totalLoadTimeNanos;
    }

    public void setTotalLoadTimeNanos(long totalLoadTimeNanos) {
        this.totalLoadTimeNanos = totalLoadTimeNanos;
    }

    public double getAverageLoadPenaltyNanos() {
        return averageLoadPenaltyNanos;
    }

    public void setAverageLoadPenaltyNanos(double averageLoadPenaltyNanos) {
        this.averageLoadPenaltyNanos = averageLoadPenaltyNanos;
    }

    public long getEstimatedMemoryBytes() {
        return estimatedMemoryBytes;
    }

    public void setEstimatedMemoryBytes(long estimatedMemoryBytes) {
        this.estimatedMemoryBytes = estimatedMemoryBytes;
    }
}
