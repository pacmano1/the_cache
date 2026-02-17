/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium <https://diridium.com> */

package com.diridium.oie.cache;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import com.mirth.connect.client.ui.components.MirthTable;

import net.miginfocom.swing.MigLayout;

/**
 * Read-only dialog showing a point-in-time snapshot of a cache:
 * statistics at the top, entries table in the middle.
 */
public class CacheInspectorDialog extends JDialog {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private static final int VALUE_TRUNCATE_LENGTH = 100;

    private final Supplier<CacheSnapshot> snapshotSupplier;
    private JPanel statsPanel;
    private JScrollPane tableScrollPane;

    public CacheInspectorDialog(JFrame parent, String cacheName,
                                CacheSnapshot snapshot, Supplier<CacheSnapshot> snapshotSupplier) {
        super(parent, "Cache Inspector: \"" + cacheName + "\"", true);
        this.snapshotSupplier = snapshotSupplier;

        setLayout(new BorderLayout(0, 8));

        statsPanel = buildStatsPanel(snapshot.getStatistics());
        tableScrollPane = new JScrollPane(buildEntriesTable(snapshot.getEntries()));

        add(statsPanel, BorderLayout.NORTH);
        add(tableScrollPane, BorderLayout.CENTER);
        add(buildBottomPanel(cacheName), BorderLayout.SOUTH);

        setSize(700, 500);
        setMinimumSize(new Dimension(550, 400));
        setLocationRelativeTo(parent);

        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "close");
        getRootPane().getActionMap().put("close", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });
    }

    private void refreshSnapshot(JButton refreshButton) {
        refreshButton.setEnabled(false);
        refreshButton.setText("Refreshing...");

        new SwingWorker<CacheSnapshot, Void>() {
            @Override
            protected CacheSnapshot doInBackground() throws Exception {
                return snapshotSupplier.get();
            }

            @Override
            protected void done() {
                refreshButton.setEnabled(true);
                refreshButton.setText("Refresh");
                try {
                    var snapshot = get();

                    remove(statsPanel);
                    remove(tableScrollPane);

                    statsPanel = buildStatsPanel(snapshot.getStatistics());
                    tableScrollPane = new JScrollPane(buildEntriesTable(snapshot.getEntries()));

                    add(statsPanel, BorderLayout.NORTH);
                    add(tableScrollPane, BorderLayout.CENTER);
                    revalidate();
                    repaint();
                } catch (Exception e) {
                    javax.swing.JOptionPane.showMessageDialog(CacheInspectorDialog.this,
                            "Failed to refresh: " + e.getMessage(),
                            "Refresh Error", javax.swing.JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private JPanel buildStatsPanel(CacheStatistics stats) {
        var nf = NumberFormat.getIntegerInstance();
        var pf = NumberFormat.getPercentInstance();
        pf.setMaximumFractionDigits(1);

        var avgLoadMs = stats.getAverageLoadPenaltyNanos() / 1_000_000.0;
        var totalLoadMs = stats.getTotalLoadTimeNanos() / 1_000_000.0;

        var panel = new JPanel(new MigLayout("insets 8", "[][80]20[][80]20[][80]", "[]8[]"));

        panel.add(tipLabel("Entries:", "Number of key-value pairs currently in the cache"));
        panel.add(tipLabel(nf.format(stats.getSize()), null));
        panel.add(tipLabel("Hit Rate:", "Percentage of lookups served from cache without a database call"));
        panel.add(tipLabel(pf.format(stats.getHitRate()), null));
        panel.add(tipLabel("Avg Load:", "Average time per database round-trip on a cache miss"));
        panel.add(tipLabel(String.format("%.1f ms", avgLoadMs), null), "wrap");

        panel.add(tipLabel("Hits:", "Lookups served from cache (no database call)"));
        panel.add(tipLabel(nf.format(stats.getHitCount()), null));
        panel.add(tipLabel("Misses:", "Lookups that required a database call to load the value"));
        panel.add(tipLabel(nf.format(stats.getMissCount()), null));
        panel.add(tipLabel("Evictions:", "Entries removed due to max size or expiration"));
        panel.add(tipLabel(nf.format(stats.getEvictionCount()), null), "wrap");

        panel.add(tipLabel("Total DB Time:", "Cumulative time spent waiting on database loads (cache misses only)"));
        panel.add(tipLabel(formatDuration(totalLoadMs), null), "span 5");

        return panel;
    }

    private MirthTable buildEntriesTable(List<CacheEntry> entries) {
        var table = new MirthTable();
        table.setModel(new EntryTableModel(entries));
        table.setAutoCreateRowSorter(true);

        // Tooltip for full value on the Value column
        table.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 1) {
                    int modelRow = table.convertRowIndexToModel(row);
                    var fullValue = entries.get(modelRow).getValue();
                    table.setToolTipText(fullValue);
                } else {
                    table.setToolTipText(null);
                }
            }
        });

        return table;
    }

    private JPanel buildBottomPanel(String cacheName) {
        var panel = new JPanel(new BorderLayout());

        var usageHint = new JTextField("$g('cache').lookup('" + cacheName + "', key)");
        usageHint.setEditable(false);
        usageHint.setBorder(null);
        usageHint.setBackground(panel.getBackground());
        usageHint.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, usageHint.getFont().getSize()));
        panel.add(usageHint, BorderLayout.WEST);

        var refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> refreshSnapshot(refreshButton));
        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        buttonPanel.add(closeButton);

        panel.add(buttonPanel, BorderLayout.EAST);
        getRootPane().setDefaultButton(closeButton);

        return panel;
    }

    private static JLabel tipLabel(String text, String tooltip) {
        if (tooltip != null) {
            var label = new JLabel("<html>" + escapeHtml(text)
                    + " <span style='color:gray'>?</span></html>");
            label.setToolTipText(tooltip);
            return label;
        }
        return new JLabel(text);
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String truncateValue(String value) {
        if (value == null) return "";
        if (value.length() <= VALUE_TRUNCATE_LENGTH) return value;
        return value.substring(0, VALUE_TRUNCATE_LENGTH) + "...";
    }

    private static String formatDuration(double millis) {
        if (millis < 1_000) return String.format("%.0f ms", millis);
        if (millis < 60_000) return String.format("%.1f s", millis / 1_000);
        if (millis < 3_600_000) return String.format("%.1f min", millis / 60_000);
        return String.format("%.1f hr", millis / 3_600_000);
    }

    private static String formatTimestamp(long millis) {
        if (millis == 0) return "-";
        return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private static class EntryTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Key", "Value", "Loaded At", "Hits"};
        private final List<CacheEntry> entries;

        EntryTableModel(List<CacheEntry> entries) {
            this.entries = entries;
        }

        @Override
        public int getRowCount() {
            return entries.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            var entry = entries.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> entry.getKey();
                case 1 -> truncateValue(entry.getValue());
                case 2 -> formatTimestamp(entry.getLoadedAtMillis());
                case 3 -> entry.getHitCount();
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3) return Long.class;
            return String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}
