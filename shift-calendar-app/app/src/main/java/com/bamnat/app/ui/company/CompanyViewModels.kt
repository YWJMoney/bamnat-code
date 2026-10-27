package com.bamnat.app.ui.company

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyDraft
import com.bamnat.app.company.PublicUse
import com.bamnat.core.plaza.PublicSetup
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.CompanyTerms
import com.bamnat.app.company.CompanyText
import com.bamnat.app.company.ReportStatus
import com.bamnat.app.company.SentReport
import com.bamnat.app.company.SharedSetup
import com.bamnat.app.company.bundle
import com.bamnat.app.company.prefetchCompanies
import com.bamnat.app.company.warmCompanies
import com.bamnat.app.data.BackupException
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.data.CompanySummary
import com.bamnat.app.ui.pay.hourlyWeek
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.PatternTimeline
import com.bamnat.core.pay.WageType
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import com.bamnat.core.time.Today

/** Terms are the same for every screen and rarely change: read once per run. */
private object TermsCache {
    @Volatile var value: CompanyTerms? = null
}

/** [SPAT-05] The terms last read, for a card that does not ask the server itself. */
internal fun cachedTerms(): CompanyTerms? = TermsCache.value

/** [SPD-13] The terms this run read, else the ones kept on the phone (a screen starts with them, no request). */
private fun AppContainer.termsNow(): CompanyTerms? = TermsCache.value ?: companySearch.cache.terms()?.value

/**
 * [SPD-13] 1.30 AP1: the terms, read once a run and kept on the phone for a day (they change rarely): a run within a day of the
 * last read asks nothing. Without an answer, the kept ones however old.
 */
private suspend fun AppContainer.loadTerms(): CompanyTerms? {
    TermsCache.value?.let { return it }
    val cache = companySearch.cache
    cache.load()
    val kept = cache.terms()
    if (kept != null && cache.fresh(kept, com.bamnat.app.company.PageCache.DAY_MS)) return kept.value.also { TermsCache.value = it }
    return runCatching { companyApi.terms() }.getOrNull()?.also { TermsCache.value = it; cache.keepTerms(it) } ?: kept?.value
}

/** [COMM-01] 회사 찾기: the name as it is typed, what the server found and the way on when nothing fits. */
@OptIn(FlowPreview::class)
class CompanyFindViewModel(
    private val c: AppContainer,
    /** [CALI-03] 1.30 BB: the calendar the company is looked up for. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    sealed interface Result {
        /** Fewer than two letters typed. */
        data object Idle : Result
        data object Searching : Result
        data class Found(val query: String, val hits: List<CompanyHit>) : Result
        data class Failed(val query: String, val code: String) : Result
    }

    val query = MutableStateFlow("")
    /** [SPAT-02] Whether coworkers' setups are searched too; without it only the public cycles are. */
    val server: Boolean get() = c.companyApi.enabled
    private val again = MutableStateFlow(0)
    private val _result = MutableStateFlow<Result>(Result.Idle)
    val result: StateFlow<Result> = _result.asStateFlow()
    private val _terms = MutableStateFlow(c.termsNow())
    val terms: StateFlow<CompanyTerms?> = _terms.asStateFlow()
    /** [SRCH-10] 1.29 AS: where the server's answer stands while companies are already on screen (asking, slow, failed). */
    private val _stage = MutableStateFlow<com.bamnat.app.company.CompanySearch.State?>(null)
    val stage: StateFlow<com.bamnat.app.company.CompanySearch.State?> = _stage.asStateFlow()

    init {
        // [SPD-12] 1.30 AP1: the whole list of companies is read now if the start of the app did not yet.
        c.warmCompanies()
        viewModelScope.launch { _terms.value = c.loadTerms() }
        // [SPD-15] What is found and stays a moment: its first companies are read ahead, so a tap opens the page at once.
        viewModelScope.launch {
            _result.collectLatest { r ->
                if (r !is Result.Found || r.hits.isEmpty()) return@collectLatest
                delay(PREFETCH_AFTER_MS)
                c.prefetchCompanies(r.hits.map { it.id })
            }
        }
        viewModelScope.launch {
            // AS 1.29 [SRCH-07][SRCH-11] Through CompanySearch: what the phone has at once, the server's answer when it comes.
            // Before 1.29 a new letter waited here for the last letter's request to end (collectLatest waits for the block it
            // cancels, and a connection does not hear that), so a slow answer held every next search back by its own length.
            combine(query, again) { q, n -> q.trim() to n }.collectLatest { (q, n) ->
                if (CompanyText.norm(q).length < 2) {
                    _result.value = Result.Idle
                    _stage.value = null
                    return@collectLatest
                }
                if (n > 0) c.companySearch.forgetAnswer(q)
                // [SPD-17] 찾기 시작 → 첫 결과, timed on this phone.
                val started = com.bamnat.app.company.NetTimings.start()
                var timed = false
                c.companySearch.states(q).collect { s ->
                    val shown = when {
                        s.stage == com.bamnat.app.company.CompanySearch.Stage.DONE -> Result.Found(q, s.hits)
                        s.hits.isNotEmpty() -> Result.Found(q, s.hits)
                        s.stage == com.bamnat.app.company.CompanySearch.Stage.FAILED -> Result.Failed(q, s.code ?: "offline")
                        // [SRCH-10] The last companies stay on screen while the next letter is asked about.
                        else -> (_result.value as? Result.Found)?.takeIf { it.hits.isNotEmpty() && q.startsWith(it.query) }
                            ?.let { prev -> Result.Found(q, prev.hits.filter { h -> CompanyText.norm(h.name).contains(CompanyText.norm(q)) }) }
                            ?.takeIf { it.hits.isNotEmpty() }
                            ?: Result.Searching
                    }
                    _stage.value = s.takeIf { shown is Result.Found && it.stage != com.bamnat.app.company.CompanySearch.Stage.DONE }
                    _result.value = shown
                    if (!timed && shown is Result.Found && shown.hits.isNotEmpty()) {
                        timed = true
                        com.bamnat.app.company.NetTimings.end(com.bamnat.app.company.NetTimings.Wait.FIND, started)
                    }
                }
            }
        }
    }

    fun retry() {
        again.value++
    }

    private companion object {
        /** [SPD-15] How long a list stays before its first companies are read ahead (not each letter's list). */
        const val PREFETCH_AFTER_MS = 400L
    }

    /** Remembers the company typed for the setup by hand, so the user can share it once it is done. */
    fun byHand(name: String) {
        val n = name.trim().take(40)
        if (CompanyText.norm(n).length >= 2) c.companyOf(calendar).store.setDraft(CompanyDraft(n))
        c.telemetry.event("company_by_hand", if (n.isEmpty()) "blank" else "named")
    }
}

/** One choice of where today falls in a shared rotation: the next week from it. */
data class Phase(val step: Int, val days: List<ShiftType?>)

/**
 * A shared rotation and today's place in it, for "오늘은 어떤 근무예요?". [current] is the row the
 * calendar already shows; [sent] the row of whoever sent the file, with their [team] when they wrote it.
 */
data class Phases(
    val options: List<Phase>,
    val cycle: Int,
    val current: Int?,
    val sent: Int? = null,
    val team: String? = null,
    val hasPattern: Boolean = false,
)

/**
 * [COMM-01] The rotation of a company file as rows of the next seven days, one per place today could
 * be in it. [fromSender] marks the row the sender's own start day gives [SETUP-02]: right for the same 조.
 */
