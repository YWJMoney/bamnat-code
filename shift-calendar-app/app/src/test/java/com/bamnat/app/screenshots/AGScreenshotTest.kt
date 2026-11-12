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
        /** The 친구 초대 권유's row in the 알림함. */
        const val NUDGE = "친구를 초대하면 무료로 써요"
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
        tapText("네, 맞아요", exact = true)
        tapText("야간", exact = true); tapText("둘째 날")
        tapText("회사 근무표와 같아요")
        reveal("친구 초대 링크로 오셨어요")
    }

    // ---- 433 [INVITE-10] 결정 AG-D2 ----
    @Test fun theLastSetupScreenSaysTheInvite() = shoot("1080_ag_setup_invite_line", PHONE_393, fresh(null) {
        toSummary()
        assertTrue(appears("저장하고 초대한 분께 알리기", exact = true))
        assertTrue(appears("알리지 않고 저장", exact = true))
        assertTrue(appears("FRND-2345"))
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
        // 2.0.0 [RVP-43] HQ-49: in the 알림함, whose row says it was told (not the question again).
        openNotice("친구 초대")
        reveal("초대한 분께 알렸어요")
        assertTrue(gone("친구 초대로 오셨나요?"))
        assertTrue(appears("당신도 친구 5명이 각자 7일 이상 쓰면"))
        assertTrue(appears("내 초대 링크 만들기", exact = true))
    })

    @Test fun savingWithoutTellingSendsNothing() = shoot("1082_ag_setup_not_told", PHONE_393, fresh(null) {
        toSummary()
        tapText("알리지 않고 저장", exact = true)
        waitFor(400)
        assertEquals(emptyList<String>(), sent())
        assertNull(c.invites.store.current.reportedCode)
        assertTrue(c.invites.store.current.offerClosed)
        assertTrue(gone("친구 초대로 오셨나요?"))
        assertNull(c.invites.relay())
    })

    @Test fun theCalendarCardIsNeverFolded() = shoot("1083_ag_arrival_first", FOLDED, style(WorkStyle.SHIFT) {
        // A phone set up another way (no last screen): the referrer is read after the first setup. (The launch's own
        // sync read the empty test referrer already, so this phone starts again as one that came by the link.)
        c.invites.store.edit { com.bamnat.app.tier.InviteState() }
        c.referrerSource = ReferrerSource { REFERRER }
        runBlocking { c.invites.sync() }
        settle()
        // 2.0.0 [RVP-43] HQ-49: a row of the 알림함's 안내 now (only what has to be seen stands over the month); the bell's number
        // says it is there, and its row opens the question in place.
        assertTrue(appears("알림 ", exact = false))
        openNotice("친구 초대")
        reveal("친구 초대로 오셨나요?")
        assertTrue(appears("알리기", exact = true))
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
        // 2.0.0 [RVP-43] HQ-48·HQ-49·HQ-50: a 권유 row of the 알림함 (not a card over the month); a tap goes to 프로's 친구 초대.
        tapText("알림 ", prefix = true, scrolls = 0)
        assertTrue(appears(NUDGE, exact = true))
        // 기능 켜고 끄기 still turns it off, and on again the same day it is back.
        val f = FeatureRegistry.get("invite.nudge")!!
        val guide = com.bamnat.app.data.GuideStore.get(app)
        guide.setOn(f, GuideState.ALL, false)
        assertTrue("off in 기능 켜고 끄기", gone(NUDGE, exact = true))
        guide.setOn(f, GuideState.ALL, true)
        assertTrue(appears(NUDGE, exact = true))
        assertNull("only a tap on it counts as seen", c.grow.current.inviteShownOn)
    })

    /** Its X rests it [com.bamnat.core.guide.GrowNudges.INVITE_EVERY_DAYS] days, as the card's 닫기 did (not back tomorrow). */
    @Test fun theInviteRowRestsWhenClosed() = shoot("1084b_ag_invite_rests", FOLDED, style(WorkStyle.SHIFT) {
        c.invites.installedAt = { Today.instant().minus(Duration.ofDays(4)) }
        api.viewAnswer = view(progress = 3, waiting = 1, friends = 3)
        runBlocking { c.invites.makeLink() }
        tab(1, wide = false); tab(0, wide = false)
        settle()
        tapText("알림 ", prefix = true, scrolls = 0)
        tapText("닫기: $NUDGE", exact = true, scrolls = 0)
        assertTrue(gone(NUDGE, exact = true))
        assertEquals(Today.date(), c.grow.current.inviteClosedOn)
    })

    @Test fun settingsStartsWithGrowingTogether() = shoot("1085_ag_settings_top", FOLDED, style(WorkStyle.SHIFT) {
        tab(SETTINGS_TAB, wide = false)
        reveal("함께 만들어요", exact = true)
        assertTrue(appears("친구 5명이 각자 7일 이상 쓰면 나에게 프로 1개월, 5명 더 채울 때마다 또 1개월(상한 없음)."))
        assertTrue(appears("불편한 점 알려 주기"))
        assertTrue(appears("개발자가 직접 읽고 답을 드리고"))
        // [J133-77] 1.33: while this card says 친구 초대, the 더 보기 row does not say it again.
        reveal("더 보기", exact = true)
        assertTrue(gone("받은 코드 넣기"))
        scrollDown(2000)
        reveal("함께 만들어요", exact = true)
        noCuts("settings_top")
    })

    @Test fun feedbackOpensTheInquiryForm() = shoot("1093_ag_feedback_form", FOLDED, style(WorkStyle.SHIFT) {
        tab(SETTINGS_TAB, wide = false)
        reveal("함께 만들어요", exact = true)
        tapText("알려 주기", exact = true)
        // The 문의 opens on its form, from here.
        reveal("문의")
        assertNull("the inquiry screen took the start", com.bamnat.app.ui.settings.InquiryLinks.pending.value)
    })

    @Test fun closingTheSettingsCardWaitsAMonth() = shoot("1092_ag_settings_closed", FOLDED, style(WorkStyle.SHIFT) {
        tab(SETTINGS_TAB, wide = false)
        reveal("함께 만들어요", exact = true)
        tapText("닫기", exact = true)
        assertTrue(gone("함께 만들어요", exact = true))
        assertEquals(Today.date(), c.grow.current.cardClosedOn)
    })

    // ---- 434 [MON-12] the 프로 sheet, in the alarm check's 등급 미리 보기 (before the launch nothing is locked) ----
    private fun lockedPay(): Screen.() -> Unit = payScreen(Tier.FREE, withProfile = true) { vm ->
        // [MON-13] 1.36: its free week used up long ago, so the tap asks for 프로.
        androidx.compose.runtime.remember { app.container.featureGate.startTrial(com.bamnat.app.tier.Feature.PAYSLIP_MATCH, 0L) }
        PayScreen(vm, onBack = {})
        com.bamnat.app.ui.pro.ProOfferHost(app.container)
    }

    /** [MON-13] 1.36 (사용자 결정): the first tap on 금액 비교하기 opens it for a week instead of asking for 프로. */
    @Test fun theFirstUseOpensAWeek() = shoot("1086b_ag_trial_first_use", PHONE_393) {
        payScreen(Tier.FREE, withProfile = true) { vm -> PayScreen(vm, onBack = {}); com.bamnat.app.ui.pro.ProOfferHost(app.container) }.invoke(this)
        openPayGuides()
        reveal("처음 쓰는 날부터 일주일은 무료예요")
        tapText("금액 비교하기", scrolls = 30)
        reveal("어느 달 명세서인가요?")
    }

    @Test fun aLockedFeatureOffersThreeWays() = shoot("1086_ag_pro_sheet", PHONE_393) {
        lockedPay().invoke(this)
        openPayGuides()
        tapText("금액 비교하기", scrolls = 30)
        reveal("‘명세서 대조’ · 프로 기능이에요")
        assertTrue(appears("광고 보고 급여 7일 열기", exact = true))
        assertTrue(appears("미리 보기에서는 광고가 나오지 않아요"))
        // 2.0.0 [RVO-13] (HQ-48) the same name and one-line promise as on 프로.
        reveal(com.bamnat.app.ui.pro.INVITE_FREE_TITLE, exact = true)
        assertTrue(appears("친구 5명이 7일 쓰면 프로 1개월"))
        assertTrue(app.container.featureGate.state.value.preview)
        noCuts("sheet")
    }

    @Test fun theSheetMakesTheLinkAfterTheLine() = shoot("1087_ag_pro_sheet_invite", PHONE_393) {
        api.viewAnswer = view()
        lockedPay().invoke(this)
        openPayGuides()
        tapText("금액 비교하기", scrolls = 30)
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
        val gate = app.container.featureGate.state.value
        content {
            androidx.compose.foundation.layout.Column {
                com.bamnat.app.ui.pro.ProLock(com.bamnat.app.tier.Feature.WORK_COMPARE, gate) {
                    androidx.compose.material3.Text("이달과 지난달 비교")
                }
            }
            com.bamnat.app.ui.pro.ProOfferHost(app.container)
        }
        settle()
        reveal(com.bamnat.app.ui.pro.INVITE_FREE_TITLE, exact = true)
        tapText(com.bamnat.app.ui.pro.INVITE_FREE_TITLE, exact = true)
        reveal("‘근무·급여 비교’ · 프로 기능이에요")
        assertTrue(appears("미리 보기에서는 광고가 나오지 않아요"))
    }

    // ---- 435 [INVITE-12] ----
    @Test fun afterAnAppStopOneAsk() = shoot("1089_ag_crash_ask", FOLDED, style(WorkStyle.SHIFT) {
        com.bamnat.app.diag.CrashLog.write(app, "main", IllegalStateException(), Today.millis() - 60_000)
        // The calendar reads the note again as it opens.
        tab(1, wide = false); tab(0, wide = false)
        // 2.0.0 [RVP-43] HQ-49: a row of the 알림함, opened in place there.
        openNotice("불편한 점 알려 주기")
        reveal("앱이 갑자기 닫혔어요")
        tapText("멈춤 기록과 함께 알려 주기")
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
        tab(SETTINGS_TAB, wide = false)
        tapText("프로", exact = true)
        tapText("친구 초대", exact = true, scrolls = 30)
        reveal("3/5명 · 2명은 사용 기간 채우는 중", exact = true)
        assertTrue(appears("다음 프로까지 2명", exact = true))
        assertTrue(appears("5명 더 채울 때마다 또 1개월"))
        assertTrue(appears("같은 친구·같은 휴대폰은 한 번만"))
        assertTrue(gone("넘게 쓸 때마다"))
    })

    @Test fun theSheetAtLargeText() = shoot("1091_ag_pro_sheet_200", PHONE_393, fontScale = 2f) {
        lockedPay().invoke(this)
        openPayGuides()
        tapText("금액 비교하기", scrolls = 30)
        reveal(com.bamnat.app.ui.pro.INVITE_FREE_TITLE, exact = true)
        noCuts("sheet_200")
    }

    // ---- 840 세 장 페이월: 내 숫자 → 프로가 더해 주는 것 → 요금제, 닫기와 7일간 보지 않기는 장마다 ----
    @Test fun thePaywallHasThreePages() = shoot("1093_ag_paywall_pages", PHONE_393) {
        app.container.proMoments.found(java.time.YearMonth.from(Today.date()), 68_400)
        content { com.bamnat.app.ui.pro.ProPaywallContent(app.container, onClose = {}) }
        reveal("밤낮이 이번 달 드린 것")
        assertTrue(appears("올해 밤낮이 찾아 준 돈 68,400원"))
        assertTrue(appears("7일간 보지 않기", exact = true))
        tapText("다음", exact = true)
        reveal("프로가 더해 주는 것")
        tapText("다음", exact = true)
        reveal("요금제", exact = true)
        assertTrue(appears("7일간 보지 않기", exact = true))
        noCuts("paywall")
    }
}
