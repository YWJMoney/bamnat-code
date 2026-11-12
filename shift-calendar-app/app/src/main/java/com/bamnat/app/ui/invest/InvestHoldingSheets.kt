package com.bamnat.app.ui.invest

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ConfirmDialog
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.components.LongTextField
import com.bamnat.app.ui.components.NudgeCard
import com.bamnat.app.ui.money.NameAndAmount
import com.bamnat.app.ui.money.WordsWithActions
import com.bamnat.app.ui.pay.ScanButtons
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.invest.EPS
import com.bamnat.core.invest.HoldingShots
import com.bamnat.core.invest.HoldingStory
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Portfolio
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.TradeDraft
import com.bamnat.core.invest.holdingKey
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToLong
import com.bamnat.app.ui.theme.bamnat
import androidx.compose.ui.text.withStyle

/** [RVK-27] The three a 수량 바뀜 sheet offers, in the order brokers list them. */
private val CHANGES = listOf(Side.SPLIT, Side.IN, Side.OUT)

/** [RVK-27] A ratio as '1주가 5주로' (1, 5) or a merge as '10주가 1주로' (10, 1). */
internal fun ratioParts(r: Double?): Pair<String, String> = when {
    r == null || r <= 0 -> "1" to ""
    r >= 1 -> "1" to InvestFmt.input(r)
    else -> InvestFmt.input((1 / r).roundToLong().toDouble()) to "1"
}

/**
 * 2.0.0 [RVK-27] (검토 MONEY-64·T06b-14·I06ba-03) 수량 바뀜: 분할·병합, 입고 (무상증자·에어드랍·다른 증권사에서 옮겨 온 주식) and
 * 출고 (다른 증권사로 옮긴 주식). No money moves; what is held and its 평균단가 change as the broker's do, shown before saving.
 * Before, there was no way to write them (a buy needed a price above 0) and the book stayed wrong for good.
 */
