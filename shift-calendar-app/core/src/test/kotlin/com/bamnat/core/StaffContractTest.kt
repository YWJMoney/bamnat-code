package com.bamnat.core

import com.bamnat.core.boss.StaffAllowance
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** 1.38 [P3-X-06] 근로계약서: the 제17조 items from the staff row, and what is missing. */
class StaffContractTest {
    private val place = Workplace("p", "가나다카페", fivePlus = false, payday = 10)
    private val s = StaffMember("s", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 11, 2), job = "홀",
        weekly = mapOf(DayOfWeek.SATURDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(18, 0)), DayOfWeek.SUNDAY to StaffShift(LocalTime.of(10, 0), LocalTime.of(18, 0))),
        allowances = listOf(StaffAllowance("식대", 5_000, perDay = true)))

    @Test fun fillsTheItems() {
        val c = StaffContract.of(place, s)
        assertTrue(c.missing.isEmpty())
        assertTrue("주 15시간: 단시간", c.shortTime)
        val body = c.items.associate { it.title to it.body }
        assertEquals("2026년 11월 2일부터 (기간을 정하지 않음)", body["근로 시작일"])
        assertTrue(body.getValue("근무 요일과 시간").contains("토요일 10:00~18:00 (휴게 30분"))
        assertTrue(body.getValue("근무 요일과 시간").contains("한 주 소정근로시간 15시간"))
        assertTrue(body.getValue("휴일").startsWith("매주 월요일 (유급 주휴일)"))
        assertTrue(body.getValue("임금").contains("시급 10,320원"))
        assertTrue(body.getValue("임금").contains("식대 5,000원(일한 날마다, 비과세)"))
        assertFalse("5인 미만은 가산 줄 없음", body.getValue("임금").contains("50%"))
        assertTrue(body.getValue("연차 유급휴가").contains("5명 미만"))
        assertTrue(StaffContract.doc(place, s, c).title.startsWith("단시간"))
    }

    /** 2.0.0 [RVG-03] 검토 BOSS-51: 한 주 15시간 미만은 5인 이상이어도 제60조 연차가 없어요(제18조 제3항). */
    @Test fun underFifteenHoursHasNoAnnualLeave() {
        val four = StaffShift(LocalTime.of(10, 0), LocalTime.of(14, 0))
        val short = s.copy(weekly = mapOf(DayOfWeek.SATURDAY to four, DayOfWeek.SUNDAY to four))
        val body = StaffContract.of(place.copy(fivePlus = true), short).items.associate { it.title to it.body }
        assertTrue(body.getValue("연차 유급휴가").contains("15시간 미만"))
        assertFalse(body.getValue("연차 유급휴가").contains("15일"))
        assertTrue(body.getValue("휴일").contains("15시간 미만"))
        // 15시간 이상, 5인 이상은 그대로 제60조.
        val full = StaffContract.of(place.copy(fivePlus = true), s).items.associate { it.title to it.body }
        assertTrue(full.getValue("연차 유급휴가").startsWith("근로기준법 제60조에 따라"))
    }

    @Test fun saysWhatIsMissing() {
        val c = StaffContract.of(place.copy(payday = null), s.copy(job = "", hiredOn = null, weekly = emptyMap()))
        assertEquals(listOf("입사일(근로 시작일)", "하는 일(업무)", "평소 근무 요일·시간", "급여 지급일(사업장 설정)"), c.missing)
    }

    @Test fun signedCopyKeepsWhatWasSentAndTheLedgerKnows() {
        val c = StaffContract.of(place, s)
        val sent = StaffContract.Sent(StaffContract.title(c), c.items, sentAt = 0, linkId = "x", token = "t", expiresAt = 10_000)
        assertTrue(sent.waiting(5_000))
        assertFalse("기한 지남", sent.waiting(10_000))
        assertTrue(StaffContract.lacking(s.copy(contract = sent), LocalDate.of(2026, 11, 20)))
        // 2026-11-03 09:05 KST
        val at = java.time.ZonedDateTime.of(2026, 11, 3, 9, 5, 0, 0, java.time.ZoneId.of("Asia/Seoul")).toInstant().toEpochMilli()
        val signed = sent.copy(signedName = "박마바", signedAt = at, linkId = null, token = null)
        assertFalse(StaffContract.lacking(s.copy(contract = signed), LocalDate.of(2026, 11, 20)))
        assertFalse("퇴사한 사람은 표시 안 함", StaffContract.lacking(s.copy(leftOn = LocalDate.of(2026, 11, 10)), LocalDate.of(2026, 11, 20)))
        // The row changes after: the PDF still says what was signed.
        val doc = StaffContract.doc(place, s.copy(base = 12_000), signed)
        val text = doc.blocks.filterIsInstance<com.bamnat.core.report.ReportDoc.Paragraph>().joinToString("\n") { it.text }
        assertTrue(text.contains("시급 10,320원"))
        assertTrue(text.contains("근로자 박마바 — 링크에서 읽고 이름을 써서 동의함 (2026년 11월 3일 09:05)"))
        val paper = StaffContract.doc(place, s, sent.copy(signedAt = at, paper = true))
        assertTrue(paper.blocks.filterIsInstance<com.bamnat.core.report.ReportDoc.Paragraph>().any { it.text.contains("종이 계약서에 서명함") })
    }

    @Test fun aNewContractKeepsTheSignedOne() {
        val c = StaffContract.of(place, s)
        val first = StaffContract.Sent("근로계약서", c.items, sentAt = 1, signedAt = 2, signedName = "박마바")
        val waiting = first.replacedBy(StaffContract.Sent("근로계약서", c.items, sentAt = 3, linkId = "x", token = "t"))
        assertEquals(listOf(2L), waiting.past.map { it.signedAt })
        // An unsigned one replaced again is not kept.
        val again = waiting.replacedBy(StaffContract.Sent("근로계약서", c.items, sentAt = 4, linkId = "y", token = "u"))
        assertEquals(1, again.past.size)
    }

    /** 2.0.0 [RVJ-24] 검토 BOSS-53: 종이 계약서를 받은 날을 고를 수 있고(늘 오늘이었어요), 그날이 PDF의 '받음'에 남아요. */
    @Test fun paperKeepsTheDayItCameBack() {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val now = LocalDate.of(2026, 11, 12).atTime(15, 30).atZone(zone).toInstant().toEpochMilli()
        val c = StaffContract.of(place, s)
        val today = StaffContract.onPaper(c, now, LocalDate.of(2026, 11, 12), zone)
        assertEquals("오늘 받은 것은 누른 때", now, today.signedAt)
        val lastWeek = StaffContract.onPaper(c, now, LocalDate.of(2026, 11, 5), zone)
        assertTrue(lastWeek.signed && lastWeek.paper)
        assertEquals("표시한 때는 지금", now, lastWeek.sentAt)
        assertEquals(LocalDate.of(2026, 11, 5), java.time.Instant.ofEpochMilli(lastWeek.signedAt!!).atZone(zone).toLocalDate())
        assertTrue(StaffContract.doc(place, s, lastWeek, zone).blocks.toString().contains("종이 계약서에 서명함 (2026년 11월 5일 받음)"))
        // A day still to come is not a day it came back: it reads as today.
        assertEquals(now, StaffContract.onPaper(c, now, LocalDate.of(2026, 11, 20), zone).signedAt)
    }

    /** 2.0.0 [RVJ-24] 검토 BOSS-53: 잘못 누른 '종이로 받았어요'는 되돌릴 수 있고, 그 앞의 서명한 계약서와 지난 계약서는 그대로예요. */
    @Test fun paperMarkCanBeTakenBack() {
        val zone = java.time.ZoneId.of("Asia/Seoul")
        val c = StaffContract.of(place, s)
        val paper = StaffContract.onPaper(c, 2_000L, LocalDate.of(1970, 1, 1), zone)
        assertEquals("앞에 아무것도 없었으면 계약서 없음으로", null, StaffContract.withoutPaper(paper))
        assertTrue(StaffContract.lacking(s.copy(contract = StaffContract.withoutPaper(paper)), LocalDate.of(2026, 11, 12)))
        // A contract signed on the link, replaced by a paper one pressed by mistake: the signed one is the contract again.
        val older = StaffContract.Sent("근로계약서", c.items, 100L, signedName = "박마바", signedAt = 150L)
        val linked = StaffContract.Sent("근로계약서", c.items, 500L, signedName = "박마바", signedAt = 600L, past = listOf(older))
        val back = StaffContract.withoutPaper(linked.replacedBy(paper))
        assertEquals(600L, back?.signedAt)
        assertFalse(back!!.paper)
        assertEquals("지난 계약서는 그대로", listOf(older), back.past)
        // What the staff member signed on the link is never taken back here, nor a link still waiting.
        assertEquals(linked, StaffContract.withoutPaper(linked))
        val waiting = StaffContract.Sent("근로계약서", c.items, 700L, linkId = "l", token = "t")
        assertEquals(waiting, StaffContract.withoutPaper(waiting))
        assertEquals(null, StaffContract.withoutPaper(null))
    }
}
