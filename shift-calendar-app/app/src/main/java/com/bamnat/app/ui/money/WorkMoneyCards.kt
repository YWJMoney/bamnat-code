package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.money.DepositMatch
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.work.CardSettlement
import com.bamnat.core.work.ShopMoney
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [FLD-06][SHOP-02] Cards in 돈 > 요약 for work calendars: a deposit that looks like a job's money
 * asks whether it is (field work), and card deposits still to come wait to be ticked (a shop).
 */
@Composable
fun WorkMoneyCards(calendarId: String, book: MoneyBook, style: WorkStyle?, today: LocalDate) {
    val c = LocalContext.current.appContainer
    val calendars by c.calendars.calendars.collectAsStateWithLifecycle()
    val cal = calendars.firstOrNull { it.id == calendarId }
    // [GUIDE-05] 입금 확인 can be turned off for its calendar.
    if (style == WorkStyle.FIELD && cal?.field?.platformMode == true) PlatformMoneyCard(c, calendarId, book, cal.field!!, today)
    if (style == WorkStyle.FIELD) com.bamnat.app.ui.guide.FeatureCard("field.deposit", calendarId) { DepositMatchCard(c, calendarId, book, cal?.field ?: FieldRules(), today) }
    if (style == WorkStyle.BUSINESS) CardDepositsCard(c, calendarId, book, cal?.shopMoney ?: ShopMoney(), today)
}

