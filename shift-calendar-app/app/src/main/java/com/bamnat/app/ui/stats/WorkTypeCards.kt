package com.bamnat.app.ui.stats

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.WorkTypePrefs
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.CountStepper
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.StatTile
import com.bamnat.app.ui.components.StepperRow
import com.bamnat.app.ui.components.WarningBanner
import com.bamnat.app.ui.guide.FeatureCard
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.hours.MonthSummary
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.pay.LeaveBook
import com.bamnat.core.pay.PayGaps
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.time.Today
import com.bamnat.core.work.DayWork
import com.bamnat.core.work.ShiftMonth
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

/*
 * Work-type cards of 통계: the first card a shift worker [SHFT-02] or a day worker [DAYW-02] sees, and the
 * cards after the pay: swaps and covers [SHFT-03], pay against what came in [SHFT-04], the 시간외 상한
 * [DAYW-05] and 징검다리 연휴 [DAYW-04]. Each shows only on a calendar of its own kind of work.
 */

/** A main calendar from before work styles were asked is a shift calendar, as its words are. */
private fun WorkStyle?.isShift() = this == null || this == WorkStyle.SHIFT

/** [SHFT-02][DAYW-02] The month's first card for a shift or day calendar; nothing for other kinds. */
@Composable
internal fun WorkTypeFirstCard(
    style: WorkStyle?,
    calendarId: String,
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    summary: MonthSummary,
    payTotal: Long?,
    profile: PayProfile?,
    columns: Int,
    /** [PAYX-25] The month's 비과세 when it needs the month's pay (생산직); null works it out from the setup. */
    nonTaxable: Long? = null,
    /** G 1.32 [WTYP-07] The days the month counts (the pay period when chosen). */
    range: Pair<LocalDate, LocalDate>? = null,
) {
    when {
        // [GUIDE-05] Each card can be turned off for its calendar.
        style.isShift() -> FeatureCard("shift.firstCard", calendarId) { ShiftFirstCard(snapshot, month, payTotal, range) }
        style == WorkStyle.DAY -> FeatureCard("day.firstCard", calendarId) { DayFirstCard(calendarId, snapshot, month, summary, payTotal, profile, columns, nonTaxable) }
    }
}

/** [SHFT-03][SHFT-04][DAYW-04][DAYW-05] The work type's cards after the month's pay. */
@Composable
internal fun WorkTypeMoreCards(
    style: WorkStyle?,
    calendarId: String,
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    summary: MonthSummary,
    payTotal: Long?,
    profile: PayProfile?,
    book: MoneyBook?,
    nonTaxable: Long? = null,
    /** [PAYX-48] The pay screen, for 월급날 비교 before pay is set up. */
    onOpenPay: () -> Unit = {},
    /** [PAYX-48] 들어온 급여 적기: a 급여 line in 돈 on that day. */
    onAddPay: (LocalDate, Long) -> Unit = { _, _ -> },
    /** [TAX-02] 세후 from the calendar's own pay records (W), when 세금 예측 is open to this phone. */
    takeHome: suspend (Long, YearMonth) -> com.bamnat.core.tax.TaxLearner.Forecast? = { _, _ -> null },
) {
    // [GUIDE-05] Each card can be turned off for its calendar.
    if (style?.usesPayEngine != false) FeatureCard("pay.gap", calendarId) {
        PayGapCard(calendarId, month, payTotal, profile, book, style, nonTaxable, onOpenPay, onAddPay, takeHome)
    }
    if (style.isShift()) FeatureCard("shift.swapCard", calendarId) { SwapCard(snapshot, month) }
    // [SHFT-07] The year's night share, runs of nights and short rests.
    if (style.isShift()) FeatureCard("shift.health", calendarId) { HealthYearCard(snapshot, calendarId) }
    // G 1.32 [WTYP-07] 609 공무원·경찰·소방 (교대 포함) start at 월 57시간 without setting it.
    if (style == WorkStyle.DAY || profile?.publicGrade != null) FeatureCard("day.overtimeCap", calendarId) { OvertimeCapCard(calendarId, summary, profile) }
    // 1.32 [WTYP-15] 선택근무 정산 기간 (사무직).
    if (style == WorkStyle.DAY) FeatureCard("day.selective", calendarId) { SelectiveCard(calendarId, snapshot) }
    // G 1.32 [WTYP-07] 609 포괄임금: the fixed overtime hours against this month's.
    com.bamnat.core.work.WorkTypeChecks.cover(profile, summary.extraMinutes)?.let { c ->
        FeatureCard("pay.coverHours", calendarId) { CoverHoursCard(c) }
    }
    // G 1.32 [WTYP-07] 609 근속 and the year for 퇴직금, from 입사일.
    if (style?.usesPayEngine != false) com.bamnat.core.work.WorkTypeChecks.tenure(profile?.hireDate, Today.date())?.let { t ->
        FeatureCard("pay.tenure", calendarId) { TenureCard(t) }
    }
    // [LEAVE-04] 징검다리 for every kind of work that takes leave.
    if (style == null || style == WorkStyle.DAY || style == WorkStyle.SHIFT || style == WorkStyle.HOURLY) {
        FeatureCard("day.bridges", calendarId) { BridgeCard(snapshot, style) }
    }
}

