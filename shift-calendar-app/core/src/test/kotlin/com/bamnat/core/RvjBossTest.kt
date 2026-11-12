package com.bamnat.core

import com.bamnat.core.boss.BossFilings
import com.bamnat.core.boss.Insurance
import com.bamnat.core.boss.InsuranceRules
import com.bamnat.core.boss.LedgerCsv
import com.bamnat.core.boss.PayrollLedger
import com.bamnat.core.boss.Payslip
import com.bamnat.core.boss.StaffAllowance
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffImport
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.imports.StaffRow
import com.bamnat.core.boss.StaffTax
import com.bamnat.core.boss.WageForm
import com.bamnat.core.boss.Workplace
import com.bamnat.core.money.DailyWorkerTax
import com.bamnat.core.work.StaffShift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/** 2.0.0 갈래 J 사장님 급여 (꺼진 기능 M25): 검토 BOSS·PAYC·PAYU 줄의 재현 시험. */
class RvjBossTest {
    private val nine = StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0))
    private val week = DayOfWeek.entries.take(5).associateWith { nine }
    private val place = Workplace("p", "가나다카페", fivePlus = true, payday = 10)
    private val nov = YearMonth.of(2026, 11)

    /** [RVJ-09] 검토 BOSS-56: 그달에 주는 가게 (매월 25일 당월 지급). The column keeps it as 100 + day. */
    @Test fun payDayCanBeTheSameMonth() {
        val same = place.copy(payday = 25, payNextMonth = false)
        assertEquals(LocalDate.of(2026, 11, 25), same.payDate(nov))
        assertEquals(LocalDate.of(2026, 12, 10), place.payDate(nov))
        assertEquals(125, Workplace.encodePayday(25, nextMonth = false))
        assertEquals(25 to false, Workplace.decodePayday(125))
        assertEquals(10 to true, Workplace.decodePayday(10))
        // 2026-04-25 is a 토요일: the day stays, the working day before is the one to plan on.
        assertEquals(LocalDate.of(2026, 4, 24), same.payDateWorkday(YearMonth.of(2026, 4)))
    }

    /** [RVJ-14] 검토 BOSS-57: 11/15 (일) 4대보험 신고는 11/16 (월)로, 까닭을 적어요. */
    @Test fun deadlinesOnADayOffMoveToTheNextWorkday() {
        val a = StaffMember("a", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 10, 20), weekly = week, insurance = Insurance.ALL)
        val due = BossFilings.upcoming(place, listOf(a), LocalDate.of(2026, 11, 12), { m -> PayrollLedger.month(place, listOf(a), m, emptyList()) })
        val rest = due.single { it.key.startsWith("in-rest:a") }
        assertEquals(LocalDate.of(2026, 11, 16), rest.date)
        assertTrue(rest.detail, rest.detail.contains("15일이 일요일이라"))
    }

    /** [RVJ-14] 검토 BOSS-58: 근로소득 간이지급명세서 (2026 하반기분 → 2027-02-01, 1/31이 일요일) and 일용 지급명세서·근로내용확인신고. */
    @Test fun wageBriefAndDailyStatements() {
        val a = StaffMember("a", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 1, 5), weekly = week, insurance = Insurance.ALL)
        val d = StaffMember("d", "이일용", base = 120_000, form = WageForm.DAY, hiredOn = LocalDate.of(2026, 12, 1),
            weekly = mapOf(DayOfWeek.SATURDAY to nine), insurance = Insurance(tax = StaffTax.DAILY))
        val staff = listOf(a, d)
        val due = BossFilings.upcoming(place, staff, LocalDate.of(2027, 1, 20), { m -> PayrollLedger.month(place, staff, m, emptyList()) })
        val brief = due.single { it.kind == BossFilings.Kind.WAGE_BRIEF }
        assertEquals(LocalDate.of(2027, 2, 1), brief.date)
        assertTrue(brief.title.contains("하반기"))
        val daily = due.filter { it.kind == BossFilings.Kind.DAILY_STATEMENT }
        assertTrue(daily.any { it.title.startsWith("근로내용확인신고 (12월") && it.date == LocalDate.of(2027, 1, 15) })
        // 일용직은 고용·산재 취득 신고가 따로 없어요(근로내용확인신고로 해요).
        assertTrue(due.none { it.key.startsWith("in-rest:d") })
    }

    /**
     * 2.0.0 [RVJ-38] 검토 PC-100 (통합 2차 PC Q16): 입사일 없이 저장한 3.3% 직원이 원천세·지급명세서 금액에서 말없이 통째로 빠지고, 그 사람만
     * 받은 달은 사업소득 간이지급명세서 줄이 아예 안 보였어요(입사일을 넣으니 32,360원 → 102,650원).
     */
    @Test fun aLineSaysByItsAmountWhoHasNoHireDate() {
        val open = Workplace("p", "식당", fivePlus = false)
        val three = StaffMember("t", "삼점삼", base = 10_320, weekly = week, insurance = Insurance(tax = StaffTax.BIZ))
        val day = StaffMember("d", "일용", form = WageForm.DAY, base = 200_000, hiredOn = LocalDate.of(2026, 9, 1),
            weekly = listOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY).associateWith { nine }, insurance = Insurance(tax = StaffTax.DAILY))
        val staff = listOf(three, day)
        val ledgerOf = { m: YearMonth -> PayrollLedger.month(open, staff, m, emptyList()) }
        val due = BossFilings.upcoming(open, staff, LocalDate.of(2026, 11, 12), ledgerOf)
        val tax = due.first { it.kind == BossFilings.Kind.WITHHOLD && it.key.endsWith(":2026-11") }
        val both = BossFilings.withheld(open, ledgerOf(YearMonth.of(2026, 10)))
        assertEquals(both.filter { !it.biz }.sumOf { it.tax + it.local }, tax.amount)
        assertEquals(listOf("t"), tax.missing!!.staffIds)
        assertEquals(both.sumOf { it.tax + it.local }, tax.missing!!.amount)
        assertTrue(tax.missing!!.line(tax.amount), tax.missing!!.line(tax.amount).contains("(넣으면 "))
        // 3.3% 직원만 받은 줄도 보여요(금액 0, 빠진 사람과 넣으면 될 금액).
        val biz = due.first { it.kind == BossFilings.Kind.BIZ_STATEMENT }
        assertEquals(0L, biz.amount)
        assertEquals(both.filter { it.biz }.sumOf { it.gross }, biz.missing!!.amount)
        assertFalse(biz.detail, biz.detail.contains("0명"))
        // 이번 달(11월에 일한 몫)은 대장처럼 모두 넣어요: 빠진 사람이 없어요.
        assertTrue(due.filter { it.worked == YearMonth.of(2026, 11) }.all { it.missing == null })
        // 원천세 표(CSV)도 카드와 같은 사람만.
        val filed = BossFilings.filed(ledgerOf(YearMonth.of(2026, 10)), YearMonth.of(2026, 10), LocalDate.of(2026, 11, 12))
        assertEquals(listOf("d"), filed.lines.filter { it.gross > 0 }.map { it.staff.id })
        // 입사일을 넣으면 빠진 사람 말이 사라지고 금액이 대장과 같아요.
        val dated = listOf(three.copy(hiredOn = LocalDate.of(2026, 9, 1)), day)
        val again = BossFilings.upcoming(open, dated, LocalDate.of(2026, 11, 12), ledgerOf = { m -> PayrollLedger.month(open, dated, m, emptyList()) })
        val tax2 = again.first { it.kind == BossFilings.Kind.WITHHOLD && it.key.endsWith(":2026-11") }
        assertEquals(null, tax2.missing)
        assertEquals(both.sumOf { it.tax + it.local }, tax2.amount)
    }

    /** [RVJ-39] 검토 PC-101 (통합 2차 PC Q17): '10월 지급분'이 9월에 일한 몫인지 카드에 없었어요. */
    @Test fun aPaidMonthSaysWhichMonthsWorkItIs() {
        val s = StaffMember("a", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 1, 5), weekly = week, insurance = Insurance.ALL)
        val next = Workplace("p", "식당", fivePlus = true)
        val due = BossFilings.upcoming(next, listOf(s), LocalDate.of(2026, 11, 12), ledgerOf = { m -> PayrollLedger.month(next, listOf(s), m, emptyList()) })
        val tax = due.first { it.kind == BossFilings.Kind.WITHHOLD && it.key.endsWith(":2026-11") }
        assertEquals("원천세 신고·납부 (11월 지급분 · 10월 일한 몫)", tax.title)
        assertEquals(YearMonth.of(2026, 10), tax.worked)
        // 그달에 주는 가게는 같은 달이라 덧붙이지 않아요, 원천세 표도 그 달이에요.
        val same = Workplace("p", "식당", fivePlus = true, payday = 31, payNextMonth = false)
        val due2 = BossFilings.upcoming(same, listOf(s), LocalDate.of(2026, 11, 12), ledgerOf = { m -> PayrollLedger.month(same, listOf(s), m, emptyList()) })
        val tax2 = due2.first { it.kind == BossFilings.Kind.WITHHOLD }
        assertEquals("원천세 신고·납부 (10월 지급분)", tax2.title)
        assertEquals(YearMonth.of(2026, 10), tax2.worked)
    }

    /**
     * [RVJ-39] 근로소득 간이지급명세서: 2026년 지급분까지 반기(하반기분 이듬해 1월 말일), 2027년 1월 지급분부터 매달(다음 달 말일). 반기 줄은
     * 반기 중에 그만둔 사람도 넣어요(카드가 읽는 다섯 달 대장에 없어도).
     */
    @Test fun theWageBriefGoesByHalvesUntil2026AndMonthlyFrom2027() {
        val stays = StaffMember("a", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 1, 5), weekly = week, insurance = Insurance.ALL)
        val left = StaffMember("b", "최사아", base = 10_320, hiredOn = LocalDate.of(2026, 1, 5), leftOn = LocalDate.of(2026, 8, 31), weekly = week, insurance = Insurance.ALL)
        val staff = listOf(stays, left)
        val p = Workplace("p", "식당", fivePlus = true)
        val ledgerOf = { m: YearMonth -> PayrollLedger.month(p, staff, m, emptyList()) }
        // 12월 22일에는 아직(40일 앞까지만), 12월 23일부터 2026년 하반기분(2027-01-31 일요일 → 2월 1일)이 보여요.
        assertTrue(BossFilings.upcoming(p, staff, LocalDate.of(2026, 12, 22), ledgerOf).none { it.kind == BossFilings.Kind.WAGE_BRIEF })
        val half = BossFilings.upcoming(p, staff, LocalDate.of(2026, 12, 23), ledgerOf).single { it.kind == BossFilings.Kind.WAGE_BRIEF }
        assertEquals(LocalDate.of(2027, 2, 1), half.date)
        assertEquals("근로소득 간이지급명세서 (2026년 하반기(7~12월 지급분))", half.title)
        assertEquals(listOf("a", "b"), half.staffIds)
        // 2027년 1월 지급분(2026년 12월에 일한 몫): 2월 말일이 일요일, 3월 1일이 삼일절이라 3월 2일, 매달.
        val monthly = BossFilings.upcoming(p, staff, LocalDate.of(2027, 2, 10), ledgerOf).filter { it.kind == BossFilings.Kind.WAGE_BRIEF }
        val jan = monthly.single { it.key.endsWith(":2027-01") }
        assertEquals(LocalDate.of(2027, 3, 2), jan.date)
        assertEquals("근로소득 간이지급명세서 (1월 지급분 · 12월 일한 몫)", jan.title)
        assertTrue(jan.detail, jan.detail.contains("반기 기한(7월·1월 말일)까지 내도 가산세가 없어요"))
    }

    /** [RVJ-12] 검토 PAYC-07: 주 20시간 월급은 104시간으로 통상시급·최저임금을 봐요 (209시간이 아니에요). */
    @Test fun partTimeMonthlyUsesItsOwnHours() {
        val four = StaffShift(LocalTime.of(9, 0), LocalTime.of(13, 30)) // 휴게 30분 빼고 4시간
        val s = StaffMember("m", "김월급", form = WageForm.MONTH, base = 1_100_000, hiredOn = LocalDate.of(2026, 1, 1),
            weekly = DayOfWeek.entries.take(5).associateWith { four })
        assertEquals(104, PayrollLedger.monthlyHours(place, s))
        // 1,100,000 ÷ 104 = 10,576원 ≥ 최저임금: no 최저임금 warning (it read ÷209 = 5,263원 before).
        val line = PayrollLedger.month(place, listOf(s), nov, emptyList()).lines.single()
        assertTrue(line.warnings.none { it.key == "min" })
        // 일급 6시간: 60,000원 ÷ 6시간 = 10,000원 < 10,320원 → warning (÷8 = 7,500원 was the old reading, also below, but now right).
        val day6 = StaffMember("d", "박일급", form = WageForm.DAY, base = 63_000, hiredOn = LocalDate.of(2026, 1, 1),
            weekly = mapOf(DayOfWeek.MONDAY to StaffShift(LocalTime.of(9, 0), LocalTime.of(15, 30))))
        assertEquals(6 * 60, PayrollLedger.dailyMinutes(place, day6))
        assertTrue(PayrollLedger.month(place, listOf(day6), nov, emptyList()).lines.single().warnings.none { it.key == "min" })
    }

    /** [RVJ-10] 검토 PAYC-08·PAYU-14: 식대는 비과세라 소득세·보험이 그만큼 줄고, 가족 수가 소득세에 들어가요. */
    @Test fun taxFreeMealAndFamilyReachTheSlip() {
        val base = StaffMember("a", "박마바", form = WageForm.MONTH, base = 3_000_000, hiredOn = LocalDate.of(2026, 1, 1), weekly = week,
            insurance = Insurance.ALL)
        val withMeal = base.copy(allowances = listOf(StaffAllowance("식대", 200_000)))
        assertTrue(withMeal.allowances.single().taxFree)
        val taxedMeal = base.copy(allowances = listOf(StaffAllowance("식대", 200_000, taxFree = false)))
        val free = Payslip.of(place, PayrollLedger.month(place, listOf(withMeal), nov, emptyList()).lines.single())
        val taxed = Payslip.of(place, PayrollLedger.month(place, listOf(taxedMeal), nov, emptyList()).lines.single())
        assertEquals(free.gross, taxed.gross)
        assertTrue("비과세 식대면 떼는 돈이 적어요", free.deducted < taxed.deducted)
        val four = base.copy(insurance = Insurance.ALL.copy(family = 4, children = 2))
        val one = Payslip.of(place, PayrollLedger.month(place, listOf(base), nov, emptyList()).lines.single())
        val fam = Payslip.of(place, PayrollLedger.month(place, listOf(four), nov, emptyList()).lines.single())
        assertTrue(fam.deducted < one.deducted)
    }

    /** [RVJ-15] 검토 PAYU-13: 임금대장 CSV의 공제액 칸이 명세서와 같은 값이에요 (늘 비어 있었어요). */
    @Test fun ledgerCsvHasTheDeductions() {
        val s = StaffMember("a", "박마바", form = WageForm.MONTH, base = 2_500_000, hiredOn = LocalDate.of(2026, 1, 1), weekly = week, insurance = Insurance.ALL)
        val ledger = PayrollLedger.month(place, listOf(s), nov, emptyList())
        val slip = Payslip.of(place, ledger.lines.single())
        val cells = LedgerCsv.cells(ledger.lines.single(), place)
        assertEquals(slip.deducted.toString(), cells[LedgerCsv.HEADER.indexOf("공제액")])
        assertTrue(slip.deducted > 0)
        assertTrue(LedgerCsv.write(ledger).lines().any { it.startsWith("합계") && it.contains(",${slip.deducted},") })
    }

    /** [RVJ-11] 검토 PAYC-22·PAYC-10: 일용직 소득세는 하루마다 15만 원을 빼고 계산한 값이 명세서와 계산기에서 같아요. */
    @Test fun dailyWorkerTaxIsTheSameEverywhere() {
        val d = StaffMember("d", "이일용", form = WageForm.DAY, base = 200_000, hiredOn = LocalDate.of(2026, 11, 1),
            weekly = mapOf(DayOfWeek.SATURDAY to nine), insurance = Insurance(tax = StaffTax.DAILY))
        val line = PayrollLedger.month(place, listOf(d), nov, emptyList()).lines.single()
        val slip = Payslip.of(place, line)
        val days = line.pay!!.days.map { day -> day.parts.sumOf { it.amount } }.filter { it > 0 }
        val want = DailyWorkerTax.paidTogether(days)
        assertEquals(want.income, slip.deductions.toMap()[com.bamnat.core.pay.TakeHome.TAX_LABEL] ?: 0L)
        assertTrue(want.income > 0)
    }

    /** [RVJ-16] 검토 BOSS-60·BOSS-74: 명단으로 넣은 주 40시간 직원은 4대보험이 채워지고, 3.3%·일용·외국인은 까닭을 말해요. */
    @Test fun importAndInsuranceRules() {
        val row = StaffRow("박마바", base = 10_320, form = WageForm.HOUR, weekly = week.mapValues { (_, t) -> t.start to t.end })
        val line = StaffImport.lines(listOf(row), emptyList(), LocalDate.of(2026, 11, 12), { "x" }).single()
        assertTrue(line.member.insurance.pension && line.member.insurance.health && line.member.insurance.employment)
        val biz = line.member.copy(insurance = Insurance.ALL.copy(tax = StaffTax.BIZ))
        val r = InsuranceRules.of(place, biz, nov)
        assertFalse(r.effective.any)
        assertTrue(r.reasons.any { it.contains("3.3%") })
        val foreign = line.member.copy(insurance = Insurance.ALL.copy(foreign = true))
        assertTrue(InsuranceRules.of(place, foreign, nov).reasons.any { it.contains("외국인") })
        // 일급도 주 15시간 미만이면 국민연금·건강보험 대상이 아니에요 (시급만 보던 것).
        val shortDay = line.member.copy(form = WageForm.DAY, base = 80_000, weekly = mapOf(DayOfWeek.SATURDAY to nine))
        assertFalse(InsuranceRules.of(place, shortDay, nov).effective.pension)
    }

    /** [RVJ-17] 검토 BOSS-52·54·55: 공휴일 유급 줄, 주 7일이면 주휴일을 먼저 묻고, 18세 미만 안내·경고, 사업주 칸. */
    @Test fun contractHolidaysMinorsAndOwner() {
        val s = StaffMember("s", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 11, 2), job = "홀", weekly = week)
        val c = StaffContract.of(place.copy(owner = "김사장", address = "서울시 어딘가 1"), s)
        val body = c.items.associate { it.title to it.body }
        assertTrue(body.getValue("휴일"), body.getValue("휴일").contains("공휴일"))
        assertTrue(body.getValue("휴일").contains("노동절"))
        val doc = StaffContract.doc(place.copy(owner = "김사장", address = "서울시 어딘가 1"), s, c)
        assertNotNull(doc)
        val seven = s.copy(weekly = DayOfWeek.entries.associateWith { nine })
        assertTrue(StaffContract.of(place, seven).missing.any { it.startsWith("주휴일") })
        val young = s.copy(birth = LocalDate.of(2010, 3, 1), weekly = week + (DayOfWeek.SATURDAY to StaffShift(LocalTime.of(20, 0), LocalTime.of(23, 30))))
        val yc = StaffContract.of(place, young)
        assertTrue(yc.items.any { it.title == "18세 미만 근로자" })
        assertTrue(yc.warn.any { it.contains("7시간") })
        assertTrue(yc.warn.any { it.contains("밤 10시") })
    }

    /** [RVJ-41] 검토 PC-103: 급여 주는 날 '말일'(31)은 계약서에도 '매월 말일', 명세서는 그 달의 마지막 날. */
    @Test fun aLastDayPaydayIsSaidAsTheLastDay() {
        val s = StaffMember("s", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 11, 2), job = "홀", weekly = week)
        val last = place.copy(payday = 31, payNextMonth = false)
        val pay = StaffContract.of(last, s).items.first { it.title == "임금" }.body
        assertTrue(pay, pay.contains("지급일: 매월 말일 (그달 것을 그달에"))
        assertFalse(pay, pay.contains("31일"))
        val tenth = StaffContract.of(place, s).items.first { it.title == "임금" }.body
        assertTrue(tenth, tenth.contains("지급일: 매월 10일"))
        assertEquals(LocalDate.of(2026, 11, 30), last.payDate(nov))
    }

    /** [RVJ-44] 검토 PC-106: 5인 미만 가게의 대장 '확인 필요'에 '회사 규칙'이 없어요; 가게 낱말 검사도 그 말을 잡아요. */
    @Test fun aSmallShopsLedgerSaysNoCompanyRules() {
        val small = Workplace("p", "가나다카페", fivePlus = false, payday = 10)
        val s = StaffMember("s", "박마바", base = 10_320, hiredOn = LocalDate.of(2026, 1, 5), weekly = DayOfWeek.entries.take(6).associateWith { nine })
        val line = PayrollLedger.month(small, listOf(s), nov, emptyList()).lines.single()
        val said = line.warnings.joinToString(" / ") { it.text }
        assertTrue(said, said.contains("5인 미만 사업장이라 따로 정하지 않은 연장·야간·휴일 가산은 계산하지 않았어요"))
        assertEquals(said, emptyList<String>(), com.bamnat.core.work.TypeWords.leaks(com.bamnat.core.work.WorkStyle.BUSINESS, said))
        assertEquals(listOf("회사 규칙"), com.bamnat.core.work.TypeWords.leaks(com.bamnat.core.work.WorkStyle.BUSINESS, "회사 규칙에 넣지 않은 가산"))
        assertTrue(com.bamnat.core.work.TypeWords.leaks(com.bamnat.core.work.WorkStyle.BUSINESS, "회사 규칙 광장").isEmpty())
    }

    /**
     * [RVJ-42] 검토 PC-104 (통합 2차 PC Q19): 월급 2,500,000원 직원을 월~토 48시간으로 두면 대장은 연장을 더하고 계약서는 '월급 2,500,000원 ·
     * 소정 48시간'이었어요. 한 번 묻고, 대장·계약서·명세서가 같은 말을 해요.
     */
    @Test fun aMonthlyPayPastFortyHoursIsAskedOnceAndSaidTheSameEverywhere() {
        val six = DayOfWeek.entries.take(6).associateWith { nine }
        val m = StaffMember("m", "박매니저", form = WageForm.MONTH, base = 2_500_000, hiredOn = LocalDate.of(2026, 1, 5), job = "매니저",
            weekly = six, insurance = Insurance.ALL)
        for (five in listOf(true, false)) {
            val p = place.copy(fivePlus = five)
            val sp = com.bamnat.core.boss.MonthlyOvertime.of(p, m)!!
            assertEquals(48 * 60, sp.weekMinutes)
            assertEquals(8 * 60, sp.overMinutes)
            assertEquals(209, sp.hours)
            assertEquals(if (five) 1.5 else 1.0, sp.rate, 0.0)
            assertEquals(2_500_000L, sp.base + sp.fixed)
            // 아직 안 물었으면: 더 주고, 대장과 계약서가 그렇다고 말해요.
            val asked = PayrollLedger.month(p, listOf(m), nov, emptyList()).lines.single()
            assertTrue("$five ${asked.gross}", asked.gross > 2_500_000)
            assertTrue(asked.warnings.any { it.key == "monthly-ot" && it.text.startsWith("한 주 48시간 월급이에요 · 40시간을 넘는 주 8시간을") })
            val ca = StaffContract.of(p, m)
            val hours = ca.items.first { it.title == "근무 요일과 시간" }.body
            assertTrue(hours, hours.contains("한 주 소정근로시간 40시간, 그 밖의 평소 근무 한 주 8시간은 연장근로"))
            assertTrue(hours, hours.contains(if (five) "근로기준법 제53조" else "5명 미만 사업장이라"))
            assertTrue(ca.items.first { it.title == "임금" }.body.contains("월급 2,500,000원 (한 주 40시간 몫, 월 209시간)"))
            assertTrue(ca.warn.any { it.contains("아직 안 골랐어요") })
            // '더 줘요'로 고르면 물음이 사라지고 금액은 그대로.
            val more = m.copy(overtimeInPay = false)
            val paidMore = PayrollLedger.month(p, listOf(more), nov, emptyList()).lines.single()
            assertEquals(asked.gross, paidMore.gross)
            assertTrue(paidMore.warnings.none { it.key == "monthly-ot" })
            assertTrue(StaffContract.of(p, more).warn.none { it.contains("아직 안 골랐어요") })
            // '월급에 들어 있어요': 11월(월~토 네 주, 연장 32시간)은 고정 몫(한 달 약 34.8시간)보다 적어 월급 그대로, 명세서는 둘로 나눠요.
            val inPay = m.copy(overtimeInPay = true)
            val l = PayrollLedger.month(p, listOf(inPay), nov, emptyList()).lines.single()
            assertEquals("$five ${l.lines.map { it.label to it.amount }}", 2_500_000L, l.gross)
            assertTrue(l.lines.any { it.label.contains(PayrollLedger.FIXED_OT) && it.amount == sp.fixed })
            assertTrue(l.warnings.none { it.key == "monthly-ot" })
            // [RVJ-60] J-488 ★13: 근거는 계약서처럼 두 줄(기본급 + 고정 연장근로수당). 음수 줄·연장 줄이 없고, 기본급은 넣은 월급에서 나와요.
            assertEquals("$five ${l.lines.map { it.label }}", listOf("기본급", PayrollLedger.FIXED_OT), l.lines.map { it.label })
            assertTrue(l.lines.none { it.amount < 0 })
            assertTrue(l.lines[0].formula, l.lines[0].formula.startsWith("월급 2,500,000원 − 고정 연장근로수당 ${"%,d원".format(sp.fixed)}"))
            assertTrue(l.lines[1].formula, l.lines[1].formula.contains("이번 달 연장근로 32시간은 이 안이에요"))
            val pay = StaffContract.of(p, inPay).items.first { it.title == "임금" }.body
            assertTrue(pay, pay.contains("월급 2,500,000원 (기본급 ${"%,d원".format(sp.base)} + 고정 연장근로수당 ${"%,d원".format(sp.fixed)})"))
            assertTrue(pay, pay.contains("실제 연장근로가 더 많은 달은 차액을 더 줘요"))
            // 연장이 고정 몫보다 많은 달(둘째 주를 날마다 밤 10시까지)은 차액을 더해요(대법원 2008다6052).
            val late = (9..14).map { d -> com.bamnat.core.boss.StaffWork("m", LocalDate.of(2026, 11, d), com.bamnat.core.boss.WorkKind.NORMAL, LocalTime.of(9, 0), LocalTime.of(22, 0)) }
            val more2 = PayrollLedger.month(p, listOf(inPay), nov, late).lines.single()
            assertTrue("$five ${more2.gross}", more2.gross > 2_500_000)
            // [RVJ-60] 더 일한 달에만 차액 한 줄.
            val gap = more2.lines.single { it.label == "연장근로 차액" }
            assertEquals(more2.gross - 2_500_000, gap.amount)
            assertTrue(gap.formula, gap.formula.endsWith("− 고정 연장근로수당 ${"%,d원".format(sp.fixed)}"))
            assertEquals(listOf("기본급", PayrollLedger.FIXED_OT, "연장근로 차액"), more2.lines.map { it.label })
        }
        // 40시간 안이면 묻지 않아요.
        assertEquals(null, com.bamnat.core.boss.MonthlyOvertime.of(place, m.copy(weekly = week)))
    }
}
