package com.bamnat.app.ui.guide

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.GuideStore
import com.bamnat.app.ui.components.OtherRow
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.guide.FitTable
import com.bamnat.core.work.WorkStyle

/** [FIT-02] The parts and tools the user added under '더 보기' to [calendar], as [com.bamnat.core.guide.FitItem.key]s. */
@Composable
fun rememberFitAdded(calendar: String): Set<String> {
    val context = LocalContext.current
    val state by remember { GuideStore.get(context) }.state.collectAsStateWithLifecycle()
    return remember(state.added, calendar) { state.addedTo(calendar) }
}

/** [FIT-02] 더하기 / 빼기 for the part [id] of [surface] on [calendar]. */
fun setFitAdded(context: Context, calendar: String, surface: FitSurface, id: String, added: Boolean) {
    val item = FitTable.item(surface, id) ?: return
    GuideStore.get(context).setAdded(calendar, item.key, added)
}

/**
 * [FIT-01] The rows '더 보기' offers on [surface] for [style]: the parts that do not suit it, named by [label]
 * and [what] (null for a part this screen does not have, which is left out).
 */
fun fitOthers(surface: FitSurface, style: WorkStyle?, added: Set<String>, label: (String) -> Pair<String, String>?): List<OtherRow> =
    FitTable.others(surface, style).mapNotNull { item -> label(item.id)?.let { (l, w) -> OtherRow(item.id, l, w, item.key in added) } }
