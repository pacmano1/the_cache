/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.awt.BorderLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingWorker;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.TableRowSorter;

import com.mirth.connect.client.ui.AbstractSettingsPanel;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.components.MirthTable;

import net.miginfocom.swing.MigLayout;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Settings panel for managing cache definitions.
 * Appears as a tab in the OIE Administrator Settings view.
 */
public class CacheSettingsPanel extends AbstractSettingsPanel {

    private static final Logger log = LoggerFactory.getLogger(CacheSettingsPanel.class);

    private CacheServletInterface servlet;
    private MirthTable table;
    private CacheDefinitionTableModel tableModel;
    private JButton btnEdit;
    private JButton btnDuplicate;
    private JButton btnDelete;
    private JButton btnShowCache;
    private JButton btnRefresh;
    private JTextField filterField;
    private TableRowSorter<CacheDefinitionTableModel> rowSorter;

    public CacheSettingsPanel(String tabName) {
        super(tabName);
        initComponents();
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        tableModel = new CacheDefinitionTableModel();
        table = new MirthTable();
        table.setModel(tableModel);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        rowSorter = new TableRowSorter<>(tableModel);
        table.setRowSorter(rowSorter);

        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                updateButtonStates();
            }
        });

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && table.getSelectedRowCount() == 1) {
                    editDefinition();
                }
            }
        });

        filterField = new JTextField();
        filterField.putClientProperty("JTextField.placeholderText", "Filter by name...");
        filterField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void removeUpdate(DocumentEvent e) { applyFilter(); }
            @Override
            public void changedUpdate(DocumentEvent e) { applyFilter(); }
        });

        var btnNew = new JButton("New");
        btnNew.addActionListener(e -> newDefinition());

        btnEdit = new JButton("Edit");
        btnEdit.setEnabled(false);
        btnEdit.addActionListener(e -> editDefinition());

        btnDuplicate = new JButton("Duplicate");
        btnDuplicate.setEnabled(false);
        btnDuplicate.addActionListener(e -> duplicateDefinition());

        btnDelete = new JButton("Delete");
        btnDelete.setEnabled(false);
        btnDelete.addActionListener(e -> deleteDefinition());

        btnShowCache = new JButton("Show Cache");
        btnShowCache.setEnabled(false);
        btnShowCache.addActionListener(e -> showCache());

        btnRefresh = new JButton("Refresh Cache");
        btnRefresh.setEnabled(false);
        btnRefresh.addActionListener(e -> refreshCache());


        var buttonPanel = new JPanel(new MigLayout("insets 0 12 0 12", "[][][][]push[][]", ""));
        buttonPanel.add(btnNew);
        buttonPanel.add(btnEdit);
        buttonPanel.add(btnDuplicate);
        buttonPanel.add(btnDelete);
        buttonPanel.add(btnShowCache);
        buttonPanel.add(btnRefresh);

        var topPanel = new JPanel(new MigLayout("insets 0 12 0 12, flowy", "[grow]", "[]4[]"));
        topPanel.add(buttonPanel, "growx");
        topPanel.add(filterField, "growx");

        add(topPanel, BorderLayout.NORTH);
        add(new JScrollPane(table), BorderLayout.CENTER);
    }

    @Override
    public void doRefresh() {
        new SwingWorker<Void, Void>() {
            private List<CacheDefinition> defs;
            private Map<String, Long> counts;
            private Map<String, Long> memory;

            @Override
            protected Void doInBackground() throws Exception {
                defs = getServlet().getCacheDefinitions();
                counts = new HashMap<>();
                memory = new HashMap<>();
                try {
                    for (var stats : getServlet().getAllCacheStatistics()) {
                        counts.put(stats.getCacheDefinitionId(), stats.getRequestCount());
                        memory.put(stats.getCacheDefinitionId(), stats.getEstimatedMemoryBytes());
                    }
                } catch (Exception e) {
                    log.debug("Could not fetch cache statistics", e);
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    tableModel.setData(defs, counts, memory);
                } catch (Exception e) {
                    log.error("Failed to load cache definitions", e);
                    tableModel.setData(Collections.emptyList(), Collections.emptyMap(), Collections.emptyMap());
                }
                updateButtonStates();
            }
        }.execute();
    }

    @Override
    public boolean doSave() {
        return true;
    }

    private void applyFilter() {
        var text = filterField.getText().trim();
        if (text.isEmpty()) {
            rowSorter.setRowFilter(null);
        } else {
            rowSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(text), 0));
        }
    }

    private void updateButtonStates() {
        boolean selected = table.getSelectedRowCount() == 1;
        btnEdit.setEnabled(selected);
        btnDuplicate.setEnabled(selected);
        btnDelete.setEnabled(selected);
        btnShowCache.setEnabled(selected);
        btnRefresh.setEnabled(selected);
    }

    private CacheDefinition getSelectedDefinition() {
        int row = table.getSelectedRow();
        if (row < 0) return null;
        int modelRow = table.convertRowIndexToModel(row);
        return tableModel.getDefinitionAt(modelRow);
    }

    private void newDefinition() {
        showAndCreateAsync(new CacheDefinitionDialog(PlatformUI.MIRTH_FRAME, null));
    }

    private void editDefinition() {
        var selected = getSelectedDefinition();
        if (selected == null) return;

        var dialog = new CacheDefinitionDialog(PlatformUI.MIRTH_FRAME, selected);
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    getServlet().updateCacheDefinition(selected.getId(), dialog.getDefinition());
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        doRefresh();
                    } catch (Exception e) {
                        PlatformUI.MIRTH_FRAME.alertThrowable(
                                PlatformUI.MIRTH_FRAME, e, "Failed to update cache definition");
                    }
                }
            }.execute();
        }
    }

    private void duplicateDefinition() {
        var selected = getSelectedDefinition();
        if (selected == null) return;

        var copy = selected.copyWithoutId();
        copy.setName("Copy of " + selected.getName());

        showAndCreateAsync(new CacheDefinitionDialog(
                PlatformUI.MIRTH_FRAME, copy, "Duplicate Cache Definition"));
    }

    private void showAndCreateAsync(CacheDefinitionDialog dialog) {
        dialog.setVisible(true);
        if (dialog.isSaved()) {
            new SwingWorker<Void, Void>() {
                @Override
                protected Void doInBackground() throws Exception {
                    getServlet().createCacheDefinition(dialog.getDefinition());
                    return null;
                }

                @Override
                protected void done() {
                    try {
                        get();
                        doRefresh();
                    } catch (Exception e) {
                        PlatformUI.MIRTH_FRAME.alertThrowable(
                                PlatformUI.MIRTH_FRAME, e, "Failed to create cache definition");
                    }
                }
            }.execute();
        }
    }

    private void deleteDefinition() {
        var selected = getSelectedDefinition();
        if (selected == null) return;

        int result = JOptionPane.showConfirmDialog(this,
                "Delete cache definition '" + selected.getName() + "'?",
                "Confirm Delete", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                getServlet().deleteCacheDefinition(selected.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    doRefresh();
                } catch (Exception e) {
                    PlatformUI.MIRTH_FRAME.alertThrowable(
                            PlatformUI.MIRTH_FRAME, e, "Failed to delete cache definition");
                }
            }
        }.execute();
    }

    private void showCache() {
        var selected = getSelectedDefinition();
        if (selected == null) return;

        new SwingWorker<CacheSnapshot, Void>() {
            @Override
            protected CacheSnapshot doInBackground() throws Exception {
                return getServlet().getCacheSnapshot(selected.getId());
            }

            @Override
            protected void done() {
                try {
                    var snapshot = get();
                    var dialog = new CacheInspectorDialog(
                            PlatformUI.MIRTH_FRAME, selected.getName(), snapshot,
                            () -> {
                                try {
                                    return getServlet().getCacheSnapshot(selected.getId());
                                } catch (Exception ex) {
                                    throw new RuntimeException(ex);
                                }
                            });
                    dialog.setVisible(true);
                } catch (Exception e) {
                    PlatformUI.MIRTH_FRAME.alertThrowable(
                            PlatformUI.MIRTH_FRAME, e, "Failed to load cache snapshot");
                }
            }
        }.execute();
    }

    private void refreshCache() {
        var selected = getSelectedDefinition();
        if (selected == null) return;

        var confirm = JOptionPane.showConfirmDialog(this,
                "This will re-fetch all cached entries for '" + selected.getName()
                        + "' from the database in the background. Continue?",
                "Refresh Cache", JOptionPane.OK_CANCEL_OPTION);
        if (confirm != JOptionPane.OK_OPTION) return;

        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                getServlet().refreshCache(selected.getId());
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    PlatformUI.MIRTH_FRAME.alertThrowable(
                            PlatformUI.MIRTH_FRAME, e, "Failed to start cache refresh");
                }
            }
        }.execute();

        JOptionPane.showMessageDialog(this,
                "Refresh started for '" + selected.getName()
                        + "'. Check the Event Log for completion.",
                "Refresh Cache", JOptionPane.INFORMATION_MESSAGE);
    }

    private CacheServletInterface getServlet() {
        if (servlet == null) {
            servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(CacheServletInterface.class);
        }
        return servlet;
    }
}
