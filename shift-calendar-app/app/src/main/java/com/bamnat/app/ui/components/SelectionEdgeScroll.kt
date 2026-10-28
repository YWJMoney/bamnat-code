package com.bamnat.app.ui.components

import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.bamnat.core.memo.CaretScroll

/**
 * [MEMO-SEL-01] 1.30 AY: a selection handle dragged to the top or the bottom of a long text scrolls the page it is on,
 * and the selection keeps growing, like a phone's own text boxes. Before, a memo longer than the screen could only be
 * copied a screenful at a time: the text box grows with its text, so the scrolling is the page's (a `verticalScroll`
 * around it), and Compose's selection handles only ever scroll the text box's own inside.
 *
 * How (`docs/plans/1.30-AY-개발계획.md` 1장):
 * - The page says what shows ([edgeScrollViewport]) and hands its scroll down ([LocalEdgeScroll]).
 * - A text box takes a [SelectionDrag] ([rememberSelectionDrag]): every change that moves one end of a selection with
 *   the text the same is a finger on a handle (or a long press being dragged). While its line is near the edge, each
 *   frame scrolls the page a little and moves that end by as much, so it stays under the finger.
 * - Compose works out a handle's place from where the finger went since it touched down, in the text box's own
 *   coordinates; a page scrolled under a still finger is not in it. [SelectionDrag.filter] adds what this drag has
 *   scrolled to each place Compose gives, so the next small move does not throw the selection back.
 * - The drag is over when the copy/paste bar comes up (Compose shows it as the finger lifts): [SelectionDrag.Toolbar].
 * - Only a touch drag: with a keyboard (shift + arrows) the caret follows as before.
 */
@Stable
class EdgeScroll(internal val scroll: ScrollableState) {
    /** The scrolling box as it shows on screen (not its content). */
    internal var viewport: LayoutCoordinates? = null
}

/** [MEMO-SEL-01] The scrolling page around a long text, for its selection handles; null where there is none. */
val LocalEdgeScroll = staticCompositionLocalOf<EdgeScroll?> { null }

@Composable
fun rememberEdgeScroll(scroll: ScrollableState): EdgeScroll = remember(scroll) { EdgeScroll(scroll) }

/** [MEMO-SEL-01] On the scrolling box, before its `verticalScroll`: the part that shows. */
fun Modifier.edgeScrollViewport(edge: EdgeScroll): Modifier = onGloballyPositioned { edge.viewport = it }

/** [MEMO-SEL-01] One text box's selection drag; see the file's top. */
@Stable
class SelectionDrag internal constructor(private val edge: EdgeScroll?) {
    /** The laid-out text's own place (inside any decoration), and its layout. */
    internal var text: LayoutCoordinates? = null
    internal var layout: TextLayoutResult? = null
    internal var focused = false
    internal var touch = true
    internal var value: () -> TextFieldValue = { TextFieldValue() }
    internal var select: (TextFieldValue) -> Unit = {}

    /** The end the finger moves in this drag, and how far the page scrolled under it since the drag began. */
    private var which: CaretScroll.End? = null
    internal var shift = 0f
        private set
    /** Where the finger is, in the text's coordinates (with [shift]). */
    private var fingerX = 0f
    private var fingerY = 0f
    /** Time since the finger last moved the selection, counted in frames so a test clock counts it too. */
    private var sinceUser = 0L
    internal var running by mutableStateOf(false)
        private set

    /**
     * From the text box's `onValueChange`: the value to keep. A finger moving one end comes back with that end where
     * the finger really is (the page may have scrolled under it), kept inside what shows; anything else as it came.
     */
    fun filter(before: TextFieldValue, next: TextFieldValue): TextFieldValue {
        if (edge == null) return next
        val end = if (touch && next.text == before.text && next.composition == null) {
            CaretScroll.movingEnd(before.selection.start, before.selection.end, next.selection.start, next.selection.end)
        } else null
        if (end == null) {
            reset()
            return next
        }
        if (end != which) {
            which = end
            shift = 0f
        }
        sinceUser = 0L
        running = true
        val l = layout ?: return next
        val r = l.getCursorRect(next.selection.of(end).coerceIn(0, l.layoutInput.text.length))
        fingerX = r.left
        fingerY = (r.top + r.bottom) / 2f + shift
        val y = inside(fingerY, r.height)
        if (shift == 0f && y == fingerY) return next
        return next.withEnd(end, offsetAt(l, y))
    }

