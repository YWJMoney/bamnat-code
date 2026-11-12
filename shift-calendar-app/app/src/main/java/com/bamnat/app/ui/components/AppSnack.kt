package com.bamnat.app.ui.components

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAccessibilityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVP-23] 뿌리 고침 4 (검토 UIX-44·I13ba-02·U13b-10·U13-22, 디자인 품질 조건 7·10): the app's one line at the bottom.
 *
 * Before, ten places shared one [SnackbarHostState] and sixteen screens had one of their own, each in its own line: a 되돌리기 that
 * came behind a long line ('캘린더에 새 일정', '부탁하신 꾸미기가 풀렸어요' that stayed until pressed) ran out its 8 seconds unseen,
 * and a memo's 되돌리기 could come up minutes later. Now every line waits in one queue:
 *
 * - 되돌리기 first ([Kind.UNDO]): it takes the place of whatever shows, at once; the line it pushed aside comes back after it.
 *   The newest 되돌리기 goes first, as it is about the last thing done. Then a failure ([Kind.FAIL]), then the rest ([Kind.INFO]).
 * - Time counts from when a line shows: 되돌리기 and a line with a button 8 s, others 4 s ([Line.millis]); the phone's
 *   accessibility settings may give longer (TalkBack).
 * - The same words never wait twice; a line that could not show for a minute (30 s for 되돌리기) goes.
 * - It shows on the topmost [AppSnackHost]: a sheet's over the app's, a tool screen's over the tabs'; it moves there when one
 *   opens or closes, so a 되돌리기 made in a sheet that closes at once is still seen.
 *
 * New code calls [show] (or [ask] to wait for the answer). Screens that still take a [SnackbarHostState] get one from
 * [rememberLane]: what they show on it goes through the same queue. 토스트는 새로 쓰지 않아요(`DesignRulesTest`).
 */
object AppSnack {
    enum class Kind { UNDO, FAIL, INFO }

    const val UNDO_MILLIS = 8_000L
    const val ACTION_MILLIS = 8_000L
    const val LONG_MILLIS = 6_000L
    const val SHORT_MILLIS = 4_000L
    /** How long a line may wait unseen before it goes (a 되돌리기 shorter: undoing something long past surprises). */
    const val STALE_MILLIS = 60_000L
    const val STALE_UNDO_MILLIS = 30_000L

    internal val UNDO_WORDS = setOf("되돌리기", "되살리기")

    /** One line: what it says, its button, and its answer (true when the button was pressed). */
    internal class Line(
        val message: String,
        val action: String?,
        val kind: Kind,
        val millis: Long,
        val tag: String?,
        val queuedAt: Long,
        val order: Long,
        val withDismissAction: Boolean = false,
    ) {
        val result = CompletableDeferred<Boolean>()
        /** Called once, when the line first shows (what is told is marked told then, not before). */
        @Volatile var onShown: (() -> Unit)? = null
        @Volatile var withdrawn = false
        var interruptions = 0
        val key: String get() = tag ?: "$message|$action"
    }

