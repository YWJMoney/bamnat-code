package com.bamnat.core.recipe

import com.bamnat.core.remote.Json
import com.bamnat.core.work.WorkStyle
import java.math.BigDecimal
import java.security.MessageDigest

/**
 * [RCP-01][RCP-27] Reads, checks and writes a recipe (설계서 4-7 S1·S2·S10):
 * - [read] takes the text as it came (from the server, a link or a draft): at most [MAX_BYTES], JSON nested at most [MAX_DEPTH],
 *   then [clean] keeps only what [PieceCatalog] knows and refuses what is out of range.
 * - [write] puts a clean recipe in one fixed form (keys in order, no spaces, numbers without exponents), so the phone and the
 *   server write the same bytes and [hash] (SHA-256) names a version; a phone checks that what it got hashes to what it asked for.
 * The server's `cleanRecipe()` follows the same steps; the cases in `docs/recipe-cases.json` hold the two together.
 */
object RecipeCodec {
    const val MAX_BYTES = 32 * 1024
    const val MAX_DEPTH = 6
    const val MAX_PIECES = 12
    const val MAX_INPUTS = 20
    const val MAX_TOKENS = 12
    const val TITLE = 30
    const val SUMMARY = 60
    const val DESC = 600
    const val TOKEN_NAME = 10
    const val INPUT_LABEL = 20
    private val INPUT_ID = Regex("^i([1-9]|1[0-9]|20)$")
    /** A recipe's code on the server: 8 letters without 0·O·1·I·L (RCP-29). */
    val CODE = Regex("^[2-9A-HJ-NP-Z]{8}$")

    /** [text] as a clean recipe, or [RecipeException] with why. */
    fun read(text: String): Recipe {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw RecipeException("too_big")
        if (depth(text) > MAX_DEPTH) throw RecipeException("too_deep")
        val raw = try { Json.parse(text) } catch (e: Exception) { throw RecipeException("json") }
        return clean(raw)
    }

    /** How deep [text]'s objects and arrays go, counted before parsing so a deep one costs nothing (S2). */
    fun depth(text: String): Int {
        var d = 0
        var max = 0
        var inString = false
        var escape = false
        for (c in text) {
            if (inString) {
                when {
                    escape -> escape = false
                    c == '\\' -> escape = true
                    c == '"' -> inString = false
                }
                continue
            }
            when (c) {
                '"' -> inString = true
                '{', '[' -> { d++; if (d > max) max = d }
                '}', ']' -> d--
            }
        }
        return max
    }

