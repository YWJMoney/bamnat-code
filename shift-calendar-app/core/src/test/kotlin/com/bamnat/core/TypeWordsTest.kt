package com.bamnat.core

import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.Off
import com.bamnat.core.guide.Place
import com.bamnat.core.guide.StartSteps
import com.bamnat.core.work.TypeWords
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.words
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 검토 뿌리 고침 5번·13번(갈래 H가 가진 시험): core에서 나오는 글이 유형에 맞는지, 기능 찾기의 '어디'와 바로 가기가 같은 곳을 말하는지.
 *
 * 쓰는 법(다른 갈래):
 * - 화면 글을 고쳐 새는 말이 없어지면 [KNOWN]에서 그 줄을 지워요(남아 있으면 '고쳐졌어요'로 실패해요). 줄어드는 쪽으로만 가요.
 * - 새 기능·새 글에 새는 말이 생기면 실패해요. 그 유형의 말([words], `dayKindNoun`, `scheduleWord`, `moneyTab`)로 바꾸거나 그 유형에서
 *   숨겨요. 정말 맞는 말이면 [TypeWords.allowed]에 그 말을 둘러싼 구절을 더해요(낱말 자체를 빼지 않아요).
 * - 줄 모양: `추적 번호|기능 id|낱말|유형들`.
 */
class TypeWordsTest {
    /** [RVH-24] The words themselves: what a shop, an investor, a part-timer must not read, and what is right everywhere. */
    @Test fun eachKindHasItsOwnForbiddenWords() {
        assertTrue(TypeWords.forbidden(WorkStyle.SHIFT).isEmpty())
        assertTrue(TypeWords.forbidden(null).isEmpty())
        assertEquals(listOf("교대", "근무표"), TypeWords.leaks(WorkStyle.BUSINESS, "교대 근무표를 보내요"))
        assertEquals(emptyList<String>(), TypeWords.leaks(WorkStyle.BUSINESS, "직원 근무표를 보내요"))
        assertEquals(listOf("52시간"), TypeWords.leaks(WorkStyle.HOURLY, "52시간까지 남은 시간"))
        // The user's own names are theirs: a 근무 종류 called 야간 is not the app's word.
        assertEquals(emptyList<String>(), TypeWords.leaks(WorkStyle.BUSINESS, "야간 8시간", own = listOf("야간")))
        assertEquals(listOf("근무"), TypeWords.leaks(WorkStyle.INVEST, "근무 유형을 바꾸면 근무가 바뀌어요"))
        // A kind's own words never leak for it.
        for (s in WorkStyle.entries) {
            val w = s.words()
            assertEquals("$s", emptyList<String>(), TypeWords.leaks(s, listOf(w.tab, w.workDay, w.offDay, w.pattern, w.types, w.income).joinToString(" ")))
        }
    }

    /** [RVH-26] 검토 T30-96: the first steps are in the calendar's words. */
    @Test fun theFirstStepsSpeakTheKindsWords() {
        // 투자 has no first steps (its first card is the trade's).
        for (s in WorkStyle.entries - WorkStyle.INVEST) for (step in StartSteps.Step.entries) {
            val text = StartSteps.label(step, s) + " " + StartSteps.doneLabel(step, s)
            assertEquals("$s $step: $text", emptyList<String>(), TypeWords.leaks(s, text))
        }
        assertEquals("영업일 넣기", StartSteps.label(StartSteps.Step.SHIFTS, WorkStyle.BUSINESS))
        assertEquals("일정이 들어 있어요", StartSteps.doneLabel(StartSteps.Step.SHIFTS, WorkStyle.FIELD))
    }

