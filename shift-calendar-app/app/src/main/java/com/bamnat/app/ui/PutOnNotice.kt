package com.bamnat.app.ui

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.bamnat.app.AppContainer
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

/** The line of [PutOnNotice], on the app's snackbar: 8 seconds when it can be taken back (as the other 되돌리기 lines), short otherwise. */
@Composable
fun PutOnNoticeHost(c: AppContainer, snackbar: SnackbarHostState) {
    LaunchedEffect(Unit) {
        PutOnNotice.notes.collect { n ->
            // A newer line takes the place of the one on show (two 입기 in a row say the last one).
            snackbar.currentSnackbarData?.dismiss()
            launch {
                val undo = n.undo
                val r = if (undo == null) snackbar.showSnackbar(n.text, withDismissAction = true, duration = SnackbarDuration.Short)
                else kotlinx.coroutines.withTimeoutOrNull(8_000) {
                    snackbar.showSnackbar(n.text, actionLabel = "되돌리기", duration = SnackbarDuration.Indefinite)
                } ?: SnackbarResult.Dismissed
                if (r == SnackbarResult.ActionPerformed && undo != null) {
                    val ok = runCatching { kotlinx.coroutines.withContext(c.appScope.coroutineContext) { undo() } }.getOrDefault(false)
                    snackbar.showSnackbar(if (ok) "되돌렸어요" else "되돌리지 못했어요", duration = SnackbarDuration.Short)
                }
            }
        }
    }
}
