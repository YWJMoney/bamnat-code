package com.bamnat.core

import com.bamnat.core.pay.PayslipCheck
import com.bamnat.core.pay.SlipProof
import com.bamnat.core.plaza.ChangeAsk
import com.bamnat.core.plaza.PlazaNews
import com.bamnat.core.plaza.PlazaNewsItem
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.YearMonth

/** [PLAZA-30][PLAZA-32][PLAZA-33][PLAZA-34][PLAZA-35] 1.27 묶음 AI: 명세서 근거, 유형별 광장, 바뀐 내용 묻기, 정보 광장 소식. */
class PlazaAITest {
    private val today = YearMonth.of(2026, 10)
    private fun check(gross: Long, app: Long, month: YearMonth = YearMonth.of(2026, 9), source: String = SlipProof.MATCH) =
        PayslipCheck(month, gross, app, 0L, source)

    @Test fun `a tick rests only on a matched month this calendar kept`() {
        assertNull(SlipProof.of(null, today))
        // Within 0.5% (at least 1,000원): a match.
        val ok = SlipProof.of(check(3_000_000, 3_010_000), today)!!
        assertTrue(ok.counts)
        assertEquals(YearMonth.of(2026, 9), ok.month)
        // Too far apart for 맞춰 보기, but within 1% for 회사 방식 맞추기.
        assertFalse(SlipProof.of(check(3_000_000, 3_025_000), today)!!.counts)
        assertTrue(SlipProof.of(check(3_000_000, 3_025_000, source = SlipProof.RULES), today)!!.counts)
        // Too old, from the future, or with nothing in it: no proof.
        assertNull(SlipProof.of(check(3_000_000, 3_000_000, YearMonth.of(2025, 8)), today))
        assertNull(SlipProof.of(check(3_000_000, 3_000_000, YearMonth.of(2026, 11)), today))
        assertNull(SlipProof.of(check(0, 3_000_000), today))
        // The words never carry an amount.
        for (p in listOf(null, ok, SlipProof.of(check(3_000_000, 3_200_000), today))) {
            assertFalse(Regex("\\d+원|,\\d{3}").containsMatchIn(SlipProof.words(p)))
        }
        assertTrue(SlipProof.words(ok).startsWith("9월 명세서로 맞춰 본 기록이 있어요"))
    }

    @Test fun `each kind of work has its own plaza, a company's members their room`() {
        assertEquals(StylePlaza.Top.RULES, StylePlaza.plan(WorkStyle.SHIFT).top)
        assertNull(StylePlaza.plan(WorkStyle.DAY).room)
        // [RCP-34] 1.34: the rule plaza of 교대·일근 links their own plaza, which is a talk.
        assertEquals(StylePlaza.DAY, StylePlaza.plan(WorkStyle.DAY).alsoRoom)
        assertEquals(listOf("교대 광장", StylePlaza.SHIFT, StylePlaza.Top.TALK), StylePlaza.own(WorkStyle.SHIFT).let { listOf(it.title, it.room, it.top) })
        assertEquals(StylePlaza.FIELD, StylePlaza.own(WorkStyle.FIELD).room)
        val field = StylePlaza.plan(WorkStyle.FIELD)
        assertEquals(listOf("현장 광장", StylePlaza.FIELD, "construction", "day"), listOf(field.title, field.room, field.rates!!.job, field.rates!!.unit))
        assertEquals("hour", StylePlaza.plan(WorkStyle.HOURLY).rates!!.unit)
        assertEquals(StylePlaza.SHOP, StylePlaza.plan(WorkStyle.BUSINESS).room)
        val invest = StylePlaza.plan(WorkStyle.INVEST, "abcdefabcdef" to "예시상사")
        assertEquals("투자 이야기", invest.title)
        assertTrue(invest.note.contains("종목 추천"))
        val room = StylePlaza.plan(WorkStyle.FIELD, "abcdefabcdef" to "예시상사")
        assertEquals(listOf("‘예시상사’ 정보 광장", "abcdefabcdef", StylePlaza.FIELD), listOf(room.title, room.room, room.alsoRoom))
        assertEquals(StylePlaza.SHIFT, StylePlaza.plan(WorkStyle.SHIFT, "abcdefabcdef" to "예시상사").alsoRoom)
    }

    @Test fun `a change says what changed, the same values saved again say nothing`() {
        val base = SetupEdit.of(PlazaCompareFixture.company)!!
        assertEquals(ChangeAsk.Result.First, ChangeAsk.of(null, base.text))
        assertEquals(ChangeAsk.Result.Same, ChangeAsk.of(base.text, base.text))
        val night = base.rate("night", 0.6)
        val r = ChangeAsk.of(base.text, night.text)
        assertTrue(r is ChangeAsk.Result.Changed)
        assertEquals("야간 50% → 60%", ChangeAsk.line(r))
        assertNull(ChangeAsk.line(ChangeAsk.Result.Same))
        // More than three: "외 n가지".
        val many = night.rate("ot", 0.6).rate("hw", 0.6).rate("ho", 0.6)
        val m = ChangeAsk.of(base.text, many.text) as ChangeAsk.Result.Changed
        assertEquals(ChangeAsk.MAX_LINES, m.lines.size)
        assertTrue(ChangeAsk.line(m)!!.endsWith("외 ${m.more}가지"))
    }

    @Test fun `new posts in a joined room are one line of 광장 소식`() {
        assertEquals("예시상사 정보 광장에 새 글이 3개 올라왔어요.", PlazaNews.line(PlazaNewsItem("room_posts", company = "예시상사", count = 3)))
        assertEquals("참여한 회사 정보 광장에 새 글이 1개 올라왔어요.", PlazaNews.line(PlazaNewsItem("room_posts")))
    }
}

/** The company file of [PlazaCompareTest], for other tests. */
internal object PlazaCompareFixture {
    val company = """{"app":"com.bamnat.app","format":"bamnat-company","v":2,"company":"",
        "shiftTypes":[{"name":"주간","label":"주","color":4281352429,"start":"07:00","end":"19:00","break":60,"off":false},
          {"name":"야간","label":"야","color":4285291751,"start":"19:00","end":"07:00","break":60,"off":false},
          {"name":"휴무","label":"휴","color":4288585374,"break":0,"off":true}],
        "rotation":{"anchor":"2026-10-01","steps":["주간","주간","야간","야간",null,null]},
        "pay":{"fiveOrMore":true,"monthlyHours":209,"holidayMode":"FULL","dailyLimit":-1,"weeklyLimit":-1,"scheduleIsRegular":false,
          "weekStart":"MONDAY","period":{"endDay":-1,"payDay":25,"payNextMonth":false},"withAmounts":false,
          "rules":{"ot":null,"night":0.5,"hw":null,"ho":null,"restDay":"LAW","weeklyHolidayDay":"FIRST_REST","publicHolidays":true},
          "items":[]}}"""
}
