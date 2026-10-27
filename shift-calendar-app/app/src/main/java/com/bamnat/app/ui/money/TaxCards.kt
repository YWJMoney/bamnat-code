package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.tax.TakeHomeView
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.FormSheet
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.invest.DecimalField
import com.bamnat.app.ui.pay.ScanButtons
import com.bamnat.core.pay.PayPhotos
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.tax.BusinessTax
import com.bamnat.core.tax.IncomeKind
import com.bamnat.core.tax.PayObservation
import com.bamnat.core.tax.TaxLearner
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToLong

/** [TAX-02][TAX-05] Which half of 세금·실수령 a calendar's kind of work uses; null hides the card (투자 has its own tax). */
internal fun taxSide(style: WorkStyle?): TaxSide? = when {
    style == null || style.usesPayEngine -> TaxSide.PAY
    style.usesJobs || style.usesSales -> TaxSide.BUSINESS
    else -> null
}

internal enum class TaxSide { PAY, BUSINESS }

/**
 * [TAX-02][TAX-05] 돈 › 요약's 세금·실수령 card (프로, 결정 W-D4): for pay, this month's 세전 beside the 세후 learned from
 * real pay (사용자 U9-다); for 현장·자영업, this year's tax and the next payment (U9-라).
 */
@Composable
internal fun TaxCard(
    side: TaxSide,
    view: TakeHomeView?,
    business: BusinessTax.Year?,
    gate: Gate,
    onOpen: () -> Unit,
) {
    SectionCard(
        title = "세금·실수령",
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (gate.locked(Feature.TAX_FORECAST)) ProPill()
                TextButton(onClick = { if (gate.allows(Feature.TAX_FORECAST)) onOpen() else com.bamnat.app.ui.pro.openPro() }) {
                    Text(if (side == TaxSide.PAY) "기록·자세히" else "자세히·설정")
                }
            }
        },
    ) {
        com.bamnat.app.ui.pro.ProLock(Feature.TAX_FORECAST, gate) {
            Column {
                when (side) {
                    TaxSide.PAY -> PayTaxLines(view)
                    TaxSide.BUSINESS -> BusinessTaxLines(business)
                }
            }
        }
    }
}

@Composable
private fun PayTaxLines(view: TakeHomeView?) {
    val f = view?.forecast
    if (view == null || f == null) {
        Muted("급여 계산을 정하면 이달 세전과 세후(실수령)를 함께 보여 드려요. 지난 월급을 적으면 회사가 실제로 떼는 만큼 맞춰요.")
        return
    }
    TaxLine("${view.month.monthValue}월 세전 예상", Fmt.won(f.gross))
    TaxLine("세후 예상 (실수령)", Fmt.won(f.net), strong = true)
    TaxLine("  떼는 돈 (세금·4대 보험)", Fmt.won(f.deducted), muted = true)
    view.lastActual?.let { a ->
        TaxLine("${a.payMonth.monthValue}월 실제 실수령 (${a.source.label})", Fmt.won(a.net))
    }
    Muted(view.fit.words + if (view.fit.guessedMode) " · 기록을 보고 떼는 방식을 짐작했어요" else "")
}

@Composable
private fun BusinessTaxLines(y: BusinessTax.Year?) {
    if (y == null) {
        Muted("매출·일감·경비를 적으면 올해 낼 세금과 내는 날을 어림해 드려요.")
        return
    }
    TaxLine("${y.year}년 낼 세금 예상", Fmt.won(y.total), strong = true)
    TaxLine("  종합소득세 (${y.best.method.label})", Fmt.won(y.incomeTaxDue.coerceAtLeast(0)), muted = true)
    TaxLine("  부가가치세", if (y.vatExempt) "면제" else Fmt.won(y.vat.coerceAtLeast(0)), muted = true)
    TaxLine("매달 모아 둘 돈", Fmt.won(y.perMonth))
    y.dues.firstOrNull { !it.date.isBefore(y.until) }?.let { d ->
        TaxLine("다음 납부 ${Fmt.monthDay(d.date)} ${d.name}", d.amount?.let(Fmt::won) ?: "")
    }
}

/**
 * [TAX-03][TAX-04] 실수령 기록: this year so far, every month's real pay (typed, read from a payslip, or a deposit the
 * ledger saw), adding a past month, and taking back a deposit that was not pay.
 */
