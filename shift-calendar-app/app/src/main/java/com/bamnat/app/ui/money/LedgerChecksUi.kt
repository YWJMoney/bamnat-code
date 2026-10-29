package com.bamnat.app.ui.money

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.LedgerChecks
import com.bamnat.core.money.LedgerSmarts
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyEntry
import java.time.LocalDate
import java.time.YearMonth

/**
 * 1.32 [LEDX-16][LEDX-19] 확인할 것: what the phone could not decide alone and asks once. A refund whose payment it
 * did not find (one tap picks it, and the note says what is left), and a card the texts used that is not added yet
 * ("체크카드 맞나요?", so the 25% 문턱 and the 연말정산 count it right). Shown only while there is something to ask.
 */
@Composable
internal fun LedgerAsksCard(book: MoneyBook, today: LocalDate, vm: MoneyViewModel) {
    val c = LocalContext.current.appContainer
    val prefs by c.autoRecordPrefs.state.collectAsStateWithLifecycle()
    val refunds = remember(book) { book.entries.filter { LedgerChecks.isUnmatchedRefund(it) }.sortedByDescending { it.date } }
    val cards = remember(book, today, prefs.askedCards) {
        LedgerChecks.unknownCards(book.entries, book.accounts, today.minusDays(90)).filter { it.last4 !in prefs.askedCards }
    }
    if (refunds.isEmpty() && cards.isEmpty()) return
    var picking by remember { mutableStateOf<MoneyEntry?>(null) }
    SectionCard(title = "확인할 것 ${refunds.size + cards.size.coerceAtMost(2)}건") {
        refunds.take(5).forEach { r ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { picking = r }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("환불 받은 돈 ${Fmt.won(-r.amount)} · ${r.title}", style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Muted("${Fmt.shortDate(r.date)} · 원래 결제를 골라 주시면 남은 금액까지 맞춰요")
                }
                TextButton(onClick = { picking = r }) { Text("고르기") }
            }
        }
        if (refunds.isNotEmpty() && cards.isNotEmpty()) HorizontalDivider(Modifier.padding(vertical = 6.dp))
        cards.take(2).forEach { u ->
            val name = (u.issuer ?: "카드") + " ${u.last4}"
            Column(Modifier.padding(vertical = 4.dp)) {
                Text(when (u.guess) {
                    AccountType.CHECK -> "$name, 체크카드 맞나요?"
                    AccountType.CREDIT -> "$name, 신용카드 맞나요?"
                    else -> "$name, 어떤 카드예요?"
                }, style = MaterialTheme.typography.bodyLarge)
                Muted("최근 90일 ${u.lines}번 · ${Fmt.won(u.spent)}. 정해 두면 소득공제 문턱과 카드값을 바르게 계산해요")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        vm.saveAccount(MoneyAccount(name = name.take(MoneyAccount.MAX_NAME), type = AccountType.CHECK, last4 = u.last4))
                        c.autoRecordPrefs.askedCard(u.last4)
                    }) { Text("체크카드") }
                    OutlinedButton(onClick = {
                        vm.saveAccount(MoneyAccount(name = name.take(MoneyAccount.MAX_NAME), type = AccountType.CREDIT, last4 = u.last4))
                        c.autoRecordPrefs.askedCard(u.last4)
                    }) { Text("신용카드") }
                    TextButton(onClick = { c.autoRecordPrefs.askedCard(u.last4) }) { Text("나중에") }
                }
            }
        }
        if (cards.isNotEmpty()) Muted("신용카드는 결제일을 넣으면 다음 카드값도 보여요. 가계부 › 요약 › 통장·카드에서 고쳐요")
    }
    picking?.let { r -> RefundPickDialog(r, book.entries, onPick = { t -> vm.saveEntry(LedgerChecks.linkRefund(r, t, book.entries)); picking = null },
        onKeep = { vm.saveEntry(r.copy(note = r.note.split(" · ").filter { it != "환불 받은 돈" && it != "원래 결제를 골라 주세요" }.joinToString(" · "), reviewed = true)); picking = null },
        onDismiss = { picking = null }) }
}

