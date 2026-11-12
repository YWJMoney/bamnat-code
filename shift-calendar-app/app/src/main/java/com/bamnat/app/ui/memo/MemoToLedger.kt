package com.bamnat.app.ui.memo

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoProps
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneySource
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch

/**
 * [TAB-03] 1.36 메모 틀 → 가계부 (2차 조사 5-7 "자영업 마감 틀 = 매출·현금·카드 칸, 저장하면 가계부에 넣기"): a memo with a 금액 칸 shows
 * "매출 320,000원 가계부에 넣기". Nothing is written until the user sees the line (들어옴/나감, 분류, 금액, 날짜) and presses 넣기 — a
 * rule never adds money by itself (원칙 2). Once the same line is in the ledger the chip says so instead of adding it twice.
 */
@Composable
internal fun MemoToLedgerChip(memo: Memo) {
    val prop = remember(memo.body) { MemoProps.money(memo) } ?: return
    val amount = prop.num?.toLong()?.takeIf { it > 0 } ?: return
    val done = rememberInLedger(memo, prop, amount)
    var asking by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { if (!done) asking = true },
        label = { Text(if (done) "가계부에 넣음 · ${Fmt.won(amount)}" else "${prop.label} ${Fmt.won(amount)} 가계부에 넣기") },
        leadingIcon = { Icon(if (done) Icons.Filled.Check else Icons.Outlined.AccountBalanceWallet, null, Modifier.size(18.dp)) },
    )
    if (asking) MemoToLedgerDialog(memo) { asking = false }
}

/** Whether the line '가계부에 넣기' would add for [memo] is already in the ledger (same day, amount and name, from a memo). */
@Composable
private fun rememberInLedger(memo: Memo, prop: MemoProps.Prop?, amount: Long?): Boolean {
    if (amount == null) return false
    val entries by LocalContext.current.appContainer.moneyRepository.entries.collectAsState(initial = emptyList())
    val date = memo.date ?: Today.date()
    val title = memo.title.ifBlank { prop?.label.orEmpty() }.take(40)
    return entries.any { it.source == MoneySource.MEMO && it.amount == amount && it.date == date && it.merchant == title }
}

/**
 * The check before a memo's money goes to the ledger. PC 확인 bad7d860 ("가계부에 넣기를 메뉴 어디에서도 못 찾았어요"): it opens from the
 * editor's ⋮ and a card's 메뉴 too, and a memo with no 금액 칸 starts with the amount empty for the user to write.
 */
@Composable
internal fun MemoToLedgerDialog(memo: Memo, onDismiss: () -> Unit) {
    val prop = remember(memo.body) { MemoProps.money(memo) }
    // PC 확인 5c21f3bb ('4500원'이 본문에 있어도 금액이 비어요): with no 금액 칸, the first '…원' in the memo is offered.
    val amount = prop?.num?.toLong()?.takeIf { it > 0 } ?: remember(memo.title, memo.body) { wonInText(memo.title + "\n" + memo.text) }
    val container = LocalContext.current.appContainer
    val money = container.moneyRepository
    val done = rememberInLedger(memo, prop, amount)
    val date = memo.date ?: Today.date()
    val label = prop?.label ?: "금액"
    val title = memo.title.ifBlank { prop?.label ?: memo.preview }.ifBlank { "메모" }.take(40)
    // 2.0.0 [RVI-34] (검토 T07a-08) '카드'·'현금' is a shop's 매출 (카드 매출); for anyone else it is how the money was spent.
    val shop = container.settingsRepository.current.workStyle == com.bamnat.core.work.WorkStyle.BUSINESS
    val income = incomeLabel(label, shop)
    var kind by remember { mutableStateOf(if (income) MoneyKind.INCOME else MoneyKind.EXPENSE) }
    var category by remember { mutableStateOf(if (income) (if (label.contains("매출")) "매출" else "부수입") else "기타") }
    var typed by remember { mutableStateOf(amount?.toString().orEmpty()) }
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("가계부에 이렇게 넣을까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${Fmt.fullDate(date)} · $title", style = MaterialTheme.typography.bodyMedium)
                if (done) Text("이 메모의 ${Fmt.won(amount ?: 0)}은 이미 가계부에 있어요. 한 번 더 넣으면 두 줄이 돼요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = kind == MoneyKind.INCOME, onClick = { kind = MoneyKind.INCOME }, label = { Text("들어온 돈") })
                    FilterChip(selected = kind == MoneyKind.EXPENSE, onClick = { kind = MoneyKind.EXPENSE }, label = { Text("나간 돈") })
                }
                OutlinedTextField(typed, { typed = numberOnly(it).substringBefore('.') }, label = { Text("금액") }, suffix = { Text("원") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                OutlinedTextField(category, { category = it.take(20) }, label = { Text("분류") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(if (prop == null) "메모에 ‘금액: 12000’ 줄을 쓰면 다음부터 금액이 미리 채워져요. 메모는 그대로 두고 가계부에 한 줄만 더해요."
                    else "메모는 그대로 두고 가계부에 한 줄만 더해요. 가계부에서 언제든 고치거나 지울 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            val n = typed.toLongOrNull() ?: 0
            TextButton(enabled = n > 0 && category.isNotBlank(), onClick = {
                // 2.0.0 [RVI-33] (검토 MEMO-18) The save runs in the app's scope: closing the dialog no longer cancels it.
                val app = context.applicationContext
                val entry = MoneyEntry(date = date, kind = kind, amount = n, category = category.trim(), merchant = title, source = MoneySource.MEMO, memo = "메모에서")
                container.appScope.launch {
                    val saved = runCatching { money.save(entry) }.isSuccess
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        android.widget.Toast.makeText(app, if (saved) "가계부에 ${Fmt.won(n)} 넣었어요" else "가계부에 넣지 못했어요 · 다시 눌러 주세요",
                            android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
                onDismiss()
            }) { Text("넣기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("그만두기") } },
    )
}

private val INCOME_WORDS = listOf("매출", "수입", "급여", "팁", "받은", "입금")
/** Words a shop's 매출 일지 uses for money in ('카드: 12000' 카드 매출); for anyone else they say how money was spent. */
private val SHOP_INCOME_WORDS = listOf("현금", "카드")

/** 2.0.0 [RVI-34] Whether a 금액 칸 named [label] is money in: '매출'·'입금'…, and '현금'·'카드' only in a [shop]. */
internal fun incomeLabel(label: String, shop: Boolean): Boolean =
    INCOME_WORDS.any { label.contains(it) } || (shop && SHOP_INCOME_WORDS.any { label.contains(it) })

/** PC 확인 5c21f3bb: the first amount written as '4500원', '4,500 원' or '3만원'·'1.5만 원' in [text], or null. */
internal fun wonInText(text: String): Long? {
    val m = Regex("""(?<![\d.,])(\d{1,3}(?:,\d{3})+|\d+)(?:\.(\d+))?\s*(만)?\s*원""").find(text) ?: return null
    val whole = m.groupValues[1].replace(",", "").toLongOrNull() ?: return null
    val won = if (m.groupValues[3].isNotEmpty()) {
        val frac = m.groupValues[2].take(4).padEnd(4, '0').toLongOrNull() ?: 0L
        whole * 10_000 + frac
    } else whole
    return won.takeIf { it in 1..9_999_999_999L }
}
