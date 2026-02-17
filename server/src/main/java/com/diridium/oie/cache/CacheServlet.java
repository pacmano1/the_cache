/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.util.LinkedHashMap;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.SecurityContext;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.core.api.MirthApiException;
import com.mirth.connect.model.ServerEvent;
import com.mirth.connect.model.ServerEvent.Level;
import com.mirth.connect.model.ServerEvent.Outcome;
import com.mirth.connect.server.api.MirthServlet;
import com.mirth.connect.server.controllers.ConfigurationController;
import com.mirth.connect.server.controllers.ControllerFactory;
import com.mirth.connect.server.controllers.EventController;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * REST servlet implementing the cache manager API.
 * Extends MirthServlet for automatic permission enforcement via @MirthOperation.
 */
public class CacheServlet extends MirthServlet implements CacheServletInterface {

    private static final Logger log = LoggerFactory.getLogger(CacheServlet.class);

    private final CacheDefinitionRepository repo;
    private final CacheManager cacheManager;
    private final EventController eventController;
    private final String serverId;

    public CacheServlet(@Context HttpServletRequest request, @Context SecurityContext sc) {
        super(request, sc, PLUGIN_NAME);
        this.repo = CacheDefinitionRepository.getInstance();
        this.cacheManager = CacheManager.getInstance();
        this.eventController = ControllerFactory.getFactory().createEventController();
        this.serverId = ConfigurationController.getInstance().getServerId();
    }

    @Override
    public List<CacheDefinition> getCacheDefinitions() throws ClientException {
        try {
            return repo.getAll();
        } catch (Exception e) {
            log.error("Failed to list cache definitions", e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public CacheDefinition getCacheDefinition(String id) throws ClientException {
        try {
            var def = repo.getById(id);
            if (def == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }
            return def;
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get cache definition {}", id, e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public CacheDefinition createCacheDefinition(CacheDefinition definition) throws ClientException {
        try {
            // Check for duplicate name
            var existing = repo.getByName(definition.getName());
            if (existing != null) {
                throw new MirthApiException(Status.CONFLICT);
            }

            var created = repo.create(definition);
            cacheManager.registerCache(created);

            dispatchEvent("Created", created.getName());
            return created;
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create cache definition", e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public CacheDefinition updateCacheDefinition(String id, CacheDefinition definition) throws ClientException {
        try {
            var existing = repo.getById(id);
            if (existing == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }

            // Check for duplicate name (exclude self)
            var byName = repo.getByName(definition.getName());
            if (byName != null && !byName.getId().equals(id)) {
                throw new MirthApiException(Status.CONFLICT);
            }

            definition.setId(id);
            var updated = repo.update(definition);

            // Re-register the cache with updated settings
            cacheManager.unregisterCache(id);
            cacheManager.registerCache(updated);

            dispatchEvent("Updated", updated.getName());
            return updated;
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to update cache definition {}", id, e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public void deleteCacheDefinition(String id) throws ClientException {
        try {
            var existing = repo.getById(id);
            if (existing == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }

            cacheManager.unregisterCache(id);
            repo.delete(id);

            dispatchEvent("Deleted", existing.getName());
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to delete cache definition {}", id, e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public void refreshCache(String id) throws ClientException {
        try {
            var def = repo.getById(id);
            if (def == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }

            cacheManager.refresh(id);
            dispatchEvent("Refreshed", def.getName());
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to refresh cache {}", id, e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public String testConnection(String id) throws ClientException {
        try {
            var def = repo.getById(id);
            if (def == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }

            var result = cacheManager.testConnection(def);
            dispatchEvent("Connection Test", def.getName());
            return result;
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to test connection for cache {}", id, e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public String testConnectionInline(CacheDefinition definition) throws ClientException {
        try {
            var result = cacheManager.testConnection(definition);
            dispatchEvent("Connection Test (Inline)", definition.getName() != null ? definition.getName() : "(unsaved)");
            return result;
        } catch (Exception e) {
            log.error("Failed to test inline connection", e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public String testQueryInline(CacheDefinition definition, String sampleKey) throws ClientException {
        try {
            var result = cacheManager.testQuery(definition, sampleKey);
            dispatchEvent("Query Test", definition.getName() != null ? definition.getName() : "(unsaved)");
            return result;
        } catch (Exception e) {
            log.error("Failed to test inline query", e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public List<CacheStatistics> getAllCacheStatistics() throws ClientException {
        try {
            return cacheManager.getAllStatistics();
        } catch (Exception e) {
            log.error("Failed to get all cache statistics", e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public CacheSnapshot getCacheSnapshot(String id) throws ClientException {
        try {
            var snapshot = cacheManager.getSnapshot(id);
            if (snapshot == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }
            return snapshot;
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get snapshot for cache {}", id, e);
            throw new MirthApiException(e);
        }
    }

    @Override
    public CacheStatistics getCacheStatistics(String id) throws ClientException {
        try {
            var stats = cacheManager.getStatistics(id);
            if (stats == null) {
                throw new MirthApiException(Status.NOT_FOUND);
            }
            return stats;
        } catch (MirthApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to get statistics for cache {}", id, e);
            throw new MirthApiException(e);
        }
    }

    private void dispatchEvent(String action, String cacheName) {
        var attributes = new LinkedHashMap<String, String>();
        attributes.put("Cache", cacheName);
        attributes.put("Action", action);
        eventController.dispatchEvent(new ServerEvent(
                serverId, PLUGIN_NAME, Level.INFORMATION, Outcome.SUCCESS, attributes));
    }
}
