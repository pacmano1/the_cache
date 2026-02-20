/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

import javax.swing.BorderFactory;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

/**
 * Modal dialog showing the result of a test query: key and value.
 * Value is displayed in a scrollable text area with word wrap;
 * right-click toggles word wrap.
 */
public class QueryTestResultDialog extends JDialog {

    public QueryTestResultDialog(JDialog parent, String key, String value) {
        super(parent, "Query Test Result", true);

        var content = new JPanel(new BorderLayout(0, 4));
        content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        // Key row at top
        var keyPanel = new JPanel(new MigLayout("insets 0", "[right]4[grow,fill]", "[]"));
        keyPanel.add(new JLabel("Key:"));
        var keyField = new JTextField(key);
        keyField.setEditable(false);
        keyPanel.add(keyField);
        content.add(keyPanel, BorderLayout.NORTH);

        // Value area fills remaining space
        var valuePanel = new JPanel(new BorderLayout(0, 2));
        valuePanel.add(new JLabel("Value:"), BorderLayout.NORTH);
        var valueArea = new JTextArea(value);
        valueArea.setEditable(false);
        valueArea.setLineWrap(true);
        valueArea.setWrapStyleWord(true);
        valueArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, valueArea.getFont().getSize()));
        valueArea.setCaretPosition(0);

        var wrapItem = new JMenuItem("Disable Word Wrap");
        var popup = new JPopupMenu();
        wrapItem.addActionListener(e -> {
            boolean wrapped = valueArea.getLineWrap();
            valueArea.setLineWrap(!wrapped);
            valueArea.setWrapStyleWord(!wrapped);
            wrapItem.setText(wrapped ? "Enable Word Wrap" : "Disable Word Wrap");
        });
        popup.add(wrapItem);

        valueArea.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) { maybeShowPopup(e); }

            @Override
            public void mouseReleased(MouseEvent e) { maybeShowPopup(e); }

            private void maybeShowPopup(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    popup.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });

        valuePanel.add(new JScrollPane(valueArea), BorderLayout.CENTER);
        content.add(valuePanel, BorderLayout.CENTER);

        setContentPane(content);
        setSize(500, 400);
        setMinimumSize(new Dimension(350, 250));
        setLocationRelativeTo(parent);

        DialogUtils.registerEscapeClose(this);
    }
}
