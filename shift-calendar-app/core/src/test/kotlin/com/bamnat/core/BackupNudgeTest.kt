package com.bamnat.core

import com.bamnat.core.backup.BackupNudge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.32 [STAB-08] (계획/1.32-조사-대조표.md C10): a line after 30 days without a backup, never with 자동 백업 or switched off. */
class BackupNudgeTest {
    private val today = LocalDate.of(2026, 11, 30)

    @Test fun thirtyDaysWithoutABackupAsksOnce() {
        val never = BackupNudge.State(firstSeen = today.minusDays(31))
        assertTrue(BackupNudge.due(never, on = true, autoOn = false, lastAuto = null, hasRecords = true, today = today))
        assertEquals("아직 백업한 적 없어요", BackupNudge.words(never, null, today))
        // Not yet 30 days, nothing to keep, switched off, or 자동 백업 running: no line.
        assertFalse(BackupNudge.due(BackupNudge.State(firstSeen = today.minusDays(29)), true, false, null, true, today))
        assertFalse(BackupNudge.due(never, true, false, null, false, today))
        assertFalse(BackupNudge.due(never, false, false, null, true, today))
        assertFalse(BackupNudge.due(never, true, true, null, true, today))
        // A file saved 12 days ago.
        val saved = never.copy(lastManual = today.minusDays(12))
        assertFalse(BackupNudge.due(saved, true, false, null, true, today))
        assertEquals("마지막 백업 12일 전", BackupNudge.words(saved, null, today))
        // An automatic backup that stopped 40 days ago counts as the last one.
        assertTrue(BackupNudge.due(never, true, false, today.minusDays(40), true, today))
        assertEquals("오늘 백업했어요", BackupNudge.words(saved, today, today))
        // '나중에' waits a week.
        val later = never.copy(quietUntil = today.plusDays(BackupNudge.LATER_DAYS))
        assertFalse(BackupNudge.due(later, true, false, null, true, today))
        assertTrue(BackupNudge.due(later, true, false, null, true, today.plusDays(7)))
    }
}
