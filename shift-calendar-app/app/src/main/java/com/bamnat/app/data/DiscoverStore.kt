package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.guide.DiscoverAnswer
import com.bamnat.core.guide.DiscoverRules
import com.bamnat.core.guide.DiscoverState
import com.bamnat.core.guide.DiscoverTold
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.40 [DISC-01][DISC-04] What this phone remembers about '안 써 본 기능 알려 주기' ([DiscoverState], rules in core [DiscoverRules]): the
 * cards shown and their answers, the gap to the next. Only on this phone (SettingsFilesBackup.DEVICE_ONLY "discover"): a new phone
 * rests first, like the first days. Nothing here is sent.
 */
class DiscoverStore private constructor(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("discover", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<DiscoverState> = _state.asStateFlow()
    val current: DiscoverState get() = _state.value

    /** The day begins: yesterday's card with no answer counts as ignored. */
    fun settle(today: LocalDate = Today.date()) {
        if (DiscoverRules.settle(current, today) != current) edit { DiscoverRules.settle(it, today) }
    }

    fun shown(id: String, today: LocalDate = Today.date()) {
        if (current.todays(today) == null) edit { DiscoverRules.shown(it, id, today) }
    }

    fun answer(a: DiscoverAnswer, today: LocalDate = Today.date()) = edit { DiscoverRules.answer(it, today, a) }

    /** Taking back today's answer ('되돌리기'): the card is today's again, with the gap it had. */
    fun undo(before: DiscoverState) = edit { before }

    /** [DISC-04] '다시 알려 주세요'. */
    fun again(id: String) = edit { DiscoverRules.again(it, id) }

    /** [DISC-03] A backup came back: the 써 봄 marks are this phone's, so the cards rest a while. */
    fun restored(today: LocalDate = Today.date()) = edit { it.copy(restoredOn = today) }

    /** An alarm stopped ringing: no card in the minutes after. */
    fun alarmEnded(at: LocalDateTime = Today.dateTime()) = edit { it.copy(alarmEndedAt = at) }

    private fun edit(change: (DiscoverState) -> DiscoverState) {
        synchronized(this) {
            _state.update(change)
            val s = _state.value
            prefs.edit()
                .putString(K_TOLD, s.told.takeLast(MAX_KEPT).joinToString("\n") { "${it.id}|${it.on}|${it.answer?.name.orEmpty()}" })
                .putInt(K_GAP, s.gap)
                .putInt(K_IGNORED, s.ignoredInRow)
                .putString(K_NEXT, s.nextOn?.toString())
                .putString(K_RESTORED, s.restoredOn?.toString())
                .putString(K_ALARM, s.alarmEndedAt?.toString())
                .apply()
        }
    }

    private fun read(): DiscoverState {
        fun day(k: String) = prefs.getString(k, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        val told = prefs.getString(K_TOLD, null).orEmpty().lines().mapNotNull { line ->
            val p = line.split('|')
            if (p.size < 2) return@mapNotNull null
            val on = runCatching { LocalDate.parse(p[1]) }.getOrNull() ?: return@mapNotNull null
            DiscoverTold(p[0], on, p.getOrNull(2)?.takeIf { it.isNotEmpty() }?.let { n -> DiscoverAnswer.entries.firstOrNull { it.name == n } })
        }
        return DiscoverState(
            told = told,
            gap = prefs.getInt(K_GAP, DiscoverRules.FIRST_GAP),
            ignoredInRow = prefs.getInt(K_IGNORED, 0),
            nextOn = day(K_NEXT),
            restoredOn = day(K_RESTORED),
            alarmEndedAt = prefs.getString(K_ALARM, null)?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() },
        )
    }

    companion object {
        private const val K_TOLD = "told"
        private const val K_GAP = "gap"
        private const val K_IGNORED = "ignored"
        private const val K_NEXT = "next"
        private const val K_RESTORED = "restored"
        private const val K_ALARM = "alarm_ended"
        /** A year's cards and more: enough for the caps and the list in 기능 켜고 끄기. */
        private const val MAX_KEPT = 80

        @Volatile private var instance: DiscoverStore? = null

        fun get(context: Context): DiscoverStore = instance ?: synchronized(this) {
            instance ?: DiscoverStore(context).also { instance = it }
        }
    }
}
