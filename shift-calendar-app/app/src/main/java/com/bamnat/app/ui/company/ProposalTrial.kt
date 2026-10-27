package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.ui.pay.hourlyWeek
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.YearMonth

/*
 * [PROP-02] 1.30 묶음 BC (사용자 2026-10-27 "제안이 채택전 사람들이 해당제안이 맞는지 검즘할수 있게 이제안을 적용해서 내 월급과
 * 비교해보기"): a proposal post in the talk carries its setup (server talk(), PROP-01), so a member works out their own month with
 * it before saying 맞아요. All on the phone (결정 D36). The month is the one matched with a payslip when there is one, so the
 * sheet can say which comes closer to what was paid.
 */

/**
 * '이 제안으로 내 월급 계산해 보기' under a proposal post, and its sheet. [company] and [setup] are the talk's (the version the
 * proposal changes); [proposal] the proposal's setup. [onVote] is the post's 공감·비공감 (TalkHolder.vote), which adopts it.
 */
@Composable
internal fun ProposalTrialButton(company: String, setup: String, proposal: String, onVote: (Boolean) -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    var trial by remember { mutableStateOf<Trial?>(null) }
    val slip = remember { c.payStore.profile.value?.verified }
    OutlinedButton(
        onClick = {
            val month = slip?.month ?: Today.month()
            trial = Trial(proposal, month, loading = true)
            c.telemetry.event("plaza_diff", "proposal_trial")
            scope.launch { trial = proposalTrial(c, company, setup, proposal, month) }
        },
        modifier = Modifier.padding(top = 4.dp).heightIn(min = 48.dp),
    ) { Text("이 제안으로 내 월급 계산해 보기") }
    TrialSheet(trial, onClose = { trial = null }, onVote = onVote, proposal = true, slip = slip)
}

/**
 * [PROP-02] [month] worked out with the user's own setup and with [proposal]'s, its allowances as the proposal has them against
 * the version [setup] of [company] it changes (read once; without it, only what the proposal adds or changes counts).
 */
internal suspend fun proposalTrial(c: AppContainer, company: String, setup: String, proposal: String, month: YearMonth): Trial {
    val mine = c.payStore.profile.value ?: return Trial(proposal, month, note = "내 급여 설정이 없어 비교할 수 없어요. 급여 설정을 먼저 해 주세요")
    val base = runCatching { c.companyApi.company(company).setups.firstOrNull { it.id == setup }?.text }.getOrNull()
    val tried = runCatching { c.companyShare.trial(proposal, base) }.getOrNull()
        ?: return Trial(proposal, month, note = "이 제안에는 급여 계산 방식이 없어 근무표만 비교할 수 있어요")
    val snapshot = c.scheduleRepository.snapshot.first()
    val settings = c.settingsRepository.current
    val calc = com.bamnat.core.pay.PayCalculator()
    return runCatching {
        val a = calc.calculate(mine.hourlyWeek(settings), month, snapshot.resolver::resolve, checks = false)
        val b = calc.calculate(tried.hourlyWeek(settings), month, snapshot.resolver::resolve, checks = false)
        val nowBy = a.lines.groupBy { it.label }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val thenBy = b.lines.groupBy { it.label }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val labels = (nowBy.keys + thenBy.keys).toList()
        Trial(proposal, month, now = a.total, then = b.total,
            lines = labels.map { TrialLine(it, nowBy[it] ?: 0, thenBy[it] ?: 0) }.sortedByDescending { kotlin.math.abs(it.then - it.now) })
    }.getOrElse { Trial(proposal, month, note = "이 달은 계산하지 못했어요") }
}
