package com.bamnat.core

import com.bamnat.core.pay.BonusBasis
import com.bamnat.core.pay.ItemShare
import com.bamnat.core.plaza.ProposalDraft
import com.bamnat.core.plaza.SetupEdit
import com.bamnat.core.plaza.SetupFacts
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [ITEM-08][ITEM-09] 1.30 묶음 BC: the proposal form's allowances (더하기·빼기·이름·누가 정하나·%·통상임금) and the 통상시급
 * hours, on the company file as the server keeps it, and the ready proposal for different hours.
 */
class SetupEditItemsTest {
    private val company = """{"app":"com.bamnat.app","format":"bamnat-company","v":2,"company":"",
        "shiftTypes":[{"name":"주간","label":"주","color":4281352429,"start":"07:00","end":"19:00","break":60,"off":false}],
        "pay":{"fiveOrMore":true,"monthlyHours":209,"holidayMode":"FULL","withAmounts":false,
          "rules":{"ot":null,"night":0.5,"restDay":"LAW"},
          "items":[{"id":"c1","name":"식대","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[]},
            {"id":"c2","name":"자격수당","amount":{"entries":[],"raise":null},"cadence":"MONTHLY","types":[],"ordinary":true}]}}"""

    private fun edit() = SetupEdit.of(company)!!

    @Test fun `an allowance is added with its guessed kind and no one's amount`() {
        val e = edit().addItem("위험예지수당")
        val a = e.allowances.single { it.name == "위험예지수당" }
        assertEquals(ItemShare.PERCENT, a.share)
        assertEquals(SetupEdit.DEFAULT_PERCENT, a.percent!!, 1e-9)
        assertEquals(BonusBasis.ORDINARY, a.percentOf)
        assertEquals("MONTHLY", a.cadence)
        assertNull(a.fixedAmount)
        // The file still has no amount for it.
        assertFalse(e.text.contains("\"entries\":[{"))
        // 위험예지수당 is in 통상임금 by its name too (OrdinaryGuess), so the words say both.
        assertEquals("매달 통상임금의 10% · 통상임금", SetupFacts.read(e.text).single { it.key == "item:위험예지수당" }.value)
        // 식대 guesses 회사 공통; a name it cannot guess is 사람마다.
        assertEquals(ItemShare.FIXED, edit().addItem("교통비").allowances.last().share)
        assertEquals(ItemShare.PERSONAL, edit().addItem("기타 수당").allowances.last().share)
    }

    @Test fun `a blank, a taken name or one too many changes nothing`() {
        val e = edit()
        assertSame(e, e.addItem("  "))
        assertSame(e, e.addItem("식대"))
        var many = e
        repeat(SetupEdit.MAX_ITEMS + 3) { many = many.addItem("수당$it") }
        assertEquals(SetupEdit.MAX_ITEMS, many.allowances.size)
        assertEquals(SetupEdit.MAX_ITEM_NAME, e.addItem("가".repeat(40)).allowances.last().name.length)
    }

    @Test fun `an allowance is taken out and renamed`() {
        val e = edit().removeItem("식대")
        assertEquals(listOf("자격수당"), e.allowances.map { it.name })
        val r = edit().renameItem("식대", "중식대")
        assertEquals(listOf("중식대", "자격수당"), r.allowances.map { it.name })
        assertEquals(listOf("식대", "자격수당"), edit().renameItem("식대", "자격수당").allowances.map { it.name })
        assertEquals(listOf("식대", "자격수당"), edit().renameItem("식대", " ").allowances.map { it.name })
    }

    @Test fun `who sets the amount, the company's one amount goes only with 회사 공통`() {
        val fixed = edit().itemShare("식대", ItemShare.FIXED, fixedAmount = 200_000)
        assertEquals(200_000L, fixed.allowances.first().fixedAmount)
        assertEquals("매달 회사 공통 200,000원", SetupFacts.read(fixed.text).single { it.key == "item:식대" }.value)
        // Too much is the server's limit.
        assertEquals(SetupEdit.MAX_FIXED, edit().itemShare("식대", ItemShare.FIXED, fixedAmount = 99_000_000).allowances.first().fixedAmount)
        // To 사람마다: no share, no amount, no percent in the file.
        val personal = fixed.itemShare("식대", ItemShare.PERSONAL)
        assertEquals(ItemShare.PERSONAL, personal.allowances.first().share)
        assertNull(personal.allowances.first().fixedAmount)
        assertFalse(personal.text.contains("\"share\""))
        assertFalse(personal.text.contains("fixedAmount"))
        // A percent is paid every month, kept within 0.1%~500%, and drops the company amount.
        val bonus = edit().itemCadence("식대", "BONUS").itemShare("식대", ItemShare.PERCENT, percent = 9.0, percentOf = BonusBasis.BASE_FIXED)
        val p = bonus.allowances.first()
        assertEquals("MONTHLY", p.cadence)
        assertEquals(5.0, p.percent!!, 1e-9)
        assertEquals(BonusBasis.BASE_FIXED, p.percentOf)
        assertNull(fixed.itemShare("식대", ItemShare.PERCENT).allowances.first().fixedAmount)
    }

