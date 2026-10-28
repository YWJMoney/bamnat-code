package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.bamnat.app.appContainer
import com.bamnat.app.company.SendGuard
import com.bamnat.core.plaza.DeptGroups
import com.bamnat.core.plaza.SetupCompare

/** "‘(주)예시정밀 · 생산팀’": a company and a department as the plaza names them. */
private fun place(company: String, dept: String) = "‘$company · ${dept.ifEmpty { "회사 전체" }}’"

/**
 * [PLZD-01][PLZD-02][PLZD-03][PLZD-05] 1.30 묶음 BD: what 내 설정 나누기 asks when [SendGuard] held a send. The conclusion
 * first, one main button:
 *  - the same rule already there: 이 규칙 쓰기 (nothing new goes);
 *  - another shape: only under another department's name, or a look at the 대표 first;
 *  - other values: 달라진 점만 제안하기 first, a new version second;
 *  - a version sent today: 바꿔서 보내기.
 */
@Composable
fun SendGuardAsk(
    held: CompanyReportViewModel.Sending.Guarded,
    onUse: (SendGuard.Same) -> Unit,
    onSend: (dept: String) -> Unit,
    onPropose: (SendGuard.Differ) -> Unit,
    onOpenCompany: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val f = held.form
    when (val v = held.verdict) {
        // A 1.32 [FIX132-04] (사용자 2026-10-28 "같은 규칙이면 기분 나쁘지 않게 '이미 같은 방식이 있어요! 구경하러 갈까요?'"):
        // glad words, the same rule counted as one more 맞아요, and a look at it.
        is SendGuard.Same -> GuardDialog(
            title = "이미 같은 방식이 있어요! 🎉",
            onDismiss = onDismiss,
        ) {
            GuardText("${place(v.page.name, v.setup.dept)}에 내 달력과 똑같은 방식이 먼저 올라와 있어요. 제대로 맞췄다는 뜻이에요! " +
                "새로 올리지 않고 이 방식을 쓰면 ‘맞아요’ 1명으로 세서 같은 회사 분들이 더 믿고 쓸 수 있어요.")
            DeptGroups.summary(v.setup.text)?.let { GuardLine(it) }
            // [FIN130-01] The pay way goes in with the 기본급 on the pay screen (PendingWay), never before it.
            if (v.withPay) GuardLine("이 규칙의 급여 계산 방식은 급여 화면에서 기본급을 넣으면 같이 들어가요. 그 전에는 내 급여가 그대로예요.")
            GuardButtons {
                Button(onClick = { onUse(v) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("맞았다고 알리고 이 방식 쓰기") }
                OutlinedButton(onClick = { onOpenCompany(v.page.id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("구경하러 가기") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
            }
        }
        is SendGuard.Shape -> {
            var name by rememberSaveable(v.lead.id) { mutableStateOf("") }
            val taken = DeptGroups.key(name) == DeptGroups.key(v.lead.dept)
            GuardDialog(title = "근무 모양이 달라요", onDismiss = onDismiss) {
                GuardText("${place(v.page.name, v.lead.dept)} 대표 판은 ${v.theirs}인데, 내 달력은 ${v.mine}예요. " +
                    "같은 부서라면 대표 판을 먼저 확인해 주세요.")
                GuardLine("다른 부서나 공장이라면 그 이름으로 보내 주세요. 같은 이름으로는 올라가지 않아요.")
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(20) },
                    label = { Text("부서·공장 이름") }, singleLine = true,
                    placeholder = { Text("예: 온산공장") },
                    isError = name.isNotBlank() && taken,
                    supportingText = { if (name.isNotBlank() && taken) Text("대표 판과 다른 이름을 적어 주세요") },
                    modifier = Modifier.fillMaxWidth(),
                )
                GuardButtons {
                    Button(onClick = { onSend(name.trim()) }, enabled = name.isNotBlank() && !taken,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("이 이름으로 보내기") }
                    OutlinedButton(onClick = { onOpenCompany(v.page.id) }, modifier = Modifier.fillMaxWidth()) { Text("대표 판 보기") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("취소") }
                }
            }
        }
        is SendGuard.Differ -> GuardDialog(title = "대표 판과 다른 점 ${v.rows.size}가지", onDismiss = onDismiss) {
            GuardText("${place(v.page.name, v.lead.dept)} 대표 판과 근무 모양은 같고 값이 달라요. " +
                if (v.draft != null) "달라진 점만 제안하면 같은 부서 분들이 맞는지 정해요." else "새 판으로 보내면 같은 부서 분들이 둘 중에 골라요.")
            v.rows.take(MAX_ROWS).forEach { r -> GuardLine("${r.label}: ${r.before ?: "없음"} → ${r.after ?: "없음"}") }
            if (v.rows.size > MAX_ROWS) GuardLine("외 ${v.rows.size - MAX_ROWS}가지")
            v.resent?.let { GuardLine("오늘 보낸 ${place(it.company, it.dept)} 판은 새 판으로 보내면 이것으로 바뀌어요.") }
            GuardButtons {
                if (v.draft != null) Button(onClick = { onPropose(v) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("달라진 점만 제안하기")
                }
                OutlinedButton(onClick = { onSend(f.dept) }, modifier = Modifier.fillMaxWidth()) { Text("새 판으로 보낼게요") }
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("취소") }
            }
        }
        is SendGuard.Clear -> v.resent?.let { r ->
            GuardDialog(title = "오늘 보낸 판이 있어요", onDismiss = onDismiss) {
                GuardText("오늘 ${place(r.company, r.dept)}에 보낸 판이 있어요. 새로 보내면 먼저 보낸 판은 이것으로 바뀌어요.")
                GuardLine("한 휴대폰은 한 회사에 하루 한 판만 올라가요. 같은 회사 분들이 헷갈리지 않게요.")
                GuardButtons {
                    Button(onClick = { onSend(f.dept) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("바꿔서 보내기") }
                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("취소") }
                }
            }
        } ?: onDismiss()
    }
}

/** At most this many differences are listed in the ask; the proposal sheet shows them all. */
private const val MAX_ROWS = 5

@Composable
private fun GuardDialog(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 520.dp)) {
            Column(Modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                content()
            }
        }
    }
}

