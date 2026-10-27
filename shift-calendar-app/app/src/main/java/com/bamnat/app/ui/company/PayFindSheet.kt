package com.bamnat.app.ui.company

import android.os.SystemClock
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.warmCompanies
import com.bamnat.app.appContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.PayFind
import com.bamnat.app.company.PendingWay
import com.bamnat.app.company.cancelPending
import com.bamnat.app.company.commit
import com.bamnat.app.company.pendingWays
import com.bamnat.app.ui.pay.BaseFields
import com.bamnat.app.ui.pay.MoneyField
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.plaza.PayFindRows
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.time.Today
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * [PAYF-02] 1.29 묶음 AP: the company server's answers for the pay box, kept for a few minutes and asked for before the box is
 * even seen (화면을 열 때 미리). 1.27's box asked again on every letter, waited 2.5 seconds and then said "급여 방식이 없어요" for
 * an answer that was only late (사용자 "안 뜨는 게 아니라 늦게 떠서 안 뜨는 것처럼 보인다"). Worker AS speeds up the client and the
 * server underneath; this only keeps what came back, in memory, for this run of the app.
 */
internal object PayFindSearch {
    private const val KEEP_MS = 5 * 60_000L
    private const val MAX = 32
    private val kept = object : LinkedHashMap<String, Pair<Long, List<CompanyHit>>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, List<CompanyHit>>>?): Boolean = size > MAX
    }

    private fun key(q: String) = com.bamnat.core.plaza.PublicCycles.norm(q)

    /** What the server said for [q] in the last five minutes, or null. */
    fun cached(q: String): List<CompanyHit>? = synchronized(kept) {
        kept[key(q)]?.takeIf { SystemClock.elapsedRealtime() - it.first < KEEP_MS }?.second
    }

    /** The server's companies for [q], from memory when it answered lately; null when it did not answer. */
    suspend fun server(c: AppContainer, q: String): List<CompanyHit>? {
        cached(q)?.let { return it }
        if (!c.companyApi.enabled || !PayFindRows.ready(q)) return null
        // [SRCH-07] Through the one company search (shared answers, one request at a time), waiting for the server's own
        // answer: inlineSearch's early 'what the phone knows' would be kept here for five minutes as if the server said it.
        val s = c.companySearch
        // [SPD-11][SPD-12] 1.30 AP1: the whole list of companies read now if not yet, and a fresh one found it without asking.
        c.warmCompanies()
        val known = s.peek(q)
        val hits = if ((known.exact && known.age < com.bamnat.app.company.CompanySearch.FRESH_MS) || known.enough) known.hits
        else try { s.ask(q).await() } catch (e: com.bamnat.app.company.CompanyException) { return null }
        synchronized(kept) { kept[key(q)] = SystemClock.elapsedRealtime() to hits }
        return hits
    }

    /** Asks for [q] now, so the rows are there when the box opens with it (the company the user already named). */
    fun prefetch(c: AppContainer, scope: CoroutineScope, q: String) {
        if (!c.companyApi.enabled || !PayFindRows.ready(q) || cached(q) != null) return
        scope.launch { server(c, q) }
    }

    /** For tests: nothing kept. */
    fun clear() = synchronized(kept) { kept.clear() }

    fun rows(hits: List<CompanyHit>): List<PayFindRows.Server> = hits.map { h ->
        val l = h.lead
        // [PAYF-08] The main 대표 주기's own pay way, else another 사업장's the server names.
        val other = l?.payWay?.takeIf { !l.pay }
        if (other != null) PayFindRows.Server(h.id, h.name, h.alias, h.users, true, other.ok, other.dept, false, other.setup)
        else PayFindRows.Server(h.id, h.name, h.alias, h.users, l?.pay == true, l?.ok ?: 0, l?.dept.orEmpty(), l?.doubted == true, l?.setup)
    }
}

/** [PAYF-02] What the server said so far for the name in the box. */
private sealed interface PayServer {
    data object Off : PayServer
    data class Asking(val slow: Boolean) : PayServer
    data class Found(val hits: List<CompanyHit>) : PayServer
    data object NoAnswer : PayServer
}

/** [PAYF-02] How long the box waits before it says it is slow (the rows stay, the answer still comes) and gives up. */
internal const val PAY_SLOW_MS = 2_500L
internal const val PAY_GIVE_UP_MS = 15_000L

