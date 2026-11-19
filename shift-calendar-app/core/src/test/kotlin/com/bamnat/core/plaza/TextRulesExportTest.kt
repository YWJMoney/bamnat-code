package com.bamnat.core.plaza

import com.bamnat.core.model.ShiftRulesExport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVN-22] 검토 I10bb-03: the phone's check is the server's. [TextRules] is the 원본; the company server reads
 * `server/company-worker/src/text-rules.js` made from it. After changing TextRules:
 *
 *     BAMNAT_WRITE_TEXT_RULES=1 ./gradlew -p core test --tests '*TextRulesExportTest*' --rerun
 *
 * then `npm test` in server/company-worker (its test holds personal() and contact() to the same samples).
 */
class TextRulesExportTest {
    @Test fun theServersCopyIsCurrent() {
        val want = render()
        val file = serverFile()
        if (System.getenv("BAMNAT_WRITE_TEXT_RULES") == "1") file.writeText(want)
        val have = if (file.exists()) file.readText() else ""
        assertEquals("${file.path} is older than core TextRules. Write it again with: " +
            "BAMNAT_WRITE_TEXT_RULES=1 ./gradlew -p core test --tests '*TextRulesExportTest*' --rerun (then `npm test` in server/company-worker)", want, have)
    }

    @Test fun everySampleSaysWhatItShould() {
        for ((text, personal, contact) in TextRules.SAMPLES) {
            assertEquals("personal: $text", personal, TextRules.personal(text))
            assertEquals("contact: $text", contact, TextRules.contact(text))
        }
    }

    @Test fun theHitIsTheVeryLetters() {
        val hit = TextRules.post("오늘 야간 끝, 110-123-456789 로 보내 주세요", room = true, roomMoney = true)!!
        assertEquals("account", hit.code)
        assertEquals("110-123-456789", hit.text)
        assertEquals("010-1234-5678", TextRules.post("연락은 010-1234-5678", room = false, roomMoney = false)!!.text)
        // With 방 글에 금액 받기 on, an amount in a room's post goes; elsewhere it does not.
        assertNull(TextRules.post("이번 달 야간 수당 32만 원", room = true, roomMoney = true))
        assertEquals("32만 원", TextRules.post("이번 달 야간 수당 32만 원", room = false, roomMoney = true)!!.text)
    }

    private fun render(): String = buildString {
        appendLine("// [RVN-22] 만든 파일이에요. 손으로 고치지 마세요.")
        appendLine("// 원본: shift-calendar-app/core/src/main/kotlin/com/bamnat/core/plaza/TextRules.kt (글에 받지 않는 것, 검토 I10bb-03)")
        appendLine("// 다시 만들기: BAMNAT_WRITE_TEXT_RULES=1 ./gradlew -p core test --tests '*TextRulesExportTest*' --rerun")
        appendLine("// 낡으면 core TextRulesExportTest가 실패해요. 회사 서버 index.js의 personal()·contact()가 이 표를 읽어요.")
        appendLine()
        appendLine("/** Each rule: code, source and flags of its RegExp, and digits [min, max] a match must hold (null: any). */")
        appendLine("export const TEXT_RULES = ${ShiftRulesExport.json(linkedMapOf("personal" to rules(TextRules.personalRules), "contact" to rules(TextRules.contactRules)))};")
        appendLine()
        appendLine("/** Texts and what personal() and contact() say of them (null: it goes). */")
        appendLine("export const TEXT_RULE_SAMPLES = [")
        TextRules.SAMPLES.forEach { (t, p, c) -> appendLine("  ${ShiftRulesExport.json(linkedMapOf("text" to t, "personal" to p, "contact" to c))},") }
        appendLine("];")
    }

    private fun rules(list: List<TextRules.Rule>) = list.map { r ->
        linkedMapOf("code" to r.code, "source" to r.pattern, "flags" to if (r.ignoreCase) "i" else "", "digits" to r.digits?.let { listOf(it.first, it.last) })
    }

    private fun serverFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val src = File(dir, "server/company-worker/src")
            if (src.isDirectory) return File(src, "text-rules.js")
            dir = dir.parentFile
        }
        error("server/company-worker/src not found above ${File("").absolutePath}")
    }
}
