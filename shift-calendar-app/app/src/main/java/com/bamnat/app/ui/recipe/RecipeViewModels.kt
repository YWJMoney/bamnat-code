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
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

/** [MKX-02] A tab's name as the 기능별 탭 chips say it. */
internal fun tabName(tab: String?): String = com.bamnat.core.recipe.LookParts.tabLabel(tab ?: "")

/** [RCP-21] 나눔 판 목록: the recipes for one kind of work, by tab, with search and 코드로 받기. */
class RecipeListViewModel(private val c: AppContainer, val calendar: String = PersonalCalendar.MAIN) : ViewModel() {
    /**
     * PC 확인 38d63451: the kind of work the list is for. It follows the 마을's own ([useStyle]) and a change of 근무 유형 in 설정, so the
     * list and 오늘의 하나 show the same kind of work's things (the list once stayed on the kind of work it was first opened with).
     */
    var style: WorkStyle? = c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)
        private set
    val enabled: Boolean get() = c.recipeApi.enabled && c.flags.on(FlagCatalog.RECIPE_BROWSE)

    companion object {
        /** The market's three halves (테마 · 기능별 탭 · 규칙·단추). */
        const val TYPING_PAUSE_MS = 500L
        val HALVES = listOf(RecipeClient.SCOPE_THEME, RecipeClient.SCOPE_TAB, RecipeClient.SCOPE_LOGIC)
        fun halfName(h: String) = when (h) { RecipeClient.SCOPE_LOGIC -> "규칙·단추"; RecipeClient.SCOPE_TAB -> "기능별 탭"; else -> "테마" }

        /** 1.39 [MKP-01] A list as last seen, for the same 구간·칸·찾기 opened again (while the app runs). */
        private class Kept(val items: List<RecipeItem>, val next: Int?, val at: Long)
        private val pages = LinkedHashMap<String, Kept>()
        private const val PAGES = 16
    }

    enum class Tab(val key: String, val label: String) { SHOWCASE("showcase", "이런 것도"), HOT("hot", "요즘 뜨는"), GOOD("good", "좋은 순"), NEW("new", "새로"), OFFICIAL("official", "시작 레시피"), DATA("data", "공개 자료") }

    data class State(val tab: Tab = Tab.HOT, val query: String = "", val items: List<RecipeItem> = emptyList(), val next: Int? = null,
        val loading: Boolean = false, val error: String? = null, val allStyles: Boolean = false,
        /**
         * [FREE-08] 종류로 보기 (RecipeClient.KINDS); null for all. [MKX-01] 1.36: the 장터 구간 — [RecipeClient.SCOPE_THEME] (테마) or
         * [RecipeClient.SCOPE_TAB] + a tab (기능별 탭).
         */
        val kind: String? = RecipeClient.SCOPE_THEME,
        /** PC D10 (사용성 중): with a word to find, how many the other halves have ("규칙·단추에 1개"), so a search is not lost in the half open. */
        val elsewhere: Map<String, String> = emptyMap(),
        /** PC 확인 38d63451: while this half is empty, how many the other half (테마 ↔ 기능별 탭) has for the same kind of work. */
        val other: Int = 0,
        /** 1.38 (장터 그림 LOW): when the list shown was read, so an offline list says how old it is. */
        val loadedAt: Long = 0L)
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    init {
        load()
        // [PRF-05] 차단·풀기 on a profile shows here at once, not after the app starts again (빌드 221dd083 확인).
        viewModelScope.launch { c.recipes.blocked.drop(1).collect { load() } }
        viewModelScope.launch {
            c.settingsRepository.settings.drop(1).collect { useStyle(c.calendars.styleOf(calendar, it.workStyle)) }
        }
    }

    fun useStyle(s: WorkStyle?) { if (s != style) { style = s; load() } }

    fun tab(t: Tab) { _state.value = _state.value.copy(tab = t); load() }
    // PC D11 (사용성 낮): 치다가 잠깐 멈추면 찾아요 (엔터를 눌러야만 다른 구간 칩이 나오던 것).
    private var typing: kotlinx.coroutines.Job? = null
    fun query(q: String) {
        val was = _state.value.query
        _state.value = _state.value.copy(query = q)
        if (q.trim() == was.trim()) return
        typing?.cancel()
        typing = viewModelScope.launch { kotlinx.coroutines.delay(TYPING_PAUSE_MS); load() }
    }
    fun allStyles(on: Boolean) { _state.value = _state.value.copy(allStyles = on); load() }
    // 1.35's 테마·스킨 way in (테마 › 장터에서 새 테마 찾기) opens the 테마 구간.
    fun kind(k: String?) { _state.value = _state.value.copy(kind = if (k == "skin") RecipeClient.SCOPE_THEME else k); load() }

    /** PC D12: a '다른 구간에 N개' chip: that half, in 새로 (where it was counted). */
    fun jump(half: String) { _state.value = _state.value.copy(kind = half, tab = Tab.NEW); load() }

    /** The half [kind] is in. */
    fun halfNow(): String = halfOf(_state.value.kind)

    /** The list asked for last; a newer ask takes its place (a tab tapped twice, words typed on). */
    private var loading: kotlinx.coroutines.Job? = null
    private var counting: kotlinx.coroutines.Job? = null

    /**
     * 1.39 [MKP-01] 장터 로딩 (사용자 2026-11-07 "장터에서 테마 로딩이 너무 느린데"):
     * - the list the phone saw last for the same 구간·칸·찾기 shows at once while it is read again ([pages]);
     * - the list shows as soon as it comes; how many the other 구간 have (찾기·빈 구간) is counted after, not before it;
     * - an older ask still on its way is dropped, so a slow answer never takes the place of the one asked last.
     */
    fun load(more: Boolean = false) {
        if (!enabled) { _state.value = _state.value.copy(error = "장터는 아직 열리지 않았어요"); return }
        val s = _state.value
        if (more && s.next == null) return
        if (more && loading?.isActive == true) return
        loading?.cancel()
        counting?.cancel()
        val styleNow = if (s.allStyles) null else style
        val key = listOf(styleNow?.name, s.tab.key, s.query.trim(), s.kind).joinToString("|")
        val kept = if (more) null else synchronized(pages) { pages[key] }
        _state.value = if (kept != null) s.copy(items = kept.items, next = kept.next, loadedAt = kept.at, loading = true, error = null)
            else s.copy(loading = true, error = null, items = if (more) s.items else emptyList())
        loading = viewModelScope.launch {
            try {
                val page = c.recipeApi.list(styleNow, s.tab.key, s.query, if (more) s.next ?: 0 else 0, s.kind)
                MarketShots.feed(c, page)
                // [PRF-05] 1.36 차단한 분의 작품은 이 폰에서 빼요 (목록은 모든 폰이 같은 캐시).
                val got = page.items.filter { RecipeClient.fits(it, s.kind) && !c.recipes.hides(it) }
                val items = if (more) _state.value.items + got else got
                val at = com.bamnat.core.time.Today.millis()
                if (!more) synchronized(pages) { pages[key] = Kept(items, page.next, at); while (pages.size > PAGES) pages.remove(pages.keys.first()) }
                _state.value = _state.value.copy(items = items, next = page.next, loading = false, loadedAt = at,
                    elsewhere = if (more) _state.value.elsewhere else emptyMap(), other = if (more) _state.value.other else 0)
                if (!more) count(s, styleNow, got.isEmpty(), items.isNotEmpty())
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = _state.value.copy(loading = false, error = RecipeClient.problem(e))
            }
        }
    }

    /**
     * PC D12 (사용성 낮): 다른 구간은 '새로'(모든 글)에서 세어요. 시작 레시피 칸처럼 좁은 칸에서 찾아도 길이 보이게, 지금 구간도 이 칸에 없으면
     * '새로'에 몇 개인지. PC 확인 38d63451: an empty 테마 says the 기능별 탭 has some (and the other way round), not only "아직 없어요".
     * 1.39 [MKP-01]: counted after the list is on the screen, the halves at the same time.
     */
    private fun count(s: State, styleNow: WorkStyle?, noneHere: Boolean, any: Boolean) {
        val otherKind = when {
            s.kind == RecipeClient.SCOPE_THEME -> RecipeClient.SCOPE_TAB
            s.kind?.startsWith(RecipeClient.SCOPE_TAB) == true -> RecipeClient.SCOPE_THEME
            else -> null
        }
        val wantOther = !any && otherKind != null && s.query.isBlank()
        if (s.query.isBlank() && !wantOther) return
        counting = viewModelScope.launch {
            val other = if (s.query.isBlank()) emptyMap() else HALVES.filter { h -> h != halfOf(s.kind) || (s.tab != Tab.NEW && noneHere) }.map { h ->
                async {
                    runCatching { c.recipeApi.list(styleNow, Tab.NEW.key, s.query, 0, h) }.getOrNull()?.let { p ->
                        val n = p.items.count { RecipeClient.fits(it, h) }
                        if (n == 0) null else h to (if (p.next != null) "${n}개 넘게" else "${n}개")
                    }
                }
            }.awaitAll().filterNotNull().toMap()
            val otherCount = if (!wantOther) 0 else runCatching {
                c.recipeApi.list(styleNow, "hot", "", 0, otherKind).items.count { RecipeClient.fits(it, otherKind) && !c.recipes.hides(it) }
            }.getOrDefault(0)
            _state.value = _state.value.copy(elsewhere = other, other = otherCount)
        }
    }

    private fun halfOf(kind: String?): String = when {
        kind == RecipeClient.SCOPE_LOGIC -> RecipeClient.SCOPE_LOGIC
        kind?.startsWith(RecipeClient.SCOPE_TAB) == true -> RecipeClient.SCOPE_TAB
        else -> RecipeClient.SCOPE_THEME
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
        val clashes: List<String> = emptyList(),
        /** 1.39 [MKP-05] 기능 조각 전후: what it touches here, 지금 ↔ 넣으면. */
        val sides: List<RecipePieces.Side> = emptyList())

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
        /**
         * PC 확인 7525ccff: each put-on 되돌리기 takes back (when, what), in the order put on, for its question. PC 확인 보안 6차 (조각을 바꿔
         * 넣어도 목록이 없어요): a tab laid on the theme ([MKX-02] 얹기) is one too, as it leaves no record of its own.
         */
        val undoable: List<Pair<Long, String>> = emptyList(),
        /** PC 확인 보안 6차: when a tab of this recipe was last laid on the theme (with no other put-on, the card says that). */
        val laidAt: Long? = null,
        /** PC 확인 보안 7차 (넣었는데 쓰는 분 0명): this phone, counted just now, until the page is read again. */
        val usersPlus: Int = 0,
        /** PC 확인 7525ccff: reported from this page, so its menu says so (the line below goes in a few seconds). */
        val flagged: Boolean = false,
    )
    private suspend fun undoableNow(): List<Pair<Long, String>> = (c.recipes.undoable(code).map { e ->
        e.appliedAt to ("넣음" + e.pieces.split(',').mapNotNull { PieceKind.of(it)?.label }.takeIf { it.isNotEmpty() }?.joinToString("·", prefix = " · ").orEmpty())
    } + laidHere().map { o -> o.at to "테마 위에 얹음 · ‘${tabName(o.tab)}’ 탭" }).sortedBy { it.first } // PC 확인 보안 7차: in the order put on
    private fun laidHere() = c.wardrobe.overlays.value.filter { it.code == code }
    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()

    /** 1.39 [MKP-03] Done once the whole work (this phone's 좋아요, whether it is mine) has been read, or could not be. */
    private val read = kotlinx.coroutines.CompletableDeferred<Unit>()
    /** The last work's written form, to tell a newer app's work from one changed on the way. */
    private var newerBody: String? = null

    init { load() }

    fun load() {
        // 1.39 [MKP-03] 작품 바로 열기 (사용자 2026-11-07 "장터에서 테마 로딩이 너무 느린데"): a card tapped in the 장터 already brought the
        // work's written form; it shows at once (조각·미리 보기·입어 보기) while the whole work is read, and 넣기 waits for that.
        if (_state.value.doc == null) MarketShots.seen(code)?.let { d ->
            runCatching { d.recipe() }.getOrNull()?.let { r ->
                newerBody = d.body
                _state.value = _state.value.copy(doc = d, recipe = r, pieces = piecesOf(r), error = null, votes = Votes(d.item.likes, d.item.dislikes, null))
                preview()
            }
        }
        viewModelScope.launch {
            try {
                // PC 확인 W34 1.36 (처음 들어갈 때 한 번 '인터넷에 연결되지 않았어요', 다시 해 보기로 열림): a connection that drops on the
                // first try is tried once more before it is said.
                val doc = try { c.recipeApi.show(code, device) } catch (e: java.io.IOException) {
                    kotlinx.coroutines.delay(800); c.recipeApi.show(code, device)
                }
                newerBody = doc.body
                val r = doc.recipe()
                MarketShots.keepDoc(c, doc)
                // What was ticked on the work shown from the card stays, when it is the same work.
                val same = _state.value.doc?.hash == doc.hash && _state.value.pieces.isNotEmpty()
                _state.value = _state.value.copy(doc = doc, recipe = r, pieces = if (same) _state.value.pieces else piecesOf(r), error = null,
                    votes = Votes(doc.item.likes, doc.item.dislikes, doc.myVote))
                read.complete(Unit)
                preview()
                // [RCX-24] Already on this phone: 되돌리기 stays here for 7 days, not only in the moment's snackbar.
                c.recipes.inUse().filter { it.code == code && c.recipes.canUndo(it) }.maxByOrNull { it.appliedAt }?.let { e ->
                    _state.value = _state.value.copy(appliedId = e.id, appliedAt = e.appliedAt)
                }
                _state.value = _state.value.copy(laidAt = laidHere().maxOfOrNull { it.at }, undoable = undoableNow())
                if (canTalk) loadTalk()
                if (doc.scope != "private") runCatching { c.recipeApi.lineage(code) }.onSuccess { _state.value = _state.value.copy(lineage = it) }
                // [LIN-02] What this 수정판 changed from the recipe it was made from (the parent's version now).
                r.parent?.let { p -> runCatching { c.recipeApi.show(p, device) }.getOrNull()?.let { pd ->
                    runCatching { pd.recipe() }.getOrNull()?.let { pr -> _state.value = _state.value.copy(parentDoc = pd, diff = com.bamnat.core.recipe.RecipeDiff.of(pr, r)) }
                } }
            } catch (e: com.bamnat.core.recipe.RecipeException) {
                // PC 확인 e50bb622 MED-1 (가계부 틀 작품이 '오는 길에 바뀌었어요'로 안 열림): a work made by a newer app carries a kind of
                // piece this app does not know; that is said as it is, with the way to open it.
                val newer = newerBody?.let { b -> runCatching {
                    val keys = PieceKind.entries.map { it.key }.toSet()
                    val a = org.json.JSONObject(b).optJSONArray("pieces")
                    a != null && (0 until a.length()).any { a.optJSONObject(it)?.optString("kind") !in keys }
                }.getOrDefault(false) } == true
                _state.value = _state.value.copy(error = if (newer) "새 판 밤낮에서 만든 작품이라 이 앱에서는 열 수 없어요. 앱을 업데이트하면 열려요"
                    else "레시피가 오는 길에 바뀌었어요. 받지 않았어요")
            } catch (e: Exception) {
                // 1.39 [MKP-03] Shown from the card already: it stays, and the problem is said once below.
                _state.value = if (_state.value.doc != null) _state.value.copy(message = RecipeClient.problem(e)) else _state.value.copy(error = RecipeClient.problem(e))
            } finally {
                read.complete(Unit)
            }
        }
    }

    private fun piecesOf(r: Recipe) = r.pieces.map { p -> Piece(p.kind, on = style == null || style in p.kind.styles, fits = style == null || style in p.kind.styles) }

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
    fun mode(kind: PieceKind, m: RecipePieces.Mode) { set(kind) { it.copy(mode = m) }; preview() }
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
            // 1.39 [MKP-05] 지금 ↔ 넣으면, with the 바꾸기 / 둘 다 두기 chosen for each piece.
            val modes = _state.value.pieces.associate { it.kind to it.mode }
            val sides = r.pieces.associate { p ->
                p.kind to c.recipes.pieces.compare(p.kind, RecipeResolve.piece(r, p, answers), calendar, modes[p.kind] ?: RecipePieces.Mode.REPLACE)
            }
            _state.value = _state.value.copy(pieces = _state.value.pieces.map {
                it.copy(lines = lines[it.kind].orEmpty(), clashes = if (it.kind == PieceKind.MEMO_TEMPLATES) clashes else emptyList(), sides = sides[it.kind].orEmpty())
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
        s.doc ?: return
        _state.value = s.copy(applying = true)
        viewModelScope.launch {
            // 1.39 [MKP-03] A work shown from its 장터 card goes in once it has been read whole (mine or not, its version now).
            kotlinx.coroutines.withTimeoutOrNull(8_000) { read.await() }
            val doc = _state.value.doc ?: s.doc
            if (doc.hash != s.doc.hash) {
                _state.value = _state.value.copy(applying = false, message = "그 사이 만든 분이 새 판을 올렸어요. 바뀐 조각을 보고 다시 넣어 주세요")
                return@launch
            }
            // [FREE-10] The looks among the chosen pieces are worn as a theme (its days, 입었던 테마, 벗기 to my own look);
            // the rest (알람, 규칙, 메모 틀) are put on as always and never run out.
            val looks = s.pieces.filter { it.on && it.fits && it.kind in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS }.map { it.kind }.toSet()
            // [MKX-02] 1.36 기능별 탭: looks of one tab are laid on the theme worn (it keeps its name), not worn as a theme of their own.
            val lookRecipe = r.copy(pieces = r.pieces.filter { it.kind in looks })
            val tab = if (looks.isEmpty()) null else com.bamnat.core.recipe.LookParts.onlyTab(lookRecipe)
            if (looks.isNotEmpty() && tab == null) c.wardrobe.beforeWear()
            val picks = s.pieces.filter { it.on && it.fits && !(tab != null && it.kind in looks) }.map { RecipeRepository.Pick(it.kind, it.mode) }
            val counted = c.recipes.countedHere(code)
            val id = if (picks.isEmpty()) null else runCatching { c.recipes.apply(r, code, doc.item.version, calendar, picks, s.answers) }.getOrNull()
            val laid = tab != null && runCatching { c.wardrobe.overlay(lookRecipe, code, s.answers) }.getOrDefault(false)
            // PC 확인 보안 8차 (넣어도 쓰는 분 0명): a tab laid on is a use too (it leaves no record, so apply did not count it).
            // Once a phone [SEC-15], as a theme put on is (the studio thread's own fix sent it at every 얹기).
            if (laid) c.recipes.countUse(code)
            val plus = if (!counted && c.recipes.countedHere(code)) 1 else 0
            // 1.37 PC (같은 모양이면 '바뀐 것이 없어요'만 뜨고 입은 테마로 안 잡혀요): the look is already that theme's, so it is worn all the same.
            val sameLook = id == null && looks.isNotEmpty() && tab == null
            if ((id != null || sameLook) && looks.isNotEmpty() && tab == null) runCatching {
                c.wardrobe.afterWear(lookRecipe, code, own = doc.mine, ver = doc.item.version)
            }
            _state.value = _state.value.copy(applying = false, appliedId = id ?: _state.value.appliedId, appliedAt = if (id != null) Today.millis() else _state.value.appliedAt,
                undoable = undoableNow(), laidAt = laidHere().maxOfOrNull { it.at }, usersPlus = _state.value.usersPlus + plus,
                message = when {
                    laid -> "‘${tabName(tab)}’ 탭만 지금 테마 위에 얹었어요. 마을 › 테마에서 뺄 수 있어요" + if (id != null) ". 기능은 7일 안에 되돌릴 수 있어요" else ""
                    sameLook -> "이미 같은 모양이라 바뀐 곳은 없어요. ‘${doc.item.title}’${com.bamnat.app.ui.Fmt.obj(doc.item.title)} 입은 테마로 잡았어요"
                    id == null -> "바뀐 것이 없어요"
                    else -> "넣었어요. 7일 안에 되돌릴 수 있어요"
                })
            // PC 확인 C10 1.36 (넣은 직후 상세는 다시 열어야 문구가 바뀌어요): what each piece does now, said again at once.
            preview()
            onDone(id ?: if (laid) 0L else null)
            // PC 제안 1.36 (넣은 직후 상세의 쓰는 분은 0명 그대로): the count read again once it is in.
            // The count read again already holds this phone's, so the share added above is let go.
            if (id != null || laid) runCatching { c.recipeApi.show(code, device) }.onSuccess { d -> _state.value = _state.value.copy(doc = d, usersPlus = 0) }
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
        if (_state.value.appliedId == null && _state.value.laidAt == null) return
        viewModelScope.launch {
            val rows = c.recipes.undoAll(code)
            // PC 확인 보안 6차: a tab of it laid on the theme comes off too, so nothing this page put on is left.
            val tabs = laidHere().map { it.tab }
            tabs.forEach { runCatching { c.wardrobe.removeOverlay(it) } }
            val n = rows + tabs.size
            // The card says what is truly left on this phone, read again, not only what was asked.
            val left = c.recipes.inUse().filter { it.code == code && c.recipes.canUndo(it) }.maxByOrNull { it.appliedAt }
            _state.value = _state.value.copy(appliedId = left?.id, appliedAt = left?.appliedAt, undoable = undoableNow(),
                laidAt = laidHere().maxOfOrNull { it.at },
                message = if (n > 0) "되돌렸어요. 그 사이 적은 기록은 그대로예요" else "되돌릴 수 없어요. 넣은 지 7일이 지났거나 이미 되돌렸어요")
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
            val sent = runCatching { c.recipeApi.flag(device, code, reason) }
            _state.value = _state.value.copy(flagged = _state.value.flagged || sent.isSuccess,
                message = sent.fold({ "신고했어요. 운영자가 살펴봐요" }, { RecipeClient.problem(it) }))
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
