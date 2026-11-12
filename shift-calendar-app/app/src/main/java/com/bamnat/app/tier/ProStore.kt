package com.bamnat.app.tier

import android.content.Context
import com.bamnat.core.tier.AdminLink
import com.bamnat.core.tier.AdRules
import com.bamnat.core.tier.PlaySub
import com.bamnat.core.tier.ProSources
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * [MON-03] The 프로 subscription as this phone last knew it: from Play on the phone, and from the
 * server when it holds the Play key ([verified]). Play on the phone gives no end date, so a phone-only
 * record lasts [PHONE_HOLD] after Play last confirmed it, to ride out a day without Play.
 */
data class SubRecord(
    val product: String,
    val basePlan: String?,
    val state: PlaySub.State,
    val expiry: Instant?,
    val trial: Boolean,
    val autoRenew: Boolean,
    val purchasedAt: Instant,
    val verified: Boolean,
    val checkedAt: Instant,
    val consentAt: Instant? = null,
    val stoppedAt: Instant? = null,
) {
    fun play(): PlaySub = PlaySub(state, expiry, trial, autoRenew, purchasedAt, verified)

    companion object {
        val PHONE_HOLD: Duration = Duration.ofDays(3)
    }
}

/**
 * [MON-06] When the trial ends: Play's end date when the server read it, otherwise a calendar month
 * after it began, as Play counts P1M.
 */
fun SubRecord.trialEnd(zone: ZoneId): Instant =
    expiry?.takeIf { verified } ?: com.bamnat.core.tier.TrialConsent.trialEnd(purchasedAt, zone)

/**
 * [SRV-07] An answer to the trial question that has not reached the server yet: it waits here and goes
 * at the next sync, with the moment the user tapped, so a "계속 쓸게요" given offline still counts.
 */
data class PendingConsent(
    val answer: String,
    val answeredAt: Instant,
    val product: String,
    val price: String,
    val trialEnd: Instant,
    val shown: String,
) {
    /** Older than the server takes (40 days), it is no use sending. */
    fun stale(now: Instant): Boolean = answeredAt.isBefore(now.minus(Duration.ofDays(35)))
}

