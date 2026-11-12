package com.bamnat.core.guide

import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.moneyTab
import com.bamnat.core.work.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVP-75] (검토 REST-17·REST-18·REST-19, T14-03~07·10~12·14, I14a-03) 기능 찾기's '어디' in the calendar's own words, and
 * 바로 가기 to the place the words name. Every feature is walked, also those behind a flag ([FeatureRegistry.everything]).
 */
class FeatureWhereTest {
    private val tabs = WorkStyle.entries.map { it.words().tab }.toSet()

    private fun first(f: AppFeature, s: WorkStyle) = f.whereFor(s).substringBefore(" › ")

    /** The first step is a part of this calendar's app: its own tab, its own money tab, 달력, 설정, 마을, 메모 … never another kind's tab. */
    @Test fun theWayIsInTheCalendarsOwnWords() {
        val bad = WorkStyle.entries.flatMap { s ->
            val mine = s.words().tab
            FeatureRegistry.everything.filter { it.fits(s) }.mapNotNull { f ->
                val head = first(f, s)
                val otherTab = (tabs - mine).any { head.startsWith(it) } && !head.startsWith(mine)
                val otherMoney = listOf("가계부", "장부").any { it != moneyTab(s) && (head == it || head.endsWith("·$it")) }
                if (otherTab || otherMoney) "${s.name} ${f.id}: ${f.whereFor(s)}" else null
            }
        }
        assertEquals(emptyList<String>(), bad)
    }

    @Test fun eachKindReadsItsOwnTab() {
        val yearIncome = FeatureRegistry.everything.first { it.where.startsWith("근무·급여 › 전체") || it.where.startsWith("통계 › 전체") }
        assertTrue(yearIncome.whereFor(WorkStyle.FIELD), yearIncome.whereFor(WorkStyle.FIELD).startsWith("일감·수입"))
        assertTrue(yearIncome.whereFor(WorkStyle.BUSINESS).startsWith("매출·영업"))
        assertTrue(yearIncome.whereFor(WorkStyle.INVEST).startsWith("투자·수익"))
        // A where that does not start at a tab stays as it is.
        val calendar = FeatureRegistry.everything.first { it.where.startsWith("달력") }
        assertEquals(calendar.where, calendar.whereFor(WorkStyle.HOURLY))
    }

    /** 바로 가기 opens the part of the app the first word names (마을 is not the pay screen, settings are settings). */
    @Test fun theWayInMatchesTheWords() {
        val bad = FeatureRegistry.everything.mapNotNull { f ->
            val head = f.where.substringBefore(" › ")
            val place = f.route.place
            val ok = when {
                head.startsWith("마을") -> place in setOf(Place.VILLAGE, Place.RECIPES, Place.RECIPE_MINE, Place.PROFILE)
                place == Place.VILLAGE -> head.startsWith("마을")
                place == Place.COMPANY_BOARD -> "같은 회사 분들 규칙 보기" in f.where
                place == Place.NOWHERE -> f.id.startsWith("alarm.")
                else -> true
            }
            if (ok) null else "${f.id}: ${f.where} → $place"
        }
        assertEquals(emptyList<String>(), bad)
        // The ringing alarm has no screen to go to: the search shows where it is only.
        assertEquals(Place.NOWHERE, FeatureRegistry.get("alarm.allOff")!!.route.place)
        // 이번 달 전부 넣기 and 한 달 마감 카드 are over the month, not today's sheet.
        assertEquals(FeatureRoute(Place.CALENDAR, "typeMonth"), FeatureRegistry.get("shift.dutyTyping")!!.route)
        assertEquals(FeatureRoute(Place.CALENDAR, "month"), FeatureRegistry.get("calendar.monthRecap")!!.route)
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
