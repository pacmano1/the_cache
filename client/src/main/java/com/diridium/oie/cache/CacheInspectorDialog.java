/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Supplier;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;

import com.mirth.connect.client.ui.Frame;
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
                    JOptionPane.showMessageDialog(CacheInspectorDialog.this,
                            "Failed to refresh: " + e.getMessage(),
                            "Refresh Error", JOptionPane.ERROR_MESSAGE);
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

        var panel = new JPanel(new MigLayout("insets 8", "[][80]20[][80]20[][80][]", "[]8[]"));

        panel.add(new JLabel("Entries:"));
        panel.add(new JLabel(nf.format(stats.getSize())));
        panel.add(new JLabel("Hit Rate:"));
        panel.add(new JLabel(pf.format(stats.getHitRate())));
        panel.add(new JLabel("Avg Load:"));
        panel.add(new JLabel(String.format("%.1f ms", avgLoadMs)));
        var helpIcon = new JLabel(new ImageIcon(Frame.class.getResource("images/help.png")));
        helpIcon.setToolTipText("Cache Statistics Help");
        helpIcon.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        helpIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                showStatsHelp();
            }
        });
        panel.add(helpIcon, "spany 3, top, wrap");

        panel.add(new JLabel("Hits:"));
        panel.add(new JLabel(nf.format(stats.getHitCount())));
        panel.add(new JLabel("Misses:"));
        panel.add(new JLabel(nf.format(stats.getMissCount())));
        panel.add(new JLabel("Evictions:"));
        panel.add(new JLabel(nf.format(stats.getEvictionCount())), "wrap");

        panel.add(new JLabel("Total DB Time:"));
        panel.add(new JLabel(formatDuration(totalLoadMs)), "span 5");

        return panel;
    }

    private MirthTable buildEntriesTable(List<CacheEntry> entries) {
        var table = new MirthTable();
        table.setModel(new EntryTableModel(entries));
        table.setAutoCreateRowSorter(true);

        // Tooltip for full value on the Value column
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
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
        usageHint.setFont(new Font(Font.MONOSPACED, Font.PLAIN, usageHint.getFont().getSize()));
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

    private void showStatsHelp() {
        var help = """
                <html><body style='width:350px; font-family:sans-serif'>
                <b>Entries</b> — Number of key-value pairs currently in the cache.<br><br>
                <b>Hit Rate</b> — Percentage of lookups served from cache without a database call.<br><br>
                <b>Avg Load</b> — Average time per database round-trip on a cache miss.<br><br>
                <b>Hits</b> — Lookups served from cache (no database call).<br><br>
                <b>Misses</b> — Lookups that required a database call to load the value.<br><br>
                <b>Evictions</b> — Entries removed due to max size or expiration.<br><br>
                <b>Total DB Time</b> — Cumulative time spent waiting on database loads (cache misses only).
                </body></html>""";
        JOptionPane.showMessageDialog(this, help, "Cache Statistics Help", JOptionPane.INFORMATION_MESSAGE);
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
