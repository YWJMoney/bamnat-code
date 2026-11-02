package com.bamnat.app.ui.settings

import androidx.core.content.edit
import com.bamnat.app.ui.theme.AppFonts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.launch
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.background
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.theme.UserFonts
import com.bamnat.app.ui.theme.fillWith
import com.bamnat.core.recipe.Fill
import com.bamnat.core.recipe.FontCatalog
import com.bamnat.core.recipe.Motion
import com.bamnat.core.recipe.PhotoAdjust
import com.bamnat.core.recipe.PhotoFrame

// ---- [MAT-01] 채우기 ----

private enum class FillKind(val label: String) { NONE("없음"), SOLID("단색"), GRADIENT("그라데이션"), PATTERN("무늬"), TEXTURE("질감"), GLASS("유리"), SHADER("움직이는 색") }

private fun kindOf(f: Fill?): FillKind = when (f) {
    null -> FillKind.NONE; is Fill.Solid -> FillKind.SOLID; is Fill.Gradient -> FillKind.GRADIENT; is Fill.Pattern -> FillKind.PATTERN
    is Fill.Texture -> FillKind.TEXTURE; is Fill.Glass -> FillKind.GLASS; is Fill.Shader -> FillKind.SHADER
}

/** A short name for a fill, for summaries and TalkBack. */
fun fillSummary(f: Fill?): String = when (f) {
    null -> "없음"
    is Fill.Solid -> "단색 " + colorName(f.color)
    is Fill.Linear -> "선형 그라데이션 ${f.stops.size}색"
    is Fill.Radial -> "방사형 그라데이션 ${f.stops.size}색"
    is Fill.Sweep -> "원뿔형 그라데이션 ${f.stops.size}색"
    is Fill.Pattern -> "무늬 " + (Fill.PATTERN_NAMES[f.kind] ?: f.kind)
    is Fill.Texture -> "질감 " + (Fill.TEXTURE_NAMES[f.kind] ?: f.kind)
    is Fill.Glass -> "유리"
    is Fill.Shader -> "움직이는 색 " + (Fill.SHADER_NAMES[f.kind] ?: f.kind)
}

