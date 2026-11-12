package com.bamnat.app.ui.team

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Check
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.team.AskRound
import com.bamnat.core.team.AskRules
import com.bamnat.core.team.TeamAsk
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamWish
import com.bamnat.core.team.WishKind
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import com.bamnat.app.ui.theme.Tone
import com.bamnat.app.ui.theme.color

/** The kinds a leader may take requests for, in the order the setup shows them (병가 is never one: 건강 사유는 리더 앱에서만). */
internal val ASK_KINDS = listOf(WishKind.OFF, WishKind.LEAVE, WishKind.HALF, WishKind.EDU, WishKind.SHIFT, WishKind.AVOID_NIGHT)

/**
 * 1.38 [P3-L-14] 신청 받기 열기: the paper sheet's rules ("평일 2명, 주말 3명") and a 마감, then a link per member. 공휴일 of the month
 * count as 주말 by themselves.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun AskSetupDialog(month: YearMonth, members: Int, onDismiss: () -> Unit, onOpen: (AskRules) -> Unit) {
    var weekday by rememberSaveable { mutableIntStateOf(2) }
    var weekend by rememberSaveable { mutableIntStateOf(3) }
    var person by rememberSaveable { mutableIntStateOf(0) }
    // 마감: a week from today at 18시, never after the month starts.
    val firstDay = month.atDay(1)
    var closeDay by rememberSaveable { mutableStateOf(minOf(Today.date().plusDays(7), firstDay.minusDays(1)).coerceAtLeast(Today.date()).toString()) }
    var closeHour by rememberSaveable { mutableIntStateOf(18) }
    var kinds by remember { mutableStateOf(setOf(WishKind.OFF, WishKind.LEAVE, WishKind.SHIFT)) }
    var firstCome by rememberSaveable { mutableStateOf(true) }
    var names by rememberSaveable { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    val closesAt = LocalDate.parse(closeDay).atTime(LocalTime.of(closeHour % 24, 0)).plusDays(if (closeHour == 24) 1 else 0)
        .atZone(ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${Fmt.monthShort(month)} 근무 신청 받기") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("팀원 ${members}명에게 신청 링크를 하나씩 보내요. 팀원은 앱 없이 달력에서 날을 눌러 신청하고, 날마다 몇 자리 남았는지 봐요.",
                style = MaterialTheme.typography.bodySmall)
            Stepper("평일 하루 쉬는 신청", weekday, { weekday = it }, zero = "제한 없음")
            Stepper("주말·공휴일 하루", weekend, { weekend = it }, zero = "제한 없음")
            Stepper("한 사람 쉬는 신청", person, { person = it }, zero = "제한 없음", unit = "일")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("마감", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { picking = true }) { Text(Fmt.monthDay(LocalDate.parse(closeDay))) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(12, 18, 24).forEach { h -> FilterChip(selected = closeHour == h, onClick = { closeHour = h }, label = { Text(if (h == 24) "자정" else "${h}시") }) }
            }
            // PC 10차(중): which kinds are on was not plain; each on chip has a ✓ and the line says how many, and none on is said.
            Text("받을 신청 · 켜진 ${kinds.size}가지(누르면 켜고 꺼요)", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ASK_KINDS.forEach { k ->
                    val on = k in kinds
                    FilterChip(selected = on, onClick = { kinds = if (on) kinds - k else kinds + k },
                        leadingIcon = if (on) { { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, null, Modifier.size(18.dp)) } } else null,
                        label = { Text(if (k == WishKind.SHIFT) "근무 희망(D·E·N)" else k.short) })
                }
            }
            if (kinds.isEmpty()) Text("하나도 켜지 않으면 팀원이 신청할 수 없어요.", style = MaterialTheme.typography.labelSmall,
                color = Tone.Caution.color())
            Text("자리가 다 찬 날", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = firstCome, onClick = { firstCome = true }, label = { Text("먼저 신청한 순(종이처럼)") })
                FilterChip(selected = !firstCome, onClick = { firstCome = false }, label = { Text("다 받고 내가 정하기") })
            }
            if (!firstCome) Text("넘친 날은 지난달 신청을 덜 지킨 사람부터 위에 보여 줘요.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("신청한 사람 이름 보이기", style = MaterialTheme.typography.bodyMedium)
                    Text("끄면 팀원에게는 날마다 몇 명인지만 보여요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = names, onCheckedChange = { names = it })
            }
            Text("서버에는 팀 이름·팀원 이름·신청한 날과 종류·한 줄만 남고, 60일 안에 지워져요. 병가 같은 건강 사유는 팀원 신청에 없어요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        TextButton(enabled = kinds.isNotEmpty() && closesAt > Today.millis(), onClick = {
            val holidays = (1..month.lengthOfMonth()).map(month::atDay).filter(KoreanHolidays::isHoliday).toSet()
            onOpen(AskRules(weekday, weekend, person, closesAt, kinds, firstCome, holidays, showNames = names))
        }) { Text("링크 만들기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
    if (picking) {
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = LocalDate.parse(closeDay).toEpochDay() * 86_400_000L)
        com.bamnat.app.ui.components.DatePickerDialog(onDismissRequest = { picking = false }, confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { closeDay = LocalDate.ofEpochDay(it / 86_400_000L).toString() }; picking = false }) { Text("고르기") }
        }, dismissButton = { TextButton(onClick = { picking = false }) { Text("닫기") } }) { androidx.compose.material3.DatePicker(state) }
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit, zero: String, unit: String = "명") {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        TextButton(onClick = { onChange((value - 1).coerceAtLeast(0)) }, enabled = value > 0) { Text("−") }
        Text(if (value == 0) zero else "$value$unit", style = MaterialTheme.typography.bodyMedium)
        TextButton(onClick = { onChange((value + 1).coerceAtMost(30)) }) { Text("+") }
    }
}

/**
 * 1.38 [P3-L-14] The round on the board: how many asked and opened, the 마감, the days over the line (in the round's order), the
 * members' lines, and what the leader can do next. [misses]: wishes not kept last month by member, for 공정 배정.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AskCard(
    round: AskRound, members: List<TeamMember>, wishes: List<TeamWish>, shifts: List<TeamShift>, busy: Boolean, misses: Map<String, Int>,
    pullFailed: Boolean = false,
    onPull: () -> Unit, onSend: () -> Unit, onClose: (Boolean) -> Unit, onEnd: () -> Unit,
) {
    val names = members.associate { it.id to it.name }
    val linked = wishes.filter { it.at != null }
    val askers = linked.map { it.memberId }.toSet()
    val now = Today.millis()
    val open = round.open(now)
    val days = remember(round, linked, misses) { TeamAsk.order(round.rules, linked, misses) }
    val over = days.filter { it.over.isNotEmpty() }
    val closes = round.rules.closesAt?.let { java.time.Instant.ofEpochMilli(it).atZone(ZoneId.of("Asia/Seoul")) }
    SectionCard(title = "${round.month.monthValue}월 신청 ${if (open) "받는 중" else "마감"}") {
        Text("신청한 사람 ${askers.size}/${members.size}명 · 링크 연 사람 ${round.seen.size}명" +
            (closes?.let { " · 마감 ${it.monthValue}/${it.dayOfMonth} ${"%02d".format(it.hour)}:00" } ?: ""), style = MaterialTheme.typography.bodyMedium)
        val notOpened = members.filter { it.id in round.links && it.id !in round.seen }
        if (notOpened.isNotEmpty() && open) Text("아직 안 연 사람: " + notOpened.joinToString(", ") { it.name }, style = MaterialTheme.typography.bodySmall,
            color = Tone.Caution.color())
        if (over.isNotEmpty()) {
            Text("자리보다 많이 신청한 날 ${over.size}일", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            over.take(6).forEach { d ->
                Text("${Fmt.monthDay(d.date)} (${d.cap}자리): " + d.inside.joinToString(", ") { names[it.memberId].orEmpty() } +
                    " | 넘침 " + d.over.joinToString(", ") { names[it.memberId].orEmpty() }, style = MaterialTheme.typography.bodySmall)
            }
            Text(if (round.rules.firstCome) "먼저 신청한 순이에요. 넘친 사람은 표에서 직접 정해 주세요." else "지난달 신청을 덜 지킨 사람이 앞에 있어요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (round.notes.isNotEmpty()) {
            Text("팀원 한 줄", style = MaterialTheme.typography.labelLarge)
            round.notes.forEach { (id, n) -> Text("${names[id] ?: "?"}: $n", style = MaterialTheme.typography.bodySmall) }
        }
        val shiftWishes = linked.count { it.kind == WishKind.SHIFT }
        if (shiftWishes > 0) Text("근무 희망 ${shiftWishes}개(" + shifts.filter { s -> linked.any { it.shiftId == s.id } }.joinToString(" ") { s -> "${s.label} ${linked.count { it.shiftId == s.id }}" } +
            ")는 자동 채우기가 먼저 넣어요.", style = MaterialTheme.typography.bodySmall)
        if (round.token == null) Text("이 휴대폰에 신청 링크 열쇠가 없어요(암호 없는 백업에서 되살림). 새로 열어 주세요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onPull, enabled = !busy && round.token != null) { Text("신청 가져오기") }
            OutlinedButton(onClick = onSend) { Text("링크 다시 보내기") }
            OutlinedButton(onClick = { onClose(open) }, enabled = !busy && round.token != null) { Text(if (open) "지금 마감" else "다시 열기") }
            TextButton(onClick = onEnd, enabled = !busy) { Text("신청 끝내기") }
        }
        round.syncedAt?.let {
            // 1.39 G2 통합 7차 PC: '22:22에 가져왔어요' on a phone at 13:22 (GMT). When it was fetched is the phone's own time, like its
            // clock; the 마감 above stays in 한국 시각, as the 신청 page shows it.
            val t = java.time.Instant.ofEpochMilli(it).atZone(com.bamnat.core.time.Today.zone())
            val at = "${t.monthValue}/${t.dayOfMonth} ${"%02d:%02d".format(t.hour, t.minute)}"
            // 1.39.1 9차 PC 리더: when the fetch on opening failed, say so with a way to try again instead of the old time alone.
            if (pullFailed) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("새 신청을 못 가져왔어요($at 것을 보여 줘요)", style = MaterialTheme.typography.labelSmall,
                    color = Tone.Caution.color(), modifier = Modifier.weight(1f))
                TextButton(onClick = onPull, enabled = !busy && round.token != null) { Text("다시") }
            } else Text("${at}에 가져왔어요. 화면을 열 때마다 새로 가져와요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The empty cell's mark for a request: 오·연·반·교 or the wished work ("D?"). */
internal fun wishMark(w: TeamWish, shifts: Map<Long, TeamShift>): String = when (w.kind) {
    WishKind.SHIFT -> (w.shiftId?.let(shifts::get)?.label ?: "희") + "?"
    WishKind.AVOID_NIGHT -> "N✕"
    WishKind.WORK -> "근?"
    else -> w.kind.short.take(1)
}
