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
        // RVS-12: every piece really went on (a piece that fails no longer stops the others, so it is checked here).
        org.junit.Assert.assertEquals("${r.title}: 넣지 못한 조각", emptyList<PieceKind>(), c.recipes.lastMissed)
    }

    private fun lookName(dark: Boolean, font: Float, q: String) =
        "${if (dark) "dark" else "light"}_${(font * 100).toInt()}_${if (q == FOLDED) "folded" else "unfolded"}"

    /** [n] put on once, then each of [looks] on tab [tab] (0 달력, 1 가계부, 2 메모). */
    private fun matrix(n: String, tab: Int = 0, r: Recipe = work(n), more: ScreenshotBase.Screen.() -> Unit = {}) {
        // HQ-56: the app's own calendar in each look first (once for the class), what a work's first row is held to.
        if (tab == 0) for ((dark, font, q) in looks) if (lookName(dark, font, q) !in PLAIN) {
            shoot("edge/plain_${lookName(dark, font, q)}", if (dark) night(q) else q, font) {
                settle()
                PLAIN[lookName(dark, font, q)] = firstRow() ?: error("기본 달력의 첫 줄을 찾지 못했어요")
            }
        }
        var worn = false
        for ((dark, font, q) in looks) {
            val name = "edge/${n}_${lookName(dark, font, q)}"
            shoot(name, if (dark) night(q) else q, font) {
                if (!worn) { put(r); worn = true; settle() }
                if (tab != 0) tab(tab, wide = q == UNFOLDED)
                more()
                settle()
                if (tab == 0) calendarStartsHigh(name, lookName(dark, font, q))
            }
        }
    }

    /**
     * Where the month's first whole row begins: the top of the highest day cell of the month shown, counting only cells drawn inside the
     * window (2.0.0 HQ-56: the pager also lays out the months beside it, off screen at (0,0,0,0), and the first match used to be one of
     * those, so the old check measured 0 and always passed). In dp, with the window's height.
     */
    private fun ScreenshotBase.Screen.firstRow(): Pair<Float, Float>? {
        val all = nodes()
        val dp = app.resources.displayMetrics.density
        val width = all.maxOf { it.boundsInWindow.right }
        val height = all.maxOf { it.boundsInWindow.bottom }
        val month = java.time.YearMonth.from(SHOT_TIME)
        val titles = (1..month.lengthOfMonth()).map { Fmt.dayTitle(month.atDay(it)) }
        val top = all.filter { n ->
            val b = n.boundsInWindow
            b.width > 1f && b.height > 1f && b.left >= 0f && b.top >= 0f && b.right <= width + 1f && b.bottom <= height + 1f &&
                n.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty().any { d -> titles.any { d.startsWith(it) } }
        }.minOfOrNull { it.boundsInWindow.top } ?: return null
        return top / dp to height / dp
    }

    /**
     * 조건 1, as far as a work can move it (HQ-56): wearing a work does not push the month's first row more than [PUSH_DP] below where
     * the app's own calendar starts in the same look. The app's own row is not yet in the top quarter (the line above the calendar,
     * P HQ-49), so the 25% line is written beside the shots (`edge/first-row.txt`) and becomes the check once HQ-49 is in.
     */
    private fun ScreenshotBase.Screen.calendarStartsHigh(name: String, look: String) {
        val (top, height) = firstRow() ?: return // a work that lays the calendar out as a list or a week: no month grid to measure
        val plain = PLAIN.getValue(look).first
        File("build/screenshots/edge/first-row.txt").apply { parentFile!!.mkdirs() }
            .appendText("$name\t첫 줄 ${top.toInt()}dp / ${height.toInt()}dp (${(top * 100 / height).toInt()}%)\t기본 ${plain.toInt()}dp\t차이 ${(top - plain).toInt()}dp\n")
        assertTrue("$name: 작품을 입으면 달력 첫 줄이 기본보다 ${(top - plain).toInt()}dp 내려가요(${PUSH_DP}dp까지)", top - plain <= PUSH_DP)
    }

    private companion object {
        /** How far a work's head (작품 머리, its decor and button) may push the month down. */
        const val PUSH_DP = 48f
        /** The app's own first row in each look, dp (top, window height). */
        val PLAIN = HashMap<String, Pair<Float, Float>>()
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

    /**
     * 2.0.0 [RVS-14] HQ-56 ③: the biggest 달력 머리 and 요일 줄 a maker can set (바깥 간격·안 여백 32, 달 이름 96sp, 요일 글자 96sp), as
     * 넣기 puts it, still leaves the first row within [PUSH_DP] of the app's own (drawn within PartFloor.ABOVE_DP).
     */
    @Test fun theBiggestHeadStillLeavesTheMonthHigh() = matrix("HEAD", r = Recipe("시험 머리 최대", styles = com.bamnat.core.work.WorkStyle.entries.toSet(),
        pieces = listOf(com.bamnat.core.recipe.Piece(PieceKind.PARTS, mapOf("parts" to listOf(
            mapOf("id" to "cal.head", "out" to 32L, "pad" to 32L, "fill" to 0xFFFFE0B2L, "r" to 24L),
            mapOf("id" to "cal.head.title", "size" to 96L, "pad" to 32L, "out" to 32L),
            mapOf("id" to "cal.head.nav", "out" to 32L, "pad" to 32L),
            mapOf("id" to "cal.head.today", "out" to 32L, "pad" to 32L),
            mapOf("id" to "cal.week", "out" to 32L, "pad" to 32L),
            mapOf("id" to "cal.week.day", "size" to 96L, "pad" to 32L, "out" to 32L),
        ))))))
}
