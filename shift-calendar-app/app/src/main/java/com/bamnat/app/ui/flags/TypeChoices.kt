package com.bamnat.app.ui.flags

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.flags.Flags
import com.bamnat.core.flags.TypeExposure
import com.bamnat.core.work.WorkStyle

/**
 * [FLAG-12][FLAG-13] 1.28 AL: the kinds of work a place where a kind is newly picked shows, by the `type.*` rows. A hidden
 * kind is left out, except on an approved admin phone the developer app gave its 미리 보기 (S 1.32 [SECX-01]), where it shows marked
 * [TypeExposure.HIDDEN_MARK]. [keep] (the kind in use or already picked) always stays. Calendars, backups and company
 * files of a hidden kind are not touched: only these lists read it.
 */
@Composable
fun typeChoices(keep: WorkStyle? = null): TypeShown {
    val c = LocalContext.current.appContainer
    val rows by c.flags.byKey.collectAsStateWithLifecycle()
    val pro by c.proStore.state.collectAsStateWithLifecycle()
    val previews = remember(pro, rows) { c.phoneFacts().let { if (it.admin) it.previews else emptySet() } }
    return remember(rows, previews, keep) { TypeShown(Flags.types(rows, previews, keep)) }
}

/** The kinds a list shows, each with whether it is hidden for everyone else. */
class TypeShown(val choices: List<TypeExposure.Choice>) {
    private val byStyle = choices.associate { it.style to it.hidden }
    fun shows(s: WorkStyle): Boolean = s in byStyle
    fun hidden(s: WorkStyle): Boolean = byStyle[s] == true

    /** [subtitle] with the owner's mark in front when [s] is hidden. */
    fun mark(s: WorkStyle, subtitle: String): String = if (hidden(s)) "${TypeExposure.HIDDEN_MARK} · $subtitle" else subtitle

    companion object {
        val ALL = TypeShown(TypeExposure.ALL)
    }
}
