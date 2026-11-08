package com.bamnat.core.schedule

import com.bamnat.core.model.NightShift
import com.bamnat.core.recipe.ShiftLooks
import java.time.LocalDate
import java.time.LocalDateTime

/** [STU-37] Which [ShiftLooks] look a moment calls for (kept out of the recipe format, which stays free of JVM calls). */
object ShiftLookMode {
    /** On the calendar [resolve] at [now]: a night shift on beats a day off; else the usual. */
    fun at(now: LocalDateTime, resolve: (LocalDate) -> ResolvedDay): ShiftLooks.Mode = when {
        NightShift.isOn(now, resolve) -> ShiftLooks.Mode.NIGHT
        // [LINK-01] 1.39 A day off with a 대근·특근 on it is a day worked: not the 쉬는 날 look. A full-day 연차 is a day off.
        resolve(now.toLocalDate()).let { d -> DayWork.of(d).let { w -> w.off && (d.shiftType?.isOff == true || w.fullLeave) } } -> ShiftLooks.Mode.OFF
        else -> ShiftLooks.Mode.NORMAL
    }
}
