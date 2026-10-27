package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.padding
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
 * [ITEM-07] The company's way of this phone's pay (null while reading, offline, or with no way). [FIN130-05] The company's
 * page kept on the phone first ([com.bamnat.app.company.PageCache]), read from the server only when it is not there (and kept
 * then), so the pay screens do not ask the server each time they open. Never on the main thread: the read is the client's own.
 */
@Composable
fun rememberCompanyWayText(): CompanyWayText? {
    val c = LocalContext.current.appContainer
    // [CALI-04] The calendar on screen's own way.
    val payWays = companyScope(c).payWays
    val way = remember(payWays) { runCatching { payWays.way.value }.getOrNull() }
    val text by produceState<CompanyWayText?>(null, way) {
        val w = way ?: return@produceState
        value = companyWayText(c, w)
    }
    return text
}

/** [FIN130-05] [way]'s file as the 광장 has it, from the page kept on the phone when there ([keptSetupText]). */
internal suspend fun companyWayText(c: com.bamnat.app.AppContainer, way: PayWay): CompanyWayText? =
    keptSetupText(c, way.companyId, way.setup)?.let { CompanyWayText(way, it) }

/**
 * [FIN130-05] Setup [setup] of [companyId] as the 광장 has it: the page kept on the phone first; else read once and kept, so the
 * next screen has it. Null offline, or when the version is not on the page; a kept page whose pay this phone was not shown
 * (read before it joined) is read again.
 */
internal suspend fun keptSetupText(c: com.bamnat.app.AppContainer, companyId: String, setup: String): String? {
    c.companySearch.page(companyId)?.setups?.firstOrNull { it.id == setup && !it.payHidden }?.let { return it.text }
    val page = runCatching { c.companyApi.company(companyId) }.getOrNull() ?: return null
    c.companySearch.keepPage(page)
    return page.setups.firstOrNull { it.id == setup }?.text
}

/**
 * [ITEM-07] The hours chips with the company's first ("회사 규칙 180시간"), and, when the user's [hours] differ from the company's,
 * the line that sends them as a proposal ([HoursProposalSheet]). [onPick] sets the user's own hours.
 * [FIN130-09] One set of chips, 묶음 BA's [com.bamnat.app.ui.pay.MonthlyHoursChips], with [choices] after the company's (1.30 had
 * three: this line, the screen's own chips and BA's); without a company way it is those chips alone.
 */
@Composable
fun CompanyHoursLine(way: CompanyWayText?, hours: Int, choices: List<Int> = com.bamnat.core.pay.BaseAsk.HOURS_CHOICES, onPick: (Int) -> Unit) {
    val company = way?.hours
    var proposing by remember { mutableStateOf<Int?>(null) }
    com.bamnat.app.ui.pay.MonthlyHoursChips(hours, onHours = { h -> onPick(h ?: company ?: hours) }, companyHours = company,
        companyName = way?.way?.company, choices = choices, onPropose = if (way != null && company != null) ({ proposing = it }) else null)
    val p = proposing
    if (p != null && way != null) HoursProposalSheet(way, p) { proposing = null }
}

/**
 * [ITEM-08] 고쳐서 제안하기 opened on the company's way with its 통상시급 hours already [hours] and the reason written: one press
 * sends it to the way's talk, where coworkers see it and agree. Nothing changes on the phone (the user's hours are already theirs).
 */
@Composable
fun HoursProposalSheet(way: CompanyWayText, hours: Int, from: String = "명세서", onDismiss: () -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    // [FIN130-08] The calendar on screen's: a proposal that carries a setup carries this calendar's.
    val calendar = LocalCompanyCalendar.current
    val holder = remember(calendar) { TalkHolder(c, scope, calendar) }
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
