package com.bamnat.app.ui.settings

import android.graphics.RectF
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.bamnat.app.recipe.LookImages
import com.bamnat.app.recipe.LookImages.Slot
import com.bamnat.app.appContainer
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.theme.rememberLookPicture
import com.bamnat.core.recipe.PhotoLook
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** The 설정 row title, also in [SettingsSearch]. */
const val PHOTO_LOOK_ROW = "그림 꾸미기"

/**
 * [RCX-30] 1.35 그림 꾸미기 (사용자 2026-10-30 "사용자들이 사진을 사용해서 버튼 모양이나 등등의 것들의 모습을 바꿀수 있게"): the user's own
 * photos for the calendar's background and top, each kind of day's mark and the widgets, and how round the app's corners are.
 * A photo is framed by moving and pinching it under the place's shape, then made small and written again on the phone (no
 * place or camera kept). Each place has its own 없애기; '처음으로' takes the whole look away. A 나눔 판 recipe sets the same.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoLookRows(encoded: String, onChange: (String) -> Unit) {
    val look = PhotoLook.decode(encoded)
    val context = LocalContext.current
    val c = context.appContainer
    val scope = rememberCoroutineScope()
    // Which place the next picked photo is for: a slot, and for 근무 그림 the kind of day.
    var target by remember { mutableStateOf<Pair<Slot, Long?>?>(null) }
    var framing by remember { mutableStateOf<Uri?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }
    // [J135-07] F-67: 없애기·처음으로 ask first (a framed photo cannot be got back without framing it again).
    var asking by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> framing = uri; problem = null }
    fun choose(slot: Slot, type: Long? = null) {
        target = slot to type
        runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
    }
    fun save(n: PhotoLook) {
        val line = n.encode()
        onChange(line)
        c.appScope.launch {
            // [J135-07] F-66: the widget is drawn again once the look is saved, not before (it read the old photo for minutes).
            kotlinx.coroutines.withTimeoutOrNull(3_000) { c.settingsRepository.settings.first { it.photoLook == line } }
            com.bamnat.app.widget.WidgetRefresher.refresh(context)
            LookImages.tidy(context, n.images)
        }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(PHOTO_LOOK_ROW, style = MaterialTheme.typography.bodyLarge)
        Text("내 사진으로 달력 배경, 달력 위 그림, 근무 표시, 위젯 바탕을 꾸며요. 사진은 휴대폰 안에서 작게 줄여 위치·기기 정보 없이 저장해요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        PlaceRow(Slot.BG, look.bg, "글자가 잘 보이게 사진 위를 흐리게 덮어요", onPick = { choose(Slot.BG) }, onClear = { asking = "달력 배경 사진을 없앨까요?" to { save(look.copy(bg = null)) } })
        if (look.bg != null) {
            var dim by remember(look.dim) { mutableFloatStateOf(look.dim.toFloat()) }
            Text("배경 흐리게 ${dim.toInt()}%", style = MaterialTheme.typography.bodySmall)
            Slider(dim, { dim = it }, valueRange = 0f..80f, steps = 7, onValueChangeFinished = { save(look.copy(dim = dim.toInt())) },
                modifier = Modifier.semantics { contentDescription = "배경 흐리게" })
        }
        PlaceRow(Slot.HEAD, look.head, "달력 맨 위에 가로로 길게 보여요", onPick = { choose(Slot.HEAD) }, onClear = { asking = "머리 그림을 없앨까요?" to { save(look.copy(head = null)) } })
        PlaceRow(Slot.WIDGET, look.widget, "홈 화면 위젯 뒤에 깔려요. 글자가 보이게 덮개를 씌워요", onPick = { choose(Slot.WIDGET) },
            onClear = { asking = "위젯 배경 사진을 없앨까요?" to { save(look.copy(widget = null)) } })
        // 근무 그림: the active calendar's kinds of day, each with its own round picture.
        val active by c.calendars.active.collectAsState()
        val snap by remember(active) { c.scopeOf(active).schedule.snapshot }.collectAsState(null)
        val types = snap?.shiftTypes.orEmpty()
        if (types.isNotEmpty()) {
            Text("근무 그림", style = MaterialTheme.typography.bodyMedium)
            Text("근무마다 동그란 사진이 달력 표시 앞에 붙어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { t ->
                    val has = look.icons[t.id] != null
                    OutlinedButton(onClick = { choose(Slot.ICON, t.id) }) {
                        ShiftBadge(t)
                        Text(if (has) " 바꾸기" else " 사진", style = MaterialTheme.typography.labelMedium)
                    }
                    if (has) TextButton(onClick = { asking = "${t.name} 그림을 없앨까요?" to { save(look.copy(icons = look.icons - t.id)) } }) { Text("${t.name} 그림 없애기") }
                }
            }
        }
        ChoiceRow("모서리 모양", "카드·창·칩의 모서리를 고르면 앱 전체가 바뀌어요",
            listOf("round" to "둥글게", "soft" to "보통 (처음)", "square" to "각지게"), look.corners ?: "soft") { k ->
            save(look.copy(corners = k.takeIf { it != "soft" }))
        }
        Row {
            if (!look.isDefault) TextButton(onClick = { asking = "사진·모서리를 모두 처음으로 돌릴까요?" to { save(PhotoLook()) } }) { Text("처음으로") }
            com.bamnat.app.ui.recipe.ShareThisSetting(com.bamnat.core.recipe.PieceKind.PHOTO_LOOK)
        }
    }
    asking?.let { (q, act) ->
        AlertDialog(
            onDismissRequest = { asking = null },
            text = { Text(q) },
            confirmButton = { TextButton(onClick = { asking = null; act() }) { Text(if ("처음으로" in q) "처음으로" else "없애기") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("그대로 두기") } },
        )
    }
    val uri = framing
    val t = target
    if (uri != null && t != null) FrameDialog(uri, t.first, onDismiss = { framing = null }) { crop ->
        framing = null
        scope.launch {
            val id = runCatching { LookImages.import(context, uri, t.first, crop) }.getOrNull()
            if (id == null) { problem = "사진을 읽지 못했어요. 다른 사진을 골라 주세요"; return@launch }
            val now = PhotoLook.decode(c.settingsRepository.current.photoLook)
            save(when (t.first) {
                Slot.BG -> now.copy(bg = id)
                Slot.HEAD -> now.copy(head = id)
                Slot.WIDGET -> now.copy(widget = id)
                Slot.ICON -> now.copy(icons = now.icons + (t.second!! to id))
            })
        }
    }
}

/** One place: its picture small (or an empty frame), what it is for, and 고르기·바꾸기 and 없애기. */
@Composable
private fun PlaceRow(slot: Slot, id: String?, help: String, onPick: () -> Unit, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        val shape = RoundedCornerShape(10.dp)
        val box = Modifier.width(if (slot == Slot.BG) 40.dp else 72.dp).aspectRatio(slot.w.toFloat() / slot.h).clip(shape)
        val picture = rememberLookPicture(id)
        if (picture != null) Image(picture.first, contentDescription = "${slot.label} 사진", contentScale = ContentScale.Crop, modifier = box)
        else Box(box.background(MaterialTheme.colorScheme.surfaceContainerHigh).border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape))
        Column(Modifier.weight(1f)) {
            Text(slot.label, style = MaterialTheme.typography.bodyMedium)
            Text(help, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row {
                TextButton(onClick = onPick) { Text(if (id == null) "사진 고르기" else "바꾸기") }
                if (id != null) TextButton(onClick = onClear) { Text("없애기") }
            }
        }
    }
}

