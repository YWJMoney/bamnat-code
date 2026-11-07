package com.bamnat.core

import com.bamnat.core.invest.FillSheetReader
import com.bamnat.core.invest.KnownStocks
import com.bamnat.core.invest.Side
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [READ-38] 1.38: a small or photographed 체결 화면 read a stroke off, its rows one off. */
class FillScreenPhotoTest {
    @Test fun rowsJoinedOneOffStillReadEachFillAndAsk() {
        val text = "체결내역\n3:하이닉스\n너드체를  12주\n체결수량  182300월\n체결단가  2187600원\nNAVER\n수해를  4주\n체결수량  205500원\n체결단가  822000원\n체결금액"
        val fills = FillSheetReader.read(text)
        assertEquals(2, fills.size)
        val (a, b) = fills
        assertEquals("SK하이닉스", a.name)
        assertEquals(Side.SELL, a.side)
        assertEquals(12.0, a.quantity, 0.0); assertEquals(182300.0, a.price, 0.0); assertEquals(2187600.0, a.amount, 0.0)
        assertTrue("rows one off are asked", !a.sure)
        assertEquals("NAVER", b.name)
        assertEquals(Side.BUY, b.side)
        assertEquals(4.0, b.quantity, 0.0); assertEquals(205500.0, b.price, 0.0)
    }

    @Test fun knownNamesPutBackOnlyWhenOneIsNear() {
        assertEquals("기아", KnownStocks.near("키아"))
        assertEquals("삼성전자", KnownStocks.near("삼성건자"))
        assertEquals("SK하이닉스", KnownStocks.near("하이니스"))
        assertNull(KnownStocks.near("삼성전자"))
        assertNull(KnownStocks.near("에스엠"))
    }
}
