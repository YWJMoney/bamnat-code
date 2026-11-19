package com.bamnat.core.stats

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVL-90] (검토 I12c-03, 처리방침 3.14 문장표 6) 앱이 멈춘 곳: only the app's own lines (five), a short value the
 * server can work out again, five places a day.
 */
class CrashMarksTest {
    @Test fun theValueIsTheServersToo() {
        // server/company-worker test/l200-crash-marks.test.mjs pins the same value (crashSig).
        assertEquals("261ed75a", CrashMarks.sig("crash", listOf("PayViewModel.save:212", "a.b.c.d:12")))
        assertTrue(CrashMarks.sig("anr", listOf("PayViewModel.save:212", "a.b.c.d:12")) != CrashMarks.sig("crash", listOf("PayViewModel.save:212", "a.b.c.d:12")))
    }

    @Test fun onlyOurLinesFiveAtMostRootCauseFirstAndNoMessage() {
        val root = IllegalStateException("010-1234-5678 is the message")
        root.stackTrace = arrayOf(
            StackTraceElement("java.util.ArrayList", "get", "ArrayList.java", 10),
            StackTraceElement("com.bamnat.app.ui.pay.PayViewModel", "save", "PayViewModel.kt", 212),
            StackTraceElement("androidx.compose.runtime.Composer", "x", "C.kt", 1),
        ) + (1..6).map { StackTraceElement("a.b.c", "d", "SourceFile", it) }
        val top = RuntimeException("wrapped", root)
        val at = CrashMarks.at(top)
        assertEquals(listOf("PayViewModel.save:212", "a.b.c.d:1", "a.b.c.d:2", "a.b.c.d:3", "a.b.c.d:4"), at)
        assertTrue(at.none { "010" in it || "message" in it })
    }

    @Test fun anAnrTraceGivesTheMainThreadsLinesOfOurs() {
        val trace = """
            "Signal Catcher" daemon prio=10
              at com.bamnat.app.Other.no(Other.kt:1)

            "main" prio=5 tid=1 Blocked
              at java.lang.Object.wait(Native Method)
              - waiting on <0x0abc> (a java.lang.Object)
              at com.bamnat.app.data.Backup.write(Backup.kt:88)
              at a.b.c(SourceFile:3)
              at android.os.Looper.loop(Looper.java:1)

            "worker" prio=5
              at com.bamnat.app.Elsewhere.run(Elsewhere.kt:9)
        """.trimIndent()
        assertEquals(listOf("Backup.write:88", "a.b.c:3"), CrashMarks.atTrace(trace.lineSequence()))
    }

    @Test fun samePlaceAddsUpADayKeepsItsFiveMostAndAWeek() {
        var marks = emptyList<CrashMarks.Mark>()
        repeat(3) { marks = CrashMarks.add(marks, 100, CrashMarks.CRASH, listOf("A.b:1")) }
        assertEquals(listOf(3), marks.map { it.count })
        (2..7).forEach { i -> marks = CrashMarks.add(marks, 100, CrashMarks.CRASH, listOf("A.b:$i")) }
        assertEquals(CrashMarks.PER_DAY, marks.size)
        assertEquals(3, marks.first().count)
        marks = CrashMarks.add(marks, 100, "oom", listOf("A.b:9"))
        marks = CrashMarks.add(marks, 100, CrashMarks.ANR, emptyList())
        assertEquals(CrashMarks.PER_DAY, marks.size)
        marks = CrashMarks.add(marks, 107, CrashMarks.ANR, listOf("A.b:1"))
        assertEquals(listOf(107L), marks.map { it.day }, "a week on, the old day is gone")
        assertEquals(listOf<CrashMarks.Mark>(), CrashMarks.due(marks, 107))
        assertEquals(marks, CrashMarks.decode(CrashMarks.encode(marks), 100))
        assertEquals(emptyList<CrashMarks.Mark>(), CrashMarks.decode("x\ty\n107\toom\t1\tA.b:1\n107\tanr\t0\tA.b:1", 0))
    }

    private fun assertEquals(expected: Any?, actual: Any?, message: String) = org.junit.Assert.assertEquals(message, expected, actual)
}
