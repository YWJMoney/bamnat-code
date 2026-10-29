package com.bamnat.app.ui.calendar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.QuickDecimalChips
import com.bamnat.app.ui.components.TimeField
import com.bamnat.app.ui.money.AmountField
import com.bamnat.app.ui.money.amountDigits
import com.bamnat.app.ui.money.toAmount
import com.bamnat.core.money.DayRate
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.PlatformWork
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalTime

/**
 * G 1.32 [WTYP-23] 조사 대조 W28: what one platform or 대리 콜 출처 takes, 수수료 % and 건당 보험료, as the user's own 정산서
 * says. Nothing is filled in for them: programs differ and change their rates.
 */
@Composable
internal fun PlatformCutDialog(platform: String, cut: PlatformWork.Cut, onDismiss: () -> Unit, onSave: (PlatformWork.Cut) -> Unit) {
    var fee by rememberSaveable { mutableStateOf(if (cut.feePermille > 0) PlatformWork.Cut.percent(cut.feePermille).removeSuffix("%") else "") }
    var call by rememberSaveable { mutableStateOf(amountDigits(cut.perCall)) }
    val feeValue = if (fee.isBlank()) 0 else PlatformWork.Cut.parsePercent(fee)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$platform 수수료·보험료") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(fee, { fee = it.filter { c -> c.isDigit() || c == '.' }.take(5) }, label = { Text("수수료 (%)") },
                    singleLine = true, isError = feeValue == null, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    supportingText = if (feeValue == null) ({ Text("0에서 100 사이로 적어 주세요") }) else null)
                AmountField("건당 보험료", call, { call = it })
                Text("플랫폼에 찍힌 금액에서 수수료를 떼고 건수만큼 보험료를 빼서, 남은 돈·시간당 순수익·입금 확인에 써요. " +
                    "프로그램마다 다르고 자주 바뀌니 내 정산서에 맞게 적어 주세요. 이미 수수료를 뺀 금액이 찍히는 곳(포인트콜 등)은 0으로 두세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(enabled = feeValue != null, onClick = { onSave(PlatformWork.Cut(feeValue ?: 0, call.toAmount())) }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * G 1.32 [WTYP-27] 조사 대조 W35: 달력 고치기's own 공수 chips (자주 쓰는 공수) and the 끝난 시각 → 공수 rules, for a day-rate
 * field calendar. Taking every chip off goes back to 1공·0.5·1.5.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DayRateUnitsSetting(calendarId: String, rules: FieldRules) {
    val calendars = androidx.compose.ui.platform.LocalContext.current.appContainer.calendars
    var addingChip by rememberSaveable { mutableStateOf(false) }
    var addingRule by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("날짜를 누르면 나오는 공수 버튼", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            rules.chips.forEach { u ->
                InputChip(selected = false, onClick = {
                    calendars.updateField(calendarId) { it.copy(unitChips = DayRate.cleanChips(it.chips - u).takeIf { l -> l.isNotEmpty() } ?: emptyList()) }
                }, label = { Text(DayRate.label(u)) },
                    trailingIcon = { Icon(Icons.Filled.Close, "${DayRate.label(u)} 빼기", Modifier.size(16.dp)) })
            }
            if (rules.chips.size < FieldRules.MAX_CHIPS) AssistChip(onClick = { addingChip = true }, label = { Text("추가") },
                leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
        }
        Text("누르면 빠져요. 모두 빼면 처음 버튼(1공·0.5·1.5)으로 돌아가요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("끝난 시각으로 공수 정하기", style = MaterialTheme.typography.labelLarge)
        if (rules.unitRules.isEmpty()) {
            Text("예: ${DayRate.rulesLine(DayRate.SAMPLE_RULES)}. 정해 두면 날짜의 ‘끝난 시각으로’ 버튼이 시각만 고르면 공수를 적어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { calendars.updateField(calendarId) { it.copy(unitRules = DayRate.SAMPLE_RULES) } }) { Text("예시대로 시작") }
        } else {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rules.unitRules.forEach { r ->
                    InputChip(selected = false, onClick = { calendars.updateField(calendarId) { it.copy(unitRules = it.unitRules - r) } },
                        label = { Text("%02d:%02d까지 %s".format(r.until.hour, r.until.minute, DayRate.label(r.units))) },
                        trailingIcon = { Icon(Icons.Filled.Close, "규칙 빼기", Modifier.size(16.dp)) })
                }
                if (rules.unitRules.size < FieldRules.MAX_RULES) AssistChip(onClick = { addingRule = true }, label = { Text("규칙 추가") },
                    leadingIcon = { Icon(Icons.Filled.Add, null, Modifier.size(18.dp)) })
            }
            Text("마지막 규칙보다 늦게 끝나면 마지막 규칙의 공수예요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    if (addingChip) UnitsDialog("공수 버튼 추가", 2.0, null, onDismiss = { addingChip = false }) { u, _ ->
        addingChip = false
        calendars.updateField(calendarId) { it.copy(unitChips = DayRate.cleanChips(it.chips + u)) }
    }
    if (addingRule) UnitsDialog("규칙 추가", 1.0, LocalTime.of(18, 0), onDismiss = { addingRule = false }) { u, at ->
        addingRule = false
        calendars.updateField(calendarId) { f ->
            f.copy(unitRules = (f.unitRules.filter { it.until != at } + DayRate.EndRule(at!!, u)).sortedBy { it.until }.take(FieldRules.MAX_RULES))
        }
    }
}

/** A 공수 picked by tapping (0.25 steps) or typed to three decimals; with [time], the time a rule ends at too. */
@Composable
private fun UnitsDialog(title: String, start: Double, time: LocalTime?, onDismiss: () -> Unit, onSave: (Double, LocalTime?) -> Unit) {
    var picked by rememberSaveable { mutableStateOf(start) }
    var typed by rememberSaveable { mutableStateOf("") }
    var at by remember { mutableStateOf(time) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (time != null) TimeField("이 시각까지 끝나면", at, { it?.let { t -> at = t } }, modifier = Modifier.fillMaxWidth())
                QuickDecimalChips(value = picked, onChange = { picked = it }, name = "공수", quick = listOf(0.5, 1.0, 1.5, 2.0),
                    step = 0.25, range = 0.25..5.0, unit = "공")
                OutlinedTextField(typed, { t -> typed = t.take(6); DayRate.parse(t)?.let { picked = it } },
                    label = { Text("직접 (0.125처럼 소수 셋째 자리까지)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(onClick = { onSave(picked, at) }) { Text("추가") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * G 1.32 [WTYP-27] The day sheet's 끝난 시각으로: pick when the day ended (now, on today) and the rules give its 공수. With no rules
 * yet the research's example is shown and kept on 저장, to be changed in 달력 고치기.
 */
@Composable
internal fun EndTimeDialog(date: LocalDate, rules: FieldRules, onDismiss: () -> Unit, onSave: (Double, FieldRules) -> Unit) {
    val list = rules.unitRules.ifEmpty { DayRate.SAMPLE_RULES }
    var end by remember {
        val now = Today.dateTime()
        mutableStateOf(if (date == now.toLocalDate()) now.toLocalTime().withSecond(0).withNano(0) else list.first().until)
    }
    val units = DayRate.byEnd(end, list) ?: 1.0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Fmt.dayTitle(date)} 끝난 시각으로") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TimeField("끝난 시각", end, { it?.let { t -> end = t } }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("→ ${DayRate.label(units)}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                }
                Text(DayRate.rulesLine(list), style = MaterialTheme.typography.bodyMedium)
                Text(if (rules.unitRules.isEmpty()) "처음이라 예시 규칙이에요. 저장하면 이 규칙을 써요. 달력 고치기 › 일 세는 방법에서 바꿔요."
                    else "규칙은 달력 고치기 › 일 세는 방법에서 바꿔요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(units, rules.copy(unitRules = list)) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
