package com.bamnat.app.ui.components

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.BamnatTheme
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 2.0.0 [RVIB-06] (PC-83, 통합 2차 확인 n-04·n-05) In the alarm window's '시작 기준' tab the shortcuts ('1시간 전', '1시간 30분 전') left the
 * line and the wheels at '시작 10분 전'; the '울릴 시각' tab's did not. A shortcut moves the line, both wheels and what 완료 saves.
 */
@RunWith(AndroidJUnit4::class)
class AlarmOffsetChipTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun aShortcutMovesTheLineAndTheWheels() {
        var saved: Int? = null
        val start = LocalTime.of(9, 0)
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent { BamnatTheme { AlarmOffsetDialog("알람 더하기", start, 10, onDismiss = {}, onPick = { saved = it }) } }
            }
            compose.waitForIdle()
            compose.onNodeWithText("시작 기준").performClick()
            compose.waitForIdle()
            for (m in listOf(90, 60, 120)) {
                compose.onNodeWithText(Fmt.offset(m)).performClick()
                compose.waitForIdle()
                compose.onNodeWithText(alarmLine(start, m)).assertIsDisplayed()
                compose.onNodeWithContentDescription("%02d시간".format(m / 60)).assertIsDisplayed()
                compose.onNodeWithContentDescription("%02d분".format(m % 60)).assertIsDisplayed()
            }
            compose.onNodeWithText("완료").performClick()
            compose.waitForIdle()
            assertEquals(120, saved)
        }
    }
}
