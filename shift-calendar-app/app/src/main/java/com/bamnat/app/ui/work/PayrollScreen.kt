package com.bamnat.app.ui.work

import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.WarningAmber
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.components.StatTile
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.app.ui.money.AmountField
import com.bamnat.app.ui.money.amountDigits
import com.bamnat.app.ui.money.toAmount
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.LedgerCsv
import com.bamnat.core.boss.LedgerLine
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.StaffWork
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.WorkKind
import com.bamnat.core.boss.Workplace
import com.bamnat.core.pay.RateTable
import com.bamnat.core.time.Today
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import androidx.compose.ui.text.font.FontWeight
import java.util.UUID

/**
 * 1.38 [P3-03][BOSS-01..07] 사장님 급여 대장 (순번 501·502, 설계 2-2), behind `boss.payroll`. The 직원 인건비 card's roster comes in
 * once as a 사업장 ([com.bamnat.app.data.PhaseThreeStore.adoptRoster]); then each staff member's month is worked out by the user's own pay engine from the
 * usual week and the days that went differently, with the 근거 of every line, ⚠ signs that explain themselves, 대장 닫기 and
 * the 임금대장 CSV (시행령 제27조 items). 참고용 계산이에요 stays on top.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PayrollScreen(roster: StaffRoster, startMonth: YearMonth, onClose: () -> Unit) {
    val context = LocalContext.current
    val store = context.appContainer.phaseThree
    val scope = rememberCoroutineScope()
    val places by store.workplaces.collectAsStateWithLifecycle(null)
    // [BOSS-06] The SHOP-11 roster becomes the first 사업장 the first time; with no roster a blank one is made.
    LaunchedEffect(places) {
        if (places?.isEmpty() == true) {
            val id = UUID.randomUUID().toString()
            if (store.adoptRoster(roster, id) == null) store.saveWorkplace(Workplace(id, "우리 가게", legalBreaks = roster.legalBreaks))
        }
    }
    val place = places?.firstOrNull()
    var month by rememberSaveable { mutableStateOf(startMonth.toString()) }
    val ym = YearMonth.parse(month)
    var editing by remember { mutableStateOf<StaffMember?>(null) }
    var settings by remember { mutableStateOf(false) }
    var rehireAsk by remember { mutableStateOf<com.bamnat.core.tier.WorkPlanRules.Need?>(null) }
    var finding by remember { mutableStateOf<Pair<String, LocalDate>?>(null) }
    var contracting by remember { mutableStateOf<String?>(null) }
    ToolScreen("급여 대장", onClose) {
        if (place == null) { Hint("사업장을 준비하고 있어요…"); return@ToolScreen }
        val staff by store.staff(place.id).collectAsStateWithLifecycle(emptyList())
        val work by store.work(place.id, ym).collectAsStateWithLifecycle(emptyList())
        val closed by store.payrollMonth(place.id, ym).collectAsStateWithLifecycle(null)
        val ledger = remember(place, staff, work, ym) { PayrollLedger.month(place, staff, ym, work) }
        val isClosed = closed?.state == "CLOSED"
        val lastWork by store.work(place.id, ym.minusMonths(1)).collectAsStateWithLifecycle(emptyList())
        val lastClosed by store.payrollMonth(place.id, ym.minusMonths(1)).collectAsStateWithLifecycle(null)
        // 1.39 G2 (PC f7f61818 '처음 넣은 직원인데 지난달 같아요'): last month counts only someone known to have worked then: an 입사일
        // before this month, days written last month, or last month closed. Without any of those it was only the usual week guessed.
        // PC 4bd5d745: the head, the bars and the '지난달과 비교' card all use these same people.
        val lastStaff = remember(staff, lastWork, lastClosed, ym) {
            val was = lastClosed?.state == "CLOSED"
            staff.filter { s -> was || s.hiredOn?.isBefore(ym.atDay(1)) == true || lastWork.any { it.staffId == s.id } }
        }
        val lastLedger = remember(place, lastStaff, lastWork, ym) { PayrollLedger.month(place, lastStaff, ym.minusMonths(1), lastWork) }

        Text(PayrollLedger.NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The month: ◀ 2026년 11월 ▶
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "지난달") }
            Text(Fmt.month(ym) + if (isClosed) " · 닫힘" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
        }
        // 1.39 G2 (PC f7f61818 '오늘이 11월인데 10월로 열려요'): the month worked, paid the month after; said once above the card.
        val thisMonth = YearMonth.from(Today.date())
        Hint(when {
            ym == thisMonth.minusMonths(1) -> "${ym.monthValue}월에 일한 몫을 정산하는 대장이에요" + (place.payDate(ym)?.let { " · 지급일 ${Fmt.monthDay(it)}" } ?: "") +
                ". 이번 달(${thisMonth.monthValue}월)은 ▶로 넘겨 미리 봐요."
            ym == thisMonth -> "${ym.monthValue}월에 일하는 중인 몫이에요. 평소 근무와 바꾼 날로 한 달을 미리 계산해요."
            else -> "${ym.monthValue}월에 일한 몫이에요."
        })
        SectionCard(title = place.name, trailing = { TextButton(onClick = { settings = true }) { Text("사업장 설정") } }) {
            ledger.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (place.fivePlus == null) LocalCalendarColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant) }
            // PC 1차 제안: not answered yet → ask right here in one tap, not behind 사업장 설정. The safe side (가산 넣기) stays until then.
            if (place.fivePlus == null) {
                Text("사장님 빼고 일하는 사람(가족 아닌 직원·알바 모두)이 평소 몇 명인가요? 지금 대장엔 ${staff.size}명이 있어요.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { scope.launch { store.updateWorkplace(place.id) { it.copy(fivePlus = true) } } }, label = { Text("5명 이상") })
                    FilterChip(selected = false, onClick = { scope.launch { store.updateWorkplace(place.id) { it.copy(fivePlus = false) } } }, label = { Text("5명 미만") })
                }
            }
            // 1.39 [FLOW-24] one number first: the month's 인건비, then each person's bar against last month.
            Text("${ym.monthValue}월 인건비", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp))
            Text(Fmt.won(ledger.total), style = MaterialTheme.typography.headlineSmall)
            // PC 4bd5d745: '+504,062원' set 민수's last month against 민수 and 지영 this month. Only the same people are set side by side.
            lastHead(ledger, lastLedger)?.let { Hint(it) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                StatTile("급여 합계", Fmt.won(ledger.gross), Modifier.weight(1f), caption = "${ledger.lines.size}명")
                // 1.39 [BOSS-12] 산재보험 (사업주만) with the 4대보험 once the 업종 is picked.
                StatTile(if (place.industry != null) "사장님 4대보험·산재" else "사장님 4대보험", Fmt.won(ledger.employerInsurance + ledger.accident), Modifier.weight(1f),
                    caption = if (ledger.accident > 0) "산재 등 ${Fmt.won(ledger.accident)} 포함 · 참고" else "참고")
            }
            // 1.39 G2 (PC f7f61818, 상): someone the law puts in the 4대보험 with nothing ticked makes the 사장님 몫 0원.
            val unticked = ledger.lines.map { it.staff }.filter { com.bamnat.core.boss.InsuranceRules.unticked(it, place.legalBreaks) }
            if (unticked.isNotEmpty() && !isClosed) Text("4대보험을 고르지 않았어요: " + unticked.joinToString(", ") { it.name } + " · 고르면 사장님 몫까지 맞게 나와요",
                style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning,
                modifier = Modifier.clickable(role = Role.Button) { editing = unticked.first() }.padding(vertical = 4.dp))
            if (place.industry == null && ledger.lines.isNotEmpty()) Text("업종을 고르면 산재보험료(사장님만 내요)도 더해요 · 사업장 설정에서 한 번",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button) { settings = true }.padding(vertical = 4.dp))
            if (ledger.lines.size >= 2 || lastLedger.lines.isNotEmpty() && ledger.lines.isNotEmpty())
                StaffCostBars(ledger.lines, lastLedger.lines.associate { it.staff.id to it.employerTotal }, Modifier.padding(vertical = 6.dp))
            // 1.38 [P3-X-07] 인건비율: the 가계부's 매출 for the month next to it.
            val book by context.appContainer.moneyRepository.book.collectAsStateWithLifecycle(null)
            val ratio = remember(book, ledger, lastLedger) {
                book?.let { b ->
                    val last = lastLedger.total.takeIf { it > 0 }
                    com.bamnat.core.boss.LaborRatio.of(ym, ledger.total, b.sales, Today.date(), last)
                }
            }
            ratio?.let { r ->
                val label = com.bamnat.core.boss.LaborRatio::label
                val pct = r.percent
                if (pct != null) {
                    ValueLine("매출 대비 인건비", label(pct) + " (매출 ${Fmt.won(r.sales)}, ${r.salesDays}일)", strong = true)
                    r.pacePercent?.let { Hint("지금까지 매출 흐름이면 이달 매출 약 ${Fmt.won(r.paceSales!!)}, 인건비율 약 ${label(it)}") }
                    r.lastPercent?.let { Hint("지난달 ${label(it)}") }
                } else if (ledger.total > 0) Hint("가계부에 하루 매출을 적으면 매출 대비 인건비가 여기에 보여요.")
            }
            place.payDate(ym)?.let { Hint("지급일 ${Fmt.monthDay(it)}") }
        }
        if (staff.isEmpty()) Hint("직원을 더하면 평소 근무 요일·시간으로 한 달 급여를 계산해요. 지각·결근·대근이 있던 날만 고치면 돼요.")
        val marks by store.lines(place.id, ym).collectAsStateWithLifecycle(emptyList())
        val marked = marks.associateBy { it.staffId }
        // 1.38 [P3-B-11] 출퇴근 기록: the days the presses say went differently, put in only when the boss ticks them.
        val punches by store.punches(place.id, ym).collectAsStateWithLifecycle(emptyList())
        LaunchedEffect(place.id) { runCatching { com.bamnat.app.boss.StaffClockJob.check(context.appContainer) } }
        if (!isClosed) ClockCard(staff, punches, work, place) { rows -> scope.launch { rows.forEach { store.setWork(it.staffId, it.date, it) } } }
        // 1.38 [P3-X-01] 대타 요청 of this month, above the lines they change.
        val calls = place.calls.filter { YearMonth.from(it.gap.date) == ym }
        LaunchedEffect(calls.filter { it.taken == null }.map { it.id }) {
            calls.filter { it.taken == null && it.token != null }.forEach { k -> runCatching { com.bamnat.app.boss.BossCallSync.pull(context.appContainer, place.id, k.id) } }
        }
        calls.forEach { k -> key(k.id) { BossCallCard(place, k, staff, work) } }
        // 1.38 [P3-X-08] 다가올 신고·지급
        if (staff.isNotEmpty()) FilingsCard(place, staff, work)
        val leaves by store.leaveDays(place.id).collectAsStateWithLifecycle(emptyMap())
        // 1.38 [P3-X-06] 계약서 서명 기다림: read once when the ledger opens.
        // PC 10차: keyed on who is waiting (the first frame's list is empty, so a key on the place alone read nobody).
        val waitingIds = staff.filter { it.contract?.waiting(Today.millis()) == true }.map { it.id }
        LaunchedEffect(place.id, waitingIds) { if (waitingIds.isNotEmpty()) runCatching { com.bamnat.app.boss.BossContractSync.pullWaiting(context.appContainer, staff) } }
        ledger.lines.forEach { l -> key(l.staff.id) { LedgerLineCard(l, place, ym, work, isClosed, marked[l.staff.id], onEdit = { editing = l.staff }, onFind = { d -> finding = l.staff.id to d },
            onContract = { contracting = l.staff.id },
            leaveLeft = com.bamnat.core.leave.LeaveLeft.forStaff(place.fivePlus, l.staff.hiredOn, leaves[l.staff.id].orEmpty(), minOf(Today.date(), ym.atEndOfMonth()))) } }
        finding?.let { (id, d) -> BossCoverDialog(place, staff, work, id, d, onDismiss = { finding = null }) }
        contracting?.let { id -> staff.firstOrNull { it.id == id }?.let { s -> key(id) { ContractDialog(place, s, onEditStaff = { contracting = null; editing = s }) { contracting = null } } } }
        // 1.38 퇴사자: 이 달에 없는 사람도 기록은 3년 남아요(근로기준법 제42조). 눌러서 보거나 다시 입사시켜요.
        val gone = staff.filter { s -> ledger.lines.none { it.staff.id == s.id } }
        if (gone.isNotEmpty()) {
            var showGone by rememberSaveable { mutableStateOf(false) }
            TextButton(onClick = { showGone = !showGone }) { Text((if (showGone) "접기 · " else "") + "이 달에 없는 직원 ${gone.size}명 (퇴사·입사 전)") }
            if (showGone) gone.forEach { g ->
                TextButton(onClick = { editing = g }) {
                    Text(g.name + (g.leftOn?.let { " · ${Fmt.shortFullDate(it)} 퇴사" } ?: g.hiredOn?.let { " · ${Fmt.shortFullDate(it)} 입사" } ?: ""),
                        style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        // [BOSS-10] 지난달 비교: why a line moved, before a staff member asks.
        // PC 4bd5d745: someone with no known last month (no 입사일, nothing written, not closed) has nothing to set against.
        val compare = remember(ledger, lastLedger) {
            com.bamnat.core.boss.PayrollCompare.rows(ledger, lastLedger).filter { it.diff != null }
        }
        if (compare.any { it.diff != 0L }) SectionCard(title = "지난달과 비교") {
            compare.forEach { r ->
                ValueLine(r.staff.name.ifBlank { "이름 없음" }, r.diff?.let { (if (it >= 0) "+" else "") + Fmt.won(it) } ?: "-")
                Hint(r.why)
            }
        }
        // 1.38 Phase 3 요금: 3 staff are the free taste; from the fourth the 직원 요금제 band that holds them. Staff already in always stay
        // and their pay is worked out and exported as before, plan or not.
        val gate by context.appContainer.featureGate.state.collectAsStateWithLifecycle()
        // 1.38 결정: 이번 달 일하는 사람만 세요. 퇴사·휴직한 사람의 기록은 그대로 두고 인원에서만 빠져요.
        val counted = staff.count { it.countsFor(java.time.YearMonth.from(Today.date())) }
        val next = gate.staff(counted + 1)
        var ask by remember { mutableStateOf<com.bamnat.core.tier.WorkPlanRules.Need?>(null) }
        OutlinedButton(onClick = {
            if (!next.allowed) { ask = next; return@OutlinedButton }
            editing = StaffMember(UUID.randomUUID().toString(), "", base = RateTable.on(ym.atDay(1)).minimumHourlyWage)
        }, enabled = !isClosed) { Text("직원 더하기") }
        // 1.39 [SPREAD-07] After the first staff member is in: one card, once, to tell another 사장님.
        val hasStaff = staff.isNotEmpty()
        LaunchedEffect(hasStaff) { if (hasStaff) context.appContainer.grow.peerDue(com.bamnat.core.guide.Peer.BOSS) }
        if (hasStaff) com.bamnat.app.ui.pro.PeerInviteCard(com.bamnat.core.guide.Peer.BOSS)
        // 1.38 가져오기: a staff list in any file or photo (엑셀·CSV·PDF·사진), checked on one page before anyone is added.
        var reading by remember { mutableStateOf<String?>(null) }
        var importing by remember { mutableStateOf<Pair<List<com.bamnat.core.boss.StaffImport.Line>, Boolean>?>(null) }
        fun readList(uri: android.net.Uri) {
            scope.launch {
                reading = "명단을 읽고 있어요…"
                when (val got = com.bamnat.app.imports.ManageImport.staff(context, uri, Today.date())) {
                    is com.bamnat.app.imports.ManageImport.Got.Read -> {
                        reading = null
                        importing = com.bamnat.core.boss.StaffImport.lines(got.value.rows, staff, ym.atDay(1)) to got.fromPhoto
                    }
                    is com.bamnat.app.imports.ManageImport.Got.Failed -> {
                        reading = got.why
                        context.appContainer.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_ERROR.key, "import")
                    }
                }
            }
        }
        val pickList = com.bamnat.app.ui.team.rememberAnyFilePicker { uri -> readList(uri) }
        // 1.39 [READ-31]: a staff list shared from 카카오톡·갤러리 is read here at once, as one picked with the button.
        val sharedList by com.bamnat.app.imports.SharedToManage.staffShared.collectAsStateWithLifecycle()
        LaunchedEffect(place.id, isClosed, sharedList) {
            if (!isClosed) com.bamnat.app.imports.SharedToManage.takeStaff()?.let(::readList)
            else if (sharedList != null) reading = "이 달은 닫혀 있어요. 열린 달로 옮기면 받은 명단을 바로 읽어요."
        }
        OutlinedButton(onClick = pickList, enabled = !isClosed) { Text("명단 파일·사진에서 가져오기") }
        reading?.let { Hint(it) }
        importing?.let { (lines, photo) ->
            // How many more fit now: the free 3, or the band held.
            val room = (1..200).takeWhile { gate.staff(counted + it).allowed }.size
            StaffImportDialog(lines, photo, room, onDismiss = { importing = null }) { add, over ->
                importing = null
                scope.launch {
                    add.forEach { store.saveStaff(it.member, place.id) }
                    context.appContainer.telemetry.event(com.bamnat.core.stats.UsageEvent.MANAGE_IMPORT.key)
                    reading = "${add.size}명을 더했어요." + if (add.any { it.check.isNotEmpty() }) " 노란 ‘확인’이 있던 사람은 이름을 눌러 한 번 봐 주세요." else ""
                    if (over) ask = gate.staff(counted + add.size + 1)
                }
            }
        }
        val hold = gate.hold(com.bamnat.core.tier.WorkPlanKind.STAFF)
        if (gate.live) Hint(when {
            hold != null && hold.cap < Int.MAX_VALUE -> "직원 요금제 ${hold.cap}명 구간 · 지금 ${counted}명"
            hold != null -> "직원 ${counted}명"
            else -> "직원 ${gate.plans.freeStaff}명까지 무료예요 · 지금 ${counted}명"
        } + if (staff.size > counted) " (퇴사·휴직 ${staff.size - counted}명은 세지 않아요)" else "")
        ask?.let { com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.STAFF, it, onDismiss = { ask = null }) }

        if (ledger.lines.isNotEmpty()) SectionCard(title = "이번 달 마무리") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    scope.launch {
                        runCatching { com.bamnat.app.report.ReportShare.csv(context, LedgerCsv.write(ledger), LedgerCsv.fileName(place, ym), "${place.name} ${Fmt.month(ym)} 임금대장") }
                    }
                }) { Text("임금대장 파일(CSV)") }
                if (isClosed) OutlinedButton(onClick = { scope.launch { store.reopen(place.id, ym, RateTable.on(ym.atDay(1)).version) } }) { Text("다시 열기") }
                else OutlinedButton(onClick = { scope.launch { store.close(place.id, ym, ledger.lines, RateTable.on(ym.atDay(1)).version); context.appContainer.telemetry.event(com.bamnat.core.stats.UsageEvent.LEDGER_CLOSED.key) } }) { Text("이 달 닫기") }
            }
            Hint(if (isClosed) "닫은 달은 근무를 고칠 수 없어요. 고칠 게 있으면 다시 열어 주세요." else "닫으면 이 달 계산을 그대로 남겨 둬요. 나중에 규칙이 바뀌어도 닫은 달은 그대로예요.")
            Hint(LedgerCsv.KEEP)
        }
    }
    editing?.let { s ->
        StaffDialog(s, place, onDismiss = { editing = null }, onSave = { n ->
            place?.let { p -> scope.launch { store.saveStaff(n, p.id) } }; editing = null
        }, onRemove = { place?.let { scope.launch { store.deleteStaff(s.id) } }; editing = null }, onRehire = { day ->
            // 1.38 다시 입사: a new 재직 기간 as its own line, so the old one's 임금대장·퇴직 정산 stay as they were.
            // 1.38 PC 8차: never on or before the 퇴사일 (두 재직 기간이 겹치던 것); the picker stops it too.
            if (s.leftOn?.let { !day.isAfter(it) } == true) return@StaffDialog
            editing = null
            place?.let { p -> scope.launch {
                val count = store.staff(p.id).first().count { it.countsFor(java.time.YearMonth.from(Today.date())) }
                val need = context.appContainer.featureGate.state.value.staff(count + 1)
                if (!need.allowed) rehireAsk = need
                else store.saveStaff(s.copy(id = UUID.randomUUID().toString(), hiredOn = day, leftOn = null, leaves = emptyList()), p.id)
            } }
        })
    }
    rehireAsk?.let { com.bamnat.app.ui.pro.WorkPlanSheet(com.bamnat.core.tier.WorkPlanKind.STAFF, it, onDismiss = { rehireAsk = null }) }
    if (settings && place != null) PlaceDialog(place, onDismiss = { settings = false }) { p -> scope.launch { store.updateWorkplace(p.id) { now -> p.copy(calls = now.calls) } }; settings = false }
}

/** One staff member's month: the total, ⚠ signs, and a tap for each line's 근거 and the days that went differently. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerLineCard(l: LedgerLine, place: Workplace, month: YearMonth, work: List<StaffWork>, closed: Boolean,
                           mark: com.bamnat.app.data.db.PayrollLineEntity?, onEdit: () -> Unit, onFind: (LocalDate) -> Unit = {}, onContract: () -> Unit = {},
                           leaveLeft: com.bamnat.core.leave.LeaveLeft.Left? = null) {
    var open by rememberSaveable(l.staff.id) { mutableStateOf(false) }
    var slip by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val warn = LocalCalendarColors.current.warning
    SectionCard(title = l.staff.name.ifBlank { "이름 없음" }, trailing = { TextButton(onClick = onEdit, enabled = !closed) { Text("직원 정보") } }) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.won(l.gross), style = MaterialTheme.typography.titleMedium)
                // 1.38 PC 8차: a 월급 직원 with no usual week has no days to count; '0일 · 0시간' read as nothing paid.
                val counted = if (l.workDays > 0 || l.workMinutes > 0) "${l.workDays}일 · ${com.bamnat.core.hours.HoursFormat.korean(l.workMinutes)}" else null
                val insured = if (l.employerInsurance > 0) "4대보험 ${Fmt.won(l.employerInsurance)}" else null
                if (counted != null || insured != null) Text(listOfNotNull(counted, insured).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (open) "접기" else "근거 보기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        // [BOSS-09][BOSS-10] 명세서 and 지급 체크, right on the line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { slip = true }, enabled = l.gross > 0) { Text(if (mark?.slipSentAt != null) "명세서 ✓" else "명세서") }
            TextButton(onClick = onContract) { Text(contractLabel(l.staff)) }
            val store = LocalContext.current.appContainer.phaseThree
            val scope = rememberCoroutineScope()
            FilterChip(selected = mark?.paidAt != null, onClick = { scope.launch { store.mark(place.id, month, l, paid = mark?.paidAt == null) } },
                label = { Text(if (mark?.paidAt != null) "지급했어요 ✓" else "지급했어요") })
        }
        // 1.38 PC 12차 (6): saved with no usual week and nothing written for the month, the line read '0원' with no why.
        if (l.gross == 0L && l.staff.weekly.isEmpty() && !closed)
            Text("일하는 요일이 없어서 0원이에요 · '직원 정보'에서 평소 요일을 고르거나, 근거 보기에서 일한 날을 하루씩 넣으면 계산돼요",
                style = MaterialTheme.typography.bodySmall, color = warn, modifier = Modifier.clickable(role = Role.Button, onClick = onEdit))
        // 1.38 [P3-X-06] 계약서 없음: 근로계약서는 써서 한 부 줘야 해요(근로기준법 제17조, 어기면 제114조 500만 원 이하 벌금).
        if (com.bamnat.core.boss.StaffContract.lacking(l.staff, minOf(Today.date(), month.atEndOfMonth())) && l.staff.contract?.waiting(Today.millis()) != true)
            Text("계약서 없음 · '계약서'를 누르면 직원 정보로 채워 링크로 서명받아요", style = MaterialTheme.typography.bodySmall, color = warn,
                modifier = Modifier.clickable(role = Role.Button, onClick = onContract))
        // 1.38 [P3-X-05] 연차 남은 날, from the hire date (제60조), less the 연차 days marked here.
        leaveLeft?.let { lf ->
            Hint("연차 남은 ${com.bamnat.core.leave.LeaveAccrual.days(lf.left)} (생긴 ${com.bamnat.core.leave.LeaveAccrual.days(lf.made)} · 쓴 ${com.bamnat.core.leave.LeaveAccrual.days(lf.used)})" +
                (lf.nextOn?.let { " · ${Fmt.monthDay(it)}에 ${com.bamnat.core.leave.LeaveAccrual.days(lf.nextDays)} 더" } ?: ""))
        }
        // [BOSS-11] 4대보험 the law leaves out, with why (공식 대조 G2·G3·G6).
        val rules = remember(place, l.staff, month) { com.bamnat.core.boss.InsuranceRules.of(place, l.staff, month) }
        rules.reasons.forEach { Hint("ⓘ $it") }
        // [BOSS-05] ⚠ signs, each explaining itself on a tap.
        if (l.warnings.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            l.warnings.forEach { w ->
                FilterChip(selected = false, onClick = { why = w.text }, label = { Text(w.title) },
                    leadingIcon = { Icon(Icons.Outlined.WarningAmber, null, Modifier.size(16.dp), tint = warn) })
            }
        }
        if (open) {
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                l.lines.forEach { p ->
                    ValueLine(p.label, Fmt.won(p.amount))
                    if (p.formula.isNotBlank()) Hint(p.formula)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("달라진 날", style = MaterialTheme.typography.labelLarge)
                val mine = work.filter { it.staffId == l.staff.id }.associateBy { it.date }
                MonthDays(month, mine, enabled = !closed) { day = it }
                Hint("평소와 다른 날만 눌러 고쳐요. 지각·조퇴·결근·대근·연차·쉬는 날을 고를 수 있어요.")
            }
        }
    }
    if (slip) PayslipDialog(place, l, month) { slip = false }
    why?.let { AlertDialog(onDismissRequest = { why = null }, confirmButton = { TextButton(onClick = { why = null }) { Text("알겠어요") } }, text = { Text(it) }) }
    day?.let { d -> WorkDayDialog(l.staff, d, work.firstOrNull { it.staffId == l.staff.id && it.date == d }, place = place, work = work, onFind = { day = null; onFind(d) }) { day = null } }
}

/** The month as day numbers, a changed day marked by its kind's first letter. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthDays(month: YearMonth, changed: Map<LocalDate, StaffWork>, enabled: Boolean, onPick: (LocalDate) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        (1..month.lengthOfMonth()).forEach { n ->
            val d = month.atDay(n)
            val w = changed[d]
            val label = w?.let { "$n ${it.kind.label.take(2)}" } ?: "$n"
            FilterChip(selected = w != null, enabled = enabled, onClick = { onPick(d) }, label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                modifier = Modifier.semantics { contentDescription = Fmt.dayTitle(d) + (w?.let { ", ${it.kind.label}" } ?: "") })
        }
    }
}

/** [BOSS-03] How one day went: a kind, and for 시간 바꿈·지각·조퇴·대근 the hours worked. "평소대로" takes the change off. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkDayDialog(s: StaffMember, date: LocalDate, now: StaffWork?, place: Workplace? = null, work: List<StaffWork> = emptyList(),
                          onFind: () -> Unit = {}, onDismiss: () -> Unit) {
    val app = LocalContext.current.appContainer
    val store = app.phaseThree
    val usual = s.weekly[date.dayOfWeek]
    var kind by remember { mutableStateOf(now?.kind ?: WorkKind.LATE) }
    var start by remember { mutableStateOf(now?.start ?: usual?.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(now?.end ?: usual?.end ?: LocalTime.of(18, 0)) }
    var picking by remember { mutableStateOf<Boolean?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${s.name.ifBlank { "직원" }} · ${Fmt.dayTitle(date)}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(usual?.let { "평소 ${Fmt.time(it.start)}~${Fmt.time(it.end)}" } ?: "평소 쉬는 날", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WorkKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
                }
                if (kind in StaffWork.TIMED) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("일한 시간", modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = true }) { Text(Fmt.time(start)) }
                    Text("~")
                    TextButton(onClick = { picking = false }) { Text(Fmt.time(end)) }
                }
                Text(when (kind) {
                    WorkKind.ABSENT -> "결근한 주는 주휴수당이 빠져요."
                    WorkKind.OFF -> "가게가 쉬는 날처럼 빼요. 결근이 아니라 주휴수당은 그대로예요."
                    WorkKind.LEAVE -> "유급 연차: 평소처럼 일한 것으로 줘요."
                    WorkKind.COVER -> "평소 쉬는 날 대신 일한 날이에요."
                    else -> "일한 시간대로 다시 계산해요."
                }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // 1.38 [P3-X-07] What this change does to the month's 인건비 (주휴·가산·4대보험 사업주 몫까지), as the ledger counts it.
                if (place != null) {
                    val delta = remember(kind, start, end, work) {
                        runCatching {
                            val month = YearMonth.from(date)
                            val others = work.filter { it.staffId == s.id && it.date != date }
                            val w = if (kind in StaffWork.TIMED) StaffWork(s.id, date, kind, start, end) else StaffWork(s.id, date, kind)
                            val before = PayrollLedger.line(place, s, month, work.filter { it.staffId == s.id }).employerTotal
                            PayrollLedger.line(place, s, month, others + w).employerTotal - before
                        }.getOrNull()
                    }
                    delta?.takeIf { it != 0L }?.let {
                        Text("이렇게 저장하면 이달 인건비 ${if (it > 0) "+" else "−"}${Fmt.won(kotlin.math.abs(it))}", style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium)
                    }
                }
                // 1.38 [P3-X-01] 대타 찾기: a day this person was to work and now won't; saved first, then who could come and what it costs.
                val gap = kind == WorkKind.ABSENT || kind == WorkKind.LEAVE || kind == WorkKind.OFF
                if (gap && (usual != null || now?.kind in StaffWork.TIMED)) OutlinedButton(onClick = {
                    app.appScope.launch { store.setWork(s.id, date, StaffWork(s.id, date, kind)); }
                    onFind()
                }) { Text("저장하고 대신 일할 사람 찾기") }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = if (kind in StaffWork.TIMED) StaffWork(s.id, date, kind, start, end) else StaffWork(s.id, date, kind)
                // 1.38 PC 5차(같은 구조): the dialog's own scope ends when it closes and could drop the save; the app's does not.
                app.appScope.launch { store.setWork(s.id, date, w) }; onDismiss()
            }, enabled = kind !in StaffWork.TIMED || start != end) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (now != null) TextButton(onClick = { app.appScope.launch { store.setWork(s.id, date, null) }; onDismiss() }) { Text("평소대로") }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )
    picking?.let { isStart ->
        TimePickerDialog(title = if (isStart) "시작" else "끝", initial = if (isStart) start else end, onDismiss = { picking = null }) { t ->
            if (isStart) start = t else end = t; picking = null
        }
    }
}

/** [BOSS-02] 직원 정보: name, 시급·월급·일급, 입사일·업무 (임금대장 칸), 주휴·4대보험 and the usual week. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StaffDialog(start: StaffMember, place: Workplace?, onDismiss: () -> Unit, onSave: (StaffMember) -> Unit, onRemove: () -> Unit, onRehire: (LocalDate) -> Unit = {}) {
    var s by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf<Pair<DayOfWeek, Boolean>?>(null) }
    var hirePick by remember { mutableStateOf(false) }
    var birthPick by remember { mutableStateOf(false) }
    var leftPick by remember { mutableStateOf(false) }
    var rehirePick by remember { mutableStateOf(false) }
    var removeAsk by remember { mutableStateOf(false) }
    // 휴직: (index, true = 시작) being picked; index == leaves.size is a new one.
    var leavePick by remember { mutableStateOf<Pair<Int, Boolean>?>(null) }
    val minimum = RateTable.on(Today.date()).minimumHourlyWage
    // 1.38 PC 8차: 뒤로·닫기 asks before what was typed is thrown away.
    var quitAsk by remember { mutableStateOf(false) }
    val close = { if (s != start) quitAsk = true else onDismiss() }
    // 1.39 G2 (PC f7f61818, 상): a new staff member's 4대보험 follow the law (월급 or 주 15시간 이상 → 모두) until the boss ticks a chip.
    val breaks = place?.legalBreaks ?: true
    var insTouched by remember { mutableStateOf(start.name.isNotBlank() || start.insurance.any) }
    LaunchedEffect(s.form, s.weekly) { if (!insTouched) s = s.copy(insurance = com.bamnat.core.boss.InsuranceRules.defaultFor(s, breaks)) }
    ToolScreen(s.name.ifBlank { "새 직원" }, close, action = "저장", actionEnabled = s.name.isNotBlank(), onAction = { onSave(s) }) {
        OutlinedTextField(s.name, { s = s.copy(name = it.take(20)) }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WageForm.entries.forEach { f ->
                FilterChip(selected = s.form == f, onClick = { if (f != s.form) s = s.copy(form = f, base = if (f == start.form) start.base else 0L, dayRates = if (f == WageForm.HOUR) s.dayRates else emptyMap()) }, label = { Text(when (f) { WageForm.HOUR -> "시급"; WageForm.MONTH -> "월급"; WageForm.DAY -> "일급" }) })
            }
        }
        AmountField(when (s.form) { WageForm.HOUR -> "시급"; WageForm.MONTH -> "월급"; WageForm.DAY -> "일급" }, amountDigits(s.base), { s = s.copy(base = it.toAmount()) })
        if (s.form == WageForm.HOUR && s.base in 1 until minimum) Text("최저임금 ${Fmt.won(minimum)}보다 적어요", style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
        oddWage(s.form, s.base, minimum)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        OutlinedTextField(s.job, { s = s.copy(job = it.take(20)) }, label = { Text("하는 일 (예: 홀, 주방)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("입사일", modifier = Modifier.weight(1f))
            TextButton(onClick = { hirePick = true }) { Text(s.hiredOn?.let(Fmt::shortFullDate) ?: "고르기") }
        }
        // 1.38 퇴사·휴직 (사용자 2026-11-06): 지우지 않고 날짜로 남겨요. 그 뒤 달은 대장·요금 인원에서 빠지고, 지난 기록은 그대로예요.
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("퇴사일 (마지막 근무일)", modifier = Modifier.weight(1f))
            if (s.leftOn != null) TextButton(onClick = { s = s.copy(leftOn = null) }) { Text("지우기") }
            TextButton(onClick = { leftPick = true }) { Text(s.leftOn?.let(Fmt::shortFullDate) ?: "없음") }
        }
        if (s.leftOn != null) {
            Hint("퇴사한 달은 마지막 근무일까지만 계산하고(월급은 날수만큼), 다음 달부터는 대장과 직원 요금제 인원에서 빠져요. 기록은 3년 남아요.")
            if (start.leftOn != null && start.name.isNotBlank()) OutlinedButton(onClick = { rehirePick = true }) { Text("다시 입사하기") }
        }
        Text("휴직 (무급으로 쉬는 기간)", style = MaterialTheme.typography.labelLarge)
        s.leaves.forEachIndexed { i, l ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { leavePick = i to true }) { Text(Fmt.shortFullDate(l.start)) }
                Text("~")
                TextButton(onClick = { leavePick = i to false }) { Text(l.end?.let(Fmt::shortFullDate) ?: "쉬는 중") }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { s = s.copy(leaves = s.leaves.filterIndexed { j, _ -> j != i }) }) { Text("빼기") }
            }
        }
        TextButton(onClick = { leavePick = s.leaves.size to true }) { Text("+ 휴직 기간 더하기") }
        if (s.leaves.isNotEmpty()) Hint("휴직한 날은 임금을 주지 않고(월급은 날수만큼 빼요), 입사일·근속은 이어져요. 한 달 내내 휴직이면 그달 직원 요금제 인원에서 빠져요.")
        if (start.name.isNotBlank() && place != null) ClockLinkLine(s, place) { s = it }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("생년월일 (선택)", modifier = Modifier.weight(1f))
            TextButton(onClick = { birthPick = true }) { Text(s.birth?.let(Fmt::shortFullDate) ?: "고르기") }
        }
        SwitchLine("주휴수당 주기", s.weeklyHolidayPay, { s = s.copy(weeklyHolidayPay = it) }, "한 주 15시간이 안 되면 저절로 빠져요")
        Text("평소 일하는 요일", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(selected = d in s.weekly, label = { Text(Fmt.dayOfWeekShort(d)) }, onClick = {
                    s = s.copy(weekly = if (d in s.weekly) s.weekly - d else s.weekly + (d to (s.weekly.values.lastOrNull() ?: StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)))))
                })
            }
        }
        if (s.weekly.isEmpty()) Hint("고르지 않으면 대장에 0원으로 나와요. 요일이 들쭉날쭉하면 비워 두고, 대장의 근거 보기에서 일한 날을 하루씩 넣어요.")
        DayOfWeek.entries.filter { it in s.weekly }.forEach { d ->
            val sh = s.weekly.getValue(d)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayOfWeekShort(d), modifier = Modifier.width(28.dp))
                TextButton(onClick = { picking = d to true }) { Text(Fmt.time(sh.start)) }
                Text("~")
                TextButton(onClick = { picking = d to false }) { Text(Fmt.time(sh.end)) }
                Text(com.bamnat.core.hours.HoursFormat.korean(sh.paidMinutes(place?.legalBreaks ?: true)), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        // 1.38 요일별 시급 (P3-B-12): "주말은 11,000원" — each weekday its own, 가산·주휴는 그 주 평균(통상시급)으로.
        if (s.form == WageForm.HOUR && s.weekly.isNotEmpty()) {
            var perDay by remember { mutableStateOf(s.dayRates.isNotEmpty()) }
            SwitchLine("요일마다 시급이 달라요", perDay, { on -> perDay = on; if (!on) s = s.copy(dayRates = emptyMap()) }, "예: 주말만 시급을 더 줄 때")
            if (perDay) {
                // 1.38 PC 8차: 비운 칸은 비운 채로 두고(그 요일은 기본 시급), 기본 시급이 다시 들어와 커서를 밀지 않아요.
                val dayText = remember { mutableStateMapOf<DayOfWeek, String>() }
                DayOfWeek.entries.filter { it in s.weekly }.forEach { d ->
                    val text = dayText[d] ?: amountDigits(s.dayRates[d] ?: s.base)
                    AmountField("${Fmt.dayOfWeekShort(d)}요일 시급", text, { v ->
                        dayText[d] = v
                        val won = v.toAmount()
                        s = s.copy(dayRates = if (won <= 0 || won == s.base) s.dayRates - d else s.dayRates + (d to won))
                    })
                    if (text.isEmpty()) Hint("비워 두면 기본 시급 ${Fmt.won(s.base)}으로 계산해요")
                    oddWage(WageForm.HOUR, text.toAmount(), minimum)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                }
                val avg = com.bamnat.core.boss.PayrollLedger.ordinaryHourly(place ?: Workplace("", ""), s)
                Hint("가산수당·주휴수당은 한 주 평균 시급(통상시급) ${Fmt.won(avg)}으로 계산하고, 요일마다 일한 시간은 그 요일 시급으로 줘요(근로기준법 시행령 제6조).")
                if (s.dayRates.values.any { it in 1 until minimum }) Text("최저임금 ${Fmt.won(minimum)}보다 적은 요일이 있어요", style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
            }
        }
        // 1.39 G2 PC 4bd5d745: the 4대보험 hint follows the days, so it sits below them (it pushed the 요일 칩 as they were tapped).
        Text("4대보험 (사장님 몫 계산)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = s.insurance.pension, onClick = { insTouched = true; s = s.copy(insurance = s.insurance.copy(pension = !s.insurance.pension)) }, label = { Text("국민연금") })
            FilterChip(selected = s.insurance.health, onClick = { insTouched = true; s = s.copy(insurance = s.insurance.copy(health = !s.insurance.health)) }, label = { Text("건강보험") })
            FilterChip(selected = s.insurance.employment, onClick = { insTouched = true; s = s.copy(insurance = s.insurance.copy(employment = !s.insurance.employment)) }, label = { Text("고용보험") })
            // 1.39 G2: 모두 keeps the 소득세 떼는 법 (it used to compare with Insurance.ALL, so 3.3% never showed 모두).
            val all = s.insurance.pension && s.insurance.health && s.insurance.employment
            FilterChip(selected = all, onClick = { insTouched = true; s = s.copy(insurance = s.insurance.copy(pension = !all, health = !all, employment = !all)) }, label = { Text("모두") })
        }
        if (!insTouched && s.insurance.any) Hint("월급이거나 한 주 15시간 이상이라 4대보험을 모두 골라 두었어요(법으로 가입 대상). 실제로 들지 않은 보험은 풀어 주세요.")
        else if (com.bamnat.core.boss.InsuranceRules.unticked(s, breaks)) Text("월급이거나 한 주 15시간 이상이면 4대보험 가입 대상이에요. 고르지 않으면 사장님 몫이 0원으로 계산돼 인건비가 적게 보여요.",
            style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
        Text("소득세 떼는 법 (명세서)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bamnat.core.boss.StaffTax.entries.forEach { t -> FilterChip(selected = s.insurance.tax == t, onClick = { s = s.copy(insurance = s.insurance.copy(tax = t)) }, label = { Text(t.label) }) }
        }
        if (s.insurance.tax == com.bamnat.core.boss.StaffTax.BIZ && s.weekly.isNotEmpty()) Text(
            "정해진 요일·시간에 일하는 직원은 보통 근로자예요. 3.3%는 프리랜서(사업소득)일 때만 맞아요.",
            style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
        if (start.name.isNotBlank()) TextButton(onClick = { removeAsk = true }) {
            Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp)); Text(" 직원 빼기")
        }
        Hint("이름·생년월일·입사일·하는 일은 임금대장 칸이에요(근로기준법 시행령 제27조). 생년월일과 입사일을 넣으면 나이·근무 기간에 따른 4대보험 가입 대상도 저절로 맞춰요. 주민등록번호는 묻지 않아요. 직원 정보는 이 휴대폰에만 있고, 백업은 암호를 걸어야 들어가요.")
    }
    picking?.let { (day, isStart) ->
        val sh = s.weekly[day] ?: StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
        TimePickerDialog(title = "${Fmt.dayOfWeekShort(day)}요일 ${if (isStart) "시작" else "끝"}", initial = if (isStart) sh.start else sh.end, onDismiss = { picking = null }) { t ->
            val next = if (isStart) sh.copy(start = t) else sh.copy(end = t)
            if (next.start != next.end) s = s.copy(weekly = s.weekly + (day to next))
            picking = null
        }
    }
    if (birthPick) DayPick(s.birth ?: LocalDate.of(2000, 1, 1), onDismiss = { birthPick = false }) { s = s.copy(birth = it); birthPick = false }
    if (hirePick) DayPick(s.hiredOn ?: Today.date(), onDismiss = { hirePick = false }, years = true) { s = s.copy(hiredOn = it); hirePick = false }
    if (leftPick) DayPick(s.leftOn ?: Today.date(), onDismiss = { leftPick = false }) { s = s.copy(leftOn = it); leftPick = false }
    if (rehirePick) {
        val first = start.leftOn?.plusDays(1)?.let { maxOf(it, Today.date()) } ?: Today.date()
        DayPick(first, onDismiss = { rehirePick = false }, after = start.leftOn) { rehirePick = false; onRehire(it) }
    }
    if (quitAsk) com.bamnat.app.ui.components.AlertDialog(onDismissRequest = { quitAsk = false }, title = { Text("저장하지 않고 나갈까요?") }, text = {
        Text("넣던 내용이 사라져요.", style = MaterialTheme.typography.bodyMedium)
    }, confirmButton = {
        TextButton(onClick = { quitAsk = false; onSave(s) }, enabled = s.name.isNotBlank()) { Text("저장") }
    }, dismissButton = {
        TextButton(onClick = { quitAsk = false; onDismiss() }) { Text("나가기", color = MaterialTheme.colorScheme.error) }
    })
    leavePick?.let { (i, isStart) ->
        val l = s.leaves.getOrNull(i)
        DayPick((if (isStart) l?.start else l?.end) ?: Today.date(), onDismiss = { leavePick = null }) { d ->
            val next = when {
                l == null -> com.bamnat.core.boss.StaffLeave(d)
                isStart -> l.copy(start = d, end = l.end?.takeIf { !it.isBefore(d) })
                else -> l.copy(end = d.takeIf { !it.isBefore(l.start) } ?: l.start)
            }
            s = s.copy(leaves = (if (l == null) s.leaves + next else s.leaves.mapIndexed { j, o -> if (j == i) next else o }).sortedBy { it.start })
            leavePick = null
        }
    }
    if (removeAsk) com.bamnat.app.ui.components.AlertDialog(onDismissRequest = { removeAsk = false }, title = { Text("${start.name} 빼기") }, text = {
        Text("임금대장은 3년 보관해야 해요(근로기준법 제42조). 지우면 이 사람의 근무·급여 기록이 지난달까지 모두 사라져요. 그만둔 거라면 '퇴사 처리'로 두면 기록은 남고 다음 달부터 인원에서 빠져요.",
            style = MaterialTheme.typography.bodyMedium)
    }, confirmButton = {
        TextButton(onClick = { removeAsk = false; onSave(s.copy(leftOn = s.leftOn ?: Today.date())) }) { Text("퇴사 처리") }
    }, dismissButton = {
        TextButton(onClick = { removeAsk = false; onRemove() }) { Text("그래도 지우기", color = MaterialTheme.colorScheme.error) }
    })
}

/** [BOSS-01] 사업장 설정: the name, 5인 이상 once (설계 P3-D7: asked once, never guessed), 지급일 and the legal break. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceDialog(start: Workplace, onDismiss: () -> Unit, onSave: (Workplace) -> Unit) {
    var p by remember { mutableStateOf(start) }
    // 같은 구조(직원 창과 같은 1.38 PC 8차): 바꾼 것이 있으면 나가기 전에 물어요.
    var quitAsk by remember { mutableStateOf(false) }
    if (quitAsk) com.bamnat.app.ui.components.AlertDialog(onDismissRequest = { quitAsk = false }, title = { Text("저장하지 않고 나갈까요?") }, text = {
        Text("바꾼 내용이 사라져요.", style = MaterialTheme.typography.bodyMedium)
    }, confirmButton = {
        TextButton(onClick = { quitAsk = false; onSave(p) }, enabled = p.name.isNotBlank()) { Text("저장") }
    }, dismissButton = {
        TextButton(onClick = { quitAsk = false; onDismiss() }) { Text("나가기", color = MaterialTheme.colorScheme.error) }
    })
    ToolScreen("사업장 설정", { if (p != start) quitAsk = true else onDismiss() }, action = "저장", actionEnabled = p.name.isNotBlank(), onAction = { onSave(p) }) {
        OutlinedTextField(p.name, { p = p.copy(name = it.take(20)) }, label = { Text("사업장 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("늘 일하는 사람이 5명 이상인가요?", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = p.fivePlus == true, onClick = { p = p.copy(fivePlus = true) }, label = { Text("5명 이상") })
            FilterChip(selected = p.fivePlus == false, onClick = { p = p.copy(fivePlus = false) }, label = { Text("5명 미만") })
        }
        Hint("5인 미만이면 연장·야간·휴일 가산(50%)이 없어요(근로기준법 제11조). 사장님 본인과 가족은 빼고 세요.")
        Text("급여 주는 날", style = MaterialTheme.typography.labelLarge)
        // 1.39 G2 PC 4bd5d745: 10일 was painted before anything was picked, so it read as set while the 대장 said '지급일 미정'.
        com.bamnat.app.ui.components.DayOfMonthPicker(p.payday ?: 0, { p = p.copy(payday = it) })
        if (p.payday == null) Hint("아직 고르지 않았어요. 고르면 대장·명세서에 지급일이 나와요.")
        SwitchLine("휴게시간 빼고 세기", p.legalBreaks, { p = p.copy(legalBreaks = it) }, "4시간이면 30분, 8시간이면 1시간을 빼요(근로기준법 제54조)")
        // 1.38 계산 단위 (경쟁 앱 조사 P3-B-12): up only — 깎는 절사는 임금 전액 지급(제43조)에 어긋나요.
        Text("근무 시간 계산 단위", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1, 10, 30, 60).forEach { u -> FilterChip(selected = p.minuteUnit == u, onClick = { p = p.copy(minuteUnit = u) }, label = { Text(if (u == 60) "1시간" else "${u}분") }) }
        }
        // 1.39 [BOSS-12] 산재보험 업종 (공식 대조 G5, 고용노동부고시 제2025-91호).
        IndustryPicker(p.industry) { p = p.copy(industry = it) }
        Hint(if (p.minuteUnit <= 1) "일한 그대로 1분 단위로 세요."
            else "하루 일한 시간을 ${if (p.minuteUnit == 60) "1시간" else "${p.minuteUnit}분"} 단위로 올려서 세요(13:07 퇴근 → ${com.bamnat.core.boss.PayrollLedger.roundUp(LocalTime.of(9, 0), LocalTime.of(13, 7), p.minuteUnit)?.let(Fmt::time)}). 내려서 깎는 건 근로기준법 제43조(임금 전액 지급)에 어긋나서 고를 수 없어요.")
    }
}

/**
 * 1.39 [BOSS-12] 산재보험 업종: 작은 가게가 많은 넷은 칩으로, 나머지는 목록에서, 결정 통지서의 요율을 직접 넣을 수도 있어요. 사업주만 내는
 * 보험료라 직원 명세서에는 나오지 않아요.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IndustryPicker(code: String?, onPick: (String?) -> Unit) {
    val kinds = com.bamnat.core.boss.AccidentRates.KINDS
    var more by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(code?.startsWith(com.bamnat.core.boss.AccidentRates.CUSTOM) == true) }
    var typed by remember { mutableStateOf(code?.removePrefix(com.bamnat.core.boss.AccidentRates.CUSTOM)?.takeIf { typing }.orEmpty()) }
    Text("산재보험 업종", style = MaterialTheme.typography.labelLarge)
    val picked = com.bamnat.core.boss.AccidentRates.kind(code)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (kinds.take(4) + listOfNotNull(picked?.takeIf { k -> kinds.indexOf(k) >= 4 })).forEach { k ->
            FilterChip(selected = code == k.code, onClick = { typing = false; onPick(k.code) }, label = { Text(k.label) })
        }
        FilterChip(selected = false, onClick = { more = true }, label = { Text("다른 업종") })
        FilterChip(selected = typing, onClick = { typing = true }, label = { Text("요율 직접 넣기") })
    }
    if (typing) OutlinedTextField(typed, { v ->
        typed = v.filter { it.isDigit() || it == '.' }.take(6)
        onPick(typed.toDoubleOrNull()?.takeIf { it > 0 && it <= 200 }?.let { com.bamnat.core.boss.AccidentRates.CUSTOM + com.bamnat.core.boss.AccidentRates.permille(it) })
    }, label = { Text("업종 요율 (천분율 ‰, 출퇴근재해 빼고)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
    Hint(picked?.let { "${com.bamnat.core.boss.AccidentRates.describe(it)} · 급여에 곱해 사장님만 내요(직원 명세서에서 떼지 않아요). ${com.bamnat.core.boss.AccidentRates.YEAR}년 고시 요율이에요." }
        ?: "업종을 고르면 인건비에 산재보험료를 더해요. 근로복지공단 사업종류 결정 통지서의 요율이 따로 있으면 '요율 직접 넣기'로 넣어요.")
    if (more) com.bamnat.app.ui.components.AlertDialog(onDismissRequest = { more = false }, title = { Text("산재보험 업종") }, text = {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
            kinds.forEach { k ->
                Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { typing = false; onPick(k.code); more = false }.padding(vertical = 10.dp)) {
                    Text(k.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("${com.bamnat.core.boss.AccidentRates.permille(k.permille)}‰", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = { more = false }) { Text("닫기") } })
}

/** A day picked on Material's calendar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DayPick(initial: LocalDate, onDismiss: () -> Unit, after: LocalDate? = null, years: Boolean = false, onPick: (LocalDate) -> Unit) {
    // [after]: only days later than it can be picked (다시 입사는 퇴사일 다음 날부터).
    val selectable = remember(after) {
        object : androidx.compose.material3.SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = after == null || LocalDate.ofEpochDay(utcTimeMillis / 86_400_000L).isAfter(after)
            override fun isSelectableYear(year: Int) = after == null || year >= after.year
        }
    }
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L, selectableDates = selectable)
    com.bamnat.app.ui.components.DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) } ?: onDismiss() }) { Text("고르기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }) {
        if (years) com.bamnat.app.ui.components.YearJump(state)
        androidx.compose.material3.DatePicker(state)
    }
}

/**
 * [BOSS-09] 임금명세서 미리보기: the 6 필수 기재사항 ticked one by one (근로기준법 시행령 제27조의2), what comes off, and sending it only
 * to that staff member, as words or a PDF, through the phone's share sheet. Nothing goes to a server.
 */
