package com.bamnat.app.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.unit.ColorProvider
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.WidgetLook

/** [RCX-14] How opaque the widget's background is, from 위젯 모양. */
private val LocalWidgetAlpha = compositionLocalOf { 1f }

/** [STU-38] 1.36 위젯 꾸미기: the background's own colour, the corners and the text size. */
private val LocalWidgetBgColor = compositionLocalOf<androidx.compose.ui.graphics.Color?> { null }
private val LocalWidgetCorner = compositionLocalOf { 20 }
private val LocalWidgetText = compositionLocalOf { 1f }

/** [STU-38] A text size in the widget, times the chosen 글자 크기. */
@Composable
fun wsp(size: Number): androidx.compose.ui.unit.TextUnit = (size.toFloat() * LocalWidgetText.current).sp

/** [RCX-30] 1.35 그림 꾸미기의 위젯 배경, read from files/looks/ (small: 800 px). */
private val LocalWidgetPicture = compositionLocalOf<android.graphics.Bitmap?> { null }

/**
 * [RCX-14] 1.35 위젯 모양 (설정 › 달력 화면, or a recipe's 위젯 모양 piece): the widgets' colours follow the phone, or always light or
 * dark, and their background is as opaque as chosen. With nothing chosen it is the app's own [GlanceTheme].
 */
@Composable
fun WidgetLookTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = runCatching { (context.applicationContext as BamnatApp).container.settingsRepository.current }.getOrNull()
    val look = settings?.widgetLook?.let(WidgetLook::decode) ?: WidgetLook()
    val pictureId = settings?.photoLook?.let { com.bamnat.core.recipe.PhotoLook.decode(it).widget }
    val picture = pictureId?.let { id ->
        runCatching { com.bamnat.app.recipe.LookImages.file(context, id).takeIf { it.isFile }?.let { com.bamnat.app.recipe.SafeBitmaps.decodeFile(it.path) } }.getOrNull()
    }
    // [STU-38] A background colour of its own decides light or dark, and the text is the chosen colour only where it reads (4.5:1).
    val ownBg = look.bg?.let { androidx.compose.ui.graphics.Color(it.toInt()) }
    val darkGround = look.bg?.let { com.bamnat.core.recipe.Palette.luminance(it) < 0.18 }
    val ink = look.ink?.let { i -> look.bg?.let { b -> com.bamnat.core.recipe.Palette.inkOn(i, b) } ?: i }?.let { androidx.compose.ui.graphics.Color(it.toInt()) }
    fun inked(c: androidx.compose.material3.ColorScheme) = if (ink == null) c else c.copy(onSurface = ink, onBackground = ink, onSurfaceVariant = ink.copy(alpha = 0.8f))
    val colors = when {
        darkGround == true || (darkGround == null && look.theme == "dark") -> androidx.glance.material3.ColorProviders(inked(darkColorScheme()))
        darkGround == false || look.theme == "light" -> androidx.glance.material3.ColorProviders(inked(lightColorScheme()))
        ink != null -> androidx.glance.material3.ColorProviders(light = inked(lightColorScheme()), dark = inked(darkColorScheme()))
        else -> GlanceTheme.colors
    }
    GlanceTheme(colors = colors) {
        // A picture behind the widget is veiled at least 45 % so its words read.
        CompositionLocalProvider(LocalWidgetAlpha provides if (picture != null) minOf(look.alpha / 100f, 0.55f).coerceAtLeast(0.45f) else look.alpha / 100f,
            LocalWidgetPicture provides picture, LocalWidgetBgColor provides ownBg, LocalWidgetCorner provides (look.corner ?: 20),
            LocalWidgetText provides look.textScale) { content() }
    }
}

/** The widget's background in the chosen opacity. */
@Composable
fun widgetBg(): ColorProvider {
    val a = LocalWidgetAlpha.current
    LocalWidgetBgColor.current?.let { return ColorProvider(it.copy(alpha = a)) }
    val base = GlanceTheme.colors.widgetBackground
    if (a >= 1f) return base
    return ColorProvider(base.getColor(LocalContext.current).copy(alpha = a))
}

/**
 * [RCX-30] The widget's ground: its colour as chosen, over the 그림 꾸미기 picture when there is one. [modifier] is the frame's own
 * (its click); the padding is inside.
 */
@Composable
fun WidgetSurface(modifier: androidx.glance.GlanceModifier, content: @Composable () -> Unit) {
    val picture = LocalWidgetPicture.current
    val r = widgetCorner()
    androidx.glance.layout.Box(modifier.fillMaxSize().cornerRadius(r)) {
        if (picture != null) androidx.glance.Image(androidx.glance.ImageProvider(picture), contentDescription = null,
            contentScale = androidx.glance.layout.ContentScale.Crop, modifier = androidx.glance.GlanceModifier.fillMaxSize().cornerRadius(r))
        androidx.glance.layout.Box(androidx.glance.GlanceModifier.fillMaxSize().background(widgetBg()).cornerRadius(r).padding(12.dp)) { content() }
    }
}

/** [STU-38] The widget's corners as chosen (20 dp is the app's own). */
@Composable
fun widgetCorner(): androidx.compose.ui.unit.Dp = LocalWidgetCorner.current.dp
