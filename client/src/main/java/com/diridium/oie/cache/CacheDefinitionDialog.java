/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.DefaultComboBoxModel;
import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

import com.mirth.connect.client.core.ClientException;
import com.mirth.connect.client.ui.PlatformUI;
import com.mirth.connect.client.ui.components.MirthComboBox;
import com.mirth.connect.client.ui.components.MirthPasswordField;
import com.mirth.connect.client.ui.components.MirthTextField;
import com.mirth.connect.client.ui.components.rsta.MirthRTextScrollPane;
import com.mirth.connect.model.DriverInfo;
import com.mirth.connect.model.codetemplates.ContextType;

import net.miginfocom.swing.MigLayout;

import org.fife.ui.rsyntaxtextarea.SyntaxConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Dialog for creating or editing a cache definition.
 */
public class CacheDefinitionDialog extends JDialog {

    private static final Logger log = LoggerFactory.getLogger(CacheDefinitionDialog.class);

    private static final String DRIVER_DEFAULT = "Please Select One";
    private static final String DRIVER_CUSTOM = "Custom";

    private final CacheDefinition definition;
    private boolean saved = false;

    // General
    private MirthTextField nameField;
    private JCheckBox enabledCheckbox;

    // Database connection
    private MirthComboBox<DriverInfo> driverCombo;
    private MirthTextField driverClassField;
    private MirthTextField urlField;
    private JButton insertUrlTemplateButton;
    private MirthTextField usernameField;
    private MirthPasswordField passwordField;
    private JButton testConnectionButton;
    private JButton testQueryButton;

    // Query
    private MirthRTextScrollPane queryPane;
    private MirthTextField keyColumnField;
    private MirthTextField valueColumnField;

    // Cache settings
    private MirthTextField maxSizeField;
    private MirthTextField evictionField;
    private MirthTextField maxConnectionsField;

    private JButton saveButton;
    private JButton cancelButton;

    private List<DriverInfo> drivers = new ArrayList<>();
    private final AtomicBoolean driverAdjusting = new AtomicBoolean(false);

    public CacheDefinitionDialog(JFrame parent, CacheDefinition existing) {
        this(parent, existing, existing == null ? "New Cache Definition" : "Edit Cache Definition");
    }

    public CacheDefinitionDialog(JFrame parent, CacheDefinition existing, String title) {
        super(parent, title, true);
        this.definition = existing != null ? existing : new CacheDefinition();

        loadDrivers();
        initComponents();
        initLayout();
        populateFields();

        setSize(650, 650);
        setMinimumSize(new Dimension(550, 550));
        setLocationRelativeTo(parent);

        DialogUtils.registerEscapeClose(this);
        getRootPane().setDefaultButton(saveButton);
    }

    public boolean isSaved() {
        return saved;
    }

    public CacheDefinition getDefinition() {
        return definition;
    }

    // ---- Component initialization ----