@Composable
private fun PayslipDialog(place: Workplace, l: LedgerLine, month: YearMonth, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = context.appContainer.phaseThree
    val scope = rememberCoroutineScope()
    val slip = remember(place, l) { com.bamnat.core.boss.Payslip.of(place, l) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("${l.staff.name.ifBlank { "직원" }} ${month.monthValue}월 명세서") }, text = {
        Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            slip.checks.forEach { c ->
                Text((if (c.ok) "✓ " else "✕ ") + c.name + if (!c.ok && c.fix.isNotBlank()) " · ${c.fix}" else "", style = MaterialTheme.typography.bodySmall,
                    color = if (c.ok) MaterialTheme.colorScheme.onSurface else LocalCalendarColors.current.warning)
            }
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            ValueLine("지급 합계", Fmt.won(slip.gross))
            slip.deductions.forEach { (n, v) -> ValueLine(n, "-" + Fmt.won(v)) }
            ValueLine("실지급액", Fmt.won(slip.net), strong = true)
            Hint(PayrollLedger.NOTICE)
        }
    }, confirmButton = {
        Row {
            TextButton(enabled = slip.ready, onClick = {
                scope.launch {
                    runCatching { com.bamnat.app.report.ReportShare.pdf(context, slip.doc(), "임금명세서-${l.staff.name.ifBlank { "직원" }}-$month.pdf") }
                    store.mark(place.id, month, l, sent = true)
                    context.appContainer.telemetry.event(com.bamnat.core.stats.UsageEvent.SLIP_SENT.key)
                }
            }) { Text("PDF") }
            TextButton(enabled = slip.ready, onClick = {
                val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, slip.text()) }
                runCatching { context.startActivity(android.content.Intent.createChooser(i, "명세서 보내기")) }
                scope.launch { store.mark(place.id, month, l, sent = true); context.appContainer.telemetry.event(com.bamnat.core.stats.UsageEvent.SLIP_SENT.key) }
            }) { Text("글로 보내기") }
        }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}

