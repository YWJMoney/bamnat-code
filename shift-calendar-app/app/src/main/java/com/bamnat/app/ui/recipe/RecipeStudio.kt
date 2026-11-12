package com.bamnat.app.ui.recipe

import com.bamnat.app.ui.decor.nextPinned
import com.bamnat.app.ui.decor.decor
import com.bamnat.app.ui.parts.partSpot
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.launch
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bamnat.app.ui.TopLevel
import com.bamnat.app.ui.calendar.CalendarPart
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.app.ui.theme.fillWith
import com.bamnat.app.ui.memo.MemoPart
import com.bamnat.app.ui.money.MoneyPart
import com.bamnat.app.ui.stats.StatsPart
import com.bamnat.app.ui.theme.shapesFor
import com.bamnat.core.layout.PageLayout
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.Field
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.Ref
import com.bamnat.core.recipe.ShiftRole
import com.bamnat.core.recipe.WEEK_DAYS
import com.bamnat.core.work.WorkStyle

/**
 * 1.35 꾸미기 스튜디오 (사용자 2026-10-30 "직접 실제 어플 모양을 보면서 커스텀 하고 삭제하고 추가하고"): the recipe is made on an
 * example app, never on mine. A phone in the middle shows each bottom tab with example days and numbers in the recipe's look; under
 * it, what that tab's screen can change — moved, hidden, added back, coloured — and the phone follows at once. Pieces without a
 * picture (알람, 메모 틀, 급여·현장·가게 규칙, 투자 원칙) are edited there as forms built from the piece catalog.
 */
@Composable
internal fun RecipeStudio(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel, tab: String, onTab: (String) -> Unit,
                          /** Whether the big example is out of sight (scrolled past), for the small one that stays in view. */
                          onMockShown: (Boolean) -> Unit = {},
                          /** Scrolls the screen so the fields start at its top (F-46: tapping the example only nudged it). */
                          onEdit: ((Float) -> Unit)? = null) {
    val r = s.recipe
    val editors = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var editorsTop by remember { mutableStateOf(0f) }
    StudioStart(s, vm)
    Text("예시 앱에서 바로 꾸며요. 화면을 누르면 고치는 칸으로 가요. 내 앱은 바뀌지 않아요", style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    Box(Modifier.onGloballyPositioned { c ->
        // Out of sight once less than a fifth of it is on the screen.
        val b = c.boundsInWindow()
        onMockShown(b.height > c.size.height / 5f)
    }) {
        MockPhone(r, vm.style, tab, onTab, onPick = { onEdit?.invoke(editorsTop) ?: scope.launch { editors.bringIntoView() } })
    }
    Text(TopLevel.entries.firstOrNull { it.route == tab }?.labelFor(vm.style).orEmpty() + " 화면에서 고칠 것", style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { heading() }.bringIntoViewRequester(editors).onGloballyPositioned { editorsTop = it.boundsInWindow().top })
    StudioEditors(r, vm, tab)
}

/**
 * [STU-03] 1.35 자유도 점검 ‘보면서 꾸미기’: the example, small, kept at the top corner while the fields under it are changed, so each
 * change shows at once; a tap goes back up to the big one.
 */
@Composable
internal fun MiniPhone(r: Recipe, style: WorkStyle?, tab: String, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val scale = 0.42f
    Box(modifier.size((340 * scale).dp, (560 * scale).dp).clip(MaterialTheme.shapes.medium)
        .clickable(onClickLabel = "예시 앱 크게 보기", onClick = onOpen).semantics(mergeDescendants = true) { contentDescription = "작은 예시 앱. 누르면 크게 봐요" },
        contentAlignment = Alignment.Center) {
        Box(Modifier.requiredSize(340.dp, 560.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            MockPhone(r, style, tab, onTab = {}, onPick = onOpen)
        }
    }
}

/** Where a recipe starts: what I have now, one of the 보여주기 판, or nothing. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StudioStart(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    var open by rememberSaveable { mutableStateOf(s.recipe.pieces.isEmpty()) }
    OutlinedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("어디서 시작할까요?", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
                TextButton(onClick = { open = !open }) { Text(if (open) "접기" else "다시 고르기") }
            }
            if (!open) {
                // Where it started, so the 수정판 line is not a surprise at 마치기.
                Text(s.parentTitle?.let { "‘$it’에서 시작했어요. 올리면 그 판의 수정판으로 붙어요" }
                    ?: if (s.recipe.pieces.isEmpty()) "빈 판이에요. 아래 조각을 켜서 꾸며요" else "새 판이에요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                return@Column
            }
            Text("어느 쪽에서 시작해도 내 앱은 그대로예요. 다 만든 뒤 내 앱에 넣을지는 따로 골라요",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (s.showcases.isNotEmpty()) {
                Text("이런 느낌으로 시작", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s.showcases.forEach { sc: com.bamnat.app.recipe.RecipeItem ->
                        OutlinedCard(Modifier.width(150.dp).clickable(onClickLabel = "이 판으로 시작") { vm.startFrom(sc); open = false }) {
                            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                RecipeCover(sc.cover, size = 40.dp)
                                Text(sc.title, style = MaterialTheme.typography.labelLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(sc.summary, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = { vm.startMine(); open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("내 지금 설정에서") }
                OutlinedButton(onClick = { vm.startBlank(); open = false }, modifier = Modifier.heightIn(min = 48.dp)) { Text("빈 판에서") }
            }
        }
    }
}

/** The recipe's piece of [kind], if it has one. */
internal fun Recipe.piece(kind: PieceKind) = pieces.firstOrNull { it.kind == kind }

// ---- the example phone ----

/** The example app: the chosen tab's screen and the bottom tabs in the recipe's order, with its corners. */
@Composable
internal fun MockPhone(r: Recipe, style: WorkStyle?, tab: String, onTab: (String) -> Unit, onPick: () -> Unit = {},
                       /** [TRY-04] 1.36: one scroll for both sides of 비교, so they move together (R-50). */
                       scroll: androidx.compose.foundation.ScrollState? = null,
                       /** 장터 카드 실제 모습: drawn first on the screen (the pay page, the widget on a home screen, the alarm screen). */
                       lead: (@Composable () -> Unit)? = null,
                       /** With [lead]: the tab's own example is left out. */
                       only: Boolean = false,
                       /** False for what is not inside the app (the home screen's widget, the alarm screen): no tab bar. */
                       bar: Boolean = true) {
    val resolved = remember(r) { r.pieces.associate { p -> p.kind to runCatching { RecipeResolve.piece(r, p, RecipeResolve.answers(r, emptyMap())) }.getOrDefault(p.values) } }
    val corners = resolved[PieceKind.PHOTO_LOOK]?.get("corners") as? String
    @Suppress("UNCHECKED_CAST")
    val workTabs = (resolved[PieceKind.LAYOUT]?.get("tabs") as? List<String>).orEmpty()
    // [TRY-07] 1.39 발굴 (3차 PC: 예시 아래 막대는 6칸, 내 앱은 5칸): the bar as the phone draws it ([com.bamnat.core.layout.TabSlots]):
    // my own places as they are (a 묶음, another screen: a 화면 배치 does not move them, as on the phone), else the work's 화면 배치
    // (or my tab order) with 설정 by the 나 button.
    val myBar by androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.settings.collectAsStateWithLifecycle()
    val slots = remember(workTabs, myBar.tabSlots, myBar.tabOrder) {
        com.bamnat.core.layout.TabSlots.resolve(myBar.tabSlots, workTabs.ifEmpty { myBar.tabOrder }, com.bamnat.app.ui.TabPicks.known)
    }
    // [BLU-05] 1.36 부품 꾸미기 on the example app: the parts as the phone draws them (앱 전체·단추·카드 through the theme).
    @Suppress("UNCHECKED_CAST")
    // 1.39 [MKP-06] On a 장터 card, the parts' fonts are the card's small fonts.
    val cardFonts = com.bamnat.app.ui.theme.LocalCardFonts.current
    val rawParts = remember(resolved, cardFonts) {
        (resolved[PieceKind.PARTS]?.get("parts") as? List<Map<String, Any?>>)?.let { l ->
            runCatching { com.bamnat.core.recipe.Blueprint(MarketFonts.lines(com.bamnat.core.recipe.Blueprint.clean(l), cardFonts.orEmpty())) }.getOrNull() }
            ?: com.bamnat.core.recipe.Blueprint.EMPTY
    }
    val blueprint = rawParts.let { b -> val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f; remember(b, dark) { b.forDark(dark) } }
    MaterialTheme(colorScheme = com.bamnat.app.ui.parts.PartTheme.colors(MaterialTheme.colorScheme, blueprint),
        typography = com.bamnat.app.ui.parts.PartTheme.typography(MaterialTheme.typography, blueprint),
        shapes = com.bamnat.app.ui.parts.PartTheme.shapes(shapesFor(corners), blueprint)) {
      // [RUL-02] 조건부 꾸미기 in the example too: the phone's own days decide which lines hold.
      val facts = com.bamnat.app.logic.LocalDayFacts.current ?: if (blueprint.hasFormulas) com.bamnat.app.logic.rememberAppDayFacts() else null
      CompositionLocalProvider(com.bamnat.app.ui.parts.LocalBlueprint provides blueprint, com.bamnat.app.logic.LocalDayFacts provides facts, com.bamnat.app.ui.parts.LocalBlueprintRaw provides rawParts) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Column(
                Modifier.widthIn(max = 340.dp).fillMaxWidth().height(560.dp)
                    .border(6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(32.dp))
                    .padding(6.dp)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(26.dp))
                    .semantics { contentDescription = "예시 앱 화면" },
            ) {
                // [FREE-05] The recipe's 화면 색·배경 and 메모장·가계부 모양 on the example, as the phone would draw them.
                val skin = remember(resolved) { com.bamnat.core.recipe.ScreenLooks.decode(resolved[PieceKind.SCREEN_LOOK]?.get("screens") as? String) }
                CompositionLocalProvider(
                    com.bamnat.app.ui.theme.LocalScreenLooks provides skin,
                    com.bamnat.app.ui.theme.LocalMemoLook provides (resolved[PieceKind.MEMO_LOOK]?.let(::memoLookOf) ?: com.bamnat.core.recipe.MemoLook()),
                    com.bamnat.app.ui.theme.LocalMoneyLook provides (resolved[PieceKind.MONEY_LOOK]?.let(::moneyLookOf) ?: com.bamnat.core.recipe.MoneyLook()),
                    // [LAY-03] The recipe's 꾸밈 층 on the example (none: no decorations, not the phone's own).
                    com.bamnat.app.ui.decor.LocalDecor provides remember(resolved) { com.bamnat.core.recipe.Decor.of(resolved[PieceKind.DECOR]) },
                ) {
                com.bamnat.app.ui.theme.ScreenSkin(com.bamnat.app.ui.theme.screenKeyOf(tab, style), Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize().clickable(onClickLabel = "이 화면 고치기", onClick = onPick).verticalScroll(scroll ?: rememberScrollState()).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lead?.invoke()
                    if (lead == null || !only) when (tab) {
                        TabOrder.CALENDAR -> MockCalendar(resolved, style)
                        TabOrder.MONEY -> {
                            MockParts(resolved[PieceKind.CARDS]?.get("money") as? String, MoneyPart.entries.map { Triple(it.id, it.label,
                                // 빌드 6 확인: 일감 is 현장·가게 words.
                                if (it == MoneyPart.OWED && style != WorkStyle.FIELD && style != WorkStyle.BUSINESS) "빌려준 돈·외상" else it.what) },
                                fits = { id -> fitsHere(com.bamnat.core.guide.FitSurface.MONEY_PARTS, id, style) }, page = TabOrder.MONEY)
                            // [FREE-06]
                            // [CNV-11] 1.37 always there, so 날짜 머리·한 줄·금액 can be picked on the canvas.
                            com.bamnat.app.ui.settings.MoneySample(resolved[PieceKind.MONEY_LOOK]?.let(::moneyLookOf) ?: com.bamnat.core.recipe.MoneyLook(),
                                com.bamnat.app.ui.studio.rememberMyMoney()) {}
                        }
                        TabOrder.MEMOS -> MockMemos(resolved, style)
                        TabOrder.STATS -> MockParts(resolved[PieceKind.CARDS]?.get("stats") as? String, StatsPart.entries.map { Triple(it.id, it.label, it.what) }, bars = true,
                            fits = { id -> fitsHere(com.bamnat.core.guide.FitSurface.STATS_PARTS, id, style) }, page = TabOrder.STATS)
                        TabOrder.PLAZA -> MockNote(com.bamnat.app.ui.theme.LocalScreenLook.current.emoji.orEmpty() + "마을",
                            "마을은 글과 단추 자리가 모두 같아요. 배경과 이모티콘만 내 화면에서 바뀌어요")
                        else -> MockSettings(resolved)
                    }
                }
                }
                }
                // PC 확인 S2 (어두운 한 색에서도 예시 맨 아래 탭 줄만 밝음): the tab bar in the screen's look too, as the phone draws it.
                val barShape = RoundedCornerShape(bottomStart = 26.dp, bottomEnd = 26.dp)
                if (bar) CompositionLocalProvider(com.bamnat.app.ui.theme.LocalScreenLooks provides skin) {
                com.bamnat.app.ui.theme.ScreenSkin(com.bamnat.app.ui.theme.screenKeyOf(tab, style), Modifier.fillMaxWidth().clip(barShape)) {
                val barPart = blueprint.style("app.tabs")
                // 1.39 [MKW-02]: 무늬·그라데이션(paint)만 준 탭 막대도 폰처럼 그 무늬로, 글자는 무늬 평균색에 맞는 색으로.
                val barPaint = barPart.paint.takeIf { barPart.fill == null }
                val barGround = (barPart.fill ?: barPaint?.average)?.let { Color(it.toInt()) }
                CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides (barGround?.let { com.bamnat.app.ui.components.contentColorOn(it) } ?: androidx.compose.material3.LocalContentColor.current)) {
                Row(Modifier.fillMaxWidth().partSpot("app.tabs").then(if (barPaint != null) Modifier.fillWith(barPaint, barShape) else Modifier.background(barPart.fill?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.surfaceContainer, barShape)).padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly) {
                    slots.forEach { slot ->
                        val first = com.bamnat.app.ui.TabPicks.of(slot.keys.first(), style) ?: return@forEach
                        val goes = slot.keys.firstOrNull { k -> TopLevel.entries.any { it.route == k } }
                        val label = (slot as? com.bamnat.core.layout.TabSlots.Group)?.name ?: first.label
                        val on = tab in slot.keys
                        val tp = blueprint.style("app.tab", if (on) setOf("sel") else emptySet())
                        val ink = tp.ink?.let { Color(it.toInt()) } ?: androidx.compose.material3.LocalContentColor.current
                        Column(Modifier.partSpot("app.tab").clickable(enabled = goes != null, onClickLabel = "$label 화면 보기") { goes?.let(onTab) }.semantics { selected = on }.padding(horizontal = 4.dp, vertical = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(Modifier.background(if (on) tp.fill?.let { Color(it.toInt()) } ?: MaterialTheme.colorScheme.secondaryContainer else Color.Transparent, CircleShape).padding(horizontal = 10.dp, vertical = 2.dp)) {
                                Icon(if (on) first.selectedIcon else first.icon, null, Modifier.size(20.dp), tint = ink)
                            }
                            Text(label, style = com.bamnat.app.ui.parts.PartMods.text(tp.copy(ink = null), MaterialTheme.typography.labelSmall), fontSize = tp.size?.sp ?: 10.sp, maxLines = 1, color = ink)
                        }
                    }
                }
                }
                }
                }
            }
        }
      }
    }
}

