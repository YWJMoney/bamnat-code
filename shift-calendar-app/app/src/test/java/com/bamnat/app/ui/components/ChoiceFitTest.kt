package com.bamnat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [RVH-46] 총괄 #451 사진 검토: a choice row never cuts its words; it goes to two lines, two to a row, or one under another. */
class ChoiceFitTest {
    /** Ten pixels a letter (a space too), as a font that size would measure. */
    private val measure: (String) -> Int = { it.length * 10 }

    @Test fun everyOptionFitsSoOneRowOfOneLine() {
        val l = ChoiceFit.layout(listOf("영업", "단축", "격주", "휴무"), width = 400, pad = 24, measure = measure)
        assertEquals(4, l.perRow)
        assertEquals(listOf("영업", "단축", "격주", "휴무"), l.labels)
    }

    @Test fun aLongOptionBreaksBetweenItsWordsNotInside() {
        // 순서대로 돌아요 is 90 wide; each half of 200 less 24 has 76: two lines, 순서대로 and 돌아요.
        val l = ChoiceFit.layout(listOf("순서대로 돌아요", "요일마다 같아요"), width = 200, pad = 24, measure = measure)
        assertEquals(2, l.perRow)
        assertEquals(listOf("순서대로\n돌아요", "요일마다\n같아요"), l.labels)
    }

    @Test fun fourThatDoNotFitGoTwoToARow() {
        // A quarter of 120 less 24 is 6, under every word; two to a row have 36 each: 격주 휴무 (50) on two lines, the rest on one.
        val l = ChoiceFit.layout(listOf("영업", "단축", "격주 휴무", "휴무"), width = 120, pad = 24, measure = measure)
        assertEquals(2, l.perRow)
        assertEquals(listOf("영업", "단축", "격주\n휴무", "휴무"), l.labels)
    }

    @Test fun whenNothingElseFitsTheyStackAndStillBreakBetweenWords() {
        val l = ChoiceFit.layout(listOf("나갈 돈", "들어올 돈", "카드값·이체"), width = 100, pad = 24, measure = measure)
        assertEquals(1, l.perRow)
        assertEquals(listOf("나갈 돈", "들어올 돈", "카드값·이체"), l.labels)
        val narrow = ChoiceFit.layout(listOf("나갈 돈", "들어올 돈"), width = 60, pad = 24, measure = measure)
        assertEquals(1, narrow.perRow)
        assertEquals(listOf("나갈\n돈", "들어올\n돈"), narrow.labels)
    }

    @Test fun aWordWiderThanTheRoomIsNeverSplit() {
        assertNull(ChoiceFit.lines("카드값·이체", room = 40, width = measure))
        assertEquals(listOf("시작", "기준"), ChoiceFit.lines("시작 기준", room = 30, width = measure))
    }
}
