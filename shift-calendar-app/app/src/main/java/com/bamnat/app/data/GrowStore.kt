package com.bamnat.app.data

import android.content.Context
import com.bamnat.core.guide.GrowNudges
import com.bamnat.core.guide.GrowState
import com.bamnat.core.guide.Peer
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.LocalDate

/**
 * AG 1.27 [INVITE-11][INVITE-12] What this phone remembers about asking to invite friends and to tell the developer
 * ([GrowState], rules in core [GrowNudges]). Only on this phone (SettingsFilesBackup.DEVICE_ONLY "grow"); turning an
 * ask off is a feature switch (guide `off`), which a backup carries.
 */
class GrowStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("grow", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<GrowState> = _state.asStateFlow()
    val current: GrowState get() = _state.value

    fun inviteShown(day: LocalDate = Today.date()) {
        if (current.inviteShownOn != day) edit { it.copy(inviteShownOn = day) }
    }

    fun inviteClosed(day: LocalDate = Today.date()) = edit { it.copy(inviteClosedOn = day) }

    fun cardClosed(day: LocalDate = Today.date()) = edit { it.copy(cardClosedOn = day) }

    /** 1.39 [SPREAD-07] The leader sent the team's links, or the 사장님 has a staff member: the one card may come. */
    fun peerDue(peer: Peer) { if (peer !in current.peerDue) edit { it.copy(peerDue = it.peerDue + peer) } }

    fun peerShown(peer: Peer, day: LocalDate = Today.date()) {
        if (current.peerShownOn[peer] == null) edit { it.copy(peerShownOn = it.peerShownOn + (peer to day)) }
    }

    fun peerClosed(peer: Peer) = edit { it.copy(peerDone = it.peerDone + peer) }

    fun crashSeen(at: Long) = edit { it.copy(crashSeenAt = maxOf(it.crashSeenAt, at)) }

    /** One 되돌리기; true when it is the one that says "잘 안 되는 게 있었나요?" (and that is recorded). */
    fun undone(day: LocalDate = Today.date()): Boolean {
        var tell = false
        edit { s ->
            val next = GrowNudges.undone(s, day)
            tell = GrowNudges.feedbackAfterUndo(next, day)
            if (tell) next.copy(undoToldOn = day) else next
        }
        return tell
    }

    private fun edit(change: (GrowState) -> GrowState) {
        synchronized(this) {
            _state.update(change)
            val s = _state.value
            prefs.edit()
                .putString(K_INVITE_SHOWN, s.inviteShownOn?.toString())
                .putString(K_INVITE_CLOSED, s.inviteClosedOn?.toString())
                .putString(K_CARD_CLOSED, s.cardClosedOn?.toString())
                .putLong(K_CRASH_SEEN, s.crashSeenAt)
                .putString(K_UNDO_DAY, s.undoDay?.toString())
                .putInt(K_UNDO_COUNT, s.undoCount)
                .putString(K_UNDO_TOLD, s.undoToldOn?.toString())
                .putStringSet(K_PEER_DUE, s.peerDue.map { it.name }.toSet())
                .putStringSet(K_PEER_DONE, s.peerDone.map { it.name }.toSet())
                .putString(K_PEER_SHOWN, s.peerShownOn.entries.joinToString(",") { "${it.key.name}=${it.value}" })
                .apply()
        }
    }

    private fun peers(k: String): Set<Peer> =
        prefs.getStringSet(k, null).orEmpty().mapNotNull { n -> Peer.entries.firstOrNull { it.name == n } }.toSet()

    private fun read(): GrowState {
        fun day(k: String) = prefs.getString(k, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return GrowState(
            inviteShownOn = day(K_INVITE_SHOWN), inviteClosedOn = day(K_INVITE_CLOSED), cardClosedOn = day(K_CARD_CLOSED),
            crashSeenAt = prefs.getLong(K_CRASH_SEEN, 0), undoDay = day(K_UNDO_DAY), undoCount = prefs.getInt(K_UNDO_COUNT, 0),
            undoToldOn = day(K_UNDO_TOLD),
            peerDue = peers(K_PEER_DUE), peerDone = peers(K_PEER_DONE),
            peerShownOn = prefs.getString(K_PEER_SHOWN, null).orEmpty().split(',').mapNotNull { e ->
                val (k, v) = e.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                val peer = Peer.entries.firstOrNull { it.name == k } ?: return@mapNotNull null
                runCatching { peer to LocalDate.parse(v) }.getOrNull()
            }.toMap(),
        )
    }

    private companion object {
        const val K_INVITE_SHOWN = "invite_shown"
        const val K_INVITE_CLOSED = "invite_closed"
        const val K_CARD_CLOSED = "card_closed"
        const val K_CRASH_SEEN = "crash_seen"
        const val K_UNDO_DAY = "undo_day"
        const val K_UNDO_COUNT = "undo_count"
        const val K_UNDO_TOLD = "undo_told"
        const val K_PEER_DUE = "peer_due"
        const val K_PEER_DONE = "peer_done"
        const val K_PEER_SHOWN = "peer_shown"
    }
}
