package com.bamnat.core.backup

import java.time.LocalDate

/**
 * 2.0.0 [RVL-52] (검토 U12-06·I12a-03) 매일 자동 백업 in a folder: one file a day named by its date, the newest [KEEP] kept. A record
 * deleted by mistake is still in yesterday's file a week on, where one file overwritten every day had lost it the next morning.
 * Only the files named exactly as these are ever counted or taken away: anything else in the folder is the user's.
 */
object BackupGenerations {
    const val KEEP = 7
    private const val PREFIX = "밤낮-자동백업-"
    private const val SUFFIX = ".bamnat"
    private val NAME = Regex("""^${Regex.escape(PREFIX)}(\d{4}-\d{2}-\d{2})${Regex.escape(SUFFIX)}$""")

    /** 밤낮-자동백업-2026-11-12.bamnat */
    fun nameFor(date: LocalDate): String = "$PREFIX$date$SUFFIX"

    /** The day a file of ours was made, or null for any other file. */
    fun dateOf(name: String): LocalDate? = NAME.matchEntire(name)?.groupValues?.get(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    /** Ours past the newest [keep], to take away once today's file is written and checked; never another file. */
    fun toDelete(names: Collection<String>, keep: Int = KEEP): List<String> =
        names.mapNotNull { n -> dateOf(n)?.let { n to it } }.distinctBy { it.first }.sortedByDescending { it.second }.drop(keep).map { it.first }

    /** Ours in [names], newest first: what the settings row and the restore list show. */
    fun kept(names: Collection<String>): List<Pair<String, LocalDate>> =
        names.mapNotNull { n -> dateOf(n)?.let { n to it } }.distinctBy { it.first }.sortedByDescending { it.second }
}
