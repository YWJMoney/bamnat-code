package com.bamnat.core

import com.bamnat.core.plaza.Reaction
import com.bamnat.core.plaza.Reactions
import org.junit.Test
import org.junit.Assert.assertEquals

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
}
