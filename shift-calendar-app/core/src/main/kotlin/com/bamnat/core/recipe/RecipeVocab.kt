package com.bamnat.core.recipe

/**
 * 2.0.0 [RVS-04] 판 호환 자동표 (검토 SD-03·SN-07·I09ba-04·RCP-07, 스튜디오 조사 보고서 5장 S3): every name this app's [RecipeCodec] knows,
 * so the server can tell which phones can open a work. An older app refuses a whole work on a part, state, 꾸밈 날, font, motion, fill or
 * choice it does not know, and a field it does not know is dropped on reading, which then fails the hash ('오는 길에 바뀌었어요').
 *
 * Each released app's names are kept as they were (`docs/recipes/vocab/<versionCode>.json`, taken from that release's own core), and
 * [since] finds for each name the first app that knows it. `PartsSinceExportTest` writes the server's table
 * (`server/company-worker/src/parts-since.js`) from it, so nobody writes a version by hand and nothing is left out (1.38·1.39's
 * memo.page*·night.* were). This file only reads [PieceKind], [Field], [BlueprintParts], [Motion], [Fill], [FontCatalog] and [Decor] in ways
 * the 1.36.0 core already had, so the same file, dropped into a released core, takes that release's names (tools/vocab_snapshot.py).
 */
object RecipeVocab {
    /** The categories, in the order the server's table writes them. */
    val KINDS = listOf("ids", "states", "decor", "fonts", "motions", "fills", "pieces", "fields", "picks")

    /**
     * The days a 꾸밈 항목 may stand on, besides the d·w·m day numbers every app reads: [Decor] keeps them as a pattern, so each is tried.
     * A new one goes here too (the export test fails while a part state [Decor] reads is missing).
     */
    val DECOR_TRIES = listOf(
        "today", "sat", "sun", "holiday", "dim", "in", "out", "sel", "dark", "late", "done",
        "role.day", "role.eve", "role.night", "role.long", "role.off", "role.other", "leave", "extra", "cover", "edited",
    )

    /** The fill kinds [Fill.GRAMMAR] may start with. */
    private val FILL_KINDS = listOf("so", "lg", "rg", "sg", "pt", "tx", "gl", "sh")

    /** What this app knows, by category: each a sorted set of names. */
    fun now(): Map<String, Set<String>> {
        val out = KINDS.associateWith { sortedSetOf<String>() }
        out.getValue("ids") += BlueprintParts.IDS
        out.getValue("states") += BlueprintParts.STATES
        out.getValue("decor") += DECOR_TRIES.filter { Decor.onProblem(it) == null }
        out.getValue("fonts") += FontCatalog.ALL.map { it.id }
        out.getValue("motions") += BlueprintParts.MOTIONS
        out.getValue("motions") += Motion.TURNS.map { "tn:$it" } + Motion.TODAYS.map { "td:$it" } + Motion.WEARS.map { "wr:$it" } +
            Motion.SPEEDS.map { "sp:$it" } + "of:1"
        out.getValue("fills") += FILL_KINDS.filter { Fill.GRAMMAR.pattern.contains("$it\\.") }
        out.getValue("fills") += Fill.PATTERNS.map { "pt.$it" } + Fill.TEXTURES.map { "tx.$it" } + Fill.SHADERS.map { "sh.$it" }
        for (kind in PieceKind.values()) {
            out.getValue("pieces") += kind.key
            walk(kind.key, kind.fields, out)
        }
        return out
    }

    private fun walk(at: String, fields: List<Field>, out: Map<String, MutableSet<String>>) {
        for (f in fields) {
            val path = "$at.${f.key}"
            out.getValue("fields") += path
            when (f) {
                is Field.Pick -> f.values.forEach { out.getValue("picks") += "$path=$it" }
                is Field.Items -> walk(path, f.fields, out)
                else -> {}
            }
        }
    }

    /** [names] as the kept file writes them: one category a line, names sorted. */
    fun encode(versionCode: Int, names: Map<String, Set<String>>): String = buildString {
        append("{\n  \"versionCode\": ").append(versionCode)
        for (k in KINDS) {
            append(",\n  \"").append(k).append("\": ")
            append(RecipeCodec.canonical(names[k].orEmpty().sorted()))
        }
        append("\n}\n")
    }

    @Suppress("UNCHECKED_CAST")
    fun decode(text: String): Pair<Int, Map<String, Set<String>>> {
        val m = com.bamnat.core.remote.Json.parse(text) as Map<String, Any?>
        val v = (m["versionCode"] as Long).toInt()
        return v to KINDS.associateWith { k -> (m[k] as? List<Any?>).orEmpty().map { it as String }.toSortedSet() }
    }

    /**
     * For each category, each name the oldest kept app does not know, with the first app that knows it: [kept] by versionCode, [current]
     * this app's names at [currentCode] (a name only this app knows is this app's).
     */
    fun since(kept: Map<Int, Map<String, Set<String>>>, currentCode: Int, current: Map<String, Set<String>>): Map<String, Map<String, Int>> {
        val all = kept.toSortedMap() + (currentCode to current)
        val oldest = all.values.first()
        return KINDS.associateWith { k ->
            val first = LinkedHashMap<String, Int>()
            for ((v, names) in all) for (n in names[k].orEmpty()) if (n !in oldest[k].orEmpty() && n !in first) first[n] = v
            first.entries.sortedWith(compareBy({ it.value }, { it.key })).associate { it.key to it.value }
        }
    }

    /** Names an older app knew that this one no longer does: a work made there would be refused here. Each "category name (since)". */
    fun dropped(kept: Map<Int, Map<String, Set<String>>>, current: Map<String, Set<String>>): List<String> =
        kept.toSortedMap().flatMap { (v, names) ->
            KINDS.flatMap { k -> names[k].orEmpty().filter { it !in current[k].orEmpty() }.map { "$k $it ($v)" } }
        }.distinct()
}
