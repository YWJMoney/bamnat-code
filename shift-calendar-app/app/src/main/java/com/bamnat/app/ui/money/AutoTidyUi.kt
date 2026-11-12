package com.bamnat.app.ui.money

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.alarm.AlarmScheduler
import com.bamnat.app.appContainer
import com.bamnat.app.data.AutoTidy
import com.bamnat.app.ui.Fmt
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVK-13] 뿌리 7 (검토 U06b-13·I06ba-06): '자동으로 정리한 것 N건 보기', one line under what the app wrote on its own. Nothing
 * shows when nothing was left out or joined in the last 30 days.
 */
@Composable
internal fun AutoTidyLine(area: String, calendarId: String?, modifier: Modifier = Modifier) {
    val box = LocalContext.current.appContainer.autoTidy
    val all by box.items.collectAsStateWithLifecycle()
    val mine = remember(all, area, calendarId) { all.filter { it.area == area && (calendarId == null || it.calendarId == calendarId) } }
    var open by rememberSaveable { mutableStateOf(false) }
    if (mine.isEmpty()) return
    TextButton(onClick = { open = true }, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text("자동으로 정리한 것 ${mine.size}건 보기")
    }
    if (open) AutoTidySheet(mine, box, onDismiss = { open = false })
}

/** [RVK-13] What the app left out or joined, newest first, each with why and '되살리기' (or '맞아요' to let it go). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AutoTidySheet(items: List<AutoTidy.Item>, box: AutoTidy, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var said by remember { mutableStateOf<String?>(null) }
    val now = Today.millis()
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("자동으로 정리한 것", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(said ?: "같은 결제로 보고 한 번만 적었거나 하나로 합친 것이에요. 다르면 되살려요.",
                style = MaterialTheme.typography.bodySmall,
                color = if (said != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp))
            if (items.isEmpty()) {
                com.bamnat.app.ui.components.EmptyState("정리한 것이 더 없어요")
            }
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
                items(items, key = { it.id }) { item ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(item.title, style = MaterialTheme.typography.titleSmall)
                        Text(item.why, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        val at = AlarmScheduler.millisToLocal(item.at)
                        val left = ((item.at + AutoTidy.KEEP_MILLIS - now) / (24 * 60 * 60 * 1000L) + 1).coerceAtLeast(1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${Fmt.monthDay(at.toLocalDate())} ${Fmt.time(at.toLocalTime())} · ${left}일 남음",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { scope.launch { box.forget(item.id) } }) { Text("맞아요") }
                            TextButton(onClick = {
                                scope.launch {
                                    said = if (box.restore(item.id)) "‘${item.title}’을 되살렸어요" else "되살리지 못했어요. 다시 해 주세요"
                                }
                            }) { Text("되살리기") }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
