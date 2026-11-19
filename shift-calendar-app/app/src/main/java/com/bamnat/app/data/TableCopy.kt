package com.bamnat.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/*
 * Copying a calendar's tables between its open database and a file of their own, by the columns both have: what 최근 지운 기록
 * ([RecordsTrash]) keeps and puts back, and 2.0.0 [RVL-76] 날짜별 되돌리기 ([TimeMachine]).
 */

private val INTERNAL = setOf("android_metadata", "room_master_table")


internal interface Db : AutoCloseable {
    fun query(sql: String, args: Array<Any?> = emptyArray()): Cursor
    fun exec(sql: String)
    fun insert(table: String, values: ContentValues)
    override fun close() {}
}

internal class SupportDb(val db: SupportSQLiteDatabase) : Db {
    override fun query(sql: String, args: Array<Any?>): Cursor = db.query(sql, args)
    override fun exec(sql: String) = db.execSQL(sql)
    override fun insert(table: String, values: ContentValues) {
        db.insert(table, SQLiteDatabase.CONFLICT_REPLACE, values)
    }
}

internal class FileDb(val db: SQLiteDatabase) : Db {
    override fun query(sql: String, args: Array<Any?>): Cursor = db.rawQuery(sql, args.map { it?.toString() }.toTypedArray())
    override fun exec(sql: String) = db.execSQL(sql)
    override fun insert(table: String, values: ContentValues) {
        db.insertWithOnConflict(table, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }
    override fun close() = db.close()
}

internal fun fileDb(file: File): FileDb = FileDb(SQLiteDatabase.openOrCreateDatabase(file, null))

internal fun tablesOf(db: SupportSQLiteDatabase): Set<String> = tablesOf(SupportDb(db)).toSet()

internal fun tablesOf(db: Db): List<String> = db.query("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").use { c ->
    buildList { while (c.moveToNext()) c.getString(0).takeIf { it !in INTERNAL && !it.startsWith("sqlite_") }?.let(::add) }
}

internal fun columnsOf(db: Db, table: String): Set<String> = db.query("PRAGMA table_info(`$table`)").use { c ->
    val name = c.getColumnIndex("name")
    buildSet { while (c.moveToNext()) add(c.getString(name)) }
}

/** Table [table] of [from] made in [to] with its rows ([where] picks some). */
internal fun dump(from: Db, to: Db, table: String, where: String? = null) {
    val create = from.query("SELECT sql FROM sqlite_master WHERE type = 'table' AND name = ?", arrayOf(table)).use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: return
    to.exec(create.replaceFirst("CREATE TABLE ", "CREATE TABLE IF NOT EXISTS ").replace("IF NOT EXISTS IF NOT EXISTS", "IF NOT EXISTS"))
    copyRows(from, to, table, where)
}

/** Rows of [table] from [from] into [to], by the columns both have; a column [to] has alone keeps its default. */
internal fun copyRows(from: Db, to: Db, table: String, where: String? = null) {
    val target = columnsOf(to, table)
    from.query("SELECT * FROM `$table`" + (where?.let { " WHERE $it" } ?: "")).use { c ->
        val cols = (0 until c.columnCount).filter { c.getColumnName(it) in target }
        while (c.moveToNext()) {
            val v = ContentValues()
            for (i in cols) {
                val name = c.getColumnName(i)
                when (c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> v.putNull(name)
                    Cursor.FIELD_TYPE_INTEGER -> v.put(name, c.getLong(i))
                    Cursor.FIELD_TYPE_FLOAT -> v.put(name, c.getDouble(i))
                    Cursor.FIELD_TYPE_BLOB -> v.put(name, c.getBlob(i))
                    else -> v.put(name, c.getString(i))
                }
            }
            to.insert(table, v)
        }
    }
}

