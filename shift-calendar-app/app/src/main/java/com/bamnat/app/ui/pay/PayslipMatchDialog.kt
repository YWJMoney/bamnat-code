package com.bamnat.app.ui.pay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.Dialog
import androidx.compose.ui.window.DialogProperties
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.pay.Cadence
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.PayItem
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.Payslip
import com.bamnat.core.pay.PayslipMatch
import com.bamnat.core.pay.PayslipRead
import com.bamnat.core.pay.SlipRole
import com.bamnat.core.pay.ValueTimeline
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.YearMonth
import kotlin.math.abs
import com.bamnat.core.time.Today

/** A month of hours, 31일 × 24시간: more than this cannot be one month's premium. */
private const val MAX_MONTH_MINUTES = 744 * 60

private enum class MatchStage { MONTH, ROSTER, AMOUNTS, WORKING, RESULT, DONE }

/** 연장·야간·휴일 pay as the payslip states it, with the hours behind it when it lists them (근로기준법 제48조 제2항). */
internal class Premium(val title: String) {
    var amount by mutableStateOf<Long?>(null)
    /** [INPUT-02] The hours the payslip lists, stepped in minutes; 0 is "not given". */
    var stepped by mutableStateOf(0)
    /** In minutes; null when not given or more than a month's worth. */
    val minutes: Int? get() = stepped.takeIf { it in 1..MAX_MONTH_MINUTES }
}

/** An allowance on the payslip with no allowance of the same name in the app. */
internal class OtherLine(
    /** [RVJ-26] Read from the payslip; only the ones added by hand count toward [MAX_OTHERS]. */
    val fromSlip: Boolean = false,
) {
    var name by mutableStateOf("")
    var amount by mutableStateOf<Long?>(null)
}

/** What the user typed from the payslip; it never leaves this screen except as a [Payslip] to the matcher. */
internal class SlipForm {
    var gross by mutableStateOf<Long?>(null)
    var base by mutableStateOf<Long?>(null)
    val overtime = Premium("연장근로수당")
    val night = Premium("야간근로수당")
    val holiday = Premium("휴일근로수당")
    /** Amounts by the id of the profile's allowance. */
    val items = mutableStateMapOf<String, Long?>()
    val others = mutableStateListOf<OtherLine>()
    /** [PAYX-36] The payslip as read from a photo or text, for its 공제 lines; null when typed by hand. */
    var read by mutableStateOf<PayslipRead?>(null)

    val premiums: List<Premium> get() = listOf(overtime, night, holiday)

    /** 2.0.0 [RVJ-64] Anything put in at all (a name with no amount yet counts): what a draft keeps and closing asks about. */
    val typed: Boolean get() = gross != null || base != null || premiums.any { it.amount != null || it.stepped != 0 } ||
        items.values.any { it != null } || others.any { it.name.isNotBlank() || it.amount != null }

    /** [RVJ-64] 처음부터: every field empty again. */
    fun clear() {
        gross = null; base = null
        premiums.forEach { it.amount = null; it.stepped = 0 }
        items.clear(); others.clear(); read = null
    }

    /** How many optional figures are filled in, for the folded section's title. */
    val extras: Int get() = listOfNotNull(base).size + premiums.count { it.amount != null } +
        items.values.count { it != null } + others.count { it.amount != null && it.name.isNotBlank() }

    /** [PAYS-05] Fills the form from a payslip read from a photo or text; the user can still correct every field. */
    fun fill(read: PayslipRead, profile: PayProfile) {
        this.read = read
        // [PAYIN-03] 1.37 B3 (PC E11): a second payslip filled over the first left the first one's figures in every field the new
        // one does not print (its 연장 stayed when this month had none), and the match then aimed at both. The form is this
        // payslip's alone now: what it does not say is empty.
        gross = read.gross
        base = read.amount(SlipRole.BASE)
        listOf(overtime to SlipRole.OVERTIME, night to SlipRole.NIGHT, holiday to SlipRole.HOLIDAY).forEach { (p, role) ->
            p.amount = read.amount(role)
            p.stepped = read.minutes(role) ?: 0
        }
        items.clear()
        others.clear()
        // [RVJ-26] 검토 PAYU-52: every allowance read goes in. The seventh and on were dropped without a word, and the match then
        // aimed at a 지급 총액 holding money no line explained ('차이 n원', or a setting changed to make up for it).
        val places = read.places(profile.items.map { it.id to it.name }, NAME_MAX)
        places.known.forEach { (id, amount) -> items[id] = amount }
        places.others.forEach { (name, amount) -> others += OtherLine(fromSlip = true).also { it.name = name; it.amount = amount } }
    }

    fun payslip(profile: PayProfile): Payslip = Payslip(
        gross = gross ?: 0,
        base = base,
        overtimePay = overtime.amount, overtimeMinutes = overtime.minutes,
        nightPay = night.amount, nightMinutes = night.minutes,
        holidayPay = holiday.amount, holidayMinutes = holiday.minutes,
        // [RVJ-26] Two lines of one name add up (a second '소급분' wrote over the first).
        allowances = buildMap {
            profile.items.forEach { i -> items[i.id]?.let { merge(i.name, it, Long::plus) } }
            others.forEach { o -> o.amount?.let { a -> if (o.name.isNotBlank()) merge(o.name.trim(), a, Long::plus) } }
        },
    )
}

