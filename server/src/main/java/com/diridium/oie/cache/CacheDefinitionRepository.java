/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import com.mirth.connect.server.util.SqlConfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data access layer for cache definitions stored in the OIE internal database via MyBatis.
 */
public class CacheDefinitionRepository {

    private static final Logger log = LoggerFactory.getLogger(CacheDefinitionRepository.class);
    private static final String NAMESPACE = "CacheDefinition";

    private static CacheDefinitionRepository instance;

    public static synchronized void init() {
        if (instance == null) {
            instance = new CacheDefinitionRepository();
        }
    }

    public static CacheDefinitionRepository getInstance() {
        if (instance == null) {
            throw new IllegalStateException("CacheDefinitionRepository not initialized");
        }
        return instance;
    }

    public static synchronized void close() {
        instance = null;
    }

    private static String stmt(String id) {
        return NAMESPACE + "." + id;
    }

    public CacheDefinition create(CacheDefinition def) {
        if (def.getId() == null) {
            def.setId(UUID.randomUUID().toString());
        }
        var params = toParams(def);
        params.put("createdAt", new Timestamp(System.currentTimeMillis()));
        params.put("updatedAt", new Timestamp(System.currentTimeMillis()));
        SqlConfig.getInstance().getSqlSessionManager().insert(stmt("insert"), params);
        return def;
    }

    public CacheDefinition update(CacheDefinition def) {
        var params = toParams(def);
        params.put("updatedAt", new Timestamp(System.currentTimeMillis()));
        SqlConfig.getInstance().getSqlSessionManager().update(stmt("update"), params);
        return def;
    }

    public void delete(String id) {
        SqlConfig.getInstance().getSqlSessionManager().delete(stmt("delete"), id);
    }

    public CacheDefinition getById(String id) {
        Map<String, Object> row = SqlConfig.getInstance().getSqlSessionManager()
                .selectOne(stmt("getById"), id);
        return row != null ? fromRow(row) : null;
    }

    public List<CacheDefinition> getAll() {
        List<Map<String, Object>> rows = SqlConfig.getInstance().getSqlSessionManager()
                .selectList(stmt("getAll"));
        return rows.stream().map(this::fromRow).collect(Collectors.toList());
    }

    public CacheDefinition getByName(String name) {
        Map<String, Object> row = SqlConfig.getInstance().getSqlSessionManager()
                .selectOne(stmt("getByName"), name);
        return row != null ? fromRow(row) : null;
    }

    private Map<String, Object> toParams(CacheDefinition def) {
        var params = new HashMap<String, Object>();
        params.put("id", def.getId());
        params.put("name", def.getName());
        params.put("enabled", def.isEnabled());
        params.put("driver", def.getDriver());
        params.put("url", def.getUrl());
        params.put("username", def.getUsername());
        params.put("password", def.getPassword());
        params.put("query", def.getQuery());
        params.put("keyColumn", def.getKeyColumn());
        params.put("valueColumn", def.getValueColumn());
        params.put("maxSize", def.getMaxSize());
        params.put("evictionDurationMinutes", def.getEvictionDurationMinutes());
        return params;
    }

    private CacheDefinition fromRow(Map<String, Object> row) {
        var def = new CacheDefinition();
        def.setId((String) row.get("id"));
        def.setName((String) row.get("name"));

        var enabledVal = row.get("enabled");
        def.setEnabled(enabledVal == null || Boolean.TRUE.equals(enabledVal)
                || (enabledVal instanceof Number n && n.intValue() != 0));

        def.setDriver((String) row.get("driver"));
        def.setUrl((String) row.get("url"));
        def.setUsername((String) row.get("username"));
        def.setPassword((String) row.get("password"));

        def.setQuery((String) row.get("query"));
        def.setKeyColumn((String) row.get("keyColumn"));
        def.setValueColumn((String) row.get("valueColumn"));

        var maxSize = row.get("maxSize");
        if (maxSize instanceof Number n) {
            def.setMaxSize(n.longValue());
        }

        var eviction = row.get("evictionDurationMinutes");
        if (eviction instanceof Number n) {
            def.setEvictionDurationMinutes(n.longValue());
        }

        return def;
    }
}
