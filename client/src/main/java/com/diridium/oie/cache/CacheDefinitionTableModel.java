/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.table.AbstractTableModel;

/**
 * Table model for displaying cache definitions in the settings panel.
 */
public class CacheDefinitionTableModel extends AbstractTableModel {

    private static final String[] COLUMN_NAMES = {"Name", "Enabled", "Max Size", "Eviction (min)", "Memory", "Lookups"};
    private static final Class<?>[] COLUMN_CLASSES = {String.class, Boolean.class, Long.class, Long.class, String.class, Long.class};

    private final List<CacheDefinition> definitions = new ArrayList<>();
    private Map<String, Long> lookupCounts = new HashMap<>();
    private Map<String, Long> memoryEstimates = new HashMap<>();

    public void setDefinitions(List<CacheDefinition> defs) {
        definitions.clear();
        if (defs != null) {
            definitions.addAll(defs);
        }
        fireTableDataChanged();
    }

    public void setLookupCounts(Map<String, Long> counts) {
        this.lookupCounts = counts != null ? counts : new HashMap<>();
        fireTableDataChanged();
    }

    public void setData(List<CacheDefinition> defs, Map<String, Long> counts, Map<String, Long> memory) {
        definitions.clear();
        if (defs != null) {
            definitions.addAll(defs);
        }
        this.lookupCounts = counts != null ? counts : new HashMap<>();
        this.memoryEstimates = memory != null ? memory : new HashMap<>();
        fireTableDataChanged();
    }

    public CacheDefinition getDefinitionAt(int row) {
        if (row >= 0 && row < definitions.size()) {
            return definitions.get(row);
        }
        return null;
    }

    @Override
    public int getRowCount() {
        return definitions.size();
    }

    @Override
    public int getColumnCount() {
        return COLUMN_NAMES.length;
    }

    @Override
    public String getColumnName(int column) {
        return COLUMN_NAMES[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return COLUMN_CLASSES[columnIndex];
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        var def = definitions.get(rowIndex);
        return switch (columnIndex) {
            case 0 -> def.getName();
            case 1 -> def.isEnabled();
            case 2 -> def.getMaxSize();
            case 3 -> def.getEvictionDurationMinutes();
            case 4 -> formatBytes(memoryEstimates.getOrDefault(def.getId(), 0L));
            case 5 -> lookupCounts.getOrDefault(def.getId(), 0L);
            default -> null;
        };
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        return false;
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
