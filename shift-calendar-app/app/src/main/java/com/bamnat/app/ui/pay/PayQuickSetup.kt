package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.appViewModel
import com.bamnat.app.ui.company.HowAdopted
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicPay
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayRange
import com.bamnat.core.pay.PayStory
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.RuleChange
import com.bamnat.core.pay.SlipRulesResult
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import com.bamnat.core.time.Today
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.YearMonth
import com.bamnat.core.pay.taxKnown

private const val COMPANY = "company"
private const val FORM = "form"
private const val AMOUNT = "amount"
private const val HOLIDAY = "holiday"
private const val CONFIRM = "confirm"

/**
 * [PAYS-12] 급여 간편 설정 (U22): how the user works, how much, the 빨간날 question for shift work, then one page that says
 * in sentences how the month is worked out and what it comes to. Anything the user does not know comes from a payslip
 * and the company's calculation sheet (two photos, [SlipRulesDialog]); the question-by-question setup ([PayWizard])
 * is behind 자세히 고치기. A setup made before keeps every value it holds (D70).
 */
@Composable
fun PayQuickSetup(
    initial: PayProfile?,
    snapshot: ScheduleSnapshot,
    /** The month a setup comes to, off the main thread. */
    preview: suspend (PayProfile) -> PayRange,
    /** [PAYS-14] 명세서 (+회사 계산 방법) → rules on top of a setup, off the main thread. */
    slipRules: suspend (PayProfile, PayslipRead, List<RuleChange>) -> SlipRulesResult?,
    /** The setup to keep, and whether a payslip filled it. */
    onDone: (PayProfile, Boolean) -> Unit,
    /** 자세히 고치기: the question-by-question setup, starting from what is here so far. */
    onDetailed: (PayProfile?) -> Unit,
    onCancel: (() -> Unit)?,
    /** Screenshots and tests only: the step to open. */
    startAt: String? = null,
    /** Screenshots and tests only: a form already picked. */
    startForm: WorkForm? = null,
    /**
     * [SPAT-04] The public cycle just put on the calendar, with its order: its form picked and its 업종's usual allowances
     * in, marked 확인 필요, for a first pay setup.
     */
    fromCycle: Pair<PublicCycle, String>? = null,
    /**
     * [PLAZA-16] 1.27 AH: 우리 회사 급여 방식 찾기, on the first question (main calendar only). [PAYF-01] 1.29 AP: the first
     * question itself, with the box to type the company in (1.27 had a button among the forms that few saw).
     */
    companyFind: Boolean = false,
    /**
     * [PAYG-01] 1.30 BA: a company's way went in with its 기본급 from the first question: the pay is kept already (the way and
     * the base at once), the setup only closes.
     */
    onApplied: () -> Unit = {},
    /** [J133-05] The calendar's kind of work: 알바 starts on 시급 파트타임, 직장인 on 주5일, whatever the shifts look like. */
    style: com.bamnat.core.work.WorkStyle? = null,
) {
    val today = remember { Today.date() }
    val thisMonth = YearMonth.from(today)
    val pattern = remember(snapshot) { PatternTimeline.current(snapshot.patterns, today) }
    val guessed = remember(snapshot, initial, style) {
        WorkForms.guess(snapshot.shiftTypes, pattern, initial).let { g ->
            when {
                initial != null -> g
                style == com.bamnat.core.work.WorkStyle.HOURLY -> WorkForm.PART_TIME
                style == com.bamnat.core.work.WorkStyle.DAY && g != WorkForm.ON_CALL -> WorkForm.OFFICE
                else -> g
            }
        }
    }
    // [PAYF-01] A public row picked in 급여 방식 찾기 with no pay way of its own starts the setup like a cycle put in from it.
    var foundCycle by remember { mutableStateOf<PublicCycle?>(null) }
    val cycleStart = fromCycle?.takeIf { initial == null } ?: foundCycle?.takeIf { initial == null }?.let { it to it.codes }
    // [PAYG-12] 1.30 BA: 설정 고치기 too takes the form of a public row picked (only the form; the values the setup has stay).
    val cycleForm = cycleStart ?: foundCycle?.let { it to it.codes }
    // [J133-05] 1.33 (보고서 심각 4): the form the calendar points to is picked already, so '다음' is on from the start.
    var form by remember { mutableStateOf(startForm ?: cycleStart?.let { PublicPay.form(it.first, it.second) } ?: guessed) }
    // [J133-04] 1.33 (보고서 C2): 5명 미만 회사 has no 연장·야간·휴일 가산 by law; asked of a part-timer, "모르면 아니요".
    var fiveOrMore by remember { mutableStateOf(initial?.fiveOrMoreEmployees) }
    var amount by remember { mutableStateOf(initial?.base?.on(thisMonth)?.value?.takeIf { it > 0 }) }
    // [PAYF-06] 통상시급 기준 시간, said only when the user knows it; null keeps what the setup has.
    var hours by remember { mutableStateOf<Int?>(null) }
    // [PAYG-06] Opens on the company the user works for: "쓰고 있어요", its way at once, or 직접 만들기 when it has none.
    val payFind = com.bamnat.app.ui.company.rememberPayFindState(usingStep = initial != null)
    var holiday by remember { mutableStateOf(initial?.rules?.holidayWork) }
    // [J133-01] 1.33 (보고서 A3, 사용자 승인 2026-10-29): the tax way on the last page, 4대보험·소득세 picked unless set before.
    // [J133-59] 1.33 (중간 빌드 확인): a part-timer starts on 떼지 않음 (주 15시간·월 60시간 미만은 4대보험을 떼지 않는 곳이 많아요)
    // until the user picks; a pick stays whatever form comes next.
    var taxPicked by remember { mutableStateOf(initial?.takeIf { it.taxKnown }?.taxMode) }
    val tax = taxPicked ?: if (form?.wageType == WageType.HOURLY) com.bamnat.core.pay.TaxMode.NONE else com.bamnat.core.pay.TaxMode.INSURANCE_4
    // A payslip's rules, kept over the form and amount the user picked; picking again starts from the form.
    var fromSlip by remember { mutableStateOf<PayProfile?>(null) }
    // [PAYF-07] Opened from a public cycle just put in, the company was already looked up (CompanyPayOffer asked "급여 계산
    // 방식도 같이 넣을까요?" when it had one): not asked again. 설정 고치기 of a pay there is asks it first all the same.
    // [J133-71] 1.33: 알바·현장 and the rest look for no company; only 교대·일근 do.
    val steps = listOfNotNull(COMPANY.takeIf { companyFind && style?.usesCompany != false && (fromCycle == null || initial != null) }, FORM, AMOUNT, HOLIDAY.takeIf { form?.asksHoliday == true }, CONFIRM)
    var step by remember { mutableStateOf(startAt?.takeIf { it in steps } ?: steps.first()) }
    val index = steps.indexOf(step).coerceAtLeast(0)
    val scope = rememberCoroutineScope()
    val container = LocalContext.current.appContainer
    val companyFinder = com.bamnat.app.ui.company.companyScope(container).find

    val since = if (initial != null) thisMonth.atDay(1) else thisMonth.minusMonths(12).atDay(1)
    fun draftOf(f: WorkForm?, a: Long?, h: HolidayWork?): PayProfile? {
        fromSlip?.let { return it }
        val chosen = f ?: return initial
        val p = WorkForms.apply(chosen, a ?: 0, since, initial, h.takeIf { chosen.asksHoliday })
            .let { p -> hours?.takeIf { p.wageType == WageType.MONTHLY }?.let { com.bamnat.core.pay.RuleOnlyNeeds.settled(p.copy(monthlyHours = it, hoursSource = com.bamnat.core.pay.HourlySource.TYPED)) } ?: p }
        return cycleStart?.let { PublicPay.prefill(p, it.first, since) } ?: p
    }
    // [J133-60] 1.33 (중간 빌드 확인: 가계부 '남은 돈'이 세전으로 셈해졌어요): a new setup counts the pay in the 가계부 as what reaches
    // the account on the pay day; one set up before keeps its pick (가계부 수입 내역의 '그달 일한 세전·지급일 실수령').
    val draft = draftOf(form, amount, holiday)?.copy(taxMode = tax, taxChosen = true, ledgerTakeHome = initial?.ledgerTakeHome ?: true)
        ?.let { p -> if (form?.wageType == WageType.HOURLY) p.copy(fiveOrMoreEmployees = fiveOrMore ?: false) else p }
    // The rules as they stand, for the 빨간날 cards: a new setup's first-setup rules, or the ones it has.
    val holidayBase = remember(form, initial) { form?.let { WorkForms.apply(it, 0, since, initial).rules } }

    var slipOpen by remember { mutableStateOf(false) }
    var slipByHand by remember { mutableStateOf(false) }
    if (slipOpen) SlipRulesDialog(
        infer = { read, rules ->
            val f = form ?: guessed ?: WorkForm.OFFICE
            slipRules(WorkForms.apply(f, amount ?: 0, since, initial, holiday.takeIf { f.asksHoliday }), read, rules)
        },
        shiftTypes = snapshot.shiftTypes,
        fiveOrMore = initial?.fiveOrMoreEmployees ?: true,
        byHand = slipByHand,
        onDismiss = { slipOpen = false; slipByHand = false },
        onApply = { r ->
            fromSlip = r.profile
            if (form == null) form = guessed ?: WorkForm.OFFICE
            amount = r.profile.base.on(thisMonth).value
            holiday = r.profile.rules.holidayWork
            slipOpen = false
            slipByHand = false
            step = CONFIRM
        },
    )

    fun go(to: String) { step = to }
    fun next() { steps.getOrNull(index + 1)?.let(::go) }
    fun pick(change: () -> Unit) {
        fromSlip = null
        change()
        scope.launch { delay(220); next() }
    }

    Column(Modifier.fillMaxSize()) {
        LinearProgressIndicator(progress = { (index + 1) / steps.size.toFloat() }, modifier = Modifier.fillMaxWidth())
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp).widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            when (step) {
                // [PAYF-01] 1.29 AP: 우리 회사 급여 방식 찾기 is the first question, the box on it; 직접 만들게요 goes on to the forms.
                COMPANY -> {
                    com.bamnat.app.ui.company.PayFindFlow(
                        payFind,
                        onByHand = { _, publicId ->
                            com.bamnat.core.plaza.PublicCycles.byId(publicId)?.takeIf { fromCycle == null }?.let { row ->
                                foundCycle = row
                                // [PAYG-12] A new setup starts on the row's form; 설정 고치기 is offered it first (FORM below).
                                form = PublicPay.form(row, row.codes)
                            }
                            payFind.step = com.bamnat.app.ui.company.PayFindStep.NAME
                            next()
                        },
                        // [PAYG-01] The way went in with its 기본급, all at once: the pay is kept already, the setup closes.
                        onDone = { payFind.step = com.bamnat.app.ui.company.PayFindStep.NAME; onApplied() },
                        byHandHere = false,
                        onDetailed = { onDetailed(initial) },
                    )
                    // [PAYG-05] 1.30 BA (사용자 "자세히 고치기 자체가 보이지 않고 들어가 보질 못함"): one tap from the first question.
                    if (initial != null && payFind.step == com.bamnat.app.ui.company.PayFindStep.NAME) {
                        TextButton(onClick = { onDetailed(initial) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                            Text("지금 설정을 하나씩 고치려면 자세히 고치기")
                        }
                    }
                }
                FORM -> {
                    StepTitle("어떤 근무로 급여를 받나요?", "고르면 그 근무에서 흔한 수당과 계산 방식을 미리 넣어요. 다른 것은 마지막 한 장에서 고칠 수 있어요.")
                    cycleStart?.let { FromCycleNote(it.first, it.second) }
                    SlipEntry(onByHand = { slipByHand = true; slipOpen = true }) { slipOpen = true }
                    // [SPAT-04] The form of the company's cycle first, when the setup starts from one.
                    val first = cycleForm?.let { PublicPay.form(it.first, it.second) } ?: guessed
                    val ordered = listOfNotNull(first) + WorkForm.entries.filter { it != first }
                    ordered.forEach { f ->
                        val why = when {
                            f != first -> ""
                            cycleForm != null -> " · 회사 근무주기에서 골랐어요"
                            else -> " · 달력을 보면 이것 같아요"
                        }
                        AnswerCard(f.label + why, f.detail, form == f) { pick { form = f } }
                    }
                }
                AMOUNT -> {
                    val wage = form?.wageType ?: WageType.MONTHLY
                    StepTitle(
                        when (wage) { WageType.MONTHLY -> "월 기본급이 얼마예요?"; WageType.HOURLY -> "시급이 얼마예요?"; WageType.DAILY -> "일당이 얼마예요?" },
                        when (wage) {
                            WageType.MONTHLY -> "수당을 빼고 명세서 ‘기본급’ 줄의 금액이에요. 모르면 명세서 사진으로 채워요."
                            WageType.HOURLY -> "계약서나 공고에 적힌 시급이에요."
                            WageType.DAILY -> "하루 일하고 받는 금액이에요."
                        },
                    )
                    // [PAYF-06] The base, and folded under it the 통상시급's hours when the user knows them.
                    BaseFields(wage, amount, onBase = { fromSlip = null; amount = it }, hours = hours, onHours = { fromSlip = null; hours = it },
                        profile = draft ?: initial, month = thisMonth)
                    if (wage == WageType.HOURLY) FiveOrMoreRow(fiveOrMore ?: false) { fromSlip = null; fiveOrMore = it }
                    SlipEntry(onByHand = { slipByHand = true; slipOpen = true }) { slipOpen = true }
                }
                HOLIDAY -> {
                    StepTitle("빨간날(공휴일)에 일하면 어떻게 받나요?", "회사마다 달라요. 모르면 ‘법대로’로 두고 명세서 사진으로 맞춰요.")
                    holidayBase?.let { base ->
                        val rules = holiday?.let { h -> if (base.holidayWork == h) base else base.withHolidayWork(h) } ?: base
                        HolidayWorkPicker(rules, snapshot.shiftTypes, onChange = { r ->
                            val changed = r.holidayWork != holiday
                            fromSlip = null
                            holiday = r.holidayWork
                            if (changed && r.holidayWork != HolidayWork.AS_EXTRA) scope.launch { delay(220); next() }
                        })
                    }
                }
                CONFIRM -> draft?.let { p ->
                    // [PAYF-06] 1.29: a payslip read for its rules only came with no base, and 1.27 saved it as 0원. The base is
                    // asked right here instead, on the page that shows what the month comes to. [PAYG-04] 1.30: the same card
                    // as the pay screen's.
                    if (!com.bamnat.core.pay.BaseAsk.showsEstimate(p, thisMonth)) {
                        BaseNeededCard(p, thisMonth, onSave = { b, h ->
                            if (h != null) hours = h
                            amount = b
                            fromSlip = fromSlip?.let { com.bamnat.core.pay.BaseAsk.apply(it, b, thisMonth, h) }
                        })
                    } else ConfirmPage(p, snapshot, thisMonth, preview, fromSlip != null, onSlip = { slipOpen = true },
                        onDetailed = { onDetailed(p) }, onTax = { taxPicked = it })
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                val finding = step == COMPANY && payFind.step != com.bamnat.app.ui.company.PayFindStep.NAME
                when {
                    // [PAYF-01] Back inside 급여 방식 찾기 goes back to the box, never out of the setup by surprise.
                    finding -> OutlinedButton(onClick = { payFind.step = com.bamnat.app.ui.company.PayFindStep.NAME }) { Text("이전") }
                    index > 0 -> OutlinedButton(onClick = { go(steps[index - 1]) }) { Text("이전") }
                    onCancel != null -> TextButton(onClick = onCancel) { Text("취소") }
                }
                Spacer(Modifier.weight(1f))
                val ready = when (step) {
                    FORM -> form != null
                    AMOUNT -> (amount ?: 0) > 0
                    // [PAYF-06] Never 0원: the base is there before the setup is kept.
                    CONFIRM -> draft?.let { com.bamnat.core.pay.BaseAsk.showsEstimate(it, thisMonth) } == true
                    else -> true
                }
                if (step == COMPANY) {
                    // The box's own way on: no company of ours there, or no wish to look.
                    // [PAYG-10] The calendar on screen's company (1.29 wrote the name into 내 달력's whatever the calendar).
                    if (!finding) Button(onClick = {
                        companyFinder.none(payFind.name)
                        next()
                    }) { Text("직접 만들게요") }
                } else if (step == CONFIRM) com.bamnat.app.ui.components.SaveButton(
                    // [J133-56] The pay screen works the month out next; the button says so at once.
                    onClick = { draft?.let { onDone(it, fromSlip != null) } },
                    enabled = ready && draft != null,
                    busyText = "계산하는 중…",
                ) { Text("이대로 계산") }
                else Button(onClick = { next() }, enabled = ready) { Text("다음") }
            }
        }
    }
}

