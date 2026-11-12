package com.bamnat.app.ui.pay

import com.bamnat.app.ui.Fmt
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.YearMonth

/**
 * 2.0.0 [RVJ-32] 검토 PC-80: with a closed 10월 on screen, adding a 수당 said '10월 급여에 +200,000원' and then
 * '10월 급여 4,847,509원 → 5,047,509원' while 10월 stayed as closed and only 11월 changed. [RVJ-33] PC-84 '(으)로' left in the
 * words, [RVJ-36] PAYU-17·PC-87 '예상 급여' on every month.
 */
class ClosedMonthWordsTest {
    private val oct = YearMonth.of(2026, 10)
    private val nov = oct.plusMonths(1)

    @Test fun aChangeMadeOnAClosedMonthLandsInTheFirstOpenMonthAfterIt() {
        assertEquals(oct, landsIn(oct, emptySet()))
        assertEquals(nov, landsIn(oct, setOf(oct)))
        assertEquals(nov.plusMonths(1), landsIn(oct, setOf(oct, nov)))
        // A closed month before the one on screen changes nothing here.
        assertEquals(nov, landsIn(nov, setOf(oct)))
    }

    @Test fun theSavedBandSaysTheClosedMonthStaysAndWhereTheChangeGoes() {
        val note = PayViewModel.ChangeNote("수당을 넣었어요", nov, 4_847_509, 5_047_509, null, shut = oct)
        assertEquals("10월은 마감돼 그대로예요 · 11월 급여부터 +200,000원", changeLine(note))
        assertEquals("10월은 마감돼 그대로예요 · 11월 급여도 그대로예요", changeLine(note.copy(after = 4_847_509)))
        assertEquals("10월은 마감돼 그대로예요 · 11월 급여부터 −50,000원", changeLine(note.copy(after = 4_797_509)))
        // Not closed: as before.
        assertEquals("11월 급여 4,847,509원 → 5,047,509원 (+200,000원)", changeLine(note.copy(shut = null)))
    }

    @Test fun aMonthsTitleSaysEstimateOnlyForAMonthStillToBePaid() {
        assertEquals("11월 예상 급여 (세전)", payMonthTitle(nov, nov, closed = false))
        assertEquals("12월 예상 급여 (세전)", payMonthTitle(nov.plusMonths(1), nov, closed = false))
        assertEquals("10월 급여 (세전)", payMonthTitle(oct, nov, closed = false))
        assertEquals("10월 급여 (세전 · 마감)", payMonthTitle(oct, nov, closed = true))
        // Closed early in its own month, beside the pay screen's 마감 chip.
        assertEquals("11월 급여 (세전)", payMonthTitle(nov, nov, closed = true, chip = true))
    }

    @Test fun theParticlesAreWorkedOutNotLeftAsTwo() {
        assertEquals("로", Fmt.ro("매달 수당 1"))
        assertEquals("로", Fmt.ro("‘기숙사비’"))
        assertEquals("으로", Fmt.ro("월급"))
        assertEquals("로", Fmt.ro("식비"))
        assertEquals("으로", Fmt.ro("외식"))
        assertEquals("을", com.bamnat.app.ui.decor.eul("Evening"))
        assertEquals("를", com.bamnat.app.ui.decor.eul("카드"))
    }
}