    /** The drag is over (the finger lifted, the text changed, the box lost focus). */
    fun reset() {
        which = null
        shift = 0f
        running = false
    }

    /** [y] moved so the line stays inside the box that shows. */
    private fun inside(y: Float, line: Float): Float {
        val port = edge?.viewport ?: return y
        val t = text ?: return y
        if (!port.isAttached || !t.isAttached) return y
        val shown = port.localPositionOf(t, Offset(0f, y)).y
        return y + CaretScroll.keepInside(shown, port.size.height, line) - shown
    }

    private fun offsetAt(l: TextLayoutResult, y: Float): Int =
        l.getOffsetForPosition(Offset(fingerX, y.coerceIn(0f, (l.size.height - 1).coerceAtLeast(0).toFloat())))

    /** The frames while a drag is on: near an edge, scroll and carry the moving end along. */
    internal suspend fun follow(band: Float, most: Float) {
        val edge = edge ?: return
        var last = 0L
        var held = 0L
        while (running) {
            val now = withFrameNanos { it }
            val dt = if (last == 0L) FRAME_NANOS else (now - last).coerceIn(0L, 4 * FRAME_NANOS)
            last = now
            sinceUser += dt
            val port = edge.viewport
            val t = text
            val l = layout
            val end = which
            if (!focused || end == null || l == null || port == null || t == null || !port.isAttached || !t.isAttached) {
                running = false
                break
            }
            val speed = CaretScroll.edgeSpeed(port.localPositionOf(t, Offset(0f, fingerY)).y, port.size.height, band, most)
            if (speed == 0f) {
                held = 0L
                // A finger resting in the middle: the frames stop until it moves again (the drag itself goes on).
                if (sinceUser > IDLE_NANOS) running = false
                continue
            }
            held += dt
            val used = edge.scroll.scrollBy(speed * dt / FRAME_NANOS * CaretScroll.boost(held / 1_000_000))
            if (used == 0f) {
                // At the very top or bottom: nothing more to bring in.
                if (sinceUser > IDLE_NANOS) running = false
                continue
            }
            shift += used
            fingerY += used
            val v = value()
            val line = l.getCursorRect(v.selection.of(end).coerceIn(0, l.layoutInput.text.length)).height
            val next = v.withEnd(end, offsetAt(l, inside(fingerY, line)))
            if (next.selection != v.selection) select(next)
        }
    }

    /** The copy/paste bar coming up means the finger lifted: the drag is over. Put around the text box. */
    @Composable
    fun Toolbar(content: @Composable () -> Unit) {
        val inner = LocalTextToolbar.current
        val ending = remember(inner, this) { EndingToolbar(inner) { reset() } }
        CompositionLocalProvider(LocalTextToolbar provides ending, content = content)
    }

    private companion object {
        const val FRAME_NANOS = 16_666_667L
        const val IDLE_NANOS = 1_500_000_000L
    }
}

private fun TextRange.of(which: CaretScroll.End): Int = if (which == CaretScroll.End.START) start else end

private fun TextFieldValue.withEnd(end: CaretScroll.End, offset: Int): TextFieldValue = copy(
    selection = if (end == CaretScroll.End.START) TextRange(offset, selection.end) else TextRange(selection.start, offset),
)

