package com.bamnat.app.ui.recipe

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bamnat.app.AppContainer
import com.bamnat.core.recipe.Recipe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * [TRY-05] 1.39 실제 화면으로 입어 보기 (사용자 2026-11-07 "마을 장터에서 테마를 고르면 입혀보기에서 가계부나 급여 일감등등이 실 모습과
 * 100% 동일하게 보여지지 않고 있다 … 이테마를 입으면 이렇게 되겠구나를 알수 있어야 한다").
 *
 * The example phone of 입어 보기 draws each tab again on its own, so 가계부·급여·일감 and the screens it has no example of never looked
 * as they will. This puts the theme's look on the real app instead, in memory only ([com.bamnat.app.data.SettingsRepository.putTrial]):
 * every tab and screen with the user's own records, exactly as 입기 would leave them ([com.bamnat.app.recipe.ThemeWardrobe.trialLook]).
 * Nothing is saved; a band over the tabs says so, flips to 지금 모습, and 끝내기 goes back to 입어 보기 with the same parts ticked,
 * where 입기 (and its ad) is as before. The trial also ends when the app goes to the background or a look is put on for real.
 */
object LiveTryOn {
    /** Where 입어 보기 was opened, to open it again when the trial ends. */
    sealed interface From {
        /** 장터 상세 ([RecipeDetailScreen]) of [code]. */
        data class Detail(val code: String) : From
        /** A theme sheet ([ThemeSheet]) of [r], opened again over the screen it was on. */
        data class Sheet(val r: Recipe, val code: String, val ver: Int) : From
        /** 1.39 [MKF-01] 장터 크게 넘겨 보기 ([MarketFlip]) at [at] of the list, [code] the work tried. */
        data class Flip(val code: String, val at: Int) : From
    }

    data class Trial(
        val title: String,
        /** The tab the real app opens on (the one 입어 보기 showed). */
        val tab: String,
        val picked: Set<String>,
        val from: From,
        /** The look settings laid on ([com.bamnat.app.recipe.ThemeWardrobe.trialLook]). */
        val look: Map<String, String>,
        /** False while 지금 모습 is shown. */
        val showing: Boolean = true,
        /** The recipe has 기능 (알람·규칙·메모 틀 …) besides its looks, not tried with them: they go in only with 넣기. */
        val looksOnly: Boolean = false,
        /** [TRY-06] The 기능 put on for the trial ([com.bamnat.app.recipe.FunctionTrial]), by name; empty: looks only. */
        val functions: List<String> = emptyList(),
        /** [TRY-07] The tabs the look changes, [tab] first ('바뀐 곳' 둘러보기), and the one on show now. */
        val tour: List<String> = listOf(tab),
        val at: Int = 0,
    ) {
        val hasLook: Boolean get() = look.isNotEmpty()
    }

    /** 입어 보기 to open again: [from] with [picked] ticked, on [tab]. */
    data class Reopen(val from: From, val picked: Set<String>, val tab: String,
                      /** [TRY-07] Asked from the band's 입기: the page goes on to its 입기·넣기 at once. */
                      val wear: Boolean = false)

    private val _trial = MutableStateFlow<Trial?>(null)
    val trial: StateFlow<Trial?> = _trial.asStateFlow()

    private val _reopen = MutableStateFlow<Reopen?>(null)
    val reopen: StateFlow<Reopen?> = _reopen.asStateFlow()

    /** Set by the app's root when a trial starts: takes the user back where 입어 보기 was (a screen of the back stack). */
    @Volatile internal var back: (() -> Unit)? = null

    private val _ended = MutableStateFlow<com.bamnat.app.recipe.FunctionTrial.Ended?>(null)
    /** [TRY-06] How the last trial with 기능 ended (what they did here), for the page it was opened from. */
    val ended: StateFlow<com.bamnat.app.recipe.FunctionTrial.Ended?> = _ended.asStateFlow()

    /**
     * Starts trying [r]'s parts [picked] on the real app; false when they change nothing on this phone. [own]: one of mine (my cells'
     * content follows it too, as [com.bamnat.app.recipe.ThemeWardrobe.wear] does).
     */
    suspend fun start(c: AppContainer, r: Recipe, own: Boolean, picked: Set<String>, tab: String, from: From,
                      answers: Map<String, Any?> = emptyMap(),
                      /** [TRY-06] 기능 to try too, each with how it meets mine, on calendar [cal]. */
                      functions: Map<com.bamnat.core.recipe.PieceKind, com.bamnat.app.recipe.RecipePieces.Mode> = emptyMap(),
                      cal: String = com.bamnat.app.data.PersonalCalendar.MAIN): Boolean {
        val all = com.bamnat.core.recipe.LookParts.of(r).map { it.id }.toSet()
        val worn = if (picked.containsAll(all)) r else com.bamnat.core.recipe.LookParts.pick(r, picked)
        val look = if (all.isEmpty() || picked.isEmpty()) emptyMap() else runCatching { c.wardrobe.trialLook(worn, own, answers) }.getOrDefault(emptyMap())
        val code = when (from) { is From.Detail -> from.code; is From.Sheet -> from.code; is From.Flip -> from.code }
        // The 기능 go on first (for real, kept to be put back); the looks are laid over what that leaves.
        val fn = if (functions.isEmpty()) emptyList() else runCatching {
            com.bamnat.app.recipe.FunctionTrial(c).start(r, code, cal, functions.keys, functions, answers)
        }.getOrDefault(emptyList())
        if (look.isEmpty() && fn.isEmpty()) return false
        _reopen.value = null
        _ended.value = null
        if (look.isNotEmpty()) c.settingsRepository.putTrial({ s -> c.recipes.pieces.withLook(s, look) }) else c.settingsRepository.putTrial(null)
        _trial.value = Trial(r.title, tab, picked, from, look,
            looksOnly = fn.isEmpty() && r.pieces.any { it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS },
            functions = fn.map { it.label }, tour = if (look.isEmpty()) listOf(tab) else tourOf(worn, tab))
        return true
    }

    /** [TRY-07] The app's tabs the parts of [worn] change, [first] first, then in the usual order (위젯 is not a tab of the app). */
    internal fun tourOf(worn: Recipe, first: String): List<String> {
        val changed = com.bamnat.core.recipe.LookParts.of(worn).map { it.tab }.toSet()
        return (listOf(first) + com.bamnat.core.layout.TabOrder.DEFAULT.filter { it in changed }).distinct()
    }

    /** [TRY-07] 바뀐 곳 둘러보기: the next tab the look changes (the app's root goes there). */
    fun next() {
        val t = _trial.value ?: return
        if (t.tour.size > 1) _trial.value = t.copy(at = (t.at + 1) % t.tour.size)
    }

    /**
     * [TRY-07] 입기 from the band: the trial ends (what the user wrote while trying stays) and the page it came from goes on to its
     * 입기·넣기 at once, with the same parts, ad and 프로 as ever.
     */
    suspend fun wear(c: AppContainer) {
        val t = _trial.value ?: return
        away?.cancel()
        val goBack = back
        val e = if (t.functions.isEmpty()) null else runCatching { com.bamnat.app.recipe.FunctionTrial(c).finish(keepMine = true) }.getOrNull()
        lookOff(c)
        goBack?.let { go -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { runCatching { go() } } }
        _reopen.value = Reopen(t.from, t.picked, t.tab, wear = true)
        e?.let { _ended.value = it }
    }

    /** 지금 모습 ([on] false) ↔ 입은 모습, on the same screen. */
    fun show(c: AppContainer, on: Boolean) {
        val t = _trial.value ?: return
        c.settingsRepository.putTrial({ s -> c.recipes.pieces.withLook(s, t.look) }, shown = on)
        _trial.value = t.copy(showing = on)
    }

    /** 끝내기: the saved look back on screen, and 입어 보기 opened again where it was, the same parts ticked. */
    fun end(c: AppContainer) {
        val t = _trial.value ?: return
        if (t.functions.isNotEmpty()) { c.appScope.launch { finish(c, keepMine = false) }; return }
        drop(c)
        back?.invoke()
        _reopen.value = Reopen(t.from, t.picked, t.tab)
    }

    /**
     * [TRY-06] 끝내기 of a trial with 기능: every piece put back, what its rules and buttons wrote taken back, and what the user wrote
     * too unless [keepMine]; then as [end].
     */
    suspend fun finish(c: AppContainer, keepMine: Boolean) {
        away?.cancel()
        val t = _trial.value
        // Taken now: once the trial is off, the app's root lets go of it.
        val goBack = back
        val e = runCatching { com.bamnat.app.recipe.FunctionTrial(c).finish(keepMine) }.getOrNull()
        lookOff(c)
        if (t != null) {
            goBack?.let { go -> kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main.immediate) { runCatching { go() } } }
            if (t.hasLook) _reopen.value = Reopen(t.from, t.picked, t.tab)
        }
        // PC 03111522 (같은 글이 카드와 스낵으로 두 번): back on the page it came from, its card says it.
        tell(e, snack = t?.from !is From.Detail)
    }

    /** The trial let go with nothing opened again (the app left, or a look put on for real); 기능 tried are put back after. */
    fun drop(c: AppContainer) {
        if (com.bamnat.app.recipe.FunctionTrial.busy(c.appContext)) c.appScope.launch { settle(c) }
        lookOff(c)
    }

    /** [TRY-06] Ends whatever is being tried, waiting for its 기능 to be put back (what the user wrote stays). */
    suspend fun settle(c: AppContainer, snack: Boolean = true) {
        away?.cancel()
        val e = runCatching { com.bamnat.app.recipe.FunctionTrial(c).finish(keepMine = true) }.getOrNull()
        lookOff(c)
        tell(e, snack)
    }

    private fun lookOff(c: AppContainer) {
        if (_trial.value == null && !c.settingsRepository.trying) return
        c.settingsRepository.putTrial(null)
        _trial.value = null
    }

    /** The card of [ended] closed. */
    fun forget() { _ended.value = null }

    /** What the 기능 did while tried, said once (and kept for the page it came from). */
    internal fun tell(e: com.bamnat.app.recipe.FunctionTrial.Ended?, snack: Boolean = true) {
        e ?: return
        _ended.value = e
        if (snack) com.bamnat.app.logic.LogicRunner.say(say(e))
    }

    /** "‘야식 관리’ 써 보기를 끝냈어요. 단추·규칙이 3번 일했고, 그 기록은 모두 지웠어요". */
    fun say(e: com.bamnat.app.recipe.FunctionTrial.Ended): String {
        val mine = "기록(${e.made.say()})"
        val rest = when {
            e.ran > 0 && e.made.total == 0 -> "단추·규칙이 ${e.ran}번 일했고, 그 기록은 모두 지웠어요"
            e.ran > 0 && e.kept -> "단추·규칙이 ${e.ran}번 일한 기록은 지우고, 직접 적은 ${mine}은 남겼어요"
            e.ran > 0 -> "단추·규칙이 ${e.ran}번 일한 기록과 써 보며 적은 ${mine}을 지웠어요"
            e.made.total == 0 -> "내 앱은 원래대로예요"
            e.kept -> "직접 적은 ${mine}은 남겼어요"
            else -> "써 보며 적은 ${mine}을 지웠어요"
        }
        return "‘${e.title}’ 써 보기를 끝냈어요. $rest"
    }

    @Volatile private var away: kotlinx.coroutines.Job? = null

    /**
     * [TRY-06] The app went to the background. A trial of looks only ends at once; one with 기능 waits [AWAY_MILLIS] (a photo picked for a
     * memo, a glance at another app) and then ends, keeping what the user wrote.
     */
    fun stepAway(c: AppContainer) {
        val t = _trial.value ?: return
        if (t.functions.isEmpty()) return end(c)
        away?.cancel()
        away = c.appScope.launch { kotlinx.coroutines.delay(AWAY_MILLIS); away = null; settle(c) }
    }

    /** Back in the app before [AWAY_MILLIS]: the trial goes on. */
    fun stepIn() { away?.cancel(); away = null }

    const val AWAY_MILLIS = 3 * 60_000L

    /** The [Reopen] for [from], taken (once). */
    fun take(match: (From) -> Boolean): Reopen? {
        val r = _reopen.value ?: return null
        if (!match(r.from)) return null
        _reopen.value = null
        return r
    }
}

