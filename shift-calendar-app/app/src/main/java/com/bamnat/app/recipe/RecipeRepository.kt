package com.bamnat.app.recipe

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.data.db.AppliedRecipeEntity
import com.bamnat.app.data.db.RecipeDraftEntity
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import org.json.JSONObject

/**
 * [RCP-17][RCP-25][RCP-26] 나눔 판 on this phone: putting a recipe on (piece by piece, with what each replaced kept), 7일
 * 되돌리기, the 7-day mark sent once, and the recipes being made. The server is reached only through [AppContainer.recipeApi].
 */
class RecipeRepository(private val c: AppContainer, context: Context) {
    private val dao = c.database.recipeDao()
    val pieces = RecipePieces(c)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val app: Context = context.applicationContext

    val applied: Flow<List<AppliedRecipeEntity>> = dao.observeApplied()
    val drafts: Flow<List<RecipeDraftEntity>> = dao.observeDrafts()

    /** [RCP-38] 설정 › 나눔 판 › 나눔 판 쓰기: off hides every way in (the plaza tab, '이 설정 나누기', the news line). */
    private val _on = MutableStateFlow(prefs.getBoolean(K_ON, true))
    val on: StateFlow<Boolean> = _on.asStateFlow()
    fun setOn(v: Boolean) { prefs.edit().putBoolean(K_ON, v).apply(); _on.value = v }

    /** [RCP-23] The rules of the plaza were read and agreed to before the first post or recipe (version of the rules). */
    fun agreed(): Int = prefs.getInt(K_AGREED, 0)
    fun agree(version: Int) = prefs.edit().putInt(K_AGREED, version).apply()

    fun reload() { _on.value = prefs.getBoolean(K_ON, true); _requestsOn.value = prefs.getBoolean(K_REQUESTS, true) }

    // [RCX-20] 1.35 끄기: each new way the 나눔 판 speaks up can be turned off on its own (설정 › 나눔 판).
    /** 새 판 알림: the daily notice that a recipe in use has a new version. */
    fun newVersionNews(): Boolean = prefs.getBoolean(K_NEWS_NEW, true)
    fun setNewVersionNews(v: Boolean) = prefs.edit().putBoolean(K_NEWS_NEW, v).apply()

    /** 팔로우·답 알림: followed makers' new recipes and makers' answers to my questions. */
    fun followNews(): Boolean = prefs.getBoolean(K_NEWS_FOLLOW, true)
    fun setFollowNews(v: Boolean) = prefs.edit().putBoolean(K_NEWS_FOLLOW, v).apply()

    /**
     * [PRF-05] 1.36 차단한 분 (공개 id): the 장터 list is the same for every phone (edge-cached), so this phone leaves them out itself.
     * Kept here and refreshed from the server's 팔로잉 call.
     */
    private val _blocked = MutableStateFlow(prefs.getString(K_BLOCKED, null)?.split(',')?.filter { it.isNotEmpty() }?.toSet().orEmpty())
    val blocked: StateFlow<Set<String>> = _blocked.asStateFlow()
    fun setBlocked(ids: Set<String>) { prefs.edit().putString(K_BLOCKED, ids.joinToString(",")).apply(); _blocked.value = ids }
    fun block(id: String, on: Boolean) = setBlocked(if (on) _blocked.value + id else _blocked.value - id)
    /** Whether [item] is from a maker this phone blocked. */
    /** [PRF-02] Goes up on every 팔로우·그만두기·차단·풀기, so 마을's 팔로잉 줄 asks again at once (빌드 221dd083 확인: it waited a minute). */
    private val _people = MutableStateFlow(0)
    val people: StateFlow<Int> = _people.asStateFlow()
    fun peopleChanged() { _people.value++ }
    fun hides(item: RecipeItem): Boolean = item.maker != null && item.maker in _blocked.value

