package com.bamnat.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.heightIn
import kotlinx.coroutines.launch
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.TimePickerDialog
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobReminders
import java.time.LocalDate
import java.time.LocalTime
import androidx.compose.ui.semantics.Role

/** Most a single line may hold: 100억원, far above any household amount, and safe from overflow. */
// 1.36 실사용 S16 (받을 금액에 900억도 들어가요): up to 99억 원 a line.
private const val MAX_DIGITS = 10

/** Longest job one booking covers; longer work is several bookings (one per stage). */
private const val MAX_JOB_DAYS = 60

/** [JOBDAYS-01] 1.39 A work period this long (or longer) is asked about once before it is saved. */
private const val LONG_JOB_DAYS = 15

/** Whole won typed as digits and shown with thousands separators [LEDG-01]. */
/**
 * 1.38 PC 확인 (대출 더하기 칸 넷이 자판에 가려 다음 칸이 안 보임): when this field takes focus, it is scrolled above the keyboard
 * once the keyboard has come up. For fields in a scrolling sheet.
 */
@Composable
fun Modifier.aboveKeyboard(): Modifier {
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    return this.then(Modifier.bringIntoViewRequester(bring).onFocusEvent { f ->
        if (f.isFocused) scope.launch { kotlinx.coroutines.delay(KEYBOARD_SETTLE_MS); bring.bringIntoView() }
    })
}

private const val KEYBOARD_SETTLE_MS = 350L

