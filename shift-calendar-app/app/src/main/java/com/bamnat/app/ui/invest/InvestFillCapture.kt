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
import com.bamnat.core.invest.FillMatcher
import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeAlerts
import com.bamnat.core.time.Today
import kotlinx.coroutines.CancellationException

/** [INV-40] One read row as the user sees and corrects it before anything is filled. */
private class FillRow(val read: FillSheetReader.Fill) {
    var qty by mutableStateOf(InvestFmt.quantity(read.quantity).replace(",", ""))
    var price by mutableStateOf(if (read.price > 0) plain(read.price) else "")
    var amount by mutableStateOf(if (read.amount > 0) plain(read.amount) else "")
    /** The user looked at a row that had something to ask, or changed it. */
    var confirmed by mutableStateOf(read.sure)
    var add by mutableStateOf(false)

    val fill: FillSheetReader.Fill
        get() = read.copy(quantity = qty.toDoubleOrNull() ?: 0.0, price = price.toDoubleOrNull() ?: 0.0, amount = amount.toDoubleOrNull() ?: 0.0,
            unsure = if (confirmed) emptySet() else read.unsure)

    /** What still does not add up after the user's changes. */
    val disagrees: Boolean get() = fill.let { f -> f.amount > 0 && f.price > 0 && !FillMath.agrees(f.quantity, f.price, f.amount) }
    val usable: Boolean get() = fill.quantity > 0 && fill.price > 0

    companion object {
        fun plain(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
    }
}

/**
 * [INV-40] 409 C: 체결 내역 캡처로 단가 채우기. The first time it shows how (어느 화면을 어떻게 캡처하는지, with example
 * screens); then a screenshot is read on the phone, every row is checked (수량 × 단가 ≈ 금액), what is not sure is
 * yellow and has to be looked at, and each row is matched to a waiting line (종목·방향·수량·시각). Nothing is written:
 * the lines get their price and still wait for 적기 (결정 D37).
 */
@Composable
internal fun FillCaptureSheet(
    vm: InvestViewModel, alerts: List<TradeAlerts.Pending>, shared: Uri?, onDismiss: () -> Unit,
    /** Text already read (a test, or a capture read elsewhere). */
    initialText: String? = null,
) {
    val guideSeen by vm.fillGuideSeen.collectAsStateWithLifecycle()
    var guide by remember { mutableStateOf(!guideSeen && shared == null && initialText == null) }
    val rows = remember { mutableStateListOf<FillRow>() }
    var read by remember { mutableStateOf(false) }
    var reading by remember { mutableStateOf(shared != null) }
    var error by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val zone = Today.zone()

    fun take(text: String) {
        val fills = FillSheetReader.read(text)
        rows.clear()
        rows += fills.map(::FillRow)
        read = true
        error = if (fills.isEmpty()) "체결을 찾지 못했어요. 종목·매수/매도·수량·단가가 한 화면에 보이게 다시 캡처해 주세요." else null
    }
    LaunchedEffect(initialText) { initialText?.let(::take) }
    LaunchedEffect(shared) {
        val uri = shared ?: return@LaunchedEffect
        try {
            take(com.bamnat.app.pay.PayslipScanner.read(context, uri))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            error = "캡처를 읽지 못했어요. 다른 캡처로 해 보세요."
        } finally {
            reading = false
        }
    }

    val matched = FillMatcher.match(alerts, rows.filter { it.usable }.map { it.fill }, zone)
    val matchOf: (FillRow) -> FillMatcher.Match? = { r -> matched.matches.firstOrNull { m -> m.fills.any { it == r.fill } } }
    val pendingAsk = rows.any { !it.confirmed || it.disagrees }
    val canFill = read && rows.isNotEmpty() && !pendingAsk && (matched.matches.isNotEmpty() || rows.any { it.add && matchOf(it) == null })

    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text(if (guide) "체결 내역 캡처로 단가 채우기" else "캡처에서 읽은 체결") },
        confirmButton = {
            if (guide) TextButton(onClick = { guide = false; vm.fillGuideShown() }) { Text("알겠어요, 캡처 고르기") }
            else TextButton(enabled = canFill, onClick = {
                val extra = rows.filter { it.add && matchOf(it) == null && it.usable }.map { r ->
                    val f = r.fill
                    val at = f.date?.let { d -> d.atTime(f.time ?: java.time.LocalTime.NOON).atZone(zone).toInstant().toEpochMilli() } ?: Today.millis()
                    TradeAlerts.Pending(at, "체결 내역 캡처", f.side, f.symbol, f.name, f.quantity, f.price, f.market, f.amount, filledBy = TradeAlerts.BY_CAPTURE)
                }
                vm.fillFromCapture(matched.matches, extra)
                onDismiss()
            }) { Text("단가 채우기") }
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
                if (read && rows.isNotEmpty()) {
                    Text("${rows.size}건을 읽었어요. 노란 칸은 숫자가 서로 안 맞거나 확실하지 않은 곳이에요. 캡처와 같은지 보고 고치거나 ‘맞아요’를 눌러 주세요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    rows.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider()
                        FillRowView(r, matchOf(r), zone)
                    }
                    if (pendingAsk) Text("노란 칸을 모두 확인해야 채울 수 있어요.", style = MaterialTheme.typography.bodySmall, color = warning())
                }
            }
        },
    )
}

