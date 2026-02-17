/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.util.Properties;

import com.mirth.connect.model.ExtensionPermission;
import com.mirth.connect.plugins.ServicePlugin;
import com.mirth.connect.server.util.GlobalVariableStore;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-side plugin entry point. Initializes the cache definition repository
 * and registers all persisted cache definitions on startup.
 */
public class CacheServerPlugin implements ServicePlugin {

    private static final Logger log = LoggerFactory.getLogger(CacheServerPlugin.class);

    @Override
    public String getPluginPointName() {
        return CacheServletInterface.PLUGIN_NAME;
    }

    @Override
    public void init(Properties properties) {
        // Called during server initialization
    }

    @Override
    public void start() {
        log.info("Starting OIE Cache Manager plugin");
        SerializationController.registerSerializableClasses();
        CacheDefinitionRepository.init();
        loadCacheDefinitions();
        GlobalVariableStore.getInstance().put("cache", new CacheLookup());
    }

    @Override
    public void stop() {
        log.info("Stopping OIE Cache Manager plugin");
        GlobalVariableStore.getInstance().remove("cache");
        CacheManager.shutdown();
        CacheDefinitionRepository.close();
    }

    @Override
    public void update(Properties properties) {
        // No runtime property updates needed
    }

    @Override
    public Properties getDefaultProperties() {
        return new Properties();
    }

    @Override
    public ExtensionPermission[] getExtensionPermissions() {
        return new ExtensionPermission[0];
    }

    private void loadCacheDefinitions() {
        try {
            var repo = CacheDefinitionRepository.getInstance();
            var manager = CacheManager.getInstance();
            var definitions = repo.getAll();
            for (var def : definitions) {
                try {
                    manager.registerCache(def);
                } catch (Exception e) {
                    log.warn("Failed to register cache '{}': {}", def.getName(), e.getMessage());
                }
            }
            log.info("Loaded {} cache definition(s)", definitions.size());
        } catch (Exception e) {
            log.error("Failed to load cache definitions on startup", e);
        }
    }
}
