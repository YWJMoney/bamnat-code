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
                         modifier: Modifier = Modifier) {
    Box(modifier.size((340 * scale).dp, (560 * scale).dp).clipToBounds(), contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(340.dp, 560.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            MockPhone(r, style, tab, onTab, onPick)
        }
    }
}

/**
 * [TRY-01] 지금 ↔ 입으면, one phone: [after] is drawn over [before] up to [split] of its width (1 = all [after]), with a line where they meet,
 * so the two can be held side by side on one screen.
 */
@Composable
internal fun CompareCanvas(before: Recipe, after: Recipe, style: WorkStyle?, tab: String, onTab: (String) -> Unit, scale: Float, split: Float,
                           onPick: () -> Unit = {}) {
    Box(Modifier.size((340 * scale).dp, (560 * scale).dp)) {
        ScaledPhone(before, style, tab, onTab, scale, onPick)
        Box(Modifier.matchParentSize().drawWithContent { clipRect(right = size.width * split) { this@drawWithContent.drawContent() } }) {
            ScaledPhone(after, style, tab, onTab, scale, onPick)
        }
        if (split in 0.02f..0.98f) Box(Modifier.fillMaxHeight().width(2.dp).offset(x = (340 * scale * split).dp - 1.dp)
            .background(MaterialTheme.colorScheme.primary))
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0f to "지금", 0.5f to "반반", 1f to "입으면").forEach { (v, label) ->
                            com.bamnat.app.ui.components.FilterChip(selected = split == v, onClick = { split = v }, label = { Text(label) })
                        }
                    }
                    Box(Modifier.padding(top = 8.dp)) {
                        CompareCanvas(mine, after, style, tab, { tab = it }, scale = 0.6f, split = split, onPick = { tapped = true })
                    }
                    Slider(split, { split = it }, modifier = Modifier.fillMaxWidth(0.7f).semantics { contentDescription = "지금과 입으면 나눠 보기"; stateDescription = "입으면 ${(split * 100).toInt()}%" })
                    Text("왼쪽이 입은 모습, 오른쪽이 지금이에요. 화면을 누르면 그 화면의 조각을 담을 수 있어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(Modifier.padding(top = 6.dp))
                Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (parts.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("고를 것 · ${picked.size}/${parts.size}", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
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
                            Text("고른 ${picked.size}개 보관함에 담아 두기 (무료)")
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
