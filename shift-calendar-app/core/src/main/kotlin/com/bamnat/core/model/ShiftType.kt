package com.bamnat.core.model

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime

/**
 * A kind of shift the user works, e.g. 주간 09:00–18:00 or 야간 22:00–07:00.
 *
 * A shift whose [end] is not after its [start] runs past midnight (overnight shift).
 * Equal start and end means a full 24-hour shift.
 */
data class ShiftType(
    val id: Long,
    val name: String,
    /** One or two characters drawn inside calendar cells, e.g. "주", "D". */
    val label: String,
    val colorArgb: Long,
    val start: LocalTime?,
    val end: LocalTime?,
    val breakMinutes: Int = 0,
    /**
     * When the break starts [PAY-01]. Break time is not working time (근로기준법 제54조), so a
     * break inside 22:00~06:00 takes night minutes off exactly; null takes it out evenly (estimate).
     */
    val breakStart: LocalTime? = null,
    /** Off-duty type (휴무, 비번). Off types never count toward work hours. */
    val isOff: Boolean = false,
    /** Turns all of this shift's alarms on or off at once. */
    val alarmEnabled: Boolean = false,
    /** Alarms before [start]; a shift can ring several times (e.g. 90 and 60 minutes before). */
    val alarms: List<AlarmSpec> = listOf(AlarmSpec(60)),
    /** Alarm sound for this shift; null uses the default sound from settings. */
    val alarmSoundUri: String? = null,
    val sortOrder: Int = 0,
    /** Key of a [ShiftIcon] shown next to the label; null shows the label only [FEAT-01]. */
    val icon: String? = null,
    /** [SHFT-01] This kind of day's own extra pay on top of the company's rules, in percent (야간 근무 수당 30%); 0 for none. */
    val premiumPercent: Int = 0,
    /**
     * [PAYX-32] This kind of day's break: true paid (유급 휴게), false unpaid, null as the company's rule says
     * ([com.bamnat.core.pay.CompanyRules.breakPay]). E.g. a 야간 whose 1시간 휴게 is paid while the 주간's is not.
     */
    val breakPaid: Boolean? = null,
    /**
     * [RVH-33] 검토 WORK-19 The times this kind of day had before, each up to its [TimeVersion.until], earliest first: a time changed
     * '오늘부터만' leaves the days before with the old one, so last month's hours, stats and pay stay as they were. Empty: the
     * times above are every day's (as before 2.0.0, and after '지난 날도').
     */
    val timeHistory: List<TimeVersion> = emptyList(),
) {
    val hasTimes: Boolean get() = !isOff && start != null && end != null

    /** 검토 WORK-19 This kind of day with the times it had on [date] ([timeHistory]); itself when they have not changed since. */
    fun on(date: LocalDate): ShiftType {
        if (timeHistory.isEmpty()) return this
        val v = timeHistory.firstOrNull { !date.isAfter(it.until) } ?: return this
        return copy(start = v.start, end = v.end, breakMinutes = v.breakMinutes, breakStart = v.breakStart)
    }

    /** 검토 WORK-19 Whether [other] works the same hours (start, end and break), whatever else differs. */
    fun sameTimes(other: ShiftType): Boolean =
        start == other.start && end == other.end && breakMinutes == other.breakMinutes && breakStart == other.breakStart

    /**
     * 검토 WORK-19 [changed] (this kind with new times) from [from] on only: the days before keep the times they had. A version that
     * already ends the day before stays as it is (a second change on the same day keeps the first times for the past).
     */
    fun withTimesFrom(changed: ShiftType, from: LocalDate): ShiftType {
        val eve = from.minusDays(1)
        val before = timeHistory.filter { it.until.isBefore(from) }
        val add = if (sameTimes(changed) || before.any { it.until == eve } || isOff) emptyList()
        else listOf(TimeVersion(eve, start, end, breakMinutes, breakStart))
        return changed.copy(timeHistory = (before + add).sortedBy { it.until })
    }

    /** 검토 WORK-19 [changed] on every day, the past too ('지난 날도'): the old times are let go. */
    fun withTimesEverywhere(changed: ShiftType): ShiftType = changed.copy(timeHistory = emptyList())

    /** Length from start to end, 1 minute to 24 hours (equal start and end is a 24-hour shift). */
    val durationMinutes: Int
        get() = if (!hasTimes) 0 else WorkTime.spanMinutes(start!!, end!!)

    /** Alarms that will actually ring, earliest first. */
    val activeAlarms: List<AlarmSpec>
        get() = if (!alarmEnabled || !hasTimes) emptyList()
        else alarms.filter { it.enabled }.distinctBy { it.offsetMinutes }.sortedByDescending { it.offsetMinutes }

    /** Same start, new length. 24 hours ends at the start time the next day. */
    fun withDuration(minutes: Int): ShiftType {
        val s = start ?: return this
        val m = minutes.coerceIn(WorkTime.MIN_DURATION_MINUTES, WorkTime.MINUTES_PER_DAY)
        return copy(end = s.plusMinutes(m.toLong()))
    }

    /** Paid working minutes of one occurrence of this shift. */
    val workMinutes: Int
        get() = if (!hasTimes) 0 else WorkTime.workMinutes(start!!, end!!, breakMinutes)
}

