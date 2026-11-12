package com.bamnat.app.ui.components

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.relocation.bringIntoViewRequester

/**
 * [CNV-13] 1.37 펼쳐 보기 (사용자 2026-11-04 "좌우로 스크롤인데 펼쳐서 볼수도 있게"): a row of chips or colours that slides sideways, with
 * ‘펼치기 ▾’ at its end when it does not fit; open, every item shows at once over as many lines as it needs, ‘접기 ▴’ last.
 * Open or folded is kept per [key], so a row opened once stays open the next time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoldRow(key: String, modifier: Modifier = Modifier, spacing: Dp = 6.dp, what: String = "", over: Boolean = false, content: @Composable () -> Unit) {
    if (over && LocalFoldHost.current != null) return FoldOver(modifier, spacing, what, content)
    val ctx = LocalContext.current
    var open by rememberSaveable(key) { mutableStateOf(foldOpen(ctx, key)) }
    fun set(v: Boolean) { open = v; setFoldOpen(ctx, key, v) }
    if (open) {
        FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(spacing), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            content()
            FoldButton("접기 ▴", "${what.ifEmpty { "이 줄" }} 한 줄로 접기") { set(false) }
        }
    } else {
        FoldLine(modifier, spacing, rememberScrollState(), button = { FoldButton("펼치기 ▾", "${what.ifEmpty { "이 줄" }} 모두 펼쳐 보기") { set(true) } },
            content = content)
    }
}

/**
 * 2.0.0 [RVS-16] (총괄 #495 사진 검토, 디자인 품질 조건 12: 글자 200% · 접은 폭에서 끝의 칩이 반쯤 잘려 ‘펼치기’에 붙어 ‘바탕 펼치기’로
 * 읽혔어요) A folded row shares its width out: the chips get the part that whole chips fill (none is cut where the row rests), then a thin
 * line, then [button] in a place of its own. The chips still slide sideways inside their part; where they come to rest the part again ends
 * after the last whole chip. [button] is there only while the chips are
 * more than the width (or [keepButton]).
 */
@Composable
private fun FoldLine(modifier: Modifier, spacing: Dp, scroll: androidx.compose.foundation.ScrollState, keepButton: Boolean = false,
                     button: @Composable () -> Unit, content: @Composable () -> Unit) {
    val ends = remember { ChipEnds() }
    var over by remember { mutableStateOf(false) }
    val line = MaterialTheme.colorScheme.outlineVariant
    androidx.compose.ui.layout.Layout(
        content = {
            androidx.compose.foundation.layout.Box(Modifier.horizontalScroll(scroll)) { ChipLine(spacing, ends, content) }
            if (over || keepButton) Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.layout.Box(Modifier.padding(horizontal = 4.dp).width(1.dp).heightIn(min = 24.dp, max = 24.dp).background(line))
                button()
            }
        },
        modifier = modifier.fillMaxWidth(),
    ) { ms, c ->
        val all = ms[0].maxIntrinsicWidth(androidx.compose.ui.unit.Constraints.Infinity)
        val width = if (c.hasBoundedWidth) c.maxWidth else all
        val needs = all > width
        if (needs != over) over = needs
        val btn = ms.getOrNull(1)?.measure(androidx.compose.ui.unit.Constraints(maxWidth = width, maxHeight = c.maxHeight))
        val slack = 1.dp.roundToPx()
        // At rest the part ends after the last chip that is whole from where the row is slid to; while it slides, the whole room.
        val view = (if (btn == null) minOf(all, width) else {
            val room = width - btn.width
            val at = scroll.value
            if (scroll.isScrollInProgress) room
            else ends.at.lastOrNull { it > at && it + slack <= at + room }?.let { it + slack - at } ?: room
        }).coerceAtLeast(0)
        val p = ms[0].measure(androidx.compose.ui.unit.Constraints(minWidth = view, maxWidth = view, maxHeight = c.maxHeight))
        val h = maxOf(p.height, btn?.height ?: 0).coerceAtLeast(c.minHeight)
        layout(width, h) {
            p.place(0, (h - p.height) / 2)
            btn?.place(width - btn.width, (h - btn.height) / 2)
        }
    }
}

/** Where each chip of a [ChipLine] ends, from the start, as the row was last asked how wide it is. */
private class ChipEnds { var at: IntArray = IntArray(0) }

