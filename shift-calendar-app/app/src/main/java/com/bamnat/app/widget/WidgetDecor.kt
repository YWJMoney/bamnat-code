package com.bamnat.app.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.font.createFontFamilyResolver
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.bamnat.app.ui.decor.drawDecor
import com.bamnat.core.recipe.Decor

/**
 * [LAY-07] 1.36 위젯 꾸밈: the widget can't run Compose drawing, so its 꾸밈 층 ([Decor.WIDGET]) is drawn once into a picture the size of
 * the widget and laid in it — [over] false under the words, true over them. Null when nothing stands there. The picture is at most twice
 * the widget's dp so a big widget stays well under the home screen's memory limit.
 */
internal object WidgetDecor {
    fun bitmap(context: Context, decor: Decor, widthDp: Float, heightDp: Float, over: Boolean, accent: Color, ink: Color): Bitmap? {
        val items = decor.on(Decor.WIDGET).filter { it.over == over && !it.hide }
        if (items.isEmpty() || widthDp < 1f || heightDp < 1f) return null
        val scale = context.resources.displayMetrics.density.coerceAtMost(2f)
        val w = (widthDp * scale).toInt().coerceIn(1, 1600)
        val h = (heightDp * scale).toInt().coerceIn(1, 1600)
        return runCatching {
            val bmp = androidx.core.graphics.createBitmap(w, h)
            val density = Density(scale, context.resources.configuration.fontScale)
            val measurer = TextMeasurer(createFontFamilyResolver(context), density, LayoutDirection.Ltr, cacheSize = 4)
            val canvas = androidx.compose.ui.graphics.Canvas(bmp.asImageBitmap())
            CanvasDrawScope().draw(density, LayoutDirection.Ltr, canvas, Size(w.toFloat(), h.toFloat())) {
                val box = Rect(Offset.Zero, size)
                items.forEach { drawDecor(it, box, measurer, accent, ink) { id -> com.bamnat.app.ui.decor.DecorPhotos.now(context, id) } }
            }
            bmp
        }.getOrNull()
    }
}
