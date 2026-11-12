package com.bamnat.app.ui.money

import com.bamnat.app.ui.invest.cleanDecimal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVK-33] (검토 MONEY-55·75 '코드 검사 규칙: Locale 없는 format 금지') 가계부·투자 code writes no number through the phone's
 * language: a German phone wrote '1.234.567원' and '0,05', and the 기본 수수료 sheet then could not read its own '0,05'. Every
 * `"%…".format(…)` and `String.format("…")` in these folders names its Locale.
 */
class NumberTextGuardTest {
    private val folders = listOf(
        File("src/main/java/com/bamnat/app/ui/money"), File("src/main/java/com/bamnat/app/ui/invest"),
        File("../core/src/main/kotlin/com/bamnat/core/money"), File("../core/src/main/kotlin/com/bamnat/core/invest"),
    )
    private val bare = listOf(Regex("\"%[^\"\\n]*\"\\.format\\((?!\\s*(java\\.util\\.)?Locale)"), Regex("String\\.format\\(\\s*\""))

    @Test fun everyNumberTextNamesItsLocale() {
        assertTrue("폴더를 못 찾았어요: ${folders.filterNot { it.isDirectory }}", folders.all { it.isDirectory })
        val found = folders.flatMap { dir -> dir.walk().filter { it.extension == "kt" }.toList() }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line -> if (bare.any { it.containsMatchIn(line) }) "${f.name}:${i + 1}: ${line.trim()}" else null }
        }
        assertEquals("Locale 없이 숫자를 글로 만드는 곳(Locale.ROOT를 넣어 주세요)", emptyList<String>(), found)
    }

    @Test fun aPriceFieldKeepsThousandsOnAKoreanPhone() {
        // MONEY-70 through the field's own cleaner: the test runs with the JVM's language, which writes decimals with a point.
        assertEquals("72500", cleanDecimal("72,500"))
        assertEquals("1.5", cleanDecimal("1.5"))
    }
}
