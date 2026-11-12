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
    private fun twoShiftToSummary(vm: SetupViewModel, draftOf: () -> SetupDraft = { draft }, beforeConfirm: () -> Unit = {}) {
        fun draft() = draftOf()
        vm.pickStyle(WorkStyle.SHIFT)
        assertEquals(SetupStep.COMPANY, draft().step)
        vm.next()
        assertEquals(SetupStep.BUNDLE, draft().step)
        vm.pickBundle(ShiftBundles.TWO)
        assertEquals(SetupStep.TIMES, draft().step)
        // [EASE-07] The order is on the times' screen, the most usual one already picked.
        assertTrue(draft().rotation.isNotEmpty())
        vm.pickPreset(vm.presets(draft()).first { it.codes == "DDOONNOO" })
        beforeConfirm()
        vm.confirmTimes()
        assertEquals(SetupStep.TODAY, draft().step)
        val night = draft().types.first { it.name == "야간" }.id
        val follow = TodayQuestion.followUp(draft().rotation, night, draft()::isOff) as FollowUp.RunDay
        vm.answerToday(today, follow.steps[1])
        vm.confirmToday()
        // [EASE-07] The alarm is a card on the summary, not a step of its own.
        assertEquals(SetupStep.SUMMARY, draft().step)
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

    /**
     * 2.0.0 [RVH-43] 검토 PC-31 (통합 1차 PC: '이대로 저장할까요?'의 달은 1일~어제가 비었는데 저장한 달력은 1일부터 차 있었어요): the
     * summary's month is the month saving makes, day by day.
     */
    @Test fun theSummarysMonthIsTheSavedMonthFromTheFirst() = runBlocking {
        val vm = vm()
        twoShiftToSummary(vm)
        val preview = vm.previewSnapshot(draft)!!.resolver
        val month = java.time.YearMonth.from(today)
        assertTrue(preview.resolve(month.atDay(1)).shiftType != null)
        vm.save()
        waitFor("the save") { c.setupDrafts.draft.value == null && c.firstSetup.saved.value != null }
        val saved = c.scheduleRepository.snapshot.first().resolver
        (1..month.lengthOfMonth()).map(month::atDay).forEach { day ->
            assertEquals(day.toString(), saved.resolve(day).shiftType?.name, preview.resolve(day).shiftType?.name)
        }
    }

    /**
     * [RVH-28] 검토 WORK-04 처음 설정 다시 받기 over the calendar's shifts: the 주간 the user gave two alarms and a color of their own
     * keeps them, its new time is said on the summary before saving and saved. Before 2.0.0 it came back with one alarm, quietly.
     */
    @Test fun setupAgainKeepsTheAlarmsAndColorsAndSaysTheNewTimes() = runBlocking {
        val first = vm()
        twoShiftToSummary(first)
        first.save()
        waitFor("the first save") { c.setupDrafts.draft.value == null && c.firstSetup.saved.value != null }
        val mine = c.scheduleRepository.shiftTypes.first().first { it.name == "주간" }
        val own = mine.copy(colorArgb = 0xFF123456, alarmEnabled = true, alarms = listOf(com.bamnat.core.model.AlarmSpec(60), com.bamnat.core.model.AlarmSpec(30)))
        c.scheduleRepository.saveShiftType(own)
        c.firstSetup.consumeSaved()

        val again = SetupViewModel(c, now = { now }, again = true)
        waitFor("the calendar's shifts") { again.kept.value.any { it.name == "주간" && it.alarms.size == 2 } }
        twoShiftToSummary(again, { c.againSetupDrafts.draft.value!! }) {
            val day = c.againSetupDrafts.draft.value!!.types.first { it.name == "주간" }
            again.saveType(day.copy(start = day.start!!.plusHours(1), end = day.end!!.plusHours(1)))
        }
        val d = c.againSetupDrafts.draft.value!!
        // The summary says the 주간 moves an hour, and that its alarms stay.
        val changes = d.timeChanges(again.kept.value)
        assertEquals(listOf("주간"), changes.map { it.second.name })
        assertEquals(mine.start!!.plusHours(1), changes.single().second.start)
        assertTrue(d.keptWork(again.kept.value).any { it.name == "주간" })
        // The month on the summary is drawn in the user's own color.
        assertEquals(0xFF123456, again.typesToSave(d).first { it.name == "주간" }.colorArgb)
        // [RVH-43] 검토 PC-31: and it is the month saving makes, the days before today as they were.
        waitFor("the calendar's order") { again.periods.value.isNotEmpty() }
        val preview = again.previewSnapshot(d)!!.resolver

        again.save()
        waitFor("the save again") { c.againSetupDrafts.draft.value == null && c.firstSetup.saved.value != null }
        val month = java.time.YearMonth.from(today)
        val after = c.scheduleRepository.snapshot.first().resolver
        (1..month.lengthOfMonth()).map(month::atDay).forEach { day ->
            assertEquals(day.toString(), after.resolve(day).shiftType?.name, preview.resolve(day).shiftType?.name)
        }
        // The days before keep the hours they had, on the summary as on the calendar (the save counts the new hours from the
        // phone's own today, the summary from this test's, so only the past is compared by the hour).
        val past = month.atDay(1).datesUntil(today.minusDays(1)).toList().first { after.resolve(it).shiftType?.name == "주간" }
        assertEquals(mine.start, preview.resolve(past).start)
        assertEquals(after.resolve(past).start, preview.resolve(past).start)
        val saved = c.scheduleRepository.shiftTypes.first().filter { it.name == "주간" }
        assertEquals(1, saved.size)
        assertEquals(listOf(60, 30), saved.single().alarms.map { it.offsetMinutes })
        assertTrue(saved.single().alarmEnabled)
        assertEquals(0xFF123456, saved.single().colorArgb)
        assertEquals(mine.start!!.plusHours(1), saved.single().start)
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

    /** 1.38 D3: the summary's 올리기 box starts unticked even for a table made by '우리 회사 근무표 올리기'; ticking it puts it up. */
    @Test fun anUploadInTheSetupStartsUnticked() {
        val vm = vm()
        vm.pickStyle(WorkStyle.SHIFT)
        val made = com.bamnat.core.plaza.PublicCycles.provided("예시정밀", "4조2교대")
        vm.pickPublic(made, "DDNNOOOO")
        assertFalse(vm.shareCycle.value)
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

    /**
     * [RVG-16] 검토 TYPE-21: a setup started from a 근무표 link was always 교대. The summary asks how the user works; the days stay the
     * link's, and the phone is set up as that kind of work (an 알바 team member gets 시급, not the 교대 pay setup).
     */
    @Test fun aLinkStartAsksHowTheUserWorks() = runBlocking {
        fun day(d: Int, type: Long?) = com.bamnat.app.share.SharedDay(today.plusDays(d.toLong()), type, null, null, null, emptyList(), null)
        val schedule = com.bamnat.app.share.SharedSchedule(
            "카페 민지",
            listOf(com.bamnat.app.share.SharedType(1, "오픈", "오", 0xFF2196F3, "09:00-14:00", false, null)),
            mapOf(today to day(0, 1), today.plusDays(1) to day(1, null), today.plusDays(2) to day(2, 1)),
            emptyList(), emptySet(),
        )
        c.setupDrafts.save(LinkStart.draft(schedule, today)!!)
        val vm = vm()
        assertEquals(WorkStyle.SHIFT, draft.savedStyle)
        vm.workAs(WorkStyle.HOURLY)
        assertEquals(WorkStyle.HOURLY, draft.savedStyle)
        // Nothing else of the link's setup changes: still its painted days, on the summary.
        assertEquals(SetupStep.SUMMARY, draft.step)
        assertEquals(2, draft.painted.count { it.value != null })
        assertEquals(draft, com.bamnat.app.data.SetupDraftCodec.decode(com.bamnat.app.data.SetupDraftCodec.encode(draft)))
        vm.save()
        waitFor("the save") { c.settingsRepository.current.setupDone && c.setupDrafts.draft.value == null }
        assertEquals(WorkStyle.HOURLY, c.settingsRepository.current.workStyle)
        assertFalse("교대's monthly roster mark stays with 교대", c.settingsRepository.current.rosterMonthly)
        val snap = c.scheduleRepository.snapshot.first()
        assertEquals("오픈", snap.resolver.resolve(today).shiftType?.name)
        assertEquals("오픈", snap.resolver.resolve(today.plusDays(2)).shiftType?.name)
        assertTrue(snap.resolver.resolve(today.plusDays(1)).shiftType?.isOff != false)
    }

    /** [RVG-18] 검토 PC-22 (도약 14): a 리더 confirmed next month; the member's summary and calendar open on it, not on an empty month. */
    @Test fun aLinkOfNextMonthOpensOnIt() = runBlocking {
        val next = java.time.YearMonth.from(today).plusMonths(1)
        fun day(d: Int, type: Long?) = com.bamnat.app.share.SharedDay(next.atDay(d), type, null, null, null, emptyList(), null)
        val schedule = com.bamnat.app.share.SharedSchedule(
            "3병동 김간호",
            listOf(com.bamnat.app.share.SharedType(1, "데이", "D", 0xFF2196F3, "07:00-15:00", false, null),
                com.bamnat.app.share.SharedType(2, "오프", "O", 0xFF9E9E9E, null, true, null)),
            mapOf(next.atDay(1) to day(1, 1), next.atDay(2) to day(2, 1), next.atDay(3) to day(3, 2)),
            emptyList(), emptySet(),
        )
        val d = LinkStart.draft(schedule, today)!!
        assertEquals(next, d.openingMonth(today))
        // A roster with a working day this month opens on this month.
        assertEquals(java.time.YearMonth.from(today), d.copy(painted = d.painted + (today to d.painted.values.first { it != null })).openingMonth(today))
        c.setupDrafts.save(d)
        val vm = vm()
        vm.save()
        waitFor("the save") { c.settingsRepository.current.setupDone && c.setupDrafts.draft.value == null }
        assertEquals(next, c.shownMonth.month.value)
    }

    /** 민지's 근무표 (가족 link): 데이 today and the day after, a day off between, 나이트 on the fourth. */
    private val familyId = "Fm1jNg2hKo3pLq4rMs5tNu"
    private fun familyShare() = com.bamnat.app.share.ReceivedShare(
        JSONObject("""{"v":2,"title":"민지","audience":"family",
            "types":[{"id":1,"name":"데이","short":"D","color":"#2196F3","time":"07:00-15:00"},{"id":2,"name":"나이트","short":"N","color":"#673AB7","time":"22:00-07:00"}],
            "days":[{"date":"$today","type":1},{"date":"${today.plusDays(1)}","type":1},{"date":"${today.plusDays(2)}"},{"date":"${today.plusDays(3)}","type":2}]}"""),
        java.time.Instant.now().plus(java.time.Duration.ofDays(60)), java.time.Instant.now(), "e1",
    )

    /** '내 근무표예요' on the first screen, as AppRoot does it: the setup filled from the link, the link kept as the user's own. */
    private fun startAsMine(): SetupViewModel {
        // The link is kept on the setup's day (its month is where the link's days start).
        com.bamnat.core.time.Today.fix(now.atZone(java.time.ZoneId.systemDefault()))
        val share = familyShare()
        c.setupDrafts.save(LinkStart.draft(com.bamnat.app.share.SharedSchedule.parse(share.schedule), today)!!)
        LinkStart.keepMine(c.received, familyId, share)
        return vm()
    }

    /**
     * [RVG-19] 검토 HQ-09 (조건 10): '내 근무표예요' pressed for 민지's link by mistake. Before, the summary had no way back to the question
     * (← only went to earlier questions) and the link stayed the user's own, followed. '가족·동료 근무표였어요' keeps it beside as theirs
     * (only seen, never put into my days), lets the setup go, and the first screen says where it went.
     */
    @Test fun aWrongMineGoesBackFromTheSummaryAsTheirs() = runBlocking {
        val vm = startAsMine()
        assertEquals(SetupStep.SUMMARY, draft.step)
        assertTrue(c.received.get(familyId)!!.mine)
        assertEquals("민지", vm.notMine())
        assertNull("the setup goes: the first screen asks the user's own work again", c.setupDrafts.draft.value)
        val cal = c.received.get(familyId)!!
        assertFalse(cal.mine)
        assertEquals(com.bamnat.app.share.RosterFollower.Mode.OFF, com.bamnat.app.share.RosterFollower.mode(cal))
        assertFalse(c.settingsRepository.current.setupDone)
        val snap = c.scheduleRepository.snapshot.first()
        assertTrue("nothing of 민지's is in my days", (0L..3L).all { snap.resolver.resolve(today.plusDays(it)).entry == null })
    }

    @org.junit.After fun letTheLinkGo() {
        c.received.remove(familyId)
        com.bamnat.core.time.Today.reset()
    }

    /**
     * [RVG-20] 검토 HQ-09 (조건 10): found after the setup. Before, '넣지 않기' was the only way, and the days the link had put in stayed.
     * '내 근무표가 아니에요' says how many days come out first, takes out only the link's (a day the user changed by hand stays), keeps
     * the link beside as theirs, and 되돌리기 puts both back.
     */
    @Test fun notMineAfterTheSetupTakesOutOnlyTheLinksDays() = runBlocking {
        val vm = startAsMine()
        vm.save()
        waitFor("the save") { c.settingsRepository.current.setupDone && c.setupDrafts.draft.value == null }
        val before = c.scheduleRepository.snapshot.first()
        val day = before.resolver.resolve(today).shiftType!!.id
        assertEquals("나이트", before.resolver.resolve(today.plusDays(3)).shiftType?.name)
        // The user changes the day after by hand (나이트 instead of 데이).
        val night = before.resolver.resolve(today.plusDays(3)).shiftType!!.id
        c.scheduleRepository.setOverride(today.plusDays(1), com.bamnat.core.model.ShiftOverride.Shift(night))

        val plan = com.bamnat.app.share.NotMine.plan(c, familyId)!!
        assertEquals("데이, 쉬는 날, 나이트: three days are the link's", listOf(today, today.plusDays(2), today.plusDays(3)), plan.theirs)
        assertEquals(listOf(today.plusDays(1)), plan.kept)
        val undo = com.bamnat.app.share.NotMine.apply(c, familyId, plan)

        val cal = c.received.get(familyId)!!
        assertFalse(cal.mine)
        assertEquals(com.bamnat.app.share.RosterFollower.Mode.OFF, com.bamnat.app.share.RosterFollower.mode(cal))
        val after = c.scheduleRepository.snapshot.first()
        assertNull("민지's 데이 is out of my days", after.resolver.resolve(today).shiftType)
        assertNull(after.resolver.resolve(today.plusDays(3)).shiftType)
        assertEquals("the day changed by hand stays", night, after.resolver.resolve(today.plusDays(1)).shiftType?.id)

        undo()
        val back = c.scheduleRepository.snapshot.first()
        assertEquals(day, back.resolver.resolve(today).shiftType?.id)
        assertEquals(night, back.resolver.resolve(today.plusDays(3)).shiftType?.id)
        assertEquals(night, back.resolver.resolve(today.plusDays(1)).shiftType?.id)
        assertTrue(c.received.get(familyId)!!.mine)
        assertEquals(com.bamnat.app.share.RosterFollower.Mode.AUTO, com.bamnat.app.share.RosterFollower.mode(c.received.get(familyId)!!))
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