    @Suppress("UNCHECKED_CAST")
    fun clean(raw: Any?): Recipe {
        val m = raw as? Map<String, Any?> ?: throw RecipeException("format")
        if (m["format"] != Recipe.FORMAT) throw RecipeException("format", "format")
        val v = (m["v"] as? Long) ?: throw RecipeException("version", "v")
        if (v > Recipe.VERSION) throw RecipeException("update", "v")
        if (v < 1) throw RecipeException("version", "v")
        val title = text(m["title"], TITLE, "title", min = 1)
        if (NameSkeleton.reserved(title)) throw RecipeException("name_taken", "title")
        val summary = text(m["summary"] ?: "", SUMMARY, "summary")
        val desc = text(m["desc"] ?: "", DESC, "desc", lines = true)
        val tokens = (m["tokens"] as? List<Any?> ?: emptyList()).also { if (it.size > MAX_TOKENS) throw RecipeException("tokens", "tokens") }
            .mapIndexed { i, t ->
                val o = t as? Map<String, Any?> ?: throw RecipeException("tokens", "tokens.$i")
                Token(text(o["name"], TOKEN_NAME, "tokens.$i.name", min = 1), color(o["light"], "tokens.$i.light"), color(o["dark"], "tokens.$i.dark"))
            }
        if (tokens.map { it.name }.toSet().size != tokens.size) throw RecipeException("tokens", "tokens")
        val inputs = (m["inputs"] as? List<Any?> ?: emptyList()).also { if (it.size > MAX_INPUTS) throw RecipeException("inputs", "inputs") }
            .mapIndexed { i, o -> input(o, "inputs.$i") }
        if (inputs.map { it.id }.toSet().size != inputs.size) throw RecipeException("inputs", "inputs")
        val ctx = Ctx(inputs.associateBy { it.id }, tokens.map { it.name }.toSet())
        val rawPieces = m["pieces"] as? List<Any?> ?: throw RecipeException("pieces", "pieces")
        if (rawPieces.size > MAX_PIECES) throw RecipeException("pieces", "pieces")
        val seen = HashSet<PieceKind>()
        val pieces = rawPieces.mapIndexedNotNull { i, p ->
            val o = p as? Map<String, Any?> ?: throw RecipeException("pieces", "pieces.$i")
            val kind = PieceKind.of(o["kind"] as? String ?: "") ?: return@mapIndexedNotNull null // a kind this app does not know
            if (!seen.add(kind)) throw RecipeException("pieces", "pieces.$i")
            Piece(kind, fields(kind.fields, o, "pieces.$i", ctx))
        }
        if (pieces.isEmpty()) throw RecipeException("pieces", "pieces")
        val fits = pieces.flatMapTo(HashSet()) { it.kind.styles }
        val styles = (m["styles"] as? List<Any?> ?: emptyList()).mapNotNull { s -> WorkStyle.entries.firstOrNull { it.name == s } }
            .filter { it in fits }.toSet()
        if (styles.isEmpty()) throw RecipeException("styles", "styles")
        val cover = (m["cover"] as? Map<String, Any?>)?.let { c ->
            Cover(
                color(c["a"], "cover.a"), color(c["b"], "cover.b"),
                ((c["dir"] as? Long) ?: 0L).toInt().also { if (it !in 0..3) throw RecipeException("cover", "cover.dir") },
                (c["emoji"] as? String)?.let(RecipeText::oneEmoji) ?: throw RecipeException("emoji", "cover.emoji"),
                // [RCX-15] 1.35 그림 표지: the id of a picture the server keeps (re-encoded on the phone, looked at before it shows).
                (c["image"] as? String)?.also { if (!Cover.IMAGE.matches(it)) throw RecipeException("cover", "cover.image") },
            )
        } ?: Cover()
        val parent = (m["parent"] as? String)?.also { if (!CODE.matches(it)) throw RecipeException("parent", "parent") }
        // An input no field uses is dropped, so a receiver is never asked for something that changes nothing.
        val used = HashSet<String>().also { u -> pieces.forEach { collectInputs(it.values, u) } }
        return Recipe(title, summary, desc, styles, cover, tokens, inputs.filter { it.id in used }, pieces, parent)
    }

    private class Ctx(val inputs: Map<String, Input>, val tokens: Set<String>)

    private fun collectInputs(v: Any?, into: MutableSet<String>) {
        when (v) {
            is Ref.Input -> into += v.id
            is Map<*, *> -> v.values.forEach { collectInputs(it, into) }
            is List<*> -> v.forEach { collectInputs(it, into) }
        }
    }

    private fun text(v: Any?, max: Int, path: String, min: Int = 0, lines: Boolean = false, guard: Boolean = true): String {
        val s = v as? String ?: throw RecipeException("text", path)
        val t = if (guard) RecipeText.clean(s, lines) else s.trim()
        val n = RecipeText.graphemes(t)
        if (n < min || n > max) throw RecipeException("text", path)
        if (guard) RecipeText.problem(t)?.let { throw RecipeException(it, path) }
        return t
    }

    private fun color(v: Any?, path: String): Long {
        val c = v as? Long ?: throw RecipeException("color", path)
        if (c !in 0L..0xFFFFFFFFL) throw RecipeException("color", path)
        return c or 0xFF000000L
    }

