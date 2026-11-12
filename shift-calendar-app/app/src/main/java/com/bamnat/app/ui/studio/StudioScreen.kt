package com.bamnat.app.ui.studio

import androidx.compose.foundation.shape.ZeroCornerSize

import com.bamnat.app.ui.decor.nextPinned
import com.bamnat.app.ui.decor.decor
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.customActions
import androidx.compose.material.icons.filled.History
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Compare
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.RadioButton
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Checkbox
import com.bamnat.app.ui.components.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import com.bamnat.app.ui.parts.partPicking
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.ui.calendar.CalendarPart
import com.bamnat.app.ui.calendar.CalendarViewSettings
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.memo.MemoPart
import com.bamnat.app.ui.money.MoneyPart
import com.bamnat.app.ui.recipe.CompareCanvas
import com.bamnat.app.ui.recipe.CornersEditor
import com.bamnat.app.ui.recipe.PartsEditor
import com.bamnat.app.ui.recipe.ScaledPhone
import com.bamnat.app.ui.recipe.TabsEditor
import com.bamnat.app.ui.recipe.fitsHere
import com.bamnat.app.ui.recipe.partsLine
import com.bamnat.app.ui.settings.AlarmSample
import com.bamnat.app.ui.settings.CellDesigner
import com.bamnat.app.ui.settings.CellWeeks
import com.bamnat.app.ui.settings.ColorMemory
import com.bamnat.app.ui.settings.FreeTarget
import com.bamnat.app.ui.settings.LocalColorMemory
import com.bamnat.app.ui.settings.MemoLookEditor
import com.bamnat.app.ui.settings.MoneyLookEditor
import com.bamnat.app.ui.settings.MoneySample
import com.bamnat.app.ui.settings.PatternEditor
import com.bamnat.app.ui.settings.PayLookEditor
import com.bamnat.app.ui.settings.PaySample
import com.bamnat.app.ui.settings.ScreenLookEditor
import com.bamnat.app.ui.settings.ScreenSample
import com.bamnat.app.ui.stats.StatsPart
import com.bamnat.app.ui.theme.LocalMemoLook
import com.bamnat.app.ui.theme.LocalMoneyLook
import com.bamnat.app.ui.theme.LocalScreenLooks
import com.bamnat.app.ui.theme.ScreenSkin
import com.bamnat.core.guide.FitSurface
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.CalendarView
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.MemoLook
import com.bamnat.core.recipe.MoneyLook
import com.bamnat.core.recipe.PayLook
import com.bamnat.core.recipe.PhotoLook
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.ScreenLook
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.WidgetLook
import com.bamnat.core.work.WorkStyle
import com.bamnat.app.data.AppSettings
import com.bamnat.app.ui.calendar.calendarLineSettled
import com.bamnat.app.ui.calendar.calendarParts
import com.bamnat.app.ui.calendar.withPartMoved
import com.bamnat.app.ui.calendar.withPartHome
import com.bamnat.app.ui.calendar.withPartShown
import com.bamnat.core.recipe.BlueprintParts
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role

/** [STU-11] The parts of the app the studio changes, each with the example tab it shows and the tools it has. */
private enum class Area(val label: String, val target: FreeTarget?, val tab: String) {
    CALENDAR("달력", FreeTarget.CALENDAR, TabOrder.CALENDAR),
    /**
     * [STU-21] 1.36 레시피 만들기 합침: 근무 색·알람 묶음·메모 틀·규칙 — what goes with the look when it is sent. [STU-25] 빌드 7: second
     * in the row (it was eleventh, three swipes away).
     */
    FEATURES("기능", null, TabOrder.CALENDAR),
    /**
     * [RUL-06] 1.36 규칙·단추 (사용자 2026-11-01 "수식·조건으로 모든 곳을 이어 직접"): 규칙, 단추, 조건부 꾸미기 in one place. PC D8: third in
     * the row, next to 기능 (맨 끝이라 스튜디오 안에서 못 찾고 설정 › 스튜디오에서 열기로 들어갔어요).
     */
    RULES("규칙·단추", null, TabOrder.CALENDAR),
    MONEY("가계부", FreeTarget.MONEY, TabOrder.MONEY),
    MEMO("메모", FreeTarget.MEMO, TabOrder.MEMOS),
    STATS("통계", FreeTarget.STATS, TabOrder.STATS),
    PAY("급여", FreeTarget.PAY, TabOrder.STATS),
    PLAZA("마을", FreeTarget.PLAZA, TabOrder.PLAZA),
    SETTINGS("설정", FreeTarget.SETTINGS, TabOrder.SETTINGS),
    WIDGET("위젯", null, TabOrder.SETTINGS),
    ALARM("알람 화면", FreeTarget.ALARM, TabOrder.SETTINGS),
    /** 1.38 팀·가게 관리 (리더·사장님): 카드 순서·숨김, 색·배경은 설정 화면과 함께. 기능이 켜진 휴대폰에서만 보여요. */
    MANAGE("팀·가게 관리", FreeTarget.SETTINGS, TabOrder.SETTINGS),
    // 사용자 2026-11-01 ("스튜디오에 결제 처리방침이 왜 있어"): 결제·처리방침·동의 화면은 꾸미지 않아요 — 늘 앱 기본 모양 ([ScreenSkin]).
    ALL("앱 전체", FreeTarget.ALL, TabOrder.CALENDAR),
    ;

    /** F-97: the area chips say what the bottom tabs say for this kind of work (근무·급여, 장부 …), not a name of their own. */
    fun labelFor(style: WorkStyle?): String = when (this) {
        STATS -> com.bamnat.app.ui.TopLevel.STATS.labelFor(style)
        MONEY -> com.bamnat.app.ui.TopLevel.MONEY.labelFor(style)
        else -> label
    }

    val tools: List<Tool> get() = when (this) {
        CALENDAR -> listOf(Tool.CELL, Tool.PARTS, Tool.DECOR, Tool.BOX, Tool.PATTERN, Tool.VIEW, Tool.DAYS, Tool.PHOTO, Tool.COLORS, Tool.CARDS)
        MEMO -> listOf(Tool.MEMO_LOOK, Tool.PARTS, Tool.DECOR, Tool.BOX, Tool.COLORS, Tool.CARDS)
        MONEY -> listOf(Tool.MONEY_LOOK, Tool.PARTS, Tool.DECOR, Tool.BOX, Tool.COLORS, Tool.CARDS)
        STATS -> listOf(Tool.COLORS, Tool.DECOR, Tool.BOX, Tool.CARDS)
        PAY -> listOf(Tool.PAY_LOOK, Tool.PARTS, Tool.COLORS, Tool.DECOR, Tool.BOX)
        PLAZA -> listOf(Tool.COLORS, Tool.DECOR, Tool.BOX)
        SETTINGS -> listOf(Tool.COLORS, Tool.PARTS, Tool.DECOR, Tool.BOX, Tool.TABS, Tool.CORNERS)
        WIDGET -> listOf(Tool.WIDGET, Tool.PARTS, Tool.DECOR, Tool.BOX)
        ALARM -> listOf(Tool.COLORS)
        MANAGE -> listOf(Tool.CARDS, Tool.COLORS)
        // [STU-30] 1.36 한 색으로 앱 전체 first: one colour makes every screen's colours.
        // [STU-37] 근무에 따라 바뀌는 모양 second.
        // [RUL-06] 한 색, 근무에 따라, 부품, 규칙·단추 first.
        ALL -> listOf(Tool.SEED, Tool.SHIFT, Tool.PARTS, Tool.LOGIC, Tool.COLORS, Tool.DECOR, Tool.FONT, Tool.MOTION, Tool.BOX, Tool.CORNERS, Tool.TABS)
        FEATURES -> listOf(Tool.FEATURE)
        // PC D4: 장터에서 담은 규칙·단추를 여기 보관함에서 얹어요.
        RULES -> listOf(Tool.RULES, Tool.BUTTONS, Tool.COND, Tool.BOX)
    }

    /** [LAY-04] Where this area's 꾸밈 층 can stand. */
    val decorPlaces: List<String> get() = when (this) {
        CALENDAR -> listOf("bg.calendar", com.bamnat.core.recipe.Decor.HEAD, com.bamnat.core.recipe.Decor.WEEK, com.bamnat.core.recipe.Decor.CELL)
        MEMO -> listOf("bg.memo"); MONEY -> listOf("bg.money"); STATS -> listOf("bg.stats"); PAY -> listOf("bg.pay")
        PLAZA -> listOf("bg.plaza"); SETTINGS -> listOf("bg.settings"); WIDGET -> listOf(com.bamnat.core.recipe.Decor.WIDGET)
        else -> listOf(com.bamnat.core.recipe.Decor.APP)
    }

    /** 사용자 2026-11-01: the 장터 구간 that holds this area's looks — a 기능별 탭 where there is one, else 테마. */
    val marketKind: String get() = com.bamnat.app.recipe.RecipeClient.let { r ->
        when (this) {
            CALENDAR -> r.SCOPE_TAB + "calendar"; MONEY -> r.SCOPE_TAB + "money"; MEMO -> r.SCOPE_TAB + "memo"
            STATS, PAY -> r.SCOPE_TAB + "pay"; WIDGET -> r.SCOPE_TAB + "widget"; ALARM -> r.SCOPE_TAB + "alarm"; FEATURES -> r.SCOPE_TAB
            else -> r.SCOPE_THEME
        }
    }

    /** Screens drawn only in 자세히 (the example app has no page of their own). */
    val detailOnly: Boolean get() = this in setOf(PAY, ALARM, WIDGET, MANAGE)

    companion object {
        fun of(tab: String): Area = when (tab) {
            TabOrder.MONEY -> MONEY; TabOrder.MEMOS -> MEMO; TabOrder.STATS -> STATS; TabOrder.PLAZA -> PLAZA; TabOrder.SETTINGS -> SETTINGS
            else -> CALENDAR
        }
    }
}

private enum class Tool(val label: String) {
    CELL("칸 디자인"), PATTERN("패턴"), VIEW("칸 높이·줄"), DAYS("오늘·주말 색"), PHOTO("그림"), COLORS("색·배경"), CARDS("카드 배치"),
    MEMO_LOOK("메모장 모양"), MONEY_LOOK("가계부 모양"), PAY_LOOK("급여 모양"), TABS("아래 메뉴"), CORNERS("모서리"), WIDGET("위젯 모양"),
    BOX("보관함"), FEATURE("켜고 고치기"), SEED("한 색"), SHIFT("근무에 따라"),
    /** [BLU-06] 1.36 부품 하나하나 꾸미기. */
    PARTS("부품"),
    /** [RUL-06] 1.36 수식·규칙·버튼: 단추와 "이럴 때 이렇게" 규칙. */
    LOGIC("규칙·단추"), RULES("규칙"), BUTTONS("단추"), COND("조건부 꾸미기"),
    /** [LAY-04] 1.36 꾸밈 층: 도형·스티커·글상자·아이콘을 마음대로 놓아요. */
    DECOR("꾸밈 층"),
    // [MAT-05][MAT-07] 1.36 글꼴(앱 전체), 움직임.
    FONT("글꼴"), MOTION("움직임"),
}

/** [STU-24] 1.36 미리 보기 크기: the handle under it sets any size between these (사용자 2026-10-31 "크게작게 접기가 너무 불편"). */

/** [CNV-04] What the 더 보기 판 under the canvas holds: an area's tool, the picked part in full, or the 층 list. */
internal enum class PanelMode { TOOL, PART, LAYERS, CARDS }

/** The area a part belongs to, for the 부품 도구's chips around it. */
private fun areaOfPart(id: String): String = when (id.substringBefore('.')) {
    "cal" -> "CALENDAR"; "memo" -> "MEMO"; "money" -> "MONEY"; "pay" -> "PAY"; "widget" -> "WIDGET"; else -> "ALL"
}

/**
 * [CNV-06][CNV-07] 1.37 A page's cards as the canvas moves them: up, down (the 달력's over the month too, 사용자 요청 6), hidden and
 * put back; each page keeps its own line in the settings, and the 달력's own switches (하루 카드·휴식 점검·이번 달 메모) go with it.
 */
internal class PageCards(
    val pageName: String,
    private val ids: List<String>,
    private val labels: Map<String, String>,
    private val fixed: Set<String>,
    private val seen: (String) -> Boolean,
    private val get: (AppSettings) -> String,
    private val put: (AppSettings, String) -> AppSettings,
    private val moveFn: ((AppSettings, String, Int) -> AppSettings)? = null,
    private val showFn: ((AppSettings, String, Boolean) -> AppSettings)? = null,
    private val hiddenFn: ((AppSettings) -> List<String>)? = null,
    private val aboveFn: ((AppSettings, String) -> Boolean)? = null,
    val across: ((AppSettings, String) -> AppSettings)? = null,
) {
    fun label(id: String): String = labels[id] ?: id
    fun move(d: AppSettings, id: String, by: Int): AppSettings =
        moveFn?.invoke(d, id, by) ?: put(d, StudioEdit.moveCard(get(d), ids, fixed, id, by, seen))
    fun canMove(d: AppSettings, id: String, by: Int): Boolean = id !in fixed && move(d, id, by) != d
    fun show(d: AppSettings, id: String, shown: Boolean): AppSettings = showFn?.invoke(d, id, shown) ?: put(d, StudioEdit.showCard(get(d), ids, id, shown))
    fun hidden(d: AppSettings): List<String> = hiddenFn?.invoke(d) ?: ids.filter { it !in fixed && seen(it) && !StudioEdit.cardShown(get(d), ids, it) }
    fun above(d: AppSettings, id: String): Boolean? = aboveFn?.invoke(d, id)
    /** [CNV-10] The cards shown now that can be put away (빈 판). */
    fun movable(d: AppSettings): List<String> = ids.filter { it !in fixed && seen(it) && it !in hidden(d) }

    companion object {
        val NONE = PageCards("", emptyList(), emptyMap(), emptySet(), { false }, { "" }, { d, _ -> d })
    }
}

/** [CNV-07] What ‘＋ 카드 넣기’ offers on [tab]: the cards hidden on the draft, and ([FIT-01]) what this kind of work put away. */
internal class CardsToAdd(val page: PageCards, val hidden: List<String>, val fit: List<com.bamnat.app.ui.components.OtherRow>)

@Composable
private fun rememberCardsToAdd(tab: String, style: WorkStyle?, d: AppSettings): CardsToAdd {
    val added = com.bamnat.app.ui.guide.rememberFitAdded(com.bamnat.app.data.PersonalCalendar.MAIN)
    val page = remember(tab, style, added) { pageCards(tab, style, added) }
    val hidden = remember(d, page) { page.hidden(d) }
    val fit = remember(tab, style, added) {
        when (fitSurface(tab)) {
            null -> emptyList()
            FitSurface.CALENDAR_PARTS -> com.bamnat.app.ui.guide.fitOthers(FitSurface.CALENDAR_PARTS, style, added) { id -> CalendarPart.byId(id)?.let { it.label to it.what } }
            FitSurface.MONEY_PARTS -> com.bamnat.app.ui.guide.fitOthers(FitSurface.MONEY_PARTS, style, added) { id -> MoneyPart.entries.firstOrNull { it.id == id }?.let { it.label to it.what } }
            else -> com.bamnat.app.ui.guide.fitOthers(FitSurface.STATS_PARTS, style, added) { id -> StatsPart.entries.firstOrNull { it.id == id }?.let { it.label to it.what } }
        }
    }
    return CardsToAdd(page, hidden, fit)
}

/**
 * [CNV-06] PC 확인 1.37 A2 (내렸다 올려 그대로인데 ‘바꾼 곳 1곳’): a page's line that puts the cards as [base] has them is [base]'s own
 * words again, so a card moved and moved back is no change.
 */
internal fun AppSettings.sameLinesAs(base: AppSettings): AppSettings {
    fun <T> same(a: String, b: String, all: List<T>, id: (T) -> String): Boolean = a == b ||
        com.bamnat.core.layout.PageLayout.decode(a).arrange(all, id).map { it.part to it.shown } ==
            com.bamnat.core.layout.PageLayout.decode(b).arrange(all, id).map { it.part to it.shown }
    // [EASE-01] The calendar's line with no order of its own is the month-first one (CALENDAR_FIRST), as the calendar shows it.
    val sameCalendar = calendarLayout == base.calendarLayout ||
        calendarParts().map { it.part to it.shown } == base.calendarParts().map { it.part to it.shown }
    return copy(
        calendarLayout = if (sameCalendar) base.calendarLayout else calendarLayout,
        moneyLayout = if (same(moneyLayout, base.moneyLayout, MoneyPart.entries.toList()) { it.id }) base.moneyLayout else moneyLayout,
        memoLayout = if (same(memoLayout, base.memoLayout, MemoPart.entries.toList()) { it.id }) base.memoLayout else memoLayout,
        statsLayout = if (same(statsLayout, base.statsLayout, StatsPart.entries.toList()) { it.id }) base.statsLayout else statsLayout,
    )
}

