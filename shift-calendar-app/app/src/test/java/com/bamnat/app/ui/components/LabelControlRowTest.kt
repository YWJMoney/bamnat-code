package com.bamnat.app.ui.components

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.memo.QuickTaskActivity
import com.bamnat.app.ui.theme.BamnatTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVH-81] 검토 HQ-98: a row's name never stands one letter per line beside its control. Beside it while the name fits on
 * one line; under it, at the end, when the column is narrow (편 폭의 달력 옆 하루 칸).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w400dp-h640dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class LabelControlRowTest {
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private fun window(content: @Composable () -> Unit, test: () -> Unit) {
        ActivityScenario.launch(QuickTaskActivity::class.java).use { s ->
            s.onActivity { a -> a.setContent { BamnatTheme { content() } } }
            compose.waitForIdle()
            test()
        }
    }

    private fun row(width: Int) = @Composable {
        Box(Modifier.width(width.dp)) {
            LabelControlRow("휴게 시간") { Box(Modifier.size(150.dp, 48.dp).testTag("ctl")) }
        }
    }

    @Test fun aWideRowKeepsTheNameBesideTheControl() = window(row(360)) {
        val name = compose.onNodeWithText("휴게 시간").getUnclippedBoundsInRoot()
        val ctl = compose.onNodeWithTag("ctl").getUnclippedBoundsInRoot()
        assertTrue("나란히: 이름 ${name.top}..${name.bottom}, 조절 ${ctl.top}..${ctl.bottom}", name.top >= ctl.top && name.bottom <= ctl.bottom)
        assertEquals(360f, ctl.right.value, 0.5f)
    }

    @Test fun aNarrowRowPutsTheControlUnderTheNameOnOneLine() = window(row(170)) {
        val name = compose.onNodeWithText("휴게 시간").getUnclippedBoundsInRoot()
        val ctl = compose.onNodeWithTag("ctl").getUnclippedBoundsInRoot()
        assertTrue("조절은 이름 밑: 이름 ${name.left}..${name.right}/${name.top}..${name.bottom}, 조절 ${ctl.left}..${ctl.right}/${ctl.top}..${ctl.bottom}", ctl.top >= name.bottom)
        // One line: at bodyLarge the name is about 24dp high; letter per line would be four times that.
        val h = (name.bottom - name.top).value
        assertFalse("세로로 선 이름 $h", h > 40f)
        assertEquals(170f, ctl.right.value, 0.5f)
    }
}
