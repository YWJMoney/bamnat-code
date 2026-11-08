package com.bamnat.app.ui.money

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.bamnat.app.ui.monthSwipe
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.NoticeLinks
import com.bamnat.app.appContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.data.AutoRecordPrefs
import com.bamnat.app.data.Capture
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.tier.Gate
import com.bamnat.app.ui.Fmt
import com.bamnat.app.ui.UndoMessage
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.components.SectionCard
import com.bamnat.core.money.AccountType
import com.bamnat.core.money.BudgetLine
import com.bamnat.core.money.Budgets
import com.bamnat.core.money.CaptureFilter
import com.bamnat.core.money.CategoryEdit
import com.bamnat.core.money.DailySales
import com.bamnat.core.money.FixedCost
import com.bamnat.core.money.Job
import com.bamnat.core.money.Ledger
import com.bamnat.core.money.LedgerFilter
import com.bamnat.core.money.LedgerPeriod
import com.bamnat.core.money.MoneyAccount
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.tax.TaxPlus
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.money.MoneyStats
import com.bamnat.core.money.MonthMoney
import com.bamnat.core.money.OtherPlaces
import com.bamnat.core.money.RepeatFinder
import com.bamnat.core.money.Reports
import com.bamnat.core.money.Upcoming
import com.bamnat.core.money.UpcomingPayments
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.pay.PayIncome
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.YearMonth
import com.bamnat.core.time.Today

