package com.bamnat.app.ui.company

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.VolunteerActivism
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.company.PublicUse
import com.bamnat.app.company.ReportStatus
import com.bamnat.app.company.SentReport
import com.bamnat.app.data.CompanySummary
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.ConfirmDialog
import java.time.ZoneId
import com.bamnat.core.time.Today

/** [COMM-02] What the report screen and the privacy policy say about what goes to the company server. */
internal const val COMPANY_SEND_NOTICE =
    "보내면 위 내용이 회사 찾기에 올라가요. 함께 가는 건 이 휴대폰에서 만든 무작위 번호뿐이라 누가 보냈는지 알 수 없어요. " +
        "‘내가 보낸 정보’에서 언제든 거두거나 모두 지울 수 있어요."

/**
 * [COMM-02] 내 설정 나누기: the user's setup without amounts goes to coworkers. What goes and what
 * does not is read from the very file that is sent, and how it becomes adopted is said before it goes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanyReportScreen(
    vm: CompanyReportViewModel,
    onBack: () -> Unit,
    onDone: () -> Unit,
    onOpenSent: () -> Unit,
    /** [SPAT-05] The company's plaza page, opened after a setup from a public row went up. */
    onOpenCompany: (String) -> Unit = {},
    /** [PLAZA-30] 1.27 AI: the main pay screen with 명세서로 맞춰 보기 open; null leaves the shortcut out. */
    onMatch: (() -> Unit)? = null,
    /** [FIN130-01] The calendar's pay screen, where a pay way taken with 이 규칙 쓰기 asks its 기본급. */
    onOpenPay: (() -> Unit)? = null,
) {
    val sending by vm.sending.collectAsStateWithLifecycle()
    val terms by vm.terms.collectAsStateWithLifecycle()
    val preview by vm.preview.collectAsStateWithLifecycle()
    val fix = vm.start?.fix == true
    var company by rememberSaveable { mutableStateOf(vm.start?.company.orEmpty()) }
    var dept by rememberSaveable { mutableStateOf(vm.start?.dept.orEmpty()) }
    // [PLAZA-30] 1.27 AI: the tick only with a month this calendar matched; it follows a match done from here.
    val payProfile by vm.payProfile.collectAsStateWithLifecycle()
    val proof = remember(payProfile) { vm.proof(payProfile) }
    val canTick = proof?.counts == true
    var ticked by rememberSaveable { mutableStateOf(true) }
    val verified = canTick && ticked
    var note by rememberSaveable { mutableStateOf(vm.startNote) }
    val cycleOnly by vm.cycleOnly.collectAsStateWithLifecycle()
    val public = vm.publicUse
    // [J133-84] 1.33 광장 급여 써 보기 ("급여 설정에서 고른 직종을 또 물어요"): the job the pay setup already has, picked to start.
    var job by rememberSaveable { mutableStateOf(payProfile?.occupation?.let(com.bamnat.core.pay.RateHint::jobOf).orEmpty()) }
    var region by rememberSaveable { mutableStateOf("") }
    var topic by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (fix) "맞는 설정 알려 주기" else "내 설정 나누기") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding(), contentAlignment = Alignment.TopCenter) {
            AnimatedContent(
                targetState = sending.let { it is CompanyReportViewModel.Sending.Sent || it is CompanyReportViewModel.Sending.Used ||
                    it is CompanyReportViewModel.Sending.Proposed },
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "report-sent",
            ) { sent ->
                val s = sending
                if (sent && s is CompanyReportViewModel.Sending.Sent) {
                    SentPanel(s.status, onDone = onDone, onOpenSent = onOpenSent,
                        // [PLAZA-30] 1.27 AI (442): every company report opens its plaza page, not only one from a public row.
                        onOpenCompany = if (!topic && s.status.companyId.isNotEmpty()) ({ onOpenCompany(s.status.companyId) }) else null)
                } else if (sent) {
                    // [PLZD-01][PLZD-03] 1.30 BD: the plaza's same rule is in use, or the difference went as a proposal.
                    GuardDonePanel(s, onDone = onDone, onOpenCompany = onOpenCompany, onOpenPay = onOpenPay)
                } else {
                    Column(
                        Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        HeroIcon(Icons.Outlined.VolunteerActivism)
                        Text(if (fix) "정답을 알려 주셔서 고마워요" else "같은 회사 분들의 길잡이가 되어 주세요",
                            style = MaterialTheme.typography.headlineSmall)
                        // [SPAT-05] The reward first, as the user asked (2026-10-25 14:05): only with the pay, once it is up.
                        if (public != null) PublicReportHead(public, cycleOnly, vm.hasPay, rewardShort(terms), onCycleOnly = vm::setCycleOnly)
                        Text(
                            if (fix) "고친 설정을 보내 주시면, 같은 회사 분들이 확인한 뒤 회사 찾기의 설정이 바로잡혀요."
                            else "내 근무표와 급여 계산 방식을 내 급여 금액 없이 보내요. 같은 회사 분들이 확인하면 회사 찾기에 올라가요.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // [PLZ-12][PLZ-13] Which plaza it goes to, and where; a hospital is a place like a company.
                        PlazaPicker(job, region, topic, onJob = { job = it }, onRegion = { region = it }, onTopic = { topic = it })
                        if (!topic) OutlinedTextField(
                            value = company, onValueChange = { company = it.take(40) },
                            label = { Text(if (job == "nurse") "병원 이름" else "회사 이름") }, singleLine = true,
                            supportingText = { Text("띄어쓰기나 (주)는 달라도 같은 곳으로 찾아요") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = dept, onValueChange = { dept = it.take(20) },
                            label = { Text("부서·조 (선택)") }, singleLine = true,
                            placeholder = { Text("예: 생산 2팀") },
                            supportingText = { Text("비워 두면 회사 전체 설정이 돼요") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        WhatGoes(preview)
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                                .clickable(enabled = canTick, role = Role.Checkbox) { ticked = !ticked }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = verified, onCheckedChange = null, enabled = canTick, modifier = Modifier.padding(horizontal = 8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("급여 명세서와 맞춰 봤어요", style = MaterialTheme.typography.bodyLarge,
                                    color = if (canTick) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(com.bamnat.core.pay.SlipProof.words(proof),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (!canTick && onMatch != null) androidx.compose.material3.TextButton(onClick = { vm.askMatch(); onMatch() },
                            modifier = Modifier.padding(start = 40.dp)) { Text("명세서로 맞춰 보기") }
                        OutlinedTextField(
                            value = note, onValueChange = { note = it.take(500) },
                            label = { Text("동료에게 한마디 (선택)") },
                            placeholder = { Text("예: 야간 가산은 22시부터예요") },
                            supportingText = { Text("금액·전화번호·메일 주소는 적지 말아 주세요") },
                            minLines = 2, modifier = Modifier.fillMaxWidth(),
                        )
                        HowAdopted(terms?.threshold ?: 3, rewardLine(terms))
                        Text(COMPANY_SEND_NOTICE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        (s as? CompanyReportViewModel.Sending.Failed)?.let { f ->
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                                Text(companyProblem(f.code, f.field), color = MaterialTheme.colorScheme.onErrorContainer,
                                    style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
                            }
                        }
                        Button(
                            onClick = { vm.send(company, dept, verified, note, job, region, topic) },
                            enabled = s != CompanyReportViewModel.Sending.Busy && (if (topic) job.isNotEmpty() else company.trim().length >= 2),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                        ) {
                            if (s == CompanyReportViewModel.Sending.Busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else Text(if (fix) "맞는 설정 보내기" else "보내기")
                        }
                        Spacer(Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    // [PLZD-01][PLZD-02][PLZD-03][PLZD-05] 1.30 BD: what the plaza already has, asked before anything goes.
    var proposing by remember { mutableStateOf<com.bamnat.app.company.SendGuard.Differ?>(null) }
    (sending as? CompanyReportViewModel.Sending.Guarded)?.takeIf { proposing == null }?.let { held ->
        SendGuardAsk(held,
            onUse = { vm.useSame(it, verified) },
            onSend = { d -> dept = d; vm.sendAnyway(held.form, d) },
            onPropose = { proposing = it },
            onOpenCompany = { id -> vm.resetSending(); onOpenCompany(id) },
            onDismiss = vm::resetSending)
    }
    proposing?.let { v ->
        GuardProposalSheet(v, vm.calendar, onSent = { proposing = null; vm.proposed(v) }, onDismiss = { proposing = null })
    }
}

/** What goes, from the file that is sent, and what never does. */
@Composable
private fun WhatGoes(preview: CompanySummary?) {
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("보내는 것", style = MaterialTheme.typography.titleSmall)
            if (preview == null) {
                Text("읽는 중…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                if (preview.shiftTypes.isNotEmpty()) Goes("근무 ${preview.shiftTypes.joinToString(" · ")}" + (preview.cycleDays?.let { " (${it}일 순서)" } ?: ""))
                if (preview.extraKinds.isNotEmpty()) Goes("연장·휴무일 근무 ${preview.extraKinds.joinToString(" · ")}")
                if (preview.hasRules) Goes("연장·야간·휴일 가산과 회사 규칙")
                if (preview.items.isNotEmpty()) Goes("수당 이름 ${preview.items.joinToString(" · ")} (금액 빼고)")
                if (!preview.hasRules) Goes("급여 계산 방식은 아직 없어요. 근무표만 가요")
            }
            Spacer(Modifier.height(6.dp))
            Text("보내지 않는 것", style = MaterialTheme.typography.titleSmall)
            NotGoes("기본급과 수당 금액")
            NotGoes("입사일·연차, 이름·연락처")
            NotGoes("날짜별 근무와 메모")
        }
    }
}

@Composable
private fun Goes(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun NotGoes(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(Icons.Filled.Block, null, tint = MaterialTheme.colorScheme.outline, modifier = Modifier.size(16.dp).padding(top = 2.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** How a setup becomes adopted, with the reward, said before anyone sends [COMM-03]. */
@Composable
internal fun HowAdopted(threshold: Int, reward: String?) {
    SoftCard {
        Column(Modifier.padding(16.dp)) {
            Text("이렇게 채택돼요", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(bottom = 6.dp))
            HowStep(1, "회사 찾기에 ‘확인 중’으로 올라가요", "같은 회사 분들은 바로 써 볼 수 있어요")
            HowStep(2, "같은 회사 분들이 확인해요", "같은 설정을 보내거나 ‘맞았어요’를 누를 때마다 점수가 쌓여요")
            HowStep(3, "${threshold}점이 되면 채택돼요", "두 사람 이상이 맞다고 해야 하고, 개발자가 한 번 더 볼 때도 있어요")
            reward?.let { RewardPill(it, Modifier.padding(top = 8.dp)) }
        }
    }
}

@Composable
private fun SentPanel(status: ReportStatus, onDone: () -> Unit, onOpenSent: () -> Unit, onOpenCompany: (() -> Unit)? = null) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    val (label, tone) = status.label()
    Column(
        Modifier.widthIn(max = 560.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        androidx.compose.animation.AnimatedVisibility(shown, enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn()) {
            Text("🎉", style = MaterialTheme.typography.displayMedium)
        }
        // [PLZD-01][PLZD-04] 1.30 BD: the rule in use sent again counts as 맞아요; another shape waits for the developer.
        val held = status.reasonCode == "shape" && status.status == "pending"
        val same = status.same && !status.again
        Text(when {
            status.again -> "이미 보낸 설정이에요"
            held -> "보냈어요. 개발자가 먼저 볼게요"
            same -> "이미 쓰는 규칙이라 맞아요로 셌어요"
            else -> "보냈어요! 고마워요"
        }, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            if (status.again) "같은 휴대폰에서 같은 설정은 한 번만 세요. 지금 상태를 보여 드릴게요."
            else if (held) "근무 모양이 이 부서 대표 판과 달라서 바로 보이지 않아요. 다른 부서라면 부서 이름을 넣어 다시 보내 주세요."
            else if (same) "${status.company}${status.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""}에서 이미 쓰는 규칙과 같아요. 새 판은 만들지 않았어요."
            else "${status.company}${status.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""} 분들이 다음부터 훨씬 쉽게 설정해요.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
        )
        SoftCard(highlighted = true) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusChip(label, tone)
                Text(status.detail(), style = MaterialTheme.typography.bodyMedium)
                if (status.status == "pending" && !status.review) PointsBar(status.points, status.threshold)
                if (status.rewardMonths > 0 && status.status == "pending") {
                    RewardPill(rewardText(status.rewardMonths))
                }
            }
        }
        Text("같은 회사 동료에게 밤낮을 알려 주면 더 빨리 채택돼요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (onOpenCompany != null) {
            Button(onClick = onOpenCompany, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("‘${status.company}’ 광장 보기") }
            OutlinedButton(onClick = onOpenSent, modifier = Modifier.fillMaxWidth()) { Text("내가 보낸 정보 보기") }
            TextButton(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        } else {
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("확인") }
            OutlinedButton(onClick = onOpenSent, modifier = Modifier.fillMaxWidth()) { Text("내가 보낸 정보 보기") }
        }
    }
}

/**
 * [SPAT-05] Above the form when the setup came from a public row: the reward rule first, what kind of report it is
 * ('공개 자료 확인·고침' or '처음 제공'), and 근무주기만 올리기 (no pay part, no reward) for a user who wants only that.
 */
@Composable
private fun PublicReportHead(use: PublicUse, cycleOnly: Boolean, hasPay: Boolean, reward: String?, onCycleOnly: (Boolean) -> Unit) {
    SoftCard(highlighted = true) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            RewardPill(publicReward(reward))
            Text(if (use.cycleId.isEmpty()) "처음 제공 · ${use.company}" else "공개 자료 확인·고침 · ${use.company}",
                style = MaterialTheme.typography.titleSmall)
            Text(if (use.picked) "내가 고른 순서로 올라가요. 같은 회사 분들이 찾아서 바로 맞춰요."
                else "공개 자료의 순서가 맞다고 올라가요. 같은 회사 분들이 찾아서 바로 맞춰요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .clickable(enabled = hasPay, role = Role.Checkbox) { onCycleOnly(!cycleOnly) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = cycleOnly, onCheckedChange = null, enabled = hasPay, modifier = Modifier.padding(horizontal = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text("근무주기만 올리기", style = MaterialTheme.typography.bodyLarge)
                    Text(if (hasPay) "급여 계산 방식은 빼고 올려요. 이때는 보상이 없어요" else "급여 설정이 아직 없어서 근무주기만 올라가요. 보상은 없어요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/**
 * [COMM-02] 내가 보낸 정보: where each report stands, the answers to ways the app cannot set yet,
 * taking one back, and deleting everything this phone sent.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySentScreen(vm: CompanyReportViewModel, onBack: () -> Unit, onShare: () -> Unit) {
    val sent by vm.sent.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val offline by vm.offline.collectAsStateWithLifecycle()
    var takingBack by remember { mutableStateOf<SentReport?>(null) }
    var forgetting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { vm.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("내가 보낸 정보") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (offline) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                        Icon(Icons.Outlined.CloudOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("인터넷에 연결되지 않아 마지막으로 본 상태예요", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        TextButton(onClick = vm::refresh) { Text("다시") }
                    }
                }
                message?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary) }
                if (sent.isEmpty()) {
                    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        HeroIcon(Icons.Outlined.Inbox)
                        Text("아직 보낸 정보가 없어요", style = MaterialTheme.typography.titleMedium)
                        Text("내 설정을 나누면 같은 회사 분들이 1분 만에 설정해요.", style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                        Button(onClick = onShare) { Text("내 설정 나누기") }
                    }
                }
                sent.forEach { r ->
                    SentCard(r, statuses[r.id], statuses.containsKey(r.id), onTakeBack = { takingBack = r },
                        // Already gone from the server: only the line here is left to drop.
                        onDrop = { vm.takeBack(r.id) {} })
                }
                if (sent.isNotEmpty()) {
                    TextButton(onClick = { forgetting = true }, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp)) {
                        Text("이 휴대폰이 보낸 정보 모두 지우기", color = MaterialTheme.colorScheme.error)
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    takingBack?.let { r ->
        ConfirmDialog(
            title = "이 정보를 거둘까요?",
            message = if (r.ask) "개발자에게 보낸 내용이 지워져요." else "회사 찾기에서 빠지고, 받기로 한 보상도 없어져요. 같은 회사 분들이 쓰던 설정은 그대로예요.",
            confirmLabel = "거두기",
            destructive = true,
            onDismiss = { takingBack = null },
            onConfirm = {
                takingBack = null
                vm.takeBack(r.id) { ok -> message = if (ok) "거뒀어요" else "인터넷에 연결한 뒤 다시 해 주세요" }
            },
        )
    }
    if (forgetting) {
        ConfirmDialog(
            title = "보낸 정보를 모두 지울까요?",
            message = "보낸 설정, 맞았어요·달라요, 토론 글과 신고, 받기로 한 보상, 앱 문의까지 서버에서 모두 지워요. 이 휴대폰에서 쓰는 설정은 그대로예요.",
            confirmLabel = "모두 지우기",
            destructive = true,
            onDismiss = { forgetting = false },
            onConfirm = {
                forgetting = false
                vm.forgetAllSaying { ok, said -> message = if (ok) said ?: "모두 지웠어요" else "인터넷에 연결한 뒤 다시 해 주세요" }
            },
        )
    }
}

@Composable
private fun SentCard(r: SentReport, status: ReportStatus?, looked: Boolean, onTakeBack: () -> Unit, onDrop: () -> Unit) {
    SoftCard(highlighted = status?.status == "adopted" || status?.status == "answered") {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.company + (r.dept.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.titleMedium)
                    Text(
                        (if (r.ask) "앱에 없는 방식 · " else "") + Fmt.shortFullDate(r.at.atZone(Today.zone()).toLocalDate()),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                when {
                    status != null -> status.label().let { (text, tone) -> StatusChip(text, tone) }
                    looked -> StatusChip("서버에 없어요", Tone.QUIET)
                }
            }
            when {
                status != null -> {
                    Text(status.detail(), style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (status.status == "answered") FontWeight.Medium else FontWeight.Normal)
                    if (status.status == "pending" && !status.review) PointsBar(status.points, status.threshold)
                }
                looked -> Text("거뒀거나 모두 지운 정보예요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (status != null && status.status !in setOf("withdrawn", "replaced")) {
                TextButton(onClick = onTakeBack, modifier = Modifier.align(Alignment.End)) { Text("거두기") }
            } else if (status == null && looked) {
                TextButton(onClick = onDrop, modifier = Modifier.align(Alignment.End)) { Text("목록에서 빼기") }
            }
        }
    }
}

/**
 * [PLZ-12][PLZ-13] 어떤 일인가요: the job plaza, the 시·도 and whether the rule is for a whole job (공무원
 * 수당, 일용직 노임처럼 한 회사가 아닌 것). Optional: left empty it goes to 모든 광장 as before.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun PlazaPicker(job: String, region: String, topic: Boolean, onJob: (String) -> Unit, onRegion: (String) -> Unit, onTopic: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("어떤 일인가요? (선택)", style = MaterialTheme.typography.titleSmall)
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            com.bamnat.app.company.Plaza.JOBS.forEach { (key, name) ->
                com.bamnat.app.ui.components.FilterChip(selected = job == key, onClick = { onJob(if (job == key) "" else key) }, label = { Text(name) })
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            var open by remember { mutableStateOf(false) }
            Box {
                com.bamnat.app.ui.components.FilterChip(selected = region.isNotEmpty(), onClick = { open = true },
                    label = { Text(if (region.isEmpty()) "지역 고르기 (전국)" else com.bamnat.app.company.Plaza.region(region)) })
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    androidx.compose.material3.DropdownMenuItem(text = { Text("전국") }, onClick = { open = false; onRegion("") })
                    com.bamnat.app.company.Plaza.REGIONS.forEach { (k, n) ->
                        androidx.compose.material3.DropdownMenuItem(text = { Text(n) }, onClick = { open = false; onRegion(k) })
                    }
                }
            }
        }
        if (job.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(role = Role.Checkbox) { onTopic(!topic) }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = topic, onCheckedChange = null, modifier = Modifier.padding(horizontal = 8.dp))
                Column(Modifier.weight(1f)) {
                    Text("한 회사가 아니라 이 일 전체의 규칙이에요", style = MaterialTheme.typography.bodyLarge)
                    Text("예: 공무원 수당, 일용직 근무 방식. ‘${com.bamnat.app.company.Plaza.job(job)} 공통’으로 올라가요",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
