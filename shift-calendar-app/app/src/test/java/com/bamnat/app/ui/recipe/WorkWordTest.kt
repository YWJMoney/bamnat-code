package com.bamnat.app.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * [RVM-10] PC-47 ('작품'과 '레시피'가 섞여 쓰여요: '이 레시피가 바꾼 설정만 돌아가요', '스튜디오에서 이 레시피부터', 설정 '장터·내 레시피'):
 * what the app says calls it 작품. '레시피' stays only as a word that still finds it (설정 찾기·도움말 찾는 말), never on a screen.
 */
class WorkWordTest {
    /** Files whose '레시피' strings are search words: typed by a person who remembers the old name, never shown. */
    private val searchWords = setOf("HelpFaq.kt", "SettingsSearch.kt")

    @Test fun theAppSaysWork() {
        val found = File("src/main/java").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name !in searchWords }.flatMap { f ->
            var inBlock = false
            f.readLines().mapIndexedNotNull { i, raw ->
                val line = raw.trim()
                val wasBlock = inBlock
                if (line.startsWith("/*")) inBlock = true
                if (inBlock && line.contains("*/")) { inBlock = false; return@mapIndexedNotNull null }
                if (wasBlock || inBlock || line.startsWith("//") || line.startsWith("*")) return@mapIndexedNotNull null
                val code = line.substringBefore(" // ")
                Regex("\"([^\"\\\\]|\\\\.)*\"").findAll(code).map { it.value }.filter { "레시피" in it }.toList()
                    .takeIf { it.isNotEmpty() }?.let { "${f.name}:${i + 1} $it" }
            }.asSequence()
        }.toList()
        assertEquals("'레시피' on a screen (say 작품):\n" + found.joinToString("\n"), emptyList<String>(), found)
    }
}
