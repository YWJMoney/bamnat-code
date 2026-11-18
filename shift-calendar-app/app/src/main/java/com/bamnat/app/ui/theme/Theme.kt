package com.bamnat.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.data.ThemeMode

// [A11Y-01] Text colours reach 4.5:1 on the page, cards and sheets (ContrastTest); secondary, tertiary and error
// were made a shade darker in 1.22 for that.
internal val LightColors = lightColorScheme(
    primary = Color(0xFF2F5BEA),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDDE4FF),
    onPrimaryContainer = Color(0xFF001257),
    secondary = Color(0xFF00756A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFC8F2EA),
    onSecondaryContainer = Color(0xFF00201C),
    tertiary = Color(0xFFA14F00),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFE2CC),
    onTertiaryContainer = Color(0xFF2E1500),
    error = Color(0xFFC4261A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF55160C),
    background = Color(0xFFF7F8FC),
    onBackground = Color(0xFF151927),
    surface = Color(0xFFF7F8FC),
    onSurface = Color(0xFF151927),
    surfaceVariant = Color(0xFFE3E6F0),
    onSurfaceVariant = Color(0xFF4A5068),
    outline = Color(0xFF7A8099),
    outlineVariant = Color(0xFFD4D8E5),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF1F3F9),
    surfaceContainer = Color(0xFFECEEF6),
    surfaceContainerHigh = Color(0xFFE6E9F2),
    surfaceContainerHighest = Color(0xFFE0E3ED),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFFB4C4FF),
    onPrimary = Color(0xFF00218B),
    primaryContainer = Color(0xFF1D41C2),
    onPrimaryContainer = Color(0xFFDDE4FF),
    secondary = Color(0xFF7FD8C8),
    onSecondary = Color(0xFF003731),
    secondaryContainer = Color(0xFF005047),
    onSecondaryContainer = Color(0xFFC8F2EA),
    tertiary = Color(0xFFFFB783),
    onTertiary = Color(0xFF4C2700),
    tertiaryContainer = Color(0xFF6C3A00),
    onTertiaryContainer = Color(0xFFFFE2CC),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF7A1A12),
    onErrorContainer = Color(0xFFFEE4E2),
    background = Color(0xFF0F1220),
    onBackground = Color(0xFFE3E5F0),
    surface = Color(0xFF0F1220),
    onSurface = Color(0xFFE3E5F0),
    surfaceVariant = Color(0xFF2E3347),
    onSurfaceVariant = Color(0xFFC1C6D9),
    outline = Color(0xFF8C91A6),
    outlineVariant = Color(0xFF3A3F55),
    surfaceContainerLowest = Color(0xFF0A0C17),
    surfaceContainerLow = Color(0xFF161A2A),
    surfaceContainer = Color(0xFF1A1E2F),
    surfaceContainerHigh = Color(0xFF24293B),
    surfaceContainerHighest = Color(0xFF2F3447),
)

/** Colors outside Material roles that the calendar needs. */
@Immutable
data class CalendarColors(
    val sunday: Color,
    val saturday: Color,
    /**
     * Sunday's red under another name. 2.0.0 [RVP-13] (검토 HQ-54): screens do not read it: a thing to check is [Tone.Caution],
     * a thing gone wrong (넘김·밀림·법 위반) is [Tone.Danger] (`Tone.color()`, `Tone.container()`); DesignRulesTest counts it.
     */
    val warning: Color,
    val warningContainer: Color,
    val holidayWork: Color,
    val memoDot: Color,
    /** [LEDG-01] Money in and out. */
    val income: Color = Color(0xFF0F7A56),
    val expense: Color = Color(0xFFC53030),
    /** [VIS-01] A day whose alarms were turned off. */
    val alarmOff: Color = Color(0xFFC4261A),
    /** [VIS-01] A day the user changed by hand. */
    val changed: Color = Color(0xFFA14F00),
    /** [SLP-14] Sleep and naps on the today strip, and the button that starts a sleep. */
    val sleep: Color = Color(0xFF4F5BD5),
    val nap: Color = Color(0xFFA5ADF0),
    val sleepContainer: Color = Color(0xFFCDD1FB),
    val onSleepContainer: Color = Color(0xFF1F287A),
    /**
     * [VIS-03] Leave marks (연·반·¼ squares and the leave lines): violet for 연차, blue for other paid leave,
     * grey for unpaid, teal for 보상휴가 [FEAT-10, PAYX-18]. Letters on them come from contentColorOn (4.45:1),
     * and the square itself stands out from the page at 3:1 (ContrastTest).
     */
    val leaveAnnual: Color = Color(0xFF7048E8),
    val leavePaid: Color = Color(0xFF1A75CC),
    val leaveUnpaid: Color = Color(0xFF666E77),
    val leaveComp: Color = Color(0xFF0B7F92),
    /**
     * [VIS-03] The pay lines' colours, matching the walkthrough's time bar; the base pay line is the theme's
     * primary. They are also written as text (an amount's colour), so 4.5:1 on cards and sheets (ContrastTest).
     */
    val payOvertime: Color = Color(0xFFA35200),
    val payNight: Color = Color(0xFF6741D9),
    val payHoliday: Color = Color(0xFFC92A2A),
    val payExtra: Color = Color(0xFFC2255C),
    val payExtraHours: Color = Color(0xFF1864AB),
    val payAllowance: Color = Color(0xFF237032),
    val payLeave: Color = Color(0xFF7048E8),
)

