package com.bamnat.app.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.WhatsNew

/**
 * [GUIDE-08] Once after an update: what is new, each line with 열기 when it is a feature, and a way to say
 * what is still missing. [onOpen] closes the dialog and opens the feature where it lives.
 */
@Composable
fun WhatsNewDialog(
    releases: List<WhatsNew.Release>,
    onOpen: (String) -> Unit,
    onAsk: () -> Unit,
    onDismiss: () -> Unit,
    recommendOff: Int = 0,
    onRecommend: () -> Unit = {},
    /** 1.38 [EASE-32] What this update fixed from the user's own questions, one short line each. */
    thanks: List<String> = emptyList(),
    onOpenAsks: () -> Unit = {},
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        // 1.38 PC 4a5c3828: from 1.36.1 two versions show at once, each with its own heading, so the title stays plain.
        title = { Text(if (releases.size > 1) "새로 생긴 것" else "${releases.first().versionName}에서 새로 생긴 것") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 1.38 [EASE-32] Said first: the user asked, and this version is the answer.
                if (thanks.isNotEmpty()) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 10.dp, end = 4.dp, bottom = 2.dp)) {
                            Text("보내 주신 의견으로 고쳤어요", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            thanks.take(3).forEach { t ->
                                Text("• ‘$t’", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(top = 2.dp, end = 8.dp))
                            }
                            if (thanks.size > 3) Text("그리고 ${thanks.size - 3}개 더", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text("알려 주셔서 고마워요. 이번 판에 들어 있어요.", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(top = 4.dp))
                            TextButton(onClick = onOpenAsks, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 문의 보기") }
                        }
                    }
                }
                // [ONBOARD-04] Once, for a phone set up before: the recommended features it has off.
                if (recommendOff > 0) {
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.secondaryContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("꺼져 있는 추천 기능 ${recommendOff}개를 켤까요? 켜 둬도 괜찮은 것만 골랐어요.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.weight(1f).padding(vertical = 8.dp))
                            TextButton(onClick = onRecommend, modifier = Modifier.heightIn(min = 48.dp)) { Text("켜러 가기") }
                        }
                    }
                }
                releases.forEach { release ->
                    if (releases.size > 1) Text(release.versionName, style = MaterialTheme.typography.titleSmall)
                    release.lines.forEach { line ->
                        val feature = line.featureId?.let(FeatureRegistry::get)
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("• " + line.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            if (feature != null) {
                                TextButton(onClick = { onOpen(feature.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("열기") }
                            }
                        }
                    }
                }
                Text("써 보고 불편하거나 바라는 점이 있으면 바로 알려 주세요. 개발자가 직접 읽고 답해요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp))
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("확인") } },
        dismissButton = { TextButton(onClick = onAsk) { Text("의견 보내기") } },
    )
}
