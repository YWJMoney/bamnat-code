package com.bamnat.app.ui.studio

import android.graphics.ColorMatrixColorFilter
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ThemeMode
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.CellLook
import com.bamnat.core.recipe.CellPattern
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ShiftLooks
import com.bamnat.core.recipe.ThemeSeed
import com.bamnat.core.work.WorkStyle

// ---- [STU-35] 느낌으로 시작하기 ----

/** [STU-35] The moods, each a [ThemeSeed.Variant] on a light or dark screen. */
internal enum class Feel(val label: String, val variant: ThemeSeed.Variant, val dark: Boolean) {
    CALM("차분하게", ThemeSeed.Variant.CALM, false),
    COZY("포근하게", ThemeSeed.Variant.SOFT, false),
    BRIGHT("또렷하게", ThemeSeed.Variant.VIVID, false),
    NIGHT("밤처럼", ThemeSeed.Variant.SOFT, true),
}

/** [STU-35] Cell starts that read on any ground (no fixed white), so every mood and colour goes with them. */
internal val FEEL_CELLS: List<Pair<String, (CalendarView) -> CalendarView>> = listOf(
    "깔끔하게" to { v -> v.copy(looks = emptyList(), pattern = CellPattern(), todayLook = null) },
    "다이어리" to { v -> v.copy(size = v.size ?: "tall", looks = listOf(CellLook(border = "thin", corner = 0, datePlace = "tl", mark = "line", pad = 3)), pattern = CellPattern()) },
    "스티커" to { v -> v.copy(looks = listOf(CellLook(bg = CellLook.SHIFT, bgAlpha = 30, corner = 14, datePlace = "tc", dateShape = "circle", mark = "dot")), pattern = CellPattern()) },
    "근무 색 채우기" to { v -> v.copy(looks = listOf(CellLook(mark = "fill", corner = 6, datePlace = "tl")), pattern = CellPattern()) },
)

/** [STU-35] The four starts a pick of 느낌·색·칸 offers: the same colour in four places ([ThemeSeed.SHUFFLES] 0, 1, 2, 5). */
internal fun feelStarts(seed: Long, feel: Feel): List<Pair<Int, ThemeSeed.Scheme>> =
    listOf(0, 1, 2, 5).map { it to ThemeSeed.shuffle(seed, feel.variant, feel.dark, it) }

/** [STU-35] A start as a draft: [scheme] over every screen and [cell]'s 칸 모양. */
internal fun feelDraft(d: AppSettings, scheme: ThemeSeed.Scheme, cell: Int): AppSettings {
    val withColours = StudioColors.withScheme(d, scheme, allScreens = true)
    val view = FEEL_CELLS.getOrNull(cell)?.second?.invoke(CalendarView.decode(withColours.calView)) ?: return withColours
    return withColours.copy(calView = view.encode())
}

/**
 * [STU-35] 1.36 느낌으로 새로 만들기 (스튜디오 극상 조사 S6, Canva의 시작안·마리오 메이커의 쉬운 시작에서 착안): 분위기, 색, 칸 모양을 하나씩
 * 고르면 시작안 넷이 바로 보이고, 하나를 누르면 그 모양으로 편집기가 열려요. 무엇이든 그 뒤에 고칠 수 있어요.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun FeelStartDialog(onDismiss: () -> Unit, onPick: (ThemeSeed.Scheme, Int) -> Unit) {
    var feel by rememberSaveable { mutableStateOf(Feel.COZY) }
    var seed by rememberSaveable { mutableLongStateOf(StudioColors.SEEDS[7]) }
    var cell by rememberSaveable { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("느낌으로 새로 만들기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("1. 분위기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Feel.entries.forEach { f -> FilterChip(selected = f == feel, onClick = { feel = f }, label = { Text(f.label) }) }
                }
                Text("2. 색", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StudioColors.SEEDS.forEachIndexed { i, c ->
                        val on = c == seed
                        Box(Modifier.size(34.dp).clip(CircleShape).background(Color(c.toInt()))
                            .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                            .clickable { seed = c }.semantics { contentDescription = "색 ${i + 1}"; selected = on })
                    }
                }
                Text("3. 칸 모양", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FEEL_CELLS.forEachIndexed { i, (name, _) -> FilterChip(selected = i == cell, onClick = { cell = i }, label = { Text(name) }) }
                }
                Text("마음에 드는 시작안을 누르세요", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                feelStarts(seed, feel).forEach { (i, s) ->
                    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(onClickLabel = "이 시작안으로 만들기") { onPick(s, cell) }) {
                        SchemeStrip(s)
                        Text("시작안 · ${ThemeSeed.SHUFFLES[i]}", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 2.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

// ---- [STU-36] 미리 보기 조건 ----

/** Deuteranopia (적록 색약, the most common) as a colour matrix, from the public Machado et al. 2009 simulation values (severity 1.0). */
private val DEUTAN = floatArrayOf(
    0.367322f, 0.860646f, -0.227968f, 0f, 0f,
    0.280085f, 0.672501f, 0.047413f, 0f, 0f,
    -0.011820f, 0.042940f, 0.968881f, 0f, 0f,
    0f, 0f, 0f, 1f, 0f,
)

