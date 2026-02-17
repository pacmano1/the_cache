/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.util.List;
import java.util.Locale;

import com.mirth.connect.model.util.MigrationException;
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
    public void migrate() throws MigrationException {
        executeScriptSafely("/" + getDatabaseType() + "-cache-tables.sql", "Cache definition tables");
    }

    private void executeScriptSafely(String scriptName, String description) {
        try {
            executeScript(scriptName);
            log.info("{} created successfully", description);
        } catch (Exception e) {
            var msg = e.getMessage() != null ? e.getMessage().toLowerCase(Locale.ROOT) : "";
            if (msg.contains("already exist")) {
                log.info("{} already exist, skipping", description);
            } else {
                log.warn("{} migration may have failed: {}", description, e.getMessage(), e);
            }
        }
    }

    @Override
    public List<String> getUninstallStatements() throws MigrationException {
        return List.of("DROP TABLE cache_definition");
    }

    @Override
    public void migrateSerializedData() throws MigrationException {
        // No serialized data migration needed
    }
}
