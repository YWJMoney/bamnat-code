package com.bamnat.app.ui.calendar

import androidx.compose.foundation.gestures.TargetedFlingBehavior
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * 2.0.0 [RVH-55] 검토 PC-88 (통합 2차 PC: 달을 빠르게 세 번 쓸면 한두 달만 넘어가요): a swipe made while the month is still
 * turning counts from the month it is turning to, so three quick swipes turn three months, as three quick taps on › do
 * (the calendar's headingMonth). Left to itself the pager counts every swipe from the month under the finger, and a swipe
 * made during a turn landed on the month already on its way.
 *
 * Only a real swipe adds a month: a finger that catches the turn and lets go slowly goes to the nearest month, as before.
 * 검토 PC-28 stays as it is: a tap during a turn stops the turn there (the pager takes the tap, no day opens), and once the
 * pager is still on a month the count starts again from it.
 */
internal class TurnQueue(private val minFling: Float) : PagerSnapDistance {
    /** The page a turn under way is going to; null when the pager is still on a page. */
    var going: Int? = null
        private set

    override fun calculateTargetPage(startPage: Int, suggestedTargetPage: Int, velocity: Float, pageSize: Int, pageSpacing: Int): Int {
        val near = suggestedTargetPage.coerceIn(startPage - 1, startPage + 1)
        val on = going
        val target = when {
            on == null || abs(velocity) < minFling -> near
            // Forward, from whichever is further on: the month on its way, or the one the finger has already dragged to.
            velocity > 0 -> maxOf(on, startPage) + 1
            else -> minOf(on, startPage) - 1
        }
        going = target
        return target
    }

    /** The pager is still on a page: the next swipe counts from it. */
    fun settled() { going = null }
}

/** [RVH-55] The month pager's fling with [TurnQueue]: the same snap and speed as the pager's own. */
@Composable
internal fun queuedTurns(state: PagerState): TargetedFlingBehavior {
    val minFling = with(LocalDensity.current) { MIN_FLING.toPx() }
    val queue = remember(state, minFling) { TurnQueue(minFling) }
    LaunchedEffect(state, queue) {
        // Still and on a page, not only between two touches of the same quick run of swipes.
        snapshotFlow { !state.isScrollInProgress && state.currentPageOffsetFraction == 0f }.collect { still -> if (still) queue.settled() }
    }
    return PagerDefaults.flingBehavior(state = state, pagerSnapDistance = queue)
}

/** The speed the pager itself takes for a swipe rather than a slow drag (Compose's own threshold for a page fling). */
private val MIN_FLING = 400.dp
