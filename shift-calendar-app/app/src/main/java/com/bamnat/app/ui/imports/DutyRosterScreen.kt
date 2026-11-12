package com.bamnat.app.ui.imports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.TouchApp
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.IncomingRoster
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.data.rules
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.UndoMessage
import com.bamnat.app.ui.calendar.MonthGrid
import com.bamnat.app.ui.calendar.WeekdayHeader
import com.bamnat.app.ui.components.ClipboardHint
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.LeaveMark
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.components.TimeRange
import com.bamnat.app.ui.components.TimeRangeDialog
import com.bamnat.app.ui.guide.WriteGate
import com.bamnat.core.example.WriteKind
import com.bamnat.core.hours.WorkHoursCalculator
import com.bamnat.core.imports.DutyCell
import com.bamnat.core.imports.DutyCodes
import com.bamnat.core.imports.DutyRoster
import com.bamnat.core.imports.DutyType
import com.bamnat.core.imports.MonthFill
import com.bamnat.core.imports.RosterRow
import com.bamnat.core.journal.JournalKind
import com.bamnat.core.journal.RosterFrom
import com.bamnat.core.journal.rosterDetail
import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.isNight
import com.bamnat.core.profile.FactKey
import com.bamnat.core.profile.Profile
import com.bamnat.core.profile.Source
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth

/**
 * [ROST-01] 한 달 근무 넣기 (1.24 순번 341, 결정 D62): the month's own calendar with a keypad under it. Typing is the first
 * way in: a key puts its kind on the marked day and the mark moves on, a finger dragged over days fills them with the
 * last key, "이 순서 반복" carries the order to the month's end. A photo, a 엑셀·CSV file or pasted text are the other ways,
 * and what they read lands on the same calendar, where any cell is fixed in place. Nothing is written until "n일 넣기",
 * which says first what it overwrites, and can be taken back after.
 * [SHFT-06][IMPORT-03][NURS-04] The photo, file and text reading of 1.18~1.23, and the doubtful cells to look at.
 */