@Composable
fun AmountField(
    label: String,
    digits: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    imeAction: ImeAction = ImeAction.Next,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
    /** [SHOP-03] 0 is an answer here (센 현금 0원), not an empty field. */
    allowZero: Boolean = false,
    enabled: Boolean = true,
) {
    // 1.38 PC 8차: a String value lost the cursor (맨 앞으로) whenever the screen put other digits in (지우면 기본값이 돌아올 때),
    // and typing went in front of them (12000 → 1,200,010,320). The field keeps its own cursor; digits from outside put it at the end.
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(digits, androidx.compose.ui.text.TextRange(digits.length))) }
    val shown = if (field.text == digits) field else androidx.compose.ui.text.input.TextFieldValue(digits, androidx.compose.ui.text.TextRange(digits.length))
    OutlinedTextField(
        enabled = enabled,
        value = shown,
        onValueChange = { v ->
            val kept = amountInput(v.text, allowZero)
            field = if (kept == v.text) v else androidx.compose.ui.text.input.TextFieldValue(kept, androidx.compose.ui.text.TextRange(kept.length))
            onChange(kept)
        },
        label = { Text(label) },
        textStyle = textStyle ?: androidx.compose.material3.LocalTextStyle.current,
        suffix = { Text("원") },
        singleLine = true,
        visualTransformation = ThousandsTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 2.0.0 [RVK-15] (검토 U06a-01): an amount box that counts. The calculator at its end opens a row of + − × ÷ = under it (the
 * phone's number pad has none); "36000÷3" shows "= 12,000원" under the box, and the amount is the result from the first sign on,
 * so 저장 never takes a half-typed sum. '=' or leaving the box puts the result in. Without a sum the row stays shut (총괄 #457
 * 리뷰, 조건 1·8: the sheet's usual look is no taller than before), and the box is [AmountField] with one more icon.
 */
@Composable
fun CalcAmountField(
    label: String,
    digits: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    textStyle: androidx.compose.ui.text.TextStyle? = null,
) {
    val calc = com.bamnat.core.money.AmountCalc
    fun valueOf(t: String): String = if (calc.hasOp(t)) calc.eval(t)?.let(::amountDigits).orEmpty() else t
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(digits, androidx.compose.ui.text.TextRange(digits.length))) }
    // What this box last said the amount is: other digits from outside (a chip, +1만) start the box again from them.
    var sent by remember { mutableStateOf(digits) }
    val shown = if (digits == sent) field
        else androidx.compose.ui.text.input.TextFieldValue(digits, androidx.compose.ui.text.TextRange(digits.length))
    val sum = calc.hasOp(shown.text)
    var focused by remember { mutableStateOf(false) }
    // The + − × ÷ = row: opened by the calculator icon, or by a sign typed on a keyboard that has one.
    var calcOpen by rememberSaveable { mutableStateOf(false) }
    fun put(text: String, keepCursor: androidx.compose.ui.text.input.TextFieldValue? = null) {
        val cleaned = calc.clean(text)
        val kept = if (calc.hasOp(cleaned)) cleaned else amountInput(cleaned)
        field = if (keepCursor != null && keepCursor.text == kept) keepCursor
            else androidx.compose.ui.text.input.TextFieldValue(kept, androidx.compose.ui.text.TextRange(kept.length))
        sent = valueOf(kept)
        onChange(sent)
    }
    fun settle() { if (calc.hasOp(shown.text)) put(valueOf(shown.text)) }
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = shown,
            onValueChange = { v -> put(v.text, v) },
            label = { Text(label) },
            textStyle = textStyle ?: androidx.compose.material3.LocalTextStyle.current,
            suffix = { Text("원") },
            trailingIcon = {
                IconButton(onClick = { if (calcOpen || sum) { settle(); calcOpen = false } else calcOpen = true }) {
                    Icon(Icons.Outlined.Calculate, if (calcOpen || sum) "계산 줄 닫기" else "계산하기",
                        tint = if (calcOpen || sum) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            singleLine = true,
            visualTransformation = GroupedSum,
            supportingText = if (sum) {
                {
                    val result = calc.eval(shown.text)
                    Text(if (result != null) "= ${Fmt.won(result)}" else "계산한 값이 0원보다 커야 적을 수 있어요",
                        color = if (result != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = { settle(); defaultKeyboardAction(ImeAction.Next) }),
            modifier = modifier.fillMaxWidth().onFocusChanged { f ->
                if (focused && !f.isFocused) settle()
                focused = f.isFocused
            },
        )
        if (calcOpen || sum) Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val keys = listOf(calc.PLUS to "더하기", calc.MINUS to "빼기", calc.TIMES to "곱하기", calc.DIVIDE to "나누기")
            keys.forEach { (op, said) ->
                com.bamnat.app.ui.components.OutlinedButton(
                    onClick = { if (shown.text.isNotEmpty()) put(shown.text + op) },
                    enabled = shown.text.isNotEmpty(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        .semantics { contentDescription = said },
                ) { Text(op.toString(), style = MaterialTheme.typography.titleLarge) }
            }
            com.bamnat.app.ui.components.OutlinedButton(
                onClick = { settle(); calcOpen = false },
                enabled = sum,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).semantics { contentDescription = "계산 끝내기" },
            ) { Text("=", style = MaterialTheme.typography.titleLarge) }
        }
    }
}

/**
 * 2.0.0 [RVK-13][RVK-16][RVK-18] (총괄 #457 리뷰, 조건 12) Words with their text buttons: side by side when both fit on one line,
 * else the words take the whole width and the buttons go under them on the right, so the words never stand in a thin column
 * beside the buttons (글자 200%, the folded phone) and an amount is not cut in two.
 */
@Composable
internal fun WordsWithActions(words: @Composable () -> Unit, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit) {
    Layout(
        contents = listOf(words, { Row(verticalAlignment = Alignment.CenterVertically, content = actions) }),
        modifier = modifier.fillMaxWidth(),
    ) { (said, buttons), c ->
        val width = c.maxWidth
        val b = buttons.first().measure(Constraints(maxWidth = width))
        val oneLine = said.first().maxIntrinsicWidth(Constraints.Infinity) + b.width <= width
        if (oneLine) {
            val w = said.first().measure(Constraints(maxWidth = width - b.width))
            // A row asked to be at least 48dp tall (a row you press) keeps the two in its middle.
            val h = maxOf(w.height, b.height, c.minHeight)
            layout(width, h) {
                w.place(0, (h - w.height) / 2)
                b.place(width - b.width, (h - b.height) / 2)
            }
        } else {
            val w = said.first().measure(Constraints(maxWidth = width))
            layout(width, w.height + b.height) {
                w.place(0, 0)
                b.place(width - b.width, w.height)
            }
        }
    }
}

/**
 * 2.0.0 [RVK-23]·HQ-30 (총괄 #457 리뷰) A name and its amount: on one line while both fit; at large text the name keeps whole lines
 * and the amount goes under it on the right, so a name is never cut down to its date ('10/14 신한카드 카드값' → '10/14').
 * Every 'name … amount' row of the ledger uses it (앞으로의 돈, 구독, 예산, 카드 실적, 봉투, 기타로 남은 곳).
 */
@Composable
internal fun NameAndAmount(name: @Composable () -> Unit, modifier: Modifier = Modifier, amount: @Composable RowScope.() -> Unit) =
    WordsWithActions(name, modifier) { androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp)); amount() }

/** [RVK-15] Each number of a sum in 1,000 groups as it is typed ("36,000÷3"); a plain amount as [GroupedNumber]. */
private object GroupedSum : VisualTransformation {
    override fun filter(text: androidx.compose.ui.text.AnnotatedString): androidx.compose.ui.text.input.TransformedText {
        val s = text.text
        if (!com.bamnat.core.money.AmountCalc.hasOp(s)) return ThousandsTransformation.filter(text)
        fun digit(k: Int) = s[k] in '0'..'9'
        val runStart = IntArray(s.length)
        val runEnd = IntArray(s.length)
        for (k in s.indices) runStart[k] = if (digit(k) && k > 0 && digit(k - 1)) runStart[k - 1] else k
        for (k in s.indices.reversed()) runEnd[k] = if (digit(k) && k < s.lastIndex && digit(k + 1)) runEnd[k + 1] else k + 1
        val out = StringBuilder(s.length + s.length / 3)
        val toShown = IntArray(s.length + 1)
        val toTyped = ArrayList<Int>(s.length + 8)
        for (k in s.indices) {
            toShown[k] = out.length
            if (digit(k) && k > runStart[k] && (runEnd[k] - k) % 3 == 0) { out.append(','); toTyped.add(k) }
            out.append(s[k]); toTyped.add(k)
        }
        toShown[s.length] = out.length
        toTyped.add(s.length)
        val mapping = object : androidx.compose.ui.text.input.OffsetMapping {
            override fun originalToTransformed(offset: Int): Int = toShown[offset.coerceIn(0, s.length)]
            override fun transformedToOriginal(offset: Int): Int = toTyped[offset.coerceIn(0, toTyped.lastIndex)]
        }
        return androidx.compose.ui.text.input.TransformedText(androidx.compose.ui.text.AnnotatedString(out.toString()), mapping)
    }
}

/** What an amount field keeps of what was typed: digits without leading zeros, a lone 0 only when [allowZero]. */
internal fun amountInput(typed: String, allowZero: Boolean = false): String {
    val d = typed.filter(Char::isDigit)
    return d.trimStart('0').ifEmpty { if (allowZero && d.isNotEmpty()) "0" else "" }.take(MAX_DIGITS)
}

fun amountDigits(amount: Long): String = if (amount <= 0) "" else amount.toString()
fun String.toAmount(): Long = toLongOrNull() ?: 0L

/** Amounts in 1,000 groups as they are typed (now the same as every number box, [com.bamnat.app.ui.components.GroupedNumber]). */
internal val ThousandsTransformation: VisualTransformation = com.bamnat.app.ui.components.GroupedNumber

/** Books or edits a job for field and freelance work [WORK-02]. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobDialog(
    date: LocalDate,
    initial: Job?,
    onSave: (Job) -> Unit,
    onDelete: ((Job) -> Unit)? = null,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title.orEmpty()) }
    var client by rememberSaveable { mutableStateOf(initial?.client.orEmpty()) }
    var place by rememberSaveable { mutableStateOf(initial?.place.orEmpty()) }
    var start by rememberSaveable { mutableStateOf(initial?.start?.toString()) }
    var end by rememberSaveable { mutableStateOf(initial?.end?.toString()) }
    var amount by rememberSaveable { mutableStateOf(amountDigits(initial?.amount ?: 0)) }
    var deposit by rememberSaveable { mutableStateOf(amountDigits(initial?.deposit ?: 0)) }
    var depositPaid by rememberSaveable { mutableStateOf(initial?.depositPaid ?: false) }
    var balancePaid by rememberSaveable { mutableStateOf(initial?.balancePaid ?: false) }
    var remind by rememberSaveable { mutableStateOf(initial?.remindMinutes) }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    var days by rememberSaveable { mutableIntStateOf(initial?.days ?: 1) }
    // [JOBDAYS-01] 1.39 '60일 맞나요?' before a long period the user has just set is saved.
    var askLong by rememberSaveable { mutableStateOf(false) }
    var costs by rememberSaveable { mutableStateOf(amountDigits(initial?.costs ?: 0)) }
    var withholding by rememberSaveable { mutableStateOf(initial?.withholding ?: false) }
    // [FLD-03] 가계약·확정·완료·취소; [FLD-09] the day the money is promised.
    var status by rememberSaveable { mutableStateOf(initial?.status ?: com.bamnat.core.money.JobStatus.CONFIRMED) }
    var due by rememberSaveable { mutableStateOf(initial?.due?.toString()) }
    // 2.0.0 [RVK-42] (검토 T06a-02·U06a-22) The start day can move; the promised payday moves with it by the same days.
    var on by rememberSaveable { mutableStateOf((initial?.date ?: date).toString()) }
    val onDay = LocalDate.parse(on)
    var picking by remember { mutableStateOf<String?>(null) }
    val startTime = start?.let(LocalTime::parse)
    val endTime = end?.let(LocalTime::parse)
    val hasDeposit = deposit.toAmount() > 0

    // [UX-08] A long form with a keyboard: a sheet, not a dialog.
    com.bamnat.app.ui.components.FormSheet(
        onDismissRequest = onDismiss,
        unsaved = title.trim() != initial?.title.orEmpty() || client.trim() != initial?.client.orEmpty() ||
            place.trim() != initial?.place.orEmpty() || amount.toAmount() != (initial?.amount ?: 0) || note.trim() != initial?.note.orEmpty() ||
            costs.toAmount() != (initial?.costs ?: 0) || deposit.toAmount() != (initial?.deposit ?: 0) || onDay != (initial?.date ?: date),
        title = { Text(if (initial == null) "${Fmt.dayTitle(onDay)} 일감" else "일감 고치기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = title, onValueChange = { title = it.take(40) }, singleLine = true,
                    label = { Text("일 내용 (예: 욕실 타일)") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // [FLD-02] Names from past jobs one tap away.
                    ClientField(client, { client = it.take(30) }, Modifier.weight(1f))
                    OutlinedTextField(value = place, onValueChange = { place = it.take(40) }, singleLine = true,
                        label = { Text("장소") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeChip("시작", startTime, Modifier.weight(1f)) { picking = "start" }
                    TimeChip("끝", endTime, Modifier.weight(1f)) { picking = "end" }
                }
                JobDateRow(onDay, days) { d ->
                    val moved = java.time.temporal.ChronoUnit.DAYS.between(onDay, d)
                    due = due?.let { LocalDate.parse(it).plusDays(moved).toString() }
                    on = d.toString()
                }
                // [WORK-02] Work that runs several days (욕실 3일, 주방 5일) is booked once.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("작업 기간", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    // [UX-08] The app's one stepper.
                    com.bamnat.app.ui.components.CountStepper(days, { days = it }, name = "작업 기간", range = 1..MAX_JOB_DAYS,
                        format = { "${it}일" }, valueWidth = 56.dp)
                }
                JobStatusRow(status, paid = balancePaid && (!hasDeposit || depositPaid) && amount.toAmount() > 0) { status = it }
                AmountField("받을 금액 (전체)", amount, { amount = it })
                SwitchRow("3.3% 떼고 받아요 (사업소득)", withholding) { withholding = it }
                // 1.36 U8 (켜면 설명 줄이 생겨 다음 칸이 밀려요): the line is always there, two lines high.
                val w = com.bamnat.core.money.Withholding.of(amount.toAmount())
                Text(if (withholding && amount.toAmount() > 0) "세금 ${Fmt.won(w)}을 떼고 ${Fmt.won(amount.toAmount() - w)}이 들어와요. 5월 종합소득세 신고 때 정산돼요."
                    else if (withholding) "받을 금액을 넣으면 세금을 뗀 금액을 보여 줘요." else "업체가 3.3%를 떼고 주면 켜 주세요. 5월에 돌려받을 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, minLines = 2)
                AmountField("선금 (계약금)", deposit, { deposit = it })
                if (hasDeposit) SwitchRow("선금 받음", depositPaid) { depositPaid = it }
                SwitchRow(if (hasDeposit) "잔금 받음" else "돈 받음", balancePaid) { balancePaid = it }
                DueRow(due?.let(LocalDate::parse), opensOn = onDay.plusDays(days.toLong())) { due = it?.toString() }
                Text("시작 전 알림", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    JobReminders.CHOICES.forEach { m ->
                        FilterChip(selected = remind == m, enabled = startTime != null || m == null, onClick = { remind = m },
                            label = { Text(JobReminders.label(m)) })
                    }
                }
                if (startTime == null) {
                    Text("시작 시간을 정하면 알림을 받을 수 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                AmountField("자재비·경비 (선택)", costs, { costs = it })
                if (costs.toAmount() > 0 && amount.toAmount() > 0) {
                    val payable = amount.toAmount() - if (withholding) com.bamnat.core.money.Withholding.of(amount.toAmount()) else 0
                    Text("이 일로 남는 돈 ${Fmt.won(payable - costs.toAmount())}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(200) },
                    label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            val save = {
                onSave(
                    Job(
                        id = initial?.id ?: 0, date = onDay, title = title.trim().ifEmpty { "일감" },
                        client = client.trim(), place = place.trim(), start = startTime, end = endTime,
                        amount = amount.toAmount(), deposit = deposit.toAmount(), depositPaid = hasDeposit && depositPaid,
                        balancePaid = balancePaid, remindMinutes = remind.takeIf { startTime != null }, note = note.trim(),
                        endDate = onDay.plusDays(days - 1L).takeIf { days > 1 },
                        costs = costs.toAmount(), withholding = withholding,
                        status = status, units = initial?.units, due = due?.let(LocalDate::parse),
                    ),
                )
            }
            TextButton(enabled = title.isNotBlank() || amount.toAmount() > 0, onClick = {
                if (days >= LONG_JOB_DAYS && days != initial?.days) askLong = true else save()
            }) { Text("저장") }
            if (askLong) {
                val start = onDay
                AlertDialog(
                    onDismissRequest = { askLong = false },
                    title = { Text("작업 기간이 ${days}일 맞나요?") },
                    text = { Text("${Fmt.shortDate(start)}부터 ${Fmt.shortDate(start.plusDays(days - 1L))}까지 달력의 ${days}일에 이 일감이 들어가요.") },
                    confirmButton = { TextButton(onClick = { askLong = false; save() }) { Text("맞아요, 저장") } },
                    dismissButton = { TextButton(onClick = { askLong = false }) { Text("기간 고치기") } },
                )
            }
        },
        dismissButton = {
            Row {
                if (initial != null && onDelete != null) {
                    TextButton(onClick = { onDelete(initial) }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
                }
                TextButton(onClick = onDismiss) { Text("닫기") }
            }
        },
    )

    picking?.let { which ->
        TimePickerDialog(
            title = if (which == "start") "시작 시간" else "끝나는 시간",
            initial = (if (which == "start") startTime else endTime ?: startTime?.plusHours(8)) ?: LocalTime.of(9, 0),
            onDismiss = { picking = null },
            onConfirm = { t ->
                if (which == "start") {
                    // A first start time turns on the usual reminder an hour before.
                    if (start == null && remind == null) remind = 60
                    start = t.toString()
                } else {
                    end = t.toString()
                }
                picking = null
            },
        )
    }
}

@Composable
private fun TimeChip(label: String, time: LocalTime?, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(time?.let { Fmt.time(it) } ?: "정하지 않음", style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** A shop's sales and costs for one day [WORK-03]; [SHOP-01] now the 하루 마감 sheet. */
@Composable
fun SalesDialog(date: LocalDate, initial: DailySales?, onSave: (DailySales) -> Unit, onDismiss: () -> Unit) =
    DayCloseDialog(date, initial, onSave, onDismiss)
