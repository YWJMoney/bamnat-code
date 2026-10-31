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
import com.bamnat.core.time.Today
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

/** [MKX-02] A tab's name as the 기능별 탭 chips say it. */
internal fun tabName(tab: String?): String = com.bamnat.core.recipe.LookParts.tabLabel(tab ?: "")

/** [RCP-21] 나눔 판 목록: the recipes for one kind of work, by tab, with search and 코드로 받기. */
class RecipeListViewModel(private val c: AppContainer, val calendar: String = PersonalCalendar.MAIN) : ViewModel() {
    val style: WorkStyle? = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
    val enabled: Boolean get() = c.recipeApi.enabled && c.flags.on(FlagCatalog.RECIPE_BROWSE)

    enum class Tab(val key: String, val label: String) { SHOWCASE("showcase", "이런 것도"), HOT("hot", "요즘 뜨는"), GOOD("good", "좋은 순"), NEW("new", "새로"), OFFICIAL("official", "시작 레시피"), DATA("data", "공개 자료") }

    data class State(val tab: Tab = Tab.HOT, val query: String = "", val items: List<RecipeItem> = emptyList(), val next: Int? = null,
        val loading: Boolean = false, val error: String? = null, val allStyles: Boolean = false,
        /**
         * [FREE-08] 종류로 보기 (RecipeClient.KINDS); null for all. [MKX-01] 1.36: the 장터 구간 — [RecipeClient.SCOPE_THEME] (테마) or
         * [RecipeClient.SCOPE_TAB] + a tab (기능별 탭).
         */
        val kind: String? = RecipeClient.SCOPE_THEME)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init { load() }

    fun tab(t: Tab) { _state.value = _state.value.copy(tab = t); load() }
    fun query(q: String) { _state.value = _state.value.copy(query = q) }
    fun allStyles(on: Boolean) { _state.value = _state.value.copy(allStyles = on); load() }
    // 1.35's 테마·스킨 way in (테마 › 장터에서 새 테마 찾기) opens the 테마 구간.
    fun kind(k: String?) { _state.value = _state.value.copy(kind = if (k == "skin") RecipeClient.SCOPE_THEME else k); load() }

    fun load(more: Boolean = false) {
        if (!enabled) { _state.value = _state.value.copy(error = "장터는 아직 열리지 않았어요"); return }
        val s = _state.value
        if (more && s.next == null) return
        _state.value = s.copy(loading = true, error = null)
        viewModelScope.launch {
            _state.value = try {
                val page = c.recipeApi.list(if (s.allStyles) null else style, s.tab.key, s.query, if (more) s.next ?: 0 else 0, s.kind)
                val got = page.items.filter { RecipeClient.fits(it, s.kind) }
                _state.value.copy(items = if (more) _state.value.items + got else got, next = page.next, loading = false)
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

    data class Piece(val kind: PieceKind, val on: Boolean, val fits: Boolean,
        val mode: RecipePieces.Mode = if (kind == PieceKind.MEMO_TEMPLATES) RecipePieces.Mode.BOTH else RecipePieces.Mode.REPLACE,
        val lines: List<RecipePieces.Change> = emptyList(),
        /** [RCX-23] Titles of mine the piece runs into (메모 틀), asked 바꾸기 / 둘 다 두기. */
        val clashes: List<String> = emptyList())

    data class State(
        val doc: RecipeDoc? = null, val recipe: Recipe? = null, val error: String? = null,
        val pieces: List<Piece> = emptyList(), val answers: Map<String, Any?> = emptyMap(),
        val votes: Votes? = null, val talk: RecipeTalk? = null, val message: String? = null,
        val applying: Boolean = false, val appliedId: Long? = null,
        /** [RCP-24] My kinds of day as they are and as the 근무 꾸미기 piece would make them. */
        val looks: List<Pair<com.bamnat.core.model.ShiftType, com.bamnat.core.model.ShiftType>> = emptyList(),
        /** [RCX-01] 1.35 계보. */
        val lineage: com.bamnat.app.recipe.Lineage? = null,
        /** [LIN-02] 1.35 수정판: the recipe it was made from and what it changed from it, piece by piece. */
        val parentDoc: RecipeDoc? = null,
        val diff: List<com.bamnat.core.recipe.RecipeDiff.Piece> = emptyList(),
        /** [RCX-24] When this recipe went on this phone, while it can still be taken back (넣은 지 7일 안). */
        val appliedAt: Long? = null,
        /** [TRY-01] The look parts ticked in 입어 보기; null: the look pieces whole, as switched on. */
        val lookParts: Set<String>? = null,
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
                // [RCX-24] Already on this phone: 되돌리기 stays here for 7 days, not only in the moment's snackbar.
                c.recipes.inUse().filter { it.code == code && c.recipes.canUndo(it) }.maxByOrNull { it.appliedAt }?.let { e ->
                    _state.value = _state.value.copy(appliedId = e.id, appliedAt = e.appliedAt)
                }
                if (canTalk) loadTalk()
                if (doc.scope != "private") runCatching { c.recipeApi.lineage(code) }.onSuccess { _state.value = _state.value.copy(lineage = it) }
                // [LIN-02] What this 수정판 changed from the recipe it was made from (the parent's version now).
                r.parent?.let { p -> runCatching { c.recipeApi.show(p, device) }.getOrNull()?.let { pd ->
                    runCatching { pd.recipe() }.getOrNull()?.let { pr -> _state.value = _state.value.copy(parentDoc = pd, diff = com.bamnat.core.recipe.RecipeDiff.of(pr, r)) }
                } }
            } catch (e: com.bamnat.core.recipe.RecipeException) {
                _state.value = _state.value.copy(error = "레시피가 오는 길에 바뀌었어요. 받지 않았어요")
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = RecipeClient.problem(e))
            }
        }
    }

