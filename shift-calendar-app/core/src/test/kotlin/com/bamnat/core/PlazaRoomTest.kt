package com.bamnat.core

import com.bamnat.core.plaza.PlazaNews
import com.bamnat.core.plaza.PlazaNewsItem
import com.bamnat.core.plaza.RoomCards
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.SetupFacts
import com.bamnat.core.plaza.SetupPatch
import com.bamnat.core.remote.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [PLAZA-09] 1.26 묶음 AF (결정 D79): 회사 방의 고친 점 카드, 이 값 쓰기, 광장 소식. */
class PlazaRoomTest {
    /** The user's own file as CompanyShare.export writes it with amounts: 식대 has the user's 150,000, 대근 its 1회 정액. */
    private val mine = """{"app":"com.bamnat.app","format":"bamnat-company","v":2,"company":"",
        "shiftTypes":[{"name":"주간","label":"주","color":1,"start":"07:00","end":"19:00","break":60,"off":false},
          {"name":"야간","label":"야","color":2,"start":"19:00","end":"07:00","break":60,"off":false},
          {"name":"휴무","label":"휴","color":3,"break":0,"off":true}],
        "extraKinds":"v2\n3|대근|대|4294198070|S,-|BY_DAY|1|0","extraKindShifts":{},"extraKindShiftSets":{},
        "rotation":{"anchor":"2026-10-01","steps":["주간","주간","야간","야간",null,null]},
        "pay":{"fiveOrMore":true,"monthlyHours":209,"holidayMode":"FULL","weekStart":"MONDAY","withAmounts":true,
          "rules":{"ot":null,"night":0.5,"restDay":"LAW","weeklyHolidayDay":"FIRST_REST","publicHolidays":true,
            "extra":{"3":{"basis":"FIXED_PER_TIME","amount":70000,"bonus":0}}},
          "items":[{"id":"mine1","name":"식대","amount":{"entries":[{"from":"2020-01-01","value":150000}],"raise":null},"cadence":"MONTHLY","types":[]}]}}"""

    private fun edit(f: (SetupEdit) -> SetupEdit): String = f(SetupEdit.of(mine)!!).text
    private fun fact(text: String, key: String) = SetupFacts.read(text).firstOrNull { it.key == key }?.value

    @Test fun `이 값 쓰기 takes one item and leaves every other value and amount as it was`() {
        val theirs = edit { it.rate("night", 0.6).rate("ot", 0.5) }
        val out = SetupPatch.take(mine, theirs, "rate:night")!!
        assertEquals("60%", fact(out, "rate:night"))
        // Only that one: 연장 stays the law's, the order and the user's own amounts stay.
        assertEquals("법대로", fact(out, "rate:ot"))
        assertEquals(fact(mine, "cycle:rotation"), fact(out, "cycle:rotation"))
        val pay = (Json.parse(out) as Map<*, *>)["pay"] as Map<*, *>
        assertEquals(70000L, (((pay["rules"] as Map<*, *>)["extra"] as Map<*, *>)["3"] as Map<*, *>)["amount"])
        assertTrue(out.contains("150000"))
        // Nothing to take when the value is the same.
        assertNull(SetupPatch.take(mine, mine, "rate:night"))
    }

    @Test fun `shift times, a kind's rule and an allowance's way come over without their amounts`() {
        val theirs = edit { it.shiftTime("야간", "18:00", "06:00", 60).kindRule("3", "HOURLY_RATE", multiplier = 2.0) }
        assertEquals("18:00~06:00 · 휴게 60분", fact(SetupPatch.take(mine, theirs, "shift:야간")!!, "shift:야간"))
        val kind = SetupPatch.take(mine, theirs, "kind:대근")!!
        assertTrue(fact(kind, "kind:대근")!!.contains("통상시급 × 200%"))
        val rule = ((((Json.parse(kind) as Map<*, *>)["pay"] as Map<*, *>)["rules"] as Map<*, *>)["extra"] as Map<*, *>)["3"] as Map<*, *>
        assertEquals(70000L, rule["amount"])
        // 식대 paid per work day: the way comes, the user's 150,000 stays; an allowance new to the user comes without one.
        val withItems = theirs.replace("\"cadence\":\"MONTHLY\"", "\"cadence\":\"PER_WORK_DAY\"").replace("150000", "0")
        val item = SetupPatch.take(mine, withItems, "item:식대")!!
        assertEquals("근무일마다", fact(item, "item:식대"))
        assertTrue(item.contains("150000"))
        assertTrue(item.contains("\"mine1\""))
    }

    @Test fun `the order of work and pay without a pay setup cannot be taken alone`() {
        val theirs = edit { it.rotation(listOf("주간", "야간", null)) }
        assertFalse(SetupPatch.canTake("cycle:rotation"))
        assertNull(SetupPatch.take(mine, theirs, "cycle:rotation"))
        val noPay = mine.substringBefore(",\n        \"pay\"") + "}"
        assertNull(SetupPatch.take(noPay, edit { it.rate("night", 0.6) }, "rate:night"))
    }

    @Test fun `cards add up the people behind the same value and say it in a line`() {
        val sixty = edit { it.rate("night", 0.6) }
        val sixtyMeal = edit { it.rate("night", 0.6).shiftTime("주간", "08:00", "20:00", 60) }
        val cards = RoomCards.cards(mine, listOf(RoomCards.Version("a", sixty, 2), RoomCards.Version("b", sixtyMeal, 1),
            RoomCards.Version("c", mine, 5)))
        val night = cards.first()
        assertEquals("rate:night", night.key)
        assertEquals(3, night.persons)
        assertEquals("a", night.from)
        assertEquals("야간 50% → 60%로 바꾼 사람 3명", night.text)
        assertEquals("주간 07:00~19:00 · 휴게 60분 → 08:00~20:00 · 휴게 60분으로 바꾼 사람 1명", cards[1].text)
        assertEquals(2, cards.size)
        val order = RoomCards.cards(mine, listOf(RoomCards.Version("d", edit { it.rotation(listOf("주간", "야간", null)) }, 1))).single()
        assertFalse(order.canTake)
    }

    @Test fun `a member hears of other versions until the company settles`() {
        assertEquals("가나공업 광장에 다르게 쓰는 판이 2개 올라왔어요. 눌러서 바뀐 값만 봐요.",
            PlazaNews.line(PlazaNewsItem("room", company = "가나공업", count = 2)))
        assertEquals("광장 소식", PlazaNews.text(listOf(PlazaNewsItem("room", company = "가나공업", count = 1)))!!.title)
    }
}
