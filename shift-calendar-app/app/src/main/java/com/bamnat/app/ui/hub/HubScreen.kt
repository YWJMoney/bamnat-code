package com.bamnat.app.ui.hub

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material.icons.filled.Search
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.recipe.thumb
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.work.WorkStyle

/**
 * [HUB-01] 1.35 마을 (사용자 2026-10-30 18:46 "나눔판을 이름을 변경하고 메인 메뉴로 … 광장을 흡수한 메인 탭"): one main tab where people
 * of the same kind of work talk (광장), pick and try themes, share and take recipes, and make their own in the studio. It was three
 * places (설정 › 화면 꾸미기, 광장, 광장 › 나눔 판) that looked like different features.
 */
enum class HubSection(val label: String, val line: String) {
    TALK("광장", "같은 일을 하는 분들과 이야기하고, 회사 규칙과 시세를 나눠요"),
    // 1.35 (사용자 2026-10-30 21:37 "장터를 2번째로 옮기고 테마를 3번째로"): 고르고 받기는 장터, 입고 간직하기는 테마, 만들기는 공방.
    SHARE("장터", "테마·알람·메모 틀·급여 규칙·화면 배치를 받아 쓰고 좋아요·댓글을 남겨요"),
    THEMES("테마", "내가 입은 테마와 간직한 테마예요. 기록은 그대로예요"),
    MAKE("공방", "스튜디오 하나에서 꾸미고, 기능까지 묶어 올려요. 내 것은 여기 한곳에"),
}

/** [VIL-07] Where 마을 remembers the part last seen (this phone only, not a setting). */
private const val HUB_PREFS = "village_ui"

/** [VIL-07] The part 마을 first opens on: 장터 for 자영업·투자, whose 광장 is a quiet talk; 광장 for the rest. */
fun firstSection(style: WorkStyle?): HubSection = when (style) {
    WorkStyle.BUSINESS, WorkStyle.INVEST -> HubSection.SHARE
    else -> HubSection.TALK
}

