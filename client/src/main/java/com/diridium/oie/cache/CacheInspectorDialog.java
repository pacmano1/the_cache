/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.text.NumberFormat;
import java.util.List;

import javax.swing.AbstractAction;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingWorker;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;

import com.mirth.connect.client.ui.Frame;
import com.mirth.connect.client.ui.components.MirthTable;

import net.miginfocom.swing.MigLayout;

/**
 * Read-only dialog showing a point-in-time snapshot of a cache:
 * statistics at the top, entries table in the middle.
 * Sort, filter, and pagination are server-driven.
 */
public class CacheInspectorDialog extends JDialog {

    private static final int VALUE_TRUNCATE_LENGTH = 100;
    private static final int DEFAULT_LIMIT = 1000;
    private static final String[] SORT_FIELDS = {"key", "value", "loadedAt", "accessCount"};

    @FunctionalInterface
    public interface SnapshotFetcher {
        CacheSnapshot fetch(int limit, String sortBy, String sortDir,
                            String filter, String filterScope, boolean filterRegex) throws Exception;
    }

    private final SnapshotFetcher fetcher;
    private JPanel statsPanel;
    private JPanel centerPanel;
    private JTextField searchField;
    private JComboBox<String> searchScopeCombo;
    private JCheckBox regexCheckBox;
    private JLabel statusLabel;
    private MirthTable entriesTable;

    // Current sort state
    private String currentSortBy = "key";
    private String currentSortDir = "asc";

    public CacheInspectorDialog(JFrame parent, String cacheName,
                                CacheSnapshot snapshot, SnapshotFetcher fetcher) {
        super(parent, "Cache Inspector: \"" + cacheName + "\"", true);
        this.fetcher = fetcher;

        setLayout(new BorderLayout(0, 8));

        statsPanel = buildStatsPanel(snapshot.getStatistics());
        centerPanel = buildCenterPanel(snapshot.getEntries());
        updateStatusLabel(snapshot);

        add(statsPanel, BorderLayout.NORTH);
        add(centerPanel, BorderLayout.CENTER);
        add(buildBottomPanel(cacheName), BorderLayout.SOUTH);

        setSize(700, 500);
        setMinimumSize(new Dimension(550, 400));
        setLocationRelativeTo(parent);

        DialogUtils.registerEscapeClose(this);
    }

