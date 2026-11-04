package com.bamnat.app.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * 1.36 PC E3 (급여 자세히 고치기: 키보드를 닫으려고 시스템 뒤로를 누르니 단계가 바뀌고 적은 나이트 수당이 사라짐): while the keyboard
 * is up, back only puts it away (and ends the typing, so what was typed is kept); the next back does [onBack].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun KeyboardFirstBackHandler(enabled: Boolean, onBack: () -> Unit) {
    val typing = WindowInsets.isImeVisible
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    BackHandler(enabled = enabled || typing) {
        if (typing) {
            keyboard?.hide()
            focus.clearFocus()
        } else onBack()
    }
}
