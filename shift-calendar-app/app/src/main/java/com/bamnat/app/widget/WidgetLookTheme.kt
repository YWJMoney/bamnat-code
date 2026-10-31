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
import androidx.glance.unit.ColorProvider
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.WidgetLook

/** [RCX-14] How opaque the widget's background is, from 위젯 모양. */
private val LocalWidgetAlpha = compositionLocalOf { 1f }

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
        runCatching { com.bamnat.app.recipe.LookImages.file(context, id).takeIf { it.isFile }?.let { android.graphics.BitmapFactory.decodeFile(it.path) } }.getOrNull()
    }
    val colors = when (look.theme) {
        "light" -> androidx.glance.material3.ColorProviders(lightColorScheme())
        "dark" -> androidx.glance.material3.ColorProviders(darkColorScheme())
        else -> GlanceTheme.colors
    }
    GlanceTheme(colors = colors) {
        // A picture behind the widget is veiled at least 45 % so its words read.
        CompositionLocalProvider(LocalWidgetAlpha provides if (picture != null) minOf(look.alpha / 100f, 0.55f).coerceAtLeast(0.45f) else look.alpha / 100f,
            LocalWidgetPicture provides picture) { content() }
    }
}

/** The widget's background in the chosen opacity. */
@Composable
fun widgetBg(): ColorProvider {
    val base = GlanceTheme.colors.widgetBackground
    val a = LocalWidgetAlpha.current
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
    androidx.glance.layout.Box(modifier.fillMaxSize().cornerRadius(20.dp)) {
        if (picture != null) androidx.glance.Image(androidx.glance.ImageProvider(picture), contentDescription = null,
            contentScale = androidx.glance.layout.ContentScale.Crop, modifier = androidx.glance.GlanceModifier.fillMaxSize().cornerRadius(20.dp))
        androidx.glance.layout.Box(androidx.glance.GlanceModifier.fillMaxSize().background(widgetBg()).cornerRadius(20.dp).padding(12.dp)) { content() }
    }
}
