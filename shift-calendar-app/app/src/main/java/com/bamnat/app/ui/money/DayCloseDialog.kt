package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import com.bamnat.app.ui.theme.bamnat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.bamnat.core.money.MoneyKind
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
 *
 * 2.0.0 [RVK-05] (검토 MONEY-12·PC-04·T06a-04·U06a-20): twelve fields are too many to lose to a tap outside or two backs, so this is a
 * [com.bamnat.app.ui.components.FormSheet] that asks before anything typed goes, and its open flag is saveable (SalesSection).
 * [RVK-06] (MONEY-30·T06a-03): the day's spending already in the ledger is said under 경비, so the same 재료비 is not taken twice.
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
    // [RVK-06] What the ledger already has for this day: it is taken off the month by itself, apart from 경비.
    val spentInLedger = remember(book.entries, date) { book.entries.filter { it.date == date && it.kind == MoneyKind.EXPENSE && it.amount > 0 } }
    val unsaved = card != amountDigits(initial?.card ?: 0) || cash != amountDigits(initial?.cash ?: 0) ||
        transfer != amountDigits(initial?.transfer ?: 0) || delivery != amountDigits(initial?.delivery ?: 0) ||
        total != amountDigits(initial?.sales ?: 0) || costs != amountDigits(initial?.costs ?: 0) ||
        counted != initial?.cashCounted?.toString().orEmpty() || receipt != amountDigits(initial?.cashReceipt ?: 0) ||
        proven != amountDigits(initial?.costsProven ?: 0) || credit.isNotBlank() || creditWho.isNotBlank() ||
        note != initial?.note.orEmpty() || weather != savedWeather?.weather || temperature != savedWeather?.temperature?.toString().orEmpty()

    fun build() = DailySales(
        date = date, sales = sales, costs = costs.toAmount(), note = note.trim(),
        card = card.toAmount(), cash = cash.toAmount(), transfer = transfer.toAmount(), delivery = delivery.toAmount(),
        cashCounted = counted.toLongOrNull(), cardSettled = initial?.cardSettled == true && card.toAmount() == initial.card,
        cashReceipt = receipt.toAmount().coerceAtMost(cash.toAmount()),
        costsProven = proven.toAmount().coerceAtMost(costs.toAmount()),
    )

    com.bamnat.app.ui.components.FormSheet(
        onDismissRequest = onDismiss,
        unsaved = unsaved,
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
                // 1.37 PC 736fb7a6 (자영업 마감: 숫자를 넣는 사이 위에 줄이 생기고 사라져 칸이 밀리고, 경비에 넣을 것이 '센 현금'에 들어감):
                // every field stays where it was while typing. The rows that wait on an amount are there from the start and turn
                // on with it, the sum keeps its field's height, and the cash note comes below, next to 센 현금.
                // 1.32 [TAXP-14] 현금영수증 발행 확인 (조사 4장): cash sales without one are what 신고 most often misses.
                val hasCash = cash.toAmount() > 0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("그중 현금영수증 발행", receipt, { receipt = it }, Modifier.weight(1f), allowZero = true, enabled = hasCash)
                    com.bamnat.app.ui.components.FilterChip(selected = hasCash && receipt.toAmount() == cash.toAmount(), enabled = hasCash,
                        onClick = { receipt = cash }, label = { Text("전부 발행") })
                }
                // PC ef116bf4: 64dp, the field's own height (56dp left a 21px step when it turned into the sum).
                Box(Modifier.fillMaxWidth().heightIn(min = 64.dp), contentAlignment = Alignment.CenterStart) {
                    if (parts == 0L) AmountField("매출 합계", total, { total = it })
                    // 1.36 실사용 S20 (나눠 적으면 '매출 합계' 칸이 사라져 저장 전 합계가 안 보여요): the sum stays in the same place.
                    else Text("매출 합계 ${Fmt.won(parts + earlierCredit)}" + if (earlierCredit > 0) " (외상 ${Fmt.won(earlierCredit)} 포함)" else "",
                        style = MaterialTheme.typography.titleSmall)
                }
                Text(if (parts == 0L) "결제 수단을 나눠 적으면 합계가 저절로 나와요." else "결제 수단별 금액을 더한 합계예요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountField("경비 (재료비·인건비 등)", costs, { costs = it })
                if (spentInLedger.isNotEmpty()) {
                    Text("가계부에 적은 이날 지출 ${spentInLedger.size}건 ${Fmt.won(spentInLedger.sumOf { it.amount })}은 따로 빠져요. 경비에는 그 밖의 것만 적어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // [J133-29] 1.33 (실사용 테스트 "경비 칸에서 증빙 여부를 고를 수 없어 부가세가 크게 나와요").
                val hasCosts = costs.toAmount() > 0
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AmountField("그중 증빙 있음", proven, { proven = it }, Modifier.weight(1f), allowZero = true, enabled = hasCosts)
                    com.bamnat.app.ui.components.FilterChip(selected = hasCosts && proven.toAmount() == costs.toAmount(), enabled = hasCosts,
                        onClick = { proven = costs }, label = { Text("전부 있음") })
                }
                com.bamnat.app.ui.components.Explain("dayclose.proven", "세금계산서·카드 영수증·현금영수증을 받은 경비는 부가세 예상에서 빠져요. " +
                    "인건비는 보통 증빙이 아니에요.", summary = "증빙 있는 경비는 부가세 예상에서 빠져요")
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                CashCheck(initial?.copy(cash = cash.toAmount(), card = card.toAmount(), transfer = transfer.toAmount(), delivery = delivery.toAmount())
                    ?: DailySales(date, sales, cash = cash.toAmount(), card = card.toAmount(), transfer = transfer.toAmount(), delivery = delivery.toAmount()),
                    rules, book, counted) { counted = it }
                // PC 8eae2521 (자판이 열린 채 현금을 넣으면 '센 현금' 칸이 172px 내려감): the note comes under 센 현금, so no field moves.
                val missing = if (hasCash) (cash.toAmount() - receipt.toAmount()).coerceAtLeast(0) else 0
                if (missing > 0) com.bamnat.app.ui.components.Explain("dayclose.cashreceipt",
                    "현금영수증 없이 받은 현금 ${Fmt.won(missing)}도 매출이에요. 부가세·종소세 신고 때 빠뜨리지 마세요. " +
                        "손님이 원하면 발행해야 하고, 의무발행업종은 10만 원 이상이면 원하지 않아도 발행해야 해요.",
                    summary = "현금영수증 없이 받은 현금 ${Fmt.won(missing)}도 매출이에요", iconOnly = false)
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
            com.bamnat.app.ui.components.Button(onClick = {
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
            color = if (diff == 0L) cal.income else MaterialTheme.bamnat.caution,
        )
    }
}
