package com.bamnat.app.ui.recipe

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.recipe.ThemeSwatch
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.CellLook
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ThemeLease
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch

/** [FREE-08] A recipe's look for [ThemeThumb], read as the server's list reads it (lookOf), for themes on the phone. */
fun swatchOf(r: Recipe): ThemeSwatch? {
    fun piece(k: PieceKind) = r.pieces.firstOrNull { it.kind == k }?.values
    val screens = ScreenLooks.decode(piece(PieceKind.SCREEN_LOOK)?.get("screens") as? String)
    val all = screens.of("calendar")
    val theme = piece(PieceKind.CAL_THEME)
    val view = piece(PieceKind.CAL_VIEW)
    val memo = piece(PieceKind.MEMO_LOOK)
    @Suppress("UNCHECKED_CAST")
    val days = (piece(PieceKind.SHIFT_LOOK)?.get("items") as? List<Map<String, Any?>>).orEmpty().take(4).map {
        ThemeSwatch.Day(it["color"] as? Long, (it["label"] as? String).orEmpty(), it["emoji"] as? String, it["role"] as? String)
    }
    val s = ThemeSwatch(all.bg, all.card, all.ink, all.accent, theme?.get("today") as? Long, theme?.get("sat") as? Long, theme?.get("sun") as? Long,
        days, (view?.get("looks") as? String)?.takeIf { CellLook.looksProblem(it) == null }, view?.get("pattern") as? String,
        view?.get("badge") as? String, memo?.get("layout") as? String, all.photo != null)
    return s.takeIf { it != ThemeSwatch() }
}

/**
 * [FREE-09][FREE-10] 1.35 내 테마 (사용자 2026-10-30 21:37 "테마는 사용자 자신의 페이지 성격이 강하므로 … 지금 중복이 존재해"): the user's
 * own wardrobe, not a second shop. The theme worn now, large, with its days and 벗기; 내 테마 (saved, 프로 more); 입었던 테마 to wear
 * again. Finding new themes is 장터's ([onFind]); only a phone that has never worn one sees three to start with. A tap opens
 * [ThemeSheet] (입어 보기, 입기). 벗기 always comes back to the user's own look; no record changes either way.
 */
