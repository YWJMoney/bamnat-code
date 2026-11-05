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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
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
    ToolScreen("급여 대장", onClose) {
        if (place == null) { Hint("사업장을 준비하고 있어요…"); return@ToolScreen }
        val staff by store.staff(place.id).collectAsStateWithLifecycle(emptyList())
        val work by store.work(place.id, ym).collectAsStateWithLifecycle(emptyList())
        val closed by store.payrollMonth(place.id, ym).collectAsStateWithLifecycle(null)
        val ledger = remember(place, staff, work, ym) { PayrollLedger.month(place, staff, ym, work) }
        val isClosed = closed?.state == "CLOSED"

        Text(PayrollLedger.NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // The month: ◀ 2026년 11월 ▶
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = ym.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "지난달") }
            Text(Fmt.month(ym) + if (isClosed) " · 닫힘" else "", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { month = ym.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
        }
        SectionCard(title = place.name, trailing = { TextButton(onClick = { settings = true }) { Text("사업장 설정") } }) {
            ledger.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = if (place.fivePlus == null) LocalCalendarColors.current.warning else MaterialTheme.colorScheme.onSurfaceVariant) }
            // PC 1차 제안: not answered yet → ask right here in one tap, not behind 사업장 설정. The safe side (가산 넣기) stays until then.
            if (place.fivePlus == null) {
                Text("사장님 빼고 일하는 사람(가족 아닌 직원·알바 모두)이 평소 몇 명인가요? 지금 대장엔 ${staff.size}명이 있어요.",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = false, onClick = { scope.launch { store.saveWorkplace(place.copy(fivePlus = true)) } }, label = { Text("5명 이상") })
                    FilterChip(selected = false, onClick = { scope.launch { store.saveWorkplace(place.copy(fivePlus = false)) } }, label = { Text("5명 미만") })
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                StatTile("급여 합계", Fmt.won(ledger.gross), Modifier.weight(1f), caption = "${staff.size}명")
                StatTile("사장님 4대보험", Fmt.won(ledger.employerInsurance), Modifier.weight(1f), caption = "참고")
            }
            ValueLine("인건비 합계", Fmt.won(ledger.total), strong = true)
            place.payDate(ym)?.let { Hint("지급일 ${Fmt.monthDay(it)}") }
        }
        if (staff.isEmpty()) Hint("직원을 더하면 평소 근무 요일·시간으로 한 달 급여를 계산해요. 지각·결근·대근이 있던 날만 고치면 돼요.")
        val marks by store.lines(place.id, ym).collectAsStateWithLifecycle(emptyList())
        val marked = marks.associateBy { it.staffId }
        ledger.lines.forEach { l -> key(l.staff.id) { LedgerLineCard(l, place, ym, work, isClosed, marked[l.staff.id], onEdit = { editing = l.staff }) } }
        // [BOSS-10] 지난달 비교: why a line moved, before a staff member asks.
        val lastWork by store.work(place.id, ym.minusMonths(1)).collectAsStateWithLifecycle(emptyList())
        val compare = remember(ledger, lastWork, staff) {
            com.bamnat.core.boss.PayrollCompare.rows(ledger, PayrollLedger.month(place, staff, ym.minusMonths(1), lastWork))
        }
        if (compare.any { it.diff != null && it.diff != 0L }) SectionCard(title = "지난달과 비교") {
            compare.forEach { r ->
                ValueLine(r.staff.name.ifBlank { "이름 없음" }, r.diff?.let { (if (it >= 0) "+" else "") + Fmt.won(it) } ?: "-")
                Hint(r.why)
            }
        }
        // 1.38 Phase 3 요금: 3 staff are the free taste; from the fourth the 직원 요금제 band that holds them. Staff already in always stay
        // and their pay is worked out and exported as before, plan or not.
        val gate by context.appContainer.featureGate.state.collectAsStateWithLifecycle()
        val next = gate.staff(staff.size + 1)
        var ask by remember { mutableStateOf<com.bamnat.core.tier.WorkPlanRules.Need?>(null) }
        OutlinedButton(onClick = {
            if (!next.allowed) { ask = next; return@OutlinedButton }
            editing = StaffMember(UUID.randomUUID().toString(), "", base = RateTable.on(ym.atDay(1)).minimumHourlyWage)
        }, enabled = !isClosed) { Text("직원 더하기") }
        val hold = gate.hold(com.bamnat.core.tier.WorkPlanKind.STAFF)
        if (gate.live) Hint(when {
            hold != null && hold.cap < Int.MAX_VALUE -> "직원 요금제 ${hold.cap}명 구간 · 지금 ${staff.size}명"
            hold != null -> "직원 ${staff.size}명"
            else -> "직원 ${gate.plans.freeStaff}명까지 무료예요 · 지금 ${staff.size}명"
        })
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
        }, onRemove = { place?.let { scope.launch { store.deleteStaff(s.id) } }; editing = null })
    }
    if (settings && place != null) PlaceDialog(place, onDismiss = { settings = false }) { p -> scope.launch { store.saveWorkplace(p) }; settings = false }
}

