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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.bamnat.app.AppContainer
import com.bamnat.app.company.VillageFound
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.ui.company.SoftCard
import com.bamnat.app.ui.company.ago
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.work.WorkStyle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import androidx.compose.ui.semantics.Role

/**
 * 1.36 [VIL-06] 마을 V6 검색 하나: one box for 장터 작품, 광장 글 and 요청, each in its own group (동물의 숲에 검색이 없어 생긴 불편에서 착안,
 * 사용자 관찰/공개 정보). Searches as the user types, after a short pause, from two letters.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VillageSearchScreen(c: AppContainer, onBack: () -> Unit, onRecipe: (String) -> Unit, onPlaza: () -> Unit, onRequests: () -> Unit) {
    var q by rememberSaveable { mutableStateOf("") }
    // PC 확인 V13: what was looked for lately (device-only `village_ui`), and the box selects what is in it when tapped again.
    val prefs = remember { c.appContext.getSharedPreferences("village_ui", android.content.Context.MODE_PRIVATE) }
    var recent by remember { mutableStateOf(runCatching { prefs.getString("recent_q", "")!!.split('\n').filter { it.isNotBlank() } }.getOrDefault(emptyList())) }
    val keep = { w: String ->
        val t = w.trim()
        if (t.length >= 2) {
            recent = (listOf(t) + recent.filter { it != t }).take(8)
            runCatching { prefs.edit().putString("recent_q", recent.joinToString("\n")).apply() }
        }
    }
    var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(q)) }
    var selectAll by remember { mutableStateOf(false) }
    // PC 확인 V13·V23: tapping the box (focused or not: a tap elsewhere does not take the focus away) selects what is in it, so
    // the next letters replace it; the cursor the same tap sends right after is let go once, within a moment.
    val presses = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    LaunchedEffect(presses) {
        presses.interactions.collect { i ->
            if (i is androidx.compose.foundation.interaction.PressInteraction.Release && field.text.isNotEmpty()) {
                field = field.copy(selection = androidx.compose.ui.text.TextRange(0, field.text.length))
                selectAll = true
            }
        }
    }
    LaunchedEffect(selectAll) { if (selectAll) { delay(400); selectAll = false } }
    // [TOWN-06] 1.37 (후보 8·25): one kind of work only, or all with mine first.
    val mineStyle = remember { c.settingsRepository.current.workStyle }
    var pickName by rememberSaveable { mutableStateOf<String?>(null) }
    val pick = pickName?.let { n -> WorkStyle.entries.firstOrNull { it.name == n } }
    var recipes by remember { mutableStateOf<List<RecipeItem>?>(null) }
    var found by remember { mutableStateOf<VillageFound?>(null) }
    var failed by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    // PC 확인: a found post opens on itself, in its own plaza's talk, not on the plaza's first screen.
    val talkScope = androidx.compose.runtime.rememberCoroutineScope()
    val talk = remember { com.bamnat.app.ui.company.TalkHolder(c, talkScope) }
    com.bamnat.app.ui.company.TalkSheet(talk)
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(q, pick) {
        val words = q.trim()
        if (words.length < 2) { recipes = null; found = null; failed = false; return@LaunchedEffect }
        delay(350)
        coroutineScope {
            val r = async { if (c.recipeApi.enabled && c.recipes.on.value) runCatching { c.recipeApi.list(pick, "hot", words).items }.getOrNull() else emptyList() }
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
                value = field, onValueChange = { v ->
                    // PC 확인 V13: the tap that focuses the box sends a cursor right after; it must not undo 전체 선택.
                    if (selectAll && v.text == field.text && v.selection.collapsed) return@OutlinedTextField
                    selectAll = false
                    field = if (v.text.length > 30) v.copy(text = v.text.take(30)) else v; q = field.text
                },
                singleLine = true,
                placeholder = { Text("작품·글·요청을 한 번에 (예: 야간, 다크, 알람)") },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = ""; field = androidx.compose.ui.text.input.TextFieldValue("") }) { Icon(Icons.Filled.Close, "지우기") } },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onSearch = { keep(q) }),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus)
                    .onFocusChanged { f -> if (f.isFocused && field.text.isNotEmpty()) {
                        field = field.copy(selection = androidx.compose.ui.text.TextRange(0, field.text.length)); selectAll = true } },
                interactionSource = presses,
            )
            // [TOWN-06] Which kind of work: 전체 (mine first) or one.
            androidx.compose.foundation.layout.Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                com.bamnat.app.ui.components.FilterChip(selected = pick == null, onClick = { pickName = null },
                    label = { Text(if (mineStyle != null) "전체 · ${RecipeDescribe.short(mineStyle)} 먼저" else "전체") })
                (listOfNotNull(mineStyle) + WorkStyle.entries.filter { it != mineStyle }).forEach { st ->
                    com.bamnat.app.ui.components.FilterChip(selected = pick == st, onClick = { pickName = if (pick == st) null else st.name },
                        label = { Text(RecipeDescribe.short(st)) })
                }
            }
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val words = q.trim()
                when {
                    words.length < 2 -> {
                        item { Hint("두 글자 이상 적으면 장터 작품, 광장 글, 요청을 함께 찾아요") }
                        if (recent.isNotEmpty()) item {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                    Group("최근 찾은 말")
                                    androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
                                    androidx.compose.material3.TextButton(onClick = { recent = emptyList(); runCatching { prefs.edit().remove("recent_q").apply() } }) { Text("모두 지우기") }
                                }
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    recent.forEach { w ->
                                        androidx.compose.material3.AssistChip(onClick = {
                                            q = w; field = androidx.compose.ui.text.input.TextFieldValue(w, androidx.compose.ui.text.TextRange(w.length))
                                        }, label = { Text(w) })
                                    }
                                }
                            }
                        }
                    }
                    failed -> item { Hint("인터넷이 없거나 서버가 바빠요. 잠시 뒤 다시 찾아 주세요") }
                    recipes == null && found == null -> item { Hint("찾는 중이에요") }
                    else -> {
                        // [TOWN-06] One kind picked: only its own (and what is made for every kind); 전체: mine first, then for every
                        // kind, then the rest, each in the server's order.
                        val rank = { styles: Set<WorkStyle> -> when { mineStyle != null && mineStyle in styles -> 0; styles.isEmpty() -> 1; else -> 2 } }
                        val rs = recipes.orEmpty().filter { pick == null || it.styles.isEmpty() || pick in it.styles }
                            .sortedBy { if (pick == null) rank(it.styles) else 0 }
                        val postStyle = { p: com.bamnat.app.company.FoundPost -> setOfNotNull(com.bamnat.core.plaza.StylePlaza.styleOf(p.room)) }
                        val ps = found?.posts.orEmpty().filter { pick == null || pick in postStyle(it) }
                            .sortedBy { if (pick == null) rank(postStyle(it)) else 0 }
                        val reqStyle = { r: com.bamnat.app.company.FoundRequest ->
                            r.style?.let { n -> WorkStyle.entries.firstOrNull { it.name == n } }.let { s -> setOfNotNull(s) } }
                        val qs = found?.requests.orEmpty().filter { pick == null || r(it.style) == null || pick in reqStyle(it) }
                            .sortedBy { if (pick == null) rank(reqStyle(it)) else 0 }
                        if (rs.isEmpty() && ps.isEmpty() && qs.isEmpty()) item { Hint("‘$words’${com.bamnat.app.ui.Fmt.ro("‘$words’")} 찾은 것이 없어요" +
                            (pick?.let { " (${RecipeDescribe.short(it)}만 보는 중 · 전체를 눌러 넓혀 보세요)" } ?: ". 요청 판에 ‘이런 거 있었으면’을 남겨 보세요")) }
                        if (rs.isNotEmpty()) {
                            item { Group("장터 작품 ${rs.size}") }
                            items(rs.take(10), key = { "r" + it.code }) { r ->
                                Row(r.title, styleTag(r.styles) + r.summary.ifBlank { "좋아요 ${r.likes} · 넣어 본 분 ${r.users}" }) { keep(words); onRecipe(r.code) }
                            }
                        }
                        if (ps.isNotEmpty()) {
                            item { Group("광장 글 ${ps.size}") }
                            items(ps, key = { "p" + it.id }) { p ->
                                Row(p.body, "${p.roomName} · ${ago(p.at)}" + if (p.replies > 0) " · 답글 ${p.replies}" else "") {
                                    keep(words); talk.show(p.room, com.bamnat.core.plaza.StylePlaza.ROOM, p.roomName, focus = p.id)
                                }
                            }
                        }
                        if (qs.isNotEmpty()) {
                            item { Group("요청 ${qs.size}") }
                            items(qs, key = { "q" + it.id }) { r ->
                                Row(r.body, (when (r.state) { "done" -> "완성"; "making" -> "만드는 중"; else -> "기다림" }) + " · 좋아요 ${r.likes}") {
                                    keep(words); r.code?.let(onRecipe) ?: onRequests()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** [TOWN-06] Whose a work is, before its line: "교대 · " for one or two kinds, nothing for every kind. */
private fun styleTag(styles: Set<WorkStyle>): String =
    if (styles.isEmpty() || styles.size > 2) "" else styles.joinToString("·") { RecipeDescribe.short(it) } + " · "

/** A request's style name, or null for every kind (ALL) or an older server. */
private fun r(style: String?): String? = style?.takeIf { it != "ALL" }

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
    SoftCard(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}
