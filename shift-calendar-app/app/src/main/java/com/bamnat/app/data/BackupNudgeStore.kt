package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.backup.BackupNudge
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * 1.32 [STAB-08] When this phone last saved a backup by hand, and when the calendar's backup line was put away. Only on this
 * phone (SettingsFilesBackup.DEVICE_ONLY "backup_nudge"): a backup made on another phone does not keep this one's records.
 */
class BackupNudgeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("backup_nudge", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<BackupNudge.State> = _state.asStateFlow()

    init {
        if (_state.value.firstSeen == null) edit { it.copy(firstSeen = Today.date()) }
    }

    /** A file saved and read back whole. */
    fun saved(day: LocalDate = Today.date()) = edit { it.copy(lastManual = day, quietUntil = null) }

    fun later(day: LocalDate = Today.date()) = edit { it.copy(quietUntil = day.plusDays(BackupNudge.LATER_DAYS)) }

    private fun edit(change: (BackupNudge.State) -> BackupNudge.State) {
        synchronized(this) {
            _state.update(change)
            val s = _state.value
            prefs.edit()
                .putString(K_FIRST, s.firstSeen?.toString())
                .putString(K_MANUAL, s.lastManual?.toString())
                .putString(K_QUIET, s.quietUntil?.toString())
                .apply()
        }
    }

    private fun read(): BackupNudge.State {
        fun day(k: String) = prefs.getString(k, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return BackupNudge.State(day(K_FIRST), day(K_MANUAL), day(K_QUIET))
    }

    private companion object {
        const val K_FIRST = "first_seen"
        const val K_MANUAL = "last_manual"
        const val K_QUIET = "quiet_until"
    }
}
