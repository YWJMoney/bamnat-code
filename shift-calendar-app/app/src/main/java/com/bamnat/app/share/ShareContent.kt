package com.bamnat.app.share

import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.pay.named
import com.bamnat.core.memo.MemoSharing
import com.bamnat.core.pay.PayCalculator
import com.bamnat.core.recipe.ShareKit
import com.bamnat.core.work.MoneyShare
import com.bamnat.core.work.MoneyShareFigures
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.time.LocalDate
import com.bamnat.core.time.Today

/** [SHR-01] A link's payload from the phone's own data, with only what the sharer ticked. */
object ShareContent {
    suspend fun payload(
        c: AppContainer, snapshot: ScheduleSnapshot, from: LocalDate, title: String, ttlDays: Int?, options: ShareOptions,
        /** [MCAL-03][MCAL-06] Whose calendar: its own kind of work, money and pay; [MEMO-01] the memos it sees. */
        calendar: String = PersonalCalendar.MAIN,
        /** [FEAT-15] Whether the link serves a calendar feed; it does only with 프로. */
        feed: Boolean = false,
    ): JSONObject =
        withContext(Dispatchers.Default) {
            val kind = kind(c, calendar)
            val memos = if (options.memos) memos(c, from, calendar) else emptyMap()
            val pay = if (options.pay) money(c, snapshot, from, kind, calendar) else emptyList()
            val jobs = if (options.jobs) jobs(c, from, calendar) else emptyMap()
            val zone = Today.zone().takeIf { feed && c.featureGate.allows(com.bamnat.app.tier.Feature.ICS_FEED) }
            val setup = if (options.sendsSetup && setupInLink(c, calendar)) setupFile(c, calendar) else null
            val open = lookInLink(c)
            val look = if (options.look && open) lookFile(c, title) else null
            val kit = if (options.kit && open) kitFile(c, calendar, title, withCycle = setup == null) else null
            val sleep = if (options.sleepNow && calendar == PersonalCalendar.MAIN) sleepNow(c) else null
            ShareLinkPayload.build(snapshot, from, title, ttlDays, options, memos, pay, kind, jobs, zone, setup, look, kit, sleep)
        }

    /** 1.38 [SLEEP-11] Whether a link may say 지금 자는 중 (`sleep.share`, from the 처리방침 date). */
    fun sleepInLink(c: AppContainer): Boolean =
        runCatching { c.flags.on(com.bamnat.core.flags.FlagCatalog.SLEEP_SHARE) }.getOrDefault(false)

    /** 1.38 [SLEEP-11] The open sleep (not an imported one) and the next alarm, or null while awake. */
    suspend fun sleepNow(c: AppContainer): SleepNow? {
        if (!sleepInLink(c)) return null
        val now = Today.dateTime()
        val open = c.sleepRepository.recent(days = 2).first().firstOrNull {
            val m = java.time.Duration.between(it.start, now).toMinutes()
            it.end == null && !it.imported && m >= 0 && m < com.bamnat.core.sleep.SleepDebt.MAX_SLEEP_MINUTES
        } ?: return null
        val since = open.start.atZone(Today.zone()).toInstant().toEpochMilli()
        return SleepNow(since, c.alarmScheduler.upcoming.value?.triggerAtMillis?.takeIf { it > since })
    }

    /**
     * [SHR-20] 1.33: a coworker's link carries the company setup only from 처리방침 3.7's date (`share.setup`, LEGAL), and only
     * for work that has a company ([AppContainer.companyFits]).
     */
    fun setupInLink(c: AppContainer, calendar: String = PersonalCalendar.MAIN): Boolean =
        c.companyFits(calendar, needsServer = false) && runCatching { c.flags.on(com.bamnat.core.flags.FlagCatalog.SHARE_SETUP) }.getOrDefault(false)

