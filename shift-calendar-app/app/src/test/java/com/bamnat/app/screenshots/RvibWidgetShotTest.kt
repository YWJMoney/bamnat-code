package com.bamnat.app.screenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.ExperimentalGlanceRemoteViewsApi
import androidx.glance.appwidget.GlanceRemoteViews
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.widget.CountdownContent
import com.bamnat.app.widget.TeamsContent
import com.bamnat.app.widget.TodayContent
import com.bamnat.app.widget.WidgetData
import com.bamnat.app.widget.WidgetFrame
import com.bamnat.app.widget.WidgetLookTheme
import com.bamnat.core.alarm.PlannedAlarm
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.money.FieldMode
import com.bamnat.core.money.FieldRules
import com.bamnat.core.money.Job
import com.bamnat.core.schedule.ScheduleResolver
import com.bamnat.core.sleep.RestLine
import com.bamnat.core.work.CalendarFocus
import com.bamnat.core.work.WidgetFigures
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * 2.0.0 [RVIB-03] (총괄 전달, 갈래 M RVM-06) The widgets with every line they can have on, as the home screen draws them: Glance's
 * RemoteViews put into a view and drawn. The three that lost their last line (일근 금요일, 현장 조용한 주, 8조), light and dark, text
 * 100% and 200%. The same file shoots the widgets before the change (the PR's 앞·뒤 사진).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w369dp-h905dp-port-420dpi")
@OptIn(ExperimentalGlanceRemoteViewsApi::class)
class RvibWidgetShotTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val today = LocalDate.of(2026, 11, 13)
    private val work = ShiftType(id = 1, name = "일근", label = "일", colorArgb = 0xFF2F80ED, start = LocalTime.of(9, 0), end = LocalTime.of(18, 0))
    private val off = ShiftType(id = 2, name = "휴무", label = "휴", colorArgb = 0xFF9E9E9E, start = null, end = null, isOff = true)
    private val resolver = ScheduleResolver(listOf(work, off), listOf(WorkPattern.Rotation(LocalDate.of(2026, 11, 9), listOf(1L, 1L, 1L, 1L, 1L, 2L, 2L))), emptyList())
    private val monday = PlannedAlarm(LocalDateTime.of(2026, 11, 16, 7, 30), LocalDate.of(2026, 11, 16), LocalTime.of(9, 0), 1, "일근", 90, null)

    private class Look(val tag: String, val night: Boolean, val font: Float)
    private val looks = listOf(Look("l100w", false, 1f), Look("l200w", false, 2f), Look("d100w", true, 1f), Look("d200w", true, 2f))

    @After fun back() {
        RuntimeEnvironment.setQualifiers("+notnight")
        RuntimeEnvironment.setFontScale(1f)
    }

    private fun shoot(name: String, size: DpSize, content: @Composable () -> Unit) {
        for (l in looks) {
            RuntimeEnvironment.setQualifiers(if (l.night) "+night" else "+notnight")
            RuntimeEnvironment.setFontScale(l.font)
            val views = runBlocking { GlanceRemoteViews().compose(context, size) { WidgetLookTheme { content() } } }
            val density = context.resources.displayMetrics.density
            val w = (size.width.value * density).toInt()
            val h = (size.height.value * density).toInt()
            // A launcher's page around it, so the widget's own edges and corners show.
            val pad = (16 * density).toInt()
            val host = FrameLayout(context).apply { setPadding(pad, pad, pad, pad) }
            host.addView(views.remoteViews.apply(context, host), FrameLayout.LayoutParams(w, h))
            val spec = { px: Int -> View.MeasureSpec.makeMeasureSpec(px, View.MeasureSpec.EXACTLY) }
            host.measure(spec(w + 2 * pad), spec(h + 2 * pad))
            host.layout(0, 0, w + 2 * pad, h + 2 * pad)
            val bmp = Bitmap.createBitmap(w + 2 * pad, h + 2 * pad, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            canvas.drawColor(if (l.night) 0xFF1E2430.toInt() else 0xFF8FA3B8.toInt())
            host.draw(canvas)
            val out = File("build/screenshots/rvib_widget_${name}_${l.tag}.png").apply { parentFile!!.mkdirs() }
            out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    // 일근 금요일, 다 켬: 다음 알람(월요일), 잘 시간, 남은 연차, 이번 주 연장, 메모 둘, 지금 퇴근.
    @Test fun officeFriday() {
        val data = WidgetData(
            today, DayOfWeek.MONDAY, resolver, hasPattern = true, nextAlarm = monday,
            restLine = RestLine.Line("23:00에 자면 7시간", sleeping = false, changesAt = null),
            style = WorkStyle.DAY, leaveLeft = 5.0, overtime = WidgetFigures.Overtime(41 * 60, 57),
            memos = mapOf(today to "회의 3시", today.plusDays(1) to "장보기"),
        )
        shoot("office_friday", DpSize(250.dp, 320.dp)) { WidgetFrame { TodayContent(data) } }
    }

    // 현장 조용한 주: 다음 일감 하나, 이번 주 공수, 오늘 공수 적기.
    @Test fun quietFieldWeek() {
        val data = WidgetData(
            today, DayOfWeek.MONDAY, resolver, hasPattern = true, nextAlarm = null,
            focus = CalendarFocus.JOBS, style = WorkStyle.FIELD,
            jobs = listOf(Job(id = 1, date = today.plusDays(5), title = "타일 시공")),
            fieldWeek = WidgetFigures.FieldWeek(3, null, 0), fieldRules = FieldRules(mode = FieldMode.DAY_RATE, dayRate = 150_000),
        )
        shoot("field_week", DpSize(250.dp, 320.dp)) { WidgetFrame { TodayContent(data) } }
    }

    // 8조: 조마다 오늘 근무, 들어올 수 있는 조.
    @Test fun eightTeams() {
        val data = WidgetData(today, DayOfWeek.MONDAY, resolver, hasPattern = true, nextAlarm = monday,
            teams = (1..8).map { WidgetData.TeamToday("${it}조", it == 1, if (it % 3 == 0) off else work, off) }, coverTeams = listOf("2조", "3조"),
            countdown = "일근까지 14시간", nextOff = "다음 휴무까지 1일")
        shoot("teams_8", DpSize(250.dp, 400.dp)) { WidgetFrame { TeamsContent(data) } }
        shoot("countdown", DpSize(250.dp, 200.dp)) { WidgetFrame { CountdownContent(data) } }
    }
}
