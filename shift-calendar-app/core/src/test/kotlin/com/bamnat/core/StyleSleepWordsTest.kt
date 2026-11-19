package com.bamnat.core

import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.Job
import com.bamnat.core.money.JobStatus
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.schedule.ShiftEvents
import com.bamnat.core.sleep.SleepInsights
import com.bamnat.core.sleep.SleepInsights.Context
import com.bamnat.core.sleep.SleepPlan
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 갈래 H 10묶음: 잠·위젯이 그 일의 말로 ([RVH-74] 검토 TYPE-07·T30-15, [RVH-75] TYPE-08·T30-16·T30-91, [RVH-76] T30-04·T30-98).
 */
class StyleSleepWordsTest {
    private fun d(day: Int) = LocalDate.of(2026, 11, day)
    private fun at(day: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, day, h, m)

    /** [RVH-74] 투자·현장·가게의 잠 묶음 이름에 '야간 뒤·근무일'이 없어요. 교대는 그대로. */
    @Test fun `each kind of work names the kinds of rest in its own words`() {
        assertEquals("야간 뒤", Context.AFTER_NIGHT.label(WorkStyle.SHIFT))
        assertEquals("근무일", Context.WORK_DAY.label(null))
        assertEquals("평일", Context.WORK_DAY.label(WorkStyle.INVEST))
        assertEquals("늦게 닫은 뒤", Context.AFTER_NIGHT.label(WorkStyle.BUSINESS))
        assertEquals("영업한 날", Context.WORK_DAY.label(WorkStyle.BUSINESS))
        assertEquals("일한 날", Context.WORK_DAY.label(WorkStyle.FIELD))
        for (style in WorkStyle.entries.filter { it != WorkStyle.SHIFT }) for (c in Context.entries) {
            val said = c.label(style)
            assertFalse("$style $c: $said", "야간" in said || "근무" in said)
        }
        assertEquals("영업한 날·쉬는 날", SleepInsights.groupsWord(WorkStyle.BUSINESS))
    }

    /** [RVH-74] 투자는 장이 여는 평일, 현장은 일감이 있는 날이 일하는 날이에요(근무가 없어도 늘 '쉬는 날'이 되지 않게). */
    @Test fun `an investor's weekdays and a field worker's job days are days of work`() {
        val invest = SleepInsights.workDay(WorkStyle.INVEST)
        assertTrue(invest(d(12)))                       // 목요일
        assertFalse(invest(d(14)))                      // 토요일
        assertFalse(invest(LocalDate.of(2026, 12, 25))) // 성탄절(금)
        val field = SleepInsights.workDay(WorkStyle.FIELD, setOf(d(12)))
        assertTrue(field(d(12)))
        assertFalse(field(d(13)))
        assertFalse(SleepInsights.workDay(WorkStyle.FIELD)(d(12)))
        // Without a shift, the day of the job is a work day for the sleep before it, the day after is off.
        assertEquals(Context.WORK_DAY, SleepInsights.contextOf(at(11, 23), at(12, 6), emptyList(), field))
        assertEquals(Context.DAY_OFF, SleepInsights.contextOf(at(12, 23), at(13, 8), emptyList(), field))
    }

    private val day = ShiftType(11, "오픈", "오", 0xFF1E88E5, LocalTime.of(7, 0), LocalTime.of(15, 0))
    private val night = ShiftType(12, "심야", "심", 0xFF5E35B1, LocalTime.of(20, 0), LocalTime.of(4, 0))
    private val off = ShiftType(13, "휴무", "휴", 0xFF9E9E9E, null, null, isOff = true)
    /** 11/2 휴무, 11/3 심야(첫 밤), 11/4 휴무, 11/5 휴무, 11/6 오픈 07:00. */
    private val resolver = ScheduleResolver(listOf(day, night, off),
        WorkPattern.Rotation(d(2), listOf(off.id, night.id, off.id, off.id, day.id)), emptyList())

    private fun tips(date: LocalDate, style: WorkStyle?) =
        SleepPlan.tips(SleepSchedule.of(resolver, date, date, alarmsOn = false), date, style)

