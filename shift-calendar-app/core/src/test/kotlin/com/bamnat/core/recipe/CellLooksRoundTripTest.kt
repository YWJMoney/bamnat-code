package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [RVF-01] 개발 전 검토 REST-01: '처음대로'(빈) 모양이 저장 글 왕복에서 빠지면 뒤 모양 번호가 당겨지고, 모양이 하나만 남으면
 * '주말 강조'가 평일까지 들어가요. 빈 모양은 자리를 지켜야 해요.
 */
class CellLooksRoundTripTest {
    private val weekend = CellLook(bg = "FFFFF1E6", border = "thin", borderColor = 0xFFF2994AL, dateBold = true)
    private val grey = CellLook(bg = "FFF1F3F9", corner = 4)

    private fun roundTrip(v: CalendarView) = CalendarView.decode(v.encode())

    @Test fun anEmptyLookKeepsItsPlaceFirstMiddleAndLast() {
        for (looks in listOf(
            listOf(CellLook(), weekend),
            listOf(weekend, CellLook(), grey),
            listOf(weekend, CellLook()),
            listOf(CellLook(), CellLook()),
        )) {
            assertEquals(looks, CellLook.decodeAll(CellLook.encodeAll(looks)))
            val v = CalendarView(looks = looks, pattern = CellPattern(CellPattern.CYCLE, n = 2))
            assertEquals(v, roundTrip(v))
        }
        // Nothing written is no looks; old words still read the same.
        assertEquals(emptyList<CellLook>(), CellLook.decodeAll(null))
        assertEquals(emptyList<CellLook>(), CellLook.decodeAll(""))
        assertEquals(listOf(CellLook(), grey), CellLook.decodeAll("/bg:FFF1F3F9|cr:4"))
    }

    @Test fun weekendHighlightStaysOnTheWeekendAfterSaving() {
        // '주말 강조': 처음대로 + 주말 모양, 토·일만 모양 2.
        val v = roundTrip(CalendarView(looks = listOf(CellLook(), weekend), pattern = CellPattern(CellPattern.WEEK, week = listOf(0, 0, 0, 0, 0, 1, 1))))
        assertEquals(2, v.looks.size)
        assertNull(v.lookFor(0L, 1, null, today = false)) // 월요일은 앱 그대로
        assertEquals(weekend, v.lookFor(0L, 6, null, today = false))
        assertEquals(weekend, v.lookFor(0L, 7, null, today = false))
    }

    @Test fun clearingAMiddleLookDoesNotMoveTheOthers() {
        // 모양 3개 중 가운데를 '처음대로' → 3번은 여전히 3번.
        val v = roundTrip(CalendarView(looks = listOf(weekend, CellLook(), grey), pattern = CellPattern(CellPattern.CYCLE, n = 3)))
        assertEquals(listOf(weekend, CellLook(), grey), v.looks)
        assertEquals(grey, v.lookFor(2L, 1, null, today = false))
    }
}