// [VIS-01] Weekend numbers and the extra-hours text reach 4.5:1 on white and on a shift's tint.
// [A11Y-01] And on cards and sheets too (ContrastTest): the reds, oranges and greens are a shade darker since 1.22.
internal val LightCalendarColors = CalendarColors(
    sunday = Color(0xFFC4261A),
    saturday = Color(0xFF2F5BEA),
    warning = Color(0xFFC4261A),
    warningContainer = Color(0xFFFEE4E2),
    holidayWork = Color(0xFFA14F00),
    memoDot = Color(0xFF00897B),
)

internal val DarkCalendarColors = CalendarColors(
    sunday = Color(0xFFFF7A7F),
    saturday = Color(0xFF8AB0FF),
    warning = Color(0xFFFF8A80),
    warningContainer = Color(0xFF4A1712),
    holidayWork = Color(0xFFFFB783),
    memoDot = Color(0xFF7FD8C8),
    income = Color(0xFF6FD3A6),
    expense = Color(0xFFFF8F8F),
    alarmOff = Color(0xFFFF8A80),
    changed = Color(0xFFFFB783),
    sleep = Color(0xFF9AA5FF),
    nap = Color(0xFF5A64B8),
    sleepContainer = Color(0xFF323A7A),
    onSleepContainer = Color(0xFFDFE1FF),
    leaveAnnual = Color(0xFFB197FC),
    leavePaid = Color(0xFF74C0FC),
    leaveUnpaid = Color(0xFFADB5BD),
    leaveComp = Color(0xFF66D9E8),
    payOvertime = Color(0xFFFFA94D),
    payNight = Color(0xFFB197FC),
    payHoliday = Color(0xFFFF8787),
    payExtra = Color(0xFFF783AC),
    payExtraHours = Color(0xFF74C0FC),
    payAllowance = Color(0xFF69DB7C),
    payLeave = Color(0xFFB197FC),
)

val LocalCalendarColors = staticCompositionLocalOf { LightCalendarColors }

/**
 * [RCP-09] 1.34 달력 테마 as the calendar draws it: the user's (or a recipe's) colours for today and 토·일·공휴일, lifted for the
 * dark calendar, and the cell style. A null colour keeps the app's own ([CalendarColors]).
 */
data class CalendarLook(
    val today: Color? = null,
    val sat: Color? = null,
    val sun: Color? = null,
    val holiday: Color? = null,
    val cell: String = "tint",
) {
    fun sunday(cal: CalendarColors): Color = sun ?: cal.sunday
    fun saturday(cal: CalendarColors): Color = sat ?: cal.saturday
    fun holiday(cal: CalendarColors): Color = holiday ?: sun ?: cal.sunday

    /** How strong a working day's wash is: 0 for none ("dot"). */
    fun washAlpha(dark: Boolean): Float = when (cell) {
        "fill" -> if (dark) 0.40f else 0.32f
        "dot" -> 0f
        else -> if (dark) 0.22f else 0.13f
    }

    companion object {
        fun of(theme: com.bamnat.core.recipe.CalendarTheme, dark: Boolean): CalendarLook {
            fun c(v: Long?) = v?.let { Color((if (dark) com.bamnat.core.layout.Contrast.onBlack(it) else it).toInt()) }
            return CalendarLook(c(theme.today), c(theme.sat), c(theme.sun), c(theme.holiday), theme.cell ?: "tint")
        }
    }
}