class MoneyViewModel(
    private val container: AppContainer,
    /** [MCAL-06] The calendar whose money this is: the one on the calendar tab. */
    val calendarId: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) : ViewModel(), MoneyActions {
    private val scope = container.scopeOf(calendarId)
    /** [NAV-01] The month shown here, the calendar's and 근무·급여's too. */
    val shownMonth: StateFlow<YearMonth> = container.shownMonth.month
    fun showMonth(month: YearMonth) = container.shownMonth.show(month)
    private val money = scope.money
    val book: StateFlow<MoneyBook?> = money.book.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val snapshot: StateFlow<ScheduleSnapshot?> =
        scope.schedule.live.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), scope.schedule.lastSnapshot)
    val settings: StateFlow<AppSettings> = scope.settings.stateIn(viewModelScope, SharingStarted.Eagerly, scope.currentSettings())
    val payProfile: StateFlow<PayProfile?> = scope.pay.profile
    val gate: StateFlow<Gate> = container.featureGate.state
    private val payCalculator = PayCalculator()

    init {
        // [LEDG-09] Repeating lines set to write themselves are written when the money tab opens.
        container.appScope.launch { runCatching { money.catchUpRecurring(Today.date()) } }
    }

    /** [LEDG-15] The money tab's three pages. */
    enum class Page(val label: String) { SUMMARY("요약"), LIST("내역"), REPORT("통계") }

    private val _page = MutableStateFlow(Page.SUMMARY)
    val page: StateFlow<Page> = _page.asStateFlow()
    fun showPage(p: Page) { _page.value = p }
    /** [DAILY-15] 화면 꾸미기 is the app's own setting, the same for every calendar. */
    fun updateSettings(change: (AppSettings) -> AppSettings) = container.settingsRepository.update(change)

    private val _view = MutableStateFlow(LedgerView())
    /** [LEDG-07] The 내역 list's period, conditions and order, kept while the user moves between pages. */
    val view: StateFlow<LedgerView> = _view.asStateFlow()
    fun setView(v: LedgerView) { _view.value = v }

    private val _selection = MutableStateFlow<Set<Long>>(emptySet())
    val selection: StateFlow<Set<Long>> = _selection.asStateFlow()
    fun select(ids: Set<Long>) { _selection.value = ids }

    /** [LEDG-10] From 분석 to the lines behind a category or a place, in the month on screen. */
    fun showCategory(category: String) {
        _view.value = LedgerView(period = LedgerPeriod.MONTH, filter = LedgerFilter(categories = setOf(category)))
        _page.value = Page.LIST
    }

    fun showPlace(place: String) {
        _view.value = LedgerView(period = LedgerPeriod.MONTH, filter = LedgerFilter(query = place))
        _page.value = Page.LIST
    }

    /** [WORK-04] Wages come from the existing pay engine; null when it is not set up. [MON-01] Free for everyone. */
    fun payEstimate(style: WorkStyle?, profile: PayProfile?, snapshot: ScheduleSnapshot?, month: YearMonth): Long? {
        if (style?.usesPayEngine == false || profile == null || snapshot == null) return null
        val gross = { m: YearMonth -> runCatching { payCalculator.calculate(profile, m, snapshot.resolver::resolve, checks = false).total }.getOrNull() }
        // [LEDG-29] As the bank account sees it: the 실수령 that arrives on the pay day in this month.
        if (profile.ledgerTakeHome) return PayIncome.paidIn(profile, month, gross).takeIf { it.isNotEmpty() }?.sumOf { it.net }
        return gross(month)
    }

    /** E 1.32 [LEDX-10] The year's 총급여 the pay engine expects (세전, every month of [year]); null without pay set up. */
    suspend fun yearGross(style: WorkStyle?, profile: PayProfile?, snapshot: ScheduleSnapshot?, year: Int): Long? = withContext(Dispatchers.Default) {
        if (style?.usesPayEngine == false || profile == null || snapshot == null) return@withContext null
        (1..12).sumOf { m -> runCatching { payCalculator.calculate(profile, YearMonth.of(year, m), snapshot.resolver::resolve, checks = false).total }.getOrDefault(0L) }
            .takeIf { it > 0 }
    }

    /**
     * 1.37 [LEDH-01][LEDH-07] What the pay engine knows ahead, from the shifts on the calendar: the take-home on
     * each pay day from last month to [until]'s month, and the take-home per hour of the latest month worked.
     * The same engine and tax setting as the pay screen, so the numbers match it to the won.
     */
    suspend fun aheadPay(style: WorkStyle?, profile: PayProfile?, snapshot: ScheduleSnapshot?, today: java.time.LocalDate, until: java.time.LocalDate): AheadPay =
        withContext(Dispatchers.Default) {
            if (style?.usesPayEngine == false || profile == null || snapshot == null || snapshot.isEmpty) return@withContext AheadPay()
            val first = YearMonth.from(today).minusMonths(1)
            val months = generateSequence(first) { it.plusMonths(1) }.takeWhile { !it.isAfter(YearMonth.from(until)) }.toList()
            val pays = months.associateWith { m -> runCatching { payCalculator.calculate(profile, m, snapshot.resolver::resolve, checks = false) }.getOrNull() }
            val days = pays.mapNotNull { (m, p) ->
                val gross = p?.total?.takeIf { it > 0 } ?: return@mapNotNull null
                val date = profile.period.payDate(m)
                com.bamnat.core.money.PayDay(date ?: m.atEndOfMonth(), PayIncome.net(profile, m, gross), "${m.monthValue}월 급여", dateGuessed = date == null)
            }
            // The latest month whose work is done: its take-home over the minutes it counted, and over the days worked.
            val done = pays.entries.filter { (m, p) -> p != null && profile.period.end(m).isBefore(today) }.reversed()
                .firstOrNull { (_, p) -> p!!.minutes.sumOf { it.total.worked } >= 600 }
            val hourly = done?.let { (m, p) -> com.bamnat.core.money.WorkTime.hourly(PayIncome.net(profile, m, p!!.total), p.minutes.sumOf { it.total.worked }) }
            val perShift = done?.let { (m, p) -> p!!.minutes.count { it.total.worked > 0 }.takeIf { it > 0 }?.let { PayIncome.net(profile, m, p.total) / it } }
            // 1.37 [LEDH-16] Last month's work for 지난달 돈 이야기.
            val last = pays[YearMonth.from(today).minusMonths(1)]?.let { p ->
                MonthWork(p.minutes.sumOf { it.total.worked }, p.minutes.count { it.total.worked > 0 }, p.minutes.count { it.total.night > 0 },
                    PayIncome.net(profile, p.month, p.total))
            }
            AheadPay(days, hourly, profile.period.payDay, perShift, last)
        }

    /** E 1.32 [LEDX-09] A subscription the user said they still use, by place, so it is not asked again for 90 days. */
    fun stillUsing(place: String) = container.autoRecordPrefs.lookedAt(com.bamnat.core.money.PaymentParser.placeKey(place))

    /** [TAX-01][TAX-04] The calendar's 월급 관측 and tax answers. */
    private val tax = container.tax(calendarId)
    val payRecords: StateFlow<List<com.bamnat.core.tax.PayObservation>> =
        tax.observations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val taxSettings: StateFlow<com.bamnat.core.tax.TaxSettings> =
        tax.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.bamnat.core.tax.TaxSettings())

    /** [TAX-01] Pay that came in since the last look becomes 월급 관측, when the money tab opens. */
    fun checkSalary() {
        container.appScope.launch {
            runCatching { com.bamnat.app.tax.SalaryWatch.check(container, calendarId) }
                .onFailure { if (it is kotlinx.coroutines.CancellationException) throw it }
        }
    }

    /** [J133-01] The pay setup's tax way, picked on the 세금·실수령 card. */
    fun pickTaxWay(mode: com.bamnat.core.pay.TaxMode) {
        container.appScope.launch { com.bamnat.app.tax.TaxWay.pick(container, calendarId, mode) }
    }

    /** [TAX-02] The 세금·실수령 card's numbers, off the main thread. */
    suspend fun takeHome(profile: PayProfile?, snapshot: ScheduleSnapshot?, records: List<com.bamnat.core.tax.PayObservation>, month: YearMonth) =
        withContext(Dispatchers.Default) { com.bamnat.app.tax.TakeHomeView.of(profile, snapshot, records, month) }

    fun savePayRecord(o: com.bamnat.core.tax.PayObservation) {
        container.appScope.launch {
            tax.save(o)
            _undo.tryEmit(UndoMessage("${Fmt.month(o.payMonth)} 실수령을 적었어요", undo = null))
        }
    }

    fun deletePayRecord(o: com.bamnat.core.tax.PayObservation) {
        container.appScope.launch {
            tax.delete(o.id)
            _undo.tryEmit(UndoMessage("${Fmt.month(o.payMonth)} 기록을 지웠어요") { tax.save(o.copy(id = 0)) })
        }
    }

    /** [TAX-01] A deposit taken for pay that was not: back to 부수입, and never taken again. */
    fun notPay(o: com.bamnat.core.tax.PayObservation) {
        val entry = book.value?.entries?.firstOrNull { it.id == o.entryId }
        container.appScope.launch {
            if (entry != null) com.bamnat.app.tax.SalaryWatch.notPay(container, calendarId, entry) else tax.delete(o.id)
            _undo.tryEmit(UndoMessage("월급이 아닌 걸로 돌려놨어요", undo = null))
        }
    }

    fun saveBusiness(p: com.bamnat.core.tax.BusinessTax.Profile) {
        container.appScope.launch { tax.saveSettings { it.copy(business = p) } }
    }

    /** [LEDG-29] The 가계부 counts pay as 지급일 실수령 or as the month's 세전. */
    fun setLedgerTakeHome(on: Boolean) = viewModelScope.launch { scope.pay.update { it.copy(ledgerTakeHome = on) } }

    /** The month with its wage estimate, off the main thread. */
    suspend fun month(book: MoneyBook, style: WorkStyle?, profile: PayProfile?, snapshot: ScheduleSnapshot?, month: YearMonth): MonthMoney =
        withContext(Dispatchers.Default) { book.month(month, payEstimate(style, profile, snapshot, month), today = Today.date()) }

    /**
     * [LEDG-10] The month against the one before, and its year month by month, each with that
     * month's wage estimate. Months still ahead are counted without one.
     */
    suspend fun analysis(book: MoneyBook, style: WorkStyle?, profile: PayProfile?, snapshot: ScheduleSnapshot?,
                         month: YearMonth, today: LocalDate): LedgerAnalysis = withContext(Dispatchers.Default) {
        val now = YearMonth.from(today)
        // [J133-37] 순번 717 (중간 빌드 확인: 오늘 깐 앱인데 '올해 들어온 돈 4천만 원'): no estimate for months before the app was in
        // use (installed, or the first line written, whichever is earlier: a backup brought over keeps its months).
        val since = listOfNotNull(
            container.installedAt?.atZone(Today.zone())?.let { YearMonth.from(it) },
            book.entries.minOfOrNull { it.date }?.let { YearMonth.from(it) },
        ).minOrNull()
        val pays = HashMap<YearMonth, Long?>()
        val at = { m: YearMonth ->
            book.month(m, if (m.isAfter(now) || (since != null && m.isBefore(since))) null else pays.getOrPut(m) { payEstimate(style, profile, snapshot, m) }, today = today)
        }
        val current = at(month)
        // [J133-62] 올해 들어온 돈 is money that came in: this month's pay still to come stays out of it.
        val year = (1..12).map { YearMonth.of(month.year, it) }.map { m -> if (m == now) book.month(m, null, today = today) else at(m) }
        LedgerAnalysis(current, Reports.month(current, at(month.minusMonths(1)), book, today), Reports.year(month.year, year))
    }

    private val _undo = MutableSharedFlow<UndoMessage>(extraBufferCapacity = 4)
    val undo: SharedFlow<UndoMessage> = _undo.asSharedFlow()

    override val ledger: MoneyBook? get() = book.value

    override fun saveEntry(entry: MoneyEntry) {
        val before = book.value
        val old = before?.entries?.firstOrNull { it.id == entry.id && entry.id != 0L }
        // 1.32 [LEDX-18] A line written at a rough rate, fixed to the card's bill: the rate it shows is kept for next time.
        val settled = old?.let { o ->
            com.bamnat.core.money.LedgerSmarts.abroadOf(o)?.takeIf { o.amount != entry.amount && entry.amount > 0 && com.bamnat.core.money.LedgerSmarts.abroadOf(entry) != null }
        }?.let { (cur, amount) ->
            val prefs = container.autoRecordPrefs
            com.bamnat.core.money.LedgerSmarts.learnedRate(amount, entry.amount, prefs.current.abroadFee)?.let { prefs.setRate(cur, it) }
            entry.copy(note = com.bamnat.core.money.LedgerSmarts.settledAbroad(entry.note))
        } ?: entry
        container.appScope.launch {
            money.save(settled)
            // [RUL-04] 1.36 규칙 '가계부에 적으면'.
            com.bamnat.app.logic.LogicRunner.onMoneySaved(container, settled.date)
            // [LEDG-18] The other lines from this place can follow in one tap.
            val same = old?.let { money.samePlace(entry, it.category) }.orEmpty()
            if (same.isNotEmpty()) {
                _undo.tryEmit(UndoMessage("같은 곳 기록 ${same.size}건도 ‘${entry.category}’(으)로 바꿀까요?", actionLabel = "바꾸기") {
                    recategorize(same, entry.category)
                })
            }
            // [LEDG-06] Said once, when this line is the one that pushes a budget over.
            val crossed = before?.let { Budgets.crossedBy(entry, it.budgets, it.entries, Today.date()) }.orEmpty()
            if (crossed.isNotEmpty()) _undo.tryEmit(UndoMessage(overText(crossed), undo = null))
        }
    }

    private fun overText(crossed: List<BudgetLine>): String {
        val names = crossed.map { if (it.isTotal) "이번 달" else it.category }
        val first = crossed.first()
        return if (crossed.size == 1) "${names.single()} 예산을 ${Fmt.won(-first.left)} 넘었어요" else "${names.joinToString("·")} 예산을 넘었어요"
    }

    override fun deleteEntry(entry: MoneyEntry) = delete(listOf(entry))

    /** [LEDG-07] Removes lines, with one undo for all of them. */
    fun delete(list: List<MoneyEntry>) {
        if (list.isEmpty()) return
        container.appScope.launch {
            money.deleteAll(list)
            val text = if (list.size == 1) "‘${list.single().title}’ 기록을 지웠어요" else "기록 ${list.size}건을 지웠어요"
            _undo.tryEmit(UndoMessage(text) { money.saveAll(list) })
        }
    }

    /** [LEDG-07] A copy of a line on today, as typed by hand. */
    fun duplicate(e: MoneyEntry) {
        container.appScope.launch {
            val saved = money.save(Ledger.duplicate(e, Today.date()))
            _undo.tryEmit(UndoMessage("‘${e.title}’ 기록을 오늘로 복사했어요") { money.delete(saved) })
        }
    }

    /** [LEDG-07] Lines moved to [category]; lines whose kind has no such category stay as they are. */
    fun recategorize(list: List<MoneyEntry>, category: String) {
        val b = book.value ?: return
        val moved = list.filter { category in b.categoriesOf(it.kind) && (it.category != category || it.parts.isNotEmpty()) }
        if (moved.isEmpty()) return
        container.appScope.launch {
            // One at a time, so a place moved to another category is learned [LEDG-04].
            moved.forEach { money.save(Ledger.recategorize(it, category)) }
            _undo.tryEmit(UndoMessage("${moved.size}건을 ‘$category’(으)로 옮겼어요") { money.saveAll(moved) })
        }
    }

    fun message(text: String) { _undo.tryEmit(UndoMessage(text, undo = null)) }

    override fun saveJob(job: Job) { container.appScope.launch { money.save(job) } }
    override fun deleteJob(job: Job) {
        container.appScope.launch {
            money.delete(job)
            _undo.tryEmit(UndoMessage("‘${job.title}’ 일감을 지웠어요") { money.save(job) })
        }
    }
    val rules: StateFlow<Map<String, String>> = money.rules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val captures = money.captures.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    override val categoryRules: Map<String, String> get() = rules.value
    override fun frequentAmounts(kind: MoneyKind): List<Long> =
        MoneyStats.frequentAmounts(book.value?.entries.orEmpty(), kind, Today.date())

    fun saveAll(entries: List<MoneyEntry>) {
        container.appScope.launch {
            val saved = entries.map { money.save(it) }
            _undo.tryEmit(UndoMessage("문자 ${saved.size}건을 적었어요") { saved.forEach { money.delete(it) } })
        }
    }
    fun markAllSeen() { container.appScope.launch { money.markAllReviewed() } }

    /** [COMN-04] Lines in 확인 대기함 confirmed, so they count. */
    fun confirm(list: List<MoneyEntry>) { container.appScope.launch { money.confirm(list) } }

    /** [LEDG-22][LEDG-24] How 결제 알림으로 적기 was tuned, and repeats turned down. */
    val autoRecord: StateFlow<AutoRecordPrefs.State> = container.autoRecordPrefs.state
    fun setCaptureFilter(filter: CaptureFilter) = container.autoRecordPrefs.setFilter(filter)

    /** E 1.32 [LEDX-06] The past lines a new rule moves, with one 되돌리기. */
    fun recategorizeAll(list: List<MoneyEntry>, category: String) {
        container.appScope.launch {
            money.saveAll(list.map { com.bamnat.core.money.Ledger.recategorize(it, category) })
            _undo.tryEmit(UndoMessage("지난 거래 ${list.size}건을 ‘$category’(으)로 옮겼어요") { money.saveAll(list) })
        }
    }

    /** [LEDG-21] A rule written or changed on the rules page. */
    fun setRule(merchant: String, category: String) { container.appScope.launch { money.setRule(merchant, category) } }
    fun deleteRule(merchant: String) {
        val old = rules.value[merchant] ?: return
        container.appScope.launch {
            money.deleteRule(merchant)
            _undo.tryEmit(UndoMessage("규칙을 지웠어요") { money.setRule(merchant, old) })
        }
    }

    /** [LEDG-24] A payment seen every month becomes a 반복 지출; 아니에요 is remembered. */
    fun addRepeat(s: RepeatFinder.Suggestion) {
        container.appScope.launch {
            val saved = money.save(s.toFixedCost())
            _undo.tryEmit(UndoMessage("‘${saved.name}’을 반복 지출로 등록했어요") { money.delete(saved) })
        }
    }
    fun notRepeating(s: RepeatFinder.Suggestion) = container.autoRecordPrefs.notRepeating(s.key)
    fun finishCapture(capture: Capture, entry: MoneyEntry) {
        container.appScope.launch { money.save(entry); money.dismissCapture(capture.id) }
    }
    fun dropCapture(capture: Capture) { container.appScope.launch { money.dismissCapture(capture.id) } }
    fun saveFixed(cost: FixedCost) {
        container.appScope.launch {
            money.save(cost)
            // [LEDG-09] One set to write itself catches up at once, so a line due today shows now.
            if (cost.auto) money.catchUpRecurring(Today.date())
        }
    }
    fun deleteFixed(cost: FixedCost) {
        container.appScope.launch {
            money.delete(cost)
            _undo.tryEmit(UndoMessage("‘${cost.name}’ 반복을 지웠어요") { money.save(cost) })
        }
    }
    fun recordFixed(u: Upcoming, today: LocalDate) {
        container.appScope.launch {
            // Paid early is still this period's payment; it is dated today, or the due day when that has passed.
            val saved = money.save(UpcomingPayments.entry(u, if (u.date.isBefore(today)) u.date else today))
            _undo.tryEmit(UndoMessage("‘${u.cost.name}’ ${Fmt.won(u.cost.amount)}을 적었어요") { money.delete(saved) })
        }
    }

    /** [LEDG-08] Saves an account. */
    fun saveAccount(a: MoneyAccount) { container.appScope.launch { money.save(a) } }

    /** [LEDG-08] Removes an account, or puts it away when lines use it; either can be undone. */
    fun removeAccount(a: MoneyAccount) {
        container.appScope.launch {
            val removed = money.remove(a)
            _undo.tryEmit(
                if (removed) UndoMessage("‘${a.name}’을 지웠어요") { money.save(a) }
                else UndoMessage("기록이 있어서 ‘${a.name}’${com.bamnat.core.text.Josa.eunneun(a.name)} 숨겼어요") { money.save(a.copy(hidden = false)) },
            )
        }
    }

    /** [LEDG-06] Saves the month's budget and each category's; 0 removes one. */
    /** 1.37 [LEDH-11][LEDH-13] 봉투 and the money in them. */
    val envelopes: StateFlow<Pair<List<com.bamnat.core.money.Envelope>, List<com.bamnat.core.money.EnvelopeMove>>> =
        money.envelopes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList<com.bamnat.core.money.Envelope>() to emptyList())

    fun saveEnvelope(e: com.bamnat.core.money.Envelope) {
        container.appScope.launch {
            money.saveEnvelope(e)
            _undo.tryEmit(UndoMessage(if (e.id == 0L) "'${e.name}' 봉투를 만들었어요" else "'${e.name}' 봉투를 고쳤어요", undo = null))
        }
    }

    fun deleteEnvelope(e: com.bamnat.core.money.Envelope) {
        container.appScope.launch {
            val (kept, moves) = money.envelopeWithMoves(e.id)
            money.deleteEnvelope(e.id)
            _undo.tryEmit(UndoMessage("'${e.name}' 봉투를 지웠어요") { kept?.let { money.restoreEnvelope(it, moves) } })
        }
    }

    /** Puts [amounts] (envelope id → won, negative takes out) in on [date]; one 되돌리기 takes them all back. */
    fun moveToEnvelopes(amounts: Map<Long, Long>, date: java.time.LocalDate, note: String, entryId: Long? = null) {
        if (amounts.values.all { it == 0L }) return
        container.appScope.launch {
            val ids = amounts.filterValues { it != 0L }.map { (id, a) -> money.moveEnvelope(com.bamnat.core.money.EnvelopeMove(envelopeId = id, date = date, amount = a, note = note, entryId = entryId)) }
            val total = amounts.values.sum()
            _undo.tryEmit(UndoMessage(if (total >= 0) "봉투에 ${Fmt.won(total)} 넣었어요" else "봉투에서 ${Fmt.won(-total)} 뺐어요") { ids.forEach { money.deleteEnvelopeMove(it) } })
        }
    }

    fun saveBudgets(values: Map<String, Long>) {
        container.appScope.launch {
            values.forEach { (c, v) -> money.setBudget(c, v) }
            _undo.tryEmit(UndoMessage("예산을 저장했어요", undo = null))
        }
    }

    /** [LEDG-14] Renames first (taking their lines along), then the order and hidden ones. */
    fun saveCategories(kind: MoneyKind, edits: List<CategoryEdit>, renames: List<Pair<String, String>>) {
        container.appScope.launch {
            renames.forEach { (from, to) -> money.renameCategory(kind, from, to) }
            money.saveCategories(kind, edits)
            _undo.tryEmit(UndoMessage("${kind.label} 분류를 저장했어요", undo = null))
        }
    }

    fun runUndo(message: UndoMessage) { message.undo?.let { u -> container.appScope.launch { u() } } }
    override fun saveSales(sales: DailySales) {
        container.appScope.launch {
            money.save(sales)
            // [TYPE-04] Sales on a day closed by itself open it again, from 돈 as from the calendar.
            com.bamnat.app.ui.work.ShopKeeper.salesSaved(container, calendarId, sales.date, sales.sales)
        }
    }
}

