package com.bamnat.app.tier

import com.bamnat.app.data.SettingsRepository
import com.bamnat.core.tier.Access
import com.bamnat.core.tier.ProAccess
import com.bamnat.core.tier.ProReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.Instant
import java.time.ZoneId
import com.bamnat.core.time.Today

/**
 * [MON-01] Two tiers for sale (결정 3): 무료 and 프로. 1.38 Phase 3 요금 (사용자 2026-11-05 20:22): 팀·직원 요금제 are not tiers but
 * bands held beside them ([Gate.holds]); each includes 프로. [LEADER] is the 알람 점검 preview of "every band open". A '플러스' saved by
 * an older version reads as 프로.
 */
enum class Tier(val label: String) {
    FREE("무료"),
    PRO("프로"),
    LEADER("리더"),
    ;

    companion object {
        fun parse(name: String?): Tier? = when (name) {
            null -> null
            "PLUS" -> PRO
            else -> entries.firstOrNull { it.name == name }
        }
    }
}

/**
 * [MON-01] What 프로 adds (수익화 확정 2-1, 5-2). Everything else is free, the month's pay estimate
 * included (결정 2): 받을 돈 [RECV-02], the work tools, the watch, 출퇴근 자동 기록, the duty roster
 * photo, the attendance sheet and quotes stay free too.
 */
enum class Feature(val minTier: Tier, private val ownLabel: String? = null) {
    NO_ADS(Tier.PRO),
    /** [PAYS-04] 명세서로 맞춰 보기 and the places it points at. */
    PAYSLIP_MATCH(Tier.PRO),
    PAY_RULE_IMPORT(Tier.PRO),
    /** [PAYX-03] A value changed over a chosen range of months, and a raise worked out over past years. */
    PAY_BULK_EDIT(Tier.PRO),
    /** [TIER-05] This month's records and totals stay free; comparisons, trends and breakdowns are pro. */
    MONEY_STATS(Tier.PRO),
    /** [MCAL-07] The main calendar and one added calendar are free; a second added calendar on is pro. */
    MORE_CALENDARS(Tier.PRO),
    // [PROV-08] 1.20.0 filled the four pillars (수익화 확정 5-2); each is its own row so the server can open one.
    /** [PROV-01] A month's or a year's pay as a PDF and a CSV, with how each line was worked out. */
    PAY_REPORT(Tier.PRO),
    /** [PROV-02] This month beside last month and the same month a year ago, and the year month by month. */
    WORK_COMPARE(Tier.PRO),
    /** [FEAT-07] The shifts written to a calendar of their own on the phone, which the phone's calendar apps show. */
    DEVICE_CALENDAR(Tier.PRO),
    /** [FEAT-15] An address other calendar apps subscribe to, beside a share link. */
    ICS_FEED(Tier.PRO),
    /** [PROV-03] A password-protected backup written to a file of the user's choosing every day. */
    AUTO_BACKUP(Tier.PRO),
    /** [PROV-04] Up to five links per calendar, each with its own parts and period, and 180 days or a year. */
    MORE_LINKS(Tier.PRO),
    /** [PROV-05] A month's and a year's roster as an A4 PDF to print. */
    ROSTER_PDF(Tier.PRO),
    /** [PROV-06][PROV-07] A task before the alarm stops, and a check a few minutes later that the user is up. */
    SURE_ALARM(Tier.PRO),
    /** [LEAVE-05] 미사용 연차수당: what the days left are worth, and a 정산 row on the 연차 원장. */
    LEAVE_PAYOUT(Tier.PRO),
    /** [TAX-02][TAX-05] 1.25 W: 세전·세후 learned from real pay, and a 사업자's year of tax (결정 W-D4). */
    TAX_FORECAST(Tier.PRO),
    /** [FREE-10] 1.35 꾸미기 혜택: themes saved as many outfits and worn with no ad and no 30 days. */
    THEME_WARDROBE(Tier.PRO),
    /** [PRF-12] 1.36 창작자 꾸미기: 프로필 표지 그림, 대표 작품 6개, 작품 통계 28일 날별 그래프. 팔로우·보기는 늘 무료. */
    CREATOR_PRO(Tier.PRO),
    /** 1.39 [TASK-07] 할 일 위젯: the first on the home screen is free, a second and more are 프로. */
    MORE_TASK_WIDGETS(Tier.PRO),
    /**
     * 1.39 [FX-01] 기능 작품 (core formula/FxPro): the 기능 장치 that came with 1.39 — 값 칸, 단추 위젯·빠른 설정·바로가기, 알림 단추 — put
     * in and used. Making and previewing them is free, and each work has one 하루 써 보기 ([com.bamnat.app.logic.FxAccess]).
     */
    FX_WORKS(Tier.PRO),
    ;

