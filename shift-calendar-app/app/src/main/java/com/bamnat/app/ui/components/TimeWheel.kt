package com.bamnat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.ScrollScope
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.theme.NumberStyle
import kotlinx.coroutines.launch
import java.time.LocalTime

internal val WHEEL_ROW_HEIGHT = 44.dp
private const val VISIBLE_ITEMS = 5
/** Each wheel repeats its values this many times so it can spin in both directions. */
private const val LOOPS = 200

/**
 * Picks a clock time with two thumb-friendly wheels (hours and minutes), shortcuts for common
 * shift times, and ± buttons. Replaces the clock dial, which needs precise taps.
 */
@Composable
fun TimePickerDialog(
    title: String,
    initial: LocalTime,
    onDismiss: () -> Unit,
    presets: List<LocalTime> = DEFAULT_PRESETS,
    onConfirm: (LocalTime) -> Unit,
) {
    var hour by remember { mutableIntStateOf(initial.hour) }
    var minute by remember { mutableIntStateOf(initial.minute) }
    // Bumped when a shortcut sets the time, so the wheels jump to it.
    var jump by remember { mutableIntStateOf(0) }
    val set: (LocalTime) -> Unit = { t ->
        hour = t.hour
        minute = t.minute
        jump++
    }
    val current = LocalTime.of(hour, minute)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${if (hour < 12) "오전" else "오후"} ${(hour % 12).let { if (it == 0) 12 else it }}시 ${minute}분",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // [TIME-03] What 완료 will change, so a wheel turned by accident shows before it is kept.
                TimeChangeLine(timeChangeNote(null, initial, current))
                Box(Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT * VISIBLE_ITEMS), contentAlignment = Alignment.Center) {
                    // Band behind the selected row.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(WHEEL_ROW_HEIGHT)
                            .clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WheelHalf(count = 24, value = hour, jump = jump, label = "시", onValue = { hour = it })
                        WheelHalf(count = 60, value = minute, jump = jump, label = "분", onValue = { minute = it })
                    }
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (delta in listOf(-30, -10, 10, 30)) {
                        TextButton(onClick = { set(current.plusMinutes(delta.toLong())) }) {
                            Text(if (delta > 0) "+${delta}분" else "${delta}분")
                        }
                    }
                }
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (t in presets) {
                        AssistChip(onClick = { set(t) }, label = { Text("%02d:%02d".format(t.hour, t.minute)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(current) }) { Text("완료") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

// [SET-04] With the hospital's 15·23시 and the 12-hour shifts' 19시 (ux-review 1 P31); the row scrolls.
val DEFAULT_PRESETS: List<LocalTime> = listOf(6, 7, 8, 9, 14, 15, 18, 19, 20, 22, 23).map { LocalTime.of(it, 0) }

/**
 * One wheel with its unit, taking an equal share of the row. [TIME-02] The whole share turns the
 * wheel, not only the digits, so a thumb anywhere over 시 or 분 works; the share ends where the next
 * wheel's begins, so a swipe never turns the neighbour.
 */
@Composable
internal fun RowScope.WheelHalf(
    count: Int,
    value: Int,
    jump: Int,
    label: String,
    onValue: (Int) -> Unit,
    cyclic: Boolean = true,
) {
    Box(Modifier.weight(1f).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Wheel(count, value, jump, label, onValue, cyclic, Modifier.fillMaxWidth())
        // The unit sits right of the centred digits. An invisible copy on the left keeps the digits
        // centred over the wheel. Plain text takes no touches, so swipes over it still turn the wheel.
        Row(Modifier.clearAndSetSemantics {}, verticalAlignment = Alignment.CenterVertically) {
            WheelUnitText(label, Modifier.alpha(0f))
            Text("00", style = WheelDigits, fontSize = 28.sp, fontWeight = FontWeight.Bold, modifier = Modifier.alpha(0f))
            WheelUnitText(label)
        }
    }
}

@Composable
private fun WheelUnitText(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = modifier.padding(horizontal = 4.dp),
    )
}

private val WheelDigits: TextStyle @Composable get() = MaterialTheme.typography.headlineSmall.merge(NumberStyle)

/**
 * One column of numbers that turns up and down. [TIME-01] The rows above and below the chosen one
 * stay readable (at 3시 you see 2 above and 4 below), fading further out, so you can tell which way
 * to turn. A [cyclic] wheel runs past the end back to the start, like a clock; a plain one stops.
 */
@Composable
internal fun Wheel(
    count: Int,
    value: Int,
    jump: Int,
    label: String,
    onValue: (Int) -> Unit,
    cyclic: Boolean = true,
    modifier: Modifier = Modifier.width(64.dp),
) {
    val middle = if (cyclic) count * LOOPS / 2 else 0
    val state = rememberLazyListState(initialFirstVisibleItemIndex = middle + value)
    val scope = rememberCoroutineScope()
    // The row nearest the middle of the wheel is the chosen one.
    val selected by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
            info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) }?.index
                ?: (middle + value)
        }
    }
    val haptics = LocalHapticFeedback.current
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { selected }.collect {
            onValue(Math.floorMod(it, count))
            // [VIS-02] A light tick for each number that passes the middle, like a real wheel.
            if (!first) haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
            first = false
        }
    }
    // A shortcut moved the time: show it on the wheel.
    LaunchedEffect(jump) {
        if (jump > 0 && Math.floorMod(selected, count) != value) {
            val base = selected - Math.floorMod(selected, count)
            state.scrollToItem(base + value)
        }
    }
    LazyColumn(
        state = state,
        flingBehavior = rememberWheelFling(state, cyclic, count),
        modifier = modifier
            .height(WHEEL_ROW_HEIGHT * VISIBLE_ITEMS)
            .semantics {
                contentDescription = "$value$label"
                // [VIS-02] TalkBack moves the wheel one step at a time, not only a page.
                val unit = if (label == "시") "1시간" else "1$label"
                val last = if (cyclic) count * LOOPS - 1 else count - 1
                customActions = listOf(
                    CustomAccessibilityAction("$unit 늘리기") { scope.launch { state.animateScrollToItem((selected + 1).coerceAtMost(last)) }; true },
                    CustomAccessibilityAction("$unit 줄이기") { scope.launch { state.animateScrollToItem((selected - 1).coerceAtLeast(0)) }; true },
                )
            },
        contentPadding = PaddingValues(vertical = WHEEL_ROW_HEIGHT * (VISIBLE_ITEMS / 2)),
    ) {
        items(if (cyclic) count * LOOPS else count) { i ->
            val v = Math.floorMod(i, count)
            val distance = kotlin.math.abs(i - selected)
            val isSelected = distance == 0
            Box(
                Modifier
                    .height(WHEEL_ROW_HEIGHT)
                    .fillMaxWidth()
                    .clickable(interactionSource = null, indication = null) { scope.launch { state.animateScrollToItem(i) } },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "%02d".format(v),
                    style = WheelDigits,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    fontSize = when (distance) { 0 -> 28.sp; 1 -> 22.sp; else -> 18.sp },
                    textAlign = TextAlign.Center,
                    modifier = Modifier.alpha(when (distance) { 0 -> 1f; 1 -> 0.7f; else -> 0.3f }),
                )
            }
        }
    }
}