@Composable
private fun warning() = com.bamnat.app.ui.theme.LocalCalendarColors.current.warning

@Composable
private fun FillRowView(r: FillRow, match: FillMatcher.Match?, zone: java.time.ZoneId) {
    val f = r.read
    val ask = !r.confirmed
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(f.side.label, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge,
                color = if (f.side == Side.BUY) com.bamnat.app.ui.theme.LocalCalendarColors.current.sunday else com.bamnat.app.ui.theme.LocalCalendarColors.current.saturday)
            Text(f.title.ifBlank { "종목 이름 못 읽음" }, style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f).then(if (ask && FillSheetReader.Field.NAME in f.unsure) Modifier.background(warning().copy(alpha = 0.2f)) else Modifier))
            listOfNotNull(f.date?.let { "${it.monthValue}/${it.dayOfMonth}" }, f.time?.let { "%02d:%02d".format(it.hour, it.minute) }).takeIf { it.isNotEmpty() }
                ?.let { Text(it.joinToString(" "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            AskField("수량", r.qty, ask && FillSheetReader.Field.QUANTITY in f.unsure, Modifier.weight(1f)) { r.qty = it; r.confirmed = true }
            AskField("단가", r.price, ask && FillSheetReader.Field.PRICE in f.unsure || r.disagrees, Modifier.weight(1.3f)) { r.price = it; r.confirmed = true }
            AskField("금액", r.amount, ask && FillSheetReader.Field.AMOUNT in f.unsure || r.disagrees, Modifier.weight(1.4f)) { r.amount = it; r.confirmed = true }
        }
        if (f.priceFromAmount) Text("단가는 금액 ÷ 수량으로 구했어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (r.disagrees) Text("수량 × 단가가 금액과 안 맞아요. 캡처를 보고 고쳐 주세요.", style = MaterialTheme.typography.bodySmall, color = warning())
        if (ask && !r.disagrees) TextButton(onClick = { r.confirmed = true }, modifier = Modifier.semantics { contentDescription = "${f.title} 읽은 값이 맞아요" }) { Text("캡처와 같아요, 맞아요") }
        if (match != null) {
            val t = java.time.Instant.ofEpochMilli(match.alert.at).atZone(zone)
            Text("→ 기다리는 ‘${match.alert.title} ${InvestFmt.quantity(match.alert.quantity)}${match.alert.market.unit}’ (${"%02d:%02d".format(t.hour, t.minute)} 알림)에 단가 ${InvestFmt.price(match.price, match.alert.market)}" +
                if (match.fills.size > 1) " · ${match.fills.size}줄 평균" else "",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        } else if (r.usable) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = r.add, onCheckedChange = { r.add = it })
                Text("알림 온 거래에 없어요. 기다리는 거래로 넣기", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
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
        Text("알림에 단가가 없을 때, 증권사·거래소 앱의 체결 내역을 캡처하면 단가를 채워 드려요.", style = MaterialTheme.typography.bodyMedium)
        Step(1, "증권사 앱(키움·미래에셋·삼성·한국투자·토스 등)이나 거래소 앱(업비트·빗썸 등)에서 ‘체결 내역’을 열어요. 앱에 따라 ‘거래내역’·‘주문내역’이라고 불러요.")
        Step(2, "종목 이름, 매수·매도, 수량, 단가(또는 체결 금액)가 한 화면에 보이게 캡처해요. 여러 건이 한 장에 있어도 돼요.")
        Step(3, "여기서 ‘캡처 고르기’를 누르거나, 갤러리에서 캡처를 밤낮으로 ‘공유’해요(투자 달력을 보고 있을 때).")
        Step(4, "읽은 숫자는 수량 × 단가 = 금액으로 맞춰 보고, 안 맞거나 흐린 칸은 노랗게 물어요. 확인한 단가만 채우고, 적기는 직접 눌러요.")
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

