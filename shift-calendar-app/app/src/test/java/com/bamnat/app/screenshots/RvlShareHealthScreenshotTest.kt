package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.share.ReceivedShare
import com.bamnat.app.share.ShareApi
import com.bamnat.app.share.ShareLinkException
import com.bamnat.app.share.ShareLinkState
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 2.0.0 [RVL-78] (검토 I12c-02) 갈래 L 13차 디자인 품질 조건 사진: 받은 달력의 끝나 가는 링크 카드와 아래 한 줄, 끝난 링크의 '새 링크 부탁하기',
 * 보낸 쪽 공유 창의 '지난 링크는 …에 끝났어요'. 밝은·어두운 × 글자 100%·200% × 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlShareHealthScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** 민지's link, ending in a day: the card under the calendar asks her to keep it open, one press. */
    private fun endsSoon(wide: Boolean = false): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val share = minjiShare().let { ReceivedShare(it.schedule, Today.instant().plus(Duration.ofHours(20)), Today.instant().minus(Duration.ofDays(2))) }
        shares.links[minjiId] = share
        cc.received.put(minjiId, share)
        settle()
        cc.received.view(minjiId)
        settle()
        val across = if (wide) 0.75f else 0.5f
        reveal("연장 부탁하기", exact = true, across = across)
        assertTrue(appears("이 링크는 내일 끝나요", exact = false) || appears("이 링크는 오늘 끝나요", exact = false))
        reveal("보낸 분이 마지막으로 고친 날", exact = false, across = across)
    }
    @Test fun endsSoonFoldLight() = shoot("rvl78_ends_soon_fold_light_100", FOLDED, endsSoon())
    @Test fun endsSoonFoldDarkHuge() = shoot("rvl78_ends_soon_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, endsSoon())
    @Test fun endsSoonOpenLight() = shoot("rvl78_ends_soon_open_light_100", UNFOLDED, endsSoon(wide = true))

    /** The card closed with X stays closed for that end date (조건 3: an answered card does not stay big). */
    @Test fun endsSoonClosed() = shoot("rvl78_ends_soon_closed_fold_light_100", FOLDED) {
        endsSoon().invoke(this)
        tapText("이 안내 닫기", exact = true)
        assertTrue(gone("연장 부탁하기", exact = true))
    }

    /** The sender cut it: the gone card's one filled button asks for a new link. */
    private fun goneLink(): Screen.() -> Unit = {
        val cc = receivedOn(minji = false)
        val share = minjiShare()
        cc.received.put(minjiId, share)
        cc.received.markGone(minjiId)
        settle()
        cc.received.view(minjiId)
        settle()
        assertTrue(appears("공유가 끝났어요", exact = true))
        assertTrue(appears("새 링크 부탁하기", exact = true))
    }
    @Test fun goneFoldLight() = shoot("rvl78_gone_fold_light_100", FOLDED, goneLink())
    @Test fun goneFoldDarkHuge() = shoot("rvl78_gone_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, goneLink())

    private class GoneApi : ShareApi {
        override val enabled = true
        override suspend fun create(payload: JSONObject): ShareLinkState = throw ShareLinkException("unused")
        override suspend fun update(state: ShareLinkState, payload: JSONObject): ShareLinkState = throw ShareLinkException("not_found")
        override suspend fun delete(state: ShareLinkState) = Unit
        override suspend fun read(id: String) = throw ShareLinkException("not_found")
    }

    /** The sender's last link stopped by itself: the share sheet says so above '링크로 보내기'. */
    private fun senderEnded(): Screen.() -> Unit = {
        c.shareLinks.clear()
        c.shareLinks.typedName = null
        c.shareLinks.ended(Today.instant().minus(Duration.ofDays(1)))
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = GoneApi(), onOpenReceived = {}) }
        settle()
        reveal("링크로 보내기", exact = true)
        assertTrue(appears("에 끝났어요. 새 링크를 보내면 받은 분들이 다시 볼 수 있어요", exact = false))
    }
    @Test fun senderEndedFoldLight() = shoot("rvl78_sender_ended_fold_light_100", FOLDED, senderEnded())
    @Test fun senderEndedFoldDarkHuge() = shoot("rvl78_sender_ended_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, senderEnded())

    companion object {
        const val FOLDED = "w369dp-h905dp-port-420dpi"
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED = "w707dp-h823dp-port-420dpi"
    }
}
