package com.bamnat.app.rules

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 갈래 K 10차 [RVK-45] (디자인 품질 조건 6): 가계부 화면에서 빨강(CalendarColors.warning)을 주의 뜻으로 쓰지 않아요.
 * '약속일 지남'·'안 받음'·'예산 선'·'곧 빠짐'·'외상'처럼 주의를 뜻하는 곳은 주황(MaterialTheme.bamnat.caution)이에요.
 * 갈래 P의 주의·위험 나누기(#512) 뒤로는 저장을 막는 잘못된 값 세 줄도 주의(△ 표시가 붙는 [Tone.Caution])로 말해요: 위험은 법을
 * 어겼거나 되돌릴 수 없는 것에만 써요. 그래서 가계부에는 CalendarColors.warning 빨강이 남지 않아요. 새로 쓰려면 까닭을 여기 적어요.
 */
class MoneyCautionRedTest {
    private val money = File("src/main/java/com/bamnat/app/ui/money")

    /** What was typed cannot be saved as it is: said in the 주의 tone, with its mark. */
    private val inputErrors = listOf(
        "나눈 금액이 전체보다", // LedgerEntrySheet: the parts add up to more than the whole
        "그보다 적게 할 수 없어요", // OwedScreen: less than what came back already
        "약속일이 생긴 날보다 앞이에요", // OwedScreen: the promised day before the day it was owed
    )

    @Test fun redInTheLedgerIsNotUsedForCaution() {
        val red = Regex("""\.warning\b(?!Container)""")
        val found = money.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val lines = f.readLines()
            lines.indices.filter { red.containsMatchIn(lines[it]) }.map { i -> "${f.name}:${i + 1}" }
        }.toList()
        assertEquals("가계부에 남은 빨강", emptyList<String>(), found)
    }

    @Test fun whatCannotBeSavedIsSaidInTheCautionTone() {
        val files = money.walkTopDown().filter { it.extension == "kt" }.map { it.name to it.readLines() }.toList()
        for (said in inputErrors) {
            val at = files.flatMap { (name, lines) -> lines.indices.filter { said in lines[it] }.map { Triple(name, it, lines) } }
            assertTrue("'$said' 줄이 가계부 화면에 없어요", at.isNotEmpty())
            for ((name, i, lines) in at) {
                val near = (maxOf(0, i - 1)..minOf(lines.lastIndex, i + 3)).joinToString("\n") { lines[it] }
                assertTrue("$name:${i + 1} '$said'는 주의 색으로", "ToneLine(" in near || "Tone.Caution" in near)
            }
        }
    }
}
