package com.bamnat.app.ui.company

import kotlinx.coroutines.Dispatchers
import com.bamnat.core.plaza.DeptGroups
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Apartment
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.company.AppliedSetup
import com.bamnat.app.company.CompanyHit
import com.bamnat.app.company.CompanyPage
import com.bamnat.app.company.SharedSetup
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ShiftBadge
import kotlinx.coroutines.launch
import java.time.LocalDate
import com.bamnat.core.time.Today

/**
 * [COMM-01] 회사 찾기, the first step of setting up work when a company server is there: a coworker's
 * setup in a few taps, or the way on to set it up by hand under the company's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyFindScreen(
    vm: CompanyFindViewModel,
    setup: Boolean,
    onBack: () -> Unit,
    onOpen: (String) -> Unit,
    onByHand: (String) -> Unit,
    /** Screenshots: no keyboard over the results. */
    focus: Boolean = true,
    /** The user's own setup is already in, so a company that is not here leads to sharing it. */
    ready: Boolean = false,
    /** [COMM-07] Every company's rules, to look through without a name. */
    onBoard: (() -> Unit)? = null,
    /**
     * [SPAT-02] 첫 설정: a public row (or the user's own company, first to give it) with the order picked, into the draft.
     * Elsewhere it is null and the row goes on the calendar from its own sheet, then on to [onPay] or [onShare].
     */
    onPublic: ((PublicCycle, String) -> Unit)? = null,
    onPay: () -> Unit = {},
    onShare: () -> Unit = {},
) {
    // [STAB-01] Read at once, on the same frame as the key: a text box one frame behind the keyboard
    // doubles or drops half-built Korean letters and moves the cursor.
    val query by vm.query.collectAsStateWithLifecycle(context = Dispatchers.Main.immediate)
    // [NAV-02][PLAZA-03] Back first clears the search, as 메모·설정 do (1.25 Z), and only then leaves the screen.
    androidx.activity.compose.BackHandler(enabled = query.isNotEmpty()) { vm.query.value = "" }
    val result by vm.result.collectAsStateWithLifecycle()
    val terms by vm.terms.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    if (focus) LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    // [SPAT-02] The user's own company when neither the public rows nor the coworkers have it (사용자 2026-10-25 14:05).
    var providing by remember { mutableStateOf<PublicCycle?>(null) }
    val byHand = { name: String ->
        keyboard?.hide()
        vm.byHand(name)
        onByHand(name.trim())
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (setup) "" else "회사 찾기") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                actions = { if (setup) TextButton(onClick = { byHand("") }) { Text("건너뛰기") } },
            )
        },
    ) { padding ->
        // [J133-75] 중간 빌드 3 확인 ("자판이 결과를 가려 현대제철(3번째)이 안 보여요. 제목을 눌러도 자판이 안 내려가요"): a tap
        // on the title, or a drag of the list, puts the keyboard away; once a name is typed the picture and the
        // explanation give their room to the results.
        val hideOnDrag = com.bamnat.app.ui.components.rememberHideKeyboardOnDrag()
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().nestedScroll(hideOnDrag).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val typed = query.isNotBlank()
                if (!typed) HeroIcon(Icons.Outlined.Apartment)
                Text(if (setup) "다니는 회사를 찾아볼까요?" else "회사 이름으로 찾아요", style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.clickable(interactionSource = null, indication = null) { keyboard?.hide() })
                if (!typed) Text(
                    if (vm.server) "같은 회사 분들이 맞춰 둔 근무표와 급여 계산 방식을 그대로 가져와요. 금액은 각자 넣어요."
                    // [SPAT-02] Without the company server: the public cycles only.
                    else "기사·채용 공고·노무 사이트에 나온 회사 근무주기를 찾아요. 고른 뒤 내 근무와 맞춰 봐요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it.take(40) },
                    placeholder = { Text("회사 이름이나 초성 (예: 예시정밀, ㅇㅅㅈㅁ)") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Filled.Close, "지우기") }
                    },
                    singleLine = true,
                    shape = MaterialTheme.shapes.extraLarge,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )
                // AS 1.29 [SRCH-10] Companies already on screen while the server is still asked: say where it stands.
                val stage by vm.stage.collectAsStateWithLifecycle()
                stage?.let { SearchStageLine(it, nothingYet = false) }
                // [SRCH-12] once per name, not on every frame.
                val publicFound = remember((result as? CompanyFindViewModel.Result.Found)?.query) {
                    (result as? CompanyFindViewModel.Result.Found)?.let { PublicCycles.search(it.query).isNotEmpty() } ?: false
                }
                AnimatedContent(
                    targetState = result,
                    contentKey = { it::class },
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "company-search",
                ) { r ->
                    when (r) {
                        CompanyFindViewModel.Result.Idle -> HowItWorks()
                        CompanyFindViewModel.Result.Searching -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                            Text("찾고 있어요…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // [SRCH-10] Where the companies will be, so the list does not jump when they come.
                            repeat(2) { SkeletonRow() }
                        }
                        is CompanyFindViewModel.Result.Found -> if (r.hits.isEmpty()) {
                            if (!publicFound) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    FirstProviderCard(r.query.trim(), rewardShort(terms), onStart = { form ->
                                        keyboard?.hide()
                                        providing = PublicCycles.provided(r.query.trim(), form)
                                    }, plaza = vm.server)
                                    if (setup) TextButton(onClick = { byHand(r.query) }) { Text("‘${r.query.trim()}’ 근무를 처음부터 직접 정할게요") }
                                    else if (ready && vm.server) TextButton(onClick = { byHand(r.query) }) { Text("지금 쓰는 내 설정을 ‘${r.query.trim()}’로 나눌게요") }
                                }
                            }
                            // [SPAT-02][SPAT-07] Otherwise the public rows below are the answer, and '여기 없어요' comes after them.
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                // [PLAZA-03] 초성 찾기 only offers companies whose rule is in use, so a guess never leads to an empty page.
                                if (com.bamnat.core.plaza.Choseong.isQuery(r.query)) {
                                    Text("초성으로 찾았어요. 쓰는 설정이 있는 회사만 보여요.", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                r.hits.forEach { hit -> CompanyRow(hit) { keyboard?.hide(); onOpen(hit.id) } }
                            }
                        }
                        is CompanyFindViewModel.Result.Failed -> Offline(r.code, onRetry = vm::retry) { byHand(r.query) }
                    }
                }
                // [SPAT-02] The public rows under the coworkers' setups, never looking like one of them (D79).
                PublicCycleSection(query, onPick = onPublic, onPay = onPay, onShare = onShare,
                    browse = result is CompanyFindViewModel.Result.Idle)
                // [SPAT-07] 1.27 AH (사용자 2026-10-25): the coworkers' companies, then the public rows, then '여기 없어요' last.
                (result as? CompanyFindViewModel.Result.Found)?.takeIf { it.hits.isNotEmpty() || publicFound }?.let { r ->
                    TextButton(onClick = { byHand(r.query) }) {
                        Text(if (ready) "여기 없어요. ‘${r.query.trim()}’로 내 설정을 나눌게요" else "여기 없어요. ‘${r.query.trim()}’ 직접 설정할게요")
                    }
                }
                if (onBoard != null) {
                    OutlinedButton(onClick = { keyboard?.hide(); onBoard() }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.Forum, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("회사 규칙 광장에서 모든 회사 규칙 보기")
                    }
                }
                if (setup && result !is CompanyFindViewModel.Result.Found) {
                    TextButton(onClick = { byHand(query) }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text("회사 없이 직접 설정할게요")
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    providing?.let { p ->
        PublicApplySheet(p, onDismiss = { providing = null }, onPay = { providing = null; onPay() }, onShare = { providing = null; onShare() },
            pick = onPublic?.let { f -> { codes: String -> providing = null; f(p, codes) } })
    }
}

@Composable
private fun HowItWorks() {
    SoftCard {
        Column(Modifier.padding(16.dp)) {
            Text("이렇게 끝나요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            HowStep(1, "회사 찾기", "띄어쓰기나 (주)는 빼고, 초성(ㅇㅅㅈㅁ)만 적어도 찾아요")
            HowStep(2, "오늘 근무 고르기", "같은 회사라도 조마다 순서가 달라서, 오늘이 어떤 근무인지만 골라요")
            HowStep(3, "내 기본급 넣기", "근무 시간, 연장·휴무일 근무, 가산 방식은 이미 들어 있어요")
        }
    }
}

@Composable
private fun CompanyRow(hit: CompanyHit, onClick: () -> Unit) {
    SoftCard(Modifier.clip(MaterialTheme.shapes.large).clickable(onClick = onClick)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(42.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(hit.name.trim().removePrefix("(주)").trim().take(1), style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(hit.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                // AS 1.29 [SRCH-01] (사용자 2026-10-27 "광장에 대표판이 있는데 회사 검색 시 뜨지 않는다"): 회사 찾기 showed only how
                // many setups a company had, never its 대표 주기 or who stands behind it, which the line under the name box did
                // (1.27 AH-I4). Now both lists say the same.
                leadText(hit.lead)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
                Text(
                    listOfNotNull(
                        hit.alias?.let { "다른 이름 $it" },
                        if (hit.lead == null && hit.users == 0) listOfNotNull(
                            if (hit.adopted > 0) "쓰는 설정 ${hit.adopted}개" else null,
                            if (hit.pending > 0) "확인 중 ${hit.pending}개" else null,
                        ).joinToString(" · ") else trustText(hit),
                    ).filter { it.isNotEmpty() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hit.lead?.doubted == true) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable
private fun Offline(code: String, onRetry: () -> Unit, onByHand: () -> Unit) {
    SoftCard {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(10.dp))
                Text(if (code == "offline") "인터넷에 연결되지 않아 찾지 못했어요" else companyProblem(code),
                    style = MaterialTheme.typography.titleSmall)
            }
            Text("연결한 뒤 다시 찾거나, 지금은 직접 설정해도 돼요. 나중에 설정 > 회사 찾기에서 다시 찾을 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRetry) { Text("다시 찾기") }
                TextButton(onClick = onByHand) { Text("직접 설정하기") }
            }
        }
    }
}

/**
 * [COMM-01] A company's setups, each in plain words with how many coworkers stand behind it, and
 * "이 설정 쓰기" from today's place in its rotation.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyDetailScreen(
    vm: CompanyDetailViewModel,
    setup: Boolean,
    onBack: () -> Unit,
    onApplied: (CompanyShare.Applied, String) -> Unit,
    onByHand: (String) -> Unit,
    /** Screenshots only: open the rotation sheet for this setup. */
    openPhasesFor: String? = null,
    /** The user's own setup is already in: "우리 부서 설정이 없나요?" leads to sharing it. */
    ready: Boolean = false,
    /** [SETUP-01] In the first setup a setup is only chosen here; it is saved with all the other answers. */
    onPick: ((CompanyPage, SharedSetup) -> Unit)? = null,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val appliedMain by vm.applied.collectAsStateWithLifecycle()
    // [SPAT-07] 1.27 AH (사용자 2026-10-25): what the main calendar uses is not what a new calendar or the first setup
    // picks here — 'n명 쓰고 있어요' opened from a new calendar said '다시 넣기'. Picking, nothing is in use yet (AH-D2).
    val applied = appliedMain.takeIf { onPick == null }
    val hasPattern by vm.hasPattern.collectAsStateWithLifecycle()
    val votes by vm.votes.collectAsStateWithLifecycle()
    val room by vm.room.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var choosing by remember { mutableStateOf<Pair<SharedSetup, Phases>?>(null) }
    var applying by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    // [PLAZA-09] What 이 값 쓰기 changed, said once.
    var notice by remember { mutableStateOf<String?>(null) }
    val page = (state as? CompanyDetailViewModel.State.Loaded)?.page
    // [PLAZA-02] 다른 점 말하기 on one rule set, with this phone's own setup for "내 설정으로 채우기".
    var proposing by remember { mutableStateOf<SharedSetup?>(null) }
    var mine by remember { mutableStateOf<String?>(null) }
    var sending by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    // [PLAZA-02][PLAZA-05] Two rule sets of one department side by side: the 대표 판 and another, or a past version.
    var comparing by remember { mutableStateOf<Compare?>(null) }
    var keepOld by rememberSaveable { mutableStateOf(false) }

    // [PAYF-03] 1.29 AP: whether the pay way goes in with the cycle, asked once before the order (null: not asked, or no pay way).
    var withPay by remember { mutableStateOf(true) }
    var askingPay by remember { mutableStateOf<SharedSetup?>(null) }
    fun use(p: CompanyPage, s: SharedSetup, step: Int?) {
        applying = true
        vm.apply(p, s, step, withPay = withPay, done = { r -> applying = false; choosing = null; onApplied(r, p.name) },
            failed = { applying = false; choosing = null; error = it })
    }
    fun order(p: CompanyPage, s: SharedSetup) {
        scope.launch {
            val phases = vm.phases(s)
            if (phases != null && phases.options.size > 1) choosing = s to phases else use(p, s, if (phases != null) 0 else null)
        }
    }
    fun start(p: CompanyPage, s: SharedSetup) {
        if (onPick != null) return onPick(p, s)
        // [PAYF-03] 사용자 2026-10-27: 'n명이 쓰고 있어요'를 눌러 적용할 때 급여까지 같이 넣을지 한 번 묻기.
        if (s.hasPay) askingPay = s else { withPay = false; order(p, s) }
    }
    askingPay?.let { s ->
        val p = page
        if (p != null) WithPayDialog(p.name, s, calendar = vm.calendar, onAnswer = { yes -> askingPay = null; withPay = yes; order(p, s) }, onDismiss = { askingPay = null })
    }
    if (openPhasesFor != null && page != null) {
        LaunchedEffect(page) { page.setups.firstOrNull { it.id == openPhasesFor }?.let { start(page, it) } }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(page?.name.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            when (val s = state) {
                CompanyDetailViewModel.State.Loading -> CircularProgressIndicator(Modifier.padding(top = 64.dp))
                is CompanyDetailViewModel.State.Failed -> Column(
                    Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(companyProblem(s.code), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
                    OutlinedButton(onClick = vm::load) { Text("다시 불러오기") }
                }
                is CompanyDetailViewModel.State.Loaded -> Column(
                    Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    val p = s.page
                    // [SPD-13] 1.30 AP1: the page kept from before is on screen while it is read again.
                    RefreshLine(vm.refreshing.collectAsStateWithLifecycle().value, vm.stale.collectAsStateWithLifecycle().value, onRetry = vm::load)
                    if (p.aliases.isNotEmpty()) {
                        Text("다른 이름: ${p.aliases.joinToString(", ")}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Info, null, Modifier.size(18.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("같은 회사 분들이 알려 준 정보예요. 회사가 낸 공식 정보는 아니어서, 첫 명세서와 한 번 맞춰 봐 주세요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (p.setups.isEmpty()) {
                        Text("아직 볼 수 있는 설정이 없어요.", style = MaterialTheme.typography.bodyLarge)
                    }
                    // [J133-88] No pay way here yet: the public record of this very company, said as the record's.
                    if (p.setups.none { it.hasPay }) PublicFirmCard(p.name, onlyExact = true)
                    // [PLAZA-09] 결정 D79: a member's room, only what the others changed, each value to take alone.
                    room?.takeIf { onPick == null }?.let { r ->
                        RoomSection(r, onTake = { card -> vm.take(card) { msg, ok -> if (ok) { error = null; notice = msg } else error = msg } })
                    }
                    // [PLAZA-08] Not a member: shift times and their order only; the pay way shows after joining.
                    if (!p.member && p.setups.any { it.payHidden }) {
                        Text(if (p.limited) "오늘은 많이 봐서 근무 시각·순서만 보여요. 내일 다시 보여요."
                            else "급여 계산 방식은 이 회사 설정을 쓰는(참여한) 분께만 보여요. 쓰면 이 회사 광장에 참여돼요.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // [PLAZA-05] The rule set this phone used is no longer the department's 대표 판 (결정 D74): say so once, with
                    // a compare against this phone's own setup, and leave the choice to keep it.
                    val old = applied?.takeIf { it.companyId == p.id && p.setups.none { su -> su.id == it.setup } }
                    val lead = old?.let { o -> p.setups.firstOrNull { it.adopted && it.dept == o.dept } }
                    if (old != null && lead != null && onPick == null && !keepOld) {
                        SoftCard(highlighted = true) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("쓰시던 판 대신 새 대표 판이 생겼어요", style = MaterialTheme.typography.titleSmall)
                                Text("같은 부서 분들이 더 많이 쓰는 판이에요. 내 설정과 비교해 보고 바꿀지 정해 주세요. 그대로 두셔도 돼요.",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { scope.launch { vm.mine()?.let { m -> comparing = Compare("내 설정과 새 대표 판", m, lead.text, "내 설정", "대표 판", lead) } } }) {
                                        Text("비교하고 바꾸기")
                                    }
                                    TextButton(onClick = { keepOld = true }) { Text("그대로 둘게요") }
                                }
                            }
                        }
                    }
                    // [COMM-14] What only this set has, beside another of the same department.
                    val diffs = remember(p) { com.bamnat.core.plaza.SetupDiff.diff(p.setups.map { it.diffItem() }) }
                    // [PLZD-06][PLZD-08] 1.30 BD: by department, one 대표 판 each; the version in use said first.
                    val sections = remember(p) { deptSections(p) }
                    var opened by rememberSaveable { mutableStateOf(listOf<String>()) }
                    applied?.takeIf { onPick == null && it.companyId == p.id }?.let { a -> p.setups.firstOrNull { it.id == a.setup } }?.let { u ->
                        val lead = sections.firstOrNull { sec -> sec.lead.id == u.id || sec.others.any { it.id == u.id } }?.lead
                        MyVersionBand(u, lead, onCompareLead = lead?.takeIf { it.id != u.id }?.let { l ->
                            { comparing = Compare("대표 판과 비교", l.text, u.text, "대표 판", "내가 쓰는 판", l) }
                        })
                    }
                    @Composable
                    fun card(su: SharedSetup, lead: SharedSetup) {
                        val using = applied?.takeIf { it.companyId == p.id && it.setup == su.id }
                        SetupCard(su, diffs[su.id], using, onUse = { start(p, su) },
                            // [PLAZA-09] A member reads the others' pay as 고친 점 cards above, the own one in full.
                            showPay = room == null || using != null || room?.page?.base?.setup == su.id,
                            // [COMM-06][COMM-07] Anyone who knows the company can vote and talk it over, using it or not.
                            vote = votes[su.id] ?: applied?.takeIf { it.companyId == p.id && it.setup == su.id }?.vote,
                            onVote = { ok, why -> vm.vote(p, su, ok, why) { e -> error = e } },
                            onTalk = { vm.talk.show(p.id, su.id, "${p.name} · ${su.dept.ifEmpty { "회사 전체" }}") },
                            onPropose = { vm.talk.show(p.id, su.id, "${p.name} · ${su.dept.ifEmpty { "회사 전체" }}", kind = "proposal") },
                            // [PLZ-10][PLZ-04] My own month with these rules, and how the rule got here.
                            onTry = if (su.hasPay) ({ vm.tryPay(su) }) else null,
                            onHistory = if (su.adopted && (su.version ?: 1) > 1) ({ vm.showHistory(p, su) }) else null,
                            onDiff = if (onPick == null) ({ sendError = null; proposing = su; scope.launch { mine = vm.mine() } }) else null,
                            onCompare = lead.takeIf { it.id != su.id }?.let { l ->
                                { comparing = Compare("대표 판과 비교", l.text, su.text, "대표 판", "이 판", su) }
                            },
                        )
                    }
                    sections.forEach { sec ->
                        card(sec.lead, sec.lead)
                        // The version this calendar uses stays open even when it is not the 대표.
                        val inUse = sec.others.firstOrNull { o -> applied?.let { it.companyId == p.id && it.setup == o.id } == true }
                        inUse?.let { card(it, sec.lead) }
                        val folded = sec.others.filter { it !== inUse }
                        if (folded.isNotEmpty()) {
                            val key = DeptGroups.key(sec.dept)
                            val open = key in opened
                            OthersFold(folded.size, open) { opened = if (open) opened - key else opened + key }
                            if (open) folded.forEach { o ->
                                OtherVersionCard(o, diffs[o.id], using = null, onUse = { start(p, o) },
                                    onCompare = { comparing = Compare("대표 판과 비교", sec.lead.text, o.text, "대표 판", "이 판", o) },
                                    onTalk = { vm.talk.show(p.id, o.id, "${p.name} · ${o.dept.ifEmpty { "회사 전체" }}") })
                            }
                        }
                    }
                    SoftCard {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("우리 부서 설정이 없나요?", style = MaterialTheme.typography.titleSmall)
                            Text(if (ready) "지금 쓰는 설정을 나눠 주시면 같은 부서 분들이 다음부터 바로 써요." else "직접 맞춘 뒤 나눠 주시면 같은 부서 분들이 다음부터 바로 써요.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { vm.byHand(p); onByHand(p.name) }) { Text(if (ready) "우리 부서 설정 나누기" else "직접 설정하고 나누기") }
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
            notice?.takeIf { error == null }?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = { notice = null }) { Text("닫기") }
                    }
                }
            }
            error?.let { msg ->
                Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).navigationBarsPadding()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = { error = null }) { Text("닫기") }
                    }
                }
            }
        }
    }

    TalkSheet(vm.talk)
    TrialSheet(vm.trial.collectAsStateWithLifecycle().value, onClose = vm::closeTrial,
        onVote = { ok -> page?.setups?.firstOrNull { it.id == vm.trial.value?.setup }?.let { su -> vm.vote(page, su, ok, if (ok) null else "wrong") { e -> error = e } } })
    HistorySheet(vm.history.collectAsStateWithLifecycle().value, onClose = vm::closeHistory,
        // [PLAZA-05] A past version stays: compared with the one in use, and put back on this phone if it fits better.
        onCompare = { v, now ->
            val su = page?.setups?.firstOrNull { it.id == now.setup }
            if (v.body != null && now.body != null && su != null) {
                vm.closeHistory()
                comparing = Compare("v${v.version}과 지금 비교", now.body, v.body, "지금 v${now.version}", "v${v.version}",
                    su.copy(id = v.setup, text = v.body, version = v.version, adopted = false), back = true)
            }
        })
    proposing?.let { su ->
        // [FIN130-02] The calendar's pay uses this very version: what is changed may go onto it too (PROP-03).
        val payWay by companyScope(androidx.compose.ui.platform.LocalContext.current.appContainer).payWays.way.collectAsStateWithLifecycle()
        DiffEditorSheet(
            title = "${page?.name.orEmpty()} · ${su.dept.ifEmpty { "회사 전체" }}",
            base = su.text, mine = mine, busy = sending, error = sendError,
            alsoMine = payWay?.setup == su.id,
            payHidden = su.payHidden,
            onSend = { lines, text, why ->
                val p = page ?: return@DiffEditorSheet
                sending = true
                vm.propose(p, su, lines, text, why) { e -> sending = false; sendError = e; if (e == null) proposing = null }
            },
            onDismiss = { proposing = null },
        )
    }
    comparing?.let { cmp ->
        CompareSheet(cmp.title, cmp.before, cmp.after, cmp.beforeName, cmp.afterName, onDismiss = { comparing = null },
            note = if (cmp.back) "지난 판도 지워지지 않아요. 우리에게 더 맞으면 이 휴대폰에 다시 넣을 수 있어요." else null,
            action = if (onPick != null || page == null) null else ({
                Button(onClick = { comparing = null; start(page, cmp.setup) }, modifier = Modifier.fillMaxWidth()) {
                    Text(when { cmp.back -> "이 판으로 되돌리기"; cmp.setup.adopted -> "이 판으로 바꾸기"; else -> "이 판 써 보기" })
                }
            }))
    }
    val pick = choosing
    if (pick != null && page != null) {
        PhaseSheet(
            phases = pick.second,
            replacing = hasPattern == true,
            busy = applying,
            onDismiss = { if (!applying) choosing = null },
            onPick = { step -> use(page, pick.first, step) },
        )
    }
}

@Composable
private fun SetupCard(
    s: SharedSetup,
    diff: String? = null,
    applied: AppliedSetup?,
    onUse: () -> Unit,
    vote: Boolean? = null,
    onVote: ((Boolean, String?) -> Unit)? = null,
    onTalk: (() -> Unit)? = null,
    /** [J133-55] After 달라요 when there is no proposal form here: the talk, on 고치자는 제안. */
    onPropose: (() -> Unit)? = null,
    onTry: (() -> Unit)? = null,
    onHistory: (() -> Unit)? = null,
    onDiff: (() -> Unit)? = null,
    onCompare: (() -> Unit)? = null,
    showPay: Boolean = true,
) {
    var more by rememberSaveable(s.id) { mutableStateOf(false) }
    val main = s.lines.filter { (label, _) -> label == "근무" || label.startsWith("순서") || label == "연장·휴무일 근무" }
    val pay = if (showPay) s.lines - main.toSet() else emptyList()
    SoftCard(highlighted = applied != null) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.dept.ifEmpty { "회사 전체" }, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                // [PLAZA-05] 결정 D74: the department's 대표 판 is the one most phones use; earlier versions stay in 바뀐 기록.
                if (s.adopted) StatusChip("대표 판${s.version?.let { " v$it" } ?: ""}", Tone.GOOD)
                // [PLAZA-10] 결정 D79: the first provider's version shows at once, as 제보 1 · 확인 중.
                else StatusChip(if (s.senders <= 1) "제보 1 · 확인 중" else "확인 중", Tone.WAIT)
                // [PLAZA-09] 안정: settled, no more nudges.
                if (s.stable) { Spacer(Modifier.width(6.dp)); StatusChip("안정", Tone.GOOD) }
            }
            // [PLAZA-14][PLAZA-15] 1.27 AH: the one shown first for its department, or why it no longer is.
            // [PLZD-06] 1.30 BD: what kind of work it is, in one line, before any detail.
            remember(s.text) { DeptGroups.summary(s.text) }?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium) }
            if (s.doubted) StatusChip("달라요가 많아요 · 다시 확인 중", Tone.WAIT)
            else if (s.lead) StatusChip("대표 주기 · 찾는 분께 먼저 보여요", Tone.GOOD)
            diff?.let { Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.primary) }
            TrustLine(s.trust, s.agree, s.persons, s.bad, s.adopted)
            if (!s.adopted) PointsBar(s.points, s.threshold)
            WaitReason(s.points, s.threshold, s.adopted, s.agree)
            // [PLZ-08] Who stands behind it, in the names they chose.
            WhoLine(s.senders, s.checked, s.uses, s.by, s.checkedLong)
            main.forEach { (label, text) -> SetupLine(label, text) }
            if (pay.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small).clickable { more = !more }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(if (more) "급여 계산 방식 접기" else "급여 계산 방식 보기 (${pay.size}가지)", style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, null, tint = MaterialTheme.colorScheme.primary)
                }
                AnimatedVisibility(more) { Column { pay.forEach { (label, text) -> SetupLine(label, text) } } }
            } else if (s.payHidden) {
                // [PLAZA-08] 결정 D79: that there is one and how many stand behind it, never its values.
                Text("급여 방식 있음 · 확인 ${s.persons}명 · 쓰면 보여요", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (!s.hasPay) {
                Text("급여 계산 방식은 없어요. 근무표만 가져와요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (applied != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("지금 이 설정을 쓰고 있어요", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (s.adopted) Button(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text(if (applied != null) "다시 넣기" else "이 설정 쓰기") }
            else FilledTonalButton(onClick = onUse, modifier = Modifier.fillMaxWidth()) { Text("확인 중이지만 써 보기") }
            if (onVote != null) {
                Text("다니는 회사라면 이 규칙이 맞는지 알려 주세요", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                VoteButtons(vote ?: s.opinion.mine?.let { it == "ok" }, onVote, s.opinion, onPropose = onDiff ?: onPropose)
            }
            if (onTry != null || onHistory != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    onTry?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("내 월급으로 시험") } }
                    onHistory?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("바뀐 기록") } }
                }
            }
            if (onDiff != null || onCompare != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    // [PLAZA-02] U2: what is different here, said on this rule set's own values, not in a blank box.
                    onDiff?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("우리는 달라요 · 다른 점 말하기") } }
                    onCompare?.let { OutlinedButton(onClick = it, modifier = Modifier.weight(1f)) { Text("대표 판과 비교") } }
                }
            }
            if (onTalk != null) {
                TextButton(onClick = onTalk, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(if (s.posts > 0) "토론 ${s.posts}개 보기 · 고치자고 제안하기" else "토론 열기 · 고치자고 제안하기")
                }
            }
        }
    }
}

