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
)

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
                .apply()
        }
    }

    private fun read(): ProMomentState {
        fun day(k: String) = prefs.getString(k, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return ProMomentState(
            offered = prefs.getStringSet(K_OFFERED, null).orEmpty().toSet(), lastOffer = day(K_LAST),
            showing = prefs.getString(K_SHOWING, null), showingOn = day(K_SHOWING_ON), closedOn = day(K_CLOSED), rosterOn = day(K_ROSTER),
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
    }
}