/**
 * [STU-23] 1.36 눌러서 고치기 (설계서 B-3 "미리 보기에서 바로 누르면 그 부분의 도구가 열림"): where on the example was tapped —
 * "cells" (달력 칸), "days" (근무 이름 줄), "cards" (카드), "memo" (메모장), "money" (가계부), "colors" (바탕). Null: the tap is the
 * screen's own ([MockPhone] onPick).
 */
val LocalMockPick = androidx.compose.runtime.compositionLocalOf<((String) -> Unit)?> { null }

/** A part of the example that opens its own tool when [LocalMockPick] listens. */
@Composable
internal fun Modifier.mockPick(region: String): Modifier {
    val pick = LocalMockPick.current ?: return this
    return this.clickable(onClickLabel = "이 부분 고치기") { pick(region) }
}

/** The kinds of day an example calendar shows when the recipe brings none: what this kind of work usually has. */
internal fun exampleDays(style: WorkStyle?): List<Map<String, Any?>> = when (style) {
    WorkStyle.SHIFT, null -> listOf(day("주간", "day", "주", 0xFFEF6C00), day("야간", "night", "야", 0xFF3949AB), day("쉬는 날", "off", "휴", 0xFF757575))
    // PC 확인 V42: 알바's own words, 일한 날 · 쉬는 날.
    WorkStyle.HOURLY -> listOf(day("일한 날", "day", "일", 0xFF2E7D32), day("쉬는 날", "off", "쉼", 0xFF757575))
    // PC 확인 W42 1.36: a day off is 쉬는 날 in every kind of work (투자는 휴장).
    // F-54: each kind of work's own words on the example days.
    WorkStyle.FIELD -> listOf(day("현장", "day", "현", 0xFF6D4C41), day("쉬는 날", "off", "휴", 0xFF757575))
    WorkStyle.BUSINESS -> listOf(day("영업", "day", "영", 0xFF00897B), day("쉬는 날", "off", "휴", 0xFF757575))
    WorkStyle.INVEST -> listOf(day("장 열림", "day", "장", 0xFF1E88E5), day("휴장", "off", "휴", 0xFF757575))
    else -> listOf(day("근무", "day", "근", 0xFF1E88E5), day("쉬는 날", "off", "휴", 0xFF757575))
}

/** What a calendar card says in the example, in this kind of work's words (F-54). */
private fun mockWhat(part: CalendarPart, style: WorkStyle?): String = when {
    part == CalendarPart.DAY_CARD -> when (style) {
        WorkStyle.FIELD -> "누른 날의 현장·공수·받을 돈"
        WorkStyle.BUSINESS -> "누른 날의 매출·지출·메모"
        WorkStyle.INVEST -> "누른 날의 매매·투자일지"
        WorkStyle.HOURLY -> "누른 날의 출퇴근·시급·알람"
        else -> part.what
    }
    // 빌드 6 확인: each kind of work's own words, not the list of all of them.
    part == CalendarPart.TODAY_WORK -> when (style) {
        WorkStyle.FIELD -> "오늘 현장·공수·일당"
        WorkStyle.BUSINESS -> "오늘 매출·지출"
        WorkStyle.INVEST -> "오늘 매매·수익"
        else -> "오늘 할 일"
    }
    else -> part.what
}

