package com.bamnat.app.ui.setup

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import kotlinx.coroutines.launch
import com.bamnat.app.data.BackupManager
import com.bamnat.app.data.CompanyShare
import com.bamnat.app.ui.components.ConfirmDialog
import com.bamnat.app.ui.settings.UnlockBackupDialog
import com.bamnat.app.ui.settings.restoreMessage
import com.bamnat.core.hours.HoursFormat
import com.bamnat.core.model.ShiftType
import com.bamnat.core.schedule.ShiftRole
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupStep

/**
 * The button at the bottom of a step: what it does, and why it cannot yet when it cannot. AG 1.27 [INVITE-10]: a [note]
 * said above it whatever its state (what the button sends), and a [secondary] way under it.
 */
private data class Primary(
    val label: String, val enabled: Boolean = true, val hint: String? = null,
    val note: String? = null, val secondary: Pair<String, () -> Unit>? = null, val onClick: () -> Unit,
)

/**
 * [SETUP-01][SETUP-02] The first setup: one question a screen, a bar that shows how far along it is and
 * one button at the bottom that says what it does. Nothing reaches the calendar before the last step,
 * and "나중에" keeps every answer for later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupHost(
    vm: SetupViewModel, onBrowse: ((com.bamnat.core.work.WorkStyle) -> Unit)? = null, browseFirst: Boolean = false,
    /** [SETUP-15] The work answered on the first screen: the first question is answered with it, once. */
    firstStyle: Pair<com.bamnat.core.work.WorkStyle, Boolean>? = null,
    onFirstStyleUsed: () -> Unit = {},
) {
    val d by vm.draft.collectAsStateWithLifecycle()
    val fixing by vm.fixing.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        val m = message ?: return@LaunchedEffect
        vm.consumeMessage()
        snackbar.showSnackbar(m)
    }
    LaunchedEffect(firstStyle) {
        val (style, roster) = firstStyle ?: return@LaunchedEffect
        if (d.style == null && vm.calendarId == null) vm.pickStyle(style, roster)
        onFirstStyleUsed()
    }
    BackHandler(enabled = fixing || d.previous() != null) { vm.back() }
    // [MCAL-02] A calendar being added: back on the first question gives it up, after asking.
    var leaving by remember { mutableStateOf(false) }
    BackHandler(enabled = vm.calendarId != null && !fixing && d.previous() == null) { leaving = true }
    // [PLAZA-06] 처음 설정 다시 받기 changed nothing yet: back on the first question just leaves.
    BackHandler(enabled = vm.again && !fixing && d.previous() == null) { vm.cancelAgain() }
    // [NAV-04] 1.25 Z 후속: the first setup's first question closed the app on one back; now a second is needed.
    val exitContext = LocalContext.current
    val exit = remember { com.bamnat.app.ui.ExitGuard() }
    BackHandler(enabled = vm.calendarId == null && !vm.again && !fixing && d.previous() == null) { exit.press(exitContext) }

    // One way in for a coworker's company file and for the user's own backup, as in settings.
    val context = LocalContext.current
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.readFile(context, uri)
    }
    // [CAL-47] A file opened from a messenger before the setup is done starts here, as 파일로 시작하기 would.
    val incoming by com.bamnat.app.IncomingFiles.file.collectAsStateWithLifecycle()
    LaunchedEffect(incoming) { com.bamnat.app.IncomingFiles.take()?.let { vm.readFile(context, it) } }
    val onOpenFile = { openFile.launch(arrayOf(BackupManager.MIME_TYPE, CompanyShare.MIME_TYPE, "text/plain", "application/octet-stream")) }
    var searching by remember { mutableStateOf(false) }
    // [SPAT-07] 1.27 AH: a company tapped in the list under the name opens the search at its page.
    var openCompany by remember { mutableStateOf<String?>(null) }
    var longShifts by remember { mutableStateOf<List<ShiftType>>(emptyList()) }

    // AG 1.27 [INVITE-10] 친구 초대 recognised by itself (결정 AG-D2): Play's referrer is read on the phone as the last
    // screen comes, and its one button says it tells the inviter; '알리지 않고 저장' sends nothing. Not for an added
    // calendar or 처음 설정 다시 받기 (that phone has been set up).
    val inviteContainer = LocalContext.current.appContainer
    var inviteCode by remember { mutableStateOf<String?>(null) }
    val atSummary = d.step == SetupStep.SUMMARY && !fixing && vm.calendarId == null && !vm.again
    LaunchedEffect(atSummary) {
        if (atSummary && inviteContainer.inviteApi.enabled) {
            inviteCode = runCatching { inviteContainer.invites.peek() }.getOrNull()
            if (inviteCode != null) runCatching { inviteContainer.invites.loadRules() }
        }
    }
    val inviteDays by inviteContainer.invites.store.state.collectAsStateWithLifecycle()
    val primary = primaryFor(d, vm, onLongShifts = { longShifts = it }).let { p ->
        val code = inviteCode
        if (p == null || !atSummary || code == null) p
        else p.copy(
            label = "저장하고 초대한 분께 알리기",
            note = "친구 초대 링크로 오셨어요. " + com.bamnat.app.ui.pro.inviteUseConsent(code, inviteDays.rules.days),
            secondary = "알리지 않고 저장" to { inviteContainer.invites.decline(); p.onClick() },
            onClick = {
                inviteContainer.appScope.launch { inviteContainer.invites.agree(code) }
                p.onClick()
            },
        )
    }
    val bigFont = androidx.compose.ui.platform.LocalDensity.current.fontScale >= 1.5f
    val stepsLeft = if (d.style != null && !fixing) d.steps.size - d.position else null
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text(if (fixing) "답 고치기" else vm.calendarName?.let { "$it 달력 설정" } ?: if (vm.again) "처음 설정 다시 받기" else "근무 설정",
                                style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // [SETUP-06] What is left, once the kind of work says how many questions there are. A "2/8"
                            // that became "3/7" when a company file skipped a question read as going backwards.
                            if (stepsLeft != null && !bigFont) StepsLeft(stepsLeft)
                        }
                    },
                    navigationIcon = {
                        if (fixing || d.previous() != null) {
                            IconButton(onClick = vm::back) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, if (fixing) "고치기 그만두기" else "이전 질문")
                            }
                        } else if (vm.calendarId != null) {
                            IconButton(onClick = { leaving = true }) { Icon(Icons.Filled.Close, "새 달력 그만 만들기") }
                        } else if (vm.again) {
                            IconButton(onClick = vm::cancelAgain) { Icon(Icons.Filled.Close, "다시 받기 그만두기") }
                        }
                    },
                    actions = {
                        // [MCAL-02] An added calendar is set up now or not made; its questions wait on no card.
                        if (d.step != SetupStep.STYLE && !fixing) {
                            if (vm.again) TextButton(onClick = vm::cancelAgain) { Text("그만두기") }
                            else if (vm.calendarId == null) TextButton(onClick = vm::later) { Text("나중에 할게요") }
                            else TextButton(onClick = { leaving = true }) { Text("그만두기") }
                        }
                    },
                )
                val progress by animateFloatAsState(
                    if (d.style == null) 0.08f else d.position.toFloat() / d.steps.size.coerceAtLeast(1),
                    animationSpec = tween(400, easing = FastOutSlowInEasing),
                    label = "setup-progress",
                )
                LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(3.dp), drawStopIndicator = {})
                // [A11Y-04] At 200% two lines no longer fit the bar's height, so what is left goes under it.
                if (stepsLeft != null && bigFont) StepsLeft(stepsLeft, Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                // [PLAZA-06] Said once at the top, so going through the questions again is not feared.
                if (vm.again && d.step == SetupStep.STYLE) {
                    Text("지금 달력은 그대로예요. 마지막에 저장해야 오늘부터 바뀌고, 지난 날짜와 직접 바꾼 날은 남아요. 회사 찾기도 여기서 다시 할 수 있어요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
            }
        },
        bottomBar = { primary?.let { BottomButton(it, busy = saving) } },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        AnimatedContent(
            targetState = d.step,
            transitionSpec = {
                // Forward slides in from the right, back from the left, so it is clear which way it went.
                val dir = if (targetState.ordinal >= initialState.ordinal) 1 else -1
                (slideInHorizontally(tween(STEP_MILLIS, easing = FastOutSlowInEasing)) { w -> dir * w / 5 } +
                    fadeIn(tween(STEP_MILLIS - 60, delayMillis = 60)))
                    .togetherWith(slideOutHorizontally(tween(STEP_MILLIS - 60)) { w -> -dir * w / 5 } + fadeOut(tween(120)))
                    .using(SizeTransform(clip = false))
            },
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            label = "setup-step",
        ) { step ->
            // [J133-79] 빌드 5 (첫 설정 회사 이름 칸): a drag of the step puts the keyboard away, as on 회사 찾기.
            val hideOnDrag = com.bamnat.app.ui.components.rememberHideKeyboardOnDrag()
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier
                        // [VIS-02] Unfolded, the questions stay a comfortable reading width.
                        .widthIn(max = 600.dp)
                        .fillMaxWidth()
                        .nestedScroll(hideOnDrag)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .semantics { paneTitle = step.title },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (step) {
                        SetupStep.STYLE -> StyleStep(d, onPick = vm::pickStyle, onOpenFile = onOpenFile, forCalendar = vm.calendarName,
                            onBrowse = onBrowse.takeIf { vm.calendarId == null }, browseFirst = browseFirst)
                        SetupStep.COMPANY -> CompanyStep(vm, d, onSearch = { openCompany = null; searching = true }, onOpenFile = onOpenFile,
                            onOpenCompany = { openCompany = it; searching = true }, inline = !searching)
                        SetupStep.BUNDLE -> BundleStep(d, onPick = vm::pickBundle)
                        SetupStep.TIMES -> TimesStep(vm, d)
                        SetupStep.ORDER -> OrderStep(vm, d)
                        SetupStep.TODAY -> TodayStep(vm, d)
                        SetupStep.ALARM -> AlarmStep(vm, d)
                        SetupStep.SUMMARY -> SummaryStep(vm, d)
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }

    // Field and shop work save at once from the first question: nothing to tap meanwhile.
    if (saving && d.step == SetupStep.STYLE) {
        Box(
            Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.2f))
                .clickable(remember { MutableInteractionSource() }, indication = null) {},
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }
    }

    if (searching) CompanySearchDialog(vm, initialQuery = d.companyName, onDismiss = { searching = false; openCompany = null }, initialOpen = openCompany)
    if (leaving) {
        ConfirmDialog(
            title = "새 달력을 그만 만들까요?",
            message = "지금까지 고른 답과 이 달력은 지워져요. 다른 달력은 그대로예요.",
            confirmLabel = "그만 만들기",
            destructive = true,
            onDismiss = { leaving = false },
            onConfirm = { leaving = false; vm.cancelCalendar() },
        )
    }

    val locked by vm.locked.collectAsStateWithLifecycle()
    locked?.let { UnlockBackupDialog(it, onUnlock = vm::unlock, onDismiss = vm::cancelFile) }
    val pendingRestore by vm.pendingRestore.collectAsStateWithLifecycle()
    pendingRestore?.let { (_, summary) ->
        ConfirmDialog(
            title = "쓰던 백업을 불러올까요?",
            message = restoreMessage(summary),
            confirmLabel = "불러오기",
            onDismiss = vm::cancelFile,
            onConfirm = vm::confirmRestore,
        )
    }
    if (longShifts.isNotEmpty()) {
        val t = longShifts.first()
        ConfirmDialog(
            title = "근무가 16시간이 넘어요",
            message = "${t.name} ${hoursOf(t)}, ${HoursFormat.korean(t.durationMinutes)} 근무가 맞나요? " +
                "끝나는 시각을 오전·오후로 잘못 고르면 이렇게 길어져요.",
            confirmLabel = "네, 맞아요",
            onDismiss = { longShifts = emptyList() },
            onConfirm = {
                longShifts = emptyList()
                vm.confirmTimes()
            },
        )
    }
}

