package com.bamnat.app.ui.pay

import androidx.compose.material.icons.filled.Close
import com.bamnat.core.pay.Unknowns
import java.time.LocalDate
import com.bamnat.core.pay.PayScale
import com.bamnat.app.ui.components.labelWidth
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.Fmt
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.EditScope
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.OrdinaryGuess
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayLine
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RaiseRule
import com.bamnat.core.pay.Rounding
import java.time.YearMonth
import kotlin.math.abs
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role

/** A won amount typed as digits; shows the grouped value underneath so long numbers stay readable. */
@Composable
internal fun MoneyField(
    label: String,
    value: Long?,
    onChange: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    /** [J133-80] The keypad's 완료: closes the keypad and, when given, goes on (the setup's 다음). */
    onDone: (() -> Unit)? = null,
    /** 1.36 PC Y17: what is still missing, in red under the field (the setup's 다음 tapped with it empty). */
    error: String? = null,
) {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    // [PAYIN-02] 1.37 PC 5566ff20 (칸을 눌러 2,600,000 뒤에 250을 쳐서 26억이 경고 없이 저장됨): a tap on the field selects what is
    // in it, so typing replaces it; an amount over 1억 원 says so in red under the field (it can still be kept).
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(value?.toString() ?: "")) }
    if ((field.text.toLongOrNull()) != value) field = androidx.compose.ui.text.input.TextFieldValue(value?.toString() ?: "", androidx.compose.ui.text.TextRange((value?.toString() ?: "").length))
    // PC 3e347ec6 (누를 때 전체 선택이 안 돼 200,000에 150000을 치니 1,500,020,000): the tap that focuses the field moves the cursor
    // after the focus, sometimes more than once. For a short while after the focus every cursor-only move is taken back to
    // all-selected, and once more after the tap has settled; a later tap places the cursor as usual.
    var focusedAt by remember { mutableStateOf(0L) }
    fun all() { field = field.copy(selection = androidx.compose.ui.text.TextRange(0, field.text.length)) }
    androidx.compose.runtime.LaunchedEffect(focusedAt) {
        if (focusedAt == 0L) return@LaunchedEffect
        kotlinx.coroutines.delay(SELECT_ALL_MS)
        if (field.selection.collapsed) all()
    }
    val huge = value != null && value >= HUGE_WON
    OutlinedTextField(
        isError = error != null || huge,
        value = field,
        onValueChange = { v ->
            if (v.text == field.text && focusedAt != 0L && android.os.SystemClock.uptimeMillis() - focusedAt < SELECT_ALL_MS) { all(); return@OutlinedTextField }
            val digits = v.text.filter(Char::isDigit).take(10)
            field = v.copy(text = digits, selection = if (v.text == digits) v.selection else androidx.compose.ui.text.TextRange(digits.length))
            onChange(digits.toLongOrNull())
        },
        label = { Text(label) },
        placeholder = if (placeholder.isNotEmpty()) ({ Text(placeholder) }) else null,
        suffix = { Text("원") },
        // [PAYS-04] Read back in words (250만 8천원) so a missing or extra zero shows at a glance.
        supportingText = error?.let { e -> { Text(e) } }
            ?: value?.let { { Text("${won(it)} · ${com.bamnat.core.pay.wonWords(it)}" + if (huge) " · 1억 원이 넘어요. 0이 더 들어가지 않았는지 봐 주세요" else "") } },
        singleLine = true,
        visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { keyboard?.hide(); onDone?.invoke() }),
        modifier = modifier.fillMaxWidth().onFocusChanged { f ->
            if (f.isFocused && focusedAt == 0L) { focusedAt = android.os.SystemClock.uptimeMillis(); all() }
            else if (!f.isFocused) focusedAt = 0L
        },
    )
}

/** [PAYIN-02] A pay amount from this much on is said to be checked (one 0 too many turns 260만 into 2,600만, 2억 6천만…). */
internal const val HUGE_WON = 100_000_000L

/**
 * [PAYIN-02] PC 3e347ec6 (1억 경고가 떠도 '적용'하면 15억이 그대로 저장됨): a save with an amount from 1억 원 on asks once more first.
 * [run] goes straight on under 1억 원.
 */
