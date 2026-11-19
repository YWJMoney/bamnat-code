package com.bamnat.app.recipe

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 2.0.0 [RVM-57] 총괄 11-19: '입어 보기로 열기' (오늘의 하나·수상작·넘겨 보기) holds only while the page is on its way. An ask whose page
 * never opened used to stay in memory and open that work's 입어 보기 by itself the next time its page opened.
 */
class TryNextTest {
    @After fun back() {
        RecipeLinks.clock = System::nanoTime
        RecipeLinks.resetForTest()
    }

    @Test fun anAskWhosePageNeverOpenedLapses() {
        var now = 5_000_000_000L
        RecipeLinks.clock = { now }
        RecipeLinks.tryNext = "MKT10003"
        assertEquals("MKT10003", RecipeLinks.tryNext)
        now += (RecipeLinks.ASK_MILLIS - 1) * 1_000_000L
        assertEquals("길을 가는 동안은 그대로", "MKT10003", RecipeLinks.tryNext)
        now += 2_000_000L
        assertNull("한참 뒤에 그 작품을 열면 입어 보기가 저절로 열리지 않아요", RecipeLinks.tryNext)
    }

    @Test fun aNewAskStartsItsOwnTime() {
        var now = 0L
        RecipeLinks.clock = { now }
        RecipeLinks.tryNext = "MKT10001"
        now += RecipeLinks.ASK_MILLIS * 1_000_000L
        RecipeLinks.tryNext = "MKT10002"
        assertEquals("MKT10002", RecipeLinks.tryNext)
        RecipeLinks.tryNext = null
        assertNull(RecipeLinks.tryNext)
    }
}