class DutyRosterViewModel(
    private val container: AppContainer, val calendarId: String,
    /** [READ-10] The month the calendar was on when 이번 달 전부 넣기 opened this screen. */
    month: YearMonth? = null,
) : ViewModel() {
    private val schedule get() = container.calendars.repository(calendarId)

    data class Ui(
        val month: YearMonth,
        val reading: Boolean = false,
        /** Null until something was read; typing alone needs no rows. */
        val rows: List<RosterRow>? = null,
        val picked: Int = 0,
        /** The user's own cells over the picked line's, by day; null takes a read cell away. */
        val edits: Map<Int, DutyCell?> = emptyMap(),
        /** [ROST-06] Codes answered here (by [DutyCodes.codeOf]): 병가 → 유급 휴가. */
        val answers: Map<String, DutyCell> = emptyMap(),
        /** The day the next key goes into. */
        val cursor: Int = 1,
        /** The key pressed last, which a drag over days fills with. */
        val lastKey: DutyCell? = null,
        val error: String? = null,
        val applied: Int? = null,
        val from: RosterFrom = RosterFrom.TYPED,
        /** [ROST-04] The month the roster named, when it did. */
        val named: YearMonth? = null,
        /**
         * [READ-37] 1.38 PC: the month on screen before a roster named another ("7병동 2026년 11월" read as July moved the
         * screen without a word): asked "사진에서 7월로 읽었어요. 맞나요?" until answered.
         */
        val movedFrom: YearMonth? = null,
        val history: List<Map<Int, DutyCell?>> = emptyList(),
        /**
         * [READ-05] False while a roster of several lines waits for "어느 줄(조)이 내 것인가요?": nothing goes on the calendar
         * before the user says which line is theirs (사용자 2026-10-25 "어느조에 해당하는지 물어보지도 않고").
         */
        val chosen: Boolean = true,
        /** [READ-04] The photo last read, for 사진 위에서 누르기 and 무엇을 읽었는지 보기; in memory only. */
        val scan: com.bamnat.app.imports.RosterScanner.Scan? = null,
        /**
         * [READ-17] 1.27 "사진과 내용이 같은가요?": null while asked, false while the user fixes the cells that differ,
         * true once they said it is the same.
         */
        val same: Boolean? = null,
        /** [READ-14] The photo read, but poorly: what to do for a better one. */
        val advice: String? = null,
        /** [READ-13] Reading n of m ("2/5번째로 읽는 중"); m is 0 until known. */
        val step: Pair<Int, Int>? = null,
        /** [READ-30] 1.38 PC: a 4주 듀티's days in the month before or after go in too, unless the user says no. */
        val withOutside: Boolean = true,
        /** 2.0.0 [RVL-20] (검토 SCAN-22) What was typed or read before the app closed, brought back: said once, with 처음부터. */
        val resumed: Boolean = false,
    ) {
        val row: RosterRow? get() = if (chosen) rows?.getOrNull(picked) else null
        private val days: Int get() = month.lengthOfMonth()

        val cells: Map<Int, DutyCell>
            get() {
                val base = row?.cells.orEmpty().mapValues { (_, c) -> resolve(c) }.toMutableMap()
                edits.forEach { (d, c) -> if (c == null) base.remove(d) else base[d] = c }
                return base.filterKeys { it <= days }
            }
        /** Cells that will be written: everything but those still unknown. */
        val writable: Map<Int, DutyCell> get() = cells.filterValues { it !is DutyCell.Unknown }
        /** [READ-30] The roster's days outside [month] (10/26~31 of a "11월" 4주 듀티), as answered. */
        val outsideAll: Map<LocalDate, DutyCell> get() = row?.outside.orEmpty().filterKeys { YearMonth.from(it) != month }.mapValues { (_, c) -> resolve(c) }
        /** Those that will be written: when the user keeps them, and known. */
        val outside: Map<LocalDate, DutyCell> get() = if (withOutside) outsideAll.filterValues { it !is DutyCell.Unknown } else emptyMap()
        /** [NAV-04] Cells typed or read and not yet put on the calendar: leaving asks first. */
        val unsaved: Boolean get() = applied == null && writable.isNotEmpty()
        /** [NURS-04] Days read with doubt the user has not touched yet, and cells still unknown. */
        val doubtful: Set<Int>
            get() = (row?.doubtful(days).orEmpty() - edits.keys) + cells.filterValues { it is DutyCell.Unknown }.keys
        /**
         * [EASE-15] 1.38: cells the user changed from what the photo read, for "사진에서 읽은 28칸 중 2칸 고쳤어요" (a photo
         * that needs few fixes is the point of reading it).
         */
        val fixed: Int
            get() {
                val read = row?.cells ?: return 0
                return edits.count { (d, c) -> d in read && c != null && resolve(read.getValue(d)) != c }
            }
        /**
         * [READ-37] 1.38 PC: a blurred photo read to the 16th and left the rest empty with no mark. Days a photo or scan
         * left empty are marked on the month like the doubtful ones, and the run at the end is said with what to do.
         */
        val missing: Set<Int>
            get() = if (row == null || !(from == RosterFrom.PHOTO || from == RosterFrom.FILE && scan != null)) emptySet()
            else (1..days).toSet() - cells.keys
        /** The last day read before an empty run of 5 days or more to the month's end, or null. */
        val readUpTo: Int?
            get() {
                val last = cells.keys.maxOrNull() ?: return null
                return last.takeIf { days - it >= 5 && (it + 1..days).all { d -> d in missing } }
            }
        /** [ROST-03] What the roster said besides the code, for the days it stays on. */
        val notes: Map<Int, String> get() = row?.notes.orEmpty().filterKeys { it <= days && it !in edits && it in writable }
        /** [ROST-04] Days of the roster this month has not (31일 on a 30-day month). */
        val beyond: List<Int> get() = row?.let { DutyRoster.beyond(it, month) }.orEmpty()
        /** [ROST-06] The codes still to answer, leave words first: one question at a time. */
        val asks: List<DutyCell.Unknown>
            get() = (cells.values + (if (withOutside) outsideAll.values else emptyList())).filterIsInstance<DutyCell.Unknown>()
                .distinctBy { it.raw }.sortedBy { if (it.leave) 0 else 1 }

        fun resolve(c: DutyCell): DutyCell = if (c is DutyCell.Unknown) answers[codeKey(c.raw)] ?: c else c
    }

    private val today = Today.date()
    private val start = month ?: container.shownMonth.month.value.let { shown ->
        // After the middle of a month the roster on the wall is usually next month's.
        if (shown == YearMonth.from(today) && today.dayOfMonth >= 15) shown.plusMonths(1) else shown
    }
    // 2.0.0 [RVL-20] (검토 SCAN-22·U07b-15, 조건 10) A month typed or read and not put on yet survives the app closed in the background.
    private val drafts = com.bamnat.app.data.RosterDraftStore(java.io.File(container.appContext.noBackupFilesDir, "roster_drafts"))
    private val _ui = MutableStateFlow(drafts.read(calendarId)?.let { d ->
        Ui(d.month, rows = d.rows, picked = d.picked, edits = d.edits, answers = d.answers, cursor = d.cursor, from = d.from,
            named = d.named, chosen = d.chosen, withOutside = d.withOutside, resumed = true)
    } ?: Ui(start))
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            @OptIn(kotlinx.coroutines.FlowPreview::class)
            _ui.debounce(DRAFT_QUIET_MS).collect { u -> keepDraft(u) }
        }
    }

    private fun keepDraft(u: Ui) {
        if (!u.unsaved) drafts.clear(calendarId)
        else drafts.write(calendarId, com.bamnat.app.data.RosterDraftStore.Draft(u.month, u.rows, u.picked, u.edits, u.answers, u.cursor,
            u.from, u.named, u.chosen, u.withOutside, com.bamnat.core.time.Today.instant()))
    }

    /** 2.0.0 [RVL-20] 처음부터: the month brought back is thrown away. */
    fun startOver() {
        drafts.clear(calendarId)
        _ui.value = Ui(start)
    }

    /** 2.0.0 [RVL-20] '버리고 나가기': nothing of it comes back. */
    fun discard() {
        _ui.update { it.copy(edits = emptyMap(), rows = null, resumed = false) }
        drafts.clear(calendarId)
    }

    fun heardResumed() = _ui.update { it.copy(resumed = false) }
    val types: StateFlow<List<ShiftType>> = schedule.shiftTypes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    /** The calendar as it is, under the cells about to go on it. */
    val snapshot: StateFlow<ScheduleSnapshot?> = schedule.snapshot.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val weekStart get() = container.settingsRepository.current.weekStart
    private val _messages = MutableSharedFlow<UndoMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UndoMessage> = _messages.asSharedFlow()

    private suspend fun codes(): DutyCodes {
        val list = types.value.ifEmpty { schedule.shiftTypes.first() }
        return DutyCodes(list.map { DutyType(it.id, it.name, it.label, it.isOff, it.start, it.isNight) }, container.dutyCodes.of(calendarId))
    }

    fun keepOutside(yes: Boolean) = _ui.update { it.copy(withOutside = yes) }

    /** [READ-37] The month the roster named is right (true), or the one on screen before was (false). */
    fun monthRight(yes: Boolean) = _ui.update {
        val back = it.movedFrom
        if (yes || back == null) it.copy(movedFrom = null)
        else it.copy(month = back, named = null, movedFrom = null, applied = null, cursor = 1, history = emptyList())
    }

    fun month(delta: Long) = _ui.update { it.copy(month = it.month.plusMonths(delta), applied = null, cursor = 1, history = emptyList()) }

    // ---- typing (the first way in) ----

    private fun change(u: Ui, edits: Map<Int, DutyCell?>, cursor: Int = u.cursor, key: DutyCell? = u.lastKey): Ui =
        u.copy(edits = edits, cursor = cursor, lastKey = key, applied = null, history = (u.history + listOf(u.edits)).takeLast(MAX_UNDO))

    /** [ROST-01] A key: [cell] on the marked day, and the mark to the next one still doubtful, else the next day. */
    fun put(cell: DutyCell?) = _ui.update { u ->
        val day = u.cursor
        val doubt = u.doubtful - day
        val next = doubt.filter { it > day }.minOrNull() ?: if (u.row != null && day in u.doubtful) doubt.minOrNull() else null
        change(u, u.edits + (day to cell), (next ?: (day + 1)).coerceAtMost(u.month.lengthOfMonth()), cell ?: u.lastKey)
    }

    /**
     * [EASE-13] ✕ 지우기 (PC 3db30e1e: the old '비우기' did this, and read as 건너뛰기): what was typed or read on this day is
     * taken away, so the day stays as the calendar has it, and on to the next. The same key as [com.bamnat.core.imports.PaintPad.erase].
     */
    fun erase() = put(null)

    /**
     * [EASE-13] » 건너뛰기: on to the next day (the next one to look at, while some are left), this day as it is; a doubtful
     * day skipped stays doubtful. The same key as [com.bamnat.core.imports.PaintPad.skip].
     */
    fun skip() = _ui.update { u ->
        val day = u.cursor
        val doubt = u.doubtful - day
        val next = doubt.filter { it > day }.minOrNull() ?: if (u.row != null && day in u.doubtful) doubt.minOrNull() else null
        u.copy(cursor = (next ?: (day + 1)).coerceAtMost(u.month.lengthOfMonth()))
    }

    fun tap(day: Int) = _ui.update { it.copy(cursor = day.coerceIn(1, it.month.lengthOfMonth())) }

    fun step(delta: Int) = _ui.update { it.copy(cursor = (it.cursor + delta).coerceIn(1, it.month.lengthOfMonth())) }

    /** [ROST-01] A finger over days: the last key on all of them. */
    fun paint(days: List<Int>) = _ui.update { u ->
        val key = u.lastKey ?: return@update u
        val inMonth = days.filter { it in 1..u.month.lengthOfMonth() }
        if (inMonth.isEmpty()) u else change(u, u.edits + inMonth.associateWith { key }, (inMonth.max() + 1).coerceAtMost(u.month.lengthOfMonth()))
    }

    /** [ROST-08] 이 순서 반복: the order typed up to the mark, to the month's end. */
    fun repeatOrder(): Boolean {
        val u = _ui.value
        val rest = MonthFill.repeat(u.cells, u.cursor, u.month.lengthOfMonth()) ?: return false
        _ui.value = change(u, u.edits + rest, u.month.lengthOfMonth())
        return true
    }

    /** [ROST-08] 지난달과 같게: last month as the calendar ended up, day for day. */
    fun sameAsLastMonth(snap: ScheduleSnapshot) = _ui.update { u ->
        val previous = u.month.minusMonths(1)
        val copy = (1..minOf(u.month.lengthOfMonth(), previous.lengthOfMonth())).mapNotNull { d ->
            val t = snap.resolver.resolve(previous.atDay(d)).shiftType ?: return@mapNotNull null
            d to DutyCell.Shift(t.id)
        }.toMap()
        if (copy.isEmpty()) u else change(u, u.edits + copy, u.month.lengthOfMonth())
    }

    /** [ROST-08] One step back, and the mark back on the first day that step changed, so the next key lands there (1.24 통합 QA). */
    fun undo() = _ui.update { u ->
        val prev = u.history.lastOrNull() ?: return@update u
        val changed = (u.edits.keys + prev.keys).filter { (it in u.edits) != (it in prev) || u.edits[it] != prev[it] }
        u.copy(edits = prev, history = u.history.dropLast(1), applied = null, cursor = changed.minOrNull() ?: u.cursor)
    }

    fun canRepeat(u: Ui): Boolean = MonthFill.repeat(u.cells, u.cursor, u.month.lengthOfMonth()) != null

    // ---- reading (photo, file, text) ----

    fun readPhoto(uri: Uri) = read { c ->
        RosterReader.photo(container.appContext, uri, c, _ui.value.month.atDay(1)) { n, m -> _ui.update { it.copy(step = n to m) } }
    }

    fun readFile(uri: Uri) = read { c -> RosterReader.file(container.appContext, uri, c, _ui.value.month.atDay(1)) }

    fun readText(text: String) = read { c -> RosterReader.text(text, c, today) }

    /** [READ-04] The last photo read again along the lines the user tapped on it. */
    fun readTapped(hint: com.bamnat.core.imports.RosterHint) {
        val scan = _ui.value.scan ?: return
        read { c -> RosterReader.tapped(scan, c, hint, _ui.value.month.atDay(1)) }
    }

    private fun read(how: suspend (DutyCodes) -> RosterResult) {
        _ui.update { it.copy(reading = true, error = null, applied = null, step = null) }
        viewModelScope.launch {
            // 1.39.1 [R391-13] The last net: a reading that fails in a way the reader did not catch ends with why, not a closed app.
            // 2.0.0 [RVL-26] (검토 SCAN-21) Read off the main thread: a big file or text never freezes the screen.
            val r = try { kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { how(codes()) } }
            catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (e: Exception) {
                RosterResult(emptyList(), null, RosterFrom.FILE, com.bamnat.app.pay.OcrModel.why(e).text)
            }
            // [READ-05] The table's own order, top to bottom, to choose from.
            val rows = r.rows.sortedBy { it.line }
            // [READ-06] The line picked on last month's roster, found again by its name or 조.
            // 2.0.0 [RVL-22] (검토 SCAN-07) Two people of that name ("김민지", "김민지 (2)"): asked, never the first taken.
            val mine = container.dutyCodes.mine(calendarId)?.takeIf { m -> !DutyRoster.twins(rows, m) }
                ?.let { m -> rows.indexOfFirst { it.label == m } }?.takeIf { it >= 0 }
            _ui.update {
                it.copy(
                    reading = false, step = null, same = if (rows.isEmpty()) it.same else null,
                    rows = rows.takeIf { rs -> rs.isNotEmpty() } ?: it.rows,
                    picked = if (rows.isEmpty()) it.picked else mine ?: 0,
                    chosen = if (rows.isEmpty()) it.chosen else rows.size == 1 || mine != null,
                    edits = if (rows.isEmpty()) it.edits else emptyMap(), error = r.error, from = r.from, advice = r.advice,
                    // [ROST-04] The month the roster names wins over the one on screen (중-6).
                    month = r.month ?: it.month, named = r.month, cursor = 1, history = emptyList(),
                    movedFrom = if (r.month != null && r.month != it.month) it.month else null,
                    scan = r.scan ?: it.scan.takeIf { r.from == RosterFrom.PHOTO },
                )
            }
        }
    }

    /** [ROST-09] A roster shared to the app from the gallery or a messenger. */
    fun receive(shared: IncomingRoster.Shared) = when (shared) {
        is IncomingRoster.Shared.Photo -> readPhoto(shared.uri)
        is IncomingRoster.Shared.Text -> readText(shared.text)
        is IncomingRoster.Shared.File -> readFile(shared.uri)
    }

    /**
     * [ROST-10] True when pasted [text] reads as a roster. The screen's [ClipboardHint] asks this only when the user taps
     * 붙여 넣기 (결정 Q-D1: the clipboard is read on the tap, never on opening), and nothing of the text is kept.
     */
    fun fitsClip(text: String): Boolean =
        _ui.value.rows == null && !_ui.value.reading && RosterReader.looksLikeRoster(text, DutyCodes(
            types.value.map { DutyType(it.id, it.name, it.label, it.isOff, it.start, it.isNight) }, container.dutyCodes.of(calendarId)))

    fun pick(i: Int) {
        _ui.update { it.copy(picked = i, chosen = true, edits = emptyMap(), cursor = 1, history = emptyList(), same = null) }
        // [READ-06] Remembered for next month's roster; a line with no name ("3번째 줄") says nothing about next month.
        _ui.value.rows?.getOrNull(i)?.label?.takeIf { it.isNotBlank() }?.let { container.dutyCodes.rememberMine(calendarId, it) }
    }

    /**
     * A cell fixed in place. [READ-19] 1.27: a photo's word the reader was not sure of, fixed to the same meaning twice
     * ([RosterLearn]), goes in the calendar's code book, so the next roster reads it so.
     */
    fun edit(day: Int, cell: DutyCell?) {
        val u = _ui.value
        _ui.update { change(it, it.edits + (day to cell)) }
        val row = u.row ?: return
        if (u.from != RosterFrom.PHOTO || cell == null) return
        val word = com.bamnat.core.imports.RosterLearn.word(row, day, cell) ?: return
        if (com.bamnat.app.data.ReadFixBook.get(container.appContext).dutyFixed(calendarId, word, cell)) {
            container.dutyCodes.learn(calendarId, codeKey(word), cell)
        }
    }

    /**
     * [READ-17] "사진과 내용이 같은가요?": yes takes the read cells as looked at (the yellow ones too: the user saw the
     * photo beside them); no lets each one be tapped and fixed.
     */
    fun sameAsPhoto(yes: Boolean) = _ui.update { u ->
        if (!yes) return@update u.copy(same = false)
        val seen = u.doubtful.mapNotNull { d -> u.cells[d]?.takeIf { it !is DutyCell.Unknown }?.let { d to it } }.toMap()
        (if (seen.isEmpty()) u else change(u, u.edits + seen)).copy(same = true)
    }

    fun askSameAgain() = _ui.update { it.copy(same = null) }

    /** [READ-15] The document scanner could not run here. */
    fun scannerError(message: String) = _ui.update { it.copy(error = message) }

    /** [ROST-06] What a code means, here and on the next roster (the calendar's code book). */
    fun answer(raw: String, cell: DutyCell) {
        val key = codeKey(raw)
        container.dutyCodes.learn(calendarId, key, cell)
        _ui.update { it.copy(answers = it.answers + (key to cell)) }
    }

    /**
     * [ROST-11] A code the calendar has no kind for (M, S, 교): made one, with the name and times the user gives, and
     * learned. The kinds of work are then partly read off a roster: the profile says so ('추정', 리뷰 N4).
     */
    fun newKind(raw: String, name: String, range: TimeRange) {
        viewModelScope.launch {
            val list = types.value
            val label = DutyCodes.norm(raw).take(2).ifEmpty { name.take(1) }
            val id = schedule.saveShiftType(
                ShiftType(
                    id = 0, name = name.trim().ifEmpty { label }, label = label, colorArgb = NEW_COLORS[list.size % NEW_COLORS.size],
                    start = range.start, end = range.end, breakMinutes = range.breakMinutes, sortOrder = (list.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                ),
            )
            answer(raw, DutyCell.Shift(id))
            markShiftsInferred()
        }
    }

    private suspend fun markShiftsInferred() {
        val fact = container.profiles.facts(calendarId)[FactKey.SHIFTS] ?: return
        if (Profile.decide(fact, Source.INFERRED, ROSTER_CONFIDENCE) == Profile.Decision.WRITE) {
            container.profiles.mark(calendarId, FactKey.SHIFTS, Source.INFERRED, ROSTER_CONFIDENCE, "듀티표")
        }
    }

    fun apply() { if (_ui.value.chosen) WriteGate.request(container, WriteKind.SHIFT) { write() } }

    private fun write() {
        val u = _ui.value
        val cells = u.writable
        val outside = u.outside
        if (cells.isEmpty()) return
        val repo = schedule // an undo stays with the calendar it was made in
        val notes = u.notes
        viewModelScope.launch {
            val all = cells.mapKeys { u.month.atDay(it.key) } + outside
            // [READ-30] 1.38 PC (x03): 연차 on the roster is a working day taken off, even where the user's own order has a rest day.
            val restLeave = restLeave(u, all, snapshot.value ?: repo.snapshot.first(), types.value.ifEmpty { repo.shiftTypes.first() })
            // 2.0.0 [RVL-18] (검토 SCAN-13) Each day as it was and as this left it, so 되돌리기 leaves alone a day changed since.
            val fill = repo.fillDays(all.keys.sorted()) { e ->
                val c = all.getValue(e.date)
                restLeave[e.date]?.let { id -> DutyRoster.leaveOnRest(e, c as DutyCell.Leave, id) } ?: DutyRoster.apply(e, c)
            }
            // [ROST-03] "D*", "N-O", "11D": what the roster said besides the code, as that day's memo; 2.0.0 [RVL-19] (검토 SCAN-12) once,
            // also when the same roster is put in again.
            val memos = notes.mapNotNull { (d, n) -> container.memoRepository.addQuickOnce(u.month.atDay(d), "듀티표 표시: $n", setOf(calendarId)) }
            // [ROST-11] 밤낮이 한 일: what came in, never the roster itself.
            container.journal.note(JournalKind.ROSTER, rosterDetail(u.month.monthValue, all.size, u.from))
            // A roster whose common codes were matched to the user's kinds says something about those kinds.
            if (u.from != RosterFrom.TYPED && u.row?.guessed.orEmpty().any { it in cells && it !in u.edits }) markShiftsInferred()
            _ui.update { it.copy(applied = all.size, history = emptyList()) }
            container.proMoments.rosterIn() // 1.32 [PROX-02]
            // 1.39 [SPREAD-06] A month filled from a 근무표 (사진·파일·직접) is the first good moment for Play's review too, as a
            // first pattern saved is (ShiftsViewModel); it is still asked only after the first month-end card, back on 내 달력.
            if (all.size >= com.bamnat.app.review.InAppReview.ROSTER_DAYS) container.settingsRepository.update { if (it.reviewAsked) it else it.copy(reviewDue = true) }
            // [EASE-15] A photo's month says how few cells needed a hand.
            val text = "${Fmt.month(u.month)}에 ${cells.size}일을 넣었어요" +
                (if (outside.isNotEmpty()) " · ${RosterReader.span(outside.keys)}도" else "") +
                if (u.from == RosterFrom.PHOTO && u.row != null) " · 사진에서 고친 칸 ${u.fixed}개" else ""
            val undo: suspend () -> Int = {
                val kept = repo.undoFill(fill).size
                // A memo the user wrote on since stays too.
                val untouched = memos.filter { m -> container.memoRepository.get(m.id)?.updatedAtMillis == m.updatedAtMillis }
                if (untouched.isNotEmpty()) container.memoRepository.delete(untouched, offerUndo = false)
                _ui.update { it.copy(applied = null) }
                kept
            }
            // [READ-11] Also from the calendar's 도구 once this screen is closed; 2.0.0 [RVL-18] and over the calendar for 10분.
            com.bamnat.app.ui.RecentFill.recordKeeping(text, calendarId, container.appScope, undo)
            val recent = com.bamnat.app.ui.RecentFill.entry.value
            _messages.emit(UndoMessage(text) {
                com.bamnat.app.ui.RecentFill.clear(recent)
                val kept = undo()
                if (kept > 0) _messages.emit(UndoMessage(com.bamnat.app.ui.RecentFill.undone(kept)))
            })
        }
    }

    /** [READ-30] Leave days of [all] that are rest days on the calendar now, with the shift each is put on. */
    private fun restLeave(u: Ui, all: Map<LocalDate, DutyCell>, snap: ScheduleSnapshot, list: List<ShiftType>): Map<LocalDate, Long> {
        val id = leaveShift(u, list) ?: return emptyMap()
        return all.filter { (d, c) -> c is DutyCell.Leave && !snap.resolver.resolve(d).isWorkDay }.mapValues { id }
    }

    fun runUndo(m: UndoMessage) { m.undo?.let { u -> container.appScope.launch { u() } } }

    /** [REST-01] The same 근무표 점검 기준 as the calendar. */
    fun rules(snap: ScheduleSnapshot, month: YearMonth): com.bamnat.core.schedule.RestRules =
        com.bamnat.core.schedule.RotationSpeed.of(com.bamnat.core.schedule.PatternTimeline.current(snap.patterns, month.atDay(1)), snap.shiftTypes)
            .adjust(container.settingsRepository.current.rules)

    companion object {
        /** 2.0.0 [RVL-20] The draft is written once typing has paused this long. */
        const val DRAFT_QUIET_MS = 400L
        private const val MAX_UNDO = 60
        /** A roster's common code matched to the user's kind is likely, not sure. */
        const val ROSTER_CONFIDENCE = 0.6
        private val NEW_COLORS = listOf(0xFF7E57C2, 0xFF26A69A, 0xFFEF6C00, 0xFF5C6BC0, 0xFF8D6E63)
        private val keyCodes = DutyCodes(emptyList())
        fun codeKey(raw: String): String = keyCodes.codeOf(raw)
        /** [READ-30] The shift a roster's leave on a rest day is put on: the line's most common work, else the first kind of work. */
        fun leaveShift(u: Ui, list: List<ShiftType>): Long? = DutyRoster.mainShift(u.writable.values + u.outside.values) ?: list.firstOrNull { !it.isOff }?.id
    }
}

