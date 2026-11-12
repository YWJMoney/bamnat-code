package com.bamnat.app.company

import com.bamnat.app.ui.company.liftedQuestion
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.plaza.PlazaTag
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * 2.0.0 [RVN-18] 검토 COMP-18·COMP-23: the question lifted on top of 전체 is someone else's, still waiting, 1 to 7 days old, not lifted
 * on this phone before, and one the reader can answer first (the same 근무형태). Before, the oldest unanswered one, weeks old, every time.
 */
class LiftedQuestionTest {
    private val now = Instant.parse("2026-11-20T03:00:00Z")
    private fun q(id: String, daysAgo: Double, who: Int = 1, mine: Boolean = false, form: WorkForm? = null, parent: String? = null) =
        TalkPost(id, who, null, false, id, now.minusSeconds((daysAgo * 86_400).toLong()), mine, parent = parent,
            kind = if (parent == null) "question" else "talk", tag = PlazaTag(WorkStyle.SHIFT, form = form))

    private val me = PlazaTag(WorkStyle.SHIFT, form = WorkForm.FOUR_TEAM)

    private fun lift(posts: List<TalkPost>, seen: Set<String> = emptySet()) =
        liftedQuestion(posts.filter { it.parent == null }, posts.filter { it.parent != null }.groupBy { it.parent }, me, seen, now)?.id

    @Test fun oneToSevenDaysOldAndStillWaiting() {
        assertNull("under a day: it is still near the top", lift(listOf(q("new", 0.5))))
        assertNull("over a week: not brought back after 지난 글 더 보기", lift(listOf(q("old", 9.0))))
        assertEquals("q", lift(listOf(q("q", 2.0))))
        assertNull("answered by someone else", lift(listOf(q("q", 2.0), q("a", 1.0, who = 2, parent = "q"))))
        assertNull("my own question", lift(listOf(q("q", 2.0, mine = true))))
    }

    @Test fun theOneICanAnswerFirstThenTheLongestWaiting() {
        val posts = listOf(q("older", 6.0), q("mine-form", 2.0, form = WorkForm.FOUR_TEAM), q("other", 3.0, form = WorkForm.THREE_SHIFT))
        assertEquals("mine-form", lift(posts))
        assertEquals("older", lift(posts, seen = setOf("mine-form")))
        assertEquals("other", lift(posts, seen = setOf("mine-form", "older")))
        assertNull(lift(posts, seen = setOf("mine-form", "older", "other")))
    }
}
