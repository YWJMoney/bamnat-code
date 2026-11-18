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
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.LaunchedEffect
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

/**
 * "주간 07~19 · 야간 19~07" with "(흔한 시간)" when the source gave only a length. When it gave some shifts' hours and not
 * the others' (상대지구대: 야간 17~08 only), the source's come first and only the rest say they are filled in
 * (012815b6 PC 1차: "주간 07~19·야간 17~08 (흔한 시간)" read as if the 야간 were made up too).
 */
internal fun shiftsLine(c: PublicCycle, codes: String = c.codes): String {
    val shifts = if (codes.isEmpty()) c.shifts else c.shiftsFor(codes)
    if (shifts.isEmpty()) return "근무 시간은 공개 자료에 없어요"
    fun line(list: List<com.bamnat.core.plaza.CycleShift>) = list.joinToString(" · ") { "${it.name} ${span(it.start, it.end)}" }
    val (filled, given) = shifts.partition { it.usual }
    return when {
        filled.isEmpty() -> line(given)
        given.isEmpty() -> line(filled) + " (흔한 시간)"
        else -> line(given) + " · " + line(filled) + " (" + filled.joinToString("·") { it.name } + ": 출처에 없어 채운 시간)"
    }
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
        // [CYC-10] 1.38: an old source says so, so no one takes a 2004 article for today's way.
        PublicCycles.yearsOld(c, com.bamnat.core.time.Today.date())?.takeIf { it >= OLD_YEARS }?.let { y ->
            StatusChip("${y}년 전 자료 · 지금과 다를 수 있어요", Tone.WAIT)
        }
        if (link && c.source.isNotEmpty()) {
            TextButton(onClick = { runCatching { uri.openUri(c.source) } }, modifier = Modifier.heightIn(min = 48.dp)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, null, Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("출처 보기")
            }
        }
    }
}

/**
 * 012815b6 PC 1차 ("창을 아래로 쓸면 친 글자와 고른 칩이 사라져요"): what was being looked for in the public rows, kept while
 * the app runs, so a search closed by mistake opens where it was. The name lives in [CompanyFindViewModel]'s query.
 */
internal object PublicBrowse {
    var form: String? = null
    var group: String? = null
    var query: String = ""
    var at: Long = 0L

    /** The name typed last, if the search was left a short while ago. */
    fun recentQuery(now: Long = com.bamnat.core.time.Today.millis()): String = if (now - at <= KEEP_MS) query else ""
    fun keep(q: String, now: Long = com.bamnat.core.time.Today.millis()) { query = q; at = now }
    fun reset() { form = null; group = null; query = ""; at = 0L }
    private const val KEEP_MS = 10 * 60 * 1000L
}

