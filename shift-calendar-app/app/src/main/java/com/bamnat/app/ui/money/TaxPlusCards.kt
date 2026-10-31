package com.bamnat.app.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.pro.openPro
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.TaxPlus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth

/**
 * F 1.32 [TAXP-01] 592: the month's 세전 and 세후 on one line of 수입 내역, from the payslip, the deposit, the user's own
 * record or the app's estimate, in that order. Only the estimate is 프로; the user's own numbers are always shown.
 */
@Composable
internal fun NetSummaryLine(line: TaxPlus.NetLine?, gate: Gate) {
    line ?: return
    val locked = line.estimated && gate.locked(Feature.TAX_FORECAST)
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp).let { if (locked) it.clickable { if (!com.bamnat.app.ui.pro.tryFeature(Feature.TAX_FORECAST)) openPro(Feature.TAX_FORECAST) } else it },
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (locked) listOfNotNull(line.gross?.let { "세전 ${Fmt.won(it)}" }, "세후 예상은 프로").joinToString(" · ") else TaxPlus.netText(line),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        if (locked) ProPill(locked = true)
    }
}

/** F 1.32 [TAXP-02] 593: how each deduction of [gross] comes about, the 간이세액표 row included. 프로 (세금 예측). */
@Composable
internal fun TaxExplainSection(gross: Long, profile: PayProfile?, month: YearMonth, gate: Gate) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("세금이 이렇게 나와요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        TextButton(onClick = { if (com.bamnat.app.ui.pro.tryFeature(Feature.TAX_FORECAST)) open = !open else openPro(Feature.TAX_FORECAST) },
            modifier = Modifier.heightIn(min = 48.dp)) { Text(if (open) "접기" else "자세히") }
    }
    if (!open) return
    val free = remember(profile, month) { profile?.let { com.bamnat.core.pay.TakeHome.nonTaxable(it, month) } ?: 0L }
    val steps = remember(gross, free, profile, month) {
        TaxPlus.explain((gross - free).coerceAtLeast(0), profile?.taxFamily ?: 1, profile?.taxChildren ?: 0, month.atDay(1))
    }
    if (free > 0) Muted("비과세 ${Fmt.won(free)}(식대 등)을 뺀 ${Fmt.won(gross - free)}에 세금을 매겨요.")
    steps.forEach { s ->
        TaxLine(s.label, Fmt.won(s.amount))
        Muted(s.how)
    }
    RateSourcesSection()
}

/** F 1.32 [TAXP-11] 602: where each rate comes from; one not yet checked says 확인 필요. */
@Composable
internal fun RateSourcesSection() {
    var open by remember { mutableStateOf(false) }
    TextButton(onClick = { open = !open }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (open) "요율 출처 접기" else "요율 출처 보기") }
    if (open) TaxPlus.SOURCES.forEach { Muted("· " + TaxPlus.sourceLine(it)) }
}

/**
 * F 1.32 [TAXP-04] 595: each credit card's next bill, what is on it now and where it ends at this pace, 할부 turns
 * included. 프로 (돈 통계); free users see the card with the amounts on it now only.
 */
@Composable
internal fun CardBillCard(book: MoneyBook, today: LocalDate, gate: Gate) {
    val rows = remember(book, today) { TaxPlus.cardForecast(book.accounts, book.entries, today) }
    if (rows.isEmpty()) return
    val pro = gate.allows(Feature.MONEY_STATS)
    SectionCard(title = "다음 카드값", trailing = { if (!pro) ProPill(locked = true) }) {
        rows.take(6).forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${r.card.label} · ${Fmt.monthDay(r.payDate)}", style = MaterialTheme.typography.bodyLarge, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                Text(Fmt.won(r.now), style = MaterialTheme.typography.bodyLarge)
            }
            if (pro && r.open && r.projected > r.now) Muted("이대로 쓰면 ${Fmt.won(r.projected)}쯤 나가요")
            if (r.installments > 0) Muted("이 중 할부 ${Fmt.won(r.installments)}")
        }
        if (pro) Muted("합계 ${Fmt.won(rows.sumOf { it.projected })} (마감일까지 지금 속도로 어림)")
        else TextButton(onClick = { openPro(Feature.MONEY_STATS) }) { Text("이대로면 얼마 나갈지 보기 (프로)") }
    }
}