    /**
     * [LIN-03] 1.35 내 판에 가져오기 (사용자 2026-10-30 "제작자는 해당 내용을 보고 수정을 할수 있게"): my recipe (the parent) with the
     * [picked] fields of this 수정판 in it — a changed field by [com.bamnat.core.recipe.Merge3.apply], an added piece whole, a taken-away
     * one out — and a line in its words naming the 수정판 that helped. The written form to make my next 판 from, or null.
     */
    fun takeInto(picked: Set<Pair<PieceKind, String>>): String? {
        val s = _state.value
        val mine = runCatching { s.parentDoc?.recipe() }.getOrNull() ?: return null
        val theirs = s.recipe ?: return null
        var pieces = mine.pieces
        for (d in s.diff) {
            val paths = picked.filter { it.first == d.kind }.map { it.second }.toSet()
            when (d.change) {
                com.bamnat.core.recipe.RecipeDiff.Kind.ADDED -> if (d.kind to "*" in picked) pieces = pieces + theirs.pieces.first { it.kind == d.kind }
                com.bamnat.core.recipe.RecipeDiff.Kind.REMOVED -> if (d.kind to "*" in picked) pieces = pieces.filter { it.kind != d.kind }
                com.bamnat.core.recipe.RecipeDiff.Kind.CHANGED -> if (paths.isNotEmpty()) pieces = pieces.map { p ->
                    if (p.kind != d.kind) p else com.bamnat.core.recipe.Piece(p.kind,
                        com.bamnat.core.recipe.Merge3.apply(p.values, theirs.pieces.first { it.kind == d.kind }.values, paths))
                }
            }
        }
        val ids = mine.inputs.map { it.id }.toSet()
        val credit = "\n도움 준 수정판: ‘${theirs.title}’"
        val merged = mine.copy(pieces = pieces.sortedBy { it.kind.ordinal }, inputs = mine.inputs + theirs.inputs.filter { it.id !in ids },
            desc = if (mine.desc.contains(credit.trim()) || mine.desc.length + credit.length > 600) mine.desc else mine.desc + credit)
        return com.bamnat.core.recipe.RecipeCodec.write(merged)
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
            val look = r.pieces.firstOrNull { it.kind == PieceKind.SHIFT_LOOK }?.let { p ->
                runCatching { c.recipes.pieces.lookPairs(RecipeResolve.piece(r, p, answers), calendar) }.getOrDefault(emptyList())
            }.orEmpty()
            val clashes = r.pieces.firstOrNull { it.kind == PieceKind.MEMO_TEMPLATES }?.let { p ->
                runCatching { c.recipes.pieces.memoClashes(RecipeResolve.piece(r, p, answers)) }.getOrDefault(emptyList())
            }.orEmpty()
            _state.value = _state.value.copy(pieces = _state.value.pieces.map {
                it.copy(lines = lines[it.kind].orEmpty(), clashes = if (it.kind == PieceKind.MEMO_TEMPLATES) clashes else emptyList())
            }, looks = look)
        }
    }

    /**
     * [TRY-01] 입어 보기 ticked these look parts: the look pieces they come from are switched on (the others off), and only those
     * parts go in with [apply].
     */
    fun lookParts(ids: Set<String>) {
        val r = _state.value.recipe ?: return
        val kinds = com.bamnat.core.recipe.LookParts.pick(r, ids).pieces.map { it.kind }.toSet()
        _state.value = _state.value.copy(lookParts = ids, pieces = _state.value.pieces.map { p ->
            if (p.kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS) p.copy(on = p.fits && p.kind in kinds) else p
        })
    }

    /** [TRY-03] 담아 두기: the parts ticked kept in 보관함. */
    fun keep(ids: Set<String>): Boolean = _state.value.recipe?.let { c.wardrobe.keep(it, code, ids.toList()) } ?: false
    fun partsLimit(): Int = c.wardrobe.partsLimit()

    fun apply(onDone: (Long?) -> Unit) {
        val s0 = _state.value
        val full = s0.recipe ?: return
        // [TRY-01] Only the look parts ticked; 알람·규칙·메모 틀 as switched on.
        val r = s0.lookParts?.let { ids ->
            val picked = com.bamnat.core.recipe.LookParts.pick(full, ids).pieces
            full.copy(pieces = full.pieces.filter { it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS } + picked)
        } ?: full
        val s = s0
        val doc = s.doc ?: return
        _state.value = s.copy(applying = true)
        viewModelScope.launch {
            // [FREE-10] The looks among the chosen pieces are worn as a theme (its days, 입었던 테마, 벗기 to my own look);
            // the rest (알람, 규칙, 메모 틀) are put on as always and never run out.
            val looks = s.pieces.filter { it.on && it.fits && it.kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }.map { it.kind }.toSet()
            // [MKX-02] 1.36 기능별 탭: looks of one tab are laid on the theme worn (it keeps its name), not worn as a theme of their own.
            val lookRecipe = r.copy(pieces = r.pieces.filter { it.kind in looks })
            val tab = if (looks.isEmpty()) null else com.bamnat.core.recipe.LookParts.onlyTab(lookRecipe)
            if (looks.isNotEmpty() && tab == null) c.wardrobe.beforeWear()
            val picks = s.pieces.filter { it.on && it.fits && !(tab != null && it.kind in looks) }.map { RecipeRepository.Pick(it.kind, it.mode) }
            val id = if (picks.isEmpty()) null else runCatching { c.recipes.apply(r, code, doc.item.version, calendar, picks, s.answers) }.getOrNull()
            val laid = tab != null && runCatching { c.wardrobe.overlay(lookRecipe, code) }.getOrDefault(false)
            if (id != null && looks.isNotEmpty() && tab == null) runCatching {
                c.wardrobe.afterWear(lookRecipe, code, own = doc.mine, ver = doc.item.version)
            }
            _state.value = _state.value.copy(applying = false, appliedId = id ?: _state.value.appliedId, appliedAt = if (id != null) Today.millis() else _state.value.appliedAt,
                message = when {
                    laid -> "‘${tabName(tab)}’ 탭만 지금 테마 위에 얹었어요. 마을 › 테마에서 뺄 수 있어요" + if (id != null) ". 기능은 7일 안에 되돌릴 수 있어요" else ""
                    id == null -> "바뀐 것이 없어요"
                    else -> "적용했어요. 7일 안에 되돌릴 수 있어요"
                })
            onDone(id ?: if (laid) 0L else null)
        }
    }

    /** [FREE-10] Whether putting the chosen looks on asks for a rewarded ad (someone else's, past the free ones, ads on, not 프로). */
    fun looksNeedAd(): Boolean {
        val s = _state.value
        val looks = s.pieces.any { it.on && it.fits && it.kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }
        return looks && c.wardrobe.needsAd(shared = s.doc?.mine == true)
    }

    /** [FREE-10] A theme put on with no ad able to come: counted. */
    fun noAdCounted(reason: String) = c.wardrobe.counted(reason)

    fun undo() {
        val id = _state.value.appliedId ?: return
        viewModelScope.launch {
            val ok = c.recipes.undo(id)
            _state.value = _state.value.copy(appliedId = null, appliedAt = null, message = if (ok) "되돌렸어요. 그 사이 적은 기록은 그대로예요" else "되돌릴 수 없어요")
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

    /** [RCX-02] Codes put on whose maker made a newer version: code → its version. */
    private val _newer = MutableStateFlow<Map<String, Int>>(emptyMap())
    val newer: StateFlow<Map<String, Int>> = _newer.asStateFlow()

    /** [RCX-03] The 새 판 반영 sheet: which application, the new version and what it changes, with the fields chosen. */
    data class Update(val entry: AppliedRecipeEntity, val recipe: com.bamnat.core.recipe.Recipe, val version: Int,
        val results: List<com.bamnat.core.recipe.Merge3.Result>, val picked: Map<PieceKind, Set<String>>)
    private val _update = MutableStateFlow<Update?>(null)
    val update: StateFlow<Update?> = _update.asStateFlow()

    /** The phone's own maker page id, for 내 페이지. */
    private val _me = MutableStateFlow<String?>(null)
    val me: StateFlow<String?> = _me.asStateFlow()

    init {
        loadMine()
        viewModelScope.launch { _newer.value = c.recipes.newer() }
        if (c.recipeApi.enabled) viewModelScope.launch { _me.value = runCatching { c.recipeApi.makerMe(c.companyStore.device) }.getOrNull() }
    }

    fun subscribed(code: String) = c.recipes.subscribed(code)
    fun subscribe(code: String, on: Boolean) {
        c.recipes.subscribe(code, on)
        _message.value = if (on) "새 판이 나오면 알려 드려요" else "새 판 알림을 껐어요"
        viewModelScope.launch { _newer.value = c.recipes.newer() }
    }

    fun openUpdate(e: AppliedRecipeEntity) {
        val v = _newer.value[e.code] ?: return
        viewModelScope.launch {
            try {
                val r = c.recipeApi.show(e.code, c.companyStore.device, v).recipe()
                val results = c.recipes.changes(e, r)
                if (results.isEmpty()) { _message.value = "새 판에서 이 휴대폰에 넣을 바뀐 점이 없어요"; return@launch }
                // What only the new version changed is chosen; where I changed it too, mine stays unless picked.
                val picked = results.associate { res -> res.kind to res.fields.filter { it.kind == com.bamnat.core.recipe.Merge3.Kind.THEIRS }.map { it.path }.toSet() }
                _update.value = Update(e, r, v, results, picked)
            } catch (x: Exception) { _message.value = RecipeClient.problem(x) }
        }
    }

    fun pick(kind: PieceKind, path: String, on: Boolean) {
        val u = _update.value ?: return
        val now = u.picked[kind].orEmpty()
        _update.value = u.copy(picked = u.picked + (kind to if (on) now + path else now - path))
    }

    fun closeUpdate() { _update.value = null }

    fun applyUpdate() {
        val u = _update.value ?: return
        viewModelScope.launch {
            val id = runCatching { c.recipes.update(u.entry, u.recipe, u.version, u.picked) }.getOrNull()
            _update.value = null
            _message.value = if (id == null) "바뀐 것이 없어요" else "새 판을 반영했어요. 7일 안에 되돌릴 수 있어요"
            _newer.value = c.recipes.newer()
        }
    }

    fun setOn(v: Boolean) = c.recipes.setOn(v)
    fun canUndo(e: AppliedRecipeEntity) = c.recipes.canUndo(e)

    private var shown = false

    /**
     * [STU-25] 1.36 빌드 7: coming back to 내 것 (from the studio's 올리기, say) reads 내가 올린 레시피 again, so the one just sent
     * is there; the first showing has init's read.
     */
    fun shownAgain() { if (shown) loadMine() else shown = true }

    fun loadMine() {
        if (!c.recipeApi.enabled) return
        viewModelScope.launch { runCatching { c.recipeApi.mine(c.companyStore.device) }.onSuccess { _mine.value = it } }
    }

    fun undo(id: Long) {
        viewModelScope.launch { _message.value = if (c.recipes.undo(id)) "되돌렸어요. 그 사이 적은 기록은 그대로예요" else "되돌릴 수 없어요" }
    }

    fun delete(code: String) {
        viewModelScope.launch {
            _message.value = runCatching { c.recipeApi.delete(c.companyStore.device, code) }.fold({
                // [THM-01] Mine taken out of 장터: 30 days, then it runs as one kept to myself.
                c.wardrobe.unshared(code); loadMine(); "지웠어요"
            }, { RecipeClient.problem(it) })
        }
    }

    fun deleteDraft(id: Long) { viewModelScope.launch { c.recipes.deleteDraft(id) } }
    fun shown() { _message.value = null }

    /** [RCX-31] 새 판 만들기 from 내가 올린 레시피: the recipe as it is now, handed to [then] to change and send again. */
    fun newVersion(code: String, then: (String) -> Unit) {
        viewModelScope.launch {
            runCatching { c.recipeApi.show(code, c.companyStore.device) }
                .onSuccess { then(it.body) }
                .onFailure { _message.value = RecipeClient.problem(it) }
        }
    }
}
