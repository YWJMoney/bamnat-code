package com.bamnat.app.company

import org.json.JSONArray
import org.json.JSONObject

/**
 * [PLZD-07] 1.30 묶음 BD: what one version says against another, as the company server says it (AX 1.30 `diffLines`, the one
 * place that says it: `changes` on a company page's folded versions, a room's others and a rule's history). The app shows it
 * as it comes and makes no diff of its own, so the phone and the developer app always say the same.
 *
 * [summary] is the one line ("가산·야간 60%→50% · 수당 1개 더함"); [changed] each fact that moved with its words marked;
 * [added] and [removed] whole facts. [against] is the version it was held against, when the server names it.
 */
data class SetupChanges(
    val summary: String,
    val changed: List<Fact> = emptyList(),
    val added: List<Pair<String, String>> = emptyList(),
    val removed: List<Pair<String, String>> = emptyList(),
    val against: String? = null,
) {
    /** One fact that moved: its [label] ("근무·주간"), the text before and after, and the words that stayed, went or came. */
    data class Fact(val label: String, val before: String, val after: String, val tokens: List<Token>)

    /** A run of words: [kind] same, del (only before) or add (only after). */
    data class Token(val text: String, val kind: String) {
        val added: Boolean get() = kind == "add"
        val deleted: Boolean get() = kind == "del"
    }

    val empty: Boolean get() = summary.isBlank() && changed.isEmpty() && added.isEmpty() && removed.isEmpty()

    companion object {
        /** The server's `changes` object; null when there is none, it says nothing, or it is a shape an older server sent. */
        fun of(o: JSONObject?): SetupChanges? {
            if (o == null) return null
            val facts = o.optJSONObject("facts")
            fun pairs(a: JSONArray?): List<Pair<String, String>> = if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
                a.optJSONArray(i)?.let { it.optString(0) to it.optString(1) }
            }
            val changed = facts?.optJSONArray("changed")?.let { a ->
                (0 until a.length()).mapNotNull { i ->
                    val f = a.optJSONObject(i) ?: return@mapNotNull null
                    val t = f.optJSONArray("tokens")
                    Fact(f.optString("label"), f.optString("before"), f.optString("after"),
                        if (t == null) emptyList() else (0 until t.length()).mapNotNull { j ->
                            t.optJSONObject(j)?.let { Token(it.optString("t"), it.optString("kind", "same")) }
                        })
                }
            }.orEmpty()
            val c = SetupChanges(
                summary = o.optString("summary"),
                changed = changed,
                // The fine facts when the server has them, else the whole lines (a server before 1.30).
                added = pairs(facts?.optJSONArray("added") ?: o.optJSONArray("added")),
                removed = pairs(facts?.optJSONArray("removed") ?: o.optJSONArray("removed")),
                against = o.optString("against").ifEmpty { null },
            )
            return c.takeIf { !it.empty }
        }
    }
}
