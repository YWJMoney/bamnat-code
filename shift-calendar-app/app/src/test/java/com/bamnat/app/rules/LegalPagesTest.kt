package com.bamnat.app.rules

import com.bamnat.app.ui.pro.PRIVACY_URL
import com.bamnat.app.ui.pro.TERMS_URL
import com.bamnat.core.tier.AdRules
import com.bamnat.core.tier.TrialConsent
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * [MON-05][MON-11][OPS-13][OPS-15] The published pages (repo root privacy/ and terms/) say what 1.19.0 does
 * about 프로, the trial's consent and ads, in the app's own numbers, and the app links to them.
 */
class LegalPagesTest {
    // Gradle runs unit tests in the module directory; the site is the repository root.
    private val site = File("../..")

    @Test fun thePrivacyPolicyCoversProAndAds() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        assertTrue(text.contains("2.5에서 바뀐 점: 앱 1.19.0부터"))
        listOf("1-5. 정식 출시 확인", "1-6. 프로 구독과 무료 체험 동의", "1-7. 보상 이용권, 복구 코드, 관리자 휴대폰 연결", "1-8. 광고").forEach {
            assertTrue(it, text.contains(it))
        }
        assertTrue("구매 확인 토큰과 보관", text.contains("구매 확인 토큰") && text.contains("구독이 끝난 날부터 30일 뒤 삭제"))
        assertTrue("동의 기록 5년", text.contains("답한 날부터 5년"))
        assertTrue("광고 ID 권한", text.contains("<td>광고 ID</td>"))
        assertTrue("광고 개인정보 선택", text.contains("광고 개인정보 선택"))
        assertTrue("체험 질문은 ${TrialConsent.ASK_BEFORE.toDays()}일 전", text.contains("체험이 끝나기 ${TrialConsent.ASK_BEFORE.toDays()}일 전부터"))
        assertTrue("설치 뒤 ${AdRules.QUIET_DAYS}일 광고 없음", text.contains("설치 뒤 ${AdRules.QUIET_DAYS}일 동안은 광고가 없습니다"))
        assertTrue(PRIVACY_URL.endsWith("/privacy/"))
    }

    /** [FEAT-07][FEAT-15][PROV-03][PROV-04] 2.6 for 1.20.0: the phone calendar, the feed address, more links and the daily backup, in the app's numbers. */
    @Test fun thePrivacyPolicyCoversThePhoneCalendarAndTheFeed() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        assertTrue(text.contains("2.6에서 바뀐 점: 앱 1.20.0부터"))
        assertTrue(text.contains("2-7. 휴대폰 캘린더에 근무 쓰기"))
        assertTrue("쓰기 권한", text.contains("<td>캘린더 쓰기 (선택, 이용자가 허용)</td>"))
        assertTrue("‘${com.bamnat.app.calendar.PhoneCalendar.ACCOUNT}’ 캘린더", text.contains("‘${com.bamnat.app.calendar.PhoneCalendar.ACCOUNT}’ 캘린더"))
        // The span the phone calendar is written over, as IcsWriter.defaultRange has it.
        val (from, to) = com.bamnat.core.schedule.IcsWriter.defaultRange(java.time.LocalDate.of(2026, 10, 22))
        assertTrue(from == java.time.LocalDate.of(2026, 7, 22) && to == java.time.LocalDate.of(2027, 10, 22))
        assertTrue(text.contains("3개월 전부터 1년 뒤까지"))
        assertTrue("구독 주소", text.contains("캘린더 구독 주소") && text.contains(".ics"))
        assertTrue("시간대 이름", text.contains("시간대 이름 하나"))
        assertTrue("링크 수", text.contains("링크를 ${com.bamnat.app.share.ShareLinkKey.MAX}개까지"))
        // [PROV-03] The daily backup: the password kept nowhere, the key on the phone only, the notice's days.
        assertTrue(text.contains("매일 자동 백업 (선택, 프로)"))
        assertTrue(text.contains("암호는 저장하지 않으며"))
        assertTrue(text.contains("Android 키 저장소"))
        assertTrue(text.contains("${com.bamnat.app.backup.AutoBackup.FAIL_DAYS}일 넘게 저장하지 못하면"))
        // [PROV-06][PROV-07] The motion sensor read only while ringing, and the question after 끄기.
        assertTrue(text.contains("<td>움직임 센서 (권한 없이 쓰는 기능)</td>"))
        assertTrue(text.contains("값은 저장하거나 휴대폰 밖으로 보내지 않습니다"))
        assertTrue(text.contains("‘일어났나요?’"))
        // [PROV-04] The links the user made go in a backup file, with their key, and not in Google's.
        assertTrue(text.contains("내가 만든 근무표 링크의 주소와 끊기용 열쇠"))
        assertTrue(text.contains("받은 근무표 링크는 넣지 않습니다"))
    }

    /** [PLZ-01][PLZ-08][PLZ-14][INV-30] 2.7 for 1.21.0: the plaza's votes, names and 임금표, and trades read from 체결 알림. */
    @Test fun thePrivacyPolicyCoversThePlazaAndTradeAlerts() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        assertTrue(text.contains("처리방침 2.7 · 앱 버전 1.21.0"))
        assertTrue("광장 이름 2~12자", text.contains("광장 이름(선택, 2~12자)"))
        assertTrue("임금표만 금액", text.contains("<b>금액을 받는 유일한 곳</b>"))
        assertTrue("채택된 것은 남음", text.contains("<b>채택된 규칙은 모두의 것으로 남습니다.</b>"))
        assertTrue("찾은 거래 ${com.bamnat.core.invest.TradeAlerts.MAX}개", text.contains("최대 ${com.bamnat.core.invest.TradeAlerts.MAX}개까지만"))
        assertTrue(text.contains("알림 글 자체는 저장하지 않습니다"))
        assertTrue(text.contains("결제 알림 자동 기록과 투자 체결 알림으로 거래 찾기"))
    }

    /**
     * [STAT-02][SRV-02][SRV-06][COMM-08][COMM-09][COMM-10] 2.8 for 1.22.0: usage stats only with a yes, photos with a
     * question, notices, hiding a writer on the phone, 광장 소식, link reports, the breach procedure and the community rules,
     * in the app's and the servers' own numbers.
     */
    @Test fun thePrivacyPolicyCoversUsageStatsPhotosAndThePlazaRules() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        assertTrue(text.contains("처리방침 2.8 · 앱 버전 1.22.0"))
        assertTrue(text.contains("2.8에서 바뀐 점: 앱 1.22.0부터"))
        listOf("1-9. 사용 통계 보내기 (선택, 동의한 경우만)", "1-10. 근무표 링크 신고 (선택)").forEach { assertTrue(it, text.contains(it)) }
        // 사용 통계: the setting's name, the events the server takes, the days kept on the phone and on the server.
        assertTrue(text.contains("‘사용 통계 보내기’"))
        assertTrue("이벤트 ${com.bamnat.core.stats.UsageEvent.entries.size}가지", text.contains("${com.bamnat.core.stats.UsageEvent.entries.size}가지 이름만"))
        assertTrue("최근 ${com.bamnat.core.stats.UsageBook.RECENT_DAYS}일", text.contains("최근 ${com.bamnat.core.stats.UsageBook.RECENT_DAYS}일"))
        assertTrue("${com.bamnat.core.stats.UsageBook.MAX_DAYS}일 지나면 버림", text.contains("${com.bamnat.core.stats.UsageBook.MAX_DAYS}일이 지나도록 보내지 못한 날은 버립니다"))
        assertTrue(text.contains("400일 뒤 삭제"))
        assertTrue(text.contains("처음에는 꺼져 있습니다"))
        // 문의 사진: how many, how large, and when they go.
        assertTrue(text.contains("사진 ${com.bamnat.app.company.InquiryPhotos.MAX}장까지"))
        assertTrue(text.contains("긴 변 1600픽셀"))
        assertTrue(text.contains("문의가 닫히고 90일"))
        // 광장: the rules page, hiding on the phone only (and how many), the daily news that keeps nothing.
        assertTrue(text.contains("../community/"))
        assertTrue(text.contains("최대 ${com.bamnat.core.plaza.HideList.MAX}개") && text.contains("가린 목록을 저장하지 않습니다"))
        assertTrue(text.contains("새로 저장하지 않습니다"))
        // 유출 대응: the procedure linked, the 72 hours and the 30 days a notice stays up.
        assertTrue(text.contains("docs/incident-response.md"))
        assertTrue(text.contains("72시간") && text.contains("30일 이상 게시"))
        assertTrue(File(site, "shift-calendar-app/docs/incident-response.md").exists())
        // [REMOTE-02] The signed rate files read from GitHub Pages once a day, with nothing about the user.
        assertTrue(text.contains("계산 자료 받기(앱 1.22.0부터, 자동)") && text.contains("remote/v1/"))
        // The page before the first post says the same rules as the app and is the version the app asks for.
        val rules = File(site, "community/index.html")
        assertTrue("커뮤니티 운영 규칙 페이지가 있어요", rules.exists())
        val r = rules.readText()
        assertTrue(r.contains("운영 규칙 ${com.bamnat.core.plaza.PlazaRules.VERSION} · 앱 버전 1.22.0"))
        assertTrue(com.bamnat.core.plaza.PlazaRules.URL.endsWith("/community/"))
        assertTrue(r.contains("제44조의2 제5항") && r.contains("30일 안에") && r.contains("‘이 사람 글 안 보기’"))
        assertTrue(r.contains("최대 ${com.bamnat.core.plaza.HideList.MAX}명"))
        // [COMM-08] The terms point to the rules ahead of time (정보통신망법 제44조의2 제5항).
        assertTrue(File(site, "terms/index.html").readText().contains("이 약관의 일부인 <a href=\"../community/\">광장 운영 규칙</a>"))
    }

    /**
     * [PRIV-02][SHR-13][SHR-14][SRV-09][SRV-10][STAT-06][INQ-05][ALRM-01][ALRM-04] 2.9 for 1.23.0: 우리 조 모아 보기, the takedown reason,
     * 밤낮 소식, hiding writers in 모두의 문의, the outbox and request ids, 18 usage events, notice audiences and the battery
     * request, in the app's and the share server's own numbers.
     */
    @Test fun thePrivacyPolicyCoversGroupsTakedownReasonsAndBamnatNews() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        assertTrue(text.contains("처리방침 2.9 · 앱 버전 1.23.0 기준 · 게시일 2026년 10월 24일 · 시행일 2026년 11월 21일"))
        assertTrue(text.contains("2.9에서 바뀐 점: 앱 1.23.0부터"))
        assertTrue(text.contains("1-11. 우리 조 모아 보기 (선택, 앱 1.23.0부터)"))
        // The group: 6 letters, the share server's limit of links, gone with the longest link.
        assertTrue(text.contains("영문·숫자 6자") && com.bamnat.app.share.ShareGroup.code("abc 234") == "ABC234")
        val share = File(site, "shift-calendar-app/server/share-worker/src/index.js").readText()
        val links = Regex("groupLinks: (\\d+)").find(share)!!.groupValues[1]
        assertTrue("링크 ${links}개", text.contains("최대 ${links}개"))
        assertTrue(text.contains("가장 늦게 끝나는 링크가 끝나면 함께 삭제"))
        // The takedown reason: as long as the share server keeps it, 90 days with the removed mark.
        val reason = Regex("removedReason: (\\d+)").find(share)!!.groupValues[1]
        assertTrue("내린 이유 ${reason}자", text.contains("내린 이유(${reason}자 이내, 앱 1.23.0부터)만 90일"))
        // 밤낮 소식 and what it asks, nothing new kept.
        assertTrue(text.contains("<b>밤낮 소식(") && text.contains("내 문의에 새 답이 왔는지") && text.contains("다음 7일"))
        // The outbox: as many questions as the store keeps; request ids 24 hours.
        assertTrue(text.contains("최대 ${com.bamnat.app.company.InquiryStore.OUTBOX_MAX}개까지"))
        assertTrue(text.contains("요청 번호의 해시와 서버의 첫 답을 24시간만"))
        // Usage events, as many as the app has.
        assertTrue("이벤트 ${com.bamnat.core.stats.UsageEvent.entries.size}가지", text.contains("${com.bamnat.core.stats.UsageEvent.entries.size}가지 이름만"))
        assertTrue(text.contains("공지 열기"))
        assertTrue(text.contains("휴대폰 안에서만 맞춰 보며"))
        // [ALRM-15] 1.31: the settings screen only, no permission to ask directly.
        assertTrue(text.contains("<td>배터리 최적화 설정 열기 (앱 1.23.0부터, 이용자가 고를 때. 앱 1.31.0부터 권한 없이 휴대폰 설정 화면만 엶)</td>"))
        // [ALRM-04] 증명 사진: the fingerprint's size is the one the app makes, the photo goes, no camera permission.
        assertTrue(text.contains("알람 미션 ‘증명 사진’(앱 1.23.0부터, 이용자가 고를 때)") && text.contains("흑백 지문(64비트)만"))
        assertTrue(text.contains("사진은 카메라 권한 없이 휴대폰의 카메라 앱으로") && text.contains("알람 끄기 미션에 <b>증명 사진</b>"))
        val manifest = File(site, "shift-calendar-app/app/src/main/AndroidManifest.xml").readText()
        assertFalse("no camera permission", manifest.contains("android.permission.CAMERA"))
        assertTrue(text.contains("‘모든 링크 끊기’") && text.contains("‘보내는 것과 권한 한눈에’"))
    }

    /**
     * [INVITE-06][INVITE-08] 3.0 for 1.26.0: 친구 초대, in the app's and the company server's own numbers — what goes and when, how
     * long it stays, the phone mark, the referrer permission the library adds, 30 days' notice (결정 D18).
     */
    @Test fun thePrivacyPolicyCoversInvites() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        assertTrue(text.contains("처리방침 3.0 · 앱 버전 1.26.0 기준 · 게시일 2026년 10월 25일 · 시행일 2026년 11월 24일"))
        assertTrue("30 days' notice", java.time.LocalDate.of(2026, 10, 25).plusDays(30) == java.time.LocalDate.of(2026, 11, 24))
        assertTrue(text.contains("3.0에서 바뀐 점: 앱 1.26.0부터 <b>친구 초대</b>"))
        assertTrue(text.contains("1-12. 친구 초대 (선택, 앱 1.26.0부터)"))
        // [INVITE-08] The server's own numbers: the default rules (the developer app may change them), the longest
        // period of use, how long records stay.
        // [SRV-20] 1.31: index.js and the files split out of it (the bundle puts them back in one).
        val server = File(site, "shift-calendar-app/server/company-worker/src").listFiles { f -> f.extension == "js" }!!.sortedBy { it.name }
            .joinToString("\n") { it.readText() }
        val d = com.bamnat.core.tier.InviteTerms.DEFAULT
        assertTrue(server.contains("  friends: ${d.need},\n  days: ${d.days},\n  months: ${d.months},\n  capMonths: ${d.capMonths},"))
        assertTrue(server.contains("days: [1, 60]") && server.contains("graceDays: 30,") && server.contains("keepCounted: 365 * DAY"))
        assertTrue(text.contains("친구 ${d.need}명마다 프로 ${d.months}개월, 사용 기간 ${d.days}일, 상한 없음") && text.contains("사용 기간은 60일까지"))
        assertTrue(text.contains("영문·숫자 8자") && com.bamnat.core.tier.InviteRules.code("abcd-2345") == "ABCD2345")
        assertTrue(text.contains("알린 날부터 (사용 기간+30)일 뒤 삭제(지금 ${d.days + 30}일)") && text.contains("사용 기간을 채운 기록은 1년 뒤 삭제"))
        // The phone mark: hashed on the phone, never the id or the advertising ID.
        assertTrue(text.contains("<b>휴대폰 표시</b>") && text.contains("원래 번호와 광고 ID는 서버로 보내지 않으며"))
        assertTrue(com.bamnat.app.tier.PhoneMark.hash("x").matches(Regex("[0-9a-f]{64}")))
        assertTrue(text.contains("설치 ${com.bamnat.core.tier.InviteRules.REFERRER_WITHIN.toDays()}일 안에 한 번만"))
        // The permission the Install Referrer library adds is in the table.
        assertTrue(text.contains("<td>설치 리퍼러 서비스 연결(Google Play, 앱 1.26.0부터)</td>"))
        assertTrue(File(site, "shift-calendar-app/app/build.gradle.kts").readText().contains("com.android.installreferrer:installreferrer:2.2"))
        // What the user is told before tapping is what the policy says goes.
        assertTrue(com.bamnat.app.ui.pro.INVITE_MAKE_CONSENT.contains("설치 번호와 휴대폰 표시"))
        assertTrue(com.bamnat.app.ui.pro.inviteUseConsent("ABCD2345", 7).contains("초대 코드 ABCD-2345"))
        assertTrue(com.bamnat.app.ui.pro.inviteUseConsent("ABCD2345", 7).contains("7일이 지난 뒤 앱을 다시 열면"))
        assertTrue(text.contains("스토어 평점·리뷰와는 묶지 않습니다"))
    }

    @Test fun theTermsCoverSubscriptionTrialAndRefunds() {
        val terms = File(site, "terms/index.html")
        assumeTrue(File(site, "privacy/index.html").exists())
        assertTrue("이용약관 페이지가 있어요", terms.exists())
        val text = terms.readText()
        assertTrue(text.contains("이용약관 1.0 · 앱 버전 1.19.0"))
        assertTrue(text.contains("1개월 무료 체험"))
        assertTrue(text.contains("체험이 끝나기 ${TrialConsent.ASK_BEFORE.toDays()}일 전부터"))
        assertTrue(text.contains("결제한 날부터 7일 안에 청약철회"))
        assertTrue(text.contains("제17조 제2항 제5호"))
        assertTrue(text.contains("Google Play › 결제 및 정기 결제"))
        assertTrue(text.contains("설치 뒤 ${AdRules.QUIET_DAYS}일 동안은 광고가 없습니다"))
        assertTrue(TERMS_URL.endsWith("/terms/"))
    }

    /** [PRIV-03] 3.1 for 1.27.0 (결정 D84): the dates the app holds the launch by are the dates the pages say. */
    @Test fun policy31AndTerms11MoveTheDateWithTheTestersYes() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        val c = com.bamnat.core.ops.PolicyConsent
        assertTrue(c.EFFECTIVE == java.time.LocalDate.of(2026, 11, 10) && c.HELD_UNTIL == java.time.LocalDate.of(2026, 11, 24))
        assertTrue(text.contains("처리방침 ${c.VERSION} · 앱 버전 1.27.0 기준 · 게시일 2026년 10월 27일 · 시행일 2026년 11월 10일"))
        assertTrue(text.contains("원래 알린 날(2026년 11월 24일)까지 결제·광고"))
        assertTrue(text.contains("대표 주기, 맞아요·달라요, 급여 방식 찾기 (선택, 앱 1.27.0부터)"))
        assertTrue(text.contains("회사 정보 광장과 일 유형별 광장 (선택, 앱 1.27.0부터, 다른 이용자에게 공개)"))
        assertTrue(text.contains("‘알리지 않고 저장’을 누르면 보내지 않습니다"))
        val terms = File(site, "terms/index.html").readText()
        assertTrue(terms.contains("이용약관 1.1 · 앱 버전 1.27.0 기준 · 게시일 2026년 10월 27일 · 시행일 2026년 11월 10일"))
        assertTrue(terms.contains("이 약관은 2026년 11월 10일부터 시행합니다"))
    }

    /**
     * [FLAG-16][PERF-16] 3.2 for 1.28.0 (결정 D100, 순번 459): the dates the `stats.more` flag waits for are the dates the
     * page says, and the page names what the one extra yes sends in the app's own words and buckets (not milliseconds).
     */
    @Test fun policy32NamesTheExtraUsageItemsAndItsDates() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        val v = com.bamnat.core.flags.PolicyCalendar.SEED.of("3.2")!!
        assertTrue(v.posted == java.time.LocalDate.of(2026, 10, 28) && v.effective == java.time.LocalDate.of(2026, 11, 27) && v.heldUntil == null)
        assertTrue(text.contains("처리방침 3.2 · 앱 버전 1.28.0 기준 · 게시일 2026년 10월 28일 · 시행일 2026년 11월 27일"))
        assertTrue("3.2 is the newest version line", text.indexOf("처리방침 3.2 ·") < text.indexOf("처리방침 3.1 ·"))
        assertTrue(text.contains("3.2에서 바뀐 점: 앱 1.28.0부터"))
        assertTrue("the card's own title", text.contains("“${com.bamnat.app.ui.flags.STATS_MORE_TITLE}”"))
        assertTrue("the settings row", text.contains("‘켜진 기능 목록·앱 시작 시간도 보내기’"))
        val b = com.bamnat.app.diag.StartupStats.BUCKETS
        assertTrue(b.first() == 100L && b[b.size - 2] == 15_000L)
        assertTrue("buckets, as the app sends them", text.contains("0.1초~15초의 정해진 구간 값"))
        assertFalse("no milliseconds", text.contains("밀리초"))
        assertTrue(text.contains("앱 1.28.0부터: 기능 켜짐 확인"))
        assertTrue(text.contains("최고 관리자의 휴대폰에서는 숨긴 근무 유형도 ‘${com.bamnat.core.flags.TypeExposure.HIDDEN_MARK}’로 보입니다"))
    }

    /**
     * [PHOTO-09] 3.3 for 1.29.0: the `photo.wrong` flag waits for 3.3's date, which is the page's date and the same day as 3.2,
     * and the page lists the screens and reasons in the app's own words and says what never goes.
     */
    @Test fun policy33NamesPhotoWrongCountsAndItsDates() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        val v = com.bamnat.core.flags.PolicyCalendar.SEED.of("3.3")!!
        val v32 = com.bamnat.core.flags.PolicyCalendar.SEED.of("3.2")!!
        assertTrue(v.posted == java.time.LocalDate.of(2026, 10, 28) && v.effective == v32.effective && v.heldUntil == null)
        assertTrue(text.contains("처리방침 3.3 · 앱 버전 1.29.0 기준 · 게시일 2026년 10월 28일 · 시행일 2026년 11월 27일"))
        assertTrue("3.3 is the newest version line", text.indexOf("처리방침 3.3 ·") < text.indexOf("처리방침 3.2 ·"))
        assertTrue(text.contains("1-13. 사진이 달라요 (선택, 앱 1.29.0부터, 처리방침 3.3 시행 뒤)"))
        com.bamnat.core.stats.PhotoScreen.entries.forEach { assertTrue(it.label, text.contains(it.label.removeSuffix(" 사진"))) }
        com.bamnat.core.stats.PhotoWrongReason.entries.forEach { assertTrue(it.label, text.contains(it.label)) }
        assertTrue(text.contains("사진, 읽은 글자와 숫자, 이용자가 적은 글, 설치 번호, 휴대폰 모델과 정확한 시각은 보내지 않습니다"))
        val flag = com.bamnat.core.flags.FlagCatalog.of(com.bamnat.core.flags.FlagCatalog.PHOTO_WRONG)!!
        assertTrue(flag.basis.policy.orEmpty().startsWith("3.3 1-13항"))
    }

    /**
     * [ITEM-03] 3.4 for 1.30.0 (결정 D102): the `plaza.fixed_amount` flag waits for 3.4's date, and the page says which amount
     * goes (회사 공통 금액 only) and which never does, and lists the two new 사진이 달라요 reasons.
     */
    @Test fun policy34NamesCompanyFixedAmountsAndItsDates() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        val v = com.bamnat.core.flags.PolicyCalendar.SEED.of("3.4")!!
        val v32 = com.bamnat.core.flags.PolicyCalendar.SEED.of("3.2")!!
        assertTrue(v.posted == java.time.LocalDate.of(2026, 10, 28) && v.effective == v32.effective && v.heldUntil == null)
        assertTrue(text.contains("처리방침 3.4 · 앱 버전 1.30.0 기준 · 게시일 2026년 10월 28일 · 시행일 2026년 11월 27일"))
        assertTrue("3.4 comes before 3.3", text.indexOf("처리방침 3.4 ·") < text.indexOf("처리방침 3.3 ·"))
        assertTrue(text.contains("‘회사 공통 금액’으로 정한 수당의 금액"))
        assertTrue(text.contains("사람마다 다른 수당의 금액"))
        val flag = com.bamnat.core.flags.FlagCatalog.of(com.bamnat.core.flags.FlagCatalog.PLAZA_FIXED_AMOUNT)!!
        assertTrue(flag.basis.policy.orEmpty().startsWith("3.4 1-2항"))
        assertTrue(flag.conditions.any { it is com.bamnat.core.flags.Condition.Date && it.policy == "3.4" })
    }

    /**
     * [STAT-08] 3.5 for 1.31.0 (N4): the `stats.lookup` flag turns on by itself on 3.5's date, which is the page's date,
     * and the page says what the lookup times are, what never goes, and the watch and battery changes of the same version.
     */
    @Test fun policy35NamesLookupTimesAndItsDates() {
        val policy = File(site, "privacy/index.html")
        assumeTrue(policy.exists())
        val text = policy.readText()
        val v = com.bamnat.core.flags.PolicyCalendar.SEED.of("3.5")!!
        assertTrue(v.posted == java.time.LocalDate.of(2026, 10, 29) && v.effective == java.time.LocalDate.of(2026, 11, 28) && v.heldUntil == null)
        assertTrue(text.contains("처리방침 3.5 · 앱 버전 1.31.0 기준 · 게시일 2026년 10월 29일 · 시행일 2026년 11월 28일"))
        assertTrue("3.5 is the newest version line", text.indexOf("처리방침 3.5 ·") < text.indexOf("처리방침 3.4 ·"))
        assertTrue(text.contains("③ 조회에 걸린 시간"))
        assertTrue(text.contains("찾은 글자, 연 회사, 주소는 보내지 않음"))
        assertTrue(text.contains("워치는 알람을 따로 갖고 있지 않으며"))
        assertTrue(text.contains("권한 없이 휴대폰 설정 화면만 엶"))
        // The words the phone shows from 1.31: the card's title and the settings row, as the page quotes them.
        assertTrue(text.contains("“${com.bamnat.app.ui.flags.STATS_MORE_TITLE}”"))
        assertTrue(text.contains("‘${com.bamnat.app.ui.flags.STATS_MORE_ROW}’"))
        val flag = com.bamnat.core.flags.FlagCatalog.of(com.bamnat.core.flags.FlagCatalog.STATS_LOOKUP)!!
        assertTrue(flag.basis.policy.orEmpty().startsWith("3.5 1-9항"))
        assertTrue(flag.conditions.any { it is com.bamnat.core.flags.Condition.Date && it.policy == "3.5" })
        assertTrue(flag.conditions.any { it is com.bamnat.core.flags.Condition.Consent && it.key == "usage.stats.lookup" })
    }
}
