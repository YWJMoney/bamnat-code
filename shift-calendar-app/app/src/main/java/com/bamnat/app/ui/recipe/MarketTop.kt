package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloat
import com.bamnat.app.ui.components.scrollEdges
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.bamnat.app.recipe.MarketMood
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.app.ui.components.ChipClear
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.Tab

/** 2.0.0 [RVM-64] One thing picked under 거르기, shown as a chip under 장터's top with its X. */
internal data class MarketPick(val label: String, val clear: () -> Unit)

/**
 * 2.0.0 [RVM-64] 검토 U08a-01: what is picked under 거르기 (what is as it starts is not shown): 정렬 when not 요즘 뜨는, the 구간's own 탭 ·
 * 꾸미기·기능 / 종류 · 쓰는 화면 / 분위기, ✦ 프로 · 무료, 다른 유형도. While a word is looked for, only those a search keeps (정렬, 값, 유형).
 */
internal fun marketPicks(state: RecipeListViewModel.State, vm: RecipeListViewModel, mood: MarketMood.Mood?, onMoodOff: () -> Unit): List<MarketPick> = buildList {
    val kind = state.kind
    val price = RecipeClient.price(kind)
    if (!state.searching) when {
        kind?.startsWith(RecipeClient.SCOPE_TAB) == true -> {
            val tab = RecipeClient.tabOf(kind)
            val made = RecipeClient.madeOf(kind)
            if (tab.isNotEmpty()) add(MarketPick(marketTabLabel(tab, vm.style)) { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind("", made), price)) })
            // The same words as its chip in 거르기.
            mapOf("look" to "🎨 꾸미기", "func" to "⚙️ 기능")[made]?.let { add(MarketPick(it) { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind(tab, null), price)) }) }
        }
        kind.isTool() -> {
            val tool = RecipeClient.toolOf(kind)
            val on = RecipeClient.toolOn(kind)
            if (tool in RecipeClient.TOOL_KINDS) add(MarketPick(toolLabel(tool)) { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind("", on), price)) })
            on?.let { add(MarketPick(screenLabel(it, vm)) { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind(tool, null), price)) }) }
        }
        else -> mood?.let { add(MarketPick(it.label, onMoodOff)) }
    }
    if (RecipeClient.fxOnly(kind)) add(MarketPick("✦ 프로 기능") { vm.kind(RecipeClient.plain(kind)) })
    if (RecipeClient.freeOnly(kind)) add(MarketPick("무료로 다 되는 것") { vm.kind(RecipeClient.plain(kind)) })
    if (state.tab != RecipeListViewModel.Tab.HOT) add(MarketPick(state.tab.label) { vm.tab(RecipeListViewModel.Tab.HOT) })
    if (state.allStyles && vm.style != null) add(MarketPick("다른 유형도") { vm.allStyles(false) })
}

private fun screenLabel(t: String, vm: RecipeListViewModel) = marketTabLabel(t, vm.style).removeSuffix(" 화면·규칙").let { if (it.endsWith("화면")) it else "$it 화면" }

/** The 구간's name on its tab: with a word looked for, how many it has ('테마 3', '도구 12+'), nothing while it is still looked in. */
private fun halfLabel(h: String, state: RecipeListViewModel.State): String {
    val name = RecipeListViewModel.halfName(h)
    val f = state.found[h]?.takeIf { state.searching && !it.loading } ?: return name
    return "$name ${f.items.size}${if (f.next != null) "+" else ""}"
}