/** [ROST-01] The month as it will be: the calendar's days with the cells about to go on them. */
internal fun overlay(snap: ScheduleSnapshot, month: YearMonth, cells: Map<Int, DutyCell>, leaveOn: Long? = null): ScheduleSnapshot {
    if (cells.isEmpty()) return snap
    val byDate = snap.entries.associateBy { it.date }.toMutableMap()
    cells.forEach { (d, c) ->
        val date = month.atDay(d)
        val e = byDate[date] ?: com.bamnat.core.model.DayEntry(date)
        // [READ-30] As it will be written: the roster's 연차 on a rest day is that shift taken off.
        byDate[date] = if (c is DutyCell.Leave && leaveOn != null && !snap.resolver.resolve(date).isWorkDay) DutyRoster.leaveOnRest(e, c, leaveOn)
            else DutyRoster.apply(e, c)
    }
    return snap.copy(entries = byDate.values.toList())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DutyRosterScreen(vm: DutyRosterViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val types by vm.types.collectAsStateWithLifecycle()
    val snap by vm.snapshot.collectAsStateWithLifecycle()
    val snackbar = com.bamnat.app.ui.components.AppSnack.rememberLane()
    val context = LocalContext.current
    // 2.0.0 [RVL-20] (검토 U07b-15) The paste window and the day being fixed stay open when the phone turns or the fold opens.
    var pasting by rememberSaveable { mutableStateOf(false) }
    var editing by rememberSaveable { mutableStateOf<Int?>(null) }
    var newKindFor by remember { mutableStateOf<String?>(null) }
    var others by rememberSaveable { mutableStateOf(false) }
    var tapping by remember { mutableStateOf(false) }
    var seeing by remember { mutableStateOf(false) }
    // The system photo picker: no storage permission, only the photo the user picks is shared with the app.
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::readPhoto) }
    val files = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::readFile) }
    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.message, actionLabel = m.undo?.let { m.actionLabel }, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.runUndo(m)
        }
    }
    // [ROST-09] A roster shared from the gallery or a messenger.
    val shared by IncomingRoster.shared.collectAsStateWithLifecycle()
    LaunchedEffect(shared) { if (shared != null) IncomingRoster.take()?.let(vm::receive) }
    val byId = types.associateBy { it.id }
    val conf = androidx.compose.ui.platform.LocalConfiguration.current
    val wide = conf.screenWidthDp >= 600 && conf.screenWidthDp > conf.screenHeightDp
    // [NAV-04] 1.25 Z 후속: back or the arrow with a month typed or read but not put on asks first; it closed at once.
    var leaving by remember { mutableStateOf(false) }
    val leave = { if (ui.unsaved) leaving = true else onBack() }
    androidx.activity.compose.BackHandler(enabled = ui.unsaved) { leaving = true }
    if (leaving) com.bamnat.app.ui.components.ConfirmDialog(
        title = "넣지 않고 나갈까요?",
        message = "${ui.month.monthValue}월에 넣은 ${ui.writable.size}일이 아직 달력에 들어가지 않았어요. 나가면 사라져요.",
        confirmLabel = "버리고 나가기",
        onDismiss = { leaving = false },
        onConfirm = { leaving = false; vm.discard(); onBack() },
        destructive = true,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("한 달 근무 넣기", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = leave) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        snackbarHost = { com.bamnat.app.ui.components.AppSnackHost() },
        bottomBar = {
            if (types.isNotEmpty() && !wide) Keypad(ui, types, snap, vm)
        },
    ) { padding ->
      // Lying down, the keypad stands beside the month instead of under it, so the month keeps the screen's height.
      Row(Modifier.fillMaxSize().padding(padding)) {
        Column(
            Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.month(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달") }
                Text(Fmt.month(ui.month), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 1)
                IconButton(onClick = { vm.month(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달") }
            }
            // 2.0.0 [RVL-20] (검토 SCAN-22) The month typed or read before the app closed, said once.
            if (ui.resumed) com.bamnat.app.ui.components.NudgeCard(
                "적던 근무표를 이어서 보여 드려요",
                detail = "앱이 닫혀도 넣기 전까지 남아 있어요",
                action = "처음부터",
                onAction = vm::startOver,
                onClose = vm::heardResumed,
            )
            // The other ways in, one row: a photo or a file reads the roster onto this month.
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    enabled = !ui.reading, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.PhotoCamera, null, Modifier.size(18.dp)); Text(" 사진으로", maxLines = 1)
                }
                OutlinedButton(onClick = { files.launch(SheetFiles.ROSTER_TYPES) }, enabled = !ui.reading, modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Description, null, Modifier.size(18.dp)); Text(" 엑셀·PDF", maxLines = 1)
                }
                TextButton(onClick = { others = !others }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (others) "다른 방법 접기" else "다른 방법", maxLines = 1) }
            }
            // [READ-15] 1.27: a roster photographed at an angle or in shade, straightened by Google's scanner on the phone.
            com.bamnat.app.imports.ScannerButton(onImage = vm::readPhoto, onError = vm::scannerError, enabled = !ui.reading)
            if (others) {
                OutlinedButton(onClick = { pasting = true }, enabled = !ui.reading, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.ContentPaste, null, Modifier.size(18.dp)); Text(" 글자로 붙여 넣기")
                }
                Text("구글 렌즈나 메신저에서 복사한 근무표 글을 붙여 넣어도 돼요. 사진·파일·글은 휴대폰 밖으로 나가지 않아요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // [ROST-10] "복사한 글이 있어요 · 붙여 넣기": the clip's description only, read on the tap (Q ClipboardHint, 1.24 통합).
            if (ui.rows == null && !ui.reading) ClipboardHint("근무표", fits = vm::fitsClip, onPaste = vm::readText)
            if (ui.reading) Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                // [READ-13] Several readings take a few seconds: say which one is going.
                Text(ui.step?.let { (n, m) -> if (m > 0) "휴대폰 안에서 ${n}/${m}번째 방법으로 읽는 중" else "휴대폰 안에서 읽는 중" } ?: "휴대폰 안에서 읽는 중",
                    style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite })
            }
            ui.error?.let { e -> Text(e, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) }
            ui.advice?.let { a -> Text(a, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
            // [READ-04][READ-05] A photo that read nothing, or not what the user sees: tap it, or see what was read.
            ui.scan?.let { scan ->
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (scan.image != null) OutlinedButton(onClick = { tapping = true }, enabled = !ui.reading, modifier = Modifier.heightIn(min = 48.dp)) {
                        Icon(Icons.Filled.TouchApp, null, Modifier.size(18.dp)); Text(" 사진 위에서 누르기", maxLines = 1)
                    }
                    TextButton(onClick = { seeing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("무엇을 읽었는지 보기", maxLines = 1) }
                }
            }
            val rows = ui.rows.orEmpty()
            if (rows.size > 1 && !ui.chosen) WhichLine(rows, types, onPick = vm::pick)
            else if (rows.size > 1) {
                Text("내 줄", style = MaterialTheme.typography.titleSmall)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    rows.forEachIndexed { i, r ->
                        FilterChip(selected = ui.picked == i, onClick = { vm.pick(i) },
                            label = { Text(r.label.ifBlank { "${i + 1}번째 줄" } + " · ${r.cells.size}일", maxLines = 1) })
                    }
                }
            }
            // [READ-17] 사진과 내용이 같은가요? — the photo's dates and line over what was read, each cell fixed on its own.
            // [READ-37] A roster that moved the screen to another month asks once; the other month is one tap back.
            val moved = ui.movedFrom
            val named = ui.named
            if (moved != null && named != null && !ui.reading) MonthMovedCard(named, moved, ui.from, vm::monthRight)
            val scanned = ui.scan
            val mine = ui.row
            if (scanned != null && mine != null && ui.from == RosterFrom.PHOTO && !ui.reading) SameAsPhotoCard(
                scanned, mine, ui.picked, ui.cells, ui.doubtful, types, ui.month.lengthOfMonth(), ui.same,
                onAnswer = vm::sameAsPhoto, onFix = { d -> vm.tap(d); editing = d }, onAgain = vm::askSameAgain,
            )
            // [PHOTO-09] 1.29: '사진이 달라요' on every photo's result.
            if (ui.from == RosterFrom.PHOTO && !ui.reading) com.bamnat.app.ui.components.PhotoWrongButton(com.bamnat.core.stats.PhotoScreen.ROSTER)
            ui.asks.firstOrNull()?.let { ask -> AskCard(ask, types, onAnswer = { vm.answer(ask.raw, it) }, onNewKind = { newKindFor = ask.raw }) }
            Notices(ui, snap, vm::keepOutside)
            val s = snap
            if (s != null) {
                val shown = remember(s, ui.month, ui.cells) { overlay(s, ui.month, ui.cells, DutyRosterViewModel.leaveShift(ui, s.shiftTypes)) }
                val calculator = remember(shown) { WorkHoursCalculator(shown.resolver) }
                WeekdayHeader(vm.weekStart, Modifier.padding(start = com.bamnat.app.ui.calendar.WEEK_BAR_SPACE))
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // [CAL-52] At least the calendar's 66dp (+ text growth): a shorter cell cut "+2h" under the badge (1.24 통합 직접 테스트).
                    val cellHeight = (maxWidth / 7 * 1.2f).coerceIn(66.dp, 90.dp) + com.bamnat.app.ui.calendar.cellTextGrowth()
                    MonthGrid(
                        month = ui.month,
                        snapshot = shown,
                        calculator = calculator,
                        weekStart = vm.weekStart,
                        selected = ui.month.atDay(ui.cursor.coerceAtMost(ui.month.lengthOfMonth())),
                        today = Today.date(),
                        memoDates = emptySet(),
                        showHours = false,
                        cellHeight = cellHeight,
                        onDayClick = { d -> if (java.time.YearMonth.from(d) == ui.month) { if (d.dayOfMonth == ui.cursor) editing = d.dayOfMonth else vm.tap(d.dayOfMonth) } },
                        onQuickAction = { _, _ -> },
                        onPaint = if (ui.lastKey != null) ({ ds -> vm.paint(ds.filter { YearMonth.from(it) == ui.month }.map { it.dayOfMonth }) }) else null,
                        quickMenu = false,
                        marked = (ui.doubtful + ui.missing).map { ui.month.atDay(it) }.toSet(),
                        modifier = Modifier.heightIn(min = cellHeight * 5),
                    )
                }
                Text("날짜를 누르면 그 날부터 넣고, 한 번 더 누르면 그 칸을 골라 고쳐요. 키를 하나 누른 뒤 손가락으로 쓸면 그 근무로 칠해요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
        }
        if (wide && types.isNotEmpty()) Keypad(ui, types, snap, vm, Modifier.width(360.dp).fillMaxHeight(), wide = true)
      }
    }
    if (pasting) PasteDialog(onDismiss = { pasting = false }) { pasting = false; vm.readText(it) }
    ui.scan?.let { scan ->
        if (tapping) PhotoTapDialog(scan, onDismiss = { tapping = false }) { hint -> tapping = false; vm.readTapped(hint) }
        if (seeing) ReadDiagnosisDialog(scan, ui.rows.orEmpty(), onDismiss = { seeing = false })
    }
    editing?.let { day ->
        CellDialog(ui.month.atDay(day), types, onDismiss = { editing = null }) { cell -> vm.edit(day, cell); editing = null }
    }
    newKindFor?.let { raw ->
        NewKindDialog(raw, onDismiss = { newKindFor = null }) { name, range -> vm.newKind(raw, name, range); newKindFor = null }
    }
}

