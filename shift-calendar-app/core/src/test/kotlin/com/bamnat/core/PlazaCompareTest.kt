package com.bamnat.core

import com.bamnat.core.plaza.Choseong
import com.bamnat.core.plaza.CompareRow
import com.bamnat.core.plaza.FactGroup
import com.bamnat.core.plaza.SetupCompare
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.SetupFacts
import com.bamnat.core.plaza.ShareAsk
import com.bamnat.core.remote.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PLAZA-02][PLAZA-03][PLAZA-04] 1.25 묶음 X: 회사 설정 항목별 비교·편집, 초성, 올리기 권유 간격 (U2·U26). */
class PlazaCompareTest {
    /** A company settings file as CompanyShare.exportForServer writes it (Android's JSONObject, `\/` and all). */
    private val company = """{"app":"com.bamnat.app","format":"bamnat-company","v":2,"company":"",
        "shiftTypes":[{"name":"주간","label":"주","color":4281352429,"start":"07:00","end":"19:00","break":60,"off":false},
          {"name":"야간","label":"야","color":4285291751,"start":"19:00","end":"07:00","break":60,"off":false},
          {"name":"휴무","label":"휴","color":4288585374,"break":0,"off":true}],
        "extraKinds":"v2\n3|대근|대|4294198070|S,-|BY_DAY|1|0","extraKindShifts":{},"extraKindShiftSets":{"3":["주간","야간"]},
        "rotation":{"anchor":"2026-10-01","steps":["주간","주간","야간","야간",null,null]},
        "pay":{"fiveOrMore":true,"monthlyHours":209,"holidayMode":"FULL","dailyLimit":-1,"weeklyLimit":-1,"scheduleIsRegular":false,
          "weekStart":"MONDAY","period":{"endDay":-1,"payDay":25,"payNextMonth":false},"withAmounts":false,
          "rules":{"ot":null,"night":0.5,"hw":null,"ho":null,"restDay":"LAW","weeklyHolidayDay":"FIRST_REST","publicHolidays":true,
            "extra":{"3":{"basis":"PERCENT_OF_BASE","multiplier":1.5,"amount":0,"min":0,"night":true,"percent":0.1,"cover":-1,
              "premiums":false,"bonus":0,"unit":"ORDINARY","unitMin":480,"adds":[]}},"extraByShift":{}},
          "items":[{"id":"c1","name":"식대","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[]},
            {"id":"c2","name":"상여","amount":{"entries":[],"raise":null},"cadence":"BONUS","types":[],"months":[6,12],"percentOfBase":1.0}]}}"""

    @Test fun `a setup reads as items in the server's words, keyed by name`() {
        val facts = SetupFacts.read(company).associateBy { it.key }
        assertEquals("07:00~19:00 · 휴게 60분", facts.getValue("shift:주간").value)
        assertEquals("쉬는 날", facts.getValue("shift:휴무").value)
        assertEquals("주간 주간 야간 야간 쉼 쉼 (6일)", facts.getValue("cycle:rotation").value)
        assertEquals("5인 이상", facts.getValue("place:size").value)
        assertEquals("50%", facts.getValue("rate:night").value)
        assertEquals("법대로", facts.getValue("rate:ot").value)
        assertEquals("쉬는 날 중 첫날", facts.getValue("rule:weeklyDay").value)
        assertEquals("주간·야간 대신 · 한 번에 기본급 10%", facts.getValue("kind:대근").value)
        assertEquals("6·12월 기본급 100%", facts.getValue("item:상여").value)
        assertEquals("bonus", facts.getValue("item:상여").field)
        assertEquals("allowance", facts.getValue("item:식대").field)
        // Groups come in the compare screen's order.
        val groups = SetupFacts.read(company).map { it.group.ordinal }
        assertEquals(groups.sorted(), groups)
    }

    @Test fun `not a company file reads as nothing`() {
        assertTrue(SetupFacts.read("{}").isEmpty())
        assertTrue(SetupFacts.read("not json").isEmpty())
        assertNull(SetupEdit.of("""{"format":"bamnat"}"""))
    }

    @Test fun `editing one item changes only that row and it becomes a proposal line`() {
        val edit = SetupEdit.of(company)!!
        val night = edit.rate("night", 1.0).shiftTime("주간", "07:00", "18:00", 60)
        val rows = SetupCompare.rows(company, night.text)
        val diff = rows.filter { !it.same }
        assertEquals(listOf("shift:주간", "rate:night"), diff.map { it.key })
        assertEquals("07:00~19:00 · 휴게 60분", diff[0].before)
        assertEquals("07:00~18:00 · 휴게 60분", diff[0].after)
        assertEquals(CompareRow.State.CHANGED, diff[1].state)
        val lines = SetupCompare.proposalLines(rows)
        assertEquals(listOf("shift", "night"), lines.map { it.field })
        assertEquals("야간: 50%", lines[1].now)
        assertEquals("야간: 100%", lines[1].want)
        assertEquals("다른 점 2가지: 주간 · 야간", SetupCompare.summary(rows))
        // The rest of the file is untouched: same facts everywhere else, still a file the server reads.
        val back = Json.parse(night.text) as Map<*, *>
        assertEquals("bamnat-company", back["format"])
        assertEquals(SetupFacts.read(company).size, night.facts.size)
    }

