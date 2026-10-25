package com.bamnat.app.tier

import com.bamnat.app.ui.pro.proFeatureOf
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.ProCatalog
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [TIER-06][TIER-07] 1.25 묶음 Y (U18): every pro feature sold is in the one list the badge and 모아보기 read. */
class ProCatalogTest {
    @Test fun everyTierRowSoldHasAFeatureAndEveryLineIsReal() {
        // 광고 없음 is not a place to open; 리더 is Phase 3 and not sold.
        val sold = Feature.entries.filter { it.minTier == Tier.PRO && it != Feature.NO_ADS }.map { it.name }.toSet()
        assertEquals(sold, ProCatalog.tiers.values.toSet())
        ProCatalog.tiers.keys.forEach { id ->
            assertNotNull(id, FeatureRegistry.get(id))
            assertNotNull(id, proFeatureOf(id))
        }
    }

    @Test fun theCollectionPutsThisCalendarsFeaturesFirst() {
        val invest = ProCatalog.of(WorkStyle.INVEST)
        assertEquals(ProCatalog.tiers.size, invest.size)
        val firstOther = invest.indexOfFirst { !it.fits(WorkStyle.INVEST) }
        assertTrue(invest.take(firstOther).all { it.fits(WorkStyle.INVEST) })
        assertTrue("pay comes after for an investor", invest.indexOfFirst { it.id == "pay.slipAudit" } >= firstOther)
        assertTrue(ProCatalog.of(WorkStyle.SHIFT).takeWhile { it.fits(WorkStyle.SHIFT) }.any { it.id == "pay.slipAudit" })
    }
}