val LocalCalendarLook = staticCompositionLocalOf { CalendarLook() }

/** [RCX-30] 1.35 그림 꾸미기, as the settings keep it (pictures by id, drawn from files/looks/). */
val LocalPhotoLook = staticCompositionLocalOf { com.bamnat.core.recipe.PhotoLook() }

/** [R200-01] The pictures of the shown calendar's kinds of day, by shift id ([com.bamnat.core.recipe.PhotoLook.icons] is by calendar). */
val LocalShiftIcons = staticCompositionLocalOf { emptyMap<Long, String>() }

/**
 * [VIS-02] Korean wraps between words, never inside one ("꺼져 있어 / 요"): phrase breaking on text
 * marked Korean, which Android 15 and later follow whatever the phone's language is (earlier versions
 * wrap as before). Titles also even out their lines.
 */
private val Korean = LocaleList("ko-KR")
private val WordWrap = LineBreak.Simple.copy(wordBreak = LineBreak.WordBreak.Phrase)
private fun TextStyle.korean(title: Boolean = false) = copy(lineBreak = if (title) LineBreak.Heading else WordWrap, localeList = Korean)

private val AppTypography = Typography().let { t ->
    Typography(
        displayLarge = t.displayLarge.korean(title = true),
        displayMedium = t.displayMedium.korean(title = true),
        displaySmall = t.displaySmall.korean(title = true),
        headlineLarge = t.headlineLarge.korean(title = true),
        headlineMedium = t.headlineMedium.korean(title = true),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp).korean(title = true),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp).korean(title = true),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold).korean(title = true),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold).korean(title = true),
        bodyLarge = t.bodyLarge.korean(),
        bodyMedium = t.bodyMedium.korean(),
        bodySmall = t.bodySmall.korean(),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold).korean(),
        labelMedium = t.labelMedium.korean(),
        labelSmall = t.labelSmall.korean(),
    )
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** [RCX-30] 1.35 그림 꾸미기의 모서리: round (더 둥글게), square (거의 각지게); null and "soft" are the app's own. */
fun shapesFor(corners: String?): Shapes = when (corners) {
    "round" -> Shapes(
        extraSmall = RoundedCornerShape(10.dp), small = RoundedCornerShape(16.dp), medium = RoundedCornerShape(24.dp),
        large = RoundedCornerShape(32.dp), extraLarge = RoundedCornerShape(40.dp),
    )
    "square" -> Shapes(
        extraSmall = RoundedCornerShape(2.dp), small = RoundedCornerShape(4.dp), medium = RoundedCornerShape(6.dp),
        large = RoundedCornerShape(8.dp), extraLarge = RoundedCornerShape(12.dp),
    )
    else -> AppShapes
}

/**
 * 2.0.0 [RVP-05] 검토 PC-128 (조건 5): the app's pill: 단추 (ui/components/Buttons.kt), 달력 머리의 '내 달력'·'공유'·'도구', 칩 줄 끝의
 * '+5'. Round while 모서리 is 보통 or 둥글게; the chips' corner when it is 각지게, so one screen has one roundness (before, the cards and chips
 * went square and these stayed round beside them). Faces and dots stay round: they are marks, not buttons.
 */
val LocalPill = staticCompositionLocalOf<androidx.compose.ui.graphics.Shape> { androidx.compose.foundation.shape.CircleShape }

val MaterialTheme.pill: androidx.compose.ui.graphics.Shape
    @Composable @androidx.compose.runtime.ReadOnlyComposable get() = LocalPill.current

/** 각지게 takes the chips' corner for the pill; the other settings keep it round ([LocalPill]). */
fun pillFor(corners: String?, shapes: Shapes): androidx.compose.ui.graphics.Shape =
    if (corners == "square") shapes.small else androidx.compose.foundation.shape.CircleShape

/** Tabular figures keep hour totals from jumping around as they change. */
val NumberStyle = TextStyle(fontFeatureSettings = "tnum")

