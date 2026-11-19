package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
import org.robolectric.RuntimeEnvironment
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
        app.container.settingsRepository.update { it.copy(previewTier = null, phoneCalendars = emptyList()) }
        com.bamnat.app.ui.settings.RecentSettings.clear()
        released(false)
        app.getSharedPreferences("forget_all", android.content.Context.MODE_PRIVATE).edit().clear().commit()
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
        // 2.0.0 [RVO-05] 요금제 카드 끝. [RVO-27] HQ-109: 약관·처리방침·사업자 정보는 조용한 링크 한 줄로.
        reveal("사업자 정보", exact = true)
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

    // RVO-14 (총괄 11-12): 프로가 아닌 폰에 휴대폰 캘린더를 골라 둔 채(프로가 끝난 뒤) 설정 › 기록과 백업의 그 줄.
    @Test fun phoneCalendarPaused() = all("11_phone_calendar_paused") {
        app.container.settingsRepository.update { it.copy(phoneCalendars = listOf(PersonalCalendar.MAIN), previewTier = Tier.FREE) }
        app.container.featureGate.refresh()
        open(FeatureRoute(Place.SETTINGS_DATA, "휴대폰 캘린더에 근무 쓰기"))
    }

    // RVO-16 (검토 SET-09): 설정 › 개인정보 맨 아래 따로 둔 '지우기', 지난번 요청이 인터넷이 없어 못 지운 폰.
    @Test fun forgetRowApart() = all("12_privacy_forget_row") {
        com.bamnat.app.ui.company.ForgetLog.record(app, java.time.ZonedDateTime.of(2026, 11, 12, 21, 30, 0, 0,
            java.time.ZoneId.of("Asia/Seoul")).toInstant(), com.bamnat.app.ui.company.ForgetLog.OFFLINE)
        open(FeatureRoute(Place.SETTINGS_PRIVACY, com.bamnat.app.ui.settings.FORGET_ALL_ROW))
    }

    // RVO-17 (검토 SET-11·U11a-07·I11aa-05): 백업 암호 창, 짧은 암호에 '짧아요' 한 줄, 보기 단추, 단추는 세로, '암호 없이 저장'은 작은 줄.
    @Test fun backupPassword() = all("13_backup_password") {
        content { com.bamnat.app.ui.settings.ExportPasswordDialog(onDismiss = {}, onExport = {}) }
        typeInto("암호", "night5")
    }

    // RVO-17 (검토 SET-11·U11a-09): 파일 불러오기의 최근 백업, 지워진 파일은 흐리게 '지워졌거나 옮겨졌어요'와 '목록에서 빼기'.
    @Test fun recentBackupsGone() = all("14_recent_backups_gone") {
        val at = java.time.LocalDateTime.of(2026, 11, 10, 21, 5)
        content {
            com.bamnat.app.ui.settings.RecentBackupsDialog(listOf(
                com.bamnat.app.ui.settings.BackupPlace.Recent(android.net.Uri.parse("content://com.example.gone/1"), "밤낮-백업-2026-11-10.bamnat", at),
                com.bamnat.app.ui.settings.BackupPlace.Recent(android.net.Uri.parse("content://com.example.gone/2"), "밤낮-백업-2026-11-03.bamnat", at.minusDays(7)),
            ), onPick = {}, onOther = {}, onDismiss = {})
        }
    }

    // ---- 3·4차 PR ----

    // RVO-21 (SET-08·U11a-18·19): 설정 › 화면과 글자, 맨 위 묶음 칩과 네 접힌 묶음(한 줄 요약).
    @Test fun displayGroups() = all("15_display_groups") {
        open(FeatureRoute(Place.SETTINGS_CALENDAR))
    }

    // RVO-21 (I11aa-03): 설정 첫 화면 '방금 바꾼 것' 세 줄과 되돌리기(설정 화면의 저장 한 곳이 적는 것과 같게).
    @Test fun recentChanges() = all("16_recent_changes") {
        val store = app.container.settingsRepository
        com.bamnat.app.ui.settings.RecentSettings.clear()
        fun change(f: (com.bamnat.app.data.AppSettings) -> com.bamnat.app.data.AppSettings) =
            store.update { s -> f(s).also { com.bamnat.app.ui.settings.RecentSettings.note(store, s, it) } }
        change { it.copy(compact = !it.compact) }
        change { it.copy(themeMode = com.bamnat.app.data.ThemeMode.DARK) }
        change { it.copy(textStep = it.textStep + 1) }
        com.bamnat.app.ui.settings.SettingsLinks.home()
        settle()
        reveal("방금 바꾼 것", exact = true, across = 0.2f)
    }

    // RVO-22 (SET-04·U11a-05·06): 설정 › 알림과 잠, 펼친 '알림 한눈에'와 '잠' 묶음, 아래 '자세히'.
    @Test fun noticesBoard() = all("17_notices_board") {
        open(FeatureRoute(Place.SETTINGS_NOTICES))
    }

    // RVO-25 (PC-146): 프로 맨 아래 받은 코드 칸에 8자리를 넣은 모습(고르기·넣기가 칸 바로 아래).
    @Test fun codeTyped() = all("18_code_typed") {
        app.container.tierApi = com.bamnat.app.tier.FakeTierApi(configAnswer = null)
        app.container.inviteApi = com.bamnat.app.tier.FakeInviteApi()
        launched().invoke(this)
        open(FeatureRoute(Place.PRO))
        repeat(16) { scrollUp(500, across = 0.5f) }
        if (appears("코드를 받으셨나요?", exact = true)) tapText("코드를 받으셨나요?", exact = true)
        typeInto("받은 코드", "ABCD2345")
        reveal("넣기", exact = true)
    }

    // RVO-25 (PC-186): 프로가 아닌 폰의 가계부 › 통계. 잠금 카드의 채운 단추와 바로 아래 친구 초대가 처음 화면에서 아래 막대 위에 있어요
    // (글자 200%는 그 카드까지 내렸을 때 카드 전체가 한 화면에).
    @Test fun lockOnTheFirstScreen() {
        val missed = mutableListOf<String>()
        all("19_lock_stats") {
            invites()
            // Robolectric draws no blur: the cover Android 11 and older get, as 379_folded_money_stats_locked.
            org.robolectric.util.ReflectionHelpers.setStaticField(android.os.Build.VERSION::class.java, "SDK_INT", 30)
            launched().invoke(this)
            open(FeatureRoute(Place.MONEY, "report"))
            val look = "${RuntimeEnvironment.getQualifiers().substringBefore("-port")} ${app.resources.configuration.fontScale}"
            val big = app.resources.configuration.fontScale > 1.5f
            // At 글자 200% the free 'N월 한눈에' card above already fills the first screen (조건 1 keeps it first): there the whole lock
            // card, lock to 친구 초대, is on one screen once it is reached.
            if (big) {
                reveal("가계부 통계", exact = true, across = 0.5f)
                repeat(12) { if (!appears(com.bamnat.app.ui.pro.INVITE_FREE_TITLE, exact = true, ms = 0)) scrollUp(120, across = 0.5f) }
            }
            if (!appears(com.bamnat.app.ui.pro.INVITE_FREE_TITLE, exact = true)) { missed += "$look: 친구 초대가 첫 화면에 없음"; return@all }
            if (!appears("가계부 통계", exact = true, ms = 0)) missed += "$look: 잠금 카드가 한 화면에 다 들어오지 않음"
            val all = nodes(merged = true)
            fun textOf(n: androidx.compose.ui.semantics.SemanticsNode) =
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { it.text }
            val invite = all.first { textOf(it) == com.bamnat.app.ui.pro.INVITE_FREE_TITLE }.boundsInWindow
            val bar = all.filter { it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Role) { null } ==
                androidx.compose.ui.semantics.Role.Tab }.map { it.boundsInWindow }
            // The phone's bar is along the bottom, its tabs in one row (the open fold's is a rail on the side).
            val tops = bar.map { it.top }.distinct()
            if (tops.size == 1 && invite.bottom > tops[0]) missed += "$look: 친구 초대 $invite, 막대 ${tops[0]}"
        }
        org.junit.Assert.assertEquals(emptyList<String>(), missed)
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

    // ---- 5차: HQ-109 결제 아래 줄 ----

    /**
     * 2.0.0 [RVO-27] HQ-109 (조건 2·9): under the plans of the 프로 화면 and on the 요금제 창, 구매 복원 and 결제·환불 문의 are the only word
     * buttons; 이용약관·개인정보처리방침·사업자 정보 are one quiet line whose words are each at least 48dp tall to tap.
     */
    @Test fun payLinksQuietLine() {
        val missed = mutableListOf<String>()
        fun Screen.check(where: String) {
            val look = "${RuntimeEnvironment.getQualifiers().substringBefore("-port")} ${app.resources.configuration.fontScale} $where"
            val all = nodes(merged = true)
            fun textOf(n: androidx.compose.ui.semantics.SemanticsNode) =
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.joinToString { it.text }
            val min = 48 * app.resources.displayMetrics.density - 1
            for (word in com.bamnat.app.ui.pro.POLICY_LINK_WORDS) {
                val n = all.firstOrNull { textOf(it) == word }
                if (n == null) { missed += "$look: '$word' 없음"; continue }
                if (n.boundsInWindow.height < min) missed += "$look: '$word' 높이 ${n.boundsInWindow.height}"
            }
            if (all.any { textOf(it)?.trim() == "·" }) missed += "$look: 사이 점이 따로 읽혀요"
        }
        all("20_pay_links_pro") {
            app.container.settingsRepository.update { it.copy(previewTier = Tier.FREE) }
            app.container.featureGate.refresh()
            open(FeatureRoute(Place.PRO))
            reveal("사업자 정보", exact = true)
            if (!appears(com.bamnat.app.ui.pro.PAY_ASK, exact = true)) missed += "프로 화면: 결제·환불 문의 단추가 없음"
            check("프로 화면")
        }
        all("21_pay_links_paywall") {
            invites()
            launched().invoke(this)
            content { com.bamnat.app.ui.pro.ProPaywallContent(app.container, onClose = {}) }
            // The plans are the last page: '다음' until it is gone.
            repeat(3) { settle(); if (shows("다음", exact = true)) tapText("다음", exact = true) }
            settle()
            reveal("사업자 정보", exact = true)
            check("요금제 창")
        }
        org.junit.Assert.assertEquals(emptyList<String>(), missed)
    }

    /**
     * 2.0.0 [RVO-28] HQ-150 (PC-201): the 운영팀 asked for the record, so the reply opens with '점검 기록도 보내기' on. A report pasted as
     * words says it need not be (no red 2,000자 line), and one press takes it out and keeps the switch on; the record then goes as the
     * reply's info.diag and the bubble says it went with it.
     */
    @Test fun replyWithTheRecord() {
        val missed = mutableListOf<String>()
        val sent = mutableListOf<String?>()
        val ask = "원인을 찾으려면 휴대폰에 남은 '앱 멈춤' 기록이 꼭 필요해요. 이 문의에 답장 쓰기에서 아래 '점검 기록도 보내기'를 켜고 보내 주세요.\n\n밤낮 운영팀"
        val report = "[앱 멈춤]\n가장 최근 기록: 11.19 07:02\n남은 기록: 3개\n\n[알람 상태]\n알람 사용: 켜짐\n" + "놓쳤을 수 있는 알람: 없음\n".repeat(120)
        fun fake(): com.bamnat.app.company.InquiryApi {
            val base = FakeInquiryApi()
            return object : com.bamnat.app.company.InquiryApi by base {
                var list: List<com.bamnat.app.company.Inquiry>? = null
                override suspend fun mine(device: String) = list ?: base.mine(device).map { q ->
                    if (q.id != "q2") q else q.copy(messages = q.messages + com.bamnat.app.company.InquiryMessage(true, ask, q.updatedAt))
                }.also { list = it }
                override suspend fun more(device: String, id: String, text: String, requestId: String, diag: String?): com.bamnat.app.company.Inquiry {
                    sent += diag
                    val q = mine(device).first { it.id == id }
                    val next = q.copy(status = "open", messages = q.messages + com.bamnat.app.company.InquiryMessage(false, text,
                        com.bamnat.core.time.Today.instant(), withCheck = diag != null))
                    list = list!!.map { if (it.id == id) next else it }
                    return next
                }
            }
        }
        fun Screen.checkOn(): Boolean = nodes(merged = true).any { n ->
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }?.any { it.text == "점검 기록도 보내기" } == true &&
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.ToggleableState) { null } == androidx.compose.ui.state.ToggleableState.On
        }
        fun Screen.open() {
            app.container.inquiryApi = fake()
            app.container.inquiryStore.asked = true
            // The reply is kept as it is written (조건 10); each look starts from an empty one.
            app.container.inquiryStore.saveReply("q2", "")
            inquiryThread()
            settle()
        }
        all("22_reply_pasted_record") {
            open()
            val look = "${RuntimeEnvironment.getQualifiers().substringBefore("-port")} ${app.resources.configuration.fontScale}"
            if (!checkOn()) missed += "$look: 기록을 부탁한 답인데 '점검 기록도 보내기'가 꺼져 있음"
            typeInto("점검 기록에 덧붙일 말(적지 않아도 돼요)", "말씀하신 기록이에요\n\n$report")
            if (!appears(com.bamnat.app.ui.settings.PASTED_LINE, exact = true)) missed += "$look: 붙여 넣은 기록에 다음 할 일이 없음"
            if (shows("${com.bamnat.app.company.InquiryClient.TEXT_MAX}자까지 쓸 수 있어요", exact = true)) missed += "$look: 2,000자 말이 그대로"
        }
        all("23_reply_record_sent") {
            open()
            typeInto("점검 기록에 덧붙일 말(적지 않아도 돼요)", "말씀하신 기록이에요\n\n$report")
            tapText(com.bamnat.app.ui.settings.PASTED_TAKE, exact = true, scrolls = 0)
            if (!checkOn()) missed += "빼고 켜기 뒤 스위치가 꺼짐"
            if (shows(com.bamnat.app.ui.settings.PASTED_LINE, exact = true)) missed += "빼고 켜기 뒤에도 붙여 넣은 기록 말이 남음"
            tapText("보내기", exact = true, scrolls = 0)
            appears("점검 기록 함께 보냄", exact = true)
            settle()
            reveal("점검 기록 함께 보냄", exact = true)
        }
        if (sent.size != looks.size || sent.any { it.isNullOrBlank() }) missed += "답장과 함께 간 기록: $sent"
        org.junit.Assert.assertEquals(emptyList<String>(), missed)
    }

    // ---- 7차: PC-204 결제·환불 안내, HQ-154 단계 띠, HQ-152 홀로 남는 점 ----

    /**
     * 2.0.0 [RVO-29] PC-204 (조건 4·13): 결제·환불 문의의 보내기 위 안내는 그 종류의 말로 시작하고, 예시대로 Play 주문 번호를 적어도
     * '금액' 말이 나오지 않아요. 보관 글은 '보관 기간·지우기' ⓘ 뒤.
     */
    @Test fun billingNotice() {
        val missed = mutableListOf<String>()
        val lead = com.bamnat.app.ui.settings.inquiryNotice(com.bamnat.app.company.InquiryKind.BILLING, share = false).lead
        all("24_billing_notice") {
            inquiriesOn()
            // The order number as the 결제·환불 example asks, written in (InquiryStart.text fills the field as the user would).
            askFromScreen(com.bamnat.app.ui.settings.InquiryStart(about = "프로·결제", kind = com.bamnat.app.company.InquiryKind.BILLING,
                text = "어제 프로 1년을 결제했는데 아직 안 열려요. Play 주문 번호는 GPA.3312-4459-2207-48812예요"))
            reveal("보관 기간·지우기", exact = true)
            val look = "${RuntimeEnvironment.getQualifiers().substringBefore("-port")} ${app.resources.configuration.fontScale}"
            if (!shows(lead, exact = true)) missed += "$look: 결제·환불 안내 첫 문장이 없음"
            if (shows("회사 방식은", exact = false)) missed += "$look: 결제 문의에 회사 방식 글"
            if (shows("금액은 적지 말아 주세요", exact = false)) missed += "$look: 주문 번호가 금액으로 막힘"
        }
        org.junit.Assert.assertEquals(emptyList<String>(), missed)
    }

    /**
     * 2.0.0 [RVO-30] PC-214 (조건 7·13): a 결제·환불 question is kept for the 운영팀 only whatever the switch said; the 내 문의 row says
     * "나만 보기" and the thread opens with the line that says why nobody else sees it (and who answers).
     */
    @Test fun billingPrivateLine() {
        val missed = mutableListOf<String>()
        all("27_billing_private") {
            inquiriesOn(asked = true)
            (app.container.inquiryApi as FakeInquiryApi).addBilling()
            askFromScreen(com.bamnat.app.ui.settings.InquiryStart(help = true))
            tapText("내 문의:", prefix = true, scrolls = 30)
            val look = "${RuntimeEnvironment.getQualifiers().substringBefore("-port")} ${app.resources.configuration.fontScale}"
            if (!shows("결제·환불 · 나만 보기", exact = false)) missed += "$look: 내 문의 줄에 '나만 보기'가 없음"
            tapText("어제 프로 1년을", prefix = true)
            if (!shows("나만 보기 · 운영팀이 직접 읽고 답해요", exact = true)) missed += "$look: 문의 안에 나만 보기 한 줄이 없음"
            if (shows("모두의 문의에 올라가 있어요", exact = false)) missed += "$look: 나만 보기 문의에 '올라가 있어요'"
        }
        org.junit.Assert.assertEquals(emptyList<String>(), missed)
    }

    /**
     * 2.0.0 [RVO-29] HQ-154 (조건 12): 내 문의의 단계 띠. 이름이 칸에 맞으면 여섯 이름, 맞지 않으면(접은 폭 200%) 띠 아래 '답변 · 3/6' 한 줄.
     * 어느 쪽이든 말줄임표(…)로 잘린 이름이 없어요.
     */
    @Test fun stepsNeverCut() {
        val missed = mutableListOf<String>()
        all("25_steps") {
            inquiriesOn(asked = true)
            inquiryThread()
            val look = "${RuntimeEnvironment.getQualifiers().substringBefore("-port")} ${app.resources.configuration.fontScale}"
            val texts = nodes(merged = false).flatMap { n ->
                n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty().map { it.text }
            }
            val names = listOf("접수", "확인 중", "답변", "개발 목록", "만드는 중", "반영")
            if (!(names.all { it in texts } || "답변 · 3/6" in texts)) missed += "$look: 단계 이름도 '답변 · 3/6'도 없음 $texts"
            if (texts.any { it.endsWith("…") && names.any { n -> n.startsWith(it.dropLast(1)) } }) missed += "$look: 잘린 이름"
        }
        org.junit.Assert.assertEquals(emptyList<String>(), missed)
    }

    /**
     * 2.0.0 [RVO-29] HQ-152 (조건 12): the lines joined by dots (친구 초대 진행, 결제 고지, 요금제 창의 가격·할인, 약관 링크) side by side:
     * at 100% on the open width each is one line with its dots; where it does not fit, one part a line and no dot left alone.
     */
    @Test fun noDotLeftAlone() = all("26_dot_lines") {
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                val plans = samplePlans
                com.bamnat.app.ui.components.DotText(com.bamnat.core.tier.InviteRules.next(3, 2, com.bamnat.core.tier.InviteTerms.DEFAULT),
                    androidx.compose.material3.MaterialTheme.typography.titleMedium)
                com.bamnat.app.ui.components.DotText(com.bamnat.app.ui.pro.priceLine(plans)!!, androidx.compose.material3.MaterialTheme.typography.labelLarge,
                    androidx.compose.material3.MaterialTheme.colorScheme.primary)
                com.bamnat.app.ui.components.DotText(com.bamnat.app.ui.pro.trialHead(com.bamnat.core.time.Today.date(), "연 39,000원"),
                    androidx.compose.material3.MaterialTheme.typography.labelLarge, androidx.compose.material3.MaterialTheme.colorScheme.primary)
                com.bamnat.app.ui.pro.PayNotice(plans, null)
                com.bamnat.app.ui.pro.PolicyLinks()
            }
        }
        settle()
        val texts = nodes(merged = false).flatMap { n ->
            n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.Text) { null }.orEmpty().map { it.text }
        }
        org.junit.Assert.assertFalse("홀로 남은 점: $texts", texts.any { it.trim() == "·" || it.trimEnd().endsWith(" ·") || it.trimStart().startsWith("· ") })
    }
}