    /**
     * [RVH-24] 검토 T30-102 외: every feature's name, what it does, its way and its tip, for each kind of work it is for, have
     * no word of another kind beyond [KNOWN] (보고서 영역 30 (4)); a line fixed leaves [KNOWN].
     */
    @Test fun featureTextsSpeakTheKindsWords() {
        val found = sortedSetOf<String>()
        for (f in FeatureRegistry.everything) for (s in stylesOf(f.styles)) {
            val said = FeatureRegistry.said(f, s)
            val text = listOf(said.name, said.what, said.where, said.tip.orEmpty()).joinToString("\n")
            for (w in TypeWords.leaks(s, text)) found += "${f.id}|$w|$s"
        }
        val known = KNOWN.trimIndent().lines().filter { it.isNotBlank() }.flatMap { l ->
            val (_, id, word, styles) = l.split('|')
            styles.split(',').map { "$id|$word|$it" }
        }.toSortedSet()
        assertEquals("새로 샌 말(그 유형의 말로 바꾸거나 숨겨요)", emptyList<String>(), (found - known).toList())
        assertEquals("고쳐졌어요: KNOWN에서 지워 주세요", emptyList<String>(), (known - found).toList())
    }

    /**
     * [RVH-25] 검토 뿌리 고침 13번 (WORK-11·12, REST-17·18): the first part of every feature's way, in each kind's own tab names,
     * is where its 바로 가기 lands ([FeatureRegistry.homeOf]). A feature that shows up by itself (a card on the calendar, a widget)
     * and whose 바로 가기 is its switch in 기능 켜고 끄기 says where it shows up instead.
     */
    @Test fun everyWayStartsWhereItsShortcutLands() {
        val wrong = mutableListOf<String>()
        for (f in FeatureRegistry.everything) for (s in stylesOf(f.styles)) {
            val first = FeatureRegistry.whereFor(f, s).substringBefore(" › ")
            if (SURFACES.any { first.startsWith(it) }) continue
            if (f.route.place == Place.FEATURES && f.off != null) continue
            val home = FeatureRegistry.homeOf(f.route.place, s)
            if (first != home && !first.startsWith("$home ")) wrong += "${f.id} $s: '$first' ≠ ${f.route.place}($home)"
        }
        assertEquals(emptyList<String>(), wrong)
    }

    /** [RVH-25] The way reads in the kind's own tab names. */
    @Test fun theWayIsInTheKindsTabNames() {
        val pay = FeatureRegistry.get("hourly.breaks")!!
        assertEquals("출근·시급", FeatureRegistry.whereFor(pay, WorkStyle.HOURLY).substringBefore(" › "))
        val all = FeatureRegistry.get("stats.all")!!
        assertEquals("매출·영업 › 전체", FeatureRegistry.whereFor(all, WorkStyle.BUSINESS))
        assertEquals("투자·수익 › 전체", FeatureRegistry.whereFor(all, WorkStyle.INVEST))
        val money = FeatureRegistry.everything.first { it.route.place == Place.MONEY && it.styles.isEmpty() && it.where.startsWith("가계부") }
        assertTrue(FeatureRegistry.whereFor(money, WorkStyle.BUSINESS).startsWith("장부"))
        // 검토 WORK-11: 광장's features open 마을 › 광장, not the pay screen.
        for (id in listOf("plaza.info", "plaza.styles", "plaza.shiftRoom")) assertEquals(id, Place.PLAZA, FeatureRegistry.get(id)!!.route.place)
        for (id in listOf("plaza.diff", "plaza.room")) assertEquals(id, Place.BOARD, FeatureRegistry.get(id)!!.route.place)
    }

    private fun stylesOf(styles: Set<WorkStyle>) = if (styles.isEmpty()) WorkStyle.entries else styles.sortedBy { it.ordinal }

    private companion object {
        /** Ways that start outside the app's screens: a widget, the watch, the alarm screen, every screen's top bar. */
        val SURFACES = listOf("홈 화면", "워치", "알람이 울릴", "각 화면", "아래 탭", "파일 앱", "잠금 화면", "할 일")

        /** 2.0.0 처음 잰 것(검토 T30-102와 영역 30 (4)): 고치면 지워요. 2묶음에서 유형별 글로 줄여요. */
        const val KNOWN = """
        """
    }
}
