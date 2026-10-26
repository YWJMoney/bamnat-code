package com.bamnat.core.flags

import com.bamnat.core.ops.PolicyConsent
import com.bamnat.core.ops.ServerSwitch
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime

/** [FLAG-01..05][FLAG-12][FLAG-17] 1.28 AL: the book, the order of the steps, KST days, the server's clock for LEGAL rows. */
class FlagEvalTest {
    private fun kst(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0): Instant = LocalDateTime.of(y, m, d, h, min).atZone(KST).toInstant()

    private val dateFlag = Flag("test.date", "날짜 시험", "w", "o", Basis(), listOf(Condition.Date(from = LocalDate.of(2026, 11, 10))), default = false)

    private fun phone(now: Instant, server: Instant? = now, agreed: Set<String> = setOf("3.1"), installed: LocalDate? = LocalDate.of(2026, 10, 1),
                      admin: Boolean = false, previews: Set<String> = emptySet(), version: Int = 39) =
        PhoneFacts(now = now, serverNow = server, versionCode = version, installed = installed, agreed = agreed, admin = admin, previews = previews,
            installHash = "abc")

    private val confirmed = FlagOverride(confirmed = mapOf("play.production" to null, "play.subscription" to null))

    // ---- order (계약 2-1) ----

    @Test fun forceOffBeatsEverything() {
        val p = phone(kst(2026, 12, 1), admin = true, previews = setOf(dateFlag.key))
        val s = FlagEval.state(dateFlag, p, FlagOverride(force = Force.OFF, why = "스팸이 몰려요"))
        assertFalse(s.on)
        assertEquals(FlagSource.FORCE_OFF, s.source)
        assertTrue(s.reason, s.reason.contains("스팸이 몰려요"))
    }

    @Test fun appRangeComesBeforePreviewAndForceOn() {
        val p = phone(kst(2026, 12, 1), admin = true, previews = setOf(dateFlag.key), version = 38)
        val s = FlagEval.state(dateFlag, p, FlagOverride(force = Force.ON, minApp = 39))
        assertEquals(FlagSource.APP_RANGE, s.source)
        assertFalse(s.on)
        assertEquals(FlagSource.APP_RANGE, FlagEval.state(dateFlag.copy(maxApp = 37), p).source)
    }

    @Test fun adminPreviewTurnsOnBeforeConditionsButOnlyOnAnAdminPhone() {
        val before = kst(2026, 11, 1)
        val admin = FlagEval.state(dateFlag, phone(before, admin = true, previews = setOf(dateFlag.key)))
        assertTrue(admin.on)
        assertEquals(FlagSource.ADMIN_PREVIEW, admin.source)
        val user = FlagEval.state(dateFlag, phone(before, admin = false, previews = setOf(dateFlag.key)))
        assertFalse("not an admin: the preview is ignored", user.on)
        assertEquals(FlagSource.CONDITIONS, user.source)
    }

    @Test fun forceOnBeatsUnmetConditionsAndEndsAtUntil() {
        val now = kst(2026, 11, 1)
        val on = FlagEval.state(dateFlag, phone(now), FlagOverride(force = Force.ON, why = "먼저 열어요", until = now.plus(Duration.ofDays(1))))
        assertTrue(on.on)
        assertEquals(FlagSource.FORCE_ON, on.source)
        val over = FlagEval.state(dateFlag, phone(now.plus(Duration.ofDays(2))), FlagOverride(force = Force.ON, until = now.plus(Duration.ofDays(1))))
        assertFalse("the force ended: back to the conditions", over.on)
        assertEquals(FlagSource.CONDITIONS, over.source)
    }

    @Test fun conditionsAllMetOtherwiseTheDefault() {
        val wait = FlagEval.state(dateFlag, phone(kst(2026, 11, 9)))
        assertFalse(wait.on)
        assertEquals(Check.Status.UNMET, wait.checks.single().status)
        assertTrue(wait.reason, wait.reason.startsWith("대기 중이에요"))
        assertTrue(FlagEval.state(dateFlag, phone(kst(2026, 11, 10))).on)
        val onByDefault = dateFlag.copy(default = true)
        assertTrue("unmet conditions leave the default", FlagEval.state(onByDefault, phone(kst(2026, 11, 9))).on)
    }

