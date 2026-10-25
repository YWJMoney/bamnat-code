package com.bamnat.core

import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.ExtraBasis
import com.bamnat.core.pay.HolidayWork
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayslipReader
import com.bamnat.core.pay.SlipRules
import com.bamnat.core.pay.SlipRulesResult
import com.bamnat.core.pay.WageType
import com.bamnat.core.model.ExtraKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [PAYS-14] 명세서 표본 5종 → 회사 규칙 역산 → 그 달 계산이 명세서 지급 총액 ±1% 안 (계획/1.25-V-개발계획.md 376).
 * 시작은 간편 설정을 막 끝낸 상태([SlipCases]), 명세서는 기기 안에서 읽은 글과 같은 고정 파일.
 */
class SlipRulesTest {
    private fun text(file: String) = requireNotNull(javaClass.getResource("/payslips/$file")) { file }.readText()

    private fun infer(c: SlipCases.Case, ruleText: String? = null): SlipRulesResult {
        val read = PayslipReader.read(text(c.file))
        val r = SlipRules.infer(c.start, SlipCases.oct, c.resolver::resolve, read, ruleText)
        assertNotNull("${c.name}: 지급 총액을 못 읽음", r)
        return r!!
    }

    private fun assertClose(c: SlipCases.Case, r: SlipRulesResult) {
        assertTrue("${c.name}: 명세서 ${r.gross}, 앱 ${r.total} (${"%.2f".format(r.gapPercent)}%)\n바뀜 ${r.changes}\n안내 ${r.hints}", r.close)
        // The month by the inferred rules equals the month by the company's real rules within 1%.
        val truth = PayCalculator().calculate(c.truth, SlipCases.oct, c.resolver::resolve).total
        assertTrue("${c.name}: 실제 규칙 $truth, 역산 ${r.total}", kotlin.math.abs(truth - r.total) <= truth / 100)
    }

    @Test
    fun `병원 간호 3교대 - 나이트수당은 나이트 근무마다, 면허수당은 매달`() {
        val c = SlipCases.hospital
        val r = infer(c)
        assertClose(c, r)
        assertEquals(2_600_000, r.profile.base.on(SlipCases.oct).value)
        val night = r.profile.items.single { it.name == "나이트수당" }
        // Named after the 나이트 shift on the roster: once per 나이트.
        assertEquals(Cadence.PER_SHIFT_TYPE, night.cadence)
        assertEquals(setOf(3L), night.shiftTypeIds)
        assertEquals(35_000, night.amount.on(SlipCases.oct).value)
        assertEquals(Cadence.MONTHLY, r.profile.items.single { it.name == "면허수당" }.cadence)
        assertTrue(r.changes.toString(), r.changes.any { it.contains("월 기본급 2,600,000원") })
    }

    @Test
    fun `제조 4조 2교대 - 대근수당 줄이 대근 1회 금액으로`() {
        val c = SlipCases.factory
        val r = infer(c)
        assertClose(c, r)
        val cover = r.profile.rules.extra[ExtraKind.COVER_ID]
        assertEquals(ExtraBasis.FIXED_PER_TIME, cover?.basis)
        assertEquals(150_000L, cover?.amount)
    }

    @Test
    fun `공기업 - 휴일 수당이 없는 명세서는 교대제라 상관없음`() {
        val c = SlipCases.publicCorp
        val r = infer(c)
        assertClose(c, r)
        assertTrue(r.profile.rules.holidayWork == HolidayWork.SHIFT_NORMAL || r.profile.rules.holidayWork == HolidayWork.SUBSTITUTE)
    }

    @Test
    fun `편의점 시급 - 명세서 시급으로 바꿈`() {
        val c = SlipCases.store
        val r = infer(c)
        assertClose(c, r)
        assertEquals(WageType.HOURLY, r.profile.wageType)
        assertEquals(10_320, r.profile.base.on(SlipCases.oct).value)
    }

    @Test
    fun `물류 야간 - 회사 야간 가산 60%, 빨간날은 특근 1회`() {
        val c = SlipCases.logistics
        val r = infer(c)
        assertClose(c, r)
        assertEquals(0.6, r.profile.rules.nightPremium!!, 1e-9)
        assertEquals(HolidayWork.AS_EXTRA, r.profile.rules.holidayWork)
        assertEquals(80_000L, r.profile.rules.holidayShiftPay(41))
    }

    @Test
    fun `두 번째 사진의 회사 계산 방법이 먼저 들어감`() {
        val c = SlipCases.logistics
        val r = infer(c, ruleText = "야간 가산 60%\n공휴일 근무는 특근처럼 계산")
        assertClose(c, r)
        assertTrue(r.changes.toString(), r.changes.any { it.startsWith("회사 계산 방법:") })
    }
}