/** 검토 WORK-19 The times a kind of day had up to and including [until]. */
data class TimeVersion(
    val until: LocalDate,
    val start: LocalTime?,
    val end: LocalTime?,
    val breakMinutes: Int = 0,
    val breakStart: LocalTime? = null,
) {
    companion object {
        /** "2026-11-11|420|1140|60|720;…" (minutes of the day, empty for none), as the database and the backup keep it. */
        fun encode(list: List<TimeVersion>): String = list.joinToString(";") { v ->
            listOf(v.until.toString(), m(v.start), m(v.end), v.breakMinutes.toString(), m(v.breakStart)).joinToString("|")
        }

        /** Back from [encode]; a part it cannot read is left out, never the whole kind of day. */
        fun decode(raw: String?): List<TimeVersion> = raw.orEmpty().split(';').mapNotNull { part ->
            val f = part.split('|')
            if (f.size < 5) return@mapNotNull null
            val until = runCatching { LocalDate.parse(f[0]) }.getOrNull() ?: return@mapNotNull null
            TimeVersion(until, t(f[1]), t(f[2]), f[3].toIntOrNull()?.coerceIn(0, 12 * 60) ?: 0, t(f[4]))
        }.sortedBy { it.until }

        private fun m(t: LocalTime?): String = t?.let { (it.hour * 60 + it.minute).toString() }.orEmpty()
        private fun t(s: String): LocalTime? = s.toIntOrNull()?.takeIf { it in 0 until 24 * 60 }?.let { LocalTime.of(it / 60, it % 60) }
    }
}

/** One alarm of a shift type, [offsetMinutes] before the shift starts. */
data class AlarmSpec(val offsetMinutes: Int, val enabled: Boolean = true)

object WorkTime {
    const val MINUTES_PER_DAY = 24 * 60
    const val MIN_DURATION_MINUTES = 30

    /** Span between [start] and [end] in minutes, treating end <= start as crossing midnight. */
    fun spanMinutes(start: LocalTime, end: LocalTime): Int {
        val raw = Duration.between(start, end).toMinutes().toInt()
        return if (raw <= 0) raw + MINUTES_PER_DAY else raw
    }

    /** Span minus break, never negative. */
    fun workMinutes(start: LocalTime, end: LocalTime, breakMinutes: Int): Int =
        (spanMinutes(start, end) - breakMinutes.coerceAtLeast(0)).coerceAtLeast(0)

    fun isOvernight(start: LocalTime, end: LocalTime): Boolean = !end.isAfter(start)
}

/** Icons a shift type can carry [FEAT-01]. The app maps each [key] to a drawable. */
enum class ShiftIcon(val key: String, val label: String) {
    SUN("sun", "해"),
    SUNRISE("sunrise", "아침"),
    EVENING("evening", "저녁"),
    MOON("moon", "달"),
    CLOCK24("clock24", "24시간"),
    BED("bed", "휴식"),
    COFFEE("coffee", "커피"),
    HOSPITAL("hospital", "병원"),
    FACTORY("factory", "공장"),
    STORE("store", "매장"),
    CAR("car", "운전"),
    SHIELD("shield", "경비"),
    SCHOOL("school", "교육"),
    FLIGHT("flight", "출장"),
    HOME("home", "재택"),
    STAR("star", "별"),
    ;

    companion object {
        fun of(key: String?): ShiftIcon? = key?.let { k -> entries.firstOrNull { it.key == k } }

        /** [RCP-08] 1.34: an emoji in place of a drawn icon, kept as "e:<emoji>" in [ShiftType.icon]; null for a drawn icon or none. */
        fun emoji(key: String?): String? = key?.takeIf { it.startsWith(EMOJI) }?.removePrefix(EMOJI)?.takeIf { it.isNotEmpty() }

        fun emojiKey(emoji: String): String = EMOJI + emoji

        const val EMOJI = "e:"

        /** A sensible icon for a new type from its times. */
        fun suggest(type: ShiftType): ShiftIcon? {
            if (type.isOff) return BED
            val s = type.start ?: return null
            if (type.durationMinutes >= 20 * 60) return CLOCK24
            return when (s.hour) {
                in 4..9 -> SUN
                in 10..15 -> EVENING
                else -> MOON
            }
        }
    }
}
