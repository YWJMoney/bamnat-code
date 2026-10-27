package com.bamnat.core

import com.bamnat.core.plaza.PayFindRows
import com.bamnat.core.plaza.PayFindRows.Start
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [PAYG-06] 1.30 묶음 BA (사용자 2026-10-27 "급여 적용 부분에서는 근무주기 적용한 회사가 있으면 뜨고, 없으면 검색, 검색에도 없으면
 * 직접 설정하는 흐름"): 급여 방식 찾기 opens on the company the user works for: its way at once when coworkers made one, 직접 만들기
 * when it has none, the box when nothing is known.
 */
class PayFindRowsStartStepTest {
    private val server = listOf(
        PayFindRows.Server("c1", "현대제철", users = 7, pay = true, ok = 4, dept = "당진", setup = "s1"),
        PayFindRows.Server("c2", "현대제철 포항", users = 2),
    )

    private fun rows(known: String, server: List<PayFindRows.Server>?) =
        PayFindRows.merge(PayFindRows.local(known, listOf(known)), server)

    @Test fun theCompanyOfTheCycleWithAPayWayIsOfferedAtOnce() {
        val row = PayFindRows.knownRow("현대제철", rows("현대제철", server))
        assertEquals("c1", row?.serverId)
        assertEquals("s1", row?.setup)
        assertEquals(Start.CONFIRM, PayFindRows.startStep(row))
    }

    @Test fun usingAWayAlreadyIsSaidFirst() {
        val row = PayFindRows.knownRow("현대제철", rows("현대제철", server))
        assertEquals(Start.USING, PayFindRows.startStep(row, using = true))
        assertEquals(Start.USING, PayFindRows.startStep(null, using = true))
    }

    @Test fun aKnownCompanyWithoutAPayWayLeadsToMakingOne() {
        val noPay = listOf(PayFindRows.Server("c2", "현대제철 포항", users = 2))
        val row = PayFindRows.knownRow("현대제철 포항", rows("현대제철 포항", noPay))
        assertEquals("c2", row?.serverId)
        assertEquals(Start.BY_HAND, PayFindRows.startStep(row))
        // Not on the 광장 at all: the user's own name, made by hand, said apart from "급여 방식이 없어요".
        val alone = PayFindRows.knownRow("우리동네정밀", rows("우리동네정밀", emptyList()))
        assertEquals("우리동네정밀", alone?.name)
        assertEquals(Start.BY_HAND, PayFindRows.startStep(alone))
        assertEquals("광장에 아직 없는 회사예요 · 첫 급여 방식을 만들어 주세요", PayFindRows.payLine(alone!!))
    }

    @Test fun nothingKnownOrNoAnswerYetKeepsTheBox() {
        assertEquals(Start.SEARCH, PayFindRows.startStep(null))
        val row = PayFindRows.knownRow("현대제철", rows("현대제철", null))
        // The phone's row only: the server has not said whether there is a pay way.
        assertEquals(Start.SEARCH, PayFindRows.startStep(row, answered = false))
        assertNull(PayFindRows.knownRow("", rows("현대제철", server)))
    }

    @Test fun aWayWithManyDiffersIsNotPutForwardByItself() {
        val doubted = listOf(PayFindRows.Server("c1", "현대제철", users = 7, pay = true, ok = 1, doubted = true, setup = "s1"))
        val row = PayFindRows.knownRow("현대제철", rows("현대제철", doubted))
        assertEquals(Start.SEARCH, PayFindRows.startStep(row))
    }
}