/** Tests only: how long the box waits for the server before it gives up (screenshots move the clock in big steps). */
@androidx.annotation.VisibleForTesting
internal var payGiveUpMs = PAY_GIVE_UP_MS

enum class PayFindStep {
    NAME, CONFIRM, NONE, DONE,
    /** [PAYG-06] The pay uses this company's way already: said so, with 자세히 고치기 and 다른 회사 찾기. */
    USING,
}

/**
 * [PAYF-01] One question at a time, wherever 급여 방식 찾기 is: the name box with the rows under it, the pay way of the company
 * picked (결정 D79: only "급여 방식 있음 · 맞아요 n명"), none, and done with the 기본급. Kept above the screen so the easy setup
 * can tell which question shows.
 */
@Stable
class PayFindState(name: String) {
    var name by mutableStateOf(name)
    var step by mutableStateOf(PayFindStep.NAME)
    var picked by mutableStateOf<PayFindRows.Row?>(null)
    var used by mutableStateOf<PayFind.Used?>(null)
    /** [PAYG-06] The question opened by itself on the company the user works for (its way, or 직접 만들기 when it has none). */
    var auto by mutableStateOf(false)
    /** [PAYG-06] The company the user works for, on the 광장 without a pay way (or not there at all): 직접 만들기 is the main button. */
    var byHand by mutableStateOf<PayFindRows.Row?>(null)
}

/**
 * [PAYF-01] The state of 급여 방식 찾기. [PAYG-06] 1.30 BA (사용자 "급여 적용 부분에서는 근무주기 적용한 회사가 있으면 뜨고, 없으면
 * 검색, 검색에도 없으면 직접 설정하는 흐름"): it opens on the company the user already works for — its way at once ("‘현대제철’
 * 급여 방식을 쓸까요?") when coworkers made one and the server answers in time, 직접 만들기 first when it has none; the box as
 * before when nothing is known. [usingStep] lets it open on "쓰고 있어요" when the pay uses a way already (the easy setup).
 * Without [autoStart] it is the box alone (자세히 고치기 opens on the questions, not on a company's way).
 */
@Composable
fun rememberPayFindState(usingStep: Boolean = false, autoStart: Boolean = true): PayFindState {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    // [CALI-04] 1.30 BB: the calendar on screen's company and pay ([LocalCompanyCalendar]).
    val company = companyScope(c)
    val find = company.find
    val state = remember(find) { PayFindState(find.known()) }
    // [PAYF-02] 화면 열 때 미리: the company already named is asked for before the user reads the question.
    LaunchedEffect(c) { PayFindSearch.prefetch(c, scope, state.name) }
    LaunchedEffect(find) {
        val using = company.payWays.way.value != null && company.pay.profile.value != null
        val known = state.name
        if (state.step != PayFindStep.NAME || !autoStart) return@LaunchedEffect
        if (using && usingStep) {
            state.step = PayFindStep.USING
            return@LaunchedEffect
        }
        if (!PayFindRows.ready(known)) return@LaunchedEffect
        val hits = if (c.companyApi.enabled) withTimeoutOrNull(PAY_SLOW_MS) { PayFindSearch.server(c, known) } else emptyList()
        val rows = PayFindRows.merge(PayFindRows.local(known, find.knownAll()), hits?.let(PayFindSearch::rows))
        val row = PayFindRows.knownRow(known, rows)
        // Only while the user has not moved on: a late answer never takes the screen from under their fingers.
        if (state.step != PayFindStep.NAME || state.name != known) return@LaunchedEffect
        when (PayFindRows.startStep(row, using = using && usingStep, answered = hits != null)) {
            PayFindRows.Start.CONFIRM -> {
                state.picked = row
                state.auto = true
                state.step = PayFindStep.CONFIRM
            }
            PayFindRows.Start.BY_HAND -> state.byHand = row
            else -> Unit
        }
    }
    return state
}

/**
 * [PAYF-01] 1.29 묶음 AP (사용자 2026-10-27 "급여 설정하는 부분에 검색하는 것이 없다", "급여 입력시 회사 검색이 안된다"): the box to
 * type the company in, right on the question (no button to find it first), the rows the phone has at once and the server's
 * laid over them as they come ([PayFindRows]), then the company's pay way → 이 급여 방식 쓰기 → the 기본급 in the same place.
 *
 * [onByHand] is 직접 만들게요 with the company typed (and its public row, for the usual allowances); [onDone] ends the flow
 * (a sheet closes); [onSaved] gets the pay as it became once the way went in with its 기본급 ([PAYG-01]: the way is already
 * kept by then, the caller only moves on). [onDetailed] is 자세히 고치기 for a pay that uses a way already ([PAYG-05]).
 */
