package com.bamnat.core

import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoSearch
import com.bamnat.core.memo.ShiftMemoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/** [MEMO-03][MEMO-04][MEMO-05][MEMO-06] 1.27 AK 순번 451: 메모 찾기·긴 메모·근무 기준 알림·지난 같은 근무. */
class MemoSearchTest {
    private val today = LocalDate.of(2026, 10, 26)
    private fun idx(m: Memo) = MemoSearch.Index(m)
    private fun fits(m: Memo, q: String, shift: (LocalDate) -> String? = { null }) = MemoSearch.matches(idx(m), q, today, shift)

    @Test fun lettersChoseongAndTagBeginnings() {
        val m = Memo(id = 1, title = "인수인계", body = "야간조에 키 반납 확인 #인수인계 #열쇠")
        assertTrue(fits(m, "키 반납"))
        assertTrue(fits(m, "ㅇㄱ"))      // 야간
        assertTrue(fits(m, "ㅇㅅㅇㄱ"))  // 인수인계
        assertTrue(fits(m, "#인수"))
        assertTrue(fits(m, "#열"))
        assertFalse(fits(m, "ㅂㄴ ㅈㄱ"))
        assertFalse(fits(m, "도시락"))
        assertTrue(fits(Memo(id = 2, isChecklist = true, checklist = listOf(ChecklistItem("도시락"))), "ㄷㅅㄹ"))
    }

    @Test fun datesByWords() {
        val m = Memo(id = 1, date = LocalDate.of(2026, 10, 19), body = "건강검진")
        assertTrue(fits(m, "10월"))
        assertTrue(fits(m, "10월 19일"))
        assertTrue(fits(m, "10/19"))
        assertTrue(fits(m, "2026년 건강"))
        assertFalse(fits(m, "10월 20일"))
        assertFalse(fits(m, "11월"))
        val t = Memo(id = 2, date = today.minusDays(1), body = "x")
        assertTrue(fits(t, "어제"))
        assertFalse(fits(t, "오늘"))
        // An undated memo is found by its words only.
        assertFalse(fits(Memo(id = 3, body = "y"), "오늘"))
        assertTrue(fits(Memo(id = 4, body = "오늘 할 일"), "오늘"))
    }

    @Test fun theShiftWorkedThatDay() {
        val night = LocalDate.of(2026, 10, 17)
        val m = Memo(id = 1, date = night, body = "+50,000 대리운전 팁")
        val shifts = mapOf(night to "야간")
        assertTrue(fits(m, "야간") { shifts[it] })
        assertTrue(fits(m, "야간 팁") { shifts[it] })
        assertFalse(fits(m, "주간") { shifts[it] })
    }

    @Test fun theCacheKeepsAnIndexUntilTheMemoIsSavedAgain() {
        val cache = MemoSearch.Cache()
        val m = Memo(id = 7, body = "a", updatedAtMillis = 10)
        val a = cache.of(m)
        assertTrue(a === cache.of(m.copy(pinned = true)))
        assertTrue(cache.of(m.copy(pinned = true)).memo.pinned)
        val b = cache.of(m.copy(body = "b", updatedAtMillis = 11))
        assertFalse(a === b)
        assertTrue(b.text.contains("b"))
        cache.keepOnly(emptySet())
        assertEquals(0, cache.size())
    }

    @Test fun aLongMemosCardShowsItsStartOrTheLinesAroundTheWord() {
        val long = "첫 줄\n" + "가".repeat(30_000) + "\n여기에 열쇠 반납 메모\n" + "나".repeat(20_000)
        val plain = MemoSearch.excerpt(long, "")
        assertTrue(plain.length <= MemoSearch.CARD_CHARS + 1)
        assertTrue(plain.startsWith("첫 줄"))
        val found = MemoSearch.excerpt(long, "열쇠")
        assertTrue(found.startsWith("…여기에 열쇠"))
        assertTrue(found.length <= MemoSearch.CARD_CHARS + 2)
        assertEquals("짧은 메모", MemoSearch.excerpt("짧은 메모", "메모"))
    }

