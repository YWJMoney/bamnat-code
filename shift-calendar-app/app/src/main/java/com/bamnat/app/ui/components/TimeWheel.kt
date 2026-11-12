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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.focusRequester
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

    // 2.0.0 [RVP-27] (검토 UIX-50·U13b-05·I13ba-03) 오전/오후로 보기 gives an 오전·오후 wheel and 1~12, and the time can be typed as
    // four digits (0630) for a one-hand change.
    val twelve = com.bamnat.app.ui.Fmt.clock12
    var typing by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            // 2.0.0 [RVP-30] (검토 UIX-53) It scrolls like its sibling windows, so a sideways phone or big letters keep the shortcuts.
            ScrollMoreColumn {
              Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${if (hour < 12) "오전" else "오후"} ${(hour % 12).let { if (it == 0) 12 else it }}시 ${minute}분",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { typing = !typing }) { Text(if (typing) "바퀴로" else "숫자로 치기") }
                }
                if (typing) TimeDigits(current) { set(it); typing = false }
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
                        if (twelve) {
                            WheelHalf(count = 2, value = hour / 12, jump = jump, label = "", cyclic = false,
                                shown = { if (it == 0) "오전" else "오후" }, onValue = { half -> hour = half * 12 + hour % 12 })
                            WheelHalf(count = 12, value = hour % 12, jump = jump, label = "시",
                                shown = { "%02d".format(if (it == 0) 12 else it) }, onValue = { h -> hour = (hour / 12) * 12 + h })
                        } else WheelHalf(count = 24, value = hour, jump = jump, label = "시", onValue = { hour = it })
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
                // PC 267560dd (큰 글자): '08:00' was cut; room under the chips as in 날짜 시간 바꾸기, and the label never wraps.
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    for (t in presets) {
                        // [RVP-27] In the clock the settings chose.
                        AssistChip(onClick = { set(t) }, label = { Text(com.bamnat.app.ui.Fmt.time(t), maxLines = 1, softWrap = false) })
                    }
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
    shown: (Int) -> String = { "%02d".format(it) },
) {
    Box(Modifier.weight(1f).padding(horizontal = 2.dp), contentAlignment = Alignment.Center) {
        Wheel(count, value, jump, label, onValue, cyclic, Modifier.fillMaxWidth(), shown)
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
    shown: (Int) -> String = { "%02d".format(it) },
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
    // 1.39.1 [R391-01] The newest [onValue], not the one from the first frame: a caller whose lambda reads a plain
    // value (the alarm window's '시작 기준' wheels read the minutes of that frame) would otherwise write an old value
    // back when the other wheel turns.
    val latest by rememberUpdatedState(onValue)
    LaunchedEffect(state) {
        var first = true
        snapshotFlow { selected }.collect {
            latest(Math.floorMod(it, count))
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
                contentDescription = "${shown(value)}$label"
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
                    .clickable(interactionSource = null, indication = null) { scope.launch { state.animateScrollToItem(i) } }
                    // 2.0.0 [RVP-32] (검토 UIX-55) One TalkBack stop for the wheel (with its 늘리기·줄이기), not one per row.
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    shown(v),
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
    val fling = remember(state, cyclic, count, density) { WheelFling(state, density, if (cyclic) count * LOOPS - 1 else count - 1) }
    // 1.37 LOW (시간 바퀴): where the finger took hold, so a long swipe does not get a flick's rows on top.
    LaunchedEffect(state, fling) {
        state.interactionSource.interactions.collect { if (it is androidx.compose.foundation.interaction.DragInteraction.Start) fling.dragFrom = state.firstVisibleItemIndex }
    }
    return fling
}

/**
 * 1.37 LOW (시간 바퀴, PC 5bbd5265 "길게 쓸면 4~6칸"): rows a flick adds after a drag that already moved [draggedRows]. The
 * finger's own way is kept as it is; a drag of two rows or more ends where the finger let go, so a long swipe no longer
 * runs two rows further.
 */
internal fun wheelFlingRowsAfterDrag(velocityDpPerSecond: Float, draggedRows: Int): Int =
    if (kotlin.math.abs(draggedRows) >= 2) 0 else wheelFlingRows(velocityDpPerSecond)

private class WheelFling(
    private val state: LazyListState,
    private val density: Density,
    private val last: Int,
) : FlingBehavior {
    /** The first row in view when the latest drag began, -1 before any. */
    var dragFrom = -1

    override suspend fun ScrollScope.performFling(initialVelocity: Float): Float {
        val info = state.layoutInfo
        val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
        val nearest = info.visibleItemsInfo.minByOrNull { kotlin.math.abs(it.offset + it.size / 2 - center) } ?: return 0f
        val rowPx = with(density) { WHEEL_ROW_HEIGHT.toPx() }
        val dragged = if (dragFrom < 0) 0 else state.firstVisibleItemIndex - dragFrom
        dragFrom = -1
        val target = (nearest.index + wheelFlingRowsAfterDrag(with(density) { initialVelocity.toDp().value }, dragged)).coerceIn(0, last)
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
    val to = wheelClock(now)
    return (part?.let { "$it " } ?: "") + "${wheelClock(was)} → ${if (nextDay) "다음날 " else ""}$to${com.bamnat.app.ui.Fmt.ro(to)} 바뀌어요"
}

/**
 * [TIME-03] A shift's start and end in one short line, so two changes still fit one line and do not push the wheels
 * down (PC 1a8e88f6: "시작 … · 끝 …" folded onto two): "19:00~07:00 → 20:00~다음날 08:00으로 바뀌어요". With one
 * of the two changed, it names that one ("끝 07:00 → 다음날 08:00으로 바뀌어요"). Null when neither changed.
 */
internal fun rangeChangeNote(wasStart: LocalTime, wasEnd: LocalTime, start: LocalTime, end: LocalTime, short: Boolean = false): String? {
    val same = { a: LocalTime, b: LocalTime -> a.hour == b.hour && a.minute == b.minute }
    val nextDay = !end.isAfter(start)
    return when {
        same(wasStart, start) && same(wasEnd, end) -> null
        same(wasEnd, end) -> timeChangeNote("시작", wasStart, start)
        same(wasStart, start) -> timeChangeNote("끝", wasEnd, end, nextDay)
        // With large text only the new times, which two lines hold whole (PC cf5ee1c6: the end was cut on a third).
        short -> "${wheelClock(start)}~${if (nextDay) "다음날 " else ""}${wheelClock(end)}${com.bamnat.app.ui.Fmt.ro(wheelClock(end))} 바뀌어요"
        else -> "${wheelClock(wasStart)}~${wheelClock(wasEnd)} → ${wheelClock(start)}~${if (nextDay) "다음날 " else ""}${wheelClock(end)}${com.bamnat.app.ui.Fmt.ro(wheelClock(end))} 바뀌어요"
    }
}

/** [TIME-03] Above this text scale the change line keeps two lines' room instead of shrinking into one. */
private const val LARGE_TEXT_SCALE = 1.15f

// 2.0.0 [RVP-27] (검토 UIX-50) In the clock the settings chose.
private fun wheelClock(t: LocalTime): String = com.bamnat.app.ui.Fmt.time(t)

/**
 * [TIME-03] One line in the primary colour under the time, saying what is about to change. With nothing changed
 * ([text] null) it still holds its line, so the wheels below do not jump when it comes and goes.
 */
@Composable
internal fun TimeChangeLine(text: String?, modifier: Modifier = Modifier, largeText: String? = text) {
    // At the usual text size it is always one line: a long one shrinks a little instead of folding and pushing the
    // wheels down (PC 1a8e88f6). With large text it keeps two lines' room from the start and wraps at full size
    // instead of turning into the smallest words in the window (PC 7934283c).
    // The held empty line is not read out, so TalkBack does not stop on a blank (PC 6ed1650c).
    val style = MaterialTheme.typography.bodyMedium.merge(NumberStyle)
        .copy(fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
    Box(
        modifier.fillMaxWidth().padding(vertical = 2.dp).then(if (text == null) Modifier.clearAndSetSemantics {} else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        if (LocalDensity.current.fontScale > LARGE_TEXT_SCALE) {
            // [largeText] is the shorter wording, so the two lines hold the new time whole (PC cf5ee1c6).
            Text(largeText ?: " ", style = style, minLines = 2, maxLines = 2, modifier = Modifier.fillMaxWidth())
        } else {
            com.bamnat.app.ui.FitText(text ?: " ", style = style)
        }
    }
}

/**
 * 2.0.0 [RVP-27] (검토 I13ba-03 '가운데 숫자를 누르면 네 자리 치기(0630)') The time as four digits: 0630 is 06:30, 1915 is 19:15; with
 * 오전/오후 on, 630 is 06:30 too. Kept when the digits make a real time.
 */
@Composable
internal fun TimeDigits(now: LocalTime, onTime: (LocalTime) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val parsed = typedTime(text)
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { v -> text = v.filter(Char::isDigit).take(4); typedTime(text)?.takeIf { text.length == 4 }?.let(onTime) },
        label = { Text("시각 네 자리 (예: ${"%02d%02d".format(now.hour, now.minute)})") },
        supportingText = { Text(parsed?.let { com.bamnat.app.ui.Fmt.time(it) + "으로" } ?: if (text.isEmpty()) "숫자만 쳐요" else "00:00~23:59로 쳐요") },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
            keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword,
            imeAction = androidx.compose.ui.text.input.ImeAction.Done,
        ),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { parsed?.let(onTime) }),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).focusRequester(focus),
    )
}

/** [RVP-27] "0630" → 06:30, "630" → 06:30, "1915" → 19:15; null when it is not a time. */
internal fun typedTime(digits: String): LocalTime? {
    if (digits.length !in 3..4 || digits.any { !it.isDigit() }) return null
    val h = digits.dropLast(2).toInt()
    val m = digits.takeLast(2).toInt()
    return if (h in 0..23 && m in 0..59) LocalTime.of(h, m) else null
}
