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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = s.onClick, enabled = s.enabled, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(s.label, textAlign = TextAlign.Center)
                                s.sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, textAlign = TextAlign.Center) }
                            }
                        }
                        s.other?.let { (words, go) -> OutlinedButton(onClick = go, modifier = Modifier.heightIn(min = 52.dp)) { Text(words) } }
                    }
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