    private fun number(v: Any?, decimals: Int, path: String): Number {
        val d = when (v) { is Long -> v.toDouble(); is Double -> v; else -> throw RecipeException("number", path) }
        if (!d.isFinite()) throw RecipeException("number", path)
        if (decimals == 0) {
            if (d != Math.rint(d)) throw RecipeException("number", path)
            return d.toLong()
        }
        val scale = Math.pow(10.0, decimals.toDouble())
        val r = Math.round(d * scale) / scale
        return if (r == Math.rint(r) && Math.abs(r) < 1e15) r.toLong() else r
    }

    @Suppress("UNCHECKED_CAST")
    private fun input(o: Any?, path: String): Input {
        val m = o as? Map<String, Any?> ?: throw RecipeException("inputs", path)
        val id = m["id"] as? String ?: throw RecipeException("inputs", "$path.id")
        if (!INPUT_ID.matches(id)) throw RecipeException("inputs", "$path.id")
        val kind = InputKind.of(m["kind"] as? String ?: "") ?: throw RecipeException("inputs", "$path.kind")
        val label = text(m["label"], INPUT_LABEL, "$path.label", min = 1)
        val min = (m["min"])?.let { number(it, 2, "$path.min").toDouble() }
        val max = (m["max"])?.let { number(it, 2, "$path.max").toDouble() }
        if (min != null && max != null && min > max) throw RecipeException("inputs", "$path.max")
        val choices = (m["choices"] as? List<Any?> ?: emptyList()).also { if (it.size > 12) throw RecipeException("inputs", "$path.choices") }
            .mapIndexed { i, c -> text(c, 20, "$path.choices.$i", min = 1) }
        if (kind == InputKind.CHOICE && choices.size < 2) throw RecipeException("inputs", "$path.choices")
        val def = m["default"]?.let { d -> inputValue(kind, d, "$path.default", min, max, choices) }
        val hint = text(m["hint"] ?: "", 60, "$path.hint")
        return Input(id, label, kind, def, min, max, m["required"] == true, if (kind == InputKind.CHOICE) choices else emptyList(), hint)
    }

    /** An answer to (or default of) an input of [kind], checked against its range. */
    fun inputValue(kind: InputKind, v: Any?, path: String, min: Double?, max: Double?, choices: List<String>): Any = when (kind) {
        InputKind.MONEY -> (v as? Long ?: throw RecipeException("number", path)).also {
            if (it < 0 || (min != null && it < min) || (max != null && it > max) || it > 100_000_000L) throw RecipeException("range", path)
        }
        InputKind.NUMBER -> number(v, 2, path).also {
            val d = it.toDouble()
            if ((min != null && d < min) || (max != null && d > max)) throw RecipeException("range", path)
        }
        InputKind.TIME -> (v as? String)?.takeIf { TIME.matches(it) } ?: throw RecipeException("time", path)
        InputKind.COLOR -> color(v, path)
        InputKind.EMOJI -> (v as? String)?.let(RecipeText::oneEmoji) ?: throw RecipeException("emoji", path)
        InputKind.CHOICE -> (v as? String)?.takeIf { it in choices } ?: throw RecipeException("choice", path)
        InputKind.TEXT -> text(v, 60, path, min = 1)
        InputKind.FLAG -> v as? Boolean ?: throw RecipeException("flag", path)
    }

    private val TIME = Regex("^([01][0-9]|2[0-3]):[0-5][0-9]$")

