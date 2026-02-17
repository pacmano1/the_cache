/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Method;

import com.mirth.connect.client.core.Permissions;
import com.mirth.connect.client.core.api.MirthOperation;

import org.junit.jupiter.api.Test;

/**
 * Verifies that each @MirthOperation on CacheServletInterface maps to the correct
 * permission and auditable setting.
 */
class CacheServletInterfacePermissionsTest {

    @Test
    void readOperations_requireSettingsView() throws Exception {
        assertPermission("getCacheDefinitions", Permissions.SERVER_SETTINGS_VIEW, false);
        assertPermission("getCacheDefinition", Permissions.SERVER_SETTINGS_VIEW, false);
        assertPermission("getAllCacheStatistics", Permissions.SERVER_SETTINGS_VIEW, false);
        assertPermission("getCacheStatistics", Permissions.SERVER_SETTINGS_VIEW, false);
        assertPermission("getCacheSnapshot", Permissions.SERVER_SETTINGS_VIEW, false);
    }

    @Test
    void writeOperations_requireSettingsEdit() throws Exception {
        assertPermission("createCacheDefinition", Permissions.SERVER_SETTINGS_EDIT, true);
        assertPermission("updateCacheDefinition", Permissions.SERVER_SETTINGS_EDIT, true);
        assertPermission("deleteCacheDefinition", Permissions.SERVER_SETTINGS_EDIT, true);
        assertPermission("refreshCache", Permissions.SERVER_SETTINGS_EDIT, true);
        assertPermission("testConnection", Permissions.SERVER_SETTINGS_EDIT, true);
        assertPermission("testConnectionInline", Permissions.SERVER_SETTINGS_EDIT, true);
    }

    private void assertPermission(String methodName, String expectedPermission, boolean expectedAuditable) {
        for (Method method : CacheServletInterface.class.getDeclaredMethods()) {
            var op = method.getAnnotation(MirthOperation.class);
            if (op != null && op.name().equals(methodName)) {
                assertEquals(expectedPermission, op.permission(),
                        methodName + " should require " + expectedPermission);
                assertEquals(expectedAuditable, op.auditable(),
                        methodName + " auditable should be " + expectedAuditable);
                return;
            }
        }
        fail("No @MirthOperation found with name '" + methodName + "'");
    }
}
