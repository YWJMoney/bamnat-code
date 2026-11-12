package com.bamnat.core.recipe

import com.bamnat.core.remote.Json
import com.bamnat.core.work.WorkStyle
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * 2.0.0 [RVS-01] 조합 시험기 ①(검토 SD-06·RCP-22·I09bb-03) (스튜디오 조사 보고서 5장 S6-1): every field of every piece at its least, its most and in
 * 2,000 random mixes, read, written and read again by [RecipeCodec]. A recipe a maker can make must come back the same; one value
 * out of range must be refused for that value (its code and its path), never with a crash; nothing but [RecipeException] may come out.
 *
 * Values a field checks for itself (채우기 글, 글꼴 이름, 조건 수식, 배치 줄 …) are not made up here: they come from the 장터 works the
 * server already holds (`docs/recipes/market-*.json`, 1,034 works), so the test mixes real values in ways no work has yet.
 */
class RecipeFuzzTest {
    private val files = listOf("market-136", "market-138", "market-138-refresh", "market-138-studio", "market-139")

    @Suppress("UNCHECKED_CAST")
    private val works: List<Map<String, Any?>> by lazy {
        files.flatMap { name ->
            val f = File("../docs/recipes/$name.json")
            if (!f.exists()) emptyList() else (Json.parse(f.readText()) as List<Map<String, Any?>>).map { it["recipe"] as Map<String, Any?> }
        }
    }

    /**
     * Every String and every list a work holds, by where it sits (`kind.key.key`, list places left out): the pool a self-checking
     * field draws from, so a value is only ever used where a work used it.
     */
    private val pools: Pair<Map<String, List<String>>, Map<String, List<List<Any?>>>> by lazy {
        val texts = HashMap<String, LinkedHashSet<String>>()
        val lists = HashMap<String, MutableList<List<Any?>>>()
        fun walk(path: String, v: Any?) {
            when (v) {
                is Map<*, *> -> v.forEach { (k, x) ->
                    val at = "$path.$k"
                    if (x is String) texts.getOrPut(at) { LinkedHashSet() } += x
                    if (x is List<*>) lists.getOrPut(at) { ArrayList() } += x
                    walk(at, x)
                }
                is List<*> -> v.forEach { walk(path, it) }
            }
        }
        @Suppress("UNCHECKED_CAST")
        works.forEach { w -> (w["pieces"] as List<Map<String, Any?>>).forEach { p -> walk(p["kind"] as String, p) } }
        texts.mapValues { it.value.toList() } to lists
    }
    private val pool get() = pools.first
    private val listPool get() = pools.second

    @Test fun everyMarketWorkComesBackTheSame() {
        assertTrue("장터 작품이 1,000개 넘게 있어야 해요: ${works.size}", works.size >= 1000)
        for (w in works) {
            val r = try { RecipeCodec.clean(w) } catch (e: RecipeException) { fail("${w["title"]}: ${e.message}"); return }
            assertRoundTrip(r.title, RecipeCodec.write(r))
        }
    }

    /** Each kind with only what it must have, and with every field at its most (as far as 32KB allows). */
    @Test fun eachPieceAtItsLeastAndItsMost() {
        for (kind in PieceKind.entries) {
            for (mode in listOf(Mode.LEAST, Mode.MOST)) {
                val gen = Gen(Random(kind.ordinal * 31 + mode.ordinal), mode)
                val values = gen.piece(kind) ?: continue
                val text = recipe(listOf(kind to values))
                val r = readOrRefusal("${kind.key} $mode", text) ?: continue
                assertRoundTrip("${kind.key} $mode", RecipeCodec.write(r))
            }
        }
    }

