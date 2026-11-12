package com.bamnat.app.ui.studio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.settings.Chips
import com.bamnat.app.ui.settings.ColorPick
import com.bamnat.app.ui.settings.LabeledSlider
import com.bamnat.app.ui.studio.StudioEdit.Quick
import com.bamnat.core.recipe.BlueprintParts
import kotlin.math.max
import kotlin.math.min

/** The example phone's size before any zoom (the same as [com.bamnat.app.ui.recipe.MockPhone] is drawn at). */
internal val CANVAS_W = 340.dp
internal val CANVAS_H = 560.dp

/**
 * [CNV-02] 1.37 확대·이동 (사용자 2026-11-04 "확대가 안되고 통으로 보여서 제작에 큰 어려움"): the example at any size from 50% to 300%,
 * moved with two fingers, back to 맞춤 with a double tap beside it or the 맞춤 button. One finger stays the example's own (scroll, tap
 * to pick), so nothing done before changes.
 */
@Stable
internal class ZoomState {
    var scale by mutableFloatStateOf(1f)
    var offset by mutableStateOf(Offset.Zero)
    /** The scale the whole example fits at, worked out from the space it has. */
    var fit by mutableFloatStateOf(1f)
    /** Zoomed or moved by hand: a new size of the space (아래 판 열고 닫기) keeps it. */
    var touched by mutableStateOf(false)
    var size by mutableStateOf(IntSize.Zero)
    var origin by mutableStateOf(Offset.Zero)
    var density = 1f
    /** [CNV-04] How much of the space's bottom the 더 보기 판 covers (px): the example can be moved up that much more. */
    var cover by mutableFloatStateOf(0f)
    /** The −·+·맞춤 box is open (folded to its percent, it sits where it covers the least: PC 확인 1.37 A2, 글꼴 1.3배). */
    var controlsOpen by mutableStateOf(false)

    val min: Float get() = min(StudioEdit.ZOOM_MIN, fit)
    val percent: Int get() = Math.round(scale * 100)

    fun toFit() { scale = fit; offset = Offset.Zero; touched = false }

    /** To [s] about [about] (a point from the middle of the space, in px), the point under it staying where it is. */
    fun zoomTo(s: Float, about: Offset = Offset.Zero) {
        val ns = s.coerceIn(min, StudioEdit.ZOOM_MAX)
        val k = ns / scale
        offset = clamp((offset - about) * k + about, ns)
        scale = ns
        touched = true
    }

    fun step(by: Int) = zoomTo(StudioEdit.zoomStep(scale, by))

    fun transform(zoom: Float, pan: Offset, about: Offset) {
        if (zoom != 1f) zoomTo(scale * zoom, about)
        offset = clamp(offset + pan, scale)
        touched = true
    }

    /** Big enough that the box [r] (root coordinates, as drawn now) fills most of the space, in the middle. */
    fun focus(r: Rect) {
        if (size.width == 0 || r.width <= 0f || r.height <= 0f) return
        val mid = Offset(origin.x + size.width / 2f, origin.y + size.height / 2f)
        val p = (r.center - mid - offset) / scale
        val w = r.width / scale
        val h = r.height / scale
        val ns = min(size.width * 0.8f / w, size.height * 0.6f / h).coerceIn(max(scale, 1f), StudioEdit.ZOOM_MAX)
        scale = ns
        offset = clamp(-p * ns, ns)
        touched = true
    }

    /** [CNV-04] Moved (not resized) so the box [r] (root coordinates, as drawn now) sits in the middle of what the 판 leaves in view. */
    fun reveal(r: Rect) {
        if (size.height == 0) return
        val top = origin.y
        val bottom = origin.y + size.height - cover
        if (r.top >= top + 8f && r.bottom <= bottom - 8f) return
        val want = (top + bottom) / 2f
        offset = clamp(offset + Offset(0f, want - r.center.y), scale)
        touched = true
    }

    /** Whether one finger moves the example: it is bigger than its space, or the 판 covers some of it. */
    val pannable: Boolean get() = scale > fit + 0.01f || cover > 0f

