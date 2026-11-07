package com.bamnat.app.logic

import android.content.Context
import com.bamnat.app.AppContainer
import com.bamnat.app.tier.Feature
import com.bamnat.core.formula.FxPro
import com.bamnat.core.formula.FxTaste
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.LogicVerb
import com.bamnat.core.formula.ValueBook
import com.bamnat.core.time.Today
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.time.Instant

/**
 * 1.39 [FX-01][FX-02] 프로 문 on the phone (core [FxPro] says what is a 기능 장치): 1.39's 기능 장치 run with 프로 ([Feature.FX_WORKS]) or
 * during a work's 하루 써 보기 ([FxTaste]); otherwise only [FxPro.free] runs. Making, previewing and 입혀 보기 are always free — the
 * 스튜디오 and the 장터 show the whole work; only running it is held here, in one place, so no screen decides by itself.
 *
 * For the 장터 (장터 스레드): [status] (쓸 수 있음 · 쓰는 중 몇 시까지 · 다 씀), [start] (하루 써 보기 시작), [pro], [on]; the card's line is
 * `FxPro.say(FxPro.Part.of…)` over the list item's `fx`.
 */
object FxAccess {
    private const val FILE = "fx_taste"
    private const val TRIED = "tried"
    private val _taste = MutableStateFlow<FxTaste.State?>(null)

    private fun load(ctx: Context): FxTaste.State = _taste.value ?: FxTaste.decode(
        ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(TRIED, null),
    ).also { _taste.value = it }

    /** Each work's try, kept up to date for screens. */
    fun taste(c: AppContainer): StateFlow<FxTaste.State?> { load(c.appContext); return _taste.asStateFlow() }

    /** This phone has 프로 for 기능 작품 (before the 정식 출시 switch every phone has). */
    fun pro(c: AppContainer): Boolean = c.featureGate.state.value.allows(Feature.FX_WORKS)

    /**
     * 기능 장치 run now: 프로, a 하루 써 보기 running, or a work being tried on with its 기능 ([TRY-06] 기능까지 써 보기, free like every
     * 입혀 보기; it is all taken back when it ends).
     */
    fun on(c: AppContainer, now: Instant = Today.instant()): Boolean =
        pro(c) || load(c.appContext).open(now) || com.bamnat.app.recipe.FunctionTrial.busy(c.appContext)

    /** [key] (a 장터 code, or [FxTaste.OWN]): never tried, running until when, or used. */
    fun status(c: AppContainer, key: String, now: Instant = Today.instant()): FxTaste.Status = FxTaste.status(load(c.appContext), key, now)

    /** Begins [key]'s 하루 써 보기; false when it was tried before. */
    fun start(c: AppContainer, key: String, now: Instant = Today.instant()): Boolean {
        val next = FxTaste.start(load(c.appContext), key, now) ?: return false
        c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
            .putString(TRIED, FxTaste.encode(next)).remove(SUM).remove(SAID).apply()
        _taste.value = next
        FxOutside.sync(c)
        LogicRunner.rulesChanged(c)
        return true
    }

    /** The 프로 기능 장치 a whole work carries in its 규칙·단추 piece (none: free to put in and to send up). */
    fun partsOf(r: com.bamnat.core.recipe.Recipe): Set<FxPro.Part> =
        (r.pieces.filter { it.kind == com.bamnat.core.recipe.PieceKind.LOGIC }.flatMap { FxPro.uses(Logic.from(it.values)) } +
            FxPro.looks(r.pieces.map { it.kind.key })).let { s -> FxPro.Part.entries.filter { it in s }.toSet() }

    /** What runs of [full] now. */
    fun runnable(c: AppContainer, full: Logic): Logic = if (on(c)) full else FxPro.free(full)

    // ---- [FX-02] C-3 하루 써 본 뒤 요약 ----

    private const val SUM = "sum"
    private const val SAID = "said"

    /** A 기능 장치 acted during a try (not with 프로): counted for the end card. */
    internal fun note(c: AppContainer, verb: LogicVerb) {
        if (pro(c) || !load(c.appContext).open(Today.instant())) return
        val p = c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val o = runCatching { JSONObject(p.getString(SUM, "{}") ?: "{}") }.getOrDefault(JSONObject())
        o.put(verb.key, o.optInt(verb.key) + 1)
        p.edit().putString(SUM, o.toString()).apply()
    }

