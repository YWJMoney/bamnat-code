package com.bamnat.app.screenshots

import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 1.33 [SHR-20][J133-77]: the share sheet's 누구에게 (동료·가족·친구), the card a coworker's link with the company setup shows,
 * and settings in a shop's and a part-timer's words.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class J133ScreenshotTest : ScreenshotBase() {
    @Test fun foldedShareCoworker() = shoot("j133_01_folded_share_coworker", FOLDED) {
        val c = receivedOn()
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = shares, onOpenReceived = {}) }
        tapText("같", prefix = true)
        reveal("보여 줄 것")
    }
    @Test fun foldedShareFamily() = shoot("j133_02_folded_share_family", FOLDED) {
        val c = receivedOn()
        val snap = runBlocking { c.scheduleRepository.snapshot.first() }
        content { com.bamnat.app.ui.calendar.ShareDialog(snap, Today.month(), {}, {}, client = shares, onOpenReceived = {}) }
        tapText("가족", prefix = true)
        reveal("보여 줄 것")
    }
    @Test fun foldedCoworkerSetupCard() = shoot("j133_03_folded_coworker_setup", FOLDED) {
        val file = coworkerFile()
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.bamnat.app.ui.calendar.CoworkerSetupCard("민지", file, onBefore = { true }, onDone = {})
                com.bamnat.app.ui.calendar.FriendStart()
            }
        }
    }
    @Test fun foldedSettingsShop() = shoot("j133_04_folded_settings_shop", FOLDED, style(WorkStyle.BUSINESS) { tab(SETTINGS_TAB, wide = false) })
    @Test fun foldedSettingsHourly() = shoot("j133_05_folded_settings_hourly", FOLDED, style(WorkStyle.HOURLY) { tab(SETTINGS_TAB, wide = false) })
    @Test fun foldedHelpField() = shoot("j133_06_folded_help_field", FOLDED, style(WorkStyle.FIELD) {
        tab(SETTINGS_TAB, wide = false)
        tapText("도움말과 문의", exact = true)
    })

    /** [J133-88][J133-89] Where no one has shared a pay way yet: the institution's own 보수규정 and its 국민연금 line, said as the record's. */
    @Test fun foldedPublicRecord() = shoot("j133_07_folded_public_record", FOLDED) {
        val c = companyOn()
        runBlocking { c.payStore.save(com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile(), keepUndo = false) }
        val setup = """{"app":"com.bamnat.app","format":"bamnat-company","v":4,"company":"서울교통공사(시험)","shiftTypes":[],""" +
            """"pay":{"fiveOrMore":true,"monthlyHours":209,"holidayMode":"FULL","withAmounts":false,"items":[]}}"""
        company.publicRecord = com.bamnat.core.plaza.PublicFirms("2026-09", "국민연금공단 국민연금 가입 사업장 내역",
            listOf(com.bamnat.core.plaza.PublicFirm("서울교통공사(시험)", "서울 성동구", "도시철도 운송업", 16_000, 6_100_000)),
            rules = listOf(com.bamnat.core.plaza.PublicRuleSet("서울교통공사(시험)", "보수규정", "2026-09-15", "https://www.cleaneye.go.kr/", 209,
                listOf(
                    com.bamnat.core.plaza.PublicRuleLine("통상임금 기준 시간", "시간급 = 기본급÷209", "", "제4조"),
                    com.bamnat.core.plaza.PublicRuleLine("야간근무수당", "22:00~06:00 통상임금×0.5/209×시간", "", "별표2 3호"),
                    com.bamnat.core.plaza.PublicRuleLine("교대근무수당", "업무보전수당: 근무형태별 기본급의 %", "4조2교대 비심야 6.75% / 심야 6.35%",
                        "별표2 9호; 표 열 배치가 섞여 대상 설명은 원문 확인 권장, 표 판독 불확실"),
                    com.bamnat.core.plaza.PublicRuleLine("승무수당", "열차승무수당 현업 3급 이하 승무직, 매월", "월 100,000원", "별표2 8호"),
                ), listOf("승무수당"), setup)),
            rulesSource = "공공기관 보수규정 (알리오 공공기관 내부규정·클린아이·국가법령정보센터)")
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                com.bamnat.app.ui.company.PublicFirmCard("서울교통공사(시험)")
            }
        }
        org.junit.Assert.assertTrue(appears("공개 규정 · 서울교통공사(시험) 보수규정", exact = true))
        org.junit.Assert.assertTrue(appears("원문 표를 다 읽지 못한 줄이에요", exact = false))
        reveal("국민연금 가입 16,000명", exact = false)
    }

    /**
     * [J133-92][J133-93] 1.33 빌드 7: 내 설정 나누기 on a calendar that took another company's setup over a public cycle shows only the
     * company in use, and the job the pay setup names as one line (the 13 chips behind 바꾸기).
     */
    @Test fun foldedReportOneCompany() = shoot("j133_08_folded_report_one_company", FOLDED) {
        val c = companyOn()
        withPay()
        c.companyStore.setPublicUse(com.bamnat.app.company.PublicUse("hyundai", "현대제철", "4-2", "DDOONNOO", true, Today.millis()))
        c.companyStore.setApplied(com.bamnat.app.company.AppliedSetup("AAAAAAAAAAAA", "GS칼텍스", "", "0123456789abcdef", null, false,
            java.time.Instant.now()))
        assertNull("the public cycle of another company ends with the setup put in", c.companyStore.publicUse.value)
        val vm = com.bamnat.app.ui.company.CompanyReportViewModel(c)
        content { com.bamnat.app.ui.company.CompanyReportScreen(vm, onBack = {}, onDone = {}, onOpenSent = {}) }
        settle()
        assertTrue(appears("어떤 일: ", exact = false))
        assertTrue("the chips wait behind 바꾸기", !appears("어떤 일인가요? (선택)", exact = true))
        assertTrue(!appears("현대제철", exact = false))
        tapText("바꾸기", exact = true)
        reveal("어떤 일인가요? (선택)", exact = true)
    }

    /** [J133-94] 빌드 8: the pay screen of a calendar on a 광장 version, its pay set up, opens (it closed the app). */
    @Test fun payScreenOnAPlazaVersionOpens() = shoot("j133_09_folded_pay_plaza_version", FOLDED) {
        val c = companyOn()
        withPay()
        c.companyStore.setApplied(com.bamnat.app.company.AppliedSetup("AAAAAAAAAAAA", "GS칼텍스", "", "0123456789abcdef", null, false,
            java.time.Instant.now()))
        val vm = com.bamnat.app.ui.pay.PayViewModel(c)
        content { com.bamnat.app.ui.pay.PayScreen(vm = vm, onBack = {}) }
        settle()
        assertTrue(appears("급여", exact = false))
    }

    /** [J133-97] 빌드 8: the rules editor opened from a 회사 규칙 chip goes back to the pay screen and stays there. */
    @Test fun rulesChipEditorCloses() = shoot("j133_10_folded_pay_rules_closed", FOLDED) {
        val c = companyOn()
        withPay()
        val vm = com.bamnat.app.ui.pay.PayViewModel(c)
        content { com.bamnat.app.ui.pay.PayScreen(vm = vm, onBack = {}, focus = com.bamnat.app.ui.pay.PayFocus.RULES) }
        settle()
        assertTrue("the editor opened", !shows("결과", exact = true))
        back()
        assertTrue("back on the pay screen", appears("결과", exact = true))
        settle()
        assertTrue("and it stays", shows("결과", exact = true))
    }

    /** [J133-99] 빌드 8: words typed in the talk are asked about before back closes it. */
    @Test fun talkAsksBeforeTypedWordsGo() = shoot("j133_11_folded_talk_back_asks", FOLDED) {
        val c = companyOn()
        val holder = com.bamnat.app.ui.company.TalkHolder(c, kotlinx.coroutines.MainScope())
        content { com.bamnat.app.ui.company.TalkSheet(holder) }
        holder.show("c1", "s1", "생산팀")
        settle()
        typeInto("이 규칙에 대해 적어 주세요", "야간 수당 계산이 달라요")
        back()
        reveal("쓴 글을 버릴까요?", exact = true)
        tapText("취소", exact = true)
        assertTrue(appears("야간 수당 계산이 달라요", exact = true))
    }
}
