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
}
