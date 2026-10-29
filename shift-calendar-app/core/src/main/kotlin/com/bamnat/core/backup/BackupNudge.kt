package com.bamnat.core.backup

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 1.32 [STAB-08] (근무유형 조사 공통 6장 "기록이 사라졌어요"가 ★1의 큰 이유): the one line that asks for a backup when the last
 * one is old. Records live on this phone only, so without a 매일 자동 백업 a lost phone loses them.
 */
object BackupNudge {
    /** Days without a backup before the line shows. */
    const val DAYS = 30L

    /** '나중에' puts it away this many days. */
    const val LATER_DAYS = 7L

    data class State(
        /** The first day this phone counted from (install or the first 1.32 open). */
        val firstSeen: LocalDate? = null,
        /** The last backup file saved by hand and read back as whole. */
        val lastManual: LocalDate? = null,
        val quietUntil: LocalDate? = null,
    )

    /** Days since the newest of [State.lastManual] and [lastAuto], counted from [State.firstSeen] when there was none. */
    fun daysSince(s: State, lastAuto: LocalDate?, today: LocalDate): Long? {
        val from = listOfNotNull(s.lastManual, lastAuto).maxOrNull() ?: s.firstSeen ?: return null
        return ChronoUnit.DAYS.between(from, today).coerceAtLeast(0)
    }

    /**
     * Whether the calendar shows the line today: [on] (설정 › 데이터), a 매일 자동 백업 not running ([autoOn]), something worth
     * keeping ([hasRecords]), [DAYS] since the last backup and not put away.
     */
    fun due(s: State, on: Boolean, autoOn: Boolean, lastAuto: LocalDate?, hasRecords: Boolean, today: LocalDate): Boolean {
        if (!on || autoOn || !hasRecords) return false
        if (s.quietUntil != null && today.isBefore(s.quietUntil)) return false
        val days = daysSince(s, lastAuto, today) ?: return false
        return days >= DAYS
    }

    /** "마지막 백업 12일 전", "오늘 백업했어요", "아직 백업한 적 없어요". */
    fun words(s: State, lastAuto: LocalDate?, today: LocalDate): String {
        val last = listOfNotNull(s.lastManual, lastAuto).maxOrNull() ?: return "아직 백업한 적 없어요"
        val days = ChronoUnit.DAYS.between(last, today).coerceAtLeast(0)
        return if (days == 0L) "오늘 백업했어요" else "마지막 백업 ${days}일 전"
    }
}
