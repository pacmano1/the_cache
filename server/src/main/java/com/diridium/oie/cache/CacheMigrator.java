/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.util.List;
import java.util.Locale;

import com.mirth.connect.server.migration.Migrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Creates the cache_definition table on first startup.
 * Detects the database type and runs the appropriate dialect-specific SQL script.
 */
public class CacheMigrator extends Migrator {

    private static final Logger log = LoggerFactory.getLogger(CacheMigrator.class);

    @Override
    public void migrate() {
        try {
            executeScript("/" + getDatabaseType() + "-cache-tables.sql");
            log.info("Cache definition tables created successfully");
        } catch (Exception e) {
            var msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            if (msg.contains("already exist")) {
                log.info("Cache definition tables already exist, skipping");
            } else {
                log.warn("Cache definition tables migration may have failed: {}", e.getMessage(), e);
            }
        }
        addMaxConnectionsColumn();
    }

    private void addMaxConnectionsColumn() {
        // Oracle and SQL Server use ADD (no COLUMN keyword); Postgres/MySQL/Derby use ADD COLUMN
        var sql = switch (getDatabaseType()) {
            case "oracle" -> "ALTER TABLE cache_definition ADD max_connections NUMBER DEFAULT 5";
            case "sqlserver" -> "ALTER TABLE cache_definition ADD max_connections INT DEFAULT 5";
            default -> "ALTER TABLE cache_definition ADD COLUMN max_connections INTEGER DEFAULT 5";
        };
        try {
            executeStatement(sql);
            log.info("Added max_connections column to cache_definition");
        } catch (Exception e) {
            var msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            if (msg.contains("already exist") || msg.contains("duplicate column")) {
                log.debug("max_connections column already exists, skipping");
            } else {
                log.warn("Failed to add max_connections column: {}", e.getMessage(), e);
            }
        }
    }

    @Override
    public List<String> getUninstallStatements() {
        return List.of("DROP TABLE cache_definition");
    }

    @Override
    public void migrateSerializedData() {
        // No serialized data migration needed
    }
}
