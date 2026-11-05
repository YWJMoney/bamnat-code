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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

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
    /** [RUL-06] "area:RULES" opens the studio on that area (설정 › 알람 › 규칙·단추 → 스튜디오에서 열기). */
    val startArea: String? = if (start.startsWith(START_AREA)) start.removePrefix(START_AREA) else null

    /**
     * [STU-20] 1.36 one step of 되돌리기: the look and the 기능 at that point, and what was changed there (기록 목록, 사용자 2026-10-31
     * "최상의 엔터프라이즈급 스튜디오").
     */
    /** One step of 되돌리기; 1.36 (사용자 2026-10-31 조각 얹기 되돌리기) the 보관함 parts laid on and their 출처 go back with it. */
    data class Snap(val draft: AppSettings, val extras: Map<PieceKind, Map<String, Any?>>, val label: String,
        val layered: List<ThemeWardrobe.Kept> = emptyList(), val sources: Map<String, String> = emptyMap())

    val style: WorkStyle? = c.settingsRepository.current.workStyle

    sealed interface Origin {
        val title: String
        /** The phone's look now (and the theme worn, if one is). */
        data class Mine(override val title: String) : Origin
        /** The app's own look, nothing changed. */
        data object Plain : Origin { override val title = "처음 모양" }
        /** A theme kept in 테마 (내 테마 or 입었던 테마). */
        data class Theme(val wear: ThemeLease.Wear) : Origin { override val title = wear.title }
        /** [STU-35] 1.36 느낌으로 새로: a start made from three picks (분위기·색·칸 모양) on the app's own look. */
        data class Fresh(override val title: String) : Origin
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
        /** [STU-25] 1.36 빌드 7: the 만들던 레시피 this was opened from; 올리기 takes it off the list once sent. */
        val draftId: Long = 0,
        val recent: List<Long> = emptyList(),
        /** [TRY-03] Others' parts laid on from 보관함: their theme's code to its name (출처 표시, and their days and ad on 입히기). */
        val sources: Map<String, String> = emptyMap(),
        /** The 보관함 items laid on, to tell what was changed by hand from what was taken. */
        val layered: List<ThemeWardrobe.Kept> = emptyList(),
        val busy: Boolean = false,
        /** One line said after 입히기 or 저장, with 되돌리기 for 입히기. */
        val said: String? = null,
        val canUndoWear: Boolean = false,
        /** 1.36: the line said after 얹기 offers 되돌리기, which takes that one step back. */
        val canUndoLayer: Boolean = false,
        /** PC 14차 (얹은 것 빼기가 아무 안내 없이 빠짐): what was just taken off, so the line said can lay it on again. */
        val unlaid: ThemeWardrobe.Kept? = null,
        /** 1.36 PC 확인: the name the draft was last saved under, for 올리기 2/4 (saved, not put on). */
        val savedTitle: String? = null,
        /** 1.36 U3: saved or sent since the last change, so leaving does not ask to throw it away. */
        val kept: Boolean = false,
        /** 1.36 (사용자 2026-10-31 "누르면 전 모습, 떼면 후 모습"): the look one step back, for holding a finger on the example. */
        val prevPieces: List<Piece>? = null,
        /** F-94: the draft is what the phone wears now (just put on, nothing changed since). */
        val wornNow: Boolean = false,
        /** [STU-37] The example drawn as 야간 근무 중 or 쉬는 날 ([ShiftLooks]); null draws it as usual. */
        val modePieces: List<Piece>? = null,
        val previewMode: com.bamnat.core.recipe.ShiftLooks.Mode = com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL,
    ) {
        val changed: Boolean get() = past.isNotEmpty() && (draft != base || extras.isNotEmpty())
    }

    private val phone = c.settingsRepository.current
    /** PC 재검토 C10: open on 느낌으로 새로 만들기 (once). */
    var askFeel: Boolean by androidx.compose.runtime.mutableStateOf(false)
    /** [MAT-05] open on a font: 앱 전체 › 글꼴, 달력 › 색·배경 (이 화면 글꼴) or 달력 › 칸 디자인 (날짜 글꼴); once. */
    var openFont: String? by androidx.compose.runtime.mutableStateOf(null)

    private val _state = MutableStateFlow(State(draft = phone, base = phone, origin = Origin.Mine(mineTitle())))
    val state: StateFlow<State> = _state.asStateFlow()
    private var beforeWear: Map<String, String>? = null

    /** The 보관함 ids last seen on the studio screen, so what came in while 장터 was open is said on the way back. */
    var seenKept: Set<Long>? = null

    // 내 서랍 3c42b520 (조각 › 스튜디오에서 꾸미기가 꺼짐, NPE lockedNow): init lays a kept part on, which reads the locks, so they are
    // made before init.
    /** [STU-39] 1.36 영역 잠그기: the tabs ([com.bamnat.core.layout.TabOrder]) that 한 색·섞기·모든 화면에·조각 얹기 leave as they are. */
    // PC 확인 L5 (덮어 설치 뒤 잠금이 풀려 있음): kept on the phone as soon as it is set, so it holds next time too.
    private val lockPrefs = c.appContext.getSharedPreferences(LOCK_PREFS, android.content.Context.MODE_PRIVATE)
    private var lockedNow: Set<String> by androidx.compose.runtime.mutableStateOf(
        lockPrefs.getString("locked", null).orEmpty().split(',').filter { it in StudioColors.LOCKABLE }.toSet())
    var locked: Set<String>
        get() = lockedNow
        set(v) { lockedNow = v; lockPrefs.edit().putString("locked", v.joinToString(",")).apply() }

    init {
        when {
            start == START_PLAIN -> startPlain()
            start.startsWith(START_THEME) -> c.wardrobe.lease.value.history.firstOrNull { it.key == start.removePrefix(START_THEME) }
                ?.let { startFrom(it) } ?: refresh(both = true)
            // F-93: 마을 › 테마의 담아 둔 조각 › 꾸미기 opens the studio on my look with that part laid on.
            start.startsWith(START_KEPT) -> {
                refresh(both = true)
                // Laid on once the model is whole (layer runs at once on the main thread, and fields below init are not made yet).
                c.wardrobe.kept.value.firstOrNull { it.id.toString() == start.removePrefix(START_KEPT) }?.let { k ->
                    viewModelScope.launch { kotlinx.coroutines.yield(); layer(k) }
                }
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
            // PC 재검토 C10: 공방's '느낌으로 새로' opens the studio on the 느낌 picker.
            start == START_FEEL -> { refresh(both = true); askFeel = true }
            // PC 확인 4차 (B): 설정 검색 '글꼴' opens on 앱 전체 › 글꼴.
            start == START_FONT || start == START_SCREEN_FONT || start == START_DATE_FONT || start == START_MOTION || start == START_PARTS -> { refresh(both = true); openFont = start }
            start.startsWith(START_DRAFT) -> {
                refresh(both = true)
                start.removePrefix(START_DRAFT).toLongOrNull()?.let { resume(it) }
            }
            else -> refresh(both = true)
        }
        autosave()
    }

    /** [STU-34] 1.36 만들던 것 이어서 하기: a 만들던 레시피 (or the studio's own 자동 저장) back in the studio, its row kept up to date. */
    fun resume(id: Long) = viewModelScope.launch {
        val d = runCatching { c.database.recipeDao().drafts().firstOrNull { it.id == id } }.getOrNull() ?: return@launch
        startRecipe(d.body, null, d.code).join()
        // PC 확인 S1 (이어서 하기로 오면 '바꾼 곳 없음'에 되돌리기가 꺼짐): the phone's own look stays 처음 모양, one step back, so 바꾼 곳
        // and 되돌리기 go on from where they were.
        val s = _state.value
        val phone = c.settingsRepository.current
        _state.value = if (s.draft == phone && s.extras.isEmpty()) s.copy(draftId = id)
            else s.copy(draftId = id, base = phone, past = listOf(Snap(phone, emptyMap(), "이어서 하기 전 (지금 휴대폰 모양)")))
        refresh(both = true)
    }

    /**
     * [STU-34] The newest draft left in the studio (step 1: 스튜디오에서 꾸미던 중) other than the one open, from the last 14 days, to offer
     * 이어서 하기 on.
     */
    val resumable: StateFlow<com.bamnat.app.data.db.RecipeDraftEntity?> =
        kotlinx.coroutines.flow.combine(c.recipes.drafts, _state) { list, s ->
            list.filter { it.step <= 1 && it.id != s.draftId && com.bamnat.core.time.Today.millis() - it.updatedAt < 14L * 86_400_000L }
                .maxByOrNull { it.updatedAt }
        }.kotlinx_stateIn()

    private fun <T> kotlinx.coroutines.flow.Flow<T?>.kotlinx_stateIn(): StateFlow<T?> =
        stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000), null)

    /**
     * [STU-34] 1.36 자동 저장 (스튜디오 극상 조사 S5, Figma처럼 만들던 것이 사라지지 않게): a moment after each change the draft (모양과
     * 기능) is kept as a 만들던 레시피 at step 1, so leaving, the app closing or the phone restarting loses nothing; 이어서 하기 brings it
     * back. Once it is on the phone or saved as 내 테마 with nothing else in it, the row goes.
     */
    @OptIn(kotlinx.coroutines.FlowPreview::class)
    private fun autosave() = viewModelScope.launch {
        _state.map { s -> AutoKey(s.pieces, s.extras, s.changed, s.kept || s.wornNow) }
            .distinctUntilChanged()
            .debounce(AUTOSAVE_MS)
            .collect { k ->
                val s = _state.value
                when {
                    k.done && s.draftId != 0L && s.extras.isEmpty() && s.meta == null && s.ownCode == null -> {
                        runCatching { c.recipes.deleteDraft(s.draftId) }
                        _state.value = _state.value.copy(draftId = 0)
                    }
                    k.changed && !k.done && s.pieces.isNotEmpty() -> {
                        val r = (s.meta ?: recipe("")).copy(title = draftTitle(s), parent = null,
                            pieces = (s.pieces + s.extras.map { (kind, v) -> Piece(kind, v) }).sortedBy { it.kind.ordinal })
                        val body = RecipeCodec.write(r)
                        // 내 서랍 (PR #170): each '안 입히고 나가기' with the same look left one more '지금 모양에서 꾸미던 것'. A new studio
                        // whose draft is the same as one kept already takes that one instead of adding another.
                        val same = if (s.draftId != 0L) null else runCatching {
                            c.recipes.drafts.first().firstOrNull { it.step <= 1 && it.body == body }
                        }.getOrNull()
                        val id = same?.id ?: runCatching { c.recipes.saveDraft(s.draftId, body, 1, s.ownCode, "public") }.getOrNull() ?: return@collect
                        if (_state.value.draftId != id) _state.value = _state.value.copy(draftId = id)
                    }
                }
            }
    }

    private data class AutoKey(val pieces: List<Piece>, val extras: Map<PieceKind, Map<String, Any?>>, val changed: Boolean, val done: Boolean)

    /** The name a kept draft shows in 만들던 레시피 and 이어서 하기. */
    private fun draftTitle(s: State): String = (s.savedTitle ?: s.meta?.title?.takeIf { it.isNotBlank() }
        ?: (s.origin as? Origin.Theme)?.wear?.title?.let { "$it 고치는 중" }
        // PC 확인 Z6 (만들던 것 이름이 늘 '꾸미던 모양'): named by how it started, with the day.
        ?: when (val o = s.origin) {
            is Origin.Fresh -> o.title
            is Origin.Plain -> "처음 모양에서 새로"
            else -> "지금 모양에서 꾸미던 것"
        } + " · " + com.bamnat.core.time.Today.date().let { "${it.monthValue}/${it.dayOfMonth}" }).take(30)

    private fun mineTitle(): String = c.wardrobe.lease.value.worn?.let { "지금 모양 · ${it.title}" } ?: "지금 내 모양"

    /** The look pieces of the draft (and of where it started), for the example phone. */
    private fun refresh(both: Boolean = false) = viewModelScope.launch {
        val s = _state.value
        val pieces = c.recipes.pieces.lookPieces(s.draft)
        val base = if (both) c.recipes.pieces.lookPieces(s.base) else s.basePieces
        val prev = s.past.lastOrNull()?.draft?.let { c.recipes.pieces.lookPieces(it) }
        val mode = s.previewMode.takeIf { it != com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL }?.let { m ->
            val (screens, cal) = com.bamnat.core.recipe.ShiftLooks.decode(s.draft.shiftLooks).apply(m, s.draft.screenLooks, s.draft.calTheme)
            c.recipes.pieces.lookPieces(s.draft.copy(screenLooks = screens, calTheme = cal))
        }
        plainPieces = c.recipes.pieces.lookPieces(c.recipes.pieces.plainLook(s.draft))
        _state.value = _state.value.copy(pieces = pieces, basePieces = base, prevPieces = prev, modePieces = mode)
    }

    /** PC 확인 V22 1.36: the app's own look as pieces, so 올리기 leaves out what is just the app's (위젯 처음대로 and the like). */
    private var plainPieces: List<Piece> = emptyList()

    /** [STU-20] Where the next change is made (영역 · 도구), for the 기록 목록. */
    var where: String = "고침"

    private fun snap(s: State, label: String = where) = Snap(s.draft, s.extras, label, s.layered, s.sources)

    /** 1.36: the example one step back (누르고 있는 동안), with that step's 기능. */
    fun previous(s: State = _state.value): Recipe? = s.prevPieces?.let { p ->
        Recipe("전", styles = WorkStyle.entries.toSet(), pieces = p + (s.past.lastOrNull()?.extras ?: s.extras).map { (k, v) -> Piece(k, v) })
    }

    /** One change to the draft; 되돌리기 goes back over it (up to [HISTORY] steps). */
    fun change(f: (AppSettings) -> AppSettings) {
        merging = null
        val s = _state.value
        val next = f(s.draft)
        if (next == s.draft) return
        _state.value = s.copy(draft = next, past = (s.past + snap(s)).takeLast(HISTORY), future = emptyList(), said = null, wornNow = false, kept = false)
        refresh()
    }

    /** [STU-30] The label of the run of picks [changeMerged] is adding to, or null after any other step. */
    private var merging: String? = null

    /**
     * [STU-30][STU-32] 한 색·섞기·스타일 복사: picks in a row under the same [label] are one step of 되돌리기 (from before the first), so
     * trying colours does not fill the 기록 and one 되돌리기 goes back to before them all.
     */
    fun changeMerged(label: String, f: (AppSettings) -> AppSettings) {
        val s = _state.value
        if (merging == label && s.past.lastOrNull()?.label == label) {
            val next = keepLocked(s.draft, f(s.draft))
            if (next == s.draft) return
            _state.value = s.copy(draft = next, future = emptyList(), said = null, wornNow = false, kept = false)
            refresh()
            return
        }
        where = label
        change { keepLocked(it, f(it)) }
        if (_state.value.past.lastOrNull()?.label == label) merging = label
    }

    /** [STU-33] 스타일 복사: a screen's key and its colours as drawn, to paste on another screen. */
    var copiedLook: Pair<String, com.bamnat.core.recipe.ScreenLook>? by androidx.compose.runtime.mutableStateOf(null)

    /** One line said under the studio (no 되돌리기). */
    fun say(line: String) { _state.value = _state.value.copy(said = line, canUndoWear = false, canUndoLayer = false) }

    // ---- [STU-21] 기능 (레시피 만들기에서 합침) ----

    /** The 기능 pieces this kind of work can carry, in the order the 기능 영역 shows them. */
    val features: List<PieceKind> = FEATURES.filter { style == null || style in it.styles }

    /** A 기능 on (what this phone has, or example values) or off (null). */
    fun setExtra(kind: PieceKind, values: Map<String, Any?>?, label: String = kind.label) {
        val s = _state.value
        val next = if (values == null) s.extras - kind else s.extras + (kind to values.filterValues { it != null })
        if (next == s.extras) return
        _state.value = s.copy(extras = next, past = (s.past + snap(s, label)).takeLast(HISTORY), future = emptyList(), said = null, kept = false)
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
        pieces = (s.modePieces ?: s.pieces) + s.extras.map { (k, v) -> Piece(k, v) })

    /** [STU-37] Draws the example as [m] (야간 근무 중, 쉬는 날) or as usual. */
    fun previewAs(m: com.bamnat.core.recipe.ShiftLooks.Mode) {
        _state.value = _state.value.copy(previewMode = m)
        refresh()
    }

    private fun darkOf(d: AppSettings): Boolean = when (d.themeMode) {
        com.bamnat.app.data.ThemeMode.DARK -> true
        com.bamnat.app.data.ThemeMode.LIGHT -> false
        else -> (c.appContext.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    private fun keepLocked(prev: AppSettings, next: AppSettings): AppSettings {
        if (locked.isEmpty()) return next
        val dark = darkOf(prev)
        return locked.fold(next) { acc, tab -> StudioColors.hold(tab, acc, prev, dark) }
    }

    /** PC 확인 1.36: a locked [tab] that draws as it started — 바꾼 곳 says 잠가서 그대로, not the colours [StudioColors.hold] wrote out. */
    fun lockedSame(tab: String): Boolean = tab in locked && _state.value.let { StudioColors.sameLook(tab, it.draft, it.base, darkOf(it.base)) }

    /** PC 확인 S7: the 느낌 (변형, 어두운 화면) the last start was made with, so 한 색 opens on it. */
    var seedFeel: Pair<com.bamnat.core.recipe.ThemeSeed.Variant, Boolean>? = null

    /** PC 확인 W6 1.36 (느낌을 바꿔도 제목은 처음 느낌): the 느낌 picked since, with the start's title it belongs to. */
    var feelNow: Pair<String, String>? by androidx.compose.runtime.mutableStateOf(null)

    /** PC 확인 W8 1.36 (시작 느낌과 칩 이름이 달라 어느 게 처음인지 모름): the 한 색 느낌 the start was made with, by the start's title. */
    var feelStart: Pair<String, com.bamnat.core.recipe.ThemeSeed.Variant>? by androidx.compose.runtime.mutableStateOf(null)

    /** [STU-35] 느낌으로 새로: the app's own look, then [d] as its first step (so 되돌리기 shows the plain look and leaving asks). */
    fun startFeel(title: String = "느낌으로 새로 만든 모양", d: (AppSettings) -> AppSettings) {
        restart(c.recipes.pieces.plainLook(c.settingsRepository.current), Origin.Fresh(title))
        where = "느낌으로 시작"
        change(d)
    }

    /** [STU-20] 기록 목록: back to just before step [i] of [State.past] (the newest last); the steps after it can be done again. */
    fun backTo(i: Int) {
        merging = null
        val s = _state.value
        if (i !in s.past.indices) return
        val target = s.past[i]
        _state.value = s.copy(draft = target.draft, extras = target.extras, layered = target.layered, sources = target.sources, past = s.past.take(i),
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
        merging = null
        val s = _state.value
        val last = s.past.lastOrNull() ?: return
        _state.value = s.copy(draft = last.draft, extras = last.extras, layered = last.layered, sources = last.sources,
            past = s.past.dropLast(1), future = listOf(snap(s, last.label)) + s.future, said = null, canUndoLayer = false)
        refresh()
    }

    fun redo() {
        merging = null
        val s = _state.value
        val next = s.future.firstOrNull() ?: return
        _state.value = s.copy(draft = next.draft, extras = next.extras, layered = next.layered, sources = next.sources,
            past = s.past + snap(s, next.label), future = s.future.drop(1), said = null)
        refresh()
    }

    /** [STU-40] 1.36 내 색 모음: the colours pinned in the settings, newest first (at most [MY_COLORS]). */
    val myColors: StateFlow<List<Long>> = c.settingsRepository.settings.map { myColorsOf(it.myColors) }.distinctUntilChanged()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, myColorsOf(c.settingsRepository.current.myColors))

    /** [STU-40] Pins [color] to 내 색, or takes it away when it is there already. Not a step of 되돌리기: it is not part of the look. */
    fun pinColor(color: Long) {
        val col = color or 0xFF000000L
        c.settingsRepository.update { s ->
            val now = myColorsOf(s.myColors)
            val next = if (col in now) now - col else (listOf(col) + now).take(MY_COLORS)
            s.copy(myColors = next.joinToString(",") { (it and 0xFFFFFFFFL).toString(16).uppercase().padStart(8, '0') })
        }
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
        merging = null
        _state.value = _state.value.copy(draft = d, base = d, origin = o, past = emptyList(), future = emptyList(), said = null, canUndoWear = false,
            sources = emptyMap(), layered = emptyList(), extras = emptyMap(), parentCode = null, ownCode = null, meta = null,
            // [STU-34] A new start is a new 자동 저장, never written over the one left before.
            draftId = 0, savedTitle = null, kept = false, previewMode = com.bamnat.core.recipe.ShiftLooks.Mode.NORMAL, modePieces = null,
            // PC 확인 8d0cddf6 ('처음 모양에서' 직후 '✓ 지금 내 앱에 입힌 모양'): a new start is not what the app wears.
            wornNow = false)
        refresh(both = true)
    }

    /** PC 8차 (따옴표가 ‘‘달력 머리 부품’의 부품 · 달력 머리’처럼 겹침): a kept title that already quotes its theme is not quoted again. */
    private fun quoted(t: String): String = if (t.startsWith("‘")) t else "‘$t’"

    /**
     * [TRY-03] 보관함의 조각 얹기: [k]'s parts laid on the draft, the rest of the draft as it is. Someone else's part keeps where it came
     * from ([State.sources]): putting the look on follows that theme's days and ad, and 올리기 names it.
     */
    fun layer(k: ThemeWardrobe.Kept) = viewModelScope.launch {
        val r = runCatching { RecipeCodec.read(k.body) }.getOrNull() ?: return@launch
        var d = _state.value.draft
        var merged: String? = null
        var overlap = emptySet<String>()
        r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }.forEach { p ->
            // [BLU-07] PC 7차: a 부품 laid on changes only its own units; the parts I made elsewhere stay.
            d = if (p.kind == com.bamnat.core.recipe.PieceKind.PARTS) {
                @Suppress("UNCHECKED_CAST")
                val top = (com.bamnat.core.recipe.RecipeResolve.piece(r, p, com.bamnat.core.recipe.RecipeResolve.answers(r, emptyMap()))["parts"] as? List<Map<String, Any?>>).orEmpty()
                val (laid, both) = com.bamnat.core.recipe.Blueprint.decode(d.parts).layUnits(top)
                overlap = overlap + both
                runCatching { d.copy(parts = com.bamnat.core.recipe.Blueprint(com.bamnat.core.recipe.Blueprint.clean(laid.lines)).encode()) }.getOrDefault(d)
            } else c.recipes.pieces.onDraft(d, p.kind, p.values)
        }
        // [RUL-07] 규칙·단추 are added to mine (the same one twice is kept once), up to the limits; they never take mine away.
        r.pieces.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.LOGIC }?.let { p ->
            val mine = com.bamnat.core.formula.Logic.decode(d.logic)
            // [SEC-12] 보관함으로 받은 남의 규칙도 장터에서 바로 넣을 때와 같아요: 가계부 100만 원까지, 메모·가계부를 쓰는 새 규칙은 꺼진 채로.
            val got = com.bamnat.core.formula.Logic.from(p.values).let { if (k.own) it else it.received() }
            if (!k.own) {
                val had = mine.rules.map { it.key }.toSet()
                val store = com.bamnat.app.logic.LogicStore(c.appContext)
                got.writingRules.filter { it.key !in had }.forEach { store.gotOff(it.key) }
                store.markGot(got.rules.map { it.key }.filter { it !in had })
                store.noteSame(mine, got)
            }
            d = d.copy(logic = com.bamnat.core.formula.Logic.encode(mine.plus(got)))
            merged = mine.mergeSay(got)
        }
        val was = _state.value.draft
        where = "보관함 · ${quoted(k.title)} 얹기"
        // [STU-39] A locked tab keeps its look when a part is laid on.
        change { keepLocked(it, d) }
        val s = _state.value
        // Nothing changed (the same part again): no step to take back.
        val stepped = s.draft != was
        // 1.36 U17: a part the look already has changes nothing; it says so and is not counted as laid on.
        if (!stepped) { _state.value = s.copy(said = merged ?: "${quoted(k.title)}${com.bamnat.app.ui.Fmt.eun(k.title)} 이미 지금 모양과 같아요", canUndoLayer = false); return@launch }
        val same = overlap.mapNotNull { com.bamnat.core.recipe.BlueprintParts.LABELS[it] }.joinToString("·")
        // PC D4: 규칙·단추만 얹은 것은 테마가 아니라서 입히기가 그 테마 기간·광고를 따르지 않아요 (출처는 레시피에 남아요).
        val logicOnly = LookParts.partUnit(r) == LookParts.LOGIC
        val writesOff = !k.own && r.pieces.any { p -> p.kind == com.bamnat.core.recipe.PieceKind.LOGIC && com.bamnat.core.formula.Logic.from(p.values).writingRules.isNotEmpty() }
        val tail = if (!logicOnly) "" else if (writesOff) ". 메모·가계부를 바꾸는 규칙은 꺼진 채로 들어가요. 입힌 뒤 설정 › 수식·규칙·버튼에서 켜요" else ". 입히면 규칙이 켜져요"
        _state.value = s.copy(layered = s.layered + k, sources = if (k.own || logicOnly) s.sources else s.sources + (k.from to k.fromTitle),
            // PC 9차 사용성 제안 (얹기 안내가 두 줄로 길어요): a single part says its own name, and the rest in a few words.
            said = (if (logicOnly) "${quoted(k.title)}${com.bamnat.app.ui.Fmt.obj(k.title)} 얹었어요" + tail
                else (k.parts.singleOrNull()?.let { LookParts.byId(it)?.label?.removePrefix("부품 · ") }?.let { "‘$it’ 부품을 얹었어요" }
                    ?: "${quoted(k.title)}${com.bamnat.app.ui.Fmt.obj(k.title)} 얹었어요") +
                    (if (same.isNotEmpty()) " · 내 ‘$same’ 꾸밈은 바뀜, 되돌리기로 돌아가요" else " · 다른 부품은 그대로")) + merged?.let { ". $it" }.orEmpty(),
            canUndoLayer = stepped)
    }

    /**
     * 1.36 U1·U2 (빼기): takes [k] off again. Laid on last, it is one step of 되돌리기; laid on earlier, its parts go back to how they
     * were just before it was laid on, and the changes made since stay.
     */
    fun unlayer(k: ThemeWardrobe.Kept) = viewModelScope.launch {
        val s = _state.value
        if (k !in s.layered) return@launch
        if (s.layered.last() == k && s.past.lastOrNull()?.let { k !in it.layered } == true) {
            undo()
            _state.value = _state.value.copy(said = "${quoted(k.title)}${com.bamnat.app.ui.Fmt.obj(k.title)} 뺐어요", unlaid = k)
            return@launch
        }
        val before = s.past.lastOrNull { k !in it.layered } ?: run {
            _state.value = s.copy(said = "오래전에 얹은 조각이라 뺄 수 없어요. 기록에서 그 앞으로 돌아가 주세요"); return@launch
        }
        val was = Recipe("전", styles = WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(before.draft))
        var d = s.draft
        // [RUL-07] 규칙·단추: what it added comes off; the rest of mine stays.
        runCatching { RecipeCodec.read(k.body) }.getOrNull()?.pieces?.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.LOGIC }?.let { p ->
            val got = com.bamnat.core.formula.Logic.from(p.values).let { if (k.own) it else it.received() }
            d = d.copy(logic = com.bamnat.core.formula.Logic.encode(com.bamnat.core.formula.Logic.decode(d.logic).minus(got)))
        }
        LookParts.pick(was, k.parts.ifEmpty { LookParts.of(was).map { it.id } }).pieces.forEach { p ->
            // [BLU-07] PC 7차: only the units the part brought go back; the parts changed since stay.
            d = if (p.kind == com.bamnat.core.recipe.PieceKind.PARTS) {
                val laid = runCatching { com.bamnat.core.recipe.RecipeCodec.read(k.body) }.getOrNull()?.pieces?.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.PARTS }
                @Suppress("UNCHECKED_CAST")
                val units = com.bamnat.core.recipe.Blueprint((laid?.values?.get("parts") as? List<Map<String, Any?>>).orEmpty()).units
                val back = com.bamnat.core.recipe.Blueprint.decode(before.draft.parts).lines.filter { com.bamnat.core.recipe.BlueprintParts.unitOf(it["id"] as String) in units }
                val keep = com.bamnat.core.recipe.Blueprint.decode(d.parts).lines.filter { com.bamnat.core.recipe.BlueprintParts.unitOf(it["id"] as String) !in units }
                runCatching { d.copy(parts = com.bamnat.core.recipe.Blueprint(com.bamnat.core.recipe.Blueprint.clean((keep + back).take(com.bamnat.core.recipe.BlueprintParts.MAX_LINES))).encode()) }.getOrDefault(d)
            } else c.recipes.pieces.onDraft(d, p.kind, p.values)
        }
        where = "보관함 · ${quoted(k.title)} 빼기"
        change { d }
        val now = _state.value
        val left = now.layered - k
        _state.value = now.copy(layered = left,
            sources = if (k.own || left.any { it.from == k.from }) now.sources else now.sources - k.from,
            said = "${quoted(k.title)}${com.bamnat.app.ui.Fmt.obj(k.title)} 뺐어요", unlaid = k)
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
    /**
     * [THM-08] A mixed theme of mine the draft starts from: opened from 내 테마, or worn now as the phone's look. It stays others'
     * work (30일, 수정판) until [LookParts.OWN_PARTS] parts are changed by hand from it.
     */
    private fun mixedStart(): ThemeLease.Wear? = when (val o = _state.value.origin) {
        is Origin.Theme -> o.wear.takeIf { it.own && it.mixedFrom.isNotBlank() }
        is Origin.Mine -> c.wardrobe.lease.value.worn?.takeIf { it.own && it.mixedFrom.isNotBlank() }
        else -> null
    }

    /** [THM-08] Every other maker's theme the draft took parts from, by name. */
    fun mixedSources(): List<String> =
        (listOfNotNull(othersTheme?.title) + _state.value.sources.values + mixedStart()?.mixedFrom?.split("·").orEmpty())
            .filter { it.isNotBlank() }.distinct()

    /** [THM-08] Their 장터 codes, in the same order (built-in looks have none). */
    private fun mixedCodes(): List<String> =
        (listOfNotNull(othersTheme?.code) + _state.value.sources.keys + mixedStart()?.mixedCodes?.split(",").orEmpty())
            .filter { it.isNotBlank() && !it.startsWith("builtin:") }.distinct()

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
        if (others == null && s.sources.isEmpty() && mixedStart() == null) return true
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
                    beforeWear?.let { com.bamnat.app.data.LookUndo.keep(c.appContext, it) }
                    c.recipes.pieces.restoreLook(c.recipes.pieces.lookOf(s.draft))
                    runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(c.appContext) }
                    true
                }
            }
        }.getOrDefault(false)
        // PC D4 (높): 규칙·단추는 모양이 아니라 테마 입기에 들어가지 않아요. 입히기가 스튜디오에서 만든 그대로 저장해요.
        val logicSaid = if (done) wearLogic(s.draft.logic) else null
        // F-94: once on, the draft is the phone's look: 바꾼 곳 counts from here (the settings may still be being written, so the draft
        // itself is the new start, not a read of them).
        _state.value = _state.value.copy(busy = false, said = if (done) "내 앱에 입혔어요. 기록은 그대로예요" + logicSaid.orEmpty() else "입히지 못했어요. 다시 해 주세요",
            canUndoWear = done && beforeWear != null, base = if (done) s.draft else _state.value.base, past = if (done) emptyList() else _state.value.past,
            future = emptyList(), wornNow = done)
        if (done) refresh(both = true)
    }

    /**
     * PC D10 (사용성 중): 규칙·단추는 휴대폰 것이라 스튜디오에서 고치면 바로 저장돼요. 설정 › 수식·규칙·버튼과 같은 목록이고, 규칙만 바꾸고
     * 나갈 때 입힐지 묻지 않아요. 지난 단계에도 같은 규칙을 넣어 되돌리기가 규칙을 되돌리지 않게 해요(규칙은 줄마다 빼기·끄기로).
     */
    fun changeLogic(l: com.bamnat.core.formula.Logic) {
        val enc = com.bamnat.core.formula.Logic.encode(l)
        c.settingsRepository.update { it.copy(logic = enc) }
        com.bamnat.app.logic.LogicRunner.rulesChanged(c)
        withLogic(enc)
    }

    /** PC D10: 장터에서 넣는 등 스튜디오 밖에서 바뀐 규칙을 화면에 돌아올 때 받아요 (스튜디오에서 얹고 아직 안 입힌 것이 없을 때). */
    fun syncLogic() {
        val now = c.settingsRepository.current.logic
        val s = _state.value
        if (s.draft.logic == s.base.logic && now != s.draft.logic) withLogic(now)
    }

    private fun withLogic(enc: String) {
        val s = _state.value
        fun AppSettings.l() = copy(logic = enc)
        _state.value = s.copy(draft = s.draft.l(), base = s.base.l(), past = s.past.map { it.copy(draft = it.draft.l()) },
            future = s.future.map { it.copy(draft = it.draft.l()) })
    }

    /** The rules and buttons before the last 입히기, for 되돌리기. */
    private var beforeLogic: String? = null

    /** Puts the draft's 규칙·단추 on (they stay with the phone, not a theme) and says what is on now: " · 규칙 2개·단추 1개가 켜졌어요". */
    private fun wearLogic(draft: String): String? {
        val now = c.settingsRepository.current.logic
        // PC D10: 입히기는 규칙을 더하기만 해요 (스튜디오를 연 뒤 장터에서 넣은 규칙이 지워지지 않게). 빼기는 줄마다 바로 저장돼요.
        val logic = com.bamnat.core.formula.Logic.encode(com.bamnat.core.formula.Logic.decode(now).plus(com.bamnat.core.formula.Logic.decode(draft)))
        if (now == logic) return null
        beforeLogic = now
        c.settingsRepository.update { it.copy(logic = logic) }
        val l = com.bamnat.core.formula.Logic.decode(logic)
        return if (l.isEmpty) " · 규칙과 단추를 모두 껐어요"
        else " · " + listOfNotNull(l.rules.size.takeIf { it > 0 }?.let { "규칙 ${it}개" }, l.buttons.size.takeIf { it > 0 }?.let { "단추 ${it}개" }).joinToString("·") + "가 켜졌어요"
    }

    /** 입히기 바로 뒤의 되돌리기: the look from just before, back on the phone and in the draft. */
    fun undoWear() {
        val before = beforeWear ?: return
        beforeLogic?.let { l -> c.settingsRepository.update { it.copy(logic = l) } }
        beforeLogic = null
        c.recipes.pieces.restoreLook(before)
        beforeWear = null
        com.bamnat.app.data.LookUndo.clear(c.appContext)
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
        // [THM-08] 1.36 (사용자 승인 섞은 테마): parts of two or more themes mixed are mine to keep in 내 테마 under my name, every
        // theme named; mostly others' parts, so it wears by the 30-day rule and 장터 takes it only as a 수정판.
        val mixed = mixedSources()
        if (!ownMake() && mixed.size >= 2) {
            val ok = c.wardrobe.saveLook(recipe(name), mixedStart()?.takeIf { it.title == name }, mixedFrom = mixed.joinToString("·"),
                mixedCodes = mixedCodes().joinToString(","))
            if (ok) _state.value = _state.value.copy(savedTitle = name, kept = true)
            _state.value = _state.value.copy(said = if (!ok) "더 저장할 수 없어요. 무료는 ${c.wardrobe.outfitLimit()}벌까지예요"
                else "‘$name’로 내 테마에 담았어요. 출처: ${mixed.joinToString("·") { "‘$it’" }}. 대부분 다른 분 조각이라 입을 때는 30일 규칙이에요(프로는 광고 없이)")
            return@launch
        }
        if (!ownMake()) {
            val from = leasedFrom() ?: return@launch
            val r = recipe("조합 · $name".take(30))
            val ok = c.wardrobe.keep(r.copy(title = from.second), from.first, LookParts.of(r).map { it.id })
            // [STU-27] 1.36 빌드 8 (S32: 두 테마의 조각을 얹었는데 출처가 하나만): every theme a part came from is named.
            val froms = (_state.value.sources.values.toList() + from.second).distinct().joinToString("·") { "‘$it’" }
            _state.value = _state.value.copy(said = if (ok) "직접 바꾼 곳이 ${LookParts.OWN_PARTS}곳보다 적어 $froms 조각의 조합으로 보관함에 담았어요. 입힐 때는 그 테마처럼 30일이에요"
                else "보관함이 꽉 찼어요 (${c.wardrobe.partsLimit()}개)")
            return@launch
        }
        val shared = ownStart?.takeIf { it.free && it.title == name }
        val ok = c.wardrobe.saveLook(recipe(name), shared)
        if (!ok) {
            _state.value = _state.value.copy(said = "더 저장할 수 없어요. 무료는 ${c.wardrobe.outfitLimit()}벌까지예요")
            return@launch
        }
        _state.value = _state.value.copy(said = "‘$name’${com.bamnat.app.ui.Fmt.ro(name)} 저장했어요. 마을 › 테마에 있어요", savedTitle = name, kept = true)
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
            com.bamnat.app.recipe.RecipeFonts.send(c, out)
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
    /**
     * [STU-26] 1.36 빌드 8 (S29: 가계부만 바꿔 올렸는데 테마 전체가 올라감): the one tab every change of the look is on, so 올리기 can send
     * that tab alone as a 기능별 탭; null when nothing changed or the changes are on more than one tab.
     */
    fun changedTab(): String? = changedTabs().singleOrNull()?.takeIf { changedTabsAll().size == 1 }

    /**
     * 사용자 2026-11-01 ("개별 가계부·달력·메모·근무 급여는 해당 탭만 만들어서 올릴 수도"): every tab the changes are on (설정 aside), so
     * 올리기 can send any one of them alone as a 기능별 탭, also when more than one tab was changed.
     */
    fun changedTabs(): List<String> = changedTabsAll().filter { it != com.bamnat.core.layout.TabOrder.SETTINGS }

    private fun changedTabsAll(): List<String> {
        val s = _state.value
        val all = WorkStyle.entries.toSet()
        val ids = com.bamnat.core.recipe.LookParts.changed(Recipe("b", styles = all, pieces = s.basePieces), Recipe("d", styles = all, pieces = s.pieces))
        return com.bamnat.core.recipe.LookParts.ALL.filter { it.id in ids }.map { it.tab }.distinct()
    }

    /** [onlyTab] sends that tab's looks alone (a 기능별 탭, no 기능); null sends the whole theme with its 기능. */
    fun upload(onlyTab: String? = null): Pair<String, String> {
        val s = _state.value
        // [THM-08] A mixed theme goes up as a 수정판 of the first theme it took parts from.
        val parent = (s.parentCode ?: leasedFrom()?.first ?: mixedCodes().firstOrNull().takeIf { !ownMake() }
            ?: (s.origin as? Origin.Theme)?.wear?.code).orEmpty().takeIf { !it.startsWith("builtin:") }.orEmpty()
        // [STU-21] The 기능 go up with the look; every amount in them becomes 받는 분이 넣을 값 first (S7), as 레시피 만들기 did.
        val pieces = if (onlyTab != null) {
            val draft = Recipe("d", styles = WorkStyle.entries.toSet(), pieces = s.pieces)
            com.bamnat.core.recipe.LookParts.pick(draft, com.bamnat.core.recipe.LookParts.of(draft).filter { it.tab == onlyTab }.map { it.id }).pieces
        // PC 확인 V22 1.36 (안 바꾼 위젯 조각까지 작품에 실림): a look piece just as the app has it is not part of the theme.
        } else (s.pieces.filter { p -> plainPieces.none { it.kind == p.kind && it.values == p.values } } + s.extras.map { (k, v) -> Piece(k, v) })
            .sortedBy { it.kind.ordinal }
        val withExtras = s.meta?.copy(pieces = pieces, parent = null)
            // 빌드 e382585e (올리기 2/4의 이름 칸이 비어 있어요): one of my themes goes up under its name.
            ?: recipe("").copy(title = s.savedTitle ?: (ownStart ?: mixedStart())?.title.orEmpty(), styles = setOf(style ?: WorkStyle.SHIFT), pieces = pieces,
                // [STU-41] The 표지 starts in the look's own colours (바탕 → 강조), not the app's blue and purple.
                cover = coverOf(s.draft))
        // [THM-08] 302576c8 PC 확인 (섞은 테마가 수정판 표시 없이 올라감): a mixed theme names every theme it took from in its 설명,
        // also when they are built-in looks with no 장터 code to be a 수정판 of.
        val mixed = mixedSources()
        // b62b9869 PC 확인 (설명이 원래 것 그대로): the 출처 line goes first, before what the description already said.
        val note = "출처: " + mixed.joinToString("·") { "‘$it’" }.let { it + com.bamnat.app.ui.Fmt.obj(it) } + " 섞었어요"
        val named = if (!ownMake() && mixed.size >= 2 && !withExtras.desc.startsWith("출처:"))
            withExtras.copy(desc = listOf(note, withExtras.desc.trim()).filter { it.isNotEmpty() }.joinToString("\n").take(300)) else withExtras
        val min = com.bamnat.app.ui.recipe.RecipeMakeViewModel.MIN_WAGE
        val r = com.bamnat.core.recipe.InputExtract.extract(named) { _, f -> if (f == "wage") min else null }
        // [THM-01] One of mine (or the phone's look) sent: 만들기 marks it as in 장터 once it is up.
        // [THM-08] A mixed theme (fewer than 3 changes by hand) goes up only as a 수정판: never marked as mine in 장터 (no ad-free).
        com.bamnat.app.recipe.RecipeLinks.ownTheme = if (onlyTab != null || s.extras.isNotEmpty() || s.meta != null || !ownMake()) null
            else ownStart?.key ?: c.wardrobe.ownWithLook(recipe())?.key ?: com.bamnat.app.recipe.RecipeLinks.NEW_OWN
        return RecipeCodec.write(r) to parent
    }

    /**
     * [BLU-07] 1.36 이 부품만 올리기 (사용자 2026-11-01): the draft's lines of one 부품 [unit] as a recipe of its own, for 장터 '부품'.
     * Null when the draft has nothing in that 부품 yet.
     */
    /** [RUL-07] 규칙·단추만 장터에 올리기 (장터 '규칙·단추' 구간): the draft's buttons and rules alone, for every kind of work. */
    fun uploadLogic(): String? {
        val l = com.bamnat.core.formula.Logic.decode(_state.value.draft.logic)
        if (l.isEmpty) return null
        com.bamnat.app.recipe.RecipeLinks.ownTheme = null
        val title = (l.rules.firstOrNull()?.name ?: l.buttons.first().label).take(20) + " 규칙·단추"
        return RecipeCodec.write(recipe("").copy(title = title.take(30), summary = "규칙 ${l.rules.size}개 · 단추 ${l.buttons.size}개", styles = WorkStyle.entries.toSet(),
            pieces = listOf(com.bamnat.core.recipe.Piece(com.bamnat.core.recipe.PieceKind.LOGIC, l.toValues())), cover = coverOf(_state.value.draft)))
    }

    /**
     * [CNV-10] 1.37 내 틀 (사용자 2026-11-04 "사용자가 틀을 만들 수 있게"): the draft's screen structure without its colours: each page's
     * cards in order and hidden ones, and the parts made one by one. What 내 틀로 담기 keeps and 틀 올리기 sends.
     */
    fun frameIds(): List<String> {
        val draft = Recipe("d", styles = WorkStyle.entries.toSet(), pieces = _state.value.pieces)
        return com.bamnat.core.recipe.LookParts.of(draft).map { it.id }
            .filter { it.endsWith(".cards") || it.startsWith(com.bamnat.core.recipe.LookParts.UNIT) }
    }

    /** [CNV-10] 내 틀로 담기: the frame into 보관함 as mine (no 장터 source), to lay on any theme later. */
    fun keepFrame(name: String): Boolean {
        val ids = frameIds()
        if (ids.isEmpty()) {
            _state.value = _state.value.copy(said = "아직 옮기거나 숨긴 카드가 없어 틀로 담을 것이 없어요")
            return false
        }
        val title = name.ifBlank { "내 틀" }.take(24)
        val ok = c.wardrobe.keep(recipe(title), "", ids)
        _state.value = _state.value.copy(said = if (ok) "‘$title’ 틀을 보관함에 담았어요. 스튜디오 ‘보관함’에서 어느 테마에든 얹어요"
            else "보관함이 꽉 찼어요 (${c.wardrobe.partsLimit()}개)")
        return ok
    }

    /** [CNV-10] 틀 올리기: the frame alone as a 장터 work (its cover in the draft's colours). */
    fun frameBody(name: String): String? {
        val ids = frameIds()
        if (ids.isEmpty()) return null
        val s = _state.value
        val only = com.bamnat.core.recipe.LookParts.pick(Recipe("d", styles = WorkStyle.entries.toSet(), pieces = s.pieces), ids)
        if (only.pieces.isEmpty()) return null
        com.bamnat.app.recipe.RecipeLinks.ownTheme = null
        val title = name.ifBlank { "내 틀" }.take(24)
        return RecipeCodec.write(recipe("").copy(title = title, summary = "카드 자리와 부품만 담은 틀이에요. 색은 지금 테마 그대로예요",
            styles = WorkStyle.entries.toSet(), pieces = only.pieces, cover = coverOf(s.draft)))
    }

    fun uploadPart(unit: String): String? {
        val s = _state.value
        val draft = Recipe("d", styles = WorkStyle.entries.toSet(), pieces = s.pieces)
        val only = com.bamnat.core.recipe.LookParts.pick(draft, listOf(com.bamnat.core.recipe.LookParts.UNIT + unit))
        if (only.pieces.isEmpty()) return null
        val label = com.bamnat.core.recipe.BlueprintParts.LABELS[unit] ?: unit
        com.bamnat.app.recipe.RecipeLinks.ownTheme = null
        return RecipeCodec.write(recipe("").copy(title = "$label 부품", summary = "$label 하나만 꾸민 부품이에요", styles = WorkStyle.entries.toSet(),
            pieces = only.pieces, cover = coverOf(s.draft)))
    }

    /** [STU-41] A 표지 in the draft's colours: '앱 전체' 강조 (or 오늘) to its 바탕, the app's own where it has none. */
    private fun coverOf(d: AppSettings): com.bamnat.core.recipe.Cover {
        val all = ScreenLooks.decode(d.screenLooks).looks[ScreenLooks.ALL]
        val accent = all?.accent ?: com.bamnat.core.recipe.CalendarTheme.decode(d.calTheme).today
        val plain = com.bamnat.core.recipe.Cover()
        if (accent == null && all?.bg == null) return plain
        val emoji = when (style) { WorkStyle.HOURLY -> "☕"; WorkStyle.FIELD -> "🛠️"; WorkStyle.BUSINESS -> "🏪"; WorkStyle.INVEST -> "📈"; WorkStyle.DAY -> "🗓️"; else -> "🌙" }
        return plain.copy(a = accent ?: plain.a, b = all?.bg ?: plain.b, dir = 2, emoji = emoji)
    }

    /** 1.36 U3: sent to 장터 from 올리기. */
    fun markKept() {
        // PC 확인 W30 1.36 (다 올렸는데 다음에 '만들던 ‘꾸미던 모양’' 카드가 다시 뜸): what went up is no longer a 만들던 것.
        val id = _state.value.draftId
        _state.value = _state.value.copy(kept = true, draftId = 0)
        if (id != 0L) viewModelScope.launch { runCatching { c.recipes.deleteDraft(id) } }
    }

    fun said() { _state.value = _state.value.copy(said = null, canUndoLayer = false, unlaid = null) }

    /** 1.36: 되돌리기 on the line said after 얹기: the same step as the 되돌리기 button above, layered parts and 출처 included. */
    fun undoLayer() { if (_state.value.canUndoLayer) undo() }

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
        /** [STU-34] How long after the last change the draft is kept. */
        const val AUTOSAVE_MS = 1_200L
        const val MY_COLORS = 12
        fun myColorsOf(s: String): List<Long> = s.split(',').mapNotNull { h -> h.trim().takeIf { it.length == 8 }?.toLongOrNull(16)?.let { it or 0xFF000000L } }.distinct()
        /** "recipe": open on the recipe left in [com.bamnat.app.recipe.RecipeLinks.remake] (고쳐 만들기, 새 판), "recipe:<code>" as mine. */
        const val START_RECIPE = "recipe"
        /** "draft:<id>": a 만들던 레시피 kept on the phone. */
        const val START_DRAFT = "draft:"
        const val START_FEEL = "feel"
        const val START_FONT = "font"
        const val START_SCREEN_FONT = "font:screen"
        const val START_DATE_FONT = "font:date"
        /** [MAT-07] 설정 검색 '움직임'·'효과'·'파티클': 앱 전체 › 움직임, and › 부품 (a part's own 움직임). */
        const val START_MOTION = "motion"
        const val START_PARTS = "parts"
        /** PC 확인 L5: the studio's 잠근 화면, this phone's. */
        const val LOCK_PREFS = "studio_locks"
        /** "feature:<piece key>": 설정의 '이 설정 나누기' — the studio with that 기능 on (or the look as it is). */
        const val START_FEATURE = "feature:"
        const val START_AREA = "area:"
        /** A start for [START_RECIPE], one of a kind each time (the studio's ViewModel is kept per start). */
        fun recipeStart(own: String?): String = START_RECIPE + (own?.let { ":$it" } ?: "") + "@" + com.bamnat.core.time.Today.millis()
        /** [STU-21] The 기능 the studio can carry, in the order shown. */
        val FEATURES = listOf(PieceKind.SHIFT_LOOK, PieceKind.ALARMS, PieceKind.MEMO_TEMPLATES, PieceKind.HOURLY_RULES, PieceKind.FIELD_SHEET,
            PieceKind.SHOP, PieceKind.INVEST, PieceKind.APP_FEEL, PieceKind.COMPANY)
    }
}
