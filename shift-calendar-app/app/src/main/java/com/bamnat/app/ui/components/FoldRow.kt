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
        val scroll = rememberScrollState()
        Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically) { content() }
            if (scroll.maxValue > 0) FoldButton("펼치기 ▾", "${what.ifEmpty { "이 줄" }} 모두 펼쳐 보기") { set(true) }
        }
    }
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
        shape = androidx.compose.foundation.shape.RoundedCornerShape(bottomStart = 12.dp, bottomEnd = 12.dp),
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
    Row(modifier.fillMaxWidth().onGloballyPositioned { coords = it }, verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(spacing),
            verticalAlignment = Alignment.CenterVertically) { content() }
        if (scroll.maxValue > 0 || isOpen) FoldButton("펼치기 ▾", "${what.ifEmpty { "이 줄" }} 모두 펼쳐 보기") {
            val o = FoldOpen({ coords }, spacing, what) { latest.value() }
            mine[0] = o; host?.open = o
        }
    }
    // Gone from the screen (another part picked, the bar changed): the open row goes with it.
    androidx.compose.runtime.DisposableEffect(host) { onDispose { if (host != null && host.open != null && host.open === mine[0]) host.open = null } }
}
