package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.money.EnvelopeKind
import com.bamnat.core.money.Envelopes
import com.bamnat.core.pay.DayWhatIf
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.WageType
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * 1.37 [LEDH-04] '이날 일하면?' in the day sheet's 돈: on a day off "주간 대근하면 +96,000원 (세후 약 +84,000원) · ‘여행’ 봉투 5일
 * 당김"; on a working day 연차 (a 월급 stays the same, said so) or 쉬는 날. Nothing is saved. Turned off in 기능 켜고 끄기.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DayWhatIfSection(date: LocalDate, snapshot: ScheduleSnapshot, profile: PayProfile, calendarId: String) {
    val day = remember(snapshot, date) { snapshot.resolver.resolve(date) }
    val changes = remember(day, snapshot) { DayWhatIf.changes(day, snapshot.shiftTypes) }
    if (changes.isEmpty()) return
    var picked by rememberSaveable(date) { mutableStateOf(0) }
    val change = changes[picked.coerceIn(0, changes.lastIndex)]
    val answer by produceState<DayWhatIf.Answer?>(null, snapshot, profile, date, change) {
        value = withContext(Dispatchers.Default) {
            runCatching {
                val entry = DayWhatIf.entry(snapshot.resolver.entryOn(date), date, change)
                val after = ScheduleResolver(snapshot.shiftTypes, snapshot.patterns, snapshot.entries.filter { it.date != date } + entry,
                    snapshot.kinds, snapshot.legalBreaks)
                DayWhatIf.answer(profile, date, change, snapshot.resolver::resolve, after::resolve)
            }.getOrNull()
        }
    }
    val context = LocalContext.current
    val goal by remember(calendarId) {
        context.appContainer.scopeOf(calendarId).money.envelopes.map { (list, moves) ->
            list.filter { !it.closed && it.kind == EnvelopeKind.GOAL && it.target > 0 }
                .sortedWith(compareBy({ it.order }, { it.id })).firstOrNull()?.let { Envelopes.statusOf(it, moves, Today.date()) }
        }
    }.collectAsState(null)
    com.bamnat.app.ui.guide.FeatureCard("money.whatIf", calendarId) {
        Column(Modifier.padding(top = 8.dp)) {
            // 2.0.0 [RVK-43] (검토 T06a-09) On a working day the chips are 연차·쉬는 날, so the question is '이날 쉬면?'.
            Text(DayWhatIf.question(changes), style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                changes.forEachIndexed { i, c -> FilterChip(selected = i == picked, onClick = { picked = i }, label = { Text(c.label) }) }
            }
            val a = answer
            val words = when {
                a == null -> "계산하는 중이에요"
                a.same && change == DayWhatIf.Change.Leave && profile.wageType == WageType.MONTHLY -> "연차는 유급이라 이달 급여가 그대로예요"
                a.same -> "이달 급여가 그대로예요"
                else -> buildString {
                    append("이달 급여 ${sign(a.gross)}${Fmt.won(kotlin.math.abs(a.gross))}")
                    a.net?.let { append(" (세후 약 ${sign(it)}${Fmt.won(kotlin.math.abs(it))})") }
                }
            }
            Text(words, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
            val gain = a?.let { it.net ?: it.gross } ?: 0
            goal?.takeIf { gain > 0 && it.left > 0 }?.let { s ->
                val sooner = Envelopes.soonerBy(s, gain, Today.date())
                val share = DayWhatIf.envelopeShare(gain, s.left)
                val line = when {
                    sooner != null && sooner > 0 -> "‘${s.envelope.name}’ 봉투에 넣으면 ${sooner}일 당겨져요"
                    share != null -> "‘${s.envelope.name}’ 봉투 남은 돈의 ${share}%예요"
                    else -> null
                }
                line?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            }
            Text("급여 설정대로 다시 셈한 어림이고 저장되지 않아요." + if (a?.net == null) " 세후는 급여에서 세금 방식을 고르면 보여요." else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun sign(v: Long) = if (v > 0) "+" else if (v < 0) "−" else ""
