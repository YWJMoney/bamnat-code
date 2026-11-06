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
import androidx.compose.foundation.layout.height
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
import com.bamnat.app.ui.components.AlertDialog
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
fun LeaderTeamScreen(calendarId: String, onBack: () -> Unit, startTeam: String? = null) {
    val c = LocalContext.current.appContainer
    val store = c.phaseThree
    val teams by store.teamList.collectAsStateWithLifecycle(null)
    // 관리 화면 opens one team straight away; its 뒤로 goes back there, not to the team list.
    var openId by rememberSaveable { mutableStateOf(startTeam) }
    var making by rememberSaveable { mutableStateOf(false) }
    // 1.38 가져오기: a team made from a file opens with that file waiting for ① 이름 ② 코드.
    var madeFrom by remember { mutableStateOf<Pair<com.bamnat.core.imports.RosterGrid, Boolean>?>(null) }
    val open = teams?.firstOrNull { it.id == openId }
    if (open != null) { TeamBoardScreen(open, onBack = { madeFrom = null; if (startTeam != null) onBack() else openId = null }, startImport = madeFrom); return }
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
            // 1.38 Phase 3 요금: one team and its first confirmed month are the free taste; more is the 팀 요금제.
            val gate by c.featureGate.state.collectAsStateWithLifecycle()
            var ask by remember { mutableStateOf<com.bamnat.core.tier.WorkPlanRules.Need?>(null) }
            var everConfirmed by remember { mutableStateOf<Set<YearMonth>?>(null) }
            LaunchedEffect(teams) { everConfirmed = store.confirmedMonths() }
            Button(onClick = {
                val need = gate.moreTeams(teams.orEmpty().count { !it.archived })
                if (!need.allowed) { ask = need; return@Button }
                making = true
            }) { Text("팀 만들기") }
            val hold = gate.hold(com.bamnat.core.tier.WorkPlanKind.TEAM)
            Text(when {
                !gate.live -> "팀원은 늘 무료로 링크를 받아요."
                hold != null && hold.cap < Int.MAX_VALUE -> "팀 요금제 ${hold.cap}명 구간을 쓰고 있어요. 팀원은 늘 무료예요."
                hold != null -> "팀원은 늘 무료로 링크를 받아요."
                everConfirmed.isNullOrEmpty() -> "팀 하나를 만들어 첫 달 확정까지 무료로 써 볼 수 있어요. 팀원은 늘 무료예요."
                else -> "무료 맛보기(첫 달 확정)를 썼어요. 다음 달 확정부터 팀 요금제예요. 만든 표는 언제든 보고 내보낼 수 있어요."
            }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            ask?.let { com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.TEAM, it, onDismiss = { ask = null }) }
        }
    }
    if (making) NewTeamDialog(calendarId, onDismiss = { making = false }) { id, from -> making = false; madeFrom = from; openId = id }
}

