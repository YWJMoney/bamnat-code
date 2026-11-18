package com.bamnat.app.ui.invest

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import com.bamnat.app.ui.components.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.invest.Market
import com.bamnat.core.invest.Portfolio
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.abs
import com.bamnat.app.ui.theme.bamnat

/**
 * [INV-01][INV-17][INV-19] How the investor calendar writes its numbers: gains red with +, losses blue
 * with −, as Korean brokers show them; amounts hidden behind dots when the user asks; dollars with
 * their won beside them.
 */
object InvestFmt {
    const val HIDDEN = "•••••"
    private val symbols = DecimalFormatSymbols(Locale.KOREA)
    private val priceFormat = DecimalFormat("#,##0.########", symbols)
    private val usdFormat = DecimalFormat("#,##0.00", symbols)

    fun won(v: Long, hide: Boolean = false): String = if (hide) HIDDEN else Fmt.won(v)
    fun short(v: Long, hide: Boolean = false): String = if (hide) HIDDEN else Fmt.wonShort(v)

    /** "+125,000원", "−3,200원", "0원". */
    fun signed(v: Long, hide: Boolean = false): String = if (hide) HIDDEN else sign(v) + Fmt.won(abs(v))

    /** "+12만", "−8천" for a calendar cell. */
    fun signedShort(v: Long, hide: Boolean = false): String = if (hide) HIDDEN else sign(v) + Fmt.wonShort(abs(v))

    /** "+3.25%"; "–" when there is nothing to divide by yet. */
    fun percent(rate: Double?): String = rate?.let { r ->
        val p = r * 100
        (if (p > 0.004) "+" else if (p < -0.004) "−" else "") + "%.2f%%".format(Locale.ROOT, abs(p))
    } ?: "–"

    /** A price in its own currency: "72,500원", "$187.20", "0.00052원" for a cheap coin. */
    fun price(p: Double, market: Market): String = if (market.foreign) "$" + usdFormat.format(p) else priceFormat.format(p) + "원"

    private val averageFormat = DecimalFormat("#,##0.##", symbols)

    /**
     * 2.0.0 [RVK-27] (검토 MONEY-64, 조건 4) An average price: a split or free shares leave 40,444.44444444원, read as 40,444.44원.
     * A coin's average keeps its small digits, a dollar one its cents.
     */
    fun average(p: Double, market: Market): String =
        if (market.foreign || market == Market.CRYPTO) price(p, market) else averageFormat.format(p) + "원"

    /** [INV-19] "$187.20 (약 262,080원)". */
    fun priceWithWon(p: Double, market: Market, fx: Double): String =
        if (market.foreign) price(p, market) + " (약 " + Fmt.won(Math.round(p * fx)) + ")" else price(p, market)

    fun quantity(q: Double): String = Portfolio.quantityText(q)

    /** Typed text for a Double field: no trailing ".0". */
    fun input(v: Double?): String = v?.takeIf { it > 0 }?.let { Portfolio.quantityText(it) } ?: ""

    private fun sign(v: Long) = if (v > 0) "+" else if (v < 0) "−" else ""

    /** 2.0.0 [RVK-25] (검토 MONEY-69) How old a typed price is: "오늘", "어제", "3일 전". */
    fun ago(date: LocalDate, today: LocalDate): String = when (val d = java.time.temporal.ChronoUnit.DAYS.between(date, today)) {
        in Long.MIN_VALUE..0L -> "오늘"
        1L -> "어제"
        else -> "${d}일 전"
    }

    /** [RVK-25] A price this many days old is worth typing again. */
    const val STALE_DAYS = 7L
}

