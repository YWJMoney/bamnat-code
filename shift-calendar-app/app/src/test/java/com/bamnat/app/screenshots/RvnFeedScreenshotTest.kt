package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.company.TalkPost
import com.bamnat.app.ui.company.FeedCard
import com.bamnat.app.ui.company.FeedMenu
import com.bamnat.core.time.Today
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * 2.0.0 갈래 N [RVN-06]: the 광장 cards as the page shows them: a long name with its marks (⋮ stays at the end, COMP-08), a post with a
 * 개인 정보 신고 folded (COMP-02), my post hidden from others with 이의 제기, and the ⋮ menu's 신고하기 opening the report reasons (COMP-01·COMP-10).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvnFeedScreenshotTest : ScreenshotBase() {
    private val now = Today.instant()
    private val posts = listOf(
        TalkPost("p1", 1, "ok", true, "4조 2교대 야간 이틀째인데 낮잠은 몇 시간 자세요? 저는 세 시간 넘으면 밤에 더 졸려요.",
            now.minus(Duration.ofMinutes(12)), false, nick = "새벽세시까지버티는간호사선생님", kind = "question", author = "a1", agree = 3),
        TalkPost("p2", 2, null, false, "제 번호로 연락 주세요", now.minus(Duration.ofHours(2)), false, nick = "야간요정", author = "a2", review = true),
        TalkPost("p3", 3, null, false, "우리 병동 수간호사님 이름이…", now.minus(Duration.ofHours(5)), true, hidden = "개인 정보가 있어요"),
    )

    private fun feed(menu: Boolean): Screen.() -> Unit = {
        companyOn()
        val flagging = androidx.compose.runtime.mutableStateOf<TalkPost?>(null)
        val m = FeedMenu(onFlag = { flagging.value = it }, onHide = {}, onDelete = {}, room = "style-shift", roomName = "교대 광장")
        content {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    posts.forEach { p -> FeedCard(p, emptyList(), m, onOpen = {}, onVote = {}) }
                }
            }
            com.bamnat.app.ui.company.FlagPostDialog(flagging.value, invest = false, onDismiss = { flagging.value = null }) { _, _ -> }
        }
        settle()
        if (menu) {
            // The first card's ⋮ (not mine), then 신고하기 in its menu, a popup window: the card's own way to the report reasons.
            tapText("이 글 더 보기", prefix = true, scrolls = 0)
            settle()
            rvnPopupNode("신고하기").config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!.invoke()
            settle()
            check(appears("이 글을 신고할까요?", exact = true)) { "신고 창이 열리지 않았어요" }
        }
    }

    @Test fun feedLight() = shoot("rvn_20_feed_folded", FOLDED, feed(false))
    @Test fun feedDarkBig() = shoot("rvn_21_feed_folded_dark_200", DARK, 2f, feed(false))
    @Test fun feedWide() = shoot("rvn_22_feed_unfolded", UNFOLDED, feed(false))
    @Test fun feedBigLight() = shoot("rvn_23_feed_folded_200", FOLDED, 2f, feed(false))
    @Test fun feedFlag() = shoot("rvn_24_feed_flag_folded", FOLDED, feed(true))

    private companion object {
        const val DARK = "w369dp-h905dp-port-night-420dpi"
    }
}
