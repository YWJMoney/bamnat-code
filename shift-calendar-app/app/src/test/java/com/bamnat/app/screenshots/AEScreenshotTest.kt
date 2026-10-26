package com.bamnat.app.screenshots

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.FakeInviteApi
import com.bamnat.app.tier.InviteAnswer
import com.bamnat.app.tier.InviteView
import com.bamnat.app.tier.PhoneMark
import com.bamnat.app.tier.ReferrerSource
import com.bamnat.core.tier.InviteRules
import com.bamnat.core.tier.InviteTerms
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration
import java.time.Instant

/**
 * [INVITE-02][INVITE-03][INVITE-05][INVITE-07][INVITE-08] 1.26 묶음 AE's screenshots, numbers 1040~1059, each an end-to-end check:
 * a tap, what the screen shows, and what was sent and kept. 1040~1043 the inviter (nothing goes before the line is
 * agreed, the share sheet carries the link and code, the progress "3/5명 · 2명은 사용 기간 채우는 중"), 1044~1047 the
 * friend's card from Play's referrer (알리기 sends, 괜찮아요 sends nothing, the period of use counted once a day),
 * 1048~1050 초대 코드 넣기 within 7 days, 1051~1052 settings and search, 1053~1055 small, landscape, dark with 200%
 * text, 1056 the developer app's rules said before anything is sent. The real server's round trip is InviteAppTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AEScreenshotTest : ScreenshotBase() {
    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val PHONE_NIGHT = "w360dp-h780dp-port-night-450dpi"
        const val REFERRER = "utm_source=bamnat_invite&utm_content=FRND2345"
    }

    private lateinit var api: FakeInviteApi

    @Before fun invites() = runBlocking {
        api = FakeInviteApi(viewAnswer = view(code = null))
        val c = app.container
        c.inviteApi = api
        c.phoneMark = { PhoneMark.hash("robolectric-android-id") }
        c.referrerSource = ReferrerSource { null }
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        // Installed a day ago unless a shot says otherwise.
        c.invites.installedAt = { Today.instant().minus(Duration.ofDays(1)) }
    }

    private fun view(
        code: String? = "ABCD2345", progress: Int = 0, waiting: Int = 0, friends: Int = 0, given: Int = 0, capReached: Boolean = false,
        terms: InviteTerms = InviteTerms.DEFAULT,
    ) = InviteView(code, code?.let { "https://company.example/i/$it" }, terms, progress, waiting, friends, given, capReached, null, Today.instant())

    /** What went to the server about this phone: the rules carry nothing of it (no install number). */
    private fun sent() = api.calls.filter { it != "rules" }

    private fun Screen.openPro(wide: Boolean = false) {
        tab(4, wide)
        tapText("프로", exact = true, across = if (wide) 0.2f else 0.5f)
        // At 200% text the 프로 screen is long: the card is far down.
        tapText("친구 초대", exact = true, scrolls = 30, across = if (wide) 0.7f else 0.5f)
        reveal("친구 초대", exact = true, across = if (wide) 0.7f else 0.5f)
    }

    private fun Screen.noCuts(name: String) {
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        LayoutCheck.report("ae_$name", cuts)
        assertEquals("$name: $cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    /** The text the share sheet was handed, or null when none opened. */
    private fun shared(): String? {
        var started: Intent? = shadowOf(app).nextStartedActivity
        while (started != null && started.action != Intent.ACTION_CHOOSER) started = shadowOf(app).nextStartedActivity
        @Suppress("DEPRECATION")
        return (started?.getParcelableExtra(Intent.EXTRA_INTENT) as Intent?)?.getStringExtra(Intent.EXTRA_TEXT)
    }

    // ---- 424 [INVITE-02] ----
    @Test fun nothingGoesBeforeTheLineIsAgreed() = shoot("1040_ae_invite_consent", FOLDED) {
        openPro()
        tapText("초대 링크 만들기", exact = true)
        reveal("설치 번호와 휴대폰 표시")
        assertTrue(shows("동의하고 링크 만들기", exact = true))
        assertEquals("nothing sent yet", emptyList<String>(), sent())
        assertNull(app.container.invites.store.current.view)
        noCuts("consent")
    }

    @Test fun theLinkIsMadeAndShared() = shoot("1041_ae_invite_shared", FOLDED) {
        api.viewAnswer = view()
        openPro()
        tapText("초대 링크 만들기", exact = true)
        tapText("동의하고 링크 만들기", exact = true)
        assertEquals(listOf("code"), api.calls.filter { it == "code" })
        // The phone mark is a 64-letter hash, never the Android ID itself.
        assertTrue(api.phones.single().matches(Regex("[0-9a-f]{64}")))
        assertFalse(api.phones.single().contains("robolectric"))
        assertEquals("ABCD2345", app.container.invites.store.current.view?.code)
        val text = shared()
        assertNotNull("the share sheet opened", text)
        assertTrue(text!!.contains("https://company.example/i/ABCD2345"))
        assertTrue(text.contains("ABCD-2345"))
        reveal("0/5명")
        assertTrue(shows("초대한 친구 0명 · 프로 0개월"))
        assertTrue(shows("초대 링크 보내기", exact = true))
    }

    @Test fun theInviterSeesFriendsAndMonths() = shoot("1042_ae_invite_counts", PHONE_393) {
        api.viewAnswer = view(progress = 3, waiting = 2, friends = 8, given = 1)
        runBlocking { app.container.invites.makeLink() }
        openPro()
        reveal("3/5명 · 2명은 사용 기간 채우는 중", exact = true)
        assertTrue(shows("초대한 친구 8명 · 프로 1개월", exact = true))
        assertTrue(shows("내 초대 코드 ABCD-2345"))
        // Opening the screen asked the server afresh, once.
        assertTrue("view" in api.calls)
        noCuts("counts")
    }

    @Test fun overTheCapFriendsStillCount() = shoot("1043_ae_invite_cap", PHONE_393) {
        api.viewAnswer = view(progress = 4, friends = 64, given = 12, capReached = true, terms = InviteTerms(5, 7, 1, 12))
        runBlocking { app.container.invites.makeLink() }
        openPro()
        reveal("올해 받을 수 있는 12개월을 모두 받았어요")
        assertTrue(shows("초대한 친구 64명 · 프로 12개월"))
        assertTrue(shows("1년에 12개월까지"))
    }

    // ---- 425 [INVITE-03] ----
    private fun arrived() = runBlocking {
        // As on a phone that came by the link: Play has the referrer from the first read on.
        app.container.invites.store.edit { com.bamnat.app.tier.InviteState() }
        app.container.referrerSource = ReferrerSource { REFERRER }
        app.container.invites.sync()
        assertEquals("FRND2345", app.container.invites.store.current.found)
        assertEquals("reading the referrer sends nothing", emptyList<String>(), sent())
    }

    /** The card sits in the calendar's notices; under the alarm warning it waits folded in "알림 n개 더 보기". */
    private fun Screen.reachArrival() {
        if (!shows("친구 초대로 오셨나요?")) tapText("알림 ", prefix = true, scrolls = 0)
        reveal("친구 초대로 오셨나요?")
    }

    @Test fun theFriendIsAskedOnTheCalendar() = shoot("1044_ae_arrival_card", FOLDED) {
        arrived()
        reachArrival()
        assertTrue(shows("FRND-2345"))
        assertTrue(shows("나에게 오는 보상은 없어요"))
        assertTrue(shows("7일이 지난 뒤 앱을 다시 열면"))
        assertTrue(sent().isEmpty())
        noCuts("arrival")
    }

    @Test fun telling() = shoot("1045_ae_arrival_told", FOLDED) {
        arrived()
        reachArrival()
        tapText("알리기", exact = true)
        assertEquals(listOf("FRND2345" to "referrer"), api.uses)
        val s = app.container.invites.store.current
        assertEquals("FRND2345", s.reportedCode)
        assertEquals("waiting", s.answer?.status)
        reveal("초대한 분께 알렸어요. 7일 뒤에 앱을 다시 열면")
        // Each day the app is opened the check goes by itself, once that day, until the period is used.
        val start = Today.clock
        try {
            Today.use(java.time.Clock.offset(start, Duration.ofDays(1)))
            api.seenAnswer = InviteAnswer("waiting", daysLeft = 6)
            runBlocking { app.container.invites.sync() }
            runBlocking { app.container.invites.sync() }
            assertEquals(1, api.calls.count { it == "seen" })
            Today.use(java.time.Clock.offset(start, Duration.ofDays(7)))
            api.seenAnswer = InviteAnswer("qualified")
            runBlocking { app.container.invites.sync() }
            assertEquals("qualified", app.container.invites.store.current.answer?.status)
            Today.use(java.time.Clock.offset(start, Duration.ofDays(8)))
            runBlocking { app.container.invites.sync() }
            assertEquals("no more checks once it counted", 2, api.calls.count { it == "seen" })
        } finally {
            Today.use(start)
        }
    }

    @Test fun declining() = shoot("1046_ae_arrival_declined", FOLDED) {
        arrived()
        reachArrival()
        tapText("괜찮아요", exact = true)
        assertFalse(shows("친구 초대로 오셨나요?"))
        assertTrue("nothing sent", sent().isEmpty())
        assertTrue(app.container.invites.store.current.offerClosed)
    }

    @Test fun aStoreSearchInstallAsksNothing() = shoot("1047_ae_no_referrer", FOLDED) {
        runBlocking {
            app.container.referrerSource = ReferrerSource { "utm_source=google-play&utm_medium=organic" }
            app.container.invites.sync()
        }
        val s = app.container.invites.store.current
        assertTrue(s.referrerRead)
        assertNull(s.found)
        assertFalse(shows("친구 초대로 오셨나요?"))
        // Read once: Play is not asked again.
        var asked = 0
        app.container.referrerSource = ReferrerSource { asked++; REFERRER }
        runBlocking { app.container.invites.sync() }
        assertEquals(0, asked)
    }

    @Test fun aCodeTypedByHand() = shoot("1048_ae_typed_code", FOLDED) {
        openPro()
        tapText("초대 코드 넣기", exact = true)
        typeInto("받은 초대 코드 (8자리)", "frnd-2345")
        reveal("7일이 지난 뒤 앱을 다시 열면")
        tapText("동의하고 보내기", exact = true)
        assertEquals(listOf("FRND2345" to "typed"), api.uses)
        reveal("초대한 분께 알렸어요")
        assertFalse("once only", shows("초대 코드 넣기", exact = true))
    }

    @Test fun aWrongCodeIsSaidPlainly() = shoot("1049_ae_typed_wrong", FOLDED) {
        openPro()
        tapText("초대 코드 넣기", exact = true)
        typeInto("받은 초대 코드 (8자리)", "ZZZZ2345")
        tapText("동의하고 보내기", exact = true)
        reveal("초대 코드가 맞지 않아요")
        assertNull(app.container.invites.store.current.reportedCode)
    }

    @Test fun afterSevenDaysThereIsNoCodeToType() = shoot("1050_ae_typed_late", FOLDED) {
        app.container.invites.installedAt = { Today.instant().minus(Duration.ofDays(8)) }
        openPro()
        assertFalse(shows("초대 코드 넣기", exact = true))
        assertTrue(shows("초대 링크 만들기", exact = true))
    }

    // ---- 427 [INVITE-07] ----
    @Test fun settingsHasTheRow() = shoot("1051_ae_settings_row", FOLDED) {
        tab(4, wide = false)
        reveal("친구 초대", exact = true)
        tapText("친구 초대", exact = true)
        reveal("초대 링크 만들기", exact = true)
    }

    @Test fun searchFindsIt() = shoot("1052_ae_search", FOLDED) {
        tab(4, wide = false)
        typeInto("설정 찾기", "초대 코드")
        reveal("초대 코드 넣기", exact = true)
    }

    @Test fun helpAnswersIt() {
        val topic = com.bamnat.app.ui.settings.HelpFaq.search("친구 초대 프로 한 달").first()
        assertEquals("invite", topic.id)
        assertTrue(topic.answer.contains("5명"))
        assertTrue(topic.answer.contains("7일"))
        assertTrue(topic.answer.contains("끄는 법"))
        assertNotNull(com.bamnat.core.guide.FeatureRegistry.get("pro.invite"))
    }

    @Test fun smallPhone() = shoot("1053_ae_small_arrival", SMALL_PHONE) {
        arrived()
        reachArrival()
        reveal("알리기", exact = true)
        noCuts("small")
    }

    @Test fun landscape() = shoot("1054_ae_landscape", PHONE_LANDSCAPE) {
        api.viewAnswer = view(progress = 1, friends = 6, given = 1)
        runBlocking { app.container.invites.makeLink() }
        openPro(wide = true)
        reveal("1/5명", across = 0.7f)
        assertTrue(shows("초대한 친구 6명 · 프로 1개월"))
    }

    @Test fun darkHugeText() = shoot("1055_ae_dark_huge", PHONE_NIGHT, fontScale = 2f) {
        api.viewAnswer = view(progress = 3, waiting = 2, friends = 3)
        runBlocking { app.container.invites.makeLink() }
        openPro()
        reveal("3/5명 · 2명은 사용 기간 채우는 중")
        reveal("초대 링크 보내기", exact = true)
        noCuts("huge")
    }

    /** [INVITE-08] The developer app's numbers reach the card before anything about this phone is sent. */
    @Test fun theServersRulesAreSaid() = shoot("1056_ae_rules_from_server", FOLDED) {
        api.rulesAnswer = InviteTerms(need = 3, days = 10, months = 2, capMonths = 6)
        openPro()
        reveal("친구 3명이 각자 10일 이상 쓰면 나에게 프로 2개월")
        assertTrue(shows("10일이 지나 앱을 다시 열면"))
        assertTrue(shows("1년에 6개월까지"))
        assertTrue("rules" in api.calls)
        assertEquals("only the rules, which carry nothing of this phone", emptyList<String>(), sent())
        assertEquals(InviteTerms(3, 10, 2, 6), app.container.invites.store.current.terms)
    }

    @Test fun thePassListNamesTheInvite() {
        assertEquals("친구 초대", com.bamnat.app.ui.pro.passWhy("invite"))
        assertEquals("회사 방식 채택", com.bamnat.app.ui.pro.passWhy("rule"))
        assertEquals("임금표 채택", com.bamnat.app.ui.pro.passWhy("rate"))
        assertTrue(InviteRules.shareText("x", "ABCD2345").contains("7일 안에"))
        assertTrue(Instant.EPOCH.isBefore(Today.instant()))
    }
}
