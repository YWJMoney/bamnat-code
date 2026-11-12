package com.bamnat.app.ui.settings

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVP-66][RVP-67][RVP-68] (검토 REST-07·REST-08·REST-09·U14-04) The look editors: each kind of work's pay example in its own
 * words, every editor's way back in one place, a border width kept as shown.
 */
class LookEditorsTest {
    @Test fun thePayExampleSpeaksEachKindOfWork() {
        WorkStyle.entries.filter { it != WorkStyle.SHIFT }.forEach { s ->
            val words = paySampleOf(s).lines.joinToString { it.first }
            assertFalse("${s.label}: $words", "야간" in words || "결근" in words)
        }
        assertTrue("주휴수당" in paySampleOf(WorkStyle.HOURLY).lines.joinToString { it.first })
        assertTrue("공" in paySampleOf(WorkStyle.FIELD).lines.first().first)
        assertTrue("매출" in paySampleOf(WorkStyle.BUSINESS).lines.first().first)
        assertEquals("월급", paySampleOf(WorkStyle.SHIFT).kind)
    }

    @Test fun aBorderWidthIsKeptAsShown() {
        assertEquals(13, tenths(1.3f))
        assertEquals(13, tenths(1.2999f))
        assertEquals(5, tenths(0.42f))
        assertEquals(40, tenths(4.4f))
    }

    @Test fun everyLookEditorEndsWithTheSameWayBack() {
        val src = File("src/main/java/com/bamnat/app/ui/settings/FreeLookScreen.kt").readText()
        listOf("CellDesigner", "MemoLookEditor", "MoneyLookEditor", "PayLookEditor", "ScreenLookEditor").forEach { f ->
            val body = src.substringAfter("fun $f(").substringBefore("\n}\n")
            assertTrue("$f has no ResetAll", "ResetAll(" in body)
        }
        // 마을 returns early: its own way back comes before the return.
        val plaza = src.substringAfter("if (target == FreeTarget.PLAZA) {").substringBefore("return")
        assertTrue("마을 has no 처음대로", "ResetAll(" in plaza)
    }
}
