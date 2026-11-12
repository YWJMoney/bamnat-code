package com.bamnat.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVJ-67] 총괄 PC-167 (조건 16 '사람 말투'): 가게 매출·영업 탭 맨 아래 '…시작한 날에 모두 셉니다'처럼 '~니다'로 끝나는 화면 글이 남아
 * 있었어요. 화면 글은 '~해요'로 해요. 글을 가려내는 Regex(남이 보낸 알림·명세서를 읽는 말)와 주석은 세지 않아요.
 */
class HaeyoToneTest {
    @Test fun screenWordsSayHaeyo() {
        val src = File("src/main/java")
        val literal = Regex(""""(?:[^"\\]|\\.)*"""")
        val formal = Regex("[가-힣]니다")
        val found = src.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            f.readLines().mapIndexedNotNull { i, line ->
                val t = line.trim()
                if (t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || "Regex(" in line) null
                else literal.findAll(line).map { it.value }.firstOrNull { formal.containsMatchIn(it) }
                    ?.let { "${f.relativeTo(src).invariantSeparatersPath}:${i + 1} $it" }
            }.asSequence()
        }.toList()
        assert(src.walkTopDown().count { it.extension == "kt" } > 500) { "앱 코드를 못 찾았어요" }
        assertEquals("화면 글은 '~해요'로 해요(조건 16)", emptyList<String>(), found)
    }
}