/**
 * [LEDG-01][WORK-04][TIER-05][LEDG-15] The money tab: 요약 (the month, today's budget, what is
 * coming, accounts), 내역 (every line, found and changed in bulk) and 분석 (where it went and how
 * the year goes). Folded it is one column; unfolded each page uses two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoneyScreen(
    vm: MoneyViewModel,
    wide: Boolean,
    onOpenPay: () -> Unit,
    /** 1.37 PC 확인 37446021: 앞으로의 돈의 '월급날 넣기'는 급여 화면의 월급날 칸으로 (설정 찾기와 같은 곳). */
    onOpenPayDay: () -> Unit = onOpenPay,
    snackbar: SnackbarHostState? = null,
    today: LocalDate = Today.date(),
) {
    val bookOrNull by vm.book.collectAsStateWithLifecycle()
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val profile by vm.payProfile.collectAsStateWithLifecycle()
    val gate by vm.gate.collectAsStateWithLifecycle()
    // [NAV-01] The same month as the calendar and 근무·급여.
    val month by vm.shownMonth.collectAsStateWithLifecycle()
    val page by vm.page.collectAsStateWithLifecycle()
    // [TIER-07] 돈 통계 opened from 프로 기능 모아보기 or the help search: the 분석 page.
    com.bamnat.app.ui.guide.FocusedTool("report", "money.stats") { vm.showPage(MoneyViewModel.Page.REPORT) }
    val view by vm.view.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    // [NAV-02] 1.25 Z (U4): back from 내역 or 분석 is 요약 of this tab, not the calendar (choosing in 내역 takes back first).
    androidx.activity.compose.BackHandler(enabled = page != MoneyViewModel.Page.SUMMARY && selection.isEmpty()) {
        vm.showPage(MoneyViewModel.Page.SUMMARY)
    }
    var adding by remember { mutableStateOf<MoneyKind?>(null) }
    var addRound by remember { mutableIntStateOf(0) }
    var editing by remember { mutableStateOf<MoneyEntry?>(null) }
    var editingJob by remember { mutableStateOf<Job?>(null) }
    var pasting by remember { mutableStateOf(false) }
    var firstFilling by remember { mutableStateOf(false) }
    var pickingChallenge by rememberSaveable { mutableStateOf(false) }
    var finishing by remember { mutableStateOf<Capture?>(null) }
    var editingFixed by remember { mutableStateOf<FixedCost?>(null) }
    var addingFixed by remember { mutableStateOf(false) }
    var budgeting by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<MoneyAccount?>(null) }
    var addingAccount by remember { mutableStateOf<AccountType?>(null) }
    var addingAnyAccount by remember { mutableStateOf(false) }
    var editingCategories by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var autoRecording by rememberSaveable { mutableStateOf(false) }
    var editingRules by rememberSaveable { mutableStateOf(false) }
    var filtering by rememberSaveable { mutableStateOf(false) }
    // [LEDG-16] 돈 적기 shrinks to its icon while the page scrolls down and comes back on the way up.
    var fabExpanded by rememberSaveable { mutableStateOf(true) }
    val fabScroll = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (available.y < -1f) fabExpanded = false else if (available.y > 1f) fabExpanded = true
                return Offset.Zero
            }
        }
    }
    LaunchedEffect(page) { fabExpanded = true }
    // [LEDG-16] Settings' 결제 알림 읽기 lands here with 자동 기록 open.
    val noticeTarget by NoticeLinks.target.collectAsStateWithLifecycle()
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.AUTO_RECORD) return@LaunchedEffect
        NoticeLinks.consume(NoticeLinks.Target.AUTO_RECORD)
        vm.showPage(MoneyViewModel.Page.SUMMARY)
        autoRecording = true
    }
    var fromOutside by remember { mutableStateOf(false) }
    // 1.37 [LEDH-14] 돈 적기 from the widget or the quick settings tile: the add sheet straight away.
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.MONEY_ADD) return@LaunchedEffect
        NoticeLinks.consume(NoticeLinks.Target.MONEY_ADD)
        vm.showMonth(YearMonth.from(com.bamnat.core.time.Today.date()))
        adding = MoneyKind.EXPENSE
        fromOutside = true
    }
    // bc7e2d43 PC 확인 LOW-8 (빠른 설정 '돈 적기'를 닫으면 설정 앱이 아니라 밤낮 가계부가 남음): opened from the tile or the widget,
    // closing the sheet goes back to where it was opened.
    val activity = androidx.activity.compose.LocalActivity.current
    LaunchedEffect(adding) {
        if (adding == null && fromOutside) { fromOutside = false; activity?.moveTaskToBack(true) }
    }
    // [TAX-01] What came in since the last look is checked for pay, and 알림 '세금 카드' lands on the sheet.
    LaunchedEffect(vm) { vm.checkSalary() }
    var takingHome by rememberSaveable { mutableStateOf(false) }
    var bizTax by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(noticeTarget) {
        if (noticeTarget != NoticeLinks.Target.TAX) return@LaunchedEffect
        NoticeLinks.consume(NoticeLinks.Target.TAX)
        vm.showPage(MoneyViewModel.Page.SUMMARY)
        if (com.bamnat.app.ui.pro.tryFeature(com.bamnat.app.tier.Feature.TAX_FORECAST)) { if (taxSide(settings.workStyle) == TaxSide.BUSINESS) bizTax = true else takingHome = true }
    }
    // [TAX-02] 세금·실수령 opened from the help search or the update notice: the summary, where the card is.
    val featureFocus by com.bamnat.app.ui.guide.FeatureLinks.focus.collectAsStateWithLifecycle()
    LaunchedEffect(featureFocus) {
        if (featureFocus == "tax" && com.bamnat.app.ui.guide.FeatureLinks.take("tax")) vm.showPage(MoneyViewModel.Page.SUMMARY)
    }
    val payRecords by vm.payRecords.collectAsStateWithLifecycle()
    val taxSettings by vm.taxSettings.collectAsStateWithLifecycle()
    val rules by vm.rules.collectAsStateWithLifecycle()
    val captures by vm.captures.collectAsStateWithLifecycle()
    val autoRecord by vm.autoRecord.collectAsStateWithLifecycle()
    val book = bookOrNull ?: MoneyBook.EMPTY
    val style = settings.workStyle
    // Records show at once; the wage estimate joins when the pay engine has run.
    // [UX-10] 1.25 Z (U7): opened again, the month shows with its wage estimate from the first frame, not without it and then with.
    val monthKey = "money:${vm.calendarId}:$month"
    val lastMonth by com.bamnat.app.ui.rememberLast(monthKey, book, style, profile, snapshot, month) { vm.month(book, style, profile, snapshot, month) }
    val m = lastMonth ?: book.month(month, today = today)
    val analysis by com.bamnat.app.ui.rememberLast("money.report:${vm.calendarId}:$month", book, style, profile, snapshot, month,
        page == MoneyViewModel.Page.REPORT) {
        if (page == MoneyViewModel.Page.REPORT) vm.analysis(book, style, profile, snapshot, month, today)
        else com.bamnat.app.ui.LastValues.value("money.report:${vm.calendarId}:$month") as LedgerAnalysis?
    }
    val side = taxSide(style)
    val takeHome by produceState<com.bamnat.app.tax.TakeHomeView?>(null, profile, snapshot, payRecords, month, side) {
        value = if (side == TaxSide.PAY) vm.takeHome(profile, snapshot, payRecords, month) else null
    }
    val business = remember(book, taxSettings, side, month, today) {
        if (side != TaxSide.BUSINESS) null
        else com.bamnat.core.tax.BusinessTax.year(month.year, today, taxSettings.business, book.jobs, book.sales, book.entries)
    }
    LaunchedEffect(snackbar) {
        val host = snackbar ?: return@LaunchedEffect
        vm.undo.collect { msg ->
            val action = msg.actionLabel.takeIf { msg.undo != null }
            if (host.showSnackbar(msg.message, actionLabel = action, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                == SnackbarResult.ActionPerformed) {
                vm.runUndo(msg)
            }
        }
    }
    val owed = remember(book, today) { book.owed(today) }
    // [COMN-04] With 확인하고 적기 on, the unconfirmed lines are in book.pending, out of every total.
    val unseen = remember(book) { book.pending + book.entries.filter { !it.reviewed } }
    val otherPlaces = remember(book, today) { OtherPlaces.groups(book.entries, today) }
    // E 1.32 [LEDX-05] Learned from this calendar's own lines, on the phone.
    // 1.32 [LEDX-21] With what each day was on this calendar, as the notification reader learns.
    val learner = remember(book.entries, snapshot) {
        com.bamnat.core.money.LedgerSmarts.Learner.of(book.entries, snapshot?.takeIf { !it.isEmpty }?.let { com.bamnat.core.tax.TaxPlus.dayLabel(it.resolver::resolve) } ?: { null })
    }
    val repeats = remember(book, today, autoRecord.notRepeating) {
        RepeatFinder.find(book.entries, book.fixed, today, autoRecord.notRepeating)
    }
    val upcoming = remember(book, today) { UpcomingPayments.within(book.fixed, book.entries, today, accounts = book.accounts) }
    val leftFixed = remember(book, today) { UpcomingPayments.leftThisMonth(book.fixed, book.entries, today) }
    // 1.37 [LEDH-01][LEDH-07] Pay days ahead and take-home per hour from the shifts on the calendar.
    val aheadPay by produceState<AheadPay?>(null, style, profile, snapshot, today) {
        value = vm.aheadPay(style, profile, snapshot, today, today.plusDays(com.bamnat.core.money.MoneyAhead.FULL_DAYS.toLong()))
    }
    // 1.37 [LEDH-11] 봉투.
    val envelopeBook by vm.envelopes.collectAsStateWithLifecycle()
    val envelopeActions = remember(vm) {
        object : EnvelopeActions {
            override fun save(e: com.bamnat.core.money.Envelope) = vm.saveEnvelope(e)
            override fun delete(e: com.bamnat.core.money.Envelope) = vm.deleteEnvelope(e)
            override fun move(amounts: Map<Long, Long>, date: java.time.LocalDate, note: String, entryId: Long?) = vm.moveToEnvelopes(amounts, date, note, entryId)
        }
    }
    // 1.37 [LEDH-06] 급여일 주기: this month's budget counted from the last pay day to the day before the next.
    val cycle = remember(settings.moneyByPayCycle, profile, today, month) {
        if (!settings.moneyByPayCycle || month != YearMonth.from(today)) null
        else profile?.period?.let { p -> com.bamnat.core.money.PayCycle.around(today, (-2L..2L).mapNotNull { p.payDate(YearMonth.from(today).plusMonths(it)) }) }
    }
    // 1.37 [LEDH-34] 예산 넘김: chosen categories carry last stretch's leftover (or overspend).
    val rolling = remember(settings.budgetRollover, settings.workStyle) {
        if (settings.workStyle == com.bamnat.core.work.WorkStyle.INVEST) emptySet() else settings.budgetRollover.split('\t').filter { it.isNotBlank() }.toSet()
    }
    val budget = remember(book, month, today, leftFixed, cycle, rolling) {
        val from = cycle?.from ?: month.atDay(1)
        val to = cycle?.to ?: month.atEndOfMonth()
        val carry = Budgets.carry(book.budgets, book.entries, from, to, rolling)
        cycle?.let { c -> Budgets.statusIn(book.budgets, book.entries, c.from, c.to, today, UpcomingPayments.leftUntil(book.fixed, book.entries, today, c.to), carry) }
            ?: Budgets.status(book.budgets, book.entries, month, today, leftFixed, carry)
    }
    val recent = remember(book, month) { book.entries.filter { YearMonth.from(it.date) == month }.take(5) }
    val listActions = remember(vm) {
        object : LedgerListActions {
            override fun open(e: MoneyEntry) { editing = e }
            override fun delete(list: List<MoneyEntry>) = vm.delete(list)
            override fun duplicate(e: MoneyEntry) = vm.duplicate(e)
            override fun recategorize(list: List<MoneyEntry>, category: String) = vm.recategorize(list, category)
            override fun message(text: String) = vm.message(text)
        }
    }

    // [DAILY-15][DAILY-04] Each card of 요약 by its id; 화면 꾸미기 orders them and turns them off.
    // 1.37 [LEDH-21] Which parts are on, so a card can leave out what another card on the page already says.
    // [FIT-01][FIT-02] The parts that suit this calendar's kind of work, and those the user added.
    val fitAdded = com.bamnat.app.ui.guide.rememberFitAdded(vm.calendarId)
    val shownPartsNow = moneyParts(settings.moneyLayout).filter { it.shown }.map { it.part }
        .filter { com.bamnat.core.guide.FitTable.visible(com.bamnat.core.guide.FitSurface.MONEY_PARTS, it.id, style, fitAdded) }
    // fc374a23 PC 확인: 앞으로의 돈 says 오늘 쓸 돈 from the accounts when it can; the summary then keeps one such number.
    val aheadSaysToday = remember(book, today, aheadPay, style, shownPartsNow) {
        MoneyPart.AHEAD in shownPartsNow && runCatching {
            val spent = book.entries.filter { it.date == today && it.kind == com.bamnat.core.money.MoneyKind.EXPENSE }.sumOf { it.amount }
            com.bamnat.core.money.MoneyAhead.forecast(book, today, com.bamnat.core.money.MoneyAhead.FULL_DAYS, aheadPay?.days.orEmpty())
                .untilNext(nextKinds(style), spent)?.todayLeft != null
        }.getOrDefault(false)
    }
    val cards: Map<MoneyPart, @Composable () -> Unit> = mapOf(
        MoneyPart.HERO to { HeroCard(m, budget, today, onBudget = { budgeting = true }, aheadSaysToday = aheadSaysToday) },
        MoneyPart.NEW to {
            NewRecordsCard(unseen, captures, onOpen = { editing = it }, onAllSeen = vm::markAllSeen,
                onFinish = { finishing = it }, onDrop = vm::dropCapture,
                waiting = autoRecord.confirmFirst, onConfirm = vm::confirm, onDelete = vm::delete,
                guesses = { e -> learner.guess(e).filter { it.category != "기타" } }, onPick = { e, c -> vm.recategorize(listOf(e), c) })
        },
        MoneyPart.AHEAD to {
            AheadCard(book, style, today, aheadPay, budget, toSort = unseen.size, gate = gate,
                onAddAccount = { addingAccount = com.bamnat.core.money.AccountType.BANK }, onOpenPay = onOpenPayDay, snapshot = snapshot)
        },
        // 1.37 [LEDH-32] 복귀 반김 first (a week or more without a line), then 지난달 돈 이야기.
        MoneyPart.STORY to {
            Column(verticalArrangement = Arrangement.spacedBy(com.bamnat.app.ui.theme.LocalSpacing.current.card)) {
                WelcomeBackCard(book, today, onAdd = { adding = MoneyKind.EXPENSE })
                // 1.38 [LEDH-29] 첫날 채우기.
                FirstFillCard(book, vm.calendarId, onFill = { firstFilling = true; pasting = true })
                // 1.38 [LEDH-31] 혼자 하는 챌린지 (⋮ › 도전 시작하기).
                val run = remember(settings.moneyChallenge) { com.bamnat.core.money.Challenges.decode(settings.moneyChallenge) }
                val nightOf = snapshot?.let { snap -> { d: LocalDate -> com.bamnat.core.schedule.DutyCheck.isNight(runCatching { snap.resolver.resolve(d).shiftType }.getOrNull()) } }
                ChallengeCard(run, book, envelopeBook.second, today, nightOf.takeIf { style == com.bamnat.core.work.WorkStyle.SHIFT }, pickingChallenge,
                    onStart = { k -> vm.updateSettings { it.copy(moneyChallenge = com.bamnat.core.money.Challenges.encode(com.bamnat.core.money.Challenges.Run(k, today))) }; pickingChallenge = false },
                    onStop = { vm.updateSettings { it.copy(moneyChallenge = "") } }, onClosePicker = { pickingChallenge = false })
                MonthStoryCard(book, envelopeBook.second, aheadPay?.lastMonth, today, gate)
            }
        },
        MoneyPart.ENVELOPES to {
            val (envs, moves) = envelopeBook
            EnvelopesCard(book, envs, moves, style, today, aheadPay?.perShift, pro = !gate.live || gate.tier >= com.bamnat.app.tier.Tier.PRO,
                actions = envelopeActions)
        },
        MoneyPart.RECENT to { RecentCard(recent, month, onOpen = { editing = it }, onAll = { vm.setView(LedgerView()); vm.showPage(MoneyViewModel.Page.LIST) }) },
        MoneyPart.INCOME to {
            // F 1.32 [TAXP-01] 세전·세후 on one line: 명세서 > 입금 > 직접 적음 > 추정.
            val net = remember(takeHome, payRecords, month) { takeHome?.let { v -> TaxPlus.netLine(month, payRecords, v.gross, v.forecast) } }
            // 1.37 [LEDH-21] 겹침 합치기: 세전·세후 is said once, in 세금·실수령, when that card is on and open.
            val taxSays = side == TaxSide.PAY && MoneyPart.TAX in shownPartsNow && !gate.locked(com.bamnat.app.tier.Feature.TAX_FORECAST)
            IncomeCard(m, style, profile, onOpenPay = onOpenPay, onTakeHome = vm::setLedgerTakeHome, netLine = net.takeIf { !taxSays }, gate = gate, taxSays = taxSays)
        },
        // [TAX-02][TAX-05] 세전·세후 and this year's tax (프로).
        MoneyPart.TAX to { side?.let { s -> TaxCard(s, takeHome, business, gate, onOpen = { if (s == TaxSide.PAY) takingHome = true else bizTax = true },
            onTaxWay = vm::pickTaxWay, ledgerNet = profile?.ledgerTakeHome?.takeIf { s == TaxSide.PAY && !gate.locked(com.bamnat.app.tier.Feature.TAX_FORECAST) },
            onLedgerNet = vm::setLedgerTakeHome) } },
        // [RECV-02] Money owed: receivables and unpaid jobs together, the whole list one tap away.
        MoneyPart.OWED to {
            OwedCard(owed, today, onOpenAll = { com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.OWED) }) { o ->
                when (o) {
                    is com.bamnat.core.money.Owed.FromJob -> editingJob = o.job
                    is com.bamnat.core.money.Owed.Written -> com.bamnat.app.NoticeLinks.open(com.bamnat.app.NoticeLinks.Target.OWED)
                }
            }
        },
        // [FLD-06][SHOP-02] A deposit that may be a job's money; card deposits still to come.
        MoneyPart.WORK to { WorkMoneyCards(vm.calendarId, book, style, today) },
        // [COMN-02] With two calendars or more, all of them added up.
        MoneyPart.ALL to { AllCalendarsCard(month) },
        MoneyPart.BUDGET to { BudgetCard(budget, today, onEdit = { budgeting = true }) },
        // [GUIDE-05] Suggestions that can be turned off for the calendar.
        MoneyPart.OTHER to { com.bamnat.app.ui.guide.FeatureCard("money.otherPlaces", vm.calendarId) { OtherPlacesCard(otherPlaces, book.categoriesOf(MoneyKind.EXPENSE), onPick = { g, c -> vm.recategorize(g.entries, c) }) } },
        MoneyPart.UPCOMING to {
            UpcomingCard(upcoming, book.fixed, leftFixed, onRecord = { vm.recordFixed(it, today) },
                onEdit = { c -> if (c == null) addingFixed = true else editingFixed = c }, style = style)
        },
        MoneyPart.REPEAT to { com.bamnat.app.ui.guide.FeatureCard("money.repeat", vm.calendarId) { RepeatSuggestCard(repeats, onAdd = vm::addRepeat, onNo = vm::notRepeating) } },
        MoneyPart.ACCOUNTS to { AccountsCard(book, today, onAdd = { t -> if (t == null) addingAnyAccount = true else addingAccount = t }, onEdit = { editingAccount = it },
            onRepay = { lines -> lines.forEach(vm::saveEntry) }) },
        // [LEDG-02] Ways to type less.
        MoneyPart.AUTO to { AutoRecordCard(onPaste = { pasting = true }) },
        // 1.37 [LEDH-21] 오늘 쓸 돈 is said by 앞으로의 돈 and the 할부 left by 할부 청구 기준 when they are on; the graph is in 통계.
        MoneyPart.PACE to { PaceCard(book, today, budget, sayToday = MoneyPart.AHEAD !in shownPartsNow, sayInstallments = MoneyPart.INSTALL !in shownPartsNow,
            onGraph = { vm.showPage(MoneyViewModel.Page.REPORT) }) },
        MoneyPart.FOUND to { FoundMoneyCard(book.entries, today, autoRecord.lookedAt, onOpen = vm::showPlace) },
        MoneyPart.SUBS to { SubscriptionsCard(book.entries, today, autoRecord.lookedAt, onStillUsing = vm::stillUsing, onOpen = vm::showPlace) },
        MoneyPart.CARD25 to {
            val gross by androidx.compose.runtime.produceState<Long?>(null, profile, snapshot, today.year) { value = vm.yearGross(style, profile, snapshot, today.year) }
            CardGaugeCard(book, today, gross)
        },
        MoneyPart.CARDBILL to { CardBillCard(book, today, gate) },
        MoneyPart.ASKME to { AskLedgerCard(book, today) },
        MoneyPart.ASK to { LedgerAsksCard(book, today, vm) },
        MoneyPart.INSTALL to { InstallmentPlanCard(book, today) },
        MoneyPart.SHIFTSPEND to {
            ShiftSpendCard(book, snapshot, today, gate, onCategory = vm::showCategory,
                challenge = remember(settings.moneyChallenge) { com.bamnat.core.money.Challenges.decode(settings.moneyChallenge) },
                onChallenge = { k ->
                    vm.updateSettings { it.copy(moneyChallenge = com.bamnat.core.money.Challenges.encode(com.bamnat.core.money.Challenges.Run(k, today))) }
                    vm.showPage(MoneyViewModel.Page.SUMMARY)
                })
        },
        MoneyPart.SALESSPLIT to { SalesSplitCard(book, month) },
        MoneyPart.FREELANCE to { FreelanceCard(vm.calendarId, book, today, business, gate) },
    )
    val shownParts = shownPartsNow
    // 1.37 [LEDH-15] With 정리함 on, its four parts live inside it (in the user's order and on/off) instead of apart.
    val inboxParts = listOf(MoneyPart.NEW, MoneyPart.ASK, MoneyPart.OTHER, MoneyPart.REPEAT)
    val inboxOn = MoneyPart.INBOX in shownParts
    val placed = if (inboxOn) shownParts.filter { it !in inboxParts } else shownParts
    val askCount = remember(book, today, autoRecord.askedCards) {
        book.entries.count { com.bamnat.core.money.LedgerChecks.isUnmatchedRefund(it) } +
            com.bamnat.core.money.LedgerChecks.unknownCards(book.entries, book.accounts, today.minusDays(90)).count { it.last4 !in autoRecord.askedCards }.coerceAtMost(2)
    }
    val inboxCount = (if (MoneyPart.NEW in shownParts) unseen.size + captures.size else 0) + (if (MoneyPart.ASK in shownParts) askCount else 0) +
        (if (MoneyPart.OTHER in shownParts) otherPlaces.size else 0) + (if (MoneyPart.REPEAT in shownParts) repeats.size else 0)
    val inboxCard: @Composable () -> Unit = {
        if (inboxCount > 0) com.bamnat.app.ui.components.SectionCard(title = "정리함 $inboxCount") {
            androidx.compose.runtime.CompositionLocalProvider(com.bamnat.app.ui.components.LocalFlatSections provides true) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    shownParts.filter { it in inboxParts }.forEach { cards.getValue(it)() }
                }
            }
        }
    }
    var arranging by rememberSaveable { mutableStateOf(false) }
    // Unfolded: the overview on the left, the month's lines on the right, each side in the user's order.
    @Composable fun draw(p: MoneyPart) { if (p == MoneyPart.INBOX) inboxCard() else cards.getValue(p)() }
    // 1.37 [LEDH-20] 요약 세 층: 오늘 / 이번 달 / 앞으로, and 점검 folded until opened.
    val gap = com.bamnat.app.ui.theme.LocalSpacing.current.card
    val onCheck: (Boolean) -> Unit = { open -> vm.updateSettings { it.copy(moneyCheckOpen = open) } }
    val left: @Composable () -> Unit = { MoneyLayered(placed.filter { !wide || !it.right }, gap, settings.moneyCheckOpen, onCheck) { draw(it) } }
    val right: @Composable () -> Unit = { if (wide) MoneyLayered(placed.filter { it.right }, gap, settings.moneyCheckOpen, onCheck) { draw(it) } }

    // 1.37 [LEDH-07] 근무 시간 환산 for the lines and the 돈 적기 sheet of this tab.
    val workTime = remember(style, settings.workTimeFrom, book, today, aheadPay) { WorkTimeSay.of(style, settings.workTimeFrom, book, today, aheadPay?.hourly) }
    androidx.compose.runtime.CompositionLocalProvider(LocalWorkTime provides workTime) {
    Box(Modifier.fillMaxSize().nestedScroll(fabScroll)) {
        Column(Modifier.fillMaxSize()) {
            // At very large text the tab has no room for the 프로 mark (the cards inside still carry it),
            // and the title gives its room to the month: the 가계부 tab below already says where this is.
            val roomy = LocalDensity.current.fontScale < 1.3f
            Row(Modifier.padding(top = 12.dp).then(com.bamnat.app.ui.theme.PhotoGround.head()).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (roomy) {
                    FitText(com.bamnat.core.work.moneyTab(style), MaterialTheme.typography.headlineSmall, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f).semantics { heading() })
                } else {
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { vm.showMonth(month.minusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "이전 달")
                }
                com.bamnat.app.ui.calendar.MonthJumpTitle(month, { vm.showMonth(it) }) // 1.32 [WTYP-21]
                IconButton(onClick = { vm.showMonth(month.plusMonths(1)) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "다음 달")
                }
                // 1.37 [LEDH-22] 금액 가리기: one tap, the whole app (and the widgets) shows dots for amounts.
                val eyeContext = LocalContext.current
                val flipHide = {
                    vm.updateSettings { it.copy(hideAmounts = !it.hideAmounts) }
                    eyeContext.appContainer.appScope.launch { com.bamnat.app.widget.WidgetRefresher.refresh(eyeContext) }
                    Unit
                }
                // At very large text the eye waits in ⋮ (금액 가리기) so the month keeps its room.
                if (roomy) IconButton(onClick = flipHide) {
                    Icon(if (settings.hideAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                        if (settings.hideAmounts) "금액 보이기" else "금액 가리기")
                }
                // [LEDG-16] What is set once and changed rarely, always in the same place.
                Box {
                    com.bamnat.app.ui.settings.GlobalSearchButton() // U 1.32 [UX132-04]
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "가계부 탭 메뉴") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("자동 기록") }, onClick = { menuOpen = false; autoRecording = true })
                        DropdownMenuItem(text = { Text("결제 문자 붙여넣기") }, onClick = { menuOpen = false; pasting = true })
                        // 1.38 [LEDH-31]
                        DropdownMenuItem(text = { Text("도전 시작하기") }, onClick = { menuOpen = false; pickingChallenge = true; vm.showPage(MoneyViewModel.Page.SUMMARY) })
                        DropdownMenuItem(text = { Text("분류 편집") }, onClick = { menuOpen = false; editingCategories = true })
                        // [DAILY-15] 화면 꾸미기, as on 달력 and 근무·급여.
                        DropdownMenuItem(text = { Text("카드 배치") }, onClick = { menuOpen = false; com.bamnat.app.ui.settings.SettingsLinks.go(com.bamnat.app.ui.settings.SettingsGo.STUDIO_MONEY) })
                        DropdownMenuItem(text = { Text(if (settings.hideAmounts) "금액 보이기" else "금액 가리기") }, onClick = { menuOpen = false; flipHide() })
                    }
                }
                com.bamnat.app.ui.MeButton() // [NAV2-02]
            }
            // [MCAL-06] Whose money this is, once there are several calendars.
            com.bamnat.app.ui.calendar.CalendarScopeChips(LocalContext.current.appContainer, Modifier.padding(horizontal = 16.dp))
            // [RUL-03] 1.36 사용자 2026-11-01 "가계부와 연동": 가계부 화면에 놓은 단추.
            com.bamnat.app.logic.LogicButtons(com.bamnat.core.formula.ButtonPlace.MONEY, com.bamnat.core.time.Today.date(), Modifier.padding(horizontal = 16.dp))
            PrimaryTabRow(selectedTabIndex = page.ordinal, containerColor = com.bamnat.app.ui.theme.PhotoGround.solid(MaterialTheme.colorScheme.surface)) {
                MoneyViewModel.Page.entries.forEach { p ->
                    Tab(
                        selected = page == p, onClick = { vm.showPage(p); if (p != MoneyViewModel.Page.LIST) vm.select(emptySet()) },
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(p.label, maxLines = 1)
                                if (p == MoneyViewModel.Page.REPORT && roomy) { Spacer(Modifier.width(6.dp)); ProPill() }
                            }
                        },
                    )
                }
            }
            when (page) {
                MoneyViewModel.Page.SUMMARY -> if (wide) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Column(Modifier.weight(1f).fillMaxHeight().monthSwipe(month) { vm.showMonth(month.plusMonths(it.toLong())) }.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(com.bamnat.app.ui.theme.LocalSpacing.current.card)) { left(); Spacer(Modifier.height(FabClearance)) }
                        Column(Modifier.weight(1f).fillMaxHeight().monthSwipe(month) { vm.showMonth(month.plusMonths(it.toLong())) }.verticalScroll(rememberScrollState()).padding(vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(com.bamnat.app.ui.theme.LocalSpacing.current.card)) { right(); Spacer(Modifier.height(FabClearance)) }
                    }
                } else {
                    Column(Modifier.fillMaxSize().monthSwipe(month) { vm.showMonth(month.plusMonths(it.toLong())) }.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(com.bamnat.app.ui.theme.LocalSpacing.current.card)) {
                        MoneyLayered(placed, gap, settings.moneyCheckOpen, onCheck) { draw(it) }
                        Spacer(Modifier.height(FabClearance))
                    }
                }
                // 1.39 [CALX-08] 내역·통계도 옆으로 밀어 달을 넘겨요 (사용자 11-08 "돈을 보여 주는 곳 모두"); 내역 줄의 밀기와 고르는 중에는 그쪽이 먼저.
                MoneyViewModel.Page.LIST -> Box(Modifier.fillMaxSize().then(if (selection.isEmpty()) Modifier.monthSwipe(month) { vm.showMonth(month.plusMonths(it.toLong())) } else Modifier)) {
                    LedgerListPane(book, month, view, vm::setView, selection, vm::select, listActions, wide,
                        onAdd = { adding = MoneyKind.EXPENSE })
                }
                MoneyViewModel.Page.REPORT -> Box(Modifier.fillMaxSize().monthSwipe(month) { vm.showMonth(month.plusMonths(it.toLong())) }) {
                    LedgerReportsPane(analysis, book, budget.total?.budget, style, gate, wide, today,
                        onCategory = vm::showCategory, onOpen = { editing = it }, onPlace = vm::showPlace, month = month,
                        snapshot = snapshot, onMessage = vm::message, budgetLines = budget.categories)
                }
            }
        }
        // PC 4277abca (통계에서 '돈 적기'가 카페 줄 금액·카드 머리를 덮음): 통계 is for reading, so the floating button is not over it;
        // 돈 적기 stays on 요약 and 내역.
        // 1.37 PC 4차: 되돌리기 줄이 '돈 적기'를 덮어, 줄이 사라질 때 누른 손이 돈 적기를 열었어요. 줄이 있는 동안과 사라진 뒤 잠깐은 숨겨요.
        val barUp = snackbar?.currentSnackbarData != null
        var fabBack by remember { mutableStateOf(true) }
        LaunchedEffect(barUp) { if (barUp) fabBack = false else { kotlinx.coroutines.delay(1_200); fabBack = true } }
        AnimatedVisibility(selection.isEmpty() && page != MoneyViewModel.Page.REPORT && !barUp && fabBack, enter = fadeIn() + scaleIn(), exit = fadeOut() + scaleOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)) {
            ExtendedFloatingActionButton(
                onClick = { adding = MoneyKind.EXPENSE },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("돈 적기") },
                expanded = fabExpanded,
                modifier = Modifier.semantics { contentDescription = "돈 적기" },
            )
        }
    }

    // 1.39 [FLOW-30] 근무 기준 하루: a coffee bought at 02:10 in last night's shift is put on that night's day, as is the small hours of
    // a day with no work before 하루 시작 시각 (기본 04:00). The date row on the sheet still changes it.
    val addDate = if (YearMonth.from(today) == month) {
        val at = com.bamnat.core.time.Today.dateTime()
        snapshot?.let { s -> com.bamnat.core.design.DayBoundary.dayOf(at, s.resolver::resolve, com.bamnat.core.design.DayBoundary.startAt(settings.dayStartMinutes)) }
            ?.takeIf { at.toLocalDate() == today } ?: today
    } else month.atEndOfMonth()
    adding?.let { k ->
        // A new round for 저장하고 하나 더, so the sheet starts empty again.
        key(addRound) {
            EntryDialog(addDate, null, k, onSave = { vm.saveEntry(it); adding = null }, onDismiss = { adding = null },
                frequent = vm::frequentAmounts, rules = rules, book = book,
                onSaveAndNext = { vm.saveEntry(it); addRound++ }, onEditCategories = { editingCategories = true })
        }
    }
    editing?.let { e ->
        EntryDialog(e.date, e, e.kind, onSave = { vm.saveEntry(it); editing = null },
            onDelete = { vm.delete(listOf(it)); editing = null }, onDismiss = { editing = null },
            frequent = vm::frequentAmounts, rules = rules, book = book,
            onDuplicate = { vm.duplicate(it); editing = null }, onEditCategories = { editingCategories = true })
    }
    finishing?.let { c ->
        val draft = remember(c) { c.draft() }
        EntryDialog(draft.date, draft, draft.kind, onSave = { vm.finishCapture(c, it); finishing = null },
            onDismiss = { finishing = null }, frequent = vm::frequentAmounts, rules = rules, book = book)
    }
    if (autoRecording) AutoRecordDialog(onPaste = { pasting = true }, onDismiss = { autoRecording = false },
        onRules = { autoRecording = false; editingRules = true }, onFilter = { autoRecording = false; filtering = true })
    if (editingRules) CategoryRulesDialog(rules, book.categoriesOf(MoneyKind.EXPENSE), onSave = vm::setRule, onDelete = vm::deleteRule,
        onDismiss = { editingRules = false }, entries = book.entries, onApplyPast = vm::recategorizeAll)
    if (filtering) CaptureFilterDialog(autoRecord, onChange = vm::setCaptureFilter, onDismiss = { filtering = false })
    if (pasting) PasteDialog(book.entries, rules, onSave = { vm.saveAll(it); pasting = false; firstFilling = false },
        onDismiss = { pasting = false; firstFilling = false }, today = today, firstFill = firstFilling)
    if (addingFixed || editingFixed != null) {
        FixedCostDialog(editingFixed, onSave = { vm.saveFixed(it); addingFixed = false; editingFixed = null },
            onDelete = { vm.deleteFixed(it); editingFixed = null },
            onDismiss = { addingFixed = false; editingFixed = null }, book = book, today = today)
    }
    editingJob?.let { j ->
        JobDialog(j.date, j, onSave = { vm.saveJob(it); editingJob = null },
            onDelete = { vm.deleteJob(it); editingJob = null }, onDismiss = { editingJob = null })
    }
    if (budgeting) BudgetEditor(book, month, onSave = { vm.saveBudgets(it); budgeting = false }, onDismiss = { budgeting = false },
        byPayCycle = settings.moneyByPayCycle.takeIf { profile?.period?.payDay != null && style?.usesPayEngine != false },
        payDayHint = profile?.period?.payDay == null && style?.usesPayEngine != false,
        onPayCycle = { on -> vm.updateSettings { it.copy(moneyByPayCycle = on) } },
        rolling = rolling, onRolling = { set -> vm.updateSettings { it.copy(budgetRollover = set.joinToString("\t")) } },
        canRoll = style != com.bamnat.core.work.WorkStyle.INVEST)
    if (editingAccount != null || addingAccount != null || addingAnyAccount) {
        AccountEditor(editingAccount, addingAccount, book,
            onSave = { vm.saveAccount(it); editingAccount = null; addingAccount = null; addingAnyAccount = false },
            onRemove = { vm.removeAccount(it); editingAccount = null },
            onDismiss = { editingAccount = null; addingAccount = null; addingAnyAccount = false })
    }
    if (takingHome) takeHome?.let { v ->
        TakeHomeSheet(v, onAdd = vm::savePayRecord, onDelete = vm::deletePayRecord, onNotPay = vm::notPay,
            onDismiss = { takingHome = false }, today = today, profile = profile, gate = gate)
    }
    if (bizTax) BusinessTaxSheet(taxSettings.business,
        yearOf = { p -> com.bamnat.core.tax.BusinessTax.year(month.year, today, p, book.jobs, book.sales, book.entries) },
        onSave = { vm.saveBusiness(it); bizTax = false }, onDismiss = { bizTax = false })
    if (arranging) MoneyEditSheet(settings.moneyLayout, vm::updateSettings, onDismiss = { arranging = false }, style = style, calendar = vm.calendarId)
    if (editingCategories) {
        CategoryEditor(book, onSave = { k, edits, renames -> vm.saveCategories(k, edits, renames); editingCategories = false },
            onDismiss = { editingCategories = false }, startKind = editing?.kind ?: adding ?: MoneyKind.EXPENSE)
    }
    }
}