/** Everything on this phone that can give it 프로, and what the user was told about it. */
data class ProState(
    val sub: SubRecord? = null,
    val passes: Passes? = null,
    /** [MON-07] The day a rewarded ad was watched; it opens the pro features until midnight. */
    val dayPass: LocalDate? = null,
    /** [MON-06] The user's own answer before the trial turns paid, kept here and on the server. */
    val trialAnswer: String? = null,
    val trialAnswerAt: Instant? = null,
    /** [MON-08] The recovery code made on this phone, kept for the backup file. */
    val code: String? = null,
    /** A recovery code from a backup, waiting to move its passes here. */
    val pendingClaim: String? = null,
    /** [MON-10] The 정식 출시 thank-you was shown. */
    val thanksSeen: Boolean = false,
    /** [MON-06] The trial whose day-23 notice went out, by its start. */
    val trialNoticeFor: Instant? = null,
    /** 1.38 (경쟁 앱 조사: 체험 뒤 자동 결제 불만): the trial whose last notice, 3 days before it turns paid, went out. */
    val trialFinalFor: Instant? = null,
    /** [SRV-07] The trial answer still to send. */
    val pendingConsent: PendingConsent? = null,
    /** 1.36 841: each 1년 이용권 bought (Play's order id) and when; they stack ([com.bamnat.core.tier.YearPass.until]). */
    val yearPasses: Map<String, Instant> = emptyMap(),
    /** 840·841: the end whose 7-days-before notice went out (a yearly renewal or a 1년 이용권). */
    val endNoticeFor: Instant? = null,
    /** 1.38 Phase 3 요금: the 팀·직원 요금제 subscriptions Play reports, by product ([com.bamnat.core.tier.WorkPlanKind.product]). */
    val work: Map<String, SubRecord> = emptyMap(),
    /** 1.38 Phase 3 요금: 1년 이용권 of a band, by order: (pass product, bought at). */
    val workPasses: Map<String, Pair<String, Instant>> = emptyMap(),
) {
    val yearPassUntil: Instant? get() = com.bamnat.core.tier.YearPass.until(yearPasses.values)

    /**
     * 1.38 Phase 3 요금: the largest band of [kind] live at [now], from a subscription (its base plan names the band) or a 1년 이용권
     * (stacked per band). Null when none.
     */
    fun hold(kind: com.bamnat.core.tier.WorkPlanKind, now: Instant, table: com.bamnat.core.tier.WorkPlanTable): com.bamnat.core.tier.WorkHold? {
        val subs = work[kind.product]?.takeIf { it.play().entitled(now) }?.let { r ->
            table.capOf(kind, r.basePlan)?.let { com.bamnat.core.tier.WorkHold(it, r.expiry, trial = r.trial) }
        }
        val passes = workPasses.values.filter { com.bamnat.core.tier.WorkPlanKind.ofProduct(it.first) == kind }.groupBy({ it.first }, { it.second })
            .mapNotNull { (product, at) ->
                val until = com.bamnat.core.tier.WorkPlanRules.passUntil(at) ?: return@mapNotNull null
                table.capOf(kind, product)?.takeIf { now < until }?.let { com.bamnat.core.tier.WorkHold(it, until, pass = true) }
            }
        return (listOfNotNull(subs) + passes).maxWithOrNull(compareBy({ it.cap }, { it.until ?: Instant.MAX }))
    }

    /** 1.38 Phase 3 요금: the latest end of any live 팀·직원 요금제, which includes 개인 프로. */
    fun workUntil(now: Instant): Instant? {
        val subs = work.values.filter { it.play().entitled(now) }.map { it.expiry ?: now.plus(SubRecord.PHONE_HOLD) }
        val passes = workPasses.values.groupBy({ it.first }, { it.second }).mapNotNull { com.bamnat.core.tier.WorkPlanRules.passUntil(it.value) }
        return (subs + passes).filter { now < it }.maxOrNull()
    }

    /** [FLAG-08] [live] is the `billing` row on this phone (the retired 정식 출시 switch). */
    fun sources(config: LaunchConfig, installedAt: Instant?, zone: ZoneId, live: Boolean, now: Instant = com.bamnat.core.time.Today.instant()): ProSources = ProSources(
        billingLive = live,
        subscription = sub?.play(),
        passUntil = passes?.until,
        admin = passes?.admin?.let { AdminLink(it.approved, passes.checkedAt) },
        installedAt = installedAt,
        launchedAt = config.launchedAt,
        thanksMonths = config.thanksMonths,
        dayPassUntil = dayPass?.let { AdRules.dayPassUntil(it, zone) },
        sharedUntil = passes?.sharedUntil,
        yearPassUntil = yearPassUntil,
        workUntil = workUntil(now),
    )

    /** [MON-06] Consent to the paid month after the trial: the server's record, or the answer given here. */
    val consentAt: Instant? get() = askSub?.consentAt ?: trialAnswerAt?.takeIf { trialAnswer == "keep" && (askSub == null || !it.isBefore(askSub!!.purchasedAt)) }

    /**
     * 1.38 Phase 3 요금: the subscription the 체험 끝 question is about. A 팀·직원 요금제 has its own free month on the yearly plan, and
     * 전자상거래법 제13조 6항 asks for it the same way; the 프로 one comes first while it is in its trial.
     */
    val askSub: SubRecord? get() = sub?.takeIf { it.trial } ?: work.values.filter { it.trial }.minByOrNull { it.purchasedAt } ?: sub
}

