package com.bamnat.app.logic

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.bamnat.app.appContainer
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.core.formula.DayFactsBook
import com.bamnat.core.holiday.KoreanHolidays
import com.bamnat.core.memo.Memo
import com.bamnat.core.money.MoneyBook
import com.bamnat.core.money.MoneyKind
import com.bamnat.core.recipe.ShiftRole
import com.bamnat.core.work.WorkStyle
import com.bamnat.app.AppContainer
import com.bamnat.app.pay.named
import com.bamnat.app.ui.pay.hourlyWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * [RUL-05] 1.36 885 탭 사이 흐름 on the phone: the days on screen as formulas read them (근무 from the calendar, 꼬리표·체크 from the memos,
 * 지출·수입 from 가계부). The calendar provides it ([LocalDayFacts]); a part drawn with a blueprint asks `scope(date)` for its `when`
 * lines and formula values (갈래 A의 그리기 엔진), and buttons and rules ask the same.
 */
val LocalDayFacts = staticCompositionLocalOf<DayFactsBook?> { null }

private val HM = DateTimeFormatter.ofPattern("HH:mm")

/** The short word formulas use for a kind of work (교대·일근·알바·현장·자영업·투자). */
fun WorkStyle.formulaWord(): String = when (this) {
    WorkStyle.SHIFT -> "교대"; WorkStyle.DAY -> "일근"; WorkStyle.HOURLY -> "알바"
    WorkStyle.FIELD -> "현장"; WorkStyle.BUSINESS -> "자영업"; WorkStyle.INVEST -> "투자"
}

/**
 * [FXR-01][FXR-02] What formulas read besides the calendar, memos and money: the answers to the 받는 칸 of the recipes in use (by
 * name, for `칸("이름")`) and the calendar's pay, for 이달급여·그날급여.
 */
class FormulaExtras(
    val inputs: Map<String, Any?> = emptyMap(),
    val pay: PaySource? = null,
    /** 1.37 [LEDH-19] 모으기 봉투 and what went into them, for 봉투모은돈·봉투진행. */
    val envelopes: Pair<List<com.bamnat.core.money.Envelope>, List<com.bamnat.core.money.EnvelopeMove>> = emptyList<com.bamnat.core.money.Envelope>() to emptyList(),
)

/** [FXR-02] The calendar's pay as the pay screen works it out: its setup, its settings (근무 종류 이름·주휴) and the closed months. */
data class PaySource(
    val profile: com.bamnat.core.pay.PayProfile,
    val settings: com.bamnat.app.data.AppSettings,
    val closed: Map<YearMonth, com.bamnat.core.pay.CalcSnapshot> = emptyMap(),
)

/**
 * [FXR-02] 이달급여·그날급여 over [snap]: a closed month as it was closed, an open one worked out as the pay screen does
 * ([com.bamnat.app.ui.pay.PayViewModel.calculate]), each month once. 그날급여 is the day's own amount on the pay screen's day list (for
 * 시급·일당 the day's base too; for 월급 what the day adds to it), 0 for a day of the month with nothing, none without a pay setup.
 */
class PayFacts(private val pay: PaySource, private val snap: ScheduleSnapshot) {
    private val calculator = com.bamnat.core.pay.PayCalculator()
    private val months = HashMap<YearMonth, com.bamnat.core.pay.MonthPay?>()

    fun payMonth(d: LocalDate): YearMonth = pay.profile.period.monthOf(d)

    private fun month(m: YearMonth): com.bamnat.core.pay.MonthPay? = synchronized(months) {
        months.getOrPut(m) {
            runCatching {
                calculator.calculate(pay.profile.named(pay.settings.extraKinds).hourlyWeek(pay.settings), m, snap.resolver::resolve)
            }.getOrNull()
        }
    }

    fun month(m: YearMonth, closedToo: Boolean = true): Long? = (if (closedToo) pay.closed[m]?.total else null) ?: month(m)?.total

    fun day(d: LocalDate): Long? = month(payMonth(d))?.let { it.day(d)?.amount ?: 0L }

    /** 1.37 [LEDH-19] Take-home pay days from last month to [months] ahead, as the 앞으로의 돈 card counts them. */
    fun payDays(today: LocalDate, months: Int = 4): List<com.bamnat.core.money.PayDay> =
        (-1..months).map { YearMonth.from(today).plusMonths(it.toLong()) }.mapNotNull { m ->
            val gross = month(m)?.total?.takeIf { it > 0 } ?: return@mapNotNull null
            val date = pay.profile.period.payDate(m)
            com.bamnat.core.money.PayDay(date ?: m.atEndOfMonth(), com.bamnat.core.pay.PayIncome.net(pay.profile, m, gross), "${m.monthValue}월 급여", dateGuessed = date == null)
        }

