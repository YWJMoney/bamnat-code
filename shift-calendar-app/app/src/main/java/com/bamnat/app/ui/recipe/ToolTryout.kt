package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.appContainer
import com.bamnat.app.logic.rememberAppDayFacts
import com.bamnat.core.formula.ButtonPlace
import com.bamnat.core.formula.DayFactsBook
import com.bamnat.core.formula.FormulaName
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicButton
import com.bamnat.core.formula.LogicRule
import com.bamnat.core.formula.LogicTryout
import com.bamnat.core.layout.TabOrder
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate

/**
 * 1.39 [MKC-02][MKC-03] 도구 전후 해 보기 (사용자 2026-11-07 "도구 또한 분류를 나눠서 사용자가 자신의 테마나 설정값(기본 테마라면)에 정확히
 * 가져다가 쓸 수 있게 해당 도구를 누르면 적용 전후 모습을 직접 조작하면서 바뀐 점을 알 수 있게"): on a 도구's page, before anything is put in.
 * - 부품: my screen as it is now with the part laid on, a line to drag, 지금 · 반반 · 넣으면, and 누르고 있으면 넣기 전.
 * - 단추: the screen it sits on, 지금 (my buttons) ↔ 넣으면 (mine and the new ones); a new one pressed says what it would do on the day
 *   picked from my own calendar, worked out on my records ([LogicTryout.press]). Nothing is written.
 * - 자동 규칙: the 상황 made on a day of mine ([LogicTryout.rule]): whether it runs, when, and what it would do, beside what my own rules do
 *   then; the days of the next two weeks it would run by itself.
 * Where it goes is said first: on the theme worn, or on my own look.
 */
@Composable
internal fun ToolTryout(r: Recipe, style: WorkStyle?, modifier: Modifier = Modifier) {
    val c = LocalContext.current.appContainer
    val logic = remember(r) { r.pieces.filter { it.kind == PieceKind.LOGIC }.fold(Logic()) { a, p -> a.plus(Logic.from(p.values)) }.received() }
    val looks = remember(r) { r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS } }
    if (logic.isEmpty && looks.isEmpty()) return
    val lease by c.wardrobe.lease.collectAsStateWithLifecycle()
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
        .padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("전후 해 보기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
        Text(lease.worn?.title?.let { "지금 입은 ‘$it’ 위에 이것만 더해요. 다른 화면과 기록은 그대로예요" }
            ?: "지금 내 모양(기본 테마) 위에 이것만 더해요. 다른 화면과 기록은 그대로예요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (looks.isNotEmpty()) PartTryout(r.copy(pieces = looks), style)
        if (!logic.isEmpty) LogicTryoutView(logic)
    }
}

/** 부품: my screen now and with the part on, side by side on one line I can drag. */
@Composable
private fun PartTryout(part: Recipe, style: WorkStyle?) {
    val c = LocalContext.current.appContainer
    val mine by produceState(Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = emptyList()), c) {
        value = Recipe("지금", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
    }
    val after = remember(mine, part) { LookParts.layer(mine, part) }
    val first = remember(part) { LookParts.of(part).firstOrNull()?.tab?.takeIf { it != LookParts.WIDGET } ?: TabOrder.CALENDAR }
    var tab by rememberSaveable(part) { mutableStateOf(first) }
    var split by remember { mutableFloatStateOf(0.5f) }
    var peek by remember { mutableStateOf(false) }
    val changed = remember(mine, after, tab) { LookParts.changed(mine, after).mapNotNull { LookParts.byId(it) }.filter { it.tab == tab }.map { it.label } }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0f to "지금", 0.5f to "반반", 1f to "넣으면").forEach { (v, label) ->
                com.bamnat.app.ui.components.FilterChip(selected = split == v, onClick = { split = v }, label = { Text(label) })
            }
        }
        // 누르고 있으면 넣기 전: a finger held on the picture shows my screen as it is, with what would change ringed.
        Box(Modifier.pointerInput(Unit) { detectTapGestures(onPress = { peek = true; tryAwaitRelease(); peek = false }) }
            .semantics { contentDescription = "부품 전후 그림. 누르고 있으면 넣기 전 모습"; stateDescription = if (peek) "넣기 전" else "넣으면 ${(split * 100).toInt()}%" }) {
            CompareCanvas(mine, after, style, tab, { tab = it }, scale = 0.62f, split = split, peek = peek, onSplit = { split = it })
        }
        Text(when {
            peek -> "넣기 전 지금 모습이에요. 바뀌는 곳에 테두리를 둘렀어요"
            split >= 1f -> "넣으면 이렇게 보여요"
            split <= 0f -> "지금 내 모습이에요"
            else -> "왼쪽이 넣은 모습, 오른쪽이 지금이에요. 가운데 선을 끌어 옮겨 봐요"
        } + (if (changed.isEmpty()) "" else ". 바뀌는 곳: " + changed.joinToString(" · ")) + ". 그림을 누르고 있으면 넣기 전 모습이에요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
    }
}

