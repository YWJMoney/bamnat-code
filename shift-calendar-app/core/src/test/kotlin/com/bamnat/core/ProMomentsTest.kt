package com.bamnat.core

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.ProCatalog
import com.bamnat.core.tier.ProMoments
import com.bamnat.core.tier.ProMoments.Moment
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** 1.32 [PROX-01][PROX-02][PROX-03] (계획/1.32-조사-대조표.md): what a day pass opens, when 프로 is offered, and free stays free. */
class ProMomentsTest {
    @Test fun aDayPassListsWhatItOpenedForThisWork() {
        ProMoments.OPENED.forEach {
            assertNotNull(it.featureId, FeatureRegistry.get(it.featureId))
            ProCatalog.tierOf(it.featureId)?.let { t -> assertEquals(it.featureId, t, it.tier) }
        }
        val shift = ProMoments.opened(WorkStyle.SHIFT).map { it.featureId }
        assertTrue("pay.slipAudit" in shift && "money.bizTax" !in shift)
        val shop = ProMoments.opened(WorkStyle.BUSINESS).map { it.featureId }
        assertTrue("money.bizTax" in shop && "pay.slipAudit" !in shop && "money.stats" in shop)
        assertEquals(listOf("money.stats"), ProMoments.opened(WorkStyle.INVEST).map { it.featureId })
    }

    @Test fun moneyDaysOfferOncePerSeasonAndNeverTwoWeeksRunning() {
        val payday = LocalDate.of(2026, 9, 10)
        assertEquals(Moment.PAYDAY, ProMoments.due(payday, WorkStyle.SHIFT, payday = true, rosterToday = false, offered = emptySet(), lastOffer = null))
        // Nothing at stake today, nothing offered.
        assertNull(ProMoments.due(payday, WorkStyle.SHIFT, false, false, emptySet(), null))
        // Already offered this quarter.
        val got = setOf(ProMoments.seasonOf(Moment.PAYDAY, payday))
        assertNull(ProMoments.due(payday.plusDays(5), WorkStyle.SHIFT, true, false, got, null))
        // Within 14 days of any offer.
        assertNull(ProMoments.due(payday.plusDays(10), WorkStyle.SHIFT, false, true, emptySet(), payday))
        assertEquals(Moment.ROSTER, ProMoments.due(payday.plusDays(14), WorkStyle.SHIFT, false, true, emptySet(), payday))
        // The season of the year comes first, and fits the work.
        val nov = LocalDate.of(2026, 11, 3)
        assertEquals(Moment.YEAR_END_PREVIEW, ProMoments.due(nov, WorkStyle.DAY, true, false, emptySet(), null))
        assertNull(ProMoments.due(nov, WorkStyle.INVEST, true, false, emptySet(), null))
        val may = LocalDate.of(2027, 5, 10)
        assertEquals(Moment.MAY_TAX, ProMoments.due(may, WorkStyle.FIELD, false, false, emptySet(), null))
        assertEquals(Moment.VAT, ProMoments.due(LocalDate.of(2027, 7, 2), WorkStyle.BUSINESS, false, false, emptySet(), null))
        assertNull(ProMoments.due(LocalDate.of(2027, 7, 2), WorkStyle.FIELD, false, false, emptySet(), null))
        // Every moment names a tier row the app sells.
        Moment.entries.forEach { m -> assertTrue(m.feature, m.feature in ProCatalog.tiers.values) }
    }

    @Test fun aFeatureOnceFreeIsNeverMovedToPro() {
        val free = javaClass.classLoader!!.getResource("free-features.txt")!!.readText().lines()
            .map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
        assertTrue(free.size > 100)
        val moved = free.filter { ProCatalog.isPro(it) }
        assertEquals("무료였던 기능은 프로로 옮기지 않아요", emptyList<String>(), moved)
        // And every free feature still in the app is on the list, so the next release's list starts from it.
        val missing = FeatureRegistry.all.map { it.id }.filterNot { ProCatalog.isPro(it) || it in free }
        assertEquals("free-features.txt 에 새 무료 기능을 더해 주세요", emptyList<String>(), missing)
    }
}