/**
 * 2.0.0 [RVJ-64] 검토 PAYU-04·U05-03 (조건 10 '적던 것은 사라지지 않아요'): what was put in from the payslip, kept on this phone
 * while it is being put in ([com.bamnat.app.ui.components.DraftStore], device only, gone by itself after 7 days), so a look at the
 * bank app, a turn to the dark theme or Android closing the app in the background no longer empties the fields. One line per figure;
 * a name loses its tabs and line breaks. The photo itself and its 공제 lines are not kept.
 */
internal object SlipDraft {
    const val KEY = "pay.match.slip"
    private const val HEAD = "slip1"

    fun encode(form: SlipForm, month: YearMonth): String {
        if (!form.typed) return ""
        return buildString {
            append(HEAD).append('\n')
            append("m\t").append(month).append('\n')
            form.gross?.let { append("g\t").append(it).append('\n') }
            form.base?.let { append("b\t").append(it).append('\n') }
            form.premiums.forEachIndexed { i, p ->
                if (p.amount != null || p.stepped != 0) append("p\t").append(i).append('\t').append(p.amount ?: "").append('\t').append(p.stepped).append('\n')
            }
            form.items.forEach { (id, v) -> if (v != null) append("i\t").append(clean(id)).append('\t').append(v).append('\n') }
            form.others.forEach { o ->
                if (o.name.isNotBlank() || o.amount != null)
                    append("o\t").append(clean(o.name)).append('\t').append(o.amount ?: "").append('\t').append(if (o.fromSlip) 1 else 0).append('\n')
            }
        }
    }

