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
            val text = listOf(f.name, f.what, FeatureRegistry.whereFor(f, s), f.tip.orEmpty()).joinToString("\n")
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
        T30-102|guide.tips|급여|INVEST
        T30-102|share.link|근무표|FIELD,BUSINESS
        T30-102|share.receive|근무표|FIELD,BUSINESS
        T30-102|share.receive|쉬는 날|BUSINESS
        T30-102|share.look|근무표|FIELD,BUSINESS
        T30-102|share.feed|근무표|FIELD,BUSINESS
        T30-102|share.rosterPdf|근무표|FIELD,BUSINESS
        T30-102|sleep.log|근무별|FIELD,BUSINESS,INVEST
        T30-102|sleep.log|근무|INVEST
        T30-102|sleep.smartWake|근무 알람|FIELD,BUSINESS,INVEST
        T30-102|sleep.smartWake|근무|INVEST
        T30-102|sleep.challenge|근무마다|FIELD,BUSINESS,INVEST
        T30-102|sleep.challenge|근무|INVEST
        T30-102|sleep.dnd|근무 알람|FIELD,BUSINESS,INVEST
        T30-102|sleep.dnd|근무|INVEST
        T30-102|sleep.shareNow|근무표|FIELD,BUSINESS,INVEST
        T30-102|sleep.shareNow|근무|INVEST
        T30-102|sleep.diary|근무|INVEST
        T30-102|sleep.sharePlan|근무표|FIELD,BUSINESS,INVEST
        T30-102|sleep.sharePlan|근무|INVEST
        T30-102|stats.compare|근무·급여|HOURLY
        T30-102|day.bridges|교대|DAY,HOURLY
        T30-102|hourly.attendance|대근|HOURLY
        T30-102|shop.staff|주휴|BUSINESS
        T30-102|shop.staff|근무표|BUSINESS
        T30-102|money.owed|급여|INVEST
        T30-102|calendar.customize|근무·급여|HOURLY,FIELD,BUSINESS,INVEST
        T30-102|calendar.customize|근무|INVEST
        T30-102|calendar.customize|급여|INVEST
        T30-102|privacy.lock|급여|INVEST
        T30-102|memo.repeat|근무마다|FIELD,BUSINESS,INVEST
        T30-102|memo.repeat|야간|BUSINESS,INVEST
        T30-102|memo.repeat|근무|INVEST
        T30-102|data.backup|근무|INVEST
        T30-102|app.pro|명세서|INVEST
        T30-102|app.pro|급여|INVEST
        T30-102|plaza.news|근무표|FIELD,BUSINESS,INVEST
        T30-102|plaza.news|근무|INVEST
        T30-102|widget.lines|연차|FIELD,BUSINESS
        T30-102|widget.lines|주휴|FIELD,BUSINESS
        T30-102|pay.rateHint|연차|FIELD
        T30-102|calendar.quickText|대근|HOURLY,FIELD,BUSINESS,INVEST
        T30-102|calendar.quickText|연차|FIELD,BUSINESS,INVEST
        T30-102|calendar.quickText|야간|BUSINESS,INVEST
        T30-102|calendar.quickText|근무|INVEST
        T30-102|calendar.monthRecap|야간|BUSINESS,INVEST
        T30-102|data.openFile|근무표|FIELD,BUSINESS,INVEST
        T30-102|data.openFile|근무|INVEST
        T30-102|data.openFile|회사|INVEST
        T30-102|pattern.weeklyOptions|근무 주기|DAY,HOURLY,FIELD
        T30-102|widget.countdown|근무|INVEST
        T30-102|alarm.suggest|근무 알람|FIELD
        T30-102|profile.summary|연차|FIELD,BUSINESS,INVEST
        T30-102|profile.summary|근무|INVEST
        T30-102|profile.summary|급여|INVEST
        T30-102|pay.holidayWork|교대|DAY,HOURLY
        T30-102|pay.slipRules|대근|HOURLY
        T30-102|setup.again|근무·급여|HOURLY,FIELD,BUSINESS,INVEST
        T30-102|setup.again|근무|INVEST
        T30-102|setup.again|급여|INVEST
        T30-102|setup.again|회사|INVEST
        T30-102|pay.ruleImport|대근|HOURLY
        T30-102|nav.tabOrder|연차|FIELD,BUSINESS,INVEST
        T30-102|nav.tabOrder|급여|INVEST
        T30-102|plaza.autoShare|근무|INVEST
        T30-102|plaza.autoShare|회사|INVEST
        T30-102|memo.search|야간|BUSINESS,INVEST
        T30-102|memo.search|근무|INVEST
        T30-102|memo.shiftRemind|출근|BUSINESS,INVEST
        T30-102|memo.shiftRemind|야간|BUSINESS,INVEST
        T30-102|memo.shiftRemind|쉬는 날|BUSINESS
        T30-102|memo.shiftRemind|근무|INVEST
        T30-102|memo.lastShift|야간|BUSINESS,INVEST
        T30-102|memo.lastShift|근무|INVEST
        T30-102|plaza.info|근무·급여|HOURLY,FIELD,BUSINESS,INVEST
        T30-102|plaza.info|근무|INVEST
        T30-102|plaza.info|급여|INVEST
        T30-102|plaza.info|회사|INVEST
        T30-102|day.story|야간|BUSINESS,INVEST
        T30-102|day.story|근무|INVEST
        T30-102|day.start|야간|BUSINESS,INVEST
        T30-102|day.start|근무|INVEST
        T30-102|pay.fixChips|근무·급여|HOURLY
        T30-102|shop.staffFree|근무표|BUSINESS
        T30-102|pay.workProof|52시간|HOURLY
        T30-102|money.ahead|근무|INVEST
        T30-102|money.envelopes|근무|INVEST
        T30-102|money.challenge|야간|BUSINESS,INVEST
        T30-102|money.whatIf|대근|HOURLY
        T30-102|money.hide|회사|INVEST
        T30-102|money.dayClose|야간이면|DAY,HOURLY,FIELD
        T30-102|money.dayClose|퇴근|FIELD
        T30-102|shift.callout|교대|DAY
        T30-102|hours.stretch|전공의|DAY
        T30-102|money.learnMore|근무|INVEST
        T30-102|calendar.jumpMonth|급여|INVEST
        T30-102|recipe.browse|근무|INVEST
        T30-102|recipe.browse|급여|INVEST
        T30-102|recipe.make|근무|INVEST
        T30-102|calendar.theme|근무|INVEST
        T30-102|shift.emoji|근무마다|FIELD,BUSINESS,INVEST
        T30-102|shift.emoji|근무|INVEST
        T30-102|calendar.view|근무|INVEST
        T30-102|look.free|근무별|FIELD,BUSINESS,INVEST
        T30-102|look.free|근무|INVEST
        T30-102|look.free|급여|INVEST
        T30-102|boss.payroll|주휴|BUSINESS
        T30-102|boss.payroll|야간|BUSINESS
        """
    }
}
