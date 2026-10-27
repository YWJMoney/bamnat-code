package com.bamnat.app.ui.company

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyText
import com.bamnat.app.company.LeadLine
import com.bamnat.app.company.CompanySearch
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** [SPAT-07] AH-D1: a pause in typing before asking, and no more than this for the company server's answer. */
internal const val INLINE_PAUSE_MS = 300L
internal const val INLINE_WAIT_MS = 2_500L

/**
 * [SPAT-07][PLAZA-17] The company server's companies for [q]; nothing found, then once more by the other spelling of the name
 * (GS ↔ 지에스, AH-I1). Null when it did not answer.
 *
 * AS 1.29 [SRCH-07]: through [com.bamnat.app.company.CompanySearch]: a fresh answer for the same name comes at once, one
 * request for the name however many screens ask, and the answer is kept for the next letters. When the server is still
 * not back just before [INLINE_WAIT_MS] and the phone already knows companies that fit (the most used ones, an answer
 * for fewer letters), those come back instead of nothing, so a caller with that time limit (급여 방식 찾기) shows them.
 */
internal suspend fun inlineSearch(c: AppContainer, q: String): List<CompanyHit>? = try {
    val s = c.companySearch
    val known = s.peek(q)
    if (known.exact && known.age < com.bamnat.app.company.CompanySearch.FRESH_MS) known.hits
    else {
        val job = s.ask(q)
        withTimeoutOrNull(INLINE_WAIT_MS - INLINE_KNOWN_EARLY_MS) { job.await() }
            ?: s.peek(q).hits.takeIf { it.isNotEmpty() }
            ?: job.await()
    }
} catch (e: CompanyException) {
    null
}

/** [SRCH-07] How long before [INLINE_WAIT_MS] [inlineSearch] gives what the phone knows instead of waiting on. */
internal const val INLINE_KNOWN_EARLY_MS = 300L

/**
 * [SPAT-07] 1.27 AH 순번 436 (사용자 2026-10-25): what is already there for the company typed, right under the name box,
 * without a 찾기 button. In this order: the companies coworkers set up (대표 주기, n명 쓰고 있어요, 급여 방식 있음), then the
 * public rows, then 여기 없어요, 직접 추가 at the bottom (처음 제공: the form, then its usual orders). Two letters at least.
 * The public rows come at once from the phone; the server only after a pause in typing, and never holds them up.
 *
 * [onServer] opens a company's page; [onPick] (첫 설정·새 달력) takes a public or provided cycle with its order instead of
 * putting it on a calendar, which otherwise happens on its own sheet ([calendar]), then [onPay] or [onShare].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyInlineResults(
    query: String,
    onServer: ((CompanyHit) -> Unit)?,
    modifier: Modifier = Modifier,
    onPick: ((PublicCycle, String) -> Unit)? = null,
    onPay: () -> Unit = {},
    onShare: () -> Unit = {},
    calendar: String = LocalCompanyCalendar.current,
    limit: Int = 5,
) {
    val c = LocalContext.current.appContainer
    val q = query.trim()
    val ready = CompanyText.norm(q).length >= 2
    // AS 1.29 [SRCH-12] The public rows from their index, off the frame's own work when the index is not made yet.
    val publicRows by produceState(remember(q) { if (ready) PublicCycles.searchIfReady(q) else emptyList() }, q) {
        if (ready && value.isEmpty()) value = withContext(Dispatchers.Default) { PublicCycles.search(q) }
    }
    val serverOn = onServer != null && c.companyApi.enabled
    // AS 1.29 [SRCH-06][SRCH-07][SRCH-10] What the phone has at once, the server's answer added whenever it comes (a late one
    // is no longer thrown away, 사용자 2026-10-27 "늦게 떠서 안 뜨는 것처럼 보인다").
    val server by produceState(
        if (ready && serverOn) CompanySearch.State(q, c.companySearch.peek(q).hits, CompanySearch.Stage.ASKING) else CompanySearch.State(q, emptyList(), CompanySearch.Stage.IDLE),
        q, serverOn,
    ) {
        if (!ready || !serverOn) { value = CompanySearch.State(q, emptyList(), CompanySearch.Stage.IDLE); return@produceState }
        c.companySearch.states(q, INLINE_PAUSE_MS).collect { value = it }
    }
    var open by remember { mutableStateOf<PublicCycle?>(null) }
    var providing by remember { mutableStateOf(false) }
    if (!ready) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val hits = server.hits
        SearchStageLine(server, nothingYet = hits.isEmpty() && publicRows.isEmpty())
        if (hits.isNotEmpty() || publicRows.isNotEmpty()) {
            Text("찾은 곳 · $q", style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() })
        }
        hits.take(limit).forEach { hit -> InlineCompanyRow(hit) { onServer?.invoke(hit) } }
        publicRows.take(limit).forEach { row -> InlinePublicRow(row) { open = row } }
        if (publicRows.size > limit) {
            Text("공개 자료 ${publicRows.size - limit}곳 더 있어요. 아래 ‘더 찾기’에서 볼 수 있어요.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // [SPAT-07] Last, under every company found: the user's own company is not there.
        OutlinedButton(onClick = { providing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Icon(Icons.Filled.Add, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(if (hits.isEmpty() && publicRows.isEmpty()) "여기 없어요, ‘$q’ 직접 추가" else "여기 없어요, 직접 추가", maxLines = 2,
                overflow = TextOverflow.Ellipsis)
        }
    }
    open?.let { row ->
        PublicApplySheet(row, onDismiss = { open = null }, onPay = { open = null; onPay() }, onShare = { open = null; onShare() },
            pick = onPick?.let { p -> { codes: String -> open = null; p(row, codes) } }, calendar = calendar)
    }
    if (providing) {
        ModalBottomSheet(onDismissRequest = { providing = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
                FirstProviderCard(q, rewardShort(cachedTerms()), onStart = { form ->
                    providing = false
                    open = PublicCycles.provided(q, form)
                }, plaza = c.companyApi.enabled)
            }
        }
    }
}

/**
 * [SRCH-10] 1.29 AS: where the coworkers' answer stands, in one small line under the name box: asking (with two placeholder
 * rows when nothing is on screen yet), taking long but still coming, or not reachable. Nothing when it came.
 */