/**
 * [PLAZA-09] 우리 회사 광장 (결정 D79), for a phone that joined: where the version in use stands (제보 1 · 확인 중, 대표 판,
 * 안정) and what others changed against this phone's own setup, one card per value with 이 값 쓰기.
 */
@Composable
internal fun RoomSection(room: CompanyDetailViewModel.Room, onTake: (com.bamnat.core.plaza.RoomCard) -> Unit) {
    val r = room.page
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("우리 회사 광장", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                val b = r.base
                when {
                    b == null -> Unit
                    b.adopted -> StatusChip("대표 판${b.version?.let { " v$it" } ?: ""}", Tone.GOOD)
                    r.first -> StatusChip("제보 1 · 확인 중", Tone.WAIT)
                    else -> StatusChip("확인 중", Tone.WAIT)
                }
                if (r.stable) { Spacer(Modifier.width(6.dp)); StatusChip("안정", Tone.GOOD) }
            }
            TeamsTodayLine()
            Text(
                when {
                    r.stable -> "같은 판을 ${r.stablePhones}대 넘게 쓰고 ${r.stableDays}일 동안 바뀐 게 없어요. 이제 바뀐 값 알림은 보내지 않아요."
                    r.first -> "처음 알려 준 판이에요. 같은 회사 분들이 맞아요·달라요로 다듬어 가요."
                    else -> "참여했어요. 다른 분들이 바꾼 값만 모아 보여요."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (room.cards.isEmpty()) {
                Text("내 설정과 다르게 쓰는 분이 아직 없어요.", style = MaterialTheme.typography.bodyMedium)
            }
            room.cards.forEach { card ->
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(card.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (card.canTake) TextButton(onClick = { onTake(card) }) { Text("이 값 쓰기") }
                }
                if (!card.canTake) {
                    Text("근무 순서는 판 전체를 쓰면 바뀌어요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * "오늘은 어떤 근무예요?": the rotation as rows of the coming week, one per place today could be in
 * it. Coworkers on other teams share the order on other days, so only the user can say [COMM-01].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PhaseSheet(phases: Phases, replacing: Boolean, busy: Boolean, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    var picked by remember(phases) { mutableStateOf(phases.current ?: phases.sent) }
    val today = remember { Today.date() }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("오늘은 어떤 근무예요?", style = MaterialTheme.typography.titleLarge)
            // [SETUP-02] A coworker on another 조 shares the order on other days: the sender's row is only a start.
            val sender = phases.team?.let { "보낸 분($it)" } ?: "보낸 분"
            Text(
                "${Fmt.dayTitle(today)} · ${phases.cycle}일 순서예요. " + when {
                    phases.current != null -> "지금 달력과 같은 줄을 골라 두었어요."
                    phases.sent != null -> "${sender}의 오늘 줄을 골라 두었어요. 조가 다르면 내 근무와 같은 줄로 바꿔 주세요."
                    else -> "다음 일주일이 내 근무와 같은 줄을 골라 주세요."
                },
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.fillMaxWidth().padding(start = 52.dp, end = 8.dp, top = 4.dp)) {
                (0 until 7).forEach { d ->
                    val day = today.plusDays(d.toLong())
                    Text(if (d == 0) "오늘" else Fmt.dayOfWeekShort(day.dayOfWeek), style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (d == 0) FontWeight.Bold else FontWeight.Normal,
                        color = if (d == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                }
            }
            phases.options.forEachIndexed { row, o ->
                val selected = picked == o.step
                val scheme = MaterialTheme.colorScheme
                // [COMM-13] One name for the row, so TalkBack reads "2번째 줄: 오늘 주간, 내일 야간, …" and not 56 letters in a run.
                val spoken = "${row + 1}번째 줄: " + o.days.mapIndexed { d, t ->
                    val day = when (d) { 0 -> "오늘"; 1 -> "내일"; else -> Fmt.dayOfWeekShort(today.plusDays(d.toLong()).dayOfWeek) + "요일" }
                    "$day ${t?.name ?: "휴무"}"
                }.joinToString(", ")
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .background(if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLow)
                        .border(if (selected) 2.dp else 0.dp, if (selected) scheme.primary else scheme.surfaceContainerLow, MaterialTheme.shapes.medium)
                        .selectable(selected = selected, role = Role.RadioButton) { picked = o.step }
                        .clearAndSetSemantics {
                            contentDescription = spoken
                            this.selected = selected
                            role = Role.RadioButton
                            onClick { picked = o.step; true }
                        }
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(horizontal = 10.dp))
                    o.days.forEach { t ->
                        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                            ShiftBadge(t, size = 30.dp, text = t?.label ?: "휴", outlined = t == null || t.isOff)
                        }
                    }
                }
            }
            if (replacing) {
                Text("오늘부터 이 순서로 바뀌어요. 지난 날짜와 직접 바꾼 날은 그대로예요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(onClick = { picked?.let(onPick) }, enabled = picked != null && !busy, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("이 순서로 쓸게요")
            }
            // [COMM-13] Why the button is grey, said under it rather than left to guess.
            if (picked == null) {
                Text("내 근무와 같은 줄을 먼저 골라 주세요", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            }
        }
    }
}

/** [PLAZA-02][PLAZA-05] What the compare sheet shows: [setup] is the second one, ready to apply; [back] for a past version. */
private data class Compare(
    val title: String,
    val before: String,
    val after: String,
    val beforeName: String,
    val afterName: String,
    val setup: SharedSetup,
    val back: Boolean = false,
)

/**
 * [PAYF-03] 1.29 묶음 AP (사용자 2026-10-27 "회사검색시 n명이 쓰고있어요 클릭해서 적용시 급여부분까지 같이 적용을 할것인지 묻게"):
 * a company setup with a pay way, before its order is picked: the pay way too, or the cycle alone. Yes leads to the 기본급 next.
 */
@Composable
internal fun WithPayDialog(company: String, setup: SharedSetup, onAnswer: (Boolean) -> Unit, onDismiss: () -> Unit,
    calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN) {
    // [J133-54] 1.33 (실사용 테스트 "넣기 전에 내 월급이 어떻게 바뀌는지 안 보여요"): this month before and after, on my own pay.
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val trial by androidx.compose.runtime.produceState<Trial?>(null, setup.id, calendar) {
        value = if (c.companyOf(calendar).pay.profile.value == null) null
            else runCatching { payTrial(c, setup.text, setup.id, Today.month(), calendar, setup.payHidden) }.getOrNull()
    }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("급여 계산 방식도 같이 넣을까요?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("‘$company’ 분들이 맞춘 급여 계산 방식이 있어요" + (setup.opinion.ok.takeIf { it > 0 }?.let { " · 맞아요 ${it}명" } ?: "") + ".",
                    style = MaterialTheme.typography.bodyMedium)
                Text("넣으면 수당과 계산 방식이 들어오고, 기본급만 내 것으로 넣으면 돼요. 금액은 가져오지 않아요.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TrialBeforeAfter(trial)
            }
        },
        confirmButton = { Button(onClick = { onAnswer(true) }) { Text("네, 같이 넣을게요") } },
        dismissButton = { TextButton(onClick = { onAnswer(false) }) { Text("근무주기만 넣을게요") } },
    )
}

/** [J133-54] This month before and after on the user's own pay, with the three lines that move most; nothing until it is read. */
@Composable
internal fun TrialBeforeAfter(trial: Trial?) {
    val t = trial?.takeIf { it.note == null && !it.loading && (it.now > 0 || it.then > 0) } ?: return
    val d = t.diff
    Text("이번 달 예상 세전 ${com.bamnat.app.ui.pay.won(t.now)} → ${com.bamnat.app.ui.pay.won(t.then)}" +
        if (d == 0L) " (같아요)" else " (${if (d > 0) "+" else "−"}${com.bamnat.app.ui.pay.won(kotlin.math.abs(d))})",
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
    t.lines.filter { it.now != it.then }.sortedByDescending { kotlin.math.abs(it.then - it.now) }.take(3).forEach { l ->
        Text("· ${l.label} ${com.bamnat.app.ui.pay.won(l.now)} → ${com.bamnat.app.ui.pay.won(l.then)}",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [CREW-08] 1.31 N1: 우리 회사 오늘 근무, from this phone's own calendar and teams (모든 조 보기): "오늘 A조(우리 조) 주간 · B조 야간".
 * Nothing new is sent or read from the server; with no rotation of two teams or more, nothing shows.
 */
@Composable
internal fun TeamsTodayLine() {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val snap by c.scheduleRepository.snapshot.collectAsStateWithLifecycle(initialValue = null)
    val rosters by c.teams.rosters.collectAsStateWithLifecycle()
    val today = com.bamnat.core.time.Today.date()
    val line = remember(snap, rosters, today) {
        snap?.let { com.bamnat.app.ui.calendar.teamsTodayLine(it, rosters[com.bamnat.app.data.PersonalCalendar.MAIN], today) }
    } ?: return
    Text("오늘 $line", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.semantics { contentDescription = "우리 회사 오늘 근무: $line" })
}
