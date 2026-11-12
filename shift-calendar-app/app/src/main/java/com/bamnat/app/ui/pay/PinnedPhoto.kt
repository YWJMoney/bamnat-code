package com.bamnat.app.ui.pay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.DropdownMenu
import com.bamnat.app.ui.components.FilterChip
import kotlin.math.roundToInt

/**
 * [PAYIN-01] 1.37 B1 (사용자 2026-11-04 "넣은 사진을 보면서 쉽게 입력… 지금은 클릭하고 닫고 입력하는 불편함"): the payslip photo stays
 * pinned over the lines while they are typed, never a window to open and close. It starts as wide as the screen from the top (a
 * payslip's 지급 is there), two fingers zoom, one finger moves it, a double tap zooms in there or back out. '사진 칸 넓게·좁게' change
 * how much of the screen it takes; '접기' leaves a thin bar to bring it back. With the keypad up it takes less, so the field stays seen.
 * In memory only, like every photo read here.
 * 2.0.0 [RVJ-61] (HQ-102, 총괄 #510 사진 08·09·11): its buttons lay on the photo and hid lines being read (two of four at big letters,
 * the last line at the open width), so they sit in a band of their own under it ([PhotoBand]), always there; the photo gives the
 * band its height, so the lines keep the room they had (PC 5566ff20).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PinnedPhotos(photos: List<Bitmap>, labels: List<String> = listOf("명세서", "계산 방법"), modifier: Modifier = Modifier) {
    if (photos.isEmpty()) return
    var index by rememberSaveable { mutableIntStateOf(0) }
    val screen = LocalConfiguration.current.screenHeightDp
    // A very small screen at very big letters has no room for photo and lines together: it starts folded (one tap opens it).
    val cramped = screen * 1.3f / LocalDensity.current.fontScale.coerceAtLeast(1f) < 500
    var folded by rememberSaveable { mutableStateOf(cramped) }
    var big by rememberSaveable { mutableStateOf(false) }
    val ime = WindowInsets.isImeVisible
    // A small phone or big letters keep the photo smaller, so the tabs and the first lines still show under it.
    val tight = screen < 700 || LocalDensity.current.fontScale > 1.3f
    val share = when {
        ime -> if (tight) 0.16f else 0.2f
        big -> 0.55f
        tight -> 0.2f
        else -> 0.26f
    }
    val photo = photos.getOrElse(index) { photos.first() }
    val names = photos.indices.map { labels.getOrElse(it) { "사진 ${it + 1}" } }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.fillMaxWidth()) {
        if (folded) Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (photos.size > 1) photos.indices.forEach { i ->
                FilterChip(selected = i == index, onClick = { index = i; folded = false }, label = { Text(names[i]) })
            }
            TextButton(onClick = { folded = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("사진 펴기") }
        } else Column(Modifier.fillMaxWidth()) {
            val zoom = rememberPhotoZoom(photo)
            ZoomablePhoto(photo, Modifier.fillMaxWidth().height(maxOf((screen * share).dp - BAND, MIN_PHOTO)), zoom)
            PhotoBand(
                zoom,
                choices = if (photos.size > 1) photos.indices.map { i -> BandAction(names[i], selected = i == index) { index = i } } else emptyList(),
                // '사진 크게·작게' is the zoom (⊕ ⊖, as the 근무표 photo); this one is how much room the photo takes.
                actions = listOf(BandAction(if (big) "사진 칸 좁게" else "사진 칸 넓게") { big = !big }, BandAction("접기") { folded = true }),
            )
        }
    }
}

/** [RVJ-61] (HQ-102) The band under a photo: the room it takes, and the least the photo keeps over it. */
private val BAND = 48.dp
private val MIN_PHOTO = 120.dp

/** [RVJ-61] (HQ-102) A button of [PhotoBand]: shown as itself while the band has room, in its ⋮ when it has not. */
internal class BandAction(val label: String, val selected: Boolean = false, val onClick: () -> Unit)

/**
 * 2.0.0 [RVJ-61] (HQ-102) The photo's buttons in one row under it, never on it: ⊖ ⊕ (and the zoom in words while zoomed, up to
 * 130% letters), then [choices] (which photo) and [actions] (사진 칸·접기) as they fit. When the row has no room for [actions] they
 * go into one ⋮; with no room for [choices] either, those too. [close] (the big photo's 닫기) always shows, at the end.
 */
