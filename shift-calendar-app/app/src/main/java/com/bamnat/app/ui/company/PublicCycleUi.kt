package com.bamnat.app.ui.company

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.company.PublicCycleFiles
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.ShiftBadge
import com.bamnat.app.ui.guide.WriteGate
import com.bamnat.core.example.WriteKind
import com.bamnat.core.model.ShiftType
import com.bamnat.core.plaza.CycleOrders
import com.bamnat.core.plaza.OrderState
import com.bamnat.core.plaza.PublicCycle
import com.bamnat.core.plaza.PublicCycles
import com.bamnat.core.plaza.PublicPay
import com.bamnat.core.plaza.PublicSetup
import com.bamnat.core.time.Today
import kotlinx.coroutines.launch
import java.time.LocalTime

/** "07~19", "07:30~19:30". */
internal fun span(start: LocalTime, end: LocalTime): String {
    fun t(x: LocalTime) = if (x.minute == 0) "%02d".format(x.hour) else "%02d:%02d".format(x.hour, x.minute)
    return "${t(start)}~${t(end)}"
}

/** "주간 07~19 · 야간 19~07" with "(흔한 시간)" when the source gave only a length. */
internal fun shiftsLine(c: PublicCycle, codes: String = c.codes): String {
    val shifts = if (codes.isEmpty()) c.shifts else c.shiftsFor(codes)
    if (shifts.isEmpty()) return "근무 시간은 공개 자료에 없어요"
    return shifts.joinToString(" · ") { "${it.name} ${span(it.start, it.end)}" } + if (shifts.any { it.usual }) " (흔한 시간)" else ""
}

