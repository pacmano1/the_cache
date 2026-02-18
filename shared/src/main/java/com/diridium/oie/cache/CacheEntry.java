/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.io.Serializable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * A single key/value entry from a cache, with the epoch millis when it was loaded.
 */
@XStreamAlias("cacheEntry")
public class CacheEntry implements Serializable {

    private static final long serialVersionUID = 3L;

    private String key;
    private String value;
    private long loadedAtMillis;
    private long accessCount;

    public CacheEntry() {
    }

    public CacheEntry(String key, String value, long loadedAtMillis, long accessCount) {
        this.key = key;
        this.value = value;
        this.loadedAtMillis = loadedAtMillis;
        this.accessCount = accessCount;
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

    public long getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(long accessCount) {
        this.accessCount = accessCount;
    }
}
