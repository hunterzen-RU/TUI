package ohi.andre.consolelauncher.managers.cron

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ohi.andre.consolelauncher.managers.HybridCommandInterpreter
import ohi.andre.consolelauncher.tuils.Tuils
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker extending CoroutineWorker that consumes a JSON payload of scheduled actions
 * and routes them to the main execution engine.
 */
class CronWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        const val KEY_PAYLOAD = "cron_payload_json"
        const val KEY_ACTION = "cron_action"
        const val KEY_CRON_EXPR = "cron_expression"
        const val KEY_TAG = "cron_tag"

        /**
         * Schedules the next occurrence of a cron task with WorkManager using a JSON payload.
         *
         * @param tag Unique identifier for the scheduled task
         * @param cronExpr Standard 5-field cron pattern (e.g. "0 9 * * *" or "* /15 * * * *")
         * @param action Command string, JSON action object, or JSON array of actions
         */
        fun scheduleNextRun(context: Context, tag: String, cronExpr: String, action: String): Long {
            val cron = CronExpression(cronExpr)
            val nextTime = cron.getNextExecutionTime()
            val delayMillis = (nextTime - System.currentTimeMillis()).coerceAtLeast(1000L)

            // Construct structured JSON payload
            val jsonPayload = buildPayloadJson(tag, cronExpr, action, nextTime)

            val data = Data.Builder()
                .putString(KEY_TAG, tag)
                .putString(KEY_CRON_EXPR, cronExpr)
                .putString(KEY_ACTION, action)
                .putString(KEY_PAYLOAD, jsonPayload)
                .build()

            val request = OneTimeWorkRequestBuilder<CronWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tag)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                tag,
                ExistingWorkPolicy.REPLACE,
                request
            )

            // Save or update in persistent SQLite database
            val db = ScheduledTasksDbHelper(context)
            val existing = db.getTaskByTag(tag)
            if (existing == null) {
                db.saveTask(tag, cronExpr, action, nextTime)
            } else {
                db.updateTaskRun(tag, existing.lastRun, nextTime)
            }

            return nextTime
        }

        /**
         * Creates a standardized JSON payload string containing metadata and actions.
         */
        fun buildPayloadJson(tag: String, cronExpr: String, action: String, nextRun: Long): String {
            return try {
                val trimmed = action.trim()
                if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                    // Action is already a JSON object
                    JSONObject(trimmed).apply {
                        if (!has("tag")) put("tag", tag)
                        if (!has("cron")) put("cron", cronExpr)
                        if (!has("scheduled_at")) put("scheduled_at", System.currentTimeMillis())
                    }.toString()
                } else if (trimmed.startsWith("[") && trimmed.endsWith("]")) {
                    // Action is an array of actions
                    val jsonArray = JSONArray(trimmed)
                    JSONObject().apply {
                        put("tag", tag)
                        put("cron", cronExpr)
                        put("scheduled_at", System.currentTimeMillis())
                        put("actions", jsonArray)
                    }.toString()
                } else {
                    // Single string action
                    JSONObject().apply {
                        put("tag", tag)
                        put("cron", cronExpr)
                        put("scheduled_at", System.currentTimeMillis())
                        put("action", action)
                    }.toString()
                }
            } catch (e: Exception) {
                JSONObject().apply {
                    put("tag", tag)
                    put("cron", cronExpr)
                    put("action", action)
                }.toString()
            }
        }

        fun cancelTask(context: Context, tag: String): Boolean {
            WorkManager.getInstance(context).cancelUniqueWork(tag)
            return ScheduledTasksDbHelper(context).deleteTask(tag)
        }
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.Main) {
        val tag = inputData.getString(KEY_TAG) ?: return@withContext Result.failure()
        val cronExpr = inputData.getString(KEY_CRON_EXPR) ?: return@withContext Result.failure()
        val action = inputData.getString(KEY_ACTION) ?: return@withContext Result.failure()
        val payloadJson = inputData.getString(KEY_PAYLOAD)

        val lastRun = System.currentTimeMillis()

        try {
            // Consume JSON payload of scheduled actions and route to main execution engine
            val interpreter = HybridCommandInterpreter(context)
            val actionsToExecute = parseActionsFromPayload(payloadJson, action)

            val outputs = mutableListOf<String>()
            for (act in actionsToExecute) {
                if (act.isNotBlank()) {
                    val result = interpreter.executeAction(act)
                    outputs.add(result)
                }
            }

            val summaryOutput = if (outputs.size == 1) {
                outputs[0]
            } else {
                outputs.joinToString("\n")
            }
            Tuils.sendOutput(context, "[Cron: $tag] $summaryOutput")

            // Recompute next execution window via cron-utils and reschedule persistently
            val cron = CronExpression(cronExpr)
            val nextTime = cron.getNextExecutionTime(System.currentTimeMillis())
            val delayMillis = (nextTime - System.currentTimeMillis()).coerceAtLeast(1000L)

            val nextPayloadJson = buildPayloadJson(tag, cronExpr, action, nextTime)

            val data = Data.Builder()
                .putString(KEY_TAG, tag)
                .putString(KEY_CRON_EXPR, cronExpr)
                .putString(KEY_ACTION, action)
                .putString(KEY_PAYLOAD, nextPayloadJson)
                .build()

            val nextRequest = OneTimeWorkRequestBuilder<CronWorker>()
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tag)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                tag,
                ExistingWorkPolicy.REPLACE,
                nextRequest
            )

            ScheduledTasksDbHelper(context).updateTaskRun(tag, lastRun, nextTime)

            Result.success()
        } catch (e: Exception) {
            Tuils.log(e)
            Tuils.sendOutput(context, "[Cron: $tag Error] ${e.localizedMessage}")
            Result.retry()
        }
    }

    /**
     * Parses single, multiple, or structured actions from JSON payload.
     */
    private fun parseActionsFromPayload(payloadJson: String?, fallbackAction: String): List<String> {
        val actions = mutableListOf<String>()
        if (payloadJson.isNullOrBlank()) {
            actions.add(fallbackAction)
            return actions
        }

        try {
            val json = JSONObject(payloadJson)
            if (json.has("actions")) {
                val array = json.getJSONArray("actions")
                for (i in 0 until array.length()) {
                    val item = array.get(i)
                    if (item is JSONObject && item.has("command")) {
                        actions.add(item.getString("command"))
                    } else {
                        actions.add(item.toString())
                    }
                }
            } else if (json.has("action")) {
                val act = json.get("action")
                if (act is JSONArray) {
                    for (i in 0 until act.length()) {
                        actions.add(act.getString(i))
                    }
                } else {
                    actions.add(act.toString())
                }
            } else if (json.has("command")) {
                actions.add(json.getString("command"))
            } else {
                actions.add(fallbackAction)
            }
        } catch (e: Exception) {
            actions.add(fallbackAction)
        }

        return actions
    }
}
