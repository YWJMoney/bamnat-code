package com.bamnat.app.tier

import android.content.Context
import com.bamnat.core.tier.ProMoments
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/** 1.32 [PROX-02] What this phone remembers about the 체험 권유 of a money day. */
data class ProMomentState(
    /** The seasons already offered ([ProMoments.seasonOf]). */
    val offered: Set<String> = emptySet(),
    val lastOffer: LocalDate? = null,
    /** The moment on screen today, kept for the day it first showed. */
    val showing: String? = null,
    val showingOn: LocalDate? = null,
    val closedOn: LocalDate? = null,
    /** The day a 듀티표 last went in. */
    val rosterOn: LocalDate? = null,
    /** 1.36 840: the days the app was opened (the last month), and the last one before today, for [com.bamnat.core.tier.ProOfferTimes]. */
    val used: Set<LocalDate> = emptySet(),
    val lastBefore: LocalDate? = null,
    /** 840 '7일간 보지 않기'. */
    val snoozedUntil: LocalDate? = null,
    /** 840 밤낮이 찾아 준 돈: by month ("2027-03"), what the app's month came to beyond the payslip at its last check. */
    val found: Map<String, Long> = emptyMap(),
    /** 840: 프로 was just bought; the calendar says welcome once. */
    val welcome: Boolean = false,
) {
    /** The year's 찾아 준 돈 so far. */
    fun foundIn(year: Int): Long = found.filterKeys { it.startsWith("$year-") }.values.sum()
}

/**
 * 1.32 [PROX-02] Only on this phone (SettingsFilesBackup.DEVICE_ONLY "pro_moments"): a new phone starts with no offer made.
 * Turning the offers off is the feature switch `pro.moments`, which a backup carries.
 */
class ProMomentStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pro_moments", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ProMomentState> = _state.asStateFlow()

    /** The moment to show today, or null ([ProMoments.due] once a day, then the same one all day). */
    fun today(s: ProMomentState, today: LocalDate, due: () -> ProMoments.Moment?): ProMoments.Moment? = when {
        s.closedOn == today -> null
        s.showingOn == today -> ProMoments.Moment.entries.firstOrNull { it.id == s.showing }
        else -> due()
    }

    fun shown(m: ProMoments.Moment, day: LocalDate = Today.date()) {
        if (_state.value.showingOn == day) return
        edit { it.copy(offered = (it.offered + ProMoments.seasonOf(m, day)).takeLastSorted(), lastOffer = day, showing = m.id, showingOn = day) }
    }

    fun closed(day: LocalDate = Today.date()) = edit { it.copy(closedOn = day) }

    /** 840: the app opened on [day]; the first time that day keeps the day before it was last opened. */
    fun opened(day: LocalDate = Today.date()) {
        val s = _state.value
        if (day in s.used) return
        edit { it.copy(used = (it.used + day).filter { d -> d > day.minusDays(USED_DAYS) }.toSet(), lastBefore = it.used.filter { d -> d < day }.maxOrNull() ?: it.lastBefore) }
    }

    /** 840: an offer card [key] shown on [day] (the same card stays all day). */
    fun offerShown(key: String, day: LocalDate = Today.date()) {
        if (_state.value.showingOn == day) return
        edit { it.copy(offered = (it.offered + key).takeLastSorted(), lastOffer = day, showing = key, showingOn = day) }
    }

    /** 840 '7일간 보지 않기'. */
    fun snooze(day: LocalDate = Today.date()) = edit { it.copy(snoozedUntil = day.plusDays(com.bamnat.core.tier.ProOfferTimes.SNOOZE_DAYS), closedOn = day) }

    /** 840: a payslip check of [month]: the app came to [beyond] more than the payslip (0 when not). */
    fun found(month: java.time.YearMonth, beyond: Long) = edit {
        val key = month.toString()
        it.copy(found = (if (beyond > 0) it.found + (key to beyond) else it.found - key).toSortedMap().entries.toList().takeLast(MAX_SEASONS).associate { e -> e.key to e.value })
    }

    fun welcome(on: Boolean) { if (_state.value.welcome != on) edit { it.copy(welcome = on) } }

    fun rosterIn(day: LocalDate = Today.date()) {
        if (_state.value.rosterOn != day) edit { it.copy(rosterOn = day) }
    }

    /** Old seasons are let go so the file stays small; a year of them is plenty. */
    private fun Set<String>.takeLastSorted(): Set<String> = sorted().takeLast(MAX_SEASONS).toSet()

    private fun edit(change: (ProMomentState) -> ProMomentState) {
        synchronized(this) {
            _state.update(change)
            val s = _state.value
            prefs.edit()
                .putStringSet(K_OFFERED, s.offered)
                .putString(K_LAST, s.lastOffer?.toString())
                .putString(K_SHOWING, s.showing)
                .putString(K_SHOWING_ON, s.showingOn?.toString())
                .putString(K_CLOSED, s.closedOn?.toString())
                .putString(K_ROSTER, s.rosterOn?.toString())
                .putStringSet(K_USED, s.used.map { it.toString() }.toSet())
                .putString(K_BEFORE, s.lastBefore?.toString())
                .putString(K_SNOOZE, s.snoozedUntil?.toString())
                .putStringSet(K_FOUND, s.found.map { (k, v) -> "$k=$v" }.toSet())
                .putBoolean(K_WELCOME, s.welcome)
                .apply()
        }
    }

    private fun read(): ProMomentState {
        fun day(k: String) = prefs.getString(k, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return ProMomentState(
            offered = prefs.getStringSet(K_OFFERED, null).orEmpty().toSet(), lastOffer = day(K_LAST),
            showing = prefs.getString(K_SHOWING, null), showingOn = day(K_SHOWING_ON), closedOn = day(K_CLOSED), rosterOn = day(K_ROSTER),
            used = prefs.getStringSet(K_USED, null).orEmpty().mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet(),
            lastBefore = day(K_BEFORE), snoozedUntil = day(K_SNOOZE),
            found = prefs.getStringSet(K_FOUND, null).orEmpty().mapNotNull { e -> e.split('=').takeIf { it.size == 2 }?.let { it[0] to (it[1].toLongOrNull() ?: return@mapNotNull null) } }.toMap(),
            welcome = prefs.getBoolean(K_WELCOME, false),
        )
    }

    private companion object {
        const val K_OFFERED = "offered"
        const val K_LAST = "last_offer"
        const val K_SHOWING = "showing"
        const val K_SHOWING_ON = "showing_on"
        const val K_CLOSED = "closed_on"
        const val K_ROSTER = "roster_on"
        const val MAX_SEASONS = 24
        const val K_USED = "used"
        const val K_BEFORE = "last_before"
        const val K_SNOOZE = "snoozed_until"
        const val K_FOUND = "found"
        const val K_WELCOME = "welcome"
        const val USED_DAYS = 31L
    }
}