/** 1.32 [LEDX-16] The payments a refund may undo, same place or card first; one tap ties it. */
@Composable
internal fun RefundPickDialog(refund: MoneyEntry, entries: List<MoneyEntry>, onPick: (MoneyEntry) -> Unit, onKeep: () -> Unit, onDismiss: () -> Unit) {
    val options = remember(refund, entries) { LedgerChecks.refundCandidates(refund, entries) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("어느 결제의 환불이에요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("${Fmt.won(-refund.amount)}을 돌려받았어요. 원래 결제를 고르면 분류가 같아지고 남은 금액도 적어 둬요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (options.isEmpty()) Muted("90일 안에 이만큼 쓴 결제가 없어요. 그냥 환불로 두셔도 돼요")
                options.forEach { e ->
                    val back = LedgerChecks.refundedBefore(e, entries, except = refund.id)
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onPick(e) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Muted(listOfNotNull(Fmt.shortDate(e.date), e.category, back.takeIf { it > 0 }?.let { "이미 ${Fmt.won(it)} 돌려받음" }).joinToString(" · "))
                        }
                        Text(Fmt.won(e.amount), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onKeep) { Text("그냥 환불로 두기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/**
 * 1.32 [LEDX-17] 할부 청구 기준: the next six card bills and the 할부 each carries (회차 and 원금, and the 수수료 of a
 * 유이자 할부 the user set), with what is still owed. Shown when there is a 할부.
 */
@Composable
internal fun InstallmentPlanCard(book: MoneyBook, today: LocalDate) {
    val plan = remember(book, today) { LedgerChecks.installmentPlan(book.entries, book.accounts, YearMonth.from(today), 6) }
    if (plan.all { it.rows.isEmpty() }) return
    val (owed, fees) = remember(book, today) { LedgerChecks.owedFrom(book.entries, book.accounts, today) }
    var open by rememberSaveable { mutableStateOf<String?>(null) }
    SectionCard(title = "할부 청구 기준") {
        Text("남은 할부 ${Fmt.won(owed)}" + if (fees > 0) " · 수수료 ${Fmt.won(fees)}" else "", style = MaterialTheme.typography.titleSmall)
        plan.filter { it.rows.isNotEmpty() }.forEach { b ->
            val key = b.month.toString()
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable { open = if (open == key) null else key }.padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text(Fmt.monthShort(b.month) + " 청구", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(Fmt.won(b.total), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            }
            if (open == key) b.rows.forEach { r ->
                Row(Modifier.fillMaxWidth().padding(start = 12.dp, bottom = 2.dp)) {
                    Text("${r.entry.title} ${r.round}/${r.entry.installments}회", style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text(Fmt.won(r.principal) + if (r.fee > 0) " + 수수료 ${Fmt.won(r.fee)}" else "", style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Muted("달을 누르면 그달 나갈 할부가 보여요. 유이자 할부는 기록을 눌러 ‘할부 수수료(연 %)’를 넣으면 수수료까지 계산해요")
    }
}

/**
 * 1.32 [LEDX-15][LEDX-18] 결제 알림 › 더 정확하게 적기: the user's own names (a deposit from one is a move between their
 * own accounts), the card's 해외 이용 수수료, and their own rates (learned when a line is fixed to the card's bill).
 */
@Composable
internal fun CaptureTuneDialog(onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val prefs by c.autoRecordPrefs.state.collectAsStateWithLifecycle()
    var names by rememberSaveable { mutableStateOf(prefs.myNames.joinToString(", ")) }
    var fee by rememberSaveable { mutableStateOf("%.2f".format(prefs.abroadFee * 100).trimEnd('0').trimEnd('.')) }
    var cur by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("더 정확하게 적기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("내 이름", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(names, { names = it.take(40) }, label = { Text("통장에 찍히는 이름 (쉼표로 3개까지)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Muted("이 이름으로 들어온 돈은 수입이 아니라 내 계좌끼리 옮긴 돈으로 적어요. 홍*동처럼 가려 와도 알아봐요. 휴대폰에만 있어요")
                HorizontalDivider()
                Text("해외 결제", style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(fee, { fee = it.filter { ch -> ch.isDigit() || ch == '.' }.take(5) }, label = { Text("해외 이용 수수료 (%)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.fillMaxWidth())
                Muted("카드사 안내문의 ‘해외 이용 수수료’예요. 보통 브랜드 1% + 해외 서비스 0.18~0.25%예요")
                if (prefs.rates.isNotEmpty()) {
                    Text("내 환율", style = MaterialTheme.typography.labelLarge)
                    prefs.rates.entries.sortedBy { it.key }.forEach { (k, v) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("$k 1 = ${"%,.2f".format(v)}원", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            IconButton(onClick = { c.autoRecordPrefs.setRate(k, null) }) { Icon(Icons.Filled.Close, "$k 환율 지우기") }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(cur, { cur = it.filter { ch -> ch.isLetter() }.uppercase().take(3) }, label = { Text("통화") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(rate, { rate = it.filter { ch -> ch.isDigit() || ch == '.' }.take(9) }, label = { Text("1단위 원") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1.4f))
                    TextButton(enabled = cur.length == 3 && (rate.toDoubleOrNull() ?: 0.0) > 0, onClick = {
                        c.autoRecordPrefs.setRate(cur, rate.toDoubleOrNull()); cur = ""; rate = ""
                    }) { Text("넣기") }
                }
                Muted("임시 원화로 적힌 해외 결제를 카드 청구서 금액으로 고치면 그 환율을 저절로 기억해요")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                c.autoRecordPrefs.setMyNames(names.split(',', '，', '/'))
                fee.toDoubleOrNull()?.let { c.autoRecordPrefs.setAbroadFeePercent(it) }
                onDismiss()
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * 1.32 [LEDX-23] The heat strip a calendar cell draws under its date when 지출 히트맵 is on: the day's spending against
 * the month's own days, four steps.
 */
@Composable
fun SpendHeatStrip(shade: Int, modifier: Modifier = Modifier) {
    if (shade <= 0) return
    val color = LocalCalendarColors.current.expense
    Box(modifier.fillMaxWidth().padding(horizontal = 3.dp).height(3.dp)
        .background(color.copy(alpha = 0.2f + 0.2f * shade.coerceIn(1, 4)), RoundedCornerShape(2.dp)))
}

/** 1.32 [LEDX-23] What the four steps mean, under the month while the heat map is on. */
@Composable
fun SpendHeatLegend(modifier: Modifier = Modifier) {
    val color = LocalCalendarColors.current.expense
    Row(modifier.semantics { contentDescription = "지출 히트맵: 날짜 밑 막대가 진할수록 그달 다른 날보다 많이 썼어요" },
        verticalAlignment = Alignment.CenterVertically) {
        Text("적게", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(4.dp))
        (1..4).forEach { s ->
            Box(Modifier.padding(horizontal = 1.dp).size(width = 14.dp, height = 4.dp).background(color.copy(alpha = 0.2f + 0.2f * s), RoundedCornerShape(2.dp)))
        }
        Spacer(Modifier.width(4.dp))
        Text("많이 쓴 날", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
