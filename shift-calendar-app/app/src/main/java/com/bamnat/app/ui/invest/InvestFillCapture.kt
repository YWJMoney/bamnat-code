package com.bamnat.app.ui.invest

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.pay.ScanButtons
import com.bamnat.core.invest.FillMath
import com.bamnat.core.invest.FillReconcile
import com.bamnat.core.invest.Trade
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.time.Today
import kotlinx.coroutines.CancellationException

/** [INV-40] One read row as the user sees and corrects it before anything is put in. */
private class FillRow(val read: FillSheetReader.Fill) {
    var qty by mutableStateOf(InvestFmt.quantity(read.quantity).replace(",", ""))
    var price by mutableStateOf(if (read.price > 0) plain(read.price) else "")
    var amount by mutableStateOf(if (read.amount > 0) plain(read.amount) else "")
    /** The user looked at a row that had something to ask, or changed it. */
    var confirmed by mutableStateOf(read.sure)
    /** [INV-42] A new row goes in (checked); a different one is fixed to the capture. */
    var add by mutableStateOf(true)
    var fix by mutableStateOf(false)
    /** [INV-44] Put in already without asking, or written already: no longer shown. */
    var done by mutableStateOf(false)

    val fill: FillSheetReader.Fill
        get() = read.copy(quantity = qty.toDoubleOrNull() ?: 0.0, price = price.toDoubleOrNull() ?: 0.0, amount = amount.toDoubleOrNull() ?: 0.0,
            unsure = if (confirmed) emptySet() else read.unsure)

    /** What still does not add up after the user's changes. */
    val disagrees: Boolean get() = fill.let { f -> f.amount > 0 && f.price > 0 && !FillMath.agrees(f.quantity, f.price, f.amount) }
    val usable: Boolean get() = fill.quantity > 0 && fill.price > 0
    /** Still yellow: not looked at, not adding up, or without a price. */
    val asks: Boolean get() = !confirmed || disagrees || !usable

    companion object {
        fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}

/**
 * [INV-40] 409 C, [INV-42..45] 1.27 AK 순번 450: 체결 내역 캡처로 채우기. The first time it shows how; then a screenshot is
 * read on the phone (결정 D68) and every row is put beside what is written already ([FillReconcile]):
 *  ① a trade written already is skipped ('이미 적은 체결 n건', one line);
 *  ② one that differs from what is written is asked as a fix, both side by side;
 *  ③ only the rows not written yet are checked, yellow where the numbers do not add up.
 * 넣기 writes them as trades at once (결정 AK-D3, 되돌리기). With '다음부터 묻지 않고 바로 넣기' the sure new rows go in
 * without this sheet; differences and yellow rows are still asked (결정 AK-D4).
 */
@Composable
internal fun FillCaptureSheet(
    vm: InvestViewModel, alerts: List<TradeAlerts.Pending>, shared: Uri?, onDismiss: () -> Unit,
    /** Text already read (a test, or a capture read elsewhere). */
    initialText: String? = null,
) {
    val guideSeen by vm.fillGuideSeen.collectAsStateWithLifecycle()
    val trust by vm.fillTrust.collectAsStateWithLifecycle()
    var guide by remember { mutableStateOf(!guideSeen && shared == null && initialText == null) }
    val rows = remember { mutableStateListOf<FillRow>() }
    var read by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(shared != null) }
    var error by remember { mutableStateOf<String?>(null) }
    // What was written when the capture was read: what is put in now does not turn into a difference with itself.
    var written by remember { mutableStateOf<List<Trade>>(emptyList()) }
    var waiting by remember { mutableStateOf(alerts) }
    /** [INV-44] How many went in without asking. */
    var putQuietly by remember { mutableStateOf(0) }
    var quietSame by remember { mutableStateOf(0) }
    var showSame by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val zone = Today.zone()
    val today = Today.date()

