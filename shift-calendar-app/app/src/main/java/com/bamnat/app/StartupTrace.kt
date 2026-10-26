package com.bamnat.app

/**
 * [PERF-01] When each step of a cold start first happened, in milliseconds from the app's start. Kept in
 * memory only: the startup budget test reads it, and on a phone the same steps end in Android's "Fully drawn"
 * line (the calendar reports itself drawn once its month is there).
 *
 * [PERF-09][PERF-15] 1.28 AO: also [Step.CONTAINER_WAIT], the moment the screen had the container it waited for, and
 * one [Sample] per opening of the app (cold: a new process; warm: the process was there, the screen is new) handed
 * to [com.bamnat.app.diag.StartupStats] once the calendar's month is drawn. What leaves the phone is decided there.
 */
object StartupTrace {
    enum class Step { APP, CONTAINER, ACTIVITY, CONTAINER_WAIT, CALENDAR }

    /** [PERF-15] How the app was opened: [COLD] started its process, [WARM] found it running. */
    enum class Kind(val key: String) { COLD("cold"), WARM("warm") }

    /**
     * [PERF-15] One opening, in milliseconds: [containerWait] the screen waited for the container (0 when it was
     * ready), [activity] from the start to the screen's creation (0 for a warm one: that is where it starts),
     * [firstMonth] from the start to the calendar's month.
     */
    data class Sample(val kind: Kind, val containerWait: Long, val activity: Long, val firstMonth: Long)

    @Volatile private var origin = System.nanoTime()
    private val at = java.util.concurrent.ConcurrentHashMap<Step, Long>()

    /** [PERF-15] The opening being timed: its kind, its origin, and when the screen got the container. */
    private class Opening(val kind: Kind, val origin: Long, val activity: Long) {
        @Volatile var waited: Long = 0
    }
    @Volatile private var opening: Opening? = null
    @Volatile private var activities = 0

    /** Called first thing in [BamnatApp.onCreate]: a new process, so a new start. */
    fun begin() {
        origin = System.nanoTime()
        at.clear()
        opening = null
        activities = 0
    }

    /** Notes [step] the first time it happens; later calls (a fold, a second window) leave it. */
    fun mark(step: Step) {
        at.putIfAbsent(step, (System.nanoTime() - origin) / 1_000_000)
    }

    /** Milliseconds from the start to [step], or null when it has not happened yet. */
    fun millis(step: Step): Long? = at[step]

    /**
     * [PERF-15] The main screen was made. A screen made again for a fold or a turn ([restoring]) is no opening. The
     * first screen of a process that started for it (within [COLD_WITHIN_MILLIS]) is a cold start; any other a warm one.
     */
    fun activityCreated(restoring: Boolean) {
        val now = System.nanoTime()
        mark(Step.ACTIVITY)
        val first = activities++ == 0
        if (restoring) return
        val sinceStart = (now - origin) / 1_000_000
        opening = if (first && sinceStart <= COLD_WITHIN_MILLIS) Opening(Kind.COLD, origin, sinceStart) else Opening(Kind.WARM, now, 0)
    }

    /** [PERF-09] The screen has the container now ([waitedNanos] it waited for it, 0 when it was ready). */
    fun containerReached(waitedNanos: Long) {
        mark(Step.CONTAINER_WAIT)
        opening?.waited = waitedNanos / 1_000_000
    }

    /** [PERF-15] The screen went away before any month showed (opened on another tab, a lock, back pressed): no sample. */
    fun activityStopped() {
        opening = null
    }

    /**
     * [PERF-01] The calendar's month is on screen. [PERF-15] Returns the opening's [Sample] the first time after an
     * opening, null otherwise (a tab switch back to the calendar, a second window).
     */
    fun monthDrawn(): Sample? {
        mark(Step.CALENDAR)
        val o = opening ?: return null
        opening = null
        val month = (System.nanoTime() - o.origin) / 1_000_000
        return Sample(o.kind, o.waited, o.activity, month)
    }

    /** [PERF-15] A process started for the screen opens it within seconds (slow phones included); later it started for an alarm or a widget. */
    const val COLD_WITHIN_MILLIS = 10_000L
}
