package com.bamnat.app.recipe

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.ThemeLease
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * [FREE-10] 1.35 테마 옷장 (사용자 2026-10-30): the theme worn now, 입었던 테마, and the user's own look kept aside while a theme is on,
 * so 벗기 always comes back to it. Only looks are ever put on ([BuiltInThemes.LOOK_KINDS]): records, alarms, pay rules, memo templates
 * and the work cycle are never touched, whatever theme comes or goes. The time a theme runs is [ThemeLease]'s.
 */
class ThemeWardrobe(private val c: AppContainer, context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val _lease = MutableStateFlow(ThemeLease.decode(prefs.getString(K_LEASE, null)))
    val lease: StateFlow<ThemeLease> = _lease.asStateFlow()

    private fun save(l: ThemeLease) {
        prefs.edit().putString(K_LEASE, ThemeLease.encode(l)).apply()
        _lease.value = l
    }

    /** Whether ads may run (the `ads` switch) and the user is on 프로; before launch the gate says 프로, so nothing runs. */
    fun adsOn(): Boolean = c.flags.on(com.bamnat.core.flags.FlagCatalog.ADS)
    fun pro(): Boolean = c.featureGate.allows(com.bamnat.app.tier.Feature.THEME_WARDROBE)

    fun state(): ThemeLease.State = _lease.value.state(Today.date().toEpochDay(), adsOn(), pro())
    fun ask(): Boolean {
        // 프로 that ended (or the launch, before which everyone is 프로): the worn theme runs 30 days from then.
        val p = pro()
        if (prefs.getBoolean(K_WAS_PRO, false) && !p) save(_lease.value.proEnded(Today.date().toEpochDay()))
        if (prefs.getBoolean(K_WAS_PRO, false) != p) prefs.edit().putBoolean(K_WAS_PRO, p).apply()
        return _lease.value.ask(Today.date().toEpochDay(), adsOn(), p)
    }
    fun needsAd(own: Boolean): Boolean = _lease.value.needsAd(own, adsOn(), pro())

    /**
     * Puts on [r]'s looks (nothing else in it) with 7일 되돌리기 as any recipe; the user's own look is kept aside the first time.
     * [code] is the 나눔 판 code, "builtin:<title>" or "" for one of mine.
     */
    suspend fun wear(r: Recipe, code: String, own: Boolean): Boolean {
        val looks = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        if (looks.pieces.isEmpty()) return false
        beforeWear()
        val picks = looks.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
        c.recipes.apply(looks, code.takeIf { !it.startsWith("builtin:") }.orEmpty(), 0, PersonalCalendar.MAIN, picks, emptyMap())
        afterWear(looks, code, own)
        return true
    }

    /** Keeps the user's own look aside before the first theme goes on (a recipe's looks put on from 나눔 판 too). */
    fun beforeWear() {
        if (_lease.value.worn == null) prefs.edit().putString(K_MINE, JSONObject(c.recipes.pieces.lookSnapshot()).toString()).apply()
    }

    /** [looks] are on: the theme worn from today, its days running for someone else's (not [own]) under the ads switch. */
    suspend fun afterWear(looks: Recipe, code: String, own: Boolean) {
        val l = _lease.value
        // [J135-16] What was worn before, so 되돌리기 of this one puts its name back with its look.
        prefs.edit().putString(K_WORN_LOOK, JSONObject(c.recipes.pieces.lookSnapshot()).toString())
            .putString(K_PREV, ThemeLease.encode(ThemeLease(worn = l.worn))).apply()
        val free = !own && adsOn() && !pro() && l.freeUsed < ThemeLease.FREE_FIRST
        val only = looks.copy(pieces = looks.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        save(l.wear(ThemeLease.Wear(looks.title, code, RecipeCodec.write(only), 0, 0, own), Today.date().toEpochDay(), free))
        refreshWidgets()
    }

    /**
     * 벗기 (or 아니요 at the end of a theme's days): the user's own look comes back. What the user changed on the theme while wearing
     * it is not lost: it is kept in 입었던 테마 as "내가 고친 …", one of mine, which never runs out.
     */
    suspend fun takeOff() {
        val worn = _lease.value.worn ?: return
        val now = c.recipes.pieces.lookSnapshot()
        val after = readMap(K_WORN_LOOK)
        var l = _lease.value
        if (after != null && after != now) {
            val mine = myLookRecipe("내가 고친 ${worn.title}".take(30))
            l = l.wear(ThemeLease.Wear(mine.title, "", RecipeCodec.write(mine), 0, 0, own = true), Today.date().toEpochDay(), free = false)
        }
        readMap(K_MINE)?.let { c.recipes.pieces.restoreLook(it) }
        prefs.edit().remove(K_MINE).remove(K_WORN_LOOK).remove(K_PREV).apply()
        save(l.takeOff())
        refreshWidgets()
    }

    /** A whole ad was watched: 30 more days. */
    fun extend() = save(_lease.value.extend(Today.date().toEpochDay()))

    /** No ad could come ([ThemeLease.REASONS]): a few days more, and the reason counted on this phone. */
    fun noAd(reason: String) {
        save(_lease.value.noAd(Today.date().toEpochDay(), Today.date().toString(), reason))
        tell(reason)
    }

    /** [FREE-10] The day's count of a free theme by why, in 사용 통계 (only when the user turned it on; never who). */
    private fun tell(reason: String) {
        val key = "theme_free_" + (reason.takeIf { it in listOf("no_fill", "timeout", "show_failed", "not_ready") } ?: "other")
        if (reason != "flag_off" && reason != "offline") runCatching { c.telemetry.event(key) }
    }

    /** A theme put on with no ad able to come: counted only. */
    fun counted(reason: String) {
        save(_lease.value.counted(Today.date().toString(), reason))
        tell(reason)
    }

    /** [FREE-10] The 3일 전 notice is said once per theme and end ("key|until"). */
    fun soonSaid(): String? = prefs.getString(K_SOON, null)
    fun saySoon(tag: String) = prefs.edit().putString(K_SOON, tag).apply()

    /** The ask closed without an answer: the theme stays, asked again another day. */
    fun later() = save(_lease.value.later(Today.date().toEpochDay()))

    /** Wears a theme from 입었던 테마 again. */
    suspend fun wearAgain(w: ThemeLease.Wear): Boolean = runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let { wear(it, w.code, w.own) } ?: false

    /** The look on the phone now as a theme of mine (its looks only). */
    suspend fun myLookRecipe(title: String): Recipe {
        val pieces = BuiltInThemes.LOOK_KINDS.mapNotNull { k ->
            runCatching { c.recipes.pieces.current(k, emptyMap(), PersonalCalendar.MAIN) }.getOrNull()?.takeIf { it.isNotEmpty() }
                ?.let { com.bamnat.core.recipe.Piece(k, it) }
        }.filter { it.kind != PieceKind.LAYOUT && it.kind != PieceKind.CARDS || it.values.isNotEmpty() }
        return Recipe(title, "내가 꾸민 모양", "", com.bamnat.core.work.WorkStyle.entries.toSet(), pieces = pieces)
    }

    private fun readMap(key: String): Map<String, String>? = prefs.getString(key, null)?.let { s ->
        runCatching { JSONObject(s).let { o -> o.keys().asSequence().associateWith { o.getString(it) } } }.getOrNull()
    }

    private suspend fun refreshWidgets() = runCatching { com.bamnat.app.widget.WidgetRefresher.refresh(c.appContext) }

    /** [FREE-10] 내 테마로 저장: the look on the phone now kept as one of mine (free [FREE_OUTFITS], 프로 [PRO_OUTFITS]). */
    suspend fun saveMine(title: String): Boolean {
        val mine = _lease.value.history.count { it.own }
        if (mine >= outfitLimit()) return false
        val r = myLookRecipe(title.take(30).ifBlank { "내 테마 ${mine + 1}" })
        if (r.pieces.isEmpty()) return false
        val l = _lease.value
        val w = ThemeLease.Wear(r.title, "", RecipeCodec.write(r), Today.date().toEpochDay(), Today.date().toEpochDay(), own = true)
        save(l.copy(history = (l.history.filter { it.key != w.key } + w).take(ThemeLease.HISTORY)))
        return true
    }

    fun outfitLimit(): Int = if (c.featureGate.allows(com.bamnat.app.tier.Feature.THEME_WARDROBE)) PRO_OUTFITS else FREE_OUTFITS

    /** A theme taken back by 되돌리기 (the recipe's own undo put the look back): no longer worn. */
    fun forget(code: String) {
        if (_lease.value.worn?.code != code) return
        val prev = ThemeLease.decode(prefs.getString(K_PREV, null)).worn
        if (prev == null) {
            prefs.edit().remove(K_MINE).remove(K_WORN_LOOK).remove(K_PREV).apply()
            save(_lease.value.takeOff())
        } else {
            // [J135-16] The theme worn before comes back on screen with the undo, and so does its name and its days.
            prefs.edit().putString(K_WORN_LOOK, JSONObject(c.recipes.pieces.lookSnapshot()).toString()).remove(K_PREV).apply()
            save(_lease.value.copy(worn = prev, askedDay = null))
        }
    }

    companion object {
        const val FREE_OUTFITS = 1
        const val PRO_OUTFITS = 10
        private const val PREFS = "theme_wardrobe"
        private const val K_LEASE = "lease"
        private const val K_MINE = "mine"
        private const val K_WORN_LOOK = "worn_look"
        private const val K_SOON = "soon_said"
        private const val K_WAS_PRO = "was_pro"
        private const val K_PREV = "prev_worn"
    }
}
