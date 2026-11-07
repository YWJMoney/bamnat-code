package com.bamnat.app.ui.setup

import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.SetupDraftStore
import com.bamnat.app.ui.calendar.PaintTool
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.setup.CompanyTemplates
import com.bamnat.core.setup.FollowUp
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupSource
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.setup.TodayQuestion
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.LocalDateTime

/**
 * [SETUP-01][SETUP-02][COMM-05] The first setup: one question at a time, nothing on the calendar until
 * the last step saves it all at once, and a save cut short finished on the next start. Companies here
 * are made up.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SetupFlowTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container
    private val now = LocalDateTime.of(2026, 10, 18, 10, 0)
    private val today = now.toLocalDate()
    private val draft: SetupDraft get() = c.setupDrafts.draft.value!!

    private fun vm(at: LocalDateTime = now) = SetupViewModel(c, now = { at })

    /** Waits for work the app does on its own threads (the save, Room). */
    private fun waitFor(what: String, done: () -> Boolean) {
        val end = System.currentTimeMillis() + 15_000
        while (!done()) {
            check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }

    @Before fun defaults() {
        // The app makes its first shifts on start; the setup always comes after.
        waitFor("the first shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().isNotEmpty() } }
    }

    /** 2교대 with 주주휴휴야야휴휴, today the second night. */
    private fun twoShiftToSummary(vm: SetupViewModel) {
        vm.pickStyle(WorkStyle.SHIFT)
        assertEquals(SetupStep.COMPANY, draft.step)
        vm.next()
        assertEquals(SetupStep.BUNDLE, draft.step)
        vm.pickBundle(ShiftBundles.TWO)
        assertEquals(SetupStep.TIMES, draft.step)
        // [EASE-07] The order is on the times' screen, the most usual one already picked.
        assertTrue(draft.rotation.isNotEmpty())
        vm.pickPreset(vm.presets(draft).first { it.codes == "DDOONNOO" })
        vm.confirmTimes()
        assertEquals(SetupStep.TODAY, draft.step)
        val night = draft.types.first { it.name == "야간" }.id
        val follow = TodayQuestion.followUp(draft.rotation, night, draft::isOff) as FollowUp.RunDay
        vm.answerToday(today, follow.steps[1])
        vm.confirmToday()
        // [EASE-07] The alarm is a card on the summary, not a step of its own.
        assertEquals(SetupStep.SUMMARY, draft.step)
    }

    @Test fun nothingReachesTheCalendarUntilTheLastStepSavesItAll() = runBlocking {
        val vm = vm()
        twoShiftToSummary(vm)
        assertTrue(c.scheduleRepository.patterns.first().isEmpty())
        assertNull(c.settingsRepository.current.workStyle)

        vm.save()
        // FirstSetup.save clears the draft a step before it hands over the saved setup, on the app's own thread:
        // wait for the whole save, not only the draft, or the offer below is read in between (CI, 2026-11-04).
        waitFor("the save") {
            c.settingsRepository.current.workStyle != null && c.setupDrafts.draft.value == null && c.firstSetup.saved.value != null
        }
        assertEquals(WorkStyle.SHIFT, c.settingsRepository.current.workStyle)
        assertFalse(c.settingsRepository.current.rosterMonthly)
        val snap = c.scheduleRepository.snapshot.first()
        assertEquals(1, snap.patterns.size)
        val r = snap.resolver
        assertEquals("야간", r.resolve(today.minusDays(1)).shiftType?.name)
        assertEquals("야간", r.resolve(today).shiftType?.name)
        assertFalse(r.resolve(today.plusDays(1)).isWorkDay)
        assertEquals("주간", r.resolve(today.plusDays(3)).shiftType?.name)
        // The bundle's shifts took the place of the first ones, each once, with the hour-before alarm.
        assertEquals(1, snap.shiftTypes.count { it.name == "주간" })
        assertFalse(snap.shiftTypes.any { it.name == "오후" })
        val day = snap.shiftTypes.first { it.name == "주간" }
        assertTrue(day.alarmEnabled)
        assertEquals(listOf(60), day.alarms.map { it.offsetMinutes })
        // "예상 급여도 계산할까요?" is offered once.
        assertEquals(WorkStyle.SHIFT, c.firstSetup.saved.value?.style)
    }

    @Test fun aDraftLeftForLaterComesBackAsItWas() {
        val vm = vm()
        vm.pickStyle(WorkStyle.DAY)
        vm.setCompanyName("가나다화학")
        vm.later()
        c.setupDrafts.flush()
        val again = SetupDraftStore(File(app.noBackupFilesDir, "setup_draft.json")).draft.value!!
        assertEquals(WorkStyle.DAY, again.style)
        assertEquals("가나다화학", again.companyName)
        assertTrue(again.later)
        assertEquals(SetupStep.COMPANY, again.step)
        // [WORK-01] Weekday work starts Monday to Friday.
        assertEquals(5, again.weekly.count { it != null })
        // [SETUP-16] The kind of work chosen is kept; the setup still counts as not done.
        assertEquals(WorkStyle.DAY, c.settingsRepository.current.workStyle)
        assertTrue(c.settingsRepository.current.setupLater)
        assertFalse(c.settingsRepository.current.setupDone)
    }

    /**
     * [SETUP-16] 1.37 PC 4ef0b92b: 알바 put off at the 요일 step was 교대 everywhere (설정 > 내 근무 '일하는 방식 정하기').
     * The kind of work and its 알바 shifts are there at once, the setup waits, and finishing it later adds no shift twice.
     */
    @Test fun hourlyPutOffAtTheDaysKeepsItsKindOfWorkUntilFinished() = runBlocking {
        val vm = vm()
        vm.pickStyle(WorkStyle.HOURLY)
        assertEquals(SetupStep.TIMES, draft.step)
        // [EASE-07] The days are on the times' screen: with none picked it stays there.
        vm.confirmTimes()
        assertEquals(SetupStep.TIMES, draft.step)
        vm.later()
        val s = c.settingsRepository.current
        assertEquals(WorkStyle.HOURLY, s.workStyle)
        assertTrue(s.setupLater)
        assertFalse(s.setupDone)
        assertTrue(draft.later)
        waitFor("the 알바 shifts") { runBlocking { c.scheduleRepository.shiftTypes.first().any { it.name == "오전 알바" } } }
        assertTrue(c.scheduleRepository.patterns.first().isEmpty())
        // 1.37 PC f42b2eba MED-b: only the 알바 shifts, no 교대 first ones left beside them ('근무 6가지').
        assertEquals(listOf("오전 알바", "오후 알바", "저녁 알바"), c.scheduleRepository.shiftTypes.first().map { it.name })

        // 이어서 하기 → 시간과 요일 → 저장.
        c.setupDrafts.save(draft.copy(later = false))
        vm.setNoFixedDays(true)
        vm.confirmTimes()
        assertEquals(SetupStep.SUMMARY, draft.step)
        vm.save()
        waitFor("the save") { !c.settingsRepository.current.setupLater && c.setupDrafts.draft.value == null && c.firstSetup.saved.value != null }
        assertEquals(WorkStyle.HOURLY, c.settingsRepository.current.workStyle)
        assertTrue(c.settingsRepository.current.setupDone)
        val names = c.scheduleRepository.shiftTypes.first().map { it.name }
        listOf("오전 알바", "오후 알바", "저녁 알바").forEach { assertEquals(it, 1, names.count { n -> n == it }) }
    }

    /** [SETUP-16] PC f42b2eba HIGH-1: the kind of work changed in 설정 > 내 근무 while the setup waited is the one it goes on with. */
    @Test fun aKindOfWorkChangedMeanwhileIsTheOneTheSetupGoesOnWith() {
        val first = vm()
        first.pickStyle(WorkStyle.HOURLY)
        first.later()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.DAY) }
        waitFor("the setup to follow") { c.setupDrafts.draft.value?.style == WorkStyle.DAY }
        assertEquals(SetupStep.COMPANY, draft.step)
        assertTrue("still waiting on the calendar's card", draft.later)
        assertTrue(c.settingsRepository.current.setupLater)
        // Taken up again and answered otherwise: that is what shows everywhere.
        c.setupDrafts.save(draft.copy(later = false))
        vm().pickStyle(WorkStyle.SHIFT)
        assertEquals(WorkStyle.SHIFT, c.settingsRepository.current.workStyle)
        assertEquals(WorkStyle.SHIFT, draft.style)
    }

    /** [SETUP-16] Changed to work written day by day while waiting: nothing more to ask, the setup is done. */
    @Test fun changedToFieldWorkWhileWaitingFinishesTheSetup() {
        val first = vm()
        first.pickStyle(WorkStyle.DAY)
        first.later()
        c.settingsRepository.update { it.copy(workStyle = WorkStyle.FIELD) }
        waitFor("the setup to finish") { c.settingsRepository.current.setupDone && c.setupDrafts.draft.value == null }
        assertEquals(WorkStyle.FIELD, c.settingsRepository.current.workStyle)
    }

    /** [SETUP-16] PC f42b2eba HIGH-2: a backup made while the setup waited brings its step to a new phone. */
    @Test fun aBackupMadeWhileWaitingGoesOnFromItsStep() = runBlocking {
        val first = vm()
        first.pickStyle(WorkStyle.HOURLY)
        first.confirmTimes()
        first.later()
        val step = draft.step
        val file = c.backupManager.export()
        // A new phone: nothing set, no setup begun.
        c.settingsRepository.update { com.bamnat.app.data.AppSettings() }
        c.setupDrafts.clear()
        c.backupManager.restore(file)
        assertEquals(WorkStyle.HOURLY, c.settingsRepository.current.workStyle)
        assertTrue(c.settingsRepository.current.setupLater)
        waitFor("the setup back") { c.setupDrafts.draft.value?.style == WorkStyle.HOURLY }
        // 1.37 PC 270fc15e: the setup's own watch must not start the next question over the answers brought back.
        val until = System.currentTimeMillis() + 800
        waitFor("the watch to settle") { System.currentTimeMillis() > until }
        assertEquals(step, draft.step)
        assertTrue(draft.later)
    }

    /** [SETUP-16] Put off before the kind of work was chosen: nothing to keep, the setup is simply not done. */
    @Test fun putOffBeforeChoosingKeepsNothing() {
        vm().later()
        assertNull(c.settingsRepository.current.workStyle)
        assertFalse(c.settingsRepository.current.setupLater)
        assertFalse(c.settingsRepository.current.setupDone)
    }

    /** [SETUP-16] 교대 put off at the 회사 step is 교대 already, its setup waiting. */
    @Test fun shiftPutOffKeepsShift() {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT)
        vm.later()
        assertEquals(WorkStyle.SHIFT, c.settingsRepository.current.workStyle)
        assertFalse(c.settingsRepository.current.setupDone)
    }

    @Test fun inTheSmallHoursTheQuestionIsAboutYesterday() {
        val vm = vm(LocalDateTime.of(2026, 10, 18, 3, 0))
        vm.pickStyle(WorkStyle.SHIFT)
        vm.next()
        vm.pickBundle(ShiftBundles.TWO)
        assertTrue(vm.earlyHours(draft))
        assertEquals(today.minusDays(1), vm.questionDate(draft))
        vm.answerForToday(true)
        assertEquals(today, vm.questionDate(draft))
        // An hour after the night shift ends it is about today again.
        val later = vm(LocalDateTime.of(2026, 10, 18, 8, 0))
        assertFalse(later.earlyHours(draft))
    }

    /** [SETUP-14] "잘 모르겠어요" on the today question: the shifts stay and the month is painted instead. */
    @Test fun notKnowingTodayGoesToPaintingWithTheShiftsKept() {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT)
        vm.next()
        vm.pickBundle(ShiftBundles.TWO)
        vm.confirmTimes()
        vm.pickPreset(vm.presets(draft).first { it.codes == "DDOONNOO" })
        vm.next()
        assertEquals(SetupStep.TODAY, draft.step)
        val types = draft.types
        vm.paintInstead()
        assertTrue(draft.rosterMonthly)
        assertEquals(SetupStep.ORDER, draft.step)
        assertEquals(types, draft.types)
        assertTrue(draft.rotation.isEmpty())
        assertNull(draft.anchor)
        assertFalse(SetupStep.TODAY in draft.steps)
    }

    @Test fun aMonthlyRosterIsPaintedAndSavedDayByDay() = runBlocking {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT, roster = true)
        vm.next()
        vm.pickBundle(ShiftBundles.HOSPITAL)
        vm.confirmTimes()
        assertEquals(SetupStep.ORDER, draft.step)
        val dayShift = draft.types.first { it.name == "데이" }
        val off = draft.types.first { it.isOff }
        vm.setPaintTool(PaintTool.Shift(dayShift.id))
        vm.paint(listOf(today, today.plusDays(1)))
        // The same again on one day takes it off.
        vm.paintTap(today.plusDays(1))
        assertEquals(setOf(today), draft.painted.keys)
        vm.setPaintTool(PaintTool.Shift(off.id))
        vm.paint(listOf(today.plusDays(2)))
        vm.undoPaint()
        assertEquals(setOf(today), draft.painted.keys)
        vm.paint(listOf(today.plusDays(2)))
        // No order and no today question for a roster.
        vm.next()
        assertEquals(SetupStep.SUMMARY, draft.step)
        vm.save()
        waitFor("the save") { c.settingsRepository.current.workStyle != null && c.setupDrafts.draft.value == null }
        assertTrue(c.settingsRepository.current.rosterMonthly)
        val snap = c.scheduleRepository.snapshot.first()
        assertTrue(snap.patterns.isEmpty())
        assertEquals("데이", snap.resolver.resolve(today).shiftType?.name)
        assertEquals("오프", snap.resolver.resolve(today.plusDays(2)).shiftType?.name)
        assertNull(snap.resolver.resolve(today.plusDays(1)).shiftType)
    }

    /** [SETUP-15] 듀티표가 있어요: a pasted roster is painted onto the month it names; its leave is kept beside the painted days (1.25). */
    @Test fun aRosterReadInTheSetupIsPaintedOntoItsMonth() {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT, roster = true)
        vm.next()
        vm.pickBundle(ShiftBundles.HOSPITAL)
        vm.confirmTimes()
        assertEquals(SetupStep.ORDER, draft.step)
        vm.readRosterText("2026년 11월 근무표\n1 2 3 4 5 6 7 8\n김간호 D D E E N 연차 O O")
        waitFor("the roster read") { vm.roster.value != null && draft.painted.isNotEmpty() }
        val nov = java.time.YearMonth.of(2026, 11)
        assertEquals(nov, vm.paintMonth.value)
        // [READ-30] 1.38: the 연차 day is painted too, with the line's main work, so the leave counts on it.
        assertEquals((1..8).map { nov.atDay(it) }.toSet(), draft.painted.keys)
        assertEquals(draft.types.first { it.name == "데이" }.id, draft.painted[nov.atDay(6)])
        assertEquals(draft.types.first { it.name == "데이" }.id, draft.painted[nov.atDay(1)])
        // O is the bundle's own 오프, as painting it by hand would be.
        assertEquals(draft.types.first { it.isOff }.id, draft.painted[nov.atDay(7)])
        // One undo takes the whole line back.
        vm.undoPaint()
        assertTrue(draft.painted.isEmpty())
    }

    /** 1.38 통합 11차: 월·수·금 13~18시 with the usual 30 minutes' break is 13시간 30분 a week, and the summary says the break decides 주휴. */
    @Test fun theSummarySaysTheBreakTakesTheWeekUnderFifteenHours() {
        fun week(breakMinutes: Int, days: Int) = SetupDraft(style = WorkStyle.HOURLY,
            types = listOf(com.bamnat.core.model.ShiftType(1, "오후 알바", "후", 0xFF000000, java.time.LocalTime.of(13, 0), java.time.LocalTime.of(18, 0),
                breakMinutes = breakMinutes)),
            weekly = List(7) { i -> if (i % 2 == 0 && i / 2 < days) 1L else null })
        val note = weeklyHolidayNote(week(30, 3))!!
        assertTrue(note, note.startsWith("쉬는 시간을 빼면 주 13시간 30분이라"))
        assertTrue(note, "1시간 30분 모자라요" in note)
        assertEquals("주 15시간 일해요 · 주 15시간 이상이라 주휴수당을 받아요", weeklyHolidayNote(week(0, 3)))
        assertTrue(weeklyHolidayNote(week(0, 2))!!.startsWith("주 10시간이라"))
        assertNull(weeklyHolidayNote(week(30, 3).copy(style = WorkStyle.DAY)))
    }

    /** 1.38 통합 11차: a shop's calendar opens every day at once (was '쉬는 날' everywhere until 영업일 was set). */
    @Test fun aShopIsOpenEveryDayFromTheSetup() = runBlocking {
        val vm = vm()
        vm.pickStyle(WorkStyle.BUSINESS)
        waitFor("the shop days") { c.calendars.get(com.bamnat.app.data.PersonalCalendar.MAIN)?.shop != null }
        waitFor("the 영업 days") { runBlocking { c.scheduleRepository.snapshot.first().resolver.resolve(today).shiftType?.name == "영업" } }
        assertEquals("영업", c.scheduleRepository.snapshot.first().resolver.resolve(today.plusDays(3)).shiftType?.name)
    }

    /** 1.38 통합 10차: a table made by '우리 회사 근무표 올리기' in the setup is to be put up (box ticked); a row of the list is not. */
    @Test fun anUploadInTheSetupIsTickedToGoUp() {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT)
        val made = com.bamnat.core.plaza.PublicCycles.provided("예시정밀", "4조2교대")
        vm.pickPublic(made, "DDNNOOOO")
        assertTrue(vm.shareCycle.value)
        vm.pickPublic(made.copy(id = "row1"), "DDNNOOOO")
        assertFalse(vm.shareCycle.value)
    }

    @Test fun aTemplateStartsTheShiftsAndTheUsersOwnOrderIsStillAsked() = runBlocking {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT)
        vm.setCompanyName("예시정밀")
        vm.pickTemplate(CompanyTemplates.byId("two12")!!)
        assertEquals(SetupStep.TIMES, draft.step)
        assertEquals(SetupSource.Kind.TEMPLATE, draft.source?.kind)
        val names = draft.types.associate { it.id to it.name }
        assertEquals(listOf("주간", "주간", "야간", "야간", "휴무", "휴무", "휴무", "휴무"), draft.rotation.map { names[it] })
        // A template's order is only the usual one: shown on the times' screen, already chosen, to be confirmed.
        assertEquals(draft.rotation, vm.sourceOrder(draft))
        vm.pickPreset(vm.presets(draft).first { it.codes == "DDOONNOO" })
        vm.confirmTimes()
        assertEquals(SetupStep.TODAY, draft.step)
        vm.answerToday(today, 0)
        vm.confirmToday()
        assertEquals(SetupStep.SUMMARY, draft.step)
        vm.save()
        waitFor("the save") { c.settingsRepository.current.workStyle != null && c.setupDrafts.draft.value == null }
        assertEquals("주간", c.scheduleRepository.snapshot.first().resolver.resolve(today).shiftType?.name)
        // The company typed is kept to invite the user to share the setup later.
        assertEquals("예시정밀", c.companyStore.draft.value?.company)
        assertNull(c.companyStore.applied.value)
    }

    @Test fun aTemplateFileNamesNoCompanyNoAmountAndNoDate() {
        CompanyTemplates.all.forEach { t ->
            val root = JSONObject(c.companyShare.templateText(t))
            assertEquals("", root.getString("company"))
            assertFalse(t.id, root.has("pay"))
            assertFalse(t.id, root.optJSONObject("rotation")?.has("anchor") ?: false)
            val read = c.companyShare.read(root.toString())
            assertEquals(t.id, t.shifts.size, read.types.size)
            assertEquals(t.id, t.orderNames, read.order)
        }
    }

    @Test fun aCoworkersFileLinesUpWhenTheTeamIsTheSame() {
        val vm = vm()
        // 라마바전자's file from a coworker on A조, whose order started two days ago.
        val root = JSONObject(c.companyShare.templateText(CompanyTemplates.byId("two12")!!)).put("company", "라마바전자")
        root.getJSONObject("rotation").put("anchor", today.minusDays(2).toString()).put("team", "A조")
        vm.importCompany(root.toString())
        assertEquals(WorkStyle.SHIFT, draft.style)
        assertEquals(SetupStep.TIMES, draft.step)
        assertEquals("A조", draft.source?.team)
        vm.confirmTimes()
        assertEquals(SetupStep.TODAY, draft.step)
        // Same 조: the coworker's start date as it is; today is the first night.
        vm.sameTeam()
        assertEquals(today.minusDays(2), draft.anchor)
        assertEquals("오늘(10/18)은 야간 첫째 날이에요. 모레(10/20)부터 휴무 4일이에요.", todaySentence(draft, today, today))
        // 1.38 통합 7차: in the small hours (asked about 어제) today's own shift is said too, and the change with its date.
        val small = todaySentence(draft, today.plusDays(2), today.plusDays(3))!!
        assertTrue(small, small.startsWith("어제(10/20)는 휴무 첫째 날이에요. 오늘(10/21)은 휴무예요."))
        assertTrue(small, small.contains("10/24"))
        // Another 조: asked like any other order.
        vm.clearToday()
        vm.answerToday(today, 0)
        assertEquals(today, draft.anchor)
        vm.confirmToday()
        assertEquals(SetupStep.SUMMARY, draft.step)
    }

    @Test fun aCoworkersFileInSettingsStartsFromTheSendersRow() = runBlocking {
        // The same file opened from 설정 > 데이터 once the app is set up: today's row is asked next.
        val root = JSONObject(c.companyShare.templateText(CompanyTemplates.byId("two12")!!)).put("company", "라마바전자")
        root.getJSONObject("rotation").put("anchor", today.minusDays(2).toString()).put("team", "A조")
        val text = root.toString()
        assertEquals("A조", c.companyShare.summarize(text).team)
        val rows = com.bamnat.app.ui.company.rotationPhases(c, text, fromSender = true, today = today)!!
        assertEquals(8, rows.cycle)
        assertEquals(8, rows.options.size)
        // A조 works the first 야간 today, two days into the order.
        assertEquals(2, rows.sent)
        assertEquals("A조", rows.team)
        assertEquals(listOf("야간", "야간", "휴무", "휴무"), rows.options.single { it.step == rows.sent }.days.take(4).map { it?.name })
        // A company page's setup has no sender to start from.
        val page = com.bamnat.app.ui.company.rotationPhases(c, text, fromSender = false, today = today)!!
        assertNull(page.sent)
        assertNull(page.team)
    }

    @Test fun aSaveCutShortIsFinishedOnceOnTheNextStart() = runBlocking {
        val vm = vm()
        twoShiftToSummary(vm)
        val d = draft.copy(committing = true)
        // The calendar part was written before the app was closed; the kind of work was not.
        c.scheduleRepository.applySetup(d, today)
        c.setupDrafts.save(d)
        c.setupDrafts.flush()
        c.firstSetup.finishInterrupted()
        assertEquals(WorkStyle.SHIFT, c.settingsRepository.current.workStyle)
        assertNull(c.setupDrafts.draft.value)
        val snap = c.scheduleRepository.snapshot.first()
        assertEquals(1, snap.patterns.size)
        assertEquals(1, snap.shiftTypes.count { it.name == "야간" })
        assertEquals("야간", snap.resolver.resolve(today).shiftType?.name)
    }

    @Test fun fixingAnAnswerGoesBackToTheSummary() {
        val vm = vm()
        twoShiftToSummary(vm)
        // [EASE-07] The alarm is answered on the summary itself.
        vm.fix(SetupStep.ALARM)
        assertEquals(SetupStep.SUMMARY, draft.step)
        vm.setAlarm(30)
        // A new order (on the times' screen) needs today again before the summary.
        vm.fix(SetupStep.ORDER)
        assertEquals(SetupStep.TIMES, draft.step)
        vm.pickPreset(vm.presets(draft).first { it.codes == "DDNNOOOO" })
        vm.confirmTimes()
        assertEquals(SetupStep.TODAY, draft.step)
        vm.answerToday(today, 0)
        vm.confirmToday()
        assertEquals(SetupStep.SUMMARY, draft.step)
        // The same kind of work again while fixing changes nothing.
        vm.fix(SetupStep.STYLE)
        vm.pickStyle(WorkStyle.SHIFT)
        assertEquals(SetupStep.SUMMARY, draft.step)
        assertEquals(30, draft.alarmMinutes)
    }

    /** 1.38 통합 8차 (09:41에 소방 당번으로 첫 설정하면 '지금은 새벽이라'): the note says why by the shift; '새벽' only before 6. */
    @Test fun theEarlyNoteSaysTheShiftNotTheDawn() {
        val day = java.time.LocalDate.of(2026, 11, 6)
        val duty = com.bamnat.core.model.ShiftType(id = 1, name = "당번", label = "당", colorArgb = 0,
            start = java.time.LocalTime.of(9, 0), end = java.time.LocalTime.of(9, 0))
        assertEquals("밤을 넘기는 근무는 시작한 날로 쳐요. 어제(11/5) 09:00에 시작한 당번이 막 끝났어요.", earlyNote(duty to false, 9, day))
        assertEquals("밤을 넘기는 근무는 시작한 날로 쳐요. 어제(11/5) 09:00에 시작한 당번이 아직 이어지고 있어요.", earlyNote(duty to true, 8, day))
        assertEquals("지금은 새벽이라, 어젯밤 시작한 근무는 어제(11/5) 근무로 쳐요.", earlyNote(duty to true, 5, day))
    }

    /** 1.38 PC 12.1: a new phone opened from a 근무표 link (a coworker's or a 팀 리더's) starts from it in one press. */
    @Test fun aLinkStartsTheCalendarWithItsOwnWorkAndDays() = runBlocking {
        fun day(d: Int, type: Long?) = com.bamnat.app.share.SharedDay(today.plusDays(d.toLong()), type, null, null, null, emptyList(), null)
        val schedule = com.bamnat.app.share.SharedSchedule(
            "3병동 김간호",
            listOf(
                com.bamnat.app.share.SharedType(1, "데이", "D", 0xFF2196F3, "07:00-15:00", false, null),
                com.bamnat.app.share.SharedType(2, "나이트", "N", 0xFF673AB7, "22:00-07:00", false, null),
                com.bamnat.app.share.SharedType(3, "오프", "O", 0xFF9E9E9E, null, true, null),
            ),
            mapOf(today to day(0, 1), today.plusDays(1) to day(1, 2), today.plusDays(2) to day(2, 3), today.minusMonths(2) to day(-61, 1)),
            emptyList(), emptySet(),
        )
        val d = LinkStart.draft(schedule, today)!!
        // Nothing asked again: the names are checked on the summary.
        assertEquals(SetupStep.SUMMARY, d.step)
        assertTrue(d.rosterMonthly)
        assertEquals("3병동 김간호", d.linkTitle)
        // A month before this one is not the new calendar's.
        assertEquals(3, d.painted.size)
        assertEquals(d, com.bamnat.app.data.SetupDraftCodec.decode(com.bamnat.app.data.SetupDraftCodec.encode(d)))
        c.setupDrafts.save(d)
        val vm = vm()
        assertEquals(SetupStep.SUMMARY, draft.step)
        vm.save()
        waitFor("the save") { c.settingsRepository.current.setupDone && c.setupDrafts.draft.value == null }
        assertTrue(c.settingsRepository.current.rosterMonthly)
        val snap = c.scheduleRepository.snapshot.first()
        assertEquals("데이", snap.resolver.resolve(today).shiftType?.name)
        assertEquals("나이트", snap.resolver.resolve(today.plusDays(1)).shiftType?.name)
        assertEquals("오프", snap.resolver.resolve(today.plusDays(2)).shiftType?.name)
    }

    @Test fun aLinkWithoutTimesAsksTheTimesOnly() {
        val schedule = com.bamnat.app.share.SharedSchedule(
            "팀", listOf(com.bamnat.app.share.SharedType(1, "주간", "주", 0xFF2196F3, null, false, null)),
            emptyMap(), emptyList(), emptySet(),
        )
        val d = LinkStart.draft(schedule, today)!!
        assertEquals(SetupStep.TIMES, d.step)
        assertFalse(d.timesConfirmed)
    }

    /** PC 12.2: back on the bundle's page, the card already picked goes on again. */
    @Test fun thePickedBundleGoesOnAgainAfterBack() {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT)
        vm.next()
        vm.pickBundle(ShiftBundles.TWO)
        assertEquals(SetupStep.TIMES, draft.step)
        vm.back()
        assertEquals(SetupStep.BUNDLE, draft.step)
        vm.pickBundle(ShiftBundles.TWO)
        assertEquals(SetupStep.TIMES, draft.step)
        vm.back(); vm.back()
        assertEquals(SetupStep.COMPANY, draft.step)
        vm.back()
        assertEquals(SetupStep.STYLE, draft.step)
        vm.pickStyle(WorkStyle.SHIFT)
        assertEquals(SetupStep.COMPANY, draft.step)
    }

    /**
     * 1.37 MED-2 (1.39 G6): in the first setup 일근 → 알바 with its 토·일 → 일근 again starts 일근 from 월~금, not from the
     * 알바's 토·일.
     */
    @Test fun dayWorkAfterHourlyInTheSetupIsMondayToFridayAgain() {
        val vm = vm()
        vm.pickStyle(WorkStyle.DAY)
        assertEquals(listOf(true, true, true, true, true, false, false), draft.weekly.map { it != null })
        vm.back()
        vm.pickStyle(WorkStyle.HOURLY)
        val shift = draft.types.first { !it.isOff }.id
        (0..6).forEach { vm.setWeekly(it, shift.takeIf { _ -> it >= 5 }) }
        assertEquals(listOf(false, false, false, false, false, true, true), draft.weekly.map { it != null })
        while (draft.step != SetupStep.STYLE) vm.back()
        vm.pickStyle(WorkStyle.DAY)
        assertEquals(listOf(true, true, true, true, true, false, false), draft.weekly.map { it != null })
    }

    /** [FLOW-03] The line said when the filled calendar first shows: from the draft about to be saved. */
    @Test fun theFillLineCountsTheDraftsDays() {
        val vm = vm()
        vm.pickStyle(WorkStyle.DAY)
        val line = vm.fillLine(draft)!!
        assertTrue(line.text, line.head.contains("일하는 날"))
        assertTrue(line.text, line.rest.contains("쉬는 날"))
    }
}
