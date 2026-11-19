package com.bamnat.core.setup

import com.bamnat.core.tier.InviteRules
import java.io.File
import java.net.URLEncoder
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVR-31] (검토 I50c-01·03, SPREAD-30·31) What bamnat.app's Play link carries into the first run. */
class WebHandoffTest {
    private fun referrer(content: String, source: String = "bamnat_web", campaign: String = "shift_cal") =
        "utm_source=$source&utm_medium=shift&utm_campaign=$campaign&utm_content=" + URLEncoder.encode(content, "UTF-8")

    @Test fun `the shift page's order, first day and 'once' come back as they were`() {
        val h = WebHandoff.fromReferrer(referrer("p-DNBO_s-20261110"))!!
        assertEquals("DNBO", h.codes)
        assertEquals(LocalDate.of(2026, 11, 10), h.first)
        assertEquals(false, h.once)
        assertEquals("shift_cal", h.campaign)
        assertEquals("웹에서 보던 주야비휴, 11월 10일부터 채울까요?", h.question())
        assertEquals("웹에서 보던 4조 2교대, 11월 10일부터 채울까요?", h.question("4조 2교대"))
        assertEquals(true, WebHandoff.fromReferrer(referrer("p-DDNNOOOO_s-20261201_o-1"))!!.once)
    }

    @Test fun `a calculator's hourly wage, and the salary pages' 명세서 맞추기`() {
        val wage = WebHandoff.fromReferrer(referrer("w-10320", campaign = "alba"))!!
        assertEquals(10_320L, wage.hourly)
        assertEquals("웹에서 넣은 시급 10,320원으로 시작할까요?", wage.question())
        val slip = WebHandoff.fromReferrer(referrer("t-slip", campaign = "salary"))!!
        assertEquals(WebHandoff.SLIP, slip.target)
        assertNull(slip.question())
        val both = WebHandoff.read("p-DDNN_s-20261112_w-12000")!!
        assertEquals("웹에서 보던 주주야야, 11월 12일부터 채우고, 시급 12,000원으로 시작할까요?", both.question())
    }

    @Test fun `the bare order the shift page sent before 2_0_0 still counts`() {
        val h = WebHandoff.fromReferrer(referrer("DNBO"))!!
        assertEquals("DNBO", h.codes)
        assertNull(h.first)
    }

    @Test fun `anything that is not the site's, or not a setting, is nothing`() {
        assertNull(WebHandoff.fromReferrer(null))
        assertNull(WebHandoff.fromReferrer("utm_source=google-play&utm_medium=organic"))
        // An invite's code is the invite's, never an order.
        val invite = "utm_source=${InviteRules.SOURCE}&utm_content=ABCD2345"
        assertNull(WebHandoff.fromReferrer(invite))
        assertEquals("ABCD2345", InviteRules.fromReferrer(invite))
        assertNull(WebHandoff.read("p-DNBQ"))
        assertNull("a day without an order means nothing", WebHandoff.read("s-20261110"))
        assertNull(WebHandoff.read("w-0_w-1000000"))
        assertNull(WebHandoff.read("p-_s-x_t-home"))
        assertEquals(LocalDate.of(2026, 11, 10), WebHandoff.read("p-D_s-20261110_s-20261399")!!.first)
        assertNull(WebHandoff.read("p-D_s-18001110")!!.first)
        assertEquals("웹에서 보던 주오야당비휴로 채울까요?", WebHandoff.read("DENXBO")!!.question())
        assertEquals("웹에서 보던 3교대(8시간)으로 채울까요?", WebHandoff.read("DEN")!!.question("3교대(8시간)"))
        assertEquals("웹에서 보던 주말 근무로 채울까요?", WebHandoff.read("DD")!!.question("주말 근무"))
    }

    @Test fun `the encoding reads back, and the app's coworker link opens the site's calendar`() {
        val day = LocalDate.of(2026, 11, 10)
        val text = WebHandoff.content("DNBO", day, once = true, hourly = 10_320, target = WebHandoff.SLIP)
        assertEquals("p-DNBO_s-20261110_o-1_w-10320_t-slip", text)
        assertEquals(WebHandoff("DNBO", day, true, 10_320, WebHandoff.SLIP), WebHandoff.read(text))
        assertEquals("w-10320", WebHandoff.content(first = day, once = true, hourly = 10_320))
        assertEquals("https://bamnat.app/shift/?p=DNBO&s=2026-11-10", WebHandoff.shiftPage("DNBO", day))
    }

    /** The site writes what this reads: the same format in calc.js (Bamnat.handoff) as here. */
    @Test fun `the site's calc js writes the same parts`() {
        val js = File("../tools/site/static/calc/calc.js").readText()
        listOf("'p-'", "'s-'", "'o-1'", "'w-'", "'t-'").forEach { assertTrue("calc.js handoff has $it", it in js) }
    }
}
