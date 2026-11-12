package com.bamnat.app.ui.calendar

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.app.ui.theme.monthTurn
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.recipe.Motion
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [PC-28] 통합 1차 PC 확인: with a 장터 작품 worn whose 달 넘김 is 스르륵(`fade`), 책장(`flip`) or 움직이지 않기(`none`), tapping
 * 11월 12일 opened 12월 10일 and a long press saved the shift there. The month after sat unseen exactly over the month on screen
 * and took the finger. 움직임 끄기 and the phone's 'remove animations' make every turn `none`, so it was not only worn works.
 *
 * The pager here is the calendar's own ([CalendarScreen]): the months either side kept made, each [MonthGrid] turned by [monthTurn].
 */
@RunWith(AndroidJUnit4::class)
class MonthTurnTapTest {
    // HQ-93: no line, window turn or open count left by an earlier test in this JVM (and none left for the next).
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val november = YearMonth.of(2026, 11)
    private val twelfth = LocalDate.of(2026, 11, 12)
    private val snapshot = ScheduleSnapshot(emptyList(), null, emptyList())
    private val cell = 44.dp

    private class Seen {
        var tapped: LocalDate? = null
        var held: LocalDate? = null
        var pager: PagerState? = null
    }

    private fun calendar(turn: String?, test: (Seen) -> Unit) {
        val seen = Seen()
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent {
                    BamnatTheme {
                        val pager = rememberPagerState(initialPage = CENTER_PAGE) { CENTER_PAGE * 2 }
                        seen.pager = pager
                        HorizontalPager(pager, Modifier.fillMaxWidth().height(cell * GRID_ROWS).testTag("months"),
                            beyondViewportPageCount = 1, key = { it }, verticalAlignment = Alignment.Top) { page ->
                            MonthGrid(
                                modifier = Modifier.monthTurn(turn) { (pager.currentPage - page) + pager.currentPageOffsetFraction },
                                month = pageToMonth(november, page),
                                snapshot = snapshot,
                                calculator = WorkHoursCalculator(snapshot.resolver, DayOfWeek.SUNDAY),
                                weekStart = DayOfWeek.SUNDAY,
                                selected = twelfth,
                                today = twelfth,
                                memoDates = emptySet(),
                                showHours = false,
                                cellHeight = cell,
                                onDayClick = { seen.tapped = it },
                                onQuickAction = { _, _ -> },
                                onLongPress = { seen.held = it },
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            test(seen)
        }
    }

    /** Where 12 sits in a grid whose weeks start on Sunday: 2026-11-01 is a Sunday, so the second row, the fifth column. */
    private fun Offset.Companion.ofThe12th(width: Int, height: Int) = Offset(width * 4.5f / 7f, height * 1.5f / GRID_ROWS)

    private fun tap() = compose.onNodeWithTag("months").performTouchInput { click(Offset.ofThe12th(width, height)) }
    private fun hold() = compose.onNodeWithTag("months").performTouchInput { longClick(Offset.ofThe12th(width, height)) }

    @Test fun theDayTappedIsTheDayOnScreenWhateverTheTurn() {
        for (turn in listOf(null) + Motion.TURNS) calendar(turn) { seen ->
            tap()
            compose.waitForIdle()
            assertEquals("달 넘김 '$turn': 11월 12일을 누르면 11월 12일", twelfth, seen.tapped)
            hold()
            compose.waitForIdle()
            assertEquals("달 넘김 '$turn': 11월 12일을 길게 누르면 11월 12일", twelfth, seen.held)
        }
    }

    /** After the month is turned (to 12월, and back), the month shown is still the one that answers. */
    @Test fun afterTurningTheMonthTheShownMonthAnswers() {
        for (turn in Motion.TURNS) calendar(turn) { seen ->
            runBlocking { compose.runOnIdle { } }
            compose.runOnUiThread { runBlocking { seen.pager!!.scrollToPage(CENTER_PAGE + 1) } }
            compose.waitForIdle()
            tap()
            compose.waitForIdle()
            // 2026-12-06 is the Sunday of December's second row.
            assertEquals("달 넘김 '$turn': 12월로 넘긴 뒤", LocalDate.of(2026, 12, 10), seen.tapped)
            compose.runOnUiThread { runBlocking { seen.pager!!.scrollToPage(CENTER_PAGE) } }
            compose.waitForIdle()
            tap()
            compose.waitForIdle()
            assertEquals("달 넘김 '$turn': 11월로 돌아온 뒤", twelfth, seen.tapped)
        }
    }

    /**
     * A page a hair off its place (a pager left a pixel short of settled) must not bring the next month back over the shown one:
     * only how the months are drawn follows the turn, never where a finger lands.
     */
    @Test fun aPagerLeftAPixelOffStillAnswersWithTheShownMonth() {
        for (turn in Motion.TURNS) calendar(turn) { seen ->
            compose.runOnUiThread { runBlocking { seen.pager!!.scrollToPage(CENTER_PAGE, pageOffsetFraction = 0.004f) } }
            compose.waitForIdle()
            tap()
            compose.waitForIdle()
            assertEquals("달 넘김 '$turn': 한 픽셀 어긋난 채", twelfth, seen.tapped)
        }
    }
}
