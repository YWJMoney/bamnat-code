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
import org.json.JSONObject

/**
 * [RCP-17][RCP-25][RCP-26] 나눔 판 on this phone: putting a recipe on (piece by piece, with what each replaced kept), 7일
 * 되돌리기, the 7-day mark sent once, and the recipes being made. The server is reached only through [AppContainer.recipeApi].
 */
class RecipeRepository(private val c: AppContainer, context: Context) {
    private val dao = c.database.recipeDao()
    val pieces = RecipePieces(c)
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    val applied: Flow<List<AppliedRecipeEntity>> = dao.observeApplied()
    val drafts: Flow<List<RecipeDraftEntity>> = dao.observeDrafts()

    /** [RCP-38] 설정 › 나눔 판 › 나눔 판 쓰기: off hides every way in (the plaza tab, '이 설정 나누기', the news line). */
    private val _on = MutableStateFlow(prefs.getBoolean(K_ON, true))
    val on: StateFlow<Boolean> = _on.asStateFlow()
    fun setOn(v: Boolean) { prefs.edit().putBoolean(K_ON, v).apply(); _on.value = v }

    /** [RCP-23] The rules of the plaza were read and agreed to before the first post or recipe (version of the rules). */
    fun agreed(): Int = prefs.getInt(K_AGREED, 0)
    fun agree(version: Int) = prefs.edit().putInt(K_AGREED, version).apply()

    fun reload() { _on.value = prefs.getBoolean(K_ON, true) }

    /** One piece chosen for applying: how it meets mine, and its values with the inputs filled in. */
    data class Pick(val kind: PieceKind, val mode: RecipePieces.Mode)

    /**
     * Puts [r] on calendar [cal]: the [picks] in order, each with [answers] for its inputs. Records stay as they are. Returns the
     * id of the record that 되돌리기 reads, or null when nothing changed.
     */
    suspend fun apply(r: Recipe, code: String, version: Int, cal: String, picks: List<Pick>, answers: Map<String, Any?>): Long? {
        val filled = RecipeResolve.answers(r, answers)
        val before = JSONObject()
        val done = ArrayList<String>()
        for (pick in picks) {
            val piece = r.pieces.firstOrNull { it.kind == pick.kind } ?: continue
            val values = RecipeResolve.piece(r, piece, filled)
            val snap = runCatching { pieces.apply(pick.kind, values, cal, code.ifEmpty { "x" + Today.millis().toString(36) }, pick.mode) }.getOrNull() ?: continue
            before.put(pick.kind.key, snap)
            done += pick.kind.key
        }
        if (done.isEmpty()) return null
        // A later recipe on the same pieces takes the earlier one's place: it is no longer the one 되돌리기 offers.
        val now = Today.millis()
        dao.applied().filter { it.undoneAt == null && it.replacedAt == null && it.calendarId == cal && it.pieces.split(',').any { k -> k in done } }
            .forEach { dao.upsertApplied(it.copy(replacedAt = now)) }
        val id = dao.insertApplied(AppliedRecipeEntity(
            code = code, version = version, hash = RecipeCodec.hash(r), title = r.title, calendarId = cal, body = RecipeCodec.write(r),
            inputs = JSONObject(filled.mapValues { (_, v) -> v }).toString(), pieces = done.joinToString(","), before = before.toString(),
            appliedAt = now, undoneAt = null, replacedAt = null, keptSent = false,
        ))
        if (code.isNotEmpty() && c.recipeApi.enabled) runCatching { c.recipeApi.applied(code, kept = false) }
        return id
    }

    /** Whether [e] can still be taken back: on, and within [UNDO_DAYS] days. */
    fun canUndo(e: AppliedRecipeEntity, now: Long = Today.millis()): Boolean =
        e.undoneAt == null && e.replacedAt == null && now - e.appliedAt < UNDO_DAYS * DAY_MILLIS

    /** 되돌리기: every piece put back, last first; what the user changed since stays. */
    suspend fun undo(id: Long): Boolean {
        val e = dao.appliedById(id) ?: return false
        if (!canUndo(e)) return false
        val before = runCatching { JSONObject(e.before) }.getOrNull() ?: return false
        for (key in e.pieces.split(',').reversed()) {
            val kind = PieceKind.of(key) ?: continue
            val snap = before.optJSONObject(key) ?: continue
            runCatching { pieces.restore(kind, snap, e.calendarId) }
        }
        dao.upsertApplied(e.copy(undoneAt = Today.millis()))
        return true
    }

    /** [RCP-26] The 7일 유지 mark of each recipe still on after 7 days, sent once; old rows no longer on are dropped after 90 days. */
    suspend fun sendKept() {
        val now = Today.millis()
        for (e in dao.applied()) {
            if (e.keptSent || e.code.isEmpty() || e.undoneAt != null || now - e.appliedAt < UNDO_DAYS * DAY_MILLIS) continue
            if (!c.recipeApi.enabled) return
            if (runCatching { c.recipeApi.applied(e.code, kept = true) }.isSuccess) dao.upsertApplied(e.copy(keptSent = true))
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
        const val UNDO_DAYS = 7L
        const val KEEP_DAYS = 90L
        const val DAY_MILLIS = 24L * 60 * 60 * 1000
    }
}