/**
 * [SPAT-02] Where a public row comes from, on every card and sheet (research README: '공개 자료 기반' + 신뢰도 + 출처 + 시점):
 * the badge, the kinds of source, the date it speaks of and the link. It never says 채택 or 대표 판: it is not a company setup.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicSourceLine(c: PublicCycle, modifier: Modifier = Modifier, link: Boolean = true) {
    val uri = LocalUriHandler.current
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically) {
        StatusChip(PublicCycles.BADGE, Tone.QUIET)
        StatusChip("신뢰도 ${c.trustLine}", if (c.weak) Tone.WAIT else Tone.QUIET)
        if (c.asOf.isNotEmpty()) Text("시점 ${c.asOf}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (link && c.source.isNotEmpty()) {
            TextButton(onClick = { runCatching { uri.openUri(c.source) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("출처 보기")
            }
        }
    }
}

/** [SPAT-02] One public row in a list: company · 사업장, the form and order, the shifts, and where it comes from. */
@Composable
fun PublicCycleCard(c: PublicCycle, onClick: () -> Unit, modifier: Modifier = Modifier) {
    SoftCard(modifier.clip(MaterialTheme.shapes.large).clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(c.company, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(listOf(c.site, c.industry).filter { it.isNotEmpty() }.joinToString(" · "), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.outline)
            }
            Text(orderLine(c), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(shiftsLine(c), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            PublicSourceLine(c, link = false)
        }
    }
}

/** "4조 2교대 · 주주휴휴야야휴휴", "4조 2교대 · 순서는 직접 골라요". */
internal fun orderLine(c: PublicCycle): String = c.formName + " · " + when {
    c.roster -> "매달 근무표"
    c.orderState == OrderState.EXACT -> CycleOrders.spoken(c.codes)
    c.orderState == OrderState.USUAL -> "${c.orderText.substringBefore(" (").take(14)} → 흔한 순서 ${CycleOrders.spoken(c.codes)}"
    else -> "공개 순서 없음 · 순서는 직접 골라요"
}

/**
 * [SPAT-02] The public rows for [query] (and the chips' form or 업종) under any search: first setup, 회사 찾기, 규칙 광장.
 * A row tapped opens the apply sheet here; with [onPick] (첫 설정) the sheet only picks the order and hands it on, and the
 * first setup asks today and saves it with the rest.
 * With no query, [browse] shows 근무형태·업종 chips to look through the rows without a name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PublicCycleSection(
    query: String,
    modifier: Modifier = Modifier,
    onPick: ((PublicCycle, String) -> Unit)? = null,
    onPay: () -> Unit = {},
    onShare: () -> Unit = {},
    limit: Int = 8,
    browse: Boolean = false,
    title: Boolean = true,
) {
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    var industry by rememberSaveable { mutableStateOf<String?>(null) }
    var more by rememberSaveable(query) { mutableStateOf(false) }
    var open by remember { mutableStateOf<PublicCycle?>(null) }
    val hits = remember(query, form, industry) { PublicCycles.search(query, industry, form) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (browse && query.trim().length < 2) {
            Text("근무형태로 찾기", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PublicCycles.forms.take(6).forEach { (key, name) ->
                    FilterChip(selected = form == key, onClick = { form = if (form == key) null else key; industry = null }, label = { Text(name) })
                }
            }
            Text("업종으로 찾기", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PublicCycles.industries.take(8).forEach { i ->
                    FilterChip(selected = industry == i, onClick = { industry = if (industry == i) null else i; form = null }, label = { Text(i) })
                }
            }
        }
        if (hits.isNotEmpty()) {
            if (title) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.Info, null, Modifier.size(18.dp).padding(top = 2.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(8.dp))
                    Text("${PublicCycles.BADGE} ${hits.size}곳 · 기사·채용 공고·노무 사이트에서 모은 근무주기예요. 회사가 확인한 정보는 아니어서, 고른 뒤 내 근무와 맞춰 봐요.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { heading() })
                }
            }
            val shown = if (more) hits else hits.take(limit)
            shown.forEach { c -> PublicCycleCard(c, onClick = { open = c }) }
            if (hits.size > shown.size) TextButton(onClick = { more = true }) { Text("공개 자료 ${hits.size - shown.size}곳 더 보기") }
        }
    }
    open?.let { c ->
        PublicApplySheet(c, onDismiss = { open = null }, onPay = { open = null; onPay() }, onShare = { open = null; onShare() },
            pick = onPick?.let { p -> { codes: String -> open = null; p(c, codes) } })
    }
}

/**
 * [SPAT-02] 사용자 2026-10-25 14:05: neither the public rows nor the coworkers' setups have [name]. The user is asked to be the
 * first, with the reward rule said first (only with pay, once it is on the plaza) and that the cycle alone helps too.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstProviderCard(name: String, reward: String?, onStart: (String) -> Unit, modifier: Modifier = Modifier, plaza: Boolean = true) {
    var form by rememberSaveable(name) { mutableStateOf<String?>(null) }
    SoftCard(modifier, highlighted = true) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("처음으로 회사 정보를 제공해 주세요!", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            if (plaza) RewardPill(publicReward(reward))
            Text(
                if (plaza) "‘$name’의 근무주기를 넣어 주시면 같은 회사 분들이 검색해서 바로 맞춰요. 근무주기만 넣어도 올라가요."
                else "‘$name’의 근무주기를 직접 넣어요. 근무형태를 고르면 흔한 순서부터 보여 드려요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("어떤 근무형태예요?", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                PublicCycles.PROVIDE_FORMS.forEach { key ->
                    FilterChip(selected = form == key, onClick = { form = key }, label = { Text(PublicCycles.formName(key)) })
                }
            }
            Button(onClick = { form?.let(onStart) }, enabled = form != null, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Text("‘$name’ 근무주기 넣기")
            }
            if (form == null) Text("근무형태를 먼저 골라 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [SPAT-05] The reward rule as the user set it (2026-10-25 14:05): only a setup with its pay rules, once it is on the plaza,
 * earns it; the server's own months when it says them. The rule itself is the company server's (묶음 AF).
 */
internal fun publicReward(reward: String?): String =
    "급여 정보까지 넣어 광장에 올리면 보상을 드려요" + (reward?.let { " ($it)" } ?: "") + ". 근무주기만 올리면 보상은 없어요"

/**
 * [SPAT-03] 근무주기 맞추기 outside the first setup: the order (the row's own, or one of the form's usual ones, or changed day by
 * day), a preview, "오늘이 주기의 몇 번째 날인가요?", then the calendar from today through [WriteGate]. Once in, the way on is
 * the pay ([onPay], 416) or, with pay already set, the plaza ([onShare], 417).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PublicApplySheet(
    cycle: PublicCycle,
    onDismiss: () -> Unit,
    onPay: () -> Unit,
    onShare: () -> Unit,
    /** 첫 설정: the order is handed on instead of written; the setup asks today itself. */
    pick: ((String) -> Unit)? = null,
    /** [SPAT-07] 1.27 AH: the calendar it goes into (an added one from its own setup). [CALI-03] Else the screen's calendar. */
    calendar: String = LocalCompanyCalendar.current,
) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    val candidates = remember(cycle) { CycleOrders.candidates(cycle) }
    var codes by rememberSaveable(cycle.id, cycle.company) { mutableStateOf(if (cycle.orderState == OrderState.NONE) "" else cycle.codes) }
    var editing by rememberSaveable(cycle.id) { mutableStateOf(false) }
    var today by rememberSaveable(cycle.id, codes) { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val weekly = cycle.weekly || (cycle.formKey in CycleOrders.WEEKLY_FORMS && codes.length == 7)
    val needsToday = !weekly && codes.isNotEmpty() && pick == null
    val ready = (codes.any { it in PublicCycle.WORK } || cycle.roster) && (!needsToday || today != null)

    ModalBottomSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(cycle.company, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            if (cycle.site.isNotEmpty()) Text(cycle.site, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (cycle.id.isNotEmpty()) PublicSourceLine(cycle) else StatusChip("처음 제공 · ${cycle.formName}", Tone.WAIT)
            if (done) {
                DonePanel(cycle, codes, onPay = onPay, onShare = onShare, onClose = onDismiss, calendar = calendar)
                return@Column
            }
            Text("${cycle.formName} · ${shiftsLine(cycle, codes)}", style = MaterialTheme.typography.bodyMedium)
            if (cycle.id.isNotEmpty() && cycle.orderText.isNotEmpty()) {
                Text("공개 자료의 순서: ${cycle.orderText}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            HorizontalDivider()
            if (cycle.roster) {
                Text("매달 근무표가 나오는 곳이에요. 근무 종류와 시간만 넣고, 날짜는 ‘한 달 근무 넣기’로 넣어요.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    when (cycle.orderState) {
                        OrderState.EXACT -> "이 순서가 맞나요?"
                        OrderState.USUAL -> "공개 자료에는 ‘${cycle.orderText.substringBefore(" (").take(20)}’까지만 있어서 흔한 순서를 골라 두었어요. 맞는 순서를 골라 주세요"
                        OrderState.NONE -> "공개 자료에는 순서가 없어요. 아는 순서를 골라 주세요"
                    },
                    style = MaterialTheme.typography.titleSmall,
                )
                candidates.forEach { cand ->
                    val picked = codes == cand.codes && !editing
                    CandidateRow(cycle, cand.name, cand.codes, picked) { codes = cand.codes; editing = false; today = null }
                }
                TextButton(onClick = { editing = true; if (codes.isEmpty()) codes = candidates.firstOrNull()?.codes ?: "" },
                    modifier = Modifier.heightIn(min = 48.dp)) {
                    Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (editing) "하루씩 고치는 중" else "목록에 없어요 · 하루씩 고치기")
                }
                if (editing) OrderEditor(cycle, codes, onChange = { codes = it; today = null })
                if (codes.isNotEmpty() && needsToday) {
                    HorizontalDivider()
                    TodayInCycle(cycle, codes, today, onPick = { today = it })
                }
                if (codes.isNotEmpty() && weekly) Text("월요일부터 이 요일대로 넣어요.", style = MaterialTheme.typography.bodySmall)
            }
            Text(if (pick != null) "시간은 다음 화면에서 고칠 수 있어요. 급여 금액은 넣지도 가져오지도 않아요."
                else "오늘부터 이 순서로 바뀌어요. 지난 날짜와 직접 바꾼 날은 그대로예요. 급여 금액은 넣지도 가져오지도 않아요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    if (pick != null) pick(codes) else WriteGate.request(c, WriteKind.PATTERN) {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { PublicCycleFiles.apply(c, cycle, codes, if (needsToday) today else null, calendar) }
                                .onSuccess {
                                    done = true
                                    // [PLAZA-14] 1.27 AH: once, "이 정보를 다른 사람이 사용해도 될까요?" (AH-D6).
                                    c.leadShare.offer(com.bamnat.app.company.LeadShare.Pick(cycle.company, PublicCycleFiles.text(cycle, codes), calendar))
                                }
                                .onFailure { error = "달력에 넣지 못했어요. 한 번 더 눌러 주세요" }
                            busy = false
                        }
                    }
                },
                enabled = ready && !busy,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text(if (pick != null) "이 순서로 설정 이어가기" else "달력에 넣기")
            }
            if (!ready) {
                Text(if (codes.isEmpty() && !cycle.roster) "순서를 먼저 골라 주세요" else "오늘이 몇 번째 날인지 골라 주세요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** The badges of [codes] with the row's own shifts, for every place the order is drawn. */
@Composable
internal fun CodeStrip(cycle: PublicCycle, codes: String, modifier: Modifier = Modifier, size: androidx.compose.ui.unit.Dp = 24.dp) {
    val types = remember(cycle, codes) { typesByCode(cycle, codes) }
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(3.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        codes.forEach { code -> val t = types[code]; ShiftBadge(t, size = size, text = t?.label ?: "휴", outlined = t == null || t.isOff) }
    }
}

private fun typesByCode(cycle: PublicCycle, codes: String): Map<Char, ShiftType> {
    val plan = PublicSetup.plan(cycle, codes)
    val byName = plan.types.mapIndexed { i, t -> t.name to t.copy(id = i + 1L) }.toMap()
    return codes.toSet().mapNotNull { code ->
        val name = when (code) { 'O' -> PublicSetup.OFF; 'B' -> PublicSetup.AFTER; else -> cycle.shiftsFor(codes).firstOrNull { it.code == code }?.name }
        name?.let(byName::get)?.let { code to it }
    }.toMap()
}

@Composable
private fun CandidateRow(cycle: PublicCycle, name: String, codes: String, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.55f) else scheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (selected) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.medium) else Modifier)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "$name: ${CycleOrders.spoken(codes)}, ${codes.length}일마다" },
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (selected) Icon(Icons.Filled.CheckCircle, null, tint = scheme.primary, modifier = Modifier.size(20.dp))
            }
            CodeStrip(cycle, codes)
            Text("${codes.length}일마다 반복 · ${CycleOrders.spoken(codes)}", style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
    }
}