@Composable
internal fun PhotoBand(
    zoom: PhotoZoom, modifier: Modifier = Modifier, choices: List<BandAction> = emptyList(), actions: List<BandAction> = emptyList(),
    close: BandAction? = null,
) {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val label = MaterialTheme.typography.labelLarge
    val small = MaterialTheme.typography.labelMedium
    fun width(text: String, style: TextStyle) = with(density) { measurer.measure(text, style).size.width.toDp() }
    // Material's own room around a label: a text button's 12dp each side (58dp at least), a chip's 8dp each side and its edge.
    fun buttonWidth(text: String) = maxOf(width(text, label) + 24.dp, 58.dp)
    val zoomed = zoom.scale > 1.01f
    val percent = "${(zoom.scale * 100).roundToInt()}%"
    val showPercent = zoomed && density.fontScale <= 1.3f
    BoxWithConstraints(modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
        val zoomWidth = 96.dp + if (showPercent) width(percent, small) + 20.dp else 0.dp
        val chipsWidth = choices.fold(0.dp) { w, c -> w + width(c.label, label) + 38.dp }
        val actionsWidth = actions.fold(0.dp) { w, a -> w + buttonWidth(a.label) }
        val closeWidth = close?.let { buttonWidth(it.label) } ?: 0.dp
        val room = maxWidth
        val actionsShown = actions.isEmpty() || zoomWidth + chipsWidth + actionsWidth + closeWidth <= room
        val chipsShown = choices.isEmpty() || zoomWidth + chipsWidth + (if (actionsShown) actionsWidth else 48.dp) + closeWidth <= room
        val inMenu = (if (chipsShown) emptyList() else choices) + (if (actionsShown) emptyList() else actions)
        Row(Modifier.fillMaxWidth().heightIn(min = BAND), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { zoom.zoomBy(1 / STEP) }, enabled = zoom.canZoomOut) { Icon(Icons.Filled.ZoomOut, contentDescription = "사진 작게") }
            IconButton(onClick = { zoom.zoomBy(STEP) }, enabled = zoom.canZoomIn) { Icon(Icons.Filled.ZoomIn, contentDescription = "사진 크게") }
            if (showPercent) Text(percent, style = small, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp))
            if (chipsShown) choices.forEach { c ->
                FilterChip(selected = c.selected, onClick = c.onClick, label = { Text(c.label) }, modifier = Modifier.padding(horizontal = 2.dp))
            }
            Spacer(Modifier.weight(1f))
            if (actionsShown) actions.forEach { a ->
                TextButton(onClick = a.onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(a.label, maxLines = 1) }
            }
            if (inMenu.isNotEmpty()) Box {
                var open by remember { mutableStateOf(false) }
                IconButton(onClick = { open = true }) { Icon(Icons.Filled.MoreVert, contentDescription = "사진 단추 더 보기") }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    inMenu.forEach { a ->
                        DropdownMenuItem(
                            text = { Text(if (a in choices) "${a.label} 사진" else a.label) },
                            // The ✓ goes after the words so every line in the menu starts at the same place.
                            trailingIcon = if (a.selected) ({ Icon(Icons.Filled.Check, contentDescription = "보는 중") }) else null,
                            onClick = { open = false; a.onClick() },
                        )
                    }
                }
            }
            close?.let { c -> TextButton(onClick = c.onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(c.label, maxLines = 1) } }
        }
    }
}

/**
 * [PAYIN-01] How far a photo is zoomed and moved, kept by its owner so the buttons under the photo ([PhotoBand]) and the photo itself
 * ([ZoomablePhoto]) share it. Never moved past the photo's edges.
 */
@Stable
internal class PhotoZoom(private val photoWidth: Int, private val photoHeight: Int) {
    var box by mutableStateOf(IntSize.Zero)
    var scale by mutableFloatStateOf(1f)
        private set
    var offset by mutableStateOf(Offset.Zero)
        private set
    val canZoomOut: Boolean get() = scale > 1.01f
    val canZoomIn: Boolean get() = scale < MAX_ZOOM - 0.01f

