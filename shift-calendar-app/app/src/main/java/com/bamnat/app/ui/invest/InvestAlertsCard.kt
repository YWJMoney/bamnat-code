package com.bamnat.app.ui.invest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.customActions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.time.Today
import com.bamnat.app.ui.theme.bamnat

/**
 * [INV-32] 알림으로 찾은 거래: each 체결 notification read on the phone waits here until the user writes
 * it (적기), opens it to fix first (고쳐서 적기) or lets it go (넘기기). Nothing is written without them
 * unless 알림 오면 바로 적기 is on (결정 D37).
 */
@Composable
internal fun FoundTradesCard(vm: InvestViewModel, state: InvestState, open: (InvestSheet) -> Unit) {
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val auto by vm.alertAuto.collectAsStateWithLifecycle()
    if (alerts.isEmpty()) {
        // 2.0.0 [RVK-13] 뿌리 7: fills left out as a second copy stay a tap away even when nothing waits.
        com.bamnat.app.ui.money.AutoTidyLine(com.bamnat.app.data.AutoTidy.AREA_INVEST, vm.calendarId)
        return
    }
    val priceless = alerts.count { it.price <= 0 }
    // 2.0.0 [RVK-17] (검토 U06b-02·MONEY-63): which account each line goes to, changed on the line ("at:account" pairs, saveable).
    var pickedText by rememberSaveable { mutableStateOf("") }
    val picked = remember(pickedText) {
        pickedText.split(',').mapNotNull { it.split(':').takeIf { p -> p.size == 2 }?.let { (a, b) -> a.toLongOrNull()?.let { k -> b.toLongOrNull()?.let { k to it } } } }.toMap()
    }
    val shownAccounts = remember(state.full.accounts) { state.full.accounts.filter { !it.hidden } }
    fun accountOf(p: TradeAlerts.Pending): Long? =
        picked[p.at]?.takeIf { id -> shownAccounts.any { it.id == id } } ?: TradeAlerts.accountFor(p, shownAccounts, state.settings.lastAccountId)
    val sure = alerts.filter { TradeAlerts.sure(it) }
    SectionCard(title = "알림으로 찾은 거래 ${alerts.size}건") {
        // 2.0.0 [RVK-24]·HQ-31 (총괄 #457 리뷰): said once for the card, like 가계부's 확인 대기함, instead of three buttons on every line.
        Text("증권사·거래소 체결 알림에서 읽었어요. 맞으면 ✓로 적고, 다르면 눌러서 고쳐 적거나 넘겨요(왼쪽으로 밀어도 넘겨요).",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // [RVK-17] Many waiting at once: the sure ones (a price that agrees) in one tap, one 되돌리기 for all. [RVK-24]·HQ-31 The card's one
        // filled button.
        if (sure.size >= 2) FilledTonalButton(
            onClick = { vm.confirmSure(sure, sure.mapNotNull { p -> accountOf(p)?.let { p.at to it } }.toMap()) },
            modifier = Modifier.align(Alignment.End).heightIn(min = 48.dp),
        ) { Text("확실한 ${sure.size}건 모두 적기") }
        // [INV-40] Lines without a price: one capture of the broker's 체결 내역 fills them.
        if (priceless > 0) com.bamnat.app.ui.money.WordsWithActions({
            Text("단가 없는 알림 ${priceless}건", style = MaterialTheme.typography.bodyMedium)
        }) {
            if (sure.size >= 2) OutlinedButton(onClick = vm::openCapture) { Text("체결 내역 캡처로 채우기") }
            else FilledTonalButton(onClick = vm::openCapture) { Text("체결 내역 캡처로 채우기") }
        }
        alerts.sortedByDescending { it.at }.forEachIndexed { i, p ->
            if (i > 0) HorizontalDivider()
            val into = accountOf(p)
            // Keyed: a line let go (slid away) must not hand its slid state to the line that takes its place.
            androidx.compose.runtime.key(p.at) {
                FoundTrade(p, into = shownAccounts.firstOrNull { it.id == into }, accounts = shownAccounts,
                    onAccount = { id -> pickedText = (picked + (p.at to id)).entries.joinToString(",") { "${it.key}:${it.value}" } }) { action ->
                    when (action) {
                        FoundAction.SAVE -> vm.confirmAlert(p, into)
                        FoundAction.EDIT -> open(InvestSheet.TradeEdit(p.toDraft(into ?: state.account ?: state.settings.lastAccountId)))
                        FoundAction.DROP -> vm.dropAlert(p)
                    }
                }
            }
        }
        HorizontalDivider()
        AutoRow(auto) { vm.setAlertAuto(it) }
        com.bamnat.app.ui.money.AutoTidyLine(com.bamnat.app.data.AutoTidy.AREA_INVEST, vm.calendarId)
    }
}

private enum class FoundAction { SAVE, EDIT, DROP }

/**
 * One fill waiting, shaped like 가계부's 확인 대기 line ([com.bamnat.app.ui.money.PendingRow]): 2.0.0 [RVK-24]·HQ-31 (총괄 #457 리뷰, 조건 2·8)
 * pressed it opens to fix and write (고쳐서 적기), ✓ on the right writes it as read (적기, also slid right), slid left it is let go
 * (넘기기, with 되돌리기). Before, 적기·고쳐서 적기·넘기기 sat under every line: nine things to press for three fills.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun FoundTrade(
    p: TradeAlerts.Pending,
    into: com.bamnat.core.invest.InvestAccount?,
    accounts: List<com.bamnat.core.invest.InvestAccount>,
    onAccount: (Long) -> Unit,
    onAction: (FoundAction) -> Unit,
) {
    val time = java.time.Instant.ofEpochMilli(p.at).atZone(Today.zone())
    // A price the notification did not say has to be typed first (press the line), or read from a capture (the card's button).
    val canSave = p.price > 0 && p.checked != false
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    val swipe = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { v -> v != androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd || canSave },
        positionalThreshold = { it * com.bamnat.app.ui.money.SWIPE_THRESHOLD },
    )
    androidx.compose.runtime.LaunchedEffect(swipe.currentValue) {
        when (swipe.currentValue) {
            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Confirm); onAction(FoundAction.SAVE)
            }
            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> {
                haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.Reject); onAction(FoundAction.DROP)
            }
            androidx.compose.material3.SwipeToDismissBoxValue.Settled -> Unit
        }
    }
    androidx.compose.material3.SwipeToDismissBox(
        state = swipe,
        enableDismissFromStartToEnd = canSave,
        backgroundContent = {
            val toRight = swipe.dismissDirection == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd
            val moving = swipe.dismissDirection != androidx.compose.material3.SwipeToDismissBoxValue.Settled
            Row(Modifier.fillMaxSize().background(
                if (!moving) androidx.compose.ui.graphics.Color.Transparent
                else (if (toRight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant).copy(alpha = 0.3f),
            ).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toRight) Arrangement.Start else Arrangement.End) {
                if (moving) Text(if (toRight) "적기" else "넘기기", style = MaterialTheme.typography.labelLarge)
            }
        },
    ) {
        Row(Modifier.background(MaterialTheme.colorScheme.surfaceContainerLow), verticalAlignment = Alignment.CenterVertically) {
            FoundTradeWords(p, time, into, accounts, onAccount, Modifier.weight(1f)
                .clickable(onClickLabel = "고쳐서 적기", role = androidx.compose.ui.semantics.Role.Button) { onAction(FoundAction.EDIT) }
                .semantics {
                    customActions = listOfNotNull(
                        androidx.compose.ui.semantics.CustomAccessibilityAction("적기") { onAction(FoundAction.SAVE); true }.takeIf { canSave },
                        androidx.compose.ui.semantics.CustomAccessibilityAction("넘기기") { onAction(FoundAction.DROP); true },
                    )
                })
            if (canSave) androidx.compose.material3.IconButton(onClick = { onAction(FoundAction.SAVE) }) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, "적기", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun FoundTradeWords(
    p: TradeAlerts.Pending,
    time: java.time.ZonedDateTime,
    into: com.bamnat.core.invest.InvestAccount?,
    accounts: List<com.bamnat.core.invest.InvestAccount>,
    onAccount: (Long) -> Unit,
    modifier: Modifier,
) {
    Column(modifier.padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(p.side.label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                color = if (p.side == Side.BUY) com.bamnat.app.ui.theme.LocalCalendarColors.current.sunday else com.bamnat.app.ui.theme.LocalCalendarColors.current.saturday)
            TradeAlerts.badge(p.market)?.let { MarketBadge(it, p.market) }
            Text(p.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
        }
        Text(
            listOfNotNull(
                "${InvestFmt.quantity(p.quantity)}${p.market.unit}" + (if (p.price > 0) " × ${InvestFmt.price(p.price, p.market)}" else " · 단가 없음"),
                "${time.monthValue}/${time.dayOfMonth} ${"%02d:%02d".format(time.hour, time.minute)}",
                "${p.app} 알림",
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // [INV-40] Where a price not said came from, and a price that does not agree with the amount.
        when (p.filledBy) {
            TradeAlerts.BY_AMOUNT -> "단가는 알림의 체결 금액 ÷ 수량이에요"
            TradeAlerts.BY_PARTS -> if (p.parts > 1) "알림 ${p.parts}개를 묶은 평균 단가예요 (나누려면 고쳐서 적기)" else "이어 온 알림에서 단가를 찾았어요"
            TradeAlerts.BY_CAPTURE -> "단가는 체결 내역 캡처에서 읽었어요"
            else -> null
        }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        if (p.checked == false) Text("수량 × 단가가 알림의 체결 금액 ${InvestFmt.price(p.amount, p.market)}과 안 맞아요. 고쳐서 적어 주세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.bamnat.caution)
        // 2.0.0 [RVK-17] (검토 U06b-02·MONEY-63): where it goes, and another account in one tap.
        if (into != null) AccountChip(into, accounts, onAccount)
    }
}

/** [RVK-17] '키움 주식 계좌로' as a chip; with more than one account it opens the list to pick another. */
@Composable
private fun AccountChip(into: com.bamnat.core.invest.InvestAccount, accounts: List<com.bamnat.core.invest.InvestAccount>, onPick: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val many = accounts.size > 1
    androidx.compose.foundation.layout.Box {
        androidx.compose.material3.AssistChip(
            onClick = { if (many) open = true },
            label = { Text("${into.name}${com.bamnat.app.ui.Fmt.ro(into.name)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.AccountBalanceWallet, null) },
            trailingIcon = if (many) { { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.ArrowDropDown, null) } } else null,
            modifier = Modifier.semantics { contentDescription = "${into.name} 계좌로 적어요" + if (many) ", 눌러서 바꾸기" else "" },
        )
        com.bamnat.app.ui.components.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            accounts.forEach { a ->
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(a.name + (a.broker.takeIf { it.isNotBlank() && it != a.name }?.let { " · $it" } ?: "")) },
                    onClick = { open = false; onPick(a.id) },
                    trailingIcon = if (a.id == into.id) { { androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Check, "고른 계좌") } } else null,
                )
            }
        }
    }
}

@Composable
internal fun AutoRow(auto: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("알림 오면 바로 적기", style = MaterialTheme.typography.bodyMedium)
            Text("끄면 여기서 확인한 뒤에 적어요. 켜도 단가가 없는 알림은 기다려요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = auto, onCheckedChange = onChange, modifier = Modifier.semantics { contentDescription = "알림 오면 바로 적기" })
    }
}
