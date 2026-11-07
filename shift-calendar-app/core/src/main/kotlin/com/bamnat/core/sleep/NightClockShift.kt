package com.bamnat.core.sleep

/**
 * 1.39 G5-N3 폴드 탁상 시계: a clock kept on all night moves a few dp each minute, so an OLED screen does not keep its shape.
 * A small walk over 15 places (5 across × 3 down), back where it started every quarter hour; never more than [MAX_DP] away.
 */
object NightClockShift {
    const val MAX_DP = 4
    private val across = listOf(-4, -2, 0, 2, 4)
    private val down = listOf(-3, 0, 3)

    /** The clock's offset in dp (x to y) at [minute] past the hour. */
    fun of(minute: Int): Pair<Int, Int> {
        val m = Math.floorMod(minute, 15)
        return across[m % 5] to down[m / 5]
    }
}