@Composable
fun ThemePicker(style: WorkStyle?, modifier: Modifier = Modifier, onFind: (() -> Unit)? = null, onFix: (() -> Unit)? = null,
                onUseKept: ((Long) -> Unit)? = null) {
    val c = LocalContext.current.appContainer
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Pair<Recipe, String>?>(null) }
    var takingOff by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        // 지금 입은 테마: what the whole app wears now, drawn as itself.
        androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val w = lease.worn
                val look = remember(w?.body) { w?.let { runCatching { RecipeCodec.read(it.body) }.getOrNull()?.let(::swatchOf) } }
                look?.let { ThemeThumb(it, width = 96.dp) }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("지금 입은 테마", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(w?.title ?: "내 모양 (테마 없음)", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                    if (w != null) {
                        val left = lease.daysLeft(Today.date().toEpochDay())
                        val runs = c.wardrobe.state() != ThemeLease.State.NONE
                        Text(if (runs && left != null) "${left}일 남았어요. 끝나는 날 연장할지 물어요" else if (w.free) "장터에 올린 내 테마라 기간 없이 입어요" else "기간 없이 입어요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (w != null) TextButton(onClick = { takingOff = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("벗기") }
                        // [THM-05] Within 7 days of a 새 판, the one before.
                        val prev = remember(w?.ver) { c.wardrobe.previous() }
                        if (w != null && prev != null) TextButton(onClick = { c.appScope.launch { c.wardrobe.backToPrevious() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                            Text("이전 판으로")
                        }
                        onFix?.let { TextButton(onClick = it, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 모양 고치기") } }
                    }
                }
            }
        }
        // [MKX-02] 1.36 얹은 탭: one tab's looks from 장터's 기능별 탭, over the theme worn, each to take off.
        val overlays by c.wardrobe.overlays.collectAsStateWithLifecycle()
        if (overlays.isNotEmpty()) androidx.compose.material3.OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("테마 위에 얹은 탭", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                overlays.forEach { o ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("🧩 ${com.bamnat.core.recipe.LookParts.tabLabel(o.tab)} · ${o.title}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { c.appScope.launch { c.wardrobe.removeOverlay(o.tab) } }) { Text("빼기") }
                    }
                }
                var keep by remember { mutableStateOf(c.wardrobe.keepOverlays) }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("다른 테마를 입어도 얹은 탭은 그대로", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(keep, { keep = it; c.wardrobe.keepOverlays = it })
                }
            }
        }
        // 내 테마: saved looks, free one, 프로 ten (꾸미기 혜택). [THM-01] Mine in 장터 never runs out; mine kept to myself runs as others' do.
        val mine = lease.history.filter { it.own && it.key != lease.worn?.key }
        val mineCount = lease.history.count { it.own }
        val limit = c.wardrobe.outfitLimit()
        Text("내 테마 ($mineCount/$limit)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
        if (mine.isNotEmpty()) Shelf(mine) { w -> runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let { open = it to w.code } }
        var saveNote by remember { mutableStateOf<String?>(null) }
        OutlinedButton(onClick = {
            scope.launch { saveNote = if (kotlinx.coroutines.withContext(c.appScope.coroutineContext) { c.wardrobe.saveMine("내 테마 ${mineCount + 1}") }) "지금 모양을 ‘내 테마 ${mineCount + 1}’로 저장했어요" else "더 저장할 수 없어요" }
        }, enabled = mineCount < limit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("지금 모양을 내 테마로 저장") }
        Text(saveNote ?: if (limit <= com.bamnat.app.recipe.ThemeWardrobe.FREE_OUTFITS) "무료는 ${limit}벌까지예요. 프로는 ${com.bamnat.app.recipe.ThemeWardrobe.PRO_OUTFITS}벌까지 저장해 바꿔 입어요"
            else "장터에 올린 내 테마는 광고 없이 입어요. 혼자 쓰는 테마는 다른 테마처럼 처음 2번 무료, 그 뒤 광고 한 번에 30일이에요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 입었던 테마: others' themes worn before, to wear again.
        val past = lease.history.filter { !it.own && it.key != lease.worn?.key }
        if (past.isNotEmpty()) {
            Text("입었던 테마", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Shelf(past.take(10)) { w -> runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let { open = it to w.code } }
        }
        // F-93 [TRY-02] 담아 둔 조각: the box shows here too, each with where it came from, to put on a draft in the studio or let go.
        val kept by c.wardrobe.kept.collectAsStateWithLifecycle()
        if (kept.isNotEmpty()) {
            Text("담아 둔 조각 (${kept.size}/${c.wardrobe.partsLimit()})", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            var letGo by remember { mutableStateOf<com.bamnat.app.recipe.ThemeWardrobe.Kept?>(null) }
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                kept.forEach { k ->
                    val look = remember(k.body) { runCatching { RecipeCodec.read(k.body) }.getOrNull()?.let(::swatchOf) }
                    Column(Modifier.width(112.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        look?.let { ThemeThumb(it, width = 112.dp) }
                        Text(k.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(if (k.own) "내 조각" else "출처 ‘${k.fromTitle}’", style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // F-103: two short buttons on one line (a wide padding broke 빼기 in two).
                        Row {
                            val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp)
                            onUseKept?.let { use -> TextButton(onClick = { use(k.id) }, contentPadding = pad, modifier = Modifier.heightIn(min = 48.dp)) { Text("꾸미기", maxLines = 1, softWrap = false) } }
                            TextButton(onClick = { letGo = k }, contentPadding = pad, modifier = Modifier.heightIn(min = 48.dp)) { Text("빼기", maxLines = 1, softWrap = false) }
                        }
                    }
                }
            }
            letGo?.let { k ->
                AlertDialog(
                    onDismissRequest = { letGo = null },
                    title = { Text("보관함에서 뺄까요?") },
                    text = { Text("‘${k.title}’을(를) 보관함에서 빼요. 지금 입은 모양은 그대로예요") },
                    confirmButton = { TextButton(onClick = { c.wardrobe.forgetKept(k.id); letGo = null }) { Text("빼기") } },
                    dismissButton = { TextButton(onClick = { letGo = null }) { Text("닫기") } },
                )
            }
        }
        // A phone that never wore a theme: three of its kind of work to start with, the rest in 장터.
        if (lease.worn == null && lease.history.isEmpty()) {
            val starters = remember(style) { BuiltInThemes.of(style).take(3) }
            Text("처음이라면 이런 테마부터", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                starters.forEach { r ->
                    Column(Modifier.width(112.dp).clickable(onClickLabel = "테마 보기") { open = r to "builtin:${r.title}" },
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        swatchOf(r)?.let { ThemeThumb(it, width = 112.dp) }
                        Text(r.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
        onFind?.let { Button(onClick = it, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("장터에서 새 테마 찾기") } }
        Text("테마는 색, 칸 모양, 배치처럼 보이는 방식만 바꿔요. 근무, 메모, 가계부, 급여 기록과 알람은 그대로예요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    open?.let { (r, code) -> ThemeSheet(r, code, style) { open = null } }
    if (takingOff) AlertDialog(
        onDismissRequest = { takingOff = false },
        title = { Text("테마를 벗을까요?") },
        text = { Text("입기 전 내 모양으로 돌아가요. 입었던 테마에 남아서 다시 입을 수 있고, 테마를 입은 채 고친 것은 ‘내가 고친 …’으로 따로 남아요") },
        confirmButton = { TextButton(onClick = { takingOff = false; c.appScope.launch { c.wardrobe.takeOff() } }) { Text("벗기") } },
        dismissButton = { TextButton(onClick = { takingOff = false }) { Text("닫기") } },
    )
}

/** Worn or saved themes in a row, each drawn as itself; a tap opens it. */
@Composable
private fun Shelf(items: List<ThemeLease.Wear>, onOpen: (ThemeLease.Wear) -> Unit) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { w ->
            val look = remember(w.body) { runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let(::swatchOf) }
            Column(Modifier.width(112.dp).clickable(onClickLabel = if (w.own) "내 테마 보기" else "다시 입기") { onOpen(w) },
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                look?.let { ThemeThumb(it, width = 112.dp) }
                Text((if (w.own) "🎨 " else "") + w.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(if (w.free) "내 테마 · 장터에 있음" else if (w.own) "내 테마 · 나만" else "다시 입기", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

/**
 * [FREE-08][FREE-10] One theme: 입어 보기 on the example phone, every tab a tap away, then 입기. Someone else's theme past the free ones
 * asks for a rewarded ad watched to the end; mine, 프로 and the ads switch off never do.
 */
@Composable
fun ThemeSheet(r: Recipe, code: String, style: WorkStyle?, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val own = code.isEmpty()
    // [THM-01] Mine kept to myself asks as others' do; mine in 장터 never.
    val needsAd = remember { c.wardrobe.needsAdFor(code, r.title) }
    val all = remember(r) { com.bamnat.core.recipe.LookParts.of(r).map { it.id }.toSet() }
    fun said(t: String) = Toast.makeText(context.applicationContext, t, Toast.LENGTH_SHORT).show()
    // [J135-15] Worn on the app's scope: the look changing redraws the screens under this sheet, and a coroutine of the sheet would be
    // cancelled half way (the look on, 입는 중 never written). [TRY-01] Only the parts ticked; the rest stays the user's own.
    fun wear(picked: Set<String>, after: () -> Unit = {}) = c.appScope.launch {
        val whole = picked.containsAll(all)
        val worn = if (whole) r else com.bamnat.core.recipe.LookParts.pick(r, picked).copy(title = "${r.title} 일부".take(30))
        if (c.wardrobe.wear(worn, code, own)) {
            after()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                said(if (whole) "‘${r.title}’을(를) 입었어요. 기록은 그대로예요" else "‘${r.title}’에서 고른 ${picked.size}개를 입었어요. 기록은 그대로예요")
                onDismiss()
            }
        }
    }
    TryOnScreen(r, style, onDismiss,
        onKeep = { ids -> c.wardrobe.keep(r, code, ids.toList()) },
    ) { picked ->
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        val label = if (picked.containsAll(all)) "이 테마 입기" else "고른 ${picked.size}개 입히기"
        if (picked.isEmpty()) Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("입힐 것을 골라 주세요") }
        else if (needsAd) com.bamnat.app.ads.ThemeAdButton(
            "광고 보고 $label", "광고를 끝까지 보면 30일 동안 입어요. 프로는 광고 없이 입어요",
            onReward = { wear(picked) },
            onQuit = { note = "광고를 끝까지 봐야 입을 수 있어요" },
            onOffline = { note = "인터넷에 연결되면 다시 해 주세요" },
            onNoAd = { reason -> wear(picked) { c.wardrobe.counted(reason) } },
        ) else Button(onClick = { wear(picked) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text(label) }
    }
}

/**
 * [FREE-10] 기간이 끝난 테마 (사용자 2026-10-30 15:56): asked once when the app opens, never over an alarm. 연장 plays an ad and only a whole
 * view adds 30 days; 아니요 goes back to the user's own look (the theme stays in 입었던 테마); closing keeps the theme and asks another day.
 * Three days before the end it says so once.
 */
@Composable
fun ThemeExpiryAsk() {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val worn = lease.worn ?: return
    var shown by remember { mutableStateOf(true) }
    var note by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(worn.key, worn.until) {
        if (c.wardrobe.state() == ThemeLease.State.SOON && c.wardrobe.soonSaid() != "${worn.key}|${worn.until}") {
            c.wardrobe.saySoon("${worn.key}|${worn.until}")
            Toast.makeText(context, "‘${worn.title}’ 테마가 ${lease.daysLeft(Today.date().toEpochDay())}일 뒤에 끝나요", Toast.LENGTH_LONG).show()
        }
    }
    if (!shown || !c.wardrobe.ask()) return
    fun said(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
    AlertDialog(
        onDismissRequest = { shown = false; c.wardrobe.later() },
        title = { Text("‘${worn.title}’ 기간이 끝났어요. 연장할까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("광고를 끝까지 보면 30일 더 입어요. 아니요를 누르면 내 모양으로 돌아가고, 입었던 테마에서 다시 입을 수 있어요. 기록은 그대로예요",
                    style = MaterialTheme.typography.bodyMedium)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                com.bamnat.app.ads.ThemeAdButton(
                    "광고 보고 30일 연장", "광고를 끝까지 봐야 연장돼요",
                    onReward = {
                        c.wardrobe.extend()
                        val until = c.wardrobe.lease.value.worn?.until?.let { java.time.LocalDate.ofEpochDay(it) }
                        said("30일 연장됐어요" + (until?.let { " · ${it.monthValue}월 ${it.dayOfMonth}일까지" } ?: ""))
                        shown = false
                    },
                    onQuit = { note = "광고를 끝까지 봐야 연장돼요. 다시 볼 수 있어요" },
                    onOffline = { shown = false; c.wardrobe.later(); said("인터넷에 연결되면 다시 물어볼게요. 테마는 그대로예요") },
                    onNoAd = { reason ->
                        c.wardrobe.noAd(reason)
                        said("광고를 불러오지 못해 ${ThemeLease.NO_AD_DAYS}일 연장했어요")
                        shown = false
                    },
                )
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { shown = false; c.appScope.launch { c.wardrobe.takeOff() } }) { Text("아니요, 내 모양으로") } },
    )
}

/**
 * [THM-05] 1.35 테마 새 판 (사용자 2026-10-30 21:53 "테마가 업데이트 됬어요 업데이트 하시겠어요? 알림을 뜨게해주고 업데이트 하기전에는
 * 바뀌지 않게"): someone else's theme worn now has a newer 판 in 장터. Asked once per 판 when the app opens; the look stays as it is until
 * 업데이트 is pressed. 바뀐 모습 보기 opens 입어 보기 on the new 판 (지금 ↔ 입으면); 지금 그대로 keeps this 판 and is not asked again for it.
 * 7 days after 업데이트, 이전 판으로 is in 마을 › 테마.
 */
@Composable
fun ThemeUpdateAsk() {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val worn = lease.worn ?: return
    var newer by remember(worn.key, worn.ver) { mutableStateOf<Pair<Recipe, Int>?>(null) }
    var trying by remember { mutableStateOf(false) }
    LaunchedEffect(worn.key, worn.ver) {
        c.wardrobe.syncShared()
        val v = c.wardrobe.newerWorn() ?: return@LaunchedEffect
        val doc = runCatching { c.recipeApi.show(worn.code, null, v) }.getOrNull() ?: return@LaunchedEffect
        runCatching { RecipeCodec.read(doc.body) }.getOrNull()?.let { newer = it to v }
    }
    val (r, v) = newer ?: return
    fun update() = c.appScope.launch {
        val ok = c.wardrobe.updateWorn(r, v)
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            Toast.makeText(context, if (ok) "‘${worn.title}’을(를) 새 판으로 바꿨어요. 7일 안에 이전 판으로 돌아갈 수 있어요" else "바꾸지 못했어요. 다시 해 주세요",
                Toast.LENGTH_LONG).show()
        }
    }
    if (trying) TryOnScreen(r, c.settingsRepository.current.workStyle, onDismiss = { trying = false }, onKeep = null) { _ ->
        Button(onClick = { trying = false; newer = null; update() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("새 판으로 바꾸기") }
    } else AlertDialog(
        onDismissRequest = { newer = null },
        title = { Text("‘${worn.title}’ 테마가 업데이트됐어요. 바꿀까요?") },
        text = { Text("만든 분이 ${v}판을 올렸어요. 누르기 전에는 지금 모양 그대로예요. 기간은 그대로이고 광고도 없어요. 기록도 그대로예요") },
        confirmButton = {
            Row {
                TextButton(onClick = { trying = true }) { Text("바뀐 모습 보기") }
                TextButton(onClick = { newer = null; update() }) { Text("업데이트") }
            }
        },
        dismissButton = { TextButton(onClick = { c.wardrobe.skip(worn.code, v); newer = null }) { Text("지금 그대로") } },
    )
}
