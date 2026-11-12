package com.bamnat.app.ui.components

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop

/**
 * 2.0.0 [RVP-24] 뿌리 고침 2·3 (검토 UIX-45·T13b-03·U13b-09, 디자인 품질 조건 10 '적던 것은 사라지지 않아요').
 *
 * [Busy] knows whether something is open over the screen (a sheet, a window) or something is being written and not saved. The app
 * then holds what would take the screen away: coming back after 30 minutes or a new day no longer jumps to the first tab over a
 * sheet being filled in (23:58 돈 적기 → 은행 앱 → 00:01), and the windows that come up when the app opens wait (OpenAsk).
 * Every [AppSheet], [AlertDialog] and [Dialog] of the app counts itself; [rememberCloseGuard] and [rememberDraft] count what is
 * typed.
 */
object Busy {
    private val windows = mutableIntStateOf(0)
    private val writing = mutableIntStateOf(0)

    /** A sheet or a window is open, or something typed is not saved yet. Snapshot state: reading it in composition follows it. */
    val now: Boolean get() = windows.intValue > 0 || writing.intValue > 0

    /** Only something typed and not saved. */
    val writingNow: Boolean get() = writing.intValue > 0

    /** Counts a sheet or window while it is in the composition (called by the app's sheet and dialog wrappers). */
    @Composable
    internal fun Window() {
        DisposableEffect(Unit) {
            windows.intValue++
            onDispose { windows.intValue-- }
        }
    }

    /** Counts unsaved writing while [on]. */
    @Composable
    fun Writing(on: Boolean) {
        if (!on) return
        DisposableEffect(Unit) {
            writing.intValue++
            onDispose { writing.intValue-- }
        }
    }

    internal fun resetForTest() { windows.intValue = 0; writing.intValue = 0 }
}

/** 뿌리 고침 3: the one way the app asks before typed words are thrown away (sheets, windows, whole screens say the same). */
@Composable
fun DiscardAsk(onKeep: () -> Unit, onDiscard: () -> Unit, what: String = "저장하지 않고 닫으면 지금 적은 것이 사라져요.") {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text("적은 내용을 버릴까요?") },
        text = { Text(what) },
        confirmButton = { TextButton(onClick = onDiscard) { Text("버리기") } },
        dismissButton = { TextButton(onClick = onKeep) { Text("계속 적기") } },
    )
}

/** What [rememberCloseGuard] gives a screen: [close] for its X or 닫기 (it asks first when something typed would be lost). */
@Stable
class CloseGuard internal constructor() {
    internal var asking by mutableStateOf(false)
    internal var unsaved by mutableStateOf(false)
    internal var leave: () -> Unit = {}

    fun close() { if (unsaved) asking = true else leave() }
}

/**
 * 뿌리 고침 3 (조건 10): a whole screen with typing asks once before 뒤로 or its X throws [unsaved] words away
 * ("적은 내용을 버릴까요?" 계속 적기 / 버리기). Put [CloseGuard.close] on the X; 뒤로 is caught here.
 */
@Composable
fun rememberCloseGuard(unsaved: Boolean, onLeave: () -> Unit): CloseGuard {
    val guard = remember { CloseGuard() }
    val leave by rememberUpdatedState(onLeave)
    guard.unsaved = unsaved
    guard.leave = { leave() }
    Busy.Writing(unsaved)
    BackHandler(enabled = unsaved) { guard.asking = true }
    if (guard.asking) DiscardAsk(onKeep = { guard.asking = false }, onDiscard = { guard.asking = false; leave() })
    return guard
}

/** [rememberCloseGuard] for a screen whose only way out is 뒤로. */
@Composable
fun AskBeforeClose(unsaved: Boolean, onLeave: () -> Unit) {
    rememberCloseGuard(unsaved, onLeave)
}

/**
 * 뿌리 고침 2 (조건 10): long writing (a memo, a question, a post) kept on the phone while it is being written, so it is still there
 * after the screen is made again, the app goes to the background and comes back the next day, or Android closes the app meanwhile.
 * [key] names what is written (a screen and its subject, never a date). [done] empties it once it is saved or sent; a draft left
 * for [DraftStore.KEEP_DAYS] days goes by itself. [restored] says the words came back, so the screen can say so in one line.
 */
@Stable
class Draft internal constructor(private val context: Context, val key: String, initial: String) {
    var text by mutableStateOf(initial)
    val restored: Boolean = initial.isNotBlank()
    internal var finished = false

    fun done() {
        finished = true
        text = ""
        DraftStore.clear(context, key)
    }
}

@Composable
fun rememberDraft(key: String): Draft {
    val context = LocalContext.current.applicationContext
    // rememberSaveable keeps it through a turn of the screen even before the store is written.
    val saved = rememberSaveable(key) { mutableStateOf<String?>(null) }
    val draft = remember(key) { Draft(context, key, saved.value ?: DraftStore.read(context, key)) }
    LaunchedEffect(draft) {
        snapshotFlow { draft.text }.drop(1).collectLatest { t ->
            saved.value = t
            delay(400)
            if (!draft.finished) DraftStore.write(context, key, t)
        }
    }
    DisposableEffect(draft) { onDispose { if (!draft.finished) DraftStore.write(context, key, draft.text) } }
    Busy.Writing(draft.text.isNotBlank())
    return draft
}

/** The drafts of [rememberDraft], on this phone only (SettingsFilesBackup.DEVICE_ONLY). */
object DraftStore {
    private const val PREFS = "drafts"
    const val KEEP_DAYS = 7L

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(context: Context, key: String, now: Long = com.bamnat.core.time.Today.millis()): String {
        val raw = prefs(context).getString(key, null) ?: return ""
        val at = raw.substringBefore('|').toLongOrNull() ?: 0L
        if (now - at > KEEP_DAYS * 86_400_000L) { clear(context, key); return "" }
        return raw.substringAfter('|')
    }

    fun write(context: Context, key: String, text: String, now: Long = com.bamnat.core.time.Today.millis()) {
        if (text.isBlank()) clear(context, key) else prefs(context).edit().putString(key, "$now|$text").apply()
    }

    fun clear(context: Context, key: String) { prefs(context).edit().remove(key).apply() }
}