/**
 * 1.38 [P3-B-11] 출퇴근 링크 in the staff window: made, sent or cut. Saved at once (the key must not be lost if the window is closed
 * without 저장). The page has only 출근·퇴근 buttons; no location, no app needed on the staff member's phone.
 */
@Composable
private fun ClockLinkLine(s: StaffMember, place: Workplace, onChange: (StaffMember) -> Unit) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var note by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Text("출퇴근 링크 (위치 없이)", style = MaterialTheme.typography.labelLarge)
    val id = s.clockId
    val token = s.clockToken
    if (id == null || token == null) {
        OutlinedButton(enabled = !busy && c.shareApi.enabled, onClick = {
            busy = true
            scope.launch {
                runCatching { c.shareApi.clockCreate(s.name.take(20), place.name.take(30)) }.onSuccess { link ->
                    val next = s.copy(clockId = link.id, clockToken = link.token)
                    c.phaseThree.saveStaff(next, place.id)
                    onChange(next)
                    com.bamnat.app.boss.StaffClockJob.schedule(context)
                    sendClock(context, s.name, link.url)
                    note = null
                }.onFailure { note = "인터넷이 안 돼 링크를 만들지 못했어요. 연결한 뒤 다시 눌러 주세요." }
                busy = false
            }
        }) { Text("출퇴근 링크 만들어 보내기") }
        Hint("직원이 링크에서 출근·퇴근을 누르면 사장님 휴대폰에 알림이 와요. 위치·전화번호는 받지 않고 누른 시각만 와요. 넣을지는 사장님이 골라요.")
    } else {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { sendClock(context, s.name, com.bamnat.app.BuildConfig.SHARE_BASE_URL.trimEnd('/') + "/c/" + id) }) { Text("링크 다시 보내기") }
            TextButton(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    runCatching { c.shareApi.clockDelete(id, token) }.onSuccess {
                        val next = s.copy(clockId = null, clockToken = null)
                        c.phaseThree.saveStaff(next, place.id)
                        onChange(next)
                        note = "링크를 끊었어요. 그 주소로는 더 누를 수 없어요."
                    }.onFailure { note = "인터넷이 안 돼 링크를 끊지 못했어요. 연결한 뒤 다시 눌러 주세요." }
                    busy = false
                }
            }) { Text("링크 끊기") }
        }
        Hint("퇴사일이 지나면 링크는 저절로 끊어요. 링크가 다른 사람에게 갔으면 끊고 새로 만들어 주세요.")
    }
    note?.let { Hint(it) }
}

