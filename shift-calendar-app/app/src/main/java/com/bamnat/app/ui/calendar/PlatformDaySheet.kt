package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.money.AmountField
import com.bamnat.app.ui.money.amountDigits
import com.bamnat.app.ui.money.toAmount
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.Job
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.PlatformWork
import java.time.LocalDate

/**
 * G 1.32 [WTYP-02] 604 플랫폼(배달·운전)형's day chips: each platform written today ("배민 23건"), then the platforms used in
 * the last 60 days, then 추가. A tap opens the day's 건수·금액·운행 시간·유류비 for that platform, big buttons first (장갑 낀 손).
 */
internal fun LazyListScope.platformChips(date: LocalDate, book: MoneyBook, rules: FieldRules, onSave: (Job) -> Unit, onDelete: (Job) -> Unit) {
    val today = book.jobs.filter { it.date == date && PlatformWork.isPlatform(it) }
    val used = book.jobs.filter { PlatformWork.isPlatform(it) && !it.date.isBefore(date.minusDays(60)) }.groupBy { it.client }
        .entries.sortedByDescending { it.value.size }.map { it.key }
    val names = (today.map { it.client } + used).distinct().take(4)
    for (name in names) item {
        var open by rememberSaveable { mutableStateOf(false) }
        val job = today.firstOrNull { it.client == name }
        FilterChip(selected = job != null, onClick = { open = true },
            label = { Text(job?.let { "$name ${PlatformWork.count(it)}건" } ?: name, maxLines = 1) })
        if (open) PlatformDayDialog(date, name, job, rules, onDismiss = { open = false }, onDelete = { onDelete(it); open = false }) { onSave(it); open = false }
    }
    item {
        var open by rememberSaveable { mutableStateOf(false) }
        AssistChip(onClick = { open = true }, label = { Text(if (names.isEmpty()) "플랫폼 기록" else "다른 플랫폼", maxLines = 1) },
            leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        if (open) PlatformDayDialog(date, null, null, rules, onDismiss = { open = false }, onDelete = {}) { onSave(it); open = false }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PlatformDayDialog(date: LocalDate, platform: String?, existing: Job?, rules: FieldRules, onDismiss: () -> Unit,
                               onDelete: (Job) -> Unit, onSave: (Job) -> Unit) {
    var name by rememberSaveable { mutableStateOf(platform ?: PlatformWork.Platform.BAEMIN.label) }
    var count by rememberSaveable { mutableIntStateOf(existing?.let(PlatformWork::count) ?: 0) }
    var amount by rememberSaveable { mutableStateOf(amountDigits(existing?.amount ?: 0)) }
    var hours by rememberSaveable { mutableStateOf(existing?.let { PlatformWork.minutes(it) / 60 }?.takeIf { it > 0 }?.toString() ?: "") }
    var minutes by rememberSaveable { mutableStateOf(existing?.let { PlatformWork.minutes(it) % 60 }?.takeIf { it > 0 }?.toString() ?: "") }
    var costs by rememberSaveable { mutableStateOf(amountDigits(existing?.costs ?: 0)) }
    val drive = (hours.toIntOrNull() ?: 0) * 60 + (minutes.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Fmt.dayTitle(date)} 플랫폼 기록") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PlatformWork.Platform.entries.forEach { p ->
                        FilterChip(selected = name == p.label, onClick = { name = p.label }, enabled = existing == null, label = { Text(p.label) })
                    }
                }
                Text("건수", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-5, -1).forEach { d -> TextButton(onClick = { count = (count + d).coerceAtLeast(0) }) { Text("$d") } }
                    Text("${count}건", style = MaterialTheme.typography.headlineSmall)
                    listOf(1, 5).forEach { d -> TextButton(onClick = { count = (count + d).coerceAtMost(999) }) { Text("+$d") } }
                }
                AmountField("오늘 금액 (플랫폼에 찍힌 돈)", amount, { amount = it })
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(hours, { hours = it.filter(Char::isDigit).take(2) }, label = { Text("운행 시간") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(2) }, label = { Text("분") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                AmountField("오늘 쓴 돈 (유류비·소모품)", costs, { costs = it })
                val net = amount.toAmount() - costs.toAmount()
                if (drive > 0 && amount.toAmount() > 0) Text("시간당 순수익 ${Fmt.won(net * 60 / drive)}", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary)
                val (cut, pay) = PlatformWork.payOf(name, rules)
                Text("정산: ${Fmt.dayOfWeekShort(cut)}요일 마감 · ${Fmt.dayOfWeekShort(pay)}요일 입금" +
                    if (PlatformWork.Platform.of(name)?.checked == false) " (확인 필요, 달력 고치기에서 바꿔요)" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = count > 0 || amount.toAmount() > 0, onClick = {
                onSave(PlatformWork.job(date, name, count, amount.toAmount(), drive, costs.toAmount(), existing))
            }) { Text("저장") }
        },
        dismissButton = {
            Row {
                if (existing != null) TextButton(onClick = { onDelete(existing) }) { Text("지우기") }
                TextButton(onClick = onDismiss) { Text("취소") }
            }
        },
    )
}