/**
 * 2.0.0 [RVM-64] 검토 U08a-01·U08a-05·MKT-03 (조작이 다섯 층, 폰 첫 화면의 절반): 장터's top is two rows that stay over the list all the
 * way down and keep one height whether it is scrolled or not: 찾기 with 거르기 and 만들기 (1.39 MKD-60, 사용자 2026-11-08: the search box
 * first), then the three 구간 as tabs. What 거르기 picked follows as chips, each taken off with its X (only when something is picked).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun MarketTop(
    state: RecipeListViewModel.State, vm: RecipeListViewModel, picks: List<MarketPick>, big: Boolean, scrolled: Boolean,
    onHalf: (String) -> Unit, onFilter: () -> Unit, onMake: () -> Unit,
    /** 마을's 한 장씩 넘겨 보기 (테마 only). */
    onSwipe: (() -> Unit)?,
) {
    Column(Modifier.fillMaxWidth().background(com.bamnat.app.ui.theme.PhotoGround.solid())) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                val find: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    SearchBox(state.query, vm::query, if (big) "작품 찾기" else "이름·설명·초성으로 찾기",
                        Modifier.weight(1f), colors = com.bamnat.app.ui.theme.PhotoGround.fieldColors(), onSearch = { vm.load() })
                    Spacer(Modifier.width(4.dp))
                    val n = picks.size
                    // One tap opens all of 거르기; how many are picked is on it, and read with it.
                    // The count is a fact, not a warning (조건 6): the picked colour, not the error red.
                    androidx.compose.material3.BadgedBox(badge = { if (n > 0) androidx.compose.material3.Badge(modifier = Modifier.offset(x = (-10).dp, y = 8.dp), containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary) { Text("$n") } }) {
                        androidx.compose.material3.IconButton(onClick = onFilter, modifier = Modifier.semantics {
                            contentDescription = "거르기·정렬"; if (n > 0) stateDescription = "${n}개 고름"
                        }) { Icon(Icons.Filled.Tune, null) }
                    }
                    com.bamnat.app.ui.components.FilledTonalIconButton(onClick = onMake, modifier = Modifier.size(48.dp)
                        .semantics { contentDescription = "스튜디오에서 만들기" }) { Icon(Icons.Filled.Add, null) }
                }
                val halves: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
                    val all = RecipeListViewModel.HALVES
                    val at = all.indexOf(vm.halfNow()).coerceAtLeast(0)
                    val tabs: @Composable () -> Unit = {
                        all.forEachIndexed { i, h ->
                            Tab(selected = i == at, onClick = { onHalf(h) }, modifier = Modifier.heightIn(min = 48.dp)
                                .semantics { if (i == at) stateDescription = if (state.searching) "고름. 찾은 것은 아래에 구간마다" else "고름. 다시 누르면 맨 위로" },
                                text = { Text(halfLabel(h, state), maxLines = 1, softWrap = false) })
                        }
                    }
                    // With big letters the three names do not fit a third each: the row slides instead of cutting one.
                    if (big) androidx.compose.material3.SecondaryScrollableTabRow(selectedTabIndex = at, Modifier.weight(1f),
                        containerColor = Color.Transparent, edgePadding = 0.dp, divider = {}) { tabs() }
                    else androidx.compose.material3.SecondaryTabRow(selectedTabIndex = at, Modifier.weight(1f),
                        containerColor = Color.Transparent, divider = {}) { tabs() }
                    onSwipe?.let { go ->
                        androidx.compose.material3.IconButton(onClick = go, modifier = Modifier.semantics { contentDescription = "한 장씩 넘겨 보기" }) {
                            Text("🃏", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
                // An unfolded phone (600dp and wider) has room for both on one row, so the works start higher (조건 1·12).
                if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600)
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { halves(); Spacer(Modifier.width(8.dp)); find() }
                else {
                    Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { find() }
                    Row(verticalAlignment = Alignment.CenterVertically) { halves() }
                }
                // With big letters the picks would fill the first screen (조건 1): one chip says how many, and opens 거르기 where they are
                // marked; 모두 지우기 beside it. One pick keeps its own chip and X.
                if (big && picks.size > 1) Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilterChip(selected = true, onClick = onFilter, label = { Text("${picks.size}개 고름", maxLines = 1, softWrap = false) },
                        modifier = Modifier.semantics { contentDescription = "고른 것: " + picks.joinToString(", ") { it.label } })
                    TextButton(onClick = { vm.clearPicks() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("모두 지우기") }
                }
                // What is picked wraps onto as many lines as it takes, so none is out of sight to the side.
                else if (picks.isNotEmpty()) FlowRow(Modifier.fillMaxWidth().padding(top = 4.dp), verticalArrangement = Arrangement.Center,
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    picks.forEach { p ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = true, onClick = onFilter, label = { Text(p.label, maxLines = 1, softWrap = false) })
                            ChipClear("${p.label} 빼기", p.clear)
                        }
                    }
                    if (picks.size > 1) TextButton(onClick = { vm.clearPicks() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("모두 지우기") }
                }
            }
        }
        // The line under the top shows once the list has gone under it (the top itself never changes height).
        HorizontalDivider(color = if (scrolled) MaterialTheme.colorScheme.outlineVariant else Color.Transparent)
    }
}