    @Suppress("UNCHECKED_CAST")
    private fun fields(schema: List<Field>, o: Map<String, Any?>, path: String, ctx: Ctx): Map<String, Any?> {
        val out = LinkedHashMap<String, Any?>()
        for (f in schema) {
            val p = "$path.${f.key}"
            val v = o[f.key]
            if (v == null) {
                if (!f.optional) throw RecipeException("missing", p)
                continue
            }
            val r = ref(f, v, p, ctx)
            if (r != null) {
                out[f.key] = r
                continue
            }
            val value: Any? = when (f) {
                is Field.Text -> text(v, f.max, p, f.min, f.lines, f.guard).also { t ->
                    f.check?.invoke(t)?.let { throw RecipeException(it, p) }
                }.takeUnless { it.isEmpty() && f.optional }
                is Field.Num -> number(v, f.decimals, p).also { if (it.toDouble() < f.lo || it.toDouble() > f.hi) throw RecipeException("range", p) }
                is Field.Money -> (v as? Long ?: throw RecipeException("number", p)).also { if (it < f.lo || it > f.hi) throw RecipeException("range", p) }
                is Field.Color -> color(v, p)
                is Field.Emoji -> (v as? String)?.let(RecipeText::oneEmoji) ?: throw RecipeException("emoji", p)
                is Field.Flag -> v as? Boolean ?: throw RecipeException("flag", p)
                is Field.Pick -> (v as? String)?.takeIf { it in f.values } ?: throw RecipeException("pick", p)
                is Field.Clock -> (v as? String)?.takeIf { TIME.matches(it) } ?: throw RecipeException("time", p)
                is Field.Texts -> (v as? List<Any?> ?: throw RecipeException("list", p)).also { if (it.size > f.max) throw RecipeException("list", p) }
                    .mapIndexed { i, t -> text(t, f.itemMax, "$p.$i", min = 1) }.takeUnless { it.isEmpty() && f.optional }
                is Field.Nums -> (v as? List<Any?> ?: throw RecipeException("list", p)).also { if (it.size > f.max) throw RecipeException("list", p) }
                    .mapIndexed { i, n -> number(n, f.decimals, "$p.$i").also { if (it.toDouble() < f.lo || it.toDouble() > f.hi) throw RecipeException("range", "$p.$i") } }
                    .distinct().takeUnless { it.isEmpty() && f.optional }
                is Field.Days -> (v as? List<Any?> ?: throw RecipeException("list", p)).map { d ->
                    (d as? String)?.takeIf { it in PieceKind.DAYS } ?: throw RecipeException("pick", p)
                }.distinct().sortedBy { PieceKind.DAYS.indexOf(it) }
                is Field.Items -> (v as? List<Any?> ?: throw RecipeException("list", p)).also { if (it.size > f.max || it.size < f.min) throw RecipeException("list", p) }
                    .mapIndexed { i, item -> fields(f.fields, item as? Map<String, Any?> ?: throw RecipeException("list", "$p.$i"), "$p.$i", ctx) }
                    .takeUnless { it.isEmpty() && f.optional }
            }
            if (value != null) out[f.key] = value
        }
        return out
    }

    /** [v] as a reference to an input or a token, where [f] may hold one; null when [v] is a plain value. */
    private fun ref(f: Field, v: Any?, path: String, ctx: Ctx): Ref? {
        val m = v as? Map<*, *> ?: return null
        if (m.size != 1) throw RecipeException("ref", path)
        (m["token"] as? String)?.let { name ->
            if (f !is Field.Color || name !in ctx.tokens) throw RecipeException("ref", path)
            return Ref.Token(name)
        }
        val id = m["input"] as? String ?: throw RecipeException("ref", path)
        val input = ctx.inputs[id] ?: throw RecipeException("ref", path)
        val fits = when (f) {
            is Field.Money -> input.kind == InputKind.MONEY
            is Field.Num -> f.input && input.kind == InputKind.NUMBER
            is Field.Text -> f.input && input.kind == InputKind.TEXT
            is Field.Color -> input.kind == InputKind.COLOR
            is Field.Emoji -> f.input && input.kind == InputKind.EMOJI
            is Field.Flag -> f.input && input.kind == InputKind.FLAG
            is Field.Clock -> f.input && input.kind == InputKind.TIME
            is Field.Pick -> f.input && input.kind == InputKind.CHOICE && input.choices.all { it in f.values }
            else -> false
        }
        if (!fits) throw RecipeException("ref", path)
        return Ref.Input(id)
    }

