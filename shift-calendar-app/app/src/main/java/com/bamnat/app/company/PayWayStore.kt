package com.bamnat.app.company

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/**
 * [PAYF-04] 1.29 묶음 AP: the company pay way the main pay uses ("이 급여 방식 쓰기", or a company setup put in with its pay), so
 * the pay screen can say whose it is and keep 맞아요·달라요 on it. Before 1.29 nothing remembered it: the rules came in and the
 * pay screen could not tell them from the user's own, and the server never counted the phone as using it.
 */
data class PayWay(
    val companyId: String,
    val company: String,
    val dept: String,
    /** The server's id of the setup the pay way came from. */
    val setup: String,
    /** true 맞아요, false 달라요, null not said yet; [why] where it differs (order·time·pay·other). */
    val vote: Boolean? = null,
    val why: String? = null,
)

/** [PAYF-04] Where [PayWay] is kept: its own small preferences file, backed up like the company setup in use. */
class PayWayStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _way = MutableStateFlow(read())
    val way: StateFlow<PayWay?> = _way.asStateFlow()

    /**
     * [PAYF-06] Set when a pay way just went onto a pay that already had amounts: the pay screen opens the 기본급 check once
     * (사용자 2026-10-27 "회사를 고르더라도 … 기본급 기반 동작"). In memory only: a restart asks it from the pay screen's card instead.
     */
    private val _askBase = MutableStateFlow(false)
    val askBase: StateFlow<Boolean> = _askBase.asStateFlow()

    /**
     * [PAYF-01] The user already said "이 급여 방식 쓰기": the amounts screen does not ask "이 회사 급여 설정을 쓸까요?" again
     * (1.27 asked it twice in a row). In memory, for the very next screen.
     */
    var decided: Boolean = false

    fun use(way: PayWay?) {
        _way.value = way
        prefs.edit().apply { if (way == null) remove(KEY) else putString(KEY, write(way)) }.apply()
    }

    fun voted(ok: Boolean, why: String?) {
        val w = _way.value ?: return
        use(w.copy(vote = ok, why = if (ok) null else why))
    }

    fun askBase(on: Boolean) { _askBase.value = on }

    /** A restored backup or a cleared calendar. */
    fun reload() { _way.value = read() }

    private fun read(): PayWay? = prefs.getString(KEY, null)?.let { s ->
        runCatching {
            val o = JSONObject(s)
            PayWay(o.getString("companyId"), o.getString("company"), o.optString("dept"), o.getString("setup"),
                if (o.has("vote")) o.getBoolean("vote") else null, o.optString("why").ifEmpty { null })
        }.getOrNull()
    }

    private fun write(w: PayWay): String = JSONObject().put("companyId", w.companyId).put("company", w.company).put("dept", w.dept)
        .put("setup", w.setup).apply { w.vote?.let { put("vote", it) }; w.why?.let { put("why", it) } }.toString()

    companion object {
        const val FILE = "pay_way"
        const val KEY = "way"
    }
}
