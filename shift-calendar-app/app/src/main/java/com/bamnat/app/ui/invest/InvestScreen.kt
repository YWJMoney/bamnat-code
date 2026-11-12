package com.bamnat.app.ui.invest

import com.bamnat.app.ui.monthSwipe
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.bamnat.app.ui.components.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.material3.OutlinedButton
import com.bamnat.app.NoticeLinks
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.app.ui.components.StatTile
import com.bamnat.app.ui.components.WarningBanner
import com.bamnat.app.ui.money.LedgerExport
import com.bamnat.app.ui.theme.NumberStyle
import com.bamnat.core.invest.CashKind
import com.bamnat.core.invest.InvestAlarms
import com.bamnat.core.invest.PeriodReturn
import com.bamnat.core.invest.ReturnMethod
import com.bamnat.core.invest.Side
import com.bamnat.core.invest.TradeDraft
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import com.bamnat.core.time.Today
import androidx.compose.ui.semantics.Role

private enum class InvestTab(val label: String) { OVERVIEW("한눈에"), HOLDINGS("종목"), RECORDS("기록"), STATS("통계"), JOURNAL("일지"), ALARMS("알람") }

/**
 * [INV-01..INV-21] The investor calendar's own tab (투자·수익): what everything is worth against what
 * was put in, the holdings, every record, the numbers, the journal and the alarms. Everything is
 * typed by the user and kept on the phone.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun InvestScreen(vm: InvestViewModel, wide: Boolean, snackbar: SnackbarHostState) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(InvestTab.OVERVIEW) }
    // [NAV-02] 1.25 Z (U4): back from another tab of 투자 is its first tab, not the calendar.
    androidx.activity.compose.BackHandler(enabled = tab != InvestTab.OVERVIEW) { tab = InvestTab.OVERVIEW }
    // Another calendar's screen starts with no sheet of this one's open.
    var sheet by remember(vm.calendarId) { mutableStateOf<InvestSheet?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshToday() }
    InvestMessages(vm, snackbar)

    // [INV-08] A tapped alarm notice opens its trade, filled in.
    val target by NoticeLinks.target.collectAsStateWithLifecycle()
    val payload by NoticeLinks.payload.collectAsStateWithLifecycle()
    val noticeCalendar by NoticeLinks.calendar.collectAsStateWithLifecycle()
    // [MCAL-03] Only the notice's own calendar takes it; another's waits for the switch to that one.
    val mine = (noticeCalendar ?: com.bamnat.app.data.PersonalCalendar.MAIN) == vm.calendarId
    LaunchedEffect(target, state.loaded, mine) {
        if (!mine) return@LaunchedEffect
        when (target) {
            NoticeLinks.Target.INVEST_TRADE -> if (state.loaded) {
                sheet = InvestSheet.TradeEdit(vm.draftFor(payload?.toLongOrNull(), null))
                NoticeLinks.consume(NoticeLinks.Target.INVEST_TRADE)
            }
            NoticeLinks.Target.INVEST -> NoticeLinks.consume(NoticeLinks.Target.INVEST)
            else -> Unit
        }
    }

    // [GUIDE-01][GUIDE-04] A feature opened from the guide or the help search: its tab, or its sheet filled in.
    val focus by com.bamnat.app.ui.guide.FeatureLinks.focus.collectAsStateWithLifecycle()
    LaunchedEffect(focus, state.loaded) {
        val key = focus ?: return@LaunchedEffect
        if (!state.loaded || !key.startsWith("invest.") || !com.bamnat.app.ui.guide.FeatureLinks.take(key)) return@LaunchedEffect
        com.bamnat.app.ui.guide.GuideUse.focus(com.bamnat.app.data.GuideStore.get(context), key)
        when (key) {
            "invest.cash" -> { tab = InvestTab.OVERVIEW; sheet = InvestSheet.CashEdit(CashKind.DEPOSIT) }
            "invest.dividend" -> sheet = InvestSheet.CashEdit(CashKind.DIVIDEND)
            "invest.trade" -> sheet = InvestSheet.TradeEdit(TradeDraft(side = Side.BUY))
            "invest.quotes" -> { tab = InvestTab.HOLDINGS; sheet = InvestSheet.Quotes }
            "invest.quantity" -> { tab = InvestTab.HOLDINGS; sheet = InvestSheet.QuantityEdit() }
            "invest.holdings" -> tab = InvestTab.HOLDINGS
            "invest.goal" -> { tab = InvestTab.OVERVIEW; sheet = InvestSheet.Goal }
            "invest.journal" -> { tab = InvestTab.JOURNAL; sheet = InvestSheet.Journal(state.today) }
            "invest.event" -> { tab = InvestTab.OVERVIEW; sheet = InvestSheet.EventEdit(state.today) }
            "invest.alarms" -> tab = InvestTab.ALARMS
            "invest.stats" -> tab = InvestTab.STATS
            "invest.records" -> tab = InvestTab.RECORDS
            "invest.overview" -> tab = InvestTab.OVERVIEW
        }
    }

    if (!state.loaded) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }
    val open: (InvestSheet) -> Unit = { sheet = it }
    Column(Modifier.fillMaxSize()) {
        InvestHeader(vm, state, open) { kind ->
            val (csv, name) = vm.csv(kind)
            scope.launch { com.bamnat.app.report.ReportShare.orTell { LedgerExport.share(context, csv, name) } } // 2.0.0 [RVP-69] (검토 REST-11) a failed file says so, with 다시 하기.
        }
        InvestTabRow(tab) { tab = it }
        // 1.39 [CALX-08] 기록·통계의 달(통계는 '연'이면 해)을 여기서 들고, 좌우로 밀어 넘겨요.
        val thisMonth = java.time.YearMonth.from(state.today).toString()
        var recordsMonth by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(thisMonth) }
        var statsMonth by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(thisMonth) }
        var statsYearly by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
        // 2.0.0 [RVK-36] (검토 MONEY-77) Each tab keeps its own place: half down 통계, over to 종목 and back is still half down 통계
        // (all six shared one, so 종목 opened halfway down and 통계 came back at 종목's place).
        val scrolls = InvestTab.entries.associateWith { rememberScrollState() }
        Column(
            Modifier.fillMaxSize()
                .then(if (tab != InvestTab.RECORDS && tab != InvestTab.STATS) Modifier else Modifier.monthSwipe(tab) { step ->
                    if (tab == InvestTab.RECORDS) recordsMonth = java.time.YearMonth.parse(recordsMonth).plusMonths(step.toLong()).toString()
                    else java.time.YearMonth.parse(statsMonth).let { ym ->
                        statsMonth = (if (statsYearly) ym.plusYears(step.toLong()) else ym.plusMonths(step.toLong())).toString()
                    }
                })
                .verticalScroll(scrolls.getValue(tab)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                InvestTab.OVERVIEW -> OverviewTab(vm, state, open)
                InvestTab.HOLDINGS -> HoldingsTab(state, open)
                InvestTab.RECORDS -> RecordsTab(state, open, recordsMonth) { recordsMonth = it }
                InvestTab.STATS -> InvestStatsTab(state, wide, vm.calendarId, statsMonth, { statsMonth = it }, statsYearly, { statsYearly = it }, onEtfKind = vm::setEtfKind)
                InvestTab.JOURNAL -> JournalTab(state, open)
                InvestTab.ALARMS -> AlarmsTab(vm, state, open)
            }
            Spacer(Modifier.height(24.dp))
        }
    }
    sheet?.let { s -> InvestSheetHost(vm, s) { sheet = it } }
    // [INV-40][INV-45] 체결 내역 캡처로 채우기: from the found trades, the ⋮ menu, or a screenshot shared from the gallery.
    val capturing by vm.capturing.collectAsStateWithLifecycle()
    val alerts by vm.alerts.collectAsStateWithLifecycle()
    val shot by com.bamnat.app.invest.IncomingFills.shot.collectAsStateWithLifecycle()
    // [INVA-01] 1.29 AR: the shared capture is read by the screen's model, so it survives the sheet, a turn of the phone
    // and the screen going off; the sheet opens on what is left to ask (or not at all when everything went in).
    LaunchedEffect(shot) { com.bamnat.app.invest.IncomingFills.take()?.let(vm::readCapture) }
    if (capturing) FillCaptureSheet(vm, alerts, null, onDismiss = vm::closeCapture)
}

/**
 * 2.0.0 [RVK-36] (총괄 HQ-63 ③, MONEY-77) The six tabs share the width, so none is cut at either edge of a folded phone (a row that
 * slid showed half a tab on each side). When a sixth of the width is too small for the words at this letter size, the six become
 * chips over two lines, as 급여's tabs do ([com.bamnat.app.ui.pay.PayTabRow]).
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InvestTabRow(tab: InvestTab, onTab: (InvestTab) -> Unit) {
    val style = MaterialTheme.typography.titleSmall
    val measurer = androidx.compose.ui.text.rememberTextMeasurer()
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val widest = remember(style, androidx.compose.ui.platform.LocalDensity.current) { InvestTab.entries.maxOf { measurer.measure(it.label, style, maxLines = 1).size.width } }
        val room = constraints.maxWidth / InvestTab.entries.size - with(androidx.compose.ui.platform.LocalDensity.current) { 8.dp.roundToPx() }
        if (widest > room) {
            androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InvestTab.entries.forEach { t -> com.bamnat.app.ui.components.FilterChip(selected = t == tab, onClick = { onTab(t) }, label = { Text(t.label) }) }
            }
        } else {
            androidx.compose.material3.PrimaryTabRow(selectedTabIndex = tab.ordinal,
                containerColor = com.bamnat.app.ui.theme.PhotoGround.solid(MaterialTheme.colorScheme.surface)) {
                InvestTab.entries.forEach { t ->
                    // The content form of Tab, so a sixth of a folded phone is enough (the text form adds 16dp on each side).
                    Tab(selected = t == tab, onClick = { onTab(t) }, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(t.label, style = style, maxLines = 1, softWrap = false, modifier = Modifier.padding(horizontal = 2.dp, vertical = 12.dp))
                    }
                }
            }
        }
    }
}

/** Saved and deleted, with 되돌리기, on the screen's snackbar. */
@Composable
fun InvestMessages(vm: InvestViewModel, snackbar: SnackbarHostState) {
    LaunchedEffect(vm) {
        vm.messages.collect { m ->
            launch {
                val r = snackbar.showSnackbar(m.message, actionLabel = m.undo?.let { m.actionLabel },
                    duration = if (m.long) SnackbarDuration.Long else SnackbarDuration.Short)
                if (r == SnackbarResult.ActionPerformed) m.undo?.let { vm.run(it) }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun InvestHeader(vm: InvestViewModel, state: InvestState, open: (InvestSheet) -> Unit, onCsv: (CsvKind) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    // 2.0.0 [RVK-34] (검토 U06b-08) The ⋮ menu was eleven lines (설정 여섯 + CSV 다섯): now six, the CSVs picked in one sheet and the
    // two 체결 switches side by side in another, each said with what it does.
    var csvOpen by rememberSaveable { mutableStateOf(false) }
    var fillOpen by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("투자·수익", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).semantics { heading() })
        // [INV-17] Amounts behind dots, for looking at it on the bus.
        IconButton(onClick = { vm.saveSettings { it.copy(hideAmounts = !it.hideAmounts) } }) {
            Icon(if (state.hide) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (state.hide) "금액 보이기" else "금액 가리기")
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "더 보기") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("목표와 수익률") }, onClick = { menu = false; open(InvestSheet.Goal) })
                DropdownMenuItem(text = { Text("내 투자 원칙") }, onClick = { menu = false; open(InvestSheet.Principles) })
                DropdownMenuItem(text = { Text("계좌 추가") }, onClick = { menu = false; open(InvestSheet.AccountEdit()) })
                // [INV-45] 1.27 AK: the capture from anywhere.
                DropdownMenuItem(text = { Text("체결 내역 캡처로 채우기") }, onClick = { menu = false; vm.openCapture() })
                DropdownMenuItem(text = { Text("체결 알림·캡처 설정") }, onClick = { menu = false; fillOpen = true })
                DropdownMenuItem(text = { Text("CSV로 내보내기") }, onClick = { menu = false; csvOpen = true })
            }
        }
    }
    if (fillOpen) com.bamnat.app.ui.components.AppSheet(onDismissRequest = { fillOpen = false }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("체결 알림·캡처", style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            // [INV-32] Found trades written at once or after 확인, also when none is waiting.
            val auto by vm.alertAuto.collectAsStateWithLifecycle()
            com.bamnat.app.ui.settings.SwitchRow("체결 알림 오면 바로 적기", if (auto) "증권사 체결 알림을 읽어 매매로 바로 적어요" else "알림에서 찾은 매매는 확인한 뒤에 적어요",
                auto) { vm.setAlertAuto(it) }
            // [INV-44] 1.27 AK: 묻지 않기 turned back.
            val trust by vm.fillTrust.collectAsStateWithLifecycle()
            com.bamnat.app.ui.settings.SwitchRow("캡처로 채울 때 넣기 전에 묻기", if (trust) "끄면 읽은 매매를 묻지 않고 바로 넣어요" else "읽은 매매를 보여 주고 넣을지 물어요",
                !trust) { vm.setFillTrust(!it) }
        }
    }
    if (csvOpen) com.bamnat.app.ui.components.AppSheet(onDismissRequest = { csvOpen = false }) {
        Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text("CSV로 내보내기", style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp).padding(bottom = 8.dp).semantics { heading() })
            CsvKind.entries.forEach { k ->
                Text(k.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.fillMaxWidth()
                    .clickable(role = Role.Button) { csvOpen = false; onCsv(k) }.heightIn(min = 48.dp).padding(horizontal = 24.dp, vertical = 12.dp))
            }
        }
    }
    // [INV-15] All accounts together, or one.
    val accounts = state.full.activeAccounts
    if (accounts.size > 1) {
        // 2.0.0 [RVK-35] (PC-126과 같은 구조) The accounts wrap instead of sliding off the folded phone's edge.
        androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = state.account == null, onClick = { vm.setAccount(null) }, label = { Text("전체") })
            accounts.forEach { a -> FilterChip(selected = state.account == a.id, onClick = { vm.setAccount(a.id) }, label = { Text(a.name) }) }
        }
    }
}