/** [J133-04] 같이 일하는 사람이 5명 이상인가요? Two chips, 아니요 unless the user knows. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FiveOrMoreRow(yes: Boolean, onChange: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("같이 일하는 사람이 사장님 빼고 5명 이상인가요?", style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.bamnat.app.ui.components.FilterChip(selected = !yes, onClick = { onChange(false) }, label = { Text("아니요 (모르면 이것)") })
            com.bamnat.app.ui.components.FilterChip(selected = yes, onClick = { onChange(true) }, label = { Text("네, 5명 이상") })
        }
        Text(if (yes) "야간(밤 10시~아침 6시)·연장·휴일에 일하면 시급의 1.5배로 계산해요."
            else "5명 미만 가게는 법으로 야간·연장·휴일 가산이 없어서 시급대로 계산해요. 주휴수당은 그대로 받아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [SPAT-04] Why a form is already picked: the company's cycle, and the allowances put in with it. */
@Composable
private fun FromCycleNote(cycle: PublicCycle, codes: String) {
    val items = remember(cycle) { PublicPay.usualItems(cycle) }
    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text(
            "‘${cycle.company}’ 근무주기에 맞춰 ${PublicPay.form(cycle, codes).label}로 골라 두었어요." +
                (if (items.isNotEmpty()) " ${cycle.industry.ifEmpty { "이 일" }}에서 흔한 ${items.joinToString("·")}도 넣어 둘게요. 금액은 ‘확인 필요’로 두니 명세서대로 고쳐요." else ""),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

/** 명세서 사진으로 한 번에: the way for a user who does not know the amounts or the company's rules. */
@Composable
private fun SlipEntry(onByHand: (() -> Unit)? = null, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.ReceiptLong, null, Modifier.size(18.dp))
        Text("  모르겠으면 명세서 사진으로 맞추기")
    }
    // [RULE-ED-01] 1.30 통합: 촬영이 안 되는 회사 — the same 칸 without a photo, one tap from where the photo is offered.
    onByHand?.let { TextButton(onClick = it, modifier = Modifier.fillMaxWidth()) { Text("사진을 찍을 수 없으면 회사 규칙을 칸으로 적기") } }
}

