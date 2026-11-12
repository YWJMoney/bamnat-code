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

    // ---- 2.0.0 [RVJ-27] 검토 PAYU-57·T05b-13 ----

    /** 사진이 한 글자 틀리게 읽은 이름은 새 팀원이 아니라 그 팀원을 먼저 골라 둬요(리더가 확인). */
    @Test fun aNameOneLetterOffIsOfferedAsThatMember() {
        val team = listOf(TeamMember("m1", "t", "김민서"), TeamMember("m2", "t", "이준호"), TeamMember("m3", "t", "박하은"), TeamMember("m4", "t", "서연"))
        val rows = listOf(Row("김민셔", null, emptyMap()), Row("이준호", null, emptyMap()), Row("박하", null, emptyMap()), Row("서언", null, emptyMap()), Row("최다인", null, emptyMap()))
        val matched = TeamGridImport.matchNames(rows, team)
        assertEquals(listOf(null, "m2", null, null, null), matched)
        assertEquals("셔·서, 빠진 글자, 두 글자 이름의 한 획", listOf("m1", null, "m3", "m4", null), TeamGridImport.closeNames(rows, team, matched))
    }

    /** 다른 사람일 수 있으면 고르지 않아요: 같은 이름이 이미 가져간 팀원, 비슷한 팀원이 둘, 비슷한 줄이 둘, 성만 같은 짧은 이름. */
    @Test fun aCloseNameIsNotOfferedWhenItCouldBeSomeoneElse() {
        val team = listOf(TeamMember("m1", "t", "김민서"), TeamMember("m2", "t", "김민지"), TeamMember("m3", "t", "이수"), TeamMember("m4", "t", "박준"), TeamMember("m5", "t", "정하늘", active = false))
        // 김민셔: 김민서 is free and one stroke off, but 김민지 is one letter off too.
        assertEquals(listOf<String?>(null), TeamGridImport.closeNames(listOf(Row("김민셔", null, emptyMap())), team))
        // 김민지 has its own row: 김민셔 can only be 김민서.
        assertEquals(listOf(null, "m1"), TeamGridImport.closeNames(listOf(Row("김민지", null, emptyMap()), Row("김민셔", null, emptyMap())), team))
        // Two rows a letter off the same member: neither is chosen.
        assertEquals(listOf<String?>(null, null), TeamGridImport.closeNames(listOf(Row("이수진", null, emptyMap()), Row("이수민", null, emptyMap())), listOf(team[2])))
        // 박준 and 박현 share a family name and no more (현 is two strokes from 준); 쉬는 팀원은 고르지 않아요.
        assertEquals(listOf<String?>(null, null), TeamGridImport.closeNames(listOf(Row("박현", null, emptyMap()), Row("정하눌", null, emptyMap())), team))
        assertFalse(TeamGridImport.closeName("김민지", "김수현"))
        assertFalse("같은 이름은 '비슷한 이름'이 아니에요", TeamGridImport.closeName("김민지", "김민지"))
    }

    /** 리더가 넣기 전에 고친 이름으로 새 팀원이 생겨요. */
    @Test fun aNewMemberTakesTheNameAsFixed() {
        val rows = listOf(Row("김민셔 책임", null, emptyMap()), Row("정우", null, emptyMap()))
        assertEquals("김민셔", TeamGridImport.newName(rows[0]))
        var n = 0
        val (made, _) = TeamGridImport.newMembers(rows, listOf(null, null), skipped = emptySet(), teamId = "t", members = members, names = mapOf(0 to " 김민서 ", 1 to " ")) { "new${n++}" }
        assertEquals(listOf("김민서", "정우"), made.map { it.name })
        assertEquals("직급은 읽은 대로", com.bamnat.core.team.Skill.CHARGE, made[0].skill)
    }

    /** 한글 약자를 쓰는 팀: 파일의 D·E·N이 팀의 데·이·나(또는 주간·야간)에, 파일의 데·이·나·오가 팀의 D·E·N·O에 짝지어져요. */
    @Test fun koreanShortCodesPairBothWays() {
        val korean = listOf(TeamShift(1, "데", "데이", 0, false), TeamShift(2, "이", "이브닝", 0, false), TeamShift(3, "나", "나이트", 0, false), TeamShift(4, "오", "오프", 0, true))
        val g = TeamGridImport.guessCodes(listOf("D", "E", "N", "O", "OFF", "주간", "야"), korean)
        assertEquals(listOf(1L, 2L, 3L, 4L, 4L, 1L, 3L), listOf("D", "E", "N", "O", "OFF", "주간", "야").map { g[it] })
        val h = TeamGridImport.guessCodes(listOf("데", "이", "나", "오", "오전", "야근"), shifts)
        assertEquals(listOf(1L, 2L, 3L, 4L, 1L, 3L), listOf("데", "이", "나", "오", "오전", "야근").map { h[it] })
        val dayNight = listOf(TeamShift(1, "주", "주간", 0, false), TeamShift(2, "야", "야간", 0, false), TeamShift(3, "휴", "휴무", 0, true))
        val k = TeamGridImport.guessCodes(listOf("D", "N", "E", "비"), dayNight)
        assertEquals(1L, k["D"]); assertEquals(2L, k["N"]); assertEquals(3L, k["비"])
        assertNull("그 팀에 없는 근무는 짝짓지 않아요", k["E"])
    }

    /** 팀의 약자·이름과 같은 코드는 그대로, 낱말로 짐작한 코드만 '짐작' 표시를 받아요. */
    @Test fun onlyCodesPairedByWordsAreMarkedAsGuessed() {
        assertEquals(setOf("A", "연", "나이트", "휴"), TeamGridImport.looselyGuessed(listOf("D", "주간", "A", "연", "나이트", "휴", "N1", "O"), shifts))
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
