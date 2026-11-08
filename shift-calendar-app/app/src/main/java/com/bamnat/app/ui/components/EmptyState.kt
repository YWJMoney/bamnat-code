package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * 1.38 [DSG-09] 빈 화면 (흐름·디자인 계획 4장): when a page has nothing yet, it says what will show here and gives one
 * button to start, instead of an empty chart or a bare "기록이 없어요". 칸이 points the way ([mood], 여기예요 by default)
 * unless 칸이 보이기 is off; then the words stand alone.
 */
@Composable
fun EmptyState(
    title: String,
    detail: String? = null,
    mood: KaniMood = KaniMood.Point,
    action: Pair<String, () -> Unit>? = null,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (LocalShowKani.current) Kani(mood, night = com.bamnat.app.ui.components.isDarkSurface(), size = 72.dp)
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        if (!detail.isNullOrEmpty()) Text(detail, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        action?.let { (label, onClick) -> Button(onClick = onClick, modifier = Modifier.padding(top = 4.dp)) { Text(label) } }
    }
}
