package com.bamnat.app.recipe

import com.bamnat.app.AppContainer
import com.bamnat.core.money.MoneySource
import com.bamnat.core.recipe.BuiltInThemes
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.Recipe
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.time.Today
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [TRY-06] 1.39 기능까지 써 보기 (사용자 2026-11-07 "무료 사용자도 입혀보기를 통해 얼마나 좋은지 명확히 체감", 조사 18장 C-1).
 *
 * A work's 기능 (규칙·단추, 메모 틀, 알람, 가계부 분류, 급여·현장·가게·투자 설정, 근무 꾸미기) put on the real app for a while and taken
 * back whole: each piece goes on the way 넣기 puts it ([RecipePieces.apply], so it works exactly as it will), its 되돌리기 snapshot kept
 * in [file] first, and [finish] puts every piece back, takes back what its rules and buttons wrote, and the records written while
 * trying it ([made]). The file outlives the app: one stopped while trying is put back at the next start ([recover]).
 *
 * Nothing of it is a 넣기: no record of a put-on, no 쓰는 분 counted, no 입은 테마. While it lasts the auto backup and the share links
 * wait ([busy]), so a work only being tried never leaves the phone.
 */
class FunctionTrial(private val c: AppContainer) {
    /** What the user wrote while trying (메모, 가계부 lines typed on the calendar tried on). */
    data class Made(val memos: Int, val money: Int) {
        val total: Int get() = memos + money
        fun say(): String = listOfNotNull(memos.takeIf { it > 0 }?.let { "메모 ${it}개" }, money.takeIf { it > 0 }?.let { "가계부 ${it}건" }).joinToString("·")
    }

    /** How a trial ended: what the rules and buttons did (taken back), and what was written ([kept] or taken back). */
    data class Ended(val code: String, val title: String, val ran: Int, val made: Made, val kept: Boolean)

    /**
     * Puts [kinds] of [r] on calendar [cal] for a trial, each with its [modes] (as 넣기 would), and switches on its rules that write
     * (one received comes in off, [com.bamnat.app.logic.LogicStore.gotOff]) so they can be felt. The kinds that changed something.
     */
    suspend fun start(r: Recipe, code: String, cal: String, kinds: Set<PieceKind>, modes: Map<PieceKind, RecipePieces.Mode>,
                      answers: Map<String, Any?>): List<PieceKind> = lock.withLock {
        // One at a time: an earlier one (an app stopped while trying) goes first.
        finishNow(keepMine = true)
        val wanted = r.pieces.filter { it.kind in kinds && it.kind in KINDS }
        if (wanted.isEmpty()) return@withLock emptyList()
        val at = Today.millis()
        val journal = JSONObject().put("at", at).put("cal", cal).put("code", code).put("title", r.title)
            .put("moneyMax", runCatching { c.scopeOf(cal).money.allEntries().maxOfOrNull { it.id } ?: 0L }.getOrDefault(0L))
            .put("order", JSONArray()).put("before", JSONObject()).put("on", JSONArray())
        write(journal)
        val filled = RecipeResolve.answers(r, answers)
        val done = ArrayList<PieceKind>()
        val trialCode = code.ifEmpty { "t" + at.toString(36) }
        for (piece in wanted) {
            val values = RecipeResolve.piece(r, piece, filled)
            val snap = runCatching { c.recipes.pieces.apply(piece.kind, values, cal, trialCode, modes[piece.kind] ?: RecipePieces.Mode.REPLACE, trial = true) }
                .getOrNull() ?: continue
            journal.getJSONArray("order").put(piece.kind.key)
            journal.getJSONObject("before").put(piece.kind.key, snap)
            done += piece.kind
            write(journal)
        }
        if (PieceKind.LOGIC in done) {
            val store = com.bamnat.app.logic.LogicStore(c.appContext)
            val keys = r.pieces.filter { it.kind == PieceKind.LOGIC }.flatMap { com.bamnat.core.formula.Logic.from(it.values).rules }.map { it.key }.filter { store.cameOff(it) }
            keys.forEach { store.setOff(it, false); journal.getJSONArray("on").put(it) }
            if (keys.isNotEmpty()) { write(journal); com.bamnat.app.logic.LogicRunner.rulesChanged(c) }
        }
        if (done.isEmpty()) drop()
        done
    }

    /** What the user wrote since the trial began ([Made.total] 0 with none on). */
    suspend fun made(): Made = lock.withLock { read()?.let { madeBy(it) } ?: Made(0, 0) }

    /**
     * Ends the trial: what its rules and buttons wrote taken back, what the user wrote too unless [keepMine], and every piece put back,
     * the last first. Null when none was on.
     */
    suspend fun finish(keepMine: Boolean): Ended? = lock.withLock { finishNow(keepMine) }

    /** At the app's start: a trial the app stopped in the middle of is put back (what the user wrote stays). */
    suspend fun recover(): Ended? = if (!busy(c.appContext)) null else finish(keepMine = true)

    /** 2.0.0 [RVM-46] The names of the rules and buttons the trial added (not one of mine from before), or null for every write. */
    private fun triedNames(j: JSONObject): ((String) -> Boolean)? {
        val pair = j.optJSONObject("before")?.optJSONObject(PieceKind.LOGIC.key)?.optJSONObject("settings")?.optJSONArray("logic") ?: return { false }
        val before = com.bamnat.core.formula.Logic.decode(pair.optString(0))
        val after = com.bamnat.core.formula.Logic.decode(pair.optString(1))
        val mine = before.buttons.map { it.label }.toSet() + before.rules.map { it.name }
        val tried = (after.buttons.map { it.label } + after.rules.map { it.name }).toSet() - mine
        return { it in tried }
    }

