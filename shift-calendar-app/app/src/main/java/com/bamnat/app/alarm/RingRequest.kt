package com.bamnat.app.alarm

import android.content.Intent
import com.bamnat.core.alarm.PlannedAlarm
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import com.bamnat.core.time.Today

/** Everything the ringing screen and service need, carried through intents. */
data class RingRequest(
    val shiftName: String,
    val shiftDate: LocalDate,
    val shiftStart: LocalTime,
    val offsetMinutes: Int,
    /** Shift-specific sound; null falls back to the default from settings. */
    val soundUri: String?,
    val triggerAtMillis: Long,
    val isSnooze: Boolean = false,
    /** Set by hand for that date from the calendar. */
    val custom: Boolean = false,
    /** [SCHED-01] A memo's schedule rings under the memo's own words instead of a shift's name. */
    val label: String? = null,
    /** [PROV-06] The shift's type, for its own mission; 0 for a day without work or a memo schedule. */
    val shiftTypeId: Long = 0,
    /** [PROV-07] Rung again because 일어났나요? got no answer; it asks no more after this one. */
    val recheck: Boolean = false,
    /** [EASE-20] The shift's colour (ARGB) for the ringing screen; 0 for none. */
    val shiftColor: Long = 0,
    /** [R200-01] The added calendar the shift is in (its own missions); null for the main calendar. */
    val calendarId: String? = null,
    /** 2.0.0 [RVI-17] The wake of a 낮잠 ([com.bamnat.app.sleep.NapWake]), put in with the day's own alarms. */
    val nap: Boolean = false,
) {
    /** "주간 근무 알람", "알람" for a hand-set alarm on a day without work, or a memo schedule's words. */
    val title: String get() = title(null)

    /** 2.0.0 [RVH-41] 검토 T30-99: [title] in [style]'s words — a field worker's, a shop's or an investor's "오후 알람". [RVI-17] 낮잠 is its own. */
    fun title(style: com.bamnat.core.work.WorkStyle?): String =
        if (recheck) "일어났나요?" else label ?: if (nap) "낮잠 알람" else if (shiftName.isBlank()) "알람" else com.bamnat.core.alarm.AlarmWords.alarm(shiftName, style)

    fun putInto(intent: Intent): Intent = intent
        .putExtra(X_NAME, shiftName)
        .putExtra(X_DATE, shiftDate.toEpochDay())
        .putExtra(X_START, shiftStart.toSecondOfDay())
        .putExtra(X_OFFSET, offsetMinutes)
        .putExtra(X_SOUND, soundUri)
        .putExtra(X_TRIGGER, triggerAtMillis)
        .putExtra(X_SNOOZE, isSnooze)
        .putExtra(X_CUSTOM, custom)
        .putExtra(X_LABEL, label)
        .putExtra(X_TYPE, shiftTypeId)
        .putExtra(X_RECHECK, recheck)
        .putExtra(X_COLOR, shiftColor)
        .putExtra(X_CAL, calendarId)
        .putExtra(X_NAP, nap)

    fun encode(): String = JSONObject()
        .put(X_NAME, shiftName)
        .put(X_DATE, shiftDate.toEpochDay())
        .put(X_START, shiftStart.toSecondOfDay())
        .put(X_OFFSET, offsetMinutes)
        .put(X_SOUND, soundUri ?: JSONObject.NULL)
        .put(X_TRIGGER, triggerAtMillis)
        .put(X_SNOOZE, isSnooze)
        .put(X_CUSTOM, custom)
        .put(X_LABEL, label ?: JSONObject.NULL)
        .put(X_TYPE, shiftTypeId)
        .put(X_RECHECK, recheck)
        .put(X_COLOR, shiftColor)
        .put(X_CAL, calendarId ?: JSONObject.NULL)
        .put(X_NAP, nap)
        .toString()

    companion object {
        private const val X_NAME = "ring_name"
        private const val X_DATE = "ring_date"
        private const val X_START = "ring_start"
        private const val X_OFFSET = "ring_offset"
        private const val X_SOUND = "ring_sound"
        private const val X_TRIGGER = "ring_trigger"
        private const val X_SNOOZE = "ring_snooze"
        private const val X_CUSTOM = "ring_custom"
        private const val X_LABEL = "ring_label"
        private const val X_TYPE = "ring_type"
        private const val X_RECHECK = "ring_recheck"
        private const val X_COLOR = "ring_color"
        private const val X_CAL = "ring_calendar"
        private const val X_NAP = "ring_nap"

        fun from(plan: PlannedAlarm, triggerAtMillis: Long, nap: Boolean = false) = RingRequest(
            shiftName = plan.shiftName,
            shiftDate = plan.shiftDate,
            shiftStart = plan.shiftStart,
            offsetMinutes = plan.offsetMinutes,
            soundUri = plan.soundUri,
            triggerAtMillis = triggerAtMillis,
            custom = plan.custom,
            shiftTypeId = plan.shiftTypeId,
            shiftColor = plan.colorArgb,
            calendarId = plan.calendarId,
            nap = nap,
        )

        fun fromIntent(intent: Intent?): RingRequest? {
            if (intent == null || !intent.hasExtra(X_NAME)) return null
            return RingRequest(
                shiftName = intent.getStringExtra(X_NAME) ?: return null,
                shiftDate = LocalDate.ofEpochDay(intent.getLongExtra(X_DATE, 0L)),
                shiftStart = LocalTime.ofSecondOfDay(intent.getIntExtra(X_START, 0).toLong().coerceIn(0, 86_399)),
                offsetMinutes = intent.getIntExtra(X_OFFSET, 0),
                soundUri = intent.getStringExtra(X_SOUND),
                triggerAtMillis = intent.getLongExtra(X_TRIGGER, Today.millis()),
                isSnooze = intent.getBooleanExtra(X_SNOOZE, false),
                custom = intent.getBooleanExtra(X_CUSTOM, false),
                label = intent.getStringExtra(X_LABEL),
                shiftTypeId = intent.getLongExtra(X_TYPE, 0L),
                recheck = intent.getBooleanExtra(X_RECHECK, false),
                shiftColor = intent.getLongExtra(X_COLOR, 0L),
                calendarId = intent.getStringExtra(X_CAL),
                nap = intent.getBooleanExtra(X_NAP, false),
            )
        }

        fun decode(raw: String?): RingRequest? {
            if (raw.isNullOrEmpty()) return null
            return runCatching {
                val o = JSONObject(raw)
                RingRequest(
                    shiftName = o.getString(X_NAME),
                    shiftDate = LocalDate.ofEpochDay(o.getLong(X_DATE)),
                    shiftStart = LocalTime.ofSecondOfDay(o.getInt(X_START).toLong()),
                    offsetMinutes = o.getInt(X_OFFSET),
                    soundUri = if (o.isNull(X_SOUND)) null else o.getString(X_SOUND),
                    triggerAtMillis = o.getLong(X_TRIGGER),
                    isSnooze = o.optBoolean(X_SNOOZE, false),
                    custom = o.optBoolean(X_CUSTOM, false),
                    label = if (o.isNull(X_LABEL)) null else o.optString(X_LABEL),
                    shiftTypeId = o.optLong(X_TYPE, 0L),
                    recheck = o.optBoolean(X_RECHECK, false),
                    shiftColor = o.optLong(X_COLOR, 0L),
                    calendarId = if (o.isNull(X_CAL)) null else o.optString(X_CAL).ifEmpty { null },
                    nap = o.optBoolean(X_NAP, false),
                )
            }.getOrNull()
        }
    }
}
