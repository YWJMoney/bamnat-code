package com.bamnat.app.company

import com.bamnat.app.AppContainer
import org.json.JSONArray
import org.json.JSONObject

/**
 * 2.0.0 [RVN-13] 검토 COMP-48: 쓰고 있어요(쓰기 시작·그만), 맞아요 and 회사 빼기 that could not reach the company server are kept on the
 * phone and sent once online by [OutboxJob]. Before, each was tried once and forgotten while the phone went on as if it was done, so
 * the 광장's "n명 쓰고 있어요" and 맞아요 never had it and a calendar that left a company stayed counted in it. For each company and
 * setup the last word wins (쓰기 then 그만 sends 그만 only). A refusal (the version is gone, …) is not kept: it would be refused again.
 */
object SmallSends {
    data class Call(
        /** "use", "vote" or "leave". */
        val kind: String,
        val company: String,
        val setup: String = "",
        /** use: in use (true) or not; vote: 맞아요 (true) or 달라요. */
        val on: Boolean = true,
        val verified: Boolean = false,
        /** [CALI-02] The calendar on the server ("" for one that names none). */
        val cal: String = "",
        /** vote: 달라요's where (wrong, old, name, pay…), "" for none. */
        val why: String = "",
    ) {
        /** One waiting call per thing: a newer word for it takes the older's place. */
        val key: String get() = "$kind|$company|$setup|$cal"

        fun json(): JSONObject = JSONObject().put("kind", kind).put("company", company).put("setup", setup).put("on", on)
            .put("verified", verified).put("cal", cal).apply { if (why.isNotEmpty()) put("why", why) }

        companion object {
            fun of(o: JSONObject): Call? = runCatching {
                Call(o.getString("kind"), o.getString("company"), o.optString("setup"), o.optBoolean("on", true), o.optBoolean("verified"),
                    o.optString("cal"), o.optString("why"))
            }.getOrNull()?.takeIf { it.kind in KINDS && it.company.isNotEmpty() }
        }
    }

    private val KINDS = setOf("use", "vote", "leave")

    fun use(company: String, setup: String, on: Boolean, cal: String) = Call("use", company, setup, on, cal = cal)
    fun vote(company: String, setup: String, ok: Boolean, verified: Boolean = false, why: String? = null) =
        Call("vote", company, setup, ok, verified, why = if (ok) "" else why.orEmpty())

    /**
     * 2.0.0 [RVN-16] 검토 U10b-03: 맞아요·달라요를 눌렀는데 서버에 닿지 못했을 때 아래 알림 줄의 한 줄. 누른 것은 휴대폰에 남아 연결되면 저절로 가요.
     */
    fun waitsLine(ok: Boolean) = "인터넷이 연결되면 ‘${if (ok) "맞아요" else "달라요"}’를 보낼게요"

    /** Whether [e] is the kind of failure a kept word gets over (no connection, the server busy), not a refusal. */
    fun waits(e: Throwable): Boolean = when (e) {
        is CompanyException -> e.retryable
        is java.io.IOException -> true
        else -> false
    }

    /** [call] could not go just now ([waits]): kept for [OutboxJob], the last word for the same thing winning. */
    fun later(c: AppContainer, store: CompanyStore, call: Call) = keep(c, store, call)
    fun leave(company: String) = Call("leave", company)

    /** [call] now; kept in [store] for [OutboxJob] when the server could not be reached. True when it went. */
    suspend fun send(c: AppContainer, store: CompanyStore, call: Call): Boolean {
        if (!c.companyApi.enabled) return false
        return try {
            run(c, store.device, call)
            store.dropCall(call.key)
            true
        } catch (e: CompanyException) {
            if (e.retryable) keep(c, store, call) else store.dropCall(call.key)
            false
        } catch (e: java.io.IOException) {
            keep(c, store, call)
            false
        } catch (e: kotlinx.coroutines.CancellationException) {
            // The screen went before the answer: the word waits, as with no connection.
            keep(c, store, call)
            throw e
        } catch (e: Exception) {
            // As before, nothing else stops what the user did (an answer the phone could not read went to the server all the same).
            store.dropCall(call.key)
            false
        }
    }

    /** What [store] keeps, oldest first; true when nothing is left. */
    suspend fun flush(c: AppContainer, store: CompanyStore): Boolean {
        if (!c.companyApi.enabled) return true
        for (call in store.keptCalls) {
            try {
                run(c, store.device, call)
            } catch (e: CompanyException) {
                if (e.retryable) return false
            } catch (e: java.io.IOException) {
                return false
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                // Read wrong, not refused: it is not tried again for ever.
            }
            store.dropCall(call.key)
        }
        return true
    }

    private fun keep(c: AppContainer, store: CompanyStore, call: Call) {
        store.keepCall(call)
        OutboxJob.schedule(c.appContext)
    }

    private suspend fun run(c: AppContainer, device: String, call: Call) {
        when (call.kind) {
            "use" -> c.companyApi.use(device, call.company, call.setup, call.on, call.cal)
            "vote" -> c.companyApi.vote(device, call.company, call.setup, ok = call.on, verified = call.verified, why = call.why.ifEmpty { null })
            "leave" -> c.companyApi.leave(device, call.company)
        }
    }

    internal fun read(text: String?): List<Call> = runCatching {
        val a = JSONArray(text ?: return emptyList())
        (0 until a.length()).mapNotNull { Call.of(a.getJSONObject(it)) }
    }.getOrDefault(emptyList())

    internal fun write(calls: List<Call>): String = JSONArray().apply { calls.forEach { put(it.json()) } }.toString()
}
