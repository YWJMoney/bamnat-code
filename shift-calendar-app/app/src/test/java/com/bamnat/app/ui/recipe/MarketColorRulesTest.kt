package com.bamnat.app.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVM-33] 총괄 HQ-71 (조건 6 '의미색을 아껴요': 주황은 주의에만): 장터·작품 상세·공방·만들기 화면의 글은 테마의 주황(`colorScheme.tertiary`,
 * 밝은 #A14F00 · 어두운 #FFB783)을 쓰지 않아요. 무엇을 주는지 알리는 줄('✦ 홈 화면 단추 · 알림 단추 …', '프로에서 켜져요')은 보조 글 색,
 * 주의('! 권고', 만들기 검사의 '!')는 `MaterialTheme.bamnat.caution`, 프로 기능 카드는 조용한 면(surfaceContainerLow).
 * 남은 것은 아래 둘뿐이에요(뜻이 아니라 꾸밈).
 */
class MarketColorRulesTest {
    private val dir = File("src/main/java/com/bamnat/app/ui/recipe")

    /** Where the orange stays, and why. */
    private val allowed = mapOf(
        "ProfileScreens.kt" to "새 이야기가 있는 작가 얼굴의 고리(초록·주황 무늬, 글이 아님)",
        "DetailParts.kt" to "표식이 있는 작가의 동그란 얼굴 바탕(글자는 onSecondaryContainer)",
    )

    @Test fun orangeIsNotAWordColourHere() {
        val orange = Regex("""colorScheme\.(tertiary|onTertiary|tertiaryContainer|onTertiaryContainer)\b""")
        val found = dir.listFiles().orEmpty().filter { it.extension == "kt" }.sortedBy { it.name }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, l -> if (orange.containsMatchIn(l)) "${f.name}:${i + 1}" else null }
        }
        val bad = found.filter { it.substringBefore(':') !in allowed }
        assertEquals("주황(colorScheme.tertiary…)은 주의 색이 아니에요. 알리는 줄은 onSurfaceVariant, 주의는 MaterialTheme.bamnat.caution, 카드는 surfaceContainerLow",
            emptyList<String>(), bad)
        // The two kept ones are one line each; a second use in those files is a new one.
        assertEquals(allowed.keys.sorted(), found.map { it.substringBefore(':') }.sorted())
    }
}
