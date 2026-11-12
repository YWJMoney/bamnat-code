package com.bamnat.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.Fmt
import com.bamnat.core.alarm.AlarmOffsets
import java.time.LocalTime

/**
 * [ALRM-06] 1.25 Z (U14): when a shift's alarm rings, on the same hour and minute wheels as every other time in the app.
 * "울릴 시각" turns the clock (06:30); "시작 기준" (1.37 "시작 몇 시간 전") turns hours and minutes before the start (1시간 30분),
 * or [ALRM-28] after it. Both say the
 * other underneath, and the shortcuts below set the common ones in one tap. It replaced chips and a ± stepper, which
 * the user found "너무 기초적이고 불편". [EASE-18] 1.38: "울릴 시각" also picks the day, 전날·당일·다음날, so an alarm can ring
 * after the start too (a nap after a 야간, stored below zero).
 */
@Composable
fun AlarmOffsetDialog(
    title: String,
    /** The shift's start. */
    start: LocalTime,
    /** Minutes before [start] the alarm rings now. */
    initialMinutes: Int,
    onDismiss: () -> Unit,
    /** Alarms the shift already has (minutes before), which cannot be added twice. */
    existing: Set<Int> = emptySet(),
    confirmLabel: String = "완료",
    shortcuts: List<Int> = ALARM_SHORTCUTS,
    onPick: (Int) -> Unit,
) {
    // [EASE-18] 1.38: the clock time and which day (전날·당일·다음날) are kept, and the minutes before the start follow;
    // a time more than a day from the start either way has none (and 완료 waits).
    val first = initialMinutes.coerceIn(AlarmOffsets.MIN_MINUTES, AlarmOffsets.MAX_MINUTES)
    var ringMin by rememberSaveable { mutableIntStateOf(AlarmOffsets.ringAt(start, first).let { it.hour * 60 + it.minute }) }
    var day by rememberSaveable { mutableIntStateOf(AlarmOffsets.dayOf(start, first)) }
    var byClock by rememberSaveable { mutableIntStateOf(1) }
    // Bumped when a shortcut or the other tab sets the value, so the wheels jump to it.
    var jump by rememberSaveable { mutableIntStateOf(0) }
    val ring = LocalTime.of(ringMin / 60, ringMin % 60)
    val picked: Int? = AlarmOffsets.offsetFor(start, ring, day)
    val minutes = picked ?: 0
    fun setMinutes(m: Int) {
        val v = m.coerceIn(AlarmOffsets.MIN_MINUTES, AlarmOffsets.MAX_MINUTES)
        val r = AlarmOffsets.ringAt(start, v)
        ringMin = r.hour * 60 + r.minute
        day = AlarmOffsets.dayOf(start, v)
    }
    val taken = picked != null && picked in existing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
          ScrollMoreColumn {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // [RVH-46] Words on two lines or stacked at a large font, never cut.
                ChoiceRow(listOf("울릴 시각", "시작 기준"), selected = if (byClock == 1) 0 else 1,
                    onSelect = { byClock = if (it == 0) 1 else 0; jump++ })
                Text(
                    when {
                        picked == null -> "근무 시작과 하루 넘게 떨어진 때는 맞출 수 없어요"
                        taken -> "이미 있는 알람이에요"
                        else -> alarmLine(start, minutes)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (taken || picked == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                // [TIME-03] What 완료 will change, as in the other time windows (PC a9bcce14: only this one lacked it).
                TimeChangeLine(
                    if (picked == null || minutes == first) null
                    else timeChangeNote(null, AlarmOffsets.ringAt(start, first), ring)
                        ?: Fmt.startOffset(minutes).let { "${Fmt.startOffset(first)} → $it${Fmt.ro(it)} 바뀌어요" },
                )
                if (byClock == 1) {
                    // [EASE-18] Which day it rings: 전날 for an early start, 다음날 for a nap after a 야간.
                    ChoiceRow(listOf(-1, 0, 1).map(AlarmOffsets::dayWord), selected = day + 1, onSelect = { day = it - 1 })
                } else {
                    // [ALRM-28] 1.38: hours after the start too (a nap after a 야간), not only from '울릴 시각'.
                    ChoiceRow(listOf("시작 전", "시작 뒤"), selected = if (minutes >= 0) 0 else 1, onSelect = {
                        if (it == 0 && minutes < 0) { setMinutes(-minutes); jump++ }
                        if (it == 1 && minutes >= 0) { setMinutes(-(minutes.coerceAtLeast(60))); jump++ }
                    })
                }
                Box(Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT * 5), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT).clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    if (byClock == 1) {
                        // A new wheel for each tab, so it starts on the value the other tab left.
                        androidx.compose.runtime.key("clock") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                WheelHalf(count = 24, value = ringMin / 60, jump = jump, label = "시",
                                    onValue = { h -> ringMin = h * 60 + ringMin % 60 })
                                WheelHalf(count = 60, value = ringMin % 60, jump = jump, label = "분",
                                    onValue = { m -> ringMin = ringMin / 60 * 60 + m })
                            }
                        }
                    } else {
                        // [ALRM-28] The wheels turn the distance from the start; the switch above says before or after.
                        val sign = if (minutes < 0) -1 else 1
                        val gap = kotlin.math.abs(minutes)
                        androidx.compose.runtime.key("before", sign) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                WheelHalf(count = 24, value = gap / 60, jump = jump, label = "시간", cyclic = false,
                                    onValue = { h -> setMinutes(sign * (h * 60 + kotlin.math.abs(minutes) % 60).coerceAtMost(AlarmOffsets.MAX_MINUTES)) })
                                WheelHalf(count = 60, value = gap % 60, jump = jump, label = "분",
                                    onValue = { m -> setMinutes(sign * (kotlin.math.abs(minutes) / 60 * 60 + m).coerceAtMost(AlarmOffsets.MAX_MINUTES)) })
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in shortcuts) {
                        FilterChip(selected = picked == m, enabled = m !in existing, onClick = { setMinutes(m); jump++ },
                            label = { Text(Fmt.offset(m), maxLines = 1, softWrap = false) })
                    }
                }
            }
          }
        },
        confirmButton = { TextButton(onClick = { picked?.let(onPick) }, enabled = picked != null && !taken) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** "06:30에 울려요 · 시작 1시간 30분 전", "전날 23:30에 울려요 · …", "다음날 14:00에 울려요 · 시작 16시간 뒤", "시작 시각에 울려요". */
internal fun alarmLine(start: LocalTime, minutes: Int): String {
    if (minutes == 0) return "${Fmt.time(start)} 시작 시각에 울려요"
    return "${alarmClock(start, minutes)}에 울려요 · " + Fmt.startOffset(minutes)
}

/** [EASE-18] When a shift's alarm rings, with the day when it is not the shift's own: "06:30", "전날 23:30", "다음날 14:00". */
fun alarmClock(start: LocalTime, minutes: Int): String {
    val d = AlarmOffsets.dayOf(start, minutes)
    return (if (d == 0) "" else AlarmOffsets.dayWord(d) + " ") + Fmt.time(AlarmOffsets.ringAt(start, minutes))
}

/** The common ones, one tap each. */
val ALARM_SHORTCUTS = listOf(10, 30, 60, 90, 120, 180)
