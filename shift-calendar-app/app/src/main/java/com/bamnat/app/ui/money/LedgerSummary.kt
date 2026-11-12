package com.bamnat.app.ui.money

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.BudgetLevel
import com.bamnat.core.money.BudgetLine
import com.bamnat.core.money.BudgetStatus
import com.bamnat.core.money.CardCycle
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyAccounts
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyBudget
import com.bamnat.core.money.MoneyCategories
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MonthMoney
import java.time.LocalDate
import java.time.YearMonth
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role

/**
 * [LEDG-01][LEDG-06] The month at a glance: what is left, money in and out, and with a budget,
 * what may still be spent today (the month's budget less what is spent and the repeating costs
 * still to leave, over the days left) and where the month is heading.
 */
@Composable
fun HeroCard(m: MonthMoney, status: BudgetStatus, today: LocalDate, onBudget: () -> Unit, aheadSaysToday: Boolean = false) {
    val cal = LocalCalendarColors.current
    val current = YearMonth.from(today) == m.month
    // 1.37 PC 확인 6a4efc92: '11월 남은 돈 0원'이 통장 잔액으로 본 '오늘 쓸 돈'과 나란히 있어 헷갈렸어요. 이 카드는 가계부에 적은
    // 수입에서 지출을 뺀 것이라 그대로 이름을 붙여요.
    SectionCard(title = "${m.month.monthValue}월 수입 − 지출") {
        // 1.36 PC C13: the big figure is what is left now; the pay still to come is a line under it.
        FitText(Fmt.won(m.leftNow), MaterialTheme.typography.displaySmall, if (m.leftNow < 0) cal.expense else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Bold)
        m.payExpected?.let {
            Text("급여가 들어오면 ${Fmt.won(m.balance)} 남을 예상이에요", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                Text("들어온 돈", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FitText((if (m.received > 0) "+" else "") + Fmt.won(m.received), MaterialTheme.typography.titleMedium, cal.income, fontWeight = FontWeight.Bold)
            }
            // [J133-02] 1.33 (보고서 A10): the pay estimate is money still to come, not money that came in.
            m.payExpected?.let { expected ->
                Column(Modifier.weight(1f)) {
                    Text("들어올 돈(예상)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FitText("+" + Fmt.won(expected), MaterialTheme.typography.titleMedium, cal.income.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                }
            }
            Column(Modifier.weight(1f)) {
                Text("나간 돈", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FitText((if (m.spent > 0) "-" else "") + Fmt.won(m.spent), MaterialTheme.typography.titleMedium, cal.expense, fontWeight = FontWeight.Bold)
            }
        }
        // 1.37 PC 실사용(자영업 S5-16): '나간 돈 −0원'인데 경비 30만이 있어 헷갈렸어요. 가게 경비는 가게 순수익(들어온 돈)에서 빠져요.
        if (m.salesCosts > 0) {
            Text("가게 경비 ${Fmt.won(m.salesCosts)}은 나간 돈이 아니라 들어온 돈의 가게 순수익(매출 − 경비)에서 뺐어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        // [RECV-04] Work written down but not paid yet stays out of 들어온 돈 and shows here instead.
        if (m.jobOwed > 0) {
            Text("아직 안 받은 일감 ${Fmt.won(m.jobOwed)}은 받을 돈에 있어요. 돈 받음을 켜면 들어온 돈에 더해져요.",
                // 2.0.0 [RVP-13] (조건 6 '빨강은 위험만') An explanation, not a warning: grey like the line above.
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
        }
        HorizontalDivider(Modifier.padding(vertical = 12.dp))
        val total = status.total
        if (total == null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("예산을 정하면 하루에 쓸 수 있는 돈을 알려 줘요.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = onBudget) { Text("예산 정하기") }
            }
            return@SectionCard
        }
        val left = status.todayLeft
        // fc374a23 PC 확인 (같은 화면에 '오늘 쓸 수 있는 돈'이 두 숫자): when 앞으로의 돈 says today's money from the accounts, this card
        // keeps the budget's share as a quiet line under its own name, not a second 오늘 쓸 돈.
        if (current && left != null && aheadSaysToday) {
            Text("예산으로는 하루 ${Fmt.won(status.perDay ?: 0)} · 남은 ${status.daysLeft}일. 오늘 쓸 돈은 ‘앞으로의 돈’이 통장으로 셈해요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
        } else if (current && left != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("오늘 쓸 수 있는 돈", style = MaterialTheme.typography.labelLarge)
                    Text("하루 ${Fmt.won(status.perDay ?: 0)} · 남은 ${status.daysLeft}일", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(Fmt.won(left.coerceAtLeast(0)), style = MaterialTheme.typography.headlineSmall.merge(NumberStyle), fontWeight = FontWeight.Bold,
                    color = if (left <= 0) cal.warning else MaterialTheme.colorScheme.primary)
            }
            if (left < 0) {
                Text("오늘 몫보다 ${Fmt.won(-left)} 더 썼어요. 내일부터 조금씩 줄여요.", style = MaterialTheme.typography.bodySmall,
                    color = cal.warning, modifier = Modifier.padding(top = 2.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        // 1.38 (1.37 PC 확인: 예산 카드를 누르면 바로 '예산 정하기' 창이 떠요): a tap opens the categories' budgets right here;
        // changing them is its own 고치기.
        var open by rememberSaveable { mutableStateOf(false) }
        BudgetLineView(total, status.elapsed.takeIf { current }, onClick = { open = !open }, whole = true, expanded = open)
        if (open) {
            Column(Modifier.fillMaxWidth().padding(start = 8.dp, top = 4.dp)) {
                if (status.categories.isEmpty()) Text("분류마다 예산을 나누면 어디서 많이 쓰는지 여기서 바로 보여요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                status.categories.forEach { b -> BudgetMiniRow(b) }
                TextButton(onClick = onBudget, modifier = Modifier.align(Alignment.End)) { Text("예산 고치기") }
            }
        }
        if (current && status.fixedLeft > 0) {
            Text("이번 달 남은 반복 지출 ${Fmt.won(status.fixedLeft)}은 미리 빼고 계산했어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** One budget: spent against it, a mark for how far the month is, and a plain word on where it stands. */
@Composable
private fun BudgetLineView(b: BudgetLine, elapsed: Float?, onClick: () -> Unit, whole: Boolean = false, expanded: Boolean? = null) {
    val cal = LocalCalendarColors.current
    val color = when (b.level) { BudgetLevel.OVER -> cal.warning; BudgetLevel.NEAR -> cal.changed; BudgetLevel.OK -> MaterialTheme.colorScheme.primary }
    val pct = (b.fraction * 100).toInt()
    val status = when {
        b.level == BudgetLevel.OVER -> "${Fmt.won(-b.left)} 넘었어요"
        b.headedOver -> "이대로면 약 ${wonBrief(b.forecast - b.budget)}원 넘을 것 같아요"
        b.level == BudgetLevel.NEAR -> "${pct}% 썼어요 · ${Fmt.won(b.left)} 남았어요"
        else -> "${Fmt.won(b.left)} 남았어요"
    }
    val name = if (whole) "이번 달 예산" else b.category
    Column(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(role = Role.Button, onClick = onClick).padding(vertical = 6.dp)
            .semantics(mergeDescendants = true) { contentDescription = "$name ${Fmt.won(b.budget)} 중 ${Fmt.won(b.spent)} 씀, $status" },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!whole) {
                CategoryAvatar(b.category, size = 28.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                // [A11Y-03] Two lines, so large text shows "이번 달 예산" instead of "이번 달 …".
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(8.dp))
            Text("${Fmt.won(b.spent)} / ${Fmt.won(b.budget)}", style = MaterialTheme.typography.bodySmall.merge(NumberStyle),
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            expanded?.let {
                Icon(if (it) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown, if (it) "분류별 예산 접기" else "분류별 예산 펼치기",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
        // 1.37 [LEDH-34] What came over from last month, inside the budget shown.
        if (b.carried != 0L) Text(if (b.carried > 0) "지난달 남은 ${Fmt.won(b.carried)} 더함" else "지난달 넘긴 ${Fmt.won(-b.carried)} 뺌",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        LedgerTrack(b.fraction, color, marker = elapsed, height = if (whole) 10.dp else 8.dp)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (b.level == BudgetLevel.OVER || b.headedOver) {
                Icon(Icons.Filled.WarningAmber, null, tint = if (b.level == BudgetLevel.OVER) cal.warning else cal.changed, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(status, style = MaterialTheme.typography.labelMedium,
                color = when { b.level == BudgetLevel.OVER -> cal.warning; b.headedOver || b.level == BudgetLevel.NEAR -> cal.changed
                    else -> MaterialTheme.colorScheme.onSurfaceVariant })
        }
    }
}

/** 1.38: one category's budget in a single line under the month's, for the budget opened in place. */
@Composable
private fun BudgetMiniRow(b: BudgetLine) {
    val cal = LocalCalendarColors.current
    val color = when (b.level) { BudgetLevel.OVER -> cal.warning; BudgetLevel.NEAR -> cal.changed; BudgetLevel.OK -> MaterialTheme.colorScheme.primary }
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(b.category, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(if (b.left >= 0) "${Fmt.won(b.left)} 남음" else "${Fmt.won(-b.left)} 넘음", style = MaterialTheme.typography.labelMedium.merge(NumberStyle),
                color = if (b.level == BudgetLevel.OVER) cal.warning else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LedgerTrack(b.fraction, color, marker = null, height = 6.dp)
    }
}

/** [LEDG-06] Each category with a budget, the ones over or closest to it first. */
@Composable
fun BudgetCard(status: BudgetStatus, today: LocalDate, onEdit: () -> Unit) {
    if (status.categories.isEmpty()) return
    val current = YearMonth.from(today) == status.month
    // 1.37 [LEDH-06] Counted over 급여일 주기, it says which days.
    SectionCard(title = if (status.isCycle) "분류별 예산 (${status.from.monthValue}/${status.from.dayOfMonth}~${status.to.monthValue}/${status.to.dayOfMonth})" else "분류별 예산", trailing = { TextButton(onClick = onEdit) { Text("고치기") } }) {
        if (status.over.isNotEmpty()) {
            Text("${status.over.joinToString(", ") { it.category }} 예산을 넘었어요", style = MaterialTheme.typography.bodySmall,
                color = LocalCalendarColors.current.warning, modifier = Modifier.padding(bottom = 4.dp))
        }
        status.categories.forEach { b -> BudgetLineView(b, status.elapsed.takeIf { current }, onClick = onEdit) }
    }
}

/**
 * [LEDG-06] The month's budget and each category's. Every category shows what went there last
 * month, and one tap fills the empty ones with the last three months' average.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetEditor(
    book: MoneyBook, month: YearMonth, onSave: (Map<String, Long>) -> Unit, onDismiss: () -> Unit,
    /** 1.37 [LEDH-06] 월급날 기준 on or off; null when no pay day is set, so the choice is not offered. */
    byPayCycle: Boolean? = null, onPayCycle: (Boolean) -> Unit = {},
    /** 1.37 PC 4차: a 월급 user with no pay day yet is told where the switch comes from. */
    payDayHint: Boolean = false,
    /** 1.37 [LEDH-34] 예산 넘김: the categories that carry, and how to change them (saved at once, like 월급날 기준). */
    rolling: Set<String> = emptySet(), onRolling: (Set<String>) -> Unit = {}, canRoll: Boolean = true,
) {
    val categories = remember(book) { book.categoriesOf(MoneyKind.EXPENSE) }
    val values = remember {
        mutableStateMapOf<String, String>().apply { book.budgets.forEach { put(it.category, amountDigits(it.amount)) } }
    }
    val lastMonth = remember(book, month) { book.month(month.minusMonths(1)).expenseByCategory.associate { it.category to it.amount } }
    val average = remember(book, month) {
        val months = (1..3).map { book.month(month.minusMonths(it.toLong())) }
        val counted = months.count { it.spent > 0 }.coerceAtLeast(1)
        months.flatMap { it.expenseByCategory }.groupBy { it.category }.mapValues { (_, l) -> l.sumOf { it.amount } / counted }
    }
    val sum = categories.sumOf { values[it]?.toAmount() ?: 0 }
    // 1.37 PC 확인 6a4efc92: 제목 줄에 닫기가 있어 손잡이 X는 빼요 (X가 두 개였어요).
    // 1.37 PC 4차: 뒤로 가기로 적은 예산이 말없이 사라졌어요. 바뀐 게 있으면 먼저 물어요.
    val initial = remember { values.toMap() }
    val unsaved = values.filterValues { it.isNotEmpty() } != initial.filterValues { it.isNotEmpty() }
    com.bamnat.app.ui.components.GuardedSheet(onDismissRequest = onDismiss, unsaved = unsaved, closeButton = false) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            SheetTitle("예산 정하기", onDismiss)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("매달 같은 예산으로 계산해요. 비워 두면 예산이 없어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountField("한 달 전체 예산", values[MoneyBudget.TOTAL].orEmpty(), { values[MoneyBudget.TOTAL] = it })
                // 1.37 [LEDH-06] 급여일 주기: the budget runs from pay day to the day before the next.
                byPayCycle?.let { on ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f)) {
                            Text("월급날 기준으로 보기", style = MaterialTheme.typography.bodyLarge)
                            Text("예산과 하루 쓸 돈을 1일~말일 대신 월급날~다음 월급날 전날로 계산해요.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        androidx.compose.material3.Switch(checked = on, onCheckedChange = onPayCycle,
                            modifier = Modifier.semantics { contentDescription = "월급날 기준으로 보기" })
                    }
                }
                if (byPayCycle == null && payDayHint) Text("월급날을 정하면 여기서 '월급날 기준으로 보기'를 켤 수 있어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (values[MoneyBudget.TOTAL].isNullOrEmpty() && sum > 0) {
                    Text("전체를 비우면 분류별 예산의 합 ${Fmt.won(sum)}이 한 달 예산이 돼요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("분류별 예산", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                    if (average.isNotEmpty()) {
                        TextButton(onClick = {
                            // Rounded up to 1만원: a budget is a round number people remember.
                            for ((c, a) in average) if (c in categories && a > 0 && values[c].isNullOrEmpty()) {
                                values[c] = amountDigits((a + 9_999) / 10_000 * 10_000)
                            }
                        }) { Text("지난 3달 평균으로 채우기") }
                    }
                }
                categories.forEach { c ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CategoryAvatar(c, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(c, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(lastMonth[c]?.let { "지난달 ${Fmt.won(it)}" } ?: "지난달 없음", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                        Spacer(Modifier.width(8.dp))
                        AmountField("예산", values[c].orEmpty(), { values[c] = it },
                            Modifier.widthIn(max = 168.dp).semantics { contentDescription = "$c 예산" })
                    }
                }
                // 1.37 [LEDH-34] 예산 넘김: what a category's budget left over this month is added to next month's (and what it went
                // over is taken off), one month at a time.
                val budgeted = categories.filter { (values[it]?.toAmount() ?: 0) > 0 }
                // bc7e2d43 PC 확인: 투자 달력에는 예산 넘김이 안 보여요.
                if (canRoll && budgeted.isNotEmpty()) {
                    Text("남은 예산 다음 달로 넘기기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                    Text("고른 분류는 이번 달에 남은 만큼 다음 달 예산이 늘고, 넘긴 만큼 줄어요. 한 달씩만 넘어가요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        budgeted.forEach { c ->
                            com.bamnat.app.ui.components.FilterChip(selected = c in rolling, onClick = { onRolling(if (c in rolling) rolling - c else rolling + c) },
                                label = { Text(c) })
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider()
            com.bamnat.app.ui.components.SaveButton(
                onClick = { onSave((listOf(MoneyBudget.TOTAL) + categories + book.budgets.map { it.category }).distinct().associateWith { values[it]?.toAmount() ?: 0 }) },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 48.dp),
            ) { Text("저장") }
        }
    }
}

@Composable
private fun SheetTitle(title: String, onDismiss: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
        IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") }
    }
}

/**
 * [LEDG-08] Cash, accounts and cards: what is in each account, and what each credit card will take
 * on its next 결제일 from what was spent on it (installments spread over their months).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountsCard(book: MoneyBook, today: LocalDate, onAdd: (AccountType?) -> Unit, onEdit: (MoneyAccount) -> Unit,
                 onRepay: ((List<com.bamnat.core.money.MoneyEntry>) -> Unit)? = null) {
    val accounts = book.activeAccounts
    val cal = LocalCalendarColors.current
    val balances = remember(book) { MoneyAccounts.balances(book.accounts, book.entries) }
    SectionCard(
        title = "결제수단·계좌",
        trailing = { com.bamnat.app.ui.components.AddButton("추가", onClick = { onAdd(null) }) },
    ) {
        if (accounts.isEmpty()) {
            Text("현금·통장·카드를 적어 두면 어디서 얼마 나갔는지, 이번 달 카드값이 얼마 나올지 알려 줘요. 카드 결제 알림도 끝 4자리로 알아서 이어 줘요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
                listOf(AccountType.CASH, AccountType.BANK, AccountType.CREDIT, AccountType.CHECK).forEach { t ->
                    AssistChip(onClick = { onAdd(t) }, label = { Text(t.label) }, leadingIcon = { Icon(accountIcon(t), null, Modifier.size(18.dp)) })
                }
            }
            return@SectionCard
        }
        accounts.forEach { a ->
            val (value, caption) = when {
                a.isCredit && a.payDay != null -> {
                    val bill = CardCycle.next(a, today, book.entries, book.accounts)
                    Fmt.won(bill.amount) to "${Fmt.shortDate(bill.payDate)} 결제 · ${Fmt.shortDate(bill.from)}~${Fmt.shortDate(bill.to)} 쓴 돈"
                }
                a.isCredit -> null to "결제일을 정하면 카드값을 계산해요"
                a.type == AccountType.CHECK -> null to (book.account(a.payFromId)?.let { "${it.name}에서 바로 나가요" } ?: "연결한 통장을 고르면 잔액에 반영해요")
                // 1.38 [LEDH-25] 대출: 남은 원금과 다 갚는 달.
                a.isLoan -> {
                    val left = com.bamnat.core.money.Debts.left(a, balances)
                    val plan = com.bamnat.core.money.Debts.plan(left, a.rate, a.monthlyPay, com.bamnat.core.money.Debts.firstMonth(a, today))
                    val payoff = plan.payoff
                    Fmt.won(left) to when {
                        left == 0L -> "다 갚았어요"
                        a.monthlyPay <= 0 -> "남은 원금 · 매달 갚는 돈을 적으면 다 갚는 달을 알려 줘요"
                        payoff == null -> "남은 원금 · 매달 갚는 돈이 이자보다 적어 줄지 않아요"
                        else -> "남은 원금 · ${payoff.year}년 ${payoff.monthValue}월에 다 갚아요"
                    }
                }
                else -> Fmt.won(balances[a.id] ?: a.opening) to (if (a.last4.isNotBlank()) "끝 ${a.last4}" else a.type.label)
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(MaterialTheme.shapes.small).clickable(role = Role.Button) { onEdit(a) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconAvatar(accountIcon(a.type), accountColor(a), size = 36.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.label, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(caption, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (value != null) {
                    val negative = !a.isCredit && !a.isLoan && (balances[a.id] ?: 0) < 0
                    Text(value, style = MaterialTheme.typography.titleSmall.merge(NumberStyle), fontWeight = FontWeight.Bold,
                        color = if (negative) cal.expense else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        // 1.38 [LEDH-27] 카드 실적 게이지, [LEDH-25] 빚 갚기 비교.
        CardPerfLines(book, today)
        DebtLines(book, today, balances, onRepay)
        val kept = accounts.filter { it.keepsBalance && !it.isLoan }
        if (kept.size > 1) {
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row {
                Text("통장·현금 합계", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Fmt.won(kept.sumOf { balances[it.id] ?: it.opening }), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), fontWeight = FontWeight.Bold)
            }
        }
        // 1.37 [LEDH-26] 순자산 한 장: 가진 돈 − 남은 카드값, and a year of month-ends as one thin line.
        val year = remember(book, today) { com.bamnat.core.money.NetWorth.year(book.accounts, book.entries, today) }
        year.lastOrNull()?.let { now ->
            HorizontalDivider(Modifier.padding(vertical = 6.dp))
            Row {
                Text("순자산", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text(Fmt.won(now.net), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), fontWeight = FontWeight.Bold,
                    color = if (now.net < 0) cal.expense else MaterialTheme.colorScheme.onSurface)
            }
            // Only the months the book was in use: before the first line the past is not known.
            // PC 확인 (통장을 오늘 처음 만들었는데 '9월 말보다 +0원'과 석 달 선): counted from the first line on an account, not any line.
            val first = book.entries.filter { it.accountId != null || it.toAccountId != null }.minOfOrNull { it.date }
            val known = year.filter { first != null && !it.date.isBefore(first) }
            val start = known.firstOrNull()?.takeIf { it != now }
            Text(
                (if (now.owe > 0) "가진 돈 ${Fmt.won(now.have)} − " + (if (accounts.any { it.isLoan }) "남은 카드값·빚" else "남은 카드값") + " ${Fmt.won(now.owe)}" else "가진 돈 ${Fmt.won(now.have)}") +
                    (start?.let { s -> val d = now.net - s.net; " · ${s.date.monthValue}월 말보다 " + (if (d >= 0) "+" else "−") + Fmt.won(kotlin.math.abs(d)) } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (known.size >= 3) {
                val line = MaterialTheme.colorScheme.primary
                val values = known.map { it.net }
                androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(36.dp).padding(top = 6.dp)
                    .semantics { contentDescription = "순자산 ${known.size}달 흐름" }) {
                    val lo = values.min().toFloat(); val hi = values.max().toFloat()
                    val span = (hi - lo).takeIf { it > 0f } ?: 1f
                    val step = size.width / (values.size - 1)
                    for (i in 1 until values.size) {
                        drawLine(line, androidx.compose.ui.geometry.Offset((i - 1) * step, size.height - (values[i - 1] - lo) / span * size.height),
                            androidx.compose.ui.geometry.Offset(i * step, size.height - (values[i] - lo) / span * size.height), strokeWidth = 2.dp.toPx())
                    }
                }
            }
        }
    }
}

/** 1.38 [LEDH-27] Each card with 실적 구간: how far this month has come and what is left to the next tier. */
@Composable
private fun CardPerfLines(book: MoneyBook, today: LocalDate) {
    val gauges = remember(book, today) { com.bamnat.core.money.CardPerf.all(book.accounts, book.entries, today) }
    if (gauges.isEmpty()) return
    val cal = LocalCalendarColors.current
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("카드 실적", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    gauges.forEach { g ->
        val next = g.next
        val reached = g.reached
        val words = when {
            next == null -> "가장 높은 ${wonBrief(reached ?: 0)}원 구간을 채웠어요"
            reached == null -> "${wonBrief(g.toNext ?: 0)}원 더 쓰면 ${wonBrief(next)}원 구간"
            else -> "${wonBrief(reached)}원 구간 · ${wonBrief(g.toNext ?: 0)}원 더 쓰면 ${wonBrief(next)}원 구간"
        }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).semantics(mergeDescendants = true) {}) {
            Row {
                Text(g.card.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("이번 달 ${Fmt.won(g.counted)}", style = MaterialTheme.typography.bodySmall.merge(NumberStyle), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            LedgerTrack(g.fraction, if (g.next == null) cal.income else MaterialTheme.colorScheme.primary, height = 6.dp,
                marker = g.card.perfTiers.dropLast(1).lastOrNull()?.let { it.toFloat() / g.card.perfTiers.last() })
            Text(words + (g.lastReached?.let { " · 지난달 ${wonBrief(it)}원 구간이라 이번 달 혜택은 그 구간이에요" } ?: ""),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    Text("1일~말일에 이 카드로 쓴 돈이에요" + (if (gauges.any { it.skipped > 0 }) "(고른 분류는 뺐어요)" else "") + ". 카드사 기준과 다를 수 있어요.",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** 1.38 [LEDH-25] Each loan: how much sooner and how much less interest if a little more is paid each month, and 이번 달 갚기. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DebtLines(book: MoneyBook, today: LocalDate, balances: Map<Long, Long>, onRepay: ((List<com.bamnat.core.money.MoneyEntry>) -> Unit)?) {
    val loans = book.activeAccounts.filter { it.isLoan && com.bamnat.core.money.Debts.left(it, balances) > 0 }
    if (loans.isEmpty()) return
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("빚 갚기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    loans.forEach { loan ->
        val left = com.bamnat.core.money.Debts.left(loan, balances)
        val first = com.bamnat.core.money.Debts.firstMonth(loan, today)
        var extra by rememberSaveable(loan.id) { mutableStateOf(com.bamnat.core.money.Debts.EXTRAS[1]) }
        val plan = remember(left, loan) { com.bamnat.core.money.Debts.plan(left, loan.rate, loan.monthlyPay, first) }
        val cmp = remember(left, loan, extra) { com.bamnat.core.money.Debts.compare(left, loan.rate, loan.monthlyPay, extra, first) }
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(loan.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            if (plan.months != null && plan.payoff != null) {
                Text("${plan.months}달 남았어요" + (if (loan.rate > 0) " · 남은 이자 약 ${wonBrief(plan.interest)}원" else ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.bamnat.core.money.Debts.EXTRAS.forEach { x ->
                        FilterChip(selected = extra == x, onClick = { extra = x }, label = { Text("매달 +${wonBrief(x)}") })
                    }
                }
                cmp?.let { c ->
                    Text(if (c.sooner <= 0) "더 갚아도 끝나는 달은 같아요"
                        else "매달 ${wonBrief(c.extra)}원 더 갚으면 ${c.sooner}달 일찍 끝나요" + (if (c.interestSaved > 0) " · 이자 ${wonBrief(c.interestSaved)}원 덜" else ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
            val paidThisMonth = book.entries.any { it.kind == MoneyKind.TRANSFER && it.toAccountId == loan.id && YearMonth.from(it.date) == YearMonth.from(today) }
            if (paidThisMonth) Text("이번 달은 갚았어요", style = MaterialTheme.typography.bodySmall, color = LocalCalendarColors.current.income)
            else if (onRepay != null && loan.monthlyPay > 0) {
                // 1.38 PC 1차: 이자는 '이자·금융'(사장님 장부는 원래 있던 '대출이자').
                val interestCategory = if ("대출이자" in book.categoriesOf(MoneyKind.EXPENSE)) "대출이자" else MoneyCategories.INTEREST
                val lines = remember(left, loan, today, interestCategory) { com.bamnat.core.money.Debts.lines(loan, left, loan.monthlyPay, today, interestCategory) }
                val interest = lines.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amount }
                TextButton(onClick = { onRepay(lines) }) {
                    Text("이번 달 갚기 ${Fmt.won(lines.sumOf { it.amount })}" + (if (interest > 0) " (이자 ${Fmt.won(interest)})" else ""))
                }
            }
        }
    }
    Text("매달 같은 돈을 갚는 셈이라 은행 상환표와 조금 다를 수 있어요.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * 2.0.0 [RVK-18] (검토 U06a-09): what the bank app shows now; the difference becomes one '잔액 맞춤' line today, said before it is
 * written (a transfer, so no month's 수입·지출 changes).
 */
@Composable
private fun BalanceAdjustDialog(account: MoneyAccount, counted: Long, onDone: (Long) -> Unit, onDismiss: () -> Unit) {
    var actual by rememberSaveable { mutableStateOf("") }
    val typed = actual.isNotEmpty()
    val diff = actual.toAmount() - counted
    com.bamnat.app.ui.components.FormSheet(
        onDismissRequest = onDismiss,
        unsaved = typed,
        title = { Text("${account.name} 잔액 맞추기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("가계부로 센 잔액은 ${Fmt.won(counted)}이에요. 통장 앱에 보이는 잔액을 적어 주세요.",
                    style = MaterialTheme.typography.bodyMedium)
                AmountField("실제 잔액", actual, { actual = it }, allowZero = true, imeAction = ImeAction.Done)
                if (typed) Text(
                    when {
                        diff == 0L -> "이미 맞아요. 적을 것이 없어요"
                        else -> "차이 ${if (diff > 0) "+" else "−"}${Fmt.won(kotlin.math.abs(diff))}을 오늘 ‘${com.bamnat.core.money.MoneyAccounts.ADJUST_NAME}’ 한 줄로 적어요. 달의 수입·지출은 그대로예요"
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            com.bamnat.app.ui.components.Button(onClick = { onDone(actual.toAmount()) }, enabled = typed && diff != 0L) { Text("맞추기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [LEDG-08] Adds or edits an account. A card names its last four digits (payment texts find it by
 * them) and the account it is paid from; a credit card its 결제일 and, if not the usual whole
 * month before, its 마감일. An account that holds money starts from what is in it now.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AccountEditor(
    initial: MoneyAccount?,
    type: AccountType?,
    book: MoneyBook,
    onSave: (MoneyAccount) -> Unit,
    onRemove: (MoneyAccount) -> Unit,
    onDismiss: () -> Unit,
    /** 2.0.0 [RVK-18] (검토 U06a-09) '실제 잔액으로 맞추기': the account and what the bank app shows in it. */
    onAdjust: ((MoneyAccount, Long) -> Unit)? = null,
) {
    var kind by rememberSaveable { mutableStateOf(initial?.type ?: type ?: AccountType.CREDIT) }
    var adjusting by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(initial?.name.orEmpty()) }
    var last4 by rememberSaveable { mutableStateOf(initial?.last4.orEmpty()) }
    var payDay by rememberSaveable { mutableIntStateOf(initial?.payDay ?: 14) }
    var ownClose by rememberSaveable { mutableStateOf(initial?.closeDay != null) }
    var closeDay by rememberSaveable { mutableIntStateOf(initial?.closeDay ?: 31) }
    var payFrom by rememberSaveable { mutableStateOf(initial?.payFromId) }
    var opening by rememberSaveable { mutableStateOf(amountDigits(initial?.opening ?: 0)) }
    var remind by rememberSaveable { mutableStateOf(initial?.remind ?: true) }
    // 1.38 [LEDH-25] 대출: 남은 원금은 양수로 받아 음수 잔액으로 둬요. [LEDH-27] 카드 실적 구간·뺄 분류.
    var owedText by rememberSaveable { mutableStateOf(amountDigits(if (initial?.isLoan == true) -initial.opening else 0)) }
    var rateText by rememberSaveable { mutableStateOf(initial?.rate?.takeIf { it > 0 }?.let { "%.2f".format(it).trimEnd('0').trimEnd('.') }.orEmpty()) }
    var monthlyText by rememberSaveable { mutableStateOf(amountDigits(initial?.monthlyPay ?: 0)) }
    var tiersText by rememberSaveable { mutableStateOf(initial?.perfTiers.orEmpty().joinToString(", ") { if (it % 10_000 == 0L) "${it / 10_000}만" else "$it" }) }
    var skipText by rememberSaveable { mutableStateOf(initial?.perfSkip.orEmpty().joinToString(",")) }
    val skip = skipText.split(',').filter { it.isNotBlank() }
    val loan = kind == AccountType.LOAN
    val banks = book.activeAccounts.filter { it.keepsBalance && !it.isLoan && it.id != initial?.id }
    val card = kind == AccountType.CREDIT || kind == AccountType.CHECK
    // 1.37 PC 확인 6a4efc92: 끌어서 닫을 때 적은 것을 묻기 (같은 구조의 돈 시트 모두).
    val typed = name.trim() != initial?.name.orEmpty() || last4 != initial?.last4.orEmpty() || opening != amountDigits(initial?.opening ?: 0) ||
        (initial != null && (kind != initial.type || payDay != (initial.payDay ?: 14) || payFrom != initial.payFromId)) ||
        owedText != amountDigits(if (initial?.isLoan == true) -initial.opening else 0) || monthlyText != amountDigits(initial?.monthlyPay ?: 0) ||
        tiersText.isNotBlank() != initial?.perfTiers.orEmpty().isNotEmpty()
    // 1.38 PC 1차 (갚는 통장을 안 고르면 '이번 달 갚기'가 어느 통장에서 나갔는지 몰라요): 대출을 처음 저장할 때 한 번만 물어요.
    var askPayFrom by rememberSaveable { mutableStateOf(false) }
    var askedPayFrom by rememberSaveable { mutableStateOf(initial?.isLoan == true) }
    com.bamnat.app.ui.components.GuardedSheet(onDismissRequest = onDismiss, unsaved = typed, closeButton = false) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            SheetTitle(if (initial == null) "결제수단 추가" else "결제수단 고치기", onDismiss) {
                if (initial != null) IconButton(onClick = { onRemove(initial) }) { Icon(Icons.Outlined.Delete, "지우기", tint = MaterialTheme.colorScheme.error) }
            }
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    AccountType.entries.forEach { t ->
                        FilterChip(selected = kind == t, onClick = { kind = t }, label = { Text(t.label) },
                            leadingIcon = { Icon(accountIcon(t), null, Modifier.size(18.dp)) })
                    }
                }
                Text(kind.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedTextField(value = name, onValueChange = { name = it.take(MoneyAccount.MAX_NAME) }, singleLine = true,
                    label = { Text(when (kind) { AccountType.CREDIT, AccountType.CHECK -> "카드 이름 (예: 신한 Deep)"; AccountType.BANK -> "통장 이름 (예: 월급 통장)"
                        AccountType.CASH -> "이름 (예: 지갑)"; AccountType.PAY -> "이름 (예: 카카오페이)"; AccountType.OTHER -> "이름"
                        AccountType.LOAN -> "이름 (예: 전세 대출, 언니에게 빌린 돈)" }) },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
                // PC 확인 (이름을 친 뒤 자판이 오르면 '지금 들어 있는 돈' 칸이 밀려 이름 칸을 누르게 됨): the balance comes right under the
                // name, and the keyboard's 다음 goes to it.
                if (loan) {
                    // 1.38 PC 1차: 자판 '다음'으로 원금 → 이자율 → 매달 갚는 돈, 고른 칸은 자판 위로 올라와요.
                    AmountField(if (initial == null) "남은 원금" else "남은 원금 (적기 시작할 때)", owedText, { owedText = it }, Modifier.aboveKeyboard())
                    OutlinedTextField(value = rateText, onValueChange = { v -> rateText = v.filter { it.isDigit() || it == '.' }.take(5) }, singleLine = true,
                        label = { Text("연 이자율 % (모르면 비워 둬요)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth().aboveKeyboard())
                    AmountField("매달 갚는 돈", monthlyText, { monthlyText = it }, Modifier.aboveKeyboard(), imeAction = ImeAction.Done)
                    DayStepper("갚는 날", payDay) { payDay = it }
                    Text("이 계좌로 보낸 돈(이체)만큼 남은 원금이 줄어요. ‘이번 달 갚기’로 적으면 이자와 원금을 나눠 적어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (!card && !loan) {
                    AmountField(if (initial == null) "지금 들어 있는 돈" else "처음 들어 있던 돈 (적기 시작할 때)", opening, { opening = it })
                    Text("이후 이 계좌로 적은 수입·지출·이체로 잔액을 계산해요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // 2.0.0 [RVK-18] (검토 U06a-09): when the count is off, the real balance in once, not the opening worked out backwards.
                    val counted = remember(book, initial) { initial?.let { com.bamnat.core.money.MoneyAccounts.balances(book.accounts, book.entries)[it.id] } }
                    if (initial != null && onAdjust != null && counted != null) {
                        // 총괄 #457 리뷰 (조건 12): at 글자 200% the balance keeps a line of its own and the button goes under it.
                        WordsWithActions({ Text("지금 잔액 ${Fmt.won(counted)}", style = MaterialTheme.typography.bodyMedium) }) {
                            TextButton(onClick = { adjusting = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("실제 잔액으로 맞추기") }
                        }
                        if (adjusting) BalanceAdjustDialog(initial, counted, onDone = { actual -> adjusting = false; onAdjust(initial, actual) },
                            onDismiss = { adjusting = false })
                    }
                }
                if (card) {
                    OutlinedTextField(value = last4, onValueChange = { v -> last4 = v.filter(Char::isDigit).take(4) }, singleLine = true,
                        label = { Text("카드 끝 4자리 (선택)") }, supportingText = { Text("이 카드의 결제 알림은 이 달력에만 알아서 적어요") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth())
                }
                if (kind == AccountType.CREDIT) {
                    DayStepper("결제일", payDay) { payDay = it }
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { ownClose = !ownClose }, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("쓴 기간 직접 정하기", style = MaterialTheme.typography.bodyLarge)
                            Text(if (!ownClose) "보통은 지난달 1일부터 말일까지 쓴 돈이 나가요" else "카드사 앱에서 '이용 기간'을 확인해 마지막 날을 넣어요",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(checked = ownClose, onCheckedChange = { ownClose = it })
                    }
                    if (ownClose) DayStepper("쓴 기간 마지막 날", closeDay) { closeDay = it }
                    val preview = MoneyAccount(name = name, type = kind, payDay = payDay, closeDay = if (ownClose) closeDay else null)
                    val (from, to) = CardCycle.period(preview, Today.month().plusMonths(1))
                    Text("${Today.month().plusMonths(1).monthValue}월 ${if (payDay >= 31) "말일" else "${payDay}일"} 결제: ${Fmt.shortDate(from)}~${Fmt.shortDate(to)}에 쓴 돈",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                // 1.38 [LEDH-27] 카드 실적 게이지: 카드사 앱의 전월 실적 구간을 적어 두면 이번 달 얼마 더 쓰면 되는지 보여 줘요.
                if (card) {
                    OutlinedTextField(value = tiersText, onValueChange = { tiersText = it.take(40) }, singleLine = true,
                        label = { Text("전월 실적 구간 (선택, 예: 30만, 50만)") },
                        supportingText = { Text("카드사 앱의 혜택 구간을 적으면 이번 달 얼마 더 쓰면 되는지 보여 줘요") },
                        modifier = Modifier.fillMaxWidth())
                    if (com.bamnat.core.money.CardPerf.parseTiers(tiersText).isNotEmpty()) {
                        Text("실적에서 빠지는 분류 (카드사마다 달라요)", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            book.categoriesOf(MoneyKind.EXPENSE).forEach { c ->
                                FilterChip(selected = c in skip, onClick = { skipText = (if (c in skip) skip - c else skip + c).joinToString(",") }, label = { Text(c) })
                            }
                        }
                    }
                }
                if ((card || loan) && banks.isNotEmpty()) {
                    Text(if (loan) "갚는 통장" else "돈이 나가는 통장", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = payFrom == null, onClick = { payFrom = null }, label = { Text("안 고름") })
                        banks.forEach { b -> FilterChip(selected = payFrom == b.id, onClick = { payFrom = b.id }, label = { Text(b.label) }) }
                    }
                }
                if (kind == AccountType.CREDIT) {
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { remind = !remind }, verticalAlignment = Alignment.CenterVertically) {
                        Text("카드값 3일 전·당일 아침 알림", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = remind, onCheckedChange = { remind = it })
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
            HorizontalDivider()
            val save = {
                    onSave((initial ?: MoneyAccount(name = name, type = kind)).copy(
                        name = name.trim(), type = kind, last4 = if (card) last4 else "",
                        payDay = payDay.takeIf { kind == AccountType.CREDIT || loan }, closeDay = closeDay.takeIf { kind == AccountType.CREDIT && ownClose },
                        payFromId = payFrom.takeIf { card || loan }, opening = when { card -> 0; loan -> -owedText.toAmount(); else -> opening.toAmount() },
                        remind = remind,
                        rate = if (loan) rateText.toDoubleOrNull()?.coerceIn(0.0, 30.0) ?: 0.0 else 0.0,
                        monthlyPay = if (loan) monthlyText.toAmount() else 0,
                        perfTiers = if (card) com.bamnat.core.money.CardPerf.parseTiers(tiersText) else emptyList(),
                        perfSkip = if (card && com.bamnat.core.money.CardPerf.parseTiers(tiersText).isNotEmpty()) skip else emptyList(),
                    ))
            }
            com.bamnat.app.ui.components.SaveButton(
                enabled = name.isNotBlank(),
                onClick = {
                    if (loan && payFrom == null && banks.isNotEmpty() && !askedPayFrom) { askedPayFrom = true; askPayFrom = true } else save()
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 48.dp),
            ) { Text("저장") }
            if (askPayFrom) {
                AlertDialog(
                    onDismissRequest = { askPayFrom = false },
                    title = { Text("어느 통장에서 갚나요?") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("고르면 ‘이번 달 갚기’가 그 통장에서 나간 돈으로 적혀요. 나중에 고쳐도 돼요.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            banks.forEach { b ->
                                TextButton(onClick = { payFrom = b.id; askPayFrom = false; save() }, modifier = Modifier.fillMaxWidth()) {
                                    Text(b.label, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { askPayFrom = false; save() }) { Text("안 고르고 저장") } },
                    dismissButton = { TextButton(onClick = { askPayFrom = false }) { Text("돌아가기") } },
                )
            }
        }
    }
}

@Composable
private fun DayStepper(label: String, day: Int, onChange: (Int) -> Unit) {
    // [UX-08] The app's one stepper; 말일 runs on to 1일.
    com.bamnat.app.ui.components.StepperRow(label) {
        com.bamnat.app.ui.components.CountStepper(day, onChange, name = label, range = 1..31, wrap = true, format = ::dayOfMonth)
    }
}

private fun dayOfMonth(day: Int): String = if (day >= 31) "말일" else "${day}일"

/** One category in the editor: its name now, the name it had, and whether it is hidden. */
private data class EditRow(val name: String, val was: String?, val hidden: Boolean) {
    companion object {
        /** Kept as three strings a row (name, the name it had or "", 숨김 "1"). */
        val Rows = androidx.compose.runtime.saveable.listSaver<androidx.compose.runtime.snapshots.SnapshotStateList<EditRow>, String>(
            save = { l -> l.flatMap { listOf(it.name, it.was.orEmpty(), if (it.hidden) "1" else "") } },
            restore = { v -> mutableStateListOf<EditRow>().apply { v.chunked(3).forEach { (n, w, h) -> add(EditRow(n, w.ifEmpty { null }, h == "1")) } } },
        )
    }
}

/**
 * [LEDG-14] The user's categories for each kind: moved up or down, hidden (kept for old lines,
 * not offered), renamed (with every line, budget and repeating line under it) or added. 기타
 * always stays last.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryEditor(
    book: MoneyBook,
    onSave: (MoneyKind, List<CategoryEdit>, List<Pair<String, String>>) -> Unit,
    onDismiss: () -> Unit,
    startKind: MoneyKind = MoneyKind.EXPENSE,
) {
    var kind by rememberSaveable { mutableStateOf(startKind) }
    // 1.37 PC 4차: 예산 창과 같은 구조라 같이, 뒤로 가기 전에 바꾼 것을 버릴지 물어요.
    val start = remember(kind) {
        val mine = book.categories.filter { it.kind == kind }.sortedBy { it.position }
        val shown = book.categoriesOf(kind).filter { it != MoneyCategories.OTHER }
        val hidden = mine.filter { it.hidden }.map { it.name }
        shown.map { EditRow(it, it, false) } + hidden.map { EditRow(it, it, true) }
    }
    // 2.0.0 [RVK-03] (MONEY-01): the names, order and hiding being changed come back after a rebuilt screen.
    val rows = rememberSaveable(kind, saver = EditRow.Rows) { mutableStateListOf<EditRow>().apply { addAll(start) } }
    var renaming by rememberSaveable { mutableStateOf<Int?>(null) }
    var adding by rememberSaveable { mutableStateOf("") }
    // 1.37 PC 확인 6a4efc92: 제목 줄에 닫기가 있어 손잡이 X는 빼요 (X가 두 개였어요).
    com.bamnat.app.ui.components.GuardedSheet(onDismissRequest = onDismiss, unsaved = rows.toList() != start || adding.isNotBlank(), closeButton = false) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            SheetTitle("분류 고치기", onDismiss)
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                com.bamnat.app.ui.components.ChoiceRow(MoneyKind.entries.map { it.label }, selected = kind.ordinal, onSelect = { kind = MoneyKind.entries[it] },
                    modifier = Modifier.padding(bottom = 8.dp))
                rows.forEachIndexed { i, r ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).semantics(mergeDescendants = true) {
                            customActions = listOfNotNull(
                                CustomAccessibilityAction("위로") { if (i > 0) { rows.add(i - 1, rows.removeAt(i)) }; true }.takeIf { i > 0 },
                                CustomAccessibilityAction("아래로") { if (i < rows.lastIndex) { rows.add(i + 1, rows.removeAt(i)) }; true }.takeIf { i < rows.lastIndex },
                            )
                        },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CategoryAvatar(r.name, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(r.name + if (r.hidden) " (숨김)" else "", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                            color = if (r.hidden) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        IconButton(onClick = { renaming = i }) { Icon(Icons.Outlined.Edit, "${r.name} 이름 바꾸기") }
                        IconButton(onClick = { rows[i] = r.copy(hidden = !r.hidden) }) {
                            Icon(if (r.hidden) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (r.hidden) "${r.name} 다시 보이기" else "${r.name} 숨기기")
                        }
                        IconButton(enabled = i > 0, onClick = { rows.add(i - 1, rows.removeAt(i)) }) { Icon(Icons.Filled.KeyboardArrowUp, "${r.name} 위로") }
                        IconButton(enabled = i < rows.lastIndex, onClick = { rows.add(i + 1, rows.removeAt(i)) }) { Icon(Icons.Filled.KeyboardArrowDown, "${r.name} 아래로") }
                    }
                }
                Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                    CategoryAvatar(MoneyCategories.OTHER, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("${MoneyCategories.OTHER} (늘 마지막)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                    OutlinedTextField(value = adding, onValueChange = { adding = it.take(MoneyCategories.MAX_NAME) }, singleLine = true,
                        label = { Text("새 분류") }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(8.dp))
                    val clean = MoneyCategories.clean(adding)
                    TextButton(enabled = clean.isNotEmpty() && rows.none { it.name == clean } && clean != MoneyCategories.OTHER, onClick = {
                        rows.add(EditRow(clean, null, false))
                        adding = ""
                    }) { Text("더하기") }
                }
                Text("이름을 바꾸면 그 분류의 기록·예산·반복도 함께 바뀌어요. 숨긴 분류의 기록은 그대로 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp))
            }
            HorizontalDivider()
            Button(
                onClick = {
                    val edits = rows.mapIndexed { i, r -> CategoryEdit(kind, r.name, i, r.hidden) }
                    val renames = rows.mapNotNull { r -> r.was?.takeIf { it != r.name }?.let { it to r.name } }
                    onSave(kind, edits, renames)
                },
                modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 48.dp),
            ) { Text("${kind.label} 분류 저장") }
        }
    }
    renaming?.let { i ->
        val r = rows.getOrNull(i) ?: return@let
        // 2.0.0 [RVK-08] (MONEY-03·T06a-10): 급여·카드값·이자·금융 and the like are read by name in the sums; renamed, 급여 would be
        // counted twice. They say so instead of opening a field; hiding them still works.
        if (!MoneyCategories.renamable(r.was ?: r.name)) {
            AlertDialog(
                onDismissRequest = { renaming = null },
                title = { Text("‘${r.name}’ 이름은 그대로 둬요") },
                text = { Text("계산에 쓰는 분류라 이름을 바꿀 수 없어요. 안 쓰면 숨길 수 있어요.") },
                confirmButton = { TextButton(onClick = { renaming = null }) { Text("확인") } },
            )
            return@let
        }
        var text by remember(i) { mutableStateOf(r.name) }
        val clean = MoneyCategories.clean(text)
        val taken = rows.any { it !== r && it.name == clean }
        val fixed = clean.isNotEmpty() && !MoneyCategories.renamable(clean)
        AlertDialog(
            onDismissRequest = { renaming = null },
            title = { Text("이름 바꾸기") },
            text = {
                OutlinedTextField(value = text, onValueChange = { text = it.take(MoneyCategories.MAX_NAME) }, singleLine = true,
                    label = { Text("분류 이름") }, modifier = Modifier.fillMaxWidth(), isError = taken || fixed,
                    supportingText = when {
                        taken -> { { Text("이미 있는 이름이에요") } }
                        fixed -> { { Text("계산에 쓰는 이름이라 쓸 수 없어요") } }
                        else -> null
                    })
            },
            confirmButton = {
                TextButton(enabled = clean.isNotEmpty() && !fixed && !taken, onClick = {
                    rows[i] = r.copy(name = clean)
                    renaming = null
                }) { Text("바꾸기") }
            },
            dismissButton = { TextButton(onClick = { renaming = null }) { Text("취소") } },
        )
    }
}
