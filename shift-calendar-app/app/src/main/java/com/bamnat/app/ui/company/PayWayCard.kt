package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.bamnat.app.company.PendingWay
import com.bamnat.app.company.cancelPending
import com.bamnat.app.company.pendingWays
import com.bamnat.app.ui.appViewModel
import com.bamnat.app.ui.pay.BaseFields
import com.bamnat.app.ui.pay.won
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
    /** [PAYG-01] The calendar's company, for a way that waits for its 기본급 ([com.bamnat.app.company.PendingWays]). */
    internal val company: com.bamnat.app.company.CompanyScope get() = scope
    internal val app: AppContainer get() = c
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
    /** 1.39 발굴 2-1: the newer version of the way in use one of its senders sent since (its id), offered as '새 판으로 바꾸기'. */
    private val _newer = MutableStateFlow<String?>(null)
    val newer: StateFlow<String?> = _newer.asStateFlow()
    /** 1.39 발굴 2-3: this phone's own 보완 요청 on the way in use: "open", "answered" or null. */
    private val _myFix = MutableStateFlow<String?>(null)
    val myFix: StateFlow<String?> = _myFix.asStateFlow()

    // Read when the screen opens and again when the way in use changes (a newer version put in, 직접 만들기).
    init {
        viewModelScope.launch {
            way.map { it?.companyId to it?.setup }.distinctUntilChanged().collect {
                _newer.value = null
                _myFix.value = null
                load()
            }
        }
    }

    fun load() {
        val w = way.value ?: return
        if (!c.companyApi.enabled) return
        viewModelScope.launch {
            val page = runCatching { c.companyApi.company(w.companyId) }.getOrNull() ?: return@launch
            _newer.value = page.newer[w.setup]?.takeIf { n -> n != w.setup && page.setups.any { it.id == n && it.hasPay } }
            page.setups.firstOrNull { it.id == w.setup }?.let { s ->
                _opinion.value = s.opinion
                _text.value = s.text.takeIf { !s.payHidden }
                _myFix.value = s.myFix
            }
        }
    }

    /**
     * 1.39 발굴 2-1: '새 판으로 바꾸기': the newer version is read (its pay part taken, as '이 급여 방식 쓰기') and waits for its 기본급
     * on this screen ([PendingWayAsk]); nothing changes until '이대로 계산'. [done] gets the error, or null.
     */
    fun takeNewer(done: (String?) -> Unit) {
        val w = way.value ?: return done(null)
        val next = _newer.value ?: return done(null)
        viewModelScope.launch {
            val error = try {
                when (com.bamnat.app.company.PayFind(c, calendar).use(w.companyId, next, PendingWay.Source.NEWER)) {
                    is com.bamnat.app.company.PayFind.Used.Ready -> { c.telemetry.event("pay_way", "newer"); null }
                    com.bamnat.app.company.PayFind.Used.Gone -> "새 판을 찾지 못했어요. 잠시 뒤 다시 해 주세요"
                }
            } catch (e: CompanyException) {
                companyProblem(e)
            }
            done(error)
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
                if (com.bamnat.app.company.SmallSends.waits(e)) {
                    // 2.0.0 [RVN-16] 검토 U10b-03: no connection: the word waits on the phone and goes once online; the card says so in its line.
                    val verified = ok && scope.slipProof()?.counts == true
                    com.bamnat.app.company.SmallSends.later(c, scope.store, com.bamnat.app.company.SmallSends.vote(w.companyId, w.setup, ok, verified, why))
                    scope.payWays.voted(ok, why)
                    if (scope.store.applied.value?.setup == w.setup) scope.store.voted(ok)
                    com.bamnat.app.company.SmallSends.waitsLine(ok)
                } else companyProblem(e)
            }
            done(error)
        }
    }

    /** 2.0.0 [RVN-18] 검토 COMP-22-1: 맞아요 ✓ pressed again takes it back; [done] gets null when it went, else what to say. */
    fun takeBack(done: (String?) -> Unit) {
        val w = way.value ?: return done(null)
        viewModelScope.launch {
            val error = try {
                c.companyApi.unvote(scope.store.device, w.companyId, w.setup)
                scope.payWays.voted(null, null)
                if (scope.store.applied.value?.setup == w.setup) scope.store.voted(null)
                c.telemetry.event("pay_way_vote", "taken_back")
                load()
                null
            } catch (e: CompanyException) {
                companyProblem(e)
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

    /** 1.39 [PAYP-05] 보완 요청 is open (처리방침 3.12 in force); before it, 의견 보내기 is 달라요 with where. */
    val fixOpen: Boolean get() = c.flags.on(com.bamnat.core.flags.FlagCatalog.PLAZA_FIX_REQUEST)

    /**
     * 1.39 [PAYP-05] 보완 요청: where the way differs ([fields], no values) and a line, to the version's talk; its senders hear of it.
     * [done] gets the error or what to say.
     */
    fun fix(fields: List<String>, body: String, done: (String?, String?) -> Unit) {
        val w = way.value ?: return done(null, null)
        viewModelScope.launch {
            try {
                if (fixOpen) {
                    val lines = fields.map { com.bamnat.app.company.ChangeLine(it, "", "", "") }
                    c.companyApi.post(scope.store.device, w.companyId, w.setup, body.trim(), null, "fix", lines, null)
                    c.telemetry.event("pay_way_fix", fields.joinToString(","))
                    done(null, "보냈어요. 공유한 분이 고치거나 답하면 밤낮 소식으로 알려 드려요")
                } else {
                    val why = fields.firstNotNullOfOrNull { FIX_TO_WHY[it] } ?: "pay"
                    c.companyApi.vote(scope.store.device, w.companyId, w.setup, false, false, why)
                    scope.payWays.voted(false, why)
                    if (scope.store.applied.value?.setup == w.setup) scope.store.voted(false)
                    c.telemetry.event("pay_way_vote", why)
                    load()
                    done(null, "알려 주셔서 고마워요. 같은 회사 분들께 ‘달라요’로 보여요")
                }
            } catch (e: CompanyException) {
                done(companyProblem(e), null)
            }
        }
    }
}

/** 1.39 [PAYP-05] Before 3.12: a 보완 요청's first field as 달라요's where. */
private val FIX_TO_WHY = mapOf("order" to "order", "shift" to "time", "break" to "time", "hours" to "pay", "overtime" to "pay",
    "night" to "pay", "holiday" to "pay", "allowance" to "pay", "bonus" to "pay")

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
fun PayWayCard(profile: PayProfile?, update: ((PayProfile) -> PayProfile) -> Unit, modifier: Modifier = Modifier, onLeave: () -> Unit = {}) {
    // [CALI-04] The pay screen's calendar ([LocalCompanyCalendar]).
    val calendar = LocalCompanyCalendar.current
    val vm = appViewModel(key = "pay-way-$calendar") { PayWayViewModel(it, calendar) }
    val way by vm.way.collectAsStateWithLifecycle()
    val askBase by vm.askBase.collectAsStateWithLifecycle()
    val opinion by vm.opinion.collectAsStateWithLifecycle()
    val text by vm.text.collectAsStateWithLifecycle()
    val month = remember { Today.month() }
    var baseOpen by rememberSaveable { mutableStateOf(false) }
    // [PAYG-03] 1.30 BA: a way that waits for its 기본급 ('n명이 쓰고 있어요' with the pay, or 다시 넣기) asks it here; askBase is
    // only the way here (the cycle screen's 'go to the pay'), the question is the pending way's own.
    LaunchedEffect(askBase) { if (askBase) vm.baseAsked() }
    if (baseOpen && profile != null) BaseAskSheet(profile, month, onDismiss = { baseOpen = false }) { base, hours ->
        update { p -> BaseAsk.apply(p, base, month, hours) }
        baseOpen = false
    }
    PendingWayAsk(vm, profile)
    var proposing by remember { mutableStateOf(false) }
    var mine by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var said by rememberSaveable { mutableStateOf<String?>(null) }
    val w = way ?: return
    if (profile == null) return
    TalkSheet(vm.talk)
    // 1.39 [PAYP-04][PAYP-05] 의견 보내기 (보완 요청) and 직접 만들기, for a way put in from 1.39 (its calculation is not shown).
    var fixing by rememberSaveable { mutableStateOf(false) }
    var leaving by rememberSaveable { mutableStateOf(false) }
    if (fixing) FixRequestSheet(vm, onDismiss = { fixing = false })
    if (leaving) LeaveSharedDialog(onLeave = { leaving = false; onLeave() }, onDismiss = { leaving = false })
    // [J135-01] (사용자 2026-10-30 "이미 의견을 보냈는데 사라지지 않고 계속 떠있어서 화면을 차지"): once 맞았어요·달라요 is sent, the
    // card folds to one line a moment after the thanks; '펼치기' brings the buttons back (바꾸기·고쳐서 제안하기·토론).
    val voted = w.vote ?: opinion?.mine?.let { it == "ok" }
    var unfolded by rememberSaveable(w.company, w.dept) { mutableStateOf(false) }
    LaunchedEffect(said) { if (said != null) { kotlinx.coroutines.delay(4_000); if (!proposing) { said = null; unfolded = false } } }
    val newer by vm.newer.collectAsStateWithLifecycle()
    val myFix by vm.myFix.collectAsStateWithLifecycle()
    var newerError by remember { mutableStateOf<String?>(null) }
    // 1.39 발굴 2-1·2-3: a newer version, or an answer to my 보완 요청, keeps the card open (folded, they would go unseen).
    val news = newer != null || (myFix == "answered" && vm.fixOpen)
    if (voted != null && !unfolded && said == null && !proposing && !news) {
        SoftCard(modifier) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (w.shared) "공유받은 ‘${w.company}’ 급여 방식" else "‘${w.company}’ 급여 방식", style = MaterialTheme.typography.bodyMedium, maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    Text(if (voted) "‘맞아요’라고 알려 주셨어요" else "‘달라요’라고 알려 주셨어요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val known = BaseAsk.known(profile, month)
                    if (known != null && BaseAsk.needed(profile, month)) Text("${profile.wageType.baseWord()} ${won(known)}은 대략이에요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error)
                }
                TextButton(onClick = { unfolded = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("펼치기") }
            }
        }
    } else SoftCard(modifier, highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (w.shared) {
                Text(SharedPayWords.TITLE, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("${SharedPayWords.from(w)}이에요. ${SharedPayWords.hidden(profile.wageType)}", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(SharedPayWords.ASK, style = MaterialTheme.typography.bodyMedium)
            } else {
                Text("‘${w.company}${w.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}’ 급여 방식을 쓰고 있어요",
                    style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                Text("같은 회사 분들이 맞춘 계산 방식이에요. 명세서와 맞는지 알려 주시면 다음 분은 더 정확하게 받아요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // [PAYF-06] A base only guessed is said here, one tap to put it in. [PAYG-04] 1.30: none at all is the pay's own
            // "기본급만 넣으면 끝나요" in place of the total (BaseNeededCard), not a second line here.
            val known = BaseAsk.known(profile, month)
            if (known != null && BaseAsk.needed(profile, month)) {
                Row(Modifier.fillMaxWidth()) {
                    Text("${profile.wageType.baseWord()} ${won(known)}은 대략이에요. 명세서대로 넣어 주세요",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = Modifier.weight(1f).padding(top = 12.dp))
                    TextButton(onClick = { baseOpen = true }) { Text("넣기") }
                }
            }
            if (!vm.server) {
                if (w.shared) OutlinedButton(onClick = { leaving = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("직접 만들기") }
                return@Column
            }
            // 1.39 발굴 2-1 (1-4 "고친 판이 나와도 쓰는 분은 옛 판에 머물러요"): one who shared this way sent a corrected one; the
            // way in use does not follow by itself, so it is offered here, put in the same way as '이 급여 방식 쓰기'.
            if (newer != null) NewerWayLine(error = newerError, onTake = { newerError = null; vm.takeNewer { e -> newerError = e } })
            // 1.39 발굴 2-3: what became of my 보완 요청 (처리방침 3.12, as 보완 요청 itself).
            if (vm.fixOpen) myFix?.let { m -> MyFixLine(answered = m == "answered", onTalk = vm::openTalk) }
            if (voted != null && unfolded) TextButton(onClick = { unfolded = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("접기") }
            VoteButtons(w.vote ?: opinion?.mine?.let { it == "ok" }, onVote = { ok, why ->
                said = null
                vm.vote(ok, why) { e ->
                    said = e ?: if (ok) "고마워요! ${w.company} 분들께 큰 힘이 돼요" else if (text != null) "알려 주셔서 고마워요. 맞는 값으로 고쳐서 제안해 주시면 바로잡혀요" else "알려 주셔서 고마워요. ‘의견 보내기’로 어디가 다른지 알려 주시면 공유한 분이 고쳐요"
                    // 달라요: straight on to what it should be, on the company's own values.
                    if (e == null && !ok && text != null) proposing = true
                }
            }, opinion, onTakeBack = {
                // 2.0.0 [RVN-18] COMP-22-1 ✓ again takes 맞아요 back (before: the same vote went again).
                said = null
                vm.takeBack { e -> said = e ?: "‘맞아요’를 거뒀어요" }
            })
            said?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            // Side by side; one under the other when the letters are large (200%), so neither breaks into syllables.
            val large = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
            // 1.39 [PAYP-05]: with the calculation not shown (text null) there is nothing to correct line by line, so 의견 보내기
            // (보완 요청: where it differs and a line) stands in for 고쳐서 제안하기.
            val propose: @Composable (Modifier) -> Unit = { m ->
                if (text != null) OutlinedButton(onClick = { sendError = null; proposing = true }, modifier = m.heightIn(min = 48.dp)) { Text("고쳐서 제안하기") }
                else OutlinedButton(onClick = { fixing = true }, modifier = m.heightIn(min = 48.dp)) { Text("의견 보내기") }
            }
            val talk: @Composable (Modifier) -> Unit = { m -> OutlinedButton(onClick = vm::openTalk, modifier = m.heightIn(min = 48.dp)) { Text("토론 보기") } }
            if (large) {
                propose(Modifier.fillMaxWidth())
                talk(Modifier.fillMaxWidth())
            } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                propose(Modifier.weight(1f))
                talk(Modifier.weight(1f))
            }
            if (w.shared) OutlinedButton(onClick = { leaving = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("직접 만들기") }
        }
    }
    val base = text
    if (proposing && base != null) {
        LaunchedEffect(Unit) { mine = vm.mine() }
        DiffEditorSheet(
            title = w.company, base = base, mine = mine, busy = sending, error = sendError,
            // [FIN130-02] This pay uses the way: what is changed may go onto it too (PROP-03).
            alsoMine = true,
            onSend = { lines, setup, why ->
                sending = true
                sendError = null
                vm.propose(lines, setup, why) { e ->
                    sending = false
                    if (e == null) { proposing = false; said = "제안을 올렸어요. 같은 회사 분들이 맞아요를 누르면 다음 판이 돼요" } else sendError = e
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
fun BaseAskSheet(profile: PayProfile, month: java.time.YearMonth, onDismiss: () -> Unit,
    /** 1.39 [PAYP-04] The way is someone else's: the 기본급 alone, the 통상시급 hours not shown. */
    locked: Boolean = false,
    onSave: (Long, Int?) -> Unit) {
    var base by rememberSaveable { mutableStateOf(BaseAsk.known(profile, month)) }
    var hours by rememberSaveable { mutableStateOf<Int?>(null) }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 2.0.0 [RVN-18] T10a-03 a 시급·일당 worker is asked the 시급·일당.
            val baseWord = profile.wageType.baseWord()
            Text("$baseWord${com.bamnat.core.text.Josa.eulreul(baseWord)} 확인해 주세요", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            Text(if (locked) SharedPayWords.baseOnly(profile.wageType) else "회사 방식은 규칙만 가져와요. 금액은 내 명세서대로 넣어야 예상 급여가 맞아요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            BaseFields(profile.wageType, base, { base = it }, hours, { hours = it }, profile, month, hideHours = locked)
            Button(onClick = { base?.let { onSave(it, hours) } }, enabled = (base ?: 0) > 0, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
                Text("이대로 계산")
            }
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("나중에") }
        }
    }
}

/**
 * [PAYG-03][PAYG-13][PAYG-08] 1.30 묶음 BA (사용자 2026-10-27 "기본급 없이 닫으면 … 창이 닫히면서 바로 적용이 되므로 동작 안 되고
 * 있음"): on the pay screen, a company's way that waits for its 기본급 — the cycle put in with "급여 계산 방식도 같이"
 * ('n명이 쓰고 있어요'), or one the user asked again — is asked in a sheet: '이대로 계산' puts it in at once, '급여 방식은 넣지
 * 않을게요' or closing leaves the pay as it was (the cycle stays: that was asked for on its own). A way left so is one line with
 * 다시 넣기; and once a way went in with 통상시급 hours other than the company's, the opinion on them is offered ready to send.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PendingWayAsk(vm: PayWayViewModel, profile: PayProfile?) {
    val company = vm.company
    val c = vm.app
    val ways = company.pendingWays
    val pending by ways.pending.collectAsStateWithLifecycle()
    val dropped by ways.dropped.collectAsStateWithLifecycle()
    val hoursAsk by ways.hoursAsk.collectAsStateWithLifecycle()
    // A way chosen in 급여 방식 찾기 is asked there; here only the cycle's, and any the user asked again from the line below.
    var again by remember { mutableStateOf<PendingWay?>(null) }
    val asking = pending?.takeIf { it.source != PendingWay.Source.FIND || it == again }
    if (asking != null && profile != null) {
        com.bamnat.app.ui.components.AppSheet(onDismissRequest = { company.cancelPending(c, asking) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PendingDone(c, company, asking, onDone = {}, onSaved = null,
                    intro = when (asking.source) {
                        PendingWay.Source.APPLY -> "‘${asking.title}’ 근무주기를 넣었어요"
                        // 1.39 발굴 2-1: the corrected version of the way in use.
                        PendingWay.Source.NEWER -> "‘${asking.title}’ 공유한 분이 고친 새 판이에요"
                        else -> null
                    },
                    onSkip = { company.cancelPending(c, asking) })
            }
        }
    }
    val d = dropped
    if (d != null && pending == null && profile != null) {
        SoftCard(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
            Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("‘${d.company}’ 급여 방식은 넣지 않았어요. 급여는 그대로예요.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { again = d; ways.again() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다시 넣기") }
                androidx.compose.material3.IconButton(onClick = ways::forgetDropped) {
                    androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Filled.Close, "닫기")
                }
            }
        }
    }
    // [PAYG-08] "고치면 의견으로": the company's way with the user's hours, ready to send to its talk (or let go).
    hoursAsk?.let { (w, h) -> HoursProposalSheet(CompanyWayText(w.way(), w.text), h) { ways.hoursAsked() } }
}

/** 1.39 발굴 2-1: "공유한 분이 고친 판을 새로 올렸어요" with 새 판으로 바꾸기. */
@Composable
internal fun NewerWayLine(error: String?, onTake: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(PayWayWords.NEWER, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Text(PayWayWords.NEWER_HINT, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onTake, modifier = Modifier.heightIn(min = 48.dp)) { Text(PayWayWords.NEWER_TAKE) }
        }
        error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}

/** 1.39 발굴 2-3: my 보완 요청 on the way in use: waiting, or answered with 토론 보기. */
@Composable
internal fun MyFixLine(answered: Boolean, onTalk: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
        Text(if (answered) PayWayWords.FIX_ANSWERED else PayWayWords.FIX_WAITING, style = MaterialTheme.typography.bodyMedium,
            color = if (answered) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onTalk, modifier = Modifier.heightIn(min = 48.dp)) { Text("토론 보기") }
    }
}

/** 1.39 발굴 2-1·2-3: the pay way card's lines, one place for the card and the tests. */
object PayWayWords {
    const val NEWER = "공유한 분이 고친 판을 새로 올렸어요"
    const val NEWER_HINT = "바꾸면 기본급만 다시 확인하고 바로 계산해요. 바꾸기 전엔 지금 방식 그대로예요."
    const val NEWER_TAKE = "새 판으로 바꾸기"
    const val FIX_WAITING = "보낸 보완 요청 · 공유한 분의 답을 기다리는 중"
    const val FIX_ANSWERED = "보낸 보완 요청에 답이 왔어요"
}