/** [PAYS-12] 이렇게 계산해요: the setup in sentences and this month's estimate, with the ways to change it. */
@Composable
private fun ConfirmPage(
    p: PayProfile,
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    preview: suspend (PayProfile) -> PayRange,
    fromSlip: Boolean,
    onSlip: () -> Unit,
    onDetailed: () -> Unit,
    onTax: (com.bamnat.core.pay.TaxMode) -> Unit = {},
) {
    var range by remember { mutableStateOf<PayRange?>(null) }
    LaunchedEffect(p) {
        range = null
        try {
            range = preview(p)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // No estimate is better than a wrong one; the sentences still say how it is worked out.
        }
    }
    StepTitle("이렇게 계산해요", if (fromSlip) "명세서에서 찾은 회사 방식을 넣었어요. 맞는지 한 번 보고 계산을 시작해요." else "맞으면 ‘이대로 계산’, 다르면 아래에서 고쳐요.")
    val names = remember(snapshot) { snapshot.shiftTypes.associate { it.id to it.name } }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PayStory.sentences(p, month, names).forEach { line ->
            Row { Text("•  ", color = MaterialTheme.colorScheme.primary); Text(line, style = MaterialTheme.typography.bodyMedium) }
        }
    }
    HorizontalDivider()
    Text("${month.monthValue}월 예상", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val r = range
    if (r == null) Row(verticalAlignment = Alignment.CenterVertically) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text("  이번 달을 계산하고 있어요", style = MaterialTheme.typography.bodyMedium)
    } else {
        PayslipPreview(r)
        // [J133-01] 이번 달 통장에 들어올 돈: the 세후 on the first setup's last page, with the way it is worked out.
        val net = remember(r.total, p.taxMode, p.taxFamily, p.taxChildren) {
            com.bamnat.core.pay.TakeHome.estimate(r.total, p.taxMode, p.taxFamily, month.atDay(1), p.taxChildren, com.bamnat.core.pay.TakeHome.nonTaxable(p, month))
        }
        Text("통장에 들어올 돈 (세후 예상)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(com.bamnat.app.ui.Fmt.won(net.net), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        if (net.deductions.isNotEmpty()) Text("떼는 돈 ${com.bamnat.app.ui.Fmt.won(r.total - net.net)} (간이세액표·4대 보험 요율 참고값)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Text("월급에서 떼는 돈", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    com.bamnat.app.tax.TaxWayChips(p.taxMode, onPick = onTax)
    Text(if (p.wageType == WageType.HOURLY && p.taxMode == com.bamnat.core.pay.TaxMode.NONE)
        "알바는 주 15시간·월 60시간 미만이면 떼지 않는 곳이 많아 ‘떼지 않음’으로 골라 뒀어요. 명세서에 4대보험이나 3.3%가 있으면 바꿔 주세요. 나중에 근무·급여에서도 바꿀 수 있어요."
        else "대부분 회사는 4대보험·소득세를 떼요. 프리랜서·알바 중 3.3%를 떼는 곳도 있어요. 나중에 근무·급여에서 바꿀 수 있어요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = onSlip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("명세서·회사 계산 방법 사진으로 맞추기")
    }
    TextButton(onClick = onDetailed, modifier = Modifier.fillMaxWidth()) { Text("자세히 고치기 (수당·계산 방식 하나씩)") }
}

/**
 * [PAYS-14] 명세서 + 회사 계산 방법, two photos read on the phone (사용자 2026-10-25, D68), worked out into the company's rules.
 * The photos go through 묶음 U's [PayslipFillDialog] (READ-08: either photo in either slot); the payslip and the rules then go
 * to [infer] with the month's work.
 * [PHOTO-34] 1.30 AW (사용자 "인식은 되지만 그 이후 입력 방식이 불편함"): no second window "이렇게 바꿀게요" any more. The result's
 * screen shows under the 칸, as they are changed, the payslip's total against the app's and what changes ([infer] a moment after
 * each change, off the main thread); its button applies. [byHand] opens on the 칸 with no photo (촬영이 안 되는 회사).
 */
@Composable
internal fun SlipRulesDialog(
    shiftTypes: List<com.bamnat.core.model.ShiftType>,
    fiveOrMore: Boolean,
    infer: suspend (PayslipRead, List<RuleChange>) -> SlipRulesResult?,
    onDismiss: () -> Unit,
    onApply: (SlipRulesResult) -> Unit,
    kinds: List<com.bamnat.core.model.ExtraKind> = emptyList(),
    /** [RULE-ED-06] The setup and calendar a 칸's own amount is worked out on ("+80,000원 · 4번"); null shows the month's change. */
    profile: PayProfile? = null,
    snapshot: ScheduleSnapshot? = null,
    effectOf: (suspend (PayProfile, ScheduleSnapshot, YearMonth, MadeItem) -> ItemEffect?)? = null,
    /** [RULE-ED-01] Opens on the 칸 with no photo. */
    byHand: Boolean = false,
) {
    var ticked by remember { mutableStateOf(emptyList<RuleChange>()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf<PayslipRead?>(null) }
    val scope = rememberCoroutineScope()
    val plaza = com.bamnat.app.ui.company.rememberCompanyWayText()

    fun apply(r: PayslipRead) {
        busy = true
        scope.launch {
            val res = try { infer(r, ticked) } catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            busy = false
            if (res != null) onApply(res) else failed = r
        }
    }

    // [PAYS-16] 1.27: the company's way of counting alone (no payslip) is enough to work the pay out.
    PayslipFillDialog("회사 계산 방식 찾기", onDismiss = onDismiss, rulesOnly = true,
        rules = RuleTarget(kinds, shiftTypes, fiveOrMore, profile, snapshot, effectOf) { changes, _ -> ticked = changes },
        preview = { r, changes -> infer(r, changes)?.let(::sheetPreviewOf) },
        plaza = plaza, startByHand = byHand,
    ) { r -> apply(r) }
    if (busy) AlertDialog(
        onDismissRequest = {},
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("  근무표와 맞춰 보고 있어요", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {},
    )
    failed?.let { r ->
        AlertDialog(
            onDismissRequest = { failed = null },
            title = { Text("회사 계산 방식 찾기") },
            text = {
                Text(if (r.lines.isEmpty()) "회사 계산 방법에서 넣을 규칙을 찾지 못했어요. ‘야간 50% 가산’처럼 무엇에 얼마인지 보이게 다시 찍거나 ‘칸 더하기’로 넣어 주세요."
                    else "명세서에서 지급 합계를 찾지 못했어요. ‘지급합계’ 줄이 보이게 다시 찍어 주세요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            },
            confirmButton = { TextButton(onClick = { failed = null }) { Text("확인") } },
        )
    }
}

/** [PHOTO-34] What [SlipRulesResult] says, for the bar under the result. */
internal fun sheetPreviewOf(res: SlipRulesResult) = com.bamnat.app.ui.pay.rulesheet.SheetPreview(
    month = res.month, total = res.total,
    hourly = com.bamnat.core.pay.HourlyFacts.of(res.profile, res.month)?.hourly,
    gross = res.gross.takeIf { res.withSlip }, changes = res.changes, hints = res.hints, close = res.close, profile = res.profile,
)

/** What a payslip changed, and how close the month now comes to it. */
@Composable
internal fun SlipRulesSummary(res: SlipRulesResult) {
    // [PAYS-16] Rules alone: the month by the rules and the calendar; nothing to compare yet.
    if (!res.withSlip) {
        Text("${res.month.year}년 ${res.month.monthValue}월 규칙으로 계산 ${won(res.total)}", style = MaterialTheme.typography.titleSmall)
        Text("달력에 넣은 근무와 이 규칙으로 계산해요. 기본급·월 소정근로시간처럼 꼭 필요한 값이 비어 있으면 급여 합계 바로 아래에서 바로 넣을 수 있어요.",
            style = MaterialTheme.typography.bodyMedium)
        res.changes.forEach { Row { Text("•  "); Text(it, style = MaterialTheme.typography.bodyMedium) } }
        res.hints.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        return
    }
    Text("${res.month.year}년 ${res.month.monthValue}월 명세서 ${won(res.gross)} · 앱 계산 ${won(res.total)}",
        style = MaterialTheme.typography.titleSmall)
    Text(
        if (res.close) "거의 같아요(차이 ${won(kotlin.math.abs(res.gap))}). 이 방식으로 다음 달도 계산해요."
        else "아직 ${won(kotlin.math.abs(res.gap))} 차이 나요. 적용한 뒤 ‘자세히 고치기’나 ‘회사 규칙’에서 더 맞출 수 있어요.",
        style = MaterialTheme.typography.bodyMedium,
        color = if (res.close) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        fontWeight = FontWeight.Medium,
    )
    if (res.changes.isEmpty()) Text("바꿀 것이 없어요. 지금 설정이 명세서와 같아요.")
    res.changes.forEach { Row { Text("•  "); Text(it, style = MaterialTheme.typography.bodyMedium) } }
    res.hints.forEach { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/**
 * [PAYS-15] After a payslip set the company's rules (U9-가): ask to put them on 광장 so people at the same company can use
 * them at once, with how it is adopted and what it brings. Only the rules go, no amounts or person (X's report flow).
 */
@Composable
internal fun PlazaAskDialog(onShare: () -> Unit, onDismiss: () -> Unit) {
    // [PLAZA-04] The reward as the server gives it now (rewardLine), the same words as every other plaza invitation.
    val container = LocalContext.current.appContainer
    val terms = if (container.companyApi.enabled)
        appViewModel { com.bamnat.app.ui.company.CompanyNudgeViewModel(it) }.terms.collectAsStateWithLifecycle().value else null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("같은 회사 사람들도 바로 쓰게 해 주실래요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("방금 명세서로 맞춘 계산 방식을 광장에 올리면, 같은 회사 분들은 회사 이름만 찾아서 1분이면 설정이 끝나요. " +
                    "금액과 이름은 올라가지 않아요.", style = MaterialTheme.typography.bodyMedium)
                HowAdopted(terms?.threshold ?: 3, com.bamnat.app.ui.company.rewardLine(terms))
            }
        },
        confirmButton = { Button(onClick = onShare) { Text("광장에 올리기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("나중에") } },
    )
}
