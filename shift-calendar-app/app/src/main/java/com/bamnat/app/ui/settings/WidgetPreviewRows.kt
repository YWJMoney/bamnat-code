package com.bamnat.app.ui.settings

import android.widget.FrameLayout
import android.widget.RemoteViews
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.bamnat.app.appContainer
import com.bamnat.app.widget.WidgetPreviews
import com.bamnat.core.time.Today

/**
 * 1.38 [EASE-29] (오늘근무 분석 R8·P11, 공개 관찰): above the widget rows, the widgets as they look now with the user's own
 * days, redrawn as soon as 위젯 모양·투명도·메모 change, so nobody has to put one on the home screen to see it.
 * [key] changes whenever one of those settings does.
 */
@Composable
internal fun WidgetPreviewStrip(key: Any, modifier: Modifier = Modifier) {
    val kinds = remember { WidgetPreviews.Kind.entries }
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text("내 위젯 미리보기", style = MaterialTheme.typography.titleSmall)
        Text("홈 화면에 놓으면 이렇게 보여요. 아래에서 모양을 바꾸면 바로 바뀌어요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            kinds.forEach { kind -> OneWidget(kind, key) }
        }
    }
}

@Composable
private fun OneWidget(kind: WidgetPreviews.Kind, key: Any) {
    val context = LocalContext.current
    val views by produceState<RemoteViews?>(null, key, kind) {
        // The settings change is saved a moment after the row is tapped; the widget reads what was saved.
        kotlinx.coroutines.delay(150)
        value = runCatching { WidgetPreviews.views(context, kind) }.getOrNull()
    }
    Column {
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHighest,
            modifier = Modifier.size(kind.size.width, kind.size.height).semantics { contentDescription = "${kind.label} 위젯 미리보기" }) {
            views?.let { v ->
                AndroidView(factory = { FrameLayout(it) }, update = { frame ->
                    frame.removeAllViews()
                    runCatching { v.apply(frame.context, frame) }.getOrNull()?.let(frame::addView)
                })
            }
        }
        Text(kind.label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 4.dp))
    }
}

/**
 * 1.38 [EASE-29] Under 잠금 화면 오늘 근무: the notice as the lock screen will show it, with the user's today and tomorrow.
 */
@Composable
internal fun LockNoticePreview(modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val snapshot by c.scheduleRepository.snapshot.collectAsState(null)
    val lines = remember(snapshot) {
        snapshot?.takeIf { !it.isEmpty }?.let { com.bamnat.app.alarm.TodayShiftNotice.lines(it.resolver, Today.date()) }
    } ?: return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .semantics(mergeDescendants = true) { contentDescription = "잠금 화면 미리보기: ${lines.first}, ${lines.second}" }) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text("밤낮 · 잠금 화면에서", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(lines.first, style = MaterialTheme.typography.titleSmall)
            Text(lines.second, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
