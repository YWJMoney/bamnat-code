package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
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
import com.bamnat.app.ui.theme.bamnat
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
                    val back: suspend () -> Unit = when (val t = m.target) {
                        is DepositMatch.Target.ForJob -> { money.save(DepositMatch.paid(t.job, t.part)); { money.save(t.job) } }
                        is DepositMatch.Target.ForReceivable -> {
                            val paid = money.receive(t.r, m.entry.amount, m.entry.date);
                            { paid?.let { money.delete(it) } }
                        }
                    }
                    // 2.0.0 [RVK-42] (검토 MONEY-16·T06a-06) A wrong '맞아요' is one tap back: the job or 받을 돈 as it was, and the deposit
                    // line again (it was taken out of 수입 or changed, with nowhere to undo it).
                    com.bamnat.app.ui.components.AppSnack.show(what + com.bamnat.core.text.Josa.fit(what, "으로 적었어요"), undo = { back(); money.save(m.entry) })
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
    SectionCard(title = "카드 입금 예정") {
        // 2.0.0 [RVK-42] (검토 U06a-21) Six at a time, and how many more there are: '외 N건 더 보기' opens the rest here.
        var all by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        (if (all) waiting else waiting.take(SHOWN_DEPOSITS)).forEachIndexed { i, e ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val late = e.payDate.isBefore(today)
            DepositRow(words = {
                Text("${Fmt.shortDate(e.payDate)} · ${Fmt.won(e.amount)}", style = MaterialTheme.typography.bodyLarge,
                    color = if (late) MaterialTheme.bamnat.caution else MaterialTheme.colorScheme.onSurface)
                Text("${Fmt.shortDate(e.saleDate)} 카드 매출 ${Fmt.won(e.card)}" + if (late) " · 예정일 지남" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }, button = {
                TextButton(onClick = {
                    val money = c.calendars.money(calendarId)
                    val day = book.sales.firstOrNull { it.date == e.saleDate } ?: return@TextButton
                    c.appScope.launch {
                        money.save(day.copy(cardSettled = true))
                        // 2.0.0 [RVK-42] (검토 MONEY-16·U06a-21) 되돌리기 here, and '확인 풀기' on the day itself afterwards.
                        com.bamnat.app.ui.components.AppSnack.show("${Fmt.shortDate(e.saleDate)} 카드 매출 입금을 확인했어요", undo = { money.save(day) })
                    }
                }) { Text("입금 확인") }
            })
        }
        if (waiting.size > SHOWN_DEPOSITS) {
            TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp)) {
                Text(if (all) "접기" else "외 ${waiting.size - SHOWN_DEPOSITS}건 더 보기")
            }
        }
        val total = waiting.sumOf { it.amount }
        // [J133-41] 순번 721 (1.32 실사용 테스트: 정한 적 없는 1.5%로 셌는데 어디서 바꾸는지 카드에 없었어요): the rate is changed right here.
        var feeOpen by remember { androidx.compose.runtime.mutableStateOf(false) }
        val said = "들어올 카드 매출 ${Fmt.won(total)} · 수수료 ${rules.feeLabel}, ${rules.settleDays}영업일 뒤로 셌어요"
        if (com.bamnat.app.ui.components.bigLetters()) {
            // 2.0.0 [RVK-42] Big letters: the line over its button, so '6,305,970원' is not cut in two beside '수수료율 바꾸기'.
            Text(said, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
            TextButton(onClick = { feeOpen = true }, modifier = Modifier.align(Alignment.End)) { Text("수수료율 바꾸기") }
        } else Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(said, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            TextButton(onClick = { feeOpen = true }) { Text("수수료율 바꾸기") }
        }
        if (feeOpen) com.bamnat.app.ui.components.AppSheet(onDismissRequest = { feeOpen = false }, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                com.bamnat.app.ui.work.ShopMoneySettings(calendarId)
            }
        }
        // F 1.32 [TAXP-08] The deposits that came, set against the days they pay.
        val gate by c.featureGate.state.collectAsStateWithLifecycle()
        CardMatchLines(calendarId, book, today, gate)
    }
}

