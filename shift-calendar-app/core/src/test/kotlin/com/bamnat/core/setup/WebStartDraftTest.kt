package com.bamnat.core.setup

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.schedule.ShiftRole
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVH-68] 검토 I50c-01 (RVR-31, SPREAD-30·31): the first setup filled from what bamnat.app's 교대 달력 carried, and the app's
 * '같은 조 동료에게 보내기' link back to it.
 */
class WebStartDraftTest {
    private val shift = SetupDraft(style = WorkStyle.SHIFT, step = SetupStep.COMPANY)
    private val nov10 = LocalDate.of(2026, 11, 10)

    private fun SetupDraft.letters(steps: List<Long?>) = steps.joinToString("") { id -> roles[id]?.code?.toString() ?: "-" }

    @Test fun `주야비휴 from the site is 2교대 with a 비번, the order and today answered, on the times`() {
        val d = WebStartDraft.draft(WebHandoff.read("p-DNBO_s-20261110")!!, shift)!!
        assertEquals(SetupStep.TIMES, d.step)
        assertEquals(ShiftBundles.TWO, d.bundle)
        assertEquals(listOf(ShiftRole.DAY, ShiftRole.NIGHT, ShiftRole.AFTER_DUTY, ShiftRole.OFF), d.types.map { d.roles.getValue(it.id) })
        assertEquals("DNBO", d.letters(d.rotation))
        assertEquals(nov10, d.anchor)
        assertTrue("오늘 근무는 웹에서 이미 답함", d.todayConfirmed)
        assertEquals(false, d.rosterMonthly)
    }

    @Test fun `주오야 is 3교대, and without a first day the order is filled and today is still asked`() {
        val d = WebStartDraft.draft(WebHandoff.read("DDEENNOO")!!, shift)!!
        assertEquals(ShiftBundles.THREE, d.bundle)
        assertEquals("DDEENNOO", d.letters(d.rotation))
        assertNull(d.anchor)
        assertEquals(false, d.todayConfirmed)
    }

    @Test fun `a month's roster written once is the hospital's, painted day by day from its first day`() {
        val d = WebStartDraft.draft(WebHandoff.read("p-DENO_s-20261201_o-1")!!, shift)!!
        assertEquals(ShiftBundles.HOSPITAL, d.bundle)
        assertTrue(d.rosterMonthly)
        assertTrue("반복하지 않음", d.rotation.isEmpty())
        assertEquals(4, d.painted.size)
        assertEquals("DENO", d.letters((0L..3L).map { d.painted[LocalDate.of(2026, 12, 1).plusDays(it)] }))
        // No first day: its kinds only, the days painted by hand.
        val bare = WebStartDraft.draft(WebHandoff.read("p-DENO_o-1")!!, shift)!!
        assertTrue(bare.rosterMonthly && bare.painted.isEmpty() && bare.rotation.isEmpty())
    }

    @Test fun `24시간 당번 and an order of its own kinds`() {
        val duty = WebStartDraft.draft(WebHandoff.read("XBO")!!, shift)!!
        assertEquals(ShiftBundles.DUTY, duty.bundle)
        assertEquals("XBO", duty.letters(duty.rotation))
        // 주간과 당번 together is no bundle: its own kinds at the usual times, then 비번 and 휴무.
        val own = WebStartDraft.draft(WebHandoff.read("DXBO")!!, shift)!!
        assertEquals(SetupDraft.CUSTOM, own.bundle)
        assertEquals(listOf(ShiftRole.DAY, ShiftRole.DUTY, ShiftRole.AFTER_DUTY, ShiftRole.OFF), own.types.map { own.roles.getValue(it.id) })
        assertEquals("DXBO", own.letters(own.rotation))
        assertEquals(LocalTime.of(7, 0), own.types.first().start)
    }

    @Test fun `nothing for another kind of work or an order with no work`() {
        assertNull(WebStartDraft.draft(WebHandoff.read("DNBO")!!, shift.copy(style = WorkStyle.DAY)))
        assertNull(WebStartDraft.draft(WebHandoff.read("OOBO")!!, shift))
        assertNull(WebStartDraft.draft(WebHandoff.read("w-10320")!!, shift))
    }

    private fun type(id: Long, name: String, start: Int?, hours: Int = 12, off: Boolean = false) = ShiftType(
        id = id, name = name, label = name.take(1), colorArgb = 0xFF000000,
        start = start?.let { LocalTime.of(it, 0) }, end = start?.let { LocalTime.of((it + hours) % 24, 0) }, isOff = off,
    )

    @Test fun `the coworkers' link carries the order's letters from the latest start on or before today`() {
        val types = listOf(type(1, "주간", 7), type(2, "야간", 19), type(3, "비번", null, off = true), type(4, "휴무", null, off = true))
            .associateBy { it.id }
        val r = WorkPattern.Rotation(LocalDate.of(2025, 3, 1), listOf(1L, 2L, 3L, 4L), anchorDate = LocalDate.of(2025, 3, 1))
        val today = LocalDate.of(2026, 11, 12)
        val link = WebStartDraft.shiftPage(r, types, today)!!
        val p = link.substringAfter("p=").substringBefore('&')
        val s = LocalDate.parse(link.substringAfter("s="))
        assertEquals("DNBO", p)
        assertTrue("$s ≤ $today", !s.isAfter(today) && s.plusDays(4).isAfter(today))
        assertEquals(0L, java.time.temporal.ChronoUnit.DAYS.between(r.anchorDate, s) % 4)
        // What the site sends back to the app reads as the same order and day.
        assertEquals(WebHandoff(codes = "DNBO", first = s), WebHandoff.read(WebHandoff.content("DNBO", s)))
        // A day off without a kind, or a kind since deleted, is 휴무; an order with no work sends nothing.
        assertEquals("DONO", WebStartDraft.shiftPage(r.copy(steps = listOf(1L, null, 2L, 99L)), types, today)!!.substringAfter("p=").substringBefore('&'))
        assertNull(WebStartDraft.shiftPage(r.copy(steps = listOf(3L, 4L)), types, today))
    }
}
