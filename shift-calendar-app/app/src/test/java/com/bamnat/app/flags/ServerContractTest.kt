package com.bamnat.app.flags

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.StartupTrace
import com.bamnat.app.appContainer
import com.bamnat.app.diag.StartupStats
import com.bamnat.app.diag.UsageStats
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.LaunchConfig
import com.bamnat.app.tier.RemoteConfigStore
import com.bamnat.app.tier.TierClient
import com.bamnat.core.flags.Check
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagEval
import com.bamnat.core.flags.FlagSource
import com.bamnat.core.flags.FlagState
import com.bamnat.core.flags.PhoneFacts
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [FLAG-35] 1.28 통합: the company server's real answers (written by `server/company-worker/test/contract.test.mjs` into
 * `src/test/resources/server-fixtures/`, `UPDATE_FIXTURES=1 npm test`) read with the app's own [LaunchConfig.parse],
 * [FlagJson], [TierClient.readPasses] and [FlagEval], so a change on either side that the other does not follow fails
 * here or there. The 사용 통계 body this app builds is written for the server test the other way
 * (`BAMNAT_WRITE_FIXTURES=1`).
 */
@RunWith(AndroidJUnit4::class)
class ServerContractTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    @Before fun clean() {
        // [QA-09] The start's own clean-up first: without a yes it forgets the waiting book (UsageStats.forgetUnless),
        // and landing after the book below was written it left preview() empty (NPE, CI on 1.32).
        context.appContainer
        context.getSharedPreferences("launch_config", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun stop() {
        scope.cancel()
        Today.reset()
    }

    private fun fixture(name: String): JSONObject =
        JSONObject(requireNotNull(javaClass.getResource("/server-fixtures/$name")) { "server-fixtures/$name: UPDATE_FIXTURES=1 npm test in server/company-worker" }.readText())

    /** The answer as the phone keeps it: parsed at the server's time (the phone and the server agree on the clock). */
    private fun config(name: String): LaunchConfig {
        val o = fixture(name).getJSONObject("config")
        return LaunchConfig.parse(o, Instant.parse(o.getString("serverTime")))
    }

    private val installedBefore31 = LocalDate.of(2026, 10, 1)

    private fun phone(c: LaunchConfig, agreed: Set<String> = setOf("3.9"), consents: Map<String, Boolean> = emptyMap(), versionCode: Int = 39,
                      admin: Boolean = false, owner: Boolean = false, previews: Set<String> = emptySet(), later: Long = 0) =
        PhoneFacts(now = c.serverTime!!.plusSeconds(later), serverNow = c.serverTime!!.plusSeconds(later), versionCode = versionCode,
            installed = installedBefore31, agreed = agreed, consents = consents, workType = "SHIFT", admin = admin, owner = owner, previews = previews)

    private fun states(c: LaunchConfig, p: PhoneFacts): Map<String, FlagState> = FlagEval.all(p, c.overrides(), c.calendar).associateBy { it.key }

    private val days = 24L * 3600

    @Test fun aFreshServerOverridesNothingAndBillingWaits() {
        val c = config("fresh.json")
        assertNotNull("a 1.28 server sends flags", c.flags)
        assertEquals(setOf(FlagCatalog.BILLING, FlagCatalog.ADS), c.flags!!.keys)
        val s = states(c, phone(c))
        assertFalse(s.getValue(FlagCatalog.BILLING).on)
        assertEquals(FlagSource.CONDITIONS, s.getValue(FlagCatalog.BILLING).source)
        assertFalse(s.getValue(FlagCatalog.ADS).on)
        assertFalse(s.getValue(FlagCatalog.LEAVE_HOURLY).on)
        assertFalse(s.getValue(FlagCatalog.STATS_MORE).on)
        WorkStyle.entries.forEach { assertTrue(it.name, s.getValue(FlagCatalog.typeKey(it)).on) }
        ServerSwitch.entries.forEach { assertTrue(it.name, s.getValue(FlagCatalog.switchKey(it)).on) }
        // Old-app fields as a 1.27 phone reads them: nothing on, nothing off.
        assertEquals(false to emptySet<Feature>(), c.billingLive to c.freeFeatures)
        assertTrue(c.off.isEmpty())
        // The calendar the phone reads is the one it ships with for 3.1 and 3.2 (the book is one, FLAG-34).
        assertEquals(com.bamnat.core.flags.PolicyCalendar.SEED.of("3.1"), c.calendar.of("3.1"))
        assertEquals(com.bamnat.core.flags.PolicyCalendar.SEED.of("3.2"), c.calendar.of("3.2"))
        assertNull(TierClient.readPasses(fixture("fresh.json").getJSONObject("passes")).admin)
    }

    @Test fun movedSettingsOf127AreReadAsTheServersFlagsNotTheOldFields() {
        val c = config("migrated-1.27.json")
        // A 1.27 phone would read these; a 1.28 phone reads flags, which the server moved them into.
        assertTrue(c.billingLive)
        assertEquals(setOf(Feature.MONEY_STATS), c.freeFeatures)
        assertEquals(setOf(ServerSwitch.PLAZA_WRITE), c.off)
        // S 1.32 [SECX-04] The admin's 이유 stays in the developer app; the force itself comes through flags.
        assertTrue("from flags, not from the old switch", c.overrides().getValue(FlagCatalog.BILLING).why.isNullOrEmpty())
        val s = states(c, phone(c))
        assertEquals(FlagSource.FORCE_ON, s.getValue(FlagCatalog.BILLING).source)
        assertTrue(s.getValue(FlagCatalog.BILLING).on)
        assertTrue(s.getValue(FlagCatalog.ADS).on)
        assertTrue(s.getValue("free.MONEY_STATS").on)
        assertEquals(FlagSource.FORCE_OFF, s.getValue("plaza.write").source)
        assertFalse(s.getValue("plaza.write").on)
    }

    @Test fun billingStaysOffUntilBothPlayConfirmationsAndOnAfter() {
        val one = config("billing-one-confirmation.json")
        assertFalse(one.billingLive)
        val waiting = states(one, phone(one)).getValue(FlagCatalog.BILLING)
        assertFalse(waiting.on)
        val left = waiting.checks.filter { it.status != Check.Status.MET }.map { it.label }
        assertEquals(listOf("${FlagEval.MANUAL.getValue("play.subscription")}(관리자가 확인)"), left)
        assertFalse(states(one, phone(one)).getValue(FlagCatalog.ADS).on)

        val both = config("billing-confirmed.json")
        assertTrue("old apps hear it too", both.billingLive)
        assertNotNull(both.launchedAt)
        val on = states(both, phone(both))
        assertTrue(on.getValue(FlagCatalog.BILLING).on)
        assertEquals(FlagSource.CONDITIONS, on.getValue(FlagCatalog.BILLING).source)
        assertTrue("ads: Google's consent not asked yet counts (AL-D5)", on.getValue(FlagCatalog.ADS).on)
        assertFalse("a no to Google's consent", states(both, phone(both, consents = mapOf("ads" to false))).getValue(FlagCatalog.ADS).on)
        // A phone installed before 3.1 that did not say yes is held until 11-24 (by the server's clock), then on by itself.
        assertFalse(states(both, phone(both, agreed = emptySet())).getValue(FlagCatalog.BILLING).on)
        assertTrue(states(both, phone(both, agreed = emptySet(), later = 12 * days)).getValue(FlagCatalog.BILLING).on)
    }

    @Test fun aForceOffWinsWithItsWhyUntilItsEndAndTheAppRangeAndTheOwnersTypes() {
        val c = config("force-off.json")
        assertFalse(c.billingLive)
        val billing = states(c, phone(c, admin = true, previews = setOf(FlagCatalog.BILLING))).getValue(FlagCatalog.BILLING)
        assertEquals(FlagSource.FORCE_OFF, billing.source)
        assertFalse(billing.on)
        // S 1.32 [SECX-04] The admin's 이유 is not sent to phones; the end date still is.
        assertTrue(billing.reason, billing.reason.startsWith("관리자가 잠시 껐어요") && billing.reason.contains("2026년 11월 20일까지"))
        assertTrue("the force ended: conditions again", states(c, phone(c, later = 9 * days)).getValue(FlagCatalog.BILLING).on)
        assertFalse(states(c, phone(c)).getValue("plaza.write").on)
        assertEquals(setOf(ServerSwitch.PLAZA_WRITE), c.off)
        // AL-D3 minApp from the server: off below it, on from it.
        assertEquals(40, c.overrides().getValue("share.create").minApp)
        assertEquals(FlagSource.APP_RANGE, states(c, phone(c, versionCode = 39)).getValue("share.create").source)
        assertTrue(states(c, phone(c, versionCode = 40)).getValue("share.create").on)
        // S 1.32 [SECX-01] Only an approved admin phone given 투자's 미리 보기 in the developer app sees it, marked; being the
        // owner adds nothing, and the server's previews list reads through.
        val ownerPass = TierClient.readPasses(fixture("force-off.json").getJSONObject("ownerPasses")).admin!!
        val memberPass = TierClient.readPasses(fixture("force-off.json").getJSONObject("memberPasses")).admin!!
        assertTrue(ownerPass.approved && ownerPass.owner)
        assertFalse(memberPass.owner)
        val ownerPhone = phone(c, admin = ownerPass.approved, owner = ownerPass.owner)
        assertEquals(emptyList<String>(), ownerPass.previews)
        assertTrue(Flags.types(states(c, ownerPhone), ownerPass.previews.toSet()).none { it.style == WorkStyle.INVEST })
        val previewing = phone(c, admin = true, owner = true, previews = setOf("type.invest"))
        assertTrue(Flags.types(states(c, previewing), previewing.previews).single { it.style == WorkStyle.INVEST }.hidden)
        val memberPhone = phone(c, admin = memberPass.approved, owner = memberPass.owner)
        assertTrue(Flags.types(states(c, memberPhone), emptySet()).none { it.style == WorkStyle.INVEST })
    }

    @Test fun statsMoreStaysOffWithoutTheExtraYesAndItsListGoesToTheServer() {
        val c = config("after-3.2.json")
        assertFalse(states(c, phone(c, consents = mapOf("usage" to true))).getValue(FlagCatalog.STATS_MORE).on)
        assertFalse(states(c, phone(c, consents = mapOf("usage" to false, "usage.stats.more" to true))).getValue(FlagCatalog.STATS_MORE).on)
        val yes = mapOf("usage" to true, "usage.stats.more" to true)
        assertTrue(states(c, phone(c, consents = yes)).getValue(FlagCatalog.STATS_MORE).on)

        // The body 사용 통계 sends from this phone, with flagsOn from the real Flags: what the server test posts.
        val remote = RemoteConfigStore(context).apply { save(c) }
        val flags = Flags(remote.config, emptyList(), scope, facts = { phone(c, consents = yes) }, now = { c.serverTime!! })
        val keys = flags.statsOn()!!
        assertTrue(FlagCatalog.STATS_MORE in keys && FlagCatalog.BILLING in keys)
        val zone = ZoneId.of("Asia/Seoul")
        Today.fix(c.serverTime!!.atZone(zone))
        val today = Today.date(zone).toEpochDay()
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).edit()
            .putString("book", UsageBook().record(UsageEvent.OPEN, today - 1).record(UsageEvent.OPEN, today - 1).encode()).commit()
        // 1.28 통합 [PERF-16]: startup times ride on the same row (AppContainer sets StartupStats.policyAllows to it).
        context.getSharedPreferences("startup_stats", Context.MODE_PRIVATE).edit().clear().commit()
        val startup = StartupStats(context, allowed = { flags.on(FlagCatalog.STATS_MORE) })
        startup.record(StartupTrace.Sample(StartupTrace.Kind.COLD, 40, 300, 1_200))
        startup.record(StartupTrace.Sample(StartupTrace.Kind.WARM, 0, 80, 350))
        val body = JSONObject(UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, _ -> }, startup = startup).preview(keys)!!)
        assertEquals(keys, body.getJSONArray("flagsOn").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertTrue("startup goes with flagsOn in one body", body.has("startup"))
        val out = JSONObject().put("at", c.serverTime.toString()).put("body", body)
        val file = fixtureFile("events-from-app.json")
        if (System.getenv("BAMNAT_WRITE_FIXTURES") == "1") file.writeText(out.toString(2) + "\n")
        assertTrue("$file is missing: BAMNAT_WRITE_FIXTURES=1 and this test, then npm test", file.exists())
        // The version name moves with each release; the rest must be what the server test posts.
        val kept = JSONObject(file.readText())
        kept.getJSONObject("body").remove("app")
        body.remove("app")
        assertEquals("events-from-app.json is old: BAMNAT_WRITE_FIXTURES=1 and this test, then npm test in server/company-worker",
            JSONObject().put("at", c.serverTime.toString()).put("body", body).toString(), kept.toString())
    }

    private fun fixtureFile(name: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val f = File(dir, "src/test/resources/server-fixtures")
            if (f.isDirectory) return File(f, name)
            val g = File(dir, "app/src/test/resources/server-fixtures")
            if (g.isDirectory) return File(g, name)
            dir = dir.parentFile
        }
        error("server-fixtures not found")
    }
}
