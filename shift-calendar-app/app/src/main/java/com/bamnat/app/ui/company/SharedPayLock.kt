package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
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
    /** 2.0.0 [RVN-18] 검토 T10a-03·COMP-19: [HIDDEN] with the user's own base: 시급·일당 workers read 시급·일당, not 기본급. */
    fun hidden(type: com.bamnat.core.pay.WageType): String = HIDDEN.replace("기본급과", type.baseWord() + com.bamnat.core.text.Josa.gwawa(type.baseWord()))
    /** 1.39 발굴 3-2: the payslip check over a shared way. */
    const val CHECK_TITLE = "명세서와 맞는지 확인"
    const val CHECK_HINT = "명세서 숫자만 넣으면 맞는지 알려 드려요. 계산 방식은 보이지 않아요."
    /** [PAYP-04] Where only the 기본급 is asked over a shared way. */
    const val BASE_ONLY = "계산 방식은 공유한 분의 것 그대로예요. 기본급만 내 명세서대로 넣으면 예상 급여가 맞아요."
    /** T10a-03 [BASE_ONLY] with the user's own base. */
    fun baseOnly(type: com.bamnat.core.pay.WageType): String = BASE_ONLY.replace("기본급만", "${type.baseWord()}만")
    /** [PAYP-04] The 금액 창 after '이 급여 방식 쓰기': what comes with the way, grouped, never its numbers. */
    const val WAY_TITLE = "공유한 분의 계산 방식"
    const val WAY_TEXT = "통상시급 기준 시간, 연장·야간 가산, 회사가 정한 수당과 상여는 공유한 분의 방식대로 들어가요. 계산식은 보이지 않아요. 넣은 뒤 내 명세서와 다르면 급여 화면의 ‘의견 보내기’로 알려 주세요."
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
    if (base) BaseAskSheet(profile, month, onDismiss = { base = false }, locked = true) { b, h -> onBase(b, h); base = false; onDismiss() }
    if (fix) FixRequestSheet(vm, onDismiss = { fix = false; onDismiss() })
    if (leave) LeaveSharedDialog(onLeave = { leave = false; onLeave(); onDismiss() }, onDismiss = { leave = false })
    if (base || fix || leave) return
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(SharedPayWords.TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("${SharedPayWords.from(way)}이에요. ${SharedPayWords.hidden(profile.wageType)}", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(SharedPayWords.ASK, style = MaterialTheme.typography.bodyLarge)
            val known = BaseAsk.known(profile, month)
            Button(onClick = { base = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                // T10a-03 시급·일당 workers are asked their 시급·일당 (the field itself already said so).
                val name = profile.wageType.baseWord()
                Text(if (known == null) "$name 넣기" else "$name 고치기 (지금 ${com.bamnat.app.ui.pay.won(known)})")
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
fun FixRequestSheet(
    vm: PayWayViewModel,
    onDismiss: () -> Unit,
    /** 1.39 발굴 3-2: the places a payslip check found apart, picked for the user (they can change them). */
    initial: List<String> = emptyList(),
    /** 1.39 발굴 3-2: what the payslip check said, over the chips. */
    note: String? = null,
) {
    var picked by rememberSaveable { mutableStateOf(initial.filter { it in FIX_FIELDS }.take(6)) }
    var body by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var sent by remember { mutableStateOf<String?>(null) }
    // 1.39 발굴(마을 돌봄): 보완 요청 한 줄도 다른 마을 글처럼 거친 말이면 보내기 전에 한 번 물어요(막지 않아요).
    var nudge by remember { mutableStateOf(false) }
    val open = vm.fixOpen
    val go = {
        busy = true
        error = null
        vm.fix(picked, body) { e, done -> busy = false; if (e != null) error = e else sent = done }
    }
    if (nudge) com.bamnat.app.ui.hub.NudgeDialog(body, onEdit = { nudge = false }, onSend = { nudge = false; go() })
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (open) "보완 요청" else "달라요 알리기", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(if (open) "어디가 다른 것 같은지 고르고 한 줄 남겨 주세요. 공유한 분이 받아서 고치거나 답해요. 금액·이름·연락처는 적지 말아 주세요."
                else "어디가 다른 것 같은지 골라 주세요. 같은 회사 분들께 ‘달라요’로 보여요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
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
                onClick = { if (open && com.bamnat.core.plaza.PostNudge.needed(body)) nudge = true else go() },
                enabled = !busy && picked.isNotEmpty() && (!open || body.trim().length >= 2),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text(if (open) "보내기" else "달라요 보내기") }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (sent == null) "닫기" else "확인") }
        }
    }
}

/**
 * 1.39 [PAYP-04] (3차 통합 PC "‘이 급여 방식 쓰기’ 직후 금액 창에 ‘통상시급 180시간’과 ‘상여 2·4·6·8·10·12월 기본급 100%’가
 * 보여요"): the 금액 창 groups what comes with a shared way under one card, its names only, so the user sees what goes in
 * without the numbers; what the user types (기본급, 내 수당 금액) stays below it.
 */
