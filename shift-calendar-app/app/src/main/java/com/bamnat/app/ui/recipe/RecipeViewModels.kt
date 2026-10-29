package com.bamnat.app.ui.recipe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.app.data.db.AppliedRecipeEntity
import com.bamnat.app.recipe.MyRecipes
import com.bamnat.app.recipe.RecipeClient
import com.bamnat.app.recipe.RecipeDoc
import com.bamnat.app.recipe.RecipeItem
import com.bamnat.app.recipe.RecipeLinks
import com.bamnat.app.recipe.RecipePieces
import com.bamnat.app.recipe.RecipeRepository
import com.bamnat.app.recipe.RecipeTalk
import com.bamnat.app.recipe.Votes
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** [RCP-21] 나눔 판 목록: the recipes for one kind of work, by tab, with search and 코드로 받기. */
class RecipeListViewModel(private val c: AppContainer, val calendar: String = PersonalCalendar.MAIN) : ViewModel() {
    val style: WorkStyle? = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    val enabled: Boolean get() = c.recipeApi.enabled && c.flags.on(FlagCatalog.RECIPE_BROWSE)

    enum class Tab(val key: String, val label: String) { HOT("hot", "요즘 뜨는"), GOOD("good", "좋은 순"), NEW("new", "새로"), OFFICIAL("official", "시작 레시피"), DATA("data", "공개 자료") }

    data class State(val tab: Tab = Tab.HOT, val query: String = "", val items: List<RecipeItem> = emptyList(), val next: Int? = null,
        val loading: Boolean = false, val error: String? = null, val allStyles: Boolean = false)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun tab(t: Tab) { _state.value = _state.value.copy(tab = t); load() }
    fun query(q: String) { _state.value = _state.value.copy(query = q) }
    fun allStyles(on: Boolean) { _state.value = _state.value.copy(allStyles = on); load() }

    fun load(more: Boolean = false) {
        if (!enabled) { _state.value = _state.value.copy(error = "나눔 판은 아직 열리지 않았어요"); return }
        val s = _state.value
        if (more && s.next == null) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                val page = c.recipeApi.list(if (s.allStyles) null else style, s.tab.key, s.query, if (more) s.next ?: 0 else 0)
                _state.value.copy(items = if (more) _state.value.items + page.items else page.items, next = page.next, loading = false)
            } catch (e: Exception) {
                _state.value.copy(loading = false, error = RecipeClient.problem(e))
            }
        }
    }

    /** 코드·링크로 받기: the code in what was typed or pasted, or null with why. */
    fun codeOf(text: String): String? = RecipeLinks.find(text)
}

/** [RCP-23..27][RCP-31..35] One recipe: its pieces, 미리 보기 on my calendar, 골라 적용, votes, talk and 신고. */
class RecipeDetailViewModel(private val c: AppContainer, val code: String, val calendar: String = PersonalCalendar.MAIN) : ViewModel() {
    val style: WorkStyle? = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    private val device get() = c.companyStore.device
    val canVote: Boolean get() = c.flags.on(FlagCatalog.RECIPE_VOTE)
    val canTalk: Boolean get() = c.flags.on(FlagCatalog.RECIPE_TALK)

    data class Piece(val kind: PieceKind, val on: Boolean, val fits: Boolean, val mode: RecipePieces.Mode = RecipePieces.Mode.REPLACE,
        val lines: List<RecipePieces.Change> = emptyList())

