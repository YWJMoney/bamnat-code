package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVS-19] SN-09: the widget and the studio's widget example draw the same part, and the 부품 도구 says what the widget leaves out. */
class WidgetDrawsTest {
    private val rich = PartStyle(
        fill = 0xFF112233L, fillA = 80, line = "solid", lineW = 2.0, lineC = 0xFFFF0000L, rTL = 4, rTR = 12, shadow = 6,
        pad = 8, font = "jua", size = 15.0, weight = 650, ink = 0xFFFFFFFFL, italic = true, talign = "center", motion = "td:pulse",
        paint = Fill.Shader("aurora", listOf(0xFF000000L, 0xFFFFFFFFL)), alpha = 60, track = 0.1,
    )

    @Test fun aWidgetPartKeepsOnlyWhatALauncherDraws() {
        val w = rich.widgetDraws()
        assertEquals(rich.paint!!.average, w.fill)
        assertEquals(80, w.fillA)
        assertEquals(4, w.r)
        assertEquals(8, w.padH)
        assertNull("only a launcher's fonts", w.font)
        assertEquals(700, w.weight)
        assertEquals(0xFFFFFFFFL, w.ink)
        assertEquals("center", w.talign)
        for (gone in listOf(w.line, w.lineW, w.shadow, w.motion, w.paint, w.alpha, w.track, w.rTR)) assertNull(gone)
        assertEquals("serif", PartStyle(font = "serif").widgetDraws().font)
        assertTrue(PartStyle.NONE.widgetDraws().isEmpty)
    }

    @Test fun theToolSaysWhatTheWidgetLeavesOut() {
        val l = WidgetDraws.leaves(rich)
        for (w in listOf("무늬·그러데이션(한 색으로)", "선", "그림자", "모서리 따로(한 가지로)", "글꼴(폰 기본 글꼴로)", "움직임", "투명도", "자간·줄 간격")) assertTrue("$w in $l", w in l)
        assertEquals(emptyList<String>(), WidgetDraws.leaves(PartStyle(fill = 0xFF112233L, r = 8, pad = 4, ink = 0xFF000000L, font = "mono")))
    }
}
