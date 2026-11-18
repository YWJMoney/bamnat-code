package com.bamnat.app.report

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.components.AppSnack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * 2.0.0 [RVP-69] (검토 REST-11·U14-08) A PDF·사진 보내기 that fails says so in one way, with 다시 하기, instead of crashing the app or
 * saying nothing; and every button that shares a file goes through it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ReportShareTest {
    @Test fun aFailedFileSaysSoWithAgain() = runBlocking {
        var tries = 0
        val ok = ReportShare.orTell { tries++; throw java.io.IOException("ENOSPC") }
        assertFalse(ok)
        assertEquals(1, tries)
        // The queue counts time on the phone's clock since start; other lines from other tests may wait before this one.
        val now = android.os.SystemClock.elapsedRealtime()
        val line = generateSequence { AppSnack.queue.pickNext(now) }.firstOrNull { it.kind == AppSnack.Kind.FAIL }
        assertEquals("파일을 만들지 못했어요. 저장 공간을 확인해 주세요", line?.message)
        assertEquals("다시 하기", line?.action)
        assertEquals(AppSnack.Kind.FAIL, line?.kind)
    }

    @Test fun noAppToSendTo() {
        assertEquals("보낼 수 있는 앱을 찾지 못했어요", ReportShare.failWords(android.content.ActivityNotFoundException()))
    }

    @Test fun aGoodFileSaysNothing() = runBlocking {
        assertTrue(ReportShare.orTell { })
    }

    /** Every place that shares a report or a picture asks [ReportShare.orTell] (within 16 lines) or says its own failure (onFailure, a note, done(ok)). */
    @Test fun noShareButtonIsSilent() {
        val root = File("src/main/java/com/bamnat/app")
        val call = Regex("""(ReportShare\.(pdf|csv|zip)|LedgerExport\.share|QuoteImage\.share|StatementImage\.share)\(context""")
        val silent = root.walkTopDown().filter { it.extension == "kt" && it.name != "ReportShare.kt" }.flatMap { f ->
            val lines = f.readLines()
            lines.withIndex().filter { (_, l) -> call.containsMatchIn(l) }.mapNotNull { (i, l) ->
                // A share inside a long block (the 상담용 묶음 zip builds its files first) is told 16 lines above.
                val near = lines.subList(maxOf(0, i - 16), minOf(lines.size, i + 4)).joinToString("\n")
                val told = "orTell" in near || "onFailure" in near || ".isSuccess" in near || "make(\"" in near || "fun share" in l
                if (told) null else "${f.name}:${i + 1}"
            }
        }.toList()
        assertTrue("share with no word when it fails: $silent", silent.isEmpty())
    }
}
