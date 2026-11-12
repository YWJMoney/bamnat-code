package com.bamnat.app.ui.money

import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.DatePicker
import com.bamnat.app.ui.components.DatePickerDialog
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.JobStatus
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role

/**
 * [FLD-02] The client (업체·고객) field: names from the calendar's past jobs drop down as the user
 * types, so the same 업체 is written the same way every time and its jobs add up together.
 */
@Composable
fun ClientField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String = "고객·업체") {
    val book = rememberLedger(null)
    val all = remember(book.jobs) { DayRate.clients(book.jobs, limit = 30) }
    var focused by remember { mutableStateOf(false) }
    val shown = remember(all, value) {
        val q = value.trim()
        all.filter { it != q && (q.isEmpty() || it.contains(q, ignoreCase = true)) }.take(5)
    }
    Box(modifier) {
        OutlinedTextField(value = value, onValueChange = onChange, singleLine = true, label = { Text(label) },
            modifier = Modifier.fillMaxWidth().onFocusChanged { focused = it.isFocused })
        DropdownMenu(
            expanded = focused && shown.isNotEmpty(),
            onDismissRequest = { focused = false },
            properties = PopupProperties(focusable = false),
        ) {
            shown.forEach { c -> DropdownMenuItem(text = { Text(c) }, onClick = { onChange(c); focused = false }) }
        }
    }
}

/**
 * [FLD-03] A job's step: 가계약 (not yet sure), 확정, 완료 (work done) or 취소. 입금 is not a step the
 * user picks: once the money is ticked as paid the row says 입금 완료.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JobStatusRow(status: JobStatus, paid: Boolean, onChange: (JobStatus) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("진행 상태", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            if (paid && status != JobStatus.CANCELLED) {
                Text("입금 완료", style = MaterialTheme.typography.labelLarge, color = LocalCalendarColors.current.income)
            }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            JobStatus.entries.forEach { s ->
                FilterChip(selected = status == s, onClick = { onChange(s) }, label = { Text(s.label) })
            }
        }
        if (status == JobStatus.CANCELLED) {
            Text("취소한 일감은 번 돈·받을 돈·통계·알림에서 빠져요. 받은 선금은 그대로 남아요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 2.0.0 [RVK-42] (검토 T06a-02·U06a-22) The day a job starts, changed in the job itself: work pushed back by rain or 자재 moves with its
 * period, its 입금 약속일 and 알림 (it had to be deleted and written again on the new day).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JobDateRow(start: LocalDate, days: Int, onChange: (LocalDate) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "작업 날짜 바꾸기") { picking = true },
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("작업 날짜", style = MaterialTheme.typography.bodyLarge)
            Text(if (days <= 1) Fmt.dayTitle(start) else "${Fmt.shortDate(start)} ~ ${Fmt.shortDate(start.plusDays(days - 1L))}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("바꾸기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
}

/** [FLD-09] 입금 약속일: when the client said they would pay; the 받을 돈 알림 follows it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DueRow(due: LocalDate?, opensOn: LocalDate? = null, onChange: (LocalDate?) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { picking = true }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("입금 약속일 (선택)", style = MaterialTheme.typography.bodyLarge)
            Text(due?.let { "${Fmt.dayTitle(it)} · 다음 날까지 안 들어오면 알려 드려요" } ?: "정하면 그 다음 날 받을 돈을 알려 드려요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (due != null) TextButton(onClick = { onChange(null) }) { Text("지우기") }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = (due ?: opensOn ?: Today.date()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
}

/** 2.0.0 [RVK-46] A job's payments kept through a screen rebuild: one "id<TAB>epochDay<TAB>amount<TAB>label" line each. */
internal fun encodeJobPayments(list: List<com.bamnat.core.money.JobPayment>): String =
    list.joinToString("\n") { "${it.id}\t${it.date.toEpochDay()}\t${it.amount}\t${it.label.replace(Regex("[\\t\\n]"), " ")}" }

internal fun decodeJobPayments(text: String, jobId: Long): List<com.bamnat.core.money.JobPayment> = text.lines().mapNotNull { line ->
    val p = line.split('\t')
    if (p.size < 4) return@mapNotNull null
    val id = p[0].toLongOrNull() ?: return@mapNotNull null
    val day = p[1].toLongOrNull() ?: return@mapNotNull null
    val amount = p[2].toLongOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
    com.bamnat.core.money.JobPayment(id, jobId, LocalDate.ofEpochDay(day), amount, p[3])
}

/**
 * 2.0.0 [RVK-46] (검토 MONEY-29·T06a-01·I06ab-04) 나눠 받은 돈 on the job sheet: 계약금·중도금·잔금 or any part, each with the day it came,
 * besides the 선금·잔금 switches. Saved with the job ([com.bamnat.app.data.MoneyRepository.save]); [left] is what is still out.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun JobPaymentsSection(
    payments: List<com.bamnat.core.money.JobPayment>, left: Long,
    /** The job has its own 선금·잔금, so only 중도금 is offered as a name. */
    hasDeposit: Boolean = false,
    onChange: (List<com.bamnat.core.money.JobPayment>) -> Unit,
) {
    var adding by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var digits by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var label by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("") }
    var day by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(Today.date().toEpochDay()) }
    var picking by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("나눠 받은 돈", style = MaterialTheme.typography.bodyLarge)
                Text(if (payments.isEmpty()) "계약금·중도금처럼 여러 번 받으면 받은 날과 함께 적어요" else "남은 돈 ${Fmt.won(left)}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!adding && left > 0) TextButton(onClick = { adding = true; digits = amountDigits(left) }) { Text("받은 돈 적기") }
        }
        payments.sortedWith(compareBy({ it.date }, { it.id })).forEach { p ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.dayTitle(p.date) + (p.label.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Fmt.won(p.amount), style = MaterialTheme.typography.bodyMedium.merge(com.bamnat.app.ui.theme.NumberStyle),
                    color = LocalCalendarColors.current.income)
                androidx.compose.material3.IconButton(onClick = { onChange(payments - p) }) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "${Fmt.shortDate(p.date)} ${Fmt.won(p.amount)} 받은 기록 지우기")
                }
            }
        }
        if (adding) {
            AmountField("받은 금액", digits, { digits = it })
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.bamnat.core.money.JobPayment.LABELS.filter { !hasDeposit || it == "중도금" }.forEach { l ->
                    FilterChip(selected = label == l, onClick = { label = if (label == l) "" else l }, label = { Text(l) })
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { picking = true }, verticalAlignment = Alignment.CenterVertically) {
                Text("받은 날", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(Fmt.dayTitle(LocalDate.ofEpochDay(day)), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { adding = false }) { Text("취소") }
                TextButton(enabled = digits.toAmount() > 0, onClick = {
                    val amount = digits.toAmount().coerceAtMost(left.coerceAtLeast(0))
                    if (amount > 0) onChange(payments + com.bamnat.core.money.JobPayment(date = LocalDate.ofEpochDay(day), amount = amount, label = label))
                    adding = false; digits = ""; label = ""
                }) { Text("더하기") }
            }
        }
    }
    if (picking) {
        val state = rememberDatePickerState(initialSelectedDateMillis = LocalDate.ofEpochDay(day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().toEpochDay() }
                    picking = false
                }) { Text("확인") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("취소") } },
        ) { DatePicker(state, showModeToggle = false) }
    }
}
