package com.bamnat.app.ui.studio

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.settings.ColorPick
import com.bamnat.core.recipe.CalendarTheme
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ThemeSeed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.semantics.Role

/**
 * [STU-30][STU-33] 1.36 한 색으로 앱 전체·스타일 복사 (스튜디오 극상 조사 S1·S4): what a scheme or a copied look does to the draft.
 * Kept apart from the screen so the app's unit tests can check it.
 */
internal object StudioColors {
    /**
     * [scheme] as the whole app's colours: '앱 전체' takes 바탕·카드·글자·강조 (its picture, dimming and emoji stay), 달력 색 takes 오늘·토·일
     * (공휴일 follows 일요일). With [allScreens] every screen's own 바탕·카드·글자·강조 go, so they follow '앱 전체' (their pictures stay).
     */
    fun withScheme(d: AppSettings, scheme: ThemeSeed.Scheme, allScreens: Boolean): AppSettings {
        val looks = ScreenLooks.decode(d.screenLooks)
        val all = (looks.looks[ScreenLooks.ALL] ?: ScreenLook()).copy(bg = scheme.bg, card = scheme.card, ink = scheme.ink, accent = scheme.accent)
        var next = looks.with(ScreenLooks.ALL, all)
        if (allScreens) next = followAll(next)
        val cal = CalendarTheme.decode(d.calTheme).copy(today = scheme.today, sat = scheme.sat, sun = scheme.sun, holiday = null)
        return d.copy(screenLooks = next.encode(), calTheme = cal.encode())
    }

    /** Every screen but '앱 전체' without its own colours (its picture stays), so it follows '앱 전체'. */
    fun followAll(looks: ScreenLooks): ScreenLooks = looks.looks.keys.filter { it != ScreenLooks.ALL }.fold(looks) { acc, k ->
        acc.with(k, (acc.looks[k] ?: ScreenLook()).copy(bg = null, card = null, ink = null, accent = null))
    }

    /** The screens (not '앱 전체') with colours of their own. */
    fun ownColours(looks: ScreenLooks): List<String> = looks.looks.filter { (k, l) ->
        k != ScreenLooks.ALL && (l.bg != null || l.card != null || l.ink != null || l.accent != null)
    }.keys.toList()

    /** S4 이 화면 색을 모든 화면에: [key]'s colours (as drawn, over '앱 전체') become '앱 전체', and every screen follows them. */
    fun toAllScreens(d: AppSettings, key: String): AppSettings {
        val looks = ScreenLooks.decode(d.screenLooks)
        val shown = looks.of(key)
        val all = (looks.looks[ScreenLooks.ALL] ?: ScreenLook()).copy(bg = shown.bg, card = shown.card, ink = shown.ink, accent = shown.accent)
        return d.copy(screenLooks = followAll(looks.with(ScreenLooks.ALL, all)).encode())
    }

    /** S4 붙이기: [look]'s colours (and its picture, if it has one) on [key]; its emoji only on 마을. */
    fun paste(d: AppSettings, key: String, look: ScreenLook): AppSettings {
        val looks = ScreenLooks.decode(d.screenLooks)
        val now = looks.looks[key] ?: ScreenLook()
        val next = if (key == ScreenLooks.PLAZA) now.copy(bg = look.bg, photo = look.photo ?: now.photo, dim = look.dim ?: now.dim)
            else now.copy(bg = look.bg, card = look.card, ink = look.ink, accent = look.accent, photo = look.photo ?: now.photo, dim = look.dim ?: now.dim)
        return d.copy(screenLooks = looks.with(key, next).encode())
    }

    /** S4 앱 전체 색 따르기: [key] without colours of its own. */
    fun clearOwn(d: AppSettings, key: String): AppSettings {
        val looks = ScreenLooks.decode(d.screenLooks)
        return d.copy(screenLooks = looks.with(key, (looks.looks[key] ?: ScreenLook()).copy(bg = null, card = null, ink = null, accent = null)).encode())
    }

