/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.io.Serializable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Defines a named cache backed by an external database query.
 * Stored in the OIE internal database via MyBatis.
 */
@XStreamAlias("cacheDefinition")
public class CacheDefinition implements Serializable {

    private static final long serialVersionUID = 5L;

    private String id;
    private String name;
    private boolean enabled = true;

    // JDBC connection settings for the external database
    private String driver;
    private String url;
    private String username;
    private String password;

    // Query configuration
    private String query;       // parameterized SQL, e.g. SELECT config FROM facilities WHERE site_code = ?
    private String keyColumn;   // which result column is the cache key
    private String valueColumn; // which result column is the cache value

    // Guava cache settings
    private long maxSize;
    private long evictionDurationMinutes;
    private int maxConnections = 5;

    public CacheDefinition() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getKeyColumn() {
        return keyColumn;
    }

    public void setKeyColumn(String keyColumn) {
        this.keyColumn = keyColumn;
    }

    public String getValueColumn() {
        return valueColumn;
    }

    public void setValueColumn(String valueColumn) {
        this.valueColumn = valueColumn;
    }

    public long getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(long maxSize) {
        this.maxSize = maxSize;
    }

    public long getEvictionDurationMinutes() {
        return evictionDurationMinutes;
    }

    public void setEvictionDurationMinutes(long evictionDurationMinutes) {
        this.evictionDurationMinutes = evictionDurationMinutes;
    }

    public int getMaxConnections() {
        return maxConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    /** Returns a copy of this definition with all fields except {@code id} (left null). */
    public CacheDefinition copyWithoutId() {
        var copy = new CacheDefinition();
        copy.setName(name);
        copy.setEnabled(enabled);
        copy.setDriver(driver);
        copy.setUrl(url);
        copy.setUsername(username);
        copy.setPassword(password);
        copy.setQuery(query);
        copy.setKeyColumn(keyColumn);
        copy.setValueColumn(valueColumn);
        copy.setMaxSize(maxSize);
        copy.setEvictionDurationMinutes(evictionDurationMinutes);
        copy.setMaxConnections(maxConnections);
        return copy;
    }
}
