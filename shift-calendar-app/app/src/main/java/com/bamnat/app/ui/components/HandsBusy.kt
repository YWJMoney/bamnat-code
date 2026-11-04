package com.bamnat.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 1.36 PC 확인 U21 (첫 칸을 칠하는 순간 '알림 허용' 창이 떠서 다음 누름이 창으로 가요): while the user's hands are busy (빠른 입력,
 * 스튜디오), asks and notices that would pop over the screen wait until they are done.
 */
object HandsBusy {
    private val holders = MutableStateFlow(0)
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    fun hold() { holders.value++; _busy.value = holders.value > 0 }
    fun release() { holders.value = (holders.value - 1).coerceAtLeast(0); _busy.value = holders.value > 0 }
}

/** Marks the hands busy while [on] (and this is on screen). */
@Composable
fun HoldHands(on: Boolean) {
    DisposableEffect(on) {
        if (on) HandsBusy.hold()
        onDispose { if (on) HandsBusy.release() }
    }
}
