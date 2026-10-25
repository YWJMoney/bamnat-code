package com.bamnat.app.data

import org.json.JSONObject
import java.io.File
import java.time.LocalDate

/**
 * [LEDG-09] For each repeating line that writes itself, the last day it was caught up to, so a line the
 * user deleted is not written again ([com.bamnat.core.money.Recurring.due]). A small file next to the
 * calendar; lost, the lines fall back to their first day and today. Null [file] keeps it in memory (tests).
 */
class RecurringMarks(private val file: File?) {
    private var marks: Map<Long, LocalDate>? = null
    /** [BAK-01] The file as it was read; a restore that writes it anew is read again. */
    private var readStamp = 0L

    @Synchronized
    fun all(): Map<Long, LocalDate> {
        val stamp = stampOf(file)
        if (stamp != readStamp) marks = null
        return marks ?: load().also { marks = it; readStamp = stamp }
    }

    @Synchronized
    fun put(changes: Map<Long, LocalDate>) {
        if (changes.isEmpty()) return
        val next = all() + changes
        marks = next
        val f = file ?: return
        runCatching {
            f.parentFile?.mkdirs()
            val o = JSONObject()
            next.forEach { (id, d) -> o.put(id.toString(), d.toEpochDay()) }
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeText(o.toString())
            tmp.renameTo(f)
            readStamp = stampOf(f)
        }
    }

    /** When and how long: a file system that keeps whole seconds still tells a restore's file from the one before. */
    private fun stampOf(f: File?): Long = f?.takeIf { it.exists() }?.let { it.lastModified() * 31 + it.length() } ?: 0L

    private fun load(): Map<Long, LocalDate> = runCatching {
        val f = file?.takeIf { it.exists() } ?: return emptyMap()
        val o = JSONObject(f.readText())
        o.keys().asSequence().mapNotNull { k -> k.toLongOrNull()?.let { it to LocalDate.ofEpochDay(o.getLong(k)) } }.toMap()
    }.getOrDefault(emptyMap())
}
