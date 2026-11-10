package ohi.andre.consolelauncher.managers.cron

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SQLite Database storage for scheduled cron jobs.
 */
class ScheduledTasksDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "scheduled_tasks.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "scheduled_tasks"
        const val COLUMN_ID = "id"
        const val COLUMN_TAG = "tag"
        const val COLUMN_CRON = "cron"
        const val COLUMN_ACTION = "action"
        const val COLUMN_CREATED_AT = "created_at"
        const val COLUMN_LAST_RUN = "last_run"
        const val COLUMN_NEXT_RUN = "next_run"
        const val COLUMN_RUN_COUNT = "run_count"
        const val COLUMN_ENABLED = "enabled"
    }

    data class ScheduledTask(
        val id: Long,
        val tag: String,
        val cron: String,
        val action: String,
        val createdAt: Long,
        val lastRun: Long,
        val nextRun: Long,
        val runCount: Int,
        val enabled: Boolean
    ) {
        fun formattedNextRun(): String {
            if (nextRun <= 0) return "none"
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(nextRun))
        }

        fun formattedLastRun(): String {
            if (lastRun <= 0) return "never"
            val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            return sdf.format(Date(lastRun))
        }
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_TAG TEXT UNIQUE NOT NULL,
                $COLUMN_CRON TEXT NOT NULL,
                $COLUMN_ACTION TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL,
                $COLUMN_LAST_RUN INTEGER NOT NULL DEFAULT 0,
                $COLUMN_NEXT_RUN INTEGER NOT NULL,
                $COLUMN_RUN_COUNT INTEGER NOT NULL DEFAULT 0,
                $COLUMN_ENABLED INTEGER NOT NULL DEFAULT 1
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun saveTask(tag: String, cron: String, action: String, nextRun: Long): Long {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_TAG, tag)
            put(COLUMN_CRON, cron)
            put(COLUMN_ACTION, action)
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
            put(COLUMN_NEXT_RUN, nextRun)
            put(COLUMN_ENABLED, 1)
        }
        return db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun updateTaskRun(tag: String, lastRun: Long, nextRun: Long) {
        val db = writableDatabase
        db.execSQL("""
            UPDATE $TABLE_NAME 
            SET $COLUMN_LAST_RUN = ?, $COLUMN_NEXT_RUN = ?, $COLUMN_RUN_COUNT = $COLUMN_RUN_COUNT + 1
            WHERE $COLUMN_TAG = ?
        """.trimIndent(), arrayOf(lastRun, nextRun, tag))
    }

    fun getTaskByTag(tag: String): ScheduledTask? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME WHERE $COLUMN_TAG = ?", arrayOf(tag))
        cursor.use {
            if (it.moveToFirst()) {
                return ScheduledTask(
                    id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                    tag = it.getString(it.getColumnIndexOrThrow(COLUMN_TAG)),
                    cron = it.getString(it.getColumnIndexOrThrow(COLUMN_CRON)),
                    action = it.getString(it.getColumnIndexOrThrow(COLUMN_ACTION)),
                    createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                    lastRun = it.getLong(it.getColumnIndexOrThrow(COLUMN_LAST_RUN)),
                    nextRun = it.getLong(it.getColumnIndexOrThrow(COLUMN_NEXT_RUN)),
                    runCount = it.getInt(it.getColumnIndexOrThrow(COLUMN_RUN_COUNT)),
                    enabled = it.getInt(it.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1
                )
            }
        }
        return null
    }

    fun getAllTasks(): List<ScheduledTask> {
        val list = mutableListOf<ScheduledTask>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT * FROM $TABLE_NAME ORDER BY $COLUMN_ID ASC", null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    list.add(ScheduledTask(
                        id = it.getLong(it.getColumnIndexOrThrow(COLUMN_ID)),
                        tag = it.getString(it.getColumnIndexOrThrow(COLUMN_TAG)),
                        cron = it.getString(it.getColumnIndexOrThrow(COLUMN_CRON)),
                        action = it.getString(it.getColumnIndexOrThrow(COLUMN_ACTION)),
                        createdAt = it.getLong(it.getColumnIndexOrThrow(COLUMN_CREATED_AT)),
                        lastRun = it.getLong(it.getColumnIndexOrThrow(COLUMN_LAST_RUN)),
                        nextRun = it.getLong(it.getColumnIndexOrThrow(COLUMN_NEXT_RUN)),
                        runCount = it.getInt(it.getColumnIndexOrThrow(COLUMN_RUN_COUNT)),
                        enabled = it.getInt(it.getColumnIndexOrThrow(COLUMN_ENABLED)) == 1
                    ))
                } while (it.moveToNext())
            }
        }
        return list
    }

    fun deleteTask(tag: String): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COLUMN_TAG = ?", arrayOf(tag)) > 0
    }

    fun clearAll(): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, null, null)
    }
}
