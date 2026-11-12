package com.bamnat.app.ui.sleep

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.design.DayStoryItems
import com.bamnat.core.design.StoryKind
import com.bamnat.core.sleep.SleepDebt
import com.bamnat.core.sleep.SleepEntry
import com.bamnat.core.sleep.SleepHead
import com.bamnat.core.sleep.SleepInsights
import com.bamnat.core.sleep.SleepPlan
import com.bamnat.core.sleep.SleepSchedule
import com.bamnat.core.time.Today
import java.time.LocalDate

/**
 * 1.39 [FLOW-09] 수면 화면 머리: the last 7 days' average as the one big number, one line against the target under it
 * ([SleepHead]). The three same-size tiles it replaces said the same in three places.
 */
@Composable
internal fun SleepHeadCard(r: SleepInsights.Report, target: Int) {
    val h = SleepHead.of(r.week, target)
    SectionCard(title = null) {
        Text("최근 7일 평균", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        com.bamnat.app.ui.components.HeroNumber(h.value, caption = h.caption, tone = com.bamnat.app.ui.components.storyTone(h.tone))
    }
}

/**
 * 1.39 [FLOW-11] 주 띠로 날 고르기 (DSG-06 [com.bamnat.app.ui.components.WeekStrip]): seven days with today on the right, a dot in
 * the meaning colour under each day with a sleep (목표만큼 초록, 1시간 넘게 모자람 주황, the same rule as the 2주 막대), ‹ › for the
 * weeks before. Under it the picked day's 잠 이야기 줄 (FLOW-08's story, its work and sleep only) and that day's records;
 * 모든 기록 opens the last 4 weeks as before.
 */
@Composable
internal fun SleepDays(s: SleepViewModel.State, settings: AppSettings, target: Int, onOpen: (SleepEntry) -> Unit) {
    val today = s.today
    var page by rememberSaveable { mutableIntStateOf(0) }
    var picked by rememberSaveable { mutableStateOf(today.toString()) }
    var all by rememberSaveable { mutableStateOf(false) }
    val day = LocalDate.parse(picked)
    val end = today.minusDays(7L * page)
    val days = (6 downTo 0).map { end.minusDays(it.toLong()) }
    val byDay = remember(s.log) {
        s.log.filter { (it.minutes ?: 0) in 1..SleepDebt.MAX_SLEEP_MINUTES }.groupBy { it.wakeDate }
            .mapValues { (_, l) -> l.sumOf { it.minutes!! } }
    }
    val marks = days.mapNotNull { d ->
        val m = byDay[d] ?: return@mapNotNull null
        d to when {
            m < target - SleepInsights.SHORT_MINUTES -> Tone.Caution
            m >= target - SleepHead.EVEN_MINUTES -> Tone.Good
            else -> Tone.Plain
        }
    }.toMap()
    val dayRecords = s.log.filter { e -> (e.wakeDate ?: e.start.toLocalDate()) == day }
    SectionCard(title = "날마다 잠") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page++; picked = today.minusDays(7L * page).toString() }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "지난주")
            }
            com.bamnat.app.ui.components.WeekStrip(days, day, marks, today, onPick = { picked = it.toString() }, modifier = Modifier.weight(1f))
            IconButton(onClick = { page--; picked = today.minusDays(7L * page).toString() }, enabled = page > 0) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 주")
            }
        }
        Text("${Fmt.dayTitle(day)} · " + (byDay[day]?.let { "${SleepInsights.hours(it)} 잤어요" } ?: "기록 없음"),
            style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
        // The day's work and sleep (and the sleep planned around the work, dashed) on one line.
        val story = remember(s.resolver, day, s.log, settings.sleepTargetMinutes, settings.commuteMinutes, settings.alarmsEnabled) {
            val res = s.resolver ?: return@remember emptyList()
            runCatching {
                val tips = SleepPlan.tips(SleepSchedule.of(res, day, day.plusDays(1), settings.alarmsEnabled,
                    settings.sleepTargetMinutes, settings.commuteMinutes), day)
                val near = s.log.filter { it.start.toLocalDate() in day.minusDays(1)..day }
                DayStoryItems.of(day, res, sleeps = near, plans = tips)
                    .filter { it.kind == StoryKind.Shift || it.kind == StoryKind.Sleep || it.kind == StoryKind.SleepPlan }
            }.getOrDefault(emptyList())
        }
        if (story.any { it.kind == StoryKind.Sleep || it.kind == StoryKind.SleepPlan }) {
            com.bamnat.app.ui.components.StoryLine(day, story, if (day == today) Today.dateTime() else null,
                modifier = Modifier.padding(top = 4.dp))
        }
        RecordRows(if (all) s.log.filter { e -> (e.wakeDate ?: e.start.toLocalDate()) >= today.minusDays(SleepInsights.WINDOW_DAYS - 1) }
            else dayRecords, s.report, onOpen)
        TextButton(onClick = { all = !all }, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(if (all) "고른 날만 보기" else "최근 ${SleepInsights.WINDOW_DAYS}일 기록 모두 보기")
        }
    }
}

/**
 * 1.39 [SLEEP-15][P3-L-18] 팀원 쪽 피곤해요: only when a received team roster's leader asked for 피곤해요 합계. One press a day; the
 * leader sees a nameless team count (none under 3 people). Sleep records never go.
 */
@Composable
internal fun TiredCard(saidToday: Boolean, onSay: () -> Unit) {
    SectionCard(title = "팀에 피곤해요 알리기") {
        Text(if (saidToday) "오늘 알렸어요. 내일 다시 누를 수 있어요."
            else "많이 피곤한 날 한 번 눌러요. 리더는 이름 없이 팀 합계만 봐요(3명이 안 되면 숫자도 없어요). 잠 기록은 가지 않아요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onSay, enabled = !saidToday, modifier = Modifier.padding(top = 8.dp).heightIn(min = 48.dp)) {
            Text(if (saidToday) "오늘 알림" else "오늘 피곤해요")
        }
    }
}

/** The day 피곤해요 was last pressed, on this phone only. */
internal object TiredStore {
    private const val PREFS = "sleep_tired"
    private const val K_DAY = "day"
    fun saidToday(c: android.content.Context): Boolean =
        c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getString(K_DAY, null) == Today.date().toString()
    fun said(c: android.content.Context) =
        c.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).edit().putString(K_DAY, Today.date().toString()).apply()
}
