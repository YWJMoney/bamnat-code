package com.bamnat.app.ui.recipe

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.ui.components.FilterChip
import com.bamnat.app.ui.theme.bamnat
import com.bamnat.app.ui.village.MakerBadge
import com.bamnat.app.ui.village.markColor
import com.bamnat.core.plaza.MakerMark
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch

/**
 * 1.39 [MKD-41] 만든 분 (사용자 2026-11-07 "장터 누가 만들었는지 팔로우는 몇인지", "유명한 제작자나 이달의 제작자 … 특별한 표식", "프로필까지 타고"):
 * 이름 · 표식 · 팔로워 수, 누르면 프로필. 표식은 광장 갈래 [MakerMark]·[MakerBadge]·[markColor]를 그대로 써서 광장과 장터가 같은 모양이에요(PLZC-40).
 */
object MarketMaker {
    /** Who made it, never "이름 없음": 밤낮's own works say 밤낮. */
    fun name(item: RecipeItem): String = item.nick?.takeIf { it.isNotBlank() } ?: if (item.official) "밤낮" else "이름 없는 만든 분"

    /** The mark (광장 갈래 [MakerMark]: 🏅 이달의 제작자, ⭐ 인기 제작자), or null (none, or a key this app does not know yet). */
    fun mark(key: String?): MakerMark? = MakerMark.of(key)

    /** "팔로워 1.2천" — short, as a card has room for. */
    fun followers(n: Int?): String? = n?.takeIf { it > 0 }?.let { "팔로워 " + count(it) }

    fun count(n: Int): String = when {
        n >= 10_000 -> "%.1f만".format(n / 10_000.0).replace(".0만", "만")
        n >= 1_000 -> "%.1f천".format(n / 1_000.0).replace(".0천", "천")
        else -> n.toString()
    }

    /** One line for a card: "만든 분 이름 🏅 · 팔로워 120". */
    fun line(item: RecipeItem): String = "만든 분 " + name(item) + (mark(item.mark)?.let { " " + it.badge } ?: "") +
        (followers(item.followers)?.let { " · $it" } ?: "")
}

/**
 * 1.39 [MKD-43] 넣기 단추의 한마디, 작품 종류대로 (PC 4차 c0ca3d9f: 부품 상세에도 '이 테마 입기'): 테마는 입기, 탭별 작품·도구는 넣기.
 * [plain] 자세히 보기의 단추처럼 '그대로'를 붙일 때.
 */
internal fun wholeWords(scope: String?, part: Boolean, plain: Boolean = false): String = when {
    part -> "이 부품 넣기"
    scope == "theme" || scope == null -> "이 테마 입기"
    else -> if (plain) "이 작품 그대로 넣기" else "이 작품 넣기"
}

/**
 * 1.39 [MKD-41] 상세 맨 위의 만든 분 줄: 얼굴(첫 글자) · 이름 · 표식 · 팔로워, 누르면 프로필 (만든 분 쪽 아이디가 있을 때만).
 */