internal suspend fun rotationPhases(c: AppContainer, text: String, fromSender: Boolean, today: LocalDate = Today.date(),
    /** [CALI-03] The calendar it would go into, whose order says where today falls. */
    calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN): Phases? {
    val root = JSONObject(text)
    val rotation = root.optJSONObject("rotation") ?: return null
    val steps = rotation.optJSONArray("steps") ?: return null
    val names = (0 until steps.length()).map { if (steps.isNull(it)) null else steps.getString(it) }
    if (names.isEmpty()) return null
    val snapshot = c.calendars.repository(calendar).snapshot.first()
    // The colors and marks the user already gave a shift of the same name, else the file's own.
    val mine = snapshot.shiftTypes.associateBy { it.name }
    val types = root.optJSONArray("shiftTypes")?.let { a ->
        (0 until a.length()).map { a.getJSONObject(it) }.associate { o ->
            val name = o.getString("name")
            name to (mine[name] ?: ShiftType(
                id = 0, name = name, label = o.optString("label").ifEmpty { name.take(1) },
                colorArgb = o.optLong("color", 0xFF5C6BC0), start = null, end = null, isOff = o.optBoolean("off"),
                icon = if (o.has("icon") && !o.isNull("icon")) o.getString("icon") else null,
            ))
        }
    }.orEmpty()
    val n = names.size
    val orderFrom = { i: Int -> (0 until n).map { names[(i + it) % n] } }
    val seen = HashSet<List<String?>>()
    val options = (0 until n).mapNotNull { i ->
        if (!seen.add(orderFrom(i))) return@mapNotNull null
        Phase(i, (0 until 7).map { d -> names[(i + d) % n]?.let(types::get) })
    }
    // The user's own order, not the calendar with its swaps and leave, says where today falls.
    val typeNames = snapshot.shiftTypes.associate { it.id to it.name }
    val week = (0 until 7).map { d ->
        val date = today.plusDays(d.toLong())
        PatternTimeline.on(snapshot.patterns, date)?.shiftTypeIdOn(date)?.let(typeNames::get)
    }
    val current = if (snapshot.hasPattern) options.firstOrNull { o -> o.days.map { it?.name } == week }?.step else null
    val anchor = rotation.optString("anchor").takeIf { fromSender && it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    val sent = anchor?.let { a ->
        val order = orderFrom(Math.floorMod(java.time.temporal.ChronoUnit.DAYS.between(a, today), n.toLong()).toInt())
        options.firstOrNull { orderFrom(it.step) == order }?.step
    }
    val team = rotation.optString("team").trim().take(CompanyShare.MAX_TEAM).takeIf { fromSender && it.isNotEmpty() }
    return Phases(options, n, current, sent, team, snapshot.hasPattern)
}

/** [COMM-01] A company's shared setups, and applying one from today's place in its rotation. */
class CompanyDetailViewModel(
    private val c: AppContainer,
    private val id: String,
    /** [CALI-03] 1.30 BB: the calendar a setup chosen here goes into, with its own pay and company. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    sealed interface State {
        data object Loading : State
        data class Loaded(val page: CompanyPage) : State
        data class Failed(val code: String) : State
    }

    /** [CALI-03] The calendar's company things. */
    private val scope: com.bamnat.app.company.CompanyScope get() = c.companyOf(calendar)

    private val _state = MutableStateFlow<State>(State.Loading)
    val state: StateFlow<State> = _state.asStateFlow()
    val applied: StateFlow<AppliedSetup?> = scope.store.applied
    val hasPattern: StateFlow<Boolean?> = MutableStateFlow<Boolean?>(null).also { f ->
        viewModelScope.launch { f.value = scope.schedule.snapshot.first().hasPattern }
    }
    val hasPay: Boolean get() = scope.pay.profile.value != null

    // [PLAZA-09] Before init: load() fills it.
    private val _room = MutableStateFlow<Room?>(null)
    val room: StateFlow<Room?> = _room.asStateFlow()

    /** [SPD-13] The page on screen is being read again (a thin line on top, not a spinner). */
    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()
    /** [SPD-13] Reading again failed: the page is the one kept from before ("최근 본 내용이에요 · 다시 시도"). */
    private val _stale = MutableStateFlow(false)
    val stale: StateFlow<Boolean> = _stale.asStateFlow()

    init {
        load()
    }

    /** [SRCH-13] The page on screen is the one kept from before, still being read again. */
    @Volatile private var fromKept = false
    /** [SPD-14] The read on its way: 이 설정 쓰기 waits for it rather than reading the page again. */
    private var reading: kotlinx.coroutines.Job? = null

    /**
     * AS 1.29 [SRCH-09][SRCH-13] The page as last read (or read ahead) shows at once, then afresh.
     * [SPD-13][SPD-14] 1.30 AP1: kept on the phone between runs ([com.bamnat.app.company.PageCache]), and read again with the
     * room in one request ([com.bamnat.app.company.bundle]; 1.29 read a member's room twice beside the page).
     */
    fun load() {
        val started = com.bamnat.app.company.NetTimings.start()
        val cache = c.companySearch.cache
        val keptNow = cache.bundle(id)
        if (keptNow != null) show(keptNow) else if (_state.value !is State.Loaded) _state.value = State.Loading
        fromKept = _state.value is State.Loaded
        _refreshing.value = true
        _stale.value = false
        reading?.cancel()
        reading = viewModelScope.launch {
            if (keptNow == null && !cache.loaded) {
                cache.load()
                cache.bundle(id)?.let { show(it); fromKept = true }
            }
            val atOnce = _state.value is State.Loaded
            com.bamnat.app.company.NetTimings.cached(atOnce)
            if (atOnce) com.bamnat.app.company.NetTimings.end(com.bamnat.app.company.NetTimings.Wait.COMPANY, started)
            try {
                val b = c.companyApi.bundle(id, c.companyStore.device)
                cache.keepBundle(b)
                show(b)
                fromKept = false
                if (!atOnce) com.bamnat.app.company.NetTimings.end(com.bamnat.app.company.NetTimings.Wait.COMPANY, started)
            } catch (e: CompanyException) {
                if (_state.value is State.Loaded) _stale.value = true else _state.value = State.Failed(e.code)
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** [SPD-13] A page with its room on screen; the room's cards are worked out off the screen's thread. */
    private fun show(b: com.bamnat.app.company.CompanyBundle) {
        _state.value = State.Loaded(b.page)
        val r = b.room?.takeIf { b.page.member }
        if (r == null) {
            if (!b.page.member) _room.value = null
            return
        }
        viewModelScope.launch {
            val cards = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { roomCards(r) }
            _room.value = Room(r, cards, _room.value?.taken.orEmpty())
        }
    }

    /**
     * [PLAZA-09] 회사 방 for a member (결정 D79): the version in use with its state (제보 1 · 확인 중, 대표 판, 안정) and only what
     * the others changed against this phone's own setup, as cards with 이 값 쓰기.
     */
    data class Room(val page: com.bamnat.app.company.RoomPage, val cards: List<com.bamnat.core.plaza.RoomCard>, val taken: Set<String> = emptySet())

    private suspend fun loadRoom() {
        val page = (_state.value as? State.Loaded)?.page ?: return
        if (!page.member) { _room.value = null; return }
        val r = runCatching { c.companyApi.room(c.companyStore.device, page.id) }.getOrNull() ?: run { _room.value = null; return }
        _room.value = Room(r, roomCards(r), _room.value?.taken.orEmpty())
        c.companySearch.cache.bundle(page.id)?.let { c.companySearch.cache.keepBundle(it.copy(room = r)) }
    }

    private suspend fun roomCards(r: com.bamnat.app.company.RoomPage): List<com.bamnat.core.plaza.RoomCard> {
        val mine = runCatching { scope.share.exportForServer().toString() }.getOrNull() ?: return emptyList()
        val versions = (r.others + listOfNotNull(r.base?.takeIf { !r.mine }))
            .map { com.bamnat.core.plaza.RoomCards.Version(it.setup, it.text, maxOf(it.senders, it.persons)) }
        return com.bamnat.core.plaza.RoomCards.cards(mine, versions)
    }

    /** 이 값 쓰기: one value of the version behind [card], the rest of the user's setup as it was. */
    fun take(card: com.bamnat.core.plaza.RoomCard, done: (String, Boolean) -> Unit) {
        val room = _room.value ?: return
        val from = (room.page.others + listOfNotNull(room.page.base)).firstOrNull { it.setup == card.from } ?: return
        viewModelScope.launch {
            val ok = try {
                scope.share.takeOne(from.text, card.key)
            } catch (e: BackupException) {
                false
            } catch (e: org.json.JSONException) {
                false
            }
            if (ok) {
                c.telemetry.event("plaza_take", card.key.substringBefore(':'))
                // [PLAZA-07] A change of the user's own goes to the plaza too, after the one-time yes.
                scope.autoShare.saved()
                _room.value = Room(room.page, roomCards(room.page), room.taken + "${card.key}=${card.after}")
            }
            done(if (ok) "${card.label}${com.bamnat.app.ui.Fmt.obj(card.label)} 바꿨어요. 내 금액은 그대로예요" else "이 값은 따로 쓸 수 없어요. 판 전체를 쓰면 바뀌어요", ok)
        }
    }

    /** The rotation of [setup] as rows of the next seven days, with today's row when the calendar already shows it. */
    suspend fun phases(setup: SharedSetup): Phases? = rotationPhases(c, setup.text, fromSender = false, calendar = calendar)

    /**
     * Applies [setup] with today at [todayStep] of its rotation. What is still to do comes back:
     * the user's own amounts when there was no pay setup yet.
     */
    fun apply(page: CompanyPage, setup: SharedSetup, todayStep: Int?, done: (CompanyShare.Applied) -> Unit, failed: (String) -> Unit) =
        apply(page, setup, todayStep, withPay = true, done = done, failed = failed)

    /**
     * [PAYF-03] 1.29 AP: [withPay] false puts in the cycle alone ("근무주기만 넣을게요"): the setup's pay part is left out, and the
     * pay the user has stays as it is. With the pay, the way is remembered for 맞아요·달라요 on the pay screen and the 기본급 is
     * asked next ([com.bamnat.app.company.PayWayStore.askBase]).
     */
    fun apply(page: CompanyPage, setup: SharedSetup, todayStep: Int?, withPay: Boolean, done: (CompanyShare.Applied) -> Unit, failed: (String) -> Unit) {
        viewModelScope.launch {
            try {
                // [CALI-03] Everything below is the calendar's: its shifts and pay, its company, `cal` on the server.
                val scope = scope
                val store = scope.store
                // [PLAZA-07] 결정 D79: applying is joining. The pay way comes only then, so the page is read again as a member.
                var text = setup.text
                // [SRCH-13] A page kept from before is read again first, so what goes in is the version as it is now.
                // [SPD-14] 1.30 AP1: the read the screen started is waited for, not made twice; only when it failed is it tried again.
                if (fromKept && !setup.payHidden && page.member) {
                    reading?.join()
                    val now = if (fromKept || _stale.value) c.companyApi.company(page.id).also(c.companySearch::keepPage)
                        else (_state.value as? State.Loaded)?.page
                    now?.setups?.firstOrNull { it.id == setup.id }?.takeIf { !it.payHidden }?.let { text = it.text }
                }
                if (setup.payHidden || !page.member) {
                    val m = c.companyApi.join(store.device, page.id, scope.serverCal)
                    if (!m.joined) {
                        failed(companyProblem(m.error ?: "member"))
                        return@launch
                    }
                    store.setMember(page.id, page.name)
                    // [SPD-16] 1.30 AP1: the member's page came with the join (SP1); a server before 1.30 is asked as before.
                    val again = (m.page ?: c.companyApi.company(page.id)).also(c.companySearch::keepPage)
                    _state.value = State.Loaded(again)
                    again.setups.firstOrNull { it.id == setup.id }?.takeIf { !it.payHidden }?.let { text = it.text }
                }
                // [PAYG-03] 1.30 BA: on a pay that has its amounts, the cycle goes in now and the pay waits for its 기본급 on the
                // pay screen (PendingWay), put in all at once there or not at all; a first pay is made on the amounts screen.
                val payWaits = withPay && scope.pay.profile.value != null && runCatching { org.json.JSONObject(text).has("pay") }.getOrDefault(false)
                val full = text
                if (!withPay || payWaits) text = org.json.JSONObject(text).apply { remove("pay") }.toString()
                val result = scope.share.apply(text, rotation = true, todayStep = todayStep).let { r -> if (payWaits) r.copy(rules = true) else r }
                if (payWaits) {
                    com.bamnat.app.company.PendingWays.of(scope).offer(com.bamnat.app.company.PendingWay(page.id, page.name, setup.dept, setup.id, full,
                        com.bamnat.app.company.PendingWay.Source.APPLY))
                    scope.payWays.askBase(true)
                } else if (withPay && result.pendingPay) {
                    // The amounts screen does not ask "쓸까요?" again; the way is kept when its amounts are saved.
                    scope.payWays.decided = true
                }
                // [PLZ-08] 쓰는 사람: this calendar now uses this rule set, and no longer the one before (by the company's id; 1.29 sent its name).
                val before = store.applied.value
                if (before != null && before.setup != setup.id) {
                    launch { runCatching { c.companyApi.use(store.device, before.companyId, before.setup, false, scope.serverCal) } }
                }
                launch { runCatching { c.companyApi.use(store.device, page.id, setup.id, true, scope.serverCal) } }
                store.setApplied(AppliedSetup(page.id, page.name, setup.dept, setup.id, setup.version, setup.adopted, Today.instant()))
                store.setDraft(null)
                // [PLAZA-32] 1.27 AI: a member already (page.member) is one here too, so 광장 opens to this company's room.
                store.setMember(page.id, page.name)
                // [PLAZA-34] What was just put in is what the plaza has: saving it again asks nothing.
                scope.autoShare.rebase()
                store.pendingPay = if (result.pendingPay && !payWaits) text else null
                c.telemetry.event("company_apply", if (setup.adopted) "adopted" else "pending")
                // [SPD-16] The next screen opens now; the room (for 고친 점) is read after it, not before.
                done(result)
                loadRoom()
            } catch (e: CompanyException) {
                failed(companyProblem(e.code, e.field))
            } catch (e: BackupException) {
                failed(e.message ?: "설정을 넣지 못했어요")
            } catch (e: org.json.JSONException) {
                // A setup the server sent that this app cannot read is a message, not a crash.
                failed("이 설정을 읽지 못했어요. 앱을 업데이트한 뒤 다시 해 주세요")
            }
        }
    }

    /** The company found, but not the user's own department: they set up by hand under its name. */
    fun byHand(page: CompanyPage) = scope.store.setDraft(CompanyDraft(page.name))

    /** [COMM-07] What this phone said about each setup on this screen: 맞았어요 (true) or 달라요. */
    private val _votes = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    val votes: StateFlow<Map<String, Boolean>> = _votes.asStateFlow()

    /** 맞았어요 or 달라요 on any setup here, applied or not; the page comes back with the new points and trust. */
    fun vote(page: CompanyPage, setup: SharedSetup, ok: Boolean, why: String? = null, done: (String?) -> Unit = {}) {
        viewModelScope.launch {
            val error = try {
                val r = c.companyApi.vote(c.companyStore.device, page.id, setup.id, ok, why = why)
                _votes.value = _votes.value + (setup.id to ok)
                if (scope.store.applied.value?.setup == setup.id) scope.store.voted(ok)
                c.telemetry.event("company_vote", if (ok) "ok_board" else why ?: "wrong")
                // [SPD-16] 1.30 AP1: the counts the vote came back with show at once; the page is read again after.
                (_state.value as? State.Loaded)?.page?.let { now ->
                    _state.value = State.Loaded(now.copy(setups = now.setups.map { s ->
                        if (s.id != setup.id) s else s.copy(points = r.points, persons = r.persons, bad = r.bad, threshold = r.threshold,
                            adopted = s.adopted || r.adopted, trust = if (r.trust > 0) r.trust else s.trust, agree = if (r.agree > 0) r.agree else s.agree,
                            opinion = s.opinion.copy(mine = if (ok) "ok" else why ?: "wrong"))
                    }))
                }
                null
            } catch (e: CompanyException) {
                companyProblem(e.code, e.field)
            }
            done(error)
            if (error == null) runCatching { c.companyApi.company(id).also(c.companySearch::keepPage) }.getOrNull()?.let { _state.value = State.Loaded(it) }
        }
    }

    /** [COMM-06] The talk on one setup, while its sheet is open. */
    val talk = TalkHolder(c, viewModelScope, calendar)

    /** [PLZ-10] 내 월급으로 시험 of one setup, while its sheet is open. */
    private val _trial = MutableStateFlow<Trial?>(null)
    val trial: StateFlow<Trial?> = _trial.asStateFlow()

    fun tryPay(setup: SharedSetup, month: YearMonth = Today.month()) {
        _trial.value = Trial(setup.id, month, loading = true)
        viewModelScope.launch { _trial.value = payTrial(c, setup.text, setup.id, month, calendar) }
    }

    fun closeTrial() {
        _trial.value = null
    }

    /** [PLZ-04] The versions of one department's rule, while its sheet is open. */
    private val _history = MutableStateFlow<HistoryState?>(null)
    val history: StateFlow<HistoryState?> = _history.asStateFlow()

    fun showHistory(page: CompanyPage, setup: SharedSetup) {
        _history.value = HistoryState(setup.dept.ifEmpty { "회사 전체" }, null)
        viewModelScope.launch {
            _history.value = try {
                HistoryState(setup.dept.ifEmpty { "회사 전체" }, c.companyApi.history(page.id, setup.id))
            } catch (e: CompanyException) {
                HistoryState(setup.dept.ifEmpty { "회사 전체" }, null, companyProblem(e.code, e.field))
            }
        }
    }

    fun closeHistory() {
        _history.value = null
    }

    /** [PLAZA-02] This phone's own setup (amounts left out), for "내 설정으로 채우기"; null when there is none to read. */
    suspend fun mine(): String? = runCatching { scope.share.exportForServer().toString() }.getOrNull()

    /**
     * [PLAZA-02] 다른 점 말하기 sent: a proposal on [setup] with the [lines] that changed and the edited [text] as the
     * rule people vote on. The talk opens on it, so the user sees it went up.
     */
    fun propose(page: CompanyPage, setup: SharedSetup, lines: List<com.bamnat.app.company.ChangeLine>, text: String, why: String, done: (String?) -> Unit) {
        talk.show(page.id, setup.id, "${page.name} · ${setup.dept.ifEmpty { "회사 전체" }}")
        val body = proposalBody(com.bamnat.core.plaza.SetupCompare.rows(setup.text, text), why)
        talk.send(body, kind = "proposal", change = lines, setupText = text) { e ->
            if (e == null) c.telemetry.event("plaza_diff", "sent") else talk.close()
            done(e)
        }
    }
}

data class HistoryState(val title: String, val versions: List<com.bamnat.app.company.RuleVersion>?, val error: String? = null)

/**
 * [PLZ-10] One month's pay worked out on the phone twice, with the user's own rules and with a shared
 * setup's, line by line. Nothing leaves the phone (결정 D36); [note] says why it could not be done.
 */
data class Trial(
    val setup: String,
    val month: YearMonth,
    val loading: Boolean = false,
    val now: Long = 0,
    val then: Long = 0,
    val lines: List<TrialLine> = emptyList(),
    val note: String? = null,
) {
    val diff: Long get() = then - now
}

data class TrialLine(val label: String, val now: Long, val then: Long)

internal suspend fun payTrial(c: AppContainer, text: String, setup: String, month: YearMonth,
    /** [CALI-03] Whose pay and shifts it is tried on. */
    calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN): Trial {
    val scope = c.companyOf(calendar)
    val mine = scope.pay.profile.value ?: return Trial(setup, month, note = "내 급여 설정이 없어 비교할 수 없어요. 급여 설정을 먼저 해 주세요")
    val trial = try {
        scope.share.trial(text)
    } catch (e: Exception) {
        null
    } ?: return Trial(setup, month, note = "이 규칙에는 급여 계산 방식이 없어 근무표만 비교할 수 있어요")
    val snapshot = scope.schedule.snapshot.first()
    val settings = c.settingsRepository.current
    val calc = com.bamnat.core.pay.PayCalculator()
    return runCatching {
        val a = calc.calculate(mine.hourlyWeek(settings), month, snapshot.resolver::resolve, checks = false)
        val b = calc.calculate(trial.hourlyWeek(settings), month, snapshot.resolver::resolve, checks = false)
        val nowBy = a.lines.groupBy { it.label }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val thenBy = b.lines.groupBy { it.label }.mapValues { (_, l) -> l.sumOf { it.amount } }
        val labels = (nowBy.keys + thenBy.keys).toList()
        Trial(setup, month, now = a.total, then = b.total,
            lines = labels.map { TrialLine(it, nowBy[it] ?: 0, thenBy[it] ?: 0) }.sortedByDescending { kotlin.math.abs(it.then - it.now) })
    }.getOrElse { Trial(setup, month, note = "이 달은 계산하지 못했어요") }
}

/**
 * [COMM-06] The talk on one rule set: its posts, writing one, taking one's own back and 신고. Shared by
 * the company screen and 회사 규칙 광장.
 */
class TalkHolder(
    private val c: AppContainer,
    private val scope: kotlinx.coroutines.CoroutineScope,
    /** [CALI-03] The calendar whose setup goes with a post that carries one. */
    private val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) {
    private companion object {
        /** [COMM-13] What this phone posted lately, in memory for as long as the app runs; never stored. */
        @Volatile var sentHere: List<com.bamnat.core.plaza.RepeatGuard.Sent> = emptyList()
        /** [SPD-16] A post on its way, shown as "나 (보내는 중)". */
        const val SENDING = "보내는 중"
    }
    data class Open(
        val company: String, val setup: String, val title: String, val talk: com.bamnat.app.company.Talk? = null, val error: String? = null,
        /** [PLAZA-32][PLAZA-33] 1.27 AI: a company's or a kind of work's room (setup "room"), with its own hint and note. */
        val hint: String? = null,
        val note: String? = null,
        /** [SPD-13] The talk on screen is the one kept on the phone, being read again (a thin line on top). */
        val refreshing: Boolean = false,
    ) {
        val room: Boolean get() = setup == com.bamnat.core.plaza.StylePlaza.ROOM
    }

    private val _open = MutableStateFlow<Open?>(null)
    val open: StateFlow<Open?> = _open.asStateFlow()

    /**
     * [SPD-14] Whether this phone changed the talk (wrote, took back, voted, hid) since it was shown: the screen under the sheet
     * takes [last] when it closes instead of reading everything again.
     */
    @Volatile var changed: Boolean = false
        private set
    /** [SPD-14] The talk as last read, kept after the sheet closes. */
    @Volatile var last: com.bamnat.app.company.Talk? = null
        private set

    /**
     * [SPD-13] 1.30 AP1: the talk kept on the phone ([com.bamnat.app.company.PageCache]; a room's also from its company's page)
     * shows at once, without a spinner, while it is read again.
     */
    fun show(company: String, setup: String, title: String, hint: String? = null, note: String? = null) {
        val kept = c.companySearch.cache.talk(company, setup)?.value
        changed = false
        last = kept
        com.bamnat.app.company.NetTimings.cached(kept != null)
        _open.value = Open(company, setup, title, talk = kept, hint = hint, note = note, refreshing = kept != null)
        reload()
    }

    fun close() {
        _open.value = null
    }

    /** [SRV-04] The developer paused writing in the plaza: the talk is read only for now. */
    val writePaused: Boolean get() = c.flags.paused(com.bamnat.core.ops.ServerSwitch.PLAZA_WRITE)

    /** [COMM-09] 이 사람 글 안 보기: the writer's posts in every talk go, on this phone only. */
    fun hide(p: com.bamnat.app.company.TalkPost) {
        val mark = p.author ?: return
        c.settingsRepository.update { s ->
            s.copy(plazaHidden = com.bamnat.core.plaza.HideList.decode(s.plazaHidden).hide(mark, p.nick ?: "참여자 ${p.who}", Today.millis()).encode())
        }
        c.telemetry.event("plaza_hide")
        changed = true
        reload()
    }

    fun reload() {
        val o = _open.value ?: return
        scope.launch {
            val next = try {
                val t = c.companyApi.talk(c.companyStore.device, o.company, o.setup)
                c.companySearch.cache.keepTalk(o.company, o.setup, t)
                if (o.company == _open.value?.company || _open.value == null) last = t
                o.copy(talk = t, error = null, refreshing = false)
            } catch (e: CompanyException) {
                // [SPD-13] What was kept stays on screen; the problem shows only when there is nothing to show.
                o.copy(error = if (o.talk != null) null else companyProblem(e.code, e.field), refreshing = false)
            }
            val now = _open.value
            if (now?.company == o.company && now.setup == o.setup) _open.value = next.copy(talk = next.talk ?: now.talk)
        }
    }

    /**
     * Sends a post; [done] gets what to tell the user when it was not taken. [parent] answers a post,
     * [kind] is talk, question or proposal; a proposal carries its [change] lines and, with [withSetup],
     * this phone's own setup (amounts left out) as the rule should be ([PLZ-09]).
     */
    fun send(
        text: String,
        parent: String? = null,
        kind: String = "talk",
        change: List<com.bamnat.app.company.ChangeLine> = emptyList(),
        withSetup: Boolean = false,
        setupText: String? = null,
        done: (String?) -> Unit,
    ) {
        val o = _open.value ?: return done(null)
        CompanyText.personal(text)?.let { return done(talkProblem(it)) }
        change.flatMap { listOf(it.now, it.want, it.why) }.firstNotNullOfOrNull(CompanyText::personal)?.let { return done(talkProblem(it)) }
        // [COMM-13] 같은 글 되풀이 is held back here, before the server's own limits.
        val talkKey = "${o.company}/${o.setup}"
        val now = com.bamnat.core.time.Today.instant()
        if (!com.bamnat.core.plaza.RepeatGuard.allowed(sentHere, talkKey, text, now)) return done(com.bamnat.core.plaza.RepeatGuard.WHY)
        // [SPD-16] 1.30 AP1: the post shows in the talk at once ("나 (보내는 중)") and the server's talk takes its place after.
        val pending = com.bamnat.app.company.TalkPost("local-${now.toEpochMilli()}", 0, null, false, text.trim(), now, mine = true, nick = SENDING,
            parent = parent, kind = kind, change = change)
        _open.value?.takeIf { it.company == o.company && it.setup == o.setup && it.talk != null }?.let { cur ->
            _open.value = cur.copy(talk = cur.talk!!.copy(posts = cur.talk.posts + pending))
        }
        scope.launch {
            val error = try {
                // [PLAZA-02] 다른 점 말하기 sends the company's setup as edited, not this phone's own.
                val setup = if (kind != "proposal") null
                else setupText?.let { org.json.JSONObject(it) } ?: if (withSetup) c.companyOf(calendar).share.exportForServer() else null
                if (parent == null && kind == "talk") c.companyApi.post(c.companyStore.device, o.company, o.setup, text)
                else c.companyApi.post(c.companyStore.device, o.company, o.setup, text, parent, kind, change, setup)
                c.telemetry.event("company_post", if (kind == "proposal") "proposal" else if (parent != null) "reply" else "sent")
                sentHere = com.bamnat.core.plaza.RepeatGuard.record(sentHere, talkKey, text, now)
                changed = true
                null
            } catch (e: CompanyException) {
                talkProblem(e.code)
            }
            if (error != null) {
                _open.value?.let { cur -> cur.talk?.let { t -> _open.value = cur.copy(talk = t.copy(posts = t.posts.filter { it.id != pending.id })) } }
            }
            done(error)
            if (error == null) reload()
        }
    }

    /** [PLZ-01] 맞아요 (true) or 달라요 (false) on someone's post; the same again takes it back. */
    fun vote(id: String, ok: Boolean, done: (String?) -> Unit = {}) {
        val o = _open.value ?: return
        val post = o.talk?.posts?.firstOrNull { it.id == id } ?: return
        val next = if (post.myVote == ok) null else ok
        scope.launch {
            val error = try {
                val v = c.companyApi.postVote(c.companyStore.device, id, next)
                val now = _open.value
                if (now?.setup == o.setup && now.talk != null) {
                    _open.value = now.copy(talk = now.talk.copy(posts = now.talk.posts.map {
                        if (it.id == id) it.copy(agree = v.agree, disagree = v.disagree, trust = v.trust, myVote = v.myVote) else it
                    }))
                }
                changed = true
                // A proposal people agree with can be adopted by this vote; its state comes back with the talk.
                if (post.proposal != null) reload()
                null
            } catch (e: CompanyException) {
                talkProblem(e.code)
            }
            done(error)
        }
    }

    fun delete(id: String) {
        scope.launch {
            runCatching { c.companyApi.deletePost(c.companyStore.device, id) }
            changed = true
            reload()
        }
    }

    fun flag(id: String, reason: String, done: (String?) -> Unit) {
        scope.launch {
            val error = try {
                c.companyApi.flag(c.companyStore.device, id, reason)
                null
            } catch (e: CompanyException) {
                talkProblem(e.code)
            }
            done(error)
            reload()
        }
    }
}

/** What to say when a post is refused. */
fun talkProblem(code: String): String = when (code) {
    "amount" -> "금액은 빼 주세요. ‘기본급의 10%’, ‘1.5배’처럼 비율로 적으면 돼요"
    "phone" -> "전화번호는 빼 주세요"
    "email" -> "메일 주소는 빼 주세요"
    "body" -> "두 글자 이상, 500자 안으로 적어 주세요"
    "muted" -> "운영 원칙을 지키지 않은 글이 있어 지금은 글을 쓸 수 없어요"
    "rate_limited" -> "오늘은 글을 더 쓸 수 없어요. 내일 다시 써 주세요"
    "own" -> "내 글에는 할 수 없어요"
    "parent" -> "답글을 달 글이 지워졌어요"
    "change" -> "무엇을 어떻게 바꾸자는지 한 줄 이상 적어 주세요"
    "same" -> "지금 규칙과 같은 설정이에요. 앱 설정을 실제와 맞게 고친 뒤 보내 주세요"
    "paused" -> com.bamnat.core.ops.ServerSwitch.pausedText(com.bamnat.core.ops.ServerSwitch.PLAZA_WRITE)
    else -> companyProblem(code)
}

/** [COMM-07] 회사 규칙 광장: every company's rule sets, ones being voted on or adopted ones, a page at a time. */
class CompanyBoardViewModel(
    private val c: AppContainer,
    /** [CALI-08] The calendar the plaza is opened for: its sent rules in 내 활동, its setup with a post. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    /**
     * [SPD-13] [fromCache]: the first page shown is the one kept on the phone, while it is read again ([loading]: a thin line on
     * top, the cards stay); [stale] when reading it again failed.
     */
    data class Tab(val items: List<com.bamnat.app.company.BoardItem> = emptyList(), val next: Int? = 0, val loading: Boolean = false, val error: String? = null,
        val fromCache: Boolean = false, val stale: Boolean = false)

    val enabled: Boolean = c.companyApi.enabled
    private val _adopted = MutableStateFlow(false)
    val adopted: StateFlow<Boolean> = _adopted.asStateFlow()
    private val tabs = mapOf(false to MutableStateFlow(Tab()), true to MutableStateFlow(Tab()))
    val voting: StateFlow<Tab> = tabs.getValue(false).asStateFlow()
    val chosen: StateFlow<Tab> = tabs.getValue(true).asStateFlow()
    private val _terms = MutableStateFlow(c.termsNow())
    val terms: StateFlow<CompanyTerms?> = _terms.asStateFlow()
    val talk = TalkHolder(c, viewModelScope, calendar)
    private val _diffs = MutableStateFlow<Map<String, String>>(emptyMap())
    /** [COMM-14] For cards of one company and department side by side: what only that one has, by setup id. */
    val diffs: StateFlow<Map<String, String>> = _diffs.asStateFlow()
    private val readPages = HashSet<String>()

    /** [COMM-07] A company's name, as typed; both tabs show only its rules once the typing stops. */
    // [COMM-12] Opened from the 급여 screen's "같은 회사 분들 규칙 보기": that company's name, typed already.
    val query = MutableStateFlow(PlazaEntry.takeQuery())
    private var asked = ""

    /** [PLZ-12][PLZ-05] The job plaza and 시·도 shown (empty for all), and the order. */
    data class Filter(val job: String = "", val region: String = "", val sort: String = "recent")
    private val _filter = MutableStateFlow(Filter())
    val filter: StateFlow<Filter> = _filter.asStateFlow()
    private val _plazas = MutableStateFlow<List<com.bamnat.app.company.PlazaJob>>(emptyList())
    val plazas: StateFlow<List<com.bamnat.app.company.PlazaJob>> = _plazas.asStateFlow()

    fun setFilter(f: Filter) {
        if (f == _filter.value) return
        _filter.value = f
        tabs.values.forEach { it.value = Tab() }
        more()
    }

    /** [PLZ-08] 내 활동 and the plaza name, while its sheet is open. */
    val activity = ActivityHolder(c, viewModelScope, calendar)

    init {
        c.warmCompanies()
        viewModelScope.launch { _terms.value = c.loadTerms() }
        // [SPD-13] The job plazas kept on the phone show at once and are not asked again within a day.
        val cache = c.companySearch.cache
        cache.plazas()?.let { _plazas.value = it.value }
        viewModelScope.launch {
            cache.load()
            val kept = cache.plazas()
            kept?.let { _plazas.value = it.value }
            if (cache.fresh(kept, com.bamnat.app.company.PageCache.DAY_MS)) return@launch
            runCatching { c.companyApi.plazas() }.getOrNull()?.let { _plazas.value = it; cache.keepPlazas(it) }
        }
        more(false)
        // AS 1.29 [SRCH-13] The 대표판 tab's first page is read beside the first one, so switching to it shows at once.
        more(true)
        viewModelScope.launch {
            query.collectLatest { typed ->
                val q = typed.trim()
                if (q == asked) return@collectLatest
                delay(300)
                asked = q
                tabs.values.forEach { it.value = Tab() }
                more()
            }
        }
    }

    fun pick(adopted: Boolean) {
        _adopted.value = adopted
        if (tabs.getValue(adopted).value.items.isEmpty()) more(adopted)
    }

    fun refresh() {
        val a = _adopted.value
        tabs.getValue(a).value = Tab()
        more(a)
    }

    /** The next page of a tab; nothing when it is loading or has no more. */
    fun more(adopted: Boolean = _adopted.value) {
        val f = tabs.getValue(adopted)
        val from = f.value.next ?: return
        if (f.value.loading) return
        val q = asked
        val fl = _filter.value
        // AS 1.29 [SRCH-13] A first page read before shows at once while it is read again.
        // [SPD-13] 1.30 AP1: kept on the phone between runs too (1.29 kept it for the run only).
        val key = "$adopted|$q|${fl.job}|${fl.region}|${fl.sort}"
        val cache = c.companySearch.cache
        val kept = if (from == 0) cache.board(key)?.value else null
        f.value = if (kept != null) f.value.copy(items = kept.items, next = kept.next, loading = true, error = null, fromCache = true, stale = false)
        else f.value.copy(loading = true, error = null, stale = false)
        viewModelScope.launch {
            if (from == 0 && kept == null && !cache.loaded) {
                cache.load()
                cache.board(key)?.value?.let { k -> if (f.value.items.isEmpty()) f.value = f.value.copy(items = k.items, next = k.next, fromCache = true) }
            }
            if (from == 0) com.bamnat.app.company.NetTimings.cached(f.value.items.isNotEmpty())
            val done = try {
                val b = c.companyApi.board(adopted, from, q, fl.job, fl.region, fl.sort)
                if (from == 0) cache.keepBoard(key, b)
                f.value.copy(items = (if (from == 0) emptyList() else f.value.items) + b.items, next = b.next, loading = false, fromCache = false)
            } catch (e: CompanyException) {
                val shown = f.value.items.isNotEmpty()
                f.value.copy(loading = false, error = if (shown) null else companyProblem(e.code, e.field), stale = shown && from == 0)
            }
            // A page for a name no longer in the box is dropped; the new name's pages are on their way.
            if (q == asked && fl == _filter.value) {
                f.value = done
                tellApart(done.items)
                // [SPD-15] The shown tab's first companies are read ahead, so a tap opens the page at once.
                if (from == 0 && adopted == _adopted.value && !done.stale) c.prefetchCompanies(done.items.map { it.companyId })
            }
        }
    }

    /** [COMM-14] Reads each company with two cards for one department once (the page the detail opens) and keeps the lines that differ. */
    private fun tellApart(items: List<com.bamnat.app.company.BoardItem>) {
        val twice = items.groupBy { it.companyId to it.dept }.filterValues { it.size > 1 }.keys.map { it.first }.distinct()
        twice.filter(readPages::add).forEach { id ->
            viewModelScope.launch {
                // [SPD-13] A page kept on the phone (read ahead, or seen before) is enough to tell the cards apart.
                val page = c.companySearch.page(id) ?: try {
                    c.companyApi.company(id).also(c.companySearch::keepPage)
                } catch (e: CompanyException) { readPages.remove(id); return@launch }
                _diffs.value = _diffs.value + com.bamnat.core.plaza.SetupDiff.diff(page.setups.map { it.diffItem() })
            }
        }
    }
}

/** [COMM-14] A setup as [com.bamnat.core.plaza.SetupDiff] compares it. */
internal fun com.bamnat.app.company.SharedSetup.diffItem() =
    com.bamnat.core.plaza.SetupDiff.Item(id, dept, adopted, version, 0, lines)

/** [PLZ-08] 내 활동: what this phone sent to the plaza, and the name it is shown by. */
class ActivityHolder(
    private val c: AppContainer,
    private val scope: kotlinx.coroutines.CoroutineScope,
    /** [CALI-08] The calendar whose sent rules are listed. */
    private val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) {
    /**
     * [CALI-08] 1.30 BB (사용자 2026-10-27 "내가 보낸 규칙도 해당 달력에 맞춰 보여져야 한다"): [activity] holds the rules sent
     * from [calendarName]'s calendar only (named when the phone has two calendars or more); [elsewhere] the other calendars'
     * names with how many each sent.
     */
    data class Open(
        val activity: com.bamnat.app.company.Activity? = null,
        val error: String? = null,
        val calendarName: String? = null,
        val elsewhere: List<Pair<String, Int>> = emptyList(),
        /** [SPD-13] What is shown is the one kept on the phone, being read again. */
        val refreshing: Boolean = false,
    )

    private val _open = MutableStateFlow<Open?>(null)
    val open: StateFlow<Open?> = _open.asStateFlow()

    /**
     * [SPD-13] 1.30 AP1: 내 활동 as last read shows at once (kept by this phone's install, its own 시세 amounts left out), then
     * afresh. Which calendar sent what is worked out on the phone each time ([mine]), so each calendar's 광장 lists its own.
     */
    fun show() {
        val device = c.companyStore.device
        val cache = c.companySearch.cache
        val kept = cache.activity(device)?.value
        com.bamnat.app.company.NetTimings.cached(kept != null)
        _open.value = kept?.let { mine(it).copy(refreshing = true) } ?: Open()
        scope.launch {
            val next = try {
                c.companyApi.activity(device).also { cache.keepActivity(device, it) }.let(::mine)
            } catch (e: CompanyException) {
                if (kept != null) mine(kept) else Open(error = companyProblem(e.code, e.field))
            }
            if (_open.value != null) _open.value = next
        }
    }

    /**
     * [CALI-08] Each report is the calendar's that kept it when it went ([com.bamnat.app.company.CompanyStore.sent]); one no
     * calendar keeps (sent before 1.30, or past the 50 kept) is 내 달력's, where everything went before.
     */
    private fun mine(a: com.bamnat.app.company.Activity): Open {
        val cals = companyCalendars(c.calendars.calendars.value)
        if (cals.size < 2) return Open(a)
        val owner = HashMap<String, String>()
        for (s in c.companyScopes()) for (r in s.store.sent.value) owner.putIfAbsent(r.id, s.calendarId)
        val ofReport = { id: String -> owner[id] ?: com.bamnat.app.data.PersonalCalendar.MAIN }
        val here = a.reports.filter { ofReport(it.id) == calendar }
        val others = a.reports.filter { ofReport(it.id) != calendar }.groupingBy { ofReport(it.id) }.eachCount()
            .mapNotNull { (id, n) -> cals.firstOrNull { it.id == id }?.let { it.name to n } }
        return Open(a.copy(reports = here), calendarName = cals.firstOrNull { it.id == calendar }?.name, elsewhere = others)
    }

    fun close() {
        _open.value = null
    }

    /** Sets the plaza name, or none when blank; [done] gets what to tell the user. */
    fun setNick(nick: String, done: (String?) -> Unit) {
        val t = nick.trim()
        if (t.isNotEmpty() && (t.length < 2 || t.length > 12)) return done("별명은 2~12자로 정해 주세요")
        CompanyText.personal(t)?.let { return done(talkProblem(it)) }
        scope.launch {
            val error = try {
                val saved = c.companyApi.nick(c.companyStore.device, t)
                _open.value = _open.value?.let { o -> o.copy(activity = o.activity?.copy(nick = saved)) }
                null
            } catch (e: CompanyException) {
                when (e.code) {
                    "taken" -> "다른 분이 쓰는 별명이에요"
                    "nick" -> "쓸 수 없는 별명이에요. 관리자·개발자·운영자 같은 말은 빼 주세요"
                    else -> talkProblem(e.code)
                }
            }
            done(error)
        }
    }
}

/**
 * [PLZ-14][PLZ-15] 임금표: what a trade is paid per day, hour or month in a job and region, as people
 * shared it, with the adopted ones' middle as 광장 시세. Sending one asks no name or pay slip.
 */
class RatesViewModel(private val c: AppContainer) : ViewModel() {
    data class Filter(val job: String = "construction", val region: String = "", val unit: String = "")
    data class State(val board: com.bamnat.app.company.RateBoard? = null, val loading: Boolean = true, val error: String? = null)

    val enabled: Boolean = c.companyApi.enabled
    private val _filter = MutableStateFlow(Filter())
    val filter: StateFlow<Filter> = _filter.asStateFlow()
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
    }

    fun setFilter(f: Filter) {
        _filter.value = f
        load()
    }

    fun load() {
        val f = _filter.value
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            val next = try {
                State(c.companyApi.rates(c.companyStore.device, f.job, f.region, f.unit).also { b ->
                    c.rateHints.keep(f.region, b.groups)
                    b.entries.firstOrNull { it.mine }?.let { c.rateHints.shared(it.job, it.region, it.trade) }
                }, loading = false)
            } catch (e: CompanyException) {
                State(_state.value.board, loading = false, error = companyProblem(e.code, e.field))
            }
            if (f == _filter.value) _state.value = next
        }
    }

    /** Sends a rate; [done] gets what to tell the user when it was not taken, null when it was. */
    fun add(trade: String, unit: String, amount: Long, note: String, done: (String?) -> Unit) {
        val f = _filter.value
        val u = com.bamnat.app.company.Plaza.RATE_UNITS[unit] ?: return done("일당·시급·월급 가운데 골라 주세요")
        if (trade.trim().length < 2) return done("직종을 두 글자 이상 적어 주세요 (예: 형틀목수)")
        if (amount !in u.min..u.max) return done("${u.name}은 ${won(u.min)}~${won(u.max)} 사이로 적어 주세요")
        CompanyText.personal(trade)?.let { return done(talkProblem(it)) }
        CompanyText.personal(note)?.let { return done(talkProblem(it)) }
        viewModelScope.launch {
            val error = try {
                c.companyApi.addRate(c.companyStore.device, f.job, f.region, trade, unit, amount, note)
                c.rateHints.shared(f.job, f.region, trade)
                c.telemetry.event("plaza_rate", "sent")
                null
            } catch (e: CompanyException) {
                when (e.code) {
                    "adopted" -> "같은 시세가 이미 채택됐어요. 다른 분의 시세에 맞아요·달라요를 눌러 주세요"
                    "trade" -> "직종을 두 글자 이상, 20자 안으로 적어 주세요"
                    else -> talkProblem(e.code)
                }
            }
            if (error == null) load()
            done(error)
        }
    }

    fun vote(rate: com.bamnat.app.company.Rate, ok: Boolean) {
        viewModelScope.launch {
            runCatching { c.companyApi.rateVote(c.companyStore.device, rate.id, if (rate.myVote == ok) null else ok) }
            load()
        }
    }

    fun takeBack(rate: com.bamnat.app.company.Rate, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val kept = runCatching { c.companyApi.takeRateBack(c.companyStore.device, rate.id) }.getOrDefault(false)
            load()
            done(kept)
        }
    }

    /**
     * [PLZ-11] 광장 시세를 내 급여에: the middle of [g] becomes my 일당·시급·월급 from this month, when my pay is
     * paid that way. Earlier months keep what they were; undo is the pay screen's. [done] gets what to say.
     */
    fun applyToMyPay(g: com.bamnat.app.company.RateGroup, done: (String) -> Unit) {
        val p = c.payStore.profile.value ?: return done("급여 설정이 없어요. 급여 설정을 먼저 해 주세요")
        val want = when (g.unit) {
            "day" -> WageType.DAILY
            "hour" -> WageType.HOURLY
            else -> WageType.MONTHLY
        }
        if (p.wageType != want) return done("내 급여는 ${wageName(p.wageType)} 방식이라 ${com.bamnat.app.company.Plaza.unit(g.unit)}을 넣을 수 없어요")
        viewModelScope.launch {
            val from = Today.month().atDay(1)
            c.payStore.save(p.copy(base = p.base.with(com.bamnat.core.pay.DatedValue(from, g.mid))))
            c.telemetry.event("plaza_rate", "applied")
            done("${from.monthValue}월부터 ${com.bamnat.app.company.Plaza.unit(g.unit)}을 ${won(g.mid)}으로 바꿨어요. 급여 화면에서 되돌릴 수 있어요")
        }
    }

    private fun wageName(w: WageType) = when (w) {
        WageType.DAILY -> "일당"
        WageType.HOURLY -> "시급"
        else -> "월급"
    }

    private fun won(v: Long) = "%,d원".format(v)
}

/** [COMM-01] The first pay setup from a shared setup: only the user's own amounts are asked. */
class CompanyPayViewModel(
    private val c: AppContainer,
    /** [CALI-04] 1.30 BB: the calendar whose pay is made. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    private val scope = c.companyOf(calendar)
    /** The company's setup the pay is made from; [PLAZA-03] the user's own corrections replace it ([useEdited]). */
    var text: String? by androidx.compose.runtime.mutableStateOf(scope.store.pendingPay)
        private set
    val company: String = scope.store.applied.value?.company.orEmpty()
    /** [PLAZA-03] The applied rule set, for 다른 점 말하기 on it; null when the setup came from a file, not the plaza. */
    private val applied: AppliedSetup? = scope.store.applied.value
    val canPropose: Boolean get() = applied != null
    /** [PLAZA-03] What the user changed before the pay was made, so the screen can say their pay uses it. */
    var edited: Boolean by androidx.compose.runtime.mutableStateOf(false)
        private set
    /** [COMM-13] 입사일 if a pay profile (an earlier one, or one restored) already knows it. */
    val hireDate: java.time.LocalDate? = scope.pay.profile.value?.hireDate
    val needs: List<CompanyShare.AmountNeed> get() = text?.let { runCatching { scope.share.amountNeeds(it) }.getOrNull() }.orEmpty()
    val summary: CompanySummary? get() = text?.let { runCatching { scope.share.summarize(it) }.getOrNull() }
    /** [PLAZA-03] Whether the company's setup has pay rules to ask "이 회사 급여 설정을 쓸까요?" about. */
    val hasRules: Boolean get() = summary?.hasRules == true
    /** [PAYF-01] 1.29 AP: the user just chose this pay way on the screen before; the question is not asked twice. */
    val decided: Boolean = scope.payWays.decided.also { scope.payWays.decided = false } ||
        com.bamnat.app.company.PendingWays.of(scope).pending.value != null
    /** [PLAZA-03] The talk the 다른 점 proposal goes to. */
    val talk = TalkHolder(c, viewModelScope, calendar)

    /**
     * [PLAZA-03] U26 "우리 급여는 달라요": the user's corrected [setup] becomes this phone's (the pay is made from it), and the
     * same goes to the company's talk as a proposal with the changed [lines], so the next coworker finds it right.
     */
    fun useEdited(lines: List<com.bamnat.app.company.ChangeLine>, setup: String, why: String, done: (String?) -> Unit) {
        val a = applied ?: return done(null)
        val base = text ?: return done(null)
        talk.show(a.companyId, a.setup, "${a.company} · ${a.dept.ifEmpty { "회사 전체" }}")
        talk.send(proposalBody(com.bamnat.core.plaza.SetupCompare.rows(base, setup), why), kind = "proposal", change = lines, setupText = setup) { e ->
            talk.close()
            // Their own pay uses what they said, sent or not: a refused post (rules, limits) never blocks the pay.
            text = setup
            edited = true
            scope.store.pendingPay = setup
            c.telemetry.event("plaza_diff", if (e == null) "pay_sent" else "pay_kept")
            done(e)
        }
    }

    /** A share of 기본급 bonus, e.g. 상여 짝수 달 100%: said so the user does not look for its amount. */
    val bonusNote: String? get() = text?.let { t ->
        runCatching {
            JSONObject(t).optJSONObject("pay")?.optJSONArray("items")?.let { a ->
                (0 until a.length()).map { a.getJSONObject(it) }
                    .filter { it.optString("cadence") == "BONUS" && it.has("percentOfBase") }
                    .joinToString(" · ") { o ->
                        val months = o.optJSONArray("months")?.let { m -> (0 until m.length()).map { m.getInt(it) } }.orEmpty()
                        "${o.optString("name")} ${months.joinToString("·")}월 기본급 ${com.bamnat.core.pay.PayCalculator.pct(o.getDouble("percentOfBase"))}"
                    }.ifEmpty { null }
            }
        }.getOrNull()
    }

    fun save(
        wageType: WageType, base: Long, rough: Boolean, dailyHours: Int, since: YearMonth, amounts: Map<String, Long>,
        hours: Int? = null, done: () -> Unit, failed: (String) -> Unit,
    ) {
        val t = text ?: return failed("설정을 다시 골라 주세요")
        viewModelScope.launch {
            try {
                val made = scope.share.createPay(t, wageType, base, rough, dailyHours, since, amounts)
                // [PAYF-06] The 통상시급's hours the user knew, over the company's (theirs are only the sender's usual).
                hours?.takeIf { wageType == WageType.MONTHLY && it in com.bamnat.core.pay.BaseAsk.HOURS_RANGE }?.let { h ->
                    scope.pay.save(com.bamnat.core.pay.RuleOnlyNeeds.settled(made.copy(monthlyHours = h)), keepUndo = false)
                }
                scope.store.pendingPay = null
                // [PAYG-01] 1.30 BA: the company's way is in use from now, its amounts given (not from the moment it was chosen).
                com.bamnat.app.company.PendingWays.keptWith(c, scope, applied, t, hours)
                c.telemetry.event("pay_setup_done", "COMPANY")
                done()
            } catch (e: BackupException) {
                failed(e.message ?: "급여 설정을 만들지 못했어요")
            }
        }
    }
}

/** [COMM-02] Sending this phone's setup, and what became of everything it sent. */
class CompanyReportViewModel(
    private val c: AppContainer,
    /** [CALI-03] 1.30 BB: the calendar whose setup goes (and joins the company), and whose sent reports are listed. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    private val scope = c.companyOf(calendar)

    sealed interface Sending {
        data object Idle : Sending
        data object Busy : Sending
        data class Sent(val status: ReportStatus) : Sending
        data class Failed(val code: String, val field: String?) : Sending
        /** [PLZD-01][PLZD-02][PLZD-03][PLZD-05] 1.30 BD: the plaza already has this, another shape, or other values; asked first. */
        data class Guarded(val verdict: com.bamnat.app.company.SendGuard.Verdict, val form: Form) : Sending
        /** [PLZD-01] 이 규칙 쓰기: nothing went, the calendar uses the plaza's rule. */
        data class Used(val company: String, val companyId: String, val dept: String, val withPay: Boolean) : Sending
        /** [PLZD-03] 달라진 점만 제안으로 보내기 went to the 대표's talk. */
        data class Proposed(val company: String, val companyId: String, val dept: String) : Sending
    }

    /** What the form said when a send was held for a question ([Sending.Guarded]). */
    data class Form(val company: String, val dept: String, val verified: Boolean, val note: String, val job: String = "",
        val region: String = "", val topic: Boolean = false)

    /** [PLZD-01] 1.30 BD: what this calendar's setup meets on the plaza before it goes. */
    val guard = com.bamnat.app.company.SendGuard(c, calendar)

    private val _terms = MutableStateFlow(c.termsNow())
    val terms: StateFlow<CompanyTerms?> = _terms.asStateFlow()
    /** What goes, read from the very file that is sent. */
    private val _preview = MutableStateFlow<CompanySummary?>(null)
    val preview: StateFlow<CompanySummary?> = _preview.asStateFlow()
    private val _sending = MutableStateFlow<Sending>(Sending.Idle)
    val sending: StateFlow<Sending> = _sending.asStateFlow()
    val draft: StateFlow<CompanyDraft?> = scope.store.draft
    /** [PAYS-02] The month a payslip was matched on this phone; only the yes or no goes out, never the figures. */
    val payslipMonth: java.time.YearMonth? get() = scope.pay.profile.value?.verified?.month
    /** [PLAZA-30] 1.27 AI: the calendar's pay, for what the 명세서 tick rests on as it changes (a match done from the form). */
    val payProfile: StateFlow<com.bamnat.core.pay.PayProfile?> = scope.pay.profile
    fun proof(p: com.bamnat.core.pay.PayProfile?): com.bamnat.core.pay.SlipProof? =
        com.bamnat.core.pay.SlipProof.of(p?.verified, java.time.YearMonth.from(Today.date()))
    /** [PLAZA-30] Opens the main pay screen's 명세서로 맞춰 보기. */
    fun askMatch() { c.pendingMatch = true }
    val applied: StateFlow<AppliedSetup?> = scope.store.applied
    /** [CALI-08] What this calendar sent. */
    val sent: StateFlow<List<SentReport>> = scope.store.sent
    private val _statuses = MutableStateFlow<Map<String, ReportStatus?>>(emptyMap())
    /** What the server says of each sent report; null when it no longer has one. */
    val statuses: StateFlow<Map<String, ReportStatus?>> = _statuses.asStateFlow()
    private val _offline = MutableStateFlow(false)
    val offline: StateFlow<Boolean> = _offline.asStateFlow()

    /**
     * [SPAT-05] The public row this phone's cycle came from, while nothing went up from it: the form starts from its
     * company and goes as '공개 자료 확인·고침' with the order the user chose (or '처음 제공' for a company not in it).
     */
    val publicUse: PublicUse? = scope.store.publicUse.value?.takeIf { it.sentId == null }
    /** Whether a pay setup is there to go with the cycle; without one only the cycle can go. */
    val hasPay: Boolean get() = scope.pay.profile.value != null
    private val _cycleOnly = MutableStateFlow(!hasPay)
    /** [SPAT-05] 근무주기만 올리기: no pay part, and so no reward (사용자 2026-10-25 14:05). */
    val cycleOnly: StateFlow<Boolean> = _cycleOnly.asStateFlow()

    init {
        viewModelScope.launch { _terms.value = c.loadTerms() }
        viewModelScope.launch {
            _cycleOnly.collect { only ->
                _preview.value = runCatching { scope.share.summarize(scope.share.exportForServer(only).toString()) }.getOrNull()
            }
        }
    }

    fun setCycleOnly(only: Boolean) {
        // Pay that is not there cannot go.
        _cycleOnly.value = only || !hasPay
    }

    /** Where the form starts: the company typed during setup or corrected, else the setup in use, else the public row's. */
    val start: CompanyDraft? = scope.store.draft.value
        ?: scope.store.applied.value?.let { CompanyDraft(it.company, it.dept) }
        ?: publicUse?.let { u -> CompanyDraft(u.company, u.cycle?.site.orEmpty().take(20)) }

    /** [SPAT-05] The note a setup from a public row starts with; the user may add to it. */
    val startNote: String = publicUse?.let { PublicSetup.note(it.cycle, it.codes, it.picked) }.orEmpty()

    /** Sends the setup; a text with an amount, phone or mail address is caught here before it goes. */
    fun send(company: String, dept: String, verified: Boolean, note: String, job: String = "", region: String = "", topic: Boolean = false,
        /** [PLZD-01] The user already answered what [guard] asked: it goes as it is. */
        checked: Boolean = false) {
        if (topic && job.isEmpty()) { _sending.value = Sending.Failed("job", "job"); return }
        // [SRV-04] The developer paused new reports; nothing is sent and the form stays as written.
        if (c.flags.paused(com.bamnat.core.ops.ServerSwitch.COMPANY_REPORT)) { _sending.value = Sending.Failed("paused", "report"); return }
        if (!topic) CompanyText.personal(company)?.let { _sending.value = Sending.Failed(it, "company"); return }
        CompanyText.personal(dept)?.let { _sending.value = Sending.Failed(it, "dept"); return }
        CompanyText.personal(note)?.let { _sending.value = Sending.Failed(it, "note"); return }
        if (!topic && CompanyText.norm(company).length < 2) { _sending.value = Sending.Failed("company", "company"); return }
        _sending.value = Sending.Busy
        viewModelScope.launch {
            _sending.value = try {
                val store = scope.store
                val setup = scope.share.exportForServer(_cycleOnly.value)
                // [PLZD-01][PLZD-02][PLZD-03][PLZD-05] A company the plaza has: the same rule, another shape or other values ask first.
                if (!topic && !checked) {
                    val v = guard.check(company, dept, setup.toString(), note)
                    if (v !is com.bamnat.app.company.SendGuard.Clear || v.resent != null) {
                        c.telemetry.event("plaza_guard", v.javaClass.simpleName.lowercase() + if (v.resent != null) ",resent" else "")
                        _sending.value = Sending.Guarded(v, Form(company, dept, verified, note, job, region, topic))
                        return@launch
                    }
                }
                // [PLZ-12][PLZ-13] Its job plaza and 시·도, or a rule for a whole job rather than one place. [CALI-02] From this calendar.
                val status = if (scope.isMain && job.isEmpty() && region.isEmpty() && !topic) c.companyApi.report(store.device, company, dept, verified, note, setup)
                    else if (scope.isMain) c.companyApi.report(store.device, company, dept, verified, note, setup, job, region, topic)
                    else c.companyApi.report(store.device, company, dept, verified, note, setup, job, region, topic, scope.serverCal)
                store.addSent(SentReport(status.id, status.company, status.dept, ask = false, at = Today.instant()))
                // [PLAZA-07] Sending a company's setup joins it (결정 D79).
                status.member?.takeIf { it.joined }?.let { store.setMember(status.companyId, status.company) }
                // [PLAZA-34] 1.27 AI: what went is what the plaza has of this calendar; the same values saved again ask nothing.
                if (!topic) store.autoBase = setup.toString()
                _statuses.value = _statuses.value + (status.id to status)
                store.setDraft(null)
                // [SPAT-05] The way on from a public row ends here; the plaza page it went to opens next.
                if (publicUse != null) store.updatePublicUse { it.copy(sentId = status.id, companyId = status.companyId) }
                c.telemetry.event("company_report", listOfNotNull(if (verified) "verified" else "plain", if (start?.fix == true) "fix" else "share",
                    if (status.again) "again" else "new", "public".takeIf { publicUse != null }, "cycle".takeIf { _cycleOnly.value }).joinToString(","))
                Sending.Sent(status)
            } catch (e: CompanyException) {
                Sending.Failed(e.code, e.field)
            }
        }
    }

    fun resetSending() {
        _sending.value = Sending.Idle
    }

    /** [PLZD-01] The held send goes as the form said, or under another department's name ([dept], asked again). */
    fun sendAnyway(f: Form, dept: String = f.dept) =
        send(f.company, dept, f.verified, f.note, f.job, f.region, f.topic, checked = dept == f.dept)

    /** [PLZD-01] 이 규칙 쓰기: the plaza's same rule is this calendar's now, and counts one more 맞아요. */
    fun useSame(v: com.bamnat.app.company.SendGuard.Same, verified: Boolean) {
        _sending.value = Sending.Busy
        viewModelScope.launch {
            _sending.value = try {
                guard.useSame(v, verified)
                Sending.Used(v.page.name, v.page.id, v.setup.dept, v.withPay)
            } catch (e: CompanyException) {
                Sending.Failed(e.code, e.field)
            } catch (e: BackupException) {
                Sending.Failed("apply", null)
            } catch (e: org.json.JSONException) {
                Sending.Failed("apply", null)
            }
        }
    }

    /** [PLZD-03] The proposal on the 대표 went up; the draft typed for it is done. */
    fun proposed(v: com.bamnat.app.company.SendGuard.Differ) {
        scope.store.setDraft(null)
        c.telemetry.event("plaza_diff", "guard")
        _sending.value = Sending.Proposed(v.page.name, v.page.id, v.lead.dept)
    }

    /** Looks up the newest reports; older ones keep what was last seen. */
    fun refresh() {
        viewModelScope.launch {
            var failed = false
            for (r in sent.value.take(20)) {
                try {
                    _statuses.value = _statuses.value + (r.id to c.companyApi.status(r.id))
                } catch (e: CompanyException) {
                    failed = true
                    break
                }
            }
            _offline.value = failed
        }
    }

    fun takeBack(id: String, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            val ok = runCatching { c.companyApi.takeBack(c.companyStore.device, id) }.isSuccess
            if (ok) {
                scope.store.removeSent(id)
                _statuses.value = _statuses.value - id
            }
            done(ok)
        }
    }

    /** Everything this phone sent goes from the server; the setup in use stays on the phone. */
    fun forgetAll(done: (Boolean) -> Unit) = forgetAllSaying { ok, _ -> done(ok) }

    /** [INQ-05] I28: as [forgetAll], with what was deleted in words. */
    fun forgetAllSaying(done: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val counts = runCatching { c.companyApi.forgetMineCounts(c.companyStore.device) }.getOrNull()
            val ok = counts != null
            if (ok) {
                // [CALI-08] The server forgot everything this phone sent, from every calendar.
                c.forgetSentEverywhere()
                // [SPD-13] and 내 활동 kept on the phone with it.
                c.companySearch.cache.forgetActivity()
                _statuses.value = emptyMap()
                // [ADMIN-02] The server deleted this phone's questions with the rest.
                c.inquiryStore.update(emptyList())
                c.inquiryStore.asked = false
            }
            done(ok, counts?.words())
        }
    }
}

