package com.bamnat.app.ui.calendar

import com.bamnat.app.share.SharedDay
import com.bamnat.app.share.SharedSchedule
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** 1.39 PC a483ca15: '함께 받은 것' names only what came, as the web page does. */
class ShowsTextTest {
    private val d = LocalDate.of(2026, 11, 2)
    private fun day(memo: String? = null, leave: String? = null) = SharedDay(d, 1L, null, leave, memo, emptyList(), false)
    private fun sched(vararg days: SharedDay, shows: Set<String>) =
        SharedSchedule("팀", emptyList(), days.associateBy { it.date }, emptyList(), shows)

    @Test fun anEmptyKindIsLeftOut() {
        assertEquals(null, sched(day(), shows = setOf("memos", "leaveKinds", "extras")).showsText())
    }

    @Test fun aLeadersNotesAreNamedForWhatTheyAre() {
        assertEquals("바뀐 날 표시, 같이 쉬는 사람", sched(day(memo = "바뀜: 주→야 · 같이 쉬어요: 민수"), shows = setOf("memos")).showsText())
        assertEquals("메모, 바뀐 날 표시", sched(day(memo = "병원 · 바뀜: 주→야"), shows = setOf("memos")).showsText())
    }

    @Test fun leaveKindsCountOnlyWhenThereIsLeave() {
        assertEquals("휴가 종류", sched(day(leave = "연차"), shows = setOf("leaveKinds")).showsText())
    }
}
