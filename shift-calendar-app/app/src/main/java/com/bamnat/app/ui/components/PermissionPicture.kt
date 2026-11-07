package com.bamnat.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import com.bamnat.app.ui.theme.LocalCalendarColors
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 1.39 [FLOW-05] (흐름·디자인 계획 3-2): what a permission does, drawn, on the card that asks it the first time it is needed
 * (EASE-05) — the result, not the system's words. Simple shapes only, sharp at any size, no image files and nothing from
 * another app. A screen reader hears what the picture shows.
 */
enum class PermissionResult(val says: String) {
    /** 알림: the alarm big on the lock screen with its 끄기. */
    LOCK_ALARM("잠금 화면에 알람이 크게 뜨고 끄기 단추가 보이는 그림"),
    /** 휴대폰 캘린더: the shifts' colours in the phone's own calendar. */
    PHONE_CALENDAR("휴대폰 캘린더에 근무 색이 칠해진 그림"),
    /** 마이크: the night's sounds as a short wave on the phone. */
    SLEEP_SOUND("밤사이 소리가 물결로 남는 그림"),
}

@Composable
fun PermissionPicture(result: PermissionResult, accent: Color, ink: Color, modifier: Modifier = Modifier, height: Dp = 64.dp) {
    Canvas(modifier.size(width = height * 0.62f, height = height).semantics { contentDescription = result.says; role = Role.Image }) {
        val w = size.width
        val h = size.height
        val r = w * 0.16f
        // The phone.
        drawRoundRect(ink.copy(alpha = 0.92f), size = Size(w, h), cornerRadius = CornerRadius(r))
        drawRoundRect(accent.copy(alpha = 0.55f), topLeft = Offset(w * 0.05f, h * 0.035f), size = Size(w * 0.9f, h * 0.93f),
            cornerRadius = CornerRadius(r * 0.8f), style = Stroke(width = w * 0.02f))
        when (result) {
            PermissionResult.LOCK_ALARM -> lockAlarm(accent)
            PermissionResult.PHONE_CALENDAR -> phoneCalendar(accent)
            PermissionResult.SLEEP_SOUND -> sleepSound(accent)
        }
    }
}

private fun DrawScope.lockAlarm(accent: Color) {
    val w = size.width
    val h = size.height
    // The time, as three bold bars, then the shift line, then the round 끄기.
    val bar = h * 0.07f
    listOf(0.22f, 0.40f, 0.58f).forEach { x -> drawRoundRect(Color.White, Offset(w * x, h * 0.2f), Size(w * 0.14f, h * 0.15f), CornerRadius(bar / 2)) }
    drawRoundRect(Color.White.copy(alpha = 0.55f), Offset(w * 0.24f, h * 0.42f), Size(w * 0.52f, bar * 0.6f), CornerRadius(bar / 2))
    drawCircle(accent, radius = w * 0.17f, center = Offset(w / 2, h * 0.72f))
    drawRoundRect(Color.White, Offset(w * 0.42f, h * 0.705f), Size(w * 0.16f, h * 0.03f), CornerRadius(h * 0.015f))
}

private fun DrawScope.phoneCalendar(accent: Color) {
    val w = size.width
    val h = size.height
    val cell = w * 0.17f
    val gap = w * 0.03f
    val left = (w - cell * 4 - gap * 3) / 2
    for (row in 0 until 5) for (col in 0 until 4) {
        val on = (row * 4 + col) % 3 != 2
        drawRoundRect(if (on) accent else Color.White.copy(alpha = 0.25f),
            Offset(left + col * (cell + gap), h * 0.16f + row * (cell + gap)), Size(cell, cell), CornerRadius(cell * 0.25f))
    }
}

private fun DrawScope.sleepSound(accent: Color) {
    val w = size.width
    val h = size.height
    val heights = listOf(0.06f, 0.12f, 0.2f, 0.1f, 0.05f, 0.16f, 0.24f, 0.09f)
    val step = w * 0.8f / heights.size
    heights.forEachIndexed { i, k ->
        val x = w * 0.1f + i * step + step * 0.2f
        drawRoundRect(accent, Offset(x, h * 0.5f - h * k / 2), Size(step * 0.6f, h * k), CornerRadius(step * 0.3f))
    }
    drawCircle(Color.White.copy(alpha = 0.8f), radius = w * 0.08f, center = Offset(w * 0.72f, h * 0.2f))
}

/**
 * 1.39 [FLOW-05] The card that asks a permission where it is first needed: the picture of what it gives at its top left, the one
 * line of why beside it and one button. Same colours as [WarningBanner], whose place it takes there.
 */
@Composable
fun PermissionAskCard(result: PermissionResult, title: String, message: String, action: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    val cal = LocalCalendarColors.current
    val ink = contentColorOn(cal.warningContainer)
    Surface(shape = MaterialTheme.shapes.medium, color = cal.warningContainer, contentColor = ink, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 12.dp, end = 4.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.Top) {
            PermissionPicture(result, accent = cal.warning, ink = MaterialTheme.colorScheme.inverseSurface)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(message, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = onAction, modifier = Modifier.padding(top = 2.dp)) { Text(action) }
            }
        }
    }
}
