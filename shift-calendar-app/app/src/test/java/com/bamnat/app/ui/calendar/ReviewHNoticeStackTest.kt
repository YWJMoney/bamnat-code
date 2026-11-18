package com.bamnat.app.ui.calendar

import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.MainActivity
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.consent.ConsentItem
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** 2.0.0 [RVH-21] 검토 CAL-26: the notices above the month never open all at once, not even on the first frame. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35], qualifiers = "w360dp-h640dp")
class ReviewHNoticeStackTest {
    @get:Rule(order = Int.MIN_VALUE) val mainReset = com.bamnat.app.rules.ComposeMainReset.rule()
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()

    @Test fun severalNoticesNeverStandOpenTogether() {
        app.container.consentStore.markSeen(ConsentItem.entries)
        val heights = mutableListOf<Int>()
        var card = 0
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a ->
                card = (100 * a.resources.displayMetrics.density).toInt()
                a.setContent {
                    BamnatTheme {
                        Column {
                            NoticeStack(
                                listOf("a", "b", "c").map { k -> Notice(k, "알림 $k", urgent = true) { Box(Modifier.fillMaxWidth().height(100.dp)) } },
                                Modifier.onSizeChanged { heights += it.height },
                            )
                        }
                    }
                }
            }
            compose.waitForIdle()
        }
        assertTrue("$heights", heights.isNotEmpty())
        // 2.0.0 [RVP-45] One band (56dp and its margin), the rest in the bell; three cards would be 300dp.
        assertTrue("$heights vs $card", heights.all { it < card * 2 })
    }

    /**
     * [RVH-21] 통합 합친 뒤 CI '큰 글자 달력' 사진: a notice that drew and then went quiet left the next one folded away, as the count
     * was kept in each notice's own layout and the quiet one was measured again alone. The next notice comes up in its place (2.0.0
     * [RVP-45]: the band says the notice's line; a band already up gives its place to the next one even once settled, PC-81).
     */
    @Test fun aNoticeThatGoesQuietGivesItsPlaceToTheNext() {
        app.container.consentStore.markSeen(ConsentItem.entries)
        var first by mutableStateOf(true)
        ActivityScenario.launch(MainActivity::class.java).use { s ->
            s.onActivity { a ->
                a.setContent {
                    BamnatTheme {
                        Column {
                            NoticeStack(listOf(
                                Notice("a", "알림 a", line = "첫 알림", urgent = true) { if (first) Box(Modifier.fillMaxWidth().height(80.dp)) { Text("첫 알림 전체") } },
                                Notice("b", "알림 b", line = "둘째 알림", urgent = true) { Box(Modifier.fillMaxWidth().height(80.dp)) { Text("둘째 알림 전체") } },
                            ))
                        }
                    }
                }
            }
            compose.waitForIdle()
            compose.onNodeWithText("첫 알림").assertExists()
            first = false
            compose.waitForIdle()
            compose.onNodeWithText("둘째 알림").assertExists()
            compose.onNodeWithText("둘째 알림").assertIsDisplayed()
        }
    }
}
