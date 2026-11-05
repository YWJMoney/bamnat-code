package com.bamnat.app.data

import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import com.bamnat.app.data.db.AppDatabase
import com.bamnat.app.data.db.toEntity
import com.bamnat.app.data.db.toModel
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.memo.MemoSharing
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flatMapLatest
import java.time.LocalDate
import com.bamnat.core.time.Today

/** Memos that were just moved to the trash, offered for undo until consumed. */
/** 1.38 [day] taken off repeating memo [memo] (이 날만 빼기). */
data class SkippedDay(val memo: Memo, val day: LocalDate)

data class DeletedMemos(val memos: List<Memo>) {
    val label: String
        get() = if (memos.size == 1) {
            val m = memos.first()
            m.title.ifBlank { m.preview }.take(20)
        } else {
            ""
        }
}

class MemoRepository(
    private val db: AppDatabase,
    private val clock: () -> Long = Today::millis,
    /** [MEMO-02] Which calendars share memos (설정 › 메모 공유), gone calendars already left out; none by default. */
    val sharing: Flow<MemoSharing> = flowOf(MemoSharing.SEPARATE),
    /**
     * [EASE-22] The kind of day on each date of calendar [calendar] (its live schedule), for 근무마다 메모; null where it is not
     * known (tests, a calendar not there), and such a memo then shows on its own date only.
     */
    private val shiftsOf: (calendar: String) -> Flow<MemoRepeat.ShiftOf?> = { flowOf(null) },
) {
    private val dao = db.memoDao()

    /** [STAB-01] Decoded off the main thread: checklists and all, for every memo. */
    val memos: Flow<List<Memo>> = dao.observeAll().map { list -> list.map { it.toModel() } }.flowOn(Dispatchers.Default)

    val trash: Flow<List<Memo>> = dao.observeTrash().map { list -> list.map { it.toModel() } }.flowOn(Dispatchers.Default)

    val datesWithMemos: Flow<Set<LocalDate>> =
        dao.observeDatesWithMemos().map { days -> days.mapTo(HashSet()) { LocalDate.ofEpochDay(it) } }

    fun memosOn(date: LocalDate): Flow<List<Memo>> =
        dao.observeOn(date.toEpochDay()).map { list -> list.map { it.toModel() } }

    /**
     * [NOTE-01][MEMO-01] The memos of [date] that show on calendar [calendar] (its own, ones for every
     * calendar, and those of calendars it shares memos with); [NOTE-05] repeating ones that come back that day too.
     */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun memosOn(date: LocalDate, calendar: String): Flow<List<Memo>> =
        memosFor(calendar).flatMapLatest { list ->
            shiftLookup(list, calendar).map { shifts -> list.filter { MemoRepeat.occursOn(it, date, shifts(it)) } }
        }

    /** [NOTE-01][MEMO-01] Dates with a memo that shows on calendar [calendar]; [NOTE-05] each day a repeating one comes back on. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun datesWithMemos(calendar: String): Flow<Set<LocalDate>> = memosFor(calendar).flatMapLatest { list ->
        shiftLookup(list, calendar).map { shifts ->
            val (from, to) = repeatWindow()
            MemoRepeat.expand(list, from, to, shifts).mapNotNullTo(HashSet()) { it.date }
        }
    }.flowOn(kotlinx.coroutines.Dispatchers.Default) // [SPD-25] 1.31 I4: four years of repeats, never on the main thread

    /**
     * [EASE-22] For the 근무마다 memos among [list] seen on calendar [calendar]: the schedule of the calendar each goes by
     * ([calendarOf]), following it as it changes (a day changed by hand, 하루 밀기), so the memo moves with its shift.
     * Any other memo gets null. No such memo: no schedule is read at all.
     */
    fun shiftLookup(list: List<Memo>, calendar: String): Flow<(Memo) -> MemoRepeat.ShiftOf?> {
        val cals = list.filter { MemoRepeat.kindOf(it.repeat) == MemoRepeat.Kind.SHIFT }.mapTo(LinkedHashSet()) { calendarOf(it, calendar) }
        if (cals.isEmpty()) return flowOf { _: Memo -> null }
        return combine(cals.map { c -> shiftsOf(c).map { c to it } }) { pairs ->
            val byCal = pairs.toMap()
            val lookup: (Memo) -> MemoRepeat.ShiftOf? = { m ->
                if (MemoRepeat.kindOf(m.repeat) == MemoRepeat.Kind.SHIFT) byCal[calendarOf(m, calendar)] else null
            }
            lookup
        }
    }

    /** [MEMO-01] Every memo calendar [calendar] sees, for its link and its views. */
    fun memosFor(calendar: String): Flow<List<Memo>> = combine(memos, sharing) { list, sh -> list.filter { it.showsOn(calendar, sh) } }


    /** [NOTE-02] Puts memos on the given calendars ([com.bamnat.core.memo.MemoCalendars.EVERY] for every calendar). */
    suspend fun setCalendars(memos: Collection<Memo>, calendars: Set<String>) {
        memos.filter { it.id != 0L && it.calendars != calendars }.forEach { m ->
            dao.get(m.id)?.toModel()?.let { dao.update(it.copy(calendars = calendars).toEntity()) }
        }
    }

    /** [NOTE-01] A calendar that goes away leaves its memos on the calendars it shared them with, or on 내 달력. */
    suspend fun forgetCalendar(id: String) {
        dao.getAll().map { it.toModel() }.filter { id in it.calendars }.forEach { m ->
            val left = (m.calendars - id).ifEmpty { setOf(com.bamnat.app.data.PersonalCalendar.MAIN) }
            dao.update(m.copy(calendars = left).toEntity())
        }
    }

    private val _lastDeleted = MutableStateFlow<DeletedMemos?>(null)
    /** The memos most recently moved to the trash, offered for undo until consumed. */
    val lastDeleted: StateFlow<DeletedMemos?> = _lastDeleted.asStateFlow()

    suspend fun get(id: Long): Memo? = dao.get(id)?.toModel()

    /** Inserts or updates; returns the memo as stored (with id and timestamps). */
    suspend fun save(memo: Memo): Memo {
        val now = clock()
        return if (memo.id == 0L) {
            val toInsert = memo.copy(createdAtMillis = now, updatedAtMillis = now)
            toInsert.copy(id = dao.insert(toInsert.toEntity()))
        } else {
            val toUpdate = memo.copy(updatedAtMillis = now, createdAtMillis = memo.createdAtMillis.takeIf { it > 0 } ?: now)
            dao.update(toUpdate.toEntity())
            toUpdate
        }
    }

    /** Quick memo from the calendar: one line becomes the body. */
    suspend fun addQuick(date: LocalDate?, text: String, calendars: Set<String> = emptySet()): Memo? {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return null
        return save(Memo(date = date, body = trimmed, calendars = calendars))
    }

    /** Copies a memo (as a new, unpinned memo) and returns the copy. */
    suspend fun duplicate(memo: Memo): Memo =
        // [SCHED-01] A copy does not ring a second time for the same plan.
        save(memo.copy(id = 0, pinned = false, deletedAtMillis = null, reminder = null))

    suspend fun setPinned(memo: Memo, pinned: Boolean) = setPinned(listOf(memo.id), pinned)

    suspend fun setPinned(ids: Collection<Long>, pinned: Boolean) {
        if (ids.isNotEmpty()) dao.setPinned(ids.toList(), pinned)
    }

    suspend fun setColor(ids: Collection<Long>, color: Int) {
        if (ids.isNotEmpty()) dao.setColor(ids.toList(), color)
    }

    /** Moves a memo to the trash. It can be restored for [TRASH_DAYS] days. */
    suspend fun delete(memo: Memo, offerUndo: Boolean = true) {
        if (memo.id == 0L) return
        if (!offerUndo) {
            // Blank memos left by the editor are clutter, not something to restore.
            dao.delete(memo.id)
            return
        }
        delete(listOf(memo), offerUndo = true)
    }

    suspend fun delete(memos: Collection<Memo>, offerUndo: Boolean = true) {
        val ids = memos.map { it.id }.filter { it != 0L }
        if (ids.isEmpty()) return
        dao.moveToTrash(ids, clock())
        if (offerUndo) _lastDeleted.value = DeletedMemos(memos.toList())
    }

    private val _lastSkipped = MutableStateFlow<SkippedDay?>(null)
    /** 1.38 이 날만 빼기: the day most recently taken off a repeating memo, offered for undo until consumed. */
    val lastSkipped: StateFlow<SkippedDay?> = _lastSkipped.asStateFlow()

    /**
     * 1.38 (DB 28판) Takes [day] off repeating memo [memo]: it stays on every other day it comes back on. With [memo] given as the
     * editor holds it, what was typed is kept too. Offers undo through [lastSkipped].
     */
    suspend fun skipDay(memo: Memo, day: LocalDate): Memo {
        val stored = save(memo.copy(skips = memo.skips + day))
        _lastSkipped.value = SkippedDay(stored, day)
        return stored
    }

    /** Puts the day of [lastSkipped] back on its memo, as the memo now is. */
    suspend fun undoSkip() {
        val skipped = _lastSkipped.value ?: return
        _lastSkipped.value = null
        get(skipped.memo.id)?.let { now -> save(now.copy(skips = now.skips - skipped.day)) }
    }

    fun consumeLastSkipped() {
        _lastSkipped.value = null
    }

    suspend fun undoDelete() {
        val deleted = _lastDeleted.value ?: return
        _lastDeleted.value = null
        dao.restoreFromTrash(deleted.memos.map { it.id })
    }

    fun consumeLastDeleted() {
        _lastDeleted.value = null
    }

    suspend fun restore(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.restoreFromTrash(ids.toList())
    }

    suspend fun deleteForever(ids: Collection<Long>) {
        if (ids.isNotEmpty()) dao.deleteAll(ids.toList())
    }

    suspend fun emptyTrash() = dao.emptyTrash()

    /** Removes memos that have been in the trash longer than [TRASH_DAYS] days. */
    suspend fun purgeOldTrash() = dao.purgeTrash(clock() - TRASH_DAYS * DAY_MILLIS)

    companion object {
        /** [NOTE-05] The days repeating memos are laid out on: a year back to three years on. */
        /**
         * [EASE-22][MEMO-01] The calendar whose kinds of day a memo seen on [viewing] goes by: [viewing] itself when the memo is
         * its own or every calendar's, else the one calendar it was written on (as the memo editor reads its 근무).
         */
        fun calendarOf(memo: Memo, viewing: String): String =
            if (memo.onEveryCalendar || viewing in memo.owners) viewing else memo.owners.singleOrNull() ?: viewing

        fun repeatWindow(today: LocalDate = Today.date()): Pair<LocalDate, LocalDate> =
            today.minusYears(1).withDayOfMonth(1) to today.plusYears(3)

        /** [SPD-25] 1.31 R5: the days the calendar lays repeating memos on, two months either side of [month]. */
        fun shownWindow(month: java.time.YearMonth): Pair<LocalDate, LocalDate> =
            month.minusMonths(2).atDay(1) to month.plusMonths(2).atEndOfMonth()

        const val TRASH_DAYS = 30L
        private const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}
