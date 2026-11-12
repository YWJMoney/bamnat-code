package com.bamnat.app.ui.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.CompositionLocalProvider
import com.bamnat.app.ui.components.LongTextField
import com.bamnat.app.ui.components.edgeScrollViewport
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ConfirmDialog
import com.bamnat.app.ui.money.AmountField
import com.bamnat.app.ui.money.amountDigits
import com.bamnat.app.ui.money.toAmount
import com.bamnat.core.invest.AccountType
import com.bamnat.core.invest.AlarmKind
import com.bamnat.core.invest.CashFlow
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.Emotion
import com.bamnat.core.invest.EPS
import com.bamnat.core.invest.InvestAccount
import com.bamnat.core.invest.InvestAlarm
import com.bamnat.core.invest.InvestEvent
import com.bamnat.core.invest.InvestFees
import com.bamnat.core.invest.InvestTaxes
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Quote
import com.bamnat.core.invest.REASON_TAGS
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeDraft
import com.bamnat.core.invest.holdingKey
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.roundToLong
import com.bamnat.core.time.Today
import com.bamnat.app.ui.theme.bamnat

/** [INV-01] Which of the investor's sheets is open. */
sealed interface InvestSheet {
    data class TradeEdit(val draft: TradeDraft? = null, val editing: Trade? = null) : InvestSheet
    /** 2.0.0 [RVK-34] (검토 U06b-03) [symbol]·[accountId]: a 종목 card's '배당' opens with that 종목 and its account. */
    data class CashEdit(val kind: CashKind = CashKind.DEPOSIT, val date: LocalDate? = null, val editing: CashFlow? = null, val symbol: String = "",
                        val accountId: Long? = null) : InvestSheet
    data class AccountEdit(val editing: InvestAccount? = null) : InvestSheet
    data object Quotes : InvestSheet
    /** [RVK-34] (U06b-03) [holding]: a 종목 card's '목표가 알람' opens with that 종목 and market. */
    data class AlarmEdit(val kind: AlarmKind = AlarmKind.PLAN, val editing: InvestAlarm? = null, val holding: TradeDraft? = null) : InvestSheet
    data class EventEdit(val date: LocalDate? = null, val editing: InvestEvent? = null) : InvestSheet
    data object Goal : InvestSheet
    data object Principles : InvestSheet
    data class Journal(val date: LocalDate) : InvestSheet
    /** 2.0.0 [RVK-27] 수량 바뀜 (분할·병합, 입고, 출고): the draft's side says which. */
    data class QuantityEdit(val draft: TradeDraft? = null, val editing: Trade? = null) : InvestSheet
    /** 2.0.0 [RVK-28] 종목 한 장: one holding's records in order; [accountId] null for every account. */
    data class Story(val key: String, val accountId: Long? = null) : InvestSheet
}

/** Opens [sheet] over whichever screen asked: the 투자 tab, the calendar or the day. */
@Composable
fun InvestSheetHost(vm: InvestViewModel, sheet: InvestSheet, onSheet: (InvestSheet?) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val close = { onSheet(null) }
    // A new sheet (a trade copied from the one open) starts from its own values.
    androidx.compose.runtime.key(sheet) { when (sheet) {
        // [RVK-27] A 수량 바뀜 opened from a list (기록, 달력, 종목 한 장) opens its own sheet.
        is InvestSheet.TradeEdit -> if (sheet.editing?.side?.trade == false) QuantityChangeSheet(vm, state, null, sheet.editing, onDismiss = close)
        else TradeSheet(vm, state, sheet.draft, sheet.editing, onDismiss = close,
            onCopy = { t -> onSheet(InvestSheet.TradeEdit(TradeDraft(t.symbol, t.name, t.market, t.side, t.quantity, t.price, t.accountId, date = state.today), null)) },
            onQuantity = { d -> onSheet(InvestSheet.QuantityEdit(d)) })
        is InvestSheet.QuantityEdit -> QuantityChangeSheet(vm, state, sheet.draft, sheet.editing, onDismiss = close)
        is InvestSheet.Story -> HoldingStorySheet(state, sheet.key, sheet.accountId, onDismiss = close, open = onSheet)
        is InvestSheet.CashEdit -> CashSheet(vm, state, sheet.kind, sheet.date, sheet.editing, onDismiss = close, symbol0 = sheet.symbol, account0 = sheet.accountId)
        is InvestSheet.AccountEdit -> AccountSheet(vm, sheet.editing, onDismiss = close)
        InvestSheet.Quotes -> QuoteSheet(vm, state, onDismiss = close, open = onSheet)
        is InvestSheet.AlarmEdit -> AlarmSheet(vm, state, sheet.kind, sheet.editing, onDismiss = close, from = sheet.holding)
        is InvestSheet.EventEdit -> EventSheet(vm, sheet.date ?: state.today, sheet.editing, onDismiss = close)
        InvestSheet.Goal -> GoalSheet(vm, state, onDismiss = close)
        InvestSheet.Principles -> PrinciplesSheet(vm, state, onDismiss = close)
        is InvestSheet.Journal -> JournalSheet(vm, state, sheet.date, onDismiss = close)
    } }
}

