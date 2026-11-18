package com.bamnat.app.ui.theme

import com.bamnat.app.ui.decor.ScreenDecor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.layout.ContentScale
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.work.WorkStyle

/** [FREE-05] Every screen's colours and background, as the settings keep them; MainActivity provides the user's. */
val LocalScreenLooks = staticCompositionLocalOf { ScreenLooks() }

/** [FREE-04] 1.35 메모장 모양. */
val LocalMemoLook = staticCompositionLocalOf { com.bamnat.core.recipe.MemoLook() }

/** [FREE-06] 1.35 가계부 모양. */
val LocalMoneyLook = staticCompositionLocalOf { com.bamnat.core.recipe.MoneyLook() }

/** [FREE-07] 1.35 급여 모양 by kind of work. */
val LocalPayLooks = staticCompositionLocalOf { emptyMap<WorkStyle, com.bamnat.core.recipe.PayLook>() }

/** [FREE-05] The look of the screen being drawn (for a screen's own touches, such as 광장's emoji). */
val LocalScreenLook = staticCompositionLocalOf { ScreenLook() }

/**
 * [FREE-05] Which screen's look a route takes: the tab it belongs to; 급여 by the calendar's kind of work; 결제·처리방침 the guarded one
 * (colours and a background only, like 알람 끄기, which its own activity draws with [SCREEN_ALARM]).
 */
fun screenKeyOf(route: String?, style: WorkStyle?): String {
    val r = route.orEmpty().substringBefore('?').substringBefore('/')
    return when {
        r == "pay" -> "pay." + (style ?: WorkStyle.SHIFT).name
        r in setOf("money", "owed") -> "money"
        r in setOf("memos", "memo", "memo_trash") -> "memo"
        r in setOf("stats", "year", "health_report") -> "stats"
        r == "plaza" || r.startsWith("company") || r.startsWith("recipe") || r in setOf("style_plaza", "plaza_rates") -> "plaza"
        r in setOf("pro", "privacy", "consent", "policy") -> ScreenLooks.GUARDED
        // PC 확인 8d0cddf6 (스튜디오 제목·칩·저장 위에 화면 배경 사진의 점): the studio is where looks are made and tried in its own
        // example, so its own frame stays plain like 결제·처리방침, and nothing behind its words is mistaken for the look being made.
        r == "studio" -> ScreenLooks.GUARDED
        r == "calendar" || r.startsWith("calendar") || r in setOf("shift_types", "pattern", "work_style", "shop_days", "combined",
            "duty_roster", "duty_roster_month", "leave", "extra_kinds", "setup_again") -> "calendar"
        r.isEmpty() -> "calendar"
        else -> "settings"
    }
}

const val SCREEN_ALARM = ScreenLooks.ALARM

/**
 * [FREE-05] Draws [content] in screen [key]'s look: its background colour or picture (with a veil of the page's colour, so text reads),
 * its cards, text and accent as the Material colours under it, so every card, sheet and button of the screen follows. The text is
 * always readable: an ink that does not stand 4.5:1 on the background and the cards becomes black or white ([Palette.inkOn]).
 * 광장 takes only the background and its emoji; the error colours never change.
 */
