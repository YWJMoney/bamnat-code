package com.bamnat.app.share

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.schedule.DutyCheck
import com.bamnat.core.schedule.RestRules
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/** [NURS-06] A received roster is read with the user's own 근무표 점검 기준, only inside the dates the link covers. */
@RunWith(AndroidJUnit4::class)
class ReceivedDutyCheckTest {
    private val raw = """{"v":1,"title":"민지","types":[
        {"id":1,"name":"데이","short":"D","color":"#1E88E5","time":"07:00-15:00"},
        {"id":2,"name":"나이트","short":"N","color":"#6C5CE7","time":"23:00-07:00"},
        {"id":3,"name":"오프","short":"O","color":"#9E9E9E","off":true}],
        "days":[{"date":"2026-11-02","type":2},{"date":"2026-11-03","type":2},{"date":"2026-11-04","type":2},{"date":"2026-11-05","type":2},
        {"date":"2026-11-06","type":3},{"date":"2026-11-07","type":1},{"date":"2026-11-08","type":1,"leave":"유급 휴가"}]}"""
    private val s = SharedSchedule.parse(JSONObject(raw))
    private val nov = YearMonth.of(2026, 11)

    @Test fun fourNightsThenOneOffIsTold() {
        val w = ReceivedDutyCheck.warnings(s, nov, RestRules())
        assertTrue(w.any { it.kind == DutyCheck.Kind.MANY_NIGHTS && it.date == LocalDate.of(2026, 11, 2) })
        // N-O-D: one day off after the nights, then a day shift.
        assertTrue(w.any { it.kind == DutyCheck.Kind.NO_OFF_AFTER_NIGHT && it.pattern == "N-O-D(나오데)" })
        assertTrue(w.all { it.source.isNotEmpty() })
        // A day of leave is no work.
        assertEquals(null, ReceivedDutyCheck.shiftOn(s, LocalDate.of(2026, 11, 8)))
    }

    /** 2.0.0 [RVG-39] 검토 PC-174: a shop's 영업일, a 현장's 일정 and an investor's 달력 are no roster, so nothing is checked. */
    @Test fun aLinkThatIsNoRosterIsNotChecked() {
        assertTrue(ReceivedDutyCheck.warnings(s, nov, RestRules()).isNotEmpty())
        fun with(extra: String) = SharedSchedule.parse(JSONObject(raw.replaceFirst("{\"v\":1,", "{\"v\":1,$extra,")))
        // 매출을 함께 보낸 가게, 일감을 함께 보낸 현장, 그리고 3.14 뒤 날짜만 보낸 가게·현장·투자.
        for (link in listOf(
            with("\"shows\":[\"pay\"],\"payKind\":\"sales\""), with("\"shows\":[\"jobs\"]"),
            with("\"word\":\"영업일\""), with("\"word\":\"일정\""), with("\"word\":\"달력\""),
        )) {
            assertTrue(link.word, link.word != "근무표")
            assertTrue(link.word, ReceivedDutyCheck.warnings(link, nov, RestRules()).isEmpty())
        }
        // A roster that says so, an old link and a word the app does not know are still read as one.
        for (link in listOf(with("\"word\":\"근무표\""), with("\"word\":\"월급\""), with("\"shows\":[\"pay\"]"))) {
            assertEquals("근무표", link.word)
            assertTrue(ReceivedDutyCheck.warnings(link, nov, RestRules()).isNotEmpty())
        }
    }

    @Test fun offOrOutsideTheLinkSaysNothing() {
        assertTrue(ReceivedDutyCheck.warnings(s, nov, RestRules(on = false)).isEmpty())
        assertTrue(ReceivedDutyCheck.warnings(s, YearMonth.of(2026, 12), RestRules()).isEmpty())
        assertTrue(ReceivedDutyCheck.warnings(s, nov, RestRules.Preset.LIGHT.rules).none { it.kind == DutyCheck.Kind.MANY_NIGHTS })
    }
}