    /** 1.37 [SHR-31][SHR-32] A link may carry the 테마 and the 근무 설정 from 처리방침 3.11's date (`share.look`, LEGAL). */
    fun lookInLink(c: AppContainer): Boolean =
        runCatching { c.flags.on(com.bamnat.core.flags.FlagCatalog.SHARE_LOOK) }.getOrDefault(false)

    /**
     * 1.37 [SHR-31] The look on the phone as a theme (looks only, [com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS]), without its
     * pictures ([com.bamnat.core.recipe.LookParts.withoutPhotos]: family photos stay on this phone); null when the look is the app's own
     * or too big for a link.
     */
    suspend fun lookFile(c: AppContainer, title: String): String? = runCatching {
        // PC 3차: with no theme worn it is the sender's own look, not a 테마; a worn theme keeps its name.
        val name = c.wardrobe.wornTitle()?.take(30)
            ?: title.trim().take(20).let { if (it.isEmpty()) "보낸 분의 달력 모양" else "${it} 님의 달력 모양" }
        val r = com.bamnat.core.recipe.LookParts.withoutPhotos(c.wardrobe.myLookRecipe(name))
        ShareKit.lookText(r)
    }.getOrNull()

    /**
     * 1.37 [SHR-32] The calendar's 근무 설정 as a recipe ([ShareKit.KINDS]): 근무 이름·글자·색, the alarms before each kind of shift and,
     * for the main calendar of 교대·일근 [withCycle], the 근무 주기 (shifts, times and order) with no pay part at all. Never 급여 규칙,
     * 기본급, an amount, a memo or a photo. Null when there is nothing to send.
     */
    suspend fun kitFile(c: AppContainer, calendar: String, title: String, withCycle: Boolean = true): String? = runCatching {
        val style = c.scopeOf(calendar).currentSettings().workStyle
        val pieces = ShareKit.kindsFor(style, calendar == PersonalCalendar.MAIN && withCycle).mapNotNull { k ->
            val values = if (k == com.bamnat.core.recipe.PieceKind.COMPANY) mapOf("setup" to c.companyShare.exportForServer(cycleOnly = true).toString())
                else c.recipes.pieces.export(k, calendar)
            values?.filterValues { it != null }?.takeIf { it.isNotEmpty() }?.let { com.bamnat.core.recipe.Piece(k, it) }
        }
        val name = title.trim().take(20).let { if (it.isEmpty()) "보낸 분의 근무 설정" else "${it}의 근무 설정" }
        ShareKit.kitText(com.bamnat.core.recipe.Recipe(name, "근무 설정", "", style?.let { setOf(it) } ?: com.bamnat.core.work.WorkStyle.entries.toSet(), pieces = pieces))
    }.getOrNull()

    /** [SHR-20] The calendar's company setup as a coworker's file has it, without amounts (회사 공통 정액 as the 광장 has it). */
    suspend fun setupFile(c: AppContainer, calendar: String = PersonalCalendar.MAIN): String? {
        val scope = c.companyOf(calendar)
        return runCatching { scope.share.export(companyName(c, calendar), withAmounts = false) }.getOrNull()
    }

    /** [SHR-20][695] The company the calendar is in, else the name typed in the first setup; "" when none. */
    fun companyName(c: AppContainer, calendar: String = PersonalCalendar.MAIN): String {
        val store = c.companyOf(calendar).store
        return store.applied.value?.company ?: store.member.value?.second?.takeIf { it.isNotBlank() } ?: store.draft.value?.company?.trim().orEmpty()
    }

    /** [SHR-11] The money a link of this user carries: pay for employees, job income for field work, sales for a shop. */
    fun kind(c: AppContainer, calendar: String = PersonalCalendar.MAIN): MoneyShare =
        MoneyShare.of(c.scopeOf(calendar).currentSettings().workStyle)