    /** [RVH-75] 가게의 잠 계획은 영업·마감 말, 현장·투자는 일정 말. 교대 근무자는 그대로 '퇴근 뒤 잠'. */
    @Test fun `the sleep plan speaks the words of the work`() {
        val shift = tips(d(3), WorkStyle.SHIFT)
        assertTrue(shift.toString(), shift.any { it.label == "퇴근 뒤 잠" && it.text.startsWith("퇴근하고") })
        assertTrue(shift.toString(), shift.any { it.label == "야간 전 낮잠" && it.text.startsWith("첫 야간 전에") })

        val shop = tips(d(3), WorkStyle.BUSINESS)
        assertTrue(shop.toString(), shop.any { it.label == "마감 뒤 잠" && it.text.startsWith("마감하고") })
        assertTrue(shop.toString(), shop.any { it.label == "밤 영업 전 낮잠" && it.text.startsWith("밤 영업 전에") })
        assertTrue(shop.toString(), shop.any { it.label == "마감 뒤 졸음 주의" })
        val shopEve = tips(d(5), WorkStyle.BUSINESS).single { it.kind == SleepPlan.Kind.BEDTIME }
        assertTrue(shopEve.text, shopEve.text.startsWith("내일 07:00 영업이라"))
        assertEquals(SleepPlan.Kind.BEDTIME.label, shopEve.label)

        val field = tips(d(3), WorkStyle.FIELD)
        assertTrue(field.toString(), field.any { it.label == "일 마친 뒤 잠" && it.text.startsWith("일을 마치고") })
        for (t in shop + field + tips(d(5), WorkStyle.FIELD)) {
            assertFalse(t.toString(), "퇴근" in t.text || "퇴근" in t.label || "야간" in t.label || "근무" in t.text)
        }
    }

    private fun job(date: LocalDate, title: String, start: LocalTime? = null, end: LocalTime? = null, last: LocalDate? = null,
                    status: JobStatus = JobStatus.CONFIRMED) =
        Job(date = date, endDate = last, title = title, start = start, end = end, status = status)

    /** [RVH-76] 현장의 '다음 일정까지' 위젯은 일감을 세어요(근무가 없어 늘 '7일 안에 일정이 없어요'였어요). */
    @Test fun `a field worker's countdown counts the jobs`() {
        val now = at(12, 6)
        val jobs = listOf(
            job(d(12), "욕실 타일", LocalTime.of(8, 0), LocalTime.of(17, 0)),
            job(d(13), "취소된 일", LocalTime.of(7, 0), LocalTime.of(9, 0), status = JobStatus.CANCELLED),
        )
        assertEquals("욕실 타일까지 2시간 · 08:00", ShiftEvents.jobCountdown(jobs, now))
        assertEquals("욕실 타일 중 · 17:00에 끝나요 (1시간 30분 남음)", ShiftEvents.jobCountdown(jobs, at(12, 15, 30)))
        // After it ends, the cancelled one is not next: nothing in the week.
        assertNull(ShiftEvents.jobCountdown(jobs, at(12, 18)))
        // A three-day job counts down to each morning.
        val three = listOf(job(d(12), "주방 철거", LocalTime.of(8, 0), LocalTime.of(17, 0), last = d(14)))
        assertEquals("주방 철거까지 14시간 · 내일 08:00", ShiftEvents.jobCountdown(three, at(12, 18)))
        // A job without times: the day and its name.
        assertEquals("오늘 현장 정리", ShiftEvents.jobCountdown(listOf(job(d(12), "현장 정리")), now))
        assertEquals("11/16(월) 현장 정리", ShiftEvents.jobCountdown(listOf(job(d(16), "현장 정리")), now))
        // A timed job earlier than an untimed one on a later day comes first; an untimed one today before a timed one tomorrow.
        assertEquals("오늘 현장 정리", ShiftEvents.jobCountdown(listOf(job(d(12), "현장 정리"), job(d(13), "도배", LocalTime.of(9, 0), LocalTime.of(18, 0))), now))
        // An overnight job from yesterday still counts down to its end.
        val overnight = listOf(job(d(11), "야간 도색", LocalTime.of(22, 0), LocalTime.of(7, 0)))
        assertEquals("야간 도색 중 · 07:00에 끝나요 (1시간 남음)", ShiftEvents.jobCountdown(overnight, now))
        assertNull(ShiftEvents.jobCountdown(listOf(job(d(20), "멀리")), now))
    }
}