/** What the bottom button does on each step; none where tapping an answer moves on by itself. */
@Composable
private fun primaryFor(d: SetupDraft, vm: SetupViewModel, onLongShifts: (List<ShiftType>) -> Unit): Primary? = when (d.step) {
    SetupStep.STYLE, SetupStep.BUNDLE -> null
    SetupStep.COMPANY -> Primary(if (d.source != null) "다음" else "건너뛰고 직접 정할게요", onClick = vm::next)
    SetupStep.TIMES -> Primary(
        "네, 시간이 맞아요",
        enabled = d.workTypes.isNotEmpty() && d.timeProblems().isEmpty(),
        hint = if (d.workTypes.isEmpty()) "근무를 하나 이상 넣어 주세요" else d.timeProblems().firstOrNull(),
    ) {
        // A 24-hour 당번 is meant to be long; anything else that long is asked once more.
        val long = d.longShifts().filter { d.roles[it.id] != ShiftRole.DUTY }
        if (long.isNotEmpty()) onLongShifts(long) else vm.confirmTimes()
    }
    SetupStep.ORDER -> when {
        d.style == com.bamnat.core.work.WorkStyle.SHIFT && d.rosterMonthly && d.painted.isEmpty() ->
            Primary("나중에 칠할게요", onClick = vm::next)
        else -> Primary("다음", enabled = d.canLeave(SetupStep.ORDER), hint = orderHint(d), onClick = vm::next)
    }
    SetupStep.TODAY -> Primary(
        "회사 근무표와 같아요",
        enabled = d.anchor != null,
        hint = if (d.anchor == null) "오늘 근무를 고르면 달력을 미리 보여 드려요" else null,
        onClick = vm::confirmToday,
    )
    SetupStep.ALARM -> Primary("다음", onClick = vm::next)
    SetupStep.SUMMARY -> Primary("저장하고 달력 보기", onClick = vm::save)
}