/**
 * 2.0.0 [RVM-64] 검토 U08a-01: 거르기, one sheet with all that was a row of chips: 정렬, the 구간's own (테마: 분위기, 탭별 작품: 탭 ·
 * 꾸미기·기능, 도구: 종류 · 쓰는 화면), 값 (✦ 프로 · 무료), 유형, 보기. Each pick shows in the list behind at once; the sheet stays open
 * until X or 다 됐어요. While a word is looked for, a 구간's own picks are not offered (a search looks in all three).
 */
@OptIn(ExperimentalLayoutApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
internal fun MarketFilterSheet(
    state: RecipeListViewModel.State, vm: RecipeListViewModel, mood: MarketMood.Mood?, onMood: (MarketMood.Mood?) -> Unit,
    small: Boolean, onSmall: () -> Unit, onSwipe: (() -> Unit)?, picks: List<MarketPick>, onDismiss: () -> Unit,
) {
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        val scroll = rememberScrollState()
        Column(Modifier.fillMaxWidth().scrollEdges(scroll).verticalScroll(scroll).padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("거르기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            val kind = state.kind
            val price = RecipeClient.price(kind)
            Group("정렬") {
                RecipeListViewModel.Tab.entries.forEach { t -> FilterChip(selected = state.tab == t, onClick = { vm.tab(t) }, label = { Text(t.label) }) }
            }
            if (!state.searching) when {
                kind?.startsWith(RecipeClient.SCOPE_TAB) == true -> {
                    val tab = RecipeClient.tabOf(kind)
                    val made = RecipeClient.madeOf(kind)
                    Group("탭") {
                        FilterChip(selected = tab.isEmpty(), onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind("", made), price)) }, label = { Text("모든 탭") })
                        // PC 확인 5c21f3bb: a tab that kind of work has no works for is not offered.
                        RecipeClient.MARKET_TABS.filter { marketTabFits(it, vm.style) }.forEach { t ->
                            FilterChip(selected = tab == t, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind(t, made), price)) }, label = { Text(marketTabLabel(t, vm.style)) })
                        }
                    }
                    Group("만든 것") {
                        FilterChip(selected = made == null, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind(tab, null), price)) }, label = { Text("모두") })
                        listOf("look" to "🎨 꾸미기", "func" to "⚙️ 기능").forEach { (m, label) ->
                            FilterChip(selected = made == m, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind(tab, m), price)) }, label = { Text(label) })
                        }
                    }
                }
                kind.isTool() -> {
                    val tool = RecipeClient.toolOf(kind)
                    val on = RecipeClient.toolOn(kind)
                    Group("종류") {
                        FilterChip(selected = tool !in RecipeClient.TOOL_KINDS, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind("", on), price)) }, label = { Text("모두") })
                        RecipeClient.TOOL_KINDS.forEach { t ->
                            FilterChip(selected = tool == t, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind(t, on), price)) }, label = { Text(toolLabel(t)) })
                        }
                    }
                    Group("쓰는 화면") {
                        FilterChip(selected = on == null, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind(tool, null), price)) }, label = { Text("모든 화면") })
                        TOOL_SCREENS.filter { marketTabFits(it, vm.style) }.forEach { t ->
                            FilterChip(selected = on == t, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind(tool, t), price)) },
                                label = { Text(marketTabLabel(t, vm.style).removeSuffix(" 화면·규칙")) })
                        }
                    }
                }
                else -> Group("분위기") {
                    FilterChip(selected = mood == null, onClick = { onMood(null) }, label = { Text("모두") })
                    MarketMood.Mood.entries.forEach { m -> FilterChip(selected = mood == m, onClick = { onMood(m) }, label = { Text(m.label) }) }
                }
            }
            val plain = RecipeClient.plain(kind) ?: RecipeClient.SCOPE_THEME
            Group("값") {
                FilterChip(selected = price.isEmpty(), onClick = { vm.kind(plain) }, label = { Text("모두") })
                FxChip(RecipeClient.fxOnly(kind)) { vm.kind(RecipeClient.withFx(plain, !RecipeClient.fxOnly(kind))) }
                FreeChip(RecipeClient.freeOnly(kind)) { vm.kind(RecipeClient.withFree(plain, !RecipeClient.freeOnly(kind))) }
            }
            vm.style?.let { s ->
                Group("유형") {
                    FilterChip(selected = !state.allStyles, onClick = { vm.allStyles(false) }, label = { Text("${RecipeDescribe.short(s)} 작품만") })
                    FilterChip(selected = state.allStyles, onClick = { vm.allStyles(true) }, label = { Text("다른 유형도") })
                }
            }
            Group("보기") {
                FilterChip(selected = small, onClick = onSmall, label = { Text("작게 보기") })
                onSwipe?.let { go -> androidx.compose.material3.AssistChip(onClick = { onDismiss(); go() }, label = { Text("🃏 한 장씩 넘겨 보기") }) }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (picks.isNotEmpty()) TextButton(onClick = { vm.clearPicks(); onMood(null) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("모두 지우기") }
                TextButton(onClick = onDismiss, modifier = Modifier.heightIn(min = 48.dp)) { Text("다 됐어요") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Group(title: String, chips: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) { chips() }
    }
}

/**
 * 2.0.0 [RVM-64] 검토 U08a-04 (첫 화면이 뱅글이 하나): while works are on their way, grey phone-shaped cards where they will be, as
 * many to a row as the list has, read once as '불러오는 중'.
 */
@Composable
internal fun MarketSkeleton(cols: Int, rows: Int, small: Boolean) {
    val still = com.bamnat.app.ui.theme.LocalMotionOff.current
    val glow = if (still) null else androidx.compose.animation.core.rememberInfiniteTransition(label = "market-skeleton").animateFloat(0.55f, 1f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(900, easing = androidx.compose.animation.core.LinearEasing),
            androidx.compose.animation.core.RepeatMode.Reverse), label = "glow")
    val grey = MaterialTheme.colorScheme.surfaceContainerHighest
    Column(Modifier.fillMaxWidth().semantics { contentDescription = "불러오는 중" }
        .graphicsLayer { alpha = glow?.value ?: 1f },
        verticalArrangement = Arrangement.spacedBy(MARKET_GAP.dp)) {
        repeat(rows) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (small) MARKET_GAP_SMALL.dp else MARKET_GAP.dp)) {
                repeat(cols) {
                    Column(Modifier.weight(1f).clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.fillMaxWidth().aspectRatio(MarketShots.PHONE_W / MarketShots.PHONE_H).clip(MaterialTheme.shapes.medium).background(grey))
                        Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(MaterialTheme.shapes.extraSmall).background(grey))
                        Box(Modifier.fillMaxWidth(0.45f).height(10.dp).clip(MaterialTheme.shapes.extraSmall).background(grey))
                    }
                }
            }
        }
    }
}

