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
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Search
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
                            Text("좋아요 ${item.likes} · 넣은 분 ${item.users}", style = MaterialTheme.typography.labelSmall,
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

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
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
    val cols = if (!small) 2 else if (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 600) 4 else 3
    // Back from trying one on the real app: 넘겨 보기 again at that work, or (입기·넣기 on the band) that work's 입어 보기 to put it in.
    val again by LiveTryOn.reopen.collectAsStateWithLifecycle()
    androidx.compose.runtime.LaunchedEffect(again) {
        LiveTryOn.take { it is LiveTryOn.From.Flip }?.let { back ->
            val f = back.from as LiveTryOn.From.Flip
            if (back.wear) { com.bamnat.app.recipe.RecipeLinks.tryNext = f.code; flipAt = null; onOpen(f.code) } else flipAt = f.at
        }
    }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    // 사용자 2026-11-03 (마을 › 장터 스크롤): in 마을 the tab row already says 장터 and whose; the list starts right under it.
    val hubHeader = if (onBack == null) com.bamnat.app.ui.hub.LocalHubHeader.current else null
    val reselect = com.bamnat.app.ui.hub.LocalHubReselect.current
    // [TOWN-11] 장터 tapped again (or opened from a 소식) also reads the list again, so a work just put up shows.
    val reselectAt = remember { reselect }
    androidx.compose.runtime.LaunchedEffect(reselect) { if (reselect > reselectAt) { vm.load(); listState.animateScrollToItem(0) } }
    // The 구간 and 정렬 chips stay at the top once the cards above them are gone, so another 구간 is one tap from anywhere in the list.
    val stickyAt = if (hubHeader != null) 2 else 1
    val pinned by remember(stickyAt) { derivedStateOf { listState.firstVisibleItemIndex >= stickyAt } }
    // A 구간 or 정렬 changed while scrolled down starts its list right under the chips (not somewhere in the middle).
    // PC 확인 a6cab3c8 (장터를 내린 채 다른 탭에 갔다 오면 맨 위로 돌아가요): only a real change of 구간·정렬 moves the list; coming back
    // to the screen (the same chips, read back as it was left) keeps where it was.
    val chipsKey = "${state.kind}|${state.tab}|${state.allStyles}"
    var chipsSeen by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(chipsKey) }
    androidx.compose.runtime.LaunchedEffect(chipsKey) {
        if (chipsKey == chipsSeen) return@LaunchedEffect
        chipsSeen = chipsKey
        if (listState.firstVisibleItemIndex > stickyAt) listState.scrollToItem(stickyAt)
    }
    Scaffold(
        topBar = {
            if (onBack != null) TopAppBar(
                title = { Text(title + (vm.style?.let { " · " + RecipeDescribe.short(it) } ?: "")) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = {
                    // [R136-13] In 마을 these live in 공방 (내 것 한곳, 코드로 받기 하나); a 장터 opened on its own keeps them.
                    if (onBack != null) {
                        TextButton(onClick = { codeAsk = true }) { Text("코드로 받기") }
                        TextButton(onClick = onMine) { Text("내 레시피") }
                    }
                },
            )
        },
        floatingActionButton = {
            // PC 확인 62a86294 스크롤 점검 (처음 화면에서 '스튜디오에서 만들기'가 첫 카드를 가리고, 내리면 '^'·'+'가 쌓여 글자를 가림):
            // at the top the button is a row of the list (under the 구간 cards, never on a work); the small round + comes only once
            // that row has scrolled away. Going back up is the 장터 tab pressed again, or the 구간 chips that stay at the top.
            val moved by remember { derivedStateOf { listState.firstVisibleItemIndex > stickyAt } }
            if (moved) androidx.compose.material3.SmallFloatingActionButton(onClick = onMake,
                modifier = Modifier.semantics { contentDescription = "스튜디오에서 만들기" }) { Icon(Icons.Filled.Add, null) }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            hubHeader?.let { h -> item(key = "hub") { Box(Modifier.widthIn(max = 640.dp).fillMaxWidth()) { h() } } }
            item(key = "top") {
                Column(Modifier.widthIn(max = 640.dp).padding(top = if (onBack == null) 4.dp else 0.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(state.query, vm::query, singleLine = true, modifier = Modifier.fillMaxWidth(),
                        label = { Text("이름·설명·초성으로 찾기") }, leadingIcon = { Icon(Icons.Filled.Search, null) }, colors = com.bamnat.app.ui.theme.PhotoGround.fieldColors(),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.load() }))
                    // PC 확인 1.36 (코드로 찾으면 어느 구간에도 안 나옴): a code typed in the box opens that work, whatever its 구간.
                    com.bamnat.app.recipe.RecipeLinks.find(state.query.takeIf { it.trim().length in 8..80 })?.let { code ->
                        FilledTonalButton(onClick = { onOpen(code) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("코드 $code 작품 열기") }
                    }
                    // [MKX-01] 1.36 두 구간 (사용자 2026-10-31 "장터가 너무 보기 어렵게 … 테마를 주는건지 기능별로 보여지는건지 구간을 나눠서"):
                    // 테마 (모든 것을 종합해 만든 한 벌) and 기능별 탭 (탭 하나를 전문으로, 고른 테마 위에 얹어요), two big halves on top.
                    // [RUL-07] PC D4 (사용성 제안 높): 맨 위를 테마 · 기능별 탭 · 규칙·단추 세 갈래로 (규칙·단추 칩이 탭 줄 끝에 숨어 있었어요).
                    // 1.39 [MKC-01] 장터 분류 다시 (사용자 2026-11-07 "테마는 말그대로 모든 것을 꾸민 모습이고 각 탭별로 기능이나 디자인 한
                    // 제작물로 분류를 나눈다. 도구 또한 분류를 나눠서"): 테마 · 탭별 작품 (꾸미기·기능) · 도구 (부품·자동 규칙·단추).
                    val tools = state.kind.isTool()
                    val featured = state.kind?.startsWith(RecipeClient.SCOPE_TAB) == true
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        MarketHalf("테마", "앱 전체를 한 벌로", "🎨", selected = !featured && !tools, Modifier.weight(1f)) { vm.kind(RecipeClient.SCOPE_THEME) }
                        MarketHalf("탭별 작품", "탭 하나를 위해", "🗂", selected = featured, Modifier.weight(1f)) {
                            if (!featured) vm.kind(RecipeClient.SCOPE_TAB)
                        }
                        MarketHalf("도구", "하나만 더하기", "🧰", selected = tools, Modifier.weight(1f)) { if (!tools) vm.kind(RecipeClient.SCOPE_TOOL) }
                    }
                    Text(when {
                        tools -> "입은 테마나 기본 모양은 그대로 두고 부품 하나, 규칙 하나, 단추 하나만 더해요. 눌러서 전후를 직접 해 볼 수 있어요"
                        featured -> "탭 하나를 위해 만든 꾸미기(모양)와 기능(틀·묶음)이에요. 지금 입은 테마는 그대로 두고 그 탭만 바꿔요"
                        else -> "달력·가계부·메모·급여까지 모든 화면을 한 벌로 꾸민 테마예요. 입은 뒤 마음에 안 드는 탭은 탭별 작품으로 바꿀 수 있어요"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    androidx.compose.material3.OutlinedButton(onClick = onMake, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("스튜디오에서 만들기")
                    }
                }
            }
            // 7차 통합 347b7c37 PC 제안 (칩 줄 판이 화면보다 좁아 좌우로 사진이 보여요): the pinned row's ground runs past the list's 16dp side
            // padding to the screen's edges; its chips stay in the 640dp column.
            stickyHeader(key = "chips") { Column(Modifier.fillMaxWidth().layout { m, c ->
                val side = 16.dp.roundToPx()
                val p = m.measure(c.copy(minWidth = c.maxWidth + 2 * side, maxWidth = c.maxWidth + 2 * side))
                layout(c.maxWidth, p.height) { p.place(-side, 0) }
            }) {
                val tools = state.kind.isTool()
                val featured = state.kind?.startsWith(RecipeClient.SCOPE_TAB) == true
                androidx.compose.material3.Surface(color = com.bamnat.app.ui.theme.PhotoGround.solid(), modifier = Modifier.fillMaxWidth()) {
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Pinned: the three 구간 as one small row, since their big cards have scrolled away.
                    val tabChips: @Composable () -> Unit = {
                        val now = RecipeClient.tabOf(state.kind)
                        val made = RecipeClient.madeOf(state.kind)
                        val price = RecipeClient.price(state.kind)
                        FilterChip(selected = now.isEmpty(), onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind("", made), price)) }, label = { Text("모든 탭") })
                        // PC 확인 5c21f3bb 장터 구간 세기: a tab that kind of work has no works for (자영업·투자의 근무·급여, 알람 조각을 받는
                        // 교대·일근·알바 밖의 알람) is not offered; their 장 시작·영업 준비 알림 are in 도구.
                        RecipeClient.MARKET_TABS.filter { t -> marketTabFits(t, vm.style) }.forEach { t ->
                            FilterChip(selected = now == t, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.tabKind(t, made), price)) }, label = { Text(marketTabLabel(t, vm.style)) })
                        }
                    }
                    // 1.39 [MKC-01] 탭별 작품: 꾸미기(모양)·기능(틀·묶음·보기 방식). Both kinds in one work show under either.
                    // 1.39 [MKC-04] (사용자 2026-11-07 "앱코드 + 스튜디오 + 장터 세 가지가 맞아떨어져야", 조사 18장 C-7): '✦ 프로 기능'이
                    // 전체 바로 다음, 맨 앞에 (새 기능 장치를 쓴 작품만).
                    val madeChips: @Composable () -> Unit = {
                        val tab = RecipeClient.tabOf(state.kind)
                        val made = RecipeClient.madeOf(state.kind)
                        val fx = RecipeClient.fxOnly(state.kind)
                        val free = RecipeClient.freeOnly(state.kind)
                        FilterChip(selected = made == null && !fx && !free, onClick = { vm.kind(RecipeClient.tabKind(tab, null)) }, label = { Text("전체") })
                        FxChip(fx) { vm.kind(RecipeClient.tabKind(tab, null, fx = !fx)) }
                        FreeChip(free) { vm.kind(RecipeClient.tabKind(tab, null, free = !free)) }
                        listOf("look" to "🎨 꾸미기", "func" to "⚙️ 기능").forEach { (m, label) ->
                            FilterChip(selected = made == m, onClick = { vm.kind(RecipeClient.tabKind(tab, m)) }, label = { Text(label) })
                        }
                    }
                    // 1.39 [MKC-01] 도구: 종류 (부품·자동 규칙·단추), then the screen it works on (a row of its own when not pinned,
                    // so neither hides at the right edge).
                    val toolChips: @Composable () -> Unit = {
                        val tool = RecipeClient.toolOf(state.kind)
                        val on = RecipeClient.toolOn(state.kind)
                        val fx = RecipeClient.fxOnly(state.kind)
                        val free = RecipeClient.freeOnly(state.kind)
                        FilterChip(selected = tool.isEmpty() && !fx && !free, onClick = { vm.kind(RecipeClient.toolKind("", on)) }, label = { Text("전체") })
                        FxChip(fx) { vm.kind(RecipeClient.toolKind("", on, fx = !fx)) }
                        FreeChip(free) { vm.kind(RecipeClient.toolKind("", on, free = !free)) }
                        RecipeClient.TOOL_KINDS.forEach { t ->
                            FilterChip(selected = tool == t, onClick = { vm.kind(RecipeClient.toolKind(t, on)) }, label = { Text(toolLabel(t)) })
                        }
                    }
                    val screenChips: @Composable () -> Unit = {
                        val tool = RecipeClient.toolOf(state.kind)
                        val on = RecipeClient.toolOn(state.kind)
                        val price = RecipeClient.price(state.kind)
                        FilterChip(selected = on == null, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind(tool, null), price)) }, label = { Text("모든 화면") })
                        TOOL_SCREENS.filter { t -> marketTabFits(t, vm.style) }.forEach { t ->
                            FilterChip(selected = on == t, onClick = { vm.kind(RecipeClient.withPrice(RecipeClient.toolKind(tool, t), price)) }, label = { Text(marketTabLabel(t, vm.style).removeSuffix(" 화면·규칙")) })
                        }
                    }
                    // Pinned: the three 구간 as one small row, since their big cards have scrolled away. PC c6a6e361 제안 (기능별 탭은
                    // 붙는 줄이 세 줄이라 목록이 좁음): pinned, 기능별 탭's tab chips go on that same row after the 구간, so two rows stay on top.
                    // PC 4277abca (붙는 순간 한 장에서 구간·탭 줄이 빠짐): no grow-in animation; the 구간 row comes in the same frame the
                    // tab row below goes, so the header never draws without both.
                    if (pinned) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            FilterChip(selected = !featured && !tools, onClick = { vm.kind(RecipeClient.SCOPE_THEME) }, label = { Text("🎨 테마") })
                            FilterChip(selected = featured, onClick = { if (!featured) vm.kind(RecipeClient.SCOPE_TAB) }, label = { Text("🗂 탭별 작품") })
                            FilterChip(selected = tools, onClick = { if (!tools) vm.kind(RecipeClient.SCOPE_TOOL) }, label = { Text("🧰 도구") })
                            if (featured) {
                                androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                                tabChips()
                                androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                                madeChips()
                            }
                            if (tools) {
                                androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                                toolChips()
                                androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 2.dp))
                                screenChips()
                            }
                        }
                    }
                    // PC D10 (사용성 중): 찾은 말이 다른 구간에 있으면 칩으로 (테마 안에서만 찾아 '체크'가 안 나오던 것).
                    if (featured && !pinned) {
                        ChipRows { tabChips() }
                        ChipRows { madeChips() }
                    }
                    if (tools && !pinned) {
                        ChipRows { toolChips() }
                        ChipRows { screenChips() }
                    }
                    // 1.39 [MKD-45] 테마 분위기: 밝은 · 어두운 · 파스텔 · 선명한 · 차분한 (다시 누르면 풀려요), 테마 구간에서 붙지 않은 머리에만.
                    if (!featured && !tools && !pinned) ChipRows {
                        com.bamnat.app.recipe.MarketMood.Mood.entries.forEach { m ->
                            FilterChip(selected = moodNow == m, onClick = { mood = if (moodNow == m) null else m.name }, label = { Text(m.label) })
                        }
                    }
                    // How sorted and whose, under the half chosen. 1.39 [MKD-02] (CLAUDE.md 7항): 좁은 폰에서 오른쪽 칩이 잘려 있는 줄 모르니 줄을 감싸요.
                    ChipRows {
                        // 1.39 [MKD-03] '값 칸'·'위젯'·'프로'처럼 장치·종류 이름으로 찾으면 그 칩으로 가는 칩을 맨 앞에.
                        RecipeClient.wordKind(state.query, state.kind)?.let { (k, name) ->
                            androidx.compose.material3.AssistChip(onClick = { vm.query(""); vm.kind(k) }, label = { Text("$name 보기") })
                        }
                        // PC D14 (사용성 낮): 다른 칸 칩은 이 줄 맨 앞에 (따로 줄을 두면 칸을 누를 때·첫 글자에 아래가 두 번 내려갔어요).
                        if (state.query.isNotBlank()) state.elsewhere.forEach { (h, n) ->
                            val here = h == vm.halfNow()
                            androidx.compose.material3.AssistChip(onClick = { vm.jump(h) },
                                label = { Text(if (here) "‘새로’에 $n" else "${RecipeListViewModel.halfName(h)}에 $n") },
                                modifier = Modifier.semantics { contentDescription = "다른 칸에도 있어요: ${if (here) "새로" else RecipeListViewModel.halfName(h)}에 $n" })
                        }
                        if (state.query.isNotBlank() && state.elsewhere.isNotEmpty())
                            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                        // 1.39 [MKC-04] 테마에는 칩 줄이 따로 없어 '✦ 프로 기능'을 이 줄 맨 앞에.
                        if (!featured && !tools) {
                            val fx = RecipeClient.fxOnly(state.kind)
                            val free = RecipeClient.freeOnly(state.kind)
                            FxChip(fx) { vm.kind(RecipeClient.withFx(RecipeClient.SCOPE_THEME, !fx)) }
                            FreeChip(free) { vm.kind(RecipeClient.withFree(RecipeClient.SCOPE_THEME, !free)) }
                            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                        }
                        RecipeListViewModel.Tab.entries.forEach { t -> FilterChip(selected = state.tab == t, onClick = { vm.tab(t) }, label = { Text(t.label) }) }
                        if (vm.style != null) {
                            androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                            FilterChip(selected = state.allStyles, onClick = { vm.allStyles(!state.allStyles) }, label = { Text("다른 유형도") })
                        }
                        // 1.39 [MKF-03] 작게 보기 (한 화면에 더 많이), 이 폰에 기억해요.
                        androidx.compose.material3.VerticalDivider(Modifier.height(24.dp).padding(horizontal = 4.dp))
                        FilterChip(selected = small, onClick = {
                            small = !small
                            runCatching { ctx.getSharedPreferences(VIEW_PREFS, 0).edit().putBoolean(VIEW_SMALL, small).apply() }
                        }, label = { Text("작게 보기") })
                    }
                }
                }
                }
                if (pinned) androidx.compose.material3.HorizontalDivider()
            } }
            if (state.tab == RecipeListViewModel.Tab.GOOD || state.tab == RecipeListViewModel.Tab.SHOWCASE || state.tab == RecipeListViewModel.Tab.DATA) item(key = "notes") {
                Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (state.tab == RecipeListViewModel.Tab.GOOD) Text("좋아요가 5개 넘은 레시피만 여기 나와요. 표가 적으면 ‘새로’에서 볼 수 있어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.tab == RecipeListViewModel.Tab.SHOWCASE) Text("장터 레시피로 이만큼 꾸밀 수 있다는 걸 보여 주려고 운영자가 만든 판이에요. 미리 보고 마음에 드는 것만 넣어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.tab == RecipeListViewModel.Tab.DATA) Text("공공 자료를 바탕으로 운영자가 만든 레시피예요. 출처가 함께 적혀 있어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // 1.38 (장터 그림 LOW, PC 제안): offline with a list already shown, one quiet line says when it was read, with 다시 시도;
            // with nothing shown, the problem and 다시 시도 (before, a red line and no way to try again but leaving).
            state.error?.let { e -> item(key = "error") {
                if (state.items.isNotEmpty() && state.loadedAt > 0L) com.bamnat.app.ui.company.RefreshLine(refreshing = false, stale = true,
                    onRetry = { vm.load() }, seenAt = java.time.Instant.ofEpochMilli(state.loadedAt).atZone(com.bamnat.core.time.Today.zone())
                        .toLocalTime().let { "%d:%02d".format(it.hour, it.minute) })
                else Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 8.dp)) {
                    Text(e, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f))
                    TextButton(onClick = { vm.load() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 시도") }
                }
            } }
            if (!state.loading && state.error == null && state.items.isEmpty()) item {
                // [R136-05] 1.36 (리뷰 R-11, 관리자 개발사항 d8quAdDH7r3QKNMp): an empty list could only be read again by closing the
                // app; 다시 불러오기 asks again (a list the server just filled shows within its minute of cache).
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // 1.39 [MKC-04] '✦ 프로 기능'만 골랐는데 없으면: 무엇이 없는지와 모든 작품으로 돌아가는 길, 처음 만들 곳.
                    if (RecipeClient.price(state.kind).isNotEmpty() && state.query.isBlank()) {
                        Text(if (RecipeClient.fxOnly(state.kind)) "이 칸에는 ✦ 프로 기능 작품이 아직 없어요. 값 칸·단추 위젯 같은 새 기능 장치로 처음 만들어 보세요"
                            else "이 칸에는 무료로 다 되는 작품이 아직 없어요", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row {
                            TextButton(onClick = { vm.kind(RecipeClient.plain(state.kind)) }) { Text("모든 작품 보기") }
                            TextButton(onClick = onMake) { Text("스튜디오에서 만들기") }
                        }
                        return@Column
                    }
                    // PC 제안 1.36 (기능별 탭 › 달력이 비면 무엇이 없는지 말이 없음): that tab's name, and the way to make the first.
                    val tabNow = state.kind?.takeIf { it.startsWith(RecipeClient.SCOPE_TAB) }?.let { RecipeClient.tabOf(it) }?.takeIf { it.isNotEmpty() }
                    if (tabNow != null && state.query.isBlank()) {
                        Text("${marketTabLabel(tabNow, vm.style)} 탭 ${madeLabel(RecipeClient.madeOf(state.kind))?.let { "$it " }.orEmpty()}작품이 아직 없어요", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row { TextButton(onClick = onMake) { Text("스튜디오에서 만들기") }; TextButton(onClick = { vm.load() }) { Text("다시 불러오기") } }
                        return@Column
                    }
                    // PC 제안 1.36 (찾기 결과가 없는데 '처음으로 나눠 보세요'): what was looked for, and the other 구간 to look in.
                    // PC 제안 1.36: a code typed has its own 작품 열기 above; no 없어요 under it.
                    if (com.bamnat.app.recipe.RecipeLinks.find(state.query.takeIf { it.trim().length in 8..80 }) != null) return@Column
                    if (state.query.isNotBlank()) {
                        Text("‘${state.query.trim()}’에 맞는 작품이 ${if (state.kind == RecipeClient.SCOPE_THEME) "테마에" else "이 구간에"} 없어요" + if (state.elsewhere.isNotEmpty()) ". 위 칩으로 다른 칸을 봐요" else "",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { vm.kind(if (state.kind == RecipeClient.SCOPE_THEME) RecipeClient.SCOPE_TAB else RecipeClient.SCOPE_THEME) }) {
                            Text(if (state.kind == RecipeClient.SCOPE_THEME) "탭별 작품에서 찾기" else "테마에서 찾기")
                        }
                        return@Column
                    }
                    Text(if (state.tab == RecipeListViewModel.Tab.SHOWCASE || state.tab == RecipeListViewModel.Tab.OFFICIAL || state.tab == RecipeListViewModel.Tab.DATA)
                        "아직 준비된 레시피가 없어요" else "아직 레시피가 없어요. 내 설정을 처음으로 나눠 보세요",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    // PC 확인 38d63451: the other half's, and a way to make the first one.
                    val inTheme = state.kind == RecipeClient.SCOPE_THEME
                    if (state.other > 0) Text(if (inTheme) "탭별 작품에는 ${state.other}개 있어요. 달력 같은 탭 하나만 바꾸는 작품이에요"
                        else "테마에는 ${state.other}개 있어요. 앱 전체를 한 벌로 바꾸는 작품이에요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                        if (state.other > 0) androidx.compose.material3.FilledTonalButton(onClick = {
                            vm.kind(if (inTheme) RecipeClient.SCOPE_TAB else RecipeClient.SCOPE_THEME)
                        }) { Text(if (inTheme) "탭별 작품 보기" else "테마 보기") }
                        TextButton(onClick = onMake) { Text("스튜디오에서 만들기") }
                        TextButton(onClick = { vm.load() }) { Text("다시 불러오기") }
                    }
                }
            }
            // [HUB-04] Themes are picked by eye: two big pictures a row. [MKV-01] 1.37 (사용자 2026-11-05 "실제로 해당 테마나 기능들의 모습을"):
            // 탭별 작품 and 도구 too, each drawn as it really looks, so every 구간 is picked by eye the same way.
            // 1.39 [MKF-03] 작게 보기: 50개씩 들어와도 한 화면에 더 많이 (폰 3열, 폴드 펼침 4열).
            if (moodNow != null && shown.isEmpty() && state.items.isNotEmpty()) item {
                Text("불러온 테마 가운데 ‘${moodNow.label}’ 테마가 아직 없어요" + (if (state.next != null) ". 아래 ‘더 보기’로 더 불러와요" else ""),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
            }
            items(shown.chunked(cols), key = { row -> row.first().code + "|" + cols }) { row ->
                Row(Modifier.widthIn(max = if (small) 900.dp else 640.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if (small) 6.dp else 10.dp)) {
                    row.forEach { item ->
                        ThemeTile(item, vm.style, Modifier.weight(1f), small = small, onPicture = { flipAt = shown.indexOf(item).coerceAtLeast(0) }) { onOpen(item.code) }
                    }
                    repeat(cols - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (state.loading) item { CircularProgressIndicator(Modifier.padding(16.dp)) }
            else if (state.next != null) item { TextButton(onClick = { vm.load(more = true) }) { Text("더 보기") } }
        }
    }
    if (codeAsk) CodeDialog(onCode = { codeAsk = false; onOpen(it) }, onDismiss = { codeAsk = false })
    flipAt?.let { at ->
        MarketFlip(shown, at, vm.style, onDismiss = { flipAt = null }, onOpen = { code -> flipAt = null; onOpen(code) }, onAt = { flipAt = it },
            onTry = { item ->
                flipScope.launch {
                    val r = MarketShots.recipe(c, item)
                    val looks = r?.let { com.bamnat.core.recipe.LookParts.of(it).map { p -> p.id }.toSet() }.orEmpty()
                    // 도구 (규칙·단추·부품) are tried 전후 in their page; a work that cannot be laid on as it is goes to its 입어 보기.
                    if (r == null || looks.isEmpty() || item.scope == "logic" || item.scope == "part") { flipAt = null; onOpen(item.code); return@launch }
                    val tab = MarketShots.tabOf(MarketShots.faceOf(item, r))
                    val at = shown.indexOfFirst { it.code == item.code }.coerceAtLeast(0)
                    val ok = runCatching { LiveTryOn.start(c, r, own = false, looks, tab, LiveTryOn.From.Flip(item.code, at)) }.getOrDefault(false)
                    if (ok) flipAt = null else { com.bamnat.app.recipe.RecipeLinks.tryNext = item.code; flipAt = null; onOpen(item.code) }
                }
            })
    }
}

/** 1.39 [MKD-02] 장터 칩 줄: 한 줄에 다 안 들어가면 다음 줄로 감싸요(좌우로 밀어야 보이는 칩이 없게). 붙은 머리는 지금처럼 한 줄이에요. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun ChipRows(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)) { content() }
}

private const val VIEW_PREFS = "market_view"
private const val VIEW_SMALL = "small"

@Composable
fun RecipeRow(item: RecipeItem, modifier: Modifier = Modifier,
    /** 1.36 PC 시험: a one-tab recipe names that tab in the viewer's words (알바 '출근·시급', not '근무·급여'). */
    style: com.bamnat.core.work.WorkStyle? = androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.current.workStyle,
    onClick: () -> Unit) {
    val fxLocked = item.fx.isNotEmpty() && !com.bamnat.app.logic.FxAccess.pro(androidx.compose.ui.platform.LocalContext.current.appContainer)
    Card(modifier.fillMaxWidth().clickable(onClickLabel = "레시피 보기", onClick = onClick)) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            // [MKV-01] 1.37: the work as it really looks, in place of its cover and small swatch.
            MarketShot(item, 64.dp, style, onClick)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    // [R136-17] 1.36 (리뷰 R-12): 보여주기 is the operator's word; people read 추천.
                    if (item.showcase) Badge("추천")
                    else if (item.official) Badge(if (item.publicData) "공개 자료" else "시작")
                    if (item.fx.isNotEmpty()) FxBadge(locked = fxLocked)
                }
                if (item.summary.isNotEmpty()) Text(item.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // [MKX-01] One tab's: which tab it changes, said once. 1.39 [MKC-01]: and whether it is 꾸미기 or 기능; a 도구 says which.
                sectionLine(item, style, long = true)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary) }
                // 1.39 [MKC-04] 무엇이 프로 기능 장치인지 한 줄 ("✦ 값 칸 · 단추 위젯·빠른 설정·바로가기").
                fxLine(item)?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.tertiary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                Text(buildString {
                    append("좋아요 ${item.likes} · 안좋아요 ${item.dislikes} · 넣은 분 ${item.users}")
                    item.kept?.let { append(" · 7일 유지 $it%") }
                    append(" · ").append(RecipeDescribe.styles(com.bamnat.core.recipe.Recipe(item.title, styles = item.styles, pieces = emptyList())))
                }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    Card(modifier.clickable(onClickLabel = when (item.scope) { "tab" -> "작품 보기"; "part", "logic" -> "도구 보기"; else -> "테마 보기" }, onClick = onClick)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                // 1.39 [MKF-01] The picture opens 크게 넘겨 보기 (the name and lines under it, the work's page).
                MarketShot(item, maxWidth, style, onPicture ?: onClick)
                // 1.39 [MKD-01] 프로 표는 그림 위 모서리에 (이름 자리를 뺏지 않게).
                if (item.fx.isNotEmpty()) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { FxBadge(locked = fxLocked) }
            }
            // PC 확인 e50bb622: 추천 by the name, not over the picture's own title.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (item.showcase) Badge("추천")
            }
            sectionLine(item, style, long = false)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            // 1.39 [MKC-04] 프로 기능 장치가 무엇인지 (값 칸 · 알림 단추 …).
            if (!small) fxLine(item)?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            // 1.36 [VIL-18] 오래 쓰는 분이 많은 것과 새 창작자의 첫 작품이 눈에 띄게.
            Text("♥ ${item.likes} · 넣은 분 ${item.users}" + (item.kept?.takeIf { it >= 60 }?.let { " · 오래 써요 $it%" } ?: ""),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // 1.39 [MKD-41] 누가 만들었는지 (표식 · 팔로워). 표식이 있으면 이름 색으로도.
            if (!small) Text(MarketMaker.line(item), style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MarketMaker.mark(item.mark)?.let { com.bamnat.app.ui.village.markColor(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant)
            if (item.newMaker) Text("새 창작자", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun Badge(text: String) {
    Spacer(Modifier.width(6.dp))
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(MaterialTheme.colorScheme.primary).padding(horizontal = 6.dp, vertical = 1.dp))
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

/** [MKX-01] One of 장터's two halves, big enough to read at a glance. */
@Composable
private fun MarketHalf(title: String, sub: String, emoji: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    androidx.compose.material3.Surface(onClick = onClick, color = bg, shape = RoundedCornerShape(16.dp),
        border = if (selected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = modifier.heightIn(min = 72.dp).semantics { this.selected = selected }) {
        // Three across a phone (테마 · 탭별 작품 · 도구): the picture over the words.
        Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(emoji, fontSize = 22.sp)
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, maxLines = 1)
            Text(sub, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 2, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
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
    item.fx.mapNotNull(com.bamnat.core.formula.FxPro.Part::of).takeIf { it.isNotEmpty() }?.let { "✦ " + com.bamnat.core.formula.FxPro.say(it) }

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
