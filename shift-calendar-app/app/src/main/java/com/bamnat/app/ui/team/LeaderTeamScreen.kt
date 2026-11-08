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
import com.bamnat.core.team.TeamAsk
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
import kotlinx.coroutines.flow.first
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
    // 1.39 [READ-31]: a roster shared from 카카오톡·갤러리 and put on a team opens that team with it waiting, as a picked file does.
    val handed = remember { com.bamnat.app.imports.SharedToManage.takeTeam() }
    var openId by rememberSaveable { mutableStateOf(handed?.teamId ?: startTeam) }
    var making by rememberSaveable { mutableStateOf(false) }
    // 1.38 가져오기: a team made from a file opens with that file waiting for ① 이름 ② 코드.
    var madeFrom by remember { mutableStateOf(handed?.let { it.grid to it.fromPhoto }) }
    val open = teams?.firstOrNull { it.id == openId }
    // PC 10차(낮음): with two or more teams, 뒤로 from a team goes to the team list first (표 이름 목록), not past it; with one, there is no list to see.
    val several = teams.orEmpty().count { !it.archived } >= 2
    if (open != null) { TeamBoardScreen(open, onBack = { madeFrom = null; if (startTeam != null && !several) onBack() else openId = null }, startImport = madeFrom); return }
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
                    // 1.39 [FLOW-23] (PC f7f61818 하): the team list says today too, as the 관리 화면 card does.
                    val nowBoard by store.board(t.id, YearMonth.from(Today.date())).collectAsStateWithLifecycle(null)
                    val today = remember(nowBoard, t, Today.date()) { nowBoard?.let { com.bamnat.core.team.TeamToday.of(t, it, Today.date()) } }
                    if (today != null && members.any { it.active } && nowBoard?.cells?.isNotEmpty() == true) TeamTodayHead(today, Modifier.padding(bottom = 4.dp))
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
    // 1.38 사용자 2026-11-06 (사장님처럼 교대가 없는 유형에 주야가 보임): the team takes the kinds in use, not those put away.
    val folded = com.bamnat.app.ui.shifts.foldedHere()
    val kinds = remember(snap, folded) { teamKinds(snap?.shiftTypes.orEmpty(), folded) }
    // 1.39 G6 [SETUP-18]: with the first setup put off, the calendar's times are the app's samples, not the team's.
    val appSettings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val ownTimes = appSettings.setupDone || calendarId != com.bamnat.app.data.PersonalCalendar.MAIN
    val pickFile = rememberAnyFilePicker { uri ->
        scope.launch {
            reading = "파일을 읽고 있어요…"
            val shifts = kinds.map { TeamShift(it.id, it.label, it.name, it.colorArgb, it.isOff, it.start, it.end) }
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
            Text("근무 종류는 내 달력의 것(${kinds.joinToString(" ") { it.label }})을 그대로 써요." +
                if (ownTimes) "" else " 내 근무 설정을 아직 안 해서, 근무 시간은 팀 표에서 한 번 정해요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }, confirmButton = {
        TextButton(enabled = name.isNotBlank() && people.isNotEmpty() && snap != null, onClick = {
            val s = snap ?: return@TextButton
            val id = UUID.randomUUID().toString()
            val shifts = teamKinds(s.shiftTypes, folded).sortedBy { it.sortOrder }.map { TeamShift(it.id, it.label, it.name, it.colorArgb, it.isOff, it.start, it.end) }
                .let { if (ownTimes) it else com.bamnat.core.team.TeamTimes.blank(it) }
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
    // 1.38 병동 신청표의 왼쪽 'NN off': last month, read with this one by the checks and auto-fill.
    val prev by store.board(team.id, month.minusMonths(1)).collectAsStateWithLifecycle(null)
    val lastWishes by store.wishes(team.id, month.minusMonths(1)).collectAsStateWithLifecycle(emptyList())
    // The board being painted: an editor over the saved one, written back a moment after each change.
    var editor by remember(team.id, month) { mutableStateOf<TeamEditor?>(null) }
    var board by remember(team.id, month) { mutableStateOf<TeamBoard?>(null) }
    LaunchedEffect(startImport) { startImport?.first?.month?.let { if (it != month) monthText = it.toString() } }
    // 1.39 PC e1240d5a: a file or shared photo of another month moves the month while the last month's board is still on its
    // way in; that board used to land on this month (머리 11월, 칸 12월) and the import, waiting for this month's board, never
    // opened. Only this month's board is taken, and one of another month already in hand is replaced.
    LaunchedEffect(saved, month) {
        val s = saved?.takeIf { it.month == month } ?: return@LaunchedEffect
        val here = board?.takeIf { it.month == s.month }
        if (editor == null || here == null || here.members != s.members) {
            editor = TeamEditor(here?.let { now -> val ids = s.members.map { it.id }.toSet(); now.copy(members = s.members, cells = now.cells.filterKeys { it.first in ids }) } ?: s)
            board = editor!!.board
        }
    }
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
    // 1.39 [SPREAD-07] The team's links went out for the first time: the one '다른 팀 리더께도' card may come (below the button).
    LaunchedEffect(linksFor != null) { if (linksFor != null) c.grow.peerDue(com.bamnat.core.guide.Peer.TEAM) }
    // 1.38 [P3-L-15] 병동 전체 보기 (on when this phone holds the team's table link) and 봤어요.
    var ward by remember(team.id) { mutableStateOf(com.bamnat.app.team.TeamReceiveSync.board(context, team.id)?.url) }
    var wardOn by remember(team.id) { mutableStateOf(ward != null) }
    var seen by remember(team.id, monthText) { mutableStateOf<Map<String, com.bamnat.app.team.TeamReceiveSync.LinkSeen>?>(null) }
    // 1.39 G2 (PC a483ca15): members whose link the server had lost and 확정 made anew; the links sheet asks to send them those.
    var freshIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var tiredOn by remember(team.id) { mutableStateOf(com.bamnat.app.team.TeamReceiveSync.tiredBox(context, team.id) != null) }
    var tired by remember(team.id) { mutableStateOf<com.bamnat.app.share.TiredGot?>(null) }
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
    // 1.39 G2: 빈칸 자동 채우기 asked for 근무별 인원 first; on 저장 the fill goes on with the numbers just saved.
    var fillAfterNeed by remember { mutableStateOf(false) }
    suspend fun fill(t: Team) {
        val now = board ?: return
        val people = now.members.filter { it.active }
        val need = c.featureGate.state.value.teamConfirm(store.confirmedMonths(), month, people.size)
        if (!need.allowed) { planAsk = need; return }
        val prevBoard = prev?.takeIf { p -> p.cells.isNotEmpty() }
        // 1.38 [P3-X-04]: the 6 months before lean the fill, so nights and weekends even out across months too.
        val past = com.bamnat.core.team.TeamLedger.past(com.bamnat.core.team.TeamLedger.of(t, now.members, ledgerMonths(store, t, month, 6)))
        plans = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { TeamAuto.plans(t, now, wishes, settings.rules, prev = prevBoard, past = past) }
    }
    // 1.38 [P3-L-14] 신청 받기: the round of this month, read again each time the board opens.
    val round = team.ask?.takeIf { it.month == month }
    var asking by remember { mutableStateOf(false) }
    fun pull() = scope.launch {
        busy = "신청을 가져오고 있어요…"
        runCatching { com.bamnat.app.team.TeamAskSync.pull(c, team) }.onFailure { e ->
            dialog = if (e.message == "not_found") "신청 링크가 서버에 없어요. 끝났거나 지워졌어요. 가져온 신청은 그대로 있어요." else "인터넷이 될 때 다시 가져와 주세요."
        }
        busy = null
    }
    LaunchedEffect(round?.id) { if (round?.token != null) runCatching { com.bamnat.app.team.TeamAskSync.pull(c, team) } }
    // 1.38 [P3-X-02] 바꾸기 요청 of this month, read again each time the board opens.
    val swap = team.swap?.takeIf { it.month == month }
    LaunchedEffect(swap?.id) { if (swap?.token != null) runCatching { com.bamnat.app.team.TeamSwapSync.pull(c, team) } }
    // 1.38 [P3-X-01] 결원 메우기: the gap being filled, and this month's asks (read again each time the board opens).
    var coverGap by remember { mutableStateOf<com.bamnat.core.team.TeamCover.Gap?>(null) }
    val calls = team.calls.filter { YearMonth.from(it.gap.date) == month }
    LaunchedEffect(calls.filter { it.taken == null }.map { it.id }) {
        calls.filter { it.taken == null && it.token != null }.forEach { k -> runCatching { com.bamnat.app.team.TeamCallSync.pull(c, store.teamNow(team.id) ?: team, k.id) } }
    }

    var timesAsked by rememberSaveable(team.id) { mutableStateOf(false) }
    var settingTimes by remember(team.id) { mutableStateOf(false) }
    // 1.39 [SETUP-21] PC 5차 리더 폰(팀은 주 07–15·야 23–07, 내 달력은 주간 09–18·야간 22–07 그대로): the team's times go to the leader's
    // own kinds of the same name too. The app's own sample times change at once (said once); a time the leader set by hand is asked.
    var mineAsk by remember(team.id) { mutableStateOf<List<com.bamnat.core.team.TeamTimes.MineChange>>(emptyList()) }
    fun span(a: java.time.LocalTime?, b: java.time.LocalTime?) = "${a ?: "–"}–${b ?: "–"}"
    if (settingTimes) TeamTimesDialog(team, onDismiss = { settingTimes = false }) { t ->
        settingTimes = false
        scope.launch {
            store.saveTeam(t)
            val changes = runCatching {
                com.bamnat.core.team.TeamTimes.mine(t, c.scheduleRepository.shiftTypes.first(), com.bamnat.app.data.DefaultShiftTypes.all())
            }.getOrDefault(emptyList())
            val now = changes.filter { !it.ask }
            now.forEach { ch -> runCatching { c.scheduleRepository.saveShiftType(ch.type.copy(start = ch.start, end = ch.end)) } }
            mineAsk = changes.filter { it.ask }
            if (mineAsk.isEmpty() && now.isNotEmpty())
                dialog = "내 달력 근무 시간도 팀과 같게 맞췄어요.\n" + now.joinToString("\n") { "${it.type.name} ${span(it.start, it.end)}" }
        }
    }
    if (mineAsk.isNotEmpty()) AlertDialog(onDismissRequest = { mineAsk = emptyList() }, title = { Text("내 달력 근무 시간도 바꿀까요?") }, text = {
        Text("직접 정한 시간이 팀 시간과 달라요. 바꾸면 내 근무 알람도 팀 시간에 맞춰 울려요.\n\n" +
            mineAsk.joinToString("\n") { "${it.type.name} ${span(it.type.start, it.type.end)} → ${span(it.start, it.end)}" })
    }, confirmButton = {
        TextButton(onClick = {
            val list = mineAsk
            mineAsk = emptyList()
            scope.launch { list.forEach { ch -> runCatching { c.scheduleRepository.saveShiftType(ch.type.copy(start = ch.start, end = ch.end)) } } }
        }) { Text("팀 시간으로 바꾸기") }
    }, dismissButton = { TextButton(onClick = { mineAsk = emptyList() }) { Text("내 달력은 그대로") } })
    Scaffold(topBar = {
        TopAppBar(title = { Text(team.name) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            actions = {
                IconButton(onClick = { editor?.let { board = it.undo() } }, enabled = editor?.canUndo == true) { Icon(Icons.AutoMirrored.Filled.Undo, "되돌리기") }
                IconButton(onClick = { editor?.let { board = it.redo() } }, enabled = editor?.canRedo == true) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 하기") }
            })
    }) { padding ->
        if (b == null) { Box(Modifier.padding(padding)) { Text("불러오는 중…", Modifier.padding(16.dp)) }; return@Scaffold }
        val rules = settings.rules
        val prevBoard = prev?.takeIf { p -> p.cells.isNotEmpty() }
        val signs = remember(b, team, rules, prevBoard) { TeamCheck.check(team, b, rules, prevBoard) }
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
            // 1.39 G6 [SETUP-18] 근무 시간 정하기: asked once on the first open, then a card until every kind of work has its times.
            val noTimes = com.bamnat.core.team.TeamTimes.missing(team)
            LaunchedEffect(team.id, noTimes.isNotEmpty()) { if (noTimes.isNotEmpty() && !timesAsked) { timesAsked = true; settingTimes = true } }
            if (noTimes.isNotEmpty()) TeamTimesCard(noTimes) { settingTimes = true }
            // 1.39 [FLOW-23] this month's board starts with today: 출근 수·빈자리 and the day's kinds by start time.
            val today = remember(b, team, Today.date()) { com.bamnat.core.team.TeamToday.of(team, b, Today.date()) }
            if (today != null && active.isNotEmpty()) TeamTodayHead(today)
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
                    Spacer(Modifier.height(if (small) 15.dp else if (round != null) 48.dp else 36.dp))
                    active.forEach { m ->
                        Row(Modifier.width(64.dp).height(cellDp.dp).clickable { member = m.id }, verticalAlignment = Alignment.CenterVertically) {
                            Text(m.name + if (m.skill == Skill.CHARGE) "★" else "", maxLines = 1, style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (m.id == current?.id) FontWeight.Bold else null, modifier = Modifier.weight(1f))
                            // 1.38 병동 신청표의 왼쪽 칸: last month's last days ("NN"), so the first days are painted knowing them.
                            if (!small && prevBoard != null) Text(TeamCheck.tail(team, prevBoard, m.id, 3).trimStart('·'), fontSize = 8.sp, maxLines = 1,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
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
                            // 1.38 [P3-L-14] the paper's day head: how many asked the day off against its 자리.
                            if (round != null && !small) {
                                val asked = TeamAsk.taken(wishes, d)
                                val cap = TeamAsk.cap(round.rules, d)
                                Text(if (asked == 0) "" else if (cap > 0) "$asked/$cap" else "$asked", style = MaterialTheme.typography.labelSmall, fontSize = 9.sp,
                                    color = if (cap in 1 until asked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center, maxLines = 1, modifier = Modifier.width(cellDp.dp).height(12.dp))
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
                                    .semantics { contentDescription = "${m.name} ${Fmt.monthDay(d)} ${t?.name ?: "빈칸"}" + (if (toCheck) ", 확인할 칸" else "") + (wishAt[m.id to d]?.let { ", 신청 ${it.kind.label}" + if (t != null && !TeamAuto.kept(team, it, t)) ", 신청과 달라요" else "" } ?: "") },
                                    contentAlignment = Alignment.Center) {
                                    // 1.38 [P3-L-14] an empty cell shows what was asked (오·연·D?) as the paper did; ♡ is gone.
                                    if (!small) Text(t?.label ?: (wishAt[m.id to d]?.let { wishMark(it, types) } ?: ""), color = if (t != null) Color.White else MaterialTheme.colorScheme.primary,
                                        fontSize = if (t == null) 11.sp else 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                                    if (t != null && wishAt[m.id to d]?.let { !TeamAuto.kept(team, it, t) } == true)
                                        Box(Modifier.size(6.dp).align(Alignment.TopStart).background(MaterialTheme.colorScheme.error, RoundedCornerShape(3.dp)))
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
                swapping = swapFrom != null, onSwap = { swapFrom = if (swapFrom == null) current.id to month.atDay(cursor) else null },
                onCover = { gap -> coverGap = gap })
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
                // 1.39 G2 PC 5066b027: with 신청 open the month is mostly empty, and 31 부족 notes pushed the 신청 card down. While a
                // round is open they fold to one line until asked for.
                val asking = round?.open(Today.millis()) == true
                var shown by remember(round?.id) { mutableStateOf(!asking) }
                if (!shown) {
                    val danger = lines.count { it.kind == TeamCheck.Kind.RULE || it.kind == TeamCheck.Kind.WEEK_HOURS }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("신청을 받는 중이라 접어 뒀어요" + (if (danger > 0) " · 규칙 ${danger}줄" else ""), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = { shown = true }) { Text("펼치기") }
                    }
                    return@SectionCard
                }
                lines.take(if (all) lines.size else 6).forEach { l ->
                    val danger = l.kind == TeamCheck.Kind.RULE || l.kind == TeamCheck.Kind.WEEK_HOURS
                    Text((if (danger) "⚠ " else "· ") + l.text, style = MaterialTheme.typography.bodySmall,
                        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        fontWeight = if (danger) FontWeight.Medium else null)
                }
                if (lines.size > 6) TextButton(onClick = { all = !all }) { Text(if (all) "접기" else "나머지 ${lines.size - 6}줄 더 보기") }
                Text("알림이에요. 그대로 확정해도 돼요. 기준은 설정 › 알림과 잠 › 근무표 점검 기준과 같아요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 1.38 [P3-L-14] 신청 받기: the round's card, above the month's tools.
            if (round != null) {
                val misses = remember(lastWishes, prevBoard) {
                    val types0 = team.shifts.associateBy { it.id }
                    lastWishes.filter { w -> prevBoard?.cell(w.memberId, w.date)?.shiftId?.let(types0::get).let { !TeamAuto.kept(team, w, it) } }
                        .groupingBy { it.memberId }.eachCount()
                }
                AskCard(round, active, wishes, team.shifts, busy != null, misses,
                    onPull = { pull() },
                    onSend = { send(context, com.bamnat.app.team.TeamAskSync.message(team, round, active), "근무 신청 링크 보내기") },
                    onClose = { close -> scope.launch { runCatching { com.bamnat.app.team.TeamAskSync.close(c, team, close) }.onFailure { dialog = "인터넷이 될 때 다시 눌러 주세요." } } },
                    onEnd = { dialog = "askEnd" })
            }
            // 1.38 [P3-X-01] 결원 메우기: each ask of the month, settled ones until closed.
            calls.forEach { k ->
                CallCard(team, k, b.members, busy != null,
                    onPull = { scope.launch { busy = "답을 가져오고 있어요…"; runCatching { com.bamnat.app.team.TeamCallSync.pull(c, team, k.id) }.onFailure { e ->
                        dialog = if (e.message == "not_found") "요청이 서버에 없어요. 기한이 지났거나 거둔 요청이에요." else "인터넷이 될 때 다시 가져와 주세요." }; busy = null } },
                    onSend = { send(context, com.bamnat.app.team.TeamCallSync.message(team, k, b.members), "대신 근무 요청 보내기") },
                    onTake = { m -> change { com.bamnat.core.team.TeamCover.apply(team, it, k.gap, m.id) }; scope.launch { com.bamnat.app.team.TeamCallSync.take(c, team, k.id, m.id) } },
                    onEnd = { scope.launch { com.bamnat.app.team.TeamCallSync.end(c, team, k.id) } })
            }
            if (swap != null) SwapCard(team, swap, b, settings.rules, busy != null,
                onDecide = { ask, yes ->
                    val after = if (yes) com.bamnat.core.team.TeamSwap.apply(b, ask) else b
                    if (yes) change { com.bamnat.core.team.TeamSwap.apply(it, ask) }
                    scope.launch {
                        busy = "링크에 알리고 있어요…"
                        runCatching { com.bamnat.app.team.TeamSwapSync.decide(c, store.teamNow(team.id) ?: team, ask, yes, after, settings.rules, prevBoard) }
                            .onFailure { dialog = if (yes) "표는 바꿨어요. 인터넷이 될 때 '고친 표 올리기'를 눌러 팀원 링크에도 알려 주세요." else "인터넷이 될 때 다시 눌러 주세요." }
                        busy = null
                    }
                },
                onPull = { scope.launch { busy = "요청을 가져오고 있어요…"; runCatching { com.bamnat.app.team.TeamSwapSync.pull(c, team) }.onFailure { e ->
                    dialog = if (e.message == "not_found") "바꾸기 링크가 서버에 없어요. 끝났거나 지워졌어요." else "인터넷이 될 때 다시 가져와 주세요." }; busy = null } },
                onSend = { send(context, com.bamnat.app.team.TeamSwapSync.message(team, swap, b.members), "근무 바꾸기 링크 보내기") },
                onRefresh = { scope.launch { busy = "고친 표를 올리고 있어요…"; runCatching { com.bamnat.app.team.TeamSwapSync.refresh(c, team, b, settings.rules, prevBoard) }
                    .onSuccess { dialog = "팀원 링크에 고친 표와 바꿀 수 있는 날을 새로 올렸어요." }.onFailure { dialog = "인터넷이 될 때 다시 눌러 주세요." }; busy = null } },
                onEnd = { scope.launch { com.bamnat.app.team.TeamSwapSync.end(c, team) } })
            // The month's tools.
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (round == null) OutlinedButton(onClick = { asking = true }) { Text("근무 신청 받기") }
                // 1.38 [P3-X-02]: once the month is painted, members trade days from their own links.
                if (swap == null && b.cells.isNotEmpty()) OutlinedButton(enabled = busy == null, onClick = {
                    scope.launch {
                        busy = "바꿀 수 있는 날을 찾고 있어요…"
                        val members = swapPeople(b)
                        runCatching { com.bamnat.app.team.TeamSwapSync.open(c, store.teamNow(team.id) ?: team, b, members, settings.rules, prevBoard) }
                            .onSuccess { t -> t.swap?.let { send(context, com.bamnat.app.team.TeamSwapSync.message(t, it, members), "근무 바꾸기 링크 보내기") } }
                            .onFailure { dialog = "바꾸기 링크를 못 만들었어요. 인터넷이 될 때 다시 눌러 주세요." }
                        busy = null
                    }
                }) { Text("바꾸기 요청 받기") }
                OutlinedButton(onClick = { scope.launch { val last = store.boardNow(team.id, month.minusMonths(1)); change { TeamCopy.nextMonth(last, it) } } }) { Text("지난달 이어 칠하기") }
                OutlinedButton(onClick = {
                    scope.launch {
                        // 1.38 PC 11차 (23): with no 근무별 인원 the fill can only put days off ("1안 · 부족 없음" meant nothing), so the
                        // numbers are asked first; 1.39 G2 (1.38 최종 PC 제안 ②) and the fill runs as soon as they are saved.
                        if (team.need.values.none { it > 0 }) { fillAfterNeed = true; dialog = "need"; return@launch }
                        fill(team)
                    }
                }, enabled = b.empty > 0) { Text("빈칸 자동 채우기") }
                OutlinedButton(onClick = { dialog = "need" }) { Text("근무별 인원") }
                OutlinedButton(onClick = { dialog = "wish" }) { Text("신청·희망 ${wishes.size}") }
                OutlinedButton(onClick = { dialog = "fair" }) { Text("공정성 표") }
                OutlinedButton(onClick = { dialog = "members" }) { Text("팀원") }
                ImportButton(onClick = pickFile)
            }
            val changes = remember(b, was, state) { if (state.state == MonthState.CONFIRMED) b.changesFrom(TeamBoard(month, b.members, was.mapValues { (k, v) -> com.bamnat.core.team.TeamCell(k.first, k.second, v) })) else emptyList() }
            // 1.39 G2 (PC f7f61818): with links gone and nothing changed, '링크 다시 보내기' next to '새 링크 n개 만들어 보내기' read as two ways
            // to the same thing; the 봤어요 line's button is the one left.
            val goneNow = seen?.let { com.bamnat.app.team.TeamReceiveSync.seenLine(state.version, active, it).gone }.orEmpty()
            if (!(state.state == MonthState.CONFIRMED && changes.isEmpty() && goneNow.isNotEmpty())) Button(onClick = {
                scope.launch {
                    // 1.38 PC 11차: a member with no day filled has nothing to share (the server refuses an empty link), so say who
                    // before 확정 instead of confirming and then blaming the internet.
                    // 1.39 G6 [SETUP-18]: the links carry each day's times (the members' alarms), so they are set before 확정.
                    if (com.bamnat.core.team.TeamTimes.missing(team).isNotEmpty()) { settingTimes = true; return@launch }
                    val blank = active.filter { b.daysOf(it.id).isEmpty() }
                    if (blank.isNotEmpty()) {
                        dialog = blank.joinToString(", ") { it.name } + "님은 이달 칸이 모두 비어 있어요. 근무나 오프를 칠한 뒤 확정해 주세요. " +
                            "이달 쉬는 팀원이면 '팀원'에서 그 사람을 빼며 '쉼으로 두기'를 고르면 링크에서 빠져요."
                        return@launch
                    }
                    // 1.38 Phase 3 요금: the first confirmed month is free; after it the 팀 요금제 band that holds the team.
                    val need = c.featureGate.state.value.teamConfirm(store.confirmedMonths(), month, active.size)
                    if (!need.allowed) { planAsk = need; return@launch }
                    busy = "링크를 만들고 있어요…"
                    // 1.38 검토: sending the links again with nothing changed keeps the same 판 (it used to count one up each time).
                    val again = state.state == MonthState.CONFIRMED && changes.isEmpty()
                    val m = if (again) state else store.confirm(team.id, b, state)
                    if (!again) c.telemetry.event(com.bamnat.core.stats.UsageEvent.TEAM_CONFIRM.key)
                    if (need.verdict == com.bamnat.core.tier.WorkPlanRules.Verdict.FREE) store.keepFreeMonth(month)
                    val made = runCatching { links(c, team, b, active, if (state.state == MonthState.CONFIRMED && !again) was else emptyMap()) }
                    // 1.38 [P3-L-15] 병동 전체 보기, when on: the table link follows each 확정.
                    if (made.isSuccess && wardOn) runCatching { com.bamnat.app.team.TeamReceiveSync.putBoard(c, context, team, b, active) }
                        .onSuccess { ward = it.url }.onFailure { dialog = "팀 전체 표는 인터넷이 될 때 다시 확정을 눌러 주세요." }
                    busy = null
                    // 1.38 PC 1차: after 다시 확정 the links are the same ones, only their days changed: say so, and offer to send anyway.
                    made.onFailure { c.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_ERROR.key, "team_links") }
                    made.onSuccess { c.telemetry.event(com.bamnat.core.stats.UsageEvent.TEAM_LINKS.key) }
                    made.onSuccess { l ->
                        freshIds = l.fresh
                        // 1.39 G2: a link the server no longer had was made anew, so "팀원 링크는 그대로" would be wrong: the new ones are shown to send.
                        if (state.state == MonthState.CONFIRMED && !again && l.fresh.isEmpty()) resent = m.version to l.list else linksFor = l.list
                    }.onFailure { e ->
                        val why = when ((e as? com.bamnat.app.share.ShareLinkException)?.message) {
                            "days" -> "칸이 모두 빈 팀원이 있어 링크를 못 만들었어요. 칠한 뒤 다시 눌러 주세요."
                            "paused", "rate_limited" -> "링크 만들기가 잠시 멈췄어요. 조금 뒤에 다시 눌러 주세요."
                            "contact" -> (e as com.bamnat.app.share.ShareLinkException).friendly.orEmpty()
                            else -> "링크는 인터넷이 될 때 다시 눌러 주세요."
                        }
                        dialog = (if (again) "" else "확정은 했어요(${m.version}판). ") + why
                    }
                }
            }, enabled = busy == null, modifier = Modifier.fillMaxWidth()) {
                Text(when {
                    state.state != MonthState.CONFIRMED -> "확정하고 팀원 링크 보내기"
                    changes.isEmpty() -> "링크 다시 보내기"
                    else -> "바뀐 칸 ${changes.size}개로 다시 확정"
                })
            }
            busy?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (linksFor == null) com.bamnat.app.ui.pro.PeerInviteCard(com.bamnat.core.guide.Peer.TEAM)
            Text("팀원은 앱이 없어도 링크로 자기 근무를 봐요. 앱이 있으면 내 달력에 넣을 수 있어요. 고친 뒤 다시 확정하면 같은 링크가 바뀌고, 밤낮 1.39 이상을 쓰는 팀원은 6시간 안에 바뀐 날이 자기 달력과 알람에 들어가요(팀원이 직접 바꾼 날은 그대로).",
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
            // 1.38 [P3-L-15] 병동 전체 보기: one more link with everyone's month and each day's count, only when the leader turns it on.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("팀 전체 표 링크도 같이 보내기", style = MaterialTheme.typography.bodyMedium)
                    Text("링크 하나에 모두의 한 달과 날마다 근무별 인원이 보여요. 이름이 다 들어가니 팀에서 괜찮을 때 켜요. 확정할 때마다 같은 주소가 새로 바뀌고, 끄면 바로 지워요.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    ward?.let { u -> TextButton(onClick = { send(context, "${team.name} ${Fmt.month(month)} 팀 전체 근무표\n$u", "팀 전체 표 보내기") }) { Text("팀 전체 표 보내기") } }
                }
                androidx.compose.material3.Switch(checked = wardOn, onCheckedChange = { on ->
                    wardOn = on
                    if (!on) scope.launch { com.bamnat.app.team.TeamReceiveSync.dropBoard(c, context, team.id); ward = null }
                    else if (state.state == MonthState.CONFIRMED) scope.launch {
                        runCatching { com.bamnat.app.team.TeamReceiveSync.putBoard(c, context, team, b, active) }.onSuccess { ward = it.url }
                            .onFailure { dialog = "인터넷이 될 때 다시 켜 주세요."; wardOn = false }
                    }
                })
            }
            // 1.38 [P3-L-18] 피곤해요 합계: members who turn it on in their own app press 피곤해요 in the morning; the leader sees only
            // how many a day, and nothing at all for a day under 3. Off until the leader turns it on.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("팀 피곤해요 합계 받기", style = MaterialTheme.typography.bodyMedium)
                    Text("앱을 쓰는 팀원이 스스로 켜면, 아침에 누른 '피곤해요'가 날마다 몇 명인지만 와요. 이름·잠 기록은 오지 않고, 3명 미만인 날은 숫자도 보이지 않아요.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                androidx.compose.material3.Switch(checked = tiredOn, onCheckedChange = { on ->
                    tiredOn = on
                    scope.launch {
                        if (on) runCatching { com.bamnat.app.team.TeamReceiveSync.tiredOn(c, context, team, active) }
                            .onFailure { dialog = "인터넷이 될 때 다시 켜 주세요."; tiredOn = false }
                        else { com.bamnat.app.team.TeamReceiveSync.tiredOff(c, context, team.id, active); tired = null }
                    }
                })
            }
            if (tiredOn) {
                LaunchedEffect(team.id) {
                    com.bamnat.app.team.TeamReceiveSync.tiredBox(context, team.id)?.let { bx -> tired = runCatching { c.shareApi.tiredRead(bx.id, bx.token) }.getOrNull() }
                }
                Text(tired?.let { com.bamnat.app.team.TeamReceiveSync.tiredLine(it, Today.date()) }?.let { "최근 7일 피곤해요: $it" }
                    ?: "최근 7일 피곤해요: 아직 없어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // 1.38 [P3-L-15] 봤어요: who opened their link since this 확정, and a resend to the others only.
            if (state.state == MonthState.CONFIRMED && active.any { it.linkId != null }) {
                // 1.39 G2 (PC a483ca15): read with the members as saved now, and again whenever a link changes, so a link 확정 has just
                // made anew is never asked by its old address; not while 확정 is still making the links.
                suspend fun readSeen() = com.bamnat.app.team.TeamReceiveSync.seen(c, store.boardNow(team.id, month).members.filter { it.active })
                val linkKey = active.joinToString(",") { it.linkId.orEmpty() }
                LaunchedEffect(team.id, monthText, state.version, linkKey, busy == null) { if (busy == null) seen = readSeen() }
                seen?.let { got ->
                    val line = com.bamnat.app.team.TeamReceiveSync.seenLine(state.version, active, got)
                    Text(line.text, style = MaterialTheme.typography.bodyMedium,
                        color = if (line.gone.isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (line.gone.isNotEmpty()) Button(enabled = busy == null, onClick = {
                            scope.launch {
                                busy = "새 링크를 만들고 있어요…"
                                val now = store.boardNow(team.id, month)
                                val goneIds = line.gone.map { it.id }.toSet()
                                runCatching { links(c, team, b, now.members.filter { it.active && it.id in goneIds }) }
                                    .onSuccess { l -> freshIds = l.list.map { it.first.id }.toSet(); linksFor = l.list }
                                    .onFailure { dialog = "새 링크는 인터넷이 될 때 다시 눌러 주세요." }
                                busy = null
                            }
                        }) { Text("새 링크 ${line.gone.size}개 만들어 보내기") }
                        if (line.notSeen.isNotEmpty()) OutlinedButton(onClick = {
                            send(context, "${team.name} ${Fmt.month(month)} 근무표가 나왔어요(바뀐 날은 그 날에 적혀 있어요).\n" +
                                line.notSeen.mapNotNull { m -> com.bamnat.app.team.TeamReceiveSync.url(m)?.let { "${m.name}: $it" } }.joinToString("\n"), "안 본 사람에게 보내기")
                        }) { Text("안 본 ${line.notSeen.size}명에게만 보내기") }
                        TextButton(enabled = busy == null, onClick = { scope.launch { seen = readSeen() } }) { Text("다시 보기") }
                    }
                }
            }
            Spacer(Modifier.heightIn(min = 24.dp))
        }
        when (dialog) {
            null -> Unit
            "need" -> NeedDialog(team, thenFill = fillAfterNeed, onDismiss = { dialog = null; fillAfterNeed = false }) { t ->
                val go = fillAfterNeed && t.need.values.any { it > 0 }
                fillAfterNeed = false
                dialog = null
                scope.launch { store.saveTeam(t); if (go) fill(t) }
            }
            "wish" -> WishDialog(team, month, active, wishes, onDismiss = { dialog = null },
                onOpenAsk = if (round == null) ({ dialog = null; asking = true }) else null)
            "fair" -> FairDialog(team, b, month, onDismiss = { dialog = null })
            "askEnd" -> AlertDialog(onDismissRequest = { dialog = null }, title = { Text("신청 끝내기") }, text = {
                Text("서버에 있는 신청과 팀원 신청 링크를 지워요. 이미 가져온 신청은 이 휴대폰 표에 그대로 남아요. 끝내기 전에 한 번 더 가져올게요.")
            }, confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    scope.launch {
                        busy = "신청을 정리하고 있어요…"
                        runCatching { com.bamnat.app.team.TeamAskSync.pull(c, team)?.let { com.bamnat.app.team.TeamAskSync.end(c, it) } ?: com.bamnat.app.team.TeamAskSync.end(c, team) }
                            .onFailure { dialog = "인터넷이 될 때 다시 눌러 주세요." }
                        busy = null
                    }
                }) { Text("끝내기") }
            }, dismissButton = { TextButton(onClick = { dialog = null }) { Text("닫기") } })
            "members" -> MembersDialog(team, b.members, onDismiss = { dialog = null })
            else -> AlertDialog(onDismissRequest = { dialog = null }, confirmButton = { TextButton(onClick = { dialog = null }) { Text("알겠어요") } }, text = { Text(dialog!!) })
        }
    }
    coverGap?.let { gap ->
        val now = board
        if (now != null) {
            val list = remember(gap, now, wishes, prev, settings.rules) {
                com.bamnat.core.team.TeamCover.candidates(team, now, gap, settings.rules, wishes, prev?.takeIf { it.cells.isNotEmpty() })
            }
            CoverDialog(team, gap, now.members.firstOrNull { it.id == gap.memberId }, list, busy != null, onDismiss = { coverGap = null },
                onPut = { m -> coverGap = null; change { com.bamnat.core.team.TeamCover.apply(team, it, gap, m.id) }
                    dialog = "${m.name}님을 넣었어요. 되돌리기로 바로 물릴 수 있어요. 확정을 다시 누르면 두 사람 링크도 고쳐져요." },
                onAsk = { people ->
                    coverGap = null
                    scope.launch {
                        busy = "요청 링크를 만들고 있어요…"
                        runCatching { com.bamnat.app.team.TeamCallSync.open(c, store.teamNow(team.id) ?: team, gap, people) }
                            .onSuccess { (t, k) -> send(context, com.bamnat.app.team.TeamCallSync.message(t, k, now.members), "대신 근무 요청 보내기") }
                            .onFailure { dialog = "요청 링크를 못 만들었어요. 인터넷이 될 때 다시 눌러 주세요." }
                        busy = null
                    }
                })
        }
    }
    planAsk?.let { com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.TEAM, it, onDismiss = { planAsk = null }) }
    if (asking) AskSetupDialog(month, board?.members?.count { it.active } ?: 0, onDismiss = { asking = false }) { rules ->
        asking = false
        scope.launch {
            busy = "신청 링크를 만들고 있어요…"
            val members = board?.members?.filter { it.active }.orEmpty()
            runCatching { com.bamnat.app.team.TeamAskSync.open(c, team, month, rules, members) }
                .onSuccess { t -> t.ask?.let { send(context, com.bamnat.app.team.TeamAskSync.message(t, it, members), "근무 신청 링크 보내기") } }
                .onFailure { dialog = "신청 링크를 못 만들었어요. 인터넷이 될 때 다시 눌러 주세요." }
            busy = null
        }
    }
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
                // 1.38 [P3-L-17] 종이 신청표: the cells go in as 신청, the board stays as it is.
                choice.wishCodes?.let { wc ->
                    val list = com.bamnat.core.team.TeamGridImport.wishes(rows, ids, wc, Today.millis())
                    store.addWishes(team.id, list, "PAPER")
                    c.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_IMPORT.key, if (photo) "team_wish_photo" else "team_wish_file")
                    dialog = "신청 ${list.size}개를 ${list.map { it.memberId }.toSet().size}명에게 넣었어요" + (if (made.isNotEmpty()) "(새 팀원 ${made.size}명)" else "") +
                        ". '신청·희망'에서 보고 고칠 수 있고, 빈칸 자동 채우기가 지켜요."
                    return@launch
                }
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
            Text("팀원 링크는 그대로이고, 링크를 열면 바뀐 근무가 보여요. 밤낮 1.39 이상을 쓰는 팀원은 6시간 안에 바뀐 날이 자기 달력과 알람에 들어가요. " +
                "아이폰 팀원이 링크에서 '캘린더에 넣기'를 해 두었다면 그 캘린더도 몇 시간 안에 따라 바뀌어요. 그 밖의 팀원은 저절로 알지 못하니 '그래도 알리기'로 알려 주세요.", style = MaterialTheme.typography.bodyMedium)
        }, confirmButton = { TextButton(onClick = { resent = null }) { Text("알겠어요") } },
            dismissButton = { TextButton(onClick = { resent = null; linksFor = list }) { Text("그래도 알리기") } })
    }
    linksFor?.let { list ->
        val fresh = list.filter { it.first.id in freshIds }
        AlertDialog(onDismissRequest = { linksFor = null; freshIds = emptySet() }, title = { Text("팀원 링크") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // 1.39 G2 (PC a483ca15): the server no longer had these links (기간이 지남·지워짐), so the old addresses do not open.
                if (fresh.isNotEmpty()) {
                    Text(fresh.joinToString(", ") { it.first.name } + "님 링크가 서버에 없어서 새로 만들었어요. 예전 링크는 열리지 않으니 새 링크를 꼭 보내 주세요. " +
                        "밤낮 앱에 예전 링크를 넣어 둔 팀원은 새 링크를 한 번 열어 다시 넣어야 해요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    if (fresh.size < list.size) Button(onClick = { send(context, "${team.name} ${Fmt.month(month)} 근무표 새 링크예요(예전 링크는 열리지 않아요).\n" +
                        fresh.joinToString("\n") { (m, u) -> "${m.name}: $u" }, "새 링크 보내기") }) { Text("새 링크 ${fresh.size}개만 보내기") }
                }
                Text("한 사람씩 보내거나, 모두 한 번에 단톡방에 보낼 수 있어요. 링크마다 그 사람 근무만 보여요.", style = MaterialTheme.typography.bodySmall)
                // 1.39 [IOS-07] The leader need not ask an iPhone teammate to install anything.
                Text("아이폰 팀원도 링크로 바로 봐요. 링크의 '아이폰 캘린더에 넣기'를 누르면 근무가 자기 캘린더에 들어가고, 다시 확정하면 따라 바뀌어요. 출근 알람은 안드로이드 밤낮 앱에서만 울려요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { send(context, list.joinToString("\n") { (m, u) -> "${m.name}: $u" } + (ward?.let { "\n팀 전체: $it" } ?: ""), "${team.name} ${Fmt.month(month)} 근무표") }) { Text("모두 한 번에 보내기") }
                list.forEach { (m, url) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(m.name + if (m.id in freshIds) " · 새 링크" else "", modifier = Modifier.weight(1f))
                        TextButton(onClick = { send(context, "${team.name} ${Fmt.month(month)} ${m.name} 근무표\n$url", m.name) }) { Text("보내기") }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { linksFor = null; freshIds = emptySet() }) { Text("닫기") } })
    }
}

