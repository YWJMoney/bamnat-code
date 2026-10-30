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
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.foundation.layout.Box
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
    /** [RCX-31] Making the next 판 of this code of mine; null for a new recipe. */
    val ownCode: String? get() = code

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
        /** [RCX-11] Recipes to tap into a 묶음 without typing codes: those put on this phone and those I put on the plaza (code to title). */
        val bundleChoices: List<Pair<String, String>> = emptyList(),
        /** [STU-02] 1.35 보여주기 판 to start the studio from (its code becomes the parent). */
        val showcases: List<com.bamnat.app.recipe.RecipeItem> = emptyList(),
        /** [STU-05] Put on this phone only (내 앱에만 넣기): the record 되돌리기 reads. */
        val putMine: Long? = null,
        /** [LIN-01] The recipe this one is a 수정판 of, as its title (for the line that says so). */
        val parentTitle: String? = null,
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
            // [RCX-31] A draft from before kept 최저임금 with its amount in the hint, which the amount check then refused.
            val r = (start ?: blank()).let { d -> d.copy(inputs = d.inputs.map { i -> if (i.hint.contains("최저임금") && i.hint.any(Char::isDigit)) i.copy(hint = WAGE_HINT) else i }) }
            val chosen = if (start != null) r.pieces.map { it.kind }.toSet() else setOfNotNull(kind)
            _state.value = _state.value.copy(recipe = if (start != null) r else withPieces(r, chosen, mine), chosen = chosen, mine = mine)
            val put = runCatching { c.recipes.inUse().map { it.code to it.title } }.getOrDefault(emptyList())
            val mineUp = runCatching { c.recipeApi.mine(c.companyStore.device).items.map { it.first.code to it.first.title } }.getOrDefault(emptyList())
            _state.value = _state.value.copy(bundleChoices = (mineUp + put).filter { it.first != code }.distinctBy { it.first }.take(20))
            val shows = runCatching { c.recipeApi.list(style, "showcase").items.filter { it.showcase } }.getOrDefault(emptyList())
            _state.value = _state.value.copy(showcases = shows)
            _state.value.recipe.parent?.let { p -> parentTitleOf(p) }
        }
    }

    private suspend fun parentTitleOf(code: String) {
        val t = runCatching { c.recipeApi.show(code, null).item.title }.getOrNull() ?: _state.value.showcases.firstOrNull { it.code == code }?.title
        _state.value = _state.value.copy(parentTitle = t)
    }

    // ---- [STU-01]..[STU-05] 1.35 꾸미기 스튜디오 ----

    /** Start from a 보여주기 판 (or any recipe on the plaza): its pieces and look, with it as the parent; the name is the maker's to give. */
    fun startFrom(item: com.bamnat.app.recipe.RecipeItem) = viewModelScope.launch {
        val r = runCatching { c.recipeApi.show(item.code, c.companyStore.device).recipe() }.getOrNull() ?: run {
            _state.value = _state.value.copy(error = "판을 받지 못했어요. 인터넷을 확인해 주세요"); return@launch
        }
        _state.value = _state.value.copy(recipe = r.copy(title = "", parent = item.code), chosen = r.pieces.map { it.kind }.toSet(),
            parentTitle = item.title, error = null)
    }

    /**
     * Start from what this phone has now: every piece it can share for this kind of work. When a recipe I put on is still in use, the
     * new one is its 수정판 (사용자 2026-10-30 "공유를 한다면 거져온 판의 수정판으로").
     */
    fun startMine() = viewModelScope.launch {
        val s = _state.value
        val fits = PieceKind.entries.filter { it != PieceKind.BUNDLE && it != PieceKind.COMPANY && (style == null || style in it.styles) && s.mine[it] != null }.toSet()
        val from = runCatching { c.recipes.inUse().maxByOrNull { it.appliedAt } }.getOrNull()
        val r = withPieces(blank().copy(parent = from?.code), fits, s.mine)
        _state.value = s.copy(recipe = r, chosen = fits, parentTitle = from?.title, error = null)
    }

    /** Start from nothing: the example app shows the app's own look until a piece is turned on. */
    fun startBlank() { _state.value = _state.value.copy(recipe = blank(), chosen = emptySet(), parentTitle = null, error = null) }

    /** A piece on with [values], or taken away (null). */
    fun setPiece(kind: PieceKind, values: Map<String, Any?>?) {
        val s = _state.value
        val rest = s.recipe.pieces.filter { it.kind != kind }
        val pieces = if (values == null) rest else (rest + Piece(kind, values.filterValues { it != null })).sortedBy { it.kind.ordinal }
        _state.value = s.copy(recipe = s.recipe.copy(pieces = pieces), chosen = pieces.map { it.kind }.toSet())
    }

    /** One field of [kind]: the piece is made when it is not there yet; null takes the field away. */
    fun setValue(kind: PieceKind, key: String, value: Any?) {
        val now = _state.value.recipe.pieces.firstOrNull { it.kind == kind }?.values ?: emptyMap()
        val next = if (value == null) now - key else now + (key to value)
        setPiece(kind, next.takeIf { it.isNotEmpty() || kind.fields.none { f -> !f.optional } })
    }

    /** What a piece starts with when it is turned on: what this phone has, or example values that pass the catalog. */
    fun starter(kind: PieceKind): Map<String, Any?> = _state.value.mine[kind]?.filterValues { it != null } ?: StudioStarters.of(kind, style)

    /** [STU-05] 내 앱에만 넣기: put the recipe on this phone without sending it; 되돌리기 works as for any recipe for 7 days. */
    fun putOnMine() = viewModelScope.launch {
        val s = _state.value
        val picks = s.recipe.pieces.filter { it.kind != PieceKind.BUNDLE }.map { com.bamnat.app.recipe.RecipeRepository.Pick(it.kind, com.bamnat.app.recipe.RecipePieces.Mode.REPLACE) }
        val answers = s.recipe.inputs.associate { it.id to it.default }
        val id = runCatching { c.recipes.apply(s.recipe.copy(title = s.recipe.title.ifBlank { "내가 만든 판" }), "", 0, calendar, picks, answers) }.getOrNull()
        _state.value = _state.value.copy(putMine = id ?: -1L)
        saveDraft()
    }

    /** [STU-05] 만들기만: kept as a draft on this phone, to finish or put on later. */
    fun keepDraft() = saveDraft()

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
            .let { it.copy(inputs = it.inputs.map { i -> if (i.label == "시급" && i.default == MIN_WAGE) i.copy(hint = WAGE_HINT) else i }) }
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
            val titles = _state.value.bundleChoices.toMap()
            r.copy(pieces = if (list.size < 2) rest else rest + Piece(PieceKind.BUNDLE, mapOf("items" to list.map { k ->
                listOfNotNull("code" to k, titles[k]?.takeIf { RecipeText.graphemes(it) <= 40 }?.let { "note" to it }).toMap()
            })))
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

    /** Step 5: 광장 (1.35 [RCX-28] 바로 올라가요), 링크로만, 나만 (만든 사람만 열어요). */
    fun publish() {
        val s = _state.value
        if (!RecipeLint.ready(lint)) return
        _state.value = s.copy(sending = true, error = null)
        viewModelScope.launch {
            try {
                val r = RecipeCodec.read(RecipeCodec.write(withServerPictures(s.recipe)))
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

    /**
     * [RCX-30] A 그림 꾸미기 piece names the phone's own pictures; each is sent (made small, no EXIF, already on the phone) and the
     * piece names the server's id instead. A picture already sent keeps its id.
     */
    private suspend fun withServerPictures(r: com.bamnat.core.recipe.Recipe): com.bamnat.core.recipe.Recipe {
        if (r.pieces.none { it.kind == PieceKind.PHOTO_LOOK }) return r
        val sent = mutableMapOf<String, String>()
        suspend fun server(id: Any?): Any? {
            val local = id as? String ?: return id
            if (!com.bamnat.app.recipe.LookImages.has(c.appContext, local)) return local
            return sent.getOrPut(local) { com.bamnat.app.recipe.LookImages.upload(c, local) }
        }
        return r.copy(pieces = r.pieces.map { p ->
            if (p.kind != PieceKind.PHOTO_LOOK) p else {
                val v = p.values.toMutableMap()
                for (k in listOf("bg", "head", "widget")) v[k]?.let { v[k] = server(it) }
                (v["icons"] as? List<*>)?.let { l -> v["icons"] = l.map { i -> (i as? Map<*, *>)?.let { m -> m.mapKeys { it.key.toString() }.toMutableMap().also { it["image"] = server(it["image"]) } } ?: i } }
                p.copy(values = v)
            }
        })
    }

    companion object {
        const val MIN_WAGE = 10_320L
        /** [RCX-31] Says where the first 시급 comes from without an amount, which the recipe's amount check refuses. */
        const val WAGE_HINT = "처음 값은 올해 최저임금이에요. 내 시급으로 바꿔 넣어요"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun RecipeMakeScreen(vm: RecipeMakeViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
    val s by vm.state.collectAsStateWithLifecycle()
    // [STU-01] 1.35: 꾸미기 스튜디오 first; what was 고르기 lives under it.
    val titles = listOf("꾸미기", "받는 분이 넣을 값", "이름·표지", "설명", "마치기")
    Scaffold(topBar = {
        TopAppBar(title = { Text((if (vm.ownCode != null) "새 판 만들기" else "레시피 만들기") + " ${s.step}/5 · ${titles[s.step - 1]}") },
            navigationIcon = { IconButton(onClick = { if (s.step > 1 && s.sent == null) vm.go(s.step - 1) else onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "뒤로") } })
    }) { padding ->
        val scroll = rememberScrollState()
        var tab by rememberSaveable { mutableStateOf(com.bamnat.core.layout.TabOrder.CALENDAR) }
        var mockShown by remember { mutableStateOf(true) }
        val scope = rememberCoroutineScope()
        var boxTop by remember { mutableStateOf(0f) }
        Box(Modifier.fillMaxSize().padding(padding).onGloballyPositioned { boxTop = it.boundsInWindow().top }) {
        Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 640.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LinearProgressIndicator(progress = { s.step / 5f }, modifier = Modifier.fillMaxWidth())
                s.sent?.let { sent -> SentCard(s.recipe.title, sent, onOpen); return@Column }
                when (s.step) {
                    1 -> {
                        RecipeStudio(s, vm, tab, { tab = it }, onMockShown = { mockShown = it },
                            onEdit = { top -> scope.launch { scroll.animateScrollTo((scroll.value + top - boxTop - 8f).toInt().coerceAtLeast(0)) } })
                        var more by rememberSaveable { mutableStateOf(false) }
                        OutlinedCard(Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("내 설정에서 더하기 · 다른 판 묶기", style = MaterialTheme.typography.titleSmall)
                                        Text("회사 근무 주기, 그림, 다른 판 묶음처럼 예시 앱에 없는 것", style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    TextButton(onClick = { more = !more }) { Text(if (more) "접기" else "열기") }
                                }
                                if (more) StepPick(s, vm)
                            }
                        }
                    }
                    2 -> StepInputs(s, vm)
                    3 -> StepLook(s, vm)
                    4 -> StepWords(s, vm)
                    else -> StepCheck(s, vm)
                }
                s.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (s.step > 1) OutlinedButton(onClick = { vm.go(s.step - 1) }, modifier = Modifier.heightIn(min = 48.dp)) { Text("이전") }
                    Spacer(Modifier.weight(1f))
                    if (s.step < 5) Button(onClick = { vm.go(s.step + 1) }, enabled = s.recipe.pieces.isNotEmpty() && (s.step != 3 || s.recipe.title.isNotBlank()) &&
                        (s.step != 2 || s.recipe.inputs.none { val lo = it.min; val hi = it.max; lo != null && hi != null && lo > hi }),
                        modifier = Modifier.heightIn(min = 48.dp)) { Text("다음") }
                }
                // 1.35 실사용 점검: say why 다음 does not go on.
                when {
                    s.step < 5 && s.recipe.pieces.isEmpty() -> "예시 앱에서 하나 이상 바꾸거나 켜면 다음으로 가요"
                    s.step == 3 && s.recipe.title.isBlank() -> "이름을 넣으면 다음으로 가요"
                    else -> null
                }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
                Text("만들던 내용은 이 휴대폰에 저장돼요. 설정 › 나눔 판에서 이어서 만들 수 있어요", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(24.dp))
            }
        }
        // [STU-03] While the fields are changed below, the example stays in view, small, at the top corner.
        // F-57: it can be put away to a small pill when it covers a field, and brought back.
        var miniOpen by rememberSaveable { mutableStateOf(true) }
        if (s.step == 1 && s.sent == null && !mockShown) {
            if (miniOpen) Column(Modifier.align(Alignment.TopEnd).padding(8.dp), horizontalAlignment = Alignment.End) {
                MiniPhone(s.recipe, vm.style, tab, onOpen = { scope.launch { scroll.animateScrollTo(0) } }, Modifier.shadow(6.dp, RoundedCornerShape(14.dp)))
                androidx.compose.material3.FilledTonalButton(onClick = { miniOpen = false }, modifier = Modifier.padding(top = 4.dp).heightIn(min = 40.dp)) { Text("작은 예시 접기") }
            } else androidx.compose.material3.FilledTonalButton(onClick = { miniOpen = true },
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).heightIn(min = 40.dp)) { Text("예시 보기") }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
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
    // 1.35 실사용 점검 F-36: what is picked is what the field holds, so one chip stays picked until a second makes the piece.
    val picked = remember(codes) { bundleCodes(codes) }
    Text("레시피 묶기 (선택)", style = MaterialTheme.typography.titleSmall)
    // [RCX-11] Tap the ones I have rather than typing codes.
    if (s.bundleChoices.isNotEmpty()) {
        Text("넣어 둔 레시피와 내가 올린 레시피에서 골라요", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            s.bundleChoices.forEach { (k, title) ->
                FilterChip(k in picked, {
                    val next = if (k in picked) picked - k else picked + k
                    codes = next.joinToString(" "); vm.bundle(next)
                }, label = { Text(title, maxLines = 1) })
            }
        }
    }
    OutlinedTextField(codes, { t -> codes = t; vm.bundle(bundleCodes(t)) },
        label = { Text("다른 레시피 코드나 링크 2~8개") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    Text(when {
        bundled.size >= 2 -> "묶음 ${bundled.size}개: 받는 분이 하나씩 열어 골라 넣어요"
        picked.size == 1 -> "하나 더 고르면 묶음이 돼요 (2~8개)"
        else -> "예: 근무 색 레시피와 알람 레시피를 한 번에 나누기"
    },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** The recipe codes in [t], typed or as links, each once. */
private fun bundleCodes(t: String): List<String> =
    Regex("[2-9A-HJ-NP-Za-hj-np-z]{8}|https?://\\S+").findAll(t).mapNotNull { m -> com.bamnat.app.recipe.RecipeLinks.find(m.value) }.distinct().toList()

@Composable
private fun StepInputs(s: RecipeMakeViewModel.State, vm: RecipeMakeViewModel) {
    Text("받는 분이 자기 값으로 채우는 칸이에요. 금액은 처음부터 칸으로 바뀌어서 내 금액은 나가지 않아요", style = MaterialTheme.typography.bodyMedium)
    if (s.recipe.inputs.isEmpty()) Text("정할 칸이 없어요. 그대로 다음으로 가도 돼요", style = MaterialTheme.typography.bodySmall)
    s.recipe.inputs.forEach { i ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(i.label, { t -> vm.edit { r -> r.copy(inputs = r.inputs.map { if (it.id == i.id) it.copy(label = t.take(20)) else it }) } },
                    label = { Text("칸 이름") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                InputField(i, null, { v -> vm.edit { r -> r.copy(inputs = r.inputs.map { if (it.id == i.id) it.copy(default = v) else it }) } })
                // [RCX-25] 꼭 채우기 and, for a number, the range the receiver must stay in (설계서 4-2 2단계).
                fun change(f: (com.bamnat.core.recipe.Input) -> com.bamnat.core.recipe.Input) = vm.edit { r -> r.copy(inputs = r.inputs.map { if (it.id == i.id) f(it) else it }) }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 12.dp)) {
                        Text("꼭 채우기", style = MaterialTheme.typography.bodyMedium)
                        Text(if (i.required) "받는 분이 채워야 넣을 수 있어요" else "비워 두면 받는 분의 지금 값 그대로예요",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    androidx.compose.material3.Switch(i.required, { on -> change { it.copy(required = on) } })
                }
                if (i.kind == com.bamnat.core.recipe.InputKind.MONEY || i.kind == com.bamnat.core.recipe.InputKind.NUMBER) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for ((label, get) in listOf<Pair<String, (com.bamnat.core.recipe.Input) -> Double?>>("가장 작게" to { it.min }, "가장 크게" to { it.max })) {
                            var text by remember(i.id, label) { mutableStateOf(get(i)?.let { d -> if (d == d.toLong().toDouble()) d.toLong().toString() else d.toString() }.orEmpty()) }
                            OutlinedTextField(text, { t ->
                                text = t.filter { it.isDigit() || it == '.' }.take(10)
                                val d = text.toDoubleOrNull()
                                change { if (label == "가장 작게") it.copy(min = d) else it.copy(max = d) }
                            }, label = { Text("$label (선택)") }, singleLine = true, modifier = Modifier.weight(1f),
                                visualTransformation = com.bamnat.app.ui.components.GroupedNumber, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
                        }
                    }
                    if (i.min != null && i.max != null && i.min!! > i.max!!) Text("가장 작게가 가장 크게보다 커요", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error)
                }
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
        else "사진 표지는 정식 출시일(11월 10일 예정)부터 돼요", style = MaterialTheme.typography.bodySmall,
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
    listOf("public" to "광장에 올리기 (바로 모두에게 보여요)", "link" to "링크로만 (링크를 받은 분만 열어요)", "private" to "나만 (내 휴대폰들에서만 열어요)").forEach { (k, label) ->
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
    // [LIN-01] 1.35 수정판: what this is made from, said before it goes up.
    s.recipe.parent?.let { Text("‘${s.parentTitle ?: it}’의 수정판으로 그 판 밑에 올라가요. 원래 만든 분 표시는 늘 남고, 그분이 달라진 점을 보고 자기 판에 가져갈 수 있어요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (!vm.canPublish) Text("올리기는 정식 출시일(11월 10일 예정)부터 열려요. 그동안 만들던 것으로 이 휴대폰에 저장돼요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Button(onClick = vm::publish, enabled = RecipeLint.ready(lines) && (s.agreed || s.scope == "private") && vm.canPublish && !s.sending,
        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text(if (s.sending) "올리는 중…" else if (s.scope == "private") "나만 보기로 저장" else "올리기") }
    // 1.35 실사용 점검: why 올리기 is off, in one line.
    when {
        !RecipeLint.ready(lines) -> "위 검사의 ✕를 먼저 고쳐 주세요"
        s.scope != "private" && !s.agreed -> "위 확인 스위치를 켜면 올릴 수 있어요"
        else -> null
    }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary) }
    // [STU-05] 1.35 올리지 않고 끝내기 (사용자 2026-10-30 "제작만 할수도 있어야해").
    HorizontalDivider()
    Text("올리지 않고 끝내기", style = MaterialTheme.typography.titleSmall)
    // 자유도 점검 F-47: what happened shows above the buttons, where the eye is, not under them off the screen.
    var kept by rememberSaveable { mutableStateOf(false) }
    val done = s.putMine?.takeIf { it > 0 }?.let { "내 앱에 넣었어요. 7일 안에 설정 › 나눔 판 › 받은 레시피에서 되돌릴 수 있어요" }
        ?: if (s.putMine == -1L) "지금 내 설정과 같아서 바뀐 것이 없어요" else null
        ?: if (kept) "저장했어요. 설정 › 나눔 판 › 만들던 레시피에서 이어서 만들어요" else null
    if (done != null) Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
        Text("✓ $done", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(12.dp).semantics { liveRegion = androidx.compose.ui.semantics.LiveRegionMode.Polite })
    } else Text("만들기만 하면 이 휴대폰에 저장만 돼요. 내 앱에만 넣으면 아무에게도 보이지 않고, 7일 안에 설정 › 나눔 판 › 받은 레시피에서 되돌릴 수 있어요",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { vm.keepDraft(); kept = true }, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("만들기만 (저장)") }
        OutlinedButton(onClick = vm::putOnMine, enabled = s.putMine == null, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("내 앱에만 넣기") }
    }
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
