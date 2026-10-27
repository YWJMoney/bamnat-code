package com.bamnat.app.ui.invest

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

/**
 * [INVA-01] 1.29 AR: a screenshot shared to 밤낮 while a calendar of another kind is on screen, when the user also keeps an
 * investor calendar. Before 1.29 it always went to 한 달 근무 넣기 then, so a 체결 내역 capture never reached the 투자 tab
 * (사용자 2026-10-27 "캡쳐로 놓기하면 … 동작하지 않는다"). One question, then it goes where the user said.
 */
@Composable
fun SharedShotChoice(investName: String, onInvest: () -> Unit, onRoster: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("이 사진을 어디에 넣을까요?") },
        text = { Text("증권사·거래소 체결 내역이면 ‘$investName’ 투자 달력에 넣고, 근무표면 한 달 근무 넣기로 읽어요. 사진은 휴대폰 안에서만 읽어요.") },
        confirmButton = { TextButton(onClick = onInvest) { Text("투자 체결 내역") } },
        dismissButton = { TextButton(onClick = onRoster) { Text("근무표") } },
    )
}
