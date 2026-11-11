package ohi.andre.consolelauncher.tuils.interfaces;

import ohi.andre.consolelauncher.commands.CommandAbstraction;

public interface TuiCommandPlugin extends CommandAbstraction {
    /**
     * Called when the plugin is loaded.
     */
    void onLoaded();

    /**
     * Unique name for the plugin.
     */
    String getPluginName();
}
