package com.bamnat.app.nxt

import com.bamnat.app.WORK_JOB_IDS
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 [RVN-22] Every JobScheduler id the app's own jobs use is held to one owner: two jobs with one id replace each other
 * (PushCheckJob's 4410~4412 had met the sleep record's 4410 and the no-tap watch's 4411·4412, so a plaza check could cancel a
 * sleep offer). Every file that builds a JobInfo is read for its `const val … = 44nn` and `-> 44nn` ids; none repeats, and none
 * falls in WorkManager's range ([WORK_JOB_IDS]) or an added calendar's share range (4_410_000 and up).
 */
class JobIdsTest {
    private val main = File("src/main/java/com/bamnat/app")

    private fun owners(): List<Pair<Int, String>> = main.walkTopDown()
        .filter { it.isFile && it.extension == "kt" }
        .filter { it.readText().contains("JobInfo.Builder(") }
        .flatMap { f ->
            val text = f.readText()
            val consts = Regex("""const val \w+\s*(?::\s*Int)?\s*=\s*(44\d\d)\b""").findAll(text).map { it.groupValues[1].toInt() }
            val arms = Regex("""->\s*(44\d\d)\b""").findAll(text).map { it.groupValues[1].toInt() }
            (consts + arms).map { it to f.name }
        }.toList()

    @Test fun everyJobIdHasOneOwner() {
        val found = owners()
        assertTrue("too few ids found: $found", found.size >= 15)
        val twice = found.groupBy { it.first }.filterValues { it.size > 1 }
        assertTrue("ids used twice: $twice", twice.isEmpty())
    }

    @Test fun noJobIdMeetsWorkManagerOrTheShareRange() {
        val found = owners()
        assertTrue(found.toString(), found.none { it.first in WORK_JOB_IDS || it.first >= 4_410_000 })
        val shareBase = Regex("""SHARE_BASE = ([\d_]+)""").find(File(main, "company/OutboxJob.kt").readText())!!.groupValues[1].replace("_", "").toInt()
        assertTrue(shareBase > WORK_JOB_IDS.last)
    }
}
