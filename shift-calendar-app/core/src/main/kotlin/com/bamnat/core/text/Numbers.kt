package com.bamnat.core.text

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

/**
 * 2.0.0 [RVK-33] (검토 MONEY-17·55·70·75·78) Numbers written and read the same way on every phone. A phone in German or
 * Vietnamese wrote '1.234.567원' and '0,05', a rate field then read '0,05' as nothing (0%), a price field turned '72,500' into
 * 72.5, and a capture showed '5.2E-4'. What the app writes goes through a fixed [Locale] (`format(Locale.ROOT, …)` or these),
 * and what a field keeps of what was typed goes through [cleanDecimal].
 */
object Numbers {
    private val THOUSANDS_COMMA = Regex("""\d{1,3}(,\d{3})+""")
    private val THOUSANDS_DOT = Regex("""\d{1,3}(\.\d{3}){2,}""")

    /** '1,234,567' on every phone. */
    fun grouped(v: Long): String = String.format(Locale.ROOT, "%,d", v)

    /** '1,234,567원' on every phone. */
    fun won(v: Long): String = grouped(v) + "원"

    /** At most [digits] decimals with a point, no trailing zeros, never '5.2E-4'. Empty for NaN or infinity. */
    fun plain(v: Double, digits: Int = 8): String {
        if (!v.isFinite()) return ""
        val s = BigDecimal.valueOf(v).setScale(digits, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
        return if (s == "-0" || s.trimStart('-').all { it == '0' || it == '.' }) "0" else s
    }

    /**
     * What a number field keeps of [typed]: digits and one point, at most [max] characters. A comma is a thousands mark
     * ('72,500' → 72500) unless the phone's language writes decimals with one ([commaIsDecimal]) and it does not read as
     * thousands ('0,05' → 0.05). With both marks the last is the decimal ('1,234.5', '1.234,5' → 1234.5); dots alone in
     * groups of three ('1.234.567') are thousands. A point typed first becomes '0.'.
     */
    fun cleanDecimal(typed: String, commaIsDecimal: Boolean, max: Int = 18): String {
        val t = typed.filter { it.isDigit() || it == '.' || it == ',' }
        val lastDot = t.lastIndexOf('.')
        val lastComma = t.lastIndexOf(',')
        val decimalAt = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastComma >= 0 -> if (commaIsDecimal && !THOUSANDS_COMMA.matches(t)) t.indexOf(',') else -1
            lastDot >= 0 -> if (THOUSANDS_DOT.matches(t)) -1 else t.indexOf('.')
            else -> -1
        }
        val sb = StringBuilder()
        t.forEachIndexed { i, ch ->
            if (ch.isDigit()) sb.append(ch) else if (i == decimalAt) sb.append(if (sb.isEmpty()) "0." else ".")
        }
        return sb.toString().take(max)
    }

    /**
     * A rate (이율·수수료 %) as typed: a comma is always the decimal there, for no rate has thousands ('4,5' → 4.5).
     */
    fun cleanRate(typed: String, max: Int = 5): String = cleanDecimal(typed.replace(',', '.'), commaIsDecimal = false, max = max)

    /**
     * A number the app wrote into a note and reads back, whatever language the phone had when it wrote it: the last mark
     * followed by one or two digits is the decimal ('12.30', '12,30', '1.234,56'), every other mark groups thousands.
     */
    fun readWritten(text: String): Double? {
        val t = text.filter { it.isDigit() || it == '.' || it == ',' }
        if (t.none { it.isDigit() }) return null
        val last = maxOf(t.lastIndexOf('.'), t.lastIndexOf(','))
        val decimals = if (last >= 0) t.length - last - 1 else 0
        val plain = if (last >= 0 && decimals in 1..2) t.substring(0, last).filter { it.isDigit() } + "." + t.substring(last + 1)
        else t.filter { it.isDigit() }
        return plain.toDoubleOrNull()
    }
}
