package com.bamnat.app.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.bamnat.core.layout.TabOrder

/** [NAV-06] The line shown while the tabs are being moved. */
internal const val TAB_EDIT_HINT_TOP = "맨 앞 메뉴가 앱을 켤 때 첫 화면이에요"
internal const val TAB_EDIT_HINT_SAVE = "다른 곳을 누르면 저장"
internal const val TAB_EDIT_HINT = "$TAB_EDIT_HINT_TOP · $TAB_EDIT_HINT_SAVE"

/** [NAV-06] TalkBack's names for what a tab can do about the order. */
internal const val TAB_EDIT_ACTION = "메뉴 순서 바꾸기"
internal const val TAB_FORWARD_ACTION = "앞으로 옮기기"
internal const val TAB_BACKWARD_ACTION = "뒤로 옮기기"
internal const val TAB_SAVE_ACTION = "순서 저장"

/**
 * [NAV-06] 1.26 AD: the tabs' edit mode. A long press on a tab starts it with the order shown; the tabs then wiggle and
 * move under a finger (or by TalkBack's actions) until a tap anywhere else, or back, saves the order ([finish]).
 */
@Stable
internal class TabEdit {
    var editing by mutableStateOf(false)
        private set
    /** The order being made, by [TabOrder] key. */
    var order by mutableStateOf(emptyList<String>())
        private set
    /** The tab under the finger, and how far it has been dragged from its place along the bar. */
    var dragging by mutableStateOf<String?>(null)
        private set
    var offset by mutableFloatStateOf(0f)
        private set
    /** What TalkBack reads out next (the hint's live region). */
    var said by mutableStateOf("")
        private set

    // Where each place of the bar is (not each tab: a dragged tab changes place, the places stay), along the bar.
    private val slotStart = FloatArray(MAX_TABS)
    private val slotSize = FloatArray(MAX_TABS)

    fun start(shown: List<String>) {
        if (editing) return
        order = shown
        editing = true
        said = "$TAB_EDIT_ACTION. $TAB_EDIT_HINT"
    }

    /** One place nearer the front (-1) or the end (+1); [label] names the tab for TalkBack. */
    fun step(key: String, by: Int, label: String) {
        order = TabOrder.step(order, key, by)
        said = "$label, ${order.indexOf(key) + 1}번째" + if (order.first() == key) " · 앱을 켤 때 첫 화면" else ""
    }

    fun place(index: Int, start: Float, size: Float) {
        if (index !in 0 until MAX_TABS) return
        slotStart[index] = start
        slotSize[index] = size
    }

    fun dragStart(key: String) {
        dragging = key
        offset = 0f
    }

    /**
     * The dragged tab follows the finger and takes a neighbour's place once its middle is a third into the neighbour
     * (a finger let go on a tab's middle has moved there; swapped, it is far from swapping back).
     */
    fun dragBy(delta: Float, label: (String) -> String) {
        val key = dragging ?: return
        offset += delta
        val i = order.indexOf(key)
        if (i < 0) return
        val middle = slotStart[i] + slotSize[i] / 2 + offset
        val moved = when {
            i < order.lastIndex && middle > slotStart[i + 1] + slotSize[i + 1] * PAST -> {
                offset -= (slotStart[i + 1] + slotSize[i + 1]) - (slotStart[i] + slotSize[i])
                i + 1
            }
            i > 0 && middle < slotStart[i - 1] + slotSize[i - 1] * (1 - PAST) -> {
                offset -= slotStart[i - 1] - slotStart[i]
                i - 1
            }
            else -> return
        }
        order = TabOrder.move(order, key, moved)
        said = "${label(key)}, ${moved + 1}번째"
    }

    fun dragEnd() {
        dragging = null
        offset = 0f
    }

    /** Ends the edit; the order made, to save. */
    fun finish(): List<String> {
        editing = false
        dragEnd()
        said = ""
        return order
    }

    private companion object {
        const val MAX_TABS = 8
        const val PAST = 0.3f
    }
}

/** [NAV-06] For screenshots: the wiggle drawn still at its widest, so the picture is the same every run. */
internal object TabWiggle {
    @androidx.annotation.VisibleForTesting
    var frozen = false
    const val DEGREES = 3.5f
}

/**
 * [NAV-06] What makes a tab of the bar ([horizontal]) or the rail movable: a long press starts the edit (with a buzz),
 * then a drag moves it; TalkBack gets the long press and 앞으로·뒤로 옮기기 as actions (GestureAlternativesTest).
 */