/** The chips side by side, [spacing] apart, each as wide as it wants; asked how wide it is, it notes where each chip ends in [ends]. */
@Composable
private fun ChipLine(spacing: Dp, ends: ChipEnds, content: @Composable () -> Unit) {
    val policy = remember(spacing, ends) {
        object : androidx.compose.ui.layout.MeasurePolicy {
            override fun androidx.compose.ui.layout.MeasureScope.measure(
                measurables: List<androidx.compose.ui.layout.Measurable>, constraints: androidx.compose.ui.unit.Constraints,
            ): androidx.compose.ui.layout.MeasureResult {
                val gap = spacing.roundToPx()
                val ps = measurables.map { it.measure(androidx.compose.ui.unit.Constraints(maxHeight = constraints.maxHeight)) }
                val w = (ps.sumOf { it.width } + gap * (ps.size - 1).coerceAtLeast(0)).coerceIn(constraints.minWidth, constraints.maxWidth)
                val h = (ps.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
                return layout(w, h) {
                    var x = 0
                    ps.forEach { it.place(x, (h - it.height) / 2); x += it.width + gap }
                }
            }

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicWidth(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int,
            ): Int {
                val gap = spacing.roundToPx()
                // A thin line between chips (a separator) is no place for the part to end, so only chips are noted.
                val thin = 12.dp.roundToPx()
                var x = 0
                val at = ArrayList<Int>(measurables.size)
                measurables.forEachIndexed { i, m -> if (i > 0) x += gap; val w = m.maxIntrinsicWidth(height); x += w; if (w >= thin) at += x }
                ends.at = at.toIntArray()
                return x
            }

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicWidth(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, height: Int,
            ): Int = maxIntrinsicWidth(measurables, height)

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.maxIntrinsicHeight(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int,
            ): Int = measurables.maxOfOrNull { it.maxIntrinsicHeight(androidx.compose.ui.unit.Constraints.Infinity) } ?: 0

            override fun androidx.compose.ui.layout.IntrinsicMeasureScope.minIntrinsicHeight(
                measurables: List<androidx.compose.ui.layout.IntrinsicMeasurable>, width: Int,
            ): Int = measurables.maxOfOrNull { it.minIntrinsicHeight(androidx.compose.ui.unit.Constraints.Infinity) } ?: 0
        }
    }
    androidx.compose.ui.layout.Layout(content = content, measurePolicy = policy)
}

/**
 * 2.0.0 [RVS-16] A picked chip in a [FoldRow] slides into the folded row's view: with only whole chips shown where the row rests, the
 * picked one could be past them (총괄 #495: 고른 ‘바탕’ 칩이 잘려 있었어요).
 */
@Composable
fun KeepInView(on: Boolean, content: @Composable () -> Unit) {
    val r = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    var h by remember { mutableStateOf(0f) }
    // Asked for with the rest of the row after it, so the row slides until the picked chip is first (none cut at the start).
    androidx.compose.runtime.LaunchedEffect(on) {
        if (on) { androidx.compose.runtime.withFrameNanos { }; r.bringIntoView(androidx.compose.ui.geometry.Rect(0f, 0f, 100_000f, h)) }
    }
    androidx.compose.foundation.layout.Box(Modifier.bringIntoViewRequester(r).onGloballyPositioned { h = it.size.height.toFloat() }) { content() }
}

/** The small word button that opens or folds a [FoldRow] (also used by 색 고르기, whose two rows fold together). */
@Composable
fun FoldButton(words: String, said: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = 8.dp),
        modifier = Modifier.heightIn(min = 40.dp).semantics { contentDescription = said }) {
        Text(words, style = MaterialTheme.typography.labelLarge, maxLines = 1)
    }
}

private const val PREFS = "fold_rows"

fun foldOpen(ctx: Context, key: String): Boolean =
    runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(key, false) }.getOrDefault(false)

fun setFoldOpen(ctx: Context, key: String, v: Boolean) {
    runCatching { ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(key, v).apply() }
}

/** For a caller that folds its own layout ([com.bamnat.app.ui.settings.ColorPick]): open or not, kept per [key]. */
@Composable
fun rememberFold(key: String): Pair<Boolean, (Boolean) -> Unit> {
    val ctx = LocalContext.current
    var open by rememberSaveable(key) { mutableStateOf(foldOpen(ctx, key)) }
    return remember(open) { open to { v: Boolean -> open = v; setFoldOpen(ctx, key, v) } }
}

/**
 * Inside an opened [FoldRow] laid over the screen: a chip that opens a menu of its own calls this so the row stays open under the menu
 * (else a tap in the row closes it once the tap is done).
 */
val LocalFoldHold = androidx.compose.runtime.staticCompositionLocalOf<() -> Unit> { {} }

