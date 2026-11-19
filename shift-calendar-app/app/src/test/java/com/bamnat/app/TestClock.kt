package com.bamnat.app

import com.bamnat.core.time.Today
import java.time.Clock
import java.time.Duration

/**
 * 2.0.0 [RVR-44] (총괄 HQ-123): a test whose result hangs on the hour it runs (an alarm 'today at 21:00', '늦음', the day
 * turning at midnight) sets the hour with [at] and puts it back with [Today.reset] in its @After.
 *
 * The clock keeps ticking from that hour of today (today as [Today] says it, so the date-watch run that moves the days still
 * moves this test): code that waits a little or measures how long something took sees time pass as before, which a
 * stopped clock ([Today.fix]) would not give it. The tests that read the real clock and ran after 21:00 UTC (06~09시 KST in CI)
 * failed: TaskWidgetTest's '21:00 늦음' (HQ-123).
 */
object TestClock {
    /** From now on [Today] reads today at [hour]:[minute], ticking. */
    fun at(hour: Int, minute: Int = 0) {
        val base = Today.clock
        val now = Today.dateTime()
        Today.use(Clock.offset(base, Duration.between(now, now.toLocalDate().atTime(hour, minute))))
    }
}
