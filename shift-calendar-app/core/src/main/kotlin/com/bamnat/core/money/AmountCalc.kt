package com.bamnat.core.money

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 2.0.0 [RVK-15] (검토 U06a-01): 금액 칸에서 바로 계산해요. "36000÷3" → 12,000, "4500+3200×2" → 10,900(곱하기·나누기 먼저),
 * 나누어 떨어지지 않으면 원 단위로 반올림(10,000÷3 → 3,333). 칸에는 + − × ÷ 로 보이고, 자판의 '*'·'x'·'/'·'-'도 같은 뜻으로 읽어요.
 */
object AmountCalc {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    val OPS: List<Char> = listOf(PLUS, MINUS, TIMES, DIVIDE)

    /** Longest expression the box keeps; a single amount keeps to the plain field's own limit. */
    const val MAX_LENGTH = 40
    private const val MAX = 99_999_999_999L

    /** True when [s] has an operator after a number: the box is a sum, not a plain amount. */
    fun hasOp(s: String): Boolean = s.drop(1).any { it in OPS }

    /**
     * What the box keeps of what was typed: digits and the four operators only, never an operator first, a second operator in a row
     * taking the place of the first ("36000+÷" → "36000÷"), no leading zeros in a number.
     */
    fun clean(typed: String): String {
        val out = StringBuilder()
        for (raw in typed) {
            val c = when (raw) {
                '*', 'x', 'X', '✕' -> TIMES
                '/' -> DIVIDE
                '-', '–', '—' -> MINUS
                else -> raw
            }
            when {
                c in '0'..'9' -> {
                    // "0" then "5" in the same number is "5".
                    val start = out.indexOfLast { it in OPS } + 1
                    if (out.length - start == 1 && out[start] == '0') out.setLength(start)
                    out.append(c)
                }
                c in OPS && out.isNotEmpty() -> {
                    if (out.last() in OPS) out.setLength(out.length - 1)
                    out.append(c)
                }
            }
            if (out.length >= MAX_LENGTH) break
        }
        return out.toString()
    }

    /**
     * The amount [s] comes to, in whole won: null when there is nothing to count, when it divides by zero, or when it comes to 0 or
     * less or past what an amount can be. An operator at the end is not counted yet ("36000÷" is 36,000).
     */
    fun eval(s: String): Long? {
        val text = clean(s).trimEnd { it in OPS }
        if (text.isEmpty()) return null
        val numbers = ArrayList<BigDecimal>()
        val ops = ArrayList<Char>()
        var i = 0
        while (i < text.length) {
            val start = i
            while (i < text.length && text[i] in '0'..'9') i++
            if (start == i) return null
            numbers += BigDecimal(text.substring(start, i))
            if (i < text.length) ops += text[i++]
        }
        // × and ÷ first, then + and − from the left.
        val terms = arrayListOf(numbers[0])
        val signs = ArrayList<Char>()
        for (k in ops.indices) {
            val n = numbers[k + 1]
            when (ops[k]) {
                TIMES -> terms[terms.lastIndex] = terms.last().multiply(n)
                DIVIDE -> {
                    if (n.signum() == 0) return null
                    terms[terms.lastIndex] = terms.last().divide(n, 10, RoundingMode.HALF_UP)
                }
                else -> { signs += ops[k]; terms += n }
            }
        }
        var sum = terms[0]
        signs.forEachIndexed { k, op -> sum = if (op == PLUS) sum.add(terms[k + 1]) else sum.subtract(terms[k + 1]) }
        val won = sum.setScale(0, RoundingMode.HALF_UP)
        if (won.signum() <= 0 || won > BigDecimal.valueOf(MAX)) return null
        return won.toLong()
    }
}
