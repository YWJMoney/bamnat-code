package com.bamnat.app.ui.recipe

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        /** The recipe has 기능 (알람·규칙·메모 틀 …) besides its looks: they go in only with 넣기. */
        val looksOnly: Boolean = false,
    )

    /** 입어 보기 to open again: [from] with [picked] ticked, on [tab]. */
    data class Reopen(val from: From, val picked: Set<String>, val tab: String)

    private val _trial = MutableStateFlow<Trial?>(null)
    val trial: StateFlow<Trial?> = _trial.asStateFlow()

    private val _reopen = MutableStateFlow<Reopen?>(null)
    val reopen: StateFlow<Reopen?> = _reopen.asStateFlow()

    /** Set by the app's root when a trial starts: takes the user back where 입어 보기 was (a screen of the back stack). */
    @Volatile internal var back: (() -> Unit)? = null

    /**
     * Starts trying [r]'s parts [picked] on the real app; false when they change nothing on this phone. [own]: one of mine (my cells'
     * content follows it too, as [com.bamnat.app.recipe.ThemeWardrobe.wear] does).
     */
    suspend fun start(c: AppContainer, r: Recipe, own: Boolean, picked: Set<String>, tab: String, from: From,
                      answers: Map<String, Any?> = emptyMap()): Boolean {
        val all = com.bamnat.core.recipe.LookParts.of(r).map { it.id }.toSet()
        val worn = if (picked.containsAll(all)) r else com.bamnat.core.recipe.LookParts.pick(r, picked)
        val look = runCatching { c.wardrobe.trialLook(worn, own, answers) }.getOrDefault(emptyMap())
        if (look.isEmpty()) return false
        _reopen.value = null
        c.settingsRepository.putTrial({ s -> c.recipes.pieces.withLook(s, look) })
        _trial.value = Trial(r.title, tab, picked, from, look,
            looksOnly = r.pieces.any { it.kind !in com.bamnat.core.recipe.BuiltInThemes.LOOK_KINDS })
        return true
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
        drop(c)
        back?.invoke()
        _reopen.value = Reopen(t.from, t.picked, t.tab)
    }

    /** The trial let go with nothing opened again (the app left, or a look put on for real). */
    fun drop(c: AppContainer) {
        if (_trial.value == null && !c.settingsRepository.trying) return
        c.settingsRepository.putTrial(null)
        _trial.value = null
    }

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
    // Its own dark-on-light pair, so it stands apart from whatever the theme tried on paints.
    Surface(color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface, tonalElevation = 3.dp) {
        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Column(Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite }) {
                Text(if (trial.showing) "‘${trial.title}’ 입어 보는 중" else "지금 내 모습 (입기 전)", style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(when {
                    !trial.showing -> "‘입은 모습’을 누르면 다시 입혀 봐요"
                    trial.looksOnly -> "실제 화면이에요 · 모양만 입혀 봤어요(기능은 넣을 때) · 저장 안 돼요"
                    else -> "실제 화면이에요 · 탭·화면을 눌러 둘러봐요 · 저장 안 돼요"
                }, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            androidx.compose.material3.OutlinedButton(onClick = { LiveTryOn.show(c, !trial.showing) }, modifier = Modifier.heightIn(min = 40.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.inverseOnSurface),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp)) {
                Text(if (trial.showing) "지금 모습" else "입은 모습", style = MaterialTheme.typography.labelMedium)
            }
            FilledTonalButton(onClick = { LiveTryOn.end(c) }, modifier = Modifier.heightIn(min = 40.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)) {
                Text("끝내기", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