    /** [SHR-11] The months' money in the user's own kind; months with nothing to show are left out. */
    suspend fun money(
        c: AppContainer, snapshot: ScheduleSnapshot, from: LocalDate, kind: MoneyShare, calendar: String = PersonalCalendar.MAIN,
    ): List<SharedPay> =
        withContext(Dispatchers.Default) {
            val months = ShareLinkPayload.months(from)
            val money = c.calendars.money(calendar)
            val figures = when (kind) {
                MoneyShare.SALES -> MoneyShareFigures.sales(months, money.sales.first())
                MoneyShare.JOBS -> MoneyShareFigures.jobs(months, money.jobs.first())
                // With no pay calculation, the salary written in the ledger still lets an employee show it.
                MoneyShare.PAY -> pay(c, snapshot, from, calendar).takeIf { it.isNotEmpty() }?.let { return@withContext it }
                    ?: MoneyShareFigures.recordedPay(months, money.entries.first())
            }
            figures.map { SharedPay(it.month, it.amount, it.label) }
        }

    /** [SHR-11] Each day's booked jobs, every day a job runs, by title and start–end time only. */
    suspend fun jobs(c: AppContainer, from: LocalDate, calendar: String = PersonalCalendar.MAIN): Map<LocalDate, List<SharedJob>> {
        val until = from.plusDays(ShareLinkPayload.DAYS.toLong())
        val out = LinkedHashMap<LocalDate, MutableList<SharedJob>>()
        c.calendars.money(calendar).jobs.first().sortedWith(compareBy({ it.date }, { it.start })).forEach { j ->
            val start = j.start
            val end = j.end
            val time = if (start != null && end != null) "%02d:%02d-%02d:%02d".format(start.hour, start.minute, end.hour, end.minute) else null
            var d = maxOf(j.date, from)
            while (!d.isAfter(j.lastDate) && d.isBefore(until)) {
                out.getOrPut(d) { mutableListOf() } += SharedJob(j.title, time)
                d = d.plusDays(1)
            }
        }
        return out
    }

    /**
     * Each day's memos in the link's days, as one line a day, newest first as under the calendar.
     * [SHR-01][MEMO-02] Only the calendar's own memos and those for every calendar: 메모 공유 shows a
     * grouped calendar's memos in the app, never on another calendar's link.
     */
    suspend fun memos(c: AppContainer, from: LocalDate, calendar: String = PersonalCalendar.MAIN): Map<LocalDate, String> {
        val until = from.plusDays(ShareLinkPayload.DAYS.toLong())
        // 1.36 [SEC-12] 보안 점검 M5: what rules wrote (a memo, a line with {원(이달지출)} in it) stays on the phone.
        val ruled = com.bamnat.app.logic.LogicStore(c.appContext)
        return c.memoRepository.memos.first()
            .filter { m -> m.showsOn(calendar, MemoSharing.SEPARATE) && !m.inTrash && m.date?.let { !it.isBefore(from) && it.isBefore(until) } == true }
            .filterNot { ruled.madeMemo(it.id) }
            .map { m -> ruled.madeLines(m.id).takeIf { it.isNotEmpty() }?.let { lines -> m.copy(body = m.body.lines().filterNot { it in lines }.joinToString("\n")) } ?: m }
            .filter { !it.isBlank }
            .sortedByDescending { it.updatedAtMillis }
            .groupBy { it.date!! }
            .mapNotNull { (date, list) -> ShareLinkPayload.memoLine(list)?.let { date to it } }
            .toMap()
    }

    /** The months' pay before tax: the closed figure when a month was closed, else this calendar's estimate. */
    fun pay(c: AppContainer, snapshot: ScheduleSnapshot, from: LocalDate, calendar: String = PersonalCalendar.MAIN): List<SharedPay> {
        val store = c.calendars.pay(calendar)
        val profile = store.profile.value ?: return emptyList()
        val closed = store.closed.value
        val calculator = PayCalculator()
        return ShareLinkPayload.months(from).map { m ->
            closed[m]?.let { SharedPay(m, it.total, "세전 확정") }
                ?: SharedPay(m, calculator.calculate(profile.named(snapshot.kinds), m, snapshot.resolver::resolve).total, "세전 예상")
        }
    }
}
