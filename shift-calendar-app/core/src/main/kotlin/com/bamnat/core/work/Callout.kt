package com.bamnat.core.work

import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.model.ExtraTiming
import com.bamnat.core.model.OffDayTreatment
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.32 [WTYP-09] (근무유형 조사 1장 교대: 경찰·소방·간호 "비번 날 비상소집·동원"): one chip on the day puts the hours called in
 * as extra work of its own kind, 비상소집, so the pay counts it as 연장 or 휴일근로 by the company's rule like any other.
 */
object Callout {
    const val NAME = "비상소집"
    const val SHORT = "비"
    private const val COLOR = 0xFFC62828

    /** Called in for about this long when nothing is said. */
    const val DEFAULT_MINUTES = 180

    /** The user's own 비상소집 kind, when there is one (hidden or not: a hidden one is shown again). */
    fun find(kinds: ExtraKinds): ExtraKind? = kinds.kinds.firstOrNull { it.name == NAME }

    /** The kind to write with: the user's, or a new one with clock times of its own. */
    fun kind(kinds: ExtraKinds): ExtraKind = find(kinds)?.copy(hidden = false)
        ?: ExtraKind(kinds.nextId(), NAME, SHORT, COLOR, ExtraTiming.Band(LocalTime.of(9, 0), LocalTime.of(12, 0)), offDay = OffDayTreatment.BY_DAY)

    fun entry(kind: ExtraKind, start: LocalTime, end: LocalTime, breakMinutes: Int = 0): ExtraEntry =
        ExtraEntry(kind.id, start = start, end = end, breakMinutes = breakMinutes)

    /** Shift and day calendars: a 비번 day first, but any day can be called in. */
    fun offered(style: WorkStyle?): Boolean = style == null || style == WorkStyle.SHIFT || style == WorkStyle.DAY

    /** The month's call-ins: how many and how long, for the month's line. */
    fun month(entries: List<DayEntry>, kinds: ExtraKinds, month: YearMonth): Pair<Int, Int> {
        val id = find(kinds)?.id ?: return 0 to 0
        val pieces = entries.filter { YearMonth.from(it.date) == month }.flatMap { e -> e.extras.filter { it.kindId == id } }
        return pieces.size to pieces.sumOf { it.paidMinutes }
    }
}
