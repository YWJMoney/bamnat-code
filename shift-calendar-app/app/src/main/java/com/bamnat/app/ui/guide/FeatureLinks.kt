package com.bamnat.app.ui.guide

import com.bamnat.app.AppContainer
import com.bamnat.app.data.CommutePrefs
import com.bamnat.app.data.GuideStore
import com.bamnat.app.money.CaptureService
import com.bamnat.app.ui.settings.SettingsPage
import com.bamnat.core.guide.AppFeature
import com.bamnat.core.guide.FeatureRegistry
import com.bamnat.core.guide.FeatureRoute
import com.bamnat.core.guide.Off
import com.bamnat.core.guide.Place
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.getAndUpdate

/**
 * [GUIDE-01] Opens a feature where it lives, from the checklist, a tip, the help search or 기능 켜고 끄기.
 * The navigation follows [target] for the calendar on screen; a tool inside a card (두 알바 비교하기,
 * 견적서, 직원 근무표) is named by [focus], which that card takes once it is on screen.
 */
object FeatureLinks {
    private val _target = MutableStateFlow<FeatureRoute?>(null)
    val target: StateFlow<FeatureRoute?> = _target.asStateFlow()
    private val _focus = MutableStateFlow<String?>(null)
    val focus: StateFlow<String?> = _focus.asStateFlow()

    /** [GUIDE-06] A feature was just turned off from its own ⋮: the snackbar says where it comes back. */
    private val _turnedOff = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    val turnedOff: SharedFlow<String> = _turnedOff.asSharedFlow()

    fun open(feature: AppFeature) = open(feature.route)

    fun open(route: FeatureRoute) {
        _focus.value = route.focus.takeIf { !route.place.isSettings }
        _target.value = route
    }

    /** 설정 › 기능 켜고 끄기. */
    fun openSwitches() = open(FeatureRoute(Place.FEATURES))

    fun consume(route: FeatureRoute) {
        _target.compareAndSet(route, null)
    }

    /** True once, for the card whose tool [key] is: that card opens it. */
    fun take(key: String): Boolean = _focus.compareAndSet(key, null)

    private val _today = MutableStateFlow<String?>(null)

    /** [GUIDE-01] A feature of today's day (지금 퇴근, 하루 마감, 공수): the calendar opens [calendar] on today, its day open. */
    fun today(calendar: String) {
        _today.value = calendar
    }

    /** The calendar a feature of today asked for, once; null when a notice opened today instead. */
    fun takeToday(): String? = _today.getAndUpdate { null }

    fun saidOff(name: String) {
        _turnedOff.tryEmit(name)
    }
}

/** [GUIDE-01] The group of settings a settings place opens; null for a place that is not one. */
fun Place.settingsPage(): SettingsPage? = when (this) {
    Place.SETTINGS_WORK -> SettingsPage.WORK
    Place.SETTINGS_ALARM -> SettingsPage.ALARM
    Place.SETTINGS_NOTICES -> SettingsPage.NOTICES
    Place.SETTINGS_PAY -> SettingsPage.PAY
    Place.SETTINGS_CALENDAR -> SettingsPage.CALENDAR
    Place.SETTINGS_DATA -> SettingsPage.DATA
    Place.SETTINGS_PRIVACY -> SettingsPage.PRIVACY
    else -> null
}

/** [GUIDE-01] The tools inside a card that [FeatureLinks.focus] can open. */
val TOOL_KEYS = setOf(
    "offers", "contract", "quit", "quote", "staff",
    // [GUIDE-01] The investor tab's sheets and tabs (InvestScreen).
    "invest.cash", "invest.dividend", "invest.trade", "invest.quotes", "invest.goal", "invest.journal", "invest.event",
    "invest.alarms", "invest.stats", "invest.records", "invest.overview",
    // [CUST-01] 화면 꾸미기 over the calendar (CalendarScreen).
    "customize",
    // [TAX-02] 세금·실수령 card on the 돈 summary (MoneyScreen).
    "tax",
    // [PAYX-48] 월급날 비교 on the stats tab: the card comes into view and asks the amount (WorkTypeCards).
    "pay.gap",
    // [TIER-07] 1.25 묶음 Y: 가계부 탭의 월간 보고서(MoneyScreen), 프로 기능 모아보기에서 열어요.
    "report",
)

/**
 * [GUIDE-05] One switch per feature that can be off, read and set in one place. The app's own switches
 * are in [GuideStore]; a switch that was there before is used as it is, never copied: 오늘의 흐름 and 잠금
 * 화면 오늘 근무 in settings, 출퇴근 자동 기록, a shop's 매출 없으면 휴무, 결제 알림 읽기 and 연차 사용 촉진 알림.
 */
object FeatureSwitches {
    /** The features of [Off.ELSEWHERE], each with its own branch below. */
    val LINKED = setOf("today.flow", "lock.today", "day.commute", "shop.autoClose", "money.auto", "shift.restRules", "privacy.usageStats", "plaza.news", "leave.promotion",
        "alarm.suggest", // O 1.23 (I)
        // 1.32 [WTYP-08] 가계부 cards turned off in 가계부 화면 꾸미기, and the two settings rows they live in.
        "money.cardBill", "money.shiftSpend", "money.subscriptions", "money.cardThreshold", "money.inCells", "pay.depositCompare",
        // 1.32 조사 대조 [LEDX-16][LEDX-17][LEDX-23]
        "money.asks", "money.installPlan", "money.heat", "backup.nudge", "day.leaveTime")

