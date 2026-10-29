package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * [J133-56] 1.33 (실사용 테스트 "저장 단추를 누르고 2~3초 동안 아무 표시가 없어서 다시 누르게 돼요"): the button says it is
 * working the moment it is tapped, and only then does the work, a frame later, so the spinner is on screen even when what
 * follows keeps the screen busy. A second tap meanwhile does nothing.
 */
@Composable
fun SaveButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busyText: String = "저장하는 중…",
    content: @Composable RowScope.() -> Unit,
) {
    var busy by remember { mutableStateOf(false) }
    val click by rememberUpdatedState(onClick)
    LaunchedEffect(busy) {
        if (!busy) return@LaunchedEffect
        withFrameNanos { }
        withFrameNanos { }
        click()
        // The screen usually goes on from here; one that stays takes taps again after a moment.
        kotlinx.coroutines.delay(1200)
        busy = false
    }
    Button(onClick = { if (!busy) busy = true }, enabled = enabled && !busy, modifier = modifier) {
        if (busy) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = LocalContentColor.current)
            Spacer(Modifier.width(8.dp))
            Text(busyText)
        } else content()
    }
}