    // ---- writing ----

    /** [r] as the map [write] puts in one form. */
    fun toMap(r: Recipe): Map<String, Any?> = buildMap {
        put("format", Recipe.FORMAT)
        put("v", Recipe.VERSION.toLong())
        put("title", r.title)
        if (r.summary.isNotEmpty()) put("summary", r.summary)
        if (r.desc.isNotEmpty()) put("desc", r.desc)
        put("styles", WorkStyle.entries.filter { it in r.styles }.map { it.name })
        put("cover", mapOf("a" to r.cover.a, "b" to r.cover.b, "dir" to r.cover.dir.toLong(), "emoji" to r.cover.emoji) +
            (r.cover.image?.let { mapOf("image" to it) } ?: emptyMap()))
        if (r.tokens.isNotEmpty()) put("tokens", r.tokens.map { mapOf("name" to it.name, "light" to it.light, "dark" to it.dark) })
        if (r.inputs.isNotEmpty()) put("inputs", r.inputs.map { i ->
            buildMap {
                put("id", i.id)
                put("label", i.label)
                put("kind", i.kind.key)
                i.default?.let { put("default", it) }
                i.min?.let { put("min", it) }
                i.max?.let { put("max", it) }
                if (i.required) put("required", true)
                if (i.choices.isNotEmpty()) put("choices", i.choices)
                if (i.hint.isNotEmpty()) put("hint", i.hint)
            }
        })
        put("pieces", r.pieces.map { p -> mapOf("kind" to p.kind.key) + p.values })
        r.parent?.let { put("parent", it) }
    }

    /** [r] in its one written form. */
    fun write(r: Recipe): String = canonical(toMap(r))

    /** The SHA-256 of [r]'s written form, in hex: the name of this version (S10). */
    fun hash(r: Recipe): String = sha256(write(r))

    fun sha256(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** JSON with keys in order and nothing between tokens, as the server's `canonical()` writes it (JSON.stringify's escapes). */
    fun canonical(v: Any?): String = StringBuilder().also { put(it, v) }.toString()

    private fun put(b: StringBuilder, v: Any?) {
        when (v) {
            null -> b.append("null")
            is Boolean -> b.append(v)
            is Long -> b.append(v)
            is Int -> b.append(v)
            is Double -> b.append(num(v))
            is String -> str(b, v)
            is Ref.Input -> put(b, mapOf("input" to v.id))
            is Ref.Token -> put(b, mapOf("token" to v.name))
            is Map<*, *> -> {
                b.append('{')
                v.entries.filter { it.value != null }.sortedBy { it.key as String }.forEachIndexed { i, (k, x) ->
                    if (i > 0) b.append(',')
                    str(b, k as String)
                    b.append(':')
                    put(b, x)
                }
                b.append('}')
            }
            is List<*> -> {
                b.append('[')
                v.forEachIndexed { i, x -> if (i > 0) b.append(','); put(b, x) }
                b.append(']')
            }
            else -> throw IllegalArgumentException("value")
        }
    }

    /** A number as JavaScript writes it, for the values a recipe holds (at most two decimals, no exponent). */
    private fun num(d: Double): String =
        if (d == Math.rint(d) && Math.abs(d) < 1e15) d.toLong().toString()
        else BigDecimal.valueOf(Math.round(d * 100), 2).stripTrailingZeros().toPlainString()

    private fun str(b: StringBuilder, s: String) {
        b.append('"')
        for (c in s) when {
            c == '"' -> b.append("\\\"")
            c == '\\' -> b.append("\\\\")
            c == '\n' -> b.append("\\n")
            c == '\r' -> b.append("\\r")
            c == '\t' -> b.append("\\t")
            c == '\b' -> b.append("\\b")
            c == '\u000C' -> b.append("\\f")
            c < ' ' -> b.append("\\u%04x".format(c.code))
            else -> b.append(c)
        }
        b.append('"')
    }
}
