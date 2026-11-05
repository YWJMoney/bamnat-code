package com.bamnat.app.ui.money

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.money.BudgetStatus
import com.bamnat.core.money.FlowKind
import com.bamnat.core.money.MoneyAhead
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyForecast
import com.bamnat.core.money.PayDay
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate

/** 1.37 [LEDH-01][LEDH-07] The pay engine's pay days ahead and take-home per hour; empty without pay set up. */
data class AheadPay(
    val days: List<PayDay> = emptyList(), val hourly: Long? = null, val payDay: Int? = null, val perShift: Long? = null,
    /** 1.37 [LEDH-16] Last month's work, for 지난달 돈 이야기. */
    val lastMonth: MonthWork? = null,
)

/** 1.37 [LEDH-16] A month's work as the pay engine counted it, and its take-home. */
data class MonthWork(val minutes: Int, val days: Int, val nights: Int, val net: Long)

/** 1.37 [LEDH-02] The kinds of money a calendar of [style] waits for until its next 'pay'. */
internal fun nextKinds(style: WorkStyle?): Set<FlowKind> = when (style) {
    WorkStyle.FIELD -> setOf(FlowKind.JOB, FlowKind.OWED)
    WorkStyle.BUSINESS -> setOf(FlowKind.CARD_SALES, FlowKind.OWED, FlowKind.JOB)
    else -> setOf(FlowKind.PAY)
}

/**
 * 1.37 [LEDH-03] 오늘 한 줄: "오늘 쓸 돈 2만 3천 원 · 월급까지 9일 · 정리할 것 3". From the forecast when account
 * balances are known, else from the budget; parts that cannot be known are left out. Null when nothing is known.
 */
internal fun todayLine(forecast: MoneyForecast?, budget: BudgetStatus, style: WorkStyle?, toSort: Int, todaySpent: Long): String? {
    val until = forecast?.untilNext(nextKinds(style), todaySpent)
    val left = until?.todayLeft ?: budget.todayLeft
    return listOfNotNull(
        left?.let { if (it >= 0) "오늘 쓸 돈 ${Fmt.won(it)}" else "오늘 ${Fmt.won(-it)} 넘게 썼어요" },
        until?.words,
        toSort.takeIf { it > 0 }?.let { "정리할 것 $it" },
    ).takeIf { it.isNotEmpty() }?.joinToString(" · ")
}

/**
 * 1.37 [LEDH-01][LEDH-02][LEDH-03][LEDH-05] 앞으로의 돈: today's line, each day's balance ahead as a line (30 days free,
 * 90 with the 7-day pass or 프로, 결정 5), a warning when money runs short or a card bill leaves before pay, and what
 * comes in and goes out next. Without an account balance it still lists what is coming and says how to get the line.
 */
