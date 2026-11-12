package com.bamnat.core

import com.bamnat.core.plaza.CompanyNames
import com.bamnat.core.plaza.CompanyNames.Problem
import com.bamnat.core.plaza.CompanyNames.Why
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVN-19] 검토 COMP-20·I10ab-01·U10a-11·T10a-10 회사 이름 도우미. */
class CompanyNamesTest {
    private val known = listOf("현대제철", "(주)예시정밀", "삼성전자", "삼성전기", "한별정밀", "GS칼텍스", "쿠팡", "가나상사", "현대건설")

    private fun like(typed: String) = CompanyNames.similar(typed, known, { listOf(it) })

    @Test fun aNameStillBeingTypedOrOnlyChoseongCannotBecomeACompany() {
        assertEquals(Problem.UNFINISHED, CompanyNames.problem("현대ㅈ"))
        assertEquals(Problem.UNFINISHED, CompanyNames.problem("한별정미ㄹ"))
        assertEquals(Problem.CHOSEONG, CompanyNames.problem("ㅎㄷㅈㅊ"))
        assertEquals(Problem.SHORT, CompanyNames.problem("현"))
        assertEquals(Problem.SHORT, CompanyNames.problem("(주) 가"))
        assertNull(CompanyNames.problem("쿠팡"))
        assertNull(CompanyNames.problem("KT"))
        // Two letters is a second look, never a refusal.
        assertTrue(CompanyNames.short("쿠팡"))
        assertFalse(CompanyNames.short("현대제철"))
        assertFalse(CompanyNames.short("현"))
    }

    @Test fun aSiteWrittenAfterTheNameIsTakenApart() {
        assertEquals("현대제철" to "당진공장", CompanyNames.split("현대제철 당진공장"))
        assertEquals("현대제철" to "당진 공장", CompanyNames.split("현대제철 당진 공장"))
        assertEquals("삼성전자" to "기흥캠퍼스", CompanyNames.split("삼성전자 기흥캠퍼스"))
        assertEquals("이마트" to "성수점", CompanyNames.split("이마트 성수점"))
        assertEquals("현대건설" to "평택현장", CompanyNames.split("현대건설 평택현장"))
        // Not a 사업장's word, one word only, or no name left before it.
        assertNull(CompanyNames.split("현대 제철"))
        assertNull(CompanyNames.split("현대제철당진공장"))
        assertNull(CompanyNames.split("가 공장"))
        assertNull(CompanyNames.split("현대 공장"))
    }

    @Test fun theSameNameWrittenAnotherWayComesFirst() {
        val l = like("예시 정밀")
        assertEquals("(주)예시정밀", l.first().name)
        assertEquals(Why.SAME, l.first().why)
    }

    @Test fun theKnownNameWithASiteAfterItIsOfferedWithTheSite() {
        val spaced = like("현대제철 당진")
        assertEquals("현대제철", spaced.first().name)
        assertEquals(Why.SITE, spaced.first().why)
        assertEquals("당진", spaced.first().site)
        val joined = like("현대제철당진공장")
        assertEquals(Why.SITE, joined.first().why)
        assertEquals("당진공장", joined.first().site)
        // Joined without a 사업장's word: a part of it, the box left to the user.
        val part = like("현대제철당진")
        assertEquals("현대제철", part.first().name)
        assertEquals(Why.PART, part.first().why)
        assertEquals("", part.first().site)
        assertEquals("‘당진공장’은 사업장 칸에 넣어요", CompanyNames.reason(Why.SITE, "당진공장"))
        assertEquals("‘당진’은 사업장 칸에 넣어요", CompanyNames.reason(Why.SITE, "당진"))
        assertEquals("‘성수점’은 사업장 칸에 넣어요", CompanyNames.reason(Why.SITE, "성수점"))
        assertEquals("‘평택 2라인’은 사업장 칸에 넣어요", CompanyNames.reason(Why.SITE, "평택 2라인"))
        assertEquals("‘기흥’은 사업장 칸에 넣어요", CompanyNames.reason(Why.SITE, "기흥"))
        assertEquals("‘서울센터’는 사업장 칸에 넣어요", CompanyNames.reason(Why.SITE, "서울센터"))
    }

    @Test fun aNameCutShortFindsTheWholeOne() {
        val l = like("현대제")
        assertEquals("현대제철", l.first().name)
        assertEquals(Why.PART, l.first().why)
        assertEquals(listOf("현대제철"), like("대제철").map { it.name })
        // A letter still being typed is left out.
        assertEquals("현대제철", like("현대제ㅊ").first().name)
    }

    @Test fun oneOrTwoLettersApartAreOffered() {
        assertEquals(Why.NEAR, like("현대재철").first().why)
        assertEquals("현대제철", like("현대재철").first().name)
        // 삼성전지: both 전자 and 전기 are one letter away, the more used (first) first.
        assertEquals(listOf("삼성전자", "삼성전기"), like("삼성전지").map { it.name })
        assertEquals("한별정밀", like("한별증밀").first().name)
        assertEquals("GS칼텍스", like("gs칼택스").first().name)
        // Too far: nothing.
        assertTrue(like("대한항공").isEmpty())
    }

    @Test fun choseongFindsTheNamesItStarts() {
        val l = like("ㅎㄷㅈㅊ")
        assertEquals("현대제철", l.single().name)
        assertEquals(Why.CHOSEONG, l.single().why)
        // The same 초성 with other vowels.
        assertTrue(like("한대지철").any { it.name == "현대제철" })
    }

    @Test fun threeAtMostTheBestFirst() {
        val many = listOf("현대제철", "현대제철소", "현대제철판", "현대제철공", "현대재철")
        val l = CompanyNames.similar("현대제철", many, { listOf(it) })
        assertEquals(3, l.size)
        assertEquals("현대제철", l.first().name)
        assertEquals(Why.SAME, l.first().why)
    }

    @Test fun otherNamesOfACompanyCountButItsOwnNameIsShown() {
        data class Co(val name: String, val alias: String)
        val l = CompanyNames.similar("지에스칼텍스", listOf(Co("GS칼텍스", "지에스칼텍스")), { listOf(it.name, it.alias) })
        assertEquals("GS칼텍스", l.single().name)
        assertEquals(Why.SAME, l.single().why)
    }

    @Test fun nothingForTooLittleTyped() {
        assertTrue(like("현").isEmpty())
        assertTrue(like("").isEmpty())
    }
}