/** [TRY-05] The line over the tabs while a theme is tried on the real app: what it is, 지금 모습, 끝내기. */
@Composable
fun LiveTryOnBand(c: AppContainer) {
    val t by LiveTryOn.trial.collectAsStateWithLifecycle()
    val trial = t ?: return
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    // [TRY-06] 끝내기 of one with 기능 asks first what to do with what the user wrote while trying, if anything.
    var asking by androidx.compose.runtime.remember(trial.title) { androidx.compose.runtime.mutableStateOf<com.bamnat.app.recipe.FunctionTrial.Made?>(null) }
    var ending by androidx.compose.runtime.remember(trial.title) { androidx.compose.runtime.mutableStateOf(false) }
    fun finish(keepMine: Boolean) {
        asking = null
        ending = true
        scope.launch { LiveTryOn.finish(c, keepMine) }
    }
    val fn = trial.functions.isNotEmpty()
    // Its own dark-on-light pair, so it stands apart from whatever the theme tried on paints.
    Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, tonalElevation = 3.dp) {
        // [TRY-07] Folded, the words get their own line and the buttons the one under it (the title is no longer cut to a few letters).
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
            val narrow = maxWidth < 520.dp
            val words: @Composable (Modifier) -> Unit = { m ->
                Column(m.semantics { liveRegion = LiveRegionMode.Polite }) {
                    Text(when {
                        ending -> "원래대로 돌리는 중…"
                        !trial.showing -> "지금 내 모습 (입기 전)"
                        fn -> "‘${trial.title}’ 써 보는 중"
                        else -> "‘${trial.title}’ 입혀 보는 중"
                    }, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(when {
                        !trial.showing -> "‘입은 모습’을 누르면 다시 입혀 봐요"
                        // PC 03111522 (모양+기능이면 '…끝내면 지…'로 잘림): two names and how many more.
                        fn -> trial.functions.distinct().let { l -> "실제로 돌아요: ${l.take(2).joinToString("·")}${if (l.size > 2) " 외 ${l.size - 2}개" else ""} · 생긴 기록은 끝내면 지워요" }
                        trial.looksOnly -> "실제 화면이에요 · 모양만 입혀 봤어요(기능은 넣을 때) · 저장 안 돼요"
                        else -> "실제 화면이에요 · 탭·화면을 눌러 둘러봐요 · 저장 안 돼요"
                    }, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            // [TRY-07] 바뀐 곳 둘러보기: each tab the look changes, one tap each, so none of it is missed.
            val tour: @Composable () -> Unit = {
                if (trial.showing && trial.tour.size > 1) androidx.compose.material3.TextButton(onClick = { LiveTryOn.next() }, enabled = !ending,
                    modifier = Modifier.heightIn(min = 40.dp).semantics(mergeDescendants = true) {
                        contentDescription = "바뀐 곳 둘러보기 ${trial.at + 1}/${trial.tour.size}, 다음은 ${com.bamnat.core.recipe.LookParts.tabLabel(trial.tour[(trial.at + 1) % trial.tour.size])}"
                    },
                    // The band's own ink: a theme's inversePrimary can be as dark as the band (1.39 TRY-07 화면 사진).
                    colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) {
                    val nextTab = trial.tour[(trial.at + 1) % trial.tour.size]
                    Text((if (narrow) "" else "바뀐 곳 ") + "${trial.at + 1}/${trial.tour.size} · 다음 ${com.bamnat.core.recipe.LookParts.tabLabel(nextTab)} ▶", style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            val buttons: @Composable () -> Unit = {
                if (trial.hasLook) androidx.compose.material3.OutlinedButton(onClick = { LiveTryOn.show(c, !trial.showing) }, enabled = !ending,
                    modifier = Modifier.heightIn(min = 40.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                    Text(if (trial.showing) "지금 모습" else "입은 모습", style = MaterialTheme.typography.labelMedium)
                }
                androidx.compose.material3.OutlinedButton(onClick = {
                    if (!fn) LiveTryOn.end(c)
                    else scope.launch {
                        val made = runCatching { com.bamnat.app.recipe.FunctionTrial(c).made() }.getOrNull()
                        if (made != null && made.total > 0) asking = made else finish(keepMine = false)
                    }
                }, enabled = !ending, modifier = Modifier.heightIn(min = 40.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                    Text("끝내기", style = MaterialTheme.typography.labelMedium)
                }
                // [TRY-07] 입기 (넣기) right here: the page it came from goes on to its own 입기, the same parts, ad and 프로 as ever.
                FilledTonalButton(onClick = { ending = true; scope.launch { LiveTryOn.wear(c) } }, enabled = !ending, modifier = Modifier.heightIn(min = 40.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                    Text(if (fn) "넣기" else "입기", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }
            if (narrow) Column(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 4.dp)) {
                words(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(Modifier.weight(1f)) { tour() }
                    buttons()
                }
            } else Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                words(Modifier.weight(1f))
                tour()
                buttons()
            }
        }
    }
    asking?.let { made ->
        com.bamnat.app.ui.components.AlertDialog(
            onDismissRequest = { asking = null },
            title = { Text("써 보며 적은 기록") },
            text = { Text("‘${trial.title}’${com.bamnat.app.ui.Fmt.obj(trial.title)} 써 보는 동안 직접 적은 기록(${made.say()})이 있어요. 써 보기용이면 지우고, " +
                "내 기록이면 남겨요. 단추·규칙이 만든 기록과 작품의 기능은 어느 쪽이든 원래대로 돌아가요") },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { finish(keepMine = false) }) { Text("지우고 끝내기") } },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { finish(keepMine = true) }) { Text("남기고 끝내기") } },
        )
    }
}
