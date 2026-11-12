package com.bamnat.core

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.ProCatalog
import com.bamnat.core.tier.ProPitch
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 2.0.0 [RVO-05] (검토 U11b-01·02, PRO-12·30, T11b-01~03) 프로 화면의 유형별 다섯 줄·먼저 보일 기능·늘 무료인 것. */
class ProPitchTest {
    private val others = mapOf(
        // 가게·투자에게 월급·연차·조·명세서는 남의 말이에요(PRO-30, T11b-02).
        WorkStyle.BUSINESS to listOf("월급", "연차", "대근", "명세서", "급여", "52시간", "모든 조"),
        WorkStyle.INVEST to listOf("월급", "연차", "대근", "명세서", "급여", "52시간", "모든 조", "근무표"),
        // 알바·현장에게 '모든 조 보기'와 '미사용 연차수당'은 남의 일이에요(T11b-03).
        WorkStyle.HOURLY to listOf("모든 조", "연차수당"),
        WorkStyle.FIELD to listOf("모든 조", "연차수당", "월급"),
    )

    @Test fun everyKindSaysItsOwnWords() {
        for (style in WorkStyle.entries) {
            val words = listOf(ProPitch.headline(style)) + ProPitch.five(style) + ProPitch.freeAlways(style)
            for (bad in others[style].orEmpty()) assertTrue("$style: $bad", words.none { bad in it })
            assertEquals(5, ProPitch.five(style).size)
            assertEquals(5, ProPitch.freeAlways(style).size)
        }
        assertFalse("T11b-01", ProPitch.five(WorkStyle.INVEST).any { "급여" in it })
    }

    @Test fun theFirstFiveArePaidFeaturesOfThatKind() {
        for (style in WorkStyle.entries) {
            val first = ProPitch.first(style)
            assertEquals(style.name, 5, first.toSet().size)
            for (id in first) {
                val f = FeatureRegistry.get(id)
                assertTrue("$style: $id is in the registry", f != null)
                assertTrue("$style: $id is 프로", ProCatalog.isPro(id))
                assertTrue("$style: $id fits", f!!.fits(style))
            }
        }
    }

    @Test fun whatIsAlwaysFreeIsNotAProFeature() {
        // 늘 무료 줄에 프로 기능의 이름이 들어가면 약속이 틀려요(PRO-12).
        val proNames = ProCatalog.tiers.keys.mapNotNull { FeatureRegistry.get(it)?.name }
        for (style in WorkStyle.entries) for (line in ProPitch.freeAlways(style)) {
            assertTrue("$style: $line", proNames.none { it in line })
        }
    }
}
