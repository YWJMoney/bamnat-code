package com.bamnat.app.ui.pro

import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVO-27] PC-166 (조건 13): 보상 이용권 '받는 때' says only the ways that kind of work can get one. */
class PassesWhenTest {
    @Test fun companyWayOnlyForWorkWithACompany() {
        for (style in WorkStyle.entries) {
            val line = passesWhen(style)
            assertEquals("$style: $line", style.usesPayEngine, "회사 근태" in line)
            assertTrue("$style", "초대한 친구" in line && "작품" in line && "문제 신고" in line)
            assertEquals("$style: $line", emptyList<String>(), TypeWords.leaks(style, line))
        }
        assertTrue(passesWhen(WorkStyle.FIELD).startsWith("받는 때: 초대한 친구"))
        assertFalse("회사" in passesWhen(WorkStyle.INVEST))
    }

    /** 2.0.0 [RVO-27] PC-196: the preview card's 지금 line says 지금 once. */
    @Test fun nowSaidOnce() {
        assertEquals("지금: 모든 기능이 무료로 열려 있어요", nowLine("지금은 모든 기능이 무료로 열려 있어요"))
        assertEquals("지금: 구독 중이에요", nowLine("구독 중이에요"))
    }
}
