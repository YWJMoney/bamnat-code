package com.bamnat.core

import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.RosterVote
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.imports.SheetKinds
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.schedule.ShiftRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * [READ-41] 1.38 PC 12차: 조-근무표-빈칸6.png (주·야·비·휴) read into a first setup of 2교대 (주간·야간·휴무) or 3교대 (주간·오후·
 * 야간·휴무), then '글자대로 맞추기'. On the phone 1조's 3·4일 came out 비번·휴무 (야·비), and with 3교대 the card did not show.
 */
class Pc12KindsTest {
    private fun reads(): List<List<RosterWord>> {
        val reads = mutableListOf<MutableList<RosterWord>>()
        javaClass.getResource("/roster-reads/team11-pc.tsv")!!.readText().lines().forEach { l ->
            if (l.startsWith("# read")) reads += mutableListOf<RosterWord>()
            val f = l.split('\t')
            if (f[0] == "word") reads.last() += RosterWord(f[6], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), angle = f[5].toFloat())
        }
        return reads
    }

    private fun check(bundle: String) {
        val shifts = ShiftBundles.byKey(bundle)!!.shifts
        val oldTypes = shifts.mapIndexed { i, s -> s.type.copy(id = i + 1L) }
        val codes = DutyCodes(oldTypes.map { DutyType(it.id, it.name, it.label, it.isOff, it.start, it.label == "야") })
        val read = RosterVote.merge(reads().map { DutyRoster.analyse(it, codes) })
        val row = read.rows.first { it.label == "1조" }
        val kinds = SheetKinds.of(read.rows.flatMap { it.raw.values }, oldTypes)
        assertNotNull("$bundle: 글자대로 맞추기 카드", kinds)
        val newTypes = kinds!!.mapIndexed { i, k -> k.type.copy(id = -(i + 1L)) }
        val after = DutyCodes(newTypes.map { DutyType(it.id, it.name, it.label, it.isOff, it.start, it.label == "야") })
        val byRole = kinds.mapIndexed { i, k -> k.role to -(i + 1L) }.toMap()
        val oldRole = shifts.mapIndexed { i, s -> (i + 1L) to s.role }.toMap()
        val again = SheetKinds.remap(row, codes, after, { oldRole[it] }, { byRole[it] })
        val truth = "주야야비휴주주야야비휴주주야야비휴주주야야비휴주주야야비휴주"
        val letter = newTypes.associate { it.id to it.label }
        val got = (1..30).joinToString("") { d -> (again.cells[d] as? DutyCell.Shift)?.let { letter[it.shiftTypeId] } ?: "." }
        assertEquals(bundle, truth, got)
    }

    @Test fun twoShifts() = check(ShiftBundles.TWO)
    @Test fun threeShifts() = check(ShiftBundles.THREE)
}
