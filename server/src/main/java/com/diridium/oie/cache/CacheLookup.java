/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

/**
 * Facade registered in globalMap so channel code can look up cached values.
 * <p>
 * Usage from channel JavaScript: {@code $g('cache').lookup('facility-config', key)}
 */
public class CacheLookup {

    /**
     * Look up a cached value by cache name and key.
     *
     * @param cacheName the name of the cache definition
     * @param key       the lookup key
     * @return the cached value, or null if not found
     */
    public String lookup(String cacheName, String key) throws Exception {
        return CacheManager.getInstance().getByName(cacheName, key);
    }
}
