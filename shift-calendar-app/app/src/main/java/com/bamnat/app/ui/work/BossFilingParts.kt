package com.bamnat.app.ui.work

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.boss.BossFilings
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

/** 했어요 ticks, on this phone (a reminder list: a restore without them shows a past duty again for a week at most). */
private object Filed {
    private fun prefs(c: Context) = c.getSharedPreferences("boss_filed", Context.MODE_PRIVATE)
    fun all(c: Context): Set<String> = runCatching { prefs(c).getStringSet("done", emptySet()).orEmpty().toSet() }.getOrDefault(emptySet())
    fun set(c: Context, key: String, on: Boolean) {
        val now = all(c)
        prefs(c).edit().putStringSet("done", (if (on) now + key else now - key).toList().takeLast(400).toSet()).apply()
    }
}

/**
 * 1.38 [P3-X-08] 다가올 신고·지급: 급여 지급일, 10일 원천세, 4대보험 취득·상실, 퇴사 정산, 간이지급명세서, from the ledger and staff rows,
 * with the amount and the 제출할 표 (CSV) right there.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilingsCard(place: Workplace, staff: List<StaffMember>, work: List<StaffWork>) {
    val context = LocalContext.current
    val store = context.appContainer.phaseThree
    val scope = rememberCoroutineScope()
    val today = Today.date()
    var done by remember { mutableStateOf(Filed.all(context)) }
    var all by rememberSaveable { mutableStateOf(false) }
    // The months the window asks for, each read once with its 달라진 날.
    val ledgers by produceState<Map<YearMonth, com.bamnat.core.boss.Ledger>?>(null, place, staff, work) {
        value = withContext(Dispatchers.Default) {
            val first = YearMonth.from(today).minusMonths(3)
            (0..4).associate { i -> first.plusMonths(i.toLong()).let { m -> m to PayrollLedger.month(place, staff, m, store.work(place.id, m).first()) } }
        }
    }
    val got = ledgers ?: return
    val ledgerOf = { m: YearMonth -> got[m] ?: PayrollLedger.month(place, staff, m, emptyList()) }
    val due = remember(got, staff, place, today) { BossFilings.upcoming(place, staff, today, ledgerOf) }
    val open = due.filter { it.key !in done }
    if (due.isEmpty()) return
    val warn = LocalCalendarColors.current.warning
    SectionCard(title = "다가올 신고·지급" + if (open.isNotEmpty()) " ${open.size}" else " ✓") {
        (if (all) due else open.take(4)).forEach { d ->
            val n = d.daysFrom(today)
            val ticked = d.key in done
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.monthDay(d.date)} · " + when { ticked -> "했어요"; n < 0 -> "${-n}일 지났어요"; n == 0L -> "오늘"; else -> "${n}일 남음" },
                        style = MaterialTheme.typography.labelMedium, color = if (!ticked && n <= 3) warn else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.title + (d.amount?.takeIf { it > 0 }?.let { " ${Fmt.won(it)}" } ?: ""), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Hint(d.detail + if (d.where.isNotEmpty()) " · ${d.where}" else "")
                    if (d.kind == BossFilings.Kind.WITHHOLD) {
                        val worked = YearMonth.from(d.date).minusMonths(2)  // 일한 달 → 다음 달 지급 → 그다음 달 10일
                        TextButton(onClick = {
                            scope.launch {
                                val l = ledgerOf(worked)
                                runCatching { com.bamnat.app.report.ReportShare.csv(context, BossFilings.withheldCsv(place, worked, BossFilings.withheld(place, l)),
                                    "원천세-${place.name}-$worked.csv", "${place.name} ${Fmt.month(worked)}분 원천세 표") }
                            }
                        }) { Text("원천세 표(CSV)") }
                    }
                    if (d.kind == BossFilings.Kind.INSURE_IN || d.kind == BossFilings.Kind.INSURE_OUT) TextButton(onClick = {
                        scope.launch {
                            runCatching { com.bamnat.app.report.ReportShare.csv(context, BossFilings.insuranceCsv(place, staff, ledgerOf, today),
                                "4대보험-신고-${place.name}.csv", "${place.name} 4대보험 취득·상실 표") }
                        }
                    }) { Text("4대보험 신고 표(CSV)") }
                }
                FilterChip(selected = ticked, onClick = { Filed.set(context, d.key, !ticked); done = Filed.all(context) }, label = { Text(if (ticked) "했어요 ✓" else "했어요") })
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (due.size > open.take(4).size) TextButton(onClick = { all = !all }) { Text(if (all) "접기" else "모두 보기 (${due.size})") }
        }
        Hint("금액은 대장과 같은 계산이고 참고예요. 반기별 납부·일용근로자는 따로 확인해요. 주민등록번호는 앱에 없어 신고할 때 넣어요.")
    }
}
