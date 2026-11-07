package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.company.PayWay
import com.bamnat.app.company.Plaza
import com.bamnat.app.ui.components.Button
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.time.Today

/**
 * 1.39 [PAYP-04] (사용자 2026-11-07): the words the pay screen says over a shared way. One place, so the card, the sheet and the tests
 * say the same.
 */
object SharedPayWords {
    const val TITLE = "다른 사람이 공유한 급여체계를 사용하고 있어요!"
    const val ASK = "다르다면 의견을 제시해 보거나 직접 만드시겠어요?"
    const val HIDDEN = "계산 방식은 공유한 분의 것이라 볼 수도 고칠 수도 없어요. 기본급과 내 수당 금액은 내 명세서대로 넣어요."
    const val LEAVE_TITLE = "직접 만들까요?"
    const val LEAVE_TEXT = "공유한 급여 계산 방식을 빼고 급여 설정을 처음부터 만들어요. 빼면 되돌릴 수 없고, 다시 쓰려면 ‘우리 회사 급여 방식 찾기’에서 다시 넣어요."

    /** Where the way came from: "‘현대제철 · 당진’ 급여 방식". */
    fun from(w: PayWay): String = "‘${w.company}${w.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}’ 급여 방식"
}

/**
 * 1.39 [PAYP-04] What the pay screen opens instead of the setup, the rules or 고친 기록 while the pay uses someone else's way: that it
 * is shared and locked, the 기본급 (the user's own) to put in, 의견 보내기 and 직접 만들기.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharedPayLockSheet(
    way: PayWay,
    profile: PayProfile,
    onBase: (Long, Int?) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val calendar = LocalCompanyCalendar.current
    val vm = com.bamnat.app.ui.appViewModel(key = "pay-way-$calendar") { PayWayViewModel(it, calendar) }
    var base by rememberSaveable { mutableStateOf(false) }
    var fix by rememberSaveable { mutableStateOf(false) }
    var leave by rememberSaveable { mutableStateOf(false) }
    val month = remember { Today.month() }
    if (base) BaseAskSheet(profile, month, onDismiss = { base = false }) { b, h -> onBase(b, h); base = false; onDismiss() }
    if (fix) FixRequestSheet(vm, onDismiss = { fix = false; onDismiss() })
    if (leave) LeaveSharedDialog(onLeave = { leave = false; onLeave(); onDismiss() }, onDismiss = { leave = false })
    if (base || fix || leave) return
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(SharedPayWords.TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("${SharedPayWords.from(way)}이에요. ${SharedPayWords.HIDDEN}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(SharedPayWords.ASK, style = MaterialTheme.typography.bodyLarge)
            val known = BaseAsk.known(profile, month)
            Button(onClick = { base = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text(if (known == null) "기본급 넣기" else "기본급 고치기 (지금 ${com.bamnat.app.ui.pay.won(known)})")
            }
            if (vm.server) OutlinedButton(onClick = { fix = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("의견 보내기") }
            OutlinedButton(onClick = { leave = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("직접 만들기") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        }
    }
}

@Composable
fun LeaveSharedDialog(onLeave: () -> Unit, onDismiss: () -> Unit) {
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(SharedPayWords.LEAVE_TITLE) },
        text = { Text(SharedPayWords.LEAVE_TEXT) },
        confirmButton = { TextButton(onClick = onLeave) { Text("직접 만들기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("그대로 쓰기") } },
    )
}

/** 1.39 [PAYP-05] Where a 보완 요청 may say a way differs, in the plaza's words (no values: a value would show the calculation). */
val FIX_FIELDS: List<String> = listOf("overtime", "night", "holiday", "allowance", "bonus", "hours", "break", "shift", "order", "other")

/**
 * 1.39 [PAYP-05] (사용자 2026-11-07 "보완요청을 하면 어떤점이 다른거 같아요를 이야기할수 있게해서 제공한 사용자가 수정을 할수 있게"): where
 * the way differs (chips, one or more) and a line, to the version's talk as a 보완 요청; its senders hear of it in 밤낮 소식 and answer
 * or send a corrected version. Before 처리방침 3.12 is in force (plaza.fix_request off) it is 달라요 with where, as before 1.39.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FixRequestSheet(vm: PayWayViewModel, onDismiss: () -> Unit) {
    var picked by rememberSaveable { mutableStateOf(listOf<String>()) }
    var body by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf<String?>(null) }
    val open = vm.fixOpen
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (open) "보완 요청" else "달라요 알리기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(if (open) "어디가 다른 것 같은지 고르고 한 줄 남겨 주세요. 공유한 분이 받아서 고치거나 답해요. 금액·이름·연락처는 적지 말아 주세요."
                else "어디가 다른 것 같은지 골라 주세요. 같은 회사 분들께 ‘달라요’로 보여요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (f in FIX_FIELDS) {
                    val on = f in picked
                    FilterChip(selected = on, onClick = { picked = if (on) picked - f else (picked + f).take(6) },
                        label = { Text(Plaza.CHANGE_FIELDS[f] ?: f) }, modifier = Modifier.heightIn(min = 48.dp))
                }
            }
            if (open) OutlinedTextField(value = body, onValueChange = { body = it.take(500) }, label = { Text("어떤 점이 다른 것 같아요?") },
                placeholder = { Text("예: 명세서에는 야간이 22시부터예요") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            sent?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
            if (sent == null) Button(
                onClick = {
                    busy = true
                    error = null
                    vm.fix(picked, body) { e, done -> busy = false; if (e != null) error = e else sent = done }
                },
                enabled = !busy && picked.isNotEmpty() && (!open || body.trim().length >= 2),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (open) "보내기" else "달라요 보내기") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (sent == null) "닫기" else "확인") }
        }
    }
}
