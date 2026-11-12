package com.bamnat.app.tier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.flags.Flags
import com.bamnat.app.ui.pro.PREVIEW_PLANS
import com.bamnat.app.ui.pro.shownPlans
import com.bamnat.app.ui.pro.tierPreviewShown
import com.bamnat.core.flags.PhoneFacts
import com.bamnat.core.tier.ProReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0.0 [RVO-01] 검토 PC-54: 로컬 확인 빌드(정식 출시 전)에서도 '프로가 아닐 때'를 볼 수 있어야 PC 확인이 잠긴 기능·하루 써 보기·요금제 창을
 * 봐요. 미리 보기 '무료'는 정식 출시 뒤의 무료 사용자와 같아야 하고(서버가 모두에게 연 기능은 열림), 출시 빌드의 보통 휴대폰에는 보이지 않아요.
 */
@RunWith(AndroidJUnit4::class)
class TierPreviewTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val now = Instant.parse("2026-11-12T03:00:00Z")

    @Before fun clean() {
        context.appContainer
        context.getSharedPreferences("launch_config", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun stop() {
        scope.cancel()
        SettingsRepository(context).update { it.copy(previewTier = null) }
    }

    /** Before the 정식 출시 by the server's clock: every feature open, as on a local debug build. */
    private val beforeLaunch = """{"v":1,"billingLive":false,"launchedAt":null,
        "flags":{"free.MONEY_STATS":{"force":"on"}},
        "policy":[{"version":"3.1","posted":"2026-10-27","effective":"2026-11-10","heldUntil":"2026-11-24"}],
        "serverTime":"2026-11-09T03:00:00Z"}"""

    private fun gate(preview: Tier?, debug: Boolean): Gate {
        val remote = RemoteConfigStore(context).apply { save(LaunchConfig.parse(JSONObject(beforeLaunch), now)) }
        val facts = PhoneFacts(now = now, serverNow = Instant.parse("2026-11-09T03:00:00Z"), versionCode = 54, installed = LocalDate.of(2026, 10, 1),
            agreed = setOf("3.1"))
        val flags = Flags(remote.config, emptyList(), scope, facts = { facts }, now = { now })
        val settings = SettingsRepository(context).apply { update { it.copy(previewTier = preview) } }
        return FeatureGate(settings, remote, ProStore(context), scope, flags, installedAt = { now.minusSeconds(86_400 * 30) },
            debug = debug, clock = { now }, zone = { ZoneId.of("Asia/Seoul") }).state.value
    }

    @Test fun theFreePreviewIsTheLaunchedAppWithoutPro() {
        val open = gate(null, debug = true)
        assertFalse("a local build before the launch: every feature open", open.live)
        assertTrue(open.allows(Feature.FX_WORKS))

        val free = gate(Tier.FREE, debug = true)
        assertTrue(free.live)
        assertTrue(free.preview)
        assertEquals(ProReason.PREVIEW, free.access.reason)
        assertFalse("기능 작품 rests, so 하루 써 보기 shows", free.allows(Feature.FX_WORKS))
        assertFalse(free.allows(Feature.PAYSLIP_MATCH))
        assertTrue("a 프로 feature the server opened to everyone stays open in the preview", free.allows(Feature.MONEY_STATS))
        assertTrue("its first week can be tried, as after the launch", free.canTry(Feature.PAYSLIP_MATCH) || free.bundle(Feature.PAYSLIP_MATCH) == null)

        val pro = gate(Tier.PRO, debug = true)
        assertTrue(pro.allows(Feature.FX_WORKS))
        assertFalse("a preview is never 프로 bought", pro.boughtPro)
    }

    @Test fun aReleaseBuildIgnoresAStoredPreviewOnAUsersPhone() {
        val g = gate(Tier.FREE, debug = false)
        assertFalse(g.preview)
        assertTrue(g.allows(Feature.FX_WORKS))
    }

    @Test fun thePlansShowInAPreviewEvenWithoutPlayAndNeverOtherwise() {
        val free = gate(Tier.FREE, debug = true)
        assertEquals(PREVIEW_PLANS, shownPlans(free, emptyList()))
        assertTrue("the example prices say so", PREVIEW_PLANS.all { "예시" in it.price })
        assertEquals(emptyList<com.bamnat.app.billing.Plan>(), shownPlans(gate(null, debug = true), emptyList()))
        val play = listOf(com.bamnat.app.billing.Plan("monthly", "4,990원", 4_990_000_000, "KRW", "P1M", trial = false, offerToken = "m"))
        assertEquals("Play's own plans first, when it gave them", play, shownPlans(free, play))
    }

    @Test fun thePreviewCardIsOnlyForDebugBuilds() {
        assertTrue(tierPreviewShown(debug = true))
        assertFalse("a release build, whoever's phone and whatever the server says", tierPreviewShown(debug = false))
    }
}