    /** 1.37 [LEDH-19] Take-home per hour of the latest month whose work is done (10시간 넘게 일한 달). */
    fun hourly(today: LocalDate): Long? = (1..3).map { YearMonth.from(today).minusMonths(it.toLong() - 1) }
        .filter { pay.profile.period.end(it).isBefore(today) }
        .firstNotNullOfOrNull { m ->
            val p = month(m) ?: return@firstNotNullOfOrNull null
            val mins = p.minutes.sumOf { it.total.worked }
            if (mins < 600) null else com.bamnat.core.money.WorkTime.hourly(com.bamnat.core.pay.PayIncome.net(pay.profile, m, p.total), mins)
        }
}

/** A book of day values over [snap], [memos] and [money]; built again only when one of them changes. */
fun dayFactsOf(
    snap: ScheduleSnapshot,
    memos: List<Memo>,
    money: MoneyBook,
    style: WorkStyle?,
    today: LocalDate,
    extras: FormulaExtras = FormulaExtras(),
): DayFactsBook {
    val byDate = memos.filter { it.date != null && !it.inTrash }.groupBy { it.date!! }
    val cash = money.entries.groupBy { it.date }.mapValues { (_, es) ->
        DayFactsBook.DayCash(
            income = es.filter { it.kind == MoneyKind.INCOME }.sumOf { it.amount },
            expense = es.filter { it.kind == MoneyKind.EXPENSE }.sumOf { it.amount },
            kinds = es.filter { it.kind == MoneyKind.EXPENSE }.map { it.category },
        )
    }
    val shifts = HashMap<LocalDate, DayFactsBook.DayShift?>()
    val pay = extras.pay?.let { PayFacts(it, snap) }
    return DayFactsBook(
        today = today,
        style = style?.formulaWord().orEmpty(),
        shifts = { d ->
            synchronized(shifts) {
                shifts.getOrPut(d) {
                    val r = runCatching { snap.resolver.resolve(d) }.getOrNull() ?: return@getOrPut null
                    val t = r.shiftType ?: r.coveredShift ?: return@getOrPut null
                    val minutes = if (t.isOff) 0 else r.scheduledMinutes
                    DayFactsBook.DayShift(
                        name = t.name,
                        role = ShiftRole.guess(t.name, t.isOff, r.start?.hour ?: t.start?.hour, minutes).key,
                        start = r.start?.format(HM),
                        end = r.end?.format(HM),
                        minutes = minutes,
                        off = t.isOff || r.leave?.isFullDay == true,
                        leave = r.leave?.kind?.label,
                    )
                }
            }
        },
        memos = { byDate[it].orEmpty() },
        money = { cash[it] },
        holiday = { KoreanHolidays.nameOf(it) },
        monthPay = { m -> pay?.month(m) },
        dayPay = { d -> pay?.day(d) },
        now = { com.bamnat.core.time.Today.time().format(HM) },
        inputs = extras.inputs,
        payMonth = { d -> pay?.payMonth(d) ?: YearMonth.from(d) },
        moneyFacts = {
            val w = style?.formulaWord().orEmpty()
            val payDays = if (style?.usesPayEngine == false) emptyList() else pay?.payDays(today).orEmpty()
            val hourly = if (style == WorkStyle.INVEST) null else pay?.hourly(today)
            com.bamnat.core.formula.MoneyFacts.of(money, today, w, payDays, hourly, extras.envelopes.first, extras.envelopes.second)
        },
    )
}

/** [FXR-01][FXR-02] Calendar [cal]'s 받는 칸 answers and pay, kept up to date. */
fun AppContainer.formulaExtras(cal: String): Flow<FormulaExtras> {
    val s = scopeOf(cal)
    // PC 확인 5c21f3bb: a tab laid on (얹은 탭) keeps its 받는 칸 too; a recipe put on later says them again over it.
    val base = combine(recipes.formulaInputs(cal), wardrobe.overlays, s.pay.profile, s.pay.closed, s.settings) { inputs, laid, profile, closed, settings ->
        FormulaExtras(com.bamnat.app.recipe.overlayInputs(laid) + inputs, profile?.let { PaySource(it, settings, closed) })
    }
    return combine(base, s.money.envelopes) { e, env -> FormulaExtras(e.inputs, e.pay, env) }
}