    /** Some of the example always stays in view. */
    private fun clamp(o: Offset, s: Float): Offset {
        val cw = CANVAS_W.value * density * s
        val ch = CANVAS_H.value * density * s
        val ax = max(0f, (cw - size.width) / 2f) + size.width * 0.35f
        val ay = max(0f, (ch - size.height) / 2f) + size.height * 0.35f
        return Offset(o.x.coerceIn(-ax, ax), o.y.coerceIn(-ay - cover, ay))
    }
}

/**
 * Two fingers zoom and move. One finger taps what is under it; a drag with one finger moves the example when it is bigger than its space
 * (PC 확인 1.37 A1: 300%에서 한 손가락으로 끌면 누르기가 되어 고르기가 풀리고 아래 판이 열렸어요).
 */
private fun Modifier.twoFingers(z: ZoomState): Modifier = pointerInput(z) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var moved = Offset.Zero
        var dragging = false
        var two = false
        do {
            val e = awaitPointerEvent(PointerEventPass.Initial)
            val down = e.changes.filter { it.pressed }
            if (down.size >= 2) {
                two = true
                val mid = Offset(size.width / 2f, size.height / 2f)
                z.transform(e.calculateZoom(), e.calculatePan(), e.calculateCentroid(useCurrent = true) - mid)
                e.changes.forEach { it.consume() }
            } else if (!two && down.size == 1 && z.pannable) {
                val c = down[0]
                val step = c.position - c.previousPosition
                moved += step
                if (!dragging && moved.getDistance() > viewConfiguration.touchSlop) dragging = true
                if (dragging) { z.transform(1f, step, Offset.Zero); c.consume() }
            } else if (dragging || two) e.changes.forEach { it.consume() }
        } while (e.changes.any { it.pressed })
    }
}

/** [CNV-01][CNV-02] The canvas: the example in the middle of all the space it has, at [z]'s size and place. */
@Composable
internal fun ZoomCanvas(z: ZoomState, modifier: Modifier = Modifier, height: Dp = CANVAS_H, picking: Modifier = Modifier,
    /** A tap nothing on the example took (PC 확인 1.37 A1: 빈 곳을 눌러도 고르기가 안 풀렸어요). */
    onEmptyTap: () -> Unit = {}, content: @Composable () -> Unit) {
    z.density = LocalDensity.current.density
    BoxWithConstraints(
        modifier.clipToBounds()
            .onGloballyPositioned { z.origin = it.positionInRoot(); z.size = it.size }
            .twoFingers(z)
            .pointerInput(z, onEmptyTap) { detectTapGestures(onTap = { onEmptyTap() }, onDoubleTap = { if (z.touched) z.toFit() else z.zoomTo(z.scale * 2f, it - Offset(size.width / 2f, size.height / 2f)) }) }
            .then(picking),
    ) {
        val fit = (min(maxWidth.value / CANVAS_W.value, maxHeight.value / height.value) * 0.97f).coerceAtMost(1.6f)
        LaunchedEffect(fit) { z.fit = fit; if (!z.touched) { z.scale = fit; z.offset = Offset.Zero } }
        Box(Modifier.align(Alignment.Center).requiredSize(CANVAS_W, height)
            .graphicsLayer { scaleX = z.scale; scaleY = z.scale; translationX = z.offset.x; translationY = z.offset.y }) {
            content()
        }
    }
}

