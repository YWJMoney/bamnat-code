package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.company.PayWay
import com.bamnat.core.plaza.ProposalDraft
import com.bamnat.core.plaza.SetupCompare

/*
 * [ITEM-07][ITEM-08] 1.30 묶음 BC (사용자 2026-10-27 "통상임금과 근로시수는 광장에 제공된 정보가 있으면 해당 정보를 따르게 하고 다르다면
 * 적용하는 사람이 수정할수 있게하고 수정한다면 해당 내용을 의견으로 제시해서 사람들이 볼수 있게"): the 통상시급 hours a month the
 * company's way says (LG화학 180시간), shown as the company's, and a change of them sent as a ready proposal on that way. The
 * 기본급 screen (묶음 BA, BaseFields·PayWayCard) and 회사 규칙 (CompanyRulesEditor) use the same pieces.
 */

/** [ITEM-07] The company's way the pay uses, as the 광장 has it: the way, its file, and the hours it says. */
data class CompanyWayText(val way: PayWay, val text: String) {
    val hours: Int? get() = com.bamnat.app.data.CompanyShare.hoursIn(text)
    val title: String get() = "${way.company} · ${way.dept.ifEmpty { "회사 전체" }}"
}

/**
 * [ITEM-07] The company's way of this phone's pay, read once from the server (null while reading, offline, or with no way).
 * Never on the main thread: the read is the client's own.
 */
@Composable
fun rememberCompanyWayText(): CompanyWayText? {
    val c = LocalContext.current.appContainer
    // [CALI-04] The calendar on screen's own way.
    val payWays = companyScope(c).payWays
    val way = remember(payWays) { runCatching { payWays.way.value }.getOrNull() }
    val text by produceState<CompanyWayText?>(null, way) {
        val w = way ?: return@produceState
        value = runCatching { c.companyApi.company(w.companyId).setups.firstOrNull { it.id == w.setup }?.text }.getOrNull()
            ?.let { CompanyWayText(w, it) }
    }
    return text
}

/**
 * [ITEM-07] The hours chips with the company's first ("회사 규칙 180시간"), and, when the user's [hours] differ from the company's,
 * the line that sends them as a proposal ([HoursProposalSheet]). [onPick] sets the user's own hours.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CompanyHoursLine(way: CompanyWayText?, hours: Int, onPick: (Int) -> Unit) {
    val company = way?.hours ?: return
    var proposing by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bamnat.app.ui.components.FilterChip(selected = hours == company, onClick = { onPick(company) },
                label = { Text("회사 규칙 ${company}시간") }, modifier = Modifier.heightIn(min = 48.dp))
        }
        if (hours == company) {
            Text("‘${way.way.company}’ 분들이 맞춘 값이에요. 명세서와 다르면 고쳐 주세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("회사 규칙은 ${company}시간이에요. ${hours}시간이 맞다면 같은 회사 분들께 의견으로 알려 주세요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { proposing = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("${hours}시간으로 의견 보내기") }
        }
    }
    if (proposing) HoursProposalSheet(way, hours) { proposing = false }
}

/**
 * [ITEM-08] 고쳐서 제안하기 opened on the company's way with its 통상시급 hours already [hours] and the reason written: one press
 * sends it to the way's talk, where coworkers see it and agree. Nothing changes on the phone (the user's hours are already theirs).
 */
@Composable
fun HoursProposalSheet(way: CompanyWayText, hours: Int, from: String = "명세서", onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val holder = remember { TalkHolder(c, scope) }
    val draft = remember(way.text, hours) { ProposalDraft.hours(way.text, hours, from) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    if (draft == null) {
        onDismiss()
        return
    }
    DiffEditorSheet(
        title = way.title,
        base = way.text,
        mine = null,
        busy = busy,
        error = error,
        draft = draft,
        onSend = { lines, setup, why ->
            busy = true
            holder.show(way.way.companyId, way.way.setup, way.title)
            holder.send(proposalBody(SetupCompare.rows(way.text, setup), why), kind = "proposal", change = lines, setupText = setup) { e ->
                busy = false
                holder.close()
                if (e == null) {
                    c.telemetry.event("plaza_diff", "hours")
                    onDismiss()
                } else error = e
            }
        },
        onDismiss = onDismiss,
    )
}

/** [ITEM-07] Paddings the hours line sits in on a settings screen. */
internal val HoursLinePadding = Modifier.padding(top = 4.dp)
