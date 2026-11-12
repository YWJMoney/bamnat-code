package com.bamnat.app.ui.decor

import kotlinx.coroutines.launch
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.settings.ColorPick
import com.bamnat.core.recipe.Decor
import com.bamnat.core.recipe.DecorGlyphs
import com.bamnat.core.recipe.DecorItem
import com.bamnat.core.recipe.DecorKind
import com.bamnat.core.recipe.RecipeText
import com.bamnat.core.recipe.ShiftRole
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The size a part has on a phone, in dp, for the 꾸미기 판 (stage): a decoration's place is ‰ of it and its size dp, so the stage draws
 * the part this big and scales the whole to fit.
 */
internal fun partSize(anchor: String, screenW: Float = 360f): Size = when (anchor) {
    // PC 확인 C2 1.36 (판이 실제 머리보다 넓어요): the parts as wide as this phone draws them, so a spot on the 판 is the same spot there.
    Decor.CELL -> Size(screenW / 7f - 4f, 64f)
    Decor.HEAD -> Size(screenW, 56f)
    Decor.WEEK -> Size(screenW, 28f)
    Decor.WIDGET -> Size(320f, 160f)
    else -> Size(screenW, 640f)
}

/** This phone's width in dp, as the 판 draws the parts. */
@Composable
private fun screenW(): Float = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp.toFloat().coerceIn(280f, 600f)

/**
 * The 달력 머리's own things, in dp from the left of a [w] wide top (‹, the month, ›, 오늘, 도구), as the calendar lays them out on a
 * phone, and the empty stretch between › and 오늘 where a new decoration goes.
 */
internal object HeadLayout {
    fun navLeft(w: Float) = 4f to 52f
    fun title(w: Float) = 52f to 158f
    fun navRight(w: Float) = 158f to 206f
    fun today(w: Float) = (w - 134f) to (w - 84f)
    fun tools(w: Float) = (w - 80f) to (w - 8f)
    fun free(w: Float): Pair<Float, Float> = navRight(w).second to today(w).first
}

/**
 * PC 확인 C1·C2 1.36 (칸 가운데 💰가 근무 표시에 가려 안 보여요, 새 글상자가 달 이름 위에 겹쳐요): where a new decoration goes on [anchor],
 * as ‰ x and y, and its size — in a cell the top right corner beside the date, on the 달력 머리 the empty stretch between › and 오늘.
 */
internal fun startPlace(anchor: String, kind: DecorKind, n: Int, screenW: Float): StartPlace {
    val (w, h) = startSize(anchor, kind)
    return when (anchor) {
        Decor.CELL -> if (kind == DecorKind.TEXT) StartPlace(500, 880, w, h) else StartPlace(800 - (n % 3) * 140, 160, w, h)
        Decor.HEAD -> {
            val (a, b) = HeadLayout.free(screenW)
            val room = (b - a).coerceAtLeast(24f)
            val cx = ((a + b) / 2 / screenW * 1000).toInt().coerceIn(0, 1000)
            // PC 확인 C2-3 1.36 (글자가 조금 위로 붙어요): the top's line of buttons is 8 dp + 24 dp down its 56, not halfway.
            if (kind == DecorKind.TEXT) StartPlace(cx, HEAD_LINE, (room - 6).toInt().coerceIn(36, 140), 22)
            else StartPlace(cx, HEAD_LINE, minOf(w.toFloat(), room - 4).toInt().coerceAtLeast(14), minOf(h.toFloat(), room - 4).toInt().coerceAtLeast(14))
        }
        else -> StartPlace((500 + (n % 4) * 60).coerceAtMost(900), (500 + (n % 3) * 60).coerceAtMost(900), w, h)
    }
}

/** The 달력 머리's middle line, in ‰ of its height: 8 dp of space on top, then its 48 dp of buttons. */
internal const val HEAD_LINE = 571

internal data class StartPlace(val x: Int, val y: Int, val w: Int, val h: Int)

/** PC 확인 C3 1.36 (다시 열면 붙는 곳이 처음으로 돌아가요): the last place used for each set of places, while the app is open. */
private val lastPlaces = HashMap<List<String>, String>()

/** A new decoration's size on [anchor]: small in a cell, bigger on a background. */
private fun startSize(anchor: String, kind: DecorKind): Pair<Int, Int> = when {
    kind == DecorKind.TEXT -> when (anchor) { Decor.CELL -> 46 to 14; Decor.WEEK -> 120 to 20; Decor.HEAD -> 140 to 28; else -> 180 to 32 }
    anchor == Decor.CELL -> 14 to 14
    anchor == Decor.WEEK -> 18 to 18
    anchor == Decor.HEAD -> 30 to 30
    anchor == Decor.WIDGET -> 36 to 36
    else -> 64 to 64
}

private enum class Pane(val label: String) { ADD("넣기"), EDIT("고치기"), LAYERS("레이어") }

