package com.bamnat.app.widget

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.glance.GlanceTheme
import androidx.glance.LocalContext
import androidx.glance.unit.ColorProvider
import com.bamnat.app.BamnatApp
import com.bamnat.core.recipe.WidgetLook

/** [RCX-14] How opaque the widget's background is, from 위젯 모양. */
private val LocalWidgetAlpha = compositionLocalOf { 1f }

/**
 * [RCX-14] 1.35 위젯 모양 (설정 › 달력 화면, or a recipe's 위젯 모양 piece): the widgets' colours follow the phone, or always light or
 * dark, and their background is as opaque as chosen. With nothing chosen it is the app's own [GlanceTheme].
 */
@Composable
fun WidgetLookTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val look = runCatching { (context.applicationContext as BamnatApp).container.settingsRepository.current.widgetLook }
        .getOrNull()?.let(WidgetLook::decode) ?: WidgetLook()
    val colors = when (look.theme) {
        "light" -> androidx.glance.material3.ColorProviders(lightColorScheme())
        "dark" -> androidx.glance.material3.ColorProviders(darkColorScheme())
        else -> GlanceTheme.colors
    }
    GlanceTheme(colors = colors) {
        CompositionLocalProvider(LocalWidgetAlpha provides look.alpha / 100f) { content() }
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
