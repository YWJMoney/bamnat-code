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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
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
    val container = LocalContext.current.appContainer
    val money = container.moneyRepository
    val entries by money.entries.collectAsState(initial = emptyList())
    val date = memo.date ?: Today.date()
    val title = memo.title.ifBlank { prop.label }.take(40)
    val done = entries.any { it.source == MoneySource.MEMO && it.amount == amount && it.date == date && it.merchant == title }
    var asking by remember { mutableStateOf(false) }
    AssistChip(
        onClick = { if (!done) asking = true },
        label = { Text(if (done) "가계부에 넣음 · ${Fmt.won(amount)}" else "${prop.label} ${Fmt.won(amount)} 가계부에 넣기") },
        leadingIcon = { Icon(if (done) Icons.Filled.Check else Icons.Outlined.AccountBalanceWallet, null, Modifier.size(18.dp)) },
    )
    if (asking) {
        val income = INCOME_WORDS.any { prop.label.contains(it) }
        var kind by remember { mutableStateOf(if (income) MoneyKind.INCOME else MoneyKind.EXPENSE) }
        var category by remember { mutableStateOf(if (income) (if (prop.label.contains("매출")) "매출" else "부수입") else "기타") }
        var typed by remember { mutableStateOf(amount.toString()) }
        val scope = rememberCoroutineScope()
        AlertDialog(
            onDismissRequest = { asking = false },
            title = { Text("가계부에 이렇게 넣을까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("${Fmt.fullDate(date)} · $title", style = MaterialTheme.typography.bodyMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = kind == MoneyKind.INCOME, onClick = { kind = MoneyKind.INCOME }, label = { Text("들어온 돈") })
                        FilterChip(selected = kind == MoneyKind.EXPENSE, onClick = { kind = MoneyKind.EXPENSE }, label = { Text("나간 돈") })
                    }
                    OutlinedTextField(typed, { typed = numberOnly(it).substringBefore('.') }, label = { Text("금액") }, suffix = { Text("원") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(category, { category = it.take(20) }, label = { Text("분류") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Text("메모는 그대로 두고 가계부에 한 줄만 더해요. 가계부에서 언제든 고치거나 지울 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                val n = typed.toLongOrNull() ?: 0
                TextButton(enabled = n > 0 && category.isNotBlank(), onClick = {
                    scope.launch {
                        money.save(MoneyEntry(date = date, kind = kind, amount = n, category = category.trim(), merchant = title, source = MoneySource.MEMO, memo = "메모에서"))
                    }
                    asking = false
                }) { Text("넣기") }
            },
            dismissButton = { TextButton(onClick = { asking = false }) { Text("그만두기") } },
        )
    }
}

private val INCOME_WORDS = listOf("매출", "수입", "급여", "팁", "받은", "입금", "현금", "카드")