    /** [NXT-03] 1.29 AU: a 프로 feature's name is core's ProFeatures table, the one the server and 진단 use for free.<FEATURE>. */
    val label: String get() = ownLabel ?: com.bamnat.core.flags.ProFeatures.name(name)
}

/**
 * [MON-02] What the gate knows now. Before the `billing` row is on ([live] false; [FLAG-08] was the 정식 출시 switch) every feature is open,
 * as in every version so far; [access] says why this phone has 프로, and until when.
 */
data class Gate(
    val live: Boolean = false,
    val tier: Tier = Tier.PRO,
    val access: Access = Access(true, ProReason.NOT_LAUNCHED),
    /** Pro features the server opened to everyone. */
    val free: Set<Feature> = emptySet(),
    /** A tester's preview, not the real state. */
    val preview: Boolean = false,
    /**
     * [MON-13] 1.36 839 묶음 7일권: the four bundles' days on this phone (first week from first use, an ad's or a missing ad's
     * 7 days), and the time they were read at.
     */
    val leases: com.bamnat.core.tier.FeatureLease.State = com.bamnat.core.tier.FeatureLease.State(),
    val now: Long = 0L,
    /** 1.38 Phase 3 요금: the band of each 팀·직원 요금제 this phone holds now (an admin phone or the 리더 preview holds every band). */
    val holds: Map<com.bamnat.core.tier.WorkPlanKind, com.bamnat.core.tier.WorkHold> = emptyMap(),
    /** 1.38 Phase 3 요금: the bands, prices and free taste the server set. */
    val plans: com.bamnat.core.tier.WorkPlanTable = com.bamnat.core.tier.WorkPlanTable.DEFAULT,
) {
    fun hold(kind: com.bamnat.core.tier.WorkPlanKind): com.bamnat.core.tier.WorkHold? = holds[kind]

    /** 1.38 Phase 3 요금: may this leader confirm [month] of a team of [members] that has confirmed [confirmed] before. */
    fun teamConfirm(confirmed: Set<java.time.YearMonth>, month: java.time.YearMonth, members: Int) =
        com.bamnat.core.tier.WorkPlanRules.teamConfirm(plans, live, hold(com.bamnat.core.tier.WorkPlanKind.TEAM), confirmed, month, members)

    fun moreTeams(teams: Int) = com.bamnat.core.tier.WorkPlanRules.moreTeams(plans, live, hold(com.bamnat.core.tier.WorkPlanKind.TEAM), teams)

    fun staff(count: Int) = com.bamnat.core.tier.WorkPlanRules.staff(plans, live, hold(com.bamnat.core.tier.WorkPlanKind.STAFF), count)

    fun allows(feature: Feature): Boolean = !live || feature in free || tier >= feature.minTier || onTrial(feature)

    /** [MON-13] 839: the bundle [feature] is in, or null for an always-on 프로 feature (no ad opens it). */
    fun bundle(feature: Feature): com.bamnat.core.tier.FeatureLease.Bundle? = com.bamnat.core.tier.FeatureLease.Bundle.of(feature.name)

    /** [MON-13] When [feature]'s bundle closes, while it is open. */
    fun trialUntil(feature: Feature): Long? = bundle(feature)?.let { leases.until[it] }?.toEpochMilli()?.takeIf { now < it }

    /** [MON-13] Its bundle was never opened here, so the first use opens a week. */
    fun canTry(feature: Feature): Boolean {
        val b = bundle(feature) ?: return false
        return b !in leases.firstGiven && trialUntil(feature) == null && live && tier < feature.minTier
    }

    private fun onTrial(feature: Feature) = trialUntil(feature) != null

    /**
     * 1.36 사용자 결정(공유 링크 기간): links stay open while this phone really has 프로 after launch (a one-day pass
     * is not 프로 kept); before launch every feature is open but no link is kept open by itself.
     */
    val keepsLinks: Boolean get() = live && !preview && access.pro && tier >= Tier.PRO && access.reason != com.bamnat.core.tier.ProReason.DAY_PASS

    /** 1.36 사용자 결정(프로 나눔 1인): only 프로 bought on Play counts, not a trial, pass, thank-you or admin phone. */
    val boughtPro: Boolean get() = live && !preview && access.pro &&
        (access.reason == com.bamnat.core.tier.ProReason.SUBSCRIPTION || access.reason == com.bamnat.core.tier.ProReason.GRACE ||
            access.reason == com.bamnat.core.tier.ProReason.PASS_1Y)

    /** 프로 badges and 'pro' notes show only where the feature is actually locked. */
    fun locked(feature: Feature): Boolean = !allows(feature)

    companion object {
        val OPEN = Gate()
    }
}