@androidx.compose.runtime.Stable
internal class HugeCheck {
    internal var asking by mutableStateOf<Pair<Long, () -> Unit>?>(null)
    fun run(vararg amounts: Long?, go: () -> Unit) {
        val big = amounts.filterNotNull().filter { it >= HUGE_WON }.maxOrNull()
        if (big == null) go() else asking = big to go
    }
}

@Composable
internal fun rememberHugeCheck(): HugeCheck {
    val c = remember { HugeCheck() }
    c.asking?.let { (amount, go) ->
        AlertDialog(
            onDismissRequest = { c.asking = null },
            title = { Text("${won(amount)}이 맞나요?") },
            text = { Text("${com.bamnat.core.pay.wonWords(amount)}이에요. 1억 원이 넘어서 한 번 더 여쭤봐요. 0이 더 들어갔다면 ‘고치기’를 눌러 주세요.") },
            confirmButton = { TextButton(onClick = { c.asking = null; go() }) { Text("맞아요, 저장") } },
            dismissButton = { TextButton(onClick = { c.asking = null }) { Text("고치기") } },
        )
    }
    return c
}

/** How long after a tap focuses a [MoneyField] its cursor moves are kept as all-selected. */
private const val SELECT_ALL_MS = 400L

/**
 * [PAYX-23][INPUT-02] 월 소정근로시간 for a company that uses e.g. 182 or 195 hours: the one stepper, 100~320 hours, held
 * to run; tapping the value gives an exact entry. Replaces the − + buttons and the typed field next to the chips.
 */
@Composable
internal fun MonthlyHoursStepper(hours: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    com.bamnat.app.ui.components.CountStepper(hours, onChange, name = "월 소정근로시간", modifier = modifier, range = 100..320,
        format = { "${it}시간" }, valueWidth = 96.dp)
}

/** [PAYX-25] An amount in won typed in, e.g. 작년 총급여; empty is null. */
@Composable
internal fun WonField(label: String, value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier, hint: String? = null) {
    var text by remember(value) { mutableStateOf(value?.toString().orEmpty()) }
    OutlinedTextField(
        value = text,
        onValueChange = { s ->
            text = s.filter(Char::isDigit).take(11)
            onChange(text.toLongOrNull())
        },
        label = { Text(label) },
        suffix = { Text("원") },
        supportingText = { Text(text.toLongOrNull()?.let { won(it) } ?: hint.orEmpty()) },
        singleLine = true,
        visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.fillMaxWidth(),
    )
}

