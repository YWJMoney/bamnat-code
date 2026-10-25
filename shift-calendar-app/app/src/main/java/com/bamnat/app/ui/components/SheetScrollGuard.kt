package com.bamnat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Velocity

/**
 * [UX-13] 1.25 Z (U13): a sheet whose inside scrolls (the day, with its groups) closed when the user swiped down to see
 * its top: once the list reached the top, the rest of the same swipe, and its fling, pulled the sheet down and shut it.
 * Put between the sheet and its content, this keeps a swipe that scrolled the content from moving the sheet. A swipe
 * that starts with the content already at its top still pulls the sheet down, and the handle, a tap outside and back
 * still close it, so nothing is only a gesture away.
 */
class SheetScrollGuard : NestedScrollConnection {
    /** The content scrolled during the swipe going on now. */
    var scrolledThisSwipe = false
        private set

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset = Offset.Zero

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (consumed.y != 0f) scrolledThisSwipe = true
        // Down (y > 0) left over after the content reached its top: kept here, not handed to the sheet.
        return if (scrolledThisSwipe && available.y > 0f) Offset(0f, available.y) else Offset.Zero
    }

    override suspend fun onPreFling(available: Velocity): Velocity = Velocity.Zero

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        val keep = scrolledThisSwipe && available.y > 0f
        scrolledThisSwipe = false
        return if (keep) Velocity(0f, available.y) else Velocity.Zero
    }
}

/** [UX-13] The guard for one sheet's content. */
@Composable
fun Modifier.sheetScrollGuard(): Modifier {
    val guard = remember { SheetScrollGuard() }
    return nestedScroll(guard)
}