/**
 * [COMM-02][COMM-03] The small asks around a company setup, on the pay screen and in settings:
 * 맞았어요·달라요 on the shared setup in use, and the invitation to share a hand-made or corrected one.
 */
class CompanyNudgeViewModel(
    private val c: AppContainer,
    /** [CALI-04] 1.30 BB: the calendar whose company setup is asked about. */
    val calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel() {
    private val scope = c.companyOf(calendar)
    private val store = scope.store
    val enabled: Boolean = c.companyApi.enabled
    val applied: StateFlow<AppliedSetup?> = store.applied
    val draft: StateFlow<CompanyDraft?> = store.draft
    private val _terms = MutableStateFlow(c.termsNow())
    val terms: StateFlow<CompanyTerms?> = _terms.asStateFlow()

    /** The setup in use while 맞았어요·달라요 is still to say and was not put off. */
    val askVote: StateFlow<AppliedSetup?> = combine(store.applied, store.voteLater, scope.payWays.way) { a, later, way ->
        // [PAYF-04] 1.29 AP: the pay screen's own card keeps 맞아요·달라요 on a pay way in use; not asked twice on one screen.
        a?.takeIf { enabled && it.vote == null && Today.millis() >= later && way?.setup != it.setup }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * A setup made or corrected by hand under a company's name and not shared yet; [PLAZA-04] or one made by hand with no
     * company (a draft with no name), until something is sent. Asked again 14, 30 and 90 days after each "다음에".
     */
    val invite: StateFlow<CompanyDraft?> = combine(store.draft, store.handMade, store.inviteDue, store.inviteNever,
        store.sent) { d, hand, due, never, sent ->
        val draft = d ?: CompanyDraft("").takeIf { hand && sent.isEmpty() && store.applied.value == null }
        draft?.takeIf { enabled && com.bamnat.core.plaza.ShareAsk.due(never, due, Today.millis(), usesCompanySetup = false) }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** [PLAZA-04] What would go, in the plaza's words and without amounts, so the user sees it before deciding. */
    private val _preview = MutableStateFlow<List<String>>(emptyList())
    val preview: StateFlow<List<String>> = _preview.asStateFlow()

    fun loadPreview() {
        if (_preview.value.isNotEmpty()) return
        viewModelScope.launch {
            _preview.value = runCatching {
                com.bamnat.core.plaza.SetupFacts.read(scope.share.exportForServer().toString())
                    .groupBy { it.group }.map { (g, f) -> "${g.title}: " + f.take(2).joinToString(" · ") { "${it.label} ${it.value}" } + if (f.size > 2) " 외 ${f.size - 2}가지" else "" }
            }.getOrDefault(emptyList())
        }
    }

    init {
        if (enabled) viewModelScope.launch { _terms.value = c.loadTerms() }
    }

    /** [why] for 달라요: wrong, old or name. [done] says whether the server took it. */
    fun vote(ok: Boolean, verified: Boolean = false, why: String? = null, done: (Boolean) -> Unit = {}) {
        val a = applied.value ?: return
        viewModelScope.launch {
            val sent = runCatching { c.companyApi.vote(c.companyStore.device, a.companyId, a.setup, ok, verified, why) }.isSuccess
            if (sent) {
                store.voted(ok)
                c.telemetry.event("company_vote", if (ok) (if (verified) "ok_verified" else "ok") else why ?: "wrong")
            }
            done(sent)
        }
    }

    /** [PLAZA-30] 1.27 AI: whether 명세서와 맞춰 봤어요 may be chosen (a matched month on this calendar). */
    fun hasProof(): Boolean =
        com.bamnat.core.pay.SlipProof.of(scope.pay.profile.value?.verified, java.time.YearMonth.from(Today.date()))?.counts == true

    /** "나중에": asked again in a week. */
    fun later() = store.voteLaterUntil(Today.millis() + 7L * 24 * 60 * 60 * 1000)

    /** After 달라요 the user corrects the setup; the invitation then asks for the right one. */
    fun startFix() {
        val a = applied.value ?: return
        store.setDraft(CompanyDraft(a.company, a.dept, fix = true))
        c.telemetry.event("company_fix_start")
    }

    /** [PLAZA-04] "다음에" (14, 30, then 90 days) or, with [never], "그만 볼게요". */
    fun closeInvite(never: Boolean = false) {
        if (never) store.inviteNever() else store.inviteLater(Today.millis())
        c.telemetry.event("company_invite_close", if (never) "never" else "later")
    }
}

/**
 * [EXTRA-06][COMM-04] A company way the app cannot set yet. Since 1.14.1 it is a question in the app
 * like any other [INQ-01]: the developer answers it with the rest in the developer app's 문의, and the
 * answer shows in 설정 > 문의하기. It is never public, since it names the workplace. [setup] is the
 * kind's settings in words, without an amount. Null when it went, else what to tell the user.
 */
suspend fun AppContainer.askDeveloper(company: String, how: String, example: String, setup: String? = null): String? {
    val text = "회사: ${company.trim()}\n${how.trim()}" + (if (example.isNotBlank()) "\n예시: ${example.trim()}" else "")
    CompanyText.personal(company)?.let { return companyProblem(it, "company") }
    CompanyText.personal(text)?.let { return companyProblem(it, "note") }
    if (CompanyText.norm(company).length < 2) return companyProblem("company")
    if (text.length > com.bamnat.app.company.InquiryClient.TEXT_MAX) return companyProblem("note")
    return try {
        inquiryApi.send(companyStore.device, com.bamnat.app.company.InquiryKind.COMPANY, text, deviceInquiryInfo().copy(setup = setup))
        inquiryStore.asked = true
        telemetry.event("company_ask", "inquiry")
        null
    } catch (e: CompanyException) {
        // The settings are made by the app; one the server will not take is a name that looks like an amount.
        if (e.field == "setup") "이 종류의 이름이나 설정에 금액·연락처처럼 보이는 글자가 있어요. 이름을 바꾼 뒤 보내 주세요"
        else companyProblem(e.code, if (e.field == "text") "note" else e.field)
    }
}

/** [ADMIN-02] What always goes with a question: this app and phone, shown on the form before it is sent. */
fun deviceInquiryInfo() = com.bamnat.app.company.InquiryInfo(
    app = "${com.bamnat.app.BuildConfig.VERSION_NAME} (${com.bamnat.app.BuildConfig.VERSION_CODE})",
    model = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
    android = "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})",
)
