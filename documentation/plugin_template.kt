package ohi.andre.tui.plugin

import android.content.Context
import ohi.andre.consolelauncher.commands.ExecutePack
import ohi.andre.consolelauncher.tuils.interfaces.TuiCommandPlugin

/**
 * Boilerplate Kotlin Template for TUI Plugin
 * Structure:
 * - Package MUST be ohi.andre.tui.plugin
 * - Class MUST be PluginEntry
 * - MUST implement TuiCommandPlugin
 */
class PluginEntry : TuiCommandPlugin {

    override fun onLoaded() {
        // This runs once when TUI loads the plugin
        // Use for one-time setup
    }

    override fun getPluginName(): String {
        // This is the command name used in the terminal (e.g. $ mycmd)
        return "myplugin"
    }

    override fun exec(pack: ExecutePack): String? {
        // This runs when the user types your command
        // pack.getString() gets the arguments
        val args = pack.getString()
        
        return "Hello from Kotlin Plugin! Args: $args"
    }

    override fun helpRes(): Int = 0 // Optional: String resource ID for help

    override fun argType(): IntArray = intArrayOf() // Define expected arg types if needed

    override fun priority(): Int = 5

    override fun onNotArgEnough(pack: ExecutePack, nArgs: Int): String? = "Not enough arguments."

    override fun onArgNotFound(pack: ExecutePack, index: Int): String? = "Argument not found."
}
