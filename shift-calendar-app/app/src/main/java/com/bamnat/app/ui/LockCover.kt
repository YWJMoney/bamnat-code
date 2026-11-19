package com.bamnat.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.review.InAppReview.findActivity

/** [LOCK-01] What shows while 앱 잠금 holds: nothing of the user's, and the way to unlock, asked once at once. */
@Composable
fun LockCover(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    // Back leaves the app; it never moves the screens under the cover.
    val activity = LocalContext.current.findActivity()
    BackHandler { activity?.moveTaskToBack(true) }
    // 1.39.1 [R391-10] (검토 UIX-41) A window over every 창 and 시트 left open (급여 대장, 돈 적기), made again on top when one opens
    // while locked; drawn in the activity, the cover was under them.
    val opened by com.bamnat.app.AppLock.opened.collectAsState()
    key(opened) {
        com.bamnat.app.ui.components.LockWindow(onBack = { activity?.moveTaskToBack(true) }) { CoverBody(onUnlock) }
    }
}

@Composable
private fun CoverBody(onUnlock: () -> Unit) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
            Text("밤낮이 잠겨 있어요", style = MaterialTheme.typography.titleLarge)
            Text("휴대폰 잠금과 같은 방법(지문·얼굴·PIN)으로 풀어요. 알람은 잠겨 있어도 그대로 울려요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Button(onClick = onUnlock, modifier = Modifier.heightIn(min = 52.dp)) { Text("잠금 풀기") }
        }
    }
}
