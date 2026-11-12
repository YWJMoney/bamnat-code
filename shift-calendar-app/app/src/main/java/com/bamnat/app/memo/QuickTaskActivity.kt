package com.bamnat.app.memo

import androidx.compose.foundation.shape.ZeroCornerSize

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bamnat.app.appContainer
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.app.widget.TaskWidget
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoReminder
import com.bamnat.core.memo.MemoTime
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Mic

/**
 * 1.39 [TASK-38][TASK-32][TASK-33] 한 줄로 할 일 적기, without opening the app: a small window over whatever is on the screen.
 * The 할 일 위젯's ＋, the quick settings '할 일 적기' tile and a text shared from another app (카톡·문자 'Share → 밤낮 할 일로 적기')
 * all open it. The day and time in the line are read ([MemoTime.line]) and shown as chips the user can change before 적기; nothing
 * is saved until then. No permission is asked: the share comes in as text, and the tile is bound by the system.
 */
class QuickTaskActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val sharedAll = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty() else ""
        val shared = sharedAll.take(MAX_CHARS)
        setContent {
            BamnatTheme {
                // 2.0.0 [RVI-76] (검토 U07a-11) 내 달력's days ahead, so "다음 야간 때" is read here as in the editor.
                val shifts by androidx.compose.runtime.produceState(emptyList<com.bamnat.core.memo.ShiftMemoTime.Day>()) {
                    value = runCatching { shiftDaysAhead(appContainer, com.bamnat.app.data.PersonalCalendar.MAIN) }.getOrDefault(emptyList())
                }
                QuickTask(shared, cut = sharedAll.length > MAX_CHARS, shifts = shifts, onClose = { finish() }, onSave = { memo, more, done ->
                    lifecycleScope.launch {
                        val saved = runCatching { appContainer.memoRepository.save(memo) }.isSuccess
                        // 2.0.0 [RVI-43] (검토 MEMO-22) '적었어요' only once it is saved.
                        done(saved)
                        if (saved) runCatching { TaskWidget.redraw(applicationContext) }
                        // 하나 더 keeps the window open and says it in the window; a failed save is always said.
                        if (!more || !saved) {
                            Toast.makeText(applicationContext, if (saved) "할 일에 적었어요" else "적지 못했어요. 앱에서 다시 적어 주세요", Toast.LENGTH_SHORT).show()
                        }
                        if (!more) finish()
                    }
                })
            }
        }
    }

    companion object {
        /** A shared text longer than this is cut: a memo, not a document. */
        const val MAX_CHARS = 4_000

        fun intent(context: Context): Intent = Intent(context, QuickTaskActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

/**
 * 2.0.0 [RVI-76] (검토 U07a-11) Calendar [calendar]'s days from yesterday to 60 days ahead, as the editor reads them for "다음 야간 때"
 * ([com.bamnat.app.ui.memo.MemoEditorViewModel.shiftDays]).
 */
internal suspend fun shiftDaysAhead(c: com.bamnat.app.AppContainer, calendar: String): List<com.bamnat.core.memo.ShiftMemoTime.Day> {
    val snap = c.calendars.repository(calendar).live.first()
    val from = Today.date()
    return (-1L..60L).mapNotNull { i -> com.bamnat.core.memo.ShiftMemoTime.of(snap.resolver.resolve(from.plusDays(i))) }
}

/** 2.0.0 [RVIB-01] The phone has speech recognition to hand the line to. */
internal fun speechAvailable(context: Context): Boolean = runCatching {
    context.packageManager.queryIntentActivities(Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0).isNotEmpty()
}.getOrDefault(false)

/** 2.0.0 [RVIB-01] One line in Korean, free words ("내일 저녁 6시 치과"). */
internal fun speechIntent(): Intent = Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
    .putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE, "ko-KR")
    .putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "할 일을 말해 주세요")
    .putExtra(android.speech.RecognizerIntent.EXTRA_MAX_RESULTS, 1)

/** Which day the line goes on: what was read, today, tomorrow, or none (a note pinned for later). */
internal enum class QuickDay { READ, TODAY, TOMORROW, NONE }

