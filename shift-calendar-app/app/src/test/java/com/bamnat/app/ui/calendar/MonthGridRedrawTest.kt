package com.bamnat.app.ui.calendar

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.DefaultShiftTypes
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.model.DayEntry
import com.bamnat.core.model.Leave
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.time.Today
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVH-67] 검토 CAL-01 (PC-25 달 넘기기 느린 프레임과 같은 뿌리): picking a day drew all 42 cells of the month again, and the
 * months either side too (126). Here a month of 근무 with a 연차, memos and the week's hours: picking a day draws again the day left and
 * the day picked, nothing else; the same picked day given again draws nothing.
 */
@RunWith(AndroidJUnit4::class)
class MonthGridRedrawTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val november = YearMonth.of(2026, 11)
    private val twelfth = LocalDate.of(2026, 11, 12)

    @Before fun fixToday() = Today.fix(twelfth.atTime(10, 0).atZone(ZoneId.of("Asia/Seoul")))
    @After fun after() {
        CellDraws.counting = false
        CellDraws.count = 0
        Today.reset()
    }

    private val types = DefaultShiftTypes.all().mapIndexed { i, t -> t.copy(id = i + 1L) }
    private val monday = LocalDate.of(2026, 11, 2)
    private val snapshot = ScheduleSnapshot(
        types,
        WorkPattern.Weekly(monday, DayOfWeek.entries.associateWith { if (it == DayOfWeek.SATURDAY || it == DayOfWeek.SUNDAY) types.last().id else types.first().id }),
        listOf(DayEntry(twelfth.plusDays(6), leave = Leave())),
    )
    private val calculator = WorkHoursCalculator(snapshot.resolver, DayOfWeek.SUNDAY)
    private val memoDates = setOf(twelfth.minusDays(2), twelfth.plusDays(9))

    private fun grid(test: (pick: (LocalDate) -> Unit) -> Unit) {
        var selected by mutableStateOf(twelfth)
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent {
                    BamnatTheme {
                        Box(Modifier.width(400.dp)) {
                            MonthGrid(
                                modifier = Modifier.fillMaxWidth(),
                                month = november, snapshot = snapshot, calculator = calculator,
                                weekStart = DayOfWeek.SUNDAY, selected = selected, today = twelfth, memoDates = memoDates,
                                showHours = true, cellHeight = 64.dp, onDayClick = { selected = it }, onQuickAction = { _, _ -> },
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
            test { d ->
                CellDraws.count = 0
                CellDraws.counting = true
                selected = d
                compose.waitForIdle()
                CellDraws.counting = false
            }
        }
    }

    @Test fun pickingADayDrawsOnlyTheDayLeftAndTheDayPicked() = grid { pick ->
        pick(twelfth.plusDays(3))
        assertEquals("다른 날을 고르면 그린 칸 수", 2, CellDraws.count)
        // A day in the next month's first week, shown greyed on this month's grid.
        pick(november.plusMonths(1).atDay(2))
        assertEquals("다음 달 칸을 고르면 그린 칸 수", 2, CellDraws.count)
        pick(november.plusMonths(1).atDay(2))
        assertEquals("같은 날을 다시 주면 그린 칸 수", 0, CellDraws.count)
    }

    @Test fun aDayOffTheGridDrawsOnlyTheDayLeft() = grid { pick ->
        // A day of another month (the month turned in the week view): only the day that was picked here lets go.
        pick(november.plusMonths(3).atDay(15))
        assertTrue("그린 칸 수 ${CellDraws.count}", CellDraws.count <= 1)
    }
}
