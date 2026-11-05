package com.bamnat.app.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.share.MonthImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.YearMonth

/**
 * 1.38 [EASE-28] (오늘근무 분석 P10: 캡처로 보내기는 스티커 켬·메모 끔이 기본, 공개 관찰): before a month goes out as a
 * picture, the sharer sees the picture itself and picks what rides on it. 메모 글 starts off every time (a memo is
 * personal and the picture leaves the phone for good); 메모 그림(앞 이모지), 공휴일 이름, 근무 이름표 start on.
 * Nothing here is kept: the next picture starts from the same safe defaults.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageShareSheet(
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    weekStart: DayOfWeek,
    memos: List<com.bamnat.core.memo.Memo>,
    onSent: () -> Unit,
    onDismiss: () -> Unit,
    /** The name the calendar's link went out with, if any: the picture's title can say whose it is. */
    startName: String = "",
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var memoOn by rememberSaveable { mutableStateOf(false) }
    var stickers by rememberSaveable { mutableStateOf(true) }
    var holidays by rememberSaveable { mutableStateOf(true) }
    var legend by rememberSaveable { mutableStateOf(true) }
    var name by rememberSaveable { mutableStateOf(startName) }
    val notes = androidx.compose.runtime.remember(memos, month) { MonthImage.notes(memos, month) }
    val hasMemo = notes.values.any { it.memo != null }
    val hasSticker = notes.values.any { it.sticker != null }
    val look = MonthImage.Look(memos = memoOn && hasMemo, stickers = stickers, holidays = holidays, legend = legend, name = name)
    // The picture as it will go, drawn off the main thread each time a choice changes.
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, look, snapshot, month, notes) {
        value = withContext(Dispatchers.Default) {
            runCatching { MonthImage.render(snapshot, month, weekStart, look, notes).asImageBitmap() }.getOrNull()
        }
    }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("${month.monthValue}월 사진으로 보내기", style = MaterialTheme.typography.titleLarge)
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                val bmp = preview
                if (bmp != null) Image(bmp, "보낼 사진 미리보기", contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).clip(RoundedCornerShape(12.dp)))
                else Spacer(Modifier.fillMaxWidth().heightIn(min = 200.dp))
            }
            Text("사진에 같이 넣을 것", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
            Pick("메모 글", when {
                !hasMemo -> "이달에 적은 메모가 없어요"
                memoOn -> "날짜마다 메모 첫 줄이 보여요. 받는 분이 사진을 다른 곳에 보낼 수 있어요"
                else -> "개인 메모라 처음엔 꺼져 있어요"
            }, memoOn && hasMemo, enabled = hasMemo) { memoOn = it }
            Pick("메모 그림", if (hasSticker) "메모 앞의 그림(🎂·✈️ 같은 이모지)만 날짜 옆에 붙여요. 글은 안 가요"
                else "메모를 🎂·✈️ 같은 그림으로 시작하면 날짜 옆에 붙어요", stickers) { stickers = it }
            Pick("공휴일 이름", "날짜 아래 작게 써요", holidays) { holidays = it }
            Pick("근무 이름표", "사진 아래 근무 색과 시간", legend) { legend = it }
            OutlinedTextField(value = name, onValueChange = { if (it.length <= 12) name = it }, singleLine = true,
                label = { Text("제목에 넣을 이름 (선택)") }, placeholder = { Text("예: 민지") },
                supportingText = { Text("‘${name.trim().ifEmpty { "민지" }}의 ${month.monthValue}월 근무표’로 보여요") },
                modifier = Modifier.fillMaxWidth())
            Button(onClick = {
                scope.launch { runCatching { MonthImage.share(context, snapshot, month, weekStart, look, notes) } }
                onSent(); onDismiss()
            }, modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp)) {
                Icon(Icons.Outlined.Image, null, Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("이 사진 보내기")
            }
        }
    }
}

@Composable
private fun Pick(title: String, hint: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null, enabled = enabled)
    }
}