    /**
     * [PRF-03] 내 프로필 최근 알림: the milestones and badges already shown (the server works out where each stands, the phone
     * remembers what it told), and since when new followers, questions and 수정판 count.
     */
    fun seenInbox(): Set<String> = keyList(K_SEEN_INBOX_LIST).toSet()
    /** [at]: the server's time of what was seen, so the next ask starts there whatever this phone's clock says. */
    fun markInboxSeen(keys: Collection<String>, at: java.time.Instant? = null) {
        prefs.edit().putString(K_SEEN_INBOX_LIST, keyListJson(keyList(K_SEEN_INBOX_LIST), keys))
            .putString(K_INBOX_SINCE, (at ?: java.time.Instant.ofEpochMilli(Today.millis())).toString()).apply()
    }
    /**
     * Keys kept in the order they were seen, newest last, so the oldest go first past [KEYS_KEPT]
     * (a string set has no order, and cutting it could forget a new key and say it again).
     */
    private fun keyList(key: String): List<String> = prefs.getString(key, null)?.let { s ->
        runCatching { org.json.JSONArray(s).let { a -> List(a.length()) { a.getString(it) } } }.getOrNull()
    }.orEmpty()
    private fun keyListJson(was: List<String>, add: Collection<String>): String =
        org.json.JSONArray((was.filterNot { it in add } + add.distinct()).takeLast(KEYS_KEPT)).toString()
    private fun counted(key: String, code: String) = prefs.edit().putString(key, keyListJson(keyList(key), listOf(code))).apply()
    /** Whether this phone was counted among a recipe's 쓰는 분 (once a recipe, M9). */
    fun countedHere(code: String): Boolean = code in keyList(K_COUNTED)
    /** Set once this phone has a public recipe, so 소식 asks about my profile only then. */
    fun hasProfile(v: Boolean) = prefs.edit().putBoolean(K_HAS_PROFILE, v).apply()
    fun hasProfile(): Boolean = prefs.getBoolean(K_HAS_PROFILE, false)
    /** 1.36 PC 6차: 프로필만 지우기 left the works (the 빈 프로필 line shows only then, not on a first upload). */
    fun profileCleared(v: Boolean) = prefs.edit().putBoolean(K_PROFILE_CLEARED, v).apply()
    fun profileCleared(): Boolean = prefs.getBoolean(K_PROFILE_CLEARED, false)
    fun inboxSince(): java.time.Instant? = prefs.getString(K_INBOX_SINCE, null)?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }

    /** 요청 판 보이기: the 요청 판 way in on the 나눔 판 card. */
    private val _requestsOn = MutableStateFlow(prefs.getBoolean(K_REQUESTS, true))
    val requestsOn: StateFlow<Boolean> = _requestsOn.asStateFlow()
    fun setRequestsOn(v: Boolean) { prefs.edit().putBoolean(K_REQUESTS, v).apply(); _requestsOn.value = v }

    /** One piece chosen for applying: how it meets mine, and its values with the inputs filled in. */
    data class Pick(val kind: PieceKind, val mode: RecipePieces.Mode)

    /**
     * Puts [r] on calendar [cal]: the [picks] in order, each with [answers] for its inputs. Records stay as they are. Returns the
     * id of the record that 되돌리기 reads, or null when nothing changed.
     */
    /** 2.0.0 [RVS-12] The pieces the last [apply] could not put on (this app could not read or write them), for 넣기 to say. */
    @Volatile var lastMissed: List<PieceKind> = emptyList()
        private set

    suspend fun apply(r: Recipe, code: String, version: Int, cal: String, picks: List<Pick>, answers: Map<String, Any?>): Long? {
        val filled = RecipeResolve.answers(r, answers)
        val before = JSONObject()
        val done = ArrayList<String>()
        val missed = ArrayList<PieceKind>()
        lastMissed = emptyList()
        for (pick in picks) {
            val piece = r.pieces.firstOrNull { it.kind == pick.kind } ?: continue
            // 2.0.0 [RVS-12] (갈래 R SD-07): a piece that could not be put on was let go without a word, the same as one already on.
            val snap = runCatching {
                val values = RecipeResolve.piece(r, piece, filled)
                pieces.apply(pick.kind, values, cal, code.ifEmpty { "x" + Today.millis().toString(36) }, pick.mode)
            }.onFailure { missed += pick.kind }.getOrNull()
            lastMissed = missed.toList()
            if (snap == null) continue
            before.put(pick.kind.key, snap)
            done += pick.kind.key
        }
        if (done.isEmpty()) {
            // PC 확인 5c21f3bb (적립일 17을 넣었는데 10이 쓰이는 듯): the pieces were all here already, but new answers to its 받는 칸 are
            // still the receiver's to change; the formulas read them from the record, so the record takes them.
            val same = if (code.isEmpty() || r.inputs.isEmpty()) null else dao.applied()
                .filter { it.undoneAt == null && it.replacedAt == null && it.calendarId == cal && it.code == code }.maxByOrNull { it.appliedAt }
            val now = JSONObject(filled.mapValues { (_, v) -> v }).toString()
            if (same != null && same.inputs != now) { dao.upsertApplied(same.copy(inputs = now)); return same.id }
            return null
        }
        // A later recipe on the same pieces takes the earlier one's place: it is no longer the one 되돌리기 offers.
        // [RVF-06] 개발 전 검토 RCP-01: piece by piece. A piece that adds (메모 틀·알람·규칙·가계부·투자) takes nobody's place; one that
        // replaces takes that piece only, the earlier work keeping its other pieces (되돌리기, 받는 칸 답, 새 판 알림). Only when nothing of it is
        // left — or it is the same work put on again — is the earlier record 'replaced' as a whole.
        val now = Today.millis()
        val takes = done.filter { k -> PieceKind.of(k) !in ADDS }.toSet()
        dao.applied().filter { it.undoneAt == null && it.replacedAt == null && it.calendarId == cal }.forEach { e ->
            val had = e.pieces.split(',')
            val same = code.isNotEmpty() && e.code == code
            if (same && had.any { it in done }) { dao.upsertApplied(e.copy(replacedAt = now)); return@forEach }
            if (had.none { it in takes }) return@forEach
            val left = had.filter { it !in takes }
            if (left.isEmpty()) dao.upsertApplied(e.copy(replacedAt = now))
            else dao.upsertApplied(e.copy(pieces = left.joinToString(","),
                before = runCatching { JSONObject(e.before).also { b -> takes.forEach { b.remove(it) } }.toString() }.getOrDefault(e.before)))
        }
        val id = dao.insertApplied(AppliedRecipeEntity(
            code = code, version = version, hash = RecipeCodec.hash(r), title = r.title, calendarId = cal, body = RecipeCodec.write(r),
            inputs = JSONObject(filled.mapValues { (_, v) -> v }).toString(), pieces = done.joinToString(","), before = before.toString(),
            appliedAt = now, undoneAt = null, replacedAt = null, keptSent = false,
        ))
        countUse(code)
        return id
    }

    /**
     * 쓰는 분: this phone counted once for [code]. 1.36 보안 점검 M9 (PC 확인 7525ccff "넣었다 되돌려도 쓰는 분이 늘어요"): once a recipe,
     * however often it is put on again; the server also counts only a few a recipe from one line a day. PC 확인 보안 8차 (넣어도 0명):
     * called for every way a recipe goes on from its page, a tab laid on the theme too, not only a put-on that leaves a record; a
     * refusal is tried again at the next put-on. True when counted just now.
     */
    suspend fun countUse(code: String): Boolean {
        if (code.isEmpty() || code.startsWith("builtin:") || !c.recipeApi.enabled || code in keyList(K_COUNTED)) return false
        val sent = runCatching { c.recipeApi.applied(code, kept = false) }
        if (com.bamnat.app.BuildConfig.DEBUG) android.util.Log.d("BamnatRecipe", "쓰는 분 $code: " + (sent.exceptionOrNull()?.let { "실패 $it" } ?: "보냄"))
        if (sent.isSuccess) counted(K_COUNTED, code)
        return sent.isSuccess
    }

    // ---- [RCX-02][RCX-03] 1.35 구독과 새 판 ----

    /** [RCX-02] 구독 (on this phone only): whether a recipe put on is followed for new versions; on unless turned off. */
    fun subscribed(code: String): Boolean = !prefs.getBoolean("$K_UNSUB$code", false)
    fun subscribe(code: String, on: Boolean) = prefs.edit().putBoolean("$K_UNSUB$code", !on).apply()

    /** The recipes on this phone still in use, newest per code, that a new version could be for. */
    suspend fun inUse(): List<AppliedRecipeEntity> = dao.applied().filter { it.code.isNotEmpty() && it.undoneAt == null && it.replacedAt == null }
        .groupBy { it.code }.map { (_, list) -> list.maxBy { it.appliedAt } }

    /**
     * 2.0.0 [RVS-12] 총괄 HQ-57 ②: works put on in 1.36~1.39 whose 부품 꾸미기 has a formula (`{"fx"}`) went on without it (넣기 could
     * not read its own formula back and let the piece go without a word, 장터 144개). Nothing is put back by itself, so no one's calendar
     * changes on its own after the update: each such work still in use, on a calendar whose 부품 no later work holds, is offered once in
     * 밤낮 소식 and stays offered in 공방 › 내 것 › 받은 작품 ([putMissedParts]).
     */
    suspend fun missedParts(): List<AppliedRecipeEntity> {
        val all = dao.applied().filter { it.undoneAt == null && it.replacedAt == null }
        return all.filter { e ->
            "parts" !in e.pieces.split(',') && all.none { o -> o.calendarId == e.calendarId && o.appliedAt > e.appliedAt && "parts" in o.pieces.split(',') } &&
                runCatching { RecipeCodec.readSoft(e.body).recipe }.getOrNull()?.pieces?.firstOrNull { it.kind == PieceKind.PARTS }
                    ?.let { p -> (p.values["parts"] as? List<*>).orEmpty().any { l -> (l as? Map<*, *>)?.values?.any { it is com.bamnat.core.recipe.Ref.Formula } == true } } == true
        }.groupBy { it.calendarId }.map { (_, list) -> list.maxBy { it.appliedAt } }
    }

    /** [missedParts]: [e]'s 부품 꾸미기 put on now, with the answers it was put on with; 7일 되돌리기 as any 넣기. */
    suspend fun putMissedParts(e: AppliedRecipeEntity): Long? {
        val r = RecipeCodec.readSoft(e.body).recipe
        val answers = runCatching { JSONObject(e.inputs).let { j -> j.keys().asSequence().associateWith { j.opt(it).takeIf { v -> v != JSONObject.NULL } } } }.getOrDefault(emptyMap())
        return apply(r, e.code, e.version, e.calendarId, listOf(Pick(PieceKind.PARTS, RecipePieces.Mode.REPLACE)), answers)
    }

    /** [RCX-02] Codes with a newer version than the one put on, asked with the codes only (no device). */
    suspend fun newer(): Map<String, Int> {
        val mine = inUse().filter { subscribed(it.code) }
        if (mine.isEmpty() || !c.recipeApi.enabled) return emptyMap()
        val v = runCatching { c.recipeApi.versions(mine.map { it.code }) }.getOrDefault(emptyMap())
        // 1.37 PC 3차 (공방 › 내 것에 가짜 ‘새 판 v1 있어요’): a theme put on without its 판 known (0) is at least the first one.
        return mine.mapNotNull { e -> v[e.code]?.takeIf { it > maxOf(e.version, 1) }?.let { e.code to it } }.toMap()
    }

    /**
     * [FXR-01] 1.36 받는 칸을 수식이 읽기: the answers of every recipe still in use, by the input's name, as `칸("이름")` reads them. A
     * recipe put on [cal] wins over one put on another calendar, a newer one over an older.
     */
    fun formulaInputs(rows: List<AppliedRecipeEntity>, cal: String): Map<String, Any?> = formulaInputsOf(rows, cal)

    fun formulaInputs(cal: String): Flow<Map<String, Any?>> =
        applied.map { formulaInputs(it, cal) }.distinctUntilChanged().flowOn(kotlinx.coroutines.Dispatchers.Default)

    private fun answersOf(e: AppliedRecipeEntity): Map<String, Any?> = runCatching {
        val o = JSONObject(e.inputs)
        o.keys().asSequence().associateWith { k -> o.get(k).takeIf { it != JSONObject.NULL } }
    }.getOrDefault(emptyMap())

    /** [RCX-03] For each piece put on from [e], what [newer] changes: the fields to offer and those that meet a change of mine. */
    suspend fun changes(e: AppliedRecipeEntity, newer: Recipe): List<com.bamnat.core.recipe.Merge3.Result> {
        val old = runCatching { RecipeCodec.read(e.body) }.getOrNull() ?: return emptyList()
        val given = answersOf(e)
        val a0 = RecipeResolve.answers(old, given)
        val a1 = RecipeResolve.answers(newer, given)
        return e.pieces.split(',').mapNotNull(PieceKind::of).mapNotNull { kind ->
            val theirs = newer.pieces.firstOrNull { it.kind == kind }?.let { RecipeResolve.piece(newer, it, a1) } ?: return@mapNotNull null
            val base = old.pieces.firstOrNull { it.kind == kind }?.let { RecipeResolve.piece(old, it, a0) }.orEmpty()
            val mine = runCatching { pieces.current(kind, base, e.calendarId) }.getOrDefault(base)
            com.bamnat.core.recipe.Merge3.of(kind, base, mine, theirs).takeIf { it.fields.isNotEmpty() }
        }
    }

    /**
     * [RCX-03] 새 판 반영: the [picked] fields of each piece from [newer], the rest as this phone has them, put on as one more
     * application (so it too can be taken back for 7 days).
     */
    suspend fun update(e: AppliedRecipeEntity, newer: Recipe, version: Int, picked: Map<PieceKind, Set<String>>): Long? {
        val old = runCatching { RecipeCodec.read(e.body) }.getOrNull() ?: return null
        val given = answersOf(e)
        val a0 = RecipeResolve.answers(old, given)
        val a1 = RecipeResolve.answers(newer, given)
        val merged = picked.filterValues { it.isNotEmpty() }.mapNotNull { (kind, paths) ->
            val theirs = newer.pieces.firstOrNull { it.kind == kind }?.let { RecipeResolve.piece(newer, it, a1) } ?: return@mapNotNull null
            val base = old.pieces.firstOrNull { it.kind == kind }?.let { RecipeResolve.piece(old, it, a0) }.orEmpty()
            val mine = runCatching { pieces.current(kind, base, e.calendarId) }.getOrDefault(base)
            com.bamnat.core.recipe.Piece(kind, com.bamnat.core.recipe.Merge3.apply(mine, theirs, paths))
        }
        if (merged.isEmpty()) return null
        val r = newer.copy(inputs = emptyList(), tokens = emptyList(), pieces = merged)
        val id = apply(r, e.code, version, e.calendarId, // 1.39 [NTC-03] 판 반영은 겹치는 알림을 말없이 끄지 않아요 (규칙·단추 BOTH).
            merged.map { Pick(it.kind, if (it.kind == PieceKind.MEMO_TEMPLATES || it.kind == PieceKind.LOGIC) RecipePieces.Mode.BOTH else RecipePieces.Mode.REPLACE) }, emptyMap())
            ?: return null
        // [RCX-31] What the next 새 판 is compared with is this version as its maker made it, with the same answers: kept as the
        // merge, a field I had changed and kept would look like the recipe's own and be offered (and ticked) for the taking.
        dao.applied().firstOrNull { it.id == id }?.let { row ->
            dao.upsertApplied(row.copy(body = RecipeCodec.write(newer), hash = RecipeCodec.hash(newer), inputs = JSONObject(a1.mapValues { (_, v) -> v }).toString()))
        }
        return id
    }

    /**
     * [RCX-02][RCX-07][RCX-10] 1.35 The 나눔 판 lines of the daily 밤낮 소식: new versions of recipes in use (asked with codes only),
     * new recipes of followed makers and makers' answers to my questions. Empty when 나눔 판 is off.
     */
    suspend fun newsLines(): List<String> {
        if (!on.value || !c.recipeApi.enabled) return emptyList()
        val lines = ArrayList<String>()
        val newer = if (newVersionNews()) newer() else emptyMap()
        if (newer.isNotEmpty()) {
            val titles = inUse().filter { it.code in newer }.map { it.title }
            lines += "‘${titles.first()}’" + (if (titles.size > 1) " 외 ${titles.size - 1}개" else "") + "에 새 판이 나왔어요"
        }
        // 2.0.0 [RVS-12] HQ-57 ②: a work whose 부품 꾸미기 did not go on before, offered once.
        missedParts().filter { !prefs.getBoolean("$K_PARTS_SAID${it.id}", false) }.takeIf { it.isNotEmpty() }?.let { list ->
            lines += "‘${list.first().title}’" + (if (list.size > 1) " 외 ${list.size - 1}개" else "") + "에 빠져 있던 부품 꾸미기를 이제 넣을 수 있어요 (마을 › 공방 › 내 것 › 받은 작품)"
            prefs.edit().apply { list.forEach { putBoolean("$K_PARTS_SAID${it.id}", true) } }.apply()
        }
        // [RCX-24] 되돌리기 in 소식 too: once, on the last day it can still be taken back.
        val now = Today.millis()
        inUse().filter { canUndo(it, now) && now - it.appliedAt >= (UNDO_DAYS - 1) * DAY_MILLIS && !prefs.getBoolean("$K_UNDO_SAID${it.id}", false) }
            .forEach { e ->
                lines += "‘${e.title}’${com.bamnat.app.ui.Fmt.eun(e.title)} 내일까지 되돌릴 수 있어요 (설정 › 장터·내 작품)"
                prefs.edit().putBoolean("$K_UNDO_SAID${e.id}", true).apply()
            }
        // [LIN-04] 1.35 A 수정판 of my recipe is news whether or not I follow anyone (the news call says it).
        if (c.flags.on(com.bamnat.core.flags.FlagCatalog.RECIPE_TALK)) {
            val since = prefs.getString(K_NEWS_SINCE, null)?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
            // [TOWN-14] PC 확인 f7c41660 (팔로우한 뒤 처음 올린 작품이 소식에서 빠졌어요): `since` is this phone's clock, the server's works
            // are dated by its own; asked from two days before, so a clock apart or a late run loses nothing, and a work already
            // said is not said again.
            val asked = since?.minus(java.time.Duration.ofDays(NEWS_OVERLAP_DAYS))
            runCatching { c.recipeApi.news(c.companyStore.device, asked) }.onSuccess { all ->
                prefs.edit().putString(K_NEWS_SINCE, java.time.Instant.ofEpochMilli(Today.millis()).toString()).apply()
                val told = keyList(K_NEWS_TOLD).toSet()
                val n = all.copy(recipes = all.recipes.filter { "r:${it.code}" !in told }, remixes = all.remixes.filter { "x:${it.first.code}" !in told })
                prefs.edit().putString(K_NEWS_TOLD, keyListJson(keyList(K_NEWS_TOLD),
                    n.recipes.map { "r:${it.code}" } + n.remixes.map { "x:${it.first.code}" })).apply()
                if (followNews()) n.recipes.firstOrNull()?.let { r -> lines += "팔로우한 분의 새 작품 ‘${r.title}’" + if (n.recipes.size > 1) " 외 ${n.recipes.size - 1}개" else "" }
                if (followNews()) n.replies.firstOrNull()?.let { (_, title, _) -> lines += "‘$title’에 만든 분이 답했어요" }
                // [PRF-03] 1.36 My own profile's news, once: new followers and a milestone passed (the rest waits in 내 프로필).
                // Asked only of a phone with recipes up (내 프로필 has something to say), with the same since as 소식.
                if (followNews() && prefs.getBoolean(K_HAS_PROFILE, false)) runCatching { c.recipeApi.inbox(c.companyStore.device, since) }.getOrNull()?.let { box ->
                    val told = keyList(K_TOLD_LIST).toSet() + seenInbox()
                    if (box.newFollowers > 0) lines += "새 팔로워 ${box.newFollowers}명이 생겼어요 (마을 › 내 프로필)"
                    box.milestones.firstOrNull { it.key !in told }?.let { m ->
                        lines += "‘${m.title}’" + (if (m.kind == "users") "${com.bamnat.app.ui.Fmt.obj("‘${m.title}’")} 쓰는 분이 ${m.n}명을 넘었어요" else "에 좋아요가 ${m.n}개를 넘었어요")
                        prefs.edit().putString(K_TOLD_LIST, keyListJson(keyList(K_TOLD_LIST), listOf(m.key))).apply()
                    }
                }
                // 1.36 [VIL-15] A request of mine someone finished is news whether or not I follow anyone.
                n.requests.firstOrNull()?.let { (_, title, _) ->
                    lines += "부탁한 작품이 ‘$title’${com.bamnat.app.ui.Fmt.ro("‘$title’")} 완성됐어요 (마을 › 공방 › 요청)" +
                        if (n.requests.size > 1) " 외 ${n.requests.size - 1}개" else ""
                }
                n.remixes.firstOrNull()?.let { (r, mine) -> lines += "내 판 ‘$mine’${com.bamnat.app.ui.Fmt.obj("‘$mine’")} 고친 수정판 ‘${r.title}’${com.bamnat.app.ui.Fmt.iga("‘${r.title}’")} 올라왔어요. 달라진 점을 보고 가져올 수 있어요" +
                    if (n.remixes.size > 1) " (외 ${n.remixes.size - 1}개)" else "" }
            }
        }
        return lines
    }

    /**
     * 2.0.0 [RVM-36] 검토 HQ-52: what people did with my works since the last round, as rows of the 알림함 ([MakerReactions]). Asked when
     * the app comes back, at most every [REACT_EVERY_MS], only on a phone that has put a work up, and not at all when 나눔 판 is off or
     * both kinds of rows were turned off ('다시 보지 않기'). It is the same call 내 프로필 makes, with its own since; nothing new is sent.
     * Returns how many rows went in.
     */
    suspend fun reactions(now: Long = Today.millis()): Int {
        if (!_on.value || !c.recipeApi.enabled) return 0
        val made = hasProfile() || app.getSharedPreferences("maker", Context.MODE_PRIVATE).getBoolean("made", false)
        if (!made) return 0
        val inbox = com.bamnat.app.inbox.Inbox.get(app)
        if (!inbox.allowed(MakerReactions.KIND, com.bamnat.app.inbox.InboxSection.PEOPLE) &&
            !inbox.allowed(MakerReactions.KIND_DECIDED, com.bamnat.app.inbox.InboxSection.NOTICE)) return 0
        val asked = prefs.getLong(K_REACT_ASKED, 0L)
        if (now >= asked && now - asked < REACT_EVERY_MS) return 0
        prefs.edit().putLong(K_REACT_ASKED, now).apply()
        val since = prefs.getString(K_REACT_SINCE, null)?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        val box = c.recipeApi.inbox(c.companyStore.device, since) ?: return 0
        val round = MakerReactions.round(
            box, inbox.rows.value.map { it.key }.toSet(), MakerReactions.decode(prefs.getString(K_REACT_TALLY, null)),
            MakerReactions.decodeTotals(prefs.getString(K_REACT_TOTALS, null)), keyList(K_REACT_TOLD).toSet(),
            prefs.getInt(K_REACT_FOLLOWERS, 0), now,
        )
        val put = round.rows.count { inbox.post(it) }
        prefs.edit()
            .putString(K_REACT_SINCE, (box.at ?: java.time.Instant.ofEpochMilli(now)).toString())
            .putString(K_REACT_TALLY, MakerReactions.encode(round.tallies))
            .putString(K_REACT_TOTALS, MakerReactions.encodeTotals(round.totals))
            .putString(K_REACT_TOLD, keyListJson(keyList(K_REACT_TOLD), round.told))
            .putInt(K_REACT_FOLLOWERS, round.followers)
            .apply()
        return put
    }

    /** Whether [e] can still be taken back: on, and within [UNDO_DAYS] days. */
    fun canUndo(e: AppliedRecipeEntity, now: Long = Today.millis()): Boolean =
        e.undoneAt == null && e.replacedAt == null && now - e.appliedAt < UNDO_DAYS * DAY_MILLIS

    /** 2.0.0 [RVM-45] 검토 RCP-02: what the last [undo] / [undoAll] left as the user made it, and the pieces it could not take back. */
    data class UndoReport(val kept: Int = 0, val failed: List<PieceKind> = emptyList(), /** [RVM-50] shifts whose alarms went off. */ val alarmsOff: List<String> = emptyList())

    /**
     * 2.0.0 [RVM-50] 검토 RCP-04: by shift, the alarm times (minutes before) the works still on this phone want, other than record
     * [except]; 되돌리기 of one work leaves those on.
     */
    suspend fun alarmsWanted(except: Long?): Map<Long, Set<Int>> {
        val out = HashMap<Long, MutableSet<Int>>()
        for (e in dao.applied()) {
            if (e.id == except || e.undoneAt != null || e.replacedAt != null || PieceKind.ALARMS.key !in e.pieces.split(',')) continue
            val a = runCatching { JSONObject(e.before).optJSONObject(PieceKind.ALARMS.key) }.getOrNull() ?: continue
            for (k in listOf("types", "also")) {
                val list = a.optJSONArray(k) ?: continue
                for (i in 0 until list.length()) {
                    val o = list.optJSONObject(i) ?: continue
                    val set = out.getOrPut(o.optLong("id")) { HashSet() }
                    for (f in listOf("added", "wanted")) o.optJSONArray(f)?.let { n -> for (j in 0 until n.length()) set += n.getInt(j) }
                }
            }
        }
        return out
    }

    @Volatile var lastUndo = UndoReport()
        private set

    /** What 되돌리기 says after [undo] (or [undoAll]) gave [ok]: what is truly so, never a plain 되돌렸어요 over what stayed. */
    fun undoSay(ok: Boolean): String = lastUndo.let { r ->
        when {
            r.failed.isNotEmpty() -> "되돌리지 못한 것이 있어요(" + r.failed.joinToString("·") { it.label } + "). 한 번 더 눌러 주세요"
            !ok -> "되돌릴 수 없어요. 넣은 지 7일이 지났거나 이미 되돌렸어요"
            r.kept > 0 -> "되돌렸어요. 넣은 뒤 직접 바꾼 ${r.kept}곳은 그대로 뒀어요"
            else -> "되돌렸어요. 그 사이 적은 기록은 그대로예요"
        } + if (ok && r.alarmsOff.isNotEmpty()) " · " + r.alarmsOff.distinct().joinToString("·") { "‘$it’" } + " 알람이 꺼졌어요" else ""
    }

    /** 되돌리기: every piece put back, last first; what the user changed since stays. */
    suspend fun undo(id: Long): Boolean = undoOne(id).also { lastUndo = it.second }.first

    private suspend fun undoOne(id: Long): Pair<Boolean, UndoReport> {
        val e = dao.appliedById(id) ?: return false to UndoReport()
        if (!canUndo(e)) return false to UndoReport()
        val before = runCatching { JSONObject(e.before) }.getOrNull() ?: return false to UndoReport()
        // 2.0.0 [RVM-45] 검토 RCP-02: a piece that failed was swallowed and the whole record stamped 되돌림, so it could not be tried
        // again. Now the record keeps the pieces still on (and stays undoable); the rest are taken back.
        val failed = ArrayList<String>()
        pieces.kept = 0
        pieces.alarmsOff.clear()
        pieces.undoing = e.id
        for (key in e.pieces.split(',').reversed()) {
            val kind = PieceKind.of(key) ?: continue
            val snap = before.optJSONObject(key) ?: continue
            runCatching { pieces.restore(kind, snap, e.calendarId) }.onFailure { failed += key }
        }
        pieces.undoing = null
        val report = UndoReport(pieces.kept, failed.mapNotNull(PieceKind::of), pieces.alarmsOff.toList())
        if (failed.isNotEmpty()) {
            dao.upsertApplied(e.copy(pieces = e.pieces.split(',').filter { it in failed }.joinToString(",")))
            return true to report
        }
        dao.upsertApplied(e.copy(undoneAt = Today.millis()))
        // [J135-16] A theme's looks taken back here (내 레시피 or the recipe's own page): 입는 중 goes back to what was worn before.
        if (e.code.isNotEmpty()) runCatching { c.wardrobe.forget(e.code) }
        return true to report
    }

    /**
     * 되돌리기 on a recipe's own page: every put-on of [code] that can still be taken back, newest first, so none of it is left
     * (PC 확인 8773e3ae: the same recipe put on twice, with other pieces or on another calendar, kept the older one's card). How many.
     */
    suspend fun undoAll(code: String): Int {
        var all = UndoReport()
        val n = undoable(code).count { e ->
            val (ok, r) = undoOne(e.id)
            all = UndoReport(all.kept + r.kept, all.failed + r.failed, all.alarmsOff + r.alarmsOff)
            ok
        }
        lastUndo = all
        return n
    }

    /** Every put-on of [code] that can still be taken back, newest first (what 되돌리기 on its page takes back). */
    suspend fun undoable(code: String): List<AppliedRecipeEntity> {
        if (code.isEmpty()) return emptyList()
        val now = Today.millis()
        return dao.applied().filter { it.code == code && canUndo(it, now) }.sortedByDescending { it.appliedAt }
    }

    /** [RCP-26] The 7일 유지 mark of each recipe still on after 7 days, sent once; old rows no longer on are dropped after 90 days. */
    suspend fun sendKept() {
        val now = Today.millis()
        for (e in dao.applied()) {
            if (e.keptSent || e.code.isEmpty() || e.undoneAt != null || now - e.appliedAt < UNDO_DAYS * DAY_MILLIS) continue
            if (!c.recipeApi.enabled) return
            // M9: 7일 유지 once a recipe a phone too, as 쓰는 분 is.
            if (e.code in keyList(K_KEPT_COUNTED)) { dao.upsertApplied(e.copy(keptSent = true)); continue }
            if (runCatching { c.recipeApi.applied(e.code, kept = true) }.isSuccess) {
                dao.upsertApplied(e.copy(keptSent = true))
                counted(K_KEPT_COUNTED, e.code)
            }
        }
        dao.pruneApplied(now - KEEP_DAYS * DAY_MILLIS)
    }

    // ---- drafts (만들기) ----

    suspend fun saveDraft(id: Long, body: String, step: Int, code: String?, scope: String): Long {
        val e = RecipeDraftEntity(id = id, body = body, step = step, code = code, scope = scope, updatedAt = Today.millis())
        return if (id == 0L) dao.insertDraft(e) else { dao.upsertDraft(e); id }
    }

    suspend fun deleteDraft(id: Long) = dao.deleteDraft(id)

    companion object {
        const val PREFS = "recipe"
        private const val K_ON = "on"
        private const val K_AGREED = "agreed"
        private const val K_UNSUB = "unsub_"
        private const val K_NEWS_NEW = "news_new"
        private const val K_NEWS_FOLLOW = "news_follow"
        private const val K_UNDO_SAID = "undo_said_"
        private const val K_PARTS_SAID = "parts_missed_said_"
        private const val K_REQUESTS = "requests_on"
        private const val K_NEWS_SINCE = "news_since"
        /** [TOWN-14] The works and 수정판 소식 already said (so the two days asked again say nothing twice). */
        private const val K_NEWS_TOLD = "news_told"
        private const val NEWS_OVERLAP_DAYS = 2L
        private const val K_BLOCKED = "blocked_makers"
        private const val K_SEEN_INBOX_LIST = "seen_inbox_list"
        private const val KEYS_KEPT = 400
        private const val K_INBOX_SINCE = "inbox_since"
        private const val K_TOLD_LIST = "told_inbox_list"
        /** M9: the codes this phone has already counted toward 쓰는 분 and 7일 유지. */
        private const val K_COUNTED = "counted_applied"
        private const val K_KEPT_COUNTED = "counted_kept"
        private const val K_HAS_PROFILE = "has_profile"
        private const val K_PROFILE_CLEARED = "profile_cleared"
        /** [RVM-36] HQ-52 The 알림함's reactions: since (the server's time), when last asked, and what the rows still up counted. */
        private const val K_REACT_SINCE = "react_since"
        private const val K_REACT_ASKED = "react_asked"
        private const val K_REACT_TALLY = "react_tally"
        private const val K_REACT_TOTALS = "react_totals"
        private const val K_REACT_TOLD = "react_told"
        private const val K_REACT_FOLLOWERS = "react_followers"
        /** Asked at most this often when the app comes back. */
        const val REACT_EVERY_MS = 10L * 60 * 1000
        /** [RVF-06] Pieces that add to what is there (RecipePieces: 알람은 더하기만, 메모 틀·분류는 더하기, 규칙·단추는 내 것 뒤에). */
        val ADDS: Set<PieceKind> = setOf(PieceKind.LOGIC, PieceKind.ALARMS, PieceKind.MEMO_TEMPLATES, PieceKind.LEDGER, PieceKind.INVEST)
        const val UNDO_DAYS = 7L
        const val KEEP_DAYS = 90L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}

/** [FXR-01] [RecipeRepository.formulaInputs] without the repository, for tests. */
internal fun formulaInputsOf(rows: List<AppliedRecipeEntity>, cal: String): Map<String, Any?> {
    val out = LinkedHashMap<String, Any?>()
    rows.filter { it.undoneAt == null && it.replacedAt == null }
        .sortedWith(compareBy<AppliedRecipeEntity>({ it.calendarId == cal }, { it.appliedAt }))
        .forEach { e ->
            val r = runCatching { RecipeCodec.read(e.body) }.getOrNull() ?: return@forEach
            val given = runCatching {
                val o = JSONObject(e.inputs)
                o.keys().asSequence().associateWith { k -> o.get(k).takeIf { it != JSONObject.NULL } }
            }.getOrDefault(emptyMap())
            out.putAll(RecipeResolve.byLabel(r, given))
        }
    return out
}
