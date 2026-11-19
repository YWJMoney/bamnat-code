package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
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
    // 2.0.0 [RVN-18] 검토 U10a-04: one line, every calendar with 펼치기.
    com.bamnat.app.ui.components.FoldRow("plaza.calendars", modifier.padding(vertical = 4.dp), spacing = 8.dp, what = "달력 칩") {
        for (cal in list) {
            val company = c.companyOf(cal.id).companyName()
            val label = cal.name + (company?.let { " · $it" } ?: "")
            // 2.0.0 (조건 12): folded on a small phone at 200%, the picked calendar's chip slides into view (RVS-16's KeepInView),
            // so the 광장 shown always says whose it is.
            com.bamnat.app.ui.components.KeepInView(cal.id == selected) {
                FilterChip(
                    selected = cal.id == selected,
                    onClick = { if (cal.id != selected) onPick(cal.id) },
                    // 2.0.0 [RVN-22] HQ-115: wider than the row (작은 폰·글자 200%), the calendar over its company on two lines.
                    label = { CalendarChipLabel(cal.name, company, label) },
                    modifier = Modifier.semantics { contentDescription = "${cal.name} 달력의 광장" + (company?.let { ", $it" } ?: "") },
                )
            }
        }
    }
}

/**
 * 2.0.0 [RVN-22] HQ-115 (조건 12): "편의점 알바 · (주)예시정밀" on one line while it fits the chip's room; else the calendar on one line and its
 * company under it, each ending in '…' only when even that is too wide. Chosen as it is measured (no subcomposition, so the folded row can
 * still ask how wide it is). Its words are said once, as the one line, whichever shows.
 */
@Composable
private fun CalendarChipLabel(name: String, company: String?, one: String) {
    if (company == null) return Text(one, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    androidx.compose.ui.layout.Layout(
        content = {
            Text(one, maxLines = 1, softWrap = false)
            androidx.compose.foundation.layout.Column {
                Text(name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(company, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        },
        modifier = Modifier.clearAndSetSemantics { text = androidx.compose.ui.text.AnnotatedString(one) },
        measurePolicy = ChipLabelPolicy,
    )
}

/** One line when it fits the width given, else the two lines; asked how wide, the one line. */
private object ChipLabelPolicy : androidx.compose.ui.layout.MeasurePolicy {
    override fun androidx.compose.ui.layout.MeasureScope.measure(
        measurables: List<androidx.compose.ui.layout.Measurable>, constraints: androidx.compose.ui.unit.Constraints,
    ): androidx.compose.ui.layout.MeasureResult {
        val one = measurables[0].measure(androidx.compose.ui.unit.Constraints(maxHeight = constraints.maxHeight))
        if (!constraints.hasBoundedWidth || one.width <= constraints.maxWidth) {
            return layout(one.width.coerceAtLeast(constraints.minWidth), one.height.coerceIn(constraints.minHeight, constraints.maxHeight)) { one.place(0, 0) }
        }
        val two = measurables[1].measure(constraints.copy(minWidth = 0, minHeight = 0))
        return layout(two.width.coerceAtLeast(constraints.minWidth), two.height.coerceIn(constraints.minHeight, constraints.maxHeight)) { two.place(0, 0) }
    }

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int,
    ): Int = measurables[0].maxIntrinsicWidth(height)

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int,
    ): Int = measurables[1].minIntrinsicWidth(height)

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int,
    ): Int = if (measurables[0].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) <= width) measurables[0].maxIntrinsicHeight(width)
        else measurables[1].maxIntrinsicHeight(width)

    override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
        measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int,
    ): Int = if (measurables[0].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity) <= width) measurables[0].minIntrinsicHeight(width)
        else measurables[1].minIntrinsicHeight(width)
}
