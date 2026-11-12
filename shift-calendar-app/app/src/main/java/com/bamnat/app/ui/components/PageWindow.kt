package com.bamnat.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat

/**
 * 2.0.0 [RVP-07] (검토 UIX-22) How wide a page's words may run on a 펼친 폴드 or a tablet: one value for the body (there were five:
 * 640·560·520·600·720), so a label and its value are never pulled to the two far edges. A table may still fill the width.
 */
object PageWidth {
    val Body = 640.dp
    /** A narrow column in the middle of a page, like the night screen's buttons. */
    val Narrow = 520.dp
}

/**
 * 2.0.0 [RVP-07] (검토 UIX-04) Inside a full-screen 창 with `decorFitsSystemWindows = false`: no grey dim behind it and the status
 * bar's and the bottom line's icons drawn dark on a light page and light on a dark one, so the 창 looks like a page of the app.
 */
@Composable
fun PageWindow() {
    val view = LocalView.current
    val dark = isDarkSurface()
    SideEffect {
        val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
        window.setDimAmount(0f)
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** 2.0.0 [RVP-07] A page that slides a quarter in from the side as it opens, like the app's own pages (still when 움직임 끄기). */
@Composable
fun SlideInPage(content: @Composable () -> Unit) {
    val still = com.bamnat.app.ui.theme.LocalMotionOff.current
    val shown = remember { MutableTransitionState(still).apply { targetState = true } }
    AnimatedVisibility(shown, enter = slideInHorizontally(tween(250)) { it / 4 } + fadeIn(tween(250))) { content() }
}
