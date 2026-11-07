package com.bamnat.core

import com.bamnat.core.plaza.CareNote
import com.bamnat.core.plaza.CommunityCare
import com.bamnat.core.plaza.PlazaNews
import com.bamnat.core.plaza.PlazaNewsItem
import com.bamnat.core.plaza.PostNudge
import com.bamnat.core.plaza.TeamLinkText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 1.39 [CARE-03][CARE-10] 마을 돌봄의 말: 부드럽고, 무엇·근거·언제까지·해명하는 법이 늘 함께(조사 ①). */
class CommunityCareTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val until = java.time.ZonedDateTime.of(2026, 11, 10, 22, 0, 0, 0, seoul).toInstant().toEpochMilli()

    @Test fun theFirstNoteIsGentleAndSaysTheRuleAndHowToExplain() {
        val w = CareNote("c1", "auto_first", by = "auto", about = "post")
        val body = CommunityCare.warningBody(w)
        assertEquals("마을 이용 안내", CommunityCare.warningTitle(w))
        assertTrue(body, body.contains("광장·토론에 쓴 글") && body.contains("처음이라 안내만") && body.contains(CommunityCare.RULE) && body.contains("해명하기"))
        val op = CommunityCare.warningBody(CareNote("c2", "abuse", note = "다른 분을 탓하는 말이 신고됐어요"))
        assertTrue(op, op.contains("운영자가 직접 확인") && op.contains("운영자 한 줄: 다른 분을 탓하는 말이 신고됐어요"))
    }

    @Test fun aHoldSaysUntilWhenWhatStillWorksAndTheAppealState() {
        val h = CareNote("c3", "spam", scope = "opinion", days = 3, untilMillis = until, about = "talk")
        assertEquals("🌙 11월 10일까지 의견 쓰기를 쉬어 가요", CommunityCare.holdLine(h, seoul))
        val body = CommunityCare.holdBody(h, 48, seoul)
        assertTrue(body, body.contains("작품 이야기") && body.contains("11월 10일까지") && body.contains("문의하기는 언제나") && body.contains("48시간"))
        assertTrue(CommunityCare.holdBody(h.copy(appeal = ""), 48, seoul).contains("읽고 있어요"))
        assertTrue(CommunityCare.holdBody(h.copy(scope = "community"), 48, seoul).contains("좋아요·투표"))
        assertEquals("🌙 11월 10일까지 마을 쓰기를 쉬어 가요", CommunityCare.holdLine(h.copy(scope = "community"), seoul))
    }

    @Test fun mutedNewsAndAppealWords() {
        assertTrue(CommunityCare.mutedText(until, seoul).startsWith("11월 10일까지"))
        assertEquals("운영 원칙을 지키지 않은 글이 있어 지금은 글을 쓸 수 없어요", CommunityCare.mutedText(null))
        assertEquals(CommunityCare.newsLine("lifted"), PlazaNews.line(PlazaNewsItem("care", result = "lifted")))
        assertNull(CommunityCare.newsLine("odd"))
        assertEquals("10자 이상 적어 주세요", CommunityCare.appealProblem("짧아요"))
        assertNull(CommunityCare.appealProblem("다른 분 글을 인용한 거였어요"))
        assertTrue(CommunityCare.appealError("already").contains("이미"))
    }

    @Test fun theNudgeAsksOnRoughWordsOnlyAndNeverOnEverydayOnes() {
        assertTrue(PostNudge.needed("이 병신 같은 회사"))
        assertTrue(PostNudge.needed("씨 발 진짜"))
        assertTrue(PostNudge.needed("ㅅㅂ 야간 또"))
        assertFalse(PostNudge.needed("야간 끝나고 바로 자는 게 좋아요"))
        assertFalse(PostNudge.needed("시발점에서 다시 생각해 봐요"))
        assertFalse(PostNudge.needed("불이 꺼져 있었어요"))
    }

    @Test fun teamLinksSayWhyAContactWasRefused() {
        assertTrue(TeamLinkText.contact("account").contains("계좌번호"))
        assertTrue(TeamLinkText.contact(null).contains("사기"))
    }

    @org.junit.Test
    fun `rules 2판 only while care is on`() {
        val r = com.bamnat.core.plaza.PlazaRules
        org.junit.Assert.assertTrue(r.agreed(1, care = false))
        org.junit.Assert.assertFalse(r.agreed(1, care = true))
        org.junit.Assert.assertTrue(r.agreed(2, care = true))
        org.junit.Assert.assertEquals(listOf(5), r.changedSince(1, care = true))
        org.junit.Assert.assertTrue(r.changedSince(1, care = false).isEmpty())
        org.junit.Assert.assertEquals(r.lines(true).size, r.since(true).size)
        org.junit.Assert.assertEquals(r.LINES.take(5), r.lines(true).take(5))
        org.junit.Assert.assertTrue(r.lines(true).none { Regex("\\d+원").containsMatchIn(it) })
    }

    @org.junit.Test
    fun `badge rest line only when badges are on and not for the first notice`() {
        val first = CareNote(id = "c1", reason = "auto_first", by = "auto")
        val warn = CareNote(id = "c2", reason = "abuse", by = "operator")
        org.junit.Assert.assertFalse(CommunityCare.warningBody(warn).contains("표식"))
        org.junit.Assert.assertTrue(CommunityCare.warningBody(warn, badgeRest = true).contains(CommunityCare.BADGE_REST_WARN))
        org.junit.Assert.assertFalse(CommunityCare.warningBody(first, badgeRest = true).contains("표식"))
    }
}
