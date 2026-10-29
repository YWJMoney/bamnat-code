package com.bamnat.app.ui.pay

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.foundation.verticalScroll
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.calendar.HintStore
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.RateNews
import com.bamnat.core.pay.TaxMode
import com.bamnat.core.pay.WageType
import com.bamnat.core.pay.YearEndPreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import com.bamnat.core.time.Today

/**
 * [PAYX-42] 연말정산 미리보기: from January to the month on screen, the 결정세액 roughly against the 간이세액표
 * withholding, run on to the year. Only for 4대보험 pay, where 소득세 is withheld each month.
 *
 * F 1.32 [TAXP-03] 594: with 프로 (세금 예측) the 신용·체크카드, 의료·교육, 보험, 월세 come from this calendar's 가계부 (run on to
 * December) and 기부·연금저축·IRP can be typed; what more could come back is said with its won. A 하루 이용권 sees the
 * result; the weekly 다시 계산 알림 is for a subscription.
 */
@Composable
internal fun YearEndCard(profile: PayProfile, snapshot: ScheduleSnapshot, month: YearMonth, calendarId: String? = null) {
    val c = LocalContext.current.appContainer
    // [J133-31] 1.33 (실사용 테스트 "안내는 '연말정산은 도구에'인데 도구에 없어요"): the card is always here; a pay without
    // 4대보험 says why it cannot be worked out, with the way to change it.
    if (profile.taxMode != TaxMode.INSURANCE_4) {
        val scope = rememberCoroutineScope()
        SectionCard(title = "${month.year}년 연말정산 미리보기") {
            Text("연말정산은 4대보험과 소득세를 떼는 월급에서 미리 볼 수 있어요. 지금 세금 방식은 ‘${profile.taxMode.label}’이에요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (calendarId != null) TextButton(onClick = { scope.launch { com.bamnat.app.tax.TaxWay.pick(c, calendarId, TaxMode.INSURANCE_4) } }) {
                Text("4대보험으로 바꾸기")
            }
        }
        return
    }
    val gate by c.featureGate.state.collectAsStateWithLifecycle()
    val pro = gate.allows(com.bamnat.app.tier.Feature.TAX_FORECAST)
    val tax = remember(calendarId) { calendarId?.let(c::tax) }
    val settings by (tax?.settings ?: kotlinx.coroutines.flow.flowOf(null)).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    val typed = settings?.yearEndFor(month.year) ?: YearEndPreview.Extras()
    val r by produceState<YearEndPreview.Result?>(null, open, profile, snapshot, month, typed, pro) {
        if (open) value = withContext(Dispatchers.Default) {
            val extras = if (pro && calendarId != null) {
                val money = c.calendars.money(calendarId)
                YearEndPreview.extrasFrom(money.allEntries(), money.allAccounts(), month.year, Today.date(), typed)
            } else YearEndPreview.Extras()
            runCatching { YearEndPreview.ofYear(profile, month, snapshot.resolver::resolve, extras = extras) }.getOrNull()
        }
    }
    SectionCard(title = "${month.year}년 연말정산 미리보기", trailing = { if (!open) TextButton(onClick = { open = true }) { Text("계산하기") } }) {
        if (!open) {
            Text("1월부터 이 달까지 근무로 내년 2월에 돌려받을지, 더 낼지 어림해요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        val x = r ?: return@SectionCard
        Text(
            if (x.refund >= 0) "약 ${won(x.refund)} 돌려받아요" else "약 ${won(-x.refund)} 더 내요",
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
            color = if (x.refund >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        settings?.yearEndLastRefund?.takeIf { settings?.yearEndLastAt != null && it != x.refund }?.let { last ->
            Text("지난 계산(${settings?.yearEndLastAt?.let { "${it.monthValue}/${it.dayOfMonth}" }})보다 ${won(kotlin.math.abs(x.refund - last))} " +
                if (x.refund > last) "늘었어요" else "줄었어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Line("총급여 (한 해로 어림, ${x.months}개월 기준)", won(x.salary))
        Line("근로소득공제", "−" + won(x.earnedDeduction))
        if (x.cardDeduction > 0) Line("신용카드 등 소득공제", "−" + won(x.cardDeduction))
        if (x.extras.hasHometax) Text("카드는 홈택스 1~9월 숫자에 10월부터 가계부를 더했어요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Line("과세표준", won(x.taxBase))
        Line("산출세액 − 세액공제", "${won(x.calculated)} − ${won(x.credits)}")
        if (x.specialCredits > 0) Line("  의료·교육·보험·기부·월세 세액공제", won(x.specialCredits))
        if (x.pensionCredit > 0) Line("  연금저축·IRP 세액공제", won(x.pensionCredit))
        if (x.standard && x.extras.any) Line("  표준세액공제가 더 커서 그것으로 셌어요", won(YearEndPreview.STANDARD_CREDIT))
        Line("결정세액 (지방소득세 ${won(x.local)} 따로)", won(x.determined))
        Line("매달 뗀 소득세 (간이세액표 어림)", won(x.withheld))
        x.tips.forEach { t -> Text("💡 ${t.text}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 4.dp)) }
        if (pro && calendarId != null) {
            Text("카드·의료·교육·보험·월세는 가계부에서 12월까지 어림했어요. 기부·연금저축은 직접 넣어 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { editing = true }) { Text("금액 고치기") }
                // A subscription (not a day pass) can have it worked out again every week until January.
                if (gate.access.reason != com.bamnat.core.tier.ProReason.DAY_PASS) {
                    Text("매주 다시 계산 알림", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    androidx.compose.material3.Switch(checked = settings?.yearEndWeekly == true, onCheckedChange = { on ->
                        scope.launch { tax?.saveSettings { it.copy(yearEndWeekly = on, yearEndLastRefund = x.refund, yearEndLastAt = Today.date()) } }
                    })
                }
            }
            Text(YearEndPreview.LEFT_OUT_EXTRAS, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text(YearEndPreview.LEFT_OUT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (!pro) TextButton(onClick = { com.bamnat.app.ui.pro.openPro(com.bamnat.app.tier.Feature.TAX_FORECAST) }) {
                Text("카드·의료비·월세·연금저축까지 넣어 보기 (프로)")
            }
        }
        Text("소득세법 제47조·제55조·제59조로 셌어요. 정확한 값은 홈택스 연말정산 미리보기에서 확인해 주세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (editing) YearEndExtrasSheet(typed, onSave = { e ->
        editing = false
        scope.launch { tax?.saveSettings { it.copy(yearEnd = e, yearEndYear = month.year) } }
    }, onDismiss = { editing = false })
}

/** F 1.32 [TAXP-03] The year's amounts to type over the ledger's; an empty field takes the ledger's again. */
@Composable
private fun YearEndExtrasSheet(start: YearEndPreview.Extras, onSave: (YearEndPreview.Extras) -> Unit, onDismiss: () -> Unit) {
    fun d(v: Long) = if (v > 0) v.toString() else ""
    var credit by remember { mutableStateOf(d(start.credit)) }
    var debit by remember { mutableStateOf(d(start.debit)) }
    var market by remember { mutableStateOf(d(start.market)) }
    var insurance by remember { mutableStateOf(d(start.insurance)) }
    var medical by remember { mutableStateOf(d(start.medical)) }
    var education by remember { mutableStateOf(d(start.education)) }
    var donation by remember { mutableStateOf(d(start.donation)) }
    var rent by remember { mutableStateOf(d(start.rent)) }
    var pension by remember { mutableStateOf(d(start.pensionSavings)) }
    var irp by remember { mutableStateOf(d(start.irp)) }
    // 1.32 [TAXP-13] 홈택스 미리보기의 1~9월 국세청 합계; [TAXP-12] 18세 미만 자녀 for the card 한도.
    var htCredit by remember { mutableStateOf(d(start.htCredit)) }
    var htDebit by remember { mutableStateOf(d(start.htDebit)) }
    var htMarket by remember { mutableStateOf(d(start.htMarket)) }
    var under18 by remember { mutableStateOf(if (start.under18 >= 0) start.under18.toString() else "") }
    fun v(s: String) = s.toLongOrNull() ?: 0L
    com.bamnat.app.ui.components.FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("연말정산 금액 (1년치)") },
        confirmButton = {
            TextButton(onClick = {
                onSave(YearEndPreview.Extras(v(credit), v(debit), v(market), v(insurance), v(medical), v(education), v(donation), v(rent), v(pension), v(irp),
                    under18 = under18.toIntOrNull()?.coerceIn(0, 20) ?: -1, htCredit = v(htCredit), htDebit = v(htDebit), htMarket = v(htMarket)))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp)) {
                Text("비워 두면 가계부 기록으로 어림해요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("홈택스 미리보기 1~9월 (있으면)", style = MaterialTheme.typography.titleSmall)
                Text("홈택스 › 연말정산 미리보기에 국세청이 채운 1~9월 합계를 보고 넣어 주세요. 10~12월은 가계부로 어림해요. 자동으로 가져오지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                com.bamnat.app.ui.money.AmountField("1~9월 신용카드", htCredit, { htCredit = it })
                com.bamnat.app.ui.money.AmountField("1~9월 체크카드·현금영수증", htDebit, { htDebit = it })
                com.bamnat.app.ui.money.AmountField("1~9월 전통시장·대중교통", htMarket, { htMarket = it })
                androidx.compose.material3.OutlinedTextField(under18, { t -> under18 = t.filter { it.isDigit() }.take(2) }, singleLine = true,
                    label = { Text("18세 미만 자녀 (카드 공제 한도, 2026년부터)") }, placeholder = { Text("비우면 급여 설정의 자녀 수") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth())
                Text("한 해 전체 금액 (직접 넣으면 위보다 먼저 써요)", style = MaterialTheme.typography.titleSmall)
                com.bamnat.app.ui.money.AmountField("신용카드", credit, { credit = it })
                com.bamnat.app.ui.money.AmountField("체크카드·현금영수증·페이", debit, { debit = it })
                com.bamnat.app.ui.money.AmountField("전통시장·대중교통", market, { market = it })
                com.bamnat.app.ui.money.AmountField("보장성 보험료", insurance, { insurance = it })
                com.bamnat.app.ui.money.AmountField("의료비", medical, { medical = it })
                com.bamnat.app.ui.money.AmountField("교육비", education, { education = it })
                com.bamnat.app.ui.money.AmountField("기부금", donation, { donation = it })
                com.bamnat.app.ui.money.AmountField("월세 (무주택, 총급여 8천만 원 이하)", rent, { rent = it })
                com.bamnat.app.ui.money.AmountField("연금저축", pension, { pension = it })
                com.bamnat.app.ui.money.AmountField("IRP", irp, { irp = it })
            }
        },
    )
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** [PAYX-44] The new year's 최저임금·보험료율 once, with the user's own 시급 against the new 최저임금. */
@Composable
internal fun RateNewsCard(profile: PayProfile, ordinaryHourly: Long) {
    val context = LocalContext.current
    val hints = remember { HintStore(context) }
    val hourly = if (profile.wageType == WageType.HOURLY) profile.base.on(Today.date()).value else ordinaryHourly
    val news = remember(hourly) { RateNews.of(Today.date(), hourly) } ?: return
    var gone by remember(news.key) { mutableStateOf(hints.done(news.key)) }
    if (gone) return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("${news.from.year}년 ${news.from.monthValue}월부터 바뀌어요", style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            news.lines.forEach { l ->
                Text("· $l", style = MaterialTheme.typography.bodyMedium,
                    color = if (news.belowMinimum && l.contains("적어요")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Text("급여 계산은 바뀐 기준으로 저절로 맞춰져요 (최저임금법 제6조).", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
            TextButton(onClick = { hints.markDone(news.key); gone = true }) { Text("알겠어요") }
        }
    }
}
