package com.bamnat.app.ui.recipe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import com.bamnat.app.ui.components.AlertDialog
import androidx.compose.material3.AssistChip
import com.bamnat.app.ui.components.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.MakerPage
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeRequest
import com.bamnat.app.ui.components.asColor
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// [RCX-05] 1.35 제작자 페이지 → [PRF-01] 1.36 창작자 프로필: MakerViewModel and MakerScreen are in ProfileScreens.kt.

/** The face of a maker: their emoji on their colour, or the first letter of their nickname. */
@Composable
fun MakerFace(face: String?, color: Long?, nick: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color?.asColor() ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center) {
        Text(face ?: nick?.take(1) ?: "🙂", fontSize = (size.value * 0.5f).sp)
    }
}

// ---- [RCX-04] 요청 판 ----

class RequestsViewModel(private val c: AppContainer, val calendar: String = PersonalCalendar.MAIN) : ViewModel() {
    private val device get() = c.companyStore.device
    val style: WorkStyle? = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    val canWrite: Boolean get() = c.flags.on(FlagCatalog.RECIPE_TALK)

    data class State(val items: List<RecipeRequest>? = null, val error: String? = null, val message: String? = null,
        val mine: List<com.bamnat.app.recipe.RecipeItem> = emptyList())
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { c.recipeApi.requests(device, style) }
                .fold({ _state.value.copy(items = it, error = null) }, { _state.value.copy(error = RecipeClient.problem(it)) })
        }
    }

    fun write(body: String, done: (Boolean) -> Unit) {
        val st = style ?: WorkStyle.SHIFT
        viewModelScope.launch {
            runCatching { c.recipeApi.request(device, st, body.trim()) }
                .onSuccess { load(); _state.value = _state.value.copy(message = "올렸어요. 좋아요가 많으면 만드는 분이 먼저 봐요"); done(true) }
                .onFailure { _state.value = _state.value.copy(message = RecipeClient.problem(it)); done(false) }
        }
    }

    fun act(id: String, what: String, on: Boolean = true, code: String? = null) {
        viewModelScope.launch {
            runCatching { c.recipeApi.requestAct(device, id, what, on, code) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(message = when (what) {
                        "take" -> if (on) "만들기로 했어요. 다 만들면 ‘완성’을 눌러 레시피를 이어 주세요" else "만들기를 내려놨어요"
                        "done" -> "완성했어요. 요청한 분이 바로 볼 수 있어요"
                        "delete" -> "지웠어요"
                        else -> null
                    })
                }
                .onFailure { _state.value = _state.value.copy(message = RecipeClient.problem(it)) }
        }
    }

    /** My recipes on the plaza, to say which one answers a request. */
    fun loadMine() {
        viewModelScope.launch {
            runCatching { c.recipeApi.mine(device) }.onSuccess { m ->
                _state.value = _state.value.copy(mine = m.items.filter { it.second.second != "private" && it.second.first != "deleted" }.map { it.first })
            }
        }
    }

    fun shown() { _state.value = _state.value.copy(message = null) }
}

/** 1.36 [VIL-15] Words to start 요청 쓰기 with, from a plaza question (광장 › 장터에 요청으로 올리기); taken once. */
object RequestDraft {
    @Volatile var text: String? = null
}

