package com.bamnat.app.data

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory

/**
 * 2.0.0 [RVH-54] The one way from an added calendar's database to its file. [CalendarStorage.Files] makes one each time it opens
 * a calendar and shuts it when the calendar goes (그만두기, 달력 지우기, a restore putting it back), before the file is deleted.
 * From then on whatever still holds that database (a screen's read, a notice's worker) is given an empty one in memory: it reads
 * nothing, what it writes goes nowhere, and the removed calendar's file is never made again. A calendar brought back with the
 * same id gets a new door, so an old hold never reaches the new file either.
 *
 * #451 CI (PersonalCalendarsTest): Room lets a read already under way finish after close (its close waits for it), and the first
 * setup's summary read, under way when 그만두기 deleted the file, opened the file afterwards and made it again.
 *
 * [afterClose] runs when Room has really closed the file's database, for a door already shut: a read under way may have opened
 * one more connection to the file by its path after the delete, and the file goes once that read is over.
 */
class CalendarFileDoor(private val afterClose: () -> Unit = {}) : SupportSQLiteOpenHelper.Factory {
    private val lock = Any()
    private var shut = false

    /** No new way to the file from here on; returns once any opening that was under way has finished. */
    fun shut() = synchronized(lock) { shut = true }

    val isShut: Boolean get() = synchronized(lock) { shut }

    override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
        val factory = FrameworkSQLiteOpenHelperFactory()
        val file = factory.create(configuration)
        return object : SupportSQLiteOpenHelper by file {
            private var memory: SupportSQLiteOpenHelper? = null

            /** The same tables, made by Room's own callback, in memory only. */
            private fun memory(): SupportSQLiteOpenHelper = memory ?: factory.create(
                SupportSQLiteOpenHelper.Configuration.builder(configuration.context).name(null).callback(configuration.callback).build(),
            ).also { memory = it }

            override val writableDatabase: SupportSQLiteDatabase
                get() = synchronized(lock) { if (shut) memory().writableDatabase else file.writableDatabase }

            override val readableDatabase: SupportSQLiteDatabase
                get() = synchronized(lock) { if (shut) memory().readableDatabase else file.readableDatabase }

            override fun close() {
                val gone = synchronized(lock) {
                    file.close()
                    memory?.close()
                    memory = null
                    shut
                }
                if (gone) runCatching(afterClose)
            }
        }
    }
}
