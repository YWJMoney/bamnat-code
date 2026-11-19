package com.bamnat.app.money

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.MoneyRepository
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.ShopCapture
import com.bamnat.core.time.Today
import com.bamnat.core.money.DailySales
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * 1.39.1 [R391-09] 검토 MONEY-41: on a shop calendar, a card company's deposit that fit no day's card sales went to the usual
 * reading and was written as 수입·부수입, so the card sales counted twice.
 */
@RunWith(AndroidJUnit4::class)
class ShopDepositTest {
    private val c get() = ApplicationProvider.getApplicationContext<BamnatApp>().container

    @After fun back() = c.calendars.setActive(PersonalCalendar.MAIN)

    @Test fun aDepositThatFitsNoDayIsNotIncome() = runBlocking {
        val id = c.calendars.begin("심야포차")
        c.calendars.finishSetup(id, WorkStyle.BUSINESS)
        val money = c.calendars.money(id)
        money.save(DailySales(LocalDate.of(2026, 11, 2), 300_000, card = 200_000))
        val text = "[Web발신]\n신한 11/04 10:12\n입금 81,300원\n삼성카드 입금\n잔액 1,000,000원"
        val at = LocalDateTime.of(2026, 11, 4, 10, 12).atZone(Today.zone()).toInstant().toEpochMilli()
        assertEquals(MoneyRepository.Outcome.SAVED, ShopNotices.take(c, id, text, "com.samsung.android.messaging", "메시지", at))
        val lines = money.book.first().entries
        assertTrue("no 수입: ${lines.map { it.kind to it.category }}", lines.none { it.kind == MoneyKind.INCOME })
        val line = lines.single()
        assertEquals(MoneyKind.TRANSFER, line.kind)
        assertEquals(ShopCapture.CARD_DEPOSIT, line.category)
        assertEquals(81_300L, line.amount)
        assertFalse("to be checked", line.reviewed)
        // The same notice again is not written twice.
        assertEquals(MoneyRepository.Outcome.DUPLICATE, ShopNotices.take(c, id, text, "com.samsung.android.messaging", "메시지", at))
    }

    /** [R200-04] (검토 MONEY-41 넘긴 일) Two card companies' parts: the second one settles the day and both lines say so. */
    @Test fun cardCompaniesPartsSettleTheDayTogether() = runBlocking {
        val id = c.calendars.begin("분식집")
        c.calendars.finishSetup(id, WorkStyle.BUSINESS)
        val money = c.calendars.money(id)
        val sale = LocalDate.of(2026, 11, 2)
        money.save(DailySales(sale, 300_000, card = 200_000))
        fun at(h: Int, m: Int) = LocalDateTime.of(2026, 11, 4, h, m).atZone(Today.zone()).toInstant().toEpochMilli()
        ShopNotices.take(c, id, "[Web발신]\n신한 11/04 10:12\n입금 81,300원\n삼성카드 입금\n잔액 1,000,000원", "com.samsung.android.messaging", "메시지", at(10, 12))
        assertFalse(money.book.first().sales.single().cardSettled)
        ShopNotices.take(c, id, "[Web발신]\n신한 11/04 11:40\n입금 115,700원\n신한카드 입금\n잔액 1,115,700원", "com.samsung.android.messaging", "메시지", at(11, 40))
        val book = money.book.first()
        assertTrue("the day's 입금 확인", book.sales.single().cardSettled)
        assertEquals(2, book.entries.size)
        assertTrue(book.entries.all { it.kind == MoneyKind.TRANSFER && it.reviewed && it.note == "11월 2일 카드 매출 입금 확인" })
        assertTrue(book.entries.none { it.kind == MoneyKind.INCOME })
    }
}