    @Test fun twoThousandMixesComeBackTheSame() {
        var read = 0
        var tooBig = 0
        for (seed in 0 until 2000) {
            val rnd = Random(seed)
            val gen = Gen(rnd, Mode.RANDOM)
            val kinds = PieceKind.entries.shuffled(rnd).take(1 + rnd.nextInt(RecipeCodec.MAX_PIECES))
            val pieces = kinds.mapNotNull { k -> gen.piece(k)?.let { k to it } }
            if (pieces.isEmpty()) continue
            val text = recipe(pieces)
            val r = readOrRefusal("mix $seed", text)
            if (r == null) { tooBig++; continue }
            read++
            assertRoundTrip("mix $seed", RecipeCodec.write(r))
        }
        assertTrue("섞은 작품 대부분이 읽혀야 해요: 읽힘 $read, 너무 큼 $tooBig", read >= 1500)
    }

    /** One value pushed out of its range: refused for that value, at that value's path. */
    @Test fun oneValueOutOfRangeIsRefusedForThatValue() {
        var tried = 0
        for (seed in 0 until 2000) {
            val rnd = Random(10_000 + seed)
            val gen = Gen(rnd, Mode.RANDOM)
            val kind = PieceKind.entries.random(rnd)
            val values = gen.piece(kind)?.toMutableMap() ?: continue
            val f = kind.fields.filter { it.key in values && wrongOf(it, rnd) != null }.randomOrNull(rnd) ?: continue
            val (bad, codes) = wrongOf(f, rnd)!!
            values[f.key] = bad
            val text = recipe(listOf(kind to values))
            if (RecipePlatform.utf8(text).size > RecipeCodec.MAX_BYTES) continue
            tried++
            try {
                RecipeCodec.read(text)
                fail("${kind.key}.${f.key} = $bad 를 받았어요")
            } catch (e: RecipeException) {
                assertTrue("${kind.key}.${f.key} = $bad: ${e.code} (기대 $codes)", e.code in codes)
                assertEquals("${kind.key}.${f.key} = $bad", "pieces.0.${f.key}", e.path)
            } catch (e: Exception) {
                fail("${kind.key}.${f.key} = $bad: RecipeException이 아닌 ${e::class.simpleName}: ${e.message}")
            }
        }
        assertTrue("범위 밖 값을 충분히 시험해야 해요: $tried", tried >= 1500)
    }

    /** What an app does not know: a piece kind is left out, a field is dropped, the rest of the work stays. */
    @Test fun whatThisAppDoesNotKnowIsLeftOut() {
        val gen = Gen(Random(7), Mode.LEAST)
        val kind = PieceKind.entries.first { gen.piece(it) != null }
        val values = gen.piece(kind)!! + ("fieldFromANewerApp" to "x")
        val body = Json.parse(recipe(listOf(kind to values))) as Map<*, *>
        @Suppress("UNCHECKED_CAST")
        val withNew = (body as Map<String, Any?>) + ("pieces" to ((body["pieces"] as List<Any?>) + mapOf("kind" to "kindFromANewerApp", "x" to 1L)))
        val r = RecipeCodec.read(RecipeCodec.canonical(withNew))
        assertEquals(listOf(kind), r.pieces.map { it.kind })
        assertTrue(r.pieces[0].values.keys.none { it == "fieldFromANewerApp" })
    }

    private fun assertRoundTrip(name: String, written: String) {
        val again = try { RecipeCodec.read(written) } catch (e: Exception) { fail("$name: 쓴 것을 다시 못 읽어요: ${e.message}"); return }
        assertEquals("$name: 다시 쓰면 같아야 해요", written, RecipeCodec.write(again))
        assertEquals("$name: 이름(해시)이 같아야 해요", RecipeCodec.sha256(written), RecipeCodec.hash(again))
    }

    /** [text] read, or null when it is over [RecipeCodec.MAX_BYTES] (the only refusal a made-up recipe in range may meet). */
    private fun readOrRefusal(name: String, text: String): Recipe? = try {
        RecipeCodec.read(text)
    } catch (e: RecipeException) {
        if (e.code == "too_big") {
            assertTrue("$name: too_big인데 크지 않아요", RecipePlatform.utf8(text).size > RecipeCodec.MAX_BYTES)
            null
        } else {
            fail("$name: 범위 안의 값인데 거절했어요: ${e.code} at ${e.path}\n${text.take(600)}")
            null
        }
    } catch (e: Exception) {
        fail("$name: RecipeException이 아닌 ${e::class.simpleName}: ${e.message}")
        null
    }

