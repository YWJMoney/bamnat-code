package com.bamnat.core.formula

import com.bamnat.core.text.Josa
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/**
 * [RVM-03] PC-37·43 (통합 1차): a work's rule words read as a person writes them. '0.6시간 잤어요. 오늘 주간예요' was 36분 and 주간이에요;
 * '7일 평균 0.2857번' was 0.3번. [RVM-05] PC-44: a count past its goal was '7/6잔'.
 */
class RuleWordsTest {
    private val day = FormulaScope.of(mapOf("근무" to "주간", "근무시간" to 9.5),
        mapOf("역할" to "야간", "잔시간" to 0.6, "평균" to 0.2857, "급여" to 2_345_000, "물" to 7, "비율" to 0.035, "휴" to "휴무", "영어" to "OFF"))

    private fun r(src: String) = FormulaText.render(src, day)

    @Test fun theParticleFitsTheWordBeforeIt() {
        assertEquals("오늘 주간이에요", r("오늘 {근무}예요"))
        assertEquals("오늘 야간이에요", r("오늘 {칸(\"역할\")}(이)에요"))
        assertEquals("내일은 휴무예요", r("내일은 {칸(\"휴\")}이에요"))
        assertEquals("주간이 시작돼요", r("{근무}가 시작돼요"))
        assertEquals("휴무를 넣었어요", r("{칸(\"휴\")}을 넣었어요"))
        assertEquals("주간으로 바꿨어요", r("{근무}로 바꿨어요"))
        assertEquals("7잔이에요", r("{칸(\"물\")}잔이에요"))
        assertEquals("물 7이에요", r("물 {칸(\"물\")}예요"))
        // A word that only starts like a 조사 is left alone, and a sound that cannot be told keeps what was written.
        assertEquals("주간이번 주", r("{근무}이번 주"))
        assertEquals("OFF예요", r("{칸(\"영어\")}예요"))
    }

    @Test fun numbersReadAsWritten() {
        assertEquals("7일 평균 0.3번", r("7일 평균 {칸(\"평균\")}번"))
        assertEquals("이달 2,345,000원", r("이달 {칸(\"급여\")}원"))
        assertEquals("0.04", r("{칸(\"비율\")}"))
        assertEquals("2026년", FormulaText.render("{2026}년", day))
    }

    @Test fun lessThanAnHourIsMinutes() {
        assertEquals("36분 잤어요. 오늘 주간이에요", r("{칸(\"잔시간\")}시간 잤어요. 오늘 {근무}예요"))
        assertEquals("1시간 잤어요", r("{1}시간 잤어요"))
        assertEquals("이번 주 야간 9.5시간", r("이번 주 야간 {근무시간}시간"))
    }

    @Test fun soundsOfNumbers() {
        assertEquals(Josa.Sound(true, rieul = true), Josa.sound("1"))
        assertEquals(Josa.Sound(false), Josa.sound("2"))
        assertEquals(Josa.Sound(true), Josa.sound("10"))
        assertEquals(Josa.Sound(true), Josa.sound("3,000"))
        assertEquals(Josa.Sound(true, rieul = true), Josa.sound("서울"))
        assertNull(Josa.sound("OK"))
        assertEquals("1로", "1" + Josa.fit("1", "으로"))
        assertEquals("3으로", "3" + Josa.fit("3", "로"))
    }

    @Test fun pastTheGoalReadsAsReached() {
        val water = LogicValue("물", ValueReset.DAY, goal = 6.0, unit = "잔")
        val today = LocalDate.of(2026, 11, 12)
        assertEquals("5/6잔", water.show(5.0, today))
        assertEquals("6잔 채움", water.show(6.0, today))
        assertEquals("6잔 채움 · 7잔", water.show(7.0, today))
        assertEquals("12번", LogicValue("집중", ValueReset.DAY, unit = "번").show(12.0, today))
    }
}