/** One staff member's month: the total, ⚠ signs, and a tap for each line's 근거 and the days that went differently. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LedgerLineCard(l: LedgerLine, place: Workplace, month: YearMonth, work: List<StaffWork>, closed: Boolean,
                           mark: com.bamnat.app.data.db.PayrollLineEntity?, onEdit: () -> Unit) {
    var open by rememberSaveable(l.staff.id) { mutableStateOf(false) }
    var slip by remember { mutableStateOf(false) }
    var why by remember { mutableStateOf<String?>(null) }
    var day by remember { mutableStateOf<LocalDate?>(null) }
    val warn = LocalCalendarColors.current.warning
    SectionCard(title = l.staff.name.ifBlank { "이름 없음" }, trailing = { TextButton(onClick = onEdit, enabled = !closed) { Text("직원 정보") } }) {
        Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { open = !open }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(Fmt.won(l.gross), style = MaterialTheme.typography.titleMedium)
                Text("${l.workDays}일 · ${com.bamnat.core.hours.HoursFormat.korean(l.workMinutes)}" +
                    (if (l.employerInsurance > 0) " · 4대보험 ${Fmt.won(l.employerInsurance)}" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (open) "접기" else "근거 보기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        // [BOSS-09][BOSS-10] 명세서 and 지급 체크, right on the line.
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { slip = true }, enabled = l.gross > 0) { Text(if (mark?.slipSentAt != null) "명세서 ✓" else "명세서") }
            val store = LocalContext.current.appContainer.phaseThree
            val scope = rememberCoroutineScope()
            FilterChip(selected = mark?.paidAt != null, onClick = { scope.launch { store.mark(place.id, month, l, paid = mark?.paidAt == null) } },
                label = { Text(if (mark?.paidAt != null) "지급했어요 ✓" else "지급했어요") })
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
    day?.let { d -> WorkDayDialog(l.staff, d, work.firstOrNull { it.staffId == l.staff.id && it.date == d }) { day = null } }
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
private fun WorkDayDialog(s: StaffMember, date: LocalDate, now: StaffWork?, onDismiss: () -> Unit) {
    val store = LocalContext.current.appContainer.phaseThree
    val scope = rememberCoroutineScope()
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
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val w = if (kind in StaffWork.TIMED) StaffWork(s.id, date, kind, start, end) else StaffWork(s.id, date, kind)
                scope.launch { store.setWork(s.id, date, w) }; onDismiss()
            }, enabled = kind !in StaffWork.TIMED || start != end) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (now != null) TextButton(onClick = { scope.launch { store.setWork(s.id, date, null) }; onDismiss() }) { Text("평소대로") }
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
private fun StaffDialog(start: StaffMember, place: Workplace?, onDismiss: () -> Unit, onSave: (StaffMember) -> Unit, onRemove: () -> Unit) {
    var s by remember { mutableStateOf(start) }
    var picking by remember { mutableStateOf<Pair<DayOfWeek, Boolean>?>(null) }
    var hirePick by remember { mutableStateOf(false) }
    var birthPick by remember { mutableStateOf(false) }
    val minimum = RateTable.on(Today.date()).minimumHourlyWage
    ToolScreen(s.name.ifBlank { "새 직원" }, onDismiss, action = "저장", actionEnabled = s.name.isNotBlank(), onAction = { onSave(s) }) {
        OutlinedTextField(s.name, { s = s.copy(name = it.take(20)) }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            WageForm.entries.forEach { f ->
                FilterChip(selected = s.form == f, onClick = { s = s.copy(form = f) }, label = { Text(when (f) { WageForm.HOUR -> "시급"; WageForm.MONTH -> "월급"; WageForm.DAY -> "일급" }) })
            }
        }
        AmountField(when (s.form) { WageForm.HOUR -> "시급"; WageForm.MONTH -> "월급"; WageForm.DAY -> "일급" }, amountDigits(s.base), { s = s.copy(base = it.toAmount()) })
        if (s.form == WageForm.HOUR && s.base in 1 until minimum) Text("최저임금 ${Fmt.won(minimum)}보다 적어요", style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
        OutlinedTextField(s.job, { s = s.copy(job = it.take(20)) }, label = { Text("하는 일 (예: 홀, 주방)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("입사일", modifier = Modifier.weight(1f))
            TextButton(onClick = { hirePick = true }) { Text(s.hiredOn?.let(Fmt::shortFullDate) ?: "고르기") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("생년월일 (선택)", modifier = Modifier.weight(1f))
            TextButton(onClick = { birthPick = true }) { Text(s.birth?.let(Fmt::shortFullDate) ?: "고르기") }
        }
        SwitchLine("주휴수당 주기", s.weeklyHolidayPay, { s = s.copy(weeklyHolidayPay = it) }, "한 주 15시간이 안 되면 저절로 빠져요")
        Text("4대보험 (사장님 몫 계산)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = s.insurance.pension, onClick = { s = s.copy(insurance = s.insurance.copy(pension = !s.insurance.pension)) }, label = { Text("국민연금") })
            FilterChip(selected = s.insurance.health, onClick = { s = s.copy(insurance = s.insurance.copy(health = !s.insurance.health)) }, label = { Text("건강보험") })
            FilterChip(selected = s.insurance.employment, onClick = { s = s.copy(insurance = s.insurance.copy(employment = !s.insurance.employment)) }, label = { Text("고용보험") })
            FilterChip(selected = s.insurance == Insurance.ALL, onClick = { s = s.copy(insurance = if (s.insurance == Insurance.ALL) Insurance() else Insurance.ALL) }, label = { Text("모두") })
        }
        Text("소득세 떼는 법 (명세서)", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bamnat.core.boss.StaffTax.entries.forEach { t -> FilterChip(selected = s.insurance.tax == t, onClick = { s = s.copy(insurance = s.insurance.copy(tax = t)) }, label = { Text(t.label) }) }
        }
        if (s.insurance.tax == com.bamnat.core.boss.StaffTax.BIZ && s.weekly.isNotEmpty()) Text(
            "정해진 요일·시간에 일하는 직원은 보통 근로자예요. 3.3%는 프리랜서(사업소득)일 때만 맞아요.",
            style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
        Text("평소 일하는 요일", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(selected = d in s.weekly, label = { Text(Fmt.dayOfWeekShort(d)) }, onClick = {
                    s = s.copy(weekly = if (d in s.weekly) s.weekly - d else s.weekly + (d to (s.weekly.values.lastOrNull() ?: StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)))))
                })
            }
        }
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
        if (start.name.isNotBlank()) TextButton(onClick = onRemove) {
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
    if (hirePick) DayPick(s.hiredOn ?: Today.date(), onDismiss = { hirePick = false }) { s = s.copy(hiredOn = it); hirePick = false }
}

/** [BOSS-01] 사업장 설정: the name, 5인 이상 once (설계 P3-D7: asked once, never guessed), 지급일 and the legal break. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlaceDialog(start: Workplace, onDismiss: () -> Unit, onSave: (Workplace) -> Unit) {
    var p by remember { mutableStateOf(start) }
    ToolScreen("사업장 설정", onDismiss, action = "저장", actionEnabled = p.name.isNotBlank(), onAction = { onSave(p) }) {
        OutlinedTextField(p.name, { p = p.copy(name = it.take(20)) }, label = { Text("사업장 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("늘 일하는 사람이 5명 이상인가요?", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = p.fivePlus == true, onClick = { p = p.copy(fivePlus = true) }, label = { Text("5명 이상") })
            FilterChip(selected = p.fivePlus == false, onClick = { p = p.copy(fivePlus = false) }, label = { Text("5명 미만") })
        }
        Hint("5인 미만이면 연장·야간·휴일 가산(50%)이 없어요(근로기준법 제11조). 사장님 본인과 가족은 빼고 세요.")
        Text("급여 주는 날", style = MaterialTheme.typography.labelLarge)
        com.bamnat.app.ui.components.DayOfMonthPicker(p.payday ?: 10, { p = p.copy(payday = it) })
        SwitchLine("휴게시간 빼고 세기", p.legalBreaks, { p = p.copy(legalBreaks = it) }, "4시간이면 30분, 8시간이면 1시간을 빼요(근로기준법 제54조)")
    }
}

/** A day picked on Material's calendar. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun DayPick(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
    com.bamnat.app.ui.components.DatePickerDialog(onDismissRequest = onDismiss, confirmButton = {
        TextButton(onClick = { state.selectedDateMillis?.let { onPick(LocalDate.ofEpochDay(it / 86_400_000L)) } ?: onDismiss() }) { Text("고르기") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } }) { androidx.compose.material3.DatePicker(state) }
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