    /** [MEMO-04] 메모 1,000개 (그중 10개는 5만 자): 찾기용 글자 만들기와 찾기가 끊김 없이. */
    @Test fun aThousandMemosWithFiftyThousandLetterOnesAreSearchedQuickly() {
        val big = buildString { repeat(5_000) { append("야간 근무 인수인계 ") } }.take(50_000)
        val memos = (1..1_000L).map { i ->
            Memo(id = i, date = today.minusDays(i % 200), title = "메모 $i", body = if (i % 100 == 0L) big + " 마지막열쇠$i" else "내용 $i #태그${i % 7}", updatedAtMillis = i)
        }
        val cache = MemoSearch.Cache()
        val t0 = System.nanoTime()
        memos.forEach { cache.of(it) }
        val build = (System.nanoTime() - t0) / 1_000_000
        // Typing: each key searches again with the kept indexes.
        val t1 = System.nanoTime()
        var hits = 0
        for (q in listOf("ㅇ", "ㅇㄱ", "열쇠", "마지막열쇠500", "#태그3", "10월", "ㅁㅁ 5")) hits += memos.count { MemoSearch.matches(cache.of(it), q, today) }
        val search = (System.nanoTime() - t1) / 1_000_000 / 7
        println("MemoSearch 1,000개: 색인 ${build}ms, 찾기 한 번 평균 ${search}ms")
        assertTrue(hits > 0)
        assertEquals(1, memos.count { MemoSearch.matches(cache.of(it), "마지막열쇠500", today) })
        assertTrue("색인 ${build}ms", build < 2_000)
        assertTrue("찾기 ${search}ms", search < 300)
    }

    // ---- MEMO-05 근무 기준 알림 ----

    private val now = LocalDateTime.of(2026, 10, 26, 10, 0)
    private val days = listOf(
        ShiftMemoTime.Day(LocalDate.of(2026, 10, 26), "주간", true, LocalTime.of(8, 0)),
        ShiftMemoTime.Day(LocalDate.of(2026, 10, 27), "주간", true, LocalTime.of(8, 0)),
        ShiftMemoTime.Day(LocalDate.of(2026, 10, 28), "야간", true, LocalTime.of(20, 0)),
        ShiftMemoTime.Day(LocalDate.of(2026, 10, 29), "야간", true, LocalTime.of(20, 0)),
        ShiftMemoTime.Day(LocalDate.of(2026, 10, 30), "비번", false, null),
        ShiftMemoTime.Day(LocalDate.of(2026, 11, 1), "야간", true, LocalTime.of(20, 0)),
    )

    @Test fun theNextNightIsAnHourBeforeItStarts() {
        val f = ShiftMemoTime.find("다음 야간 때 열쇠 챙기기", now, days)!!
        assertEquals(LocalDateTime.of(2026, 10, 28, 19, 0), f.at)
        assertEquals("다음 야간(10/28) 출근 1시간 전", f.phrase)
        assertEquals(LocalDateTime.of(2026, 10, 28, 19, 0), ShiftMemoTime.find("야간 출근 전에 도시락", now, days)!!.at)
        assertEquals(LocalDateTime.of(2026, 10, 29, 19, 0), ShiftMemoTime.find("다다음 야간 때", now, days)!!.at)
        // Today's 주간 has started: the next is tomorrow's.
        assertEquals(LocalDateTime.of(2026, 10, 27, 7, 0), ShiftMemoTime.find("주간 날 서류 내기", now, days)!!.at)
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 0), ShiftMemoTime.find("쉬는 날 은행 가기", now, days)!!.at)
        assertEquals(LocalDateTime.of(2026, 10, 30, 9, 0), ShiftMemoTime.find("비번 날 은행", now, days)!!.at)
    }

    @Test fun aShiftNameAloneIsNotAskedAbout() {
        assertNull(ShiftMemoTime.find("야간 수당 확인", now, days))
        assertNull(ShiftMemoTime.find("다음 오후 때", now, days))
        assertNull(ShiftMemoTime.find("다음 야간 때", now, emptyList()))
    }

    @Test fun suggestionsAreTheNextOfEachKind() {
        val s = ShiftMemoTime.suggestions(days, now)
        assertEquals(listOf("다음 주간(10/27) 출근 1시간 전", "다음 야간(10/28) 출근 1시간 전", "다음 비번(10/30) 아침"), s.map { it.label })
    }

    @Test fun theLastSameShiftBefore() {
        val past = listOf(LocalDate.of(2026, 10, 17) to "야간", LocalDate.of(2026, 10, 19) to "주간", LocalDate.of(2026, 10, 9) to "야간", LocalDate.of(2026, 10, 29) to "야간")
        assertEquals(LocalDate.of(2026, 10, 17), ShiftMemoTime.lastSame(LocalDate.of(2026, 10, 28), "야간", past))
        assertNull(ShiftMemoTime.lastSame(LocalDate.of(2026, 10, 28), "휴무", past))
        assertNull(ShiftMemoTime.lastSame(LocalDate.of(2026, 10, 28), null, past))
        assertNotNull(ShiftMemoTime.lastSame(LocalDate.of(2026, 10, 18), "야간", past))
    }
}
