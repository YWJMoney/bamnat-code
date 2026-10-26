package com.bamnat.app.imports

import android.net.Uri
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ReadFixBook
import com.bamnat.app.pay.PayslipScanner
import com.bamnat.app.ui.imports.DutyRosterViewModel
import com.bamnat.app.ui.pay.PayViewModel
import com.bamnat.app.ui.pay.payNeeds
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.RosterWord
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.RuleOnlyNeeds
import com.bamnat.core.pay.RuleText
import com.bamnat.core.pay.SlipNames
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.WorkForm
import com.bamnat.core.pay.WorkForms
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.YearMonth

/**
 * [READ-13][READ-16][READ-17][READ-18][READ-19][PAYS-16][PAYS-17] 1.27 묶음 AJ 끝에서 끝 (사용자 2026-10-25 "사진과 내용이
 * 같은가요? 물어보고 다르면 … 해당 부분만 수정", "임금 규칙만 제공해도 달력 근무입력 기반 적용"): a roster photo read four ways
 * whose readings differ on one day shows it yellow, '다른 곳이 있어요' fixes that one day and it is what goes on the calendar;
 * a word fixed the same way twice is learned; a payslip line's kind taught once reads so next time; the company's rules
 * alone, with the calendar's shifts, give the month's pay and the card's lines go as they are put in.
 * ML Kit does not run here: [RosterScanner.fakeReads] and [PayslipScanner.fake] give what the made-over pictures would read.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PhotoCheckTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val nov = YearMonth.of(2026, 11)

    private fun idleUntil(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 30_000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.yield()
        }
    }

    @Before fun ready() {
        idleUntil("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
    }

    @After fun noFake() {
        RosterScanner.fakeReads = null
        PayslipScanner.fake = null
    }

    private fun types() = runBlocking { c.scheduleRepository.shiftTypes.first() }.associateBy { it.name }

    /** 이수진's 5th read 주간 by two ways and 야간 by two others: a tie, yellow. */
    private fun split(way: Int): List<RosterWord> =
        RosterPhotoTest.roster(0.0) { p, d -> if (p == 1 && d == 5 && way >= 2) "야간" else RosterPhotoTest.codeOf(p, d) }

    @Test fun aDayReadTwoWaysIsYellowAndFixedOnItsOwn() = runBlocking {
        RosterScanner.fakeReads = { (0 until 4).map(::split) }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null }
        vm.pick(1)
        val u = vm.ui.value
        assertEquals(4, u.scan!!.ways)
        assertEquals("asked 사진과 내용이 같은가요?", null, u.same)
        assertTrue("the 5th is yellow", 5 in u.doubtful)
        // Every other day read alike four ways goes in as it is.
        assertTrue(u.doubtful.size <= 2)
        // '다른 곳이 있어요' → the 5th, which the photo says is 야간.
        vm.sameAsPhoto(false)
        assertEquals(false, vm.ui.value.same)
        val night = types().getValue("야간")
        vm.edit(5, DutyCell.Shift(night.id))
        assertFalse(5 in vm.ui.value.doubtful)
        vm.sameAsPhoto(true)
        assertTrue(vm.ui.value.doubtful.isEmpty())
        vm.apply()
        idleUntil("the month put in") { vm.ui.value.applied != null }
        val snap = c.scheduleRepository.snapshot.first()
        assertEquals(night.id, snap.resolver.resolve(nov.atDay(5)).shiftType?.id)
        assertEquals(types().getValue(RosterPhotoTest.codeOf(1, 6)).id, snap.resolver.resolve(nov.atDay(6)).shiftType?.id)
    }

    @Test fun aWordFixedTheSameWayTwiceIsLearned() = runBlocking {
        // A Korean roster where two of 이수진's 야간 came out as "OF" every way: other alphabet, guessed, yellow.
        val words = RosterPhotoTest.roster(0.0) { p, d -> if (p == 1 && d in setOf(3, 9)) "OF" else RosterPhotoTest.codeOf(p, d) }
        RosterScanner.fakeReads = { List(3) { words } }
        val vm = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov)
        vm.readPhoto(Uri.parse("content://test/roster.jpg"))
        idleUntil("the photo read") { vm.ui.value.rows != null }
        vm.pick(1)
        val row = vm.ui.value.row!!
        assertTrue("OF is looked at", 3 in vm.ui.value.doubtful && 9 in vm.ui.value.doubtful)
        assertEquals("OF", row.raw[3])
        val night = types().getValue("야간")
        vm.sameAsPhoto(false)
        vm.edit(3, DutyCell.Shift(night.id))
        assertTrue("one fix teaches nothing", c.dutyCodes.of(PersonalCalendar.MAIN).isEmpty())
        vm.edit(9, DutyCell.Shift(night.id))
        assertEquals("twice: learned", DutyCell.Shift(night.id), c.dutyCodes.of(PersonalCalendar.MAIN)[DutyRosterViewModel.codeKey("OF")])
        // Next month's roster reads OF as 야간.
        val next = DutyRosterViewModel(c, PersonalCalendar.MAIN, nov.plusMonths(1))
        next.readPhoto(Uri.parse("content://test/roster2.jpg"))
        idleUntil("the next photo read") { next.ui.value.rows != null }
        next.pick(1)
        assertEquals(DutyCell.Shift(night.id), next.ui.value.cells[3])
    }

    @Test fun aPayslipReadSeveralWaysAsksAboutTheLineReadTwoWays() = runBlocking {
        val right = "2026년 9월 급여명세서\n기본급 2,508,000원\n연장근로수당 288,000원\n지급합계 2,796,000원"
        val lost = right.replace("288,000", "28,800")
        PayslipScanner.fake = { listOf(right, lost, right) }
        val scan = PayslipScanner.scan(app, Uri.parse("content://test/slip.jpg"))
        assertEquals(3, scan.ways)
        assertEquals(288_000L, scan.slip.read.lines.first { it.role == SlipRole.OVERTIME }.amount)
        // The sums vouch for it: 2,508,000 + 288,000 is the stated 지급합계.
        assertTrue(scan.slip.sure)
        // Two ways each: nothing vouches, the line is yellow.
        PayslipScanner.fake = { listOf(right.replace("지급합계 2,796,000원", ""), lost.replace("지급합계 2,796,000원", "")) }
        val unsure = PayslipScanner.scan(app, Uri.parse("content://test/slip2.jpg"))
        assertFalse(unsure.slip.sure)
        assertTrue(unsure.slip.unsure.isNotEmpty())
    }

    @Test fun aLinesKindTaughtOnceReadsSoNextTime() {
        val book = ReadFixBook.get(app)
        val before = com.bamnat.core.pay.PayslipReader.read("가족수당 100,000\n기본급 2,000,000\n지급합계 2,100,000")
        val after = before.copy(lines = before.lines.map { if (it.name.startsWith("가족")) it.copy(role = SlipRole.BONUS) else it })
        book.learnSlip(SlipNames.learned(before, after))
        val next = SlipNames.apply(com.bamnat.core.pay.PayslipReader.read("가족수당 100,000\n기본급 2,000,000\n지급합계 2,100,000"), ReadFixBook.get(app).slipNames())
        assertEquals(SlipRole.BONUS, next.lines.first { it.name.startsWith("가족") }.role)
    }

    @Test fun theCompanysRulesAloneGiveTheMonthsPay() = runBlocking {
        val since = nov.minusMonths(1).atDay(1)
        val profile = WorkForms.apply(WorkForm.TWO_SHIFT, 2_508_000, since)
        c.payStore.save(profile, keepUndo = false)
        idleUntil("the pay profile") { c.payStore.profile.value != null }
        // Three nights in November on the calendar.
        val night = types().getValue("야간")
        c.scheduleRepository.updateDays((2..4).map { nov.atDay(it) }) { e -> com.bamnat.core.imports.DutyRoster.apply(e, DutyCell.Shift(night.id)) }
        val snap = c.scheduleRepository.snapshot.first()
        val vm = PayViewModel(c)
        // The easy setup never asks 시수: it is one of the lines to put in.
        val saved = c.payStore.profile.value!!
        assertTrue(payNeeds(saved, snap, nov).any { it.kind == RuleOnlyNeeds.Kind.MONTHLY_HOURS })
        // Only the company's rules page, no payslip.
        val rules = RuleText.read("야간근로는 통상시급의 60%를 가산하여 지급한다.").map { it.change }
        assertTrue(rules.isNotEmpty())
        val res = vm.slipRules(saved, snap, PayslipRead(emptyList(), nov), rules)
        assertNotNull("rules alone are enough", res)
        assertFalse(res!!.withSlip)
        assertTrue(res.total > 2_508_000)
        assertEquals(PayCalculator().calculate(res.profile, nov, snap.resolver::resolve, checks = false).total, res.total)
        vm.applySlipRules(res)
        idleUntil("the rules kept") { c.payStore.profile.value?.rules?.nightPremium == res.profile.rules.nightPremium }
        // 시수 put in from the card: the line goes.
        vm.update { RuleOnlyNeeds.settled(it.copy(monthlyHours = 209)) }
        idleUntil("the hours kept") { c.payStore.profile.value?.let { p -> payNeeds(p, snap, nov).none { it.kind == RuleOnlyNeeds.Kind.MONTHLY_HOURS } } == true }
    }
}