/**
 * [MAT-01] 1.36 채우기 고르기 (2차 사용성 제안 "색 창 맨 위 '단색 · 그라데이션 · 무늬 · 질감 · 사진' 탭, 같은 창이 모든 부품에"): one editor
 * for every part — a live swatch on top, 바로 고르기, then the kind's own handles. [base] is the colour a new fill starts from.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FillEditor(label: String, value: Fill?, base: Long = 0xFFFFF4CC, onChange: (Fill?) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary.toArgb().toLong() and 0xFFFFFFFFL
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(56.dp, 40.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp))
                .fillWith(value).semantics { contentDescription = "$label 미리 보기: ${fillSummary(value)}" })
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(fillSummary(value), style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
            }
            if (value != null) TextButton(onClick = { onChange(null) }) { Text("없애기") }
        }
        // [MAT-01] PC 확인 1차 (사용성 제안 "바로 고르기를 위로"): ready-made fills first, whichever kind is chosen.
        Text("바로 고르기", style = MaterialTheme.typography.labelMedium, color = scheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Fill.PRESETS.forEach { (name, f) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClickLabel = "$name 채우기") { onChange(f) }
                    .semantics { selected = value == f }) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                        .border(if (value == f) 3.dp else 1.dp, if (value == f) scheme.primary else scheme.outlineVariant, RoundedCornerShape(10.dp)).fillWith(f))
                    Text(name, fontSize = 11.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FillKind.entries.forEach { k ->
                FilterChip(selected = kindOf(value) == k, onClick = { onChange(start(k, value, base, accent)) }, label = { Text(k.label) })
            }
        }
        if (value == null) return@Column
        when (value) {
            is Fill.Solid -> {
                ColorPick("색", value.color or 0xFF000000L, allowNone = false) { c -> c?.let { onChange(Fill.Solid(withAlpha(it, value.color))) } }
                AlphaSlider(value.color) { a -> onChange(Fill.Solid(setAlpha(value.color, a))) }
            }
            is Fill.Gradient -> GradientHandles(value, onChange)
            is Fill.Pattern -> {
                Text("무늬", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fill.PATTERNS.forEach { k ->
                        val f = value.copy(kind = k)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClickLabel = "무늬 ${Fill.PATTERN_NAMES[k]}") { onChange(f) }
                            .semantics { selected = k == value.kind }) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                .border(if (k == value.kind) 3.dp else 1.dp, if (k == value.kind) scheme.primary else scheme.outlineVariant, RoundedCornerShape(8.dp)).fillWith(f))
                            Text(Fill.PATTERN_NAMES[k] ?: k, fontSize = 10.sp)
                        }
                    }
                }
                ColorPick("무늬 색", value.ink or 0xFF000000L, allowNone = false) { c -> c?.let { onChange(value.copy(ink = withAlpha(it, value.ink))) } }
                AlphaSlider(value.ink, "무늬 진하기") { a -> onChange(value.copy(ink = setAlpha(value.ink, a))) }
                ColorPick("바탕 색", value.ground or 0xFF000000L, allowNone = false) { c -> c?.let { onChange(value.copy(ground = withAlpha(it, value.ground))) } }
                MatSlider("크기", value.size.toFloat(), 50f..300f, "%") { onChange(value.copy(size = it.toInt())) }
                MatSlider("기울기", value.angle.toFloat(), 0f..359f, "°") { onChange(value.copy(angle = it.toInt())) }
            }
            is Fill.Texture -> {
                Text("질감", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fill.TEXTURES.forEach { k ->
                        val f = value.copy(kind = k)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClickLabel = "질감 ${Fill.TEXTURE_NAMES[k]}") { onChange(f) }
                            .semantics { selected = k == value.kind }) {
                            Box(Modifier.size(38.dp).clip(RoundedCornerShape(8.dp))
                                .border(if (k == value.kind) 3.dp else 1.dp, if (k == value.kind) scheme.primary else scheme.outlineVariant, RoundedCornerShape(8.dp)).fillWith(f))
                            Text(Fill.TEXTURE_NAMES[k] ?: k, fontSize = 10.sp)
                        }
                    }
                }
                ColorPick("바탕 색", value.tint or 0xFF000000L, allowNone = false) { c -> c?.let { onChange(value.copy(tint = it or 0xFF000000L)) } }
                MatSlider("질감 세기", value.strength.toFloat(), 0f..100f, "%") { onChange(value.copy(strength = it.toInt())) }
            }
            is Fill.Glass -> {
                ColorPick("유리 색", value.tint or 0xFF000000L, allowNone = false) { c -> c?.let { onChange(value.copy(tint = withAlpha(it, value.tint))) } }
                AlphaSlider(value.tint, "비치는 정도", invert = true) { a -> onChange(value.copy(tint = setAlpha(value.tint, a))) }
                MatSlider("서리 느낌", value.blur.toFloat(), 0f..40f, "") { onChange(value.copy(blur = it.toInt())) }
                Text("유리는 뒤가 비치는 반투명 판이에요. 사진 배경 위 칸·카드에 쓰면 잘 어울려요", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
            is Fill.Shader -> {
                Text("움직임", style = MaterialTheme.typography.labelMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Fill.SHADERS.forEach { k ->
                        val f = value.copy(kind = k)
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClickLabel = "${Fill.SHADER_NAMES[k]}") { onChange(f) }
                            .semantics { selected = k == value.kind }) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp))
                                .border(if (k == value.kind) 3.dp else 1.dp, if (k == value.kind) scheme.primary else scheme.outlineVariant, RoundedCornerShape(8.dp)).fillWith(f))
                            Text(Fill.SHADER_NAMES[k] ?: k, fontSize = 10.sp)
                        }
                    }
                }
                value.colors.forEachIndexed { i, c ->
                    ColorPick("색 ${i + 1}", c or 0xFF000000L, allowNone = false) { n -> n?.let { onChange(value.copy(colors = value.colors.toMutableList().also { l -> l[i] = it or 0xFF000000L })) } }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (value.colors.size < 3) OutlinedButton(onClick = { onChange(value.copy(colors = value.colors + value.colors.last())) }) { Text("+ 색 더하기") }
                    if (value.colors.size > 2) OutlinedButton(onClick = { onChange(value.copy(colors = value.colors.dropLast(1))) }) { Text("마지막 색 빼기") }
                }
                MatSlider("빠르기", value.speed.toFloat(), 0f..10f, "", steps = 9) { onChange(value.copy(speed = it.toInt())) }
                Text("안드로이드 13부터 움직여요. 그 아래 휴대폰과 ‘움직임 끄기’에서는 같은 색의 멈춘 그라데이션으로 보여요",
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun GradientHandles(g: Fill.Gradient, onChange: (Fill?) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = g is Fill.Linear, onClick = { onChange(Fill.Linear(135, g.stops)) }, label = { Text("선형") })
        FilterChip(selected = g is Fill.Radial, onClick = { onChange(Fill.Radial(50, 40, 90, g.stops)) }, label = { Text("방사형") })
        FilterChip(selected = g is Fill.Sweep, onClick = { onChange(Fill.Sweep(50, 50, 0, g.stops)) }, label = { Text("원뿔형") })
        FilterChip(selected = false, onClick = { onChange(withStops(g, g.stops.reversed().map { it.copy(at = it.at?.let { a -> 100 - a }) })) }, label = { Text("색 뒤집기") })
    }
    when (g) {
        is Fill.Linear -> {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(0 to "↓", 45 to "↘", 90 to "→", 135 to "↗", 180 to "↑", 270 to "←").forEach { (a, arrow) ->
                    FilterChip(selected = g.angle == a, onClick = { onChange(g.copy(angle = a)) }, label = { Text(arrow) },
                        modifier = Modifier.semantics { contentDescription = "방향 ${a}도" })
                }
            }
            MatSlider("방향", g.angle.toFloat(), 0f..359f, "°") { onChange(g.copy(angle = it.toInt())) }
        }
        is Fill.Radial -> {
            MatSlider("가운데 가로", g.cx.toFloat(), 0f..100f, "%") { onChange(g.copy(cx = it.toInt())) }
            MatSlider("가운데 세로", g.cy.toFloat(), 0f..100f, "%") { onChange(g.copy(cy = it.toInt())) }
            MatSlider("퍼지는 크기", g.radius.toFloat(), 10f..150f, "%") { onChange(g.copy(radius = it.toInt())) }
        }
        is Fill.Sweep -> {
            MatSlider("가운데 가로", g.cx.toFloat(), 0f..100f, "%") { onChange(g.copy(cx = it.toInt())) }
            MatSlider("가운데 세로", g.cy.toFloat(), 0f..100f, "%") { onChange(g.copy(cy = it.toInt())) }
            MatSlider("시작 각도", g.turn.toFloat(), 0f..359f, "°") { onChange(g.copy(turn = it.toInt())) }
        }
    }
    g.stops.forEachIndexed { i, s ->
        ColorPick("색 ${i + 1}", s.color or 0xFF000000L, allowNone = false) { c ->
            c?.let { onChange(withStops(g, g.stops.toMutableList().also { l -> l[i] = s.copy(color = withAlpha(it, s.color)) })) }
        }
        val place = g.places()[i]
        if (i in 1 until g.stops.lastIndex) MatSlider("색 ${i + 1} 자리", place * 100, 0f..100f, "%") { v ->
            onChange(withStops(g, g.stops.toMutableList().also { l -> l[i] = s.copy(at = v.toInt()) }))
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (g.stops.size < Fill.MAX_STOPS) OutlinedButton(onClick = { onChange(withStops(g, g.stops + Fill.Stop(g.stops.last().color))) }) { Text("+ 색 더하기") }
        if (g.stops.size > 2) OutlinedButton(onClick = { onChange(withStops(g, g.stops.dropLast(1))) }) { Text("마지막 색 빼기") }
    }
}

private fun withStops(g: Fill.Gradient, stops: List<Fill.Stop>): Fill = when (g) {
    is Fill.Linear -> g.copy(stops = stops); is Fill.Radial -> g.copy(stops = stops); is Fill.Sweep -> g.copy(stops = stops)
}

/** A fill of kind [k] that keeps what it can of [from] (its colours) and starts from [base]. */
private fun start(k: FillKind, from: Fill?, base: Long, accent: Long): Fill? {
    val a = from?.average?.let { it or 0xFF000000L } ?: (base or 0xFF000000L)
    // PC 확인 1차: a first gradient from a near-white base (흰색→회색) was hard to see; it starts in the app's accent instead.
    val pale = FloatArray(3).also { android.graphics.Color.colorToHSV(a.toInt(), it) }[1] < 0.15f
    val colors = when (from) {
        is Fill.Gradient -> from.stops.map { it.color }
        is Fill.Shader -> from.colors
        else -> if (pale) listOf(Fill.mix(listOf(accent, 0xFFFFFFFFL, 0xFFFFFFFFL)), Fill.mix(listOf(accent, 0xFFFFFFFFL)), accent) else listOf(a, shade(a))
    }
    return when (k) {
        FillKind.NONE -> null
        FillKind.SOLID -> from as? Fill.Solid ?: Fill.Solid(a)
        FillKind.GRADIENT -> from as? Fill.Gradient ?: Fill.Linear(135, colors.take(Fill.MAX_STOPS).map { Fill.Stop(it) })
        FillKind.PATTERN -> from as? Fill.Pattern ?: Fill.Pattern("dots", 0x33000000L, a)
        FillKind.TEXTURE -> from as? Fill.Texture ?: Fill.Texture("paper", a, 45)
        FillKind.GLASS -> from as? Fill.Glass ?: Fill.Glass(0x66FFFFFFL, 16)
        FillKind.SHADER -> from as? Fill.Shader ?: Fill.Shader("aurora", (colors + shade(colors.last())).take(3), 3)
    }
}

