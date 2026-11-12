package com.bamnat.app.ui.work

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.theme.bamnat
import com.bamnat.core.boss.BossFilings
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

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
/**
 * 2.0.0 [RVJ-38] (검토 PC-100) A line whose amount leaves out someone with no 입사일 says so right under its title, with what it comes to
 * with them; pressing it opens that person's 직원 정보 ([onEditStaff]). [RVJ-39] (검토 PC-101) While no 급여 주는 날 is picked, one line at the
 * top says the 지급분 lines count it as the next month's 1일 ([onPayDay] opens 사업장 설정), and 모두 보기 lists the rest as date · name ·
 * amount, a press opening its words.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FilingsCard(place: Workplace, staff: List<StaffMember>, work: List<StaffWork>, onEditStaff: (StaffMember) -> Unit = {}, onPayDay: () -> Unit = {}) {
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
            (0..4).associate { i -> first.plusMonths(i.toLong()).let { m -> m to store.ledgerOf(place, m) } }
        }
    }
    val got = ledgers ?: return
    val ledgerOf = { m: YearMonth -> got[m] ?: PayrollLedger.month(place, staff, m, emptyList()) }
    val due = remember(got, staff, place, today) { BossFilings.upcoming(place, staff, today, ledgerOf) }
    val open = due.filter { it.key !in done }
    if (due.isEmpty()) return
    // [RVJ-39] A date close or past is a 주의 (주황), not a 위험: two or three lines in red read as one alarm (조건 6).
    val warn = MaterialTheme.bamnat.caution
    // The rows opened from 모두 보기's short list.
    var opened by rememberSaveable { mutableStateOf(emptyList<String>()) }
    SectionCard(title = "다가올 신고·지급" + if (open.isNotEmpty()) " ${open.size}" else " ✓") {
        // [RVJ-39] Which month's work a '○월 지급분' is, while it is worked out from the default pay day (a fact: ⓘ, not 주의).
        if (place.payday == null) ToneLine("급여 주는 날을 안 골라 일한 달의 다음 달 1일에 준 것으로 셌어요 · 급여 주는 날 고르기", tone = Tone.Plain, onClick = onPayDay)
        val first = open.take(4)
        val shown = if (all) due else first
        shown.forEach { d ->
            val n = d.daysFrom(today)
            val ticked = d.key in done
            val amount = d.amount?.takeIf { it > 0 }?.let { " ${Fmt.won(it)}" } ?: ""
            val whoMissing = d.missing?.let { m -> staff.firstOrNull { it.id == m.staffIds.first() } }
            if (d !in first && d.key !in opened) {
                // [RVJ-39] 모두 보기: date · name · amount, the words a press away (일곱 줄이 한 화면을 넘던 것).
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClickLabel = "설명 보기") { opened = opened + d.key }.heightIn(min = 48.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${Fmt.monthDay(d.date)} · ${d.title}$amount" + if (ticked) " · 했어요" else "", style = MaterialTheme.typography.bodyMedium,
                        color = if (ticked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
                    FilterChip(selected = ticked, onClick = { Filed.set(context, d.key, !ticked); done = Filed.all(context) }, label = { Text(if (ticked) "했어요 ✓" else "했어요") })
                }
                // Who is left out stays said even on the short list: it changes what is filed.
                d.missing?.let { m -> ToneLine(m.line(d.amount), onClick = whoMissing?.let { s -> { onEditStaff(s) } }) }
                return@forEach
            }
            // [RVJ-38] Only the date and the title share their line with 했어요; what follows takes the card's width (at 200% on the
            // folded width the 주의 line under the amount was squeezed to a third of it, seven lines).
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.monthDay(d.date)} · " + when { ticked -> "했어요"; n < 0 -> "${-n}일 지났어요"; n == 0L -> "오늘"; else -> "${n}일 남음" },
                        style = MaterialTheme.typography.labelMedium, color = if (!ticked && n <= 3) warn else MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(d.title + amount, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                FilterChip(selected = ticked, onClick = { Filed.set(context, d.key, !ticked); done = Filed.all(context) }, label = { Text(if (ticked) "했어요 ✓" else "했어요") })
            }
            Column(Modifier.fillMaxWidth()) {
                // [RVJ-38] Right by the amount: who it leaves out, what it comes to with them, and a press to put the 입사일 in.
                d.missing?.let { m -> ToneLine(m.line(d.amount), onClick = whoMissing?.let { s -> { onEditStaff(s) } }) }
                Hint(d.detail + if (d.where.isNotEmpty()) " · ${d.where}" else "")
                if (d.kind == BossFilings.Kind.WITHHOLD) d.worked?.let { worked ->
                    // [RVJ-38] The month the line is about (a same-month pay day is one month before the 10일, not two), and the same people
                    // as the line ([BossFilings.filed]).
                    TextButton(onClick = {
                        scope.launch {
                            val l = BossFilings.filed(ledgerOf(worked), worked, today)
                            com.bamnat.app.report.ReportShare.orTell { com.bamnat.app.report.ReportShare.csv(context, BossFilings.withheldCsv(place, worked, BossFilings.withheld(place, l)),
                                "원천세-${place.name}-$worked.csv", "${place.name} ${Fmt.month(worked)}분 원천세 표") }
                        }
                    }) { Text("원천세 표(CSV)") }
                }
                if (d.kind == BossFilings.Kind.INSURE_IN || d.kind == BossFilings.Kind.INSURE_OUT) TextButton(onClick = {
                    scope.launch {
                        com.bamnat.app.report.ReportShare.orTell { com.bamnat.app.report.ReportShare.csv(context, BossFilings.insuranceCsv(place, staff, ledgerOf, today),
                            "4대보험-신고-${place.name}.csv", "${place.name} 4대보험 취득·상실 표") }
                    }
                }) { Text("4대보험 신고 표(CSV)") }
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (due.size > open.take(4).size) TextButton(onClick = { all = !all }) { Text(if (all) "접기" else "모두 보기 (${due.size})") }
        }
        // Said on the lines themselves now ([RVJ-38]); this one line is left for when none of the shown lines carries it.
        BossFilings.noHireDate(staff, today).takeIf { it.isNotEmpty() && shown.none { d -> d.missing != null } }?.let { l ->
            ToneLine("입사일이 없는 ${l.joinToString(", ") { it.name.ifBlank { "직원" } }}님은 지난달 신고에서 뺐어요 · 입사일 넣기", onClick = { onEditStaff(l.first()) })
        }
        Hint("금액은 대장과 같은 계산이고 참고예요. 반기별 납부·일용근로자는 따로 확인해요. 주민등록번호는 앱에 없어 신고할 때 넣어요.")
    }
}
