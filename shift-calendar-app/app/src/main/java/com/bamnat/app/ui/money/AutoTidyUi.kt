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
internal fun AutoTidyLine(area: String, calendarId: String?, modifier: Modifier = Modifier, match: (AutoTidy.Item) -> Boolean = { true }) {
    val box = LocalContext.current.appContainer.autoTidy
    val all by box.items.collectAsStateWithLifecycle()
    // 2.0.0 [RVH-57] [match]: one received link's days under that link only.
    val mine = remember(all, area, calendarId) { all.filter { it.area == area && (calendarId == null || it.calendarId == calendarId) } }.filter(match)
    var open by rememberSaveable { mutableStateOf(false) }
    if (mine.isEmpty()) return
    TextButton(onClick = { open = true }, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.Inventory2, null, Modifier.size(18.dp))
        androidx.compose.foundation.layout.Spacer(Modifier.size(8.dp))
        Text("자동으로 정리한 것 ${mine.size}건 보기")
    }
    if (open) AutoTidySheet(mine, box, tidyWords(area), onDismiss = { open = false })
}

/**
 * 2.0.0 [RVH-57] What the list says, in each area's words (조건 13): what it is under the title, the button that lets it be and the
 * one that puts it back. The money words (one payment written once) mean nothing for a day a received roster changed, and
 * [RVM-26] RCP-08 the 내 서랍 one holds themes the 입었던 테마 limit let go, not payments.
 */
private class TidyWords(val intro: String, val keep: String, val back: String, val done: (String) -> String, val failed: String)

private fun restored(title: String) = "‘$title’${com.bamnat.core.text.Josa.eulreul(title)} 되살렸어요"

private fun tidyWords(area: String) = when (area) {
    com.bamnat.app.share.RosterFollower.TIDY_AREA -> TidyWords(
        intro = "근무표대로 하려면 '근무표대로'를 눌러요.",
        keep = "그대로 둘게요", back = "근무표대로",
        done = { "‘$it’${com.bamnat.core.text.Josa.eulreul(it)} 근무표대로 넣었어요" }, failed = "넣지 못했어요. 다시 해 주세요",
    )
    AutoTidy.AREA_RECIPE -> TidyWords(
        intro = "입었던 테마가 ${com.bamnat.core.recipe.ThemeLease.HISTORY}개를 넘어 서랍에서 뺀 것이에요. 다시 두려면 되살려요.",
        keep = "맞아요", back = "되살리기", done = ::restored, failed = "되살리지 못했어요. 다시 해 주세요",
    )
    else -> TidyWords(
        intro = "같은 결제로 보고 한 번만 적었거나 하나로 합친 것이에요. 다르면 되살려요.",
        keep = "맞아요", back = "되살리기", done = ::restored, failed = "되살리지 못했어요. 다시 해 주세요",
    )
}

/** What the sheet says it holds, under its title ([RVM-26] the 내 서랍 one: themes, not payments). */
internal fun tidyAbout(area: String): String = tidyWords(area).intro

/** [RVK-13] What the app left out or joined, newest first, each with why and '되살리기' (or '맞아요' to let it go). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AutoTidySheet(items: List<AutoTidy.Item>, box: AutoTidy, words: TidyWords, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var said by remember { mutableStateOf<String?>(null) }
    val now = Today.millis()
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp)) {
            Text("자동으로 정리한 것", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(said ?: words.intro,
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
                        val left = item.daysLeft(now)
                        // 총괄 #457 리뷰 (조건 12): when the day and the two buttons do not fit on one line, the buttons go under it.
                        WordsWithActions({
                            Text("${Fmt.monthDay(at.toLocalDate())} ${Fmt.time(at.toLocalTime())} · ${left}일 남음",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }) {
                            TextButton(onClick = { scope.launch { box.forget(item.id) } }) { Text(words.keep) }
                            TextButton(onClick = {
                                scope.launch {
                                    said = if (box.restore(item.id)) words.done(item.title) else words.failed
                                }
                            }) { Text(words.back) }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
