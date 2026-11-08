package com.bamnat.core

import com.bamnat.core.design.ChartEmphasis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.38 [DSG-04] 최고·최저 둘까지만, 빈 날은 빼고. */
class ChartEmphasisTest {
    @Test fun highestAndLowestOfWhatIsThere() {
        assertEquals(ChartEmphasis.Picked(2, 3), ChartEmphasis.pick(listOf(0.0, 6.0, 8.5, 4.0, 7.0)))
    }

    @Test fun nothingStandsOutWhenItCannot() {
        assertEquals(ChartEmphasis.Picked(null, null), ChartEmphasis.pick(listOf(0.0, 6.0, 0.0)))
        assertEquals(ChartEmphasis.Picked(null, null), ChartEmphasis.pick(listOf(5.0, 5.0)))
        assertEquals(ChartEmphasis.Picked(null, null), ChartEmphasis.pick(emptyList()))
    }

    @Test fun averageSkipsEmptyDays() {
        assertEquals(6.0, ChartEmphasis.average(listOf(0.0, 4.0, 8.0))!!, 0.0)
        assertNull(ChartEmphasis.average(listOf(0.0)))
    }
}