/**
 * 2.0.0 [RVI-76] (검토 U07a-11) [MemoTime.line] and, when it read no time, day or repeat, the words of a 근무 the editor reads too
 * ("다음 야간 때 열쇠 챙기기" → 야간 출근 1시간 전, "쉬는 날 장보기" → the next day off's morning), on [shifts] (the calendar's days
 * ahead, [com.bamnat.core.memo.ShiftMemoTime.of]); those words leave the title as a day and time do.
 */
internal fun quickLine(text: String, now: LocalDateTime, half: MemoTime.Half? = null, shifts: List<com.bamnat.core.memo.ShiftMemoTime.Day> = emptyList()): MemoTime.Line {
    val line = MemoTime.line(text, now, half)
    if (shifts.isEmpty() || line.at != null || line.written || line.repeat != com.bamnat.core.memo.MemoRepeat.Kind.NONE) return line
    val m = com.bamnat.core.memo.ShiftMemoTime.match(line.title, now, shifts) ?: return line
    val title = line.title.removeRange(m.range).replace(Regex("\\s+"), " ").trim().trim(',', '.', '·', '-').trim().ifEmpty { line.title }
    return line.copy(title = title, date = m.found.at.toLocalDate(), at = m.found.at, read = m.found.phrase, written = true)
}

/**
 * The memo [text] makes with the chips as they stand, or null when there is nothing written. 2.0.0 [RVI-76] (검토 U07a-03) [base]:
 * the day of the calendar's day window it was written in, which a line that names no day goes on; [shifts] as [quickLine].
 */
internal fun quickMemo(
    text: String, day: QuickDay, ring: Boolean, now: LocalDateTime, half: MemoTime.Half? = null,
    base: LocalDate? = null, shifts: List<com.bamnat.core.memo.ShiftMemoTime.Day> = emptyList(),
): Memo? {
    if (text.isBlank()) return null
    val line = quickLine(text, now, half, shifts)
    val date: LocalDate? = quickDate(line, day, now, base)
    // The time read goes with the day picked (내일 저녁 6시, moved to 오늘, rings 오늘 저녁 6시); no day, no ring.
    // 1.39 [TASK-44] A repeating one ('매일 오전 9시') keeps its time even when today's is gone: it rings on its next day.
    val repeat = date?.let { com.bamnat.core.memo.MemoRepeat.code(line.repeat, it) }.orEmpty()
    val at = line.at?.let { a -> date?.atTime(a.toLocalTime()) }?.takeIf { ring && (it.isAfter(now) || repeat.isNotEmpty()) }
    // 2.0.0 [RVI-43] (검토 MEMO-22) A long shared line with no line break: its first words are the title, the rest the body.
    val (title, more) = quickTitle(line.title)
    return Memo(
        date = date,
        title = title,
        body = listOfNotNull(more, line.rest.ifBlank { null }).joinToString("\n"),
        repeat = repeat,
        reminder = at?.let { MemoReminder(it, ring = false, notify = true) },
        // A line with no day stays at hand as a pinned note, as on the widget's 고정 list.
        pinned = date == null,
    )
}

/** 2.0.0 [RVI-43] [title] cut to [QUICK_TITLE] characters at a space where it can be, and what is left (null when nothing is). */
internal fun quickTitle(title: String): Pair<String, String?> {
    if (title.length <= QUICK_TITLE) return title to null
    val space = title.lastIndexOf(' ', QUICK_TITLE).takeIf { it >= QUICK_TITLE / 2 } ?: QUICK_TITLE
    return title.substring(0, space).trim() to title.substring(space).trim().ifEmpty { null }
}

/** A title longer than this reads as a whole message, not a thing to do. */
internal const val QUICK_TITLE = 40

/** PC 7차: the 오전·오후 the window shows for an unmarked time, so 적기 saves the one shown even if the clock moves on meanwhile. */
private fun MemoTime.Line.half(): MemoTime.Half? = at?.takeIf { unmarked }?.let { if (it.hour >= 12) MemoTime.Half.PM else MemoTime.Half.AM }