/** [STU-36] Whether this phone can show the 색약 눈 view (a colour filter on a layer needs Android 12). */
internal val canSeeAsCvd: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * [STU-36] 1.36 미리 보기 조건 (스튜디오 극상 조사 S7): the example as it reads on a dark screen (the app's own parts dark, the user's
 * colours as they are), with big letters (1.5배) or through 적록 색약 eyes.
 */
@Composable
internal fun Conditioned(dark: Boolean, big: Boolean, cvd: Boolean, accent: Long, content: @Composable () -> Unit) {
    val m = if (cvd && canSeeAsCvd) Modifier.graphicsLayer {
        renderEffect = android.graphics.RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(DEUTAN)).asComposeRenderEffect()
    } else Modifier
    val base = LocalDensity.current
    val inner: @Composable () -> Unit = {
        CompositionLocalProvider(LocalDensity provides if (big) Density(base.density, base.fontScale * 1.5f) else base) { Box(m) { content() } }
    }
    if (dark) com.bamnat.app.ui.theme.BamnatTheme(themeMode = ThemeMode.DARK, accent = accent, content = inner) else inner()
}

/** [STU-36] The chips that turn the conditions on, in a row under the example's own chips. */
@Composable
internal fun ConditionChips(dark: Boolean, big: Boolean, cvd: Boolean, onDark: (Boolean) -> Unit, onBig: (Boolean) -> Unit, onCvd: (Boolean) -> Unit) {
    FilterChip(selected = dark, onClick = { onDark(!dark) }, label = { Text("어두운 화면") })
    FilterChip(selected = big, onClick = { onBig(!big) }, label = { Text("큰 글자") })
    if (canSeeAsCvd) FilterChip(selected = cvd, onClick = { onCvd(!cvd) }, label = { Text("색약 눈") })
}

// ---- [STU-39] 영역 잠그기 ----

/** [STU-39] 잠그기 chips: a locked tab keeps its look when 한 색·섞기·모든 화면에·조각 얹기 change the rest. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun LockChips(vm: StudioViewModel) {
    Text("잠그기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text("잠근 화면은 한 색·섞기·모든 화면에·조각 얹기가 건드리지 않아요. 그 화면 도구로는 고칠 수 있어요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        StudioColors.LOCKABLE.forEach { tab ->
            val on = tab in vm.locked
            FilterChip(selected = on, onClick = { vm.locked = if (on) vm.locked - tab else vm.locked + tab },
                label = { Text((if (on) "🔒 " else "") + lockName(tab, vm.style)) },
                modifier = Modifier.semantics { contentDescription = "${lockName(tab, vm.style)} " + if (on) "잠금 풀기" else "잠그기" })
        }
    }
}

internal fun lockName(tab: String, style: WorkStyle?): String = when (tab) {
    com.bamnat.core.layout.TabOrder.MONEY -> com.bamnat.app.ui.TopLevel.MONEY.labelFor(style)
    com.bamnat.core.layout.TabOrder.STATS -> com.bamnat.app.ui.TopLevel.STATS.labelFor(style)
    com.bamnat.core.layout.TabOrder.SETTINGS -> "설정·알람"
    else -> LookParts.tabLabel(tab)
}

// ---- [STU-37] 근무에 따라 바뀌는 모양 ----

/** [STU-37] A scheme as a 근무에 따라 look: the whole app's colours and 달력 색. */
internal fun shiftLookOf(s: ThemeSeed.Scheme): ShiftLooks.Look = ShiftLooks.Look(
    ScreenLooks().with(ScreenLooks.ALL, ScreenLook(bg = s.bg, card = s.card, ink = s.ink, accent = s.accent)).encode(),
    CalendarTheme(today = s.today, sat = s.sat, sun = s.sun).encode(),
)