    private suspend fun finishNow(keepMine: Boolean): Ended? {
        val j = read() ?: return null
        val at = j.optLong("at")
        val cal = j.optString("cal")
        // 2.0.0 [RVM-46] 검토 RCP-20: what the tried work's own rules and buttons wrote goes; what my own rules wrote meanwhile stays
        // (a name I already had before the trial is mine, so its writes stay too).
        val ran = runCatching { com.bamnat.app.logic.LogicRunner.takeBackSince(c, at, triedNames(j)) }.getOrDefault(0)
        val made = runCatching { madeBy(j) }.getOrDefault(Made(0, 0))
        if (!keepMine) runCatching {
            // Gone, not to the 휴지통: they were only ever a try.
            mineMemos(at).forEach { c.memoRepository.delete(it, offerUndo = false) }
            val money = c.scopeOf(cal).money
            mineMoney(j).forEach { money.delete(it) }
        }
        val before = j.optJSONObject("before") ?: JSONObject()
        val order = j.optJSONArray("order") ?: JSONArray()
        for (i in order.length() - 1 downTo 0) {
            val kind = PieceKind.of(order.optString(i)) ?: continue
            val snap = before.optJSONObject(kind.key) ?: continue
            runCatching { c.recipes.pieces.restore(kind, snap, cal) }
        }
        val on = j.optJSONArray("on") ?: JSONArray()
        if (on.length() > 0) {
            val store = com.bamnat.app.logic.LogicStore(c.appContext)
            for (i in 0 until on.length()) store.gotOff(on.optString(i))
        }
        if (order.length() > 0) runCatching { com.bamnat.app.logic.LogicRunner.rulesChanged(c) }
        drop()
        return Ended(j.optString("code"), j.optString("title"), ran, made, keepMine)
    }

    private suspend fun madeBy(j: JSONObject): Made = Made(mineMemos(j.optLong("at")).size, mineMoney(j).size)

    /** What the rules and buttons wrote since [at] ("memo"/"money" ids): theirs, not the user's. */
    private fun ruleMade(at: Long, key: String): Set<Long> = com.bamnat.app.logic.LogicStore(c.appContext).written()
        .filter { it.optLong("at", -1L) >= at && it.has(key) }.map { it.optLong(key) }.toSet()

    /** Memos the user wrote since [at] and still here. */
    private suspend fun mineMemos(at: Long): List<com.bamnat.core.memo.Memo> {
        val theirs = ruleMade(at, "memo")
        return c.memoRepository.memos.first().filter { it.createdAtMillis >= at && !it.inTrash && it.id !in theirs }
    }

    /** 가계부 lines the user typed on the calendar tried on since it began (one read from a payment notice is real, and stays). */
    private suspend fun mineMoney(j: JSONObject): List<com.bamnat.core.money.MoneyEntry> {
        val theirs = ruleMade(j.optLong("at"), "money")
        return c.scopeOf(j.optString("cal")).money.allEntries()
            .filter { it.id > j.optLong("moneyMax") && it.source == MoneySource.MANUAL && it.id !in theirs }
    }

    private suspend fun write(j: JSONObject) = withContext(Dispatchers.IO) {
        val f = file(c.appContext)
        val part = File(f.parentFile, f.name + ".part")
        part.writeText(j.toString())
        if (!part.renameTo(f)) { f.writeText(j.toString()); part.delete() }
    }

    private suspend fun read(): JSONObject? = withContext(Dispatchers.IO) {
        file(c.appContext).takeIf { it.exists() }?.let { runCatching { JSONObject(it.readText()) }.getOrNull() }
    }

    private suspend fun drop() = withContext(Dispatchers.IO) { file(c.appContext).delete() }

    companion object {
        /**
         * The pieces a trial puts on: all but the looks (those are laid over the screens in memory, [com.bamnat.app.ui.recipe.LiveTryOn]),
         * 회사 (it joins a company on the server) and 묶음 (other recipes).
         */
        val KINDS: Set<PieceKind> = PieceKind.entries.toSet() - BuiltInThemes.LOOK_KINDS - PieceKind.COMPANY - PieceKind.BUNDLE

        private val lock = Mutex()

        fun file(ctx: android.content.Context) = File(ctx.noBackupFilesDir, "function_trial.json")

        /** The tab a trial of [kinds] of [r] opens on: where they are first seen (a button's place, 메모 틀 in 메모, 가계부 틀 in 가계부, 급여 in 통계). */
        fun tab(r: Recipe, kinds: Set<PieceKind>): String {
            val buttons = r.pieces.filter { it.kind == PieceKind.LOGIC }.flatMap { com.bamnat.core.formula.Logic.from(it.values).buttons }
            return when {
                PieceKind.LOGIC in kinds && buttons.isNotEmpty() -> when (buttons.first().place) {
                    com.bamnat.core.formula.ButtonPlace.MONEY -> "money"
                    com.bamnat.core.formula.ButtonPlace.MEMO -> "memos"
                    else -> "calendar"
                }
                PieceKind.MEMO_TEMPLATES in kinds -> "memos"
                PieceKind.LEDGER in kinds || PieceKind.SHOP in kinds || PieceKind.INVEST in kinds -> "money"
                PieceKind.HOURLY_RULES in kinds || PieceKind.FIELD_SHEET in kinds -> "stats"
                else -> "calendar"
            }
        }

        /** Whether a work's 기능 are on for a trial now (or were when the app stopped). */
        fun busy(ctx: android.content.Context): Boolean = file(ctx).exists()
    }
}