/** 팀 만들기: a name, the names pasted in one go ([TeamPaste]), the kinds of day copied from the leader's own calendar. */
@Composable
private fun NewTeamDialog(calendarId: String, onDismiss: () -> Unit, onMade: (String, Pair<com.bamnat.core.imports.RosterGrid, Boolean>?) -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val snapFlow = remember(calendarId) { c.scopeOf(calendarId).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    var name by rememberSaveable { mutableStateOf("") }
    var names by rememberSaveable { mutableStateOf("") }
    val people = remember(names) { TeamPaste.people(names) }
    val context = LocalContext.current
    // 1.38 가져오기: names straight from a roster file or photo; the file goes on the board right after 만들기.
    var from by remember { mutableStateOf<Pair<com.bamnat.core.imports.RosterGrid, Boolean>?>(null) }
    var reading by remember { mutableStateOf<String?>(null) }
    val pickFile = rememberAnyFilePicker { uri ->
        scope.launch {
            reading = "파일을 읽고 있어요…"
            val shifts = snap?.shiftTypes.orEmpty().map { TeamShift(it.id, it.label, it.name, it.colorArgb, it.isOff, it.start, it.end) }
            when (val got = com.bamnat.app.imports.ManageImport.roster(context, uri, YearMonth.from(Today.date()).plusMonths(1), shifts, Today.date())) {
                is com.bamnat.app.imports.ManageImport.Got.Read -> {
                    from = got.value to got.fromPhoto
                    names = got.value.rows.joinToString("\n") { r -> r.name + (r.role?.let { " $it" } ?: "") }.take(3000)
                    reading = null
                }
                is com.bamnat.app.imports.ManageImport.Got.Failed -> reading = got.why
            }
        }
    }
    // 1.38 PC 1차 (ESC·뒤로에 명단이 사라짐): with something typed, a tap outside or 뒤로 does not close it; 닫기 does.
    val typed = name.isNotBlank() || names.isNotBlank()
    AlertDialog(onDismissRequest = { if (!typed) onDismiss() }, properties = androidx.compose.ui.window.DialogProperties(dismissOnBackPress = !typed, dismissOnClickOutside = !typed),
        title = { Text("팀 만들기") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(name, { name = it.take(20) }, label = { Text("팀 이름 (예: 3병동)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(names, { names = it.take(3000) }, label = { Text("팀원 이름, 한 줄에 한 명") }, minLines = 4, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = pickFile, modifier = Modifier.fillMaxWidth()) { Text(if (from == null) "근무표 파일·사진에서 이름 가져오기" else "다른 파일 고르기") }
            (reading ?: from?.let { (g, _) -> "${Fmt.month(g.month)} 근무표 ${g.rows.size}명을 읽었어요. 만들면 근무도 바로 넣을 수 있어요." })?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
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
                c.phaseThree.saveTeam(Team(id, name.trim(), shifts = shifts, createdAt = Today.millis()))
                c.phaseThree.saveMembers(people.mapIndexed { i, p -> TeamMember(UUID.randomUUID().toString(), id, p.name, p.role, p.skill, sort = i) })
                c.telemetry.event(com.bamnat.core.stats.UsageEvent.TEAM_MADE.key)
                onMade(id, from)
            }
        }) { Text("만들기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

private const val CELL = 34

/** The board of one team and month: the grid, the keypad under it, and what to do with the month. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun TeamBoardScreen(team: Team, onBack: () -> Unit, startImport: Pair<com.bamnat.core.imports.RosterGrid, Boolean>? = null) {
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
    LaunchedEffect(startImport) { startImport?.first?.month?.let { if (it != month) monthText = it.toString() } }
    LaunchedEffect(saved) { val s = saved ?: return@LaunchedEffect; if (editor == null || board?.members != s.members) { editor = TeamEditor(board?.let { now -> val ids = s.members.map { it.id }.toSet(); now.copy(members = s.members, cells = now.cells.filterKeys { it.first in ids }) } ?: s); board = editor!!.board } }
    LaunchedEffect(board) { val b = board ?: return@LaunchedEffect; if (b != saved) { delay(400); store.saveBoard(team.id, b) } }
    // 1.38 검토(데이터 보존): leaving within the 400ms wait used to drop the last cells painted; they are written on the way out.
    val lastBoard by androidx.compose.runtime.rememberUpdatedState(board)
    val lastSaved by androidx.compose.runtime.rememberUpdatedState(saved)
    androidx.compose.runtime.DisposableEffect(team.id, month) {
        onDispose { val b = lastBoard; if (b != null && b != lastSaved) c.appScope.launch { store.saveBoard(team.id, b) } }
    }
    var member by rememberSaveable(team.id) { mutableStateOf<String?>(null) }
    var cursor by rememberSaveable(team.id, monthText) { mutableStateOf(1) }
    var swapFrom by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    var linksFor by remember { mutableStateOf<List<Pair<TeamMember, String>>?>(null) }
    var busy by remember { mutableStateOf<String?>(null) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var planAsk by remember { mutableStateOf<com.bamnat.core.tier.WorkPlanRules.Need?>(null) }
    var plans by remember { mutableStateOf<List<TeamAuto.Plan>?>(null) }
    var resent by remember { mutableStateOf<Pair<Int, List<Pair<TeamMember, String>>>?>(null) }
    var fit by rememberSaveable { mutableStateOf(false) }
    // 1.38 가져오기: the file read (and whether from a photo) waiting for ① 이름 ② 코드, and the cells to check after it.
    var importing by remember { mutableStateOf(startImport) }
    var checkCells by remember(team.id, month) { mutableStateOf<List<Pair<String, LocalDate>>>(emptyList()) }
    val pickFile = rememberAnyFilePicker { uri ->
        scope.launch {
            busy = "파일을 읽고 있어요…"
            val got = com.bamnat.app.imports.ManageImport.roster(context, uri, month, team.shifts, Today.date())
            busy = null
            when (got) {
                is com.bamnat.app.imports.ManageImport.Got.Read -> {
                    if (got.value.month != month) monthText = got.value.month.toString()
                    importing = got.value to got.fromPhoto
                }
                is com.bamnat.app.imports.ManageImport.Got.Failed -> {
                    c.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_ERROR.key, "import")
                    dialog = got.why
                }
            }
        }
    }
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
            // 1.38 검토: the whole month at once (사용자 2026-11-04 "좌우로 스크롤해야 보이는 줄은 펼쳐서 한눈에"), colors only when small.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${active.size}명 · ${b.days.size}일", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { fit = !fit }) { Text(if (fit) "크게 보기" else "한 달 한눈에") }
            }
            val screen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
            val cellDp = if (fit) ((screen - 24 - 64) / b.days.size).coerceIn(9, CELL) else CELL
            val small = cellDp < 22
            // The grid: names fixed on the left, days to the side; the marked cell is outlined.
            Row {
                Column {
                    // As tall as the date head (two lines and the mark row, or one small line).
                    Spacer(Modifier.height(if (small) 15.dp else 36.dp))
                    active.forEach { m ->
                        Text(m.name + if (m.skill == Skill.CHARGE) "★" else "", maxLines = 1, style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (m.id == current?.id) FontWeight.Bold else null,
                            modifier = Modifier.width(64.dp).height(cellDp.dp).clickable { member = m.id }.padding(top = if (small) 0.dp else 9.dp))
                    }
                    if (!small) Text("인원", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(64.dp).padding(top = 6.dp))
                }
                Row(if (fit) Modifier else Modifier.horizontalScroll(rememberScrollState())) {
                    b.days.forEach { d ->
                        val short = daySigns[d].orEmpty()
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            // 1.38 PC 1차 (날짜 머리가 모두 빨간색): the weekday's own color; a day short of people gets an amber mark under it,
                            // 책임자 없음 only a small dot, so the header says the danger and not every note.
                            val lack = short.any { it.kind == TeamCheck.Kind.SHORT }
                            val dayColor = when (d.dayOfWeek) {
                                java.time.DayOfWeek.SUNDAY -> MaterialTheme.colorScheme.error
                                java.time.DayOfWeek.SATURDAY -> Color(0xFF3D6FD6)
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }
                            Text(if (small) "${d.dayOfMonth}" else "${d.dayOfMonth}\n${Fmt.dayOfWeekShort(d.dayOfWeek)}", style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center,
                                color = dayColor, fontSize = if (small) 8.sp else androidx.compose.ui.unit.TextUnit.Unspecified, maxLines = if (small) 1 else 2,
                                modifier = Modifier.width(cellDp.dp)
                                    .background(if (lack) com.bamnat.app.ui.theme.LocalCalendarColors.current.warning.copy(alpha = 0.22f) else Color.Transparent, RoundedCornerShape(4.dp))
                                    .clickable(enabled = short.isNotEmpty()) { dialog = short.joinToString("\n") { "${Fmt.monthDay(d)} ${it.text}" } })
                            Box(Modifier.width(cellDp.dp).height(4.dp), contentAlignment = Alignment.Center) {
                                if (short.isNotEmpty() && !lack) Box(Modifier.size(3.dp).background(MaterialTheme.colorScheme.outline, RoundedCornerShape(2.dp)))
                            }
                            active.forEach { m ->
                                val cell = b.cell(m.id, d)
                                val t = cell?.shiftId?.let(types::get)
                                val marked = m.id == current?.id && d.dayOfMonth == cursor
                                val warn = cellSigns[m.id to d]
                                val changed = state.state == MonthState.CONFIRMED && was[m.id to d] != cell?.shiftId
                                val toCheck = (m.id to d) in checkCells
                                Box(Modifier.size(cellDp.dp).padding(if (small) 0.5.dp else 1.dp)
                                    .background(t?.let { Color(it.colorArgb).copy(alpha = 0.85f) } ?: MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))
                                    .then(if (marked) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                                        else if (warn != null) Modifier.border(2.dp, MaterialTheme.colorScheme.error, RoundedCornerShape(4.dp))
                                        else if (toCheck) Modifier.border(2.dp, com.bamnat.app.ui.theme.LocalCalendarColors.current.warning, RoundedCornerShape(4.dp)) else Modifier)
                                    .clickable {
                                        val from = swapFrom
                                        if (from != null && from.second == d && from.first != m.id) {
                                            val news = TeamCheck.swapCheck(team, b, from.first, m.id, d, rules)
                                            change { it.swap(from.first, m.id, d) }; swapFrom = null
                                            if (news.isNotEmpty()) dialog = "맞바꿨어요. 확인할 것:\n" + news.joinToString("\n") { it.text }
                                        } else { member = m.id; cursor = d.dayOfMonth; if (toCheck) checkCells = checkCells - (m.id to d); warn?.let { w -> dialog = w.joinToString("\n") { it.text } } }
                                    }
                                    .semantics { contentDescription = "${m.name} ${Fmt.monthDay(d)} ${t?.name ?: "빈칸"}" + (if (toCheck) ", 확인할 칸" else "") + (wishAt[m.id to d]?.let { ", 희망 ${it.kind.label}" } ?: "") },
                                    contentAlignment = Alignment.Center) {
                                    if (!small) Text(t?.label ?: (if (wishAt[m.id to d] != null) "♡" else ""), color = if (t != null) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    if (cell?.locked == true) Icon(Icons.Outlined.Lock, null, Modifier.size(9.dp).align(Alignment.TopEnd), tint = Color.White)
                                    if (changed) Box(Modifier.size(6.dp).align(Alignment.BottomEnd).background(MaterialTheme.colorScheme.tertiary, RoundedCornerShape(3.dp)))
                                }
                            }
                            val count = b.count(d)
                            if (!small) Text(team.shifts.filter { !it.isOff }.joinToString("\n") { "${it.label}${count[it.id] ?: 0}" }, style = MaterialTheme.typography.labelSmall,
                                textAlign = TextAlign.Center, modifier = Modifier.width(cellDp.dp))
                        }
                    }
                }
            }
            if (current != null) Keypad(team, b, current, month, cursor, onCursor = { cursor = it }, onMember = { member = it }, change = ::change,
                swapping = swapFrom != null, onSwap = { swapFrom = if (swapFrom == null) current.id to month.atDay(cursor) else null })
            // 1.38 가져오기 ③: the cells read without being sure, or with a code not paired, one after another.
            if (checkCells.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                Text("확인할 칸 ${checkCells.size}개", style = MaterialTheme.typography.bodyMedium, color = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                TextButton(onClick = { checkCells = emptyList() }) { Text("다 봤어요") }
                Button(onClick = {
                    val next = checkCells.first()
                    member = next.first; cursor = next.second.dayOfMonth; checkCells = checkCells.drop(1)
                }) { Text("다음 칸") }
            }
            if (swapFrom != null) Text("${Fmt.monthDay(swapFrom!!.second)} 같은 날 다른 사람 칸을 누르면 맞바꿔요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            if (signs.isNotEmpty()) SectionCard(title = "확인할 것 ${signs.size}") {
                // 1.38 PC 1차: danger first, 부족·책임자 없음 one line each, so an N→D is never under 50 notes.
                val charges = active.count { it.skill == Skill.CHARGE }
                val lines = remember(signs, charges) { TeamCheck.summary(signs, { Fmt.monthDay(it) }, charges) }
                var all by remember { mutableStateOf(false) }
                lines.take(if (all) lines.size else 6).forEach { l ->
                    val danger = l.kind == TeamCheck.Kind.RULE || l.kind == TeamCheck.Kind.WEEK_HOURS
                    Text((if (danger) "⚠ " else "· ") + l.text, style = MaterialTheme.typography.bodySmall,
                        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (danger) FontWeight.Medium else null)
                }
                if (lines.size > 6) TextButton(onClick = { all = !all }) { Text(if (all) "접기" else "나머지 ${lines.size - 6}줄 더 보기") }
                Text("알림이에요. 그대로 확정해도 돼요. 기준은 설정 › 알림과 잠 › 근무표 점검 기준과 같아요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // The month's tools.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { scope.launch { val last = store.boardNow(team.id, month.minusMonths(1)); change { TeamCopy.nextMonth(last, it) } } }) { Text("지난달 이어 칠하기") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val need = c.featureGate.state.value.teamConfirm(store.confirmedMonths(), month, active.size)
                        if (!need.allowed) { planAsk = need; return@launch }
                        plans = TeamAuto.plans(team, b, wishes, rules)
                    }
                }, enabled = b.empty > 0) { Text("빈칸 자동 채우기") }
                OutlinedButton(onClick = { dialog = "need" }) { Text("근무별 인원") }
                OutlinedButton(onClick = { dialog = "wish" }) { Text("희망 휴무 ${wishes.size}") }
                OutlinedButton(onClick = { dialog = "fair" }) { Text("공정성 표") }
                OutlinedButton(onClick = { dialog = "members" }) { Text("팀원") }
                ImportButton(onClick = pickFile)
            }
            val changes = remember(b, was, state) { if (state.state == MonthState.CONFIRMED) b.changesFrom(TeamBoard(month, b.members, was.mapValues { (k, v) -> com.bamnat.core.team.TeamCell(k.first, k.second, v) })) else emptyList() }
            Button(onClick = {
                scope.launch {
                    // 1.38 Phase 3 요금: the first confirmed month is free; after it the 팀 요금제 band that holds the team.
                    val need = c.featureGate.state.value.teamConfirm(store.confirmedMonths(), month, active.size)
                    if (!need.allowed) { planAsk = need; return@launch }
                    busy = "링크를 만들고 있어요…"
                    // 1.38 검토: sending the links again with nothing changed keeps the same 판 (it used to count one up each time).
                    val again = state.state == MonthState.CONFIRMED && changes.isEmpty()
                    val m = if (again) state else store.confirm(team.id, b, state)
                    if (!again) c.telemetry.event(com.bamnat.core.stats.UsageEvent.TEAM_CONFIRM.key)
                    if (need.verdict == com.bamnat.core.tier.WorkPlanRules.Verdict.FREE) store.keepFreeMonth(month)
                    val made = runCatching { links(c, team, b, active) }
                    busy = null
                    // 1.38 PC 1차: after 다시 확정 the links are the same ones, only their days changed: say so, and offer to send anyway.
                    made.onFailure { c.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_ERROR.key, "team_links") }
                    made.onSuccess { c.telemetry.event(com.bamnat.core.stats.UsageEvent.TEAM_LINKS.key) }
                    made.onSuccess { l -> if (state.state == MonthState.CONFIRMED && !again) resent = m.version to l else linksFor = l }.onFailure { dialog = (if (again) "" else "확정은 했어요(${m.version}판). ") + "링크는 인터넷이 될 때 다시 눌러 주세요." }
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
            // 1.38 같이 쉬는 사람 (경쟁 앱 조사 P3-L-11): off until the leader turns it on — it puts teammates' names in each link.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("팀원 링크에 같이 쉬는 사람 보이기", style = MaterialTheme.typography.bodyMedium)
                    Text("쉬는 날마다 그날 같이 쉬는 팀원 이름이 보여요. 이름이 다른 팀원 링크에도 들어가니 팀에서 괜찮을 때 켜요. 다음 확정·링크 보내기부터 들어가요.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = team.showTogether, onCheckedChange = { on -> scope.launch { store.saveTeam(team.copy(showTogether = on)) } })
            }
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
    planAsk?.let { com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.TEAM, it, onDismiss = { planAsk = null }) }
    // Shown once the board of the file's month is there, so ① matches the names against this team as it is now.
    importing?.let { (grid, photo) ->
        val now = board
        if (now != null && now.month == grid.month) TeamImportDialog(grid, photo, now.members, team.shifts, onDismiss = { importing = null }) { choice ->
            importing = null
            scope.launch {
                val rows = com.bamnat.core.team.TeamGridImport.rows(grid)
                val cur = board ?: return@launch
                val (made, ids) = com.bamnat.core.team.TeamGridImport.newMembers(rows, choice.memberOf, choice.skipped, team.id, cur.members)
                if (made.isNotEmpty()) store.saveMembers(made)
                var applied: com.bamnat.core.team.TeamGridImport.Applied? = null
                // One change, so one 되돌리기 takes the whole file back (the new members stay, with nothing painted).
                change { it0 -> com.bamnat.core.team.TeamGridImport.apply(it0.copy(members = it0.members + made.filter { m -> it0.members.none { o -> o.id == m.id } }), rows, ids, choice.codes).also { applied = it }.board }
                val a = applied ?: return@launch
                val order = (cur.members + made).map { it.id }
                checkCells = a.toCheck.sortedWith(compareBy({ order.indexOf(it.first) }, { it.second }))
                checkCells.firstOrNull()?.let { member = it.first; cursor = it.second.dayOfMonth }
                c.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_IMPORT.key, if (photo) "team_photo" else "team_file")
                dialog = importSummary(a, made.size)
            }
        }
    }
    plans?.let { list ->
        AlertDialog(onDismissRequest = { plans = null }, title = { Text("빈칸 자동 채우기") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("칠한 칸과 잠근 칸은 그대로 두고 빈칸만 채워요. 주 52시간·연속 근무·휴식을 먼저 지키고, 그래도 모자란 날은 부족으로 남겨요.",
                    style = MaterialTheme.typography.bodySmall)
                // 1.38 PC 2차: what the need asks of each person, said before any 안, so a short 안 is not a surprise.
                val asks = remember(team, b) { b?.let { TeamAuto.weekHoursNeeded(team, it) } ?: 0.0 }
                if (asks > TeamCheck.WEEK_LIMIT_MINUTES / 60.0) Text("이 인원으로 근무별 인원을 다 채우면 한 사람이 평균 주 ${"%.0f".format(asks)}시간이라 주 52시간을 지킬 수 없어요. 사람을 더하거나 근무별 인원을 줄여 주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                else if (asks > 48) Text("한 사람이 평균 주 ${"%.0f".format(asks)}시간이라 빠듯해요. 52시간과 휴식을 지키느라 부족한 날이 생길 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning)
                list.forEachIndexed { i, p ->
                    OutlinedButton(onClick = { change { p.board }; plans = null }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${i + 1}안 · ${p.filled}칸 · " + (if (p.shortPeople > 0) "부족 ${p.shortPeople}명·${p.short.size}일" else "부족 없음"),
                                style = MaterialTheme.typography.bodyMedium)
                            Text((if (p.danger > 0) "⚠ " + listOfNotNull(p.overWeeks.takeIf { it > 0 }?.let { "52시간 넘김 ${it}건" }, p.ruleSigns.takeIf { it > 0 }?.let { "규칙 ${it}건" }).joinToString(" · ") + " · "
                                else "위험 없음 · ") + "최대 연속 ${p.maxRun}일 · 시간 차이 ${p.hourSpread}시간 · 희망 ${p.wishesKept}/${p.wishesKept + p.wishesMissed} · 나이트 차이 ${p.spread}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (p.danger > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (team.need.isEmpty()) Text("‘근무별 인원’을 정하면 그 수만큼 채워요. 정하지 않으면 쉬는 날로만 채워요.",
                    style = MaterialTheme.typography.bodySmall, color = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning)
            }
        }, confirmButton = { TextButton(onClick = { plans = null }) { Text("닫기") } })
    }
    resent?.let { (v, list) ->
        AlertDialog(onDismissRequest = { resent = null }, title = { Text("${v}판으로 다시 확정했어요") }, text = {
            Text("팀원 링크는 그대로이고, 링크를 열면 바뀐 근무가 보여요. 다시 보낼 필요는 없어요. 바뀐 걸 알려 주고 싶으면 보내도 돼요.", style = MaterialTheme.typography.bodyMedium)
        }, confirmButton = { TextButton(onClick = { resent = null }) { Text("알겠어요") } },
            dismissButton = { TextButton(onClick = { resent = null; linksFor = list }) { Text("그래도 알리기") } })
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
                // 갈래5 PaintPad와 같은 글자·같은 읽기 (TalkBack: "n월 d일 넣은 것 지우기", "d일 건너뛰기").
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${month.monthValue}월 ${cursor}일 넣은 것 지우기" }) { Text("✕ 지우기") }
            OutlinedButton(onClick = { after(pad().skip(), cursor == days) },
                modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "${cursor}일 건너뛰기" }) { Text("» 건너뛰기") }
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
        // 1.38 같이 쉬는 사람 (P3-L-11): only when the leader turned it on; the day's line under the member's own day off.
        val off = team.shifts.filter { it.isOff }.map { it.id }.toSet()
        val together = if (team.showTogether) b.offTogether(m.id) { it in off }.mapValues { (_, n) -> "같이 쉬어요: " + n.joinToString(", ") } else emptyMap()
        val payload = ShareLinkPayload.build(ScheduleSnapshot(shiftTypes = types, patterns = emptyList(), entries = entries), b.month.atDay(1),
            "${team.name} ${m.name}", ttlDays = 90, options = com.bamnat.app.share.ShareOptions(memos = together.isNotEmpty()), memos = together)
        val id = m.linkId
        val token = m.linkToken
        val st = if (id != null && token != null) runCatching {
            c.shareApi.update(ShareLinkState(id, "", token, Today.instant(), "${team.name} ${m.name}"), payload)
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

/**
 * 1.38 보안 검토: cuts [m]'s 근무 링크 on the server, so a person who rests or leaves no longer sees the team's days.
 * True when there is nothing left to cut. A link restored without its key (암호 없는 백업) can't be cut from here; it ends by itself in 90 days.
 */
internal suspend fun cutLink(c: com.bamnat.app.AppContainer, m: TeamMember): Boolean {
    val id = m.linkId ?: return true
    val token = m.linkToken
    if (token != null) runCatching { c.shareApi.delete(ShareLinkState(id, "", token, Today.instant(), m.name)) }.getOrElse { return false }
    c.phaseThree.saveMembers(listOf(m.copy(linkId = null, linkToken = null)))
    return true
}

/** 팀원: add by pasting, 책임 on or off, rest or take out (their cells go with them), and each one's link cut when they rest or leave. */
@Composable
private fun MembersDialog(team: Team, members: List<TeamMember>, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val store = c.phaseThree
    val scope = rememberCoroutineScope()
    var add by rememberSaveable { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    val offline = "인터넷이 안 돼 링크를 끊지 못했어요. 연결한 뒤 다시 눌러 주세요."
    fun rest(m: TeamMember) = scope.launch {
        val cut = cutLink(c, m)
        store.saveMembers(listOf(m.copy(active = false, linkId = if (cut) null else m.linkId, linkToken = if (cut) null else m.linkToken)))
        note = if (!cut) "${m.name}님을 쉼으로 두었어요. " + offline else if (m.linkId != null) "${m.name}님을 쉼으로 두고 근무 링크를 끊었어요. 복귀하면 다음 확정 때 새 링크를 보내요." else null
    }
    // 1.38 검토(데이터 보존): taking someone out deletes their days in every month, confirmed ones too; ask first and offer 쉼, which keeps them.
    var removing by remember { mutableStateOf<TeamMember?>(null) }
    removing?.let { m ->
        AlertDialog(onDismissRequest = { removing = null }, title = { Text("${m.name} 빼기") }, text = {
            Text("빼면 ${m.name}의 칠한 칸이 지난달·확정한 달까지 모두 지워지고, 근무 링크도 바로 끊겨요. 그만두거나 쉬는 거라면 '쉼'으로 두면 지난 근무표가 그대로 남아요(링크는 끊겨요).",
                style = MaterialTheme.typography.bodyMedium)
        }, confirmButton = {
            TextButton(onClick = { rest(m); removing = null }) { Text("쉼으로 두기") }
        }, dismissButton = {
            TextButton(onClick = {
                scope.launch { if (cutLink(c, m)) { store.removeMember(m.id); note = null } else note = "${m.name}님을 빼지 않았어요. " + offline }
                removing = null
            }) { Text("그래도 지우기", color = MaterialTheme.colorScheme.error) }
        })
    }
    var cutting by remember { mutableStateOf<TeamMember?>(null) }
    cutting?.let { m ->
        AlertDialog(onDismissRequest = { cutting = null }, title = { Text("${m.name} 링크 새로 만들기") }, text = {
            Text("지금 링크를 끊어요. 링크가 다른 사람에게 갔을 때 쓰세요. 다음에 확정해 보낼 때 ${m.name}님에게 새 링크가 가요.", style = MaterialTheme.typography.bodyMedium)
        }, confirmButton = {
            TextButton(onClick = { scope.launch { note = if (cutLink(c, m)) "${m.name}님의 링크를 끊었어요. 다음 확정 때 새 링크가 가요." else offline }; cutting = null }) { Text("링크 끊기") }
        }, dismissButton = { TextButton(onClick = { cutting = null }) { Text("그만두기") } })
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("팀원 ${members.count { it.active }}명") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            members.forEach { m ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(m.name + if (!m.active) " (쉬는 중)" else "", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    FilterChip(selected = m.skill == Skill.CHARGE, onClick = { scope.launch { store.saveMembers(listOf(m.copy(skill = if (m.skill == Skill.CHARGE) Skill.REG else Skill.CHARGE))) } },
                        label = { Text("책임") })
                    TextButton(onClick = { if (m.active) rest(m) else scope.launch { store.saveMembers(listOf(m.copy(active = true))) } }) { Text(if (m.active) "쉼" else "복귀") }
                    TextButton(onClick = { removing = m }) { Text("빼기") }
                }
                if (m.linkId != null) TextButton(onClick = { cutting = m }, modifier = Modifier.padding(start = 0.dp)) {
                    Text("링크 끊고 새로 만들기", style = MaterialTheme.typography.labelSmall)
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
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
