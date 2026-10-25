package com.bamnat.app.invest

import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [INV-40] 409 C: a screenshot shared to 밤낮 from the gallery while an investor calendar is on screen is a 체결 내역
 * capture. The 투자 tab takes it and reads it on the phone (결정 D68); nothing changes until the user fills the prices.
 */
object IncomingFills {
    private val _shot = MutableStateFlow<Uri?>(null)
    val shot: StateFlow<Uri?> = _shot.asStateFlow()

    fun put(uri: Uri) { _shot.value = uri }

    /** The waiting capture, once. */
    fun take(): Uri? = _shot.value.also { _shot.value = null }
}
