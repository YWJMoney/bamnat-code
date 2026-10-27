package com.bamnat.core

import com.bamnat.core.plaza.DeptGroups
import com.bamnat.core.plaza.DeptGroups.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** [PLZD-07][PLZD-10] 1.30 묶음 BD: one 대표 per department, the rest folded under it, and the one-line kind of work. */
class DeptGroupsTest {
    @Test fun `each department has one lead and the others under it`() {
        val g = DeptGroups.of(listOf(
            Item("a", "생산팀", adopted = true, support = 3, persons = 3),
            Item("b", "생산 팀", adopted = false, support = 5, persons = 2),
            Item("c", "", adopted = false, support = 1, persons = 1),
            Item("d", "포장팀", adopted = false, support = 2, persons = 2),
            Item("e", "포장팀", adopted = false, support = 4, persons = 3),
        ))
        // 회사 전체 first, then the departments most stand behind.
        assertEquals(listOf("", "생산팀", "포장팀"), g.map { it.dept })
        assertEquals("회사 전체", g[0].title)
        // The one in use leads its department even when another has more support; spacing makes no new department.
        assertEquals("a", g[1].lead.id)
        assertEquals(listOf("b"), g[1].others.map { it.id })
        // Without one in use, the most support.
        assertEquals("e", g[2].lead.id)
        assertEquals(listOf("d"), g[2].others.map { it.id })
    }

    @Test fun `the server's lead mark wins, and a doubted version never leads`() {
        val g = DeptGroups.of(listOf(
            Item("a", "생산팀", adopted = true, doubted = true, support = 9),
            Item("b", "생산팀", adopted = false, support = 1),
            Item("c", "생산팀", adopted = false, lead = true, support = 2),
        )).single()
        assertEquals("c", g.lead.id)
        assertEquals(listOf("b", "a").sorted(), g.others.map { it.id }.sorted())
        val alone = DeptGroups.of(listOf(Item("x", "", adopted = false, doubted = true))).single()
        assertEquals("x", alone.lead.id)
    }

    @Test fun `the kind of work in one line`() {
        assertEquals("2교대 · 주주야야휴휴 · 급여 방식 있음", DeptGroups.summary(file(""""주간","주간","야간","야간","휴무",null""", pay = true)))
        assertEquals("2교대 · 주야휴휴", DeptGroups.summary(file(""""주간","야간",null,null,"주간","야간",null,null""")))
        val long = (1..14).joinToString(",") { if (it % 2 == 0) "\"야간\"" else "\"주간\"" } + ",null"
        assertEquals("2교대 · 15일 순서", DeptGroups.summary(file(long)))
        assertNull(DeptGroups.summary("{}"))
    }

    @Test fun `a calendar of another shape is said before it is used`() {
        val two = file(""""주간","주간","야간","야간",null,null""")
        val three = """{"format":"bamnat-company","shiftTypes":[{"name":"주간","start":"06:00","end":"14:00"},{"name":"오후","start":"14:00","end":"22:00"},""" +
            """{"name":"야간","start":"22:00","end":"06:00"}],"rotation":{"steps":["주간","오후","야간",null]}}"""
        assertEquals("내 달력은 3교대인데 이 판은 2교대예요", DeptGroups.mismatch(three, two))
        assertEquals("내 달력은 4일 순서인데 이 판은 6일 순서예요", DeptGroups.mismatch(file(""""주간","야간",null,null"""), two))
        assertNull(DeptGroups.mismatch(two, two))
        assertNull(DeptGroups.mismatch(null, two))
    }

    private fun file(steps: String, pay: Boolean = false) =
        """{"format":"bamnat-company","shiftTypes":[{"name":"주간","start":"08:00","end":"20:00"},{"name":"야간","start":"20:00","end":"08:00"},""" +
            """{"name":"휴무","off":true}],"rotation":{"steps":[$steps]}""" + (if (pay) ""","pay":{"fiveOrMore":true,"items":[]}""" else "") + "}"
}
