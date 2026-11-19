package com.bamnat.core.imports

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

/**
 * 2.0.0 [RVL-73] (검토 I07bb-05) A calendar file (.ics, iCalendar RFC 5545): the open format other calendar and roster apps export, read
 * into the same [DeviceEvent]s the phone's calendars give, so [CalendarImport] proposes them the same way (근무·쉬는 날·휴가·메모).
 *
 * Read: VEVENT's SUMMARY, DTSTART/DTEND (a date, a local time, UTC "Z" or with TZID), DURATION, LOCATION, DESCRIPTION, folded lines and
 * escaped text; RRULE's FREQ DAILY·WEEKLY·MONTHLY·YEARLY with INTERVAL, COUNT, UNTIL and BYDAY, and EXDATE, opened within [from]..[to];
 * a cancelled event (STATUS:CANCELLED) is left out. An event's key stays the same for the same file read again (its UID and start), so
 * one brought in once is never brought in twice.
 */
object Ics {
    /** The calendar id the file's events carry (the phone's calendars have ids from 1). */
    const val FILE_CALENDAR = -1L
    /** At most this many occurrences of one repeating event. */
    const val MAX_REPEATS = 800
    /** A file bigger than this is not a calendar a person exports. */
    const val MAX_BYTES = 4 * 1024 * 1024

    fun looksLikeIcs(text: String): Boolean = text.trimStart('﻿', ' ', '\r', '\n').startsWith("BEGIN:VCALENDAR", ignoreCase = true)

    fun read(text: String, zone: ZoneId, from: LocalDate, to: LocalDate): List<DeviceEvent> {
        val out = mutableListOf<DeviceEvent>()
        var props: MutableList<Prop>? = null
        var depth = 0
        for (line in unfold(text)) {
            val upper = line.uppercase()
            when {
                upper == "BEGIN:VEVENT" -> { props = mutableListOf(); depth = 0 }
                upper.startsWith("BEGIN:") && props != null -> depth++ // a VALARM inside the event
                upper.startsWith("END:") && props != null && depth > 0 -> depth--
                upper == "END:VEVENT" -> { props?.let { out += event(it, zone, from, to) }; props = null }
                props != null && depth == 0 -> prop(line)?.let { props.add(it) }
            }
        }
        return out.sortedWith(compareBy({ it.begin }, { it.title }))
    }

    private data class Prop(val name: String, val params: Map<String, String>, val value: String)

