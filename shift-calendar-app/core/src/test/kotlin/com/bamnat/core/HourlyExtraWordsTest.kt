package com.bamnat.core

import com.bamnat.core.model.ExtraKind
import com.bamnat.core.model.ExtraKinds
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/** 2.0.0 [RVH-50] HQ-18 (총괄 사진 검토, 알바 설정에 '잔업 · 특근 · 대근'): an 알바 calendar says its extra work in its own words. */
class HourlyExtraWordsTest {
    @Test fun aPartTimersKindsAndMarks() {
        val k = ExtraKinds().forStyle(WorkStyle.HOURLY)
        assertEquals(listOf("추가 근무", "쉬는 날 출근", "대타"), k.visible().map { it.name })
        // 추·출·대: none of them is 연차's 연 or a day off's 휴.
        assertEquals(listOf("추", "출", "대"), k.visible().map { it.mark })
        // Every other kind of work keeps the kinds as they are.
        val base = ExtraKinds()
        for (s in WorkStyle.entries - WorkStyle.HOURLY) assertSame(base, base.forStyle(s))
        assertSame(base, base.forStyle(null))
    }

    @Test fun whatTheUserChangedStays() {
        val k = ExtraKinds().put(ExtraKinds().of(ExtraKind.OVERTIME_ID).copy(short = "O"))
        assertEquals("추가 근무", k.forHourly().name(ExtraKind.OVERTIME_ID))
        assertEquals("O", k.forHourly().of(ExtraKind.OVERTIME_ID).mark)
        val renamed = ExtraKinds().put(ExtraKinds().of(ExtraKind.SPECIAL_ID).copy(name = "주말 근무"))
        assertEquals("주말 근무", renamed.forHourly().name(ExtraKind.SPECIAL_ID))
        // Already in an 알바's words: nothing more to do.
        val once = ExtraKinds().forHourly()
        assertSame(once, once.forHourly())
    }

    @Test fun anAppSentenceSaysItInAnAlbasWords() {
        assertEquals("추가 근무·대타 같은 추가 근무는 더해서 계산해요", TypeWords.say(WorkStyle.HOURLY, "잔업·특근·대근 같은 추가 근무는 더해서 계산해요"))
        assertEquals("연차·추가 근무 기록은 남아요", TypeWords.say(WorkStyle.HOURLY, "연차·잔업·특근 기록은 남아요"))
        assertEquals("쉬는 날 출근", TypeWords.say(WorkStyle.HOURLY, "특근"))
        assertEquals(listOf("잔업", "특근"), TypeWords.leaks(WorkStyle.HOURLY, "잔업과 특근"))
        // 교대·일근 keep them: their words.
        assertEquals(emptyList<String>(), TypeWords.leaks(WorkStyle.DAY, "잔업과 특근"))
    }
}