/**
 * [LAY-04][LAY-05][LAY-06] 1.36 꾸밈 층 도구 (개발계획 877~879, 제안 6·25·15·13·8·30, 사용성 제안 '누른 요소 바로 고치기·자석·돋보기'):
 * - 붙는 곳 ([places]: this area's parts) and the 꾸미기 판, the part drawn big with its decorations: a tap picks the one under the finger
 *   (again on the same spot: the one under it), one finger moves it, two make it bigger or smaller and turn it. 자석 snaps its centre and
 *   edges to the part's middle, thirds and edges and to the others' centres, with the guide drawn; 돋보기 shows the spot under the finger
 *   three times bigger while moving.
 * - 넣기: 요소 모음 (도형·아이콘·이모지·글상자, 찾기), 고치기: the picked one's look, 1 dp 화살표, 날짜·근무 고정, 레이어: 부품 나무 under 붙는
 *   곳, 순서·잠금·숨김·묶기·복제·지우기, several picked at once.
 * - 고급 보기 ([advanced]): every value as a number (‰ place, dp size, 도, %), 눈금, several picked moved and changed together.
 * [onChange] with a label merges a run of the same change into one 되돌리기 step (끌기, 화살표, 밀대); null is a step of its own.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DecorTool(decor: Decor, places: List<String>, onChange: (String?, Decor) -> Unit, onUndo: (() -> Unit)? = null,
              advancedIn: Boolean? = null, onAdvanced: (Boolean) -> Unit = {}, focus: Int? = null, onFocused: () -> Unit = {},
              /** [LAY-11] The theme's 규칙 단추 a decoration may press (id to words); empty when it has none. */
              buttons: List<Pair<String, String>> = emptyList(),
              /** 1.38 PC 스튜디오 확인: each new value opens 넣기 and brings it into view (‘＋ 아이콘·사진’). */
              addAsk: Int = 0) {
    if (places.isEmpty()) return
    var place by rememberSaveable(places) {
        mutableStateOf(lastPlaces[places]?.takeIf { it in places } ?: decor.items.lastOrNull { it.at in places }?.at ?: places.first())
    }
    androidx.compose.runtime.LaunchedEffect(place, places) { lastPlaces[places] = place }
    val stageW = screenW()
    var picked by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var pane by rememberSaveable { mutableStateOf(Pane.ADD) }
    var addJump by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    // 6차 통합 2eb28e83 PC 확인 (‘＋ 아이콘·사진’을 누르면 꾸밈 판이 먼저 나와 아이콘 칸을 보려면 더 내려야 해요): asked for 넣기, the 판 folds
    // so the library stands right under the header; it opens again once something is put in or picked, to be moved there.
    var stageOpen by rememberSaveable { mutableStateOf(addAsk == 0) }
    // [LAY-09] 고급 보기 is the studio's own when it gives one (one switch for 부품·꾸밈 층·층 나무).
    var ownAdvanced by rememberSaveable { mutableStateOf(false) }
    val advanced = advancedIn ?: ownAdvanced
    fun flipAdvanced() { if (advancedIn == null) ownAdvanced = !ownAdvanced else onAdvanced(!advancedIn) }
    var magnet by rememberSaveable { mutableStateOf(true) }
    var loupe by rememberSaveable { mutableStateOf(true) }
    var many by rememberSaveable { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    // A picked index that no longer is (되돌리기, another place) is let go.
    val valid = picked.filter { it in decor.items.indices && decor.items[it].at == place }.toSet()
    if (valid != picked) picked = valid
    val items = decor.items
    // [LAY-09] 층 나무 opened one item: its place, picked, 고치기.
    androidx.compose.runtime.LaunchedEffect(focus) {
        val i = focus ?: return@LaunchedEffect
        items.getOrNull(i)?.takeIf { it.at in places }?.let { place = it.at; picked = setOf(i); pane = Pane.EDIT }
        onFocused()
    }

    fun set(label: String?, f: (DecorItem) -> DecorItem, which: Set<Int> = picked) {
        if (which.isEmpty()) return
        onChange(label, Decor(items.mapIndexed { i, it -> if (i in which) f(it) else it }))
    }
    DecorPhotos.Load(decor.images)
    fun add(kind: DecorKind, glyph: String, aspect: Float? = null, icon: Boolean = false) {
        if (items.size >= Decor.MAX) { said = "꾸밈은 ${Decor.MAX}개까지 넣을 수 있어요. 레이어에서 안 쓰는 것을 지워 주세요"; return }
        if (place == Decor.CELL && items.count { it.at == Decor.CELL } >= Decor.MAX_CELL) {
            said = "달력 칸에는 ${Decor.MAX_CELL}개까지예요(칸마다 그려서 달력 넘김이 느려지지 않게)"; return
        }
        // A free spot of the part, a little apart each time, so a second one does not hide the first.
        val n = items.count { it.at == place }
        val sp = startPlace(place, kind, n, stageW).let { st ->
            // [LAY-10] A photo keeps its own shape, its long side the place's size.
            if (kind != DecorKind.PHOTO || aspect == null) st else {
                val long = if (icon) iconSide(place) else photoSide(place)
                if (aspect >= 1f) st.copy(w = long, h = (long / aspect).roundToInt().coerceAtLeast(2)) else st.copy(w = (long * aspect).roundToInt().coerceAtLeast(2), h = long)
            }
        }
        // PC 확인 C6 1.36 (배경 사진은 화면을 채우게): a photo put on a screen's background fills the screen at first.
        val item = DecorItem(kind, glyph, place, x = sp.x, y = sp.y, w = sp.w, h = sp.h, r = if (kind == DecorKind.PHOTO && !icon) 8 else 0,
            full = kind == DecorKind.PHOTO && !icon && (place == Decor.APP || place.startsWith("bg.")),
            fill = if (kind == DecorKind.SHAPE) DEFAULT_FILLS[n % DEFAULT_FILLS.size] else null,
            fillA = if (glyph == "tape") 70 else 100,
            size = if (kind == DecorKind.TEXT) (when (place) { Decor.CELL -> 8.0; Decor.HEAD -> 11.0; else -> 14.0 }) else 14.0,
            // PC 확인 C2-1 1.36 (처음값 '모든 날'이라 넣자마자 모든 칸에 붙어요): a cell's new one stands on today's date, every month.
            on = if (place == Decor.CELL) "d" + com.bamnat.core.time.Today.date().dayOfMonth else null)
        onChange(null, Decor(items + item))
        picked = setOf(items.size)
        stageOpen = true
        if (place == Decor.CELL) {
            pane = Pane.EDIT
            said = "${quoted(item)}${eul(item.label)} 매달 ${com.bamnat.core.time.Today.date().dayOfMonth}일 칸에 넣었어요. 아래 ‘어느 날에 붙일까요’에서 바꿔요"
        } else said = "${quoted(item)}${eul(item.label)} 넣었어요. 판에서 끌어 옮기고, 두 손가락으로 키우고 돌려요"
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // 1.38 PC 스튜디오 확인 (넣기가 판 밑 화면 밖): 넣기로 가는 단추를 맨 위에.
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 7차 통합 347b7c37 PC 제안 (판이 접혀도 안내·자석 줄 때문에 아이콘 첫 줄이 화면 밖): folded, the long line and 보기 도구 step aside.
            if (stageOpen) Text("꾸밈 층: 도형·아이콘·이모지·글상자를 마음대로 놓아요. 날짜와 근무는 가리지 않아요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            else Text("아이콘·사진을 골라 넣어요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            com.bamnat.app.ui.components.FilledTonalButton(onClick = { pane = Pane.ADD; stageOpen = false; addJump++ }, modifier = Modifier.heightIn(min = 44.dp)) { Text("＋ 넣기") }
        }
        com.bamnat.app.ui.components.FoldRow("decor.1") {
            places.forEach { a ->
                val n = items.count { it.at == a }
                FilterChip(selected = a == place, onClick = { place = a; picked = emptySet() },
                    label = { Text(Decor.anchorLabel(a) + if (n > 0) " $n" else "") })
            }
        }
        // 8차 통합 e690bef6 PC (접기가 판 밑이라 두 번 내려야 보이고, 접으면 펼치기 줄이 화면 밖으로): one 접기·펼치기 line above the 판, so it
        // stays under the finger whichever way it goes; nothing above it changes height.
        val shown = items.count { it.at == place }
        com.bamnat.app.ui.components.OutlinedButton(onClick = { stageOpen = !stageOpen }, modifier = Modifier.fillMaxWidth().heightIn(min = 44.dp)) {
            Text(if (stageOpen) "꾸밈 판 접기 ▲" else "꾸밈 판 펼치기 ▼" + if (shown > 0) " (${shown}개)" else "")
        }
        if (stageOpen) Stage(items, place, picked, advanced, magnet, loupe,
            onPick = { i -> picked = when { i == null -> if (many) picked else emptySet(); many -> if (i in picked) picked - i else picked + i; else -> setOf(i) }
                if (i != null) pane = Pane.EDIT },
            onMove = { label, next -> onChange(label, Decor(next)) })
        if (stageOpen) com.bamnat.app.ui.components.FoldRow("decor.2", what = "보기 도구") {
            FilterChip(selected = magnet, onClick = { magnet = !magnet }, label = { Text("🧲 자석") })
            FilterChip(selected = loupe, onClick = { loupe = !loupe }, label = { Text("🔍 돋보기") })
            FilterChip(selected = many, onClick = { many = !many; if (!many && picked.size > 1) picked = setOf(picked.first()) }, label = { Text("여러 개 고르기") })
            FilterChip(selected = advanced, onClick = { flipAdvanced() }, label = { Text("고급 보기") })
        }
        said?.let { line ->
            androidx.compose.runtime.LaunchedEffect(line) { kotlinx.coroutines.delay(4000); said = null }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                // 사용성 제안 (사용자 2026-10-31 "얹은 뒤 그 자리에서 되돌리기"): right where it was said.
                if (onUndo != null && "넣었어요" in line) TextButton(onClick = { onUndo(); said = null; picked = emptySet() }) { Text("되돌리기") }
            }
        }
        // PC 확인 C4 1.36 (탭을 바꾸면 앞 탭에서 내린 자리 그대로 열려요): a new pane opens from its top, the tabs in sight.
        val tabsAt = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
        var shownPane by remember { mutableStateOf(pane) }
        androidx.compose.runtime.LaunchedEffect(pane) { if (pane != shownPane) { shownPane = pane; runCatching { tabsAt.bringIntoView() } } }
        androidx.compose.runtime.LaunchedEffect(addJump, addAsk) { if (addJump > 0 || addAsk > 0) { pane = Pane.ADD; if (addAsk > 0) stageOpen = false; runCatching { tabsAt.bringIntoView() } } }
        Row(Modifier.bringIntoViewRequester(tabsAt), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Pane.entries.forEach { p ->
                TextButton(onClick = { pane = p }, modifier = Modifier.heightIn(min = 44.dp)) {
                    Text(p.label + when (p) { Pane.EDIT -> if (picked.isEmpty()) "" else " ${picked.size}"; Pane.LAYERS -> " ${items.size}"; else -> "" },
                        fontWeight = if (p == pane) FontWeight.Bold else FontWeight.Normal,
                        color = if (p == pane) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        HorizontalDivider()
        when (pane) {
            Pane.ADD -> Library(place, { k, g -> add(k, g) }, onPhoto = { id, aspect -> add(DecorKind.PHOTO, id, aspect) },
                onMaterial = { m -> add(DecorKind.PHOTO, m.id, m.aspect, icon = m.isIcon) }, iconAsk = addAsk)
            Pane.EDIT -> if (picked.isEmpty()) Text("판에서 꾸밈을 누르거나, 레이어에서 골라요", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            else ItemEditor(items, picked, place, advanced, ::set, buttons,
                onCopy = {
                    if (items.size + picked.size > Decor.MAX) { said = "꾸밈은 ${Decor.MAX}개까지예요"; return@ItemEditor }
                    val copies = picked.sorted().map { i -> items[i].let { it.copy(x = (it.x + 40).coerceAtMost(1000), y = (it.y + 40).coerceAtMost(1000), lock = false) } }
                    onChange(null, Decor(items + copies)); picked = (items.size until items.size + copies.size).toSet()
                },
                onDelete = { onChange(null, Decor(items.filterIndexed { i, _ -> i !in picked })); picked = emptySet() },
                onOrder = { up -> onChange(null, Decor(reorder(items, picked, up))); picked = picked.map { (it + if (up) 1 else -1).coerceIn(items.indices) }.toSet() })
            Pane.LAYERS -> Layers(items, places, picked, onPick = { i, add -> picked = if (add || many) (if (i in picked) picked - i else picked + i) else setOf(i); place = items[i].at },
                set = { label, f, which -> set(label, f, which) },
                onGroup = {
                    val g = (1..9).firstOrNull { n -> items.none { it.group == n } } ?: 9
                    set(null, { it.copy(group = g) }); said = "${picked.size}개를 묶었어요. 하나를 옮기면 같이 움직여요"
                },
                onUngroup = { set(null, { it.copy(group = 0) }) },
                onMove = { from, up -> onChange(null, Decor(reorder(items, setOf(from), up))); picked = setOf(from + if (up) 1 else -1) },
                onEdit = { pane = Pane.EDIT })
        }
    }
}

private val DEFAULT_FILLS = listOf(0xFFFFB300, 0xFFEF5350, 0xFF42A5F5, 0xFF66BB6A, 0xFFAB47BC, 0xFFFF7043)

/** [picked] one step forward (drawn later, over the others) or back. */
private fun reorder(items: List<DecorItem>, picked: Set<Int>, up: Boolean): List<DecorItem> {
    val out = items.toMutableList()
    val order = if (up) picked.sortedDescending() else picked.sorted()
    for (i in order) {
        val j = if (up) i + 1 else i - 1
        if (j !in out.indices || j in picked) continue
        val t = out[i]; out[i] = out[j]; out[j] = t
    }
    return out
}

/**
 * The 꾸미기 판: [place] drawn big with its decorations, picked ones ringed. Moving keeps a live copy and gives [onMove] the whole list
 * each frame under one label ("꾸밈 옮기기"), so a drag is one 되돌리기 step.
 */
@Composable
private fun Stage(items: List<DecorItem>, place: String, picked: Set<Int>, advanced: Boolean, magnet: Boolean, loupe: Boolean,
                  onPick: (Int?) -> Unit, onMove: (String, List<DecorItem>) -> Unit) {
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val accent = MaterialTheme.colorScheme.primary
    val ink = MaterialTheme.colorScheme.onSurface
    val ground = MaterialTheme.colorScheme.surface
    val line = MaterialTheme.colorScheme.outlineVariant
    val ringColor = MaterialTheme.colorScheme.tertiary
    val part = partSize(place, screenW())
    val slop = LocalViewConfiguration.current.touchSlop
    val cur by rememberUpdatedState(items)
    val sel by rememberUpdatedState(picked)
    var guides by remember { mutableStateOf<Pair<Float?, Float?>>(null to null) }
    var finger by remember { mutableStateOf<Offset?>(null) }
    var lastTap by remember { mutableStateOf<Pair<Offset, Int>?>(null) }
    BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        val density = LocalDensity.current
        val maxH = if (place.startsWith("bg.") || place == Decor.APP) 300.dp else 180.dp
        // The part scaled to fit the width (and [maxH]): k px of the stage for each dp of the part.
        val k = with(density) { minOf(maxWidth.toPx() / part.width, maxH.toPx() / part.height) }
        val w = with(density) { (part.width * k).toDp() }
        val h = with(density) { (part.height * k).toDp() }
        Canvas(Modifier.size(w, h).clip(MaterialTheme.shapes.small).background(ground).border(1.dp, line, MaterialTheme.shapes.small)
            .semantics {
                contentDescription = "꾸미기 판: ${Decor.anchorLabel(place)}. 꾸밈 ${items.count { it.at == place && !it.hide }}개"
                stateDescription = if (picked.isEmpty()) "고른 꾸밈 없음" else "고른 꾸밈 ${picked.size}개"
            }
            .pointerInput(place, k, magnet) {
                // One density px of the part is k / density px of the stage.
                val partPx = Size(part.width * density.density, part.height * density.density)
                fun box() = Rect(0f, 0f, partPx.width, partPx.height)
                fun hit(at: Offset): List<Int> {
                    val p = Offset(at.x / k * density.density, at.y / k * density.density)
                    return cur.indices.filter { i -> cur[i].at == place && !cur[i].hide }
                        .filter { i -> placeOfPx(cur[i], box(), density.density).inflate(4 * density.density).contains(p) }.reversed()
                        // A 화면 가득 photo is the ground: what stands on it is found first.
                        .sortedBy { i -> cur[i].full }
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val under = hit(down.position)
                    // A second tap on the same spot goes to the one under (부모처럼 한 단계 아래로).
                    val again = lastTap?.takeIf { (o, _) -> (o - down.position).getDistance() < slop * 2 }
                    val choice = when {
                        under.isEmpty() -> null
                        again != null && again.second in under && sel.contains(again.second) -> under[(under.indexOf(again.second) + 1) % under.size]
                        sel.any { it in under } -> sel.first { it in under }
                        else -> under.first()
                    }
                    val moving = when {
                        choice == null -> emptySet()
                        choice in sel -> sel
                        else -> setOf(choice)
                    }
                    if (choice !in sel || choice == null) onPick(choice)
                    // Items in the same group as a moving one move with it.
                    val groups = moving.mapNotNull { cur.getOrNull(it)?.group?.takeIf { g -> g != 0 } }.toSet()
                    val all = (moving + cur.indices.filter { cur[it].group in groups && cur[it].at == place }).filter { !cur[it].lock }.toSet()
                    val start = cur
                    var total = Offset.Zero
                    var zoom = 1f
                    var turn = 0f
                    var started = false
                    do {
                        val e = awaitPointerEvent()
                        if (all.isEmpty()) continue
                        total += e.calculatePan()
                        zoom *= e.calculateZoom()
                        turn += e.calculateRotation()
                        if (!started && (total.getDistance() > slop || zoom != 1f || turn != 0f)) started = true
                        if (started) {
                            finger = e.changes.firstOrNull()?.position
                            // ‰ of the part per stage px.
                            val dx = total.x / k / part.width * 1000f
                            val dy = total.y / k / part.height * 1000f
                            val lead = start[choice ?: all.first()]
                            var nx = lead.x + dx
                            var ny = lead.y + dy
                            var gx: Float? = null
                            var gy: Float? = null
                            if (magnet && zoom == 1f && turn == 0f) {
                                val tolX = 6f / part.width * 1000f * (part.width / (w.value)) .coerceAtLeast(1f)
                                val tolY = 6f / part.height * 1000f * (part.height / (h.value)).coerceAtLeast(1f)
                                val halfW = lead.w / 2f / part.width * 1000f
                                val halfH = lead.h / 2f / part.height * 1000f
                                val others = start.filterIndexed { i, it -> i !in all && it.at == place && !it.hide }
                                val xs = listOf(500f, 333f, 667f) + others.map { it.x.toFloat() } +
                                    listOf(halfW, 1000f - halfW) + others.flatMap { o -> val ow = o.w / 2f / part.width * 1000f; listOf(o.x - ow - halfW, o.x + ow + halfW) }
                                val ys = listOf(500f, 333f, 667f) + (if (place == Decor.HEAD) listOf(HEAD_LINE.toFloat()) else emptyList()) + others.map { it.y.toFloat() } +
                                    listOf(halfH, 1000f - halfH) + others.flatMap { o -> val oh = o.h / 2f / part.height * 1000f; listOf(o.y - oh - halfH, o.y + oh + halfH) }
                                xs.minByOrNull { abs(it - nx) }?.takeIf { abs(it - nx) < tolX }?.let { gx = it; nx = it }
                                ys.minByOrNull { abs(it - ny) }?.takeIf { abs(it - ny) < tolY }?.let { gy = it; ny = it }
                            }
                            guides = gx to gy
                            val ddx = nx - lead.x
                            val ddy = ny - lead.y
                            val next = start.mapIndexed { i, it ->
                                if (i !in all) it else it.copy(
                                    x = (it.x + ddx).roundToInt().coerceIn(0, 1000), y = (it.y + ddy).roundToInt().coerceIn(0, 1000),
                                    w = (it.w * zoom).roundToInt().coerceIn(2, Decor.MAX_SIZE), h = (it.h * zoom).roundToInt().coerceIn(2, Decor.MAX_SIZE),
                                    rot = ((it.rot + turn).roundToInt() % 360 + 360) % 360,
                                )
                            }
                            onMove("꾸밈 옮기기", next)
                            e.changes.forEach { it.consume() }
                        }
                    } while (e.changes.any { it.pressed })
                    if (!started && choice != null) lastTap = down.position to choice
                    finger = null
                    guides = null to null
                }
            }) {
            val dpx = density.density
            val partPx = Size(part.width * dpx, part.height * dpx)
            val box = Rect(Offset.Zero, partPx)
            fun DrawScope.scene() {
                scale(k / dpx, k / dpx, pivot = Offset.Zero) {
                    backdrop(place, box, measurer, ink, line)
                    items.forEachIndexed { i, it ->
                        if (it.at != place || it.hide) return@forEachIndexed
                        drawDecor(it, box, measurer, accent, ink)
                        if (i in picked) {
                            val r = placeOfPx(it, box, dpx).inflate(2 * dpx)
                            drawRoundRect(ringColor, r.topLeft, r.size, CornerRadius(3 * dpx), style = Stroke(1.5f * dpx / (k / dpx).coerceAtLeast(0.1f),
                                pathEffect = if (it.lock) PathEffect.dashPathEffect(floatArrayOf(4 * dpx, 3 * dpx)) else null))
                        }
                    }
                }
            }
            scene()
            // 고급 보기: 눈금 (every 10 % of the part) and the thirds.
            if (advanced) for (n in 1..9) {
                val c = line.copy(alpha = if (n == 5) 0.9f else 0.35f)
                drawLine(c, Offset(size.width * n / 10, 0f), Offset(size.width * n / 10, size.height), strokeWidth = 1f)
                drawLine(c, Offset(0f, size.height * n / 10), Offset(size.width, size.height * n / 10), strokeWidth = 1f)
            }
            guides.first?.let { gx -> drawLine(Color(0xFFE91E63), Offset(size.width * gx / 1000, 0f), Offset(size.width * gx / 1000, size.height), strokeWidth = 1.5f * dpx) }
            guides.second?.let { gy -> drawLine(Color(0xFFE91E63), Offset(0f, size.height * gy / 1000), Offset(size.width, size.height * gy / 1000), strokeWidth = 1.5f * dpx) }
            // 돋보기: the spot under the finger three times bigger, in the corner away from it.
            val f = finger
            if (loupe && f != null) {
                val r = 44 * dpx
                val c = Offset(if (f.x > size.width / 2) r + 6 * dpx else size.width - r - 6 * dpx, r + 6 * dpx)
                val circle = Path().apply { addOval(Rect(c, r)) }
                drawCircle(ground, r, c)
                clipPath(circle) {
                    translate(c.x - f.x * 3, c.y - f.y * 3) { scale(3f, 3f, pivot = Offset.Zero) { scene() } }
                }
                drawCircle(ringColor, r, c, style = Stroke(2 * dpx))
                drawLine(ringColor, c - Offset(6 * dpx, 0f), c + Offset(6 * dpx, 0f), strokeWidth = 1 * dpx)
                drawLine(ringColor, c - Offset(0f, 6 * dpx), c + Offset(0f, 6 * dpx), strokeWidth = 1 * dpx)
            }
        }
    }
}

/** [placeOf] without a draw scope: [item]'s box in [box] at [density] px per dp. */
internal fun placeOfPx(item: DecorItem, box: Rect, density: Float): Rect {
    if (item.full) return box
    val w = item.w * density
    val h = item.h * density
    val cx = box.left + box.width * item.x / 1000f
    val cy = box.top + box.height * item.y / 1000f
    return Rect(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2)
}

/** What a part looks like behind its decorations on the 꾸미기 판, so a sticker is placed against a date, a title or a card. */
private fun DrawScope.backdrop(place: String, box: Rect, measurer: androidx.compose.ui.text.TextMeasurer, ink: Color, line: Color) {
    val d = density
    val soft = ink.copy(alpha = 0.55f)
    fun text(s: String, at: Offset, size: Float, c: Color = ink, bold: Boolean = false) {
        val l = measurer.measure(s, TextStyle(fontSize = (size / fontScale).sp, color = c, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal))
        drawText(l, topLeft = Offset(at.x - l.size.width / 2, at.y - l.size.height / 2))
    }
    when (place) {
        Decor.CELL -> {
            drawRoundRect(Color(0x1F3949AB), box.topLeft, box.size, CornerRadius(8 * d))
            text("25", Offset(box.width / 2, 12 * d), 13f, bold = true)
            drawRoundRect(Color(0xFF3949AB), Offset(box.width / 2 - 9 * d, 22 * d), Size(18 * d, 14 * d), CornerRadius(3 * d))
            text("야", Offset(box.width / 2, 29 * d), 8f, Color.White, bold = true)
            text("인수인계", Offset(box.width / 2, 46 * d), 7f, soft)
            text("8h", Offset(box.width / 2, 56 * d), 7f, soft)
        }
        Decor.HEAD -> {
            // PC 확인 C2 1.36: the top as the calendar lays it out — ‹ 2026년 11월 ▾ › … 오늘 [도구] — and the empty stretch marked.
            val w = box.width / d
            val cy = (8f + 24f) * d
            fun mid(r: Pair<Float, Float>) = (r.first + r.second) / 2 * d
            text("‹", Offset(mid(HeadLayout.navLeft(w)), cy), 20f, soft)
            text("2026년 11월 ▾", Offset(mid(HeadLayout.title(w)), cy), 14f, bold = true)
            text("›", Offset(mid(HeadLayout.navRight(w)), cy), 20f, soft)
            text("오늘", Offset(mid(HeadLayout.today(w)), cy), 12f, soft)
            val t = HeadLayout.tools(w)
            drawRoundRect(Color(0x332E7D32), Offset(t.first * d, cy - 18 * d), Size((t.second - t.first) * d, 36 * d), CornerRadius(18 * d))
            text("도구", Offset(mid(t), cy), 12f, soft)
            val f = HeadLayout.free(w)
            if (f.second - f.first > 8f) drawRoundRect(line, Offset(f.first * d, cy - 20 * d), Size((f.second - f.first) * d, 40 * d), CornerRadius(6 * d),
                style = Stroke(1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(4 * d, 3 * d))))
        }
        Decor.WEEK -> listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, s ->
            text(s, Offset(box.width * (i + 0.5f) / 7, box.height / 2), 11f, when (i) { 0 -> Color(0xFFD32F2F); 6 -> Color(0xFF1976D2); else -> soft }) }
        Decor.WIDGET -> { drawRoundRect(line.copy(alpha = 0.4f), box.topLeft, box.size, CornerRadius(20 * d))
            text("오늘 · 야간 22:00", Offset(box.width / 2, 30 * d), 14f, bold = true); text("내일 · 쉬는 날", Offset(box.width / 2, 60 * d), 12f, soft) }
        else -> {
            // A screen: its top bar, a few cards and the tab bar.
            drawRect(line.copy(alpha = 0.25f), Offset.Zero, Size(box.width, 56 * d))
            for (n in 0 until 4) drawRoundRect(line.copy(alpha = 0.35f), Offset(16 * d, (80 + n * 120) * d), Size(box.width - 32 * d, 100 * d), CornerRadius(12 * d))
            drawRect(line.copy(alpha = 0.25f), Offset(0f, box.height - 64 * d), Size(box.width, 64 * d))
        }
    }
}

/** 요소 모음: 도형·아이콘·이모지·글상자, each found by its words. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Library(place: String, onAdd: (DecorKind, String) -> Unit, onPhoto: (String, Float) -> Unit,
                    onMaterial: (com.bamnat.app.recipe.StudioMaterial) -> Unit, iconAsk: Int = 0) {
    var kind by rememberSaveable { mutableStateOf(if (iconAsk > 0) DecorKind.ICON else DecorKind.SHAPE) }
    // ‘＋ 아이콘·사진’ opens on 아이콘 (밤낮 색 아이콘 첫 줄), not on 도형.
    androidx.compose.runtime.LaunchedEffect(iconAsk) { if (iconAsk > 0) kind = DecorKind.ICON }
    var q by rememberSaveable { mutableStateOf("") }
    var words by rememberSaveable { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        com.bamnat.app.ui.components.FoldRow("decor.3") {
            DecorKind.entries.forEach { k -> FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) }) }
        }
        // 7차 통합 347b7c37 PC 제안 (아이콘 그림이 찾기 칸 아래라 한 번 더 내려야 해요): 아이콘's own search sits under the 밤낮 색 아이콘.
        if (kind != DecorKind.TEXT && kind != DecorKind.PHOTO && kind != DecorKind.ICON) OutlinedTextField(q, { q = it.take(20) }, Modifier.fillMaxWidth(), singleLine = true,
            label = { Text(when (kind) { DecorKind.EMOJI -> "이모지 찾기 (예: 달, 돈, 하트)"; else -> "찾기 (예: 별, 하트, 달)" }) })
        val ink = MaterialTheme.colorScheme.onSurface
        val accent = MaterialTheme.colorScheme.primary
        when (kind) {
            DecorKind.SHAPE -> GlyphGrid(DecorGlyphs.search(DecorGlyphs.SHAPE_LIST, q)) { g ->
                Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.small).clickable(onClickLabel = "${g.label} 넣기") { onAdd(DecorKind.SHAPE, g.id) }
                    .semantics { contentDescription = g.label }, contentAlignment = Alignment.Center) {
                    Canvas(Modifier.size(30.dp)) {
                        val p = DecorShapes.path(g.id, size.width, size.height)
                        if (g.id in DecorShapes.STROKES) drawPath(p, accent, style = Stroke(2.dp.toPx())) else drawPath(p, accent)
                    }
                }
            }
            DecorKind.ICON -> {
                // 1.38 [STM-06] 밤낮 색 아이콘 first while nothing is searched; the line icons (색을 바꿔 쓰는) follow.
                if (q.isBlank()) { MaterialShelf(com.bamnat.app.recipe.StudioMaterial.ICON, onMaterial); HorizontalDivider()
                    Text("선 아이콘 (색을 골라 써요)", style = MaterialTheme.typography.titleSmall) }
                OutlinedTextField(q, { q = it.take(20) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("선 아이콘 찾기 (예: 별, 하트, 달)") })
                GlyphGrid(DecorGlyphs.search(DecorGlyphs.ICON_LIST, q)) { g ->
                    Box(Modifier.size(52.dp).clip(MaterialTheme.shapes.small).clickable(onClickLabel = "${g.label} 넣기") { onAdd(DecorKind.ICON, g.id) },
                        contentAlignment = Alignment.Center) {
                        DecorIcons.of(g.id)?.let { Icon(it, g.label.substringBefore(' '), tint = ink, modifier = Modifier.size(28.dp)) }
                    }
                }
            }
            DecorKind.EMOJI -> {
                val list = if (q.isBlank()) RecipeText.emoji.take(160).map { it.emoji } else RecipeText.findEmoji(q).map { it.emoji }
                if (list.isEmpty()) Text("찾는 이모지가 없어요", style = MaterialTheme.typography.bodySmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    list.forEach { e ->
                        Box(Modifier.size(44.dp).clip(MaterialTheme.shapes.small).clickable(onClickLabel = "$e 넣기") { onAdd(DecorKind.EMOJI, e) },
                            contentAlignment = Alignment.Center) { Text(e, fontSize = 24.sp) }
                    }
                }
            }
            DecorKind.PHOTO -> {
                Text("내 사진", style = MaterialTheme.typography.titleSmall)
                PhotoAdd(onPhoto)
                HorizontalDivider()
                // 1.38 [STM-06] 밤낮 배경: pictures made for a screen's background, ready to put on.
                MaterialShelf(com.bamnat.app.recipe.StudioMaterial.BG, onMaterial)
            }
            DecorKind.TEXT -> {
                OutlinedTextField(words, { words = it.take(Decor.MAX_TEXT) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("넣을 글 (${Decor.MAX_TEXT}자까지)") })
                val problem = words.trim().takeIf { it.isNotEmpty() }?.let { RecipeText.problem(it) }
                problem?.let { Text(RecipeText.problemText(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                OutlinedButton(onClick = { onAdd(DecorKind.TEXT, RecipeText.clean(words)); words = "" }, enabled = words.isNotBlank() && problem == null) {
                    Text("글상자 넣기")
                }
                Text("나눌 때 받는 분이 이 글을 바꿔 쓸 수 있게 하려면 올리기에서 '받는 분이 넣을 값'으로 정해요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text("${Decor.anchorLabel(place)}에 넣어요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GlyphGrid(list: List<DecorGlyphs.Glyph>, cell: @Composable (DecorGlyphs.Glyph) -> Unit) {
    if (list.isEmpty()) { Text("찾는 것이 없어요. 다른 말로 찾아봐요", style = MaterialTheme.typography.bodySmall); return }
    list.groupBy { it.group }.forEach { (group, gs) ->
        Text(group, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) { gs.forEach { cell(it) } }
    }
}

/** The days a cell decoration may stand on, in the order they are offered. */
private val ON_CHOICES: List<String?> = listOf(null, "today", "holiday", "sat", "sun") + ShiftRole.entries.map { "role." + it.key } +
    // 1.39 [LINK-01] 근무 입력에 따라: 잔업·특근·대근, 대근, 연차·반차, 직접 바꾼 날.
    listOf("extra", "cover", "leave", "edited")

/** 고치기: the picked decorations' look, place and size; several picked are changed together. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ItemEditor(items: List<DecorItem>, picked: Set<Int>, place: String, advanced: Boolean,
                       set: (String?, (DecorItem) -> DecorItem, Set<Int>) -> Unit, buttons: List<Pair<String, String>>, onCopy: () -> Unit, onDelete: () -> Unit, onOrder: (Boolean) -> Unit) {
    val first = items[picked.min()]
    fun all(label: String?, f: (DecorItem) -> DecorItem) = set(label, f, picked)
    val part = partSize(place, screenW())
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(if (picked.size == 1) (if (first.kind == DecorKind.PHOTO) first.kind.label else "${first.kind.label} ${quoted(first)}") + (if (first.lock) " · 잠금" else "") else "${picked.size}개를 함께 고쳐요",
            style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        // PC 확인 C2-2 1.36 (넣자마자 '어느 날에 붙일까요'를 바로 보여 주기): a cell's day first, right under its name.
        if (place == Decor.CELL) {
            Text("어느 날에 붙일까요", style = MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ON_CHOICES.forEach { o -> FilterChip(selected = first.on == o, onClick = { all(null) { it.copy(on = o) } }, label = { Text(Decor.onLabel(o)) }) }
                FilterChip(selected = first.on?.startsWith("d") == true, onClick = { all(null) { it.copy(on = "d25") } }, label = { Text("매달 며칠") })
                FilterChip(selected = first.on?.matches(Regex("w[1-7]")) == true, onClick = { all(null) { it.copy(on = "w1") } }, label = { Text("매주 요일") })
                FilterChip(selected = first.on?.startsWith("m") == true, onClick = { all(null) { it.copy(on = "m1d1") } }, label = { Text("매년 그날") })
            }
            DayPinFields(first.on) { o -> all(null) { it.copy(on = o) } }
        }
        // 1 dp 화살표 (사용성 제안 '손가락으로 작은 스티커 위치를 정확히 맞추기 어려움').
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            val stepX = (1000f / part.width).roundToInt().coerceAtLeast(1)
            val stepY = (1000f / part.height).roundToInt().coerceAtLeast(1)
            fun nudge(dx: Int, dy: Int) = all("꾸밈 1dp 옮기기") { if (it.lock) it else it.copy(x = (it.x + dx).coerceIn(0, 1000), y = (it.y + dy).coerceIn(0, 1000)) }
            Text("옮기기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
            listOf("←" to (-stepX to 0), "→" to (stepX to 0), "↑" to (0 to -stepY), "↓" to (0 to stepY)).forEach { (s, d) ->
                OutlinedButton(onClick = { nudge(d.first, d.second) }, modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 48.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(s) }
            }
            Text("1dp씩", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("크기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(56.dp))
            listOf("−" to -1, "+" to 1).forEach { (s, d) ->
                OutlinedButton(onClick = { all("꾸밈 크기") { it.copy(w = (it.w + d).coerceIn(2, Decor.MAX_SIZE), h = (it.h + d).coerceIn(2, Decor.MAX_SIZE)) } },
                    modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(s) }
            }
            Text("돌리기", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 8.dp))
            listOf("↺" to -15, "↻" to 15).forEach { (s, d) ->
                OutlinedButton(onClick = { all("꾸밈 돌리기") { it.copy(rot = ((it.rot + d) % 360 + 360) % 360) } },
                    modifier = Modifier.heightIn(min = 44.dp).widthIn(min = 48.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text(s) }
            }
        }
        if (advanced) NumberFields(first, picked.size > 1) { label, f -> all(label, f) }
        when (first.kind) {
            DecorKind.SHAPE -> {
                ColorPick("채우기 색", first.fill, allowNone = true) { c -> all(null) { it.copy(fill = c) } }
                SliderRow("채우기 진하기", first.fillA.toFloat(), 0f..100f, "%") { v -> all("꾸밈 채우기 진하기") { it.copy(fillA = v.roundToInt()) } }
                if (first.glyph in setOf("rect", "round", "frame", "bubble", "bubble_r"))
                    SliderRow("모서리", first.r.toFloat(), 0f..48f, "dp") { v -> all("꾸밈 모서리") { it.copy(r = v.roundToInt()) } }
            }
            DecorKind.ICON -> ColorPick("아이콘 색", first.ink, allowNone = true) { c -> all(null) { it.copy(ink = c) } }
            DecorKind.TEXT -> {
                var words by remember(first.glyph) { mutableStateOf(first.glyph) }
                val problem = words.trim().takeIf { it.isNotEmpty() }?.let { RecipeText.problem(it) }
                OutlinedTextField(words, { s -> words = s.take(Decor.MAX_TEXT)
                    val t = RecipeText.clean(words)
                    if (t.isNotEmpty() && RecipeText.problem(t) == null) all("꾸밈 글") { it.copy(glyph = t) } },
                    Modifier.fillMaxWidth(), singleLine = true, label = { Text("글") }, isError = problem != null,
                    supportingText = problem?.let { p -> { Text(RecipeText.problemText(p)) } })
                SliderRow("글자 크기", first.size.toFloat(), 6f..48f, "sp") { v -> all("꾸밈 글자 크기") { it.copy(size = (v * 2).roundToInt() / 2.0) } }
                com.bamnat.app.ui.components.FoldRow("decor.4") {
                    FilterChip(selected = first.weight >= 700, onClick = { all(null) { it.copy(weight = if (first.weight >= 700) 400 else 700) } }, label = { Text("굵게") })
                    FilterChip(selected = first.italic, onClick = { all(null) { it.copy(italic = !first.italic) } }, label = { Text("기울임") })
                    listOf(null to "기본", "serif" to "명조", "hand" to "손글씨", "round" to "둥근", "mono" to "고정폭").forEach { (id, name) ->
                        FilterChip(selected = first.font == id, onClick = { all(null) { it.copy(font = id) } }, label = { Text(name, fontFamily = familyOf(id)) })
                    }
                }
                ColorPick("글자 색", first.ink, allowNone = true) { c -> all(null) { it.copy(ink = c) } }
                ColorPick("글상자 바탕", first.fill, allowNone = true) { c -> all(null) { it.copy(fill = c) } }
            }
            DecorKind.EMOJI -> {}
            DecorKind.PHOTO -> {
                if (place == Decor.APP || place.startsWith("bg.") || place == Decor.WIDGET) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = first.full, onClick = { all(null) { it.copy(full = true) } }, label = { Text("화면 가득") })
                    FilterChip(selected = !first.full, onClick = { all(null) { it.copy(full = false) } }, label = { Text("자리·크기 정하기") })
                }
                if (!first.full) SliderRow("모서리", first.r.toFloat(), 0f..48f, "dp") { v -> all("꾸밈 모서리") { it.copy(r = v.roundToInt()) } }
                // PC 확인 C7 1.36: why a 화면 가득 photo looks paler on the real screen.
                else if (!first.over) Text("글자가 묻히지 않게 사진 위에 화면 바탕색을 옅게 덮어요",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("올리면 운영자가 사진을 본 뒤 다른 분께 보여요. 그 전에는 화면 가득 사진은 기본 바탕, 자리를 정한 사진은 회색 상자로 보여요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (first.kind != DecorKind.EMOJI) {
            Text("선", style = MaterialTheme.typography.labelLarge)
            com.bamnat.app.ui.components.FoldRow("decor.5") {
                listOf(null to "없음", "solid" to "실선", "dash" to "끊은 선", "dot" to "점선", "double" to "겹선").forEach { (id, name) ->
                    FilterChip(selected = first.line == id, onClick = { all(null) { it.copy(line = id, lineW = if (id != null && it.lineW == 0.0) 1.0 else it.lineW) } },
                        label = { Text(name) })
                }
            }
            if (first.line != null || first.glyph in DecorShapes.STROKES) {
                SliderRow("선 두께", first.lineW.toFloat(), 0f..8f, "dp", step = 0.5f) { v -> all("꾸밈 선 두께") { it.copy(lineW = (v * 2).roundToInt() / 2.0) } }
                ColorPick("선 색", first.lineC, allowNone = true) { c -> all(null) { it.copy(lineC = c) } }
            }
        }
        SliderRow("투명도", first.alpha.toFloat(), 0f..100f, "%") { v -> all("꾸밈 투명도") { it.copy(alpha = v.roundToInt()) } }
        SliderRow("그림자", first.shadow.toFloat(), 0f..24f, "dp") { v -> all("꾸밈 그림자") { it.copy(shadow = v.roundToInt()) } }
        // PC 확인 C5 1.36 사용성 제안 (배경 사진이 칸 뒤에만 깔려 칸 사이로 조금만 보여요): a screen's background picks 칸 뒤 or 칸 위(흐리게)
        // in one tap — over the cells at a third, so the dates still read.
        if (place == Decor.APP || place.startsWith("bg.")) com.bamnat.app.ui.components.FoldRow("decor.6") {
            FilterChip(selected = !first.over, onClick = { all(null) { it.copy(over = false, alpha = if (it.over && it.alpha == 35) 100 else it.alpha) } }, label = { Text("칸·글 뒤") })
            FilterChip(selected = first.over && first.alpha <= 40, onClick = { all(null) { it.copy(over = true, alpha = minOf(it.alpha, 35)) } }, label = { Text("칸 위에 흐리게") })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("이 꾸밈을 날짜·근무·글 위에 그리기", style = MaterialTheme.typography.bodyMedium)
                Text(if (first.over) "글자를 가릴 수 있어요. 꼭 필요할 때만 켜요" else "꺼 두면 날짜·근무·글 밑에 그려요", style = MaterialTheme.typography.labelSmall,
                    color = if (first.over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(first.over, { v -> all(null) { it.copy(over = v) } })
        }
        // [LAY-11] 누르면 단추 (누락 점검 N8): the decoration presses one of the theme's 규칙 단추.
        Text("누르면", style = MaterialTheme.typography.labelLarge)
        if (buttons.isEmpty()) Text(if (first.tap != null) "‘${first.tap}’ 단추를 눌러요. 이 테마에 그 단추가 없으면 그림으로만 보여요"
            else "규칙에서 단추를 만들면, 이 꾸밈을 눌러 그 단추를 누를 수 있어요", style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        else com.bamnat.app.ui.components.FoldRow("decor.7") {
            FilterChip(selected = first.tap == null, onClick = { all(null) { it.copy(tap = null) } }, label = { Text("그림만") })
            buttons.forEach { (id, words) -> FilterChip(selected = first.tap == id, onClick = { all(null) { it.copy(tap = id) } }, label = { Text(words) }) }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AssistChip(onClick = { all(null) { it.copy(flip = !first.flip) } }, label = { Text("좌우 뒤집기") })
            AssistChip(onClick = { onOrder(true) }, label = { Text("앞으로") })
            AssistChip(onClick = { onOrder(false) }, label = { Text("뒤로") })
            AssistChip(onClick = { all(null) { it.copy(lock = !first.lock) } }, label = { Text(if (first.lock) "잠금 풀기" else "잠그기") })
            AssistChip(onClick = onCopy, label = { Text("복제") })
            AssistChip(onClick = onDelete, label = { Text("지우기", color = MaterialTheme.colorScheme.error) })
        }
    }
}

/** 매달 며칠·매주 요일·매년 그날's own numbers. */
@Composable
private fun DayPinFields(on: String?, onPick: (String) -> Unit) {
    when {
        on == null -> {}
        on.matches(Regex("d\\d+")) -> SliderRow("매달", on.drop(1).toFloat(), 1f..31f, "일") { onPick("d" + it.roundToInt()) }
        on.matches(Regex("w[1-7]")) -> Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("월", "화", "수", "목", "금", "토", "일").forEachIndexed { i, s ->
                FilterChip(selected = on == "w${i + 1}", onClick = { onPick("w${i + 1}") }, label = { Text(s) })
            }
        }
        on.startsWith("m") -> {
            val (m, d) = on.drop(1).split('d').let { (it[0].toIntOrNull() ?: 1) to (it.getOrNull(1)?.toIntOrNull() ?: 1) }
            SliderRow("매년", m.toFloat(), 1f..12f, "월") { onPick("m${it.roundToInt()}d$d") }
            SliderRow("그날", d.toFloat(), 1f..31f, "일") { onPick("m${m}d${it.roundToInt()}") }
        }
    }
}

/** 고급 보기: every value of the first picked as a number; with several picked, a value typed goes to all. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NumberFields(item: DecorItem, several: Boolean, set: (String?, (DecorItem) -> DecorItem) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("숫자로 맞추기" + if (several) " (고른 것 모두에)" else "", style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            NumberField("가로 자리 ‰", item.x, 0..1000) { v -> set(null) { it.copy(x = v) } }
            NumberField("세로 자리 ‰", item.y, 0..1000) { v -> set(null) { it.copy(y = v) } }
            NumberField("너비 dp", item.w, 2..Decor.MAX_SIZE) { v -> set(null) { it.copy(w = v) } }
            NumberField("높이 dp", item.h, 2..Decor.MAX_SIZE) { v -> set(null) { it.copy(h = v) } }
            NumberField("돌리기 도", item.rot, 0..359) { v -> set(null) { it.copy(rot = v) } }
            NumberField("투명도 %", item.alpha, 0..100) { v -> set(null) { it.copy(alpha = v) } }
            NumberField("모서리 dp", item.r, 0..48) { v -> set(null) { it.copy(r = v) } }
            NumberField("그림자 dp", item.shadow, 0..24) { v -> set(null) { it.copy(shadow = v) } }
            if (item.kind == DecorKind.TEXT) NumberField("굵기", item.weight, 100..900) { v -> set(null) { it.copy(weight = (v / 100) * 100) } }
            NumberField("묶음", item.group, 0..9) { v -> set(null) { it.copy(group = v) } }
        }
        HexField("채우기 색", item.fill) { c -> set(null) { it.copy(fill = c) } }
    }
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onSet: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    val n = text.toIntOrNull()
    OutlinedTextField(text, { s -> text = s.filter { it.isDigit() }.take(4); text.toIntOrNull()?.takeIf { it in range && it != value }?.let(onSet) },
        Modifier.width(104.dp), singleLine = true, label = { Text(label, maxLines = 1) }, isError = n == null || n !in range,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
}

@Composable
private fun HexField(label: String, value: Long?, onSet: (Long?) -> Unit) {
    var text by remember(value) { mutableStateOf(value?.let { (it and 0xFFFFFF).toString(16).uppercase().padStart(6, '0') } ?: "") }
    OutlinedTextField(text, { s ->
        text = s.uppercase().filter { it in "0123456789ABCDEF" }.take(6)
        if (text.length == 6) onSet(0xFF000000L or text.toLong(16)) else if (text.isEmpty()) onSet(null)
    }, Modifier.width(160.dp), singleLine = true, label = { Text("$label #") }, isError = text.isNotEmpty() && text.length != 6)
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, step: Float = 1f, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(88.dp))
        Slider(value.coerceIn(range), onChange, Modifier.weight(1f).semantics { contentDescription = label },
            valueRange = range, steps = ((range.endInclusive - range.start) / step).toInt() - 1)
        Text((if (step < 1f) "%.1f".format(value) else value.roundToInt().toString()) + unit, style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.width(48.dp))
    }
}

/**
 * [LAY-05] 레이어 패널 (제안 13): 부품 나무 — each 붙는 곳 under its part (앱 · 달력 · 위젯), its decorations front first. A row picks it (with
 * 여러 개 고르기 or the box, adds it), and shows 숨김·잠금 and ↑↓ at once; 묶기 joins the picked so they move together.
 */
@Composable
private fun Layers(items: List<DecorItem>, places: List<String>, picked: Set<Int>, onPick: (Int, Boolean) -> Unit,
                   set: (String?, (DecorItem) -> DecorItem, Set<Int>) -> Unit, onGroup: () -> Unit, onUngroup: () -> Unit, onMove: (Int, Boolean) -> Unit,
                   onEdit: () -> Unit = {}) {
    if (items.isEmpty()) { Text("아직 꾸밈이 없어요. 넣기에서 골라요", style = MaterialTheme.typography.bodyMedium); return }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OutlinedButton(onClick = onGroup, enabled = picked.size > 1) { Text("묶기") }
            OutlinedButton(onClick = onUngroup, enabled = picked.any { items.getOrNull(it)?.group != 0 }) { Text("묶음 풀기") }
        }
        val shown = (places + items.map { it.at }).distinct()
        shown.groupBy { Decor.parentOf(it) }.forEach { (parent, anchors) ->
            Text(when (parent) { Decor.APP -> "앱"; Decor.WIDGET -> "위젯"; else -> "달력" }, style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 6.dp))
            anchors.forEach { a ->
                val mine = items.indices.filter { items[it].at == a }.reversed()
                if (mine.isEmpty()) return@forEach
                Text("  " + Decor.anchorLabel(a), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                mine.forEach { i -> LayerRow(items[i], i in picked, onPick = { add -> onPick(i, add) }, set = { f -> set(null, f, setOf(i)) },
                    canUp = i < items.lastIndex, canDown = i > 0, onMove = { up -> onMove(i, up) }, onEdit = onEdit) }
            }
        }
    }
}

