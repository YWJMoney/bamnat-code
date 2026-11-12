package com.bamnat.app.ui.calendar

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.data.DayChange
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AppSheet
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.app.ui.components.EmptyState
import com.bamnat.app.ui.components.KaniMood
import com.bamnat.app.ui.components.SkeletonRows
import com.bamnat.core.model.DayEntry
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 2.0.0 [RVH-65] 검토 PC-73 최근 바꾼 기록 (달력 도구): the days of the calendar on screen changed by hand in the last [RECENT_DAYS] days,
 * across months, the one changed last first. Each line says what the day holds now (as '이 날 맞아요?' does, [TurnSlipAsk.dayWhat]) and its
 * last change with when; a tap opens the day (its 기록 has every change), and ↶ puts the day back with 되돌리기 on the bar (조건 10).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun RecentChangesSheet(calendarId: String, style: WorkStyle?, onOpenDay: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val repo = remember(calendarId) { c.calendars.repository(calendarId) }
    val since = remember { Today.millis() - RECENT_DAYS * DAY_MILLIS }
    val days by remember(repo, since) { repo.recentChanges(since) }.collectAsStateWithLifecycle(null)
    val snap by repo.snapshot.collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val zone = Today.zone()
    val fmt = remember { DateTimeFormatter.ofPattern("M/d HH:mm") }
    AppSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("최근 바꾼 기록", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("최근 ${RECENT_DAYS}일 동안 손으로 바꾼 날이에요. 누르면 그 날이 열려요.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val list = days
            val s = snap
            when {
                list == null || s == null -> SkeletonRows(4)
                list.isEmpty() -> EmptyState("아직 손으로 바꾼 날이 없어요", detail = "날짜를 눌러 근무나 시간을 바꾸면 여기에 모여요.", mood = KaniMood.Point)
                else -> list.forEachIndexed { i, (date, changes) ->
                    if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    val day = s.resolver.resolve(date)
                    val planned = s.shiftTypes.firstOrNull { it.id == s.resolver.patternShiftIdOn(date) }
                    val back = s.backWord
                    ChangedDay(
                        date = date,
                        what = TurnSlipAsk.dayWhat(day, style, TurnSlipAsk.alarmOff(day, planned)),
                        last = changes.first(),
                        more = changes.size - 1,
                        whenText = Instant.ofEpochMilli(changes.first().atMillis).atZone(zone).format(fmt),
                        back = back,
                        onOpen = { onOpenDay(date) },
                        onBack = day.entry?.takeUnless { it.isEmpty }?.let {
                            {
                                scope.launch {
                                    val was = repo.updateDay(date) { DayEntry(date) }
                                    AppSnack.show(
                                        if (back == "지우기") "${Fmt.monthDay(date)}에 바꾼 것을 지웠어요" else "${Fmt.monthDay(date)}을 $back 되돌렸어요",
                                        undo = { repo.restoreDay(date, was) },
                                    )
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

/** One day of 최근 바꾼 기록: what it holds now, its last change and when; ↶ only while it holds something of the user's. */
@Composable
private fun ChangedDay(
    date: LocalDate, what: String, last: DayChange, more: Int, whenText: String, back: String,
    onOpen: () -> Unit, onBack: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable(onClick = onOpen).heightIn(min = 56.dp).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("${Fmt.dayTitle(date)} · $what", style = MaterialTheme.typography.titleSmall)
            Text("$whenText · ${last.text}" + if (more > 0) " · 그 전 ${more}번" else "", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onBack != null) IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.Undo,
                contentDescription = if (back == "지우기") "${Fmt.monthDay(date)}에 바꾼 것 지우기" else "${Fmt.monthDay(date)}을 $back 되돌리기",
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** How far back 최근 바꾼 기록 looks: two months, the stretch a slip of the month turn could reach. */
internal const val RECENT_DAYS = 60L
private const val DAY_MILLIS = 24L * 60 * 60 * 1000
