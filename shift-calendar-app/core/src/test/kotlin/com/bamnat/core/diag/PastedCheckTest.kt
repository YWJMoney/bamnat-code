package com.bamnat.core.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVO-28] HQ-150 (PC-201): 붙여 넣은 점검 보고서를 알아보고, 기록을 부탁한 답을 알아봐요. */
class PastedCheckTest {
    private val report = "[앱 멈춤]\n가장 최근 기록: 11.19 07:02\n남은 기록: 3개\n\n[알람 상태]\n알람 사용: 켜짐\n\n[권한]\n알림: 허용\n\n[최근 이벤트 100개]\n11.19 07:01  app_open"

    @Test fun findsTheReportAfterWhatWasWritten() {
        val text = "말씀하신 기록 보내 드려요\n\n$report"
        assertEquals(text.indexOf("[앱 멈춤]"), PastedCheck.start(text))
        assertEquals("말씀하신 기록 보내 드려요", PastedCheck.without(text))
        assertEquals("", PastedCheck.without(report))
    }

    @Test fun oneBracketInWordsIsNotAReport() {
        val text = "설정에서 [기기] 줄을 봤는데 이상해요"
        assertNull(PastedCheck.start(text))
        assertEquals(text, PastedCheck.without(text))
        assertNull(PastedCheck.start("[기기]\n갤럭시\n\n[기기]\n또"))
    }

    @Test fun anAnswerAskingForTheRecordIsKnown() {
        assertTrue(PastedCheck.asked("오른쪽 위 ⋮(더 보기) › '보고서 복사'를 눌러 주세요"))
        assertTrue(PastedCheck.asked("아래 '점검 기록도 보내기'를 켜고 보내 주세요"))
        assertTrue(PastedCheck.asked("휴대폰에 남은 '앱 멈춤' 기록이 꼭 필요해요"))
        assertFalse(PastedCheck.asked("다음 업데이트에서 고칠게요"))
        assertFalse(PastedCheck.asked(null))
    }
}