private fun shade(c: Long): Long {
    val hsv = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toInt(), it) }
    hsv[0] = (hsv[0] + 40f) % 360f
    hsv[2] = (hsv[2] * 0.8f).coerceIn(0.2f, 1f)
    return android.graphics.Color.HSVToColor(hsv).toLong() and 0xFFFFFFFFL
}

/** [c]'s colour with [old]'s alpha (the colour picker gives opaque colours). */
private fun withAlpha(c: Long, old: Long): Long = (c and 0x00FFFFFFL) or (old and 0xFF000000L)
private fun setAlpha(c: Long, percent: Int): Long = (c and 0x00FFFFFFL) or ((percent * 255 / 100).toLong().coerceIn(0, 255) shl 24)

@Composable
private fun AlphaSlider(c: Long, label: String = "진하기", invert: Boolean = false, onDone: (Int) -> Unit) {
    val now = (((c shr 24) and 0xFF) * 100 / 255).toInt()
    MatSlider(label, (if (invert) 100 - now else now).toFloat(), 0f..100f, "%") { v -> onDone(if (invert) 100 - v.toInt() else v.toInt()) }
}

@Composable
internal fun MatSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, steps: Int = 0, shown: (Float) -> String = { "${it.toInt()}$unit" }, onDone: (Float) -> Unit) {
    var v by remember(value) { mutableFloatStateOf(value) }
    Column {
        Text("$label ${shown(v)}", style = MaterialTheme.typography.bodyMedium)
        Slider(v, { v = it }, valueRange = range, steps = steps, onValueChangeFinished = { onDone(v) }, modifier = Modifier.semantics { contentDescription = label })
    }
}

