package com.bamnat.app.widget

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.alarm.PlannedAlarm
import com.bamnat.core.work.CalendarFocus
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.FieldMode
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.Job
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.RestLine
import com.bamnat.core.work.WidgetFigures
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.shadows.ShadowLog
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVIB-03] (총괄 전달, 갈래 M RVM-06) A Glance Row or Column keeps its first 10 children and drops the rest, saying so only in
 * the log ("Truncated … to 10 elements"). The widgets are drawn here with every line they can have on, and none may be cut.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class WidgetChildLimitTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    // A Friday: work today, the weekend off, Monday's alarm further off.
    private val today = LocalDate.of(2026, 11, 13)
    private val work = ShiftType(id = 1, name = "일근", label = "일", colorArgb = 0xFF2F80ED, start = LocalTime.of(9, 0), end = LocalTime.of(18, 0))
    private val off = ShiftType(id = 2, name = "휴무", label = "휴", colorArgb = 0xFF9E9E9E, start = null, end = null, isOff = true)
    private val resolver = ScheduleResolver(listOf(work, off), listOf(WorkPattern.Rotation(LocalDate.of(2026, 11, 9), listOf(1L, 1L, 1L, 1L, 1L, 2L, 2L))), emptyList())
    private val monday = PlannedAlarm(LocalDateTime.of(2026, 11, 16, 7, 30), LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), 1, "일근", 90, null)

    @Before fun clean() = ShadowLog.reset()

    private fun draw(size: DpSize, content: @androidx.compose.runtime.Composable () -> Unit): List<String> = runBlocking {
        val views = GlanceRemoteViews().compose(context, size) { WidgetLookTheme { content() } }
        val cut = ShadowLog.getLogs().filter { it.msg?.contains("Truncated") == true }.map { it.msg }
        assertEquals("Glance dropped children: $cut", emptyList<String>(), cut)
        val texts = mutableListOf<String>()
        fun walk(v: View) {
            if (v is TextView && v.visibility == View.VISIBLE) texts += v.text.toString()
            if (v is ViewGroup) for (i in 0 until v.childCount) walk(v.getChildAt(i))
        }
        walk(views.remoteViews.apply(context, android.widget.FrameLayout(context)))
        texts
    }

    @Test fun anOfficeFridayKeepsMondaysAlarm() {
        val data = WidgetData(
            today, DayOfWeek.MONDAY, resolver, hasPattern = true, nextAlarm = monday,
            restLine = RestLine.Line("23:00에 자면 7시간", sleeping = false, changesAt = null),
            style = WorkStyle.DAY, leaveLeft = 5.0, overtime = WidgetFigures.Overtime(41 * 60, 57),
            memos = mapOf(today to "회의 3시", today.plusDays(1) to "장보기"),
        )
        val texts = draw(DpSize(250.dp, 320.dp)) { WidgetFrame { TodayContent(data) } }
        assertTrue(texts.toString(), texts.any { it.startsWith("⏰") })
        assertTrue(texts.toString(), texts.any { it.contains("지금 퇴근") })
        assertTrue(texts.toString(), texts.any { it.contains("남은 연차") })
    }

    @Test fun aQuietFieldWeekKeepsTheButton() {
        val data = WidgetData(
            today, DayOfWeek.MONDAY, resolver, hasPattern = true, nextAlarm = null,
            focus = CalendarFocus.JOBS, style = WorkStyle.FIELD,
            jobs = listOf(Job(id = 1, date = today.plusDays(5), title = "타일 시공")),
            fieldWeek = WidgetFigures.FieldWeek(3, null, 0), fieldRules = FieldRules(mode = FieldMode.DAY_RATE, dayRate = 150_000),
        )
        val texts = draw(DpSize(250.dp, 320.dp)) { WidgetFrame { TodayContent(data) } }
        assertTrue(texts.toString(), texts.any { it.startsWith("다음 일감") })
        assertTrue(texts.toString(), texts.any { it.startsWith("오늘 ") && it.contains("공") })
    }

    @Test fun theOtherWidgetsStayUnderTen() {
        val data = WidgetData(today, DayOfWeek.MONDAY, resolver, hasPattern = true, nextAlarm = monday,
            teams = (1..8).map { WidgetData.TeamToday("${it}조", it == 1, work, off) }, coverTeams = listOf("2조", "3조"),
            countdown = "일근까지 14시간", nextOff = "다음 휴무까지 1일")
        draw(DpSize(320.dp, 320.dp)) { WidgetFrame { WeekContent(data) } }
        draw(DpSize(250.dp, 200.dp)) { WidgetFrame { CountdownContent(data) } }
        val teams = draw(DpSize(250.dp, 400.dp)) { WidgetFrame { TeamsContent(data) } }
        assertTrue(teams.toString(), teams.any { it.startsWith("들어올 수 있는 조") })
    }
}
