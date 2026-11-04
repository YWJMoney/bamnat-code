package com.bamnat.app.ui.pay

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp

/**
 * [PAYIN-01] 1.37 B1 (사용자 2026-11-04 "넣은 사진을 보면서 쉽게 입력… 지금은 클릭하고 닫고 입력하는 불편함"): the payslip photo stays
 * pinned over the lines while they are typed, never a window to open and close. It starts as wide as the screen from the top (a
 * payslip's 지급 is there), two fingers zoom, one finger moves it, a double tap zooms in there or back out. '크게'·'작게' change how
 * much of the screen it takes; '접기' leaves a thin bar to bring it back. With the keypad up it takes less, so the field stays seen.
 * In memory only, like every photo read here.
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
        ime -> if (tight) 0.18f else 0.24f
        big -> 0.55f
        tight -> 0.22f
        else -> 0.3f
    }
    val photo = photos.getOrElse(index) { photos.first() }
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHighest, modifier = modifier.fillMaxWidth()) {
        androidx.compose.foundation.layout.Column {
            if (!folded) ZoomablePhoto(photo, Modifier.fillMaxWidth().height((screen * share).dp))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (photos.size > 1) photos.indices.forEach { i ->
                    FilterChip(selected = i == index, onClick = { index = i; folded = false }, label = { Text(labels.getOrElse(i) { "사진 ${i + 1}" }) })
                }
                if (folded) TextButton(onClick = { folded = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("사진 펴기") }
                else {
                    if (!tight) Text("두 손가락으로 키우고 밀어서 봐요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { big = !big }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (big) "사진 작게" else "사진 크게") }
                    TextButton(onClick = { folded = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("접기") }
                }
            }
        }
    }
}

/** [PAYIN-01] One photo filling [modifier]'s box from the top, as wide as the box; zoomed and moved by hand, never past its edges. */
@Composable
internal fun ZoomablePhoto(photo: Bitmap, modifier: Modifier = Modifier) {
    val image = remember(photo) { photo.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    var scale by remember(photo) { mutableFloatStateOf(1f) }
    var offset by remember(photo) { mutableStateOf(Offset.Zero) }
    fun clamp(o: Offset, s: Float): Offset {
        if (box.width == 0) return o
        val w = box.width * s
        val h = box.width.toFloat() * photo.height / photo.width.coerceAtLeast(1) * s
        val x = o.x.coerceIn(minOf(0f, box.width - w), 0f)
        val y = if (h <= box.height) 0f else o.y.coerceIn(box.height - h, 0f)
        return Offset(x, y)
    }
    Box(
        modifier.clipToBounds().onSizeChanged { box = it }
            .semantics { contentDescription = "읽은 명세서 사진, 두 손가락으로 키워 봐요" }
            .pointerInput(photo) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val next = (scale * zoom).coerceIn(1f, 6f)
                    val moved = centroid - (centroid - offset) * (next / scale) + pan
                    scale = next
                    offset = clamp(moved, next)
                }
            }
            .pointerInput(photo) {
                detectTapGestures(onDoubleTap = { at ->
                    if (scale > 1.01f) { scale = 1f; offset = clamp(Offset.Zero, 1f) }
                    else { val next = 2.5f; offset = clamp(at - (at - offset) * next, next); scale = next }
                })
            },
    ) {
        Image(
            image, contentDescription = null, contentScale = ContentScale.FillBounds,
            // The whole photo laid out at the box's width (taller than the box when it is a long payslip), the box cutting it.
            modifier = Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true)
                .aspectRatio(photo.width.coerceAtLeast(1).toFloat() / photo.height.coerceAtLeast(1))
                .graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
                transformOrigin = TransformOrigin(0f, 0f)
            },
        )
        if (scale > 1.01f) Text("${(scale * 100).toInt()}%", style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(width = 44.dp, height = 18.dp))
    }
}