    private void fetchSnapshot() {
        var filter = searchField.getText().trim();
        var scopeItem = (String) searchScopeCombo.getSelectedItem();
        var filterScope = switch (scopeItem) {
            case "Value" -> "value";
            case "Both" -> "both";
            default -> "key";
        };
        var filterRegex = regexCheckBox.isSelected();

        new SwingWorker<CacheSnapshot, Void>() {
            @Override
            protected CacheSnapshot doInBackground() throws Exception {
                return fetcher.fetch(DEFAULT_LIMIT, currentSortBy, currentSortDir,
                        filter.isEmpty() ? null : filter, filterScope, filterRegex);
            }

            @Override
            protected void done() {
                try {
                    var snapshot = get();
                    var searchText = searchField.getText();
                    var searchScope = searchScopeCombo.getSelectedIndex();
                    var regexEnabled = regexCheckBox.isSelected();

                    remove(statsPanel);
                    remove(centerPanel);

                    statsPanel = buildStatsPanel(snapshot.getStatistics());
                    centerPanel = buildCenterPanel(snapshot.getEntries());
                    searchScopeCombo.setSelectedIndex(searchScope);
                    searchField.setText(searchText);
                    regexCheckBox.setSelected(regexEnabled);
                    updateStatusLabel(snapshot);

                    add(statsPanel, BorderLayout.NORTH);
                    add(centerPanel, BorderLayout.CENTER);
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

    private void updateStatusLabel(CacheSnapshot snapshot) {
        if (statusLabel == null) return;
        var nf = NumberFormat.getIntegerInstance();
        long shown = snapshot.getEntries().size();
        long matched = snapshot.getMatchedEntries();
        long total = snapshot.getTotalEntries();

        if (matched < total) {
            statusLabel.setText("Showing " + nf.format(shown) + " of "
                    + nf.format(matched) + " matches (" + nf.format(total) + " total)");
        } else if (shown < total) {
            statusLabel.setText("Showing " + nf.format(shown) + " of " + nf.format(total) + " entries");
        } else {
            statusLabel.setText("Showing " + nf.format(shown) + " entries");
        }
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
        panel.add(new JLabel(formatDuration(totalLoadMs)));
        panel.add(new JLabel("Est. Without Cache:"));
        var estWithoutCacheMs = stats.getAverageLoadPenaltyNanos() / 1_000_000.0 * stats.getRequestCount();
        panel.add(new JLabel(formatDuration(estWithoutCacheMs)), "span 3");

        return panel;
    }

    private JPanel buildCenterPanel(List<CacheEntry> entries) {
        var panel = new JPanel(new BorderLayout(0, 4));

        var searchPanel = new JPanel(new BorderLayout(4, 0));
        searchPanel.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 8, 0, 8));
        searchScopeCombo = new JComboBox<>(new String[]{"Key", "Value", "Both"});
        searchPanel.add(searchScopeCombo, BorderLayout.WEST);
        searchField = new JTextField();
        searchField.setToolTipText("Filter entries (case-insensitive). Press Enter or Apply to search.");
        searchField.addActionListener(e -> fetchSnapshot());
        searchPanel.add(searchField, BorderLayout.CENTER);

        var rightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        regexCheckBox = new JCheckBox("Regex");
        regexCheckBox.setToolTipText("Treat search text as a regular expression");
        rightPanel.add(regexCheckBox);
        var applyButton = new JButton("Apply");
        applyButton.addActionListener(e -> fetchSnapshot());
        rightPanel.add(applyButton);
        searchPanel.add(rightPanel, BorderLayout.EAST);

        panel.add(searchPanel, BorderLayout.NORTH);
        entriesTable = buildEntriesTable(entries);
        panel.add(new JScrollPane(entriesTable), BorderLayout.CENTER);

        return panel;
    }

    private void copySelectedCell(MirthTable table) {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        if (row < 0 || col < 0) return;

        var model = (EntryTableModel) table.getModel();
        String value;
        if (col == 1) {
            value = model.getEntry(row).getValue();
        } else {
            value = String.valueOf(table.getValueAt(row, col));
        }

        var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(value), null);
    }

    private MirthTable buildEntriesTable(List<CacheEntry> entries) {
        var table = new MirthTable();
        var model = new EntryTableModel(entries);
        table.setModel(model);
        table.setAutoCreateRowSorter(false);

        table.setCellSelectionEnabled(true);

        // Column header click triggers server-side sort
        JTableHeader header = table.getTableHeader();
        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int col = header.columnAtPoint(e.getPoint());
                if (col < 0 || col >= SORT_FIELDS.length) return;
                var field = SORT_FIELDS[col];
                if (field.equals(currentSortBy)) {
                    currentSortDir = "asc".equals(currentSortDir) ? "desc" : "asc";
                } else {
                    currentSortBy = field;
                    currentSortDir = "asc";
                }
                fetchSnapshot();
            }
        });

        // Ctrl+C copies the selected cell value (full value for the Value column)
        table.getInputMap(JComponent.WHEN_FOCUSED)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_C, KeyEvent.CTRL_DOWN_MASK), "copyCell");
        table.getActionMap().put("copyCell", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copySelectedCell(table);
            }
        });

        // Right-click context menu
        var popup = new JPopupMenu();
        var copyItem = new JMenuItem("Copy Value");
        copyItem.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) return;
            var entry = ((EntryTableModel) table.getModel()).getEntry(row);
            var clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(entry.getValue()), null);
        });
        popup.add(copyItem);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        table.setRowSelectionInterval(row, row);
                        popup.show(e.getComponent(), e.getX(), e.getY());
                    }
                }
            }
        });

        // Double-click opens entry detail dialog
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        var entry = ((EntryTableModel) table.getModel()).getEntry(row);
                        new EntryDetailDialog(CacheInspectorDialog.this, entry).setVisible(true);
                    }
                }
            }
        });

        // Tooltip for full value on the Value column
        table.addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col == 1) {
                    var fullValue = model.getEntry(row).getValue();
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

        var leftPanel = new JPanel(new MigLayout("insets 0 8 0 0, flowy", "[]", "[]0[]"));
        var usagePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        var usageLabel = new JLabel("Usage: ");
        usagePanel.add(usageLabel);
        var usageHint = new JTextField("$g('" + cacheName + "').lookup(key)");
        usageHint.setEditable(false);
        usageHint.setBorder(null);
        usageHint.setBackground(panel.getBackground());
        usageHint.setFont(new Font(Font.MONOSPACED, Font.PLAIN, usageHint.getFont().getSize()));
        usagePanel.add(usageHint);
        leftPanel.add(usagePanel);

        statusLabel = new JLabel(" ");
        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.PLAIN, 11f));
        leftPanel.add(statusLabel);
        panel.add(leftPanel, BorderLayout.WEST);

        var refreshButton = new JButton("Refresh");
        refreshButton.addActionListener(e -> fetchSnapshot());
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
                <html><body style='width:400px; font-family:sans-serif'>
                <b>Entries</b> — Number of key-value pairs currently in the cache.<br><br>
                <b>Hit Rate</b> — Percentage of lookups served from cache without a database call.<br><br>
                <b>Avg Load</b> — Average time per database round-trip on a cache miss.<br><br>
                <b>Hits</b> — Lookups served from cache (no database call).<br><br>
                <b>Misses</b> — Lookups that required a database call to load the value.<br><br>
                <b>Evictions</b> — Entries removed due to max size or expiration.<br><br>
                <b>Total DB Time</b> — Cumulative time spent waiting on database loads (cache misses only).<br><br>
                <b>Est. Without Cache</b> — Approximate total time that would have been spent \
                on database calls if every lookup had been a cache miss. Calculated as \
                <i>Avg Load &times; total lookups</i>. This is a rough estimate — actual \
                database performance may vary under different load conditions.
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
        return DialogUtils.formatTimestamp(millis);
    }

    private static class EntryTableModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Key", "Value", "Loaded At", "Accesses"};
        private final List<CacheEntry> entries;

        EntryTableModel(List<CacheEntry> entries) {
            this.entries = entries;
        }

        CacheEntry getEntry(int rowIndex) {
            return entries.get(rowIndex);
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
                case 3 -> entry.getAccessCount();
                default -> null;
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            if (columnIndex == 3) return Long.class;
            return String.class;
        }
    }
}
