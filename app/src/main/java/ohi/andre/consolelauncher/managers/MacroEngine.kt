package ohi.andre.consolelauncher.managers

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Inbuilt T-UI Native Macro Engine.
 * Allows users to record, configure, develop, and execute multi-command automation sequences
 * stored in local JSON configuration (`macros.json`).
 */
class MacroEngine private constructor(private val context: Context) {

    companion object {
        private const val TAG = "MacroEngine"
        const val MACROS_FILE_NAME = "macros.json"

        @Volatile
        private var instance: MacroEngine? = null

        fun getInstance(context: Context): MacroEngine {
            return instance ?: synchronized(this) {
                instance ?: MacroEngine(context.applicationContext).also { instance = it }
            }
        }
    }

    data class MacroDefinition(
        val name: String,
        var description: String = "",
        val commands: MutableList<String> = mutableListOf(),
        var author: String = "user",
        var enabled: Boolean = true,
        val createdAt: Long = System.currentTimeMillis(),
        var lastExecuted: Long = 0L,
        var runCount: Int = 0
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            json.put("name", name)
            json.put("description", description)
            val arr = JSONArray()
            commands.forEach { arr.put(it) }
            json.put("commands", arr)
            json.put("author", author)
            json.put("enabled", enabled)
            json.put("created_at", createdAt)
            json.put("last_executed", lastExecuted)
            json.put("run_count", runCount)
            return json
        }

