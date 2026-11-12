package com.bamnat.core

import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoAsked
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.memo.MoneyRepeatLine
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.Repeat
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVIB-08] (검토 I07ac-04) "매달 25일 월세 50만 원" as a memo that comes back and a 고정 지출, in one line. */
class MoneyRepeatLineTest {
    private val today = LocalDate.of(2026, 11, 12)

    @Test fun rentOnThe25th() {
        val f = MoneyRepeatLine.find("매달 25일 월세 50만 원")!!
        assertEquals("월세", f.name)
        assertEquals(500_000L, f.amount)
        assertEquals(Repeat.MONTH, f.repeat)
        assertEquals(25, f.day)
        assertEquals("매달 25일", f.said)
        val cost = f.cost()
        assertEquals("주거", cost.category)
        assertEquals(MoneyKind.EXPENSE, cost.kind)
        val first = f.firstFrom(today)
        assertEquals(LocalDate.of(2026, 11, 25), first)
        val memo = Memo(id = 1, title = "월세", date = first, repeat = f.memoRepeat(first))
        assertTrue(MemoRepeat.occursOn(memo, LocalDate.of(2026, 12, 25)))
    }

    @Test fun otherWaysOfSayingIt() {
        MoneyRepeatLine.find("매주 금요일 레슨비 3만원")!!.let {
            assertEquals(Repeat.WEEK, it.repeat); assertEquals(5, it.day); assertEquals(30_000L, it.amount); assertEquals("레슨비", it.name)
            assertEquals(LocalDate.of(2026, 11, 13), it.firstFrom(today))
        }
        MoneyRepeatLine.find("매년 3월 2일 자동차 보험 62만 원")!!.let {
            assertEquals(Repeat.YEAR, it.repeat); assertEquals(3, it.month); assertEquals(2, it.day); assertEquals("보험", it.cost().category)
            assertEquals(LocalDate.of(2027, 3, 2), it.firstFrom(today))
        }
        MoneyRepeatLine.find("장보기\n25일마다 관리비 12만")!!.let { assertEquals(25, it.day); assertEquals(120_000L, it.amount) }
        MoneyRepeatLine.find("매달 말일 적금 30만")!!.let {
            assertEquals(31, it.day); assertEquals(MoneyKind.TRANSFER, it.kind); assertEquals("저축", it.cost().category)
            assertEquals(LocalDate.of(2026, 11, 30), it.firstFrom(today))
            // 말일 stays the month's last day, not the 30th.
            val memo = Memo(id = 1, date = it.firstFrom(today), repeat = it.memoRepeat(it.firstFrom(today)))
            assertTrue(MemoRepeat.occursOn(memo, LocalDate.of(2026, 12, 31)))
        }
        MoneyRepeatLine.find("매달 10일 월급 250만")!!.let { assertEquals(MoneyKind.INCOME, it.kind); assertEquals("월급", it.cost().category) }
    }

    @Test fun notEveryLineIsMoney() {
        // No amount with its unit, or no repeat: nothing is offered.
        assertNull(MoneyRepeatLine.find("매달 25일 분리수거"))
        assertNull(MoneyRepeatLine.find("월세 50만 원 보내기"))
        assertNull(MoneyRepeatLine.find("매달 25일 3층 회의 2026"))
        assertNull(MoneyRepeatLine.find("매달 40일 월세 50만 원"))
    }

    @Test fun answeringOneKindKeepsTheOther() {
        val f = MoneyRepeatLine.find("매달 25일 월세 50만 원")!!
        val time = com.bamnat.core.memo.MemoTime.Found(LocalDate.of(2026, 11, 25).atTime(9, 0), "9시")
        // Answered before 2.0.0: one time's key alone.
        assertTrue(time.answeredBy("w:9시"))
        val both = MemoAsked.with(MemoAsked.with("w:9시", f.key), "w:9시")
        assertTrue(time.answeredBy(both))
        assertTrue(MemoAsked.has(both, f.key))
        // A new time answered replaces the old time, not the money line.
        val moved = MemoAsked.with(both, "w:10시")
        assertTrue(MemoAsked.has(moved, f.key))
        assertTrue(!time.answeredBy(moved))
    }
}
