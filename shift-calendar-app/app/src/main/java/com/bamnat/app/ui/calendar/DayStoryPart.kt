package com.bamnat.app.ui.calendar

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.design.StoryItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepPlan
import com.bamnat.core.time.Today
import java.time.LocalDate

/**
 * 1.38 [FLOW-08] 내 하루 이야기, worked out in one place for every spot that shows it (1.39 [FLOW-22]: 날짜 화면, 달력 아래 카드): the shifts
 * (last night's too), the alarms that will ring, sleep, money and memos of [date]. Null when 하루 이야기 is off in settings, or it would
 * say no more than the work card unless [always].
 */
internal fun dayStoryItems(
    snapshot: ScheduleSnapshot,
    date: LocalDate,
    settings: AppSettings,
    calendarAlarms: Boolean,
    sleepLog: List<SleepEntry>,
    money: List<MoneyEntry>,
    memos: List<Memo>,
    tips: List<SleepPlan.Tip>,
    always: Boolean = false,
): List<StoryItem>? {
    if (!settings.dayStory) return null
    return runCatching {
        val ring = settings.alarmsEnabled && calendarAlarms
        val alarmsAround = if (!ring) emptyList() else listOf(date, date.plusDays(1))
            .flatMap { com.bamnat.core.alarm.AlarmPlanner.alarmsOn(snapshot.resolver, it) }
        val near = sleepLog.filter { it.start.toLocalDate() in date.minusDays(1)..date }
        val items = com.bamnat.core.design.DayStoryItems.of(date, snapshot.resolver, alarmsAround, near, tips, money,
            com.bamnat.core.memo.MemoStory.items(memos, date),
            dayStartsAt = com.bamnat.core.design.DayBoundary.startAt(settings.dayStartMinutes)) // 1.39 [FLOW-30]
        items.takeIf { always || com.bamnat.core.design.DayStoryItems.worthShowing(com.bamnat.core.design.DayStory.arrange(date, it)) }
    }.getOrNull()
}

/**
 * 1.39 [FLOW-22] 하루 이야기를 부품으로: the picked day's story as a card under (or over) the month, placed from 카드 배치 like the other
 * calendar cards. Off until the user adds it ([CalendarPart.DAY_STORY]); 설정 › 화면과 글자 › 하루 이야기 off hides it too. A memo on the
 * line opens that memo. A day with only its shift says so in one line instead of an empty frame (DSG-09).
 */
@Composable
internal fun DayStoryCalendarCard(
    vm: CalendarViewModel,
    snapshot: ScheduleSnapshot,
    date: LocalDate,
    onOpenMemo: (Long, LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    if (!settings.dayStory) return
    val memos by remember(date) { vm.memosOn(date) }.collectAsStateWithLifecycle(emptyList())
    val book by vm.moneyBook.collectAsStateWithLifecycle()
    val sleepLog by vm.sleepLog.collectAsStateWithLifecycle()
    val calendarAlarms by vm.calendarAlarmsOn.collectAsStateWithLifecycle()
    val tips = remember(snapshot, date, settings) { if (settings.sleepTips) vm.sleepTips(snapshot, date, settings) else emptyList() }
    val dayEntries = remember(book, date) { book.on(date).entries }
    val story = remember(snapshot, date, settings, calendarAlarms, sleepLog, dayEntries, memos, tips) {
        dayStoryItems(snapshot, date, settings, calendarAlarms, sleepLog, dayEntries, memos, tips, always = true)
    }
    val day = remember(snapshot, date) { snapshot.resolver.resolve(date) }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${date.monthValue}월 ${date.dayOfMonth}일 하루 이야기", style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f).padding(bottom = 6.dp).semantics { heading() })
            }
            val events = story.orEmpty()
            if (events.isEmpty() || com.bamnat.core.design.DayStory.arrange(date, events).isEmpty()) {
                Text("이날은 아직 적힌 것이 없어요. 잠·돈·메모를 적으면 시간 순서대로 이어져요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 8.dp))
            } else {
                com.bamnat.app.ui.components.StoryLine(
                    day = date, events = events, now = Today.dateTime(),
                    onOpen = { e -> com.bamnat.core.memo.MemoStory.memoIdOf(e)?.let { onOpenMemo(it, date) } },
                    shiftColor = day.shiftType?.let { Color((it.colorArgb or 0xFF000000L).toInt()) } ?: MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