/** [MON-02] Where [ProState] is kept: this phone only, apart from the recovery code in the backup. */
class ProStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("pro", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<ProState> = _state.asStateFlow()
    val current: ProState get() = _state.value

    fun setSub(s: SubRecord?) = edit { it.copy(sub = s) }
    fun setPasses(p: Passes) = edit { it.copy(passes = p) }
    fun dayPass(day: LocalDate) = edit { it.copy(dayPass = day) }
    fun answer(answer: String, at: Instant, toSend: PendingConsent? = null) =
        edit { it.copy(trialAnswer = answer, trialAnswerAt = at, pendingConsent = toSend ?: it.pendingConsent) }
    /** [SRV-07] The server kept [sent] (or will never take it): it leaves the queue unless a newer answer took its place. */
    fun consentSent(sent: PendingConsent) = edit { s -> if (s.pendingConsent == sent) s.copy(pendingConsent = null) else s }
    fun setCode(code: String?) = edit { it.copy(code = code) }
    fun claimLater(code: String?) = edit { it.copy(pendingClaim = code) }
    fun thanksSeen() = edit { it.copy(thanksSeen = true) }
    fun trialNoticed(start: Instant) = edit { it.copy(trialNoticeFor = start) }
    fun trialFinalNoticed(start: Instant) = edit { it.copy(trialFinalFor = start) }
    /** 841: a 1년 이용권 Play reports, by its order; the same order twice counts once. */
    fun addYearPass(order: String, at: Instant) { if (current.yearPasses[order] != at) edit { it.copy(yearPasses = it.yearPasses + (order to at)) } }
    fun endNoticed(end: Instant) = edit { it.copy(endNoticeFor = end) }
    fun removeYearPass(order: String) { if (order in current.yearPasses) edit { it.copy(yearPasses = it.yearPasses - order) } }
    /** 1.38 Phase 3 요금: a 팀·직원 요금제 subscription as Play (and the server) said now; null when it is gone. */
    fun setWork(product: String, r: SubRecord?) = edit { it.copy(work = if (r == null) it.work - product else it.work + (product to r)) }
    fun addWorkPass(order: String, product: String, at: Instant) {
        if (current.workPasses[order] != (product to at)) edit { it.copy(workPasses = it.workPasses + (order to (product to at))) }
    }
    fun removeWorkPass(order: String) { if (order in current.workPasses) edit { it.copy(workPasses = it.workPasses - order) } }

    /** [MON-08] For the backup: the recovery code and what was already shown, never the purchase. */
    fun toBackup(): JSONObject = JSONObject().put("code", current.code ?: JSONObject.NULL).put("thanksSeen", current.thanksSeen)

    /** A backup's code moves its passes here at the next sync, unless it is this phone's own. */
    fun fromBackup(o: JSONObject) {
        val code = if (o.isNull("code")) null else o.optString("code").takeIf { it.isNotBlank() }
        edit { s ->
            s.copy(
                thanksSeen = s.thanksSeen || o.optBoolean("thanksSeen"),
                code = code ?: s.code,
                pendingClaim = code?.takeIf { it != s.code } ?: s.pendingClaim,
            )
        }
    }

    private fun edit(change: (ProState) -> ProState) {
        _state.update(change)
        prefs.edit().putString(KEY, write(_state.value).toString()).apply()
    }

    private fun read(): ProState = runCatching { parse(JSONObject(prefs.getString(KEY, null) ?: return ProState())) }.getOrDefault(ProState())

    companion object {
        private const val KEY = "state"

        private fun JSONObject.instant(k: String): Instant? = if (isNull(k) || !has(k)) null else Instant.parse(getString(k))
        private fun JSONObject.text(k: String): String? = if (isNull(k) || !has(k)) null else getString(k)
        private fun Instant?.json(): Any = this?.toString() ?: JSONObject.NULL

        private fun subJson(r: SubRecord): JSONObject =
            JSONObject().put("product", r.product).put("basePlan", r.basePlan ?: JSONObject.NULL).put("state", r.state.name)
                .put("expiry", r.expiry.json()).put("trial", r.trial).put("autoRenew", r.autoRenew).put("purchasedAt", r.purchasedAt.toString())
                .put("verified", r.verified).put("checkedAt", r.checkedAt.toString()).put("consentAt", r.consentAt.json()).put("stoppedAt", r.stoppedAt.json())

        private fun subOf(r: JSONObject): SubRecord = SubRecord(
            product = r.getString("product"), basePlan = r.text("basePlan"),
            state = PlaySub.State.entries.firstOrNull { it.name == r.optString("state") } ?: PlaySub.State.PENDING,
            expiry = r.instant("expiry"), trial = r.optBoolean("trial"), autoRenew = r.optBoolean("autoRenew"),
            purchasedAt = Instant.parse(r.getString("purchasedAt")), verified = r.optBoolean("verified"),
            checkedAt = Instant.parse(r.getString("checkedAt")), consentAt = r.instant("consentAt"), stoppedAt = r.instant("stoppedAt"),
        )

        internal fun write(s: ProState): JSONObject = JSONObject()
            .put("sub", s.sub?.let(::subJson) ?: JSONObject.NULL)
            .put("passes", s.passes?.let { p ->
                JSONObject().put("until", p.until.json()).put("waitingMonths", p.waitingMonths)
                    .put("items", JSONArray(p.items.map { JSONObject().put("months", it.months).put("at", it.at.toString()).put("why", it.why).put("what", it.what) }))
                    .put("deferred", p.deferred).put("code", p.hasCode).put("checkedAt", p.checkedAt.toString())
                    .put("shared", p.sharedUntil?.let { JSONObject().put("until", it.toString()) } ?: JSONObject.NULL)
                    .put("waitingDays", p.waitingDays).put("familyPaused", p.familyPaused)
                    .put("pending", JSONArray(p.pending.map { JSONObject().put("months", it.months).put("why", it.why).put("what", it.what) }))
                    .put("earned", JSONArray(p.earned.map { JSONObject().put("days", it.days).put("at", it.at.toString()).put("why", it.why) }))
                    .put("admin", p.admin?.let { JSONObject().put("status", it.status).put("name", it.name).put("owner", it.owner)
                        .put("previews", org.json.JSONArray(it.previews)).put("family", it.family) } ?: JSONObject.NULL)
            } ?: JSONObject.NULL)
            .put("dayPass", s.dayPass?.toString() ?: JSONObject.NULL)
            .put("trialAnswer", s.trialAnswer ?: JSONObject.NULL).put("trialAnswerAt", s.trialAnswerAt.json())
            .put("code", s.code ?: JSONObject.NULL).put("pendingClaim", s.pendingClaim ?: JSONObject.NULL)
            .put("thanksSeen", s.thanksSeen).put("trialNoticeFor", s.trialNoticeFor.json()).put("trialFinalFor", s.trialFinalFor.json())
            .put("yearPasses", JSONObject().apply { s.yearPasses.forEach { (k, v) -> put(k, v.toString()) } })
            .put("endNoticeFor", s.endNoticeFor.json())
            .put("work", JSONObject().apply { s.work.forEach { (k, r) -> put(k, subJson(r)) } })
            .put("workPasses", JSONObject().apply { s.workPasses.forEach { (k, v) -> put(k, JSONObject().put("product", v.first).put("at", v.second.toString())) } })
            .put("pendingConsent", s.pendingConsent?.let { q ->
                JSONObject().put("answer", q.answer).put("answeredAt", q.answeredAt.toString()).put("product", q.product)
                    .put("price", q.price).put("trialEnd", q.trialEnd.toString()).put("shown", q.shown)
            } ?: JSONObject.NULL)

        internal fun parse(o: JSONObject): ProState = ProState(
            sub = o.optJSONObject("sub")?.let(::subOf),
            work = o.optJSONObject("work")?.let { m -> m.keys().asSequence().mapNotNull { k -> runCatching { k to subOf(m.getJSONObject(k)) }.getOrNull() }.toMap() }.orEmpty(),
            workPasses = o.optJSONObject("workPasses")?.let { m ->
                m.keys().asSequence().mapNotNull { k -> runCatching { val v = m.getJSONObject(k); k to (v.getString("product") to Instant.parse(v.getString("at"))) }.getOrNull() }.toMap()
            }.orEmpty(),
            passes = o.optJSONObject("passes")?.let { TierClient.readPasses(it) },
            dayPass = o.text("dayPass")?.let(LocalDate::parse),
            trialAnswer = o.text("trialAnswer"), trialAnswerAt = o.instant("trialAnswerAt"),
            code = o.text("code"), pendingClaim = o.text("pendingClaim"),
            thanksSeen = o.optBoolean("thanksSeen"), trialNoticeFor = o.instant("trialNoticeFor"), trialFinalFor = o.instant("trialFinalFor"),
            yearPasses = o.optJSONObject("yearPasses")?.let { m -> m.keys().asSequence().mapNotNull { k -> runCatching { k to Instant.parse(m.getString(k)) }.getOrNull() }.toMap() }.orEmpty(),
            endNoticeFor = o.instant("endNoticeFor"),
            pendingConsent = o.optJSONObject("pendingConsent")?.let { q ->
                runCatching {
                    PendingConsent(q.getString("answer"), Instant.parse(q.getString("answeredAt")), q.getString("product"),
                        q.getString("price"), Instant.parse(q.getString("trialEnd")), q.optString("shown"))
                }.getOrNull()
            },
        )
    }
}
