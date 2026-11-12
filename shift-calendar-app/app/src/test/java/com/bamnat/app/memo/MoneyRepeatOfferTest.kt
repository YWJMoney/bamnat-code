package com.bamnat.app.memo

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.appContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.ui.memo.MemoEditorViewModel
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVIB-08] (검토 I07ac-04) "매달 25일 월세 50만 원" in a memo: '넣기' makes it come back on the 25th and puts a 고정 지출 in the
 * 가계부, once; ↶ takes both back and ↷ puts both back; '메모로 둘게요' is not asked again.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class MoneyRepeatOfferTest {
    private val context: Application = ApplicationProvider.getApplicationContext()
    private val looper = shadowOf(android.os.Looper.getMainLooper())
    private fun until(what: () -> Boolean) {
        repeat(200) { if (what()) return; looper.idleFor(java.time.Duration.ofMillis(50)); Thread.sleep(10) }
        throw AssertionError("기다려도 오지 않았어요")
    }
    private fun fixed() = runBlocking { context.appContainer.calendars.money(PersonalCalendar.MAIN).book.first().fixed }

    @Test fun oneTapMakesTheMemoAndTheCostAndTheArrowTakesBothBack() = runBlocking<Unit> {
        val repo = context.appContainer.memoRepository
        val memo = repo.save(Memo(title = "월세", body = "매달 25일 월세 50만 원"))
        val vm = MemoEditorViewModel(context.appContainer, memo.id, null, false)
        // The offer is read while the screen watches it.
        val watch = CoroutineScope(Dispatchers.Unconfined).launch { vm.moneyOffer.collect { } }
        until { vm.loaded.value && vm.moneyOffer.value != null }
        val found = vm.moneyOffer.value!!
        assertEquals("월세", found.name)
        vm.acceptMoney(found)
        until { fixed().size == 1 }
        val cost = fixed().single()
        assertEquals(500_000L, cost.amount); assertEquals(25, cost.day); assertEquals(MoneyKind.EXPENSE, cost.kind); assertEquals("주거", cost.category)
        assertEquals("M25", vm.memo.value.repeat)
        assertEquals(25, vm.memo.value.date!!.dayOfMonth)
        until { vm.moneyOffer.value == null }
        // ↶ takes the repeat and the 고정 지출 back; ↷ puts both back.
        vm.undo()
        until { fixed().isEmpty() }
        assertEquals("", vm.memo.value.repeat)
        vm.redo()
        until { fixed().size == 1 }
        assertEquals("M25", vm.memo.value.repeat)
        // Taken again: not made twice.
        vm.undo(); until { fixed().isEmpty() }
        vm.keepMoneyAsMemo(found)
        until { vm.moneyOffer.value == null }
        assertTrue(fixed().isEmpty())
        watch.cancel()
    }

    @Test fun aTimeAnsweredStaysAnsweredWhenTheMoneyIsToo() {
        val time = com.bamnat.core.memo.MemoTime.Found(java.time.LocalDateTime.of(2026, 11, 25, 9, 0), "9시")
        val money = com.bamnat.core.memo.MoneyRepeatLine.find("매달 25일 월세 50만 원")!!
        val asked = com.bamnat.core.memo.MemoAsked.with(com.bamnat.core.memo.MemoAsked.with(null, time.key), money.key)
        assertTrue(time.answeredBy(asked))
        assertTrue(com.bamnat.core.memo.MemoAsked.has(asked, money.key))
    }
}