/** [CNV-02] − 배율 + 맞춤, over the canvas's corner; the percent itself goes to 100%. */
@Composable
internal fun ZoomControls(z: ZoomState, onFocus: (() -> Unit)?, modifier: Modifier = Modifier) {
    // PC 확인 1.37 A1 (확대 상자가 미리 보기의 아래 탭 막대를 늘 가림): folded to its percent, one tap opens it.
    var open by z::controlsOpen
    Surface(modifier, shape = MaterialTheme.shapes.large, tonalElevation = 4.dp, shadowElevation = 2.dp) {
        if (!open) {
            TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 44.dp).semantics { contentDescription = "확대 ${z.percent}퍼센트, 확대 단추 펴기" }) {
                Text("🔍 ${z.percent}%")
            }
            return@Surface
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { z.step(-1) }, enabled = z.scale > z.min + 0.01f, modifier = Modifier.size(44.dp)
                .semantics { contentDescription = "작게 보기" }) { Text("−") }
            Text("${z.percent}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier
                .clickable(onClickLabel = "실제 크기(100%)로") { z.zoomTo(1f) }.padding(horizontal = 4.dp, vertical = 12.dp)
                .semantics { stateDescription = "${z.percent}퍼센트"; liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
            TextButton(onClick = { z.step(1) }, enabled = z.scale < StudioEdit.ZOOM_MAX - 0.01f, modifier = Modifier.size(44.dp)
                .semantics { contentDescription = "크게 보기" }) { Text("+") }
            if (onFocus != null) TextButton(onClick = onFocus, modifier = Modifier.heightIn(min = 44.dp)) { Text("고른 곳 확대") }
            TextButton(onClick = { z.toFit() }, enabled = z.touched, modifier = Modifier.heightIn(min = 44.dp)) { Text("맞춤") }
            TextButton(onClick = { open = false }, modifier = Modifier.size(44.dp).semantics { contentDescription = "확대 단추 접기" }) { Text("›") }
        }
    }
}

