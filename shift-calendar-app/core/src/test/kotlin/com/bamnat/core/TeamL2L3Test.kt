package com.bamnat.core

import com.bamnat.core.team.Skill
import com.bamnat.core.team.Team
import com.bamnat.core.team.TeamAuto
import com.bamnat.core.team.TeamBoard
import com.bamnat.core.team.TeamCheck
import com.bamnat.core.team.TeamMember
import com.bamnat.core.team.TeamShift
import com.bamnat.core.team.TeamWish
import com.bamnat.core.team.TeamWishText
import com.bamnat.core.team.WishKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.YearMonth

/** 1.38 [P3-04] L2 바로 검사·공정성·교환 검사·희망 휴무 글, L3 자동 채우기. */
class TeamL2L3Test {
    private val nov = YearMonth.of(2026, 11)
    private val shifts = listOf(
        TeamShift(1, "D", "데이", 0xFF2196F3, false, LocalTime.of(7, 0), LocalTime.of(15, 0)),
        TeamShift(2, "E", "이브닝", 0xFF4CAF50, false, LocalTime.of(15, 0), LocalTime.of(23, 0)),
        TeamShift(3, "N", "나이트", 0xFF673AB7, false, LocalTime.of(23, 0), LocalTime.of(7, 0)),
        TeamShift(4, "O", "오프", 0xFF9E9E9E, true),
    )
    private fun team(need: Map<Long, Int> = emptyMap()) = Team("t", "3병동", shifts = shifts, need = need)
    private fun people(n: Int) = (0 until n).map { TeamMember("m$it", "t", "간호$it", sort = it, skill = if (it == 0) Skill.CHARGE else Skill.REG) }

    @Test
    fun `N then D is a rule sign on the member, a short day on the day`() {
        val b = TeamBoard(nov, people(2)).paint("m1", nov.atDay(3), 3).paint("m1", nov.atDay(4), 1)
        val signs = TeamCheck.check(team(mapOf(1L to 1)), b)
        assertTrue(signs.any { it.kind == TeamCheck.Kind.RULE && it.memberId == "m1" && it.date == nov.atDay(4) })
        assertTrue(signs.any { it.kind == TeamCheck.Kind.SHORT && it.date == nov.atDay(1) && it.text == "D 1명 부족" })
        // m1 works D on the 4th without the 책임 m0.
        assertTrue(signs.any { it.kind == TeamCheck.Kind.NO_CHARGE && it.date == nov.atDay(4) })
    }

    @Test
    fun `more than 52 hours in a week is told`() {
        var b = TeamBoard(nov, people(1))
        // Mon 2 .. Sun 8: seven 8-hour days = 56 hours.
        (2..8).forEach { b = b.paint("m0", nov.atDay(it), 1) }
        assertTrue(TeamCheck.check(team(), b).any { it.kind == TeamCheck.Kind.WEEK_HOURS })
    }

    @Test
    fun `fairness counts nights weekends and offs`() {
        val b = TeamBoard(nov, people(2)).paint("m0", nov.atDay(1), 3).paint("m0", nov.atDay(2), 4).paint("m1", nov.atDay(7), 1)
        val f = TeamCheck.fairness(team(), b).associateBy { it.member.id }
        assertEquals(1, f.getValue("m0").nights)
        assertEquals(1, f.getValue("m0").weekends) // 11월 1일 is a Sunday.
        assertEquals(1, f.getValue("m0").offs)
        assertEquals(1, f.getValue("m1").weekends)
    }

    @Test
    fun `a swap that makes N then D is caught before it is made`() {
        // m0 N on the 3rd; m1 D on the 4th, m0 O on the 4th: trading the 4th gives m0 N→D.
        val b = TeamBoard(nov, people(2)).paint("m0", nov.atDay(3), 3).paint("m0", nov.atDay(4), 4).paint("m1", nov.atDay(4), 1)
        val news = TeamCheck.swapCheck(team(), b, "m0", "m1", nov.atDay(4))
        assertTrue(news.any { it.memberId == "m0" && it.kind == TeamCheck.Kind.RULE })
    }

    @Test
    fun `wish text goes both ways and reads a leader's note`() {
        val m = people(3)
        val mine = listOf(TeamWish("m1", nov.atDay(3)), TeamWish("m1", nov.atDay(15)), TeamWish("m1", nov.atDay(20), WishKind.AVOID_NIGHT))
        val text = TeamWishText.write(nov, mine, "간호1")
        assertEquals("[밤낮 희망] 간호1 11월 3 15 쉬고 / 20 나이트 빼고", text)
        assertEquals(mine.toSet(), TeamWishText.read(text, nov, m).toSet())
        // A leader's own line, a day past the month's end left out.
        assertEquals(listOf(TeamWish("m2", nov.atDay(7)), TeamWish("m2", nov.atDay(9))), TeamWishText.read("간호2 7, 9, 31", nov, m))
        // A line without a name goes to the member it was received for.
        assertEquals(listOf(TeamWish("m0", nov.atDay(5))), TeamWishText.read("5일 오프", nov, m, fallback = "m0"))
    }

