package ohi.andre.consolelauncher.managers

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SmsManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import ohi.andre.consolelauncher.MainManager
import ohi.andre.consolelauncher.R
import ohi.andre.consolelauncher.commands.main.MainPack
import ohi.andre.consolelauncher.managers.cron.CronExpression
import ohi.andre.consolelauncher.managers.cron.CronWorker
import ohi.andre.consolelauncher.managers.cron.ScheduledTasksDbHelper
import ohi.andre.consolelauncher.tuils.Tuils
import java.io.File
import java.util.Date
import java.util.regex.Pattern

/**
 * Hybrid Command Interpreter for T-UI Launcher (API 26+)
 * Handlers:
 * 1. Cron / Scheduling Commands (`schedule "expr" <action>` or `cron "expr" <action>`)
 * 2. MacroDroid Triggering with variable extraction (`macro "name" var1=val`)
 * 3. Specific Actions live execution (`sms`, `launch`, `notify`)
 * 4. SQLite-backed Alias system (`alias -add <name> <cmd>`, etc.)
 * 5. Native T-UI Launcher commands
 * 6. Fallback Termux / Shell PTY execution
 */
class HybridCommandInterpreter(
    private val context: Context,
    private val mainPack: MainPack? = null
) {

    companion object {
        private const val TAG = "HybridInterpreter"

        const val CHANNEL_ID = "tui_cron_notifications"
        const val CHANNEL_NAME = "T-UI Scheduled Notifications"

        // MacroDroid Intent Constants
        const val MACRODROID_PACKAGE = "com.arlosoft.macrodroid"
        const val ACTION_RUN_MACRO = "com.arlosoft.macrodroid.macro.RUN_MACRO"
        const val EXTRA_MACRO_NAME = "macro_name"
        const val EXTRA_MACRO_NAME_ALT = "com.arlosoft.macrodroid.MACRO_NAME"

        // Regex patterns
        private val SCHEDULE_PATTERN = Pattern.compile(
            "^(?:schedule|cron)\\s+(?:\"([^\"]+)\"|'([^']+)'|(\\S+\\s+\\S+\\s+\\S+\\s+\\S+\\s+\\S+))\\s+(.+)$",
            Pattern.CASE_INSENSITIVE
        )

        private val MACRO_PATTERN = Pattern.compile(
            "^macro\\s+(\"(?:[^\"]|\\\\\")*\"|\\S+)(?:\\s+(.*))?$",
            Pattern.CASE_INSENSITIVE
        )

        private val KV_PATTERN = Pattern.compile("(\\w+)=(?:\"([^\"]*)\"|'([^']*)'|(\\S+))")

        // Action patterns
        private val SMS_PATTERN = Pattern.compile(
            "^sms\\s+(\\+?[0-9a-zA-Z*#]+)\\s+(?:\"([^\"]*)\"|'([^']*)'|(.+))$",
            Pattern.CASE_INSENSITIVE
        )

        private val LAUNCH_PATTERN = Pattern.compile(
            "^launch\\s+(\\S+)(?:\\s+(\\S+))?$",
            Pattern.CASE_INSENSITIVE
        )

        private val NOTIFY_PATTERN = Pattern.compile(
            "^notify\\s+(?:\"([^\"]*)\"|'([^']*)'|(\\S+))\\s+(?:\"([^\"]*)\"|'([^']*)'|(.+))$",
            Pattern.CASE_INSENSITIVE
        )
    }

    sealed class CommandResult {
        data class Scheduled(
            val tag: String,
            val cronExpr: String,
            val action: String,
            val nextRunMillis: Long
        ) : CommandResult()

        data class ActionExecuted(
            val actionType: String,
            val output: String
        ) : CommandResult()

        data class MacroTriggered(
            val macroName: String,
            val variables: Map<String, Any>,
            val intentSent: Boolean
        ) : CommandResult()

        data class NativeCommand(
            val commandName: String,
            val rawInput: String
        ) : CommandResult()

        data class ShellExecution(
            val command: String,
            val environment: Map<String, String>
        ) : CommandResult()
    }

    /**
     * Sequential input handler:
     * 1. Schedule / Cron interception
     * 2. MacroDroid Trigger check
     * 3. Specific Actions live check (sms, launch, notify)
     * 4. SQLite Alias resolution
     * 5. Native T-UI command check
     * 6. Termux PTY Shell fallback
     */
    fun handleInput(input: String): CommandResult {
        val trimmedInput = input.trim()
        if (trimmedInput.isEmpty()) {
            return CommandResult.ShellExecution("", getTermuxEnvironment())
        }

        // 1. Schedule / Cron Interception
        val schedMatcher = SCHEDULE_PATTERN.matcher(trimmedInput)
        if (schedMatcher.matches()) {
            val cronExpr = schedMatcher.group(1) ?: schedMatcher.group(2) ?: schedMatcher.group(3) ?: ""
            val actionPayload = schedMatcher.group(4)?.trim() ?: ""

            return try {
                val tag = "job_" + System.currentTimeMillis() % 100000
                val nextRun = CronWorker.scheduleNextRun(context, tag, cronExpr.trim(), actionPayload)
                CommandResult.Scheduled(tag, cronExpr.trim(), actionPayload, nextRun)
            } catch (e: Exception) {
                CommandResult.ActionExecuted("schedule", "Cron parse error: ${e.localizedMessage}")
            }
        }

        // 2. MacroDroid Execution check
        val macroMatcher = MACRO_PATTERN.matcher(trimmedInput)
        if (macroMatcher.matches()) {
            val rawMacroName = macroMatcher.group(1) ?: ""
            val rawVars = macroMatcher.group(2) ?: ""

            val macroName = if (rawMacroName.startsWith("\"") && rawMacroName.endsWith("\"") && rawMacroName.length >= 2) {
                rawMacroName.substring(1, rawMacroName.length - 1).replace("\\\"", "\"")
            } else {
                rawMacroName
            }

            val variables = parseVariables(rawVars)
            val success = fireMacroDroidIntent(macroName, variables)
            return CommandResult.MacroTriggered(macroName, variables, success)
        }

        // 3. Live Actions Check (sms, launch, notify)
        if (isActionCommand(trimmedInput)) {
            val actionOutput = executeAction(trimmedInput)
            val actionName = trimmedInput.split("\\s+".toRegex())[0].lowercase()
            return CommandResult.ActionExecuted(actionName, actionOutput)
        }

        // 4. SQLite Alias resolution
        val aliasDb = AliasDbHelper(context)
        val firstWord = trimmedInput.split("\\s+".toRegex()).firstOrNull() ?: ""
        val dbAliasVal = aliasDb.getAlias(firstWord)
        if (dbAliasVal != null) {
            val remainder = if (trimmedInput.length > firstWord.length) {
                trimmedInput.substring(firstWord.length).trim()
            } else ""
            val expanded = if (remainder.isNotEmpty()) "$dbAliasVal $remainder" else dbAliasVal
            return handleInput(expanded)
        }

        // 5. Native T-UI Commands check
        if (isNativeTUICommand(firstWord)) {
            return CommandResult.NativeCommand(firstWord, trimmedInput)
        }

        // 6. Fallback Termux Shell Execution
        val env = getTermuxEnvironment()
        return CommandResult.ShellExecution(trimmedInput, env)
    }

    private fun isActionCommand(input: String): Boolean {
        val lower = input.lowercase()
        return lower.startsWith("sms ") || lower.startsWith("launch ") || lower.startsWith("notify ")
    }

    /**
     * Executes an action string across supported domains:
     * - sms <number> "<message>"
     * - launch <package> [<activity>]
     * - notify "<title>" "<message>"
     * - shell <cmd>
     * - Native T-UI command
     */
    fun executeAction(action: String): String {
        val trimmed = action.trim()

        // 1. Send SMS Command
        val smsMatcher = SMS_PATTERN.matcher(trimmed)
        if (smsMatcher.matches()) {
            val phone = smsMatcher.group(1) ?: ""
            val message = smsMatcher.group(2) ?: smsMatcher.group(3) ?: smsMatcher.group(4) ?: ""
            return sendSms(phone, message)
        }

        // 2. Launch App Command
        val launchMatcher = LAUNCH_PATTERN.matcher(trimmed)
        if (launchMatcher.matches()) {
            val pkg = launchMatcher.group(1) ?: ""
            val activity = launchMatcher.group(2)
            return launchApp(pkg, activity)
        }

        // 3. Post Notification Command
        val notifyMatcher = NOTIFY_PATTERN.matcher(trimmed)
        if (notifyMatcher.matches()) {
            val title = notifyMatcher.group(1) ?: notifyMatcher.group(2) ?: notifyMatcher.group(3) ?: "T-UI"
            val body = notifyMatcher.group(4) ?: notifyMatcher.group(5) ?: notifyMatcher.group(6) ?: ""
            return postNotification(title, body)
        }

        // 4. Shell / Fallback execution
        return if (trimmed.startsWith("shell ", ignoreCase = true)) {
            val rawCmd = trimmed.substring(6).trim()
            TermuxSessionManager(context).executeDirectShellCommand(rawCmd, 15)
        } else {
            // Forward to T-UI execution loop
            val intent = Intent(MainManager.ACTION_EXEC).apply {
                putExtra(MainManager.CMD, trimmed)
                putExtra(MainManager.CMD_COUNT, MainManager.commandCount)
            }
            LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
            "Dispatched command to T-UI interpreter: $trimmed"
        }
    }

    private fun sendSms(phoneNumber: String, message: String): String {
        return try {
            val smsManager: SmsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            val parts = smsManager.divideMessage(message)
            if (parts.size > 1) {
                smsManager.sendMultipartTextMessage(phoneNumber, null, parts, null, null)
            } else {
                smsManager.sendTextMessage(phoneNumber, null, message, null, null)
            }
            "SMS sent successfully to $phoneNumber"
        } catch (e: Exception) {
            "Failed to send SMS to $phoneNumber: ${e.localizedMessage}"
        }
    }

    private fun launchApp(packageName: String, activityName: String?): String {
        return try {
            val pm = context.packageManager
            val intent: Intent = if (activityName != null && activityName.isNotBlank()) {
                val fullActivityName = if (activityName.startsWith(".")) {
                    packageName + activityName
                } else if (!activityName.contains(".")) {
                    "$packageName.$activityName"
                } else {
                    activityName
                }

                Intent(Intent.ACTION_MAIN).apply {
                    component = ComponentName(packageName, fullActivityName)
                    addCategory(Intent.CATEGORY_LAUNCHER)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                }
            } else {
                pm.getLaunchIntentForPackage(packageName)?.apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                } ?: throw IllegalArgumentException("No launcher intent found for package '$packageName'")
            }

            context.startActivity(intent)
            "Launched ${packageName}${if (activityName != null) "/$activityName" else ""}"
        } catch (e: Exception) {
            "Failed to launch app '$packageName': ${e.localizedMessage}"
        }
    }

    private fun postNotification(title: String, message: String): String {
        return try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifications posted by T-UI scheduler and notify command"
                    enableVibration(true)
                }
                notificationManager.createNotificationChannel(channel)
            }

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .build()

            val notificationId = (System.currentTimeMillis() % 10000).toInt()
            notificationManager.notify(notificationId, notification)
            "Notification posted: \"$title\" - \"$message\""
        } catch (e: Exception) {
            "Failed to post notification: ${e.localizedMessage}"
        }
    }

    /**
     * Parse trailing variable key-value pairs (e.g. var1="value" var2=123 var3=true)
     */
    fun parseVariables(argsString: String): Map<String, Any> {
        val variables = mutableMapOf<String, Any>()
        if (argsString.isBlank()) return variables

        val matcher = KV_PATTERN.matcher(argsString)
        while (matcher.find()) {
            val key = matcher.group(1) ?: continue
            val quotedDouble = matcher.group(2)
            val quotedSingle = matcher.group(3)
            val unquoted = matcher.group(4)

            val stringVal = quotedDouble ?: quotedSingle ?: unquoted ?: ""

            val typedValue: Any = when {
                stringVal.equals("true", ignoreCase = true) -> true
                stringVal.equals("false", ignoreCase = true) -> false
                stringVal.toIntOrNull() != null -> stringVal.toInt()
                stringVal.toDoubleOrNull() != null -> stringVal.toDouble()
                else -> stringVal
            }

            variables[key] = typedValue
        }

        return variables
    }

    /**
     * Constructs and fires system-wide broadcast intent to MacroDroid with dynamic extras
     */
    fun fireMacroDroidIntent(macroName: String, variables: Map<String, Any>): Boolean {
        return try {
            val intent = Intent(ACTION_RUN_MACRO).apply {
                setPackage(MACRODROID_PACKAGE)
                putExtra(EXTRA_MACRO_NAME, macroName)
                putExtra(EXTRA_MACRO_NAME_ALT, macroName)

                variables.forEach { (key, value) ->
                    when (value) {
                        is String -> putExtra(key, value)
                        is Int -> putExtra(key, value)
                        is Double -> putExtra(key, value)
                        is Boolean -> putExtra(key, value)
                        else -> putExtra(key, value.toString())
                    }
                }
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }

            context.sendBroadcast(intent)
            Log.d(TAG, "Sent MacroDroid intent for macro '$macroName' with ${variables.size} variables")
            MacroHistoryDbHelper(context).logExecution(macroName, variables, "SENT")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send MacroDroid intent", e)
            MacroHistoryDbHelper(context).logExecution(macroName, variables, "FAILED")
            false
        }
    }

    private fun isNativeTUICommand(commandName: String): Boolean {
        if (mainPack == null) return false
        val commandGroup = mainPack.commandGroup ?: return false
        return commandGroup.getCommandByName(commandName) != null ||
               mainPack.aliasManager?.getAlias(commandName, false)?.get(0) != null
    }

    fun getTermuxEnvironment(): Map<String, String> {
        val externalFiles = context.getExternalFilesDir(null) ?: context.filesDir
        val homeDir = File(externalFiles, "home").apply { if (!exists()) mkdirs() }
        val prefixDir = File(externalFiles, "usr").apply { if (!exists()) mkdirs() }
        val tmpDir = File(context.cacheDir, "tmp").apply { if (!exists()) mkdirs() }
        val binDir = File(prefixDir, "bin").apply { if (!exists()) mkdirs() }

        val sysPath = System.getenv("PATH") ?: "/system/bin:/system/xbin"

        return mapOf(
            "HOME" to homeDir.absolutePath,
            "PREFIX" to prefixDir.absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "PATH" to "${binDir.absolutePath}:$sysPath",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor"
        )
    }
}