/** [HUB-01] The tab's name, kept in one place so the bottom bar, the guide and the search say the same. */
const val HUB_NAME = "마을"

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun HubScreen(
    c: AppContainer,
    style: WorkStyle?,
    talk: @Composable () -> Unit,
    /** 장터, opened on one kind ("skin" from 테마's 장터에서 새 테마 찾기) or on all. */
    share: @Composable (kind: String?) -> Unit,
    onMake: () -> Unit,
    onMine: () -> Unit,
    onRequests: () -> Unit,
    onCode: () -> Unit,
    /** 스튜디오, started from "mine", "plain" or "theme:<key>" ([com.bamnat.app.ui.studio.StudioViewModel]). */
    onStudio: (String) -> Unit,
    /** [PRF-02] 1.36 a profile from the 팔로잉 줄 (내 프로필 first), and 팔로잉 모두 보기. */
    onMaker: (String) -> Unit = {},
    onPeople: () -> Unit = {},
    /** [VIL-04] 오늘의 하나: one recipe's page. */
    onRecipe: (String) -> Unit = {},
    /** [VIL-13] 간직한 글 · 내 글. */
    onPosts: () -> Unit = {},
    /** [VIL-06] 마을에서 찾기. */
    onSearch: (() -> Unit)? = null,
    /** [VIL-17] 장터 넘겨 보기. */
    onSwipe: (() -> Unit)? = null,
) {
    // [VIL-07] 1.36 마을 V7: 마을 opens on the part last seen; the first time on the one that fits the kind of work (자영업·투자 have a quiet 광장).
    val prefs = remember { c.appContext.getSharedPreferences(HUB_PREFS, android.content.Context.MODE_PRIVATE) }
    var section by rememberSaveable { mutableStateOf(prefs.getString("section", null)?.let { n -> HubSection.entries.firstOrNull { it.name == n } } ?: firstSection(style)) }
    androidx.compose.runtime.LaunchedEffect(section) { prefs.edit().putString("section", section.name).apply() }
    var shareKind by rememberSaveable { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.windowInsetsPadding(WindowInsets.statusBars)) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp), verticalAlignment = Alignment.Bottom) {
                    Text(HUB_NAME, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                    Spacer(Modifier.padding(start = 8.dp))
                    style?.let { Text(RecipeDescribe.short(it) + " 분들과", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp)) }
                    // 1.36 [VIL-06] 마을 V6: one search for 장터·광장·요청, at the top of 마을.
                    onSearch?.let {
                        Spacer(Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = it) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Search, "마을에서 찾기")
                        }
                    }
                }
                PrimaryTabRow(selectedTabIndex = section.ordinal, containerColor = MaterialTheme.colorScheme.surface) {
                    HubSection.entries.forEach { s ->
                        Tab(selected = section == s, onClick = { if (s == HubSection.SHARE && section != s) shareKind = null; section = s }, text = { Text(s.label) })
                    }
                }
            }
        }
        // [PRF-02] 1.36 (사용자 2026-10-31 "팔로워 한 사람의 프로필에 들어가"): 팔로잉 줄 above 광장 and 장터, where people meet and pick.
        val recipesOn by c.recipes.on.collectAsStateWithLifecycle()
        // PC 확인 0efe9dbc: the 팔로잉 줄·오늘의 하나·넘겨 보기 fold away while the list is scrolled down and come back on scrolling up,
        // so 광장·장터's list has the whole screen; 찾기 stays in the top bar.
        var headerShown by remember { mutableStateOf(true) }
        androidx.compose.runtime.LaunchedEffect(section) { headerShown = true }
        val density = androidx.compose.ui.platform.LocalDensity.current
        val fold = remember(density) {
            val step = with(density) { 24.dp.toPx() }
            object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
                var run = 0f
                override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                    run = if ((run < 0) == (available.y < 0)) run + available.y else available.y
                    if (run < -step) headerShown = false else if (run > step) headerShown = true
                    return androidx.compose.ui.geometry.Offset.Zero
                }
            }
        }
        androidx.compose.animation.AnimatedVisibility(headerShown && recipesOn && c.recipeApi.enabled && (section == HubSection.TALK || section == HubSection.SHARE)) {
          Column {
            val strip = com.bamnat.app.ui.appViewModel(key = "following-strip-${style?.name}") { com.bamnat.app.ui.recipe.FollowingStripViewModel(it, style) }
            com.bamnat.app.ui.recipe.FollowingStrip(strip, onMaker = onMaker, onAll = onPeople)
            TodayOne(c, style, prefs, onRecipe, onMake = { onStudio("plain") })
            // 1.36 [VIL-17] 마을 V17: 장터's themes one by one, big, flipped upward.
            if (section == HubSection.SHARE && onSwipe != null) {
                TextButton(onClick = onSwipe, modifier = Modifier.padding(horizontal = 8.dp)) { Text("🃏 테마 넘겨 보며 고르기") }
            }
          }
        }
        Box(Modifier.weight(1f).fillMaxWidth().consumeWindowInsets(WindowInsets.statusBars)
            .nestedScroll(fold)) {
            when (section) {
                HubSection.TALK -> talk()
                HubSection.SHARE -> share(shareKind)
                HubSection.THEMES -> Themes(style, onLook = { onStudio("mine") }, onFind = { shareKind = "skin"; section = HubSection.SHARE },
                    onKept = { id -> onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_KEPT + id) })
                HubSection.MAKE -> Make(c, style, onStudio, onMake, onMine, onRequests, onCode, onPosts, onRecipe)
            }
        }
    }
}

/**
 * [VIL-04] 1.36 마을 V4 '오늘의 마을': under the 팔로잉 줄, one thing for today that fits the kind of work — this week's pick, or a
 * popular one — changing each day; '오늘은 그만' folds it until tomorrow. Nothing shows while there is nothing to show.
 */
