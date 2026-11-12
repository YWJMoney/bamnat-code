package com.bamnat.app.ui.work

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.bamnat.app.ui.components.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.boss.BossContractSync
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.AlertDialog
import com.bamnat.app.ui.components.Button
import com.bamnat.app.ui.components.ToneLine
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
 *
 * 2.0.0 [RVJ-24] 검토 BOSS-53·U05c-04, 총괄 #448 사진 24 (조건 1·2·9·11): a sheet now, not a 가운데 창. It is a long read with
 * things to do (부품 표: 긴 내용은 아래 시트), and in the 창 the 조항 were cut in half above '닫기' while its buttons sat under all
 * eight 조항. What it is and what to do next come first, the 조항 after; the X is the sheet's own. '종이로 받았어요' asks the day
 * the paper came back (and says what is blank) before it marks anything, and '종이 표시 취소' takes a mistaken press back.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
internal fun ContractSheet(place: Workplace, staff: StaffMember, onEditStaff: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.appContainer
    val scope = rememberCoroutineScope()
    var sent by remember(staff.id) { mutableStateOf(staff.contract) }
    var busy by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var fresh by remember { mutableStateOf(false) }
    var paperAsk by rememberSaveable { mutableStateOf(false) }
    val draft = remember(place, staff) { StaffContract.of(place, staff) }
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
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(if (showDraft) StaffContract.title(draft) else cur!!.title, style = MaterialTheme.typography.titleLarge)
            // ---- What it is now ----
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
            } else {
                cur?.takeIf { it.signed }?.let { Hint("새로 쓴 계약서를 보내도 ${day(it.signedAt!!)}에 서명한 계약서는 '지난 계약서'로 남아요.") }
                // PC 10차(중): a contract with blanks is not sent to sign; the PDF can still be printed and filled by hand.
                if (draft.missing.isNotEmpty()) ToneLine("직원 정보에 없는 것: " + draft.missing.joinToString(", ") +
                    ". 채우면 서명 링크를 보낼 수 있어요.", style = MaterialTheme.typography.bodyMedium)
                else Hint("직원 정보로 채웠어요. 근로기준법 제17조가 적으라고 한 것(임금·소정근로시간·휴일·연차, 단시간은 요일별 시간)을 다 담았어요. 보내기 전에 읽어 봐 주세요.")
            }
            note?.let { ToneLine(it) }
            // ---- What to do next: one 채운 단추 for the step that moves it on, the rest lighter (조건 2) ----
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when {
                    showDraft -> {
                        // The one 채운 단추 is the step that moves it on now: with blanks that is filling them (a 서명 링크 단추 that
                        // cannot be pressed was the biggest thing in the sheet), without them it is sending the link.
                        val blanks = draft.missing.isNotEmpty()
                        Button(enabled = !busy && (blanks || app.shareApi.enabled), onClick = {
                            if (blanks) onEditStaff() else {
                                busy = true
                                scope.launch {
                                    runCatching { BossContractSync.send(app, place, staff) }
                                        .onSuccess { sent = it; fresh = false; note = null; share(BossContractSync.message(place, staff, it)) }
                                        .onFailure { e -> note = (e as? com.bamnat.app.share.ShareLinkException)?.friendly
                                            ?: "인터넷이 안 돼 링크를 만들지 못했어요. 연결한 뒤 다시 눌러 주세요." }
                                    busy = false
                                }
                            }
                        }) { Text(if (blanks) "직원 정보에서 채우기" else "서명 링크 보내기") }
                        OutlinedButton(onClick = { pdf(StaffContract.doc(place, staff, draft), false) }) { Text("PDF로 인쇄") }
                        TextButton(enabled = !busy, onClick = { paperAsk = true }) { Text(PAPER) }
                    }
                    cur!!.signed -> {
                        OutlinedButton(onClick = { pdf(StaffContract.doc(place, staff, cur), true) }) { Text("서명한 계약서 PDF") }
                        TextButton(onClick = { fresh = true }) { Text("새로 쓰기") }
                        if (cur.paper) TextButton(enabled = !busy, onClick = {
                            busy = true
                            scope.launch {
                                sent = BossContractSync.unpaper(app, staff)
                                note = "종이로 받았다는 표시를 지웠어요. 다시 받으면 '$PAPER'를 눌러 주세요."
                                busy = false
                            }
                        }) { Text(PAPER_UNDO) }
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
            // ---- The 조항, as they were sent or as they would go ----
            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            (if (!showDraft && cur != null) cur.items else draft.items).forEachIndexed { i, it ->
                Text("${i + 1}. ${it.title}", style = MaterialTheme.typography.labelLarge)
                Text(it.body, style = MaterialTheme.typography.bodySmall)
            }
            // The signed ones a newer contract replaced: each a line to press, not one more 글자 단추 in the row above.
            cur?.takeIf { !showDraft && it.signed && it.past.isNotEmpty() }?.let { c ->
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Text("지난 계약서", style = MaterialTheme.typography.labelLarge)
                c.past.asReversed().forEach { p ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { pdf(StaffContract.doc(place, staff, p), true) },
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Description, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(12.dp))
                        Text("지난 계약서 ${day(p.signedAt ?: p.sentAt)} PDF", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Hint("링크에는 계약 내용만 가고, 직원이 쓴 이름과 서명한 시각만 돌아와요. 서명을 받으면 서버에서 바로 지워요(길어도 ${BossContractSync.DAYS}일).")
        }
    }
    if (paperAsk) PaperAsk(draft.missing, onDismiss = { paperAsk = false }) { on ->
        paperAsk = false
        busy = true
        scope.launch { BossContractSync.paper(app, place, staff, on)?.let { sent = it; fresh = false; note = null }; busy = false }
    }
}

internal const val PAPER = "종이로 받았어요"
internal const val PAPER_UNDO = "종이 표시 취소"

/**
 * 2.0.0 [RVJ-24] 검토 BOSS-53: before a contract is marked signed on paper, the day the paper came back (오늘 unless changed, never
 * a day still to come) and, when the staff row has blanks, a line asking that the paper has them. One press marked it before,
 * blanks and all, and nothing took it back.
 */
@Composable
private fun PaperAsk(missing: List<String>, onDismiss: () -> Unit, onConfirm: (java.time.LocalDate) -> Unit) {
    var on by rememberSaveable { mutableStateOf(Today.date()) }
    var late by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("종이 계약서를 받았나요?") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (missing.isNotEmpty()) ToneLine("직원 정보에 빈 것이 있어요: " + missing.joinToString(", ") + ". 종이에는 다 적혀 있는지 봐 주세요.",
                style = MaterialTheme.typography.bodyMedium)
            DateField("받은 날", on, { d -> if (d != null) { late = d.isAfter(Today.date()); if (!late) on = d } }, clearable = false)
            if (late) ToneLine("아직 오지 않은 날은 고를 수 없어요.")
            Hint("받은 날이 서명한 날로 계약서 PDF에 남아요. 잘못 눌렀으면 '$PAPER_UNDO'로 되돌려요.")
        }
    }, confirmButton = { TextButton(onClick = { onConfirm(on) }) { Text("받았어요") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } })
}