// ---- [MAT-03][MAT-04] 사진 조정·틀 ----

/** [MAT-03] 1.36 사진 조정: 바로 고르기, then 밝기·대비·채도·흑백·색 입히기·흐림·블렌드. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoAdjustEditor(value: PhotoAdjust, onChange: (PhotoAdjust) -> Unit) {
    Text("사진 조정", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PhotoAdjust.PRESETS.forEach { (name, p) -> FilterChip(selected = p == value, onClick = { onChange(p) }, label = { Text(name) }) }
    }
    MatSlider("밝기", value.bright.toFloat(), -100f..100f, "") { onChange(value.copy(bright = it.toInt())) }
    MatSlider("대비", value.contrast.toFloat(), -100f..100f, "") { onChange(value.copy(contrast = it.toInt())) }
    MatSlider("채도", value.saturate.toFloat(), -100f..100f, "") { onChange(value.copy(saturate = it.toInt())) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("흑백", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(value.gray, { onChange(value.copy(gray = it)) }, modifier = Modifier.semantics { contentDescription = "흑백" })
    }
    ColorPick("색 입히기", value.tint) { onChange(value.copy(tint = it, tintAmount = if (it == null) 0 else value.tintAmount.takeIf { a -> a > 0 } ?: 30)) }
    if (value.tint != null) MatSlider("입히는 정도", value.tintAmount.toFloat(), 0f..100f, "%") { onChange(value.copy(tintAmount = it.toInt())) }
    MatSlider("흐림", value.blur.toFloat(), 0f..25f, "", shown = { if (it < 1) "없음" else "${it.toInt()}" }) { onChange(value.copy(blur = it.toInt())) }
    Text("흐림은 안드로이드 12부터 보여요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("아래와 섞기", style = MaterialTheme.typography.bodyMedium)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        PhotoAdjust.BLENDS.forEach { b -> FilterChip(selected = value.blend == b, onClick = { onChange(value.copy(blend = b)) }, label = { Text(PhotoAdjust.BLEND_NAMES[b] ?: b) }) }
    }
}

/** [MAT-04] 1.36 사진 틀 모양: 11 shapes and 글자 모양 (a word of up to four letters). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoFrameEditor(label: String, value: String?, onChange: (String?) -> Unit) {
    var letters by remember { mutableStateOf(false) }
    Text(label, style = MaterialTheme.typography.bodyMedium)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        FilterChip(selected = value == null, onClick = { onChange(null) }, label = { Text("처음대로") })
        PhotoFrame.SHAPES.forEach { k -> FilterChip(selected = value == k, onClick = { onChange(k) }, label = { Text(PhotoFrame.NAMES[k] ?: k) }) }
        FilterChip(selected = PhotoFrame.letters(value) != null, onClick = { letters = true },
            label = { Text(PhotoFrame.letters(value)?.let { "글자 ‘$it’" } ?: "글자 모양") })
    }
    if (letters) {
        var word by remember { mutableStateOf(PhotoFrame.letters(value).orEmpty()) }
        AlertDialog(
            onDismissRequest = { letters = false },
            title = { Text("글자 모양 틀") },
            text = {
                Column {
                    Text("사진이 이 글자 모양으로 잘려요. 네 글자까지예요", style = MaterialTheme.typography.bodySmall)
                    androidx.compose.material3.OutlinedTextField(word, { word = it.take(8) }, singleLine = true, label = { Text("글자") })
                }
            },
            confirmButton = { TextButton(onClick = { letters = false; PhotoFrame.ofLetters(word)?.let(onChange) }, enabled = word.isNotBlank()) { Text("쓰기") } },
            dismissButton = { TextButton(onClick = { letters = false }) { Text("닫기") } },
        )
    }
}

// ---- [MAT-05][MAT-06] 글꼴 ----

/** The [FontRow] to bring into view when it appears (설정 검색 › 화면마다 글꼴·날짜 숫자 글꼴), by its label; once. */
object FontRowFocus {
    @Volatile var label: String? = null
    /**
     * PC 확인 11차: the row a search asked for that its editor shows first (칸 디자인's 날짜 글꼴), as state so the editor already on
     * screen sees it; till closed or the editor leaves.
     */
    var pinned by mutableStateOf<String?>(null)
}

