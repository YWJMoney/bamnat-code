package com.bamnat.app.ui.village

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.company.VillageFound
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.company.ago
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * 1.36 [VIL-06] 마을 V6 검색 하나: one box for 장터 작품, 광장 글 and 요청, each in its own group (동물의 숲에 검색이 없어 생긴 불편에서 착안,
 * 사용자 관찰/공개 정보). Searches as the user types, after a short pause, from two letters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillageSearchScreen(c: AppContainer, onBack: () -> Unit, onRecipe: (String) -> Unit, onPlaza: () -> Unit, onRequests: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    var recipes by remember { mutableStateOf<List<RecipeItem>?>(null) }
    var found by remember { mutableStateOf<VillageFound?>(null) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q) {
        val words = q.trim()
        if (words.length < 2) { recipes = null; found = null; failed = false; return@LaunchedEffect }
        delay(350)
        coroutineScope {
            val r = async { if (c.recipeApi.enabled && c.recipes.on.value) runCatching { c.recipeApi.list(null, "good", words).items }.getOrNull() else emptyList() }
            val v = async { runCatching { c.companyApi.villageSearch(words) }.getOrNull() }
            recipes = r.await()
            found = v.await()
            failed = recipes == null && found == null
        }
    }
    Scaffold(topBar = {
        TopAppBar(title = { Text("마을에서 찾기") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            OutlinedTextField(
                value = q, onValueChange = { q = it.take(30) }, singleLine = true,
                placeholder = { Text("작품·글·요청을 한 번에 (예: 야간, 다크, 알람)") },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "지우기") } },
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
            )
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val words = q.trim()
                when {
                    words.length < 2 -> item { Hint("두 글자 이상 적으면 장터 작품, 광장 글, 요청을 함께 찾아요") }
                    failed -> item { Hint("인터넷이 없거나 서버가 바빠요. 잠시 뒤 다시 찾아 주세요") }
                    recipes == null && found == null -> item { Hint("찾는 중이에요") }
                    else -> {
                        val rs = recipes.orEmpty()
                        val ps = found?.posts.orEmpty()
                        val qs = found?.requests.orEmpty()
                        if (rs.isEmpty() && ps.isEmpty() && qs.isEmpty()) item { Hint("‘$words’${com.bamnat.app.ui.Fmt.ro("‘$words’")} 찾은 것이 없어요. 요청 판에 ‘이런 거 있었으면’을 남겨 보세요") }
                        if (rs.isNotEmpty()) {
                            item { Group("장터 작품 ${rs.size}") }
                            items(rs.take(10), key = { "r" + it.code }) { r ->
                                Row(r.title, r.summary.ifBlank { "좋아요 ${r.likes} · 쓰는 분 ${r.users}" }) { onRecipe(r.code) }
                            }
                        }
                        if (ps.isNotEmpty()) {
                            item { Group("광장 글 ${ps.size}") }
                            items(ps, key = { "p" + it.id }) { p ->
                                Row(p.body, "${p.roomName} · ${ago(p.at)}" + if (p.replies > 0) " · 답글 ${p.replies}" else "", onPlaza)
                            }
                        }
                        if (qs.isNotEmpty()) {
                            item { Group("요청 ${qs.size}") }
                            items(qs, key = { "q" + it.id }) { r ->
                                Row(r.body, (when (r.state) { "done" -> "완성"; "making" -> "만드는 중"; else -> "기다림" }) + " · 좋아요 ${r.likes}") {
                                    r.code?.let(onRecipe) ?: onRequests()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Group(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp).semantics { heading() })
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun Row(title: String, line: String, onClick: () -> Unit) {
    SoftCard(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
