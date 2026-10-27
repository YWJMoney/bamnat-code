package com.bamnat.app.ui.shifts

import com.bamnat.app.data.rules
import com.bamnat.core.model.ShiftIcon
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.ShiftTypeSet
import com.bamnat.core.schedule.ShiftTypeSets
import com.bamnat.core.model.WorkPattern
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDate
import com.bamnat.core.time.Today

/**
 * Shared by the shift type list and the pattern editor. [MCAL-01] Both edit one calendar: the one the
 * calendar tab showed when they were opened from it, the main one from settings.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class ShiftsViewModel(
    private val container: AppContainer,
    val calendarId: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    private val repo: com.bamnat.app.data.ScheduleRepository get() = container.calendars.repository(calendarId)
    // Taken out meanwhile: the main calendar's, as [repository] answers.
    private val active = container.calendars.calendars.map { container.calendars.repository(calendarId) }.distinctUntilChanged()

    /** [MCAL-01] The added calendar being edited, for the screens' titles; null for the main one. */
    val calendarName: StateFlow<String?> = container.calendars.calendars.map { list -> list.firstOrNull { it.id == calendarId && !it.isMain }?.name }
        .stateIn(viewModelScope, SharingStarted.Eagerly, container.calendars.get(calendarId)?.takeIf { !it.isMain }?.name)

    val shiftTypes: StateFlow<List<ShiftType>?> =
        active.flatMapLatest { it.shiftTypes }.map<List<ShiftType>, List<ShiftType>?> { it }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Null until loaded; [PatternState.pattern] is null when there is no pattern. */
    val pattern: StateFlow<PatternState?> =
        active.flatMapLatest { it.pattern }.map { PatternState(it) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings

    /** [SCHED-02][SCHED-03] The calendar's own days, to see which past records a pattern edit would move. */
    val entries: StateFlow<List<com.bamnat.core.model.DayEntry>> =
        active.flatMapLatest { it.entries }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [SHFT-01] A shift calendar (or the main one from before work styles), where each kind can carry its own premium. */
    val isShiftCalendar: Boolean
        get() = container.scopeOf(calendarId).currentSettings().workStyle.let { it == null || it == com.bamnat.core.work.WorkStyle.SHIFT }

    /** Pattern being edited; survives navigating to the shift type screen and back. */
    val draft = PatternDraft()

    fun save(type: ShiftType) {
        viewModelScope.launch { repo.saveShiftType(type) }
    }

    /**
     * Adds the set's shift types the user does not have yet [MKT-03]; with [retime], the user's shifts of the
     * same name take the set's times and break too [SCHED-05].
     */
    fun addSet(set: ShiftTypeSet, retime: Boolean = false) {
        viewModelScope.launch {
            val existing = shiftTypes.value ?: return@launch
            // Sets come with a fitting icon [FEAT-01]; the user can change or remove it.
            ShiftTypeSets.missing(set, existing).forEach { repo.saveShiftType(it.copy(icon = it.icon ?: ShiftIcon.suggest(it)?.key)) }
            if (retime) ShiftTypeSets.differing(set, existing).forEach { (mine, preset) -> repo.saveShiftType(ShiftTypeSets.retimed(mine, preset)) }
            container.telemetry.event("shift_set_added", set.name)
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteShiftType(id) }
    }

    /** [SHFT-09] Where a shift type is used, said in its delete dialog. */
    suspend fun use(id: Long): com.bamnat.app.data.ScheduleRepository.ShiftTypeUse = repo.shiftTypeUse(id)

    /** [SHFT-09] The last delete, for the snackbar's 되돌리기. */
    private val _removed = kotlinx.coroutines.flow.MutableSharedFlow<com.bamnat.app.data.ScheduleRepository.ShiftTypeRemoval>(extraBufferCapacity = 1)
    val removed: kotlinx.coroutines.flow.SharedFlow<com.bamnat.app.data.ScheduleRepository.ShiftTypeRemoval> = _removed

    /** [SHFT-09] Deletes shift type [id]; its days and work-order steps go to [replaceWith], or are emptied without one. */
    fun deleteUndoable(id: Long, replaceWith: Long? = null) {
        viewModelScope.launch {
            repo.deleteShiftTypeUndoable(id, replaceWith)?.let { _removed.emit(it) }
            container.telemetry.event("shift_type_delete", if (replaceWith != null) "replace" else "clear")
        }
    }

    /** [SHFT-09] 되돌리기: the shift type, its days, steps and 대근 as they were. Runs past the screen. */
    fun restore(removal: com.bamnat.app.data.ScheduleRepository.ShiftTypeRemoval) {
        container.appScope.launch { repo.restoreShiftType(removal) }
    }

    fun move(id: Long, delta: Int) {
        viewModelScope.launch { repo.moveShiftType(id, delta) }
    }

    /** Every pattern period, earliest first, for the history list. */
    val history: StateFlow<List<WorkPattern>> =
        active.flatMapLatest { it.patterns }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * [PLAZA-07] 결정 D79: the calendar's work order is shared by itself after the one-time yes, which this may ask.
     * [CALI-04] 1.30 BB: every calendar's, into its own company (1.29: the main calendar's only).
     */
    private fun shared() {
        container.companyOf(calendarId).autoShare.saved()
    }

    /** First pattern: also fills the last two years. */
    fun saveFirstPattern(pattern: WorkPattern, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.setFirstPattern(pattern)
            shared()
            container.settingsRepository.update { if (it.reviewAsked) it else it.copy(reviewDue = true) }
            onDone()
        }
    }

    /** Team change: the new pattern from [from] on, every earlier day as it was. */
    fun changePatternFrom(pattern: WorkPattern, from: LocalDate, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.changePatternFrom(pattern, from)
            shared()
            onDone()
        }
    }

    /** Corrects the period being worked now without starting a new one. */
    fun fixCurrentPattern(pattern: WorkPattern, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.replaceCurrentPattern(pattern)
            shared()
            onDone()
        }
    }

    fun clearPattern(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.clearPattern()
            // [SPAT-07] 1.27 AH-D3: this calendar starts again as the first time, company and all.
            container.leadShare.forget(calendarId)
            onDone()
        }
    }

    /**
     * [REST-02][REST-03] What the 근무표 점검 says about [pattern] before it is saved (순번 106-4): two of its cycles
     * from [from] read with the user's own limits as the new order turns. Empty when the check is off.
     */
    fun previewIssues(types: List<com.bamnat.core.model.ShiftType>, pattern: com.bamnat.core.model.WorkPattern, from: java.time.LocalDate): Pair<com.bamnat.core.schedule.RotationSpeed, List<com.bamnat.core.schedule.RestCheck.Issue>> {
        val speed = com.bamnat.core.schedule.RotationSpeed.of(pattern, types)
        val s = settings.value
        if (!s.restCheck) return speed to emptyList()
        val rules = speed.adjust(s.rules)
        val cycle = when (pattern) {
            is com.bamnat.core.model.WorkPattern.Rotation -> pattern.steps.size
            is com.bamnat.core.model.WorkPattern.Weekly -> 7
        }.coerceIn(7, 42)
        val resolver = com.bamnat.core.schedule.ScheduleResolver(types, pattern.withRange(from.minusDays(60), null), emptyList())
        val until = from.plusDays(cycle * 2L - 1)
        val days = generateSequence(com.bamnat.core.schedule.RestCheck.lookbackFrom(from, rules)) { it.plusDays(1) }
            .takeWhile { !it.isAfter(until) }.map(resolver::resolve).toList()
        return speed to com.bamnat.core.schedule.RestCheck.check(days, rules).filter { !it.date.isBefore(from) }
    }
}