/** [FIT-02] What [tab]'s cards hide for a kind of work (null for a page without such cards). */
internal fun fitSurface(tab: String): FitSurface? = when (tab) {
    TabOrder.CALENDAR -> FitSurface.CALENDAR_PARTS
    TabOrder.MONEY -> FitSurface.MONEY_PARTS
    TabOrder.STATS -> FitSurface.STATS_PARTS
    else -> null
}

/** [CNV-07] [tab]'s cards for [style], with what was kept on anyway by 더하기 ([added], [FIT-02]). */
internal fun pageCards(tab: String, style: WorkStyle?, added: Set<String> = emptySet()): PageCards = when (tab) {
    TabOrder.CALENDAR -> {
        val skip = setOf(CalendarPart.ASK, CalendarPart.JOURNAL, CalendarPart.MONTH)
        val ids = CalendarPart.entries.map { it.id }
        fun fits(p: CalendarPart) = com.bamnat.core.guide.FitTable.visible(FitSurface.CALENDAR_PARTS, p.id, style, added)
        fun aboveIn(d: AppSettings, p: CalendarPart) = com.bamnat.app.ui.calendar.calendarAbove(d.calendarParts().map { it.part })(p)
        PageCards("달력", ids, CalendarPart.entries.associate { it.id to it.labelFor(style) }, skip.map { it.id }.toSet(),
            // 1.39 [FLOW-22] PC 4차 (＋ 카드 넣기에 하루 이야기가 없음): the line as the calendar reads it, a card that starts off included.
            { id -> CalendarPart.byId(id)?.let(::fits) == true }, { it.calendarLineSettled() }, { d, l -> d.copy(calendarLayout = l) },
            moveFn = { d, id, by -> CalendarPart.byId(id)?.let { p -> d.withPartMoved(p, by) { q -> fits(q) } } ?: d },
            showFn = { d, id, shown -> CalendarPart.byId(id)?.let { p -> d.withPartShown(p, shown).let { x -> x.copy(calendarLayout = StudioEdit.showCard(x.calendarLineSettled(), ids, id, shown)) } } ?: d },
            hiddenFn = { d -> d.calendarParts().filter { it.part !in skip && fits(it.part) && !StudioEdit.cardShown(d.calendarLineSettled(), ids, it.part.id) }.map { it.part.id } },
            aboveFn = { d, id -> CalendarPart.byId(id)?.let { aboveIn(d, it) } ?: true },
            across = { d, id ->
                val p = CalendarPart.byId(id)
                if (p == null) d else {
                    val up = aboveIn(d, p)
                    var x = d
                    for (i in 0 until CalendarPart.entries.size) {
                        val n = x.withPartMoved(p, if (up) 1 else -1) { q -> fits(q) }
                        if (n == x) break
                        x = n
                        if (aboveIn(x, p) != up) break
                    }
                    // Above the month 요약 is drawn before 근무 횟수 whatever the order, so it goes back to its own place there
                    // (PC 확인 1.37 A2: 내렸다 올리면 그대로 보이는데 ‘바꾼 곳 1곳’).
                    if (!up && p == CalendarPart.SUMMARY) {
                        val order = x.calendarParts().map { it.part }
                        val i = order.indexOf(p)
                        if (i > 0 && order[i - 1] == CalendarPart.COUNTS) x = x.withPartMoved(p, -1) { q -> fits(q) }
                    }
                    // [EASE-01] Back on the side the month-first line has it: at its own place there.
                    val home = com.bamnat.app.ui.calendar.calendarAbove(com.bamnat.app.ui.calendar.CALENDAR_FIRST)(p)
                    if (aboveIn(x, p) == home) x = x.withPartHome(p)
                    x
                }
            })
    }
    TabOrder.MONEY -> PageCards(com.bamnat.app.ui.TopLevel.MONEY.labelFor(style), MoneyPart.entries.map { it.id }, MoneyPart.entries.associate { it.id to it.label },
        MoneyPart.entries.filterNot { it.movable }.map { it.id }.toSet(), { com.bamnat.core.guide.FitTable.visible(FitSurface.MONEY_PARTS, it, style, added) },
        { it.moneyLayout }, { d, l -> d.copy(moneyLayout = l) })
    TabOrder.MEMOS -> PageCards("메모", MemoPart.entries.map { it.id }, MemoPart.entries.associate { it.id to it.label },
        MemoPart.entries.filterNot { it.movable }.map { it.id }.toSet(), { true }, { it.memoLayout }, { d, l -> d.copy(memoLayout = l) })
    TabOrder.STATS -> PageCards(com.bamnat.app.ui.TopLevel.STATS.labelFor(style), StatsPart.entries.map { it.id }, StatsPart.entries.associate { it.id to it.label },
        StatsPart.entries.filterNot { it.movable }.map { it.id }.toSet(), { com.bamnat.core.guide.FitTable.visible(FitSurface.STATS_PARTS, it, style, added) },
        { it.statsLayout }, { d, l -> d.copy(statsLayout = l) })
    else -> PageCards.NONE
}