    internal val queue = SnackQueue()
    internal val hosts = mutableStateListOf<SnackbarHostState>()
    private var clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }
    private val scope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }

    /** True while some screen shows the line (before the first setup's own host, nothing does). */
    val hasHost: Boolean get() = hosts.isNotEmpty()

    /** The line on screen now, if any (for tests and for a screen that must close it). */
    val current: androidx.compose.material3.SnackbarData? get() = hosts.lastOrNull()?.currentSnackbarData

    /**
     * Says [message]. With [undo], the line has 되돌리기 (8 s, first in the queue) and [undo] runs when it is pressed; [action] with
     * [onAction] is another button. Fire and forget: it never waits.
     */
    fun show(
        message: String,
        undo: (suspend () -> Unit)? = null,
        kind: Kind = if (undo != null) Kind.UNDO else Kind.INFO,
        action: String? = if (undo != null) "되돌리기" else null,
        onAction: (() -> Unit)? = null,
        tag: String? = null,
        millis: Long? = null,
    ) {
        val line = add(message, action, kind, tag, millis)
        if (undo == null && onAction == null) return
        scope.launch {
            if (line.result.await()) {
                onAction?.invoke()
                undo?.let { runCatching { it() } }
            }
        }
    }

    /**
     * 2.0.0 [RVK-42] (검토 MONEY-14·U06a-14·U06a-19) A view model's [com.bamnat.app.ui.UndoMessage], straight into the queue: a 되돌리기
     * takes the place of the line on show at once, so the one seen is always about the last thing done, and [run] does it when pressed.
     * (가계부·받을 돈 collected them one by one onto a lane: each waited for the one before it to run out, and a fifth was dropped.)
     * A button that is not 되돌리기 ('바꾸기') waits its turn as any line with a button.
     */
    fun post(msg: com.bamnat.app.ui.UndoMessage, run: (com.bamnat.app.ui.UndoMessage) -> Unit) {
        if (msg.undo == null) show(msg.message, millis = if (msg.long) LONG_MILLIS else null)
        else show(msg.message, undo = { run(msg) }, kind = if (msg.actionLabel in UNDO_WORDS) Kind.UNDO else Kind.INFO, action = msg.actionLabel)
    }

    /** Says [message] and waits: true when [action] was pressed, false when the line went (timed out, pushed out, or never shown). */
    suspend fun ask(
        message: String,
        action: String? = null,
        kind: Kind = if (action in UNDO_WORDS) Kind.UNDO else Kind.INFO,
        tag: String? = null,
        millis: Long? = null,
        long: Boolean = false,
        withDismissAction: Boolean = false,
        onShown: (() -> Unit)? = null,
    ): Boolean {
        val line = add(message, action, kind, tag, millis, long, withDismissAction)
        if (onShown != null && line.onShown == null) line.onShown = onShown
        return try {
            coroutineScope {
                // A line nobody could show (no screen with a host) goes after its wait, so the caller is not held forever.
                val stale = launch { delay(staleMillis(kind)); queue.dropIfWaiting(line) }
                line.result.await().also { stale.cancel() }
            }
        } catch (e: CancellationException) {
            queue.withdraw(line)
            throw e
        }
    }

    private fun add(message: String, action: String?, kind: Kind, tag: String?, millis: Long?, long: Boolean = false, withDismissAction: Boolean = false): Line =
        queue.add(Line(message, action, kind, millis ?: millisOf(kind, action, long), tag, clock(), queue.nextOrder(), withDismissAction))

    internal fun millisOf(kind: Kind, action: String?, long: Boolean): Long = when {
        kind == Kind.UNDO -> UNDO_MILLIS
        action != null -> ACTION_MILLIS
        long -> LONG_MILLIS
        else -> SHORT_MILLIS
    }

    internal fun staleMillis(kind: Kind) = if (kind == Kind.UNDO) STALE_UNDO_MILLIS else STALE_MILLIS

    /**
     * A [SnackbarHostState] for a screen that still calls `showSnackbar` on one: what it shows goes through the queue, with
     * 되돌리기 lines first. Nothing draws it; the line shows on the topmost [AppSnackHost].
     */
    @Composable
    fun rememberLane(): SnackbarHostState {
        val lane = remember { SnackbarHostState() }
        LaunchedEffect(lane) {
            snapshotFlow { lane.currentSnackbarData }.collectLatest { data ->
                if (data == null) return@collectLatest
                val v = data.visuals
                // A newer line on the same lane, or the screen taking its line back, cancels this wait (collectLatest).
                val pressed = ask(v.message, v.actionLabel, long = v.duration != SnackbarDuration.Short, withDismissAction = v.withDismissAction)
                if (pressed) data.performAction() else data.dismiss()
            }
        }
        return lane
    }

    /** Shows lines on [host] until it is no longer the topmost one (then the line on show moves to the next). */
    internal suspend fun serve(host: SnackbarHostState, timeout: (Line) -> Long) {
        while (true) {
            val line = awaitNext()
            var pushed = false
            try {
                val pressed = coroutineScope {
                    val watch = launch {
                        queue.changes.first { queue.shouldStop(line) }
                        pushed = true
                        host.currentSnackbarData?.dismiss()
                    }
                    val timer = launch { delay(timeout(line)); host.currentSnackbarData?.dismiss() }
                    line.onShown?.let { line.onShown = null; runCatching { it() } }
                    val r = host.showSnackbar(Visuals(line)) == SnackbarResult.ActionPerformed
                    watch.cancel(); timer.cancel()
                    r
                }
                when {
                    pressed -> queue.finish(line, true)
                    pushed && !line.withdrawn -> queue.putBack(line, interrupted = true)
                    else -> queue.finish(line, false)
                }
            } catch (e: CancellationException) {
                // The host went (a sheet closed): the line shows again on the one under it.
                if (!line.result.isCompleted) queue.putBack(line, interrupted = false)
                throw e
            }
        }
    }

    private suspend fun awaitNext(): Line {
        while (true) {
            queue.pickNext(clock())?.let { return it }
            queue.changes.first { queue.hasWaiting() }
        }
    }

    private class Visuals(line: Line) : SnackbarVisuals {
        override val message = line.message
        override val actionLabel = line.action
        override val withDismissAction = line.withDismissAction
        override val duration = SnackbarDuration.Indefinite
    }

    /** For tests: an empty queue and a clock of their own. */
    internal fun resetForTest(now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
        queue.clear()
        clock = now
    }
}

