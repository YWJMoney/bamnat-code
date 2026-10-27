package com.bamnat.core

import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.Risk
import com.bamnat.core.stats.PhotoScreen
import com.bamnat.core.stats.PhotoWrongBook
import com.bamnat.core.stats.PhotoWrongReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [PHOTO-09] '사진이 달라요' counts: only day, screen, reason and a number; what went comes off; old days are dropped. */
class PhotoWrongBookTest {
    private val day = LocalDate.of(2026, 10, 27).toEpochDay()
    private fun iso(d: Long) = LocalDate.ofEpochDay(d).toString()

    @Test fun countsGoAsDayScreenReasonAndNumberOnly() {
        val b = PhotoWrongBook()
            .record(PhotoScreen.PAYSLIP, PhotoWrongReason.NUMBERS, day)
            .record(PhotoScreen.PAYSLIP, PhotoWrongReason.NUMBERS, day)
            .record(PhotoScreen.ROSTER, PhotoWrongReason.MISSING, day - 1)
        assertEquals(3, b.waiting)
        assertEquals(
            "{\"v\":1,\"app\":\"1.29.0\",\"counts\":[{\"day\":\"2026-10-26\",\"screen\":\"roster\",\"reason\":\"missing\",\"n\":1}," +
                "{\"day\":\"2026-10-27\",\"screen\":\"payslip\",\"reason\":\"numbers\",\"n\":2}]}",
            b.bundle("1.29.0", ::iso),
        )
        assertEquals(b, PhotoWrongBook.decode(b.encode()))
        assertNull(PhotoWrongBook().bundle("1.29.0", ::iso))
    }

    @Test fun pressesMadeWhileSendingStay() {
        val b = PhotoWrongBook().record(PhotoScreen.RULES, PhotoWrongReason.NAMES, day)
        val going = b.toSend()
        val later = b.record(PhotoScreen.RULES, PhotoWrongReason.NAMES, day).record(PhotoScreen.INVEST, PhotoWrongReason.OTHER, day)
        val left = later.sent(going)
        assertEquals(2, left.waiting)
        assertEquals(1, left.counts[PhotoWrongBook.Key(day, PhotoScreen.RULES, PhotoWrongReason.NAMES)])
    }

    @Test fun oldDaysAndBrokenRowsAreDropped() {
        val b = PhotoWrongBook().record(PhotoScreen.TAX, PhotoWrongReason.NOTHING, day - 100).record(PhotoScreen.TAX, PhotoWrongReason.NOTHING, day)
        assertEquals(1, b.waiting)
        assertEquals(0, PhotoWrongBook.decode("x\ty\n1\tnope\tnumbers\t3\n2\tpayslip\tnumbers\t-1").waiting)
    }

    @Test fun theFlagIsLegalAndOffUntilPolicy33() {
        val f = FlagCatalog.PHOTO_WRONG_FLAG
        assertEquals(Risk.LEGAL, f.risk)
        assertFalse(f.default)
        assertTrue(f in FlagCatalog.ALL)
        assertTrue(f.basis.policy!!.startsWith("3.3"))
    }
}
