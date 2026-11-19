package com.bamnat.core

import com.bamnat.core.backup.BackupGenerations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** 2.0.0 [RVL-52] (검토 U12-06·I12a-03) Seven dated files kept in the backup folder, and nothing of the user's taken away. */
class BackupGenerationsTest {
    private val nov = (1..10).map { BackupGenerations.nameFor(LocalDate.of(2026, 11, it)) }

    @Test fun aFileIsNamedByItsDay() {
        assertEquals("밤낮-자동백업-2026-11-12.bamnat", BackupGenerations.nameFor(LocalDate.of(2026, 11, 12)))
        assertEquals(LocalDate.of(2026, 11, 12), BackupGenerations.dateOf("밤낮-자동백업-2026-11-12.bamnat"))
    }

    @Test fun theNewestSevenStay() {
        assertEquals(nov.take(3).reversed(), BackupGenerations.toDelete(nov.shuffled()))
        assertEquals(emptyList<String>(), BackupGenerations.toDelete(nov.take(7)))
    }

    @Test fun otherFilesAreNeverTaken() {
        val theirs = listOf("밤낮-자동백업.bamnat", "밤낮-백업-2026-11-01-0930.bamnat", "밤낮-자동백업-2026-11-01 (1).bamnat",
            "사진.jpg", "밤낮-자동백업-2026-13-01.bamnat")
        theirs.forEach { assertNull(it, BackupGenerations.dateOf(it)) }
        assertEquals(nov.take(3).reversed(), BackupGenerations.toDelete(nov + theirs))
    }

    @Test fun keptIsNewestFirst() {
        assertEquals(LocalDate.of(2026, 11, 10), BackupGenerations.kept(nov.shuffled()).first().second)
        assertEquals(10, BackupGenerations.kept(nov + nov).size)
    }
}
