package com.bamnat.core

import com.bamnat.core.plaza.ChangeAsk
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.SharePart
import com.bamnat.core.plaza.ShareParts
import com.bamnat.core.plaza.ShareWay
import com.bamnat.core.plaza.ShareWays
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2.0.0 [RVN-22] 검토 I10bc-03 (COMP-44): what goes to the company's plaza is three parts the user decides one by one (근무주기·근무
 * 시간·급여 계산 방식, each 저절로·묻기·안 함), and a change is told apart by part so each asks, holds or stays as its part was chosen.
 */
class SharePartsTest {
    private val raw = PlazaCompareFixture.company
    private val base = SetupEdit.of(raw)!!.text

    @Test fun `a change is the part it touches`() {
        // Another rate: the pay way.
        assertEquals(setOf(SharePart.PAY), ShareParts.changed(base, SetupEdit.of(base)!!.rate("night", 0.6).text))
        // Other hours or break for a shift that stays: its hours.
        assertEquals(setOf(SharePart.TIMES), ShareParts.changed(base, SetupEdit.of(base)!!.shiftTime("주간", "08:00", "20:00", 60).text))
        assertEquals(setOf(SharePart.TIMES), ShareParts.changed(raw, raw.replace("\"end\":\"07:00\",\"break\":60", "\"end\":\"07:00\",\"break\":90")))
        // Another order, a shift added or turned into a day off: the cycle.
        assertEquals(setOf(SharePart.CYCLE), ShareParts.changed(base, SetupEdit.of(base)!!.rotation(listOf("주간", "야간", null, null)).text))
        assertEquals(setOf(SharePart.CYCLE), ShareParts.changed(raw, raw.replace("\"shiftTypes\":[",
            "\"shiftTypes\":[{\"name\":\"모닝\",\"label\":\"모\",\"color\":4281352429,\"start\":\"05:00\",\"end\":\"13:00\",\"break\":30,\"off\":false},")))
        assertEquals(setOf(SharePart.CYCLE), ShareParts.changed(raw, raw.replace(
            "{\"name\":\"야간\",\"label\":\"야\",\"color\":4285291751,\"start\":\"19:00\",\"end\":\"07:00\",\"break\":60,\"off\":false}",
            "{\"name\":\"야간\",\"label\":\"야\",\"color\":4285291751,\"break\":0,\"off\":true}")))
        // Two at once: both; the same file: none.
        assertEquals(setOf(SharePart.TIMES, SharePart.PAY),
            ShareParts.changed(base, SetupEdit.of(base)!!.shiftTime("주간", "08:00", "20:00", 60).rate("night", 0.6).text))
        assertEquals(emptySet<SharePart>(), ShareParts.changed(base, base))
    }

    @Test fun `with nothing before, the cycle and every part the file has`() {
        assertEquals(SharePart.entries.toSet(), ShareParts.changed(null, base))
        val noPay = raw.substringBefore(",\n        \"pay\":") + "}"
        assertEquals(setOf(SharePart.CYCLE, SharePart.TIMES), ShareParts.changed(null, noPay))
    }

    @Test fun `the ask says only the lines of its parts`() {
        val both = SetupEdit.of(base)!!.shiftTime("주간", "08:00", "20:00", 60).rate("night", 0.6).text
        val pay = ShareParts.lines(base, both, setOf(SharePart.PAY)) as ChangeAsk.Result.Changed
        assertEquals(listOf("야간 50% → 60%"), pay.lines)
        val times = ShareParts.lines(base, both, setOf(SharePart.TIMES)) as ChangeAsk.Result.Changed
        assertEquals(1, times.lines.size)
        assertEquals(ChangeAsk.Result.First, ShareParts.lines(null, both, setOf(SharePart.PAY)))
        assertEquals(ChangeAsk.Result.Same, ShareParts.lines(base, both, setOf(SharePart.CYCLE)))
    }

    @Test fun `the words name the parts in their own order`() {
        assertEquals("근무주기·급여 계산 방식", ShareParts.words(setOf(SharePart.PAY, SharePart.CYCLE)))
        assertEquals("근무주기·근무 시간·급여 계산 방식", ShareParts.words(SharePart.entries))
    }

    @Test fun `the phone's ways are kept and read back, and a phone from before keeps what it said`() {
        val w = ShareWays(ShareWay.AUTO, ShareWay.ASK, ShareWay.NEVER)
        assertEquals("CYCLE=AUTO,TIMES=ASK,PAY=NEVER", w.encode())
        assertEquals(w, ShareWays.decode(w.encode()))
        assertNull(ShareWays.decode(""))
        assertNull(ShareWays.decode("CYCLE=SOMETIMES"))
        assertNull(ShareWays.decode("WHAT=AUTO"))
        assertEquals(setOf(SharePart.CYCLE), w.of(ShareWay.AUTO))
        assertEquals(setOf(SharePart.TIMES, SharePart.PAY), w.with(SharePart.TIMES, ShareWay.NEVER).of(ShareWay.NEVER))
        // Before 2.0.0: null asked about everything, true 저절로 for the cycle and its hours (the pay way only after its own yes), false never.
        assertEquals(ShareWays(), ShareWays.fromOld(null, false))
        assertEquals(ShareWays(ShareWay.AUTO, ShareWay.AUTO, ShareWay.ASK), ShareWays.fromOld(true, false))
        assertEquals(ShareWays(ShareWay.AUTO, ShareWay.AUTO, ShareWay.AUTO), ShareWays.fromOld(true, true))
        assertEquals(ShareWays(ShareWay.NEVER, ShareWay.NEVER, ShareWay.NEVER), ShareWays.fromOld(false, true))
        // The one switch the older screens still ask is the cycle's way.
        assertEquals(true, w.legacy)
        assertEquals(null, ShareWays().legacy)
        assertEquals(false, w.with(SharePart.CYCLE, ShareWay.NEVER).legacy)
    }
}
