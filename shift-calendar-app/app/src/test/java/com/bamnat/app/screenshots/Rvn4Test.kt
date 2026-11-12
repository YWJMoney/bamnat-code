package com.bamnat.app.screenshots

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.PutOnNotice
import com.bamnat.app.ui.components.AppSnack
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import com.bamnat.core.time.Today
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.time.LocalDate
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp

/**
 * 2.0.0 [RVN-18] 검토 COMP-23 (위험한 곳인데 시험이 없어요): 광장 시세를 내 급여에 넣기와 그 되돌리기(COMP-06), ✓ 맞아요를 다시 눌러
 * 거두기(COMP-22-1), 공개 규정 값 넣기와 되돌리기(COMP-16). Each 되돌리기 takes back only what it put in, and leaves a pay changed since.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class Rvn4Test : ScreenshotBase() {
    /** The line [act] puts on the app's snackbar with its 되돌리기 (listened for before it is said). */
    private fun noteOf(act: () -> Unit): PutOnNotice.Note {
        val got = CompletableDeferred<PutOnNotice.Note>()
        val job = CoroutineScope(Dispatchers.Unconfined).launch { got.complete(PutOnNotice.notes.first()) }
        act()
        assertTrue("nothing was said", waitUntil { got.isCompleted })
        job.cancel()
        return runBlocking { got.await() }
    }

    private fun daily(c: com.bamnat.app.AppContainer, base: Long) = runBlocking {
        c.payStore.save(com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile().copy(wageType = WageType.DAILY,
            base = ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), base)))), keepUndo = false)
    }

    private fun baseOn(c: com.bamnat.app.AppContainer, day: LocalDate) = c.payStore.profile.value!!.base.on(day).value

    @Test fun aRateGoesInFromThisMonthAndUndoTakesBackOnlyItsOwn() {
        val c = companyOn()
        daily(c, 150_000)
        val vm = com.bamnat.app.ui.company.RatesViewModel(c)
        assertTrue(waitUntil { vm.state.value.board?.groups?.any { it.unit == "day" } == true })
        val g = vm.state.value.board!!.groups.first { it.unit == "day" }
        val (plan, why) = vm.planApply(g)
        assertNull(why)
        plan!!
        assertEquals(150_000L, plan.before)
        assertEquals(g.mid, plan.after)
        assertEquals("일당", plan.what)
        assertEquals(Today.month().atDay(1), plan.from)
        // Before this month the pay stays as it was (지난 달 급여는 그대로).
        val lastMonth = plan.from.minusDays(1)

        val note = noteOf { vm.apply(plan) }
        assertEquals("${plan.from.monthValue}월부터 일당을 ${"%,d원".format(g.mid)}으로 바꿨어요", note.text)
        assertTrue(waitUntil { baseOn(c, plan.from) == g.mid })
        assertEquals(150_000L, baseOn(c, lastMonth))
        assertTrue(runBlocking { note.undo!!() })
        assertEquals(150_000L, baseOn(c, plan.from))

        // Put in again, then changed by hand: 되돌리기 leaves the hand's change and says it could not.
        val again = noteOf { vm.apply(plan) }
        assertTrue(waitUntil { baseOn(c, plan.from) == g.mid })
        runBlocking { c.payStore.update("손으로 고침") { it.copy(base = it.base.with(DatedValue(plan.from, 180_000))) } }
        assertFalse(runBlocking { again.undo!!() })
        assertEquals(180_000L, baseOn(c, plan.from))
    }

    @Test fun aRateOfAnotherWayOfPayIsNotPutIn() {
        val c = companyOn()
        runBlocking {
            c.payStore.save(com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile().copy(wageType = WageType.MONTHLY), keepUndo = false)
        }
        val vm = com.bamnat.app.ui.company.RatesViewModel(c)
        assertTrue(waitUntil { vm.state.value.board?.groups?.any { it.unit == "day" } == true })
        val (plan, why) = vm.planApply(vm.state.value.board!!.groups.first { it.unit == "day" })
        assertNull(plan)
        assertEquals("내 급여는 월급 방식이라 일당을 넣을 수 없어요", why)
    }

    @Test fun aSecondTapOnAgreeTakesItBack() {
        val c = companyOn()
        company.unvoted.clear()
        val vm = com.bamnat.app.ui.company.CompanyDetailViewModel(c, "c1")
        assertTrue(waitUntil { vm.state.value is com.bamnat.app.ui.company.CompanyDetailViewModel.State.Loaded })
        val page = (vm.state.value as com.bamnat.app.ui.company.CompanyDetailViewModel.State.Loaded).page
        val su = page.setups.first()
        vm.vote(page, su, true)
        assertTrue(waitUntil { vm.votes.value[su.id] == true })
        vm.takeBack(page, su)
        assertTrue(waitUntil { su.id !in vm.votes.value })
        assertEquals(listOf(su.id), company.unvoted)
        val after = (vm.state.value as com.bamnat.app.ui.company.CompanyDetailViewModel.State.Loaded).page.setups.first { it.id == su.id }
        assertNull(after.opinion.mine)
        assertEquals(2, after.persons)
        val now = android.os.SystemClock.elapsedRealtime()
        val said = generateSequence { AppSnack.queue.pickNext(now) }.map { it.message }.toList()
        assertTrue(said.toString(), "‘맞아요’를 거뒀어요" in said)
    }

    /** COMP-16 공개 규정 › 통상시급 기준 209시간 넣기: says what changed with 되돌리기, stays 넣었어요, and 되돌리기 puts back 243시간. */
    @Test fun aPublicRuleValueGoesInWithUndo() = shoot("rvn_48_public_take_folded", FOLDED) {
        val c = companyOn()
        runBlocking { c.payStore.save(com.bamnat.app.ui.pay.PayViewModel(c).sampleProfile().copy(monthlyHours = 243), keepUndo = false) }
        val setup = """{"app":"com.bamnat.app","format":"bamnat-company","v":4,"company":"서울교통공사(시험)","shiftTypes":[],""" +
            """"pay":{"fiveOrMore":true,"monthlyHours":209,"holidayMode":"FULL","withAmounts":false,"items":[]}}"""
        company.publicRecord = com.bamnat.core.plaza.PublicFirms("2026-09", "국민연금공단 국민연금 가입 사업장 내역", emptyList(),
            rules = listOf(com.bamnat.core.plaza.PublicRuleSet("서울교통공사(시험)", "보수규정", "2026-09-15", "https://www.cleaneye.go.kr/", 209,
                listOf(com.bamnat.core.plaza.PublicRuleLine("통상임금 기준 시간", "시간급 = 기본급÷209", "", "제4조")), emptyList(), setup)),
            rulesSource = "공공기관 보수규정")
        content {
            androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.padding(16.dp)) {
                com.bamnat.app.ui.company.PublicFirmCard("서울교통공사(시험)")
            }
        }
        settle()
        val note = noteOf { tapText("통상시급 기준 209시간 넣기", exact = true) }
        assertEquals("내 급여에 ‘통상시급 기준 209시간’ 값을 넣었어요", note.text)
        assertTrue(waitUntil { c.payStore.profile.value?.monthlyHours == 209 })
        assertTrue(appears("통상시급 기준 209시간 넣었어요", exact = true))
        assertTrue(runBlocking { note.undo!!() })
        assertEquals(243, c.payStore.profile.value?.monthlyHours)
        settle()
        assertTrue(appears("통상시급 기준 209시간 넣기", exact = true))
    }
}
