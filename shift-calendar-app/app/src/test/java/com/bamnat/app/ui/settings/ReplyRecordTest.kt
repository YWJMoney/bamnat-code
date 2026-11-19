package com.bamnat.app.ui.settings

import com.bamnat.app.ui.diag.EVENTS_SECTION
import com.bamnat.app.ui.diag.STOP_SECTION
import com.bamnat.app.ui.diag.STOP_TRAIL_SECTION
import com.bamnat.app.ui.diag.USAGE_SECTION
import com.bamnat.core.diag.PastedCheck
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 2.0.0 [RVO-28] HQ-150 (PC-201): 답장의 점검 기록과 붙여 넣은 보고서. */
class ReplyRecordTest {
    /** 앱이 만드는 보고서의 칸 이름을 core가 모두 알아야 붙여 넣은 보고서를 알아봐요. */
    @Test fun everyReportSectionIsKnown() {
        for (s in listOf(STOP_SECTION, STOP_TRAIL_SECTION, USAGE_SECTION, EVENTS_SECTION)) assertTrue(s, s in PastedCheck.SECTIONS)
        val source = File("src/main/java/com/bamnat/app/ui/diag/DiagnosticsScreen.kt").readText()
        val literal = Regex("""^\s+"([^"$]+)" to (alarm|permissions|device)\b""", RegexOption.MULTILINE).findAll(source).map { it.groupValues[1] }.toList()
        assertEquals(listOf("알람 상태", "권한", "기기"), literal)
        for (s in literal) assertTrue(s, s in PastedCheck.SECTIONS)
    }

    /** 붙여 넣은 보고서는 2,000자 말 대신 다음 할 일이 막아요. 빼면 원래 판정으로 돌아와요. */
    @Test fun pastedSaysWhatToDoInstead() {
        assertEquals(PASTED_LINE, replyProblem("2000자까지 쓸 수 있어요", pasted = true))
        assertEquals(PASTED_LINE, replyProblem(null, pasted = true))
        assertEquals("2000자까지 쓸 수 있어요", replyProblem("2000자까지 쓸 수 있어요", pasted = false))
        assertEquals(null, replyProblem(null, pasted = false))
    }
}