    private void initComponents() {
        nameField = new MirthTextField();
        enabledCheckbox = new JCheckBox("Enabled", true);

        // Driver combo with display name renderer
        driverCombo = new MirthComboBox<>();
        driverCombo.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                    int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (value instanceof DriverInfo di) {
                    setText(di.getName());
                }
                return this;
            }
        });
        driverCombo.addActionListener(e -> {
            if (!driverAdjusting.getAndSet(true)) {
                try {
                    updateDriverFieldFromCombo();
                } finally {
                    driverAdjusting.set(false);
                }
            }
        });
        updateDriverComboModel();

        driverClassField = new MirthTextField();
        urlField = new MirthTextField();
        insertUrlTemplateButton = new JButton("Insert URL Template");
        insertUrlTemplateButton.addActionListener(e -> onInsertUrlTemplate());
        usernameField = new MirthTextField();
        passwordField = new MirthPasswordField();
        testConnectionButton = new JButton("Test Connection");
        testConnectionButton.addActionListener(e -> onTestConnection());
        testQueryButton = new JButton("Test Query");
        testQueryButton.addActionListener(e -> onTestQuery());

        // Query editor with SQL syntax highlighting
        queryPane = new MirthRTextScrollPane(ContextType.GLOBAL_DEPLOY, true,
                SyntaxConstants.SYNTAX_STYLE_SQL, false);

        keyColumnField = new MirthTextField();
        valueColumnField = new MirthTextField();

        maxSizeField = new MirthTextField();
        evictionField = new MirthTextField();
        maxConnectionsField = new MirthTextField();

        saveButton = new JButton("Save");
        saveButton.addActionListener(e -> onSave());

        cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());
    }

    private void initLayout() {
        var content = new JPanel(new MigLayout("insets 10, fill", "[][grow, fill]", ""));

        content.add(new JLabel("Name:"));
        content.add(nameField, "wrap");

        content.add(new JLabel(""));
        content.add(enabledCheckbox, "wrap");

        // Database connection section
        content.add(new JLabel("Driver:"));
        content.add(driverCombo, "wrap");

        content.add(new JLabel("Driver Class:"));
        content.add(driverClassField, "wrap");

        content.add(new JLabel("URL:"));
        var urlPanel = new JPanel(new MigLayout("insets 0, fill", "[grow, fill][]", ""));
        urlPanel.add(urlField, "grow");
        urlPanel.add(insertUrlTemplateButton);
        content.add(urlPanel, "wrap");

        content.add(new JLabel("Username:"));
        content.add(usernameField, "wrap");

        content.add(new JLabel("Password:"));
        content.add(passwordField, "wrap");

        content.add(new JLabel(""));
        var testPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        testPanel.add(testConnectionButton);
        testPanel.add(testQueryButton);
        content.add(testPanel, "wrap");

        // Query section
        content.add(new JLabel("SQL:"), "top");
        content.add(queryPane, "h 100:140:200, wrap");

        content.add(new JLabel("Key Column:"));
        content.add(keyColumnField, "wrap");

        content.add(new JLabel("Value Column:"));
        content.add(valueColumnField, "wrap");

        // Cache settings section
        content.add(new JLabel("Max Size:"));
        content.add(maxSizeField, "wrap");

        content.add(new JLabel("Eviction (min):"));
        content.add(evictionField, "wrap");

        content.add(new JLabel("Max Connections:"));
        content.add(maxConnectionsField, "wrap");

        var buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(saveButton);
        buttonPanel.add(cancelButton);

        setLayout(new BorderLayout());
        add(content, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    // ---- Population ----

    private void populateFields() {
        if (definition.getName() != null) nameField.setText(definition.getName());
        enabledCheckbox.setSelected(definition.isEnabled());

        if (definition.getDriver() != null) {
            driverClassField.setText(definition.getDriver());
            updateDriverComboFromField();
        }
        if (definition.getUrl() != null) urlField.setText(definition.getUrl());
        if (definition.getUsername() != null) usernameField.setText(definition.getUsername());
        if (definition.getPassword() != null) passwordField.setText(definition.getPassword());

        if (definition.getQuery() != null) queryPane.setText(definition.getQuery());

        if (definition.getKeyColumn() != null) keyColumnField.setText(definition.getKeyColumn());
        if (definition.getValueColumn() != null) valueColumnField.setText(definition.getValueColumn());
        if (definition.getMaxSize() > 0) maxSizeField.setText(String.valueOf(definition.getMaxSize()));
        if (definition.getEvictionDurationMinutes() > 0) evictionField.setText(String.valueOf(definition.getEvictionDurationMinutes()));
        if (definition.getMaxConnections() > 0) maxConnectionsField.setText(String.valueOf(definition.getMaxConnections()));
    }

    // ---- Driver handling ----

    private void loadDrivers() {
        try {
            drivers = new ArrayList<>(PlatformUI.MIRTH_FRAME.mirthClient.getDatabaseDrivers());
        } catch (Exception e) {
            log.error("Failed to load database drivers", e);
            drivers = new ArrayList<>(DriverInfo.getDefaultDrivers());
        }
        fixDriversList();
    }

    private void fixDriversList() {
        if (drivers.isEmpty()) {
            drivers = new ArrayList<>(DriverInfo.getDefaultDrivers());
        }
        if (!Objects.equals(drivers.get(0).getName(), DRIVER_DEFAULT)) {
            drivers.add(0, new DriverInfo(DRIVER_DEFAULT, "", "", ""));
        }
        if (!Objects.equals(drivers.get(drivers.size() - 1).getName(), DRIVER_CUSTOM)) {
            drivers.add(new DriverInfo(DRIVER_CUSTOM, "", "", ""));
        }
    }

    private void updateDriverComboModel() {
        driverAdjusting.set(true);
        try {
            driverCombo.setModel(new DefaultComboBoxModel<>(drivers.toArray(new DriverInfo[0])));
        } finally {
            driverAdjusting.set(false);
        }
    }

    private void updateDriverFieldFromCombo() {
        var selected = (DriverInfo) driverCombo.getSelectedItem();
        if (selected != null && !Objects.equals(selected.getName(), DRIVER_CUSTOM)) {
            driverClassField.setText(selected.getClassName());
        }
    }

    private void updateDriverComboFromField() {
        driverAdjusting.set(true);
        try {
            var className = driverClassField.getText();
            DriverInfo found = null;

            for (int i = 0; i < driverCombo.getModel().getSize(); i++) {
                var di = driverCombo.getModel().getElementAt(i);
                if (Objects.equals(className, di.getClassName())) {
                    found = di;
                    break;
                }
                var alts = di.getAlternativeClassNames();
                if (alts != null) {
                    for (var alt : alts) {
                        if (Objects.equals(className, alt)) {
                            found = di;
                            break;
                        }
                    }
                    if (found != null) break;
                }
            }

            if (found != null) {
                driverCombo.setSelectedItem(found);
            } else {
                // Select "Custom" entry
                driverCombo.setSelectedIndex(driverCombo.getItemCount() - 1);
            }
        } finally {
            driverAdjusting.set(false);
        }
    }

    private void onInsertUrlTemplate() {
        var selected = (DriverInfo) driverCombo.getSelectedItem();
        if (selected == null || selected.getTemplate() == null || selected.getTemplate().isEmpty()) {
            return;
        }
        if (urlField.getText() != null && !urlField.getText().isEmpty()) {
            int result = JOptionPane.showConfirmDialog(this,
                    "Replace current URL with the template?",
                    "Insert URL Template", JOptionPane.YES_NO_OPTION);
            if (result != JOptionPane.YES_OPTION) return;
        }
        urlField.setText(selected.getTemplate());
    }

    private CacheDefinition buildDefinitionFromFields() {
        var def = new CacheDefinition();
        def.setDriver(driverClassField.getText().trim());
        def.setUrl(urlField.getText().trim());
        def.setUsername(usernameField.getText().trim());
        def.setPassword(new String(passwordField.getPassword()));
        def.setName(nameField.getText().trim());
        return def;
    }

    private void onTestConnection() {
        var def = buildDefinitionFromFields();

        testConnectionButton.setEnabled(false);
        testConnectionButton.setText("Testing...");

        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                var servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(CacheServletInterface.class);
                return servlet.testConnectionInline(def);
            }

            @Override
            protected void done() {
                testConnectionButton.setEnabled(true);
                testConnectionButton.setText("Test Connection");
                try {
                    var result = get();
                    JOptionPane.showMessageDialog(CacheDefinitionDialog.this, result,
                            "Connection Test", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    var cause = ex.getCause() instanceof ClientException ce ? ce.getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(CacheDefinitionDialog.this,
                            "Connection failed: " + cause,
                            "Connection Test", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onTestQuery() {
        var sampleKey = JOptionPane.showInputDialog(this, "Enter a sample key:", "Test Query", JOptionPane.PLAIN_MESSAGE);
        if (sampleKey == null || sampleKey.trim().isEmpty()) {
            return;
        }

        var def = buildDefinitionFromFields();
        def.setQuery(queryPane.getText().trim());
        def.setKeyColumn(keyColumnField.getText().trim());
        def.setValueColumn(valueColumnField.getText().trim());

        testQueryButton.setEnabled(false);
        testQueryButton.setText("Testing...");

        var key = sampleKey.trim();
        new SwingWorker<String, Void>() {
            @Override
            protected String doInBackground() throws Exception {
                var servlet = PlatformUI.MIRTH_FRAME.mirthClient.getServlet(CacheServletInterface.class);
                return servlet.testQueryInline(def, key);
            }

            @Override
            protected void done() {
                testQueryButton.setEnabled(true);
                testQueryButton.setText("Test Query");
                try {
                    var result = get();
                    JOptionPane.showMessageDialog(CacheDefinitionDialog.this, result,
                            "Query Test", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    var cause = ex.getCause() instanceof ClientException ce ? ce.getMessage() : ex.getMessage();
                    JOptionPane.showMessageDialog(CacheDefinitionDialog.this,
                            "Query test failed: " + cause,
                            "Query Test", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    // ---- Save / validation ----

    private void onSave() {
        var name = nameField.getText().trim();
        if (name.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Name is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            nameField.requestFocus();
            return;
        }

        var driverClass = driverClassField.getText().trim();
        if (driverClass.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Driver class is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            driverClassField.requestFocus();
            return;
        }

        var url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "JDBC URL is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            urlField.requestFocus();
            return;
        }

        var query = queryPane.getText().trim();
        if (query.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Query is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            queryPane.getTextArea().requestFocus();
            return;
        }

        var valueColumn = valueColumnField.getText().trim();
        if (valueColumn.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Value Column is required.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            valueColumnField.requestFocus();
            return;
        }

        long maxSize;
        try {
            maxSize = maxSizeField.getText().trim().isEmpty() ? 10000 : Long.parseLong(maxSizeField.getText().trim());
            if (maxSize < 0) {
                JOptionPane.showMessageDialog(this, "Max Size must be non-negative.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                maxSizeField.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Max Size must be a number.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            maxSizeField.requestFocus();
            return;
        }

        long eviction;
        try {
            eviction = evictionField.getText().trim().isEmpty() ? 60 : Long.parseLong(evictionField.getText().trim());
            if (eviction < 0) {
                JOptionPane.showMessageDialog(this, "Eviction duration must be non-negative.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                evictionField.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Eviction duration must be a number.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            evictionField.requestFocus();
            return;
        }

        int maxConnections;
        try {
            maxConnections = maxConnectionsField.getText().trim().isEmpty() ? 5 : Integer.parseInt(maxConnectionsField.getText().trim());
            if (maxConnections < 1) {
                JOptionPane.showMessageDialog(this, "Max Connections must be at least 1.", "Validation Error", JOptionPane.ERROR_MESSAGE);
                maxConnectionsField.requestFocus();
                return;
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Max Connections must be a number.", "Validation Error", JOptionPane.ERROR_MESSAGE);
            maxConnectionsField.requestFocus();
            return;
        }

        definition.setName(name);
        definition.setEnabled(enabledCheckbox.isSelected());
        definition.setDriver(driverClass);
        definition.setUrl(url);
        definition.setUsername(usernameField.getText().trim());
        definition.setPassword(new String(passwordField.getPassword()));
        definition.setQuery(query);
        definition.setKeyColumn(keyColumnField.getText().trim());
        definition.setValueColumn(valueColumnField.getText().trim());
        definition.setMaxSize(maxSize);
        definition.setEvictionDurationMinutes(eviction);
        definition.setMaxConnections(maxConnections);

        saved = true;
        dispose();
    }

}
