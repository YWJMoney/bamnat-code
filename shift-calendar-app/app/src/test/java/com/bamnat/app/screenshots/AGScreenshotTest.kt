package com.bamnat.app.screenshots

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.tier.FakeInviteApi
import com.bamnat.app.tier.InviteView
import com.bamnat.app.tier.PhoneMark
import com.bamnat.app.tier.ReferrerSource
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.pay.PayScreen
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.GuideState
import com.bamnat.core.tier.InviteTerms
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.Duration

/**
 * AG 1.27 [INVITE-09][INVITE-10][INVITE-11][INVITE-12][INVITE-13][MON-12] 묶음 AG's screenshots, numbers 1080~1099, each an
 * end-to-end check (a tap, what shows, what was sent and kept): 1080~1082 the friend recognised by Play's referrer at the
 * end of the first setup (the last button tells, '알리지 않고 저장' sends nothing), 1083 the calendar card first and never
 * folded, 1084 the calendar's invite card and 그만 보기, 1085 설정 맨 위 '함께 만들어요', 1086~1088 the 프로 sheet a locked
 * feature opens in the 등급 미리 보기 (ad and invite beside 프로) and the lock panel, 1089 after an app stop, 1090 the 프로
 * screen's '다음 프로까지', 1091 the sheet at 200% text. The real server's round trip is tier/InviteAutoAppTest.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class AGScreenshotTest : ScreenshotBase() {
    private companion object {
        const val PHONE_393 = "w393dp-h873dp-port-440dpi"
        const val REFERRER = "utm_source=bamnat_invite&utm_content=FRND2345"
    }

    private lateinit var api: FakeInviteApi
    private val c get() = app.container

    @Before fun invites() {
        api = FakeInviteApi(viewAnswer = view(code = null))
        c.inviteApi = api
        c.phoneMark = { PhoneMark.hash("robolectric-android-id") }
        c.referrerSource = ReferrerSource { null }
        c.invites.installedAt = { Today.instant().minus(Duration.ofHours(1)) }
        clearGlobals()
    }

    /** The sheet, the links and the feature switches live past one test (objects, GuideStore's one instance). */
    @After fun clearGlobals() {
        com.bamnat.app.ui.pro.ProOffer.close()
        com.bamnat.app.ui.settings.InquiryLinks.take()
        com.bamnat.app.ui.settings.SettingsLinks.target.value?.let { com.bamnat.app.ui.settings.SettingsLinks.consume(it) }
        val guide = com.bamnat.app.data.GuideStore.get(app)
        listOf("invite.nudge", "grow.card").forEach { guide.setOn(FeatureRegistry.get(it)!!, GuideState.ALL, true) }
    }

    private fun view(code: String? = "ABCD2345", progress: Int = 0, waiting: Int = 0, friends: Int = 0, given: Int = 0) =
        InviteView(code, code?.let { "https://company.example/i/$it" }, InviteTerms.DEFAULT, progress, waiting, friends, given, false, null, Today.instant())

    private fun sent() = api.calls.filter { it != "rules" }

    private fun Screen.noCuts(name: String) {
        val m = app.resources.displayMetrics
        val cuts = LayoutCheck.cuts(nodes(merged = false), m.widthPixels, m.heightPixels)
        LayoutCheck.report("ag_$name", cuts)
        assertEquals("$name: $cuts", emptyList<LayoutCheck.Cut>(), cuts)
    }

    private fun shared(): String? {
        var started: Intent? = shadowOf(app).nextStartedActivity
        while (started != null && started.action != Intent.ACTION_CHOOSER) started = shadowOf(app).nextStartedActivity
        @Suppress("DEPRECATION")
        return (started?.getParcelableExtra(Intent.EXTRA_INTENT) as Intent?)?.getStringExtra(Intent.EXTRA_TEXT)
    }

    /** The first setup by a public cycle up to its last screen, on a phone that came by a friend's link. */
    private fun Screen.toSummary() {
        c.referrerSource = ReferrerSource { REFERRER }
        c.companyApi = com.bamnat.app.company.CompanyClient(baseUrl = "")
        tapText("순서가 돌아가요")
        typeInto("회사 이름 (선택)", "GS EPS")
        tapText("‘GS EPS’", prefix = true)
        waitFor(400)
        tapText("4조 2교대 · 주주휴휴야야휴휴")
        tapText("이 순서로 설정 이어가기")
        tapText("네, 시간이 맞아요")
        tapText("다음", exact = true)
        tapText("야간", exact = true); tapText("둘째 날")
        tapText("회사 근무표와 같아요"); tapText("다음", exact = true)
        reveal("친구 초대 링크로 오셨어요")
    }

    // ---- 433 [INVITE-10] 결정 AG-D2 ----
    @Test fun theLastSetupScreenSaysTheInvite() = shoot("1080_ag_setup_invite_line", PHONE_393, fresh(null) {
        toSummary()
        assertTrue(shows("저장하고 초대한 분께 알리기", exact = true))
        assertTrue(shows("알리지 않고 저장", exact = true))
        assertTrue(shows("FRND-2345"))
        // Play's referrer was read on the phone; nothing about it went anywhere.
        assertEquals("FRND2345", c.invites.store.current.found)
        assertEquals(emptyList<String>(), sent())
        noCuts("setup")
    })

    @Test fun theLastButtonTellsTheInviter() = shoot("1081_ag_setup_told", PHONE_393, fresh(null) {
        toSummary()
        tapText("저장하고 초대한 분께 알리기", exact = true)
        waitFor(400)
        assertEquals(listOf("FRND2345" to "referrer"), api.uses)
        assertEquals("FRND2345", c.invites.store.current.reportedCode)
        // [INVITE-13] On the calendar, no question to find: what was said, and the friend's own chance to invite.
        reveal("초대한 분께 알렸어요")
        assertFalse(shows("친구 초대로 오셨나요?"))
        assertTrue(shows("당신도 친구 5명이 각자 7일 이상 쓰면"))
        assertTrue(shows("내 초대 링크 만들기", exact = true))
    })

    @Test fun savingWithoutTellingSendsNothing() = shoot("1082_ag_setup_not_told", PHONE_393, fresh(null) {
        toSummary()
        tapText("알리지 않고 저장", exact = true)
        waitFor(400)
        assertEquals(emptyList<String>(), sent())
        assertNull(c.invites.store.current.reportedCode)
        assertTrue(c.invites.store.current.offerClosed)
        assertFalse(shows("친구 초대로 오셨나요?"))
        assertNull(c.invites.relay())
    })

    @Test fun theCalendarCardIsNeverFolded() = shoot("1083_ag_arrival_first", FOLDED, style(WorkStyle.SHIFT) {
        // A phone set up another way (no last screen): the referrer is read after the first setup. (The launch's own
        // sync read the empty test referrer already, so this phone starts again as one that came by the link.)
        c.invites.store.edit { com.bamnat.app.tier.InviteState() }
        c.referrerSource = ReferrerSource { REFERRER }
        runBlocking { c.invites.sync() }
        settle()
        // First among the notices: seen without opening "알림 n개 더 보기".
        reveal("친구 초대로 오셨나요?")
        assertTrue(shows("알리기", exact = true))
        // A code from the link is kept: none can be typed over it.
        assertFalse(c.invites.mayType())
    })

    // ---- 434 [INVITE-11] ----
    @Test fun theCalendarAsksToInviteSeldom() = shoot("1084_ag_invite_nudge", FOLDED, style(WorkStyle.SHIFT) {
        c.invites.installedAt = { Today.instant().minus(Duration.ofDays(4)) }
        api.viewAnswer = view(progress = 3, waiting = 1, friends = 3)
        runBlocking { c.invites.makeLink() }
        // The calendar reads the install day again as it opens.
        tab(1, wide = false); tab(0, wide = false)
        settle()
        if (!shows("친구에게 밤낮 알려 주기")) tapText("알림 ", prefix = true, scrolls = 0)
        reveal("친구에게 밤낮 알려 주기")
        assertTrue(shows("다음 프로까지 2명"))
        assertEquals(Today.date(), c.grow.current.inviteShownOn)
        tapText("그만 보기", exact = true)
        assertFalse(shows("친구에게 밤낮 알려 주기"))
        val f = FeatureRegistry.get("invite.nudge")!!
        assertTrue("off in 기능 켜고 끄기", com.bamnat.app.data.GuideStore.get(app).current.isOff(f, GuideState.ALL))
        // On again in 기능 켜고 끄기: the same day it shows again, and the picture is of the card.
        com.bamnat.app.data.GuideStore.get(app).setOn(f, GuideState.ALL, true)
        settle()
        if (!shows("친구에게 밤낮 알려 주기")) tapText("알림 ", prefix = true, scrolls = 0)
        reveal("친구에게 밤낮 알려 주기")
    })

    @Test fun settingsStartsWithGrowingTogether() = shoot("1085_ag_settings_top", FOLDED, style(WorkStyle.SHIFT) {
        tab(4, wide = false)
        reveal("함께 만들어요", exact = true)
        assertTrue(shows("친구 5명이 각자 7일 이상 쓰면 나에게 프로 1개월, 5명 더 채울 때마다 또 1개월(상한 없음)."))
        assertTrue(shows("불편한 점 알려 주기"))
        assertTrue(shows("개발자가 직접 읽고 답을 드리고"))
        // The 더 보기 row says the rule as meant, not "다른 날 한 번 더 열면".
        reveal("5명 더 채울 때마다 또 · 초대 코드 넣기")
        scrollDown(2000)
        reveal("함께 만들어요", exact = true)
        noCuts("settings_top")
    })

    @Test fun feedbackOpensTheInquiryForm() = shoot("1093_ag_feedback_form", FOLDED, style(WorkStyle.SHIFT) {
        tab(4, wide = false)
        reveal("함께 만들어요", exact = true)
        tapText("알려 주기", exact = true)
        // The 문의 opens on its form, from here.
        reveal("문의")
        assertNull("the inquiry screen took the start", com.bamnat.app.ui.settings.InquiryLinks.pending.value)
    })

    @Test fun closingTheSettingsCardWaitsAMonth() = shoot("1092_ag_settings_closed", FOLDED, style(WorkStyle.SHIFT) {
        tab(4, wide = false)
        reveal("함께 만들어요", exact = true)
        tapText("닫기", exact = true)
        assertFalse(shows("함께 만들어요", exact = true))
        assertEquals(Today.date(), c.grow.current.cardClosedOn)
    })

    // ---- 434 [MON-12] the 프로 sheet, in the alarm check's 등급 미리 보기 (before the launch nothing is locked) ----
    private fun lockedPay(): Screen.() -> Unit = payScreen(Tier.FREE, withProfile = true) { vm ->
        PayScreen(vm, onBack = {})
        com.bamnat.app.ui.pro.ProOfferHost(app.container)
    }

    @Test fun aLockedFeatureOffersThreeWays() = shoot("1086_ag_pro_sheet", PHONE_393) {
        lockedPay().invoke(this)
        tapText("명세서로 맞춰 보기", scrolls = 30)
        reveal("‘명세서 대조’ · 프로 기능이에요")
        assertTrue(shows("광고 보고 오늘 하루 열기", exact = true))
        assertTrue(shows("미리 보기에서는 광고가 나오지 않아요"))
        reveal("친구 초대로 프로 이용권 받기", exact = true)
        assertTrue(shows("친구 5명이 각자 7일 이상 쓰면"))
        assertTrue(app.container.featureGate.state.value.preview)
        noCuts("sheet")
    }

    @Test fun theSheetMakesTheLinkAfterTheLine() = shoot("1087_ag_pro_sheet_invite", PHONE_393) {
        api.viewAnswer = view()
        lockedPay().invoke(this)
        tapText("명세서로 맞춰 보기", scrolls = 30)
        tapText("친구 초대 링크 만들기", exact = true)
        reveal("설치 번호와 휴대폰 표시")
        assertEquals(emptyList<String>(), sent())
        tapText("동의하고 링크 만들기", exact = true)
        assertEquals(listOf("code"), api.calls.filter { it == "code" })
        val text = shared()
        assertNotNull(text)
        assertTrue(text!!.contains("https://company.example/i/ABCD2345"))
        assertTrue(text.contains("저절로"))
    }

    @Test fun theLockPanelOffersTheInviteToo() = shoot("1088_ag_lock_panel", PHONE_393) {
        c.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
        settle()
        content {
            val gate = app.container.featureGate.state.value
            androidx.compose.foundation.layout.Column {
                com.bamnat.app.ui.pro.ProLock(com.bamnat.app.tier.Feature.WORK_COMPARE, gate) {
                    androidx.compose.material3.Text("이달과 지난달 비교")
                }
            }
            com.bamnat.app.ui.pro.ProOfferHost(app.container)
        }
        settle()
        reveal("친구 초대로 프로 이용권 받기", exact = true)
        tapText("친구 초대로 프로 이용권 받기", exact = true)
        reveal("‘근무·급여 비교’ · 프로 기능이에요")
        assertTrue(shows("미리 보기에서는 광고가 나오지 않아요"))
    }

    // ---- 435 [INVITE-12] ----
    @Test fun afterAnAppStopOneAsk() = shoot("1089_ag_crash_ask", FOLDED, style(WorkStyle.SHIFT) {
        com.bamnat.app.diag.CrashLog.write(app, "main", IllegalStateException(), Today.millis() - 60_000)
        // The calendar reads the note again as it opens.
        tab(1, wide = false); tab(0, wide = false)
        if (!shows("앱이 갑자기 닫혔어요")) tapText("알림 ", prefix = true, scrolls = 0)
        reveal("앱이 갑자기 닫혔어요")
        tapText("점검 내용과 함께 알려 주기")
        assertTrue(c.grow.current.crashSeenAt > 0)
        com.bamnat.app.diag.CrashLog.clear(app)
    })

    @Test fun theThirdUndoOfADayAsksOnce() {
        val store = app.container.grow
        assertFalse(store.undone())
        assertFalse(store.undone())
        assertTrue(store.undone())
        assertFalse("once in two weeks", store.undone())
        assertEquals(Today.date(), store.current.undoToldOn)
    }

    // ---- 432 [INVITE-09] ----
    @Test fun theProScreenSaysWhatIsLeft() = shoot("1090_ag_pro_next", PHONE_393, style(WorkStyle.SHIFT) {
        api.viewAnswer = view(progress = 3, waiting = 2, friends = 8, given = 1)
        runBlocking { c.invites.makeLink() }
        tab(4, wide = false)
        tapText("프로", exact = true)
        tapText("친구 초대", exact = true, scrolls = 30)
        reveal("3/5명 · 2명은 사용 기간 채우는 중", exact = true)
        assertTrue(shows("다음 프로까지 2명", exact = true))
        assertTrue(shows("5명 더 채울 때마다 또 1개월"))
        assertTrue(shows("같은 친구·같은 휴대폰은 한 번만"))
        assertFalse(shows("넘게 쓸 때마다"))
    })

    @Test fun theSheetAtLargeText() = shoot("1091_ag_pro_sheet_200", PHONE_393, fontScale = 2f) {
        lockedPay().invoke(this)
        tapText("명세서로 맞춰 보기", scrolls = 30)
        reveal("친구 초대로 프로 이용권 받기", exact = true)
        noCuts("sheet_200")
    }
}
