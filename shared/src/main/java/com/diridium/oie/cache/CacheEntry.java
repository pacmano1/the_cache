/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.io.Serializable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * A single key/value entry from a cache, with the epoch millis when it was loaded.
 */
@XStreamAlias("cacheEntry")
public class CacheEntry implements Serializable {

    private static final long serialVersionUID = 2L;

    private String key;
    private String value;
    private long loadedAtMillis;
    private long hitCount;

    public CacheEntry() {
    }

    public CacheEntry(String key, String value, long loadedAtMillis, long hitCount) {
        this.key = key;
        this.value = value;
        this.loadedAtMillis = loadedAtMillis;
        this.hitCount = hitCount;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public long getLoadedAtMillis() {
        return loadedAtMillis;
    }

    public void setLoadedAtMillis(long loadedAtMillis) {
        this.loadedAtMillis = loadedAtMillis;
    }

    public long getHitCount() {
        return hitCount;
    }

    public void setHitCount(long hitCount) {
        this.hitCount = hitCount;
    }
}
