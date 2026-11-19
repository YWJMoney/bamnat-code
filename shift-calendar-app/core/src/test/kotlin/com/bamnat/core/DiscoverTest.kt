package com.bamnat.core

import com.bamnat.core.guide.AppFeature
import com.bamnat.core.guide.Discover
import com.bamnat.core.guide.DiscoverAnswer
import com.bamnat.core.guide.DiscoverCatalog
import com.bamnat.core.guide.DiscoverFacts
import com.bamnat.core.guide.DiscoverPicker
import com.bamnat.core.guide.DiscoverRules
import com.bamnat.core.guide.DiscoverState
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.GuideState
import com.bamnat.core.guide.Off
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert
import org.junit.Test

private fun assertTrue(v: Boolean, m: String = "") = Assert.assertTrue(m, v)
private fun assertFalse(v: Boolean, m: String = "") = Assert.assertFalse(m, v)
private fun <T> assertEquals(e: T, a: T, m: String = "") = Assert.assertEquals(m, e, a)
private fun assertNull(v: Any?, m: String = "") = Assert.assertNull(m, v)
private fun <T : Any> assertNotNull(v: T?, m: String = ""): T { Assert.assertNotNull(m, v); return v!! }

/** 1.40 [DISC-01]~[DISC-05] 안 써 본 기능 가끔 알려 주기. */
class DiscoverTest {
    private val installed = LocalDate.of(2026, 9, 1)
    private val day = LocalDate.of(2026, 11, 12)
    private fun facts(on: LocalDate = day, hour: Int = 12, others: Boolean = false) = DiscoverFacts(
        now = on.atTime(hour, 0), installedOn = installed, setupDone = true, guideUp = false, othersUp = others, quietToday = false,
    )
    private fun pick(s: DiscoverState, style: WorkStyle?, guide: GuideState = GuideState(), f: DiscoverFacts = facts(),
                     ready: Boolean = true, alreadyOn: (AppFeature) -> Boolean = { false }) =
        DiscoverPicker.pick(s, guide, "main", style, f, { ready }, alreadyOn)

    /** [DISC-02] Every line is for a feature that exists, fits its kind of work, and is short, kind and free of nagging. */
    @Test fun linesAreShortKindAndReal() {
        DiscoverCatalog.lines.forEach { (id, l) ->
            val f = assertNotNull(FeatureRegistry.get(id), id)
            assertTrue(l.title.length <= DiscoverCatalog.TITLE_MAX, "$id 제목 ${l.title.length}자")
            assertTrue(l.body.length <= DiscoverCatalog.BODY_MAX, "$id 본문 ${l.body.length}자")
            assertTrue(l.action.length <= DiscoverCatalog.ACTION_MAX, "$id 단추 ${l.action.length}자")
            assertTrue(l.value in 1..10, id)
            DiscoverCatalog.NAGGING.forEach { w -> assertFalse(w in l.title + l.body + l.action, "$id: '$w'") }
            // The title is the situation, not the feature's own name.
            assertFalse(l.title.contains(f.name), "$id 제목에 기능 이름")
            l.styles?.forEach { assertTrue(f.fits(it), "$id 은 ${it.label}에 없는 기능") }
            assertTrue(f.flag == null, "$id 는 깃발 뒤 기능")
            // Widgets, the lock screen widget and the watch are used without the app opening: never a candidate (DISC-03).
            assertFalse(id.startsWith("widget.") || id.startsWith("watch."), id)
            assertTrue(l.body.endsWith("요."), "$id 본문은 '~요.'")
        }
    }

    /** [DISC-02] Each kind of work has its own cards, and none of another kind's words. */
    @Test fun everyKindHasItsOwnAndOnlyFittingWords() {
        val shiftWords = listOf("야간", "나이트", "교대", "조 ")
        val payWords = listOf("연차", "월급", "급여", "근무")
        for (style in WorkStyle.entries) {
            val mine = DiscoverCatalog.lines.filter { (id, l) ->
                FeatureRegistry.get(id)!!.fits(style) && (l.styles == null || style in l.styles)
            }
            assertTrue(mine.size >= 8, "${style.label} ${mine.size}개")
            val words = mine.values.joinToString(" ") { it.title + it.body }
            if (style != WorkStyle.SHIFT && style != WorkStyle.DAY) shiftWords.forEach { assertFalse(it in words, "${style.label}: $it") }
            if (style == WorkStyle.INVEST || style == WorkStyle.BUSINESS) payWords.forEach { assertFalse(it in words, "${style.label}: $it") }
        }
    }

    @Test fun theSwitchIsAnAppSwitchUnderTheGuide() {
        val f = assertNotNull(FeatureRegistry.get(DiscoverPicker.SWITCH))
        assertEquals(Off.APP, f.off)
        assertNull(pick(DiscoverState(), WorkStyle.SHIFT, GuideState(off = setOf(GuideState.offKey(GuideState.ALL, f.id)))))
    }