@Composable
internal fun SearchStageLine(state: CompanySearch.State, nothingYet: Boolean) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    when (state.stage) {
        CompanySearch.Stage.ASKING -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text(if (nothingYet) "같은 회사 분들 자료를 찾고 있어요" else "같은 회사 분들 자료를 더 찾고 있어요", style = MaterialTheme.typography.bodySmall, color = muted)
            if (nothingYet) repeat(2) { SkeletonRow() }
        }
        CompanySearch.Stage.SLOW -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("연결이 느려 조금 오래 걸려요. 찾으면 바로 여기에 보여 드려요.", style = MaterialTheme.typography.bodySmall, color = muted)
        }
        CompanySearch.Stage.FAILED -> Text(
            if (state.code == "offline") "같은 회사 분들 자료는 지금 못 불러왔어요. 인터넷 연결을 확인해 주세요. 공개 자료부터 보여 드려요."
            else "같은 회사 분들 자료는 지금 못 불러왔어요. 공개 자료부터 보여 드려요.",
            style = MaterialTheme.typography.bodySmall, color = muted,
        )
        else -> Unit
    }
}

/** [SRCH-10] A row's place while its company is on the way: the same height as a company row, no words. */
@Composable
internal fun SkeletonRow() {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth().heightIn(min = 62.dp).clearAndSetSemantics { }) {}
}

/** [SPAT-07][PLAZA-14] One company of the server: its name, its 대표 주기 and who stands behind it (AH-I4). */
@Composable
internal fun InlineCompanyRow(hit: CompanyHit, onClick: () -> Unit) {
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(38.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(hit.name.trim().removePrefix("(주)").trim().take(1), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(hit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                leadText(hit.lead)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Text(trustText(hit), style = MaterialTheme.typography.bodySmall,
                    color = if (hit.lead?.doubted == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/** "대표 주기 주주휴휴야야휴휴" (a 사업장's with its name), from the search's line. */
internal fun leadText(l: LeadLine?): String? {
    if (l == null) return null
    val days = l.steps ?: l.weekly
    val said = when {
        l.roster -> "매달 근무표"
        days == null -> return null
        else -> days.joinToString("") { d -> if (d == null || d in l.offs) "휴" else d.take(1) }
    }
    return "대표 주기 " + said + if (l.dept.isNotEmpty()) " · ${l.dept}" else ""
}

/** AH-I4: "3명 쓰고 있어요 · 맞아요 2명 · 급여 방식 있음", or that too many said it is wrong. */
internal fun trustText(hit: CompanyHit): String = listOfNotNull(
    hit.users.takeIf { it > 0 }?.let { "${it}명 쓰고 있어요" },
    hit.lead?.takeIf { it.doubted }?.let { "달라요가 많아요" } ?: hit.lead?.ok?.takeIf { it > 0 }?.let { "맞아요 ${it}명" },
    "급여 방식 있음".takeIf { hit.lead?.pay == true },
    hit.sites.takeIf { it > 1 }?.let { "사업장 ${it}곳" },
).ifEmpty { listOf(if (hit.adopted > 0) "쓰는 설정 ${hit.adopted}개" else "확인 중 ${hit.pending}개") }.joinToString(" · ")

/** [SPAT-07] One public row, small: company · 사업장, its order, the badge. */
@Composable
private fun InlinePublicRow(row: PublicCycle, onClick: () -> Unit) {
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(listOf(row.company, row.site).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.titleSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(orderLine(row), style = MaterialTheme.typography.bodyMedium)
                StatusChip(PublicCycles.BADGE + " · " + row.trustLine, if (row.weak) Tone.WAIT else Tone.QUIET)
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}