/** [READ-37] "사진에서 7월로 읽었어요. 맞나요?" with the month the screen was on as the other answer. */
@Composable
private fun MonthMovedCard(named: YearMonth, before: YearMonth, from: RosterFrom, onAnswer: (Boolean) -> Unit) {
    val what = when (from) { RosterFrom.PHOTO -> "사진"; RosterFrom.FILE -> "파일"; else -> "근무표" }
    val name = if (named.year == before.year) "${named.monthValue}월" else Fmt.month(named)
    val other = if (named.year == before.year) "${before.monthValue}월" else Fmt.month(before)
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium).padding(start = 12.dp, top = 8.dp, end = 4.dp)
        .semantics { liveRegion = LiveRegionMode.Polite }) {
        Text("${what}에서 ${name}로 읽었어요. 맞나요?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = { onAnswer(false) }) { Text("${other}이에요") }
            TextButton(onClick = { onAnswer(true) }) { Text("맞아요") }
        }
    }
    Spacer(Modifier.height(6.dp))
}

/** [ROST-02][ROST-04][NURS-04] What to know before putting the days in, a line each. */
@Composable
private fun Notices(ui: DutyRosterViewModel.Ui, snap: ScheduleSnapshot?, onOutside: (Boolean) -> Unit = {}) {
    // [READ-30] 1.38 PC: a 4주 듀티 across two months says the other month's days go in too, and lets them be left out.
    val spill = ui.outsideAll.keys
    if (ui.row != null && spill.isNotEmpty()) {
        Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium).padding(start = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(if (ui.withOutside) "근무표에 있는 ${RosterReader.span(spill)}도 함께 넣어요" else "${RosterReader.span(spill)}은 넣지 않아요",
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            TextButton(onClick = { onOutside(!ui.withOutside) }) { Text(if (ui.withOutside) "빼기" else "같이 넣기") }
        }
        Spacer(Modifier.height(6.dp))
    }
    val lines = buildList {
        if (ui.named != null && ui.row != null) add("근무표에 적힌 ${ui.named.monthValue}월로 넣어요" + if (ui.outside.isNotEmpty()) " (${ui.named.monthValue}월 ${ui.writable.size}일 + ${ui.outside.size}일)" else "")
        if (ui.beyond.isNotEmpty()) add("${ui.beyond.joinToString(",")}일 칸이 있는데 ${ui.month.monthValue}월은 ${ui.month.lengthOfMonth()}일까지라 그 칸은 빼요")
        if (ui.doubtful.isNotEmpty()) add("노란 칸 ${ui.doubtful.size}개는 확실하지 않아요. 누르고 키로 맞춰 주세요")
        // [EASE-15] How much the photo saved: the cells read, and how few needed a key.
        val read = ui.row?.cells?.size ?: 0
        // [READ-32] 1.38 PC: a scanned PDF says it too, and how many days it left empty (it read from the 7th on).
        val scanned = ui.from == RosterFrom.FILE && ui.scan != null
        val empty = ui.month.lengthOfMonth() - ui.cells.size
        val upTo = ui.readUpTo
        if ((ui.from == RosterFrom.PHOTO || scanned) && read > 0) add((if (scanned) "스캔한 PDF" else "사진") + "에서 ${read}칸을 읽었어요" +
            (if (ui.fixed > 0) " · ${ui.fixed}칸 고쳤어요" else "") + (if (empty > 0 && upTo == null) " · 노란 빈칸 ${empty}일은 키로 채워 주세요" else ""))
        // [READ-37] Read only to the 16th: say where it stopped, and that a sharper photo or the keys fill the rest.
        if (upTo != null) add("${upTo}일까지만 읽었어요. ${upTo + 1}~${ui.month.lengthOfMonth()}일은 비어 있어요. " +
            (if (scanned) "더 선명한 파일을 고르거나" else "밝은 곳에서 표 전체가 화면에 차게 다시 찍거나") + " 아래 키로 채워 주세요")
        // [EASE-15] A photo that read nothing goes straight on by hand: the keys below start on the 1st.
        if (ui.from == RosterFrom.PHOTO && ui.error != null && ui.rows.isNullOrEmpty()) add("아래 키로 ${ui.month.monthValue}월 ${ui.cursor}일부터 바로 이어서 넣을 수 있어요")
        if (ui.notes.isNotEmpty()) add("표시가 붙은 ${ui.notes.size}칸은 그 날 메모로 남겨요")
        if (snap != null) {
            val over = DutyRoster.overwrites(ui.writable.mapKeys { ui.month.atDay(it.key) }, snap.entries.associateBy { it.date })
            if (over.isNotEmpty()) add("이미 있던 연차·바꾼 근무 ${over.size}일을 덮어써요")
            // Leave only counts on a day with work to take off (FEAT-10): say so before it quietly counts nothing.
            val idle = ui.writable.filterValues { it is DutyCell.Leave }.keys.sorted()
                .filter { !snap.resolver.resolve(ui.month.atDay(it)).isWorkDay }
            // [READ-30] 1.38 PC (x03): the roster's 연차 wins. It goes on as a working day taken off, so it counts and is paid.
            if (idle.isNotEmpty()) {
                val name = DutyRosterViewModel.leaveShift(ui, snap.shiftTypes)?.let { id -> snap.shiftTypes.firstOrNull { it.id == id }?.name }
                add("${idle.joinToString(",")}일은 내 근무 순서로는 쉬는 날이지만 근무표대로 연차로 넣어요" +
                    (name?.let { " ('$it' 근무를 쉰 날로, 휴가 일수에 세요)" } ?: ""))
            }
        }
    }
    if (lines.isEmpty()) return
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.medium).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall) }
    }
}

