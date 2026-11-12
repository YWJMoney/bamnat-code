package com.bamnat.app.rules

import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * [QA-08] One clock (결정 D66): the main code of the phone app, core, the watch and the database asks core's Today
 * what day and time it is, never `LocalDate.now()` or `System.currentTimeMillis()` itself, so a screenshot test
 * that fixes the clock draws the same screen on any day. A line that needs the real clock (a new item's id) says
 * so with a `// real clock:` comment.
 */
/** [RVR-15] How many direct clock reads the tests may still have; it only goes down (2.0.0 [RVR-24]: 218 → 147, the tests the 2027-01-01 run broke read Today). */
private const val TEST_CLOCK_CAP = 147

class ClockRulesTest {
    private val roots = listOf("src/main", "../core/src/main", "../wear/src/main", "../database/src/main").map(::File)
    private val direct = Regex(
        """\b(?:LocalDate|LocalDateTime|LocalTime|YearMonth|ZonedDateTime|OffsetDateTime|Instant)\.now\(|""" +
            """System(?:\.|::)currentTimeMillis\b|ZoneId\.systemDefault\(\)|Clock\.system(?:DefaultZone|UTC)\(|""" +
            """\b(?:LocalDate|LocalDateTime|LocalTime|YearMonth|Instant)::now\b""",
    )

    @After fun back() = Today.reset()

    @Test fun noMainCodeReadsTheClockItself() {
        val found = roots.flatMap { root ->
            assertTrue("$root is missing", root.isDirectory)
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "Today.kt" }.flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) -> direct.containsMatchIn(line) && "// real clock:" !in line && !line.trimStart().startsWith("*") }
                    .map { (i, line) -> "${f.path}:${i + 1}: ${line.trim()}" }
            }.toList()
        }
        assertEquals("read the clock through com.bamnat.core.time.Today", emptyList<String>(), found)
    }

    /**
     * 2.0.0 [RVR-15] (검토 I60a-02·TEST-07) The tests' own direct reads of the clock: 221 lines when this rule came in (2026-11-12),
     * nearly all in the app's tests. They may only go down: a new test reads Today (fixed with Today.fix/use) or says why it needs
     * the real clock with `// real clock:` (a timeout, a file stamp). When you take some out, lower [TEST_CLOCK_CAP] with them.
     */
    @Test fun testsDoNotReadTheClockMoreThanBefore() {
        val tests = listOf("src/test", "../core/src/test", "../wear/src/test", "../database/src/test").map(::File).filter { it.isDirectory }
        val withYear = Regex(direct.pattern + """|\bYear\.now\(""")
        val found = tests.flatMap { root ->
            root.walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ClockRulesTest.kt" }.flatMap { f ->
                f.readLines().withIndex()
                    .filter { (_, line) -> withYear.containsMatchIn(line) && "// real clock:" !in line && !line.trimStart().startsWith("*") }
                    .map { (i, _) -> "${f.path}:${i + 1}" }
            }.toList()
        }
        assertTrue(
            "tests read the clock directly in ${found.size} lines (at most $TEST_CLOCK_CAP): use Today, or `// real clock:` with why.\n" +
                found.takeLast(5).joinToString("\n"),
            found.size <= TEST_CLOCK_CAP,
        )
    }

    @Test fun aFixedClockStaysPutAndResetGoesBackToTheSystem() {
        val seoul = ZoneId.of("Asia/Seoul")
        Today.fix(ZonedDateTime.of(2026, 10, 14, 10, 30, 0, 0, seoul))
        assertTrue(Today.isFixed)
        assertEquals(LocalDate.of(2026, 10, 14), Today.date())
        assertEquals(LocalTime.of(10, 30), Today.time())
        assertEquals(seoul, Today.zone())
        assertEquals(Today.millis(), Today.millis())
        // 10:30 in Seoul is still the 14th in UTC, and already the 13th's evening in Los Angeles.
        assertEquals(LocalDate.of(2026, 10, 13), Today.date(ZoneId.of("America/Los_Angeles")))
        Today.reset()
        assertFalse(Today.isFixed)
        assertEquals(ZoneId.systemDefault(), Today.zone())
    }
}