/** [INV-01] Gain red, loss blue, nothing grey: the colours of Korean brokers. */
@Composable
fun pnlColor(v: Double): Color {
    val cal = LocalCalendarColors.current
    return when {
        v > 0.5 -> cal.sunday
        v < -0.5 -> cal.saturday
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun pnlColor(v: Long): Color = pnlColor(v.toDouble())

@Composable
fun rateColor(r: Double?): Color = pnlColor(((r ?: 0.0) * 1_000_000))

/** A number with decimals (quantity, price, rate): digits and one point. */
@Composable
fun DecimalField(
    label: String,
    text: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    suffix: String? = null,
    supporting: String? = null,
    imeAction: ImeAction = ImeAction.Next,
    /** 2.0.0 [RVK-26] A value read from a capture that the user should look at: the line under it in the caution colour. */
    warn: Boolean = false,
) {
    OutlinedTextField(
        value = text,
        onValueChange = { v -> onChange(cleanDecimal(v)) },
        label = { Text(label) },
        suffix = suffix?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it, color = if (warn) MaterialTheme.bamnat.caution else androidx.compose.ui.graphics.Color.Unspecified) } },
        singleLine = true,
        visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = imeAction),
        modifier = modifier,
    )
}

/**
 * 2.0.0 [RVK-27] (검토 MONEY-64, 조건 12) 종목명 and 코드 side by side, one under the other at a big font so that '코드 (선택)' does not
 * break over two lines in a narrow box. The trade sheet and the quantity sheet share it.
 */
@Composable
internal fun NameCodeFields(name: String, onName: (String) -> Unit, symbol: String, onSymbol: (String) -> Unit, examples: Boolean = false) {
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    val nameField: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(name, { onName(it.take(40)) }, label = { Text("종목명") }, placeholder = if (examples) ({ Text("삼성전자") }) else null,
            singleLine = true, modifier = m)
    }
    val codeField: @Composable (Modifier) -> Unit = { m ->
        OutlinedTextField(symbol, { onSymbol(it.take(20)) }, label = { Text("코드 (선택)") }, placeholder = if (examples) ({ Text("005930") }) else null,
            singleLine = true, modifier = m)
    }
    if (big) androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        nameField(Modifier.fillMaxWidth()); codeField(Modifier.fillMaxWidth())
    } else androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        nameField(Modifier.weight(1.4f)); codeField(Modifier.weight(1f))
    }
}

/**
 * 2.0.0 [RVK-33] (검토 MONEY-70) What a number field keeps: a comma is a thousands mark ('72,500' stays 72500, it was 72.5),
 * and the decimal only on a phone whose language writes it so ([com.bamnat.core.text.Numbers.cleanDecimal]).
 */
internal fun cleanDecimal(v: String): String = com.bamnat.core.text.Numbers.cleanDecimal(v, commaIsDecimal = phoneWritesDecimalComma())

/** [RVK-33] True on a phone in German, Vietnamese, French and the like, whose number keypad's decimal key is a comma. */
internal fun phoneWritesDecimalComma(): Boolean = java.text.DecimalFormatSymbols.getInstance().decimalSeparator == ','

internal fun String.toDecimal(): Double? = toDoubleOrNull()?.takeIf { it.isFinite() }

/** One-tap tags in a wrapping row. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun <T> ChoiceChips(items: List<T>, selected: (T) -> Boolean, label: (T) -> String, onClick: (T) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { item -> FilterChip(selected = selected(item), onClick = { onClick(item) }, label = { Text(label(item)) }) }
    }
}

/** A date picker that answers a [LocalDate]. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestDatePicker(initial: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            }) { Text("확인") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    ) { DatePicker(state, showModeToggle = false) }
}

/** "09:05" for minutes after midnight; "" without. */
internal fun minuteText(m: Int?): String = m?.let { "%02d:%02d".format(java.util.Locale.ROOT, it / 60, it % 60) } ?: ""

/** "0905", "9:05" or "905" typed → minutes; null when it is not a time. */
internal fun parseMinute(s: String): Int? {
    val d = s.filter(Char::isDigit)
    if (d.isEmpty()) return null
    val (h, m) = when (d.length) {
        1, 2 -> d.toInt() to 0
        3 -> d.take(1).toInt() to d.drop(1).toInt()
        else -> d.take(2).toInt() to d.drop(2).take(2).toInt()
    }
    return if (h in 0..23 && m in 0..59) h * 60 + m else null
}
