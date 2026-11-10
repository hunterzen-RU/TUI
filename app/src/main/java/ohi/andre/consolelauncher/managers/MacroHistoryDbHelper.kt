package ohi.andre.consolelauncher.managers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SQLite Database storage to log the last 20 MacroDroid execution requests.
 */
class MacroHistoryDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "macro_history.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "macro_history"
        const val COLUMN_ID = "id"
        const val COLUMN_MACRO_NAME = "macro_name"
        const val COLUMN_VARIABLES = "variables"
        const val COLUMN_TIMESTAMP = "timestamp"
        const val COLUMN_STATUS = "status"

        private const val MAX_LOG_ENTRIES = 20
    }

    data class MacroLogEntry(
        val id: Long,
        val macroName: String,
        val variablesJson: String,
        val timestamp: Long,
        val status: String
    ) {
        fun formattedTime(): String {
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(timestamp))
        }

        fun formattedVariables(): String {
            return try {
                val json = JSONObject(variablesJson)
                val keys = json.keys()
                val list = mutableListOf<String>()
                while (keys.hasNext()) {
                    val k = keys.next()
                    list.add("$k=${json.get(k)}")
                }
                if (list.isEmpty()) "none" else list.joinToString(", ")
            } catch (e: Exception) {
                variablesJson
            }
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTableQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_MACRO_NAME TEXT NOT NULL,
                $COLUMN_VARIABLES TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_STATUS TEXT NOT NULL
            )
        """.trimIndent()
        db.execSQL(createTableQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * Inserts execution log and prunes DB to keep only the last 20 records.
     */
    fun logExecution(macroName: String, variables: Map<String, Any>, status: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val variablesJson = JSONObject(variables).toString()
            val values = ContentValues().apply {
                put(COLUMN_MACRO_NAME, macroName)
                put(COLUMN_VARIABLES, variablesJson)
                put(COLUMN_TIMESTAMP, System.currentTimeMillis())
                put(COLUMN_STATUS, status)
            }
            db.insert(TABLE_NAME, null, values)

            // Prune to keep only the latest 20 entries
            db.execSQL("""
                DELETE FROM $TABLE_NAME 
                WHERE $COLUMN_ID NOT IN (
                    SELECT $COLUMN_ID FROM $TABLE_NAME 
                    ORDER BY $COLUMN_ID DESC LIMIT $MAX_LOG_ENTRIES
                )
            """.trimIndent())

            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Retrieves the last 20 macro execution records (most recent first).
     */
    fun getRecentHistory(limit: Int = MAX_LOG_ENTRIES): List<MacroLogEntry> {
        val historyList = mutableListOf<MacroLogEntry>()
        val db = readableDatabase
        val query = "SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_ID DESC LIMIT $limit"

        val cursor = db.rawQuery(query, null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    val id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID))
                    val macroName = it.getString(it.getColumnIndexOrThrow(COLUMN_MACRO_NAME))
                    val variables = it.getString(it.getColumnIndexOrThrow(COLUMN_VARIABLES))
                    val timestamp = it.getLong(it.getColumnIndexOrThrow(COLUMN_TIMESTAMP))
                    val status = it.getString(it.getColumnIndexOrThrow(COLUMN_STATUS))

                    historyList.add(MacroLogEntry(id, macroName, variables, timestamp, status))
                } while (it.moveToNext())
            }
        }
        return historyList
    }

    /**
     * Clears all log entries.
     */
    fun clearHistory() {
        val db = writableDatabase
        db.delete(TABLE_NAME, null, null)
    }
}
