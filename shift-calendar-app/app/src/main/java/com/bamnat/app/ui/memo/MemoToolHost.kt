package com.bamnat.app.ui.memo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * [MPH-02][MPH-07] 1.38 (PC 확인 3dd61e00): the tools of a chosen photo or 자유 판 sit at the bottom of the editor, above the keyboard bar,
 * not under the photo. Under the photo they moved up and down as the photo grew, so the next tap hit the wrong button.
 */
@Stable
internal class MemoToolHost {
    private var owner: Any? = null
    var content by mutableStateOf<(@Composable () -> Unit)?>(null)
        private set

    fun show(who: Any, what: @Composable () -> Unit) { owner = who; content = what }
    fun hide(who: Any) { if (owner === who) { owner = null; content = null } }
}

internal val LocalMemoToolHost = staticCompositionLocalOf<MemoToolHost?> { null }

/** [content] while [shown]: at the editor's bottom when there is a host, else right here (a checklist, a sample). */
@Composable
internal fun MemoToolPanel(shown: Boolean, content: @Composable () -> Unit) {
    val host = LocalMemoToolHost.current
    if (host == null) { if (shown) content(); return }
    val me = remember { Any() }
    val latest by rememberUpdatedState(content)
    DisposableEffect(shown, host) {
        if (shown) host.show(me) { latest() }
        onDispose { host.hide(me) }
    }
}

/** Where the editor draws the host's tools: fixed, outside the scrolling memo. */
@Composable
internal fun MemoToolHostSlot(host: MemoToolHost) {
    val c = host.content ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) { c() }
}
