package com.bamnat.core

import com.bamnat.core.plaza.PublicFirm
import com.bamnat.core.plaza.PublicFirmWords
import com.bamnat.core.plaza.PublicFirms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [J133-88] 공개 자료 (국민연금 가입 사업장): the name matched as the server does, and the words said of a workplace. */
class PublicFirmsTest {
    private val steel = PublicFirm("현대제철(주)", "인천 동구", "1차 철강 제조업", 11_000, 6_100_000)
    private val found = PublicFirms("2026-09", "국민연금공단", listOf(steel, PublicFirm("현대제철정비(주)", "충남 당진시", "", 40, 3_900_000)))

    @Test fun `the name matches however it is written, and only the very name`() {
        assertEquals("현대제철", PublicFirmWords.norm("（주）현대 제철"))
        assertEquals("현대제철", PublicFirmWords.norm("주식회사 현대제철"))
        assertEquals(steel, PublicFirmWords.exact(found, "현대제철"))
        assertNull(PublicFirmWords.exact(found, "현대"))
        assertNull(PublicFirmWords.exact(null, "현대제철"))
        // Two workplaces of the same name: not guessed.
        assertNull(PublicFirmWords.exact(found.copy(firms = found.firms + steel.copy(region = "충남 당진시")), "현대제철"))
    }

    @Test fun `the facts are the record's, in plain words`() {
        assertEquals("국민연금 가입 11,000명 · 1인 평균 월 약 610만원", PublicFirmWords.facts(steel))
        assertEquals("인천 동구 · 1차 철강 제조업", PublicFirmWords.where(steel))
        assertEquals("2026년 9월", PublicFirmWords.month("2026-09"))
        assertEquals("국민연금 가입 7명", PublicFirmWords.facts(steel.copy(members = 7, average = 0)))
        assertTrue(PublicFirmWords.fiveOrMore(steel))
        assertFalse(PublicFirmWords.fiveOrMore(steel.copy(members = 4)))
    }
}