@Composable
private fun TodayOne(c: AppContainer, style: WorkStyle?, prefs: android.content.SharedPreferences, onRecipe: (String) -> Unit, onMake: () -> Unit = {}) {
    val today = com.bamnat.core.time.Today.date().toString()
    var folded by remember { mutableStateOf(prefs.getString("todayFolded", null) == today) }
    if (folded) return
    var looked by remember(style, today) { mutableStateOf(false) }
    val item by androidx.compose.runtime.produceState<com.bamnat.app.recipe.RecipeItem?>(null, style, today) {
        val pool = runCatching { c.recipeApi.picks(style) }.getOrDefault(emptyList())
            .ifEmpty { runCatching { c.recipeApi.list(style, "hot").items.take(5) }.getOrDefault(emptyList()) }
            // PC 확인 V40: only what is made for this kind of work (a 교대 theme is no 오늘의 하나 for 알바).
            .filter { !c.recipes.hides(it) && (style == null || it.styles.isEmpty() || style in it.styles) }
        value = pool.takeIf { it.isNotEmpty() }?.let { it[(com.bamnat.core.time.Today.date().dayOfYear) % it.size] }
        looked = true
    }
    val r = item
    // PC 확인 V40: nothing for this kind of work yet: one line asking for its first, folded with 오늘은 그만 like the rest.
    if (r == null) {
        if (!looked || style == null) return
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("오늘의 하나", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text("아직 ${when (style) { WorkStyle.SHIFT -> "교대"; WorkStyle.DAY -> "일근"; WorkStyle.HOURLY -> "알바"; WorkStyle.FIELD -> "현장"; WorkStyle.BUSINESS -> "가게"; WorkStyle.INVEST -> "투자" }} 작품이 없어요. 첫 작품을 만들어 보세요", style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            }
            TextButton(onClick = onMake) { Text("만들기") }
            TextButton(onClick = { prefs.edit().putString("todayFolded", today).apply(); folded = true }) { Text("오늘은 그만") }
        }
        return
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp).heightIn(min = 48.dp)
        .clickable(onClickLabel = "오늘의 하나 보기") { onRecipe(r.code) }, verticalAlignment = Alignment.CenterVertically) {
        // PC 확인 V18: a small picture of it and 입어 보기, so it says what it is before a tap.
        // PC 확인: big enough to see the week, not an icon.
        r.thumb()?.let { com.bamnat.app.ui.recipe.ThemeThumb(it, width = 88.dp); Spacer(Modifier.padding(start = 8.dp)) }
        Column(Modifier.weight(1f)) {
            Text("오늘의 하나", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Text(r.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        TextButton(onClick = { com.bamnat.app.recipe.RecipeLinks.tryNext = r.code; onRecipe(r.code) }) { Text("입어 보기") }
        TextButton(onClick = { prefs.edit().putString("todayFolded", today).apply(); folded = true }) { Text("오늘은 그만") }
    }
}

@Composable
private fun Page(scroll: androidx.compose.foundation.ScrollState = rememberScrollState(), content: @Composable () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
            Spacer(Modifier.height(24.dp))
        }
    }
}

