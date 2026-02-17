/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.util.Arrays;
import java.util.List;

import com.mirth.connect.model.converters.ObjectXMLSerializer;

/**
 * Registers plugin model classes with Mirth's XStream-based ObjectXMLSerializer.
 * Must be called from both server and client plugin startup to prevent ForbiddenClassException.
 */
public class SerializationController {

    private static final Class<?>[] SERIALIZABLE_CLASSES = {
            CacheDefinition.class,
            CacheStatistics.class,
            CacheEntry.class,
            CacheSnapshot.class
    };

    public static void registerSerializableClasses() {
        var names = Arrays.stream(SERIALIZABLE_CLASSES)
                .map(Class::getCanonicalName)
                .toList();
        ObjectXMLSerializer.getInstance().allowTypes(names, List.of(), List.of());
        ObjectXMLSerializer.getInstance().processAnnotations(SERIALIZABLE_CLASSES);
    }

    private SerializationController() {
    }
}
