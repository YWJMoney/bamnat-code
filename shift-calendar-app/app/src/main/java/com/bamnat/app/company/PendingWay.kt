package com.bamnat.app.company

import com.bamnat.app.AppContainer
import com.bamnat.app.data.CompanyShare
import com.bamnat.core.pay.BaseAsk
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Unknowns
import com.bamnat.core.pay.ValueTimeline
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.YearMonth
import java.util.WeakHashMap

/**
 * [PAYG-01] 1.30 묶음 BA (사용자 2026-10-27 "'이 급여 방식 쓰기' → 기본급 칸(비우면 다음으로 못 감) — 입력을 안 해도 잘 넘어가고
 * 있음. 나중에 클릭하면 적용이 된 상태로 페이지를 빠져나간다", "기본급 없이 닫으면 … 창이 닫히면서 바로 적용이 되므로"): a company's
 * pay way the user chose and has not yet put in. 1.29 wrote the way into the pay the moment it was chosen and asked the 기본급
 * after, so leaving the question left the rules in. Now choosing only keeps it here, in memory: the pay, the pay way in use, the
 * company record and the server's '쓰고 있어요' change only at [commit], all at once with the 기본급, and closing anything before
 * that is [PendingWays.cancel] — nothing changed.
 *
 * Joining the company's 광장 is the one thing done at choosing: the pay way is shown to members only (결정 D79), and the server
 * keeps a join for 30 days whatever the app does, so the phone remembers it truly ([CompanyStore.setMember]).
 */
data class PendingWay(
    val companyId: String,
    val company: String,
    val dept: String,
    /** The server's id of the setup the way comes from. */
    val setup: String,
    /** The company's setup file, its pay part what goes in. */
    val text: String,
    val source: Source,
) {
    enum class Source {
        /** 우리 회사 급여 방식 찾기 → 이 급여 방식 쓰기. */
        FIND,
        /** A company's cycle put in with "급여 계산 방식도 같이" ('n명이 쓰고 있어요'): the cycle is in, the pay waits. */
        APPLY,
        /** The first setup or a calendar's setup picked a coworkers' setup with its pay. */
        FIRST_SETUP,
    }

    /** [PAYG-08] The 통상시급's hours the company's way says (LG화학 180), for the 기본급 field to show first. */
    val hours: Int? get() = CompanyShare.hoursIn(text)

    /** "현대제철 · 당진", or the company alone. */
    val title: String get() = if (dept.isEmpty()) company else "$company · $dept"

    /** The pay way as the pay screen keeps it once put in. */
    fun way(): PayWay = PayWay(companyId, company, dept, setup)
}

/**
 * [PAYG-01] One calendar's [PendingWay], in memory: a restart forgets it, which is the same as not putting it in. [dropped] is
 * the one closed last, for the pay screen's line "‘현대제철’ 급여 방식은 넣지 않았어요 · 다시 넣기" ([PAYG-13]); [hoursAsk] the
 * 통상시급 hours the user put in over the company's, to offer them back as an opinion ([PAYG-08]).
 */
class PendingWays {
    private val _pending = MutableStateFlow<PendingWay?>(null)
    val pending: StateFlow<PendingWay?> = _pending.asStateFlow()
    private val _dropped = MutableStateFlow<PendingWay?>(null)
    val dropped: StateFlow<PendingWay?> = _dropped.asStateFlow()
    private val _hoursAsk = MutableStateFlow<Pair<PendingWay, Int>?>(null)
    val hoursAsk: StateFlow<Pair<PendingWay, Int>?> = _hoursAsk.asStateFlow()

    fun offer(way: PendingWay) {
        _dropped.value = null
        _pending.value = way
    }

    /** Closed without its 기본급: nothing was put in; kept as [dropped] for 다시 넣기. Only [way] when said (a late close). */
    fun cancel(way: PendingWay? = null) {
        val p = _pending.value ?: return
        if (way != null && way != p) return
        _pending.value = null
        _dropped.value = p
    }

    /** 다시 넣기: the way closed last, asked again. */
    fun again() {
        val d = _dropped.value ?: return
        _dropped.value = null
        _pending.value = d
    }

    /** The line about the way not put in is closed. */
    fun forgetDropped() {
        _dropped.value = null
    }

    internal fun committed(way: PendingWay, proposeHours: Int?) {
        if (_pending.value == way) _pending.value = null
        if (_dropped.value == way) _dropped.value = null
        _hoursAsk.value = proposeHours?.let { way to it }
    }

    /** The opinion on the hours was sent or let go. */
    fun hoursAsked() {
        _hoursAsk.value = null
    }