    /** [DISC-01] Only after 21 days, the first setup, the first guides; not on a quiet day, at night, or with another card up. */
    @Test fun quietDays() {
        val s = DiscoverState()
        assertNotNull(pick(s, WorkStyle.SHIFT))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(installedOn = day.minusDays(20))))
        assertNotNull(pick(s, WorkStyle.SHIFT, f = facts().copy(installedOn = day.minusDays(21))))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(setupDone = false)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(guideUp = true)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(quietToday = true)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts(others = true)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(tipToday = true)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts(hour = 22)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts(hour = 5)))
        assertNotNull(pick(s, WorkStyle.SHIFT, f = facts(hour = 6)))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(updatedOn = day.minusDays(2))))
        assertNotNull(pick(s, WorkStyle.SHIFT, f = facts().copy(updatedOn = day.minusDays(3))))
        assertNull(pick(s, WorkStyle.SHIFT, f = facts().copy(crashedOn = day.minusDays(6))))
        assertNull(pick(s.copy(restoredOn = day.minusDays(29)), WorkStyle.SHIFT))
        assertNotNull(pick(s.copy(restoredOn = day.minusDays(30)), WorkStyle.SHIFT))
        val alarm = LocalDateTime.of(day, java.time.LocalTime.of(11, 57))
        assertNull(pick(s.copy(alarmEndedAt = alarm), WorkStyle.SHIFT))
        assertNotNull(pick(s.copy(alarmEndedAt = alarm.minusMinutes(5)), WorkStyle.SHIFT))
    }

    /** [DISC-01] One a day, the same all day; never what was used, put away, switched off, not ready or already on. */
    @Test fun oneFittingCardADay() {
        val first = assertNotNull(pick(DiscoverState(), WorkStyle.HOURLY))
        assertTrue(first.isNew)
        assertTrue(first.feature.fits(WorkStyle.HOURLY))
        val shown = DiscoverRules.shown(DiscoverState(), first.feature.id, day)
        val again = assertNotNull(pick(shown, WorkStyle.HOURLY, f = facts(others = true)))
        assertEquals(first.feature.id, again.feature.id)
        assertFalse(again.isNew)
        assertNull(pick(DiscoverRules.answer(shown, day, DiscoverAnswer.LATER), WorkStyle.HOURLY))
        assertTrue(DiscoverRules.shown(shown, "shop.cash", day).told.size == 1)

        val id = first.feature.id
        assertTrue(pick(DiscoverState(), WorkStyle.HOURLY, GuideState(used = setOf(id)))!!.feature.id != id)
        assertTrue(pick(DiscoverState(), WorkStyle.HOURLY, GuideState(never = setOf(id)))!!.feature.id != id)
        assertTrue(pick(DiscoverState(), WorkStyle.HOURLY, alreadyOn = { it.id == id })!!.feature.id != id)
        // Nothing that needs records before the records are there.
        val bare = pick(DiscoverState(), WorkStyle.BUSINESS, ready = false)!!
        assertNull(DiscoverCatalog.of(bare.feature.id)!!.needs)
    }

    /** 사용자 결정 2026-11-11 빈도 A: 7일에 한 장, 안 눌러 주면 7→14→28→56일, 세 번 연달아 무시하면 60일 쉼. */
    @Test fun theGapGrowsWhenNobodyAnswers() {
        var s = DiscoverRules.shown(DiscoverState(), "alarm.check", day)
        s = DiscoverRules.answer(s, day, DiscoverAnswer.OPENED)
        assertEquals(day.plusDays(7), s.nextOn)
        assertNull(pick(s, WorkStyle.SHIFT, f = facts(day.plusDays(6))))
        assertNotNull(pick(s, WorkStyle.SHIFT, f = facts(day.plusDays(7))))

        // An unanswered card is settled as ignored the next day: 14, then 28 days.
        var d = day.plusDays(7)
        s = DiscoverRules.settle(DiscoverRules.shown(s, "shift.swap", d), d.plusDays(1))
        assertEquals(14, s.gap); assertEquals(d.plusDays(14), s.nextOn); assertEquals(1, s.ignoredInRow)
        d = d.plusDays(14)
        s = DiscoverRules.answer(DiscoverRules.shown(s, "shift.health", d), d, DiscoverAnswer.IGNORED)
        assertEquals(28, s.gap); assertEquals(2, s.ignoredInRow)
        d = d.plusDays(28)
        s = DiscoverRules.answer(DiscoverRules.shown(s, "shift.restRules", d), d, DiscoverAnswer.IGNORED)
        assertEquals(d.plusDays(60), s.nextOn, "세 번 연달아 무시하면 60일 쉼")
        assertEquals(0, s.ignoredInRow)
        assertEquals(56, s.gap)
        s = DiscoverRules.answer(DiscoverRules.shown(s, "sleep.adapt", d.plusDays(60)), d.plusDays(60), DiscoverAnswer.IGNORED)
        assertEquals(56, s.gap, "56일이 상한")
        // 나중에 keeps the gap; 열어 보기 starts again at 7.
        assertEquals(56, DiscoverRules.answer(s.copy(told = s.told + com.bamnat.core.guide.DiscoverTold("pay.gap", d)), d, DiscoverAnswer.LATER).gap)
        assertEquals(7, DiscoverRules.answer(s, d, DiscoverAnswer.OPENED).gap)
        assertEquals(56, DiscoverRules.answer(s, d, DiscoverAnswer.FEWER).gap)
    }

    /** At most 4 in 30 days and 20 in a year; 나중에 brings a feature back once after 60 days; everything told, one in 60 days. */
    @Test fun capsAndComingBack() {
        val ids = DiscoverCatalog.lines.keys.filter { FeatureRegistry.get(it)!!.fits(WorkStyle.SHIFT) }.toList()
        val four = DiscoverState(told = (0 until 4).map { com.bamnat.core.guide.DiscoverTold(ids[it], day.minusDays(7L * it + 1), DiscoverAnswer.OPENED) })
        assertNull(pick(four, WorkStyle.SHIFT))
        val twenty = DiscoverState(told = (0 until 20).map { com.bamnat.core.guide.DiscoverTold(ids[it % ids.size], day.minusDays(17L * it + 31), DiscoverAnswer.OPENED) })
        assertNull(pick(twenty, WorkStyle.SHIFT))

        val first = ids.first()
        val laterOnce = DiscoverState(told = ids.map { com.bamnat.core.guide.DiscoverTold(it, day.minusDays(400), if (it == first) DiscoverAnswer.LATER else DiscoverAnswer.OPENED) })
        assertEquals(first, pick(laterOnce, WorkStyle.SHIFT)?.feature?.id)
        val recent = laterOnce.copy(told = laterOnce.told + com.bamnat.core.guide.DiscoverTold("shift.swap", day.minusDays(59), DiscoverAnswer.OPENED))
        assertNull(pick(recent, WorkStyle.SHIFT), "다 알린 뒤에는 60일에 한 장")
        assertTrue(DiscoverRules.again(laterOnce, first).told.none { it.id == first })
    }

    /** No feature from the same place three times running when another is there. */
    @Test fun aChangeOfScene() {
        val stats = DiscoverCatalog.lines.keys.filter { FeatureRegistry.get(it)!!.route.place == com.bamnat.core.guide.Place.STATS && FeatureRegistry.get(it)!!.fits(WorkStyle.INVEST) }
        assertTrue(stats.size >= 3)
        val s = DiscoverState(told = stats.take(2).mapIndexed { i, id -> com.bamnat.core.guide.DiscoverTold(id, day.minusDays(30L - i * 10), DiscoverAnswer.OPENED) })
        val next = assertNotNull(pick(s, WorkStyle.INVEST))
        assertTrue(next.feature.route.place != com.bamnat.core.guide.Place.STATS, next.feature.id)
    }

    /** 프로 features wait until every free one was told, then one in four at most (설계 1항). */
    @Test fun proWaitsAndStaysRare() {
        val pro: (String) -> Boolean = { it == "alarm.check" }
        val guide = GuideState()
        val first = DiscoverPicker.pick(DiscoverState(), guide, "main", WorkStyle.SHIFT, facts(), { true }, isPro = pro)!!
        assertTrue(first.feature.id != "alarm.check")
        val free = DiscoverCatalog.lines.keys.filter { FeatureRegistry.get(it)!!.fits(WorkStyle.SHIFT) && DiscoverCatalog.of(it)!!.styles.let { s -> s == null || WorkStyle.SHIFT in s } && it != "alarm.check" }
        val allFree = DiscoverState(told = free.map { com.bamnat.core.guide.DiscoverTold(it, day.minusDays(500), DiscoverAnswer.OPENED) })
        assertEquals("alarm.check", DiscoverPicker.pick(allFree, guide, "main", WorkStyle.SHIFT, facts(), { true }, isPro = pro)?.feature?.id)
        val oneFree = DiscoverState(told = listOf(com.bamnat.core.guide.DiscoverTold("shift.swap", day.minusDays(500), DiscoverAnswer.OPENED)))
        val few = DiscoverPicker.pick(oneFree, guide, "main", WorkStyle.SHIFT, facts(), { true }, isPro = { it != "shift.swap" })
        assertNull(few, "4장에 1장까지")
    }

    /** [DISC-05] A feature whose notice is already said is left out (the app asks alreadyOn with NoticeOverlap). */
    @Test fun noticeFeaturesNameTheirNotice() {
        val lock = DiscoverCatalog.of("lock.today")!!
        assertEquals(com.bamnat.core.formula.NoticeOverlap.BuiltIn.TODAY, lock.notice)
        assertTrue(DiscoverCatalog.lines.values.all { it.notice == null || it.action.contains("켜는 곳") }, "알림을 켜는 기능은 켜는 곳으로만 가요")
    }

    @Suppress("unused") private fun unused(d: Discover) = d
}
