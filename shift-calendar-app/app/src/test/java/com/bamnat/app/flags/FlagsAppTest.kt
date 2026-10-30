package com.bamnat.app.flags

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.SettingsRepository
import com.bamnat.app.diag.UsageStats
import com.bamnat.app.tier.AdminState
import com.bamnat.app.tier.Feature
import com.bamnat.app.tier.FeatureGate
import com.bamnat.app.tier.LaunchConfig
import com.bamnat.app.tier.Passes
import com.bamnat.app.tier.ProStore
import com.bamnat.app.tier.RemoteConfigStore
import com.bamnat.app.tier.TierClient
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagSource
import com.bamnat.core.flags.Force
import com.bamnat.core.flags.PhoneFacts
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.stats.UsageBook
import com.bamnat.core.stats.UsageEvent
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.json.JSONException
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [FLAG-06..16][FLAG-19] 1.28 AL on the phone: the server's answer (1.27 and 1.28 shapes) read into overrides, the
 * server's clock carried forward, the gate and the ads on the rows, the kinds of work for the owner's phone, and
 * `flagsOn` in 사용 통계 only with `stats.more`.
 */
@RunWith(AndroidJUnit4::class)
class FlagsAppTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val now = Instant.parse("2026-11-12T03:00:00Z")

    @Before fun clean() {
        context.appContainer // [QA-09] the start's own clean-up first (it forgets a usage book written before it)
        context.getSharedPreferences("launch_config", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("flag_preview", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After fun stop() {
        scope.cancel()
        context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE).edit().clear().commit()
    }

    private fun phone(agreed: Set<String> = setOf("3.9"), server: Instant? = now, admin: Boolean = false, owner: Boolean = false,
                      previews: Set<String> = emptySet(), consents: Map<String, Boolean> = emptyMap()) =
        PhoneFacts(now = now, serverNow = server, versionCode = 39, installed = LocalDate.of(2026, 10, 1), agreed = agreed,
            admin = admin, owner = owner, previews = previews, consents = consents)

    private fun flags(config: LaunchConfig, facts: () -> PhoneFacts = { phone() }): Flags {
        val remote = RemoteConfigStore(context).apply { save(config) }
        return Flags(remote.config, emptyList(), scope, facts = facts, now = { now })
    }

    private val newAnswer = """{"v":1,"billingLive":false,"launchedAt":null,
        "flags":{
          "billing":{"force":null,"why":null,"conditions":null,"until":null,"confirmed":{"play.production":"2026-11-09T00:00:00Z","play.subscription":null}},
          "plaza.write":{"force":"off","why":"스팸 글이 몰려 잠시 막음","until":"2026-11-13T00:00:00Z"},
          "type.invest":{"force":"off","why":"캡처 인식 검증 중"},
          "free.MONEY_STATS":{"force":"on"},
          "some.newer.flag":{"force":"on"},
          "ads":{"conditions":[{"type":"teleport"}],"confirmed":["play.production","play.subscription"]}
        },
        "policy":[{"version":"3.1","posted":"2026-10-27","effective":"2026-11-10","heldUntil":"2026-11-24"},{"version":"9.9","posted":"2027-01-01","effective":"2027-02-01","heldUntil":null}],
        "serverTime":"2026-11-12T03:00:00Z"}"""

    // ---- reading the server ----

    @Test fun aNewServersAnswerIsReadAndKept() {
        val c = LaunchConfig.parse(JSONObject(newAnswer), now)
        val o = c.overrides()
        assertEquals(setOf("billing", "plaza.write", "type.invest", "free.MONEY_STATS", "ads"), o.keys)
        assertEquals(Force.OFF, o.getValue("plaza.write").force)
        assertEquals(setOf("play.production", "play.subscription"), o.getValue("billing").confirmed.keys)
        assertNull("an unknown kind of condition drops the server's list (AL-D1)", o.getValue("ads").conditions)
        assertEquals(Instant.parse("2026-11-12T03:00:00Z"), c.serverTime)
        assertEquals("9.9", c.calendar.versions.last().version)
        assertEquals(c, LaunchConfig.fromSaved(c.toJson().toString()))
        // The billing row turns on by its own conditions, though the server's billingLive (for 1.27 apps) still says false.
        val f = flags(c)
        assertTrue(f.on(FlagCatalog.BILLING))
        assertEquals(FlagSource.CONDITIONS, f.state(FlagCatalog.BILLING)!!.source)
        assertTrue(f.on(FlagCatalog.ADS))
        assertTrue(f.paused(ServerSwitch.PLAZA_WRITE))
        assertFalse(f.paused(ServerSwitch.SHARE_CREATE))
        assertTrue(f.on("free.MONEY_STATS"))
        assertFalse(f.on("some.newer.flag"))
    }

    @Test fun aWrongShapeFailsTheAnswerSoTheLastGoodOneStays() {
        val bad = listOf(
            """{"flags":{"billing":"on"}}""",
            """{"flags":{"billing":{"force":"maybe"}}}""",
            """{"flags":{"billing":{"until":"tomorrow"}}}""",
            """{"flags":{},"policy":[{"version":"3.1","posted":"2026-11-10","effective":"2026-10-27"}]}""",
            """{"flags":{},"serverTime":"noon"}""",
            """{"launchedAt":null}""",
        )
        for (b in bad) {
            try {
                LaunchConfig.parse(JSONObject(b), now)
                fail(b)
            } catch (_: Exception) {
            }
        }
        // flags without billingLive is a 1.28 server's answer, and fine.
        assertTrue(LaunchConfig.parse(JSONObject("""{"flags":{}}"""), now).flags!!.isEmpty())
    }

    @Test fun anOldServersAnswerBehavesExactlyAs127() {
        val on = LaunchConfig.parse(JSONObject("""{"billingLive":true,"freeFeatures":["MONEY_STATS"],"off":["PLAZA_WRITE"]}"""), now)
        assertNull(on.flags)
        // 1.27 had no 11-10 check on the switch: before 3.1's date it was on as soon as the switch was.
        val early = flags(on) { phone(server = Instant.parse("2026-10-14T01:00:00Z")) }
        assertTrue(early.on(FlagCatalog.BILLING))
        assertTrue(early.on(FlagCatalog.ADS))
        assertTrue(early.on("free.MONEY_STATS"))
        assertTrue(early.paused(ServerSwitch.PLAZA_WRITE))
        // A tester without the 3.1 yes waits until 11-24, as 1.27's mask did.
        val tester = flags(on) { phone(agreed = emptySet()).copy(installed = LocalDate.of(2026, 10, 21)) }
        assertFalse(tester.on(FlagCatalog.BILLING))
        val off = flags(LaunchConfig.parse(JSONObject("""{"billingLive":false}"""), now))
        assertFalse(off.on(FlagCatalog.BILLING))
        assertFalse(off.on(FlagCatalog.ADS))
        assertFalse(off.paused(ServerSwitch.PLAZA_WRITE))
    }

    @Test fun theServersClockIsCarriedForwardOnTheSameBootOnly() {
        var elapsed = 1_000L
        var boot: Int? = 7
        val store = RemoteConfigStore(context, elapsed = { elapsed }, boot = { boot })
        assertNull(store.serverNow())
        val t = Instant.parse("2026-11-09T14:00:00Z")
        store.save(LaunchConfig(flags = emptyMap(), serverTime = t, fetchedAt = now))
        elapsed += 3_600_000L
        assertEquals(t.plusSeconds(3_600), store.serverNow())
        assertEquals("kept across a restart of the app", t.plusSeconds(3_600), RemoteConfigStore(context, elapsed = { elapsed }, boot = { boot }).serverNow())
        boot = 8
        assertEquals("after a reboot: the server's time as it was", t, store.serverNow())
    }

    // ---- the gate, the ads ----

    @Test fun theGateIsTheBillingRowAndFreeRowsOpenOnlyProFeatures() {
        val c = LaunchConfig.parse(JSONObject(newAnswer), now)
        val remote = RemoteConfigStore(context).apply { save(c) }
        val f = Flags(remote.config, emptyList(), scope, facts = { phone() }, now = { now })
        val gate = FeatureGate(SettingsRepository(context), remote, ProStore(context), scope, f, installedAt = { now.minusSeconds(86_400) },
            debug = false, clock = { now }, zone = { ZoneId.of("Asia/Seoul") }).state.value
        assertTrue(gate.live)
        assertTrue(gate.allows(Feature.MONEY_STATS))
        assertTrue(gate.locked(Feature.PAYSLIP_MATCH))
        // Before 11-10 by the server's clock the same answer leaves everything open.
        val f2 = Flags(remote.config, emptyList(), scope, facts = { phone(server = Instant.parse("2026-11-09T14:59:00Z")) }, now = { now })
        val before = FeatureGate(SettingsRepository(context), remote, ProStore(context), scope, f2, installedAt = { null }, debug = false,
            clock = { now }).state.value
        assertFalse(before.live)
        Feature.entries.forEach { assertTrue(it.name, before.allows(it)) }
    }

    @Test fun aRefusedAdConsentTurnsTheAdsRowOff() {
        val c = LaunchConfig.parse(JSONObject(newAnswer), now)
        assertTrue(flags(c).on(FlagCatalog.ADS))
        assertFalse(flags(c) { phone(consents = mapOf("ads" to false)) }.on(FlagCatalog.ADS))
        assertTrue(flags(c) { phone(consents = mapOf("ads" to true)) }.on(FlagCatalog.BILLING))
    }

    // ---- kinds of work ----

    // S 1.32 [SECX-01] Being the owner shows nothing hidden; only the developer app's 미리 보기 for that phone does.
    @Test fun aHiddenKindLeavesThePickingListsButAPreviewPhoneSeesItMarked() {
        val c = LaunchConfig.parse(JSONObject(newAnswer), now)
        val user = flags(c)
        assertEquals(WorkStyle.entries - WorkStyle.INVEST, user.types().map { it.style })
        assertTrue("the kind in use stays", user.types(keep = WorkStyle.INVEST).any { it.style == WorkStyle.INVEST && !it.hidden })
        val admin = flags(c) { phone(admin = true, owner = false) }
        assertEquals("an admin who is not the owner: hidden too", WorkStyle.entries - WorkStyle.INVEST, admin.types().map { it.style })
        val owner = flags(c) { phone(admin = true, owner = true) }
        assertEquals("the owner alone: hidden too", WorkStyle.entries - WorkStyle.INVEST, owner.types().map { it.style })
        val preview = flags(c) { phone(admin = true, owner = false, previews = setOf("type.invest")) }
        assertEquals(WorkStyle.entries, preview.types().map { it.style })
        assertEquals(listOf(WorkStyle.INVEST), preview.types().filter { it.hidden }.map { it.style })
        assertFalse("not an approved admin: no preview", flags(c) { phone(admin = false, previews = setOf("type.invest")) }.types().any { it.style == WorkStyle.INVEST })
    }

    @Test fun theOwnerMarkComesFromPassesAndIsKept() {
        val o = JSONObject("""{"until":null,"waitingMonths":0,"items":[],"deferred":0,"code":false,"checkedAt":"2026-11-12T00:00:00Z",
            "admin":{"status":"approved","name":"dd","owner":true}}""")
        val p = TierClient.readPasses(o)
        assertTrue(p.admin!!.owner && p.admin!!.approved)
        assertFalse(TierClient.readPasses(JSONObject(o.toString().replace("\"owner\":true", "\"owner\":false"))).admin!!.owner)
        val store = ProStore(context)
        store.setPasses(Passes(null, 0, emptyList(), 0, AdminState("approved", "dd", owner = true), false, now))
        assertTrue(ProStore(context).current.passes!!.admin!!.owner)
    }

    // ---- admin preview, stats ----

    @Test fun anAdminPreviewTurnsARowOnOnlyOnThatPhone() {
        // S 1.32 [SECX-01] The list comes from /api/passes (the developer app); nothing on the phone sets it.
        val o = JSONObject("""{"until":null,"waitingMonths":0,"items":[],"deferred":0,"code":false,"checkedAt":"2026-11-12T00:00:00Z",
            "admin":{"status":"approved","name":"dd","owner":false,"previews":["leave.hourly",""]}}""")
        val previews = TierClient.readPasses(o).admin!!.previews.toSet()
        assertEquals(setOf(FlagCatalog.LEAVE_HOURLY), previews)
        val c = LaunchConfig(flags = emptyMap())
        val admin = flags(c) { phone(admin = true, previews = previews) }
        assertTrue(admin.on(FlagCatalog.LEAVE_HOURLY))
        assertEquals(FlagSource.ADMIN_PREVIEW, admin.state(FlagCatalog.LEAVE_HOURLY)!!.source)
        assertFalse(flags(c) { phone(admin = false, previews = previews) }.on(FlagCatalog.LEAVE_HOURLY))
        val store = ProStore(context)
        store.setPasses(TierClient.readPasses(o))
        assertEquals("kept with the passes", listOf(FlagCatalog.LEAVE_HOURLY), ProStore(context).current.passes!!.admin!!.previews)
    }

    @Test fun flagsOnGoesOnlyWithStatsMore() = runBlocking {
        val c = LaunchConfig(flags = emptyMap(), serverTime = Instant.parse("2026-11-27T00:00:00Z"))
        val yes = mapOf("usage" to true, "usage.stats.more" to true)
        val previews = setOf(FlagCatalog.LEAVE_HOURLY)
        val on = flags(c) { phone(server = Instant.parse("2026-11-27T00:00:00Z"), consents = yes, admin = true, previews = previews) }
        val keys = on.statsOn()!!
        assertEquals(keys.sorted(), keys)
        assertTrue(FlagCatalog.STATS_MORE in keys && "type.shift" in keys)
        assertFalse("a preview tells an admin phone apart: left out", FlagCatalog.LEAVE_HOURLY in keys)
        assertNull("before 정식 출시일 (3.9)", flags(c) { phone(server = Instant.parse("2026-11-09T00:00:00Z"), consents = yes) }.statsOn())
        assertNull("without the extra yes", flags(c) { phone(server = Instant.parse("2026-11-27T00:00:00Z"), consents = mapOf("usage" to true)) }.statsOn())

        // What 사용 통계 sends: flagsOn beside the counts only when given.
        val zone = ZoneId.of("Asia/Seoul")
        val today = com.bamnat.core.time.Today.date(zone).toEpochDay() // the day UsageStats counts by, even with a fixed clock
        val prefs = context.getSharedPreferences("usage_stats", Context.MODE_PRIVATE)
        prefs.edit().putString("book", UsageBook().record(UsageEvent.OPEN, today - 1).encode()).commit()
        val sent = mutableListOf<JSONObject>()
        val stats = UsageStats(context, baseUrl = "https://company.example", zone = { zone }, post = { _, body -> sent += body })
        assertFalse(JSONObject(stats.preview()!!).has("flagsOn"))
        assertEquals(keys, JSONObject(stats.preview(keys)!!).getJSONArray("flagsOn").let { a -> (0 until a.length()).map { a.getString(it) } })
        assertTrue(stats.sendIfDue(allowed = true, flagsOn = keys))
        assertEquals(keys.size, sent.single().getJSONArray("flagsOn").length())
        assertFalse(sent.single().toString().contains("abc"))
    }

    @Test fun theExtraYesIsKeptAndRestored() {
        val s = SettingsRepository(context)
        assertNull(s.current.usageStatsMore)
        s.update { it.copy(usageStatsMore = true) }
        assertEquals(true, SettingsRepository(context).current.usageStatsMore)
        s.update { it.copy(usageStatsMore = null) }
        assertNull(SettingsRepository(context).current.usageStatsMore)
    }
}