    /** [STU-39] The screen keys ([ScreenLooks.KEYS]) a tab's look lives in. */
    fun screensOf(tab: String): List<String> = when (tab) {
        com.bamnat.core.layout.TabOrder.CALENDAR -> listOf("calendar")
        com.bamnat.core.layout.TabOrder.MONEY -> listOf("money")
        com.bamnat.core.layout.TabOrder.MEMOS -> listOf("memo")
        com.bamnat.core.layout.TabOrder.STATS -> listOf("stats", "pay") + ScreenLooks.PAY_STYLES
        com.bamnat.core.layout.TabOrder.PLAZA -> listOf(ScreenLooks.PLAZA)
        com.bamnat.core.layout.TabOrder.SETTINGS -> listOf("settings", ScreenLooks.ALARM)
        else -> emptyList()
    }

    /** [STU-39] The tabs that can be locked, in the order shown. */
    val LOCKABLE = listOf(com.bamnat.core.layout.TabOrder.CALENDAR, com.bamnat.core.layout.TabOrder.MONEY, com.bamnat.core.layout.TabOrder.MEMOS,
        com.bamnat.core.layout.TabOrder.STATS, com.bamnat.core.layout.TabOrder.PLAZA, com.bamnat.core.layout.TabOrder.SETTINGS)

    /**
     * [STU-39] 잠근 탭: [next] with [tab] looking as it did in [prev]. Its screens keep the colours they were drawn with (also those that
     * came from '앱 전체', so a new '앱 전체' colour does not reach them) and their own pictures; 달력 keeps its 달력 색·칸·그림, 메모·가계부·급여
     * their own looks.
     */
    fun hold(tab: String, next: AppSettings, prev: AppSettings, dark: Boolean = false): AppSettings {
        val was = ScreenLooks.decode(prev.screenLooks)
        var now = ScreenLooks.decode(next.screenLooks)
        // A colour the screen took from the app's own theme (none set) is written out as that theme's, or the new '앱 전체' colour
        // would come through (none means 'follow 앱 전체').
        val app = if (dark) com.bamnat.app.ui.theme.DarkColors else com.bamnat.app.ui.theme.LightColors
        fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb().toLong() and 0xFFFFFFFFL
        screensOf(tab).forEach { k ->
            val seen = was.of(k)
            val before = seen.copy(bg = seen.bg ?: argb(app.background), card = seen.card ?: argb(app.surfaceContainerLow),
                ink = seen.ink ?: argb(app.onSurface), accent = seen.accent ?: argb(app.primary))
            val after = now.of(k)
            if (seen.bg != after.bg || seen.card != after.card || seen.ink != after.ink || seen.accent != after.accent ||
                seen.photo != after.photo || seen.dim != after.dim) {
                val own = was.looks[k] ?: ScreenLook()
                now = now.with(k, own.copy(bg = before.bg, card = before.card, ink = before.ink, accent = before.accent,
                    photo = own.photo, dim = own.dim))
            }
        }
        val d = next.copy(screenLooks = now.encode())
        return when (tab) {
            com.bamnat.core.layout.TabOrder.CALENDAR -> d.copy(calTheme = prev.calTheme, calView = prev.calView, photoLook = prev.photoLook,
                calendarLayout = prev.calendarLayout)
            com.bamnat.core.layout.TabOrder.MONEY -> d.copy(moneyLook = prev.moneyLook, moneyLayout = prev.moneyLayout)
            com.bamnat.core.layout.TabOrder.MEMOS -> d.copy(memoLook = prev.memoLook, memoLayout = prev.memoLayout)
            com.bamnat.core.layout.TabOrder.STATS -> d.copy(payLooks = prev.payLooks, statsLayout = prev.statsLayout)
            else -> d
        }
    }

