package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterLearn
import com.bamnat.core.imports.RosterPhotoRead
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipLine
import com.bamnat.core.pay.SlipNames
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.SlipVote
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [READ-17][READ-18][READ-19] 1.27 AJ 순번 447: what a fix on "사진과 같은가요?" teaches, and what it never does. */
class PhotoFixTest {
    @Test fun aPayslipLinesKindTaughtOnceReadsSoNextTime() {
        val before = PayslipReader.read("가족수당 100,000\n기본급 2,000,000\n지급합계 2,100,000")
        val line = before.lines.first { it.name.startsWith("가족") }
        val after = before.copy(lines = before.lines.map { if (it === line) it.copy(role = SlipRole.BONUS) else it })
        val taught = SlipNames.learned(before, after)
        assertEquals(mapOf(SlipNames.key("가족수당") to SlipRole.BONUS), taught)
        val next = SlipNames.apply(PayslipReader.read("가족수당 100,000\n기본급 2,100,000\n지급합계 2,200,000"), taught)
        assertEquals(SlipRole.BONUS, next.lines.first { it.name.startsWith("가족") }.role)
        // An amount the user fixed teaches nothing: amounts change every month.
        val amountOnly = before.copy(lines = before.lines.map { if (it === line) it.copy(amount = 120_000) else it })
        assertTrue(SlipNames.learned(before, amountOnly).isEmpty())
        // Read a little differently next month (아간 for 야간), the same name.
        assertEquals(SlipNames.key("야간근로수당"), SlipNames.key("아간근로수당"))
    }

    @Test fun aRosterWordTeachesOnlyWhenTheReaderWasNotSure() {
        val row = RosterRow("김민지", mapOf(1 to DutyCell.Shift(1), 2 to DutyCell.Shift(4), 3 to DutyCell.Unknown("M")),
            guessed = setOf(2), raw = mapOf(1 to "D", 2 to "OF", 3 to "M"))
        // D read and known exactly: the user changing it is a misread letter, not what D means.
        assertNull(RosterLearn.word(row, 1, DutyCell.Shift(2)))
        // OF guessed as 휴무, the user says 야간: OF teaches.
        assertEquals("OF", RosterLearn.word(row, 2, DutyCell.Shift(3)))
        // M unknown: the user's answer teaches.
        assertEquals("M", RosterLearn.word(row, 3, DutyCell.Shift(5)))
        // The same as read, or taken away: nothing.
        assertNull(RosterLearn.word(row, 2, DutyCell.Shift(4)))
        assertNull(RosterLearn.word(row, 2, null))
    }

    @Test fun theAnchorIsTheReadingWithTheMostKnownCells() {
        val few = RosterPhotoRead(listOf(RosterRow("a", mapOf(1 to DutyCell.Shift(1)))), 0.0, emptyList(), null)
        val many = RosterPhotoRead(listOf(RosterRow("a", (1..5).associateWith { DutyCell.Shift(1) })), 0.0, emptyList(), null)
        val none = RosterPhotoRead(emptyList(), 0.0, emptyList(), RosterPhotoRead.Problem.NO_TEXT)
        assertEquals(1, RosterVote.anchor(listOf(few, many, none)))
        assertEquals(0, RosterVote.anchor(listOf(none)))
    }

    @Test fun aLineOneReadingAloneHasIsLookedAtUnlessTheSumsVouch() {
        val full = "기본급 2,000,000\n식대 200,000\n지급합계 2,200,000"
        val lost = "기본급 2,000,000\n지급합계 2,200,000"
        // 식대 read once: the sum of the lines is the stated total, so it is vouched.
        assertTrue(SlipVote.merge(listOf(full, lost, lost)).sure)
        // Without a stated total nothing vouches: the lines read once are yellow.
        val noTotal = SlipVote.merge(listOf("기본급 2,000,000\n식대 200,000", "기본급 2,000,000"))
        assertFalse(noTotal.sure)
        assertTrue(noTotal.unsure.isNotEmpty())
    }

