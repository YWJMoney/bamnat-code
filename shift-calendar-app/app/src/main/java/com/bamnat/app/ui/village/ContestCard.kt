package com.bamnat.app.ui.village

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import com.bamnat.app.ui.components.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.Contest
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch
import androidx.compose.ui.semantics.Role

/**
 * 1.36 [VIL-19] 마을 V19 이달의 주제 공모, in 공방: this month's theme for my kind of work with 내 작품 넣기, and last month's 이달의 테마
 * to look at. Nothing shows when there is neither a theme nor picks (the operator sets them in 관리자 앱 › 마을 운영).
 */
@Composable
fun ContestCard(c: AppContainer, style: WorkStyle?, onRecipe: (String) -> Unit) {
    val st = style ?: return
    var contest by remember { mutableStateOf<Contest?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    var picking by remember { mutableStateOf(false) }
    var problem by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(st, reload) { contest = runCatching { c.companyApi.contest(c.companyStore.device, st.name) }.getOrNull() }
    val k = contest ?: return
    if (k.theme == null && k.picks.isEmpty()) return
    SoftCard {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            // PC 확인 V31: which month, in the title; with only last month's picks, it is that month's 이달의 테마.
            val month = k.month.split('-').getOrNull(1)?.toIntOrNull()
            val lastMonth = month?.let { if (it == 1) 12 else it - 1 }
            Text(if (k.theme != null) "${month?.let { "${it}월 " } ?: ""}이달의 주제" else "${lastMonth?.let { "${it}월 " } ?: "지난 달 "}이달의 테마",
                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            k.theme?.let { theme ->
                Text("‘$theme’", style = MaterialTheme.typography.bodyLarge)
                // 1.36 [GRW-09] 뽑히면 프로 한 달 (만든 작품 보상).
                Text("만든 작품을 넣으면 달 끝에 운영자가 3개를 골라 ‘이달의 테마’로 마을에 소개해요. 뽑히면 프로 한 달을 드려요 · 지금 ${k.entries}개",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                // PC 확인 V26: what I put in by name, and how many more can go in.
                if (k.mine.isNotEmpty()) {
                    Text("내가 넣은 작품 ${k.mine.size}개 · ${(3 - k.mine.size).coerceAtLeast(0)}개 더 넣을 수 있어요", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary)
                    if (k.mineTitles.isNotEmpty()) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        k.mineTitles.forEach { (code, title) -> AssistChip(onClick = { onRecipe(code) }, label = { Text("🎨 $title", maxLines = 1) }) }
                    }
                }
                if (c.recipeApi.enabled) FilledTonalButton(onClick = { picking = true }, modifier = Modifier.heightIn(min = 48.dp)) {
                    Text(if (k.mine.isEmpty()) "내 작품 넣기" else "넣은 작품 바꾸기")
                }
            }
            problem?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            if (k.picks.isNotEmpty()) {
                // PC 확인 V33: with no theme this month the title already says it; the line only says the theme.
                Text((if (k.theme == null) "" else (lastMonth?.let { "${it}월" } ?: "지난 달") + " 이달의 테마 · ") + (k.lastTheme?.let { "‘$it’ · " } ?: "") + "눌러서 입어 보기",
                    style = MaterialTheme.typography.labelLarge)
                // PC 확인 13503764: one pick takes the card's width, its picture bigger and the title beside it.
                if (k.picks.size == 1) k.picks.first().let { (code, title) ->
                    Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                        .clickable(onClickLabel = "입어 보기") { com.bamnat.app.recipe.RecipeLinks.tryNext = code; onRecipe(code) }
                        .padding(4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        com.bamnat.app.ui.recipe.ThemeThumb(k.pickLooks[code]?.withDays(st) ?: com.bamnat.app.recipe.ThemeSwatch.example(st), width = 148.dp)
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("🏆 $title", style = MaterialTheme.typography.titleSmall, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            Text("눌러서 입어 보기", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                // PC 확인 f39bcfbe: each pick as its small picture with the title under it, the whole a tap to try it on.
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    k.picks.forEach { (code, title) ->
                        Column(Modifier.width(112.dp).clip(MaterialTheme.shapes.medium)
                            .clickable(onClickLabel = "입어 보기") { com.bamnat.app.recipe.RecipeLinks.tryNext = code; onRecipe(code) }
                            .padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            com.bamnat.app.ui.recipe.ThemeThumb(k.pickLooks[code]?.withDays(st) ?: com.bamnat.app.recipe.ThemeSwatch.example(st), width = 104.dp)
                            Text("🏆 $title", style = MaterialTheme.typography.labelMedium, maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        }
                    }
                }
                }
            }
        }
    }
    if (picking) {
        var mine by remember { mutableStateOf<List<com.bamnat.app.recipe.RecipeItem>?>(null) }
        var chosen by remember { mutableStateOf(k.mine.toSet()) }
        LaunchedEffect(Unit) {
            mine = runCatching { c.recipeApi.mine(c.companyStore.device).items }.getOrDefault(emptyList())
                .filter { (r, s) -> s.first == "visible" && s.second == "public" && (r.styles.isEmpty() || st in r.styles) }.map { (r, _) -> r }
        }
        AlertDialog(
            onDismissRequest = { picking = false },
            title = { Text("이달의 주제에 넣을 작품") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("공개로 올린 내 작품만 넣을 수 있어요 (한 달에 3개까지)", style = MaterialTheme.typography.bodySmall)
                    val list = mine
                    when {
                        list == null -> Text("불러오는 중이에요")
                        list.isEmpty() -> Text("공개로 올린 작품이 아직 없어요. 스튜디오에서 만들어 공개로 올리면 넣을 수 있어요")
                        else -> list.forEach { r ->
                            val code = r.code
                            // PC 확인 V25: each with a small picture, the whole row a tap.
                            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { chosen = if (code in chosen) chosen - code else chosen + code },
                                verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(code in chosen, onCheckedChange = { on -> chosen = if (on) chosen + code else chosen - code })
                                // PC 확인 V25: one without a look of its own still shows the example week in plain colours.
                                com.bamnat.app.ui.recipe.ThemeThumb(r.look?.withDays(st) ?: com.bamnat.app.recipe.ThemeSwatch.example(st), width = 64.dp)
                                androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 8.dp))
                                Text(r.title, maxLines = 1)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = chosen.size <= 3, onClick = {
                    picking = false
                    scope.launch {
                        problem = try {
                            (chosen - k.mine.toSet()).forEach { c.companyApi.enterContest(c.companyStore.device, st.name, it, true) }
                            (k.mine.toSet() - chosen).forEach { c.companyApi.enterContest(c.companyStore.device, st.name, it, false) }
                            null
                        } catch (e: CompanyException) {
                            "넣지 못했어요. 한 달에 3개까지, 공개로 올린 내 작품만 넣을 수 있어요"
                        }
                        reload++
                    }
                }) { Text(if (chosen.size > 3) "3개까지 골라 주세요" else "저장") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("닫기") } },
        )
    }
}
