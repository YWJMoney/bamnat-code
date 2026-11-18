package com.bamnat.app.ui.company

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.bamnat.app.AppContainer
import com.bamnat.app.company.OutboxJob
import com.bamnat.app.company.SmallSends
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.app.ui.components.NetWords
import com.bamnat.app.ui.components.OfflineLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 2.0.0 [RVN-22] 검토 I10bb-01 (지하·공장 안처럼 신호가 약한 곳): what was pressed in the 광장 and 회사 screens and waits for a connection
 * (맞아요·달라요, 쓰고 있어요, 회사 빼기, 신고, 되돌리기, 나눌게요) as one line, "인터넷이 연결되면 보낼게요 · n개", with 지금 보내기. Nothing
 * waits: no line. It goes by itself once online ([OutboxJob]); the line is read again whenever the screen comes back.
 */
@Composable
fun WaitingSendsLine(c: AppContainer, modifier: Modifier = Modifier) {
    var n by remember { mutableIntStateOf(SmallSends.waitingCount(c)) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { n = SmallSends.waitingCount(c) }
    if (n <= 0) return
    OfflineLine(modifier, text = waitingLine(n), action = "지금 보내기", onRetry = {
        if (busy) return@OfflineLine
        busy = true
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { OutboxJob.send(c) } }
            val left = SmallSends.waitingCount(c)
            if (left > 0) AppSnack.show(NetWords.OFFLINE, kind = AppSnack.Kind.FAIL) else AppSnack.show("기다리던 ${n}개를 보냈어요")
            n = left
            busy = false
        }
    })
}

/** The line's words for [n] things waiting. */
internal fun waitingLine(n: Int) = "인터넷이 연결되면 보낼게요 · ${n}개"
