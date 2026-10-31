package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.Receivable
import com.bamnat.core.money.ReceivableKind
import com.bamnat.core.work.CashDrawer
import com.bamnat.core.work.ShopMoney
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * [SHOP-01][SHOP-03][SHOP-06] 하루 마감: the day's sales by how they were paid (카드·현금·계좌이체·
 * 배달앱) adding up as they are typed, the total typed by itself when nothing is split, the day's
 * costs and a memo. 센 현금 is checked against the cash that should be in the till, and a sale on
 * credit (외상) is written as a receivable with who owes it, and counted in the day's sales.
 */
@Composable
fun DayCloseDialog(date: LocalDate, initial: DailySales?, onSave: (DailySales) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val book = rememberLedger(null)
    val calendar by c.calendars.activeCalendar.collectAsStateWithLifecycle(c.calendars.current())
    val rules = calendar.shopMoney ?: ShopMoney()
    val credits = remember(book, date) { book.receivables.filter { it.date == date && it.kind == ReceivableKind.SALE } }
    var card by rememberSaveable { mutableStateOf(amountDigits(initial?.card ?: 0)) }
    var cash by rememberSaveable { mutableStateOf(amountDigits(initial?.cash ?: 0)) }
    var transfer by rememberSaveable { mutableStateOf(amountDigits(initial?.transfer ?: 0)) }
    var delivery by rememberSaveable { mutableStateOf(amountDigits(initial?.delivery ?: 0)) }
    var total by rememberSaveable { mutableStateOf(amountDigits(initial?.sales ?: 0)) }
    var costs by rememberSaveable { mutableStateOf(amountDigits(initial?.costs ?: 0)) }
    var counted by rememberSaveable { mutableStateOf(initial?.cashCounted?.toString().orEmpty()) }
    // 1.32 [TAXP-14] Of the cash sales, what a 현금영수증 was issued for.
    var receipt by rememberSaveable { mutableStateOf(amountDigits(initial?.cashReceipt ?: 0)) }
    // [J133-29] Of the costs, what came with a VAT proof.
    var proven by rememberSaveable { mutableStateOf(amountDigits(initial?.costsProven ?: 0)) }
    var credit by rememberSaveable { mutableStateOf("") }
    var creditWho by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf(initial?.note.orEmpty()) }
    // [SHOP-10] 날씨 메모, kept beside the sales in the calendar's own file.
    val tools = remember { com.bamnat.app.data.WorkToolsStore.get(c.appContext) }
    val savedWeather = remember(calendar.id, date) { tools.current(calendar.id).weather[date] }
    var weather by rememberSaveable { mutableStateOf(savedWeather?.weather) }
    var temperature by rememberSaveable { mutableStateOf(savedWeather?.temperature?.toString().orEmpty()) }
    val parts = card.toAmount() + cash.toAmount() + transfer.toAmount() + delivery.toAmount()
    // 외상 written before for this day is already in its sales; a new one joins them on save.
    val earlierCredit = if (initial != null && initial.byMethod > 0) (initial.sales - initial.byMethod).coerceAtLeast(0) else 0
    val sales = if (parts > 0) parts + earlierCredit + credit.toAmount() else total.toAmount() + credit.toAmount()
    val net = sales - costs.toAmount()

    fun build() = DailySales(
        date = date, sales = sales, costs = costs.toAmount(), note = note.trim(),
        card = card.toAmount(), cash = cash.toAmount(), transfer = transfer.toAmount(), delivery = delivery.toAmount(),
        cashCounted = counted.toLongOrNull(), cardSettled = initial?.cardSettled == true && card.toAmount() == initial.card,
        cashReceipt = receipt.toAmount().coerceAtMost(cash.toAmount()),
        costsProven = proven.toAmount().coerceAtMost(costs.toAmount()),
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Fmt.dayTitle(date)} 마감") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("카드", card, { card = it }, Modifier.weight(1f))
                    AmountField("현금", cash, { cash = it }, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("계좌이체", transfer, { transfer = it }, Modifier.weight(1f))
                    AmountField("배달앱", delivery, { delivery = it }, Modifier.weight(1f))
                }
                // 1.32 [TAXP-14] 현금영수증 발행 확인 (조사 4장): cash sales without one are what 신고 most often misses.
                if (cash.toAmount() > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmountField("그중 현금영수증 발행", receipt, { receipt = it }, Modifier.weight(1f), allowZero = true)
                        com.bamnat.app.ui.components.FilterChip(selected = receipt.toAmount() == cash.toAmount(), onClick = { receipt = cash },
                            label = { Text("전부 발행") })
                    }
                    val missing = (cash.toAmount() - receipt.toAmount()).coerceAtLeast(0)
                    if (missing > 0) Text("현금영수증 없이 받은 현금 ${Fmt.won(missing)}도 매출이에요. 부가세·종소세 신고 때 빠뜨리지 마세요. " +
                        "손님이 원하면 발행해야 하고, 의무발행업종은 10만 원 이상이면 원하지 않아도 발행해야 해요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (parts == 0L) {
                    AmountField("매출 합계", total, { total = it })
                    Text("결제 수단을 나눠 적으면 합계가 저절로 나와요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    // 1.36 실사용 S20 (나눠 적으면 '매출 합계' 칸이 사라져 저장 전 합계가 안 보여요): the sum stays in the same place.
                    Text("매출 합계 ${Fmt.won(parts + earlierCredit)}" + if (earlierCredit > 0) " (외상 ${Fmt.won(earlierCredit)} 포함)" else "",
                        style = MaterialTheme.typography.titleSmall)
                }
                AmountField("경비 (재료비·인건비 등)", costs, { costs = it })
                // [J133-29] 1.33 (실사용 테스트 "경비 칸에서 증빙 여부를 고를 수 없어 부가세가 크게 나와요").
                if (costs.toAmount() > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AmountField("그중 증빙 있음", proven, { proven = it }, Modifier.weight(1f), allowZero = true)
                        com.bamnat.app.ui.components.FilterChip(selected = proven.toAmount() == costs.toAmount(), onClick = { proven = costs },
                            label = { Text("전부 있음") })
                    }
                    Text("세금계산서·카드 영수증·현금영수증을 받은 경비는 부가세 예상에서 빠져요. 인건비는 보통 증빙이 아니에요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                CashCheck(initial?.copy(cash = cash.toAmount(), card = card.toAmount(), transfer = transfer.toAmount(), delivery = delivery.toAmount())
                    ?: DailySales(date, sales, cash = cash.toAmount(), card = card.toAmount(), transfer = transfer.toAmount(), delivery = delivery.toAmount()),
                    rules, book, counted) { counted = it }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                // [SHOP-06] 외상: a sale not paid yet, followed up in 받을 돈.
                Text("외상 (나중에 받을 돈)", style = MaterialTheme.typography.labelLarge)
                credits.forEach { r ->
                    Text("${r.who.ifBlank { "이름 없음" }} ${Fmt.won(r.amount)}" + if (r.left == 0L) " · 받음" else " · 남은 돈 ${Fmt.won(r.left)}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("외상 금액", credit, { credit = it }, Modifier.weight(1f))
                    OutlinedTextField(value = creditWho, onValueChange = { creditWho = it.take(20) }, singleLine = true,
                        label = { Text("누가") }, modifier = Modifier.weight(1f))
                }
                OutlinedTextField(value = note, onValueChange = { note = it.take(100) }, singleLine = true,
                    label = { Text("메모 (선택)") }, modifier = Modifier.fillMaxWidth())
                com.bamnat.app.ui.work.WeatherPicker(weather, temperature, { weather = it }, { temperature = it })
                Text("매출 ${Fmt.won(sales)}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("순수익 ${Fmt.won(net)}", style = MaterialTheme.typography.bodyLarge,
                    color = if (net < 0) LocalCalendarColors.current.expense else MaterialTheme.colorScheme.onSurface)
                // F 1.32 [TAXP-07] 세금 통장: what of today's sales to put aside.
                SetAsideLine(calendar.id, book, date, sales)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val owed = credit.toAmount()
                if (owed > 0) {
                    val id = calendar.id
                    c.appScope.launch {
                        c.calendars.money(id).save(Receivable(
                            who = creditWho.trim(), title = "${Fmt.monthDay(date)} 외상", amount = owed, date = date,
                            kind = ReceivableKind.SALE, note = "하루 마감에서 적음",
                        ))
                    }
                }
                com.bamnat.app.ui.work.saveWeather(tools, calendar.id, date, weather, temperature)
                onSave(build())
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** [SHOP-03] 시재: the cash that should be in the till (시작 시재 + 현금 매출 − 현금 지출) against what was counted. */
@Composable
private fun CashCheck(day: DailySales, rules: ShopMoney, book: MoneyBook, counted: String, onCounted: (String) -> Unit) {
    val cashAccounts = remember(book.accounts) { book.accounts.filter { it.type == AccountType.CASH }.map { it.id }.toSet() }
    val spent = remember(book.entries, day.date, cashAccounts) { CashDrawer.cashSpent(book.entries, day.date, cashAccounts) }
    val check = CashDrawer.check(day.copy(cashCounted = counted.toLongOrNull()), rules.openingCash, spent)
    AmountField("센 현금 (선택)", counted, { onCounted(it) }, allowZero = true)
    Text(
        "있어야 할 현금 ${Fmt.won(check.expected)}" +
            " (시작 ${Fmt.won(rules.openingCash)} + 현금 매출 ${Fmt.won(if (day.byMethod > 0) day.cash else 0)}" +
            (if (spent > 0) " − 현금 지출 ${Fmt.won(spent)}" else "") + ")",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    check.difference?.let { diff ->
        val cal = LocalCalendarColors.current
        Text(
            when {
                diff == 0L -> "딱 맞아요"
                diff > 0 -> "${Fmt.won(diff)} 더 있어요"
                else -> "${Fmt.won(-diff)} 모자라요"
            },
            style = MaterialTheme.typography.titleSmall,
            color = if (diff == 0L) cal.income else cal.warning,
        )
    }
}
