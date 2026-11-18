package com.bamnat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVJ-79] 총괄 HQ-125 (조건 5·14): 눌리는 한 줄([ToneLine]에 onClick)은 가는 곳 낱말을 [action]으로 받아 알림함 줄·안내 카드처럼
 * 파란 글자로 보여 줘요. 회색 글 끝에 '· 입사일 넣기'를 적어 넣으면 읽는 글로 보여 누르지 않았어요. 펼치는 줄(펼치기 화살표 `trailing`과
 * 화면 읽기 `onClickLabel`이 있는 줄)은 화살표가 그 표시라 빼요. 아래 목록은 다른 갈래가 맡은 화면이라 그 갈래가 옮길 때까지 남겨 둬요.
 */
class ToneLineActionTest {
    private val main = File("src/main/java/com/bamnat/app")

    private val later = mapOf(
        "StudioScreen.kt" to "갈래 S(스튜디오): 달력 머리 높이 줄",
        "StudioPartsTool.kt" to "갈래 S(스튜디오): '누르면 그 색으로 저장해요' 줄",
    )

    @Test
    fun `every line that goes somewhere says where`() {
        val missing = mutableListOf<String>()
        main.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ToneLine.kt" && it.name !in later }.forEach { f ->
            val s = f.readText()
            Regex("""ToneLine\(""").findAll(s).forEach { m ->
                val call = s.callFrom(m.range.last)
                if ("onClick" in call && "action =" !in call && "trailing =" !in call) {
                    missing += "${f.name}:${s.substring(0, m.range.first).count { it == '\n' } + 1}"
                }
            }
        }
        assertEquals("눌리는 ToneLine에 action(가는 곳 낱말)이 없어요", emptyList<String>(), missing)
    }

    /** The arguments of the call whose '(' is at [open]: up to its matching ')'. */
    private fun String.callFrom(open: Int): String {
        var depth = 0
        for (i in open until length) {
            when (this[i]) { '(' -> depth++; ')' -> if (--depth == 0) return substring(open, i + 1) }
        }
        return substring(open)
    }
}
