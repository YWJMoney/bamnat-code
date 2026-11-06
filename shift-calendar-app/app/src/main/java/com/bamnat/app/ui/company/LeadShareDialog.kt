package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.LeadShare
import com.bamnat.app.company.LeadShare.Step

/**
 * [PLAZA-14] 1.27 묶음 AH (사용자 2026-10-25): after a cycle is picked from a company search, one question —
 * "이 정보를 다른 사람이 사용해도 될까요?" — and then what it became: the company's 대표 주기, one more 맞아요 for the same
 * order, or (when another order leads) the 사업장 it is for. One question per dialog; no ad on it.
 */
@Composable
fun LeadShareHost(c: AppContainer, onOpen: (String) -> Unit) {
    val step by c.leadShare.step.collectAsStateWithLifecycle()
    step?.let { LeadShareDialog(it, c.leadShare, onOpen) }
}

@Composable
fun LeadShareDialog(step: Step, lead: LeadShare, onOpen: (String) -> Unit) {
    val company = step.pick.company
    var site by rememberSaveable(step.pick) { mutableStateOf("") }
    val open = { id: String? -> lead.close(); id?.let(onOpen) }
    AlertDialog(
        // [SETUP-16] 1.37 PC 122d18a0·b98f9d1e (뒤로 닫으면 무엇을 고른 건지 몰라요; 알림은 바로 뜨는 급여 시트에 가려요): the question is
        // answered with one of its two buttons ('내 달력에만 쓸게요' is the visible way out); back and a tap outside leave it open.
        onDismissRequest = { if (step !is Step.Ask && step !is Step.Working) lead.close() },
        title = {
            Text(
                when (step) {
                    is Step.Ask -> "이 정보를 다른 사람이 사용해도 될까요?"
                    is Step.Working -> "나누는 중이에요"
                    is Step.Made -> "이제 ‘${step.name}’의 대표 주기가 됐어요!"
                    is Step.Agreed -> "같은 주기가 이미 있어요"
                    is Step.NeedSite -> "어느 사업장에서 일하세요?"
                    is Step.Waiting -> "나눴어요"
                    is Step.Failed -> "나누지 못했어요"
                },
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).semantics { liveRegion = LiveRegionMode.Polite },
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                when (step) {
                    is Step.Ask -> {
                        Text("‘$company’에서 일하는 다른 분이 이 근무주기를 찾으면 바로 쓸 수 있어요.", style = MaterialTheme.typography.bodyLarge)
                        Text("근무 이름·시간·순서만 나눠요. 급여 금액, 이름, 연락처, 사진은 보내지 않아요.",
                            style = MaterialTheme.typography.bodyMedium)
                        Text("한 번 정하면 다시 묻지 않아요. 설정 › 개인정보·앱 정보에서 바꿀 수 있어요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is Step.Working -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("‘$company’ 광장에 보내고 있어요")
                    }
                    is Step.Made -> {
                        Text("회사 찾기에서 ‘${step.name}’을 찾는 분께 바로 맨 위에 보여요. 맞아요가 쌓이면 더 믿을 수 있게 돼요.",
                            style = MaterialTheme.typography.bodyLarge)
                        if (step.pick.firstGiven) PublicLaterLine()
                    }
                    is Step.Agreed -> Text("‘${step.name}’의 대표 주기와 같아서, 새로 올리지 않고 맞아요 한 표를 더했어요.",
                        style = MaterialTheme.typography.bodyLarge)
                    is Step.NeedSite -> {
                        Text("‘${step.name}’에는 다른 순서(${step.lead})가 대표예요. 공장·사업장·부서 이름을 알려 주시면 그곳의 대표 주기로 나눠요.",
                            style = MaterialTheme.typography.bodyLarge)
                        OutlinedTextField(
                            value = site, onValueChange = { site = it.take(40) }, singleLine = true,
                            label = { Text("사업장 이름 (예: 여수공장)") },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { if (site.isNotBlank()) lead.site(site) }),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    is Step.Waiting -> {
                        Text("‘${step.name}’에는 다른 순서가 대표예요. 같은 곳 분들이 맞아요를 누르면 이 주기가 대표가 돼요.",
                            style = MaterialTheme.typography.bodyLarge)
                        if (step.pick.firstGiven) PublicLaterLine()
                    }
                    is Step.Failed -> Text(step.message, style = MaterialTheme.typography.bodyLarge)
                }
            }
        },
        confirmButton = {
            when (step) {
                is Step.Ask -> TextButton(onClick = lead::yes) { Text("같이 쓸게요") }
                is Step.Working -> Unit
                is Step.Made -> TextButton(onClick = { open(step.companyId) }) { Text("광장 보기") }
                is Step.Agreed -> TextButton(onClick = { open(step.companyId) }) { Text("광장 보기") }
                is Step.Waiting -> TextButton(onClick = { open(step.companyId) }) { Text("광장 보기") }
                is Step.NeedSite -> TextButton(onClick = { lead.site(site) }, enabled = site.isNotBlank()) { Text("이 사업장으로 나누기") }
                is Step.Failed -> TextButton(onClick = { if (step.site != null) lead.site(step.site) else lead.retry() }) { Text("다시 해 보기") }
            }
        },
        dismissButton = {
            when (step) {
                // 1.38 통합 8차: a table put up from '우리 회사 근무표 올리기' may not be in the user's calendar at all.
                is Step.Ask -> TextButton(onClick = lead::no) { Text(if (step.pick.firstGiven) "올리지 않을게요" else "내 달력에만 쓸게요") }
                is Step.Working -> Unit
                is Step.NeedSite -> TextButton(onClick = lead::close) { Text("나누지 않을래요") }
                else -> TextButton(onClick = lead::close) { Text("닫기") }
            }
        },
    )
}

/**
 * 1.38 통합 8차 [CYC-03] ('대표 주기가 됐어요 … 맨 위에 보여요'가 공개 목록에도 바로 보이는 것처럼 읽혀요): coworkers see it now;
 * the public list (공개 자료) takes it only after the review.
 */
@Composable
private fun PublicLaterLine() {
    Text("공개 근무주기 목록에는 검토 뒤(최대 7일)에 올라가요.", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}
