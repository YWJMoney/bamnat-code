package com.bamnat.core.invest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVK-17] (검토 U06b-02·MONEY-63) 알림으로 찾은 거래: 어느 계좌로 가는지, 확실한 것 모두 적기. 이름은 지어낸 것이에요. */
class FoundTradesK200Test {
    private fun fill(app: String, price: Double = 70_000.0, amount: Double = 0.0) =
        TradeAlerts.Pending(1_000L, app, Side.BUY, "005930", "삼성전자", 10.0, price, Market.KR_STOCK, amount = amount)

    private val kiwoom = InvestAccount(id = 1, name = "주식 계좌", broker = "키움증권")
    private val toss = InvestAccount(id = 2, name = "토스", broker = "")
    private val pension = InvestAccount(id = 3, name = "연금저축", broker = "한국투자증권")

    @Test fun theAppsBrokerPicksTheAccount() {
        val all = listOf(kiwoom, toss, pension)
        assertEquals(1L, TradeAlerts.accountFor(fill("키움증권"), all, last = 2))
        assertEquals(1L, TradeAlerts.accountFor(fill("키움 증권"), all, last = 2))
        assertEquals(2L, TradeAlerts.accountFor(fill("토스증권"), all, last = 1))
        assertEquals(3L, TradeAlerts.accountFor(fill("한국투자증권"), all, last = 1))
        // No broker named: the one last written to, else the first shown one.
        assertEquals(2L, TradeAlerts.accountFor(fill("카카오톡"), all, last = 2))
        assertEquals(1L, TradeAlerts.accountFor(fill("카카오톡"), all, last = null))
        // A hidden account is never picked.
        assertEquals(2L, TradeAlerts.accountFor(fill("키움증권"), listOf(kiwoom.copy(hidden = true), toss), last = 1))
        assertNull(TradeAlerts.accountFor(fill("키움증권"), emptyList(), last = null))
    }

    @Test fun twoAccountsAtOneBrokerGoToTheLastOne() {
        val second = InvestAccount(id = 4, name = "키움 ISA", broker = "키움")
        assertEquals(4L, TradeAlerts.accountFor(fill("키움증권"), listOf(kiwoom, second), last = 4))
        assertEquals(1L, TradeAlerts.accountFor(fill("키움증권"), listOf(kiwoom, second), last = 1))
    }

    @Test fun sureMeansAPriceThatAgrees() {
        assertTrue(TradeAlerts.sure(fill("키움")))
        assertTrue(TradeAlerts.sure(fill("키움", amount = 700_000.0)))
        assertFalse(TradeAlerts.sure(fill("키움", price = 0.0)))
        assertFalse(TradeAlerts.sure(fill("키움", amount = 900_000.0)))
    }
}
