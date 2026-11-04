package com.bamnat.app.ui.components

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [CNV-13] 1.37 펼쳐 보기 (사용자 2026-11-04 "좌우로 스크롤인데 펼쳐서 볼수도 있게"): a row of chips or colours that slides sideways, with
 * ‘펼치기 ▾’ at its end when it does not fit; open, every item shows at once over as many lines as it needs, ‘접기 ▴’ last.
 * Open or folded is kept per [key], so a row opened once stays open the next time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoldRow(key: String, modifier: Modifier = Modifier, spacing: Dp = 6.dp, what: String = "", content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    var open by rememberSaveable(key) { mutableStateOf(foldOpen(ctx, key)) }
    fun set(v: Boolean) { open = v; setFoldOpen(ctx, key, v) }
    if (open) {
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
            FoldButton("접기 ▴", "${what.ifEmpty { "이 줄" }} 한 줄로 접기") { set(false) }
        }
    } else {
        val scroll = rememberScrollState()
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically) { content() }
            if (scroll.maxValue > 0) FoldButton("펼치기 ▾", "${what.ifEmpty { "이 줄" }} 모두 펼쳐 보기") { set(true) }
        }
    }
}

/** The small word button that opens or folds a [FoldRow] (also used by 색 고르기, whose two rows fold together). */
@Composable
fun FoldButton(words: String, said: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = said }) {
        Text(words, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private const val PREFS = "fold_rows"

fun foldOpen(ctx: Context, key: String): Boolean =
    runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false) }.getOrDefault(false)

fun setFoldOpen(ctx: Context, key: String, v: Boolean) {
    runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, v).apply() }
}

/** For a caller that folds its own layout ([com.bamnat.app.ui.settings.ColorPick]): open or not, kept per [key]. */
@Composable
fun rememberFold(key: String): Pair<Boolean, (Boolean) -> Unit> {
    val ctx = LocalContext.current
    var open by rememberSaveable(key) { mutableStateOf(foldOpen(ctx, key)) }
    return remember(open) { open to { v: Boolean -> open = v; setFoldOpen(ctx, key, v) } }
}