@Composable
internal fun TakeHomeSheet(
    view: TakeHomeView,
    onAdd: (PayObservation) -> Unit,
    onDelete: (PayObservation) -> Unit,
    onNotPay: (PayObservation) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate,
) {
    var adding by remember { mutableStateOf(false) }
    FormSheet(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        title = { Text("세금·실수령") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                view.forecast?.let { f ->
                    TaxLine("${view.month.monthValue}월 세전 예상", Fmt.won(f.gross))
                    TaxLine("세후 예상 (실수령)", Fmt.won(f.net), strong = true)
                    if (f.tableNet != f.net) TaxLine("  간이세액표대로라면", Fmt.won(f.tableNet), muted = true)
                }
                Muted(view.fit.words)
                val y = view.year
                if (y.months > 0) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    Text("${y.year}년 지금까지 (${y.months}달)", style = MaterialTheme.typography.titleSmall)
                    TaxLine("세전", Fmt.won(y.gross))
                    TaxLine("실수령", Fmt.won(y.net))
                    TaxLine("  세금 (소득세·지방소득세)", Fmt.won(y.tax), muted = true)
                    TaxLine("  4대 보험", Fmt.won(y.insurance), muted = true)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("실수령 기록 ${view.records.size}건", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { adding = true }) { Text("지난 월급 적기") }
                }
                if (view.records.isEmpty()) {
                    Muted("월급이 통장에 들어오면 결제 알림으로 저절로 남아요. 지난 명세서를 사진으로 읽거나 직접 적어도 돼요.")
                }
                view.records.forEach { o -> RecordRow(o, onDelete, onNotPay) }
                Muted("들어온 월급은 수입에 한 번만 셉니다: 예상 급여가 있으면 그 달은 예상으로 세고, 들어온 돈은 관측으로 남겨 세금 예측에만 써요. 기록은 휴대폰 밖으로 나가지 않아요.")
            }
        },
    )
    if (adding) PayRecordDialog(YearMonth.from(today).minusMonths(1), onSave = { onAdd(it); adding = false }, onDismiss = { adding = false })
}

