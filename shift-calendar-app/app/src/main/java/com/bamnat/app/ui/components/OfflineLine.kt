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
    // 2.0.0 [RVP-13] (조건 4·12, 장터를 못 읽은 360dp 폰 글자 200%: '장터는 아직 / 열리지 / 않았어요' 세 줄 옆에 '다시 하기'가 떨어져
    // 있었어요): where the words beside '다시 하기' would take more than two lines, '다시 하기' goes under them (as the nudge card does).
    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        val measurer = androidx.compose.ui.text.rememberTextMeasurer()
        val style = MaterialTheme.typography.bodyMedium
        val button = MaterialTheme.typography.labelLarge
        val density = androidx.compose.ui.platform.LocalDensity.current
        val stacked = onRetry != null && with(density) {
            val room = maxWidth - LEAD - (measurer.measure("다시 하기", button).size.width.toDp() + 24.dp)
            room <= 0.dp || measurer.measure(text, style, constraints = androidx.compose.ui.unit.Constraints(maxWidth = room.roundToPx())).lineCount > 2
        }
        val words: @Composable (Modifier) -> Unit = { m ->
            Row(m, verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(text, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (stacked) androidx.compose.foundation.layout.Column(Modifier.fillMaxWidth()) {
            words(Modifier.fillMaxWidth().heightIn(min = 48.dp))
            TextButton(onClick = onRetry!!, modifier = Modifier.padding(start = LEAD - 12.dp).heightIn(min = 48.dp)) { Text("다시 하기") }
        } else Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            words(Modifier.weight(1f))
            if (onRetry != null) TextButton(onClick = onRetry) { Text("다시 하기") }
        }
    }
}

/** The cloud and the space after it, which the words start after. */
private val LEAD = 30.dp
