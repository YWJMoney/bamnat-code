package com.bamnat.app.memo

import android.content.Context
import android.content.Intent
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.core.time.Today
import java.time.LocalDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 1.39 [TASK-38][TASK-32] 한 줄로 할 일 적기: the chips the user leaves or changes, and a text shared from another app. */
@RunWith(AndroidJUnit4::class)
class QuickTaskTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val now = LocalDateTime.of(2026, 11, 7, 19, 17)

    @Test fun theChipsDecideTheDayAndTheRing() {
        val read = quickMemo("내일 저녁 6시 치과", QuickDay.READ, ring = true, now)!!
        assertEquals("치과", read.title)
        assertEquals(now.toLocalDate().plusDays(1), read.date)
        assertEquals(now.toLocalDate().plusDays(1).atTime(18, 0), read.reminder?.at)
        assertTrue("a to-do notifies, it does not ring like an alarm", read.reminder!!.notify && !read.reminder!!.ring)
        // Moved to 오늘: the time read comes along to today; a time already past today does not ring.
        assertEquals(now.toLocalDate().atTime(21, 0), quickMemo("모레 밤 9시 운동", QuickDay.TODAY, ring = true, now)!!.reminder?.at)
        assertNull(quickMemo("내일 저녁 6시 치과", QuickDay.TODAY, ring = true, now)!!.reminder)
        assertNull("알림 off", quickMemo("내일 저녁 6시 치과", QuickDay.READ, ring = false, now)!!.reminder)
        val none = quickMemo("우유 사기", QuickDay.NONE, ring = true, now)!!
        assertNull(none.date)
        assertTrue("a line with no day is kept at hand, pinned", none.pinned)
        assertEquals("a plain line is for today", now.toLocalDate(), quickMemo("우유 사기", QuickDay.READ, ring = true, now)!!.date)
        assertNull(quickMemo("  ", QuickDay.READ, ring = true, now))
    }

    @Test fun aSharedTextIsWrittenOnlyWhenTheUserSaysSo() = runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val before = repo.memos.first().size
        val share = Intent(context, QuickTaskActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "모레 3시 회의\n3층 회의실")
        ActivityScenario.launch<QuickTaskActivity>(share).use { scenario ->
            compose.onNodeWithText("제목: 회의", substring = true).assertExists()
            assertEquals("nothing saved before 적기", before, repo.memos.first().size)
            compose.onNodeWithText("적기").performClick()
            compose.waitUntil(5_000) { runBlocking { repo.memos.first().size } == before + 1 }
            val saved = repo.memos.first().first { it.title == "회의" }
            assertEquals("3층 회의실", saved.body)
            assertEquals(Today.date().plusDays(2), saved.date)
            assertFalse(saved.pinned)
        }
    }
}