@Composable
private fun OverviewTab(vm: InvestViewModel, state: InvestState, open: (InvestSheet) -> Unit) {
    val s = state.result.summary
    val hide = state.hide
    // 2.0.0 [RVK-24] (조건 2) Trades found in notifications are the thing to do first: their card goes on top and keeps the screen's
    // one filled button, and the empty book's 입금 적기 steps back to an outlined one.
    val waiting = vm.alerts.collectAsStateWithLifecycle().value.isNotEmpty()
    if (waiting) FoundTradesCard(vm, state, open)
    if (state.full.isEmpty) {
        SectionCard(title = "원금부터 적어 보세요") {
            Text("계좌에 넣은 돈(입금)을 적으면 원금이 정해지고, 매매를 적을 때마다 수익률·현금·목표가 바로 바뀌어요.",
                style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (waiting) OutlinedButton(onClick = { open(InvestSheet.CashEdit(CashKind.DEPOSIT)) }) { Text("입금 적기") }
                else FilledTonalButton(onClick = { open(InvestSheet.CashEdit(CashKind.DEPOSIT)) }) { Text("입금 적기") }
                TextButton(onClick = { open(InvestSheet.AccountEdit()) }) { Text("계좌부터 만들기") }
            }
        }
    }
    if (!waiting) FoundTradesCard(vm, state, open)
    SummaryCard(vm, state, open)
    QuickActions(open)
    if (s.missingQuotes.isNotEmpty()) WarningBanner(
        title = "현재가를 적지 않은 종목이 ${s.missingQuotes.size}개 있어요",
        message = s.missingQuotes.take(3).joinToString(", ").let { it + com.bamnat.app.ui.Fmt.eun(it) } + " 산 가격으로 셌어요. 평가손익을 보려면 현재가를 적어 주세요.",
        action = "현재가 적기", onAction = { open(InvestSheet.Quotes) },
    )
    if (s.cashShort) WarningBanner(
        title = "현금이 ${InvestFmt.won(-s.cash, hide)} 모자라요",
        message = "산 돈보다 넣은 돈이 적게 적혀 있어요. 입금을 적으면 원금과 수익률이 맞아요.",
        action = "입금 적기", onAction = { open(InvestSheet.CashEdit(CashKind.DEPOSIT)) },
    )
    // [GUIDE-05] Cards that can be turned off for this calendar.
    com.bamnat.app.ui.guide.FeatureCard("invest.goal", vm.calendarId) { GoalCard(state, open) }
    TodayCard(state, open)
    if (state.account == null && state.full.activeAccounts.isNotEmpty()) AccountsCard(state, open)
    com.bamnat.app.ui.guide.FeatureCard("invest.history", vm.calendarId) { HistoryCard(state) }
    com.bamnat.app.ui.guide.FeatureCard("invest.events", vm.calendarId) { UpcomingCard(state, open) }
}

