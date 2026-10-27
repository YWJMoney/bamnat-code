package com.bamnat.app.ui.company

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyScope
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.components.FilterChip

/**
 * [CALI-04] 1.30 BB: the calendar whose company the cards and sheets inside a screen are about (the pay screen of an added
 * calendar, its 광장). The route that shows them provides it; anything outside such a route is the main calendar's, as before.
 */
val LocalCompanyCalendar: ProvidableCompositionLocal<String> = staticCompositionLocalOf { PersonalCalendar.MAIN }

/** [CALI-04] The company things of the calendar the screen is about ([LocalCompanyCalendar]). */
@Composable
fun companyScope(c: AppContainer): CompanyScope = c.companyOf(LocalCompanyCalendar.current)

/**
 * [CALI-03] The calendar a company route works on: the one it was opened for, else the calendar on screen (설정 or a notice
 * open the plaza without one). A phone with only 내 달력 always gets it.
 */
fun companyCalendarOf(c: AppContainer, arg: String?): String {
    fun usable(id: String?) = id?.takeIf { it.isNotEmpty() && it != PersonalCalendar.EXAMPLE && c.calendars.get(it) != null }
    return usable(arg) ?: usable(c.calendars.active.value) ?: PersonalCalendar.MAIN
}

/** [CALI-08] The calendars a company can be chosen for: 내 달력 and the added ones set up (not the example). */
internal fun companyCalendars(list: List<PersonalCalendar>): List<PersonalCalendar> = list.filter { it.isMain || (it.ready && !it.isExample) }

/**
 * [CALI-08] 1.30 BB (사용자 2026-10-27 "달력은 개별적으로"): above 광장, with two calendars or more, which calendar's company
 * it is about. Each chip says the calendar and its company; choosing one shows that calendar's 광장. Nothing with one calendar.
 */
@Composable
fun PlazaCalendarChips(c: AppContainer, selected: String, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val all by c.calendars.calendars.collectAsStateWithLifecycle()
    val list = companyCalendars(all)
    if (list.size < 2) return
    Row(
        modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (cal in list) {
            val company = c.companyOf(cal.id).companyName()
            val label = cal.name + (company?.let { " · $it" } ?: "")
            FilterChip(
                selected = cal.id == selected,
                onClick = { if (cal.id != selected) onPick(cal.id) },
                label = { Text(label, maxLines = 1) },
                modifier = Modifier.semantics { contentDescription = "${cal.name} 달력의 광장" + (company?.let { ", $it" } ?: "") },
            )
        }
    }
}