/** [SHFT-02] Each code's count this month, the night shifts and the expected pay. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShiftFirstCard(snapshot: ScheduleSnapshot, month: YearMonth, payTotal: Long?, range: Pair<LocalDate, LocalDate>? = null) {
    val tally = remember(snapshot, month, range) {
        ShiftMonth.of(month, snapshot.shiftTypes, snapshot.resolver::resolve, range?.first ?: month.atDay(1), range?.second ?: month.atEndOfMonth())
    }
    if (tally.counts.isEmpty()) return
    SectionCard(title = "${month.monthValue}월 근무") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            tally.counts.forEach { c ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ShiftBadge(c.type, size = 22.dp, outlined = c.type.isOff)
                    Spacer(Modifier.width(6.dp))
                    Text("${c.days}번", style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // [STAT-07] Not the 야 badge's count (1.23 점검 중-5): work into 22~06시, 대근 included, under its own name.
        val night: @Composable (Modifier) -> Unit = { StatTile("밤 시간 근무", "${tally.nights}번", it, caption = "22~06시에 걸침 · 대근 포함") }
        val pay: @Composable (Modifier) -> Unit = { StatTile("예상 급여", payTotal?.let { p -> Fmt.won(p) } ?: "-", it, caption = "세전") }
        // [VIS-05] With large text the two tiles stand one under the other: half a 320dp phone cut the caption.
        if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { night(Modifier.fillMaxWidth()); pay(Modifier.fillMaxWidth()) }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { night(Modifier.weight(1f)); pay(Modifier.weight(1f)) }
    }
}

/** [SHFT-03] The month's 교환 and 대근 with the people's names. */
@Composable
private fun SwapCard(snapshot: ScheduleSnapshot, month: YearMonth) {
    val tally = remember(snapshot, month) { ShiftMonth.of(month, snapshot.shiftTypes, snapshot.resolver::resolve) }
    if (tally.swaps.isEmpty()) return
    SectionCard(title = "교환·대근") {
        Text("교환 ${tally.swapped.size}일 · 대근 ${tally.covers.size}번", style = MaterialTheme.typography.titleSmall.merge(NumberStyle))
        tally.swaps.forEach { s ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.shortDate(s.date), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), modifier = Modifier.width(52.dp))
                Text(
                    when {
                        s.cover && s.name.isNotBlank() -> "대근 · ${s.name} 대신"
                        s.cover -> "대근"
                        else -> "${s.name}${if (endsWithBatchim(s.name)) "과" else "와"} 교환"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        if (tally.names.isNotEmpty()) {
            Text(
                tally.names.joinToString(", ") { (n, c) -> if (c > 1) "$n ${c}번" else n },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun endsWithBatchim(word: String): Boolean {
    val c = word.trim().lastOrNull() ?: return false
    return c in '가'..'힣' && (c - '가') % 28 != 0
}

/** [DAYW-02] This month's 야근, 연차 used and left, and the estimated take-home pay. */
@Composable
private fun DayFirstCard(
    calendarId: String,
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    summary: MonthSummary,
    payTotal: Long?,
    profile: PayProfile?,
    columns: Int,
    nonTaxable: Long? = null,
) {
    val context = LocalContext.current
    val prefs = WorkTypePrefs.get(context)
    val choices by prefs.all.collectAsStateWithLifecycle()
    val mode = choices[calendarId]?.taxMode ?: TaxMode.INSURANCE_4
    val today = remember { Today.date() }
    // [LEAVE-01] The 연차 원장 of this calendar.
    val ledger by remember(calendarId) { context.appContainer.calendars.leave(calendarId).summary }
        .collectAsStateWithLifecycle(null)
    val used = remember(snapshot, month) {
        (1..month.lengthOfMonth()).mapNotNull { snapshot.resolver.resolve(month.atDay(it)).leave }.filter { it.kind.annual }.sumOf { it.days }
    }
    val left = ledger?.takeIf { it.lots.isNotEmpty() }?.available
    val takeHome = payTotal?.let { TakeHome.estimate(it, mode, profile?.taxFamily ?: 1, month.atDay(1), profile?.taxChildren ?: 0,
        nonTaxable ?: profile?.let { p -> TakeHome.nonTaxable(p, month) } ?: 0) }
    val overtimeDays = remember(snapshot, month) {
        (1..month.lengthOfMonth()).count { snapshot.resolver.resolve(month.atDay(it)).hasExtraWork }
    }
    SectionCard(title = "${month.monthValue}월 한눈에") {
        val tiles: List<@Composable (Modifier) -> Unit> = listOf(
            { m -> StatTile("야근", HoursFormat.korean(summary.extraMinutes), m, caption = "${overtimeDays}일") },
            { m ->
                StatTile("연차", "${LeaveBook.days(used)} 씀", m,
                    caption = left?.let { "남은 ${LeaveBook.days(it)}" } ?: "입사일을 넣으면 남은 날이 보여요",
                    warning = (left ?: 0.0) < 0)
            },
            { m ->
                StatTile("예상 실수령", takeHome?.let { Fmt.won(it.net) } ?: "-", m,
                    caption = takeHome?.let { "세전 ${Fmt.won(it.gross)} · 참고용" } ?: "급여 계산을 설정하면 보여요")
            },
        )
        tiles.chunked(columns.coerceAtMost(3)).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                row.forEach { it(Modifier.weight(1f)) }
                repeat(columns.coerceAtMost(3) - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        // 1.32 [WTYP-26] (조사 4·8장) 이번 주 52시간 and, for 공무원, 이번 달 시간외 57시간: bars on the first card.
        if (YearMonth.from(today) == month) {
            val week = remember(snapshot, today) { com.bamnat.core.hours.WorkHoursCalculator(snapshot.resolver, java.time.DayOfWeek.MONDAY).week(today) }
            HoursBar("이번 주 근무", week.totalMinutes, com.bamnat.core.hours.HourLimits().legalWeeklyHours)
        }
        com.bamnat.core.work.WorkTypeChecks.capHours(choices[calendarId]?.overtimeCapHours, profile)?.let { cap ->
            HoursBar("${month.monthValue}월 시간외", summary.extraMinutes, cap)
            if (profile?.publicGrade != null) Text(DayWork.PUBLIC_RULE_NOTE, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        }
        TaxModeChips(mode) { prefs.setTaxMode(calendarId, it) }
        if (mode == TaxMode.INSURANCE_4 && profile != null) TaxFamilyRow(calendarId, profile)
        takeHome?.takeIf { it.deductions.isNotEmpty() }?.let { r ->
            Text(
                r.deductions.joinToString(" · ") { (name, v) -> "$name ${Fmt.won(v)}" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            // [PAYX-23] The pension line is this month's pay, the bill last year's.
            if (mode == TaxMode.INSURANCE_4) Text(TakeHome.PENSION_NOTE, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [PAYR-04] 간이세액표's two questions for 근로소득세: 공제대상가족 수 (본인 포함) and 8~20세 자녀 수, kept in the pay
 * setup (so they go into backups with it). Small steppers right under the tax chips; no new menu.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaxFamilyRow(calendarId: String, profile: PayProfile) {
    val store = LocalContext.current.appContainer.calendars.pay(calendarId)
    val scope = rememberCoroutineScope()
    fun set(change: (PayProfile) -> PayProfile) = scope.launch { store.update(change) }
    Column(Modifier.padding(top = 4.dp)) {
        // [UX-08] The app's one stepper; the row puts it under the words on a narrow screen or a big font.
        StepperRow("가족 수", hint = "본인·배우자 포함") {
            CountStepper(profile.taxFamily, { n -> set { it.copy(taxFamily = n, taxChildren = it.taxChildren.coerceAtMost((n - 1).coerceAtLeast(0))) } },
                name = "가족 수", range = 1..20, format = { "${it}명" })
        }
        StepperRow("8~20세 자녀") {
            CountStepper(profile.taxChildren, { n -> set { it.copy(taxChildren = n) } }, name = "8~20세 자녀",
                range = 0..(profile.taxFamily - 1).coerceAtLeast(0), format = { "${it}명" })
        }
        Text("근로소득세는 국세청 간이세액표 값이에요. 가족 수는 본인·배우자를 포함해 세어 주세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [DAYW-02] How tax comes off, for the take-home estimate. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaxModeChips(mode: TaxMode, onChange: (TaxMode) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(TaxMode.INSURANCE_4 to "4대보험", TaxMode.WITHHOLDING_3_3 to "3.3%", TaxMode.NONE to "떼지 않음").forEach { (m, label) ->
            FilterChip(selected = mode == m, onClick = { onChange(m) }, label = { Text(label) })
        }
    }
}

/**
 * [SHFT-04][PAYX-48] 월급날 비교: once the month's 급여 came in (a 급여 line in 돈, around the pay day when one is set), how far it
 * was from the estimate after tax. Always on screen (U9-나: '사용하러 가기' used to land on nothing when no 급여 line was there):
 * before pay is set up it leads to the setup, before the pay comes in it takes the amount right here.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PayGapCard(
    calendarId: String, month: YearMonth, payTotal: Long?, profile: PayProfile?, book: MoneyBook?, style: WorkStyle?,
    nonTaxable: Long? = null, onOpenPay: () -> Unit = {}, onAddPay: (LocalDate, Long) -> Unit = { _, _ -> },
    takeHome: suspend (Long, YearMonth) -> com.bamnat.core.tax.TaxLearner.Forecast? = { _, _ -> null },
) {
    val context = LocalContext.current
    val prefs = WorkTypePrefs.get(context)
    val choices by prefs.all.collectAsStateWithLifecycle()
    // [HRLY-04] A part-timer chose how tax comes off on the pay screen; others choose it on the first card.
    val mode = if (style == WorkStyle.HOURLY) profile?.taxMode ?: TaxMode.NONE else choices[calendarId]?.taxMode ?: TaxMode.INSURANCE_4
    // [TAX-02] 1.25 W·V: with 세금 예측 open, the 예상 is the same 세후 the pay screen shows, learned from the pay that came in.
    val learned by androidx.compose.runtime.produceState<com.bamnat.core.tax.TaxLearner.Forecast?>(null, payTotal, month, book, profile) {
        value = payTotal?.let { runCatching { takeHome(it, month) }.getOrNull() }
    }
    val fromRecords = learned?.basis == com.bamnat.core.tax.TaxLearner.Basis.LEARNED
    val expected = if (fromRecords) learned?.net else payTotal?.let { TakeHome.estimate(it, mode, profile?.taxFamily ?: 1, month.atDay(1), profile?.taxChildren ?: 0,
        nonTaxable ?: profile?.let { p -> TakeHome.nonTaxable(p, month) } ?: 0).net }
    val basisLabel = if (fromRecords) "내 월급 기록으로 맞춘 세후" else mode.gapLabel()
    val payDate = profile?.period?.payDate(month)
    val gap = remember(book, month, expected, profile) { book?.let { PayGaps.of(month, expected, payDate, it.entries) } }
    var adding by remember { mutableStateOf(false) }
    // [PAYX-48] Opened from 기능 찾기, a tip or 새 기능: the card comes into view and, with pay set up, asks the amount.
    val bring = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    com.bamnat.app.ui.guide.FocusedTool("pay.gap", "pay.gap") {
        scope.launch { bring.bringIntoView() }
        if (profile != null && gap == null) adding = true
    }
    val cal = LocalCalendarColors.current
    SectionCard(title = "${month.monthValue}월 월급날 비교", modifier = Modifier.bringIntoViewRequester(bring)) {
        when {
            profile == null || expected == null -> {
                Text("급여 계산을 먼저 맞추면, 들어온 급여와 계산한 급여의 차이를 알려 드려요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = onOpenPay, modifier = Modifier.padding(top = 8.dp)) { Text("급여 계산 맞추기") }
            }
            gap == null -> {
                Text("예상 ${Fmt.won(expected)} ($basisLabel)", style = MaterialTheme.typography.titleMedium.merge(NumberStyle))
                Text("월급이 들어오면 금액을 적어 주세요. 계산과 얼마나 다른지 바로 보여 드려요. 가계부 탭에 ‘급여’로 들어간 것도 똑같이 셈해요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
                Button(onClick = { adding = true }, modifier = Modifier.padding(top = 8.dp)) { Text("들어온 급여 적기") }
            }
            else -> {
                Text(
                    gap.text,
                    style = MaterialTheme.typography.titleMedium,
                    color = when {
                        gap.matches -> MaterialTheme.colorScheme.primary
                        gap.diff < 0 -> cal.warning
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
                Text(
                    "들어온 돈 ${Fmt.won(gap.received)} (${gap.dates.joinToString(", ") { Fmt.shortDate(it) }}) · " +
                        "예상 ${Fmt.won(gap.expected)} ($basisLabel)",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                PayGaps.short(gap)?.let { short ->
                    // D 1.32 [PAYA-04] 덜 받은 돈: the amount free; the 근거표 and the 정리본 PDF are 프로 (PAY_REPORT).
                    Text("덜 받은 돈 ${Fmt.won(short)}", style = MaterialTheme.typography.titleSmall, color = cal.warning,
                        modifier = Modifier.padding(top = 6.dp))
                    Text("명세서와 근무 기록을 맞춰 보세요. 빠진 수당이나 공제가 있을 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val gate by context.appContainer.featureGate.state.collectAsStateWithLifecycle()
                    TextButton(onClick = {
                        if (gate.allows(com.bamnat.app.tier.Feature.PAY_REPORT)) scope.launch {
                            val gross = payTotal ?: return@launch
                            val ded = TakeHome.estimate(gross, mode, profile?.taxFamily ?: 1, month.atDay(1), profile?.taxChildren ?: 0,
                                nonTaxable ?: profile?.let { p -> TakeHome.nonTaxable(p, month) } ?: 0).deductions
                            com.bamnat.app.report.ReportShare.pdf(context, PayGaps.doc(gap, gross, if (fromRecords) emptyList() else ded, basisLabel),
                                "bamnat-덜받은돈-$month.pdf")
                        } else com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.PAY_REPORT)
                    }) {
                        Text("근거표·정리본 PDF")
                        if (gate.locked(com.bamnat.app.tier.Feature.PAY_REPORT)) com.bamnat.app.ui.components.ProBadge(Modifier.padding(start = 4.dp), locked = true)
                    }
                }
                TextButton(onClick = { adding = true }) { Text("더 들어온 급여 적기") }
            }
        }
    }
    if (adding) AddPayDialog(
        start = payDate?.takeIf { !it.isAfter(Today.date()) } ?: Today.date(),
        onDismiss = { adding = false },
        onSave = { date, amount -> onAddPay(date, amount); adding = false },
    )
}

private fun TaxMode.gapLabel(): String = when (this) {
    TaxMode.INSURANCE_4 -> "4대보험·세금 뺀 참고값"
    TaxMode.WITHHOLDING_3_3 -> "3.3% 뺀 값"
    TaxMode.NONE -> "세전"
}

/** [PAYX-48] 들어온 급여: the amount that reached the account and the day, kept as a 급여 line in 돈. */
@Composable
private fun AddPayDialog(start: LocalDate, onDismiss: () -> Unit, onSave: (LocalDate, Long) -> Unit) {
    var amount by remember { mutableStateOf<Long?>(null) }
    var date by remember { mutableStateOf(start) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("들어온 급여 적기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("통장에 실제로 들어온 금액이에요(세금·4대보험 뺀 금액).", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bamnat.app.ui.pay.MoneyField("들어온 급여", amount, onChange = { amount = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { date = date.minusDays(1) }) { Text("‹") }
                    Text(Fmt.shortDate(date), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { date = date.plusDays(1) }, enabled = date.isBefore(Today.date())) { Text("›") }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(date, amount!!) }, enabled = (amount ?: 0) > 0) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** 1.32 [WTYP-26] "n / 52시간" with a bar, red past the limit. */
@Composable
internal fun HoursBar(label: String, minutes: Int, limitHours: Int) {
    val cal = LocalCalendarColors.current
    val over = minutes > limitHours * 60
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Text("${HoursFormat.korean(minutes)} / ${limitHours}시간", style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                color = if (over) cal.warning else MaterialTheme.colorScheme.onSurface)
        }
        LinearProgressIndicator(
            progress = { (minutes / (limitHours * 60f)).coerceIn(0f, 1f) },
            color = if (over) cal.warning else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
        )
    }
}

/** 1.32 [WTYP-15] 선택근무 정산 기간: worked against 일수 ÷ 7 × 40시간, what is left and per day; set here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SelectiveCard(calendarId: String, snapshot: ScheduleSnapshot) {
    val prefs = WorkTypePrefs.get(LocalContext.current)
    val choices by prefs.all.collectAsStateWithLifecycle()
    val c = choices[calendarId] ?: com.bamnat.app.data.WorkTypeChoices()
    var editing by remember { mutableStateOf(false) }
    val today = remember { Today.date() }
    val cal = LocalCalendarColors.current
    SectionCard(title = "선택근무 정산 기간", trailing = { TextButton(onClick = { editing = true }) { Text(if (c.selectiveMonths == null) "정하기" else "바꾸기") } }) {
        val months = c.selectiveMonths
        if (months == null) {
            Text("선택근무제를 쓰면 정산 기간(1개월·3개월)을 정해 두고 남은 시간과 하루 평균을 봐요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val period = com.bamnat.core.hours.SelectiveWork.period(today, months, c.selectiveStart)
            val p = remember(snapshot, period, today) { com.bamnat.core.hours.SelectiveWork.progress(period, today, snapshot.resolver::resolve) }
            Text("${Fmt.monthDay(period.from)} ~ ${Fmt.monthDay(period.to)} · 기준 ${HoursFormat.korean(period.standardMinutes)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            HoursBar("일한 시간", p.workedMinutes, period.standardMinutes / 60)
            Text(when {
                p.overMinutes > 0 -> "기준보다 ${HoursFormat.korean(p.overMinutes)} 더 일했어요 · 연장근로예요"
                p.perDayLeft != null -> "남은 ${HoursFormat.korean(p.leftMinutes)} · 남은 근무일 ${p.workDaysLeft}일이면 하루 평균 ${HoursFormat.korean(p.perDayLeft!!)}"
                else -> "남은 ${HoursFormat.korean(p.leftMinutes)}"
            }, style = MaterialTheme.typography.bodyMedium, color = if (p.overMinutes > 0) cal.warning else MaterialTheme.colorScheme.onSurface)
            if (p.overMinutes == 0 && p.plannedOverMinutes > 0) Text("근무표대로 하면 기간 끝에 ${HoursFormat.korean(p.plannedOverMinutes)} 넘어요",
                style = MaterialTheme.typography.bodySmall, color = cal.warning)
            Text(com.bamnat.core.hours.SelectiveWork.NOTE, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
    if (editing) {
        var months by remember { mutableStateOf(c.selectiveMonths ?: 1) }
        var start by remember { mutableStateOf(c.selectiveStart) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("선택근무 정산 기간") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        com.bamnat.core.hours.SelectiveWork.MONTHS.forEach { m ->
                            com.bamnat.app.ui.components.FilterChip(selected = months == m, onClick = { months = m },
                                label = { Text(if (m == 1) "1개월 (매달 1일~말일)" else "3개월 (연구개발 등)") })
                        }
                    }
                    if (months == 3) com.bamnat.app.ui.components.CountStepper(start, { start = it }, name = "시작하는 달", range = 1..12,
                        format = { "${it}월 시작" }, valueWidth = 96.dp)
                    Text("근로기준법 제52조: 정산 기간은 1개월 이내, 신상품·신기술 연구개발 업무는 3개월 이내예요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = { TextButton(onClick = { prefs.setSelective(calendarId, months, start); editing = false }) { Text("저장") } },
            dismissButton = { TextButton(onClick = { prefs.setSelective(calendarId, null); editing = false }) { Text("안 써요") } },
        )
    }
}

/** [DAYW-05] This month's extra hours against the monthly 시간외 상한, set here. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OvertimeCapCard(calendarId: String, summary: MonthSummary, profile: PayProfile? = null) {
    val prefs = WorkTypePrefs.get(LocalContext.current)
    val choices by prefs.all.collectAsStateWithLifecycle()
    val capHours = com.bamnat.core.work.WorkTypeChecks.capHours(choices[calendarId]?.overtimeCapHours, profile)
    var editing by remember { mutableStateOf(false) }
    val cal = LocalCalendarColors.current
    SectionCard(title = "시간외 상한", trailing = { TextButton(onClick = { editing = true }) { Text(if (capHours == null) "정하기" else "바꾸기") } }) {
        if (capHours == null) {
            Text("한 달 시간외 상한(예: ${DayWork.DEFAULT_CAP_HOURS}시간)을 정하면 이번 달 남은 시간을 알려 드려요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            val cap = DayWork.OvertimeCap(summary.extraMinutes, capHours)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${HoursFormat.korean(cap.usedMinutes)} / ${capHours}시간", style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                    modifier = Modifier.weight(1f))
                Text(if (cap.over) "${HoursFormat.korean(cap.overMinutes)} 넘음" else "${HoursFormat.korean(cap.leftMinutes)} 남음",
                    style = MaterialTheme.typography.titleSmall.merge(NumberStyle),
                    color = if (cap.over) cal.warning else MaterialTheme.colorScheme.primary)
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { cap.share.toFloat().coerceIn(0f, 1f) },
                color = if (cap.over) cal.warning else MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth(),
            )
            if (cap.over) {
                Spacer(Modifier.height(8.dp))
                WarningBanner("시간외 상한을 넘었어요", "이번 달 시간외가 ${capHours}시간보다 ${HoursFormat.korean(cap.overMinutes)} 많아요. 넘은 시간은 인정되지 않을 수 있어요.")
            }
        }
    }
    if (editing) {
        // [INPUT-02] Tapped from the usual caps or stepped by an hour, not typed.
        var hours by remember { mutableStateOf(capHours ?: DayWork.DEFAULT_CAP_HOURS) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("한 달 시간외 상한") },
            text = {
                Column {
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CAP_CHOICES.forEach { h ->
                            com.bamnat.app.ui.components.FilterChip(selected = hours == h, onClick = { hours = h }, label = { Text("${h}시간") })
                        }
                    }
                    com.bamnat.app.ui.components.CountStepper(hours, { hours = it }, name = "한 달 시간외 상한", range = 1..300,
                        format = { "${it}시간" }, valueWidth = 96.dp)
                    Text("잔업·특근처럼 달력에 넣은 추가 근무를 모두 더해요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    prefs.setOvertimeCap(calendarId, hours.takeIf { it > 0 })
                    editing = false
                }) { Text("저장") }
            },
            dismissButton = {
                TextButton(onClick = {
                    prefs.setOvertimeCap(calendarId, null)
                    editing = false
                }) { Text("상한 없음") }
            },
        )
    }
}

/** [DAYW-04] 징검다리 연휴 in the next 12 months: 연차 1~2일로 길게 쉬는 날. */
@Composable
private fun BridgeCard(snapshot: ScheduleSnapshot, style: WorkStyle?) {
    val today = remember { Today.date() }
    val bridges = remember(snapshot, style, today) { com.bamnat.app.ui.leave.LeaveBridges.of(snapshot, style, today) }
    if (bridges.isEmpty()) return
    SectionCard(title = "징검다리 연휴") {
        bridges.take(8).forEach { b ->
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.shortDate(b.from)}~${Fmt.shortDate(b.to)}" + if (b.holidays.isNotEmpty()) " · ${b.holidays.joinToString("·")}" else "",
                        style = MaterialTheme.typography.bodyLarge.merge(NumberStyle))
                    Text("연차: " + b.leave.joinToString(", ") { "${Fmt.shortDate(it)}(${Fmt.dayOfWeekShort(it.dayOfWeek)})" },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(b.text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(com.bamnat.app.ui.leave.LeaveBridges.caption(style), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
    }
}

/** [INPUT-02] The caps people set most: 주 12시간 × 4주 ≈ 52시간, the app's own 57 and rounder company limits. */
private val CAP_CHOICES = listOf(40, 52, DayWork.DEFAULT_CAP_HOURS, 60)

/** G 1.32 [WTYP-07] 609 포괄임금: 고정 연장수당이 덮는 시간과 이번 달 실제 연장. 넘은 시간은 따로 받을 수 있어요. */
@Composable
private fun CoverHoursCard(c: com.bamnat.core.work.WorkTypeChecks.Cover) {
    val cal = LocalCalendarColors.current
    SectionCard(title = "포괄 연장 시간") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("${HoursFormat.korean(c.usedMinutes)} / ${HoursFormat.korean(c.coveredMinutes)}", style = MaterialTheme.typography.titleMedium.merge(NumberStyle),
                modifier = Modifier.weight(1f))
            Text(if (c.over) "${HoursFormat.korean(c.overMinutes)} 넘음" else "${HoursFormat.korean(c.leftMinutes)} 남음",
                style = MaterialTheme.typography.titleSmall.merge(NumberStyle), color = if (c.over) cal.warning else MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { c.share.toFloat().coerceIn(0f, 1f) }, color = if (c.over) cal.warning else MaterialTheme.colorScheme.primary,
            modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(8.dp))
        Text(if (c.over) "고정 연장수당이 정한 시간보다 더 일했어요. 넘은 시간의 연장수당은 따로 받을 수 있어요 (근로기준법 제56조)."
            else "고정 연장수당에 들어 있는 시간이에요. 이 시간을 넘으면 알려 드려요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** G 1.32 [WTYP-07] 609 근속 기간, 다음 해까지, 퇴직금 1년. */
@Composable
private fun TenureCard(t: com.bamnat.core.work.WorkTypeChecks.Tenure) {
    SectionCard(title = "근속") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t.text, style = MaterialTheme.typography.titleMedium.merge(NumberStyle), modifier = Modifier.weight(1f))
            Text("입사 ${t.hire.year}.${t.hire.monthValue}.${t.hire.dayOfMonth}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(t.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
