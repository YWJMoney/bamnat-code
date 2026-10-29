package com.bamnat.app.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 1.32 [LEDX-22] (가계부 조사 보강할 점 "알림 원문 바로 버림, 충돌 보고·이벤트 기록에 금액·가게 없음"): what leaves the money in the
 * ledger never reaches the event log, the statistics or a crash note.
 */
class LedgerPrivacyTest {
    @Test fun anEventsDetailNeverCarriesAShopName() {
        assertTrue(Telemetry.cleanDetail("스타벅스 강남점").startsWith("text"))
        assertTrue(Telemetry.cleanDetail("신한카드 승인 12,000원 GS25").startsWith("text"))
        assertEquals("matched", Telemetry.cleanDetail("matched"))
    }

    @Test fun aCrashNoteNeverQuotesTheErrorsMessage() {
        val note = CrashLog.describe("main", IllegalStateException("이마트 45,600원 결제", RuntimeException("홍길동 계좌")))
        assertFalse(note, note.contains("이마트") || note.contains("45,600") || note.contains("홍길동"))
        assertTrue(note.contains("IllegalStateException"))
    }

    /** No event, journal of usage or crash line is made from a ledger line's amount, shop, category or memo. */
    @Test fun noEventIsMadeFromALedgerLine() {
        val money = Regex("""\.(amount|merchant|category|note|memo|cardLast4|accountName)\b|Fmt\.won|won\(""")
        val calls = Regex("""(telemetry\.event|usageStats\.\w+|lookupStats\.\w+|startupStats\.\w+)\(([^\n]*)""")
        val found = mutableListOf<String>()
        File("src/main/java").walkTopDown().filter { it.extension == "kt" }.forEach { f ->
            f.readLines().forEachIndexed { i, line ->
                calls.find(line)?.let { m -> if (money.containsMatchIn(m.groupValues[2])) found += "${f.name}:${i + 1}: ${line.trim()}" }
            }
        }
        assertEquals("가계부 금액·가게·분류·메모로 만든 이벤트", emptyList<String>(), found)
    }
}
