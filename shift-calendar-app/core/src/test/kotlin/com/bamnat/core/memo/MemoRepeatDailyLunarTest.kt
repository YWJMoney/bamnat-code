package com.bamnat.core.memo

import com.bamnat.core.holiday.KoreanLunar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.39 [CAL2-06] 매일과 매년 음력 반복 (두 앱 조사, 제사·음력 생일). */
class MemoRepeatDailyLunarTest {
    @Test fun aDailyMemoIsOnEveryDayFromItsOwn() {
        val start = LocalDate.of(2026, 11, 7)
        val memo = Memo(id = 1, date = start, title = "약 먹기", repeat = MemoRepeat.code(MemoRepeat.Kind.DAY, start))
        assertEquals("D", memo.repeat)
        assertEquals(MemoRepeat.Kind.DAY, MemoRepeat.kindOf(memo.repeat))
        assertEquals("매일", MemoRepeat.label(memo.repeat))
        assertEquals(5, MemoRepeat.datesIn(memo, start.minusDays(3), start.plusDays(4)).size)
        assertFalse(MemoRepeat.occursOn(memo, start.minusDays(1)))
        // 이 날만 빼기 works on it too.
        val skipped = memo.copy(skips = setOf(start.plusDays(1)))
        assertFalse(MemoRepeat.occursOn(skipped, start.plusDays(1)))
        assertTrue(MemoRepeat.occursOn(skipped, start.plusDays(2)))
    }

    @Test fun aLunarMemoComesBackOnTheSameLunarDayEachYear() {
        // 추석 (음력 8월 15일): 2025-10-06, 2026-09-25, 2027-09-15.
        val chuseok = LocalDate.of(2025, 10, 6)
        val code = MemoRepeat.code(MemoRepeat.Kind.LUNAR, chuseok)
        assertEquals("L0815", code)
        assertEquals(MemoRepeat.Kind.LUNAR, MemoRepeat.kindOf(code))
        assertEquals("매년 음력 8월 15일", MemoRepeat.label(code))
        val memo = Memo(id = 1, date = chuseok, title = "차례", repeat = code)
        val days = MemoRepeat.datesIn(memo, chuseok, LocalDate.of(2027, 12, 31))
        assertEquals(listOf(chuseok, LocalDate.of(2026, 9, 25), LocalDate.of(2027, 9, 15)), days)
        // The solar date a year on is not it.
        assertFalse(MemoRepeat.occursOn(memo, LocalDate.of(2026, 10, 6)))
    }

    @Test fun aThirtiethFallsOnTheTwentyNinthInAShortMonthAndALeapMonthIsNotIt() {
        // A lunar month that has 30 days one year and 29 the next.
        val (month, longYear, shortYear) = (1..12).firstNotNullOf { m ->
            (2026..2034).zipWithNext().firstOrNull { (a, b) ->
                KoreanLunar.toSolar(a, m, 30) != null && KoreanLunar.toSolar(b, m, 30) == null
            }?.let { (a, b) -> Triple(m, a, b) }
        }
        val start = KoreanLunar.toSolar(longYear, month, 30)!!
        val memo = Memo(id = 1, date = start, repeat = MemoRepeat.code(MemoRepeat.Kind.LUNAR, start))
        assertEquals("L%02d30".format(month), memo.repeat)
        val next = KoreanLunar.toSolar(shortYear, month, 29)!!
        assertTrue(MemoRepeat.occursOn(memo, next))
        assertFalse(MemoRepeat.occursOn(memo, next.minusDays(1)))
        // A 윤달 with the same number is not the day.
        val leap = (2026..2040).firstNotNullOfOrNull { y -> (1..12).firstNotNullOfOrNull { m -> KoreanLunar.toSolar(y, m, 10, leap = true)?.let { Triple(y, m, it) } } }!!
        val plain = KoreanLunar.toSolar(leap.first - 1, leap.second, 10)!!
        val each = Memo(id = 2, date = plain, repeat = MemoRepeat.code(MemoRepeat.Kind.LUNAR, plain))
        assertFalse(MemoRepeat.occursOn(each, leap.third))
        assertTrue(MemoRepeat.occursOn(each, KoreanLunar.toSolar(leap.first, leap.second, 10)!!))
    }

    @Test fun anOlderVersionsUnknownCodeIsNoRepeat() {
        assertEquals(MemoRepeat.Kind.NONE, MemoRepeat.kindOf("L1340"))
        assertEquals(MemoRepeat.Kind.NONE, MemoRepeat.kindOf("DD"))
    }
}
