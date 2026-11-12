package com.bamnat.core.diag

/**
 * One line of the on-device event log [MEAS-03, MEAS-04].
 * [detail] is short free text such as "08:00 D" or "exact=false"; never memo text or names.
 */
data class DiagEvent(val atMillis: Long, val name: String, val detail: String = "")

/** Event names. Kept in one place so the diagnostics screen and later analytics agree. */
object DiagEvents {
    const val APP_OPEN = "app_open"
    const val ALARM_SCHEDULED = "alarm_scheduled"
    const val ALARM_CLEARED = "alarm_cleared"
    const val ALARM_FIRED = "alarm_fired"
    const val ALARM_LATE_SKIPPED = "alarm_late_skipped"
    const val ALARM_FALLBACK = "alarm_fallback"
    const val ALARM_DISMISSED = "alarm_dismissed"
    const val ALARM_SNOOZED = "alarm_snoozed"
    /** [REL-13] The self-check found the system's alarm missing or different and registered it again. */
    const val ALARM_REPAIRED = "alarm_repaired"
    /** [REL-13] The one-minute test alarm from 알람 점검 was set, and when it rang (with its delay). */
    const val ALARM_TEST_SET = "alarm_test_set"
    const val ALARM_TEST_FIRED = "alarm_test_fired"
    /**
     * 2.0.0 [RVI-91] (검토 I03c-02) 낮 예행 알람: once a day a silent alarm in the daytime, set the way a shift alarm is, to see
     * whether this phone delivers alarms on time. Set (its time) and came (its time and how late), never anything else.
     */
    const val ALARM_REHEARSAL_SET = "alarm_rehearsal_set"
    const val ALARM_REHEARSAL = "alarm_rehearsal"
    const val SNOOZE_FIRED = "snooze_fired"
    const val SYSTEM_EVENT = "system_event"
    const val BACKUP_EXPORT = "backup_export"
    const val BACKUP_RESTORE = "backup_restore"
    const val ICS_EXPORT = "ics_export"
    const val COMPANY_SHARE = "company_share"
    const val COMPANY_IMPORT = "company_import"
    const val SCREEN = "screen"
    /** [STAB-01] A background job failed and was tried again; the detail is the job and the error's class. */
    const val JOB_FAILED = "job_failed"
    /**
     * [ALRM-01] The daily 밤낮 소식 job ran: a line a day that the phone and the app were alive, so a
     * missed alarm can be told apart as "the phone was off" (no line around it) or "the app was stopped".
     * Never sent anywhere.
     */
    const val ALIVE = "alive"
    /**
     * [ALRM-03] An alarm suggestion was answered: the detail is only its kind and the answer
     * ("late applied", "short declined", "early never", "late undone"), never times or names.
     */
    const val ALARM_SUGGEST = "alarm_suggest"
}

/**
 * [ALRM-01] The last seven days of alarms for the top of 알람 점검: how many were planned, how many rang,
 * how many rang a minute or more late (and by how long on average), and those that may have been missed.
 */
data class AlarmWeek(val planned: Int, val rang: Int, val late: Int, val averageLateSeconds: Long, val missed: List<Long>) {
    val allRang: Boolean get() = planned > 0 && missed.isEmpty()
}

/** Text codec, trimming and summaries for the event log. No Android code, so it is unit tested. */
object EventBook {
    /** The log is cut back to [KEEP] lines once it grows past [MAX]. */
    const val MAX = 1200
    const val KEEP = 1000

    fun encode(e: DiagEvent): String =
        "${e.atMillis}\t${clean(e.name)}\t${clean(e.detail)}"

    fun decode(line: String): DiagEvent? {
        val parts = line.split('\t', limit = 3)
        if (parts.size < 2) return null
        val at = parts[0].toLongOrNull() ?: return null
        if (parts[1].isEmpty()) return null
        return DiagEvent(at, parts[1], parts.getOrElse(2) { "" })
    }

    fun decodeAll(text: String): List<DiagEvent> = text.lineSequence().mapNotNull(::decode).toList()

    /** The newest [KEEP] lines when the log is over [MAX], otherwise null (nothing to do). */
    fun trimmed(lines: List<String>): List<String>? =
        if (lines.size > MAX) lines.takeLast(KEEP) else null

