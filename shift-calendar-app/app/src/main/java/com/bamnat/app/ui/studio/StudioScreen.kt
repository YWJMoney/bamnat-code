package com.bamnat.app.ui.studio

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import kotlinx.coroutines.launch

/** [STU-11] The parts of the app the studio changes, each with the example tab it shows and the tools it has. */
private enum class Area(val label: String, val target: FreeTarget?, val tab: String) {
    CALENDAR("달력", FreeTarget.CALENDAR, TabOrder.CALENDAR),
    /**
     * [STU-21] 1.36 레시피 만들기 합침: 근무 색·알람 묶음·메모 틀·규칙 — what goes with the look when it is sent. [STU-25] 빌드 7: second
     * in the row (it was eleventh, three swipes away).
     */
    FEATURES("기능", null, TabOrder.CALENDAR),
    MONEY("가계부", FreeTarget.MONEY, TabOrder.MONEY),
    MEMO("메모", FreeTarget.MEMO, TabOrder.MEMOS),
    STATS("통계", FreeTarget.STATS, TabOrder.STATS),
    PAY("급여", FreeTarget.PAY, TabOrder.STATS),
    PLAZA("마을", FreeTarget.PLAZA, TabOrder.PLAZA),
    SETTINGS("설정", FreeTarget.SETTINGS, TabOrder.SETTINGS),
    WIDGET("위젯", null, TabOrder.SETTINGS),
    ALARM("알람 화면", FreeTarget.ALARM, TabOrder.SETTINGS),
    GUARDED("결제·처리방침", FreeTarget.GUARDED, TabOrder.SETTINGS),
    ALL("앱 전체", FreeTarget.ALL, TabOrder.CALENDAR),
    ;

    /** F-97: the area chips say what the bottom tabs say for this kind of work (근무·급여, 장부 …), not a name of their own. */
    fun labelFor(style: WorkStyle?): String = when (this) {
        STATS -> com.bamnat.app.ui.TopLevel.STATS.labelFor(style)
        MONEY -> com.bamnat.app.ui.TopLevel.MONEY.labelFor(style)
        else -> label
    }

    val tools: List<Tool> get() = when (this) {
        CALENDAR -> listOf(Tool.CELL, Tool.BOX, Tool.PATTERN, Tool.VIEW, Tool.DAYS, Tool.PHOTO, Tool.COLORS, Tool.CARDS)
        MEMO -> listOf(Tool.MEMO_LOOK, Tool.BOX, Tool.COLORS, Tool.CARDS)
        MONEY -> listOf(Tool.MONEY_LOOK, Tool.BOX, Tool.COLORS, Tool.CARDS)
        STATS -> listOf(Tool.COLORS, Tool.BOX, Tool.CARDS)
        PAY -> listOf(Tool.PAY_LOOK, Tool.COLORS, Tool.BOX)
        PLAZA -> listOf(Tool.COLORS, Tool.BOX)
        SETTINGS -> listOf(Tool.COLORS, Tool.BOX, Tool.TABS, Tool.CORNERS)
        WIDGET -> listOf(Tool.WIDGET, Tool.BOX)
        ALARM, GUARDED -> listOf(Tool.COLORS)
        ALL -> listOf(Tool.COLORS, Tool.BOX, Tool.CORNERS, Tool.TABS)
        FEATURES -> listOf(Tool.FEATURE)
    }

    /** Screens drawn only in 자세히 (the example app has no page of their own). */
    val detailOnly: Boolean get() = this in setOf(PAY, ALARM, GUARDED, WIDGET)

    companion object {
        fun of(tab: String): Area = when (tab) {
            TabOrder.MONEY -> MONEY; TabOrder.MEMOS -> MEMO; TabOrder.STATS -> STATS; TabOrder.PLAZA -> PLAZA; TabOrder.SETTINGS -> SETTINGS
            else -> CALENDAR
        }
    }
}

private enum class Tool(val label: String) {
    CELL("칸 디자인"), PATTERN("패턴"), VIEW("칸 높이·줄"), DAYS("오늘·주말 색"), PHOTO("그림"), COLORS("색·배경"), CARDS("카드 배치"),
    MEMO_LOOK("메모장 모양"), MONEY_LOOK("가계부 모양"), PAY_LOOK("급여 모양"), TABS("아래 탭"), CORNERS("모서리"), WIDGET("위젯 모양"),
    BOX("보관함"), FEATURE("켜고 고치기"),
}

