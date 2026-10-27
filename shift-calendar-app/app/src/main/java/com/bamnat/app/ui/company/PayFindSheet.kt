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
import androidx.compose.runtime.LaunchedEffect
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
import com.bamnat.app.ui.pay.BaseFields
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.plaza.PayFindRows
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

enum class PayFindStep { NAME, CONFIRM, NONE, DONE }

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
}

@Composable
fun rememberPayFindState(): PayFindState {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    // [CALI-04] 1.30 BB: the calendar on screen's company and pay ([LocalCompanyCalendar]).
    val find = companyScope(c).find
    val state = remember(find) { PayFindState(find.known()) }
    // [PAYF-02] 화면 열 때 미리: the company already named is asked for before the user reads the question.
    LaunchedEffect(c) { PayFindSearch.prefetch(c, scope, state.name) }
    return state
}

/**
 * [PAYF-01] 1.29 묶음 AP (사용자 2026-10-27 "급여 설정하는 부분에 검색하는 것이 없다", "급여 입력시 회사 검색이 안된다"): the box to
 * type the company in, right on the question (no button to find it first), the rows the phone has at once and the server's
 * laid over them as they come ([PayFindRows]), then the company's pay way → 이 급여 방식 쓰기 → the 기본급 in the same place.
 *
 * [onByHand] is 직접 만들게요 with the company typed (and its public row, for the usual allowances); [onDone] ends the flow
 * (a sheet closes); [onSaved] keeps the pay with its 기본급 after the way went onto a pay that had one.
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
) {
    val c = LocalContext.current.appContainer
    val find = companyScope(c).find
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        when (state.step) {
            PayFindStep.NAME -> NameStep(state, find, byHandHere, onByHand) { row ->
                state.picked = row
                error = null
                state.step = if (row.pay) PayFindStep.CONFIRM else PayFindStep.NONE
            }
            PayFindStep.CONFIRM -> {
                val row = state.picked ?: return@Column
                Text("‘${row.name}’ 급여 방식을 쓸까요?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                // 결정 D79: before using it, only that there is one and how many said 맞아요 — never its values.
                StatusChip(PayFindRows.payLine(row), if (row.doubted) Tone.WAIT else Tone.GOOD)
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
                            state.step = if (used == PayFind.Used.Gone) PayFindStep.NONE else PayFindStep.DONE
                        } catch (e: CompanyException) {
                            error = companyProblem(e.code, e.field)
                        } catch (e: com.bamnat.app.data.BackupException) {
                            error = e.message ?: "급여 방식을 넣지 못했어요"
                        }
                        busy = false
                    }
                }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("이 급여 방식 쓰기")
                }
                TextButton(onClick = { state.step = PayFindStep.NAME }, enabled = !busy) { Text("다른 회사 찾기") }
            }
            PayFindStep.NONE -> {
                val row = state.picked
                val company = row?.name ?: state.name.trim()
                Text(if (state.used == PayFind.Used.Gone) "‘$company’ 급여 방식을 지금은 받을 수 없어요" else "아직 ‘$company’ 급여 방식이 없어요",
                    style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                Text("근무주기처럼 직접 만들어 주세요. 몇 가지만 고르면 돼요. 다 만들면 같은 회사 분들도 쓰게 ‘$company’ 이름으로 광장에 나눌지 한 번 여쭤봐요(금액·이름은 빼고).",
                    style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { find.none(company); onByHand(company, row?.publicId) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                    Text("직접 만들게요")
                }
                TextButton(onClick = { state.step = PayFindStep.NAME }) { Text("다른 회사 찾기") }
            }
            PayFindStep.DONE -> DoneStep(c, state, onDone, onSaved)
        }
    }
}

/** The box and its rows: the phone's at once, the server's laid over them, and never "없어요" for an answer still coming. */
@Composable
private fun NameStep(state: PayFindState, find: PayFind, byHandHere: Boolean, onByHand: (String, String?) -> Unit, onPick: (PayFindRows.Row) -> Unit) {
    val c = LocalContext.current.appContainer
    Text("우리 회사 급여 방식 찾기", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("회사 이름을 치면 같은 회사 분들이 확인한 계산 방식이 있는지 바로 보여요. 있으면 그대로 쓰고 기본급만 넣어요.",
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
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

/** [PAYF-06] The way is in: with a pay that had amounts, the 기본급 is checked right here; without one, the amounts screen asks it. */
@Composable
private fun DoneStep(c: AppContainer, state: PayFindState, onDone: () -> Unit, onSaved: ((PayProfile) -> Unit)?) {
    val used = state.used
    val company = state.picked?.name.orEmpty()
    if (used != PayFind.Used.Applied) {
        Text("이제 내 금액만 넣으면 돼요", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        Text("‘$company’ 방식으로 계산해요. 기본급과 내 수당 금액만 물어볼게요.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("내 금액 넣기") }
        return
    }
    val scope = rememberCoroutineScope()
    val month = remember { Today.month() }
    val pay = companyScope(c).pay
    val profile by pay.profile.collectAsStateWithLifecycle()
    var base by rememberSaveable { mutableStateOf(BaseAsk.known(profile, month)) }
    var hours by rememberSaveable { mutableStateOf<Int?>(null) }
    Text("급여 방식을 넣었어요", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
    Text("‘$company’ 수당과 계산 방식이 들어왔어요. 근무 순서는 그대로예요. 기본급만 확인해 주세요.", style = MaterialTheme.typography.bodyMedium)
    BaseFields(profile?.wageType ?: com.bamnat.core.pay.WageType.MONTHLY, base, { base = it }, hours, { hours = it }, profile, month)
    Button(onClick = {
        val b = base ?: return@Button
        val p = pay.profile.value ?: return@Button onDone()
        val next = BaseAsk.apply(p, b, month, hours)
        if (onSaved != null) onSaved(next) else scope.launch { pay.save(next) }
        onDone()
    }, enabled = (base ?: 0) > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("이대로 계산") }
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

/** [PAYF-01] The flow in a sheet, for places that are not a question of their own (자세히 고치기, the pay screen's line). */
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
fun PayFindLine(onSaved: ((PayProfile) -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val c = context.appContainer
    val hints = remember { com.bamnat.app.ui.calendar.HintStore(context) }
    var closed by rememberSaveable { mutableStateOf(hints.done(LINE_CLOSED)) }
    var open by rememberSaveable { mutableStateOf(false) }
    val company = companyScope(c)
    val way by company.payWays.way.collectAsStateWithLifecycle()
    val applied by company.store.applied.collectAsStateWithLifecycle()
    if (open) PayFindSheet(onDismiss = { open = false }, onSaved = onSaved)
    if (closed || way != null || applied != null) return
    SoftCard(modifier) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("우리 회사 급여 방식이 있나요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { hints.markDone(LINE_CLOSED); closed = true }) { Icon(Icons.Filled.Close, "닫기") }
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