/** [LEDG-07] The month's latest lines, with the whole list a tap away. */
@Composable
private fun RecentCard(recent: List<MoneyEntry>, month: YearMonth, onOpen: (MoneyEntry) -> Unit, onAll: () -> Unit) {
    SectionCard(title = "${month.monthValue}월 최근 기록", trailing = { TextButton(onClick = onAll) { Text("내역 모두 보기") } }) {
        if (recent.isEmpty()) {
            Text("아래 ‘돈 적기’나 달력의 날짜에서 지출과 수입을 적어요.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        recent.forEach { e -> EntryRow(e, showDate = true) { onOpen(e) } }
    }
}

@Composable
internal fun IncomeCard(m: MonthMoney, style: WorkStyle?, profile: PayProfile?, onOpenPay: () -> Unit, onTakeHome: (Boolean) -> Unit,
                        netLine: TaxPlus.NetLine? = null, gate: com.bamnat.app.tier.Gate? = null, taxSays: Boolean = false) {
    val hasProfile = profile != null
    val net = profile?.ledgerTakeHome == true
    SectionCard(title = "수입 내역") {
        if (style?.usesPayEngine != false) {
            // [J133-02] 1.33: the salary that came in counts; until then the estimate is 들어올 돈.
            val label = when {
                m.payRecorded > 0 -> "급여 (받은 금액)"
                m.payEstimate != null && net -> "들어올 돈 · 급여 (지급일 실수령 예상)"
                m.payEstimate != null -> "들어올 돈 · 급여 (세전 예상)"
                else -> "급여"
            }
            if (m.payRecorded > 0 || m.payEstimate != null) {
                Line(label, Fmt.won(m.payCounted))
                if (gate != null) NetSummaryLine(netLine, gate)
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hasProfile) "급여 계산을 열면 예상 급여가 들어가요" else "시급·월급을 정하면 근무표로 급여를 계산해요",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenPay) { Text(if (hasProfile) "열기" else "정하기") }
                }
            }
            // [TAX-01] 결정 W-D1: the pay that came is an observation beside the estimate, never added on top.
            if (m.payObserved) m.payEstimate?.let { est ->
                val d = m.payRecorded - est
                Line("  ${if (net) "실수령" else "세전"} 예상 ${Fmt.won(est)}" + when {
                    d == 0L -> " · 같아요"
                    d > 0 -> " · ${Fmt.won(d)} 더 들어왔어요"
                    else -> " · ${Fmt.won(-d)} 덜 들어왔어요"
                }, "", muted = true)
            }
            // [LEDG-29] 세전 of the month's work, or the 실수령 that lands on the pay day (통장에 찍히는 돈).
            // 25f2364b PC 확인 (세전·실수령 칩이 세금·실수령 카드와 겹쳐요): with that card on, the choice is in it.
            if (hasProfile && !taxSays && (m.payRecorded == 0L || m.payObserved)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !net, onClick = { onTakeHome(false) }, label = { Text("그달 일한 세전") })
                FilterChip(selected = net, onClick = { onTakeHome(true) }, label = { Text("지급일 실수령") })
            }
        }
        if (style?.usesJobs == true || m.jobCount > 0) {
            // [RECV-04] Only what was paid counts; the rest of the work is shown, not added.
            Line("일감 ${m.jobCount}건 받은 돈", Fmt.won(m.jobReceived))
            if (m.jobEarned > 0) Line("  일한 금액 ${Fmt.won(m.jobEarned)} · 아직 안 받은 돈 ${Fmt.won(m.jobOwed)}", "", muted = true)
            if (m.jobWithheld > 0) Line("  3.3% 세금 뗀 뒤 금액 (뗀 세금 ${Fmt.won(m.jobWithheld)})", "", muted = true)
            if (m.jobCosts > 0) Line("  자재비·경비는 나간 돈에 넣었어요", Fmt.won(m.jobCosts), muted = true)
        }
        if (style?.usesSales == true || m.salesDays > 0) {
            Line("가게 순수익 (${m.salesDays}일)", Fmt.won(m.salesNet))
            if (m.sales > 0) Line("  매출 ${Fmt.won(m.sales)} · 경비 ${Fmt.won(m.salesCosts)}", "", muted = true)
        }
        if (m.otherIncome > 0) Line("기타 수입", Fmt.won(m.otherIncome))
        HorizontalDivider(Modifier.padding(vertical = 6.dp))
        Line("합계", Fmt.won(m.income), strong = true)
    }
}

