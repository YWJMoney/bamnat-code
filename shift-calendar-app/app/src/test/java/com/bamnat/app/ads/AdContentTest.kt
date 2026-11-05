package com.bamnat.app.ads

import com.google.android.gms.ads.RequestConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

/** [ADAGE-01] Ads never go above teen content, and the rest of the configuration (test devices, tags) is kept. */
class AdContentTest {
    @Test fun capsAtTeenAndKeepsTheRest() {
        val before = RequestConfiguration.Builder()
            .setTestDeviceIds(listOf("TEST-DEVICE"))
            .setTagForChildDirectedTreatment(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE)
            .setMaxAdContentRating(RequestConfiguration.MAX_AD_CONTENT_RATING_MA)
            .build()
        val after = AdContent.teenSafe(before)
        assertEquals(RequestConfiguration.MAX_AD_CONTENT_RATING_T, after.maxAdContentRating)
        assertEquals(listOf("TEST-DEVICE"), after.testDeviceIds)
        assertEquals(RequestConfiguration.TAG_FOR_CHILD_DIRECTED_TREATMENT_FALSE, after.tagForChildDirectedTreatment)
    }

    @Test fun anEmptyConfigurationGetsTheCapToo() {
        assertEquals(RequestConfiguration.MAX_AD_CONTENT_RATING_T, AdContent.teenSafe(RequestConfiguration.Builder().build()).maxAdContentRating)
    }
}