/** [RVM-64] One row of a search's one list. */
internal sealed interface FindRow {
    val key: String
    /** A 구간's head: its name and how many. */
    data class Head(val half: String, val line: String) : FindRow { override val key get() = "find-head-$half" }
    data class Cards(val half: String, val row: List<RecipeItem>) : FindRow { override val key get() = "find-$half-" + row.first().code }
    data class Waiting(val half: String) : FindRow { override val key get() = "find-wait-$half" }
    data class Failed(val half: String, val text: String) : FindRow { override val key get() = "find-fail-$half" }
    data class More(val half: String) : FindRow { override val key get() = "find-more-$half" }
}

/**
 * [RVM-64] A search as one list: each 구간 that has something (or is still being looked in) under its head, in the tabs' order. A 구간
 * with nothing is left out; its tab says 0.
 */
internal fun findRows(state: RecipeListViewModel.State, cols: Int): List<FindRow> = buildList {
    RecipeListViewModel.HALVES.forEach { h ->
        val f = state.found[h] ?: return@forEach
        if (!f.loading && f.error == null && f.items.isEmpty()) return@forEach
        val name = RecipeListViewModel.halfName(h)
        add(FindRow.Head(h, if (f.items.isEmpty()) name else "$name · ${f.items.size}개${if (f.next != null) " 넘게" else ""}"))
        f.items.chunked(cols).forEach { add(FindRow.Cards(h, it)) }
        when {
            f.loading -> add(FindRow.Waiting(h))
            f.error != null -> add(FindRow.Failed(h, f.error))
            f.next != null -> add(FindRow.More(h))
        }
    }
}
