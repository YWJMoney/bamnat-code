package com.bamnat.app.ui.components

import androidx.compose.ui.text.AnnotatedString
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.35 [J135-02] Number boxes show 1,000 groups while typed; the cursor maps over the commas. */
class GroupedNumberTest {
    private fun shown(s: String) = GroupedNumber.filter(AnnotatedString(s)).text.text

    @Test fun groupsTheWholePartOnly() {
        assertEquals("1,000,000", shown("1000000"))
        assertEquals("999", shown("999"))
        assertEquals("12,345.6789", shown("12345.6789"))
        assertEquals("-2,500", shown("-2500"))
        assertEquals("", shown(""))
        assertEquals("0.125", shown("0.125"))
    }

    @Test fun theCursorStepsOverCommas() {
        val t = GroupedNumber.filter(AnnotatedString("1234567"))
        val m = t.offsetMapping
        assertEquals("1,234,567", t.text.text)
        assertEquals(9, m.originalToTransformed(7))
        assertEquals(0, m.originalToTransformed(0))
        assertEquals(2, m.originalToTransformed(1))
        assertEquals(7, m.transformedToOriginal(9))
        assertEquals(1, m.transformedToOriginal(1))
        assertEquals(1, m.transformedToOriginal(2))
        for (o in 0..7) assertEquals(o, m.transformedToOriginal(m.originalToTransformed(o)))
    }
}
