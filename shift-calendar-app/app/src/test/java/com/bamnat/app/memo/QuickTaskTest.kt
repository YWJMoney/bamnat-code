package com.bamnat.app.memo

import android.content.Context
import android.content.Intent
import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsNotEnabled
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

    /** PC c0ca3d9f: '오늘' was picked but the line still said 11/9 21:00, and a time gone today was dropped without a word. */
    @Test fun theLineSaysWhereTheChipsPutIt() {
        val fmt = java.time.format.DateTimeFormatter.ofPattern("M/d (E)", java.util.Locale.KOREAN)
        val late = now.withHour(23).withMinute(6)
        val line = com.bamnat.core.memo.MemoTime.line("모레 밤 9시 운동", late)
        assertEquals("제목: 운동 · 11/9 (월) 21:00 알림", quickSay(line, QuickDay.READ, ring = true, late, fmt))
        assertFalse(quickPast(line, QuickDay.READ, late))
        assertTrue("오늘 21:00 at 23:06 is gone", quickPast(line, QuickDay.TODAY, late))
        assertTrue(quickSay(line, QuickDay.TODAY, ring = true, late, fmt).startsWith("제목: 운동 · 11/7 (토) · 21:00은 지났어요"))
        assertNull("what the line said is what 적기 does", quickMemo("모레 밤 9시 운동", QuickDay.TODAY, ring = true, late)!!.reminder)
        assertEquals("제목: 운동 · 11/8 (일) 21:00 알림", quickSay(line, QuickDay.TOMORROW, ring = true, late, fmt))
        assertEquals("제목: 운동 · 11/8 (일) · 알림 없이", quickSay(line, QuickDay.TOMORROW, ring = false, late, fmt))
        assertEquals("제목: 운동 · 날짜 없이 고정", quickSay(line, QuickDay.NONE, ring = true, late, fmt))
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

/** 1.39 [TASK-39] 적고 하나 더 and 다른 때…: the two small windows on their own. */
@RunWith(AndroidJUnit4::class)
class QuickWindowsTest {
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    /** [content] drawn in a plain window of ours (the 한 줄 창's own activity, its content replaced). */
    private fun window(content: @androidx.compose.runtime.Composable () -> Unit, test: () -> Unit) {
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a -> a.setContent { com.bamnat.app.ui.theme.BamnatTheme { content() } } }
            compose.waitForIdle()
            test()
        }
    }

    /** 1.39 [TASK-39] 적고 하나 더: the window stays, empty, says what was written, and 적기 still closes. */
    @Test fun oneMoreKeepsTheWindowOpen() {
        val saved = mutableListOf<Pair<String, Boolean>>()
        var closed = false
        window({ QuickTask("우유 사기", onClose = { closed = true }, onSave = { m, more -> saved += m.title to more }) }) {
        compose.onNodeWithText("적고 하나 더").performClick()
        assertEquals(listOf("우유 사기" to true), saved)
        compose.onNodeWithText("적었어요: 우유 사기").assertExists()
        assertFalse(closed)
        compose.onNodeWithText("적고 하나 더").assertIsNotEnabled()
        compose.onNodeWithText("적기").assertIsNotEnabled()
        }
    }

    /** 다른 때…: the day off's morning first, then 10분·30분·1시간·3시간 뒤 and 내일 이 시각; nothing moves until one is picked. */
    @Test fun laterOffersTheDayOffFirst() {
        val picked = mutableListOf<com.bamnat.core.memo.MemoSnooze.Way>()
        window({ MemoLater(java.time.LocalDate.of(2026, 11, 12), onClose = {}, onPick = { picked += it }) }) {
        compose.onNodeWithText("쉬는 날(11/12) 아침").assertExists()
        compose.onNodeWithText("3시간 뒤").assertExists()
        assertTrue(picked.isEmpty())
        compose.onNodeWithText("내일 이 시각").performClick()
        assertEquals(listOf(com.bamnat.core.memo.MemoSnooze.Way.TOMORROW), picked)
        }
    }
}