/** The day the chips put the line on, as [quickMemo] does; [base] the calendar day it was written on. */
private fun quickDate(line: MemoTime.Line, day: QuickDay, now: LocalDateTime, base: LocalDate? = null): LocalDate? = when (day) {
    QuickDay.READ -> (if (base != null && !line.written) base else line.date) ?: base ?: now.toLocalDate()
    QuickDay.TODAY -> now.toLocalDate()
    QuickDay.TOMORROW -> now.toLocalDate().plusDays(1)
    QuickDay.NONE -> null
}

/** Whether the time read is already gone on the day picked (오늘 21:00 at 23:06): it cannot ring, so the window says so. */
internal fun quickPast(line: MemoTime.Line, day: QuickDay, now: LocalDateTime): Boolean {
    // 1.39 [TASK-44] A repeating line rings on its next day instead.
    if (line.repeat != com.bamnat.core.memo.MemoRepeat.Kind.NONE) return false
    val at = line.at ?: return false
    val date = quickDate(line, day, now) ?: return false
    return !date.atTime(at.toLocalTime()).isAfter(now)
}

/** "제목: 운동 · 11/7 (토) 21:00 알림", "… · 21:00은 지났어요, 시각 없이 적어요", "… · 날짜 없이 고정": where 적기 puts it now. */
internal fun quickSay(line: MemoTime.Line, day: QuickDay, ring: Boolean, now: LocalDateTime, dayFmt: DateTimeFormatter): String {
    val date = quickDate(line, day, now)
    val time = line.at?.toLocalTime()
    // 1.39 [TASK-44] "매일 · 11/9 (월)부터 09:24 알림".
    val again = date?.let { com.bamnat.core.memo.MemoRepeat.label(com.bamnat.core.memo.MemoRepeat.code(line.repeat, it)) }.orEmpty()
    val where = when {
        date == null -> "날짜 없이 고정"
        again.isNotEmpty() && time == null -> "$again · ${dayFmt.format(date)}부터"
        again.isNotEmpty() -> "$again · ${dayFmt.format(date)}부터 $time" + if (ring) " 알림" else " · 알림 없이"
        time == null -> dayFmt.format(date)
        quickPast(line, day, now) -> "${dayFmt.format(date)} · ${time}은 지났어요. 시각 없이 적어요(알림은 '내일'을 골라요)"
        ring -> "${dayFmt.format(date)} $time 알림"
        else -> "${dayFmt.format(date)} · 알림 없이"
    }
    return "제목: ${line.title} · $where"
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun QuickTask(
    shared: String, cut: Boolean = false, onClose: () -> Unit, onSave: (Memo, Boolean, (Boolean) -> Unit) -> Unit,
    shifts: List<com.bamnat.core.memo.ShiftMemoTime.Day> = emptyList(),
) {
    var text by rememberSaveable { mutableStateOf(shared) }
    // 1.39 [TASK-39] 적고 하나 더 (두 앱 실사용 2차: 할 일 앱의 넣기 창은 열린 채 남아 이어 적어요): what was just written, said in the window.
    var written by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var day by rememberSaveable { mutableStateOf(QuickDay.READ) }
    var ring by rememberSaveable { mutableStateOf(true) }
    val now = remember { Today.dateTime() }
    // PC 7차: a time without 오전·오후 ("8시") is a guess; 오전·오후 chips flip it, kept until the time written changes.
    val timeRead = remember(text) { MemoTime.line(text, now).read }
    var half by rememberSaveable(timeRead) { mutableStateOf<MemoTime.Half?>(null) }
    val line = remember(text, half, shifts) { quickLine(text, now, half, shifts) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // 2.0.0 [RVIB-01] (검토 I07ab-02, TASK-03) 말로 적기: the phone's own speech recognition (gloves on a site, at the wheel). What it
    // heard goes into the line to read and change before 적기; no sound is kept by the app, and no microphone permission is asked.
    val context = androidx.compose.ui.platform.LocalContext.current
    val canListen = remember { speechAvailable(context) }
    val listen = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { r ->
        r.data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.takeIf { it.isNotBlank() }?.let { heard ->
            text = (if (text.isBlank()) heard else "${text.trimEnd()} $heard").take(QuickTaskActivity.MAX_CHARS)
        }
    }
    val dayFmt = remember { DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN) }
    // The dim around the card closes it, as a sheet does. 2.0.0 [RVI-43] (검토 MEMO-22) Not while something is written: a brush past
    // the card lost the line; 닫기 is right there.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = { if (text.isBlank()) onClose() })
            .semantics { contentDescription = "닫기" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().navigationBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("할 일 적기", style = MaterialTheme.typography.titleMedium)
                if (written.isNotEmpty()) {
                    Text("적었어요: " + written.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary, maxLines = 2)
                }
                OutlinedTextField(
                    value = text, onValueChange = { text = it.take(QuickTaskActivity.MAX_CHARS) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("예: 내일 저녁 6시 치과") },
                    maxLines = 4,
                    trailingIcon = if (!canListen) null else ({
                        androidx.compose.material3.IconButton(onClick = { runCatching { listen.launch(speechIntent()) } },
                            modifier = Modifier.semantics { contentDescription = "말로 적기" }) {
                            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Outlined.Mic, null)
                        }
                    }),
                )
                // 2.0.0 [RVI-43] What was cut is said, not dropped silently.
                if (cut || text.length >= QuickTaskActivity.MAX_CHARS) {
                    Text("너무 길어 앞 ${"%,d".format(QuickTaskActivity.MAX_CHARS)}자만 넣었어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                // Where it goes, said back with the chips as they stand (PC c0ca3d9f: the line kept the read day after '오늘' was picked,
                // and a time already gone today was dropped without a word).
                if (text.isNotBlank()) {
                    Text(
                        quickSay(line, day, ring, now, dayFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (quickPast(line, day, now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // 2.0.0 [RVI-48] (검토 MEMO-29) The chips wrap to a second line instead of sliding past the edge (큰 글자, 접은 폭).
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    // PC 7차: 오전·오후 first, where the guess is (the line above says which was read).
                    line.at?.takeIf { line.unmarked }?.let { at ->
                        val pm = at.hour >= 12
                        FilterChip(!pm, { half = MemoTime.Half.AM }, { Text("오전") })
                        FilterChip(pm, { half = MemoTime.Half.PM }, { Text("오후") })
                    }
                    val readDay = line.date?.takeIf { it != now.toLocalDate() && it != now.toLocalDate().plusDays(1) }
                    if (readDay != null) FilterChip(day == QuickDay.READ, { day = QuickDay.READ }, { Text(dayFmt.format(readDay)) })
                    FilterChip(day == QuickDay.TODAY || (day == QuickDay.READ && readDay == null && (line.date == null || line.date == now.toLocalDate())),
                        { day = QuickDay.TODAY }, { Text("오늘") })
                    FilterChip(day == QuickDay.TOMORROW || (day == QuickDay.READ && line.date == now.toLocalDate().plusDays(1)),
                        { day = QuickDay.TOMORROW }, { Text("내일") })
                    FilterChip(day == QuickDay.NONE, { day = QuickDay.NONE }, { Text("날짜 없이") })
                    // A time already gone on the day picked cannot ring: no 알림 chip, and the line above says to pick 내일.
                    line.at?.takeIf { day != QuickDay.NONE && !quickPast(line, day, now) }?.let { at -> FilterChip(ring, { ring = !ring }, { Text("알림 ${at.toLocalTime()}") }) }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose) { Text("닫기") }
                    TextButton(onClick = {
                        quickMemo(text, day, ring, Today.dateTime(), half ?: line.half(), shifts = shifts)?.let { memo ->
                            val was = text
                            text = ""; day = QuickDay.READ; ring = true
                            runCatching { focus.requestFocus() }
                            // 2.0.0 [RVI-43] Said once saved; a line that could not be saved comes back to the box.
                            onSave(memo, true) { ok -> if (ok) written = written + memo.title else if (text.isEmpty()) text = was }
                        }
                    }, enabled = text.isNotBlank()) { Text("적고 하나 더") }
                    Spacer(Modifier.height(1.dp).padding(horizontal = 4.dp))
                    Button(onClick = { quickMemo(text, day, ring, Today.dateTime(), half ?: line.half(), shifts = shifts)?.let { onSave(it, false) {} } }, enabled = text.isNotBlank()) { Text("적기") }
                }
            }
        }
    }
}
