package com.bamnat.app.rules

import android.view.Choreographer
import androidx.compose.ui.platform.AndroidUiDispatcher
import kotlin.coroutines.ContinuationInterceptor

/**
 * [QA-09] One fix for the tests that failed only in a full run (StartupBudgetTest's month that never came, screenshots
 * that could not find what was plainly coming): Compose's main-thread dispatcher is made once per JVM and outlives a
 * test, but Robolectric empties the main thread's queue between tests. A test that ended with a Compose dispatch
 * waiting (an animation, a recomposition) left the dispatcher sure one was already posted, so it never posted again
 * and every later screen in that JVM stopped after its first frame. Call [now] first thing in a test that shows
 * Compose: what was dropped is posted again; with nothing dropped it does nothing.
 */
object ComposeMainReset {
    fun now() {
        val ui = AndroidUiDispatcher.CurrentThread[ContinuationInterceptor] as? AndroidUiDispatcher ?: return
        val type = AndroidUiDispatcher::class.java
        fun flag(name: String) = type.getDeclaredField(name).apply { isAccessible = true }
        val callback = flag("dispatchCallback").get(ui)
        val handler = flag("handler").get(ui) as android.os.Handler
        // Both read under the dispatcher's own lock; the test thread is its main thread, so nothing else runs now.
        if (flag("scheduledTrampolineDispatch").getBoolean(ui)) handler.post(callback as Runnable)
        if (flag("scheduledFrameDispatch").getBoolean(ui)) ui.choreographer.postFrameCallback(callback as Choreographer.FrameCallback)
    }

    /** [now] before everything else of a test, ahead of a Compose rule: `@get:Rule(order = Int.MIN_VALUE)`. */
    fun rule(): org.junit.rules.TestRule = org.junit.rules.TestRule { base, _ ->
        object : org.junit.runners.model.Statement() {
            override fun evaluate() {
                now()
                base.evaluate()
            }
        }
    }
}
