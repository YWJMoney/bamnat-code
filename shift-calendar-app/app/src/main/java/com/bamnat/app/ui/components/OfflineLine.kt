package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVP-03] (검토 UIX-14) The three things a screen can say when the server cannot be reached, said the same way everywhere
 * (there were 40 ways). A screen that adds its own first half ("링크를 못 만들었어요. ") ends with one of these.
 */
object NetWords {
    /** No connection at all. */
    const val OFFLINE = "인터넷에 연결되지 않았어요. 연결되면 다시 해 주세요."
    /** The server answered late or not at all. */
    const val BUSY = "인터넷이 없거나 서버가 바빠요. 잠시 뒤 다시 해 주세요."
    /** It took too long. */
    const val SLOW = "인터넷이 느려 받지 못했어요. 다시 해 주세요."
    /** The tail a sentence ends with when it will be tried again by hand. */
    const val AGAIN = "인터넷에 연결되면 다시 해 주세요."
}

/**
 * 2.0.0 [RVP-03] (검토 UIX-14, 더할 것 I13a-02) The one line a screen shows when it could not reach the server: a cloud with a line
 * through it, the words ([NetWords.OFFLINE] unless the screen knows better) and '다시 하기'. It is in the app, so it follows the
 * app's 글자 크기 and 꾸미기, unlike a toast, and it can carry the retry. Inside a list it takes the place of the list.
 */
@Composable
fun OfflineLine(
    modifier: Modifier = Modifier,
    text: String = NetWords.OFFLINE,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = 4.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        if (onRetry != null) TextButton(onClick = onRetry) { Text("다시 하기") }
    }
}
