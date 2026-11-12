package com.bamnat.app.ui.components

import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performCustomAccessibilityActionWithLabel
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.BamnatTheme
import java.time.LocalTime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVR-18] (검토 TEST-10) Each window with time wheels, turned the way a person does and confirmed: the value 완료 hands back is
 * the one the wheels show. Until now only the fling arithmetic had tests, so a window that saved another value than it showed (1.39.1
 * R391-01: the alarm window's '시작 기준' wheels kept the minutes of their first frame and wrote old hours back) passed every test.
 *
 * The wheels turn by their TalkBack steps (1시간 늘리기·1분 줄이기, the same scroll a tap on a row makes) and once by a finger's swipe.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class TimeWheelDialogsTest {
    @get:Rule val compose = createEmptyComposeRule()

    private var wasClock12 = false

    @Before fun twentyFourHours() {
        wasClock12 = Fmt.clock12
        Fmt.clock12 = false
    }

    @After fun putBack() {
        Fmt.clock12 = wasClock12
    }

    private fun window(content: @Composable () -> Unit, test: () -> Unit) {
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a -> a.setContent { BamnatTheme { content() } } }
            compose.waitForIdle()
            test()
        }
    }

    /** Turns the wheel named [label] by [steps] rows (up the numbers when positive), waiting for each to settle. */
    private fun turn(label: String, steps: Int) {
        val unit = if (label == "시") "1시간" else "1$label"
        val way = if (steps > 0) "늘리기" else "줄이기"
        repeat(kotlin.math.abs(steps)) {
            compose.onNodeWithTag("wheel:$label").performCustomAccessibilityActionWithLabel("$unit $way")
            compose.waitForIdle()
        }
    }

    private fun press(label: String) {
        compose.onNodeWithText(label).performClick()
        compose.waitForIdle()
    }

    @Test fun theClockWindowGivesBackTheTimeItsWheelsShow() {
        var got: LocalTime? = null
        window({ TimePickerDialog("출근 시각", LocalTime.of(9, 0), onDismiss = {}) { got = it } }) {
            turn("시", 3)
            turn("분", 2)
            // The line above the wheels says the same time the wheels show.
            compose.onNodeWithText("오후 12시 2분").assertExists()
            press("완료")
            assertEquals(LocalTime.of(12, 2), got)
        }
    }

    @Test fun theHourWheelRunsPastMidnightLikeAClock() {
        var got: LocalTime? = null
        window({ TimePickerDialog("퇴근 시각", LocalTime.of(23, 0), onDismiss = {}) { got = it } }) {
            turn("시", 3)
            turn("분", -1)
            press("완료")
            assertEquals(LocalTime.of(2, 59), got)
        }
    }

    @Test fun theTwelveHourWheelsGiveBackTheAfternoon() {
        Fmt.clock12 = true
        var got: LocalTime? = null
        window({ TimePickerDialog("출근 시각", LocalTime.of(9, 0), onDismiss = {}) { got = it } }) {
            turn("", 1) // 오전 → 오후
            turn("시", 2)
            turn("분", 2)
            compose.onNodeWithText("오후 11시 2분").assertExists()
            press("완료")
            assertEquals(LocalTime.of(23, 2), got)
        }
    }

    /** A slow swipe of two rows turns two hours: the finger's own way, no flick on top. */
    @Test fun aSwipeOfTwoRowsTurnsTwoHours() {
        var got: LocalTime? = null
        window({ TimePickerDialog("출근 시각", LocalTime.of(9, 30), onDismiss = {}) { got = it } }) {
            compose.onNodeWithTag("wheel:시").performTouchInput {
                val row = WHEEL_ROW_HEIGHT.toPx()
                swipeUp(startY = centerY + row, endY = centerY - row, durationMillis = 900)
            }
            compose.waitForIdle()
            press("완료")
            assertEquals(LocalTime.of(11, 30), got)
        }
    }

    @Test fun theShiftTimeWindowKeepsEachTabsTime() {
        var got: TimeRange? = null
        val day = TimeRange(LocalTime.of(9, 0), LocalTime.of(18, 0), 60)
        window({ TimeRangeDialog("근무 시간", day, onDismiss = {}, onConfirm = { got = it }) }) {
            turn("시", -1)
            turn("분", 2)
            press("끝")
            turn("시", 1)
            turn("분", 30)
            press("완료")
            assertEquals(TimeRange(LocalTime.of(8, 2), LocalTime.of(19, 30), 60), got)
        }
    }

    @Test fun theAlarmWindowByClockGivesBackTheMinutesBeforeTheStart() {
        var got: Int? = null
        // A 07:00 shift with its alarm an hour before (06:00).
        window({ AlarmOffsetDialog("알람", LocalTime.of(7, 0), initialMinutes = 60, onDismiss = {}) { got = it } }) {
            turn("시", -3)
            turn("분", 2)
            press("완료")
            // 03:02 is 3시간 58분 before 07:00.
            assertEquals(3 * 60 + 58, got)
        }
    }

    /** 1.39.1 R391-01: the minutes wheel after the hours wheel must not put the old hours back. */
    @Test fun theAlarmWindowFromTheStartKeepsBothWheels() {
        var got: Int? = null
        window({ AlarmOffsetDialog("알람", LocalTime.of(7, 0), initialMinutes = 60, onDismiss = {}) { got = it } }) {
            press("시작 기준")
            turn("시간", 1)
            turn("분", 2)
            turn("시간", 1)
            press("완료")
            assertEquals(3 * 60 + 2, got)
        }
    }

    @Test fun theLengthWindowGivesBackItsMinutesAndStopsPastTheMost() {
        var got: Int? = null
        window({ DurationDialog("휴게 시간", initialMinutes = 90, maxMinutes = 4 * 60, onDismiss = {}) { got = it } }) {
            turn("시간", 1)
            turn("분", 2)
            press("확인")
            assertEquals(2 * 60 + 32, got)
            // 4시간 32분 is past the 4 hours this one allows: 확인 waits.
            turn("시간", 2)
            compose.onNodeWithText("확인").assertIsNotEnabled()
            turn("분", -32)
            compose.onNodeWithText("확인").assertIsEnabled()
            press("확인")
            assertEquals(4 * 60, got)
        }
    }
}
