package com.bamnat.core.recipe

import com.bamnat.core.remote.Json

/**
 * 2.0.0 [RVS-18] 작품 호환 (검토 SI-04·SD-03 ④, 스튜디오 조사 6-1): which app a work needs, worked out the way the server lists it
 * (`server/company-worker/src/recipes.js` partsMinApp → min_app) from the same table ([PartsSinceTable], written with the server's
 * `parts-since.js` by `PartsSinceExportTest`), so the studio can say "1.39 이하 폰에선 장터에서 안 보여요" while the work is made, not
 * after it went up. `MinAppAgreesTest` writes cases the server's test reads, so the two readings stay one.
 */
object RecipeNeeds {
    /** The oldest app that reads 부품 (1.36.0): a name not in the table is read by every app from it. */
    const val OLDEST = 49

    /** Released apps by versionCode, as people know them. */
    val NAMES: Map<Int, String> = mapOf(49 to "1.36.0", 50 to "1.36.1", 51 to "1.37.0", 52 to "1.38.0", 53 to "1.39.0", 54 to "2.0.0")

    /** [code] as people know it; a code past the list is "새 판". */
    fun nameOf(code: Int): String = NAMES[code] ?: if (code > NAMES.keys.max()) "새 판" else "1.36.0"

    /** One name the work uses that [app] first reads: its category ([RecipeVocab.KINDS]) and name. */
    data class Why(val kind: String, val name: String)

    /** What a work needs: [app] the oldest app that opens all of it ([OLDEST] when every app does), [why] the names that ask for it. */
    class Need(val app: Int, val why: List<Why>) {
        val everyApp: Boolean get() = app <= OLDEST

        /** The chip's words: "1.36부터 모든 폰" or "2.0.0부터". */
        val chip: String get() = if (everyApp) "모든 판" else "${nameOf(app)}부터"

        /** The line under it: who does not see the work, and what asks for the newer app (three at most). */
        val line: String
            get() = if (everyApp) "${nameOf(OLDEST)}부터 모든 폰에서 열려요"
            else "${nameOf(app - 1)} 이하 폰에선 장터에서 이 작품이 안 보여요 · ${nameOf(app)}부터 열리는 것: " +
                whyWords().take(3).joinToString(", ") + (if (whyWords().size > 3) " 등" else "")

        /** [why] in a maker's words, each once. */
        fun whyWords(): List<String> = why.map { w ->
            when (w.kind) {
                "ids" -> BlueprintParts.LABELS[w.name] ?: w.name
                "pieces" -> PieceKind.entries.firstOrNull { it.key == w.name }?.label ?: w.name
                "fields", "picks" -> (PieceKind.entries.firstOrNull { it.key == w.name.substringBefore('.') }?.label ?: "조각") + "의 새 칸"
                "fonts" -> "글꼴 " + (FontCatalog.byId(w.name)?.name ?: w.name)
                "motions" -> "움직임"
                "fills" -> "채우기 무늬"
                "states" -> "근무 상태 꾸밈"
                "decor" -> "꾸밈 날"
                else -> w.name
            }
        }.distinct()
    }

    /** What [r] needs, as it goes up ([RecipeCodec.write]). */
    fun of(r: Recipe, table: Map<String, Map<String, Int>> = PartsSinceTable.MAP): Need =
        ofJson(Json.parse(RecipeCodec.write(r)), table)

    /** What a recipe as JSON ([Json.parse]) needs: the server's partsMinApp, step for step. */
    fun ofJson(recipe: Any?, table: Map<String, Map<String, Int>> = PartsSinceTable.MAP): Need {
        val hits = ArrayList<Pair<Int, Why>>()
        fun since(kind: String, name: String?) {
            val v = name?.let { table[kind]?.get(it) } ?: return
            if (v > 0) hits += v to Why(kind, name)
        }

        fun fieldsOf(v: Any?, path: String) {
            when (v) {
                is List<*> -> for (x in v) if (x is String) since("picks", "$path=$x") else fieldsOf(x, path)
                is Map<*, *> -> for ((k, x) in v) {
                    val at = "$path.$k"
                    since("fields", at)
                    if (x is String) since("picks", "$at=$x") else fieldsOf(x, at)
                }
                else -> {}
            }
        }

        fun fillKind(f: String): String = if (Regex("^(pt|tx|sh)\\.").containsMatchIn(f)) f.split('.').take(2).joinToString(".") else f.split('.')[0]
        fun pieces(s: String): List<String> = s.split('|', '/').map { it.substring(it.indexOf('~') + 1) }
        fun walk(v: Any?) {
            when (v) {
                is List<*> -> v.forEach(::walk)
                is Map<*, *> -> {
                    for ((k, x) in v) {
                        if (x !is String || x.isEmpty()) continue
                        when (k) {
                            "font" -> since("fonts", x)
                            "motion" -> pieces(x).forEach { since("motions", it) }
                            "paint", "linePaint" -> since("fills", fillKind(x))
                            "looks", "screens" -> for (p in pieces(x)) when {
                                p.startsWith("ff:") || p.startsWith("ft:") -> since("fonts", p.substring(3))
                                p.startsWith("fl:") -> since("fills", fillKind(p.substring(3)))
                            }
                        }
                    }
                    v.values.forEach(::walk)
                }
                else -> {}
            }
        }

        val list = ((recipe as? Map<*, *>)?.get("pieces") as? List<*>).orEmpty()
        for (p in list) {
            val m = p as? Map<*, *> ?: continue
            val kind = m["kind"] as? String
            if (kind != null) {
                since("pieces", kind)
                fieldsOf(m.filterKeys { it != "kind" }, kind)
            }
            if (kind == "parts") for (l in (m["parts"] as? List<*>).orEmpty()) {
                val line = l as? Map<*, *> ?: continue
                val id = line["id"] as? String ?: continue
                since("ids", id)
                (line["on"] as? String)?.let { if (id in BlueprintParts.IDS) since("states", it) }
                // An older app asks only whether a name in `order` is one of its children's last words, so of several children with that
                // last word (card under app: app.card, memo.card) the oldest one answers.
                (line["order"] as? String)?.split(',')?.forEach { c ->
                    val kids = BlueprintParts.IDS.filter { BlueprintParts.parentOf(it) == id && it.substringAfterLast('.') == c }
                    if (kids.isNotEmpty()) {
                        val oldest = kids.minBy { table["ids"]?.get(it) ?: 0 }
                        since("ids", oldest)
                    }
                }
            }
            if (kind == "decor") for (it in (m["items"] as? List<*>).orEmpty()) {
                val item = it as? Map<*, *> ?: continue
                if (item["on"] is String && item["at"] is String) since("decor", item["on"] as String)
            }
            walk(m)
        }
        val need = hits.maxOfOrNull { it.first } ?: 0
        return Need(maxOf(need, OLDEST), if (need <= OLDEST) emptyList() else hits.filter { it.first == need }.map { it.second }.distinct())
    }
}
