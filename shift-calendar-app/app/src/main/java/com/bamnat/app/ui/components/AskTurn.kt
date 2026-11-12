package com.bamnat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.bamnat.core.guide.OpenAsk

/**
 * 2.0.0 [RVP-22] The opening windows' turns ([OpenAsk.pick]): each window that comes up by itself when the app opens calls [turn]
 * just before it would draw, and draws only when it gets it. One at a time, one per visit, never over a sheet or writing ([Busy]),
 * the law first. A visit starts when the process starts and when the app comes back after 30 minutes or a new day
 * ([com.bamnat.app.HomeRequests], [newVisit]).
 */
object AskTurn {
    private val wanting = mutableStateMapOf<OpenAsk, Boolean>()
    private val shown = mutableStateMapOf<OpenAsk, Unit>()
    private var current by mutableStateOf<OpenAsk?>(null)

    /** The window to show now (snapshot state). */
    val now: OpenAsk? get() = OpenAsk.pick(wanting.map { (a, asked) -> OpenAsk.Want(a, asked) }, current, shown.keys, Busy.now)

    /**
     * True when [ask] may show now. Call it only where the window would draw (after its own conditions): leaving the composition
     * (answered, closed) gives the turn back. [asked]: the user asked for it (a notice's 새 기능 보기).
     */
    @Composable
    fun turn(ask: OpenAsk, asked: Boolean = false): Boolean {
        DisposableEffect(ask, asked) {
            wanting[ask] = asked
            onDispose {
                wanting.remove(ask)
                if (current == ask) current = null
            }
        }
        val mine = now == ask
        // Taken in the same frame as the window opens, before its own window counts as busy.
        if (mine) SideEffect { if (current != ask) { current = ask; shown[ask] = Unit } }
        return mine
    }

    /** The app opened again (30 minutes away, or a new day): the next window may come. */
    fun newVisit() {
        shown.clear()
    }

    internal fun resetForTest() {
        wanting.clear(); shown.clear(); current = null
    }
}