    data class State(
        val doc: RecipeDoc? = null, val recipe: Recipe? = null, val error: String? = null,
        val pieces: List<Piece> = emptyList(), val answers: Map<String, Any?> = emptyMap(),
        val votes: Votes? = null, val talk: RecipeTalk? = null, val message: String? = null,
        val applying: Boolean = false, val appliedId: Long? = null,
    )
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            try {
                val doc = c.recipeApi.show(code, device)
                val r = doc.recipe()
                val pieces = r.pieces.map { p -> Piece(p.kind, on = style == null || style in p.kind.styles, fits = style == null || style in p.kind.styles) }
                _state.value = _state.value.copy(doc = doc, recipe = r, pieces = pieces, error = null,
                    votes = Votes(doc.item.likes, doc.item.dislikes, doc.myVote))
                preview()
                if (canTalk) loadTalk()
            } catch (e: com.bamnat.core.recipe.RecipeException) {
                _state.value = _state.value.copy(error = "레시피가 오는 길에 바뀌었어요. 받지 않았어요")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = RecipeClient.problem(e))
            }
        }
    }

    fun toggle(kind: PieceKind, on: Boolean) = set(kind) { it.copy(on = on) }
    fun mode(kind: PieceKind, m: RecipePieces.Mode) = set(kind) { it.copy(mode = m) }
    fun answer(id: String, v: Any?) { _state.value = _state.value.copy(answers = _state.value.answers + (id to v)); preview() }

    private fun set(kind: PieceKind, f: (Piece) -> Piece) {
        _state.value = _state.value.copy(pieces = _state.value.pieces.map { if (it.kind == kind) f(it) else it })
    }

    /** 내 달력에서 미리 보기: what each chosen piece would change here, worked out again when an answer changes. */
    fun preview() {
        val r = _state.value.recipe ?: return
        viewModelScope.launch {
            val answers = RecipeResolve.answers(r, _state.value.answers)
            val lines = r.pieces.associate { p ->
                p.kind to runCatching { c.recipes.pieces.preview(p.kind, RecipeResolve.piece(r, p, answers), calendar) }.getOrDefault(emptyList())
            }
            _state.value = _state.value.copy(pieces = _state.value.pieces.map { it.copy(lines = lines[it.kind].orEmpty()) })
        }
    }

    fun apply(onDone: (Long?) -> Unit) {
        val s = _state.value
        val r = s.recipe ?: return
        val doc = s.doc ?: return
        _state.value = s.copy(applying = true)
        viewModelScope.launch {
            val id = runCatching {
                c.recipes.apply(r, code, doc.item.version, calendar,
                    s.pieces.filter { it.on && it.fits }.map { RecipeRepository.Pick(it.kind, it.mode) }, s.answers)
            }.getOrNull()
            _state.value = _state.value.copy(applying = false, appliedId = id,
                message = if (id == null) "바뀐 것이 없어요" else "적용했어요. 7일 안에 되돌릴 수 있어요")
            onDone(id)
        }
    }

    fun undo() {
        val id = _state.value.appliedId ?: return
        viewModelScope.launch {
            val ok = c.recipes.undo(id)
            _state.value = _state.value.copy(appliedId = null, message = if (ok) "되돌렸어요. 그 사이 적은 기록은 그대로예요" else "되돌릴 수 없어요")
        }
    }

    /** 좋아요 (1), 안좋아요 (-1, with a reason and an opinion), or null to take a vote back. */
    fun vote(value: Int?, reason: String? = null, opinion: String? = null) {
        viewModelScope.launch {
            try {
                val v = c.recipeApi.vote(device, code, value, reason, opinion)
                _state.value = _state.value.copy(votes = v, message = if (v.held) "새 휴대폰의 표는 조금 뒤에 세어져요" else null)
            } catch (e: Exception) { _state.value = _state.value.copy(message = RecipeClient.problem(e)) }
        }
    }

    fun flag(reason: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(message = runCatching { c.recipeApi.flag(device, code, reason) }
                .fold({ "신고했어요. 운영자가 살펴봐요" }, { RecipeClient.problem(it) }))
        }
    }

    fun loadTalk() {
        viewModelScope.launch {
            runCatching { c.recipeApi.talk(device, code) }.onSuccess { _state.value = _state.value.copy(talk = it) }
        }
    }

    /** How many days this phone has had the recipe on, for 쓰는 중 N일 next to a post. */
    private suspend fun usingDays(): Int? {
        val e = c.recipes.applied.first().firstOrNull { it.code == code && it.undoneAt == null } ?: return null
        return ((com.bamnat.core.time.Today.millis() - e.appliedAt) / RecipeRepository.DAY_MILLIS).toInt()
    }

    fun post(body: String, kind: String, parent: String?, done: (Boolean) -> Unit) {
        viewModelScope.launch {
            try {
                c.recipeApi.talkPost(device, code, body, kind, parent, usingDays())
                loadTalk(); done(true)
            } catch (e: Exception) { _state.value = _state.value.copy(message = RecipeClient.problem(e)); done(false) }
        }
    }

    fun votePost(post: String, value: Int?, reason: String? = null, opinion: String? = null) {
        viewModelScope.launch {
            runCatching { c.recipeApi.talkVote(device, post, value, reason, opinion) }
                .onSuccess { loadTalk() }.onFailure { _state.value = _state.value.copy(message = RecipeClient.problem(it)) }
        }
    }

    fun shown() { _state.value = _state.value.copy(message = null) }

    /** The written recipe, for 고쳐 만들기. */
    fun body(): String? = _state.value.recipe?.let(RecipeCodec::write)
}

/** [RCP-38] 설정 › 나눔 판: the switch, recipes put on (with 되돌리기), my recipes on the plaza and those being made. */
class RecipeMineViewModel(private val c: AppContainer) : ViewModel() {
    val on: StateFlow<Boolean> = c.recipes.on
    val applied: StateFlow<List<AppliedRecipeEntity>> = c.recipes.applied.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val drafts = c.recipes.drafts.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    private val _mine = MutableStateFlow<MyRecipes?>(null)
    val mine: StateFlow<MyRecipes?> = _mine.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init { loadMine() }

    fun setOn(v: Boolean) = c.recipes.setOn(v)
    fun canUndo(e: AppliedRecipeEntity) = c.recipes.canUndo(e)

    fun loadMine() {
        if (!c.recipeApi.enabled) return
        viewModelScope.launch { runCatching { c.recipeApi.mine(c.companyStore.device) }.onSuccess { _mine.value = it } }
    }

    fun undo(id: Long) {
        viewModelScope.launch { _message.value = if (c.recipes.undo(id)) "되돌렸어요. 그 사이 적은 기록은 그대로예요" else "되돌릴 수 없어요" }
    }

    fun delete(code: String) {
        viewModelScope.launch {
            _message.value = runCatching { c.recipeApi.delete(c.companyStore.device, code) }.fold({ loadMine(); "지웠어요" }, { RecipeClient.problem(it) })
        }
    }

    fun deleteDraft(id: Long) { viewModelScope.launch { c.recipes.deleteDraft(id) } }
    fun shown() { _message.value = null }
}
