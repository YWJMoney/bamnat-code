package com.bamnat.wear

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Recomposer
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.MaterialTheme
import com.bamnat.core.sleep.RestLine
import com.bamnat.core.time.Today
import com.bamnat.core.work.WatchDay
import com.bamnat.core.work.WatchPayload
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 2.0.0 갈래 I (총괄 #453): the watch's 오늘 page on a round watch, small (192dp, Pixel Watch) and large (225dp), at the
 * watch's usual and largest text (1.24), into build/screenshots. 오늘 알람 끄기 in its three steps ([RVI-11]: 한 번 더 누르면
 * 꺼요, 껐어요 · 되돌리기, 못 껐어요) and the words of a 가게, 현장 and 투자 calendar ([RVI-09] 영업·일정, no 52시간 line;
 * [RVI-18] the line by the next alarm). The shots are square; the tools draw the round edge on them.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class WatchScreenshotTest {
    @get:Rule val compose = createEmptyComposeRule()

    private class Look(val tag: String, val qualifiers: String, val font: Float)

    private val looks = listOf(
        Look("s100", "w192dp-h192dp-round-watch-xhdpi", 1f), Look("s124", "w192dp-h192dp-round-watch-xhdpi", 1.24f),
        Look("l100", "w225dp-h225dp-round-watch-xhdpi", 1f), Look("l124", "w225dp-h225dp-round-watch-xhdpi", 1.24f),
    )

    private val seoul = ZoneId.of("Asia/Seoul")
    private val day = LocalDate.of(2026, 10, 14)

    @After fun reset() {
        WatchStore.sendForTest = null
        Today.reset()
    }

    private fun at(t: LocalDateTime) = Today.fix(ZonedDateTime.of(t, seoul))

    private fun d(date: LocalDate, name: String, label: String, color: Long, s: String? = null, e: String? = null, off: Boolean = false) =
        WatchDay(date, name, label, color, s?.let(LocalTime::parse), e?.let(LocalTime::parse), off)

    /** A 교대 calendar at 05:20 with the 06:00 주간 alarm still to ring. */
    private fun shift(now: LocalDateTime) = WatchPayload(
        days = listOf(
            d(day, "주간", "주", 0xFF2F80ED, "07:00", "19:00"), d(day.plusDays(1), "야간", "야", 0xFF5B4FCF, "19:00", "07:00"),
            d(day.plusDays(2), "비번", "비", 0xFF999999, off = true), d(day.plusDays(3), "휴무", "휴", 0xFF999999, off = true),
        ),
        syncedAt = now.minusMinutes(20), nextAlarm = day.atTime(6, 0), alarmLabel = "주간 근무", calendar = "내 달력",
        weekMinutes = 24 * 60, alarmToday = true,
    )

    /** A 가게, 현장 or 투자 calendar the evening before, as the phone's WatchSync sends it: its own word, no 52시간 line. */
    private fun ownWork(noun: String, today: WatchDay?, tomorrow: WatchDay, alarm: LocalDateTime, label: String, now: LocalDateTime) = WatchPayload(
        days = listOfNotNull(today, tomorrow),
        syncedAt = now.minusMinutes(5), nextAlarm = alarm, alarmLabel = label, calendar = "내 달력",
        weekMinutes = -1, sleepLine = RestLine.of(now, emptyList(), alarm, true, 7 * 60)?.text.orEmpty(), noun = noun,
    )

    private fun shoot(name: String, p: WatchPayload, actions: () -> Unit = {}) {
        for (l in looks) {
            RuntimeEnvironment.setQualifiers(l.qualifiers)
            RuntimeEnvironment.setFontScale(l.font)
            compose.mainClock.autoAdvance = false
            val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
            try {
                val activity = controller.get()
                activity.setContent { MaterialTheme { Box(Modifier.fillMaxSize().background(Color.Black)) { TodayPage(p, refreshing = false, onRefresh = {}) } } }
                settle()
                actions()
                settle()
                val view = activity.window.decorView
                val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bmp))
                val out = File("build/screenshots/watch_${name}_${l.tag}.png").apply { parentFile!!.mkdirs() }
                out.outputStream().use { s -> bmp.compress(Bitmap.CompressFormat.PNG, 100, s) }
            } finally {
                controller.pause().stop().destroy()
            }
        }
    }

    /** Less than 5 seconds of the compose clock in all, so 한 번 더 누르면 꺼요 does not run out before the shot. */
    private fun settle() {
        val looper = shadowOf(Looper.getMainLooper())
        for (round in 1..12) {
            Snapshot.sendApplyNotifications()
            looper.idle()
            compose.mainClock.advanceTimeBy(100)
            if (round >= 3 && Recomposer.runningRecomposers.value.none { it.hasPendingWork } && looper.isIdle) break
        }
    }

    /**
     * The round list scrolls to [text] with an animation, which needs the clock to run: with it held ([shoot]), waitForIdle waited
     * for the scroll for ever.
     */
    private fun showButton(text: String) {
        compose.mainClock.autoAdvance = true
        try {
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
        } finally {
            compose.mainClock.autoAdvance = false
        }
        settle()
    }

    private fun tap(text: String) {
        compose.onNodeWithText(text).performClick()
        settle()
    }

    // The today list's lines keep their words whole with word joiners ([whole]); they are read without them.
    private fun appears(text: String) = compose.onAllNodes(SemanticsMatcher("text has $text") { n ->
        n.config.getOrNull(SemanticsProperties.Text).orEmpty().any { it.text.replace("\u2060", "").contains(text) }
    }).fetchSemanticsNodes().isNotEmpty()

    @Test fun shiftToday() {
        val now = day.atTime(5, 20); at(now)
        shoot("shift_today", shift(now)) { showButton("오늘 알람 끄기") }
    }

    @Test fun shiftArmed() {
        val now = day.atTime(5, 20); at(now)
        shoot("shift_armed", shift(now)) {
            showButton("오늘 알람 끄기")
            tap("오늘 알람 끄기")
            assertTrue(appears("한 번 더 누르면 꺼요"))
        }
    }

    @Test fun shiftOff() {
        val now = day.atTime(5, 20); at(now)
        WatchStore.sendForTest = { _, _ -> true }
        shoot("shift_off", shift(now)) {
            showButton("오늘 알람 끄기")
            tap("오늘 알람 끄기")
            tap("한 번 더 누르면 꺼요")
            assertTrue(appears("오늘 알람을 껐어요") && appears("되돌리기"))
        }
    }

    @Test fun shiftNotSent() {
        val now = day.atTime(5, 20); at(now)
        WatchStore.sendForTest = { _, _ -> false }
        shoot("shift_fail", shift(now)) {
            showButton("오늘 알람 끄기")
            tap("오늘 알람 끄기")
            tap("한 번 더 누르면 꺼요")
            assertTrue(appears("휴대폰과 연결되어 있지 않아 못 껐어요"))
        }
    }

    // 2.0.0 [RVIB-09] (총괄 SN-18) An emoji 근무 그림 ("e:🌙", 장터 작품 100개가 써요) shows as a letter in front of the name: "🌙 야간".
    @Test fun shiftEmoji() {
        val now = day.atTime(5, 20); at(now)
        val p = shift(now).let { s -> s.copy(days = s.days.mapIndexed { i, w -> when (i) { 0 -> w.copy(icon = "e:☀️"); 1 -> w.copy(icon = "e:🌙"); else -> w } }) }
        shoot("shift_emoji", p)
    }

    @Test fun shop() {
        val now = day.minusDays(1).atTime(22, 40); at(now)
        shoot("shop", ownWork("영업", d(day.minusDays(1), "영업", "영", 0xFFE67E22, "10:00", "22:00"), d(day, "영업", "영", 0xFFE67E22, "10:00", "22:00"),
            day.atTime(8, 30), "영업 준비", now)) { assertTrue(!appears("52시간")) }
    }

    @Test fun field() {
        val now = day.minusDays(1).atTime(21, 50); at(now)
        shoot("field", ownWork("일정", null, d(day, "A동 타일", "A", 0xFF27AE60, "07:30", "16:30"), day.atTime(5, 40), "A동 타일", now)) {
            assertTrue(appears("일정 정보 없음") && !appears("52시간"))
        }
    }

    @Test fun invest() {
        val now = day.minusDays(1).atTime(23, 10); at(now)
        shoot("invest", ownWork("일정", d(day.minusDays(1), "정규장", "정", 0xFF2D9CDB, "09:00", "15:30"), d(day, "정규장", "정", 0xFF2D9CDB, "09:00", "15:30"),
            day.atTime(7, 30), "장 시작 전", now)) { assertTrue(!appears("52시간") && !appears("근무")) }
    }
}
