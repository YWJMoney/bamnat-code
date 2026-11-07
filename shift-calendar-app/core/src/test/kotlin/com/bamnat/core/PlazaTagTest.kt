package com.bamnat.core

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.plaza.PlazaTag
import com.bamnat.core.plaza.PlazaTags
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** 1.39 [PLZC-10] 근무 태그: 달력 설정에서만 정해지고, 정해진 값만, 근무 유형에 맞는 말만. */
class PlazaTagTest {
    private fun t(h: Int) = LocalTime.of(h, 0)
    private fun type(id: Long, name: String, s: Int?, e: Int?) = ShiftType(id, name, name.take(1), 0xFF000000, s?.let(::t), e?.let(::t), isOff = s == null)
    private val day12 = type(1, "주간", 7, 19)
    private val night12 = type(2, "야간", 19, 7)
    private val off = type(9, "휴무", null, null)
    private val fourTeam = WorkPattern.Rotation(LocalDate.of(2026, 1, 1), listOf(1L, 1L, 2L, 2L, 9L, 9L, 9L, 9L))

    @Test
    fun `a calendar running 4조 2교대 says so, with what the writer chose to show`() {
        val tag = PlazaTags.compute(WorkStyle.SHIFT, listOf(day12, night12, off), fourTeam, null, worked = 0,
            scope = PlazaTags.Scope(industry = "semi"), now = PlazaTag.Now.NIGHT)
        assertEquals(WorkForm.FOUR_TEAM, tag.form)
        assertEquals("semi", tag.industry)
        assertEquals(PlazaTag.Now.NIGHT, tag.now)
        assertEquals(mapOf("s" to "SHIFT", "f" to "FOUR_TEAM", "i" to "semi", "n" to "night"), tag.fields())
        assertEquals("교대 · 4조 2교대 · 반도체·전자 · 밤낮 7개월", tag.copy(months = 7).line())
        assertEquals("교대 · 4조 2교대 · 반도체·전자 · 밤낮 처음", tag.copy(months = 0).line())
    }

    @Test
    fun `no pattern and few worked days - the kind of work alone`() {
        val few = PlazaTags.compute(WorkStyle.SHIFT, listOf(day12, night12, off), null, null, worked = 3, scope = PlazaTags.Scope())
        assertNull(few.form)
        val enough = PlazaTags.compute(WorkStyle.SHIFT, listOf(day12, night12, off), null, null, worked = PlazaTags.MIN_WORKED, scope = PlazaTags.Scope())
        assertEquals(WorkForm.TWO_SHIFT, enough.form)
        // Hidden by the writer.
        assertNull(PlazaTags.compute(WorkStyle.SHIFT, listOf(day12, night12, off), fourTeam, null, 0, PlazaTags.Scope(form = false)).form)
    }

    @Test
    fun `each kind of work carries only its own words`() {
        // A 교대 form on a 가게 calendar, 업종 on 알바, 근무 중 on 가게: none of them.
        val shop = PlazaTags.compute(WorkStyle.BUSINESS, listOf(day12, night12, off), fourTeam, null, 0, PlazaTags.Scope(industry = "semi"), PlazaTag.Now.DAY)
        assertEquals(PlazaTag(WorkStyle.BUSINESS), shop)
        val hourly = PlazaTags.compute(WorkStyle.HOURLY, listOf(day12), null, null, 20, PlazaTags.Scope(industry = "food"), PlazaTag.Now.DAY)
        assertNull(hourly.industry)
        assertEquals(PlazaTag.Now.DAY, hourly.now)
        // 그 밖의 업종 says nothing.
        assertNull(PlazaTags.compute(WorkStyle.DAY, listOf(type(3, "근무", 9, 18)), null, null, 20, PlazaTags.Scope(industry = "other")).industry)
        assertEquals(WorkForm.OFFICE, PlazaTags.compute(WorkStyle.DAY, listOf(type(3, "근무", 9, 18)), null, null, 20, PlazaTags.Scope()).form)
    }

    @Test
    fun `a tag from the server keeps only what is on the lists`() {
        val raw = mapOf("s" to "SHIFT", "f" to "OFFICE", "i" to "semi", "m" to 4, "n" to "night")
        val t = PlazaTag.of { raw[it] }!!
        assertNull(t.form) // 일근 words on a 교대 tag
        assertEquals("semi", t.industry)
        assertEquals(4, t.months)
        assertEquals(PlazaTag.Now.NIGHT, t.now)
        assertNull(PlazaTag.of { mapOf("s" to "NOPE")[it] })
        assertNull(PlazaTag.of { mapOf("s" to "BUSINESS", "n" to "day")[it] }!!.now)
        assertNull(PlazaTag.of { mapOf("s" to "SHIFT", "m" to 9999)[it] }!!.months)
    }

    @Test
    fun `at work by the calendar own hours, 야간 22시 to 6시`() {
        val from = LocalDateTime.of(2026, 11, 7, 19, 0)
        val to = from.plusHours(12)
        assertEquals(PlazaTag.Now.DAY, PlazaTags.nowOf(from, to, from.plusHours(1)))
        assertEquals(PlazaTag.Now.NIGHT, PlazaTags.nowOf(from, to, from.plusHours(5)))
        assertNull(PlazaTags.nowOf(from, to, to))
        assertNull(PlazaTags.nowOf(from, to, from.minusMinutes(1)))
        assertNull(PlazaTags.nowOf(null, null, from))
    }

    @Test
    fun `PLZC-21 오늘 출근 kind by the shift start, an 18시 night counts as night all evening`() {
        assertEquals(PlazaTag.Now.NIGHT, PlazaTags.shiftOf(18))
        assertEquals(PlazaTag.Now.NIGHT, PlazaTags.shiftOf(22))
        assertEquals(PlazaTag.Now.NIGHT, PlazaTags.shiftOf(0))
        assertEquals(PlazaTag.Now.DAY, PlazaTags.shiftOf(6))
        assertEquals(PlazaTag.Now.DAY, PlazaTags.shiftOf(14))
    }
}
