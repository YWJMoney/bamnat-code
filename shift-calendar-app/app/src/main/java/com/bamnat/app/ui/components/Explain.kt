package com.bamnat.app.ui.components

import android.content.Context
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Collections

/**
 * 2.0.0 [RVP-08] (검토 UIX-16·I13a-06·I13c-04·U13-10 '설명은 한 줄 + ⓘ', '한 번 읽으면 줄어드는 화면') A grey explanation that
 * shrinks as it is learned: the first time a screen is opened it is said in full, the second time in one line (its [summary],
 * or the first sentence) with ⓘ to open the rest, and from the third time only ⓘ, so 처음 1분은 친절하고 매일 1초는 조용해요.
 * How many times each one was seen stays on this phone only (no server, no backup: a new phone starts kind again), and
 * 설정 › 화면 › 설명 처음처럼 보기 ([ExplainSeen.reset]) brings every one back in full.
 *
 * [key] names the explanation; change it when the words change a lot so the new words are said in full once.
 */
@Composable
fun Explain(
    key: String,
    text: String,
    modifier: Modifier = Modifier,
    summary: String? = null,
    /** False keeps the one line for good (a switch's line says what it does); true lets it go down to ⓘ alone. */
    iconOnly: Boolean = true,
) {
    val context = LocalContext.current
    val preview = LocalInspectionMode.current
    // The look is fixed for the visit when the screen opens; the count goes up once a visit.
    val seen = remember(key) { if (preview) 0 else ExplainSeen.count(context, key) }
    LaunchedEffect(key) { if (!preview) ExplainSeen.saw(context, key) }
    var open by rememberSaveable(key) { mutableStateOf(false) }
    val short = summary ?: firstSentence(text)
    val ink = MaterialTheme.colorScheme.onSurfaceVariant
    val style = MaterialTheme.typography.bodySmall
    when {
        seen == 0 || open || short == text -> Text(text, style = style, color = ink, modifier = modifier.animateContentSize())
        else -> Row(
            modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                stateDescription = "접힘"
                onClick("설명 펼치기") { open = true; true }
            },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (seen == 1 || !iconOnly) Text(short, style = style, color = ink, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false))
            IconButton(onClick = { open = true }) {
                Icon(Icons.Outlined.Info, contentDescription = "설명 보기", tint = ink, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** [RVP-08] The first sentence of an explanation, for its one-line form. */
internal fun firstSentence(text: String): String {
    val t = text.trim()
    val m = Regex("""[.!?。](?=\s|$)|\n""").find(t) ?: return t
    val end = if (m.value == "\n") m.range.first else m.range.last + 1
    return t.substring(0, end).trim()
}

/** [RVP-08] How many times each explanation has been seen, on this phone. */
object ExplainSeen {
    private const val PREFS = "explain_seen"
    private val thisVisit = Collections.synchronizedSet(mutableSetOf<String>())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun count(context: Context, key: String): Int = prefs(context).getInt(key, 0)

    /** Counts a visit once while the app is open, however often the screen is drawn again. */
    fun saw(context: Context, key: String) {
        if (!thisVisit.add(key)) return
        val p = prefs(context)
        p.edit().putInt(key, (p.getInt(key, 0) + 1).coerceAtMost(9)).apply()
    }

    /** 설명 처음처럼 보기: every explanation is said in full again. */
    fun reset(context: Context) {
        prefs(context).edit().clear().apply()
        thisVisit.clear()
    }
}
