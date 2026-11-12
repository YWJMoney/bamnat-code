package com.bamnat.app.ui.parts

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.calendar.MonthGrid
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.BlueprintParts
import com.bamnat.core.time.Today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVS-02] 스튜디오 조사 보고서 SN-02 (PC-28과 같은 뿌리): a part's 움직임 (숨쉬기 1.12배, 통통 3dp 위로) and its 둥글기 went
 * into how the part is laid out for the finger, not only how it is drawn. On a worn work whose 오늘 칸 breathes, the edge of the day before
 * opened 오늘, and the bottom of 오늘 opened nothing; a round cell lost its corners.
 *
 * Here every 움직임 × 둥글기 0·48 × 그림자, on the calendar (MonthGrid) and the 가계부 달력 (the same `partBox` → `clickable` order as
 * 메모 달력·가계부 표·급여 줄), with the animation at its most: a point 1dp inside any edge of a cell opens that cell's day.
 */
@RunWith(AndroidJUnit4::class)
class PartMotionTapTest {
    // HQ-93: no line, window turn or open count left by an earlier test in this JVM (and none left for the next).
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val november = YearMonth.of(2026, 11)
    private val twelfth = LocalDate.of(2026, 11, 12)

    @Before fun fixToday() = Today.fix(twelfth.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")))
    @After fun freeToday() = Today.reset()

    private class Seen { var tapped: LocalDate? = null }

    /** 오늘's part moving as [motion], round by [r], with a [shadow] (a 달력 칸 draws its box only with a shadow or 바깥 간격). */
    private fun parts(id: String, motion: String, r: Long, shadow: Long) = Blueprint(Blueprint.clean(listOf(
        mapOf("id" to id, "on" to "today", "motion" to motion, "r" to r, "shadow" to shadow),
    )))

    private fun show(bp: Blueprint, width: Dp, content: @Composable (Seen) -> Unit, test: (Seen) -> Unit) {
        val seen = Seen()
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            compose.mainClock.autoAdvance = false
            s.onActivity { a ->
                a.setContent {
                    BamnatTheme {
                        CompositionLocalProvider(LocalBlueprint provides bp) {
                            Box(Modifier.width(width)) { content(seen) }
                        }
                    }
                }
            }
            // 숨쉬기·통통 go up for 1.1 s and back: here they are at their most.
            compose.mainClock.advanceTimeBy(1_100)
            test(seen)
        }
    }

