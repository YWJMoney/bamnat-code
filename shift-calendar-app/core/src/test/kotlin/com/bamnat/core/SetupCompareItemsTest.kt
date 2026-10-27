package com.bamnat.core

import com.bamnat.core.pay.ItemShare
import com.bamnat.core.plaza.CompareRow
import com.bamnat.core.plaza.SetupCompare
import com.bamnat.core.plaza.SetupEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ITEM-04][ITEM-09] 1.30 묶음 BC: what a proposal that adds, takes out or changes an allowance says, line by line, and that
 * no amount is ever in its words (the server refuses one: personal()).
 */
class SetupCompareItemsTest {
    private val company = """{"app":"com.bamnat.app","format":"bamnat-company","v":2,"company":"",
        "pay":{"fiveOrMore":true,"monthlyHours":209,"withAmounts":false,"rules":{"night":0.5},
          "items":[{"id":"c1","name":"식대","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[]},
            {"id":"c2","name":"자격수당","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[]}]}}"""
    private val e = SetupEdit.of(company)!!

    private fun rows(after: SetupEdit) = SetupCompare.rows(company, after.text).filter { !it.same }

    @Test fun `added and taken out allowances read as such`() {
        val added = rows(e.addItem("위험예지수당")).single()
        assertEquals(CompareRow.State.ADDED, added.state)
        assertEquals("allowance", added.field)
        val removed = rows(e.removeItem("자격수당")).single()
        assertEquals(CompareRow.State.REMOVED, removed.state)
        // A rename is the old name taken out and the new one added.
        assertEquals(listOf(CompareRow.State.REMOVED, CompareRow.State.ADDED).sorted(), rows(e.renameItem("식대", "중식대")).map { it.state }.sorted())
    }

    @Test fun `a company amount is never in the proposal's words`() {
        val lines = SetupCompare.proposalLines(rows(e.itemShare("식대", ItemShare.FIXED, fixedAmount = 200_000)))
        val l = lines.single()
        assertEquals("식대: 매달", l.now)
        assertEquals("식대: 매달 회사 공통 정액", l.want)
        assertFalse(l.want.contains("200"))
        // Only the amount moves: the words say so instead of reading the same.
        val before = e.itemShare("식대", ItemShare.FIXED, fixedAmount = 200_000).text
        val after = SetupEdit.of(before)!!.itemShare("식대", ItemShare.FIXED, fixedAmount = 250_000).text
        val moved = SetupCompare.proposalLines(SetupCompare.rows(before, after)).single()
        assertEquals("식대: 매달 회사 공통 정액", moved.now)
        assertEquals("식대: 매달 회사 공통 정액 (금액 바뀜)", moved.want)
    }

    @Test fun `a percent and 통상임금 read in the server's words`() {
        val l = SetupCompare.proposalLines(rows(e.itemShare("자격수당", ItemShare.PERCENT, percent = 0.05).itemOrdinary("자격수당", true))).single()
        assertEquals("자격수당: 매달 기본급의 5% · 통상임금", l.want)
        assertTrue(SetupCompare.summary(SetupCompare.rows(company, e.removeItem("식대").text))!!.contains("식대"))
    }

    @Test fun `hours are their own line`() {
        val l = SetupCompare.proposalLines(rows(e.monthlyHours(180))).single()
        assertEquals("hours", l.field)
        assertEquals("월 소정 시간: 180시간", l.want)
    }
}
