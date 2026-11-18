package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVP-85] 검토 HQ-53 (갈래 L이 PR 사진에서 찾음, 조건 12): the app's one top bar. Material's bar is 64dp whatever the title, so a
 * long title that took two lines at 글자 200% or on a folded screen ('캘린더에서 가져오기', '추가 근무 종류', '합쳐 보기', 리더 팀 표,
 * 영업일) had its second line cut by the bar's bottom. Here the title is never cut:
 *
 * - from 글자 130% the title is one step smaller (titleLarge → titleMedium), as the calendar's month steps down ([RVP-45] HQ-22);
 * - the bar grows to hold the title it has (two lines are two lines tall), and stays 64dp while the title is one line, so nothing
 *   moves on a screen whose title fits.
 *
 * The same parameters as [androidx.compose.material3.TopAppBar]; screens import this one instead (DesignRulesTest counts the other).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null,
) {
    val density = LocalDensity.current
    val style = titleStyleFor(density.fontScale)
    val base = TopAppBarDefaults.TopAppBarExpandedHeight
    // The title's own height, read where it is laid out without the bar's 64dp limit; the bar is that and its padding.
    var needed by remember { mutableIntStateOf(0) }
    val height = with(density) { maxOf(base, needed.toDp() + TITLE_PAD) }
    androidx.compose.material3.TopAppBar(
        title = {
            ProvideTextStyle(style) {
                Layout(content = title) { measurables, constraints ->
                    val loose = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
                    val placeables = measurables.map { it.measure(loose) }
                    val h = placeables.maxOfOrNull { it.height } ?: 0
                    if (h != needed) needed = h
                    val w = placeables.maxOfOrNull { it.width } ?: 0
                    layout(w, minOf(h, constraints.maxHeight)) { placeables.forEach { it.placeRelative(0, 0) } }
                }
            }
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        expandedHeight = height,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

/** The bar's title: titleLarge, one step smaller from 글자 130% (HQ-53, as the calendar's month in HQ-22). */
@Composable
internal fun titleStyleFor(fontScale: Float): TextStyle =
    if (fontScale >= 1.3f) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge

/** Room above and below a title that needs more than the bar's 64dp. */
private val TITLE_PAD = 16.dp
