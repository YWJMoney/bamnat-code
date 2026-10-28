package com.bamnat.core.guide

import com.bamnat.core.work.WorkStyle

/**
 * [FIT-01] A screen that lists parts or tools the user can see, each of which suits some kinds of work.
 * 1.25 묶음 Y (U3): the screens used to list every part for every calendar, so an investor's 화면 꾸미기
 * had 근무 횟수 줄 and its 도구 had 근무 주기 설정.
 */
enum class FitSurface(val label: String) {
    CALENDAR_PARTS("달력 화면 꾸미기"),
    CALENDAR_TOOLS("달력 도구"),
    STATS_PARTS("근무·급여 화면 꾸미기"),
    MONEY_PARTS("돈 화면 꾸미기"),
}

/**
 * [FIT-01] One part or tool of a [surface] and the kinds of work it suits: those of its [feature] in
 * [FeatureRegistry] when it is one, else its own [own] ([ANY] for every kind).
 */
data class FitItem(val surface: FitSurface, val id: String, private val own: Set<WorkStyle> = ANY, val feature: String? = null) {
    val styles: Set<WorkStyle> get() = feature?.let { FeatureRegistry.get(it)?.styles } ?: own

    /** Kept with the calendar when the user adds it: "CALENDAR_TOOLS:pattern". */
    val key: String get() = "${surface.name}:$id"

    /** A main calendar from before kinds of work were asked is a shift calendar, as in [AppFeature.fits]. */
    fun fits(style: WorkStyle?): Boolean = styles.isEmpty() || (style ?: WorkStyle.SHIFT) in styles

    companion object {
        val ANY: Set<WorkStyle> = emptySet()
    }
}

/**
 * [FIT-01][FIT-02] Which parts and tools each kind of work sees, in one table every screen reads. A part
 * that does not suit the calendar is not deleted: it waits under '이 달력에 안 맞아 숨긴 기능 · 더 보기',
 * and one the user adds there is kept for that calendar ([added] holds its [FitItem.key]).
 * A screen's part missing from here is shown everywhere, and [FitTableTest] fails until it is listed.
 */
object FitTable {
    private val SHIFT = WorkStyle.SHIFT
    private val DAY = WorkStyle.DAY
    private val HOURLY = WorkStyle.HOURLY
    private val FIELD = WorkStyle.FIELD
    private val SHOP = WorkStyle.BUSINESS
    private val INVEST = WorkStyle.INVEST
    private val PAID = setOf(SHIFT, DAY, HOURLY)
    private val ANY = FitItem.ANY

    private fun cal(id: String, styles: Set<WorkStyle> = ANY, feature: String? = null) = FitItem(FitSurface.CALENDAR_PARTS, id, styles, feature)
    private fun tool(id: String, styles: Set<WorkStyle> = ANY, feature: String? = null) = FitItem(FitSurface.CALENDAR_TOOLS, id, styles, feature)
    private fun stats(id: String, styles: Set<WorkStyle> = ANY) = FitItem(FitSurface.STATS_PARTS, id, styles)
    private fun money(id: String, styles: Set<WorkStyle> = ANY) = FitItem(FitSurface.MONEY_PARTS, id, styles)

    val all: List<FitItem> = listOf(
        // 달력 화면 꾸미기 (the app's CalendarPart ids).
        cal("ask"), cal("journal"),
        cal("todayFlow", feature = "today.flow"),
        cal("todayWork", setOf(FIELD, SHOP, INVEST)),
        cal("summary"),
        cal("counts", PAID),
        cal("dayCard"),
        cal("hours", PAID),
        cal("rest", feature = "shift.restRules"),
        cal("memos"),
        // 달력 도구 (the tool sheet).
        tool("edit"),
        tool("teams", feature = "shift.teams"),
        tool("typeMonth", setOf(SHIFT, HOURLY)),
        tool("share"),
        tool("year"),
        tool("receive", PAID),
        tool("pattern", setOf(SHIFT, DAY)),
        tool("newCalendar"), tool("customize"), tool("viewSettings"), tool("help"),
        // 근무·급여 화면 꾸미기 (StatsPart): hours and pay are for the kinds paid by the hour or the month.
        stats("tiles", PAID), stats("pay", PAID), stats("kinds", PAID), stats("three", PAID), stats("weeks", PAID),
        stats("trend", PAID), stats("compare", PAID), stats("year", PAID),
        // 돈 화면 꾸미기 (MoneyPart).
        money("hero"), money("new"), money("recent"), money("income"), money("owed"),
        money("work", setOf(FIELD, SHOP)),
        money("all"), money("budget"), money("other"), money("upcoming"), money("repeat"), money("accounts"), money("auto"),
        // E 1.32 [LEDX-07][LEDX-09][LEDX-10] 카드 문턱 is for pay that has 총급여.
        money("pace"), money("subs"), money("card25", setOf(SHIFT, DAY, HOURLY)),
        // F 1.32 [TAXP-04][TAXP-05][TAXP-06][TAXP-10] 근무별 소비 needs a roster; 매출 is the shop's; 3.3% the field's.
        money("cardbill"), money("shiftspend", setOf(SHIFT)), money("salessplit", setOf(SHOP)), money("freelance", setOf(FIELD)),
    )

    private val byKey: Map<String, FitItem> = all.associateBy { it.key }

    fun item(surface: FitSurface, id: String): FitItem? = byKey["${surface.name}:$id"]

    fun of(surface: FitSurface): List<FitItem> = all.filter { it.surface == surface }

    /** On the screen: it suits [style], the user added it to this calendar, or the table does not know it. */
    fun visible(surface: FitSurface, id: String, style: WorkStyle?, added: Set<String>): Boolean {
        val item = item(surface, id) ?: return true
        return item.fits(style) || item.key in added
    }

    /** What '더 보기' offers: the parts of [surface] that do not suit [style], added or not. */
    fun others(surface: FitSurface, style: WorkStyle?): List<FitItem> = of(surface).filterNot { it.fits(style) }

    /** The ids [style] sees on [surface] with nothing added, in the table's order. */
    fun shown(surface: FitSurface, style: WorkStyle?): List<String> = of(surface).filter { it.fits(style) }.map { it.id }
}
