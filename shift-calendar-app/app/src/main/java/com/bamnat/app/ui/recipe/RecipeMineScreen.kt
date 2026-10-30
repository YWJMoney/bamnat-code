package com.bamnat.app.ui.recipe

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.layout.heightIn
import com.bamnat.app.appContainer
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.core.recipe.PieceKind

/**
 * [RCP-38] 설정 › 나눔 판: turn it off (every way in goes, with where to turn it back on), the recipes put on this phone with
 * 되돌리기 for 7 days, my recipes on the plaza with their state and 안좋아요 의견, and those being made.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecipeMineScreen(vm: RecipeMineViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onDraft: (Long) -> Unit, onList: () -> Unit,
    /** [RCX-05] 1.35 내 만든 분 페이지. */
    onMaker: (String) -> Unit = {},
    /** [RCX-31] 1.35 새 판 만들기: the code and the recipe as it is. */
    onNewVersion: (String, String) -> Unit = { _, _ -> }) {
    val on by vm.on.collectAsStateWithLifecycle()
    val applied by vm.applied.collectAsStateWithLifecycle()
    val drafts by vm.drafts.collectAsStateWithLifecycle()
    val mine by vm.mine.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val newer by vm.newer.collectAsStateWithLifecycle()
    val update by vm.update.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    // [RCX-26] Every 지우기 here asks first (한 번 지운 것은 돌아오지 않아요).
    var asking by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    // 1.35 자유도 점검: 되돌리기 asks first too (what it puts back and what it keeps).
    var undoing by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }
    LaunchedEffect(message) { message?.let { vm.shown(); snack.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text("장터·내 레시피") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } }) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("장터 쓰기", style = MaterialTheme.typography.bodyLarge)
                        Text(if (on) "마을에 장터 칸과 설정 화면의 ‘이 설정 나누기’가 보여요"
                            else "꺼져 있어요. 다시 켜려면 여기(설정 › 장터·내 레시피)로 오세요. 넣어 둔 레시피는 그대로예요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(on, vm::setOn)
                }
                if (on) NewsSwitches()
                if (on) Row {
                    TextButton(onClick = onList) { Text("레시피 보러 가기") }
                    me?.let { m -> TextButton(onClick = { onMaker(m) }) { Text("내 만든 분 페이지") } }
                }

                Header("받은 레시피")
                if (applied.isEmpty()) Muted("아직 넣은 레시피가 없어요")
                applied.take(30).forEach { e ->
                    Card(Modifier.fillMaxWidth().clickable(enabled = e.code.isNotEmpty()) { onOpen(e.code) }) {
                        Column(Modifier.padding(12.dp)) {
                            Text(e.title, style = MaterialTheme.typography.titleSmall)
                            Text(e.pieces.split(',').mapNotNull { PieceKind.of(it)?.label }.joinToString(" · ") + " · " +
                                java.text.SimpleDateFormat("M월 d일", java.util.Locale.KOREA).format(java.util.Date(e.appliedAt)) + " 넣음",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            // [RCX-02][RCX-03] 새 판: offered with what it changes; mine stays where I changed it.
                            if (e.undoneAt == null && e.replacedAt == null && e.code.isNotEmpty()) {
                                newer[e.code]?.let { v ->
                                    androidx.compose.material3.Button(onClick = { vm.openUpdate(e) }) { Text("새 판 v$v 있어요 · 바뀐 점 보고 반영") }
                                }
                                var sub by androidx.compose.runtime.remember(e.code) { androidx.compose.runtime.mutableStateOf(vm.subscribed(e.code)) }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("새 판 알림", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                    Switch(sub, { sub = it; vm.subscribe(e.code, it) })
                                }
                            }
                            when {
                                e.undoneAt != null -> Muted("되돌렸어요")
                                e.replacedAt == com.bamnat.app.data.RecipeBackup.RESTORED -> Muted("백업에서 돌아왔어요 · 되돌리기는 넣은 휴대폰에서만 돼요")
                                e.replacedAt != null -> Muted("다른 레시피가 그 자리에 들어갔어요")
                                vm.canUndo(e) -> TextButton(onClick = { undoing = e.title to { vm.undo(e.id) } }) { Text("되돌리기 (넣은 지 7일 안)") }
                                else -> Muted("7일이 지나 그대로 쓰고 있어요")
                            }
                        }
                    }
                }

                Header("내가 올린 레시피")
                val m = mine
                if (m == null) Muted("불러오는 중이거나 인터넷이 없어요")
                else {
                    val review = if (m.reviewLeft > 0) " · 앞으로 ${m.reviewLeft}개는 운영자가 보고 올려요" else ""
                    Muted("하루에 ${if (m.trust >= 1) 4 else 2}개까지 올릴 수 있어요$review")
                    if (m.items.isEmpty()) Muted("아직 올린 레시피가 없어요")
                    m.items.forEach { (item, st) ->
                        Card(Modifier.fillMaxWidth().clickable { onOpen(item.code) }) {
                            Column(Modifier.padding(12.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(stateLabel(st.first, st.second), style = MaterialTheme.typography.bodySmall)
                                st.third?.let { Text("운영자 메모: $it", style = MaterialTheme.typography.bodySmall) }
                                Text("좋아요 ${item.likes} · 안좋아요 ${item.dislikes} · 쓰는 분 ${item.users} · 코드 ${item.code}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                RemixCount(item.code)
                                Row {
                                    TextButton(onClick = { vm.newVersion(item.code) { body -> onNewVersion(item.code, body) } }) { Text("새 판 만들기") }
                                    TextButton(onClick = { asking = "‘${item.title}’을(를) 장터에서 지울까요? 이미 넣은 분들의 설정은 그대로예요" to { vm.delete(item.code) } }) {
                                        Text("지우기", color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }

                Header("만들던 레시피")
                if (drafts.isEmpty()) Muted("만들던 레시피가 없어요")
                drafts.forEach { d ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f).clickable { onDraft(d.id) }.padding(vertical = 8.dp)) {
                            Text(runCatching { org.json.JSONObject(d.body).optString("title") }.getOrNull()?.ifEmpty { null } ?: "이름 없는 레시피",
                                style = MaterialTheme.typography.bodyLarge)
                            Muted("${d.step}/5단계에서 멈춤")
                        }
                        TextButton(onClick = { asking = "만들던 레시피를 지울까요? 지우면 이어서 만들 수 없어요" to { vm.deleteDraft(d.id) } }) { Text("지우기") }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    update?.let { UpdateDialog(it, vm) }
    undoing?.let { (title, go) ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { undoing = null }, title = { Text("되돌릴까요?") },
            text = { Text("‘$title’이 바꾼 것만 넣기 전으로 돌아가요. 넣은 뒤 내가 직접 바꾼 것과 근무 기록·가계부·메모는 그대로예요") },
            confirmButton = { TextButton(onClick = { undoing = null; go() }) { Text("되돌리기") } },
            dismissButton = { TextButton(onClick = { undoing = null }) { Text("닫기") } })
    }
    asking?.let { (q, go) ->
        androidx.compose.material3.AlertDialog(onDismissRequest = { asking = null }, title = { Text("지울까요?") }, text = { Text(q) },
            confirmButton = { TextButton(onClick = { asking = null; go() }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { asking = null }) { Text("닫기") } })
    }
}

/** [RCX-03] 새 판 반영: each piece's changed fields; 부딪힘 (I changed it too) is off unless chosen. */
@Composable
private fun UpdateDialog(u: RecipeMineViewModel.Update, vm: RecipeMineViewModel) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = vm::closeUpdate,
        title = { Text("‘${u.recipe.title}’ v${u.version}에서 바뀐 점") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Muted("고른 것만 내 설정에 들어가요. 내가 따로 고친 칸은 ‘내가 고침’으로 표시되고, 고르지 않으면 그대로예요")
                u.results.forEach { res ->
                    Text(res.kind.label, style = MaterialTheme.typography.labelLarge)
                    res.fields.forEach { f ->
                        val on = f.path in u.picked[res.kind].orEmpty()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            androidx.compose.material3.Checkbox(on, { vm.pick(res.kind, f.path, it) })
                            Column(Modifier.weight(1f)) {
                                Text(fieldName(f.path), style = MaterialTheme.typography.bodyMedium)
                                Text("지금 ${shortValue(f.mine)} → 새 판 ${shortValue(f.theirs)}" +
                                    if (f.kind == com.bamnat.core.recipe.Merge3.Kind.CONFLICT) " · 내가 고침" else "",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = vm::applyUpdate) { Text("고른 것 반영") } },
        dismissButton = { TextButton(onClick = vm::closeUpdate) { Text("닫기") } },
    )
}

private val FIELD_WORDS = mapOf("label" to "글자", "color" to "색", "emoji" to "이모지", "today" to "오늘 색", "sat" to "토요일 색", "sun" to "일요일 색",
    "holiday" to "공휴일 색", "cell" to "칸 칠하기", "wage" to "시급", "night" to "야간 가산", "weeklyPay" to "주휴수당", "five" to "5인 이상",
    "breakPay" to "휴게시간", "tax" to "세금", "mode" to "세는 방식", "dayRate" to "일당", "chips" to "공수 칩", "open" to "영업일",
    "opens" to "여는 시각", "closes" to "닫는 시각", "cardFee" to "카드 수수료", "tabs" to "탭 순서", "calendar" to "달력 카드", "stats" to "통계 카드",
    "money" to "가계부 카드", "memo" to "메모", "alpha" to "투명도", "theme" to "밝기", "snooze" to "다시 울림", "ring" to "울림 길이", "before" to "알람")

/** "items[name=야간].color" → "야간 · 색". */
private fun fieldName(path: String): String {
    val item = Regex("\\[[a-z]+=([^\\]]+)]").find(path)?.groupValues?.get(1)
    val leaf = path.substringAfterLast('.').substringBefore('[')
    return listOfNotNull(item, FIELD_WORDS[leaf] ?: leaf).joinToString(" · ")
}

private fun shortValue(v: Any?): String = when (v) {
    null -> "없음"
    is Boolean -> if (v) "켬" else "끔"
    is Long -> if (v > 0xFFFFFFL) "#%06X".format(v and 0xFFFFFF) else v.toString()
    is List<*> -> v.joinToString("·").take(24)
    else -> v.toString().take(24)
}

@Composable
private fun Header(t: String) = Text(t, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp).semantics { heading() })

@Composable
private fun Muted(t: String) = Text(t, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** [RCX-20] 1.35 끄기: 새 판 알림, 팔로우·답 알림, 요청 판, each on its own; this is where they come back on. */
@Composable
private fun NewsSwitches() {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    var newer by remember { mutableStateOf(c.recipes.newVersionNews()) }
    var follow by remember { mutableStateOf(c.recipes.followNews()) }
    val requests by c.recipes.requestsOn.collectAsStateWithLifecycle()
    @Composable fun line(title: String, what: String, value: Boolean, set: (Boolean) -> Unit) =
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f).padding(end = 12.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium)
                Text(what, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(value, set)
        }
    line("새 판 알림", "넣은 레시피에 새 판이 나오면 밤낮 소식으로 알려요. 레시피마다 끄려면 아래 받은 레시피에서", newer) {
        c.recipes.setNewVersionNews(it); newer = it
    }
    line("팔로우·답 알림", "팔로우한 분의 새 레시피와, 내 질문에 만든 분이 단 답을 알려요", follow) { c.recipes.setFollowNews(it); follow = it }
    line("요청 판 보이기", "마을 › 공방의 ‘이런 거 있었으면’ 칸에 ‘이런 레시피 있었으면’이 보여요", requests, c.recipes::setRequestsOn)
}

/**
 * [LIN-04] 1.35 수정판 of my recipe: how many others made from it, and how many came since I last looked (a dot), so I open it and
 * see what each changed (사용자 2026-10-30 "제작자는 해당 내용을 보고 수정을 할수 있게").
 */
@Composable
private fun RemixCount(code: String) {
    val c = androidx.compose.ui.platform.LocalContext.current.appContainer
    val prefs = remember { c.appContext.getSharedPreferences("recipe_remix_seen", android.content.Context.MODE_PRIVATE) }
    val kids by androidx.compose.runtime.produceState<Int?>(null, code) { value = runCatching { c.recipeApi.lineage(code).children.size }.getOrNull() }
    val n = kids ?: return
    if (n == 0) return
    val seen = remember(code) { prefs.getInt(code, 0) }
    androidx.compose.runtime.LaunchedEffect(n) { prefs.edit().putInt(code, n).apply() }
    Text("고쳐 만든 수정판 ${n}개" + (if (n > seen) " · 새로 ${n - seen}개" else "") + " · 열어서 달라진 점을 보고 가져올 수 있어요",
        style = MaterialTheme.typography.labelMedium, color = if (n > seen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
}
