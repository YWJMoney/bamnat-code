package com.bamnat.core

import com.bamnat.core.stats.SetupTaps
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.39 [SETUP-20] Only a bucket edge and a kind of work go, never the exact count. */
class SetupTapsTest {
    @Test fun countsAreRoundedUpToAnEdge() {
        assertEquals(5, SetupTaps.bucket(0))
        assertEquals(5, SetupTaps.bucket(5))
        assertEquals(10, SetupTaps.bucket(6))
        assertEquals(40, SetupTaps.bucket(31))
        assertEquals(100, SetupTaps.bucket(100))
        assertEquals(150, SetupTaps.bucket(101))
        assertEquals(150, SetupTaps.bucket(5000))
    }

    @Test fun whatWaitsReadsBackOnlyWhenItIsWellFormed() {
        assertEquals("SHIFT,20", SetupTaps.encode(WorkStyle.SHIFT, 17))
        assertEquals("BUSINESS" to 40, SetupTaps.decode(SetupTaps.encode(WorkStyle.BUSINESS, 33)))
        assertNull(SetupTaps.decode("SHIFT,17"))
        assertNull(SetupTaps.decode("NURSE,20"))
        assertNull(SetupTaps.decode(null))
        assertNull(SetupTaps.decode("SHIFT"))
    }
}