data class PatternState(val pattern: WorkPattern?)

enum class PatternKind { ROTATION, WEEKLY }

class PatternDraft {
    var initialized by mutableStateOf(false)
        private set
    var kind by mutableStateOf(PatternKind.ROTATION)
    val rotation = mutableStateListOf<Long?>()
    val weekly = mutableStateListOf<Long?>().apply { repeat(7) { add(null) } }
    var startDate: LocalDate by mutableStateOf(Today.date())
    var endDate: LocalDate? by mutableStateOf(null)
    /** [SCHED-04] 요일 고정: 공휴일엔 쉬어요. */
    var holidaysOff by mutableStateOf(false)
    /** [SCHED-04] 요일 고정: a Saturday that is worked when Saturdays alternate; null when every Saturday is the same. */
    var altSaturday: LocalDate? by mutableStateOf(null)

    /** [SPAT-03] Unsaved edits dropped: the next screen loads the saved pattern again (a company's cycle put in meanwhile). */
    fun forget() {
        initialized = false
        rotation.clear()
        for (i in 0 until 7) weekly[i] = null
        holidaysOff = false
        altSaturday = null
    }

    /** Loads the saved pattern once; later calls keep the user's unsaved edits. */
    fun initFrom(existing: WorkPattern?, today: LocalDate, weekdayShiftId: Long? = null) {
        if (initialized) return
        when (existing) {
            is WorkPattern.Rotation -> {
                kind = PatternKind.ROTATION
                rotation.addAll(existing.steps)
            }
            is WorkPattern.Weekly -> {
                kind = PatternKind.WEEKLY
                existing.steps.forEachIndexed { i, id -> weekly[i] = id }
                holidaysOff = existing.holidaysOff
                altSaturday = existing.altSaturday
            }
            // [WORK-01] Day workers start from Monday to Friday on their day shift.
            null -> if (weekdayShiftId != null) {
                kind = PatternKind.WEEKLY
                for (i in 0 until 5) weekly[i] = weekdayShiftId
            }
        }
        // A rotation is edited by its step-0 day; its period may reach back years.
        startDate = (existing as? WorkPattern.Rotation)?.anchorDate ?: existing?.startDate ?: today
        endDate = existing?.endDate
        initialized = true
    }
}
