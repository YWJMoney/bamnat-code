package com.bamnat.app.ui.work

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.WorkToolsStore
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.components.StatTile
import com.bamnat.app.ui.components.StatTiles
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.manage.ManageDesk
import com.bamnat.core.team.MonthState
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.tier.WorkPlanKind
import com.bamnat.core.time.Today
import java.time.YearMonth
import androidx.compose.ui.semantics.Role

/**
 * 1.38 관리 화면 (사용자 2026-11-05 "리더·사장님에게 보여지는 페이지는 일반 사용자 페이지와 달라야 해"): one place apart from the
 * personal calendar for everything a leader or a boss manages — 할 일 across every team and shop (most urgent first, [ManageDesk]),
 * each team's month, the shop's ledger, 명세서·직원, the plan in use, and where the records are kept. Every number is the same one the
 * board and the ledger show, read from the same store, so the two never disagree.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageScreen(calendarId: String, onBack: () -> Unit,
    onOpenBackup: () -> Unit = { com.bamnat.app.ui.settings.SettingsLinks.open(com.bamnat.app.ui.settings.SettingsPage.DATA) },
    /** Screenshot tests show both halves without the flags. */
    showAll: Boolean = false) {
    val context = LocalContext.current
    val c = context.appContainer
    val flagRows by c.flags.byKey.collectAsStateWithLifecycle()
    val leaderOn = showAll || flagRows[FlagCatalog.TEAM_LEADER]?.on == true
    val bossOn = showAll || flagRows[FlagCatalog.BOSS_PAYROLL]?.on == true
    androidx.compose.runtime.LaunchedEffect(Unit) { c.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_OPEN.key) }
    var teamOpen by rememberSaveable { mutableStateOf<String?>(null) }
    var teamsList by rememberSaveable { mutableStateOf(false) }
    // 1.39 [READ-31]: a staff list shared from 카카오톡·갤러리 opens the 급여 대장, which reads it.
    var ledgerOpen by rememberSaveable { mutableStateOf(com.bamnat.app.imports.SharedToManage.staffWaiting || com.bamnat.app.imports.SharedToManage.takeLedger()) }
    // 2.0.0 [RVJ-95] What the 급여 대장 opens on ([PayrollScreen]'s focus) and its month: a 할 일 pressed, or the 신고·지급 알림 ([RVJ-96]).
    var ledgerAt by rememberSaveable { mutableStateOf(com.bamnat.app.imports.SharedToManage.takeLedgerFocus()) }
    var ledgerMonth by rememberSaveable { mutableStateOf<String?>(null) }
    // 2.0.0 [RVJ-100] 검토 T05-05·U05c-09: which 가게's 대장 (a 가게 card, its 할 일 or its notice); null keeps the one last looked at.
    var ledgerPlace by rememberSaveable { mutableStateOf(com.bamnat.app.imports.SharedToManage.takeLedgerPlace()) }
    val ledgerAsked by com.bamnat.app.imports.SharedToManage.ledgerAsked.collectAsStateWithLifecycle()
    val staffShared by com.bamnat.app.imports.SharedToManage.staffShared.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(staffShared) { if (staffShared != null) { teamOpen = null; teamsList = false; ledgerOpen = true } }
    androidx.compose.runtime.LaunchedEffect(ledgerAsked) {
        if (ledgerAsked && com.bamnat.app.imports.SharedToManage.takeLedger()) {
            teamOpen = null; teamsList = false; ledgerAt = com.bamnat.app.imports.SharedToManage.takeLedgerFocus(); ledgerMonth = null
            ledgerPlace = com.bamnat.app.imports.SharedToManage.takeLedgerPlace(); ledgerOpen = true
        }
    }
    if (teamOpen != null || teamsList) {
        BackHandler { teamOpen = null; teamsList = false }
        com.bamnat.app.ui.team.LeaderTeamScreen(calendarId, onBack = { teamOpen = null; teamsList = false }, startTeam = teamOpen)
        return
    }
    val store = c.phaseThree
    val teams by store.teamList.collectAsStateWithLifecycle(null)
    val places by store.workplaces.collectAsStateWithLifecycle(null)
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val now = remember { YearMonth.from(Today.date()) }
    // Each card reports its glance up, so the 할 일 on top reads every team and shop at once.
    val teamGlances = remember { mutableStateMapOf<String, ManageDesk.TeamGlance>() }
    val shopGlances = remember { mutableStateMapOf<String, ManageDesk.ShopGlance>() }
    val liveTeams = teams.orEmpty().filter { !it.archived }
    val livePlaces = places.orEmpty()
    // 2.0.0 [RVO-19] 검토 T11a-15 · [RVH-78] 검토 T30-105: the same name as the 설정 row that opened it (팀 관리, 사업장 관리, 가게는 가게 관리).
    val manageStyle = c.calendars.styleOf(calendarId, c.settingsRepository.current.workStyle)
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(manageTitle(leaderOn, bossOn, manageStyle)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            // Its own color, so it never reads as the personal calendar.
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.secondaryContainer,
                titleContentColor = MaterialTheme.colorScheme.onSecondaryContainer),
        )
    }) { padding ->
        // 2.0.0 [RVJ-50] 검토 PC-118 (조건 12): on a 펼친 폴드 the words keep to the page's body width, in the middle.
        androidx.compose.foundation.layout.Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = com.bamnat.app.ui.components.PageWidth.Body).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("내 달력과 따로, 내가 맡은 ${manageWhat(leaderOn, bossOn, "과", manageStyle)} 일만 모았어요.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

            // 할 일: every team and shop, the urgent ones first.
            val todos = ManageDesk.todos(Today.date(),
                liveTeams.mapNotNull { teamGlances[it.id] }, livePlaces.mapNotNull { shopGlances[it.id] })
            SectionCard(title = "할 일" + if (todos.isNotEmpty()) " ${todos.size}" else "") {
                if (todos.isEmpty()) Hint(if (liveTeams.isEmpty() && livePlaces.isEmpty()) manageWhat(leaderOn, bossOn, "이나", manageStyle).let { "$it${com.bamnat.core.text.Josa.eulreul(it)} 만들면 확정·대장 닫기·명세서처럼 챙길 일을 여기서 모아 보여 드려요." }
                    else "지금 챙길 일이 없어요.")
                todos.forEach { t ->
                    // 2.0.0 [RVJ-50] 검토 PC-118 (조건 3·6): two or three to-dos with a date close stood in red; they are 주의 lines (the
                    // mark's colour says it), and only a team's 위험 (휴식·연속·주 52시간) is red.
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
                        if (t.where == ManageDesk.Where.TEAM) teamOpen = t.id else {
                            // 2.0.0 [RVJ-95] 검토 U05c-01: a 신고 기한, a 근로계약서 or a 대타 opens on its own card, not on the top of the 대장.
                            ledgerAt = when (t.open) { ManageDesk.Open.PLACE -> null; ManageDesk.Open.FILINGS -> t.open.name; else -> "${t.open.name}|${t.ref}" }
                            ledgerMonth = t.month?.toString()
                            ledgerPlace = t.id
                            ledgerOpen = true
                        }
                    }.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (t.urgent) com.bamnat.app.ui.components.ToneLine(t.text, Modifier.weight(1f),
                            tone = if (t.danger) com.bamnat.app.ui.theme.Tone.Danger else com.bamnat.app.ui.theme.Tone.Caution,
                            style = MaterialTheme.typography.bodyMedium)
                        else Text("· " + t.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("열기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            val settings by c.settingsRepository.settings.collectAsStateWithLifecycle()
            val parts = remember(settings.manageLayout) { com.bamnat.core.layout.PageLayout.decode(settings.manageLayout).arrange(ManagePart.editable) { it.id } }
            // A hidden card still counts its 할 일: nothing to do may be hidden by a layout.
            if (parts.none { it.part == ManagePart.TEAMS && it.shown } && leaderOn) liveTeams.forEach { t -> key(t.id) { TeamGlanceCard(t, calendarId, now, {}, show = false) { teamGlances[t.id] = it } } }
            if (parts.none { it.part == ManagePart.SHOPS && it.shown } && bossOn) livePlaces.forEach { p -> key(p.id) { ShopGlanceCard(p, now, {}, show = false) { shopGlances[p.id] = it } } }
            parts.filter { it.shown }.forEach { placed -> when (placed.part) {
            ManagePart.TEAMS -> if (leaderOn) {
                Text("팀 근무표", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                liveTeams.forEach { t -> key(t.id) { TeamGlanceCard(t, calendarId, now, onOpen = { teamOpen = t.id }) { teamGlances[t.id] = it } } }
                OutlinedButton(onClick = { teamsList = true }, modifier = Modifier.fillMaxWidth()) { Text(if (liveTeams.isEmpty()) "팀 만들기" else "팀 목록·새 팀") }
                if (liveTeams.isEmpty()) Text("지금 쓰는 근무표가 엑셀·PDF·종이 사진이어도 팀 만들기에서 그대로 가져와요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ManagePart.SHOPS -> if (bossOn) {
                Text(ManagePart.SHOPS.label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                livePlaces.forEach { p -> key(p.id) { ShopGlanceCard(p, now, onOpen = { ledgerAt = null; ledgerMonth = null; ledgerPlace = p.id; ledgerOpen = true }) { shopGlances[p.id] = it } } }
                if (livePlaces.isEmpty()) OutlinedButton(onClick = { ledgerAt = null; ledgerMonth = null; ledgerOpen = true }, modifier = Modifier.fillMaxWidth()) { Text("급여 대장 시작하기") }
                if (livePlaces.isEmpty()) Text("직원 명단이 엑셀·PDF·사진이어도 급여 대장에서 한 번에 가져와요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            // 요금제: the band in use, or the free taste, in one line each.
            ManagePart.PLAN -> SectionCard(title = "요금제") {
                if (!gate.live) Hint("지금은 모두 무료로 써요.")
                else {
                    if (leaderOn) PlanLine(gate.hold(WorkPlanKind.TEAM), WorkPlanKind.TEAM, "팀 하나·첫 달 확정까지 무료")
                    if (bossOn) PlanLine(gate.hold(WorkPlanKind.STAFF), WorkPlanKind.STAFF, "직원 ${gate.plans.freeStaff}명까지 무료")
                    Hint("팀원·직원은 늘 무료로 받아요. 요금제가 끝나도 만든 근무표·대장·명세서는 보고 내보낼 수 있어요.")
                }
            }
            // 데이터 지키기: where the records live and how they are kept.
            ManagePart.KEEP -> SectionCard(title = "기록 지키기", trailing = { TextButton(onClick = onOpenBackup) { Text("백업") } }) {
                Hint("팀 근무표·급여 대장·명세서 기록은 이 휴대폰에 있고, 백업 파일에 함께 들어가요. 닫은 달 대장은 그때 금액 그대로 남아요(근로기준법 제48조, 3년 보관).")
            }
            ManagePart.TODO -> Unit
            } }
            TextButton(onClick = { com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.STUDIO_MANAGE) }) { Text("카드 순서·색 바꾸기 (스튜디오)") }
        }
        }
    }
    if (ledgerOpen) {
        val roster = remember { WorkToolsStore.get(context).current(calendarId).roster }
        PayrollScreen(roster, ledgerMonth?.let(YearMonth::parse), ledgerAt, ledgerPlace) { ledgerOpen = false; ledgerAt = null; ledgerMonth = null; ledgerPlace = null }
    }
}

@Composable
private fun PlanLine(hold: com.bamnat.core.tier.WorkHold?, kind: WorkPlanKind, free: String) {
    Text(kind.title + ": " + when {
        hold == null -> "무료 맛보기 ($free)"
        hold.cap == Int.MAX_VALUE -> "모두 열림"
        else -> "${kind.who} ${hold.cap}명 구간" + if (hold.pass) " · 1년 이용권" else if (hold.trial) " · 무료 체험 중" else ""
    }, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun TeamGlanceCard(team: Team, calendarId: String, now: YearMonth, onOpen: () -> Unit, show: Boolean = true, report: (ManageDesk.TeamGlance) -> Unit) {
    val c = LocalContext.current.appContainer
    val store = c.phaseThree
    val board by store.board(team.id, now).collectAsStateWithLifecycle(null)
    val month by store.month(team.id, now).collectAsStateWithLifecycle(null)
    val nextBoard by store.board(team.id, now.plusMonths(1)).collectAsStateWithLifecycle(null)
    val nextMonth by store.month(team.id, now.plusMonths(1)).collectAsStateWithLifecycle(null)
    // 2.0.0 [RVJ-30][RVJ-31] The same count the board shows: weeks read over the month's edges, 주 52시간 not for a 5인 미만 사업장.
    val prevBoard by store.board(team.id, now.minusMonths(1)).collectAsStateWithLifecycle(null)
    val rules = com.bamnat.app.ui.team.teamRules(team, calendarId)
    val b = board ?: return
    val signs = remember(b, team, rules, prevBoard, nextBoard) {
        TeamCheck.check(team, b, rules, prevBoard?.takeIf { it.cells.isNotEmpty() }, nextBoard?.takeIf { it.cells.isNotEmpty() })
    }
    val danger = signs.count { it.kind == TeamCheck.Kind.RULE || it.kind == TeamCheck.Kind.WEEK_HOURS }
    val law = signs.count { it.law } // 2.0.0 [RVJ-68] (HQ-107) 주 52시간, the law's; the rest are the team's 기준
    val short = signs.filter { it.kind == TeamCheck.Kind.SHORT }.map { it.date }.distinct().size
    val m = month?.first
    val nm = nextMonth?.first
    val members = b.members.count { it.active }
    val g = ManageDesk.TeamGlance(team.id, team.name, members, m?.state == MonthState.CONFIRMED, m?.version ?: 0, b.empty, danger, short,
        nm?.state == MonthState.CONFIRMED, nextBoard?.empty ?: 0, painted = b.cells.size, weekLimit = rules.weekLimit, law = law)
    androidx.compose.runtime.LaunchedEffect(g) { report(g) }
    if (!show) return
    val today = remember(b, team, Today.date()) { com.bamnat.core.team.TeamToday.of(team, b, Today.date()) }
    // 2.0.0 [RVJ-73] (PC-176) A month with no cell painted (a team made for next month) is '아직 안 짠 달', said quietly: no 오늘 출근 0명 ·
    // 빈자리, no 빈칸 150, no 인원 모자란 날 30. They come from the month it is painted.
    val unpainted = !g.confirmed && g.painted == 0
    SectionCard(title = team.name, trailing = { TextButton(onClick = onOpen) { Text("열기") } }) {
        // 1.39 [FLOW-23] one number first: who is at work today and how many places are empty.
        if (today != null && members > 0 && !unpainted) com.bamnat.app.ui.team.TeamTodayHead(today, Modifier.padding(bottom = 8.dp))
        StatTiles {
            StatTile("${now.monthValue}월", if (g.confirmed) "확정 ${g.version}판" else if (unpainted) "아직 안 짬" else "짜는 중", Modifier.weight(1f),
                // 2.0.0 [RVJ-66] (HQ-103, 조건 6) 짜는 중인 달은 할 일이지 위험이 아니에요(가게 칸 RVJ-50과 같이): 빨간 칸 대신 주의 글.
                caption = if (unpainted) null else if (g.empty > 0) "빈칸 ${g.empty}" else "다 채움",
                captionTone = if (!g.confirmed && members > 0 && g.painted > 0) com.bamnat.app.ui.theme.Tone.Caution else com.bamnat.app.ui.theme.Tone.Plain)
            StatTile("${now.plusMonths(1).monthValue}월", if (g.nextConfirmed) "확정 ${nm?.version ?: 0}판" else "짜는 중", Modifier.weight(1f),
                caption = if (g.nextEmpty > 0) "빈칸 ${g.nextEmpty}" else "다 채움")
        }
        Text((if (unpainted) listOf("${members}명") else listOfNotNull("${members}명", law.takeIf { it > 0 }?.let { "주 52시간 넘김 $it" },
            "기준 넘김 ${danger - law}", "인원 모자란 날 $short")).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall,
            // [RVJ-66] 위험은 위 할 일 줄 하나가 빨강이에요; 여기는 같은 수를 말로만.
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun ShopGlanceCard(place: com.bamnat.core.boss.Workplace, now: YearMonth, onOpen: () -> Unit, show: Boolean = true, report: (ManageDesk.ShopGlance) -> Unit) {
    val store = LocalContext.current.appContainer.phaseThree
    val last = now.minusMonths(1)
    val staff by store.staff(place.id).collectAsStateWithLifecycle(emptyList())
    // 2.0.0 [RVG-01·02]: the same month as the 급여 대장 shows (a closed month as it was closed, the crossing weeks whole).
    val inputs by remember(place, last) { store.inputs(place, last) }.collectAsStateWithLifecycle(null)
    val closed by store.payrollMonth(place.id, last).collectAsStateWithLifecycle(null)
    val marks by store.lines(place.id, last).collectAsStateWithLifecycle(emptyList())
    val ledger = inputs?.takeIf { it.month == last }?.ledger ?: PayrollLedger.month(place, emptyList(), last, emptyList())
    val isClosed = closed?.state == "CLOSED"
    val counted = if (isClosed) marks.size else ledger.lines.size
    // 2.0.0 [RVJ-95] 검토 U05c-01·I05ca-03: what else waits on the boss, said in the 할 일 on top.
    val context = LocalContext.current
    val today = Today.date()
    val filedTick by Filed.changes.collectAsStateWithLifecycle()
    // The 신고·납부 of the coming week as the 급여 대장's 다가올 신고·지급 reads them (the same months, the same 했어요); the 지급일
    // itself is the 닫기·지급 lines'.
    val dues by androidx.compose.runtime.produceState(emptyList<ManageDesk.DueSoon>(), place, staff, filedTick, today) {
        value = if (staff.isEmpty()) emptyList() else runCatching {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                val first = YearMonth.from(today).minusMonths(3)
                val ledgers = (0..4).associate { i -> first.plusMonths(i.toLong()).let { m -> m to store.ledgerOf(place, m) } }
                val done = Filed.all(context)
                com.bamnat.core.boss.BossFilings.upcoming(place, staff, today) { m -> ledgers[m] ?: PayrollLedger.month(place, staff, m, emptyList()) }
                    .filter { it.kind != com.bamnat.core.boss.BossFilings.Kind.PAY && it.key !in done }.map { ManageDesk.DueSoon(it.key, it.date, it.title) }
            }
        }.getOrDefault(emptyList())
    }
    val names = staff.associate { it.id to it.name.ifBlank { "직원" } }
    // A 근로계약서 sent on its link and not signed (one marked 종이로 받았어요 is signed), for someone still working.
    val unsigned = staff.filter { it.leftOn?.isBefore(today) != true }.mapNotNull { s ->
        s.contract?.takeIf { !it.signed && it.linkId != null }?.let { k ->
            ManageDesk.Unsigned(s.id, names.getValue(s.id), java.time.Instant.ofEpochMilli(k.sentAt).atZone(Today.zone()).toLocalDate(),
                k.expiresAt?.let { Today.millis() >= it } == true)
        }
    }
    // A 대타 ask someone said yes to, not settled yet; the names in the order they were asked.
    val coverYes = place.calls.filter { k -> k.taken == null && k.answers.any { it.value } }.map { k ->
        ManageDesk.CoverYes(k.id, k.gap.date, names[k.gap.memberId] ?: "직원", k.people.filter { k.answers[it] == true }.map { names[it] ?: "직원" })
    }
    val g = ManageDesk.ShopGlance(place.id, place.name, staff.size, place.fivePlus != null, isClosed, counted,
        marks.count { it.paidAt != null }, marks.count { it.slipSentAt != null }, place.payDate(last), ledger.lines.sumOf { it.warnings.size },
        dues = dues, unsigned = unsigned, coverYes = coverYes)
    androidx.compose.runtime.LaunchedEffect(g) { report(g) }
    if (!show) return
    SectionCard(title = place.name, trailing = { TextButton(onClick = onOpen) { Text("급여 대장") } }) {
        StatTiles {
            // 2.0.0 [RVJ-50] 검토 PC-118: a month not closed yet is a to-do (할 일 says it with the 지급일), not a danger: the tile
            // was red. It is a plain tile whose caption says '닫기 전' (조건 6, 빨강은 위험만).
            StatTile("${last.monthValue}월 인건비", Fmt.won(ledger.total), Modifier.weight(1f), caption = "${staff.size}명 · " + if (isClosed) "닫음" else "닫기 전")
            StatTile("지급·명세서", if (isClosed) "${g.lastPaid}/${g.lastLines} · ${g.lastSent}/${g.lastLines}" else "-", Modifier.weight(1f),
                caption = place.payDate(last)?.let { "지급일 ${Fmt.monthDay(it)}" } ?: "지급일 미정")
        }
        // [RVJ-50] The one thing to look at here, as the 주의 line every card uses (it was red text).
        if (place.fivePlus == null) com.bamnat.app.ui.components.ToneLine("5인 이상인지 아직 안 골랐어요", Modifier.padding(top = 6.dp), onClick = onOpen, action = "고르기")
    }
}

/**
 * 2.0.0 [RVH-78] 검토 T30-105: the 관리 화면 was '팀·가게 관리' to a 현장·사무실 사장님 and to a leader whose team is no shop.
 * Its name says what this phone leads: 팀, 사업장 (the word the 사장님's own screens use), or both.
 */
const val MANAGE_NAME = "팀·사업장 관리"

/** 2.0.0 [RVO-19] 검토 T11a-15: what a 사장님 leads, in the calendar's words: a shop's 가게 (as its settings say '내 가게'), else 사업장. */
private fun managePlace(style: com.bamnat.core.work.WorkStyle?): String = if (style == com.bamnat.core.work.WorkStyle.BUSINESS) "가게" else "사업장"

/** The name of the 관리 화면 and of the 설정 row that opens it (one function for both, 총괄 합치기: [RVH-78] + [RVO-19]). */
internal fun manageTitle(leader: Boolean, boss: Boolean, style: com.bamnat.core.work.WorkStyle? = null): String = when {
    leader && !boss -> "팀 관리"
    boss && !leader -> "${managePlace(style)} 관리"
    else -> "팀·${managePlace(style)} 관리"
}

/** "팀과 사업장", "팀", "사업장" (a shop's "가게") with [and] ("과", "이나") between the two. */
internal fun manageWhat(leader: Boolean, boss: Boolean, and: String, style: com.bamnat.core.work.WorkStyle? = null): String = when {
    leader && !boss -> "팀"
    boss && !leader -> managePlace(style)
    else -> "팀${and} ${managePlace(style)}"
}

/** 1.38 The cards of the 관리 화면, in their own order (스튜디오 › 팀·가게 관리 › 카드 배치). 할 일 is always first and always shown. */
enum class ManagePart(val id: String, val label: String) {
    TODO("todo", "할 일"), TEAMS("teams", "팀 근무표"), SHOPS("shops", "직원 급여 대장"), PLAN("plan", "요금제"), KEEP("keep", "기록 지키기");

    companion object {
        val editable: List<ManagePart> = entries.filter { it != TODO }
    }
}

/** Whether this phone has the 관리 화면 (either half of Phase 3 on). */
@Composable
fun rememberManageOn(): Boolean {
    val rows by LocalContext.current.appContainer.flags.byKey.collectAsStateWithLifecycle()
    return rows[FlagCatalog.TEAM_LEADER]?.on == true || rows[FlagCatalog.BOSS_PAYROLL]?.on == true
}

/** The 관리 화면 in the studio's 자세히: its cards in the draft's order, made-up numbers, a tap opens 카드 배치. */
@Composable
fun ManageSample(layout: String, onTap: () -> Unit) {
    val parts = remember(layout) { com.bamnat.core.layout.PageLayout.decode(layout).arrange(ManagePart.editable) { it.id } }
    Column(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onTap).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(MANAGE_NAME, style = MaterialTheme.typography.titleMedium)
        SectionCard(title = "할 일 2") {
            com.bamnat.app.ui.components.ToneLine("7병동 12월 근무표 짜기 · 빈칸 40")
            Text("· 우리 사업장 11월: 지급 표시 1명 남았어요", style = MaterialTheme.typography.bodySmall)
        }
        parts.filter { it.shown }.forEach { p ->
            SectionCard(title = p.part.label) {
                Text(when (p.part) {
                    ManagePart.TEAMS -> "7병동 · 11월 확정 2판 · 12월 짜는 중"
                    ManagePart.SHOPS -> "우리 사업장 · 10월 인건비 · 지급 3/3"
                    ManagePart.PLAN -> "팀 요금제: 팀원 15명 구간"
                    ManagePart.KEEP -> "기록은 이 휴대폰과 백업 파일에"
                    ManagePart.TODO -> ""
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val hidden = parts.filterNot { it.shown }
        if (hidden.isNotEmpty()) Text("숨김: " + hidden.joinToString(", ") { it.part.label }, style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
