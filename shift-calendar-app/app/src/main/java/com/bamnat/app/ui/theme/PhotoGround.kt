package com.bamnat.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * 1.38 PC 스튜디오 확인 (배경 사진 테마를 입으면 장터 위 칩 줄이 투명해서 스크롤한 카드가 비치고 글자가 안 읽혀요): a 꾸밈 사진 under the
 * screen makes the page's background and surface see-through ([ScreenSkin]). What stays on top of a moving list (붙는 칩 줄, 탭 줄, 날짜 머리)
 * or carries accent words (오늘의 하나) stands on this veil instead: dark under light words, light under dark ones, so the words read.
 */
object PhotoGround {
    /** Whether the screen's ground is a picture (its background colour see-through). */
    @Composable
    fun on(): Boolean = MaterialTheme.colorScheme.background.alpha < 0.99f

    /** The ground for a bar over a list: the page's own colour, or on a picture a veil the words read on. */
    @Composable
    fun bar(normal: Color = MaterialTheme.colorScheme.background): Color =
        if (!on()) normal else if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color.Black.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.84f)
}