    private fun startsWith(text: String) = SemanticsMatcher("설명이 '$text'로 시작") { n ->
        n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it == text || it.startsWith("$text,") || it.startsWith("$text ") } == true
    }

    private fun boundsOf(text: String): Rect = compose.onAllNodes(startsWith(text), useUnmergedTree = true).fetchSemanticsNodes().first().boundsInRoot

    private fun tap(seen: Seen, at: Offset): LocalDate? {
        seen.tapped = null
        compose.onRoot().performTouchInput { click(at) }
        compose.mainClock.advanceTimeByFrame()
        return seen.tapped
    }

    /**
     * Where 오늘 is laid out, from the cells around it (which do not move): its left and right by the days beside it, its top and bottom
     * by the weeks above and below, less the gaps between cells.
     */
    private fun around(name: (LocalDate) -> String, onDay: (Seen, Offset) -> LocalDate?, seen: Seen, label: String) {
        val left = boundsOf(name(twelfth.minusDays(1)))
        val right = boundsOf(name(twelfth.plusDays(1)))
        val up = boundsOf(name(twelfth.minusWeeks(1)))
        val down = boundsOf(name(twelfth.plusWeeks(1)))
        val gx = (right.left - left.right - left.width) / 2
        val gy = (down.top - up.bottom - left.height) / 2
        val today = Rect(left.right + gx, up.bottom + gy, right.left - gx, down.top - gy)
        val d = compose.onRoot().fetchSemanticsNode().layoutInfo.density.density // 1dp
        val mid = today.center
        val cases = listOf(
            "어제 칸 오른쪽 가장자리" to (Offset(left.right - d, mid.y) to twelfth.minusDays(1)),
            "내일 칸 왼쪽 가장자리" to (Offset(right.left + d, mid.y) to twelfth.plusDays(1)),
            "지난주 칸 아래 가장자리" to (Offset(mid.x, up.bottom - d) to twelfth.minusWeeks(1)),
            "다음 주 칸 위 가장자리" to (Offset(mid.x, down.top + d) to twelfth.plusWeeks(1)),
            "오늘 칸 왼쪽 가장자리" to (Offset(today.left + d, mid.y) to twelfth),
            "오늘 칸 오른쪽 가장자리" to (Offset(today.right - d, mid.y) to twelfth),
            "오늘 칸 위 가장자리" to (Offset(mid.x, today.top + d) to twelfth),
            "오늘 칸 아래 가장자리" to (Offset(mid.x, today.bottom - d) to twelfth),
            "오늘 칸 왼쪽 위 모서리" to (Offset(today.left + 2 * d, today.top + 2 * d) to twelfth),
            "오늘 칸 오른쪽 아래 모서리" to (Offset(today.right - 2 * d, today.bottom - 2 * d) to twelfth),
        )
        for ((where, c) in cases) assertEquals("$label: $where", c.second, onDay(seen, c.first))
    }

    private fun calendar(bp: Blueprint, width: Dp, cell: Dp, label: String) = show(bp, width, { seen ->
        val snapshot = ScheduleSnapshot(emptyList(), null, emptyList())
        MonthGrid(
            modifier = Modifier.fillMaxWidth(),
            month = november, snapshot = snapshot, calculator = WorkHoursCalculator(snapshot.resolver, DayOfWeek.SUNDAY),
            weekStart = DayOfWeek.SUNDAY, selected = twelfth.plusDays(3), today = twelfth, memoDates = emptySet(), showHours = false,
            cellHeight = cell, onDayClick = { seen.tapped = it }, onQuickAction = { _, _ -> },
        )
    }) { seen -> around(Fmt::dayTitle, ::tap, seen, label) }

    private fun ledger(bp: Blueprint, width: Dp, label: String) = show(bp, width, { seen ->
        com.bamnat.app.ui.money.LedgerMonthGrid(november, emptyList(), picked = null, onPick = { seen.tapped = it })
    }) { seen -> around(Fmt::monthDay, ::tap, seen, label) }

    @Test fun theCalendarCellOpensItsOwnDayWhateverItsMotion() {
        for (motion in BlueprintParts.MOTIONS) for (r in listOf(0L, 48L)) {
            calendar(parts("cal.cell", motion, r, shadow = 2), 380.dp, 52.dp, "달력 칸 움직임 '$motion' 둥글기 $r (접은 폭)")
        }
    }

    /** 편 폭(673dp): a cell over 48dp gets no 'near enough' help from the finger's minimum target, so a round cell's corners must be its own. */
    @Test fun aWideCalendarCellKeepsItsCorners() {
        for (motion in listOf("none", "pulse", "bounce")) {
            calendar(parts("cal.cell", motion, 48, shadow = 2), 673.dp, 72.dp, "달력 칸 움직임 '$motion' 둥글기 48 (편 폭)")
        }
    }

    @Test fun theLedgerCalendarCellOpensItsOwnDayWhateverItsMotion() {
        for (motion in BlueprintParts.MOTIONS) for (r in listOf(0L, 48L)) {
            ledger(parts("money.cal.cell", motion, r, shadow = 0), 380.dp, "가계부 달력 칸 움직임 '$motion' 둥글기 $r (접은 폭)")
        }
        for (motion in listOf("none", "pulse", "bounce")) {
            ledger(parts("money.cal.cell", motion, 48, shadow = 0), 673.dp, "가계부 달력 칸 움직임 '$motion' 둥글기 48 (편 폭)")
        }
    }
}
