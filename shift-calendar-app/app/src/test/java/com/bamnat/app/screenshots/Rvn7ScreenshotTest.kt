package com.bamnat.app.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.company.AutoShareHost
import com.bamnat.app.ui.company.PlazaHomeScreen
import com.bamnat.app.ui.company.PlazaHomeViewModel
import com.bamnat.app.ui.settings.AutoShareRow
import com.bamnat.core.pay.CompanyRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVN-22] 갈래 N 7차 화면 사진(앞·뒤 같은 차례): 검토 I10bc-03, 회사 광장에 나눌 것을 부분마다(근무주기·근무 시간·급여 계산 방식)
 * 저절로·묻기·안 함으로 고르는 설정, 그 부분만 묻는 창, '내일 오전에 나눠요 · 취소' 한 줄(광장 위에도 남음). 검토 PC-27 문의의 '점검 기록도
 * 보내기'(앱이 이상해요). U10b-08 보내기 대기(몇 개·고칠 것·까닭), I10bb-02 서버에 있는 내 정보, I10bb-03 쓰는 동안 글 검사. 이 파일은 앞(7차 전)
 * 코드에서도 돌아요: 앞 코드에 있는 길(set, setAutoShare, setAutoPay, check, queue, refused, talk.show)만 써요.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn7ScreenshotTest : ScreenshotBase() {
    private val c get() = app.container

    /** A 교대 calendar of (주)예시정밀 with its pay way (야간 50%), shared once; then 야간 60% saved. */
    private fun changed(autoPay: Boolean) {
        companyOn()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.SHIFT) }
        runBlocking {
            c.payStore.save(PayProfile(Occupation.MANUFACTURING, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(Today.date(), 2_500_000))),
                rules = CompanyRules(nightPremium = 0.5)), keepUndo = false)
        }
        c.companyStore.setMember("c1", "(주)예시정밀")
        c.companyStore.setApplied(com.bamnat.app.company.AppliedSetup("c1", "(주)예시정밀", "생산팀", "s1", 2, true, Today.instant()))
        c.companyStore.setAutoShare(true)
        c.companyStore.setAutoPay(autoPay)
        c.companyStore.autoBase = runBlocking { c.companyShare.exportForServer().toString() }
        c.companyStore.autoSentAt = Today.millis()
        runBlocking { c.payStore.save(c.payStore.profile.value!!.copy(rules = CompanyRules(nightPremium = 0.6)), keepUndo = false) }
    }

    /** 설정 › 개인정보·앱 정보: how each part goes, with what waits to go by itself. */
    private fun setting(): Screen.() -> Unit = {
        changed(autoPay = true)
        runBlocking { c.autoShare.check() }
        content {
            Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).verticalScroll(rememberScrollState()).padding(16.dp)) {
                AutoShareRow()
            }
        }
        settle()
        waitFor(400)
    }

    /** The cycle goes by itself, the pay way is asked: a change of the pay way asks about it alone. */
    private fun ask(): Screen.() -> Unit = {
        changed(autoPay = false)
        content { Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) { AutoShareHost(c, onOpen = {}) } }
        runBlocking { c.autoShare.check() }
        settle()
        waitFor(400)
    }

    /** The pay way goes by itself: the one line right after the save. */
    private fun line(): Screen.() -> Unit = {
        changed(autoPay = true)
        content {
            val bar = remember { SnackbarHostState() }
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                AutoShareHost(c, onOpen = {}, snackbar = bar)
                SnackbarHost(bar, Modifier.align(Alignment.BottomCenter))
            }
        }
        runBlocking { c.autoShare.check() }
        settle()
        waitFor(400)
    }

    /** The 광장 later that day: what waits for the morning stays above, with 취소. */
    private fun plaza(): Screen.() -> Unit = {
        changed(autoPay = true)
        runBlocking { c.autoShare.check() }
        c.autoShare.sharedDone()
        val vm = PlazaHomeViewModel(c, own = true)
        content { PlazaHomeScreen(vm, onBack = {}, onAll = {}, onCompany = {}, onRates = {}, onShare = {}) }
        settle()
        waitFor(400)
    }

    /** 검토 PC-27: 문의 › 앱이 이상해요, scrolled to 점검 기록도 보내기 (before: ticked by itself; after: offered, with why). */
    private fun inquiry(): Screen.() -> Unit = {
        com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "시험"))
        val vm = com.bamnat.app.ui.settings.InquiryViewModel(c)
        content { com.bamnat.app.ui.settings.InquiryScreen(vm, onBack = {}) }
        settle()
        tapText("앱이 이상해요")
        scrolledTo("점검 기록도 보내기")
        settle()
        waitFor(400)
    }

    /** 검토 U10b-08: two questions wait for the internet, one the server would not take (a phone number); how many, and why. */
    private fun waiting(): Screen.() -> Unit = {
        val info = com.bamnat.app.company.InquiryInfo("2.0.0", "Pixel", "15")
        c.inquiryStore.queue(com.bamnat.app.company.QueuedInquiry("request-id-0000001", com.bamnat.app.company.InquiryKind.BUG,
            "알람이 5분 늦게 울려요", info, Today.instant()))
        c.inquiryStore.queue(com.bamnat.app.company.QueuedInquiry("request-id-0000002", com.bamnat.app.company.InquiryKind.QUESTION,
            "010-1234-5678로 연락 주세요", info, Today.instant()))
        c.inquiryStore.refused("request-id-0000002", "phone")
        val vm = com.bamnat.app.ui.settings.InquiryViewModel(c)
        content { com.bamnat.app.ui.settings.InquiryScreen(vm, onBack = {}) }
        settle()
        tapText("내 문의", exact = true)
        waitFor(600)
        scrolledTo("보내기 대기")
        settle()
    }

    /** 검토 I10bb-02: 내가 보낸 정보 at its end, where 서버에 있는 내 정보 says what the server has (before: only 모두 지우기). */
    private fun mine(): Screen.() -> Unit = {
        companyOn()
        company.mine.reversed().forEach { c.companyStore.addSent(com.bamnat.app.company.SentReport(it.id, it.company, it.dept, it.ask, it.createdAt)) }
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanySentScreen(vm, onBack = {}, onShare = {}) }
        settle()
        waitFor(600)
        scrolledTo("이 휴대폰이 보낸 정보 모두 지우기")
        settle()
        waitFor(400)
    }

    /** 검토 I10bb-03: an account number typed in the 교대 광장 box is marked while typing and said under it (before: only after 보내기). */
    private fun typing(big: Boolean): Screen.() -> Unit = {
        val vm = com.bamnat.app.ui.company.CompanyBoardViewModel(companyOn())
        content { com.bamnat.app.ui.company.CompanyBoardScreen(vm, onBack = {}, onOpen = {}, onShare = {}) }
        vm.talk.show("style-shift", com.bamnat.core.plaza.StylePlaza.ROOM, "교대 광장")
        settle()
        check(appears("교대 광장")) { "광장 방 토론이 열리지 않았어요" }
        typeInto(if (big) "이야기를 적어요" else "이 광장 분들과 나누고 싶은 이야기를 적어 주세요", "회비는 국민 123456-01-234567로 보내 주세요")
        settle()
        waitFor(400)
    }

    @Test fun settingLight() = shoot("rvn_100_share_ways_folded", FOLDED, setting())
    @Test fun settingDarkBig() = shoot("rvn_101_share_ways_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, setting())
    @Test fun settingWide() = shoot("rvn_102_share_ways_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, setting())
    @Test fun askLight() = shoot("rvn_103_share_ask_folded", FOLDED, ask())
    @Test fun askDarkBig() = shoot("rvn_104_share_ask_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, ask())
    @Test fun askWide() = shoot("rvn_105_share_ask_unfolded", UNFOLDED, ask())
    @Test fun lineLight() = shoot("rvn_106_share_held_line_folded", FOLDED, line())
    @Test fun lineDarkBig() = shoot("rvn_107_share_held_line_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, line())
    @Test fun plazaLight() = shoot("rvn_108_share_held_plaza_folded", FOLDED, plaza())
    @Test fun plazaDarkBig() = shoot("rvn_109_share_held_plaza_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, plaza())
    @Test fun inquiryLight() = shoot("rvn_110_inquiry_bug_check_folded", FOLDED, inquiry())
    @Test fun inquiryDarkBig() = shoot("rvn_111_inquiry_bug_check_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, inquiry())
    @Test fun waitingLight() = shoot("rvn_112_inquiry_waiting_folded", FOLDED, waiting())
    @Test fun waitingDarkBig() = shoot("rvn_113_inquiry_waiting_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, waiting())
    @Test fun waitingWide() = shoot("rvn_114_inquiry_waiting_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, waiting())
    @Test fun mineLight() = shoot("rvn_115_server_mine_folded", FOLDED, mine())
    @Test fun mineDarkBig() = shoot("rvn_116_server_mine_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, mine())
    @Test fun mineWide() = shoot("rvn_117_server_mine_unfolded", UNFOLDED, mine())
    @Test fun typingLight() = shoot("rvn_118_text_check_typing_folded", FOLDED, typing(big = false))
    @Test fun typingDarkBig() = shoot("rvn_119_text_check_typing_folded_dark_200", Rvn5ScreenshotTest.DARK, 2f, typing(big = true))
    @Test fun typingWide() = shoot("rvn_120_text_check_typing_unfolded_dark", Rvn5ScreenshotTest.DARK_WIDE, typing(big = false))
}