/** The investor's sheets share one frame: title and actions on top, the form, one button at the bottom. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InvestSheetFrame(
    title: String,
    onDismiss: () -> Unit,
    onSave: (() -> Unit)?,
    saveLabel: String = "저장",
    canSave: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
    /**
     * 2.0.0 [RVK-27] (검토 MONEY-64, 조건 10) Something typed that a swipe, 뒤로 or a tap outside would throw away: the sheet asks
     * first, as the ledger's sheets do ([com.bamnat.app.ui.components.GuardedSheet]); the X and 저장 close at once.
     */
    unsaved: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    com.bamnat.app.ui.components.GuardedSheet(onDismissRequest = onDismiss, unsaved = unsaved, closeButton = false) {
        Column(Modifier.fillMaxWidth().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
                actions()
                IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") }
            }
            // [MEMO-SEL-02] 1.30 AY: a long note's selection handles (일지 복기) scroll the form, like a memo's.
            val formScroll = rememberScrollState()
            val edge = com.bamnat.app.ui.components.rememberEdgeScroll(formScroll)
            CompositionLocalProvider(com.bamnat.app.ui.components.LocalEdgeScroll provides edge) {
                Column(
                    Modifier.weight(1f, fill = false).edgeScrollViewport(edge).verticalScroll(formScroll).padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    content()
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (onSave != null) {
                Button(onClick = onSave, enabled = canSave, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp).navigationBarsPadding()) {
                    Text(saveLabel)
                }
            }
        }
    }
}

/**
 * 2.0.0 [RVK-27] (조건 10) Whether what a sheet holds now differs from what it opened with, for [InvestSheetFrame]'s `unsaved`. The
 * first look is kept across a turn of the phone, so a form filled before the turn still asks.
 */
@Composable
internal fun changedSinceOpen(vararg now: Any?): Boolean {
    val look = now.joinToString("\u0001") { it.toString() }
    val first = rememberSaveable { look }
    return look != first
}

@Composable
internal fun FieldLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** [INV-02] The account a record goes into, as one-tap chips; with none yet the first is made on saving. */
@Composable
internal fun AccountPicker(accounts: List<InvestAccount>, selected: Long, onPick: (Long) -> Unit) {
    if (accounts.isEmpty()) {
        Text("저장하면 ‘일반 계좌’를 만들어 넣어요. 계좌는 나중에 바꿀 수 있어요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    if (accounts.size == 1) return
    FieldLabel("계좌")
    ChoiceChips(accounts, { it.id == selected }, { it.name }, { onPick(it.id) })
}

@Composable
internal fun DateRow(date: LocalDate, minuteText: String?, onDate: (LocalDate) -> Unit, onMinute: ((String) -> Unit)?) {
    var picking by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { picking = true }) {
            Icon(Icons.Outlined.CalendarMonth, null)
            Spacer(Modifier.width(6.dp))
            Text(if (date == Today.date()) "오늘 · ${Fmt.dayTitle(date)}" else Fmt.dayTitle(date))
        }
        // [INPUT-02] The time is picked on the wheels, not typed; it stays "HH:mm" text for the trade's minute.
        if (onMinute != null && minuteText != null) {
            com.bamnat.app.ui.components.TimeField(
                label = "시각", time = parseMinute(minuteText)?.let { LocalTime.of(it / 60, it % 60) },
                onChange = { t -> onMinute(t?.let { minuteText(it.hour * 60 + it.minute) }.orEmpty()) },
                clearable = true, start = LocalTime.of(9, 0), modifier = Modifier.weight(1f),
            )
        }
    }
    if (picking) InvestDatePicker(date, onDate) { picking = false }
}

/**
 * [INV-03][INV-04][INV-08][INV-13][INV-20] One buy or sell: the holding (tapped from the ones already
 * held), quantity and price, the fee and 거래세 worked out as it is typed, then why and how it felt.
 */