/** [ROST-06] One code to answer: a leave word asks its kind, anything else which of the user's kinds it is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskCard(ask: DutyCell.Unknown, types: List<ShiftType>, onAnswer: (DutyCell) -> Unit, onNewKind: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // 1.39 G2 (같은 구조): '휴일'는 → '휴일'은. Letters and codes (D, N, OFF) keep 는.
            val nun = if (ask.raw.trim().lastOrNull()?.let { it in '가'..'힣' } == true) com.bamnat.core.text.Josa.eunneun(ask.raw) else "는"
            // 2.0.0 [RVL-21] (검토 SCAN-06·T07b-04) AM·V·반·생: a leave on one roster, a shift on another. Asked, the leave it
            // most often is first; a kind of work is right there too.
            val guess = remember(ask.raw) { com.bamnat.core.imports.DutyCodes.guessOf(ask.raw) }
            Text(when {
                ask.leave -> "‘${ask.raw}’$nun 어떤 휴가인가요?"
                guess != null -> "‘${ask.raw}’$nun ${guess.first}인가요, 근무인가요?"
                else -> "‘${ask.raw}’$nun 어떤 근무인가요?"
            }, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            Text(if (ask.leave || guess != null) "회사마다 달라요. 한 번 고르면 다음 근무표부터는 묻지 않아요." else "한 번 고르면 다음 근무표부터는 바로 들어가요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (guess != null && !ask.leave) {
                    val cell = guess.second
                    FilterChip(selected = false, onClick = { onAnswer(cell) }, label = { Text(guess.first) },
                        leadingIcon = { if (cell is DutyCell.Leave) LeaveMark(cell.leave, size = 18.dp) })
                }
                if (ask.leave) {
                    LeaveKind.entries.forEach { k ->
                        FilterChip(selected = false, onClick = { onAnswer(DutyCell.Leave(k)) }, label = { Text(k.label) },
                            leadingIcon = { LeaveMark(Leave(k), size = 18.dp) })
                    }
                } else {
                    types.forEach { t ->
                        FilterChip(selected = false, onClick = { onAnswer(DutyCell.Shift(t.id)) }, label = { Text(t.name) },
                            leadingIcon = { ShiftBadge(t, size = 18.dp, outlined = t.isOff) })
                    }
                    // 2.0.0 [RVL-21] 사진 검토: 'V' asked as 연차 first showed a second '연차' here, the same answer twice (조건 4).
                    if (guess?.second != DutyCell.Leave()) FilterChip(selected = false, onClick = { onAnswer(DutyCell.Leave()) }, label = { Text("연차") })
                    FilterChip(selected = false, onClick = onNewKind, label = { Text("새 근무로 만들기") })
                }
            }
        }
    }
}

/**
 * [ROST-01][ROST-08] The keypad under the month: a key per kind of day (and 연차·반차), then moving the mark, 비우기,
 * 이 순서 반복, 지난달과 같게 and 되돌리기, then "n일 넣기". Rows scroll sideways, so a key never shrinks under a big font.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Keypad(
    ui: DutyRosterViewModel.Ui, types: List<ShiftType>, snap: ScheduleSnapshot?, vm: DutyRosterViewModel,
    modifier: Modifier = Modifier, wide: Boolean = false,
) {
    // Under the month the keys scroll sideways in one row each; beside it (lying down) they wrap, all in sight.
    @Composable fun Keys(content: @Composable () -> Unit) {
        if (wide) FlowRow(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) { content() }
        else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { content() }
    }
    Surface(modifier, tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().then(if (wide) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                "${ui.month.monthValue}월 ${ui.cursor}일에 넣을 차례",
                style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp).semantics { liveRegion = LiveRegionMode.Polite },
            )
            Keys {
                types.forEach { t ->
                    FilledTonalButton(onClick = { vm.put(DutyCell.Shift(t.id)) }, contentPadding = KEY_PADDING,
                        modifier = Modifier.heightIn(min = 52.dp).semantics { contentDescription = "${t.name} 넣기" }) {
                        ShiftBadge(t, size = 22.dp, outlined = t.isOff)
                        Text(" ${t.name}", maxLines = 1)
                    }
                }
                if (types.none { it.isOff }) FilledTonalButton(onClick = { vm.put(DutyCell.Off) }, contentPadding = KEY_PADDING,
                    modifier = Modifier.heightIn(min = 52.dp)) { Text("쉬는 날", maxLines = 1) }
                LEAVE_KEYS.forEach { (name, cell) ->
                    FilledTonalButton(onClick = { vm.put(cell) }, contentPadding = KEY_PADDING,
                        modifier = Modifier.heightIn(min = 52.dp).semantics { contentDescription = "$name 넣기" }) {
                        LeaveMark(cell.leave, size = 20.dp)
                        Text(" $name", maxLines = 1)
                    }
                }
            }
            Keys {
                IconButton(onClick = { vm.step(-1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "앞 날") }
                IconButton(onClick = { vm.step(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 날") }
                // A wrong key is taken back first, so 되돌리기 comes before the rest.
                TextButton(onClick = vm::undo, enabled = ui.history.isNotEmpty()) {
                    Icon(Icons.AutoMirrored.Filled.Undo, null, Modifier.size(18.dp)); Text(" 되돌리기", maxLines = 1)
                }
                // [EASE-13] The same two keys as the keypad sheet (and the team roster's, P3-02): ✕ takes the day away, » leaves it.
                TextButton(onClick = vm::erase, modifier = Modifier.semantics { contentDescription = "${ui.month.monthValue}월 ${ui.cursor}일 넣은 것 지우기" }) {
                    Text("✕ 지우기", maxLines = 1)
                }
                TextButton(onClick = vm::skip, modifier = Modifier.semantics { contentDescription = "${ui.cursor}일 건너뛰기" }) {
                    Text("» 건너뛰기", maxLines = 1)
                }
                TextButton(onClick = { vm.repeatOrder() }, enabled = vm.canRepeat(ui)) {
                    Icon(Icons.Filled.Repeat, null, Modifier.size(18.dp)); Text(" 이 순서 반복", maxLines = 1)
                }
                if (snap != null) TextButton(onClick = { vm.sameAsLastMonth(snap) }) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Text(" 지난달과 같게", maxLines = 1)
                }
            }
            HorizontalDivider()
            val n = ui.writable.size
            Button(onClick = vm::apply, enabled = n > 0 && ui.applied == null && ui.chosen,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).heightIn(min = 52.dp)) {
                Text(when {
                    ui.applied != null -> "넣었어요"
                    !ui.chosen -> "먼저 내 줄을 골라 주세요"
                    n == 0 -> "키를 눌러 하루씩 넣어요"
                    else -> "${ui.month.monthValue}월에 ${n}일 넣기"
                }, maxLines = 1)
            }
        }
    }
}

private val KEY_PADDING = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/** [ROST-07] 연차, and 반차 off the start (오전) or the end (오후) of the day. */
private val LEAVE_KEYS = listOf(
    "연차" to DutyCell.Leave(),
    "오전 반차" to DutyCell.Leave(quarters = 2, fromStart = true),
    "오후 반차" to DutyCell.Leave(quarters = 2),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CellDialog(date: LocalDate, types: List<ShiftType>, onDismiss: () -> Unit, onPick: (DutyCell?) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Fmt.monthDay(date)) },
        text = {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                types.forEach { t -> FilterChip(selected = false, onClick = { onPick(DutyCell.Shift(t.id)) }, label = { Text(t.name) },
                    leadingIcon = { ShiftBadge(t, size = 18.dp, outlined = t.isOff) }) }
                if (types.none { it.isOff }) FilterChip(selected = false, onClick = { onPick(DutyCell.Off) }, label = { Text("쉬는 날") })
                LEAVE_KEYS.forEach { (name, cell) -> FilterChip(selected = false, onClick = { onPick(cell) }, label = { Text(name) }) }
                FilterChip(selected = false, onClick = { onPick(null) }, label = { Text("그대로 두기") })
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}

