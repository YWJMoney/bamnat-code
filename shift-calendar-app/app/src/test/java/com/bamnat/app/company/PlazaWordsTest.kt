package com.bamnat.app.company

import com.bamnat.core.plaza.PlazaWords
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVN-18] 검토 COMP-19·I10aa-04: the company and plaza screens call one thing by one name ([PlazaWords]): 맞아요 (not 맞았어요),
 * 마을 이름 (not 별명), 안 보는 사람 (not 가린 사람), and an edition is a 판 ("3판", not "v3"). Comments may say the old words; what the
 * screens say may not.
 */
class PlazaWordsTest {
    private fun said(dir: String): List<String> = File(dir).walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
        var inBlock = false
        f.readLines().mapIndexedNotNull { i, raw ->
            val line = raw.trim()
            val wasBlock = inBlock
            if (line.startsWith("/*")) inBlock = true
            if (inBlock && line.contains("*/")) { inBlock = false; return@mapIndexedNotNull null }
            if (wasBlock || inBlock || line.startsWith("//") || line.startsWith("*")) return@mapIndexedNotNull null
            "${f.name}:${i + 1}: ${line.substringBefore(" // ")}"
        }
    }.toList()

    @Test fun theCompanyScreensSayEachThingByItsOneName() {
        val lines = said("src/main/java/com/bamnat/app/ui/company")
        val others = lines.flatMap { l -> PlazaWords.others(l.substringAfter(": ")).map { w -> "$l  ← ‘$w’ → ‘${PlazaWords.OTHER_NAMES[w]}’" } }
        assertEquals(others.joinToString("\n"), emptyList<String>(), others)
        // An edition said as "v3" (the server's numbers) is "3판".
        val v = Regex("""" v\$|"v\$\{|\{ "v\$""")
        val versions = lines.filter { v.containsMatchIn(it) && "telemetry" !in it && "event(" !in it }
        assertEquals(versions.joinToString("\n"), emptyList<String>(), versions)
    }

    @Test fun theWordsReadAsOne() {
        assertEquals("3판", PlazaWords.edition(3))
        assertEquals(" · 2판", PlazaWords.editionAfter(2))
        assertEquals("", PlazaWords.editionAfter(null))
        assertEquals(listOf("맞았어요", "별명"), PlazaWords.others("맞았어요 · 별명"))
        assertEquals(emptyList<String>(), PlazaWords.others("맞아요 · 마을 이름 · 앱을 최신 버전으로 업데이트해 주세요"))
    }
}
