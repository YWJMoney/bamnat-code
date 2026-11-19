package com.bamnat.core

import com.bamnat.core.plaza.Reaction
import com.bamnat.core.plaza.Reactions
import org.junit.Test
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** 2.0.0 [RVN-14] 검토 HQ-51: the 알림함 rows of what others did to what this phone put out. */
class ReactionsTest {
    @Test
    fun answersAndMeTooOnOnePostAreOneRowWithTheZerosLeftOut() {
        val rows = Reactions.rows(listOf(
            Reaction("answer", "p1", "교대 광장", 3, 2_000, room = "style-shift"),
            Reaction("agree", "p1", "교대 광장", 2, 3_000, room = "style-shift", up = 2),
            Reaction("rate_vote", "r1", "용접", 1, 1_000, up = 1),
        ))
        assertEquals(listOf("plaza.post:p1", "plaza.rate:r1"), rows.map { it.key })
        assertEquals("내 글에 반응이 왔어요", rows[0].title)
        assertEquals("교대 광장 · 답 3 · 나도요 2", rows[0].line)
        assertEquals(3_000L, rows[0].atMillis)
        assertEquals("style-shift", rows[0].room)
        assertEquals("용접 · 맞아요 1", rows[1].line)
    }

    @Test
    fun eachKindSaysItInItsOwnWords() {
        val line = { r: Reaction -> Reactions.rows(listOf(r)).single().let { it.title to it.line } }
        assertEquals("내가 답한 글에 새 답이 왔어요" to "일근 광장 · 새 답 2", line(Reaction("thread", "p2", "일근 광장", 2, 1, room = "style-day")))
        // A proposal in a company's room: 공감 and 비공감 both.
        assertEquals("내 글에 반응이 왔어요" to "가나다화학 · 공감 1 · 비공감 2",
            line(Reaction("agree", "p3", "가나다화학", 3, 1, room = "c123456789ab", postKind = "proposal", up = 1, down = 2)))
        assertEquals("내가 알린 규칙에 반응이 왔어요" to "가나다화학 · 맞아요 1 · 달라요 1",
            line(Reaction("report_vote", "c1/s1", "가나다화학", 2, 1, up = 1, down = 1)))
        assertEquals("내 부탁에 반응이 왔어요" to "간호사 3교대 알람 세트 · 좋아요 2", line(Reaction("request_like", "q1", "간호사 3교대 알람 세트", 2, 1)))
        assertEquals("내 부탁을 누군가 만들기 시작했어요" to "간호사 3교대 알람 세트", line(Reaction("request_take", "q1", "간호사 3교대 알람 세트", 1, 1)))
        assertEquals("이달의 공모에 내 작품이 뽑혔어요" to "밤 달력 · 야간 달력", line(Reaction("contest_pick", "ABCD2345", "밤 달력", 1, 1, theme = "야간 달력")))
    }

    @Test
    fun aKindTurnedOffOrUnknownIsLeftOutAndTheSwitchesTravelAsText() {
        val list = listOf(Reaction("rate_vote", "r1", "용접", 1, 1, up = 1), Reaction("request_like", "q1", "부탁", 1, 2),
            Reaction("a_newer_kind", "x", "", 1, 3))
        assertEquals(listOf("village.request:q1"), Reactions.rows(list, off = setOf("plaza.rate")).map { it.key })
        val off = setOf("village.contest", "plaza.rate", "not.a.kind")
        assertEquals("plaza.rate,village.contest", Reactions.encodeOff(off))
        assertEquals(setOf("plaza.rate", "village.contest"), Reactions.decodeOff(Reactions.encodeOff(off)))
        assertEquals(emptySet<String>(), Reactions.decodeOff(""))
    }
    /** 2.0.0 [RVN-19] HQ-51 A row still in the 알림함 adds what came since; the phone's own count of answers is not added on top. */
    @Test
    fun aRowStillThereAddsWhatCameAndTheAnswersCountedHereAreNotAddedTwice() {
        val day1 = listOf(Reaction("agree", "p1", "교대 광장", 2, 1_000, room = "style-shift", setup = "room", up = 2),
            Reaction("report_vote", "c1/s1", "가나다화학", 1, 1_000, up = 1))
        val day2 = listOf(Reaction("agree", "p1", "교대 광장", 1, 2_000, room = "style-shift", setup = "room", up = 1),
            Reaction("report_vote", "c1/s1", "가나다화학", 1, 2_000, down = 1))
        val both = Reactions.rows(Reactions.merge(day1, day2))
        assertEquals(listOf("교대 광장 · 나도요 3", "가나다화학 · 맞아요 1 · 달라요 1"), both.map { it.line })
        assertEquals(2_000L, both[0].atMillis)
        assertEquals("room", both[0].setup)
        assertEquals("교대 광장", both[0].place)
        // Answers under my room post: the phone's count (unseen) sets the number; the larger stays, its time only moves when it grew.
        val kept = Reactions.withAnswers(emptyList(), listOf(Reaction("answer", "p1", "교대 광장", 2, 3_000, room = "style-shift", setup = "room")))
        val same = Reactions.withAnswers(kept, listOf(Reaction("answer", "p1", "교대 광장", 2, 4_000, room = "style-shift")))
        assertEquals(3_000L, same.single().atMillis)
        val grew = Reactions.withAnswers(same, listOf(Reaction("answer", "p1", "교대 광장", 3, 5_000, room = "style-shift")))
        assertEquals(3, grew.single().count)
        assertEquals("room", grew.single().setup)
        // The server's answers under a room post are the phone's to count; under a rule's talk they are the server's.
        assertTrue(Reactions.roomAnswer(Reaction("answer", "p1", "", 1, 1, room = "style-shift", setup = "room")))
        assertTrue(Reactions.roomAnswer(Reaction("answer", "p1", "", 1, 1, room = "style-shift")))
        assertFalse(Reactions.roomAnswer(Reaction("answer", "p9", "", 1, 1, room = "c123456789ab", setup = "0123456789abcdef")))
        assertFalse(Reactions.roomAnswer(Reaction("thread", "p1", "", 1, 1, room = "style-shift", setup = "room")))
    }
}
