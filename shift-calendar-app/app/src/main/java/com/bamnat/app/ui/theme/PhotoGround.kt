package com.bamnat.app.ui.theme

import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

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

    /**
     * 6차 통합 2eb28e83 PC 확인 (칩 줄 바탕이 반투명이라 아래로 지나가는 카드가 아직 비쳐 칩 글자와 겹쳐요): a bar that a list scrolls
     * under (붙는 칩 줄, 가계부 날짜 머리) is opaque on a picture: the photo's average with the veil laid on it, so no card shows through
     * and the words, chosen for that ground, still read.
     */
    @Composable
    fun solid(normal: Color = MaterialTheme.colorScheme.background): Color {
        if (!on()) return normal
        val veil = bar(normal)
        val ground = com.bamnat.app.ui.decor.LocalPhotoGround.current?.copy(alpha = 1f) ?: veil.copy(alpha = 1f)
        return lerp(ground, veil.copy(alpha = 1f), veil.alpha)
    }

    /**
     * 7차 통합 347b7c37 PC 제안 (어두운 사진에서 빨간 금액이 창 위에 겹쳐요): a list line on a picture (가계부 한 줄) stands on a thicker
     * veil than a bar, so its coloured amount reads over the photo's bright and dark spots alike.
     */
    @Composable
    fun row(normal: Color = MaterialTheme.colorScheme.surface): Color =
        if (!on()) normal else if (MaterialTheme.colorScheme.onSurface.luminance() > 0.5f) Color.Black.copy(alpha = 0.74f) else Color.White.copy(alpha = 0.9f)

    /**
     * 8차 통합 e690bef6 PC (메모 찾기 칸에 사진의 달이 비쳐요): a search field on a picture fills with the opaque ground; the app's own
     * look otherwise.
     */
    @Composable
    fun fieldColors(): androidx.compose.material3.TextFieldColors =
        if (!on()) androidx.compose.material3.OutlinedTextFieldDefaults.colors()
        else solid(MaterialTheme.colorScheme.surface).let { g -> androidx.compose.material3.OutlinedTextFieldDefaults.colors(focusedContainerColor = g, unfocusedContainerColor = g) }

    /**
     * 8차 통합 e690bef6 PC (근무·급여 머리와 달력 머리의 '>'·아이콘 위에 사진의 달이 걸려요): a head row (달 이름과 넘기기) on a picture stands on
     * the opaque ground, rounded; nothing otherwise.
     */
    @Composable
    fun head(top: androidx.compose.ui.unit.Dp = 0.dp): androidx.compose.ui.Modifier {
        if (!on()) return androidx.compose.ui.Modifier
        val c = solid(MaterialTheme.colorScheme.surface)
        // Drawn behind (and under a 꾸밈 층 laid on the row), [top] left as it was so the row's own spacing stays.
        return androidx.compose.ui.Modifier.drawBehind {
            val y = top.toPx()
            drawRoundRect(c, topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, size.height - y),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()))
        }
    }
}
