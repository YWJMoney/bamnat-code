package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.Opinion
import com.bamnat.app.company.PayWay
import com.bamnat.app.ui.appViewModel
import com.bamnat.app.ui.pay.BaseFields
import com.bamnat.app.ui.pay.won
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [PAYF-04] 1.29 묶음 AP: the company pay way in use as the pay screen shows it — its opinion as the plaza has it (맞아요·달라요,
 * 달라요 by where), the setup it came from for 고쳐서 제안하기, and the talk on it. Read once when the screen opens.
 */
class PayWayViewModel(
    private val c: AppContainer,
    /** [CALI-04] 1.30 BB: the calendar whose pay this is; its own company pay way. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    private val scope = c.companyOf(calendar)
    val way: StateFlow<PayWay?> = scope.payWays.way
    val askBase: StateFlow<Boolean> = scope.payWays.askBase
    private val _opinion = MutableStateFlow<Opinion?>(null)
    val opinion: StateFlow<Opinion?> = _opinion.asStateFlow()
    /** The company's setup the way came from (as the plaza has it now), to propose changes on. */
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text.asStateFlow()
    val talk = TalkHolder(c, viewModelScope, calendar)
    /** Without the company server only the 기본급 line is left (a build with no server address). */
    val server: Boolean = c.companyApi.enabled

    init { load() }

    fun load() {
        val w = way.value ?: return
        if (!c.companyApi.enabled) return
        viewModelScope.launch {
            runCatching { c.companyApi.company(w.companyId) }.getOrNull()?.setups?.firstOrNull { it.id == w.setup }?.let { s ->
                _opinion.value = s.opinion
                _text.value = s.text.takeIf { !s.payHidden }
            }
        }
    }

    /** 맞아요 (with a matched payslip it counts double, as everywhere) or 달라요 with where; null when it went. */
    fun vote(ok: Boolean, why: String?, done: (String?) -> Unit) {
        val w = way.value ?: return done(null)
        viewModelScope.launch {
            val error = try {
                val verified = ok && scope.slipProof()?.counts == true
                c.companyApi.vote(scope.store.device, w.companyId, w.setup, ok, verified, why)
                scope.payWays.voted(ok, why)
                if (scope.store.applied.value?.setup == w.setup) scope.store.voted(ok)
                c.telemetry.event("pay_way_vote", if (ok) "ok" else why ?: "bad")
                load()
                null
            } catch (e: CompanyException) {
                companyProblem(e.code, e.field)
            }
            done(error)
        }
    }

    /** This phone's own setup (amounts left out), for 내 설정으로 채우기 in the proposal. */
    suspend fun mine(): String? = runCatching { scope.share.exportForServer().toString() }.getOrNull()

    /** 달라요 → 고쳐서 제안하기: the company's setup as the user says it is, to its talk as a proposal (D79 rules, no amount). */
    fun propose(lines: List<com.bamnat.app.company.ChangeLine>, setup: String, why: String, done: (String?) -> Unit) {
        val w = way.value ?: return done(null)
        val base = _text.value ?: return done(null)
        talk.show(w.companyId, w.setup, "${w.company} · ${w.dept.ifEmpty { "회사 전체" }}")
        talk.send(proposalBody(com.bamnat.core.plaza.SetupCompare.rows(base, setup), why), kind = "proposal", change = lines, setupText = setup) { e ->
            if (e == null) c.telemetry.event("plaza_diff", "pay_way") else talk.close()
            done(e)
        }
    }

    fun openTalk() {
        val w = way.value ?: return
        talk.show(w.companyId, w.setup, "${w.company} · ${w.dept.ifEmpty { "회사 전체" }}")
    }

    fun baseAsked() = scope.payWays.askBase(false)
}

/**
 * [PAYF-04] 1.29 묶음 AP (사용자 2026-10-27 "맞아요 달라요 버튼이 해당 급여설정 페이지에서 보여야한다"): while the pay uses a
 * company's way, the pay screen says whose it is and keeps 맞아요·달라요 on it (1.27's opinion form: 달라요 asks where). 달라요
 * leads straight to 고쳐서 제안하기 on that company's 대표 주기 and pay rules (1.25's 다른 점 말하기, the plaza's proposal that
 * becomes the next version when coworkers agree) and to its talk. [PAYF-06] The 기본급 is checked here too: once right after a
 * way went onto a pay with amounts, and as a line while it is missing or only guessed.
 *
 * [update] changes the pay (the pay screen's own [com.bamnat.app.ui.pay.PayViewModel.update]).
 */