/** Tells [onShow] when the bar comes up, and is the platform's bar otherwise. */
private class EndingToolbar(private val inner: TextToolbar, private val onShow: () -> Unit) : TextToolbar {
    override val status: TextToolbarStatus get() = inner.status
    override fun hide() = inner.hide()
    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
    ) {
        onShow()
        inner.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested)
    }

    override fun showMenu(
        rect: Rect,
        onCopyRequested: (() -> Unit)?,
        onPasteRequested: (() -> Unit)?,
        onCutRequested: (() -> Unit)?,
        onSelectAllRequested: (() -> Unit)?,
        onAutofillRequested: (() -> Unit)?,
    ) {
        onShow()
        inner.showMenu(rect, onCopyRequested, onPasteRequested, onCutRequested, onSelectAllRequested, onAutofillRequested)
    }
}

/**
 * [MEMO-SEL-01] A text box's selection drag on the page above it ([LocalEdgeScroll]); [value] is the box's value now and
 * [select] sets it (only the selection changes). Without a page it does nothing.
 */
@Composable
fun rememberSelectionDrag(value: () -> TextFieldValue, select: (TextFieldValue) -> Unit): SelectionDrag {
    val edge = LocalEdgeScroll.current
    val drag = remember(edge) { SelectionDrag(edge) }
    drag.value = value
    drag.select = select
    drag.touch = LocalInputModeManager.current.inputMode == InputMode.Touch
    if (edge != null) {
        val density = LocalDensity.current
        val band = with(density) { EDGE_BAND.toPx() }
        val most = with(density) { MOST_PER_FRAME.toPx() }
        LaunchedEffect(drag, drag.running) { if (drag.running) drag.follow(band, most) }
    }
    return drag
}

/** [MEMO-SEL-01] Where the edge starts scrolling, and the most it scrolls in a frame (before [CaretScroll.boost]). */
private val EDGE_BAND = 56.dp
private val MOST_PER_FRAME = 10.dp

/**
 * [MEMO-SEL-02] 1.30 AY: an outlined text box for long writing (문의, 일지) whose selection handles scroll the page around
 * it ([SelectionDrag]); otherwise like `OutlinedTextField(value: String, …)`.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun LongTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    supportingText: (@Composable () -> Unit)? = null,
    isError: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    // As BasicTextField(String) keeps it: the selection and the half-built letter live here, the text is the caller's.
    var state by remember { mutableStateOf(TextFieldValue(value)) }
    val shown = state.copy(text = value)
    if (shown.selection != state.selection || shown.composition != state.composition || shown.text != state.text) state = shown
    val drag = rememberSelectionDrag(value = { state }, select = { state = it })
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val colors = OutlinedTextFieldDefaults.colors()
    val textColor = when {
        isError -> colors.errorTextColor
        focused -> colors.focusedTextColor
        else -> colors.unfocusedTextColor
    }
    CompositionLocalProvider(LocalTextSelectionColors provides colors.textSelectionColors) {
        drag.Toolbar {
            BasicTextField(
                value = state,
                onValueChange = { next ->
                    val kept = drag.filter(state, next)
                    val changed = kept.text != state.text
                    state = kept
                    if (changed) onValueChange(kept.text)
                },
                modifier = modifier
                    .then(if (label != null) Modifier.padding(top = 8.dp) else Modifier)
                    .defaultMinSize(OutlinedTextFieldDefaults.MinWidth, OutlinedTextFieldDefaults.MinHeight)
                    .onFocusChanged { drag.focused = it.isFocused; if (!it.isFocused) drag.reset() },
                textStyle = LocalTextStyle.current.merge(color = textColor),
                cursorBrush = SolidColor(if (isError) colors.errorCursorColor else colors.cursorColor),
                keyboardOptions = keyboardOptions,
                minLines = minLines,
                interactionSource = interaction,
                onTextLayout = { drag.layout = it },
                decorationBox = { inner ->
                    OutlinedTextFieldDefaults.DecorationBox(
                        value = state.text,
                        innerTextField = { Box(Modifier.onGloballyPositioned { drag.text = it }) { inner() } },
                        enabled = true,
                        singleLine = false,
                        visualTransformation = VisualTransformation.None,
                        interactionSource = interaction,
                        isError = isError,
                        label = label,
                        placeholder = placeholder,
                        supportingText = supportingText,
                        colors = colors,
                    )
                },
            )
        }
    }
}
