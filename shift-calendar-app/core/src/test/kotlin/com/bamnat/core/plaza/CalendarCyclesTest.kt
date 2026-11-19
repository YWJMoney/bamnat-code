package com.bamnat.core.plaza

import com.bamnat.core.model.ShiftType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/** 2.0.0 [RVN-24] 검토 I10ac-02: the order on my calendar finds the public rows and companies with the same work, on the phone. */
class CalendarCyclesTest {
    private fun t(id: Long, name: String, start: String?, end: String?, off: Boolean = false) =
        ShiftType(id, name, name.take(1), 0xFF000000, start?.let(LocalTime::parse), end?.let(LocalTime::parse), isOff = off)

    private val day = t(1, "주간", "07:00", "19:00")
    private val night = t(2, "야간", "19:00", "07:00")
    private val off = t(3, "휴무", null, null, off = true)
    private val types = listOf(day, night, off).associateBy { it.id }

    private fun row(id: String, company: String, codes: String, state: OrderState = OrderState.EXACT, trust: Set<CycleTrust> = setOf(CycleTrust.entries.first()),
        shifts: List<CycleShift> = emptyList()) = PublicCycle(
        id = id, display = company, company = company, industry = "제조", site = "", form = "4조2교대", formKey = "4조2교대", orderText = "", timeText = "",
        asOf = "2025", trustText = "", trust = trust, linkCheck = false, partner = false, source = "", codes = codes, orderState = state,
        weekly = false, roster = false, shifts = shifts,
    )

    @Test fun myOrderReadsAsCodes() {
        assertEquals("DDNNOO", CalendarCycles.codes(listOf(1L, 1L, 2L, 2L, 3L, null), types))
        assertEquals('X', CalendarCycles.code(t(9, "당번", "09:00", "09:00")))
        assertEquals('E', CalendarCycles.code(t(9, "오후", "14:00", "22:00")))
        // A 주5일 week says nothing, a shift with no times cannot be told.
        assertNull(CalendarCycles.codes(listOf(1L, 1L, 1L, 1L, 1L, null, null), types))
        assertNull(CalendarCycles.codes(listOf(1L, 9L), types + (9L to t(9, "교육", null, null))))
    }

    @Test fun theSameOrderStartedAnotherDayIsFoundAndAGuessedOneIsNot() {
        val rows = listOf(
            row("a", "가나정밀", "DDNNBO", shifts = listOf(CycleShift('D', "주간", "주", LocalTime.of(8, 0), LocalTime.of(20, 0)))),
            row("b", "다라화학", "NNBODD", shifts = listOf(CycleShift('D', "주간", "주", LocalTime.of(7, 0), LocalTime.of(19, 0)))),
            row("c", "마바전자", "DDNNOO", state = OrderState.USUAL),
            row("d", "사아물류", "DNOO"),
            row("e", "자차제철", "DDNNOO", trust = setOf(CycleTrust.COMMUNITY)),
        )
        val mine = CalendarCycles.codes(listOf(1L, 1L, 2L, 2L, 3L, 3L), types)!!
        val times = CalendarCycles.times(listOf(1L, 1L, 2L, 2L, 3L, 3L), types)
        // Same times first, a community answer last; the usual order guessed for a count and another length are not.
        assertEquals(listOf("b", "a", "e"), CalendarCycles.publicRows(mine, rows, times).map { it.id })
    }

    @Test fun aCompanysOrderByNames() {
        assertEquals("DDNNOO", CalendarCycles.nameCodes(listOf("주간", "주간", "야간", "야간", "비번", null), setOf("휴무")))
        assertEquals("XOO", CalendarCycles.nameCodes(listOf("당번", "비번", "휴무"), setOf("휴무")))
        assertNull(CalendarCycles.nameCodes(listOf("주간", "교육", "야간"), emptySet()))
        assertTrue(CalendarCycles.same("DDNNOO", "NNOODD"))
        assertFalse(CalendarCycles.same("DDNNOO", "DNDNOO"))
    }
}
