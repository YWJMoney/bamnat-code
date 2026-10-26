package com.bamnat.app.flags

import com.bamnat.core.flags.Condition
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.flags.FlagOverride
import com.bamnat.core.flags.Force
import com.bamnat.core.flags.PolicyVersion
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * [FLAG-06] `/api/config`'s `flags` and `policy` read like the rest of the answer (계약 2-1): strictly, so a wrong shape
 * throws and the last good answer stays, but tolerantly, so a newer server's rows and kinds of condition are skipped.
 *
 * - `flags: { "<key>": { "force": "on"|"off"|null, "why": "…", "conditions": [...]|null, "until": iso|null,
 *   "minApp": n|null, "maxApp": n|null, "confirmed": { "<manual key>": iso|null } | ["<manual key>", …] } }`
 * - a condition: `{"type": "date", "from": "2026-11-10"}` or `{"type": "date", "policy": "3.1"}`, `policy {version}`,
 *   `consent {key, unknownMeets?}`, `profile {key}`, `app {min?, max?}`, `workType {in: [...]}`, `rollout {percent}`,
 *   `manual {key}`, `admin`, `any {of: [...]}` (AL-D1).
 * - `policy: [{version, posted, effective, heldUntil}]` (KST days).
 */
object FlagJson {
    private val VERSION = Regex("^\\d{1,2}(\\.\\d{1,3}){0,2}$")
    private val KEY = Regex("^[a-z][a-z0-9]*(\\.[a-zA-Z0-9_]+){0,3}$")

    /** The rows this app knows, by key. Unknown keys are skipped; a known key with a wrong shape fails the answer. */
    fun overrides(o: JSONObject): Map<String, FlagOverride> {
        val out = LinkedHashMap<String, FlagOverride>()
        for (key in o.keys()) {
            if (!KEY.matches(key) || FlagCatalog.of(key) == null) continue
            val v = o.get(key)
            if (v !is JSONObject) throw JSONException("flags.$key")
            out[key] = override(v)
        }
        return out
    }

    private fun override(o: JSONObject): FlagOverride {
        val force = when (val f = if (o.isNull("force")) null else o.get("force")) {
            null -> null
            "on" -> Force.ON
            "off" -> Force.OFF
            else -> throw JSONException("force $f")
        }
        // AL-D1: one condition this app cannot read and the server's list is left out; the app's own conditions stay.
        val conditions = if (o.isNull("conditions")) null else o.getJSONArray("conditions").let { a ->
            runCatching { (0 until a.length()).map { condition(a.getJSONObject(it)) } }.getOrNull()
        }
        return FlagOverride(
            force = force,
            why = if (o.isNull("why")) null else o.getString("why").take(300),
            conditions = conditions,
            until = if (o.isNull("until")) null else Instant.parse(o.getString("until")),
            minApp = if (o.isNull("minApp")) null else o.getInt("minApp"),
            maxApp = if (o.isNull("maxApp")) null else o.getInt("maxApp"),
            confirmed = confirmed(if (o.isNull("confirmed")) null else o.get("confirmed")),
        )
    }

    private fun confirmed(v: Any?): Map<String, Instant?> = when (v) {
        null -> emptyMap()
        is JSONArray -> (0 until v.length()).associate { v.getString(it) to null }
        is JSONObject -> v.keys().asSequence().associateWith { k ->
            when (val at = v.get(k)) {
                JSONObject.NULL, true -> null
                is String -> Instant.parse(at)
                else -> throw JSONException("confirmed.$k")
            }
        }
        else -> throw JSONException("confirmed")
    }

