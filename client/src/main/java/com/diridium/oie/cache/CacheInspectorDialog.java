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

    public CacheInspectorDialog(JFrame parent, String cacheName, CacheSnapshot snapshot) {
        super(parent, "Cache Inspector: \"" + cacheName + "\"", true);

        setLayout(new BorderLayout(0, 8));

        add(buildStatsPanel(snapshot.getStatistics()), BorderLayout.NORTH);
        add(new JScrollPane(buildEntriesTable(snapshot.getEntries())), BorderLayout.CENTER);
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

    private JPanel buildStatsPanel(CacheStatistics stats) {
        var nf = NumberFormat.getIntegerInstance();
        var pf = NumberFormat.getPercentInstance();
        pf.setMaximumFractionDigits(1);

        var avgLoadMs = stats.getAverageLoadPenaltyNanos() / 1_000_000.0;

        var panel = new JPanel(new MigLayout("insets 8", "[][80]20[][80]20[][80]", "[]8[]"));

        panel.add(new JLabel("Entries:"));
        panel.add(new JLabel(nf.format(stats.getSize())));
        panel.add(new JLabel("Hit Rate:"));
        panel.add(new JLabel(pf.format(stats.getHitRate())));
        panel.add(new JLabel("Avg Load:"));
        panel.add(new JLabel(String.format("%.1f ms", avgLoadMs)), "wrap");

        panel.add(new JLabel("Hits:"));
        panel.add(new JLabel(nf.format(stats.getHitCount())));
        panel.add(new JLabel("Misses:"));
        panel.add(new JLabel(nf.format(stats.getMissCount())));
        panel.add(new JLabel("Evictions:"));
        panel.add(new JLabel(nf.format(stats.getEvictionCount())));

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

        var closeButton = new JButton("Close");
        closeButton.addActionListener(e -> dispose());
        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(closeButton);

        panel.add(buttonPanel, BorderLayout.EAST);
        getRootPane().setDefaultButton(closeButton);

        return panel;
    }

    private static String truncateValue(String value) {
        if (value == null) return "";
        if (value.length() <= VALUE_TRUNCATE_LENGTH) return value;
        return value.substring(0, VALUE_TRUNCATE_LENGTH) + "...";
    }

    private static String formatTimestamp(long millis) {
        if (millis == 0) return "-";
        return TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(millis));
    }

    private static class EntryTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Key", "Value", "Loaded At"};
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
                default -> null;
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return false;
        }
    }
}
