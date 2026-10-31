package com.bamnat.app.ui.company

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.RoomPage
import com.bamnat.app.company.Talk
import com.bamnat.app.company.bundle
import com.bamnat.app.company.warmCompanies
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.plaza.RoomCard
import com.bamnat.core.plaza.SetupFacts
import com.bamnat.core.plaza.StylePlaza
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PLAZA-32][PLAZA-33] 1.27 묶음 AI: what 광장 opens to. A phone whose main calendar joined a company sees ‘<회사> 정보 광장’
 * (자유 토의 under 참여자 번호, the company's 근무·급여 정보 and 고친 점); a kind of work with no company its own plaza
 * ([StylePlaza.plan]); 교대·일근 with no company keep the rule plaza ([CompanyBoardScreen]), which every plaza links to.
 */
class PlazaHomeViewModel(
    private val c: AppContainer,
    /** [CALI-08] 1.30 BB: the calendar whose company and kind of work the plaza is about. */
    val calendar: String = PersonalCalendar.MAIN,
    /** [RCP-34] 1.34: open on the kind of work's own plaza (교대 광장·일근 광장 from the rule plaza), not the company's room. */
    own: Boolean = false,
) : ViewModel() {
    private val scope = c.companyOf(calendar)
    val enabled: Boolean = c.companyApi.enabled
    private val style = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    /** The company this calendar joined, as the server last said; else none. */
    private val company = scope.store.member.value?.takeIf { it.first.isNotBlank() }
        ?.let { it.first to it.second.ifBlank { scope.store.applied.value?.company ?: "우리 회사" } }

    private val _plan = MutableStateFlow(if (own) StylePlaza.own(style) else StylePlaza.plan(style, company))
    val plan: StateFlow<StylePlaza.Plan> = _plan.asStateFlow()

    /** On a kind of work's plaza reached from the company's room: the way back to it. */
    val backRoom: String? get() = company?.first?.takeIf { StylePlaza.styleRoom(style) != null && style != com.bamnat.core.work.WorkStyle.INVEST }

    /** The one line after 이 설정 쓰기 (443), once. */
    val note: String? = c.pendingPlazaNote.also { c.pendingPlazaNote = null }

    /**
     * [SPD-13] [refreshing]: what is on screen is the one kept on the phone, being read again (a thin line on top); [stale]
     * when reading it again failed ("최근 본 내용이에요 · 다시 시도").
     */
    data class State(val talk: Talk? = null, val error: String? = null, val room: RoomPage? = null, val cards: List<RoomCard> = emptyList(),
        val facts: List<Pair<String, String>> = emptyList(), val refreshing: Boolean = false, val stale: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    val talk = TalkHolder(c, viewModelScope, calendar)

    init {
        // [SPD-12] The whole list of companies, for 다른 회사 규칙 광장 and its search, if the start of the app did not yet.
        c.warmCompanies()
        load()
    }

    /** Back and forth between a company's room and its kind of work's plaza ([StylePlaza.Plan.alsoRoom]). */
    fun across() {
        val p = _plan.value
        _plan.value = if (p.top == StylePlaza.Top.COMPANY_ROOM) StylePlaza.own(style) else StylePlaza.plan(style, company)
        _state.value = State()
        load()
    }

    /**
     * AS 1.29 [SRCH-13] The talk and the company's room are asked together, and the cards worked out off the screen's thread.
     * [SPD-13][SPD-14] 1.30 AP1 (사용자 2026-10-27 "조회하는 로딩이 너무 느리다"): what the phone kept ([com.bamnat.app.company.PageCache]:
     * the company's page with its room and room talk, or a kind of work's talk) shows at once, then one request reads it all
     * again ([com.bamnat.app.company.bundle]; 1.29 asked twice for each, and read the plaza twice as it opened).
     */
    fun load() {
        val p = _plan.value
        val room = p.room ?: return
        val started = com.bamnat.app.company.NetTimings.start()
        val company = p.top == StylePlaza.Top.COMPANY_ROOM
        val cache = c.companySearch.cache
        viewModelScope.launch {
            cache.load()
            val keptRoom = if (company) cache.bundle(room)?.room else null
            val keptTalk = cache.talk(room, StylePlaza.ROOM)?.value
            if (_state.value.talk == null && keptTalk != null) {
                val shown = stateOf(keptTalk, null, keptRoom).copy(refreshing = true)
                if (_plan.value == p) _state.value = shown
            } else if (_plan.value == p) _state.value = _state.value.copy(refreshing = true, stale = false)
            val atOnce = _state.value.talk != null
            com.bamnat.app.company.NetTimings.cached(atOnce)
            if (atOnce) com.bamnat.app.company.NetTimings.end(com.bamnat.app.company.NetTimings.Wait.PLAZA, started)
            var r: RoomPage? = keptRoom
            val talk = try {
                if (company) {
                    val b = c.companyApi.bundle(room, c.companyStore.device)
                    cache.keepBundle(b)
                    r = b.room
                    // A server's page without the talk (this phone not a member there now): the talk itself says why.
                    b.talk ?: c.companyApi.talk(c.companyStore.device, room, StylePlaza.ROOM).also { cache.keepTalk(room, StylePlaza.ROOM, it) }
                } else {
                    c.companyApi.talk(c.companyStore.device, room, StylePlaza.ROOM).also { cache.keepTalk(room, StylePlaza.ROOM, it) }
                } to null
            } catch (e: CompanyException) {
                null to companyProblem(e)
            }
            val next = if (talk.first == null && _state.value.talk != null) _state.value.copy(refreshing = false, stale = true)
                else stateOf(talk.first, talk.second, r)
            if (!atOnce && talk.first != null) com.bamnat.app.company.NetTimings.end(com.bamnat.app.company.NetTimings.Wait.PLAZA, started)
            if (_plan.value == p) _state.value = next
        }
    }

    /** The plaza's state from a talk and a company's room: 고친 점 cards and the 근무·급여 정보 lines, worked out off the screen's thread. */
    private suspend fun stateOf(talk: Talk?, error: String?, r: RoomPage?): State {
        val next = State(talk = talk, error = error)
        if (r == null) return next
        return withContext(Dispatchers.Default) {
            val mine = runCatching { scope.share.exportForServer().toString() }.getOrNull()
            val versions = (r.others + listOfNotNull(r.base?.takeIf { !r.mine }))
                .map { com.bamnat.core.plaza.RoomCards.Version(it.setup, it.text, maxOf(it.senders, it.persons)) }
            val cards = mine?.let { runCatching { com.bamnat.core.plaza.RoomCards.cards(it, versions, max = 3) }.getOrNull() }.orEmpty()
            val base = r.base?.text ?: mine
            val facts = base?.let { t -> runCatching { SetupFacts.read(t) }.getOrNull() }.orEmpty()
                .filter { it.key.startsWith("cycle:") || it.key.startsWith("shift:") || it.key.startsWith("rate:") }
                .take(5).map { it.label to it.value }
            next.copy(room = r, cards = cards, facts = facts)
        }
    }

    /**
     * [SPD-14] The talk sheet closed: when this phone wrote (or took back, voted) there, its talk as the sheet last read it takes
     * the place of the one on screen; nothing changed, nothing is read again (1.29 read the whole plaza again each time).
     */
    fun sheetClosed() {
        if (!talk.changed) return
        val t = talk.last ?: return load()
        _state.value = _state.value.copy(talk = t, error = null)
    }

    fun openTalk() {
        val p = _plan.value
        val room = p.room ?: return
        talk.show(room, StylePlaza.ROOM, p.title, hint = p.hint, note = p.note)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlazaHomeScreen(
    vm: PlazaHomeViewModel,
    onBack: (() -> Unit)?,
    /** The rule plaza of every company (규칙 광장). */
    onAll: () -> Unit,
    onCompany: (String) -> Unit,
    onRates: () -> Unit,
    onShare: () -> Unit,
    /** [CALI-08] Above everything: which calendar's 광장 this is (the calendar chips), when the phone has two or more. */
    header: (@Composable () -> Unit)? = null,
) {
    val plan by vm.plan.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val open by vm.talk.open.collectAsStateWithLifecycle()
    // [SPD-14] 1.30 AP1: the room's own posts come back changed after the sheet closes, only when this phone wrote there; the
    // plaza is not read a second time as it opens (1.29: init and this both read it).
    var sheetWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(open == null) {
        if (open != null) sheetWasOpen = true
        else if (sheetWasOpen) { sheetWasOpen = false; vm.sheetClosed() }
    }
    Scaffold(
        topBar = {
            TopAppBar(
                // [FREE-05] 광장's own emoji (사용자 2026-10-30: 배경이나 이모티콘), on this phone only.
                title = { Text(listOfNotNull(com.bamnat.app.ui.theme.LocalScreenLook.current.emoji, plan.title).joinToString(" "), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { if (onBack != null) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // [SPD-13] What the phone kept is on screen while it is read again: a thin line, not a spinner.
                RefreshLine(state.refreshing, state.stale, onRetry = vm::load)
                header?.invoke()
                vm.note?.let { n ->
                    SoftCard(highlighted = true) {
                        Text(n, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
                    }
                }
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(16.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(plan.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (plan.top == StylePlaza.Top.COMPANY_ROOM) {
                    InfoCard(state, onCompany = { plan.room?.let(onCompany) })
                    FixesCard(state, onCompany = { plan.room?.let(onCompany) }, onShare = onShare)
                }
                plan.rates?.let { r ->
                    SoftCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(r.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            Text(if (r.unit == "hour") "같은 지역 분들이 올린 시급을 금액 그대로 모아 봐요. 누가 올렸는지는 보이지 않아요."
                                else "지역·직종별로 올라온 일당·단가를 모아 봐요. 누가 올렸는지는 보이지 않아요.",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            FilledTonalButton(onClick = onRates, modifier = Modifier.heightIn(min = 48.dp)) { Text("시세 보기·올리기") }
                        }
                    }
                }
                TalkCard(plan, state, onOpen = vm::openTalk)
                (plan.alsoRoom ?: vm.backRoom.takeIf { plan.top != StylePlaza.Top.COMPANY_ROOM })?.let {
                    OutlinedButton(onClick = vm::across, modifier = Modifier.fillMaxWidth()) {
                        Text(if (plan.top == StylePlaza.Top.COMPANY_ROOM) "${styleName(it)}도 보기" else "회사 정보 광장으로")
                    }
                }
                // [J133-71] 1.33: other companies' rules mean something to 교대·일근 and to a company's room, not to 알바·현장·가게·투자.
                if (plan.top == StylePlaza.Top.COMPANY_ROOM) TextButton(onClick = onAll, modifier = Modifier.fillMaxWidth()) { Text("다른 회사 규칙 광장 보기") }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    TalkSheet(vm.talk)
}

/** [RCP-34] 1.34: on the rule plaza of 교대·일근, the way to their own plaza. */
@Composable
fun StylePlazaLink(room: String, modifier: Modifier = Modifier, onOpen: () -> Unit) {
    SoftCard(modifier) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(styleName(room), style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("회사와 상관없이 같은 근무를 하는 분들과 이야기를 나누는 곳이에요", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            FilledTonalButton(onClick = onOpen, modifier = Modifier.heightIn(min = 48.dp)) { Text("${styleName(room)} 가기") }
        }
    }
}

private fun styleName(room: String): String = when (room) {
    StylePlaza.SHIFT -> "교대 광장"
    StylePlaza.DAY -> "일근 광장"
    StylePlaza.FIELD -> "현장 광장"
    StylePlaza.SHOP -> "가게 광장"
    StylePlaza.HOURLY -> "알바 광장"
    StylePlaza.INVEST -> "투자 이야기"
    else -> "유형 광장"
}

/** 근무·급여 정보: what the company's setup in use says, in the plaza's words (no amounts). */
@Composable
private fun InfoCard(state: PlazaHomeViewModel.State, onCompany: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("근무·급여 정보", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val r = state.room
            if (state.facts.isEmpty()) Text(if (r == null) "불러오는 중이에요" else "아직 정리된 정보가 없어요",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.facts.forEach { (label, value) ->
                Row {
                    Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(88.dp))
                    Text(value, style = MaterialTheme.typography.bodyMedium)
                }
            }
            r?.base?.let { b ->
                Text((if (b.adopted) "채택된 설정" else "확인 중인 설정") + " · 쓰는 분 ${b.uses}명" + (b.version?.let { " · v$it" } ?: ""),
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onCompany) { Text("회사 화면에서 자세히") }
        }
    }
}

/** 고친 점: what other versions of the company did differently, with how many stand behind each. */
@Composable
private fun FixesCard(state: PlazaHomeViewModel.State, onCompany: () -> Unit, onShare: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("고친 점", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (state.cards.isEmpty()) Text("같은 회사에서 다르게 고친 분이 아직 없어요. 다른 점이 있으면 고쳐서 보내 주세요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.cards.forEach { Text("• ${it.text}", style = MaterialTheme.typography.bodyMedium) }
            Row {
                if (state.cards.isNotEmpty()) TextButton(onClick = onCompany) { Text("회사 화면에서 이 값 쓰기") }
                TextButton(onClick = onShare) { Text("내 설정 보내기") }
            }
        }
    }
}

/** 자유 토의: the newest few posts, and the sheet to read and write them all. */
@Composable
private fun TalkCard(plan: StylePlaza.Plan, state: PlazaHomeViewModel.State, onOpen: () -> Unit) {
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(plan.roomTitle.ifEmpty { "자유 토의" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            val talk = state.talk
            when {
                talk == null && state.error != null -> Text(state.error, style = MaterialTheme.typography.bodyMedium)
                talk == null -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                talk.posts.isEmpty() -> Text("아직 글이 없어요. 처음 이야기를 꺼내 주세요.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                else -> talk.posts.filter { it.parent == null && it.hidden == null }.takeLast(3).reversed().forEach { p ->
                    Column {
                        Text(if (p.mine) "나 (${p.nick ?: "참여자 ${p.who}"})" else p.nick ?: "참여자 ${p.who}", style = MaterialTheme.typography.labelLarge)
                        Text(p.body, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        val replies = talk.posts.count { it.parent == p.id }
                        if (replies > 0) Text("댓글 ${replies}개", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            talk?.leftPosts?.let { n -> Text("오늘 ${n}번 더 쓸 수 있어요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Button(onClick = onOpen, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("글 보기·쓰기") }
        }
    }
}
