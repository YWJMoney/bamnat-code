package com.bamnat.core

import com.bamnat.core.pay.OcrLine
import com.bamnat.core.pay.PhotoTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** A 1.32 [FIX132-01] a payslip photo upside down or on its side is read the right way up. Lines are made up. */
class PhotoTurnTest {
    private fun line(text: String, y: Int, angle: Float = 0f) = OcrLine(text, 10, y, 10 + text.length * 30, y + 30, angle)
    private val upright = listOf(line("급여명세서 2026년 10월", 0), line("기본급 2,508,000", 40), line("연장근로수당 288,000", 80),
        line("국민연금 112,500", 120), line("건강보험 88,000", 160), line("실수령액 2,210,400", 200))
    // What a reader makes of the same payslip upside down: letters that are not words, no amounts with commas.
    private val flipped = listOf(line("000'80S'Z モ굴Y", 0, 179f), line("000'88Z 루곡ㄹ근율ㅇ", 40, -178f), line("ㅇ00Sㄹㄹㄴ ㄹ눈ㄷ논", 80, 180f),
        line("00b'0TZ'Z 뉴놀둔룰", 120, 179f))

    @Test fun anUpsideDownPhotoIsTurnedHalfway() {
        assertTrue(PhotoTurn.upsideDown(flipped))
        assertTrue(PhotoTurn.worthTurning(flipped))
        assertEquals(180, PhotoTurn.candidates(flipped).first())
        assertEquals(180, PhotoTurn.pick(flipped, mapOf(180 to upright, 90 to emptyList(), 270 to emptyList())))
    }

    @Test fun aStraightPhotoStays() {
        assertFalse(PhotoTurn.worthTurning(upright))
        // Even if asked, a turn reading about the same is not taken.
        assertEquals(0, PhotoTurn.pick(upright, mapOf(180 to upright.drop(1))))
    }

    @Test fun onItsSideBothQuarterTurnsAreWeighedNotTheFirst() {
        val side = upright.map { it.copy(angle = 90f) }
        assertEquals(listOf(90, 270, 180), PhotoTurn.candidates(side))
        // 90° gives more words but upside down; 270° reads the payslip.
        val more = flipped + flipped
        assertEquals(270, PhotoTurn.pick(side.map { it.copy(text = "ㅁ") }, mapOf(90 to more, 270 to upright)))
    }

    @Test fun theLastTurnThatWorkedIsTriedFirst() {
        assertEquals(listOf(270, 180, 90), PhotoTurn.candidates(flipped, remembered = 270))
        assertEquals(listOf(180, 90, 270), PhotoTurn.candidates(flipped, remembered = 45))
        assertEquals(90, PhotoTurn.add(270, 180))
        assertEquals(0, PhotoTurn.add(270, 90))
    }
}