@Composable
fun SharedWayNote(modifier: Modifier = Modifier) {
    SoftCard(modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(SharedPayWords.WAY_TITLE, style = MaterialTheme.typography.titleSmall)
            Text(SharedPayWords.WAY_TEXT, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * 1.39 발굴 2-2: where on the pay screen a 보완 요청's fields are set: allowances and bonus in 내 급여 값 › 수당, a shift's times in
 * 근무시간, the rest (가산, 통상시급 기준 시간, 휴게, 그 밖) in 회사 규칙.
 */
fun fixFocus(fields: List<String>): com.bamnat.app.ui.pay.PayFocus = fields.firstNotNullOfOrNull { f ->
    when (f) {
        "allowance", "bonus" -> com.bamnat.app.ui.pay.PayFocus.ITEMS
        "shift" -> com.bamnat.app.ui.pay.PayFocus.HOURS
        "overtime", "night", "holiday", "hours", "break" -> com.bamnat.app.ui.pay.PayFocus.RULES
        else -> null
    }
} ?: com.bamnat.app.ui.pay.PayFocus.RULES

/**
 * 1.39 발굴 3-2 (작은 판): with someone else's way in use the payslip match is not offered (it would change the rules), so this card
 * checks the month against a payslip by amounts alone ([com.bamnat.core.pay.SlipGlance]) and turns the answer into 맞아요, or a
 * 보완 요청 with the places that differ picked. The calculation stays hidden.
 */
@Composable
fun SharedSlipCheckCard(month: com.bamnat.core.pay.MonthPay, modifier: Modifier = Modifier) {
    val calendar = LocalCompanyCalendar.current
    val vm = com.bamnat.app.ui.appViewModel(key = "pay-way-$calendar") { PayWayViewModel(it, calendar) }
    if (!vm.server || month.total <= 0) return
    var open by rememberSaveable { mutableStateOf(false) }
    SoftCard(modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(SharedPayWords.CHECK_TITLE, style = MaterialTheme.typography.titleSmall)
                Text(SharedPayWords.CHECK_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = { open = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("넣기") }
        }
    }
    if (open) SharedSlipCheckSheet(vm, month, onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SharedSlipCheckSheet(vm: PayWayViewModel, month: com.bamnat.core.pay.MonthPay, onDismiss: () -> Unit) {
    var total by rememberSaveable { mutableStateOf<Long?>(null) }
    val parts = remember { androidx.compose.runtime.mutableStateMapOf<com.bamnat.core.pay.SlipGlance.Part, Long>() }
    var result by remember { mutableStateOf<com.bamnat.core.pay.SlipGlance.Result?>(null) }
    var fixing by rememberSaveable { mutableStateOf(false) }
    var said by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val r = result
    if (fixing && r != null) {
        FixRequestSheet(vm, onDismiss = { fixing = false; onDismiss() }, initial = r.fixFields,
            note = com.bamnat.core.pay.SlipGlance.line(r, { v: Long -> com.bamnat.app.ui.pay.won(v) }))
        return
    }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(SharedPayWords.CHECK_TITLE, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("${month.month.monthValue}월 명세서의 숫자를 넣으면 이 방식과 맞는지만 알려 드려요. 계산 방식은 그대로 보이지 않아요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            com.bamnat.app.ui.pay.MoneyField("지급 총액 (세전)", total, onChange = { total = it; result = null },
                placeholder = "명세서의 ‘지급 합계’ 또는 ‘총 지급액’")
            Text("있으면 더 정확해요 (선택)", style = MaterialTheme.typography.titleSmall)
            com.bamnat.core.pay.SlipGlance.Part.entries.forEach { p ->
                com.bamnat.app.ui.pay.MoneyField(p.label, parts[p], onChange = { v -> if (v == null) parts.remove(p) else parts[p] = v; result = null },
                    placeholder = "명세서의 ${p.label} 줄, 모르면 비워 두세요")
            }
            if (r == null) Button(onClick = { total?.let { result = com.bamnat.core.pay.SlipGlance.of(month, it, parts.toMap()) } },
                enabled = (total ?: 0) > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("맞는지 보기") }
            else {
                Text(com.bamnat.core.pay.SlipGlance.line(r, { v: Long -> com.bamnat.app.ui.pay.won(v) }), style = MaterialTheme.typography.titleMedium,
                    color = if (r.matched) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                said?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                if (said == null) {
                    if (r.matched) Button(onClick = {
                        busy = true
                        vm.vote(true, null) { e -> busy = false; said = e ?: "‘맞아요’를 보냈어요. 같은 회사 분들께 큰 힘이 돼요" }
                    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("맞아요 보내기") }
                    else Button(onClick = { fixing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                        Text(if (vm.fixOpen) "보완 요청 보내기" else "달라요 알리기")
                    }
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text(if (said == null) "닫기" else "확인") }
        }
    }
}

/** 2.0.0 [RVN-18] 검토 T10a-03: what a 기본급 is called for this way of being paid (월급 기본급, 시급, 일당). */
internal fun com.bamnat.core.pay.WageType.baseWord(): String = when (this) {
    com.bamnat.core.pay.WageType.MONTHLY -> "기본급"
    com.bamnat.core.pay.WageType.HOURLY -> "시급"
    com.bamnat.core.pay.WageType.DAILY -> "일당"
}
