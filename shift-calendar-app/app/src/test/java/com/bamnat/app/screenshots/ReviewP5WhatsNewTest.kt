package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.guide.WhatsNew
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 갈래 P 5 (검토 PC-160: 1.39.0에서 2.0.0으로 올린 폰에 '새로 생긴 것'이 뜨지 않았어요. 판 번호 54의 줄이 없어 [WhatsNew.since]가
 * 빈 목록을 냈어요): the newest release in [WhatsNew.releases] is the one being built, and a phone coming from 1.39.0 (53) is told what
 * 2.0.0 brought once (the window, or its row in the 알림함 when another window has the turn), and not again on the next opening.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewP5WhatsNewTest : ScreenshotBase() {
    @Test fun theNewestReleaseIsThisVersion() {
        assertEquals(com.bamnat.app.BuildConfig.VERSION_CODE, WhatsNew.releases.first().versionCode)
        assertEquals(com.bamnat.app.BuildConfig.VERSION_NAME, WhatsNew.releases.first().versionName)
    }

    @Test fun anUpdateFrom139IsToldOnce() {
        app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        // The phone last saw 1.39.0's news.
        com.bamnat.app.data.GuideStore.get(app).whatsNewSeen(53)
        val title = WhatsNew.title(WorkStyle.SHIFT)
        shoot("rvp5_09_whats_new_from_139", FOLDED) {
            settle()
            // The window itself, or (another window having the turn) its row in the 알림함, which opens it.
            if (!appears(title, exact = true)) openNotice("새로 생긴 것")
            check(appears(title, exact = true)) { "1.39.0에서 올린 폰에 '새로 생긴 것'이 없어요" }
            tapText("확인", exact = true, scrolls = 0)
            check(gone(title, exact = true)) { "'새로 생긴 것'이 닫히지 않았어요" }
        }
        // The next opening: nothing again, neither the window nor its row.
        shoot("rvp5_10_whats_new_not_again", FOLDED) {
            settle()
            check(!shows(title, exact = true)) { "'새로 생긴 것'이 또 떴어요" }
            if (shows("알림 ", exact = false)) {
                tapText("알림 ", prefix = true, scrolls = 0)
                check(!shows("업데이트로 새로 생긴 것이 있어요")) { "알림함에 '새로 생긴 것'이 또 있어요" }
            }
        }
    }
}
