package com.bamnat.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ReportProblem
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.company.InquiryKind
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.stats.PhotoScreen
import com.bamnat.core.stats.PhotoWrongReason

/**
 * [PHOTO-09] 1.29 AQ: '사진이 달라요' on every screen that shows what a photo was read as. One tap asks why (a few fixed reasons)
 * and keeps a count: the date, the screen, the reason and the app version, never the photo or its text. The count goes once
 * 처리방침 3.3 lets it (row `photo.wrong`) and waits on the phone until then, offline too. '직접 문의하기' opens 문의하기 on
 * '앱이 이상해요' with the screen named, for a person to answer; [onAsk] closes what is open over it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhotoWrongButton(screen: PhotoScreen, modifier: Modifier = Modifier, onAsk: (() -> Unit)? = null) {
    val context = LocalContext.current
    var open by rememberSaveable { mutableStateOf(false) }
    var reason by rememberSaveable { mutableStateOf<PhotoWrongReason?>(null) }
    var done by rememberSaveable { mutableStateOf(false) }
    TextButton(onClick = { open = true; done = false; reason = null }, modifier = modifier.heightIn(min = 48.dp)) {
        Icon(Icons.Outlined.ReportProblem, null, Modifier.size(18.dp))
        Spacer(Modifier.width(6.dp))
        Text("사진이 달라요")
    }
    if (!open) return
    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text(if (done) "알려 줘서 고마워요" else "사진과 다르게 읽었나요?") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Text("읽는 방법을 고치는 데 써요. 틀린 칸은 이 화면에서 바로 고치거나 더할 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                    Text("자세히 알려 주시면 더 빨리 고칠 수 있어요. ‘직접 문의하기’에 사진을 붙일 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("어떤 점이 다른지 하나 골라 주세요.", style = MaterialTheme.typography.bodyMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        // [PHOTO-28] 1.30: 통상시급·기준 시간 on a payslip's or the rules' screen only.
                        PhotoWrongReason.of(screen).forEach { r ->
                            FilterChip(selected = reason == r, onClick = { reason = r }, label = { Text(r.label) }, modifier = Modifier.heightIn(min = 48.dp))
                        }
                    }
                    Text("사진과 읽은 글은 보내지 않아요. 날짜·화면 종류·고른 이유·앱 버전의 횟수만 모아요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (done) TextButton(onClick = { open = false }) { Text("닫기") }
            else Button(onClick = {
                val r = reason ?: return@Button
                runCatching {
                    val c = context.appContainer
                    c.photoWrong.record(screen, r)
                    if (c.flags.on(FlagCatalog.PHOTO_WRONG)) com.bamnat.app.company.OutboxJob.schedule(context)
                }
                done = true
            }, enabled = reason != null) { Text("알리기") }
        },
        dismissButton = {
            TextButton(onClick = {
                open = false
                com.bamnat.app.ui.settings.InquiryLinks.ask(
                    com.bamnat.app.ui.settings.InquiryStart(about = "사진이 달라요 › ${screen.label}", kind = InquiryKind.BUG),
                )
                onAsk?.invoke()
            }) { Text("직접 문의하기") }
        },
    )
}
