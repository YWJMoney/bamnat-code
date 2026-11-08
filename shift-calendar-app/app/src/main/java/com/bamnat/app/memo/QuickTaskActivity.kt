package com.bamnat.app.memo

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
import androidx.compose.material3.Button
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
import kotlinx.coroutines.launch

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
        val shared = if (intent?.action == Intent.ACTION_SEND) intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty().take(MAX_CHARS) else ""
        setContent {
            BamnatTheme {
                QuickTask(shared, onClose = { finish() }, onSave = { memo, more ->
                    lifecycleScope.launch {
                        val saved = runCatching { appContainer.memoRepository.save(memo) }.isSuccess
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

/** Which day the line goes on: what was read, today, tomorrow, or none (a note pinned for later). */
internal enum class QuickDay { READ, TODAY, TOMORROW, NONE }

/** The memo [text] makes with the chips as they stand, or null when there is nothing written. */
internal fun quickMemo(text: String, day: QuickDay, ring: Boolean, now: LocalDateTime): Memo? {
    if (text.isBlank()) return null
    val line = MemoTime.line(text, now)
    val date: LocalDate? = quickDate(line, day, now)
    // The time read goes with the day picked (내일 저녁 6시, moved to 오늘, rings 오늘 저녁 6시); no day, no ring.
    val at = line.at?.let { a -> date?.atTime(a.toLocalTime()) }?.takeIf { ring && it.isAfter(now) }
    return Memo(
        date = date,
        title = line.title,
        body = line.rest,
        reminder = at?.let { MemoReminder(it, ring = false, notify = true) },
        // A line with no day stays at hand as a pinned note, as on the widget's 고정 list.
        pinned = date == null,
    )
}

/** The day the chips put the line on, as [quickMemo] does. */
private fun quickDate(line: MemoTime.Line, day: QuickDay, now: LocalDateTime): LocalDate? = when (day) {
    QuickDay.READ -> line.date ?: now.toLocalDate()
    QuickDay.TODAY -> now.toLocalDate()
    QuickDay.TOMORROW -> now.toLocalDate().plusDays(1)
    QuickDay.NONE -> null
}

/** Whether the time read is already gone on the day picked (오늘 21:00 at 23:06): it cannot ring, so the window says so. */
internal fun quickPast(line: MemoTime.Line, day: QuickDay, now: LocalDateTime): Boolean {
    val at = line.at ?: return false
    val date = quickDate(line, day, now) ?: return false
    return !date.atTime(at.toLocalTime()).isAfter(now)
}

/** "제목: 운동 · 11/7 (토) 21:00 알림", "… · 21:00은 지났어요, 시각 없이 적어요", "… · 날짜 없이 고정": where 적기 puts it now. */
internal fun quickSay(line: MemoTime.Line, day: QuickDay, ring: Boolean, now: LocalDateTime, dayFmt: DateTimeFormatter): String {
    val date = quickDate(line, day, now)
    val time = line.at?.toLocalTime()
    val where = when {
        date == null -> "날짜 없이 고정"
        time == null -> dayFmt.format(date)
        quickPast(line, day, now) -> "${dayFmt.format(date)} · ${time}은 지났어요. 시각 없이 적어요(알림은 '내일'을 골라요)"
        ring -> "${dayFmt.format(date)} $time 알림"
        else -> "${dayFmt.format(date)} · 알림 없이"
    }
    return "제목: ${line.title} · $where"
}

@Composable
internal fun QuickTask(shared: String, onClose: () -> Unit, onSave: (Memo, Boolean) -> Unit) {
    var text by rememberSaveable { mutableStateOf(shared) }
    // 1.39 [TASK-39] 적고 하나 더 (두 앱 실사용 2차: 할 일 앱의 넣기 창은 열린 채 남아 이어 적어요): what was just written, said in the window.
    var written by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var day by rememberSaveable { mutableStateOf(QuickDay.READ) }
    var ring by rememberSaveable { mutableStateOf(true) }
    val now = remember { Today.dateTime() }
    val line = remember(text) { MemoTime.line(text, now) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val dayFmt = remember { DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN) }
    // The dim around the card closes it, as a sheet does.
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semantics { contentDescription = "닫기" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().imePadding().navigationBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
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
                )
                // Where it goes, said back with the chips as they stand (PC c0ca3d9f: the line kept the read day after '오늘' was picked,
                // and a time already gone today was dropped without a word).
                if (text.isNotBlank()) {
                    Text(
                        quickSay(line, day, ring, now, dayFmt),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (quickPast(line, day, now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        quickMemo(text, day, ring, Today.dateTime())?.let { memo ->
                            onSave(memo, true)
                            written = written + memo.title
                            text = ""; day = QuickDay.READ; ring = true
                            runCatching { focus.requestFocus() }
                        }
                    }, enabled = text.isNotBlank()) { Text("적고 하나 더") }
                    Spacer(Modifier.height(1.dp).padding(horizontal = 4.dp))
                    Button(onClick = { quickMemo(text, day, ring, Today.dateTime())?.let { onSave(it, false) } }, enabled = text.isNotBlank()) { Text("적기") }
                }
            }
        }
    }
}