private fun stateWord(s: String) = when (s) { "making" -> "만드는 중"; "done" -> "완성"; else -> "기다림" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(vm: RequestsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onMake: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    // 1.36 [VIL-15] Opened from a plaza question: 요청 쓰기 starts with its words.
    val draft = remember { RequestDraft.text.also { RequestDraft.text = null } }
    var writing by remember { mutableStateOf(draft != null && vm.canWrite) }
    var finishing by remember { mutableStateOf<String?>(null) }
    // 1.36 [GRW-03] PC 6ec395bf: 완성 전에 그 작품이 이 부탁에 맞는지 한 번 보고 정해요(부탁과 상관없는 작품으로 잘못 잇지 않게).
    var confirming by remember { mutableStateOf<Pair<com.bamnat.app.recipe.RecipeRequest, com.bamnat.app.recipe.RecipeItem>?>(null) }
    // 1.36 [VIL-15] While I make something for a request, my newest recipe is offered to finish it with in one tap.
    val taking = s.items.orEmpty().any { it.taking }
    LaunchedEffect(taking) { if (taking) vm.loadMine() }
    var deleting by remember { mutableStateOf<String?>(null) }
    var takingAsk by remember { mutableStateOf<String?>(null) }
    // PC 확인 20ab0254: cleared after it was shown; cleared first, the key changed and took the snackbar down at once.
    LaunchedEffect(s.message) { s.message?.let { snack.showSnackbar(it); vm.shown() } }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = {
            TopAppBar(title = { Text("요청 판" + (vm.style?.let { " · " + RecipeDescribe.short(it) } ?: "")) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("‘이런 레시피 있었으면’을 적으면 좋아요가 모이고, 만들 분이 ‘만들게요’를 눌러 완성하면 그 레시피로 이어져요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (vm.canWrite) Button(onClick = { writing = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("요청 쓰기") }
                else Text("요청 쓰기·좋아요는 정식 출시일부터 돼요", style = MaterialTheme.typography.bodySmall)
                val items = s.items
                when {
                    items == null && s.error != null -> { Text(s.error!!); TextButton(onClick = vm::load) { Text("다시 해 보기") } }
                    items == null -> Text("불러오는 중이에요")
                    items.isEmpty() -> Text("아직 요청이 없어요. 바라는 레시피를 처음 적어 주세요", style = MaterialTheme.typography.bodyMedium)
                    else -> items.forEach { q ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(q.body, style = MaterialTheme.typography.bodyLarge)
                                Text("${stateWord(q.state)}" + (if (q.askers > 0) " · 스튜디오에서 ${q.askers}명이 부탁" else "") + " · 좋아요 ${q.likes}" + (if (q.mine) " · 내 요청" else "") + (if (q.taking) (if (q.state == "done") " · 내가 만든 작품" else " · 내가 만드는 중") else ""),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                // 1.36 누락 점검 N14 (1차 사용성 제안): 14일 동안 아무도 못 만든 요청은 이유와 다음 일을 말해요.
                                if (q.told && q.state == "waiting") Text("스튜디오로 아직 못 만드는 것일 수 있어요. 개발자에게 알렸어요. 만들 수 있게 되면 이 요청에 알려 드려요",
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!q.mine && vm.canWrite) FilterChip(q.liked, onClick = { vm.act(q.id, "like", !q.liked) }, label = { Text("👍 나도 바라요") })
                                    when {
                                        q.state == "done" && q.code != null -> TextButton(onClick = { onOpen(q.code) }) { Text("완성된 레시피 보기") }
                                        q.taking && q.state == "making" -> {
                                            TextButton(onClick = onMake) { Text("스튜디오에서 만들기") }
                                            TextButton(onClick = { vm.loadMine(); finishing = q.id }) { Text("완성") }
                                            TextButton(onClick = { vm.act(q.id, "take", false) }) { Text("내려놓기") }
                                        }
                                        q.state == "waiting" && !q.mine && vm.canWrite -> TextButton(onClick = { takingAsk = q.id }) { Text("만들게요") }
                                    }
                                    if (q.mine) TextButton(onClick = { deleting = q.id }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
                                }
                                // 1.36 [VIL-15] 올리고 돌아오면 바로 잇기: my newest public recipe, one tap.
                                if (q.taking && q.state == "making") s.mine.firstOrNull()?.let { r ->
                                    OutlinedButton(onClick = { confirming = q to r }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                                        Text("방금 올린 ‘${r.title}’${com.bamnat.app.ui.Fmt.ro("‘${r.title}’")} 완성하기", maxLines = 1)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    // 1.36 [VIL-15] 만들게요 says what it means before taking: no deadline, 내려놓기 any time.
    takingAsk?.let { id ->
        AlertDialog(onDismissRequest = { takingAsk = null }, title = { Text("이 요청을 맡을까요?") },
            text = { Text("요청한 분에게 \"만드는 중\"으로 보여요. 마감은 없고, 못 하게 되면 언제든 내려놓기를 누르면 다른 분이 맡을 수 있어요. 스튜디오에서 만들어 올린 뒤 이 화면에서 완성을 눌러 이어 주세요.") },
            confirmButton = { TextButton(onClick = { vm.act(id, "take"); takingAsk = null }) { Text("만들게요") } },
            dismissButton = { TextButton(onClick = { takingAsk = null }) { Text("취소") } })
    }
    // [RCX-26] 요청 지우기 asks first.
    deleting?.let { id ->
        AlertDialog(onDismissRequest = { deleting = null }, title = { Text("요청을 지울까요?") },
            text = { Text("모인 좋아요도 함께 사라지고 되돌릴 수 없어요") },
            confirmButton = { TextButton(onClick = { deleting = null; vm.act(id, "delete") }) { Text("지우기", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("닫기") } })
    }
    if (writing) {
        var text by remember { mutableStateOf(draft?.take(200).orEmpty()) }
        // 1.39 발굴(마을 돌봄): 요청 글도 거친 말이면 올리기 전에 한 번 물어요(막지 않아요).
        var nudge by remember { mutableStateOf(false) }
        val go = { vm.write(text) { ok -> if (ok) writing = false } }
        if (nudge) com.bamnat.app.ui.hub.NudgeDialog(onEdit = { nudge = false }, onSend = { nudge = false; go() })
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("이런 레시피 있었으면") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(text, { if (it.length <= 200) text = it }, label = { Text("예: 간호사 3교대 근무별 알람 세트") }, minLines = 2)
                    Text("금액·연락처·링크는 적지 않아요 (5~200자)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = text.trim().length >= 5, onClick = { if (com.bamnat.core.plaza.PostNudge.needed(text)) nudge = true else go() }) { Text("올리기") } },
            dismissButton = { TextButton(onClick = { writing = false }) { Text("닫기") } },
        )
    }
    confirming?.let { (q, r) ->
        AlertDialog(
            onDismissRequest = { confirming = null },
            title = { Text("이 부탁에 맞나요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("부탁: ${q.body}", style = MaterialTheme.typography.bodyMedium)
                    Text("완성할 작품: ‘${r.title}’ (${r.code})", style = MaterialTheme.typography.bodyMedium)
                    Text("완성하면 부탁한 분들께 이 작품이 알려져요. 맞는지 먼저 열어 볼 수 있어요", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { onOpen(r.code) }) { Text("작품 먼저 보기") }
                    if (s.mine.size > 1) TextButton(onClick = { confirming = null; vm.loadMine(); finishing = q.id }) { Text("다른 작품 고르기") }
                }
            },
            confirmButton = { TextButton(onClick = { confirming = null; vm.act(q.id, "done", code = r.code) }) { Text("맞아요, 완성") } },
            dismissButton = { TextButton(onClick = { confirming = null }) { Text("닫기") } },
        )
    }
    finishing?.let { id ->
        AlertDialog(
            onDismissRequest = { finishing = null },
            title = { Text("어떤 레시피로 완성했나요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.mine.isEmpty()) Text("올린 레시피가 아직 없어요. 먼저 만들어 올려 주세요")
                    s.mine.forEach { r -> TextButton(onClick = { finishing = null; s.items.orEmpty().firstOrNull { it.id == id }?.let { q -> confirming = q to r } }) { Text(r.title) } }
                }
            },
            confirmButton = { TextButton(onClick = { finishing = null; onMake() }) { Text("지금 만들기") } },
            dismissButton = { TextButton(onClick = { finishing = null }) { Text("닫기") } },
        )
    }
}
