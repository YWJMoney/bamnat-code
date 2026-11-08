package com.bamnat.app.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

/**
 * 1.39 [CALX-08] (사용자 2026-11-08 "급여를 볼 때 좌우 스크롤로 월을 이동"): a sideways swipe on a month page turns the month, as on
 * the calendar: to the left for the next one, to the right for the one before. Rows that scroll sideways themselves (chips, charts)
 * keep their own swipe, since they take the drag first; a drag shorter than [MONTH_SWIPE_DP] or mostly up and down does nothing.
 */
fun Modifier.monthSwipe(key: Any?, onStep: (Int) -> Unit): Modifier = pointerInput(key) {
    val need = MONTH_SWIPE_DP.dp.toPx()
    var dx = 0f
    detectHorizontalDragGestures(
        onDragStart = { dx = 0f },
        onDragEnd = { monthSwipeStep(dx, need)?.let(onStep) },
        onDragCancel = { dx = 0f },
    ) { change, amount ->
        dx += amount
        change.consume()
    }
}

/** [CALX-08] The month a drag of [dx] pixels goes to: +1 (next) for a swipe to the left, -1 to the right, null when too short. */
internal fun monthSwipeStep(dx: Float, need: Float): Int? = when {
    dx <= -need -> 1
    dx >= need -> -1
    else -> null
}

internal const val MONTH_SWIPE_DP = 56
