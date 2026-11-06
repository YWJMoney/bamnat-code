package com.bamnat.core.design

/**
 * 1.38 [DSG-04] 그래프 규칙: bars are one colour and at most two stand out, the highest and the lowest. Days with no value
 * (0 or less) are left out of both, and a chart of one value or of all-equal values marks nothing.
 */
object ChartEmphasis {
    data class Picked(val maxIndex: Int?, val minIndex: Int?)

    fun pick(values: List<Double>): Picked {
        val real = values.withIndex().filter { it.value > 0.0 }
        if (real.size < 2) return Picked(null, null)
        val max = real.maxBy { it.value }
        val min = real.minBy { it.value }
        if (max.value == min.value) return Picked(null, null)
        return Picked(max.index, min.index)
    }

    /** The average of the values that are there, for the baseline when there is no target; null with none. */
    fun average(values: List<Double>): Double? = values.filter { it > 0.0 }.takeIf { it.isNotEmpty() }?.average()
}
