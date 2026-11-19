package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.model.Leave
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.YearMonth

/**
 * 2.0.0 검토 갈래 H 9묶음의 화면 사진: 한 달 근무 넣기 미리 보기의 점과 주 막대([RVH-70] HQ-82), 날짜 창 근무 칩 줄의 '휴무'([RVH-71]
 * HQ-87). 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH9ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )
    private val nov = YearMonth.of(2026, 11)
    private val repo get() = app.container.calendars.repository(PersonalCalendar.MAIN)
    private fun id(name: String) = runBlocking { repo.shiftTypes.first() }.first { it.name == name }.id

    /**
     * HQ-82: fourteen days typed over a calendar with an order (two of them on leave already). Before, each of them had the orange
     * 바꾼 날 dot and every long week a red bar with nothing to say what it was; now no dot (TalkBack: no '직접 바꿈' either), the
     * bar in 주의, and one line under the month for what it means.
     */
    private fun roster(look: Int) {
        val (l, q, scale) = looks[look]
        shoot("rvh9_48_roster_preview_$l", q, scale, style(WorkStyle.SHIFT) {
            File(app.noBackupFilesDir, "roster_drafts").deleteRecursively()
            runBlocking { repo.setLeave(nov.atDay(5), Leave()); repo.setLeave(nov.atDay(12), Leave()) }
            val vm = DutyRosterViewModel(app.container, PersonalCalendar.MAIN, nov)
            content { com.bamnat.app.ui.imports.DutyRosterScreen(vm, onBack = {}) }
            settle()
            vm.tap(1)
            repeat(14) { i -> vm.put(DutyCell.Shift(id(if (i % 4 < 2) "주간" else "야간"))) }
            settle()
            assertTrue(appears("5·12일 (연차·바꾼 근무)"))
            assertFalse("미리 보기의 날마다 '직접 바꿈'", shows("직접 바꿈"))
            // A finger's swipe over the month would paint it (a key is held): scrolled by its semantics instead.
            scrollToEnd()
            assertTrue(shows("왼쪽 막대는 64시간이 넘는 주예요"))
        })
    }
    @Test fun rosterA() = roster(0)
    @Test fun rosterB() = roster(1)
    @Test fun rosterC() = roster(2)
    @Test fun rosterD() = roster(3)

    /** HQ-87: a 주간 day's sheet; at 접은 폭 200% '야간'·'휴무' went into '+2'. Now the chips take two lines and 휴무 is never folded. */
    private fun chips(look: Int) {
        val (l, q, scale) = looks[look]
        shoot("rvh9_49_day_chips_$l", q, scale, style(WorkStyle.SHIFT) {
            val d = runBlocking {
                val snap = app.container.scheduleRepository.snapshot.first()
                (0..12).map { today.plusDays(it.toLong()) }.filter { it.month == today.month }
                    .first { snap.resolver.resolve(it).shiftType?.name == "주간" }
            }
            openDay(d)
            settle()
            assertTrue(appears("휴무", exact = true))
        })
    }
    @Test fun chipsA() = chips(0)
    @Test fun chipsB() = chips(1)
    @Test fun chipsC() = chips(2)
    @Test fun chipsD() = chips(3)
}
