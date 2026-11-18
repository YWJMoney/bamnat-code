package com.bamnat.app.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.share.MonthImage
import com.bamnat.app.share.YearRecapImage
import com.bamnat.app.ui.components.ToneLine
import com.bamnat.app.ui.theme.Tone
import com.bamnat.core.work.YearRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1.40 [SPREAD-37] 연말 근무 결산: the picture as it will go, the money's switch (off every time, and not offered while
 * 금액 가리기 is on), the '밤낮' corner (the same standing choice as every picture), then 사진에 저장 and 보내기.
 *
 * 2.0.0 (디자인 품질 조건 7·13·16): a save or a failure is said in the sheet, in one line by the buttons (a toast went under the
 * sheet); nothing to sum up is the empty state with 칸이, in the calendar's own words; sent, the sheet closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearRecapSheet(recap: YearRecap?, onSent: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsRepo = context.appContainer.settingsRepository
    val hideAmounts = remember { settingsRepo.current.hideAmounts }
    var money by remember { mutableStateOf(false) }
    var mark by remember { mutableStateOf(settingsRepo.current.exportMark) }
    var busy by remember { mutableStateOf(false) }
    /** What the last save or send came to, said by the buttons. */
    var said by remember { mutableStateOf<Pair<Tone, String>?>(null) }
    val look = YearRecapImage.Look(money = money && !hideAmounts, mark = mark)
    val preview by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, recap, look) {
        value = recap?.let { r -> withContext(Dispatchers.Default) { runCatching { YearRecapImage.render(r, look).asImageBitmap() }.getOrNull() } }
    }
    val hasMoney = recap?.tiles?.any { it.money } == true
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(recap?.title ?: "연말 결산", style = MaterialTheme.typography.titleLarge)
            if (recap == null) {
                Text("한 해를 모으는 중이에요…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (recap.isEmpty) {
                com.bamnat.app.ui.components.EmptyState("아직 모을 기록이 없어요", when (recap.focus) {
                    com.bamnat.core.work.CalendarFocus.SALES -> "매출을 적으면 여기에 한 해가 모여요"
                    com.bamnat.core.work.CalendarFocus.JOBS -> "일감을 적으면 여기에 한 해가 모여요"
                    else -> "근무를 적으면 여기에 한 해가 모여요"
                })
                return@Column
            }
            // 조건 4: one line; the date it starts from says the calendar was begun then.
            Text(
                if (recap.unfinished) "${recap.period} · 1월에 한 번 더 알려 드려요" else recap.period,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                val bmp = preview
                if (bmp != null) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    Image(bmp, "결산 사진 미리보기", contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(max = 440.dp).aspectRatio(bmp.width.toFloat() / bmp.height, matchHeightConstraintsFirst = true)
                            .clip(MaterialTheme.shapes.small))
                } else Spacer(Modifier.fillMaxWidth().heightIn(min = 240.dp))
            }
            if (hasMoney) Pick(
                "금액 보이기",
                when {
                    hideAmounts -> "금액 가리기가 켜져 있어 사진에 넣지 않아요"
                    money -> "사진에 금액이 들어가요. 받은 분이 다른 곳에 보낼 수 있어요"
                    else -> "처음엔 꺼져 있어요. 켜면 사진에 한 해 금액이 들어가요"
                },
                money && !hideAmounts, enabled = !hideAmounts,
            ) { money = it }
            Pick("밤낮 표시", if (mark) "오른쪽 아래에 작게 ‘${MonthImage.MARK}’. 받은 분이 앱을 찾을 수 있어요"
                else "표시 없이 보내요. 설정 › 데이터에서도 바꿀 수 있어요", mark) { on -> mark = on; settingsRepo.update { it.copy(exportMark = on) } }
            said?.let { (tone, words) -> ToneLine(words, Modifier.padding(top = 4.dp), tone = tone, style = MaterialTheme.typography.bodyMedium) }
            val save: @Composable (Modifier) -> Unit = { m ->
                OutlinedButton(onClick = {
                    busy = true
                    said = null
                    scope.launch {
                        val saved = YearRecapImage.save(context, recap, look)
                        busy = false
                        said = if (saved != null) Tone.Good to "사진 앱의 ‘밤낮’ 앨범에 저장했어요"
                            else Tone.Caution to "저장하지 못했어요. 보내기에서 저장할 곳을 고를 수 있어요"
                        if (saved != null) onSent()
                    }
                }, enabled = !busy, modifier = m.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("사진에 저장")
                }
            }
            val send: @Composable (Modifier) -> Unit = { m ->
                Button(onClick = {
                    busy = true
                    said = null
                    // The sheet closes once the share window is asked for, not at the tap (its scope draws the picture, ImageShareSheet PC 5차).
                    scope.launch {
                        val sent = runCatching { YearRecapImage.share(context, recap, look) }
                        busy = false
                        if (sent.isSuccess) { onSent(); onDismiss() }
                        else said = Tone.Caution to "사진을 만들지 못했어요. 다시 눌러 주세요"
                    }
                }, enabled = !busy, modifier = m.heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (busy) "만드는 중…" else "보내기")
                }
            }
            // 조건 12: side by side while '사진에 저장' fits on one line in half the row (less a button's 24dp sides and its icon);
            // else each on its own line, 보내기 nearest the thumb ('사진에 / 저장' broke in two at 200% on the folded screen).
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                val measurer = rememberTextMeasurer()
                val words = with(LocalDensity.current) { measurer.measure("사진에 저장", MaterialTheme.typography.labelLarge).size.width.toDp() }
                val stack = YearRecapImage.canSave && words > (maxWidth - 8.dp) / 2 - 48.dp - 28.dp
                if (stack) Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    save(Modifier.fillMaxWidth())
                    send(Modifier.fillMaxWidth())
                } else Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (YearRecapImage.canSave) save(Modifier.weight(1f))
                    send(Modifier.weight(1f))
                }
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
