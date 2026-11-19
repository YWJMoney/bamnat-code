package com.bamnat.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVP-23][RVP-37] 뿌리 고침 4 (검토 UIX-44·I13ba-02·UIX-62): the app's one line, as a table. 되돌리기 first and the newest
 * of them before an older one, then a failure, then the rest; a waiting 되돌리기 stops the line on show, which comes back after;
 * the same words wait once; a line that waited too long goes.
 */
class SnackQueueTest {
    private var order = 0L
    private fun line(msg: String, kind: AppSnack.Kind = AppSnack.Kind.INFO, action: String? = null, at: Long = 0, tag: String? = null) =
        AppSnack.Line(msg, action, kind, AppSnack.millisOf(kind, action, long = false), tag, at, ++order)

    @Test fun undoFirstTheNewestUndoBeforeAnOlderThenAFailureThenTheRest() {
        val q = SnackQueue()
        val info = q.add(line("캘린더에 새 일정 3개가 있어요", action = "가져오기"))
        val old = q.add(line("메모를 휴지통으로 옮겼어요", AppSnack.Kind.UNDO, "되돌리기"))
        val fail = q.add(line("되돌리지 못했어요", AppSnack.Kind.FAIL))
        val new = q.add(line("기록을 지우고 일근으로 새로 시작해요", AppSnack.Kind.UNDO, "되돌리기"))
        val later = q.add(line("‘가계부’ 껐어요", action = "바로 가기"))
        for (expected in listOf(new, old, fail, info, later)) {
            val next = q.pickNext(0)
            assertSame(expected.message, expected, next)
            q.finish(next!!, false)
        }
        assertNull(q.pickNext(0))
    }

    @Test fun aWaitingUndoStopsTheLineOnShowAndThatLineComesBackAfter() {
        val q = SnackQueue()
        val solved = q.add(line("부탁하신 꾸미기가 풀렸어요", action = "보러 가기"))
        assertSame(solved, q.pickNext(0))
        assertFalse(q.shouldStop(solved))
        // The case of UIX-44: 기록 지우기 while a long line shows. The 되돌리기 no longer waits out its 8 s behind it.
        val undo = q.add(line("기록을 지웠어요", AppSnack.Kind.UNDO, "되돌리기"))
        assertTrue(q.shouldStop(solved))
        q.putBack(solved, interrupted = true)
        assertSame(undo, q.pickNext(1_000))
        q.finish(undo, true)
        assertTrue(undo.result.isCompleted)
        assertSame("the line pushed aside comes back", solved, q.pickNext(9_000))
    }

    @Test fun anOrdinaryLineNeverStopsAnother() {
        val q = SnackQueue()
        val a = q.add(line("저장했어요"))
        assertSame(a, q.pickNext(0))
        q.add(line("보냈어요"))
        q.add(line("못 보냈어요", AppSnack.Kind.FAIL))
        assertTrue("a failure goes before the rest, so it stops a plain line", q.shouldStop(a))
        val b = SnackQueue()
        val x = b.add(line("저장했어요"))
        assertSame(x, b.pickNext(0))
        b.add(line("보냈어요"))
        assertFalse(b.shouldStop(x))
    }

    @Test fun theSameWordsWaitOnceButEachUndoIsItsOwn() {
        val q = SnackQueue()
        val a = q.add(line("캘린더에 새 일정 3개가 있어요", action = "가져오기"))
        assertSame(a, q.add(line("캘린더에 새 일정 3개가 있어요", action = "가져오기")))
        // Two memos taken off one after the other: both can be taken back.
        val m1 = q.add(line("메모를 휴지통으로 옮겼어요", AppSnack.Kind.UNDO, "되돌리기"))
        val m2 = q.add(line("메모를 휴지통으로 옮겼어요", AppSnack.Kind.UNDO, "되돌리기"))
        assertTrue(m1 !== m2)
        // A tag makes one of them (한 번 더 누르면 밤낮이 닫혀요, pressed twice).
        val e1 = q.add(line("한 번 더 누르면 밤낮이 닫혀요", tag = "exit"))
        assertSame(e1, q.add(line("한 번 더 누르면 밤낮이 닫혀요", tag = "exit")))
    }

    @Test fun aLineThatWaitedTooLongGoes() {
        val q = SnackQueue()
        val info = q.add(line("저장했어요", at = 0))
        val undo = q.add(line("지웠어요", AppSnack.Kind.UNDO, "되돌리기", at = 0))
        assertNull("nothing could show them for a minute", q.pickNext(AppSnack.STALE_MILLIS + 1))
        assertTrue(info.result.isCompleted && undo.result.isCompleted)
        assertFalse(undo.result.getCompleted())
        val fresh = q.add(line("지웠어요", AppSnack.Kind.UNDO, "되돌리기", at = 100_000))
        assertNull("a 되돌리기 goes sooner", q.pickNext(100_000 + AppSnack.STALE_UNDO_MILLIS + 1))
        assertFalse(fresh.result.getCompleted())
    }

    @Test fun aLinePushedAsideThreeTimesGoesAndAWithdrawnOneStops() {
        val q = SnackQueue()
        val a = q.add(line("저장했어요"))
        repeat(3) { assertSame(a, q.pickNext(0)); q.putBack(a, interrupted = true) }
        assertTrue(a.result.isCompleted)
        assertNull(q.pickNext(0))
        val b = q.add(line("보냈어요"))
        assertSame(b, q.pickNext(0))
        q.withdraw(b)
        assertTrue(q.shouldStop(b))
        assertFalse(b.result.getCompleted())
        // A host that went (a sheet closed) is not a push: it shows again on the next host without counting.
        val c = q.add(line("넣었어요"))
        repeat(5) { assertSame(c, q.pickNext(0)); q.putBack(c, interrupted = false) }
        assertFalse(c.result.isCompleted)
    }

    @Test fun timeCountsByKind() {
        assertEquals(8_000L, AppSnack.millisOf(AppSnack.Kind.UNDO, "되돌리기", long = false))
        assertEquals(8_000L, AppSnack.millisOf(AppSnack.Kind.INFO, "가져오기", long = false))
        assertEquals(6_000L, AppSnack.millisOf(AppSnack.Kind.INFO, null, long = true))
        assertEquals(4_000L, AppSnack.millisOf(AppSnack.Kind.INFO, null, long = false))
    }
}
