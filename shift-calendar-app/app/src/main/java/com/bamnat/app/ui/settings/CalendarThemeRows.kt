package com.bamnat.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.recipe.ColorSwatch
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.recipe.CalendarTheme

/** The 설정 row title, also in [SettingsSearch]. */
const val CAL_THEME_ROW = "달력 색"

/**
 * [RCP-09] 1.34 달력 테마: today's circle and the 토·일·공휴일 colours, and how a working day's cell is coloured. A 나눔 판 recipe
 * sets the same; '처음 색으로' brings the app's own back.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CalendarThemeRows(encoded: String, onChange: (String) -> Unit) {
    val theme = CalendarTheme.decode(encoded)
    val cal = LocalCalendarColors.current
    val scheme = MaterialTheme.colorScheme
    fun own(c: androidx.compose.ui.graphics.Color) = c.toArgb().toLong() and 0xFFFFFFFFL
    Column(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(CAL_THEME_ROW, style = MaterialTheme.typography.bodyLarge)
        Text("오늘 동그라미와 토·일·공휴일 글자색, 근무 날 칸의 색칠을 골라요. 어두운 화면에서는 잘 보이게 밝혀서 그려요",
            style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            ColorSwatch(theme.today ?: own(scheme.primary), "오늘", { onChange(theme.copy(today = it).encode()) })
            ColorSwatch(theme.sat ?: own(cal.saturday), "토요일", { onChange(theme.copy(sat = it).encode()) })
            ColorSwatch(theme.sun ?: own(cal.sunday), "일요일", { onChange(theme.copy(sun = it).encode()) })
            ColorSwatch(theme.holiday ?: theme.sun ?: own(cal.sunday), "공휴일", { onChange(theme.copy(holiday = it).encode()) })
        }
    }
    ChoiceRow("근무 날 칸", "근무가 있는 날 칸을 어떻게 칠할지 골라요",
        listOf("tint" to "옅게 (처음)", "fill" to "진하게", "dot" to "칠하지 않기"), theme.cell ?: "tint") { c ->
        onChange(theme.copy(cell = c.takeIf { it != "tint" }).encode())
    }
    androidx.compose.foundation.layout.Row {
        if (!theme.isDefault) TextButton(onClick = { onChange("") }) { Text("처음 색으로") }
        com.bamnat.app.ui.recipe.ShareThisSetting(com.bamnat.core.recipe.PieceKind.CAL_THEME)
    }
}
