package com.bamnat.app.alarm

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.ZeroCornerSize
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
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.theme.BamnatTheme
import com.bamnat.app.widget.WidgetRefresher
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch

/**
 * 2.0.0 [RVI-88] (검토 I03b-03) 자기 전에 가장 많이 보는 한 가지: the today widget's alarm (⏰ 06:00) opens this small window over the
 * home screen, "내일 오전 6:00 · 주간 근무 알람" with 이번만 끄기 (or 다시 켜기 when it was put aside). The same skip as 곧 알람이
 * 울려요 ([AlarmSoon.skip]): that one alarm only, its notice offers 다시 켜기, and the next one is planned.
 */
class AlarmPeekActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val at = intent.getLongExtra(EXTRA_AT, 0L)
        val what = intent.getStringExtra(EXTRA_WHAT).orEmpty()
        // Rang or past already (the widget was drawn a while ago): nothing to put aside.
        if (at <= Today.millis()) { finish(); return }
        val off = AlarmSoon.isSkipped(this, at)
        setContent {
            BamnatTheme {
                AlarmPeek(peekTitle(at, what), off, onClose = { finish() }, onToggle = {
                    lifecycleScope.launch {
                        if (off) AlarmSoon.unskip(applicationContext, at) else AlarmSoon.skip(applicationContext, at)
                        runCatching { WidgetRefresher.refresh(applicationContext, "alarm_peek") }
                        finish()
                    }
                })
            }
        }
    }

    companion object {
        private const val EXTRA_AT = "at"
        private const val EXTRA_WHAT = "what"
        private const val REQUEST = 0x4D100

        fun intent(context: Context, at: Long, what: String): Intent = Intent(context, AlarmPeekActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(EXTRA_AT, at).putExtra(EXTRA_WHAT, what)

        fun pending(context: Context, at: Long, what: String): PendingIntent =
            PendingIntent.getActivity(context, REQUEST, intent(context, at, what), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** "내일 오전 6:00 · 주간 근무 알람", "오늘 오후 5:00 · 영업 알람". */
internal fun peekTitle(at: Long, what: String): String {
    val t = AlarmScheduler.millisToLocal(at)
    val day = when (t.toLocalDate()) {
        Today.date() -> "오늘"
        Today.date().plusDays(1) -> "내일"
        else -> Fmt.shortDate(t.toLocalDate())
    }
    return "$day ${Fmt.time(t.toLocalTime())}" + (if (what.isNotBlank()) " · $what" else "")
}

@Composable
internal fun AlarmPeek(title: String, off: Boolean, onClose: () -> Unit, onToggle: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.32f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
            .semantics { contentDescription = "닫기" },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            Modifier.widthIn(max = 560.dp).fillMaxWidth().navigationBarsPadding()
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { },
            shape = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
            tonalElevation = 3.dp,
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(if (off) "이번만 꺼 두었어요. 그다음 알람은 그대로 울려요" else "이번 한 번만 끄고, 그다음 알람은 그대로 울려요",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onClose, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                    OutlinedButton(onClick = onToggle, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (off) "다시 켜기" else "이번만 끄기") }
                }
            }
        }
    }
}
