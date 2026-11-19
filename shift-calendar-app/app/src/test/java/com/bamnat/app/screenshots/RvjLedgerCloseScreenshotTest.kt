package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffRoster
import com.bamnat.core.work.StaffShift
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * 2.0.0 갈래 J [RVJ-101] 검토 U05-09·U05-10·U05-11·PAYU-21·U05c-08, the 급여 대장 and 대타 찾기 of a 사장님:
 * - a closed month says why 직원 더하기 is off, right under it, with 다시 열기 (U05-09);
 * - when the 지급일 has passed and everyone is marked 지급했어요, one line at the top closes the month (U05-11);
 * - 명세서 ✓ is the 사장님's own mark: the window says the day and takes it off again with 보냄 취소 (PAYU-21, U05-10);
 * - 대타 찾기 asks for the day's own hours and they can be changed before asking or putting anyone in (U05c-08).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvjLedgerCloseScreenshotTest : ScreenshotBase() {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val sep = YearMonth.of(2026, 9)
    private val kim = StaffMember("s1", "김알바", base = 10_320L, hiredOn = LocalDate.of(2026, 3, 2), job = "홀",
        weekly = DayOfWeek.entries.take(5).associateWith { nine }, insurance = Insurance.ALL)
    private val lee = StaffMember("s2", "이바리", base = 11_000L, hiredOn = LocalDate.of(2026, 5, 1), job = "주방",
        weekly = DayOfWeek.entries.drop(5).associateWith { nine }, insurance = Insurance.ALL)

    private fun seed(paid: Boolean = false, sent: Boolean = false) = runBlocking {
        val store = app.container.phaseThree
        store.saveWorkplace(Workplace("p1", "식당", fivePlus = true, payday = 10))
        store.saveStaff(kim, "p1", 0)
        store.saveStaff(lee, "p1", 1)
        val place = store.workplaceNow("p1")!!
        for (s in listOf(kim, lee)) {
            val line = PayrollLedger.line(place, s, sep, emptyList())
            if (paid) store.mark("p1", sep, line, paid = true)
            if (sent && s.id == "s1") store.mark("p1", sep, line, sent = true)
        }
    }

    private fun ledger(paid: Boolean = false, sent: Boolean = false): Screen.() -> Unit = {
        seed(paid, sent)
        content { com.bamnat.app.ui.work.PayrollScreen(StaffRoster(emptyList()), sep) {} }
        repeat(2) { settle() }
    }

    private fun closed(): Screen.() -> Unit = {
        ledger(paid = true).invoke(this)
        assertTrue("모두 지급했어요: 맨 위 한 줄", appears("9월은 모두 지급했어요"))
        tapText("이 달 닫기", exact = true)
        repeat(2) { settle() }
        assertTrue("닫힌 달이라는 말이 직원 더하기 바로 아래에", appears("9월은 닫힌 달이에요"))
        assertFalse("닫으면 맨 위 한 줄은 없어요", shows("9월은 모두 지급했어요"))
    }

    @Test fun theCloseLineAtTheTopWhenEveryoneIsPaid() = shoot("rvj_366_close_line_folded", FOLDED) {
        ledger(paid = true).invoke(this)
        assertTrue(appears("9월은 모두 지급했어요"))
        assertFalse("지급일이 지났어요 줄과 겹치지 않아요", shows("지급일이 지났어요"))
    }

    @Test fun noCloseLineWhileSomeoneIsNotPaid() = shoot("rvj_367_close_line_not_yet_folded", FOLDED) {
        ledger(paid = false).invoke(this)
        assertTrue(appears("지급일이 지났어요"))
        assertFalse(shows("9월은 모두 지급했어요"))
    }

    @Test fun aClosedMonthSaysWhyAndOpensAgain() = shoot("rvj_368_closed_month_folded", FOLDED, closed())

    @Test fun aClosedMonthDarkBig() = shoot("rvj_369_closed_month_folded_dark_200", DARK, 2f, closed())

    @Test fun aClosedMonthUnfolded() = shoot("rvj_370_closed_month_unfolded", UNFOLDED, closed())

    @Test fun reopeningFromThatLine() = shoot("rvj_371_reopened_folded", FOLDED) {
        closed().invoke(this)
        tapText("다시 열기", exact = true)
        repeat(2) { settle() }
        assertFalse(shows("9월은 닫힌 달이에요"))
        val month = runBlocking { app.container.phaseThree.payrollMonth("p1", sep).first() }
        assertEquals("OPEN", month?.state)
    }

    @Test fun theSentMarkIsSaidAndTakenOff() = shoot("rvj_372_slip_sent_folded", FOLDED) {
        ledger(sent = true).invoke(this)
        tapText("명세서 ✓", exact = true)
        repeat(2) { settle() }
        assertTrue("보낸 날이 말로", appears("보냈다고 표시했어요"))
        assertTrue(shows("보냄 취소"))
    }

    @Test fun theSentMarkIsSaidDarkBig() = shoot("rvj_373_slip_sent_folded_dark_200", DARK, 2f) {
        ledger(sent = true).invoke(this)
        tapText("명세서 ✓", exact = true)
        repeat(2) { settle() }
        assertTrue(appears("보냈다고 표시했어요"))
    }

    @Test fun cancellingTheSentMarkClearsIt() = shoot("rvj_374_slip_unsent_folded", FOLDED) {
        ledger(sent = true).invoke(this)
        tapText("명세서 ✓", exact = true)
        repeat(2) { settle() }
        tapText("보냄 취소", exact = true)
        repeat(2) { settle() }
        assertFalse("취소하면 그 줄은 없어요", shows("보냈다고 표시했어요"))
        val line = runBlocking { app.container.phaseThree.lines("p1", sep).first() }.first { it.staffId == "s1" }
        assertNull(line.slipSentAt)
    }

    // 대타 찾기: 김알바 is off on Friday 10/16 from 9 to 18; 이바리 works weekends only.
    private val friday = LocalDate.of(2026, 10, 16)
    private val place = Workplace("p1", "식당", fivePlus = true, payday = 10)

    private fun cover(): Screen.() -> Unit = {
        content { com.bamnat.app.ui.work.BossCoverDialog(place, listOf(kim, lee), emptyList(), "s1", friday) {} }
        repeat(2) { settle() }
    }

    @Test fun coverAsksForTheDaysHours() = shoot("rvj_375_cover_hours_folded", FOLDED) {
        cover().invoke(this)
        assertTrue("그날 칸의 시간", appears("09:00~18:00"))
        assertTrue("보내기 전에 고칠 수 있어요", shows("시간 고치기"))
    }

    @Test fun coverHoursChangedBeforeAsking() = shoot("rvj_376_cover_hours_changed_folded", FOLDED) {
        cover().invoke(this)
        tapText("시간 고치기", exact = true)
        repeat(2) { settle() }
        assertTrue("몇 시부터 먼저", appears("몇 시부터"))
        tapText("+30분", exact = true)
        tapText("완료", exact = true)
        repeat(2) { settle() }
        assertTrue("이어서 몇 시까지", appears("몇 시까지"))
        tapText("완료", exact = true)
        repeat(2) { settle() }
        assertTrue("고친 시간으로 물어요", appears("09:30~18:00"))
        assertTrue("원래대로 돌아갈 길", shows("원래 시간으로"))
    }

    @Test fun coverHoursDarkBig() = shoot("rvj_377_cover_hours_folded_dark_200", DARK, 2f) {
        cover().invoke(this)
        assertTrue(appears("시간 고치기"))
    }

    @Test fun aDayWithNoUsualHoursCanBeGivenOne() = shoot("rvj_378_cover_no_hours_folded", FOLDED) {
        // 일요일 is 김알바's usual day off and 이바리's day: ask about 이바리 on a Monday, when they have no hours.
        content { com.bamnat.app.ui.work.BossCoverDialog(place, listOf(kim, lee), emptyList(), "s2", LocalDate.of(2026, 10, 19)) {} }
        repeat(2) { settle() }
        assertTrue("시간이 없으면 정하는 단추가", appears("시간 정하기"))
        assertTrue(shows("시간을 정하면 찾을 수 있어요"))
    }

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
