package com.bamnat.app.ui.setup

import com.bamnat.app.ui.withBar
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.company.PublicCycleFiles
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.SharedSetup
import com.bamnat.app.data.BackupException
import com.bamnat.app.data.BackupSummary
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.alarm.AlarmPlanner
import com.bamnat.core.alarm.PlannedAlarm
import com.bamnat.app.ui.calendar.PaintTool
import com.bamnat.app.ui.settings.LockedBackup
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.PatternPreset
import com.bamnat.core.schedule.PatternPresets
import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.schedule.ShiftRole
import com.bamnat.core.setup.CompanyTemplate
import com.bamnat.core.setup.CompanyTemplates
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupSource
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.setup.TodayQuestion
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import com.bamnat.core.time.Today

/**
 * [SETUP-01][SETUP-02] The first setup, one question at a time. Every answer goes to the draft on the
 * phone and nothing reaches the calendar until the last step saves it all at once.
 */
class SetupViewModel(
    private val c: AppContainer,
    private val now: () -> LocalDateTime = Today::dateTime,
    /**
     * [MCAL-02] A calendar being added: the same questions as the first launch, saved into that
     * calendar only. The app's settings, pay setup and company stay as they are.
     */
    val calendarId: String? = null,
    /**
     * [PLAZA-06] U25-나 처음 설정 다시 받기: the first setup's questions again, for someone who skipped them or wants a
     * company's setup now. Answers wait in their own draft; leaving changes nothing, and saving applies them as the
     * first setup would (from today on; past days and days changed by hand stay).
     */
    val again: Boolean = false,
) : ViewModel() {
    private val store = when {
        calendarId != null -> c.calendarSetupDrafts
        again -> c.againSetupDrafts
        else -> c.setupDrafts
    }
    private val current: SetupDraft get() = store.draft.value ?: SetupDraft()

    val draft: StateFlow<SetupDraft> = store.draft.map { it ?: SetupDraft() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, current)

    init {
        if (again && store.draft.value == null) {
            store.save(SetupDraft())
            c.telemetry.event("setup_again")
        }
        // A draft kept from an older build, or cut short, can sit on a step it no longer has.
        val d = current
        if (d.step !in d.steps) store.save(d.copy(step = d.steps.firstOrNull { !d.canLeave(it) } ?: SetupStep.SUMMARY))
    }

    /**
     * [RVH-28] 검토 WORK-04 처음 설정 다시 받기: the shifts the calendar has now. A shift of the same name keeps its alarms, color and
     * sound when saved, and the summary says which times change. Empty for a first setup and a calendar being added.
     */
    val kept: StateFlow<List<ShiftType>> =
        if (again) c.scheduleRepository.shiftTypes.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList()) else MutableStateFlow(emptyList<ShiftType>()).asStateFlow()

    /** The shifts as they will be saved: the draft's, with what [kept] keeps ( 검토 WORK-04); the summary's month and alarm say these. */
    fun typesToSave(d: SetupDraft): List<ShiftType> = if (again) d.typesToSave(kept.value, today) else d.typesToSave()

    /**
     * 2.0.0 [RVH-43] 검토 PC-31: the order periods the calendar has before this setup is saved (its own for a calendar being added), so the
     * summary's month is filled as saving fills it ([previewSnapshot]).
     */
    val periods: StateFlow<List<com.bamnat.core.model.WorkPattern>> =
        (calendarId?.let { runCatching { c.calendars.repository(it) }.getOrNull() } ?: c.scheduleRepository).patterns
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * 2.0.0 [RVH-43] 검토 PC-31 (통합 1차 PC: '이대로 저장할까요?'의 달은 11/1~11/11이 비었는데 저장한 달력은 1일부터 차 있어서 '오늘부터만
     * 채워지나?'로 읽혔어요): the month as [com.bamnat.app.data.ScheduleRepository.applySetup] will save it. A first setup's order
     * fills the past too (two years back); a calendar being added starts today; a setup again keeps the days before today as they
     * were and its new order from today. Null when nothing would be on the calendar.
     */
    fun previewSnapshot(d: SetupDraft, before: List<com.bamnat.core.model.WorkPattern> = periods.value): ScheduleSnapshot? {
        val kept = kept.value
        // The ids saving gives: a shift of the same name the calendar has keeps its id, a new one takes one after them.
        val next = (kept.maxOfOrNull { it.id } ?: 0L) + 1
        val ids = d.types.withIndex().associate { (i, t) -> t.id to (kept.firstOrNull { k -> k.name.trim() == t.name.trim() }?.id ?: (next + i)) }
        val saved = if (kept.isEmpty()) d else d.remap(ids)
        val types = typesToSave(saved).let { mine -> mine + kept.filter { k -> mine.none { it.id == k.id } } }
        val order = saved.pattern(today)
        val patterns = when {
            order == null -> before
            before.isEmpty() -> listOf(if (calendarId == null) com.bamnat.core.schedule.PatternTimeline.first(order, today) else order)
            else -> com.bamnat.core.schedule.PatternTimeline.changeFrom(before, order, today)
        }
        return ScheduleSnapshot(types, patterns, saved.paintedEntries()).takeIf { !it.isEmpty }
    }

    /** The week as the calendar shows it, for the month previews. */
    val weekStart: DayOfWeek get() = c.settingsRepository.current.weekStart

    /** [COMM-01] With a company server the company step searches it; without one it offers templates only. */
    val companySearch: Boolean get() = c.companyApi.enabled

    val today: LocalDate get() = now().toLocalDate()

    /** [MCAL-02] The name of the calendar being added; null for the first setup. */
    val calendarName: String? get() = calendarId?.let { c.calendars.get(it)?.name }

    private val _done = MutableStateFlow(false)
    /** [MCAL-02] The added calendar was saved (or given up): the screen closes. */
    val done: StateFlow<Boolean> = _done.asStateFlow()

    /** [MCAL-02] Gives up the calendar being added, with its answers. */
    fun cancelCalendar() {
        val id = calendarId ?: return
        store.clear()
        c.calendars.delete(id)
        c.telemetry.event("calendar_setup_cancel")
        _done.value = true
    }

    /** [EASE-03] 1.38: the summary's '같은 회사 분들과 이 근무주기 나누기' box; never ticked beforehand. */
    val shareCycle = MutableStateFlow(false)

    /**
     * 1.39 사용자 2026-11-08 (첫 설정 끝에 '잠을 아래 메뉴에 둘까요?' 한 번 권하기; 8차 PC 1-24: 새 설치는 첫 설정 요약이 마지막 화면이라
     * 추천 화면의 권하기가 안 보였어요): the place of the bar 잠 takes when the first setup is saved; -1 keeps the bar. Never picked
     * beforehand. Only the first setup of a new install offers it ([offersSleepBar]).
     */
    val sleepSlot = MutableStateFlow(-1)
    val offersSleepBar: Boolean get() = calendarId == null && !again

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** [READ-15] A line to show, e.g. why the document scanner could not run. */
    fun say(text: String) { _message.value = text }

    /**
     * [SETUP-16] 1.37 PC fca16ba8 ('백업을 불러왔어요… 이어서 해요'가 잠깐 떴다 사라져 못 봤어요): a line that stays over the step
     * until the user goes on or closes it.
     */
    private val _note = MutableStateFlow<String?>(null)
    val note: StateFlow<String?> = _note.asStateFlow()
    fun closeNote() { _note.value = null }

    fun consumeMessage() {
        _message.value = null
    }

    /** From the summary's 고치기: once that answer is in, back to the summary past what is still right. */
    private val _fixing = MutableStateFlow(false)
    val fixing: StateFlow<Boolean> = _fixing.asStateFlow()

    /** [SETUP-01] In the small hours the question is about yesterday; the user can answer for today instead. */
    private val _todayBased = MutableStateFlow(false)
    val todayBased: StateFlow<Boolean> = _todayBased.asStateFlow()

    /** The day "오늘 무슨 근무예요?" asks about: yesterday while last night's shift may still be on. */
    fun questionDate(d: SetupDraft): LocalDate =
        if (_todayBased.value) today else TodayQuestion.questionDate(now(), d.types)

    /** In the small hours, when the question would be about yesterday unless the user says otherwise. */
    fun earlyHours(d: SetupDraft): Boolean = TodayQuestion.questionDate(now(), d.types) != today

    /** 1.38 통합 8차: the overnight shift that makes the question yesterday's, and whether it is still on now. */
    fun earlyBecause(d: SetupDraft): Pair<com.bamnat.core.model.ShiftType, Boolean>? =
        TodayQuestion.becauseOf(now(), d.types)?.let { t -> t to now().toLocalTime().isBefore(t.end!!) }

    /** The hour now, for saying 새벽 only in the small hours. */
    fun hourNow(): Int = now().hour

    fun answerForToday(on: Boolean) {
        _todayBased.value = on
        update { it.copy(anchor = null, todayConfirmed = false) }
    }

    private fun update(transform: (SetupDraft) -> SetupDraft) {
        val before = current
        val next = transform(before)
        if (next != before) store.save(next)
    }

    // ---- moving between steps ----

    fun next() {
        _note.value = null
        val d = current
        if (!d.canLeave()) return
        if (d.step == SetupStep.SUMMARY) return save()
        val ahead = d.steps.dropWhile { it != d.step }.drop(1)
        val target = if (_fixing.value) ahead.firstOrNull { it == SetupStep.SUMMARY || !d.canLeave(it) } else ahead.firstOrNull()
        if (target == SetupStep.SUMMARY) _fixing.value = false
        update { it.copy(step = target ?: SetupStep.SUMMARY) }
    }

    /** One step back; while fixing from the summary, straight back to it. */
    fun back() {
        _note.value = null
        if (_fixing.value) {
            _fixing.value = false
            update { it.copy(step = SetupStep.SUMMARY) }
            return
        }
        update { d -> d.copy(step = d.previous() ?: d.step) }
    }

    fun fix(step: SetupStep) {
        // [EASE-07] The order is on the times' screen and the alarm on the last one now.
        val screen = current.screenOf(step)
        if (screen !in current.steps || screen == SetupStep.SUMMARY && current.step == SetupStep.SUMMARY) return
        _fixing.value = true
        update { it.copy(step = screen) }
    }

    /**
     * "나중에": the app opens on the calendar with a card to go on from here. [SETUP-16] 1.37: the kind of work already
     * chosen is the app's from now on (설정 > 내 근무, the tabs, pay and every card that fits a kind of work), with
     * [com.bamnat.app.data.AppSettings.setupLater] so the setup still counts as not done.
     */
    fun later() {
        c.telemetry.event("setup_later", current.step.name)
        if (again) return cancelAgain()
        if (calendarId == null) c.setupTaps.end() // 1.39 [SETUP-20] 나중에: install to a full calendar did not happen, nothing goes.
        update { it.copy(later = true) }
        if (calendarId == null) current.style?.let { style -> keepStyle(style) }
    }

    /** [SETUP-16] A first setup put off with [style] chosen: that kind of work shows now, its setup waits. */
    private fun keepStyle(style: WorkStyle) {
        c.settingsRepository.update { s -> if (s.setupDone) s else s.copy(workStyle = style, setupLater = true) }
        // 1.37 PC f42b2eba (교대 기본값이 섞여 '근무 6가지', 일근 출근 시간에 오후·야간): the shifts answered so far are the calendar's
        // already, the first ones it does not use gone. Finishing the setup finds them by name, never twice.
        val d = current.takeIf { it.style == style && it.types.isNotEmpty() }
        if (d != null) c.appScope.launch {
            runCatching { c.scheduleRepository.applySetupTypes(d) }.onFailure { android.util.Log.w("Setup", "shifts put off", it) }
        }
    }

    /** [PLAZA-06] Leaves 처음 설정 다시 받기: its answers go, the calendar stays as it was. */
    fun cancelAgain() {
        if (!again) return
        store.clear()
        _done.value = true
    }

    // ---- 1. how the user works ----

    /**
     * [RVG-16] 검토 TYPE-21: a setup filled from a 근무표 link asks on its summary how the user works (교대 · 일근 · 알바); the days stay
     * the link's. Only these three: the link is a roster of shifts.
     */
    fun workAs(style: WorkStyle) = update { d ->
        if (d.linkTitle.isBlank() || style !in LINK_WORK) d else d.copy(workAs = style.takeIf { it != d.style })
    }

    fun pickStyle(style: WorkStyle, roster: Boolean = false) {
        val d = current
        when (style) {
            // Field and shop work are written day by day: nothing more to ask.
            WorkStyle.FIELD, WorkStyle.BUSINESS, WorkStyle.INVEST -> {
                // [J133-77] A shop's days were asked on their own screen right after. [EASE-09] 1.38 PC 994c2328 (자영업 1번 뒤에
                // 영업일 화면이 나와 저장·뒤로를 더 눌러요): the calendar comes first with every day open; 영업일·정기휴무 is in
                // 근무 주기 when the user wants it.
                save(SetupDraft(style = style))
            }
            else -> {
                val same = d.style == style && d.rosterMonthly == (roster && style == WorkStyle.SHIFT)
                val start = when {
                    d.style == style -> d.copy(rosterMonthly = roster && style == WorkStyle.SHIFT)
                    else -> fresh(style, roster).copy(companyName = d.companyName)
                }
                // The same answer while fixing changes nothing: straight back to the summary.
                val target = if (same && _fixing.value) SetupStep.SUMMARY else start.steps.getOrElse(1) { SetupStep.SUMMARY }
                _fixing.value = false
                c.telemetry.event("setup_style", style.name + if (roster) "_ROSTER" else "")
                store.save(start.copy(step = target))
                // [SETUP-16] A setup put off and taken up again: the kind of work shown everywhere follows the new answer at once
                // (after the draft, so the setup's own watch finds them agreeing).
                if (calendarId == null && !again && c.settingsRepository.current.setupLater && c.settingsRepository.current.workStyle != style) keepStyle(style)
            }
        }
    }

    private fun fresh(style: WorkStyle, roster: Boolean): SetupDraft = when (style) {
        WorkStyle.DAY -> SetupDraft(style = style).withShifts(null, ShiftBundles.weekday)
        WorkStyle.HOURLY -> SetupDraft(style = style).withShifts(null, ShiftBundles.hourly)
        else -> SetupDraft(style = style, rosterMonthly = roster)
    }

    // ---- files: a coworker's company setup, or the user's own backup ----

    private val _pendingRestore = MutableStateFlow<Pair<String, BackupSummary>?>(null)
    /** A backup read and checked, waiting for the user to say yes. */
    val pendingRestore: StateFlow<Pair<String, BackupSummary>?> = _pendingRestore.asStateFlow()
    private val _locked = MutableStateFlow<LockedBackup?>(null)
    val locked: StateFlow<LockedBackup?> = _locked.asStateFlow()

    /** [SET-01] One way in for both kinds of file, as in settings. */
    fun readFile(context: Context, uri: Uri) {
        viewModelScope.launch {
            runCatching {
                val text = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)!!.use { c.backupManager.read(it) }
                }
                when {
                    CompanyShare.isCompanyFile(text) -> importCompany(text)
                    // [MCAL-02] A backup replaces every calendar; it is not something to add one from.
                    // [SETUP-13] Names the menu as it is: 설정 > 데이터 > 파일 불러오기.
                    calendarId != null -> _message.value = "백업은 설정 > 데이터 > 파일 불러오기에서 열 수 있어요. 여기서는 회사 근무 파일만 열 수 있어요"
                    c.backupManager.isSealed(text) -> _locked.value = LockedBackup(text)
                    else -> _pendingRestore.value = text to c.backupManager.summarize(text)
                }
            }.onFailure { e -> _message.value = (e as? BackupException)?.message ?: "파일을 읽을 수 없어요" }
        }
    }

    fun unlock(password: CharArray) {
        val locked = _locked.value ?: return
        _locked.value = locked.copy(working = true, error = null)
        viewModelScope.launch {
            runCatching {
                val json = withContext(Dispatchers.Default) { c.backupManager.unseal(locked.text, password) }
                json to c.backupManager.summarize(json)
            }.onSuccess {
                _locked.value = null
                _pendingRestore.value = it
            }.onFailure { e ->
                _locked.value = locked.copy(working = false, error = (e as? BackupException)?.message ?: "백업 파일을 읽을 수 없어요")
            }
            password.fill(' ')
        }
    }

    fun cancelFile() {
        _locked.value = null
        _pendingRestore.value = null
        c.backupManager.putAway()
    }

    /** The user's own backup: everything comes back as it was and the first setup is not needed. */
    fun confirmRestore() {
        val json = _pendingRestore.value?.first ?: return
        _pendingRestore.value = null
        _saving.value = true
        c.appScope.launch {
            runCatching {
                c.backupManager.restore(json)
                c.telemetry.event("setup_restore")
                com.bamnat.app.data.DiscoverStore.get(c.appContext).restored() // 1.40 [DISC-03]
                // A backup from before the kind of work was asked, without a pattern: the setup goes on.
                if (c.settingsRepository.current.setupDone) store.clear()
                // [SETUP-16] 1.37 PC f42b2eba HIGH-2: a backup made while its first setup was put off goes on here, from its step, and says so.
                else if (c.settingsRepository.current.setupLater) {
                    store.draft.value?.let { store.save(it.copy(later = false)) }
                    _note.value = "백업을 불러왔어요. ${c.settingsRepository.current.workStyle?.label ?: "근무"} 설정을 멈췄던 곳부터 이어서 해요"
                }
            }.onFailure { e -> _message.value = (e as? BackupException)?.message ?: "백업을 불러오지 못했어요" }
            _saving.value = false
        }
    }

    /** [COMM-00][SETUP-02] A coworker's company file: its shifts and order, with their 조 when it says one. */
    internal fun importCompany(text: String) {
        val shared = c.companyShare.read(text)
        val d = current
        val style = d.style?.takeIf { it.usesPattern || it == WorkStyle.HOURLY }
            ?: if (shared.order.isNotEmpty() || shared.roster) WorkStyle.SHIFT else WorkStyle.DAY
        val base = (if (d.style == style) d else SetupDraft(style = style, companyName = d.companyName))
            .let { if (shared.roster && style == WorkStyle.SHIFT) it.copy(rosterMonthly = true) else it }
        val source = SetupSource(SetupSource.Kind.FILE, text, name = shared.company, team = shared.team, anchor = shared.anchor)
        val withFile = base.withShared(source, shared.types, shared.order, shared.weekly)
        _fixing.value = false
        c.telemetry.event("setup_file", if (shared.team != null) "team" else "")
        store.save(withFile.copy(step = SetupStep.TIMES))
    }

    // ---- 2. the company ----

    val templates: List<CompanyTemplate> get() = CompanyTemplates.forStyle(current.style)

    fun pickTemplate(t: CompanyTemplate) {
        val text = c.companyShare.templateText(t)
        // A template says whether its roster comes every month (병원 간호) or its order repeats.
        val roster = when {
            current.style != WorkStyle.SHIFT -> null
            t.roster -> true
            t.order.isNotEmpty() -> false
            else -> null
        }
        useShared(SetupSource(SetupSource.Kind.TEMPLATE, text, id = t.id, name = t.name), text, roster)
        c.telemetry.event("setup_template", t.id)
    }

    /** [COMM-01] A coworkers' setup from the company server, without its 조: today's shift is asked next. */
    fun pickServer(page: CompanyPage, setup: SharedSetup) {
        useShared(
            SetupSource(SetupSource.Kind.SERVER, setup.text, id = page.id, setupId = setup.id, name = page.name, dept = setup.dept,
                version = setup.version, adopted = setup.adopted),
            setup.text,
        )
        c.telemetry.event("company_apply", if (setup.adopted) "adopted" else "pending")
    }

    /**
     * [SPAT-02][SPAT-03] A public row (or the user's own company, first to give it) with the order picked on its sheet: read
     * as a company file, so the times, the order and today are asked next as for any shared setup. [SetupSource.dept] keeps
     * the 근무형태 key for a first provider's row.
     */
    fun pickPublic(cycle: PublicCycle, codes: String) {
        // [CYC-04] 1.38 업종 기념일.
        com.bamnat.app.ui.calendar.rememberOrgGroup(c.settingsRepository, cycle)
        val text = PublicCycleFiles.text(cycle, codes)
        val roster = if (current.style == WorkStyle.SHIFT) cycle.roster else null
        useShared(SetupSource(SetupSource.Kind.PUBLIC, text, id = cycle.id, name = cycle.company, dept = cycle.formKey), text, roster)
        // 1.38 출시 점검 D3 (사용자 2026-11-07, 추천안): a choice that sends something to a server starts unticked, like 사용 통계,
        // so the summary's 올리기 box starts empty even for a table made by '우리 회사 근무표 올리기'; the user ticks it there.
        shareCycle.value = false
        c.telemetry.event("setup_public", if (cycle.id.isEmpty()) "provided" else cycle.orderState.name.lowercase())
    }

    private fun useShared(source: SetupSource, text: String, roster: Boolean? = null) {
        val shared = try {
            c.companyShare.read(text)
        } catch (e: BackupException) {
            _message.value = e.message ?: "설정을 읽지 못했어요"
            return
        }
        // Only a coworker's own file says which 조 it is; a template or the server does not.
        // [PLAZA-01] A company whose file says its roster comes every month is painted, not repeated.
        val monthly = roster ?: shared.roster.takeIf { it && current.style == WorkStyle.SHIFT }
        val base = monthly?.let { current.copy(rosterMonthly = it) } ?: current
        val d = base.withShared(source.copy(team = null, anchor = null), shared.types, shared.order, shared.weekly).let { d ->
            // [J133-26] 1.33 (실사용 테스트 "회사 이름이 고른 회사 대신 검색어('현대')로 저장돼요"): the company picked is its name.
            if (source.kind != SetupSource.Kind.TEMPLATE && source.name.isNotBlank()) d.copy(companyName = source.name.take(40)) else d
        }
        // New shifts are a new setup from here on, not a fix of one answer.
        _fixing.value = false
        store.save(d.copy(step = d.steps.firstOrNull { it != SetupStep.STYLE && it != SetupStep.COMPANY } ?: SetupStep.SUMMARY))
    }

    /**
     * The order the shared setup came with, by this draft's shift ids (given in the file's order when
     * it was read, so a shift renamed since still matches); null without one.
     */
    fun sourceOrder(d: SetupDraft): List<Long?>? {
        val text = d.source?.text ?: return null
        val shared = runCatching { c.companyShare.read(text) }.getOrNull() ?: return null
        if (shared.order.isEmpty()) return null
        val ids = shared.types.mapIndexed { i, t -> t.name to -(i + 1L) }.toMap()
        return shared.order.map { name -> name?.let(ids::get) }
    }

    /** Back to choosing: the shifts start again from the kind of work. */
    fun clearSource() {
        val d = current
        val style = d.style ?: return
        _fixing.value = false
        val again = fresh(style, d.rosterMonthly).copy(companyName = d.companyName)
        // [J133-71] 알바 has no company step: back to its first question.
        store.save(again.copy(step = if (SetupStep.COMPANY in again.steps) SetupStep.COMPANY else again.steps.getOrElse(1) { SetupStep.STYLE }))
    }

    fun setCompanyName(name: String) = update { it.copy(companyName = name.take(40)) }

    // ---- 3. bundles ----

    fun pickBundle(key: String) {
        // The same answer again keeps the times already changed.
        if (current.bundle == key && current.types.isNotEmpty()) return update { it.copy(step = it.next() ?: SetupStep.SUMMARY) }
        val shifts = ShiftBundles.byKey(key)?.shifts ?: ShiftBundles.custom
        val d = current.withShifts(ShiftBundles.byKey(key)?.key ?: SetupDraft.CUSTOM, shifts).let { d ->
            // [EASE-07] 1.38: the bundle's most usual order is picked already on the times' screen (one tap less); today's month
            // preview and '회사 근무표와 같아요' come before anything is saved.
            val first = if (d.usesRotation) presets(d).firstOrNull() else null
            if (first != null) d.copy(rotation = PatternPresets.toSteps(first, d.roles)) else d
        }
        _fixing.value = false
        c.telemetry.event("setup_bundle", key)
        store.save(d.copy(step = d.next() ?: SetupStep.SUMMARY))
    }

    // ---- 4. times ----

    fun saveType(type: ShiftType) = update { d ->
        if (d.types.any { it.id == type.id }) {
            d.copy(types = d.types.map { if (it.id == type.id) type else it }, roles = d.roles + (type.id to (d.roles[type.id]?.takeIf { r -> r.isOff == type.isOff } ?: ShiftRole.guess(type))))
        } else {
            d.copy(types = d.types + type.copy(sortOrder = d.types.size), roles = d.roles + (type.id to ShiftRole.guess(type)))
        }
    }

    /** A new work shift to fill in, after the work shifts there are. */
    fun newType(): ShiftType {
        val d = current
        return ShiftType(d.nextTypeId(), "", "", NEW_COLORS[d.types.size % NEW_COLORS.size], LocalTime.of(9, 0), LocalTime.of(18, 0), breakMinutes = 60)
    }

    fun removeType(id: Long) = update { d ->
        d.copy(
            types = d.types.filter { it.id != id }, roles = d.roles - id,
            rotation = d.rotation.map { if (it == id) null else it }, weekly = d.weekly.map { if (it == id) null else it },
            painted = d.painted.filterValues { it != id }, alarmByType = d.alarmByType - id,
        )
    }

    /** "네, 맞아요". */
    fun confirmTimes() {
        update { it.copy(timesConfirmed = true) }
        next()
    }

    // ---- 5. the order ----

    /** The usual orders the shifts can fill, from the bundle or from the parts the shifts play. */
    fun presets(d: SetupDraft): List<PatternPreset> =
        ShiftBundles.byKey(d.bundle)?.presets ?: PatternPresets.forCodes(d.roles.filterKeys { id -> d.workTypes.any { it.id == id } }.values.map { it.code }.toSet())

    fun pickPreset(preset: PatternPreset) = update { d ->
        d.copy(rotation = PatternPresets.toSteps(preset, d.roles), anchor = null, todayConfirmed = false)
    }

    fun setRotation(steps: List<Long?>) = update { it.copy(rotation = steps.take(MAX_STEPS), anchor = null, todayConfirmed = false) }

    fun setWeekly(day: Int, id: Long?) = update { d -> d.copy(weekly = d.weekly.mapIndexed { i, v -> if (i == day) id else v }) }

    fun setNoFixedDays(on: Boolean) = update { it.copy(noFixedDays = on) }
    fun setHolidaysOff(on: Boolean) = update { it.copy(holidaysOff = on) }

    // [SETUP-02] Painting a monthly roster.
    private val _paintMonth = MutableStateFlow(YearMonth.from(now()))
    val paintMonth: StateFlow<YearMonth> = _paintMonth.asStateFlow()
    private val _paintTool = MutableStateFlow<PaintTool>(PaintTool.Pick)
    val paintTool: StateFlow<PaintTool> = _paintTool.asStateFlow()
    private val _paintUndo = MutableStateFlow<Map<LocalDate, Long?>?>(null)
    val canUndoPaint: StateFlow<Boolean> = _paintUndo.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun showPaintMonth(month: YearMonth) {
        _paintMonth.value = month
    }

    fun setPaintTool(tool: PaintTool) {
        _paintTool.value = if (_paintTool.value == tool) PaintTool.Pick else tool
    }

    fun paint(dates: List<LocalDate>) {
        val tool = _paintTool.value
        if (tool == PaintTool.Pick || dates.isEmpty()) return
        _paintUndo.value = current.painted
        leavesUndo = null
        update { d ->
            d.copy(painted = when (tool) {
                is PaintTool.Shift -> d.painted + dates.associateWith { tool.shiftTypeId }
                PaintTool.Off -> d.painted + dates.associateWith { null }
                else -> d.painted - dates.toSet()
            })
        }
    }

    /** A tap on one day: paints it, or takes the paint off when the day already has what is chosen. */
    fun paintTap(date: LocalDate) {
        val d = current
        val same = when (val tool = _paintTool.value) {
            PaintTool.Pick -> {
                _message.value = "위에서 근무를 먼저 고르세요"
                return
            }
            is PaintTool.Shift -> date in d.painted && d.painted[date] == tool.shiftTypeId
            PaintTool.Off -> date in d.painted && d.painted[date] == null
            PaintTool.Clear -> false
        }
        if (!same) return paint(listOf(date))
        _paintUndo.value = d.painted
        update { it.copy(painted = it.painted - date) }
    }

    fun undoPaint() {
        val before = _paintUndo.value ?: return
        _paintUndo.value = null
        val leaves = leavesUndo
        leavesUndo = null
        update { it.copy(painted = before, leaves = leaves ?: it.leaves) }
    }

    /** The leave before a roster's line was put in, so [undoPaint] takes the whole line back. */
    private var leavesUndo: Map<LocalDate, com.bamnat.core.model.Leave>? = null

    // [SETUP-15] 듀티표가 있어요 (중-8, 리뷰 P9): a roster photo or file read the same way as 한 달 근무 넣기, its line
    // painted onto the month it names. [READ-05] 1.25 순번 371: a roster of several lines asks whose it is first; its leave
    // goes in with the setup and a code the setup's kinds do not cover is asked here, so nothing is left for "after".
    private val _roster = MutableStateFlow<com.bamnat.app.ui.imports.RosterResult?>(null)
    val roster: StateFlow<com.bamnat.app.ui.imports.RosterResult?> = _roster.asStateFlow()
    private val _rosterReading = MutableStateFlow(false)
    val rosterReading: StateFlow<Boolean> = _rosterReading.asStateFlow()
    /** 1.36 PC W10: which reading of the photo is under way (n of m; m 0 while the first one runs), for the reading card. */
    private val _rosterStep = MutableStateFlow(0 to 0)
    val rosterStep: StateFlow<Pair<Int, Int>> = _rosterStep.asStateFlow()
    private var rosterJob: kotlinx.coroutines.Job? = null
    /** Codes of the picked line the setup's kinds do not cover ("M", "교육"), with their days, one asked at a time. */
    private val _rosterAsks = MutableStateFlow<List<Pair<com.bamnat.core.imports.DutyCell.Unknown, List<LocalDate>>>>(emptyList())
    val rosterAsks: StateFlow<List<Pair<com.bamnat.core.imports.DutyCell.Unknown, List<LocalDate>>>> = _rosterAsks.asStateFlow()

    private fun rosterCodes(): com.bamnat.core.imports.DutyCodes = com.bamnat.core.imports.DutyCodes(
        current.types.map { com.bamnat.core.imports.DutyType(it.id, it.name, it.label, it.isOff, it.start, com.bamnat.core.model.NightShift.of(it)) },
    )

    /**
     * [READ-17] 1.27 "사진과 내용이 같은가요?" for the setup's roster photo: null while asked, false while fixing, true once
     * the same. [rosterRank] is the picked line's place in the table, to find it on the photo.
     */
    private val _rosterSame = MutableStateFlow<Boolean?>(null)
    val rosterSame: StateFlow<Boolean?> = _rosterSame.asStateFlow()
    var rosterRank = 0
        private set

    fun rosterSame(yes: Boolean?) { _rosterSame.value = yes }

    /** [READ-17] One day of the roster's month fixed from the photo card: painted, given leave, or left off (휴무). */
    fun fixRosterDay(date: LocalDate, cell: com.bamnat.core.imports.DutyCell?) {
        when (cell) {
            is com.bamnat.core.imports.DutyCell.Shift -> update { it.copy(painted = it.painted + (date to cell.shiftTypeId), leaves = it.leaves - date) }
            com.bamnat.core.imports.DutyCell.Off -> update { it.copy(painted = it.painted + (date to null), leaves = it.leaves - date) }
            is com.bamnat.core.imports.DutyCell.Leave -> update { d -> d.copy(painted = d.painted + leaveWork(d, listOf(date)), leaves = d.leaves + (date to cell.leave)) }
            else -> Unit
        }
    }

    fun readRoster(how: suspend (com.bamnat.core.imports.DutyCodes) -> com.bamnat.app.ui.imports.RosterResult) {
        rosterJob?.cancel()
        _rosterReading.value = true
        _rosterStep.value = 0 to 0
        _rosterSame.value = null
        rosterRank = 0
        rosterJob = viewModelScope.launch {
            val started = System.nanoTime()
            // 1.36 PC W10 (사진을 고른 뒤 20초 넘게 아무 말 없음): a reading that takes too long ends with why and what to do.
            // 1.39.1 [R391-13] The last net: a reading that fails in a way the reader did not catch ends with why, not a closed app.
            val r = try {
                kotlinx.coroutines.withTimeoutOrNull(ROSTER_READ_LIMIT_MS) { how(rosterCodes()) }
                    ?: com.bamnat.app.ui.imports.RosterResult(emptyList(), null, com.bamnat.core.journal.RosterFrom.PHOTO, ROSTER_TOO_LONG)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                com.bamnat.app.ui.imports.RosterResult(emptyList(), null, com.bamnat.core.journal.RosterFrom.FILE, com.bamnat.app.pay.OcrModel.why(e).text)
            }
            _rosterReading.value = false
            // The steps of the reading, for 진단 (no words of the photo): how long, how many ways, words and lines found.
            runCatching {
                val ms = (System.nanoTime() - started) / 1_000_000
                c.telemetry.event("roster_read", "${r.from.name.lowercase()} ms=$ms ways=${r.scan?.ways ?: 0} words=${r.scan?.words?.size ?: 0} " +
                    "rows=${r.rows.size} problem=${r.scan?.read?.problem?.name?.lowercase() ?: "-"}${if (r.error != null && r.rows.isEmpty()) " fail" else ""}")
            }
            val rows = r.rows.sortedBy { it.line }
            // A photo that read nothing keeps its picture, for 사진 위에서 누르기.
            _roster.value = r.copy(rows = rows, scan = r.scan ?: _roster.value?.scan.takeIf { r.from == com.bamnat.core.journal.RosterFrom.PHOTO })
            _rosterAsks.value = emptyList()
            if (rows.size == 1) useRosterRow(0) else if (r.error != null) _message.value = r.error
        }
    }

    fun readRosterPhoto(context: Context, uri: Uri) = readRoster {
        com.bamnat.app.ui.imports.RosterReader.photo(context, uri, it, today) { n, m -> _rosterStep.value = n to m }
    }

    /**
     * [EASE-16] 1.38 "근무표 사진으로 한 번에" on the company page: without kinds of work yet the roster template's (데이·이브닝·나이트·
     * 오프, 병원 간호) are taken, the setup goes on to its times, and the photo is read meanwhile; the painting step shows it.
     */
    fun rosterPhotoFirst(context: Context, uri: Uri) {
        if (current.types.isEmpty()) templates.firstOrNull { it.roster }?.let(::pickTemplate)
        readRosterPhoto(context, uri)
    }

    private val _captureReading = MutableStateFlow(false)
    /** [GROW-05] A screenshot of another app's month being read for its order. */
    val captureReading: StateFlow<Boolean> = _captureReading.asStateFlow()

    /**
     * [GROW-05] 1.38 다른 앱에서 옮기기: a screenshot of the month view of the app used before (or any month calendar with the
     * work on it) read on the phone, and the order it repeats put in as the setup's order with today's place in it. Only
     * the picture is read: no other app's files or servers. Says why when the picture shows no order.
     */
    fun orderFromCapture(context: Context, uri: Uri) {
        if (_captureReading.value) return
        _captureReading.value = true
        viewModelScope.launch {
            val r = runCatching { com.bamnat.app.ui.imports.RosterReader.photo(context, uri, rosterCodes(), today) }.getOrNull()
            _captureReading.value = false
            val row = r?.rows?.maxByOrNull { it.cells.size }
            val days = row?.cells.orEmpty().mapNotNull { (d, cell) ->
                when (cell) {
                    is com.bamnat.core.imports.DutyCell.Shift -> d to cell.shiftTypeId.takeIf { id -> current.types.firstOrNull { it.id == id }?.isOff != true }
                    com.bamnat.core.imports.DutyCell.Off -> d to null
                    else -> null
                }
            }.toMap()
            val order = com.bamnat.core.imports.CalendarShot.order(days)
            val month = r?.month ?: java.time.YearMonth.from(today)
            if (order == null) {
                _message.value = if (row == null) "캡처에서 달력의 날짜와 근무를 찾지 못했어요. 한 달이 다 나온 화면을 캡처해 주세요."
                    else "캡처에서 되풀이되는 순서를 찾지 못했어요. 매달 근무표가 바뀐다면 ‘잘 모르겠어요’에서 칠해 주세요."
                return@launch
            }
            setRotation(order.steps)
            // The 1st of the captured month is step 0: today's place follows, asked once more on the next step.
            answerToday(month.atDay(1), 0)
            _message.value = "캡처에서 ${order.steps.size}일 순서를 찾았어요. 맞는지 아래 달력으로 확인해 주세요."
        }
    }

    /** 1.36 PC W10: 취소 on the reading card; nothing is changed. */
    fun cancelRosterRead() {
        rosterJob?.cancel()
        rosterJob = null
        _rosterReading.value = false
        _message.value = "읽기를 멈췄어요. 다른 사진으로 하거나 ‘직접 칠하기’로 넣어 주세요."
    }

    fun readRosterFile(context: Context, uri: Uri) = readRoster { com.bamnat.app.ui.imports.RosterReader.file(context, uri, it, today) }

    fun readRosterText(text: String) = readRoster { com.bamnat.app.ui.imports.RosterReader.text(text, it, today) }

    /** [READ-04] The roster photo read again along the two lines the user tapped. */
    fun readRosterTapped(hint: com.bamnat.core.imports.RosterHint) {
        val scan = _roster.value?.scan ?: return
        readRoster { com.bamnat.app.ui.imports.RosterReader.tapped(scan, it, hint, today) }
    }

    fun clearRoster() {
        _roster.value = null
        _rosterAsks.value = emptyList()
        _sheetKinds.value = null
    }

    /** [SETUP-15] The picked line onto the painted days of the roster's month, its leave with it; one undo takes it back. */
    fun useRosterRow(i: Int) {
        val r = _roster.value ?: return
        val row = r.rows.getOrNull(i) ?: return
        val month = r.month ?: _paintMonth.value
        // [READ-30] 1.38 PC: a 4주 듀티 file's days in the month before or after go on too.
        val cells = row.cells.filterKeys { it <= month.lengthOfMonth() }.mapKeys { month.atDay(it.key) } + row.outside
        val days = cells.mapNotNull { (date, cell) ->
            when (cell) {
                is com.bamnat.core.imports.DutyCell.Shift -> date to cell.shiftTypeId
                com.bamnat.core.imports.DutyCell.Off -> date to null
                else -> null
            }
        }.toMap()
        val leaves = cells.mapNotNull { (date, cell) -> (cell as? com.bamnat.core.imports.DutyCell.Leave)?.let { date to it.leave } }.toMap()
        val asks = cells.entries.mapNotNull { (date, cell) -> (cell as? com.bamnat.core.imports.DutyCell.Unknown)?.let { it to date } }
            .groupBy({ it.first.raw }, { it }).map { (_, list) -> list.first().first to list.map { it.second } }
        if (days.isEmpty() && leaves.isEmpty()) {
            _message.value = "근무 칸을 찾지 못했어요. 직접 칠해 주세요."
            return
        }
        _paintUndo.value = current.painted
        leavesUndo = current.leaves
        // [READ-30] 1.38 PC (x03): the roster's 연차 is a working day taken off: painted with the line's main work, so the
        // leave counts, instead of a blank day the leave means nothing on.
        val main = com.bamnat.core.imports.DutyRoster.mainShift(cells.values) ?: current.types.firstOrNull { !it.isOff }?.id
        val leaveDays = if (main == null) emptyMap() else leaves.keys.filter { it !in days }.associateWith { main }
        update { it.copy(painted = it.painted + days + leaveDays, leaves = it.leaves - days.keys + leaves) }
        _paintMonth.value = month
        if (r.rows.size > 1) rosterRank = i
        _roster.value = r.copy(rows = listOf(row))
        _rosterAsks.value = asks
        // [READ-36] The sheet's own letters (주·야·비·휴) the setup's kinds do not use: offered as the kinds, once.
        _sheetKinds.value = com.bamnat.core.imports.SheetKinds.of(r.rows.flatMap { it.raw.values }, current.types, ::roleOfType)
        val spill = row.outside.keys.sorted()
        _message.value = "${month.monthValue}월 ${days.size}일을 칠했어요" +
            (if (spill.isNotEmpty()) " (${com.bamnat.app.ui.imports.RosterReader.span(spill)} 포함)" else "") +
            (if (leaves.isNotEmpty()) " · 휴가 ${leaves.size}일도 넣어요" else "") +
            (if (asks.isNotEmpty()) " · 모르는 글자 ${asks.size}개는 아래에서 골라 주세요" else "")
    }

    /**
     * [READ-30] Leave [dates] with no work painted yet: the work they take off, the month's most painted kind (the roster's
     * main shift), so the leave counts.
     */
    private fun leaveWork(d: com.bamnat.core.setup.SetupDraft, dates: List<LocalDate>): Map<LocalDate, Long?> {
        val main = d.painted.values.filterNotNull().groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            ?: d.types.firstOrNull { !it.isOff }?.id ?: return emptyMap()
        return dates.filter { d.painted[it] == null }.associateWith { main }
    }

    private val _sheetKinds = MutableStateFlow<List<com.bamnat.core.schedule.BundleShift>?>(null)
    /** [READ-36] Kinds named by the roster's own letters, offered once after it is read; null when not. */
    val sheetKinds: StateFlow<List<com.bamnat.core.schedule.BundleShift>?> = _sheetKinds.asStateFlow()

    fun keepKinds() { _sheetKinds.value = null }

    /** [READ-41] The part a kind of the setup plays: as picked, else guessed from its times. */
    private fun roleOfType(t: com.bamnat.core.model.ShiftType): com.bamnat.core.schedule.ShiftRole =
        current.roles[t.id] ?: com.bamnat.core.schedule.ShiftRole.guess(t)

    /** [READ-41] 1.38 PC 12.3: whether the offered kinds keep the times picked in the setup (데이 06~14 → 주간 06~14). */
    fun sheetKindsKeepTimes(kinds: List<com.bamnat.core.schedule.BundleShift>): Boolean =
        com.bamnat.core.imports.SheetKinds.keepsTimes(kinds, current.types, ::roleOfType)

    /**
     * [READ-36] 1.38 PC 8차: the setup's kinds become the sheet's own (주간 주 · 야간 야 · 비번 비 · 휴무 휴) and the picked line is
     * read again with them from what each cell said, so 비번 and 휴무 stay apart; days the photo lost are filled from the
     * 조 repeat again (yellow). The times are the usual ones, to change on the times' step.
     */
    fun useSheetKinds() {
        val kinds = _sheetKinds.value ?: return
        _sheetKinds.value = null
        val r = _roster.value ?: return
        val row = r.rows.singleOrNull() ?: return
        val before = rosterCodes()
        val oldRoles = current.roles
        val oldTypes = current.types
        update { it.withShifts(com.bamnat.core.setup.SetupDraft.CUSTOM, kinds) }
        val byRole = current.roles.entries.associate { (id, role) -> role to id }
        val again = com.bamnat.core.imports.SheetKinds.remap(row, before, rosterCodes(), { id -> oldRoles[id] ?: oldTypes.firstOrNull { it.id == id }?.let(com.bamnat.core.schedule.ShiftRole::guess) }, { byRole[it] })
        _roster.value = r.copy(rows = listOf(again))
        useRosterRow(0)
        _sheetKinds.value = null
        _message.value = "근무 종류를 ${kinds.joinToString("·") { it.type.name }}로 맞췄어요. 시간은 고른 시간 그대로이고, ‘이전’의 근무 시간에서 바꿀 수 있어요"
    }

    /** [SETUP-15] What a code the setup did not know means: its days painted (or given leave), or left as they are (null). */
    fun answerRosterCode(raw: String, cell: com.bamnat.core.imports.DutyCell?) {
        val ask = _rosterAsks.value.firstOrNull { it.first.raw == raw } ?: return
        _rosterAsks.value = _rosterAsks.value - ask
        val dates = ask.second
        when (cell) {
            is com.bamnat.core.imports.DutyCell.Shift -> update { it.copy(painted = it.painted + dates.associateWith { cell.shiftTypeId }, leaves = it.leaves - dates.toSet()) }
            com.bamnat.core.imports.DutyCell.Off -> update { it.copy(painted = it.painted + dates.associateWith { null }, leaves = it.leaves - dates.toSet()) }
            is com.bamnat.core.imports.DutyCell.Leave -> update { d -> d.copy(painted = d.painted + leaveWork(d, dates), leaves = d.leaves + dates.associateWith { cell.leave }) }
            else -> Unit
        }
    }

    // ---- 6. today ----

    /** Today's answer: [step] of the order on [date]. */
    fun answerToday(date: LocalDate, step: Int) = update { it.copy(anchor = TodayQuestion.anchor(date, step), todayConfirmed = false) }

    /** [SETUP-02] Same 조 as the coworker who sent the file: their order lines up as it is. */
    fun sameTeam() = update { d -> d.copy(anchor = d.source?.anchor, todayConfirmed = false) }

    /**
     * [SETUP-14] "잘 모르겠어요": the month is painted by hand instead of a repeating order. The shifts stay; the
     * order and today's answer go, and the setup goes to the painting step.
     */
    fun paintInstead() = update { d ->
        d.copy(rosterMonthly = true, rotation = emptyList(), anchor = null, todayConfirmed = false, step = SetupStep.ORDER)
    }

    fun clearToday() = update { it.copy(anchor = null, todayConfirmed = false) }

    /** [CYC-06] 1.39: the 조 the setup's source names, as 모든 조 보기 with the one answered marked mine; null without them. */
    private fun teamsOf(d: SetupDraft): com.bamnat.core.schedule.TeamRoster? {
        val source = d.source ?: return null
        val anchor = d.anchor ?: return null
        if (d.rotation.size < 2 || sourceOrder(d) != d.rotation) return null
        val teams = when (source.kind) {
            SetupSource.Kind.PUBLIC -> source.id?.let(com.bamnat.core.plaza.PublicCycles::byId)?.teams.orEmpty()
            SetupSource.Kind.FILE -> com.bamnat.app.data.CompanyShare.teamsOf(source.text)
            else -> emptyList()
        }
        return com.bamnat.core.setup.TeamAnchors.roster(teams, anchor, d.rotation.size)
    }

    /** [CYC-06] "몇 조예요?" answered: the day that team starts the cycle. */
    fun answerAnchor(date: LocalDate) = update { it.copy(anchor = date, todayConfirmed = false) }

    private val _todayAsk = MutableStateFlow<String?>(null)
    /** [EASE-08] What the today question still waits for (a second question open), said by the bottom button. */
    val todayAsk: StateFlow<String?> = _todayAsk.asStateFlow()
    fun todayAsking(line: String?) { _todayAsk.value = line }

    /** "회사 근무표와 같아요". */
    fun confirmToday() {
        update { it.copy(todayConfirmed = true) }
        next()
    }

    // ---- 7. alarms ----

    fun setAlarm(minutes: Int?) = update { it.copy(alarmMinutes = minutes, alarmByType = emptyMap()) }

    fun setAlarmFor(typeId: Long, minutes: Int?) = update { it.copy(alarmByType = it.alarmByType + (typeId to minutes)) }

    /** "근무마다 다르게": each shift starts from the common choice. */
    fun alarmPerShift(on: Boolean) = update { d ->
        d.copy(alarmByType = if (on) d.workTypes.associate { it.id to d.alarmMinutes } else emptyMap())
    }

    /** The first alarm the saved setup will ring, for the summary. */
    fun firstAlarm(d: SetupDraft): PlannedAlarm? {
        val at = now()
        val snapshot = ScheduleSnapshot(typesToSave(d), d.pattern(at.toLocalDate()), d.paintedEntries())
        return AlarmPlanner.nextAlarm(snapshot.resolver, at)
    }

    /** 1.39 [FLOW-03] What the saved setup puts on the calendar from today, said in one line; null when no day works. */
    internal fun fillLine(d: SetupDraft): com.bamnat.core.setup.FillMoment.Line? {
        val snapshot = ScheduleSnapshot(typesToSave(d), d.pattern(today), d.paintedEntries())
        if (snapshot.isEmpty) return null
        return com.bamnat.core.setup.FillMoment.of(snapshot.resolver.resolveRange(today, today.plusDays(70)), today, firstAlarm(d)?.triggerAt)
    }

    // ---- 8. saving ----

    fun save() = save(current)

    private fun save(d: SetupDraft) {
        if (_saving.value) return
        _saving.value = true
        // The setup screen goes away as soon as the kind of work is saved, so the save outlives it.
        c.appScope.launch {
            runCatching { if (calendarId != null) saveCalendar(calendarId, d) else c.firstSetup.save(d, keep = again) }
                .onSuccess {
                    // 1.39 [SETUP-20] The first setup of a new install is saved: its tap count waits for 사용 통계.
                    if (calendarId == null && !again) c.setupTaps.finish(d.savedStyle)
                    // [RVG-18] 검토 PC-22 (도약 14): started from a link of next month's 근무표, the calendar opens on that month.
                    if (d.linkTitle.isNotBlank()) d.openingMonth(today).takeIf { it != java.time.YearMonth.from(today) }?.let(c.shownMonth::show)
                    // [RVG-16] Started from a link as 일근·알바: that kind of work's notices, not the 교대 ones turned on with the link.
                    if (calendarId == null && d.workAs != null && d.workAs != d.style)
                        c.settingsRepository.update { com.bamnat.app.data.RecommendedSettings.apply(it, com.bamnat.core.consent.RecommendedPlan.forStyle(d.workAs).start) }
                    // 1.39 사용자 2026-11-08: 잠 in the place picked on the summary ([sleepSlot]).
                    if (offersSleepBar && sleepSlot.value >= 0) runCatching { putSleepOnBar(sleepSlot.value) }
                        .onFailure { android.util.Log.w("Setup", "sleep on the bar", it) }
                    // 1.39 [FLOW-03] The month fills over the calendar with its one line ("이번 달 야간 8번 · 첫 알람 내일 06:00").
                    runCatching { fillLine(d) }.getOrNull()?.let { line ->
                        com.bamnat.app.ui.calendar.SetupFill.set(com.bamnat.app.ui.calendar.SetupFill.Moment(calendarId ?: com.bamnat.app.data.PersonalCalendar.MAIN, line))
                    }
                    if (again) { store.clear(); _done.value = true }
                    // [J133-51] The day the calendar was set up: no question cards or tips on top of it.
                    val cal = calendarId ?: com.bamnat.app.data.PersonalCalendar.MAIN
                    c.asks.set(cal, com.bamnat.core.profile.AskScheduler.rest(c.asks.of(cal), today))
                    // [CYC-06] 1.39: "몇 조예요?" answered from a row or a coworker's file sets up 모든 조 보기 too, unless it is set.
                    runCatching { teamsOf(d)?.let { r -> if (c.teams.get(cal) == null) c.teams.set(cal, r) } }
                    // 1.38 통합 11차 (식당 운영: 영업일을 정하기 전까지 모든 칸이 '쉬는 날'): [EASE-09] keeps the calendar first, so the shop
                    // opens every day 10~22 at once; 영업일·정기휴무 changes it whenever the user wants.
                    if (d.style == WorkStyle.BUSINESS && c.calendars.get(cal)?.shop == null)
                        runCatching { com.bamnat.app.ui.work.ShopKeeper.apply(c, cal, com.bamnat.core.work.ShopRules()) }
                            .onFailure { android.util.Log.w("Setup", "shop days", it) }
                    // [J133-52] The setup came from a company: the first week's card does not ask for one again.
                    if (com.bamnat.app.ui.guide.StartStepsState.fromCompany(d.source)) com.bamnat.app.ui.guide.StartStepsState.companyPicked(c)
                    // [PLAZA-14] 1.27 AH: a cycle from a company search is shared for the calendar it went into. [EASE-03] 1.38 PC 994c2328:
                    // asked on the summary (one box), not in a window over the new calendar.
                    d.source?.takeIf { it.kind == SetupSource.Kind.PUBLIC }?.let { s ->
                        c.leadShare.afterSetup(com.bamnat.app.company.LeadShare.Pick(s.name, s.text, calendarId ?: com.bamnat.app.data.PersonalCalendar.MAIN,
                            firstGiven = s.id.isNullOrEmpty()), shareCycle.value)
                    }
                    c.telemetry.event("setup_done", buildString {
                        append(d.savedStyle?.name)
                        d.source?.let { append('_').append(it.kind.name) }
                    })
                }
                .onFailure { e ->
                    android.util.Log.w("Setup", "The setup was not saved", e)
                    _message.value = "저장하지 못했어요. 한 번 더 눌러 주세요"
                }
            _saving.value = false
        }
    }

    /** 1.39 사용자 2026-11-08: the bar with 잠 in place [index] (one the summary offered: never the home screen's). */
    private fun putSleepOnBar(index: Int) {
        val key = com.bamnat.core.layout.TabSlots.FEATURE + com.bamnat.core.guide.Place.SLEEP.name
        c.settingsRepository.update { s ->
            val bar = com.bamnat.core.layout.TabSlots.resolve(s.tabSlots, s.tabOrder, com.bamnat.app.ui.TabPicks.known)
            if (index !in com.bamnat.core.layout.TabSlots.swapChoices(bar, key)) s
            else s.withBar(com.bamnat.core.layout.TabSlots.replace(bar, index, key))
        }
        c.telemetry.event("bar_sleep", "setup")
    }

    /**
     * [MCAL-02] Saves the added calendar's answers into its own database, then lists and shows it.
     * Field and shop work keep the seeded shifts, as on the first launch.
     */
    private suspend fun saveCalendar(id: String, d: SetupDraft) {
        val style = requireNotNull(d.style) { "A setup without a kind of work" }
        requireNotNull(c.calendars.get(id)) { "The calendar was removed" }
        val repo = c.calendars.repository(id)
        check(repo !== c.scheduleRepository) { "An added calendar never saves into the main one" }
        val marked = d.copy(committing = true, later = false)
        withContext(Dispatchers.IO) {
            store.save(marked)
            store.flush()
        }
        repo.ensureDefaults()
        repo.applySetup(marked, today, backfill = false)
        // [CALI-05] 1.30 BB: the company chosen in this setup is this calendar's (its rules into its own pay, its 이 설정 쓰기,
        // its pay way), as the first setup does for 내 달력. 1.29 kept nothing, so the pay asked the same company again.
        runCatching { com.bamnat.app.data.FirstSetup.keepCompany(c.companyOf(id), marked, c::companySetupUsed) }
            .onFailure { android.util.Log.w("SetupViewModel", "company of $id", it) }
        c.calendars.finishSetup(id, style)
        store.clear()
        _done.value = true
    }

    companion object {
        const val MAX_STEPS = 62
        /** [RVG-16] How a member started from a 근무표 link may work: the link is a roster of shifts. */
        val LINK_WORK = listOf(WorkStyle.SHIFT, WorkStyle.DAY, WorkStyle.HOURLY)
        /** Colors for shifts the user adds, after the bundle's. */
        private val NEW_COLORS = listOf(0xFF00A88F, 0xFFE0A100, 0xFFD6457A, 0xFF27AE60, 0xFF8E44AD, 0xFF2D9CDB)
    }
}

/** 1.36 PC W10: how long a roster may take to read before it stops and says so. */
internal const val ROSTER_READ_LIMIT_MS = 45_000L
internal const val ROSTER_TOO_LONG = "읽는 데 너무 오래 걸려서 멈췄어요. 표만 크게 나오게 다시 찍거나 ‘직접 칠하기’로 넣어 주세요."
