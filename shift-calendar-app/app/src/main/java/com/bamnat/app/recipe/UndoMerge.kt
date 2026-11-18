package com.bamnat.app.recipe

import org.json.JSONArray
import org.json.JSONObject

/**
 * 2.0.0 [RVM-44] 검토 RCP-03·T09b-05 (조건 10 '그것이 바꾼 것만 되돌려요'): taking back a change from [b] to [a] when the thing is
 * now [now]. Each part the user has not touched since (still as [a]) goes back to [b]; a part changed since stays as it is now.
 * Objects go key by key, and lists of objects with an "id" (급여 수당) item by item, so one allowance typed after keeps only itself.
 */
internal object UndoMerge {
    fun back(now: JSONObject, b: JSONObject, a: JSONObject): JSONObject {
        val out = JSONObject(now.toString())
        val keys = LinkedHashSet<String>().apply { now.keys().forEach(::add); b.keys().forEach(::add); a.keys().forEach(::add) }
        for (k in keys) {
            val n = now.opt(k); val bv = b.opt(k); val av = a.opt(k)
            when {
                same(n, av) -> if (bv == null) out.remove(k) else out.put(k, bv)
                n is JSONObject && bv is JSONObject && av is JSONObject -> out.put(k, back(n, bv, av))
                n is JSONArray && bv is JSONArray && av is JSONArray && byId(n) && byId(bv) && byId(av) -> out.put(k, backList(n, bv, av))
                else -> Unit  // changed since: the user's
            }
        }
        return out
    }

    private fun backList(now: JSONArray, b: JSONArray, a: JSONArray): JSONArray {
        val n = ids(now); val bb = ids(b); val aa = ids(a)
        val out = JSONArray()
        for ((id, item) in n) {
            val av = aa[id]; val bv = bb[id]
            when {
                av == null -> out.put(item)  // made after: the user's
                same(item, av) -> if (bv != null) out.put(bv)
                bv != null -> out.put(back(item, bv, av))
                else -> out.put(item)  // the change's own, but changed since: kept
            }
        }
        // What the change took away and the user has not put back.
        for ((id, item) in bb) if (id !in n && id !in aa) out.put(item)
        return out
    }

    private fun byId(a: JSONArray) = (0 until a.length()).all { (a.opt(it) as? JSONObject)?.has("id") == true }
    private fun ids(a: JSONArray) = LinkedHashMap<String, JSONObject>().apply {
        for (i in 0 until a.length()) a.getJSONObject(i).let { put(it.get("id").toString(), it) }
    }

    /** The same value, keys in any order (a missing key and JSON null are the same). */
    fun same(x: Any?, y: Any?): Boolean = canon(x) == canon(y)

    private fun canon(v: Any?): String = when (v) {
        null, JSONObject.NULL -> "null"
        is JSONObject -> v.keys().asSequence().sorted().joinToString(",", "{", "}") { k -> JSONObject.quote(k) + ":" + canon(v.opt(k)) }
        is JSONArray -> (0 until v.length()).joinToString(",", "[", "]") { canon(v.opt(it)) }
        is String -> JSONObject.quote(v)
        is Number -> v.toDouble().let { d -> if (d == Math.floor(d) && !d.isInfinite()) d.toLong().toString() else d.toString() }
        else -> v.toString()
    }
}
