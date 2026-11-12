package com.bamnat.app.ui.village

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.bamnat.app.ui.components.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.bamnat.app.ui.components.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.app.company.CompanyException
import com.bamnat.app.company.MyPost
import com.bamnat.app.company.SavedPosts
import com.bamnat.app.ui.company.ago
import com.bamnat.app.ui.company.SoftCard
import kotlinx.coroutines.launch

/**
 * 1.36 [VIL-13] 마을 V13 간직한 글 · 내 글 · 내 답글, in one place under 마을 › 공방 (Pinterest 보드에서 착안, 공개 정보).
 * 간직한 글 stay on this phone as they were when kept; 내 글 and 내 답글 come from the server, only for this install.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyVillagePostsScreen(c: AppContainer, onBack: () -> Unit, onPlaza: () -> Unit, startTab: Int = 0) {
    val saved = remember { SavedPosts.of(c.appContext) }
    val keptList by saved.items.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(startTab.coerceIn(0, 2)) }
    var mine by remember { mutableStateOf<List<MyPost>?>(null) }
    // [TOWN-02] 1.37: answers that came since this phone last looked, marked on each of my posts until I leave this screen.
    var fresh by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var problem by remember { mutableStateOf<String?>(null) }
    val snack = com.bamnat.app.ui.components.AppSnack.rememberLane()
    val scope = rememberCoroutineScope()
    // PC 확인: 글 보기 opens the post itself in its plaza's talk.
    val talk = remember { com.bamnat.app.ui.company.TalkHolder(c, scope) }
    com.bamnat.app.ui.company.TalkSheet(talk)
    val open = { room: String, name: String, id: String -> talk.show(room, com.bamnat.core.plaza.StylePlaza.ROOM, name, focus = id) }
    // 1.39 발굴: from '보완 요청이 n개 왔어요', the version's talk opens at once.
    val fixTalk by com.bamnat.app.ui.company.TalkSheets.pending.collectAsStateWithLifecycle()
    LaunchedEffect(fixTalk) {
        val f = fixTalk ?: return@LaunchedEffect
        com.bamnat.app.ui.company.TalkSheets.pending.value = null
        talk.show(f.company, f.setup, f.title)
    }
    // 1.39 조사 ③(4) 가려진 내 글 '다시 보여 주세요'(마을 돌봄이 켜진 뒤, 처리방침 3.12): 운영자가 다시 읽고 답해요.
    val canAsk = c.flags.on(com.bamnat.core.flags.FlagCatalog.COMMUNITY_CARE)
    var askFor by remember { mutableStateOf<String?>(null) }
    askFor?.let { id ->
        com.bamnat.app.ui.hub.AppealDialog(c, "post:$id", onDone = { sent -> askFor = null; sent?.let { scope.launch { snack.showSnackbar(it) } } })
    }
    LaunchedEffect(Unit) {
        mine = try {
            c.companyApi.myPosts(c.companyStore.device).also { list ->
                fresh = com.bamnat.app.company.VillageReplies.fresh(c.appContext, list)
                // [TOWN-02] Seen once shown here: 광장's 새 답 line goes; the marks stay on this screen while it is open.
                com.bamnat.app.company.VillageReplies.markSeen(c.appContext, list)
            }
        } catch (e: CompanyException) {
            problem = "인터넷이 없거나 서버가 바빠요. 잠시 뒤 다시 열어 주세요"
            null
        }
    }
    Scaffold(
        snackbarHost = { com.bamnat.app.ui.components.AppSnackHost() },
        topBar = { TopAppBar(title = { Text("간직한 글 · 내 글") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            val written = mine?.filter { it.parent == null }
            val replies = mine?.filter { it.parent != null }
            TabRow(selectedTabIndex = tab, modifier = Modifier.widthIn(max = 640.dp)) {
                listOf("간직한 글 ${keptList.size}", "내 글${written?.let { " ${it.size}" } ?: ""}" + (if (fresh.isNotEmpty()) " •" else ""),
                    "내 답글${replies?.let { " ${it.size}" } ?: ""}")
                    .forEachIndexed { i, label -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(label) }) }
            }
            LazyColumn(Modifier.widthIn(max = 640.dp).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (tab) {
                    0 -> {
                        if (keptList.isEmpty()) item {
                            Empty("아직 간직한 글이 없어요. 광장 글의 ⋯ › ‘간직하기’로 좋은 팁을 모아 두면 여기서 다시 봐요", "광장 가기", onPlaza)
                        }
                        items(keptList, key = { it.id }) { s ->
                            PostCard(s.roomName, label(s.kind), s.body, ago(s.at), extra = null) {
                                TextButton(onClick = { open(s.room, s.roomName, s.id) }) { Text("글 보기") }
                                TextButton(onClick = {
                                    saved.remove(s.id)
                                    scope.launch {
                                        if (snack.showSnackbar("간직한 글에서 뺐어요", "되돌리기", withDismissAction = true) == SnackbarResult.ActionPerformed) saved.restore(s)
                                    }
                                }) { Text("빼기") }
                            }
                        }
                    }
                    else -> {
                        val list = if (tab == 1) written else replies
                        when {
                            list == null -> item { Text(problem ?: "불러오는 중이에요", style = MaterialTheme.typography.bodyMedium) }
                            list.isEmpty() -> item {
                                Empty(if (tab == 1) "아직 광장에 쓴 글이 없어요" else "아직 단 답글이 없어요", "광장 가기", onPlaza)
                            }
                            // [TOWN-02] Posts with new answers first, marked.
                            else -> items(if (tab == 1) list.sortedByDescending { fresh[it.id] ?: 0 } else list, key = { it.id }) { p ->
                                PostCard(p.roomName, if (p.parent != null) "답글" else label(p.kind), p.body, ago(p.at),
                                    news = fresh[p.id]?.let { "💬 새 답 ${it}개" },
                                    extra = listOfNotNull(
                                        if (p.parent == null && p.replies > 0) "답글 ${p.replies}" else null,
                                        if (p.hidden) "다른 분께는 가려졌어요" else null,
                                    ).joinToString(" · ").ifEmpty { null }) {
                                    if (p.room.isNotBlank()) TextButton(onClick = { open(p.room, p.roomName, p.id) }) { Text("글 보기") }
                                    if (p.hidden && canAsk) TextButton(onClick = { askFor = p.id }) { Text("다시 보여 주세요") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun label(kind: String) = when (kind) {
    "question" -> "질문"
    "tip" -> "팁"
    "show" -> "자랑"
    "proposal" -> "제안"
    else -> "이야기"
}

@Composable
private fun PostCard(room: String, kind: String, body: String, at: String, extra: String?, news: String? = null, actions: @Composable () -> Unit) {
    SoftCard {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("$room · $kind", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.weight(1f))
                Text(at, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            news?.let { Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            Text(body, style = MaterialTheme.typography.bodyMedium, maxLines = 6)
            extra?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Row { Spacer(Modifier.weight(1f)); actions() }
        }
    }
}

@Composable
private fun Empty(text: String, action: String, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = onClick) { Text(action) }
    }
}
