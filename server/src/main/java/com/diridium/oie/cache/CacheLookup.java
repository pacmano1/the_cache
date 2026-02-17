/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

/**
 * Per-cache facade registered in globalMap so channel code can look up cached values.
 * <p>
 * Usage from channel JavaScript: {@code $g('myCacheName').lookup(key)}
 */
public class CacheLookup {

    private final String cacheName;

    public CacheLookup(String cacheName) {
        this.cacheName = cacheName;
    }

    /**
     * Look up a cached value by key.
     *
     * @param key the lookup key
     * @return the cached value, or null if not found
     */
    public String lookup(String key) throws Exception {
        return CacheManager.getInstance().getByName(cacheName, key);
    }
}
