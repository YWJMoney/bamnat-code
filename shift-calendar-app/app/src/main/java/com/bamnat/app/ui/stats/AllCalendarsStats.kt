package com.bamnat.app.ui.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.money.AllCalendarsFigures
import com.bamnat.app.ui.money.rememberAllCalendarsMonth
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.money.byType
import java.time.YearMonth

/**
 * [COMN-03] 통계's 전체: whatever kind of work each calendar is, only 총수입 · 총지출 · 순저축 for the
 * month over every calendar, and each calendar's days and hours of work. False with fewer than two
 * calendars, for the screen to show the calendar's own figures instead.
 */
@Composable
fun AllCalendarsStats(month: YearMonth): Boolean {
    val c = LocalContext.current.appContainer
    val calendars by c.calendars.calendars.collectAsStateWithLifecycle()
    if (calendars.size < 2) return false
    val all = rememberAllCalendarsMonth(month)
    if (all == null) {
        Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return true
    }
    SectionCard(title = "모든 달력 · ${month.monthValue}월") {
        AllCalendarsFigures(all)
    }
    SectionCard(title = "달력별 근무") {
        all.rows.forEach { row ->
            val color = calendars.firstOrNull { it.id == row.calendar }?.color
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline))
                Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text(
                    "${row.workDays}일" + if (row.workMinutes > 0) " · ${HoursFormat.korean(row.workMinutes)}" else "",
                    style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                )
            }
        }
        Text("일 종류가 달라도 수입과 지출만 모아 봐요. 달력마다 자세한 통계는 위에서 달력을 고르면 보여요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
    com.bamnat.app.ui.guide.FeatureCard("all.yearIncome", com.bamnat.app.data.PersonalCalendar.MAIN) { YearIncomeCard(month, calendars) }
    return true
}

/** G 1.32 [WTYP-07] 609 N잡: 올해 달력별 수입과 몫, 둘 이상에서 돈이 들어오면 5월 신고 안내. */
@Composable
private fun YearIncomeCard(month: YearMonth, calendars: List<com.bamnat.app.data.PersonalCalendar>) {
    val c = LocalContext.current.appContainer
    val parts by androidx.compose.runtime.remember(c) { com.bamnat.app.data.EveryCalendar.parts(c) }.collectAsStateWithLifecycle(null)
    val year by com.bamnat.app.ui.rememberLast("allYear:${month.year}:${month.monthValue}", parts, month) {
        val p = parts ?: return@rememberLast null
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            com.bamnat.core.money.AllCalendarsYear(month.year, (1..month.monthValue).map { com.bamnat.app.data.EveryCalendar.month(p, YearMonth.of(month.year, it)) })
        }
    }
    val y = year ?: return
    if (y.income <= 0) return
    SectionCard(title = "${month.year}년 달력별 수입 (1~${month.monthValue}월)") {
        y.rows.filter { it.income > 0 }.forEach { r ->
            val color = calendars.firstOrNull { it.id == r.calendar }?.color
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(color?.let { Color(it) } ?: MaterialTheme.colorScheme.outline))
                Text(r.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                Text("${com.bamnat.app.ui.Fmt.won(r.income)} · ${y.share(r)}%", style = MaterialTheme.typography.bodyMedium.merge(NumberStyle))
            }
        }
        // 1.32 [WTYP-19] 소득 종류별 합계: each calendar's kind follows its work, and a tap on the calendar's chip changes it.
        val prefs = com.bamnat.app.data.WorkTypePrefs.get(LocalContext.current)
        val choices by prefs.all.collectAsStateWithLifecycle()
        val styles = parts.orEmpty().associate { it.cal.id to (it.style to it.cal.field) }
        val typeOf = { id: String ->
            com.bamnat.core.money.IncomeType.of(choices[id]?.incomeType)
                ?: styles[id]?.let { (st, f) -> com.bamnat.core.money.IncomeType.guess(st, f) }
        }
        val byType = androidx.compose.runtime.remember(y, choices, styles) { y.byType(typeOf) }
        if (byType.isNotEmpty()) {
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("소득 종류별", style = MaterialTheme.typography.titleSmall)
            byType.forEach { (t, v) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(t.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(com.bamnat.app.ui.Fmt.won(v), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle))
                }
                Text(t.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            var picking by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                y.rows.filter { it.income > 0 }.forEach { r ->
                    androidx.compose.material3.AssistChip(onClick = { picking = r.calendar },
                        label = { Text("${r.name} · ${typeOf(r.calendar)?.label ?: "정하기"}", maxLines = 1) })
                }
            }
            picking?.let { id ->
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { picking = null },
                    title = { Text("이 달력의 소득 종류") },
                    text = {
                        Column {
                            com.bamnat.core.money.IncomeType.entries.forEach { t ->
                                androidx.compose.material3.TextButton(onClick = { prefs.setIncomeType(id, t.name); picking = null }) { Text(t.label) }
                            }
                            Text("3.3%를 떼면 사업소득, 8.8%를 떼면 기타소득, 일당에서 떼면 일용근로소득이에요. 참고용이에요.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    confirmButton = { androidx.compose.material3.TextButton(onClick = { prefs.setIncomeType(id, null); picking = null }) { Text("일 종류대로") } },
                )
            }
        }
        if (y.manyJobs) Text("두 곳 이상에서 돈이 들어왔어요. 근로소득 말고 사업·기타소득이 있으면 다음 해 5월 종합소득세 신고 대상일 수 있어요 " +
            "(현장 달력은 장부 › 요약 › 3.3% 합계에서 5월 신고 준비를 만들어요).", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp))
    }
}