    @Test
    fun `auto fill takes only empty cells, keeps wishes and rules and fills the need`() {
        val ppl = people(8)
        val t = team(mapOf(1L to 2, 2L to 2, 3L to 1))
        val locked = TeamBoard(nov, ppl).paint("m3", nov.atDay(1), 3).lock("m3", nov.atDay(1), true)
        val wishes = listOf(TeamWish("m1", nov.atDay(2)), TeamWish("m2", nov.atDay(5), WishKind.AVOID_NIGHT))
        val started = System.nanoTime()
        val plan = TeamAuto.plans(t, locked, wishes).first()
        assertTrue("fast", System.nanoTime() - started < 5_000_000_000L)
        val b = plan.board
        assertEquals(3L, b.cell("m3", nov.atDay(1))?.shiftId)
        assertTrue(b.cell("m3", nov.atDay(1))!!.locked)
        assertEquals(4L, b.cell("m1", nov.atDay(2))?.shiftId)
        assertTrue(b.cell("m2", nov.atDay(5))?.shiftId != 3L)
        assertEquals(0, b.empty)
        assertEquals(2, plan.wishesKept)
        // No N→D anywhere, and every day has its people.
        assertTrue(TeamCheck.check(t, b).none { it.kind == TeamCheck.Kind.RULE && it.text.contains("휴식") && it.text.contains("N") })
        assertTrue(plan.short.isEmpty())
    }

    @Test
    fun `too few people is told as short, never forced`() {
        val plan = TeamAuto.fill(team(mapOf(1L to 3)), TeamBoard(nov, people(2)))
        assertEquals(nov.lengthOfMonth(), plan.short.size)
        assertTrue(plan.short.values.all { it[1L] == 1 })
    }

    @Test
    fun `thirty people over thirty-one days stays quick`() {
        val t = team(mapOf(1L to 7, 2L to 7, 3L to 5))
        val started = System.nanoTime()
        TeamAuto.plans(t, TeamBoard(YearMonth.of(2026, 12), (0 until 30).map { TeamMember("p$it", "t", "사람$it", sort = it) }))
        assertTrue("30×31 under 5 s", System.nanoTime() - started < 5_000_000_000L)
    }
}

/** 1.38 [BOSS-08..10] B2 명세서·공제·지난달 비교. */
class PayslipTest {
    private val nov = YearMonth.of(2026, 11)
    private val place = com.bamnat.core.boss.Workplace("w", "가나다카페", fivePlus = false, payday = 10)
    private fun staff(ins: com.bamnat.core.boss.Insurance) = com.bamnat.core.boss.StaffMember("s", "이다라", base = 10_320,
        weekly = java.time.DayOfWeek.entries.take(5).associateWith { com.bamnat.core.work.StaffShift(LocalTime.of(9, 0), LocalTime.of(18, 0)) }, insurance = ins)

    @Test
    fun `a slip has every required item and only the insurance the staff is in`() {
        val line = com.bamnat.core.boss.PayrollLedger.line(place, staff(com.bamnat.core.boss.Insurance(employment = true)), nov, emptyList())
        val slip = com.bamnat.core.boss.Payslip.of(place, line)
        assertTrue(slip.ready)
        assertEquals(java.time.LocalDate.of(2026, 12, 10), slip.payDate)
        val names = slip.deductions.map { it.first }
        assertTrue("고용보험" in names)
        assertTrue("국민연금" !in names && "건강보험" !in names)
        assertEquals(slip.gross - slip.deducted, slip.net)
        assertTrue(slip.text().contains("실지급액"))
        assertEquals("2026년 11월 임금명세서", slip.doc().title)
    }

    @Test
    fun `3_3 percent for a freelancer, nothing for none`() {
        val biz = com.bamnat.core.boss.Payslip.of(place, com.bamnat.core.boss.PayrollLedger.line(place,
            staff(com.bamnat.core.boss.Insurance(tax = com.bamnat.core.boss.StaffTax.BIZ)), nov, emptyList()))
        assertEquals(listOf("원천징수 3.3%"), biz.deductions.map { it.first })
        val none = com.bamnat.core.boss.Payslip.of(place, com.bamnat.core.boss.PayrollLedger.line(place,
            staff(com.bamnat.core.boss.Insurance(tax = com.bamnat.core.boss.StaffTax.NONE)), nov, emptyList()))
        assertEquals(none.gross, none.net)
    }

