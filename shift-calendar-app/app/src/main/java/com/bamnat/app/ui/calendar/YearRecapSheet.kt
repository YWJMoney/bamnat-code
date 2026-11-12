package com.bamnat.app.ui.calendar

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.share.MonthImage
import com.bamnat.app.share.YearRecapImage
import com.bamnat.core.work.YearRecap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 1.40 [SPREAD-37] 연말 근무 결산: the picture as it will go, the money's switch (off every time, and not offered while
 * 금액 가리기 is on), the '밤낮' corner (the same standing choice as every picture), then 사진에 저장 and 보내기.
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
            Text(recap?.title ?: "내 근무 결산", style = MaterialTheme.typography.titleLarge)
            if (recap == null) {
                Text("한 해를 모으는 중이에요…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            if (recap.isEmpty) {
                Text("아직 모을 기록이 없어요. 근무를 적으면 여기에 한 해가 모여요.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text(
                buildString {
                    append(recap.period)
                    if (recap.partial) append(" · 이 달력을 시작한 날부터 셌어요")
                    if (recap.unfinished) append(" · 1월에 한 해를 다 채워 다시 알려 드려요")
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
                val bmp = preview
                if (bmp != null) Box(Modifier.fillMaxWidth().padding(8.dp), contentAlignment = Alignment.Center) {
                    Image(bmp, "결산 사진 미리보기", contentScale = ContentScale.Fit,
                        modifier = Modifier.heightIn(max = 440.dp).aspectRatio(bmp.width.toFloat() / bmp.height, matchHeightConstraintsFirst = true)
                            .clip(RoundedCornerShape(8.dp)))
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
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (YearRecapImage.canSave) OutlinedButton(onClick = {
                    busy = true
                    scope.launch {
                        val saved = YearRecapImage.save(context, recap, look)
                        busy = false
                        android.widget.Toast.makeText(context, if (saved != null) "사진에 저장했어요 (사진 › 밤낮)" else "저장하지 못했어요. 보내기로 저장해 주세요",
                            android.widget.Toast.LENGTH_SHORT).show()
                        if (saved != null) onSent()
                    }
                }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Download, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("사진에 저장")
                }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        val sent = runCatching { YearRecapImage.share(context, recap, look) }
                        busy = false
                        if (sent.isSuccess) onSent()
                        else android.widget.Toast.makeText(context, "사진을 만들지 못했어요. 다시 눌러 주세요", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }, enabled = !busy, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                    Icon(Icons.Outlined.Share, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("보내기")
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
