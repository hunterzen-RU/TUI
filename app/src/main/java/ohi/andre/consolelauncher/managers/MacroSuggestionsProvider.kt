package ohi.andre.consolelauncher.managers

import android.content.Context

/**
 * Registry and Tab-completion provider for MacroDroid macros.
 * Provides registered macro names alongside native T-UI commands for auto-completion.
 */
object MacroSuggestionsProvider {

    // Default registered macro set (can be queried or dynamically expanded)
    private val registeredMacros = mutableSetOf(
        "System Reboot",
        "Night Mode",
        "Toggle Wifi",
        "Mute Audio",
        "Take Photo",
        "Battery Saver",
        "Flashlight Toggle",
        "Volume Boost",
        "Location Sync",
        "Send Status SMS"
    )

    /**
     * Registers a new macro name dynamically
     */
    fun registerMacro(macroName: String) {
        if (macroName.isNotBlank()) {
            registeredMacros.add(macroName.trim())
        }
    }

    /**
     * Returns all registered macro names
     */
    fun getRegisteredMacroNames(): Set<String> {
        return registeredMacros
    }

    /**
     * Filter macro name suggestions based on user prefix query
     */
    fun getMacroSuggestions(query: String): List<String> {
        val cleanQuery = query.trim().lowercase()
        if (cleanQuery.isEmpty()) {
            return registeredMacros.toList()
        }
        return registeredMacros.filter { it.lowercase().startsWith(cleanQuery) || it.lowercase().contains(cleanQuery) }
    }
}