/** [CYC-10] A source this many years old or more is marked as old. */
private const val OLD_YEARS = 5

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
    /** [CYC-09] '우리 회사 근무표 올리기' at the end of the list. */
    upload: Boolean = true,
) {
    // [CYC-09] 1.38: an 업종 묶음 (소방, 병원·요양…) in place of the 70 raw 업종 words. 012815b6 PC 1차: the chip picked stays
    // when the search is closed and opened again ([PublicBrowse]), as the name typed does.
    var form by rememberSaveable { mutableStateOf(PublicBrowse.form) }
    var group by rememberSaveable { mutableStateOf(PublicBrowse.group) }
    LaunchedEffect(form, group) { PublicBrowse.form = form; PublicBrowse.group = group }
    // 012815b6 PC 1차: with a chip picked the 25 chips fold to that one, so its rows come right under it.
    var allChips by rememberSaveable { mutableStateOf(false) }
    val folded = !allChips && (form != null || group != null)
    var more by rememberSaveable(query) { mutableStateOf(false) }
    var open by remember { mutableStateOf<PublicCycle?>(null) }
    var uploading by rememberSaveable { mutableStateOf(false) }
    // 1.38 통합 8차: the sheet opened by '우리 회사 근무표 올리기' puts the table up first; the user's calendar only when asked.
    var uploaded by remember { mutableStateOf(false) }
    val hits = remember(query, form, group) { PublicCycles.search(query, form = form, group = group) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (browse && query.trim().length < 2) {
            Text("근무형태로 찾기", style = MaterialTheme.typography.titleSmall)
            // [CYC-10] 1.38 순서로 찾기.
            Text("회사 이름 대신 ‘주야비휴’, ‘당비비’처럼 근무 순서를 써도 찾아요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            val groups = remember { com.bamnat.core.plaza.IndustryGroups.counts(PublicCycles.all) }
            val pickForm = { key: String -> form = if (form == key) null else key; group = null; allChips = false }
            val pickGroup = { key: String -> group = if (group == key) null else key; form = null; allChips = false }
            if (folded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp),
                    itemVerticalAlignment = Alignment.CenterVertically) {
                    PublicCycles.forms.firstOrNull { it.first == form }?.let { (key, name) ->
                        FilterChip(selected = true, onClick = { pickForm(key) }, label = { Text(name) })
                    }
                    groups.firstOrNull { it.first.key == group }?.let { (g, n) ->
                        FilterChip(selected = true, onClick = { pickGroup(g.key) }, label = { Text("${g.name} $n") })
                    }
                    TextButton(onClick = { allChips = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("다른 근무형태·업종 보기") }
                }
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PublicCycles.forms.take(6).forEach { (key, name) ->
                        FilterChip(selected = form == key, onClick = { pickForm(key) }, label = { Text(name) })
                    }
                }
                Text("업종으로 찾기", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    groups.forEach { (g, n) ->
                        FilterChip(selected = group == g.key, onClick = { pickGroup(g.key) }, label = { Text("${g.name} $n") })
                    }
                }
            }
            // [CYC-09] One line on how the picked 업종's work usually goes.
            com.bamnat.core.plaza.IndustryGroups.byKey(group)?.let { g ->
                Text(g.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        // [CYC-09][CYC-03] 1.38: always at the end of the list, the way to add one's own company when it is not here (or wrong).
        if (upload && (browse || hits.isNotEmpty())) {
            OutlinedButton(onClick = { uploading = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Edit, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("우리 회사 근무표 올리기")
            }
        }
    }
    if (uploading) {
        UploadCycleDialog(query.trim(), onDismiss = { uploading = false }) { name, formKey ->
            uploading = false
            uploaded = true
            open = PublicCycles.provided(name, formKey)
        }
    }
    open?.let { c ->
        PublicApplySheet(c, onDismiss = { open = null; uploaded = false }, onPay = { open = null; onPay() }, onShare = { open = null; onShare() },
            pick = onPick?.let { p -> { codes: String -> open = null; p(c, codes) } }, uploadOnly = uploaded)
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
    /**
     * 1.38 통합 8차 [CYC-03] (올리기로 들어왔는데 마지막 단추가 '달력에 넣기'이고 오늘 며칠째인지도 물어요): opened by '우리 회사 근무표
     * 올리기', the table is put up and the user's own calendar stays as it is; '내 달력에도 넣기' is the way to put it in too.
     */
    uploadOnly: Boolean = false,
) {
    val c = LocalContext.current.appContainer
    val scope = rememberCoroutineScope()
    var alsoMine by rememberSaveable(cycle.id, cycle.company) { mutableStateOf(false) }
    val upOnly = uploadOnly && pick == null && !alsoMine
    val candidates = remember(cycle) { CycleOrders.candidates(cycle) }
    var codes by rememberSaveable(cycle.id, cycle.company) { mutableStateOf(if (cycle.orderState == OrderState.NONE) "" else cycle.codes) }
    var editing by rememberSaveable(cycle.id) { mutableStateOf(false) }
    var today by rememberSaveable(cycle.id, codes) { mutableStateOf<Int?>(null) }
    var busy by remember { mutableStateOf(false) }
    var done by rememberSaveable { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val weekly = cycle.weekly || (cycle.formKey in CycleOrders.WEEKLY_FORMS && codes.length == 7)
    val needsToday = !weekly && codes.isNotEmpty() && pick == null && !upOnly
    val ready = (codes.any { it in PublicCycle.WORK } || cycle.roster) && (!needsToday || today != null)

    // 2.0.0 [RVN-18] 검토 COMP-22-3: kept up while it is putting the cycle in (before, a drag hid it and it still held the screen).
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = { if (!busy) onDismiss() }, sheetState = busySheetState(busy)) {
        // [J133-61] 1.33 (중간 빌드 확인): the button stays at the bottom of the sheet; the order list scrolls above it, so '이 순서로
        // 설정 이어가기' is never below the fold.
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(
            Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()),
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
            Text(when {
                    pick != null -> "시간은 다음 화면에서 고칠 수 있어요. 급여 금액은 넣지도 가져오지도 않아요."
                    upOnly -> "근무 이름·시간·순서만 올려요. 내 달력은 그대로예요. 회사 찾기에서 같은 회사 분께 바로 보이고, 공개 근무주기 목록에는 검토 뒤(최대 7일)에 올라가요."
                    else -> "오늘부터 이 순서로 바뀌어요. 지난 날짜와 직접 바꾼 날은 그대로예요. 급여 금액은 넣지도 가져오지도 않아요."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (!done && upOnly) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    val sent = c.leadShare.upload(com.bamnat.app.company.LeadShare.Pick(cycle.company, PublicCycleFiles.text(cycle, codes), calendar, firstGiven = cycle.id.isEmpty()))
                    if (sent) onDismiss() else error = "지금은 올릴 수 없어요. 인터넷에 연결된 뒤 다시 눌러 주세요"
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("올리기만 할게요 (내 달력은 그대로)") }
            TextButton(onClick = { alsoMine = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("내 달력에도 넣기") }
            if (!ready) Text("순서를 먼저 골라 주세요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (!done) {
            error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
            Button(
                onClick = {
                    if (pick != null) pick(codes) else WriteGate.request(c, WriteKind.PATTERN) {
                        busy = true
                        error = null
                        scope.launch {
                            runCatching { PublicCycleFiles.apply(c, cycle, codes, if (needsToday) today else null, calendar) }
                                .onSuccess { applied ->
                                    done = true
                                    // 2.0.0 [RVN-08] COMP-11 Over the calendar's own order: 되돌리기 on the app's snackbar.
                                    applied.undo?.let { com.bamnat.app.ui.PutOnNotice.say("달력에 넣었어요 · 오늘부터 이 순서예요", it) }
                                    // [CYC-04] 1.38 업종 기념일.
                                    com.bamnat.app.ui.calendar.rememberOrgGroup(c.settingsRepository, cycle)
                                    // [PLAZA-14] 1.27 AH: once, "이 정보를 다른 사람이 사용해도 될까요?" (AH-D6).
                                    val pickNow = com.bamnat.app.company.LeadShare.Pick(cycle.company, PublicCycleFiles.text(cycle, codes), calendar, firstGiven = cycle.id.isEmpty())
                                    // 1.38 통합 8차: came to put it up, so asked even after a no to another share ask.
                                    if (uploadOnly) c.leadShare.upload(pickNow) else c.leadShare.offer(pickNow)
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
    // [PAYG-01] 1.30 BA: the way chosen waits for its 기본급 right here, put in only with it (closing leaves the pay as it was).
    var ready by remember { mutableStateOf<com.bamnat.app.company.PendingWay?>(null) }
    val h = hit ?: return false
    if (declined) return false
    val scope = rememberCoroutineScope()
    ready?.let { w ->
        PendingDone(c, c.companyOf(calendar), w, onDone = onPay, onSaved = null, onSkip = { ready = null; declined = true })
        return true
    }
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
                when (val r = s.find.use(h)) {
                    com.bamnat.app.company.PayFind.Used.Gone -> declined = true
                    is com.bamnat.app.company.PayFind.Used.Ready -> ready = r.way
                }
            } catch (e: com.bamnat.app.company.CompanyException) {
                error = companyProblem(e)
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

/**
 * [CYC-03][CYC-09] 1.38 우리 회사 근무표 올리기: the company's name and its 근무형태, then the same sheet as a public row (order
 * picked or made day by day, today in the cycle). Once it is on the calendar the plaza asks once whether others may use it
 * ([com.bamnat.app.company.LeadShare]: only shifts, times and order, no name or pay); a yes reaches the admin app's 검토
 * (제보), where a checked one can become a row of the public table ("공개 표 줄 만들기").
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UploadCycleDialog(initial: String, onDismiss: () -> Unit, onNext: (name: String, formKey: String) -> Unit) {
    var name by rememberSaveable { mutableStateOf(initial) }
    var form by rememberSaveable { mutableStateOf<String?>(null) }
    val ok = PublicCycles.norm(name).length >= 2 && form != null
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("우리 회사 근무표 올리기") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // 2.0.0 [RVN-18] COMP-22-2 both ways on (올리기만, 내 달력에도) ask before anything goes, so it says that.
                Text("근무 순서와 시간만 넣어요. 이름·급여는 보내지 않고, 다른 분이 써도 되는지는 보내기 전에 한 번 물어요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.OutlinedTextField(name, { name = it.take(40) }, label = { Text("회사·기관 이름") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth())
                Text("어떤 근무형태예요?", style = MaterialTheme.typography.titleSmall)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    PublicCycles.PROVIDE_FORMS.forEach { key ->
                        FilterChip(selected = form == key, onClick = { form = key }, label = { Text(PublicCycles.formName(key)) })
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { form?.let { onNext(name.trim(), it) } }, enabled = ok) { Text("순서 넣기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("닫기") } },
    )
}