    /** How often each event happened since [sinceMillis], most frequent first. */
    fun counts(events: List<DiagEvent>, sinceMillis: Long): List<Pair<String, Int>> =
        events.asSequence()
            .filter { it.atMillis >= sinceMillis }
            .groupingBy { if (it.name == DiagEvents.SCREEN) "screen:${it.detail}" else it.name }
            .eachCount()
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key to it.value }

    /**
     * Alarms that were planned but have no fired, fallback or late record within [graceMillis]
     * of their time. Each one is a possible missed alarm worth a look [OPS-19].
     */
    fun possiblyMissed(events: List<DiagEvent>, nowMillis: Long, graceMillis: Long = 10 * 60_000L): List<Long> {
        val rang = events.filter {
            it.name == DiagEvents.ALARM_FIRED || it.name == DiagEvents.ALARM_FALLBACK || it.name == DiagEvents.ALARM_LATE_SKIPPED
        }.mapNotNull { triggerOf(it.detail) }.toSet()
        val plans = events.filter { it.name == DiagEvents.ALARM_SCHEDULED }
        return plans.mapNotNull { triggerOf(it.detail) }.distinct().sorted().filter { t ->
            if (t + graceMillis >= nowMillis || t in rang) return@filter false
            val lastPlanAt = plans.last { triggerOf(it.detail) == t }.atMillis
            // A plan replaced or cleared before its time (schedule edited, alarms off) is not missed.
            val superseded = events.any { e ->
                e.atMillis > lastPlanAt && e.atMillis < t &&
                    (e.name == DiagEvents.ALARM_CLEARED || (e.name == DiagEvents.ALARM_SCHEDULED && triggerOf(e.detail) != t))
            }
            !superseded
        }
    }

    /** [ALRM-01] The week up to [nowMillis] of alarms, from the log. */
    fun week(events: List<DiagEvent>, nowMillis: Long): AlarmWeek {
        val from = nowMillis - 7 * 24 * 60 * 60_000L
        val missed = possiblyMissed(events, nowMillis).filter { it >= from }
        val fired = events.filter { it.name == DiagEvents.ALARM_FIRED }
            .mapNotNull { e -> triggerOf(e.detail)?.takeIf { it in from..nowMillis }?.let { it to lateSeconds(e.detail) } }
            .distinctBy { it.first }
        val other = events.filter { it.name == DiagEvents.ALARM_FALLBACK || it.name == DiagEvents.ALARM_LATE_SKIPPED }
            .mapNotNull { triggerOf(it.detail)?.takeIf { t -> t in from..nowMillis } }.toSet() - fired.map { it.first }.toSet()
        val late = fired.filter { (it.second ?: 0) >= 60 }
        return AlarmWeek(
            planned = fired.size + other.size + missed.size,
            rang = fired.size + other.size,
            late = late.size + events.count { it.name == DiagEvents.ALARM_LATE_SKIPPED && (triggerOf(it.detail) ?: 0) in from..nowMillis },
            averageLateSeconds = late.mapNotNull { it.second }.takeIf { it.isNotEmpty() }?.average()?.toLong() ?: 0L,
            missed = missed,
        )
    }

    /**
     * 2.0.0 [RVI-86] (검토 I03a-06, 대기열 G4G9 발굴 3-2) Each alarm of the week up to [nowMillis], newest first: when it was to ring,
     * how late it came ([Rang.lateSeconds], null when it rang another way: the fallback or skipped as too late) and whether there is
     * no record of it at all ([Rang.missed]). The card listed only the totals; a late one or a missed one is now seen by its day.
     */
    fun weekRows(events: List<DiagEvent>, nowMillis: Long): List<Rang> {
        val from = nowMillis - 7 * 24 * 60 * 60_000L
        val fired = events.filter { it.name == DiagEvents.ALARM_FIRED }
            .mapNotNull { e -> triggerOf(e.detail)?.takeIf { it in from..nowMillis }?.let { Rang(it, lateSeconds(e.detail) ?: 0L, false) } }
            .distinctBy { it.at }
        val other = events.filter { it.name == DiagEvents.ALARM_FALLBACK || it.name == DiagEvents.ALARM_LATE_SKIPPED }
            .mapNotNull { triggerOf(it.detail)?.takeIf { t -> t in from..nowMillis } }.toSet() - fired.map { it.at }.toSet()
        val missed = possiblyMissed(events, nowMillis).filter { it >= from }
        return (fired + other.map { Rang(it, null, false) } + missed.map { Rang(it, null, true) }).distinctBy { it.at }.sortedByDescending { it.at }
    }

    /**
     * 2.0.0 [RVI-91] (검토 I03c-02) The silent daytime rehearsals of the week up to [nowMillis], newest first: each one set, how late it
     * came, or [Rang.missed] when it never came within [graceMillis] (the phone or the app was stopped).
     */
    fun rehearsals(events: List<DiagEvent>, nowMillis: Long, graceMillis: Long = 15 * 60_000L): List<Rang> {
        val from = nowMillis - 7 * 24 * 60 * 60_000L
        val came = events.filter { it.name == DiagEvents.ALARM_REHEARSAL }
            .mapNotNull { e -> triggerOf(e.detail)?.let { it to (lateSeconds(e.detail) ?: 0L) } }.toMap()
        return events.filter { it.name == DiagEvents.ALARM_REHEARSAL_SET }.mapNotNull { triggerOf(it.detail) }.distinct()
            .filter { it in from..nowMillis && (it in came || it + graceMillis < nowMillis) }
            .map { t -> came[t]?.let { Rang(t, it, false) } ?: Rang(t, null, true) }
            .sortedByDescending { it.at }
    }

    /**
     * 2.0.0 [RVI-91] What the rehearsals say, for 알람 점검 and the notice: null with none yet. "낮 예행 7번 모두 제때",
     * "낮 예행 7번 중 2번 늦게 · 가장 늦게 4분", "낮 예행 1번이 오지 않았어요".
     */
    fun rehearsalLine(rows: List<Rang>): String? {
        if (rows.isEmpty()) return null
        val missed = rows.count { it.missed }
        val late = rows.filter { it.late }
        return when {
            missed > 0 -> "낮 예행 ${missed}번이 오지 않았어요"
            late.isEmpty() -> "낮 예행 ${rows.size}번 모두 제때"
            else -> "낮 예행 ${rows.size}번 중 ${late.size}번 늦게 · 가장 늦게 ${late.maxOf { it.lateSeconds ?: 0L } / 60}분"
        }
    }

    /**
     * 2.0.0 [RVI-92] (검토 I03b-04, 대기열 발굴 3-3) What the last 1분 뒤 시험 알람 showed, once it was due: came on time, came late,
     * rang only as the plain notice (the ringing screen could not start), or never came. Null before one was set or while it is ahead.
     */
    fun lastTest(events: List<DiagEvent>, nowMillis: Long, graceMillis: Long = 3 * 60_000L): Rang? {
        val set = events.lastOrNull { it.name == DiagEvents.ALARM_TEST_SET }?.let { triggerOf(it.detail) } ?: return null
        val fired = events.lastOrNull { it.name == DiagEvents.ALARM_TEST_FIRED && triggerOf(it.detail) == set }
        if (fired == null) return if (set + graceMillis < nowMillis) Rang(set, null, true) else null
        val fallback = events.any { it.name == DiagEvents.ALARM_FALLBACK && triggerOf(it.detail) == set }
        return Rang(set, if (fallback) null else lateSeconds(fired.detail) ?: 0L, false)
    }

    /**
     * 2.0.0 [RVI-91] (검토 I03c-02) The week in one line for the top of 설정 › 알람: "지난 7일 알람 21번 모두 제때", "지난 7일 알람
     * 5번 중 1번 늦게", "지난 7일 알람 1번 기록 없음", with the daytime rehearsals after a dot. Null with neither yet.
     */
    fun trustLine(events: List<DiagEvent>, nowMillis: Long): String? {
        val rows = weekRows(events, nowMillis)
        val alarms = if (rows.isEmpty()) null else {
            val missed = rows.count { it.missed }
            val late = rows.count { it.late }
            when {
                missed > 0 -> "지난 7일 알람 ${rows.size}번 중 ${missed}번 기록 없음"
                late > 0 -> "지난 7일 알람 ${rows.size}번 중 ${late}번 늦게"
                else -> "지난 7일 알람 ${rows.size}번 모두 제때"
            }
        }
        val rehearsal = rehearsalLine(rehearsals(events, nowMillis))
        return listOfNotNull(alarms, rehearsal).joinToString(" · ").ifEmpty { null }
    }

    /** [RVI-86] One alarm of [weekRows]. */
    data class Rang(val at: Long, val lateSeconds: Long?, val missed: Boolean) {
        /** "제때", "3분 늦게", "울림" (another way), "기록 없음". */
        val said: String get() = when {
            missed -> "기록 없음"
            lateSeconds == null -> "울림"
            lateSeconds < 60 -> "제때"
            else -> "${lateSeconds / 60}분 늦게"
        }
        val late: Boolean get() = (lateSeconds ?: 0) >= 60
    }

    /** The "late_s=" a delivered alarm carries, in seconds. */
    fun lateSeconds(detail: String): Long? =
        detail.split(' ').firstOrNull { it.startsWith("late_s=") }?.removePrefix("late_s=")?.toLongOrNull()

    /** [ALRM-01] Whether the phone logged [DiagEvents.ALIVE] or opened the app within a day before [atMillis]. */
    fun aliveBefore(events: List<DiagEvent>, atMillis: Long): Boolean = events.any {
        (it.name == DiagEvents.ALIVE || it.name == DiagEvents.APP_OPEN) && it.atMillis in (atMillis - 24 * 60 * 60_000L)..atMillis
    }

    /** Alarm events carry "at=<epoch millis>" so plans and deliveries can be matched. */
    fun triggerDetail(triggerAtMillis: Long, extra: String = ""): String =
        if (extra.isEmpty()) "at=$triggerAtMillis" else "at=$triggerAtMillis $extra"

    fun triggerOf(detail: String): Long? =
        detail.split(' ').firstOrNull { it.startsWith("at=") }?.removePrefix("at=")?.toLongOrNull()

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ').take(200)
}
