package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.AdminState
import com.bamnat.app.tier.LaunchConfig
import com.bamnat.app.tier.Passes
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagSource
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * [FLAG-12][FLAG-13][FLAG-16] 묶음 AL's screenshots, numbers 1203~1219 (1.28 순번 454): the first question and the
 * kind-of-work screen with 투자 hidden, on a phone and on an admin phone given its 미리 보기 (marked); the 3.2 extra-yes card.
 * S 1.32 [SECX-01]: the 기능 상태 shots (1200~1202) went with the card.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ALScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** A 1.28 server's answer on the shot's day: 투자 hidden, 광장 글쓰기 rested, Play's approval not confirmed yet. */
    private fun server() {
        val now = Today.instant()
        c.remoteConfig.save(LaunchConfig.parse(JSONObject("""{"billingLive":false,
            "flags":{"billing":{"confirmed":{"play.subscription":"2026-10-13T00:00:00Z"}},
              "plaza.write":{"force":"off","why":"스팸 글이 몰려 잠시 막음","until":"2026-10-15T00:00:00Z"},
              "type.invest":{"force":"off","why":"증권사 캡처 인식 검증이 끝날 때까지"}},
            "serverTime":"$now"}"""), now))
        c.featureGate.refresh()
        until { c.flags.paused(com.bamnat.core.ops.ServerSwitch.PLAZA_WRITE) }
        settle()
    }

    /** The rows are worked out on the app's background scope: wait (2 s at most) until [ok]. */
    private fun until(ok: () -> Boolean) {
        repeat(200) { if (ok()) return; Thread.sleep(10) }
    }

    /** S 1.32 [SECX-01] An approved admin phone with the 미리 보기 the developer app set for it ([previews]). */
    private fun admin(vararg previews: String, owner: Boolean = true) {
        c.proStore.setPasses(Passes(null, 0, emptyList(), 0, AdminState("approved", "dd", owner = owner, previews = previews.toList()), false, Today.instant()))
        c.featureGate.refresh()
        until { c.phoneFacts().admin && c.phoneFacts().previews == previews.toSet() }
        settle()
    }

    // S 1.32 [SECX-01] 1200~1202 (진단's 기능 상태 card and its switches) are gone with the card.

    @Test fun ownerWithoutPreviewSeesNoHiddenKind() {
        server()
        admin()
        assertFalse("being the owner alone shows nothing hidden", c.flags.types().any { it.style == WorkStyle.INVEST })
        admin(FlagCatalog.LEAVE_HOURLY)
        until { c.flags.on(FlagCatalog.LEAVE_HOURLY) }
        assertEquals(FlagSource.ADMIN_PREVIEW, c.flags.state(FlagCatalog.LEAVE_HOURLY)!!.source)
        assertFalse(c.flags.statsOn().orEmpty().contains(FlagCatalog.LEAVE_HOURLY))
    }

    @Test fun phoneFirstQuestionHidesInvest() = shoot("1203_phone_first_question_hidden", PHONE) {
        server()
        assertFalse(c.flags.types().any { it.style == WorkStyle.INVEST })
        content { com.bamnat.app.ui.onboarding.FirstStyleScreen(onPick = {}, onSkip = {}) }
    }

    @Test fun phoneFirstQuestionOwner() = shoot("1204_phone_first_question_owner", PHONE) {
        server()
        admin("type.invest")
        assertTrue(c.flags.types().single { it.style == WorkStyle.INVEST }.hidden)
        content { com.bamnat.app.ui.onboarding.FirstStyleScreen(onPick = {}, onSkip = {}) }
        settle()
        scrollUp(900)
    }

    @Test fun phoneWorkStyleOwner() = shoot("1205_phone_work_style_owner", PHONE) {
        server()
        admin("type.invest")
        content { com.bamnat.app.ui.work.WorkStyleScreen(current = WorkStyle.SHIFT, onPick = {}) }
        settle()
        scrollUp(900)
    }

    @Test fun phoneWorkStyleInUseStays() = shoot("1206_phone_work_style_in_use", PHONE) {
        // A calendar already 투자 keeps its kind in the list after it was hidden; nothing else of it changes.
        server()
        content { com.bamnat.app.ui.work.WorkStyleScreen(current = WorkStyle.INVEST, onPick = {}) }
        settle()
        scrollUp(900)
    }

    @Test fun phoneStatsMoreCard() = shoot("1207_phone_stats_more", PHONE) {
        c.settingsRepository.update { it.copy(shareUsageStats = true, usageStatsMore = null) }
        val now = Today.instant()
        // 3.2 moved by the server to the shot's day, so the ask shows now.
        c.remoteConfig.save(LaunchConfig.parse(JSONObject("""{"flags":{},
            "policy":[{"version":"3.2","posted":"2026-09-14","effective":"2026-10-14","heldUntil":null}],"serverTime":"$now"}"""), now))
        c.featureGate.refresh()
        until { com.bamnat.app.ui.flags.statsMoreAsks(c) }
        settle()
        assertTrue(com.bamnat.app.ui.flags.statsMoreAsks(c))
        content {
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                Column(Modifier.statusBarsPadding().padding(top = 16.dp)) { com.bamnat.app.ui.flags.StatsMoreNotice() }
            }
        }
    }
}