    @Test fun `the law's premium, the order, weekly days, kind rule and allowance cadence can all be changed`() {
        val e = SetupEdit.of(company)!!
            .rate("night", null)
            .rotation(listOf("주간", "야간", null, null))
            .weekly(listOf("주간", "주간", "주간", "주간", "주간", null, "없는 근무"))
            .kindRule("3", "HOURLY_RATE", multiplier = 2.0)
            .itemCadence("식대", "PER_WORK_DAY")
            .restDay("HOLIDAY")
            .fiveOrMore(false)
            .nightWindow("22:00", "06:00")
        val f = e.facts.associateBy { it.key }
        assertEquals("법대로", f.getValue("rate:night").value)
        assertEquals("주간 야간 쉼 쉼 (4일)", f.getValue("cycle:rotation").value)
        // A shift the file does not have is a day off, never a new name.
        assertEquals("월 주간 · 화 주간 · 수 주간 · 목 주간 · 금 주간 · 토 쉼 · 일 쉼", f.getValue("cycle:weekly").value)
        assertEquals("주간·야간 대신 · 통상시급 × 200%", f.getValue("kind:대근").value)
        assertEquals("근무일마다", f.getValue("item:식대").value)
        assertEquals("휴일근로로", f.getValue("rule:restDay").value)
        assertEquals("5인 미만", f.getValue("place:size").value)
        assertEquals("22:00~06:00", f.getValue("rate:window").value)
        assertEquals(listOf("주간", "야간", null, null), e.rotation)
        assertEquals(2.0, e.kindRules.single().multiplier, 0.0)
    }

    @Test fun `more than six differences keep five and say how many more`() {
        var e = SetupEdit.of(company)!!
        listOf("ot", "night", "hw", "ho").forEach { e = e.rate(it, 2.0) }
        e = e.shiftTime("주간", "08:00", "20:00", 30).shiftTime("야간", "20:00", "08:00", 30).restDay("OVERTIME")
        val lines = SetupCompare.proposalLines(SetupCompare.rows(company, e.text))
        assertEquals(SetupCompare.MAX_LINES, lines.size)
        assertEquals("그 밖에 2가지가 달라요 (비교 보기에서 모두 보여요)", lines.last().want)
        assertTrue(lines.all { it.now.length <= 80 && it.want.length <= 80 })
    }

    @Test fun `items only one side has are added or removed`() {
        val other = company.replace(""""name":"식대"""", """"name":"교통비"""")
        val rows = SetupCompare.rows(company, other)
        assertEquals(CompareRow.State.REMOVED, rows.first { it.key == "item:식대" }.state)
        assertEquals(CompareRow.State.ADDED, rows.first { it.key == "item:교통비" }.state)
        assertEquals(FactGroup.ITEM, rows.first { it.key == "item:교통비" }.group)
        assertEquals("교통비: 없음", SetupCompare.proposalLines(rows).first { it.want.startsWith("교통비") }.now)
    }

    @Test fun `weekly days and a monthly roster are items too, for weekday and part-time and nurse work`() {
        val weekly = company.replace(""""rotation":{"anchor":"2026-10-01","steps":["주간","주간","야간","야간",null,null]},""",
            """"weekly":["주간","주간","주간","주간","주간",null,null],"roster":true,""")
        val f = SetupFacts.read(weekly).associateBy { it.key }
        assertNull(f["cycle:rotation"])
        assertEquals("월 주간 · 화 주간 · 수 주간 · 목 주간 · 금 주간 · 토 쉼 · 일 쉼", f.getValue("cycle:weekly").value)
        assertEquals("매달 근무표(순서 없음)", f.getValue("cycle:roster").value)
        assertEquals("order", f.getValue("cycle:weekly").field)
    }

    @Test fun `the written file reads back the same`() {
        val e = SetupEdit.of(company)!!.rate("ot", 0.75)
        assertEquals(e.text, SetupEdit.of(e.text)!!.text)
        assertTrue(e.text.contains("\"ot\":0.75"))
        assertTrue(e.text.contains("\"extraKinds\":\"v2\\n3|대근"))
    }

    @Test fun `초성 of a name, and which queries are 초성 only`() {
        assertEquals("ㅅㅅㅈㅈ", Choseong.of("삼성전자"))
        assertEquals("ㅎㄷㅈㄷㅊ", Choseong.of("현대 자동차"))
        assertEquals("ㅅㅇㄷㅂㅇabc1", Choseong.of("서울대병원 ABC-1"))
        assertTrue(Choseong.isQuery("ㅅㅅㅈ"))
        assertTrue(Choseong.isQuery("ㅎㄷ ㅈㄷㅊ"))
        assertFalse(Choseong.isQuery("ㅅ"))
        assertFalse(Choseong.isQuery("삼ㅅ"))
    }

    @Test fun `asking again waits 14 then 30 then 90 days, never after stop`() {
        val day = 24L * 60 * 60 * 1000
        assertEquals(14L, ShareAsk.waitDays(1))
        assertEquals(30L, ShareAsk.waitDays(2))
        assertEquals(90L, ShareAsk.waitDays(3))
        assertEquals(90L, ShareAsk.waitDays(9))
        assertEquals(1000 + 14 * day, ShareAsk.nextDue(1, 1000))
        assertTrue(ShareAsk.due(never = false, due = 0, now = 1, usesCompanySetup = false))
        assertFalse(ShareAsk.due(never = true, due = 0, now = 1, usesCompanySetup = false))
        assertFalse(ShareAsk.due(never = false, due = 5, now = 1, usesCompanySetup = false))
        // Someone using a company's setup as it is has nothing of their own to share.
        assertFalse(ShareAsk.due(never = false, due = 0, now = 1, usesCompanySetup = true))
    }

    @Test fun `every proposal field is one the server knows`() {
        val known = setOf("shift", "order", "break", "overtime", "night", "holiday", "allowance", "bonus", "leave", "other")
        val withAll = SetupEdit.of(company)!!.nightWindow("22:00", "06:00").weekly(List(7) { "주간" })
        assertTrue(withAll.facts.all { it.field in known })
        assertNotNull(withAll.facts.firstOrNull { it.key == "rule:weeklyDay" })
    }
}