    /** Lines as written, a folded line (one starting with a space or tab) joined to the one before. */
    private fun unfold(text: String): List<String> {
        val lines = mutableListOf<StringBuilder>()
        for (raw in text.removePrefix("﻿").split("\r\n", "\n", "\r")) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && lines.isNotEmpty()) lines.last().append(raw.substring(1))
            else lines += StringBuilder(raw)
        }
        return lines.map { it.toString() }.filter { it.isNotBlank() }
    }

    private fun prop(line: String): Prop? {
        // NAME;PARAM=a;PARAM="b:c":value — the first ':' outside quotes ends the name and its parameters.
        var quoted = false
        var colon = -1
        for ((i, ch) in line.withIndex()) {
            if (ch == '"') quoted = !quoted
            if (ch == ':' && !quoted) { colon = i; break }
        }
        if (colon <= 0) return null
        val head = line.substring(0, colon).split(';')
        val params = head.drop(1).mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].trim('"') } }.toMap()
        return Prop(head[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun text(v: String): String = buildString {
        var i = 0
        while (i < v.length) {
            val ch = v[i]
            if (ch == '\\' && i + 1 < v.length) {
                when (v[i + 1]) { 'n', 'N' -> append('\n'); else -> append(v[i + 1]) }
                i += 2
            } else { append(ch); i++ }
        }
    }.trim()

    /** A DTSTART-like value: the moment in [zone], and whether it is a date only. */
    private fun time(p: Prop, zone: ZoneId): Pair<LocalDateTime, Boolean>? {
        val v = p.value.trim()
        val dateOnly = p.params["VALUE"].equals("DATE", true) || Regex("""^\d{8}$""").matches(v)
        return runCatching {
            if (dateOnly) LocalDate.parse(v.take(8), DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay() to true
            else {
                val local = LocalDateTime.parse(v.removeSuffix("Z").take(15), STAMP)
                val at = when {
                    v.endsWith("Z") -> local.atZone(ZoneId.of("UTC"))
                    p.params["TZID"] != null -> runCatching { local.atZone(ZoneId.of(p.params.getValue("TZID"))) }.getOrElse { local.atZone(zone) }
                    else -> local.atZone(zone)
                }
                at.withZoneSameInstant(zone).toLocalDateTime() to false
            }
        }.getOrNull()
    }

    private val STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    /** "PT8H", "P1D", "PT8H30M": the length; null when not one. */
    private fun duration(v: String): java.time.Duration? = runCatching {
        val m = Regex("""^([+-])?P(?:(\d+)W)?(?:(\d+)D)?(?:T(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?)?$""").find(v.trim().uppercase()) ?: return null
        val (_, w, d, h, mi, s) = m.destructured
        java.time.Duration.ofDays((w.toLongOrNull() ?: 0) * 7 + (d.toLongOrNull() ?: 0)).plusHours(h.toLongOrNull() ?: 0)
            .plusMinutes(mi.toLongOrNull() ?: 0).plusSeconds(s.toLongOrNull() ?: 0)
    }.getOrNull()

    private fun event(props: List<Prop>, zone: ZoneId, from: LocalDate, to: LocalDate): List<DeviceEvent> {
        fun one(name: String) = props.firstOrNull { it.name == name }
        if (one("STATUS")?.value?.trim().equals("CANCELLED", true)) return emptyList()
        val (begin, allDay) = one("DTSTART")?.let { time(it, zone) } ?: return emptyList()
        val end = one("DTEND")?.let { time(it, zone)?.first }
            ?: one("DURATION")?.let { duration(it.value) }?.let { begin.plus(it) }
            ?: if (allDay) begin.plusDays(1) else begin
        val length = java.time.Duration.between(begin, end).takeIf { !it.isNegative } ?: java.time.Duration.ZERO
        val uid = one("UID")?.value?.trim().orEmpty().ifEmpty { "${one("SUMMARY")?.value}@$begin" }
        val title = one("SUMMARY")?.value?.let(::text).orEmpty()
        val location = one("LOCATION")?.value?.let(::text).orEmpty()
        val description = one("DESCRIPTION")?.value?.let(::text).orEmpty()
        val except = props.filter { it.name == "EXDATE" }.flatMap { p -> p.value.split(',').mapNotNull { v -> time(p.copy(value = v), zone)?.first } }.toSet()
        val starts = one("RRULE")?.let { repeats(begin, it.value, zone, from.minusDays(length.toDays() + 1), to) } ?: listOf(begin)
        // The id is the UID's, the same each time the file is read; the start tells its occurrences apart (DeviceEvent.key).
        val id = uid.hashCode().toLong() and 0xFFFFFFFFL
        return starts.filter { it !in except && it.toLocalDate() <= to && it.plus(length).toLocalDate() >= from }.map { s ->
            DeviceEvent(id, FILE_CALENDAR, title, s, s.plus(length), allDay, location, description)
        }
    }

    /** The starts of a repeating event from [first] that fall in [from]..[to], as RRULE says (COUNT counted from [first]). */
    internal fun repeats(first: LocalDateTime, rule: String, zone: ZoneId, from: LocalDate, to: LocalDate): List<LocalDateTime> {
        val parts = rule.split(';').mapNotNull { p -> p.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0].uppercase() to it[1].uppercase() } }.toMap()
        val freq = parts["FREQ"] ?: return listOf(first)
        val interval = parts["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1
        val count = parts["COUNT"]?.toIntOrNull()
        val until = parts["UNTIL"]?.let { u ->
            runCatching {
                if (u.length == 8) LocalDate.parse(u, DateTimeFormatter.BASIC_ISO_DATE).atTime(LocalTime.MAX)
                else LocalDateTime.parse(u.removeSuffix("Z").take(15), STAMP).let { l -> if (u.endsWith("Z")) l.atZone(ZoneId.of("UTC")).withZoneSameInstant(zone).toLocalDateTime() else l }
            }.getOrNull()
        }
        val last = minOf(until ?: LocalDateTime.MAX, to.atTime(LocalTime.MAX))
        val days = parts["BYDAY"]?.split(',')?.mapNotNull { d -> DAYS[d.takeLast(2)] }?.toSet().orEmpty()
        val out = mutableListOf<LocalDateTime>()
        var made = 0
        fun add(t: LocalDateTime): Boolean {
            if (t < first) return true
            if (t > last || (count != null && made >= count) || out.size >= MAX_REPEATS) return false
            made++
            if (t.toLocalDate() >= from) out += t
            return true
        }
        // Without COUNT, the steps before [from] need not be walked one by one (an event repeating since 2010).
        val ahead = if (count != null || !first.toLocalDate().isBefore(from)) 0L else when (freq) {
            "DAILY" -> ChronoUnit.DAYS.between(first.toLocalDate(), from)
            "WEEKLY" -> ChronoUnit.WEEKS.between(first.toLocalDate(), from)
            "MONTHLY" -> ChronoUnit.MONTHS.between(first.toLocalDate(), from)
            "YEARLY" -> ChronoUnit.YEARS.between(first.toLocalDate(), from)
            else -> 0L
        } / interval - 1
        val start = ahead.coerceAtLeast(0)
        var step = start
        loop@ while (true) {
            when (freq) {
                "DAILY" -> if (!add(first.plusDays(step * interval))) break@loop
                "WEEKLY" -> {
                    val week = first.plusWeeks(step * interval)
                    if (days.isEmpty()) { if (!add(week)) break@loop }
                    else {
                        val monday = week.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                        for (d in days.sorted()) if (!add(monday.plusDays(d.value - 1L))) break@loop
                    }
                }
                "MONTHLY" -> {
                    val m = first.plusMonths(step * interval)
                    if (m.dayOfMonth == first.dayOfMonth && !add(m)) break@loop
                    if (m.dayOfMonth != first.dayOfMonth && m > last) break@loop
                }
                "YEARLY" -> if (!add(first.plusYears(step * interval))) break@loop
                else -> return listOf(first)
            }
            step++
            if (step - start > MAX_REPEATS * 7) break
        }
        return out
    }

    private val DAYS = mapOf("MO" to DayOfWeek.MONDAY, "TU" to DayOfWeek.TUESDAY, "WE" to DayOfWeek.WEDNESDAY, "TH" to DayOfWeek.THURSDAY,
        "FR" to DayOfWeek.FRIDAY, "SA" to DayOfWeek.SATURDAY, "SU" to DayOfWeek.SUNDAY)
}
