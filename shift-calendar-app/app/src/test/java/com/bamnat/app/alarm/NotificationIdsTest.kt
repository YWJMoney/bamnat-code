package com.bamnat.app.alarm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVI-54] (검토 ALM-27·ALM-05) Every notification number in the 7000s is one notice's: two notices with the same number take
 * each other's place (7314 was both 잠 자동 잡기 and 잠금 화면 오늘 근무). Read from the code, so a new notice with a number already
 * taken fails here before it reaches a phone.
 */
class NotificationIdsTest {
    /** Numbers two places share on purpose, and why. */
    private val shared = mapOf(
        // FxOutside's '10분 뒤 다시' takes back the rule's own notice (same number, its tag tells which).
        7601 to setOf("LogicRunner.kt", "FxOutside.kt"),
    )

    @Test fun eachNoticeHasItsOwnNumber() {
        val root = File("src/main/java")
        assertTrue("run from the app module", root.isDirectory)
        val id = Regex("""const val (\w*ID\w*) = (7\d{3})\b""")
        val found = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.flatMap { f ->
            id.findAll(f.readText()).map { m -> Triple(m.groupValues[2].toInt(), f.name, m.groupValues[1]) }
        }.toList()
        assertTrue("the notices are found (${found.size})", found.size >= 40)
        val twice = found.groupBy { it.first }.filterValues { it.size > 1 }
            .filter { (n, at) -> shared[n] != at.map { it.second }.toSet() }
        assertEquals("numbers used by more than one notice: ${twice.mapValues { (_, at) -> at.map { "${it.second} ${it.third}" } }}",
            emptyMap<Int, Any>(), twice)
    }
}
