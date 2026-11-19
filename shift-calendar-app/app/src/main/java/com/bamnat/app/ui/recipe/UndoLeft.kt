package com.bamnat.app.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.ui.components.AppSheet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVM-55] 검토 U09b-02 (조건 10 '되돌리기는 그것이 바꾼 것만'): 되돌리기 leaves what the user changed since as they made it,
 * and said only how many. Its line now has 보기, which opens this: each place that stayed, why, and 이것도 되돌리기 for one the user
 * wants back as before the work after all (asked once in the row, since it takes away a change of their own). One the app cannot
 * take back from here (a shift that days use) says where it can.
 */
object UndoLeft {
    private val _open = MutableStateFlow<List<RecipePieces.Kept>?>(null)
    val open = _open.asStateFlow()

    fun show(items: List<RecipePieces.Kept>) { if (items.isNotEmpty()) _open.value = items }
    fun close() { _open.value = null }

    /** The 되돌리기 line's action, when something stayed. */
    const val SEE = "보기"
}

/** Where [UndoLeft] opens, once for the app (next to [com.bamnat.app.ui.PutOnNoticeHost]). */
@Composable
fun UndoLeftHost(c: AppContainer) {
    val items by UndoLeft.open.collectAsStateWithLifecycle()
    val list = items ?: return
    UndoLeftSheet(c, list, onDismiss = UndoLeft::close)
}

private enum class Step { ASK, DONE, FAILED }

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun UndoLeftSheet(c: AppContainer, items: List<RecipePieces.Kept>, onDismiss: () -> Unit) {
    val rows = remember(items) { mutableStateMapOf<Int, Step>() }
    val scope = rememberCoroutineScope()
    // Opened whole: a half-open sheet at 200% showed the title and one line, and the rest only by dragging it up.
    AppSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("그대로 둔 것 ${items.size}개", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("넣은 뒤 직접 바꾼 곳이라 남겨 뒀어요. 작품을 넣기 전으로 돌리고 싶은 것만 골라요",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            items.forEachIndexed { i, k ->
                HorizontalDivider()
                // The words above, what can be done under them (at 200% on a folded phone two buttons beside them left a word a line).
                Column(Modifier.fillMaxWidth()) {
                    Text(k.what, style = MaterialTheme.typography.bodyLarge)
                    Text(when (rows[i]) {
                        Step.ASK -> "내가 바꾼 값은 사라지고 넣기 전 값이 돼요"
                        Step.DONE -> "넣기 전으로 돌렸어요"
                        Step.FAILED -> "돌리지 못했어요. 한 번 더 눌러 주세요"
                        null -> k.why
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val back = k.back
                    if (back != null && rows[i] != Step.DONE) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically) {
                        if (rows[i] == Step.ASK) {
                            TextButton(onClick = { rows.remove(i) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("그만") }
                            TextButton(onClick = {
                                scope.launch {
                                    val ok = runCatching { kotlinx.coroutines.withContext(c.appScope.coroutineContext) { back() } }.isSuccess
                                    rows[i] = if (ok) Step.DONE else Step.FAILED
                                }
                            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("돌리기") }
                        } else TextButton(onClick = { rows[i] = Step.ASK }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이것도 되돌리기") }
                    }
                }
            }
        }
    }
}
