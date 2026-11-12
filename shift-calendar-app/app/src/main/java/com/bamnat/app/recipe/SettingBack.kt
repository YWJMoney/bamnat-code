package com.bamnat.app.recipe

import com.bamnat.core.formula.Logic

/**
 * 2.0.0 [RVM-45] 검토 RCP-02 (조건 10 '그것이 바꾼 것만 되돌려요'): one setting taken back from what a work left ([after]) to what was
 * there before ([before]), when it is [now]. What the user made or changed since stays, and [kept] counts those places.
 *  - 규칙·단추 (`logic`): only the buttons, rules and 값 칸 the work added go, each as it came; one the user changed stays.
 *  - A look kept as `key=value;key=value` (달력 모양·위젯·사진·달력 색 …): key by key.
 *  - Anything else (an order, a switch): as a whole, back only when it is still as the work left it.
 */
internal object SettingBack {
    data class Back(val text: String, val kept: Int)

    fun of(key: String, now: String, before: String, after: String): Back = when {
        now == after -> Back(before, 0)
        key == "logic" -> logic(now, before, after)
        pairs(now) != null && pairs(before) != null && pairs(after) != null -> pairs(now, before, after)
        else -> Back(now, if (after != before) 1 else 0)
    }

    private fun logic(now: String, before: String, after: String): Back {
        val n = Logic.decode(now); val b = Logic.decode(before); val a = Logic.decode(after)
        val addedButtons = a.buttons.filter { x -> x !in b.buttons }
        val addedRules = a.rules.filter { x -> b.rules.none { it.key == x.key } }
        val addedValues = a.values.filter { x -> b.values.none { it.name == x.name } }
        var kept = 0
        // A rule's key is what it does: one the user changed since is another rule, theirs.
        val rules = n.rules.filter { r -> addedRules.none { it.key == r.key } }
        val buttons = n.buttons.filter { it !in addedButtons }
        // A button the work added that the user changed since (its name or what it does) is theirs now.
        kept += addedButtons.count { x -> x !in n.buttons && n.buttons.any { it.id == x.id } }
        val values = n.values.filter { v -> addedValues.none { it.name == v.name } }
        return Back(Logic.encode(Logic(buttons, rules, values)), kept)
    }

    /** `key=value` pieces split by ';' (an empty text is no pieces), or null when the text is not of that shape. */
    private fun pairs(s: String): Map<String, String>? {
        if (s.isEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (t in s.split(';')) {
            val i = t.indexOf('=')
            if (i <= 0 || t.substring(0, i) in out) return null
            out[t.substring(0, i)] = t.substring(i + 1)
        }
        return out
    }

    private fun pairs(now: String, before: String, after: String): Back {
        val n = pairs(now)!!; val b = pairs(before)!!; val a = pairs(after)!!
        val out = LinkedHashMap<String, String>()
        var kept = 0
        // In the order the app writes them: before's, then what came since.
        for (k in (b.keys + a.keys + n.keys).distinct()) {
            val v = if (n[k] == a[k]) b[k] else n[k].also { if (a[k] != b[k]) kept++ }
            if (v != null) out[k] = v
        }
        return Back(out.entries.joinToString(";") { "${it.key}=${it.value}" }, kept)
    }
}
