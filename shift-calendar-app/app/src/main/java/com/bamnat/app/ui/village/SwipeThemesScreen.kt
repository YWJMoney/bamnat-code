package com.bamnat.app.ui.village

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.ui.recipe.thumb
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.launch

/**
 * 1.36 [VIL-17] 마을 V17 넘겨 보기: 장터's themes one at a time, big, to flip through upward like a deck (마리오 메이커 끝없이 도전·동물의 숲
 * 꿈 방문에서 착안, 사용자 관찰/공개 정보). ♥ and 입어 보기 on each; 입어 보기 opens the theme's page, where trying it on is one tap and
 * 되돌리기 is there. Themes I hid are not in the deck.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeThemesScreen(c: AppContainer, style: WorkStyle?, onBack: () -> Unit, onRecipe: (String) -> Unit) {
    var deck by remember { mutableStateOf<List<RecipeItem>?>(null) }
    var failed by remember { mutableStateOf(false) }
    val liked = remember { mutableStateOf(setOf<String>()) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(style) {
        val got = runCatching {
            val first = c.recipeApi.list(style, "hot", kind = RecipeClient.SCOPE_THEME)
            val more = first.next?.let { runCatching { c.recipeApi.list(style, "hot", cursor = it, kind = RecipeClient.SCOPE_THEME).items }.getOrNull() }.orEmpty()
            (first.items + more).distinctBy { it.code }.filter { it.thumb() != null && it.scope != "tab" && !c.recipes.hides(it) }
        }
        deck = got.getOrNull()
        failed = got.isFailure
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("넘겨 보며 고르기") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        val list = deck
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            when {
                failed -> Text("인터넷이 없거나 서버가 바빠요. 잠시 뒤 다시 열어 주세요", Modifier.padding(16.dp))
                list == null -> Text("테마를 모으는 중이에요", Modifier.padding(16.dp))
                list.isEmpty() -> Text("아직 넘겨 볼 테마가 없어요", Modifier.padding(16.dp))
                else -> {
                    val pager = rememberPagerState { list.size }
                    Text("위로 넘기면 다음 테마 · ${pager.currentPage + 1}/${list.size}", style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    VerticalPager(pager, Modifier.weight(1f).fillMaxWidth(), key = { list[it].code }) { i ->
                        val t = list[i]
                        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp, Alignment.Top)) {
                            // [MKV-01] 1.37: the theme as it really looks, as big as fits above its name and buttons.
                            BoxWithConstraints(Modifier.widthIn(max = 260.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                                com.bamnat.app.ui.recipe.MarketShot(t, maxWidth, style, onClick = { com.bamnat.app.recipe.RecipeLinks.tryNext = t.code; onRecipe(t.code) })
                            }
                            Text(t.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            // 1.36 [VIL-17] PC 확인 V9: say what wearing it changes, not just a picture.
                            if (t.tabs.isNotEmpty()) Text("바뀌는 곳: " + t.tabs.joinToString(" · ") { com.bamnat.app.ui.recipe.marketTabLabel(it, style) },
                                style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            // PC 확인: the rest of the page says what it is, who made it and how to see it on one's own calendar.
                            if (t.summary.isNotBlank()) Text(t.summary, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            t.nick?.let { Text("만든 분 $it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text("♥ ${t.likes + if (t.code in liked.value) 1 else 0} · 입은 분 ${t.users}" + if (t.newMaker) " · 새 창작자" else "",
                                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                val on = t.code in liked.value
                                OutlinedButton(onClick = {
                                    liked.value = if (on) liked.value - t.code else liked.value + t.code
                                    scope.launch { runCatching { c.recipeApi.vote(c.companyStore.device, t.code, if (on) null else 1) } }
                                }, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (on) "♥ 좋아요 함" else "♡ 좋아요") }
                                Button(onClick = { com.bamnat.app.recipe.RecipeLinks.tryNext = t.code; onRecipe(t.code) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("입어 보기") }
                            }
                            if (i < list.size - 1) FilledTonalButton(onClick = { scope.launch { pager.animateScrollToPage(i + 1) } }) { Text("다음 테마") }
                            Text("입어 보기를 누르면 내 달력에 먼저 입혀 보고, 마음에 드는 부분만 골라 넣어요. 넣은 뒤 7일 안에는 되돌릴 수 있어요",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
                        }
                    }
                }
            }
        }
    }
}
