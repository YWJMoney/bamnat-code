package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.getOrNull
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.calendar.MonthGrid
import com.bamnat.app.ui.parts.LocalBlueprint
import com.bamnat.app.ui.settings.CellWeeks
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import java.io.File
import java.time.DayOfWeek
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVS-01] 조합 시험기 ②(검토 SD-06) (스튜디오 조사 보고서 5장 S6-2, 9장): the 가장자리 시험 작품 E1~E12 (`docs/recipes/edge-200.json`,
 * `node tools/market-seed.mjs --set edge`가 서버 검사로 만듦) put on the real app the way 넣기 puts them, each drawn four times so every
 * pair of 밝은·어두운 × 글자 100·200% × 접은 폭·편 폭 is seen once (a covering set of the eight). Each shot must draw (no crash) and,
 * on the calendar, the month must start in the top quarter of the screen (조건 1). The shots are also the 앞·뒤 사진 of the S PRs
 * (`build/screenshots/edge/`).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class EdgeWorksScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    private val works: List<Recipe> by lazy {
        val a = JSONArray(File("../docs/recipes/edge-200.json").readText())
        (0 until a.length()).map { RecipeCodec.read(a.getJSONObject(it).getJSONObject("recipe").toString()) }
    }

    private fun work(n: String): Recipe = works.first { it.title.startsWith("시험 $n ") }

    /** (어두운, 글자 배율, 화면): each pair of the three once. */
    private val looks = listOf(
        Triple(false, 1f, FOLDED), Triple(true, 2f, FOLDED), Triple(false, 2f, UNFOLDED), Triple(true, 1f, UNFOLDED),
    )

    private fun night(q: String) = q.replace("-port-", "-port-night-")

    private fun put(r: Recipe) = runBlocking {
        c.recipes.apply(r, "EDGE2345", 1, PersonalCalendar.MAIN, r.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }, emptyMap())
    }

    /** [n] put on once, then each of [looks] on tab [tab] (0 달력, 1 가계부, 2 메모). */
    private fun matrix(n: String, tab: Int = 0, more: ScreenshotBase.Screen.() -> Unit = {}) {
        val r = work(n)
        var worn = false
        for ((dark, font, q) in looks) {
            val name = "edge/${n}_${if (dark) "dark" else "light"}_${(font * 100).toInt()}_${if (q == FOLDED) "folded" else "unfolded"}"
            shoot(name, if (dark) night(q) else q, font) {
                if (!worn) { put(r); worn = true; settle() }
                if (tab != 0) tab(tab, wide = q == UNFOLDED)
                more()
                settle()
                if (tab == 0) calendarStartsHigh(name)
            }
        }
    }

    /** 조건 1: the first row of the month (the cell of its 1st) begins within the top quarter of the window. */
    private fun ScreenshotBase.Screen.calendarStartsHigh(name: String) {
        val all = nodes()
        val height = all.maxOf { it.boundsInWindow.bottom }
        val first = SHOT_TIME.toLocalDate().withDayOfMonth(1)
        val cell = all.firstOrNull { n ->
            n.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith(Fmt.dayTitle(first)) } == true
        } ?: return // a work that lays the calendar out as a list or a week: no month grid to measure
        assertTrue("$name: 달력 첫 줄이 화면 위 25% 안에서 시작해야 해요 (${cell.boundsInWindow.top} / $height)", cell.boundsInWindow.top <= height * 0.25f)
    }

    /**
     * 2.0.0 [RVS-03] (검토 SN-01): the studio's example cells and the calendar wearing the same 달력 칸 부품, one over the other. E1 (오늘 빛,
     * no 바깥 간격·그림자) and E2 (숨쉬기 + 그림자 2): before, the example showed the 움직임 but not the 그림자 and the calendar the 그림자 but
     * not E1's 빛. Today is the 4th in both.
     */
    private fun sideBySide(n: String) {
        @Suppress("UNCHECKED_CAST")
        val bp = Blueprint(work(n).pieces.first { it.kind == PieceKind.PARTS }.values["parts"] as List<Map<String, Any?>>)
        val month = java.time.YearMonth.of(2026, 11)
        val today = month.atDay(4)
        for ((dark, font, q) in looks.take(2)) shoot("edge/${n}_studio_vs_calendar_${if (dark) "dark" else "light"}_${(font * 100).toInt()}", if (dark) night(q) else q, font) {
            content {
                CompositionLocalProvider(LocalBlueprint provides bp) {
                    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(16.dp)) {
                        Text("스튜디오 예시", style = MaterialTheme.typography.titleSmall)
                        CellWeeks(CalendarView(), rows = 2, onCell = null, start = today.minusDays(today.dayOfWeek.value % 7L), todayDate = today)
                        Text("실제 달력", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp))
                        val snapshot = ScheduleSnapshot(emptyList(), null, emptyList())
                        MonthGrid(
                            modifier = Modifier.fillMaxWidth(), month = month, snapshot = snapshot,
                            calculator = WorkHoursCalculator(snapshot.resolver, DayOfWeek.SUNDAY), weekStart = DayOfWeek.SUNDAY,
                            selected = today.plusDays(3), today = today, memoDates = emptySet(), showHours = false,
                            cellHeight = 56.dp, onDayClick = {}, onQuickAction = { _, _ -> },
                        )
                    }
                }
            }
            settle()
        }
    }

    @Test fun e01StudioMatchesTheCalendar() = sideBySide("E1")
    @Test fun e02StudioMatchesTheCalendar() = sideBySide("E2")

    @Test fun e01TodayGlow() = matrix("E1")
    @Test fun e02BreathingCell() = matrix("E2")
    @Test fun e03BouncingLedger() = matrix("E3", tab = 1) { tapText("내역", exact = true) }
    @Test fun e04GlassList() = matrix("E4", tab = 2)
    @Test fun e05AppFeel() = matrix("E5")
    @Test fun e06DarkGround() = matrix("E6", tab = 2)
    @Test fun e07SmallLetters() = matrix("E7")
    @Test fun e09SixteenPieces() = matrix("E9")
    @Test fun e11CellDecor() = matrix("E11")
    @Test fun e12HiddenTabs() = matrix("E12")
}