    @Test fun serverConditionsReplaceTheAppsOwn() {
        val o = FlagOverride(conditions = listOf(Condition.Date(from = LocalDate.of(2026, 11, 1))))
        assertTrue(FlagEval.state(dateFlag, phone(kst(2026, 11, 2)), o).on)
    }

    // ---- KST days, offline ----

    @Test fun aDateRowTurnsOnAtKoreanMidnightWithoutTheServer() {
        val p = { at: Instant -> phone(at, server = null) }
        assertFalse(FlagEval.state(dateFlag, p(kst(2026, 11, 9, 23, 59))).on)
        assertTrue("offline, at 0시 KST", FlagEval.state(dateFlag, p(kst(2026, 11, 10, 0, 0))).on)
        // 2026-11-09 16:00 UTC is already 11-10 01:00 in Korea.
        assertTrue(FlagEval.state(dateFlag, p(Instant.parse("2026-11-09T16:00:00Z"))).on)
    }

    @Test fun hourlyLeaveOpensOnItsDay() {
        val f = FlagCatalog.LEAVE_HOURLY_FLAG
        assertEquals(com.bamnat.core.leave.HourlyLeave.FROM, (f.conditions.single() as Condition.Date).from)
        assertFalse(FlagEval.state(f, phone(kst(2027, 6, 9, 23, 59), server = null)).on)
        assertTrue(FlagEval.state(f, phone(kst(2027, 6, 10, 0, 1), server = null)).on)
    }

    // ---- LEGAL: the server's clock, the policy check kept ----

    @Test fun movingThePhonesClockForwardDoesNotOpenALegalRow() {
        val serverSays = kst(2026, 11, 1)
        val p = phone(now = kst(2026, 12, 25), server = serverSays)
        val s = FlagEval.state(FlagCatalog.BILLING_FLAG, p, confirmed)
        assertFalse(s.on)
        assertEquals(Check.Status.UNMET, s.checks.first().status)
        // The same phone, the server's clock carried forward past 11-10 (offline, by the time since), opens it.
        assertTrue(FlagEval.state(FlagCatalog.BILLING_FLAG, p.copy(serverNow = kst(2026, 11, 10, 0, 0)), confirmed).on)
    }

    @Test fun aLegalRowWithNoServerTimeWaits() {
        val s = FlagEval.state(FlagCatalog.BILLING_FLAG, phone(kst(2026, 12, 1), server = null), confirmed)
        assertFalse(s.on)
        assertEquals(Check.Status.UNKNOWN, s.checks.first().status)
    }

    @Test fun billingNeedsBothManualFactsFromTheServer() {
        val p = phone(kst(2026, 11, 12))
        assertFalse("no answer from the server: unknown", FlagEval.state(FlagCatalog.BILLING_FLAG, p).on)
        val one = FlagOverride(confirmed = mapOf("play.production" to null))
        val s = FlagEval.state(FlagCatalog.BILLING_FLAG, p, one)
        assertFalse(s.on)
        assertTrue(s.reason, s.reason.contains("정기 결제 상품 활성"))
        assertTrue(FlagEval.state(FlagCatalog.BILLING_FLAG, p, confirmed).on)
    }

    @Test fun aTesterWithoutTheYesIsHeldUntilTheOldDayEvenWhenForcedOn() {
        val tester = LocalDate.of(2026, 10, 21)
        val p = phone(kst(2026, 11, 12), agreed = emptySet(), installed = tester)
        val forced = FlagEval.state(FlagCatalog.BILLING_FLAG, p, FlagOverride(force = Force.ON))
        assertFalse("결정 D84: the hold stays", forced.on)
        assertEquals(FlagSource.FORCE_ON, forced.source)
        assertTrue(FlagEval.state(FlagCatalog.BILLING_FLAG, p.copy(agreed = setOf("3.1")), FlagOverride(force = Force.ON)).on)
        assertTrue("the held-until day", FlagEval.state(FlagCatalog.BILLING_FLAG, p.copy(serverNow = kst(2026, 11, 24, 0, 1)), FlagOverride(force = Force.ON)).on)
        val preview = FlagEval.state(FlagCatalog.BILLING_FLAG, p.copy(admin = true, previews = setOf("billing")))
        assertFalse("an admin preview keeps the policy check too", preview.on)
    }

