package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.tier.Tier
import com.bamnat.app.ui.guide.FeatureLinks
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Place
import com.bamnat.core.model.AlarmSpec
import com.bamnat.core.model.ShiftType
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalTime

/**
 * 2.0.0 검토 갈래 O의 앞·뒤 화면 사진(디자인 품질 조건 확인). 같은 시험을 바탕 커밋과 고친 커밋에서 돌려 나란히 둬요.
 * 네 가지 모양(접은 폭 밝은 100%, 접은 폭 어두운 200%, 편 폭 밝은 200%, 편 폭 어두운 100%).
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class ReviewOShotsTest : ScreenshotBase() {
    private val looks = listOf(
        Triple("a_folded_light_100", FOLDED, 1f), Triple("b_folded_dark_200", FOLDED.replace("-port-", "-port-night-"), 2f),
        Triple("c_open_light_200", UNFOLDED, 2f), Triple("d_open_dark_100", UNFOLDED.replace("-port-", "-port-night-"), 1f),
    )

    private fun all(name: String, go: Screen.() -> Unit) {
        for ((look, q, scale) in looks) shoot("rvo_${name}_$look", q, scale, go)
    }

    @After fun back() {
        app.container.calendars.setActive(PersonalCalendar.MAIN)
        app.container.settingsRepository.update { it.copy(previewTier = null) }
        released(false)
        com.bamnat.core.time.Today.fix(SHOT_TIME)
    }

    /**
     * 2.0.0 [RVO-12] The release build's own facts (배포판), set by name so the same test runs on the commit before it (where it does
     * nothing and the phone looks as it did then).
     */
    private fun released(on: Boolean) {
        runCatching {
            val k = Class.forName("com.bamnat.app.tier.ReleaseBuild")
            k.getMethod("setOn", Boolean::class.javaPrimitiveType).invoke(k.getField("INSTANCE").get(null), on)
        }
    }

    /** 친구 초대가 켜진 서버(가짜): 받은 프로 1개월, 사용 기간을 채우는 친구 2명. */
    private fun invites() {
        app.container.tierApi = com.bamnat.app.tier.FakeTierApi(configAnswer = null)
        app.container.inviteApi = com.bamnat.app.tier.FakeInviteApi(viewAnswer = com.bamnat.app.tier.InviteView(
            code = "ABCD2345", link = "https://company.example/i/ABCD2345", terms = com.bamnat.core.tier.InviteTerms.DEFAULT, progress = 3,
            waiting = 2, friends = 6, given = 1, capReached = false, used = null, checkedAt = java.time.Instant.parse("2026-10-25T00:00:00Z")))
    }

    private fun open(route: FeatureRoute) {
        FeatureLinks.open(route)
        settle()
    }

    /** A second calendar (알바) with its own shift that rings, shown on the calendar tab. */
    private fun secondCalendar() {
        val c = app.container
        val id = c.calendars.calendars.value.firstOrNull { !it.isMain }?.id ?: c.calendars.begin("알바 달력").also {
            c.calendars.finishSetup(it, WorkStyle.HOURLY)
            runBlocking {
                c.calendars.repository(it).saveShiftType(ShiftType(0, "편의점", "편", 0xFF1E88E5, LocalTime.of(18, 0), LocalTime.of(23, 0),
                    alarmEnabled = true, alarms = listOf(AlarmSpec(60))))
            }
        }
        c.calendars.setActive(id)
    }

    // PC-14 · U11a-01 · SET-05: 달력이 둘일 때 설정 › 알람.
    @Test fun alarmPageWithTwoCalendars() = all("1_alarm_two_calendars") {
        secondCalendar()
        open(FeatureRoute(Place.SETTINGS_ALARM))
    }

    // HQ-01: 편 폭·큰 글자에서 설정 왼쪽 목록의 알람 카드.
    @Test fun settingsList() = all("2_settings_list") {
        app.container.settingsRepository.update { it.copy(alarmsEnabled = true) }
        open(FeatureRoute(Place.SETTINGS_WORK))
    }

    // PC-54: 프로 화면 맨 위의 등급 미리 보기, 그리고 '무료'를 고른 모습(요금제 창까지).
    @Test fun proPreviewFree() = all("3_pro_preview_free") {
        app.container.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
        app.container.featureGate.refresh()
        open(FeatureRoute(Place.PRO))
    }

    @Test fun proPreviewFreePlans() = all("4_pro_preview_plans") {
        app.container.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
        app.container.featureGate.refresh()
        open(FeatureRoute(Place.PRO))
        // 2.0.0 [RVO-05] 요금제 카드 끝(약관·사업자 정보는 한 단추의 메뉴로).
        reveal(com.bamnat.app.ui.pro.PAY_PAPERS, exact = true)
    }

    // ---- 2차 PR: 프로 화면 한 장(U11b-01·02·03, PRO-12·30), 페이월 가격 한 줄(U11b-04). 같은 시험을 바탕과 고친 커밋에서 돌려요. ----

    // U11b-01: 정식 출시 뒤 무료인 교대 근무자가 프로 화면을 열면 맨 위.
    // HQ-48: 친구 초대가 켜진 폰이라 '지금' 바로 아래 초대 카드(바탕은 아래쪽에 있어 맨 위에 안 보임).
    @Test fun proTopShift() = all("5_pro_top_shift") {
        app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        invites()
        launched().invoke(this)
        open(FeatureRoute(Place.PRO))
    }

    // PRO-30·T11b-02: 가게 사장님이 아래로 내려 본 모습(기능 목록과 무료인 것).
    @Test fun proMiddleBusiness() = all("6_pro_middle_business") {
        app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.BUSINESS) }
        launched().invoke(this)
        open(FeatureRoute(Place.PRO))
        // 내용을 위로 900dp 밀어 가운데를 봐요(scrollUp은 내용을 위로 올려요).
        repeat(2) { scrollUp(450, across = 0.5f) }
    }

    // U11b-03·SET-10: 프로 화면 맨 아래(관리자 연결 → 코드를 받으셨나요?).
    @Test fun proBottom() = all("7_pro_bottom") {
        // 초대·이용권·코드 칸은 서버가 켜져 있을 때만 보여요: 가짜 서버로 켜요(설정은 서버에서 받지 않음).
        app.container.tierApi = com.bamnat.app.tier.FakeTierApi(configAnswer = null)
        app.container.inviteApi = com.bamnat.app.tier.FakeInviteApi()
        launched().invoke(this)
        open(FeatureRoute(Place.PRO))
        repeat(16) { scrollUp(500, across = 0.5f) }
        // 2차 PR: 맨 아래 '코드를 받으셨나요?'를 펴서 칸 하나를 봐요(바탕에는 없음).
        if (appears("코드를 받으셨나요?", exact = true)) {
            tapText("코드를 받으셨나요?", exact = true)
            repeat(4) { scrollUp(500, across = 0.5f) }
        }
    }

    // U11b-04·T11b-01: 페이월의 '프로가 더해 주는 것' 장(찾아 준 돈이 없으면 첫 장), 아래에 가격 한 줄. 투자의 다섯 줄.
    @Test fun paywallPitchPage() = all("8_paywall_pitch") {
        app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.INVEST) }
        // HQ-48: 장마다 '친구를 초대하면 무료로 써요' 한 줄.
        invites()
        launched().invoke(this)
        content { com.bamnat.app.ui.pro.ProPaywallContent(app.container, onClose = {}) }
    }

    // ---- 2차 PR 덧: HQ-48 잠긴 기능 시트, HQ-46 3.1 보류 폰 ----

    // HQ-48: 프로가 아닌 일근 직장인이 잠긴 '명세서 금액 비교'를 누르면 뜨는 시트(초대 길이 같은 이름).
    @Test fun lockedSheet() = all("9_locked_sheet") {
        app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.DAY) }
        invites()
        launched().invoke(this)
        val gate = app.container.featureGate.state.value
        content { com.bamnat.app.ui.pro.ProOfferContent(app.container, com.bamnat.app.tier.Feature.PAYSLIP_MATCH, gate, onPro = {}) }
    }

    // HQ-46: 배포판, 11-13, 서버 답 전, 10-27 전에 깔고 3.1에 동의하지 않은 폰(바탕은 '정식 출시 전이라 모든 기능이 무료예요').
    // HQ-48 (총괄 11-12 23:37 KST): 그 폰도 구독한 프로가 아니라 '지금' 바로 아래가 친구 초대(초대 서버는 가짜로 켬).
    @Test fun heldPhone() = all("10_pro_held") {
        invites()
        com.bamnat.core.time.Today.fix(java.time.ZonedDateTime.of(2026, 11, 13, 10, 30, 0, 0, java.time.ZoneId.of("Asia/Seoul")))
        released(true)
        app.container.remoteConfig.save(com.bamnat.app.tier.LaunchConfig())
        app.container.flags.refresh()
        app.container.featureGate.refresh()
        open(FeatureRoute(Place.PRO))
    }
}