/**
 * Framing a photo for [slot]: the place's shape stays still and the photo moves and grows under it (one finger moves, two
 * pinch). What is inside the frame is what is kept, as fractions of the photo.
 */
@Composable
private fun FrameDialog(uri: Uri, slot: Slot, onDismiss: () -> Unit, onDone: (RectF) -> Unit) {
    val context = LocalContext.current
    val photo by produceState<android.graphics.Bitmap?>(null, uri) { value = LookImages.preview(context, uri) }
    var frame by remember { mutableStateOf(IntSize.Zero) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val p = photo
    // The photo's scale when it just covers the frame; zoom on top of it.
    fun base(): Float = if (p == null || frame.width == 0) 1f else maxOf(frame.width / p.width.toFloat(), frame.height / p.height.toFloat())
    fun clamp(o: Offset, z: Float): Offset {
        if (p == null) return o
        val s = base() * z
        val maxX = ((p.width * s - frame.width) / 2f).coerceAtLeast(0f)
        val maxY = ((p.height * s - frame.height) / 2f).coerceAtLeast(0f)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${slot.label} 자리 맞추기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("한 손가락으로 옮기고, 두 손가락으로 크게 해요. 네모 안이 그대로 쓰여요", style = MaterialTheme.typography.bodySmall)
                val frameShape = if (slot == Slot.ICON) CircleShape else RoundedCornerShape(12.dp)
                Box(
                    Modifier.fillMaxWidth().heightIn(max = 420.dp).aspectRatio(slot.w.toFloat() / slot.h, matchHeightConstraintsFirst = slot.h > slot.w)
                        .clip(frameShape).clipToBounds().background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .border(2.dp, MaterialTheme.colorScheme.primary, frameShape)
                        .onSizeChanged { frame = it }
                        // Without the two-finger gesture (TalkBack, one hand): the same moves as actions.
                        .semantics {
                            val step = frame.width / 8f
                            fun move(dx: Float, dy: Float): Boolean { offset = clamp(offset + Offset(dx, dy), zoom); return true }
                            customActions = listOf(
                                CustomAccessibilityAction("크게") { zoom = (zoom * 1.25f).coerceIn(1f, 5f); offset = clamp(offset, zoom); true },
                                CustomAccessibilityAction("작게") { zoom = (zoom / 1.25f).coerceIn(1f, 5f); offset = clamp(offset, zoom); true },
                                CustomAccessibilityAction("사진을 왼쪽으로") { move(-step, 0f) },
                                CustomAccessibilityAction("사진을 오른쪽으로") { move(step, 0f) },
                                CustomAccessibilityAction("사진을 위로") { move(0f, -step) },
                                CustomAccessibilityAction("사진을 아래로") { move(0f, step) },
                            )
                        }
                        .pointerInput(p) {
                            detectTransformGestures { _, pan, gesture, _ ->
                                val z = (zoom * gesture).coerceIn(1f, 5f)
                                zoom = z
                                offset = clamp(offset + pan, z)
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    if (p == null) CircularProgressIndicator(Modifier.size(32.dp))
                    else Image(p.asImageBitmap(), contentDescription = "고른 사진", contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize().graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = offset.x; translationY = offset.y })
                }
            }
        },
        confirmButton = {
            TextButton(enabled = p != null && frame.width > 0, onClick = {
                val b = p ?: return@TextButton
                val s = base() * zoom
                val w = frame.width / s
                val h = frame.height / s
                val cx = b.width / 2f - offset.x / s
                val cy = b.height / 2f - offset.y / s
                onDone(RectF(((cx - w / 2) / b.width).coerceIn(0f, 1f), ((cy - h / 2) / b.height).coerceIn(0f, 1f),
                    ((cx + w / 2) / b.width).coerceIn(0f, 1f), ((cy + h / 2) / b.height).coerceIn(0f, 1f)))
            }) { Text("이대로 쓰기") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/**
 * [FREE-05] A photo for any place: returns what starts the picker. The photo picked is framed for [slot]'s shape, made small and kept
 * on the phone ([LookImages.import]); [onPicked] gets its id, [onProblem] what went wrong.
 */
@Composable
fun rememberPhotoPicker(slot: Slot, onPicked: (String) -> Unit, onProblem: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var framing by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> framing = uri }
    framing?.let { uri ->
        FrameDialog(uri, slot, onDismiss = { framing = null }) { crop ->
            framing = null
            scope.launch {
                val id = runCatching { LookImages.import(context, uri, slot, crop) }.getOrNull()
                if (id == null) onProblem("사진을 읽지 못했어요. 다른 사진을 골라 주세요") else onPicked(id)
            }
        }
    }
    return { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } }
}
