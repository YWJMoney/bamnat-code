package com.bamnat.app.ui.memo

import android.content.ActivityNotFoundException
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.Dashboard
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import com.bamnat.app.ui.components.DropdownMenu
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.memo.MemoPhotos
import com.bamnat.core.memo.MemoPhotos.Align
import com.bamnat.core.memo.MemoPhotos.Photo
import kotlinx.coroutines.launch

/**
 * [MPH-01][MPH-02] 1.38 메모 사진, the editor's side. The body is drawn as its blocks ([MemoPhotos.parse]): a text field for each
 * run of words and a photo for each photo line. Without photos it is the one body field it always was.
 *
 * A photo is tapped to choose it; then a finger on its corner makes it bigger or smaller, and the row under it gives 작게·반·크게·꽉,
 * 왼쪽·가운데·오른쪽, 옆에 글 쓰기 (the words beside it, like a word processor's 텍스트 감싸기), 위로·아래로, 크게 보기 and 빼기. 완료 or a
 * second tap lets it go.
 */
@Composable
internal fun MemoBlocksBody(
    body: String,
    onBody: (String) -> Unit,
    textStyle: TextStyle,
    /** A run of words: index of its block, its words, what to call with new words, whether it is the first and the last one. */
    field: @Composable (index: Int, text: String, onText: (String) -> Unit, first: Boolean, last: Boolean) -> Unit,
    /** The words beside a photo. */
    sideField: @Composable (index: Int, text: String, onText: (String) -> Unit, modifier: Modifier) -> Unit,
    /** A checklist keeps its words in the list: its body shows only photos, no field after them. */
    trailingField: Boolean = true,
    /** The body as it is now (an edit made since this was drawn is not lost when the next one is written). */
    current: () -> String = { body },
    /** [MPH-07] 자유 판 '+ 사진': open the photo picker for the board at this block; each photo comes back through the given call. */
    onBoardPhoto: ((index: Int) -> Unit)? = null,
) {
    val parsed = remember(body) { MemoPhotos.parse(body) }
    // A memo ending with a photo gets an empty field after it, so the words can go on under it.
    val virtual = trailingField && parsed.lastOrNull() is Photo
    val blocks = if (virtual) parsed + MemoPhotos.Text("") else parsed
    var chosen by remember { mutableStateOf<Int?>(null) }
    var viewing by remember { mutableStateOf<String?>(null) }
    val count = parsed.size
    fun write(i: Int, b: MemoPhotos.Block) {
        val now = current()
        val fresh = MemoPhotos.parse(now)
        val freshVirtual = trailingField && fresh.lastOrNull() is Photo
        val all = if (freshVirtual) fresh + MemoPhotos.Text("") else fresh
        if (i !in all.indices || all[i]::class != b::class) return
        val next = all.toMutableList().also { it[i] = b }
        val kept = if (freshVirtual && (next.last() as? MemoPhotos.Text)?.text.isNullOrEmpty()) next.dropLast(1) else next
        val joined = MemoPhotos.join(kept)
        if (joined != now) onBody(joined)
    }
    val firstText = blocks.indexOfFirst { it is MemoPhotos.Text }
    val lastText = blocks.indexOfLast { it is MemoPhotos.Text }
    Column {
        blocks.forEachIndexed { i, b ->
            key(i, b::class) {
                when (b) {
                    is MemoPhotos.Text -> field(i, b.text, { write(i, MemoPhotos.Text(it)) }, i == firstText, i == lastText)
                    is Photo -> MemoPhotoBlock(
                        photo = b,
                        chosen = chosen == i,
                        canUp = i > 0,
                        canDown = i < count - 1,
                        onChoose = { chosen = if (chosen == i) null else i },
                        onChange = { p -> write(i, p) },
                        onRemove = { chosen = null; onBody(MemoPhotos.remove(current(), i)) },
                        onUnwrap = { onBody(MemoPhotos.unwrap(current(), i)) },
                        onMove = { by -> chosen = (i + by).coerceIn(0, count - 1); onBody(MemoPhotos.move(current(), i, by)) },
                        onView = { viewing = b.name },
                        side = { mod -> sideField(i, b.side.orEmpty(), { write(i, b.copy(side = it)) }, mod) },
                        textStyle = textStyle,
                    )
                    // [MPH-05] The paper line is drawn by the fields, not shown.
                    is MemoPhotos.Paper -> Unit
                    is MemoPhotos.Board -> MemoBoardBlock(
                        board = b,
                        chosen = chosen == i,
                        canUp = i > 0,
                        canDown = i < count - 1,
                        onChoose = { chosen = if (chosen == i) null else i },
                        onChange = { nb -> write(i, nb) },
                        onRemove = { chosen = null; onBody(MemoPhotos.remove(current(), i)) },
                        onMove = { by -> chosen = (i + by).coerceIn(0, count - 1); onBody(MemoPhotos.move(current(), i, by)) },
                        onAddPhoto = { onBoardPhoto?.invoke(i) },
                        textStyle = textStyle,
                    )
                }
            }
        }
    }
    viewing?.let { name -> MemoPhotoViewer(name) { viewing = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MemoPhotoBlock(
    photo: Photo,
    chosen: Boolean,
    canUp: Boolean,
    canDown: Boolean,
    onChoose: () -> Unit,
    onChange: (Photo) -> Unit,
    onRemove: () -> Unit,
    onUnwrap: () -> Unit,
    onMove: (Int) -> Unit,
    onView: () -> Unit,
    side: @Composable (Modifier) -> Unit,
    textStyle: TextStyle,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    // The width while a finger drags the corner; written to the memo when the finger lets go (one step to undo).
    var live by remember(photo.width) { mutableIntStateOf(photo.width) }
    val most = if (photo.wraps) MemoPhotos.MAX_SIDE_WIDTH else 100
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val full = maxWidth
            val fullPx = with(density) { full.toPx() }.coerceAtLeast(1f)
            val w = full * (live / 100f)
            val widthPx = with(density) { w.toPx() }.toInt()
            val ratio by produceState<Float?>(null, photo.name) { value = MemoPhotoFiles.ratio(context, photo.name) }
            val image by produceState<ImageBitmap?>(null, photo.name, widthPx / 200) { value = MemoPhotoFiles.load(context, photo.name, widthPx) }
            val picture: @Composable () -> Unit = {
                // [MPH-06] 스튜디오 부품 '메모 사진'(memo.photo): 모서리·테두리·그림자·틀(채우기+안 여백, 폴라로이드처럼).
                val photoPart = com.bamnat.app.ui.parts.partStyle("memo.photo")
                val photoShape = com.bamnat.app.ui.parts.PartMods.shapeOf(photoPart, RoundedCornerShape(10.dp))
                Box(
                    Modifier
                        .width(w)
                        .then(com.bamnat.app.ui.parts.PartMods.box(photoPart, photoShape))
                        .aspectRatio(ratio ?: (4f / 3f))
                        .clip(photoShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .then(if (chosen) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                        .clickable(onClickLabel = if (chosen) "사진 고르기 끝" else "사진 고르기") { onChoose() }
                        .semantics { contentDescription = "메모 사진, 너비 ${live}%" },
                ) {
                    val img = image
                    if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else if (ratio == null) Text("사진을 찾지 못했어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.Center).padding(8.dp))
                    if (chosen) {
                        // The corner to drag: away from where the photo is pinned (a photo at the right grows to the left).
                        val corner = if (photo.align == Align.RIGHT) Alignment.BottomStart else Alignment.BottomEnd
                        val sign = if (photo.align == Align.RIGHT) -1f else 1f
                        val both = if (photo.align == Align.CENTER) 2f else 1f
                        Box(
                            Modifier
                                .align(corner)
                                .padding(4.dp)
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary)
                                .pointerInput(photo.align, most) {
                                    var start = 0f
                                    detectDragGestures(
                                        onDragStart = { start = live.toFloat() },
                                        onDragEnd = { if (live != photo.width) onChange(photo.copy(width = live)) },
                                        onDragCancel = { live = photo.width },
                                    ) { change, drag ->
                                        change.consume()
                                        start += drag.x * sign * both / fullPx * 100f
                                        live = start.toInt().coerceIn(MemoPhotos.MIN_WIDTH, most)
                                    }
                                }
                                .semantics { contentDescription = "끌어서 크기 바꾸기" },
                            contentAlignment = Alignment.Center,
                        ) { Text("↔", color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            if (photo.wraps) {
                Row(Modifier.fillMaxWidth()) {
                    if (photo.align == Align.LEFT) { picture(); Spacer(Modifier.width(10.dp)) }
                    side(Modifier.weight(1f).heightIn(min = 48.dp))
                    if (photo.align == Align.RIGHT) { Spacer(Modifier.width(10.dp)); picture() }
                }
            } else {
                Box(Modifier.fillMaxWidth(), contentAlignment = when (photo.align) {
                    Align.LEFT -> Alignment.CenterStart; Align.CENTER -> Alignment.Center; Align.RIGHT -> Alignment.CenterEnd
                }) { picture() }
            }
        }
        if (chosen) Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
        ) {
            // Wrapped, never a row to scroll sideways: every choice is in sight.
            FlowRow(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("크기 ${live}%", style = MaterialTheme.typography.labelLarge, modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp))
                listOf(33 to "작게", 50 to "반", 75 to "크게", 100 to "꽉 차게").forEach { (pct, label) ->
                    val p = pct.coerceAtMost(most)
                    FilterChip(selected = live == p, onClick = { live = p; onChange(photo.copy(width = p)) }, label = { Text(label) })
                }
                Align.entries.forEach { a ->
                    FilterChip(selected = photo.align == a, onClick = {
                        // Words beside a photo moved to the middle stay, under it (MemoPhotos.join).
                        onChange(photo.copy(align = a))
                    }, label = { Text(a.word) })
                }
                FilterChip(
                    selected = photo.wraps,
                    onClick = {
                        if (photo.wraps) onUnwrap() // the words go on under it
                        else onChange(photo.copy(
                            side = photo.side.orEmpty(),
                            align = if (photo.align == Align.CENTER) Align.LEFT else photo.align,
                            width = photo.width.coerceAtMost(50),
                        ))
                    },
                    label = { Text("옆에 글 쓰기") },
                )
                TextButton(onClick = { onMove(-1) }, enabled = canUp) { Text("↑ 위로") }
                TextButton(onClick = { onMove(1) }, enabled = canDown) { Text("↓ 아래로") }
                TextButton(onClick = onView) { Text("크게 보기") }
                TextButton(onClick = onRemove) { Text("빼기", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = onChoose) { Text("완료") }
            }
        }
        if (chosen && photo.wraps) Text(
            "사진 옆 칸에 쓴 글은 사진 옆에 놓여요. 사진 밑에 이어 쓰려면 사진 아래 빈 곳을 누르세요",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp),
        )
    }
}

/** [MPH-02] 크게 보기: the photo on the whole screen, two fingers to zoom, one to move, a double tap back; ✕ to close. */
@Composable
internal fun MemoPhotoViewer(name: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(null, name) { value = MemoPhotoFiles.load(context, name, MemoPhotoFiles.MAX_SIDE) }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            image?.let { img ->
                Image(img, "메모 사진", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y })
            }
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                .background(Color.Black.copy(alpha = 0.5f), CircleShape)) {
                Icon(Icons.Filled.Close, "닫기", tint = Color.White)
            }
        }
    }
}

/**
 * [MPH-01] 사진 넣기: 사진 고르기 (the system photo picker, several at once, no permission) or 사진 찍기 (the camera app). Each photo goes
 * in through [onPhoto] as it is copied in; [menu] opens the choice.
 */
class MemoPhotoPicker internal constructor(val open: () -> Unit, val pick: () -> Unit)

@Composable
internal fun rememberMemoPhotoPicker(
    left: () -> Int,
    /** [MPH-07] '자유 판 넣기' in the same menu; null hides it. */
    onBoard: (() -> Unit)? = null,
    /** Called when the menu opens (a board that asked for photos before is let go). */
    onOpen: () -> Unit = {},
    onPhoto: (String) -> Unit,
): Pair<MemoPhotoPicker, @Composable () -> Unit> {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    fun take(uris: List<Uri>) {
        if (uris.isEmpty()) return
        val room = left()
        if (room <= 0) { Toast.makeText(context, "메모 하나에 사진은 ${MemoPhotos.MAX_PER_MEMO}장까지 넣을 수 있어요", Toast.LENGTH_LONG).show(); return }
        busy = true
        scope.launch {
            var failed = 0
            for (uri in uris.take(room)) {
                val name = MemoPhotoFiles.import(context, uri)
                if (name != null) onPhoto(name) else failed++
            }
            busy = false
            if (uris.size > room) Toast.makeText(context, "메모 하나에 사진은 ${MemoPhotos.MAX_PER_MEMO}장까지라 ${room}장만 넣었어요", Toast.LENGTH_LONG).show()
            else if (failed > 0) Toast.makeText(context, "사진 ${failed}장을 열지 못했어요", Toast.LENGTH_SHORT).show()
        }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(10)) { take(it) }
    var shot by remember { mutableStateOf<Uri?>(null) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val uri = shot
        if (ok && uri != null) take(listOf(uri))
    }
    val picker = remember {
        MemoPhotoPicker(open = { onOpen(); menu = true }, pick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
    }
    val ui: @Composable () -> Unit = {
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text("사진 고르기") },
                leadingIcon = { Icon(Icons.Outlined.PhotoLibrary, null) },
                onClick = { menu = false; pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            )
            DropdownMenuItem(
                text = { Text("사진 찍기") },
                leadingIcon = { Icon(Icons.Outlined.PhotoCamera, null) },
                onClick = {
                    menu = false
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.share", MemoPhotoFiles.cameraFile(context))
                    shot = uri
                    try { camera.launch(uri) } catch (e: ActivityNotFoundException) {
                        Toast.makeText(context, "카메라 앱을 찾지 못했어요. ‘사진 고르기’로 골라 주세요", Toast.LENGTH_LONG).show()
                    }
                },
            )
            if (onBoard != null) DropdownMenuItem(
                text = { Column { Text("자유 판 넣기"); Text("사진·글상자·스티커를 아무 자리에", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant) } },
                leadingIcon = { Icon(Icons.Outlined.Dashboard, null) },
                onClick = { menu = false; onBoard() },
            )
        }
        if (busy) Text("  사진 넣는 중…", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    return picker to ui
}

/** [MPH-01] The chip in the editor's row: 사진 넣기 and how many the memo has. */
@Composable
internal fun MemoPhotoChip(count: Int, picker: MemoPhotoPicker, menu: @Composable () -> Unit) {
    Box {
        AssistChip(
            onClick = picker.open,
            label = { Text(if (count > 0) "사진 ${count}장 · 넣기" else "사진 넣기") },
            leadingIcon = { Icon(Icons.Outlined.AddPhotoAlternate, null, Modifier.size(18.dp)) },
        )
        menu()
    }
}

/** [MPH-04] 1.38 Up to three of a memo's photos, small, on its card in the list; "+N" for the rest. */
@Composable
internal fun MemoPhotoStrip(names: List<String>) {
    val context = LocalContext.current
    Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        names.take(3).forEach { name ->
            val img by produceState<ImageBitmap?>(null, name) { value = MemoPhotoFiles.load(context, name, 240) }
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
            }
        }
        if (names.size > 3) Text("+${names.size - 3}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Spacer(Modifier.size(2.dp))
}