@Composable
private fun LayerRow(item: DecorItem, picked: Boolean, onPick: (Boolean) -> Unit, set: ((DecorItem) -> DecorItem) -> Unit,
                     canUp: Boolean, canDown: Boolean, onMove: (Boolean) -> Unit, onEdit: () -> Unit = {}) {
    // PC 확인 1.36 (레이어 줄 글자가 좁아 숨기기 단추가 눌려요): 숨기기·잠그기 on the row; 앞으로·뒤로·고치기 under the picked one.
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
        .background(if (picked) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)) {
    Row(Modifier.fillMaxWidth()
        .clickable(onClickLabel = "고르기") { onPick(false) }.padding(start = 16.dp),
        verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.Checkbox(picked, { onPick(true) })
        Text((if (item.group != 0) "🔗${item.group} " else "") + item.label + (if (item.on != null) " · " + Decor.onLabel(item.on) else ""),
            style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
            color = if (item.hide) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
        IconButton(onClick = { set { it.copy(hide = !item.hide) } }) {
            Icon(if (item.hide) androidx.compose.material.icons.Icons.Filled.VisibilityOff else androidx.compose.material.icons.Icons.Filled.Visibility ,
                if (item.hide) "숨김 풀기" else "숨기기")
        }
        IconButton(onClick = { set { it.copy(lock = !item.lock) } }) {
            Icon(if (item.lock) androidx.compose.material.icons.Icons.Filled.Lock else androidx.compose.material.icons.Icons.Filled.LockOpen ,
                if (item.lock) "잠금 풀기" else "잠그기")
        }
    }
    if (picked) Row(Modifier.fillMaxWidth().padding(start = 56.dp, bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = { onMove(true) }, enabled = canUp) { Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowUp, null); Text("앞으로") }
        TextButton(onClick = { onMove(false) }, enabled = canDown) { Icon(androidx.compose.material.icons.Icons.Filled.KeyboardArrowDown, null); Text("뒤로") }
        TextButton(onClick = onEdit) { Text("고치기 ›") }
    }
    }
}

