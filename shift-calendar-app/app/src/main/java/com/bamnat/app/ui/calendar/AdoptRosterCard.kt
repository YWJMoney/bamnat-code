package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
 * comes by itself (or is asked, as '이 근무표가 바뀌면' says), so the note says that instead. [RVG-21] 검토 HQ-19: 리더 only for a
 * 리더's team link ([com.bamnat.app.share.ReceivedCalendar.fromLeader]); a 가족·동료's link is changed by 보낸 분.
 */
internal fun onceNote(leader: Boolean) =
    "넣은 뒤 ${changer(leader)} 근무표를 고치면 6시간 안에 받아서, 아래 '이 근무표가 바뀌면'에 고른 대로 내 달력과 알람에 넣거나 여쭤봐요. 내가 직접 바꾼 날은 그대로 둬요."

/** [RVG-21] Who changes the link later, as a sentence's subject. */
internal fun changer(leader: Boolean) = if (leader) "리더가" else "보낸 분이"

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
    val hand = remember(all, cal?.basis, cal?.mine, cal?.adopted, cal?.note, pairs, mine, month) {
        val basis = cal?.basis?.takeIf { cal.mine || cal.adopted }?.let { runCatching { SharedSchedule.parse(it) }.getOrNull() }
            ?: return@remember emptySet<LocalDate>()
        val was = basis.days.filterKeys { YearMonth.from(it) == month }.mapValues { it.value.type }
        // 1.39 PC 5c4ad849: the days the leader changed that are still asked (넣을지 물어보기) were matched already (basis is the new
        // copy) but not put in: they are the link's change, not the user's.
        val asked = cal.note?.takeIf { it.kind == com.bamnat.app.share.FollowNote.ASK }?.apply?.keys.orEmpty()
        (all.keys intersect RosterAdopt.changes(was, pairs, mine.shiftTypes, mine.resolver::resolve).keys) - asked
    }
    var withHand by remember(id, month) { mutableStateOf(false) }
    val changes = if (withHand) all else all.filterKeys { it !in hand }
    var undo by remember(schedule, month) { mutableStateOf<com.bamnat.app.share.PutIn.Undo?>(null) }
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
    // 2.0.0 [RVG-33] 검토 HQ-55 (조건 4): what happens after 넣기 is one line with ⓘ (the common [Explain]); the rest of the old
    // paragraph (the pay note, how a later change comes) is behind ⓘ, and '이 근무표가 바뀌면' just below says it in full anyway.
    val afterNote = onceNote(cal?.fromLeader == true) + " " + if (pay != null) "예상 급여는 야간·휴일·연장 수당까지 급여 화면과 같은 계산이에요(참고)."
        else "급여 설정을 하면 이 근무표로 이번 달 월급이 얼마인지도 바로 보여요."
    val followLine = @Composable {
        com.bamnat.app.ui.components.Explain("adopt:after", afterNote, summary = "바뀌면 다시 받아요", iconOnly = false)
    }
    SectionCard(title = "내 달력에 넣기") {
        if (undo != null && (changes.isEmpty() || all.isEmpty())) {
            Text("${undo!!.days}일 넣었어요. 근무 알람도 이 근무대로 울려요.", style = MaterialTheme.typography.bodyMedium)
            followLine()
            // [RVG-31] 검토 PC-95: back to before '넣기' only (the days and the link's answers), so the days can be put in again.
            TextButton(onClick = { val u = undo!!; undo = null; app.launch { u.back() } }) { Text("되돌리기") }
            return@SectionCard
        }
        Text("${month.monthValue}월 내 달력과 다른 날 ${changes.size}일", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        // [RVG-33] The days are on the calendar just above; here one line of them, cut where it ends.
        Text(changes.keys.joinToString(", ") { d -> "${d.dayOfMonth}일 ${mine.typesById[(changes[d] as ShiftOverride.Shift).shiftTypeId]?.label ?: ""}" },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        pay?.let { (now, after) ->
            val diff = after - now
            Text("${month.monthValue}월 예상 ${Fmt.won(after)}" + if (diff != 0L) " (지금보다 ${if (diff > 0) "+" else "−"}${Fmt.won(kotlin.math.abs(diff))})" else "",
                style = MaterialTheme.typography.bodyMedium)
        }
        if (hand.isNotEmpty()) {
            val handDays = hand.sorted().joinToString(", ") { "${it.dayOfMonth}일" }
            Text(if (withHand) "직접 바꾼 날($handDays)도 함께 넣어요." else "직접 바꾼 날은 빼고 넣어요: $handDays",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { withHand = !withHand }) { Text(if (withHand) "직접 바꾼 날은 빼기" else "직접 바꾼 날도 넣기") }
        }
        if (missing.isNotEmpty()) com.bamnat.app.ui.components.ToneLine("내 근무에 없는 ${missing.joinToString(", ") { "'${it.name}'" }}이 있는 날은 그대로 둬요. 설정 › 근무 종류에서 만들면 같이 넣어요.")
        followLine()
        val putIn = {
            val todo = changes
            app.launch { undo = com.bamnat.app.share.PutIn.apply(c, id, todo) }
            Unit
        }
        val words = "${changes.size}일 내 달력에 넣기"
        // [RVG-23] 검토 HQ-21 (조건 2·10): a link only seen beside mine (neither the user's own nor put in) does not lead with putting
        // it in. Right after '내 근무표가 아니에요' took its days out, the biggest thing on screen was the way to put them back.
        val seenOnly = cal == null || !cal.mine && !cal.adopted
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (seenOnly) androidx.compose.material3.OutlinedButton(onClick = putIn, enabled = changes.isNotEmpty(),
                modifier = androidx.compose.ui.Modifier.heightIn(min = 48.dp)) { Text(words) }
            else Button(onClick = putIn, enabled = changes.isNotEmpty()) { Text(words) }
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
        shape = MaterialTheme.shapes.medium, modifier = modifier.fillMaxWidth()) {
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

/**
 * 1.39 [TEAM-22] 근무표가 바뀌면: 바로 내 달력에 넣기 / 넣을지 물어보기 / 넣지 않기, for one received link. [RVG-20] For the link the
 * user's own work came from, '내 근무표가 아니에요' too ([NotMineButton]); [onSay] says what it did, with 되돌리기.
 */
@Composable
internal fun FollowModeRow(cal: com.bamnat.app.share.ReceivedCalendar, onSay: ((com.bamnat.app.ui.UndoMessage) -> Unit)? = null) {
    val c = LocalContext.current.appContainer
    val mode = com.bamnat.app.share.RosterFollower.mode(cal)
    SectionCard(title = "이 근무표가 바뀌면") {
        // [RVG-21] 검토 HQ-19: a 리더 sends the team's 근무표 again; anyone else's link changes as they change their own.
        Text(if (cal.fromLeader) "리더가 근무표를 고쳐 다시 보내면 6시간 안에 받아요. 내가 직접 바꾼 날은 그대로 둬요."
            else "보낸 분이 근무표를 고치면 6시간 안에 받아요. 내가 직접 바꾼 날은 그대로 둬요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.bamnat.app.share.RosterFollower.Mode.entries.forEach { m ->
                com.bamnat.app.ui.components.FilterChip(selected = m == mode, onClick = {
                    c.received.setFollow(cal.id, m.key)
                    com.bamnat.app.share.RosterFollower.sync(c.appContext, c)
                }, label = { Text(m.words) })
            }
        }
        if (cal.mine && onSay != null) NotMineButton(cal, onSay)
    }
}

/**
 * [RVG-20] 2.0.0 검토 HQ-09 (조건 10): '내 근무표예요' was picked for a 가족·동료's link, found after the setup. '넣지 않기' only stops
 * later changes; this takes out the days the link put in. How many is said first (and how many the user changed by hand, which stay);
 * the line after it has 되돌리기 for both the days and the link.
 */
@Composable
private fun NotMineButton(cal: com.bamnat.app.share.ReceivedCalendar, onSay: (com.bamnat.app.ui.UndoMessage) -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var asking by remember(cal.id) { mutableStateOf<com.bamnat.core.schedule.LinkDays.Split?>(null) }
    // Its words line up with the card's (a text button's own side room is 12dp).
    TextButton(onClick = { scope.launch { asking = com.bamnat.app.share.NotMine.plan(c, cal.id) } },
        modifier = androidx.compose.ui.Modifier.offset(x = (-12).dp)) { Text("내 근무표가 아니에요") }
    val split = asking ?: return
    val out = split.theirs.size
    // "N일 치": a count of days, never read as a date ("8일" alone reads as the 8th).
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = { asking = null },
        title = { Text(if (out > 0) "${cal.roster}가 넣은 ${out}일 치를 뺄까요?" else "${cal.roster}를 받은 달력으로 옮길까요?") },
        text = {
            Text(listOfNotNull(
                "직접 바꾼 ${split.kept.size}일 치는 그대로 둬요.".takeIf { split.kept.isNotEmpty() },
                "이 근무표는 받은 달력에서 보기만 해요.",
            ).joinToString(" "))
        },
        confirmButton = {
            TextButton(onClick = {
                asking = null
                // Outlives this card: once the link is not the user's own, the card goes.
                c.appScope.launch {
                    val undo = com.bamnat.app.share.NotMine.apply(c, cal.id, split)
                    onSay(com.bamnat.app.ui.UndoMessage(
                        if (out > 0) "${cal.roster}가 넣은 ${out}일 치를 뺐어요" else "${cal.roster}는 받은 달력에서 보기만 해요",
                        undo = undo,
                    ))
                }
            }) { Text(if (out > 0) "${out}일 치 빼기" else "옮기기") }
        },
        dismissButton = { TextButton(onClick = { asking = null }) { Text("그대로 두기") } },
    )
}

