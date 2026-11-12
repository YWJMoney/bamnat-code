package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 [RVL-74][RVL-75] (검토 I07ba-03·I07ba-02) 갈래 L 10차의 화면 (디자인 품질 조건): 다시 온 근무표는 바뀌는 날만 먼저 말하고, 요일 줄이 다른 달을
 * 말하면 '12월로 넣기', 하루씩 어긋나면 노란 칸과 '맞게 읽었어요'. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlRosterAgainScreenshotTest : ScreenshotBase() {
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)
    private val dec = nov.plusMonths(1)
    private val names = listOf("일", "월", "화", "수", "목", "금", "토")
    private val days = (1..14).joinToString(" ")
    private val line = "주 주 야 야 휴 휴 주 주 야 야 휴 휴 주 주"

    private fun Screen.waitFor(what: String, done: () -> Boolean) {
        val until = System.currentTimeMillis() + 20_000  // real clock: a timeout for waiting, not a date
        while (!done()) {
            check(System.currentTimeMillis() < until) { "Timed out waiting for $what" }  // real clock: a timeout for waiting, not a date
            Thread.sleep(20)
            settle()
        }
        settle()
    }

    private fun pasted(month: YearMonth, text: String, before: Screen.() -> Unit = {}, then: Screen.(DutyRosterViewModel) -> Unit): Screen.() -> Unit = style(WorkStyle.SHIFT) {
        File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
        before()
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, month)
        content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
        settle()
        vm.readText(text)
        waitFor("the roster read") { vm.ui.value.rows != null }
        then(vm)
        settle()
    }

    // ---- RVL-74: the same roster again, three days fixed ----
    private fun again(): Screen.() -> Unit = pasted(dec, "2026년 12월 근무표\n$days\n주 주 야 휴 휴 휴 주 주 야 야 휴 주 야 주", before = {
        val first = DutyRosterViewModel(c, PersonalCalendar.MAIN, dec)
        first.readText("2026년 12월 근무표\n$days\n$line")
        waitFor("the first roster") { first.ui.value.rows != null }
        first.apply()
        waitFor("the month written") { runBlocking { c.scheduleRepository.snapshot.first() }.resolver.resolve(dec.atDay(14)).shiftType?.name == "주간" }
    }) { _ ->
        assertTrue(appears("바뀌는 날은 3일이에요"))
        assertTrue(appears("바뀐 3일 고치기"))
    }

    @Test fun againFoldLight() = shoot("rvl74_again_fold_light_100", FOLDED, again())
    @Test fun againFoldDarkHuge() = shoot("rvl74_again_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, again())
    @Test fun againOpenLight() = shoot("rvl74_again_open_light_100", UNFOLDED, again())

    // ---- RVL-75: weekdays of December under a roster read as November ----
    private fun otherMonth(): Screen.() -> Unit = pasted(nov,
        "2026년 11월 근무표\n$days\n" + (1..14).joinToString(" ") { d -> names[dec.atDay(d).dayOfWeek.value % 7] } + "\n$line") { _ ->
        assertTrue(appears("요일을 보면 12월 근무표예요"))
    }

    @Test fun otherMonthFoldLight() = shoot("rvl75_other_month_fold_light_100", FOLDED, otherMonth())
    @Test fun otherMonthFoldDarkHuge() = shoot("rvl75_other_month_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, otherMonth())
    @Test fun otherMonthOpenLight() = shoot("rvl75_other_month_open_light_100", UNFOLDED, otherMonth())

    /** The weekday under each date is the day before's: a column lost. */
    private fun shifted(): Screen.() -> Unit = pasted(nov,
        "2026년 11월 근무표\n$days\n" + (1..14).joinToString(" ") { d -> names[nov.atDay(d).minusDays(1).dayOfWeek.value % 7] } + "\n$line") { _ ->
        assertTrue(appears("날짜와 요일이 하루씩 어긋나 보여요"))
    }

    @Test fun shiftedFoldLight() = shoot("rvl75_shifted_fold_light_100", FOLDED, shifted())
    @Test fun shiftedFoldDarkHuge() = shoot("rvl75_shifted_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, shifted())

    companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