@Composable
fun ScreenSkin(key: String, modifier: Modifier = Modifier, bars: Boolean = false,
    /** PC 확인 5c21f3bb: false for an editing screen (스튜디오), whose own words and bars the 꾸밈 층 would cover; its previews keep it. */
    decorated: Boolean = true, content: @Composable () -> Unit) {
    val all = LocalScreenLooks.current
    // 사용자 2026-11-01: 결제·처리방침·동의 화면은 어떤 테마에서도 앱 기본 모양 그대로 — 값과 약관이 늘 똑같이 또렷하게 읽혀요.
    val raw = if (key == ScreenLooks.GUARDED) ScreenLook() else all.of(key)
    // [MAT-01][MAT-03] 1.36 마을 takes a background fill and the picture's adjustment too (still only the background).
    val look = if (key == ScreenLooks.PLAZA) ScreenLook(bg = raw.bg, photo = raw.photo, dim = raw.dim, emoji = raw.emoji, fill = raw.fill, adjust = raw.adjust) else raw
    // PC 확인 W44 1.36 (어두운 테마에서 프로 화면만 갑자기 밝아 눈부심): the plain 결제·처리방침 look follows how dark the app is made.
    val outer = MaterialTheme.colorScheme
    val base = outer.let { b ->
        val allBg = all.of(ScreenLooks.ALL).bg
        if (key == ScreenLooks.GUARDED && allBg != null && Palette.luminance(allBg) < 0.3 && Palette.luminance(b.background.argb()) >= 0.5) DarkColors else b
    }
    val photo = rememberLookPicture(look.photo)
    val plain = look.isDefault
    // PC 확인 C4 1.36 (꾸밈 사진을 달력 화면 배경에 넣었는데 실제 화면에는 안 보여요): the screen's page and bars are opaque and covered
    // what the 꾸밈 층 drew under them. With a decoration under the words, the ground is drawn here once and the page above it is clear.
    val under = com.bamnat.app.ui.decor.LocalDecor.current.let { d ->
        val bg = "bg." + key.substringBefore('.')
        if (!decorated || d.isDefault || bg.removePrefix("bg.") !in com.bamnat.core.recipe.Decor.SCREENS) emptyList()
        else d.items.filter { (it.at == com.bamnat.core.recipe.Decor.APP || it.at == bg) && !it.over && !it.hide }
    }
    val decorUnder = under.isNotEmpty()
    // PC 확인 C7 1.36 (진한 사진 위에서 '2026년'·요일 줄·'모든 조'가 거의 안 읽혀요): a photo filling the screen under the words gets a
    // veil of the page's colour, as a 화면 배경 사진 does, so the screen's own ink reads on it.
    // PC 확인 C8 1.36 (55%면 진한 사진이 파스텔처럼 옅어져요): a lighter veil, and the words take their ink from how light the photo is,
    // as with a 화면 배경 사진 (light words on a dark photo), so the photo keeps its colour and the words still read.
    // PC 확인 C9: only once the photo is in; until then the screen keeps its own ground.
    val fullPhoto = under.firstOrNull { it.kind == com.bamnat.core.recipe.DecorKind.PHOTO && it.full && com.bamnat.app.ui.decor.DecorPhotos.of(it.glyph) != null }
    val photoUnder = fullPhoto != null
    val decorLight = if (photo == null) fullPhoto?.let { com.bamnat.app.ui.decor.DecorPhotos.lightOf(it.glyph) } else null
    // PC 확인 C12 1.36 (사진 위 파랑 글자 1.03:1): the photo's own average colour, not a stand-in grey, is what the inks are read against.
    // PC 확인 C16 1.36 (스튜디오 색·배경 '사진 고르기'로 깐 어두운 사진 위 '오늘'·요일·알림 1.3~2.3:1): the 화면 배경 사진 goes by the
    // same rule as a 꾸밈 사진 — its real average colour, cards on the ground's side, the softer words full ink, dates and buttons checked.
    val screenPhoto = photo != null && !plain
    val screenAvg = androidx.compose.runtime.remember(photo?.first) { photo?.first?.let { averageColour(it) } }
    val decorAvg = if (photo == null) fullPhoto?.let { com.bamnat.app.ui.decor.DecorPhotos.averageOf(it.glyph) } else screenAvg
    val veiled = if (photoUnder) look.copy(dim = look.dim ?: PHOTO_VEIL) else look
    val light = photo?.second ?: decorLight
    val inked = !plain || decorLight != null
    val onPhoto = (photoUnder && decorLight != null) || screenPhoto
    val photoGround = groundOf(base, veiled, light, decorAvg)
    val dark = Palette.luminance((if (!inked) base.background else photoGround).argb()) < if (!inked) 0.5 else 0.3
    // PC 확인 C13 1.36 ('오늘' 회색 글자 1.6:1): the words had to read on the app's light cards too, so a dark photo got dark words. On a
    // 꾸밈 사진 the cards take the ground's side (dark cards on a dark photo), unless the screen gives its own card colour.
    val pageColor = look.bg?.let { Color(it) } ?: base.background
    val photoCard = if (dark) lerp(pageColor, Color.Black, 0.8f) else lerp(pageColor, Color.White, 0.5f)
    val worn = if (onPhoto && veiled.card == null) veiled.copy(card = photoCard.argb()) else veiled
    val skin = if (!inked) base else skinned(base, worn, light, decorAvg)
    // PC 확인 C14 1.36 ('오늘' 옅은 글자 2.1:1): on a 꾸밈 사진 the softer words are the full ink, as a photo is lighter and darker in places.
    val scheme = (if (decorUnder) skin.copy(background = Color.Transparent, surface = Color.Transparent) else skin)
        .let { if (onPhoto && inked) it.copy(onSurfaceVariant = it.onSurface) else it }
    // 1.35 [J135-11] The status bar's clock and icons stand on the screen's own ground (dark icons on a dark theme were unreadable).
    if (bars) BarIcons(light = !dark)
    // 1.35 [J135-12] The warning cards and the money colours that do not follow the scheme take the light or dark set of the ground,
    // so a 허용 on an alarm warning card reads on a dark theme too.
    val cal = LocalCalendarColors.current
    val darkened = base !== outer
    val fitted = if (!inked && !darkened) cal else (if (dark) DarkCalendarColors else LightCalendarColors).let { tone ->
        cal.copy(warning = tone.warning, warningContainer = tone.warningContainer, income = tone.income, expense = tone.expense,
            alarmOff = tone.alarmOff, changed = tone.changed, holidayWork = tone.holidayWork)
    }
    // [J135-15] One shape of tree for the user's own look and for a theme: putting a theme on or taking it off only changes colours.
    // Two shapes threw away everything under the screen (an open sheet closed, 화면 꾸미기 fell back to 설정, a wear half done).
    // [MAT-05] 1.36 이 화면 글꼴: every text style of the screen in it (sizes and weights stay, 큰 글자 still grows them).
    val family = rememberFontFamily(look.font)
    // [N10] 1.36 진짜 유리: the background (colour, fill, picture and its veil) kept small for the glass parts of this screen to see.
    val holder = remember { BackdropHolder() }
    val density = androidx.compose.ui.platform.LocalDensity.current.density
    var placed by remember { mutableStateOf<Pair<IntSize, Offset>?>(null) }
    val veilColor = (look.ground?.let { Color(it) } ?: base.background).copy(alpha = maxOf((look.dim ?: 30).coerceIn(0, 80), 20) / 100f)
    val made = remember(plain, look, photo?.first, placed, pageColor, veilColor, density) {
        val p = placed
        if (plain || p == null) null else Backdrop.make(p.first, p.second, density) {
            drawRect(pageColor)
            look.fill?.let { paintFill(it, FillTiles.tile(it, this.density), null, 0f, 1f) }
            photo?.first?.let { img ->
                drawCropped(img, look.adjust)
                drawRect(veilColor)
            }
        }
    }
    SideEffect { holder.screen = made }
    // PC 확인 5c21f3bb (C절): words on a photo get a soft halo of the other side's colour, so a bright or dark spot behind them
    // cannot swallow them (the ink alone goes by the photo's average).
    val halo = if (onPhoto && inked) androidx.compose.ui.graphics.Shadow(
        color = if (dark) Color.Black.copy(alpha = 0.75f) else Color.White.copy(alpha = 0.85f), offset = Offset.Zero, blurRadius = 3f * density) else null
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography.inFamily(family).withHalo(halo), shapes = MaterialTheme.shapes) {
        Box((if (plain && !darkened && !decorUnder) modifier else modifier.background(pageColor).fillWith(look.fill))
            .onGloballyPositioned { c -> if (!plain) (c.size to c.positionInWindow()).let { if (it != placed) placed = it } }) {
            if (photo != null && !plain) {
                // [MAT-03] 1.36 사진 조정·블렌드: drawn through the look's colour matrix, blend and blur.
                if (look.adjust == null) Image(photo.first, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                else Box(Modifier.matchParentSize().adjustedPhoto(photo.first, look.adjust))
                val veil = (look.dim ?: 30).coerceIn(0, 80)
                Box(Modifier.matchParentSize().background((look.ground?.let { Color(it) } ?: base.background).copy(alpha = maxOf(veil, 20) / 100f)))
            }
            // [LAY-03] 1.36 꾸밈 층 on the screen's background: under its words, then (the maker's choice) over them.
            if (decorated) ScreenDecor(key, over = false)
            val page = look.bg?.let { Color(it) } ?: base.background
            if (photoUnder) Box(Modifier.matchParentSize().background(page.copy(alpha = (worn.dim ?: PHOTO_VEIL).coerceIn(20, 80) / 100f)))
            // [J135-12] Text drawn straight on the page (no card or bar of its own) takes the skin's ink, not the ink of what is around.
            // [BLU-12] PC 15차: on a ground of its own the parts' 어두운 화면 lines follow that ground (a white 화면 색 on a dark phone
            // keeps the light lines, so a light-grey 금액 meant for dark does not land on white).
            val raw = com.bamnat.app.ui.parts.LocalBlueprintRaw.current
            val grounded = if (!inked && !darkened) com.bamnat.app.ui.parts.LocalBlueprint.current else androidx.compose.runtime.remember(raw, dark) { raw.forDark(dark) }
            CompositionLocalProvider(LocalScreenLook provides look, LocalCalendarColors provides fitted, LocalBackdrop provides holder,
                // PC 확인 C10: the ground goes with the words' ink (dark under light words on a dark photo), so a pale cell never turns grey.
                com.bamnat.app.ui.decor.LocalOnPhoto provides if (!onPhoto) null else photoCard,
                com.bamnat.app.ui.decor.LocalPhotoGround provides if (onPhoto) photoGround else null,
                com.bamnat.app.ui.parts.LocalBlueprint provides (if (raw.isEmpty) com.bamnat.app.ui.parts.LocalBlueprint.current else grounded),
                androidx.compose.material3.LocalContentColor provides if (!inked && !darkened) androidx.compose.material3.LocalContentColor.current else scheme.onBackground) {
                content()
            }
            if (decorated) ScreenDecor(key, over = true)
        }
    }
}