    /** One condition; throws for a kind this app does not know (the caller drops the list). */
    fun condition(o: JSONObject): Condition = when (val type = o.getString("type")) {
        "date" -> if (o.has("policy") && !o.isNull("policy")) Condition.Date(policy = o.getString("policy").also { require(VERSION.matches(it)) })
            else Condition.Date(from = LocalDate.parse(o.getString("from")))
        "policy" -> Condition.Policy(o.getString("version").also { require(VERSION.matches(it)) })
        "consent" -> Condition.Consent(o.getString("key"), o.optBoolean("unknownMeets", false))
        "profile" -> Condition.Profile(o.getString("key"))
        "app" -> Condition.App(if (o.isNull("min")) null else o.optInt("min"), if (o.isNull("max")) null else o.optInt("max"))
        "workType" -> Condition.WorkType(o.getJSONArray("in").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() })
        "rollout" -> Condition.Rollout(o.getInt("percent").coerceIn(0, 100))
        "manual" -> Condition.Manual(o.getString("key"))
        "admin" -> Condition.Admin
        "any" -> Condition.AnyOf(o.getJSONArray("of").let { a -> (0 until a.length()).map { condition(a.getJSONObject(it)) } })
        else -> throw JSONException("condition $type")
    }

    fun policy(a: JSONArray): List<PolicyVersion> = (0 until a.length()).map { i ->
        val o = a.getJSONObject(i)
        val version = o.getString("version")
        if (!VERSION.matches(version)) throw JSONException("policy.version")
        val v = PolicyVersion(version, LocalDate.parse(o.getString("posted")), LocalDate.parse(o.getString("effective")),
            if (o.isNull("heldUntil") || !o.has("heldUntil")) null else LocalDate.parse(o.getString("heldUntil")))
        if (v.effective < v.posted || v.holdEnds < v.effective) throw JSONException("policy.dates")
        v
    }

    // ---- saving (the store keeps the last good answer as JSON) ----

    fun toJson(map: Map<String, FlagOverride>): JSONObject = JSONObject().also { root ->
        map.forEach { (k, v) ->
            root.put(k, JSONObject()
                .put("force", when (v.force) { Force.ON -> "on"; Force.OFF -> "off"; null -> JSONObject.NULL })
                .put("why", v.why ?: JSONObject.NULL)
                .put("conditions", v.conditions?.let { l -> JSONArray(l.map(::toJson)) } ?: JSONObject.NULL)
                .put("until", v.until?.toString() ?: JSONObject.NULL)
                .put("minApp", v.minApp ?: JSONObject.NULL).put("maxApp", v.maxApp ?: JSONObject.NULL)
                .put("confirmed", JSONObject().also { c -> v.confirmed.forEach { (mk, at) -> c.put(mk, at?.toString() ?: JSONObject.NULL) } }))
        }
    }

    fun toJson(c: Condition): JSONObject = when (c) {
        is Condition.Date -> JSONObject().put("type", "date").apply { if (c.policy != null) put("policy", c.policy) else put("from", c.from.toString()) }
        is Condition.Policy -> JSONObject().put("type", "policy").put("version", c.version)
        is Condition.Consent -> JSONObject().put("type", "consent").put("key", c.key).put("unknownMeets", c.unknownMeets)
        is Condition.Profile -> JSONObject().put("type", "profile").put("key", c.key)
        is Condition.App -> JSONObject().put("type", "app").put("min", c.min ?: JSONObject.NULL).put("max", c.max ?: JSONObject.NULL)
        is Condition.WorkType -> JSONObject().put("type", "workType").put("in", JSONArray(c.types.sorted()))
        is Condition.Rollout -> JSONObject().put("type", "rollout").put("percent", c.percent)
        is Condition.Manual -> JSONObject().put("type", "manual").put("key", c.key)
        Condition.Admin -> JSONObject().put("type", "admin")
        is Condition.AnyOf -> JSONObject().put("type", "any").put("of", JSONArray(c.any.map(::toJson)))
    }

    fun toJson(list: List<PolicyVersion>): JSONArray = JSONArray(list.map {
        JSONObject().put("version", it.version).put("posted", it.posted.toString()).put("effective", it.effective.toString())
            .put("heldUntil", it.heldUntil?.toString() ?: JSONObject.NULL)
    })
}
