package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.bamnat.app.appContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
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
            Text("장터 · 레시피", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
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
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "레시피 보기") { onItem(item.code) },
                        verticalAlignment = Alignment.CenterVertically) {
                        RecipeCover(item.cover, size = 32.dp)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("좋아요 ${item.likes} · 쓰는 분 ${item.users}", style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("레시피 보기") }
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
                TextButton(onClick = open, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (wishes.isEmpty()) "요청 판: 이런 레시피 있었으면" else "요청 판 전체 보기") }
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
            OutlinedCard(Modifier.width(168.dp).clickable(onClickLabel = "레시피 보기") { onItem(item.code) }) {
                Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // [FREE-08] A theme shows as itself; the rest by their cover.
                    item.look?.let { ThemeThumb(it, width = 148.dp) } ?: RecipeCover(item.cover, size = 56.dp)
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

@OptIn(ExperimentalMaterial3Api::class)
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
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var codeAsk by remember { mutableStateOf(askCode) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title + (vm.style?.let { " · " + RecipeDescribe.short(it) } ?: "")) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    TextButton(onClick = { codeAsk = true }) { Text("코드로 받기") }
                    TextButton(onClick = onMine) { Text("내 레시피") }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onMake, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("만들기") })
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            item {
                Column(Modifier.widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.query, vm::query, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("이름·설명·초성으로 찾기") }, leadingIcon = { Icon(Icons.Filled.Search, null) },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.load() }))
                    // [MKX-01] 1.36 두 구간 (사용자 2026-10-31 "장터가 너무 보기 어렵게 … 테마를 주는건지 기능별로 보여지는건지 구간을 나눠서"):
                    // 테마 (모든 것을 종합해 만든 한 벌) and 기능별 탭 (탭 하나를 전문으로, 고른 테마 위에 얹어요), two big halves on top.
                    val featured = state.kind?.startsWith(RecipeClient.SCOPE_TAB) == true
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarketHalf("테마", "앱 전체를 한 벌로", "🎨", selected = !featured, Modifier.weight(1f)) { vm.kind(RecipeClient.SCOPE_THEME) }
                        MarketHalf("기능별 탭", "탭 하나를 전문으로", "🧩", selected = featured, Modifier.weight(1f)) {
                            if (!featured) vm.kind(RecipeClient.SCOPE_TAB)
                        }
                    }
                    Text(if (featured) "지금 입은 테마는 그대로 두고, 고른 탭 하나만 바꿔요. 입어 보기에서 내 테마 위에 얹어 봐요"
                        else "달력·가계부·메모 등 여러 화면을 한 번에 꾸민 테마예요. 입은 뒤 마음에 안 드는 탭은 기능별 탭으로 바꿀 수 있어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (featured) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val now = state.kind.orEmpty().removePrefix(RecipeClient.SCOPE_TAB)
                        FilterChip(selected = now.isEmpty(), onClick = { vm.kind(RecipeClient.SCOPE_TAB) }, label = { Text("모든 탭") })
                        RecipeClient.MARKET_TABS.forEach { t ->
                            FilterChip(selected = now == t, onClick = { vm.kind(RecipeClient.SCOPE_TAB + t) }, label = { Text(marketTabLabel(t, vm.style)) })
                        }
                    }
                    // How sorted and whose, under the half chosen.
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        RecipeListViewModel.Tab.entries.forEach { t -> FilterChip(selected = state.tab == t, onClick = { vm.tab(t) }, label = { Text(t.label) }) }
                        if (vm.style != null) {
                            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                            FilterChip(selected = state.allStyles, onClick = { vm.allStyles(!state.allStyles) }, label = { Text("다른 유형도") })
                        }
                    }
                    if (state.tab == RecipeListViewModel.Tab.GOOD) Text("좋아요가 5개 넘은 레시피만 여기 나와요. 표가 적으면 ‘새로’에서 볼 수 있어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.tab == RecipeListViewModel.Tab.SHOWCASE) Text("장터 레시피로 이만큼 꾸밀 수 있다는 걸 보여 주려고 운영자가 만든 판이에요. 미리 보고 마음에 드는 것만 넣어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.tab == RecipeListViewModel.Tab.DATA) Text("공공 자료를 바탕으로 운영자가 만든 레시피예요. 출처가 함께 적혀 있어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            state.error?.let { e -> item { Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) } }
            if (!state.loading && state.error == null && state.items.isEmpty()) item {
                // [R136-05] 1.36 (리뷰 R-11, 관리자 개발사항 d8quAdDH7r3QKNMp): an empty list could only be read again by closing the
                // app; 다시 불러오기 asks again (a list the server just filled shows within its minute of cache).
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(if (state.tab == RecipeListViewModel.Tab.SHOWCASE || state.tab == RecipeListViewModel.Tab.OFFICIAL || state.tab == RecipeListViewModel.Tab.DATA)
                        "아직 준비된 레시피가 없어요" else "아직 레시피가 없어요. 내 설정을 처음으로 나눠 보세요",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TextButton(onClick = { vm.load() }) { Text("다시 불러오기") }
                }
            }
            // [HUB-04] Themes are picked by eye: two big pictures a row; the rest as rows.
            if (state.kind == RecipeClient.SCOPE_THEME) items(state.items.chunked(2), key = { row -> row.first().code }) { row ->
                Row(Modifier.widthIn(max = 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { item -> ThemeTile(item, Modifier.weight(1f)) { onOpen(item.code) } }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            } else items(state.items, key = { it.code }) { item -> RecipeRow(item, Modifier.widthIn(max = 640.dp)) { onOpen(item.code) } }
            if (state.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            else if (state.next != null) item { TextButton(onClick = { vm.load(more = true) }) { Text("더 보기") } }
        }
    }
    if (codeAsk) CodeDialog(onCode = { codeAsk = false; onOpen(it) }, onDismiss = { codeAsk = false })
}

@Composable
fun RecipeRow(item: RecipeItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.fillMaxWidth().clickable(onClickLabel = "레시피 보기", onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RecipeCover(item.cover)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    // [R136-17] 1.36 (리뷰 R-12): 보여주기 is the operator's word; people read 추천.
                    if (item.showcase) Badge("추천")
                    else if (item.official) Badge(if (item.publicData) "공개 자료" else "시작")
                }
                if (item.summary.isNotEmpty()) Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [MKX-01] One tab's: which tab it changes, said once.
                if (item.scope == "tab") item.tabs.firstOrNull()?.let { t ->
                    Text("🧩 ${marketTabLabel(t, null)} 탭만 바꿔요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                Text(buildString {
                    append("좋아요 ${item.likes} · 안좋아요 ${item.dislikes} · 쓰는 분 ${item.users}")
                    item.kept?.let { append(" · 7일 유지 $it%") }
                    append(" · ").append(RecipeDescribe.styles(com.bamnat.core.recipe.Recipe(item.title, styles = item.styles, pieces = emptyList())))
                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // [FREE-08] The theme itself, to pick by eye.
            item.look?.let { Spacer(Modifier.width(8.dp)); ThemeThumb(it, width = 84.dp) }
        }
    }
}

/** [HUB-04] One theme in 장터's grid: the theme drawn large, its name and how many like and wear it. */
@Composable
private fun ThemeTile(item: RecipeItem, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(modifier.clickable(onClickLabel = "테마 보기", onClick = onClick)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
                item.look?.let { ThemeThumb(it, width = maxWidth) } ?: RecipeCover(item.cover, size = maxWidth)
            }
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("♥ ${item.likes} · 입은 분 ${item.users}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp))
}

/** [MKX-01] One of 장터's two halves, big enough to read at a glance. */
@Composable
private fun MarketHalf(title: String, sub: String, emoji: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Surface(onClick = onClick, color = bg, shape = RoundedCornerShape(16.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.heightIn(min = 72.dp).semantics { this.selected = selected }) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            Spacer(Modifier.width(8.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium, color = fg, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
                Text(sub, style = MaterialTheme.typography.bodySmall, color = fg)
            }
        }
    }
}

/** [MKX-01] A 기능별 탭's name in this kind of work's words (근무·급여, 장부 …). */
internal fun marketTabLabel(tab: String, style: com.bamnat.core.work.WorkStyle?): String = when (tab) {
    "calendar" -> "달력"
    "pay" -> com.bamnat.app.ui.TopLevel.STATS.labelFor(style)
    "money" -> com.bamnat.app.ui.TopLevel.MONEY.labelFor(style)
    "memo" -> "메모"
    "alarm" -> "알람"
    "widget" -> "위젯"
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
