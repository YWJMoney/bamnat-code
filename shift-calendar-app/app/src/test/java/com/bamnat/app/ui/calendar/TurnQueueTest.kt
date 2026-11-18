package com.bamnat.app.ui.calendar

import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.app.ui.theme.monthTurn
import com.bamnat.core.hours.WorkHoursCalculator
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVH-55] 검토 PC-88 (통합 2차 PC): three quick swipes turned only one or two months. The pager here is the calendar's own,
 * as in [MonthTurnTapTest], with the fling the calendar uses ([queuedTurns]); the pager's own fling is run beside it to show what
 * it did before.
 */
@RunWith(AndroidJUnit4::class)
class TurnQueueTest {
    // HQ-93: no line, window turn or open count left by an earlier test in this JVM (and none left for the next).
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val november = YearMonth.of(2026, 11)
    private val twelfth = LocalDate.of(2026, 11, 12)
    private val snapshot = ScheduleSnapshot(emptyList(), null, emptyList())
    private val cell = 44.dp

    private class Seen {
        var tapped: LocalDate? = null
        var pager: PagerState? = null
    }

    private fun calendar(queued: Boolean, test: (Seen) -> Unit) {
        val seen = Seen()
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent {
                    BamnatTheme {
                        val pager = rememberPagerState(initialPage = CENTER_PAGE) { CENTER_PAGE * 2 }
                        seen.pager = pager
                        val fling: TargetedFlingBehavior = if (queued) queuedTurns(pager) else PagerDefaults.flingBehavior(pager)
                        HorizontalPager(pager, Modifier.fillMaxWidth().height(cell * GRID_ROWS).testTag("months"),
                            beyondViewportPageCount = 1, key = { it }, verticalAlignment = Alignment.Top, flingBehavior = fling) { page ->
                            Month(pager, page) { seen.tapped = it }
                        }
                    }
                }
            }
            compose.waitForIdle()
            test(seen)
        }
    }

    @Composable
    private fun Month(pager: PagerState, page: Int, onDay: (LocalDate) -> Unit) = MonthGrid(
        modifier = Modifier.monthTurn(null) { (pager.currentPage - page) + pager.currentPageOffsetFraction },
        month = pageToMonth(november, page),
        snapshot = snapshot,
        calculator = WorkHoursCalculator(snapshot.resolver, DayOfWeek.SUNDAY),
        weekStart = DayOfWeek.SUNDAY,
        selected = twelfth,
        today = twelfth,
        memoDates = emptySet(),
        showHours = false,
        cellHeight = cell,
        onDayClick = onDay,
        onQuickAction = { _, _ -> },
    )

    private fun months() = compose.onNodeWithTag("months")

    /** A quick swipe as a thumb makes it: a third of the width in 60 ms, far quicker than the pager's own fling speed. */
    private fun flick(forward: Boolean = true) = months().performTouchInput {
        if (forward) swipe(Offset(width * 0.7f, centerY), Offset(width * 0.4f, centerY), durationMillis = 60)
        else swipe(Offset(width * 0.3f, centerY), Offset(width * 0.6f, centerY), durationMillis = 60)
    }

    /** Quick swipes, each a little into the turn of the one before. */
    private fun quickSwipes(n: Int, forward: Boolean = true) {
        compose.mainClock.autoAdvance = false
        repeat(n) {
            flick(forward)
            compose.mainClock.advanceTimeBy(60)
        }
        compose.mainClock.autoAdvance = true
        compose.waitForIdle()
    }

    private fun page(seen: Seen) = seen.pager!!.currentPage - CENTER_PAGE

    @Test fun threeQuickSwipesTurnThreeMonths() {
        calendar(queued = false) { seen ->
            quickSwipes(3)
            assertTrue("고치기 전(페이저 그대로): 세 번 쓸어 ${page(seen)}달", page(seen) < 3)
        }
        calendar(queued = true) { seen ->
            quickSwipes(3)
            assertEquals("세 번 빠르게 쓸면 세 달", 3, page(seen))
            assertEquals(0f, seen.pager!!.currentPageOffsetFraction)
            quickSwipes(2, forward = false)
            assertEquals("되돌아 두 번이면 두 달 앞으로", 1, page(seen))
        }
    }

    @Test fun oneSwipeIsStillOneMonthAndTheCountStartsAgainOnceStill() {
        calendar(queued = true) { seen ->
            flick()
            compose.waitForIdle()
            assertEquals(1, page(seen))
            flick()
            compose.waitForIdle()
            assertEquals("멈춘 뒤 한 번은 한 달", 2, page(seen))
        }
    }

    /** 검토 PC-28: a tap during a turn stops the turn and opens no day; the next swipe counts from where it stopped. */
    @Test fun aTapDuringATurnOnlyStopsItAndTheNextSwipeCountsFromThere() {
        calendar(queued = true) { seen ->
            compose.mainClock.autoAdvance = false
            flick()
            compose.mainClock.advanceTimeBy(40)
            assertTrue("누를 때 아직 넘기는 중", seen.pager!!.isScrollInProgress)
            months().performTouchInput { click(Offset(width * 4.5f / 7f, height * 1.5f / GRID_ROWS)) }
            compose.mainClock.autoAdvance = true
            compose.waitForIdle()
            assertNull("넘기는 중 누르면 날은 열리지 않아요", seen.tapped)
            assertEquals(0f, seen.pager!!.currentPageOffsetFraction)
            val stopped = page(seen)
            flick()
            compose.waitForIdle()
            assertEquals("멈춘 달에서 한 달", stopped + 1, page(seen))
        }
    }

    /** A finger that catches the turn and lets go slowly adds no month: it goes where the pager alone would take it, as before. */
    @Test fun aSlowDragDuringATurnAddsNoMonth() {
        val landed = listOf(false, true).map { queued ->
            var at = 0
            calendar(queued) { seen ->
                compose.mainClock.autoAdvance = false
                flick()
                compose.mainClock.advanceTimeBy(60)
                months().performTouchInput { swipe(center, center + Offset(-4f, 0f), durationMillis = 900) }
                compose.mainClock.autoAdvance = true
                compose.waitForIdle()
                at = page(seen)
            }
            at
        }
        assertEquals("천천히 잡았다 놓으면 페이저 그대로와 같은 달", landed[0], landed[1])
        assertTrue(landed[1] in 0..1)
    }
}
