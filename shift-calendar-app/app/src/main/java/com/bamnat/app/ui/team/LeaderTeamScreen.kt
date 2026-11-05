package com.bamnat.app.ui.team

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.rules
import com.bamnat.app.share.ShareLinkPayload
import com.bamnat.app.share.ShareLinkState
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.imports.PaintPad
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.team.MonthState
import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.team.TeamCheck.asType
import com.bamnat.core.team.TeamCopy
import com.bamnat.core.team.TeamEditor
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamPaste
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamWish
import com.bamnat.core.team.TeamWishText
import com.bamnat.core.team.WishKind
import com.bamnat.core.time.Today
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID

/**
 * 1.38 [P3-02][P3-04] 리더 팀 근무표 (Phase 3 M24, 설계 2-1), behind `team.leader`: the leader makes a team (names pasted at once),
 * paints a 팀원×날짜 board with the same keypad rules as 한 달 넣기 ([PaintPad]: a key puts the day's kind and moves on, ✕ and »),
 * sees 규칙·인원·책임자·주 52시간 signs while painting ([TeamCheck]), carries last month on ([TeamCopy]), fills the empty cells
 * ([TeamAuto], 희망 휴무 kept), and at 확정 makes each member a link of their own days only. The board stays on the leader's phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderTeamScreen(calendarId: String, onBack: () -> Unit) {
    val c = LocalContext.current.appContainer
    val store = c.phaseThree
    val teams by store.teamList.collectAsStateWithLifecycle(null)
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    var making by rememberSaveable { mutableStateOf(false) }
    val open = teams?.firstOrNull { it.id == openId }
    if (open != null) { TeamBoardScreen(open, onBack = { openId = null }); return }
    Scaffold(topBar = {
        TopAppBar(title = { Text("내가 짜는 팀 근무표") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("팀원 이름을 붙여 넣고 표를 칠하면, 확정할 때 팀원마다 자기 근무만 담긴 링크를 보내요. 팀 표는 이 휴대폰에만 있어요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            teams.orEmpty().filter { !it.archived }.forEach { t ->
                SectionCard(title = t.name, trailing = { TextButton(onClick = { openId = t.id }) { Text("열기") } }) {
                    val members by store.members(t.id).collectAsStateWithLifecycle(emptyList())
                    Text("${members.count { it.active }}명 · 근무 ${t.shifts.count { !it.isOff }}가지", style = MaterialTheme.typography.bodySmall)
                }
            }
            // [GROW-06] One team is free; a second is 프로.
            val gate by c.featureGate.state.collectAsStateWithLifecycle()
            val config by c.remoteConfig.config.collectAsStateWithLifecycle()
            val more = teams.orEmpty().count { !it.archived } >= config.freeTeams
            Button(onClick = {
                if (more && gate.locked(com.bamnat.app.tier.Feature.TEAM_ROSTER) && !com.bamnat.app.ui.pro.tryFeature(com.bamnat.app.tier.Feature.TEAM_ROSTER)) {
                    com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.TEAM_ROSTER); return@Button
                }
                making = true
            }) { Text(if (more && gate.locked(com.bamnat.app.tier.Feature.TEAM_ROSTER)) "팀 더 만들기 (프로)" else "팀 만들기") }
            Text("팀 ${config.freeTeams}개는 무료예요. 팀원은 늘 무료로 링크를 받아요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (making) NewTeamDialog(calendarId, onDismiss = { making = false }) { id -> making = false; openId = id }
}

/** 팀 만들기: a name, the names pasted in one go ([TeamPaste]), the kinds of day copied from the leader's own calendar. */
@Composable
private fun NewTeamDialog(calendarId: String, onDismiss: () -> Unit, onMade: (String) -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val snapFlow = remember(calendarId) { c.scopeOf(calendarId).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    var name by rememberSaveable { mutableStateOf("") }
    var names by rememberSaveable { mutableStateOf("") }
    val people = remember(names) { TeamPaste.people(names) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("팀 만들기") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("팀 이름 (예: 3병동)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(names, { names = it.take(3000) }, label = { Text("팀원 이름, 한 줄에 한 명") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            Text(if (people.isEmpty()) "단톡방 명단을 그대로 붙여 넣어도 돼요. '민지 책임'처럼 직급은 따로 나눠요."
                else "${people.size}명: " + people.take(6).joinToString(", ") { it.name } + if (people.size > 6) " …" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("근무 종류는 내 달력의 것(${snap?.shiftTypes?.joinToString(" ") { it.label }.orEmpty()})을 그대로 써요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && people.isNotEmpty() && snap != null, onClick = {
            val s = snap ?: return@TextButton
            val id = UUID.randomUUID().toString()
            val shifts = s.shiftTypes.sortedBy { it.sortOrder }.map { TeamShift(it.id, it.label, it.name, it.colorArgb, it.isOff, it.start, it.end) }
            scope.launch {
                c.phaseThree.saveTeam(Team(id, name.trim(), shifts = shifts, createdAt = System.currentTimeMillis()))
                c.phaseThree.saveMembers(people.mapIndexed { i, p -> TeamMember(UUID.randomUUID().toString(), id, p.name, p.role, p.skill, sort = i) })
                onMade(id)
            }
        }) { Text("만들기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

private const val CELL = 34

/** The board of one team and month: the grid, the keypad under it, and what to do with the month. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TeamBoardScreen(team: Team, onBack: () -> Unit) {
    val context = LocalContext.current
    val c = context.appContainer
    val store = c.phaseThree
    val scope = rememberCoroutineScope()
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    var monthText by rememberSaveable { mutableStateOf(YearMonth.from(Today.date()).plusMonths(1).toString()) }
    val month = YearMonth.parse(monthText)
    val saved by store.board(team.id, month).collectAsStateWithLifecycle(null)
    val confirmed by store.month(team.id, month).collectAsStateWithLifecycle(null)
    val wishes by store.wishes(team.id, month).collectAsStateWithLifecycle(emptyList())
    // The board being painted: an editor over the saved one, written back a moment after each change.
    var editor by remember(team.id, month) { mutableStateOf<TeamEditor?>(null) }
    var board by remember(team.id, month) { mutableStateOf<TeamBoard?>(null) }
    LaunchedEffect(saved) { val s = saved ?: return@LaunchedEffect; if (editor == null || board?.members != s.members) { editor = TeamEditor(board?.let { now -> val ids = s.members.map { it.id }.toSet(); now.copy(members = s.members, cells = now.cells.filterKeys { it.first in ids }) } ?: s); board = editor!!.board } }
    LaunchedEffect(board) { val b = board ?: return@LaunchedEffect; if (b != saved) { delay(400); store.saveBoard(team.id, b) } }
    var member by rememberSaveable(team.id) { mutableStateOf<String?>(null) }
    var cursor by rememberSaveable(team.id, monthText) { mutableStateOf(1) }
    var swapFrom by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    var linksFor by remember { mutableStateOf<List<Pair<TeamMember, String>>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var plans by remember { mutableStateOf<List<TeamAuto.Plan>?>(null) }
    val b = board
    fun change(f: (TeamBoard) -> TeamBoard) { val e = editor ?: return; board = e.apply(f) }

    Scaffold(topBar = {
        TopAppBar(title = { Text(team.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            actions = {
                IconButton(onClick = { editor?.let { board = it.undo() } }, enabled = editor?.canUndo == true) { Icon(Icons.AutoMirrored.Filled.Undo, "되돌리기") }
                IconButton(onClick = { editor?.let { board = it.redo() } }, enabled = editor?.canRedo == true) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 하기") }
            })
    }) { padding ->
        if (b == null) { Box(Modifier.padding(padding)) { Text("불러오는 중…", Modifier.padding(16.dp)) }; return@Scaffold }
        val rules = settings.rules
        val signs = remember(b, team, rules) { TeamCheck.check(team, b, rules) }
        val cellSigns = remember(signs) { signs.filter { it.memberId != null }.groupBy { it.memberId!! to it.date } }
        val daySigns = remember(signs) { signs.filter { it.memberId == null }.groupBy { it.date } }
        val types = team.shifts.associateBy { it.id }
        val wishAt = wishes.associateBy { it.memberId to it.date }
        val (state, was) = confirmed ?: (com.bamnat.core.team.TeamMonth(team.id, month) to emptyMap())
        val active = b.members.filter { it.active }
        val current = active.firstOrNull { it.id == member } ?: active.firstOrNull()
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { monthText = month.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "지난달") }
                Text(Fmt.month(month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(when {
                    state.state == MonthState.CONFIRMED -> "확정 ${state.version}판"
                    b.empty > 0 -> "빈칸 ${b.empty}"
                    else -> "다 칠함"
                }, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                IconButton(onClick = { monthText = month.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
            }
            // The grid: names fixed on the left, days to the side; the marked cell is outlined.
            Row {
                Column {
                    Spacer(Modifier.heightIn(min = 28.dp))
                    active.forEach { m ->
                        Text(m.name + if (m.skill == Skill.CHARGE) "★" else "", maxLines = 1, style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (m.id == current?.id) FontWeight.Bold else null,
                            modifier = Modifier.width(64.dp).heightIn(min = CELL.dp).clickable { member = m.id }.padding(top = 9.dp))
                    }
                    Text("인원", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp).padding(top = 6.dp))
                }
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    b.days.forEach { d ->
                        val short = daySigns[d].orEmpty()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${d.dayOfMonth}\n${Fmt.dayOfWeekShort(d.dayOfWeek)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                color = if (short.isNotEmpty()) com.bamnat.app.ui.theme.LocalCalendarColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(CELL.dp).clickable(enabled = short.isNotEmpty()) { dialog = short.joinToString("\n") { "${Fmt.monthDay(d)} ${it.text}" } })
                            active.forEach { m ->
                                val cell = b.cell(m.id, d)
                                val t = cell?.shiftId?.let(types::get)
                                val marked = m.id == current?.id && d.dayOfMonth == cursor
                                val warn = cellSigns[m.id to d]
                                val changed = state.state == MonthState.CONFIRMED && was[m.id to d] != cell?.shiftId
                                Box(Modifier.size(CELL.dp).padding(1.dp)
                                    .background(t?.let { Color(it.colorArgb).copy(alpha = 0.85f) } ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .then(if (marked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        else if (warn != null) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp)) else Modifier)
                                    .clickable {
                                        val from = swapFrom
                                        if (from != null && from.second == d && from.first != m.id) {
                                            val news = TeamCheck.swapCheck(team, b, from.first, m.id, d, rules)
                                            change { it.swap(from.first, m.id, d) }; swapFrom = null
                                            if (news.isNotEmpty()) dialog = "맞바꿨어요. 확인할 것:\n" + news.joinToString("\n") { it.text }
                                        } else { member = m.id; cursor = d.dayOfMonth; warn?.let { w -> dialog = w.joinToString("\n") { it.text } } }
                                    }
                                    .semantics { contentDescription = "${m.name} ${Fmt.monthDay(d)} ${t?.name ?: "빈칸"}" + (wishAt[m.id to d]?.let { ", 희망 ${it.kind.label}" } ?: "") },
                                    contentAlignment = Alignment.Center) {
                                    Text(t?.label ?: (if (wishAt[m.id to d] != null) "♡" else ""), color = if (t != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    if (cell?.locked == true) Icon(Icons.Outlined.Lock, null, Modifier.size(9.dp).align(Alignment.TopEnd), tint = Color.White)
                                    if (changed) Box(Modifier.size(6.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(3.dp)))
                                }
                            }
                            val count = b.count(d)
                            Text(team.shifts.filter { !it.isOff }.joinToString("\n") { "${it.label}${count[it.id] ?: 0}" }, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, modifier = Modifier.width(CELL.dp))
                        }
                    }
                }
            }
            if (current != null) Keypad(team, b, current, month, cursor, onCursor = { cursor = it }, onMember = { member = it }, change = ::change,
                swapping = swapFrom != null, onSwap = { swapFrom = if (swapFrom == null) current.id to month.atDay(cursor) else null })
            if (swapFrom != null) Text("${Fmt.monthDay(swapFrom!!.second)} 같은 날 다른 사람 칸을 누르면 맞바꿔요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (signs.isNotEmpty()) SectionCard(title = "확인할 것 ${signs.size}") {
                signs.take(8).forEach { Text("${Fmt.monthDay(it.date)} ${it.text}", style = MaterialTheme.typography.bodySmall) }
                if (signs.size > 8) Text("…외 ${signs.size - 8}개", style = MaterialTheme.typography.bodySmall)
                Text("알림이에요. 그대로 확정해도 돼요. 기준은 설정 › 근무표 점검 기준과 같아요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The month's tools.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { scope.launch { val last = store.boardNow(team.id, month.minusMonths(1)); change { TeamCopy.nextMonth(last, it) } } }) { Text("지난달 이어 칠하기") }
                OutlinedButton(onClick = {
                    val f = com.bamnat.app.tier.Feature.TEAM_ROSTER
                    if (c.featureGate.state.value.locked(f) && !com.bamnat.app.ui.pro.tryFeature(f)) { com.bamnat.app.ui.pro.openPro(f); return@OutlinedButton }
                    plans = TeamAuto.plans(team, b, wishes, rules)
                }, enabled = b.empty > 0) { Text("빈칸 자동 채우기") }
                OutlinedButton(onClick = { dialog = "need" }) { Text("근무별 인원") }
                OutlinedButton(onClick = { dialog = "wish" }) { Text("희망 휴무 ${wishes.size}") }
                OutlinedButton(onClick = { dialog = "fair" }) { Text("공정성 표") }
                OutlinedButton(onClick = { dialog = "members" }) { Text("팀원") }
            }
            val changes = remember(b, was, state) { if (state.state == MonthState.CONFIRMED) b.changesFrom(TeamBoard(month, b.members, was.mapValues { (k, v) -> com.bamnat.core.team.TeamCell(k.first, k.second, v) })) else emptyList() }
            Button(onClick = {
                scope.launch {
                    busy = "링크를 만들고 있어요…"
                    val m = store.confirm(team.id, b, state)
                    val made = runCatching { links(c, team, b, active) }
                    busy = null
                    made.onSuccess { linksFor = it }.onFailure { dialog = "확정은 했어요(${m.version}판). 링크는 인터넷이 될 때 다시 눌러 주세요." }
                }
            }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    state.state != MonthState.CONFIRMED -> "확정하고 팀원 링크 보내기"
                    changes.isEmpty() -> "링크 다시 보내기"
                    else -> "바뀐 칸 ${changes.size}개로 다시 확정"
                })
            }
            busy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Text("팀원은 앱이 없어도 링크로 자기 근무를 봐요. 앱이 있으면 내 달력에 넣을 수 있어요. 고친 뒤 다시 확정하면 같은 링크가 바뀌어요.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.heightIn(min = 24.dp))
        }
        when (dialog) {
            null -> Unit
            "need" -> NeedDialog(team, onDismiss = { dialog = null }) { scope.launch { store.saveTeam(it) }; dialog = null }
            "wish" -> WishDialog(team, month, active, wishes, onDismiss = { dialog = null })
            "fair" -> FairDialog(team, b, onDismiss = { dialog = null })
            "members" -> MembersDialog(team, b.members, onDismiss = { dialog = null })
            else -> AlertDialog(onDismissRequest = { dialog = null }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("알겠어요") } }, text = { Text(dialog!!) })
        }
    }
    plans?.let { list ->
        AlertDialog(onDismissRequest = { plans = null }, title = { Text("빈칸 자동 채우기") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("칠한 칸과 잠근 칸은 그대로 두고 빈칸만 채워요. 마음에 드는 안을 고르세요.", style = MaterialTheme.typography.bodySmall)
                list.forEachIndexed { i, p ->
                    val short = p.short.values.sumOf { it.values.sum() }
                    OutlinedButton(onClick = { change { p.board }; plans = null }, modifier = Modifier.fillMaxWidth()) {
                        Text("${i + 1}안 · ${p.filled}칸 · " + (if (short > 0) "부족 ${short}명·${p.short.size}일" else "부족 없음") +
                            " · 희망 ${p.wishesKept}/${p.wishesKept + p.wishesMissed} · 나이트 차이 ${p.spread}")
                    }
                }
                if (team.need.isEmpty()) Text("‘근무별 인원’을 정하면 그 수만큼 채워요. 정하지 않으면 쉬는 날로만 채워요.",
                    style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning)
            }
        }, confirmButton = { TextButton(onClick = { plans = null }) { Text("닫기") } })
    }
    linksFor?.let { list ->
        AlertDialog(onDismissRequest = { linksFor = null }, title = { Text("팀원 링크") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("한 사람씩 보내거나, 모두 한 번에 단톡방에 보낼 수 있어요. 링크마다 그 사람 근무만 보여요.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = { send(context, list.joinToString("\n") { (m, u) -> "${m.name}: $u" }, "${team.name} ${Fmt.month(month)} 근무표") }) { Text("모두 한 번에 보내기") }
                list.forEach { (m, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name, modifier = Modifier.weight(1f))
                        TextButton(onClick = { send(context, "${team.name} ${Fmt.month(month)} ${m.name} 근무표\n$url", m.name) }) { Text("보내기") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { linksFor = null }) { Text("닫기") } })
    }
}

/**
 * The keypad: one key a kind of day, then ✕ and »; the marked day of the chosen member gets the key and the mark moves on as
 * [PaintPad] moves it. After the month's last day it goes on to the next member, so the whole team is painted without lifting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Keypad(team: Team, b: TeamBoard, m: TeamMember, month: YearMonth, cursor: Int, onCursor: (Int) -> Unit, onMember: (String) -> Unit,
                   change: ((TeamBoard) -> TeamBoard) -> Unit, swapping: Boolean, onSwap: () -> Unit) {
    val days = month.lengthOfMonth()
    val active = b.members.filter { it.active }
    fun pad() = PaintPad.open(days, b.daysOf(m.id).mapKeys { it.key.dayOfMonth }).tap(cursor)
    fun after(p: PaintPad<Long>, wasLast: Boolean) {
        if (wasLast) active.getOrNull(active.indexOfFirst { it.id == m.id } + 1)?.let { onMember(it.id); onCursor(1); return }
        onCursor(p.cursor)
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${m.name} · ${Fmt.monthDay(month.atDay(cursor.coerceIn(1, days)))}", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            team.shifts.forEach { s ->
                Button(onClick = {
                    val p = pad().put(s.id)
                    change { it.paint(m.id, month.atDay(cursor), s.id) }
                    after(p, cursor == days)
                }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(s.colorArgb)),
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${s.name} 칠하기" }) { Text(s.label, color = Color.White, fontWeight = FontWeight.Bold) }
            }
            OutlinedButton(onClick = { change { it.paint(m.id, month.atDay(cursor), null) }; after(pad().erase(), cursor == days) },
                modifier = Modifier.heightIn(min = 48.dp)) { Text("✕ 지우기") }
            OutlinedButton(onClick = { after(pad().skip(), cursor == days) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("» 건너뛰기") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val cell = b.cell(m.id, month.atDay(cursor))
            TextButton(onClick = { change { it.lock(m.id, month.atDay(cursor), cell?.locked != true) } }, enabled = cell != null) {
                Text(if (cell?.locked == true) "잠금 풀기" else "이 칸 잠그기")
            }
            TextButton(onClick = onSwap) { Text(if (swapping) "맞바꾸기 그만" else "맞바꾸기") }
        }
    }
}

/** 확정: each member a link of their own days only (설계 2-1 "그 사람 근무만"), the same link again after the first time. */
private suspend fun links(c: com.bamnat.app.AppContainer, team: Team, b: TeamBoard, members: List<TeamMember>): List<Pair<TeamMember, String>> {
    val types = team.shifts.map { it.asType() }
    val out = ArrayList<Pair<TeamMember, String>>()
    val updated = ArrayList<TeamMember>()
    for (m in members) {
        val entries = b.daysOf(m.id).map { (d, id) -> DayEntry(d, ShiftOverride.Shift(id)) }
        val payload = ShareLinkPayload.build(ScheduleSnapshot(shiftTypes = types, patterns = emptyList(), entries = entries), b.month.atDay(1),
            "${team.name} ${m.name}", ttlDays = 90)
        val id = m.linkId
        val token = m.linkToken
        val st = if (id != null && token != null) runCatching {
            c.shareApi.update(ShareLinkState(id, "", token, Instant.now(), "${team.name} ${m.name}"), payload)
        }.getOrElse { c.shareApi.create(payload) } else c.shareApi.create(payload)
        out += m to st.url
        updated += m.copy(linkId = st.id, linkToken = st.deleteToken)
    }
    c.phaseThree.saveMembers(updated)
    return out
}

private fun send(context: android.content.Context, text: String, title: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(i, title)) }
}

/** [TEAM-15] 근무별 최소 인원: "D 3명". */
@Composable
private fun NeedDialog(team: Team, onDismiss: () -> Unit, onSave: (Team) -> Unit) {
    var need by remember { mutableStateOf(team.need) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("근무별 인원") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("날마다 꼭 있어야 하는 사람 수예요. 모자란 날은 날짜가 주황색이 되고, 자동 채우기가 이 수만큼 채워요.", style = MaterialTheme.typography.bodySmall)
            team.shifts.filter { !it.isOff }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.label} ${s.name}", modifier = Modifier.weight(1f))
                    com.bamnat.app.ui.components.CountStepper(need[s.id] ?: 0, { n -> need = if (n <= 0) need - s.id else need + (s.id to n) },
                        name = "${s.name} 인원", range = 0..30, format = { "${it}명" })
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(team.copy(need = need)) }) { Text("저장") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/** [TEAM-19] 희망 휴무: pasted lines from members ("[밤낮 희망] 민지 11월 3 7 쉬고"), or typed by the leader; ♡ on the board. */
@Composable
private fun WishDialog(team: Team, month: YearMonth, members: List<TeamMember>, wishes: List<TeamWish>, onDismiss: () -> Unit) {
    val store = LocalContext.current.appContainer.phaseThree
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val read = remember(text, members) { TeamWishText.read(text, month, members) }
    val names = members.associate { it.id to it.name }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("희망 휴무 (${Fmt.monthShort(month)})") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("팀원이 보낸 글을 그대로 붙여 넣거나 '민지 3 7 15'처럼 적어요. 자동 채우기가 지켜요.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(text, { text = it.take(4000) }, minLines = 3, modifier = Modifier.fillMaxWidth(), label = { Text("받은 글") })
            if (read.isNotEmpty()) Text("${read.size}개 읽음: " + read.take(5).joinToString(", ") { "${names[it.memberId]} ${it.date.dayOfMonth}일 ${it.kind.label}" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            wishes.groupBy { it.memberId }.forEach { (id, l) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${names[id] ?: "?"}: " + l.sortedBy { it.date }.joinToString(" ") { "${it.date.dayOfMonth}" + if (it.kind != WishKind.OFF) "(${it.kind.label.take(3)})" else "" },
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { l.forEach { store.removeWish(it.memberId, it.date) } } }) { Text("지우기") }
                }
            }
        }
    }, confirmButton = {
        TextButton(enabled = read.isNotEmpty(), onClick = { scope.launch { store.addWishes(team.id, read, "TEXT"); text = "" } }) { Text("넣기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/** [TEAM-17] 공정성 표: nights, weekends and offs side by side, the most and least marked. */
@Composable
private fun FairDialog(team: Team, b: TeamBoard, onDismiss: () -> Unit) {
    val rows = remember(team, b) { TeamCheck.fairness(team, b) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("공정성 표") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row { listOf("이름", "나이트", "주말", "쉼", "시간").forEach { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)) } }
            val maxN = rows.maxOfOrNull { it.nights } ?: 0
            val minN = rows.minOfOrNull { it.nights } ?: 0
            rows.forEach { r ->
                Row {
                    Text(r.member.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                    Text("${r.nights}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                        color = if (maxN - minN >= 2 && (r.nights == maxN || r.nights == minN)) com.bamnat.app.ui.theme.LocalCalendarColors.current.warning else Color.Unspecified)
                    Text("${r.weekends}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${r.offs}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text("${r.minutes / 60}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/** 팀원: add by pasting, 책임 on or off, rest or take out (their cells go with them). */
@Composable
private fun MembersDialog(team: Team, members: List<TeamMember>, onDismiss: () -> Unit) {
    val store = LocalContext.current.appContainer.phaseThree
    val scope = rememberCoroutineScope()
    var add by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("팀원 ${members.count { it.active }}명") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            members.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name + if (!m.active) " (쉬는 중)" else "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FilterChip(selected = m.skill == Skill.CHARGE, onClick = { scope.launch { store.saveMembers(listOf(m.copy(skill = if (m.skill == Skill.CHARGE) Skill.REG else Skill.CHARGE))) } },
                        label = { Text("책임") })
                    TextButton(onClick = { scope.launch { store.saveMembers(listOf(m.copy(active = !m.active))) } }) { Text(if (m.active) "쉼" else "복귀") }
                    TextButton(onClick = { scope.launch { store.removeMember(m.id) } }) { Text("빼기") }
                }
            }
            OutlinedTextField(add, { add = it.take(1000) }, label = { Text("더할 이름 (한 줄에 한 명)") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        }
    }, confirmButton = {
        TextButton(enabled = add.isNotBlank() && members.size < 60, onClick = {
            val start = (members.maxOfOrNull { it.sort } ?: -1) + 1
            val ppl = TeamPaste.people(add).take(60 - members.size)
            scope.launch { store.saveMembers(ppl.mapIndexed { i, p -> TeamMember(UUID.randomUUID().toString(), team.id, p.name, p.role, p.skill, sort = start + i) }) }
            add = ""
        }) { Text("더하기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