/** [STU-37] The modes a kind of work has: 야간 근무 only where nights are worked, 쉬는 날 everywhere but 투자. */
internal fun shiftModes(style: WorkStyle?): List<ShiftLooks.Mode> = when (style) {
    WorkStyle.INVEST -> emptyList()
    WorkStyle.DAY, WorkStyle.BUSINESS -> listOf(ShiftLooks.Mode.OFF)
    else -> listOf(ShiftLooks.Mode.NIGHT, ShiftLooks.Mode.OFF)
}

/**
 * [STU-37] 1.36 근무에 따라 바뀌는 모양 도구: for 야간 근무 중 and 쉬는 날, a switch, one colour and a 느낌 that make that look, and 미리 보기
 * to see the example as it would be then. It goes on the phone with 입히기, like the rest of the look.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ShiftLooksTool(vm: StudioViewModel, d: AppSettings, previewing: ShiftLooks.Mode) {
    val looks = remember(d.shiftLooks) { ShiftLooks.decode(d.shiftLooks) }
    val modes = shiftModes(vm.style)
    Text("근무표에 따라 앱 색이 저절로 바뀌어요. " + (if (ShiftLooks.Mode.NIGHT in modes) "22~06시에 걸친 근무 중에는 ‘야간 근무’ 모양, " else "") +
        "근무표에 쉬는 날로 정한 날에는 ‘쉬는 날’ 모양이에요. 칸 디자인·배치·그림은 그대로이고 색만 바뀌어요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    modes.forEach { mode ->
        val look = looks.of(mode)
        var seed by rememberSaveable(mode) { mutableLongStateOf(StudioColors.seedOf(d)) }
        var variant by rememberSaveable(mode) { mutableStateOf(ThemeSeed.Variant.SOFT) }
        var dark by rememberSaveable(mode) { mutableStateOf(mode == ShiftLooks.Mode.NIGHT) }
        fun put(s: Long = seed, v: ThemeSeed.Variant = variant, dk: Boolean = dark) {
            seed = s; variant = v; dark = dk
            val l = shiftLookOf(ThemeSeed.scheme(s, v, dk))
            vm.changeMerged("근무에 따라 · ${mode.label}") { it.copy(shiftLooks = ShiftLooks.decode(it.shiftLooks).with(mode, l).encode()) }
        }
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${mode.label} 모양", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                        Text(if (look == null) "꺼져 있어요" else if (mode == ShiftLooks.Mode.NIGHT) "근무 중에만 바뀌고, 끝나면 평소 모양이에요" else "그날 하루 바뀌어요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(look != null, { on ->
                        if (on) put() else {
                            if (previewing == mode) vm.previewAs(ShiftLooks.Mode.NORMAL)
                            vm.changeMerged("근무에 따라 · ${mode.label} 끔") { it.copy(shiftLooks = ShiftLooks.decode(it.shiftLooks).with(mode, null).encode()) }
                        }
                    }, modifier = Modifier.semantics { contentDescription = "${mode.label} 모양 켜기" })
                }
                if (look != null) {
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StudioColors.SEEDS.forEachIndexed { i, c ->
                            val on = c == seed
                            Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c.toInt()))
                                .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                                .clickable { put(s = c) }.semantics { contentDescription = "${mode.label} 색 ${i + 1}"; selected = on })
                        }
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ThemeSeed.Variant.entries.forEach { v -> FilterChip(selected = v == variant, onClick = { put(v = v) }, label = { Text(v.label) }) }
                        FilterChip(selected = dark, onClick = { put(dk = !dark) }, label = { Text("어둡게") })
                    }
                    SchemeStrip(ThemeSeed.scheme(seed, variant, dark))
                    OutlinedButton(onClick = { vm.previewAs(if (previewing == mode) ShiftLooks.Mode.NORMAL else mode) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (previewing == mode) "평소 모양으로 보기" else "미리 보기에 ${mode.label} 모양 보기")
                    }
                }
            }
        }
    }
    Text("입히기를 누르면 근무에 따라 바뀌는 모양도 함께 내 앱에 들어가요. 장터에 올리는 테마에는 들어가지 않아요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