    /**
     * PC 확인 1.36 (달력을 잠갔는데 한 색 뒤 바꾼 곳에 '달력 색·배경'이 나옴): [hold] writes a locked screen's colours out, so its look is
     * stored differently but drawn the same. True when [tab] draws in [a] as in [b] — the same colours once 'follow 앱 전체' and the app's
     * own are filled in, the same pictures, and the tab's own looks.
     */
    fun sameLook(tab: String, a: AppSettings, b: AppSettings, dark: Boolean = false): Boolean {
        val app = if (dark) com.bamnat.app.ui.theme.DarkColors else com.bamnat.app.ui.theme.LightColors
        fun argb(c: androidx.compose.ui.graphics.Color) = c.toArgb().toLong() and 0xFFFFFFFFL
        val la = ScreenLooks.decode(a.screenLooks)
        val lb = ScreenLooks.decode(b.screenLooks)
        fun drawn(l: ScreenLooks, k: String) = l.of(k).let { x ->
            listOf(x.bg ?: argb(app.background), x.card ?: argb(app.surfaceContainerLow), x.ink ?: argb(app.onSurface), x.accent ?: argb(app.primary), x.photo, x.dim)
        }
        if (screensOf(tab).any { k -> drawn(la, k) != drawn(lb, k) }) return false
        return when (tab) {
            com.bamnat.core.layout.TabOrder.CALENDAR -> a.calTheme == b.calTheme && a.calView == b.calView && a.photoLook == b.photoLook &&
                a.calendarLayout == b.calendarLayout
            com.bamnat.core.layout.TabOrder.MONEY -> a.moneyLook == b.moneyLook && a.moneyLayout == b.moneyLayout
            com.bamnat.core.layout.TabOrder.MEMOS -> a.memoLook == b.memoLook && a.memoLayout == b.memoLayout
            com.bamnat.core.layout.TabOrder.STATS -> a.payLooks == b.payLooks && a.statsLayout == b.statsLayout
            else -> true
        }
    }

    /** Where the seed starts: '앱 전체' 강조, else 오늘 색, else the app's blue. */
    fun seedOf(d: AppSettings): Long = ScreenLooks.decode(d.screenLooks).looks[ScreenLooks.ALL]?.accent
        ?: CalendarTheme.decode(d.calTheme).today ?: ThemeSeed.DEFAULT_SEED

    /** Colours to start from in one tap: a wheel of 12 that each make a good scheme. */
    val SEEDS = listOf(0xFFE5484DL, 0xFFF2994AL, 0xFFF2C94CL, 0xFF27AE60L, 0xFF00857AL, 0xFF00A3C4L, 0xFF2F5BEAL, 0xFF4F6BEDL,
        0xFF9B51E0L, 0xFFE84393L, 0xFF8D6E63L, 0xFF607D8BL)

    /** [STU-31] A small copy of a picture's pixels (about 64 px across), read off the main thread. */
    suspend fun pixels(context: Context, uri: Uri): IntArray? = withContext(Dispatchers.IO) {
        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            var sample = 1
            while (bounds.outWidth / (sample * 2) >= 64 && bounds.outHeight / (sample * 2) >= 64) sample *= 2
            val bm = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
            bm?.let { pixels(it) }
        }.getOrNull()
    }

    fun pixels(bm: Bitmap): IntArray {
        val small = if (bm.width > 96 || bm.height > 96) Bitmap.createScaledBitmap(bm, 64, (64f * bm.height / bm.width).toInt().coerceAtLeast(1), true) else bm
        return IntArray(small.width * small.height).also { small.getPixels(it, 0, small.width, 0, 0, small.width, small.height) }
    }
}