    companion object {
        // One per calendar's pay way store (the main calendar's is the app's own, an added one's made once per calendar), so
        // two calendars never see each other's, and a store that went (a calendar deleted) takes its own with it.
        private val all = WeakHashMap<PayWayStore, PendingWays>()

        fun of(scope: CompanyScope): PendingWays = synchronized(all) { all.getOrPut(scope.payWays) { PendingWays() } }

        /**
         * [PAYG-01] The amounts screen made a first pay from a coworkers' setup ([applied], file [text]): its pay way is in use
         * from now — not from the moment it was chosen — and 통상시급 [hours] other than the company's are offered back.
         */
        fun keptWith(c: AppContainer, scope: CompanyScope, applied: AppliedSetup?, text: String, hours: Int?) {
            val ways = of(scope)
            val waiting = ways.pending.value
            val a = applied ?: waiting?.let { AppliedSetup(it.companyId, it.company, it.dept, it.setup, null, false, com.bamnat.core.time.Today.instant()) }
            if (a == null || !runCatching { org.json.JSONObject(text).has("pay") }.getOrDefault(false)) return
            if (scope.payWays.way.value?.setup != a.setup) scope.payWays.use(PayWay(a.companyId, a.company, a.dept, a.setup))
            val way = waiting?.takeIf { it.setup == a.setup } ?: PendingWay(a.companyId, a.company, a.dept, a.setup, text, PendingWay.Source.FIRST_SETUP)
            ways.committed(way, BaseAsk.hoursToPropose(hours, CompanyShare.hoursIn(text)))
            c.telemetry.event("pay_way", "commit_amounts")
        }
    }
}

/** [PAYG-01] The calendar's way chosen and not yet put in ([PendingWays]). */
val CompanyScope.pendingWays: PendingWays get() = PendingWays.of(this)

/**
 * [PAYG-01] Puts [way] into this calendar's pay with the user's own [base], all at once: the rules and allowances with the
 * 기본급 (and, when said, the 통상시급's [hours]) in one save ([CompanyShare.applyRulesWith]), then the amounts of the allowances
 * that differ by person the user typed ([amounts], by name; the rest stay 금액 모름), then the pay way in use, the company's name
 * for a later share and the server's '쓰고 있어요'. [month] is where a new pay's base starts (a year back, as the amounts
 * screen does) or where a known one changes. False when nothing went in ([base] not above 0, or no pay in the file).
 */
suspend fun CompanyScope.commit(
    c: AppContainer,
    way: PendingWay,
    base: Long,
    hours: Int?,
    month: YearMonth,
    amounts: Map<String, Long> = emptyMap(),
): Boolean {
    if (!BaseAsk.ready(base)) return false
    val had = pay.profile.value
    if (!share.applyRulesWith(way.text, base, hours, month)) return false
    // The allowances the way just brought in that differ by person: the amounts the user typed, the rest 금액 모름 (listed
    // under the total, never counted as 0원 without a word).
    val names = had?.items?.map { it.name }?.toSet().orEmpty()
    val typed = amounts.filterValues { it > 0 }.filterKeys { it !in names }
    pay.profile.value?.let { p ->
        val from = month.atDay(1)
        val brought = p.items.filter { it.name !in names && it.amount.entries.isEmpty() && !it.isPercent && !it.isCompanyFixed &&
            !(it.cadence == com.bamnat.core.pay.Cadence.BONUS && it.percentOfBase != null) }
        if (brought.isEmpty()) return@let
        val filled = brought.filter { it.name in typed }.map { it.id }.toSet()
        val unknown = brought.filter { it.name !in typed }.map { Unknowns.item(it.id) }.toSet()
        pay.save(p.copy(
            items = p.items.map { i -> if (i.id in filled) i.copy(amount = ValueTimeline(listOf(DatedValue(from, typed.getValue(i.name))))) else i },
            estimatedFields = p.estimatedFields - filled.map(Unknowns::item).toSet() + unknown,
        ), keepUndo = false)
    }
    payWays.use(way.way())
    payWays.decided = true
    // The company the pay is for, as a setup typed by hand would say, so a later share goes to the same 광장.
    if (store.applied.value == null) store.setDraft(CompanyDraft(way.company, way.dept))
    if (store.pendingPay == way.text) store.pendingPay = null
    // [PAYF-04] Counted as one more phone using it ("n명 쓰고 있어요"); a cycle put in with it said so already.
    if (way.source == PendingWay.Source.FIND && c.companyApi.enabled) {
        val device = store.device
        val cal = serverCal
        c.appScope.launch { runCatching { c.companyApi.use(device, way.companyId, way.setup, true, cal) } }
    }
    pendingWays.committed(way, BaseAsk.hoursToPropose(hours, way.hours))
    c.telemetry.event("pay_way", "commit_" + way.source.name.lowercase())
    return true
}

/** [PAYG-01] Closed before its 기본급: nothing changes; the pay screen says so in one line. */
fun CompanyScope.cancelPending(c: AppContainer, way: PendingWay? = null) {
    val p = pendingWays.pending.value ?: return
    if (way != null && way != p) return
    pendingWays.cancel(p)
    c.telemetry.event("pay_way", "cancel_" + p.source.name.lowercase())
}
