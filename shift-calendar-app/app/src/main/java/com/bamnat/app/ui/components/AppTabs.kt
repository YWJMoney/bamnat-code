package com.bamnat.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * 2.0.0 [RVP-02] (검토 UIX-06·U13-12) Material's [androidx.compose.material3.Tab] with the same name and parameters (a screen moves over by
 * its import), but a tab that is not picked is grey ([androidx.compose.material3.ColorScheme.onSurfaceVariant]) instead of the same
 * blue as the picked one, so where you are reads at a glance and the top of 마을·메모·급여·투자 is no longer a row of blue words.
 */
@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    text: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    selectedContentColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    interactionSource: MutableInteractionSource? = null,
) = androidx.compose.material3.Tab(selected, onClick, modifier, enabled, text, icon, selectedContentColor, unselectedContentColor,
    interactionSource)

/** [RVP-02] The same for a tab drawn from its own [content]. */
@Composable
fun Tab(
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContentColor: Color = MaterialTheme.colorScheme.primary,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    interactionSource: MutableInteractionSource? = null,
    content: @Composable ColumnScope.() -> Unit,
) = androidx.compose.material3.Tab(selected, onClick, modifier, enabled, selectedContentColor, unselectedContentColor, interactionSource, content)
