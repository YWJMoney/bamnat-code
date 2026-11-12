package com.bamnat.app.tier

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.ui.pro.closedBillingWords
import com.bamnat.app.ui.pro.inviteFirst
import com.bamnat.core.flags.PhoneFacts
import com.bamnat.core.flags.Released
import com.bamnat.core.tier.ProReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0.0 [RVO-12] 검토 HQ-46 (사용자 2026-11-12 20:27·20:32 KST): a release build that has not heard from the server yet is the launched
 * app (무료·프로가 나뉨, 감사 프로는 출시 날부터), while a debug build, the local test server and the tests keep every feature open;
 * 처리방침 3.1's hold stays and says its last free day. [RVO-13] HQ-48: who gets 친구 초대 right under '지금'.
 */
@RunWith(AndroidJUnit4::class)
class ReleaseBuildTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val zone = ZoneId.of("Asia/Seoul")
    private val now = Instant.parse("2026-11-13T03:00:00Z")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val was = ReleaseBuild.on

    @After fun stop() {
        ReleaseBuild.on = was
        scope.cancel()
    }

    /** A phone that never reached the server (첫 실행이 인터넷 없이, 또는 서버가 막힘): the app's own conditions, by the phone's date (AL-D6). */
    private fun gate(release: Boolean, installed: LocalDate, agreed: Set<String> = emptySet()): Gate {
        ReleaseBuild.on = release
        val remote = RemoteConfigStore(context).apply { save(LaunchConfig()) }
        val facts = PhoneFacts(now = now, serverNow = now, versionCode = 54, installed = installed, agreed = agreed,
            knownDay = Released.DAY.takeIf { release }, knownChecks = if (release) Released.CHECKS else emptyMap())
        val flags = com.bamnat.app.flags.Flags(remote.config, emptyList(), scope, now = { now }, facts = { facts })
        val at = installed.atStartOfDay(zone).toInstant()
        return FeatureGate(SettingsRepository(context), remote, ProStore(context), scope, flags, installedAt = { at }, clock = { now }, zone = { zone }).state.value
    }

    @Test fun aDebugBuildAndTheTestsKeepEveryFeatureOpen() {
        val g = gate(release = false, installed = LocalDate.of(2026, 11, 13))
        assertFalse(g.live)
        assertEquals(ProReason.ALL_OPEN, g.access.reason)
        Feature.entries.forEach { assertTrue(it.name, g.allows(it)) }
        assertFalse("결제가 열리지 않으면 초대 카드는 아래 그대로", inviteFirst(g))
    }

    @Test fun aReleaseBuildBeforeTheServerAnswersIsTheLaunchedApp() {
        val g = gate(release = true, installed = LocalDate.of(2026, 11, 13))
        assertTrue(g.live)
        assertEquals(Tier.FREE, g.tier)
        assertEquals(ProReason.NONE, g.access.reason)
        assertTrue(g.locked(Feature.NO_ADS))
        assertTrue("프로 없는 폰은 '지금' 바로 아래가 친구 초대", inviteFirst(g))
    }

    @Test fun aPhoneFromBeforeTheLaunchHasItsThanksMonthFromTheLaunch() {
        val g = gate(release = true, installed = LocalDate.of(2026, 11, 2))
        assertTrue(g.live)
        assertEquals(ProReason.THANKS, g.access.reason)
        assertEquals(LocalDate.of(2026, 12, 12), g.access.until!!.atZone(zone).toLocalDate())
        assertTrue("감사 기간도 산 프로가 아니라 초대가 먼저", inviteFirst(g))
        assertEquals(Released.AT, LaunchConfig().launchAt(released = true))
        assertNull("debug: the server's value only", LaunchConfig().launchAt(released = false))
        assertEquals(1, LaunchConfig().thanks(released = true))
        assertEquals("the server's answer wins, 0 included", 0, LaunchConfig(fetchedAt = now, thanksMonths = 0).thanks(released = true))
    }

    @Test fun theThreePointOneHoldStaysAndSaysItsLastFreeDay() {
        // 9항: installed before 10-27, no yes: every feature free until 11-24.
        val held = gate(release = true, installed = LocalDate.of(2026, 10, 20))
        assertFalse(held.live)
        assertEquals(ProReason.HELD, held.access.reason)
        assertEquals("처리방침 3.1 동의 전이라 11월 23일까지 모든 기능이 무료예요", held.access.say(zone))
        Feature.entries.forEach { assertTrue(it.name, held.allows(it)) }
        val thanks = com.bamnat.core.tier.ProAccess.thanksUntil(LocalDate.of(2026, 10, 20).atStartOfDay(zone).toInstant(), Released.AT, 1, zone)
        val words = closedBillingWords(held.access, thanks, zone)
        assertTrue(words, words.startsWith("처리방침 3.1 동의 전이라 11월 23일까지 모든 기능이 무료예요. 11월 24일부터 무료·프로가 나뉘어요."))
        assertTrue(words, words.contains("2026년 12월 12일까지는 감사 프로로"))
        assertFalse(words.contains("정식 출시 전이라"))
        // The yes ends the hold at once.
        assertTrue(gate(release = true, installed = LocalDate.of(2026, 10, 20), agreed = setOf("3.1")).live)
        assertEquals("지금은 모든 기능이 무료로 열려 있어요. 결제가 열리면 앱에서 알려 드려요.",
            closedBillingWords(gate(release = false, installed = LocalDate.of(2026, 11, 13)).access, null, zone))
    }

    @Test fun theAppsOwnFactsAreReadOnlyInARelease() {
        ReleaseBuild.on = false
        val debug = context.appContainer.phoneFacts()
        assertNull(debug.knownDay)
        assertTrue(debug.knownChecks.isEmpty())
        ReleaseBuild.on = true
        val release = context.appContainer.phoneFacts()
        assertEquals(LocalDate.of(2026, 11, 12), release.knownDay)
        assertEquals(setOf("play.production", "play.subscription"), release.knownChecks.keys)
    }
}
