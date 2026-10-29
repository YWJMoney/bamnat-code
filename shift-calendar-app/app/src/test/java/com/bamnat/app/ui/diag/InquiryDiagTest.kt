package com.bamnat.app.ui.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** [NXT-12] 1.29: the alarm check that goes with an inquiry leaves the '기능 상태' rows on the phone. */
class InquiryDiagTest {
    @Test fun inquiryCopyDropsFlagRows() {
        val report = DiagReport(listOf("기기" to listOf("Android 15"), DiagReport.OLD_FLAG_SECTION to listOf("billing: 대기")))
        val sent = report.withoutFlags()
        assertEquals(listOf("기기"), sent.sections.map { it.first })
        assertFalse(sent.asText().contains("billing"))
        // The screen's own copy still has them.
        assertEquals(2, report.sections.size)
    }
}