private fun sendClock(context: android.content.Context, name: String, url: String) {
    val text = "${name}님 출퇴근 링크예요. 출근·퇴근할 때 열어서 단추만 눌러 주세요(위치는 보내지 않아요).\n$url"
    val i = android.content.Intent(android.content.Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, text) }
    runCatching { context.startActivity(android.content.Intent.createChooser(i, "출퇴근 링크 보내기").addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * 1.38 [P3-B-11] 출퇴근 기록 → 대장: the days the presses say went differently from the usual week (지각·조퇴·다른 시간), each ticked to
 * put in. Days the boss already wrote, days missing a 퇴근, and days that went as usual are left out (2분까지는 평소대로 봐요).
 */
@Composable
private fun ClockCard(staff: List<StaffMember>, punches: List<com.bamnat.core.boss.StaffPunch>, work: List<StaffWork>, place: Workplace, onPut: (List<StaffWork>) -> Unit) {
    if (punches.isEmpty()) return
    val byStaff = punches.groupBy { it.staffId }
    val rows = remember(staff, punches, work) {
        staff.flatMap { s ->
            val days = com.bamnat.core.boss.StaffClock.days(byStaff[s.id].orEmpty())
            com.bamnat.core.boss.StaffClock.suggest(s, days, work.filter { it.staffId == s.id }.map { it.date }.toSet(), graceMinutes = 2)
        }.sortedBy { it.date }
    }
    val open = remember(staff, punches) {
        staff.flatMap { s -> com.bamnat.core.boss.StaffClock.days(byStaff[s.id].orEmpty()).filter { it.start != null && it.end == null }.map { s.name to it } }
    }
    SectionCard(title = "출퇴근 기록") {
        if (rows.isEmpty()) Hint("기록 ${punches.size}개 · 평소와 다른 날이 없어요." + if (open.isNotEmpty()) " 퇴근을 아직 안 누른 날 ${open.size}일." else "")
        else {
            val on = remember(rows) { mutableStateListOf<Boolean>().apply { addAll(rows.map { com.bamnat.core.boss.StaffClock.shortNote(it.start, it.end) == null }) } }
            rows.forEachIndexed { i, r ->
                val name = staff.firstOrNull { it.id == r.staffId }?.name.orEmpty()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = on[i], onCheckedChange = { on[i] = it })
                    Column {
                        Text("$name · ${Fmt.monthDay(r.date)} ${r.kind.label} ${r.start?.let(Fmt::time)}~${r.end?.let(Fmt::time)}", style = MaterialTheme.typography.bodyMedium)
                        // 1.38 PC 8차: 출근·퇴근을 바로 이어 누른 날(1분 근무)은 실수일 수 있어요.
                        com.bamnat.core.boss.StaffClock.shortNote(r.start, r.end)?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning) }
                    }
                }
            }
            OutlinedButton(enabled = on.any { it }, onClick = { onPut(rows.filterIndexed { i, _ -> on[i] }) }) { Text("고른 ${on.count { it }}일 대장에 넣기") }
            Hint("직원이 출퇴근 링크에서 누른 시각이에요. 넣은 뒤에도 그날을 눌러 고칠 수 있어요." + if (place.minuteUnit > 1) " 계산 단위 ${place.minuteUnit}분으로 올려 계산해요." else "")
            if (open.isNotEmpty()) Hint("퇴근을 안 누른 날 ${open.size}일은 넣지 않았어요: " + open.joinToString(", ") { (n, d) -> "$n ${Fmt.monthDay(d.date)}" })
        }
    }
}