/** 단추·자동 규칙: tried on a day of mine, never done. */
@Composable
private fun LogicTryoutView(got: Logic) {
    val c = LocalContext.current.appContainer
    val facts = rememberAppDayFacts()
    val mine = remember { com.bamnat.app.logic.LogicRunner.logic(c) }
    val today = remember { Today.date() }
    val days = remember(today) { (0L..13L).map { today.plusDays(it) } }
    var day by rememberSaveable { mutableStateOf(today.toEpochDay()) }
    val date = LocalDate.ofEpochDay(day)
    if (facts == null) {
        Text("내 달력을 읽는 중이에요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    Text("어느 날로 해 볼까요", style = MaterialTheme.typography.labelLarge)
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        days.forEach { d ->
            com.bamnat.app.ui.components.FilterChip(selected = d == date, onClick = { day = d.toEpochDay() }, label = { Text(dayWords(d, facts, today)) })
        }
    }
    if (got.buttons.isNotEmpty()) ButtonTryout(got.buttons, mine.buttons, date, facts)
    if (got.rules.isNotEmpty()) RuleTryout(got.rules, mine.rules, date, days, facts) { day = it.toEpochDay() }
}

/** "오늘 야간", "11/9 일 휴무". */
private fun dayWords(d: LocalDate, facts: DayFactsBook, today: LocalDate): String {
    val v = facts.values(d)
    val role = (v[FormulaName.ROLE.ko] as? String).orEmpty()
    val date = when (d) { today -> "오늘"; today.plusDays(1) -> "내일"; else -> "${d.monthValue}/${d.dayOfMonth} ${"월화수목금토일"[d.dayOfWeek.value - 1]}" }
    return if (role.isEmpty()) date else "$date $role"
}

/** 단추: the screen it sits on, 지금 ↔ 넣으면, and the new ones to press. */
@Composable
private fun ButtonTryout(got: List<LogicButton>, mine: List<LogicButton>, date: LocalDate, facts: DayFactsBook) {
    var after by rememberSaveable { mutableStateOf(true) }
    var held by remember { mutableStateOf(false) }
    var pressed by rememberSaveable { mutableStateOf<String?>(null) }
    val showAfter = after && !held
    Text("단추 · 눌러 보기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        com.bamnat.app.ui.components.FilterChip(selected = !after, onClick = { after = false; pressed = null }, label = { Text("지금") })
        com.bamnat.app.ui.components.FilterChip(selected = after, onClick = { after = true }, label = { Text("넣으면") })
    }
    got.map { it.place }.distinct().forEach { place ->
        val mineHere = mine.filter { it.place == place }
        val gotHere = got.filter { it.place == place }
        // 예시 화면: where the buttons sit. 누르고 있으면 지금.
        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(14.dp))
            .pointerInput(Unit) { detectTapGestures(onLongPress = { held = true }, onPress = { tryAwaitRelease(); held = false }) }) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${placeWords(place)} · ${date.monthValue}월 ${date.dayOfMonth}일", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    mineHere.forEach { b ->
                        val t = LogicTryout.press(b, date, facts)
                        if (t.runs) androidx.compose.material3.AssistChip(onClick = {}, enabled = false,
                            label = { Text(listOfNotNull(b.emoji, b.label).joinToString(" ")) })
                    }
                    if (showAfter) gotHere.forEach { b ->
                        val t = LogicTryout.press(b, date, facts)
                        androidx.compose.material3.FilledTonalButton(onClick = { pressed = b.id }, enabled = t.runs, modifier = Modifier.heightIn(min = 40.dp)) {
                            Text("✨ " + listOfNotNull(b.emoji, b.label).joinToString(" "), fontWeight = FontWeight.Bold)
                        }
                    }
                    if (mineHere.isEmpty() && !showAfter) Text("단추 없음", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showAfter) gotHere.map { it to LogicTryout.press(it, date, facts) }.filter { !it.second.runs }.forEach { (b, t) ->
                    Text("‘${b.label}’: ${t.why}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                gotHere.firstOrNull { it.id == pressed }?.takeIf { showAfter }?.let { b ->
                    val t = LogicTryout.press(b, date, facts)
                    ResultLine(true, "‘${b.label}’${com.bamnat.app.ui.Fmt.obj(b.label)} 누르면 ${t.result}")
                }
            }
        }
    }
    Text(if (showAfter) "✨ 표시가 새로 생기는 단추예요. 눌러 보면 무엇을 하는지 보여요 (해 보기라 저장되지 않아요). 화면을 길게 누르면 지금 모습이에요"
        else "지금 내 화면이에요. ‘넣으면’을 눌러 새 단추를 봐요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun placeWords(p: ButtonPlace): String = when (p) {
    ButtonPlace.DAY -> "하루 화면"
    ButtonPlace.MEMO -> "메모장"
    ButtonPlace.CALENDAR -> "달력"
    ButtonPlace.MONEY -> "가계부"
    ButtonPlace.DECOR -> "꾸밈 그림을 누르면"
}