    @Test fun `통상임금 and the hours`() {
        val e = edit().itemOrdinary("식대", true)
        assertTrue(e.allowances.first().ordinary)
        assertEquals("매달 통상임금", SetupFacts.read(e.text).single { it.key == "item:식대" }.value)
        assertEquals(209, edit().monthlyHours)
        assertEquals(180, edit().monthlyHours(180).monthlyHours)
        assertEquals(SetupEdit.HOURS_MAX, edit().monthlyHours(999).monthlyHours)
        assertEquals(SetupEdit.HOURS_MIN, edit().monthlyHours(10).monthlyHours)
        val hours = SetupFacts.read(edit().monthlyHours(180).text).single { it.key == "place:hours" }
        assertEquals("180시간", hours.value)
        assertEquals("hours", hours.field)
    }

    @Test fun `different hours make a ready proposal`() {
        val d = ProposalDraft.hours(company, 180)!!
        assertEquals(company, d.base)
        assertEquals(180, SetupEdit.of(d.text)!!.monthlyHours)
        assertEquals(1, d.lines.size)
        assertEquals("hours", d.lines.single().field)
        assertEquals("월 소정 시간: 209시간", d.lines.single().now)
        assertEquals("월 소정 시간: 180시간", d.lines.single().want)
        assertEquals("명세서에 통상시급 기준 시간이 180시간으로 나와요 (지금 209시간)", d.why)
        assertNull(ProposalDraft.hours(company, 209))
        assertNull(ProposalDraft.hours(company, 40))
        assertNull(ProposalDraft.hours("{}", 180))
        // A file without pay has no hours to change.
        assertNull(ProposalDraft.hours("""{"app":"com.bamnat.app","format":"bamnat-company","v":2,"shiftTypes":[]}""", 180))
    }

    private val shiftsOnly = """{"app":"com.bamnat.app","format":"bamnat-company","v":2,"company":"",
        "shiftTypes":[{"name":"주간","label":"주","color":4281352429,"start":"07:00","end":"19:00","break":60,"off":false}],
        "rotation":{"anchor":"2026-10-01","steps":["주간",null]}}"""

    @Test fun `J133-82 a shifts-only setup takes the user's pay way, without shifts it does not have`() {
        val mine = SetupEdit.of(company.replace(""""c2","name":"자격수당"""",
            """"c3","name":"야간수당","amount":{"entries":[],"raise":null},"cadence":"PER_SHIFT_TYPE","types":["주간","야간"]},
            {"id":"c2","name":"자격수당""""))!!
        val e = SetupEdit.of(shiftsOnly)!!
        assertFalse(e.hasPay)
        val with = e.withPayOf(mine)
        assertTrue(with.hasPay)
        assertEquals(209, with.monthlyHours)
        assertEquals(listOf("식대", "야간수당", "자격수당"), with.allowances.map { it.name })
        // The shifts and their order stay this file's.
        assertEquals(e.rotation, with.rotation)
        assertFalse(with.text.contains("\"야간\""))
        assertTrue(with.text.contains("\"withAmounts\":false"))
        // Nothing to take from a file without pay.
        assertSame(e, e.withPayOf(e))
        // The compare shows the pay as what changed.
        assertTrue(com.bamnat.core.plaza.SetupCompare.rows(shiftsOnly, with.text).any { !it.same })
    }

    @Test fun `J133-82 the law's pay way starts with nothing but the law`() {
        val e = SetupEdit.of(shiftsOnly)!!.withLawPay(fiveOrMore = false)
        assertTrue(e.hasPay)
        assertFalse(e.fiveOrMore)
        assertEquals(SetupEdit.LAW_HOURS, e.monthlyHours)
        assertTrue(e.allowances.isEmpty())
        assertNull(e.rate("ot"))
        // It can be changed like any other pay way.
        assertEquals(1.5, e.rate("night", 1.5).rate("night")!!, 1e-9)
        assertEquals("식대", e.addItem("식대").allowances.single().name)
    }
}
