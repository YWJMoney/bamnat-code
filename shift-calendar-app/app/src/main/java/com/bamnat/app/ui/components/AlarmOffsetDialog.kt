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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
 * "울릴 시각" turns the clock (06:30); "시작 몇 시간 전" turns hours and minutes before the start (1시간 30분). Both say the
 * other underneath, and the shortcuts below set the common ones in one tap. It replaced chips and a ± stepper, which
 * the user found "너무 기초적이고 불편".
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
    var minutes by rememberSaveable { mutableIntStateOf(initialMinutes.coerceIn(0, AlarmOffsets.MAX_MINUTES)) }
    var byClock by rememberSaveable { mutableIntStateOf(1) }
    // Bumped when a shortcut or the other tab sets the value, so the wheels jump to it.
    var jump by rememberSaveable { mutableIntStateOf(0) }
    val ring = AlarmOffsets.ringAt(start, minutes)
    val taken = minutes in existing
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(selected = byClock == 1, onClick = { byClock = 1; jump++ },
                        shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("울릴 시각") }
                    SegmentedButton(selected = byClock == 0, onClick = { byClock = 0; jump++ },
                        shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("시작 몇 시간 전") }
                }
                Text(
                    if (taken) "이미 있는 알람이에요" else alarmLine(start, minutes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (taken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Box(Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT * 5), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.fillMaxWidth().height(WHEEL_ROW_HEIGHT).clip(MaterialTheme.shapes.medium)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                    )
                    if (byClock == 1) {
                        // A new wheel for each tab, so it starts on the value the other tab left.
                        androidx.compose.runtime.key("clock") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                WheelHalf(count = 24, value = ring.hour, jump = jump, label = "시",
                                    onValue = { h -> minutes = AlarmOffsets.offsetFor(start, LocalTime.of(h, AlarmOffsets.ringAt(start, minutes).minute)) })
                                WheelHalf(count = 60, value = ring.minute, jump = jump, label = "분",
                                    onValue = { m -> minutes = AlarmOffsets.offsetFor(start, LocalTime.of(AlarmOffsets.ringAt(start, minutes).hour, m)) })
                            }
                        }
                    } else {
                        androidx.compose.runtime.key("before") {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                WheelHalf(count = 24, value = minutes / 60, jump = jump, label = "시간", cyclic = false,
                                    onValue = { h -> minutes = (h * 60 + minutes % 60).coerceAtMost(AlarmOffsets.MAX_MINUTES) })
                                WheelHalf(count = 60, value = minutes % 60, jump = jump, label = "분",
                                    onValue = { m -> minutes = (minutes / 60 * 60 + m).coerceAtMost(AlarmOffsets.MAX_MINUTES) })
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    for (m in shortcuts) {
                        FilterChip(selected = minutes == m, enabled = m !in existing, onClick = { minutes = m; jump++ },
                            label = { Text(Fmt.offset(m)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(minutes) }, enabled = !taken) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** "06:30에 울려요 · 시작 1시간 30분 전", "전날 23:30에 울려요 · …", "시작 시각에 울려요". */
internal fun alarmLine(start: LocalTime, minutes: Int): String {
    if (minutes == 0) return "${Fmt.time(start)} 시작 시각에 울려요"
    val ring = AlarmOffsets.ringAt(start, minutes)
    return (if (AlarmOffsets.dayBefore(start, minutes)) "전날 " else "") + "${Fmt.time(ring)}에 울려요 · 시작 ${Fmt.offset(minutes)}"
}

/** The common ones, one tap each. */
val ALARM_SHORTCUTS = listOf(10, 30, 60, 90, 120, 180)
