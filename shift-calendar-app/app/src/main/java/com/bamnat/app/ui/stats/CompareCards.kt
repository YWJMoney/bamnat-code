package com.bamnat.app.ui.stats

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.heightIn
import com.bamnat.core.work.words
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.pro.ProLock
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.report.MonthCompare
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.YearMonth
import com.bamnat.core.time.Today

/**
 * [PROV-02] 지난달·작년 비교 (프로): this month's hours, night work, work on days off, extra work and pay beside
 * last month's and the same month a year ago. Without 프로 the card shows the user's own figures blurred.
 */
@Composable
internal fun CompareCard(vm: StatsViewModel, calc: WorkHoursCalculator, snap: ScheduleSnapshot, month: YearMonth, profile: PayProfile?, gate: Gate) {
    // [UX-10] 1.25 Z (U7): the last figures from the first frame, worked out again only when the days or pay changed.
    val sides by com.bamnat.app.ui.rememberLast("compare:${vm.calendarId}:$month", snap, month, profile) {
        runCatching {
            withContext(Dispatchers.Default) {
                listOf(month, month.minusMonths(1), month.minusYears(1)).map { m -> MonthCompare.side(calc.month(m), profile?.let { vm.monthPay(it, snap, m) }) }
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }
    val s = sides ?: return
    val (now, last, lastYear) = s
    ProLock(Feature.WORK_COMPARE, gate) {
        SectionCard(title = "${month.monthValue}월 비교", trailing = { com.bamnat.app.ui.components.ProBadge(locked = gate.locked(Feature.WORK_COMPARE)) }) {
            CompareTable(now, last, "지난달 ${last.month.monthValue}월")
            HorizontalDivider(Modifier.padding(vertical = 10.dp))
            if (lastYear.empty) {
                Text("작년 ${lastYear.month.year % 100}년 ${lastYear.month.monthValue}월은 달력에 근무가 없어 비교할 게 없어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                CompareTable(now, lastYear, "작년 ${lastYear.month.monthValue}월")
            }
            Text(if (profile == null) "급여를 설정하면 야간과 급여도 비교해요." else "급여는 세금 떼기 전 예상이에요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
private fun CompareTable(now: MonthCompare.Side, before: MonthCompare.Side, beforeLabel: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text("", Modifier.weight(1.3f))
            Text("${now.month.monthValue}월", Modifier.weight(1.15f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            Text(beforeLabel, Modifier.weight(1.25f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("차이", Modifier.weight(1.45f), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
        }
        val cal = LocalCalendarColors.current
        MonthCompare.rows(now, before).forEach { r ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(r.label, Modifier.weight(1.3f), style = MaterialTheme.typography.bodyMedium)
                Text(com.bamnat.app.ui.Fmt.hideShort(r.now), Modifier.weight(1.15f), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), textAlign = TextAlign.End)
                Text(com.bamnat.app.ui.Fmt.hideShort(r.before), Modifier.weight(1.25f), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(com.bamnat.app.ui.Fmt.hideShort(r.change), Modifier.weight(1.45f), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), textAlign = TextAlign.End,
                    fontWeight = FontWeight.SemiBold,
                    // [A11Y-03] The theme's orange and blue words, which keep 4.5:1 in light and dark (the old fixed ones did not).
                    color = when (r.more) { true -> cal.holidayWork; false -> cal.saturday; null -> MaterialTheme.colorScheme.onSurfaceVariant })
            }
        }
    }
}

/** [PROV-02] 올해 근무·급여 (프로): every month of the year with its hours and pay, and the year so far. */
@Composable
internal fun YearWorkCard(vm: StatsViewModel, calc: WorkHoursCalculator, snap: ScheduleSnapshot, month: YearMonth, profile: PayProfile?, gate: Gate,
    /** PC 02d6dabf: the pay screen's 도구, where the report is made (this card sits on 근무·급여 itself). */
    onOpenReport: (() -> Unit)? = null) {
    val year by com.bamnat.app.ui.rememberLast("year:${vm.calendarId}:${month.year}", snap, month.year, profile) {
        runCatching {
            withContext(Dispatchers.Default) {
                MonthCompare.Year(month.year, (1..12).map { m ->
                    val ym = YearMonth.of(month.year, m)
                    MonthCompare.side(calc.month(ym), profile?.let { vm.monthPay(it, snap, ym) })
                })
            }
        }.onFailure { if (it is CancellationException) throw it }.getOrNull()
    }
    // Read as state (lint StateFlowValueCalledInComposition): the title follows a change of the kind of work.
    val settingsNow by vm.settings.collectAsStateWithLifecycle()
    val y = year ?: return
    val now = Today.month()
    ProLock(Feature.WORK_COMPARE, gate) {
        // PC 02d6dabf: named as the tab it sits on (알바 '출근·시급').
        SectionCard(title = "${y.year}년 ${settingsNow.workStyle.words().tab}", trailing = { com.bamnat.app.ui.components.ProBadge(locked = gate.locked(Feature.WORK_COMPARE)) }) {
            yearBars(y, now)?.let { (bars, line) -> com.bamnat.app.ui.components.BarChart(bars, line, height = 96.dp) }
            y.months.forEach { s ->
                val ahead = s.month.isAfter(now)
                val color = if (ahead) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.month.monthValue}월" + if (ahead) " 예상" else "", Modifier.width(64.dp), style = MaterialTheme.typography.bodyMedium, color = color)
                    Text(HoursFormat.korean(s.worked), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), color = color)
                    Text(s.night?.let { "야간 " + HoursFormat.korean(it) } ?: "${s.workDays}일", Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    s.pay?.let { Text(com.bamnat.app.ui.Fmt.hideShort(MonthCompare.man(it)), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), color = color, textAlign = TextAlign.End) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("한 해", Modifier.width(64.dp), style = MaterialTheme.typography.titleSmall)
                Text(HoursFormat.korean(y.worked), Modifier.weight(1f), style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                Text(y.night?.let { "야간 " + HoursFormat.korean(it) } ?: "${y.workDays}일", Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
                y.pay?.let { Text(com.bamnat.app.ui.Fmt.hideShort(MonthCompare.man(it)), style = MaterialTheme.typography.titleSmall.merge(NumberStyle)) }
            }
            // PC e1ca9e5b·02d6dabf: the report is the pay screen's (도구), not this tab's; a link goes there.
            Text("근무표대로 앞으로의 달도 셌어요. 급여 리포트(PDF·CSV)는 급여 계산 › 도구에서 만들어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            onOpenReport?.let { go -> TextButton(onClick = go, modifier = Modifier.heightIn(min = 48.dp)) { Text("급여 리포트 만들러 가기") } }
        }
    }
}

/**
 * 1.39 [FLOW-17] 그래프 규칙 한 벌 on 근무 통계: the year's months as one chart (one colour, the busiest month in 주의 only when it
 * goes past 52 hours a week on average, the months so far averaged on one dashed line, a tap says the month). Months still ahead
 * are counted from the roster and say "예상". Null when the year has nothing yet.
 */
internal fun yearBars(y: MonthCompare.Year, now: YearMonth): Pair<List<com.bamnat.app.ui.components.ChartBar>, com.bamnat.app.ui.components.Baseline?>? {
    if (y.months.all { it.worked == 0 }) return null
    val bars = y.months.map { s ->
        val weeks = s.month.lengthOfMonth() / 7.0
        val over = s.worked > 52 * 60 * weeks
        com.bamnat.app.ui.components.ChartBar(
            label = "${s.month.monthValue}월" + if (s.month.isAfter(now)) " 예상" else "",
            value = s.worked / 60.0,
            tone = if (over) com.bamnat.app.ui.theme.Tone.Caution else com.bamnat.app.ui.theme.Tone.Plain,
            text = if (s.worked == 0) "기록 없음" else HoursFormat.korean(s.worked),
        )
    }
    val past = y.months.filter { !it.month.isAfter(now) && it.worked > 0 }
    val line = past.takeIf { it.isNotEmpty() }?.let { l ->
        val avg = l.sumOf { it.worked } / l.size
        com.bamnat.app.ui.components.Baseline(avg / 60.0, "점선 평균 ${HoursFormat.korean(avg)}")
    }
    return bars to line
}