/** [STU-24] 1.36 미리 보기 크기: the handle under it sets any size between these (사용자 2026-10-31 "크게작게 접기가 너무 불편"). */
private const val SCALE_MIN = 0.3f
private const val SCALE_MAX = 0.8f
private const val SCALE_BIG = 0.62f
private const val SCALE_SMALL = 0.38f

/**
 * [STU-10][STU-11] 1.35 스튜디오 (사용자 2026-10-30 21:39 "인터랙션이 뛰어나야하고 좀더 밀도있게"): one screen for every look. On top the
 * example app (앱 화면) or the screen in detail (자세히) — 크게, 작게 or 접기 — that follows every change; under it the area (달력, 가계부 …
 * 앱 전체) and that area's tools as short tabs, one open at a time, so nothing is a long scroll. 되돌리기·다시 하기 over every change, and
 * 비교 held down shows the look it started from. The phone stays as it is until 내 앱에 입히기; 저장 keeps it as 내 테마, 올리기 sends it to 장터.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudioScreen(vm: StudioViewModel, onBack: () -> Unit, onOpen: (String) -> Unit = {}) {
    com.bamnat.app.ui.theme.BarIcons(light = MaterialTheme.colorScheme.surface.luminance() > 0.5f)
    val c = LocalContext.current.appContainer
    val s by vm.state.collectAsStateWithLifecycle()
    val kept by c.wardrobe.kept.collectAsStateWithLifecycle()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var area by rememberSaveable { mutableStateOf(Area.CALENDAR) }
    var tool by rememberSaveable { mutableStateOf(Tool.CELL) }
    var scale by rememberSaveable { mutableStateOf(SCALE_BIG) }
    var history by remember { mutableStateOf(false) }
    var changes by remember { mutableStateOf(false) }
    var publishing by remember { mutableStateOf<Triple<String, String, String?>?>(null) }
    var uploadWhat by remember { mutableStateOf<String?>(null) }
    var openedLine by remember { mutableStateOf<String?>(null) }
    var detail by rememberSaveable { mutableStateOf(false) }
    var comparing by remember { mutableStateOf(false) }
    var half by rememberSaveable { mutableStateOf(false) }
    var payStyle by rememberSaveable { mutableStateOf(vm.style ?: WorkStyle.SHIFT) }
    var lookIndex by rememberSaveable { mutableIntStateOf(0) }
    var leaving by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var startAsk by remember { mutableStateOf<(() -> Unit)?>(null) }
    var inkAsk by remember { mutableStateOf<(() -> Unit)?>(null) }
    val snack = remember { SnackbarHostState() }

    val d = s.draft
    val looks = remember(d.screenLooks) { ScreenLooks.decode(d.screenLooks) }
    val view = remember(d.calView) { CalendarView.decode(d.calView) }
    val memoLook = remember(d.memoLook) { MemoLook.decode(d.memoLook) }
    val moneyLook = remember(d.moneyLook) { MoneyLook.decode(d.moneyLook) }
    val payLooks = remember(d.payLooks) { PayLook.decodeAll(d.payLooks) }
    // [STU-21] The example draws the 기능 that show (근무 색, 메모 틀, 투자 일지) along with the look.
    val now = remember(s.pieces, s.extras) { vm.shown(s) }
    val before = remember(s.basePieces) { Recipe("처음", styles = WorkStyle.entries.toSet(), pieces = s.basePieces) }
    val diff = remember(s.draft, s.base) { c.recipes.pieces.lookDiff(s.draft, s.base) }
    fun pick(a: Area) { area = a; tool = a.tools.first(); lookIndex = 0; if (a.detailOnly) detail = true }
    // [STU-20] The 기록 목록 says where each change was made.
    vm.where = "${area.labelFor(vm.style)} · ${(if (tool in area.tools) tool else area.tools.first()).label}"
    // [STU-23] 눌러서 고치기: a tap on a part of the example opens its tool, and says so.
    fun picked(region: String) {
        val (a, t) = when (region) {
            "cells" -> Area.CALENDAR to Tool.CELL
            "days" -> Area.FEATURES to Tool.FEATURE
            "cards" -> (if (area.tools.contains(Tool.CARDS)) area else Area.of(area.tab)) to Tool.CARDS
            "memo" -> Area.MEMO to Tool.MEMO_LOOK
            "money" -> Area.MONEY to Tool.MONEY_LOOK
            else -> area to Tool.COLORS
        }
        area = a; tool = if (t in a.tools) t else a.tools.first()
        if (scale > SCALE_SMALL) scale = SCALE_SMALL + 0.06f
        openedLine = "‘${a.labelFor(vm.style)} · ${tool.label}’을 열었어요. 바꾸면 위에 바로 보여요"
    }
    // Ask first when the draft has things the phone does not.
    fun guarded(then: () -> Unit) { if (s.changed) startAsk = then else then() }
    // [STU-12] 조합 자동 점검 before it goes anywhere.
    fun checked(then: () -> Unit) { if (vm.hardToRead().isNotEmpty()) inkAsk = then else then() }

    LaunchedEffect(s.said) {
        val said = s.said ?: return@LaunchedEffect
        val r = snack.showSnackbar(said, actionLabel = if (s.canUndoWear) "되돌리기" else null, duration = SnackbarDuration.Long)
        if (r == SnackbarResult.ActionPerformed) vm.undoWear() else vm.said()
    }
    androidx.activity.compose.BackHandler(enabled = s.changed) { leaving = true }

    CompositionLocalProvider(LocalColorMemory provides ColorMemory(s.recent, vm::usedColor)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snack) },
            topBar = {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = { if (s.changed) leaving = true else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } },
                    title = {
                        var menu by remember { mutableStateOf(false) }
                        Column(Modifier.clickable(onClickLabel = "시작할 모양 바꾸기") { menu = true }) {
                            Text("스튜디오", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(s.origin.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false))
                                Icon(Icons.Filled.ArrowDropDown, null)
                            }
                        }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("지금 내 모양에서") }, onClick = { menu = false; guarded { vm.startMine() } })
                            DropdownMenuItem({ Text("처음 모양에서") }, onClick = { menu = false; guarded { vm.startPlain() } })
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
                Surface(tonalElevation = 3.dp) {
                    Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth().clickable(enabled = diff > 0 || s.extras.isNotEmpty(), onClickLabel = "바꾼 곳 보기") { changes = true },
                            verticalAlignment = Alignment.CenterVertically) {
                            Text(when {
                                s.wornNow && diff == 0 -> "✓ 지금 내 앱에 입힌 모양이에요. 더 고치면 다시 입혀요"
                                diff == 0 -> "바꾼 곳 없음 · 입히기 전까지 내 앱은 그대로예요"
                                else -> "바꾼 곳 ${diff}개 · 입히기 전까지 내 앱은 그대로예요"
                            } + (if (s.extras.isNotEmpty()) " · 기능 ${s.extras.size}개" else "") + (s.sources.values.distinct().takeIf { it.isNotEmpty() }?.let { v -> " · " + v.joinToString("·") { "‘$it’" } + " 조각 사용" } ?: ""),
                                style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            if (diff > 0 || s.extras.isNotEmpty()) Text("보기 ›", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            OutlinedButton(onClick = { checked { saving = true } }, modifier = Modifier.heightIn(min = 48.dp)) { Text("저장") }
                            // [STU-21] 1.36 올리기 stays in the studio: 받는 분이 넣을 값 · 이름 · 설명 · 마치기 over it, back here with 스튜디오로.
                            OutlinedButton(onClick = { checked {
                                // [STU-26] Changes on one tab only: ask whether that tab goes up alone (a 기능별 탭) or the whole theme.
                                val tab = vm.changedTab()
                                if (tab != null) uploadWhat = tab
                                else { val (body, parent) = vm.upload(); publishing = Triple(body, parent, vm.nextOf()) }
                            } },
                                modifier = Modifier.heightIn(min = 48.dp)) { Text("올리기") }
                            Box(Modifier.weight(1f)) {
                                WearButton(vm, s.busy) { go -> checked(go) }
                            }
                        }
                    }
                }
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                // The example: 앱 화면 or 자세히, its size, and 반반 비교.
                Column(Modifier.fillMaxWidth().animateContentSize().padding(horizontal = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(selected = !detail, onClick = { detail = false }, label = { Text("앱 화면") }, enabled = !area.detailOnly)
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = detail, onClick = { detail = true }, label = { Text("자세히") })
                        Spacer(Modifier.weight(1f))
                    }
                    // [STU-24] Two fingers on the example make it bigger or smaller too.
                    val zoom = androidx.compose.foundation.gestures.rememberTransformableState { z, _, _ ->
                        scale = (scale * z).coerceIn(SCALE_MIN, SCALE_MAX)
                    }
                    if (scale > 0f) Box(Modifier.fillMaxWidth().transformable(zoom, canPan = { false }), contentAlignment = Alignment.Center) {
                        CompositionLocalProvider(com.bamnat.app.ui.recipe.LocalMockPick provides ::picked) {
                        if (!detail && !area.detailOnly) {
                            // [TRY-04] 누르고 있으면 전 모양 too: in the draft's places, so only the looks change under the finger.
                            val shown = if (comparing) remember(before, now) { LookParts.frame(before, now) } else now
                            if (half && !comparing) CompareCanvas(before, now, vm.style, area.tab, { pick(Area.of(it)) }, scale, 0.5f) { picked("colors") }
                            else ScaledPhone(shown, vm.style, area.tab, { pick(Area.of(it)) }, scale, onPick = { picked("colors") })
                        } else {
                            val sl = if (comparing) remember(s.base.screenLooks) { ScreenLooks.decode(s.base.screenLooks) } else looks
                            Box(Modifier.fillMaxWidth().heightIn(max = (scale * 580).dp).clip(RoundedCornerShape(12.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp)).verticalScroll(rememberScrollState())) {
                                CompositionLocalProvider(
                                    LocalScreenLooks provides sl,
                                    com.bamnat.app.ui.calendar.LocalCalendarView provides view,
                                    LocalMemoLook provides memoLook,
                                    LocalMoneyLook provides moneyLook,
                                    com.bamnat.app.ui.theme.LocalPayLooks provides payLooks,
                                ) {
                                    val t = area.target ?: FreeTarget.ALL
                                    ScreenSkin(t.keyFor(payStyle), Modifier.fillMaxWidth()) {
                                        when (area) {
                                            Area.CALENDAR -> {
                                                // [STU-25] 1.36 빌드 7: the phone's own weeks, as the example phone above shows them.
                                                val my = com.bamnat.app.ui.recipe.rememberMyWeeks(emptyList(), weeks = 3)
                                                CellWeeks(view, rows = if (scale >= 0.5f) 3 else 1, onCell = { i -> lookIndex = i; tool = Tool.CELL },
                                                    start = my?.first, todayDate = my?.today, days = my?.days)
                                            }
                                            Area.MEMO -> com.bamnat.app.ui.memo.MemoLookSample(memoLook) { tool = Tool.MEMO_LOOK }
                                            Area.MONEY -> MoneySample(moneyLook) { tool = Tool.MONEY_LOOK }
                                            Area.PAY -> PaySample(payLooks[payStyle] ?: PayLook(), payStyle) { tool = Tool.PAY_LOOK }
                                            Area.ALARM -> AlarmSample { tool = Tool.COLORS }
                                            Area.WIDGET -> WidgetSample(WidgetLook.decode(d.widgetLook))
                                            else -> ScreenSample(t) { tool = Tool.COLORS }
                                        }
                                    }
                                }
                            }
                        }
                        }
                    }
                    PreviewHandle(scale) { scale = it }
                    openedLine?.let { line ->
                        LaunchedEffect(line) { kotlinx.coroutines.delay(3500); openedLine = null }
                        Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
                    }
                    if (comparing) Text("고치기 전 모양이에요", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.align(Alignment.CenterHorizontally))
                    else if (half && !detail) Text("왼쪽이 지금 고친 모양, 오른쪽이 고치기 전이에요", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
                // Where, then which tool.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Area.entries.forEach { a -> FilterChip(selected = a == area, onClick = { pick(a) }, label = { Text(a.labelFor(vm.style)) }) }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    area.tools.forEach { t ->
                        TextButton(onClick = { tool = t }, modifier = Modifier.heightIn(min = 44.dp)) {
                            Text(t.label + if (t == Tool.BOX) " ${kept.size}" else "", fontWeight = if (t == tool) FontWeight.Bold else FontWeight.Normal,
                                color = if (t == tool) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                HorizontalDivider()
                Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    area.target?.takeIf { it.restricted }?.let { Text(it.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    val t = if (tool in area.tools) tool else area.tools.first()
                    val key = (area.target ?: FreeTarget.ALL).keyFor(payStyle)
                    fun saveView(v: CalendarView) = vm.change { with(CalendarViewSettings) { it.withView(v) } }
                    when (t) {
                        Tool.CELL -> CellDesigner(view, lookIndex, { lookIndex = it }, ::saveView)
                        Tool.PATTERN -> PatternEditor(view, ::saveView)
                        Tool.VIEW -> com.bamnat.app.ui.calendar.CalendarViewEditor(view, ::saveView, sample = {})
                        Tool.DAYS -> com.bamnat.app.ui.settings.CalendarThemeRows(d.calTheme) { v -> vm.change { it.copy(calTheme = v) } }
                        Tool.PHOTO -> com.bamnat.app.ui.settings.PhotoLookRows(d.photoLook) { v -> vm.change { it.copy(photoLook = v) } }
                        Tool.COLORS -> ScreenLookEditor(area.target ?: FreeTarget.ALL, looks.looks[key] ?: ScreenLook(), looks.of(key)) { l ->
                            vm.change { it.copy(screenLooks = ScreenLooks.decode(it.screenLooks).with(key, l).encode()) }
                        }
                        Tool.CARDS -> CardsTool(area, vm.style, d) { f -> vm.change(f) }
                        Tool.MEMO_LOOK -> MemoLookEditor(memoLook) { l -> vm.change { it.copy(memoLook = l.encode()) } }
                        Tool.MONEY_LOOK -> MoneyLookEditor(moneyLook) { l -> vm.change { it.copy(moneyLook = l.encode()) } }
                        Tool.PAY_LOOK -> {
                            Text("근무 유형마다 따로 꾸며요", style = MaterialTheme.typography.labelLarge)
                            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                        Tool.BOX -> BoxTool(kept, area, onLay = vm::layer, onForget = { c.wardrobe.forgetKept(it.id) }, laid = s.layered.map { it.id }.toSet())
                        Tool.FEATURE -> FeaturesTool(vm, s.extras)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
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
                TextButton(onClick = { leaving = false; onBack() }, modifier = Modifier.heightIn(min = 48.dp)) { Text("버리고 나가기") }
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
    if (history) HistorySheet(s, onBack = { i -> vm.backTo(i) }, onDismiss = { history = false })
    if (changes) ChangesSheet(vm, s, before, now, onGo = { a -> pick(Area.valueOf(a)); changes = false }, onDismiss = { changes = false })
    uploadWhat?.let { tab ->
        var alone by remember(tab) { mutableStateOf(true) }
        val name = com.bamnat.app.ui.recipe.tabName(tab)
        AlertDialog(
            onDismissRequest = { uploadWhat = null },
            title = { Text("무엇을 올릴까요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(true to "바꾼 탭만 ($name)", false to "테마 전체").forEach { (v, label) ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { alone = v }, verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = alone == v, onClick = { alone = v })
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyLarge)
                                Text(if (v) "장터 ‘기능별 탭’에 올라가요. 받는 분은 지금 테마 위에 이 탭만 얹어요" +
                                    (if (s.extras.isNotEmpty()) ". 기능 ${s.extras.size}개는 빠져요" else "")
                                    else "장터 ‘테마’에 올라가요. 모든 화면의 모양" + (if (s.extras.isNotEmpty()) "과 기능 ${s.extras.size}개가" else "이") + " 함께 가요",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = {
                uploadWhat = null
                val (body, parent) = vm.upload(if (alone) tab else null)
                publishing = Triple(body, parent, if (alone) null else vm.nextOf())
            }) { Text("다음") } },
            dismissButton = { TextButton(onClick = { uploadWhat = null }) { Text("닫기") } },
        )
    }
    publishing?.let { (body, parent, own) ->
        // [STU-21] 올리기 over the studio (레시피 만들기 steps 2–5): 스튜디오로 or closing comes back with the draft as it was.
        androidx.compose.ui.window.Dialog(onDismissRequest = { publishing = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
            Surface(Modifier.fillMaxSize()) {
                val up = com.bamnat.app.ui.appViewModel(key = "studio-up-${body.hashCode()}-$own") {
                    com.bamnat.app.ui.recipe.RecipeMakeViewModel(it, com.bamnat.app.data.PersonalCalendar.MAIN, vm.state.value.draftId, body, parent.takeIf { own == null }, null, own)
                }
                com.bamnat.app.ui.recipe.RecipeMakeScreen(up, onBack = { publishing = null }, onOpen = { code -> publishing = null; onOpen(code) }, finishOnly = true)
            }
        }
    }
    if (saving) SaveDialog(vm, s, onDismiss = { saving = false }) { name, toPlaza ->
        saving = false
        scope.launch { vm.save(name, toPlaza).join(); if (leaving) onBack() }
    }
}

/** 내 앱에 입히기: an ad first when the look keeps someone else's days ([StudioViewModel.needsAd]). */
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
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf(100 to "불투명", 85 to "조금 비침", 65 to "반쯤 비침", 40 to "많이 비침").forEach { (a, label) ->
            FilterChip(selected = w.alpha == a, onClick = { onChange(w.copy(alpha = a)) }, label = { Text(label) })
        }
    }
    Text("위젯 사진은 ‘달력 › 그림’에서 골라요. 위젯에 메모를 보일지는 설정 › 화면과 글자에 있어요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The widget's example: today's day on its plate, light or dark and as see-through as chosen. */
@Composable
private fun WidgetSample(w: WidgetLook) {
    val dark = when (w.theme) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
    val plate = (if (dark) androidx.compose.ui.graphics.Color(0xFF1E1F24) else androidx.compose.ui.graphics.Color.White).copy(alpha = w.alpha / 100f)
    val ink = if (dark) androidx.compose.ui.graphics.Color.White else androidx.compose.ui.graphics.Color(0xFF1B1B1B)
    Box(Modifier.fillMaxWidth().background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(androidx.compose.ui.graphics.Color(0xFF7FA7D9), androidx.compose.ui.graphics.Color(0xFFE7B5C4)))).padding(20.dp),
        contentAlignment = Alignment.Center) {
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(plate).padding(horizontal = 20.dp, vertical = 14.dp)) {
            Text("오늘 · 주간 07:00", color = ink, style = MaterialTheme.typography.titleMedium)
            Text("내일 · 야간 22:00", color = ink.copy(alpha = 0.75f), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/**
 * [TRY-03] 보관함 in the studio: the parts kept from themes (this area's first), each laid on the draft with a tap. Someone else's part
 * says whose it is; a look with it keeps that theme's days and ad.
 */
@Composable
private fun BoxTool(kept: List<com.bamnat.app.recipe.ThemeWardrobe.Kept>, area: Area, onLay: (com.bamnat.app.recipe.ThemeWardrobe.Kept) -> Unit,
                    onForget: (com.bamnat.app.recipe.ThemeWardrobe.Kept) -> Unit, laid: Set<Long> = emptySet()) {
    if (kept.isEmpty()) {
        Text("아직 담아 둔 조각이 없어요. 장터에서 테마를 입어 볼 때 마음에 드는 부분을 ‘담아 두기’하면 여기서 꺼내 내 모양에 얹어요",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val here = kept.filter { k -> k.parts.isEmpty() || k.parts.any { LookParts.byId(it)?.tab == area.tab } }
    val rest = kept - here.toSet()
    Text("누르면 지금 모양에 얹어요. 얹은 뒤에도 되돌리기로 뺄 수 있어요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
            Row(Modifier.clickable(onClickLabel = "얹기") { onLay(k) }.padding(start = 14.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(k.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text((if (on) "✓ 얹음 · " else "") + partsLine(k.parts) + " · " + if (k.own) "내 것" else "출처 ‘${k.fromTitle}’", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                }
                TextButton(onClick = { onLay(k) }) { Text("얹기") }
                TextButton(onClick = { onForget(k) }) { Text("버리기") }
            }
        }
    }
}


/**
 * [STU-24] 1.36 미리 보기 손잡이 (사용자 2026-10-31 "크게작게 접기가 너무 불편"): one bar under the example — drag down to make it
 * bigger, up to make it smaller (past the smallest it folds away); two taps go between big and small; folded, a tap brings it back.
 */
@Composable
private fun PreviewHandle(scale: Float, onScale: (Float) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    var drag by remember { mutableStateOf(scale) }
    Box(Modifier.fillMaxWidth().height(28.dp)
        .pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { onScale(if (scale >= (SCALE_BIG + SCALE_SMALL) / 2) SCALE_SMALL else SCALE_BIG) },
                onTap = { if (scale == 0f) onScale(SCALE_SMALL) },
            )
        }
        .draggable(androidx.compose.foundation.gestures.rememberDraggableState { dy ->
            drag = (drag + with(density) { dy.toDp().value } / 700f).coerceIn(0.15f, SCALE_MAX)
            onScale(if (drag < SCALE_MIN) 0f else drag)
        }, androidx.compose.foundation.gestures.Orientation.Vertical, onDragStarted = { drag = if (scale == 0f) 0.2f else scale })
        .semantics {
            contentDescription = if (scale == 0f) "접힌 예시. 누르면 펼쳐요" else "예시 크기 손잡이. 아래로 끌면 커지고 위로 밀면 작아져요"
            customActions = listOf(
                androidx.compose.ui.semantics.CustomAccessibilityAction("크게") { onScale(SCALE_BIG); true },
                androidx.compose.ui.semantics.CustomAccessibilityAction("작게") { onScale(SCALE_SMALL); true },
                androidx.compose.ui.semantics.CustomAccessibilityAction("접기") { onScale(0f); true },
            )
        },
        contentAlignment = Alignment.Center) {
        if (scale == 0f) Text("예시 펼치기 ⌄", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        else Box(Modifier.size(width = 44.dp, height = 5.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.outline))
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
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
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
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("바꾼 곳", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            parts.groupBy { it.tab }.forEach { (tab, list) ->
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
                        Text(list.joinToString(" · ") { it.label }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        }
    }
}

/** [STU-22] One screen's look back to where it started: its settings and its 화면 색·배경, the rest of the draft as it is. */
private fun resetTab(tab: String, d: com.bamnat.app.data.AppSettings, b: com.bamnat.app.data.AppSettings): com.bamnat.app.data.AppSettings {
    fun screens(keys: (String) -> Boolean): String {
        val now = ScreenLooks.decode(d.screenLooks)
        val was = ScreenLooks.decode(b.screenLooks)
        val keep = now.looks.filterKeys { !keys(it) } + was.looks.filterKeys(keys)
        return ScreenLooks(keep).encode()
    }
    return when (tab) {
        TabOrder.CALENDAR -> d.copy(calView = b.calView, calTheme = b.calTheme, calendarLayout = b.calendarLayout, photoLook = b.photoLook,
            screenLooks = screens { it == "calendar" })
        TabOrder.MEMOS -> d.copy(memoLook = b.memoLook, memoLayout = b.memoLayout, screenLooks = screens { it == "memo" })
        TabOrder.MONEY -> d.copy(moneyLook = b.moneyLook, moneyLayout = b.moneyLayout, screenLooks = screens { it == "money" })
        TabOrder.STATS -> d.copy(statsLayout = b.statsLayout, payLooks = b.payLooks, screenLooks = screens { it == "stats" || it.startsWith("pay.") })
        TabOrder.PLAZA -> d.copy(screenLooks = screens { it == ScreenLooks.PLAZA })
        LookParts.WIDGET -> d.copy(widgetLook = b.widgetLook)
        else -> d.copy(tabOrder = b.tabOrder, screenLooks = screens { it in setOf(ScreenLooks.ALL, "settings", ScreenLooks.ALARM, ScreenLooks.GUARDED) })
    }
}
