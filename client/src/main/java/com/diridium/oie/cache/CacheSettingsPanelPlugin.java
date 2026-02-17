/* SPDX-License-Identifier: MPL-2.0
 * Copyright (c) 2026 Diridium Technologies Inc. */

package com.diridium.oie.cache;

import com.mirth.connect.plugins.SettingsPanelPlugin;
import com.mirth.connect.client.ui.AbstractSettingsPanel;

/**
 * Registers the Cache Manager settings panel in the OIE Administrator Settings tab.
 */
public class CacheSettingsPanelPlugin extends SettingsPanelPlugin {

    public CacheSettingsPanelPlugin(String name) {
        super(CacheServletInterface.PLUGIN_NAME);
        SerializationController.registerSerializableClasses();
    }

    @Override
    public String getPluginPointName() {
        return CacheServletInterface.PLUGIN_NAME;
    }

    @Override
    public AbstractSettingsPanel getSettingsPanel() {
        return new CacheSettingsPanel(CacheServletInterface.PLUGIN_NAME);
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public void reset() {
    }
}