private fun day(name: String, role: String, label: String, color: Long) = mapOf("name" to name, "role" to role, "label" to label, "color" to color)

@Suppress("UNCHECKED_CAST")
@Composable
private fun MockCalendar(resolved: Map<PieceKind, Map<String, Any?>>, style: WorkStyle?) {
    val line = resolved[PieceKind.LAYOUT]?.get("calendar") as? String
    val all = PageLayout.decode(line).arrange(CalendarPart.entries.toList()) { it.id }
    // [CVW-06] The cards above and under the month as the recipe orders them, the month among them.
    val above = com.bamnat.app.ui.calendar.calendarAbove(all.map { it.part })
    // Only the cards this kind of work has, as on the phone (no 오늘 일감·매출 on a 교대 example, 자유도 점검).
    val placed = all.filter { it.shown && it.part != CalendarPart.MONTH && it.part != CalendarPart.ASK && it.part != CalendarPart.JOURNAL &&
        fitsHere(com.bamnat.core.guide.FitSurface.CALENDAR_PARTS, it.part.id, style) }
    // [CNV-06] 1.37 Each card says which it is, for the studio's canvas to pick and move it.
    fun spot(p: CalendarPart) = com.bamnat.app.ui.studio.StudioEdit.cardId(TabOrder.CALENDAR, p.id)
    placed.filter { above(it.part) && it.part != CalendarPart.SUMMARY && it.part != CalendarPart.COUNTS }.forEach { MockCard(it.part.labelFor(style), mockWhat(it.part, style), spot = spot(it.part)) }
    if (placed.any { it.part == CalendarPart.SUMMARY && above(it.part) }) Box(Modifier.partSpot(spot(CalendarPart.SUMMARY)).mockPick("cards")) { MockSummary(style) }
    val ownDays = (resolved[PieceKind.SHIFT_LOOK]?.get("items") as? List<Map<String, Any?>>)?.takeIf { it.isNotEmpty() }
    val days = ownDays ?: exampleDays(style)
    // PC 확인 Z13: this month's own counts of each kind of day, when the calendar has work (else the example's).
    val counts = rememberMonthKinds(days)
    val countsRow: @Composable () -> Unit = {
        Row(Modifier.partSpot(spot(CalendarPart.COUNTS)).mockPick("days"), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEachIndexed { i, d -> Text("${d["label"]} ${counts?.getOrNull(i) ?: if (d["role"] == "off") 8 else 11}", style = MaterialTheme.typography.labelSmall,
                color = ((d["color"] as? Long) ?: 0xFF757575L).asColor()) }
        }
    }
    if (placed.any { it.part == CalendarPart.COUNTS && above(it.part) }) countsRow()
    Box(Modifier.mockPick("cells")) { MockMonth(days, resolved[PieceKind.CAL_THEME].orEmpty(), viewOf(resolved[PieceKind.CAL_VIEW]), ownDays.orEmpty(), style) }
    placed.filter { !above(it.part) }.forEach {
        when (it.part) {
            CalendarPart.SUMMARY -> Box(Modifier.partSpot(spot(CalendarPart.SUMMARY)).mockPick("cards")) { MockSummary(style) }
            CalendarPart.COUNTS -> countsRow()
            else -> MockCard(it.part.labelFor(style), mockWhat(it.part, style), spot = spot(it.part))
        }
    }
}

/** [CVW-01] 달력 모양's values as the calendar reads them. */
internal fun viewOf(v: Map<String, Any?>?): com.bamnat.core.recipe.CalendarView = if (v == null) com.bamnat.core.recipe.CalendarView() else
    com.bamnat.core.recipe.CalendarView(size = v["size"] as? String, badge = v["badge"] as? String, date = v["date"] as? String, text = v["text"] as? String,
        grid = v["grid"] as? String, memo = (v["memo"] as? Number)?.toInt(), order = (v["order"] as? String)?.split(',')?.filter { it.isNotEmpty() }?.ifEmpty { null },
        // [FREE-01][FREE-02]
        looks = com.bamnat.core.recipe.CellLook.decodeAll(v["looks"] as? String), pattern = com.bamnat.core.recipe.CellPattern.decode(v["pattern"] as? String),
        todayLook = (v["today"] as? Number)?.toInt())

/** [CVW-01] [CalendarView] back as a piece's values (what is the app's own is left out). */
internal fun valuesOf(v: com.bamnat.core.recipe.CalendarView, keep: Map<String, Any?>): Map<String, Any?> =
    keep.filterKeys { it in setOf("hours", "lunar", "spend") } + listOfNotNull(v.size?.let { "size" to it }, v.badge?.let { "badge" to it },
        v.date?.let { "date" to it }, v.text?.let { "text" to it }, v.grid?.let { "grid" to it }, v.memo?.let { "memo" to it.toLong() },
        v.order?.let { "order" to it.joinToString(",") },
        v.looks.takeIf { it.isNotEmpty() }?.let { l -> "looks" to l.joinToString("/") { it.encode() } },
        v.pattern.takeIf { !it.isDefault }?.let { "pattern" to it.encode() }, v.todayLook?.let { "today" to it.toLong() }).toMap()

/** [R136-07][STU-25] This week and the next ([weeks] in all) of the phone's own calendar, each day in [days]' look for its kind of day. */
internal class MyWeeks(val first: java.time.LocalDate, val today: java.time.LocalDate, val days: List<com.bamnat.app.ui.calendar.SampleDay>)

@Composable
internal fun rememberMyWeeks(days: List<Map<String, Any?>>, weeks: Int = 2, from: java.time.LocalDate? = null,
    /** PC 확인 1.37 A9 (현장 일감 3건인데 입어 보기·스튜디오 달력이 예시): a kind of work written day by day has no 근무표, so its month is
     *  my own dates and holidays, the days blank, never the example. */
    blankOk: Boolean = false,
    /** PC 확인 1.37 A11: on those blank days, my 일감 (현장) by its 공수 or title. */
    jobs: List<com.bamnat.core.money.Job>? = null,
    /** [CELL-02] 1.39 My memos by date ([com.bamnat.app.ui.studio.rememberMyDayMemos]); a day's jobs come first among its lines. */
    memos: Map<java.time.LocalDate, List<String>>? = null): MyWeeks? {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val snapFlow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    val today = remember { com.bamnat.core.time.Today.date() }
    // [LAY-03] From the week of [from] (a day the 꾸밈 층 is pinned to) when it is given, else this week.
    // [LINK-01] 1.39 On the week start my calendar keeps (월요일 시작이면 예시도 월요일부터), so the studio and 입어 보기 draw my weeks as they are.
    val settingsNow by c.settingsRepository.settings.collectAsStateWithLifecycle()
    val weekStart = settingsNow.weekStart
    val first = remember(today, from, weekStart) { (from ?: today).with(java.time.temporal.TemporalAdjusters.previousOrSame(weekStart)) }
    val real = remember(snap, days, first, weeks, blankOk, jobs, memos) {
        val s = snap ?: return@remember null
        val resolved = s.resolver.resolveRange(first, first.plusDays(weeks * 7L - 1))
        // [CELL-02] What I wrote on the day: its jobs by 업체 (or name), then its memos.
        fun written(d: java.time.LocalDate): List<String> =
            jobs.orEmpty().filter { j -> !d.isBefore(j.date) && !d.isAfter(j.endDate ?: j.date) }.map { it.client.ifBlank { it.title }.trim() }.filter { it.isNotEmpty() } +
                memos?.get(d).orEmpty()
        if (resolved.none { it.shiftType != null }) return@remember if (!blankOk) null else resolved.map { r ->
            val job = jobs?.firstOrNull { j -> !r.date.isBefore(j.date) && !r.date.isAfter(j.endDate ?: j.date) }
            if (job != null) com.bamnat.app.ui.calendar.SampleDay(com.bamnat.app.ui.studio.jobCell(job), 0xFF8D6E63L, written(r.date),
                holiday = com.bamnat.core.holiday.KoreanHolidays.nameOf(r.date))
            else com.bamnat.app.ui.calendar.SampleDay("", 0x00000000L, written(r.date), holiday = com.bamnat.core.holiday.KoreanHolidays.nameOf(r.date), off = true, blank = true)
        }
        resolved.map { r ->
            // [LINK-01] 1.39 The day as the phone's calendar has it (쉬는 날의 대근은 그 근무로, 특근만 있는 날은 특근으로, 연차·직접 바꾼 날도).
            val w = com.bamnat.core.schedule.DayWork.of(r, s.hasPattern)
            val covered = r.coveredShift?.takeIf { !r.isWorkDay && !it.isOff }
            val t = covered ?: r.shiftType
            val onlyExtra = r.extras.firstOrNull()?.takeIf { !r.isWorkDay && covered == null }
            val role = w.role?.key
            val item = if (onlyExtra != null) null else t?.let { k -> days.firstOrNull { (it["name"] as? String)?.trim() == k.name.trim() } ?: days.firstOrNull { it["role"] == role } }
            com.bamnat.app.ui.calendar.SampleDay(
                ((item?.get("emoji") as? String) ?: item?.get("label")?.toString() ?: onlyExtra?.kind?.mark ?: t?.label).orEmpty(),
                (item?.get("color") as? Long) ?: (onlyExtra?.kind?.colorArgb ?: t?.colorArgb)?.or(0xFF000000L) ?: 0xFF757575L,
                memos = written(r.date),
                hours = w.totalMinutes.takeIf { it > 0 }?.let { m -> "${m / 60}h" },
                holiday = com.bamnat.core.holiday.KoreanHolidays.nameOf(r.date), off = w.off, role = role, work = w.states,
            )
        }
    }
    return remember(real, first, today) { real?.let { MyWeeks(first, today, it) } }
}