@Composable
private fun GuardText(text: String) = Text(text, style = MaterialTheme.typography.bodyMedium)

@Composable
private fun GuardLine(text: String) =
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
private fun GuardButtons(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) { content() }
}

/**
 * [PLZD-03] 달라진 점만 제안하기: the 대표 opened in 다른 점 말하기 with this calendar's values already in and the form's 한마디
 * as the why; one press sends it to the 대표's talk.
 */
@Composable
fun GuardProposalSheet(v: SendGuard.Differ, calendar: String, onSent: () -> Unit, onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val holder = remember { TalkHolder(c, scope, calendar) }
    val draft = v.draft ?: return onDismiss()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val title = "${v.page.name} · ${v.lead.dept.ifEmpty { "회사 전체" }}"
    DiffEditorSheet(
        title = title, base = v.lead.text, mine = null, busy = busy, error = error, draft = draft,
        onSend = { lines, setup, why ->
            busy = true
            holder.show(v.page.id, v.lead.id, title)
            holder.send(proposalBody(SetupCompare.rows(v.lead.text, setup), why), kind = "proposal", change = lines, setupText = setup) { e ->
                busy = false
                holder.close()
                if (e == null) onSent() else error = e
            }
        },
        onDismiss = onDismiss,
    )
}

/**
 * [PLZD-01][PLZD-03] After 이 규칙 쓰기 or a proposal: what happened, and the company's 광장. [FIN130-01] A pay way that waits
 * for its 기본급 ([CompanyReportViewModel.Sending.Used.toPay]) makes the pay screen the one button ([onOpenPay]).
 */
@Composable
fun GuardDonePanel(s: CompanyReportViewModel.Sending, onDone: () -> Unit, onOpenCompany: ((String) -> Unit)?, onOpenPay: (() -> Unit)? = null) {
    val (title, body, id, company) = when (s) {
        is CompanyReportViewModel.Sending.Used -> listOf(
            "이 규칙을 쓰고 있어요",
            "${place(s.company, s.dept)} 규칙을 내 달력에 쓰고 맞아요 1명으로 셌어요. 새로 올린 것은 없어요." +
                if (s.toPay) " 급여 계산 방식은 급여 화면에서 기본급을 넣으면 들어가요." else "",
            s.companyId, s.company)
        is CompanyReportViewModel.Sending.Proposed -> listOf(
            "제안을 보냈어요",
            "${place(s.company, s.dept)} 대표 판 토론에 달라진 점만 올렸어요. 같은 부서 분들이 맞아요를 누르면 대표 판이 바뀌어요.",
            s.companyId, s.company)
        else -> return
    }
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        HeroIcon(if (s is CompanyReportViewModel.Sending.Used) Icons.Outlined.CheckCircle else Icons.Outlined.RateReview)
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (s is CompanyReportViewModel.Sending.Used && s.toPay && onOpenPay != null) {
            Button(onClick = onOpenPay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("급여 화면에서 기본급 넣기") }
            if (onOpenCompany != null && id.isNotEmpty()) OutlinedButton(onClick = { onOpenCompany(id) }, modifier = Modifier.fillMaxWidth()) { Text("‘$company’ 광장 보기") }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        } else if (onOpenCompany != null && id.isNotEmpty()) {
            Button(onClick = { onOpenCompany(id) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("‘$company’ 광장 보기") }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("확인") }
        }
    }
}
