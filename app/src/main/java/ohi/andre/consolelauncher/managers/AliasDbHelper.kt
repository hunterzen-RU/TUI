package ohi.andre.consolelauncher.managers

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite Database storage for aliases mapping short keywords to complex shell or launcher commands.
 */
class AliasDbHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "aliases.db"
        private const val DATABASE_VERSION = 1

        const val TABLE_NAME = "aliases"
        const val COLUMN_ID = "id"
        const val COLUMN_NAME = "name"
        const val COLUMN_VALUE = "value"
        const val COLUMN_CREATED_AT = "created_at"
    }

    data class AliasEntry(
        val id: Long,
        val name: String,
        val value: String
    )

    override fun onCreate(db: SQLiteDatabase) {
        val createQuery = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_NAME TEXT UNIQUE NOT NULL,
                $COLUMN_VALUE TEXT NOT NULL,
                $COLUMN_CREATED_AT INTEGER NOT NULL
            )
        """.trimIndent()
        db.execSQL(createQuery)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    fun setAlias(name: String, value: String): Boolean {
        val db = writableDatabase
        val values = ContentValues().apply {
            put(COLUMN_NAME, name.trim())
            put(COLUMN_VALUE, value.trim())
            put(COLUMN_CREATED_AT, System.currentTimeMillis())
        }
        val rowId = db.insertWithOnConflict(TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE)
        return rowId != -1L
    }

    fun getAlias(name: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_VALUE FROM $TABLE_NAME WHERE $COLUMN_NAME = ?", arrayOf(name.trim()))
        cursor.use {
            if (it.moveToFirst()) {
                return it.getString(0)
            }
        }
        return null
    }

    fun removeAlias(name: String): Boolean {
        val db = writableDatabase
        return db.delete(TABLE_NAME, "$COLUMN_NAME = ?", arrayOf(name.trim())) > 0
    }

    fun getAllAliases(): List<AliasEntry> {
        val list = mutableListOf<AliasEntry>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT $COLUMN_ID, $COLUMN_NAME, $COLUMN_VALUE FROM $TABLE_NAME ORDER BY $COLUMN_NAME ASC", null)
        cursor.use {
            if (it.moveToFirst()) {
                do {
                    list.add(
                        AliasEntry(
                            id = it.getLong(0),
                            name = it.getString(1),
                            value = it.getString(2)
                        )
                    )
                } while (it.moveToNext())
            }
        }
        return list
    }

    fun clearAliases(): Int {
        val db = writableDatabase
        return db.delete(TABLE_NAME, null, null)
    }
}
