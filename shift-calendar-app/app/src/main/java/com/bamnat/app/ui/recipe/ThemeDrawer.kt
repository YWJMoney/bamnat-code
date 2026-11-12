package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.db.AppliedRecipeEntity
import com.bamnat.app.data.db.RecipeDraftEntity
import com.bamnat.app.recipe.Drawer
import com.bamnat.app.recipe.DrawerItem
import com.bamnat.app.recipe.DrawerKind
import com.bamnat.app.recipe.RecipeLinks
import com.bamnat.app.recipe.ThemeWardrobe
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.PutOnNotice
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.AppSheet
import com.bamnat.app.ui.components.Button
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.ThemeLease
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role

/** What one [DrawerItem] stands for, so its sheet knows what it can do. */
private sealed interface Held {
    /** [part]: only some of its parts are worn now (입어 보고 고른 것만 입기). */
    data class Theme(val w: ThemeLease.Wear, val now: Boolean = false, val part: Boolean = false) : Held
    data class Got(val e: AppliedRecipeEntity) : Held
    data class Part(val k: ThemeWardrobe.Kept) : Held
    data class Draft(val d: RecipeDraftEntity) : Held
    /** A tab laid on the theme worn (장터 › 기능별 탭). */
    data class Laid(val o: ThemeWardrobe.Overlay) : Held
}

private const val DAY = 86_400_000L

/**
 * [DRW-01][DRW-02] 1.37 마을 › 테마 = 내 서랍 (사용자 2026-11-04 "테마 탭은 개인 보관함", 계획 묶음 C): the theme worn now on top, then
 * everything of mine in one place — 내가 만든 것 (my themes, the studio drafts), 받은 것 (recipes put on from 장터), 입었던 것 and 담아 둔
 * 조각 — as a grid to sort, search and put in 묶음. A tap on one opens what can be done with it right there: 입기, 스튜디오로 고치기, 장터
 * 올리기, 링크 나누기, 묶음, 지우기. New themes are found in 장터 ([onFind]); only a phone that never wore one sees three to start with.
 */
