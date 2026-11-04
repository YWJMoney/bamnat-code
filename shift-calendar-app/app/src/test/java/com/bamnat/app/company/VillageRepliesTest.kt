package com.bamnat.app.company

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/** [TOWN-02] 1.37 내 글에 새 답: others' answers only, nothing new on the first look, new after that, gone once seen. */
@RunWith(AndroidJUnit4::class)
class VillageRepliesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val at = Instant.parse("2026-11-04T12:00:00Z")

    private fun q(id: String, replies: Int, room: String = "style-shift") = MyPost(id, room, "교대 광장", "질문", "question", null, replies, at, false)
    private fun mine(id: String, under: String) = MyPost(id, "style-shift", "교대 광장", "답", "talk", under, 0, at, false)

    @Before fun clean() { context.getSharedPreferences("village_ui", Context.MODE_PRIVATE).edit().clear().commit() }

    @Test fun myOwnRepliesUnderMyQuestionAreNotAnswers() {
        assertEquals(mapOf("a" to 1), VillageReplies.others(listOf(q("a", 3), mine("r1", "a"), mine("r2", "a"))))
    }

    @Test fun firstLookOnlyTakesNoteThenNewAnswersShowUntilSeen() {
        assertTrue(VillageReplies.fresh(context, listOf(q("a", 2))).isEmpty())
        val later = listOf(q("a", 4), q("b", 1))
        assertEquals(mapOf("a" to 2, "b" to 1), VillageReplies.fresh(context, later))
        VillageReplies.markSeen(context, later)
        assertTrue(VillageReplies.fresh(context, later).isEmpty())
    }

    @Test fun hiddenPostsSayNothing() {
        assertTrue(VillageReplies.others(listOf(q("a", 2).copy(hidden = true))).isEmpty())
    }
}
