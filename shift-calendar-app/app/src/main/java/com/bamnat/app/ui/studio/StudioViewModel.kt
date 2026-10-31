package com.bamnat.app.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bamnat.app.AppContainer
import com.bamnat.app.data.AppSettings
import com.bamnat.app.recipe.ThemeWardrobe
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.LookParts
import com.bamnat.core.recipe.Palette
import com.bamnat.core.recipe.Piece
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeLint
import com.bamnat.core.recipe.ScreenLooks
import com.bamnat.core.recipe.ThemeLease
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [STU-10] 1.35 스튜디오 (사용자 2026-10-30 21:39 "스튜디오 기능이 좋은데 왜 화면 꾸미기랑 분리를 해놓은거야? 둘이 합쳐서 엔터프라이즈급
 * 스튜디오로"): one place to make a look, whatever it is for. The look is a draft held here as settings — the phone's own stay as they
 * are until 입히기 — with 되돌리기·다시 하기 over every change. From the draft: 내 앱에 입히기, 내 테마로 저장, or 장터에 올리기.
 *
 * Where it started decides how 입히기 goes ([Origin]): the phone's look (or the plain one) goes straight on, free, as 화면 꾸미기 did
 * (사용자 2026-10-30 21:56 "직접 꾸미기는 무료 유지"); a theme of mine is worn as mine (in 장터, no ad; kept to myself, as others' are);
 * someone else's theme, or parts of others' from 보관함 ([State.sources]), keep their days and ad, as wearing them from 장터 would.
 */
class StudioViewModel(private val c: AppContainer, start: String = START_MINE) : ViewModel() {
    /**
     * [STU-20] 1.36 one step of 되돌리기: the look and the 기능 at that point, and what was changed there (기록 목록, 사용자 2026-10-31
     * "최상의 엔터프라이즈급 스튜디오").
     */
    data class Snap(val draft: AppSettings, val extras: Map<PieceKind, Map<String, Any?>>, val label: String)

    val style: WorkStyle? = c.settingsRepository.current.workStyle

    sealed interface Origin {
        val title: String
        /** The phone's look now (and the theme worn, if one is). */
        data class Mine(override val title: String) : Origin
        /** The app's own look, nothing changed. */
        data object Plain : Origin { override val title = "처음 모양" }
        /** A theme kept in 테마 (내 테마 or 입었던 테마). */
        data class Theme(val wear: ThemeLease.Wear) : Origin { override val title = wear.title }
    }

    data class State(
        val draft: AppSettings,
        /** The look the draft started from, for 비교 and for 바뀐 곳. */
        val base: AppSettings,
        val origin: Origin,
        val pieces: List<Piece> = emptyList(),
        val basePieces: List<Piece> = emptyList(),
        val past: List<Snap> = emptyList(),
        val future: List<Snap> = emptyList(),
        /**
         * [STU-21] 1.36 레시피 만들기 합침: the 기능 pieces going with the look (근무 색·이모지, 알람 묶음, 메모 틀, 급여·현장·가게 규칙, 투자
         * 원칙, 앱 느낌, 회사 설정). They show on the example and go up with 올리기; 입히기 puts on the look only.
         */
        val extras: Map<PieceKind, Map<String, Any?>> = emptyMap(),
        /** [STU-21] The recipe this draft is a 수정판 of (고쳐 만들기 from 장터), or the code of mine it is the next 판 of. */
        val parentCode: String? = null,
        val ownCode: String? = null,
        /** [STU-21] The remade recipe's name, words, cover and 받는 분이 넣을 값 (its 기능 point at them), kept for 올리기. */
        val meta: Recipe? = null,
        val recent: List<Long> = emptyList(),
        /** [TRY-03] Others' parts laid on from 보관함: their theme's code to its name (출처 표시, and their days and ad on 입히기). */
        val sources: Map<String, String> = emptyMap(),
        /** The 보관함 items laid on, to tell what was changed by hand from what was taken. */
        val layered: List<ThemeWardrobe.Kept> = emptyList(),
        val busy: Boolean = false,
        /** One line said after 입히기 or 저장, with 되돌리기 for 입히기. */
        val said: String? = null,
        val canUndoWear: Boolean = false,
        /** F-94: the draft is what the phone wears now (just put on, nothing changed since). */
        val wornNow: Boolean = false,
    ) {
        val changed: Boolean get() = past.isNotEmpty() && (draft != base || extras.isNotEmpty())
    }

    private val phone = c.settingsRepository.current
    private val _state = MutableStateFlow(State(draft = phone, base = phone, origin = Origin.Mine(mineTitle())))
    val state: StateFlow<State> = _state.asStateFlow()
    private var beforeWear: Map<String, String>? = null

    init {
        when {
            start == START_PLAIN -> startPlain()
            start.startsWith(START_THEME) -> c.wardrobe.lease.value.history.firstOrNull { it.key == start.removePrefix(START_THEME) }
                ?.let { startFrom(it) } ?: refresh(both = true)
            // F-93: 마을 › 테마의 담아 둔 조각 › 꾸미기 opens the studio on my look with that part laid on.
            start.startsWith(START_KEPT) -> {
                refresh(both = true)
                c.wardrobe.kept.value.firstOrNull { it.id.toString() == start.removePrefix(START_KEPT) }?.let { layer(it) }
            }
            start.startsWith(START_RECIPE) -> {
                refresh(both = true)
                val remake = com.bamnat.app.recipe.RecipeLinks.remake.also { com.bamnat.app.recipe.RecipeLinks.remake = null }
                val own = start.removePrefix(START_RECIPE).substringBefore('@').removePrefix(":").ifEmpty { null }
                if (remake != null) startRecipe(remake.first, remake.second, own)
            }
            start.startsWith(START_FEATURE) -> {
                refresh(both = true)
                PieceKind.of(start.removePrefix(START_FEATURE))?.takeIf { it in FEATURES && (style == null || style in it.styles) }?.let { starter(it) }
            }
            start.startsWith(START_DRAFT) -> {
                refresh(both = true)
                viewModelScope.launch {
                    val id = start.removePrefix(START_DRAFT).toLongOrNull() ?: return@launch
                    val d = runCatching { c.database.recipeDao().drafts().firstOrNull { it.id == id } }.getOrNull() ?: return@launch
                    startRecipe(d.body, null, d.code)
                }
            }
            else -> refresh(both = true)
        }
    }

    private fun mineTitle(): String = c.wardrobe.lease.value.worn?.let { "지금 모양 · ${it.title}" } ?: "지금 내 모양"

    /** The look pieces of the draft (and of where it started), for the example phone. */
    private fun refresh(both: Boolean = false) = viewModelScope.launch {
        val s = _state.value
        val pieces = c.recipes.pieces.lookPieces(s.draft)
        val base = if (both) c.recipes.pieces.lookPieces(s.base) else s.basePieces
        _state.value = _state.value.copy(pieces = pieces, basePieces = base)
    }

    /** [STU-20] Where the next change is made (영역 · 도구), for the 기록 목록. */
    var where: String = "고침"

    private fun snap(s: State, label: String = where) = Snap(s.draft, s.extras, label)

    /** One change to the draft; 되돌리기 goes back over it (up to [HISTORY] steps). */
    fun change(f: (AppSettings) -> AppSettings) {
        val s = _state.value
        val next = f(s.draft)
        if (next == s.draft) return
        _state.value = s.copy(draft = next, past = (s.past + snap(s)).takeLast(HISTORY), future = emptyList(), said = null, wornNow = false)
        refresh()
    }

    // ---- [STU-21] 기능 (레시피 만들기에서 합침) ----

    /** The 기능 pieces this kind of work can carry, in the order the 기능 영역 shows them. */
    val features: List<PieceKind> = FEATURES.filter { style == null || style in it.styles }

    /** A 기능 on (what this phone has, or example values) or off (null). */
    fun setExtra(kind: PieceKind, values: Map<String, Any?>?, label: String = kind.label) {
        val s = _state.value
        val next = if (values == null) s.extras - kind else s.extras + (kind to values.filterValues { it != null })
        if (next == s.extras) return
        _state.value = s.copy(extras = next, past = (s.past + snap(s, label)).takeLast(HISTORY), future = emptyList(), said = null)
    }

    fun setExtraValue(kind: PieceKind, key: String, value: Any?) {
        val now = _state.value.extras[kind] ?: return
        setExtra(kind, if (value == null) now - key else now + (key to value), "${kind.label} · 고침")
    }

    /** What a 기능 starts with when turned on: this phone's own (its amounts become 받는 분이 넣을 값 on 올리기), or example values. */
    fun starter(kind: PieceKind) = viewModelScope.launch {
        val mine = runCatching { c.recipes.pieces.export(kind, com.bamnat.app.data.PersonalCalendar.MAIN) }.getOrNull()?.filterValues { it != null }
        setExtra(kind, mine?.takeIf { it.isNotEmpty() } ?: com.bamnat.app.ui.recipe.StudioStarters.of(kind, style), "${kind.label} 켬")
    }

    /** The example's recipe: the look and the 기능 that draw (근무 색, 메모 틀, 투자 일지). */
    fun shown(s: State = _state.value): Recipe = Recipe("초안", styles = WorkStyle.entries.toSet(),
        pieces = s.pieces + s.extras.map { (k, v) -> Piece(k, v) })

    /** [STU-20] 기록 목록: back to just before step [i] of [State.past] (the newest last); the steps after it can be done again. */
    fun backTo(i: Int) {
        val s = _state.value
        if (i !in s.past.indices) return
        val target = s.past[i]
        _state.value = s.copy(draft = target.draft, extras = target.extras, past = s.past.take(i),
            future = s.past.drop(i + 1) + snap(s) + s.future, said = null)
        refresh()
    }

    /** [STU-22] 이 영역만 처음대로: [fields] of the draft back to where it started (one step of 되돌리기). */
    fun resetFields(label: String, f: (draft: AppSettings, base: AppSettings) -> AppSettings) {
        val s = _state.value
        val next = f(s.draft, s.base)
        if (next == s.draft) return
        _state.value = s.copy(draft = next, past = (s.past + snap(s, label)).takeLast(HISTORY), future = emptyList(), said = null, wornNow = false)
        refresh()
    }

    /** A recipe piece's values on the draft (카드 배치, 아래 탭, 모서리, 위젯, 달력 테마). */
    fun setPiece(kind: PieceKind, values: Map<String, Any?>) = viewModelScope.launch {
        val next = c.recipes.pieces.onDraft(_state.value.draft, kind, values)
        change { next }
    }

    fun undo() {
        val s = _state.value
        val last = s.past.lastOrNull() ?: return
        _state.value = s.copy(draft = last.draft, extras = last.extras, past = s.past.dropLast(1), future = listOf(snap(s, last.label)) + s.future, said = null)
        refresh()
    }

    fun redo() {
        val s = _state.value
        val next = s.future.firstOrNull() ?: return
        _state.value = s.copy(draft = next.draft, extras = next.extras, past = s.past + snap(s, next.label), future = s.future.drop(1), said = null)
        refresh()
    }

    fun usedColor(c: Long) {
        val s = _state.value
        _state.value = s.copy(recent = (listOf(c) + s.recent.filter { it != c }).take(8))
    }

    // ---- where it starts ----

    fun startMine() = restart(c.settingsRepository.current, Origin.Mine(mineTitle()))

    fun startPlain() = restart(c.recipes.pieces.plainLook(c.settingsRepository.current), Origin.Plain)

    fun startFrom(w: ThemeLease.Wear) = viewModelScope.launch {
        val r = runCatching { RecipeCodec.read(w.body) }.getOrNull() ?: return@launch
        var d = c.recipes.pieces.plainLook(c.settingsRepository.current)
        r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }.forEach { p -> d = c.recipes.pieces.onDraft(d, p.kind, p.values) }
        restart(d, Origin.Theme(w))
    }

    /** Themes this phone keeps (내 테마 first), to start from. */
    fun kept(): List<ThemeLease.Wear> = c.wardrobe.lease.value.history.sortedByDescending { it.own }

    private fun restart(d: AppSettings, o: Origin) {
        _state.value = _state.value.copy(draft = d, base = d, origin = o, past = emptyList(), future = emptyList(), said = null, canUndoWear = false,
            sources = emptyMap(), layered = emptyList(), extras = emptyMap(), parentCode = null, ownCode = null, meta = null)
        refresh(both = true)
    }

    /**
     * [TRY-03] 보관함의 조각 얹기: [k]'s parts laid on the draft, the rest of the draft as it is. Someone else's part keeps where it came
     * from ([State.sources]): putting the look on follows that theme's days and ad, and 올리기 names it.
     */
    fun layer(k: ThemeWardrobe.Kept) = viewModelScope.launch {
        val r = runCatching { RecipeCodec.read(k.body) }.getOrNull() ?: return@launch
        var d = _state.value.draft
        r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }.forEach { p -> d = c.recipes.pieces.onDraft(d, p.kind, p.values) }
        change { d }
        val s = _state.value
        _state.value = s.copy(layered = s.layered + k, sources = if (k.own) s.sources else s.sources + (k.from to k.fromTitle),
            said = "‘${k.title}’을(를) 얹었어요")
    }

    // ---- 입히기 · 저장 · 올리기 ----

    /** The draft as a theme (its looks only). */
    fun recipe(title: String = _state.value.origin.title): Recipe = Recipe(title.take(30).ifBlank { "내가 꾸민 테마" }, "내가 꾸민 모양", "",
        WorkStyle.entries.toSet(), pieces = _state.value.pieces)

    /** Someone else's theme, not worn now: 입히기 follows that theme's days and ad ([needsAd]). */
    val othersTheme: ThemeLease.Wear?
        get() = (_state.value.origin as? Origin.Theme)?.wear?.takeIf { !it.own && it.key != c.wardrobe.lease.value.worn?.key }

    /** One of my themes, worn as itself (unchanged). */
    private val myTheme: ThemeLease.Wear?
        get() = (_state.value.origin as? Origin.Theme)?.wear?.takeIf { it.own }

    /**
     * F-101 F-104: the theme of mine this draft is a next 판 of: the one started from, or, from 지금 모양 (내 모양 고치기), the one of mine
     * worn now. 저장 keeps its name and 장터 link; 올리기 sends its next 판 instead of a new code.
     */
    val ownStart: ThemeLease.Wear?
        get() = myTheme ?: (_state.value.origin as? Origin.Mine)?.let {
            c.wardrobe.lease.value.worn?.takeIf { it.own }?.let { w -> c.wardrobe.lease.value.history.firstOrNull { h -> h.key == w.key } ?: w }
        }

    /** The theme whose days and ad 입히기 follows: someone else's started from, or the first of others' parts laid on. */
    private fun leasedFrom(): Pair<String, String>? = othersTheme?.let { it.code to it.title } ?: _state.value.sources.entries.firstOrNull()?.toPair()

    fun needsAd(): Boolean = when {
        leasedFrom() != null -> c.wardrobe.needsAd(shared = false)
        myTheme != null && !_state.value.changed -> c.wardrobe.needsAd(shared = myTheme?.free == true)
        else -> false
    }

    /**
     * [THM-02] Whether the draft is my own make: started from mine or the plain look, or changed by hand in [LookParts.OWN_PARTS] parts
     * beyond what others' theme or parts gave. Fewer, and it stays theirs (조합).
     */
    fun ownMake(): Boolean {
        val s = _state.value
        val others = othersTheme ?: (s.origin as? Origin.Theme)?.wear?.takeIf { !it.own }
        if (others == null && s.sources.isEmpty()) return true
        val now = recipe()
        val start = Recipe("", styles = WorkStyle.entries.toSet(), pieces = s.basePieces)
        val taken = s.layered.filter { !it.own }.mapNotNull { k -> runCatching { RecipeCodec.read(k.body) }.getOrNull() }
        val byHand = LookParts.changed(start, now).filter { id -> taken.none { t -> LookParts.pick(t, listOf(id)).pieces.let { it.isNotEmpty() && it == LookParts.pick(now, listOf(id)).pieces } } }
        return byHand.size >= LookParts.OWN_PARTS
    }

    /** [STU-12] 조합 자동 점검: the screens whose chosen 글자색 does not read on their ground (it would show as black or white). */
    fun hardToRead(): List<String> {
        val looks = ScreenLooks.decode(_state.value.draft.screenLooks)
        return looks.looks.keys.filter { k ->
            val l = looks.of(k)
            val ground = l.bg ?: 0xFFF7F8FCL
            l.ink != null && Palette.inkOn(l.ink, ground, l.card ?: ground) != l.ink
        }
    }

    /** 글자색 고치기: those screens' 글자색 left to the app (black or white, whichever reads). */
    fun fixInk() = change { d ->
        val looks = ScreenLooks.decode(d.screenLooks)
        val bad = hardToRead()
        d.copy(screenLooks = bad.fold(looks) { acc, k -> acc.with(k, (acc.looks[k] ?: return@fold acc).copy(ink = null)) }.encode())
    }

    /**
     * 내 앱에 입히기. The phone's look (or the plain one) goes straight on with 되돌리기 here, free; one of my themes unchanged is worn as
     * mine; someone else's theme, or others' parts laid on, as that theme (its ad already watched, [needsAd]).
     */
    fun wear() = viewModelScope.launch {
        val s = _state.value
        _state.value = s.copy(busy = true)
        val leased = leasedFrom()
        val mine = myTheme
        val done = runCatching {
            when {
                leased != null && !ownMake() -> c.wardrobe.wear(recipe(if (s.sources.isEmpty()) leased.second else "조합 · ${s.origin.title}"), leased.first, own = false)
                mine != null && !s.changed -> c.wardrobe.wear(recipe(mine.title), "", own = true, ver = mine.ver)
                else -> {
                    beforeWear = c.recipes.pieces.lookSnapshot()
                    c.recipes.pieces.restoreLook(c.recipes.pieces.lookOf(s.draft))
                    runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(c.appContext) }
                    true
                }
            }
        }.getOrDefault(false)
        // F-94: once on, the draft is the phone's look: 바꾼 곳 counts from here (the settings may still be being written, so the draft
        // itself is the new start, not a read of them).
        _state.value = _state.value.copy(busy = false, said = if (done) "내 앱에 입혔어요. 기록은 그대로예요" else "입히지 못했어요. 다시 해 주세요",
            canUndoWear = done && beforeWear != null, base = if (done) s.draft else _state.value.base, past = if (done) emptyList() else _state.value.past,
            future = emptyList(), wornNow = done)
        if (done) refresh(both = true)
    }

    /** 입히기 바로 뒤의 되돌리기: the look from just before, back on the phone and in the draft. */
    fun undoWear() {
        val before = beforeWear ?: return
        c.recipes.pieces.restoreLook(before)
        beforeWear = null
        val now = c.settingsRepository.current
        _state.value = _state.value.copy(draft = now, base = now, said = "입히기 전 모양으로 돌렸어요", canUndoWear = false, wornNow = false)
        refresh(both = true)
    }

    /**
     * 저장. My own make goes into 내 테마 (free one, 프로 more); one with too little changed from others' goes into 보관함 as a 조합 with
     * where it came from, so it keeps their days and ad ([THM-02]). [toPlaza]: one of mine already in 장터 sends its next 판 there too
     * ([THM-04] 사용자 2026-10-30 21:53 "자신의 테마를 수정할때 장터에 올라간 테마도 자동으로 업데이트"), through the same checks as 올리기.
     */
    fun save(title: String, toPlaza: Boolean = true) = viewModelScope.launch {
        val name = title.ifBlank { "내 테마" }.take(30)
        if (!ownMake()) {
            val from = leasedFrom() ?: return@launch
            val r = recipe("조합 · $name".take(30))
            val ok = c.wardrobe.keep(r.copy(title = from.second), from.first, LookParts.of(r).map { it.id })
            _state.value = _state.value.copy(said = if (ok) "직접 바꾼 곳이 ${LookParts.OWN_PARTS}곳보다 적어 ‘${from.second}’의 조합으로 보관함에 담았어요. 입힐 때는 그 테마처럼 30일이에요"
                else "보관함이 꽉 찼어요 (${c.wardrobe.partsLimit()}개)")
            return@launch
        }
        val shared = ownStart?.takeIf { it.free && it.title == name }
        val ok = c.wardrobe.saveLook(recipe(name), shared)
        if (!ok) {
            _state.value = _state.value.copy(said = "더 저장할 수 없어요. 무료는 ${c.wardrobe.outfitLimit()}벌까지예요")
            return@launch
        }
        _state.value = _state.value.copy(said = "‘$name’로 저장했어요. 마을 › 테마에 있어요")
        if (shared != null && toPlaza) sendNext(shared, name)
    }

    /** [THM-04] The next 판 of mine in 장터, its pictures left out when it went without them; a check that fails leaves 장터 as it was. */
    private suspend fun sendNext(w: ThemeLease.Wear, name: String) {
        // F-102: the next 판 keeps the title it has in 장터 (the 내 테마 name is the maker's own label).
        val title = runCatching { c.recipeApi.show(w.shared, c.companyStore.device).item.title }.getOrNull() ?: name
        val r0 = recipe(title).copy(styles = setOf(style ?: WorkStyle.SHIFT))
        val r = if (w.bare) LookParts.withoutPhotos(r0) else r0
        if (!RecipeLint.ready(RecipeLint.check(r))) {
            _state.value = _state.value.copy(said = "저장했어요. 장터 판은 고칠 곳이 있어 그대로예요. 공방 › 내 레시피에서 확인해 주세요")
            return
        }
        val sent = runCatching {
            val out = com.bamnat.app.ui.recipe.serverPictures(c, RecipeCodec.read(RecipeCodec.write(r)))
            c.recipeApi.newVersion(c.companyStore.device, w.shared, out, null)
        }.getOrNull()
        if (sent != null) c.wardrobe.markShared(ThemeLease.Wear(name, "", "", 0, 0, own = true).key, w.shared, sent.version, w.bare)
        _state.value = _state.value.copy(said = if (sent != null) "저장하고 장터 판도 ${sent.version}판으로 바꿨어요. 입은 분들께는 바꿀지 물어봐요"
            else "저장했어요. 장터 판은 인터넷에 연결되면 다시 저장해 주세요")
    }

    /** The draft is one of my themes already in 장터 (저장 also sends its next 판). */
    fun inPlaza(): Boolean = ownStart?.free == true

    /** F-101: the 장터 code 올리기 sends a next 판 to, when the draft is one of mine already there. */
    fun plazaCode(): String? = ownStart?.takeIf { it.free }?.shared

    fun mineCount(): Int = c.wardrobe.lease.value.history.count { it.own }
    fun outfitLimit(): Int = c.wardrobe.outfitLimit()

    /** 장터에 올리기: the draft as a recipe to finish in 만들기 (이름·설명·확인), with the theme it came from as its parent. */
    fun upload(): Pair<String, String> {
        val s = _state.value
        val parent = (s.parentCode ?: leasedFrom()?.first ?: (s.origin as? Origin.Theme)?.wear?.code).orEmpty().takeIf { !it.startsWith("builtin:") }.orEmpty()
        // [STU-21] The 기능 go up with the look; every amount in them becomes 받는 분이 넣을 값 first (S7), as 레시피 만들기 did.
        val pieces = (s.pieces + s.extras.map { (k, v) -> Piece(k, v) }).sortedBy { it.kind.ordinal }
        val withExtras = s.meta?.copy(pieces = pieces, parent = null)
            ?: recipe("").copy(title = "", styles = setOf(style ?: WorkStyle.SHIFT), pieces = pieces)
        val min = com.bamnat.app.ui.recipe.RecipeMakeViewModel.MIN_WAGE
        val r = com.bamnat.core.recipe.InputExtract.extract(withExtras) { _, f -> if (f == "wage") min else null }
        // [THM-01] One of mine (or the phone's look) sent: 만들기 marks it as in 장터 once it is up.
        com.bamnat.app.recipe.RecipeLinks.ownTheme = if (s.extras.isNotEmpty() || s.meta != null) null else ownStart?.key ?: c.wardrobe.ownWithLook(recipe())?.key
            ?: com.bamnat.app.recipe.RecipeLinks.NEW_OWN.takeIf { ownMake() }
        return RecipeCodec.write(r) to parent
    }

    fun said() { _state.value = _state.value.copy(said = null) }

    /** [STU-21] The code 올리기 sends a next 판 of: one of mine being remade, or one of mine already in 장터 ([plazaCode]). */
    fun nextOf(): String? = _state.value.ownCode ?: plazaCode()

    /**
     * [STU-21] 1.36 고쳐 만들기·새 판·만들던 레시피 open here too (레시피 만들기 합침): [body]'s looks on the plain look as the draft, its
     * 기능 as [State.extras], [parent] as what it is a 수정판 of, or [own] as mine whose next 판 it is.
     */
    fun startRecipe(body: String, parent: String?, own: String?) = viewModelScope.launch {
        val r = runCatching { RecipeCodec.read(body) }.getOrNull() ?: return@launch
        var d = c.recipes.pieces.plainLook(c.settingsRepository.current)
        r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }.forEach { p -> d = c.recipes.pieces.onDraft(d, p.kind, p.values) }
        restart(d, Origin.Mine(r.title.ifBlank { "만들던 판" }))
        val ex = r.pieces.filter { it.kind !in BuiltInThemes.LOOK_KINDS && it.kind != PieceKind.BUNDLE }.associate { it.kind to it.values }
        val from = parent?.takeIf { it.isNotBlank() && own == null && !it.startsWith("builtin:") }
        // Someone else's recipe remade keeps their days and ad on 입히기, as their theme would ([needsAd], [ownMake]).
        _state.value = _state.value.copy(extras = ex, parentCode = from, ownCode = own, meta = r,
            sources = if (from != null && r.pieces.any { it.kind in BuiltInThemes.LOOK_KINDS }) mapOf(from to r.title.ifBlank { "가져온 판" }) else emptyMap())
    }

    companion object {
        const val START_MINE = "mine"
        const val START_PLAIN = "plain"
        /** "theme:<key>": a theme kept in 테마 ([ThemeLease.Wear.key]). */
        const val START_THEME = "theme:"
        const val START_KEPT = "kept:"
        const val HISTORY = 50
        /** "recipe": open on the recipe left in [com.bamnat.app.recipe.RecipeLinks.remake] (고쳐 만들기, 새 판), "recipe:<code>" as mine. */
        const val START_RECIPE = "recipe"
        /** "draft:<id>": a 만들던 레시피 kept on the phone. */
        const val START_DRAFT = "draft:"
        /** "feature:<piece key>": 설정의 '이 설정 나누기' — the studio with that 기능 on (or the look as it is). */
        const val START_FEATURE = "feature:"
        /** A start for [START_RECIPE], one of a kind each time (the studio's ViewModel is kept per start). */
        fun recipeStart(own: String?): String = START_RECIPE + (own?.let { ":$it" } ?: "") + "@" + System.currentTimeMillis()
        /** [STU-21] The 기능 the studio can carry, in the order shown. */
        val FEATURES = listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS, PieceKind.MEMO_TEMPLATES, PieceKind.HOURLY_RULES, PieceKind.FIELD_SHEET,
            PieceKind.SHOP, PieceKind.INVEST, PieceKind.APP_FEEL, PieceKind.COMPANY)
    }
}
