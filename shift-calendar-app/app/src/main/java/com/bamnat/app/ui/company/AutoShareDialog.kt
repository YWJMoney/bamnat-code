package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.AutoShare

/**
 * [PLAZA-07] 결정 D79: the one-time ask, the first time a cycle or pay way is saved for a known company. Yes is
 * remembered and opens the company's 광장; no is remembered too. 설정 > 데이터·개인정보 changes it later.
 */
@Composable
fun AutoShareHost(c: AppContainer, onOpen: (String) -> Unit) {
    val ask by c.autoShare.ask.collectAsStateWithLifecycle()
    val opened by c.autoShare.opened.collectAsStateWithLifecycle()
    LaunchedEffect(opened) {
        val id = opened ?: return@LaunchedEffect
        c.autoShare.openedDone()
        onOpen(id)
    }
    ask?.let { AutoShareDialog(it, onYes = c.autoShare::approve, onNo = c.autoShare::decline) }
}

@Composable
fun AutoShareDialog(ask: AutoShare.Ask, onYes: () -> Unit, onNo: () -> Unit) {
    AlertDialog(
        onDismissRequest = onNo,
        title = { Text("같은 회사 사람들이 쓰게 근무 순서·계산 방식(금액·이름 빼고)을 나눌까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${ask.company} 광장에 나눠요. 바꿀 때마다 하루 한 번 저절로 고쳐 올려요.", style = MaterialTheme.typography.bodyMedium)
                Text("금액, 이름, 연락처, 입사일, 명세서와 사진은 보내지 않아요. 설정 > 데이터·개인정보에서 언제든 끌 수 있어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (ask.rewardMonths > 0) {
                    Text("처음 알려 주는 급여 방식이면 프로 ${ask.rewardMonths}개월을 드려요.", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = { TextButton(onClick = onYes) { Text("나눌게요") } },
        dismissButton = { TextButton(onClick = onNo) { Text("나누지 않을래요") } },
    )
}