    fun take(text: String) {
        val fills = FillSheetReader.read(text)
        rows.clear()
        rows += fills.map(::FillRow)
        written = vm.state.value.full.trades
        waiting = alerts
        read = true
        putQuietly = 0
        quietSame = 0
        error = if (fills.isEmpty()) "체결을 찾지 못했어요. 종목·매수/매도·수량·단가가 한 화면에 보이게 다시 캡처해 주세요." else null
        val r = FillReconcile.reconcile(rows.map { it.read }, written, waiting, today, zone)
        if (fills.isNotEmpty() && vm.fillTrust.value) {
            // [INV-44] 묻지 않기: the sure new rows go in now; only differences and yellow rows stay to be asked.
            val sure = r.sureNew
            vm.applyCapture(sure.map { FillReconcile.pending(it, zone, today) }, emptyList(), skipped = r.same.size, asked = false)
            (sure + r.same).forEach { item -> item.fills.forEach { f -> rows.firstOrNull { it.read === f }?.done = true } }
            putQuietly = sure.size
            quietSame = r.same.size
            if (r.toAsk.isEmpty()) onDismiss()
        }
    }
    LaunchedEffect(initialText) { initialText?.let(::take) }
    LaunchedEffect(shared) {
        val uri = shared ?: return@LaunchedEffect
        try {
            // [READ-09] Through U's ImagePrep (upright, sized for reading), on the phone only (결정 D68).
            take(com.bamnat.app.pay.PayslipScanner.read(context, uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "캡처를 읽지 못했어요. 다른 캡처로 해 보세요."
        } finally {
            reading = false
        }
    }

    val live = rows.filter { !it.done }
    val fills = live.map { it.fill }
    val result = FillReconcile.reconcile(fills, written, waiting, today, zone)
    val itemRow: (FillReconcile.Item) -> FillRow? = { item -> fills.indexOfFirst { it === item.fill }.takeIf { it >= 0 }?.let { live[it] } }
    val same = result.same
    val different = result.different
    val new = result.new
    val putLines = new.filter { item -> itemRow(item)?.let { it.add && !it.asks } == true }.map { FillReconcile.pending(it, zone, today) } +
        different.filter { it.trade == null && itemRow(it)?.let { r -> r.fix && !r.asks } == true }.map { FillReconcile.pending(it, zone, today) }
    val fixes = different.filter { it.trade != null && itemRow(it)?.let { r -> r.fix && !r.asks } == true }.mapNotNull { FillReconcile.fixed(it, zone) }
    val count = putLines.size + fixes.size
    val yellowLeft = (new + different).any { item -> itemRow(item)?.asks == true }

    fun put(trustAfter: Boolean) {
        vm.applyCapture(putLines, fixes, skipped = same.size)
        if (trustAfter) vm.setFillTrust(true, quiet = true)
        onDismiss()
    }

    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (guide) "체결 내역 캡처로 채우기" else "캡처에서 읽은 체결") },
        confirmButton = {
            if (guide) TextButton(onClick = { guide = false; vm.fillGuideShown() }) { Text("알겠어요, 캡처 고르기") }
            else TextButton(enabled = read && count > 0, onClick = { put(false) }) { Text(if (count > 0) "넣기 ${count}건" else "넣기") }
        },
        dismissButton = {
            if (guide) TextButton(onClick = onDismiss) { Text("닫기") }
            else TextButton(onClick = { guide = true }) { Text("사용법") }
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (guide) {
                    FillGuide()
                    return@Column
                }
                if (!read) {
                    Text("증권사·거래소 앱의 ‘체결 내역’ 화면 캡처를 골라 주세요. 글자는 휴대폰 안에서만 읽고, 어디에도 보내지 않아요.",
                        style = MaterialTheme.typography.bodyMedium)
                }
                if (reading) Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.width(16.dp), strokeWidth = 2.dp)
                    Text("  휴대폰 안에서 캡처를 읽고 있어요", style = MaterialTheme.typography.bodySmall)
                }
                ScanButtons(onText = ::take)
                error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (!read || rows.isEmpty()) return@Column
                if (putQuietly > 0 || quietSame > 0) Text(
                    listOfNotNull(putQuietly.takeIf { it > 0 }?.let { "확실한 ${it}건은 묻지 않고 바로 넣었어요" },
                        quietSame.takeIf { it > 0 }?.let { "이미 있음 ${it}건" }).joinToString(" · ") + ". 아래만 확인해 주세요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                // ① One line for what is written already.
                if (same.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("이미 적은 체결 ${same.size}건은 건너뛰어요", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = { showSame = !showSame }) { Text(if (showSame) "접기" else "보기") }
                }
                if (showSame) same.forEach { item ->
                    val f = item.fill
                    Text("· ${f.side.label} ${f.title} ${InvestFmt.quantity(item.fills.sumOf { it.quantity })}${f.market.unit}" +
                        (item.trade?.let { " (${it.date.monthValue}/${it.date.dayOfMonth} 기록)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // ② Differences: asked, never changed quietly.
                if (different.isNotEmpty()) {
                    HorizontalDivider()
                    Text("기록과 다른 체결 ${different.size}건 · 고칠까요?", style = MaterialTheme.typography.titleSmall)
                    different.forEach { item -> itemRow(item)?.let { DiffRowView(item, it, zone) } }
                }
                // ③ New rows only.
                if (new.isNotEmpty()) {
                    HorizontalDivider()
                    Text("아직 안 적은 체결 ${new.size}건", style = MaterialTheme.typography.titleSmall)
                    Text("넣을 것만 체크해 주세요. 노란 칸은 숫자가 서로 안 맞거나 흐린 곳이에요. 캡처와 같은지 보고 고치거나 ‘맞아요’를 눌러 주세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    new.forEachIndexed { i, item ->
                        if (i > 0) HorizontalDivider()
                        itemRow(item)?.let { NewRowView(item, it, zone) }
                    }
                }
                if (yellowLeft) Text("노란 칸은 확인해야 넣을 수 있어요.", style = MaterialTheme.typography.bodySmall, color = warning())
                if (new.isEmpty() && different.isEmpty()) Text("새로 넣을 체결이 없어요.", style = MaterialTheme.typography.bodyMedium)
                HorizontalDivider()
                // [INV-44] Once the user trusts it: the next captures go in without this sheet.
                if (!trust) {
                    OutlinedButton(enabled = count > 0, onClick = { put(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text("넣고, 다음부터 묻지 않고 바로 넣기")
                    }
                    Text("믿음이 생기면 눌러 주세요. 그 뒤에도 기록과 다르거나 흐린 값은 늘 물어요. 투자 탭 ⋮에서 다시 묻게 할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("묻지 않고 바로 넣기가 켜져 있어요", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.setFillTrust(false) }) { Text("다시 묻기") }
                }
            }
        },
    )
}

@Composable
private fun warning() = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning

@Composable
private fun sideColor(side: Side) =
    if (side == Side.BUY) com.bamnat.app.ui.theme.LocalCalendarColors.current.sunday else com.bamnat.app.ui.theme.LocalCalendarColors.current.saturday

@Composable
private fun RowTitle(f: FillSheetReader.Fill, ask: Boolean, lead: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        lead?.invoke()
        Text(f.side.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge, color = sideColor(f.side))
        Text(f.title.ifBlank { "종목 이름 못 읽음" }, style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).then(if (ask && FillSheetReader.Field.NAME in f.unsure) Modifier.background(warning().copy(alpha = 0.2f)) else Modifier))
        listOfNotNull(f.date?.let { "${it.monthValue}/${it.dayOfMonth}" }, f.time?.let { "%02d:%02d".format(it.hour, it.minute) }).takeIf { it.isNotEmpty() }
            ?.let { Text(it.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
private fun AskFields(r: FillRow) {
    val f = r.read
    val ask = !r.confirmed
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        AskField("수량", r.qty, ask && FillSheetReader.Field.QUANTITY in f.unsure, Modifier.weight(1f)) { r.qty = it; r.confirmed = true }
        AskField("단가", r.price, ask && FillSheetReader.Field.PRICE in f.unsure || r.disagrees || r.price.isBlank(), Modifier.weight(1.3f)) { r.price = it; r.confirmed = true }
        AskField("금액", r.amount, ask && FillSheetReader.Field.AMOUNT in f.unsure || r.disagrees, Modifier.weight(1.4f)) { r.amount = it; r.confirmed = true }
    }
    if (f.priceFromAmount) Text("단가는 금액 ÷ 수량으로 구했어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (r.disagrees) Text("수량 × 단가가 금액과 안 맞아요. 캡처를 보고 고쳐 주세요.", style = MaterialTheme.typography.bodySmall, color = warning())
    if (ask && !r.disagrees) TextButton(onClick = { r.confirmed = true },
        modifier = Modifier.semantics { contentDescription = "${f.title} 읽은 값이 맞아요" }) { Text("캡처와 같아요, 맞아요") }
}

/** ③ A row not written yet: a check box to put it in (off while yellow), and the read numbers. */
@Composable
private fun NewRowView(item: FillReconcile.Item, r: FillRow, zone: java.time.ZoneId) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RowTitle(r.read, !r.confirmed) {
            Checkbox(checked = r.add && !r.asks, enabled = !r.asks, onCheckedChange = { r.add = it },
                modifier = Modifier.semantics { contentDescription = "${r.read.title} 넣기" })
        }
        AskFields(r)
        item.alert?.let { a ->
            val t = java.time.Instant.ofEpochMilli(a.at).atZone(zone)
            Text("기다리던 ‘${a.title} ${InvestFmt.quantity(a.quantity)}${a.market.unit}’ 알림(${"%02d:%02d".format(t.hour, t.minute)})이에요 · 넣으면 대기함에서 빠져요" +
                if (item.fills.size > 1) " · ${item.fills.size}줄 평균" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

private data class Side4(val label: String, val q: Double, val p: Double, val minute: Int?)

/** ② What is written and what the capture says, side by side, the parts that differ in bold; fixed only when ticked. */
@Composable
private fun DiffRowView(item: FillReconcile.Item, r: FillRow, zone: java.time.ZoneId) {
    val f = r.fill
    val t = item.trade
    val a = item.alert
    val was = when {
        t != null -> Side4("기록", t.quantity, t.price, t.minute)
        a != null -> Side4("알림", a.quantity, a.price, FillReconcile.minuteOf(a, zone))
        else -> return
    }
    val market = t?.market ?: a?.market ?: f.market
    val capMinute = f.time?.let { it.hour * 60 + it.minute } ?: was.minute
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        RowTitle(r.read, !r.confirmed)
        DiffLine(was.label, was.q, was.p, was.minute, market, emptySet())
        DiffLine("캡처", f.quantity, f.price, capMinute, market, item.parts)
        Text("다른 곳: " + item.parts.joinToString("·") { it.label }, style = MaterialTheme.typography.bodySmall, color = warning())
        if (r.asks) AskFields(r)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = r.fix && !r.asks, enabled = !r.asks, onCheckedChange = { r.fix = it },
                modifier = Modifier.semantics { contentDescription = "${r.read.title} 캡처대로 고치기" })
            Text(if (t != null) "캡처대로 고치기 (안 고르면 기록 그대로)" else "캡처 단가로 넣기 (안 고르면 알림 그대로)", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun DiffLine(who: String, q: Double, p: Double, minute: Int?, market: com.bamnat.core.invest.Market, bold: Set<FillReconcile.Part>) {
    fun w(part: FillReconcile.Part) = if (part in bold) FontWeight.Bold else FontWeight.Normal
    val text = androidx.compose.ui.text.buildAnnotatedString {
        fun part(s: String, part: FillReconcile.Part) {
            pushStyle(androidx.compose.ui.text.SpanStyle(fontWeight = w(part)))
            append(s)
            pop()
        }
        append("$who  ")
        part("${InvestFmt.quantity(q)}${market.unit}", FillReconcile.Part.QUANTITY)
        append(" × ")
        part(if (p > 0) InvestFmt.price(p, market) else "단가 없음", FillReconcile.Part.PRICE)
        append(" · ")
        part(minute?.let { "%02d:%02d".format(it / 60, it % 60) } ?: "시각 없음", FillReconcile.Part.TIME)
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun AskField(label: String, text: String, ask: Boolean, modifier: Modifier, onChange: (String) -> Unit) {
    Box(modifier.then(if (ask) Modifier.background(warning().copy(alpha = 0.18f), RoundedCornerShape(6.dp)) else Modifier)) {
        DecimalField(if (ask) "$label 확인" else label, text, onChange)
    }
}

/**
 * [INV-40] The once-only guide: what screen to capture and how, with five example screens drawn here (표 한 줄·카드·
 * 머리글 표·해외 달러·코인). The examples are drawings of the layouts, not any company's real screen.
 */
@Composable
internal fun FillGuide() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("증권사·거래소 앱의 체결 내역을 캡처하면 아직 안 적은 체결을 넣고, 단가 없는 알림도 채워 드려요.", style = MaterialTheme.typography.bodyMedium)
        Step(1, "증권사 앱(키움·미래에셋·삼성·한국투자·토스 등)이나 거래소 앱(업비트·빗썸 등)에서 ‘체결 내역’을 열어요. 앱에 따라 ‘거래내역’·‘주문내역’이라고 불러요.")
        Step(2, "종목 이름, 매수·매도, 수량, 단가(또는 체결 금액)가 한 화면에 보이게 캡처해요. 여러 건이 한 장에 있어도 돼요.")
        Step(3, "여기서 ‘캡처 고르기’를 누르거나, 갤러리에서 캡처를 밤낮으로 ‘공유’해요(투자 달력을 보고 있을 때).")
        Step(4, "이미 적은 체결은 건너뛰고, 적은 것과 다르면 나란히 보여 고칠지 묻고, 아직 안 적은 것만 체크해 넣어요. 수량 × 단가 = 금액이 안 맞거나 흐린 칸은 노랗게 물어요.")
        Step(5, "믿음이 생기면 ‘다음부터 묻지 않고 바로 넣기’를 눌러요. 그 뒤에도 기록과 다르거나 흐린 값은 늘 물어요.")
        Text("이런 화면이면 돼요", style = MaterialTheme.typography.titleSmall)
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Example("표 한 줄에 한 건", listOf("체결내역", "삼성전자 매수 10 71,500 715,000", "카카오 매도 5 41,250 206,250"))
            Example("카드 한 장에 한 건", listOf("SK하이닉스", "매수 체결", "체결수량 3주", "체결단가 182,000원", "체결금액 546,000원"))
            Example("머리글이 있는 표", listOf("종목명 구분 체결가 수량 금액", "NAVER 매수 176,300 4 705,200"))
            Example("해외 주식(달러)", listOf("애플 AAPL 매수 3주", "$231.50  $694.50"))
            Example("코인 거래내역", listOf("비트코인 BTC/KRW 매수", "체결수량 0.00123 BTC", "체결가격 98,500,000 KRW"))
        }
        Text("계좌번호·이름은 읽지 않고 남기지도 않아요. 캡처 글자는 휴대폰 안에서만 읽어요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("$n", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun Example(title: String, lines: List<String>) {
    Column(
        Modifier.width(188.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(title, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
        lines.forEachIndexed { i, l ->
            // The parts 밤낮 reads are marked, the way the screen should show them.
            val mark = i > 0 || lines.size == 2
            Text(l, style = MaterialTheme.typography.bodySmall, maxLines = 1,
                modifier = if (mark) Modifier.background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f), RoundedCornerShape(4.dp)).padding(horizontal = 4.dp) else Modifier)
        }
    }
}

