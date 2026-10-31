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
    /** [THM-01] Whether putting a theme on asks for an ad; [shared] is one of mine in 장터 (never asks). */
    fun needsAd(shared: Boolean): Boolean = _lease.value.needsAd(shared, adsOn(), pro())

    /** The theme kept under [code] (or mine named [title]) and whether it asks for an ad now. */
    fun needsAdFor(code: String, title: String): Boolean = needsAd(kept(code, title)?.free == true)

    fun kept(code: String, title: String): ThemeLease.Wear? = code.ifEmpty { "own:$title" }.let { k -> _lease.value.history.firstOrNull { it.key == k } }

    /** [THM-01] Mine sent to 장터 ([key] in the history) as [code] at [ver]: no days or ads from now; [bare] sent without pictures. */
    fun markShared(key: String, code: String, ver: Int, bare: Boolean) = save(_lease.value.shared(key, code, ver, bare))

    /**
     * F-100: one of my saved themes that looks just like [r] (part by part), so sending the look from 지금 모양 counts for the theme it
     * already is, instead of saving it again (무료는 한 벌이라 그 저장이 막혀 공개가 이어지지 않았어요).
     */
    fun ownWithLook(r: Recipe): ThemeLease.Wear? = _lease.value.history.firstOrNull { w ->
        w.own && runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let { com.bamnat.core.recipe.LookParts.changed(it, r).isEmpty() } == true
    }

    /** [THM-01] Mine taken out of 장터 (deleted, or hidden): [ThemeLease.DAYS] before its first ad. */
    fun unshared(code: String) = save(_lease.value.unshared(code, Today.date().toEpochDay()))

    /**
     * Puts on [r]'s looks (nothing else in it) with 7일 되돌리기 as any recipe; the user's own look is kept aside the first time.
     * [code] is the 나눔 판 code, "builtin:<title>" or "" for one of mine.
     */
    suspend fun wear(r: Recipe, code: String, own: Boolean, ver: Int = 0): Boolean {
        val looks = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        if (looks.pieces.isEmpty()) return false
        beforeWear()
        val picks = looks.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
        c.recipes.apply(looks, code.takeIf { !it.startsWith("builtin:") }.orEmpty(), ver, PersonalCalendar.MAIN, picks, emptyMap())
        afterWear(looks, code, own, ver)
        return true
    }

    /** Keeps the user's own look aside before the first theme goes on (a recipe's looks put on from 나눔 판 too). */
    fun beforeWear() {
        if (_lease.value.worn == null) prefs.edit().putString(K_MINE, JSONObject(c.recipes.pieces.lookSnapshot()).toString()).apply()
    }

    /** [looks] are on: the theme worn from today, its days running for someone else's (not [own]) under the ads switch. */
    suspend fun afterWear(looks: Recipe, code: String, own: Boolean, ver: Int = 0) {
        val l = _lease.value
        // [J135-16] What was worn before, so 되돌리기 of this one puts its name back with its look.
        prefs.edit().putString(K_WORN_LOOK, JSONObject(c.recipes.pieces.lookSnapshot()).toString())
            .putString(K_PREV, ThemeLease.encode(ThemeLease(worn = l.worn))).apply()
        // [THM-01] Mine keeps whether it is in 장터 (one of mine put on from 장터 is, by its own code).
        val was = l.history.firstOrNull { it.key == code.ifEmpty { "own:${looks.title}" } }
        val shared = was?.shared?.takeIf { own }.orEmpty().ifEmpty { if (own && code.isNotEmpty() && !code.startsWith("builtin:")) code else "" }
        val free = shared.isEmpty() && adsOn() && !pro() && l.freeUsed < ThemeLease.FREE_FIRST
        val only = looks.copy(pieces = looks.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        save(l.wear(ThemeLease.Wear(looks.title, code, RecipeCodec.write(only), 0, 0, own, shared, if (ver > 0) ver else was?.ver ?: 0, was?.bare == true),
            Today.date().toEpochDay(), free))
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
    suspend fun wearAgain(w: ThemeLease.Wear): Boolean = runCatching { RecipeCodec.read(w.body) }.getOrNull()?.let { wear(it, w.code, w.own, w.ver) } ?: false

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

    // ---- [TRY-03] 1.35 보관함의 조각 (사용자 2026-10-30 21:45 "해당 부분만 저장해서 가져오는거지") ----

    /**
     * A part of a theme kept to use later in 스튜디오: its looks only ([com.bamnat.core.recipe.LookParts.pick]), the theme it came from
     * ([from] its code, "" for mine) and that theme's name. Keeping one is free; putting on a look with someone else's part follows that
     * theme's days and ad, as wearing the theme would.
     */
    data class Kept(val id: Long, val title: String, val from: String, val fromTitle: String, val body: String, val parts: List<String>) {
        val own: Boolean get() = from.isEmpty()
    }

    private val _kept = MutableStateFlow(readKept())
    val kept: StateFlow<List<Kept>> = _kept.asStateFlow()

    private fun readKept(): List<Kept> = runCatching {
        val a = org.json.JSONArray(prefs.getString(K_PARTS, "[]"))
        (0 until a.length()).map { i -> a.getJSONObject(i) }.map { o ->
            Kept(o.getLong("id"), o.getString("title"), o.optString("from"), o.optString("fromTitle"), o.getString("body"),
                o.optString("parts").split(',').filter { it.isNotEmpty() })
        }
    }.getOrDefault(emptyList())

    private fun writeKept(list: List<Kept>) {
        val a = org.json.JSONArray()
        list.forEach { k -> a.put(JSONObject().put("id", k.id).put("title", k.title).put("from", k.from).put("fromTitle", k.fromTitle)
            .put("body", k.body).put("parts", k.parts.joinToString(","))) }
        prefs.edit().putString(K_PARTS, a.toString()).apply()
        _kept.value = list
    }

    fun partsLimit(): Int = if (pro()) PRO_PARTS else FREE_PARTS

    /** 담아 두기: [r] (a theme or recipe) cut to [ids], kept with where it came from. False when the box is full or nothing is in it. */
    fun keep(r: Recipe, code: String, ids: List<String>): Boolean {
        val only = com.bamnat.core.recipe.LookParts.pick(r, ids)
        if (only.pieces.isEmpty() || _kept.value.size >= partsLimit()) return false
        val from = code
        val k = Kept(Today.millis(), com.bamnat.core.recipe.LookParts.title(r.title, ids), from, r.title.take(30),
            RecipeCodec.write(only.copy(title = r.title.take(30).ifBlank { "조각" })), ids)
        writeKept(listOf(k) + _kept.value)
        return true
    }

    fun forgetKept(id: Long) = writeKept(_kept.value.filter { it.id != id })

    /** 1.35 [STU-10] 스튜디오의 내 테마로 저장: [r]'s looks kept as one of mine, as [saveMine] keeps the phone's. */
    fun saveLook(r: Recipe, was: ThemeLease.Wear? = null): Boolean {
        val l = _lease.value
        val name = r.title.take(30)
        // Saving over one of mine of the same name replaces it (and keeps it in 장터 if it was).
        val same = l.history.firstOrNull { it.own && it.key == "own:$name" }
        val mine = l.history.count { it.own }
        val only = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        if ((same == null && mine >= outfitLimit()) || only.pieces.isEmpty()) return false
        val keep = was ?: same
        val w = ThemeLease.Wear(name.ifBlank { "내 테마 ${mine + 1}" }, "", RecipeCodec.write(only), keep?.from ?: Today.date().toEpochDay(),
            keep?.until ?: Today.date().toEpochDay(), own = true, shared = keep?.shared.orEmpty(), ver = keep?.ver ?: 0, bare = keep?.bare == true)
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

    // ---- [THM-01][THM-05] 1.35 장터에 올린 내 테마와 새 판 (사용자 2026-10-30 21:53) ----

    /** Mine marked as in 장터 but no longer shown to all there (deleted, hidden, made private): kept to myself from now. */
    suspend fun syncShared() {
        val mine = _lease.value.history.filter { it.own && it.shared.isNotEmpty() }
        if (mine.isEmpty() || !c.recipeApi.enabled) return
        val items = runCatching { c.recipeApi.mine(c.companyStore.device) }.getOrNull() ?: return
        val shown = items.items.filter { (_, s) -> s.first in setOf("visible", "pending") && s.second == "public" }.map { it.first.code }.toSet()
        mine.filter { it.shared !in shown }.forEach { unshared(it.shared) }
    }

    /**
     * Someone else's theme worn now has a newer 판 in 장터, not turned down yet: its number, asked with the code only. The worn look never
     * changes by itself (사용자 "업데이트 하기전에는 바뀌지 않게").
     */
    suspend fun newerWorn(): Int? {
        val w = _lease.value.worn ?: return null
        if (w.own || w.code.isEmpty() || w.code.startsWith("builtin:") || !c.recipeApi.enabled) return null
        val v = runCatching { c.recipeApi.versions(listOf(w.code)) }.getOrNull()?.get(w.code) ?: return null
        return v.takeIf { it > w.ver && prefs.getString(K_SKIP, null) != "${w.code}|$it" }
    }

    /** 지금 그대로: this 판 is not asked about again (a later one is). */
    fun skip(code: String, ver: Int) = prefs.edit().putString(K_SKIP, "$code|$ver").apply()

    /**
     * 업데이트: the worn theme's looks become [r]'s ([ver]), its days as they were (no new ad, no free one used). The 판 before is kept
     * 7 days for [backToPrevious].
     */
    suspend fun updateWorn(r: Recipe, ver: Int): Boolean {
        val w = _lease.value.worn ?: return false
        prefs.edit().putString(K_PREV_VER, JSONObject().put("code", w.code).put("body", w.body).put("ver", w.ver)
            .put("title", w.title).put("day", Today.date().toEpochDay()).toString()).apply()
        return putOn(w, r, ver)
    }

    /** The 판 worn before the last 업데이트, within 7 days of it: its version, or null. */
    fun previous(): Int? {
        val o = prefs.getString(K_PREV_VER, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return null
        val w = _lease.value.worn ?: return null
        if (o.optString("code") != w.code || Today.date().toEpochDay() - o.optLong("day") > 7) return null
        return o.optInt("ver")
    }

    /** 이전 판으로: back to the 판 worn before the last 업데이트; the newer one is not asked about again. */
    suspend fun backToPrevious(): Boolean {
        val o = prefs.getString(K_PREV_VER, null)?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return false
        val w = _lease.value.worn ?: return false
        if (previous() == null) return false
        val r = runCatching { RecipeCodec.read(o.getString("body")) }.getOrNull() ?: return false
        skip(w.code, w.ver)
        prefs.edit().remove(K_PREV_VER).apply()
        return putOn(w, r.copy(title = o.optString("title", r.title)), o.optInt("ver"))
    }

    private suspend fun putOn(w: ThemeLease.Wear, r: Recipe, ver: Int): Boolean {
        val looks = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        if (looks.pieces.isEmpty()) return false
        val picks = looks.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
        c.recipes.apply(looks, w.code, ver, PersonalCalendar.MAIN, picks, emptyMap())
        prefs.edit().putString(K_WORN_LOOK, JSONObject(c.recipes.pieces.lookSnapshot()).toString()).apply()
        val next = w.copy(title = looks.title.take(30).ifBlank { w.title }, body = RecipeCodec.write(looks), ver = ver)
        val l = _lease.value
        save(l.copy(worn = next, history = l.history.map { if (it.key == w.key) next else it }))
        refreshWidgets()
        return true
    }

    companion object {
        const val FREE_OUTFITS = 1
        const val PRO_OUTFITS = 10
        /** [TRY-03] 조각 보관함: free 20, 프로 200. */
        const val FREE_PARTS = 20
        const val PRO_PARTS = 200
        private const val PREFS = "theme_wardrobe"
        private const val K_LEASE = "lease"
        private const val K_MINE = "mine"
        private const val K_WORN_LOOK = "worn_look"
        private const val K_SOON = "soon_said"
        private const val K_WAS_PRO = "was_pro"
        private const val K_PREV = "prev_worn"
        private const val K_PARTS = "parts"
        private const val K_SKIP = "skip_ver"
        private const val K_PREV_VER = "prev_ver"
    }
}
