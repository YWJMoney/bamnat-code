package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle

/** The example app at [scale], drawn at its full size and shrunk (taps still land where they are seen). */
@Composable
internal fun ScaledPhone(r: Recipe, style: WorkStyle?, tab: String, onTab: (String) -> Unit, scale: Float, onPick: () -> Unit = {},
                         modifier: Modifier = Modifier, scroll: androidx.compose.foundation.ScrollState? = null) {
    Box(modifier.size((340 * scale).dp, (560 * scale).dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(340.dp, 560.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            MockPhone(r, style, tab, onTab, onPick, scroll)
        }
    }
}

/**
 * [TRY-01][TRY-04] 지금 ↔ 입으면, one phone: [after] shows on the left up to [split] of the width (1 = all [after]) and [before] on the
 * right, with a line where they meet.
 *
 * 1.36 (사용자 2026-10-31 "입어보고 고르기에서 사진을 보면 서로 다르게 보이는 문제 … 동일하게 움직이게 만들고 달라진점만 보이게", R-50):
 * - 한 뼈대, 두 옷: [before] is laid out in [after]'s places ([LookParts.frame]), so the line meets the same day and the same card on both sides.
 * - 같이 움직이기: one scroll and one tab for both; a drag on either side moves the two.
 * - [diff] 바뀌는 곳만: [after] whole, what stays the same dimmed and what changes left bright with a ring ([onChanged] says how much).
 * Each side is cut by its own box (no clip drawn over a layer, which also left the screen black in a screenshot of 반반).
 */
@Composable
internal fun CompareCanvas(before: Recipe, after: Recipe, style: WorkStyle?, tab: String, onTab: (String) -> Unit, scale: Float, split: Float,
                           onPick: () -> Unit = {}, diff: Boolean = false, onChanged: (Int) -> Unit = {}) {
    val framed = remember(before, after) { LookParts.frame(before, after) }
    val scroll = rememberScrollState()
    val w = (340 * scale).dp
    val h = (560 * scale).dp
    val shownSplit = if (diff) 1f else split
    val beforeLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val afterLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    var marks by remember { mutableStateOf<List<androidx.compose.ui.geometry.Rect>>(emptyList()) }
    // The two sides drawn into layers when 바뀌는 곳만 is on, read back once they settle, and compared block by block.
    if (diff) androidx.compose.runtime.LaunchedEffect(framed, after, tab, scroll.value, scale) {
        kotlinx.coroutines.delay(180)
        val a = runCatching { afterLayer.toImageBitmap() }.getOrNull() ?: return@LaunchedEffect
        val b = runCatching { beforeLayer.toImageBitmap() }.getOrNull() ?: return@LaunchedEffect
        marks = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { changedBlocks(a, b) }
        onChanged(marks.size)
    } else androidx.compose.runtime.LaunchedEffect(Unit) { marks = emptyList() }
    fun Modifier.into(layer: androidx.compose.ui.graphics.layer.GraphicsLayer) = if (!diff) this else drawWithContent {
        layer.record { this@drawWithContent.drawContent() }
        drawLayer(layer)
    }
    Box(Modifier.size(w, h)) {
        // 지금, the right side (and under 바뀌는 곳만, drawn but hidden: only read).
        Box(Modifier.matchParentSize().graphicsLayer { alpha = if (diff) 0f else 1f }) {
            ScaledPhone(framed, style, tab, onTab, scale, onPick, Modifier.into(beforeLayer), scroll)
        }
        // 입으면, the left side, cut at the line by its own box.
        if (shownSplit > 0.001f) Box(Modifier.width(w * shownSplit).height(h).clipToBounds()) {
            Box(Modifier.wrapContentSize(Alignment.TopStart, unbounded = true)) {
                ScaledPhone(after, style, tab, onTab, scale, onPick, Modifier.into(afterLayer), scroll)
            }
        }
        if (diff && marks.isNotEmpty()) androidx.compose.foundation.Canvas(Modifier.matchParentSize()) {
            val sx = size.width
            val sy = size.height
            val dim = androidx.compose.ui.graphics.Path().apply {
                fillType = androidx.compose.ui.graphics.PathFillType.EvenOdd
                addRect(androidx.compose.ui.geometry.Rect(0f, 0f, sx, sy))
                marks.forEach { r -> addRect(androidx.compose.ui.geometry.Rect(r.left * sx, r.top * sy, r.right * sx, r.bottom * sy)) }
            }
            drawPath(dim, androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f))
            marks.forEach { r ->
                drawRect(androidx.compose.ui.graphics.Color(0xFFFFB300), topLeft = androidx.compose.ui.geometry.Offset(r.left * sx, r.top * sy),
                    size = androidx.compose.ui.geometry.Size(r.width * sx, r.height * sy), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
            }
        }
        if (!diff && shownSplit in 0.02f..0.98f) Box(Modifier.fillMaxHeight().width(2.dp).offset(x = w * shownSplit - 1.dp)
            .background(MaterialTheme.colorScheme.primary))
    }
}

/**
 * [TRY-04] Where two pictures of the same size differ: the picture cut into blocks (about 24 a row), each block compared on a few of its
 * pixels, the changed blocks joined into boxes row by row and then box to box, given as fractions of the picture (0..1).
 */
internal fun changedBlocks(a: androidx.compose.ui.graphics.ImageBitmap, b: androidx.compose.ui.graphics.ImageBitmap): List<androidx.compose.ui.geometry.Rect> {
    val pa = a.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
    val pb = b.asAndroidBitmap().let { if (it.config == android.graphics.Bitmap.Config.HARDWARE) it.copy(android.graphics.Bitmap.Config.ARGB_8888, false) else it }
    val wid = minOf(pa.width, pb.width)
    val hei = minOf(pa.height, pb.height)
    if (wid < 8 || hei < 8) return emptyList()
    val ia = IntArray(wid * hei).also { pa.getPixels(it, 0, wid, 0, 0, wid, hei) }
    val ib = IntArray(wid * hei).also { pb.getPixels(it, 0, wid, 0, 0, wid, hei) }
    val cols = 24
    val bw = maxOf(4, wid / cols)
    val rows = (hei + bw - 1) / bw
    val nc = (wid + bw - 1) / bw
    val on = Array(rows) { BooleanArray(nc) }
    fun far(x: Int, y: Int): Boolean {
        val p = ia[y * wid + x]; val q = ib[y * wid + x]
        val d = kotlin.math.abs((p shr 16 and 255) - (q shr 16 and 255)) + kotlin.math.abs((p shr 8 and 255) - (q shr 8 and 255)) + kotlin.math.abs((p and 255) - (q and 255))
        return d > 36
    }
    for (r in 0 until rows) for (cI in 0 until nc) {
        val x0 = cI * bw; val y0 = r * bw
        val x1 = minOf(wid, x0 + bw); val y1 = minOf(hei, y0 + bw)
        var hits = 0
        var y = y0
        while (y < y1 && hits < 2) { var x = x0; while (x < x1) { if (far(x, y)) { hits++; if (hits >= 2) break }; x += 2 }; y += 2 }
        on[r][cI] = hits >= 2
    }
    // Runs along each row, then runs stacked when they line up.
    val boxes = ArrayList<IntArray>() // c0, c1, r0, r1
    for (r in 0 until rows) {
        var cI = 0
        while (cI < nc) {
            if (!on[r][cI]) { cI++; continue }
            val s = cI
            while (cI < nc && on[r][cI]) cI++
            val above = boxes.firstOrNull { it[3] == r - 1 && it[0] == s && it[1] == cI - 1 }
            if (above != null) above[3] = r else boxes += intArrayOf(s, cI - 1, r, r)
        }
    }
    return boxes.map { bx ->
        androidx.compose.ui.geometry.Rect(bx[0] * bw / wid.toFloat(), bx[2] * bw / hei.toFloat(),
            minOf(1f, (bx[1] + 1) * bw / wid.toFloat()), minOf(1f, (bx[3] + 1) * bw / hei.toFloat()))
    }
}

/**
 * [TRY-01] 1.35 입어 보기, one screen (사용자 2026-10-30 21:45 "입어보기랑 넣기가 버튼 두개가 있을 필요가 없고 입어보기 기능 하나로 전 후를
 * 비교하고 체크 할수 있게 해서 선택적으로 도입"): the phone as it is now and as it would be, side by side on one example with a line to
 * slide; under it what the theme has, by tab, each to tick — the whole, a tab, or one part ([LookParts]). The example follows the ticks
 * at once. A tap on the example opens that screen's parts with 담기 for each (눌러서 담기). [extra] is a recipe's other pieces (알람,
 * 규칙); [actions] are the buttons at the bottom, given the parts ticked (입히기 or 넣기, and 담아 두기).
 */
@Composable
fun TryOnScreen(
    r: Recipe,
    style: WorkStyle?,
    onDismiss: () -> Unit,
    onKeep: ((Set<String>) -> Boolean)?,
    extra: (@Composable ColumnScope.() -> Unit)? = null,
    actions: @Composable ColumnScope.(Set<String>) -> Unit,
) {
    val c = LocalContext.current.appContainer
    val parts = remember(r) { LookParts.of(r) }
    var picked by remember(r) { mutableStateOf(parts.map { it.id }.toSet()) }
    var tab by remember { mutableStateOf(parts.firstOrNull()?.tab?.takeIf { it != LookParts.WIDGET } ?: TabOrder.CALENDAR) }
    var split by remember { mutableFloatStateOf(1f) }
    var tapped by remember { mutableStateOf(false) }
    // F-96: what 담기 did stays on the screen (a toast is gone before it is read, and hides under the sheet).
    var keptLine by remember(r) { mutableStateOf<String?>(null) }
    val boxSize = c.wardrobe.kept.collectAsStateWithLifecycle().value.size
    fun keep(ids: Set<String>) {
        keptLine = if (onKeep?.invoke(ids) == true) "✓ 보관함에 담았어요 (${boxSize + 1}개). 마을 › 테마와 스튜디오의 보관함에서 꺼내 써요"
            else "보관함이 꽉 찼거나 담을 부분이 없어요 (${c.wardrobe.partsLimit()}개까지)"
    }
    val mine by produceState(Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = emptyList()), c) {
        value = Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
    }
    val after = remember(mine, picked, r) { LookParts.layer(mine, LookParts.pick(r, picked)) }
    var onlyChanges by remember { mutableStateOf(false) }
    var changedBlocks by remember { mutableStateOf(0) }
    // [TRY-04] The parts that change on the screen shown, by name.
    val changedHere = remember(mine, after, tab) { LookParts.changed(mine, after).mapNotNull { LookParts.byId(it) }.filter { it.tab == tab }.map { it.label } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(Modifier.fillMaxSize()) {
            // F-95: this window's own bar icons follow its ground (they stayed white on a light sheet).
            com.bamnat.app.ui.theme.BarIcons(light = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("입어 보기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(r.title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                    }
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") }
                }
                // 지금 | 반반 | 입으면, and the line to slide.
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0f to "지금", 0.5f to "반반", 1f to "입으면").forEach { (v, label) ->
                            com.bamnat.app.ui.components.FilterChip(selected = !onlyChanges && split == v, onClick = { onlyChanges = false; split = v }, label = { Text(label) })
                        }
                        // [TRY-04] 1.36 바뀌는 곳만 (사용자 2026-10-31 "달라진점만 보이게").
                        com.bamnat.app.ui.components.FilterChip(selected = onlyChanges, onClick = { onlyChanges = !onlyChanges }, label = { Text("바뀌는 곳만") })
                    }
                    Box(Modifier.padding(top = 8.dp)) {
                        CompareCanvas(mine, after, style, tab, { tab = it }, scale = 0.6f, split = split, onPick = { tapped = true },
                            diff = onlyChanges, onChanged = { changedBlocks = it })
                    }
                    if (!onlyChanges) Slider(split, { split = it }, modifier = Modifier.fillMaxWidth(0.7f).semantics { contentDescription = "지금과 입으면 나눠 보기"; stateDescription = "입으면 ${(split * 100).toInt()}%" })
                    // [R136-06] 1.36 (리뷰 R-18): what the picture shows follows the slider.
                    Text(when {
                        onlyChanges && changedBlocks == 0 && changedHere.isEmpty() -> "이 화면은 입어도 똑같아요. 아래 탭을 눌러 다른 화면을 봐요."
                        onlyChanges -> "밝은 곳이 바뀌어요" + (changedHere.takeIf { it.isNotEmpty() }?.let { ": " + it.joinToString(" · ") } ?: "") + "."
                        split == 1f -> "입으면 이렇게 보여요."
                        split == 0f -> "지금 내 모습이에요."
                        else -> "왼쪽이 입은 모습, 오른쪽이 지금이에요. 두 쪽은 같이 움직여요."
                    } + if (onlyChanges) "" else " 화면을 누르면 그 화면의 조각을 담을 수 있어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(top = 6.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (parts.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("고를 모양 · ${picked.size}/${parts.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                            TextButton(onClick = { picked = if (picked.size == parts.size) emptySet() else parts.map { it.id }.toSet() }) {
                                Text(if (picked.size == parts.size) "모두 빼기" else "전체 고르기")
                            }
                        }
                        parts.groupBy { it.tab }.forEach { (t, list) ->
                            val ids = list.map { it.id }.toSet()
                            val all = picked.containsAll(ids)
                            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { tab = if (t == LookParts.WIDGET) tab else t; picked = if (all) picked - ids else picked + ids },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(all, { on -> picked = if (on) picked + ids else picked - ids })
                                Text(LookParts.tabLabel(t) + " 전체", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                                Text("${list.count { it.id in picked }}/${list.size}", style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Row(Modifier.fillMaxWidth().padding(start = 40.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                list.forEach { p ->
                                    com.bamnat.app.ui.components.FilterChip(selected = p.id in picked, onClick = {
                                        if (t != LookParts.WIDGET) tab = t
                                        picked = if (p.id in picked) picked - p.id else picked + p.id
                                    }, label = { Text(p.label) })
                                }
                            }
                        }
                    }
                    extra?.invoke(this)
                }
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        actions(picked)
                        if (onKeep != null && picked.isNotEmpty()) TextButton(onClick = { keep(picked) }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
                            Text("고른 모양 ${picked.size}개 보관함에 담아 두기 (무료)")
                        }
                        keptLine?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite }) }
                    }
                }
            }
        }
    }
    // 눌러서 담기: the screen tapped, its parts to keep one by one.
    if (tapped) {
        val here = parts.filter { it.tab == tab }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { tapped = false },
            title = { Text("${LookParts.tabLabel(tab)}에서 마음에 드는 부분") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (here.isEmpty()) Text("이 화면에는 이 테마가 바꾸는 부분이 없어요")
                    here.forEach { p ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(p.label, Modifier.weight(1f))
                            if (onKeep != null) TextButton(onClick = { keep(setOf(p.id)); tapped = false }) { Text("담기") }
                            TextButton(onClick = { picked = picked + p.id; tapped = false }) { Text("고르기") }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { tapped = false }) { Text("닫기") } },
        )
    }
}

/** The parts a box item ([com.bamnat.app.recipe.ThemeWardrobe.Kept]) holds, said in a line. */
internal fun partsLine(ids: List<String>): String = ids.mapNotNull { LookParts.byId(it)?.label }.joinToString(" · ").ifEmpty { "전체" }