    /**
     * Fills [form] from [raw] and gives the month it was for; null when there is nothing to bring back. An allowance of the app
     * that is gone since ([ids]) is left out.
     */
    fun decode(raw: String, form: SlipForm, ids: Set<String>): YearMonth? {
        val lines = raw.lines()
        if (lines.firstOrNull() != HEAD) return null
        var month: YearMonth? = null
        lines.drop(1).forEach { line ->
            val f = line.split('\t')
            when (f[0]) {
                "m" -> month = f.getOrNull(1)?.let { runCatching { YearMonth.parse(it) }.getOrNull() }
                "g" -> form.gross = f.getOrNull(1)?.toLongOrNull()
                "b" -> form.base = f.getOrNull(1)?.toLongOrNull()
                "p" -> f.getOrNull(1)?.toIntOrNull()?.let(form.premiums::getOrNull)?.let { p ->
                    p.amount = f.getOrNull(2)?.toLongOrNull()
                    p.stepped = f.getOrNull(3)?.toIntOrNull()?.coerceIn(0, MAX_MONTH_MINUTES) ?: 0
                }
                "i" -> if (f.size >= 3 && f[1] in ids) form.items[f[1]] = f[2].toLongOrNull()
                "o" -> if (f.size >= 3) form.others += OtherLine(fromSlip = f.getOrNull(3) == "1").also {
                    it.name = f[1].take(NAME_MAX); it.amount = f[2].toLongOrNull()
                }
            }
        }
        return month?.takeIf { form.typed }
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ')
}

/**
 * [PAYS-02] 명세서로 맞춰 보기: the user types what one payslip says (only 지급 총액 is needed) and the app finds
 * the settings that come closest. What would change is shown first and nothing is saved until the user
 * agrees; 되돌리기 brings the old settings back. Legal facts are only suggested, never changed, and the
 * figures stay on the phone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PayslipMatchDialog(
    vm: PayViewModel,
    profile: PayProfile,
    snapshot: ScheduleSnapshot,
    month: YearMonth,
    onDismiss: () -> Unit,
    /** [PAYS-09] 회사 설정 보내기 for the invitation once the payslip and the app agree; null leaves that way out. */
    onShareWay: (() -> Unit)? = null,
) {
    val latest = Today.month()
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    val form = remember { SlipForm() }
    // 2.0.0 [RVJ-64] 검토 U05-03 '넣던 명세서 이어서': what was being put in last time comes back, on its month and at the amounts.
    var restored by remember {
        mutableStateOf(SlipDraft.decode(com.bamnat.app.ui.components.DraftStore.read(context, SlipDraft.KEY), form,
            profile.items.map { it.id }.toSet())?.let { minOf(it, latest) })
    }
    var chosen by remember { mutableStateOf(restored ?: minOf(month, latest)) }
    var stage by remember { mutableStateOf(if (restored != null) MatchStage.AMOUNTS else MatchStage.MONTH) }
    var more by remember { mutableStateOf(restored != null && form.extras > 0) }
    // Once the match is put in, looked at and left, or thrown away, the draft is gone and nothing writes it back.
    var finished by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        androidx.compose.runtime.snapshotFlow { SlipDraft.encode(form, chosen) }.collectLatest { t ->
            kotlinx.coroutines.delay(400)
            if (!finished) com.bamnat.app.ui.components.DraftStore.write(context, SlipDraft.KEY, t)
        }
    }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose { if (!finished) com.bamnat.app.ui.components.DraftStore.write(context, SlipDraft.KEY, SlipDraft.encode(form, chosen)) }
    }
    fun finish() {
        finished = true
        com.bamnat.app.ui.components.DraftStore.clear(context, SlipDraft.KEY)
        PayslipWorks.drop(PayslipWorks.MATCH)
    }
    var match by remember { mutableStateOf<PayslipMatch?>(null) }
    // [PAYIN-03] PC 3e347ec6: each change is the user's to take or leave (a way of counting is not swept in with the amounts).
    // PC 6da49f0b: a way of counting (시간·연장·휴일) starts unticked; the user ticks it only when the company really does so.
    var keep by remember(match) { mutableStateOf(match?.let { m -> m.changes.map { it.key }.filterNot(m::isWayChange).toSet() }.orEmpty()) }
    // PC a34f53b0 (명세서에 식대가 있고 앱에 없을 때 '바뀌는 것'에 더하기 줄이 없어, 고른 것만 바꾸기가 아무것도 안 바꿈): the
    // payslip's allowances the setup lacks are 더하기 lines to pick too, ticked to begin with.
    var adds by remember(match) { mutableStateOf(match?.unmatched?.keys?.toSet().orEmpty()) }
    fun newItems(names: List<Pair<String, Long>>): List<PayItem> {
        // From the setup's first month, as a monthly allowance counts in every month ([PAYIN-03] never from a mid-month day).
        val from = (profile.base.entries.map { it.from } + chosen.atDay(1)).min()
        // 1.39 [PAYX-57] 소급분 is paid once: it goes in as that month's rule ([oneOffRules]), not every month.
        return names.filterNot { CustomRule.looksOneMonth(it.first) }.mapIndexed { i, (name, amount) ->
            PayItem("item-${System.currentTimeMillis()}-$i", name,  // real clock: an id, not a time
                ValueTimeline(listOf(DatedValue(from, amount))), Cadence.MONTHLY,
                ordinary = com.bamnat.core.pay.OrdinaryGuess.of(name, Cadence.MONTHLY) ?: true)
        }
    }
    /** 1.39 [PAYX-57] 명세서의 소급분 (지급): that month only, taxed like pay (소급분은 그달 근로소득이에요). */
    fun oneOffRules(names: List<Pair<String, Long>>): List<CustomRule> = names.filter { CustomRule.looksOneMonth(it.first) }.mapIndexed { i, (name, amount) ->
        CustomRule("rule-${System.currentTimeMillis().toString(36)}-$i", name.take(CustomRule.MAX_NAME), com.bamnat.core.pay.RuleWhen(),  // real clock: an id
            com.bamnat.core.pay.RulePay.Monthly(amount), from = chosen, until = chosen, source = "명세서 ${chosen.monthValue}월")
    }
    var failed by remember { mutableStateOf(false) }
    // Payslip allowances the user chose to add; ids stay the same when the match runs again.
    val added = remember { mutableStateListOf<PayItem>() }
    // [PAYS-08] Rules of the company's own made from the gap, and the gap the builder is open for.
    val addedRules = remember { mutableStateListOf<CustomRule>() }
    var makingFor by remember { mutableStateOf<PayslipMatch?>(null) }
    val scope = rememberCoroutineScope()
    val appTotal by produceState<Long?>(null, chosen) { value = null; value = vm.calculateOff(profile, snapshot, chosen).total }
    // [PAYS-05] 명세서 사진으로 채우기, and what it filled.
    var scanning by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) } // [RVJ-62] the photo comes back with it
    var filled by remember { mutableStateOf<String?>(null) }
    // [READ-08] With the company's way of counting as a second photo: its ticked rules go into the pay setup from that month.
    val payAppSettings by vm.settings.collectAsState()
    if (scanning) PayslipFillDialog("명세서 사진으로 채우기", onDismiss = { scanning = false }, keep = PayslipWorks.MATCH,
        rules = RuleTarget(payAppSettings.extraKinds.kinds, snapshot.shiftTypes, profile.fiveOrMoreEmployees) { changes, m ->
            vm.applyRuleChanges(changes, m ?: chosen)
            filled = "회사 규칙 ${changes.size}개를 급여 설정에 넣었어요"
        }) { read ->
        // [PAYIN-03] Only the company's rules came (no payslip line): the figures typed here stay as they are.
        if (read.lines.isEmpty()) { scanning = false; return@PayslipFillDialog }
        form.fill(read, profile)
        vm.observePayslip(read, chosen) // [TAX-04] 세금 예측도 이 명세서로 맞춰요
        more = true
        val moved = read.month?.takeIf { !it.isAfter(latest) && it != chosen }
        moved?.let { chosen = it }
        // [RVJ-26] 검토 PAYU-52: the lines put in, not the lines read; what has no field of its own is named.
        val noField = read.unplaced.map { it.name }.distinct()
        filled = "명세서에서 ${read.placedLines}줄을 채웠어요" +
            (moved?.let { " · ${it.monthValue}월 명세서로 맞춰 볼게요" } ?: "") + ". 틀린 칸은 고쳐 주세요." +
            (if (noField.isEmpty()) "" else " ${noField.take(3).joinToString(", ")}${if (noField.size > 3) " 등 ${noField.size}줄" else " 줄"}은 따로 넣는 칸이 없어 지급 총액으로만 맞춰 봐요.")
        scanning = false
    }

    fun startMatch() {
        val slip = form.payslip(profile)
        stage = MatchStage.WORKING
        failed = false
        scope.launch {
            try {
                match = vm.match(profile.copy(items = profile.items + added, customRules = profile.customRules + addedRules), snapshot, chosen, slip)
                stage = MatchStage.RESULT
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed = true
                stage = MatchStage.AMOUNTS
            }
        }
    }

    fun back() {
        stage = when (stage) {
            MatchStage.ROSTER, MatchStage.AMOUNTS -> MatchStage.MONTH
            MatchStage.RESULT -> MatchStage.AMOUNTS
            else -> stage
        }
    }
    val canGoBack = stage == MatchStage.ROSTER || stage == MatchStage.AMOUNTS || stage == MatchStage.RESULT

    makingFor?.let { m ->
        CustomItemDialog(
            vm, m.profile, snapshot, chosen,
            onDismiss = { makingFor = null },
            onSave = { made ->
                when (made) {
                    is MadeItem.Item -> added += made.item
                    is MadeItem.Rule -> addedRules += made.rule
                }
                makingFor = null
                startMatch()
            },
            gap = m.gap,
        )
    }

    // 2.0.0 [RVJ-63] 검토 PAYU-05 (조건 10): 닫기·뒤로 한 번에 넣던 숫자를 버리지 않고 한 번 물어요. '닫고 달력 고치기' keeps them
    // for when the user comes back ([SlipDraft]).
    val guard = com.bamnat.app.ui.components.rememberCloseGuard(
        unsaved = !finished && form.typed && stage != MatchStage.DONE,
        onLeave = { finish(); onDismiss() },
    )
    Dialog(onDismissRequest = guard::close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        BackHandler(enabled = canGoBack) { back() }
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("명세서로 맞춰 보기") },
                        navigationIcon = {
                            if (canGoBack) IconButton(onClick = ::back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "이전") }
                            else IconButton(onClick = guard::close) { Icon(Icons.Filled.Close, "닫기") }
                        },
                    )
                },
                bottomBar = {
                    val m = match
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        when (stage) {
                            MatchStage.AMOUNTS -> Button(
                                onClick = ::startMatch,
                                enabled = (form.gross ?: 0) > 0,
                                modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                            ) { Text("맞춰 보기") }
                            MatchStage.RESULT -> if (m != null) {
                                OutlinedButton(onClick = { stage = MatchStage.AMOUNTS }, modifier = Modifier.heightIn(min = 52.dp)) { Text("다시 넣기") }
                                val nothingThere = m.changes.isEmpty() && m.unmatched.isEmpty() && added.isEmpty() && addedRules.isEmpty()
                                // PC a34f53b0: with nothing ticked the button says so and only closes.
                                val nothingPicked = !nothingThere && keep.isEmpty() && adds.isEmpty() && added.isEmpty() && addedRules.isEmpty()
                                Button(
                                    onClick = {
                                        if (nothingPicked) { finish(); onDismiss() }
                                        else {
                                            val picked = m.unmatched.filterKeys { it in adds }.toList()
                                            vm.applyMatch(m, keep, newItems(picked), oneOffRules(picked))
                                            finish()
                                            stage = MatchStage.DONE
                                        }
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                                ) {
                                    Text(when {
                                        nothingThere -> "확인했어요"
                                        nothingPicked -> "바꾸지 않고 닫기"
                                        keep.size < m.changes.size || adds.size < m.unmatched.size -> "고른 것만 바꾸기"
                                        else -> "이 설정으로 바꾸기"
                                    })
                                }
                            }
                            MatchStage.DONE -> Button(onClick = onDismiss, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text("닫기") }
                            else -> Unit
                        }
                    }
                },
            ) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    AnimatedContent(
                        targetState = stage,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "payslip-stage",
                        modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
                    ) { s ->
                        Column(
                            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            when (s) {
                                MatchStage.MONTH -> MonthStage(profile, chosen, latest, appTotal, { chosen = it }) { same ->
                                    stage = if (same) MatchStage.AMOUNTS else MatchStage.ROSTER
                                }
                                MatchStage.ROSTER -> RosterStage(onFix = onDismiss, onAnyway = { stage = MatchStage.AMOUNTS })
                                MatchStage.AMOUNTS -> AmountsStage(profile, chosen, form, appTotal, more, { more = it }, failed, filled,
                                    resume = PayslipWorks.waiting(PayslipWorks.MATCH)?.kept,
                                    restored = restored,
                                    onFresh = {
                                        // [RVJ-64] 처음부터: the kept figures go, and the month is asked again.
                                        form.clear(); filled = null; more = false; restored = null
                                        com.bamnat.app.ui.components.DraftStore.clear(context, SlipDraft.KEY)
                                        PayslipWorks.drop(PayslipWorks.MATCH)
                                        chosen = minOf(month, latest)
                                        stage = MatchStage.MONTH
                                    }) { scanning = true }
                                MatchStage.WORKING -> Working()
                                MatchStage.RESULT -> match?.let { m ->
                                    ResultStage(m, added, addedRules, keep, { k -> keep = if (k in keep) keep - k else keep + k },
                                        adds, { n -> adds = if (n in adds) adds - n else adds + n }, onMakeGap = { makingFor = m },
                                        audit = { SlipAuditCard(m, form.payslip(profile), form.read, snapshot) }) { names ->
                                        added += newItems(names)
                                        addedRules += oneOffRules(names)
                                        startMatch()
                                    }
                                }
                                MatchStage.DONE -> match?.let { m ->
                                    DoneStage(m.changes.isNotEmpty() || added.isNotEmpty() || addedRules.isNotEmpty())
                                    // [PAYS-09] Only once the setup proved itself: the invitation to share it.
                                    if (m.matched) com.bamnat.app.ui.company.ShareWayCard(
                                        com.bamnat.app.ui.company.ShareMoment.MATCHED, onShare = onShareWay?.let { go -> { onDismiss(); go() } },
                                        modifier = Modifier.padding(top = 16.dp),
                                        ways = waysText(m.profile, snapshot.shiftTypes.associate { it.id to it.name }, null),
                                        notInFile = m.profile.customRules.isNotEmpty(),
                                    )
                                }
                            }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.MonthStage(
    profile: PayProfile,
    month: YearMonth,
    latest: YearMonth,
    appTotal: Long?,
    onMonth: (YearMonth) -> Unit,
    onRoster: (Boolean) -> Unit,
) {
    StepTitle("어느 달 명세서인가요?", "가지고 있는 명세서 한 장이면 돼요. 앱이 계산한 그 달과 나란히 놓고 맞춰 봐요.")
    MonthStepper(month, { if (!it.isAfter(latest)) onMonth(it) }, Modifier.align(Alignment.CenterHorizontally))
    val start = profile.period.start(month)
    val end = profile.period.end(month)
    Text(
        "급여 기간 ${start.monthValue}월 ${start.dayOfMonth}일~${end.monthValue}월 ${end.dayOfMonth}일 · 앱 계산 " +
            (appTotal?.let(::won) ?: "계산 중"),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    StepTitle("그 달 근무가 달력과 같았나요?", "근무, 잔업·특근·대근, 쉬는 날이 달력과 같아야 정확히 맞춰 볼 수 있어요.")
    AnswerCard("네, 같았어요", null, false) { onRoster(true) }
    AnswerCard("아니요, 달라요", "달력을 먼저 고치면 더 정확해요", false) { onRoster(false) }
}

@Composable
private fun RosterStage(onFix: () -> Unit, onAnyway: () -> Unit) {
    StepTitle("먼저 달력을 고쳐 주세요", "근무표가 실제와 다르면 맞춰 본 결과도 어긋나요. 달력 탭에서 그 달 근무와 잔업·특근·대근을 고친 뒤 다시 와 주세요.")
    Button(onClick = onFix, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("닫고 달력 고치기") }
    TextButton(onClick = onAnyway, modifier = Modifier.fillMaxWidth()) { Text("그래도 맞춰 볼게요") }
}

@Composable
private fun ColumnScope.AmountsStage(
    profile: PayProfile,
    month: YearMonth,
    form: SlipForm,
    appTotal: Long?,
    more: Boolean,
    onMore: (Boolean) -> Unit,
    failed: Boolean,
    filled: String?,
    /** 2.0.0 [RVJ-62] What a payslip being read holds (lines and 칸), when one waits to go on ([PayslipWorks]). */
    resume: Int? = null,
    /** 2.0.0 [RVJ-64] The month of the figures brought back from last time ([SlipDraft]); null when they were typed now. */
    restored: YearMonth? = null,
    onFresh: () -> Unit = {},
    onScan: () -> Unit,
) {
    // [RVJ-64] 검토 U05-03: one line says the figures are last time's, with the way to start over beside it.
    if (restored != null) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("넣던 ${restored.monthValue}월 명세서를 이어서 적어요", style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        TextButton(onClick = onFresh, modifier = Modifier.heightIn(min = 48.dp)) { Text("처음부터") }
    }
    // [PAYS-04][PAYS-05] The easiest way first: one photo fills every field below.
    Surface(
        onClick = onScan,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.PhotoCamera, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(if (resume != null) "넣던 명세서 이어서" else "명세서 사진으로 한 번에 채우기", style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface)
                Text(if (resume != null) "읽고 고친 ${resume}줄이 그대로 있어요. 누르면 하던 데서 이어서 봐요."
                    else "사진이나 캡처 한 장이면 아래 칸을 앱이 채워요. 휴대폰 안에서만 읽어요.", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    filled?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
    StepTitle("${month.monthValue}월 명세서의 지급 총액을 넣어 주세요", "세금과 4대보험을 빼기 전 금액이에요. ‘지급 합계’나 ‘지급액 계’로 적혀 있기도 해요.")
    MoneyField("지급 총액", form.gross, { form.gross = it })
    val gross = form.gross
    if (gross != null && appTotal != null && gross < appTotal * 0.85) {
        Text(
            "앱 계산(${won(appTotal)})보다 많이 적어요. 실수령액을 넣으셨다면 공제 전 지급 총액으로 바꿔 주세요.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.tertiary,
        )
        // [PAYX-23] A 실수령액 typed in: the pay before tax that leaves it, by the same 4대보험·간이세액표 the app uses.
        val mode = profile.taxMode.takeIf { it != com.bamnat.core.pay.TaxMode.NONE } ?: com.bamnat.core.pay.TaxMode.INSURANCE_4
        val before = remember(gross, profile, month) {
            // 1.39 [PAYX-56] 실수령액 is after 사택관리비·회비 too: they are added back before the tax is worked back.
            com.bamnat.core.pay.TakeHome.grossFromNet(gross + com.bamnat.core.pay.TakeHome.afterTax(profile, month).sumOf { it.second }, mode,
                profile.taxFamily, month.atDay(1), profile.taxChildren, com.bamnat.core.pay.TakeHome.nonTaxable(profile, month), profile.insuranceOff)
        }
        if (before > gross) TextButton(onClick = { form.gross = before }) {
            Text("실수령액이었다면: 공제 전 약 ${won(before)}으로 바꾸기")
        }
    }
    if (failed) Text("맞춰 보지 못했어요. 한 번 더 눌러 주세요.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)

    SectionCard(title = null) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button) { onMore(!more) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("더 넣으면 더 정확해요 (선택)", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (form.extras > 0) "${form.extras}개 넣었어요" else "기본급, 연장·야간·휴일 수당과 시간, 다른 수당",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(if (more) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore, if (more) "접기" else "펼치기")
        }
        Column(Modifier.animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (more) MoreFields(profile, month, form)
        }
    }
    Text("넣은 금액은 이 휴대폰 밖으로 나가지 않아요.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun MoreFields(profile: PayProfile, month: YearMonth, form: SlipForm) {
    Spacer(Modifier.height(4.dp))
    MoneyField("기본급", form.base, { form.base = it })
    form.premiums.forEach { p ->
        MoneyField(p.title, p.amount, { p.amount = it })
        // [INPUT-02] The hours behind it, stepped by 30분 (the value opens an exact entry), not typed as 12.5.
        com.bamnat.app.ui.components.StepperRow("${p.title.removeSuffix("수당").trim()} 시간", hint = "명세서에 있으면") {
            com.bamnat.app.ui.components.MinutesStepper(p.stepped, { p.stepped = it }, max = MAX_MONTH_MINUTES, dialogTitle = "${p.title} 시간")
        }
    }
    Text("명세서 아래쪽 ‘계산 방법’에 적힌 시간을 넣으면 통상시급까지 맞춰 봐요.", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    // A bonus set as a share of 기본급 follows the 기본급, so only fixed amounts are asked.
    val items = profile.items.filter { it.cadence != Cadence.BONUS || it.percentOfBase == null && it.timesIn(month) > 0 }
    if (items.isNotEmpty()) {
        Text("앱에 넣어 둔 수당", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
        items.forEach { item -> MoneyField(item.name, form.items[item.id], { form.items[item.id] = it }) }
    }
    Text("명세서에만 있는 수당", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
    form.others.forEachIndexed { i, line ->
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = line.name,
                onValueChange = { line.name = it.take(NAME_MAX) },
                label = { Text("이름") },
                placeholder = { Text("예: 자격수당") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            MoneyField("금액", line.amount, { line.amount = it }, Modifier.weight(1.2f))
            IconButton(onClick = { form.others.removeAt(i) }, modifier = Modifier.padding(top = 8.dp)) { Icon(Icons.Filled.Close, "이 줄 지우기") }
        }
    }
    // [RVJ-26] The six are the ones typed by hand; what the payslip itself listed is all there, however many.
    if (form.others.count { !it.fromSlip } < MAX_OTHERS) {
        com.bamnat.app.ui.components.AddButton("수당 넣기", onClick = { form.others += OtherLine() })
    }
}

private const val MAX_OTHERS = 6
private const val NAME_MAX = 20

/** A gap of this share of the payslip or more points at the roster or an allowance first. */
private const val FAR_PERCENT = 5.0

@Composable
private fun ColumnScope.Working() {
    Spacer(Modifier.heightIn(min = 48.dp))
    CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
    Text("여러 계산 방식으로 맞춰 보는 중이에요", style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.align(Alignment.CenterHorizontally))
}

@Composable
private fun ResultStage(
    m: PayslipMatch,
    added: List<PayItem>,
    addedRules: List<CustomRule>,
    keep: Set<String>,
    onToggle: (String) -> Unit,
    adds: Set<String>,
    onToggleAdd: (String) -> Unit,
    onMakeGap: () -> Unit,
    audit: @Composable () -> Unit = {},
    onAdd: (List<Pair<String, Long>>) -> Unit,
) {
    val pct = "%.1f".format(abs(m.gapPercent))
    val (title, body) = when {
        m.matched && m.changes.isEmpty() && added.isEmpty() -> "지금 설정이 명세서와 맞아요" to "차이 ${won(abs(m.gap))} ($pct%)"
        m.matched -> "이렇게 바꾸면 명세서와 맞아요" to "차이 ${won(abs(m.gap))} ($pct%)"
        else -> "가장 가까운 설정이에요" to
            "앱이 ${won(abs(m.gap))} ${if (m.gap > 0) "적게" else "많이"} 계산해요 ($pct%)"
    }
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (m.matched) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.matched) {
                Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(12.dp))
            }
            Column {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Text(body, style = MaterialTheme.typography.bodyMedium)
                Text("명세서 ${won(m.gross)} · 앱 ${won(m.total)}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                // A gap this wide is more often a day or an allowance missing than a way of calculating.
                if (!m.matched && abs(m.gapPercent) >= FAR_PERCENT) {
                    Text("차이가 커요. 그 달 근무와 수당이 실제와 같은지 먼저 봐 주세요.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }

    if (m.changes.isNotEmpty() || m.unmatched.isNotEmpty() || added.isNotEmpty() || addedRules.isNotEmpty()) {
        SectionCard(title = "바뀌는 것") {
            val (amounts, ways) = m.changes.partition { !m.isWayChange(it.key) }
            if (m.changes.isNotEmpty() || m.unmatched.isNotEmpty()) Text("바꿀 것만 골라 주세요. 빼면 지금 설정 그대로예요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            amounts.forEach { c -> PickRow(c.label, "${c.from} → ${c.to}", c.key in keep) { onToggle(c.key) } }
            m.unmatched.forEach { (name, amount) -> PickRow("‘$name’ 더하기",
                if (CustomRule.looksOneMonth(name)) "명세서에만 있는 소급분 · ${m.month.monthValue}월만 ${won(amount)}" else "명세서에만 있는 수당 · 매달 ${won(amount)}", name in adds) { onToggleAdd(name) } }
            if (ways.isNotEmpty()) {
                Text("계산 방식 (회사가 정말 이렇게 계산하는지 알 때만)", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary, modifier = Modifier.padding(top = 8.dp))
                ways.forEach { c -> PickRow(c.label, "${c.from} → ${c.to}", c.key in keep) { onToggle(c.key) } }
            }
            if (keep.size < m.changes.size || adds.size < m.unmatched.size) Text("다 고르지 않으면 이 달 계산이 명세서와 다를 수 있어요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
            added.forEach { a ->
                val now = m.profile.items.firstOrNull { it.id == a.id } ?: a
                ChangeRow(now.name, "새 수당 · ${now.cadence.label} ${won(now.amount.on(m.month).value)} · 통상임금 ${if (now.ordinary) "포함" else "제외"}")
            }
            addedRules.forEach { r -> ChangeRow(r.name, (if (r.deduct) "새 차감 · " else "새 항목 · ") + r.describe()) }
        }
    }

    // [PAYX-41] Line by line: what to ask the company and what to fix in the app, and the 공제 next to the estimate.
    audit()

    // [PAYS-08] What is left of the gap points at the line the app does not have, and makes it in one go.
    if (!m.matched) GapCard(m.gap, onMakeGap)

    // The payslip-only allowances are picked in '바뀌는 것'; adding them first shows how close the month then comes.
    if (adds.isNotEmpty()) OutlinedButton(onClick = { onAdd(m.unmatched.filterKeys { it in adds }.toList()) }, modifier = Modifier.fillMaxWidth()) {
        Text("고른 수당을 넣고 다시 맞춰 보기")
    }

    val hints = m.hints.filterNot { it.key.startsWith("unmatched:") }
    if (hints.isNotEmpty()) {
        SectionCard(title = "회사에 확인해 보세요") {
            Text("법이나 회사 사정에 달린 것이라 앱이 바꾸지 않았어요. 맞다면 설정에서 직접 바꿔 주세요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            hints.forEach { h ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(h.text, style = MaterialTheme.typography.bodyMedium)
                    Text("그렇다면 ${won(h.total)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    m.belowLaw?.let { below ->
        Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("법 기준으로 계산하면 이 달 ${won(below)} 더 많아요", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer)
                Text("회사 계산 방식이 법 기준보다 적게 주는 부분이 있을 수 있어요. 회사에 물어보거나 고용노동부 상담센터(국번 없이 1350)에서 확인해 보세요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
        }
    }

    m.payslipHourly?.let {
        Text("명세서의 수당과 시간으로 거꾸로 계산한 통상시급은 ${won(it)}이에요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * [PAYX-36][PAYX-41] 내 월급 맞나요?: the pay lines that differ in two baskets, then the payslip's 공제 next to the
 * app's estimate for the same 지급 총액.
 */
@Composable
internal fun SlipAuditCard(m: PayslipMatch, slip: Payslip, read: PayslipRead?, snapshot: ScheduleSnapshot) {
    val pay = remember(m) { com.bamnat.core.pay.PayCalculator().calculate(m.profile, m.month, snapshot.resolver::resolve, checks = false) }
    val gaps = remember(m, slip) { com.bamnat.core.pay.SlipAudit.gaps(slip, pay) }
    val deductions = remember(m, read) { read?.let { com.bamnat.core.pay.SlipAudit.deductions(it, m.profile, m.month) }.orEmpty() }
    // D 1.32 [PAYA-02] The hours behind each premium, so a gap reads as 요율 or as 근무 기록·마감.
    val hours = remember(m, slip) { com.bamnat.core.pay.SlipAudit.hours(slip, pay) }
    if (gaps.isEmpty() && deductions.isEmpty() && hours.isEmpty()) return
    val context = androidx.compose.ui.platform.LocalContext.current
    val ui = androidx.compose.runtime.rememberCoroutineScope()
    SectionCard(title = "줄마다 맞춰 보기", trailing = {
        TextButton(onClick = {
            ui.launch {
                // 2.0.0 [RVP-69] (검토 REST-11) a failed file says so, with 다시 하기.
                com.bamnat.app.report.ReportShare.orTell { com.bamnat.app.report.ReportShare.pdf(context, com.bamnat.core.pay.SlipAudit.doc(m.month, m.gross, m.total, gaps, deductions, m.belowLaw, hours),
                    "bamnat-명세서대조-${m.month}.pdf") }
            }
        }) { Text("PDF") }
    }) {
        com.bamnat.core.pay.SlipAudit.Basket.entries.forEach { b ->
            val mine = gaps.filter { it.basket == b }
            if (mine.isEmpty()) return@forEach
            Text(b.label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp),
                color = if (b == com.bamnat.core.pay.SlipAudit.Basket.COMPANY_LESS) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
            mine.forEach { g ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(g.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("명세서 ${won(g.slip)} · 앱 ${won(g.app)}", style = MaterialTheme.typography.bodySmall)
                }
            }
            Text(b.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (hours.isNotEmpty()) {
            Text("시간 비교 (명세서 · 달력)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            hours.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(h.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${com.bamnat.core.pay.SlipAudit.hoursText(h.slipMinutes)} · ${com.bamnat.core.pay.SlipAudit.hoursText(h.appMinutes)}",
                        style = MaterialTheme.typography.bodySmall)
                    Text(" ${h.cause.label}", style = MaterialTheme.typography.labelMedium,
                        color = if (h.cause == com.bamnat.core.pay.SlipAudit.Cause.SAME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                }
            }
            com.bamnat.core.pay.SlipAudit.hoursVerdict(hours)?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            hours.map { it.cause }.distinct().filter { it != com.bamnat.core.pay.SlipAudit.Cause.SAME }.forEach {
                Text(it.hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (gaps.any { it.basket == com.bamnat.core.pay.SlipAudit.Basket.COMPANY_LESS } || m.belowLaw != null) {
            Text(com.bamnat.core.pay.SlipAudit.ASK_NOTE, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (deductions.isNotEmpty()) {
            Text("공제 (명세서 · 앱 예상)", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
            // E 1.32 [LEDX-11] Each line opens to how it is worked out.
            var open by remember { mutableStateOf<String?>(null) }
            deductions.forEach { d ->
                Row(Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(onClickLabel = "${d.name} 설명") { open = if (open == d.name) null else d.name }
                    .padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(d.name + if (open == d.name) " ▴" else " ▾", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    Text("${d.slip?.let(::won) ?: "없음"} · ${d.estimate?.let(::won) ?: "-"}", style = MaterialTheme.typography.bodySmall)
                }
                if (open == d.name) Text(com.bamnat.core.pay.SlipAudit.explain(d.name, m.gross, m.month), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
            }
            Text("국민연금은 작년 소득으로 정한 금액이라 다를 수 있어요. 소득세는 회사가 고른 비율(80·100·120%)과 가족 수에 따라 달라요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** [PAYS-08] "명세서보다 12,000원 적어요 → 이 차이를 항목으로 만들기". */
@Composable
private fun GapCard(gap: Long, onMake: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                if (gap > 0) "앱이 명세서보다 ${won(gap)} 적어요" else "앱이 명세서보다 ${won(-gap)} 많아요",
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Text(
                if (gap > 0) "회사만 주는 수당이 앱에 없을 수 있어요. 그 항목을 만들면 차이가 메워지는지 바로 다시 맞춰 봐요."
                else "회사가 빼는 돈(차감)이 앱에 없을 수 있어요. 그 항목을 만들면 바로 다시 맞춰 봐요.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Button(onClick = onMake, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                Icon(Icons.Filled.Add, null)
                Text(" 이 차이를 항목으로 만들기")
            }
        }
    }
}

/** [PAYIN-03] One change to take or leave. */
@Composable
private fun PickRow(label: String, text: String, checked: Boolean, onToggle: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Checkbox, onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = checked, onCheckedChange = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ChangeRow(label: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ColumnScope.DoneStage(changed: Boolean) {
    Spacer(Modifier.height(40.dp))
    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(56.dp).align(Alignment.CenterHorizontally))
    Text(if (changed) "바꿨어요" else "맞춰 본 것을 남겨 뒀어요", style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.align(Alignment.CenterHorizontally))
    Text(
        (if (changed) "급여 화면의 ‘방금 바꾼 것 되돌리기’로 예전 설정으로 돌아갈 수 있어요. " else "") +
            "명세서 금액은 이 휴대폰에만 남아요.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.align(Alignment.CenterHorizontally),
    )
}
