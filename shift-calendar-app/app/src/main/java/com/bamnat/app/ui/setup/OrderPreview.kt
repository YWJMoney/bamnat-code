package com.bamnat.app.ui.setup

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.core.model.ShiftType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/**
 * [EASE-14] 1.38 순서 만들기: the order being typed, repeated over the month at once, and the months before and after with
 * ‹ ›, so "D D E E N N O O" is checked against the wall roster before going on. [todayStep] is the step today falls on
 * (시작일): ‹ › beside "오늘은 n번째 날" moves it, and [onToday] tells the setup, which then asks only to confirm.
 */
@Composable
internal fun OrderPreview(
    steps: List<Long?>,
    types: Map<Long, ShiftType>,
    today: LocalDate,
    todayStep: Int,
    onToday: (Int) -> Unit,
    weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    /**
     * The calendar's own today (Today). [today] is the day the setup asks about, which is 어제 in the small hours; the line
     * names that day as it is ("어제(11/5)는 1번째 날"), so it and the ringed cell say the same day (1.38 통합 6차 2eb28e83).
     */
    realToday: LocalDate = today,
) {
    if (steps.isEmpty()) return
    var month by rememberSaveable { mutableStateOf(YearMonth.from(today).toString()) }
    val shown = YearMonth.parse(month)
    val n = steps.size
    val step = Math.floorMod(todayStep, n)
    val anchor = today.minusDays(step.toLong())
    val dayName = relativeDay(today, realToday)
    val dayObject = if (dayName == "오늘") "오늘을" else "${dayName}를"
    val dayWord = if (today == realToday) "오늘은" else "$dayName(${Fmt.shortDate(today)})는"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("$dayWord ", style = MaterialTheme.typography.bodyMedium)
            IconButton(onClick = { onToday(Math.floorMod(step - 1, n)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "$dayObject 앞 순서로") }
            val t = steps[step]?.let(types::get)
            ShiftBadge(t, size = 22.dp, text = t?.label ?: "휴", outlined = t == null || t.isOff)
            Text(" ${step + 1}번째 날", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { contentDescription = "$dayWord 순서의 ${step + 1}번째 날, ${t?.name ?: "휴무"}" })
            IconButton(onClick = { onToday(Math.floorMod(step + 1, n)) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "$dayObject 다음 순서로") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { month = shown.minusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "지난달") }
            Text("${shown.year}년 ${shown.monthValue}월", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { month = shown.plusMonths(1).toString() }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
        }
        val lead = Math.floorMod(shown.atDay(1).dayOfWeek.value - weekStart.value, 7)
        val cells = lead + shown.lengthOfMonth()
        Row { (0 until 7).map { weekStart.plus(it.toLong()) }.forEach { Text(Fmt.dayOfWeekShort(it), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall) } }
        for (week in 0 until (cells + 6) / 7) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                for (col in 0 until 7) {
                    val day = week * 7 + col - lead + 1
                    Box(Modifier.weight(1f).heightIn(min = 40.dp), contentAlignment = Alignment.Center) {
                        if (day in 1..shown.lengthOfMonth()) {
                            val date = shown.atDay(day)
                            val id = steps[Math.floorMod(ChronoUnit.DAYS.between(anchor, date), n.toLong()).toInt()]
                            val t = id?.let(types::get)
                            val isToday = date == today
                            Column(
                                Modifier.fillMaxWidth()
                                    .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp)) else Modifier)
                                    .padding(vertical = 2.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("$day", style = MaterialTheme.typography.labelSmall, fontWeight = if (isToday) FontWeight.Bold else null,
                                    color = if (isToday) MaterialTheme.colorScheme.primary else Color.Unspecified)
                                ShiftBadge(t, size = 20.dp, text = t?.label ?: "휴", outlined = t == null || t.isOff)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.width(0.dp))
        Text("다음 단계에서 $dayName 근무를 한 번 더 확인해요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The step today is on with the draft's answer, else the first. */
internal fun todayStepOf(anchor: LocalDate?, size: Int, today: LocalDate): Int =
    if (anchor == null || size == 0) 0 else Math.floorMod(ChronoUnit.DAYS.between(anchor, today), size.toLong()).toInt()