@Composable
private fun Fact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.labelWidth(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

/** 계산 풀이 for one line [PAYX-05]: formula, minutes, hourly rate, multiplier, legal basis, rate version. */
@Composable
fun LineDetailDialog(
    line: PayLine, result: MonthPay,
    /** [PAYS-20] 1.27: the value behind the line fixed from here (이 값 고치기, 계산 방식 고치기). */
    onFix: (() -> Unit)? = null, fixLabel: String = "이 값 고치기",
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(line.label) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(won(line.amount), style = MaterialTheme.typography.headlineSmall, color = kindColor(line.kind))
                Text(line.formula, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 8.dp))
                HorizontalDivider()
                if (line.minutes > 0) Fact("시간", PayCalculator.hours(line.minutes))
                if (line.hourly > 0) Fact("기준 시급", won(line.hourly) + if (line.hourly == result.ordinaryHourly) " (통상시급)" else "")
                if (line.multiplier > 0) Fact("배율", PayCalculator.pct(line.multiplier))
                if (line.basis.isNotEmpty()) Fact("근거", line.basis)
                Fact("요율 기준", result.ratesVersion)
                if (line.estimated) {
                    Text("입력하지 않은 값을 앱이 짐작해 넣은 부분이 있어요. ‘내 급여 값’에서 실제 값으로 바꾸면 정확해져요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp))
                }
                // 1.36 PC 급여 검토 W5: the days this line came from, so "연장 60시간" can be checked day by day.
                val from = remember(line, result) {
                    result.days.mapNotNull { d ->
                        val parts = d.parts.filter { it.kind == line.kind && (it.label == line.label || line.kind != com.bamnat.core.pay.LineKind.ALLOWANCE) }
                        parts.sumOf { it.amount }.takeIf { parts.isNotEmpty() }?.let { d to it }
                    }
                }
                if (from.isNotEmpty()) {
                    HorizontalDivider(Modifier.padding(top = 8.dp))
                    Text("이 항목이 붙은 날 ${from.size}일", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
                    from.forEach { (d, amount) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text("${d.date.monthValue}/${d.date.dayOfMonth} ${(com.bamnat.app.ui.Fmt.dayOfWeekShort(d.date.dayOfWeek))} · ${d.label}" + (d.holidayName?.let { " · $it" } ?: ""),
                                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                            Text(won(amount), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        dismissButton = onFix?.let { f -> { TextButton(onClick = f) { Text(fixLabel) } } },
    )
}

// A 1.32 [FIX132-03] 모든 달 first and picked: 1.31's '이 달부터' left the months before on the old value.
private enum class ScopeKind(val label: String) { ALL("모든 달 같게"), FROM("이 달부터 계속"), ONLY("이 달만"), RANGE("기간 지정") }

/**
 * Changes a value with a scope and shows what it does to the next 12 months before applying
 * [PAYX-03]. Applying can be undone in one tap on the result screen.
 */
@Composable
fun EditValueDialog(
    vm: PayViewModel,
    profile: PayProfile,
    snapshot: ScheduleSnapshot,
    target: ValueTarget,
    month: YearMonth,
    onDismiss: () -> Unit,
    /** [MON-01] 기간 지정 is 값 일괄 적용, a pro feature; locked, it opens the 프로 screen. */
    rangeOpen: Boolean = true,
) {
    val timeline = remember(profile, target) { vm.timelineOf(profile, target) }
    val current = timeline.on(month).value
    var value by remember { mutableStateOf<Long?>(current) }
    var scope by remember { mutableStateOf(ScopeKind.ALL) }
    var until by remember { mutableStateOf(month.plusMonths(2)) }
    val editScope = when (scope) {
        ScopeKind.ALL -> EditScope.AllMonths
        ScopeKind.ONLY -> EditScope.OnlyMonth(month)
        ScopeKind.FROM -> EditScope.FromMonth(month)
        ScopeKind.RANGE -> EditScope.Range(month, maxOf(month, until))
    }
    val v = value
    // [QA-01] A plan recalculates a year of months; wait for typing to pause and work off the main thread.
    val plan by androidx.compose.runtime.produceState<com.bamnat.core.pay.EditPlan?>(null, v, editScope, profile) {
        this.value = null // never apply a plan for a number that has since changed
        kotlinx.coroutines.delay(250)
        this.value = v?.let { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { vm.planEdit(profile, snapshot, target, it, editScope, month) } }
    }
    val huge = rememberHugeCheck()
    val name = when (target) {
        ValueTarget.Base -> "기본 금액"
        is ValueTarget.Item -> profile.items.firstOrNull { it.id == target.id }?.name ?: "수당"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$name 바꾸기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${month.monthValue}월 지금 값: ${won(current)}", style = MaterialTheme.typography.bodyMedium)
                MoneyField("새 금액", value, { value = it }, Modifier.padding(top = 8.dp))
                Text("어디에 적용할까요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                ScopeKind.entries.forEach { k ->
                    val locked = k == ScopeKind.RANGE && !rangeOpen
                    val pick = { if (locked) { onDismiss(); com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.PAY_BULK_EDIT) } else scope = k }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = pick), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = scope == k, onClick = pick)
                        Text(
                            when (k) {
                                ScopeKind.ALL -> "모든 달 같게 (지난달·다음 달 모두, 추천)"
                                ScopeKind.ONLY -> "${month.monthValue}월만 (다음 달부터는 원래대로)"
                                ScopeKind.FROM -> "${month.monthValue}월부터 계속 (오른 달처럼 그 전 달은 예전 값 그대로)"
                                ScopeKind.RANGE -> "기간 지정"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (k == ScopeKind.RANGE) com.bamnat.app.ui.money.ProPill(Modifier.padding(start = 8.dp), locked = locked) // [TIER-06]
                    }
                }
                if (scope == ScopeKind.RANGE) {
                    Text("언제까지", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 12.dp))
                    MonthStepper(maxOf(month, until), { until = it }, Modifier.padding(start = 12.dp))
                }
                plan?.let { p ->
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    val changed = p.changedMonths
                    Text(
                        // PC 3e347ec6: '그대로예요' under a new amount read as if it had not been taken.
                        if (v == current && scope == ScopeKind.ALL && timeline.entries.size <= 1) "지금 값과 같아요"
                        else if (changed.isEmpty()) "앞으로 12개월 합계는 그대로예요 (이 값이 쓰이는 근무가 없는 달이거나 마감한 달이에요)"
                        else "${changed.size}개월이 바뀌어요 · 합계 ${signed(p.totalDelta)}",
                        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
                    )
                    Column(Modifier.heightIn(max = 220.dp).verticalScroll(rememberScrollState())) {
                        changed.forEach { d ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                Text("${d.month.year % 100}.${d.month.monthValue}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.labelWidth(44.dp))
                                Text("${won(d.before)} → ${won(d.after)}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(if (d.locked) "마감" else signed(d.delta), style = MaterialTheme.typography.bodySmall,
                                    color = if (d.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                    Text("적용한 뒤에도 ‘방금 바꾼 것 되돌리기’로 한 번에 되돌릴 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            Button(onClick = { plan?.let { p -> huge.run(v) { vm.applyEdit(target, p); onDismiss() } } }, enabled = plan != null) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

private fun signed(v: Long) = (if (v > 0) "+" else if (v < 0) "−" else "") + won(abs(v))

/** 연도별 인상 규칙 [PAYX-02]: month, percent or amount, rounding unit; previews three years. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RaiseDialog(vm: PayViewModel, profile: PayProfile, target: ValueTarget, onDismiss: () -> Unit) {
    val timeline = remember(profile, target) { vm.timelineOf(profile, target) }
    val existing = timeline.raise
    var month by remember { mutableStateOf(existing?.month ?: 1) }
    var byPercent by remember { mutableStateOf(existing?.amount == null) }
    var text by remember { mutableStateOf(existing?.let { it.percent?.toString() ?: it.amount?.toString() } ?: "") }
    var unit by remember { mutableStateOf(existing?.unit ?: 10L) }
    var rounding by remember { mutableStateOf(existing?.rounding ?: Rounding.UP) }
    var scale by remember { mutableStateOf(false) }
    if (scale) {
        PayScaleDialog(vm, profile, onDismiss = { scale = false; onDismiss() })
        return
    }
    val rule = runCatching {
        val n = text.toDouble()
        require(n > 0)
        if (byPercent) RaiseRule(month, percent = n, unit = unit, rounding = rounding)
        else RaiseRule(month, amount = n.toLong(), unit = unit, rounding = rounding)
    }.getOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("매년 인상 규칙") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("입력한 값이 없는 해는 이 규칙으로 올려서 계산해요. 실제로 받은 금액을 입력하면 그 값이 우선이에요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [PAYX-20] A company with a 호봉표 raises by step, not by a rate.
                if (target == ValueTarget.Base) OutlinedButton(onClick = { scale = true }) { Text("호봉표로 넣기") }
                Text("인상되는 달", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..12).forEach { m -> FilterChip(selected = month == m, onClick = { month = m }, label = { Text("${m}월") }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = byPercent, onClick = { if (!byPercent) text = ""; byPercent = true }, label = { Text("비율(%)") })
                    FilterChip(selected = !byPercent, onClick = { if (byPercent) text = ""; byPercent = false }, label = { Text("금액(원)") })
                }
                // [INPUT-02] A rate is tapped (the usual raises) or stepped by 0.1%; an amount in won stays on the number pad.
                if (byPercent) com.bamnat.app.ui.components.QuickDecimalChips(
                    value = text.toDoubleOrNull(), onChange = { text = com.bamnat.app.ui.components.decimalText(it) }, name = "인상률",
                    quick = listOf(2.0, 3.0, 3.5, 4.0, 5.0), step = 0.1, range = 0.1..30.0, unit = "%", start = 3.0,
                ) else OutlinedTextField(
                    value = text,
                    onValueChange = { s -> text = s.filter { it.isDigit() }.take(9) },
                    label = { Text("인상 금액") },
                    suffix = { Text("원") },
                    singleLine = true,
                    visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("끝자리 처리", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(1L, 10L, 100L, 1000L, 10000L).forEach { u -> FilterChip(selected = unit == u, onClick = { unit = u }, label = { Text(won(u)) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Rounding.entries.forEach { r ->
                        FilterChip(selected = rounding == r, onClick = { rounding = r },
                            label = { Text(when (r) { Rounding.UP -> "올림"; Rounding.NEAREST -> "반올림"; Rounding.DOWN -> "버림" }) })
                    }
                }
                rule?.let { r ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(r.describe(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    val t = timeline.copy(raise = r)
                    val start = Today.month()
                    (0..3).forEach { y ->
                        val m = YearMonth.of(start.year + y, r.month).let { if (it < start) start else it }
                        Fact(Fmt.month(m), won(t.on(m).value))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { vm.setRaise(target, rule); onDismiss() }, enabled = rule != null) { Text("적용") } },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { vm.setRaise(target, null); onDismiss() }) { Text("규칙 없애기") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}

/**
 * Fills values from pay rules written in words [PAYX-04][PAYS-06]: typed, pasted or read from a photo. Each
 * sentence becomes a change shown next to it; nothing changes until the user ticks and applies, from [month] on.
 */
@Composable
fun RuleImportDialog(vm: PayViewModel, month: YearMonth, onDismiss: () -> Unit) {
    val snapshot by vm.snapshot.collectAsState()
    val settings by vm.settings.collectAsState()
    val profile by vm.profile.collectAsState()
    var from by remember { mutableStateOf(month) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("규칙을 말로 적기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("취업규칙, 근로계약서, 동료에게 들은 말 그대로 적어도 돼요. 정해진 방식이 아니어도 규칙대로 계산해요. 휴대폰 안에서만 읽어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("언제부터 적용할까요?", style = MaterialTheme.typography.titleSmall)
                MonthStepper(from, { from = it })
                RuleSentenceBox(settings.extraKinds.kinds, snapshot?.shiftTypes.orEmpty(), profile?.fiveOrMoreEmployees ?: true) { changes ->
                    vm.applyRuleChanges(changes, from)
                    onDismiss()
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/**
 * A bonus [PAY-05]: paid in chosen months, as a percent of 기본급 or a fixed amount. Counted in
 * 통상임금 by default, since a regular bonus is 통상임금 even with an employment condition
 * (대법원 2024.12.19. 2020다247190 전원합의체).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddBonusDialog(
    onDismiss: () -> Unit,
    onAdd: (String, Set<Int>, Double?, Long, Boolean, Set<com.bamnat.core.pay.Festival>, Boolean) -> Unit,
) {
    var name by remember { mutableStateOf("상여금") }
    var byPercent by remember { mutableStateOf(true) }
    var percent by remember { mutableStateOf(100) }
    // A yearly total such as 상여 800% is split over the chosen months.
    var annual by remember { mutableStateOf<Int?>(null) }
    var amount by remember { mutableStateOf<Long?>(null) }
    var months by remember { mutableStateOf(setOf(1, 4, 7, 10)) }
    var ordinary by remember { mutableStateOf(true) }
    // [PAYX-10] 명절상여 follows 설날·추석, whose months move every year.
    var festivals by remember { mutableStateOf(emptySet<com.bamnat.core.pay.Festival>()) }
    var festivalBefore by remember { mutableStateOf(false) }
    val times = months.size + festivals.size
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("상여금 추가") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it.take(20) }, label = { Text("이름") },
                    singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("한 번에 얼마?", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = byPercent, onClick = { byPercent = true }, label = { Text("기본급의 %") })
                    FilterChip(selected = !byPercent, onClick = { byPercent = false }, label = { Text("정해진 금액") })
                }
                if (byPercent) {
                    Text("연 합계로", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(400, 600, 800, 1000).forEach { a ->
                            FilterChip(selected = annual == a, onClick = { annual = a }, label = { Text("연 $a%") })
                        }
                    }
                    annual?.takeIf { times > 0 }?.let { a ->
                        Text("한 번에 기본급의 ${PayCalculator.pct(a / 100.0 / times)}씩 ${times}번",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Text("한 번에", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(50, 100, 150, 200).forEach { p ->
                            FilterChip(selected = annual == null && percent == p, onClick = { percent = p; annual = null }, label = { Text("$p%") })
                        }
                        // PC 6da49f0b (−/+ 단추가 줄바꿈되면서 '말일'·'-' 글자만 혼자 남음): the stepper keeps together on one line.
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { percent = (percent - 10).coerceAtLeast(10); annual = null }) { Text("−") }
                            Text("$percent%", style = MaterialTheme.typography.titleSmall, modifier = Modifier.align(Alignment.CenterVertically))
                            OutlinedButton(onClick = { percent = (percent + 10).coerceAtMost(1000); annual = null }) { Text("+") }
                        }
                    }
                } else {
                    MoneyField("1회 금액", amount, { amount = it })
                }
                Text("명절상여", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bamnat.core.pay.Festival.entries.forEach { f ->
                        val date = remember(f) { f.date(Today.date().year) }
                        FilterChip(selected = f in festivals, onClick = {
                            festivals = if (f in festivals) festivals - f else festivals + f
                            if (festivals.isNotEmpty() && name == "상여금") name = "명절상여"
                        }, label = { Text(f.label + (date?.let { " (올해 ${it.monthValue}/${it.dayOfMonth})" } ?: "")) })
                    }
                }
                if (festivals.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = !festivalBefore, onClick = { festivalBefore = false }, label = { Text("명절 있는 달") })
                        FilterChip(selected = festivalBefore, onClick = { festivalBefore = true }, label = { Text("명절 전 달") })
                    }
                    Text("음력이라 해마다 받는 달이 바뀌어요. 달력에 맞춰 자동으로 계산해요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(if (festivals.isEmpty()) "받는 달" else "그 밖에 받는 달 (없으면 비워 두세요)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("분기 (1·4·7·10월)" to setOf(1, 4, 7, 10), "짝수 달" to setOf(2, 4, 6, 8, 10, 12),
                        "매달" to (1..12).toSet(), "반기 (6·12월)" to setOf(6, 12)).forEach { (label, set) ->
                        FilterChip(selected = months == set, onClick = { months = set }, label = { Text(label) })
                    }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..12).forEach { m ->
                        FilterChip(selected = m in months, onClick = { months = if (m in months) months - m else months + m },
                            label = { Text("${m}월") })
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { ordinary = !ordinary }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("통상임금에 포함", style = MaterialTheme.typography.bodyLarge)
                        Text("정해진 조건으로 정기적으로 받는 상여는 재직 조건이 있어도 통상임금이에요(대법원 2024.12.19. 판결). 성과에 따라 달라지면 꺼 주세요. " +
                            "성과급에 꼭 주는 최소 금액이 있으면 그 금액만 따로 상여로 넣고 켜요(고용노동부 통상임금 지침 2025.2.6.).",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = ordinary, onCheckedChange = { ordinary = it })
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onAdd(name, months, if (byPercent) (annual?.let { it / 100.0 / times } ?: (percent / 100.0)) else null,
                        if (byPercent) 0 else amount ?: 0, ordinary, festivals, festivalBefore)
                },
                enabled = times > 0 && (byPercent || (amount ?: 0) > 0),
            ) { Text("추가") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [PAYX-20][PAYX-47] 호봉표: the company's table as rows to fill in (호봉 n · 금액; a row added under the last starts
 * at the last step plus its raise), the step now and the month a step goes up. Pasting the table or reading it from
 * a photo is still there, folded under the rows (계획/1.24-Q-개발계획.md 348). Applied, it becomes the 기본급's dated
 * values from [PayScale.since] on (the table is the raise, so no yearly rule).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PayScaleDialog(vm: PayViewModel, profile: PayProfile, onDismiss: () -> Unit) {
    // 기본급 by row, 1호봉 first; a blank row is null.
    val rows = remember { androidx.compose.runtime.mutableStateListOf<Long?>(null, null) }
    var other by remember { mutableStateOf(false) }
    var text by remember { mutableStateOf("") }
    var stepMonth by remember { mutableStateOf(1) }
    var step by remember { mutableStateOf(1) }
    val since = remember { Today.date().withDayOfMonth(1) }
    val steps = rows.mapIndexedNotNull { i, a -> a?.takeIf { it > 0 }?.let { (i + 1) to it } }.toMap()
    val scale = steps.takeIf { it.isNotEmpty() }?.let {
        runCatching { PayScale(it, stepMonth, step.coerceIn(it.keys.min(), it.keys.max()), since) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("호봉표로 기본급 넣기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("호봉마다 기본급을 넣어요. 아래 줄을 더하면 위 호봉만큼 오른 금액으로 채워 둬요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.forEachIndexed { i, amount ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${i + 1}호봉", style = MaterialTheme.typography.titleSmall, modifier = Modifier.width(64.dp))
                        StepAmountField(i + 1, amount, { rows[i] = it }, Modifier.weight(1f))
                        if (rows.size > 1) androidx.compose.material3.IconButton(onClick = { rows.removeAt(i) }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "${i + 1}호봉 줄 지우기")
                        }
                    }
                }
                if (rows.size < MAX_STEPS) com.bamnat.app.ui.components.AddButton("${rows.size + 1}호봉 더하기", onClick = {
                    rows += PayScale.nextAmount(rows.filterNotNull().filter { it > 0 })
                })
                TextButton(onClick = { other = !other }) { Text(if (other) "사진·글로 넣기 접기" else "사진·글로 넣기") }
                if (other) {
                    var scanned by remember { mutableStateOf(false) }
                    ScanButtons(onText = { t -> text = t; scanned = true })
                    // [PHOTO-09] 1.29
                    if (scanned) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.PAY_TABLE)
                    OutlinedTextField(text, { text = it.take(4000) }, label = { Text("호봉표 붙여 넣기") },
                        placeholder = { Text("1호봉 2,400,000\n2호봉 2,480,000") }, minLines = 3, modifier = Modifier.fillMaxWidth())
                    val read = remember(text) { PayScale.parse(text) }
                    OutlinedButton(enabled = read.isNotEmpty(), onClick = {
                        rows.clear()
                        (1..read.keys.max().coerceAtMost(MAX_STEPS)).forEach { n -> rows += read[n] }
                        other = false
                    }) { Text(if (read.isEmpty()) "읽을 호봉이 없어요" else "${read.size}개 호봉을 줄에 채우기") }
                }
                if (steps.isNotEmpty()) {
                    Text("지금 호봉", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        steps.keys.sorted().forEach { n -> FilterChip(selected = step == n, onClick = { step = n }, label = { Text("${n}호봉") }) }
                    }
                }
                Text("승급하는 달", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..12).forEach { m -> FilterChip(selected = stepMonth == m, onClick = { stepMonth = m }, label = { Text("${m}월") }) }
                }
                scale?.let { sc ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text(sc.describe(), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    sc.values(since.plusYears(4)).take(5).forEach { v -> Fact(Fmt.month(YearMonth.from(v.from)), won(v.value)) }
                }
            }
        },
        confirmButton = {
            Button(enabled = scale != null, onClick = {
                scale?.let { sc -> vm.update { p -> p.copy(base = sc.applyTo(p.base, since.plusYears(40)), estimatedFields = p.estimatedFields - Unknowns.BASE) } }
                onDismiss()
            }) { Text("적용") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** Tables past this are not 호봉표 any more (공무원 봉급표 goes to 32). */
private const val MAX_STEPS = 40

/** [PAYX-47] One 호봉's 기본급: digits on the number pad, shown grouped (2,480,000) with no line under it. */
@Composable
private fun StepAmountField(n: Int, value: Long?, onChange: (Long?) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value?.toString() ?: "",
        onValueChange = { s -> onChange(s.filter(Char::isDigit).take(10).toLongOrNull()) },
        placeholder = { Text("기본급") },
        suffix = { Text("원") },
        singleLine = true,
        visualTransformation = com.bamnat.app.ui.money.ThousandsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier.semantics { contentDescription = "${n}호봉 기본급" },
    )
}
