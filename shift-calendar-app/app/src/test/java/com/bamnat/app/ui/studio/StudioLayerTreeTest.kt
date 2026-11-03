package com.bamnat.app.ui.studio

import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [LAY-09] 누락 점검 N6: 곳 줄 자주 쓰는 6곳, 층 나무. */
class StudioLayerTreeTest {
    private val order = listOf("CALENDAR", "FEATURES", "MONEY", "MEMO", "STATS", "PAY", "PLAZA", "SETTINGS", "WIDGET", "ALARM", "GUARDED", "ALL")

    @Test fun aNewStudioShowsTheStartSixInItsOwnOrder() {
        assertEquals(listOf("CALENDAR", "FEATURES", "MONEY", "MEMO", "STATS", "ALL"), AreaRank.shown(order, emptyMap(), "CALENDAR"))
    }

    @Test fun theMostUsedComeInAndTheOpenOneStays() {
        val uses = mapOf("WIDGET" to 5, "PLAZA" to 3, "CALENDAR" to 9, "MEMO" to 1)
        val shown = AreaRank.shown(order, uses, "GUARDED")
        assertTrue("WIDGET" in shown && "PLAZA" in shown && "GUARDED" in shown)
        assertEquals(7, shown.size)
        assertEquals(order.filter { it in shown }, shown)
    }

    @Test fun countsAreKeptAndHalvedWhenOld() {
        val m = AreaRank.used(AreaRank.used(emptyMap(), "MEMO"), "MEMO")
        assertEquals(mapOf("MEMO" to 2), AreaRank.decode(AreaRank.encode(m)))
        assertEquals(mapOf("MEMO" to 500, "PAY" to 2), AreaRank.used(mapOf("MEMO" to 1000, "PAY" to 4), "MEMO"))
        assertEquals(emptyMap<String, Int>(), AreaRank.decode("x,:,a:b"))
    }

    @Test fun theTreeNestsPartsAndListsDecorFrontFirst() {
        val bp = Blueprint.decode("""[{"id":"cal.head","fill":4294901760},{"id":"cal.cell","on":"today","fill":4278190335}]""")
        val decor = Decor(listOf(
            DecorItem(DecorKind.EMOJI, "💰", Decor.CELL, on = "d25"),
            DecorItem(DecorKind.TEXT, "힘내기", Decor.HEAD, lock = true),
            DecorItem(DecorKind.EMOJI, "⭐", Decor.CELL),
        ))
        val rows = treeRows(listOf("cal", "cal.head", "cal.head.title", "cal.cell"), bp, decor, listOf(Decor.HEAD, Decor.CELL))
        val head = rows.first { it.part == "cal.head" }
        assertEquals("● 달력 머리", head.label)
        assertEquals(2, head.depth)
        assertEquals(3, rows.first { it.part == "cal.head.title" }.depth)
        assertEquals("오늘", rows.first { it.part == "cal.cell" }.sub)
        val cell = rows.dropWhile { it.label != Decor.anchorLabel(Decor.CELL) }
        assertEquals("2개", cell.first().sub)
        assertEquals(listOf(2, 0), cell.drop(1).mapNotNull { it.decor })
        assertEquals("매달 25일", cell[2].sub)
        assertEquals("잠금", rows.first { it.decor == 1 }.sub)
    }

    /** PC 확인 8d0cddf6: the studio's own frame (제목·칩·저장) never wears a screen's photo or 꾸밈 층. */
    @org.junit.Test fun studioFrameStaysPlain() {
        org.junit.Assert.assertEquals(com.bamnat.core.recipe.ScreenLooks.GUARDED,
            com.bamnat.app.ui.theme.screenKeyOf("studio?start={start}", com.bamnat.core.work.WorkStyle.SHIFT))
    }
}
