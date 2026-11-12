package com.bamnat.app.ui.pay

import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.DatePicker
import com.bamnat.app.ui.components.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
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
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.money.LedgerExport
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.TakeHome
import com.bamnat.core.pay.InsuranceLine
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.work.HourlyWork
import com.bamnat.core.work.WorkLogCsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import com.bamnat.core.time.Today

/**
 * [HRLY-07] The profile as pay sees it. Until the user picks a 주휴 week start, 주휴 weeks follow the pay
 * week ([PayProfile.holidayWeekFrom]), as they always did, so nobody's pay moves without being asked;
 * the calendar's 주휴 badges and 통계 count from the same day.
 */
@Suppress("UNUSED_PARAMETER")
fun PayProfile.hourlyWeek(settings: AppSettings): PayProfile = this

/** [HRLY-04] Names of each tax mode, as the chips say them. */
val TaxMode.label: String
    get() = when (this) {
        TaxMode.NONE -> "떼지 않음"
        TaxMode.WITHHOLDING_3_3 -> "3.3%"
        TaxMode.INSURANCE_4 -> "4대보험"
    }

/**
 * 1.39 [PAYX-58][PAYX-59] Under a take-home's lines: why a 4대보험 line is missing or on the 28만 원 floor, and a 비과세 kind over its
 * 월 20만 원 (식대가 둘이면 합쳐서).
 */
