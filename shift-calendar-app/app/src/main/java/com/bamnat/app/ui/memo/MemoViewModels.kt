package com.bamnat.app.ui.memo

import java.time.YearMonth
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.Dispatchers
import com.bamnat.core.schedule.ResolvedDay
import com.bamnat.app.data.ScheduleSnapshot
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.core.memo.ChecklistItem
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoOrdering
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.MemoTime
import java.time.LocalDateTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import com.bamnat.core.time.Today

enum class MemoTab(val label: String) { CALENDAR("달력 메모"), GENERAL("일반 메모") }

enum class CalendarFilter(val label: String) {
    ALL("전체"), UPCOMING("다가오는 일정"), THIS_MONTH("이번 달"), PAST("지난 메모"), CHECKLIST("체크리스트"),
    /** 1.39 [FLOW-19] Memos with a photo in them ([Memo.photos]). */
    PHOTO("사진"), PINNED("고정")
}

enum class GeneralFilter(val label: String) { ALL("전체"), PINNED("고정"), CHECKLIST("체크리스트"), TEXT("글 메모"), PHOTO("사진") }

enum class MemoSort(val label: String) { UPDATED("최근 고친 순"), CREATED("최근 작성순"), TITLE("제목순") }

/** One date in the calendar memo list, with what was worked that day. */
data class MemoDateGroup(val date: LocalDate, val day: ResolvedDay?, val memos: List<Memo>)

/**
 * [MEMO-01] Whose shift to show beside [memos]' date: the calendar on screen when one of them is its own
 * or on every calendar, else the one calendar they all belong to; null (no shift) for several others.
 */
internal fun shiftCalendar(memos: List<Memo>, active: String): String? {
    if (memos.any { it.onEveryCalendar || active in it.owners }) return active
    return memos.flatMapTo(HashSet()) { it.owners }.singleOrNull()
}

/** 2.0.0 [RVI-80] What '오래된 할 일' does to them all at once. */
enum class OldWay(val label: String, val said: String) { DONE("모두 끝내기", "끝냈어요"), TODAY("모두 오늘로", "오늘로 옮겼어요"), TRASH("모두 휴지통으로", "휴지통으로 옮겼어요") }

/** 2.0.0 [RVI-80] The 할 일 위젯's '오래된 할 일' line opens the memo tab with them shown. */
object MemoLinks {
    val old = kotlinx.coroutines.flow.MutableStateFlow(false)
}

/** 2.0.0 [RVI-80] The payload of [com.bamnat.app.NoticeLinks.Target.MEMOS] that opens '오래된 할 일'. */
const val OLD_TASKS = "old"

data class MemoListState(
    val loaded: Boolean = false,
    val tab: MemoTab = MemoTab.CALENDAR,
    val query: String = "",
    val calendarFilter: CalendarFilter = CalendarFilter.ALL,
    val generalFilter: GeneralFilter = GeneralFilter.ALL,
    val sort: MemoSort = MemoSort.UPDATED,
    val tag: String? = null,
    /** Today and later, nearest first. */
    val upcoming: List<MemoDateGroup> = emptyList(),
    /** Before today, latest first. */
    val past: List<MemoDateGroup> = emptyList(),
    val general: List<Memo> = emptyList(),
    val calendarCount: Int = 0,
    val generalCount: Int = 0,
    /** Tags used in the current tab, most used first. */
    val tags: List<String> = emptyList(),
    val selection: Set<Long> = emptySet(),
    val trashCount: Int = 0,
    /** [NOTE-02] The notebook shown, and the calendars to choose from (one: no notebooks at all). */
    val notebook: com.bamnat.core.memo.MemoNotebook = com.bamnat.core.memo.MemoNotebook.CALENDAR,
    val calendars: List<com.bamnat.app.data.PersonalCalendar> = emptyList(),
    val activeCalendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
    /** [MEMO-03] 1.27 AK: the shifts worked on days with a calendar memo (for '야간 날' chips), and the one chosen. */
    val shifts: List<String> = emptyList(),
    val shift: String? = null,
    /** [EASE-24] The calendar on screen's kind of day on a date, for the 달력 보기's cells. */
    val shiftOn: (LocalDate) -> com.bamnat.core.model.ShiftType? = { null },
    /** [EASE-22] The schedule each 근무마다 memo goes by, to lay it on its days in the 달력 보기. */
    val shiftsOf: (Memo) -> com.bamnat.core.memo.MemoRepeat.ShiftOf? = { null },
    /** 1.39 [FLOW-19] 주 띠: the seven days shown, the one picked (only its memos), and how many memos each day has. */
    val week: List<LocalDate> = emptyList(),
    val day: LocalDate? = null,
    val weekCounts: Map<LocalDate, Int> = emptyMap(),
    /**
     * 2.0.0 [RVI-77] (검토 U07a-02·I07ab-01) The memo tab's '오늘' 묶음: 늦음·오늘·고정 of the memos the notebook shows, the same
     * [com.bamnat.core.memo.TaskList] the 할 일 위젯 shows.
     */
    val todayTasks: List<com.bamnat.core.memo.TaskList.Row> = emptyList(),
    /** 2.0.0 [RVI-80] (검토 U07a-09) To-dos left from before the 늦음 week ([com.bamnat.core.memo.TaskList.old]). */
    val oldTasks: List<Memo> = emptyList(),
) {
    val manyCalendars: Boolean get() = calendars.size > 1
    val activeName: String get() = calendars.firstOrNull { it.id == activeCalendar }?.name ?: com.bamnat.app.data.PersonalCalendar.MAIN_NAME
    val selecting: Boolean get() = selection.isNotEmpty()
    val shownCount: Int
        get() = if (tab == MemoTab.CALENDAR) upcoming.sumOf { it.memos.size } + past.sumOf { it.memos.size } else general.size
    val filtering: Boolean
        get() = query.isNotBlank() || tag != null ||
            (if (tab == MemoTab.CALENDAR) calendarFilter != CalendarFilter.ALL || shift != null || day != null else generalFilter != GeneralFilter.ALL)
}

private data class MemoQuery(
    val tab: MemoTab = MemoTab.CALENDAR,
    val query: String = "",
    val calendarFilter: CalendarFilter = CalendarFilter.ALL,
    val generalFilter: GeneralFilter = GeneralFilter.ALL,
    val sort: MemoSort = MemoSort.UPDATED,
    val tag: String? = null,
    /** [MEMO-03] Only the memos of days with this shift. */
    val shift: String? = null,
    /** 1.39 [FLOW-19] The 주 띠's first day (null: this week), and the one day picked on it. */
    val weekOf: LocalDate? = null,
    val day: LocalDate? = null,
    val weekStart: java.time.DayOfWeek = java.time.DayOfWeek.SUNDAY,
)

/** 2.0.0 [RVI-49] How far ahead a repeating memo's next day is looked for in the list. */
private const val NEXT_LOOK_DAYS = 400

class MemoListViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.memoRepository
    // PC 확인 11825213 사용성 제안: the 메모 tab opens on the 칸 last looked at (it always opened on 달력 메모, empty or not).
    private val tabPrefs = container.appContext.getSharedPreferences("memo_list", android.content.Context.MODE_PRIVATE)
    private val savedTab = tabPrefs.getString("tab", null)?.let { n -> MemoTab.entries.firstOrNull { it.name == n } }
    private val q = MutableStateFlow(MemoQuery(tab = savedTab ?: MemoTab.CALENDAR))
    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    private var visibleIds: Set<Long> = emptySet()
    /** [MEMO-03][MEMO-04] 1.27 AK: each memo's search text, made once per saved version. */
    private val searchCache = com.bamnat.core.memo.MemoSearch.Cache()

    private data class Where(
        val notebook: com.bamnat.core.memo.MemoNotebook,
        val calendars: List<com.bamnat.app.data.PersonalCalendar>,
        val active: String,
        val sharing: com.bamnat.core.memo.MemoSharing,
    )

    /** [NOTE-02][MEMO-01] The notebook, the calendar it is read for and who shares memos; the calendar's own schedule names the days. */
    private val where = combine(container.memoNotebook, container.calendars.calendars, container.calendars.active, repo.sharing) { n, list, a, sh ->
        Where(n, list, a, sh)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val state: StateFlow<MemoListState> = combine(
        repo.memos,
        // [MEMO-01] Every calendar's schedule: a date's shift is read from the calendar its memos belong to.
        where.flatMapLatest { w ->
            val ids = (listOf(w.active) + w.calendars.map { it.id }).distinct()
            // [SRCH-13] 1.29 AS: the reading the calendar already holds (live), not a whole-history read of its own each time.
            combine(ids.map { id -> container.calendars.repository(id).live.map { id to it } }) { w to it.toMap() }
        },
        q,
        selection,
        repo.trash.map { it.size },
    ) { all, (w, snapshots), query, selected, trashCount ->
        val notebook = if (w.calendars.size > 1) w.notebook else com.bamnat.core.memo.MemoNotebook.ALL
        build(all.filter { notebook.shows(it, w.active, w.sharing) }, w.active, snapshots, query, selected, trashCount, Today.date())
            .copy(notebook = notebook, calendars = w.calendars, activeCalendar = w.active)
    }.flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemoListState())

    private fun build(
        all: List<Memo>,
        active: String,
        snapshots: Map<String, ScheduleSnapshot>,
        query: MemoQuery,
        selected: Set<Long>,
        trashCount: Int,
        today: LocalDate,
    ): MemoListState {
        val (calendar, general) = all.partition { it.isCalendarMemo }
        val inTab = if (query.tab == MemoTab.CALENDAR) calendar else general
        val tags = inTab.flatMap { it.tags }.groupingBy { it }.eachCount()
            .entries.sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .map { it.key }
        val tag = query.tag?.takeIf { it in tags }
        // [MEMO-03] 1.27 AK: the shift of a memo's day, from the calendar it belongs to, worked out once per day and calendar.
        val shiftNames = HashMap<Pair<String, LocalDate>, String?>()
        fun shiftOf(m: Memo, date: LocalDate): String? {
            val cal = shiftCalendar(listOf(m), active) ?: return null
            return shiftNames.getOrPut(cal to date) { snapshots[cal]?.resolver?.resolve(date)?.shiftType?.name }
        }
        // 2.0.0 [RVI-49] (검토 MEMO-11) A repeating memo goes by its next day from today on: 매일 '약' made on 11/1 is under today, in
        // 다가오는·이번 달 and on the week strip, not only in 11/1's past group. Worked out once per memo.
        val nextDays = HashMap<Long, LocalDate>()
        fun dayOf(m: Memo): LocalDate {
            val d = m.date!!
            if (m.repeat.isEmpty() || !d.isBefore(today)) return d
            return nextDays.getOrPut(m.id) {
                val shifts = if (!com.bamnat.core.memo.MemoRepeat.followsSchedule(m)) null
                    else snapshots[com.bamnat.app.data.MemoRepository.calendarOf(m, active)]?.resolver?.let { com.bamnat.core.memo.ShiftMemoTime.shiftOf(it) }
                generateSequence(today) { it.plusDays(1) }.take(NEXT_LOOK_DAYS).firstOrNull { com.bamnat.core.memo.MemoRepeat.occursOn(m, it, shifts) } ?: d
            }
        }
        val repeatShifts = { m: Memo ->
            if (!com.bamnat.core.memo.MemoRepeat.followsSchedule(m)) null
            else snapshots[com.bamnat.app.data.MemoRepository.calendarOf(m, active)]?.resolver
                ?.let { r -> com.bamnat.core.memo.ShiftMemoTime.shiftOf(r) }
        }
        val words = com.bamnat.core.memo.MemoSearch.words(query.query)
        searchCache.keepOnly(all.mapTo(HashSet()) { it.id })
        val matching = inTab.filter { m ->
            (tag == null || tag in m.tags) &&
                com.bamnat.core.memo.MemoSearch.matches(searchCache.of(m), words, today) { d -> shiftOf(m, d) }
        }
        val shifts = if (query.tab == MemoTab.CALENDAR) {
            calendar.mapNotNull { m -> shiftOf(m, dayOf(m)) }.groupingBy { it }.eachCount().entries
                .sortedByDescending { it.value }.map { it.key }
        } else emptyList()
        val shift = query.shift?.takeIf { it in shifts }

        var upcoming = emptyList<MemoDateGroup>()
        var past = emptyList<MemoDateGroup>()
        var generalList = emptyList<Memo>()
        var weekDays = emptyList<LocalDate>()
        var weekCounts = emptyMap<LocalDate, Int>()
        var pickedDay: LocalDate? = null
        if (query.tab == MemoTab.CALENDAR) {
            val month = YearMonth.from(today)
            val byFilter = matching.filter { m ->
                val d = dayOf(m)
                if (shift != null && shiftOf(m, d) != shift) return@filter false
                when (query.calendarFilter) {
                    CalendarFilter.ALL -> true
                    CalendarFilter.UPCOMING -> !d.isBefore(today)
                    CalendarFilter.PAST -> d.isBefore(today)
                    CalendarFilter.THIS_MONTH -> YearMonth.from(d) == month
                    CalendarFilter.CHECKLIST -> m.isChecklist
                    CalendarFilter.PHOTO -> m.photos.isNotEmpty()
                    CalendarFilter.PINNED -> m.pinned
                }
            }
            // 1.39 [FLOW-19] 주 띠: each day's memos after the other filters, and only the picked day's when one is picked.
            weekDays = com.bamnat.core.schedule.CalendarGrid.weekStartOf(query.weekOf ?: today, query.weekStart).let { s -> (0L..6L).map { s.plusDays(it) } }
            weekCounts = byFilter.groupingBy { dayOf(it) }.eachCount().filterKeys { it in weekDays }
            pickedDay = query.day
            val filtered = if (pickedDay == null) byFilter else byFilter.filter { dayOf(it) == pickedDay }
            val groups = filtered.groupBy { dayOf(it) }.map { (date, memos) ->
                MemoDateGroup(date, shiftCalendar(memos, active)?.let { snapshots[it] }?.resolver?.resolve(date), memos.sortedWith(MemoOrdering))
            }
            upcoming = groups.filter { !it.date.isBefore(today) }.sortedBy { it.date }
            past = groups.filter { it.date.isBefore(today) }.sortedByDescending { it.date }
            visibleIds = filtered.mapTo(HashSet()) { it.id }
        } else {
            val filtered = matching.filter { m ->
                when (query.generalFilter) {
                    GeneralFilter.ALL -> true
                    GeneralFilter.PINNED -> m.pinned
                    GeneralFilter.CHECKLIST -> m.isChecklist
                    GeneralFilter.TEXT -> !m.isChecklist
                    GeneralFilter.PHOTO -> m.photos.isNotEmpty()
                }
            }
            val order = when (query.sort) {
                MemoSort.UPDATED -> MemoOrdering
                MemoSort.CREATED -> compareByDescending<Memo> { it.pinned }.thenByDescending { it.createdAtMillis }
                MemoSort.TITLE -> compareByDescending<Memo> { it.pinned }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title.ifBlank { it.preview } }
            }
            generalList = filtered.sortedWith(order)
            visibleIds = filtered.mapTo(HashSet()) { it.id }
        }
        return MemoListState(
            loaded = true,
            tab = query.tab,
            query = query.query,
            calendarFilter = query.calendarFilter,
            generalFilter = query.generalFilter,
            sort = query.sort,
            tag = tag,
            upcoming = upcoming,
            past = past,
            general = generalList,
            calendarCount = calendar.size,
            generalCount = general.size,
            tags = tags,
            // Memos deleted elsewhere drop out of the selection.
            selection = selected.filterTo(HashSet()) { id -> all.any { it.id == id } },
            trashCount = trashCount,
            shifts = shifts,
            shift = shift,
            week = weekDays,
            day = pickedDay,
            weekCounts = weekCounts,
            shiftOn = snapshots[active]?.resolver?.let { r -> { d: LocalDate -> r.resolve(d).shiftType } } ?: { _: LocalDate -> null },
            shiftsOf = repeatShifts,
            todayTasks = com.bamnat.core.memo.TaskList.of(all, today, repeatShifts),
            oldTasks = com.bamnat.core.memo.TaskList.old(all, today),
        )
    }

    /** [NOTE-02] Shows another notebook; new memos are written into it. */
    fun setNotebook(n: com.bamnat.core.memo.MemoNotebook) {
        container.memoNotebook.value = n
        clearSelection()
    }

    /** [NOTE-02] Shares the selected memos with these calendars (empty for every calendar). */
    fun shareSelected(calendars: Set<String>) {
        val memos = selectedMemos()
        viewModelScope.launch { repo.setCalendars(memos, calendars) }
        clearSelection()
    }

    fun setTab(tab: MemoTab) {
        q.update { it.copy(tab = tab, tag = null, shift = null, day = null, weekOf = null) }
        clearSelection()
        tabPrefs.edit().putString("tab", tab.name).apply()
    }

    init {
        // Never chosen yet: the first time, a 칸 with memos rather than an empty 달력 메모.
        if (savedTab == null) viewModelScope.launch {
            val first = state.first { it.loaded }
            if (q.value.tab == MemoTab.CALENDAR && first.calendarCount == 0 && first.generalCount > 0) q.update { it.copy(tab = MemoTab.GENERAL) }
        }
    }
    fun setQuery(text: String) = q.update { it.copy(query = text) }
    /** [STAB-01] What the search box shows, apart from the list that is built off the main thread. */
    val query: StateFlow<String> = q.map { it.query }.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    fun setCalendarFilter(f: CalendarFilter) = q.update { it.copy(calendarFilter = f) }
    fun setGeneralFilter(f: GeneralFilter) = q.update { it.copy(generalFilter = f) }
    fun setSort(sort: MemoSort) = q.update { it.copy(sort = sort) }
    fun toggleTag(tag: String) = q.update { it.copy(tag = if (it.tag == tag) null else tag) }
    /** [MEMO-03] '야간 날': only the memos of days with that shift. */
    fun toggleShift(name: String) = q.update { it.copy(shift = if (it.shift == name) null else name) }
    /** 1.39 [FLOW-19] 주 띠: one day's memos only; the same day again shows them all. */
    fun pickDay(d: LocalDate) = q.update { it.copy(day = if (it.day == d) null else d) }
    fun clearDay() = q.update { it.copy(day = null) }
    /** 1.39 [FLOW-19] The week before or after (by weeks), its days' dots; the picked day is let go. */
    fun moveWeek(by: Long, today: LocalDate = Today.date()) = q.update {
        val from = com.bamnat.core.schedule.CalendarGrid.weekStartOf(it.weekOf ?: today, it.weekStart).plusWeeks(by)
        it.copy(weekOf = from.takeIf { f -> f != com.bamnat.core.schedule.CalendarGrid.weekStartOf(today, it.weekStart) }, day = null)
    }
    fun setWeekStart(d: java.time.DayOfWeek) { if (q.value.weekStart != d) q.update { it.copy(weekStart = d) } }

    fun toggleSelected(id: Long) = selection.update { if (id in it) it - id else it + id }
    fun selectAllVisible() = selection.update { visibleIds }
    fun clearSelection() {
        selection.value = emptySet()
    }

    private fun selectedMemos(): List<Memo> {
        val ids = state.value.selection
        val s = state.value
        return (s.upcoming + s.past).flatMap { it.memos }.plus(s.general).filter { it.id in ids }
    }

    fun pinSelected() {
        val memos = selectedMemos()
        // Pins all unless every selected memo is already pinned, then unpins.
        val pin = memos.any { !it.pinned }
        viewModelScope.launch { repo.setPinned(memos.map { it.id }, pin) }
        clearSelection()
    }

    fun colorSelected(color: Int) {
        val ids = state.value.selection
        viewModelScope.launch { repo.setColor(ids, color) }
        clearSelection()
    }

    fun deleteSelected() {
        val memos = selectedMemos()
        viewModelScope.launch { repo.delete(memos) }
        clearSelection()
    }

    fun togglePin(memo: Memo) {
        viewModelScope.launch { repo.setPinned(memo, !memo.pinned) }
    }

    fun duplicate(memo: Memo) {
        viewModelScope.launch { repo.duplicate(memo) }
    }

    fun delete(memo: Memo) {
        viewModelScope.launch { repo.delete(memo) }
    }

    /** [TAB-02] 누락 점검 N9: a memo changed in a view (보드 칸 옮기기, 표 고르기, 되돌리기) saved as it now reads. */
    fun saveEdited(memo: Memo) {
        viewModelScope.launch { repo.save(memo) }
    }

    /**
     * 1.39 [TASK-02] 끝냄 from a card: the stored memo (not a repeating one's copy laid on a day) finished or not on [day].
     * 2.0.0 [RVI-74] (검토 U07a-01) Not an edit: '최근 수정순' keeps the card where it is, under the finger.
     */
    fun setDone(memo: Memo, day: LocalDate?, done: Boolean) {
        viewModelScope.launch { repo.get(memo.id)?.let { repo.save(it.withDone(done, day, Today.millis()), touch = false) } }
    }

    /** 2.0.0 [RVI-77] (검토 U07a-11) The calendar on screen's days ahead, so the memo tab's line reads "다음 야간 때" as it is typed. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val shiftDays: StateFlow<List<com.bamnat.core.memo.ShiftMemoTime.Day>> = container.calendars.active.flatMapLatest { cal ->
        container.calendars.repository(cal).live.map { snap ->
            val from = Today.date()
            (-1L..60L).mapNotNull { i -> com.bamnat.core.memo.ShiftMemoTime.of(snap.resolver.resolve(from.plusDays(i))) }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * 2.0.0 [RVI-77] (검토 U07a-02·03·11, I07ab-01) 한 줄로 적기 in the memo tab's '오늘' 묶음: read as the 할 일 위젯's window reads it
     * ([com.bamnat.app.memo.quickMemo], "다음 야간 때" by the calendar on screen too), into the notebook shown; [said] gets what was
     * saved, or null.
     */
    fun addQuick(text: String, said: (Memo?) -> Unit) {
        viewModelScope.launch {
            val active = container.calendars.active.value
            val shifts = shiftDays.value.ifEmpty { runCatching { com.bamnat.app.memo.shiftDaysAhead(container, active) }.getOrDefault(emptyList()) }
            val read = com.bamnat.app.memo.quickMemo(text, com.bamnat.app.memo.QuickDay.READ, ring = true, Today.dateTime(), shifts = shifts)
            if (read == null) { said(null); return@launch }
            val calendars = if (container.calendars.calendars.value.size > 1) container.memoNotebook.value.newMemoCalendars(active) else setOf(active)
            val saved = runCatching { repo.save(read.copy(calendars = calendars)) }.getOrNull()
            if (saved != null) runCatching { com.bamnat.app.widget.TaskWidget.redraw(container.appContext) }
            said(saved)
        }
    }

    /**
     * 2.0.0 [RVI-80] (검토 U07a-09) The old to-dos at once: finished, moved to today (a time still ahead today kept, one gone
     * dropped), or into the trash. Saved without touching '고친 시각'; [before] for 되돌리기 is what they were.
     */
    fun settleOld(memos: List<Memo>, how: OldWay) {
        viewModelScope.launch {
            // 2.0.0 [RVIB-05] The same as the 지난 할 일 card's buttons.
            com.bamnat.app.memo.settleLeft(repo, memos.map { it.id }, how)
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(container.appContext) }
        }
    }

    /** 2.0.0 [RVI-80] 되돌리기 of [settleOld]: the memos as they were (out of the trash too). */
    fun restoreOld(before: List<Memo>) {
        viewModelScope.launch {
            repo.restore(before.map { it.id })
            before.forEach { repo.save(it, touch = false) }
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(container.appContext) }
        }
    }

    /** 2.0.0 [RVI-77] 되돌리기 of a line just written: gone, not into the trash. */
    fun unwrite(memo: Memo) {
        viewModelScope.launch {
            repo.deleteForever(listOf(memo.id))
            runCatching { com.bamnat.app.widget.TaskWidget.redraw(container.appContext) }
        }
    }

    /** 2.0.0 [RVI-74] (검토 U07a-01·I07aa-01) A checklist line ticked on the card itself, without opening the memo. */
    fun setItemChecked(memo: Memo, index: Int, checked: Boolean) {
        val day = if (memo.repeat.isNotEmpty()) Today.date() else memo.date
        viewModelScope.launch { repo.get(memo.id)?.let { repo.save(it.withItemChecked(index, checked, day, Today.millis()), touch = false) } }
    }
}

class MemoTrashViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.memoRepository

    val trash: StateFlow<List<Memo>?> = repo.trash.map<List<Memo>, List<Memo>?> { it }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun restore(memo: Memo) {
        viewModelScope.launch { repo.restore(listOf(memo.id)) }
    }

    fun restoreAll() {
        val ids = trash.value.orEmpty().map { it.id }
        viewModelScope.launch { repo.restore(ids) }
    }

    fun deleteForever(memo: Memo) {
        viewModelScope.launch { repo.deleteForever(listOf(memo.id)) }
    }

    fun emptyTrash() {
        viewModelScope.launch { repo.emptyTrash() }
    }
}

/**
 * Editing one memo with autosave: every change is written shortly after typing stops,
 * and once more when the editor closes, so nothing is ever lost.
 */
class MemoEditorViewModel(
    private val container: AppContainer,
    private val memoId: Long,
    initialDate: LocalDate?,
    startAsChecklist: Boolean,
    /** Started from the memo tab, so a new memo goes into the notebook shown there; from the calendar, to that calendar. */
    fromNotebook: Boolean = false,
) : ViewModel() {
    private val repo = container.memoRepository
    private val saveMutex = Mutex()
    private var saveJob: Job? = null
    private var dirty = false

    private val _memo = MutableStateFlow(
        Memo(
            date = initialDate,
            isChecklist = startAsChecklist,
            checklist = if (startAsChecklist) listOf(ChecklistItem("")) else emptyList(),
            // [NOTE-01][MEMO-01] A new memo belongs to the calendar on screen only, even with one calendar (a
            // calendar added later does not see it), or to every one when written in the shared notebook.
            calendars = if (fromNotebook && container.calendars.calendars.value.size > 1) {
                container.memoNotebook.value.newMemoCalendars(container.calendars.active.value)
            } else setOf(container.calendars.active.value),
        ),
    )

    /** [NOTE-02] The user's calendars, for 어느 달력에 보일까요. */
    val calendars: StateFlow<List<com.bamnat.app.data.PersonalCalendar>> = container.calendars.calendars
    val memo: StateFlow<Memo> = _memo.asStateFlow()

    private val _loaded = MutableStateFlow(memoId == 0L)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    /** Time of the last successful save, for the "저장됨" label. */
    private val _savedAt = MutableStateFlow<Long?>(null)
    val savedAt: StateFlow<Long?> = _savedAt.asStateFlow()

    private var deleted = false
    /** 2.0.0 [RVI-44] The memo was stored with words once (opened so, or saved so). */
    private var hadWords = false
    private val _trashedOutside = MutableStateFlow(false)
    /** 2.0.0 [RVI-32] The memo went to the trash outside the editor (the list beside it on a fold): the editor closes. */
    val trashedOutside: StateFlow<Boolean> = _trashedOutside.asStateFlow()
    /** [RUL-04] This visit changed and saved the memo. */
    private var savedSomething = false

    /**
     * 1.39 [TASK-39] The memo as last read or saved here, so a time moved or a 끝내기 from its notice (or the 할 일 위젯's circle)
     * while the editor is open is kept, not written over by the next save ([com.bamnat.core.memo.MemoOutside]). PC 6차.
     */
    private var base: Memo? = null

    init {
        // [RUL-03] A button or rule opened this new memo with a 메모 틀.
        if (memoId == 0L) com.bamnat.app.logic.LogicLinks.takeTemplate()?.let { title ->
            container.memoTemplates.items.value.firstOrNull { it.title == title }?.let { t ->
                _memo.value = t.start(_memo.value.date, com.bamnat.core.time.Today.date(), _memo.value)
                viewModelScope.launch { _memo.value = com.bamnat.app.logic.LogicRunner.fillMemo(container, _memo.value) }
            }
        }
        if (memoId != 0L) {
            viewModelScope.launch {
                repo.get(memoId)?.let { stored ->
                    _memo.value = stored
                    _savedAt.value = stored.updatedAtMillis
                    base = stored
                    hadWords = !stored.isBlank
                }
                _loaded.value = true
                watchOutside(memoId)
            }
        }
    }

    /** Shows a time or 끝냄 changed outside the editor at once, as [save] keeps it. */
    private fun watchOutside(id: Long) {
        viewModelScope.launch {
            // 2.0.0 [RVI-53] (검토 MEMO-19) This one memo, read alone, not every memo at each save.
            repo.observe(id).collect { stored ->
                if (stored == null) return@collect
                saveMutex.withLock {
                    val b = base ?: return@withLock
                    // A list read before this editor's own last save is older than what it holds.
                    if (stored.updatedAtMillis < b.updatedAtMillis) return@withLock
                    // 2.0.0 [RVI-32] (검토 MEMO-12) Trashed from the list beside it: the editor closes, nothing is saved over it.
                    if (com.bamnat.core.memo.MemoOutside.trashedOutside(b, stored)) {
                        deleted = true
                        saveJob?.cancel()
                        _trashedOutside.value = true
                        return@withLock
                    }
                    val kept = com.bamnat.core.memo.MemoOutside.keep(_memo.value, b, stored)
                    if (kept != _memo.value) _memo.value = kept
                    base = com.bamnat.core.memo.MemoOutside.seen(b, stored)
                }
            }
        }
    }

    private val undoStack = ArrayDeque<Memo>()
    private val redoStack = ArrayDeque<Memo>()
    private var lastHistoryAt = 0L
    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()
    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    fun update(transform: (Memo) -> Memo) = applyChange(record = true, transform = transform)

    fun undo() {
        val previous = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_memo.value)
        // 2.0.0 [RVIB-08] The step that made a 고정 지출 takes it back too.
        costStep?.takeIf { previous === it.before }?.let { s ->
            s.saved?.let { c -> container.appScope.launch { container.calendars.money(s.calendar).delete(c) } }
            s.saved = null
        }
        applyChange(record = false) { cur -> previous.keepIdentityOf(cur) }
    }

    fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_memo.value)
        costStep?.takeIf { next === it.after && it.saved == null }?.let { s ->
            container.appScope.launch { s.saved = container.calendars.money(s.calendar).save(s.cost.copy(id = 0)) }
        }
        applyChange(record = false) { cur -> next.keepIdentityOf(cur) }
    }

    private fun Memo.keepIdentityOf(cur: Memo) =
        copy(id = cur.id, createdAtMillis = cur.createdAtMillis, updatedAtMillis = cur.updatedAtMillis)

    private fun applyChange(record: Boolean, transform: (Memo) -> Memo) {
        if (deleted) return
        val before = _memo.value
        // 2.0.0 [RVI-47] (검토 MEMO-27) A body past [MAX_BODY] (a pasted document) is cut there and said: one row too big for the
        // database's window failed every place that reads all memos (the list, widgets, notices).
        val after = transform(before).let { a ->
            if (a.body.length <= MAX_BODY || a.body.length <= before.body.length) a
            else a.copy(body = a.body.take(maxOf(MAX_BODY, before.body.length))).also {
                _sayings.tryEmit("너무 길어 ${"%,d".format(MAX_BODY)}자까지만 넣었어요")
            }
        }
        if (record && after == before) return
        if (record) {
            // Typing bursts become one undo step.
            val now = System.nanoTime() / 1_000_000
            if (undoStack.isEmpty() || now - lastHistoryAt > HISTORY_COALESCE_MILLIS) {
                undoStack.addLast(before)
                if (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
            }
            lastHistoryAt = now
            redoStack.clear()
        }
        _canUndo.value = undoStack.isNotEmpty()
        _canRedo.value = redoStack.isNotEmpty()
        _memo.value = after
        dirty = true
        saveJob?.cancel()
        saveJob = viewModelScope.launch {
            delay(AUTOSAVE_DELAY_MILLIS)
            // Only the wait is cancellable. A save that started must finish, or a new memo's id
            // could be lost after the insert and the next save would insert a duplicate.
            withContext(NonCancellable) { save() }
        }
    }

    fun setTitle(title: String) = update { it.copy(title = title) }
    fun setBody(body: String) = update { it.copy(body = body) }

    /**
     * [MPH-01] 1.38 A photo put in where nothing is being typed: at the end of the words (above the list of a checklist). False when
     * the memo already holds [com.bamnat.core.memo.MemoPhotos.MAX_PER_MEMO].
     */
    fun addPhotos(names: List<String>): Boolean {
        val room = com.bamnat.core.memo.MemoPhotos.MAX_PER_MEMO - com.bamnat.core.memo.MemoPhotos.count(_memo.value.body)
        if (room <= 0 || names.isEmpty()) return names.isEmpty()
        update { m -> m.copy(body = com.bamnat.core.memo.MemoPhotos.insertAll(m.body, null, names.take(room)).text) }
        return true
    }

    /** [RCP-11] 1.34 메모 틀: the user's own and those a 나눔 판 recipe added. */
    val templates: StateFlow<List<com.bamnat.core.memo.MemoTemplate>> = container.memoTemplates.items

    /** [RCP-11] 틀에서 시작: the template's title, list or text and repeat; the date and calendars stay as they are. */
    fun startFrom(t: com.bamnat.core.memo.MemoTemplate, answers: Map<String, String> = emptyMap()) {
        update { t.start(it.date, com.bamnat.core.time.Today.date(), it, answers) }
        // [RUL-05] 1.36 사용자 "메모장 및 모든 곳에서": {근무}·{이번주야간}처럼 틀 속 수식은 그날 값으로.
        viewModelScope.launch {
            val filled = com.bamnat.app.logic.LogicRunner.fillMemo(container, _memo.value)
            if (filled != _memo.value) applyChange(record = false) { filled }
        }
    }

    /**
     * [RCP-11] 이 메모를 틀로 저장: the memo's words and list (ticks off), never its date or calendars. [TAB-03] 1.36: under the name the user
     * gave, with the "칸: 값" lines they picked as 입력 칸. Null when saved, else why not (empty memo, 40 틀 already).
     */
    fun saveAsTemplate(name: String = "", asked: Set<String> = emptySet(), picks: Map<String, List<String>> = emptyMap()): String? {
        val m = _memo.value
        if (m.isBlank) return "빈 메모는 틀로 저장할 수 없어요"
        val t = com.bamnat.core.memo.MemoTemplate.fromMemo(m, "m" + com.bamnat.core.time.Today.millis().toString(36), name, asked, picks)
        return if (container.memoTemplates.add(t)) null else "틀이 ${com.bamnat.core.memo.MemoTemplate.MAX}개로 가득 찼어요. 새 메모의 ‘틀에서 시작’에서 안 쓰는 틀을 지워 주세요"
    }

    fun removeTemplate(id: String) = container.memoTemplates.remove(id)
    fun setColor(index: Int) = update { it.copy(colorIndex = index) }
    fun togglePinned() = update { it.copy(pinned = !it.pinned) }

    /**
     * 1.39 [TASK-02] The day 끝냄 is about: a repeating memo is finished day by day, on the day it was opened from (today from
     * elsewhere); one that does not repeat is finished once.
     */
    val doneDay: LocalDate get() = openedOn ?: Today.date()

    /** 1.39 [TASK-02] 끝냄 표시 / 끝냄 풀기. */
    fun setDone(done: Boolean) = update { it.withDone(done, doneDay, Today.millis()) }
    /**
     * [NOTE-05] A repeat follows the new date (same kind, its weekday or day), and goes with the date. [EASE-22] 근무마다 stays as it is.
     * 2.0.0 [RVI-30] (검토 MEMO-08·09) The schedule on the old day moves with it, at the same time; returns what changed besides the
     * date for the user to read (the way back is the editor's 되돌리기), or null.
     */
    fun setDate(date: LocalDate?): String? {
        val m = _memo.value
        val r = m.reminder
        val moves = date != null && r != null && m.date != null && r.at.toLocalDate() == m.date && date != m.date
        update {
            // 2.0.0 [RVI-73] One read from the date (매주, 매달 14일, 둘째 수요일) is read from the new one; one of its own days (월·수·금,
            // 격주, 며칠마다, 근무마다) stays as it is, and so does its end.
            val repeat = date?.let { d -> com.bamnat.core.memo.MemoRepeat.moved(it.repeat, d) }.orEmpty()
            val reminder = if (moves) it.reminder?.let { rr -> rr.copy(at = date!!.atTime(rr.at.toLocalTime())) } else it.reminder
            it.copy(date = date, repeat = repeat, reminder = reminder)
        }
        return when {
            moves -> "알림도 옮겨서 ${com.bamnat.app.ui.Fmt.dayTitle(date!!)} ${java.time.format.DateTimeFormatter.ofPattern("a h:mm", java.util.Locale.KOREAN).format(r!!.at)}에 알려요 · 위 ↶로 되돌려요"
            date == null && m.repeat.isNotEmpty() -> "날짜를 떼서 반복(${com.bamnat.core.memo.MemoRepeat.label(m.repeat)})도 꺼졌어요 · 위 ↶로 되돌려요"
            else -> null
        }
    }

    /**
     * [NOTE-05] How a dated memo comes back: every week on its weekday, every month on its day, every year. [EASE-23] 1.38: a
     * memo with no date gets today's (a repeat needs a day to start from) instead of being told to put one first; returns what
     * was done for the user to read, or null when nothing needs saying.
     */
    fun setRepeat(kind: com.bamnat.core.memo.MemoRepeat.Kind, from: LocalDate? = null): String? {
        val m = _memo.value
        if (kind == com.bamnat.core.memo.MemoRepeat.Kind.SHIFT) return null
        val today = Today.date()
        if (kind == com.bamnat.core.memo.MemoRepeat.Kind.NONE) { update { it.copy(repeat = "") }; return null }
        // 1.39 [TASK-44] From the 일정 창, an undated memo starts on its schedule's day.
        val date = m.date ?: from ?: today
        update { it.copy(date = date, repeat = com.bamnat.core.memo.MemoRepeat.code(kind, date)) }
        val day = if (date == today) "오늘" else com.bamnat.app.ui.Fmt.dayTitle(date)
        return if (m.date == null) "반복하려면 시작할 날이 있어야 해서 $day(${com.bamnat.app.ui.Fmt.monthDay(date)})부터로 정했어요" else null
    }

    /**
     * 2.0.0 [RVI-73] (검토 U07a-07) What the one repeat window picked, as its stored text ("" none, "K135", "E2W3~20261231" …). A memo
     * with no date starts on the first day the repeat falls on; one whose day is not one of the weekdays picked moves to the first
     * that is, its schedule on that day with it. Returns what was done besides the repeat, for the user to read, or null.
     */
    fun setRepeatCode(code: String, from: LocalDate? = null): String? {
        val repeat = com.bamnat.core.memo.MemoRepeat
        val kind = repeat.kindOf(code)
        if (kind == repeat.Kind.SHIFT) return repeat.shiftIdOf(code)?.let(::setShiftRepeat)
        if (code.isEmpty() || kind == repeat.Kind.NONE) { update { it.copy(repeat = "") }; return null }
        val m = _memo.value
        val today = Today.date()
        val base = m.date ?: from ?: today
        // Its own date is one of its days unless the weekdays picked leave it out (화요일 memo, 월·수·금).
        val fits = repeat.weekdaysOf(code).let { it.isEmpty() || base.dayOfWeek in it } &&
            (kind != repeat.Kind.NTH_WEEKDAY || repeat.firstFrom(code, base) == base)
        val date = if (m.date != null && fits) base else repeat.firstFrom(code, base) ?: base
        val r = m.reminder
        val moves = m.date != null && date != m.date && r != null && r.at.toLocalDate() == m.date
        update {
            it.copy(date = date, repeat = code, reminder = if (moves) r?.let { rr -> rr.copy(at = date.atTime(rr.at.toLocalTime())) } else it.reminder)
        }
        val fmt = com.bamnat.app.ui.Fmt
        val old = m.date
        return when {
            old == null -> "반복하려면 시작할 날이 있어야 해서 ${if (date == today) "오늘" else fmt.dayTitle(date)}(${fmt.monthDay(date)})부터로 정했어요"
            date != old -> "${fmt.monthDay(old)}${fmt.eun(fmt.monthDay(old))} 고른 날이 아니라서 ${fmt.dayTitle(date)}부터로 옮겼어요" +
                (if (moves) " · 알림도 함께" else "") + " · 위 ↶로 되돌려요"
            else -> null
        }
    }

    /**
     * [EASE-22] "야간마다": the memo comes back on every day of kind [shiftId] on its calendar, moving with it. [EASE-23] The memo
     * needs a day of that kind to start on: an undated memo, or one on a day of another kind, starts on the next such day (from its
     * date, else today), and the returned line says so.
     */
    fun setShiftRepeat(shiftId: Long): String? {
        val start = _memo.value.date
        val snap = shiftSnap.value
        val name = snap?.typesById?.get(shiftId)?.name ?: "그 근무"
        val from = start ?: Today.date()
        val resolver = snap?.resolver
        val first = if (resolver == null) null
            else generateSequence(from) { it.plusDays(1) }.take(SHIFT_SEARCH_DAYS).firstOrNull { resolver.shiftIdOn(it) == shiftId }
        val date = first ?: from
        update { it.copy(date = date, repeat = com.bamnat.core.memo.MemoRepeat.shiftCode(shiftId)) }
        val fmt = com.bamnat.app.ui.Fmt
        val i = fmt.iga(name)
        return when {
            first == null && resolver != null -> "앞으로 1년 안에 $name 날이 없어요. $name$i 달력에 생기면 그날마다 보여요"
            start == null -> "반복하려면 시작할 날이 있어야 해서 다음 $name ${fmt.dayTitle(date)}부터로 정했어요"
            date != start -> "${fmt.monthDay(start)}${fmt.eun(fmt.monthDay(start))} $name$i 아니라서 다음 $name ${fmt.dayTitle(date)}부터 보여요"
            else -> "${name}마다 보여요. 근무가 바뀌거나 밀리면 메모도 따라가요"
        }
    }
    /** [NOTE-02][MEMO-01] The calendars the memo shows on; [com.bamnat.core.memo.MemoCalendars.EVERY] for every calendar. */
    fun setCalendars(ids: Set<String>) = update { it.copy(calendars = ids) }

    /**
     * What was worked on the memo's date, shown next to the date: on the calendar the memo belongs to
     * [MEMO-01], none for a memo of several other calendars.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val day: StateFlow<ResolvedDay?> = combine(_memo.map { it.date to it }, container.calendars.active) { (date, m), active ->
        date to shiftCalendar(listOf(m), active)?.takeIf { container.calendars.get(it) != null }
    }.distinctUntilChanged().flatMapLatest { (date, cal) ->
        if (date == null || cal == null) kotlinx.coroutines.flow.flowOf(null)
        else container.calendars.repository(cal).live.map { it.resolver.resolve(date) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * [SCHED-01] A time written in the memo that has not been answered yet: offered as a schedule, or,
     * when the memo is one already, as its new time. Null when there is nothing to ask.
     */
    /** [MEMO-05] 1.27 AK: the calendar whose shifts this memo goes by (its own, else the one on screen). */
    private val shiftCal = combine(_memo.map { Triple(it.calendars, it.onEveryCalendar, com.bamnat.core.memo.MemoRepeat.followsSchedule(it)) }.distinctUntilChanged(), container.calendars.active) { (_, _, bySchedule), active ->
        // 2.0.0 [RVI-78] (검토 T07a-04) A 근무마다 memo by the one calendar its notices go by, not the one on screen.
        (if (bySchedule) com.bamnat.app.data.MemoRepository.scheduleCalendarOf(_memo.value) else shiftCalendar(listOf(_memo.value), active) ?: active)
            .takeIf { container.calendars.get(it) != null } ?: com.bamnat.app.data.PersonalCalendar.MAIN
    }.distinctUntilChanged()

    /**
     * [MEMO-05] The next 60 days of that calendar, for '다음 야간 때' and the dialog's '근무에 맞춰' chips. 1.39 [CAL2-20] From
     * yesterday, with each shift's end, so a time this morning is checked against last night's 야간 too (those only look ahead).
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val shiftDays: StateFlow<List<com.bamnat.core.memo.ShiftMemoTime.Day>> = shiftCal.flatMapLatest { cal ->
        container.calendars.repository(cal).live.map { snap ->
            val from = Today.date()
            // 1.39 [TASK-39] PC 6차: a day the schedule leaves empty (평일 일근's 토·일) is a day off too, so '근무에 맞춰' offers its morning.
            (-1L..60L).mapNotNull { i -> com.bamnat.core.memo.ShiftMemoTime.of(snap.resolver.resolve(from.plusDays(i))) }
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** [EASE-22] The schedule of the calendar this memo goes by: its kinds of day for '근무마다', and where each one falls. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val shiftSnap: StateFlow<ScheduleSnapshot?> = shiftCal.flatMapLatest { cal -> container.calendars.repository(cal).live }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** 2.0.0 [RVIB-04] '야간 끝나고 일어나면' is the night's end and this much sleep (잠 목표). */
    val sleepTarget: Int get() = container.settingsRepository.current.sleepTargetMinutes
    /** 2.0.0 [RVIB-05] 근무 중 메모 알림 모으기 ([com.bamnat.app.memo.MemoMissed.hold]). */
    val holdAtWork: Boolean get() = container.settingsRepository.current.memoHoldAtWork

    /** [EASE-22] The kinds of day offered as '야간마다', in the calendar's order; 1.36 kinds put away are left out. */
    val shiftTypes: StateFlow<List<com.bamnat.core.model.ShiftType>> = shiftSnap.map { s -> s?.shiftTypes.orEmpty().sortedBy { it.sortOrder } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * [SCHED-01] A time written in the memo that has not been answered yet: offered as a schedule, or,
     * when the memo is one already, as its new time. Null when there is nothing to ask. [MEMO-05] 1.27 AK: with no clock
     * time, words that name a shift ("다음 야간 때", "쉬는 날") are offered as that shift's time.
     */
    // 2.0.0 [RVI-53] (검토 MEMO-19) Read again only when what it reads changes (not a colour or a pin), off the main thread, and
    // only the newest while one types faster than it reads.
    val scheduleOffer: StateFlow<MemoTime.Found?> = combine(_memo.distinctUntilChanged { a, b ->
        a.title == b.title && a.body == b.body && a.checklist == b.checklist && a.reminder == b.reminder &&
            a.reminderAsked == b.reminderAsked && a.date == b.date && a.createdAtMillis == b.createdAtMillis
    }.conflate(), shiftDays) { m, days ->
        val now = Today.dateTime()
        val found = MemoTime.find(m, now, Today.zone()) ?: com.bamnat.core.memo.ShiftMemoTime.find(
            (sequenceOf(m.title, m.text) + m.checklist.asSequence().map { it.text }).joinToString("\n"), now, days) ?: return@combine null
        found.takeIf { !it.answeredBy(m.reminderAsked) && m.reminder?.at != it.at }
    }.distinctUntilChanged().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * [MEMO-06] 1.27 AK: the memos of the latest earlier day that had the same shift as this memo's day (인수인계 이어 보기),
     * on the same calendar; null for an undated memo or when there is none.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val lastSameShift: StateFlow<Pair<String, List<Memo>>?> = combine(_memo.map { it.date to it.id }.distinctUntilChanged(), shiftCal) { di, cal -> di to cal }
        .flatMapLatest { (di, cal) ->
            val (date, id) = di
            if (date == null) return@flatMapLatest kotlinx.coroutines.flow.flowOf(null)
            combine(repo.memosFor(cal), container.calendars.repository(cal).snapshot) { memos, snap ->
                val name = snap.resolver.resolve(date).shiftType?.name ?: return@combine null
                val dated = memos.filter { it.id != id && it.date?.isBefore(date) == true && !it.isBlank }
                val past = dated.map { it.date!! }.distinct().map { it to snap.resolver.resolve(it).shiftType?.name }
                val day = com.bamnat.core.memo.ShiftMemoTime.lastSame(date, name, past) ?: return@combine null
                "$name(${day.monthValue}/${day.dayOfMonth})" to dated.filter { it.date == day }.sortedWith(MemoOrdering)
            }
        }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 2.0.0 [RVI-85] (검토 T07a-11·I07ac-03) The lines left unticked on the last same shift, added to this memo to tick. */
    fun carry(lines: List<String>) = update { com.bamnat.core.memo.TaskList.withCarried(it, lines) }

    /** [SCHED-01] Makes the memo a schedule (or a plain memo again with null); [answered] is the time this answers. */
    fun setReminder(reminder: MemoReminder?, answered: MemoTime.Found? = null) = update {
        it.copy(reminder = reminder, reminderAsked = answered?.let { a -> com.bamnat.core.memo.MemoAsked.with(it.reminderAsked, a.key) } ?: it.reminderAsked)
    }

    /** [SCHED-01] "메모로 둘게요": the same time is not offered again. */
    fun keepAsMemo(found: MemoTime.Found) = update { it.copy(reminderAsked = com.bamnat.core.memo.MemoAsked.with(it.reminderAsked, found.key)) }

    /**
     * 2.0.0 [RVIB-08] (검토 I07ac-04) "매달 25일 월세 50만 원": offered once, under a time's offer when there is one, as a memo that comes
     * back on that day and a 고정 지출 (or 수입) of that amount in the 가계부. Asked by its words, like a time.
     */
    val moneyOffer: StateFlow<com.bamnat.core.memo.MoneyRepeatLine.Found?> = _memo.distinctUntilChanged { a, b ->
        a.title == b.title && a.body == b.body && a.checklist == b.checklist && a.reminderAsked == b.reminderAsked
    }.conflate().map { m ->
        com.bamnat.core.memo.MoneyRepeatLine.find((sequenceOf(m.title, m.text) + m.checklist.asSequence().map { it.text }).joinToString("\n"))
            ?.takeIf { !com.bamnat.core.memo.MemoAsked.has(m.reminderAsked, it.key) }
    }.distinctUntilChanged().flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** 2.0.0 [RVIB-08] The 고정 지출 [acceptMoney] made, so ↶ takes it back with the repeat (and ↷ puts it back). */
    private class CostStep(val before: Memo, val after: Memo, val cost: com.bamnat.core.money.FixedCost, val calendar: String) {
        @Volatile var saved: com.bamnat.core.money.FixedCost? = null
    }
    private var costStep: CostStep? = null

    /**
     * 2.0.0 [RVIB-08] '넣기': the memo comes back on that day (its date the first one from today) and the 가계부 gets the 고정 지출, in
     * one step that ↶ takes back whole. One already there by the same name, amount and day is not made twice. Returns what to say.
     */
    fun acceptMoney(found: com.bamnat.core.memo.MoneyRepeatLine.Found): String {
        val first = found.firstFrom(Today.date())
        val before = _memo.value
        lastHistoryAt = 0L
        update { it.copy(date = first, repeat = found.memoRepeat(first), reminderAsked = com.bamnat.core.memo.MemoAsked.with(it.reminderAsked, found.key)) }
        val after = _memo.value
        val calendar = container.calendars.active.value
        val cost = found.cost()
        val step = CostStep(before, after, cost, calendar).also { costStep = it }
        container.appScope.launch {
            val money = container.calendars.money(calendar)
            val there = money.book.first().fixed.any { it.name == cost.name && it.amount == cost.amount && it.day == cost.day && it.repeat == cost.repeat }
            // The 가계부's notices re-plan from the book (AppContainer), so its 알림 comes with it.
            if (!there) step.saved = money.save(cost)
        }
        val what = if (found.kind == com.bamnat.core.money.MoneyKind.INCOME) "고정 수입" else "고정 지출"
        return "${found.said}에 메모가 돌아오고, 가계부 $what${com.bamnat.app.ui.Fmt.ro(what)} 넣었어요 · 위 ↶로 되돌려요"
    }

    /** 2.0.0 [RVIB-08] '메모로 둘게요': the same words are not offered again. */
    fun keepMoneyAsMemo(found: com.bamnat.core.memo.MoneyRepeatLine.Found) =
        update { it.copy(reminderAsked = com.bamnat.core.memo.MemoAsked.with(it.reminderAsked, found.key)) }

    /** Saves a copy of this memo and returns nothing; the copy shows up in the list. */
    fun duplicate() {
        val current = _memo.value
        if (current.isBlank) return
        container.appScope.launch { repo.duplicate(current) }
    }

    /** Switches between text and checklist, carrying the content over line by line. */
    fun toggleChecklist() = update { m ->
        if (m.isChecklist) {
            // 2.0.0 [RVI-35] (검토 MEMO-16) A checked line keeps its ☑ in the words, and comes back checked.
            val text = com.bamnat.core.memo.ChecklistEdit.toText(m.checklist)
            m.copy(isChecklist = false, body = listOf(m.body, text).filter { it.isNotBlank() }.joinToString("\n"),
                checklist = emptyList())
        } else {
            // [MPH-04] 1.38 The words become items; the photos stay, above the list.
            val words = com.bamnat.core.memo.MemoPhotos.parse(m.body).mapNotNull { b ->
                (b as? com.bamnat.core.memo.MemoPhotos.Text)?.text ?: (b as? com.bamnat.core.memo.MemoPhotos.Photo)?.side
            }.joinToString("\n")
            val items = com.bamnat.core.memo.ChecklistEdit.fromText(words)
            // [MPH-05][MPH-07] The paper and the 자유 판 stay too (a board keeps its own words).
            val photos = com.bamnat.core.memo.MemoPhotos.parse(m.body).mapNotNull { b ->
                when (b) {
                    is com.bamnat.core.memo.MemoPhotos.Photo -> b.copy(side = null)
                    is com.bamnat.core.memo.MemoPhotos.Paper, is com.bamnat.core.memo.MemoPhotos.Board -> b
                    else -> null
                }
            }
            m.copy(isChecklist = true, body = com.bamnat.core.memo.MemoPhotos.join(photos), checklist = items.ifEmpty { listOf(ChecklistItem("")) })
        }
    }

    fun setItemText(index: Int, text: String) = update { m ->
        m.copy(checklist = m.checklist.mapIndexed { i, item -> if (i == index) item.copy(text = text) else item })
    }

    // 1.39 [TASK-05] A line taken off again means the memo is not finished after all (Memo.withItemChecked, as on the card).
    fun setItemChecked(index: Int, checked: Boolean) = update { m -> m.withItemChecked(index, checked, doneDay, Today.millis()) }

    /**
     * 2.0.0 [RVI-35] (검토 MEMO-16) Typed or pasted into the line at [index]: several lines pasted become a line each. Returns the
     * line to put the cursor on, or null to leave it.
     */
    fun typeItem(index: Int, value: String): Int? {
        var focus: Int? = null
        update { m ->
            val (list, at) = com.bamnat.core.memo.ChecklistEdit.typed(m.checklist, index, value)
            focus = at
            m.copy(checklist = list)
        }
        return focus
    }

    /** 2.0.0 [RVI-35] ⌫ at the start of a line: it joins the line shown [above] it. Returns the line and where the cursor goes. */
    fun joinItemAbove(index: Int, above: Int?): Pair<Int, Int>? {
        var to: Pair<Int, Int>? = null
        update { m ->
            val joined = com.bamnat.core.memo.ChecklistEdit.joinAbove(m.checklist, index, above) ?: return@update m
            to = joined.second to joined.third
            m.copy(checklist = joined.first)
        }
        return to
    }

    /** 2.0.0 [RVI-35] A line moved by its handle or the 위로·아래로 action. */
    fun moveItem(from: Int, to: Int) = update { m -> m.copy(checklist = com.bamnat.core.memo.ChecklistEdit.move(m.checklist, from, to)) }

    /** 2.0.0 [RVIB-02] (검토 I07ab-03) 별표: starred, the line goes up to the top of the open lines; unstarred, it stays. */
    fun starItem(index: Int, on: Boolean): Int? {
        var at: Int? = null
        update { m ->
            val list = com.bamnat.core.memo.ChecklistEdit.star(m.checklist, index, on)
            val moved = m.checklist.getOrNull(index)
            // Where the line went, so the cursor goes with it (the copy is the only line that is not one of the old ones).
            at = list.indexOfFirst { item -> m.checklist.none { it === item } }.takeIf { it >= 0 && moved != null }
            m.copy(checklist = list)
        }
        return at
    }

    /** 2.0.0 [RVIB-02] A line's own time (shown by it, not rung), or none. */
    fun setItemTime(index: Int, time: java.time.LocalTime?) = update { m -> m.copy(checklist = com.bamnat.core.memo.ChecklistEdit.time(m.checklist, index, time)) }

    /** 2.0.0 [RVIB-02] A line's note under it. */
    fun setItemNote(index: Int, note: String) = update { m -> m.copy(checklist = com.bamnat.core.memo.ChecklistEdit.note(m.checklist, index, note)) }

    /** Inserts an empty item after [index] and returns its position. */
    fun insertItemAfter(index: Int, text: String = ""): Int {
        var position = 0
        update { m ->
            val list = m.checklist.toMutableList()
            position = (index + 1).coerceIn(0, list.size)
            list.add(position, ChecklistItem(text))
            m.copy(checklist = list)
        }
        return position
    }

    fun removeItem(index: Int) = update { m ->
        if (index !in m.checklist.indices) m else m.copy(checklist = m.checklist.filterIndexed { i, _ -> i != index })
    }

    fun clearChecked() = update { m -> m.copy(checklist = m.checklist.filter { !it.checked }) }

    fun delete(onDone: () -> Unit) {
        deleted = true
        saveJob?.cancel()
        container.appScope.launch {
            saveMutex.withLock {
                val current = _memo.value
                if (current.id != 0L) repo.delete(current)
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }

    /** 1.38 The day this memo was opened from on the calendar (a repeating memo's day), for 이 날만 빼기; null from elsewhere. */
    val openedOn: LocalDate? = if (memoId != 0L) initialDate else null

    /**
     * 1.38 (DB 28판) 이 날만 빼기 from the editor: [day] comes off the repeat, with what was typed saved too, and the editor closes.
     * The undo is offered where the memo deletion's is (AppRoot).
     */
    fun skipDay(day: LocalDate, onDone: () -> Unit) {
        saveJob?.cancel()
        container.appScope.launch {
            saveMutex.withLock {
                val stored = repo.skipDay(_memo.value, day)
                _memo.value = stored
                dirty = false
            }
            withContext(kotlinx.coroutines.Dispatchers.Main) { onDone() }
        }
    }

    /** 1.38 Brings back every day taken off this repeating memo. */
    fun clearSkips() = update { it.copy(skips = emptySet()) }

    private suspend fun save() {
        saveMutex.withLock {
            if (deleted || !dirty) return
            // 1.39 [TASK-39] A time moved or 끝내기 pressed from the notice since this editor read the memo stays.
            val outside = base?.let { b -> if (_memo.value.id != 0L) repo.get(_memo.value.id)?.let { b to it } else null }
            if (outside != null) {
                _memo.value = com.bamnat.core.memo.MemoOutside.keep(_memo.value, outside.first, outside.second)
            }
            val current = _memo.value
            if (current.id == 0L && current.isBlank) return
            // 2.0.0 [RVI-44] (검토 MEMO-23) A memo with words, emptied: the words stay stored, so closing it now puts them in the trash.
            if (current.isBlank && hadWords) return
            val stored = repo.save(current)
            if (!stored.isBlank) hadWords = true
            val firstSave = base == null
            base = stored
            if (firstSave) watchOutside(stored.id)
            dirty = false
            // Keep the id (for new memos) without overwriting text typed during the save.
            _memo.value = _memo.value.copy(id = stored.id, createdAtMillis = stored.createdAtMillis,
                updatedAtMillis = stored.updatedAtMillis)
            if (_memo.value != current.copy(id = stored.id, createdAtMillis = stored.createdAtMillis,
                    updatedAtMillis = stored.updatedAtMillis)
            ) {
                dirty = true
            }
            _savedAt.value = stored.updatedAtMillis
            savedSomething = true
        }
    }

    /** Final save when the editor closes. Blank memos are removed instead of kept as clutter. */
    override fun onCleared() {
        saveJob?.cancel()
        if (deleted) return
        container.appScope.launch {
            withContext(NonCancellable) {
                save()
                val current = _memo.value
                // 2.0.0 [RVI-44] (검토 MEMO-23) Emptied and closed: a memo that had words goes to the trash with them (되돌리기 on the
                // list); one that never had any is clutter and goes.
                if (current.id != 0L && current.isBlank) {
                    if (hadWords) repo.get(current.id)?.let { repo.delete(it, offerUndo = true) } else repo.delete(current, offerUndo = false)
                }
                // [RUL-04] 메모를 저장하면 규칙: once, when the memo is closed with words in it.
                else if (current.id != 0L && savedSomething) com.bamnat.app.logic.LogicRunner.onMemoSaved(container, current.date)
            }
        }
    }

    private val _sayings = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 1)
    /** 2.0.0 [RVI-47] A line for the editor to say once (what was cut). */
    val sayings: kotlinx.coroutines.flow.SharedFlow<String> = _sayings

    companion object {
        /** 2.0.0 [RVI-47] The longest body a memo keeps (10만 자: a long 인수인계 fits many times over). */
        const val MAX_BODY = 100_000
        private const val AUTOSAVE_DELAY_MILLIS = 400L
        private const val HISTORY_COALESCE_MILLIS = 1_000L
        /** [EASE-22] How far ahead the first day of a 근무마다 memo's kind is looked for. */
        private const val SHIFT_SEARCH_DAYS = 366
        private const val MAX_HISTORY = 200
    }
}
