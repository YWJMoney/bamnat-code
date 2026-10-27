package com.bamnat.app.tier

import com.bamnat.app.data.SettingsRepository
import com.bamnat.core.tier.Access
import com.bamnat.core.tier.ProAccess
import com.bamnat.core.tier.ProReason
import kotlinx.coroutines.CoroutineScope
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
 * [MON-01] Two tiers for sale (결정 3): 무료 and 프로. 리더 (team rosters) waits for Phase 3. A '플러스'
 * saved by an older version reads as 프로.
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
    /** Phase 3 (M24), not sold yet. */
    TEAM_ROSTER(Tier.LEADER, "팀 근무표 만들기"),
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
) {
    fun allows(feature: Feature): Boolean = !live || feature in free || tier >= feature.minTier

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
) {
    private val tick = MutableStateFlow(0L)
    private val preview = settings.settings.map { it.previewTier }.distinctUntilChanged()

    val state: StateFlow<Gate> = combine(preview, remote.config, pro.state, tick, flags.byKey) { p, config, sources, _, rows -> gate(p, config, sources, rows) }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, gate(settings.current.previewTier, remote.current, pro.current, flags.byKey.value))

    fun allows(feature: Feature): Boolean = state.value.allows(feature)

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
        if (previewTier != null && (debug || !live)) {
            val on = previewTier >= Tier.PRO
            return Gate(live = true, tier = previewTier, access = Access(on, ProReason.PREVIEW), preview = true)
        }
        val access = ProAccess.resolve(s.sources(config, installedAt(), zone(), live), clock(), zone())
        return Gate(live = live, tier = if (access.pro) Tier.PRO else Tier.FREE, access = access, free = free)
    }
}
