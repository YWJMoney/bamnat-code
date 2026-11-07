package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.pay.named
import com.bamnat.app.share.SharedSchedule
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.schedule.RosterAdopt
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 1.38 출시 점검 D9 (사용자 2026-11-07, 추천안): what is put in is a copy taken once; the leader's later change shows up here
 * again but does not move the user's own calendar or alarms by itself (that is 1.39's).
 */
internal const val ONCE_NOTE = "넣은 근무는 한 번만 옮겨요. 리더가 근무표를 고쳐도 내 달력과 알람은 저절로 바뀌지 않으니, 바뀌었다고 들으면 이 링크를 다시 열어 바뀐 날을 넣어 주세요."

/**
 * 1.38 [P3-X-03] 받은 근무표를 내 달력으로: the month's days of the link written into the user's own calendar (so the 근무 알람 rings
 * for them), with the pay the user's own 급여 설정 makes of it, next to what their calendar says now. A changed link shows its
 * changed days here again ("바뀐 날 2일, +38,000원"); one press puts them in, one more takes them back.
 */
@Composable
internal fun AdoptRosterCard(schedule: SharedSchedule, month: YearMonth, mine: ScheduleSnapshot) {
    val c = LocalContext.current.appContainer
    val app = c.appScope
    val settings by c.settingsRepository.settings.collectAsState()
    val profile by c.payStore.profile.collectAsState()
    val theirs = remember(schedule) {
        schedule.types.map { t ->
            val parts = t.time?.split("-")
            fun at(s: String?) = s?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
            RosterAdopt.Theirs(t.id, t.short, t.name, at(parts?.getOrNull(0)), at(parts?.getOrNull(1)), t.off)
        }
    }
    val pairs = remember(theirs, mine.shiftTypes) { RosterAdopt.match(theirs, mine.shiftTypes) }
    val days = remember(schedule, month) { schedule.days.filterKeys { YearMonth.from(it) == month }.mapValues { it.value.type } }
    val changes = remember(days, pairs, mine) { RosterAdopt.changes(days, pairs, mine.shiftTypes, mine.resolver::resolve) }
    var undo by remember(schedule, month) { mutableStateOf<Map<LocalDate, DayEntry?>?>(null) }
    if (days.isEmpty() || (changes.isEmpty() && undo == null)) return
    // The pay the user's own setup makes of the month, now and with the link's days (참고, 닫은 달이 아니어도).
    val pay = remember(changes, profile, mine, settings.extraKinds) {
        val p = profile?.named(settings.extraKinds) ?: return@remember null
        val byDate = mine.entries.associateBy { it.date }
        val merged = mine.entries.filter { it.date !in changes } + changes.map { (d, o) -> (byDate[d] ?: DayEntry(d)).copy(override = o) }
        runCatching {
            val calc = PayCalculator()
            calc.calculate(p, month, mine.resolver::resolve, checks = false).total to calc.calculate(p, month, mine.copy(entries = merged).resolver::resolve, checks = false).total
        }.getOrNull()
    }
    val missing = theirs.filter { pairs[it.id] == null && days.values.any { d -> d == it.id } }
    SectionCard(title = "내 달력에 넣기") {
        if (undo != null && changes.isEmpty()) {
            Text("${undo!!.size}일 넣었어요. 근무 알람도 이 근무대로 울려요.", style = MaterialTheme.typography.bodyMedium)
            Text(ONCE_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { val u = undo!!; undo = null; app.launch { u.forEach { (d, e) -> c.scheduleRepository.restoreDay(d, e) } } }) { Text("되돌리기") }
            return@SectionCard
        }
        Text("${month.monthValue}월 받은 근무 중 내 달력과 다른 날 ${changes.size}일", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        Text(changes.keys.take(8).joinToString(", ") { d -> "${d.dayOfMonth}일 ${mine.typesById[(changes[d] as ShiftOverride.Shift).shiftTypeId]?.label ?: ""}" } +
            if (changes.size > 8) " 외 ${changes.size - 8}일" else "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        pay?.let { (now, after) ->
            val diff = after - now
            Text("내 급여 설정으로 ${month.monthValue}월 예상 ${Fmt.won(after)}" + if (diff != 0L) " (지금보다 ${if (diff > 0) "+" else "−"}${Fmt.won(kotlin.math.abs(diff))})" else "",
                style = MaterialTheme.typography.bodyMedium)
            Text("야간·휴일·연장 수당까지 급여 화면과 같은 계산이에요(참고).", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } ?: Text("급여 설정을 하면 이 근무표로 이번 달 월급이 얼마인지도 바로 보여요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (missing.isNotEmpty()) Text("내 근무에 없는 ${missing.joinToString(", ") { "'${it.name}'" }}이 있는 날은 그대로 둬요. 설정 › 근무 종류에서 만들면 같이 넣어요.",
            style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning)
        Text(ONCE_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val todo = changes
                app.launch {
                    val before = todo.mapValues { (d, o) -> c.scheduleRepository.updateDay(d) { it.copy(override = o) } }
                    undo = before
                }
            }) { Text("${changes.size}일 내 달력에 넣기") }
        }
    }
}
