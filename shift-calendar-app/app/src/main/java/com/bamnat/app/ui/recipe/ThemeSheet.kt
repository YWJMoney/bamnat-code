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
 * [FREE-09][FREE-10] 1.35 테마 고르기: the theme worn now and its days, the eight 추천 테마 of this kind of work drawn as themselves, and
 * 입었던 테마. A tap opens [ThemeSheet] (입어 보기, 입기). 벗기 always comes back to the user's own look; no record changes either way.
 */
@Composable
fun ThemePicker(style: WorkStyle?, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf<Pair<Recipe, String>?>(null) }
    var takingOff by remember { mutableStateOf(false) }
    val themes = remember(style) { BuiltInThemes.of(style) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lease.worn?.let { w ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                val left = lease.daysLeft(Today.date().toEpochDay())
                val runs = c.wardrobe.state() != ThemeLease.State.NONE
                Text("지금 입은 테마: ${w.title}" + if (runs && left != null) " · ${left}일 남음" else "",
                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { takingOff = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("벗기") }
            }
        }
        Text("추천 테마 · " + (style?.let { com.bamnat.core.recipe.RecipeDescribe.short(it) } ?: "교대"), style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.semantics { heading() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            themes.forEach { r ->
                Column(Modifier.width(112.dp).clickable(onClickLabel = "테마 보기") { open = r to "builtin:${r.title}" },
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    swatchOf(r)?.let { ThemeThumb(it, width = 112.dp) }
                    Text(r.title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        val past = lease.history.filter { it.key != lease.worn?.key }
        if (past.isNotEmpty()) {
            Text("입었던 테마", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
            past.take(10).forEach { w ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "다시 보기") {
                    runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let { open = it to w.code }
                }, verticalAlignment = Alignment.CenterVertically) {
                    Text((if (w.own) "🎨 " else "") + w.title, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text(if (w.own) "내 테마" else "다시 입기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        // [FREE-10] 내 테마로 저장: free one, 프로 ten (꾸미기 혜택).
        var saveNote by remember { mutableStateOf<String?>(null) }
        val mineCount = lease.history.count { it.own }
        val limit = c.wardrobe.outfitLimit()
        OutlinedButton(onClick = {
            scope.launch { saveNote = if (kotlinx.coroutines.withContext(c.appScope.coroutineContext) { c.wardrobe.saveMine("내 테마 ${mineCount + 1}") }) "지금 모양을 ‘내 테마 ${mineCount + 1}’로 저장했어요" else "더 저장할 수 없어요" }
        }, enabled = mineCount < limit, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("지금 모양을 내 테마로 저장 ($mineCount/$limit)") }
        Text(saveNote ?: if (limit <= com.bamnat.app.recipe.ThemeWardrobe.FREE_OUTFITS) "무료는 ${limit}벌까지예요. 프로는 ${com.bamnat.app.recipe.ThemeWardrobe.PRO_OUTFITS}벌까지 저장해 바꿔 입어요"
            else "내 테마는 기간 없이 언제든 다시 입어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/**
 * [FREE-08][FREE-10] One theme: 입어 보기 on the example phone, every tab a tap away, then 입기. Someone else's theme past the free ones
 * asks for a rewarded ad watched to the end; mine, 프로 and the ads switch off never do.
 */
@Composable
fun ThemeSheet(r: Recipe, code: String, style: WorkStyle?, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val context = LocalContext.current
    var tab by remember { mutableStateOf(TabOrder.CALENDAR) }
    var note by remember { mutableStateOf<String?>(null) }
    val own = code.isEmpty()
    val needsAd = remember { c.wardrobe.needsAd(own) }
    // [J135-15] Worn on the app's scope: the look changing redraws the screens under this sheet, and a coroutine of the sheet would be
    // cancelled half way (the look on, 입는 중 never written).
    fun wear(after: () -> Unit = {}) = c.appScope.launch {
        if (c.wardrobe.wear(r, code, own)) {
            after()
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                Toast.makeText(context.applicationContext, "‘${r.title}’을(를) 입었어요. 기록은 그대로예요", Toast.LENGTH_SHORT).show()
                onDismiss()
            }
        }
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxWidth().padding(12.dp), shape = MaterialTheme.shapes.extraLarge) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (r.summary.isNotEmpty()) Text(r.summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("입어 보기: 아래 탭을 눌러 화면마다 봐요", style = MaterialTheme.typography.labelMedium)
                MockPhone(r, style, tab, onTab = { tab = it })
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                if (needsAd) com.bamnat.app.ads.ThemeAdButton(
                    "광고 보고 입기", "광고를 끝까지 보면 30일 동안 입어요. 프로는 광고 없이 입어요",
                    onReward = { wear() },
                    onQuit = { note = "광고를 끝까지 봐야 입을 수 있어요" },
                    onOffline = { note = "인터넷에 연결되면 다시 해 주세요" },
                    onNoAd = { reason -> wear { c.wardrobe.counted(reason) } },
                ) else Button(onClick = { wear() }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("이 테마 입기") }
                OutlinedButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("닫기") }
            }
        }
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
