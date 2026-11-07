package com.bamnat.app.ui.calendar

import androidx.compose.runtime.staticCompositionLocalOf
import com.bamnat.app.data.AppSettings
import com.bamnat.core.plaza.IndustryGroups
import java.time.LocalDate

/**
 * [CYC-04] 1.38: the 업종 묶음 whose law-named days (소방의 날, 경찰의 날…) the month shows, or null for none. Provided by the
 * calendar from [AppSettings.orgDayGroup] while [AppSettings.orgDaysOn]; a cell reads it so no parameter runs through
 * every month and week composable.
 */
val LocalOrgDayGroup = staticCompositionLocalOf<String?> { null }

/** The group to show for [s], or null when off or none. */
fun orgGroupOf(s: AppSettings): String? = s.orgDayGroup.takeIf { s.orgDaysOn && it.isNotEmpty() }

/** The name of the day [date] has for [group], never on a red day (the red day's name wins the line). */
fun orgDayName(group: String?, date: LocalDate, holiday: String?): String? =
    if (group == null || holiday != null) null else IndustryGroups.dayOn(group, date)?.name

/**
 * [CYC-04] A public cycle of [cycle]'s 업종 went on the calendar (or was picked in the first setup): its group's days show
 * from now on, when it has any and the user has not picked a group already (설정 › 달력 stays theirs).
 */
fun rememberOrgGroup(settings: com.bamnat.app.data.SettingsRepository, cycle: com.bamnat.core.plaza.PublicCycle) {
    val g = cycle.group.takeIf { it.days.isNotEmpty() } ?: return
    settings.update { s -> if (s.orgDayGroup.isEmpty()) s.copy(orgDayGroup = g.key) else s }
}

/**
 * [EASE-04] 1.38 The 월급날 that fall on [m]'s six-week grid (the month before and after too), for the '급여' mark; empty
 * when no 월급날 was entered.
 */
fun payDaysOn(period: com.bamnat.core.pay.PayPeriod?, m: java.time.YearMonth): Set<LocalDate> =
    if (period?.payDay == null) emptySet() else (-2L..1L).mapNotNull { period.payDate(m.plusMonths(it)) }.toSet()
