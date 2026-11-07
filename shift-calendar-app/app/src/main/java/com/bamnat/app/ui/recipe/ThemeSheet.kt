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
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
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
 * [FREE-09][FREE-10] 1.35 지금 입은 테마 (사용자 2026-10-30 21:37), kept as the top of [ThemeDrawer] in 1.37: what the whole app wears
 * now, drawn as itself, with its days, 벗기 and 내 모양 고치기; under it the tabs laid on ([MKX-02]), each to take off. 벗기 and 빼기
 * say so with 되돌리기 ([DRW-03]). No record changes either way.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun WornNow(onFix: (() -> Unit)?) {
    val c = LocalContext.current.appContainer
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    var takingOff by remember { mutableStateOf(false) }
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
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                    TextButton(onClick = {
                        c.appScope.launch {
                            val m = c.wardrobe.mark()
                            c.wardrobe.removeOverlay(o.tab)
                            com.bamnat.app.ui.PutOnNotice.say("‘${com.bamnat.core.recipe.LookParts.tabLabel(o.tab)}’ 탭에서 ‘${o.title}’${com.bamnat.app.ui.Fmt.obj(o.title)} 뺐어요") {
                                c.wardrobe.undoTo(m); true
                            }
                        }
                    }) { Text("빼기") }
                }
            }
            var keep by remember { mutableStateOf(c.wardrobe.keepOverlays) }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("다른 테마를 입어도 얹은 탭은 그대로", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                androidx.compose.material3.Switch(keep, { keep = it; c.wardrobe.keepOverlays = it })
            }
        }
    }
    if (takingOff) AlertDialog(
        onDismissRequest = { takingOff = false },
        title = { Text("테마를 벗을까요?") },
        text = { Text("입기 전 내 모양으로 돌아가요. 입었던 것에 남아서 다시 입을 수 있고, 테마를 입은 채 고친 것은 ‘내가 고친 …’으로 따로 남아요") },
        confirmButton = { TextButton(onClick = {
            takingOff = false
            val title = lease.worn?.title.orEmpty()
            c.appScope.launch {
                val m = c.wardrobe.mark()
                c.wardrobe.takeOff()
                com.bamnat.app.ui.PutOnNotice.say("‘$title’${com.bamnat.app.ui.Fmt.obj(title)} 벗었어요. 내 모양으로 돌아왔어요") { c.wardrobe.undoTo(m); true }
            }
        }) { Text("벗기") } },
        dismissButton = { TextButton(onClick = { takingOff = false }) { Text("닫기") } },
    )
}

/**
 * [FREE-08][FREE-10] One theme: 입어 보기 on the example phone, every tab a tap away, then 입기. Someone else's theme past the free ones
 * asks for a rewarded ad watched to the end; mine, 프로 and the ads switch off never do.
 */
@Composable
fun ThemeSheet(r: Recipe, code: String, style: WorkStyle?, ver: Int = 0,
               /** [TRY-05] Opened again after trying it on the real app: the parts ticked and the tab shown then. */
               again: LiveTryOn.Reopen? = null, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    var note by remember { mutableStateOf<String?>(null) }
    val own = code.isEmpty()
    // [THM-01] Mine kept to myself asks as others' do; mine in 장터 never.
    val needsAd = remember { c.wardrobe.needsAdFor(code, r.title) }
    val all = remember(r) { com.bamnat.core.recipe.LookParts.of(r).map { it.id }.toSet() }
    // [J135-15] Worn on the app's scope: the look changing redraws the screens under this sheet, and a coroutine of the sheet would be
    // cancelled half way (the look on, 입는 중 never written). [TRY-01] Only the parts ticked; the rest stays the user's own.
    fun wear(picked: Set<String>, after: () -> Unit = {}) = c.appScope.launch {
        val whole = picked.containsAll(all)
        val worn = if (whole) r else com.bamnat.core.recipe.LookParts.pick(r, picked).copy(title = "${r.title} 일부".take(30))
        // [DRW-03] 1.37 넣었어요 · 되돌리기: what was worn just before, so the line under it takes this one back.
        val m = c.wardrobe.mark()
        if (c.wardrobe.wear(worn, code, own, ver, whole = r.takeIf { !whole })) {
            after()
            // The sheet closes first, so the line with 되돌리기 is not under it.
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { onDismiss() }
            // PC 3차 (얹은 탭이 같은 곳을 덮으면 일부만 입은 게 안 보여요): the tabs laid on over what was just put on are named.
            val tabs = com.bamnat.core.recipe.LookParts.of(worn).map { it.tab }.toSet()
            val under = c.wardrobe.overlays.value.filter { it.tab in tabs }
            val hidden = if (under.isEmpty()) "" else ". " + under.joinToString(", ") { "‘${com.bamnat.core.recipe.LookParts.tabLabel(it.tab)}’" } +
                " 탭은 얹은 탭이 덮고 있어 그대로 보여요. 마을 › 테마 › 테마 위에 얹은 탭에서 빼면 보여요"
            com.bamnat.app.ui.PutOnNotice.say((if (whole) "‘${r.title}’${com.bamnat.app.ui.Fmt.obj(r.title)} 입었어요" else "‘${r.title}’에서 고른 ${picked.size}개를 입었어요") +
                hidden.ifEmpty { ". 기록은 그대로예요" }) { c.wardrobe.undoTo(m); true }
        }
    }
    TryOnScreen(r, style, onDismiss,
        onKeep = { ids -> c.wardrobe.keep(r, code, ids.toList()) },
        // [TRY-05] 1.39 실제 화면으로 입어 보기: the sheet closes; 끝내기 opens it again over the screen it was on.
        live = { picked, tab -> LiveTryOn.start(c, r, own, picked, tab, LiveTryOn.From.Sheet(r, code, ver)).also { if (it) onDismiss() } },
        startPicked = again?.picked, startTab = again?.tab,
    ) { picked ->
        note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        // [TAB-05] PC 확인 bf62e966 사용성 제안: before wearing a 전문 달력, one line on how the 달력·메모·가계부 will be laid out.
        r.pieces.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.TAB_VIEW }?.let { tv ->
            val keep = buildSet {
                if ("cal.board" in picked) add("cal")
                if ("memo.view" in picked) addAll(listOf("memo", "memoBy", "memoSort"))
                if ("money.board" in picked) add("money")
            }
            val shown = tv.copy(values = tv.values.filterKeys { it in keep })
            if (shown.values.isNotEmpty()) Text("입으면 바뀌는 판: ${com.bamnat.core.recipe.RecipeDescribe.line(shown)}. 탭마다 보기 단추에서 언제든 바꿔요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
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
                    // 1.36.1: today's two ads are used, so the theme stays on and the ask comes back tomorrow.
                    onCappedLater = { shown = false; c.wardrobe.later() },
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
            Toast.makeText(context, if (ok) "‘${worn.title}’${com.bamnat.app.ui.Fmt.obj(worn.title)} 새 판으로 바꿨어요. 7일 안에 이전 판으로 돌아갈 수 있어요" else "바꾸지 못했어요. 다시 해 주세요",
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