@Composable
internal fun tabEditModifier(
    edit: TabEdit,
    key: String,
    index: Int,
    count: Int,
    label: String,
    horizontal: Boolean,
    shown: () -> List<String>,
    labelOf: (String) -> String,
    onSave: () -> Unit,
): Modifier {
    val haptic = LocalHapticFeedback.current
    val at by rememberUpdatedState(index)
    val names by rememberUpdatedState(labelOf)
    val order by rememberUpdatedState(shown)
    val enter = {
        if (!edit.editing) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            edit.start(order())
        }
    }
    val enterNow by rememberUpdatedState(enter)
    return Modifier
        .onPlaced { c ->
            val p = c.positionInParent()
            edit.place(at, if (horizontal) p.x else p.y, (if (horizontal) c.size.width else c.size.height).toFloat())
        }
        .zIndex(if (edit.dragging == key) 1f else 0f)
        .graphicsLayer {
            val d = if (edit.dragging == key) edit.offset else 0f
            if (horizontal) translationX = d else translationY = d
        }
        .pointerInput(key) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                if (!edit.editing) {
                    // A tap or a scroll is the tab's own; only a finger held still starts the edit.
                    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var over = false
                        while (!over) {
                            val e = awaitPointerEvent(PointerEventPass.Initial)
                            val c = e.changes.firstOrNull { it.id == down.id }
                            over = c == null || !c.pressed || (c.position - down.position).getDistance() > viewConfiguration.touchSlop
                        }
                        true
                    }
                    if (ended != null) return@awaitEachGesture
                    enterNow()
                }
                edit.dragStart(key)
                while (true) {
                    val e = awaitPointerEvent(PointerEventPass.Initial)
                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                    if (!c.pressed) {
                        c.consume()
                        break
                    }
                    val d = c.positionChange()
                    if (d != Offset.Zero) {
                        c.consume()
                        edit.dragBy(if (horizontal) d.x else d.y, names)
                    }
                }
                edit.dragEnd()
            }
        }
        .semantics {
            if (edit.editing) stateDescription = "순서 바꾸는 중 · ${index + 1}번째"
            else onLongClick(TAB_EDIT_ACTION) { enterNow(); true }
            customActions = listOfNotNull(
                CustomAccessibilityAction(TAB_FORWARD_ACTION) { enterNow(); edit.step(key, -1, label); true }.takeIf { index > 0 },
                CustomAccessibilityAction(TAB_BACKWARD_ACTION) { enterNow(); edit.step(key, +1, label); true }.takeIf { index < count - 1 },
                CustomAccessibilityAction(TAB_SAVE_ACTION) { onSave(); true }.takeIf { edit.editing },
            )
        }
}

/** The phone's "remove animations" is on: no wiggle, an outline instead. */
@Composable
private fun animationsOff(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            android.provider.Settings.Global.getFloat(context.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
    }
}

/**
 * [NAV-06] A tab's icon while the order is edited: it wiggles a little (±3.5°, each tab a beat after the one before), or
 * with animations off gets a still outline. Outside the edit it is just [icon].
 */
@Composable
internal fun WigglingIcon(editing: Boolean, index: Int, icon: @Composable () -> Unit) {
    if (!editing) {
        icon()
        return
    }
    if (animationsOff()) {
        val line = MaterialTheme.colorScheme.primary
        Box(Modifier.drawBehind {
            val pad = 5.dp.toPx()
            drawRoundRect(line, topLeft = Offset(-pad, -pad), size = Size(size.width + pad * 2, size.height + pad * 2),
                cornerRadius = CornerRadius(8.dp.toPx()), style = Stroke(1.5.dp.toPx()))
        }) { icon() }
        return
    }
    val angle = if (TabWiggle.frozen) {
        if (index % 2 == 0) TabWiggle.DEGREES else -TabWiggle.DEGREES
    } else {
        val wiggle = rememberInfiniteTransition(label = "tabWiggle")
        val a by wiggle.animateFloat(
            initialValue = -TabWiggle.DEGREES,
            targetValue = TabWiggle.DEGREES,
            animationSpec = infiniteRepeatable(tween(WIGGLE_MILLIS, easing = LinearEasing), RepeatMode.Reverse,
                initialStartOffset = StartOffset(index * WIGGLE_STAGGER_MILLIS)),
            label = "tabWiggleAngle",
        )
        a
    }
    Box(Modifier.graphicsLayer { rotationZ = angle }) { icon() }
}

/**
 * [NAV-06] The one line over the tabs while they are edited, where the ad usually is. Tapping it saves too, and it
 * says each move aloud to TalkBack.
 */
@Composable
internal fun TabEditHint(said: String, onSave: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = TAB_SAVE_ACTION, onClick = onSave)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = said.ifEmpty { TAB_EDIT_HINT }
            },
    ) {
        Row(Modifier.heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.SwapHoriz, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            // Two lines of their own, so a narrow screen does not break the sentence in the middle.
            androidx.compose.foundation.layout.Column {
                Text(TAB_EDIT_HINT_TOP, style = MaterialTheme.typography.bodyMedium)
                Text(TAB_EDIT_HINT_SAVE, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** [NAV-06] Laid over everything but the tabs while they are edited: the first touch anywhere saves, and goes no further. */
@Composable
internal fun TabEditCatcher(onSave: () -> Unit, modifier: Modifier = Modifier) {
    val save by rememberUpdatedState(onSave)
    Box(modifier.pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false).consume()
            save()
            do {
                val e = awaitPointerEvent()
                e.changes.forEach { it.consume() }
            } while (e.changes.any { it.pressed })
        }
    })
}

private const val WIGGLE_MILLIS = 140
private const val WIGGLE_STAGGER_MILLIS = 50
