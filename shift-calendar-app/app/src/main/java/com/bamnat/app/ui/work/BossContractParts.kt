package com.bamnat.app.ui.work

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.boss.BossContractSync
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.theme.LocalCalendarColors
import com.bamnat.core.boss.StaffContract
import com.bamnat.core.boss.StaffMember
import com.bamnat.core.boss.Workplace
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

private fun day(ms: Long) = Fmt.monthDay(Instant.ofEpochMilli(ms).atZone(ZoneId.of("Asia/Seoul")).toLocalDate())

/** 1.38 [P3-X-06] What the ledger line says of the contract: "계약서 ✓", "서명 기다림" or "계약서 없음". */
internal fun contractLabel(s: StaffMember, now: Long = Today.millis()): String = when {
    s.contract?.signed == true -> "계약서 ✓"
    s.contract?.waiting(now) == true -> "계약서 서명 기다림"
    else -> "계약서"
}

/**
 * 1.38 [P3-X-06] 근로계약서: filled from the staff row (what is missing listed first), then sent as a link the staff member signs by
 * typing their name, printed as a PDF, or marked as signed on paper. Signed, it gives the PDF of what was signed.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ContractDialog(place: Workplace, staff: StaffMember, onEditStaff: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    var sent by remember(staff.id) { mutableStateOf(staff.contract) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(false) }
    val draft = remember(place, staff) { StaffContract.of(place, staff) }
    val warn = LocalCalendarColors.current.warning
    val now = Today.millis()
    fun share(text: String) {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }
        runCatching { context.startActivity(Intent.createChooser(i, "계약서 링크 보내기")) }
    }
    fun pdf(doc: com.bamnat.core.report.ReportDoc, signed: Boolean) = scope.launch {
        runCatching { com.bamnat.app.report.ReportShare.pdf(context, doc, "근로계약서-${staff.name.ifBlank { "직원" }}${if (signed) "-서명" else ""}.pdf") }
            .onFailure { note = "PDF를 만들지 못했어요. 다시 눌러 주세요." }
    }
    // Opening it reads a waiting link once.
    LaunchedEffect(staff.id) {
        if (sent?.waiting(now) == true) runCatching { BossContractSync.pull(app, staff.id) }.onSuccess { sent = it ?: sent }
    }
    val cur = sent
    val showDraft = cur == null || fresh || (!cur.signed && !cur.waiting(now))
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (showDraft) StaffContract.title(draft) else cur!!.title) }, text = {
        Column(Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (!showDraft && cur != null) {
                if (cur.signed) {
                    Text((if (cur.paper) "종이 계약서로 받았어요 · " else "${cur.signedName ?: staff.name}님이 링크에서 서명했어요 · ") + day(cur.signedAt!!),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Hint("서명한 계약서는 이 휴대폰과 PDF에만 있어요. 직원에게 PDF를 한 부 보내 주세요(근로기준법 제17조). 시급이나 근무 시간이 바뀌면 새로 써요.")
                    // 2.0.0 [RVJ-17] 검토 BOSS-55 '직원에게 줬는지도 안 남아요': one switch (not a third 글자 단추, 조건 2); the day stays here
                    // and on the PDF, and turning it off takes a mistaken press back.
                    com.bamnat.app.ui.settings.SwitchRow(
                        "직원에게 한 부 줬어요",
                        cur.givenAt?.let { "${day(it)}에 줬어요 · 계약서 PDF에 남아요" } ?: "주고 나서 켜 두면 준 날이 계약서 PDF에 남아요",
                        checked = cur.givenAt != null, enabled = !busy,
                    ) { on ->
                        busy = true
                        scope.launch { BossContractSync.given(app, staff, given = on)?.let { sent = it }; busy = false }
                    }
                } else {
                    Text("${day(cur.sentAt)}에 보냈어요 · " + (cur.seenAt?.let { "${day(it)}에 열어 봤어요" } ?: "아직 안 열어 봤어요"),
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    cur.expiresAt?.let { Hint("${day(it)}까지 서명할 수 있어요. 서명하면 이 화면을 열 때 바로 보여요.") }
                }
                cur.items.forEachIndexed { i, it ->
                    Text("${i + 1}. ${it.title}", style = MaterialTheme.typography.labelLarge)
                    Text(it.body, style = MaterialTheme.typography.bodySmall)
                }
            } else {
                cur?.takeIf { it.signed }?.let { Hint("새로 쓴 계약서를 보내도 ${day(it.signedAt!!)}에 서명한 계약서는 '지난 계약서'로 남아요.") }
                if (draft.missing.isNotEmpty()) {
                    Text("직원 정보에 없는 것: " + draft.missing.joinToString(", "), style = MaterialTheme.typography.bodyMedium, color = warn)
                    TextButton(onClick = onEditStaff) { Text("직원 정보에서 채우기") }
                    // PC 10차(중): a contract with blanks is not sent to sign; the PDF can still be printed and filled by hand.
                    Hint("빈 것을 채우면 서명 링크를 보낼 수 있어요. 종이에 손으로 채울 거면 'PDF로 인쇄'를 써요.")
                }
                Hint("직원 정보로 채웠어요. 근로기준법 제17조가 적으라고 한 것(임금·소정근로시간·휴일·연차, 단시간은 요일별 시간)을 다 담았어요. 보내기 전에 읽어 봐 주세요.")
                draft.items.forEachIndexed { i, it ->
                    Text("${i + 1}. ${it.title}", style = MaterialTheme.typography.labelLarge)
                    Text(it.body, style = MaterialTheme.typography.bodySmall)
                }
            }
            note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                when {
                    showDraft -> {
                        OutlinedButton(enabled = !busy && app.shareApi.enabled && draft.missing.isEmpty(), onClick = {
                            busy = true
                            scope.launch {
                                runCatching { BossContractSync.send(app, place, staff) }
                                    .onSuccess { sent = it; fresh = false; note = null; share(BossContractSync.message(place, staff, it)) }
                                    .onFailure { e -> note = (e as? com.bamnat.app.share.ShareLinkException)?.friendly
                                        ?: "인터넷이 안 돼 링크를 만들지 못했어요. 연결한 뒤 다시 눌러 주세요." }
                                busy = false
                            }
                        }) { Text("서명 링크 보내기") }
                        OutlinedButton(onClick = { pdf(StaffContract.doc(place, staff, draft), false) }) { Text("PDF로 인쇄") }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch { BossContractSync.paper(app, place, staff)?.let { sent = it; fresh = false }; busy = false }
                        }) { Text("종이로 받았어요") }
                    }
                    cur!!.signed -> {
                        OutlinedButton(onClick = { pdf(StaffContract.doc(place, staff, cur), true) }) { Text("서명한 계약서 PDF") }
                        TextButton(onClick = { fresh = true }) { Text("새로 쓰기") }
                        cur.past.asReversed().forEach { p ->
                            TextButton(onClick = { pdf(StaffContract.doc(place, staff, p), true) }) { Text("지난 계약서 ${day(p.signedAt ?: p.sentAt)} PDF") }
                        }
                    }
                    else -> {
                        OutlinedButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { BossContractSync.pull(app, staff.id) }.onSuccess { sent = it ?: sent; note = if (it?.signed == true) null else "아직 서명하지 않았어요." }
                                    .onFailure { note = if (it.message == "not_found") "링크가 서버에 없어요. 기한이 지났으면 다시 보내 주세요." else "인터넷이 될 때 다시 확인해 주세요." }
                                busy = false
                            }
                        }) { Text("서명 확인") }
                        OutlinedButton(onClick = { share(BossContractSync.message(place, staff, cur)) }) { Text("링크 다시 보내기") }
                        TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                runCatching { BossContractSync.withdraw(app, staff) }.onSuccess { sent = app.phaseThree.allStaff().firstOrNull { it.id == staff.id }?.contract; note = "링크를 거뒀어요." }
                                    .onFailure { note = "인터넷이 안 돼 거두지 못했어요." }
                                busy = false
                            }
                        }) { Text("거두기") }
                    }
                }
            }
            Hint("링크에는 계약 내용만 가고, 직원이 쓴 이름과 서명한 시각만 돌아와요. 서명을 받으면 서버에서 바로 지워요(길어도 ${BossContractSync.DAYS}일).")
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("닫기") } })
}
