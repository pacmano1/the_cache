/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.util.List;

import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * Registers plugin model classes with Mirth's XStream-based ObjectXMLSerializer.
 * Must be called from both server and client plugin startup to prevent ForbiddenClassException.
 */
public class SerializationController {

    private static final List<String> types = List.of(
            CacheDefinition.class.getCanonicalName(),
            CacheStatistics.class.getCanonicalName(),
            CacheEntry.class.getCanonicalName(),
            CacheSnapshot.class.getCanonicalName());

    private static final Class<?>[] classes = new Class[]{
            CacheDefinition.class,
            CacheStatistics.class,
            CacheEntry.class,
            CacheSnapshot.class};

    public static void registerSerializableClasses() {
        ObjectXMLSerializer.getInstance().allowTypes(types, List.of(), List.of());
        ObjectXMLSerializer.getInstance().processAnnotations(classes);
    }

    private SerializationController() {
    }
}
