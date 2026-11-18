package com.bamnat.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.bamnat.app.AppContainer
import com.bamnat.app.ui.components.AppSnack
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

/**
 * [DRW-03] 1.37 넣었어요 · 되돌리기 (후보 1, 사용자 2026-11-04 묶음 C3): right after a theme is put on or taken off, a tab laid on is taken
 * off or a rule is switched on, one line says what happened, with 되돌리기, wherever it was done — the screen that did it often closes at
 * once (입어 보기), so the line is the app's own, over the bottom bar.
 */
object PutOnNotice {
    class Note(val text: String, val undo: (suspend () -> Boolean)?)

    private val _notes = MutableSharedFlow<Note>(extraBufferCapacity = 4)
    val notes: SharedFlow<Note> = _notes.asSharedFlow()

    /** Says [text]; [undo] (when there is one) takes it back and says whether it could. */
    fun say(text: String, undo: (suspend () -> Boolean)? = null) { _notes.tryEmit(Note(text, undo)) }
}

/**
 * The line of [PutOnNotice], on the app's line ([AppSnack]): 8 seconds from when it shows when it can be taken back (as the other
 * 되돌리기 lines, and first in the queue), short otherwise. 2.0.0 [RVP-23]: a newer 되돌리기 goes before an older one by itself, so
 * this no longer closes whatever line was showing (another screen's 되돌리기 included).
 */
@Composable
fun PutOnNoticeHost(c: AppContainer) {
    LaunchedEffect(Unit) {
        PutOnNotice.notes.collect { n ->
            launch {
                val undo = n.undo
                if (undo == null) { AppSnack.show(n.text); return@launch }
                if (AppSnack.ask(n.text, "되돌리기")) {
                    val ok = runCatching { kotlinx.coroutines.withContext(c.appScope.coroutineContext) { undo() } }.getOrDefault(false)
                    if (ok) AppSnack.show("되돌렸어요") else AppSnack.show("되돌리지 못했어요", kind = AppSnack.Kind.FAIL)
                }
            }
        }
    }
}
