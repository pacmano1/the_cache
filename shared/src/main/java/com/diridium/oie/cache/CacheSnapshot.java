/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Point-in-time snapshot of a cache: statistics plus all current entries.
 */
@XStreamAlias("cacheSnapshot")
public class CacheSnapshot implements Serializable {

    private static final long serialVersionUID = 1L;

    private CacheStatistics statistics;
    private List<CacheEntry> entries;

    public CacheSnapshot() {
    }

    public CacheSnapshot(CacheStatistics statistics, List<CacheEntry> entries) {
        this.statistics = statistics;
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }

    public CacheStatistics getStatistics() {
        return statistics;
    }

    public void setStatistics(CacheStatistics statistics) {
        this.statistics = statistics;
    }

    public List<CacheEntry> getEntries() {
        return entries != null ? Collections.unmodifiableList(entries) : Collections.emptyList();
    }

    public void setEntries(List<CacheEntry> entries) {
        this.entries = entries != null ? new ArrayList<>(entries) : new ArrayList<>();
    }
}
