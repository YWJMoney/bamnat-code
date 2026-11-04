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

    /**
     * 1.37 PC d12d4cf1 (백업을 복원하면 담아 둔 조각·입어 본 테마·묶음이 사라짐): after a restore wrote this file, what is held in memory
     * is read again from it, so the next save does not write the old phone's state back over it.
     */
    fun reload() {
        _lease.value = ThemeLease.decode(prefs.getString(K_LEASE, null))
        _kept.value = readKept()
        _overlays.value = readOverlays()
        _folders.value = readFolders()
        _placed.value = readPlaced()
    }

    /**
     * 내 테마로 저장's count: my themes, without a studio mix kept by itself when it was taken off (PC 6eed57a2: 저장한 적 없는 조합이
     * 한 칸을 써요).
     */
    fun ownCount(l: ThemeLease = _lease.value): Int = l.history.count { it.own && !isMix(it.title) }

    /**
     * 1.37 PC d12d4cf1 (입던 조합을 벗거나 바꾸면 서랍에서 사라져요): a mix of other themes' parts made in the studio stays in 입어 본
     * 테마 as one of mine mixed from them ([ThemeLease.Wear.mixedFrom]: the 30-day rule, 장터 only as a 수정판), to wear again.
     */
    private fun keepMix(l: ThemeLease): ThemeLease {
        val w = l.worn?.takeIf { isMix(it.title) && !it.own } ?: return l
        val from = l.history.firstOrNull { it.key == w.key }?.title ?: w.title.removePrefix(MIX)
        val mine = ThemeLease.Wear(w.title, "", w.body, w.from, w.until, own = true, mixedFrom = from.take(30), mixedCodes = w.code)
        return l.copy(history = (listOf(mine) + l.history.filter { it.key != mine.key }).take(ThemeLease.HISTORY))
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
    suspend fun wear(r: Recipe, code: String, own: Boolean, ver: Int = 0, whole: Recipe? = null): Boolean {
        val looks = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        if (looks.pieces.isEmpty()) return false
        beforeWear()
        val picks = looks.pieces.map { RecipeRepository.Pick(it.kind, RecipePieces.Mode.REPLACE) }
        // A mix made in the studio is mine to look at: no 받은 레시피 row under the code its parts came from (no 새 판 알림 for it).
        lastApplied = c.recipes.apply(looks, code.takeIf { !it.startsWith("builtin:") && !isMix(r.title) }.orEmpty(), ver, PersonalCalendar.MAIN, picks, emptyMap())
        afterWear(looks, code, own, ver, whole)
        // [MKX-02] The tabs laid on stay over the new theme.
        reOverlay()
        return true
    }

    /** Keeps the user's own look aside before the first theme goes on (a recipe's looks put on from 나눔 판 too). */
    fun beforeWear() {
        if (_lease.value.worn == null) prefs.edit().putString(K_MINE, JSONObject(c.recipes.pieces.lookSnapshot()).toString()).apply()
    }

    /** [looks] are on: the theme worn from today, its days running for someone else's (not [own]) under the ads switch. */
    suspend fun afterWear(looks: Recipe, code: String, own: Boolean, ver: Int = 0, whole: Recipe? = null) {
        val l = keepMix(_lease.value)
        // [J135-16] What was worn before, so 되돌리기 of this one puts its name back with its look.
        prefs.edit().putString(K_WORN_LOOK, JSONObject(c.recipes.pieces.lookSnapshot()).toString())
            .putString(K_PREV, ThemeLease.encode(ThemeLease(worn = l.worn))).apply()
        // [THM-01] Mine keeps whether it is in 장터 (one of mine put on from 장터 is, by its own code).
        val was = l.history.firstOrNull { it.key == code.ifEmpty { "own:${looks.title}" } }
        val shared = was?.shared?.takeIf { own }.orEmpty().ifEmpty { if (own && code.isNotEmpty() && !code.startsWith("builtin:")) code else "" }
        val free = shared.isEmpty() && adsOn() && !pro() && l.freeUsed < ThemeLease.FREE_FIRST
        val only = looks.copy(pieces = looks.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        // PC 6eed57a2 (섞은 테마를 다시 입으면 '내 것 · 나만'으로): one of mine mixed from others keeps where it came from.
        val next = l.wear(ThemeLease.Wear(looks.title, code, RecipeCodec.write(only), 0, 0, own, shared, if (ver > 0) ver else was?.ver ?: 0, was?.bare == true,
            mixedFrom = was?.mixedFrom?.takeIf { own }.orEmpty(), mixedCodes = was?.mixedCodes?.takeIf { own }.orEmpty()),
            Today.date().toEpochDay(), free)
        save(when {
            whole != null -> keepWhole(l, next, whole)
            // 1.37 PC (스튜디오에서 입힌 섞은 모양이 서랍에서 그 테마 자리를 차지해요): the theme its parts came from stays in 입었던 테마.
            isMix(looks.title) && !own -> next.copy(history = (l.history.filter { it.key == next.worn?.key && !isMix(it.title) } + next.history.filter { it.key != next.worn?.key && !(isMix(it.title) && !it.own) })
                .take(ThemeLease.HISTORY))
            else -> next
        })
        next.worn?.let { stamp(it.key) }
        refreshWidgets()
    }

    /**
     * 1.37 PC (일부만 입으면 서랍의 테마가 ‘… 일부’로 바뀌어요): [whole]'s parts were picked, so 입었던 테마 keeps the whole theme under its
     * own name (worn first), and only what is worn now is ‘… 일부’.
     */
    private fun keepWhole(prev: ThemeLease, l: ThemeLease, whole: Recipe): ThemeLease {
        val now = l.worn ?: return l
        val looks = whole.copy(pieces = whole.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        val w = now.copy(title = whole.title.take(30), body = RecipeCodec.write(looks))
        val before = prev.history.firstOrNull { it.key == w.key }
        val keep = before?.copy(from = now.from, until = now.until) ?: w
        return l.copy(history = (listOf(keep) + l.history.filter { it.key != w.key && it.key != now.key }).take(ThemeLease.HISTORY))
    }

    /**
     * 벗기 (or 아니요 at the end of a theme's days): the user's own look comes back. What the user changed on the theme while wearing
     * it is not lost: it is kept in 입었던 테마 as "내가 고친 …", one of mine, which never runs out.
     */
    suspend fun takeOff() {
        val worn = _lease.value.worn ?: return
        val now = c.recipes.pieces.lookSnapshot()
        val after = readMap(K_WORN_LOOK)
        var l = keepMix(_lease.value)
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
        val mine = ownCount()
        if (mine >= outfitLimit()) return false
        val r = myLookRecipe(title.take(30).ifBlank { "내 테마 ${mine + 1}" })
        if (r.pieces.isEmpty()) return false
        val l = _lease.value
        val w = ThemeLease.Wear(r.title, "", RecipeCodec.write(r), Today.date().toEpochDay(), Today.date().toEpochDay(), own = true)
        save(l.copy(history = (listOf(w) + l.history.filter { it.key != w.key }).take(ThemeLease.HISTORY)))
        stamp(w.key)
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
        // PC 08a6f771 (같은 조각을 다시 담으면 같은 칸이 늘어요): the same parts from the same theme are one 조각, the newer.
        writeKept(listOf(k) + _kept.value.filter { it.from != k.from || it.fromTitle != k.fromTitle || it.parts.toSet() != k.parts.toSet() })
        return true
    }

    fun forgetKept(id: Long) = writeKept(_kept.value.filter { it.id != id })

    // ---- [MKX-02] 1.36 기능별 탭 얹기 (사용자 2026-10-31 "고른 테마에 자신에게 맞는 기능별 좋은 탭을 적용") ----

    /** One tab's looks laid on the theme worn: which tab, from which recipe, its looks, and the look from just before (빼기). */
    data class Overlay(val tab: String, val code: String, val title: String, val body: String, val before: Map<String, String>, val at: Long,
        /** PC 확인 5c21f3bb: the receiver's answers to its 받는 칸 (by id), for its looks and for the formulas' 칸("이름"). */
        val answers: Map<String, Any?> = emptyMap())

    private val _overlays = MutableStateFlow(readOverlays())
    val overlays: StateFlow<List<Overlay>> = _overlays.asStateFlow()

    /** Whether the tabs laid on stay when another theme is put on (on unless the user says no). */
    var keepOverlays: Boolean
        get() = prefs.getBoolean(K_KEEP_OVERLAYS, true)
        set(v) { prefs.edit().putBoolean(K_KEEP_OVERLAYS, v).apply() }

    private fun readOverlays(): List<Overlay> = runCatching {
        val a = org.json.JSONArray(prefs.getString(K_OVERLAYS, "[]"))
        (0 until a.length()).map { a.getJSONObject(it) }.map { o ->
            val b = o.optJSONObject("before")
            val a = o.optJSONObject("answers")
            Overlay(o.getString("tab"), o.optString("code"), o.optString("title"), o.getString("body"),
                b?.keys()?.asSequence()?.associateWith { b.getString(it) }.orEmpty(), o.optLong("at"),
                a?.keys()?.asSequence()?.associateWith { k -> a.get(k).takeIf { it != JSONObject.NULL } }.orEmpty())
        }
    }.getOrDefault(emptyList())

    private fun writeOverlays(list: List<Overlay>) {
        val a = org.json.JSONArray()
        list.forEach { o -> a.put(JSONObject().put("tab", o.tab).put("code", o.code).put("title", o.title).put("body", o.body)
            .put("before", JSONObject(o.before)).put("at", o.at).put("answers", JSONObject(o.answers))) }
        prefs.edit().putString(K_OVERLAYS, a.toString()).apply()
        _overlays.value = list
    }

    /** The looks of [r] laid over what the phone wears, the rest of it as it is ([LookParts.layer]). */
    private suspend fun layOn(r0: Recipe, answers: Map<String, Any?> = emptyMap()) {
        // PC 확인 5c21f3bb (목표 150만을 넣어도 기본 250만): its 받는 칸 and theme colours filled in, as a recipe put on is.
        val filled = com.bamnat.core.recipe.RecipeResolve.answers(r0, answers)
        val r = r0.copy(pieces = r0.pieces.map { p -> p.copy(values = com.bamnat.core.recipe.RecipeResolve.piece(r0, p, filled)) })
        val now = Recipe("지금", styles = com.bamnat.core.work.WorkStyle.entries.toSet(), pieces = c.recipes.pieces.lookPieces(c.settingsRepository.current))
        // [LAY-10] PC 확인 C10 1.36 (기능별 탭으로 얹은 사진 작품은 사진이 안 와요): its 꾸밈 사진 asked for now, and one the operator has not
        // let show yet is marked as waiting, so the calendar says so.
        r.pieces.firstOrNull { it.kind == com.bamnat.core.recipe.PieceKind.DECOR }?.let { p ->
            runCatching { com.bamnat.app.ui.decor.DecorPhotos.load(c, com.bamnat.core.recipe.Decor.of(p.values).images) }
        }
        val merged = com.bamnat.core.recipe.LookParts.layer(now, r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }))
        var d = c.settingsRepository.current
        merged.pieces.forEach { p -> d = c.recipes.pieces.onDraft(d, p.kind, p.values) }
        c.recipes.pieces.restoreLook(c.recipes.pieces.lookOf(d))
        refreshWidgets()
    }

    /**
     * [MKX-02] 얹기: [r]'s one tab ([LookParts.onlyTab]) laid on the theme worn, which keeps its name; one already on that tab is
     * replaced. False when [r] is not one tab's.
     */
    suspend fun overlay(r: Recipe, code: String, answers: Map<String, Any?> = emptyMap()): Boolean {
        val tab = com.bamnat.core.recipe.LookParts.onlyTab(r) ?: return false
        val before = c.recipes.pieces.lookSnapshot()
        // [MAT-06] PC 확인 9차 (얹은 탭의 글꼴은 받지도 기다리지도 않음): its pictures and shared fonts come first, as when a theme goes on.
        r.pieces.forEach { p -> if (p.kind == com.bamnat.core.recipe.PieceKind.PHOTO_LOOK || p.kind == com.bamnat.core.recipe.PieceKind.SCREEN_LOOK)
            runCatching { c.recipes.pieces.photoIds(p.values).forEach { LookImages.fetch(c, it) } } }
        runCatching { RecipeFonts.fetchIn(c, RecipeCodec.write(r)) }
        layOn(r, answers)
        val only = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        writeOverlays(_overlays.value.filter { it.tab != tab } + Overlay(tab, code, r.title.take(30), RecipeCodec.write(only), before, Today.millis(),
            answers.filterValues { it != null && it != "" }))
        wornLookNow()
        return true
    }

    /**
     * [MKX-02] 1.36 빌드 8 (탭을 얹고 테마를 벗으면 '내가 고친 …'이 저절로 내 테마에): a tab laid on or taken off is not the
     * user's own change to the theme worn, so the look 벗기 compares with is the one with the tabs.
     */
    private fun wornLookNow() {
        if (_lease.value.worn != null) prefs.edit().putString(K_WORN_LOOK, JSONObject(c.recipes.pieces.lookSnapshot()).toString()).apply()
    }

    /** 빼기: the tab back to how it looked just before it was laid on (that tab's settings only), and forgotten. */
    suspend fun removeOverlay(tab: String) {
        val o = _overlays.value.firstOrNull { it.tab == tab } ?: return
        writeOverlays(_overlays.value.filter { it.tab != tab })
        // The theme worn, put back whole on the app's own look (or, with none worn, the look from just before it was laid on); then the
        // other tabs still on go over it again.
        val worn = _lease.value.worn?.let { w -> runCatching { RecipeCodec.read(w.body) }.getOrNull() }
        if (worn != null) {
            var d = c.recipes.pieces.plainLook(c.settingsRepository.current)
            worn.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS }.forEach { p -> d = c.recipes.pieces.onDraft(d, p.kind, p.values) }
            c.recipes.pieces.restoreLook(c.recipes.pieces.lookOf(d))
        } else c.recipes.pieces.restoreLook(o.before)
        _overlays.value.forEach { other -> runCatching { RecipeCodec.read(other.body) }.getOrNull()?.let { layOn(it, other.answers) } }
        wornLookNow()
        refreshWidgets()
    }

    /** After a theme goes on: the tabs laid on go back over it ([keepOverlays]), or are let go. */
    private suspend fun reOverlay() {
        if (_overlays.value.isEmpty()) return
        if (!keepOverlays) { writeOverlays(emptyList()); return }
        _overlays.value.forEach { o -> runCatching { RecipeCodec.read(o.body) }.getOrNull()?.let { layOn(it, o.answers) } }
        wornLookNow()
    }

    /** 1.35 [STU-10] 스튜디오의 내 테마로 저장: [r]'s looks kept as one of mine, as [saveMine] keeps the phone's. */
    fun saveLook(r: Recipe, was: ThemeLease.Wear? = null, mixedFrom: String = "", mixedCodes: String = ""): Boolean {
        val l = _lease.value
        val name = r.title.take(30)
        // Saving over one of mine of the same name replaces it (and keeps it in 장터 if it was).
        val same = l.history.firstOrNull { it.own && it.key == "own:$name" }
        val mine = ownCount(l)
        val only = r.copy(pieces = r.pieces.filter { it.kind in BuiltInThemes.LOOK_KINDS })
        if ((same == null && mine >= outfitLimit()) || only.pieces.isEmpty()) return false
        val keep = was ?: same
        val w = ThemeLease.Wear(name.ifBlank { "내 테마 ${mine + 1}" }, "", RecipeCodec.write(only), keep?.from ?: Today.date().toEpochDay(),
            keep?.until ?: Today.date().toEpochDay(), own = true, shared = keep?.shared.orEmpty(), ver = keep?.ver ?: 0, bare = keep?.bare == true,
            mixedFrom = mixedFrom, mixedCodes = mixedCodes)
        save(l.copy(history = (listOf(w) + l.history.filter { it.key != w.key }).take(ThemeLease.HISTORY)))
        stamp(w.key)
        return true
    }

    fun outfitLimit(): Int = if (c.featureGate.allows(com.bamnat.app.tier.Feature.THEME_WARDROBE)) PRO_OUTFITS else FREE_OUTFITS

    /**
     * [THM-07] 1.36 빌드 8: one of mine or one worn before let go from the lists (the theme worn now stays on; a theme of mine in
     * 장터 stays there).
     */
    fun removeKept(key: String) {
        val l = _lease.value
        save(l.copy(history = l.history.filter { it.key != key }))
    }

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
        // 1.37 PC (스튜디오에서 조각을 얹어 입히면 ‘조합 · …’에 '만든 분이 1판을 올렸어요'): a mix of parts is not that theme's 판, and a
        // theme worn without its 판 known (0) is at least the first.
        if (w.own || isMix(w.title) || w.code.isEmpty() || w.code.startsWith("builtin:") || !c.recipeApi.enabled) return null
        val v = runCatching { c.recipeApi.versions(listOf(w.code)) }.getOrNull()?.get(w.code) ?: return null
        return v.takeIf { it > maxOf(w.ver, 1) && prefs.getString(K_SKIP, null) != "${w.code}|$it" }
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

    // ---- [DRW-03] 1.37 넣었어요 · 되돌리기 (후보 1, 사용자 2026-11-04 묶음 C) ----

    /** The 받은 레시피 row the last [wear] wrote (its looks), so 되돌리기 marks it taken back too. */
    @Volatile private var lastApplied: Long? = null

    /** What the phone wore just before a 입기·벗기·빼기: its look and this wardrobe's own record of it. */
    class Mark internal constructor(val look: Map<String, String>, val saved: Map<String, String?>)

    /** Taken right before a change that 넣었어요 · 되돌리기 offers to take back. */
    fun mark(): Mark {
        lastApplied = null
        return Mark(c.recipes.pieces.lookSnapshot(), UNDO_KEYS.associateWith { prefs.getString(it, null) })
    }

    /**
     * 되돌리기: the look and the theme worn (its days, 입었던 테마, the tabs laid on) back to [m]. A free wear already used stays used, so
     * 입기 → 되돌리기 → 입기 does not hand out free ones. A 받은 레시피 row the 입기 wrote is marked taken back.
     */
    suspend fun undoTo(m: Mark) {
        val id = lastApplied
        lastApplied = null
        // PC 확인 e34fae4e (입은 뒤 되돌리기를 눌러도 그대로): the look and the theme go back first, at once, whatever the 받은 레시피 row
        // does after; that row's own 되돌리기 (shift colours, pictures) gets a few seconds and never holds the rest up.
        putBack(m)
        if (id != null) {
            val done = kotlinx.coroutines.withTimeoutOrNull(UNDO_ROW_MS) { runCatching { c.recipes.undo(id) }.onFailure { android.util.Log.w(TAG, "row undo", it) } }
            if (done == null) android.util.Log.w(TAG, "row undo took over ${UNDO_ROW_MS}ms")
            // Its own undo also says what is worn (forget) and puts its looks back: what was worn just before stays the answer.
            putBack(m)
        }
    }

    private suspend fun putBack(m: Mark) {
        val used = _lease.value.freeUsed
        val e = prefs.edit()
        m.saved.forEach { (k, v) -> if (v == null) e.remove(k) else e.putString(k, v) }
        e.apply()
        val back = ThemeLease.decode(m.saved[K_LEASE])
        save(back.copy(freeUsed = maxOf(back.freeUsed, used)))
        _overlays.value = readOverlays()
        c.recipes.pieces.restoreLook(m.look)
        refreshWidgets()
    }

    // ---- [DRW-02] 1.37 내 서랍의 묶음 (사용자 2026-11-04 "폴더") ----

    private val _folders = MutableStateFlow(readFolders())
    /** The 묶음 name of each thing in 내 서랍, by its [com.bamnat.app.recipe.DrawerItem.key]. */
    val folders: StateFlow<Map<String, String>> = _folders.asStateFlow()

    private fun readFolders(): Map<String, String> = runCatching {
        val o = JSONObject(prefs.getString(K_FOLDERS, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getString(it) }
    }.getOrDefault(emptyMap())

    /** Puts [key] in the 묶음 [name] (blank takes it out of any). */
    fun setFolder(key: String, name: String?) {
        val n = name?.trim()?.take(FOLDER_NAME).orEmpty()
        val next = if (n.isEmpty()) _folders.value - key else _folders.value + (key to n)
        prefs.edit().putString(K_FOLDERS, JSONObject(next).toString()).apply()
        _folders.value = next
    }

    /** A 묶음 renamed ([to] blank lets every thing in it out). */
    fun renameFolder(from: String, to: String) {
        val n = to.trim().take(FOLDER_NAME)
        val next = _folders.value.mapNotNull { (k, v) -> if (v != from) k to v else if (n.isEmpty()) null else k to n }.toMap()
        prefs.edit().putString(K_FOLDERS, JSONObject(next).toString()).apply()
        _folders.value = next
    }

    /**
     * 1.37 PC 3차 (‘최근 것부터’로 보면 방금 저장한 테마가 맨 뒤에): when each theme came into 내 서랍 or was last put on, to the
     * millisecond ([ThemeLease.Wear.from] is only the day), by its key.
     */
    private val _placed = MutableStateFlow(readPlaced())
    val placed: StateFlow<Map<String, Long>> = _placed.asStateFlow()

    private fun readPlaced(): Map<String, Long> = runCatching {
        val o = JSONObject(prefs.getString(K_PLACED, "{}") ?: "{}")
        o.keys().asSequence().associateWith { o.getLong(it) }
    }.getOrDefault(emptyMap())

    private fun stamp(key: String) {
        val keys = _lease.value.history.map { it.key }.toSet() + key
        val next = (_placed.value + (key to Today.millis())).filterKeys { it in keys }
        prefs.edit().putString(K_PLACED, JSONObject(next).toString()).apply()
        _placed.value = next
    }

    companion object {
        /** The name the studio gives a look made of other themes' parts ("조합 · <내 모양>"). */
        const val MIX = "조합 · "
        fun isMix(title: String) = title.startsWith(MIX)
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
        private const val K_OVERLAYS = "overlays"
        private const val K_KEEP_OVERLAYS = "keep_overlays"
        private const val K_SKIP = "skip_ver"
        private const val K_PREV_VER = "prev_ver"
        private const val K_FOLDERS = "folders"
        private const val K_PLACED = "placed"
        private const val TAG = "BamnatDrawer"
        private const val UNDO_ROW_MS = 4_000L
        const val FOLDER_NAME = 12
        /** What [mark] keeps of this wardrobe: the theme worn and what 벗기 and 되돌리기 need of it. */
        private val UNDO_KEYS = listOf(K_LEASE, K_MINE, K_WORN_LOOK, K_PREV, K_OVERLAYS)
    }
}

/** PC 확인 5c21f3bb: the 받는 칸 answers of the tabs laid on, by name, as formulas' 칸("이름") read them (older first). */
fun overlayInputs(list: List<ThemeWardrobe.Overlay>): Map<String, Any?> = buildMap {
    list.sortedBy { it.at }.forEach { o ->
        runCatching { RecipeCodec.read(o.body) }.getOrNull()?.let { putAll(com.bamnat.core.recipe.RecipeResolve.byLabel(it, o.answers)) }
    }
}
