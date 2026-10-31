package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** [RCX-16] 1.35 아이폰 형식: the recipe format leans on the JVM in one file only, and its plain-Kotlin numbers match the old ones. */
class RecipePlatformTest {
    @Test fun onlyThePlatformFileUsesTheJvm() {
        val jvm = Regex("""\bjava\.|\bMath\.|\.format\(|::class\.java|Charsets\.""")
        val found = File("src/main/kotlin/com/bamnat/core/recipe").listFiles()!!.filter { it.extension == "kt" && it.name != "RecipePlatform.kt" }
            .flatMap { f -> f.readLines().mapIndexedNotNull { i, l -> if (jvm.containsMatchIn(l)) "${f.name}:${i + 1}: ${l.trim()}" else null } }
        assertEquals("레시피 형식에서 JVM 전용 호출은 RecipePlatform.kt에만 둬요", emptyList<String>(), found)
    }

    @Test fun numbersAreWrittenAsBefore() {
        for (v in listOf(0L, 7L, 999L, 1000L, 10320L, 1234567L, -2500000L)) assertEquals("%,d".format(v), RecipeNumbers.grouped(v))
        for (c in listOf(150L, 25L, 5L, 100L, -310L, 1234L, 0L, -5L)) {
            assertEquals(java.math.BigDecimal.valueOf(c, 2).stripTrailingZeros().toPlainString(), RecipeNumbers.cents(c))
        }
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", RecipeCodec.sha256("abc"))
    }
}