/**
 * PC 확인 1.37 A9 (펼친 줄이 예시를 49%까지 밀어냈고, 밀린 자리에 ‘숨기기’가 와 잘못 눌렸어요): the screen that holds rows laid over
 * it. An opened [FoldRow] with `over` is drawn here, on the row's own place and over what is below it, so nothing under it moves.
 */
class FoldHost {
    internal var box: androidx.compose.ui.layout.LayoutCoordinates? = null
    internal var open by mutableStateOf<FoldOpen?>(null)
    val isOpen: Boolean get() = open != null
    fun fold() { open = null }
}

internal class FoldOpen(val at: () -> androidx.compose.ui.layout.LayoutCoordinates?, val spacing: Dp, val what: String, val content: @Composable () -> Unit)

val LocalFoldHost = androidx.compose.runtime.staticCompositionLocalOf<FoldHost?> { null }

/** On the box [FoldOverlay] is drawn in, so the open row finds its place in it. */
fun foldHostBox(host: FoldHost): Modifier = Modifier.onGloballyPositioned { host.box = it }

/** The open row over the screen, last in the host's box: a clear layer that folds it on a tap outside, and the row's items all at once. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FoldOverlay(host: FoldHost) {
    val o = host.open ?: return
    val box = host.box
    val row = o.at()
    if (box == null || row == null || !row.isAttached || !box.isAttached) return
    val pos = box.localPositionOf(row, androidx.compose.ui.geometry.Offset.Zero)
    val density = androidx.compose.ui.platform.LocalDensity.current
    var held by remember { mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun fold() { if (host.open === o) host.open = null }
    // PC 확인 1.37 A10 (펼친 칩이 밑의 단추를 덮어 그 자리를 누르면 '숨기기'가 눌림): what is under the open row is dimmed, so it reads
    // as covered, and a tap there only folds the row.
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
        .pointerInput(o) { detectTapGestures { fold() } })
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 6.dp, tonalElevation = 2.dp,
        shape = MaterialTheme.shapes.medium.copy(topStart = androidx.compose.foundation.shape.ZeroCornerSize, topEnd = androidx.compose.foundation.shape.ZeroCornerSize),
        modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(pos.x.toInt(), pos.y.toInt()) }
            .width(with(density) { row.size.width.toDp() })) {
        androidx.compose.runtime.CompositionLocalProvider(LocalFoldHold provides { held = true }) {
            // At most about half the screen, so 접기 ▴ (last) is in view with five lines of chips.
            FlowRow(Modifier.heightIn(max = with(density) { (box.size.height * 0.5f).toDp() }.coerceAtLeast(220.dp)).verticalScroll(rememberScrollState())
                .pointerInput(o) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        held = false
                        var moved = false
                        // Watched after the item (it takes the tap); a drag that scrolls the open row does not fold it.
                        do {
                            val e = awaitPointerEvent(PointerEventPass.Final)
                            if (e.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) moved = true
                        } while (e.changes.any { it.pressed })
                        // The item's own tap runs first; then the row folds unless the item opened a menu of its own.
                        if (!moved) scope.launch { androidx.compose.runtime.withFrameNanos { }; if (!held) fold() }
                    }
                }
                .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(o.spacing), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                o.content()
                FoldButton("접기 ▴", "${o.what.ifEmpty { "이 줄" }} 한 줄로 접기") { fold() }
            }
        }
    }
}

/** A row over the example: opened, it is drawn by the screen's [FoldHost] on top of what is below (with no host it opens in place). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FoldOver(modifier: Modifier, spacing: Dp, what: String, content: @Composable () -> Unit) {
    val host = LocalFoldHost.current
    val scroll = rememberScrollState()
    var coords by remember { mutableStateOf<androidx.compose.ui.layout.LayoutCoordinates?>(null) }
    val mine = remember { arrayOfNulls<FoldOpen>(1) }
    val latest = androidx.compose.runtime.rememberUpdatedState(content)
    val isOpen = host != null && host.open != null && host.open === mine[0]
    FoldLine(modifier.onGloballyPositioned { coords = it }, spacing, scroll, keepButton = isOpen, button = {
        FoldButton("펼치기 ▾", "${what.ifEmpty { "이 줄" }} 모두 펼쳐 보기") {
            val o = FoldOpen({ coords }, spacing, what) { latest.value() }
            mine[0] = o; host?.open = o
        }
    }, content = content)
    // Gone from the screen (another part picked, the bar changed): the open row goes with it.
    androidx.compose.runtime.DisposableEffect(host) { onDispose { if (host != null && host.open != null && host.open === mine[0]) host.open = null } }
}
