package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.plaza.PlazaTag
import com.bamnat.core.plaza.PlazaTags
import com.bamnat.core.plaza.StylePlaza
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.json.JSONObject

/**
 * 1.39 [PLZC-10] 근무 태그 on this phone: what the writer chose to show ([scope], kept on this phone only) and the tag a post in a
 * kind of work's plaza carries, worked out from the calendar of that kind of work when it is sent. Nothing leaves the phone but
 * the keys of the tag, and only while plaza.people is on (처리방침 3.12). 설계 3장.
 */
class PlazaTagSource(private val c: AppContainer) {
    private val prefs by lazy { c.appContext.getSharedPreferences("plaza_tag", android.content.Context.MODE_PRIVATE) }

    /** Whether tags are on at all (처리방침 3.12). */
    val on: Boolean get() = c.flags.on(FlagCatalog.PLAZA_PEOPLE)

    fun scope(): PlazaTags.Scope = runCatching {
        PlazaTags.Scope(
            form = prefs.getBoolean("form", true),
            industry = prefs.getString("industry", null)?.takeIf { it.isNotBlank() },
            months = prefs.getBoolean("months", true),
            now = prefs.getBoolean("now", true),
        )
    }.getOrDefault(PlazaTags.Scope())

    fun setScope(s: PlazaTags.Scope) {
        prefs.edit().putBoolean("form", s.form).putString("industry", s.industry).putBoolean("months", s.months).putBoolean("now", s.now)
            .putBoolean("chosen", true).apply()
    }

    /** Whether the user has seen and kept (or changed) the 공개 범위 once; until then the writing box shows it first. */
    val chosen: Boolean get() = runCatching { prefs.getBoolean("chosen", false) }.getOrDefault(false)

    /** The calendar whose kind of work [room] is: the one on screen first, then the main, then any. */
    private fun calendarFor(style: WorkStyle): String? {
        val main = c.settingsRepository.current.workStyle
        val ids = listOf(c.calendars.current().id, PersonalCalendar.MAIN) + c.calendars.calendars.value.map { it.id }
        return ids.distinct().firstOrNull { c.calendars.styleOf(it, main) == style }
    }

    /** My day by the calendar of [room]'s kind of work: the tag a post carries now, and whether a night just ended or today is off. */
    data class MyDay(val tag: PlazaTag, val offAfterNight: Boolean = false, val dayOff: Boolean = false, val todayShift: PlazaTag.Now? = null)

    /** The tag a post in [room] carries now (months left to the server); null without a calendar of that kind of work. */
    suspend fun tagFor(room: String): PlazaTag? = dayFor(room)?.tag

    suspend fun dayFor(room: String): MyDay? {
        val style = StylePlaza.styleOf(room) ?: return null
        val calendar = calendarFor(style) ?: return MyDay(PlazaTag(style))
        val today = Today.date()
        val snap = c.calendars.repository(calendar).windowSnapshot(java.time.YearMonth.from(today))
        val pattern = PatternTimeline.current(snap.patterns, today)
        val worked = snap.resolver.resolveRange(today.minusDays(PlazaTags.WINDOW_DAYS - 1L), today).count { it.isWorkDay }
        val profile = runCatching { c.calendars.pay(calendar).profile.value }.getOrNull()
        val at = Today.dateTime()
        val yesterday = snap.resolver.resolve(today.minusDays(1))
        val todayDay = snap.resolver.resolve(today)
        // Today's shift, or yesterday's still running past midnight.
        val now = listOf(yesterday, todayDay).firstNotNullOfOrNull { PlazaTags.nowOf(it.shiftFrom, it.shiftTo, at) }
        // A night shift that ended this morning, within the last 10 hours.
        val offAfterNight = now == null && yesterday.shiftTo?.let { end ->
            end.toLocalDate() == today && !at.isBefore(end) && at.isBefore(end.plusHours(10)) && yesterday.shiftFrom?.toLocalDate() != today
        } == true
        val dayOff = now == null && !offAfterNight && !todayDay.isWorkDay
        // [PLZC-21] 오늘 출근: the shift on now (yesterday's night still running counts), else today's own, night or day.
        val running = listOf(yesterday, todayDay).firstOrNull { PlazaTags.nowOf(it.shiftFrom, it.shiftTo, at) != null }
        val todayShift = (running ?: todayDay.takeIf { it.isWorkDay })?.shiftFrom?.let { PlazaTags.shiftOf(it.hour) }
        return MyDay(PlazaTags.compute(style, snap.shiftTypes, pattern, profile, worked, scope(), now), offAfterNight, dayOff, todayShift)
    }

    /** What goes with a post: the tag's keys, and whether the server may add the months ("m": true). */
    suspend fun json(room: String): JSONObject? {
        if (!on) return null
        val t = tagFor(room) ?: return null
        return JSONObject(t.fields()).apply { if (scope().months) put("m", true) }
    }
}