    private fun recipe(pieces: List<Pair<PieceKind, Map<String, Any?>>>): String = RecipeCodec.canonical(
        mapOf(
            "format" to Recipe.FORMAT, "v" to Recipe.VERSION.toLong(), "title" to "퍼즈 작품", "summary" to "", "desc" to "",
            "styles" to WorkStyle.entries.map { it.name },
            "pieces" to pieces.map { (k, v) -> mapOf("kind" to k.key) + v },
        ),
    )

    /** A wrong value for [f] and the codes it may be refused with; null when [f] has no simple wrong value. */
    private fun wrongOf(f: Field, rnd: Random): Pair<Any?, Set<String>>? = when (f) {
        is Field.Num -> if (f.decimals == 0 && rnd.nextBoolean()) (f.lo + 0.5) to setOf("number", "range")
            else (if (rnd.nextBoolean()) f.hi + 1 else f.lo - 1).let { d -> (if (f.decimals == 0) d.toLong() else d) to setOf("range") }
        is Field.Money -> (if (rnd.nextBoolean()) f.hi + 1 else f.lo - 1) to setOf("range")
        is Field.Pick -> "zz-${rnd.nextInt(1000)}" to setOf("pick")
        is Field.Color -> (if (rnd.nextBoolean()) 0x1_0000_0000L else -1L) to setOf("color")
        is Field.Clock -> listOf("24:00", "7:5", "12:60", "아침").random(rnd) to setOf("time")
        is Field.Flag -> "네" to setOf("flag")
        is Field.Text -> if (f.check == null && f.guard) "가".repeat(f.max + 1) to setOf("text") else null
        is Field.Texts -> List(f.max + 1) { "가" } to setOf("list")
        is Field.Nums -> List(f.max + 1) { f.lo } to setOf("list")
        is Field.Days -> listOf("SOMEDAY") to setOf("pick")
        is Field.Items -> if (f.max < 64) List(f.max + 1) { emptyMap<String, Any?>() } to setOf("list", "missing") else null
        is Field.Emoji -> "가" to setOf("emoji")
    }

    private enum class Mode { LEAST, MOST, RANDOM }

