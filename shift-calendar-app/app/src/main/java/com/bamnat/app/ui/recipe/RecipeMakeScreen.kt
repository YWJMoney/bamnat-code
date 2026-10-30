package com.bamnat.app.ui.recipe

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.bamnat.app.ui.components.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeLinks
import com.bamnat.app.recipe.Sent
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.recipe.Cover
import com.bamnat.core.recipe.InputExtract
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeDescribe
import com.bamnat.core.recipe.RecipeException
import com.bamnat.core.recipe.RecipeLint
import com.bamnat.core.recipe.RecipeText
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [RCP-17..20] 만들기 5단계: 고르기 → 받는 사람이 정할 칸 → 꾸미기 → 설명 → 검사하고 올리기. Every step is kept on the phone
 * (만들던 레시피), so closing the screen loses nothing. [from] is a recipe to 고쳐 만들기 (its code becomes the parent); [kind] a
 * piece to start with (설정 화면의 '이 설정 나누기').
 */
class RecipeMakeViewModel(
    private val c: AppContainer,
    val calendar: String = PersonalCalendar.MAIN,
    draftId: Long = 0,
    from: String? = null,
    parent: String? = null,
    kind: PieceKind? = null,
    /** The user's own recipe this becomes a new version of. */
    private val code: String? = null,
) : ViewModel() {
    val style: WorkStyle? = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    val canPublish: Boolean get() = c.recipeApi.enabled && c.flags.on(FlagCatalog.RECIPE_PUBLISH)

    data class State(
        val step: Int = 1,
        val recipe: Recipe,
        /** The pieces chosen in step 1 and what this phone has for each (null: nothing to share). */
        val chosen: Set<PieceKind> = emptySet(),
        val mine: Map<PieceKind, Map<String, Any?>?> = emptyMap(),
        val kept: Set<String> = emptySet(),
        val scope: String = "public",
        val agreed: Boolean = false,
        val sending: Boolean = false,
        val sent: Sent? = null,
        val error: String? = null,
        val draftId: Long = 0,
    )

    private val _state = MutableStateFlow(State(recipe = blank(), draftId = draftId))
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val mine = PieceKind.entries.associateWith { k -> runCatching { c.recipes.pieces.export(k, calendar) }.getOrNull() }
            val start = when {
                from != null -> lenient(from)?.copy(parent = parent)
                draftId != 0L -> c.database.recipeDao().drafts().firstOrNull { it.id == draftId }?.let { d ->
                    _state.value = _state.value.copy(step = d.step, scope = d.scope); lenient(d.body)
                }
                else -> null
            }
            val r = start ?: blank()
            val chosen = if (start != null) r.pieces.map { it.kind }.toSet() else setOfNotNull(kind)
            _state.value = _state.value.copy(recipe = if (start != null) r else withPieces(r, chosen, mine), chosen = chosen, mine = mine)
        }
    }

    private fun blank() = Recipe(title = "", styles = setOfNotNull(style ?: WorkStyle.SHIFT), pieces = emptyList(),
        cover = Cover(emoji = when (style) { WorkStyle.HOURLY -> "☕"; WorkStyle.FIELD -> "🛠️"; WorkStyle.BUSINESS -> "🏪"; WorkStyle.INVEST -> "📈"; WorkStyle.DAY -> "🗓️"; else -> "🌙" }))

    /** A draft or a remade recipe read as far as it goes: a piece that does not pass alone is left out. */
    @Suppress("UNCHECKED_CAST")
    private fun lenient(body: String): Recipe? = runCatching { RecipeCodec.read(body) }.getOrNull() ?: runCatching {
        val m = com.bamnat.core.remote.Json.parse(body) as Map<String, Any?>
        val pieces = (m["pieces"] as? List<Any?>).orEmpty().mapNotNull { p ->
            runCatching {
                RecipeCodec.clean(mapOf("format" to Recipe.FORMAT, "v" to 1L, "title" to "임시", "styles" to WorkStyle.entries.map { it.name },
                    "inputs" to m["inputs"], "tokens" to m["tokens"], "pieces" to listOf(p))).let { it.pieces.first() to it.inputs }
            }.getOrNull()
        }
        val cover = (m["cover"] as? Map<String, Any?>)?.let { Cover((it["a"] as? Long) ?: Cover().a, (it["b"] as? Long) ?: Cover().b,
            ((it["dir"] as? Long) ?: 0L).toInt(), (it["emoji"] as? String) ?: Cover().emoji,
            (it["image"] as? String)?.takeIf { id -> Cover.IMAGE.matches(id) }) } ?: Cover()
        Recipe(title = (m["title"] as? String).orEmpty(), summary = (m["summary"] as? String).orEmpty(), desc = (m["desc"] as? String).orEmpty(),
            styles = (m["styles"] as? List<Any?>).orEmpty().mapNotNull { s -> WorkStyle.entries.firstOrNull { it.name == s } }.toSet().ifEmpty { blank().styles },
            cover = cover, inputs = pieces.flatMap { it.second }.distinctBy { it.id }, pieces = pieces.map { it.first },
            parent = m["parent"] as? String)
    }.getOrNull()

    private fun withPieces(r: Recipe, chosen: Set<PieceKind>, mine: Map<PieceKind, Map<String, Any?>?>): Recipe {
        val kept = r.pieces.filter { it.kind in chosen }
        val added = chosen.filter { k -> kept.none { it.kind == k } }.mapNotNull { k -> mine[k]?.let { Piece(k, it.filterValues { v -> v != null }) } }
        // Every amount becomes 받는 사람이 정할 칸 first (S7); the maker can keep one on purpose in step 2.
        return InputExtract.extract(r.copy(pieces = (kept + added).sortedBy { it.kind.ordinal }), _state.value.kept) { _, f -> if (f == "wage") MIN_WAGE else null }
            .let { it.copy(inputs = it.inputs.map { i -> if (i.label == "시급" && i.default == MIN_WAGE) i.copy(hint = "처음 값은 2026 최저임금 ${MIN_WAGE}원이에요") else i }) }
    }

    fun toggle(kind: PieceKind) {
        val s = _state.value
        val chosen = if (kind in s.chosen) s.chosen - kind else s.chosen + kind
        _state.value = s.copy(chosen = chosen, recipe = withPieces(s.recipe, chosen, s.mine))
    }

    /** Step 2: an amount the same for everyone (회사 공통 정액) stays in the recipe as it is. */
    fun keep(inputId: String, value: Long) {
        val s = _state.value
        val i = s.recipe.input(inputId) ?: return
        val key = s.recipe.pieces.firstNotNullOfOrNull { p -> p.values.entries.firstOrNull { it.value == com.bamnat.core.recipe.Ref.Input(inputId) }?.let { "${p.kind.key}.${it.key}" } }
        _state.value = s.copy(recipe = InputExtract.keep(s.recipe, i.id, value), kept = s.kept + listOfNotNull(key))
    }

    fun edit(f: (Recipe) -> Recipe) { _state.value = _state.value.copy(recipe = f(_state.value.recipe)) }

    /** [RCX-11] 묶음: the codes of other recipes (2~8) as one piece; fewer than two takes the piece away. */
    fun bundle(codes: List<String>) {
        val list = codes.distinct().take(8)
        edit { r ->
            val rest = r.pieces.filter { it.kind != PieceKind.BUNDLE }
            r.copy(pieces = if (list.size < 2) rest else rest + Piece(PieceKind.BUNDLE, mapOf("items" to list.map { mapOf("code" to it) })))
        }
        _state.value = _state.value.copy(chosen = if (list.size < 2) _state.value.chosen - PieceKind.BUNDLE else _state.value.chosen + PieceKind.BUNDLE)
    }

    /** [RCX-15] 그림 표지: the picked photo made small and re-encoded on the phone (no EXIF), sent, and its id on the cover. */
    fun coverPhoto(bitmap: android.graphics.Bitmap?) {
        if (bitmap == null) { edit { it.copy(cover = it.cover.copy(image = null)) }; return }
        viewModelScope.launch {
            val webp = com.bamnat.app.recipe.CoverImages.encode(bitmap)
            if (webp == null) { _state.value = _state.value.copy(error = "사진이 너무 작거나 읽을 수 없어요"); return@launch }
            try {
                val id = c.recipeApi.image(c.companyStore.device, webp)
                android.graphics.BitmapFactory.decodeByteArray(webp, 0, webp.size)?.let { com.bamnat.app.recipe.CoverImages.put(id, it) }
                edit { it.copy(cover = it.cover.copy(image = id)) }
                _state.value = _state.value.copy(error = null)
            } catch (e: Exception) { _state.value = _state.value.copy(error = RecipeClient.problem(e)) }
        }
    }
    fun scope(v: String) { _state.value = _state.value.copy(scope = v) }
    fun agree(v: Boolean) { _state.value = _state.value.copy(agreed = v) }

    fun go(step: Int) {
        _state.value = _state.value.copy(step = step.coerceIn(1, 5), error = null)
        saveDraft()
    }

    private fun saveDraft() {
        val s = _state.value
        if (s.sent != null || s.recipe.pieces.isEmpty()) return
        viewModelScope.launch {
            val id = c.recipes.saveDraft(s.draftId, RecipeCodec.write(s.recipe), s.step, code, s.scope)
            _state.value = _state.value.copy(draftId = id)
        }
    }

    val lint: List<RecipeLint.Line> get() = RecipeLint.check(_state.value.recipe)

    fun fix(line: RecipeLint.Line) { line.fix?.let { f -> edit(f) } }

    /** Step 5: 광장 (첫 3개는 검토 뒤), 링크로만, 나만 (만든 사람만 열어요). */
    fun publish() {
        val s = _state.value
        if (!RecipeLint.ready(lint)) return
        _state.value = s.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                val r = RecipeCodec.read(RecipeCodec.write(s.recipe))
                val sent = if (code != null) c.recipeApi.newVersion(c.companyStore.device, code, r, s.scope)
                    else c.recipeApi.publish(c.companyStore.device, r, s.scope)
                if (s.draftId != 0L) c.recipes.deleteDraft(s.draftId)
                _state.value = _state.value.copy(sending = false, sent = sent)
            } catch (e: RecipeException) {
                _state.value = _state.value.copy(sending = false, error = RecipeText.problemText(e.code))
            } catch (e: Exception) {
                _state.value = _state.value.copy(sending = false, error = RecipeClient.problem(e))
            }
        }
    }

    companion object { const val MIN_WAGE = 10_320L }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeMakeScreen(vm: RecipeMakeViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    val titles = listOf("고르기", "받는 사람이 정할 칸", "꾸미기", "설명", "검사하고 올리기")
    Scaffold(topBar = {
        TopAppBar(title = { Text("레시피 만들기 ${s.step}/5 · ${titles[s.step - 1]}") },
            navigationIcon = { IconButton(onClick = { if (s.step > 1 && s.sent == null) vm.go(s.step - 1) else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { s.step / 5f }, modifier = Modifier.fillMaxWidth())
                s.sent?.let { sent -> SentCard(s.recipe.title, sent, onOpen); return@Column }
                when (s.step) {
                    1 -> StepPick(s, vm)
                    2 -> StepInputs(s, vm)
                    3 -> StepLook(s, vm)
                    4 -> StepWords(s, vm)
                    else -> StepCheck(s, vm)
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.step > 1) OutlinedButton(onClick = { vm.go(s.step - 1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이전") }
                    Spacer(Modifier.weight(1f))
                    if (s.step < 5) Button(onClick = { vm.go(s.step + 1) }, enabled = s.recipe.pieces.isNotEmpty() && (s.step != 3 || s.recipe.title.isNotBlank()),
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("다음") }
                }
                Text("만들던 내용은 이 휴대폰에 저장돼요. 설정 › 나눔 판에서 이어서 만들 수 있어요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun StepPick(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    Text("내 설정에서 나눌 것을 골라요. 근무 기록·가계부·메모 글은 들어가지 않아요", style = MaterialTheme.typography.bodyMedium)
    val fits = PieceKind.entries.filter { it != PieceKind.BUNDLE && (vm.style == null || vm.style in it.styles) }
    var more by remember { mutableStateOf(false) }
    (if (more) PieceKind.entries.filter { it != PieceKind.BUNDLE } else fits).forEach { k ->
        val mine = s.mine[k]
        Row(Modifier.fillMaxWidth().selectable(selected = k in s.chosen, enabled = mine != null, role = Role.Checkbox) { vm.toggle(k) }.padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Checkbox(k in s.chosen, null, enabled = mine != null)
            Column(Modifier.weight(1f)) {
                Text(k.label, style = MaterialTheme.typography.bodyLarge)
                Text(mine?.let { RecipeDescribe.line(Piece(k, it.filterValues { v -> v != null }), amounts = false) } ?: "지금 나눌 설정이 없어요",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    if (fits.size < PieceKind.entries.size) TextButton(onClick = { more = !more }) { Text(if (more) "내 유형 조각만 보기" else "다른 유형 조각도 보기") }
    // [RCX-11] 묶음: other recipes by their codes or links, put in one after another by whoever takes this one.
    val bundled = s.recipe.pieces.firstOrNull { it.kind == PieceKind.BUNDLE }?.let { p ->
        @Suppress("UNCHECKED_CAST") (p.values["items"] as? List<Map<String, Any?>>).orEmpty().mapNotNull { it["code"] as? String }
    }.orEmpty()
    var codes by remember { mutableStateOf(bundled.joinToString(" ")) }
    Text("레시피 묶기 (선택)", style = MaterialTheme.typography.titleSmall)
    OutlinedTextField(codes, { t -> codes = t; vm.bundle(Regex("[2-9A-HJ-NP-Za-hj-np-z]{8}|https?://\\S+").findAll(t).mapNotNull { m -> com.bamnat.app.recipe.RecipeLinks.find(m.value) }.toList()) },
        label = { Text("다른 레시피 코드나 링크 2~8개") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Text(if (bundled.size >= 2) "묶음 ${bundled.size}개: 받는 분이 하나씩 열어 골라 넣어요" else "예: 근무 색 레시피와 알람 레시피를 한 번에 나누기",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepInputs(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    Text("받는 분이 자기 값으로 채우는 칸이에요. 금액은 처음부터 칸으로 바뀌어서 내 금액은 나가지 않아요", style = MaterialTheme.typography.bodyMedium)
    if (s.recipe.inputs.isEmpty()) Text("정할 칸이 없어요. 그대로 다음으로 가도 돼요", style = MaterialTheme.typography.bodySmall)
    s.recipe.inputs.forEach { i ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(i.label, { t -> vm.edit { r -> r.copy(inputs = r.inputs.map { if (it.id == i.id) it.copy(label = t.take(20)) else it }) } },
                    label = { Text("칸 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                InputField(i, null, { v -> vm.edit { r -> r.copy(inputs = r.inputs.map { if (it.id == i.id) it.copy(default = v, required = v == null && it.required) else it }) } })
                if (i.kind == com.bamnat.core.recipe.InputKind.MONEY) {
                    var asking by remember { mutableStateOf(false) }
                    TextButton(onClick = { asking = true }) { Text("모두에게 같은 금액이에요 (회사 공통 정액)") }
                    if (asking) AlertDialog(onDismissRequest = { asking = false }, title = { Text("이 금액을 레시피에 그대로 둘까요?") },
                        text = { Text("받는 분 모두에게 같은 금액일 때만 두세요. 내 시급·일당이면 칸으로 두는 게 안전해요") },
                        confirmButton = { TextButton(onClick = { (i.default as? Long)?.let { vm.keep(i.id, it) }; asking = false }, enabled = i.default is Long) { Text("그대로 두기") } },
                        dismissButton = { TextButton(onClick = { asking = false }) { Text("칸으로 두기") } })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StepLook(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    val r = s.recipe
    OutlinedTextField(r.title, { t -> vm.edit { it.copy(title = t.take(30)) } }, label = { Text("이름 (30자까지)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Row(verticalAlignment = Alignment.CenterVertically) {
        RecipeCover(r.cover, 72.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            ColorSwatch(r.cover.a, "첫째 색", { c -> vm.edit { it.copy(cover = it.cover.copy(a = c)) } })
            ColorSwatch(r.cover.b, "둘째 색", { c -> vm.edit { it.copy(cover = it.cover.copy(b = c)) } })
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("위→아래", "왼→오", "대각선 ↘", "대각선 ↙").forEachIndexed { i, label ->
            FilterChip(r.cover.dir == i, { vm.edit { it.copy(cover = it.cover.copy(dir = i)) } }, label = { Text(label) })
        }
    }
    var pick by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { pick = true }) { Text("표지 이모지: ${r.cover.emoji}") }
    // [RCX-15] 사진 표지: made small and written again on this phone (위치·기기 정보 없이), shown once the operator looked.
    val context = androidx.compose.ui.platform.LocalContext.current
    val photo = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.coverPhoto(runCatching {
            context.contentResolver.openInputStream(uri)?.use { android.graphics.BitmapFactory.decodeStream(it) }
        }.getOrNull())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = {
            photo.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly))
        }, enabled = vm.canPublish) { Text(if (r.cover.image == null) "사진으로 표지" else "사진 바꾸기") }
        if (r.cover.image != null) TextButton(onClick = { vm.coverPhoto(null) }) { Text("사진 빼기") }
    }
    Text(if (vm.canPublish) "사진은 작게 줄이고 위치·기기 정보를 지운 뒤 보내요. 운영자가 본 뒤 다른 분께 보여요"
        else "사진 표지는 처리방침 3.8이 시행되는 11월 29일부터 돼요", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (pick) EmojiPickerDialog(onPick = { e -> vm.edit { it.copy(cover = it.cover.copy(emoji = e)) }; pick = false }, onDismiss = { pick = false })
    ThemeTokens(r) { f -> vm.edit(f) }
    Text("맞는 일 유형", style = MaterialTheme.typography.labelLarge)
    val fits = r.pieces.flatMap { it.kind.styles }.toSet()
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        WorkStyle.entries.filter { it in fits }.forEach { st ->
            FilterChip(st in r.styles, { vm.edit { it.copy(styles = if (st in it.styles && it.styles.size > 1) it.styles - st else it.styles + st) } },
                label = { Text(RecipeDescribe.short(st)) })
        }
    }
}

@Composable
private fun StepWords(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    val r = s.recipe
    OutlinedTextField(r.summary, { t -> vm.edit { it.copy(summary = t.take(60)) } }, label = { Text("한 줄 설명 (60자까지)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
    OutlinedTextField(r.desc, { t -> vm.edit { it.copy(desc = t.take(600)) } }, label = { Text("설명") }, minLines = 5, modifier = Modifier.fillMaxWidth(),
        placeholder = { Text("누구에게 좋아요:\n적용하면 바뀌는 것:\n주의할 점:") })
    if (r.desc.isEmpty()) TextButton(onClick = { vm.edit { it.copy(desc = "누구에게 좋아요: \n적용하면 바뀌는 것: \n주의할 점: ") } }) { Text("틀 채우기") }
    Text("사람 이름·연락처·가게 이름은 적지 않아요. 걸리면 다음 단계에서 알려 드려요", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun StepCheck(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    val lines = vm.lint
    lines.forEach { l ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(when (l.level) { RecipeLint.Level.OK -> "✓"; RecipeLint.Level.WARN -> "!"; RecipeLint.Level.ERROR -> "✕" },
                color = when (l.level) { RecipeLint.Level.OK -> MaterialTheme.colorScheme.primary; RecipeLint.Level.WARN -> MaterialTheme.colorScheme.tertiary; RecipeLint.Level.ERROR -> MaterialTheme.colorScheme.error },
                modifier = Modifier.width(24.dp))
            Text(l.text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            if (l.fix != null) TextButton(onClick = { vm.fix(l) }) { Text("고치기") }
            else if (l.level != RecipeLint.Level.OK) TextButton(onClick = { vm.go(l.step) }) { Text("가기") }
        }
    }
    ExampleCalendars(s.recipe)
    Text("어디에 둘까요?", style = MaterialTheme.typography.titleSmall)
    listOf("public" to "광장에 올리기 (처음 3개는 운영자가 보고 올려요)", "link" to "링크로만 (링크를 받은 분만 열어요)", "private" to "나만 (내 휴대폰들에서만 열어요)").forEach { (k, label) ->
        Row(Modifier.fillMaxWidth().selectable(s.scope == k, role = Role.RadioButton) { vm.scope(k) }, verticalAlignment = Alignment.CenterVertically) {
            RadioButton(s.scope == k, null); Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }
    var all by remember { mutableStateOf(false) }
    TextButton(onClick = { all = !all }) { Text(if (all) "접기" else "올라갈 내용 전부 보기") }
    if (all) Text(RecipeDescribe.changes(s.recipe) + "\n\n" + RecipeCodec.write(s.recipe), style = MaterialTheme.typography.bodySmall)
    if (s.scope != "private") Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(s.agreed, vm::agree)
        Spacer(Modifier.width(8.dp))
        Text("값만 나누고 개인 정보는 넣지 않았어요. 신고가 쌓이면 가려질 수 있어요 (처리방침 3.8)", style = MaterialTheme.typography.bodySmall)
    }
    if (!vm.canPublish) Text("올리기는 처리방침 3.8이 시행되는 11월 29일부터 열려요. 그동안 만들던 것으로 이 휴대폰에 저장돼요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = vm::publish, enabled = RecipeLint.ready(lines) && (s.agreed || s.scope == "private") && vm.canPublish && !s.sending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (s.sending) "올리는 중…" else if (s.scope == "private") "나만 보기로 저장" else "올리기") }
}

@Composable
private fun SentCard(title: String, sent: Sent, onOpen: (String) -> Unit) {
    val context = LocalContext.current
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("올렸어요", style = MaterialTheme.typography.titleMedium)
            Text(stateLabel(sent.state, sent.scope), style = MaterialTheme.typography.bodyMedium)
            Text("코드 ${sent.code} · v${sent.version}", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, RecipeLinks.shareText(title, sent.code))
                    runCatching { context.startActivity(Intent.createChooser(send, "레시피 링크 보내기")) }
                }) { Text("링크 보내기") }
                Button(onClick = { onOpen(sent.code) }) { Text("레시피 보기") }
            }
        }
    }
}