/** 자동 규칙: the 상황 made on the day, beside what my own rules do then. */
@Composable
private fun RuleTryout(got: List<LogicRule>, mine: List<LogicRule>, date: LocalDate, days: List<LocalDate>, facts: DayFactsBook, onDay: (LocalDate) -> Unit) {
    Text("자동 규칙 · 상황 만들어 보기", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    got.forEachIndexed { i, r ->
        var made by remember(r, date) { mutableStateOf(false) }
        Surface(shape = RoundedCornerShape(14.dp), tonalElevation = 2.dp, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(r.name, style = MaterialTheme.typography.titleSmall)
                Text(r.say(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (LogicTryout.timed(r)) {
                    val on = remember(r, days, facts) { LogicTryout.runsOn(r, days, facts) }
                    Text(if (on.isEmpty()) "앞으로 2주 동안은 돌 날이 없어요" else "앞으로 2주 동안 ${on.size}번 돌아요",
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    if (on.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        on.forEach { d -> androidx.compose.material3.SuggestionChip(onClick = { onDay(d); made = true },
                            label = { Text("${d.monthValue}/${d.dayOfMonth}") }) }
                    }
                }
                FilledTonalButton(onClick = { made = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text("${date.monthValue}/${date.dayOfMonth}에 " + LogicTryout.situation(r))
                }
                if (made) {
                    val t = LogicTryout.rule(r, date, facts)
                    // 지금: what my own rules on the same 상황 do that day.
                    val now = mine.filter { it.on == r.on }.map { it to LogicTryout.rule(it, date, facts) }.filter { it.second.runs }
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("지금", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(if (now.isEmpty()) "이때 아무 일도 없어요" else now.joinToString("\n") { (m, x) -> "‘${m.name}’: ${x.result}" },
                            style = MaterialTheme.typography.bodySmall, modifier = Modifier.alpha(0.75f))
                        Text("넣으면", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        ResultLine(t.runs, if (t.runs) "${t.why} ${t.result}" else t.why)
                    }
                }
            }
        }
        if (i == got.lastIndex) Text("해 보기는 저장하지 않아요. 넣은 뒤에는 스튜디오 › 규칙·단추에서 하나씩 끄고 켤 수 있어요",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ResultLine(ok: Boolean, text: String) {
    Text((if (ok) "✓ " else "✕ ") + text, style = MaterialTheme.typography.bodyMedium, fontWeight = if (ok) FontWeight.SemiBold else null,
        color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
}