@Composable
fun BamnatTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    /** U 1.32 [UX132-05] 글자 크기 단계 ([TextSize]). */
    textStep: Int = TextSize.DEFAULT,
    /** U 1.32 [UX132-06] 촘촘하게. */
    compact: Boolean = true,
    /** [RCX-30] 그림 꾸미기의 모서리 ([shapesFor]). */
    corners: String? = null,
    /** [CVW-08] 1.35 앱 강조색 (ARGB, 0 = the app's own): buttons, the picked tab and the selected things. */
    accent: Long = 0L,
    /** [BLU-05] 1.36 부품 꾸미기: the app-wide parts (앱 전체·단추·카드·칩·아래 창) over the colours, shapes and letters. */
    parts: com.bamnat.core.recipe.Blueprint = com.bamnat.core.recipe.Blueprint.EMPTY,
    /**
     * 1.39 [FLOW-14][DSG-07] 밤 색 now ([rememberAppNight]): the whole app in [NightPalette], over the accent, the phone's own
     * colours and the parts' colours (a bright part colour is what would glare); letters and shapes stay the user's.
     */
    night: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = night || when (themeMode) {
        // [SHFT-05] The app resolves NIGHT_SHIFT before it gets here (rememberThemeMode); elsewhere it is the phone's.
        ThemeMode.SYSTEM, ThemeMode.NIGHT_SHIFT -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val colors: ColorScheme = if (night) NightPalette.scheme else when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }.let { if (accent == 0L || night) it else accented(it, accent, dark) }
    // [BLU-12] 1.36 어두운 화면: the parts' `on: dark` lines (a token's dark colour) laid over theirs when the screen is dark.
    val raw = parts
    // 1.39 [MKP-06] 입은 테마의 부품 글꼴 (장터 로딩 2차에서 찾음): the open fonts its parts name come down (nothing asked for them, so a
    // worn 테마's own font was drawn as the closest phone font until it was picked in an editor), and the parts are read again once they are.
    val partFonts = androidx.compose.runtime.remember(raw) { raw.lines.mapNotNull { it["font"] as? String }.filter { AppFonts.downloads(it) }.distinct() }
    val fontsCame = partFonts.count { AppFonts.loads[it] == AppFonts.Load.READY }
    // 2.0.0 [RVM-21] MKT-32 While a theme is tried on the real app (LiveTryOn): its fonts are card fonts from our public files, the whole
    // font (Google Fonts) comes only with 입기.
    val cardFonts = LocalCardFonts.current
    if (partFonts.isNotEmpty() && cardFonts == null) {
        val ctx = LocalContext.current
        androidx.compose.runtime.LaunchedEffect(partFonts) { partFonts.forEach { AppFonts.start(ctx, it) } }
    }
    val parts = androidx.compose.runtime.remember(raw, dark, fontsCame, cardFonts) {
        raw.forDark(dark).let {
            when {
                !cardFonts.isNullOrEmpty() -> com.bamnat.core.recipe.Blueprint(com.bamnat.app.ui.recipe.MarketFonts.lines(it.lines, cardFonts))
                fontsCame > 0 -> com.bamnat.core.recipe.Blueprint(it.lines)
                else -> it
            }
        }
    }
    // 2.0.0 [RVP-14] (검토 UIX-05) 밤 색 over the whole app takes the dark calendar colours too, whatever the phone's own theme.
    CompositionLocalProvider(LocalCalendarColors provides if (dark || night) DarkCalendarColors else LightCalendarColors) {
        // U 1.32 [UX132-05] The app's own size times the phone's, never past 2배 so no screen breaks; layouts that measure in
        // sp grow with it and the ones that count columns ([LocalSpacing], the calendar's cells) read the scale.
        val base = androidx.compose.ui.platform.LocalDensity.current
        val scaled = androidx.compose.ui.unit.Density(base.density, TextSize.scale(base.fontScale, textStep))
        // 1.36 (PC 확인: 글자 크기를 적용한 직후 첫 그리기가 멈춤 카드에): a new size draws every screen again; that frame is
        // told to the freeze watch before it is made, so it is not kept as a stop.
        val lastScale = androidx.compose.runtime.remember { floatArrayOf(scaled.fontScale) }
        if (lastScale[0] != scaled.fontScale) {
            lastScale[0] = scaled.fontScale
            com.bamnat.app.diag.MainStall.redrawAhead("text")
        }
        CompositionLocalProvider(
            androidx.compose.ui.platform.LocalDensity provides scaled,
            LocalSpacing provides if (compact) Spacing.COMPACT else Spacing.ROOMY,
        ) {
            val shapes = com.bamnat.app.ui.parts.PartTheme.shapes(shapesFor(corners), parts)
            MaterialTheme(
                colorScheme = if (night) colors else com.bamnat.app.ui.parts.PartTheme.colors(colors, parts),
                typography = com.bamnat.app.ui.parts.PartTheme.typography(AppTypography, parts),
                shapes = shapes,
            ) {
                // 2.0.0 [RVS-06] 그릴 때 지키는 바닥선: every part as this screen draws it (letters that read, a tab bar that is seen), the
                // look's own values kept as set ([com.bamnat.core.recipe.PartFloor]).
                val screenArgb = MaterialTheme.colorScheme.background.toArgb().toLong() and 0xFFFFFFFFL
                val shown = androidx.compose.runtime.remember(parts, screenArgb) { parts.floored(screenArgb) }
                CompositionLocalProvider(com.bamnat.app.ui.parts.LocalBlueprint provides shown,
                    com.bamnat.app.ui.parts.LocalBlueprintRaw provides raw, LocalPill provides pillFor(corners, shapes), content = content)
            }
        }
    }
}