/**
 * The keypad: one key a kind of day, then ✕ and »; the marked day of the chosen member gets the key and the mark moves on as
 * [PaintPad] moves it. After the month's last day it goes on to the next member, so the whole team is painted without lifting.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Keypad(team: Team, b: TeamBoard, m: TeamMember, month: YearMonth, cursor: Int, onCursor: (Int) -> Unit, onMember: (String) -> Unit,
                   change: ((TeamBoard) -> TeamBoard) -> Unit, swapping: Boolean, onSwap: () -> Unit,
                   onCover: (com.bamnat.core.team.TeamCover.Gap) -> Unit) {
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
            // 1.38 [P3-X-01]: this person cannot come on a working day; who could, best first.
            val work = cell?.shiftId?.let { id -> team.shifts.firstOrNull { it.id == id && !it.isOff } }
            if (work != null) TextButton(onClick = { onCover(com.bamnat.core.team.TeamCover.Gap(m.id, month.atDay(cursor), work.id)) }) { Text("결원 메우기") }
        }
    }
}

/** What 확정 sent: each member's link, and [fresh] the members who had a link the server no longer has, made anew (a new address). */
private class Made(val list: List<Pair<TeamMember, String>>, val fresh: Set<String>)

/** 확정: each member a link of their own days only (설계 2-1 "그 사람 근무만"), the same link again after the first time. */
private suspend fun links(c: com.bamnat.app.AppContainer, team: Team, b: TeamBoard, members: List<TeamMember>,
                          was: Map<Pair<String, LocalDate>, Long> = emptyMap()): Made {
    // 1.38 [P3-L-15] 다시 확정: each changed day says so on the member's link ("바뀜: D→E").
    val changed = com.bamnat.core.team.TeamReceive.changes(team, b, was)
    val types = team.shifts.map { it.asType() }
    val out = ArrayList<Pair<TeamMember, String>>()
    val updated = ArrayList<TeamMember>()
    for (m in members) {
        val entries = b.daysOf(m.id).map { (d, id) -> DayEntry(d, ShiftOverride.Shift(id)) }
        // 1.38 같이 쉬는 사람 (P3-L-11): only when the leader turned it on; the day's line under the member's own day off.
        val off = team.shifts.filter { it.isOff }.map { it.id }.toSet()
        val together = com.bamnat.core.team.TeamReceive.merge(
            if (team.showTogether) b.offTogether(m.id) { it in off }.mapValues { (_, n) -> "같이 쉬어요: " + n.joinToString(", ") } else emptyMap(),
            changed[m.id].orEmpty())
        // 1.39 [IOS-01] (사용자 2026-11-07 결정) A member's link always carries its calendar address, whatever the leader's plan: a
        // teammate on an iPhone (no 밤낮 app) puts the days in their own calendar from the page, and it follows each 다시 확정.
        val payload = ShareLinkPayload.build(ScheduleSnapshot(shiftTypes = types, patterns = emptyList(), entries = entries), b.month.atDay(1),
            "${team.name} ${m.name}", ttlDays = 90, options = com.bamnat.app.share.ShareOptions(memos = together.isNotEmpty()), memos = together,
            feed = com.bamnat.core.time.Today.zone()).apply {
            // Without the phone's 시간대 이름 (the server reads Asia/Seoul), so only the 'on' mark goes. 처리방침 3.12 names it (총괄 몫).
            remove("tz")
            // 1.39 PC (SPREAD 2차): the page speaks to a teammate from the first 확정 (card, preview), not only after a 바뀜.
            put("team", true)
        }
        val id = m.linkId
        val token = m.linkToken
        val st = if (id != null && token != null) runCatching {
            c.shareApi.update(ShareLinkState(id, "", token, Today.instant(), "${team.name} ${m.name}"), payload)
        }.getOrElse { c.shareApi.create(payload) } else c.shareApi.create(payload)
        out += m to st.url
        updated += m.copy(linkId = st.id, linkToken = st.deleteToken)
        // 봤어요: asking once turns it on for the link, so the first open after this 확정 is kept.
        runCatching { c.shareApi.seen(st.id, st.deleteToken) }
        // 1.38 [P3-L-18] a link made after 피곤해요 합계 was turned on joins it.
        if (st.id != id) com.bamnat.app.team.TeamReceiveSync.tiredBox(c.appContext, team.id)?.let { bx -> runCatching { c.shareApi.tiredTie(st.id, st.deleteToken, bx.id) } }
    }
    c.phaseThree.saveMembers(updated)
    return Made(out, updated.filter { u -> members.any { it.id == u.id && it.linkId != null && it.linkId != u.linkId } }.map { it.id }.toSet())
}