/**
 * F 1.32 [TAXP-05] 596: spending per day by what the day was (야간 다음 날, 쉬는 날, each shift), the last 90 days.
 * 프로 (돈 통계).
 */
@Composable
internal fun ShiftSpendCard(book: MoneyBook, snapshot: ScheduleSnapshot?, today: LocalDate, gate: Gate) {
    snapshot ?: return
    val rows by produceState<List<TaxPlus.ShiftSpend>>(emptyList(), book, snapshot, today) {
        value = withContext(Dispatchers.Default) {
            runCatching { TaxPlus.byShift(book.entries, today.minusDays(89), today, TaxPlus.dayLabel(snapshot.resolver::resolve)) }.getOrDefault(emptyList())
        }
    }
    if (rows.size < 2) return
    SectionCard(title = "근무별 소비 (90일)", trailing = { if (gate.locked(Feature.MONEY_STATS)) ProPill(locked = true) }) {
        com.bamnat.app.ui.pro.ProLock(Feature.MONEY_STATS, gate) {
            Column {
                TaxPlus.shiftWords(rows)?.let { Text(it, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 4.dp)) }
                val top = rows.maxOf { it.perDay }.coerceAtLeast(1)
                rows.take(6).forEach { r ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Text("${r.label} (${r.days}일)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Text("하루 ${Fmt.won(r.perDay)}", style = MaterialTheme.typography.bodyMedium)
                    }
                    LinearProgressIndicator(progress = { r.perDay.toFloat() / top }, modifier = Modifier.fillMaxWidth())
                }
                Muted("배달·편의점이 늘어나는 날을 알면 미리 장을 보거나 도시락을 챙길 수 있어요.")
            }
        }
    }
}

/** F 1.32 [TAXP-06] 597: this month's sales by how they were paid. Free. */
@Composable
internal fun SalesSplitCard(book: MoneyBook, month: YearMonth) {
    val s = remember(book, month) {
        val credit = com.bamnat.core.work.SalesSplit.creditByDay(book.receivables)
        TaxPlus.salesSplit(book.sales, month.atDay(1), month.atEndOfMonth()) { credit[it] ?: 0 }
    }
    if (s.total <= 0) return
    SectionCard(title = "${month.monthValue}월 매출 나눠 보기") {
        s.rows.forEach { (name, v) ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${Fmt.won(v)} · ${s.share(v)}%", style = MaterialTheme.typography.bodyMedium)
            }
            LinearProgressIndicator(progress = { s.share(v) / 100f }, modifier = Modifier.fillMaxWidth())
        }
        Muted("${s.days}일 · 합계 ${Fmt.won(s.total)}. 카드·배달앱 매출은 부가세 발행세액공제(1.3%)를 받아요.")
        // 1.32 [TAXP-14] 현금 매출 and its 현금영수증, once the user writes them in 하루 마감.
        val cashDays = remember(book.sales, month) { book.sales.filter { YearMonth.from(it.date) == month && it.cash > 0 } }
        if (cashDays.any { it.cashReceipt > 0 }) {
            val cash = cashDays.sumOf { it.cash }
            val issued = cashDays.sumOf { it.cashReceipt.coerceAtMost(it.cash) }
            Muted("현금 ${Fmt.won(cash)} 중 현금영수증 ${Fmt.won(issued)}" +
                if (cash > issued) " · 영수증 없는 현금 ${Fmt.won(cash - issued)}도 신고 매출에 넣어요" else " · 모두 발행했어요")
        } else if (cashDays.isNotEmpty()) {
            Muted("하루 마감에 현금영수증 발행 금액을 적으면 신고 때 빠진 현금이 없는지 볼 수 있어요")
        }
    }
}