/** [ROST-11] A code the calendar has no kind for, made one: its name (the code by default) and its times. */
@Composable
private fun NewKindDialog(raw: String, onDismiss: () -> Unit, onSave: (String, TimeRange) -> Unit) {
    var name by rememberSaveable { mutableStateOf(raw) }
    var times by remember { mutableStateOf(false) }
    if (times) {
        TimeRangeDialog(title = "‘$name’ 근무 시간", initial = TimeRange(LocalTime.of(9, 0), LocalTime.of(18, 0), 60),
            onDismiss = { times = false }, onConfirm = { onSave(name, it) }, confirmLabel = "만들기")
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("새 근무로 만들기") },
        text = {
            Column {
                Text("근무표의 ‘$raw’를 내 근무 종류로 만들어요. 이름을 확인하고 다음에서 시간을 골라 주세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                // A name is words by nature (규칙 13).
                OutlinedTextField(name, { name = it.take(12) }, singleLine = true, label = { Text("이름") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(onClick = { times = true }, enabled = name.isNotBlank()) { Text("다음: 시간") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onRead: (String) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        // 2.0.0 [RVL-20] (조건 10) A tap beside the window does not throw away what was pasted; 취소 still does.
        onDismissRequest = { if (text.isBlank()) onDismiss() },
        title = { Text("글자로 붙여 넣기") },
        text = {
            Column {
                Text("날짜 줄과 근무 줄을 한 줄씩 붙여 넣어 주세요.\n예) 1 2 3 4 5\n김민지 D E N O O",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                // Pasted text is the point here; this is the fallback way in (결정 D62).
                OutlinedTextField(text, { text = it }, Modifier.fillMaxWidth().heightIn(min = 140.dp), placeholder = { Text("여기에 붙여 넣기") })
            }
        },
        confirmButton = { TextButton(onClick = { onRead(text) }, enabled = text.isNotBlank()) { Text("읽기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}
