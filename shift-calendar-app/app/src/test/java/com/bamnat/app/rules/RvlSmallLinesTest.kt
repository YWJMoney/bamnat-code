package com.bamnat.app.rules

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 갈래 L: 시험으로 지킬 길이 글 한 줄·표시 한 줄뿐이라 소스에서 그 줄이 있는지(없는지) 본다.
 * (묶음 1 '근거 없는 줄'의 L 두 줄: DATA-26 → RVL-11, SCAN-27 → RVL-27)
 */
class RvlSmallLinesTest {
    private val ui = File("src/main/java/com/bamnat/app/ui")

    /** [RVL-11] 복구 코드를 복사할 때 '민감' 표시가 붙어, 안드로이드 13+가 복사 미리 보기에 코드를 보이지 않아요. */
    @Test
    fun theRecoveryCodeIsCopiedMarkedSensitive() {
        val src = File(ui, "pro/ProScreen.kt").readText()
        val copy = src.substringAfter("private fun copy(context: Context, text: String)").substringBefore("/** [INPUT-04]")
        assertTrue("복구 코드 복사에 민감 표시가 있어야 해요", "IS_SENSITIVE" in copy)
        assertTrue("두 길(13 이상·미만) 모두 같은 표시", "android.content.extra.IS_SENSITIVE" in copy && "EXTRA_IS_SENSITIVE" in copy)
        assertTrue("setPrimaryClip 앞에 달려 있어야 해요", copy.indexOf("IS_SENSITIVE") < copy.indexOf("setPrimaryClip"))
    }

    /** [RVL-27] 옛 엑셀(.xls)은 그대로 읽어요: 근태 화면이 '다시 저장해 주세요'라고 하지 않아요. */
    @Test
    fun theAttendanceScreenDoesNotAskToSaveAnOldXlsAgain() {
        val src = File(ui, "imports/AttendanceImportScreen.kt").readText()
        val shown = src.lines().filter { !it.trimStart().startsWith("//") && !it.trimStart().startsWith("*") && !it.trimStart().startsWith("/*") }
        assertFalse(".xls를 다시 저장하라는 글이 남아 있어요", shown.any { Regex("""\.xls(?!x)""").containsMatchIn(it) && "저장" in it })
    }
}
