package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.imports.RosterWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * [READ-36] 1.38 PC 8차 (표본 조-근무표-빈칸6.png): four 조 on a 6-day 주·야·비·휴 order with six cells left blank, read
 * as the phone read it: 1조's 20일 "야" as "아", and a piece of a line read twice with no name. The blanks are filled from
 * the order and looked at, "아" is 야 looked at, and the piece is no fifth line.
 */
class RosterTeamPhotoTest {
    private val codes = DutyCodes(listOf(
        DutyType(1, "데이", "D", false, LocalTime.of(7, 0)), DutyType(3, "나이트", "N", false, LocalTime.of(23, 0), night = true),
        DutyType(4, "오프", "O", true),
    ))
    private val order = listOf("주", "야", "야", "비", "휴", "주")
    private fun code(team: Int, d: Int) = order[(d + team * 2) % 6]
    private val blank = setOf(0 to 5, 0 to 18, 1 to 9, 1 to 22, 2 to 13, 3 to 27)

    private fun words(): List<RosterWord> {
        val w = mutableListOf<RosterWord>()
        w += RosterWord("2026년", 800, 60, 900, 96); w += RosterWord("11월", 910, 60, 970, 96); w += RosterWord("근무표", 980, 60, 1080, 96)
        val x0 = 230; val cw = 64; val rh = 52; val y0 = 150
        for (d in 1..30) w += RosterWord("$d", x0 + (d - 1) * cw + 20, y0 + 12, x0 + (d - 1) * cw + 44, y0 + 40)
        for (t in 0..3) {
            val y = y0 + rh * (t + 1)
            w += RosterWord("${t + 1}조", 74, y + 12, 120, y + 40)
            for (d in 1..30) {
                if (t to d in blank) continue
                val text = if (t == 0 && d == 20) "아" else code(t, d)
                w += RosterWord(text, x0 + (d - 1) * cw + 20, y + 12, x0 + (d - 1) * cw + 44, y + 40)
            }
        }
        // A piece of 2조's line read again, half a line lower, without its name.
        val y = y0 + rh * 2 + rh / 2
        for (d in 1..18) if (1 to d !in blank) w += RosterWord(code(1, d), x0 + (d - 1) * cw + 20, y + 12, x0 + (d - 1) * cw + 44, y + 40)
        return w
    }

    @Test fun blanksFilledFromTheOrderAndLookedAt() {
        val read = RosterVote.merge(listOf(DutyRoster.analyse(words(), codes)))
        assertEquals("네 조만: ${read.rows.map { it.label + "·" + it.cells.size }}", listOf("1조", "2조", "3조", "4조"), read.rows.map { it.label }.sorted())
        for (t in 0..3) {
            val row = read.rows.first { it.label == "${t + 1}조" }
            val doubt = row.doubtful(30)
            for (d in 1..30) {
                val want = codes.match(code(t, d))
                assertEquals("${t + 1}조 ${d}일", want, row.cells[d])
                if (t to d in blank) assertTrue("${t + 1}조 ${d}일 채운 칸은 노란 표시", d in doubt)
            }
        }
        assertTrue("아로 읽힌 20일은 노란 표시", 20 in read.rows.first { it.label == "1조" }.doubtful(30))
    }

    @Test fun aLetterAStrokeOffIsTheCodeItNearlyIs() {
        assertEquals(codes.match("야"), codes.match("아"))
        assertEquals(codes.match("주"), codes.match("추"))
        assertTrue(codes.guessedWork("아", codes.match("아")!!))
    }

    @Test fun theSheetsOwnLettersBecomeTheKinds() {
        val hospital = listOf(
            com.bamnat.core.model.ShiftType(1, "데이", "D", 0, LocalTime.of(7, 0), LocalTime.of(15, 0)),
            com.bamnat.core.model.ShiftType(2, "나이트", "N", 0, LocalTime.of(23, 0), LocalTime.of(7, 0)),
            com.bamnat.core.model.ShiftType(3, "오프", "O", 0, null, null, isOff = true),
        )
        val raws = (1..30).map { code(0, it) } + "아"
        val kinds = com.bamnat.core.imports.SheetKinds.of(raws, hospital)!!
        assertEquals(listOf("주간 주 07:00", "야간 야 19:00", "비번 비 null", "휴무 휴 null"), kinds.map { "${it.type.name} ${it.type.label} ${it.type.start}" })
        // Kinds that already say 주·야·비·휴, or a sheet of D·E·N·O: nothing offered.
        assertEquals(null, com.bamnat.core.imports.SheetKinds.of(raws, kinds.map { it.type }))
        assertEquals(null, com.bamnat.core.imports.SheetKinds.of((1..30).map { listOf("D", "E", "N", "O")[it % 4] }, hospital))
    }

    @Test fun aLineReadAgainInTheSheetsKindsKeepsEveryDay() {
        val read = RosterVote.merge(listOf(DutyRoster.analyse(words(), codes)))
        val row = read.rows.first { it.label == "1조" }
        // As several readings put it together: the words of only the first week kept.
        val voted = row.copy(raw = row.raw.filterKeys { it <= 7 })
        val kinds = com.bamnat.core.imports.SheetKinds.of(read.rows.flatMap { it.raw.values }, emptyList())!!
        val newTypes = kinds.mapIndexed { i, k -> k.type.copy(id = -(i + 1L)) }
        val after = DutyCodes(newTypes.map { DutyType(it.id, it.name, it.label, it.isOff, it.start, it.label == "야") })
        val byRole = kinds.mapIndexed { i, k -> k.role to -(i + 1L) }.toMap()
        val oldRole = mapOf(1L to com.bamnat.core.schedule.ShiftRole.DAY, 3L to com.bamnat.core.schedule.ShiftRole.NIGHT, 4L to com.bamnat.core.schedule.ShiftRole.OFF)
        val again = com.bamnat.core.imports.SheetKinds.remap(voted, codes, after, { oldRole[it] }, { byRole[it] })
        for (d in 1..30) assertEquals("1조 ${d}일", after.match(code(0, d)), again.cells[d])
    }
}
