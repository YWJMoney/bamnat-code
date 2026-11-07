package com.bamnat.core

import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCell
import com.bamnat.core.team.TeamGridImport
import com.bamnat.core.team.TeamGridImport.Row
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.38 어떤 형식이든 가져오기 → 팀 근무표 (사용자 2026-11-05). */
class TeamGridImportTest {
    private val shifts = listOf(
        TeamShift(1, "D", "주간", 0, false), TeamShift(2, "E", "저녁", 0, false), TeamShift(3, "N", "야간", 0, false), TeamShift(4, "O", "휴무", 0, true),
    )
    private val month = YearMonth.of(2026, 12)
    private fun d(n: Int) = month.atDay(n)
    private val members = listOf(TeamMember("m1", "t", "김민지", "책임"), TeamMember("m2", "t", "이 준호"), TeamMember("m3", "t", "서연"))

    @Test fun codesPeopleWriteAreGuessed() {
        val g = TeamGridImport.guessCodes(listOf("D", "e", "나이트", "휴", "OFF", "주간", "N1", "", "/"), shifts)
        assertEquals(1L, g["D"]); assertEquals(2L, g["e"]); assertEquals(3L, g["나이트"]); assertEquals(4L, g["휴"])
        assertEquals(4L, g["OFF"]); assertEquals(1L, g["주간"]); assertEquals(4L, g["/"])
        assertNull("모르는 코드는 리더가 짝지어요", g["N1"])
        assertNull(g[""])
    }

    @Test fun namesMatchWithRolesAndSpacesAside() {
        val rows = listOf(Row("김민지 (책임)", null, emptyMap()), Row("이준호", null, emptyMap()), Row("박하은", null, emptyMap()), Row("서연", null, emptyMap()))
        assertEquals(listOf("m1", "m2", null, "m3"), TeamGridImport.matchNames(rows, members))
    }

    @Test fun rowsGoOnTheBoardAndUnsureCellsComeFirst() {
        val board = TeamBoard(month, members, mapOf(("m3" to d(2)) to TeamCell("m3", d(2), 2, locked = true)))
        val rows = listOf(
            Row("김민지", null, mapOf(d(1) to "D", d(2) to "N1", d(3) to "휴", LocalDate.of(2026, 11, 30) to "E"), doubtful = setOf(d(3))),
            Row("서연", null, mapOf(d(1) to "N", d(2) to "D")),
        )
        val codes = TeamGridImport.guessCodes(rows.flatMap { it.cells.values }, shifts)
        val a = TeamGridImport.apply(board, rows, listOf("m1", "m3"), codes)
        assertEquals(1L, a.board.cell("m1", d(1))?.shiftId)
        assertNull("짝 없는 코드는 칸을 그대로 두고", a.board.cell("m1", d(2)))
        assertTrue("확인할 칸으로", ("m1" to d(2)) in a.toCheck)
        assertEquals(4L, a.board.cell("m1", d(3))?.shiftId)
        assertTrue("자신 없는 칸도 먼저", ("m1" to d(3)) in a.toCheck)
        assertEquals(1, a.outside)
        assertEquals("잠근 칸은 지키고", 2L, a.board.cell("m3", d(2))?.shiftId)
        assertEquals(1, a.keptLocked)
        assertEquals(3, a.written)
    }

    @Test fun rowsWithNoMemberBecomeNewOnesAfterTheTeam() {
        val rows = listOf(Row("김민지", null, emptyMap()), Row("박하은", "책임", emptyMap()), Row("정우", null, emptyMap()), Row("한별", null, emptyMap()))
        var n = 0
        val (made, ids) = TeamGridImport.newMembers(rows, listOf("m1", null, null, null), skipped = setOf(2), teamId = "t", members = members, limit = 4) { "new${n++}" }
        assertEquals(listOf("m1", "new0", null, null), ids)
        assertEquals("박하은", made.single().name)
        assertEquals(com.bamnat.core.team.Skill.CHARGE, made.single().skill)
        assertEquals("팀 순서 뒤에", 1, made.single().sort)
    }

    @Test fun theBigCodesAreListedFirst() {
        val rows = listOf(Row("a", null, mapOf(d(1) to "D", d(2) to "D", d(3) to "N1")), Row("b", null, mapOf(d(1) to "D", d(2) to "", d(3) to "N1", d(4) to "O")))
        assertEquals(listOf("D" to 3, "N1" to 2, "O" to 1), TeamGridImport.codeCounts(rows))
    }

    // 1.38 [P3-L-17] 종이 신청표: a few days each, 휴·연·교·N빼, read as 신청.
    @Test fun aRequestSheetIsReadAsRequests() {
        val sheet = listOf(
            TeamGridImport.Row("김민지", cells = mapOf(month.atDay(3) to "휴", month.atDay(4) to "연", month.atDay(20) to "교")),
            TeamGridImport.Row("이준호", cells = mapOf(month.atDay(24) to "OFF", month.atDay(25) to "N빼")),
            TeamGridImport.Row("서연", cells = mapOf(month.atDay(10) to "D")),
        )
        assertTrue(TeamGridImport.looksLikeRequests(sheet, 31, shifts))
        val full = listOf(TeamGridImport.Row("김민지", cells = (1..31).associate { month.atDay(it) to if (it % 4 == 0) "O" else "D" }))
        assertFalse("다 칠한 근무표는 근무표로", TeamGridImport.looksLikeRequests(full, 31, shifts))
        val codes = TeamGridImport.guessWishes(listOf("휴", "연", "교", "OFF", "N빼", "D"), shifts)
        assertEquals("OFF", codes["휴"]); assertEquals("LEAVE", codes["연"]); assertEquals("EDU", codes["교"]); assertEquals("AVOID_NIGHT", codes["N빼"])
        assertTrue(codes["D"]!!.startsWith("SHIFT:"))
        val w = TeamGridImport.wishes(sheet, listOf("m1", "m2", null), codes, at = 7)
        assertEquals(5, w.size)
        assertEquals(com.bamnat.core.team.TeamWish("m1", month.atDay(4), com.bamnat.core.team.WishKind.LEAVE, null, 7), w.first { it.date == month.atDay(4) })
    }
}