/** For [Spacer] users that need a decoration's colour as an int (위젯). */
internal fun Color.argbLong(): Long = toArgb().toLong() and 0xFFFFFFFFL

@Suppress("unused") private val keepSpacer: @Composable () -> Unit = { Spacer(Modifier.height(0.dp)) }

/** 을 or 를 after [w], by its last syllable's 받침. */
internal fun eul(w: String): String {
    val c = w.lastOrNull { it in '가'..'힣' } ?: return "을(를)"
    return if ((c - '가') % 28 != 0) "을" else "를"
}

/** An item's name in a sentence: a text box's own words already stand in quotes. */
private fun quoted(i: DecorItem): String = if (i.kind == DecorKind.TEXT) i.label else "‘${i.label}’"

/** 1.38 [STM-06] A 밤낮 색 아이콘's side when it is put on [place], dp (smaller than a photo, like a sticker). */
private fun iconSide(place: String): Int = when (place) {
    Decor.CELL -> 14; Decor.WEEK -> 16; Decor.HEAD -> 28; Decor.WIDGET -> 40; else -> 64
}

/** [LAY-10] A photo's long side when it is put on [place], dp. */
private fun photoSide(place: String): Int = when (place) {
    Decor.CELL -> 20; Decor.WEEK -> 20; Decor.HEAD -> 40; Decor.WIDGET -> 72; else -> 140
}

/**
 * [LAY-10] 1.36 꾸밈 사진 (누락 점검 N12): one photo from the phone's photo picker (no permission asked), made small, upright and without
 * its place or camera ([LookImages.importWhole]).
 */
@Composable
private fun PhotoAdd(onPhoto: (String, Float) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    val pick = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val got = com.bamnat.app.recipe.LookImages.importWhole(context, uri)
            busy = false
            if (got == null) said = "이 사진은 열 수 없어요. 64px보다 큰 다른 사진을 골라 주세요" else { said = null; onPhoto(got.first, got.second) }
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        OutlinedButton(onClick = { pick.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (busy) "사진 준비하는 중…" else "사진 고르기") }
        said?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        Text("사진은 휴대폰에서 작게 줄이고 찍은 곳·기기 정보를 지워요. 장터에 올리면 운영자가 본 뒤에 다른 분께 보여요. 올릴 때 ‘사진 빼고 올리기’를 켜면 이 휴대폰에만 남아요",
            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

