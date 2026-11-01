package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.fillMaxWidth
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch

/**
 * 1.36 누락 점검 N13 (2차 보고서 4장 바퀴 1번): 찾아도 없을 때 '없다고 알리기' 한 번. 그 말과 어디서 찾았는지만 가고,
 * 휴대폰·설치 번호는 가지 않아요. 운영자는 2번 이상 모인 말만 손잡이 지도에서 봐요. 저절로 보내지 않고 누를 때만.
 */
@Composable
internal fun SearchMissRow(place: String, query: String, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    if (!c.recipeApi.enabled || query.isBlank()) return
    val scope = rememberCoroutineScope()
    var sent by remember(query) { mutableStateOf<Boolean?>(null) }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            when (sent) {
                true -> "알렸어요. 같은 말이 모이면 먼저 살펴봐요"
                false -> "지금은 보내지 못했어요. 잠시 뒤에 다시 눌러 주세요"
                null -> "찾는 게 없나요? 누가 찾았는지는 빼고 이 말만 알려요"
            },
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
        )
        if (sent != true) TextButton(onClick = { scope.launch { sent = c.recipeApi.searchMiss(place, query) } }) { Text("없다고 알리기") }
    }
}
