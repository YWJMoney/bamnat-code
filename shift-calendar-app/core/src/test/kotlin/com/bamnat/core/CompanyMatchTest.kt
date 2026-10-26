package com.bamnat.core

import com.bamnat.core.plaza.CompanyMatch
import com.bamnat.core.plaza.CycleMatch
import com.bamnat.core.plaza.CycleOrders
import com.bamnat.core.plaza.PublicCycles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PLAZA-17][PLAZA-14][PLAZA-18] 1.27 묶음 AH: 회사 이름 맞춰 찾기, 같은 주기, 오늘 근무로 자리 찾기. */
class CompanyMatchTest {
    @Test fun namesAreFoundTheWayPeopleSayThem() {
        assertEquals("지에스칼텍스", CompanyMatch.spoken("GS칼텍스"))
        assertEquals("에스오일", CompanyMatch.spoken("S-OIL"))
        assertEquals("에스케이하이닉스", CompanyMatch.spoken("sk 하이닉스"))
        assertEquals("포스코", CompanyMatch.spoken("POSCO"))
        assertEquals(listOf("gs칼텍스", "지에스칼텍스"), CompanyMatch.variants("(주) GS 칼텍스"))
        assertEquals(0, CompanyMatch.fit("GS칼텍스", "지에스칼텍스"))
        assertEquals(0, CompanyMatch.fit("GS칼텍스", "gs 칼텍스"))
        assertEquals(1, CompanyMatch.fit("SK하이닉스", "하이닉스"))
        // One 자모 wrong in a name of three syllables or more.
        assertEquals(2, CompanyMatch.fit("GS칼텍스", "칼택스"))
        assertEquals(2, CompanyMatch.fit("유한킴벌리", "유한킴벌라"))
        assertNull("two letters are too few to guess", CompanyMatch.fit("GS칼텍스", "칼택"))
        assertNull(CompanyMatch.fit("GS칼텍스", "한화토탈"))
        assertTrue(CompanyMatch.nearIn("abcdef", "abxdef"))
        assertFalse(CompanyMatch.nearIn("abcdef", "axxdef"))
    }

    @Test fun thePublicSearchUsesIt() {
        assertEquals("GS칼텍스", PublicCycles.search("지에스칼텍스").first().company)
        assertEquals("GS칼텍스", PublicCycles.search("gs 칼텍스").first().company)
        assertEquals("GS칼텍스", PublicCycles.search("칼택스").first().company)
        assertEquals("유한킴벌리", PublicCycles.search("유한킴벌라").first().company)
        // Exact ones still come first: 'SK' finds SK rows before any guess.
        assertTrue(PublicCycles.search("SK").take(3).all { it.company.startsWith("SK") })
        assertTrue(PublicCycles.search("없는회사이름").isEmpty())
    }

    @Test fun theSameCycleBegunOnAnotherDay() {
        val a = listOf("주간", "주간", "휴", "휴", "야간", "야간", "휴", "휴")
        val b = listOf("야간", "야간", "휴", "휴", "주간", "주간", "휴", "휴")
        assertTrue("another 조 of the same order", CycleMatch.same(a, b))
        assertFalse(CycleMatch.same(a, listOf("주간", "주간", "주간", "휴", "휴", "휴", "야간", "야간", "야간", "휴", "휴", "휴")))
        assertFalse(CycleMatch.same(a, listOf("주간", "야간", "휴", "휴", "주간", "야간", "휴", "휴")))
        assertTrue(CycleMatch.sameShape(listOf("주", "야", "휴"), listOf("야간", "휴", "주간")))
        assertEquals(CycleMatch.OFF, CycleMatch.day("비번", off = true))
        assertEquals(CycleMatch.OFF, CycleMatch.day(null, off = false))
        assertFalse(CycleMatch.same(emptyList(), emptyList()))
    }

    @Test fun todaysShiftFindsThePlace() {
        val codes = "DDOONNOO"
        assertEquals(listOf(0, 1), CycleOrders.positions(codes, 'D'))
        assertEquals(listOf(1), CycleOrders.positions(codes, 'D', 'O'))
        assertEquals(listOf(2, 6), CycleOrders.positions(codes, 'O', 'O'))
        assertEquals(listOf(2), CycleOrders.positions(codes, 'O', 'O', 'N'))
        assertEquals(listOf(7), CycleOrders.positions(codes, 'O', 'D'))
        assertEquals(listOf('D', 'O', 'N'), CycleOrders.kinds(codes))
        assertTrue(CycleOrders.positions("", 'D').isEmpty())
    }
}
