# OIE Guava Cache Plugin — Project Context

## Project Overview

An OIE (Open Integration Engine / Mirth Connect fork) plugin that wraps Google Guava `LoadingCache` to provide fast in-memory lookups for crosswalk and configuration data stored in external databases. The plugin does NOT own the data — external databases are the source of truth. The cache makes lookups fast.

## Architecture

```
the_cache/
├── client/          # Swing UI — SettingsPanelPlugin for admin Settings tab
├── server/          # Server-side plugin — cache management, JDBC, REST API
├── shared/          # Servlet interface (JAX-RS), DTOs, models
└── package/         # Assembly ZIP, plugin.xml, sqlmap.xml
```

### Technology Stack

- Java 17
- Mirth Connect 4.5.2 plugin architecture
- Google Guava `LoadingCache`
- MyBatis for cross-database SQL (plugin's own metadata storage)
- Raw JDBC via `DriverManager` for external database queries (no connection pool)
- Maven 4-module build (client/server/shared/package)

## Core Concepts

### Cache Behavior

1. **Lazy loading**: First access for a key → cache miss → CacheLoader runs parameterized query against external DB → stores and returns result
2. **Cache hit**: Subsequent calls return instantly from memory
3. **Eviction**: Guava `expireAfterAccess` + `maximumSize` for memory management
4. **Refresh**: Explicit action (API call or admin trigger) re-fetches only entries currently in cache — does NOT reload the entire backing dataset
5. **Read-only**: No write API from channel code — external DB is the authority

### Cache Definitions

Created via admin UI OR programmatically through the REST API. Each definition includes:

- **Name** — the key engineers reference in channel code
- **Database connection** — driver, URL, username, password (per cache)
- **Parameterized SQL query** — e.g., `SELECT config FROM facilities WHERE site_code = ?`
- **Key column / value column** — which result columns map to cache key and value
- **Max size** — Guava `maximumSize`
- **Eviction duration** — Guava `expireAfterAccess` time

All caches are globally scoped. Use different names if you need separation.

### Cache Values Are Opaque

The plugin stores and returns `String` values. A value could be a plain string, a JSON object, or anything else. The plugin does not interpret the data — the channel code consumer does.

Example: A facility config cache returns a JSON blob per site code. The channel parses the returned string as needed.

### Channel API

A `CacheLookup` facade is registered in `globalMap` on startup, so channel code can look up cached values:

```java
$g('cache').lookup('facility-config', key)
```

The method is named `lookup` (not `get`) to avoid confusion with `Map.get(Object)` on the `Object` return from `$g()`.

## Database Connections

Each cache definition manages its own JDBC connection to its external data source. Follows the same pattern as Mirth's Database Reader connector:

- `DriverManager.getConnection(url, username, password)`
- No connection pooling — connections are opened for cache loads and closed after
- Supports custom drivers via Mirth's `ContextFactory` / isolated classloader pattern

## Plugin Metadata Storage

The plugin stores cache definitions in the OIE database using MyBatis. This is separate from the external databases the caches query.

- Table creation via `Migrator` subclass (auto-runs on install)
- Multi-database support: PostgreSQL, MySQL, Oracle, SQL Server, Derby
- Uses `ConfigurationController.saveProperty()` pattern where appropriate

## Permissions

All REST operations use `@MirthOperation` with permissions from `Permissions.*`. The engine enforces these via `MirthServlet` automatically.

- **Read operations** (list cache definitions, get stats) → `Permissions.SERVER_SETTINGS_VIEW`
- **Write operations** (create/update/delete cache definitions, refresh) → `Permissions.SERVER_SETTINGS_EDIT`

Cache lives in the Settings tab, so settings permissions are the natural fit.

## Event Logging

Mutating operations log to the OIE Event Log via `EventController.dispatchEvent()`:

- Cache definition created / updated / deleted
- Manual cache refresh triggered
- Connection test executed

Read operations (list, stats, channel-code lookups) do NOT generate events — marked `auditable = false`.

Pattern (from simple-channel-history):
```java
var attributes = new LinkedHashMap<String, String>();
attributes.put("Cache", cacheName);
attributes.put("Action", "Created");
eventController.dispatchEvent(new ServerEvent(
    serverId, PLUGIN_NAME, Level.INFORMATION, Outcome.SUCCESS, attributes));
```

## REST API

Servlet interface defines CRUD for cache definitions plus operational endpoints:

- Create / update / delete cache definitions
- List cache definitions
- Refresh a cache (re-fetch entries currently in memory)
- Cache statistics (optional)

Path: `/extensions/oie-cache-manager` (or similar)

## What This Plugin Is NOT

- **Not a data store** — unlike BridgeLink's Dynamic Lookup Gateway, this plugin does NOT own the data. It caches data from external sources.
- **No write API** from channel code
- **No audit trail** — we are not mutating data
- **No JSON awareness** — values are opaque strings

## Reference Projects

- **simple-channel-history** — Primary reference for plugin architecture (client/server/shared/package modules, Migrator, MyBatis, REST servlet, SettingsPanelPlugin)
- **oie-tls-settings-editor** — Reference for SettingsPanelPlugin UI patterns (AbstractSettingsPanel, task pane, dialogs)
- **oie-tls-manager** — Reference for REST API patterns and shared model design
- **Engine Database Reader** (`com.mirth.connect.connectors.jdbc`) — Reference for JDBC connection pattern (DriverManager, no pooling, CustomDriver support)

## Build & Test

```bash
# Build (produces package/target/oie-cache-manager-<version>.zip)
mvn clean package

# Run tests
mvn test

# Skip tests
mvn clean package -DskipTests
```

### Test Coverage

JUnit 5 + Mockito. Tests live in each module's `src/test/java/` directory.

**Cache behavior**
- CacheLoader: miss triggers JDBC query, hit returns from memory without query
- Eviction: entries expire after `expireAfterAccess` duration, `maximumSize` enforced
- Refresh: explicit refresh re-fetches only entries currently in cache, does not load new keys

**JDBC**
- Connection opened before query, closed after (even on error)
- Parameterized query binds key to `?` placeholder
- Query returns key/value columns as configured
- Connection failure surfaces meaningful error
- Custom driver classloader support

**Servlet permissions** (shared module)
- Verify each `@MirthOperation` maps to correct `Permissions.*` constant
- Read operations → `SERVER_SETTINGS_VIEW`
- Write operations → `SERVER_SETTINGS_EDIT`
- Read operations marked `auditable = false`

**Event dispatch** (server module)
- Create/update/delete cache definition → event dispatched with correct attributes
- Refresh and connection test → event dispatched
- List/get/stats/channel lookups → no event dispatched

**Cache definition CRUD** (server module, mock MyBatis)
- Create persists definition and returns it
- Update modifies existing definition
- Delete removes definition and invalidates associated cache
- List returns all definitions
- Duplicate name rejected

## Conventions

- License header: `/* SPDX-License-Identifier: MPL-2.0 \n * Copyright (c) 2026 Diridium <https://diridium.com> */`
- Java 17 (var, switch expressions, text blocks OK)
- JUnit 5 + Mockito for tests
- MigLayout for complex Swing layouts, BorderLayout/FlowLayout for simple ones
- No Lombok