/**
 * [FXR-03] The calendar in use as formulas read it, kept up to date: what every tab hands its dated parts (메모 카드, 가계부 한 줄, 급여,
 * 달력 머리·판), and what rules and buttons read away from the calendar.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
fun AppContainer.dayFactsFlow(): Flow<DayFactsBook> = calendars.active.flatMapLatest { cal ->
    val s = scopeOf(cal)
    combine(s.schedule.live, memoRepository.memos.map { list -> list.filter { it.showsOn(cal) } }, s.money.book, s.settings, formulaExtras(cal)) { snap, memos, money, settings, extras ->
        dayFactsOf(snap, memos, money, settings.workStyle, com.bamnat.core.time.Today.date(), extras)
    }
}.flowOn(Dispatchers.Default)

@Composable
fun rememberDayFacts(snap: ScheduleSnapshot?, memos: List<Memo>, money: MoneyBook, style: WorkStyle?, today: LocalDate): DayFactsBook? {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val cal by c.calendars.active.collectAsState()
    val extrasFlow = remember(c, cal) { c.formulaExtras(cal) }
    val extras by extrasFlow.collectAsState(FormulaExtras())
    return remember(snap, memos, money, style, today, extras) { snap?.let { dayFactsOf(it, memos, money, style, today, extras) } }
}

/**
 * The phone's own day values for screens away from the calendar (스튜디오 미리 보기·규칙 편집, and [FXR-03] every tab's dated parts),
 * followed in the background; null until the first is read, or when [on] is false (nothing on screen reads a formula).
 */
@Composable
fun rememberAppDayFacts(on: Boolean = true): DayFactsBook? {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val flow = remember(c, on) { if (on) c.dayFactsFlow() else kotlinx.coroutines.flow.flowOf(null) }
    val v by flow.collectAsState(null)
    return v
}

/**
 * [FXR-03] A part drawn for [date] (메모 카드, 가계부 한 줄, 급여, 달력 머리 …): its style with that day's values, so its `when` lines and
 * formula values work as they do in the month's cells. Without a date or a book of day values, as [com.bamnat.app.ui.parts.partStyle].
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun partStyleOn(id: String, date: LocalDate?, states: Set<String> = emptySet()): com.bamnat.core.recipe.PartStyle =
    com.bamnat.app.ui.parts.LocalBlueprint.current.style(id, states, scopeOn(date))

/** [FXR-03] The day's values for a part drawn for [date], or null (no date, no book, or nothing in the blueprint changes by day). */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun scopeOn(date: LocalDate?): com.bamnat.core.formula.FormulaScope? {
    date ?: return null
    if (!com.bamnat.app.ui.parts.LocalBlueprint.current.hasFormulas) return null
    return LocalDayFacts.current?.scope(date)
}

/**
 * [TAB-08] PC 4277abca (분류 색 가계부·고정 메모 금색이 안 바뀜): a part drawn for one thing — a ledger line, a memo card — reads that
 * thing's own values first (분류·종류·금액, 고정·체크율·꼬리표 …, [com.bamnat.core.layout.TabFacts]), then its day's.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
fun scopeFor(date: LocalDate?, facts: () -> com.bamnat.core.layout.TabFacts.Facts): com.bamnat.core.formula.FormulaScope? {
    if (!com.bamnat.app.ui.parts.LocalBlueprint.current.hasFormulas) return null
    val day = date?.let { LocalDayFacts.current?.scope(it) }
    val own = facts()
    return object : com.bamnat.core.formula.FormulaScope {
        override fun get(name: String): com.bamnat.core.formula.Value? =
            if (own.values.containsKey(name)) com.bamnat.core.formula.Value.of(own.values[name]) else day?.get(name)
        override fun input(label: String): com.bamnat.core.formula.Value? =
            if (own.inputs.containsKey(label)) com.bamnat.core.formula.Value.of(own.inputs[label]) else day?.input(label)
    }
}

/** [FXR-03] The day a month's part is drawn for (달력 머리, 연간 판 한 달, 급여): today in this month, else its first day. */
fun dayOfMonth(m: YearMonth, today: LocalDate = com.bamnat.core.time.Today.date()): LocalDate =
    if (YearMonth.from(today) == m) today else m.atDay(1)
