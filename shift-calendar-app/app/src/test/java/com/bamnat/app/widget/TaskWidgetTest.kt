package com.bamnat.app.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.tier.Feature
import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.TaskList
import com.bamnat.core.time.Today
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 1.39 [TASK-07] 할 일 위젯: the list from 내 달력's memos, the circle finishing a memo there, and the second widget being 프로. */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class TaskWidgetTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun theCircleFinishesTodaysMemoAndBringsItBack() = runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val today = Today.date()
        val memo = repo.save(Memo(date = today, title = "택배 받기", isChecklist = true, checklist = listOf(ChecklistItem("문 앞"))))
        val pill = repo.save(Memo(date = today.minusDays(3), title = "약 먹기", repeat = "D"))
        val shown = TaskWidget.load(context, null)
        assertTrue(shown.allowed)
        assertEquals(listOf("택배 받기", "약 먹기"), shown.rows.map { it.text })

        assertNotNull(TaskDoneAction.toggle(context, pill.id, today))
        val after = repo.get(pill.id)!!
        assertTrue(after.isDoneOn(today))
        assertFalse("only today: tomorrow it is to do again", after.isDoneOn(today.plusDays(1)))
        assertEquals(listOf("택배 받기", "약 먹기"), TaskWidget.load(context, null).rows.map { it.text })
        assertEquals(listOf(false, true), TaskWidget.load(context, null).rows.map { it.done })

        TaskDoneAction.toggle(context, memo.id, today)
        assertEquals("오늘 할 일을 다 끝냈어요", TaskList.headline(TaskWidget.load(context, null).rows))
        TaskDoneAction.toggle(context, memo.id, today)
        assertFalse(repo.get(memo.id)!!.isDone)
    }

    @Test fun aGoneMemoIsLeftAlone() = runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val memo = repo.save(Memo(date = Today.date(), title = "지울 것"))
        repo.delete(memo, offerUndo = false)
        assertEquals(null, TaskDoneAction.toggle(context, memo.id, Today.date()))
        assertEquals(null, TaskDoneAction.toggle(context, 987_654L, Today.date()))
    }

    @Test fun theWidgetDrawsItsRowsAndTheLockedOne() = runBlocking<Unit> {
        val today = Today.date()
        val rows = TaskList.of(listOf(
            Memo(id = 1, date = today, title = "치과"),
            Memo(id = 2, date = today, title = "빨래", doneAtMillis = 1),
        ), today)
        val views = GlanceRemoteViews().compose(context, DpSize(250.dp, 180.dp)) { WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskContent(rows, today) } } }
        assertNotNull(views.remoteViews)
        assertNotNull(GlanceRemoteViews().compose(context, DpSize(250.dp, 180.dp)) { WidgetLookTheme { TaskLocked() } }.remoteViews)
        // 1.39 [TASK-31] 할 일 한 줄: one row, with what is left, and its locked one.
        assertNotNull(GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) { WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskLine(rows) } } }.remoteViews)
        assertNotNull(GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) { WidgetLookTheme { TaskLine(emptyList()) } }.remoteViews)
        assertNotNull(GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) { WidgetLookTheme { TaskLocked(short = true) } }.remoteViews)
        assertTrue(TaskWidget.SHORT_BELOW > 60.dp && TaskWidget.SHORT_BELOW < 110.dp)
        // PC c0ca3d9f: a 2×1 cell on a Pixel launcher is about 103 dp. Placed as 할 일 한 줄 it stays one row there; the big one
        // made that short is one row too, and either opens into the list two cells high.
        assertTrue(TaskWidget.oneRow(103.dp, placedAsLine = true))
        assertTrue(!TaskWidget.oneRow(103.dp, placedAsLine = false))
        assertTrue(TaskWidget.oneRow(80.dp, placedAsLine = false))
        assertTrue(!TaskWidget.oneRow(200.dp, placedAsLine = true))
        // PC e0f19f47: a launcher drew the "○" letter as a dot. The circle is a picture, at least 18 dp, and no row writes "○" or "✓".
        val line = GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) { WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskLine(rows) } } }
        val drawn = line.remoteViews.apply(context, android.widget.FrameLayout(context))
        val texts = mutableListOf<String>()
        val pictures = mutableListOf<android.widget.ImageView>()
        fun walk(v: android.view.View) {
            if (v is android.widget.TextView) texts += v.text.toString()
            if (v is android.widget.ImageView && v.drawable != null) pictures += v
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(drawn)
        assertTrue("no circle letter: $texts", texts.none { "○" in it || "✓" in it })
        assertTrue("the circle is drawn: $texts", pictures.isNotEmpty())
        // PC eae4e024: at 2×1 '우유 사기 ·…' cut the time and the count. The title, its time and the number left are each their own.
        val three = TaskList.of(listOf(
            Memo(id = 3, date = today, title = "우유 사기", reminder = com.bamnat.core.memo.MemoReminder(today.atTime(21, 0), ring = false, notify = true)),
            Memo(id = 4, date = today, title = "운동"),
            Memo(id = 5, date = today, title = "빨래"),
        ), today)
        val split = GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) { WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskLine(three) } } }
        texts.clear()
        walk(split.remoteViews.apply(context, android.widget.FrameLayout(context)))
        assertTrue("title alone: $texts", "우유 사기" in texts)
        assertTrue("time under it: $texts", texts.any { it.endsWith("21:00") || it.endsWith("9:00") })
        assertTrue("how many left, by ＋: $texts", "3" in texts)
        // PC 6차: today's 9:21 gone by showed no 늦음 in either widget. Past its time it says 늦음 by the time, in both.
        val late = GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) {
            WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskLine(three, today.atTime(21, 30)) } }
        }
        texts.clear()
        walk(late.remoteViews.apply(context, android.widget.FrameLayout(context)))
        // PC 7차: '늦음 · 09:…' was cut at 2×1; the time comes first, then 늦음. PC 8차: '16:05 · 늦…' at 2×1; narrow, no dot.
        assertTrue("time first, then 늦음, no dot at 2×1: $texts", texts.any { it.endsWith(" 늦음") && " · " !in it && (it.startsWith("21:00") || it.startsWith("9:00") || it.startsWith("오후")) })
        val wide = GlanceRemoteViews().compose(context, DpSize(320.dp, 60.dp)) {
            WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskLine(three, today.atTime(21, 30)) } }
        }
        texts.clear()
        walk(wide.remoteViews.apply(context, android.widget.FrameLayout(context)))
        assertTrue("wider, with the dot: $texts", texts.any { it.endsWith(" · 늦음") })
        val early = GlanceRemoteViews().compose(context, DpSize(180.dp, 60.dp)) {
            WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskLine(three, today.atTime(20, 30)) } }
        }
        texts.clear()
        walk(early.remoteViews.apply(context, android.widget.FrameLayout(context)))
        assertTrue("not yet: $texts", texts.none { "늦음" in it })
        val first = three.first { it.text == "우유 사기" }
        assertTrue(note(first, TaskList.overdue(first, today.atTime(21, 30)))!!.endsWith("늦음")) // [RVP-54] when first, 늦음 last
        assertEquals(null, note(three.first { it.text == "운동" }, false))
        assertNotNull(GlanceRemoteViews().compose(context, DpSize(250.dp, 180.dp)) {
            WidgetLookTheme { WidgetSurface(androidx.glance.GlanceModifier) { TaskContent(three, today, today.atTime(21, 30)) } }
        }.remoteViews)
        // The second widget's row is 프로; the first is free whatever the tier.
        assertEquals(com.bamnat.app.tier.Tier.PRO, Feature.MORE_TASK_WIDGETS.minTier)
    }
}
