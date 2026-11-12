package com.bamnat.app.rules

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.app.ui.components.Busy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVG-37] HQ-93: what one test leaves on the app's screen-wide objects does not reach the next. LinkOwnerTest's press on the summary
 * failed in full runs because a line queued by an earlier test showed over its button; [ComposeMainReset.now] empties the queue first.
 */
@RunWith(AndroidJUnit4::class)
class ComposeMainResetTest {
    @Test fun aLineLeftByAnEarlierTestIsGone() {
        AppSnack.show("앞 시험이 남긴 줄")
        assertTrue(AppSnack.queue.hasWaiting())
        ComposeMainReset.now()
        assertFalse("the next test's screen starts with no line over it", AppSnack.queue.hasWaiting())
        assertFalse("nor a sheet counted open", Busy.now)
    }
}
