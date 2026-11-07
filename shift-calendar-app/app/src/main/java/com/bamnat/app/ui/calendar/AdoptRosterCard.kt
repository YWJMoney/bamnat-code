package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import java.time.YearMonth

/**
 * 1.38 출시 점검 D9 (사용자 2026-11-07, 추천안): what is put in was a copy taken once. 1.39 [TEAM-22]: the leader's later change now
 * comes by itself (or is asked, as '이 근무표가 바뀌면' says), so the note says that instead.
 */
internal const val ONCE_NOTE = "넣은 뒤 리더가 근무표를 고치면 6시간 안에 받아서, 아래 '이 근무표가 바뀌면'에 고른 대로 내 달력과 알람에 넣거나 여쭤봐요. 내가 직접 바꾼 날은 그대로 둬요."

/**
 * 1.38 [P3-X-03] 받은 근무표를 내 달력으로: the month's days of the link written into the user's own calendar (so the 근무 알람 rings
 * for them), with the pay the user's own 급여 설정 makes of it, next to what their calendar says now. A changed link shows its
 * changed days here again ("바뀐 날 2일, +38,000원"); one press puts them in, one more takes them back.
 */
@Composable
internal fun AdoptRosterCard(id: String, schedule: SharedSchedule, month: YearMonth, mine: ScheduleSnapshot) {
    val c = LocalContext.current.appContainer
    val app = c.appScope
    val settings by c.settingsRepository.settings.collectAsState()
    val profile by c.payStore.profile.collectAsState()
    val theirs = remember(schedule) { com.bamnat.app.share.RosterFollower.theirTypes(schedule) }
    val pairs = remember(theirs, mine.shiftTypes) { RosterAdopt.match(theirs, mine.shiftTypes) }
    val days = remember(schedule, month) { schedule.days.filterKeys { YearMonth.from(it) == month }.mapValues { it.value.type } }
    val all = remember(days, pairs, mine) { RosterAdopt.changes(days, pairs, mine.shiftTypes, mine.resolver::resolve) }
    // 1.39 PC a483ca15 (손으로 바꾼 2일·15일까지 '다른 날'로 세어 넣음): on a link already followed, a day that differs from the copy my
    // days were last matched to was changed by hand; it is left out unless asked for, as '직접 바꾼 날은 그대로' says.
    val cal = c.received.calendars.collectAsState().value.firstOrNull { it.id == id }
    val hand = remember(all, cal?.basis, cal?.mine, cal?.adopted, pairs, mine, month) {
        val basis = cal?.basis?.takeIf { cal.mine || cal.adopted }?.let { runCatching { SharedSchedule.parse(it) }.getOrNull() }
            ?: return@remember emptySet<LocalDate>()
        val was = basis.days.filterKeys { YearMonth.from(it) == month }.mapValues { it.value.type }
        all.keys intersect RosterAdopt.changes(was, pairs, mine.shiftTypes, mine.resolver::resolve).keys
    }
    var withHand by remember(id, month) { mutableStateOf(false) }
    val changes = if (withHand) all else all.filterKeys { it !in hand }
    var undo by remember(schedule, month) { mutableStateOf<Map<LocalDate, DayEntry?>?>(null) }
    if (days.isEmpty() || (all.isEmpty() && undo == null)) return
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
        if (undo != null && (changes.isEmpty() || all.isEmpty())) {
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
        if (hand.isNotEmpty()) {
            val handDays = hand.sorted().joinToString(", ") { "${it.dayOfMonth}일" }
            Text(if (withHand) "직접 바꾼 날($handDays)도 함께 넣어요." else "직접 바꾼 날은 빼고 넣어요: $handDays",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { withHand = !withHand }) { Text(if (withHand) "직접 바꾼 날은 빼기" else "직접 바꾼 날도 넣기") }
        }
        if (missing.isNotEmpty()) Text("내 근무에 없는 ${missing.joinToString(", ") { "'${it.name}'" }}이 있는 날은 그대로 둬요. 설정 › 근무 종류에서 만들면 같이 넣어요.",
            style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning)
        Text(ONCE_NOTE, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val todo = changes
                app.launch {
                    val before = todo.mapValues { (d, o) -> c.scheduleRepository.updateDay(d) { it.copy(override = o) } }
                    undo = before
                    // 1.39 [TEAM-22] Put in once: when the 리더 changes this link later, the change is offered (or as set below).
                    c.received.markAdopted(id)
                    com.bamnat.app.share.RosterFollower.sync(c.appContext, c)
                }
            }, enabled = changes.isNotEmpty()) { Text("${changes.size}일 내 달력에 넣기") }
        }
    }
}

/**
 * 1.39 [TEAM-22] D9: what a followed link's change did, over the user's own calendar until closed: put in (되돌리기), asked
 * (내 달력에 넣기), or the link gone (the user's days stay).
 */
@Composable
internal fun RosterFollowLine(cal: com.bamnat.app.share.ReceivedCalendar, modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier) {
    val note = cal.note ?: return
    val c = LocalContext.current.appContainer
    val app = c.appScope
    val (title, text) = remember(cal, note) { com.bamnat.app.share.RosterFollower.words(cal, note) }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(14.dp), modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(text, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    note.kind == com.bamnat.app.share.FollowNote.DONE && note.undo.isNotEmpty() ->
                        TextButton(onClick = { app.launch { com.bamnat.app.share.RosterFollower.takeBack(c, cal.id) } }) { Text("되돌리기") }
                    note.kind == com.bamnat.app.share.FollowNote.ASK && note.apply.isNotEmpty() ->
                        TextButton(onClick = { app.launch { com.bamnat.app.share.RosterFollower.putIn(c, cal.id) } }) { Text("${note.apply.size}일 내 달력에 넣기") }
                }
                TextButton(onClick = { com.bamnat.app.share.RosterFollower.close(c, cal.id) }) { Text("닫기") }
            }
        }
    }
}

/** 1.39 [TEAM-22] 근무표가 바뀌면: 바로 내 달력에 넣기 / 넣을지 물어보기 / 넣지 않기, for one received link. */
@Composable
internal fun FollowModeRow(cal: com.bamnat.app.share.ReceivedCalendar) {
    val c = LocalContext.current.appContainer
    val mode = com.bamnat.app.share.RosterFollower.mode(cal)
    SectionCard(title = "이 근무표가 바뀌면") {
        Text("리더가 근무표를 고쳐 다시 보내면 6시간 안에 받아요. 내가 직접 바꾼 날은 그대로 둬요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.bamnat.app.share.RosterFollower.Mode.entries.forEach { m ->
                com.bamnat.app.ui.components.FilterChip(selected = m == mode, onClick = {
                    c.received.setFollow(cal.id, m.key)
                    com.bamnat.app.share.RosterFollower.sync(c.appContext, c)
                }, label = { Text(m.words) })
            }
        }
    }
}