/**
 * [STU-10][STU-11] 1.35 스튜디오 (사용자 2026-10-30 21:39 "인터랙션이 뛰어나야하고 좀더 밀도있게"): one screen for every look. On top the
 * example app (앱 화면) or the screen in detail (자세히) — 크게, 작게 or 접기 — that follows every change; under it the area (달력, 가계부 …
 * 앱 전체) and that area's tools as short tabs, one open at a time, so nothing is a long scroll. 되돌리기·다시 하기 over every change, and
 * 비교 held down shows the look it started from. The phone stays as it is until 내 앱에 입히기; 저장 keeps it as 내 테마, 올리기 sends it to 장터.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(vm: StudioViewModel, onBack: () -> Unit, onOpen: (String) -> Unit = {}, onMarket: ((String) -> Unit)? = null) {
    com.bamnat.app.ui.theme.BarIcons(light = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
    val c = LocalContext.current.appContainer
    val s by vm.state.collectAsStateWithLifecycle()
    val kept by c.wardrobe.kept.collectAsStateWithLifecycle()
    val studioFacts = com.bamnat.app.logic.rememberAppDayFacts()
    // PC D10: rules put in from the market (or switched in 설정) while the studio was behind show here too.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { vm.syncLogic() }
    // 2.0.0 [RVS-13] 검토 U09a-13: kept at once when the studio goes behind (홈·화면 끄기·다른 앱), and the draft open is remembered
    // with the screen, so if Android closes the app meanwhile it opens again by itself instead of only being offered.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { vm.keepNow() }
    var openDraft by rememberSaveable { mutableStateOf(0L) }
    LaunchedEffect(s.draftId) { if (!vm.cameBack(openDraft)) openDraft = s.draftId }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // 1.38 PC 19차 LOW (다시 열면 화면 고르기가 늘 '앱 전체'): with nothing asked for, the studio opens on the screen picked last.
    val studioPrefsEarly = LocalContext.current.let { ctx -> remember { ctx.getSharedPreferences("studio_ui", android.content.Context.MODE_PRIVATE) } }
    var area by rememberSaveable { mutableStateOf(vm.startArea?.let { a -> Area.entries.firstOrNull { it.name == a } }
        ?: studioPrefsEarly.getString("lastArea", null)?.let { a -> Area.entries.firstOrNull { it.name == a } } ?: Area.ALL) }
    var tool by rememberSaveable { mutableStateOf(if (Tool.SEED in area.tools || area == Area.ALL) Tool.SEED else area.tools.first()) }
    var partPick by remember { mutableStateOf<Pair<String, String?>?>(null) }
    // [LAY-09] 고급 보기 for the whole studio (부품 숫자 칸·여러 부품·색 변수, 꾸밈 층 숫자 칸, 층 나무), and the item the 층 나무 opens.
    var studioAdvanced by rememberSaveable { mutableStateOf(false) }
    var partsTogether by remember { mutableIntStateOf(1) }
    var decorFocus by remember { mutableStateOf<Int?>(null) }
    // [LAY-09] 곳 줄: the six most used, kept on the phone, and '더 보기'.
    val studioCtx = LocalContext.current
    val studioPrefs = remember { studioCtx.getSharedPreferences("studio_ui", android.content.Context.MODE_PRIVATE) }
    var areaUses by remember { mutableStateOf(AreaRank.decode(studioPrefs.getString("areaUses", null))) }
    // [CNV-03] 1.37 캔버스: what is picked on the example (a part or a card), the state and condition its tools write for, the quick
    // tool open under the bar, the 더 보기 판 and the zoom.
    var sel by rememberSaveable { mutableStateOf<String?>(null) }
    var selOn by rememberSaveable { mutableStateOf<String?>(null) }
    var selWhen by rememberSaveable { mutableStateOf<String?>(null) }
    var quick by remember { mutableStateOf<StudioEdit.Quick?>(null) }
    var panel by rememberSaveable { mutableStateOf(false) }
    var panelMode by rememberSaveable { mutableStateOf(PanelMode.TOOL) }
    // PC 확인 1.37 A18 MED-1: the 판's size is kept across restarts (with the folded rows, a device-only choice).
    val (panelBig, setPanelBig) = com.bamnat.app.ui.components.rememberFold("studio.panel.big")
    // 1.37 가계부 LOW-4: the 기능 tool changes no look, so its 판 opens big (the example folded to a strip) until 작게.
    val (featureSmall, setFeatureSmall) = com.bamnat.app.ui.components.rememberFold("studio.panel.feature.small")
    var condFirst by remember { mutableStateOf(false) }
    // PC 확인 1.37 A8: each ‘＋ 조건으로 꾸미기’ asks again, also when the part's panel is already open.
    var condAsk by remember { mutableStateOf(0) }
    var hintSeen by rememberSaveable { mutableStateOf(false) }
    val zoom = remember { ZoomState() }
    val spots = remember { com.bamnat.app.ui.parts.PartSpots() }
    var history by remember { mutableStateOf(false) }
    var changes by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    // PC 확인 보안 7차 (두 번째 올리기에 지난 끝 화면이 먼저 나와요): each 올리기 is a new round, not the last one's screen.
    var publishRound by remember { mutableStateOf(0) }
    var preparing by remember { mutableStateOf(false) }
    var uploadWhat by remember { mutableStateOf<List<String>?>(null) }
    // PC 006d0aec: what went up from here (body → the answer), so 올리기 with nothing changed says so at once, before '무엇을 올릴까요'.
    var sentBodies by remember { mutableStateOf(mapOf<String, com.bamnat.app.recipe.Sent>()) }
    var noChange by remember { mutableStateOf<com.bamnat.app.recipe.Sent?>(null) }
    var nothingToSend by remember { mutableStateOf(false) }
    var openedLine by remember { mutableStateOf<String?>(null) }
    var detail by rememberSaveable { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    // 1.36 (사용자 2026-10-31 "화면 터치하면 전 모습, 떼면 후 모습"): a finger held on the example shows the look one step back.
    var peeking by remember { mutableStateOf(false) }
    var peekMarks by remember { mutableStateOf(0) }
    var halfAt by remember { mutableStateOf(0.5f) }
    var half by rememberSaveable { mutableStateOf(false) }
    var payStyle by rememberSaveable { mutableStateOf(vm.style ?: WorkStyle.SHIFT) }
    var lookIndex by rememberSaveable { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var startAsk by remember { mutableStateOf<(() -> Unit)?>(null) }
    var inkAsk by remember { mutableStateOf<(() -> Unit)?>(null) }
    // 1.36 [GRW-01] 원하는 꾸미기가 없을 때 스튜디오를 떠나지 않고 그 자리에서 부탁해요.
    var wish by remember { mutableStateOf(false) }
    val snack = com.bamnat.app.ui.components.AppSnack.rememberLane()
    val resumable by vm.resumable.collectAsStateWithLifecycle()
    var resumeShut by rememberSaveable { mutableStateOf(false) }
    var feelOpen by remember { mutableStateOf(false) }
    LaunchedEffect(vm.askFeel) { if (vm.askFeel) { vm.askFeel = false; feelOpen = true } }
    LaunchedEffect(vm.openFont) {
        when (vm.openFont) {
            StudioViewModel.START_FONT -> { area = Area.ALL; tool = Tool.FONT }
            StudioViewModel.START_MOTION -> { area = Area.ALL; tool = Tool.MOTION }
            StudioViewModel.START_PARTS -> { area = Area.ALL; tool = Tool.PARTS }
            StudioViewModel.START_SCREEN_FONT -> { com.bamnat.app.ui.settings.FontRowFocus.label = "이 화면 글꼴"; area = Area.CALENDAR; tool = Tool.COLORS }
            StudioViewModel.START_DATE_FONT -> { com.bamnat.app.ui.settings.FontRowFocus.pinned = "날짜 글꼴"; area = Area.CALENDAR; tool = Tool.CELL }
        }
        if (vm.openFont != null) { panelMode = PanelMode.TOOL; panel = true }
        vm.openFont = null
    }
    val shotLayer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    // [STU-41] The example as a picture, taken as 올리기 opens.
    // [CNV-12] The example shows my own month, money and memos; the 표지 others see is taken on the made-up example (a frame or two of it).
    var shotExample by remember { mutableStateOf(false) }
    fun takeShot() { scope.launch {
        shotExample = true
        repeat(3) { androidx.compose.runtime.withFrameNanos { } }
        StudioShot.last = runCatching { shotLayer.toImageBitmap().asAndroidBitmap() }.getOrNull()
        shotExample = false
    } }
    // [STU-36] 미리 보기 조건.
    var seeDark by rememberSaveable { mutableStateOf(false) }
    var seeBig by rememberSaveable { mutableStateOf(false) }
    var seeCvd by rememberSaveable { mutableStateOf(false) }

    val d = s.draft
    val looks = remember(d.screenLooks) { ScreenLooks.decode(d.screenLooks) }
    val view = remember(d.calView) { CalendarView.decode(d.calView) }
    val memoLook = remember(d.memoLook) { MemoLook.decode(d.memoLook) }
    val moneyLook = remember(d.moneyLook) { MoneyLook.decode(d.moneyLook) }
    val payLooks = remember(d.payLooks) { PayLook.decodeAll(d.payLooks) }
    // [LAY-04] 1.36 꾸밈 층 being made.
    val decor = remember(d.decor) { com.bamnat.core.recipe.Decor.decode(d.decor) }
    // [STU-21] The example draws the 기능 that show (근무 색, 메모 틀, 투자 일지) along with the look.
    val now = remember(s.pieces, s.extras) { vm.shown(s) }
    val before = remember(s.basePieces) { Recipe("처음", styles = WorkStyle.entries.toSet(), pieces = s.basePieces) }
    // [BLU-06] The bar counts what the 바꾼 곳 sheet lists (PC 3차: 막대는 1개인데 시트는 2줄).
    val changedCount = remember(before, now) { LookParts.changed(before, now).size }
    val diff = remember(s.draft, s.base) { c.recipes.pieces.lookDiff(s.draft, s.base) }
    fun pick(a: Area) {
        area = a; tool = a.tools.first(); lookIndex = 0; if (a.detailOnly) detail = true
        sel = null; quick = null; if (panelMode != PanelMode.TOOL) panel = false
        areaUses = AreaRank.used(areaUses, a.name)
        runCatching { studioPrefs.edit().putString("areaUses", AreaRank.encode(areaUses)).putString("lastArea", a.name).apply() }
    }
    // [CNV-04] A tool of an area in the 더 보기 판, the example staying in view above it.
    /** 1.38: ‘＋ 아이콘·사진’ asks the 꾸밈 층 to open on 넣기 (판 접고 아이콘 칸), scrolled into view; any other way in sets it back to 0. */
    var decorAdd by remember { mutableIntStateOf(0) }
    fun openTool(a: Area, t: Tool, addIcons: Boolean = false) {
        if (a != area) { area = a; lookIndex = 0; if (a.detailOnly) detail = true }
        tool = if (t in a.tools) t else a.tools.first()
        decorAdd = if (addIcons) decorAdd + 1 else 0
        // 8차 통합 e690bef6 PC (작은 판에서 '＋ 아이콘·사진'을 누르면 아이콘 첫 줄이 화면 밖): it opens the panel big.
        if (addIcons && !panelBig) setPanelBig(true)
        panelMode = PanelMode.TOOL; panel = true
    }
    fun openPanel(m: PanelMode) { panelMode = m; panel = true }
    // [CNV-03] A part or card picked on the example (or in the 층 list, or a name in the bar): its first quick tool opens at once,
    // so a tap and a colour are all it takes (사용자 "유닛 클릭하고 색상선택시 색상이 입혀지게").
    fun selectPart(id: String?) {
        if (id != sel) { selOn = null; selWhen = null; condFirst = false }
        sel = id
        quick = id?.takeIf { StudioEdit.card(it) == null }?.let { StudioEdit.tools(it).firstOrNull() }
        if (id != null) hintSeen = true
        if (id == null && panelMode == PanelMode.PART) panel = false
        if (id != null && StudioEdit.card(id) != null && panelMode == PanelMode.PART) panel = false
    }
    // [CNV-05] A quick tool's pick goes on the draft at once; picks in a row of the same tool on the same part are one 되돌리기.
    fun setPart(label: String, kv: Array<Pair<String, Any?>>) {
        val id = sel ?: return
        val on = selOn
        val w = selWhen
        vm.changeMerged("${BlueprintParts.LABELS[id] ?: id} · $label") { x -> StudioEdit.set(x.parts, id, on, w, *kv)?.let { x.copy(parts = it) } ?: x }
    }
    // PC 확인 1.36 (처음 모양으로 시작하면 달력 › 칸 디자인이 열려 안내가 말한 ‘한 색’이 안 보임): a plain start opens 앱 전체 › 한 색 once.
    val plainStart = diff == 0 && s.origin is StudioViewModel.Origin.Plain && s.extras.isEmpty()
    fun openSeed() { openTool(Area.ALL, Tool.SEED) }
    var plainOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(plainStart) { if (plainStart && !plainOpened) { plainOpened = true; openSeed() } }
    // [CNV-08] Opened from a tab's 카드 배치: said how the cards move here.
    LaunchedEffect(Unit) {
        if (vm.startArea in setOf("CALENDAR", "MONEY", "MEMO", "STATS")) openedLine = "카드를 누르면 위·아래로 옮기거나 숨겨요. 다 되면 아래 ‘내 앱에 입히기’"
    }
    // [STU-20] The 기록 목록 says where each change was made.
    vm.where = "${area.labelFor(vm.style)} · ${(if (tool in area.tools) tool else area.tools.first()).label}"
    // [STU-23] 눌러서 고치기: a tap on a place of the example no part marks opens its tool; a tap on its background lets the pick go.
    fun picked(region: String) {
        val (a, t) = when (region) {
            "cells" -> Area.CALENDAR to Tool.CELL
            "days" -> Area.FEATURES to Tool.FEATURE
            "cards" -> (if (area.tools.contains(Tool.CARDS)) area else Area.of(area.tab)) to Tool.CARDS
            "memo" -> Area.MEMO to Tool.MEMO_LOOK
            "money" -> Area.MONEY to Tool.MONEY_LOOK
            else -> { selectPart(null); return }
        }
        selectPart(null)
        openTool(a, t)
        openedLine = "‘${a.labelFor(vm.style)} · ${tool.label}’을 열었어요. 바꾸면 위에 바로 보여요"
    }
    // Ask first when the draft has things the phone does not.
    fun guarded(then: () -> Unit) { if (s.changed) startAsk = then else then() }
    // [STU-12] 조합 자동 점검 before it goes anywhere.
    fun checked(then: () -> Unit) { if (vm.hardToRead().isNotEmpty()) inkAsk = then else then() }

    LaunchedEffect(s.said) {
        val said = s.said ?: return@LaunchedEffect
        // 1.36 (사용자 2026-10-31): 얹기 too can be taken back from here, the same step as 되돌리기 above.
        val r = snack.showSnackbar(said, actionLabel = if (s.canUndoWear || s.canUndoLayer || s.unlaid != null) "되돌리기" else null, duration = SnackbarDuration.Long)
        // PC 14차: 빼기 too is taken back from the line (laid on again).
        if (r == SnackbarResult.ActionPerformed) { val back = s.unlaid; if (back != null) { vm.said(); vm.layer(back) } else if (s.canUndoLayer) vm.undoLayer() else vm.undoWear() } else vm.said()
    }
    // PC 제안 1.36 (장터에서 담고 돌아와도 보관함에 들어온 줄 모름): what came into 보관함 while away is said, with 보기 to it.
    LaunchedEffect(kept) {
        val ids = kept.map { it.id }.toSet()
        val before = vm.seenKept
        vm.seenKept = ids
        val n = before?.let { (ids - it).size } ?: 0
        if (n > 0 && snack.showSnackbar("보관함에 새로 들어왔어요 · ${n}개", actionLabel = "보기", duration = SnackbarDuration.Long) == SnackbarResult.ActionPerformed) {
            openTool(if (Tool.BOX in area.tools) area else Area.CALENDAR, Tool.BOX)
        }
    }
    // 1.36 U3: saved or sent since the last change, it leaves without asking.
    androidx.activity.compose.BackHandler(enabled = s.changed && !s.kept) { leaving = true }

    // [STU-40] 내 색 모음 lives in the settings (kept across studios and in the backup).
    val pinned by vm.myColors.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalColorMemory provides ColorMemory(s.recent, vm::usedColor, pinned, vm::pinColor)) {
        val foldHost = remember { com.bamnat.app.ui.components.FoldHost() }
        CompositionLocalProvider(com.bamnat.app.ui.components.LocalFoldHost provides foldHost) {
        Scaffold(
            snackbarHost = { com.bamnat.app.ui.components.AppSnackHost() },
            topBar = {
                // PC 확인 W7 1.36: nothing changed (되돌리기 to the end), or back on the start's own 느낌, and it goes.
                val feelLine = vm.feelNow?.takeIf { it.first == s.origin.title && s.changed &&
                    vm.feelStart?.takeIf { f -> f.first == s.origin.title }?.second?.label != it.second }
                // 2.0.0 [RVS-16] (총괄 #495 사진, 조건 12: 글자 200%에서 제목 ‘지금 …’의 아래가 잘렸어요) The bar is as tall as its lines at this
                // text size (스튜디오 · the start's name · 지금: …), never under the app bar's own height.
                val typo = MaterialTheme.typography
                val barHeight = with(androidx.compose.ui.platform.LocalDensity.current) {
                    fun lh(t: androidx.compose.ui.text.TextStyle) = (if (t.lineHeight.isSpecified) t.lineHeight else t.fontSize * 1.4f).toDp()
                    lh(typo.labelMedium) + lh(typo.titleMedium) + (if (feelLine != null) lh(typo.labelSmall) else 0.dp) + 16.dp
                }.coerceAtLeast(androidx.compose.material3.TopAppBarDefaults.TopAppBarExpandedHeight)
                TopAppBar(
                    expandedHeight = barHeight,
                    navigationIcon = { IconButton(onClick = { if (s.changed && !s.kept) leaving = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                    title = {
                        var menu by remember { mutableStateOf(false) }
                        Column(Modifier.clickable(onClickLabel = "시작할 모양 바꾸기") { menu = true }) {
                            Text("스튜디오", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.origin.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                            feelLine?.let {
                                Text("지금: ${it.second}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("지금 내 모양에서") }, onClick = { menu = false; guarded { vm.startMine() } })
                            DropdownMenuItem({ Text("처음 모양에서") }, onClick = { menu = false; guarded { vm.startPlain() } })
                            // [STU-35] 느낌 세 번 고르기로 시작안 넷.
                            DropdownMenuItem({ Text("느낌으로 새로 만들기") }, onClick = { menu = false; feelOpen = true })
                            // [STU-34] 자동 저장된 만들던 것.
                            resumable?.let { r ->
                                DropdownMenuItem({ Text("이어서 하기 · ${draftName(r.body)}", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = { menu = false; guarded { vm.resume(r.id) } })
                            }
                            val themes = vm.kept()
                            if (themes.isNotEmpty()) HorizontalDivider()
                            themes.take(12).forEach { w ->
                                DropdownMenuItem({ Text((if (w.own) "🎨 " else "") + w.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    onClick = { menu = false; guarded { vm.startFrom(w) } })
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = vm::undo, enabled = s.past.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Undo, "되돌리기") }
                        IconButton(onClick = vm::redo, enabled = s.future.isNotEmpty()) { Icon(Icons.AutoMirrored.Filled.Redo, "다시 하기") }
                        // [STU-20] 기록 목록: every change in a line, back to any of them.
                        IconButton(onClick = { history = true }, enabled = s.past.isNotEmpty() || s.future.isNotEmpty()) {
                            Icon(Icons.Filled.History, "바꾼 기록")
                        }
                        // 비교: held down, the look it started from.
                        // [STU-10] Held: the look before; a tap (and TalkBack's double tap) puts the two side by side instead.
                        Box(Modifier.size(48.dp).semantics {
                            contentDescription = "비교. 누르고 있으면 고치기 전 모양, 한 번 누르면 반반"
                            onClick(label = if (half) "반반 비교 끄기" else "반반 비교 켜기") { half = !half; true }
                        }
                            .pointerInput(Unit) { detectTapGestures(onPress = { comparing = true; tryAwaitRelease(); comparing = false }, onTap = { half = !half }) },
                            contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Compare, null, tint = if (comparing || half) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                )
            },
            bottomBar = {
                // PC 확인 1.37 A11 (덮개가 아래 저장·입히기 줄은 안 덮음): an open row above dims this bar too, and a tap on it only folds the row.
                Box {
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        // 2.0.0 [RVS-07] 작품 크기 (검토 SD-04 ①③·SN-08·SI-04): from 80% of what 올리기 takes it says so, and past it which pieces
                        // to take out first, before 올리기 fails; nothing while it is small. Worked out off the main thread (STU-28).
                        val size by androidx.compose.runtime.produceState<Pair<com.bamnat.core.recipe.WorkSize.Of, String?>?>(null, s.pieces, s.extras) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                val z = vm.size(s)
                                z to (if (z.over) com.bamnat.core.recipe.WorkSize.takeOutLine(vm.takeOut(s)) else null)
                            }
                        }
                        size?.takeIf { it.first.near || it.first.over }?.let { (z, out) ->
                            com.bamnat.app.ui.components.ToneLine(
                                if (z.over) listOfNotNull(z.overLine, out).joinToString(" · ")
                                else "작품 크기 ${z.line} · 장터에 올릴 수 있는 만큼에 가까워요",
                                style = MaterialTheme.typography.labelMedium)
                        }
                        // 2.0.0 [RVS-14] 첫 줄 먼저 (총괄 HQ-56, 조건 1): a 달력 머리·요일 줄 that pushes the month's first row down past what the app
                        // keeps says so here, beside 작품 크기, and that it is drawn smaller; tapping it opens 달력 머리 in 부품.
                        val push by androidx.compose.runtime.produceState(0, d.parts) {
                            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                                runCatching { com.bamnat.core.recipe.Blueprint.decode(d.parts).abovePush() }.getOrDefault(0)
                            }
                        }
                        if (push > com.bamnat.core.recipe.PartFloor.ABOVE_DP) com.bamnat.app.ui.components.ToneLine(
                            "달력 머리·요일 줄이 첫 줄을 ${push}dp 내려요 · 달력이 먼저 보이게 ${com.bamnat.core.recipe.PartFloor.ABOVE_DP}dp까지만 줄여 그려요",
                            style = MaterialTheme.typography.labelMedium, tone = com.bamnat.app.ui.theme.Tone.Plain, onClick = { selectPart("cal.head"); openPanel(PanelMode.PART) })
                        Row(Modifier.fillMaxWidth().clickable(enabled = diff > 0 || s.extras.isNotEmpty(), onClickLabel = "바꾼 곳 보기") { changes = true },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(when {
                                s.wornNow && diff == 0 -> "✓ 지금 내 앱에 입힌 모양이에요. 더 고치면 다시 입혀요"
                                // 마을 PC 제안 1.36 (처음 모양으로 열려도 무엇을 하는 곳인지 말이 없음): a start in this kind of work's words.
                                diff == 0 && s.origin is StudioViewModel.Origin.Plain && s.extras.isEmpty() ->
                                    "${vm.style?.let { com.bamnat.core.recipe.RecipeDescribe.short(it) + " " }.orEmpty()}첫 작품을 만들어요 · ‘한 색’에서 색 하나부터 골라 보세요"
                                diff == 0 -> "바꾼 곳 없음 · 입히기 전까지 내 앱은 그대로예요"
                                else -> "바꾼 곳 ${maxOf(changedCount, 1)}곳 · 입히기 전까지 내 앱은 그대로예요"
                            } + (if (s.extras.isNotEmpty()) " · 기능 ${s.extras.size}개" else "") + (s.sources.values.distinct().takeIf { it.isNotEmpty() }?.let { v -> " · " + v.joinToString("·") { "‘$it’" } + " 조각 사용" } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (diff > 0 || s.extras.isNotEmpty()) Text("보기 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            // PC 13차 사용성 제안 (얹은 뒤 '…조각 사용'만 있고 빼는 곳이 안 보임): the last one laid on, taken off right here.
                            s.layered.lastOrNull()?.let { k -> TextButton(onClick = { vm.unlayer(k) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("얹은 것 빼기") } }
                            // [RVM-17] PC-57: the calendar's band went after a moment; going back to the look from before stays here.
                            if (s.keptWear && s.layered.isEmpty()) TextButton(onClick = { vm.undoWear() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("입히기 전으로") }
                            if (plainStart && !(area == Area.ALL && tool == Tool.SEED)) TextButton(onClick = { openSeed() }) { Text("한 색 열기") }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            // PC 재검토 C11 (저장·올리기·입히기가 같은 무게): 내 앱에 입히기 is the one big button; 저장 and 올리기 are lighter.
                            TextButton(onClick = { checked { saving = true } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("저장") }
                            // [STU-21] 1.36 올리기 stays in the studio: 받는 분이 넣을 값 · 이름 · 설명 · 마치기 over it, back here with 스튜디오로.
                            // PC 확인 W37 1.36 (올리기를 눌러도 몇 초 아무 표시가 없어 여러 번 누름): 준비 중 at once, one tap only.
                            TextButton(onClick = { checked {
                                preparing = true
                                // [STU-26] Changes on one tab only: ask whether that tab goes up alone (a 기능별 탭) or the whole theme.
                                val tabs = vm.changedTabs()
                                takeShot()
                                // fc374a23 PC 확인: 기능을 담았으면 '기능만'도 고를 수 있게 늘 물어요.
                                if (tabs.isNotEmpty() || s.extras.isNotEmpty()) { uploadWhat = tabs; preparing = false }
                                else scope.launch {
                                    val (body, parent) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { vm.upload() }
                                    // PC 006d0aec: nothing changed since it last went up from here: said at once.
                                    val same = sentBodies[body]
                                    // bc7e2d43 PC 확인 LOW-6 (기능 0개·모양 그대로인데 빈 레시피가 올라감): nothing to send, said.
                                    val empty = body.contains("\"pieces\":[]")
                                    if (empty) { nothingToSend = true; preparing = false }
                                    else if (same != null) { noChange = same; preparing = false }
                                    else { publishRound++; publishing = Triple(body, parent, vm.nextOf()) }
                                }
                            } }, enabled = !preparing && publishing == null,
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(if (preparing) "준비 중…" else "올리기") }
                            Box(Modifier.weight(1f)) {
                                WearButton(vm, s.busy) { go -> checked(go) }
                            }
                        }
                    }
                }
                if (foldHost.isOpen) Box(Modifier.matchParentSize().background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.32f))
                    .pointerInput(Unit) { detectTapGestures { foldHost.fold() } })
                }
            },
        ) { padding ->
            BoxWithConstraints(Modifier.fillMaxSize().padding(padding).then(com.bamnat.app.ui.components.foldHostBox(foldHost))) {
            val totalH = maxHeight
            Column(Modifier.fillMaxSize()) {
                // [CNV-04] 1.37 위 고정 상황 도구 줄: the screen's tools with nothing picked, the picked part's (or card's) tools with one.
                val cardPick = StudioEdit.card(sel)
                if (sel != null && cardPick == null) PartBar(sel!!, d.parts, selOn, selWhen,
                    onSel = { selectPart(it) }, onOn = { o, w -> selOn = o; selWhen = w },
                    quick = quick, onQuick = { quick = it },
                    set = { label, kv -> setPart(label, kv) },
                    onMore = { openPanel(PanelMode.PART) },
                    onCondition = { condFirst = true; condAsk++; openPanel(PanelMode.PART) })
                else if (cardPick != null) {
                    val (page, cid) = cardPick
                    val cards = remember(page, d, vm.style) { pageCards(page, vm.style) }
                    CardBar(cards.label(cid), cards.pageName, onSel = { selectPart(it) },
                        canUp = cards.canMove(d, cid, -1), canDown = cards.canMove(d, cid, 1),
                        onMove = { by -> vm.change { cards.move(it, cid, by).sameLinesAs(s.base) } },
                        above = cards.above(d, cid), onAcross = cards.across?.let { go -> { vm.change { go(it, cid).sameLinesAs(s.base) } } },
                        onHide = { vm.change { cards.show(it, cid, false).sameLinesAs(s.base) }; selectPart(null) },
                        onLook = { selectPart("app.card") })
                } else ScreenBar {
                    com.bamnat.app.ui.components.FoldRow("studio.top", what = "화면 줄", over = true) {
                        // 화면 고르기: the screens of the app (영역 칩 12개 → 하나).
                        Box {
                            var open by remember { mutableStateOf(false) }
                            val hold = com.bamnat.app.ui.components.LocalFoldHold.current
                            FilterChip(selected = true, onClick = { hold(); open = true }, label = { Text("화면: ${area.labelFor(vm.style)} ▾") })
                            DropdownMenu(open, { open = false }) {
                                // PC 확인 8d0cddf6 (투자 스튜디오에 '급여' 칩): 급여 has no place for 자영업·투자, as on their bottom tabs.
                                val manageOn = com.bamnat.app.ui.work.rememberManageOn()
                                // 1.39 PC 2afb3a76 (스튜디오에서 위젯으로 가는 길을 못 찾았어요): 위젯 has no screen of its own (no target) and had
                                // dropped out of this list with 1.37's canvas; it is one of the screens again.
                                Area.entries.filter { (it.target != null || it == Area.WIDGET) && it != Area.ALL && (it != Area.PAY || (vm.style != WorkStyle.BUSINESS && vm.style != WorkStyle.INVEST)) && (it != Area.MANAGE || manageOn) }
                                    .forEach { a -> DropdownMenuItem({ Text(a.labelFor(vm.style)) }, onClick = { open = false; pick(a) }) }
                                HorizontalDivider()
                                listOf(Area.ALL, Area.FEATURES, Area.RULES).forEach { a ->
                                    DropdownMenuItem({ Text(a.labelFor(vm.style) + " 도구") }, onClick = { open = false; openTool(a, a.tools.first()) })
                                }
                            }
                        }
                        FilterChip(selected = detail, onClick = { if (!area.detailOnly) detail = !detail }, label = { Text("자세히") })
                        // PC 확인 L2·L4: the locked screens; a tap shows that screen and says where to unlock it.
                        vm.locked.forEach { tab ->
                            androidx.compose.material3.AssistChip(onClick = { pick(Area.of(tab)); detail = true
                                scope.launch {
                                    val name = lockName(tab, vm.style)
                                    if (snack.showSnackbar(name + com.bamnat.app.ui.Fmt.eun(name) + " 잠겨 있어요", actionLabel = "풀기") == SnackbarResult.ActionPerformed) {
                                        vm.locked = vm.locked - tab
                                    }
                                }
                            }, label = { Text("🔒 " + lockName(tab, vm.style)) })
                        }
                        Box(Modifier.size(1.dp, 24.dp).background(MaterialTheme.colorScheme.outlineVariant))
                        // [STU-36] 어두운 화면·큰 글자·색약 눈으로 보기.
                        ConditionChips(seeDark, seeBig, seeCvd, { seeDark = it }, { seeBig = it }, { seeCvd = it })
                    }
                    com.bamnat.app.ui.components.FoldRow("studio.tools", what = "도구 줄", over = true) {
                        // [CNV-07] 숨긴 카드 다시 넣기 (빈 판에서 카드 넣기도 여기서).
                        val toAdd = rememberCardsToAdd(area.tab, vm.style, d)
                        if (!detail && toAdd.page !== PageCards.NONE)
                            FilterChip(selected = panel && panelMode == PanelMode.CARDS, onClick = { openPanel(PanelMode.CARDS) },
                                label = { Text("＋ 카드 넣기" + if (toAdd.hidden.isEmpty()) "" else " ${toAdd.hidden.size}") })
                        // 1.38 PC 스튜디오 확인 (재료까지 5단계): 꾸밈 층의 넣기(아이콘·밤낮 재료·사진)로 바로.
                        if (Tool.DECOR in area.tools)
                            FilterChip(selected = false, onClick = { openTool(area, Tool.DECOR, addIcons = true) }, label = { Text("＋ 아이콘·사진") })
                        // PC 확인 1.37 A5 (앱 전체에서 보관함이 화면 밖): with something kept, 보관함 comes first.
                        area.tools.filter { it != Tool.SHIFT || shiftModes(vm.style).isNotEmpty() }.sortedBy { if (it == Tool.BOX && kept.isNotEmpty()) 0 else 1 }.forEach { t ->
                            FilterChip(selected = panel && panelMode == PanelMode.TOOL && t == tool, onClick = { openTool(area, t) },
                                label = { Text(t.label + if (t == Tool.BOX) " ${kept.size}" else "") })
                        }
                        if (area != Area.ALL) Box {
                            var open by remember { mutableStateOf(false) }
                            val hold = com.bamnat.app.ui.components.LocalFoldHold.current
                            FilterChip(selected = false, onClick = { hold(); open = true }, label = { Text("앱 전체 ▾") })
                            DropdownMenu(open, { open = false }) {
                                Area.ALL.tools.filter { it != Tool.SHIFT || shiftModes(vm.style).isNotEmpty() }.forEach { t ->
                                    DropdownMenuItem({ Text(t.label) }, onClick = { open = false; openTool(Area.ALL, t) })
                                }
                            }
                        }
                        FilterChip(selected = panel && panelMode == PanelMode.LAYERS, onClick = { openPanel(PanelMode.LAYERS) }, label = { Text("부품 목록") })
                        FilterChip(selected = studioAdvanced, onClick = { studioAdvanced = !studioAdvanced }, label = { Text("고급 보기") })
                    }
                }
                if (s.previewMode != com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL) Text("미리 보기: ${s.previewMode.label} 모양이에요",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = 12.dp))
                // [CNV-01] The canvas: the example takes all the room left, at any size ([CNV-02]).
                val spotsOn = !half && !comparing && !peeking
                BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val featurePanel = panel && panelMode == PanelMode.TOOL && tool == Tool.FEATURE
                val big = if (featurePanel) !featureSmall else panelBig
                val panelH = (if (featurePanel && big) maxHeight - 96.dp else totalH * (if (big) 0.66f else 0.42f))
                    .coerceAtMost(maxHeight - 96.dp).coerceAtLeast(160.dp)
                val coverPx = with(androidx.compose.ui.platform.LocalDensity.current) { if (panel) panelH.toPx() else 0f }
                androidx.compose.runtime.SideEffect { zoom.cover = coverPx }
                // [CNV-04] A part picked while the 판 is open (or the 판 opened over one) is brought into the part left in view.
                LaunchedEffect(sel, panel, big) { if (panel) sel?.let { id -> spots.boxes(id).firstOrNull()?.let { zoom.reveal(it) } } }
                // PC 확인 1.37 A9 (색 도구를 열면 고른 부품이 가려짐): a quick tool opened in the bar above brings the part into the view left.
                LaunchedEffect(quick) { if (quick != null) { kotlinx.coroutines.delay(180); sel?.let { id -> spots.boxes(id).firstOrNull()?.let { zoom.reveal(it) } } } }
                Column(Modifier.fillMaxSize()) {
                Box(Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceContainerLowest)) {
                    ZoomCanvas(zoom, Modifier.fillMaxSize(), picking = Modifier
                        .holdToPeek(enabled = s.prevPieces != null) { peeking = it; if (!it) peekMarks = 0 }
                        .partPicking(if (spotsOn) spots else null, sel) { id -> selectPart(id) }, onEmptyTap = { if (sel != null) selectPart(null) }) {
                        Box(Modifier.fillMaxSize()
                            // [STU-41] What the example shows is kept as a picture, for 올리기's 표지.
                            .drawWithContent { shotLayer.record { this@drawWithContent.drawContent() }; drawLayer(shotLayer) }) {
                        CompositionLocalProvider(com.bamnat.app.ui.recipe.LocalMockPick provides ::picked,
                            com.bamnat.app.ui.parts.LocalPartSpots provides (if (spotsOn) spots else null), LocalRealLook provides !shotExample) {
                        Conditioned(seeDark, seeBig, seeCvd, c.settingsRepository.current.accentColor) {
                        if (!detail && !area.detailOnly) {
                            // [TRY-04] 누르고 있으면 전 모양 too: in the draft's places, so only the looks change under the finger.
                            val prev = remember(s.prevPieces, s.past) { vm.previous(s) }
                            val shown = if (comparing) remember(before, now) { LookParts.frame(before, now) } else now
                            if (half && !comparing) CompareCanvas(before, now, vm.style, area.tab, { pick(Area.of(it)) }, 1f, halfAt, onPick = { picked("colors") },
                                onSplit = { halfAt = it })
                            // U15: the step before in the same places and days, what changed ringed (nothing dimmed: it is a quick look).
                            else if (peeking && !comparing && prev != null) CompareCanvas(prev, now, vm.style, area.tab, { pick(Area.of(it)) }, 1f, 0f,
                                onPick = { picked("colors") }, onChanged = { peekMarks = it }, peek = true)
                            else ScaledPhone(shown, vm.style, area.tab, { pick(Area.of(it)) }, 1f, onPick = { picked("colors") })
                        } else {
                            val sl = if (comparing) remember(s.base.screenLooks) { ScreenLooks.decode(s.base.screenLooks) } else looks
                            Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium).background(MaterialTheme.colorScheme.surface)
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium).verticalScroll(rememberScrollState())) {
                                CompositionLocalProvider(
                                    LocalScreenLooks provides sl,
                                    com.bamnat.app.ui.calendar.LocalCalendarView provides view,
                                    LocalMemoLook provides memoLook,
                                    LocalMoneyLook provides moneyLook,
                                    com.bamnat.app.ui.theme.LocalPayLooks provides payLooks,
                                    // [BLU-06] the draft's parts in 자세히 too.
                                    com.bamnat.app.ui.parts.LocalBlueprint provides (MaterialTheme.colorScheme.background.luminance() < 0.5f).let { dark ->
                                        remember(d.parts, dark) { com.bamnat.core.recipe.Blueprint.decode(d.parts).forDark(dark) } },
                                    com.bamnat.app.ui.decor.LocalDecor provides decor,
                                    com.bamnat.app.ui.parts.LocalBlueprintRaw provides remember(d.parts) { com.bamnat.core.recipe.Blueprint.decode(d.parts) },
                                    // [RUL-02] the phone's own days, so 조건부 꾸미기 shows on the weeks below.
                                    com.bamnat.app.logic.LocalDayFacts provides studioFacts,
                                ) {
                                    val t = area.target ?: FreeTarget.ALL
                                    ScreenSkin(t.keyFor(payStyle), Modifier.fillMaxWidth()) {
                                        when (area) {
                                            Area.CALENDAR -> {
                                                // [STU-25] 1.36 빌드 7: the phone's own weeks, as the example phone above shows them.
                                                // [LAY-03] PC 확인 C1: from the week of a day the 꾸밈 층 is pinned to, when it is past these weeks.
                                                val today = remember { com.bamnat.core.time.Today.date() }
                                                val pinnedDay = remember(decor, today) { decor.nextPinned(today)?.takeIf { it.toEpochDay() - today.minusDays((today.dayOfWeek.value % 7).toLong()).toEpochDay() >= 21 } }
                                                val my = com.bamnat.app.ui.recipe.rememberMyWeeks(emptyList(), weeks = 3, from = pinnedDay)
                                                CellWeeks(view, rows = 3, onCell = { i -> lookIndex = i; openTool(Area.CALENDAR, Tool.CELL) },
                                                    start = my?.first, todayDate = my?.today, days = my?.days,
                                                    todayColor = com.bamnat.core.recipe.CalendarTheme.decode(d.calTheme).today?.let { androidx.compose.ui.graphics.Color(it.toInt()) })
                                            }
                                            Area.MEMO -> com.bamnat.app.ui.memo.MemoLookSample(memoLook, rememberMyMemos()) { openTool(Area.MEMO, Tool.MEMO_LOOK) }
                                            Area.MONEY -> MoneySample(moneyLook, rememberMyMoney()) { openTool(Area.MONEY, Tool.MONEY_LOOK) }
                                            Area.PAY -> PaySample(payLooks[payStyle] ?: PayLook(), payStyle) { openTool(Area.PAY, Tool.PAY_LOOK) }
                                            Area.ALARM -> AlarmSample { openTool(Area.ALARM, Tool.COLORS) }
                                            Area.WIDGET -> WidgetSample(WidgetLook.decode(d.widgetLook), vm.style)
                                            Area.MANAGE -> com.bamnat.app.ui.work.ManageSample(d.manageLayout) { openTool(Area.MANAGE, Tool.CARDS) }
                                            else -> ScreenSample(t) { openTool(area, Tool.COLORS) }
                                        }
                                    }
                                }
                            }
                        }
                        }
                        }
                        }
                    }
                    // PC 확인 1.37 A18: over a 판 opened big the hint would hide the strip of example left.
                    if (sel == null && !hintSeen && !(panel && big)) PickHint(Modifier.align(Alignment.TopCenter).padding(top = 8.dp))
                    ZoomControls(zoom, onFocus = sel?.let { id -> { spots.boxes(id).firstOrNull()?.let { zoom.focus(it) }; Unit } },
                        modifier = (if (zoom.controlsOpen) Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp + if (panel) panelH else 0.dp)
                            else Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 4.dp)))
                }
                // Lines about the example, under it.
                Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp).animateContentSize()) {
                    openedLine?.let { line ->
                        LaunchedEffect(line) { kotlinx.coroutines.delay(3500); openedLine = null }
                        Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    }
                    if (peeking && !comparing) Text("바로 전 모양이에요" + (if (peekMarks > 0) " · 바뀐 곳 ${peekMarks}곳 테두리" else "") + ". 손을 떼면 지금 모양이에요", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.CenterHorizontally))
                    // PC 확인 1.36 (한 색 '밝은 화면'인데 어두운 화면 미리 보기가 밝은 채): a look with its own light colours stays light on a
                    // dark phone too (ScreenSkin), so the example is right; it says why, and where the dark one is.
                    if (seeDark && looks.of(com.bamnat.core.recipe.ScreenLooks.ALL).bg?.let { com.bamnat.core.recipe.Palette.luminance(it) >= 0.5 } == true)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("색을 정해 둔 테마라 휴대폰이 어두워도 이렇게 밝게 보여요. 어둡게 하려면 한 색에서 ‘어두운 화면’을 골라요",
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (!(panel && area == Area.ALL && tool == Tool.SEED)) TextButton(onClick = { openSeed() }) { Text("한 색 열기") }
                        }
                    // [STU-36] With a condition on, what does not read is said under the example.
                    if (seeDark || seeBig || seeCvd) vm.hardToRead().takeIf { it.isNotEmpty() }?.let { bad ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("글자가 잘 안 보일 수 있는 화면 ${bad.size}곳", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.weight(1f))
                            TextButton(onClick = { vm.fixInk() }) { Text("글자색 고치기") }
                        }
                    }
                    if (comparing) Text("고치기 전 모양이에요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    else if (half && !detail) Text("왼쪽이 지금 고친 모양, 오른쪽이 고치기 전이에요", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                // [STU-34] 만들던 것 이어서 하기: offered once on a fresh start, until anything is changed or it is shut.
                // PC 확인 1.37 A4 (안 입히고 나간 뒤 다른 화면의 스튜디오까지 따라와 예시를 가림): not when opened for a page's cards, and once shut
                // it stays shut for that 만들던 것 (until it changes).
                val shutAt = remember(resumeShut) { runCatching { studioPrefs.getLong("resumeShut", 0L) }.getOrDefault(0L) }
                // PC 확인 1.37 A10 (첫 색을 고르는 순간 띠가 사라져 예시가 커지고 다음 누름이 엉뚱한 색으로): the band goes when a part or a 판 is picked,
                // before the first change, so nothing moves between picks.
                resumable?.takeIf { !resumeShut && vm.startArea == null && it.updatedAt != shutAt && s.past.isEmpty() && s.draftId == 0L && sel == null && !panel }?.let { r ->
                    Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(draftName(r.body).let { n -> "만들던 ‘$n’${com.bamnat.app.ui.Fmt.iga("$n’")} 남아 있어요" } + " · ${ago(r.updatedAt)}",
                                style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                            TextButton(onClick = { vm.resume(r.id) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이어서 하기") }
                            TextButton(onClick = { runCatching { studioPrefs.edit().putLong("resumeShut", r.updatedAt).apply() }; resumeShut = true },
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                        }
                    }
                }
                }
                // [CNV-04] 더 보기 판 (PC 확인 1.37 A1: 크게 열면 캔버스가 줄었어요): over the canvas, which keeps its size; the picked part is moved into the part left in view.
                if (panel) Surface(tonalElevation = 2.dp, shadowElevation = 6.dp, shape = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize),
                    modifier = Modifier.fillMaxWidth().height(panelH).align(Alignment.BottomCenter)) {
                    Column(Modifier.fillMaxSize()) {
                        val t = if (tool in area.tools) tool else area.tools.first()
                        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(when (panelMode) {
                                PanelMode.PART -> sel?.let { "${BlueprintParts.LABELS[it] ?: it} 자세히" } ?: "부품"
                                PanelMode.LAYERS -> "층 · 화면의 부품"
                                PanelMode.CARDS -> "${area.labelFor(vm.style)} · 카드 넣기"
                                PanelMode.TOOL -> "${area.labelFor(vm.style)} · ${t.label}"
                            }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).semantics { heading() })
                            TextButton(onClick = { if (featurePanel) setFeatureSmall(!featureSmall) else setPanelBig(!panelBig) },
                                modifier = Modifier.heightIn(min = 48.dp)) { Text(if (big) "작게" else "크게") }
                            TextButton(onClick = { panel = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("닫기") }
                        }
                        HorizontalDivider()
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            when (panelMode) {
                                PanelMode.CARDS -> {
                                    val toAdd = rememberCardsToAdd(area.tab, vm.style, d)
                                    if (toAdd.hidden.isEmpty() && toAdd.fit.isEmpty()) Text("숨긴 카드가 없어요. 카드를 눌러 ‘숨기기’를 고르면 여기로 와요",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    toAdd.hidden.forEach { cid ->
                                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text(toAdd.page.label(cid), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                                            TextButton(onClick = { vm.change { toAdd.page.show(it, cid, true).sameLinesAs(s.base) }; selectPart(StudioEdit.cardId(area.tab, cid)) },
                                                modifier = Modifier.heightIn(min = 48.dp)) { Text("넣기") }
                                        }
                                    }
                                    // [CNV-10] 빈 판에서 시작: every card this page can move or hide put away, to put back one by one.
                                    val movableHere = remember(toAdd.page, d) { toAdd.page.movable(d) }
                                    if (movableHere.isNotEmpty()) OutlinedButton(onClick = {
                                        vm.change { x -> movableHere.fold(x) { acc, cid -> toAdd.page.show(acc, cid, false) }.sameLinesAs(s.base) }
                                        selectPart(null)
                                    }, modifier = Modifier.heightIn(min = 48.dp)) { Text("빈 판으로 (카드 ${movableHere.size}개 모두 숨기기)") }
                                    val surface = fitSurface(area.tab)
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    if (surface != null) com.bamnat.app.ui.components.FitMore(toAdd.fit, { id, on ->
                                        com.bamnat.app.ui.guide.setFitAdded(ctx, com.bamnat.app.data.PersonalCalendar.MAIN, surface, id, on)
                                    })
                                    // [CNV-10] 내 틀: 카드 자리와 부품만 보관함에, 원하면 장터에.
                                    HorizontalDivider()
                                    Text("내 틀", style = MaterialTheme.typography.titleSmall)
                                    Text("카드 자리·숨긴 카드·부품 모양만 담아요. 색은 빼고 담아서 어느 테마에든 얹을 수 있어요",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    var frameName by rememberSaveable { mutableStateOf("") }
                                    OutlinedTextField(frameName, { frameName = it.take(24) }, label = { Text("틀 이름") }, singleLine = true,
                                        placeholder = { Text("내 틀") }, modifier = Modifier.fillMaxWidth())
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        androidx.compose.material3.Button(onClick = { vm.keepFrame(frameName) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 틀로 담기") }
                                        if (onMarket != null) OutlinedButton(onClick = {
                                            val body = vm.frameBody(frameName)
                                            if (body == null) openedLine = "아직 옮기거나 숨긴 카드가 없어 올릴 틀이 없어요"
                                            else { publishRound++; publishing = Triple(body, "", null) }
                                        }, modifier = Modifier.heightIn(min = 48.dp)) { Text("장터에 틀 올리기") }
                                    }
                                }
                                PanelMode.LAYERS -> {
                                    val partIds = partsGroupOf(area.name).let { g ->
                                        com.bamnat.core.recipe.BlueprintParts.IDS.filter { it == g || it.startsWith("$g.") || (g == "app" && it.startsWith("app")) }
                                    }
                                    val bp = remember(d.parts) { com.bamnat.core.recipe.Blueprint.decode(d.parts) }
                                    Text("이름을 누르면 화면에서 그 부품을 골라요. ●는 꾸민 부품이에요", style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    LayerTree(treeRows(partIds, bp, decor, if (Tool.DECOR in area.tools) area.decorPlaces else emptyList(), vm.style),
                                        onPart = { p -> selectPart(p) },
                                        onDecor = { i -> decorFocus = i; openTool(area.takeIf { Tool.DECOR in it.tools } ?: Area.CALENDAR, Tool.DECOR) })
                                }
                                PanelMode.PART -> sel?.takeIf { StudioEdit.card(it) == null }?.let { id ->
                                    PartsTool(partsGroupOf(areaOfPart(id)), d.parts, focus = id, focusOn = selOn,
                                        onFocus = { pid, pon -> if (pid != sel) { sel = pid; selWhen = null; quick = null }; selOn = pon },
                                        onUploadPart = { unit -> vm.uploadPart(unit)?.let { body -> publishRound++; publishing = Triple(body, "", null) } }, advanced = studioAdvanced,
                                        conditionFirst = condFirst, conditionAsk = condAsk, onWhen = { w -> selWhen = w },
                                        rulesUsing = { f -> com.bamnat.core.formula.Logic.decode(d.logic).rules.filter { it.cond == f }.map { it.name } },
                                        onOpenRules = { openTool(Area.RULES, Tool.RULES) },
                                        onTogether = { partsTogether = it }) { v -> vm.change { it.copy(parts = v) } }
                                }
                                PanelMode.TOOL -> {
                                    area.target?.takeIf { it.restricted }?.let { Text(it.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                    val key = (area.target ?: FreeTarget.ALL).keyFor(payStyle)
                                    fun saveView(v: CalendarView) = vm.change { with(CalendarViewSettings) { it.withView(v) } }
                                    when (t) {
                                        Tool.CELL -> CellDesigner(view, lookIndex, { lookIndex = it }, ::saveView)
                                        Tool.PATTERN -> PatternEditor(view, calendarRoles(), ::saveView)
                                        Tool.VIEW -> com.bamnat.app.ui.calendar.CalendarViewEditor(view, ::saveView, sample = {})
                                        Tool.DAYS -> com.bamnat.app.ui.settings.CalendarThemeRows(d.calTheme) { v -> vm.change { it.copy(calTheme = v) } }
                                        Tool.PHOTO -> com.bamnat.app.ui.settings.PhotoLookRows(d.photoLook) { v -> vm.change { it.copy(photoLook = v) } }
                                        Tool.COLORS -> {
                                            ScreenLookEditor(area.target ?: FreeTarget.ALL, looks.looks[key] ?: ScreenLook(), looks.of(key)) { l ->
                                                vm.change { it.copy(screenLooks = ScreenLooks.decode(it.screenLooks).with(key, l).encode()) }
                                            }
                                            // [STU-33] 스타일 복사: this screen's colours to every screen, copy and paste, back to '앱 전체'.
                                            ScreenCopyRow(vm, d, key) { openTool(Area.ALL, Tool.SEED) }
                                        }
                                        Tool.SEED -> SeedTool(vm, d)
                                        Tool.PARTS -> PartsTool(partsGroupOf(area.name), d.parts, focus = partPick?.first, focusOn = partPick?.second,
                                            onFocus = { pid, pon -> partPick = null; sel = pid; selOn = pon },
                                            onUploadPart = { unit -> vm.uploadPart(unit)?.let { body -> publishRound++; publishing = Triple(body, "", null) } }, advanced = studioAdvanced,
                                            onTogether = { partsTogether = it }) { v -> vm.change { it.copy(parts = v) } }
                                        Tool.SHIFT -> ShiftLooksTool(vm, d, s.previewMode)
                                        Tool.CARDS -> CardsTool(area, vm.style, d) { f -> vm.change(f) }
                                        Tool.MEMO_LOOK -> MemoLookEditor(memoLook) { l -> vm.change { it.copy(memoLook = l.encode()) } }
                                        Tool.MONEY_LOOK -> MoneyLookEditor(moneyLook) { l -> vm.change { it.copy(moneyLook = l.encode()) } }
                                        Tool.PAY_LOOK -> {
                                            Text("근무 유형마다 따로 꾸며요", style = MaterialTheme.typography.labelLarge)
                                            com.bamnat.app.ui.components.FoldRow("studio.paystyle", what = "근무 유형") {
                                                WorkStyle.entries.forEach { st -> FilterChip(selected = st == payStyle, onClick = { payStyle = st }, label = { Text(st.label.substringBefore(' ')) }) }
                                            }
                                            PayLookEditor(payLooks[payStyle] ?: PayLook()) { l ->
                                                vm.change { x -> x.copy(payLooks = PayLook.encodeAll(PayLook.decodeAll(x.payLooks) + (payStyle to l))) }
                                            }
                                        }
                                        Tool.TABS -> TabsEditor(d.tabOrder, vm.style) { list -> vm.change { it.copy(tabOrder = list.orEmpty()) } }
                                        Tool.CORNERS -> CornersEditor(PhotoLook.decode(d.photoLook).corners) { k ->
                                            vm.change { it.copy(photoLook = PhotoLook.decode(it.photoLook).copy(corners = k).encode()) }
                                        }
                                        Tool.WIDGET -> WidgetTool(WidgetLook.decode(d.widgetLook)) { w -> vm.change { it.copy(widgetLook = w.encode()) } }
                                        Tool.BOX -> BoxTool(kept, area, onLay = vm::layer, onForget = { c.wardrobe.forgetKept(it.id) }, laid = s.layered.map { it.id }.toSet(), onTakeOff = vm::unlayer,
                                            onMarket = onMarket?.let { go -> { go(area.marketKind) } }, style = vm.style)
                                        Tool.FEATURE -> {
                                            // [RUL-06] PC 1차 확인: '규칙·단추'를 못 찾았어요.
                                            OutlinedButton(onClick = { openTool(Area.RULES, Tool.RULES) }, modifier = Modifier.fillMaxWidth()) { Text("＋ 이럴 때 이렇게: 규칙·단추 만들기") }
                                            FeaturesTool(vm, s.extras)
                                        }
                                        Tool.LOGIC, Tool.RULES, Tool.BUTTONS -> {
                                            // PC D10: 규칙·단추는 고치면 바로 휴대폰에 저장돼요 (설정과 같은 목록).
                                            Text("규칙·단추는 고치면 바로 저장돼요. 입히지 않아도 돼요", style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            com.bamnat.app.logic.LogicEditor(com.bamnat.core.formula.Logic.decode(d.logic), { l -> vm.changeLogic(l) }, show = when (t) { Tool.RULES -> com.bamnat.app.logic.LogicShow.RULES; Tool.BUTTONS -> com.bamnat.app.logic.LogicShow.BUTTONS; else -> com.bamnat.app.logic.LogicShow.ALL },
                                                usedInParts = { f -> com.bamnat.core.recipe.Blueprint.decode(d.parts).lines.any { it["when"] == f } },
                                                onOpenParts = { openTool(Area.RULES, Tool.COND) })
                                            // [RUL-07] 규칙·단추만 따로 장터에 올려요(테마 없이). 받는 사람은 보관함에서 얹어요.
                                            if (!com.bamnat.core.formula.Logic.decode(d.logic).isEmpty) {
                                                OutlinedButton(onClick = { vm.uploadLogic()?.let { body -> publishing = Triple(body, "", null) } },
                                                    modifier = Modifier.fillMaxWidth()) { Text("규칙·단추만 장터에 올리기") }
                                            }
                                        }
                                        Tool.COND -> {
                                            Text("조건이 맞는 날의 칸만 다르게 꾸며요. 예: 야간이고 메모에 #인수인계가 있으면 테두리 빨강. 메모 카드·가계부 한 줄도 화면에서 누르고 ‘언제 ▾’에서 조건을 붙여요",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            PartsTool("cal", d.parts, focus = "cal.cell", onChange = { v -> vm.change { it.copy(parts = v) } },
                                                conditionFirst = true, rulesUsing = { f -> com.bamnat.core.formula.Logic.decode(d.logic).rules.filter { it.cond == f }.map { it.name } },
                                                onOpenRules = { openTool(Area.RULES, Tool.RULES) })
                                        }
                                        Tool.DECOR -> com.bamnat.app.ui.decor.DecorTool(decor, area.decorPlaces, { label, nd ->
                                            if (label == null) vm.change { it.copy(decor = nd.encode()) } else vm.changeMerged(label) { it.copy(decor = nd.encode()) }
                                        }, vm::undo, advancedIn = studioAdvanced, onAdvanced = { studioAdvanced = it }, focus = decorFocus, onFocused = { decorFocus = null },
                                            buttons = remember(d) { com.bamnat.app.ui.decor.DecorTap.buttons(d) }, addAsk = decorAdd)
                                        // [MAT-05] 1.36 앱 전체 글꼴: every screen's text, 화면마다 따로는 ‘색·배경’에서.
                                        Tool.FONT -> {
                                            val all = looks.looks[ScreenLooks.ALL] ?: ScreenLook()
                                            com.bamnat.app.ui.settings.FontRow("앱 전체 글꼴", all.font) { f ->
                                                vm.change { it.copy(screenLooks = ScreenLooks.decode(it.screenLooks).with(ScreenLooks.ALL, all.copy(font = f)).encode()) }
                                            }
                                            Text("화면마다 다른 글꼴은 각 화면의 ‘색·배경’에서, 글자 하나하나는 화면에서 눌러 ‘글꼴’로 골라요",
                                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        // [MAT-07] 1.36 움직임.
                                        Tool.MOTION -> com.bamnat.app.ui.settings.MotionEditor(com.bamnat.core.recipe.Motion.decode(d.motion)) { m ->
                                            vm.change { it.copy(motion = m.encode()) }
                                        }
                                    }
                                }
                            }
                            TextButton(onClick = { wish = true }) { Text("원하는 꾸미기가 없나요? 부탁하기") }
                            Spacer(Modifier.height(24.dp))
                        }
                    }
                }
                }
            }
            com.bamnat.app.ui.components.FoldOverlay(foldHost)
            }
        }
        }
    }

    if (wish) StudioWish(where = "${area.label} › ${tool.label}", style = vm.style, onDismiss = { wish = false }) { sent ->
        wish = false
        // 2.0.0 [RVQ-29] 검토 SVC-17·T22-07: 'N명' counts different people (one internet connection is one), and once enough have
        // asked the operator words it shortly before it goes up to 장터 '만들어 주세요' (it no longer goes up by itself).
        val gathered = sent?.takeIf { it.studioGoal > 0 }?.let { q ->
            if (q.studioAskers >= q.studioGoal) "같은 부탁이 ${q.studioAskers}명 모였어요. 운영자가 짧게 다듬어 장터 '만들어 주세요'에 올려요"
            else "같은 부탁 ${q.studioAskers}/${q.studioGoal}명 모였어요. ${q.studioGoal}명이 되면 운영자가 다듬어 장터 '만들어 주세요'에 올리고, 모이지 않아도 운영자가 함께 봐요"
        }
        scope.launch {
            snack.showSnackbar(when {
                sent == null -> "보내지 못했어요. " + com.bamnat.app.ui.components.NetWords.BUSY
                gathered != null -> "보냈어요. $gathered. 풀리면 알림과 '내 문의'로 알려 드려요"
                else -> "보냈어요. 같은 부탁이 모이면 운영자가 다듬어 장터 '만들어 주세요'에 올리고, 풀리면 알려 드려요"
            })
        }
    }

    if (leaving) AlertDialog(
        onDismissRequest = { leaving = false },
        title = { Text("바꾼 것을 어떻게 할까요?") },
        text = { Text("입히기를 누르기 전이라 내 앱은 아직 그대로예요") },
        // F-91: the choices one under another at full width, so no two labels share a line (큰 글자에서 겹치던 것).
        confirmButton = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                if (!vm.needsAd()) TextButton(onClick = { leaving = false; vm.wear().invokeOnCompletion { onBack() } }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("내 앱에 입히고 나가기")
                }
                TextButton(onClick = { leaving = false; saving = true }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 테마로 저장하고 나가기") }
                TextButton(onClick = { leaving = false; vm.keepNow(); onBack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 앱엔 안 입히고 나가기") }
                TextButton(onClick = { leaving = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("계속 꾸미기") }
            }
        },
    )
    startAsk?.let { go ->
        AlertDialog(
            onDismissRequest = { startAsk = null },
            text = { Text("지금 바꾼 것을 버리고 새로 시작할까요? 내 앱은 그대로예요") },
            confirmButton = { TextButton(onClick = { startAsk = null; go() }) { Text("새로 시작") } },
            dismissButton = { TextButton(onClick = { startAsk = null }) { Text("계속 꾸미기") } },
        )
    }
    inkAsk?.let { go ->
        val bad = vm.hardToRead().map { k -> Area.entries.firstOrNull { it.target?.keyFor(payStyle) == k }?.label ?: if (k.startsWith("pay.")) "급여" else k }
        AlertDialog(
            onDismissRequest = { inkAsk = null },
            title = { Text("글자가 잘 안 보일 수 있어요") },
            text = { Text("${bad.joinToString(", ")} 화면의 글자색이 바탕과 비슷해요. 고치면 그 화면 글자는 바탕에 맞춰 검정이나 흰색으로 보여요") },
            confirmButton = { TextButton(onClick = { inkAsk = null; vm.fixInk(); go() }) { Text("글자색 고치고 계속") } },
            dismissButton = { TextButton(onClick = { inkAsk = null; go() }) { Text("그대로 계속") } },
        )
    }
    // [STU-37] Leaving the 근무에 따라 tool draws the example as usual again.
    LaunchedEffect(tool, area) { if ((tool != Tool.SHIFT || area != Area.ALL) && s.previewMode != com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL) vm.previewAs(com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL) }
    if (feelOpen) FeelStartDialog(onDismiss = { feelOpen = false }) { scheme, cell, feel ->
        feelOpen = false
        guarded { vm.seedFeel = feel.variant to feel.dark; vm.startFeel("‘${feel.label}’ 느낌") { feelDraft(it, scheme, cell) }
            vm.feelNow = null; vm.feelStart = vm.state.value.origin.title to feel.variant; pick(Area.ALL) }
    }
    if (history) HistorySheet(s, onBack = { i -> vm.backTo(i) }, onDismiss = { history = false })
    if (changes) ChangesSheet(vm, s, before, now, onGo = { a -> pick(Area.valueOf(a)); changes = false }, onDismiss = { changes = false })
    uploadWhat?.let { tabs ->
        // 사용자 2026-11-01: 테마 (모든 곳을 한 벌로) or any one tab changed, alone, as a 기능별 탭 — one tab changed starts on that tab.
        val single = vm.changedTab()
        val featuresOnly = StudioViewModel.FEATURES_ONLY
        // A 기능 with no look changed starts on 기능만, so a 가계부 틀 goes to 장터 '기능별 › 가계부', not under 테마.
        var choice by remember(tabs) { mutableStateOf(single ?: featuresOnly.takeIf { tabs.isEmpty() && s.extras.isNotEmpty() }) }
        AlertDialog(
            onDismissRequest = { uploadWhat = null },
            title = { Text("무엇을 올릴까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (tabs + listOfNotNull(featuresOnly.takeIf { s.extras.isNotEmpty() }) + listOf<String?>(null)).forEach { v ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button) { choice = v }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = choice == v, onClick = { choice = v })
                            Column {
                                Text(when (v) { null -> "테마 전체"; featuresOnly -> "기능만 (" + s.extras.keys.sortedBy { it.ordinal }.joinToString(", ") { it.label } + ")"
                                    else -> "${com.bamnat.app.ui.recipe.tabName(v)} 탭만" }, style = MaterialTheme.typography.bodyLarge)
                                Text(if (v == featuresOnly) "장터 ‘탭별 작품 › 기능’에 올라가요. 모양은 빠지고 기능 ${s.extras.size}개만 가요"
                                    else if (v != null) "장터 ‘탭별 작품’에 올라가요. 받는 분은 지금 테마 위에 이 탭만 얹어요" +
                                    (if (s.extras.isNotEmpty()) ". 기능 ${s.extras.size}개는 빠져요" else "")
                                    // PC 확인 1.36 (한 화면만 바꾸고 테마 전체로 올리면 장터 어디에도 안 보임): one screen changed is one tab in 장터, said.
                                    else if (single != null && s.extras.isEmpty()) "한 화면(${com.bamnat.app.ui.recipe.tabName(single)})만 바꿔서 장터에서는 ‘탭별 작품 › ${com.bamnat.app.ui.recipe.tabName(single)}’에 올라가요"
                                    else "장터 ‘테마’에 올라가요. 모든 화면의 모양" + (if (s.extras.isNotEmpty()) "과 기능 ${s.extras.size}개가" else "이") + " 함께 가요",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                    // PC 확인 1.36 (3초 뒤 안내가 한 번도 안 보임: 올리기 화면을 그리는 동안은 화면이 멈춰 있음): said at once with 준비 중.
                    if (preparing) Text("처음엔 몇 초 걸릴 수 있어요. 잠시만 기다려 주세요", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            // PC 확인 W37 1.36 (고르기 창 '다음' 뒤 8초 동안 아무 표시 없음): 준비 중 with a spinner, and this window stays until 올리기 is up.
            confirmButton = { TextButton(onClick = {
                preparing = true
                scope.launch {
                    val (body, parent) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) { vm.upload(choice) }
                    publishRound++
                    publishing = Triple(body, parent, if (choice != null) null else vm.nextOf())
                }
            }, enabled = !preparing) {
                if (preparing) { androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                Text(if (preparing) "준비 중…" else "다음")
            } },
            dismissButton = { TextButton(onClick = { uploadWhat = null }, enabled = !preparing) { Text("닫기") } },
        )
    }
    publishing?.let { (body, parent, own) ->
        val up = com.bamnat.app.ui.appViewModel(key = "studio-up-${body.hashCode()}-$own-$publishRound") {
            com.bamnat.app.ui.recipe.RecipeMakeViewModel(it, com.bamnat.app.data.PersonalCalendar.MAIN, vm.state.value.draftId, body, parent.takeIf { own == null }, null, own)
        }
        // PC e20cfe53: 바뀐 것 없이 다시 올리기를 누르면 지난 '올렸어요' 끝 화면 대신, 바뀐 곳이 없다고 말하고 지난번 올린 것을 열어요.
        val already = remember(publishing) { up.state.value.sent }
        if (already != null) {
            NoChangeDialog(already, onOpen = { publishing = null; onOpen(already.code) }, onDismiss = { publishing = null })
            return@let
        }
        // [STU-21] 올리기 over the studio (레시피 만들기 steps 2–5): 스튜디오로 or closing comes back with the draft as it was.
        com.bamnat.app.ui.components.Dialog(onDismissRequest = { publishing = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) {
                // W37: once 올리기 is drawn, the 무엇을 올릴까요 window and 준비 중 go.
                LaunchedEffect(Unit) { uploadWhat = null; preparing = false }
                // 빌드 751b88f6 PC 확인 (올린 뒤 나가도 '바꾼 것을 어떻게 할까요?'): once it is up, what was changed counts as kept,
                // whether 레시피 보기 or 뒤로 follows.
                val upState by up.state.collectAsState()
                LaunchedEffect(upState.sent != null) { upState.sent?.let { sent -> vm.markKept(); sentBodies = sentBodies + (body to sent) } }
                com.bamnat.app.ui.recipe.RecipeMakeScreen(up, onBack = { publishing = null }, onOpen = { code -> publishing = null; vm.markKept(); onOpen(code) }, finishOnly = true)
            }
        }
    }
    if (nothingToSend) AlertDialog(onDismissRequest = { nothingToSend = false }, title = { Text("올릴 것이 없어요") },
        text = { Text("모양을 바꾸거나 기능(가계부 틀 등)을 켠 뒤 올려 주세요.") },
        confirmButton = { TextButton(onClick = { nothingToSend = false }) { Text("확인") } })
    noChange?.let { sent -> NoChangeDialog(sent, onOpen = { noChange = null; onOpen(sent.code) }, onDismiss = { noChange = null }) }
    if (saving) SaveDialog(vm, s, onDismiss = { saving = false }) { name, toPlaza ->
        saving = false
        scope.launch { vm.save(name, toPlaza).join(); if (leaving) onBack() }
    }
}

/** PC e20cfe53·006d0aec: 올리기 with nothing changed since the last time it went up. */
@Composable
private fun NoChangeDialog(sent: com.bamnat.app.recipe.Sent, onOpen: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("바뀐 곳이 없어요") },
        text = { Text("지난번에 올린 그대로예요 (코드 ${sent.code} · ${sent.version}판). 스튜디오에서 고친 뒤 올리면 새로 올라가요",
            style = MaterialTheme.typography.bodyMedium) },
        confirmButton = { TextButton(onClick = onOpen) { Text("지난번 올린 것 보기") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("스튜디오로") } },
    )
}

/** 내 앱에 입히기: an ad first when the look keeps someone else's days ([StudioViewModel.needsAd]). */
/**
 * 1.36 [GRW-01] 스튜디오 요구: 보는 칸·도구를 앞에 붙여 개발자에게 보내요(공개 안 함). 서버가 같은 부탁끼리 묶고, 3명이 모이면 장터
 * '만들어 주세요'에, 풀리거나 기본 도구가 되면 문의 답으로 알려 줘요. 고치던 꾸미기는 그대로예요.
 */
@Composable
private fun StudioWish(where: String, style: WorkStyle?, onDismiss: () -> Unit, onSent: (com.bamnat.app.company.Inquiry?) -> Unit) {
    val c = LocalContext.current.appContainer
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var text by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val tools by androidx.compose.runtime.produceState(emptyList<com.bamnat.app.recipe.CommonTool>()) {
        value = runCatching { c.recipeApi.tools(style) }.getOrDefault(emptyList())
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("원하는 꾸미기 부탁하기") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("[$where]에서 무엇을 하고 싶은지 적어 주세요. 같은 부탁이 모이면 다른 분이 만들어 주거나 기본 도구가 돼요. 적은 글은 운영자만 봐요",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = text, onValueChange = { text = it.take(500) }, minLines = 3, modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("예: 야간 근무 칸에 달 모양을 넣고 싶어요") })
                if (tools.isNotEmpty()) {
                    Text("먼저 모두의 도구를 살펴보세요", style = MaterialTheme.typography.labelLarge)
                    tools.take(3).forEach { t -> Text("• ${t.title} · ${t.users}명이 써요", style = MaterialTheme.typography.bodySmall) }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = !busy && text.isNotBlank(), onClick = {
                busy = true
                scope.launch {
                    val sent = runCatching {
                        c.inquiryApi.send(c.companyStore.device, com.bamnat.app.company.InquiryKind.STUDIO, "[스튜디오 › $where] ${text.trim()}",
                            com.bamnat.app.ui.company.deviceInquiryInfo().copy(studio = style?.name, public = false))
                    }.getOrNull()
                    if (sent != null) {
                        // PC 확인 8d290ec3: 답(풀림·기본 도구·거절)이 오면 알림과 '내 문의'에 보이게, 다른 문의처럼 표시해요.
                        c.inquiryStore.asked = true
                        c.telemetry.event(com.bamnat.core.stats.UsageEvent.INQUIRY.key)
                        com.bamnat.app.company.InquiryReplyJob.schedule(c.appContext)
                    }
                    busy = false
                    onSent(sent)
                }
            }) { Text(if (busy) "보내는 중" else "보내기") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("닫기") } },
    )
}

@Composable
private fun WearButton(vm: StudioViewModel, busy: Boolean, check: (() -> Unit) -> Unit) {
    val c = LocalContext.current.appContainer
    var note by remember { mutableStateOf<String?>(null) }
    Column {
        if (vm.needsAd()) com.bamnat.app.ads.ThemeAdButton(
            "광고 보고 입히기", note ?: "다른 분의 테마나 조각이 들어 있어 30일 동안 입어요",
            onReward = { check { vm.wear() } },
            onQuit = { note = "광고를 끝까지 봐야 입힐 수 있어요" },
            onOffline = { note = "인터넷에 연결되면 다시 해 주세요" },
            onNoAd = { reason -> check { vm.wear(); c.wardrobe.counted(reason) } },
        ) else Button(onClick = { check { vm.wear() } }, enabled = !busy, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text(if (busy) "입히는 중…" else "내 앱에 입히기")
        }
    }
}

@Composable
private fun SaveDialog(vm: StudioViewModel, s: StudioViewModel.State, onDismiss: () -> Unit, onSave: (String, Boolean) -> Unit) {
    val own = vm.ownStart
    var name by remember { mutableStateOf(own?.title ?: "내 테마 ${vm.mineCount() + 1}") }
    var toPlaza by remember { mutableStateOf(true) }
    val ownMake = remember { vm.ownMake() }
    val mixed = remember { vm.mixedSources() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("내 테마로 저장") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it.take(30) }, label = { Text("이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // [THM-08] 1.36 섞은 테마.
                if (!ownMake && mixed.size >= 2) Text("${mixed.joinToString("·") { "‘$it’" }}의 조각을 섞었어요. 내 테마에 내 이름으로 담기고 출처도 함께 적혀요. " +
                    "직접 바꾼 곳이 ${LookParts.OWN_PARTS}곳보다 적어 입을 때는 30일 규칙이고(프로는 광고 없이), 장터에는 수정판으로만 올라가요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                else if (!ownMake) Text("다른 분의 테마에서 직접 바꾼 곳이 ${LookParts.OWN_PARTS}곳보다 적어요. 그 테마의 조합으로 보관함에 담기고, 입힐 때는 그 테마처럼 30일이에요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                else if (vm.inPlaza() && name == own?.title) Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(toPlaza, { toPlaza = it })
                    Text("장터에 올린 판도 바꾸기. 입은 분들께는 바꿀지 물어봐요", style = MaterialTheme.typography.bodySmall)
                } else Text("혼자 쓰는 내 테마는 다른 테마처럼 처음 2번 무료, 그 뒤 광고 한 번에 30일이에요. 장터에 올리면 광고 없이 입어요. 무료는 ${vm.outfitLimit()}벌까지예요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, toPlaza) }) { Text("저장") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("취소") } },
    )
}

/** A page's cards in order and shown or hidden, as 카드 배치 does on the phone, on the draft. */
@Composable
private fun CardsTool(area: Area, style: WorkStyle?, d: com.bamnat.app.data.AppSettings, change: ((com.bamnat.app.data.AppSettings) -> com.bamnat.app.data.AppSettings) -> Unit) {
    when (area) {
        Area.CALENDAR -> PartsEditor("달력 화면 카드", CalendarPart.entries.filter { it != CalendarPart.ASK && it != CalendarPart.JOURNAL && it != CalendarPart.MONTH && fitsHere(FitSurface.CALENDAR_PARTS, it.id, style) }
            .map { Triple(it.id, it.labelFor(style), it.movable) }, d.calendarLayout.ifEmpty { null }) { line -> change { it.copy(calendarLayout = line.orEmpty()) } }
        Area.MONEY -> PartsEditor("가계부 카드", MoneyPart.entries.filter { fitsHere(FitSurface.MONEY_PARTS, it.id, style) }.map { Triple(it.id, it.label, it.movable) },
            d.moneyLayout.ifEmpty { null }) { line -> change { it.copy(moneyLayout = line.orEmpty()) } }
        Area.MEMO -> PartsEditor("메모 화면", MemoPart.entries.map { Triple(it.id, it.label, it.movable) }, d.memoLayout.ifEmpty { null }) { line ->
            change { it.copy(memoLayout = line.orEmpty()) }
        }
        Area.STATS -> PartsEditor("통계 카드", StatsPart.entries.filter { fitsHere(FitSurface.STATS_PARTS, it.id, style) }.map { Triple(it.id, it.label, it.movable) },
            d.statsLayout.ifEmpty { null }) { line -> change { it.copy(statsLayout = line.orEmpty()) } }
        Area.MANAGE -> PartsEditor("팀·가게 관리 카드", com.bamnat.app.ui.work.ManagePart.editable.map { Triple(it.id, it.label, true) }, d.manageLayout.ifEmpty { null },
            note = "할 일은 늘 맨 위에 있어요. 이 순서는 이 휴대폰에만 남고 테마·작품에는 들어가지 않아요") { line -> change { it.copy(manageLayout = line.orEmpty()) } }
        else -> Unit
    }
}

@Composable
private fun WidgetTool(w: WidgetLook, onChange: (WidgetLook) -> Unit) {
    Text("바탕", style = MaterialTheme.typography.labelLarge)
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("system" to "휴대폰 따라", "light" to "밝게", "dark" to "어둡게").forEach { (k, label) ->
            FilterChip(selected = w.theme == k, onClick = { onChange(w.copy(theme = k)) }, label = { Text(label) })
        }
    }
    Text("바탕 투명도", style = MaterialTheme.typography.labelLarge)
    com.bamnat.app.ui.components.FoldRow("studio.widget1") {
        listOf(100 to "불투명", 85 to "조금 비침", 65 to "반쯤 비침", 40 to "많이 비침").forEach { (a, label) ->
            FilterChip(selected = w.alpha == a, onClick = { onChange(w.copy(alpha = a)) }, label = { Text(label) })
        }
    }
    // [STU-38] 1.36 위젯 꾸미기 넓히기: 바탕색·글자색·모서리·글자 크기.
    com.bamnat.app.ui.settings.ColorPick("바탕색", w.bg) { onChange(w.copy(bg = it)) }
    com.bamnat.app.ui.settings.ColorPick("글자색", w.ink) { onChange(w.copy(ink = it)) }
    val wi = w.ink
    val wb = w.bg
    if (wi != null && wb != null && com.bamnat.core.recipe.Palette.inkOn(wi, wb) != wi) Text("고른 글자색은 바탕에서 잘 안 보여서 검정이나 흰색으로 보여요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
    Text("모서리", style = MaterialTheme.typography.labelLarge)
    com.bamnat.app.ui.components.FoldRow("studio.widget2") {
        listOf(null to "처음대로", 4 to "각지게", 12 to "조금 둥글게", 28 to "아주 둥글게").forEach { (r, label) ->
            FilterChip(selected = w.corner == r, onClick = { onChange(w.copy(corner = r)) }, label = { Text(label) })
        }
    }
    // PC 확인 1.36 (각지게가 홈 화면에서는 둥근 그대로): from Android 12 many home screens round every widget to their own corners.
    if (android.os.Build.VERSION.SDK_INT >= 31) Text("안드로이드 12부터는 홈 화면이 위젯 모서리를 직접 둥글게 하기도 해요. 그런 휴대폰에서는 ‘각지게’가 덜 각져 보여요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text("글자 크기", style = MaterialTheme.typography.labelLarge)
    com.bamnat.app.ui.components.FoldRow("studio.widget3") {
        listOf(null to "보통", "s" to "작게", "l" to "크게").forEach { (t, label) ->
            FilterChip(selected = w.text == t, onClick = { onChange(w.copy(text = t)) }, label = { Text(label) })
        }
    }
    Text("위젯 사진은 ‘달력 › 그림’에서 골라요. 위젯에 메모를 보일지는 설정 › 화면과 글자에 있어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    // PC 확인 Z11 1.36 (홈 화면에 위젯을 놓기 어려움): 홈 화면에 놓기 asks the launcher to place one, where it can.
    val context = LocalContext.current
    val wm = remember { android.appwidget.AppWidgetManager.getInstance(context) }
    if (runCatching { wm.isRequestPinAppWidgetSupported }.getOrDefault(false)) {
        var placed by remember { mutableStateOf(false) }
        Text("홈 화면에 놓기", style = MaterialTheme.typography.labelLarge)
        com.bamnat.app.ui.components.FoldRow("studio.widget4") {
            // PC 확인 W3 1.36: each chip says how big the widget is.
            listOf(com.bamnat.app.widget.TodayWidgetReceiver::class.java to "오늘 · 2×1", com.bamnat.app.widget.WeekWidgetReceiver::class.java to "이번 주 · 4×1",
                com.bamnat.app.widget.MonthWidgetReceiver::class.java to "이번 달 · 4×3", com.bamnat.app.widget.CountdownWidgetReceiver::class.java to "다음 근무까지 · 2×1",
                // 1.39 [TASK-07] 할 일 위젯 (처음 하나는 무료).
                com.bamnat.app.widget.TaskWidgetReceiver::class.java to "할 일 · 3×2",
                // 1.39 [TASK-31] 할 일 한 줄 (같은 할 일 위젯, 처음 놓은 하나가 무료).
                com.bamnat.app.widget.TaskLineWidgetReceiver::class.java to "할 일 한 줄 · 2×1").forEach { (cls, label) ->
                androidx.compose.material3.AssistChip(onClick = {
                    if (runCatching { wm.requestPinAppWidget(android.content.ComponentName(context, cls), null, null) }.getOrDefault(false)) placed = true
                }, label = { Text(label) })
            }
        }
        // PC 확인 W2 1.36 (놓은 위젯이 홈 둘째 쪽에 들어가 안 보여요): say where it may have gone.
        if (placed) Text("휴대폰이 묻는 창에서 ‘추가’를 누르면 홈 화면에 놓여요. 첫 화면에 자리가 없으면 다음 쪽에 들어가요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The widget's example: today's day on its plate, light or dark and as see-through as chosen, and [BLU-10] the 위젯 parts as the home
 * screen draws them (판 전체 `widget`, 머리 `widget.head`, 칸 `widget.cell`; a tap on one opens it in 부품).
 * [LAY-12] 1.36 위젯 편집 화면 (누락 점검 N2, 제안 26): the three widgets the home screen has — 오늘 (today and tomorrow), 주 (this week's
 * seven days) and 달 (the month) — each picked above the example, so a part's look is seen where it matters: the 머리 is the date line,
 * '이번 주 근무' and the month's name; a 칸 is a line, a day and a date cell, with its states (오늘·토·일·공휴일·다른 달).
 */
@Composable
internal fun WidgetSample(w: WidgetLook, style: WorkStyle? = null) {
    val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
    val whole = if (bp.touches("widget")) bp.style("widget") else com.bamnat.core.recipe.PartStyle.NONE
    val bg = whole.fill?.let { it or 0xFF000000L } ?: w.bg
    val dark = bg?.let { com.bamnat.core.recipe.Palette.luminance(it) < 0.18 }
        ?: when (w.theme) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    val ground = bg ?: if (dark) 0xFF1E1F24L else 0xFFFFFFFFL
    val alpha = whole.fillA?.takeIf { whole.fill != null }?.let { it / 100f } ?: (w.alpha / 100f)
    val plate = androidx.compose.ui.graphics.Color(ground.toInt()).copy(alpha = alpha)
    // [STU-38] The chosen text colour where it reads on the plate, else black or white, as the widget draws it.
    val ink = androidx.compose.ui.graphics.Color(com.bamnat.core.recipe.Palette.inkOn(whole.ink ?: w.ink ?: if (dark) 0xFFFFFFFFL else 0xFF1B1B1BL, ground).toInt())
    val scale = w.textScale
    val face = com.bamnat.core.recipe.PartStyle(font = whole.font, italic = whole.italic, deco = whole.deco)
    var kind by rememberSaveable { mutableStateOf(WidgetKind.TODAY) }
    val day0 = com.bamnat.core.time.Today.date()
    fun holiday(d: java.time.LocalDate) = com.bamnat.core.holiday.KoreanHolidays.isHoliday(d)
    @Composable
    fun Head(text: String) {
        val head = bp.style("widget.head", setOfNotNull("holiday".takeIf { holiday(day0) }))
        if (head.hide != true) com.bamnat.app.ui.parts.PartText(text, face.over(head),
            MaterialTheme.typography.labelMedium.copy(color = ink.copy(alpha = 0.8f), fontSize = (12 * scale).sp),
            Modifier.then(com.bamnat.app.ui.parts.partSpotModifier("widget.head")).then(com.bamnat.app.ui.parts.PartMods.box(head.copy(ink = null), MaterialTheme.shapes.extraSmall)))
    }
    @Composable
    fun Cell(text: String, d: java.time.LocalDate, inMonth: Boolean, big: Boolean, size: Int, modifier: Modifier, center: Boolean = false) {
        val cell = bp.style("widget.cell", com.bamnat.app.ui.parts.dayStates(null, d.dayOfWeek, holiday(d), inMonth, d == day0, false))
        if (cell.hide == true) { Box(modifier); return }
        val base = (if (big) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium)
        com.bamnat.app.ui.parts.PartText(text, face.over(cell.copy(size = cell.size?.let { if (big) it * 1.3 else it })),
            base.copy(color = if (big || d == day0) ink else ink.copy(alpha = if (inMonth) 0.75f else 0.4f), fontSize = (size * scale).sp,
                textAlign = if (center) androidx.compose.ui.text.style.TextAlign.Center else androidx.compose.ui.text.style.TextAlign.Unspecified),
            modifier.then(com.bamnat.app.ui.parts.partSpotModifier("widget.cell")).then(com.bamnat.app.ui.parts.PartMods.box(cell.copy(ink = null), MaterialTheme.shapes.extraSmall)))
    }
    Column(Modifier.fillMaxWidth()) {
        com.bamnat.app.ui.components.FoldRow("studio.widgetkind", Modifier.padding(horizontal = 12.dp, vertical = 4.dp), what = "위젯 종류") {
            WidgetKind.entries.forEach { k -> FilterChip(selected = k == kind, onClick = { kind = k }, label = { Text(k.label) }) }
        }
        Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF7FA7D9), androidx.compose.ui.graphics.Color(0xFFE7B5C4)))).padding(20.dp),
            contentAlignment = Alignment.Center) {
            // [LAY-07] 위젯 꾸밈 drawn on the plate as the home screen draws it.
            Column(Modifier.fillMaxWidth()
                .then(com.bamnat.app.ui.parts.partSpotModifier("widget")).clip(RoundedCornerShape((whole.r ?: w.corner ?: 20).dp)).background(plate)
                .decor(com.bamnat.core.recipe.Decor.WIDGET)
                .padding(horizontal = (whole.padH ?: 20).dp, vertical = (whole.padV ?: 14).dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                when (kind) {
                    WidgetKind.TODAY -> {
                        // PC 확인 W1 1.36 (예시 근무가 내 근무와 달라요): my own today and tomorrow, the example only before any work is set.
                        // PC 확인 5c21f3bb: the example before any work is set fits the kind of work (투자 had '주간 07:00').
                        val (today, tomorrow) = rememberTwoDays() ?: when (style) {
                            WorkStyle.INVEST -> "오늘 · 장 열림 09:00" to "내일 · 장 열림 09:00"
                            WorkStyle.BUSINESS -> "오늘 · 영업 10:00" to "내일 · 휴무"
                            WorkStyle.FIELD -> "오늘 · 현장 08:00" to "내일 · 일감 없음"
                            WorkStyle.HOURLY -> "오늘 · 알바 18:00" to "내일 · 쉬는 날"
                            WorkStyle.DAY -> "오늘 · 근무 09:00" to "내일 · 근무 09:00"
                            else -> "오늘 · 주간 07:00" to "내일 · 야간 22:00"
                        }
                        Head("${day0.monthValue}월 ${day0.dayOfMonth}일")
                        Cell(today, day0, true, true, 16, Modifier.fillMaxWidth())
                        Cell(tomorrow, day0.plusDays(1), true, false, 14, Modifier.fillMaxWidth())
                    }
                    WidgetKind.WEEK -> {
                        Head(com.bamnat.core.work.TypeWords.say(style, "이번 주 근무"))
                        val mon = day0.minusDays((day0.dayOfWeek.value - 1).toLong())
                        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            (0 until 7).forEach { i ->
                                val d = mon.plusDays(i.toLong())
                                Cell("${"월화수목금토일"[i]}\n${d.dayOfMonth}\n${weekSample(style)[i]}", d, true, false, 11, Modifier.weight(1f), center = true)
                            }
                        }
                    }
                    WidgetKind.MONTH -> {
                        Head("${day0.monthValue}월")
                        val first = day0.withDayOfMonth(1)
                        val start = first.minusDays((first.dayOfWeek.value % 7).toLong())
                        val rows = (first.dayOfWeek.value % 7 + day0.lengthOfMonth() + 6) / 7
                        (0 until rows).forEach { r ->
                            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                (0 until 7).forEach { c ->
                                    val d = start.plusDays((r * 7 + c).toLong())
                                    Cell("${d.dayOfMonth}", d, d.month == day0.month, false, 11, Modifier.weight(1f), center = true)
                                }
                            }
                        }
                    }
                    // 1.39 [TASK-07] 할 일 위젯: 머리와 ＋, 늦은·오늘·끝낸 할 일 한 줄씩, 각 줄 앞 동그라미 (부품 widget.task·check·add).
                    WidgetKind.TASKS -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) { Head("할 일 2개 · 늦은 것 1개") }
                            val plus = bp.style("widget.task.add")
                            if (plus.hide != true) com.bamnat.app.ui.parts.PartText("＋", face.over(plus),
                                MaterialTheme.typography.titleMedium.copy(color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = (16 * scale).sp),
                                Modifier.then(com.bamnat.app.ui.parts.partSpotModifier("widget.task.add")).clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.primaryContainer).then(com.bamnat.app.ui.parts.PartMods.box(plus.copy(ink = null), MaterialTheme.shapes.large))
                                    .padding(horizontal = 12.dp, vertical = 2.dp))
                        }
                        TASK_SAMPLE.forEach { (words, late, done) ->
                            val states = setOfNotNull("late".takeIf { late }, "done".takeIf { done }, "today".takeIf { !late })
                            val line = bp.style("widget.task", states)
                            if (line.hide == true) return@forEach
                            val check = bp.style("widget.task.check", states - "today")
                            Row(Modifier.fillMaxWidth().then(com.bamnat.app.ui.parts.partSpotModifier("widget.task")).then(com.bamnat.app.ui.parts.PartMods.box(line.copy(ink = null), MaterialTheme.shapes.extraSmall)),
                                verticalAlignment = Alignment.CenterVertically) {
                                // The same picture the home widget draws (a launcher draws the "○" letter as a dot), in the part's colour and size.
                                val circle = com.bamnat.app.ui.parts.PartMods.text(face.over(check), MaterialTheme.typography.titleMedium.copy(
                                    color = when { done -> MaterialTheme.colorScheme.primary; late -> MaterialTheme.colorScheme.error
                                        else -> ink.copy(alpha = 0.7f) }, fontSize = (18 * scale).sp))
                                Box(Modifier.padding(horizontal = 8.dp).then(com.bamnat.app.ui.parts.partSpotModifier("widget.task.check"))
                                    .then(com.bamnat.app.ui.parts.PartMods.box(check.copy(ink = null), MaterialTheme.shapes.large))) {
                                    androidx.compose.material3.Icon(
                                        androidx.compose.ui.res.painterResource(if (done) com.bamnat.app.R.drawable.ic_task_done else com.bamnat.app.R.drawable.ic_task_open),
                                        contentDescription = null, tint = circle.color,
                                        modifier = Modifier.size(with(androidx.compose.ui.platform.LocalDensity.current) { circle.fontSize.toDp() }.coerceIn(10.dp, 40.dp)))
                                }
                                Column(Modifier.weight(1f)) {
                                    com.bamnat.app.ui.parts.PartText(words, face.over(line),
                                        MaterialTheme.typography.bodyMedium.copy(color = if (done) ink.copy(alpha = 0.6f) else ink, fontSize = (14 * scale).sp,
                                            textDecoration = if (done) androidx.compose.ui.text.style.TextDecoration.LineThrough else null), Modifier)
                                    if (late) Text("늦음 · 어제", style = MaterialTheme.typography.labelSmall.copy(fontSize = (11 * scale).sp), color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** [LAY-12] The home screen's widgets, as the studio's widget example shows them. */
private enum class WidgetKind(val label: String) { TODAY("오늘 위젯"), WEEK("주 위젯"), MONTH("달 위젯"), TASKS("할 일 위젯") }

/** 1.39 [TASK-07] The 할 일 widget's example rows: the words, and whether each is late, finished or neither. */
private val TASK_SAMPLE = listOf(Triple("서류 내기", true, false), Triple("택배 받기", false, false), Triple("약 먹기", false, true))

/** 2.0.0 [RVS-10] 검토 STU-27: the week widget's example days, in the words of the studio's own kind of work (월~일). */
private fun weekSample(style: WorkStyle?): List<String> = when (style) {
    WorkStyle.DAY -> listOf("출근", "출근", "출근", "출근", "출근", "휴무", "휴무")
    WorkStyle.HOURLY -> listOf("출근", "휴무", "출근", "출근", "휴무", "출근", "출근")
    WorkStyle.FIELD -> listOf("현장", "현장", "빈 날", "현장", "현장", "빈 날", "빈 날")
    WorkStyle.BUSINESS -> listOf("휴무", "영업", "영업", "영업", "영업", "영업", "영업")
    WorkStyle.INVEST -> listOf("장중", "장중", "장중", "장중", "장중", "휴장", "휴장")
    else -> listOf("주간", "주간", "야간", "야간", "휴무", "휴무", "주간")
}

/** "오늘 · 야간 19:00" and "내일 · 휴무" from my own calendar, or null while nothing is set for either day. */
@Composable
private fun rememberTwoDays(): Pair<String, String>? {
    val c = LocalContext.current.appContainer
    val snapFlow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    return remember(snap) {
        val sn = snap ?: return@remember null
        val day = com.bamnat.core.time.Today.date()
        val two = sn.resolver.resolveRange(day, day.plusDays(1))
        if (two.none { it.shiftType != null }) return@remember null
        fun line(label: String, i: Int): String {
            val t = two.getOrNull(i)?.shiftType ?: return "$label · 근무 없음"
            val at = t.start?.takeUnless { t.isOff }?.let { " %02d:%02d".format(it.hour, it.minute) }.orEmpty()
            return "$label · ${t.name.trim()}$at"
        }
        line("오늘", 0) to line("내일", 1)
    }
}

/**
 * [TRY-03] 보관함 in the studio: the parts kept from themes (this area's first), each laid on the draft with a tap. Someone else's part
 * says whose it is; a look with it keeps that theme's days and ad.
 */
@Composable
private fun BoxTool(kept0: List<com.bamnat.app.recipe.ThemeWardrobe.Kept>, area: Area, onLay: (com.bamnat.app.recipe.ThemeWardrobe.Kept) -> Unit,
                    onForget: (com.bamnat.app.recipe.ThemeWardrobe.Kept) -> Unit, laid: Set<Long> = emptySet(),
                    onTakeOff: (com.bamnat.app.recipe.ThemeWardrobe.Kept) -> Unit = {}, onMarket: (() -> Unit)? = null, style: WorkStyle? = null) {
    // 사용자 2026-11-01 ("다른 분이 만든 것을 해당 카테고리에 맞게 찾아서 스튜디오에서 사용"): 장터 opens on this area's 구간 (기능별 탭 › 가계부 …);
    // what is 담아 두기'd there comes back here, in 보관함, to lay on.
    if (onMarket != null) OutlinedButton(onClick = onMarket, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        Text(if (area.marketKind.startsWith(com.bamnat.app.recipe.RecipeClient.SCOPE_TAB) && area.marketKind != com.bamnat.app.recipe.RecipeClient.SCOPE_TAB)
            "장터 ‘탭별 작품 › ${com.bamnat.app.ui.recipe.marketTabLabel(area.marketKind.removePrefix(com.bamnat.app.recipe.RecipeClient.SCOPE_TAB), style)}’에서 찾기"
        else if (area.marketKind == com.bamnat.app.recipe.RecipeClient.SCOPE_THEME) "장터 ‘테마’에서 찾기" else "장터 ‘탭별 작품’에서 찾기")
    }
    // 1.36 U2 (얹으면 순서와 높이가 바뀌어 다음 얹기가 이미 얹은 걸 다시 눌러요): the newest first, always in the same places.
    val kept = remember(kept0) { kept0.sortedByDescending { it.id } }
    if (kept.isEmpty()) {
        Text("아직 담아 둔 조각이 없어요. 장터에서 테마를 입어 볼 때 마음에 드는 부분을 ‘담아 두기’하면 여기서 꺼내 내 모양에 얹어요",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val here = if (area == Area.RULES) kept.filter { k -> LookParts.LOGIC in k.parts }
        else kept.filter { k -> k.parts.isEmpty() || k.parts.any { LookParts.byId(it)?.tab == area.tab } }
    val rest = kept - here.toSet()
    Text("누르면 지금 모양에 얹어요. 얹은 조각은 ‘빼기’로 다시 빼요. 미리 보기를 누르고 있으면 바로 전 모양이에요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    // [STU-27] 1.36 빌드 8 (S32: 메모 조각이 달력 영역에서 안 보이는 줄 앎): every piece shows in every area, this area's first, under a title.
    val list = buildList {
        if (here.isNotEmpty()) { add(null to "이 영역(${area.label}) 조각"); here.forEach { add(it to "") } }
        if (rest.isNotEmpty()) { add(null to "다른 영역 조각 · 얹으면 그 화면이 바뀌어요"); rest.forEach { add(it to "") } }
    }
    list.forEach { (k0, head) ->
        if (k0 == null) { Text(head, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 4.dp)); return@forEach }
        val k = k0
        val on = k.id in laid
        Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.clickable(onClickLabel = if (on) "빼기" else "얹기") { if (on) onTakeOff(k) else onLay(k) }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(k.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (on) "✓ 얹음 · " else "") + partsLine(k.parts) + " · " + if (k.own) "내 것" else "출처 ‘${k.fromTitle}’", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, minLines = 2)
                }
                // 1.36 U1·U2: a part laid on says so and takes off with 빼기, in the same place as 얹기.
                if (on) TextButton(onClick = { onTakeOff(k) }) { Text("빼기") } else TextButton(onClick = { onLay(k) }) { Text("얹기") }
                TextButton(onClick = { onForget(k) }) { Text("버리기") }
            }
        }
    }
}


/**
 * [STU-21] 1.36 기능 (레시피 만들기의 좋은 기능을 스튜디오로): each 기능 this kind of work has, turned on from the phone's own (or
 * example values) and changed in place. 근무 색·이모지와 메모 틀 show on the example at once; all of them go up with 올리기, with
 * amounts left for the one who takes it to fill in.
 */
@Composable
private fun FeaturesTool(vm: StudioViewModel, extras: Map<com.bamnat.core.recipe.PieceKind, Map<String, Any?>>) {
    Text("켜면 지금 내 설정에서 시작해요. 올리기에 함께 나가고, 금액은 받는 분이 직접 넣어요. 내 앱에 입히기는 모양만 바꿔요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    vm.features.forEach { k ->
        val v = extras[k]
        com.bamnat.app.ui.recipe.StudioCard(k.label, if (v == null) "꺼져 있어요" else if (k == com.bamnat.core.recipe.PieceKind.COMPANY) "내 회사 설정이 그대로 나가요" else "켜져 있어요. 아래에서 바로 고쳐요",
            trailing = {
                androidx.compose.material3.Switch(v != null, { on -> if (on) vm.starter(k) else vm.setExtra(k, null, "${k.label} 끔") },
                    modifier = Modifier.semantics { contentDescription = "${k.label} 함께 올리기" })
            }) {
            if (v != null && k != com.bamnat.core.recipe.PieceKind.COMPANY) com.bamnat.app.ui.recipe.FieldsForm(k.fields.filter { com.bamnat.app.ui.recipe.studioField(it) }, v, k) { key, x ->
                vm.setExtraValue(k, key, x)
            }
        }
    }
}

/** [STU-20] 기록 목록: what was changed, newest first; a tap goes back to just before it (다시 하기 brings the rest back). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HistorySheet(s: StudioViewModel.State, onBack: (Int) -> Unit, onDismiss: () -> Unit) {
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("바꾼 기록", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            Text("누르면 그 고침 바로 전으로 돌아가요. 다시 하기로 되살릴 수 있어요", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("● 지금", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            }
            s.past.withIndex().reversed().forEach { (i, step) ->
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(onClickLabel = "이 고침 전으로") { onBack(i); onDismiss() },
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("${i + 1}.", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(36.dp))
                    Text(step.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Text("이 전으로", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
            }
            if (s.past.isEmpty()) Text("아직 바꾼 것이 없어요", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * [STU-22] 바꾼 곳 보기: what differs from where it started, by screen, each with 보기 (that screen's tools) and 처음대로 (that screen
 * only, one step of 되돌리기); the 기능 going with it too.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChangesSheet(vm: StudioViewModel, s: StudioViewModel.State, before: Recipe, now: Recipe, onGo: (String) -> Unit, onDismiss: () -> Unit) {
    val parts = remember(before, now) { LookParts.changed(before, now).mapNotNull { LookParts.byId(it) } }
    com.bamnat.app.ui.components.AppSheet(onDismissRequest = onDismiss, sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("바꾼 곳", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            parts.groupBy { it.tab }.forEach { (tab, list) ->
                // PC 제안 1.36: a locked tab that looks as it started is one line, 🔒 잠가서 그대로.
                if (vm.lockedSame(tab)) {
                    Text("🔒 ${LookParts.tabLabel(tab)} · 잠가서 그대로", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp))
                    return@forEach
                }
                val area = when (tab) {
                    TabOrder.MONEY -> Area.MONEY; TabOrder.MEMOS -> Area.MEMO; TabOrder.STATS -> if (list.any { it.id == "stats.pay" }) Area.PAY else Area.STATS
                    TabOrder.PLAZA -> Area.PLAZA; TabOrder.SETTINGS -> Area.ALL; LookParts.WIDGET -> Area.WIDGET; else -> Area.CALENDAR
                }
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(LookParts.tabLabel(tab), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            TextButton(onClick = { onGo(area.name) }) { Text("보기") }
                            TextButton(onClick = { vm.resetFields("${LookParts.tabLabel(tab)} 처음대로") { d, b -> resetTab(tab, d, b) } }) { Text("처음대로") }
                        }
                        Text(list.joinToString(" · ") { p -> p.label + (partsDetail(p, before, now)?.let { " ($it)" } ?: "") },
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (s.extras.isNotEmpty()) {
                Text("함께 올릴 기능", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 6.dp))
                s.extras.keys.forEach { k ->
                    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(k.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = { onGo(Area.FEATURES.name) }) { Text("보기") }
                        TextButton(onClick = { vm.setExtra(k, null, "${k.label} 끔") }) { Text("빼기") }
                    }
                }
            }
            if (parts.isEmpty() && s.extras.isEmpty()) Text("처음 모양과 같아요", style = MaterialTheme.typography.bodyMedium)
            // [STU-39] 잠그기, where every screen's changes are listed; under the list, so what changed comes first (PC 2차 사용성 제안).
            LockChips(vm)
        }
    }
}

/** [BLU-06] Which 부품 a screen's 부품 part changed and how: "달력 머리 · 둥글기, 근무 표시 · 선" (PC 3차 사용성 제안). */
private fun partsDetail(p: LookParts.Part, before: Recipe, now: Recipe): String? {
    val roots = p.takes.filterIsInstance<LookParts.Take.Parts>().flatMap { it.roots }.toSet().takeIf { it.isNotEmpty() } ?: return null
    fun lines(r: Recipe) = (r.pieces.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.PARTS }?.values?.get("parts") as? List<*>)
        ?.filterIsInstance<Map<String, Any?>>()?.filter { LookParts.under(it["id"] as? String, roots) }.orEmpty()
    fun key(l: Map<String, Any?>) = listOf(l["id"], l["on"], l["when"])
    val was = lines(before).associateBy(::key)
    val is_ = lines(now).associateBy(::key)
    val words = mapOf("fill" to "채우기", "paint" to "채우기", "line" to "선", "lineW" to "선", "lineC" to "선", "r" to "둥글기", "rTL" to "둥글기", "rTR" to "둥글기",
        "rBR" to "둥글기", "rBL" to "둥글기", "shadow" to "그림자", "pad" to "여백", "out" to "여백", "font" to "글꼴", "size" to "글자 크기", "weight" to "굵기",
        "ink" to "글자 색", "alpha" to "보이는 정도", "hide" to "숨기기", "order" to "순서", "dir" to "배치", "gap" to "간격",
        "tshadow" to "글자 그림자", "tshadowC" to "글자 그림자", "tshadowX" to "글자 그림자", "tshadowY" to "글자 그림자", "deco" to "밑줄")
    return (was.keys + is_.keys).distinct().mapNotNull { k ->
        val a = was[k].orEmpty(); val b = is_[k].orEmpty()
        val fields = (a.keys + b.keys).filter { it != "id" && it != "on" && it != "when" && a[it] != b[it] }.map { words[it] ?: "모양" }.distinct()
        if (fields.isEmpty()) null else (com.bamnat.core.recipe.BlueprintParts.LABELS[k[0] as String] ?: k[0]).toString() + " · " + fields.joinToString("·")
    }.distinct().take(4).joinToString(", ").takeIf { it.isNotEmpty() }
}

/** [STU-22] One screen's look back to where it started: its settings and its 화면 색·배경, the rest of the draft as it is. */
private fun resetTab(tab: String, d: com.bamnat.app.data.AppSettings, b: com.bamnat.app.data.AppSettings): com.bamnat.app.data.AppSettings {
    fun screens(keys: (String) -> Boolean): String {
        val now = ScreenLooks.decode(d.screenLooks)
        val was = ScreenLooks.decode(b.screenLooks)
        val keep = now.looks.filterKeys { !keys(it) } + was.looks.filterKeys(keys)
        return ScreenLooks(keep).encode()
    }
    // [BLU-05] That screen's 부품 lines back to where they started, the other screens' parts as they are.
    fun parts(vararg roots: String): String {
        val r = roots.toSet()
        val keep = com.bamnat.core.recipe.Blueprint.decode(d.parts).lines.filterNot { LookParts.under(it["id"] as? String, r) } +
            com.bamnat.core.recipe.Blueprint.decode(b.parts).lines.filter { LookParts.under(it["id"] as? String, r) }
        return if (keep.isEmpty()) "" else com.bamnat.core.recipe.Blueprint(keep).encode()
    }
    return when (tab) {
        TabOrder.CALENDAR -> d.copy(calView = b.calView, calTheme = b.calTheme, calendarLayout = b.calendarLayout, photoLook = b.photoLook,
            screenLooks = screens { it == "calendar" }, parts = parts("cal"))
        TabOrder.MEMOS -> d.copy(memoLook = b.memoLook, memoLayout = b.memoLayout, screenLooks = screens { it == "memo" }, parts = parts("memo"))
        TabOrder.MONEY -> d.copy(moneyLook = b.moneyLook, moneyLayout = b.moneyLayout, screenLooks = screens { it == "money" }, parts = parts("money"))
        TabOrder.STATS -> d.copy(statsLayout = b.statsLayout, payLooks = b.payLooks, screenLooks = screens { it == "stats" || it.startsWith("pay.") }, parts = parts("pay"))
        TabOrder.PLAZA -> d.copy(screenLooks = screens { it == ScreenLooks.PLAZA })
        LookParts.WIDGET -> d.copy(widgetLook = b.widgetLook, parts = parts("widget"))
        else -> d.copy(tabOrder = b.tabOrder, screenLooks = screens { it in setOf(ScreenLooks.ALL, "settings", ScreenLooks.ALARM, ScreenLooks.GUARDED) }, parts = parts("app"))
    }
}

/**
 * 1.36 (사용자 2026-10-31): a finger held still on the example for a moment reports true until it lifts, and that touch then
 * opens nothing (a quick tap still picks the part, a pinch still zooms).
 */
private fun Modifier.holdToPeek(enabled: Boolean, onPeek: (Boolean) -> Unit): Modifier = if (!enabled) this else pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = androidx.compose.ui.input.pointer.PointerEventPass.Initial)
        var moved = false
        val lifted = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                if (e.changes.size > 1 || e.changes.any { (it.position - down.position).getDistance() > viewConfiguration.touchSlop }) { moved = true; return@withTimeoutOrNull true }
                if (e.changes.all { !it.pressed }) return@withTimeoutOrNull true
            }
            @Suppress("UNREACHABLE_CODE") true
        } != null
        if (lifted || moved) return@awaitEachGesture
        onPeek(true)
        try {
            while (true) {
                val e = awaitPointerEvent(androidx.compose.ui.input.pointer.PointerEventPass.Initial)
                e.changes.forEach { it.consume() }
                if (e.changes.all { !it.pressed }) break
            }
        } finally { onPeek(false) }
    }
}

/** [STU-34] A kept draft's name, from its body. */
internal fun draftName(body: String): String =
    runCatching { org.json.JSONObject(body).optString("title") }.getOrNull()?.ifBlank { null } ?: "꾸미던 모양"

/** [STU-34] How long ago, in words: 방금, N분 전, N시간 전, N일 전. */
internal fun ago(millis: Long, now: Long = com.bamnat.core.time.Today.millis()): String {
    val m = ((now - millis) / 60_000L).coerceAtLeast(0)
    return when { m < 1 -> "방금"; m < 60 -> "${m}분 전"; m < 60 * 24 -> "${m / 60}시간 전"; else -> "${m / (60 * 24)}일 전" }
}

/** [STU-41] 1.36 장터 표지 자동: the studio's example as a picture when 올리기 opened, offered as the 표지 there. */
object StudioShot {
    @Volatile var last: android.graphics.Bitmap? = null
}

/**
 * 2.0.0 [RVP-66] (검토 REST-07·T14-09) The roles of the main calendar's kinds of day, by the same guess the 근무 꾸미기 piece matches
 * with, so 칸 디자인 › 패턴 › 근무별 offers only the lines this calendar has; null until the calendar is read.
 */
@Composable
private fun calendarRoles(): Set<String>? {
    val c = LocalContext.current.appContainer
    val snapFlow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    return remember(snap) {
        snap?.shiftTypes?.map { t ->
            com.bamnat.core.recipe.ShiftRole.guess(t.name, t.isOff, t.start?.hour, if (t.isOff) 0 else t.durationMinutes).key
        }?.toSet()
    }
}
