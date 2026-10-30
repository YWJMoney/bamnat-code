package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.ui.components.asColor
import com.bamnat.app.ui.components.contentColorOn
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.RecipeTokens
import com.bamnat.core.work.WorkStyle

/**
 * [RCX-18] 1.35 테마 색 in 만들기 › 모양: tie the recipe's colours to named colours, change one and every place follows.
 */
@Composable
internal fun ThemeTokens(r: Recipe, edit: ((Recipe) -> Recipe) -> Unit) {
    val hasColours = r.pieces.any { it.kind == PieceKind.SHIFT_LOOK || it.kind == PieceKind.CAL_THEME }
    if (!hasColours && r.tokens.isEmpty()) return
    Text("테마 색", style = MaterialTheme.typography.labelLarge, modifier = Modifier.semantics { heading() })
    if (r.tokens.isEmpty()) {
        Text("같은 색을 한 이름으로 묶으면, 그 색 하나만 바꿔도 근무 색·달력 색이 함께 바뀌어요 (12개까지)",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = { edit(RecipeTokens::bind) }) { Text("색을 테마 색으로 묶기") }
        return
    }
    r.tokens.forEach { t ->
        ColorSwatch(t.light, "${t.name} · ${RecipeTokens.uses(r, t.name)}곳", { c -> edit { RecipeTokens.set(it, t.name, c) } })
    }
    TextButton(onClick = { edit(RecipeTokens::unbind) }) { Text("테마 색 풀기 (색을 칸마다 따로)") }
}

/**
 * [RCX-17] 1.35 예시 달력 미리 보기 (만들기 5단계): for each kind of work it is made for, one example week with this recipe in it
 * (근무 꾸미기·달력 색), and what it changes there and what that kind of calendar does not take.
 */
@Composable
internal fun ExampleCalendars(r: Recipe) {
    Text("예시 달력에 넣어 보면", style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
    WorkStyle.entries.filter { it in r.styles }.forEach { style ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${RecipeDescribe.short(style)} 달력", style = MaterialTheme.typography.labelLarge)
                ExampleWeek(r, style)
                r.piecesFor(style).forEach { p ->
                    Text("• ${p.kind.label}: ${RecipeDescribe.line(p, amounts = false)}", style = MaterialTheme.typography.bodySmall)
                }
                val not = r.piecesNotFor(style)
                if (not.isNotEmpty()) Text("이 유형에는 없어요: ${not.joinToString(", ") { it.kind.label }}", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
@Suppress("UNCHECKED_CAST")
private fun ExampleWeek(r: Recipe, style: WorkStyle) {
    val pieces = r.piecesFor(style)
    val look = pieces.firstOrNull { it.kind == PieceKind.SHIFT_LOOK }?.let { RecipeResolve.piece(r, it, emptyMap()) }
    val theme = pieces.firstOrNull { it.kind == PieceKind.CAL_THEME }?.let { RecipeResolve.piece(r, it, emptyMap()) }
    val items = (look?.get("items") as? List<Map<String, Any?>>).orEmpty()
    if (items.isEmpty() && theme == null) return
    val days = listOf("월", "화", "수", "목", "금", "토", "일")
    val said = days.indices.joinToString(", ") { i -> "${days[i]} ${items.getOrNull(i % items.size.coerceAtLeast(1))?.get("name") ?: "쉬는 날"}" }
    Row(Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "예시 한 주: $said" }, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        days.forEachIndexed { i, d ->
            val item = items.getOrNull(i % items.size.coerceAtLeast(1))
            val bg = (item?.get("color") as? Long)?.asColor() ?: MaterialTheme.colorScheme.surfaceVariant
            val dayColor = when {
                i == 5 -> (theme?.get("sat") as? Long)?.asColor()
                i == 6 -> (theme?.get("sun") as? Long)?.asColor()
                i == 2 -> (theme?.get("today") as? Long)?.asColor()
                else -> null
            } ?: MaterialTheme.colorScheme.onSurfaceVariant
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(d, style = MaterialTheme.typography.labelSmall, color = dayColor)
                Box(Modifier.fillMaxWidth().aspectRatio(1f).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                    Text((item?.get("emoji") as? String)?.takeIf { it.isNotEmpty() } ?: (item?.get("label") as? String).orEmpty(),
                        color = contentColorOn(bg), style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}