private fun orderHint(d: SetupDraft): String? = when {
    d.canLeave(SetupStep.ORDER) -> null
    d.usesRotation -> "순서에 근무가 하루 이상 있어야 해요"
    else -> "일하는 요일을 하루 이상 골라 주세요"
}

@Composable
private fun BottomButton(p: Primary, busy: Boolean) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            p.note?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().padding(bottom = 8.dp),
                )
            }
            if (p.hint != null && !p.enabled) {
                Text(
                    p.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                onClick = p.onClick,
                enabled = p.enabled && !busy,
                modifier = Modifier.widthIn(max = 560.dp).fillMaxWidth().heightIn(min = 56.dp),
            ) {
                if (busy) {
                    CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.5.dp, color = LocalContentColor.current)
                } else {
                    Text(p.label, style = MaterialTheme.typography.titleMedium)
                }
            }
            p.secondary?.let { (label, go) ->
                TextButton(onClick = go, enabled = p.enabled && !busy, modifier = Modifier.heightIn(min = 48.dp)) { Text(label) }
            }
        }
    }
}

@Composable
private fun StepsLeft(left: Int, modifier: Modifier = Modifier) {
    Text(
        if (left <= 0) "마지막 확인이에요" else "${left}단계 남았어요",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = modifier,
    )
}

/** Said by TalkBack when a step opens. */
private val SetupStep.title: String
    get() = when (this) {
        SetupStep.STYLE -> "근무 형태"
        SetupStep.COMPANY -> "회사"
        SetupStep.BUNDLE -> "근무 종류"
        SetupStep.TIMES -> "근무 시간"
        SetupStep.ORDER -> "근무 순서"
        SetupStep.TODAY -> "오늘 근무"
        SetupStep.ALARM -> "알람"
        SetupStep.SUMMARY -> "확인"
    }

/** [VIS-02] A step slides over 260 ms. */
private const val STEP_MILLIS = 260
