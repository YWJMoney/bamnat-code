package com.bamnat.app.memo

import android.app.PendingIntent
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.core.memo.MemoSnooze
import com.bamnat.core.time.Today
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

/**
 * 1.39 [TASK-39] '다른 때…' on a memo's schedule notice: a small window over whatever is on the screen that asks when to tell again
 * (두 앱 실사용 2차: the 할 일 앱 put off 5분 only, the 비즈 달력 asked 얼마 동안 / 언제까지). The day off's morning comes first, as
 * 밤낮 goes by the shifts; then 10분·30분·1시간·3시간 뒤 and 내일 이 시각. Nothing moves until one is picked.
 */
class MemoLaterActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val id = intent.getLongExtra(EXTRA_MEMO, -1L)
        val rest = intent.getLongExtra(EXTRA_REST, Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE }?.let(LocalDate::ofEpochDay)
        setContent {
            BamnatTheme {
                MemoLater(rest, onClose = { finish() }, onPick = { way ->
                    lifecycleScope.launch {
                        val at = runCatching { MemoSnoozeReceiver.put(applicationContext, id, way, rest) }.getOrNull()
                        Toast.makeText(applicationContext, at?.let { "${laterSaid(it, Today.dateTime())}에 다시 알려요" } ?: "메모를 찾지 못했어요",
                            Toast.LENGTH_SHORT).show()
                        finish()
                    }
                })
            }
        }
    }

    companion object {
        private const val EXTRA_MEMO = "memo"
        private const val EXTRA_REST = "rest"
        private const val REQUEST_BASE = 0x4C000

        fun intent(context: Context, memo: Long, rest: LocalDate?): PendingIntent = PendingIntent.getActivity(
            context, REQUEST_BASE + (memo.toInt() and 0x3ff),
            Intent(context, MemoLaterActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra(EXTRA_MEMO, memo).apply { rest?.let { putExtra(EXTRA_REST, it.toEpochDay()) } },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** "오후 3:10", "내일 오후 3:00", "11/12 (목) 오전 9:00": when it will tell again, as the toast says it. */
internal fun laterSaid(at: LocalDateTime, now: LocalDateTime): String {
    val time = DateTimeFormatter.ofPattern("a h:mm", Locale.KOREAN).format(at)
    return when (at.toLocalDate()) {
        now.toLocalDate() -> time
        now.toLocalDate().plusDays(1) -> "내일 $time"
        else -> DateTimeFormatter.ofPattern("M/d (E)", Locale.KOREAN).format(at) + " " + time
    }
}

@Composable
internal fun MemoLater(rest: LocalDate?, onClose: () -> Unit, onPick: (MemoSnooze.Way) -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semantics { contentDescription = "닫기" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().navigationBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("언제 다시 알릴까요?", style = MaterialTheme.typography.titleMedium)
                MemoSnooze.choices(rest).chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        pair.forEach { way ->
                            val words = when (way) {
                                MemoSnooze.Way.REST -> MemoSnooze.label(way, rest) + " 아침"
                                MemoSnooze.Way.TOMORROW -> "내일 이 시각"
                                else -> MemoSnooze.label(way, rest)
                            }
                            OutlinedButton(onClick = { onPick(way) }, modifier = Modifier.weight(1f)) { Text(words, maxLines = 1) }
                        }
                        if (pair.size == 1) Box(Modifier.weight(1f))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onClose) { Text("닫기") }
                }
            }
        }
    }
}