/** 2.0.0 [RVK-40] One line of the platform month: what it is on the left, the amount on the right. */
@Composable
private fun PlatformLine(label: String, value: String, strong: Boolean = false, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        // 2.0.0 [HQ-75] What the line holds goes under it as its own small line, so big letters never leave one syllable ('험') alone.
        Column(Modifier.weight(1f).padding(end = 8.dp)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            hint?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Text(value, style = if (strong) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
            fontWeight = if (strong) androidx.compose.ui.text.font.FontWeight.Bold else null)
    }
}

/**
 * 2.0.0 [HQ-75] A waiting deposit with its '입금 확인'. Side by side as before; with big letters (130% and up) the words take the whole
 * width and the button sits under them at the start, so the money does not break a word to a line and the button keeps clear of the
 * floating + at the end.
 */
@Composable
private fun DepositRow(button: @Composable () -> Unit, words: @Composable () -> Unit) {
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.3f) {
        Column(Modifier.fillMaxWidth()) {
            words()
            Box(Modifier.offset(x = (-12).dp)) { button() }
        }
    } else {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { words() }
            button()
        }
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
        // 2.0.0 [RVK-40] (검토 MONEY-49·T06b-04) 남은 돈 is what came: the 3.3% is off it, and what it was before is under it. A line each
        // (조건 4), not one sentence that ran to four lines with big letters.
        PlatformLine("쓴 돈", Fmt.won(m.costs), hint = "유류비·정비·보험")
        if (m.fees > 0) PlatformLine("수수료", Fmt.won(m.fees))
        PlatformLine("남은 돈", Fmt.won(m.net), strong = true)
        if (m.withheld > 0) Text("3.3% 떼기 전 ${Fmt.won(m.netBeforeTax)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.End)
        m.perHour?.let { Text("시간당 순수익 ${Fmt.won(it)} · 한 건 ${m.perCount?.let(Fmt::won) ?: "-"}", style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary) }
        payouts.take(4).forEach { p ->
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            val dep = remember(p, book.entries) { com.bamnat.core.money.PlatformWork.depositFor(p, book.entries) }
            // 2.0.0 [HQ-75] A late week is 주의 (orange), not 위험 red; the words take the width with big letters.
            DepositRow(words = {
                Text("${p.platform} ${Fmt.shortDate(p.payDate)} 정산 ${Fmt.won(p.expected)}", style = MaterialTheme.typography.bodyLarge,
                    color = if (p.payDate.isBefore(today.minusDays(2)) && dep == null) MaterialTheme.bamnat.caution else MaterialTheme.colorScheme.onSurface)
                Text("${Fmt.shortDate(p.from)}~${Fmt.shortDate(p.to)} 일한 몫" + (dep?.let { " · ${Fmt.shortDate(it.date)} ${Fmt.won(it.amount)} 들어온 것 같아요" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }, button = {
                TextButton(onClick = {
                    c.appScope.launch {
                        val money = c.calendars.money(calendarId)
                        com.bamnat.core.money.PlatformWork.settle(p).forEach { money.save(it) }
                        // 2.0.0 [RVK-42] (검토 MONEY-16·T06a-06) The week's jobs back to unpaid if this was not its money.
                        com.bamnat.app.ui.components.AppSnack.show("${p.platform} ${Fmt.shortDate(p.payDate)} 정산을 받은 것으로 적었어요",
                            undo = { p.jobs.forEach { money.save(it) } })
                    }
                }) { Text("입금 확인") }
            })
        }
    }
}

/** 2.0.0 [RVK-42] (검토 U06a-21) How many 카드 입금 예정 lines show before '외 N건 더 보기'. */
private const val SHOWN_DEPOSITS = 6
