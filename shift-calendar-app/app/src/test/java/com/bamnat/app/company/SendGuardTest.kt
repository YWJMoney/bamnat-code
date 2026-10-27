package com.bamnat.app.company

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * [PLZD-01][PLZD-02][PLZD-03] 1.30 묶음 BD: what 내 설정 나누기 meets on a company page before it sends ([SendGuard.decide]): the
 * same rule (use it, its pay too), another shape (another department's name only), other values (a proposal on the 대표 with only
 * what differs), nothing in the department (it goes). Companies are made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SendGuardTest {
    private fun file(steps: String, shifts: String = TWO, pay: String? = PAY) =
        """{"format":"bamnat-company","v":3,"company":"","shiftTypes":[$shifts],"rotation":{"anchor":"2026-10-01","steps":[$steps]}""" +
            (pay?.let { ""","pay":$it""" } ?: "") + "}"

    private val order = """"주간","주간","야간","야간",null,null"""
    private val lead = file(order)

    private fun setup(id: String, text: String, dept: String = "생산팀", adopted: Boolean = true, lead: Boolean = adopted,
        doubted: Boolean = false, payHidden: Boolean = false) =
        SharedSetup(id, adopted, 3, 3, 0, 3, dept, if (adopted) 1 else null, text, emptyList(), lead = lead, doubted = doubted, payHidden = payHidden)

    private fun page(vararg s: SharedSetup) = CompanyPage("c1", "(주)가상정밀", emptyList(), s.toList())

    @Test fun theSameRuleIsUsedNotSentAgainWithItsPayWhenMineHasNone() {
        val mine = file(""""야간","야간",null,null,"주간","주간"""", pay = null)
        val v = SendGuard.decide(page(setup("s1", lead)), "생산팀", mine)
        assertTrue(v is SendGuard.Same)
        v as SendGuard.Same
        assertEquals("s1", v.setup.id)
        assertTrue("the plaza's pay goes in with it", v.withPay)
        // With my own pay the same: nothing more to take.
        val same = SendGuard.decide(page(setup("s1", lead)), " 생산 팀 ", lead) as SendGuard.Same
        assertFalse(same.withPay)
        // Any version of the department, not only the 대표.
        val other = file(order, shifts =TWO.replace("08:00\",\"end\":\"20:00", "07:00\",\"end\":\"19:00"))
        val two = SendGuard.decide(page(setup("s1", lead), setup("s2", other, adopted = false)), "생산팀", other)
        assertEquals("s2", (two as SendGuard.Same).setup.id)
    }

    @Test fun anotherShapeGoesOnlyUnderAnotherName() {
        val three = file(""""주간","주간","오후","오후","야간","야간",null,null""", shifts = THREE)
        val v = SendGuard.decide(page(setup("s1", lead)), "생산팀", three)
        assertTrue(v is SendGuard.Shape)
        v as SendGuard.Shape
        assertEquals("3교대 · 8일 순서", v.mine)
        assertEquals("2교대 · 6일 순서", v.theirs)
        // Under its own department name the plaza has nothing yet: it goes.
        assertTrue(SendGuard.decide(page(setup("s1", lead)), "온산공장", three) is SendGuard.Clear)
        // A 대표 people say is wrong may be followed by another shape.
        assertTrue(SendGuard.decide(page(setup("s1", lead, doubted = true)), "생산팀", three) is SendGuard.Clear)
        // A company with nothing on the plaza at all: nothing to compare.
        assertTrue(SendGuard.decide(page(), "", three) is SendGuard.Clear)
    }

    @Test fun otherValuesAreProposedOnTheLeadWithOnlyWhatDiffers() {
        val moved = file(""""야간","야간",null,null,"주간","주간"""", pay = null,
            shifts = TWO.replace("08:00\",\"end\":\"20:00", "07:00\",\"end\":\"19:00"))
        val v = SendGuard.decide(page(setup("s1", lead)), "생산팀", moved, note = "10월부터 바뀌었어요")
        assertTrue(v is SendGuard.Differ)
        v as SendGuard.Differ
        assertEquals(1, v.rows.size)
        assertNotNull(v.draft)
        val d = v.draft!!
        assertEquals(lead, d.base)
        assertEquals("10월부터 바뀌었어요", d.why)
        assertEquals(1, d.lines.size)
        // The proposal keeps the 대표's pay and its order (mine is the same order from another day).
        val text = JSONObject(d.text)
        assertTrue(text.has("pay"))
        assertEquals(JSONObject(lead).getJSONObject("rotation").toString(), text.getJSONObject("rotation").toString())
        assertEquals("07:00", text.getJSONArray("shiftTypes").getJSONObject(0).getString("start"))
    }

    @Test fun aPayNotShownIsNeitherCalledTheSameNorProposed() {
        val workOnly = file(order, pay = null)
        // Same work, pay unknown: it goes, and the server counts it.
        assertTrue(SendGuard.decide(page(setup("s1", workOnly, payHidden = true)), "생산팀", lead) is SendGuard.Clear)
        // Other work, pay unknown: a new version only, no proposal that would take the pay out.
        val moved = file(order, shifts = TWO.replace("08:00\",\"end\":\"20:00", "07:00\",\"end\":\"19:00"))
        val v = SendGuard.decide(page(setup("s1", workOnly, payHidden = true)), "생산팀", moved) as SendGuard.Differ
        assertNull(v.draft)
    }

    @Test fun theLeadIsTheOneComparedWhenADepartmentHasMany() {
        val alt = file(""""주간","야간","주간","야간",null,null""", pay = null)
        val moved = file(order, shifts = TWO.replace("20:00\",\"end\":\"08:00", "21:00\",\"end\":\"09:00"))
        val v = SendGuard.decide(page(setup("s2", alt, adopted = false, lead = false), setup("s1", lead)), "생산팀", moved,
            resent = SentReport("r1", "(주)가상정밀", "생산팀", false, Instant.parse("2026-10-27T01:00:00Z")))
        assertEquals("s1", (v as SendGuard.Differ).lead.id)
        assertEquals("r1", v.resent?.id)
    }

    private companion object {
        const val TWO = """{"name":"주간","start":"08:00","end":"20:00","break":60},{"name":"야간","start":"20:00","end":"08:00","break":60},{"name":"휴무","off":true}"""
        const val THREE = """{"name":"주간","start":"06:00","end":"14:00","break":30},{"name":"오후","start":"14:00","end":"22:00","break":30},""" +
            """{"name":"야간","start":"22:00","end":"06:00","break":30},{"name":"휴무","off":true}"""
        const val PAY = """{"fiveOrMore":true,"monthlyHours":209,"withAmounts":false,"rules":{"night":0.5},"items":[]}"""
    }
}
