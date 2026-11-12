package com.bamnat.core.guide

import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVP-75] (검토 REST-18·REST-19, T14-06·07·10~12) 기능 찾기's 바로 가기 to the place the words name, each feature for the kinds
 * it says, and the features behind a flag marked. The way's first word against its place, for every feature and kind, is
 * `TypeWordsTest.everyWayStartsWhereItsShortcutLands` (갈래 H, RVH-25); these are the rows that test does not see.
 */
class FeatureWhereTest {
    /** 바로 가기 opens what the words name: the month's keypad, the month (not today's sheet), 회사 규칙 광장 from the pay screen. */
    @Test fun theWayInMatchesTheWords() {
        // The ringing alarm has no screen to go to: the search shows where it is only.
        assertEquals(Place.NOWHERE, FeatureRegistry.get("alarm.allOff")!!.route.place)
        assertEquals(FeatureRoute(Place.CALENDAR, "typeMonth"), FeatureRegistry.get("shift.dutyTyping")!!.route)
        assertEquals(FeatureRoute(Place.CALENDAR, "month"), FeatureRegistry.get("calendar.monthRecap")!!.route)
        // 검토 REST-18 (2): settings has no 회사 규칙 광장 row; it opens from the pay screen's 같은 회사 분들 규칙 보기.
        for (id in listOf("plaza.diff", "plaza.room")) for (s in listOf(WorkStyle.SHIFT, WorkStyle.DAY)) {
            val way = FeatureRegistry.whereFor(FeatureRegistry.get(id)!!, s)
            assertTrue(way, way.startsWith(s.words().tab + " › ○월 예상 급여 › 같은 회사 분들 규칙 보기"))
        }
    }

    /** One kind set for the alarm group (현장 rings too), 공무원 봉급표·회사 규칙 not for 알바, roster sharing not for 투자. */
    @Test fun eachFeatureFitsTheKindsItSays() {
        // 근무마다 맞춘 알람 한 번에 옮기기 (alarm.moveAll) is the shift kinds' own alarms, so it stays 교대·일근.
        val alarms = listOf("alarm.check", "alarm.mission", "alarm.wakeCheck", "alarm.missionPhoto", "alarm.suggest", "alarm.buttons", "alarm.allOff")
            .map { FeatureRegistry.get(it)!! }
        alarms.forEach { assertTrue(it.id, it.fits(WorkStyle.FIELD) && !it.fits(WorkStyle.BUSINESS) && !it.fits(WorkStyle.INVEST)) }
        listOf("pay.publicGrade", "pay.ruleBuilder", "plaza.diff", "plaza.room").forEach { assertFalse(it, FeatureRegistry.get(it)!!.fits(WorkStyle.HOURLY)) }
        listOf("share.link", "share.receive", "share.rosterPdf", "calendar.phone", "calendar.year").forEach {
            assertFalse(it, FeatureRegistry.get(it)!!.fits(WorkStyle.INVEST))
        }
    }

    /** What rests on a flag that may be off (결제, 광고, 레시피 장터, 사장님) says so, so the search never shows a button that is not there. */
    @Test fun featuresBehindAFlagSaySo() {
        mapOf("app.pro" to "billing", "pro.collection" to "billing", "privacy.adChoices" to "ads", "recipe.browse" to "recipe.browse",
            "recipe.make" to "recipe.publish", "boss.payroll" to "boss.payroll").forEach { (id, flag) ->
            assertEquals(id, flag, FeatureRegistry.get(id)!!.flag)
        }
        val saved = FeatureRegistry.flagOn
        try {
            FeatureRegistry.flagOn = { it != "billing" }
            assertFalse(FeatureRegistry.all.any { it.id == "app.pro" })
            assertTrue(FeatureRegistry.everything.any { it.id == "app.pro" })
        } finally {
            FeatureRegistry.flagOn = saved
        }
    }
}