/** A line that shows the font now (in itself) and opens [FontPickerDialog]. */
@Composable
fun FontRow(label: String, value: String?, numbersOnly: Boolean = false, focusNow: Boolean = false, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val ctx = LocalContext.current
    // PC 확인 2차: the font before the last pick, for 전 글꼴로 when the new one could not come down.
    var before by remember { mutableStateOf<String?>(null) }
    var picked by remember { mutableStateOf(false) }
    val load = value?.let { AppFonts.loads[it] }
    // PC 확인 6차 (StrictMode: 화면 스레드에서 파일 읽기): a user font's name is read off the main thread.
    val picked0 = value
    // PC 확인 9차: a shared font a recipe named that has not come yet is 받을 글꼴 (기다리는 중), not 내 글꼴.
    val userName by androidx.compose.runtime.produceState<String?>(null, picked0, load) {
        this.value = if (picked0 == null || FontCatalog.byId(picked0) != null) null
            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { UserFonts.list(ctx).firstOrNull { it.id == picked0 }?.name ?: WAITING_NAME }
    }
    val waiting = value != null && FontCatalog.isUser(value) && userName == WAITING_NAME
    val base = value?.let { id -> FontCatalog.byId(id)?.name ?: userName?.takeIf { it != WAITING_NAME } ?: if (waiting) "받을 글꼴" else "내 글꼴" } ?: "처음대로"
    val name = when { load == AppFonts.Load.LOADING -> "$base (받는 중)"; load == AppFonts.Load.FAILED -> "$base (못 받음)"; waiting -> "$base (기다리는 중)"; else -> base }
    // PC 확인 6차 (사용성 제안 "설정 검색에서 오면 글꼴 칸으로 바로"): the studio asks for this row by its label.
    val bring = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    // PC 확인 7차 (사용성 제안 "검색에서 온 칸을 화면 가운데로, 잠깐 반짝"): the row is asked into view with half a screen round it, so it
    // lands in the middle, and its background flashes twice.
    var rowSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val flash = remember { androidx.compose.animation.core.Animatable(0f) }
    val screenPx = with(androidx.compose.ui.platform.LocalDensity.current) { androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp.dp.toPx() }
    val roomMax = with(androidx.compose.ui.platform.LocalDensity.current) { 200.dp.toPx() }
    androidx.compose.runtime.LaunchedEffect(label) {
        if (focusNow || FontRowFocus.label == label) {
            if (FontRowFocus.label == label) FontRowFocus.label = null
            // PC 8차 (검색에서 오면 칸 디자인 맨 위에서 열림): a rect taller than the editor's own window (half a screen each side was) counts
            // as already in view, so nothing scrolled. First the row itself, then a little room round it — at most what fits.
            kotlinx.coroutines.delay(350)
            bring.bringIntoView()
            val h = rowSize.height.toFloat()
            val room = ((screenPx / 4f) - h / 2f).coerceIn(0f, roomMax)
            bring.bringIntoView(androidx.compose.ui.geometry.Rect(0f, -room, rowSize.width.toFloat(), h + room))
            repeat(2) {
                flash.animateTo(1f, androidx.compose.animation.core.tween(220))
                flash.animateTo(0f, androidx.compose.animation.core.tween(380))
            }
        }
    }
    val flashColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f * flash.value)
    Row(Modifier.fillMaxWidth().onSizeChanged { rowSize = it }.bringIntoViewRequester(bring).background(flashColor, MaterialTheme.shapes.small)
        .clickable(onClickLabel = "$label 고르기") { open = true }.heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(FontCatalog.SAMPLE, fontFamily = com.bamnat.app.ui.theme.rememberFontFamily(value), style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(name, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (value != null) TextButton(onClick = { onPick(null) }) { Text("처음대로") }
    }
    val standInName by androidx.compose.runtime.produceState<String?>(null, picked0, waiting) {
        this.value = if (!waiting || picked0 == null) null
            else kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { FontCatalog.byId(UserFonts.fallbackOf(ctx, picked0) ?: "sans")?.name }
    }
    if (waiting) Text("보낸 분이 나눈 글꼴이에요. 운영자가 확인하면(보통 하루 안) 앱을 열 때 저절로 들어와요. 그 전에는 비슷한 앱 글꼴(${standInName ?: "기본"})로 보여요",
        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else if (value != null) FontLoadLine(load, AppFonts.reasons[value], AppFonts.progress[value],
        retry = { AppFonts.start(ctx, value) },
        standIn = { onPick(AppFonts.standInId(value)) },
        // PC 확인 3차: 전 글꼴로 always, 처음대로 when there was none before.
        back = { onPick(if (picked) before else null) }, backName = if (picked && before != null) FontCatalog.byId(before!!)?.name else null)
    if (open) FontPickerDialog(value, numbersOnly, onDismiss = { open = false }) { open = false; FontRecent.add(ctx, it); before = value; picked = true; onPick(it) }
}

/**
 * 받는 중… · 받았어요 · 못 받았어요 (이유) · 다시 · 비슷한 앱 글꼴로 쓰기 · 전 글꼴로, for an open font; nothing for the phone's own or the user's.
 * PC 확인 2차 (사용성 제안 "실패하면 이유를 한 줄로, '비슷한 앱 글꼴로 쓰기' 단추").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FontLoadLine(load: AppFonts.Load?, reason: com.bamnat.app.ui.theme.OpenFonts.Reason?, progress: Pair<Long, Long>?, retry: () -> Unit,
                         standIn: () -> Unit, back: () -> Unit, backName: String?) {
    val scheme = MaterialTheme.colorScheme
    val ctx = LocalContext.current
    when (load) {
        AppFonts.Load.LOADING -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            // PC 확인 3차 (사용성 제안 "진행 막대와 '받는 중 · 14MB'").
            val (read, total) = progress ?: (0L to 0L)
            if (total > 0) androidx.compose.material3.LinearProgressIndicator(progress = { (read.toFloat() / total).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            else androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
            val mb = { b: Long -> "%.1f".format(b / 1048576f) }
            Text(if (total > 0) "글꼴 받는 중 · ${mb(read)} / ${mb(total)}MB. 받기 전에는 비슷한 앱 글꼴로 보여요" else "글꼴 받는 중… 받기 전에는 비슷한 앱 글꼴로 보여요",
                style = MaterialTheme.typography.labelSmall, color = scheme.onSurfaceVariant)
        }
        AppFonts.Load.READY -> Text("받았어요", style = MaterialTheme.typography.labelSmall, color = scheme.primary)
        AppFonts.Load.FAILED -> Column {
            Text("못 받았어요: ${reason?.text ?: "글꼴을 받지 못했어요"}. 지금은 비슷한 앱 글꼴로 보여요", style = MaterialTheme.typography.labelSmall, color = scheme.error)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (reason?.time == true) TextButton(onClick = {
                    runCatching { ctx.startActivity(android.content.Intent(android.provider.Settings.ACTION_DATE_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }) { Text("날짜·시간 설정 열기") }
                TextButton(onClick = retry) { Text("다시") }
                TextButton(onClick = standIn) { Text("비슷한 앱 글꼴로") }
                TextButton(onClick = back) { Text(if (backName != null) "전 글꼴로 ($backName)" else "전 글꼴로") }
            }
        }
        AppFonts.Load.WAITING, null -> {}
    }
}

/** [FontRow]'s mark for a user font id this phone has not got (a shared font still waiting). */
private const val WAITING_NAME = "\u0000waiting"

/** [MAT-05] PC 확인 1차 (사용성 제안 "최근 쓴 글꼴 줄"): the last five fonts picked, newest first, on this phone only. */
internal object FontRecent {
    private const val FILE = "font_recent"
    fun list(c: android.content.Context): List<String> = c.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE)
        .getString("ids", "").orEmpty().split(',').filter { it.isNotEmpty() && (FontCatalog.byId(it) != null || UserFonts.file(c, it) != null) }
    fun add(c: android.content.Context, id: String) {
        val next = (listOf(id) + list(c).filter { it != id }).take(5)
        c.getSharedPreferences(FILE, android.content.Context.MODE_PRIVATE).edit { putString("ids", next.joinToString(",")) }
    }
}

/**
 * [MAT-05] 1.36 글꼴 고르기 (2차 사용성 제안 "글꼴 목록을 그 글꼴로 쓴 '3월 14일 야간' 견본으로"): every font in its own face, by group,
 * 내 글꼴 first with 넣기·빼기·나눌 수 있음. An open font comes down the first time its sample is drawn.
 */
@Composable
fun FontPickerDialog(value: String?, numbersOnly: Boolean = false, onDismiss: () -> Unit, onPick: (String) -> Unit) {
    val ctx = LocalContext.current
    var group by remember { mutableStateOf<FontCatalog.Group?>(null) }
    var mine by remember { mutableStateOf(UserFonts.list(ctx)) }
    var problem by remember { mutableStateOf<String?>(null) }
    // PC 확인 7차: a font without 한글 is said so when it goes in (not in red: it works), and asked about once before it draws whole screens.
    var note by remember { mutableStateOf<String?>(null) }
    var askHangul by remember { mutableStateOf<UserFonts.Mine?>(null) }
    val choose: (String) -> Unit = { id ->
        val m = mine.firstOrNull { it.id == id }
        if (m != null && !m.hangul && !m.hangulOk && !numbersOnly) askHangul = m else onPick(id)
    }
    var stamp by remember { mutableIntStateOf(0) }
    var reportOf by remember { mutableStateOf<UserFonts.Mine?>(null) }
    var q by remember { mutableStateOf("") }
    val recent = remember { FontRecent.list(ctx) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    fun hit(name: String) = q.isBlank() || name.contains(q.trim(), ignoreCase = true)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        when (val r = UserFonts.add(ctx, uri, name)) {
            is UserFonts.Result.Added -> {
                mine = UserFonts.list(ctx); stamp++
                problem = if (r.font.sold) "‘${r.font.name}’ 글꼴은 안에 유료 글꼴 회사가 적혀 있어 이 휴대폰에서만 쓸 수 있어요(레시피와 함께 나눌 수 없어요)" else null
                note = if (!r.font.hangul) (FontCatalog.byId(r.font.fallback)?.name ?: "기본 글꼴").let { like ->
                    "‘${r.font.name}’ 글꼴에는 한글이 없어요. 숫자·영문만 이 글꼴로, 한글은 $like${com.bamnat.app.ui.Fmt.ro(like)} 보여요" } else null
            }
            is UserFonts.Result.Problem -> { problem = r.text; note = null }
            // PC 확인 9차 (같은 글꼴을 넣으면 아무 말 없음).
            is UserFonts.Result.Same -> { problem = null; note = "이미 넣은 글꼴과 같은 파일이에요: ‘${r.font.name}’" }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("글꼴 고르기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = group == null, onClick = { group = null }, label = { Text("모두 ${FontCatalog.ALL.size + mine.size}") })
                    FontCatalog.Group.entries.forEach { g -> FilterChip(selected = group == g, onClick = { group = g }, label = { Text(g.label) }) }
                }
                // [MAT-05] PC 확인 1차 (사용성 제안 "검색칸과 최근 쓴 글꼴 줄").
                androidx.compose.material3.OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), singleLine = true,
                    placeholder = { Text("글꼴 이름 찾기 (예: 명조, 손글씨)") },
                    trailingIcon = { if (q.isNotEmpty()) TextButton(onClick = { q = "" }) { Text("지우기") } })
                problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
                note?.let { Text(it, color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall) }
                LazyColumn(Modifier.heightIn(max = 420.dp)) {
                    if (group == null && q.isBlank() && recent.isNotEmpty()) {
                        item(key = "recent") { Text("최근 쓴 글꼴", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
                        items(recent, key = { "r-$it" }) { id ->
                            val n = FontCatalog.byId(id)?.name ?: mine.firstOrNull { it.id == id }?.name ?: "내 글꼴"
                            FontLine(id, n, "최근", id == value, choose)
                        }
                    }
                    if (group == null) {
                        item {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("내 글꼴 ${mine.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                                TextButton(onClick = { pick.launch(arrayOf("*/*")) }) {
                                    Text("+ 내 글꼴 넣기")
                                }
                            }
                            Text("TTF·OTF 파일을 이 휴대폰에 넣어요(20MB까지). 만든 곳이 누구나 쓰게 허락한 공개 글꼴(OFL 등)로 밝히면 레시피와 함께 가요(8MB까지). 아니면 받는 분께 비슷한 앱 글꼴로 보여요",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(mine.filter { hit(it.name) }, key = { it.id + stamp }) { f ->
                            val like = FontCatalog.byId(f.fallback)?.name ?: "기본"
                            FontLine(f.id, f.name, if (f.hangul) "비슷한 앱 글꼴: $like" else "한글 없음 · 한글은 $like", f.id == value, choose)
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                if (f.received) {
                                    // [MAT-06] A font that came with a recipe: its sender said it is open; 신고 if it is not.
                                    Text("레시피와 함께 받은 글꼴", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { reportOf = f }) { Text("신고") }
                                } else if (f.sold) {
                                    // PC 확인 6차: told when it goes in, not only after 올리기.
                                    Text("글꼴 안에 유료 글꼴 회사가 적혀 있어 나눌 수 없어요. 이 휴대폰에서는 쓸 수 있어요", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                                } else {
                                    Text("누구나 쓰게 허락한 공개 글꼴(OFL 등)", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Switch(f.open, { UserFonts.setOpen(ctx, f.id, it); mine = UserFonts.list(ctx) },
                                        modifier = Modifier.semantics { contentDescription = "${f.name} 공개 글꼴로 표시" })
                                }
                                TextButton(onClick = { UserFonts.remove(ctx, f.id); mine = UserFonts.list(ctx) }) { Text("빼기") }
                            }
                        }
                    }
                    FontCatalog.grouped().filter { group == null || it.first == group }.map { (g, fonts) -> g to fonts.filter { hit(it.name) || hit(g.label) } }
                        .filter { it.second.isNotEmpty() }.forEach { (g, fonts) ->
                        item(key = "g-${g.name}") { Text(g.label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp)) }
                        items(fonts, key = { it.id }) { f ->
                            FontLine(f.id, f.name, if (f.system) "휴대폰 기본" else if (!f.korean) "숫자·영문만 이 글꼴" else "공개 글꼴 · 고르면 받아요", f.id == value, choose)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
    askHangul?.let { f ->
        val like = FontCatalog.byId(f.fallback)?.name ?: "기본"
        AlertDialog(
            onDismissRequest = { askHangul = null },
            title = { Text("한글이 없는 글꼴이에요") },
            text = { Text("‘${f.name}’ 글꼴로는 숫자·영문만 보이고, 한글은 $like 글꼴로 보여요. 그래도 쓸까요? 다음부터는 묻지 않아요") },
            confirmButton = { TextButton(onClick = { UserFonts.markHangulOk(ctx, f.id); mine = UserFonts.list(ctx); askHangul = null; onPick(f.id) }) { Text("그래도 쓰기") } },
            dismissButton = { TextButton(onClick = { askHangul = null }) { Text("다른 글꼴 고르기") } },
        )
    }
    // [MAT-06] 받은 글꼴 신고: three phones take it down for everyone until the operator looks; it leaves this phone at once.
    reportOf?.let { f ->
        val whys = listOf("license" to "허락 없이 나눈 글꼴 같아요", "broken" to "글자가 깨져요", "bad" to "불쾌한 모양이에요")
        AlertDialog(
            onDismissRequest = { reportOf = null },
            title = { Text("‘${f.name}’ 신고") },
            text = {
                Column {
                    Text("고르면 이 휴대폰에서 바로 빠지고, 여러 분이 신고하면 모두에게서 내려가요.", style = MaterialTheme.typography.bodySmall)
                    whys.forEach { (k, label) ->
                        TextButton(onClick = {
                            val c = ctx.applicationContext.let { (it as? com.bamnat.app.BamnatApp)?.container }
                            if (c != null) scope.launch { runCatching { c.recipeApi.fontReport(c.companyStore.device, f.id, k) } }
                            UserFonts.remove(ctx, f.id); mine = UserFonts.list(ctx); reportOf = null
                        }, modifier = Modifier.fillMaxWidth()) { Text(label) }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { reportOf = null }) { Text("닫기") } },
        )
    }
}

@Composable
private fun FontLine(id: String, name: String, note: String, on: Boolean, onPick: (String) -> Unit) {
    // The list never starts 70 downloads: a font comes down when it is picked (its picture stands in until then).
    val family = com.bamnat.app.ui.theme.rememberFontFamily(id, fetch = false)
    val sample = if (FontCatalog.byId(id)?.korean == false) FontCatalog.SAMPLE_LATIN else FontCatalog.SAMPLE
    val ctx = LocalContext.current
    // [MAT-05] PC 확인 1차: until an open font has come down, its line shows the sample drawn in it ahead of time.
    val picture = remember(id) { AppFonts.sampleImage(ctx, id) }.takeIf { it != 0 && AppFonts.loads[id] != AppFonts.Load.READY }
    Column(Modifier.fillMaxWidth().clickable(onClickLabel = "$name 쓰기") { onPick(id) }
        .border(if (on) 2.dp else 0.dp, if (on) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent, RoundedCornerShape(8.dp))
        .padding(8.dp).semantics { selected = on }) {
        if (picture != null) androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(picture), contentDescription = "$name 견본: $sample",
            modifier = Modifier.heightIn(min = 26.dp, max = 26.dp), contentScale = androidx.compose.ui.layout.ContentScale.FillHeight, alignment = Alignment.CenterStart,
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.onSurface))
        else Text(sample, fontFamily = family, fontSize = 20.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("$name · $note", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---- [MAT-07] 움직임 ----

/** [MAT-07] 1.36 움직임: 달 넘김, 오늘 강조, 입히기 전환, 빠르기, 끄기 (and what the phone's own setting does). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MotionEditor(value: Motion, onChange: (Motion) -> Unit) {
    val ctx = LocalContext.current
    val system = remember { com.bamnat.app.ui.theme.systemAnimationsOff(ctx) }
    if (system) Text("휴대폰 설정에서 ‘애니메이션 삭제’가 켜져 있어서 앱도 움직이지 않아요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("움직임 끄기", style = MaterialTheme.typography.bodyMedium)
            Text("켜면 화면이 움직이지 않아요. 멀미가 나거나 배터리를 아끼고 싶을 때", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(value.off, { onChange(value.copy(off = it)) }, modifier = Modifier.semantics { contentDescription = "움직임 끄기" })
    }
    if (value.off) return
    Text("달 넘길 때", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Motion.TURNS.forEach { k -> FilterChip(selected = (value.turn ?: "slide") == k, onClick = { onChange(value.copy(turn = k.takeIf { it != "slide" })) }, label = { Text(Motion.TURN_NAMES[k] ?: k) }) }
    }
    Text("오늘 칸", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Motion.TODAYS.forEach { k -> FilterChip(selected = (value.today ?: "none") == k, onClick = { onChange(value.copy(today = k.takeIf { it != "none" })) }, label = { Text(Motion.TODAY_NAMES[k] ?: k) }) }
    }
    Text("테마를 입힐 때", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Motion.WEARS.forEach { k -> FilterChip(selected = (value.wear ?: "fade") == k, onClick = { onChange(value.copy(wear = k.takeIf { it != "fade" })) }, label = { Text(Motion.WEAR_NAMES[k] ?: k) }) }
    }
    Text("빠르기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Motion.SPEEDS.forEach { k -> FilterChip(selected = (value.speed ?: "n") == k, onClick = { onChange(value.copy(speed = k.takeIf { it != "n" })) }, label = { Text(Motion.SPEED_NAMES[k] ?: k) }) }
    }
}
