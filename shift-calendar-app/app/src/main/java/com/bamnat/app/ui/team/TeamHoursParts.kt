package com.bamnat.app.ui.team

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.Explain
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.components.SideOrBelow
import com.bamnat.app.ui.components.TimeRange
import com.bamnat.app.data.rules
import com.bamnat.app.ui.components.TimeRangeDialog
import com.bamnat.app.ui.components.TimeRangePart
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.boss.Workplace
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.schedule.LegalBreak
import com.bamnat.core.schedule.RestRules
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamTimes
import java.time.LocalTime

/**
 * 2.0.0 [RVJ-31] (검토 T05-01) 근무표 점검 기준 as this team's board reads them: the user's own (설정 › 알림과 잠 › 근무표 점검 기준),
 * with 주 52시간 left out for a 5인 미만 사업장 ([Workplace.teamUnderFive]: the 급여 대장's answer, else the leader's own 급여 설정 when
 * they chose it themselves, not while it is only a guess).
 */
@Composable
internal fun teamRules(team: Team, calendarId: String): RestRules {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val places by c.phaseThree.workplaces.collectAsStateWithLifecycle(emptyList())
    val own by remember(calendarId) { c.scopeOf(calendarId).pay.profile }.collectAsStateWithLifecycle()
    val said = own?.takeIf { Unknowns.FIVE_OR_MORE !in it.estimatedFields }?.fiveOrMoreEmployees
    return remember(settings.rules, places, said, team.id) { settings.rules.copy(weekLimit = !Workplace.teamUnderFive(team.id, places, said)) }
}

/** "09:00 – 18:00 · 휴게 1시간 · 일하는 시간 8시간", or null while the kind has no times (what is read out for a row). */
internal fun teamHoursWords(s: TeamShift): String? = teamBreakWords(s)?.let { "${Fmt.timeRange(s.start!!, s.end!!)} · $it" }

/** A row's second line, "휴게 1시간 · 일하는 시간 8시간": said apart from the times so it breaks between the two, never inside. */
internal fun teamBreakWords(s: TeamShift): String? {
    if (s.start == null || s.end == null) return null
    return "휴게 ${HoursFormat.breakText(TeamCheck.breakOf(s))} · 일하는 시간 ${HoursFormat.korean(TeamCheck.minutesOf(s))}"
}

/** The › at the end of a row that opens something, as big as the words beside it (글자 200% too). */
@Composable
private fun OpenMark() {
    val size = with(LocalDensity.current) { 24.sp.toDp() }
    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, Modifier.size(size), tint = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 2.0.0 [RVJ-29] (검토 PC-18 '팀 표가 어느 시간을 쓰는지 표시가 없어요', T05-04 '휴게를 넣을 곳이 없어요') Under the paint keys: the
 * times the team's kinds go out with, and the way to the sheet that changes them. One line that is pressed whole (48dp), not one
 * more button among the board's tools. [apart] says a kind's times are no longer the leader's own calendar's.
 */
@Composable
internal fun TeamHoursLine(team: Team, apart: Boolean, onOpen: () -> Unit) {
    val work = team.shifts.filter { !it.isOff && it.start != null && it.end != null }
    if (work.isEmpty()) return
    // A no-break space inside each kind, so at 글자 200% the line breaks between kinds ('주 07:00–15:00 ·' / '오 14:00–22:00'), never
    // between a letter and its times.
    val times = work.joinToString(" · ") { "${it.label}\u00A0${Fmt.timeRangeShort(it.start!!, it.end!!)}" }
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
            .clickable(role = Role.Button, onClickLabel = "근무 시간과 휴게 고치기", onClick = onOpen)
            .heightIn(min = 48.dp).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("근무 시간과 휴게", style = MaterialTheme.typography.bodyMedium)
            Text(times, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // A fact, not a warning (a team may keep its own times by choice): the ⓘ mark, no 주의 색 (디자인 품질 조건 6).
            if (apart) ToneLine("내 달력과 다른 시간이 있어요", tone = Tone.Plain)
        }
        OpenMark()
    }
}

/** The sheet's kinds while they are changed, kept over a turn of the screen: id, start, end (seconds of the day), 휴게 (−1: the law's). */
private val HoursSaver = listSaver<Map<Long, TeamTimes.Hours>, String>(
    save = { m -> m.map { (id, h) -> "$id|${h.start?.toSecondOfDay() ?: -1}|${h.end?.toSecondOfDay() ?: -1}|${h.breakMinutes ?: -1}" } },
    restore = { l ->
        l.associate { row ->
            val v = row.split("|")
            fun time(i: Int) = v[i].toInt().takeIf { it >= 0 }?.let { LocalTime.ofSecondOfDay(it.toLong()) }
            v[0].toLong() to TeamTimes.Hours(time(1), time(2), v[3].toInt().takeIf { it >= 0 })
        }
    },
)

/**
 * The 휴게 to keep after the time window: what was picked there, or null (the law's) when it is what the law asks for the new
 * length anyway, or when it was the law's before and was not touched (so 09~18 → 09~13 goes from an hour to 30분 by itself).
 */
internal fun pickedBreak(before: TeamTimes.Hours, shownBreak: Int, picked: TimeRange): Int? {
    val legal = LegalBreak.minutesFor(picked.spanMinutes)
    return when {
        picked.breakMinutes == legal -> null
        before.breakMinutes == null && picked.breakMinutes == shownBreak -> null
        else -> picked.breakMinutes
    }
}