    /** "값 더하기 7번 · 알림 2번" of the last try, or null when nothing acted. */
    fun summary(c: AppContainer): String? {
        val o = runCatching { JSONObject(c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(SUM, "{}") ?: "{}") }.getOrNull() ?: return null
        val parts = LogicVerb.entries.mapNotNull { v -> o.optInt(v.key).takeIf { it > 0 }?.let { "${v.label} ${it}번" } }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /**
     * The try ended and its card was not said yet: once, a quiet notice "하루 써 보기가 끝났어요 · 오늘 이 작품이 한 일 … · 프로에서 계속"
     * that opens the 프로 screen. Asked when the app opens.
     */
    fun endedCard(c: AppContainer, now: Instant = Today.instant()): String? {
        if (pro(c)) return null
        val s = load(c.appContext)
        if (s.tried.isEmpty() || s.open(now)) return null
        val p = c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        val last = s.tried.values.max().toEpochMilli().toString()
        if (p.getString(SAID, null) == last) return null
        p.edit().putString(SAID, last).apply()
        val did = summary(c)
        return if (did != null) "하루 써 보기가 끝났어요. 그동안 한 일: $did. 프로에서는 계속 돌아요" else "하루 써 보기가 끝났어요. 프로에서는 기능 장치가 계속 돌아요"
    }
}

/**
 * 1.39 [FX-03] 값 칸 numbers on this phone (file `fx_values`, in the backup). The definitions (reset, start, goal) come from the
 * logic piece as kept ([Logic.values]); only the numbers and their period are here.
 */
object FxValues {
    private const val FILE = "fx_values"
    private const val ALL = "all"
    @Volatile private var app: () -> AppContainer? = { null }
    private val _all = MutableStateFlow<Map<String, ValueBook.Kept>?>(null)

    fun attach(c: () -> AppContainer?) { app = c }

    private fun load(ctx: Context): Map<String, ValueBook.Kept> = _all.value
        ?: ValueBook.decode(ctx.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ALL, null)).also { _all.value = it }

    /** Changes to any number, for widgets and screens. */
    fun flow(c: AppContainer): StateFlow<Map<String, ValueBook.Kept>?> { load(c.appContext); return _all.asStateFlow() }

    /** [name]'s number today (its start in a new period), or null when no work has a 값 칸 of that name. */
    fun now(name: String): Double? {
        val c = app() ?: return null
        val def = LogicRunner.logic(c, all = true).value(name) ?: return null
        return ValueBook.now(load(c.appContext)[name], def, Today.date())
    }

    /** 1.39 [FX-13] [name] with its past, for 값() on each day and 지난값·연속·값합계·지난시간; null when no work has it. */
    fun series(name: String): com.bamnat.core.formula.ValueSeries? {
        val c = app() ?: return null
        val def = LogicRunner.logic(c, all = true).value(name) ?: return null
        return com.bamnat.core.formula.ValueSeries(def, load(c.appContext)[name], Today.date(), Today.dateTime())
    }

    /** "6/8잔" for a button that counts [name] ("18:30" a 시각, the words of a 글 값 칸), or null. */
    fun show(c: AppContainer, name: String): String? {
        val def = LogicRunner.logic(c, all = true).value(name) ?: return null
        return def.show(load(c.appContext)[name], Today.date())
    }

    /** The last [n] periods of [name], oldest first (missing ones its start), for the widget's small bars. */
    fun recent(c: AppContainer, name: String, n: Int): List<Double> {
        val def = LogicRunner.logic(c, all = true).value(name) ?: return emptyList()
        val k = load(c.appContext)[name]
        return (n - 1 downTo 0).map { ValueBook.back(k, def, Today.date(), it) ?: def.start }
    }

    /**
     * Adds [by] (or sets to [to], or to now's minutes when [stamp], or [text] for a 글 값 칸) and keeps it; the number after, or null when
     * there is no such 값 칸.
     */
    internal fun change(c: AppContainer, name: String, by: Double? = null, to: Double? = null, stamp: Boolean = false, text: String? = null): Double? {
        val def = LogicRunner.logic(c, all = true).value(name) ?: return null
        val all = load(c.appContext)
        val today = Today.date()
        val kept = when {
            stamp -> ValueBook.set(def, today, ValueBook.minutes(Today.dateTime()), all[name])
            text != null -> ValueBook.set(def, today, ValueBook.now(all[name], def, today), all[name], text.take(com.bamnat.core.formula.LogicAct.MAX_TEXT))
            to != null -> ValueBook.set(def, today, to, all[name])
            else -> ValueBook.add(all[name], def, today, by ?: 1.0)
        }
        val next = all + (name to kept)
        c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ALL, ValueBook.encode(next)).apply()
        _all.value = next
        return kept.v
    }

    /** Puts back [name]'s number as it was (되돌리기). */
    internal fun restore(c: AppContainer, name: String, v: Double, period: String, t: String? = null) {
        val all = load(c.appContext)
        val next = all + (name to ValueBook.Kept(v, period, t, all[name]?.past.orEmpty()))
        c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ALL, ValueBook.encode(next)).apply()
        _all.value = next
    }

    internal fun kept(c: AppContainer, name: String): ValueBook.Kept? = load(c.appContext)[name]

    /** Forgets every number (설정에서 '값 칸 비우기'). */
    fun clear(c: AppContainer) {
        c.appContext.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().remove(ALL).apply()
        _all.value = emptyMap()
    }
}