/**
 * F 1.32 [TAXP-07] 598 세금 통장: of the day's sales, what to put aside for 부가세 and 종소세 at the year's pace (프로).
 * Shown in 하루 마감.
 */
@Composable
internal fun SetAsideLine(calendarId: String, book: MoneyBook, date: LocalDate, sales: Long) {
    val c = LocalContext.current.appContainer
    val gate by c.featureGate.state.collectAsState()
    val repo = remember(calendarId) { c.tax(calendarId) }
    val settings by repo.settings.collectAsState(initial = null)
    val y = remember(book, settings, date) {
        settings?.let { BusinessTax.year(date.year, date, it.business, book.jobs, book.sales, book.entries) }
    } ?: return
    if (sales <= 0) return
    if (gate.locked(Feature.TAX_FORECAST)) {
        TextButton(onClick = { openPro(Feature.TAX_FORECAST) }) { Text("오늘 매출 중 세금으로 모아 둘 돈 보기 (프로)") }
        return
    }
    val (vat, income) = y.setAside(sales)
    if (vat + income <= 0) return
    Text("세금 통장에 ${Fmt.won(vat + income)} 옮겨 두세요", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    Muted("부가세 몫 ${Fmt.won(vat)} · 종소세 몫 ${Fmt.won(income)} (올해 속도로 어림). 1·7월 부가세, 5월 종소세 때 목돈이 덜 무서워요.")
    y.lineNote?.let { Muted(it) }
}

/** F 1.32 [TAXP-08] 599: a day's card sales and the deposit the app found for it ("맞아요" marks it 입금 확인). 프로. */
@Composable
internal fun CardMatchLines(calendarId: String, book: MoneyBook, today: LocalDate, gate: Gate) {
    val c = LocalContext.current.appContainer
    // 1.32 [TAXP-15] Against what should come in after the fee the user set (가게 돈 설정 › 카드 수수료율).
    val calendar = remember(calendarId) { c.calendars.get(calendarId) }
    val feeBp = calendar?.shopMoney?.cardFeeBp ?: 0
    val matches = remember(book, today, feeBp) { TaxPlus.cardDeposits(book.sales, book.entries, today, feeBp) }
    val found = matches.filter { it.matched }
    val late = matches.filter { it.late }
    if (found.isEmpty() && late.isEmpty()) return
    if (gate.locked(Feature.TAX_FORECAST)) {
        TextButton(onClick = { openPro(Feature.TAX_FORECAST) }) { Text("카드 매출 입금 ${found.size + late.size}건 자동 대조 (프로)") }
        return
    }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("입금 대조", style = MaterialTheme.typography.titleSmall)
    found.take(4).forEach { m ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${Fmt.shortDate(m.date)} 매출 ${Fmt.won(m.card)} → 입금 ${Fmt.won(m.paid)}", style = MaterialTheme.typography.bodyMedium)
                Muted("수수료 ${Fmt.won(m.fee)}" + (if (feeBp > 0) " (설정 ${"%.2f".format(feeBp / 100.0).trimEnd('0').trimEnd('.')}%면 ${Fmt.won(m.card - m.expected)})" else "") +
                    " · " + m.deposits.joinToString(", ") { "${Fmt.shortDate(it.date)} ${it.merchant}" })
                if (m.overFee >= TaxPlus.OVER_FEE_NOTE) Text("설정한 수수료보다 ${Fmt.won(m.overFee)} 더 빠졌어요. 카드사 가맹점 앱에서 수수료율이나 보류를 확인해 보세요.",
                    style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.warning)
            }
            TextButton(onClick = {
                val day = book.sales.firstOrNull { it.date == m.date } ?: return@TextButton
                c.appScope.launch { c.calendars.money(calendarId).save(day.copy(cardSettled = true)) }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("맞아요") }
        }
    }
    if (late.isNotEmpty()) Text("입금을 못 찾은 날 ${late.size}일 · ${Fmt.won(late.sumOf { it.card })}. 카드사 가맹점 앱에서 지급 보류인지 확인해 보세요.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
}

/**
 * F 1.32 [TAXP-09] 600: the year's money as a PDF (프로, 돈 통계) and the year's lines as CSV (free), at the foot of 통계.
 */
@Composable
internal fun YearReportCard(book: MoneyBook, year: Int, snapshot: ScheduleSnapshot?, gate: Gate, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    SectionCard(title = "${year}년 가계부 리포트") {
        Muted("달마다·분류별·결제수단별·근무별 합계와 연말정산에 쓰는 합계를 한 장으로 정리해요. 가게 이름은 표에 넣지 않아요.")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            OutlinedButton(onClick = {
                if (gate.locked(Feature.MONEY_STATS)) { openPro(Feature.MONEY_STATS); return@OutlinedButton }
                scope.launch {
                    val doc = withContext(Dispatchers.Default) {
                        val from = LocalDate.of(year, 1, 1); val to = minOf(LocalDate.of(year, 12, 31), com.bamnat.core.time.Today.date())
                        val shifts = snapshot?.let { s -> runCatching { TaxPlus.byShift(book.entries, from, to, TaxPlus.dayLabel(s.resolver::resolve)) }.getOrNull() } ?: emptyList()
                        TaxPlus.yearDoc(year, book.entries, book.accounts, shifts,
                            com.bamnat.core.pay.YearEndPreview.extrasFrom(book.entries, book.accounts, year, com.bamnat.core.time.Today.date()))
                    }
                    runCatching { com.bamnat.app.report.ReportShare.pdf(context, doc, "bamnat-money-$year.pdf") }.onFailure { onMessage("PDF를 만들지 못했어요") }
                }
            }) { Text(if (gate.locked(Feature.MONEY_STATS)) "PDF (프로)" else "PDF") }
            OutlinedButton(onClick = {
                scope.launch {
                    val csv = withContext(Dispatchers.Default) { TaxPlus.yearCsv(year, book.entries, book.accounts) }
                    runCatching { LedgerExport.share(context, csv, "bamnat-money-$year.csv") }.onFailure { onMessage("공유하지 못했어요") }
                }
            }) { Text("CSV (무료)") }
        }
    }
}

