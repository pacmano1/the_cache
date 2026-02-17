/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.io.Serializable;

import com.thoughtworks.xstream.annotations.XStreamAlias;

/**
 * Defines a named cache backed by an external database query.
 * Stored in the OIE internal database via MyBatis.
 */
@XStreamAlias("cacheDefinition")
public class CacheDefinition implements Serializable {

    private static final long serialVersionUID = 4L;

    private String id;
    private String name;
    private boolean enabled = true;

    // JDBC connection settings for the external database
    private String driver;
    private String url;
    private String username;
    private String password;

    // Query configuration
    private boolean useJavaScript;
    private String query;       // parameterized SQL or JavaScript (when useJavaScript is true)
    private String keyColumn;   // SQL mode only — which result column is the value
    private String valueColumn; // SQL mode only — which result column is the value

    // Guava cache settings
    private long maxSize;
    private long evictionDurationMinutes;

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

    public boolean isUseJavaScript() {
        return useJavaScript;
    }

    public void setUseJavaScript(boolean useJavaScript) {
        this.useJavaScript = useJavaScript;
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
}
