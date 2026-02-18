/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

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
 * Modal dialog showing all fields for a single cache entry.
 * Value is displayed in a scrollable text area with word wrap on by default;
 * right-click toggles word wrap.
 */
public class EntryDetailDialog extends JDialog {

    public EntryDetailDialog(JDialog parent, CacheEntry entry) {
        super(parent, "Entry Detail", true);

        var panel = new JPanel(new MigLayout("insets 8, fill", "[right][grow,fill]", "[][grow,fill][][]"));

        panel.add(new JLabel("Key:"));
        var keyField = readOnlyField(entry.getKey());
        panel.add(keyField, "wrap");

        panel.add(new JLabel("Value:"), "top");
        var valueArea = new JTextArea(entry.getValue());
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

        panel.add(new JScrollPane(valueArea), "grow, push, wrap");

        panel.add(new JLabel("Loaded At:"));
        var loadedAt = DialogUtils.formatTimestamp(entry.getLoadedAtMillis());
        panel.add(readOnlyField(loadedAt), "wrap");

        panel.add(new JLabel("Hits:"));
        panel.add(readOnlyField(String.valueOf(entry.getHitCount())), "wrap");

        setContentPane(panel);
        setSize(500, 400);
        setMinimumSize(new Dimension(350, 250));
        setLocationRelativeTo(parent);

        DialogUtils.registerEscapeClose(this);
    }

    private static JTextField readOnlyField(String text) {
        var field = new JTextField(text);
        field.setEditable(false);
        return field;
    }
}
