package com.bamnat.app.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
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
fun ScreenSkin(key: String, modifier: Modifier = Modifier, bars: Boolean = false, content: @Composable () -> Unit) {
    val all = LocalScreenLooks.current
    val raw = all.of(key)
    val look = if (key == ScreenLooks.PLAZA) ScreenLook(bg = raw.bg, photo = raw.photo, dim = raw.dim, emoji = raw.emoji) else raw
    val base = MaterialTheme.colorScheme
    if (look.isDefault) {
        if (bars) BarIcons(light = Palette.luminance(base.background.argb()) > 0.5)
        Box(modifier) { CompositionLocalProvider(LocalScreenLook provides look) { content() } }
        return
    }
    val photo = rememberLookPicture(look.photo)
    val scheme = skinned(base, look, photo?.second)
    val dark = Palette.luminance(groundOf(base, look, photo?.second).argb()) < 0.3
    // 1.35 [J135-11] The status bar's clock and icons stand on the screen's own ground (dark icons on a dark theme were unreadable).
    if (bars) BarIcons(light = !dark)
    // 1.35 [J135-12] The warning cards and the money colours that do not follow the scheme take the light or dark set of the ground,
    // so a 허용 on an alarm warning card reads on a dark theme too.
    val cal = LocalCalendarColors.current
    val tone = if (dark) DarkCalendarColors else LightCalendarColors
    val fitted = cal.copy(warning = tone.warning, warningContainer = tone.warningContainer, income = tone.income, expense = tone.expense,
        alarmOff = tone.alarmOff, changed = tone.changed, holidayWork = tone.holidayWork)
    MaterialTheme(colorScheme = scheme, typography = MaterialTheme.typography, shapes = MaterialTheme.shapes) {
        Box(modifier.background(look.bg?.let { Color(it) } ?: base.background)) {
            if (photo != null) {
                Image(photo.first, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                val veil = (look.dim ?: 30).coerceIn(0, 80)
                Box(Modifier.matchParentSize().background((look.bg?.let { Color(it) } ?: base.background).copy(alpha = maxOf(veil, 20) / 100f)))
            }
            // [J135-12] Text drawn straight on the page (no card or bar of its own) takes the skin's ink, not the ink of what is around.
            CompositionLocalProvider(LocalScreenLook provides look, LocalCalendarColors provides fitted,
                androidx.compose.material3.LocalContentColor provides scheme.onBackground) { content() }
        }
    }
}

/** [J135-11] The status and navigation bar icons dark ([light] ground) or light, on the activity that draws this. */
@Composable
private fun BarIcons(light: Boolean) {
    val view = androidx.compose.ui.platform.LocalView.current
    if (view.isInEditMode) return
    androidx.compose.runtime.SideEffect {
        var c = view.context
        while (c is android.content.ContextWrapper && c !is android.app.Activity) c = c.baseContext
        (c as? android.app.Activity)?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }
}

/** The colour the screen's text stands on: the page, or what a picture and its veil come to. */
private fun groundOf(base: ColorScheme, look: ScreenLook, photoLight: Float?): Color {
    val page = look.bg?.let { Color(it) } ?: base.background
    if (photoLight == null) return page
    val veil = (look.dim ?: 30).coerceIn(20, 80) / 100f
    return lerp(if (photoLight >= 0.5f) Color(0xFFEDEDED) else Color(0xFF2A2A2A), page, veil)
}

/**
 * [FREE-05] [base] in [look]'s colours. With a picture the page is clear so the picture shows, and the ink is read against what the
 * picture and its veil come to ([photoLight] 0 dark … 1 light).
 */
fun skinned(base: ColorScheme, look: ScreenLook, photoLight: Float? = null): ColorScheme {
    val page = look.bg?.let { Color(it) } ?: base.background
    val ground = groundOf(base, look, photoLight)
    val card = look.card?.let { Color(it) }
    val cardLow = card ?: base.surfaceContainerLow
    val inkArgb = Palette.inkOn(look.ink, ground.argb(), cardLow.argb(), (card ?: base.surfaceContainerHigh).argb())
    val ink = Color(inkArgb)
    val soft = lerp(ink, ground, 0.28f).let { s ->
        if (Palette.contrast(s.argb(), ground.argb()) >= 4.5 && Palette.contrast(s.argb(), cardLow.argb()) >= 4.5) s else ink
    }
    var s = base.copy(
        background = if (photoLight != null) Color.Transparent else page,
        surface = if (photoLight != null) Color.Transparent else page,
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
    } else if (look.bg != null) {
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

private fun Color.argb(): Long = toArgb().toLong() and 0xFFFFFFFFL