    /** 1.32 [WTYP-08] The 가계부 card each of these features is ([com.bamnat.app.ui.money.MoneyPart]). */
    private val MONEY_CARDS = mapOf("money.cardBill" to "cardbill", "money.shiftSpend" to "shiftspend",
        "money.subscriptions" to "subs", "money.cardThreshold" to "card25", "money.asks" to "ask", "money.installPlan" to "install")

    private fun moneyCardShown(c: AppContainer, card: String) =
        com.bamnat.app.ui.money.moneyParts(c.settingsRepository.current.moneyLayout).firstOrNull { it.part.id == card }?.shown ?: true

    private fun showMoneyCard(c: AppContainer, card: String, on: Boolean) = c.settingsRepository.update { s ->
        val placed = com.bamnat.app.ui.money.moneyParts(s.moneyLayout).map { if (it.part.id == card) it.copy(shown = on) else it }
        s.copy(moneyLayout = com.bamnat.core.layout.PageLayout.of(placed) { p -> p.id }.encode())
    }

    fun isOn(c: AppContainer, feature: AppFeature, calendar: String): Boolean = when (feature.off) {
        null -> true
        Off.CALENDAR, Off.APP -> !GuideStore.get(c.appContext).current.isOff(feature, calendar)
        Off.ELSEWHERE -> when (feature.id) {
            "today.flow" -> c.settingsRepository.current.sleepTips
            "lock.today" -> c.settingsRepository.current.todayShiftNotice
            "day.commute" -> CommutePrefs.get(c.appContext).current.enabled
            "shop.autoClose" -> c.calendars.get(calendar)?.shop?.autoClosed ?: true
            "money.auto" -> CaptureService.isOn(c.appContext) && CaptureService.hasAccess(c.appContext)
            "shift.restRules" -> c.settingsRepository.current.restCheck
            "privacy.usageStats" -> c.settingsRepository.current.shareUsageStats
            "plaza.news" -> c.settingsRepository.current.plazaNews
            // [LEAVE-03] The notice switch in settings and on the 연차 screen.
            "leave.promotion" -> c.settingsRepository.current.leaveNotice
            "alarm.suggest" -> c.settingsRepository.current.alarmSuggest // O 1.23 (I)
            "money.inCells" -> c.settingsRepository.current.spendInCells
            "pay.depositCompare" -> c.settingsRepository.current.payDepositNotice
            "money.heat" -> c.settingsRepository.current.spendHeat
            "backup.nudge" -> c.settingsRepository.current.backupNudge
            "day.leaveTime" -> c.settingsRepository.current.leaveTimeNotice
            in MONEY_CARDS -> moneyCardShown(c, MONEY_CARDS.getValue(feature.id))
            else -> true
        }
    }

    /** The feature is off here, or under a feature that is: it is not shown. */
    fun hidden(c: AppContainer, id: String, calendar: String): Boolean =
        FeatureRegistry.get(id)?.let { f -> !isOn(c, f, calendar) && f.off != Off.ELSEWHERE } ?: false

    /**
     * Turns [feature] on or off. Some can only be turned on where they live (a permission to give, a place to
     * pick): that screen opens instead, and false comes back.
     */
    fun set(c: AppContainer, feature: AppFeature, calendar: String, on: Boolean): Boolean {
        when (feature.off) {
            null -> return false
            Off.CALENDAR, Off.APP -> {
                GuideStore.get(c.appContext).setOn(feature, calendar, on)
                if (feature.id == "widget.lines") c.appScope.launch { com.bamnat.app.widget.WidgetRefresher.refresh(c.appContext) }
            }
            Off.ELSEWHERE -> when (feature.id) {
                "today.flow" -> c.settingsRepository.update { it.copy(sleepTips = on) }
                "lock.today" -> if (on) { FeatureLinks.open(feature); return false } else c.settingsRepository.update { it.copy(todayShiftNotice = false) }
                "day.commute" -> if (on) { FeatureLinks.open(feature); return false } else com.bamnat.app.commute.CommuteRecorder.turnOff(c.appContext)
                "shop.autoClose" -> c.calendars.updateShop(calendar) { it.copy(autoClosed = on) }
                "money.auto" -> if (on) { FeatureLinks.open(feature); return false } else CaptureService.setOn(c.appContext, false)
                "shift.restRules" -> c.settingsRepository.update { it.copy(restCheck = on) }
                // A yes to sending is given where what goes is shown.
                "privacy.usageStats" -> if (on) { FeatureLinks.open(feature); return false } else c.setUsageStats(false)
                "plaza.news" -> c.settingsRepository.update { it.copy(plazaNews = on) }
                "leave.promotion" -> c.settingsRepository.update { it.copy(leaveNotice = on) }
                "alarm.suggest" -> c.settingsRepository.update { it.copy(alarmSuggest = on) } // O 1.23 (I)
                "money.inCells" -> c.settingsRepository.update { it.copy(spendInCells = on) }
                "pay.depositCompare" -> c.settingsRepository.update { it.copy(payDepositNotice = on) }
                "money.heat" -> c.settingsRepository.update { it.copy(spendHeat = on) }
                "backup.nudge" -> c.settingsRepository.update { it.copy(backupNudge = on) }
                "day.leaveTime" -> c.settingsRepository.update { it.copy(leaveTimeNotice = on) }
                in MONEY_CARDS -> showMoneyCard(c, MONEY_CARDS.getValue(feature.id), on)
                else -> { FeatureLinks.open(feature); return false }
            }
        }
        return true
    }
}
