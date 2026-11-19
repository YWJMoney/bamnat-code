package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterForm
import com.bamnat.core.imports.RosterLearn
import com.bamnat.core.imports.RosterRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVL-80] (검토 I07bc-02) The ward's sheet form: finding the user's line on next month's sheet, and nothing it must not find. */
class RosterFormTest {
    private fun row(label: String, line: Int) = RosterRow(label, (1..10).associateWith { DutyCell.Off }, line = line)

    private val sheet = listOf(row("김민지", 0), row("이수진", 1), row("박하늘", 2), row("최유리", 3))

    @Test fun theNameAsItWasComesFirst() {
        assertEquals(1, RosterForm.find(sheet, "이수진", null))
        assertEquals(1, RosterForm.find(sheet.reversed(), "박하늘", null))
    }

    @Test fun aNameOneLetterOffIsMineWhenOnlyOneIsSoClose() {
        val misread = sheet.map { if (it.label == "이수진") it.copy(label = "이수신") else it }
        assertEquals(1, RosterForm.find(misread, "이수진", null))
        // Two lines that close: asked.
        val two = misread + row("이수전", 4)
        assertNull(RosterForm.find(two, "이수진", null))
    }

    @Test fun shortNamesAndTeamsAreNeverGuessed() {
        assertFalse(RosterForm.close("1조", "2조"))
        assertFalse(RosterForm.close("A조", "B조"))
        assertFalse(RosterForm.close("민지", "민시"))
        assertFalse(RosterForm.close("김민지", "김민지 (2)"))
        assertFalse(RosterForm.close("3팀 1조", "3팀 2조"))
        assertTrue(RosterForm.close("김민지", "김 민지"))
        assertTrue(RosterForm.close("Kim Minji", "Kim Mlnji"))
    }

    @Test fun twoOfTheNameAreAskedEvenWithAForm() {
        val twins = listOf(row("김민지", 0), row("김민지 (2)", 1), row("박하늘", 2), row("최유리", 3))
        assertNull(RosterForm.find(twins, "김민지", RosterForm(0, 4)))
    }

    @Test fun aLostNameIsFoundByItsPlaceOnASheetOfAsManyLines() {
        val lost = sheet.map { if (it.label == "이수진") it.copy(label = "") else it }
        val form = RosterForm(rank = 1, lines = 4)
        assertEquals(1, RosterForm.find(lost, "이수진", form))
        // Another person's name in that place is never taken; nor a sheet of other lines.
        val other = sheet.map { if (it.label == "이수진") it.copy(label = "정다은") else it }
        assertNull(RosterForm.find(other, "이수진", form))
        assertNull(RosterForm.find(lost + row("정다은", 4), "이수진", form))
        // A line with no name last month too (3번째 줄): found by its place.
        val nameless = listOf(row("", 2), row("", 0), row("", 1))
        assertEquals(0, RosterForm.find(nameless, null, RosterForm(2, 3)))
    }

    @Test fun theFormKeepsNoNameAndReadsBack() {
        val form = RosterForm.of(sheet.reversed(), 2, 120 to 640)!!
        assertEquals(RosterForm(1, 4, 120, 640), form)
        assertEquals(form, RosterForm.decode(form.encode()))
        assertFalse(form.encode().any { it.isLetter() })
        assertNull(RosterForm.decode("5,4,-1,-1"))
        assertNull(RosterForm.decode("garbage"))
        assertEquals(RosterForm(0, 2), RosterForm.decode("0,2,-1,-1"))
    }

    @Test fun theTapsLandWhereTheyWereOnTheTable() {
        val form = RosterForm(1, 4, dayAt = 100, myAt = 500)
        val hint = RosterForm.hint(form, 0, 2000, 200, 1200)!!
        assertEquals(300, hint.dayRow!!.second)
        assertEquals(700, hint.myRow!!.second)
        assertEquals(500, RosterForm.at(700, 200, 1200))
        assertNull(RosterForm.hint(RosterForm(1, 4), 0, 2000, 200, 1200))
    }

    /** A sheet of five lines, 30 days wide, turned [deg] about the photo's corner; the line [i]'s middle, as a pixel. */
    private fun turned(deg: Double, scale: Double = 1.0, down: Int = 0): Pair<List<com.bamnat.core.imports.RosterWord>, (Int) -> Pair<Int, Int>> {
        val a = Math.toRadians(deg)
        fun at(x: Double, y: Double) = (x * Math.cos(a) - y * Math.sin(a)) * scale to (x * Math.sin(a) + y * Math.cos(a)) * scale + down
        val words = mutableListOf<com.bamnat.core.imports.RosterWord>()
        fun add(t: String, x: Int, y: Int, w: Int) {
            val (cx, cy) = at(x + w / 2.0, y + 20.0)
            val hw = w * scale / 2; val hh = 20 * scale
            words += com.bamnat.core.imports.RosterWord(t, (cx - hw).toInt(), (cy - hh).toInt(), (cx + hw).toInt(), (cy + hh).toInt(), angle = deg.toFloat())
        }
        add("근무표", 610, 0, 120)
        for (d in 1..30) add("·", 300 + (d - 1) * 110, 100, 10)
        for (i in 0 until 5) { add("이름$i", 100, 180 + i * 70, 120); for (d in 1..30) add("D", 300 + (d - 1) * 110, 180 + i * 70, 70) }
        return words to { i -> at(1950.0, 200.0 + i * 70).let { (x, y) -> x.toInt() to y.toInt() } }
    }

    @Test fun aTapOnTheNameOfATurnedPhotoIsItsLineNextMonthToo() {
        // November: turned 1°, the user tapped their name (line 3) at the left edge.
        val (nov, _) = turned(1.0)
        val name = nov.first { it.text == "이름3" }
        val form = RosterForm(3, 5, myAt = RosterForm.at(name.centerX, name.centerY, nov))
        // December: the same sheet turned the other way, bigger and lower; the tap lands on line 3, not a neighbour.
        val (dec, middle) = turned(-0.5, scale = 1.1, down = 60)
        val tap = RosterForm.hint(form, dec)!!.myRow!!
        val gap = 70 * 1.1
        assertTrue("$tap vs ${middle(3)}", Math.abs(tap.second - middle(3).second) < gap / 2)
        assertNull(RosterForm.hint(RosterForm(1, 4), dec))
    }

    @Test fun theCodesSeenRightAreCountedByDay() {
        val r = RosterRow("김민지", mapOf(1 to DutyCell.Shift(1), 2 to DutyCell.Shift(1), 3 to DutyCell.Shift(2), 4 to DutyCell.Shift(2), 5 to DutyCell.Shift(3)),
            guessed = setOf(1, 2, 3, 4, 5), raw = mapOf(1 to "D", 2 to "d", 3 to "N", 4 to "N", 5 to "M"))
        // The user changed day 4 by hand: that N was misread, and only day 3's says N is right as read.
        val seen = RosterLearn.confirmed(r, r.cells + (4 to DutyCell.Shift(1)))
        assertEquals(DutyCell.Shift(1) to 2, seen["D"])
        assertEquals(DutyCell.Shift(2) to 1, seen["N"])
        assertEquals(DutyCell.Shift(3) to 1, seen["M"])
        // A day not read by a guess (the calendar's own name) teaches nothing new.
        assertTrue(RosterLearn.confirmed(r.copy(guessed = emptySet()), r.cells).isEmpty())
    }
}