/**
 * [CNV-03][CNV-04] 1.37 위 고정 상황 도구 줄, a part picked: where it is (달력 › 날짜 칸 › 날짜 숫자, each name a tap to the part around it),
 * when it is changed (언제나 · 오늘 · 야간 … · 만약 …), and the tools that fit it, the one open just under them. Every pick goes on the
 * example at once (확인 단추 없음); one 되돌리기 takes back a run of the same tool.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun PartBar(
    sel: String, parts: String, on: String?, whenF: String?,
    onSel: (String?) -> Unit, onOn: (String?, String?) -> Unit,
    quick: Quick?, onQuick: (Quick?) -> Unit,
    set: (label: String, kv: Array<Pair<String, Any?>>) -> Unit,
    onMore: () -> Unit, onCondition: () -> Unit,
) {
    val line = remember(parts, sel, on, whenF) { StudioEdit.line(parts, sel, on, whenF) }
    val hidden = line["hide"] == true
    BarSurface {
        Trail(StudioEdit.trail(sel).map { it to (BlueprintParts.LABELS[it] ?: it) }, onSel)
        com.bamnat.app.ui.components.FoldRow("studio.partbar", what = "꾸미기 도구", over = true) {
            // 언제 고치나: 언제나, the part's own states, its conditions.
            val states = statesFor(sel)
            val conds = remember(parts, sel, on) { StudioEdit.conditions(parts, sel, on) }
            if (states.isNotEmpty() || BlueprintParts.dated(sel)) Box {
                var open by remember { mutableStateOf(false) }
                val hold = com.bamnat.app.ui.components.LocalFoldHold.current
                FilterChip(selected = on != null || whenF != null, onClick = { hold(); open = true },
                    label = { Text("언제: " + listOfNotNull(on?.let { stateWord(sel, it) }, whenF?.let { w ->
                        "만약 " + (com.bamnat.core.formula.EasyWhen.read(w)?.say() ?: w).take(12) }).ifEmpty { listOf("언제나") }.joinToString(" · ") + " ▾",
                        maxLines = 1) })
                com.bamnat.app.ui.components.DropdownMenu(open, { open = false }) {
                    (listOf<String?>(null) + states).forEach { st ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(stateWord(sel, st)) }, onClick = { open = false; onOn(st, null) })
                    }
                    conds.forEach { w ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text("만약 " + (com.bamnat.core.formula.EasyWhen.read(w)?.say() ?: w).take(24)) },
                            onClick = { open = false; onOn(on, w) })
                    }
                    if (BlueprintParts.dated(sel)) androidx.compose.material3.DropdownMenuItem(text = { Text("＋ 조건으로 꾸미기 (수식·조건)") },
                        onClick = { open = false; onCondition() })
                }
            }
            StudioEdit.tools(sel).forEach { q ->
                FilterChip(selected = quick == q, onClick = { onQuick(if (quick == q) null else q) }, label = { Text(q.label) })
            }
            FilterChip(selected = false, onClick = onMore, label = { Text("더 보기 ›") })
            // PC 확인 1.37 A10 (펼친 줄에서 잘못 눌려 메모 제목이 숨겨짐): 숨기기 last, after the tools, away from where the next tap goes.
            if (StudioEdit.canHide(sel)) FilterChip(selected = hidden, onClick = { set("숨기기", arrayOf("hide" to (if (hidden) null else true))) },
                label = { Text(if (hidden) "다시 보이기" else "숨기기") })
        }
        quick?.let { q ->
            // PC 확인 1.37 A9 (도구를 열면 예시가 33~49%로 줄어요): the quick tool takes less of the height; the rest scrolls inside it.
            Column(Modifier.fillMaxWidth().heightIn(max = 168.dp).verticalScroll(rememberScrollState()).animateContentSize()) {
                QuickEditor(q, sel, line) { kv -> set(q.label, kv) }
            }
        }
    }
}

/** The one quick tool open under the line, on [line]'s values. */
@Composable
private fun QuickEditor(q: Quick, id: String, line: Map<String, Any?>, set: (Array<Pair<String, Any?>>) -> Unit) {
    fun one(k: String, v: Any?) = set(arrayOf(k to v))
    fun num(k: String): Float? = (line[k] as? Number)?.toFloat()
    fun col(k: String): Long? = line[k] as? Long
    @Composable fun slider(label: String, k: String, start: Float, range: ClosedFloatingPointRange<Float>, unit: String, step: Int = 1) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                LabeledSlider(label, num(k) ?: start, range, unit, unset = if (line[k] == null) "처음대로" else null, step = step) { one(k, Math.round(it).toLong()) }
            }
            if (line[k] != null) TextButton(onClick = { one(k, null) }) { Text("처음대로") }
        }
    }
    when (q) {
        Quick.FILL -> {
            ColorPick("바탕색", col("fill")) { one("fill", it) }
            if (col("fill") != null) slider("진하기", "fillA", 100f, 0f..100f, "%", step = 5)
        }
        Quick.INK -> ColorPick("글자색", col("ink")) { one("ink", it) }
        Quick.SIZE -> slider("글자 크기", "size", 14f, 6f..40f, "sp")
        Quick.WEIGHT -> Chips(listOf<Long?>(null, 300L, 400L, 600L, 800L).zip(listOf("처음대로", "가늘게", "보통", "조금 굵게", "굵게")),
            (line["weight"] as? Number)?.toLong()) { one("weight", it) }
        Quick.FONT -> com.bamnat.app.ui.settings.FontRow("글꼴", line["font"] as? String) { one("font", it) }
        Quick.ALIGN -> Chips(listOf<String?>(null, "start", "center", "end").zip(listOf("처음대로", "왼쪽", "가운데", "오른쪽")), line["talign"] as? String) { one("talign", it) }
        Quick.LINE -> {
            Chips(listOf<String?>(null, "none", "solid", "dash", "dot", "double").zip(listOf("처음대로", "없음", "실선", "끊은 선", "점선", "두 줄")),
                line["line"] as? String) { one("line", it) }
            if (line["line"] != null && line["line"] != "none") {
                slider("두께", "lineW", 1f, 0f..8f, "dp")
                ColorPick("테두리 색", col("lineC")) { one("lineC", it) }
            }
        }
        Quick.CORNER -> slider("둥글기", "r", 8f, 0f..48f, "dp")
        Quick.SHADOW -> slider("그림자", "shadow", 0f, 0f..24f, "dp")
        Quick.PAD -> slider("안 여백", "pad", 4f, 0f..32f, "dp")
        Quick.GAP -> {
            slider("사이 간격", "gap", 4f, 0f..32f, "dp")
            Text("안에 든 것 사이가 모두 같은 간격으로 맞춰져요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Quick.ALPHA -> slider("보이는 정도", "alpha", 100f, 0f..100f, "%", step = 5)
    }
    if (q == Quick.INK || q == Quick.FILL) Text("${BlueprintParts.LABELS[id] ?: id}에만 입혀져요. 같은 종류 칸은 모두 함께 바뀌어요",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * [CNV-06] A card picked (오늘의 흐름, 이번 달 요약 …): move it up or down — over the month too, so a card from above can stand under it
 * (사용자 요청 6) — hide it, or change how every card looks.
 */
@Composable
internal fun CardBar(
    label: String, page: String, onSel: (String?) -> Unit,
    canUp: Boolean, canDown: Boolean, onMove: (Int) -> Unit,
    /** 달력 only: the side of the month the card is on now (true = above), and the move to the other side. */
    above: Boolean?, onAcross: (() -> Unit)?,
    onHide: () -> Unit, onLook: () -> Unit,
) {
    BarSurface {
        // PC 확인 1.37 A1: 고르기 끝 and the page's name both let the card go (Trail gives null for them).
        Trail(listOf("" to page, "card" to label)) { onSel(null) }
        com.bamnat.app.ui.components.FoldRow("studio.cardbar", what = "카드 도구", over = true) {
            FilterChip(selected = false, onClick = { onMove(-1) }, enabled = canUp, label = { Text("▲ 위로") })
            FilterChip(selected = false, onClick = { onMove(1) }, enabled = canDown, label = { Text("▼ 아래로") })
            if (above != null && onAcross != null) FilterChip(selected = false, onClick = onAcross,
                label = { Text(if (above) "달력 아래로 보내기" else "달력 위로 올리기") })
            FilterChip(selected = false, onClick = onHide, label = { Text("숨기기") })
            FilterChip(selected = false, onClick = onLook, label = { Text("카드 모양 (모든 카드)") })
        }
        Text("숨긴 카드는 위 ‘＋ 카드 넣기’에서 다시 넣어요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun BarSurface(content: @Composable () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) { content() }
    }
}

/**
 * [CNV-03] 범례: the picked part's place as words (달력 › 날짜 칸 › 날짜 숫자); a name before the last goes to that part, ✕ lets go.
 * A key of "" is the screen (the part let go).
 */
@Composable
internal fun Trail(items: List<Pair<String, String>>, onSel: (String?) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).border(2.dp, PICK_RING, RoundedCornerShape(2.dp)))
            items.forEachIndexed { i, (k, words) ->
                val last = i == items.lastIndex
                if (i > 0) Text(" › ", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(words, style = MaterialTheme.typography.labelLarge, fontWeight = if (last) FontWeight.Bold else FontWeight.Normal,
                    color = if (last) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = (if (last) Modifier else Modifier.clickable(onClickLabel = "$words 고르기") { onSel(k.ifEmpty { null }) })
                        .padding(vertical = 10.dp, horizontal = 2.dp).widthIn(max = 160.dp)
                        .semantics { if (last) contentDescription = "고친 곳: ${items.joinToString(", ") { it.second }}" })
            }
        }
        TextButton(onClick = { onSel(null) }, modifier = Modifier.heightIn(min = 44.dp)) { Text("고르기 끝") }
    }
}

/** The ring a picked part has on the example (the same as [com.bamnat.app.ui.parts.partPicking] draws). */
internal val PICK_RING = androidx.compose.ui.graphics.Color(0xFFE91E63)

/** Shown once over an example nothing is picked on yet. */
@Composable
internal fun PickHint(modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.9f)) {
        Text("고칠 곳을 화면에서 눌러요 · 두 손가락으로 크게", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.inverseOnSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}

/** The screen's row with nothing picked: a background for the chips so it reads as one bar with [PartBar]. */
@Composable
internal fun ScreenBar(content: @Composable () -> Unit) = BarSurface(content)

internal fun Modifier.pickBackground(): Modifier = this.background(androidx.compose.ui.graphics.Color.Transparent)