/**
 * The waiting lines, in order: 되돌리기 (newest first), then failures, then the rest (oldest first). Thread-safe; [changes] ticks on
 * every change so a host can wait for the next line or for a reason to stop the one on show.
 */
internal class SnackQueue {
    private val waiting = mutableListOf<AppSnack.Line>()
    private var showing: AppSnack.Line? = null
    private var order = 0L
    val changes = MutableStateFlow(0L)

    @Synchronized fun nextOrder(): Long = ++order

    /** Adds [line], or gives back the same line already waiting or on show (same words, or same tag), except a 되돌리기 with no tag. */
    fun add(line: AppSnack.Line): AppSnack.Line {
        synchronized(this) {
            if (line.kind != AppSnack.Kind.UNDO || line.tag != null) {
                val same = (waiting + listOfNotNull(showing)).firstOrNull { it.key == line.key && !it.result.isCompleted }
                if (same != null) return same
            }
            waiting += line
        }
        tick()
        return line
    }

    /** The next line to show, taken out of the queue; lines that waited too long go first. */
    fun pickNext(now: Long): AppSnack.Line? {
        val stale = mutableListOf<AppSnack.Line>()
        val next = synchronized(this) {
            waiting.filter { now - it.queuedAt > AppSnack.staleMillis(it.kind) }.forEach { waiting -= it; stale += it }
            waiting.minWithOrNull(compareBy<AppSnack.Line> { it.kind.ordinal }.thenBy { if (it.kind == AppSnack.Kind.UNDO) -it.order else it.order })
                ?.also { waiting -= it; showing = it }
        }
        stale.forEach { it.result.complete(false) }
        return next
    }

    /** The line on show should give way: taken back, or a line that goes before it is waiting. */
    fun shouldStop(line: AppSnack.Line): Boolean = synchronized(this) { line.withdrawn || waiting.any { outranks(it, line) } }

    fun hasWaiting(): Boolean = synchronized(this) { waiting.isNotEmpty() }

    fun isWaiting(line: AppSnack.Line): Boolean = synchronized(this) { line in waiting }

    fun finish(line: AppSnack.Line, pressed: Boolean) {
        synchronized(this) { if (showing === line) showing = null; waiting -= line }
        line.result.complete(pressed)
        tick()
    }

    /** Back in the queue (pushed aside, or its host went); a line pushed aside three times goes. */
    fun putBack(line: AppSnack.Line, interrupted: Boolean) {
        var gone = false
        synchronized(this) {
            if (showing === line) showing = null
            if (interrupted) line.interruptions++
            if (line.interruptions > 2 || line.withdrawn) gone = true else if (line !in waiting) waiting += line
        }
        if (gone) line.result.complete(false)
        tick()
    }

    /** The caller does not want it any more (its screen went, or it said something newer). */
    fun withdraw(line: AppSnack.Line) {
        line.withdrawn = true
        synchronized(this) { waiting -= line }
        line.result.complete(false)
        tick()
    }

    fun dropIfWaiting(line: AppSnack.Line) {
        if (synchronized(this) { waiting.remove(line) }) { line.result.complete(false); tick() }
    }

    fun clear() {
        val all = synchronized(this) { (waiting + listOfNotNull(showing)).also { waiting.clear(); showing = null } }
        all.forEach { it.result.complete(false) }
        tick()
    }

    private fun tick() { changes.value = changes.value + 1 }

    companion object {
        fun outranks(a: AppSnack.Line, b: AppSnack.Line): Boolean =
            a.kind.ordinal < b.kind.ordinal || (a.kind == AppSnack.Kind.UNDO && b.kind == AppSnack.Kind.UNDO && a.order > b.order)
    }
}

/**
 * Where [AppSnack]'s line shows: in place of `SnackbarHost(state)`. The topmost one on screen shows it (a sheet's over the app's).
 */
@Composable
fun AppSnackHost(modifier: Modifier = Modifier) {
    val state = remember { SnackbarHostState() }
    DisposableEffect(state) {
        AppSnack.hosts += state
        onDispose { AppSnack.hosts -= state }
    }
    val a11y = LocalAccessibilityManager.current
    if (AppSnack.hosts.lastOrNull() === state) LaunchedEffect(state) {
        AppSnack.serve(state) { line ->
            a11y?.calculateRecommendedTimeoutMillis(line.millis, containsIcons = false, containsText = true, containsControls = line.action != null)
                ?: line.millis
        }
    }
    SnackbarHost(state, modifier)
}
