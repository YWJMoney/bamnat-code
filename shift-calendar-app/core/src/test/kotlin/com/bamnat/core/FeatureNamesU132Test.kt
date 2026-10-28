package com.bamnat.core

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.moneyTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** U 1.32 [UX132-01][UX132-07] (조사 4.5): the guide's paths say what the screens say. */
class FeatureNamesU132Test {
    @Test fun pathsUseTheScreensWords() {
        val old = listOf("이번 달 급여", "회사 계산 방식", "› 값 고치기", "돈 ›", "돈 관리", "달력 화면 ›")
        for (f in FeatureRegistry.all) for (w in old) assertFalse("${f.id}: ${f.where}", f.where.startsWith(w) || f.where.contains(w) && w != "돈 ›" || f.where.startsWith("돈 ›"))
    }

    @Test fun theMoneyTabIsNamedForTheWork() {
        assertEquals("가계부", moneyTab(null))
        assertEquals("가계부", moneyTab(WorkStyle.SHIFT))
        assertEquals("장부", moneyTab(WorkStyle.BUSINESS))
        assertEquals("장부", moneyTab(WorkStyle.FIELD))
    }
}
