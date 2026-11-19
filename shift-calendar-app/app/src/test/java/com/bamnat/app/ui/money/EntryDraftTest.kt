package com.bamnat.app.ui.money

import com.bamnat.core.money.MoneyKind
import com.bamnat.core.time.Today
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 2.0.0 [RVK-04] (검토 MONEY-27·U06a-02) 적다 만 돈 적기: what was typed and not saved comes back with every field, only in the calendar
 * it was written in, only on the day the sheet was opened on, once, and for thirty minutes. Names and amounts are made up.
 */
class EntryDraftTest {
    private val zone = ZoneId.of("Asia/Seoul")
    private val opened = LocalDate.of(2026, 10, 14)
    private var now = Instant.parse("2026-10-14T01:00:00Z")

    private val moving = object : Clock() {
        override fun getZone(): ZoneId = zone
        override fun withZone(z: ZoneId): Clock = this
        override fun instant(): Instant = now
    }

    @Before fun fixClock() { Today.use(moving); EntryDraft.clear() }
    @After fun release() { EntryDraft.clear(); Today.reset() }

    private fun draft(calendar: String = "main", day: LocalDate = opened) = EntryDraft.Saved(
        calendar, opened, day, MoneyKind.EXPENSE, "12500", "식비", "단골 분식", "회식 뒤", account = 3, toAccount = 4,
        tags = "#회식 #팀", parts = "식비 10000\n생활 2500", installments = 3, rate = "15.9", proof = "카드 영수증", receipt = "a.jpg",
    )

    @Test fun everyFieldComesBackAndTheDayItWasMovedTo() {
        val d = draft(day = opened.minusDays(1))
        EntryDraft.keep(d)
        assertEquals(d, EntryDraft.take(opened, "main"))
    }

    @Test fun itComesBackOnceOnly() {
        EntryDraft.keep(draft())
        EntryDraft.take(opened, "main")
        assertNull(EntryDraft.take(opened, "main"))
    }

    @Test fun anotherCalendarNeverGetsIt() {
        EntryDraft.keep(draft("main"))
        assertNull(EntryDraft.take(opened, "second"))
    }

    @Test fun anotherDayOpenedDoesNotGetIt() {
        EntryDraft.keep(draft())
        assertNull(EntryDraft.take(opened.plusDays(1), "main"))
    }

    @Test fun savingClearsItAndHalfAnHourKeepsIt() {
        EntryDraft.keep(draft())
        now = now.plusSeconds(29 * 60)
        assertEquals(draft(), EntryDraft.take(opened, "main"))
        EntryDraft.keep(draft())
        now = now.plusSeconds(31 * 60)
        assertNull(EntryDraft.take(opened, "main"))
        EntryDraft.keep(draft())
        EntryDraft.clear()
        assertNull(EntryDraft.take(opened, "main"))
    }
}