    /** Makes values for a field list; null for a piece it cannot make (a required self-checking field no work has a value for). */
    private inner class Gen(val rnd: Random, val mode: Mode) {
        private val letters = "가나다라마바사아자차카타파하고노도로모보소오조초코토포호구누두루무부수우주추쿠투푸후"

        fun piece(kind: PieceKind): Map<String, Any?>? = fields(kind.fields, kind.key, depth = 0)

        private fun fields(schema: List<Field>, path: String, depth: Int): Map<String, Any?>? {
            val out = LinkedHashMap<String, Any?>()
            for (f in schema) {
                val include = !f.optional || when (mode) { Mode.LEAST -> false; Mode.MOST -> true; Mode.RANDOM -> rnd.nextInt(3) > 0 }
                if (!include) continue
                val v = value(f, "$path.${f.key}", depth)
                if (v == null) { if (!f.optional) return null else continue }
                out[f.key] = v
            }
            return out
        }

        private fun count(lo: Int, hi: Int, cap: Int): Int = when (mode) {
            Mode.LEAST -> lo
            Mode.MOST -> hi.coerceAtMost(maxOf(lo, cap))
            Mode.RANDOM -> lo + rnd.nextInt((hi.coerceAtMost(maxOf(lo, cap)) - lo) + 1)
        }

        private fun word(lo: Int, hi: Int): String {
            val n = when (mode) { Mode.LEAST -> lo.coerceAtLeast(if (lo == 0) 0 else 1); Mode.MOST -> hi; Mode.RANDOM -> lo + rnd.nextInt((hi.coerceAtMost(lo + 40) - lo) + 1) }
            return String(CharArray(n) { letters[rnd.nextInt(letters.length)] })
        }

        private fun number(lo: Double, hi: Double, decimals: Int): Any {
            val d = when (mode) { Mode.LEAST -> lo; Mode.MOST -> hi; Mode.RANDOM -> when (rnd.nextInt(4)) { 0 -> lo; 1 -> hi; else -> lo + rnd.nextDouble() * (hi - lo) } }
            if (decimals == 0) return kotlin.math.round(d).toLong().coerceIn(kotlin.math.ceil(lo).toLong(), kotlin.math.floor(hi).toLong())
            val scale = Math.pow(10.0, decimals.toDouble())
            val r = (kotlin.math.round(d * scale) / scale).coerceIn(lo, hi)
            return if (r == kotlin.math.round(r)) r.toLong() else r
        }

        /** A value a work used at [at] that [f] takes as it is. */
        private fun fromPool(at: String, f: Field.Text): String? = pool[at]?.filter { t ->
            RecipeText.graphemes(t) in f.min..f.max && f.check?.invoke(t) == null &&
                (!f.guard || (RecipeText.problem(t) == null && !RecipeText.investAdvice(t) && t == RecipeText.clean(t, f.lines)))
        }?.let { if (it.isEmpty()) null else if (mode == Mode.LEAST) it.first() else it.random(rnd) }

        @Suppress("UNCHECKED_CAST")
        private fun value(f: Field, at: String, depth: Int): Any? = when (f) {
            is Field.Text -> if (f.check != null || !f.guard) fromPool(at, f)
                else word(maxOf(f.min, 1), f.max).takeIf { it.isNotEmpty() }
            is Field.Num -> number(f.lo, f.hi, f.decimals)
            is Field.Money -> when (mode) { Mode.LEAST -> f.lo; Mode.MOST -> f.hi; Mode.RANDOM -> f.lo + (rnd.nextDouble() * (f.hi - f.lo)).toLong() }
            is Field.Color -> when (mode) { Mode.LEAST -> 0L; Mode.MOST -> 0xFFFFFFFFL; Mode.RANDOM -> rnd.nextLong(0L, 0x1_0000_0000L) }
            is Field.Emoji -> pool[at]?.filter { RecipeText.oneEmoji(it) != null }?.randomOrNull(rnd) ?: "🌙"
            is Field.Flag -> if (mode == Mode.RANDOM) rnd.nextBoolean() else mode == Mode.MOST
            is Field.Pick -> when (mode) { Mode.LEAST -> f.values.first(); Mode.MOST -> f.values.last(); Mode.RANDOM -> f.values.random(rnd) }
            is Field.Clock -> "%02d:%02d".format(rnd.nextInt(24), rnd.nextInt(60))
            is Field.Texts -> List(count(0, f.max, 6)) { word(1, f.itemMax) }.distinct().takeIf { it.isNotEmpty() || !f.optional }
            is Field.Nums -> List(count(0, f.max, 6)) { number(f.lo, f.hi, f.decimals) }.distinct().takeIf { it.isNotEmpty() || !f.optional }
            is Field.Days -> PieceKind.DAYS.shuffled(rnd).take(count(0, PieceKind.DAYS.size, 7)).takeIf { it.isNotEmpty() }
            is Field.Items -> items(f, at, depth)
        }

        private fun items(f: Field.Items, at: String, depth: Int): List<Any?>? {
            // A list that checks itself (부품 줄이 겹치지 않게 …): one a work holds, or ours made to pass.
            if (depth > 3) return null
            val made = ArrayList<Map<String, Any?>>()
            repeat(count(f.min, f.max, 8)) { fields(f.fields, at, depth + 1)?.let { made += it } }
            if (made.size < f.min) return listPool[at]?.filter { it.size in f.min..f.max }?.randomOrNull(rnd)
            val check = f.check ?: return made.takeIf { it.isNotEmpty() || !f.optional }
            // The cleaned list is what the check reads: read our items through the codec's own rules by dropping until it passes.
            while (made.size > f.min && check(made) != null) made.removeAt(made.size - 1)
            return if (check(made) == null) made.takeIf { it.isNotEmpty() || !f.optional }
                else listPool[at]?.filter { it.size in f.min..f.max }?.randomOrNull(rnd)
        }
    }
}
