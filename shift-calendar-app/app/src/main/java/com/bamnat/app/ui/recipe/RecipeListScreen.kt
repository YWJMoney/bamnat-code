package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloat
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.onPlaced
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Close
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.stateDescription
import com.bamnat.app.recipe.RecipeClient
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.Dp
import com.bamnat.core.work.WorkStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.ui.components.asColor
import com.bamnat.core.recipe.Cover
import com.bamnat.core.recipe.RecipeDescribe

/** [RCP-23] 표지: the two colours as a gradient in the maker's direction, with the emoji large in the middle. */
@Composable
fun RecipeCover(cover: Cover, size: Dp = 56.dp, modifier: Modifier = Modifier) {
    val colors = listOf(cover.a.asColor(), cover.b.asColor())
    val brush = when (cover.dir) {
        1 -> Brush.horizontalGradient(colors)
        2 -> Brush.linearGradient(colors)
        3 -> Brush.linearGradient(colors, start = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f), end = androidx.compose.ui.geometry.Offset(0f, Float.POSITIVE_INFINITY))
        else -> Brush.verticalGradient(colors)
    }
    // [RCX-15] A picture the operator let show goes over the gradient; until it comes (or offline) the emoji stays.
    val c = androidx.compose.ui.platform.LocalContext.current.applicationContext.let { (it as? com.bamnat.app.BamnatApp)?.container }
    val picture by androidx.compose.runtime.produceState<android.graphics.Bitmap?>(null, cover.image) {
        value = cover.image?.let { id -> c?.let { com.bamnat.app.recipe.CoverImages.load(it, id) } }
    }
    Box(modifier.size(size).clip(RoundedCornerShape(size / 4)).background(brush), contentAlignment = Alignment.Center) {
        val p = picture
        if (p != null) androidx.compose.foundation.Image(p.asImageBitmap(), contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop, modifier = Modifier.matchParentSize())
        else Text(cover.emoji, fontSize = (size.value * 0.5f).sp)
    }
}

/**
 * [RCP-19] The way into 나눔 판 on every kind of work's 광장: what it is, in one line, and the button.
 * [RCP-34] 1.34: with the plaza's front blocks under it, 요즘 뜨는 and 새로 (three each) of this kind of work.
 * [RCX-08][RCX-04] 1.35: 이번 주 고른 것 first, and the 요청 판 last (설계서 4-5: 네 덩어리).
 */