/** [HUB-02] 테마: the user's own page (지금 입은 테마, 내 테마, 입었던 테마); new themes are found in 장터, made in 공방. */
@Composable
private fun Themes(style: WorkStyle?, onLook: () -> Unit, onFind: () -> Unit, onKept: (Long) -> Unit) = Page {
    Text(HubSection.THEMES.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    com.bamnat.app.ui.recipe.ThemePicker(style, onFind = onFind, onFix = onLook, onUseKept = onKept)
}

/** [HUB-03] 공방: the studio, my recipes and what others wish for, in one place. */
/**
 * [HUB-03] 공방 (사용자 2026-10-30 21:39 "마을 탭 자체를 아주 좋게"): the studio first, large, with my look as it is now; my themes to go on
 * from in a row; then a recipe with 알람·메모 틀·규칙 too, my recipes, the wishes and a code.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
private fun Make(c: AppContainer, style: WorkStyle?, onStudio: (String) -> Unit, onMake: () -> Unit, onMine: () -> Unit, onRequests: () -> Unit,
                 onCode: () -> Unit, onPosts: () -> Unit, onRecipe: (String) -> Unit) {
    // PC 확인 V20: 공방 opens at its top, also when the 완성 카드 comes in after the page drew.
    val scroll = rememberScrollState()
    Page(scroll) { MakeBody(c, style, onStudio, onMake, onMine, onRequests, onCode, onPosts, onRecipe, onTop = { scroll.scrollTo(0) }) }
}

@Suppress("UNUSED_PARAMETER")
@Composable
private fun MakeBody(c: AppContainer, style: WorkStyle?, onStudio: (String) -> Unit, onMake: () -> Unit, onMine: () -> Unit, onRequests: () -> Unit,
                 onCode: () -> Unit, onPosts: () -> Unit, onRecipe: (String) -> Unit, onTop: suspend () -> Unit) {
    // 1.36 [VIL-15] PC 확인 V2·V17: my request someone finished shows first, at once (the 소식 comes only once a day), until opened.
    DoneRequests(c, style, onRecipe, onTop)
    Text(HubSection.MAKE.line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val kept by c.wardrobe.kept.collectAsStateWithLifecycle()
    val mine by androidx.compose.runtime.produceState<com.bamnat.core.recipe.Recipe?>(null, lease) {
        value = com.bamnat.core.recipe.Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
    }
    SoftCard {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            mine?.let { r ->
                Box(Modifier.clickable(onClickLabel = "스튜디오 열기") { onStudio("mine") }) {
                    com.bamnat.app.ui.recipe.ScaledPhone(r, style, com.bamnat.core.layout.TabOrder.CALENDAR, {}, 0.3f)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("스튜디오", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
                // [STU-21] 1.36 레시피 만들기 합침: one way in for every make.
                Text("달력 칸·색·배경·카드 배치·위젯에 알람 묶음·메모 틀·규칙까지 한곳에서 만들고, 입히기·저장·올리기도 여기서 해요",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.Button(onClick = { onStudio("mine") }, modifier = Modifier.heightIn(min = 48.dp)) { Text("지금 모양에서 꾸미기") }
                TextButton(onClick = { onStudio("plain") }) { Text("처음 모양에서 새로") }
            }
        }
    }
    // 1.36 [VIL-19] 이달의 주제 공모, under 스튜디오 where things are made.
    com.bamnat.app.ui.village.ContestCard(c, style, onRecipe)
    val themes = lease.history.sortedByDescending { it.own }.take(10)
    if (themes.isNotEmpty() || kept.isNotEmpty()) {
        Text("이어서 꾸미기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            themes.forEach { w ->
                androidx.compose.material3.AssistChip(onClick = { onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_THEME + w.key) },
                    label = { Text((if (w.own) "🎨 " else "") + w.title, maxLines = 1) })
            }
        }
        if (kept.isNotEmpty()) Text("보관함에 조각 ${kept.size}개가 있어요. 마을 › 테마나 스튜디오의 ‘보관함’에서 얹어 섞어요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    // [R136-13] 1.36 (리뷰 R-23·M6): 내 것 in one place — 올린 것·받은 것·만들던 것 — and 코드로 받기 only here.
    Card("내 것", "올린 레시피의 좋아요·댓글, 받은 레시피(7일 안 되돌리기), 만들던 판을 한곳에서 봐요", "열기", onMine)
    // 1.36 [VIL-13] 마을 V13: 간직한 글 · 내 글 · 내 답글 next to 내 것.
    Card("간직한 글 · 내 글", "광장에서 간직한 팁, 내가 쓴 글과 답글을 다시 봐요", "보기", onPosts)
    Card("이런 거 있었으면", "필요한 레시피를 부탁하거나, 다른 분의 부탁을 ‘만들게요’로 받아 만들어요", "요청 보기", onRequests)
    OutlinedButton(onClick = onCode, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("코드·링크로 받기") }
}

@Composable
private fun Card(title: String, text: String, action: String, onClick: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onClick, modifier = Modifier.heightIn(min = 48.dp)) { Text(action) }
        }
    }
}

/** 1.36 [VIL-15] My requests that came back 완성, not yet opened from here (device-only `village_ui`). */
@Composable
private fun DoneRequests(c: AppContainer, style: WorkStyle?, onRecipe: (String) -> Unit, onTop: suspend () -> Unit = {}) {
    val prefs = remember { c.appContext.getSharedPreferences(HUB_PREFS, android.content.Context.MODE_PRIVATE) }
    var seen by remember { mutableStateOf(runCatching { prefs.getStringSet("req_seen", emptySet())!!.toSet() }.getOrDefault(emptySet())) }
    val done by androidx.compose.runtime.produceState(emptyList<com.bamnat.app.recipe.RecipeRequest>(), style) {
        if (!c.recipeApi.enabled) return@produceState
        value = runCatching { c.recipeApi.requests(c.companyStore.device, style) }.getOrDefault(emptyList())
            .filter { it.mine && it.state == "done" && it.code != null }
    }
    val fresh = done.filter { it.code !in seen }
    val q = fresh.firstOrNull() ?: return
    androidx.compose.runtime.LaunchedEffect(q.code) { onTop() }
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("🎁 부탁한 레시피가 완성됐어요", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("‘${q.body.take(40)}’" + if (fresh.size > 1) " 외 ${fresh.size - 1}개" else "", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                androidx.compose.material3.Button(onClick = {
                    seen = seen + q.code!!
                    runCatching { prefs.edit().putStringSet("req_seen", seen).apply() }
                    onRecipe(q.code)
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("보러 가기") }
                TextButton(onClick = {
                    seen = seen + fresh.mapNotNull { it.code }
                    runCatching { prefs.edit().putStringSet("req_seen", seen).apply() }
                }, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
            }
        }
    }
}
