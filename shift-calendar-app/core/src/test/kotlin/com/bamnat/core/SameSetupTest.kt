package com.bamnat.core

import com.bamnat.core.plaza.SameSetup
import com.bamnat.core.plaza.SetupEdit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PLZD-01][PLZD-02][PLZD-03] 1.30 묶음 BD: what a setup about to go is beside the plaza's version: the same (another 조's
 * day, pay left out), another shape (2교대 against 3교대, another order length, 요일별, 근무표), or a change to propose.
 */
class SameSetupTest {
    private fun file(steps: String, shifts: String = TWO, pay: String? = PAY, extra: String = "") =
        """{"format":"bamnat-company","v":3,"company":"","shiftTypes":[$shifts],"rotation":{"steps":[$steps]}$extra""" +
            (pay?.let { ""","pay":$it""" } ?: "") + "}"

    private val two = file(""""주간","주간","야간","야간",null,null""")

    @Test fun `the same order from another day is the same setup`() {
        // A coworker on another 조 is two days on: 야간 야간 휴 휴 주간 주간.
        val other = file(""""야간","야간",null,null,"주간","주간"""")
        val v = SameSetup.compare(other, two)
        assertEquals(SameSetup.Kind.SAME, v.kind)
        assertTrue(v.rows.isEmpty())
        // Written twice over it is still one turn.
        assertEquals(SameSetup.Kind.SAME, SameSetup.compare(file(""""주간","주간","야간","야간",null,null,"주간","주간","야간","야간",null,null"""), two).kind)
    }

    @Test fun `the work alone adds nothing to a version with its pay`() {
        val workOnly = file(""""주간","주간","야간","야간",null,null""", pay = null)
        assertEquals(SameSetup.Kind.SAME, SameSetup.compare(workOnly, two).kind)
        // Pay the plaza hides from a phone that has not joined is not compared, and the verdict says so.
        val hidden = file(""""주간","주간","야간","야간",null,null""", pay = null)
        val v = SameSetup.compare(two, hidden, theirsPayHidden = true)
        assertEquals(SameSetup.Kind.SAME, v.kind)
        assertTrue(v.payUnknown)
    }

    @Test fun `pay added to a version that had none is new`() {
        val workOnly = file(""""주간","주간","야간","야간",null,null""", pay = null)
        val v = SameSetup.compare(two, workOnly)
        assertEquals(SameSetup.Kind.DIFFERENT, v.kind)
        assertTrue(v.rows.any { it.after != null && it.before == null })
    }

    @Test fun `three kinds of work against two is another shape`() {
        val three = file(""""주간","주간","오후","오후","야간","야간",null,null""", shifts = THREE)
        val v = SameSetup.compare(three, two)
        assertEquals(SameSetup.Kind.SHAPE, v.kind)
        assertEquals(SameSetup.Shape(3, 8), v.mine)
        assertEquals(SameSetup.Shape(2, 6), v.theirs)
        assertEquals("3교대 · 8일 순서", v.mine!!.words)
        assertEquals("2교대 · 6일 순서", v.theirs!!.words)
    }

    @Test fun `another order length is another shape, a changed time is not`() {
        assertEquals(SameSetup.Kind.SHAPE, SameSetup.compare(file(""""주간","야간",null,null"""), two).kind)
        // Two names with the same times are one kind of work.
        val renamed = TWO.replace("\"야간\"", "\"밤\"") + """,{"name":"주간2","start":"08:00","end":"20:00","break":60}"""
        assertEquals(SameSetup.Shape(2, 6), SameSetup.shape(file(""""주간","주간2","밤","밤",null,null""", shifts = renamed)))
        val moved = SetupEdit.of(two)!!.shiftTime("주간", "07:00", "19:00", 60).text
        val v = SameSetup.compare(moved, two)
        assertEquals(SameSetup.Kind.DIFFERENT, v.kind)
        assertEquals(listOf("주간"), v.rows.map { it.label })
    }

    @Test fun `a week's days and a month's roster have their own shapes`() {
        val weekly = """{"format":"bamnat-company","v":3,"shiftTypes":[$TWO],"weekly":["주간","주간","주간","주간","주간",null,null]}"""
        assertEquals(SameSetup.Shape(1, 7, weekly = true), SameSetup.shape(weekly))
        assertEquals("요일별 · 한 가지 근무", SameSetup.shape(weekly)!!.words)
        val roster = """{"format":"bamnat-company","v":3,"shiftTypes":[$THREE],"roster":true}"""
        assertEquals("매달 근무표", SameSetup.shape(roster)!!.words)
        assertEquals(SameSetup.Kind.SHAPE, SameSetup.compare(weekly, two).kind)
        assertFalse(SameSetup.sameShape(SameSetup.shape(roster), SameSetup.shape(two)))
    }

    @Test fun `a pay-only file has no shape and fits any`() {
        val payOnly = """{"format":"bamnat-company","v":3,"shiftTypes":[],"pay":$PAY}"""
        assertNull(SameSetup.shape(payOnly))
        assertTrue(SameSetup.sameShape(null, SameSetup.shape(two)))
        assertNull(SameSetup.shape("not json"))
    }

    @Test fun `a turn is found in any repetition`() {
        assertEquals(listOf(1, 2), SameSetup.period(listOf(1, 2, 1, 2, 1, 2)))
        assertEquals(listOf(1, 2, 3), SameSetup.period(listOf(1, 2, 3)))
        assertTrue(SameSetup.sameTurn(listOf("a", "b", "c"), listOf("c", "a", "b", "c", "a", "b")))
        assertFalse(SameSetup.sameTurn(listOf("a", "b", "c"), listOf("a", "c", "b")))
    }

    private companion object {
        const val TWO = """{"name":"주간","start":"08:00","end":"20:00","break":60},{"name":"야간","start":"20:00","end":"08:00","break":60},{"name":"휴무","off":true}"""
        const val THREE = """{"name":"주간","start":"06:00","end":"14:00","break":30},{"name":"오후","start":"14:00","end":"22:00","break":30},""" +
            """{"name":"야간","start":"22:00","end":"06:00","break":30},{"name":"휴무","off":true}"""
        const val PAY = """{"fiveOrMore":true,"monthlyHours":209,"withAmounts":false,"rules":{"night":0.5},"items":[]}"""
    }
}