/**
 * F 1.32 [TAXP-10] 601: the year's 3.3% (who paid what and what was held) and the 5월 신고 준비 묶음 PDF (프로). For a
 * calendar with jobs, or 프리랜서 pay records.
 */
@Composable
internal fun FreelanceCard(calendarId: String, book: MoneyBook, today: LocalDate, business: BusinessTax.Year?, gate: Gate) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = remember(calendarId) { c.tax(calendarId) }
    val records by repo.observations.collectAsState(initial = emptyList())
    val year = today.year
    val f = remember(book, records, business, year) { TaxPlus.freelance(year, book.jobs, records, book.entries, business) }
    if (f.payers.isEmpty()) return
    SectionCard(title = "${year}년 3.3% 합계") {
        TaxLine("받기로 한 돈", Fmt.won(f.amount))
        TaxLine("뗀 3.3% (5월에 정산)", Fmt.won(f.withheld), strong = true)
        f.payers.take(4).forEach { TaxLine("  ${it.name} ${it.count}건", Fmt.won(it.amount), muted = true) }
        f.refund?.let { r -> if (gate.allows(Feature.TAX_FORECAST)) Muted(if (r >= 0) "5월 신고 뒤 약 ${Fmt.won(r)} 돌려받을 것 같아요" else "5월에 약 ${Fmt.won(-r)} 더 낼 것 같아요") }
        TextButton(onClick = {
            if (gate.locked(Feature.TAX_FORECAST)) { openPro(Feature.TAX_FORECAST); return@TextButton }
            scope.launch { runCatching { com.bamnat.app.report.ReportShare.pdf(context, TaxPlus.mayPack(f), "bamnat-may-$year.pdf") } }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (gate.locked(Feature.TAX_FORECAST)) "5월 신고 준비 묶음 (프로)" else "5월 신고 준비 묶음 PDF") }
    }
}
