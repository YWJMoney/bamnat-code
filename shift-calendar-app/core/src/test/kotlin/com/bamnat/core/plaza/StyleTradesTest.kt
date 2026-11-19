package com.bamnat.core.plaza

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.0.0 [RVN-24] 검토 T22c-04·T22c-03: 현장·알바·가게 업종 무리, 가게의 영업 카드. */
class StyleTradesTest {
    @Test fun `a 현장·알바·가게 tag shows the 업종 the writer picked, from its own list only`() {
        val scope = PlazaTags.Scope(trades = mapOf(WorkStyle.HOURLY to "conv", WorkStyle.FIELD to "build"))
        val alba = PlazaTags.compute(WorkStyle.HOURLY, emptyList(), null, null, worked = 0, scope = scope)
        assertEquals("conv", alba.industry)
        assertEquals(mapOf("s" to "HOURLY", "i" to "conv"), alba.fields())
        assertEquals("알바 · 편의점·마트 · 밤낮 3개월", alba.copy(months = 3).line())
        assertEquals("현장 · 건축", PlazaTags.compute(WorkStyle.FIELD, emptyList(), null, null, 0, scope).line())
        // Not picked for 가게: none. 교대's 업종 is the company list, not these.
        assertNull(PlazaTags.compute(WorkStyle.BUSINESS, emptyList(), null, null, 0, scope).industry)
        assertNull(PlazaTags.compute(WorkStyle.SHIFT, emptyList(), null, null, 0, PlazaTags.Scope(trades = mapOf(WorkStyle.SHIFT to "conv"))).industry)
        // What the server sends back is read by the room's kind of work.
        assertEquals("conv", PlazaTag.of(mapOf("s" to "HOURLY", "i" to "conv")::get)?.industry)
        assertNull(PlazaTag.of(mapOf("s" to "FIELD", "i" to "conv")::get)?.industry)
        assertNull(PlazaTag.of(mapOf("s" to "INVEST", "i" to "retail")::get)?.industry)
        assertTrue(PlazaTags.industryChoices(WorkStyle.INVEST).isEmpty())
        assertEquals(StyleTrades.BUSINESS.map { it.key to it.name }, PlazaTags.industryChoices(WorkStyle.BUSINESS))
    }

    @Test fun `the server takes the very keys of each list`() {
        val js = serverFile().readText()
        val block = Regex("""export const PEOPLE_TRADES = \{(.*?)\};""", RegexOption.DOT_MATCHES_ALL).find(js)!!.groupValues[1]
        for (style in StyleTrades.STYLES) {
            val line = Regex("""${style.name}: \[(.*?)]""").find(block)!!.groupValues[1]
            assertEquals(style.name, StyleTrades.of(style).map { it.key }, Regex("\"([a-z]+)\"").findAll(line).map { it.groupValues[1] }.toList())
        }
        assertTrue("CARD_STYLES has 가게", Regex("""CARD_STYLES = \[[^\]]*"BUSINESS"""").containsMatchIn(js))
    }

    @Test fun `a 가게's card says 영업 and no nights, the same numbers go`() {
        val card = WorkCard(days = 18, work = 15, nights = 15, streak = 9, off = 3, shop = true)
        assertEquals("이번 달 영업 15일 · 최장 연속 9일 · 쉬는 날 3", card.line())
        assertEquals("영업 카드", card.name)
        assertTrue(card.spoken().startsWith("영업 카드, 이번 달 18일 동안"))
        assertEquals(card.copy(shop = false).fields(), card.fields())
        assertEquals("이번 달 근무 15일 · 야간 15 · 최장 연속 9일 · 쉬는 날 3", card.copy(shop = false).line())
    }

    private fun serverFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "server/company-worker/src/plaza-people.js")
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("server/company-worker/src/plaza-people.js not found above ${File("").absolutePath}")
    }
}