/**
 * The one place that answers "can this user use X" [TIER-02][MON-02]: the 정식 출시 switch from the
 * company server, then [ProAccess] over the Play subscription, an admin phone, reward passes, the
 * testers' thank-you and a day opened by a rewarded ad. Screens read [state] and do not change when a
 * new way to get 프로 comes in.
 */
class FeatureGate(
    settings: SettingsRepository,
    private val remote: RemoteConfigStore,
    private val pro: ProStore,
    scope: CoroutineScope,
    /** [FLAG-08] The dark-merge rows: `billing` is the retired 정식 출시 switch, `free.*` the features opened to everyone. */
    private val flags: com.bamnat.app.flags.Flags,
    private val installedAt: () -> Instant?,
    private val debug: Boolean = com.bamnat.app.BuildConfig.DEBUG,
    private val clock: () -> Instant = Today::instant,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    /** [MON-13] The bundles' days: first weeks from first use, an ad's 7 days. */
    private val trials: FeatureTrials = FeatureTrials(null),
) {
    private val tick = MutableStateFlow(0L)
    private val preview = settings.settings.map { it.previewTier }.distinctUntilChanged()

    val state: StateFlow<Gate> = combine(preview, remote.config, pro.state, tick, flags.byKey) { p, config, sources, _, rows -> gate(p, config, sources, rows) }
        .combine(trials.all) { g, t -> withTrials(g, t) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, withTrials(gate(settings.current.previewTier, remote.current, pro.current, flags.byKey.value), trials.all.value))

    // The 프로 둘러보기 preview (무료) shows the free weeks too, so a tester sees them as a user would.
    private fun withTrials(g: Gate, t: com.bamnat.core.tier.FeatureLease.State): Gate = g.copy(leases = t, now = clock().toEpochMilli())

    init {
        // [MON-13] 839: a bundle's days ending while the app is open (or the alarm about to ring) closes it at that moment.
        scope.launch {
            trials.all.collectLatest { s ->
                // 1.36 PC Y15 (처음 7일이 지나도 세후 예상이 안 잠겨요): one long wait missed the end when the clock moved on without
                // it — the phone's time changed, or days asleep, which the wait does not count. It looks at the clock every minute.
                while (true) {
                    val now = clock()
                    // 1.36.1: with today's two ads used, 00:00 too, so a screen left open offers the ad again then.
                    val next = com.bamnat.core.tier.FeatureLease.nextChange(s, now, zone()) ?: break
                    kotlinx.coroutines.delay(minOf(java.time.Duration.between(now, next).toMillis() + 1_000, END_CHECK_MS))
                    if (!clock().isBefore(next)) tick.value++
                }
            }
        }
    }

    /**
     * [MON-13] The first use of a trial feature while it is locked: its week starts now. True when it is open (프로, a week
     * going on, or one just started); false when it asks for 프로.
     */
    /** [MON-13] A trial's week as if begun at [at] (tests: one used up long ago). */
    internal fun startTrial(feature: Feature, at: Long) = trials.start(feature, at)

    /** [MON-13] Debug builds' 미리 보기: the free weeks start again. */
    fun resetTrials() = trials.clear()

    fun tryOrAsk(feature: Feature): Boolean {
        val g = fresh()
        if (g.allows(feature)) return true
        if (!g.canTry(feature)) return false
        trials.start(feature, clock().toEpochMilli())
        return true
    }

    /** [MON-13] 839: an ad watched to the end ([com.bamnat.core.tier.FeatureLease.Why.AD]) or one that did not come: [bundle] for 7 days. */
    fun grant(bundle: com.bamnat.core.tier.FeatureLease.Bundle, why: com.bamnat.core.tier.FeatureLease.Why, reason: String? = null) {
        trials.grant(bundle, clock(), clock().atZone(zone()).toLocalDate(), why, reason)
    }

    /** 840: back after a month away, [bundle] for 7 days ([com.bamnat.core.tier.ProOfferTimes.Time.BACK]). */
    fun back(bundle: com.bamnat.core.tier.FeatureLease.Bundle) = trials.back(bundle, clock())

    /** 1.36.1 광고 하루 두 편: a theme's ad watched to the end uses one of today's two. */
    fun countThemeAd() = trials.themeAd(clock().atZone(zone()).toLocalDate())

    /** [MON-13] 839: the offer closed without an ad. */
    fun decline(bundle: com.bamnat.core.tier.FeatureLease.Bundle) = trials.decline(bundle, clock())

    /** Read with the time now, so a bundle that just ended is closed (the alarm asks at the moment it rings). */
    fun allows(feature: Feature): Boolean = fresh().allows(feature)

    private fun fresh(): Gate = state.value.copy(now = clock().toEpochMilli())

    /** Works the answer out again: on coming back to the app, and when a pass or a day ends. */
    fun refresh() {
        // [FLAG-07] Coming back to the app looks at every dark-merge row again too.
        flags.refresh()
        tick.value++
    }

    private fun gate(previewTier: Tier?, config: LaunchConfig, s: ProState, rows: Map<String, com.bamnat.core.flags.FlagState>): Gate {
        // [FLAG-08] The `billing` row in place of the 정식 출시 switch; `free.<FEATURE>` rows open a 프로 feature, never lock a free one.
        val live = rows[com.bamnat.core.flags.FlagCatalog.BILLING]?.on == true
        val free = Feature.entries.filter { it.minTier > Tier.FREE && rows[com.bamnat.core.flags.FlagCatalog.FREE_PREFIX + it.name]?.on == true }.toSet()
        // [MON-02] The alarm check screen's preview counts before the launch and in debug builds only.
        // S 1.32 [SECX-01] Before the launch only on an approved admin phone: a user's phone ignores a stored preview.
        if (previewTier != null && (debug || (!live && s.passes?.admin?.approved == true))) {
            val on = previewTier >= Tier.PRO
            val all = if (previewTier == Tier.LEADER) everyBand() else emptyMap()
            return Gate(live = true, tier = previewTier, access = Access(on, ProReason.PREVIEW), preview = true, holds = all, plans = config.workPlans)
        }
        val now = clock()
        val access = ProAccess.resolve(s.sources(config, installedAt(), zone(), live, now), now, zone())
        val admin = s.passes?.admin?.takeIf { it.approved && java.time.Duration.between(s.passes.checkedAt, now) < ProAccess.ADMIN_CHECK } != null
        val holds = if (admin) everyBand() else com.bamnat.core.tier.WorkPlanKind.entries.mapNotNull { k -> s.hold(k, now, config.workPlans)?.let { k to it } }.toMap()
        return Gate(live = live, tier = if (access.pro) Tier.PRO else Tier.FREE, access = access, free = free, holds = holds, plans = config.workPlans)
    }
}

/** An admin phone and the 리더 preview: every band, no end. */
private fun everyBand() = com.bamnat.core.tier.WorkPlanKind.entries.associateWith { com.bamnat.core.tier.WorkHold(Int.MAX_VALUE, null) }

/** [MON-13] How often an open bundle's end is looked for: a changed clock or a long sleep ends it within this. */
private const val END_CHECK_MS = 60_000L