/** [SPAT-03] The order a day at a time: a day tapped turns to the next kind; days are added or taken off the end. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OrderEditor(cycle: PublicCycle, codes: String, onChange: (String) -> Unit) {
    val choices = remember(cycle, codes) { CycleOrders.choices(cycle, codes) }
    val types = remember(cycle, codes + choices.joinToString("")) { typesByCode(cycle, codes + choices.joinToString("")) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("날을 누르면 다음 근무로 바뀌어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            codes.forEachIndexed { i, code ->
                val t = types[code]
                val next = choices[(choices.indexOf(code) + 1).mod(choices.size)]
                Column(
                    Modifier.sizeIn(minWidth = 44.dp, minHeight = 48.dp).clip(MaterialTheme.shapes.small)
                        .clickable(role = Role.Button) { onChange(CycleOrders.set(codes, i, next)) }
                        .semantics { contentDescription = "${i + 1}번째 날 ${t?.name ?: "휴무"}, 누르면 ${types[next]?.name ?: "휴무"}" }
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ShiftBadge(t, size = 26.dp, text = t?.label ?: "휴", outlined = t == null || t.isOff)
                    Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onChange(CycleOrders.set(codes, codes.length, 'O')) }, enabled = codes.length < CycleOrders.MAX) { Text("하루 더") }
            OutlinedButton(onClick = { onChange(codes.dropLast(1)) }, enabled = codes.length > 1) { Text("하루 빼기") }
        }
    }
}

/**
 * [SPAT-03] "오늘이 주기의 몇 번째 날인가요?": each day of the order with its number; the one picked is today, and the next
 * week from it is said under, so a wrong pick shows before it is saved.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayInCycle(cycle: PublicCycle, codes: String, picked: Int?, onPick: (Int) -> Unit) {
    val types = remember(cycle, codes) { typesByCode(cycle, codes) }
    val scheme = MaterialTheme.colorScheme
    Text("오늘이 주기의 몇 번째 날인가요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Text("회사 근무표에서 오늘 칸을 찾아 같은 날을 눌러 주세요. 같은 회사라도 조마다 오늘이 달라요.",
        style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
    TodayFinder(codes, types, onPick)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        codes.forEachIndexed { i, code ->
            val t = types[code]
            val selected = picked == i
            Column(
                Modifier.sizeIn(minWidth = 44.dp, minHeight = 52.dp).clip(MaterialTheme.shapes.small)
                    .then(if (selected) Modifier.border(2.dp, scheme.primary, MaterialTheme.shapes.small) else Modifier)
                    .selectable(selected = selected, role = Role.RadioButton) { onPick(i) }
                    .semantics { contentDescription = "${i + 1}번째 날 ${t?.name ?: "휴무"}" }
                    .padding(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ShiftBadge(t, size = 26.dp, text = t?.label ?: "휴", outlined = t == null || t.isOff)
                Text("${i + 1}", style = MaterialTheme.typography.labelSmall, color = if (selected) scheme.primary else scheme.onSurfaceVariant,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
    picked?.let { p ->
        val week = (0 until 7).map { d -> codes[(p + d) % codes.length] }
        val names = listOf("오늘", "내일", "모레")
        Text(
            week.take(3).mapIndexed { d, code -> "${names[d]} ${types[code]?.name ?: "휴무"}" }.joinToString(" · ") + " …",
            style = MaterialTheme.typography.bodyMedium, color = scheme.primary,
        )
    }
}

/**
 * [PLAZA-18] 1.27 AH-I3: the place found by the shifts the user knows — today's, then tomorrow's and the day after's only
 * while more than one day of the order fits — instead of counting days on the company's sheet.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TodayFinder(codes: String, types: Map<Char, ShiftType>, onPick: (Int) -> Unit) {
    val kinds = remember(codes) { CycleOrders.kinds(codes) }
    if (kinds.size < 2) return
    var said by remember(codes) { mutableStateOf("") }
    val fits = remember(codes, said) {
        if (said.isEmpty()) codes.indices.toList() else CycleOrders.positions(codes, said[0], said.getOrNull(1), said.getOrNull(2))
    }
    val asks = listOf("오늘 근무가 뭐예요?", "내일은요?", "모레는요?")
    Text(if (said.isEmpty()) "모르겠으면 · ${asks[0]}" else asks.getOrElse(said.length) { "" }, style = MaterialTheme.typography.labelLarge)
    if (fits.size > 1 && said.length < 3) {
        val next = remember(codes, said) { fits.map { codes[(it + said.length) % codes.length] }.distinct() }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            next.forEach { code ->
                androidx.compose.material3.AssistChip(
                    onClick = {
                        said += code
                        val left = CycleOrders.positions(codes, said[0], said.getOrNull(1), said.getOrNull(2))
                        if (left.size == 1) onPick(left.single())
                    },
                    label = { Text(types[code]?.name ?: "휴무") },
                )
            }
        }
    } else if (said.isNotEmpty()) {
        Text(if (fits.size == 1) "${fits.single() + 1}번째 날이에요" else "아래에서 골라 주세요 (${fits.joinToString(", ") { "${it + 1}" }}번째 날 중)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
    if (said.isNotEmpty()) TextButton(onClick = { said = "" }) { Text("다시 고르기") }
}

/** After the cycle is in: what comes next, the pay with its usual allowances, or the plaza when the pay is set. */
@Composable
private fun DonePanel(cycle: PublicCycle, codes: String, onPay: () -> Unit, onShare: () -> Unit, onClose: () -> Unit, calendar: String) {
    val c = LocalContext.current.appContainer
    // [CALI-06] The pay of the calendar the cycle went into.
    val hasPay = remember(calendar) { c.companyOf(calendar).pay }.profile.collectAsState().value != null
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(8.dp))
        Text("달력에 넣었어요" + if (codes.isNotEmpty()) " · ${codes.length}일 순서" else "", style = MaterialTheme.typography.titleMedium)
    }
    // [PAYF-03] 1.29 AP: the same company may have a pay way coworkers set up: offered here, once, before the usual allowances.
    if (CompanyPayOffer(cycle.company, onPay, calendar)) return
    if (!hasPay) {
        val items = remember(cycle) { PublicPay.usualItems(cycle) }
        Text("다음은 급여예요. ${PublicPay.form(cycle, codes).label} 근무로 골라 두고" +
            (if (items.isNotEmpty()) " ${cycle.industry.ifEmpty { "이 일" }}에서 흔한 ${items.joinToString("·")}도 미리 넣어 둘게요." else " 기본급만 넣으면 돼요.") +
            " 금액은 내 명세서대로 고쳐요.", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onPay, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("급여도 넣기") }
        // [SPAT-05] The cycle alone goes up too, without the reward (사용자 2026-10-25 14:05).
        if (c.companyApi.enabled) TextButton(onClick = onShare, modifier = Modifier.fillMaxWidth()) { Text("급여 없이 근무주기만 올리기") }
    } else if (c.companyApi.enabled) {
        RewardPill(publicReward(null))
        Text("급여 설정이 이미 있어요. 같은 회사 분들이 쓰게 광장에 올려 주실래요?", style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onShare, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("광장에 올리기") }
    }
    TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("나중에") }
}

