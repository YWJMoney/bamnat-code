package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.money.BudgetStatus
import com.bamnat.core.money.LedgerSmarts
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.UpcomingPayments
import java.time.LocalDate

/**
 * E 1.32 [LEDX-07] 587: where the month ends at this pace (fixed costs still to leave included) and, with a budget, what
 * may be spent today. Only for the month on today.
 */
@Composable
internal fun PaceCard(book: MoneyBook, today: LocalDate, budget: BudgetStatus) {
    val leftFixed = remember(book, today) { UpcomingPayments.leftThisMonth(book.fixed, book.entries, today) }
    val pace = remember(book, today, budget) { LedgerSmarts.pace(book.entries, today, budget.total?.budget, leftFixed) }
    if (pace.spent <= 0 && leftFixed <= 0) return
    SectionCard(title = "이번 달 흐름") {
        Text("지금까지 ${Fmt.won(pace.spent)} · 이대로면 월말 ${Fmt.won(pace.projected)}", style = MaterialTheme.typography.titleSmall)
        pace.budget?.let { b ->
            val over = pace.projected - b
            Text(if (over > 0) "예산보다 ${Fmt.won(over)} 넘을 것 같아요" else "예산 안에서 끝날 것 같아요 (${Fmt.won(-over)} 남음)",
                style = MaterialTheme.typography.bodySmall,
                color = if (over > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        pace.todayLeft?.let { Text("오늘 쓸 수 있는 돈 ${Fmt.won(it)} (남은 ${pace.daysLeft}일)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp)) }
            ?: Text("예산을 정하면 오늘 쓸 수 있는 돈을 알려 드려요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val inst = remember(book, today) { LedgerSmarts.installmentsLeft(book.entries, today) }
        if (inst > 0) Text("할부로 남은 돈 ${Fmt.won(inst)} (이번 달 회차 포함)", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * E 1.32 [LEDX-09] 589: the payments that come back every month, what they cost a year, a price that went up, and
 * "아직 쓰나요?" for one paid three months or more and not looked at in 90 days.
 */
@Composable
internal fun SubscriptionsCard(entries: List<MoneyEntry>, today: LocalDate, lookedAt: Map<String, LocalDate>,
                               onStillUsing: (String) -> Unit, onOpen: (String) -> Unit) {
    val subs = remember(entries, today, lookedAt) { LedgerSmarts.subscriptions(entries, today, lookedAt) }
    if (subs.isEmpty()) return
    SectionCard(title = "구독 점검 · 한 달 ${Fmt.won(subs.sumOf { it.monthly })}") {
        Text("1년이면 ${Fmt.won(subs.sumOf { it.yearly })}이에요. 안 쓰는 건 해지하면 바로 아껴요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        subs.take(8).forEach { s ->
            Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(s.place, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${Fmt.won(s.monthly)}/월", style = MaterialTheme.typography.bodyMedium)
                }
                val notes = listOfNotNull("${s.months}달째", s.raised?.let { "${Fmt.won(it)} 올랐어요" })
                Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                    color = if (s.raised != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if (s.stale) Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("아직 쓰나요?", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onStillUsing(s.place) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("써요") }
                    TextButton(onClick = { onOpen(s.place) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내역 보기") }
                }
            }
        }
    }
}

/**
 * E 1.32 [LEDX-10] 590: this year's card spending against the 총급여 25% 문턱 (조세특례제한법 제126조의2), and which card to use
 * now. Shown with pay set up (the 문턱 comes from the year's pay).
 */
@Composable
internal fun CardGaugeCard(book: MoneyBook, today: LocalDate, yearGross: Long?) {
    val gauge = remember(book, today, yearGross) { yearGross?.let { LedgerSmarts.cardGauge(book.entries, book.accounts, today.year, it) } } ?: return
    SectionCard(title = "카드 소득공제 문턱 (${today.year}년)") {
        LinearProgressIndicator(progress = { gauge.share.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        Text("쓴 돈 ${Fmt.won(gauge.spent)} / 문턱 ${Fmt.won(gauge.threshold)} (총급여의 25%)", style = MaterialTheme.typography.bodyMedium)
        Text("신용카드 ${Fmt.won(gauge.credit)} · 체크카드·현금·페이 ${Fmt.won(gauge.debitAndCash)}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(gauge.tip, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 4.dp))
        Text("통장·카드에 카드 종류를 넣으면 더 정확해요. 전통시장·대중교통·문화비는 공제율이 더 높아요(40~80%).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