/**
 * [TIME-03] How many rows one flick may move past the row under the finger. A plain snap fling ran on with
 * the flick's speed, so a flick (or a swipe meant to scroll the window) turned 18시 into 22시 or 02시 and
 * the user did not notice (PC rec-70710298-3, S15-02·04·06). Now a flick moves at most this many rows;
 * a long way still goes by dragging, which follows the finger, or by the ±10·30분 buttons and the shortcuts.
 */
internal const val WHEEL_MAX_FLING_ROWS = 2

/**
 * [TIME-03] Rows a flick adds, from its speed in dp per second: a slow release just settles on the nearest row,
 * a flick moves one more, a hard flick two, never more than [WHEEL_MAX_FLING_ROWS].
 */
internal fun wheelFlingRows(velocityDpPerSecond: Float): Int {
    val speed = kotlin.math.abs(velocityDpPerSecond)
    val rows = when {
        speed < 400f -> 0
        speed < 1800f -> 1
        else -> 2
    }.coerceAtMost(WHEEL_MAX_FLING_ROWS)
    return if (velocityDpPerSecond < 0) -rows else rows
}

/** [TIME-03] Settles the wheel on a row at most [WHEEL_MAX_FLING_ROWS] away from where the finger let go. */
@Composable
private fun rememberWheelFling(state: LazyListState, cyclic: Boolean, count: Int): FlingBehavior {
    val density = LocalDensity.current
    return remember(state, cyclic, count, density) { WheelFling(state, density, if (cyclic) count * LOOPS - 1 else count - 1) }
}