@Composable
fun PayFindFlow(
    state: PayFindState,
    onByHand: (company: String, publicId: String?) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    onSaved: ((PayProfile) -> Unit)? = null,
    /** Inline the 직접 만들게요 below the rows is the screen's own button; a sheet shows it here. */
    byHandHere: Boolean = true,
    onDetailed: (() -> Unit)? = null,
) {
    val c = LocalContext.current.appContainer
    val company = companyScope(c)
    val find = company.find
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (state.step) {
            PayFindStep.NAME -> NameStep(state, find, byHandHere, onByHand) { row ->
                state.picked = row
                state.auto = false
                error = null
                state.step = if (row.pay) PayFindStep.CONFIRM else PayFindStep.NONE
            }
            PayFindStep.USING -> UsingStep(company, onDetailed) { state.step = PayFindStep.NAME }
            PayFindStep.CONFIRM -> {
                val row = state.picked ?: return@Column
                Text("‘${row.name}’ 급여 방식을 쓸까요?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                // 결정 D79: before using it, only that there is one and how many said 맞아요 — never its values.
                StatusChip(PayFindRows.payLine(row), if (row.doubted) Tone.WAIT else Tone.GOOD)
                // [PAYG-06] Opened by itself: why this company.
                if (state.auto) Text("근무주기에 넣은 회사라 먼저 찾아 두었어요.", style = MaterialTheme.typography.bodyMedium)
                if (row.dept.isNotEmpty()) Text("${row.dept}의 대표 주기에 딸린 방식이에요.", style = MaterialTheme.typography.bodyMedium)
                Text("쓰면 이 회사 광장에 참여되고, 수당과 계산 방식만 들어와요. 근무 순서는 그대로예요. 기본급은 바로 다음에 내 것만 넣어요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(onClick = {
                    val id = row.serverId ?: return@Button
                    busy = true
                    scope.launch {
                        try {
                            val used = find.use(id, row.setup)
                            state.used = used
                            state.step = if (used is PayFind.Used.Ready) PayFindStep.DONE else PayFindStep.NONE
                        } catch (e: CompanyException) {
                            error = companyProblem(e.code, e.field)
                        }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("이 급여 방식 쓰기")
                }
                TextButton(onClick = { state.auto = false; state.step = PayFindStep.NAME }, enabled = !busy) { Text("다른 회사 찾기") }
            }
            PayFindStep.NONE -> {
                val row = state.picked
                val name = row?.name ?: state.name.trim()
                Text(if (state.used == PayFind.Used.Gone) "‘$name’ 급여 방식을 지금은 받을 수 없어요" else "아직 ‘$name’ 급여 방식이 없어요",
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("근무주기처럼 직접 만들어 주세요. 몇 가지만 고르면 돼요. 다 만들면 같은 회사 분들도 쓰게 ‘$name’ 이름으로 광장에 나눌지 한 번 여쭤봐요(금액·이름은 빼고).",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { find.none(name); onByHand(name, row?.publicId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("직접 만들게요")
                }
                TextButton(onClick = { state.step = PayFindStep.NAME }) { Text("다른 회사 찾기") }
            }
            PayFindStep.DONE -> {
                val way = (state.used as? PayFind.Used.Ready)?.way
                if (way == null) LaunchedEffect(Unit) { state.step = PayFindStep.NAME }
                else PendingDone(c, company, way, onDone, onSaved)
            }
        }
    }
}

/** [PAYG-06] The pay already uses a company's way: what it is, and the ways to change it. */
@Composable
private fun UsingStep(company: com.bamnat.app.company.CompanyScope, onDetailed: (() -> Unit)?, onOther: () -> Unit) {
    val way by company.payWays.way.collectAsStateWithLifecycle()
    val w = way
    if (w == null) {
        LaunchedEffect(Unit) { onOther() }
        return
    }
    Text("‘${w.company}${w.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}’ 급여 방식을 쓰고 있어요",
        style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("같은 회사 분들이 맞춘 계산 방식에 내 기본급으로 계산하고 있어요. 수당이나 계산 방식 하나만 고치려면 자세히 고치기가 빨라요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (onDetailed != null) Button(onClick = onDetailed, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("자세히 고치기") }
    OutlinedButton(onClick = onOther, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("다른 회사 급여 방식 찾기") }
}

/** The box and its rows: the phone's at once, the server's laid over them, and never "없어요" for an answer still coming. */
@Composable
private fun NameStep(state: PayFindState, find: PayFind, byHandHere: Boolean, onByHand: (String, String?) -> Unit, onPick: (PayFindRows.Row) -> Unit) {
    val c = LocalContext.current.appContainer
    Text("우리 회사 급여 방식 찾기", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("회사 이름을 치면 같은 회사 분들이 확인한 계산 방식이 있는지 바로 보여요. 있으면 그대로 쓰고 기본급만 넣어요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // [PAYG-13] A way chosen and left before its 기본급: said plainly that nothing went in.
    val pending = companyScope(c).pendingWays
    val dropped by pending.dropped.collectAsStateWithLifecycle()
    dropped?.let { d -> Text("‘${d.company}’ 급여 방식은 넣지 않았어요. 다시 고르면 기본급만 넣고 바로 쓸 수 있어요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    // [PAYG-06] The company the user works for, with no pay way yet: making it is the one thing left.
    state.byHand?.takeIf { PublicCycles.norm(it.name) == PublicCycles.norm(state.name.trim()) }?.let { row ->
        SoftCard(highlighted = true) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("‘${row.name}’ 급여 방식은 아직 없어요", style = MaterialTheme.typography.titleMedium)
                Text("근무주기에 넣은 회사예요. 몇 가지만 골라 직접 만들면 같은 회사 분들의 첫 급여 방식이 돼요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = { find.none(row.name); onByHand(row.name, row.publicId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("‘${row.name}’ 직접 만들기", maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    OutlinedTextField(
        state.name, { state.name = it.take(40) },
        label = { Text("회사 이름") },
        placeholder = { Text("예: 현대제철, 예시병원") },
        leadingIcon = { Icon(Icons.Filled.Search, null) },
        trailingIcon = if (state.name.isNotEmpty()) ({ IconButton(onClick = { state.name = "" }) { Icon(Icons.Filled.Clear, "지우기") } }) else null,
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = {}),
        modifier = Modifier.fillMaxWidth(),
    )
    val q = state.name.trim()
    val ready = PayFindRows.ready(q)
    val known = remember(c) { find.knownAll() }
    val local = remember(q, known) { PayFindRows.local(q, known) }
    val server by produceState<PayServer>(PayFindSearch.cached(q)?.let { PayServer.Found(it) } ?: PayServer.Off, q) {
        if (!ready || !c.companyApi.enabled) { value = PayServer.Off; return@produceState }
        PayFindSearch.cached(q)?.let { value = PayServer.Found(it); return@produceState }
        value = PayServer.Asking(slow = false)
        // A short pause in typing; the phone's rows are already there meanwhile.
        delay(INLINE_PAUSE_MS)
        val started = SystemClock.elapsedRealtime()
        val slow = launch { delay(PAY_SLOW_MS); if (value is PayServer.Asking) value = PayServer.Asking(slow = true) }
        val hits = withTimeoutOrNull(payGiveUpMs) { PayFindSearch.server(c, q) }
        slow.cancel()
        value = hits?.let { PayServer.Found(it) } ?: PayServer.NoAnswer
        c.telemetry.event("pay_find", if (hits == null) "no_answer" else if (SystemClock.elapsedRealtime() - started > PAY_SLOW_MS) "slow" else "fast")
    }
    if (!ready) {
        if (q.isNotEmpty()) Text("두 글자부터 찾아요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val rows = PayFindRows.merge(local, (server as? PayServer.Found)?.hits?.let(PayFindSearch::rows))
    Column(Modifier.semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row -> PayRow(row) { onPick(row) } }
        when (val s = server) {
            is PayServer.Asking -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text(if (s.slow) "서버가 조금 느려요. 찾는 대로 바로 보여 드릴게요" else "같은 회사 분들이 확인한 급여 방식을 찾고 있어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            PayServer.NoAnswer -> Text("지금은 같은 회사 분들 자료를 불러오지 못했어요. 인터넷이 되면 다시 찾아 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            else -> Unit
        }
        // Only once the server answered (or there is none) is "없어요" true.
        val settled = server is PayServer.Found || server == PayServer.Off
        if (settled && rows.none { it.pay }) {
            Text("‘$q’ 급여 방식은 아직 없어요. 직접 만들면 같은 회사 분들의 첫 급여 방식이 돼요.", style = MaterialTheme.typography.bodyMedium)
        }
        if (byHandHere) {
            OutlinedButton(onClick = { find.none(q); onByHand(q, null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text(if (rows.isEmpty()) "‘$q’ 직접 만들게요" else "여기 없어요, 직접 만들게요", maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/**
 * [PAYG-01][PAYG-02] 1.30 묶음 BA (사용자 2026-10-27 "기본급 칸(비우면 다음으로 못 감) — 입력을 안 해도 잘 넘어가고 있음. 나중에
 * 클릭하면 적용이 된 상태로 페이지를 빠져나간다"): the way is read, nothing is in yet. The 기본급 starts empty (the setup's own is
 * a chip), the company's 통상시급 hours first, the allowances the company sets for everyone said, and those that differ by person
 * asked (never required). '이대로 계산' puts it all in at once ([commit]); leaving this step any way (이전, 다른 회사, the sheet
 * pulled down, back) puts nothing in ([cancelPending]).
 */
@Composable
internal fun PendingDone(
    c: AppContainer,
    company: com.bamnat.app.company.CompanyScope,
    way: PendingWay,
    onDone: () -> Unit,
    onSaved: ((PayProfile) -> Unit)?,
    /** What came before, said first ("근무주기를 넣었어요"); null for the way alone. */
    intro: String? = null,
    /** [PAYG-03] "급여 방식은 넣지 않을게요": nothing goes in, said as a choice rather than by closing. */
    onSkip: (() -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val month = remember { Today.month() }
    val profile by company.pay.profile.collectAsStateWithLifecycle()
    var base by rememberSaveable { mutableStateOf<Long?>(null) }
    var hours by rememberSaveable { mutableStateOf<Int?>(null) }
    val amounts = remember { mutableStateMapOf<String, Long>() }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // Closed before 이대로 계산: the way waits no more, and nothing went in.
    DisposableEffect(way) { onDispose { company.cancelPending(c, way) } }
    val had = profile
    // [ITEM-06] Only what differs by person, and only allowances the pay does not have yet (its own amounts stay).
    val needs = remember(way.text, had) {
        val mine = had?.items?.map { it.name }?.toSet().orEmpty()
        runCatching { company.share.amountNeeds(way.text) }.getOrDefault(emptyList())
            .filter { it.key.startsWith("item:") && it.key.removePrefix("item:") !in mine }
    }
    intro?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
    Text("기본급만 넣으면 끝나요", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("‘${way.title}’ 수당과 계산 방식이 준비됐어요. ‘이대로 계산’을 누르면 한 번에 들어가요. 그 전에 닫으면 급여는 그대로예요.",
        style = MaterialTheme.typography.bodyMedium)
    CommonItemLines(way.text)
    val wage = had?.wageType ?: com.bamnat.core.pay.WageType.MONTHLY
    BaseFields(wage, base, { base = it }, hours, { hours = it }, had, month,
        companyHours = way.hours, companyName = way.title, keepBase = BaseAsk.keepChip(had, month))
    if (needs.isNotEmpty()) {
        Text("내 수당 금액 (사람마다 다른 것만, 몰라도 돼요)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp))
        needs.forEach { need ->
            MoneyField(
                label = "${need.label} · ${need.hint}",
                value = amounts[need.label],
                onChange = { v -> if (v == null) amounts.remove(need.label) else amounts[need.label] = v },
                placeholder = "모르면 비워 두세요",
            )
        }
    }
    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    Button(onClick = {
        val b = base?.takeIf(BaseAsk::ready) ?: return@Button
        busy = true
        error = null
        // Not cancelled when the sheet leaves the screen half way (a first pay made hides the setup's sheet at once): the way
        // goes in whole, and the screen after it opens.
        scope.launch(kotlinx.coroutines.NonCancellable) {
            // A new pay counts from a year back, so the months already worked come to an amount too (as the amounts screen).
            val from = if (company.pay.profile.value == null) com.bamnat.core.pay.PaySince.start(month, null) else month
            val ok = try {
                company.commit(c, way, b, hours, from, amounts.toMap())
            } catch (e: com.bamnat.app.data.BackupException) {
                error = e.message ?: "급여 방식을 넣지 못했어요"
                false
            } catch (e: org.json.JSONException) {
                error = "이 급여 방식을 읽지 못했어요. 앱을 업데이트한 뒤 다시 해 주세요"
                false
            }
            busy = false
            if (!ok) {
                if (error == null) error = "급여 방식을 넣지 못했어요"
                return@launch
            }
            company.pay.profile.value?.let { onSaved?.invoke(it) }
            onDone()
        }
    }, enabled = !busy && BaseAsk.ready(base), modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("이대로 계산")
    }
    if (onSkip != null) TextButton(onClick = onSkip, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text("급여 방식은 넣지 않을게요")
    }
}

/** One company under the box: its name and the only thing said of its pay way before it is used (결정 D79). */
@Composable
private fun PayRow(row: PayFindRows.Row, onClick: () -> Unit) {
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick), highlighted = row.pay) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(PayFindRows.payLine(row), style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (row.pay) FontWeight.Medium else FontWeight.Normal,
                    color = if (row.pay) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                listOfNotNull(row.industry.ifEmpty { null }, row.form.ifEmpty { null }, "내 회사".takeIf { row.known })
                    .takeIf { it.isNotEmpty() }?.let { Text(it.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

/**
 * [PLAZA-16] 1.27 묶음 AH: the way into 우리 회사 급여 방식 찾기 as a button (자세히 고치기의 첫 질문), opening [PayFindSheet].
 * [PAYF-01] 1.29: no longer hidden without the company server: the phone's rows and 직접 만들게요 still help.
 */
@Composable
fun PayFindEntry(modifier: Modifier = Modifier, open: Boolean = false) {
    var showing by rememberSaveable { mutableStateOf(open) }
    OutlinedButton(onClick = { showing = true }, modifier = modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Icon(Icons.Filled.Business, null, Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("우리 회사 급여 방식 찾기")
    }
    if (showing) PayFindSheet(onDismiss = { showing = false })
}

/**
 * [PAYF-01] The flow in a sheet, for places that are not a question of their own (자세히 고치기, the pay screen's line).
 * [PAYG-01] Pulled down (or back) before '이대로 계산': nothing went in. [onSaved] hears of the pay once the way is in (kept already).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayFindSheet(onDismiss: () -> Unit, onSaved: ((PayProfile) -> Unit)? = null) {
    val state = rememberPayFindState()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState())) {
            PayFindFlow(state, onByHand = { _, _ -> onDismiss() }, onDone = onDismiss, onSaved = onSaved)
        }
    }
}

/**
 * [PAYF-01] 1.29 (사용자 "급여 설정에 검색 칸이 없다"): on the pay screen of a pay already set up by hand, the search box one tap
 * away, until a company pay way is in use. × hides it on this phone; the easy setup's first question still has it, as it says.
 */
@Composable
fun PayFindLine(@Suppress("UNUSED_PARAMETER") onSaved: ((PayProfile) -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    val hints = remember { com.bamnat.app.ui.calendar.HintStore(context) }
    // [PAYG-10] Closed for this calendar only: another calendar's pay still offers its own company's way.
    val calendar = LocalCompanyCalendar.current
    val key = if (calendar == com.bamnat.app.data.PersonalCalendar.MAIN) LINE_CLOSED else "$LINE_CLOSED:$calendar"
    var closed by rememberSaveable(key) { mutableStateOf(hints.done(key)) }
    var open by rememberSaveable { mutableStateOf(false) }
    val company = companyScope(c)
    val way by company.payWays.way.collectAsStateWithLifecycle()
    val applied by company.store.applied.collectAsStateWithLifecycle()
    // [PAYG-01] The way is kept by the time it is saved; the pay screen follows the store, so nothing is saved twice.
    if (open) PayFindSheet(onDismiss = { open = false }, onSaved = null)
    if (closed || way != null || applied != null) return
    SoftCard(modifier) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("우리 회사 급여 방식이 있나요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { hints.markDone(key); closed = true }) { Icon(Icons.Filled.Close, "닫기") }
            }
            Surface(
                onClick = { open = true },
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().padding(end = 12.dp).heightIn(min = 48.dp),
            ) {
                Row(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(10.dp))
                    Text("회사 이름으로 찾기", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text("같은 회사 분들이 맞춘 계산 방식을 쓰면 기본급만 넣으면 돼요. 닫아도 ‘설정 고치기’ 첫 질문에서 찾을 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        }
    }
}

private const val LINE_CLOSED = "pay_find_line"