/**
 * [CVW-08] The scheme with [accent] as its primary: on a dark surface it is lifted toward white so it reads, the containers are
 * washes of it over the surface, and every text on it is black or white by [com.bamnat.core.layout.Contrast] (A11Y 4.5:1).
 */
fun accented(base: ColorScheme, accent: Long, dark: Boolean): ColorScheme {
    val a = Color(accent or 0xFF000000L)
    val primary = if (dark) androidx.compose.ui.graphics.lerp(a, Color.White, 0.35f) else a
    val container = androidx.compose.ui.graphics.lerp(base.surface, a, if (dark) 0.35f else 0.18f)
    val secondary = androidx.compose.ui.graphics.lerp(base.surface, a, if (dark) 0.25f else 0.12f)
    fun on(c: Color) = com.bamnat.app.ui.components.contentColorOn(c)
    return base.copy(
        primary = primary, onPrimary = on(primary), primaryContainer = container, onPrimaryContainer = on(container),
        secondaryContainer = secondary, onSecondaryContainer = on(secondary), surfaceTint = primary,
        inversePrimary = androidx.compose.ui.graphics.lerp(a, if (dark) Color.Black else Color.White, 0.4f),
    )
}

/** U 1.32 [UX132-05] 설정 › 화면과 글자 › 글자 크기: five steps, times the phone's own 글꼴 크기. */
object TextSize {
    val STEPS = listOf(0.9f, 1.0f, 1.15f, 1.3f, 1.5f)
    val LABELS = listOf("작게", "보통", "크게", "더 크게", "아주 크게")
    const val DEFAULT = 1
    const val MAX_SCALE = 2.0f
    fun scale(system: Float, step: Int): Float = (system * STEPS[step.coerceIn(0, STEPS.lastIndex)]).coerceAtMost(MAX_SCALE.coerceAtLeast(system))
}

/** U 1.32 [UX132-06] The gaps between a screen's cards: 촘촘하게 (기본) or 넉넉하게. */
data class Spacing(val card: androidx.compose.ui.unit.Dp, val inner: androidx.compose.ui.unit.Dp, val page: androidx.compose.ui.unit.Dp) {
    companion object {
        val COMPACT = Spacing(card = 8.dp, inner = 12.dp, page = 12.dp)
        val ROOMY = Spacing(card = 12.dp, inner = 16.dp, page = 16.dp)
    }
}

val LocalSpacing = androidx.compose.runtime.staticCompositionLocalOf { Spacing.COMPACT }

/** Palette offered for shift types. [A11Y-03] Every colour carries its letters at 4.45:1 or more (ContrastTest). */
val ShiftPalette: List<Long> = listOf(
    0xFF2F80ED, 0xFF2F5BEA, 0xFF6C5CE7, 0xFF9B51E0, 0xFFE84393, 0xFFE5484D,
    0xFFF2994A, 0xFFF2C94C, 0xFF27AE60, 0xFF00857A, 0xFF00A3C4, 0xFF8E9AAF,
)