/** Today, for the tests that fix the clock. */
internal fun todayMillis(): Long = Today.millis()

/**
 * [PAYF-03] 1.29 묶음 AP (사용자 2026-10-27): a cycle just put in for [company], whose 광장 has a pay way: "급여 계산 방식도 같이
 * 넣을까요?" (결정 D79: only that there is one and how many said 맞아요). Yes uses it ([PayFind]) and goes on to the 기본급
 * ([onPay]). True while it shows, so the panel leaves out its own next step.
 */
@Composable
internal fun CompanyPayOffer(company: String, onPay: () -> Unit, calendar: String = LocalCompanyCalendar.current): Boolean {
    val c = LocalContext.current.appContainer
    if (!c.companyApi.enabled) return false
    val hit by androidx.compose.runtime.produceState<com.bamnat.app.company.CompanyHit?>(null, company) {
        value = PayFindSearch.server(c, company).orEmpty().firstOrNull { h ->
            (h.lead?.pay == true && h.lead.doubted.not() || h.lead?.payWay != null) &&
                (com.bamnat.core.plaza.PublicCycles.norm(h.name) == com.bamnat.core.plaza.PublicCycles.norm(company) ||
                    h.alias?.let { com.bamnat.core.plaza.PublicCycles.norm(it) == com.bamnat.core.plaza.PublicCycles.norm(company) } == true)
        }
    }
    var declined by rememberSaveable(company) { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val h = hit ?: return false
    if (declined) return false
    val scope = rememberCoroutineScope()
    Text("급여 계산 방식도 같이 넣을까요?", style = MaterialTheme.typography.titleSmall)
    StatusChip("급여 방식 있음 · 맞아요 ${h.lead?.payWay?.takeIf { h.lead.pay.not() }?.ok ?: h.lead?.ok ?: 0}명", Tone.GOOD)
    Text("‘${h.name}’ 분들이 맞춘 계산 방식이에요. 넣으면 수당과 계산 방식이 들어오고, 기본급만 내 것으로 넣으면 돼요.",
        style = MaterialTheme.typography.bodyMedium)
    error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
    Button(onClick = {
        busy = true
        scope.launch {
            try {
                // [CALI-04] Into the pay of the calendar the cycle went into.
                val s = c.companyOf(calendar)
                when (s.find.use(h)) {
                    com.bamnat.app.company.PayFind.Used.Gone -> declined = true
                    com.bamnat.app.company.PayFind.Used.Applied -> { s.payWays.askBase(true); onPay() }
                    com.bamnat.app.company.PayFind.Used.Amounts -> onPay()
                }
            } catch (e: com.bamnat.app.company.CompanyException) {
                error = companyProblem(e.code, e.field)
            } catch (e: com.bamnat.app.data.BackupException) {
                error = e.message ?: "급여 방식을 넣지 못했어요"
            }
            busy = false
        }
    }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Text("네, 급여 방식도 넣을게요")
    }
    TextButton(onClick = { declined = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) { Text("아니요, 근무주기만") }
    return true
}