@Composable
internal fun TradeSheet(
    vm: InvestViewModel,
    state: InvestState,
    draft: TradeDraft?,
    editing: Trade?,
    onDismiss: () -> Unit,
    onCopy: (Trade) -> Unit,
    /** 2.0.0 [RVK-27] The holding's quantity changed without a trade: 분할·병합, 입고, 출고. */
    onQuantity: (TradeDraft) -> Unit = {},
) {
    val book = state.full
    val accounts = book.activeAccounts
    val start = editing
    val d = draft ?: TradeDraft()
    var side by rememberSaveable { mutableStateOf(start?.side ?: d.side) }
    // [INV-03] A new trade starts in the draft's account, else the chosen one, else the last used (open ones only).
    var accountId by rememberSaveable {
        mutableStateOf(start?.accountId ?: listOf(d.accountId, state.account, book.settings.lastAccountId)
            .firstOrNull { id -> id != null && accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L)
    }
    val account = book.account(accountId)
    var name by rememberSaveable { mutableStateOf(start?.name ?: d.name) }
    var symbol by rememberSaveable { mutableStateOf(start?.symbol ?: d.symbol) }
    var market by rememberSaveable { mutableStateOf(start?.market ?: d.market.takeIf { draft != null && (d.symbol.isNotBlank() || d.name.isNotBlank()) } ?: account?.type?.market ?: Market.KR_STOCK) }
    var qty by rememberSaveable { mutableStateOf(InvestFmt.input(start?.quantity ?: d.quantity)) }
    var price by rememberSaveable { mutableStateOf(InvestFmt.input(start?.price ?: d.price)) }
    var date by rememberSaveable { mutableStateOf((start?.date ?: d.date ?: state.today).toString()) }
    var minute by rememberSaveable {
        mutableStateOf(minuteText(start?.minute ?: if (start == null && (d.date ?: state.today) == Today.date()) Today.time().let { it.hour * 60 + it.minute } else null))
    }
    var fx by rememberSaveable { mutableStateOf(InvestFmt.input(start?.fxRate?.takeIf { start.market.foreign } ?: book.settings.usdKrw)) }
    // [INV-38] Fees follow the quantity, price, market and account until the user writes their own in this sheet;
    // a trade being fixed keeps its stored ones only while none of those changed ([InvestFees.shown]).
    var feeText by rememberSaveable { mutableStateOf(start?.fee?.let { InvestFmt.input(it) } ?: "") }
    var taxText by rememberSaveable { mutableStateOf(start?.tax?.let { InvestFmt.input(it) } ?: "") }
    var costsTyped by rememberSaveable { mutableStateOf(false) }
    var editingFees by remember { mutableStateOf(false) }
    val tags = remember { mutableStateListOf<String>().apply { addAll(start?.tags ?: d.tags) } }
    val emotions = remember { mutableStateListOf<Emotion>().apply { addAll(start?.emotions.orEmpty()) } }
    var rules by rememberSaveable { mutableStateOf(start?.followedRules) }
    var memo by rememberSaveable { mutableStateOf(start?.memo ?: "") }
    var deleting by remember { mutableStateOf(false) }
    // 2.0.0 [RVK-31] (검토 MONEY-66) A 국내 ETF's kind, picked here once and kept for that holding.
    var etfPicked by rememberSaveable { mutableStateOf<Boolean?>(null) }
    // 2.0.0 [RVK-34] (검토 U06b-05) 이유·마음·원칙·메모 open for someone who writes them (this trade has some, or any trade
    // before), one line for everyone else.
    var notesOpen by rememberSaveable {
        mutableStateOf(tags.isNotEmpty() || emotions.isNotEmpty() || rules != null || memo.isNotBlank() ||
            book.trades.any { it.tags.isNotEmpty() || it.emotions.isNotEmpty() || it.followedRules != null || it.memo.isNotBlank() })
    }

    val q = qty.toDecimal() ?: 0.0
    val p = price.toDecimal() ?: 0.0
    val suggestion = InvestFees.suggest(market, side, q, p, account, book.settings)
    val costs = InvestFees.shown(start, if (costsTyped) InvestFees.Suggestion(feeText.toDecimal() ?: 0.0, taxText.toDecimal() ?: 0.0) else null,
        market, side, q, p, accountId, suggestion)
    val fee = costs.fee
    val tax = costs.tax
    val rate = if (market.foreign) fx.toDecimal() ?: book.settings.usdKrw else 1.0
    val key = holdingKey(symbol, name)
    val held = if (key.isEmpty()) 0.0 else vm.heldIn(accountId, key) + (if (start != null && start.key == key && start.accountId == accountId) {
        if (start.side == Side.BUY) -start.quantity else start.quantity
    } else 0.0)
    val holding = state.fullResult.holdings.firstOrNull { it.accountId == accountId && it.key == key }
    val canSave = key.isNotEmpty() && q > 0 && p > 0
    val hide = state.hide
    val etfTaxed = etfPicked ?: book.settings.etfTaxed[key]

    fun save() {
        val day = runCatching { LocalDate.parse(date) }.getOrDefault(state.today)
        vm.saveTrade(Trade(
            id = start?.id ?: 0, accountId = accountId, date = day, minute = parseMinute(minute), symbol = symbol.trim(), name = name.trim(),
            market = market, side = side, quantity = q, price = p, fxRate = rate, fee = fee, tax = tax,
            reason = start?.reason ?: "", emotions = emotions.toSet(), tags = tags.toList(), followedRules = rules, memo = memo.trim(),
            alarmId = start?.alarmId ?: d.alarmId, createdAt = start?.createdAt ?: 0,
        ), start)
        etfPicked?.takeIf { market == Market.KR_ETF && book.settings.etfTaxed[key] != it }?.let { vm.setEtfKind(key, it) }
        if (start == null) d.alertAt?.let { vm.alertSaved(it) }
        onDismiss()
    }

    InvestSheetFrame(
        title = if (start == null) "매매 기록" else "매매 고치기",
        onDismiss = onDismiss,
        unsaved = changedSinceOpen(side, accountId, name, symbol, market, qty, price, date, minute, fx, feeText, taxText, rules, memo,
            tags.toList(), emotions.toList(), etfPicked),
        onSave = ::save,
        saveLabel = if (start == null) "${side.label} 저장" else "고친 내용 저장",
        canSave = canSave,
        actions = {
            if (start != null) {
                IconButton(onClick = { onCopy(start) }) { Icon(Icons.Outlined.ContentCopy, "오늘 날짜로 복사") }
                IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "지우기", tint = MaterialTheme.colorScheme.error) }
            }
            // 2.0.0 [RVK-24] (총괄 #472 리뷰, 조건 9·14) A found trade opened from its line can be let go here too, as the ledger's
            // waiting line opens to a sheet with 지우기: the swipe to the left is a shortcut, not the only way (되돌리기 as on the line).
            if (start == null) d.alertAt?.let { at -> TextButton(onClick = { vm.dropAlertAt(at); onDismiss() }) { Text("넘기기") } }
        },
    ) {
        com.bamnat.app.ui.components.ChoiceRow(Side.TRADES.map { it.label }, selected = Side.TRADES.indexOf(side).coerceAtLeast(0), onSelect = { side = Side.TRADES[it] })
        // 2.0.0 [RVK-27] (검토 T06b-14) A split or shares moved in change what is held without a buy: written on their own sheet.
        // (조건 1) Short enough to sit beside its button on a folded phone, so the sheet's own fields stay near the top.
        if (start == null) com.bamnat.app.ui.money.WordsWithActions({
            Text("분할·무상증자·옮긴 주식은", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }) {
            TextButton(onClick = {
                onQuantity(TradeDraft(symbol.trim(), name.trim(), market, Side.SPLIT, accountId = accountId, date = runCatching { LocalDate.parse(date) }.getOrNull()))
            }) { Text("수량 바뀜 적기") }
        }
        AccountPicker(accounts, accountId) { id ->
            accountId = id
            if (key.isEmpty()) book.account(id)?.type?.market?.let { market = it }
        }
        // One tap on a holding already there fills its name, code and market; a sell also its quantity.
        val known = remember(state.fullResult) { vm.knownHoldings().take(10) }
        if (known.isNotEmpty()) {
            FieldLabel(if (side == Side.SELL) "갖고 있는 종목" else "전에 산 종목")
            val picks = if (side == Side.SELL) known.filter { it.held } else known
            ChoiceChips(picks, { it.key == key }, { it.title }, { h ->
                val changed = h.key != key
                name = h.name; symbol = h.symbol; market = h.market
                // 1.36 실사용 S22: a sell stays a sell, and picking a holding fills what is held there (another holding replaces it).
                if (side == Side.SELL && (qty.isEmpty() || changed)) {
                    val inAccount = state.fullResult.holdings.firstOrNull { it.accountId == accountId && it.key == h.key }?.quantity ?: h.quantity
                    if (inAccount > EPS) qty = InvestFmt.input(inAccount)
                }
                if (price.isEmpty()) book.quote(h.key)?.let { price = InvestFmt.input(it.price) }
            })
        }
        NameCodeFields(name, { name = it }, symbol, { symbol = it }, examples = true)
        ChoiceChips(Market.entries, { it == market }, { it.label }, { market = it })
        // 2.0.0 [RVK-31] (검토 MONEY-66·T06b-12) Only 국내주식형 ETF gains are untaxed; asked once for each ETF and kept, so the sale's
        // line and the year's 세금 미리 보기 are right. Not picked: the sale says both.
        if (market == Market.KR_ETF) {
            FieldLabel("ETF 종류")
            ChoiceChips(listOf(false, true), { it == etfTaxed }, { if (it) "해외지수·채권·원자재 등" else "국내주식형" }, { etfPicked = it })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField("수량", qty, { qty = it }, Modifier.weight(1f), suffix = market.unit)
            DecimalField(if (market.foreign) "단가 (달러)" else "단가", price, { price = it }, Modifier.weight(1.3f), suffix = if (market.foreign) "$" else "원")
        }
        if (side == Side.SELL && held > EPS) {
            // 1.36 U12 (매도 단가가 빈칸): what it was bought at, as the line to sell from.
            holding?.takeIf { it.held && it.averagePrice > 0 }?.let { h ->
                Text("평균 ${InvestFmt.average(h.averagePrice, market)}에 샀어요" + (book.quote(h.key)?.let { " · 현재가 ${InvestFmt.price(it.price, market)}" } ?: ""),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("이 계좌에 ${InvestFmt.quantity(held)}${market.unit} 있어요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { qty = InvestFmt.input(held) }) { Text("전부") }
                TextButton(onClick = { qty = InvestFmt.input(if (market.fractional) held / 2 else kotlin.math.floor(held / 2)) }) { Text("절반") }
            }
        }
        if (side == Side.SELL && key.isNotEmpty() && q > held + EPS) {
            Text("갖고 있는 수량보다 많아요. 넘는 만큼은 손익 없이 계산해요. 산 기록을 먼저 적어 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.bamnat.caution)
        }
        if (market.foreign) DecimalField("환율 (1달러)", fx, { fx = it }, Modifier.fillMaxWidth(), suffix = "원",
            supporting = "실제 체결 환율을 적으면 원화 손익이 더 정확해요")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DecimalField("수수료", if (costsTyped) feeText else InvestFmt.input(fee), { feeText = it; taxText = if (costsTyped) taxText else InvestFmt.input(tax); costsTyped = true },
                Modifier.weight(1f), suffix = if (market.foreign) "$" else "원")
            DecimalField("세금", if (costsTyped) taxText else InvestFmt.input(tax), { taxText = it; feeText = if (costsTyped) feeText else InvestFmt.input(fee); costsTyped = true },
                Modifier.weight(1f), suffix = if (market.foreign) "$" else "원")
        }
        val feeRate = InvestFees.rateFor(market, side, account, book.settings)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (costsTyped) "직접 적은 수수료·세금으로 저장해요" else
                    "수수료 ${InvestFees.rateText(feeRate)}%" + (if (account?.ownFee(market) != null) " (이 계좌)" else "") +
                        (if (side == Side.SELL && market.sellTaxRate > 0) " · 거래세 ${market.sellTaxRate}%" else "") + "로 자동 계산했어요. 고치면 그 값으로 저장해요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f),
            )
            // [INV-38] Back to the worked-out costs, or the calendar's own 기본 수수료 changed once for every trade.
            if (costsTyped) TextButton(onClick = { costsTyped = false }) { Text("자동 계산") }
            else TextButton(onClick = { editingFees = true }) { Text("기본 수수료") }
        }
        // What this trade comes to, and for a sell what it made against 평균단가.
        if (q > 0 && p > 0) {
            val grossWon = q * p * rate
            val costsWon = (fee + tax) * rate
            val total = if (side == Side.BUY) grossWon + costsWon else grossWon - costsWon
            val lines = buildList {
                add((if (side == Side.BUY) "내는 돈 " else "받는 돈 ") + InvestFmt.won(total.roundToLong(), hide))
                var gain = 0L
                if (side == Side.SELL && holding != null && holding.quantity > EPS) {
                    val sold = minOf(q, holding.quantity)
                    val cost = holding.costWon * sold / holding.quantity
                    val pnl = (total * sold / q - cost).roundToLong()
                    gain = pnl
                    add("예상 실현손익 ${InvestFmt.signed(pnl, hide)} (${InvestFmt.percent(if (cost > 0) pnl / cost else null)})")
                }
                // [INV-39] A sell of 해외 주식: what it adds to this year's 양도소득세; 국내 주식: why there is none.
                if (side == Side.SELL) {
                    val day = runCatching { LocalDate.parse(date) }.getOrDefault(state.today)
                    val preview = com.bamnat.core.invest.InvestTaxes.sellPreview(book, Trade(id = start?.id ?: 0, accountId = accountId, date = day,
                        symbol = symbol.trim(), name = name.trim(), market = market, side = side, quantity = q, price = p, fxRate = rate, fee = fee, tax = tax))
                    if (preview != null) add(
                        if (preview.extraTax > 0) "예상 양도소득세 +${InvestFmt.won(preview.extraTax, hide)} (22%, 올해 해외 주식 이익 ${InvestFmt.signed(preview.yearGain, hide)}, 250만 원 공제 뒤)"
                        else "양도소득세 없음: 올해 해외 주식 이익이 250만 원 공제 안이에요 (남은 공제 ${InvestFmt.won(preview.deductionLeft, hide)})"
                    // 2.0.0 [RVK-31] (검토 U06b-11) Every other market and ISA·연금 say their own line; with amounts hidden, no amount.
                    ) else com.bamnat.core.invest.InvestTaxes.sellLine(book, Trade(accountId = accountId, date = day, symbol = symbol.trim(),
                        name = name.trim(), market = market, side = side, quantity = q, price = p), gainWon = if (hide) 0L else gain,
                        etfTaxed = etfTaxed)?.let { add(it) }
                }
                if (side == Side.BUY) {
                    val cash = state.fullResult.accounts.firstOrNull { it.accountId == accountId }?.cash ?: 0L
                    val after = cash - total.roundToLong() + (if (start != null && start.side == Side.BUY) (-start.cashWon).roundToLong() else 0L)
                    if (after < 0) add("현금이 ${InvestFmt.won(-after, hide)} 모자라요. 입금도 적어 두면 원금과 수익률이 맞아요")
                    else add("남는 현금 ${InvestFmt.won(after, hide)}")
                }
            }
            Text(lines.joinToString("\n"), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
        DateRow(runCatching { LocalDate.parse(date) }.getOrDefault(state.today), minute, { date = it.toString() }, { minute = it })
        HorizontalDivider()
        if (notesOpen) {
            FieldLabel("이유")
            ChoiceChips(REASON_TAGS, { it in tags }, { it }, { t -> if (t in tags) tags.remove(t) else tags.add(t) })
            FieldLabel("그때 마음")
            ChoiceChips(Emotion.entries, { it in emotions }, { it.label }, { e -> if (e in emotions) emotions.remove(e) else emotions.add(e) })
            FieldLabel("내 원칙")
            ChoiceChips(listOf(true, false), { rules == it }, { if (it) "지켰어요" else "어겼어요" }, { v -> rules = if (rules == v) null else v })
            LongTextField(memo, { memo = it.take(500) }, label = { Text("메모") }, placeholder = { Text("왜 샀는지, 언제 팔지") },
                modifier = Modifier.fillMaxWidth(), minLines = 2)
        } else Row(
            // 2.0.0 [RVK-34] (검토 U06b-05) One line until wanted: 수량·단가·요약 come first and the sheet ends at 저장.
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = androidx.compose.ui.semantics.Role.Button) { notesOpen = true },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("이유·마음·메모 적기", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    }
    if (editingFees) FeeRatesDialog(book.settings, onDismiss = { editingFees = false }) { buy, sell ->
        vm.saveSettings { it.copy(buyFeeRate = buy, sellFeeRate = sell) }
        editingFees = false
    }
    if (deleting && start != null) ConfirmDialog(
        title = "이 매매를 지울까요?", message = "${start.title} ${start.side.label} ${InvestFmt.quantity(start.quantity)}${start.market.unit} 기록이 없어지고 손익도 다시 계산돼요. 바로 되돌릴 수 있어요.",
        confirmLabel = "지우기", destructive = true, onDismiss = { deleting = false },
        onConfirm = { deleting = false; vm.deleteTrade(start); onDismiss() },
    )
}

/**
 * [INV-02][INV-05][INV-09] 입금·출금, 배당·이자, 기타 비용. While a deposit or withdrawal is typed the
 * sheet shows what it does to 원금, 수익률, cash and the goal, before it is saved.
 */
@Composable
internal fun CashSheet(vm: InvestViewModel, state: InvestState, kind0: CashKind, date0: LocalDate?, editing: CashFlow?, onDismiss: () -> Unit,
                       symbol0: String = "", account0: Long? = null) {
    val book = state.full
    val accounts = book.activeAccounts
    var kind by rememberSaveable { mutableStateOf(editing?.kind ?: kind0) }
    var accountId by rememberSaveable {
        mutableStateOf(editing?.accountId ?: (account0 ?: state.account ?: book.settings.lastAccountId)?.takeIf { id -> accounts.any { it.id == id } }
            ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var amount by rememberSaveable { mutableStateOf(amountDigits(editing?.amount ?: 0)) }
    var taxText by rememberSaveable { mutableStateOf(amountDigits(editing?.tax ?: 0)) }
    var taxTyped by rememberSaveable { mutableStateOf(editing != null) }
    var symbol by rememberSaveable { mutableStateOf(editing?.symbol ?: symbol0) }
    var memo by rememberSaveable { mutableStateOf(editing?.memo ?: "") }
    var date by rememberSaveable { mutableStateOf((editing?.date ?: date0 ?: state.today).toString()) }
    var deleting by remember { mutableStateOf(false) }
    val a = amount.toAmount()
    val hide = state.hide
    val holdingMarket = state.fullResult.byKey.firstOrNull { it.key == holdingKey(symbol, "") }?.market ?: Market.KR_STOCK
    val suggestedTax = if (kind == CashKind.DIVIDEND) InvestTaxes.dividendTax(a, holdingMarket)
        else if (kind == CashKind.INTEREST) (a * InvestTaxes.DIVIDEND_RATE).toLong() else 0L
    val tax = if (!kind.isIncome) 0L else if (taxTyped) taxText.toAmount() else suggestedTax

    fun save() {
        val day = runCatching { LocalDate.parse(date) }.getOrDefault(state.today)
        vm.saveCash(CashFlow(id = editing?.id ?: 0, accountId = accountId, date = day, minute = editing?.minute, kind = kind, amount = a,
            tax = tax.coerceIn(0, a), symbol = if (kind == CashKind.DIVIDEND) symbol.trim() else "", memo = memo.trim(), createdAt = editing?.createdAt ?: 0), editing)
        onDismiss()
    }

    InvestSheetFrame(
        title = if (editing == null) "${kind.label} 적기" else "${kind.label} 고치기",
        onDismiss = onDismiss, onSave = ::save, canSave = a > 0,
        unsaved = changedSinceOpen(kind, accountId, amount, taxText, symbol, memo, date),
        actions = {
            if (editing != null) IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "지우기", tint = MaterialTheme.colorScheme.error) }
        },
    ) {
        ChoiceChips(CashKind.entries, { it == kind }, { it.label }, { kind = it })
        AccountPicker(accounts, accountId) { accountId = it }
        AmountField(label = if (kind.isIncome) "받은 금액 (세전)" else "금액", digits = amount, onChange = { amount = it }, modifier = Modifier.fillMaxWidth())
        // Quick amounts for the usual round deposits.
        if (kind == CashKind.DEPOSIT || kind == CashKind.WITHDRAW) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(100_000L, 500_000L, 1_000_000L, 5_000_000L).forEach { step ->
                    OutlinedButton(onClick = { amount = (a + step).toString() }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)) {
                        Text("+" + Fmt.wonShort(step))
                    }
                }
            }
        }
        if (kind == CashKind.DIVIDEND) {
            val held = state.fullResult.byKey.filter { it.held || it.dividendsWon > 0 }.take(10)
            if (held.isNotEmpty()) ChoiceChips(held, { it.symbol.equals(symbol, true) || it.key == holdingKey(symbol, "") }, { it.title }, { symbol = it.symbol.ifBlank { it.name } })
            OutlinedTextField(symbol, { symbol = it.take(20) }, label = { Text("종목") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (kind.isIncome) {
            AmountField(label = "떼인 세금", digits = if (taxTyped) taxText else amountDigits(suggestedTax), onChange = { taxText = it; taxTyped = true }, modifier = Modifier.fillMaxWidth())
            Text(
                if (taxTyped) "실제로 떼인 세금으로 저장해요"
                else if (kind == CashKind.DIVIDEND && holdingMarket.foreign) "미국 배당은 현지에서 15%를 떼요. 실수령 ${InvestFmt.won(a - tax, hide)}"
                else "배당·이자소득세 15.4%로 계산했어요. 실수령 ${InvestFmt.won(a - tax, hide)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        // [INV-05] Principal typed: 원금, 수익률, cash and the goal move at once.
        if (a > 0) {
            val typed = CashFlow(accountId = accountId, date = state.today, kind = kind, amount = a, tax = tax.coerceIn(0, a))
            CashPreview(state, typed.cashWon - (editing?.cashWon ?: 0), typed.principalWon - (editing?.principalWon ?: 0))
        }
        DateRow(runCatching { LocalDate.parse(date) }.getOrDefault(state.today), null, { date = it.toString() }, null)
        OutlinedTextField(memo, { memo = it.take(200) }, label = { Text("메모") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    }
    if (deleting && editing != null) ConfirmDialog(
        title = "이 기록을 지울까요?", message = "${editing.kind.label} ${InvestFmt.won(editing.amount, hide)} 기록이 없어지고 원금과 수익률도 다시 계산돼요.",
        confirmLabel = "지우기", destructive = true, onDismiss = { deleting = false },
        onConfirm = { deleting = false; vm.deleteCash(editing); onDismiss() },
    )
}

/** [INV-05][INV-11] 원금 1,000만 → 1,100만 · 수익률 +5.00% → +4.55% · 현금 · 목표. */
@Composable
private fun CashPreview(state: InvestState, cashDelta: Long, principalDelta: Long) {
    val s = state.fullResult.summary
    val hide = state.hide
    val newPrincipal = s.principal + principalDelta
    val newAssets = s.totalAssets + cashDelta
    val newRate = if (newPrincipal > 0) (newAssets - newPrincipal).toDouble() / newPrincipal else null
    val lines = buildList {
        if (principalDelta != 0L) add("원금 ${InvestFmt.short(s.principal, hide)} → ${InvestFmt.short(newPrincipal, hide)}")
        add("수익률 ${InvestFmt.percent(s.simpleReturn)} → ${InvestFmt.percent(newRate)}")
        add("현금 ${InvestFmt.short(s.cash, hide)} → ${InvestFmt.short(s.cash + (newAssets - s.totalAssets), hide)}")
        state.settings.goalAmount?.let { g -> add("목표 ${InvestFmt.short(g, hide)}까지 ${"%.0f".format(java.util.Locale.ROOT, newAssets * 100.0 / g)}%") }
    }
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("저장하면 바로 바뀌어요", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
            lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            if (newPrincipal < 0) Text("뺀 돈이 넣은 돈보다 많아요. 처음 넣은 돈도 입금으로 적어 주세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
        }
    }
}

/** [INV-02][INV-03] An account: its name, kind (which decides taxes and the market), broker and its own fee. */
@Composable
internal fun AccountSheet(vm: InvestViewModel, editing: InvestAccount?, onDismiss: () -> Unit) {
    var type by rememberSaveable { mutableStateOf(editing?.type ?: AccountType.GENERAL) }
    var name by rememberSaveable { mutableStateOf(editing?.name ?: "") }
    var broker by rememberSaveable { mutableStateOf(editing?.broker ?: "") }
    var fee by rememberSaveable { mutableStateOf(editing?.feeRate?.let { InvestFmt.input(it) } ?: "") }
    // [INV-33] A 국내 account's own fee on 해외 주식, which brokers charge apart.
    var usFee by rememberSaveable { mutableStateOf(editing?.marketFees?.get(Market.US_STOCK)?.let { InvestFmt.input(it) } ?: "") }
    val usApart = !type.market.foreign && type != AccountType.CRYPTO
    var deleting by remember { mutableStateOf(false) }
    InvestSheetFrame(
        title = if (editing == null) "계좌 추가" else "계좌 고치기",
        onDismiss = onDismiss,
        unsaved = changedSinceOpen(type, name, broker, fee, usFee),
        onSave = {
            val fees = (editing?.marketFees.orEmpty() - Market.US_STOCK) +
                (usFee.toDecimal()?.takeIf { usApart && it in 0.0..5.0 }?.let { mapOf(Market.US_STOCK to it) } ?: emptyMap())
            vm.saveAccount((editing ?: InvestAccount(name = "")).copy(name = name.ifBlank { type.label }, type = type, broker = broker.trim(),
                feeRate = fee.toDecimal(), marketFees = fees))
            onDismiss()
        },
        actions = {
            if (editing != null) IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "계좌 정리", tint = MaterialTheme.colorScheme.error) }
        },
    ) {
        ChoiceChips(AccountType.entries, { it == type }, { it.label }, { type = it })
        OutlinedTextField(name, { name = it.take(20) }, label = { Text("이름") }, placeholder = { Text(type.label) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(broker, { broker = it.take(20) }, label = { Text("증권사·거래소 (선택)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DecimalField("수수료율 (선택)", fee, { fee = it }, Modifier.fillMaxWidth(), suffix = "%",
            supporting = "비워 두면 ${type.market.label} 보통 수수료 ${type.market.defaultFeeRate}%로 계산해요. 증권사 이벤트로 바뀌면 여기서 고쳐요",
            imeAction = if (usApart) ImeAction.Next else ImeAction.Done)
        if (usApart) {
            DecimalField("해외 주식 수수료율 (선택)", usFee, { usFee = it }, Modifier.fillMaxWidth(), suffix = "%",
                supporting = "이 계좌로 미국 주식도 사면 적어요. 비워 두면 위 수수료율로 계산해요", imeAction = ImeAction.Done)
        }
        Text(
            when {
                type == AccountType.ISA -> "ISA는 순이익 200만 원(서민형 400만 원)까지 세금이 없고, 넘는 만큼 9.9%만 내요."
                type.taxDeferred -> "연금저축·IRP는 이익에 바로 세금이 붙지 않고, 연금으로 받을 때 내요."
                type == AccountType.CRYPTO -> "코인 이익은 2027년부터 250만 원을 넘는 만큼 22% 세금이 붙을 예정이에요."
                type == AccountType.OVERSEAS -> "해외 주식은 1년 이익에서 250만 원을 뺀 만큼 22%를 다음 해 5월에 신고해요."
                else -> "국내 주식은 팔 때 거래세 0.20%가 붙고, 대주주가 아니면 양도세는 없어요. ETF는 거래세가 없어요."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    if (deleting && editing != null) ConfirmDialog(
        title = "‘${editing.name}’ 계좌를 정리할까요?", message = "기록이 있으면 목록에서만 숨기고 합계에는 그대로 남겨요. 기록이 없으면 지워요.",
        confirmLabel = "정리", destructive = true, onDismiss = { deleting = false },
        onConfirm = { deleting = false; vm.deleteAccount(editing); onDismiss() },
    )
}

/**
 * [INV-04][INV-19] 현재가 for every holding at once, typed from the broker's app, and the dollar's rate.
 * Nothing is fetched: the prices are the user's own.
 * 2.0.0 [RVK-25] (검토 MONEY-69·I06ba-02) Only a price typed anew is dated today; one left as it was keeps its day (before, saving
 * dated every price today), unless the user says it was looked at today. Each line says how old its price is.
 * 2.0.0 [RVK-26] (검토 U06b-01·I06bb-01) 잔고 캡처: one screenshot of the broker's 잔고 fills the prices, and a quantity the book does
 * not have is asked as a split, a missing trade or a holding not written yet; going there keeps the prices (saved first).
 */
@Composable
internal fun QuoteSheet(
    vm: InvestViewModel, state: InvestState, onDismiss: () -> Unit, open: (InvestSheet) -> Unit = {},
    /** Text of a 잔고 capture read elsewhere (a test). */
    initialShot: String? = null,
) {
    val held = state.fullResult.held
    val today = state.today
    val first = remember { held.associate { it.key to InvestFmt.input(it.quote?.price) } }
    // (조건 10) What was typed or read from a capture stays when the phone turns.
    val typed = rememberSaveable(saver = androidx.compose.runtime.saveable.listSaver(
        save = { m -> m.flatMap { (k, v) -> listOf(k, v) } },
        restore = { l -> mutableStateMapOf<String, String>().apply { l.chunked(2).forEach { (k, v) -> put(k, v) } } },
    )) { mutableStateMapOf<String, String>().apply { putAll(first) } }
    val foreign = held.any { it.market.foreign }
    var usd by rememberSaveable { mutableStateOf(InvestFmt.input(state.settings.usdKrw)) }
    var lookedToday by rememberSaveable { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var shot by remember { mutableStateOf<List<com.bamnat.core.invest.HoldingShots.Found>?>(null) }
    fun changed(key: String) = typed[key].orEmpty() != first[key].orEmpty()
    // Prices left as they were from an earlier day.
    val old = held.filter { h -> h.quote?.date?.isBefore(today) == true && !changed(h.key) }

    fun take(text: String) {
        val found = com.bamnat.core.invest.HoldingShots.match(com.bamnat.core.invest.HoldingSheetReader.read(text), state.result.held)
        found.forEach { f -> f.holding?.let { h -> if (f.price > 0) typed[h.key] = InvestFmt.input(f.price) } }
        shot = found
    }
    androidx.compose.runtime.LaunchedEffect(initialShot) { initialShot?.let(::take) }

    fun save() {
        val quotes = quotesToSave(held, typed, first, today, lookedToday)
        // [INV-04] Blank or 0 takes the price away, as a price of nothing is no price.
        val removed = held.filter { h -> h.quote != null && (typed[h.key]?.toDecimal() ?: 0.0) <= 0 }.map { it.key }
        vm.saveQuotes(quotes, removed, if (foreign) usd.toDecimal() else null)
    }

    InvestSheetFrame(
        title = "현재가 적기",
        onDismiss = onDismiss,
        unsaved = typed.any { (k, v) -> v != first[k].orEmpty() } || lookedToday,
        onSave = { save(); onDismiss() },
        canSave = held.isNotEmpty(),
    ) {
        Text("증권사 앱에서 본 가격을 적어 주세요. 가격을 받아 오지는 않아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (held.isEmpty()) Text("갖고 있는 종목이 아직 없어요. 매수를 먼저 적어 주세요.", style = MaterialTheme.typography.bodyMedium)
        else OutlinedButton(onClick = { capturing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Outlined.PhotoCamera, null)
            Spacer(Modifier.width(6.dp))
            Text("잔고 화면 캡처로 한 번에 채우기")
        }
        // A capture read: what it filled and what the book does not have; a fix goes to its sheet with the prices kept.
        shot?.let { found -> ShotFindings(found) { sheet -> save(); open(sheet) } }
        if (foreign) DecimalField("환율 (1달러)", usd, { usd = it }, Modifier.fillMaxWidth(), suffix = "원", supporting = "모든 해외 종목의 원화 평가에 써요")
        held.forEach { h ->
            val read = shot?.firstOrNull { it.holding?.key == h.key }
            val age = h.quote?.date?.let { java.time.temporal.ChronoUnit.DAYS.between(it, today) }
            DecimalField(
                label = h.title, text = typed[h.key].orEmpty(), onChange = { typed[h.key] = it }, modifier = Modifier.fillMaxWidth(),
                suffix = if (h.market.foreign) "$" else "원",
                supporting = "평균 ${InvestFmt.average(h.averagePrice, h.market)} · ${InvestFmt.quantity(h.quantity)}${h.market.unit}" + when {
                    read != null && read.unsure && changed(h.key) -> " · 캡처에서 읽음, 맞는지 봐 주세요"
                    read != null && changed(h.key) -> " · 캡처에서 읽음"
                    changed(h.key) -> ""
                    h.quote != null -> " · ${InvestFmt.ago(h.quote!!.date, today)} 가격"
                    else -> ""
                },
                warn = (read?.unsure == true && changed(h.key)) || (!changed(h.key) && (age ?: 0) >= InvestFmt.STALE_DAYS),
            )
        }
        if (old.isNotEmpty()) Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .toggleable(value = lookedToday, role = androidx.compose.ui.semantics.Role.Checkbox) { lookedToday = it },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            androidx.compose.material3.Checkbox(checked = lookedToday, onCheckedChange = null)
            Spacer(Modifier.width(8.dp))
            Text("그대로 둔 ${old.size}개도 오늘 본 가격이에요", style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (capturing) HoldingShotPicker(onDismiss = { capturing = false }, onText = { take(it); capturing = false })
}

/**
 * 2.0.0 [RVK-25] (검토 MONEY-69) The prices a 현재가 save writes, dated [today]: each typed anew (or first typed), and the ones left
 * as they were only when the user says they looked at them today. Before, every price was saved again as today's.
 */
internal fun quotesToSave(
    held: List<com.bamnat.core.invest.Holding>, typed: Map<String, String>, first: Map<String, String>, today: LocalDate, lookedToday: Boolean,
): List<Quote> = held.mapNotNull { h ->
    val p = typed[h.key]?.toDecimal()?.takeIf { it > 0 } ?: return@mapNotNull null
    if (h.quote != null && typed[h.key].orEmpty() == first[h.key].orEmpty() && !lookedToday) null else Quote(h.key, p, today, h.title, h.market)
}

/**
 * [INV-38] 기본 수수료 (사용자 U17, 결정 D73): 0.05% unless changed, kept in the calendar and used for every trade whose
 * account has no fee of its own; a buy and a sell can differ.
 */
@Composable
internal fun FeeRatesDialog(settings: com.bamnat.core.invest.InvestSettings, onDismiss: () -> Unit, onSave: (Double, Double) -> Unit) {
    var buy by rememberSaveable { mutableStateOf(InvestFees.rateText(settings.buyFeeRate)) }
    var sell by rememberSaveable { mutableStateOf(InvestFees.rateText(settings.sellFeeRate)) }
    val b = buy.toDecimal()?.takeIf { it in 0.0..5.0 }
    val s = sell.toDecimal()?.takeIf { it in 0.0..5.0 }
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("기본 수수료") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("계좌에 따로 정한 수수료가 없으면 모든 매매에 이 값을 써요. 증권사 앱의 수수료율을 넣어 주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                DecimalField("살 때", buy, { buy = it }, Modifier.fillMaxWidth(), suffix = "%")
                DecimalField("팔 때", sell, { sell = it }, Modifier.fillMaxWidth(), suffix = "%")
                TextButton(onClick = {
                    buy = InvestFees.rateText(com.bamnat.core.invest.InvestSettings.DEFAULT_FEE_RATE)
                    sell = InvestFees.rateText(com.bamnat.core.invest.InvestSettings.DEFAULT_FEE_RATE)
                }) { Text("0.05%로 되돌리기") }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(b!!, s!!) }, enabled = b != null && s != null) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