/**
 * [STU-30][STU-31][STU-32] 한 색 도구: pick one colour (a wheel of 12, colours from a picture, or any colour) and the whole app's colours
 * follow in a [ThemeSeed.Variant], light or dark; 섞기 tries other places for the same colours. Every pick shows at once on the example and
 * the picks in a row are one step of 되돌리기 ([StudioViewModel.changeMerged]).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SeedTool(vm: StudioViewModel, d: AppSettings) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var seed by rememberSaveable { mutableLongStateOf(StudioColors.seedOf(d)) }
    // PC 확인 S7 ('밤처럼'으로 시작했는데 '부드럽게·밝은 화면'이 골라져 있음): the start's 느낌, else what the colours are now.
    // PC 확인 L3 (빨강을 고르니 '차분하게'가 골라져 있음): the start's 느낌 is taken once, then forgotten.
    val feel = remember { vm.seedFeel.also { vm.seedFeel = null } }
    var variant by rememberSaveable { mutableStateOf(feel?.first ?: ThemeSeed.Variant.SOFT) }
    var dark by rememberSaveable { mutableStateOf(feel?.second ?: (ScreenLooks.decode(d.screenLooks).looks[ScreenLooks.ALL]?.bg
        ?.let { com.bamnat.core.recipe.Palette.luminance(it) < 0.3 } ?: false)) }
    var mix by rememberSaveable { mutableIntStateOf(0) }
    var photoColours by remember { mutableStateOf<List<Long>>(emptyList()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val own = remember(d.screenLooks) { StudioColors.ownColours(ScreenLooks.decode(d.screenLooks)) }
    var allScreens by rememberSaveable { mutableStateOf(true) }
    val scheme = ThemeSeed.shuffle(seed, variant, dark, mix)

    fun put(s: Long = seed, v: ThemeSeed.Variant = variant, dk: Boolean = dark, m: Int = mix, all: Boolean = allScreens) {
        if (v != variant) vm.feelNow = vm.state.value.origin.title to v.label
        seed = s; variant = v; dark = dk; mix = m; allScreens = all
        vm.changeMerged(LABEL) { StudioColors.withScheme(it, ThemeSeed.shuffle(s, v, dk, m), all) }
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            val px = StudioColors.pixels(context, uri)
            if (px == null) { problem = "사진을 읽지 못했어요. 다른 사진을 골라 주세요"; return@launch }
            problem = null
            photoColours = ThemeSeed.fromPixels(px)
            put(s = photoColours.first(), m = 0)
        }
    }
    val bgPhoto = remember(d.screenLooks) { ScreenLooks.decode(d.screenLooks).looks[ScreenLooks.ALL]?.photo }

    // PC 확인 L1 (잠그기가 한 색 칸 맨 아래라 한참 내려야 보임): 잠그기 at the top, before the colours it holds back.
    LockChips(vm)
    Text("색 하나만 고르면 바탕·카드·글자·강조·오늘·주말 색이 어울리게 맞춰져요. 글자는 늘 잘 읽히게 맞춰요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("색 고르기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    com.bamnat.app.ui.components.FoldRow("studio.seeds", what = "색") {
        (photoColours + StudioColors.SEEDS.filter { it !in photoColours }).forEachIndexed { i, c ->
            if (i == photoColours.size && photoColours.isNotEmpty()) Box(Modifier.size(1.dp, 26.dp).background(MaterialTheme.colorScheme.outlineVariant))
            val on = c == seed
            Box(Modifier.size(36.dp).clip(CircleShape).background(Color(c.toInt()))
                .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                .clickable(role = Role.Button) { put(s = c, m = 0) }
                .semantics { contentDescription = (if (i < photoColours.size) "사진 색 ${i + 1}" else "색 ${i - photoColours.size + 1}"); selected = on })
        }
    }
    // PC 확인 S5 (느낌까지 두 번 밀어야 보임): 느낌 and 섞기 right under the colours, the photo and own pick after.
    Text("느낌", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val start = vm.feelStart?.takeIf { it.first == vm.state.value.origin.title }?.second
        ThemeSeed.Variant.entries.forEach { v -> FilterChip(selected = v == variant, onClick = { put(v = v) },
            label = { Text(if (v == start) "${v.label} · 처음" else v.label) }) }
        FilterChip(selected = !dark, onClick = { put(dk = false) }, label = { Text("밝은 화면") })
        FilterChip(selected = dark, onClick = { put(dk = true) }, label = { Text("어두운 화면") })
    }

    SchemeStrip(scheme)
    // PC 확인 S3 (노랑·연한 색은 바뀐 게 잘 안 보임): where the colour goes, said once under the strip.
    Text("들어가는 곳: 바탕 · 카드 · 글자 · 강조(단추·고른 탭·선택 칩) · 오늘 동그라미 · 토·일 글자", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // [STU-32] 섞기: the same colours in the next place; the whole run of picks goes back with one 되돌리기.
        Button(onClick = { put(m = mix + 1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("섞어 보기") }
        Text(ThemeSeed.SHUFFLES[Math.floorMod(mix, ThemeSeed.SHUFFLES.size)] + " · ${Math.floorMod(mix, ThemeSeed.SHUFFLES.size) + 1}/${ThemeSeed.SHUFFLES.size}",
            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        OutlinedButton(onClick = { runCatching { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
            modifier = Modifier.heightIn(min = 48.dp)) { Text("사진에서 색 뽑기") }
        if (bgPhoto != null) TextButton(onClick = {
            scope.launch {
                val bm = withContext(Dispatchers.IO) { com.bamnat.app.recipe.LookImages.bitmap(context, bgPhoto) }
                if (bm == null) { problem = "배경 사진을 읽지 못했어요"; return@launch }
                photoColours = ThemeSeed.fromPixels(StudioColors.pixels(bm))
                put(s = photoColours.first(), m = 0)
            }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("배경 사진에서") }
    }
    problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    // PC 제안 1.36 (사진에서 뽑은 색이 무엇인지 모름): the colours the picture gave, named, right under the button, each to pick.
    if (photoColours.isNotEmpty()) {
        val names = remember(photoColours) { com.bamnat.app.ui.settings.colorNames(photoColours) }
        Text("사진에서 뽑은 색 ${photoColours.size}개 · 눌러서 골라요", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        com.bamnat.app.ui.components.FoldRow("studio.photocolours", spacing = 10.dp, what = "사진 색") {
            photoColours.forEachIndexed { i, c ->
                val on = c == seed
                Column(Modifier.clickable(role = Role.Button) { put(s = c, m = 0) }.semantics { contentDescription = "사진 색 ${names[i]}"; selected = on },
                    horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(Color(c.toInt()))
                        .border(if (on) 3.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape))
                    Text(names[i], style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
    ColorPick("직접 고르기", seed, allowNone = false) { c -> c?.let { put(s = it, m = 0) } }

    // PC 확인 1.36 (달력을 잠갔는데 '따로 고른 색 1곳(달력)' 스위치가 켜져 있어 잠금과 헷갈림): a locked screen is not one of them; it says so.
    val heldKeys = vm.locked.flatMap { StudioColors.screensOf(it) }.toSet()
    val free = own.filter { it !in heldKeys }
    if (vm.locked.isNotEmpty()) Text(vm.locked.joinToString("·") { lockName(it, vm.style) }.let { "🔒 $it" + com.bamnat.app.ui.Fmt.eun(it) } + " 잠가서 그대로예요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (free.isNotEmpty()) Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { put(all = !allScreens) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("화면마다 따로 고른 색도 이 색으로", style = MaterialTheme.typography.bodyMedium)
            Text("${free.size}곳(${free.joinToString("·") { screenName(it) }})의 색이 따로 있어요. 끄면 그 화면들은 그대로예요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(allScreens, { put(all = it) }, modifier = Modifier.semantics { contentDescription = "화면마다 따로 고른 색도 이 색으로" })
    }
    Text("마음에 안 들면 위의 되돌리기 한 번으로 고르기 전으로 돌아가요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The scheme's colours side by side with their names, the text on its ground as it will read. */
