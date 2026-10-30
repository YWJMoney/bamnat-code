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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
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

// ---- [RCX-05][RCX-06][RCX-07] 제작자 페이지 ----

class MakerViewModel(private val c: AppContainer, val id: String) : ViewModel() {
    private val device get() = c.companyStore.device
    val canFollow: Boolean get() = c.flags.on(FlagCatalog.RECIPE_TALK)

    data class State(val page: MakerPage? = null, val error: String? = null, val message: String? = null)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = runCatching { c.recipeApi.maker(device, id) }
                .fold({ _state.value.copy(page = it, error = null) }, { _state.value.copy(error = RecipeClient.problem(it)) })
        }
    }

    fun follow(on: Boolean) {
        viewModelScope.launch {
            runCatching { c.recipeApi.follow(device, id, on) }
                .onSuccess {
                    load()
                    _state.value = _state.value.copy(message = if (on) "팔로우했어요. 새 레시피가 올라오면 밤낮 소식으로 알려 드려요" else "팔로우를 그만뒀어요")
                }
                .onFailure { _state.value = _state.value.copy(message = RecipeClient.problem(it)) }
        }
    }

    fun face(emoji: String?, color: Long?) {
        viewModelScope.launch {
            runCatching { c.recipeApi.makerMe(device, emoji, color, set = true) }
                .onSuccess { load() }.onFailure { _state.value = _state.value.copy(message = RecipeClient.problem(it)) }
        }
    }

    fun shown() { _state.value = _state.value.copy(message = null) }
}