private fun send(context: android.content.Context, text: String, title: String) {
    val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(Intent.createChooser(i, title)) }
}

/** [TEAM-15] 근무별 최소 인원: "D 3명". */
@Composable
private fun NeedDialog(team: Team, thenFill: Boolean = false, onDismiss: () -> Unit, onSave: (Team) -> Unit) {
    var need by remember { mutableStateOf(team.need) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("근무별 인원") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("날마다 꼭 있어야 하는 사람 수예요. 모자란 날은 날짜가 주황색이 되고, 자동 채우기가 이 수만큼 채워요.", style = MaterialTheme.typography.bodySmall)
            if (team.need.values.none { it > 0 }) Text("빈칸 자동 채우기는 이 수를 먼저 정해야 써요. 근무마다 하루에 몇 명이 있어야 하는지 정해 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            team.shifts.filter { !it.isOff }.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${s.label} ${s.name}", modifier = Modifier.weight(1f))
                    com.bamnat.app.ui.components.CountStepper(need[s.id] ?: 0, { n -> need = if (n <= 0) need - s.id else need + (s.id to n) },
                        name = "${s.name} 인원", range = 0..30, format = { "${it}명" })
                }
            }
        }
    }, confirmButton = {
        // 1.39 G2 (1.38 최종 PC 제안 ②): from 빈칸 자동 채우기 the 안 come up right after 저장, not on a second press.
        val fills = thenFill && need.values.any { it > 0 }
        TextButton(onClick = { onSave(team.copy(need = need)) }) { Text(if (fills) "저장하고 채우기" else "저장") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/** [TEAM-19] 희망 휴무: pasted lines from members ("[밤낮 희망] 민지 11월 3 7 쉬고"), or typed by the leader; ♡ on the board. */
@Composable
private fun WishDialog(team: Team, month: YearMonth, members: List<TeamMember>, wishes: List<TeamWish>, onDismiss: () -> Unit,
                       onOpenAsk: (() -> Unit)? = null) {
    val store = LocalContext.current.appContainer.phaseThree
    val scope = rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    val read = remember(text, members) { TeamWishText.read(text, month, members) }
    val names = members.associate { it.id to it.name }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("희망 휴무 (${Fmt.monthShort(month)})") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 1.39 G2 통합 6차 PC: '신청·희망 0' was found but not '근무 신청 받기' beside it; the way members ask for themselves starts here too.
            if (onOpenAsk != null) {
                Text("팀원이 직접 신청하게 하려면 링크를 보내요. 팀원은 앱 없이 그 링크에서 날짜를 골라 보내요.", style = MaterialTheme.typography.bodySmall)
                OutlinedButton(onClick = onOpenAsk, modifier = Modifier.fillMaxWidth()) { Text("근무 신청 받기 (팀원에게 링크)") }
                Text("또는", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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
private fun FairDialog(team: Team, b: TeamBoard, month: YearMonth, onDismiss: () -> Unit) {
    // 1.38 [P3-X-04] 공정성 장부: this month as it is now, or the 3·6·12 months before it added up (신청 지킨 비율과 함께).
    val store = LocalContext.current.appContainer.phaseThree
    var span by rememberSaveable { mutableStateOf(0) }
    var past by remember { mutableStateOf<List<com.bamnat.core.team.TeamLedger.Row>?>(null) }
    LaunchedEffect(span, b.members) {
        past = null
        if (span > 0) past = com.bamnat.core.team.TeamLedger.of(team, b.members, ledgerMonths(store, team, month, span, withThis = b))
    }
    val now = remember(team, b) { TeamCheck.fairness(team, b) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("공정성 장부") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "이번 달", 3 to "3달", 6 to "6달", 12 to "12달").forEach { (n, l) -> FilterChip(selected = span == n, onClick = { span = n }, label = { Text(l) }) }
            }
            if (span == 0) {
                Row { listOf("이름", "나이트", "주말", "쉼", "시간").forEach { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)) } }
                val maxN = now.maxOfOrNull { it.nights } ?: 0
                val minN = now.minOfOrNull { it.nights } ?: 0
                now.forEach { r ->
                    Row {
                        Text(r.member.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                        Text("${r.nights}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                            color = if (maxN - minN >= 2 && (r.nights == maxN || r.nights == minN)) com.bamnat.app.ui.theme.LocalCalendarColors.current.warning else Color.Unspecified)
                        Text("${r.weekends}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${r.offs}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${r.minutes / 60}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    }
                }
            } else {
                val rows = past
                if (rows == null) Text("지난 달들을 모으고 있어요…", style = MaterialTheme.typography.bodySmall)
                else {
                    Text("이번 달까지 ${span}달, 한 달 평균이에요. 자동 채우기는 지난 6달을 보고 많이 한 사람에게 덜 넣어요.",
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row { listOf("이름", "나이트", "주말", "공휴일", "신청 지킴").forEach { Text(it, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f)) } }
                    val per = rows.filter { it.months > 0 }.map { it.nights.toDouble() / it.months }
                    val hi = per.maxOrNull() ?: 0.0
                    val lo = per.minOrNull() ?: 0.0
                    rows.forEach { r ->
                        fun avg(x: Int) = if (r.months == 0) "-" else "%.1f".format(x.toDouble() / r.months)
                        val n = if (r.months == 0) 0.0 else r.nights.toDouble() / r.months
                        Row {
                            Text(r.member.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1)
                            Text(avg(r.nights), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                                color = if (hi - lo >= 1.5 && r.months > 0 && (n == hi || n == lo)) com.bamnat.app.ui.theme.LocalCalendarColors.current.warning else Color.Unspecified)
                            Text(avg(r.weekends), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(avg(r.holidays), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(r.keptPercent?.let { "$it%" } ?: "-", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/** 1.38 [P3-X-04] The [n] months up to [month] (this one as [withThis] when given, else before it), with each month's 신청 and 공휴일. */
internal suspend fun ledgerMonths(store: com.bamnat.app.data.PhaseThreeStore, team: Team, month: YearMonth, n: Int, withThis: TeamBoard? = null):
    List<com.bamnat.core.team.TeamLedger.Month> = (if (withThis != null) 0 until n else 1..n).map { k ->
    val m = month.minusMonths(k.toLong())
    val board = if (k == 0 && withThis != null) withThis else store.boardNow(team.id, m)
    val holidays = (1..m.lengthOfMonth()).map(m::atDay).filter(com.bamnat.core.holiday.KoreanHolidays::isHoliday).toSet()
    com.bamnat.core.team.TeamLedger.Month(board, store.wishes(team.id, m).first(), holidays)
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
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var hiring by remember { mutableStateOf<TeamMember?>(null) }
    hiring?.let { m ->
        val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = (m.hiredOn ?: com.bamnat.core.time.Today.date()).toEpochDay() * 86_400_000L)
        com.bamnat.app.ui.components.DatePickerDialog(onDismissRequest = { hiring = null }, confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { ms -> scope.launch { store.saveMembers(listOf(m.copy(hiredOn = LocalDate.ofEpochDay(ms / 86_400_000L)))) } }
                hiring = null
            }) { Text("고르기") }
        }, dismissButton = {
            Row {
                if (m.hiredOn != null) TextButton(onClick = { scope.launch { store.saveMembers(listOf(m.copy(hiredOn = null))) }; hiring = null }) { Text("지우기") }
                TextButton(onClick = { hiring = null }) { Text("닫기") }
            }
        }) {
            com.bamnat.app.ui.components.YearJump(state)
            androidx.compose.material3.DatePicker(state)
        }
    }
    // 1.39 G2 통합 7차 PC: a name could not be changed (a 전화번호 put in it meant 빼고 다시 더하기, which wipes their days). Pressing the
    // name changes it in place; the days, links and 입사일 stay. CARE-09's refusal points here.
    var renaming by remember { mutableStateOf<TeamMember?>(null) }
    renaming?.let { m ->
        var name by rememberSaveable(m.id) { mutableStateOf(m.name) }
        val clean = name.trim().take(20)
        val taken = clean.isNotEmpty() && clean != m.name && members.any { it.id != m.id && it.name == clean }
        AlertDialog(onDismissRequest = { renaming = null }, title = { Text("이름 고치기") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(name, { name = it.take(20) }, label = { Text("팀원 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(if (taken) "같은 이름의 팀원이 있어요. 구별되게 적어 주세요." else "칠한 칸·링크·입사일은 그대로예요. 이름은 다음 확정 때 그 팀원 링크에도 바뀌어요. 전화번호·계좌는 넣지 않아요.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (taken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }, confirmButton = {
            TextButton(enabled = clean.isNotEmpty() && clean != m.name && !taken, onClick = {
                scope.launch { store.saveMembers(listOf(m.copy(name = clean))) }
                renaming = null
            }) { Text("저장") }
        }, dismissButton = { TextButton(onClick = { renaming = null }) { Text("닫기") } })
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
                    Text(m.name + (if (!m.active) " (쉬는 중)" else "") + " ✎", style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).clickable(role = androidx.compose.ui.semantics.Role.Button, onClickLabel = "이름 고치기") { renaming = m }
                            .padding(vertical = 10.dp))
                    FilterChip(selected = m.skill == Skill.CHARGE, onClick = { scope.launch { store.saveMembers(listOf(m.copy(skill = if (m.skill == Skill.CHARGE) Skill.REG else Skill.CHARGE))) } },
                        label = { Text("책임") })
                    TextButton(onClick = { if (m.active) rest(m) else scope.launch { store.saveMembers(listOf(m.copy(active = true))) } }) { Text(if (m.active) "쉼" else "복귀") }
                    TextButton(onClick = { removing = m }) { Text("빼기") }
                }
                // 1.38 [P3-X-05]: 입사일 → 남은 연차 on the 신청 링크.
                TextButton(onClick = { hiring = m }) {
                    Text(m.hiredOn?.let { "입사 ${it.year}.${it.monthValue}.${it.dayOfMonth} · 바꾸기" } ?: "입사일 넣기(연차 남은 날 계산)", style = MaterialTheme.typography.labelSmall)
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

/**
 * 1.38 사용자 2026-11-06: the calendar's kinds a new team starts with, without those put away ('안 쓰는 종류', a 자영업's 주간·오후·야간);
 * all of them when that would leave no kind to work in, so the board can still be painted.
 */
internal fun teamKinds(types: List<com.bamnat.core.model.ShiftType>, folded: Set<Long>): List<com.bamnat.core.model.ShiftType> =
    types.filter { it.id !in folded }.takeIf { kept -> kept.any { !it.isOff } } ?: types

/** 1.39 G6 [SETUP-18] The kinds of work still without times, and what they are for. */
@Composable
private fun TeamTimesCard(missing: List<TeamShift>, onSet: () -> Unit) {
    val cal = com.bamnat.app.ui.theme.LocalCalendarColors.current
    androidx.compose.material3.Surface(onClick = onSet, shape = MaterialTheme.shapes.medium, color = cal.warningContainer, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("근무 시간이 비어 있어요: ${missing.joinToString(" ") { it.label }}", style = MaterialTheme.typography.titleSmall)
                Text("팀원 링크의 알람과 오늘 출근 시각이 이 시간으로 나가요. 확정하기 전에 정해 주세요.", style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onSet, modifier = Modifier.heightIn(min = 48.dp)) { Text("정하기") }
        }
    }
}

/**
 * 1.39 G6 [SETUP-18] 근무 시간 정하기: each kind of work's start and end, set once for the team. When the leader's own calendar has
 * its times by now (the first setup finished meanwhile), they are offered first.
 */
@Composable
private fun TeamTimesDialog(team: Team, onDismiss: () -> Unit, onSave: (Team) -> Unit) {
    val c = LocalContext.current.appContainer
    val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val mine by c.scheduleRepository.shiftTypes.collectAsStateWithLifecycle(emptyList())
    val work = team.shifts.filter { !it.isOff }
    // 1.39 G6 발굴 [SETUP-19] (PC 5c4ad849): the usual times by name already in, so a 3교대 team is one 저장 when they are its own.
    val suggested = remember(team.id) { com.bamnat.core.team.TeamTimes.suggest(team.shifts) }
    val times = remember(team.id) { androidx.compose.runtime.mutableStateMapOf<Long, Pair<java.time.LocalTime?, java.time.LocalTime?>>().apply {
        work.forEach { s -> put(s.id, suggested[s.id]?.let { (a, b) -> (s.start ?: a) to (s.end ?: b) } ?: (s.start to s.end)) } } }
    val fromMine = if (settings.setupDone) work.mapNotNull { s -> mine.firstOrNull { it.id == s.id && it.start != null && it.end != null }?.let { s.id to (it.start to it.end) } }.toMap() else emptyMap()
    AlertDialog(onDismissRequest = onDismiss, title = { Text("근무 시간 정하기") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("팀원 링크의 알람과 오늘 출근 시각이 이 시간으로 나가요. " + (if (suggested.isNotEmpty()) "흔한 시간으로 채워 두었으니 다른 것만 고쳐요. " else "") +
                "쉬는 날은 시간이 없어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (fromMine.isNotEmpty()) TextButton(onClick = { times.putAll(fromMine) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 달력 근무 시간 가져오기") }
            work.forEach { s ->
                val (a, b) = times[s.id] ?: (null to null)
                Text("${s.label} · ${s.name}", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.bamnat.app.ui.components.TimeField("시작", a, { times[s.id] = it to times[s.id]?.second }, Modifier.weight(1f), empty = "정하기")
                    com.bamnat.app.ui.components.TimeField("끝", b, { times[s.id] = times[s.id]?.first to it }, Modifier.weight(1f), empty = "정하기",
                        start = a?.plusHours(8) ?: java.time.LocalTime.of(18, 0))
                }
            }
        }
    }, confirmButton = {
        TextButton(onClick = { onSave(com.bamnat.core.team.TeamTimes.set(team, times.toMap())) }) { Text("저장") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("나중에") } })
}
