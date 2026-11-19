package com.bamnat.app.company

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2.0.0 [RVJ-82] 결정 3 (처리방침 3.14 12번): a version's card in '지난달 명세서로 맞추기' says how many matched it on a payslip (the count the
 * server keeps since 1.32 [PAYA-06], no amounts), from the 3.14 day only; a version nobody checked so says nothing of it.
 */
class WayPickSlipsTest {
    private fun setup(id: String, dept: String, version: Int?, persons: Int, checked: Int) =
        SharedSetup(id, false, persons, persons, 0, 3, dept, version, "{}", emptyList(), checked = checked)

    @Test fun theCountFollowsTheMatchesOnlyFrom314() {
        val a = setup("a", "생산팀", 2, 3, 4)
        val b = setup("b", "생산팀", 1, 0, 0)
        val all = listOf(a, b)
        assertEquals("생산팀 · 2판 · 맞아요 3", WayPicker.label(a, all, slips = false))
        assertEquals("생산팀 · 2판 · 맞아요 3 · 명세서로 맞춰 본 분 4명", WayPicker.label(a, all, slips = true))
        assertEquals("생산팀 · 1판", WayPicker.label(b, all, slips = true))
        assertEquals("부서 없음 · 명세서로 맞춰 본 분 1명", WayPicker.label(setup("c", "", null, 0, 1), listOf(setup("c", "", null, 0, 1)), slips = true))
    }
}
