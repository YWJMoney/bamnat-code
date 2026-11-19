package com.bamnat.wear

import androidx.compose.ui.graphics.Color

/**
 * 2.0.0 [RVI-11] The watch's few colours by what they mean, so a screen names a meaning and not a number (디자인 품질 조건 5).
 * The watch has its own dark look; the phone's theme tokens are not here.
 */
internal object WearTone {
    /** Body lines on the black watch face. */
    val Body = Color(0xFFD6DAE3)
    /** Secondary lines: the date, when it was sent. */
    val Muted = Color(0xFF9AA3B5)
    /** Hints under everything. */
    val Hint = Color(0xFF6E7687)
    /** 주의: an alarm turned off, a send that did not reach the phone. */
    val Caution = Color(0xFFFFC266)
}
