package com.bamnat.core.diag

import com.bamnat.core.diag.StopTrail.Kind
import com.bamnat.core.diag.StopTrail.Line
import com.bamnat.core.ops.InquiryTopics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

/** 1.32.1 [STAB-10][STAB-14] 멈춤 관찰 기록: only codes and numbers, kept short, read back as a person's words. */
class StopTrailTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test fun aLineGoesAndComesBack() {
        val l = Line(now, Kind.NO_DRAW, "ms=3200 main=free focus=1 at=MainActivity.onStart:172")
        assertEquals(l, StopTrail.decode(StopTrail.encode(l)))
    }

    @Test fun typedWordsNeverStayOnlyTheirLength() {
        assertEquals("text5", StopTrail.clean("월급 얼마"))
        assertEquals("text3", StopTrail.decode("$now\tfront\t홍길동")!!.detail)
        assertNull(StopTrail.decode("garbage"))
        assertNull(StopTrail.decode("$now\tnope\t"))
    }

    @Test fun onlyThreeDaysAndTheNewestLinesAreKept() {
        val old = Line(now - 4 * day, Kind.FRONT, "")
        val many = (0 until 400).map { Line(now - it * 1000L, Kind.FOCUS, "1") }
        val kept = StopTrail.kept(many + old, now)
        assertEquals(StopTrail.KEEP_LINES, kept.size)
        assertFalse(old in kept)
        assertEquals(now, kept.last().atMillis)
    }

    @Test fun anEndWhileStuckIsFound() {
        val lines = listOf(
            Line(now, Kind.UNLOCKED, ""), Line(now + 1, Kind.FRONT, ""), Line(now + 4000, Kind.NO_DRAW, "ms=3000 main=free"),
            Line(now + 60_000, Kind.START, "last=swipe"),
            Line(now + 70_000, Kind.STALL, "at=X.y:1"), Line(now + 73_000, Kind.STALL_END, "ms=3100"),
            Line(now + 90_000, Kind.START, "last=user"),
        )
        assertEquals(listOf(lines[2]), StopTrail.stuckEnds(lines))
        assertEquals(now + 70_000, StopTrail.lastStuckAt(lines))
        val summary = StopTrail.summary(lines).first()
        assertTrue(summary, summary.contains("화면 멈춤 1번 (가장 길게 3.1초)"))
        assertTrue(summary, summary.contains("멈춘 채 끝남 1번"))
    }

    @Test fun theFullestHeapIsSaid() {
        val lines = listOf(Line(now, Kind.MEMORY, StopTrail.memory(230L shl 20, 256L shl 20, 80L shl 20)), Line(now + 1, Kind.MEMORY, "heap=100/256 native=80"))
        assertEquals(89, StopTrail.heapPercent(lines[0].detail))
        assertTrue(StopTrail.summary(lines).last().contains("heap=230/256 (89%)"))
    }

    @Test fun howItEndedIsInWords() {
        val text = StopTrail.readable(Line(now, Kind.START, "last=swipe lastmem=412"), zone)
        assertTrue(text, text.endsWith("앱 시작 지난번 끝:최근 앱에서 밀어 닫음 lastmem=412"))
    }

    @Test fun aStopIsNotAnAlarm() {
        assertEquals("app", InquiryTopics.of("[앱이 갑자기 닫힘] 렉걸림"))
        assertTrue(StopTrail.aboutStop("앱이 멈췄어요", ""))
        assertTrue(StopTrail.aboutStop(null, "[앱이 갑자기 닫힘] 렉걸림"))
        assertTrue(StopTrail.aboutStop(null, "잠금 풀면 화면이 굳어요"))
        assertFalse(StopTrail.aboutStop(null, "알람이 울리다 멈췄어요"))
        assertFalse(StopTrail.aboutStop(null, "월급 계산이 이상해요"))
    }
}
