package com.bamnat.app.rules

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 2.0.0 갈래 K 10차 [RVK-45] (디자인 품질 조건 6): 가계부 화면에서 빨강(CalendarColors.warning)은 저장을 막는 잘못된 값에만 써요.
 * '약속일 지남'·'안 받음'·'예산 선'·'곧 빠짐'·'외상'처럼 주의를 뜻하는 곳은 주황(MaterialTheme.bamnat.caution)이에요. 새로 빨강을
 * 쓰려면 아래 목록에 그 줄을 넣고 까닭을 적어요.
 */
class MoneyCautionRedTest {
    private val money = File("src/main/java/com/bamnat/app/ui/money")

    /** The red that stays: what was typed cannot be saved as it is. */
    private val inputErrors = listOf(
        "나눈 금액이 전체보다", // LedgerEntrySheet: the parts add up to more than the whole
        "그보다 적게 할 수 없어요", // OwedScreen: less than what came back already
        "약속일이 생긴 날보다 앞이에요", // OwedScreen: the promised day before the day it was owed
    )

    @Test fun redInTheLedgerIsOnlyForWhatCannotBeSaved() {
        val red = Regex("""\.warning\b(?!Container)""")
        val found = money.walkTopDown().filter { it.extension == "kt" }.flatMap { f ->
            val lines = f.readLines()
            lines.indices.filter { red.containsMatchIn(lines[it]) }.map { i ->
                val near = (maxOf(0, i - 3)..i).joinToString("\n") { lines[it] }
                Triple(f.name, i + 1, inputErrors.firstOrNull { it in near })
            }
        }.toList()
        assertEquals("주의 뜻의 빨강: ${found.filter { it.third == null }}", emptyList<Triple<String, Int, String?>>(), found.filter { it.third == null })
        assertEquals("남은 빨강은 저장을 막는 세 줄", inputErrors.toSet(), found.mapNotNull { it.third }.toSet())
    }
}
