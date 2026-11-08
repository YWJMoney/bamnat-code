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
        assertTrue(body.getValue("임금").contains("식대 5,000원(일한 날마다)"))
        assertFalse("5인 미만은 가산 줄 없음", body.getValue("임금").contains("50%"))
        assertTrue(body.getValue("연차 유급휴가").contains("5명 미만"))
        assertTrue(StaffContract.doc(place, s, c).title.startsWith("단시간"))
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
}