        companion object {
            fun fromJson(json: JSONObject): MacroDefinition {
                val name = json.optString("name", "")
                val desc = json.optString("description", "")
                val commands = mutableListOf<String>()
                val arr = json.optJSONArray("commands")
                if (arr != null) {
                    for (i in 0 until arr.length()) {
                        commands.add(arr.getString(i))
                    }
                }
                val author = json.optString("author", "user")
                val enabled = json.optBoolean("enabled", true)
                val createdAt = json.optLong("created_at", System.currentTimeMillis())
                val lastExecuted = json.optLong("last_executed", 0L)
                val runCount = json.optInt("run_count", 0)
                return MacroDefinition(name, desc, commands, author, enabled, createdAt, lastExecuted, runCount)
            }
        }
    }

    private val macrosMap = ConcurrentHashMap<String, MacroDefinition>()
    private var isRecordingState = false
    private var activeRecordingName: String? = null
    private var activeRecordingDesc: String = ""
    private val recordingBuffer = mutableListOf<String>()

    init {
        loadMacros()
    }

    fun getMacrosFile(): File {
        val tuiFolder = Tuils.getFolder()
        return if (tuiFolder != null && tuiFolder.exists()) {
            File(tuiFolder, MACROS_FILE_NAME)
        } else {
            File(context.filesDir, MACROS_FILE_NAME)
        }
    }

    @Synchronized
    fun loadMacros() {
        macrosMap.clear()
        val file = getMacrosFile()
        if (!file.exists()) {
            prefillDefaultMacros()
            saveMacros()
            return
        }

        try {
            val content = file.readText()
            val root = JSONObject(content)
            val arr = root.optJSONArray("macros")
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val def = MacroDefinition.fromJson(obj)
                    if (def.name.isNotBlank()) {
                        macrosMap[def.name.lowercase()] = def
                        MacroSuggestionsProvider.registerMacro(def.name)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load macros.json", e)
            prefillDefaultMacros()
        }
    }

    @Synchronized
    fun saveMacros(): Boolean {
        val file = getMacrosFile()
        return try {
            val root = JSONObject()
            val arr = JSONArray()
            macrosMap.values.forEach { arr.put(it.toJson()) }
            root.put("macros", arr)
            root.put("version", "1.0")
            root.put("updated_at", System.currentTimeMillis())

            FileOutputStream(file).use { fos ->
                fos.write(root.toString(2).toByteArray())
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save macros.json", e)
            false
        }
    }

    private fun prefillDefaultMacros() {
        val nightMacro = MacroDefinition(
            name = "night",
            description = "Night mode automation (dim brightness & notification)",
            commands = mutableListOf("brightness 15", "notify \"Night Mode\" \"Device dimmed for night\"", "toast Night mode active")
        )
        val sysinfoMacro = MacroDefinition(
            name = "syscheck",
            description = "Run full system telemetry and report battery",
            commands = mutableListOf("sys -graph", "echo Battery status: {{battery_status}} ({{battery_level}}%)")
        )
        val devCleanMacro = MacroDefinition(
            name = "clean",
            description = "Clean terminal output and execution history",
            commands = mutableListOf("clear", "toast Terminal cleaned")
        )

        macrosMap[nightMacro.name.lowercase()] = nightMacro
        macrosMap[sysinfoMacro.name.lowercase()] = sysinfoMacro
        macrosMap[devCleanMacro.name.lowercase()] = devCleanMacro

        MacroSuggestionsProvider.registerMacro(nightMacro.name)
        MacroSuggestionsProvider.registerMacro(sysinfoMacro.name)
        MacroSuggestionsProvider.registerMacro(devCleanMacro.name)
    }

    // --- Recording Engine ---

    @Synchronized
    fun startRecording(name: String, description: String = ""): Boolean {
        val clean = name.trim().lowercase()
        if (clean.isBlank()) return false

        isRecordingState = true
        activeRecordingName = clean
        activeRecordingDesc = description
        recordingBuffer.clear()
        return true
    }

    fun isRecording(): Boolean = isRecordingState

    fun getRecordingName(): String? = activeRecordingName

    fun getRecordedBuffer(): List<String> = ArrayList(recordingBuffer)

    @Synchronized
    fun recordCommand(rawCommand: String) {
        if (!isRecordingState || rawCommand.isBlank()) return

        val trimmed = rawCommand.trim()
        val firstToken = trimmed.split("\\s+".toRegex())[0].lowercase()

        // Ignore control commands during recording
        if (firstToken == "macro" || firstToken == "macros") {
            if (trimmed.contains("-stop") || trimmed.contains("-cancel") || trimmed.contains("-status")) {
                return
            }
        }

        recordingBuffer.add(trimmed)
        Log.d(TAG, "Recorded command into macro '$activeRecordingName': $trimmed")
    }

    @Synchronized
    fun stopRecording(): MacroDefinition? {
        if (!isRecordingState) return null
        val name = activeRecordingName ?: return null

        val macro = MacroDefinition(
            name = name,
            description = activeRecordingDesc.ifBlank { "Recorded on " + java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date()) },
            commands = ArrayList(recordingBuffer),
            author = VariablesManager.getVariable("user").ifBlank { "user" }
        )

        macrosMap[name.lowercase()] = macro
        MacroSuggestionsProvider.registerMacro(name)
        saveMacros()

        isRecordingState = false
        activeRecordingName = null
        activeRecordingDesc = ""
        recordingBuffer.clear()

        return macro
    }

    @Synchronized
    fun cancelRecording(): Boolean {
        if (!isRecordingState) return false
        isRecordingState = false
        activeRecordingName = null
        activeRecordingDesc = ""
        recordingBuffer.clear()
        return true
    }

    // --- Macro Management ---

    fun hasMacro(name: String): Boolean {
        return macrosMap.containsKey(name.trim().lowercase())
    }

    fun getMacro(name: String): MacroDefinition? {
        return macrosMap[name.trim().lowercase()]
    }

    fun getAllMacros(): List<MacroDefinition> {
        return macrosMap.values.sortedBy { it.name }
    }

    fun addMacro(macro: MacroDefinition): Boolean {
        if (macro.name.isBlank()) return false
        macrosMap[macro.name.trim().lowercase()] = macro
        MacroSuggestionsProvider.registerMacro(macro.name.trim())
        return saveMacros()
    }

    fun removeMacro(name: String): Boolean {
        val removed = macrosMap.remove(name.trim().lowercase())
        if (removed != null) {
            saveMacros()
            return true
        }
        return false
    }

    // --- Execution Engine ---

    fun executeMacro(
        macroName: String,
        positionalArgs: List<String> = emptyList(),
        namedVariables: Map<String, Any> = emptyMap(),
        onOutput: ((String) -> Unit)? = null
    ): Boolean {
        val clean = macroName.trim().lowercase()
        val macro = macrosMap[clean] ?: return false

        if (!macro.enabled) {
            onOutput?.invoke("Macro '${macro.name}' is currently disabled.")
            return false
        }

        macro.lastExecuted = System.currentTimeMillis()
        macro.runCount++
        saveMacros()

        Thread {
            try {
                onOutput?.invoke("▶ Executing macro: ${macro.name} (${macro.commands.size} steps)")
                val historyDb = MacroHistoryDbHelper(context)

                for ((idx, rawStep) in macro.commands.withIndex()) {
                    val stepNum = idx + 1
                    var evaluatedStep = rawStep

                    // 1. Substitute positional arguments ($1, $2, $*, etc.)
                    for (i in positionalArgs.indices) {
                        evaluatedStep = evaluatedStep.replace("\$${i + 1}", positionalArgs[i])
                    }
                    evaluatedStep = evaluatedStep.replace("\$*", positionalArgs.joinToString(" "))
                    evaluatedStep = evaluatedStep.replace("\$@", positionalArgs.joinToString(" "))

                    // 2. Substitute named variables (var1="val" -> {{var1}} or $var1)
                    namedVariables.forEach { (k, v) ->
                        evaluatedStep = evaluatedStep.replace("{{$k}}", v.toString())
                        evaluatedStep = evaluatedStep.replace("\$$k", v.toString())
                    }

                    // 3. Substitute global predefined T-UI variables ({{battery}}, {{uptime}}, etc.)
                    evaluatedStep = VariablesManager.resolveVariables(context, evaluatedStep, null)

                    val trimmed = evaluatedStep.trim()
                    if (trimmed.isEmpty()) continue

                    // Support sleep delay command
                    if (trimmed.startsWith("sleep ", ignoreCase = true)) {
                        val parts = trimmed.split("\\s+".toRegex())
                        val ms = parts.getOrNull(1)?.toLongOrNull() ?: 1000L
                        onOutput?.invoke("⏱ Sleeping for ${ms}ms...")
                        Thread.sleep(ms)
                        continue
                    }

                    // Direct echo support
                    if (trimmed.startsWith("echo ", ignoreCase = true)) {
                        val msg = trimmed.substring(5).trim()
                        onOutput?.invoke(msg)
                        continue
                    }

                    // Dispatch command to MainManager executor via local broadcast
                    Log.d(TAG, "Macro '${macro.name}' step $stepNum: $trimmed")
                    val intent = Intent(MainManager.ACTION_EXEC).apply {
                        putExtra(MainManager.CMD, trimmed as String)
                        putExtra(MainManager.NEED_WRITE_INPUT, false)
                    }
                    LocalBroadcastManager.getInstance(context).sendBroadcast(intent)

                    // Small breathing room between steps to let terminal process
                    Thread.sleep(150)
                }

                historyDb.logExecution(macro.name, namedVariables, "COMPLETED")
                onOutput?.invoke("✓ Macro '${macro.name}' completed successfully.")
            } catch (e: Exception) {
                Log.e(TAG, "Error running macro '${macro.name}'", e)
                onOutput?.invoke("✗ Macro '${macro.name}' failed: ${e.localizedMessage}")
                MacroHistoryDbHelper(context).logExecution(macro.name, namedVariables, "ERROR: ${e.message}")
            }
        }.start()

        return true
    }
}