    @Test
    fun `a slip without a payday is not ready and says where to fix it`() {
        val p = place.copy(payday = null)
        val slip = com.bamnat.core.boss.Payslip.of(p, com.bamnat.core.boss.PayrollLedger.line(p, staff(com.bamnat.core.boss.Insurance()), nov, emptyList()))
        assertTrue(!slip.ready)
        assertEquals("임금지급일", slip.checks.first { !it.ok }.name)
    }

    @Test
    fun `last month compare says why`() {
        val s = staff(com.bamnat.core.boss.Insurance())
        val now = com.bamnat.core.boss.PayrollLedger.month(place, listOf(s), nov, emptyList())
        val before = com.bamnat.core.boss.PayrollLedger.month(place, listOf(s), nov.minusMonths(1), emptyList())
        val row = com.bamnat.core.boss.PayrollCompare.rows(now, before).single()
        assertTrue(row.before != null)
        assertTrue(row.why.isNotBlank())
        assertEquals("지난달 기록 없음", com.bamnat.core.boss.PayrollCompare.rows(now, null).single().why)
    }
}

/** 1.38 [BOSS-11] 4대보험 가입 대상 자동 판단 (공식 대조 G2·G3·G4·G6). */
class InsuranceRulesTest {
    private val nov = YearMonth.of(2026, 11)
    private val place = com.bamnat.core.boss.Workplace("w", "카페", fivePlus = false, payday = 10)
    private fun shift(h: Int) = com.bamnat.core.work.StaffShift(LocalTime.of(9, 0), LocalTime.of(9 + h, 0))
    private fun staff(days: Int, hours: Int, hired: java.time.LocalDate? = null, birth: java.time.LocalDate? = null) = com.bamnat.core.boss.StaffMember(
        "s", "가", base = 10_320, hiredOn = hired, birth = birth, insurance = com.bamnat.core.boss.Insurance.ALL,
        weekly = java.time.DayOfWeek.entries.take(days).associateWith { shift(hours) })

    @Test
    fun `under 15 hours a week is out of pension health and new employment`() {
        // 3일 × 4시간(휴게 30분 빼면 3.5시간) = 10.5시간.
        val r = com.bamnat.core.boss.InsuranceRules.of(place, staff(3, 4, hired = nov.atDay(1)), nov)
        assertEquals(com.bamnat.core.boss.Insurance(), r.effective)
        assertEquals(2, r.reasons.count { it.contains("15시간") })
        // After three months the 고용보험 comes back.
        val later = com.bamnat.core.boss.InsuranceRules.of(place, staff(3, 4, hired = nov.minusMonths(3).atDay(1)), nov)
        assertEquals(com.bamnat.core.boss.Insurance(employment = true), later.effective)
    }

    @Test
    fun `age rules for pension and employment`() {
        val sixty = com.bamnat.core.boss.InsuranceRules.of(place, staff(5, 8, birth = java.time.LocalDate.of(1965, 1, 1)), nov)
        assertTrue(!sixty.effective.pension && sixty.effective.health)
        val late = com.bamnat.core.boss.InsuranceRules.of(place, staff(5, 8, hired = java.time.LocalDate.of(2026, 3, 1), birth = java.time.LocalDate.of(1960, 1, 1)), nov)
        assertTrue(!late.effective.employment)
        val full = com.bamnat.core.boss.InsuranceRules.of(place, staff(5, 8, birth = java.time.LocalDate.of(1995, 1, 1)), nov)
        assertEquals(com.bamnat.core.boss.Insurance.ALL, full.effective)
    }

    @Test
    fun `a slip leaves out what the law leaves out, and health pays on at least 28만 원`() {
        val short = staff(3, 4, hired = nov.atDay(1))
        val slip = com.bamnat.core.boss.Payslip.of(place, com.bamnat.core.boss.PayrollLedger.line(place, short, nov, emptyList()))
        assertTrue(slip.deductions.none { it.first in setOf("국민연금", "건강보험", "고용보험") })
        // A 월급 of 20만 원 with 건강보험: the 보험료 is on 28만 원.
        val one = com.bamnat.core.boss.StaffMember("o", "나", form = com.bamnat.core.boss.WageForm.MONTH, base = 200_000,
            insurance = com.bamnat.core.boss.Insurance(health = true), hiredOn = java.time.LocalDate.of(2026, 1, 1),
            weekly = java.time.DayOfWeek.entries.take(5).associateWith { shift(8) })
        val l = com.bamnat.core.boss.PayrollLedger.line(place, one, nov, emptyList())
        assertTrue(l.gross in 1 until 280_000)
        val s = com.bamnat.core.boss.Payslip.of(place, l)
        assertEquals((280_000 * com.bamnat.core.pay.TakeHome.sharesOn(nov.atEndOfMonth()).health).toLong() / 10 * 10,
            s.deductions.first { it.first == "건강보험" }.second)
    }
}
