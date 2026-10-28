package com.bamnat.app.ui.guide

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.GuideStore

/**
 * U 1.32 [UX132-07] (조사 4.6, NN/g "한 번에 하나, 알맞은 때"): a one-line tip where the thing is, shown until × once.
 * 기능 켜고 끄기 › 알맞은 때 한 줄 안내 off hides these too.
 */
@Composable
internal fun OnceTip(key: String, text: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val hints = remember { com.bamnat.app.ui.calendar.HintStore(context) }
    var shown by remember { mutableStateOf(!hints.done(key)) }
    if (!shown) return
    val tipsOff = remember { GuideStore.get(context).isOff("guide.tips", com.bamnat.app.data.PersonalCalendar.MAIN) }
    if (tipsOff) return
    val scheme = MaterialTheme.colorScheme
    Surface(shape = MaterialTheme.shapes.large, color = scheme.secondaryContainer, contentColor = scheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 14.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Lightbulb, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { hints.markDone(key); shown = false }) { Icon(Icons.Filled.Close, "안내 닫기") }
        }
    }
}
