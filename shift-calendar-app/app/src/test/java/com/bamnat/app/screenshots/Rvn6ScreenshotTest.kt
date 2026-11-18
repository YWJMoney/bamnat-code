package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.company.PlazaHomeScreen
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.pay.LineKind
import com.bamnat.core.pay.MonthPay
import com.bamnat.core.pay.PayLine
import com.bamnat.core.plaza.PublicCycles
import java.time.YearMonth
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-20] 갈래 N 6차 화면 사진(앞·뒤 같은 차례): 빈 광장의 시작 꾸러미(U10a-02·I10ab-03), 글쓴이 동그라미(U10a-03), 공개 근무주기의
 * '넣기 전 요약'(I10aa-02), 나눈 급여 방식의 명세서 사진으로 채우기(U10a-15), 부서 카드 근무 줄(HQ-90). 이 파일은 앞(5차) 코드에서도 돌아요.
 */
/**
 * 처리방침 3.12(1-4항)가 시행되고 이 폰이 동의한 상태: 광장 '사람 먼저'(plaza.people) 화면. 2.0.0을 받는 폰은 늦어도 12-09부터 모두 이 화면이에요.
 */
internal fun plazaPeopleOn(c: com.bamnat.app.AppContainer) {
    val now = com.bamnat.core.time.Today.instant()
    val v = com.bamnat.core.flags.FlagCatalog.PEOPLE_POLICY
    // The shots' clock is its own day: 3.12 in force from the day before it.
    val day = com.bamnat.core.time.Today.date().minusDays(1)
    c.remoteConfig.save(com.bamnat.app.tier.LaunchConfig.parse(org.json.JSONObject(
        """{"flags":{},"policy":[{"version":"$v","posted":"$day","effective":"$day","heldUntil":null}],"serverTime":"$now"}"""), now))
    c.remoteConfig.agreePolicy(now, v)
    c.featureGate.refresh()
    c.flags.refresh()
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn6ScreenshotTest : ScreenshotBase() {
    private fun plaza(posts: Int?, people: Boolean = true): Screen.() -> Unit = {
        val c = companyOn()
        app.container.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        if (people) plazaPeopleOn(c)
        posts?.let { n -> company.talkPosts = List(n) { i ->
            com.bamnat.app.company.TalkPost("p${i + 1}", i + 1, null, false, "야간 끝나고 다들 뭐 드세요? 저는 국밥이요.",
                com.bamnat.core.time.Today.instant().minus(java.time.Duration.ofHours(2L + i)), false, nick = "새벽국밥")
        } }
        val vm = PlazaHomeViewModel(c, own = true)
        content { PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        waitFor(400)
    }

    private fun startPlaza(): Screen.() -> Unit = {
        plaza(0)()
        // Before 6차 the empty plaza had one line of text only; the same screen is shot either way.
        if (!shows("이렇게 시작해 보세요", exact = true)) runCatching { reveal("이렇게 시작해 보세요", exact = true) }
    }

    private fun onePost(): Screen.() -> Unit = {
        plaza(1)()
        reveal("야간 끝나고 다들 뭐 드세요? 저는 국밥이요.", exact = false)
    }

    private fun cards(): Screen.() -> Unit = {
        plaza(null)()
        reveal("11월부터 주간이 한 시간 줄었어요", exact = false)
    }

    /** 공개 근무주기를 이미 순서가 있는 달력에 넣기 전. */
    private fun publicChanges(): Screen.() -> Unit = {
        val gsEps = PublicCycles.all.first { it.company == "GS EPS" }
        content { com.bamnat.app.ui.company.PublicApplySheet(gsEps, onDismiss = {}, onPay = {}, onShare = {}) }
        settle()
        waitFor(400)
        reveal("급여 금액은 넣지도 가져오지도 않아요", exact = false)
    }

    private val month = MonthPay(YearMonth.of(2026, 10), listOf(
        PayLine(LineKind.BASE, "기본급", 2_500_000, "x"), PayLine(LineKind.OVERTIME, "연장", 300_000, "x"),
        PayLine(LineKind.NIGHT, "야간", 150_000, "x"), PayLine(LineKind.ALLOWANCE, "식대", 200_000, "x"),
    ), ordinaryHourly = 0, ratesVersion = "", minutes = emptyList(), warnings = emptyList())

    private fun slip(): Screen.() -> Unit = {
        companyOn()
        content { com.bamnat.app.ui.company.SharedSlipCheckCard(month) }
        settle()
        tapText("넣기", exact = true)
        settle()
    }

    /** HQ-90 회사 쪽 부서 카드의 '근무' 줄. */
    private fun deptCard(): Screen.() -> Unit = {
        val c = companyOn()
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        content { com.bamnat.app.ui.company.CompanyDetailScreen(vm, setup = false, onBack = {}, onApplied = { _, _ -> }, onByHand = {}) }
        settle()
        waitFor(400)
        reveal("야간 20:00~08:00", exact = false)
    }

    @Test fun deptLight() = shoot("rvn_91_dept_shifts_folded", FOLDED, deptCard())
    @Test fun deptDarkBig() = shoot("rvn_92_dept_shifts_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, deptCard())
    @Test fun deptWide() = shoot("rvn_93_dept_shifts_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, deptCard())

    /** 총괄 사진 검토(rvn_91): 대표가 아닌 판(포장팀)의 '확인 중이지만 써 보기'는 테두리 단추, 채운 단추는 대표 판의 것 하나. */
    private fun unadopted(): Screen.() -> Unit = {
        deptCard()()
        reveal("확인 중이지만 써 보기", exact = true)
    }
    @Test fun unadoptedLight() = shoot("rvn_98_unadopted_try_folded", FOLDED, unadopted())
    @Test fun unadoptedDarkBig() = shoot("rvn_99_unadopted_try_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, unadopted())

    @Test fun startLight() = shoot("rvn_69_plaza_start_folded", FOLDED, startPlaza())
    @Test fun startDarkBig() = shoot("rvn_70_plaza_start_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, startPlaza())
    @Test fun startWide() = shoot("rvn_71_plaza_start_unfolded", UNFOLDED, startPlaza())
    @Test fun oneLight() = shoot("rvn_72_plaza_one_post_folded", FOLDED, onePost())
    /** 3.12 전(사람 먼저 꺼짐) 광장 카드의 빈 광장. */
    @Test fun startBefore312() = shoot("rvn_90_plaza_start_before312_folded", FOLDED) {
        plaza(0, people = false)()
        if (!shows("이렇게 시작해 보세요", exact = true)) runCatching { reveal("이렇게 시작해 보세요", exact = true) }
    }
    @Test fun cardsLight() = shoot("rvn_73_plaza_cards_folded", FOLDED, cards())
    @Test fun cardsDarkBig() = shoot("rvn_74_plaza_cards_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, cards())
    @Test fun cardsWide() = shoot("rvn_75_plaza_cards_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, cards())
    @Test fun publicLight() = shoot("rvn_76_public_changes_folded", FOLDED, publicChanges())
    @Test fun publicDarkBig() = shoot("rvn_77_public_changes_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, publicChanges())
    @Test fun publicWide() = shoot("rvn_78_public_changes_unfolded", UNFOLDED, publicChanges())
    @Test fun slipLight() = shoot("rvn_79_slip_check_folded", FOLDED, slip())
    @Test fun slipDarkBig() = shoot("rvn_80_slip_check_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, slip())
    @Test fun slipWide() = shoot("rvn_81_slip_check_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, slip())
}