/**
 * 2.0.0 [RVJ-29] 근무 시간과 휴게 (검토 T05-04·PAYU-03·PC-18): each kind of work's start, end and 휴게, changed in the window the
 * calendar's own kinds use ([TimeRangeDialog]) and saved together. It takes the place of the 1.39 '근무 시간 정하기' window, which
 * opened only while a time was missing: a team made from the leader's calendar had no way to see or change its times after.
 * [mine] are the leader's own kinds of work (empty while the first setup is put off): the sheet says, as it is changed, which kinds
 * are not the calendar's ([TeamTimes.apart]). [weekLimit] false says why 52시간 is not told.
 */
@Composable
internal fun TeamHoursSheet(team: Team, mine: List<com.bamnat.core.model.ShiftType>, weekLimit: Boolean, onDismiss: () -> Unit, onSave: (Team) -> Unit) {
    val work = team.shifts.filter { !it.isOff }
    // 1.39 G6 발굴 [SETUP-19]: the usual times by name already in for a kind without them, so a 3교대 team is one 저장.
    val suggested = remember(team.id) { TeamTimes.suggest(team.shifts) }
    var draft by rememberSaveable(team.id, stateSaver = HoursSaver) {
        mutableStateOf(work.associate { s -> s.id to TeamTimes.Hours(s.start ?: suggested[s.id]?.first, s.end ?: suggested[s.id]?.second, s.breakMinutes) })
    }
    var picking by rememberSaveable { mutableStateOf<Long?>(null) }
    val now = TeamTimes.setHours(team, draft)
    val changed = now != team
    val apart = remember(now, mine) { TeamTimes.apart(now, mine) }
    FormSheet(onDismissRequest = onDismiss, unsaved = changed, title = { Text("근무 시간과 휴게") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // Short (조건 4): what the times are for, and the law's 휴게 in one bracket (근로기준법 제54조, [LegalBreak]).
            val check = if (weekLimit) "주 52시간 점검" else "근무표 점검"
            Explain("team:hours", "팀원 링크의 알람과 ${check}이 이 시간으로 나가요. 휴게를 정하지 않으면 법대로(4시간에 30분, 8시간 30분부터 1시간) 빼요.",
                summary = "팀원 링크의 알람과 ${check}이 이 시간으로 나가요.")
            // Side by side while the line has room, the button under it at 글자 200% (one word a line otherwise, 조건 12).
            if (apart.isNotEmpty()) SideOrBelow(minStart = 180.dp, belowAlign = Alignment.Start, start = {
                ToneLine("내 달력과 다른 시간이 있어요", tone = Tone.Plain)
            }, end = {
                TextButton(onClick = { draft = TeamTimes.takeMine(draft, apart) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 달력 시간으로") }
            })
            now.shifts.filter { !it.isOff }.forEach { s ->
                val words = teamHoursWords(s)
                val second = teamBreakWords(s)
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button, onClickLabel = "${s.name} 시간 고치기") { picking = s.id }
                        .heightIn(min = 56.dp).padding(vertical = 6.dp)
                        .semantics(mergeDescendants = true) { contentDescription = "${s.name}, ${words ?: "시간을 아직 안 정했어요"}" },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("${s.label} · ${s.name}", style = MaterialTheme.typography.titleSmall)
                        Text(if (s.start != null && s.end != null) Fmt.timeRange(s.start!!, s.end!!) else "시간 정하기", style = MaterialTheme.typography.bodyMedium,
                            color = if (words == null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        second?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        apart[s.id]?.let { h ->
                            if (h.start != null && h.end != null) Text("내 달력은 ${Fmt.timeRange(h.start!!, h.end!!)}" + (h.breakMinutes?.let { " · 휴게 ${HoursFormat.breakText(it)}" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    OpenMark()
                }
            }
            if (!weekLimit) Text("5인 미만 사업장이라 주 52시간은 알리지 않아요(근로기준법 제11조). 연속 근무·11시간 같은 점검 기준은 그대로예요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        Button(onClick = { onSave(now) }, enabled = changed && TeamTimes.missing(now).isEmpty()) { Text("저장") }
        // Asked by itself while a time is missing ([SETUP-18]): '나중에' then, as the card that stays says it can wait.
    }, dismissButton = { TextButton(onClick = onDismiss) { Text(if (TeamTimes.missing(team).isEmpty()) "닫기" else "나중에") } })
    picking?.let { id ->
        val s = now.shifts.firstOrNull { it.id == id } ?: return@let
        val before = draft[id] ?: TeamTimes.hours(s)
        val start = s.start ?: LocalTime.of(9, 0)
        val end = s.end ?: start.plusHours(9)
        // The window opens on the 휴게 the board counts now: the team's own, or the law's for that length.
        val shown = s.breakMinutes ?: LegalBreak.minutesFor(TimeRange(start, end).spanMinutes)
        TimeRangeDialog(
            title = "${s.name} 시간", initial = TimeRange(start, end, shown), maxBreakMinutes = TeamTimes.MAX_BREAK,
            startOn = TimeRangePart.START, onDismiss = { picking = null },
            onConfirm = { r -> draft = draft + (id to TeamTimes.Hours(r.start, r.end, pickedBreak(before, shown, r))); picking = null },
        )
    }
}