    @Test fun anAmountReadThreeWaysTheSameIsNotTakenOnCount() {
        // The same last digit lost three times; the one reading that adds up to the stated 지급합계 is the payslip.
        val right = "기본급 2,508,000\n연장근로수당 288,000\n지급합계 2,796,000"
        val lost = "기본급 250,800\n연장근로수당 288,000\n지급합계 2,796,000"
        val r = SlipVote.merge(listOf(lost, lost, lost, right))
        assertEquals(2_508_000L, r.read.lines.first { it.role == SlipRole.BASE }.amount)
        assertTrue(r.sure)
        // No stated total to settle it: 3 to 1 is still yellow, never taken quietly.
        val noTotal = SlipVote.merge(listOf(lost, lost, lost, right).map { it.substringBefore("\n지급합계") })
        assertFalse(noTotal.sure)
        val base = noTotal.read.lines.indexOfFirst { it.role == SlipRole.BASE }
        assertTrue("기본급 read two ways is yellow", base in noTotal.unsure)
    }

    @Test fun anEmptyPayslipWithRulesStillCounts() {
        assertTrue(PayslipRead(emptyList()).lines.isEmpty())
        assertEquals(SlipRole.BASE, PayslipReader.read("기본려 2,000,000").lines.first().role)
        assertEquals(SlipLine("식대", SlipRole.ALLOWANCE, 200_000).role, SlipRole.ALLOWANCE)
    }

    /** [READ-14] A good photo reads on; a dark, blurred one with small letters is asked again, the worst reason first. */
    @Test fun aPoorPhotoIsAskedAgainWithItsReason() {
        val good = com.bamnat.core.imports.PhotoQuality.of(com.bamnat.core.imports.PhotoVariants.Stats(180.0, 160, 20.0, 0.1), 24, 200)
        assertFalse(good.retake)
        assertNull(good.line)
        val poor = com.bamnat.core.imports.PhotoQuality.of(com.bamnat.core.imports.PhotoVariants.Stats(55.0, 50, 4.0, 0.6), 8, 120)
        assertTrue(poor.retake)
        assertEquals(com.bamnat.core.imports.PhotoQuality.Issue.DARK, poor.issues.first())
        assertTrue(poor.line!!.contains("어두워요"))
    }

    /** [READ-13] Light evened and the darkest third of a shadowed page kept readable: print stays darker than paper. */
    @Test fun theShadowIsTakenOutButThePrintStays() {
        val w = 128; val h = 64
        val px = IntArray(w * h) { i -> val x = i % w; val y = i / w
            val paper = if (x < w / 2) 220 else 90 // half the page in shadow
            if (y in 30..33 && x % 16 in 2..9) paper - 70 else paper }
        val g = com.bamnat.core.imports.PhotoVariants.Gray(w, h, px)
        val f = com.bamnat.core.imports.PhotoVariants.flat(g)
        // On the shadowed side the print is still darker than its paper after flattening.
        assertTrue(f[100, 31] < f[100, 10])
        val b = com.bamnat.core.imports.PhotoVariants.binary(g)
        assertTrue(b[100, 31] < b[100, 10])
        assertEquals(w * 2, com.bamnat.core.imports.PhotoVariants.up2(g).w)
    }

    /** [READ-19] A person's line under the 1–15 and 16–30 rows of days keeps the words as read, so a fix still teaches. */
    @Test fun theWordsAsReadSurviveTwoHalves() {
        val codes = com.bamnat.core.imports.DutyCodes(listOf(com.bamnat.core.imports.DutyType(1, "데이", "D", false),
            com.bamnat.core.imports.DutyType(3, "나이트", "N", false, night = true), com.bamnat.core.imports.DutyType(4, "휴무", "O", true)))
        val words = mutableListOf<com.bamnat.core.imports.RosterWord>()
        fun add(t: String, x: Int, y: Int) { words += com.bamnat.core.imports.RosterWord(t, x, y, x + 24, y + 26) }
        for (half in 0..1) {
            val top = half * 200
            add("김민지", 0, top + 60)
            for (i in 0 until 15) {
                val d = half * 15 + i + 1
                add("$d", 120 + i * 60, top)
                add(if (d == 14) "OF" else listOf("D", "N", "O")[d % 3], 120 + i * 60, top + 60)
            }
        }
        val row = com.bamnat.core.imports.DutyRoster.analyse(words, codes).rows.first { it.label.contains("김민지") }
        assertEquals(30, row.cells.size)
        assertEquals("OF", row.raw[14])
        assertEquals(listOf("D", "N", "O")[30 % 3], row.raw[30])
    }
}