/** An example month in the recipe's 달력 모양 and theme: the kinds of day in turn (2 days each), example memos, today ringed. */
@Composable
private fun MockMonth(days: List<Map<String, Any?>>, theme: Map<String, Any?>, view: com.bamnat.core.recipe.CalendarView,
                      /** The recipe's own 근무 모양 (none: my calendar keeps its own colours, not the example's). */
                      ownDays: List<Map<String, Any?>> = days,
                      /** PC 확인 1.37 A6: the example's memos and hours in this kind of work's words (투자 has no 8h, no 월급날). */
                      style: WorkStyle? = null) {
    // [R136-07] 1.36 (리뷰 R-16·R-21, 후보 1 '내 달력으로 입어 보기'): this week and the next of the phone's own calendar, each day in
    // the recipe's look for its kind of day (the same name, then the same role) or its own; the real holidays and today. Memos stay
    // off the picture. A calendar with no kinds of day yet keeps the example.
    // [LAY-03] PC 확인 C1 1.36 (25일 칸 💰가 예시에 없어요): a 꾸밈 pinned to a date past these two weeks moves the example to its week.
    val now = remember { com.bamnat.core.time.Today.date() }
    val layer = com.bamnat.app.ui.decor.LocalDecor.current
    val pinned = remember(layer, now) { layer.nextPinned(now)?.takeIf { it.toEpochDay() - now.minusDays((now.dayOfWeek.value % 7).toLong()).toEpochDay() >= 14 } }
    // PC 확인 C4 1.36 (예시 근무 색이 실제 달력 색과 달라요): my days take a recipe's 근무 모양 only when it has one.
    // [CNV-12] 1.37 (사용자 "실제의 현재 모습"): on my phone this whole month of my calendar, from the week of the 1st; the 올리기 표지 the example.
    val realOn = com.bamnat.app.ui.studio.LocalRealLook.current
    val monthStart = remember(now) { now.withDayOfMonth(1) }
    val weeksN = remember(now) { ((monthStart.dayOfWeek.value % 7) + now.lengthOfMonth() + 6) / 7 }
    val whole = pinned == null
    val myJobs = if (style == WorkStyle.FIELD) com.bamnat.app.ui.studio.rememberMyJobs() else null
    val myMemos = com.bamnat.app.ui.studio.rememberMyDayMemos()
    val mine = rememberMyWeeks(ownDays, weeks = if (whole) weeksN else 2, from = pinned ?: monthStart, blankOk = style != null && !style.usesPattern && style != WorkStyle.HOURLY,
        jobs = myJobs, memos = myMemos).takeIf { realOn }
    // [CELL-02] 1.39 My month listed the way my calendar lists it (날짜 칸에 일감·메모를 줄마다), in the theme's look.
    val mySettings by androidx.compose.ui.platform.LocalContext.current.appContainer.settingsRepository.settings.collectAsStateWithLifecycle()
    // [TRY-05] 1.39 PC a483ca15 (입어 보기엔 메모 줄, 입은 달력은 점): the calendar's own choice, on the 달력 모양 this example draws (a theme
    // of someone else's keeps my 메모 줄 수, [com.bamnat.core.recipe.keepMyCellContent]).
    val listed = mine != null && com.bamnat.app.ui.calendar.cellListOn(mySettings.cellList, style, mySettings.memoInCells, view,
        hasMemos = !myMemos.isNullOrEmpty())
    val rows = if (mine != null && whole) weeksN else 2
    val n = rows * 7
    val exFirst = pinned?.let { it.minusDays((it.dayOfWeek.value % 7).toLong()) } ?: EXAMPLE_FIRST
    val today = mine?.today ?: now
    val first = mine?.first ?: today
    // PC 확인 V19 1.36: the 공방 card is my own month, blank days before any work; elsewhere the example shows the kinds of day.
    val own = LocalOwnMonth.current
    val blank = remember(own, mine, first) { if (mine != null || !own) null else (0 until 14).map { i -> val d = first.minusDays((first.dayOfWeek.value % 7).toLong()).plusDays(i.toLong())
        com.bamnat.app.ui.calendar.SampleDay("", 0x00000000L, holiday = com.bamnat.core.holiday.KoreanHolidays.nameOf(d), off = true, blank = true) } }
    val start0 = if (blank != null) first.minusDays((first.dayOfWeek.value % 7).toLong()) else first
    val real = mine?.days ?: blank
    // PC 확인 7b219467 (알바·현장 예시에 '월급날'·'인수인계'): the example's memos in each kind of work's own words.
    val memos = when (style) {
        WorkStyle.HOURLY -> listOf(listOf("병원 예약", "택배 오는 날"), emptyList(), listOf("대타 부탁"), emptyList(), listOf("가족 모임", "장보기", "헬스"), emptyList(), listOf("알바비 들어오는 날"))
        WorkStyle.FIELD -> listOf(listOf("자재 챙기기", "택배 오는 날"), emptyList(), listOf("현장 이동"), emptyList(), listOf("가족 모임", "장보기"), emptyList(), listOf("일당 받는 날"))
        WorkStyle.BUSINESS -> listOf(listOf("재료 주문", "택배 오는 날"), emptyList(), listOf("단골 예약"), emptyList(), listOf("가족 모임", "장보기"), emptyList(), listOf("카드 정산일"))
        WorkStyle.INVEST -> listOf(listOf("실적 발표", "택배 오는 날"), emptyList(), listOf("매매 일지"), emptyList(), listOf("가족 모임", "장보기"), emptyList(), listOf("배당 들어오는 날"))
        WorkStyle.DAY -> listOf(listOf("병원 예약", "택배 오는 날"), emptyList(), listOf("주간 회의"), emptyList(), listOf("가족 모임", "장보기", "헬스"), emptyList(), listOf("월급날"))
        else -> listOf(listOf("병원 예약", "택배 오는 날"), emptyList(), listOf("인수인계"), emptyList(), listOf("가족 모임", "장보기", "헬스"), emptyList(), listOf("월급날"))
    }
    val sample = real ?: (0 until 14).map { i ->
        val d = days[(i / 2) % days.size]
        com.bamnat.app.ui.calendar.SampleDay(((d["emoji"] as? String) ?: d["label"]?.toString()).orEmpty(), (d["color"] as? Long) ?: 0xFF757575L,
            memos[i % memos.size], hours = if (d["role"] == "off" || style == WorkStyle.INVEST) null else "8h", off = d["role"] == "off", role = d["role"] as? String)
    }
    Column(Modifier.partSpot("cal"), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        // [LAY-03] The 꾸밈 층's 달력 머리 on the example's title, too.
        // [BLU-09] The example's title is the 달력 머리 and 달 이름, drawn as the parts say and found by a tap in the 부품 도구.
        val bp = com.bamnat.app.ui.parts.LocalBlueprint.current
        val headScope = com.bamnat.app.logic.scopeOn(com.bamnat.core.time.Today.date())
        val head = bp.style("cal.head", scope = headScope)
        val title = bp.style("cal.head.title", scope = headScope)
        // PC 10차 사용성 제안 (점선이 글자만 감싸 범위가 헷갈림): the head is the whole row, as on the calendar.
        Box(Modifier.fillMaxWidth().partSpot("cal.head").decor(com.bamnat.core.recipe.Decor.HEAD).then(com.bamnat.app.ui.parts.PartMods.box(head.copy(ink = null), RoundedCornerShape(0.dp)))) {
            if (title.hide != true) Box(Modifier.partSpot("cal.head.title").then(com.bamnat.app.ui.parts.PartMods.box(title.copy(ink = null), RoundedCornerShape(0.dp)))) {
                com.bamnat.app.ui.parts.PartText(if (real != null) "${today.monthValue}월 · 내 달력" else "예시 달력", title,
                    MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold))
            }
        }
        // F-92: a cell design or pattern is drawn by the month's own cell, as 자세히 and the phone do (the plain sample has neither).
        // U15 1.36 (빌드 확인: 누르고 있으면 전 모양이 다른 예시 날로 바뀜): with no own calendar both ways draw the same example — the
        // same days from the same Sunday the 9th, today the 3rd cell — so a cell design added or taken away changes only the looks.
        if ((view.looks.isNotEmpty() || bp.touches("cal") || com.bamnat.app.ui.parts.LocalPartSpots.current != null) && blank == null) com.bamnat.app.ui.settings.CellWeeks(view, rows = if (real != null) rows else 2, onCell = null,
            start = if (real != null) start0 else exFirst, todayDate = if (real != null) today else exFirst.plusDays(2), days = sample,
            todayColor = (theme["today"] as? Long)?.asColor(), listed = listed)
        else if (real != null) com.bamnat.app.ui.calendar.CalendarViewSample(view, sample, cell = theme["cell"] as? String, today = (theme["today"] as? Long)?.asColor(),
            rows = if (mine != null) rows else 2, numbers = (0 until (if (mine != null) n else 14)).map { start0.plusDays(it.toLong()).dayOfMonth },
            todayAt = (today.toEpochDay() - start0.toEpochDay()).toInt(), listed = listed)
        else com.bamnat.app.ui.calendar.CalendarViewSample(view, sample, cell = theme["cell"] as? String, today = (theme["today"] as? Long)?.asColor(), rows = 2,
            numbers = (0 until 14).map { exFirst.plusDays(it.toLong()).dayOfMonth })
        if (mine == null) Text("근무를 넣으면 내 달력으로 보여요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** PC 확인 V19 1.36: true where the picture is my own look as it is (the 공방 card), so it shows my month even with no work in it. */
internal val LocalOwnMonth = androidx.compose.runtime.staticCompositionLocalOf { false }

/** U15: the example weeks start on a Sunday the 9th (as the plain example always did). */
private val EXAMPLE_FIRST: java.time.LocalDate = java.time.LocalDate.of(2026, 8, 9)

@Composable
private fun MockSummary(style: WorkStyle?) {
    // [STU-03] What this kind of work's summary card says (자유도 점검 F-45: a 투자·가게 example showed 근무·급여).
    val items = when (style) {
        // PC 확인 V43: the same count as the example month's 일 11.
        WorkStyle.HOURLY -> listOf("일한 날" to "11일", "시간" to "58h", "급여" to "예상")
        WorkStyle.FIELD -> listOf("일한 날" to "15일", "공수" to "16.5", "받을 돈" to "예상")
        WorkStyle.BUSINESS -> listOf("영업" to "24일", "매출" to "합계", "순이익" to "예상")
        WorkStyle.INVEST -> listOf("기록" to "9일", "수익률" to "이번 달", "원금" to "합계")
        else -> listOf("근무" to "18일", "시간" to "162h", "급여" to "예상")
    }.let { ex ->
        // PC 확인 1.37 A11: 현장 on my phone says my 일감 this month (일한 날 · 공수 · 받을 돈).
        val jobs = if (style == WorkStyle.FIELD) com.bamnat.app.ui.studio.rememberMyJobs() else null
        val rules = com.bamnat.app.ui.studio.rememberFieldRules()
        if (jobs != null) return@let com.bamnat.app.ui.studio.jobMonth(jobs, rules).let { m ->
            listOf("일한 날" to "${m.days}일", (if (m.units != null) "공수" to com.bamnat.core.money.DayRate.trim(m.units) else "일감" to "${m.count}건"),
                "받을 돈" to (if (m.amount > 0) com.bamnat.app.ui.studio.won(m.amount) else "0원"))
        }
        // PC 확인 Z5 (예시 숫자 '근무 18일 · 162h'): this month of the phone's own calendar, when it has work in it.
        // PC 확인 1.37 A10 (현장 기록 0건인데 '일한 날 15일 · 공수 16.5'): on my phone a kind of work with no 근무표 says my month, 0 when empty.
        val realOn = com.bamnat.app.ui.studio.LocalRealLook.current
        val noTable = style != null && !style.usesPattern && style != WorkStyle.HOURLY
        val mine = rememberMonthWork() ?: if (realOn && noTable) 0 to 0 else return@let ex
        if (style == WorkStyle.BUSINESS || style == WorkStyle.INVEST) (if (realOn) ex.map { (k, v) -> k to (if (v.endsWith("일")) "${mine.first}일" else v) } else ex)
        else listOf((if (style == WorkStyle.HOURLY || style == WorkStyle.FIELD) "일한 날" else "근무") to "${mine.first}일", "시간" to "${mine.second}h", ex.last())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach { (k, v) ->
            Column(Modifier.weight(1f).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(6.dp)) {
                Text(k, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(v, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

@Composable
private fun MockCard(title: String, what: String, bars: Boolean = false, spot: String? = null,
    /** [CNV-09] What the card shows on the phone (example numbers), in place of the words about it. */
    body: com.bamnat.app.ui.studio.StudioExamples.Body? = null) {
    Card(Modifier.fillMaxWidth().partSpot("app.card").let { m -> if (spot != null) m.partSpot(spot) else m }.mockPick("cards"), shape = MaterialTheme.shapes.medium, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            if (body == null) Text(what, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            else {
                body.rows.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, style = MaterialTheme.typography.bodySmall, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Text(v, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                }
                body.meter?.let { f ->
                    Box(Modifier.fillMaxWidth().height(6.dp).background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.extraSmall)) {
                        Box(Modifier.fillMaxWidth(f).fillMaxSize().background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall))
                    }
                }
            }
            if (bars) Row(Modifier.height(24.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                listOf(0.5f, 0.8f, 0.65f, 1f, 0.7f).forEach { h -> Box(Modifier.width(14.dp).fillMaxSize(h).background(MaterialTheme.colorScheme.primary, MaterialTheme.shapes.extraSmall)) }
            }
        }
    }
}

@Composable
private fun MockParts(line: String?, parts: List<Triple<String, String, String>>, bars: Boolean = false, fits: (String) -> Boolean = { true }, page: String? = null) {
    val placed = PageLayout.decode(line).arrange(parts) { it.first }.filter { it.shown && fits(it.part.first) }
    // [CNV-12] My own numbers where the card has them (this month's money, work days and hours); the example elsewhere.
    val realOn = com.bamnat.app.ui.studio.LocalRealLook.current
    val myMoney = if (page == TabOrder.MONEY) com.bamnat.app.ui.studio.rememberMyMoney() else null
    val myWork = if (page == TabOrder.STATS) rememberMonthWork()?.takeIf { it.first > 0 } else null
    placed.forEach { (p, _, _) -> MockCard(p.second, p.third, bars && p.first in setOf("weeks", "trend", "year", "three"),
        spot = page?.let { com.bamnat.app.ui.studio.StudioEdit.cardId(it, p.first) },
        // PC 확인 1.37 A7 (내 합계 옆에 예시 숫자 '세전 3,120,000원'): with my own numbers on the page, a card that has none of them says
        // what it is for instead of made-up numbers that read as mine.
        // Example numbers only on the 올리기 표지 (or a 가계부·통계 page whose kind I have nothing of yet: then words, not numbers).
        body = when {
            myMoney != null -> com.bamnat.app.ui.studio.myMoneyBody(p.first, myMoney)
            myWork != null -> myWork.takeIf { p.first == "tiles" }?.let { (d, h) -> com.bamnat.app.ui.studio.StudioExamples.Body(listOf("근무한 날" to "${d}일", "근무 시간" to "${h}h")) }
            realOn -> null
            else -> page?.let { com.bamnat.app.ui.studio.StudioExamples.body(it, p.first) }
        }) }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun MockMemos(resolved: Map<PieceKind, Map<String, Any?>>, style: WorkStyle? = null) {
    val placed = PageLayout.decode(resolved[PieceKind.CARDS]?.get("memo") as? String).arrange(MemoPart.entries.toList()) { it.id }.filter { it.shown }
    placed.forEach { p ->
        when (p.part) {
            MemoPart.SEARCH -> Box(Modifier.fillMaxWidth().partSpot(com.bamnat.app.ui.studio.StudioEdit.cardId(TabOrder.MEMOS, p.part.id)).background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.extraLarge).padding(10.dp)) {
                Text("🔍 제목·내용·#태그 찾기", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            MemoPart.NOTEBOOKS -> Row(Modifier.partSpot(com.bamnat.app.ui.studio.StudioEdit.cardId(TabOrder.MEMOS, p.part.id)), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("전체", when (style) { WorkStyle.BUSINESS -> "가게"; WorkStyle.INVEST -> "매매"; WorkStyle.FIELD -> "현장"; else -> "근무" }, "개인").forEach { Text(it, Modifier.background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer) }
            }
            else -> Unit
        }
    }
    val tpls = (resolved[PieceKind.MEMO_TEMPLATES]?.get("items") as? List<Map<String, Any?>>).orEmpty()
    val journal = (resolved[PieceKind.INVEST]?.get("journal") as? List<String>).orEmpty()
    tpls.forEach { t ->
        MockCard("${t["emoji"] ?: "📝"} ${t["title"]}" + ((t["repeat"] as? String)?.let { " · ${repeatLabel(it)}" } ?: ""),
            (t["check"] as? List<String>)?.joinToString(" · ") { "☐ $it" }?.ifEmpty { null } ?: (t["body"] as? String).orEmpty())
    }
    if (journal.isNotEmpty()) MockCard("📒 투자일지", journal.joinToString(" · "))
    // [FREE-04] The memos in the recipe's 메모장 모양.
    // [CNV-11] 1.37 always there, so each memo card's parts (카드·제목·내용·날짜) can be picked on the canvas.
    // [CNV-12] My own memos on my phone (pinned first), the example ones for the 올리기 표지.
    val myMemos = com.bamnat.app.ui.studio.rememberMyMemos()
    Box(Modifier.mockPick("memo")) { com.bamnat.app.ui.memo.MemoLookSample(resolved[PieceKind.MEMO_LOOK]?.let(::memoLookOf) ?: com.bamnat.core.recipe.MemoLook(), myMemos, style) {} }
}

/** [FREE-04][FREE-06] A piece's values as the look they write (the same k=v line the settings keep). */
internal fun memoLookOf(v: Map<String, Any?>): com.bamnat.core.recipe.MemoLook = com.bamnat.core.recipe.MemoLook.decode(lookLine(v))

internal fun moneyLookOf(v: Map<String, Any?>): com.bamnat.core.recipe.MoneyLook = com.bamnat.core.recipe.MoneyLook.decode(lookLine(v))

private fun lookLine(v: Map<String, Any?>): String = v.entries.mapNotNull { (k, x) ->
    when (x) {
        is Boolean -> "$k=" + if (x) 1 else 0
        is Long -> if (k in setOf("cardColor", "bg", "income", "expense")) "$k=" + (x and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0') else "$k=$x"
        is Number -> "$k=${x.toLong()}"
        is String -> "$k=$x"
        else -> null
    }
}.joinToString(";")

@Composable
private fun MockSettings(resolved: Map<PieceKind, Map<String, Any?>>) {
    val w = resolved[PieceKind.WIDGET]
    MockNote("설정", "아래 메뉴 순서, 위젯 모양, 모서리를 여기서 바꿔요")
    if (w != null) {
        val alpha = ((w["alpha"] as? Number)?.toInt() ?: 100).coerceIn(30, 100)
        val dark = w["theme"] == "dark"
        Box(Modifier.fillMaxWidth().height(70.dp).background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF5C6BC0), Color(0xFF26A69A))), MaterialTheme.shapes.medium).padding(10.dp)) {
            Column(Modifier.fillMaxSize().background((if (dark) Color(0xFF1E1E24) else Color(0xFFFAFAFA)).copy(alpha = alpha / 100f), MaterialTheme.shapes.small).padding(8.dp)) {
                Text("위젯 · 오늘 주간 07:00", style = MaterialTheme.typography.labelMedium, color = if (dark) Color.White else Color(0xFF1B1B1F))
                if (w["memo"] == true) Text("메모 한 줄", style = MaterialTheme.typography.labelSmall, color = if (dark) Color.White else Color(0xFF1B1B1F))
            }
        }
    }
}

@Composable
private fun MockNote(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun repeatLabel(r: String) = when {
    r.startsWith("M") -> "매달 ${r.drop(1)}일"
    r.startsWith("W") -> "매주 " + listOf("월", "화", "수", "목", "금", "토", "일").getOrElse((r.drop(1).toIntOrNull() ?: 1) - 1) { "" } + "요일"
    else -> r
}

// ---- what each tab's screen can change ----

/** The pieces a tab's screen shows, in the order they are edited there. */
private fun piecesOn(tab: String): List<PieceKind> = when (tab) {
    TabOrder.CALENDAR -> listOf(PieceKind.SHIFT_LOOK, PieceKind.CAL_THEME, PieceKind.ALARMS)
    TabOrder.MONEY -> listOf(PieceKind.MONEY_LOOK, PieceKind.LEDGER, PieceKind.SHOP, PieceKind.FIELD_SHEET)
    TabOrder.MEMOS -> listOf(PieceKind.MEMO_LOOK, PieceKind.MEMO_TEMPLATES, PieceKind.INVEST)
    TabOrder.STATS -> listOf(PieceKind.HOURLY_RULES)
    // [FREE-05] 화면 색·배경 comes from 설정 › 화면 꾸미기 (the colours and pictures are made there, with the example on top).
    TabOrder.SETTINGS -> listOf(PieceKind.APP_FEEL, PieceKind.WIDGET, PieceKind.SCREEN_LOOK)
    else -> emptyList()
}

@Composable
private fun StudioEditors(r: Recipe, vm: RecipeMakeViewModel, tab: String) {
    val fits: (PieceKind) -> Boolean = { k -> vm.style == null || vm.style in k.styles }
    @Suppress("UNCHECKED_CAST")
    when (tab) {
        TabOrder.CALENDAR -> {
            // [CVW-02] 달력 모양 first: the phone above shows it at once.
            val cv = r.piece(PieceKind.CAL_VIEW)
            StudioCard("달력 모양", "칸 높이, 근무 표시, 메모 줄을 바꾸면 위 예시 달력이 바로 바뀌어요",
                trailing = { Switch(cv != null, { on -> vm.setPiece(PieceKind.CAL_VIEW, if (on) vm.starter(PieceKind.CAL_VIEW) else null) },
                    modifier = Modifier.semantics { contentDescription = "달력 모양 넣기" }) }) {
                if (cv != null) com.bamnat.app.ui.calendar.CalendarViewEditor(viewOf(cv.values), { v -> vm.setPiece(PieceKind.CAL_VIEW, valuesOf(v, cv.values)) }, sample = {})
            }
            PartsEditor("달력 화면 카드", CalendarPart.entries.filter { it != CalendarPart.ASK && it != CalendarPart.JOURNAL && it != CalendarPart.MONTH && fitsHere(com.bamnat.core.guide.FitSurface.CALENDAR_PARTS, it.id, vm.style) }.map { Triple(it.id, it.labelFor(vm.style), it.movable) },
                r.piece(PieceKind.LAYOUT)?.values?.get("calendar") as? String) { line -> vm.setValue(PieceKind.LAYOUT, "calendar", line) }
        }
        TabOrder.MONEY -> PartsEditor("가계부 카드", MoneyPart.entries.filter { fitsHere(com.bamnat.core.guide.FitSurface.MONEY_PARTS, it.id, vm.style) }.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.CARDS)?.values?.get("money") as? String) { line -> vm.setValue(PieceKind.CARDS, "money", line) }
        TabOrder.MEMOS -> PartsEditor("메모 화면", MemoPart.entries.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.CARDS)?.values?.get("memo") as? String) { line -> vm.setValue(PieceKind.CARDS, "memo", line) }
        TabOrder.STATS -> PartsEditor("통계 카드", StatsPart.entries.filter { fitsHere(com.bamnat.core.guide.FitSurface.STATS_PARTS, it.id, vm.style) }.map { Triple(it.id, it.label, it.movable) },
            r.piece(PieceKind.CARDS)?.values?.get("stats") as? String) { line -> vm.setValue(PieceKind.CARDS, "stats", line) }
        TabOrder.SETTINGS -> {
            TabsEditor((r.piece(PieceKind.LAYOUT)?.values?.get("tabs") as? List<String>).orEmpty(), vm.style) { list -> vm.setValue(PieceKind.LAYOUT, "tabs", list) }
            CornersEditor(r.piece(PieceKind.PHOTO_LOOK)?.values?.get("corners") as? String) { c -> vm.setValue(PieceKind.PHOTO_LOOK, "corners", c) }
        }
        else -> Unit
    }
    piecesOn(tab).filter(fits).forEach { k -> PieceSection(r, k, vm) }
    if (tab == TabOrder.CALENDAR) ThemeTokens(r) { f -> vm.edit(f) }
}

/** A page's parts in order: up and down for the movable ones, shown or hidden for each, and 처음대로. */
@Composable
internal fun PartsEditor(title: String, parts: List<Triple<String, String, Boolean>>, line: String?, note: String? = null, onChange: (String?) -> Unit) {
    val placed = PageLayout.decode(line).arrange(parts) { it.first }
    StudioCard(title, note ?: if (line == null) "처음 모양이에요. 바꾸면 레시피에 들어가요" else "바꾼 순서와 숨김이 레시피에 들어가요",
        onReset = line?.let { { onChange(null) } }) {
        placed.forEachIndexed { i, p ->
            val (id, label, movable) = p.part
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    color = if (p.shown) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                if (movable) {
                    IconButton(onClick = { onChange(PageLayout.of(PageLayout.move(placed, i, -1)) { it.first }.encode()) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, "$label 위로")
                    }
                    IconButton(onClick = { onChange(PageLayout.of(PageLayout.move(placed, i, 1)) { it.first }.encode()) }, enabled = i < placed.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, "$label 아래로")
                    }
                }
                Switch(p.shown, { on -> onChange(PageLayout.of(placed.map { if (it.part.first == id) it.copy(shown = on) else it }) { it.first }.encode()) },
                    modifier = Modifier.semantics { contentDescription = "$label 보이기" })
            }
        }
    }
}

@Composable
internal fun TabsEditor(saved: List<String>, style: WorkStyle?, onChange: (List<String>?) -> Unit) {
    val order = TabOrder.of(saved)
    StudioCard("아래 메뉴 순서", "맨 앞 탭이 앱을 열면 처음 보이는 화면이에요", onReset = saved.takeIf { it.isNotEmpty() }?.let { { onChange(null) } }) {
        order.forEachIndexed { i, key ->
            val t = TopLevel.entries.firstOrNull { it.route == key } ?: return@forEachIndexed
            Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(t.icon, null, Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
                Text(t.labelFor(style), Modifier.weight(1f))
                IconButton(onClick = { onChange(TabOrder.toSave(TabOrder.move(order, key, i - 1)).ifEmpty { null }) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "${t.labelFor(style)} 앞으로") }
                IconButton(onClick = { onChange(TabOrder.toSave(TabOrder.move(order, key, i + 1)).ifEmpty { null }) }, enabled = i < order.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "${t.labelFor(style)} 뒤로") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CornersEditor(corners: String?, onChange: (String?) -> Unit) {
    StudioCard("모서리", "카드와 단추의 둥근 정도예요", onReset = corners?.let { { onChange(null) } }) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("round" to "둥글게", "soft" to "보통", "square" to "각지게").forEach { (k, label) ->
                FilterChip(corners == k, { onChange(k) }, label = { Text(label) }, shape = shapesFor(k).small)
            }
        }
    }
}

/** One piece on its own: in the recipe or not, and its fields when it is. */
@Composable
private fun PieceSection(r: Recipe, kind: PieceKind, vm: RecipeMakeViewModel) {
    val piece = r.piece(kind)
    StudioCard(kind.label, if (piece == null) "넣지 않았어요. 켜면 예시 값으로 시작해요" else "받는 분이 고를 수 있는 한 묶음이에요",
        trailing = { Switch(piece != null, { on -> vm.setPiece(kind, if (on) vm.starter(kind) else null) }, modifier = Modifier.semantics { contentDescription = "${kind.label} 넣기" }) }) {
        if (piece != null) FieldsForm(kind.fields.filter(::studioField), piece.values, kind) { k, v -> vm.setValue(kind, k, v) }
    }
}

/** Fields the studio edits by hand: the pictures (그림 꾸미기) come from the settings screen, the company file from 회사 화면. */
internal fun studioField(f: Field): Boolean = when (f) {
    is Field.Text -> f.guard || f.key == "body"
    is Field.Items -> f.key != "icons"
    else -> true
}

@Composable
internal fun StudioCard(title: String, sub: String, onReset: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, body: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                    Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                onReset?.let { TextButton(onClick = it) { Text("처음대로") } }
                trailing?.invoke()
            }
            body()
        }
    }
}

// ---- a form from the piece catalog ----

private val KEY_LABELS = com.bamnat.core.recipe.RecipeWords.KEYS
private val PICK_LABELS = com.bamnat.core.recipe.RecipeWords.PICKS

@OptIn(ExperimentalLayoutApi::class)
@Suppress("UNCHECKED_CAST")
@Composable
internal fun FieldsForm(fields: List<Field>, values: Map<String, Any?>, kind: PieceKind, onChange: (String, Any?) -> Unit) {
    fields.forEach { f ->
        val label = com.bamnat.core.recipe.RecipeWords.KIND_KEYS["${kind.key}.${f.key}"] ?: KEY_LABELS[f.key] ?: f.key
        val v = values[f.key]
        when (f) {
            is Field.Text -> OutlinedTextField((v as? String).orEmpty(), { t -> onChange(f.key, t.take(f.max).ifEmpty { if (f.optional) null else t }) },
                label = { Text(label) }, singleLine = !f.lines, minLines = if (f.lines) 3 else 1, modifier = Modifier.fillMaxWidth())
            is Field.Num -> NumberRow(label, (v as? Number)?.toDouble(), f.decimals, f.lo, f.hi) { d -> onChange(f.key, d?.let { if (f.decimals == 0) it.toLong() else it }) }
            // bc7e2d43 PC 확인 LOW-5 ('모두에게 같은 금액'이라 적혔는데 올리기 1/4에서는 받는 칸): an amount here is mine for now; on
            // upload it becomes the receiver's field first, and 1/4 is where it can be kept the same for everyone.
            is Field.Money -> Text(if (v is Ref.Input || v == null) "$label: 받는 분이 직접 넣어요"
                else "$label: 지금 ${com.bamnat.app.ui.Fmt.won((v as? Number)?.toLong() ?: 0)} · 올리면 받는 분이 정하는 칸이 돼요(올리기 1/4에서 바꿔요)",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            is Field.Color -> ColorSwatch((v as? Long) ?: 0xFF5C6BC0L, label + if (v is Ref) " (테마·받는 분 색)" else "", { c -> onChange(f.key, c) })
            is Field.Emoji -> {
                var pick by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { pick = true }) { Text("$label: ${(v as? String) ?: "없음"}") }
                if (pick) EmojiPickerDialog(onPick = { e -> onChange(f.key, e); pick = false }, onDismiss = { pick = false }, onClear = { onChange(f.key, null); pick = false })
            }
            is Field.Flag -> Row(Modifier.fillMaxWidth().heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(label, Modifier.weight(1f))
                Switch(v == true, { on -> onChange(f.key, on) })
            }
            is Field.Pick -> {
                Text(label, style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.values.forEach { p -> FilterChip(v == p, { onChange(f.key, if (v == p && f.optional) null else p) }, label = { Text(PICK_LABELS[p] ?: p) }) }
                }
            }
            is Field.Clock -> OutlinedTextField((v as? String).orEmpty(), { t ->
                val clean = t.filter { it.isDigit() || it == ':' }.take(5)
                onChange(f.key, clean.takeIf { Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$").matches(it) } ?: if (clean.isEmpty() && f.optional) null else v)
            }, label = { Text("$label (예: 09:00)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            is Field.Texts -> TextsEditor(label, (v as? List<String>).orEmpty(), f.max, f.itemMax) { onChange(f.key, it) }
            is Field.Nums -> {
                var text by remember(kind, f.key) { mutableStateOf((v as? List<Number>).orEmpty().joinToString(", ") { n -> n.toDouble().let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() } }) }
                OutlinedTextField(text, { t ->
                    text = t
                    val list = t.split(',', ' ').mapNotNull { it.trim().toDoubleOrNull() }.filter { it in f.lo..f.hi }.take(f.max)
                    onChange(f.key, list.map { if (f.decimals == 0) it.toLong() else it })
                }, label = { Text("$label (쉼표로 나눠요)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
            is Field.Days -> {
                Text(label, style = MaterialTheme.typography.labelLarge)
                val on = (v as? List<String>).orEmpty()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    WEEK_DAYS.forEach { d -> FilterChip(d in on, { onChange(f.key, WEEK_DAYS.filter { x -> if (x == d) d !in on else x in on }) }, label = { Text(PICK_LABELS.getValue(d)) }) }
                }
            }
            is Field.Items -> ItemsEditor(label, f, (v as? List<Map<String, Any?>>).orEmpty(), kind) { onChange(f.key, it) }
        }
    }
}

@Composable
private fun NumberRow(label: String, value: Double?, decimals: Int, lo: Double, hi: Double, onChange: (Double?) -> Unit) {
    var text by remember(label) { mutableStateOf(value?.let { if (decimals == 0) it.toLong().toString() else it.toString() }.orEmpty()) }
    OutlinedTextField(text, { t ->
        text = t.filter { it.isDigit() || (decimals > 0 && it == '.') }.take(8)
        onChange(text.toDoubleOrNull()?.coerceIn(lo, hi))
    }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth(),
        supportingText = { Text("${fmt(lo)}~${fmt(hi)}") },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal))
}

private fun fmt(d: Double) = if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString()

@Composable
private fun TextsEditor(label: String, list: List<String>, max: Int, itemMax: Int, onChange: (List<String>) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    // fc374a23 PC 확인 (새 분류 이름을 치는 동안 키보드가 '더하기'를 가림): typing in a row keeps 더하기 in sight above the keyboard,
    // and the keyboard's 다음 on the last row adds the next one.
    val addSeen = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    list.forEachIndexed { i, t ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(t, { s -> onChange(list.toMutableList().apply { set(i, s.take(itemMax)) }) }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Next),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onNext = {
                    if (i == list.lastIndex && t.isNotBlank() && list.size < max) onChange(list + "") else defaultKeyboardAction(androidx.compose.ui.text.input.ImeAction.Next)
                }),
                modifier = Modifier.weight(1f).onFocusEvent { e -> if (e.isFocused) scope.launch { kotlinx.coroutines.delay(300); addSeen.bringIntoView() } })
            IconButton(onClick = { onChange(list.toMutableList().apply { removeAt(i) }) }) { Icon(Icons.Filled.Close, "$label ${i + 1}번 빼기") }
        }
    }
    if (list.size < max) TextButton(onClick = { onChange(list + "") }, modifier = Modifier.bringIntoViewRequester(addSeen)) { Icon(Icons.Filled.Add, null); Text(" 더하기") }
}

@Suppress("UNCHECKED_CAST")
@Composable
private fun ItemsEditor(label: String, f: Field.Items, list: List<Map<String, Any?>>, kind: PieceKind, onChange: (List<Map<String, Any?>>) -> Unit) {
    Text(label, style = MaterialTheme.typography.labelLarge)
    list.forEachIndexed { i, item ->
        OutlinedCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(((item["name"] ?: item["title"] ?: item["text"] ?: item["category"] ?: item["role"]?.let { PICK_LABELS[it] }) as? String) ?: "${i + 1}번",
                        style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (list.size > f.min) IconButton(onClick = { onChange(list.toMutableList().apply { removeAt(i) }) }) { Icon(Icons.Filled.Close, "${i + 1}번 빼기") }
                }
                FieldsForm(f.fields.filter(::studioField), item, kind) { k, v -> onChange(list.toMutableList().apply { set(i, (item + (k to v)).filterValues { x -> x != null }) }) }
            }
        }
    }
    if (list.size < f.max) TextButton(onClick = { onChange(list + newItem(f, list.size + 1)) }) { Icon(Icons.Filled.Add, null); Text(" 더하기") }
}

/** A new row of [f]: what each field needs to be valid, so it can be filled in from there. */
/** [n] numbers the default name, so two new rows never share one (F-55). */
private fun newItem(f: Field.Items, n: Int = 1): Map<String, Any?> = f.fields.mapNotNull { x ->
    when (x) {
        is Field.Text -> if (x.optional) null else x.key to if (x.min > 0) (if (x.key == "label") (if (x.max >= 4) "칸 $n" else "새") else if (x.max >= 5) "새로 $n" else "새로") else ""
        is Field.Pick -> if (x.optional) null else x.key to x.values.first()
        is Field.Color -> if (x.optional) null else x.key to 0xFF5C6BC0L
        is Field.Nums -> x.key to listOf(60L)
        is Field.Num -> if (x.optional) null else x.key to x.lo.coerceAtLeast(1.0).let { if (x.decimals == 0) it.toLong() else it }
        is Field.Clock -> if (x.optional) null else x.key to "18:00"
        is Field.Texts -> x.key to emptyList<String>()
        else -> null
    }
}.toMap()

/**
 * [STU-04] What a piece starts with in the studio when this phone has none of it: example values that pass the catalog, close to
 * what that kind of work usually has, so turning a piece on shows something on the example app at once.
 */
internal object StudioStarters {
    fun of(kind: PieceKind, style: WorkStyle?): Map<String, Any?> = when (kind) {
        PieceKind.SHIFT_LOOK -> mapOf("items" to exampleDays(style).map { it + ("emoji" to when (it["role"]) { "night" -> "🌙"; "off" -> "🌿"; else -> "☀️" }) })
        PieceKind.CAL_THEME -> mapOf("cell" to "tint", "sun" to 0xFFC62828L, "sat" to 0xFF1565C0L)
        // [BLU-06] 1.36 부품 꾸미기: a bold month name and a rounded 오늘, so turning it on shows at once.
        PieceKind.PARTS -> mapOf("parts" to listOf(mapOf("id" to "cal.head.title", "weight" to 800L), mapOf("id" to "cal.cell.date", "on" to "today", "r" to 6L)))
        // [RUL-06] 1.36 수식·규칙·버튼: one button that opens today's memo with a template, and one rule for night work days.
        PieceKind.LOGIC -> mapOf(
            "buttons" to listOf(mapOf("label" to "오늘 기록", "emoji" to "📝", "where" to "day", "do" to "memoTpl", "arg" to "{근무} 인수인계\n- ")),
            // 1.38 PC 11차 식당 둘러보기: 야간 for 교대 only; other work gets a rule on any work day.
            "rules" to listOf(if (style == null || style == WorkStyle.SHIFT)
                mapOf("name" to "야간 전 알림", "on" to "start", "min" to -60L, "role" to "night", "do" to "notify", "arg" to "{근무} 한 시간 전이에요")
            else mapOf("name" to "시작 전 알림", "on" to "start", "min" to -60L, "do" to "notify", "arg" to "{근무} 한 시간 전이에요")),
        )
        PieceKind.ALARMS -> mapOf("byRole" to listOf(mapOf("role" to "day", "before" to listOf(60L))), "snooze" to 5L)
        PieceKind.MEMO_TEMPLATES -> mapOf("items" to listOf(mapOf("title" to "하루 기록", "emoji" to "📝",
            "asks" to listOf(mapOf("label" to "기분", "type" to "star"), mapOf("label" to "한 줄", "type" to "text")))))
        PieceKind.HOURLY_RULES -> mapOf("weeklyPay" to true, "legalBreaks" to true)
        PieceKind.FIELD_SHEET -> mapOf("mode" to "DAY_RATE", "chips" to listOf(0.5, 1.0, 1.5), "kinds" to listOf("철거", "설치"), "costs" to listOf("자재", "식대"))
        PieceKind.SHOP -> mapOf("open" to WEEK_DAYS.take(6), "opens" to "10:00", "closes" to "21:00", "closing" to listOf("시재 확인", "카드 정산"))
        PieceKind.INVEST -> mapOf("rules" to listOf(mapOf("text" to "한 종목에 자산의 20%를 넘기지 않아요")), "journal" to listOf("산 이유", "팔 기준"))
        PieceKind.LAYOUT -> mapOf("tabs" to TabOrder.DEFAULT)
        PieceKind.CARDS -> mapOf("stats" to "")
        PieceKind.WIDGET -> mapOf("alpha" to 90L, "theme" to "system", "memo" to true)
        PieceKind.PHOTO_LOOK -> mapOf("corners" to "round")
        PieceKind.CAL_VIEW -> valuesOf(com.bamnat.core.recipe.CalendarView.PRESETS.first { it.first == "메모 중심" }.second, emptyMap())
        PieceKind.APP_FEEL -> mapOf("theme" to "system", "hold" to "menu")
        // [FREE-04][FREE-05][FREE-06]
        PieceKind.MEMO_LOOK -> mapOf("layout" to "sticky", "card" to "own", "corner" to 12L, "lines" to 4L)
        PieceKind.MONEY_LOOK -> mapOf("rows" to "roomy", "amount" to "l", "dayHead" to "band")
        PieceKind.SCREEN_LOOK -> mapOf("screens" to com.bamnat.core.recipe.ScreenLooks().with(
            com.bamnat.core.recipe.ScreenLooks.ALL, com.bamnat.core.recipe.ScreenLook(bg = 0xFFFFF8EEL, card = 0xFFFFFFFFL, accent = 0xFFE07A5FL),
        ).encode())
        PieceKind.COMPANY, PieceKind.BUNDLE -> emptyMap()
        // [LAY-01] A star on every 25th, so turning 꾸밈 층 on shows something at once.
        PieceKind.DECOR -> com.bamnat.core.recipe.Decor(listOf(com.bamnat.core.recipe.DecorItem(com.bamnat.core.recipe.DecorKind.SHAPE, "star5",
            com.bamnat.core.recipe.Decor.CELL, on = "d25", x = 820, y = 180, w = 12, h = 12, fill = 0xFFFFB300))).values()
        // [TAB-07] 1.36 탭 보기: a 근무 띠 calendar and a 보드 메모장 to start from.
        PieceKind.TAB_VIEW -> mapOf("cal" to "rota", "memo" to "board", "memoBy" to "tag", "money" to "board")
        // 1.37 [LEDH-18] 가계부 틀: a 야간 근무자's start.
        PieceKind.LEDGER -> mapOf("costs" to listOf("야식", "편의점", "택시"), "budgets" to listOf(mapOf("category" to "야식", "amount" to 100_000L)),
            "envelopes" to listOf(mapOf("name" to "비상금", "target" to 1_000_000L, "emoji" to "🛟")))
    }
}

/** Whether a card belongs on this kind of work's screen, as on the phone ([com.bamnat.core.guide.FitTable]). */
internal fun fitsHere(surface: com.bamnat.core.guide.FitSurface, id: String, style: WorkStyle?): Boolean =
    // PC 확인 1.37 A6 (첫 설정을 미룬 달력에 한 카드가 예시에도, 숨긴 기능에도): no kind of work yet fits as on the phone, not 'everything'.
    com.bamnat.core.guide.FitTable.visible(surface, id, style, emptySet())


/** PC 확인 Z5: this month's work days and scheduled hours on the phone's own calendar (0 and 0 with none), or null while it loads. */
@Composable
private fun rememberMonthWork(): Pair<Int, Int>? {
    val own = LocalOwnMonth.current
    val realOn = com.bamnat.app.ui.studio.LocalRealLook.current
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val snapFlow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    return remember(snap, realOn) {
        val sn = snap?.takeIf { realOn } ?: return@remember null
        val month = java.time.YearMonth.from(com.bamnat.core.time.Today.date())
        val days = sn.resolver.resolveRange(month.atDay(1), month.atEndOfMonth()).filter { it.isWorkDay }
        // PC 확인 V19 1.36 (공방 카드에 예시 '18일 · 162h'): the 공방 card is my own month even with no work yet (0일 · 0h); the studio
        // and 입어 보기 keep the whole example then, so its numbers and days match.
        if (days.isEmpty() && !own) null else days.size to days.sumOf { it.scheduledMinutes } / 60
    }
}


/** PC 확인 Z13: how many days of this month are each of [days]' kinds (the same name, else the same role), or null with no work yet. */
@Composable
private fun rememberMonthKinds(days: List<Map<String, Any?>>): List<Int>? {
    val own = LocalOwnMonth.current
    val realOn = com.bamnat.app.ui.studio.LocalRealLook.current
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val snapFlow = remember(c) { c.scopeOf(com.bamnat.app.data.PersonalCalendar.MAIN).schedule.snapshot }
    val snap by snapFlow.collectAsStateWithLifecycle(null)
    return remember(snap, days, realOn) {
        val sn = snap?.takeIf { realOn } ?: return@remember null
        val month = java.time.YearMonth.from(com.bamnat.core.time.Today.date())
        val resolved = sn.resolver.resolveRange(month.atDay(1), month.atEndOfMonth())
        if (resolved.none { it.isWorkDay }) return@remember if (own) days.map { 0 } else null
        val kinds = resolved.mapNotNull { r -> r.shiftType?.let { t ->
            t.name.trim() to com.bamnat.core.recipe.ShiftRole.guess(t.name, t.isOff, t.start?.hour, if (t.isOff) 0 else t.durationMinutes).key } }
        days.map { d ->
            val name = (d["name"] as? String)?.trim()
            val byName = kinds.count { it.first == name }
            if (byName > 0) byName else kinds.count { it.second == d["role"] && days.none { o -> (o["name"] as? String)?.trim() == it.first } }
        }
    }
}
