package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.TurnSlipCheck
import com.bamnat.app.ui.calendar.TurnSlipAsk
import com.bamnat.core.memo.Memo
import com.bamnat.core.model.ExtraEntry
import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.Leave
import com.bamnat.core.model.ShiftOverride
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.recipe.Motion
import com.bamnat.core.schedule.TurnSlip
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVH-64] 검토 PC-72 '이 날 맞아요?' (설계 4절): the card over the calendar, the update notice's line, the sheet with one line (a day
 * whose alarm will not ring) and with five (a day meant that holds something, a day gone by, a memo, a 가계부 line), and the end. Four
 * looks (접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%). [RVH-65] PC-73 최근 바꾼 기록 too.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewH6ShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    @After fun forget() = TurnSlipAsk.resetForTest()

    private fun all(name: String, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvh6_${name}_$look", q, scale, style(WorkStyle.SHIFT) { go() })
    }

    private fun one(name: String, look: Int, go: Screen.() -> Unit) {
        val (l, q, scale) = looks[look]
        shoot("rvh6_${name}_$l", q, scale, style(WorkStyle.SHIFT) { go() })
    }

    /**
     * The lines as 1.36.1~1.39.0 left them, put on this phone's calendar (once: the four looks share its data) and handed to the sheet
     * as the first run of 2.0.0 would find them. 스르륵 is the month turn, so the card shows.
     */
    private fun found(five: Boolean, told: Boolean = true) = runBlocking {
        val c = app.container
        c.settingsRepository.update { it.copy(motion = Motion(turn = "fade").encode()) }
        val repo = c.scheduleRepository
        val snap = repo.snapshot.first()
        val week = c.settingsRepository.current.weekStart
        val month = YearMonth.from(today)
        val gap = ChronoUnit.DAYS.between(TurnSlip.firstCell(month, week), TurnSlip.firstCell(month.plusMonths(1), week))
        fun rings(d: LocalDate) = snap.shiftTypes.firstOrNull { it.id == snap.resolver.patternShiftIdOn(d) }?.let { !it.isOff && it.alarmEnabled } == true
        val ahead = (8L..60L).map { today.plusDays(it) }.filter(::rings)
        val at = Today.instant().minusSeconds(86_400)
        fun line(kind: TurnSlip.Kind, d: LocalDate, id: Long = 0) =
            TurnSlip.Line(TurnSlip.Source(kind, d, at, id), YearMonth.from(d.minusDays(gap)), d.minusDays(gap))
        val leaveDay = ahead.first()
        if (repo.entryNow(leaveDay)?.leave == null) repo.setLeave(leaveDay, Leave())
        val out = mutableListOf(TurnSlipCheck.Found(PersonalCalendar.MAIN, line(TurnSlip.Kind.DAY, leaveDay), held = repo.entryNow(leaveDay)))
        if (five) {
            val extraDay = ahead.first { it.isAfter(leaveDay.plusDays(2)) }
            if (repo.entryNow(extraDay)?.extras.isNullOrEmpty())
                repo.addExtra(extraDay, ExtraEntry(ExtraKind.OVERTIME_ID, LocalTime.of(18, 0), LocalTime.of(20, 0)))
            val meant = extraDay.minusDays(gap)
            if (repo.entryNow(meant)?.override == null) repo.setOverride(meant, ShiftOverride.Off)
            out += TurnSlipCheck.Found(PersonalCalendar.MAIN, line(TurnSlip.Kind.DAY, extraDay), held = repo.entryNow(extraDay),
                meantHeld = repo.entryNow(meant))
            val gone = today.minusDays(3)
            if (repo.entryNow(gone)?.leave == null) repo.setLeave(gone, Leave(quarters = 2))
            out += TurnSlipCheck.Found(PersonalCalendar.MAIN, line(TurnSlip.Kind.DAY, gone), held = repo.entryNow(gone))
            val memos = c.memoRepository
            val memo = memos.memos.first().firstOrNull { it.title == "건강검진" } ?: memos.save(Memo(date = today.plusDays(20), title = "건강검진"))
            out += TurnSlipCheck.Found(PersonalCalendar.MAIN, line(TurnSlip.Kind.MEMO, memo.date!!, memo.id), memo = memo)
            val book = c.calendars.money(PersonalCalendar.MAIN)
            val entry = book.save(MoneyEntry(date = today.plusDays(12), kind = MoneyKind.EXPENSE, amount = 12_000, category = "식비"))
            out += TurnSlipCheck.Found(PersonalCalendar.MAIN, line(TurnSlip.Kind.MONEY, entry.date, entry.id), entry = entry)
        }
        TurnSlipAsk.resetForTest()
        if (told) TurnSlipAsk.markTold(app) else app.getSharedPreferences("turn_slip", 0).edit().remove("told").commit()
        TurnSlipAsk.putForTest(TurnSlipAsk.Ask(TurnSlipAsk.rows(c, out, today), folded = false), told)
    }

    /** The card over the calendar: one line, the 주의 mark, 확인 and X (조건 3). */
    @Test fun card() = all("36_slip_card") { found(five = true); settle() }

    /** The update notice: the line first, with 확인. A look each: the notice is shown once a visit, and a test is one visit. */
    private val news: Screen.() -> Unit = { found(five = true, told = false); settle() }
    @Test fun newsA() = one("37_slip_news", 0, news)
    @Test fun newsB() = one("37_slip_news", 1, news)
    @Test fun newsC() = one("37_slip_news", 2, news)
    @Test fun newsD() = one("37_slip_news", 3, news)

    /** One line: a 연차 on a day whose alarm would ring, so it will not (조건 6: one orange mark). */
    @Test fun sheetOne() = all("38_slip_one") { found(five = false); settle(); TurnSlipAsk.show(); settle() }

    /** Five lines: days to come first; a day gone by and the memo·가계부 lines folded; a day meant that holds something said. */
    @Test fun sheetFive() = all("39_slip_five") { found(five = true); settle(); TurnSlipAsk.show(); settle() }

    /** The end: 칸이, what was changed, 되돌리기 and 닫기 (조건 16·10). Drawn a look each: the answer changes the phone's data. */
    private val end: Screen.() -> Unit = {
        found(five = false)
        settle()
        TurnSlipAsk.show()
        settle()
        tapText("일로 옮기기")
        settle()
        tapText("이대로 할게요", exact = true)
        settle()
    }

    /** [RVH-65] PC-73 달력 도구: 최근 바꾼 기록 among the tools. */
    @Test fun tools() = all("41_tools") {
        found(five = true)
        settle()
        tapText("도구", scrolls = 0)
        settle()
    }

    /** [RVH-65] PC-73 달력 도구 › 최근 바꾼 기록: the days changed by hand across months, with ↶ on each. */
    @Test fun recentChanges() = all("42_recent_changes") {
        found(five = true)
        settle()
        tapText("도구", scrolls = 0)
        settle()
        tapText("최근 바꾼 기록")
        settle()
    }

    @Test fun endA() = one("40_slip_end", 0, end)
    @Test fun endB() = one("40_slip_end", 1, end)
    @Test fun endC() = one("40_slip_end", 2, end)
    @Test fun endD() = one("40_slip_end", 3, end)
}
