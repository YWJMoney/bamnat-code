package com.bamnat.app.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Close

/**
 * 2.0.0 [RVP-06] (검토 UIX-19·I13a-04·U13-03 '떠 있는 단추 규칙') Every floating "add" button follows one rule: it says its words
 * while the page is at rest or coming back up, and shrinks to its icon once the page scrolls down, so it never sits on a line
 * of words or the last row of the calendar. With big letters (글자 크기 [BigFontScale]배 이상) it is the icon alone from the start,
 * because the words would cover half the screen there. The words are always read out by TalkBack ([description]).
 */
@Composable
fun AddFab(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    open: Boolean = true,
    icon: ImageVector = Icons.Filled.Add,
    description: String = text,
) {
    ExtendedFloatingActionButton(
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text, maxLines = 1) },
        expanded = open && !bigLetters(),
        modifier = modifier.semantics { contentDescription = description },
    )
}

/** [RVP-06] The letter size from which a floating button keeps to its icon. */
const val BigFontScale = 1.3f

/** [RVP-06] True when the person reads with big letters, where wide buttons cover the page. */
@Composable
fun bigLetters(): Boolean = LocalDensity.current.fontScale >= BigFontScale

/**
 * [RVP-06] Room to leave under a list or the calendar so its last row can be scrolled clear of a floating button
 * (56dp button + 16dp margin + 16dp air).
 */
val FabRoom = 88.dp

/**
 * [RVP-06] Watches any scrolling child (a list, a column, a pager page) without knowing which: put [connection] on a parent with
 * `Modifier.nestedScroll(...)` and read [open]. Scrolling down shrinks the button; scrolling up, or bumping the top, opens it.
 */
@Stable
class FabOpen internal constructor(start: Boolean) {
    var open by mutableStateOf(start)
        internal set

    /** Opens the button again, for a new page or tab. */
    fun reset() { open = true }

    val connection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -1f) open = false else if (available.y > 1f) open = true
            return Offset.Zero
        }

        override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
            if (available.y > 0f) open = true
            return Velocity.Zero
        }
    }
}

/** [RVP-06] A [FabOpen] that keeps its state when the screen turns or the app comes back. */
@Composable
fun rememberFabOpen(): FabOpen {
    var saved by rememberSaveable { mutableStateOf(true) }
    val state = androidx.compose.runtime.remember { FabOpen(saved) }
    androidx.compose.runtime.SideEffect { saved = state.open }
    return state
}

/**
 * 2.0.0 [RVP-10] (검토 UIX-10·U13-20 '메모 편집: 날짜·일정 칩의 X가 18dp') The X that takes something off a chip: it looks 18dp
 * but is pressed as 48dp, beside the chip (a chip's own tap opens it, so the X cannot live inside it).
 */
@Composable
fun ChipClear(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.IconButton(onClick = onClick, modifier = modifier.offset(x = (-6).dp).size(48.dp)) {
        Icon(androidx.compose.material.icons.Icons.Filled.Close, contentDescription = label,
            modifier = Modifier.size(18.dp), tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