@Composable
internal fun TakeHomeNotes(r: com.bamnat.core.pay.TakeHomeResult, profile: PayProfile?, month: YearMonth) {
    val over = remember(profile, month) { profile?.let { TakeHome.nonTaxableParts(it, month).filter { p -> p.over } }.orEmpty() }
    (r.notes + over.map { p -> "${p.kind} ${won(p.paid)} 중 ${won(p.amount)}까지만 비과세예요(한도는 종류마다 월 20만 원" +
        (if (p.names.size > 1) ", ${p.names.joinToString("·")} 합쳐서" else "") + ")" }).forEach {
        Text("ⓘ $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 1.39 [PAYX-59] 공식 대조 G2·G3·G6: 4대보험 the worker is out of, picked here (the app does not ask a 생년월일): 만 60세 이상 국민연금,
 * 65세 이후 새로 일한 곳의 고용보험, 주 15시간 미만. Each chip on means that line is not taken off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InsuranceOffRow(off: Set<InsuranceLine>, onChange: (Set<InsuranceLine>) -> Unit) {
    var open by remember { mutableStateOf(off.isNotEmpty()) }
    if (!open) {
        TextButton(onClick = { open = true }) { Text("떼지 않는 보험이 있어요 (만 60세 이상·주 15시간 미만 등)") }
        return
    }
    Text("떼지 않는 보험", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        InsuranceLine.entries.forEach { l ->
            FilterChip(selected = l in off, onClick = { onChange(if (l in off) off - l else off + l) }, label = { Text("${l.label} 안 뗌") })
        }
    }
    Text("국민연금: 만 60세가 되면 의무 가입이 끝나요(국민연금법 제6조). 고용보험: 65세 이후 새로 일한 곳은 떼지 않아요(고용보험법 제10조). " +
        "주 15시간 미만이면 국민연금·건강보험 대상이 아니고, 고용보험은 3개월 이상 일하면 떼요. 명세서에 그 줄이 없으면 골라 주세요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 2.0.0 [RVJ-05] 검토 PAYC-20·U04-05: in a year whose 요율 are not in the app yet (2028-01 with 2027 known), says the 실수령 is
 * worked out on last year's values (국민연금 follows the law's yearly step). Nothing once the year is in.
 */
@Composable
fun StaleRatesNote(month: java.time.YearMonth, modifier: Modifier = Modifier) {
    val y = com.bamnat.core.pay.RateTable.staleYear(month.atDay(1)) ?: return
    Text("${month.year}년 요율 대기 중 · ${y}년 값으로 어림했어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = modifier)
}

/** [HRLY-04] "예상 실수령 1,784,750원 (4대보험)" under a pay before tax; nothing when no tax is taken. */
@Composable
fun TakeHomeLine(gross: Long, mode: TaxMode, modifier: Modifier = Modifier, on: java.time.LocalDate? = null) {
    if (mode == TaxMode.NONE) return
    val r = remember(gross, mode, on) { TakeHome.estimate(gross, mode, on = on) }
    Text("예상 실수령 ${won(r.net)} (${mode.label})", style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary, modifier = modifier)
}

/**
 * [HRLY-04..HRLY-08] The 알바 card on the pay screen: 예상 실수령 by tax mode, then the settings only a
 * part-timer needs (법정 휴게, 수습, 주휴 시작 요일) and 근무 기록 내보내기.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun HourlyPayCard(
    vm: PayViewModel, profile: PayProfile, gross: Long, unlocked: Boolean, snapshot: ScheduleSnapshot, month: YearMonth,
    holidayWeeks: List<com.bamnat.core.pay.HolidayWeek> = emptyList(),
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    var pickingDate by remember { mutableStateOf(false) }
    SectionCard(title = "예상 실수령") {
        Text("세금 떼는 방식", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TaxMode.entries.forEach { m ->
                FilterChip(selected = profile.taxMode == m, onClick = { vm.update { it.copy(taxMode = m, taxChosen = true) } }, label = { Text(m.label) })
            }
        }
        // [PAYR-04] The same 가족·자녀 the stats card sets, so both screens show one take-home.
        val r = remember(gross, profile, month) {
            TakeHome.forProfile(profile, month, gross)
        }
        r.deductions.forEach { (name, amount) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(if (unlocked) "−${won(amount)}" else "●●●원", style = MaterialTheme.typography.bodyMedium)
            }
        }
        TakeHomeNotes(r, profile, month)
        if (profile.taxMode == TaxMode.INSURANCE_4) {
            // 1.39 [PAYX-59] G2: 주마다 15시간이 안 되는 달이면 국민연금·건강보험은 대상이 아니에요(한 번 눌러 빼기).
            val short = holidayWeeks.isNotEmpty() && holidayWeeks.all { it.workedMinutes < 15 * 60 }
            if (short && (InsuranceLine.PENSION !in profile.insuranceOff || InsuranceLine.HEALTH !in profile.insuranceOff)) {
                Text("이번 달은 주마다 15시간이 안 돼요. 주 15시간(월 60시간) 미만이면 국민연금·건강보험은 떼지 않아요(국민연금법 시행령 제2조, 국민건강보험법 시행령 제9조).",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                TextButton(onClick = { vm.update { it.copy(insuranceOff = it.insuranceOff + InsuranceLine.PENSION + InsuranceLine.HEALTH) } }) {
                    Text("국민연금·건강보험 떼지 않기")
                }
            }
            InsuranceOffRow(profile.insuranceOff) { off -> vm.update { it.copy(insuranceOff = off) } }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("세전 ${if (unlocked) won(r.gross) else "●●●원"}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("실수령 ${if (unlocked) won(r.net) else "●●●원"}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary)
        }
        StaleRatesNote(month)
        Text(
            when (profile.taxMode) {
                TaxMode.NONE -> "가게에서 세금을 떼지 않으면 세전 금액이 그대로 들어와요."
                TaxMode.WITHHOLDING_3_3 -> "3.3%(소득세 3% + 지방소득세 0.3%)로 떼였다면 다음 해 5월 종합소득세 신고로 돌려받을 수 있어요."
                TaxMode.INSURANCE_4 -> "4대보험은 그해 근로자 몫이에요. 소득세는 국세청 간이세액표(가족 ${profile.taxFamily}명" +
                    (if (profile.taxChildren > 0) ", 8~20세 자녀 ${profile.taxChildren}명" else "") +
                    ") 값이고, 실제는 회사 선택(80·100·120%)과 연말정산으로 정해져요. 가족 수는 통계 ‘○월 한눈에’에서 바꿔요. " + TakeHome.PENSION_NOTE
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp),
        )
        HorizontalDivider(Modifier.padding(vertical = 8.dp))

        // [HRLY-05] 근로기준법 제54조
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("법정 휴게 자동 빼기", style = MaterialTheme.typography.bodyLarge)
                Text("휴게를 안 적은 날, 4시간 넘게 일하면 30분·8시간이면 1시간을 빼요. 4시간 근무에 휴게를 안 쓰기로 했다면 그날 휴게를 0분으로 적어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = profile.legalBreaks, onCheckedChange = { on -> vm.update { it.copy(legalBreaks = on) } })
        }

        // [HRLY-06] 수습 감액
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("수습 감액", style = MaterialTheme.typography.bodyLarge)
                Text(profile.probationEnd?.let { "${it.monthValue}월 ${it.dayOfMonth}일 전까지 시급의 ${profile.probationPercent}%" } ?: "수습이 아니면 그대로 두세요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (profile.probationEnd != null) TextButton(onClick = { vm.update { it.copy(probationEnd = null) } }) { Text("없음") }
            TextButton(onClick = { pickingDate = true }) { Text(if (profile.probationEnd == null) "끝나는 날 넣기" else "바꾸기") }
        }

        // [HRLY-07] 주휴 계산 시작 요일
        Text("주휴 계산 시작 요일", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp))
        val weekFrom = profile.hourlyWeek(settings).holidayWeekFrom
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayOfWeek.entries.forEach { d ->
                FilterChip(selected = weekFrom == d, onClick = { vm.update { it.copy(holidayWeekStart = d) } }, label = { Text(WEEKDAYS[d.ordinal]) })
            }
        }
        // 1.39 [PAYX-62] 발굴 G3-M3: 이번 주가 15시간 바로 아래·위면 한 줄, 끄면 다시 켜는 곳을 말해요.
        WeekHoursNudgeLines(holidayWeeks, settings.weekHoursNudge, vm::weekHoursNudge)
        // [PAYX-35] Week by week: did it earn 주휴, and if not why (근로기준법 제55조, 시행령 제30조: 주 15시간 이상, 개근).
        if (holidayWeeks.isNotEmpty()) {
            Text("이번 달 주마다 주휴", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                holidayWeeks.forEach { w ->
                    androidx.compose.material3.AssistChip(
                        onClick = {},
                        label = { Text("${w.from.monthValue}/${w.from.dayOfMonth}~${w.to.monthValue}/${w.to.dayOfMonth} · ${w.text}") },
                        leadingIcon = {
                            Icon(if (w.earned) Icons.Filled.Check else Icons.Filled.Close, contentDescription = if (w.earned) "받아요" else "못 받아요",
                                modifier = Modifier.size(16.dp))
                        },
                    )
                }
            }
            Text("주 15시간 이상 일하고 결근이 없는 주에 받아요. 쉬는 날을 바꿔 넣으면 바로 다시 세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // 1.32 [WTYP-17] 대타는 주휴에서 빠져요, [WTYP-18] 휴게 포함 시급 환산과 알바 2곳까지 무료.
        val covers = remember(snapshot, month) { com.bamnat.core.work.HourlyLines.covers(month, snapshot.resolver::resolve) }
        com.bamnat.core.work.HourlyLines.coverLine(covers)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        val stay = remember(snapshot, month, profile) {
            com.bamnat.core.work.HourlyLines.stay(month, profile.base.on(month.atDay(1)).value, snapshot.resolver::resolve)
        }
        com.bamnat.core.work.HourlyLines.stayLine(stay)?.let {
            Text(if (unlocked) it else "휴게 포함 시급 환산은 금액 가리기를 풀면 보여요.", style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp))
        }
        Text(com.bamnat.core.work.HourlyLines.FREE_LINE, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))

        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        // [HRLY-08] 근무 기록 내보내기
        WorkLogExport(profile, snapshot, month, onDone = {})
    }
    if (pickingDate) {
        PickProbationEnd(profile.probationEnd ?: Today.date().plusMonths(3), onDismiss = { pickingDate = false }) { d ->
            pickingDate = false
            vm.update { it.copy(probationEnd = d) }
        }
    }
}

private val WEEKDAYS = listOf("월", "화", "수", "목", "금", "토", "일")

/**
 * 1.39 [PAYX-62] 발굴 G3-M3: 주 15시간 경계 알림(근로기준법 제18조 제3항, 제55조). '이 알림 끄기'를 누르면 바로 사라지고,
 * 다시 켜는 곳(설정 › 알림과 잠 › 돈)을 그 자리에서 한 번 말해요.
 */
@Composable
internal fun WeekHoursNudgeLines(weeks: List<com.bamnat.core.pay.HolidayWeek>, on: Boolean, onChange: (Boolean) -> Unit) {
    var justOff by remember { mutableStateOf(false) }
    if (!on) {
        if (justOff) Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("주 15시간 경계 알림을 껐어요. 다시 켜려면 ${com.bamnat.core.pay.WeekHoursNudge.WHERE}에서 켜요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { justOff = false; onChange(true) }) { Text("되돌리기") }
        }
        return
    }
    val lines = remember(weeks) { com.bamnat.core.pay.WeekHoursNudge.lines(weeks, Today.date()) }
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("주휴수당은 한 주 15시간 이상 일하고 결근이 없을 때 받아요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { justOff = true; onChange(false) }) { Text("이 알림 끄기") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickProbationEnd(initial: LocalDate, onDismiss: () -> Unit, onPick: (LocalDate) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) } ?: onDismiss()
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) { DatePicker(state = state, title = { Text("수습이 끝나는 날", Modifier.padding(start = 24.dp, top = 16.dp)) }) }
}

/**
 * [HRLY-08] 근무 기록 내보내기: the month's days (날짜, 출근, 퇴근, 휴게, 시간, 출결, 일급) as a CSV file, shared
 * or saved where the user picks, as the ledger's export does. Made on the phone and sent nowhere else.
 */
@Composable
internal fun WorkLogExport(profile: PayProfile?, snapshot: ScheduleSnapshot, month: YearMonth, onDone: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val name = WorkLogCsv.fileName(month)
    val csv = remember(profile, snapshot, month) {
        val resolver = snapshot.copy(legalBreaks = snapshot.legalBreaks || profile?.legalBreaks == true).resolver
        WorkLogCsv.write(resolver.resolveRange(month.atDay(1), month.atEndOfMonth())) { HourlyWork.dayPay(it, profile) }
    }
    var said by remember { mutableStateOf<String?>(null) }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(LedgerExport.MIME)) { uri ->
        if (uri != null) scope.launch {
            said = if (LedgerExport.save(context, uri, csv)) "파일로 저장했어요" else "파일을 저장하지 못했어요"
            onDone(said!!)
        }
    }
    Text("근무 기록 내보내기", style = MaterialTheme.typography.bodyLarge)
    Text("${month.monthValue}월 출퇴근·휴게·시간·출결·일급을 엑셀에서 여는 파일로 만들어요. 수당을 못 받았을 때 증거가 돼요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
        OutlinedButton(onClick = { runCatching { save.launch(name) }.onFailure { said = "파일을 저장할 곳을 열지 못했어요" } }) {
            Icon(Icons.Outlined.FileDownload, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("파일로 저장")
        }
        OutlinedButton(onClick = { scope.launch { runCatching { shareWorkLog(context, csv, name) }.onFailure { said = "공유하지 못했어요" } } }) {
            Text("공유하기")
        }
    }
    said?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
}

/** [HRLY-08] The work log through the share sheet; the copy sits in the app's cache, replaced by the next one. */
private suspend fun shareWorkLog(context: Context, csv: String, name: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share/worklog").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, name).apply { writeText(csv, Charsets.UTF_8) }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", file)
    val send = Intent(Intent.ACTION_SEND).apply {
        type = LedgerExport.MIME
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, name.removeSuffix(".csv"))
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(send, "근무 기록 내보내기").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
