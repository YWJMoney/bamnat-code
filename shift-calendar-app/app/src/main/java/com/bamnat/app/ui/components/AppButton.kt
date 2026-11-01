package com.bamnat.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonElevation
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.bamnat.app.ui.parts.LocalBlueprint
import com.bamnat.app.ui.theme.fillWith
import com.bamnat.core.recipe.Fill

/**
 * [MAT-01] 1.36 누락 점검 N10 단추도 채우기로. Material's filled Button, same name and parameters (a screen moves over by its import),
 * that draws the 단추 part's 채우기 when it is more than one colour: a gradient, a pattern, glass, a shader. The theme already gives
 * the button the fill's average colour (and its letters the colour that reads on it), so a one-colour fill, a button given its own
 * [colors] (such as a red 지우기) and a turned-off button stay Material's own.
 */
@Composable
fun Button(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    colors: ButtonColors? = null,
    elevation: ButtonElevation? = ButtonDefaults.buttonElevation(),
    border: BorderStroke? = null,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val paint = LocalBlueprint.current.let { bp -> if (bp.isEmpty) null else bp.own("app.button").paint }?.takeIf { it !is Fill.Solid }
    if (paint == null || colors != null || !enabled) {
        androidx.compose.material3.Button(onClick, modifier, enabled, shape, colors ?: ButtonDefaults.buttonColors(), elevation, border, contentPadding,
            interactionSource, content)
        return
    }
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val ink = MaterialTheme.colorScheme.onPrimary
    // The fill is drawn on the button's own box: the room Material leaves round a small button (48 dp) is made out here, so the
    // fill is as tall as the button looks and the screen keeps its spacing.
    Box(
        modifier.minimumInteractiveComponentSize(),
        contentAlignment = Alignment.Center,
        propagateMinConstraints = true,
    ) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
            androidx.compose.material3.Button(onClick, Modifier.fillWith(paint, shape), true, shape,
                ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = ink), elevation, border, contentPadding, source, content)
        }
    }
}