@Composable
internal fun QuantityChangeSheet(vm: InvestViewModel, state: InvestState, draft: TradeDraft?, editing: Trade?, onDismiss: () -> Unit) {
    val book = state.full
    val accounts = book.activeAccounts
    val start = editing
    val d = draft ?: TradeDraft(side = Side.SPLIT)
    var side by rememberSaveable { mutableStateOf(start?.side ?: d.side.takeIf { !it.trade } ?: Side.SPLIT) }
    var accountId by rememberSaveable {
        mutableStateOf(start?.accountId ?: listOf(d.accountId, state.account, book.settings.lastAccountId)
            .firstOrNull { id -> id != null && accounts.any { it.id == id } } ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var name by rememberSaveable { mutableStateOf(start?.name ?: d.name) }
    var symbol by rememberSaveable { mutableStateOf(start?.symbol ?: d.symbol) }
    var market by rememberSaveable { mutableStateOf(start?.market ?: d.market) }
    var date by rememberSaveable { mutableStateOf((start?.date ?: d.date ?: state.today).toString()) }
    val parts = ratioParts(start?.takeIf { it.side == Side.SPLIT }?.quantity ?: d.quantity?.takeIf { d.side == Side.SPLIT })
    var from by rememberSaveable { mutableStateOf(parts.first) }
    var to by rememberSaveable { mutableStateOf(parts.second) }
    var qty by rememberSaveable {
        mutableStateOf(InvestFmt.input(start?.takeIf { it.side != Side.SPLIT }?.quantity ?: d.quantity?.takeIf { d.side == Side.IN || d.side == Side.OUT }))
    }
    var price by rememberSaveable { mutableStateOf(InvestFmt.input(start?.takeIf { it.side == Side.IN }?.price ?: d.price?.takeIf { d.side == Side.IN })) }
    var memo by rememberSaveable { mutableStateOf(start?.memo ?: "") }
    var deleting by remember { mutableStateOf(false) }

    val key = holdingKey(symbol, name)
    val day = runCatching { LocalDate.parse(date) }.getOrDefault(state.today)
    // What that account held of it on the day, before this record: the line every number below starts from.
    val before = remember(book, start?.id, accountId, key, day) {
        if (key.isEmpty()) null
        else Portfolio.build(book.copy(trades = book.trades.filter { it.id != start?.id }), upTo = day, useQuotes = false)
            .holdings.firstOrNull { it.accountId == accountId && it.key == key }
    }
    val held = before?.quantity ?: 0.0
    val average = before?.averagePrice ?: 0.0
    val f = from.toDecimal() ?: 0.0
    val t = to.toDecimal() ?: 0.0
    val ratio = if (f > 0 && t > 0) t / f else 0.0
    val q = qty.toDecimal() ?: 0.0
    val p = price.toDecimal() ?: 0.0
    val unit = market.unit
    val canSave = key.isNotEmpty() && when (side) {
        Side.SPLIT -> ratio > 0 && abs(ratio - 1) > 1e-9 && held > EPS
        Side.IN -> q > 0
        Side.OUT -> q > 0 && held > EPS && q <= held + EPS
        else -> false
    }

    fun save() {
        vm.saveTrade(Trade(
            id = start?.id ?: 0, accountId = accountId, date = day, minute = start?.minute, symbol = symbol.trim(), name = name.trim(),
            market = market, side = side, quantity = if (side == Side.SPLIT) ratio else q, price = if (side == Side.IN) p else 0.0,
            fxRate = if (market.foreign) start?.fxRate?.takeIf { it > 0 } ?: book.settings.usdKrw else 1.0,
            tags = start?.tags.orEmpty(), memo = memo.trim(), createdAt = start?.createdAt ?: 0,
        ), start)
        onDismiss()
    }

    InvestSheetFrame(
        title = if (start == null) "수량 바뀜 적기" else "수량 바뀜 고치기",
        onDismiss = onDismiss,
        unsaved = changedSinceOpen(side, accountId, name, symbol, market, date, from, to, qty, price, memo),
        onSave = ::save,
        saveLabel = if (start == null) "${side.label} 저장" else "고친 내용 저장",
        canSave = canSave,
        actions = {
            if (start != null) IconButton(onClick = { deleting = true }) { Icon(Icons.Outlined.Delete, "지우기", tint = MaterialTheme.colorScheme.error) }
        },
    ) {
        com.bamnat.app.ui.components.ChoiceRow(CHANGES.map { it.label }, selected = CHANGES.indexOf(side).coerceAtLeast(0), onSelect = { side = CHANGES[it] })
        Text(
            when (side) {
                Side.SPLIT -> "액면분할·병합이에요. 산 돈은 그대로, 수량과 평균단가만 바뀌어요."
                Side.IN -> "무상증자·에어드랍·다른 증권사에서 옮겨 온 주식이에요. 계좌의 현금은 그대로예요."
                else -> "다른 증권사로 옮긴 주식이에요. 산 값 그대로 빠지고 손익은 없어요."
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        AccountPicker(accounts, accountId) { accountId = it }
        // A split or shares out are of something held there; shares in may be new.
        val picks = remember(state.fullResult, side, accountId) {
            if (side == Side.IN) vm.knownHoldings().take(10) else state.fullResult.holdings.filter { it.accountId == accountId && it.held }
        }
        if (picks.isNotEmpty()) {
            FieldLabel(if (side == Side.IN) "전에 산 종목" else "갖고 있는 종목")
            ChoiceChips(picks, { it.key == key }, { it.title }, { h -> name = h.name; symbol = h.symbol; market = h.market })
        }
        // (조건 4) A split or shares out pick a holding from the chips, so the name boxes show only when there is none to pick
        // or the record is of one no longer held; shares in may be of a new one.
        if (side == Side.IN || picks.isEmpty() || (key.isNotEmpty() && picks.none { it.key == key })) {
            NameCodeFields(name, { name = it }, symbol, { symbol = it })
        }
        if (side == Side.IN && before == null) ChoiceChips(Market.entries, { it == market }, { it.label }, { market = it })
        when (side) {
            Side.SPLIT -> {
                FieldLabel("몇 ${unit}가 몇 ${unit}로 바뀌었나요")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    DecimalField("전", from, { from = it }, Modifier.weight(1f), suffix = unit)
                    Text("→", style = MaterialTheme.typography.titleMedium)
                    DecimalField("후", to, { to = it }, Modifier.weight(1f), suffix = unit)
                }
                // The usual ones in one tap: 1:2, 1:5, 1:10 액면분할 and 10:1 병합.
                ChoiceChips(listOf(2.0, 5.0, 10.0, 0.1), { abs(ratio - it) < 1e-9 }, {
                    ratioParts(it).let { (a, b) -> "${a}→${b}" }
                }, { r -> ratioParts(r).let { (a, b) -> from = a; to = b } })
            }
            Side.IN -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DecimalField("수량", qty, { qty = it }, Modifier.weight(1f), suffix = unit)
                DecimalField(if (market.foreign) "한 ${unit} 값 (달러)" else "한 ${unit} 값", price, { price = it }, Modifier.weight(1.3f),
                    suffix = if (market.foreign) "$" else "원", supporting = "무상이면 비워 두세요")
            }
            else -> {
                DecimalField("수량", qty, { qty = it }, Modifier.fillMaxWidth(), suffix = unit)
                if (held > EPS) WordsWithActions({
                    Text("그날 이 계좌에 ${InvestFmt.quantity(held)}$unit 있었어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }) { TextButton(onClick = { qty = InvestFmt.input(held) }) { Text("전부") } }
            }
        }
        // What it does, before it is saved.
        val preview: Pair<String, Boolean>? = when {
            key.isEmpty() -> null
            side != Side.IN && held <= EPS -> "그날 이 계좌에 이 종목이 없어요. 산 기록을 먼저 적어 주세요." to true
            side == Side.SPLIT && ratio > 0 && abs(ratio - 1) > 1e-9 ->
                "${InvestFmt.quantity(held)}$unit → ${InvestFmt.quantity(held * ratio)}$unit · 평균 ${InvestFmt.average(average, market)} → ${InvestFmt.average(average / ratio, market)}" to false
            side == Side.IN && q > 0 -> {
                val cost = (before?.costNative ?: 0.0) + q * p
                "${InvestFmt.quantity(held)}$unit → ${InvestFmt.quantity(held + q)}$unit · 평균 ${InvestFmt.average(average, market)} → ${InvestFmt.average(cost / (held + q), market)}" to false
            }
            side == Side.OUT && q > held + EPS -> "갖고 있던 ${InvestFmt.quantity(held)}${unit}보다 많아요." to true
            side == Side.OUT && q > 0 -> "${InvestFmt.quantity(held)}$unit → ${InvestFmt.quantity(held - q)}$unit · 평균단가 그대로, 손익 없음" to false
            else -> null
        }
        preview?.let { (words, warn) ->
            Text(words, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = if (warn) MaterialTheme.bamnat.caution else MaterialTheme.colorScheme.onSurface)
        }
        DateRow(day, null, { date = it.toString() }, null)
        LongTextField(memo, { memo = it.take(500) }, label = { Text("메모") }, placeholder = { Text("1:5 액면분할, ○○증권에서 옮겨 옴") },
            modifier = Modifier.fillMaxWidth(), minLines = 1)
    }
    if (deleting && start != null) ConfirmDialog(
        title = "이 기록을 지울까요?", message = "${start.title} ${start.side.label} 기록이 없어지고 보유 수량과 평균단가도 다시 계산돼요. 바로 되돌릴 수 있어요.",
        confirmLabel = "지우기", destructive = true, onDismiss = { deleting = false },
        onConfirm = { deleting = false; vm.deleteTrade(start); onDismiss() },
    )
}

/**
 * 2.0.0 [RVK-26] (검토 U06b-01·I06bb-01) 잔고 캡처: the screenshot is picked or taken in the same frame as a 체결 or 명세서 capture and
 * read on the phone; [onText] gets its words.
 */
@Composable
internal fun HoldingShotPicker(onDismiss: () -> Unit, onText: (String) -> Unit) {
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("잔고 화면 캡처로 채우기") },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("증권사·거래소 앱의 ‘잔고’(보유 종목) 화면을 캡처해 골라 주세요. 종목·수량·현재가를 읽어 칸을 채워요.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("글자는 휴대폰 안에서만 읽고, 어디에도 보내지 않아요. 가격을 받아 오지도 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ScanButtons(onText = onText)
            }
        },
    )
}

/**
 * [RVK-26] What a 잔고 capture said: how many prices it filled, and each quantity the book does not have, asked as what it most
 * likely is (분할·병합 when a whole ratio explains it, else a buy or shares in, a sale or shares out) with the sheet to write it.
 */
@Composable
internal fun ShotFindings(found: List<HoldingShots.Found>, go: (InvestSheet) -> Unit) {
    val filled = found.count { it.holding != null && it.price > 0 }
    val unsure = found.count { it.holding != null && it.unsure }
    val gaps = found.filter { it.holding != null && it.gap != null }
    val missing = found.filter { it.holding == null && it.row.quantity > 0 }
    NudgeCard(
        text = when {
            found.isEmpty() -> "캡처에서 종목을 찾지 못했어요"
            filled == 0 -> "장부의 종목과 맞는 줄이 없어요"
            else -> "현재가 ${filled}개를 채웠어요. 저장하면 반영돼요"
        },
        tone = if (found.isEmpty() || unsure > 0) Tone.Caution else Tone.Plain,
        detail = when {
            found.isEmpty() -> "‘잔고’·‘보유 종목’ 화면을 종목 이름이 보이게 캡처해 주세요."
            unsure > 0 -> "노란 줄 ${unsure}개는 캡처와 같은지 봐 주세요."
            else -> null
        },
    )
    gaps.forEach { f ->
        val h = f.holding!!
        val gap = f.gap!!
        val r = f.ratio
        val unit = h.market.unit
        val words = "${h.title}: 장부 ${InvestFmt.quantity(h.quantity)}$unit, 캡처 ${InvestFmt.quantity(f.row.quantity)}$unit. " + when {
            r != null && r > 1 -> "1${unit}가 ${InvestFmt.quantity(r)}${unit}로 나뉜 분할인가요?"
            r != null -> "${InvestFmt.quantity((1 / r).roundToLong().toDouble())}${unit}가 1${unit}로 합쳐진 병합인가요?"
            gap > 0 -> "${InvestFmt.quantity(gap)}${unit}가 장부에 없어요."
            else -> "장부보다 ${InvestFmt.quantity(-gap)}$unit 적어요."
        }
        WordsWithActions({ Text(words, style = MaterialTheme.typography.bodyMedium) }) {
            when {
                r != null -> TextButton(onClick = {
                    go(InvestSheet.QuantityEdit(TradeDraft(h.symbol, h.name, h.market, Side.SPLIT, r, accountId = h.accountId)))
                }) { Text("분할·병합 적기") }
                gap > 0 -> {
                    TextButton(onClick = { go(InvestSheet.TradeEdit(TradeDraft(h.symbol, h.name, h.market, Side.BUY, gap, accountId = h.accountId))) }) { Text("매수 적기") }
                    TextButton(onClick = { go(InvestSheet.QuantityEdit(TradeDraft(h.symbol, h.name, h.market, Side.IN, gap, accountId = h.accountId))) }) { Text("입고") }
                }
                else -> {
                    TextButton(onClick = {
                        go(InvestSheet.TradeEdit(TradeDraft(h.symbol, h.name, h.market, Side.SELL, -gap, f.price.takeIf { it > 0 }, accountId = h.accountId)))
                    }) { Text("매도 적기") }
                    TextButton(onClick = { go(InvestSheet.QuantityEdit(TradeDraft(h.symbol, h.name, h.market, Side.OUT, -gap, accountId = h.accountId))) }) { Text("출고") }
                }
            }
        }
    }
    missing.forEach { f ->
        val row = f.row
        WordsWithActions({
            Text("${row.name.ifBlank { row.symbol }} ${InvestFmt.quantity(row.quantity)}${row.market.unit}: 장부에 없어요.", style = MaterialTheme.typography.bodyMedium)
        }) {
            TextButton(onClick = {
                go(InvestSheet.TradeEdit(TradeDraft(row.symbol, row.name, row.market, Side.BUY, row.quantity, row.average.takeIf { it > 0 })))
            }) { Text("매수 적기") }
        }
    }
}

/**
 * 2.0.0 [RVK-28] (검토 U06b-04·I06ba-04) 종목 한 장: one holding's buys, sales, 수량 바뀜, dividends and notes in the order they
 * happened, with what was held and its 평균단가 after each, so '왜 샀더라' is read again in one place. A line opens that record.
 * Before, only a calendar day showed a note, and a holding sold out did nothing when pressed.
 */
@Composable
internal fun HoldingStorySheet(state: InvestState, key: String, accountId: Long?, onDismiss: () -> Unit, open: (InvestSheet) -> Unit) {
    val book = state.full
    val steps = remember(book, key, accountId) { HoldingStory.of(book, key, accountId, state.fullResult) }
    val h = (if (accountId == null) state.fullResult.byKey else state.fullResult.holdings.filter { it.accountId == accountId }).firstOrNull { it.key == key }
    val market = h?.market ?: steps.firstNotNullOfOrNull { it.trade?.market } ?: Market.KR_STOCK
    val hide = state.hide
    InvestSheetFrame(title = h?.title ?: steps.firstNotNullOfOrNull { it.trade?.title } ?: key, onDismiss = onDismiss, onSave = null) {
        if (h != null) {
            Text(
                if (h.held) "지금 ${InvestFmt.quantity(h.quantity)}${market.unit} · 평균 ${InvestFmt.average(h.averagePrice, market)}" else "다 팔았어요",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                listOfNotNull(
                    "실현손익 ${InvestFmt.signed(h.realizedWon.roundToLong(), hide)}",
                    h.dividendsWon.takeIf { it > 0 }?.let { "배당 ${InvestFmt.won(it, hide)}" },
                    "합계 ${InvestFmt.signed(h.totalPnlWon.roundToLong(), hide)}",
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), color = pnlColor(h.totalPnlWon),
            )
        }
        HorizontalDivider()
        if (steps.isEmpty()) Text("이 종목의 기록이 아직 없어요.", style = MaterialTheme.typography.bodyMedium)
        steps.forEachIndexed { i, s ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            StoryStep(s, market, state, open)
        }
        Text("평균단가는 수수료까지 더한 이동평균이에요. 줄을 누르면 그 기록을 고칠 수 있어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StoryStep(s: HoldingStory.Step, market: Market, state: InvestState, open: (InvestSheet) -> Unit) {
    val hide = state.hide
    val unit = market.unit
    val t = s.trade
    val c = s.cash
    val what = when {
        t != null -> when (t.side) {
            Side.BUY, Side.SELL -> "${t.side.label} ${InvestFmt.quantity(t.quantity)}$unit × ${InvestFmt.price(t.price, t.market)}"
            Side.SPLIT -> ratioParts(t.quantity).let { (a, b) -> "분할·병합 ${a}$unit → ${b}$unit" }
            Side.IN -> "입고 ${InvestFmt.quantity(t.quantity)}$unit" + if (t.price > 0) " × ${InvestFmt.price(t.price, t.market)}" else " (무상)"
            Side.OUT -> "출고 ${InvestFmt.quantity(t.quantity)}$unit"
        }
        c != null -> c.kind.label + if (c.tax > 0) " (세금 ${InvestFmt.won(c.tax, hide)})" else ""
        else -> ""
    }
    val notes = t?.let { tr -> listOfNotNull(tr.memo.takeIf { it.isNotBlank() }, tr.reason.takeIf { it.isNotBlank() }) }
        ?: listOfNotNull(c?.memo?.takeIf { it.isNotBlank() })
    // (조건 4) Tags, the mood and the rule on one quiet line under the memo, not a line each; a broken rule in the caution colour.
    val caution = MaterialTheme.bamnat.caution
    val marks = t?.let { tr ->
        val plain = tr.tags.map { "#$it" } + tr.emotions.map { it.label } + listOfNotNull(if (tr.followedRules == true) "원칙 지킴" else null)
        if (plain.isEmpty() && tr.followedRules != false) null else androidx.compose.ui.text.buildAnnotatedString {
            append(plain.joinToString(" · "))
            if (tr.followedRules == false) {
                if (plain.isNotEmpty()) append(" · ")
                withStyle(androidx.compose.ui.text.SpanStyle(color = caution)) { append("원칙 어김") }
            }
        }
    }
    Column(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) {
            if (t != null) open(InvestSheet.TradeEdit(editing = t)) else if (c != null) open(InvestSheet.CashEdit(c.kind, editing = c))
        }.padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        NameAndAmount({
            Text("${Fmt.shortDate(s.date)} · $what", style = MaterialTheme.typography.bodyLarge)
        }) {
            val made = s.realizedWon
            when {
                made != null -> Text(InvestFmt.signed(made.roundToLong(), hide), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                    color = pnlColor(made))
                t != null && t.side == Side.BUY -> Text(InvestFmt.won(abs(t.cashWon).roundToLong(), hide), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle))
                c != null -> Text("+" + InvestFmt.won(c.amount - c.tax, hide), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle),
                    color = MaterialTheme.colorScheme.primary)
            }
        }
        Text(
            if (s.quantity > EPS) "보유 ${InvestFmt.quantity(s.quantity)}$unit · 평균 ${InvestFmt.average(s.average, market)}" else "보유 없음",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        notes.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        marks?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}
