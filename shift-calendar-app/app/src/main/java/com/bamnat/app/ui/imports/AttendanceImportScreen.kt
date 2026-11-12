package com.bamnat.app.ui.imports

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.UndoMessage
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.imports.AttendanceField
import com.bamnat.core.imports.AttendanceImport
import com.bamnat.core.imports.AttendanceRow
import com.bamnat.core.imports.ColumnMap
import com.bamnat.core.imports.Sheets
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role

/**
 * [DAYW-06] 근태 엑셀·CSV 가져오기: a file exported from the company's 근태 system (.xlsx or .csv) is read on the
 * phone, its columns are guessed and can be changed, and after a look at the days the times, leave and
 * overtime go on the calendar on screen. Taken back in one tap. The file is not kept.
 */
class AttendanceImportViewModel(private val container: AppContainer, val calendarId: String) : ViewModel() {
    private val schedule get() = container.calendars.repository(calendarId)

    data class Ui(
        val reading: Boolean = false,
        val fileName: String = "",
        val rows: List<List<String>> = emptyList(),
        val map: ColumnMap? = null,
        val error: String? = null,
        val applied: Boolean = false,
        /** 2.0.0 [RVL-26] (검토 SCAN-14·T07b-06) Whose rows to put in, when the file names people. */
        val who: String? = null,
        /**
         * [DAYW-06] A date without its year takes the year nearest today (last December's export read in January). 2.0.0 [RVL-26]
         * (검토 SCAN-21) Worked out off the main thread whenever the file, its columns or [who] change, not at each drawing.
         */
        val days: List<AttendanceRow> = emptyList(),
        /** [IMPORT-05] Rows left out and cells not read, with why. */
        val problems: List<AttendanceImport.Problem> = emptyList(),
        /** 2.0.0 [RVL-26] The people the file names (two or more), to pick one. */
        val people: List<String> = emptyList(),
        /** 2.0.0 [RVL-26] Days of several rows (for [who]): over [AttendanceImport.MANY_ROWS], said at the top. */
        val shared: Int = 0,
    ) {
        val width: Int get() = rows.maxOfOrNull { it.size } ?: 0
        val todo: List<AttendanceRow> get() = days.filter { !it.isEmpty }
        /** 2.0.0 [RVL-26] Nothing goes in until it is said whose file it is. */
        val asking: Boolean get() = people.isNotEmpty() && who == null
        /** What a column is called: its header, or its letter. */
        fun columnName(c: Int): String {
            val head = map?.headerRow?.takeIf { it >= 0 }?.let { rows.getOrNull(it)?.getOrNull(c) }.orEmpty()
            val letter = if (c < 26) ('A' + c).toString() else "${'A' + c / 26 - 1}${'A' + c % 26}"
            return if (head.isNotBlank()) "$letter · $head" else "${letter}열"
        }
    }

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    /** 1.32 [WTYP-14] My own record, to set the file beside it. */
    val snapshot = schedule.snapshot
    private val _messages = MutableSharedFlow<UndoMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<UndoMessage> = _messages.asSharedFlow()

    fun open(uri: Uri) {
        _ui.update { Ui(reading = true) }
        viewModelScope.launch {
            val result = runCatching { SheetFiles.read(container.appContext, uri) }
            result.onSuccess { (name, rows) ->
                val map = AttendanceImport.guess(rows)
                _ui.update {
                    Ui(fileName = name, rows = rows, map = map ?: ColumnMap(0, emptyMap()),
                        error = when {
                            rows.isEmpty() -> "파일에 표가 없어요"
                            map == null -> "날짜 열을 찾지 못했어요. 아래에서 열을 골라 주세요."
                            else -> null
                        })
                }
                work()
            }.onFailure { e ->
                if (e is kotlinx.coroutines.CancellationException) throw e
                _ui.update { Ui(error = (e as? Sheets.SheetException)?.message ?: "이 파일은 읽지 못했어요. 엑셀(.xlsx)이나 CSV로 저장한 파일을 골라 주세요.") }
            }
        }
    }

    fun setColumn(field: AttendanceField, column: Int?) {
        _ui.update { u ->
            val m = u.map ?: ColumnMap(0, emptyMap())
            val cols = m.columns.filterValues { it != column }.toMutableMap()
            if (column == null) cols.remove(field) else cols[field] = column
            u.copy(map = m.copy(columns = cols), applied = false, who = u.who.takeIf { field != AttendanceField.NAME })
        }
        work()
    }