private class WheelFling(
    private val state: LazyListState,
    private val density: Density,
    private val last: Int,
) : FlingBehavior {
    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val info = state.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val nearest = info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) } ?: return 0f
        val rowPx = with(density) { WHEEL_ROW_HEIGHT.toPx() }
        val target = (nearest.index + wheelFlingRows(with(density) { initialVelocity.toDp().value })).coerceIn(0, last)
        // Positive scrolls toward later rows, the same sign as the velocity.
        val distance = (nearest.offset + nearest.size / 2 - center) + (target - nearest.index) * rowPx
        if (kotlin.math.abs(distance) < 0.5f) return 0f
        var done = 0f
        animate(
            initialValue = 0f,
            targetValue = distance,
            // Settling back against the flick (a slow release short of half a row) starts from rest.
            initialVelocity = if (initialVelocity * distance > 0) initialVelocity else 0f,
            animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        ) { value, _ ->
            val consumed = scrollBy(value - done)
            done += consumed
        }
        return 0f
    }
}

/**
 * [TIME-03] "끝 18:30 → 07:30으로 바뀌어요" when [now] differs from [was], null when it is the same. [part] names
 * which time ("시작", "끝"), or null for a window with one time.
 */
internal fun timeChangeNote(part: String?, was: LocalTime, now: LocalTime, nextDay: Boolean = false): String? {
    if (was.hour == now.hour && was.minute == now.minute) return null
    return (part?.let { "$it " } ?: "") + "${wheelClock(was)} → ${if (nextDay) "다음날 " else ""}${wheelClock(now)}으로 바뀌어요"
}

/**
 * [TIME-03] A shift's start and end in one short line, so two changes still fit one line and do not push the wheels
 * down (PC 1a8e88f6: "시작 … · 끝 …" folded onto two): "19:00~07:00 → 20:00~다음날 08:00으로 바뀌어요". With one
 * of the two changed, it names that one ("끝 07:00 → 다음날 08:00으로 바뀌어요"). Null when neither changed.
 */
internal fun rangeChangeNote(wasStart: LocalTime, wasEnd: LocalTime, start: LocalTime, end: LocalTime): String? {
    val same = { a: LocalTime, b: LocalTime -> a.hour == b.hour && a.minute == b.minute }
    val nextDay = !end.isAfter(start)
    return when {
        same(wasStart, start) && same(wasEnd, end) -> null
        same(wasEnd, end) -> timeChangeNote("시작", wasStart, start)
        same(wasStart, start) -> timeChangeNote("끝", wasEnd, end, nextDay)
        else -> "${wheelClock(wasStart)}~${wheelClock(wasEnd)} → ${wheelClock(start)}~${if (nextDay) "다음날 " else ""}${wheelClock(end)}으로 바뀌어요"
    }
}

private fun wheelClock(t: LocalTime): String = "%02d:%02d".format(t.hour, t.minute)

/**
 * [TIME-03] One line in the primary colour under the time, saying what is about to change. With nothing changed
 * ([text] null) it still holds its line, so the wheels below do not jump when it comes and goes.
 */
@Composable
internal fun TimeChangeLine(text: String?, modifier: Modifier = Modifier) {
    // Always one line: a long one shrinks a little instead of folding and pushing the wheels down (PC 1a8e88f6).
    // The held empty line is not read out, so TalkBack does not stop on a blank (PC 6ed1650c).
    Box(
        modifier.fillMaxWidth().padding(vertical = 2.dp).then(if (text == null) Modifier.clearAndSetSemantics {} else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        com.bamnat.app.ui.FitText(
            text ?: " ",
            style = MaterialTheme.typography.bodyMedium.merge(NumberStyle)
                .copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary),
        )
    }
}
