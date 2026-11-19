package com.bamnat.app.tier

import android.app.job.JobScheduler
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.billing.Billing
import com.bamnat.app.billing.NoBilling
import com.bamnat.app.billing.ProductIds
import com.bamnat.app.data.SettingsRepository
import com.bamnat.core.tier.PlaySub
import com.bamnat.core.tier.ProReason
import com.bamnat.core.time.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.Instant
import java.time.ZoneId

/**
 * [R200-03] (검토 PRO-08 넘긴 일) A subscription near its end is read again in the background once a day, and the gate closes
 * at the end's minute while the app is open, not only on the next return to it.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ProRecheckTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @After fun stop() {
        scope.cancel()
        (context as BamnatApp).container.proStore.setSub(null)
    }

    private fun sub(expiry: Instant, autoRenew: Boolean, checkedAt: Instant) = SubRecord(
        product = ProductIds.PRO, basePlan = ProductIds.YEARLY, state = PlaySub.State.ACTIVE, expiry = expiry,
        trial = false, autoRenew = autoRenew, purchasedAt = expiry.minus(Duration.ofDays(365)), verified = true, checkedAt = checkedAt,
    )

    @Test fun theGateClosesAtTheEndWhileTheAppIsOpen() {
        var now = Instant.parse("2027-03-10T01:00:00Z")
        val settings = SettingsRepository(context)
        settings.update { it.copy(previewTier = null) }
        val remote = RemoteConfigStore(context).apply { save(LaunchConfig(billingLive = true)) }
        val pro = ProStore(context).apply { setSub(sub(now.plusSeconds(1), autoRenew = false, checkedAt = now)) }
        val flags = com.bamnat.app.flags.Flags(remote.config, emptyList(), scope, now = { now },
            facts = { com.bamnat.core.flags.PhoneFacts(now = now, serverNow = now, versionCode = 54, agreed = setOf("3.1")) })
        val gate = FeatureGate(settings, remote, pro, scope, flags, installedAt = { now.minusSeconds(86_400 * 30) },
            clock = { now }, zone = { ZoneId.of("Asia/Seoul") })
        assertEquals(ProReason.SUBSCRIPTION, gate.state.value.access.reason)
        now = now.plusSeconds(5)
        val until = System.currentTimeMillis() + 10_000
        while (gate.state.value.access.reason == ProReason.SUBSCRIPTION && System.currentTimeMillis() < until) Thread.sleep(50)
        assertNotEquals("the end is seen without coming back to the app", ProReason.SUBSCRIPTION, gate.state.value.access.reason)
        pro.setSub(null)
    }

    @Test fun aSubscriptionNearItsEndIsReadAgainWithoutTheApp() = runBlocking {
        val c = (context as BamnatApp).container
        var asked = 0
        c.billing = object : Billing by NoBilling() {
            override suspend fun refresh() { asked++ }
        }
        val now = Today.instant()
        val jobs = context.getSystemService(JobScheduler::class.java)

        // A month to go: watched, not yet read again.
        c.proStore.setSub(sub(now.plus(Duration.ofDays(30)), autoRenew = true, checkedAt = now.minus(Duration.ofDays(2))))
        ProRecheckJob.schedule(context, c)
        assertNotNull("set while there is a subscription to watch", jobs.allPendingJobs.firstOrNull { it.service.className.endsWith("ProRecheckJob") })
        assertFalse(ProRecheckJob.check(c))
        assertEquals(0, asked)

        // Renewing in 3시간: Play is asked.
        c.proStore.setSub(sub(now.plus(Duration.ofHours(3)), autoRenew = true, checkedAt = now.minus(Duration.ofDays(2))))
        assertTrue(ProRecheckJob.check(c))
        assertEquals(1, asked)

        // No subscription: the job goes.
        c.proStore.setSub(null)
        assertFalse(ProRecheckJob.check(c))
        assertNull(jobs.allPendingJobs.firstOrNull { it.service.className.endsWith("ProRecheckJob") })
    }
}
