package com.bamnat.app.rules

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 2.0.0 [RVR-18] (검토 TEST-19) Every place in the app that sets a system alarm, with the way it is promised to ring. [AlarmMatrixTest]
 * sets the ones that ring (the shift's, a memo's, an investor's, the smart wake's window) on four Android versions; this table keeps the
 * other twenty from changing their way, or a new one from coming in, without anyone deciding it:
 * - CLOCK: an alarm clock (setAlarmClock, or AlarmScheduler.setAlarm), on time even in Doze, the alarm icon in the bar;
 * - EXACT: setExactAndAllowWhileIdle, on time without the icon;
 * - ROUGH: setAndAllowWhileIdle (or a plain set), within some minutes, for notices that need not be to the minute.
 * CLOCK and EXACT need 알람 및 리마인더 from Android 12: each of those files falls back when it is refused.
 */
class AlarmWaysRulesTest {
    private enum class Way { CLOCK, EXACT, ROUGH }

    private val promised = mapOf(
        // The ones that ring.
        "alarm/AlarmScheduler.kt" to Way.CLOCK,
        "alarm/AlarmService.kt" to Way.CLOCK, // a snooze from the ringing screen
        "alarm/BootAlarms.kt" to Way.CLOCK, // the next ring and snooze put back after a restart, before unlock
        "memo/MemoScheduleAlarm.kt" to Way.CLOCK, // a memo's schedule that rings (a notice-only one is EXACT)
        "invest/InvestNotices.kt" to Way.CLOCK, // an investor's alarm that rings (a notice-only one is EXACT)
        // On the minute, without the alarm icon.
        "alarm/AlarmSoon.kt" to Way.EXACT, // 곧 울려요 before the alarm
        "alarm/WakeCheck.kt" to Way.EXACT, // 일어났나요?
        "logic/LogicRunner.kt" to Way.EXACT, // a 로직's timed rule
        "money/JobReminderScheduler.kt" to Way.EXACT,
        "sleep/DayNoticeScheduler.kt" to Way.EXACT,
        "sleep/SmartWake.kt" to Way.EXACT, // the smart wake's window opens
        // Notices for which some minutes do not matter.
        "alarm/AlarmGuard.kt" to Way.ROUGH,
        "alarm/LeaveTimeNotice.kt" to Way.ROUGH,
        "calendar/DateNoticeAlarm.kt" to Way.ROUGH,
        "commute/CommuteRecorder.kt" to Way.ROUGH,
        "invest/InvestMonthNotice.kt" to Way.ROUGH,
        "money/DayCloseNotice.kt" to Way.ROUGH,
        "money/ShiftPreviewNotice.kt" to Way.ROUGH,
        "pay/LeaveReminder.kt" to Way.ROUGH,
        "share/ShareExpiryReminder.kt" to Way.ROUGH,
        "sleep/QuietHold.kt" to Way.ROUGH,
        "tier/TrialNotice.kt" to Way.ROUGH,
        "widget/WidgetRefresher.kt" to Way.ROUGH,
    )

    private val root = File("src/main/java/com/bamnat/app")
    private val clock = Regex("""\.setAlarmClock\(|\bAlarmScheduler\.setAlarm\(""")
    private val exact = Regex("""\.setExact(?:AndAllowWhileIdle)?\(""")
    private val rough = Regex("""\b(?:am|alarmManager)\.(?:setAndAllowWhileIdle|setInexactRepeating|setRepeating|setWindow|set)\(""")

    /** The code lines of each file that sets a system alarm, comments left out. */
    private fun setters(): Map<String, List<String>> = root.walkTopDown().filter { it.isFile && it.extension == "kt" }.mapNotNull { f ->
        val code = f.readLines().filter { val t = it.trimStart(); !t.startsWith("//") && !t.startsWith("*") && !t.startsWith("/*") }
        code.takeIf { lines -> lines.any { clock.containsMatchIn(it) || exact.containsMatchIn(it) || rough.containsMatchIn(it) } }
            ?.let { f.relativeTo(root).invariantSeparatorsPath to it }
    }.toMap()

    @Test fun everyPlaceThatSetsASystemAlarmKeepsItsPromisedWay() {
        val found = setters().mapValues { (_, lines) ->
            when {
                lines.any { clock.containsMatchIn(it) } -> Way.CLOCK
                lines.any { exact.containsMatchIn(it) } -> Way.EXACT
                else -> Way.ROUGH
            }
        }
        assertEquals(
            "A new place that sets a system alarm, or one whose way changed: say here how it is promised to ring",
            promised.toSortedMap(), found.toSortedMap(),
        )
    }

    @Test fun anExactAlarmFallsBackWhenAlarmsAndRemindersIsRefused() {
        val missing = setters().filter { (_, lines) ->
            val direct = lines.any { it.contains(".setAlarmClock(") || exact.containsMatchIn(it) }
            direct && lines.none { "canScheduleExactAlarms" in it }
        }.keys
        assertEquals("these set an exact alarm without asking canScheduleExactAlarms first (a SecurityException from Android 12)", emptySet<String>(), missing)
    }
}