@Composable
internal fun AheadCard(
    book: MoneyBook, style: WorkStyle?, today: LocalDate, pay: AheadPay?, budget: BudgetStatus, toSort: Int, gate: Gate,
    onAddAccount: () -> Unit, onOpenPay: () -> Unit,
    snapshot: com.bamnat.app.data.ScheduleSnapshot? = null,
) {
    var span by rememberSaveable { mutableIntStateOf(MoneyAhead.FREE_DAYS) }
    val full = remember(book, today, pay) { MoneyAhead.forecast(book, today, MoneyAhead.FULL_DAYS, pay?.days.orEmpty()) }
    val shown = remember(full, span) { full.copy(days = full.days.take(span)) }
    val todaySpent = remember(book, today) { book.entries.filter { it.date == today && it.kind == com.bamnat.core.money.MoneyKind.EXPENSE }.sumOf { it.amount } }
    val line = todayLine(full, budget, style, toSort, todaySpent)
    // 1.37 [LEDH-08] 근무 리듬 예산: the money until pay shared out by how each kind of day spends (교대·일근·알바).
    val rhythm by androidx.compose.runtime.produceState<com.bamnat.core.money.ShiftRhythm.Plan?>(null, full, snapshot, style, today) {
        value = if (snapshot == null || style?.usesPayEngine != true) null else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val until = full.untilNext(nextKinds(style), todaySpent) ?: return@runCatching null
                val perDay = until.perDay ?: return@runCatching null
                val label = com.bamnat.core.tax.TaxPlus.dayLabel(snapshot.resolver::resolve)
                val everyday = book.entries.filter { it.source != com.bamnat.core.money.MoneySource.FIXED }
                val rows = com.bamnat.core.tax.TaxPlus.byShift(everyday, today.minusDays(89), today, label)
                val spendDays = everyday.filter { it.kind == com.bamnat.core.money.MoneyKind.EXPENSE && !it.date.isBefore(today.minusDays(89)) && !it.date.isAfter(today) }
                    .map { it.date }.distinct().size
                com.bamnat.core.money.ShiftRhythm.plan(perDay * until.daysLeft, today, until.next.date.minusDays(1), label, rows, spendDays)
            }.getOrNull()
        }
    }
    if (line == null && shown.flows.isEmpty() && !shown.hasBalance) return
    SectionCard(title = "앞으로의 돈") {
        line?.let { Text(it, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        // 1.37 PC 확인 6a4efc92 제안: '오늘 쓸 돈'을 어떻게 셌는지 한 줄 풀이.
        full.untilNext(nextKinds(style), todaySpent)?.takeIf { it.perDay != null }?.let { u ->
            Muted("(" + (if (todaySpent > 0) "오늘 아침 통장 " else "통장 ") + "${Fmt.won(full.start + todaySpent)}" + (if (u.reserved > 0) " − 그 전에 나갈 돈 ${Fmt.won(u.reserved)}" else "") +
                (if (u.arriving > 0) " + 들어올 돈 ${Fmt.won(u.arriving)}" else "") + ") ÷ ${u.daysLeft}일 = 하루 ${Fmt.won(u.perDay!!)}" +
                (if (todaySpent > 0) ", 오늘 쓴 ${Fmt.won(todaySpent)}을 빼고 보여요" else ""))
        }
        rhythm?.let { p ->
            val say: @Composable () -> Unit = {
                Muted("근무 리듬대로 하루: ${p.words(Fmt::won)}. 오늘(${p.todayLabel})은 ${Fmt.won((p.today - todaySpent).coerceAtLeast(0))} 남았어요.")
            }
            if (gate.allows(Feature.MONEY_STATS)) say() else com.bamnat.app.ui.pro.ProLock(Feature.MONEY_STATS, gate) { say() }
        }
        if (shown.hasBalance) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                FilterChip(span == MoneyAhead.FREE_DAYS, { span = MoneyAhead.FREE_DAYS }, { Text("30일") })
                FilterChip(span == MoneyAhead.FULL_DAYS, { span = MoneyAhead.FULL_DAYS }, { Text("90일") })
            }
            val chart: @Composable () -> Unit = { BalanceLine(shown) }
            if (span > MoneyAhead.FREE_DAYS) com.bamnat.app.ui.pro.ProLock(Feature.MONEY_STATS, gate) { chart() } else chart()
            shown.end?.let { e -> Muted("${Fmt.monthDay(e.date)}에는 약 ${Fmt.won(e.balance)}, 가장 적을 때 ${shown.lowest?.let { "${Fmt.monthDay(it.date)} ${Fmt.won(it.balance)}" }}") }
            shown.firstBelowZero?.let { d ->
                Text("${Fmt.monthDay(d.date)}에 통장이 ${Fmt.won(d.balance)}이 돼요", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp))
            }
            shown.billsBeforePay.firstOrNull()?.let { (bill, p) ->
                val gap = java.time.temporal.ChronoUnit.DAYS.between(bill.date, p.date)
                Muted("${bill.label}이 ${p.label}보다 ${gap}일 먼저 나가요. 카드사에서 결제일을 월급 뒤로 옮기거나, 그 전에 다른 통장에서 옮겨 두면 막을 수 있어요.")
            }
        } else {
            Muted("통장·현금 잔액을 넣으면 날마다 남을 돈을 선으로 그려 드려요.")
            TextButton(onClick = onAddAccount) { Text("통장 넣기") }
        }
        val next = shown.flows.take(5)
        if (next.isNotEmpty()) {
            Column(Modifier.padding(top = 6.dp)) {
                next.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                        Text("${Fmt.shortDate(f.date)} ${f.label}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 1)
                        Text((if (f.amount > 0) "+" else "−") + Fmt.won(kotlin.math.abs(f.amount)), style = MaterialTheme.typography.bodyMedium,
                            color = if (f.amount > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
        if (shown.everydayPerDay > 0) Muted("평소 하루 지출 ${Fmt.won(shown.everydayPerDay)}을 날마다 뺐어요" + if (shown.everydayGuessed) " (기록이 2주가 안 돼서 대략이에요)." else ".")
        if (shown.late > 0) Muted("약속한 날이 지난 받을 돈 ${Fmt.won(shown.late)}은 넣지 않았어요.")
        if (pay?.days?.any { it.dateGuessed } == true) {
            Muted("월급날을 몰라서 달 마지막 날로 넣었어요.")
            TextButton(onClick = onOpenPay) { Text("월급날 넣기") }
        } else if (pay == null || (pay.days.isEmpty() && style?.usesPayEngine == true)) {
            Muted("급여를 설정하면 달력의 근무로 계산한 실수령이 월급날에 들어가요.")
        }
    }
}

/** 1.37 [LEDH-01] Each day's balance as one line, zero dashed, pay days dotted. */
@Composable
private fun BalanceLine(f: MoneyForecast) {
    val color = MaterialTheme.colorScheme.primary
    val warn = MaterialTheme.colorScheme.error
    val axis = MaterialTheme.colorScheme.outlineVariant
    val days = f.days
    if (days.size < 2) return
    val hi = maxOf(days.maxOf { it.balance }, f.start, 0)
    val lo = minOf(days.minOf { it.balance }, 0)
    val range = (hi - lo).coerceAtLeast(1)
    val words = "앞으로 ${days.size}일 잔액: 지금 ${Fmt.won(f.start)}, 마지막 날 ${Fmt.won(days.last().balance)}, 가장 적을 때 ${Fmt.won(days.minOf { it.balance })}"
    Canvas(Modifier.fillMaxWidth().height(96.dp).padding(vertical = 8.dp).semantics { contentDescription = words }) {
        val stepX = size.width / (days.size - 1)
        fun y(v: Long) = size.height - ((v - lo).toFloat() / range) * size.height
        val zero = y(0)
        drawLine(axis, Offset(0f, zero), Offset(size.width, zero), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)))
        val path = Path()
        days.forEachIndexed { i, d -> if (i == 0) path.moveTo(0f, y(d.balance)) else path.lineTo(i * stepX, y(d.balance)) }
        drawPath(path, if (f.firstBelowZero != null) warn else color, style = Stroke(width = 2.dp.toPx()))
        days.forEachIndexed { i, d ->
            if (d.flows.any { it.kind == FlowKind.PAY || it.kind == FlowKind.JOB }) drawCircle(color, 3.dp.toPx(), Offset(i * stepX, y(d.balance)))
        }
    }
}

/**
 * 1.37 [LEDH-07] 근무 시간 환산 on this screen: spending of [from] won or more said as the work it took, from the
 * take-home per hour ([hourly]), a day's pay of 공수 work ([dayRate], 현장) or an average day's sales ([daySales], 자영업).
 */
data class WorkTimeSay(val from: Long, val hourly: Long? = null, val dayRate: Long? = null, val daySales: Long? = null) {
    fun words(amount: Long): String? {
        if (!com.bamnat.core.money.WorkTime.shows(amount, from)) return null
        return hourly?.let { com.bamnat.core.money.WorkTime.words(amount, it) }
            ?: dayRate?.let { com.bamnat.core.money.WorkTime.units(amount, it) }
            ?: daySales?.let { com.bamnat.core.money.WorkTime.salesShare(amount, it) }
    }

    /**
     * 1.37 PC 확인 6a4efc92 ("근무 4.2시간 — 기준이 불분명"): what the hours are counted by. 시급 is the latest finished pay
     * month's take-home (세후) over the hours it counted, so it is below the 통상시급.
     */
    fun basis(): String? = hourly?.let { "최근 한 달 실수령 ÷ 일한 시간 = 시간당 ${Fmt.won(it)} 기준" }
        ?: dayRate?.let { "최근 90일 공수 1당 ${Fmt.won(it)} 기준" }
        ?: daySales?.let { "최근 90일 하루 매출 평균 ${Fmt.won(it)} 기준" }

    companion object {
        /** What the calendar knows: 투자 says nothing; 현장 by 공수, 자영업 by sales, the rest by pay. */
        fun of(style: WorkStyle?, from: Long, book: MoneyBook, today: LocalDate, hourly: Long?): WorkTimeSay? {
            if (from <= 0 || style == WorkStyle.INVEST) return null
            val since = today.minusDays(90)
            return when (style) {
                WorkStyle.FIELD -> {
                    val jobs = book.jobs.filter { !it.cancelled && it.date.isAfter(since) && (it.units ?: 0.0) > 0 }
                    val units = jobs.sumOf { it.units ?: 0.0 }
                    (if (units > 0) (jobs.sumOf { it.payable } / units).toLong() else null)?.let { WorkTimeSay(from, dayRate = it) }
                }
                WorkStyle.BUSINESS -> {
                    val days = book.sales.filter { it.date.isAfter(since) && it.sales > 0 }
                    days.takeIf { it.isNotEmpty() }?.let { d -> WorkTimeSay(from, daySales = d.sumOf { it.sales } / d.size) }
                }
                else -> hourly?.let { WorkTimeSay(from, hourly = it) }
            }
        }
    }
}

/** 1.37 [LEDH-07] Given by the money tab; null elsewhere, so nothing is said there. */
val LocalWorkTime = androidx.compose.runtime.staticCompositionLocalOf<WorkTimeSay?> { null }