/**
 * PC 확인 C7·C8 1.36: how much of the page (in %) lies over a 꾸밈 사진 that fills the screen under the words, unless the screen has its own
 * 흐리게; the words take their ink from the photo and this veil.
 */
const val PHOTO_VEIL = 40

/** [J135-11] The status and navigation bar icons dark ([light] ground) or light, on the window that draws this (a full-screen dialog's own). */
@Composable
fun BarIcons(light: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        var c = view.context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        ((view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window ?: (c as? android.app.Activity)?.window)?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}

/** The colour the screen's text stands on: the page, or what a picture and its veil come to. */
private fun groundOf(base: ColorScheme, look: ScreenLook, photoLight: Float?, photoAvg: Color? = null): Color {
    val page = look.ground?.let { Color(it) } ?: base.background
    if (photoLight == null) return page
    val veil = (look.dim ?: 30).coerceIn(20, 80) / 100f
    return lerp(photoAvg ?: if (photoLight >= 0.5f) Color(0xFFEDEDED) else Color(0xFF2A2A2A), page, veil)
}

/**
 * 1.39 작품제작: a skin with a picture or fill makes [ColorScheme.surface] clear, and a clear colour at some alpha is black at that
 * alpha (메모 날짜·꼬리표 칩, 프로 잠금 가림막 went dark grey on paper·texture memo screens). Wash over this instead.
 */
val ColorScheme.washSurface: androidx.compose.ui.graphics.Color get() = if (surface.alpha == 0f) surfaceContainer else surface

/**
 * [FREE-05] [base] in [look]'s colours. With a picture the page is clear so the picture shows, and the ink is read against what the
 * picture and its veil come to ([photoLight] 0 dark … 1 light).
 */
fun skinned(base: ColorScheme, look: ScreenLook, photoLight: Float? = null, photoAvg: Color? = null): ColorScheme {
    val page = look.ground?.let { Color(it) } ?: base.background
    // [MAT-01] A fill shows through the page like a picture does.
    val clear = photoLight != null || look.fill != null
    val ground = groundOf(base, look, photoLight, photoAvg)
    val card = look.card?.let { Color(it) }
    val cardLow = card ?: base.surfaceContainerLow
    val inkArgb = Palette.inkOn(look.ink, ground.argb(), cardLow.argb(), (card ?: base.surfaceContainerHigh).argb())
    val ink = Color(inkArgb)
    val soft = lerp(ink, ground, 0.28f).let { s ->
        if (Palette.contrast(s.argb(), ground.argb()) >= 4.5 && Palette.contrast(s.argb(), cardLow.argb()) >= 4.5) s else ink
    }
    var s = base.copy(
        background = if (clear) Color.Transparent else page,
        surface = if (clear) Color.Transparent else page,
        onBackground = ink, onSurface = ink, onSurfaceVariant = soft,
        outline = ink.copy(alpha = 0.55f), outlineVariant = ink.copy(alpha = 0.18f),
    )
    if (card != null) {
        val toward = if (Palette.luminance(card.argb()) > 0.5) Color.Black else Color.White
        s = s.copy(
            surfaceContainerLowest = card, surfaceContainerLow = card,
            surfaceContainer = lerp(card, toward, 0.03f), surfaceContainerHigh = lerp(card, toward, 0.06f),
            surfaceContainerHighest = lerp(card, toward, 0.09f), surfaceVariant = lerp(card, toward, 0.08f),
        )
    } else if (look.ground != null) {
        // A page colour without a card colour: the cards are washes of the page, so the screen holds together.
        val toward = if (Palette.luminance(page.argb()) > 0.5) Color.White else Color.Black
        s = s.copy(
            surfaceContainerLowest = lerp(page, toward, 0.7f), surfaceContainerLow = lerp(page, toward, 0.5f),
            surfaceContainer = lerp(page, toward, 0.35f), surfaceContainerHigh = lerp(page, toward, 0.25f),
            surfaceContainerHighest = lerp(page, toward, 0.15f), surfaceVariant = lerp(page, toward, 0.2f),
        )
    }
    val dark = Palette.luminance(ground.argb()) < 0.3
    return look.accent?.let { accented(s.copy(surface = page), it, dark).copy(surface = s.surface) } ?: s
}

/** PC 확인 C16 1.36: a picture's average colour (one pixel of it scaled down), what the words over it are read against. */
private fun averageColour(b: androidx.compose.ui.graphics.ImageBitmap): Color? = runCatching {
    val px = android.graphics.Bitmap.createScaledBitmap(b.asAndroidBitmap(), 1, 1, true).getPixel(0, 0)
    Color(px).copy(alpha = 1f)
}.getOrNull()

/**
 * 2.0.0 [RVP-67] (검토 REST-08) The ink [skinned] draws for [look] on [base] (a photo aside), so the 색·배경 editor's "잘 안 보여서"
 * warning follows the same rule as the screen, dark screens and the theme's own ground included.
 */
internal fun inkShown(base: ColorScheme, look: ScreenLook): Long {
    val ground = groundOf(base, look, null)
    val card = look.card?.let { Color(it) }
    return Palette.inkOn(look.ink, ground.argb(), (card ?: base.surfaceContainerLow).argb(), (card ?: base.surfaceContainerHigh).argb())
}

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