/** [FLD-06] "이 입금, ○○ 일감 잔금인가요?" — 맞아요 ticks it paid, 아니에요 stops asking. */
@Composable
private fun DepositMatchCard(c: AppContainer, calendarId: String, book: MoneyBook, rules: FieldRules, today: LocalDate) {
    val matches = remember(book, rules.notMatched, today) { DepositMatch.pending(book.entries, book.jobs, book.receivables, rules.notMatched, today) }
    val m = matches.firstOrNull() ?: return
    val what = when (val t = m.target) {
        is DepositMatch.Target.ForJob -> "‘${t.name}’ 일감 " + when (t.part) {
            DepositMatch.Part.DEPOSIT -> "선금"
            DepositMatch.Part.BALANCE -> "잔금"
            DepositMatch.Part.ALL -> "돈"
        }
        is DepositMatch.Target.ForReceivable -> "‘${t.name}’ 받을 돈"
    }
    SectionCard(title = "입금 확인") {
        Text("${Fmt.shortDate(m.entry.date)} ${m.entry.title} ${Fmt.won(m.entry.amount)}", style = MaterialTheme.typography.bodyLarge)
        Text("이 입금, ${what}인가요?", style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 4.dp))
        Text("맞으면 받은 돈으로 바꿔요. 같은 돈이 두 번 세지 않게 이 입금은 수입에서 빼고, 계좌 잔액은 그대로 둬요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
            OutlinedButton(onClick = {
                c.calendars.updateField(calendarId) { it.copy(notMatched = (it.notMatched + m.entry.id).takeLast(FieldRules.KEEP_NOT_MATCHED)) }
            }) { Text("아니에요") }
            FilledTonalButton(onClick = {
                val money = c.calendars.money(calendarId)
                c.appScope.launch {
                    // The line stays as money into its account when it had one, so the balance is kept.
                    val kept = DepositMatch.kept(m.entry, book.accounts, m.target.name)
                    if (kept != null) money.save(kept) else money.delete(m.entry)
                    when (val t = m.target) {
                        is DepositMatch.Target.ForJob -> money.save(DepositMatch.paid(t.job, t.part))
                        is DepositMatch.Target.ForReceivable -> money.receive(t.r, m.entry.amount, m.entry.date)
                    }
                }
            }) { Text("맞아요") }
        }
        if (matches.size > 1) {
            Text("확인할 입금이 ${matches.size - 1}건 더 있어요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

/** [SHOP-02] 카드 입금 예정: each day's card sales less the fee, due a few business days later, with 입금 확인. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun CardDepositsCard(c: AppContainer, calendarId: String, book: MoneyBook, rules: ShopMoney, today: LocalDate) {
    val waiting = remember(book.sales, rules, today) {
        CardSettlement.waiting(CardSettlement.expected(book.sales, rules, KoreanHolidays::isHoliday), today.plusDays(7)).sortedBy { it.payDate }
    }
    if (waiting.isEmpty()) return
    val cal = LocalCalendarColors.current
    SectionCard(title = "카드 입금 예정") {
        waiting.take(6).forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val late = e.payDate.isBefore(today)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${Fmt.shortDate(e.payDate)} · ${Fmt.won(e.amount)}", style = MaterialTheme.typography.bodyLarge,
                        color = if (late) cal.warning else MaterialTheme.colorScheme.onSurface)
                    Text("${Fmt.shortDate(e.saleDate)} 카드 매출 ${Fmt.won(e.card)}" + if (late) " · 예정일 지남" else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    val money = c.calendars.money(calendarId)
                    val day = book.sales.firstOrNull { it.date == e.saleDate } ?: return@TextButton
                    c.appScope.launch { money.save(day.copy(cardSettled = true)) }
                }) { Text("입금 확인") }
            }
        }
        val total = waiting.sumOf { it.amount }
        // [J133-41] 순번 721 (1.32 실사용 테스트: 정한 적 없는 1.5%로 셌는데 어디서 바꾸는지 카드에 없었어요): the rate is changed right here.
        var feeOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("들어올 카드 매출 ${Fmt.won(total)} · 수수료 ${rules.feeLabel}, ${rules.settleDays}영업일 뒤로 셌어요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { feeOpen = true }) { Text("수수료율 바꾸기") }
        }
        if (feeOpen) com.bamnat.app.ui.components.AppSheet(onDismissRequest = { feeOpen = false }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.bamnat.app.ui.work.ShopMoneySettings(calendarId)
            }
        }
        // F 1.32 [TAXP-08] The deposits that came, set against the days they pay.
        val gate by c.featureGate.state.collectAsStateWithLifecycle()
        CardMatchLines(calendarId, book, today, gate)
    }
}

/**
 * G 1.32 [WTYP-02] 604: this month by platform (건수, 금액, 운행 시간), costs (each day's and the ledger's 자동차·교통), 시간당
 * 순수익, and the weeks still to be paid with the deposit that looks like each, "입금 확인" marking the week paid.
 */
@Composable
private fun PlatformMoneyCard(c: AppContainer, calendarId: String, book: MoneyBook, rules: FieldRules, today: LocalDate) {
    val month = java.time.YearMonth.from(today)
    val m = remember(book, month, rules) { com.bamnat.core.money.PlatformWork.month(month, book.jobs, book.entries, rules) }
    val payouts = remember(book, rules) { com.bamnat.core.money.PlatformWork.payouts(book.jobs, rules) }
    if (m.rows.isEmpty() && payouts.isEmpty()) return
    SectionCard(title = "${month.monthValue}월 플랫폼 수입") {
        m.rows.forEach { r ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("${r.platform} ${r.count}건", style = MaterialTheme.typography.bodyLarge)
                    // G 1.32 [WTYP-23] 콜 출처마다 수수료·보험료.
                    if (r.fees > 0) Text("수수료·보험 ${Fmt.won(r.fees)} 빼면 ${Fmt.won(r.amount - r.fees)}", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Fmt.won(r.amount), style = MaterialTheme.typography.bodyLarge)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 4.dp))
        Text("쓴 돈 ${Fmt.won(m.costs)} (유류비·정비·보험 포함)" + (if (m.fees > 0) " · 수수료 ${Fmt.won(m.fees)}" else "") + " · 남은 돈 ${Fmt.won(m.net)}",
            style = MaterialTheme.typography.bodyMedium)
        m.perHour?.let { Text("시간당 순수익 ${Fmt.won(it)} · 한 건 ${m.perCount?.let(Fmt::won) ?: "-"}", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary) }
        payouts.take(4).forEach { p ->
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val dep = remember(p, book.entries) { com.bamnat.core.money.PlatformWork.depositFor(p, book.entries) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${p.platform} ${Fmt.shortDate(p.payDate)} 정산 ${Fmt.won(p.expected)}", style = MaterialTheme.typography.bodyLarge,
                        color = if (p.payDate.isBefore(today.minusDays(2)) && dep == null) LocalCalendarColors.current.warning else MaterialTheme.colorScheme.onSurface)
                    Text("${Fmt.shortDate(p.from)}~${Fmt.shortDate(p.to)} 일한 몫" + (dep?.let { " · ${Fmt.shortDate(it.date)} ${Fmt.won(it.amount)} 들어온 것 같아요" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = {
                    c.appScope.launch { com.bamnat.core.money.PlatformWork.settle(p).forEach { c.calendars.money(calendarId).save(it) } }
                }) { Text("입금 확인") }
            }
        }
    }
}