    private fun clamp(o: Offset, s: Float): Offset {
        if (box.width == 0) return o
        val w = box.width * s
        val h = box.width.toFloat() * photoHeight / photoWidth.coerceAtLeast(1) * s
        val x = o.x.coerceIn(minOf(0f, box.width - w), 0f)
        val y = if (h <= box.height) 0f else o.y.coerceIn(box.height - h, 0f)
        return Offset(x, y)
    }

    fun zoomBy(f: Float, at: Offset = Offset(box.width / 2f, box.height / 2f)) {
        val next = (scale * f).coerceIn(1f, MAX_ZOOM)
        offset = clamp(at - (at - offset) * (next / scale), next)
        scale = next
    }

    fun move(dx: Float, dy: Float): Boolean { offset = clamp(offset + Offset(dx, dy), scale); return true }

    fun pinch(centroid: Offset, pan: Offset, zoom: Float) {
        val next = (scale * zoom).coerceIn(1f, MAX_ZOOM)
        val moved = centroid - (centroid - offset) * (next / scale) + pan
        scale = next
        offset = clamp(moved, next)
    }

    fun doubleTap(at: Offset) {
        if (scale > 1.01f) { scale = 1f; offset = clamp(Offset.Zero, 1f) } else zoomBy(DOUBLE_TAP, at)
    }
}

@Composable
internal fun rememberPhotoZoom(photo: Bitmap): PhotoZoom = remember(photo) { PhotoZoom(photo.width, photo.height) }

/**
 * [PAYIN-01] One photo filling [modifier]'s box from the top, as wide as the box; zoomed and moved by hand, never past its edges.
 * 2.0.0 [RVJ-61] 검토 HQ-81 (PC 3차): two fingers and a double tap were the only zoom. ⊖ ⊕ ([PhotoBand], under the photo since
 * (HQ-102)) and the read-aloud actions (크게·작게·사진을 왼쪽·오른쪽·위·아래로) do it too, as the 근무표 photo (갈래 L RVL-35,
 * `RosterPhoto`) does, for one hand, TalkBack and Switch Access.
 */
@Composable
internal fun ZoomablePhoto(photo: Bitmap, modifier: Modifier = Modifier, zoom: PhotoZoom = rememberPhotoZoom(photo), onTap: (() -> Unit)? = null) {
    val image = remember(photo) { photo.asImageBitmap() }
    Box(
        modifier.clipToBounds().onSizeChanged { zoom.box = it }
            .semantics {
                contentDescription = "읽은 명세서 사진, 두 손가락이나 ⊕로 키워 봐요"
                customActions = listOf(
                    CustomAccessibilityAction("크게") { zoom.zoomBy(STEP); true },
                    CustomAccessibilityAction("작게") { zoom.zoomBy(1 / STEP); true },
                    CustomAccessibilityAction("사진을 왼쪽으로") { zoom.move(zoom.box.width / 4f, 0f) },
                    CustomAccessibilityAction("사진을 오른쪽으로") { zoom.move(-zoom.box.width / 4f, 0f) },
                    CustomAccessibilityAction("사진을 위로") { zoom.move(0f, zoom.box.height / 4f) },
                    CustomAccessibilityAction("사진을 아래로") { zoom.move(0f, -zoom.box.height / 4f) },
                )
            }
            .pointerInput(zoom) { detectTransformGestures { centroid, pan, z, _ -> zoom.pinch(centroid, pan, z) } }
            .pointerInput(zoom) { detectTapGestures(onTap = { onTap?.invoke() }, onDoubleTap = zoom::doubleTap) },
    ) {
        Image(
            image, contentDescription = null, contentScale = ContentScale.FillBounds,
            // The whole photo laid out at the box's width (taller than the box when it is a long payslip), the box cutting it.
            modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .aspectRatio(photo.width.coerceAtLeast(1).toFloat() / photo.height.coerceAtLeast(1))
                .graphicsLayer {
                    scaleX = zoom.scale; scaleY = zoom.scale
                    translationX = zoom.offset.x; translationY = zoom.offset.y
                    transformOrigin = TransformOrigin(0f, 0f)
                },
        )
    }
}

/** [RVJ-61] The 근무표 photo's zoom steps (RVL-35): up to 6×, ⊕ ⊖ by 1.5×, a double tap to 2.5×. */
private const val MAX_ZOOM = 6f
private const val STEP = 1.5f
private const val DOUBLE_TAP = 2.5f