@Composable
internal fun MakerLine(item: RecipeItem, onMaker: ((String) -> Unit)?) {
    val name = MarketMaker.name(item)
    val mark = MarketMaker.mark(item.mark)
    val go = item.maker?.takeIf { onMaker != null }
    val tone = markColor(mark)
    Row(
        Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
            .then(if (go != null) Modifier.clickable(role = Role.Button) { onMaker?.invoke(go) } else Modifier)
            .heightIn(min = 48.dp).padding(vertical = 4.dp)
            .semantics(mergeDescendants = true) { if (go != null) contentDescription = "$name 프로필 · 다른 작품 보기" },
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(if (mark != null) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center) {
            Text(if (item.official && item.nick == null) "🌗" else name.take(1), style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = tone, maxLines = 1,
                    overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                mark?.let { m ->
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = MaterialTheme.shapes.small) {
                        MakerBadge(m, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
            val sub = listOfNotNull(MarketMaker.followers(item.followers), if (go != null) "프로필 · 다른 작품" else null).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (go != null) Text("›", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 1.39 [MKD-42] 상세 맨 위 (V4, 사용자 2026-11-07 "실제 맛은 미리보기와 입어보기 예시 사용들으로"): 작품 실제 모습을 크게, 좌우로 밀면 그 작품이
 * 바꾸는 다른 화면(달력 → 가계부 → 메모 …, [MarketShots.faces]). 그림을 누르면 [onTap] (예시로 보기).
 */
@Composable
internal fun WorkHero(item: RecipeItem, r: Recipe, style: WorkStyle?, onTap: () -> Unit) {
    val shown = style?.takeIf { it in item.styles || item.styles.isEmpty() } ?: item.styles.firstOrNull() ?: style
    val faces = remember(r, shown) { MarketShots.faces(item, r, shown) }
    val pager = rememberPagerState { faces.size.coerceAtLeast(1) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (faces.size > 1) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            faces.forEachIndexed { i, f ->
                FilterChip(selected = pager.currentPage == i, onClick = { scope.launch { pager.animateScrollToPage(i) } },
                    label = { Text(MarketShots.faceName(f, shown)) })
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val w = min(maxWidth * 0.66f, 280.dp)
            val h = w * (MarketShots.PHONE_H / MarketShots.PHONE_W)
            if (faces.size <= 1) WorkFace(r, faces.firstOrNull() ?: MarketShots.faceOf(item, r), w, shown, onClick = onTap)
            else HorizontalPager(pager, Modifier.fillMaxWidth().heightIn(min = h), key = { it }) { p ->
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { WorkFace(r, faces[p], w, shown, onClick = onTap) }
            }
        }
        if (faces.size > 1) Row(Modifier.semantics { contentDescription = "${faces.size}개 화면 가운데 ${pager.currentPage + 1}번째" },
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            faces.indices.forEach { i ->
                Box(Modifier.size(if (i == pager.currentPage) 8.dp else 6.dp).clip(CircleShape)
                    .background(if (i == pager.currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant))
            }
        }
    }
}

/** 1.39 [MKD-42] 설명은 두 줄까지, 더 있으면 '더 보기'. */
@Composable
internal fun ShortDesc(text: String) {
    if (text.isBlank()) return
    var open by remember(text) { mutableStateOf(false) }
    var long by remember(text) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Text(text, style = MaterialTheme.typography.bodyMedium, maxLines = if (open) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis,
            onTextLayout = { if (!open) long = it.hasVisualOverflow })
        if (long || open) TextButton(onClick = { open = !open }, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 0.dp)) {
            Text(if (open) "접기" else "더 보기")
        }
    }
}

/** One step of 넣기 전에 해 보기. [main] is the last (넣기), drawn as the big button; [done] puts ✓ where the number was. */
internal data class LadderStep(
    val label: String, val sub: String?, val done: Boolean = false, val main: Boolean = false, val enabled: Boolean = true,
    val action: String? = null,
    /** A second, smaller way (되돌리기 on a work put in). */
    val other: Pair<String, () -> Unit>? = null,
    val onClick: () -> Unit,
)

/**
 * 1.39 [MKD-43] 넣기 전에 해 보기 사다리 (V5·V6, 사용자 2026-11-07 "사용해보기전에 완벽하게 미리 사용해볼수 있는 시스템"): 흩어져 있던
 * 입어 보고 고르기 · 실제로 써 보기 · 하루 써 보기 · 넣기를 한 칸에, 해 볼 순서대로. 이름은 입혀 보기·FX 갈래와 맞췄어요:
 * 예시로 보기(그림) → 내 앱에 입혀 보기(실제 화면, 저장 안 됨) → 하루 써 보기(✦, 진짜로 넣음) → 넣기. 해 본 칸에는 ✓.
 */
@Composable
internal fun TryLadder(steps: List<LadderStep>, note: (@Composable () -> Unit)? = null) {
    if (steps.isEmpty()) return
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("넣기 전에 해 보기", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            steps.forEachIndexed { i, s ->
                if (s.main) {
                    note?.invoke()
                    PutRow(s)
                    return@forEachIndexed
                }
                Row(
                    Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium).clickable(enabled = s.enabled, role = Role.Button, onClick = s.onClick)
                        .heightIn(min = 56.dp).padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(Modifier.size(28.dp).clip(CircleShape).background(if (s.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center) {
                        Text(if (s.done) "✓" else "${i + 1}", style = MaterialTheme.typography.labelLarge,
                            color = if (s.done) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(s.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium,
                            color = if (s.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                        s.sub?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(s.action ?: "›", style = if (s.action == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge,
                        color = if (s.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The 넣기 step: the one filled button (its words and a line under), and a smaller second way (되돌리기) beside it. */
@Composable
internal fun PutRow(s: LadderStep) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = s.onClick, enabled = s.enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(s.label, textAlign = TextAlign.Center)
                s.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
            }
        }
        s.other?.let { (words, go) -> OutlinedButton(onClick = go, modifier = Modifier.heightIn(min = 52.dp)) { Text(words) } }
    }
}

/**
 * [RVM-13] PC-59·60·34 (넣기 창 '이렇게 바뀌어요'가 두 화면 넘는 만든 사람 말투 목록, 알람이 더 울리는 것은 맨 아래에만, 프로가 아니면
 * 단추가 쉰다는 말이 없음): what 넣기 changes, at most four short lines in the user's words, the full list folded under it.
 */
internal data class PutLine(val text: String, val kind: Kind) {
    enum class Kind { LOOK, LETTERS, LETTERS_KEPT, FX, REST, ALARM, ALARM_OFF, ALARM_WEAK, ALARM_WEAK_ON, WRITES, TAPS, OTHER }

    /** 2.0.0 [RVM-23] U09b-01 A 조심 줄: what the work does that one would not see coming, said first, with the caution mark. */
    val caution: Boolean get() = kind in CAUTION

    companion object {
        val CAUTION = setOf(Kind.ALARM_WEAK, Kind.ALARM_WEAK_ON, Kind.WRITES, Kind.TAPS)
    }
}

/** 2.0.0 [RVM-23] U09b-01 What steps that write are written in, as the 조심 줄 says it: "가계부", "메모", "가계부·메모". */
private fun writesTo(acts: List<com.bamnat.core.formula.LogicAct>): String {
    val money = setOf(com.bamnat.core.formula.LogicVerb.ADD_MONEY, com.bamnat.core.formula.LogicVerb.ADD_INCOME, com.bamnat.core.formula.LogicVerb.ENVELOPE)
    val w = acts.filter { it.verb.writes }
    return listOfNotNull("가계부".takeIf { w.any { it.verb in money } }, "메모".takeIf { w.any { it.verb !in money } }).joinToString("·")
}

internal fun putBrief(s: RecipeDetailViewModel.State, fxOn: Boolean): List<PutLine> {
    val kinds = com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS
    val on = s.pieces.filter { it.on && it.fits }
    val out = mutableListOf<PutLine>()
    val tabs = s.lookParts?.mapNotNull { com.bamnat.core.recipe.LookParts.byId(it) }?.map { com.bamnat.core.recipe.LookParts.tabLabel(it.tab) }?.distinct()
        ?: on.filter { it.kind in kinds }.map { it.kind.label }
    if (tabs.isNotEmpty()) out += PutLine("모양이 바뀌어요 · " + tabs.take(3).joinToString(", ") + if (tabs.size > 3) " 등" else "", PutLine.Kind.LOOK)
    // 2.0.0 [PC-33] A 칸 디자인 that shows shifts by colour alone (점만·선·칸 색) is said before it goes in, with '글자 남기기' (조건 14).
    val cells = on.any { it.kind == com.bamnat.core.recipe.PieceKind.CAL_VIEW } && (s.lookParts == null || "cal.cells" in s.lookParts)
    val colourOnly = s.recipe?.takeIf { cells }?.let { com.bamnat.core.recipe.CellLetters.colourOnly(it) }
    if (colourOnly != null) out += if (s.letters) PutLine("칸에 근무 글자를 남겨요", PutLine.Kind.LETTERS_KEPT)
        else PutLine(com.bamnat.core.recipe.CellLetters.said(colourOnly), PutLine.Kind.LETTERS)
    val logic = s.recipe?.pieces?.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.LOGIC }
        ?.takeIf { on.any { p -> p.kind == com.bamnat.core.recipe.PieceKind.LOGIC } }
        ?.let { com.bamnat.core.formula.Logic.from(it.values) }
    if (logic != null && (logic.buttons.isNotEmpty() || logic.rules.isNotEmpty())) {
        val what = listOfNotNull(logic.buttons.size.takeIf { it > 0 }?.let { "단추 ${it}개" }, logic.rules.size.takeIf { it > 0 }?.let { "자동 규칙 ${it}개" })
        out += PutLine(what.joinToString(" · ") + "가 생겨요", PutLine.Kind.FX)
        val restB = logic.buttons.count { com.bamnat.core.formula.FxPro.parts(logic, it).isNotEmpty() }
        val restR = logic.rules.count { com.bamnat.core.formula.FxPro.parts(logic, it).isNotEmpty() }
        if (!fxOn && restB + restR > 0) {
            val who = listOfNotNull(restB.takeIf { it > 0 }?.let { "단추 ${it}개" }, restR.takeIf { it > 0 }?.let { "규칙 ${it}개" }).joinToString("·")
            out += PutLine("${who}는 프로에서 켜져요. 모양은 그대로 입어요", PutLine.Kind.REST)
        }
    }
    val alarms = s.pieces.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.ALARMS && it.fits }
    if (alarms != null) {
        val added = alarms.lines.filter { !it.skipped && it.text.endsWith(" 알람 더하기") }.map { it.text.removeSuffix(" 알람 더하기") }
        // 2.0.0 [RVM-22] RCP-05: the way it rings (진동·점점 크게·울림 길이·다시 울림·미션) is said even when alarms are added too.
        val how = alarms.lines.any { !it.skipped && !it.text.endsWith(" 알람 더하기") }
        when {
            !alarms.on -> out += PutLine("알람은 빼고 넣어요", PutLine.Kind.ALARM_OFF)
            added.isNotEmpty() -> out += PutLine("알람이 더 울려요 · " + added.joinToString(", ").let { if (it.length > 32) it.take(31) + "…" else it } +
                (if (how) " · 울리는 방식도 바뀌어요" else ""), PutLine.Kind.ALARM)
            how -> out += PutLine("알람 울리는 방식이 바뀌어요", PutLine.Kind.ALARM)
        }
        // 2.0.0 [RVM-22] RCP-05 (받은 작품 하나로 알람이 약해져 못 깨는 사고): what would make the alarm weaker stays out unless chosen.
        if (alarms.on && s.alarmWeak.isNotEmpty()) {
            val w = s.alarmWeak.joinToString(", ").let { if (it.length > 32) it.take(31) + "…" else it }
            out += if (s.alarmWeaker) PutLine("알람이 약해져요 · $w", PutLine.Kind.ALARM_WEAK_ON)
                else PutLine("알람을 약하게 하는 설정은 빼고 넣어요 · $w", PutLine.Kind.ALARM_WEAK)
        }
    }
    val rest = on.filter { it.kind !in kinds && it.kind != com.bamnat.core.recipe.PieceKind.LOGIC && it.kind != com.bamnat.core.recipe.PieceKind.ALARMS }
        .map { it.kind.label }
    if (rest.isNotEmpty()) out += PutLine("그 밖에 " + rest.take(3).joinToString(" · ") + if (rest.size > 3) " 등" else "", PutLine.Kind.OTHER)
    // 2.0.0 [RVM-23] U09b-01 (넣기 창 맨 위에 '조심 줄'을 따로, RCP-05·17·STU-23): what one would not see coming goes first — the alarm made
    // weaker, a 단추 or 규칙 that writes 가계부·메모 (any step, not the first alone), a 꾸밈 that presses a 단추 when the calendar is touched.
    if (logic != null) {
        val bw = logic.buttons.filter { b -> b.acts.any { it.verb.writes } }
        val rw = logic.rules.filter { r -> r.acts.any { it.verb.writes } }
        val said = listOfNotNull(
            bw.takeIf { it.isNotEmpty() }?.let { "누르면 ${writesTo(it.flatMap { b -> b.acts })}에 적는 단추 ${it.size}개" },
            rw.takeIf { it.isNotEmpty() }?.let { "${writesTo(it.flatMap { r -> r.acts })}에 적는 규칙 ${it.size}개는 꺼진 채로 들어와요" },
        )
        if (said.isNotEmpty()) out += PutLine(said.joinToString(" · "), PutLine.Kind.WRITES)
        val decor = s.recipe?.pieces?.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.DECOR }
            ?.takeIf { on.any { p -> p.kind == com.bamnat.core.recipe.PieceKind.DECOR } }
        val taps = decor?.let { com.bamnat.core.recipe.DecorTapRule.taps(com.bamnat.core.recipe.Decor.of(it.values).items) }
            ?.filter { t -> t.tap?.let(logic::button) != null }.orEmpty()
        if (taps.isNotEmpty()) out += PutLine(
            (if (taps.any { it.at == com.bamnat.core.recipe.Decor.CELL }) "달력 칸의 꾸밈을 누르면 단추가 눌려요" else "꾸밈을 누르면 단추가 눌려요") + " · ${taps.size}개",
            PutLine.Kind.TAPS)
    }
    return out.filter { it.caution } + out.filterNot { it.caution }
}

/**
 * [RVM-13] The brief at the top of the 넣기 창. A line with a choice (알람, 칸의 근무 글자) carries it as a switch row under its words, the
 * whole row pressed (48dp): side-by-side 글자 단추 squeezed the words at 200% into one a line, and with '바뀌는 것 모두 보기' and the 창's
 * own 닫기·넣기 they were more 글자 단추 than 조건 2 lets a screen have (갈래 J RVJ-17 did the same).
 */
@Composable
internal fun PutBrief(lines: List<PutLine>, onLetters: (Boolean) -> Unit = {}, onWeaker: (Boolean) -> Unit = {}, onAlarms: (Boolean) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { l ->
            // [RVM-23] U09b-01: a 조심 줄 has the caution mark at its left (the words stay the page's colour, 조건 3·6·14).
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                if (l.caution) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = "조심", tint = MaterialTheme.bamnat.caution,
                        modifier = Modifier.padding(top = 2.dp, end = 8.dp).size(18.dp))
                }
                Text(
                    l.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f),
                    color = when (l.kind) {
                        PutLine.Kind.REST -> MaterialTheme.colorScheme.tertiary
                        PutLine.Kind.ALARM_OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                )
            }
            val choice: Triple<String, Boolean, (Boolean) -> Unit>? = when (l.kind) {
                PutLine.Kind.ALARM, PutLine.Kind.ALARM_OFF -> Triple("알람도 넣기", l.kind == PutLine.Kind.ALARM, onAlarms)
                PutLine.Kind.LETTERS, PutLine.Kind.LETTERS_KEPT -> Triple("칸에 근무 글자 남기기", l.kind == PutLine.Kind.LETTERS_KEPT, onLetters)
                PutLine.Kind.ALARM_WEAK, PutLine.Kind.ALARM_WEAK_ON -> Triple("약하게 하는 설정도 넣기", l.kind == PutLine.Kind.ALARM_WEAK_ON, onWeaker)
                else -> null
            }
            choice?.let { (label, on, set) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .toggleable(value = on, role = Role.Switch, onValueChange = set).padding(start = 12.dp),
                ) {
                    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(checked = on, onCheckedChange = null)
                }
            }
        }
    }
}
