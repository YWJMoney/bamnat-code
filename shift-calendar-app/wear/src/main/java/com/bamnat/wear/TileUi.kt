package com.bamnat.wear

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders

/**
 * The tiles' few pieces, one look for all of them: a line of text, a gap, and a pill the size of a finger. 2.0.0 [RVI-94][RVI-95]
 * Shared by 오늘 근무, 잠 and 오늘 할 일, so the three tiles read as one app.
 */
internal object TileUi {
    fun text(s: String, size: Float, color: Int, bold: Boolean = false, lines: Int = 2): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Text.Builder()
            .setText(s)
            .setMaxLines(lines)
            .setFontStyle(LayoutElementBuilders.FontStyle.Builder()
                .setSize(sp(size))
                .setColor(argb(color))
                .setWeight(if (bold) LayoutElementBuilders.FONT_WEIGHT_BOLD else LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                .build())
            .build()

    fun space(h: Float): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Spacer.Builder().setHeight(dp(h)).build()

    /** A pill the size of a finger that asks the tile again with [id] as the tapped clickable. */
    fun button(label: String, id: String, color: Int = ShiftTileService.WHITE): LayoutElementBuilders.LayoutElement = LayoutElementBuilders.Box.Builder()
        .setHeight(dp(40f))
        .setWidth(androidx.wear.protolayout.DimensionBuilders.wrap())
        .setModifiers(ModifiersBuilders.Modifiers.Builder()
            .setClickable(ModifiersBuilders.Clickable.Builder().setId(id).setOnClick(ActionBuilders.LoadAction.Builder().build()).build())
            .setBackground(ModifiersBuilders.Background.Builder().setColor(argb(ShiftTileService.BUTTON))
                .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(20f)).build()).build())
            .setPadding(ModifiersBuilders.Padding.Builder().setStart(dp(14f)).setEnd(dp(14f)).build())
            .build())
        .addContent(text(label, 13f, color, lines = 1))
        .build()

    /** The whole tile: [content] in the middle, a tap anywhere else opening the watch app. */
    fun frame(packageName: String, content: LayoutElementBuilders.LayoutElement): LayoutElementBuilders.LayoutElement {
        val open = ModifiersBuilders.Clickable.Builder()
            .setId("open")
            .setOnClick(ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(ActionBuilders.AndroidActivity.Builder().setPackageName(packageName).setClassName(MainActivity::class.java.name).build())
                .build())
            .build()
        return LayoutElementBuilders.Box.Builder()
            .setWidth(androidx.wear.protolayout.DimensionBuilders.expand())
            .setHeight(androidx.wear.protolayout.DimensionBuilders.expand())
            .setModifiers(ModifiersBuilders.Modifiers.Builder().setClickable(open).build())
            .addContent(content)
            .build()
    }
}