    @Test fun thePolicyHoldIsTheSameAs127() {
        val cal = PolicyCalendar.SEED
        val days = (0..60L).map { LocalDate.of(2026, 10, 10).plusDays(it) }
        val installs = listOf(null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 2))
        for (d in days) for (i in installs) for (a in listOf(true, false)) {
            assertEquals("$d $i $a", PolicyConsent.asks(i, a, d), cal.holds("3.1", i, if (a) setOf("3.1") else emptySet(), d))
        }
        assertEquals("a later yes counts", false, cal.holds("3.1", null, setOf("3.2"), LocalDate.of(2026, 11, 12)))
    }

    @Test fun theServerMovesPolicyDates() {
        val moved = PolicyCalendar.SEED.merged(listOf(PolicyVersion("3.1", LocalDate.of(2026, 10, 27), LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 24))))
        val p = phone(kst(2026, 11, 12))
        assertTrue(FlagEval.state(FlagCatalog.BILLING_FLAG, p, confirmed).on)
        assertFalse("the effective day moved to 11-20", FlagEval.state(FlagCatalog.BILLING_FLAG, p, confirmed, moved).on)
        assertNotNull("3.2 is kept", moved.of("3.2"))
    }

    @Test fun adsNeedTheBillingConditionsAndNoRefusedAdConsent() {
        val p = phone(kst(2026, 11, 12))
        assertTrue("not asked yet: the consent form may still ask", FlagEval.state(FlagCatalog.ADS_FLAG, p, confirmed).on)
        assertFalse(FlagEval.state(FlagCatalog.ADS_FLAG, p.copy(consents = mapOf("ads" to false)), confirmed).on)
        assertFalse(FlagEval.state(FlagCatalog.ADS_FLAG, phone(kst(2026, 11, 1)), confirmed).on)
    }

    @Test fun statsMoreWaitsForPolicy32AndItsOwnYes() {
        val f = FlagCatalog.STATS_MORE_FLAG
        assertEquals(Risk.LEGAL, f.risk)
        val after = phone(kst(2026, 11, 27)).copy(consents = mapOf("usage" to true, "usage.stats.more" to true))
        assertTrue(FlagEval.state(f, after).on)
        assertFalse("before 3.2", FlagEval.state(f, after.copy(serverNow = kst(2026, 11, 26))).on)
        assertFalse("no extra yes", FlagEval.state(f, after.copy(consents = mapOf("usage" to true))).on)
        assertFalse("usage stats off", FlagEval.state(f, after.copy(consents = mapOf("usage" to false, "usage.stats.more" to true))).on)
    }

    // ---- the other kinds of condition ----

    @Test fun rolloutIsStableAndSpread() {
        val f = Flag("r", "r", "w", "o", Basis(), listOf(Condition.Rollout(30)), default = false)
        val hashes = (0 until 2000).map { "install-$it" }
        val first = hashes.map { FlagEval.state(f, phone(kst(2026, 11, 1)).copy(installHash = it)).on }
        val again = hashes.map { FlagEval.state(f, phone(kst(2027, 1, 1)).copy(installHash = it)).on }
        assertEquals("the same phone, the same answer", first, again)
        val share = first.count { it } / 2000.0
        assertTrue("about 30%: $share", share in 0.26..0.34)
        assertEquals(Check.Status.UNKNOWN, FlagEval.state(f, phone(kst(2026, 11, 1)).copy(installHash = null)).checks.single().status)
        assertTrue((0 until 100).all { FlagEval.bucket("x$it", "k") in 0..99 })
    }

    @Test fun profileWorkTypeAdminAppAndAnyOf() {
        val p = phone(kst(2026, 11, 1)).copy(profile = setOf("pay.setup"), workType = "SHIFT")
        fun on(vararg c: Condition) = FlagEval.state(Flag("k", "k", "w", "o", Basis(), c.toList(), default = false), p).on
        assertTrue(on(Condition.Profile("pay.setup")))
        assertFalse(on(Condition.Profile("company.linked")))
        assertTrue(on(Condition.WorkType(setOf("SHIFT", "DAY"))))
        assertFalse(on(Condition.WorkType(setOf("BUSINESS"))))
        assertFalse(on(Condition.Admin))
        assertTrue(on(Condition.App(min = 39)))
        assertFalse(on(Condition.App(max = 38)))
        assertTrue(on(Condition.AnyOf(listOf(Condition.Admin, Condition.Profile("pay.setup")))))
        assertFalse(on(Condition.AnyOf(listOf(Condition.Admin, Condition.Profile("company.linked")))))
    }

    // ---- the book ----

    @Test fun theBookHasTheContractsKeysAndEveryRowIsExplained() {
        val keys = FlagCatalog.ALL.map { it.key }
        val contract = listOf("billing", "ads", "type.shift", "type.day", "type.hourly", "type.field", "type.business", "type.invest", "leave.hourly",
            "plaza.write", "company.report", "inquiry.photos", "share.create", "usage.stats", "plaza.news")
        assertTrue(keys.containsAll(contract))
        assertEquals(keys.size, keys.toSet().size)
        for (f in FlagCatalog.ALL) {
            assertTrue(f.key, f.name.isNotBlank() && f.what.isNotBlank() && f.whyOff.isNotBlank() && f.basis.words.isNotBlank())
            assertTrue(f.key, f.owner.isNotBlank() && f.since == (if (f.group == FlagGroup.SERVER) "1.22.0" else "1.28.0"))
            if (f.risk == Risk.LEGAL) assertTrue("${f.key}: a LEGAL row rests on a policy date", f.conditions.any { it is Condition.Date && it.policy != null })
        }
        ServerSwitch.entries.forEach { assertEquals(it, FlagCatalog.switchOf(FlagCatalog.switchKey(it))) }
        WorkStyle.entries.forEach { assertTrue(FlagCatalog.of(FlagCatalog.typeKey(it))!!.default) }
    }

    @Test fun unknownKeysAreIgnoredAndFreeRowsAreReadByName() {
        assertNull(FlagCatalog.of("some.newer.flag"))
        assertNull(FlagCatalog.of("free.lower_case"))
        val free = FlagCatalog.of("free.MONEY_STATS")!!
        assertFalse(free.default)
        val states = FlagEval.all(phone(kst(2026, 11, 1)), mapOf("free.MONEY_STATS" to FlagOverride(force = Force.ON), "some.newer.flag" to FlagOverride(force = Force.OFF)))
        assertTrue(states.single { it.key == "free.MONEY_STATS" }.on)
        assertTrue(states.none { it.key == "some.newer.flag" })
        assertEquals(FlagCatalog.ALL.size + 1, states.size)
    }

    // ---- kinds of work ----

    @Test fun hiddenKindsLeaveOnlyThePickingPlaces() {
        val off = setOf(WorkStyle.INVEST, WorkStyle.BUSINESS)
        val user = TypeExposure.choices({ it !in off }, owner = false)
        assertEquals(WorkStyle.entries - off, user.map { it.style })
        val keep = TypeExposure.choices({ it !in off }, owner = false, keep = WorkStyle.INVEST)
        assertTrue("the kind in use stays", keep.any { it.style == WorkStyle.INVEST && !it.hidden })
        val owner = TypeExposure.choices({ it !in off }, owner = true)
        assertEquals(WorkStyle.entries, owner.map { it.style })
        assertEquals(off, owner.filter { it.hidden }.map { it.style }.toSet())
        assertEquals("all hidden: 교대 anyway", listOf(TypeExposure.Choice(WorkStyle.SHIFT, false)), TypeExposure.choices({ false }, owner = false))
        assertEquals(TypeExposure.ALL, TypeExposure.choices({ true }, owner = false))
    }
}
