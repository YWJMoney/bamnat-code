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
) {
    val today = remember { Today.date() }
    val thisMonth = YearMonth.from(today)
    val pattern = remember(snapshot) { PatternTimeline.current(snapshot.patterns, today) }
    val guessed = remember(snapshot, initial) { WorkForms.guess(snapshot.shiftTypes, pattern, initial) }
    val cycleStart = fromCycle?.takeIf { initial == null }
    var form by remember { mutableStateOf(startForm ?: cycleStart?.let { PublicPay.form(it.first, it.second) } ?: initial?.let { guessed }) }
    var amount by remember { mutableStateOf(initial?.base?.on(thisMonth)?.value?.takeIf { it > 0 }) }
    var holiday by remember { mutableStateOf(initial?.rules?.holidayWork) }
    // A payslip's rules, kept over the form and amount the user picked; picking again starts from the form.
    var fromSlip by remember { mutableStateOf<PayProfile?>(null) }
    val steps = listOfNotNull(FORM, AMOUNT, HOLIDAY.takeIf { form?.asksHoliday == true }, CONFIRM)
    var step by remember { mutableStateOf(startAt?.takeIf { it in steps } ?: FORM) }
    val index = steps.indexOf(step).coerceAtLeast(0)
    val scope = rememberCoroutineScope()

    val since = if (initial != null) thisMonth.atDay(1) else thisMonth.minusMonths(12).atDay(1)
    fun draftOf(f: WorkForm?, a: Long?, h: HolidayWork?): PayProfile? {
        fromSlip?.let { return it }
        val chosen = f ?: return initial
        val p = WorkForms.apply(chosen, a ?: 0, since, initial, h.takeIf { chosen.asksHoliday })
        return cycleStart?.let { PublicPay.prefill(p, it.first, since) } ?: p
    }
    val draft = draftOf(form, amount, holiday)
    // The rules as they stand, for the 빨간날 cards: a new setup's first-setup rules, or the ones it has.
    val holidayBase = remember(form, initial) { form?.let { WorkForms.apply(it, 0, since, initial).rules } }

    var slipOpen by remember { mutableStateOf(false) }
    if (slipOpen) SlipRulesDialog(
        infer = { read, rules ->
            val f = form ?: guessed ?: WorkForm.OFFICE
            slipRules(WorkForms.apply(f, amount ?: 0, since, initial, holiday.takeIf { f.asksHoliday }), read, rules)
        },
        shiftTypes = snapshot.shiftTypes,
        fiveOrMore = initial?.fiveOrMoreEmployees ?: true,
        onDismiss = { slipOpen = false },
        onApply = { r ->
            fromSlip = r.profile
            if (form == null) form = guessed ?: WorkForm.OFFICE
            amount = r.profile.base.on(thisMonth).value
            holiday = r.profile.rules.holidayWork
            slipOpen = false
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
                FORM -> {
                    StepTitle("어떤 근무로 급여를 받나요?", "고르면 그 근무에서 흔한 수당과 계산 방식을 미리 넣어요. 다른 것은 마지막 한 장에서 고칠 수 있어요.")
                    cycleStart?.let { FromCycleNote(it.first, it.second) }
                    SlipEntry { slipOpen = true }
                    val ordered = listOfNotNull(guessed) + WorkForm.entries.filter { it != guessed }
                    ordered.forEach { f ->
                        AnswerCard(f.label + if (f == guessed) " · 달력을 보면 이것 같아요" else "", f.detail, form == f) { pick { form = f } }
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
                    MoneyField(
                        when (wage) { WageType.MONTHLY -> "월 기본급"; WageType.HOURLY -> "시급"; WageType.DAILY -> "일당" },
                        amount, onChange = { fromSlip = null; amount = it },
                    )
                    SlipEntry { slipOpen = true }
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
                    ConfirmPage(p, snapshot, thisMonth, preview, fromSlip != null, onSlip = { slipOpen = true },
                        onDetailed = { onDetailed(p) })
                }
            }
        }
        Surface(tonalElevation = 3.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                when {
                    index > 0 -> OutlinedButton(onClick = { go(steps[index - 1]) }) { Text("이전") }
                    onCancel != null -> TextButton(onClick = onCancel) { Text("취소") }
                }
                Spacer(Modifier.weight(1f))
                val ready = when (step) {
                    FORM -> form != null
                    AMOUNT -> (amount ?: 0) > 0
                    else -> true
                }
                Button(
                    onClick = { if (step == CONFIRM) draft?.let { onDone(it, fromSlip != null) } else next() },
                    enabled = ready && (step != CONFIRM || draft != null),
                ) { Text(if (step == CONFIRM) "이대로 계산" else "다음") }
            }
        }
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
private fun SlipEntry(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.ReceiptLong, null, Modifier.size(18.dp))
        Text("  모르겠으면 명세서 사진으로 맞추기")
    }
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
    } else PayslipPreview(r)
    OutlinedButton(onClick = onSlip, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("명세서·회사 계산 방법 사진으로 맞추기")
    }
    TextButton(onClick = onDetailed, modifier = Modifier.fillMaxWidth()) { Text("자세히 고치기 (수당·계산 방식 하나씩)") }
}

/**
 * [PAYS-14] 명세서 + 회사 계산 방법, two photos read on the phone (사용자 2026-10-25, D68), worked out into the company's rules.
 * The photos go through 묶음 U's [PayslipFillDialog] (READ-08: either photo in either slot, the rules found ticked); the
 * payslip and the ticked rules then go to [infer] with the month's work, and this shows what changes in words and the
 * payslip's total against the app's before 적용.
 */
@Composable
internal fun SlipRulesDialog(
    shiftTypes: List<com.bamnat.core.model.ShiftType>,
    fiveOrMore: Boolean,
    infer: suspend (PayslipRead, List<RuleChange>) -> SlipRulesResult?,
    onDismiss: () -> Unit,
    onApply: (SlipRulesResult) -> Unit,
    kinds: List<com.bamnat.core.model.ExtraKind> = emptyList(),
) {
    var ticked by remember { mutableStateOf(emptyList<RuleChange>()) }
    var read by remember { mutableStateOf<PayslipRead?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SlipRulesResult?>(null) }
    var failed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun analyse(r: PayslipRead) {
        read = r
        busy = true
        failed = false
        scope.launch {
            try {
                result = infer(r, ticked)
                failed = result == null
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                failed = true
            } finally {
                busy = false
            }
        }
    }

    if (read == null) {
        PayslipFillDialog("명세서로 회사 방식 맞추기", onDismiss = onDismiss,
            rules = RuleTarget(kinds, shiftTypes, fiveOrMore) { changes, _ -> ticked = changes }) { r -> analyse(r) }
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (result != null) "이렇게 바꿀게요" else "명세서로 회사 방식 맞추기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val res = result
                when {
                    res != null -> SlipRulesSummary(res)
                    busy -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text("  근무표와 맞춰 보고 있어요", style = MaterialTheme.typography.bodySmall)
                    }
                    failed -> Text("명세서에서 지급 합계를 찾지 못했어요. ‘지급합계’ 줄이 보이게 다시 찍어 주세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            val res = result
            if (res != null) Button(onClick = { onApply(res) }) { Text("이대로 적용") }
        },
        dismissButton = { TextButton(onClick = { read = null; result = null; ticked = emptyList() }) { Text("다시 하기") } },
    )
}

/** What a payslip changed, and how close the month now comes to it. */
@Composable
internal fun SlipRulesSummary(res: SlipRulesResult) {
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