@Composable
fun ThemeDrawer(
    style: WorkStyle?,
    onFind: (() -> Unit)?,
    onFix: (() -> Unit)?,
    /** 스튜디오, started from "theme:<key>", "kept:<id>" or "draft:<id>". */
    onStudio: (String) -> Unit,
    /** A recipe's page in 장터. */
    onRecipe: (String) -> Unit,
    /** 올리기 (만들기's 이름·설명·확인) with [RecipeLinks.remake] set. */
    onUpload: () -> Unit,
    /** PC d12d4cf1: a 만들던 레시피 from 만들기 (규칙 나누기 …) opens there again, at the step it was left on. */
    onMakeDraft: ((Long) -> Unit)? = null,
) {
    val c = LocalContext.current.appContainer
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val kept by c.wardrobe.kept.collectAsStateWithLifecycle()
    val folders by c.wardrobe.folders.collectAsStateWithLifecycle()
    val drafts by c.recipes.drafts.collectAsStateWithLifecycle(emptyList())
    val applied by c.recipes.applied.collectAsStateWithLifecycle(emptyList())
    val laid by c.wardrobe.overlays.collectAsStateWithLifecycle()
    val placed by c.wardrobe.placed.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    WornNow(onFix)

    // Everything of mine as one list; what each one is, kept by its key.
    val held = remember(lease, kept, drafts, applied, laid, placed) { gather(lease, kept, drafts, applied, laid, placed) }
    val items = remember(held, folders) { held.map { (i, _) -> i.copy(folder = folders[i.key]) } }
    val byKey = remember(held) { held.associate { (i, h) -> i.key to h } }

    var kind by rememberSaveable { mutableStateOf<DrawerKind?>(null) }
    var folder by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(Drawer.Sort.RECENT) }
    var open by remember { mutableStateOf<String?>(null) }

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("내 서랍", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f).semantics { heading() })
        TextButton(onClick = { sort = if (sort == Drawer.Sort.RECENT) Drawer.Sort.NAME else Drawer.Sort.RECENT },
            modifier = Modifier.heightIn(min = 48.dp)) { Text("↕ ${sort.label}") }
    }
    if (items.isNotEmpty()) {
        OutlinedTextField(query, { query = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("서랍에서 찾기") },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = if (query.isNotEmpty()) ({ IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Clear, "찾기 지우기") } }) else null)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            // PC 08a6f771: the numbers follow what 찾기 and the 묶음 leave.
            Drawer.counts(Drawer.shown(items, null, folder, query, sort)).forEach { (k, n) ->
                if (k == null || n > 0 || kind == k) FilterChip(kind == k, { kind = k }, label = { Text("${k?.label ?: "전체"} $n") })
            }
        }
        val names = Drawer.folders(items)
        if (names.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                FilterChip(folder == null, { folder = null }, label = { Text("모든 묶음") })
                names.forEach { f -> FilterChip(folder == f, { folder = if (folder == f) null else f }, label = { Text("📁 $f") }) }
                FilterChip(folder == Drawer.NO_FOLDER, { folder = Drawer.NO_FOLDER }, label = { Text("묶음 없음") })
            }
        }
        // PC 3차 (묶음을 풀면 걸러 놓은 빈 화면이 남아요): a 묶음 gone (or the last one) lets its filter go too.
        if (folder != null && (names.isEmpty() || folder != Drawer.NO_FOLDER && folder !in names)) folder = null
        val f = folder
        if (f != null && f != Drawer.NO_FOLDER) FolderRename(f) { folder = it }
    }

    val shown = Drawer.shown(items, kind, folder, query, sort)
    if (items.isEmpty()) Text("아직 서랍이 비었어요. 아래 테마를 입거나, 지금 모양을 저장하거나, 장터에서 마음에 드는 조각을 담아 두면 여기에 모여요",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    else if (shown.isEmpty()) Row(verticalAlignment = Alignment.CenterVertically) {
        Text("찾는 것이 없어요", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = { query = ""; kind = null; folder = null }) { Text("모두 보기") }
    } else Grid(shown, byKey) { open = it.key }

    // 내 테마로 저장: the look on the phone now kept as one of mine (free one, 프로 ten).
    val mineCount = c.wardrobe.ownCount(lease)
    val limit = c.wardrobe.outfitLimit()
    var saveNote by remember { mutableStateOf<String?>(null) }
    // PC 확인 e34fae4e (이름을 묻지 않고 '내 테마 1'로 저장): the name is asked first, '내 테마 N' already in it.
    var naming by remember { mutableStateOf<String?>(null) }
    OutlinedButton(onClick = { naming = "내 테마 ${mineCount + 1}" }, enabled = mineCount < limit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("지금 모양을 내 테마로 저장 ($mineCount/$limit)")
    }
    naming?.let { start ->
        var name by remember(start) { mutableStateOf(start) }
        AlertDialog(
            onDismissRequest = { naming = null },
            title = { Text("어떤 이름으로 저장할까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    OutlinedTextField(name, { name = it.take(30) }, Modifier.fillMaxWidth(), singleLine = true, label = { Text("테마 이름") })
                    Text("지금 보이는 모양(색·칸·배치)만 저장해요. 기록은 그대로예요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val n = name.trim().ifBlank { start }
                    naming = null
                    scope.launch {
                        saveNote = if (kotlinx.coroutines.withContext(c.appScope.coroutineContext) { c.wardrobe.saveMine(n) }) "지금 모양을 ‘$n’${Fmt.ro(n)} 서랍에 저장했어요"
                            else if (lease.history.any { it.own && it.title == n }) "‘$n’ 이름이 이미 있어요. 다른 이름으로 저장해 주세요" else "더 저장할 수 없어요"
                    }
                }) { Text("저장") }
            },
            dismissButton = { TextButton(onClick = { naming = null }) { Text("닫기") } },
        )
    }
    Text(saveNote ?: if (limit <= ThemeWardrobe.FREE_OUTFITS) "무료는 ${limit}벌까지예요. 프로는 ${ThemeWardrobe.PRO_OUTFITS}벌까지 저장해 바꿔 입어요"
        else "장터에 올린 내 테마는 광고 없이 입어요. 혼자 쓰는 테마는 다른 테마처럼 처음 2번 무료, 그 뒤 광고 한 번에 30일이에요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    // A phone that never wore a theme: three of its kind of work to start with, the rest in 장터.
    var trying by remember { mutableStateOf<Triple<Recipe, String, Int>?>(null) }
    // PC 확인 e34fae4e (하나 입으면 추천이 사라짐): the starters stay while the drawer holds little, without the ones already in it.
    val inDrawer = remember(lease) { (lease.history.map { it.title } + listOfNotNull(lease.worn?.title)).toSet() }
    val starters = remember(style, inDrawer) { BuiltInThemes.of(style).filter { it.title !in inDrawer }.take(3) }
    if (items.size < 4 && starters.isNotEmpty()) {
        Text(if (lease.worn == null && lease.history.isEmpty()) "처음이라면 이런 테마부터" else "이런 테마도 있어요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            starters.forEach { r ->
                Column(Modifier.width(112.dp).clickable(onClickLabel = "테마 보기") { trying = Triple(r, "builtin:${r.title}", 0) },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // [MKV-05] PC 확인 edb46c62 LOW: the same real look as the 장터 cards, not the old small swatch.
                    BuiltInShot(r, r.title, 112.dp, style, onClick = { trying = Triple(r, "builtin:${r.title}", 0) })
                    Text(r.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    // [TAB-05] PC 확인 38d2d8e7: three 전문 달력 start on 목록, so each says how it differs.
                    if (r.title.endsWith("전문 달력") && r.summary.isNotBlank()) Text(r.summary, style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    onFind?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("장터에서 새 테마 찾기") } }
    Text("테마는 색, 칸 모양, 배치처럼 보이는 방식만 바꿔요. 근무, 메모, 가계부, 급여 기록과 알람은 그대로예요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    trying?.let { (r, code, ver) -> ThemeSheet(r, code, style, ver) { trying = null } }
    open?.let { key ->
        val item = items.firstOrNull { it.key == key }
        val h = byKey[key]
        if (item == null || h == null) open = null
        else ItemSheet(item, h, onDismiss = { open = null }, onTry = { r, code, ver -> open = null; trying = Triple(r, code, ver) },
            onStudio = { open = null; onStudio(it) }, onRecipe = { open = null; onRecipe(it) }, onUpload = { open = null; onUpload() },
            onMakeDraft = { open = null; onMakeDraft?.invoke(it) ?: onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_DRAFT + it) })
    }
}

/** Everything of mine, each with what it is. The theme worn now is on its own card above, so it is not here twice. */
private fun gather(lease: ThemeLease, kept: List<ThemeWardrobe.Kept>, drafts: List<RecipeDraftEntity>, applied: List<AppliedRecipeEntity>,
                   laid: List<ThemeWardrobe.Overlay> = emptyList(), placed: Map<String, Long> = emptyMap()): List<Pair<DrawerItem, Held>> {
    val out = ArrayList<Pair<DrawerItem, Held>>()
    // PC 확인 e34fae4e (테마를 입었는데 서랍이 비었다고 해요): the theme worn now is in the drawer too, first, marked 입는 중.
    val worn = lease.worn
    // PC 3차: a theme worn in part stays itself in the drawer (입는 중 · 일부), not a new ‘… 일부’.
    // PC 08a6f771: a mix made in the studio (‘조합 · …’) is mine, on its own, and the theme its parts came from stays as it was.
    val mix = worn?.takeIf { ThemeWardrobe.isMix(it.title) && !it.own }
    val whole = if (mix != null) null else worn?.let { w ->
        lease.history.firstOrNull { it.key == w.key } ?: lease.history.firstOrNull { it.own && w.own && it.title == w.title.removeSuffix(" 일부") } }
    (listOfNotNull(worn?.takeIf { whole == null }) + lease.history.filter { !(ThemeWardrobe.isMix(it.title) && !it.own) }).forEach { w ->
        val isMix = mix != null && w === mix
        val now = if (mix != null) isMix else w.key == (whole ?: worn)?.key
        val words = listOfNotNull(w.mixedFrom.takeIf { it.isNotBlank() }, "장터".takeIf { w.free }, "입는 중".takeIf { now }, "섞음".takeIf { isMix }).joinToString(" ")
        out += DrawerItem(if (isMix) "theme:mix:${w.key}" else "theme:${w.key}", if (w.own || isMix) DrawerKind.MINE else DrawerKind.WORN, w.title,
            if (now) Long.MAX_VALUE else placed[w.key] ?: (w.from * DAY),
            words = words) to Held.Theme(w, now, part = !isMix && now && worn != null && worn.body != w.body)
    }
    // PC 3차 (빈 ✏ 카드): a draft with nothing in it yet is not shown. PC 08a6f771: one 만들기 left half way (규칙 하나, 이름 없음) is
    // read leniently, as 만들기 itself reads it; the same draft kept twice (나갈 때마다 하나씩) shows once, the newest.
    drafts.sortedByDescending { it.updatedAt }.distinctBy { it.body }.mapNotNull { d -> draftKinds(d.body)?.let { d to it } }.forEach { (d, kinds) ->
        // A draft with no name yet says what it holds (규칙·단추 …), not 꾸미던 모양 for everything.
        val what = kinds.filter { it !in BuiltInThemes.LOOK_KINDS }.map { it.label }.distinct().take(2).joinToString("·")
        val name = runCatching { org.json.JSONObject(d.body).optString("title") }.getOrNull().orEmpty()
        val title = name.ifBlank { if (what.isEmpty()) "꾸미던 모양" else "만들던 $what" }
        out += DrawerItem("draft:${d.id}", DrawerKind.MINE, title, d.updatedAt, words = "만들던 $what") to Held.Draft(d)
    }
    // 받은 것: recipes from 장터 still on (the newest put-on of each); one that was only a theme's (or a tab's) looks shows as that already.
    val themeCodes = lease.history.map { it.code }.toSet() + listOfNotNull(lease.worn?.code) + laid.map { it.code }
    applied.filter { it.code.isNotEmpty() && it.undoneAt == null && it.replacedAt == null }
        .groupBy { it.code }.values.map { l -> l.maxBy { it.appliedAt } }
        .filterNot { e -> e.code in themeCodes && e.pieces.split(',').all { k -> PieceKind.of(k)?.let { it in BuiltInThemes.LOOK_KINDS } != false } }
        .forEach { e ->
            val what = e.pieces.split(',').mapNotNull { PieceKind.of(it)?.label }.joinToString(" ")
            out += DrawerItem("got:${e.code}", DrawerKind.GOT, e.title, e.appliedAt, words = what) to Held.Got(e)
        }
    kept.forEach { k ->
        out += DrawerItem("part:${k.id}", DrawerKind.PART, k.title, k.id, words = k.fromTitle) to Held.Part(k)
    }
    // PC 3차 (덮개 꾸밈은 서랍에도 공방에도 없어요): the tabs laid on the theme are things got too.
    laid.forEach { o ->
        out += DrawerItem("tab:${o.tab}", DrawerKind.GOT, o.title, o.at, words = "얹은 탭 ${com.bamnat.core.recipe.LookParts.tabLabel(o.tab)}") to Held.Laid(o)
    }
    return out
}

/** The kinds of piece a draft holds (null when it holds none), read as loosely as 만들기 reads its drafts. */
private fun draftKinds(body: String): List<PieceKind>? {
    val kinds = runCatching { RecipeCodec.read(body).pieces.map { it.kind } }.getOrNull()
        ?: runCatching {
            val a = org.json.JSONObject(body).optJSONArray("pieces") ?: org.json.JSONArray()
            (0 until a.length()).mapNotNull { i -> a.optJSONObject(i)?.optString("kind")?.let(PieceKind::of) }
        }.getOrNull()
    return kinds?.takeIf { it.isNotEmpty() }
}

/** Two to four a row by the width, each drawn as itself where it has a look. */
@Composable
private fun Grid(items: List<DrawerItem>, byKey: Map<String, Held>, onOpen: (DrawerItem) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val cols = when { maxWidth >= 560.dp -> 4; maxWidth >= 380.dp -> 3; else -> 2 }
        val gap = 10.dp
        val cell = (maxWidth - gap * (cols - 1)) / cols
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items.chunked(cols).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { i -> Cell(i, byKey[i.key], cell, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun Cell(i: DrawerItem, h: Held?, width: androidx.compose.ui.unit.Dp, onOpen: (DrawerItem) -> Unit) {
    Column(Modifier.width(width).clip(MaterialTheme.shapes.small).clickable(onClickLabel = "할 일 보기") { onOpen(i) },
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Thumb(h, width)
        Text((if (i.kind == DrawerKind.MINE && h is Held.Theme) "🎨 " else "") + i.title, style = MaterialTheme.typography.labelLarge, maxLines = 2,
            overflow = TextOverflow.Ellipsis)
        Text(listOfNotNull(status(h), i.folder?.let { "📁 $it" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Its look as a little phone; a recipe with no look or a draft gets a tile with what it holds. */
@Composable
private fun Thumb(h: Held?, width: androidx.compose.ui.unit.Dp) {
    val body = when (h) { is Held.Theme -> h.w.body; is Held.Part -> h.k.body; is Held.Got -> h.e.body; is Held.Laid -> h.o.body; is Held.Draft -> h.d.body; null -> null }
    val look = remember(body) { body?.let { b -> runCatching { RecipeCodec.read(b) }.getOrNull()?.let(::swatchOf) } }
    if (look != null) { ThemeThumb(look, width = width); return }
    val emoji = when (h) {
        is Held.Draft -> "✏️"
        is Held.Got -> h.e.pieces.split(',').firstNotNullOfOrNull { PieceKind.of(it) }?.let(::emojiOf) ?: "📦"
        else -> "🧩"
    }
    Box(Modifier.width(width).aspectRatio(1.8f).clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center) { Text(emoji, style = MaterialTheme.typography.headlineMedium) }
}

private fun emojiOf(k: PieceKind): String = when {
    k in BuiltInThemes.LOOK_KINDS -> "🎨"
    k.name.contains("ALARM") -> "⏰"
    k.name.contains("MEMO") -> "📝"
    k.name.contains("PAY") || k.name.contains("WAGE") -> "💰"
    k.name.contains("LOGIC") -> "⚙️"
    else -> "📦"
}

/** One short line on where it stands. */
private fun status(h: Held?): String? = when (h) {
    is Held.Theme -> (if (h.part) "일부 입는 중 · " else if (h.now) "입는 중 · " else "") +
        when { ThemeWardrobe.isMix(h.w.title) && !h.w.own -> "내가 섞은 것"; h.w.free -> "내 것 · 장터에 있음"; h.w.own && h.w.mixedFrom.isNotBlank() -> "내 것 · 섞음"; h.w.own -> "내 것 · 나만"; h.now -> "남의 테마"; else -> "입었던" }
    is Held.Draft -> if (h.d.step >= 2) "만들던 · 올리던 중" else "만들던 · ${com.bamnat.app.ui.studio.ago(h.d.updatedAt)}"
    is Held.Got -> "받음 · " + h.e.pieces.split(',').mapNotNull { PieceKind.of(it)?.label }.take(2).joinToString("·")
    is Held.Part -> if (h.k.own) "조각 · 내 것" else "조각 · ‘${h.k.fromTitle}’에서"
    is Held.Laid -> "얹은 탭 · ${com.bamnat.core.recipe.LookParts.tabLabel(h.o.tab)}"
    null -> null
}

/** A 장터 code that can be opened and sent as a link (not a built-in look, not "" for mine). */
private fun marketCode(code: String): String? = code.takeIf { it.isNotEmpty() && !it.startsWith("builtin:") }

/**
 * [DRW-02] What can be done with one thing, right from the drawer. Every 지우기·빼기·되돌리기 asks first; 바로 입기 and 되돌리기 say so
 * with 되돌리기 ([PutOnNotice]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ItemSheet(i: DrawerItem, h: Held, onDismiss: () -> Unit, onTry: (Recipe, String, Int) -> Unit, onStudio: (String) -> Unit,
                      onRecipe: (String) -> Unit, onUpload: () -> Unit, onMakeDraft: (Long) -> Unit) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    var asking by remember { mutableStateOf<Triple<String, String, () -> Unit>?>(null) }
    var filing by remember { mutableStateOf(false) }
    fun share(title: String, code: String) {
        val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
            .putExtra(android.content.Intent.EXTRA_TEXT, RecipeLinks.shareText(title, code))
        runCatching { context.startActivity(android.content.Intent.createChooser(send, "링크 나누기")) }
    }
    // PC 08a6f771 (시트가 반만 열려 아래 단추가 가려요): opened all the way.
    AppSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Thumb(h, 72.dp)
                Column(Modifier.weight(1f)) {
                    Text(i.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    Text(listOfNotNull(i.kind.label, status(h)?.substringAfter(" · ", "")?.ifBlank { null }, i.folder?.let { "📁 $it" }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.heightIn(min = 4.dp))
            @Composable fun Act(label: String, line: String? = null, primary: Boolean = false, go: () -> Unit) {
                if (primary) Button(onClick = go, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
                else OutlinedButton(onClick = go, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
                line?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            when (h) {
                is Held.Theme -> {
                    val w = h.w
                    val r = remember(w.body) { runCatching { RecipeCodec.read(w.body) }.getOrNull() }
                    val ad = remember(w.key) { c.wardrobe.needsAdFor(w.code, w.title) }
                    if (r != null && !ad && (!h.now || h.part)) Act(if (h.part) "전부 입기" else "바로 입기", primary = true) {
                        onDismiss()
                        c.appScope.launch {
                            val m = c.wardrobe.mark()
                            if (c.wardrobe.wearAgain(w)) PutOnNotice.say("‘${w.title}’${Fmt.obj(w.title)} 입었어요. 기록은 그대로예요") { c.wardrobe.undoTo(m); true }
                        }
                    }
                    if (r != null && (!h.now || h.part)) Act(if (ad) "입어 보고 입기 (광고)" else "입어 보고 고른 것만 입기", primary = ad) { onTry(r, w.code, w.ver) }
                    Act("스튜디오로 고치기") { onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_THEME + w.key) }
                    val code = if (w.own) marketCode(w.shared) else marketCode(w.code)
                    if (w.own && code == null && c.recipeApi.enabled && r != null) Act("장터에 올리기",
                        if (w.mixedFrom.isNotBlank()) "다른 테마를 섞은 것이라 원작의 수정판으로 올라가요" else "장터에 올린 내 테마는 기간·광고 없이 입어요. 나만 보기(링크로만)로도 올릴 수 있어요") {
                        RecipeLinks.remake = w.body to (w.mixedCodes.split(',').firstOrNull { it.isNotBlank() } ?: "")
                        RecipeLinks.ownTheme = if (w.mixedFrom.isNotBlank()) null else w.key
                        onUpload()
                    }
                    if (code != null) {
                        Act("장터에서 보기") { onRecipe(code) }
                        Act("링크 나누기") { share(w.title, code) }
                    }
                    Act(if (i.folder == null) "묶음에 넣기" else "묶음 바꾸기") { filing = true }
                    // A mix worn now lives only while it is worn (벗기 is on the card above); its key is the theme its parts came from.
                    if (!(ThemeWardrobe.isMix(w.title) && !w.own)) Act(if (w.own) "지우기" else "서랍에서 빼기") {
                        asking = Triple(if (w.own) "내 테마를 지울까요?" else "입었던 것에서 뺄까요?",
                            "‘${w.title}’${Fmt.obj(w.title)} 서랍에서 빼요. 지금 입은 모양은 그대로예요" + if (w.own && w.shared.isNotEmpty()) ". 장터에 올린 것은 장터에 그대로 있어요" else "") {
                            c.wardrobe.removeKept(w.key); c.wardrobe.setFolder(i.key, null); onDismiss()
                        }
                    }
                }
                is Held.Got -> {
                    val e = h.e
                    Act("장터에서 보기", "넣은 것을 고르거나 새 판을 볼 수 있어요", primary = true) { onRecipe(e.code) }
                    Act("링크 나누기") { share(e.title, e.code) }
                    Act(if (i.folder == null) "묶음에 넣기" else "묶음 바꾸기") { filing = true }
                    if (c.recipes.canUndo(e)) Act("되돌리기 (넣은 지 7일 안)", "넣기 전으로 돌아가요. 그 사이 적은 기록은 그대로예요") {
                        asking = Triple("되돌릴까요?", "‘${e.title}’${Fmt.iga(e.title)} 바꾼 것만 넣기 전으로 돌아가요. 넣은 뒤 내가 직접 바꾼 것과 근무 기록·가계부·메모는 그대로예요") {
                            c.appScope.launch {
                                val ok = c.recipes.undo(e.id)
                                PutOnNotice.say(if (ok) "‘${e.title}’${Fmt.obj(e.title)} 되돌렸어요" else "되돌릴 수 없어요. 넣은 지 7일이 지났어요")
                            }
                            onDismiss()
                        }
                    } else Text("넣은 지 7일이 지나 그대로 쓰고 있어요. 바꾸려면 그 설정 화면에서 고쳐요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                is Held.Part -> {
                    val k = h.k
                    Act("스튜디오에서 꾸미기", "지금 모양 위에 이 조각을 얹어 보고, 마음에 들면 입혀요", primary = true) {
                        onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_KEPT + k.id)
                    }
                    marketCode(k.from)?.let { code -> Act("나온 테마 보기 (‘${k.fromTitle}’)") { onRecipe(code) } }
                    Act(if (i.folder == null) "묶음에 넣기" else "묶음 바꾸기") { filing = true }
                    Act("서랍에서 빼기") {
                        asking = Triple("조각을 뺄까요?", "‘${k.title}’${Fmt.obj(k.title)} 서랍에서 빼요. 지금 입은 모양은 그대로예요") {
                            c.wardrobe.forgetKept(k.id); c.wardrobe.setFolder(i.key, null); onDismiss()
                        }
                    }
                }
                is Held.Laid -> {
                    val o = h.o
                    val tab = com.bamnat.core.recipe.LookParts.tabLabel(o.tab)
                    Act("탭에서 빼기", "‘$tab’ 탭이 입은 테마 모양으로 돌아가요", primary = true) {
                        onDismiss()
                        c.appScope.launch {
                            val m = c.wardrobe.mark()
                            c.wardrobe.removeOverlay(o.tab)
                            PutOnNotice.say("‘$tab’ 탭에서 ‘${o.title}’${Fmt.obj(o.title)} 뺐어요") { c.wardrobe.undoTo(m); true }
                        }
                    }
                    marketCode(o.code)?.let { code ->
                        Act("장터에서 보기") { onRecipe(code) }
                        Act("링크 나누기") { share(o.title, code) }
                    }
                    Act(if (i.folder == null) "묶음에 넣기" else "묶음 바꾸기") { filing = true }
                }
                is Held.Draft -> {
                    val d = h.d
                    // 스튜디오 drafts (step 1, looks) go back to the studio; one from 만들기 (올리기 단계, 규칙 같은 기능) to 만들기.
                    val made = d.step >= 2 || draftKinds(d.body).orEmpty().any { it !in BuiltInThemes.LOOK_KINDS }
                    Act("이어서 하기", if (d.step >= 2) "올리기 ${d.step - 1}/4단계에서 멈춘 곳부터" else if (made) "만들기에서 멈춘 곳부터" else "스튜디오에서 꾸미던 곳부터", primary = true) {
                        if (made) onMakeDraft(d.id)
                        else onStudio(com.bamnat.app.ui.studio.StudioViewModel.START_DRAFT + d.id)
                    }
                    Act(if (i.folder == null) "묶음에 넣기" else "묶음 바꾸기") { filing = true }
                    Act("지우기") {
                        asking = Triple("만들던 것을 지울까요?", "지우면 이어서 만들 수 없어요") {
                            c.appScope.launch { c.recipes.deleteDraft(d.id) }; c.wardrobe.setFolder(i.key, null); onDismiss()
                        }
                    }
                }
            }
        }
    }
    asking?.let { (title, text, go) ->
        AlertDialog(onDismissRequest = { asking = null }, title = { Text(title) }, text = { Text(text) },
            confirmButton = { TextButton(onClick = { asking = null; go() }) { Text(if (title.startsWith("되돌")) "되돌리기" else if (title.contains("지울")) "지우기" else "빼기") } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("닫기") } })
    }
    if (filing) FolderPick(i) { filing = false }
}

/** 묶음에 넣기: one of the 묶음 in use, or a new name; 묶음에서 빼기 when it is in one. */
@Composable
private fun FolderPick(i: DrawerItem, onDone: () -> Unit) {
    val c = LocalContext.current.appContainer
    val all by c.wardrobe.folders.collectAsStateWithLifecycle()
    val names = remember(all) { all.values.filter { it.isNotBlank() }.distinct().sortedWith(java.text.Collator.getInstance(java.util.Locale.KOREAN)) }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("어느 묶음에 넣을까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                names.forEach { n ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { c.wardrobe.setFolder(i.key, n); onDone() },
                        verticalAlignment = Alignment.CenterVertically) {
                        Text((if (i.folder == n) "✓ " else "") + "📁 $n", style = MaterialTheme.typography.bodyLarge)
                    }
                }
                OutlinedTextField(name, { name = it.take(ThemeWardrobe.FOLDER_NAME) }, Modifier.fillMaxWidth(), singleLine = true,
                    label = { Text("새 묶음 이름") }, placeholder = { Text("예: 겨울, 출근용") })
            }
        },
        confirmButton = { TextButton(onClick = { c.wardrobe.setFolder(i.key, name); onDone() }, enabled = name.isNotBlank()) { Text("새 묶음에 넣기") } },
        dismissButton = {
            Row {
                if (i.folder != null) TextButton(onClick = { c.wardrobe.setFolder(i.key, null); onDone() }) { Text("묶음에서 빼기") }
                TextButton(onClick = onDone) { Text("닫기") }
            }
        },
    )
}

/** The 묶음 on show, renamed (or let go: what is in it stays in the drawer). */
@Composable
private fun FolderRename(f: String, onRenamed: (String?) -> Unit) {
    val c = LocalContext.current.appContainer
    var editing by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        TextButton(onClick = { editing = true }) { Text("‘$f’ 묶음 이름 바꾸기") }
    }
    if (!editing) return
    // PC d12d4cf1 (묶음 이름 칸을 누르면 커서가 맨 앞): the cursor starts after the name.
    var field by remember(f) { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(f, androidx.compose.ui.text.TextRange(f.length))) }
    val name = field.text
    AlertDialog(
        onDismissRequest = { editing = false },
        title = { Text("묶음 이름") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedTextField(field, { field = if (it.text.length <= ThemeWardrobe.FOLDER_NAME) it else field }, Modifier.fillMaxWidth(), singleLine = true)
                Text("묶음을 풀어도 안에 있던 것은 서랍에 그대로 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        // PC 08a6f771 (이름을 바꾸면 거르기가 풀려요): the filter goes on with the new name.
        confirmButton = { TextButton(onClick = { c.wardrobe.renameFolder(f, name); onRenamed(name.trim().take(ThemeWardrobe.FOLDER_NAME)); editing = false }, enabled = name.isNotBlank()) { Text("바꾸기") } },
        dismissButton = {
            Row {
                TextButton(onClick = { c.wardrobe.renameFolder(f, ""); onRenamed(null); editing = false }) { Text("묶음 풀기") }
                TextButton(onClick = { editing = false }) { Text("닫기") }
            }
        },
    )
}