@Composable
fun PayWayCard(profile: PayProfile?, update: ((PayProfile) -> PayProfile) -> Unit, modifier: Modifier = Modifier) {
    // [CALI-04] The pay screen's calendar ([LocalCompanyCalendar]).
    val calendar = LocalCompanyCalendar.current
    val vm = appViewModel(key = "pay-way-$calendar") { PayWayViewModel(it, calendar) }
    val way by vm.way.collectAsStateWithLifecycle()
    val askBase by vm.askBase.collectAsStateWithLifecycle()
    val opinion by vm.opinion.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val month = remember { Today.month() }
    var baseOpen by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(askBase) { if (askBase) { baseOpen = true; vm.baseAsked() } }
    if (baseOpen && profile != null) BaseAskSheet(profile, month, onDismiss = { baseOpen = false }) { base, hours ->
        update { p -> BaseAsk.apply(p, base, month, hours) }
        baseOpen = false
    }
    var proposing by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var said by rememberSaveable { mutableStateOf<String?>(null) }
    val w = way ?: return
    if (profile == null) return
    TalkSheet(vm.talk)
    SoftCard(modifier, highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("‘${w.company}${w.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}’ 급여 방식을 쓰고 있어요",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("같은 회사 분들이 맞춘 계산 방식이에요. 명세서와 맞는지 알려 주시면 다음 분은 더 정확하게 받아요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // [PAYF-06] Rules without the user's own base come to 0원 or a guess: said here, one tap to put it in.
            val known = BaseAsk.known(profile, month)
            if (BaseAsk.needed(profile, month)) {
                Row(Modifier.fillMaxWidth()) {
                    Text(if (known == null) "기본급을 넣어야 예상 급여가 맞아요" else "기본급 ${won(known)}은 대략이에요. 명세서대로 넣어 주세요",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { baseOpen = true }) { Text("넣기") }
                }
            }
            if (!vm.server) return@Column
            VoteButtons(w.vote ?: opinion?.mine?.let { it == "ok" }, onVote = { ok, why ->
                said = null
                vm.vote(ok, why) { e ->
                    said = e ?: if (ok) "고마워요! ${w.company} 분들께 큰 힘이 돼요" else "알려 주셔서 고마워요. 맞는 값으로 고쳐서 제안해 주시면 바로잡혀요"
                    // 달라요: straight on to what it should be, on the company's own values.
                    if (e == null && !ok && text != null) proposing = true
                }
            }, opinion)
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            // Side by side; one under the other when the letters are large (200%), so neither breaks into syllables.
            val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
            val propose: @Composable (Modifier) -> Unit = { m ->
                if (text != null) OutlinedButton(onClick = { sendError = null; proposing = true }, modifier = m.heightIn(min = 48.dp)) { Text("고쳐서 제안하기") }
            }
            val talk: @Composable (Modifier) -> Unit = { m -> OutlinedButton(onClick = vm::openTalk, modifier = m.heightIn(min = 48.dp)) { Text("토론 보기") } }
            if (large) {
                propose(Modifier.fillMaxWidth())
                talk(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                if (text != null) propose(Modifier.weight(1f))
                talk(Modifier.weight(1f))
            }
        }
    }
    val base = text
    if (proposing && base != null) {
        LaunchedEffect(Unit) { mine = vm.mine() }
        DiffEditorSheet(
            title = w.company, base = base, mine = mine, busy = sending, error = sendError,
            onSend = { lines, setup, why ->
                sending = true
                sendError = null
                vm.propose(lines, setup, why) { e ->
                    sending = false
                    if (e == null) { proposing = false; said = "제안을 올렸어요. 같은 회사 분들이 맞아요를 누르면 다음 버전이 돼요" } else sendError = e
                }
            },
            onDismiss = { proposing = false },
        )
    }
}

/**
 * [PAYF-06] The 기본급 check in a sheet: the one required field and, folded, the 통상시급's hours. [onSave] gets the base and
 * the hours (null leaves them).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BaseAskSheet(profile: PayProfile, month: java.time.YearMonth, onDismiss: () -> Unit, onSave: (Long, Int?) -> Unit) {
    var base by rememberSaveable { mutableStateOf(BaseAsk.known(profile, month)) }
    var hours by rememberSaveable { mutableStateOf<Int?>(null) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("기본급을 확인해 주세요", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text("회사 방식은 규칙만 가져와요. 금액은 내 명세서대로 넣어야 예상 급여가 맞아요.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            BaseFields(profile.wageType, base, { base = it }, hours, { hours = it }, profile, month)
            Button(onClick = { base?.let { onSave(it, hours) } }, enabled = (base ?: 0) > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("이대로 계산")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("나중에") }
        }
    }
}
