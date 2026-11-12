package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.appContainer
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.ui.components.Dialog
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch

/**
 * 1.39 [MKF-01] 크게 넘겨 보기 (사용자 2026-11-07 "입어보고 리스트를 보며 살펴보는 인터랙션 부족하거나 사용자 경험이 없으면 의미가 없어"):
 * 장터 카드 그림을 누르면 열려요. 좌우로 밀면 목록 순서대로 다음 작품, 위아래로 밀면 그 작품이 바꾸는 다른 화면(달력 → 가계부 → 메모 → 급여
 * → 위젯 → 알람 …, [MarketShots.faces]). 아래 단추는 '내 앱에 입혀 보기'(저장 없이 실제 화면, [onTry])와 '자세히'([onOpen]).
 * 폴드 펼침처럼 넓으면 두 장을 나란히 보여 줘요(지금 · 다음).
 */
@Composable
internal fun MarketFlip(
    items: List<RecipeItem>, start: Int, style: WorkStyle?, onDismiss: () -> Unit,
    onOpen: (String) -> Unit, onTry: (RecipeItem) -> Unit, onAt: (Int) -> Unit = {},
) {
    if (items.isEmpty()) return
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            val pager = rememberPagerState(start.coerceIn(0, items.lastIndex)) { items.size }
            val scope = rememberCoroutineScope()
            androidx.compose.runtime.LaunchedEffect(pager.currentPage) { onAt(pager.currentPage) }
            // PC 4차 c0ca3d9f (장터 5): inside the safe area — the top row clear of the status bar's clock, the hint clear of the gesture bar.
            com.bamnat.app.ui.theme.BarIcons(light = MaterialTheme.colorScheme.background.luminance() > 0.5f)
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing).padding(top = 8.dp)) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "닫기") }
                    Text("크게 넘겨 보기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    // Where in the list, and a way back by one (a finger may not reach the far side of a fold).
                    // [RVM-35] HQ-66: a quick second tap counts from the work it is going to (targetPage), not the one still under the finger.
                    if (pager.currentPage > 0) androidx.compose.material3.TextButton(onClick = { scope.launch { pager.animateScrollToPage((pager.targetPage - 1).coerceAtLeast(0)) } }) { Text("◀ 이전") }
                    Text("${pager.currentPage + 1} / ${items.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp))
                    if (pager.currentPage < items.lastIndex) androidx.compose.material3.TextButton(onClick = { scope.launch { pager.animateScrollToPage((pager.targetPage + 1).coerceAtMost(items.lastIndex)) } }) { Text("다음 ▶") }
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val wide = maxWidth >= 600.dp
                    // 2.0.0 [RVM-35] 총괄 HQ-66 (갈래 H PC-88과 같은 구조): a quick swipe made while a work is still turning counts from the
                    // work it is going to, so three quick swipes turn three works (before: two). The calendar's fling, as it is.
                    HorizontalPager(pager, Modifier.fillMaxSize().testTag(FLIP_WORKS_TAG), pageSize = if (wide) PageSize.Fixed(maxWidth / 2) else PageSize.Fill,
                        key = { items[it].code }, beyondViewportPageCount = 1, flingBehavior = com.bamnat.app.ui.calendar.queuedTurns(pager)) { i ->
                        FlipPage(items[i], style, onOpen = { onOpen(items[i].code) }, onTry = { onTry(items[i]) })
                    }
                }
                Text("좌우로 밀면 다음 작품, 위아래로 밀면 이 작품의 다른 화면이에요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 12.dp))
            }
        }
    }
}

/** The works' pager in [MarketFlip], for tests. */
internal const val FLIP_WORKS_TAG = "market-flip-works"

/** One work: its screens one above the other, its name and what it is, and the two ways on. */
@Composable
private fun FlipPage(item: RecipeItem, style: WorkStyle?, onOpen: () -> Unit, onTry: () -> Unit) {
    val c = LocalContext.current.appContainer
    val recipe by produceState<Recipe?>(null, item.code, item.version) { value = MarketShots.recipe(c, item) }
    val shown = style?.takeIf { it in item.styles || item.styles.isEmpty() } ?: item.styles.firstOrNull() ?: style
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        val r = recipe
        val faces = remember(r, shown) { r?.let { MarketShots.faces(item, it, shown) }.orEmpty() }
        val v = rememberPagerState { faces.size.coerceAtLeast(1) }
        val scope = rememberCoroutineScope()
        // The screens it changes, as chips (a tap goes there; the dots say there are more below).
        if (faces.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            faces.forEachIndexed { i, f ->
                FilterChip(selected = v.currentPage == i, onClick = { scope.launch { v.animateScrollToPage(i) } }, label = { Text(MarketShots.faceName(f, shown)) })
            }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            // As big as the space lets it, the example phone's shape.
            val w = min(maxWidth * 0.9f, (maxHeight - 8.dp) * (MarketShots.PHONE_W / MarketShots.PHONE_H))
            when {
                r == null -> CircularProgressIndicator()
                faces.size <= 1 -> WorkFace(r, faces.firstOrNull() ?: MarketShots.faceOf(item, r), w, shown, onClick = onTry)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    VerticalPager(v, Modifier.weight(1f, fill = false).size(w, w * (MarketShots.PHONE_H / MarketShots.PHONE_W)), key = { it },
                        flingBehavior = com.bamnat.app.ui.calendar.queuedTurns(v)) { p ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { WorkFace(r, faces[p], w, shown, onClick = onTry) }
                    }
                    // Dots on the side: where in this work's screens.
                    Column(Modifier.padding(start = 6.dp).semantics { contentDescription = "${faces.size}개 화면 가운데 ${v.currentPage + 1}번째" },
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        faces.indices.forEach { i ->
                            Box(Modifier.size(if (i == v.currentPage) 8.dp else 6.dp).clip(CircleShape)
                                .background(if (i == v.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
                        }
                    }
                }
            }
        }
        Column(Modifier.widthIn(max = 520.dp).fillMaxWidth().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.fx.isNotEmpty()) FxBadge(locked = !com.bamnat.app.logic.FxAccess.pro(c))
            }
            sectionLine(item, shown, long = true)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
            fxLine(item)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // [RVM-34] HQ-72: the numbers and who made it, within two lines at 200% (at large text the maker has a line of its own).
            if (androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f) {
                MarketNumbersText(item)
                Text(MarketMaker.line(item), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            } else MarketNumbersText(item, tail = MarketMaker.line(item))
            Spacer(Modifier.size(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onTry, enabled = recipe != null, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                    Text(if (item.scope == "logic" || item.scope == "part") "전후 해 보기" else "내 앱에 입혀 보기", maxLines = 1)
                }
                OutlinedButton(onClick = onOpen, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("자세히", maxLines = 1) }
            }
        }
    }
}