@Composable
private fun RecordRow(o: PayObservation, onDelete: (PayObservation) -> Unit, onNotPay: (PayObservation) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("${Fmt.month(o.payMonth)} · 실수령 ${Fmt.won(o.net)}", style = MaterialTheme.typography.bodyLarge)
            Text(listOfNotNull(o.gross?.let { "세전 ${Fmt.won(it)}" }, o.kind.label.takeIf { o.kind != IncomeKind.SALARY }, o.source.label,
                o.note.takeIf { it.isNotBlank() }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (o.source == PayObservation.Source.DEPOSIT) {
            TextButton(onClick = { onNotPay(o) }) { Text("월급 아니에요") }
        } else {
            IconButton(onClick = { onDelete(o) }, modifier = Modifier.semantics { contentDescription = "${Fmt.month(o.payMonth)} 기록 지우기" }) {
                Icon(Icons.Filled.Delete, null)
            }
        }
    }
}

/** [TAX-04] One past month's pay, typed or read from a payslip photo on the phone (결정 D68). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PayRecordDialog(start: YearMonth, onSave: (PayObservation) -> Unit, onDismiss: () -> Unit) {
    var kind by remember { mutableStateOf(IncomeKind.SALARY) }
    var month by remember { mutableStateOf(start) }
    var gross by remember { mutableStateOf("") }
    var net by remember { mutableStateOf("") }
    var hours by remember { mutableStateOf("") }
    var rate by remember { mutableStateOf("") }
    var days by remember { mutableStateOf("") }
    var source by remember { mutableStateOf(PayObservation.Source.TYPED) }
    var read by remember { mutableStateOf<String?>(null) }
    val texts = remember { mutableStateListOf<String>() }
    val g: Long? = when (kind) {
        IncomeKind.HOURLY -> hours.toDoubleOrNull()?.let { h -> rate.toLongOrNull()?.let { (h * it).roundToLong() } } ?: gross.toLongOrNull()
        IncomeKind.DAILY -> days.toIntOrNull()?.let { d -> rate.toLongOrNull()?.let { d * it } } ?: gross.toLongOrNull()
        else -> gross.toLongOrNull()
    }
    // What the law holds back, shown until the user types what really came.
    val lawNet: Long? = when (kind) {
        IncomeKind.FREELANCE -> g?.let(TaxLearner::freelanceNet)
        IncomeKind.DAILY -> days.toIntOrNull()?.let { d -> rate.toLongOrNull()?.let { r -> d * (r - TaxLearner.dailyWithheld(r)) } }
        else -> null
    }
    val n = net.toLongOrNull() ?: lawNet
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("지난 월급 적기") },
        confirmButton = {
            TextButton(enabled = n != null && n > 0, onClick = {
                onSave(PayObservation(payMonth = month, net = n!!, gross = g, date = month.atEndOfMonth(), source = source, kind = kind,
                    hours = hours.toDoubleOrNull().takeIf { kind == IncomeKind.HOURLY }, days = days.toIntOrNull().takeIf { kind == IncomeKind.DAILY }))
            }) { Text("저장") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IncomeKind.entries.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
                }
                Muted(kind.hint)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { month = month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달") }
                    Text("${Fmt.month(month)}분", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = { month = month.plusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
                }
                // [D68] The photo is read on the phone (U의 ImagePrep를 지남); nothing is sent anywhere.
                // [READ-08] A payslip and the company's 계산 방법 can come as two photos in either order: [PayPhotos] takes
                // the one that reads as a payslip, and its 공제·실수령 (worked out when the photo cut the bottom line) fill this month.
                Text("명세서 사진으로 채우기 (회사 계산 방법 사진도 같이 넣어도 돼요)", style = MaterialTheme.typography.labelLarge)
                ScanButtons(onText = { text ->
                    texts += text
                    val photos = PayPhotos.read(texts.toList())
                    val r = photos.slip ?: PayslipReader.read(text)
                    val g0 = r.gross
                    val n0 = r.net ?: photos.deductionRate?.let { cut -> g0?.let { (it * (1 - cut)).roundToLong() } }
                    g0?.let { gross = it.toString() }
                    n0?.let { net = it.toString() }
                    r.month?.let { month = it }
                    source = PayObservation.Source.PAYSLIP
                    read = if (g0 == null && n0 == null) "세전·실수령을 찾지 못했어요. 명세서 사진을 한 장 더 넣거나 직접 적어 주세요."
                    else listOfNotNull("명세서에서 읽었어요", r.deducted?.let { "공제 ${Fmt.won(it)}" }).joinToString(" · ") +
                        ". 숫자가 맞는지 보고 다르면 고쳐 주세요."
                })
                read?.let { Muted(it) }
                // [PHOTO-09] 1.29
                if (read != null) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.TAX)
                when (kind) {
                    IncomeKind.HOURLY -> {
                        DecimalField("일한 시간", hours, { hours = it }, suffix = "시간")
                        AmountField("시급", rate, { rate = it })
                    }
                    IncomeKind.DAILY -> {
                        DecimalField("일한 날", days, { days = it.substringBefore('.') }, suffix = "일")
                        AmountField("일당", rate, { rate = it })
                    }
                    else -> AmountField(if (kind == IncomeKind.FREELANCE) "받기로 한 돈 (세전)" else "세전 (지급 총액)", gross, { gross = it })
                }
                if (kind == IncomeKind.HOURLY || kind == IncomeKind.DAILY) g?.let { Muted("세전 ${Fmt.won(it)}") }
                AmountField("실수령 (통장에 들어온 돈)", net, { net = it })
                lawNet?.takeIf { net.isEmpty() }?.let { Muted("비워 두면 법대로 뗀 ${Fmt.won(it)}으로 적어요") }
            }
        },
    )
}

/** [TAX-05][TAX-06] 사업자 세금: the answers it needs (from 홈택스, 결정 W-D3) and the year's tax worked out from them. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BusinessTaxSheet(
    start: BusinessTax.Profile,
    yearOf: (BusinessTax.Profile) -> BusinessTax.Year,
    onSave: (BusinessTax.Profile) -> Unit,
    onDismiss: () -> Unit,
) {
    var p by remember { mutableStateOf(start) }
    var simple by remember { mutableStateOf(start.simpleRate?.toString().orEmpty()) }
    var standard by remember { mutableStateOf(start.standardRate?.toString().orEmpty()) }
    var lastIncome by remember { mutableStateOf(start.lastYearIncome?.toString().orEmpty()) }
    var lastTax by remember { mutableStateOf(start.lastYearTax?.toString().orEmpty()) }
    val draft = p.copy(
        simpleRate = simple.toDoubleOrNull()?.takeIf { it in 0.0..100.0 }, standardRate = standard.toDoubleOrNull()?.takeIf { it in 0.0..100.0 },
        lastYearIncome = lastIncome.toLongOrNull(), lastYearTax = lastTax.toLongOrNull(),
    )
    val y = remember(draft) { yearOf(draft) }
    FormSheet(
        onDismissRequest = onDismiss,
        title = { Text("사업자 세금 예상") },
        confirmButton = { TextButton(onClick = { onSave(draft) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TaxLine("${y.year}년 낼 세금 예상", Fmt.won(y.total), strong = true)
                Muted("${Fmt.monthDay(y.until)}까지 적은 매출 ${Fmt.won(y.sales)} · 경비 ${Fmt.won(y.costs)} (증빙 ${Fmt.won(y.proved)})를 한 해로 늘려 계산했어요.")
                y.options.forEach { o ->
                    TaxLine((if (o == y.best) "✓ " else "") + o.method.label, if (o.allowed) Fmt.won(o.tax) else "못 써요", muted = o != y.best)
                    if (o.why.isNotBlank()) Muted("  " + o.why)
                }
                if (y.withheld > 0) Muted("  일감에서 지금까지 뗀 3.3% ${Fmt.won(y.withheld)}는 한 해로 늘려 빼고 계산했어요")
                // 1.25 통합 직접 시험: 3.3%로 미리 뗀 돈이 세금보다 많으면 "낼 돈 -530,427원"으로 보였어요 → 돌려받을 돈.
                if (y.incomeTaxDue < 0) {
                    TaxLine("종합소득세 돌려받을 돈 (지방세 포함)", Fmt.won(-y.incomeTaxDue))
                    Muted("  미리 뗀 3.3%가 올해 세금보다 많아요. 5월 확정신고를 하면 돌려받아요")
                } else TaxLine("종합소득세 낼 돈 (지방세 포함)", Fmt.won(y.incomeTaxDue))
                TaxLine("부가가치세", if (y.vatExempt) "면제 (연 4,800만 원 미만)" else Fmt.won(y.vat))
                TaxLine("매달 모아 둘 돈", Fmt.won(y.perMonth))
                Text("내는 날", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
                y.dues.forEach { d ->
                    TaxLine("${Fmt.shortFullDate(d.date)} ${d.name}", d.amount?.let(Fmt::won) ?: "")
                    if (d.note.isNotBlank()) Muted("  " + d.note)
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("과세 유형", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BusinessTax.VatType.entries.forEach { v -> FilterChip(selected = p.vat == v, onClick = { p = p.copy(vat = v) }, label = { Text(v.label) }) }
                }
                Text("업종", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BusinessTax.Industry.entries.forEach { i -> FilterChip(selected = p.industry == i, onClick = { p = p.copy(industry = i) }, label = { Text(i.label) }) }
                }
                Muted("경비율은 홈택스 › 조회/발급 › 기타조회 › 기준·단순경비율(업종코드)에서 내 업종 코드로 찾아 적어요. 비워 두면 그 방법은 빼고 계산해요.")
                DecimalField("단순경비율", simple, { simple = it }, suffix = "%")
                DecimalField("기준경비율", standard, { standard = it }, suffix = "%")
                AmountField("작년 수입금액 (모르면 비워요)", lastIncome, { lastIncome = it })
                AmountField("작년 종합소득세 (11월 중간예납용)", lastTax, { lastTax = it })
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("기본공제 사람 수 (나 포함)", modifier = Modifier.weight(1f))
                    TextButton(onClick = { p = p.copy(family = (p.family - 1).coerceAtLeast(1)) }) { Text("−") }
                    Text("${p.family}명")
                    TextButton(onClick = { p = p.copy(family = (p.family + 1).coerceAtMost(10)) }) { Text("+") }
                }
                SwitchRow("올해 처음 시작한 사업", p.newBusiness) { p = p.copy(newBusiness = it) }
                SwitchRow("성실사업자 (표준세액공제 12만 원)", p.sincere) { p = p.copy(sincere = it) }
                Muted(BusinessTax.NOT_ADVICE)
            }
        },
    )
}

@Composable
private fun SwitchRow(label: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun TaxLine(label: String, value: String, muted: Boolean = false, strong: Boolean = false) {
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    // 1.25 통합 직접 시험 (855): at the largest text sizes the amount left the name a few letters a line ("세후 / 예상 /
    // (실수 / 령)"); there the amount goes under the name, as the other cards do (VIS-05).
    if (androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f) {
        Column(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
            Text(label, style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge, color = color)
            Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = color,
                fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.align(Alignment.End))
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge, color = color,
            modifier = Modifier.weight(1f))
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = color,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun Muted(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