/**
 * 1.38 PC 8차 (시급 1,200,010,320원이 경고 없이 대장에 49,479,528,856원으로): a wage far beyond any real one is told at once.
 * 시급·일급은 최저임금의 10배·100배(약 10만·100만 원), 월급은 3천만 원을 넘으면 물어요. 막지는 않아요(입력은 사장님 몫).
 */
internal fun oddWage(form: WageForm, amount: Long, minimum: Long): String? {
    val limit = when (form) { WageForm.HOUR -> minimum * 10; WageForm.DAY -> minimum * 100; WageForm.MONTH -> 30_000_000L }
    if (amount <= limit) return null
    val word = when (form) { WageForm.HOUR -> "시급"; WageForm.DAY -> "일급"; WageForm.MONTH -> "월급" }
    return "$word ${Fmt.won(amount)}이 맞나요? 보통보다 아주 커요. 숫자를 한 번 더 봐 주세요"
}

/**
 * 1.39 G2 PC 4bd5d745: '지난달 X · ±Y' over the same people only. When some have no known last month the line says whose months
 * were set side by side ('민수 기준'), so a new 지영 never reads as a raise.
 */
internal fun lastHead(now: com.bamnat.core.boss.Ledger, last: com.bamnat.core.boss.Ledger): String? {
    val ids = last.lines.filter { it.employerTotal > 0 }.map { it.staff.id }.toSet()
    val same = now.lines.filter { it.staff.id in ids }
    if (same.isEmpty()) return null
    val before = last.lines.filter { it.staff.id in ids }.sumOf { it.employerTotal }
    val nowSame = same.sumOf { it.employerTotal }
    if (before <= 0 || nowSame <= 0) return null
    val d = nowSame - before
    val change = if (d == 0L) "같아요" else "${if (d > 0) "+" else "−"}${Fmt.won(kotlin.math.abs(d))}"
    if (same.size == now.lines.size) return "지난달 ${Fmt.won(before)} · $change"
    val who = same.map { it.staff.name.ifBlank { "이름 없음" } }.let { if (it.size <= 2) it.joinToString(", ") else "${it.size}명" }
    return "$who 기준 지난달 ${Fmt.won(before)} → ${Fmt.won(nowSame)} · $change"
}