@Composable
internal fun SchemeStrip(s: ThemeSeed.Scheme) {
    Box(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).background(Color(s.bg.toInt())).padding(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).background(Color(s.card.toInt())).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("카드 글자", color = Color(s.ink.toInt()), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("토", color = Color(s.sat.toInt()), fontWeight = FontWeight.Bold)
                Text("일", color = Color(s.sun.toInt()), fontWeight = FontWeight.Bold)
                Box(Modifier.size(26.dp).clip(CircleShape).background(Color(s.today.toInt())), contentAlignment = Alignment.Center) {
                    Text("9", color = Color(Palette.inkOn(null, s.today).toInt()), style = MaterialTheme.typography.labelMedium)
                }
                Box(Modifier.clip(MaterialTheme.shapes.small).background(Color(s.accent.toInt())).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("단추", color = Color(Palette.inkOn(null, s.accent).toInt()), style = MaterialTheme.typography.labelMedium)
                }
            }
            Text("바탕 글자", color = Color(Palette.inkOn(s.ink, s.bg).toInt()), style = MaterialTheme.typography.labelMedium)
        }
    }
}

/**
 * [STU-33] S4 스타일 복사: under a screen's 색·배경, its colours to every screen, copied to paste on another screen, or back to
 * following '앱 전체'.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ScreenCopyRow(vm: StudioViewModel, d: AppSettings, key: String, onSeed: () -> Unit) {
    val looks = remember(d.screenLooks) { ScreenLooks.decode(d.screenLooks) }
    val mine = looks.looks[key]
    val copied = vm.copiedLook
    Text("한 번에 하기", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (key != ScreenLooks.ALL && key != ScreenLooks.PLAZA) TextButton(onClick = {
            vm.changeMerged("${screenName(key)} 색을 모든 화면에") { StudioColors.toAllScreens(it, key) }
            // PC 제안 1.36 (눌러도 안내가 없음): said, with what a lock kept as it was.
            vm.say("‘${screenName(key)}’ 색을 모든 화면에 넣었어요" + vm.locked.takeIf { it.isNotEmpty() }
                ?.joinToString("·") { lockName(it, vm.style) }?.let { " · 🔒 $it" + com.bamnat.app.ui.Fmt.eun(it) + " 그대로" }.orEmpty())
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이 색을 모든 화면에") }
        if (key == ScreenLooks.ALL) {
            val own = StudioColors.ownColours(looks)
            if (own.isNotEmpty()) TextButton(onClick = {
                vm.changeMerged("화면마다 따로 고른 색 지우기") { it.copy(screenLooks = StudioColors.followAll(ScreenLooks.decode(it.screenLooks)).encode()) }
            }, modifier = Modifier.heightIn(min = 48.dp)) { Text("모든 화면을 이 색으로 (${own.size}곳 따로 고른 색 지우기)") }
        }
        TextButton(onClick = { vm.copiedLook = key to looks.of(key); vm.say("‘${screenName(key)}’ 색을 복사했어요. 다른 화면의 색·배경에서 붙여요") },
            modifier = Modifier.heightIn(min = 48.dp)) { Text("복사") }
        if (copied != null && copied.first != key) TextButton(onClick = {
            // PC 제안 1.36 (같은 색이면 붙여도 아무 말이 없음): said either way.
            val c2 = copied.second; val here = looks.of(key)
            if (c2.bg == here.bg && c2.card == here.card && c2.ink == here.ink && c2.accent == here.accent && c2.photo == here.photo) {
                vm.say(screenName(key).let { it + com.bamnat.app.ui.Fmt.eun(it) } + " 이미 ‘${screenName(copied.first)}’ 색과 같아요")
            } else {
                vm.changeMerged("‘${screenName(copied.first)}’ 색을 ${screenName(key)}에 붙이기") { StudioColors.paste(it, key, c2) }
                vm.say("‘${screenName(copied.first)}’ 색을 ${screenName(key)}에 붙였어요 · 위 되돌리기로 돌아가요")
            }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("붙이기 (‘${screenName(copied.first)}’ 색)") }
        if (key != ScreenLooks.ALL && mine != null && (mine.bg != null || mine.card != null || mine.ink != null || mine.accent != null)) TextButton(onClick = {
            vm.changeMerged("${screenName(key)} 앱 전체 색 따르기") { StudioColors.clearOwn(it, key) }
        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("앱 전체 색 따르기") }
        TextButton(onClick = onSeed, modifier = Modifier.heightIn(min = 48.dp)) { Text("한 색으로 앱 전체 ›") }
    }
}

/** A screen key ([ScreenLooks.KEYS], pay.<style>) as people say it. */
internal fun screenName(key: String): String = when (key.substringBefore('.')) {
    ScreenLooks.ALL -> "앱 전체"; "calendar" -> "달력"; "money" -> "가계부"; "memo" -> "메모"; "stats" -> "통계"; "pay" -> "급여"
    "plaza" -> "마을"; "settings" -> "설정"; "alarm" -> "알람 화면"; "guarded" -> "결제·처리방침"; else -> key
}

private const val LABEL = "앱 전체 · 한 색"