/** [INV-05] 총자산, 원금, 손익 and 수익률 by the chosen method, with what the method means. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun SummaryCard(vm: InvestViewModel, state: InvestState, open: (InvestSheet) -> Unit) {
    val s = state.result.summary
    val hide = state.hide
    val returns by vm.returns.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val method = state.settings.returnMethod
    val chosen: PeriodReturn? = returns.firstOrNull { it.method == method }
    SectionCard(title = state.account?.let { id -> state.full.account(id)?.name } ?: "내 투자") {
        Text("총자산", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(InvestFmt.won(s.totalAssets, hide), style = MaterialTheme.typography.headlineMedium.merge(NumberStyle), fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "총자산 ${InvestFmt.won(s.totalAssets, hide)}" })
        val rate = chosen?.rate
        Text(
            "${InvestFmt.signed(if (method == ReturnMethod.SIMPLE) s.pnl else chosen?.pnl ?: s.pnl, hide)} (${InvestFmt.percent(rate)})",
            style = MaterialTheme.typography.titleMedium.merge(NumberStyle), color = rateColor(rate), fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("원금", InvestFmt.short(s.principal, hide), Modifier.weight(1f), caption = "입금 − 출금",
                onClick = { open(InvestSheet.CashEdit(CashKind.DEPOSIT)) })
            StatTile("현금", InvestFmt.short(s.cash, hide), Modifier.weight(1f), warning = s.cashShort)
            // 2.0.0 [RVK-25] (검토 MONEY-69·I06ba-02) How old the oldest price is, by the number (before, '현재가 기준' however old);
            // after a week the caption says so in the caution colour (not red, 조건 6), and the tile opens 현재가 적기.
            val oldest = state.result.held.mapNotNull { it.quote?.date }.minOrNull()
            val stale = oldest != null && java.time.temporal.ChronoUnit.DAYS.between(oldest, state.today) >= InvestFmt.STALE_DAYS
            StatTile("평가손익", InvestFmt.signedShort(s.unrealized, hide), Modifier.weight(1f),
                caption = when {
                    s.missingQuotes.isNotEmpty() -> "일부 매입가"
                    oldest == null -> "현재가 기준"
                    else -> "${InvestFmt.ago(oldest, state.today)} 가격"
                },
                captionTone = if (stale) com.bamnat.app.ui.theme.Tone.Caution else com.bamnat.app.ui.theme.Tone.Plain,
                onClick = if (state.result.held.isNotEmpty()) ({ open(InvestSheet.Quotes) }) else null)
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("실현손익", InvestFmt.signedShort(s.realized, hide), Modifier.weight(1f))
            StatTile("배당·이자", InvestFmt.short(s.income, hide), Modifier.weight(1f), caption = "세후")
            StatTile("수수료·세금", InvestFmt.short(s.fees + s.taxes, hide), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Text("수익률 계산", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        // 2.0.0 [RVK-35] (PC-126과 같은 구조) The three ways and the periods wrap: at 글자 200% the last one was past the edge.
        androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReturnMethod.entries.forEach { m ->
                val r = returns.firstOrNull { it.method == m }
                FilterChip(selected = m == method, onClick = { vm.saveSettings { it.copy(returnMethod = m) } },
                    label = { Text("${m.label} ${InvestFmt.percent(r?.rate)}") })
            }
        }
        if (method != ReturnMethod.SIMPLE) androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ReturnPeriod.entries.forEach { p -> FilterChip(selected = p == period, onClick = { vm.setPeriod(p) }, label = { Text(p.label) }) }
        }
        Text(method.explain + if (method == ReturnMethod.SIMPLE) " 기간과 상관없이 처음부터 계산해요." else "",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (state.account != null && method == ReturnMethod.TIME_WEIGHTED) Text("계좌 하나만 볼 때는 날마다의 평가액이 없어 산 가격으로 이어 계산해요.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One tap to the records an investor writes most. 2.0.0 [RVK-35] (PC-126과 같은 구조) All six seen: '현재가' was past the folded edge. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun QuickActions(open: (InvestSheet) -> Unit) {
    androidx.compose.foundation.layout.FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilledTonalButton(onClick = { open(InvestSheet.TradeEdit(TradeDraft(side = Side.BUY))) }) { Text("매수") }
        FilledTonalButton(onClick = { open(InvestSheet.TradeEdit(TradeDraft(side = Side.SELL))) }) { Text("매도") }
        AssistChip(onClick = { open(InvestSheet.CashEdit(CashKind.DEPOSIT)) }, label = { Text("입금") })
        AssistChip(onClick = { open(InvestSheet.CashEdit(CashKind.WITHDRAW)) }, label = { Text("출금") })
        AssistChip(onClick = { open(InvestSheet.CashEdit(CashKind.DIVIDEND)) }, label = { Text("배당") })
        AssistChip(onClick = { open(InvestSheet.Quotes) }, label = { Text("현재가") })
    }
}

/** [INV-11] How near the goal is, and when it may be reached. */
@Composable
private fun GoalCard(state: InvestState, open: (InvestSheet) -> Unit) {
    val g = state.goal
    val hide = state.hide
    if (!g.hasGoal) {
        com.bamnat.app.ui.components.AddButton("목표 정하기 (금액·수익률)", onClick = { open(InvestSheet.Goal) })
        return
    }
    SectionCard(title = "목표", trailing = { TextButton(onClick = { open(InvestSheet.Goal) }) { Text("고치기") } }) {
        g.amountRatio?.let { r ->
            Text("${InvestFmt.short(state.settings.goalAmount ?: 0, hide)} 중 ${"%.0f".format(java.util.Locale.ROOT, r * 100)}%", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { r.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        }
        g.returnRatio?.let { r ->
            Text("수익률 ${state.settings.goalReturnPercent?.let { InvestFmt.quantity(it) }}% 목표의 ${"%.0f".format(java.util.Locale.ROOT, r.coerceAtLeast(0.0))}%", style = MaterialTheme.typography.bodyLarge)
            LinearProgressIndicator(progress = { (r / 100).toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
        }
        val notes = listOfNotNull(
            g.amountLeft?.takeIf { it > 0 }?.let { "남은 ${InvestFmt.short(it, hide)}" },
            g.monthsLeft?.let { if (it == 0) "목표에 닿았어요!" else "매달 넣으면 약 ${it}개월" },
            g.perMonthToDate?.let { "기한까지 매달 ${InvestFmt.short(it, hide)}" },
        )
        if (notes.isNotEmpty()) Text(notes.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [INV-06][INV-07] Today: what it earned, its trades and the journal. */
@Composable
private fun TodayCard(state: InvestState, open: (InvestSheet) -> Unit) {
    val today = state.today
    val pnl = state.daily[today] ?: 0L
    val trades = state.tradesOn(today)
    val journal = state.full.journalOn(today)
    SectionCard(title = "오늘 · ${Fmt.dayTitle(today)}", trailing = {
        TextButton(onClick = { open(InvestSheet.Journal(today)) }) { Text(if (journal == null) "일지 쓰기" else "일지 보기") }
    }) {
        Text(if (trades.isEmpty() && pnl == 0L) "오늘은 아직 매매가 없어요" else "오늘 손익 ${InvestFmt.signed(pnl, state.hide)} · 매매 ${trades.size}건",
            style = MaterialTheme.typography.bodyLarge, color = pnlColor(pnl))
        trades.take(4).forEach { t -> TradeLine(t, state, onClick = { open(InvestSheet.TradeEdit(editing = t)) }) }
        if (InvestAlarms.marketClosed(today)) Text("오늘은 국내 증시가 쉬는 날이에요", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** [INV-15] Each account's own 원금, 총자산 and 수익률. */
@Composable
private fun AccountsCard(state: InvestState, open: (InvestSheet) -> Unit) {
    val hide = state.hide
    SectionCard(title = "계좌별", trailing = { TextButton(onClick = { open(InvestSheet.AccountEdit()) }) { Text("계좌 추가") } }) {
        state.fullResult.accounts.filter { it.account?.hidden != true }.forEach { a ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.Button) { a.account?.let { open(InvestSheet.AccountEdit(it)) } }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(a.account?.name ?: "계좌", style = MaterialTheme.typography.bodyLarge)
                    Text("원금 ${InvestFmt.short(a.principal, hide)} · 현금 ${InvestFmt.short(a.cash, hide)}" + (a.account?.type?.let { " · ${it.label}" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(InvestFmt.short(a.totalAssets, hide), style = MaterialTheme.typography.bodyLarge.merge(NumberStyle))
                    Text(InvestFmt.percent(a.rate), style = MaterialTheme.typography.bodySmall.merge(NumberStyle), color = rateColor(a.rate))
                }
            }
        }
    }
}

/**
 * [INV-21] 총자산 against 원금 over the days a value was kept. 2.0.0 [RVK-34] (검토 U06b-06) On the one chart rule the 월별 손익 bars
 * keep (1.39 DSG-04): a tap shows that day's 총자산 and 원금 in one bubble over the chart, with a line down to the day, and the first
 * and last days are said under it. Before, the lines had no date, no value and nothing to tap.
 */
@Composable
private fun HistoryCard(state: InvestState) {
    val snaps = state.full.snapshots.sortedBy { it.date }.takeLast(120)
    if (state.account != null || snaps.size < 2) return
    val profit = LocalCalendarColorsSunday()
    val line = MaterialTheme.colorScheme.outline
    val mark = MaterialTheme.colorScheme.onSurface
    var picked by rememberSaveable(snaps.size) { mutableStateOf<Int?>(null) }
    SectionCard(title = "총자산 흐름") {
        val max = snaps.maxOf { maxOf(it.value, it.principal) }.coerceAtLeast(1)
        val min = snaps.minOf { minOf(it.value, it.principal) }.coerceAtMost(max - 1)
        // 2.0.0 [RVK-25] (검토 I06ba-02) Across by date: a month without a value is a long stretch, not one step like a day.
        val first = snaps.first().date
        val span = java.time.temporal.ChronoUnit.DAYS.between(first, snaps.last().date).coerceAtLeast(1).toFloat()
        val across = snaps.map { java.time.temporal.ChronoUnit.DAYS.between(first, it.date) / span }
        val bubble = picked?.let(snaps::getOrNull)
        Text(bubble?.let { "${Fmt.shortDate(it.date)} · 총자산 ${InvestFmt.short(it.value, state.hide)} · 원금 ${InvestFmt.short(it.principal, state.hide)}" } ?: " ",
            style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 4.dp))
        Canvas(Modifier.fillMaxWidth().height(120.dp).semantics {
            val top = snaps.maxBy { it.value }
            contentDescription = "${Fmt.shortDate(snaps.first().date)}부터 ${Fmt.shortDate(snaps.last().date)}까지 총자산 ${InvestFmt.short(snaps.first().value, state.hide)}에서 ${InvestFmt.short(snaps.last().value, state.hide)}" +
                ", 가장 높았던 날 ${Fmt.shortDate(top.date)} ${InvestFmt.short(top.value, state.hide)}, 원금 ${InvestFmt.short(snaps.last().principal, state.hide)}"
        }.pointerInput(snaps) {
            detectTapGestures { p ->
                val at = p.x / size.width
                val i = across.indices.minBy { kotlin.math.abs(across[it] - at) }
                picked = if (picked == i) null else i
            }
        }) {
            fun pt(i: Int, v: Long) = Offset(size.width * across[i], size.height * (1f - (v - min).toFloat() / (max - min)))
            for (i in 1 until snaps.size) {
                drawLine(line, pt(i - 1, snaps[i - 1].principal), pt(i, snaps[i].principal), strokeWidth = 2f)
                drawLine(profit, pt(i - 1, snaps[i - 1].value), pt(i, snaps[i].value), strokeWidth = 4f)
            }
            val i = picked?.takeIf { it in snaps.indices }
            if (i != null) {
                val x = size.width * across[i]
                drawLine(mark.copy(alpha = 0.4f), Offset(x, 0f), Offset(x, size.height), strokeWidth = 2f)
                drawCircle(mark, 6f, pt(i, snaps[i].value), style = Stroke(3f))
                drawCircle(line, 4f, pt(i, snaps[i].principal))
            } else drawCircle(profit, 6f, pt(snaps.size - 1, snaps.last().value), style = Stroke(3f))
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            Text(Fmt.shortDate(first), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f))
            Text(Fmt.shortDate(snaps.last().date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("굵은 선은 총자산, 가는 선은 원금이에요. 현재가를 모두 적은 날의 값이 남아요.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LocalCalendarColorsSunday() = com.bamnat.app.ui.theme.LocalCalendarColors.current.sunday

/** [INV-08][INV-14] The next alarm and the dates coming up this week. */
@Composable
private fun UpcomingCard(state: InvestState, open: (InvestSheet) -> Unit) {
    val now = Today.dateTime()
    val next = state.full.alarms.mapNotNull { a -> InvestAlarms.next(a, now)?.let { a to it } }.minByOrNull { it.second }
    val events = state.full.events.filter { !it.date.isBefore(state.today) && it.date.isBefore(state.today.plusDays(8)) }.sortedBy { it.date }
    if (next == null && events.isEmpty()) return
    SectionCard(title = "다가오는 일") {
        next?.let { (a, at) ->
            Text("${a.title} · ${Fmt.dayTitle(at.toLocalDate())} ${minuteText(at.hour * 60 + at.minute)}", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open(InvestSheet.AlarmEdit(editing = a)) }.padding(vertical = 4.dp))
        }
        events.forEach { e ->
            Text("${Fmt.shortDate(e.date)} ${e.kind.label} · ${e.label}", style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { open(InvestSheet.EventEdit(editing = e)) }.padding(vertical = 4.dp))
        }
    }
}
