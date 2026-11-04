package com.bamnat.app.ui.calendar

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.bamnat.app.appContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.app.recipe.ThemeWardrobe
import com.bamnat.app.ui.components.Button
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeDescribe
import kotlinx.coroutines.launch

/**
 * 1.37 묶음 E (사용자 2026-11-04 "달력 공유 시 테마와 설정까지"): under a received link's preview, what came with it besides the days.
 * - [SHR-31] 테마: 입어 보기 opens the same 입어 보기 as 장터 ([com.bamnat.app.ui.recipe.ThemeSheet]): before and after on one example,
 *   each part to tick, then 입기 (or 안 받기: closing changes nothing). It follows 장터's rules for someone else's theme.
 * - [SHR-32] 근무 설정: each piece with what it would change on this phone, ticked or not, then 고른 것 넣기 (7일 안에 되돌리기 as any
 *   recipe). Pay never comes: a link carries no 급여 규칙 or amount.
 * Nothing goes on by itself; the link is still kept beside the calendar only with its own button.
 */
@Composable
internal fun SharedLookCards(schedule: com.bamnat.app.share.SharedSchedule, linkId: String) {
    val look = schedule.look
    val kit = schedule.kit
    if (look == null && kit == null) return
    val c = LocalContext.current.appContainer
    val style = remember { c.scopeOf(PersonalCalendar.MAIN).currentSettings().workStyle }
    val who = schedule.title.trim().ifEmpty { "보낸 분" }
    val fits = remember(kit, style) { kit?.piecesFor(style).orEmpty() }
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                when {
                    look != null && fits.isNotEmpty() -> "$who 님이 테마와 근무 설정도 보냈어요"
                    look != null -> "$who 님이 테마도 보냈어요"
                    else -> "$who 님이 근무 설정도 보냈어요"
                },
                style = MaterialTheme.typography.titleSmall,
            )
            Text("입어 보거나 골라 보기 전에는 아무것도 바뀌지 않아요. 급여·금액·메모·사진은 오지 않아요.",
                style = MaterialTheme.typography.bodySmall)
            if (look != null) {
                var trying by remember { mutableStateOf(false) }
                Button(onClick = { trying = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("테마 입어 보기") }
                if (trying) com.bamnat.app.ui.recipe.ThemeSheet(look, ThemeWardrobe.LINK + linkId, style) { trying = false }
            }
            if (kit != null && fits.isNotEmpty()) {
                var picking by remember { mutableStateOf(false) }
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text("근무 설정 골라 넣기 (${fits.size}가지)")
                }
                if (picking) KitDialog(kit.copy(pieces = fits), who) { picking = false }
            } else if (kit != null) {
                Text("보낸 근무 설정은 지금 근무 방식에는 맞지 않아 넣을 것이 없어요.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

/** [SHR-32] One row per piece: its name, what it would do here, ticked at first; 고른 것 넣기 puts the ticked ones on 내 달력. */
@Composable
private fun KitDialog(kit: Recipe, who: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val c = context.appContainer
    var picked by remember(kit) { mutableStateOf(kit.pieces.map { it.kind }.toSet()) }
    var busy by remember { mutableStateOf(false) }
    var asking by remember { mutableStateOf(false) }
    val lines by produceState<Map<PieceKind, String>>(emptyMap(), kit) {
        value = kit.pieces.associate { p ->
            p.kind to (runCatching { c.recipes.pieces.preview(p.kind, p.values, PersonalCalendar.MAIN) }.getOrNull()
                ?.joinToString(" · ") { it.text }?.takeIf { it.isNotBlank() } ?: RecipeDescribe.line(p, amounts = false))
        }
    }
    fun put() {
        busy = true
        // The app's scope: the put-on goes on even if the sheet under this closes.
        c.appScope.launch {
            // The 근무 주기 first: it may add the kinds of day the looks and alarms then match.
            val picks = kit.pieces.map { it.kind }.filter { it in picked }.sortedBy { if (it == PieceKind.COMPANY) 0 else 1 }
                .map { RecipeRepository.Pick(it, RecipePieces.Mode.REPLACE) }
            val ok = runCatching { c.recipes.apply(kit, "", 0, PersonalCalendar.MAIN, picks, emptyMap()) }.isSuccess
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                busy = false
                Toast.makeText(context.applicationContext,
                    if (ok) "고른 ${picks.size}가지를 넣었어요. 마을 › 장터 › 내 레시피에서 7일 안에 되돌려요" else "넣지 못했어요. 다시 해 주세요",
                    Toast.LENGTH_LONG).show()
                if (ok) onDismiss()
            }
        }
    }
    com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("$who 님의 근무 설정") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("넣을 것만 골라요. 지금 것은 남겨 두어 7일 안에 되돌릴 수 있어요.", style = MaterialTheme.typography.bodySmall)
                kit.pieces.forEach { p ->
                    val on = p.kind in picked
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .toggleable(on, role = Role.Checkbox) { picked = if (it) picked + p.kind else picked - p.kind },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = on, onCheckedChange = null)
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(kitLabel(p.kind), style = MaterialTheme.typography.bodyMedium)
                            lines[p.kind]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
                if (PieceKind.COMPANY in picked) Text("근무 주기를 넣으면 내 근무 종류와 순서가 보낸 분과 같아져요(같은 날짜 기준). 다른 조면 넣은 뒤 근무 주기에서 시작일을 옮겨요.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        confirmButton = {
            TextButton(enabled = picked.isNotEmpty() && !busy, onClick = { if (PieceKind.COMPANY in picked) asking = true else put() }) {
                Text(if (picked.isEmpty()) "고른 것이 없어요" else "고른 ${picked.size}가지 넣기")
            }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("안 받기") } },
    )
    if (asking) com.bamnat.app.ui.components.AlertDialog(
        onDismissRequest = { asking = false },
        title = { Text("내 근무 주기를 바꿀까요?") },
        text = { Text("내 달력의 근무 종류와 순서가 보낸 설정으로 바뀌어요. 지금 것은 남겨 두어 7일 안에 되돌릴 수 있어요.") },
        confirmButton = { TextButton(onClick = { asking = false; put() }) { Text("바꾸기") } },
        dismissButton = { TextButton(onClick = { asking = false }) { Text("취소") } },
    )
}

/** [SHR-32] A piece of 근무 설정 in the words of the share sheet. */
internal fun kitLabel(kind: PieceKind): String = when (kind) {
    PieceKind.SHIFT_LOOK -> "근무 이름·색·글자"
    PieceKind.ALARMS -> "근무 전 알람 시각"
    PieceKind.COMPANY -> "근무 주기(근무 종류·시간·순서)"
    else -> kind.label
}