    fun setHeaderRow(row: Int) {
        _ui.update { u -> u.copy(map = (u.map ?: ColumnMap(0, emptyMap())).copy(headerRow = row), applied = false, who = null) }
        work()
    }

    /** 2.0.0 [RVL-26] (검토 SCAN-14·T07b-06) The person whose rows go in; the day of another is never mine. */
    fun pickWho(name: String?) {
        _ui.update { it.copy(who = name, applied = false) }
        work()
    }

    private var working: kotlinx.coroutines.Job? = null

    /** 2.0.0 [RVL-26] (검토 SCAN-21) The days, problems and people, worked out off the main thread. */
    private fun work() {
        working?.cancel()
        val u = _ui.value
        working = viewModelScope.launch {
            val today = Today.date()
            val map = u.map
            val out = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                if (map == null || AttendanceField.DATE !in map.columns) return@withContext Ui()
                val people = AttendanceImport.people(u.rows, map)
                val who = u.who?.takeIf { it in people } ?: people.singleOrNull()
                if (people.isNotEmpty() && who == null) return@withContext Ui(people = people)
                Ui(days = AttendanceImport.read(u.rows, map, today.year, near = today, who = who),
                    problems = AttendanceImport.problems(u.rows, map, today.year, near = today, who = who),
                    people = people, shared = AttendanceImport.sharedDays(u.rows, map, today.year, near = today, who = who), who = who)
            }
            _ui.update { now ->
                // A file, column or person changed meanwhile: that change's own work brings its days.
                if (now.rows !== u.rows || now.map != u.map || now.who != u.who) now
                else now.copy(days = out.days, problems = out.problems, people = out.people, shared = out.shared, who = out.who)
            }
        }
    }

    fun apply() {
        val todo = _ui.value.todo.associateBy { it.date }
        if (todo.isEmpty()) return
        val repo = schedule // an undo stays with the calendar it was made in
        viewModelScope.launch {
            val resolver = repo.snapshot.first().resolver
            // 2.0.0 [RVL-18] (검토 SCAN-13·U07b-11·I07ba-04) As 한 달 근무 넣기: 되돌리기 over the calendar too for 10분, and it leaves
            // alone a day changed since.
            val fill = repo.fillDays(todo.keys.sorted()) { e ->
                AttendanceImport.apply(resolver.resolve(e.date), e, todo.getValue(e.date))
            }
            _ui.update { it.copy(applied = true) }
            val text = "근태 ${todo.size}일을 달력에 넣었어요"
            val undo: suspend () -> Int = {
                val kept = repo.undoFill(fill).size
                _ui.update { it.copy(applied = false) }
                kept
            }
            com.bamnat.app.ui.RecentFill.recordKeeping(text, calendarId, container.appScope, undo)
            val recent = com.bamnat.app.ui.RecentFill.entry.value
            _messages.emit(UndoMessage(text) {
                com.bamnat.app.ui.RecentFill.clear(recent)
                val kept = undo()
                if (kept > 0) _messages.emit(UndoMessage(com.bamnat.app.ui.RecentFill.undone(kept)))
            })
        }
    }

    fun runUndo(m: UndoMessage) { m.undo?.let { u -> container.appScope.launch { u() } } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttendanceImportScreen(vm: AttendanceImportViewModel, onBack: () -> Unit) {
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var choosing by remember { mutableStateOf<AttendanceField?>(null) }
    var choosingHeader by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> uri?.let(vm::open) }
    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            val r = snackbar.showSnackbar(m.message, actionLabel = m.undo?.let { m.actionLabel }, duration = SnackbarDuration.Long)
            if (r == SnackbarResult.ActionPerformed) vm.runUndo(m)
        }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("근태 엑셀 가져오기") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            if (ui.rows.isNotEmpty()) Surface(tonalElevation = 3.dp) {
                val n = ui.todo.size
                Button(onClick = vm::apply, enabled = n > 0 && !ui.applied,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).heightIn(min = 52.dp)) {
                    Text(when {
                        ui.applied -> "넣었어요"
                        ui.asking -> "누구 근태인지 먼저 골라 주세요"
                        n == 0 -> "넣을 날이 없어요"
                        else -> "${n}일 달력에 넣기"
                    })
                }
            }
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                SectionCard(title = "회사 근태 기록을 달력으로") {
                    Text("회사 근태 시스템에서 내려받은 엑셀(.xlsx)이나 CSV 파일을 골라요. 날짜·출근·퇴근·연장·휴가 칸을 읽어서 " +
                        "늦은 출근, 이른 퇴근, 야근(잔업), 쉬는 날 근무(특근), 연차·반차를 그날에 적어요. 파일은 휴대폰 안에서만 읽고 따로 저장하지 않아요.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        picker.launch(SheetFiles.TYPES)
                    }, enabled = !ui.reading, modifier = Modifier.fillMaxWidth()) { Text(if (ui.rows.isEmpty()) "파일 고르기" else "다른 파일 고르기") }
                    // 2.0.0 [RVL-27] (검토 SCAN-27) 옛 엑셀(.xls) is read as it is: no line asking to save it again.
                }
            }
            if (ui.reading) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            ui.error?.let { e -> item { Text(e, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error) } }
            if (ui.rows.isNotEmpty()) item {
                SectionCard(title = "어느 열이 무엇인가요?") {
                    Text(ui.fileName.ifBlank { "고른 파일" } + " · ${ui.rows.size}줄", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MapRow("제목 줄", ui.map?.headerRow?.let { if (it < 0) "없음" else "${it + 1}번째 줄" } ?: "-") { choosingHeader = true }
                    AttendanceField.entries.forEach { f ->
                        HorizontalDivider()
                        MapRow(f.label, ui.map?.columns?.get(f)?.let(ui::columnName) ?: "안 씀") { choosing = f }
                    }
                }
            }
            // 2.0.0 [RVL-26] (검토 SCAN-14·T07b-06) A file of many people (사장님's 출퇴근 기록): whose days go in, asked before any
            // day is shown, so another's 연차·결근 never lands on my calendar.
            if (ui.rows.isNotEmpty() && ui.people.isNotEmpty()) item { WhoCard(ui.people, ui.who, vm::pickWho) }
            if (ui.rows.isNotEmpty() && !ui.asking && ui.shared > AttendanceImport.MANY_ROWS) item {
                com.bamnat.app.ui.components.NudgeCard(
                    "같은 날 줄이 ${ui.shared}일 있어요",
                    tone = com.bamnat.app.ui.theme.Tone.Caution,
                    detail = if (AttendanceField.NAME in ui.map?.columns.orEmpty()) "한 날로 묶었어요(이른 출근·늦은 퇴근)"
                        else "여러 사람 파일이면 '이름' 열을 골라 주세요",
                    action = if (AttendanceField.NAME in ui.map?.columns.orEmpty()) null else "이름 열 고르기",
                    onAction = { choosing = AttendanceField.NAME },
                )
            }
            val problems = ui.problems
            if (ui.rows.isNotEmpty() && problems.isNotEmpty()) item {
                // [IMPORT-05] Which row, which column and why, instead of only a count.
                SectionCard(title = "읽지 못한 칸 ${problems.size}곳") {
                    problems.take(30).forEach { p ->
                        Text("${p.row}번째 줄 · ${p.field.label}" + (if (p.cell.isNotEmpty()) " ‘${p.cell.take(20)}’" else "") + " — ${p.reason}",
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                    }
                    if (problems.size > 30) Text("외 ${problems.size - 30}곳", style = MaterialTheme.typography.bodySmall)
                    Text("열을 잘못 골랐다면 위에서 바꿔 보세요. 나머지 줄은 그대로 적을 수 있어요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val days = ui.days
            // 1.32 [WTYP-14] 조사 대조 W11: 회사 기록 vs 내 기록, nothing written.
            if (ui.rows.isNotEmpty() && days.isNotEmpty()) item { CompareCard(vm, days) }
            if (ui.rows.isNotEmpty() && days.isNotEmpty()) {
                item {
                    Text("미리 보기 · ${days.size}일 중 ${ui.todo.size}일을 적어요", style = MaterialTheme.typography.titleSmall)
                }
                items(days.take(62), key = { it.date.toEpochDay() }) { d -> DayLine(d) }
                if (days.size > 62) item { Text("외 ${days.size - 62}일", style = MaterialTheme.typography.bodySmall) }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
    choosing?.let { f ->
        PickDialog(title = f.label, options = listOf(null) + (0 until ui.width).toList(), label = { it?.let(ui::columnName) ?: "안 씀" },
            sample = { c -> c?.let { col -> ui.rows.drop((ui.map?.headerRow ?: -1) + 1).firstOrNull { it.getOrNull(col).orEmpty().isNotBlank() }?.getOrNull(col) } },
            onDismiss = { choosing = null }) { vm.setColumn(f, it); choosing = null }
    }
    if (choosingHeader) {
        PickDialog(title = "제목 줄", options = listOf(-1) + (0 until minOf(ui.rows.size, 10)).toList(),
            label = { if (it < 0) "제목 줄 없음" else "${it + 1}번째 줄" },
            sample = { r -> ui.rows.getOrNull(r)?.filter { it.isNotBlank() }?.joinToString(" · ") },
            onDismiss = { choosingHeader = false }) { vm.setHeaderRow(it); choosingHeader = false }
    }
}

/** 2.0.0 [RVL-26] (검토 SCAN-14·T07b-06) Whose 근태 to put in: one chip a person, the one picked filled. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun WhoCard(people: List<String>, who: String?, onPick: (String) -> Unit) {
    SectionCard(title = if (who == null) "누구 근태를 넣을까요?" else "$who 님 근태를 넣어요") {
        Text("이 파일에 ${people.size}명이 있어요. 고른 사람 줄만 달력에 넣어요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            people.forEach { p ->
                androidx.compose.material3.FilterChip(selected = p == who, onClick = { onPick(p) }, label = { Text(p, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}

/**
 * 1.32 [WTYP-14] 조사 대조 W11: the company's days beside mine, only where they differ (출근·퇴근·연장·결근·휴가), with what the
 * company counts less in all. The calendar is not changed; the lines can be copied to send.
 */
@Composable
private fun CompareCard(vm: AttendanceImportViewModel, days: List<AttendanceRow>) {
    val snap by vm.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val s = snap ?: return
    val result = remember(s, days) { com.bamnat.core.imports.AttendanceCompare.compare(days, s.resolver::resolve) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    var all by remember { mutableStateOf(false) }
    SectionCard(title = "내 기록과 비교") {
        Text(result.summary, style = MaterialTheme.typography.titleSmall,
            color = if (result.shortMinutes > 0) com.bamnat.app.ui.theme.LocalCalendarColors.current.warning else MaterialTheme.colorScheme.primary)
        if (result.leftEarlierDays > 0) Text("회사 퇴근이 내 기록보다 이른 날 ${result.leftEarlierDays}일: 조퇴로 처리되지 않았는지 확인해 보세요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val shown = if (all) result.diffs else result.diffs.take(10)
        shown.forEach { d ->
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Text("${Fmt.shortDate(d.date)}(${Fmt.dayOfWeekShort(d.date.dayOfWeek)})", style = MaterialTheme.typography.labelLarge.merge(NumberStyle))
            d.lines.forEach { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
        Row {
            if (result.diffs.size > 10 && !all) TextButton(onClick = { all = true }) { Text("${result.diffs.size - 10}일 더 보기") }
            if (result.diffs.isNotEmpty()) TextButton(onClick = {
                clipboard.setText(androidx.compose.ui.text.AnnotatedString(com.bamnat.core.imports.AttendanceCompare.text(result) {
                    "${Fmt.shortDate(it)}(${Fmt.dayOfWeekShort(it.dayOfWeek)})"
                }))
            }) { Text("다른 날 복사") }
        }
        Text("비교만 해요. 달력은 그대로이고, 회사 기록대로 고치려면 아래 ‘달력에 넣기’를 눌러요. 내 기록은 휴대폰에만 있어요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun MapRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.width(96.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun DayLine(d: AttendanceRow) {
    val parts = listOfNotNull(
        if (d.hasTimes) "${Fmt.time(d.start)}–${Fmt.time(d.end)}" else null,
        d.leave?.label,
        if (d.absent) "결근" else null,
        d.overtimeMinutes?.takeIf { it > 0 }?.let { "연장 ${HoursFormat.korean(it)}" },
    )
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(Fmt.shortDate(d.date), style = MaterialTheme.typography.bodyMedium.merge(NumberStyle), modifier = Modifier.width(56.dp))
        Text(parts.joinToString(" · ").ifEmpty { "적을 것 없음" + d.kind.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty() },
            style = MaterialTheme.typography.bodyMedium,
            color = if (d.isEmpty) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun <T> PickDialog(title: String, options: List<T>, label: (T) -> String, sample: (T) -> String?, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(options) { o ->
                    Column(Modifier.fillMaxWidth().clickable(role = Role.Button) { onPick(o) }.padding(vertical = 10.dp)) {
                        Text(label(o), style = MaterialTheme.typography.bodyLarge)
                        sample(o)?.takeIf { it.isNotBlank() }?.let {
                            Text("예) $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1,
                                overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
