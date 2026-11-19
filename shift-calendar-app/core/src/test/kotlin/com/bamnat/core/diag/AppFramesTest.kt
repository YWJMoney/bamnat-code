package com.bamnat.core.diag

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVL-55] (검토 DATA-07·I12a-04) A stop's place, in a debug build (names) and in the release build (renamed lines). */
class AppFramesTest {
    private fun f(cls: String, m: String, line: Int) = StackTraceElement(cls, m, "SourceFile", line)

    @Test fun inADebugBuildTheFirstAppLineByNameAsBefore() {
        val frames = arrayOf(
            f("java.util.ArrayList", "get", 437),
            f("kotlin.collections.CollectionsKt", "first", 12),
            f("com.bamnat.app.ui.pay.PayViewModel", "save", 212),
            f("com.bamnat.app.ui.pay.PayScreen", "click", 40),
        )
        // The form 1.32~1.39 wrote, so a stop counted then is still the same stop.
        assertEquals("PayViewModel.save:212", AppFrames.where(frames))
    }

    @Test fun inTheReleaseBuildRenamedLinesAreOursWholeWithTheNext() {
        val frames = arrayOf(
            f("java.util.Objects", "requireNonNull", 207),
            f("android.view.View", "performClick", 7448),
            f("a.b.c", "a", 12),
            f("d.e", "b", 40),
            f("android.os.Handler", "dispatchMessage", 99),
        )
        assertEquals("a.b.c.a:12<d.e.b:40", AppFrames.where(frames))
        assertTrue(AppFrames.ours("a.b.c"))
        assertFalse(AppFrames.ours("android.view.View"))
        assertFalse(AppFrames.ours("java.util.Objects"))
        // The phone's lines are never ours; android's ui lines are still kept in a 화면 멈춤 note around ours.
        assertTrue(AppFrames.kept("android.view.View"))
        assertFalse(AppFrames.kept("java.lang.Thread"))
        assertTrue(AppFrames.kept("a.b.c"))
    }

    @Test fun librariesByNameAreNotOursInADebugBuild() {
        for (lib in listOf("androidx.compose.runtime.Recomposer", "kotlin.jvm.internal.Intrinsics", "kotlinx.coroutines.Job", "com.google.firebase.X"))
            assertFalse(lib, AppFrames.ours(lib))
        assertTrue(AppFrames.ours("com.bamnat.core.pay.PayCalc"))
        assertNull(AppFrames.where(arrayOf(f("java.lang.Thread", "run", 1))))
    }

    @Test fun writtenLinesReadTheSameWay() {
        assertEquals("com.bamnat.app.ui.pay.PayViewModel", AppFrames.classOf("  at com.bamnat.app.ui.pay.PayViewModel.save:212"))
        assertEquals("a.b.c", AppFrames.classOf("at a.b.c.a(SourceFile:12)"))
        assertEquals("android.os.Looper", AppFrames.classOf("at android.os.Looper.loop"))
        assertEquals("PayViewModel.save:212", AppFrames.shortLine("com.bamnat.app.ui.pay.PayViewModel.save:212"))
        assertEquals("a.b.c.a:12", AppFrames.shortLine("at a.b.c.a:12"))
    }
}