@Composable
fun RecipeEntryCard(onOpen: () -> Unit, onMake: () -> Unit, modifier: Modifier = Modifier,
    hot: List<RecipeItem> = emptyList(), fresh: List<RecipeItem> = emptyList(), onItem: (String) -> Unit = {}, style: WorkStyle? = null,
    picks: List<RecipeItem> = emptyList(), onRequests: (() -> Unit)? = null,
    /** [RCX-04] 1.35 네 번째 덩어리: the most wished-for requests not yet done. */
    wishes: List<com.bamnat.app.recipe.RecipeRequest> = emptyList(),
    /** [RCX-32] 1.35 운영자가 꾸민 보여주기 판: what can be made, first and big (사용자 2026-10-30 "이것도 할수 있네?"). */
    showcase: List<RecipeItem> = emptyList()) {
    Card(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("장터 · 작품", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(entryLine(style),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showcase.isNotEmpty()) ShowcaseStrip(showcase, onItem)
            val shown = showcase.mapTo(HashSet()) { it.code }
            for ((label, list) in listOf("이번 주 고른 것" to picks, "요즘 뜨는" to hot, "새로 올라온" to fresh)) {
                val rest = list.filter { it.code !in shown }.take(3)
                if (rest.isEmpty()) continue
                shown += rest.map { it.code }
                Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                rest.forEach { item ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "작품 보기") { onItem(item.code) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RecipeCover(item.cover, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("좋아요 ${item.likes} · 넣어 본 분 ${item.users}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("작품 보기") }
                OutlinedButton(onClick = onMake, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 설정 나누기") }
            }
            // [RCX-20] 요청 판 보이기 off (설정 › 나눔 판) hides the way in.
            val requestsOn by LocalContext.current.appContainer.recipes.requestsOn.collectAsStateWithLifecycle()
            onRequests?.takeIf { requestsOn }?.let { open ->
                if (wishes.isNotEmpty()) {
                    Text("요청 판 · 많이 바라는 것", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
                    wishes.forEach { q ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClickLabel = "요청 판 열기", onClick = open),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("🙏", modifier = Modifier.width(32.dp))
                            Text(q.body, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text((if (q.state == "making") "만드는 중 · " else "") + "👍 ${q.likes}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = open, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (wishes.isEmpty()) "요청 판: 이런 작품 있었으면" else "요청 판 전체 보기") }
            }
        }
    }
}

/**
 * [RCX-32] 이런 것도 만들 수 있어요: the showcases side by side, each a big cover with its name and what it does,
 * so the 광장 shows at a glance how far a 판 can go. A tap opens it like any recipe (미리 보기, 고른 것만 넣기).
 */
@Composable
internal fun ShowcaseStrip(items: List<RecipeItem>, onItem: (String) -> Unit) {
    Text("이런 것도 만들 수 있어요", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp).semantics { heading() })
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.take(SHOWCASE_SHOWN).forEach { item ->
            OutlinedCard(Modifier.width(168.dp).clickable(onClickLabel = "작품 보기") { onItem(item.code) }) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // [MKV-01] 1.37: every work as it really looks (a theme on its calendar, a tab on its page, rules as their notifications).
                    MarketShot(item, 148.dp, LocalContext.current.appContainer.settingsRepository.current.workStyle, onClick = { onItem(item.code) })
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.heightIn(min = 40.dp))
                    Text(item.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
        }
    }
}

/** The showcases on a 광장: five per kind of work are made, so all of them fit. */
private const val SHOWCASE_SHOWN = 8

/** [RCP-34] The 나눔 판 block of a 광장, with its 고른 것·요즘 뜨는·새로 read once for the calendar's kind of work (quietly empty offline). */
@Composable
fun PlazaRecipeBlock(c: com.bamnat.app.AppContainer, calendar: String, onOpen: () -> Unit, onMake: () -> Unit, onItem: (String) -> Unit,
    modifier: Modifier = Modifier, onRequests: (() -> Unit)? = null) {
    val style = remember(calendar) { c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle) }
    val lists by androidx.compose.runtime.produceState(Triple(emptyList<RecipeItem>(), emptyList<RecipeItem>(), emptyList<RecipeItem>()), calendar) {
        if (!c.flags.on(com.bamnat.core.flags.FlagCatalog.RECIPE_BROWSE)) return@produceState
        value = runCatching {
            Triple(runCatching { c.recipeApi.picks(style) }.getOrDefault(emptyList()), c.recipeApi.list(style, "hot").items, c.recipeApi.list(style, "new").items)
        }.getOrDefault(value)
    }
    // [RCX-32] Read on its own: a server before 1.35 answers 보여주기 with the usual list, which is not shown then.
    val showcase by androidx.compose.runtime.produceState(emptyList<RecipeItem>(), calendar) {
        if (!c.flags.on(com.bamnat.core.flags.FlagCatalog.RECIPE_BROWSE)) return@produceState
        value = runCatching { c.recipeApi.list(style, "showcase").items.filter { it.showcase } }.getOrDefault(emptyList())
    }
    val wishes by androidx.compose.runtime.produceState(emptyList<com.bamnat.app.recipe.RecipeRequest>(), calendar) {
        if (onRequests == null || !c.flags.on(com.bamnat.core.flags.FlagCatalog.RECIPE_BROWSE)) return@produceState
        value = runCatching { c.recipeApi.requests(null, style) }.getOrDefault(emptyList())
            .filter { it.state != "done" }.sortedByDescending { it.likes }.take(3)
    }
    RecipeEntryCard(onOpen, onMake, modifier, lists.second, lists.third, onItem, style, lists.first, onRequests, wishes, showcase)
}

/** [RCP-38] The first line of 나눔 판 in the words of each kind of work: what is worth taking there. */
internal fun entryLine(style: WorkStyle?): String = when (style) {
    WorkStyle.SHIFT -> "다른 교대 분들이 꾸민 근무 색·이모지, 근무별 알람, 인수인계 메모 틀을 미리 보고 고른 것만 넣어요"
    WorkStyle.DAY -> "다른 일근 분들이 쓰는 달력 색, 출근 알람, 업무 메모 틀을 미리 보고 고른 것만 넣어요"
    WorkStyle.HOURLY -> "다른 알바 분들이 쓰는 주휴·야간·휴게 규칙과 알람을 미리 보고 넣어요. 시급은 내가 넣어요"
    WorkStyle.FIELD -> "다른 현장 분들이 쓰는 공수 칩, 경비 항목, 단가표 틀을 미리 보고 넣어요. 일당은 내가 넣어요"
    WorkStyle.BUSINESS -> "다른 사장님들이 쓰는 영업일, 마감 체크 목록, 경비 분류를 미리 보고 넣어요"
    WorkStyle.INVEST -> "다른 분들이 쓰는 투자 원칙과 일지 틀을 미리 보고 넣어요. 종목 추천은 올라오지 않아요"
    null -> "다른 분이 꾸민 근무 색·알람·메모 틀·규칙을 미리 보고, 고른 것만 내 달력에 넣어요"
} + ". 7일 안에 되돌릴 수 있어요"

@Composable
fun RecipeListScreen(
    vm: RecipeListViewModel,
    /** Null in 마을's 장터, a place of the tab ([HUB-01]). */
    onBack: (() -> Unit)?,
    onOpen: (String) -> Unit,
    onMake: () -> Unit,
    onMine: () -> Unit,
    title: String = "장터",
    askCode: Boolean = false,
    /** [VIL-17] 테마 넘겨 보며 고르기 (마을's 장터), a chip at the head of 테마's 분위기. */
    onSwipe: (() -> Unit)? = null,
) {
    // 2.0.0 [RVM-21] MKT-32 Seeing the 장터 never brings a whole font from Google Fonts (each card draws in its card fonts).
    MarketFontsOf(emptySet<String>()) { RecipeListPage(vm, onBack, onOpen, onMake, onMine, title, askCode, onSwipe) }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun RecipeListPage(vm: RecipeListViewModel, onBack: (() -> Unit)?, onOpen: (String) -> Unit, onMake: () -> Unit, onMine: () -> Unit,
                           title: String, askCode: Boolean, onSwipe: (() -> Unit)?) {
    val state by vm.state.collectAsStateWithLifecycle()
    var codeAsk by remember { mutableStateOf(askCode) }
    // 1.39 [MKF-01] 크게 넘겨 보기: the card's picture opens it at that work; [MKF-03] 작게 보기 (3열, 펼침 4열), kept on this phone.
    val ctx = LocalContext.current
    val c = ctx.appContainer
    var flipAt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<Int?>(null) }
    // 1.39 [MKD-45] 테마 분위기 칩 (불러온 테마 가운데서 이 폰이 걸러요).
    var mood by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf<String?>(null) }
    val inThemes = RecipeClient.plain(state.kind) == RecipeClient.SCOPE_THEME
    val moodNow = com.bamnat.app.recipe.MarketMood.Mood.entries.firstOrNull { it.name == mood }?.takeIf { inThemes }
    val shown = if (moodNow == null) state.items else state.items.filter { moodNow in com.bamnat.app.recipe.MarketMood.of(it.look) }
    var small by remember { mutableStateOf(runCatching { ctx.getSharedPreferences(VIEW_PREFS, 0).getBoolean(VIEW_SMALL, false) }.getOrDefault(false)) }
    val flipScope = androidx.compose.runtime.rememberCoroutineScope()
    // 2.0.0 [RVM-32] 총괄 HQ-43 (조건 1·12, 편 폭에서도 두 칸이라 미리 그림 하나가 화면 높이의 절반을 넘고 한 화면에 두 작품): a card keeps
    // about the size it has on a folded phone and a row takes as many as the width holds (펼친 폴드 4칸, 작게 보기 6칸); the grid runs the
    // full width (조건 12: 표는 가득), the words above it stay in the 640dp column.
    val cols = marketCols(androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 32, small)
    // [RVM-31] HQ-42: with big letters (130% and up) the box asks in two words, so the ask is not cut.
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    // Back from trying one on the real app: 넘겨 보기 again at that work, or (입기·넣기 on the band) that work's 입어 보기 to put it in.
    val again by LiveTryOn.reopen.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(again) {
        LiveTryOn.take { it is LiveTryOn.From.Flip }?.let { back ->
            val f = back.from as LiveTryOn.From.Flip
            if (back.wear) { com.bamnat.app.recipe.RecipeLinks.tryNext = f.code; flipAt = null; onOpen(f.code) } else flipAt = f.at
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // [RVQ-36] U22-04: the next page comes by itself near the end; RVM-63 (below, M) does it and 3c's own was dropped for it.
    // 사용자 2026-11-03 (마을 › 장터 스크롤): in 마을 the tab row already says 장터 and whose; the list starts right under it.
    val hubHeader = if (onBack == null) com.bamnat.app.ui.hub.LocalHubHeader.current else null
    val reselect = com.bamnat.app.ui.hub.LocalHubReselect.current
    // [TOWN-11] 장터 tapped again (or opened from a 소식) also reads the list again, so a work just put up shows.
    val reselectAt = remember { reselect }
    androidx.compose.runtime.LaunchedEffect(reselect) { if (reselect > reselectAt) { vm.load(); listState.animateScrollToItem(0) } }
    // 2.0.0 [RVM-63] 검토 U08a-02: the next page comes by itself a few rows before the end (no '더 보기' to find and press); a page that
    // failed waits for 다시 해 보기 at the end, so a phone offline does not ask again and again.
    val nearEnd by remember { derivedStateOf { listState.layoutInfo.let { i -> i.totalItemsCount > 0 && (i.visibleItemsInfo.lastOrNull()?.index ?: 0) >= i.totalItemsCount - 4 } } }
    androidx.compose.runtime.LaunchedEffect(nearEnd, state.items.size, state.next) {
        if (nearEnd && state.next != null && !state.loading && state.moreError == null && state.error == null) vm.load(more = true)
    }
    // A 구간 or 정렬 changed while scrolled down starts its list at the top (not somewhere in the middle).
    // PC 확인 a6cab3c8 (장터를 내린 채 다른 탭에 갔다 오면 맨 위로 돌아가요): only a real change of 구간·정렬 moves the list; coming back
    // to the screen (the same picks, read back as it was left) keeps where it was.
    val chipsKey = "${state.kind}|${state.tab}|${state.allStyles}|${state.searching}"
    var chipsSeen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(chipsKey) }
    androidx.compose.runtime.LaunchedEffect(chipsKey) {
        if (chipsKey == chipsSeen) return@LaunchedEffect
        chipsSeen = chipsKey
        if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) listState.scrollToItem(0)
    }
    // 2.0.0 [RVM-64] 검토 U08a-01: 거르기 is one sheet; what it picked shows under the top as chips with X.
    var filterOpen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    val picks = marketPicks(state, vm, moodNow, onMoodOff = { mood = null })
    val toSmall = {
        small = !small
        runCatching { ctx.getSharedPreferences(VIEW_PREFS, 0).edit().putBoolean(VIEW_SMALL, small).apply() }
        Unit
    }
    // [RVM-64] 검토 U08a-03: with a word, the three 구간's works as one list under their heads (넘겨 보기 goes through all of them).
    val find = if (state.searching) findRows(state, cols) else emptyList()
    val flipList = if (state.searching) RecipeListViewModel.HALVES.flatMap { state.found[it]?.items.orEmpty() } else shown
    val codeFound = com.bamnat.app.recipe.RecipeLinks.find(state.query.takeIf { it.trim().length in 8..80 })
    val hint = RecipeClient.wordKind(state.query, state.kind)?.takeIf { state.searching }
    val listScope = androidx.compose.runtime.rememberCoroutineScope()
    Scaffold(
        topBar = {
            if (onBack != null) TopAppBar(
                title = { Text(title + (vm.style?.let { " · " + RecipeDescribe.short(it) } ?: ""), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    // [R136-13] In 마을 these live in 공방 (내 것 한곳, 코드로 받기 하나); a 장터 opened on its own keeps them.
                    if (onBack != null) {
                        // [RVM-21] 총괄 #462 (장터 글자 200%: 위 막대 제목이 '…'만 남음): with big letters (or a very narrow phone) the two go
                        // under one '더 보기', so the title keeps its room; at the usual size they stay one tap each.
                        val tight = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f ||
                            androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 360
                        if (tight) {
                            var menu by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "더 보기: 코드로 받기, 내 작품") }
                                com.bamnat.app.ui.components.DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("코드로 받기") }, onClick = { menu = false; codeAsk = true })
                                    androidx.compose.material3.DropdownMenuItem(text = { Text("내 작품") }, onClick = { menu = false; onMine() })
                                }
                            }
                        } else {
                            TextButton(onClick = { codeAsk = true }) { Text("코드로 받기") }
                            TextButton(onClick = onMine) { Text("내 작품") }
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)) {
            // 2.0.0 [RVM-64] 검토 U08a-01·U08a-05·MKT-03: 찾기 · 거르기 · 만들기 and the three 구간 stay over the list, one height all the way
            // down (before, the box went up with the list and the row of chips changed height as it stuck); 만들기 is always here, so no
            // round + comes over the works once scrolled.
            val lead = listOfNotNull(codeFound, hint, state.error).size
            MarketTop(state, vm, picks, big, scrolled = listState.canScrollBackward,
                onHalf = { h ->
                    if (state.searching) {
                        vm.half(h)
                        val at = find.indexOfFirst { it is FindRow.Head && it.half == h }
                        if (at >= 0) listScope.launch { listState.animateScrollToItem(lead + at) }
                    } else if (h == vm.halfNow()) listScope.launch { listState.animateScrollToItem(0) }
                    else vm.half(h)
                },
                onFilter = { filterOpen = true }, onMake = onMake, onSwipe = onSwipe?.takeIf { inThemes })
            LazyColumn(
                // 2.0.0 [RVO-09] Same as 설정 찾기 (PC-82): the found works end above the keyboard.
                Modifier.fillMaxSize().imePadding(),
                state = listState,
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                // [RVM-31] HQ-42: 8dp between rows, as between the cards of a row ([MARKET_GAP]).
                verticalArrangement = Arrangement.spacedBy(MARKET_GAP.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // PC 확인 1.36 (코드로 찾으면 어느 구간에도 안 나옴): a code typed in the box opens that work, whatever its 구간.
                codeFound?.let { code -> item(key = "code") {
                    FilledTonalButton(onClick = { onOpen(code) }, modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 48.dp)) { Text("코드 $code 작품 열기") }
                } }
                // 1.39 [MKD-03] '값 칸'·'위젯'·'프로'처럼 장치·종류 이름으로 찾으면 그 칩으로 가는 길을 맨 앞에.
                hint?.let { (k, name) -> item(key = "hint") {
                    Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) {
                        androidx.compose.material3.AssistChip(onClick = { vm.query(""); vm.kind(k) }, label = { Text("$name 보기") })
                    }
                } }
                // 1.38 (장터 그림 LOW, PC 제안): offline with a list already shown, one quiet line says when it was read, with 다시 시도;
                // with nothing shown, the problem and 다시 시도 (before, a red line and no way to try again but leaving).
                state.error?.let { e -> item(key = "error") {
                    if (state.items.isNotEmpty() && state.loadedAt > 0L) com.bamnat.app.ui.company.RefreshLine(refreshing = false, stale = true,
                        onRetry = { vm.load() }, seenAt = java.time.Instant.ofEpochMilli(state.loadedAt).atZone(com.bamnat.core.time.Today.zone())
                            .toLocalTime().let { "%d:%02d".format(it.hour, it.minute) })
                    // 2.0.0 [RVP-13] 검토 UIX-15 (조건 6·7): a market that cannot be read is not a danger: the one offline line, quiet, with 다시 하기.
                    else com.bamnat.app.ui.components.OfflineLine(Modifier.padding(start = 8.dp), text = e, onRetry = { vm.load() })
                } }
                val cardRow: @Composable (List<RecipeItem>) -> Unit = { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (small) MARKET_GAP_SMALL.dp else MARKET_GAP.dp)) {
                        row.forEach { item ->
                            ThemeTile(item, vm.style, Modifier.weight(1f), small = small, onPicture = { flipAt = flipList.indexOf(item).coerceAtLeast(0) }) { onOpen(item.code) }
                        }
                        repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                if (state.searching) {
                    items(find, key = { it.key }) { r ->
                        when (r) {
                            is FindRow.Head -> Text(r.line, style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(top = 8.dp).semantics { heading() })
                            is FindRow.Cards -> cardRow(r.row)
                            is FindRow.Waiting -> MarketSkeleton(cols, rows = 1, small = small)
                            is FindRow.Failed -> com.bamnat.app.ui.components.OfflineLine(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 8.dp),
                                text = r.text, onRetry = { if (state.found[r.half]?.items.isNullOrEmpty()) vm.load() else vm.findMore(r.half) })
                            is FindRow.More -> TextButton(onClick = { vm.findMore(r.half) }, modifier = Modifier.heightIn(min = 48.dp)) {
                                Text("${RecipeListViewModel.halfName(r.half)} 더 보기")
                            }
                        }
                    }
                    // [RVM-64] 검토 MKT-04: '없어요' only once all three 구간 have answered.
                    if (!state.loading && state.error == null && find.isEmpty()) item(key = "find-none") { MarketEmpty(state, vm, onMake) }
                    return@LazyColumn
                }
                if (state.tab == RecipeListViewModel.Tab.GOOD || state.tab == RecipeListViewModel.Tab.SHOWCASE || state.tab == RecipeListViewModel.Tab.DATA) item(key = "notes") {
                    Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (state.tab == RecipeListViewModel.Tab.GOOD) Text("좋아요가 5개 넘은 작품만 여기 나와요. 표가 적으면 ‘새로’에서 볼 수 있어요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.tab == RecipeListViewModel.Tab.SHOWCASE) Text("장터 작품으로 이만큼 꾸밀 수 있다는 걸 보여 주려고 운영자가 만든 판이에요. 미리 보고 마음에 드는 것만 넣어요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (state.tab == RecipeListViewModel.Tab.DATA) Text("공공 자료를 바탕으로 운영자가 만든 작품이에요. 출처가 함께 적혀 있어요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (!state.loading && state.error == null && state.items.isEmpty()) item {
                    // [R136-05] 1.36 (리뷰 R-11): an empty list can be read again (다시 불러오기). [RVM-09] PC-64 (찾기 결과 없음에 칸이가 없고
                    // 단추가 글과 줄이 안 맞아요): every empty 장터 is the empty-screen part, 칸이 + one line + one button, centred as one.
                    MarketEmpty(state, vm, onMake)
                }
                // [HUB-04] Themes are picked by eye: two big pictures a row. [MKV-01] 1.37 (사용자 2026-11-05 "실제로 해당 테마나 기능들의 모습을"):
                // 탭별 작품 and 도구 too, each drawn as it really looks, so every 구간 is picked by eye the same way.
                // 1.39 [MKF-03] 작게 보기: 50개씩 들어와도 한 화면에 더 많이 (폰 3열, 폴드 펼침 4열).
                if (moodNow != null && shown.isEmpty() && state.items.isNotEmpty()) item {
                    Text("불러온 테마 가운데 ‘${moodNow.label}’ 테마가 아직 없어요" + (if (state.next != null) ". 아래로 내리면 더 불러와요" else ""),
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
                val rows = shown.chunked(cols)
                rows.firstOrNull()?.let { row -> item(key = row.first().code + "|" + cols) { cardRow(row) } }
                // 2.0.0 [RVM-31] HQ-42 (조건 1): in 마을 the 팔로잉 줄 (or 이달의 제작자) comes under the first row of works, still on the first
                // screen; above them it put the first work at about 36% of a folded phone. It scrolls away with the list as before.
                hubHeader?.let { h -> item(key = "hub") { Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { h() } } }
                items(rows.drop(1), key = { row -> row.first().code + "|" + cols }) { row -> cardRow(row) }
                // [RVM-64] 검토 U08a-04 (첫 화면이 뱅글이 하나): the first page on its way is grey phone-shaped cards where the works will be.
                if (state.loading && state.items.isEmpty()) item(key = "skeleton") { MarketSkeleton(cols, rows = if (small) 4 else 3, small = small) }
                else if (state.loading) item(key = "loading") { MarketSkeleton(cols, rows = 1, small = small) }
                // [RVM-63] 검토 MKT-02: a next page that failed says so here, where the eye is, with 다시 해 보기 (not at the top, off screen).
                else if (state.moreError != null) item(key = "more-error") {
                    com.bamnat.app.ui.components.OfflineLine(Modifier.padding(start = 8.dp, top = 8.dp, bottom = 8.dp), text = state.moreError!!, onRetry = { vm.load(more = true) })
                }
                else if (state.next != null) item { TextButton(onClick = { vm.load(more = true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("더 보기") } }
            }
        }
    }
    if (filterOpen) MarketFilterSheet(state, vm, moodNow, onMood = { m -> mood = m?.name }, small = small, onSmall = toSmall,
        onSwipe = onSwipe?.takeIf { inThemes }, picks = picks, onDismiss = { filterOpen = false })
    if (codeAsk) CodeDialog(onCode = { codeAsk = false; onOpen(it) }, onDismiss = { codeAsk = false })
    flipAt?.let { at ->
        MarketFlip(flipList, at, vm.style, onDismiss = { flipAt = null }, onOpen = { code -> flipAt = null; onOpen(code) },
            // 2.0.0 [RVM-63] 검토 MKT-30: 크게 넘겨 보기 reads the next page as it nears the end of what is loaded.
            onAt = { i -> flipAt = i; if (!state.searching && i >= shown.size - 3 && state.next != null && !state.loading && state.moreError == null) vm.load(more = true) },
            more = !state.searching && state.next != null,
            onTry = { item ->
                flipScope.launch {
                    val r = MarketShots.recipe(c, item)
                    val looks = r?.let { com.bamnat.core.recipe.LookParts.of(it).map { p -> p.id }.toSet() }.orEmpty()
                    // 도구 (규칙·단추·부품) are tried 전후 in their page; a work that cannot be laid on as it is goes to its 입어 보기.
                    if (r == null || looks.isEmpty() || item.scope == "logic" || item.scope == "part") { flipAt = null; onOpen(item.code); return@launch }
                    val tab = MarketShots.tabOf(MarketShots.faceOf(item, r))
                    val at = flipList.indexOfFirst { it.code == item.code }.coerceAtLeast(0)
                    val ok = runCatching { LiveTryOn.start(c, r, own = false, looks, tab, LiveTryOn.From.Flip(item.code, at)) }.getOrDefault(false)
                    if (ok) flipAt = null else { com.bamnat.app.recipe.RecipeLinks.tryNext = item.code; flipAt = null; onOpen(item.code) }
                }
            })
    }
}

/**
 * 2.0.0 [RVM-32] 총괄 HQ-43: how many cards a row of 장터 takes in [widthDp]: each keeps at least the size it has two to a row on a folded
 * phone (160dp, 작게 보기 104dp), so a folded phone has 2 (작게 3) and an unfolded one 4 (작게 6). Also the maker's page grid.
 */
internal fun marketCols(widthDp: Int, small: Boolean): Int {
    val min = if (small) MARKET_CARD_SMALL else MARKET_CARD
    val gap = if (small) MARKET_GAP_SMALL else MARKET_GAP
    return ((widthDp + gap) / (min + gap)).coerceIn(if (small) 3 else 2, if (small) 8 else 6)
}

internal const val MARKET_CARD = 160
internal const val MARKET_CARD_SMALL = 104
internal const val MARKET_GAP = 8

internal const val MARKET_GAP_SMALL = 6

private const val VIEW_PREFS = "market_view"
private const val VIEW_SMALL = "small"

@Composable
fun RecipeRow(item: RecipeItem, modifier: Modifier = Modifier,
    /** 1.36 PC 시험: a one-tab recipe names that tab in the viewer's words (알바 '출근·시급', not '근무·급여'). */
    style: com.bamnat.core.work.WorkStyle? = androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.current.workStyle,
    onClick: () -> Unit) {
    val fxLocked = item.fx.isNotEmpty() && !com.bamnat.app.logic.FxAccess.pro(androidx.compose.ui.platform.LocalContext.current.appContainer)
    Card(modifier.fillMaxWidth().clickable(onClickLabel = "작품 보기", onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // [MKV-01] 1.37: the work as it really looks, in place of its cover and small swatch.
            MarketShot(item, 64.dp, style, onClick)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                // 2.0.0 [RVM-37] 총괄 HQ-83: at large text the marks beside the name cut it ('야간 회복 …'); there the name has the line
                // (two lines) and the marks go on the line under it.
                val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
                val marks: @Composable () -> Unit = {
                    // [R136-17] 1.36 (리뷰 R-12): 보여주기 is the operator's word; people read 추천.
                    if (item.showcase) Badge("추천")
                    else if (item.official) Badge(if (item.publicData) "공개 자료" else "시작")
                    if (item.fx.isNotEmpty()) FxBadge(locked = fxLocked)
                }
                if (big) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (item.showcase || item.official || item.fx.isNotEmpty()) Row(verticalAlignment = Alignment.CenterVertically) { marks() }
                } else Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    marks()
                }
                if (item.summary.isNotEmpty()) Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [MKX-01] One tab's: which tab it changes, said once. 1.39 [MKC-01]: and whether it is 꾸미기 or 기능; a 도구 says which.
                sectionLine(item, style, long = true)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                // 1.39 [MKC-04] 무엇이 프로 기능 장치인지 한 줄 ("✦ 값 칸 · 단추 위젯·빠른 설정·바로가기").
                fxLine(item)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                // [RVM-34] HQ-72: within two lines at 200% (the styles go at large text; the line is read aloud whole).
                MarketNumbersText(item, long = true, tail = RecipeDescribe.styles(com.bamnat.core.recipe.Recipe(item.title, styles = item.styles, pieces = emptyList())))
            }
        }
    }
}

/**
 * [HUB-04] One work in 장터's grid: the work drawn large, its name and how many like and wear it. [MKV-01] 1.37: the picture is the work
 * as it really looks ([MarketShot]); a 기능별 탭 says which tab, a 추천 says so.
 */
@Composable
private fun ThemeTile(item: RecipeItem, style: WorkStyle?, modifier: Modifier = Modifier, small: Boolean = false, onPicture: (() -> Unit)? = null, onClick: () -> Unit) {
    val fxLocked = item.fx.isNotEmpty() && !com.bamnat.app.logic.FxAccess.pro(androidx.compose.ui.platform.LocalContext.current.appContainer)
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    Card(modifier.clickable(onClickLabel = when (item.scope) { "tab" -> "작품 보기"; "part", "logic" -> "도구 보기"; else -> "테마 보기" }, onClick = onClick)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 1.39 [MKF-01] The picture opens 크게 넘겨 보기 (the name and lines under it, the work's page).
                MarketShot(item, maxWidth, style, onPicture ?: onClick)
                // 1.39 [MKD-01] 프로 표는 그림 위 모서리에 (이름 자리를 뺏지 않게). 2.0.0 [RVM-37] HQ-83: at large text 추천 joins it
                // there, above 프로, so the name has the whole width.
                val corner = item.fx.isNotEmpty() || (big && item.showcase)
                if (corner) Column(Modifier.align(Alignment.TopEnd).padding(6.dp), horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (big && item.showcase) Badge("추천")
                    if (item.fx.isNotEmpty()) FxBadge(locked = fxLocked)
                }
            }
            // PC 확인 e50bb622: 추천 by the name, not over the picture's own title. 총괄 #462 (글자 200%에서 이름이 '야간 …'): two lines.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (!big && item.showcase) Badge("추천")
            }
            // [RVM-42] HQ-100: a work for another kind of work says whose ('일근용 작품'), seen with 다른 유형도 on.
            RecipeDescribe.otherKind(item.styles, style)?.let {
                Text("$it 작품", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            sectionLine(item, style, long = false)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 1.39 [MKC-04] 프로 기능 장치가 무엇인지 (값 칸 · 알림 단추 …).
            if (!small) fxLine(item)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            // 1.36 [VIL-18] 오래 쓰는 분이 많은 것과 새 창작자의 첫 작품이 눈에 띄게.
            // [RVM-34] HQ-72: '♥ 12 · 34명' at large text, so it stays within two lines in a tile.
            MarketNumbersText(item)
            // 1.39 [MKD-41] 누가 만들었는지 (표식 · 팔로워). 표식이 있으면 이름 색으로도.
            if (!small) Text(MarketMaker.line(item), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MarketMaker.mark(item.mark)?.let { com.bamnat.app.ui.village.markColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.newMaker) Text("새 창작자", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * 2.0.0 [RVM-34] (총괄 HQ-72, 조건 4·12: 글자 200%에서 장터 카드의 숫자 줄이 세 줄): how many like and put in a work, in one line that
 * stays within two at any text size. At 100% it says it in words; at large text it keeps the two numbers that matter ("♥ 12 · 34명")
 * and drops the rest, and a screen reader always hears the whole of it in words.
 */
internal object MarketNumbers {
    /** The whole line in words, as read aloud. */
    fun spoken(item: RecipeItem): String = listOfNotNull("좋아요 ${item.likes}", "넣어 본 분 ${item.users}명",
        item.kept?.let { "7일 뒤에도 쓰는 분 $it%" }).joinToString(" · ")

    /**
     * The card line: [long] is the list row's (with 안좋아요 and 7일 유지), else the tile's; [big] is text at 130% or more. A line that
     * wraps breaks between the numbers ('·'), never inside one ('오래 / 써요 81%'): the spaces inside each are no-break spaces.
     */
    fun line(item: RecipeItem, big: Boolean, long: Boolean = false): String = when {
        big -> listOf("♥ ${item.likes}", "${item.users}명")
        long -> listOfNotNull("좋아요 ${item.likes}", "안좋아요 ${item.dislikes}", "넣어 본 분 ${item.users}", item.kept?.let { "7일 유지 $it%" })
        else -> listOfNotNull("♥ ${item.likes}", "넣어 본 분 ${item.users}", item.kept?.takeIf { it >= 60 }?.let { "오래 써요 $it%" })
    }.joinToString(" · ") { it.replace(' ', '\u00A0') }
}

/** [MarketNumbers] as a card shows it: two lines at most, read aloud in words. [tail] goes at large text unless [keepTail] (공방's 코드). */
@Composable
internal fun MarketNumbersText(item: RecipeItem, long: Boolean = false, tail: String? = null, keepTail: Boolean = false, modifier: Modifier = Modifier) {
    val big = androidx.compose.ui.platform.LocalDensity.current.fontScale > 1.3f
    val spoken = MarketNumbers.spoken(item) + (tail?.let { " · $it" } ?: "")
    Text(MarketNumbers.line(item, big, long) + (tail?.takeIf { !big || keepTail }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
        modifier = modifier.semantics { contentDescription = spoken })
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.clip(MaterialTheme.shapes.extraSmall).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** 1.39 [MKD-11] '무료로 다 되는 것' 칩: ✦ 프로 기능 칩과 짝, 고르면 프로 없이 모두 도는 작품만 (서버 fx=0). */
@Composable
internal fun FreeChip(selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text("무료로 다 되는 것") },
        modifier = Modifier.semantics { contentDescription = "프로 없이 모두 무료로 쓰는 작품만 보기" })
}

/** 1.39 [MKC-04] '✦ 프로 기능' 칩: 고르면 1.39부터 새로 연 기능 장치(값 칸·단추 위젯·알림 단추 …)를 쓴 작품만. */
@Composable
internal fun FxChip(selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text("✦ 프로 기능") },
        modifier = Modifier.semantics { contentDescription = "프로 기능 작품만 보기" })
}

/**
 * 1.39 [MKC-04] 카드의 프로 표: 앱 어디서나 같은 [com.bamnat.app.ui.components.ProBadge] (TIER-06), 이 폰에서 아직 켤 수 없으면 자물쇠.
 * 1.38까지 장치만 쓴 작품에는 붙지 않아요 (모양·옛 규칙·단추는 무료).
 */
@Composable
internal fun FxBadge(locked: Boolean) {
    com.bamnat.app.ui.components.ProBadge(Modifier.padding(start = 6.dp), locked = locked)
}

/** 1.39 [MKD-60] What each 구간 is, in full (the line that was under the three cards), for TalkBack. */
private fun about(title: String): String = when (title) {
    "도구" -> "입은 테마나 기본 모양은 그대로 두고 부품 하나, 규칙 하나, 단추 하나만 더해요. 눌러서 전후를 직접 해 볼 수 있어요"
    "탭별 작품" -> "탭 하나를 위해 만든 꾸미기(모양)와 기능(틀·묶음)이에요. 지금 입은 테마는 그대로 두고 그 탭만 바꿔요"
    else -> "달력·가계부·메모·급여까지 모든 화면을 한 벌로 꾸민 테마예요. 입은 뒤 마음에 안 드는 탭은 탭별 작품으로 바꿀 수 있어요"
}

/**
 * 1.39 [MKC-01] 카드 한 줄: 탭별 작품은 어느 탭에 들어가는지와 꾸미기·기능, 도구는 종류와 쓰는 화면 ("🗂 메모 탭에만 · 꾸미기", "⚡ 자동 규칙 · 가계부").
 */
internal fun sectionLine(item: com.bamnat.app.recipe.RecipeItem, style: WorkStyle?, long: Boolean): String? = when (item.scope) {
    "tab" -> item.tabs.firstOrNull()?.let { t ->
        "🗂 " + marketTabLabel(t, style) + (if (t == "alarm" || t == "widget" || t == "night") "에만" else " 탭에만") + (if (long) " 들어가요" else "") +
            (madeLabel(item.made)?.let { " · $it" } ?: "") +
            // 1.39 [MKD-44] (V10) 위젯 작품은 크기부터 봐요: 위젯 모양은 밤낮 위젯을 한꺼번에 꾸며요.
            (if (t == "widget") " · " + WIDGET_SIZES else "")
    }
    "part", "logic" -> {
        val kinds = item.tools.ifEmpty { if (item.scope == "part") listOf("part") else emptyList() }
        val what = if (kinds.isEmpty()) "⚡ 규칙·단추" else kinds.joinToString(" + ") { toolLabel(it) }
        val on = item.tabs.filter { it in RecipeClient.MARKET_TABS }.map { marketTabLabel(it, style).removeSuffix(" 화면·규칙") }
        what + (if (on.isEmpty()) "" else " · " + on.joinToString("·")) +
            // 1.39 [MKD-44] 바깥 단추(단추 위젯·빠른 설정)를 쓰는 도구는 2×1 단추 위젯.
            (if ("outside" in item.fx) " · 단추 위젯 2×1" else "")
    }
    else -> null
}

/** 1.39 [MKD-44] 위젯 모양이 꾸미는 밤낮 위젯의 크기 (res/xml widget_*_info: 오늘·가계부·디데이 2×1, 주 4×1, 달 4×3). */
internal const val WIDGET_SIZES = "오늘 2×1 · 주 4×1 · 달 4×3"

/** 1.39 [MKC-04] 작품에 든 1.39 새 기능 장치 ("✦ 값 칸 · 알림 단추"); null when it has none (모두 무료). */
internal fun fxLine(item: com.bamnat.app.recipe.RecipeItem): String? =
    item.fx.mapNotNull(com.bamnat.core.formula.FxPro.Part::of).takeIf { it.isNotEmpty() }?.let { "✦ " + com.bamnat.core.formula.FxPro.plain(it) }

/** 1.39 [MKC-01] 탭별 작품이 바꾸는 것. */
internal fun madeLabel(m: String?): String? = when (m) { "look" -> "꾸미기"; "func" -> "기능"; "both" -> "꾸미기+기능"; else -> null }

/** 1.39 [MKC-01] 도구의 종류. */
internal fun toolLabel(t: String): String = when (t) { "part" -> "🧱 부품"; "rule" -> "⚡ 자동 규칙"; "button" -> "🔘 단추"; else -> "🧰 도구" }

/** 1.39 [MKC-01] 도구가 쓰이는 화면 (위젯은 부품이 없어 빼요). */
internal val TOOL_SCREENS = listOf("calendar", "memo", "money", "pay", "alarm", "night")

/** 1.39 [MKC-01] Whether this 장터 kind is 도구 (옛 규칙·단추 포함). */
internal fun String?.isTool(): Boolean = this == RecipeClient.SCOPE_LOGIC || this?.startsWith(RecipeClient.SCOPE_TOOL) == true

/** [MKX-01] A 탭별 작품's tab name in this kind of work's words (근무·급여, 장부 …). */
internal fun marketTabLabel(tab: String, style: com.bamnat.core.work.WorkStyle?): String = when (tab) {
    "calendar" -> "달력"
    "pay" -> com.bamnat.app.ui.TopLevel.STATS.labelFor(style)
    "money" -> com.bamnat.app.ui.TopLevel.MONEY.labelFor(style)
    "memo" -> "메모"
    // 빌드 6 확인: the app has no 알람 tab; it is the alarm's screen and rules.
    "alarm" -> "알람 화면·규칙"
    "widget" -> "위젯"
    // 1.39 [FLOW-21]
    "night" -> "밤 화면"
    else -> "앱 전체"
}

/** [FREE-08] The words of 종류로 보기's chips. */
internal val KIND_NAMES = mapOf("skin" to "테마·스킨", "layout" to "배치", "memo" to "메모장", "money" to "가계부", "rules" to "알람·규칙")

/** [RCP-22] 코드로 받기: 8 letters, or a link or message pasted whole. */
@Composable
fun CodeDialog(onCode: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val code = com.bamnat.app.recipe.RecipeLinks.find(text)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("코드로 받기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(text, { text = it }, label = { Text("코드 8자리나 받은 링크") }, modifier = Modifier.fillMaxWidth())
                Text(if (text.isNotBlank() && code == null) "코드를 찾지 못했어요. 0·O·1·I 없이 8자리예요" else "받은 메시지를 통째로 붙여 넣어도 돼요",
                    style = MaterialTheme.typography.bodySmall, color = if (text.isNotBlank() && code == null) MaterialTheme.colorScheme.error else Color.Unspecified)
            }
        },
        confirmButton = { TextButton(onClick = { code?.let(onCode) }, enabled = code != null) { Text("열기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/**
 * PC 확인 V44: the small picture of a list card — the recipe's own look, or for a theme or one that changes the calendar but brought
 * no colours, the example week in plain colours (the same as 자랑 카드 and 넣기 창), so the same work looks the same everywhere.
 */
internal fun com.bamnat.app.recipe.RecipeItem.thumb(): com.bamnat.app.recipe.ThemeSwatch? =
    look?.withDays(styles.firstOrNull()) ?: com.bamnat.app.recipe.ThemeSwatch.example(styles.firstOrNull()).takeIf { scope == "theme" || "calendar" in tabs || "app" in tabs }

/** Whether 장터's 탭별 작품 [t] has works for [style] (null: every kind of work). */
internal fun marketTabFits(t: String, style: WorkStyle?): Boolean = when (t) {
    "pay" -> style != WorkStyle.BUSINESS && style != WorkStyle.INVEST
    "alarm" -> style == null || style == WorkStyle.SHIFT || style == WorkStyle.DAY || style == WorkStyle.HOURLY
    else -> true
}

/** [RVM-09] PC-64: an empty 장터 (a 구간, a 탭, a filter or a search with nothing) as one 빈 화면: what is not here, and the one way on. */
@Composable
private fun MarketEmpty(state: RecipeListViewModel.State, vm: RecipeListViewModel, onMake: () -> Unit) {
    val q = state.query.trim()
    // A code typed has its own 작품 열기 above; no 없어요 under it.
    if (q.isNotEmpty() && com.bamnat.app.recipe.RecipeLinks.find(q.takeIf { it.length in 8..80 }) != null) return
    val more = mutableListOf<Pair<String, () -> Unit>>()
    var title: String
    var detail: String? = null
    var mood = com.bamnat.app.ui.components.KaniMood.Point
    var action: Pair<String, () -> Unit>? = null
    val tabNow = state.kind?.takeIf { it.startsWith(RecipeClient.SCOPE_TAB) }?.let { RecipeClient.tabOf(it) }?.takeIf { it.isNotEmpty() }
    when {
        // 1.39 [MKC-04] '✦ 프로 기능'·'무료'만 골랐는데 없으면: 무엇이 없는지와 모든 작품으로 돌아가는 길, 처음 만들 곳.
        RecipeClient.price(state.kind).isNotEmpty() && q.isEmpty() -> {
            val fx = RecipeClient.fxOnly(state.kind)
            title = if (fx) "✦ 프로 기능 작품이 아직 없어요" else "무료로 다 되는 작품이 아직 없어요"
            detail = if (fx) "값 칸·단추 위젯 같은 새 기능 장치로 처음 만들어 보세요" else null
            action = "모든 작품 보기" to { vm.kind(RecipeClient.plain(state.kind)) }
            more += "스튜디오에서 만들기" to onMake
        }
        // PC 제안 1.36 (기능별 탭 › 달력이 비면): that tab's name, and the way to make the first.
        tabNow != null && q.isEmpty() -> {
            title = "${marketTabLabel(tabNow, vm.style)} 탭 ${madeLabel(RecipeClient.madeOf(state.kind))?.let { "$it " }.orEmpty()}작품이 아직 없어요"
            action = "스튜디오에서 만들기" to onMake
            more += "다시 불러오기" to { vm.load() }
        }
        // 2.0.0 [RVQ-37] 검토 U22b-06: '!!!' or '...' has nothing to find by (the server says ask: "words"); said as it is, not 'none'.
        q.isNotEmpty() && !searchable(q) -> {
            title = "찾는 말을 글자로 적어 주세요"
            detail = "작품 이름이나 ‘교대 가계부’, ‘밤 근무 알람’처럼 적으면 찾아요"
        }
        // 1.39.1 [P391-01] 2.0.0 [RVM-64]: a search looks in all three 구간 at once; this says none only after all three answered.
        q.isNotEmpty() -> {
            when {
                // 2.0.0 [RVM-40] PC-149: not for my kind of work, but another kind has it: where, and the way there in one tap.
                state.otherStyles.isNotEmpty() -> {
                    title = "‘$q’에 맞는 작품이 내 유형에는 없어요"
                    detail = "다른 유형 작품: " + state.otherStyles.entries.joinToString(", ") { (h, n) -> "${RecipeListViewModel.halfName(h)}에 $n" }
                    mood = com.bamnat.app.ui.components.KaniMood.Basic
                    action = "다른 유형 작품 보기" to { vm.allStyles(true) }
                    more += "스튜디오에서 만들기" to onMake
                }
                else -> {
                    title = "‘$q’에 맞는 작품이 없어요"
                    detail = "다른 낱말로 찾거나, 없으면 직접 만들어 볼 수 있어요"
                    mood = com.bamnat.app.ui.components.KaniMood.Worry
                    action = "스튜디오에서 만들기" to onMake
                }
            }
        }
        else -> {
            val ready = state.tab == RecipeListViewModel.Tab.SHOWCASE || state.tab == RecipeListViewModel.Tab.OFFICIAL || state.tab == RecipeListViewModel.Tab.DATA
            val inTheme = state.kind == RecipeClient.SCOPE_THEME
            title = if (ready) "아직 준비된 작품이 없어요" else "아직 작품이 없어요"
            // PC 확인 38d63451: the other half's, and a way to make the first one.
            detail = if (state.other > 0) (if (inTheme) "탭별 작품에는 ${state.other}개 있어요" else "테마에는 ${state.other}개 있어요")
                else if (ready) null else "내 설정을 처음으로 나눠 보세요"
            if (state.other > 0) {
                action = (if (inTheme) "탭별 작품 보기" else "테마 보기") to { vm.kind(if (inTheme) RecipeClient.SCOPE_TAB else RecipeClient.SCOPE_THEME) }
                more += "스튜디오에서 만들기" to onMake
            } else action = "스튜디오에서 만들기" to onMake
            more += "다시 불러오기" to { vm.load() }
        }
    }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        com.bamnat.app.ui.components.EmptyState(title, detail, mood = mood, action = action)
        if (more.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            more.take(2).forEach { (words, go) -> TextButton(onClick = go, modifier = Modifier.heightIn(min = 48.dp)) { Text(words) } }
        }
    }
}

/** 2.0.0 [RVQ-37] 검토 U22b-06: a search the server can look by: a letter, a digit or a 표지 emoji (as the server's searchWords). */
internal fun searchable(q: String): Boolean = q.any { it.isLetterOrDigit() } ||
    q.codePoints().anyMatch { it >= 0x1F000 || it in 0x2600..0x27BF }