/** The face of a maker: their emoji on their colour, or the first letter of their nickname. */
@Composable
fun MakerFace(face: String?, color: Long?, nick: String?, size: androidx.compose.ui.unit.Dp = 56.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(color?.asColor() ?: MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center) {
        Text(face ?: nick?.take(1) ?: "🙂", fontSize = (size.value * 0.5f).sp)
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MakerScreen(vm: MakerViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var picking by remember { mutableStateOf(false) }
    var coloring by remember { mutableStateOf(false) }
    LaunchedEffect(s.message) { s.message?.let { vm.shown(); snack.showSnackbar(it) } }
    Scaffold(
        snackbarHost = { SnackbarHost(snack) },
        topBar = { TopAppBar(title = { Text(s.page?.nick ?: "만든 분") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } }) },
    ) { padding ->
        val p = s.page
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (p == null) {
                    Text(s.error ?: "불러오는 중이에요", style = MaterialTheme.typography.bodyMedium)
                    if (s.error != null) TextButton(onClick = vm::load) { Text("다시 해 보기") }
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MakerFace(p.face, p.color, p.nick, 72.dp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.nick ?: "이름 없는 분", style = MaterialTheme.typography.titleLarge)
                        Text("레시피 ${p.recipes.size}개 · 받은 좋아요 ${p.likes} · 쓰는 분 ${p.users}명 · 팔로워 ${p.followers}",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (p.badges.isNotEmpty()) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    p.badges.forEach { (_, name) -> AssistChip(onClick = {}, label = { Text("🏅 $name") }) }
                }
                if (p.mine) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { picking = true }) { Text("얼굴 이모지 바꾸기") }
                    OutlinedButton(onClick = { coloring = true }) { Text("바탕색 바꾸기") }
                } else if (vm.canFollow) {
                    if (p.following) OutlinedButton(onClick = { vm.follow(false) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("팔로우 그만두기") }
                    else Button(onClick = { vm.follow(true) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("팔로우 (새 레시피 소식 받기)") }
                } else Text("팔로우는 처리방침 3.8이 시행되는 11월 29일부터 돼요", style = MaterialTheme.typography.bodySmall)
                Text("만든 레시피", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                if (p.recipes.isEmpty()) Text("광장에 올린 레시피가 아직 없어요", style = MaterialTheme.typography.bodySmall)
                p.recipes.forEach { r -> RecipeRow(r) { onOpen(r.code) } }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (picking) EmojiPickerDialog(onPick = { e -> picking = false; vm.face(e, s.page?.color) }, onDismiss = { picking = false })
    if (coloring) ColorPickerDialog(initial = s.page?.color ?: 0xFF5C6BC0L, onPick = { col -> coloring = false; vm.face(s.page?.face, col) },
        onDismiss = { coloring = false })
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

private fun stateWord(s: String) = when (s) { "making" -> "만드는 중"; "done" -> "완성"; else -> "기다림" }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestsScreen(vm: RequestsViewModel, onBack: () -> Unit, onOpen: (String) -> Unit, onMake: () -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val snack = remember { SnackbarHostState() }
    var writing by remember { mutableStateOf(false) }
    var finishing by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(s.message) { s.message?.let { vm.shown(); snack.showSnackbar(it) } }
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
                else Text("요청 쓰기·좋아요는 처리방침 3.8이 시행되는 11월 29일부터 돼요", style = MaterialTheme.typography.bodySmall)
                val items = s.items
                when {
                    items == null && s.error != null -> { Text(s.error!!); TextButton(onClick = vm::load) { Text("다시 해 보기") } }
                    items == null -> Text("불러오는 중이에요")
                    items.isEmpty() -> Text("아직 요청이 없어요. 바라는 레시피를 처음 적어 주세요", style = MaterialTheme.typography.bodyMedium)
                    else -> items.forEach { q ->
                        Card(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(q.body, style = MaterialTheme.typography.bodyLarge)
                                Text("${stateWord(q.state)} · 좋아요 ${q.likes}" + (if (q.mine) " · 내 요청" else "") + (if (q.taking) " · 내가 만드는 중" else ""),
                                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    if (!q.mine && vm.canWrite) FilterChip(q.liked, onClick = { vm.act(q.id, "like", !q.liked) }, label = { Text("👍 나도 바라요") })
                                    when {
                                        q.state == "done" && q.code != null -> TextButton(onClick = { onOpen(q.code) }) { Text("완성된 레시피 보기") }
                                        q.taking -> {
                                            TextButton(onClick = { vm.loadMine(); finishing = q.id }) { Text("완성") }
                                            TextButton(onClick = { vm.act(q.id, "take", false) }) { Text("내려놓기") }
                                        }
                                        q.state == "waiting" && !q.mine && vm.canWrite -> TextButton(onClick = { vm.act(q.id, "take") }) { Text("만들게요") }
                                    }
                                    if (q.mine) TextButton(onClick = { vm.act(q.id, "delete") }) { Text("지우기", color = MaterialTheme.colorScheme.error) }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    if (writing) {
        var text by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { writing = false },
            title = { Text("이런 레시피 있었으면") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(text, { if (it.length <= 200) text = it }, label = { Text("예: 간호사 3교대 근무별 알람 세트") }, minLines = 2)
                    Text("금액·연락처·링크는 적지 않아요 (5~200자)", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = text.trim().length >= 5, onClick = { vm.write(text) { ok -> if (ok) writing = false } }) { Text("올리기") } },
            dismissButton = { TextButton(onClick = { writing = false }) { Text("닫기") } },
        )
    }
    finishing?.let { id ->
        AlertDialog(
            onDismissRequest = { finishing = null },
            title = { Text("어떤 레시피로 완성했나요?") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (s.mine.isEmpty()) Text("올린 레시피가 아직 없어요. 먼저 만들어 올려 주세요")
                    s.mine.forEach { r -> TextButton(onClick = { finishing = null; vm.act(id, "done", code = r.code) }) { Text(r.title) } }
                }
            },
            confirmButton = { TextButton(onClick = { finishing = null; onMake() }) { Text("지금 만들기") } },
            dismissButton = { TextButton(onClick = { finishing = null }) { Text("닫기") } },
        )
    }
}