@Composable
private fun Line(label: String, value: String, muted: Boolean = false, strong: Boolean = false) {
    val color = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        // 1.37 PC 확인 6a4efc92: '(지급일 실수령 예상)3,530,963원'처럼 붙지 않게 글과 금액 사이를 띄워요.
        Text(label, style = if (muted) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge, color = color,
            modifier = Modifier.weight(1f).padding(end = 8.dp))
        Text(value, style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge, color = color,
            fontWeight = if (strong) FontWeight.Bold else FontWeight.Normal)
    }
}

/** [WORK-02][RECV-02] Work done but not fully paid, and money written down as owed; the real worry of field work and shops. */
@Composable
private fun OwedCard(owed: List<com.bamnat.core.money.Owed>, today: LocalDate, onOpenAll: () -> Unit, onOpen: (com.bamnat.core.money.Owed) -> Unit) {
    val cal = com.bamnat.app.ui.theme.LocalCalendarColors.current
    val overdue = owed.filter { it.status(today) == com.bamnat.core.money.ReceivableStatus.OVERDUE }
    SectionCard(
        title = if (owed.isEmpty()) "받을 돈 (미수금)" else "받을 돈 ${Fmt.won(owed.sumOf { it.left })}",
        trailing = { TextButton(onClick = onOpenAll) { Text(if (owed.isEmpty()) "적기" else "모두 보기") } },
    ) {
        if (owed.isEmpty()) {
            Text("외상, 밀린 일당, 빌려준 돈처럼 아직 못 받은 돈을 적어 두면 약속일에 알려 줘요.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@SectionCard
        }
        if (overdue.isNotEmpty()) {
            Text("약속일 지난 돈 ${overdue.size}건 · ${Fmt.won(overdue.sumOf { it.left })}", style = MaterialTheme.typography.bodyMedium,
                color = cal.warning, modifier = Modifier.padding(bottom = 4.dp))
        }
        owed.take(4).forEachIndexed { i, o ->
            if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
            when (o) {
                is com.bamnat.core.money.Owed.FromJob -> JobRow(o.job, showDate = true) { onOpen(o) }
                is com.bamnat.core.money.Owed.Written -> Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable { onOpen(o) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(o.name, style = MaterialTheme.typography.bodyLarge)
                        Text(listOfNotNull(o.who.takeIf { it.isNotBlank() && it != o.name }, o.status(today).label,
                            o.due?.let { "약속 ${Fmt.shortDate(it)}" }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(Fmt.won(o.left), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold,
                        color = if (o.status(today) == com.bamnat.core.money.ReceivableStatus.OVERDUE) cal.warning else MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        if (owed.size > 4) {
            Text("그 밖에 ${owed.size - 4}건", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * [DAILY-15] 가계부 › 요약's cards the user can order or turn off (화면 꾸미기). [DAILY-04] 결정 1.21 UX: the
 * month's sum first, then what waits for a look, then the month's lines; [right] is the unfolded right side.
 */
internal enum class MoneyPart(val id: String, val label: String, val what: String, val movable: Boolean = true, val right: Boolean = false) {
    HERO("hero", "이번 달 합계", "수입·지출·남은 돈과 예산", movable = false),
    // 1.37 [LEDH-15] 정리함 하나로: the four below in one card, one count, when it is on.
    INBOX("inbox", "정리함", "새로 들어온 기록·확인할 것·분류 제안·반복 지출을 한곳에", movable = false, right = true),
    NEW("new", "새로 들어온 기록", "자동 기록·확인할 기록이 있을 때만", movable = false, right = true),
    // 1.32 [LEDX-16][LEDX-19]
    ASK("ask", "확인할 것", "원래 결제를 못 찾은 환불, 처음 보는 카드", movable = false, right = true),
    // 1.37 [LEDH-16] 지난달 돈 이야기, the first week of a month only.
    STORY("story", "지난달 돈 이야기", "달 초 일주일, 일한 시간·번 돈·쓴 돈·모은 돈"),
    // 1.37 [LEDH-01][LEDH-02][LEDH-03] 근무가 보이는 가계부.
    AHEAD("ahead", "앞으로의 돈", "오늘 쓸 돈·월급까지 며칠·날마다 남을 돈"),
    // 1.37 [LEDH-11][LEDH-13]
    ENVELOPES("envelopes", "모으기 봉투", "목표까지 얼마·언제 채우나, 월급 나누기, 세금 봉투"),
    // 1.37 [LEDH-17]
    FOUND("found", "찾아 준 돈", "올해 이어 붙인 환불, 볼 만한 구독, 오른 값"),
    RECENT("recent", "최근 기록", "이번 달 마지막 다섯 줄", right = true),
    INCOME("income", "수입 내역", "급여 예상과 적은 수입", right = true),
    TAX("tax", "세금·실수령", "세전·세후 예상, 올해 낼 세금", right = true),
    OWED("owed", "받을 돈", "빌려준 돈·외상·안 받은 일감", right = true),
    WORK("work", "일감·매출 돈", "일감 입금 맞추기, 카드 매출 입금 예정"),
    ALL("all", "모든 달력 합계", "달력이 둘 이상일 때"),
    BUDGET("budget", "예산", "분류별 예산과 남은 돈"),
    OTHER("other", "다른 곳 지출 제안", "자주 가는 곳을 분류로"),
    UPCOMING("upcoming", "나갈 돈", "고정 지출과 이번 달 남은 것"),
    REPEAT("repeat", "반복 지출 찾기", "매달 나가는 것 같은 지출"),
    ACCOUNTS("accounts", "통장·카드", "계좌와 카드 잔액"),
    // E 1.32 [LEDX-07][LEDX-09][LEDX-10]
    PACE("pace", "이번 달 흐름", "이대로면 월말 지출, 지난달과 견주기"),
    SUBS("subs", "구독 점검", "매달 나가는 결제와 1년치, 오른 값"),
    CARD25("card25", "카드 소득공제 문턱", "총급여 25% 문턱과 신용·체크 고르기"),
    CARDBILL("cardbill", "다음 카드값", "카드별 다음 결제 금액과 이대로면 얼마"),
    // 1.32 [LEDX-17]
    INSTALL("install", "할부 청구 기준", "달마다 나갈 할부와 수수료, 남은 잔액"),
    SHIFTSPEND("shiftspend", "근무별 소비", "야간 다음 날·쉬는 날 하루 소비"),
    SALESSPLIT("salessplit", "매출 나눠 보기", "카드·현금·이체·배달앱 비율"),
    FREELANCE("freelance", "3.3% 합계", "일한 곳별 뗀 3.3%와 5월 신고 준비"),
    AUTO("auto", "덜 적는 법", "결제 알림 읽기·문자 붙여넣기", right = true),
    // 1.37 [LEDH-33]
    ASKME("askme", "가계부에 묻기", "어디에 제일 많이 썼는지, 지난달보다 더 썼는지 칩 한 번에"),
}

internal fun moneyParts(layout: String) = com.bamnat.core.layout.PageLayout.decode(layout).arrange(MoneyPart.entries.toList()) { it.id }

@Composable
internal fun MoneyEditSheet(
    layout: String, update: ((com.bamnat.app.data.AppSettings) -> com.bamnat.app.data.AppSettings) -> Unit, onDismiss: () -> Unit,
    /** [FIT-01] The calendar's kind of work and id: the parts it does not use wait under 더 보기. */
    style: com.bamnat.core.work.WorkStyle? = null, calendar: String = com.bamnat.app.data.PersonalCalendar.MAIN,
) {
    // 1.37 [LEDH-20] Cards move inside their layer (오늘·이번 달·앞으로·점검), the order the summary draws them in.
    val placed = byLayer(moneyParts(layout)) { it.part }
    val context = androidx.compose.ui.platform.LocalContext.current
    val added = com.bamnat.app.ui.guide.rememberFitAdded(calendar)
    val surface = com.bamnat.core.guide.FitSurface.MONEY_PARTS
    fun write(next: List<com.bamnat.core.layout.PageLayout.Placed<MoneyPart>>) =
        update { it.copy(moneyLayout = com.bamnat.core.layout.PageLayout.of(next) { p -> p.id }.encode()) }
    val rows = { movable: Boolean, layer: MoneyLayer? ->
        placed.filter { it.part.movable == movable && (layer == null || it.part.layer == layer) && com.bamnat.core.guide.FitTable.visible(surface, it.part.id, style, added) }
            .map { com.bamnat.app.ui.components.EditRow(it.part.id, it.part.label, it.part.what, it.shown, movable, it.isNew) }
    }
    com.bamnat.app.ui.components.PageEditSheet(
        title = "가계부 카드 배치",
        groups = listOf(
            com.bamnat.app.ui.components.EditGroup("위쪽", rows(false, null)),
        ) + MoneyLayer.entries.mapNotNull { l ->
            l.title?.let { t -> com.bamnat.app.ui.components.EditGroup(if (l == MoneyLayer.CHECK) "$t (요약에서 접어 둘 수 있어요)" else "$t 카드 순서", rows(true, l)) }
        }.filter { it.rows.isNotEmpty() },
        middle = "요약·내역·통계 탭",
        others = com.bamnat.app.ui.guide.fitOthers(surface, style, added) { id -> MoneyPart.entries.firstOrNull { it.id == id }?.let { it.label to it.what } },
        onAdd = { id, on -> com.bamnat.app.ui.guide.setFitAdded(context, calendar, surface, id, on) },
        onShow = { id, shown -> write(placed.map { if (it.part.id == id) it.copy(shown = shown) else it }) },
        onMove = { id, by ->
            val fixed = placed.filterNot { it.part.movable }
            val layer = placed.first { it.part.id == id }.part.layer
            val movable = MoneyLayer.entries.flatMap { l ->
                val mine = placed.filter { it.part.movable && it.part.layer == l }
                if (l == layer) com.bamnat.core.layout.PageLayout.move(mine, mine.indexOfFirst { it.part.id == id }, by) else mine
            }
            write(fixed + movable)
        },
        onReset = { update { it.copy(moneyLayout = "") } },
        onAsk = {
            onDismiss()
            com.bamnat.app.ui.settings.InquiryLinks.ask(com.bamnat.app.ui.settings.InquiryStart(about = "카드 배치"))
        },
        onDismiss = onDismiss,
    )
}