/** [EXTRA-01] Palette offered for kinds of extra work; the first three are the built-in 잔업·특근·대근. */
val ExtraPalette: List<Long> = listOf(
    0xFFE8900C, 0xFFE03131, 0xFF0E9F6E, 0xFF7048E8, 0xFF1A75CC, 0xFFD6336C,
    0xFF0B7285, 0xFF5C940D, 0xFF862E9C, 0xFFB35C00, 0xFF495057, 0xFF268038,
)

/** Soft backgrounds for memo cards: index 0 is the plain surface. */
val MemoPaletteLight: List<Color> = listOf(
    Color.Unspecified, Color(0xFFFFF4CC), Color(0xFFFFE2CC), Color(0xFFFFDDE4),
    Color(0xFFE6DDFF), Color(0xFFD9E7FF), Color(0xFFCFF3EA), Color(0xFFE8F5D0),
)
val MemoPaletteDark: List<Color> = listOf(
    Color.Unspecified, Color(0xFF4A3F12), Color(0xFF4D2E14), Color(0xFF4D2230),
    Color(0xFF332A5C), Color(0xFF1E335C), Color(0xFF16443B), Color(0xFF2E4119),
)

/**
 * 1.38 [DSG-03] (흐름·디자인 계획 4장): two meaning colours and danger, the same everywhere a number is judged. Green is
 * good, orange asks for care, red is kept for danger only (an alarm that will not ring, a rule broken), so red stays
 * loud. Each sits at 4.5:1 or more on the page, cards and sheets of its own mode (ContrastTest), and none is a shift
 * colour's twin. A studio look can hand its own set down with [LocalBamnatColors].
 */
enum class Tone { Plain, Good, Caution, Danger }

@Immutable
data class BamnatColors(
    val good: Color, val caution: Color, val danger: Color,
    /**
     * 2.0.0 [RVP-13] (검토 HQ-54) The soft ground under a 주의 or 위험 mark that must stand apart from the card (a 노란 칸 in a
     * table, a cell to check); its own ink reads on it at 4.5:1 (ContrastTest). Notice cards keep the one quiet ground (조건 3).
     */
    val cautionContainer: Color = Color(0xFFFFEFD9), val dangerContainer: Color = Color(0xFFFEE4E2),
) {
    companion object {
        val Light = BamnatColors(good = Color(0xFF1B7A4C), caution = Color(0xFFA65400), danger = Color(0xFFC62828),
            cautionContainer = Color(0xFFFFEFD9), dangerContainer = Color(0xFFFEE4E2))
        val Dark = BamnatColors(good = Color(0xFF6FD3A0), caution = Color(0xFFFFB25C), danger = Color(0xFFFF8A80),
            cautionContainer = Color(0xFF3F2A0E), dangerContainer = Color(0xFF4A1712))
    }
}

/** A studio look's own meaning colours; null keeps [BamnatColors.Light]/[BamnatColors.Dark] by the page's brightness. */
val LocalBamnatColors = staticCompositionLocalOf<BamnatColors?> { null }

/** `MaterialTheme.bamnat.good` etc.: read where a value is judged good, careful or dangerous. */
val MaterialTheme.bamnat: BamnatColors
    @Composable @androidx.compose.runtime.ReadOnlyComposable
    get() = LocalBamnatColors.current
        ?: if (colorScheme.surface.luminance() < 0.5f) BamnatColors.Dark else BamnatColors.Light

/** The colour a [Tone] is written in; [Tone.Plain] is the page's own text colour. */
@Composable @androidx.compose.runtime.ReadOnlyComposable
fun Tone.color(): Color = when (this) {
    Tone.Plain -> MaterialTheme.colorScheme.onSurface
    Tone.Good -> MaterialTheme.bamnat.good
    Tone.Caution -> MaterialTheme.bamnat.caution
    Tone.Danger -> MaterialTheme.bamnat.danger
}

/**
 * 2.0.0 [RVP-13] (검토 HQ-54) The soft ground a [Tone] mark stands on where it must stand apart (a cell or a tile to check);
 * [Tone.Plain] and [Tone.Good] keep the card's quiet ground.
 */
@Composable @androidx.compose.runtime.ReadOnlyComposable
fun Tone.container(): Color = when (this) {
    Tone.Plain, Tone.Good -> MaterialTheme.colorScheme.surfaceContainerLow
    Tone.Caution -> MaterialTheme.bamnat.cautionContainer
    Tone.Danger -> MaterialTheme.bamnat.dangerContainer
}
