package com.bamnat.core.recipe

import com.bamnat.core.layout.Contrast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVS-06] 그릴 때 지키는 바닥선 (검토 SD-02·SN-05·SN-06·SN-12(1)·RCP-18): what a maker sets is kept as set, and a screen draws it
 * so letters read, presses land where they are seen, and a screen of repeated parts does not all move.
 */
class PartFloorTest {
    private val white = 0xFFFFFFFFL
    private val dark = 0xFF121212L

    private fun line(vararg kv: Pair<String, Any?>) = mapOf(*kv)

    /** Letters the colour of what is under them (RCP-18 '글자색=바탕색') are moved toward black or white until they read, same hue. */
    @Test fun lettersReadOnWhatIsUnderThem() {
        val bp = Blueprint(listOf(
            line("id" to "app", "fill" to 0xFFFFF4B0L),
            line("id" to "memo.card.title", "ink" to 0xFFFFF4B0L, "size" to 16.0),
            line("id" to "cal.cell", "fill" to 0xFF1A237EL),
            line("id" to "cal.cell.memo", "ink" to 0xFF283593L),
        ))
        val shown = bp.floored(white)
        val title = shown.style("memo.card.title")
        assertTrue(Contrast.ratio(title.ink!!, 0xFFFFF4B0L) >= PartFloor.BODY)
        // Moved toward black from a pale yellow: still warm, never another colour.
        assertTrue(((title.ink!! shr 16) and 0xFF) >= ((title.ink!!) and 0xFF))
        // A cell's helper line on the cell's own dark fill reads at 3:1, lifted toward white.
        val memo = shown.style("cal.cell.memo")
        assertTrue(Contrast.ratio(memo.ink!!, 0xFF1A237EL) >= PartFloor.SMALL)
        assertTrue(Contrast.luminance(memo.ink!!) > Contrast.luminance(0xFF283593L))
        assertEquals(setOf(PartFloor.Fix.INK), shown.fixes("cal.cell.memo"))
        // The lines are kept as the maker set them; only drawing changes, and only once floored.
        assertEquals(0xFFFFF4B0L, bp.style("memo.card.title").ink)
        assertEquals(0xFFFFF4B0L, shown.lines[1]["ink"])
    }

    /** Letters that already read are drawn as set; a colour under the part that something else paints (a photo, 칸 꾸미기) is not guessed. */
    @Test fun whatAlreadyReadsOrIsUnknownIsLeft() {
        val bp = Blueprint(listOf(
            line("id" to "cal.head.title", "ink" to 0xFF1B5E20L),
            line("id" to "cal.cell.date", "ink" to 0xFFFFFFFFL),
        )).floored(white)
        assertEquals(0xFF1B5E20L, bp.style("cal.head.title").ink)
        // White dates may sit on a dark 칸 꾸미기 fill this blueprint does not know of: left as set.
        assertEquals(0xFFFFFFFFL, bp.style("cal.cell.date").ink)
        assertTrue(bp.fixes("cal.cell.date").isEmpty())
    }

    /** SN-06: 6~9sp letters are drawn at 12sp (10sp for a cell's small lines); bigger ones as set. */
    @Test fun lettersAreNotTooSmall() {
        val bp = Blueprint(listOf(
            line("id" to "memo.card.body", "size" to 6.0),
            line("id" to "cal.cell.lunar", "size" to 7.0),
            line("id" to "pay.total", "size" to 30.0),
        )).floored(white)
        assertEquals(PartFloor.BODY_SP, bp.style("memo.card.body").size)
        assertEquals(PartFloor.CELL_SP, bp.style("cal.cell.lunar").size)
        assertEquals(30.0, bp.style("pay.total").size)
        assertEquals(setOf(PartFloor.Fix.SIZE), bp.fixes("memo.card.body"))
    }

    /** SN-12(1)·RCP-17: a part a finger presses is never hidden nor near clear; a part only looked at may be hidden. */
    @Test fun whatIsPressedIsSeen() {
        val bp = Blueprint(listOf(
            line("id" to "app.tabs", "alpha" to 0L),
            line("id" to "app.button", "hide" to true),
            line("id" to "cal.cell.lunar", "hide" to true),
            line("id" to "cal.cell.date", "hide" to true),
        )).floored(dark)
        assertEquals(PartFloor.SHOW_ALPHA, bp.style("app.tabs").alpha)
        assertNull(bp.style("app.button").hide)
        assertEquals(true, bp.style("cal.cell.lunar").hide)
        // A calendar without its dates is broken, not a look.
        assertNull(bp.style("cal.cell.date").hide)
        assertEquals(setOf(PartFloor.Fix.ALPHA), bp.fixes("app.tabs"))
    }

    /** SN-03: a part drawn many times on a screen moves only on today's or the picked one; a part drawn once moves as set. */
    @Test fun repeatedPartsMoveOnlyOnTodayOrThePickedOne() {
        val bp = Blueprint(listOf(
            line("id" to "memo.card", "motion" to "pulse"),
            line("id" to "cal.cell.date", "motion" to "glow"),
            line("id" to "cal.head.title", "motion" to "shimmer"),
        )).floored(white)
        assertNull(bp.style("memo.card").motion)
        assertEquals("pulse", bp.style("memo.card", setOf("sel")).motion)
        assertNull(bp.style("cal.cell.date", setOf("sat")).motion)
        assertEquals("glow", bp.style("cal.cell.date", setOf("today")).motion)
        assertEquals("shimmer", bp.style("cal.head.title").motion)
    }

    /** 공간: 바깥 간격 + 안 여백 within a third of the short side, each made smaller by the same share. */
    @Test fun spaceFitsThePart() {
        assertNull(PartFloor.space(2, 4, 4, 48f))
        val (o, h, v) = PartFloor.space(12, 12, 8, 36f)!!
        assertTrue(o + maxOf(h, v) <= 12)
        assertTrue(o > 0 && h > v)
    }

    /** SN-05: a new ground brings helper letters, lines and fields that read on it, on a light ground and a dark one alike. */
    @Test fun aNewGroundBringsColoursThatReadOnIt() {
        for (ground in listOf(0xFF0B1E3AL, 0xFFFFF4B0L, 0xFF7A1F5CL, 0xFF9E9E9EL)) {
            val r = PartFloor.roles(ground)
            assertTrue("on $ground", Contrast.ratio(r.on, ground) >= PartFloor.BODY)
            assertTrue("onVariant $ground", Contrast.ratio(r.onVariant, ground) >= PartFloor.BODY)
            assertTrue("outline $ground", Contrast.ratio(r.outline, ground) >= PartFloor.SMALL)
            // Fields and raised cards are told from the ground, but only just.
            assertTrue(r.field != (ground or 0xFF000000L) && Contrast.ratio(r.field, ground) < 1.5)
        }
        // A look's own letters are kept when they read, moved when they do not.
        assertEquals(0xFFFFE082L, PartFloor.roles(0xFF0B1E3AL, 0xFFFFE082L).on)
        assertTrue(Contrast.ratio(PartFloor.roles(0xFF0B1E3AL, 0xFF102A50L).on, 0xFF0B1E3AL) >= PartFloor.BODY)
    }

    /**
     * Every 장터 work (1,034) and every 가장자리 작품 (E1~E12), drawn on a light and a dark screen, keeps every floor on every part in every
     * state it names: letters that read where what is under them is known, letters not too small, pressed parts seen.
     */
    @Suppress("UNCHECKED_CAST")
    @Test fun everyWorkKeepsTheFloors() {
        val market = listOf("market-136", "market-138", "market-138-refresh", "market-138-studio", "market-139").flatMap { name ->
            val f = java.io.File("../docs/recipes/$name.json")
            if (!f.exists()) emptyList() else (com.bamnat.core.remote.Json.parse(f.readText()) as List<Map<String, Any?>>).map { it["recipe"] as Map<String, Any?> }
        }
        val edge = (java.io.File("../docs/recipes/edge-200").listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .map { com.bamnat.core.remote.Json.parse(it.readText()) as Map<String, Any?> }
        assertTrue("works: ${market.size} + ${edge.size}", market.size >= 1000 && edge.size >= 12)
        var parts = 0
        var fixed = 0
        for (w in market + edge) {
            val r = try { RecipeCodec.clean(w) } catch (e: RecipeException) { continue }
            val lines = r.pieces.firstOrNull { it.kind == PieceKind.PARTS }?.values?.get("parts") as? List<Map<String, Any?>> ?: continue
            val plain = lines.map { l -> l.mapValues { (_, v) -> if (v is Ref) null else v }.filterValues { it != null } }
            parts++
            for (screen in listOf(white, dark)) {
                val bp = Blueprint(plain).forDark(screen == dark).floored(screen)
                for (l in plain) {
                    val id = l["id"] as String
                    val states = setOfNotNull(l["on"] as? String)
                    val s = bp.style(id, states)
                    if (bp.fixes(id, states).isNotEmpty()) fixed++
                    s.size?.let { assertTrue("${r.title} $id size $it", it >= if (PartFloor.small(id)) PartFloor.CELL_SP else PartFloor.BODY_SP) }
                    if (id in PartFloor.PRESSED) {
                        assertTrue("${r.title} $id hidden", s.hide != true)
                        assertTrue("${r.title} $id alpha ${s.alpha}", (s.alpha ?: 100) >= PartFloor.SHOW_ALPHA)
                    }
                    // Where the part's own solid fill is what is under its letters, they read on it.
                    val ink = s.ink
                    if (ink != null && PartFloor.fillStrength(s) >= 1.0) {
                        val ratio = Contrast.ratio(ink or 0xFF000000L, PartFloor.groundOf(s, screen))
                        assertTrue("${r.title} $id ink on its fill $ratio", ratio >= PartFloor.SMALL - 0.01)
                    }
                }
            }
        }
        assertTrue("works with parts: $parts", parts > 100)
        assertTrue("floors that did something: $fixed", fixed > 0)
    }

    private val aboveIds = PartFloor.ABOVE

    /**
     * 2.0.0 [RVS-14] 첫 줄 먼저 (HQ-56): the biggest 달력 머리 a maker can set (바깥 간격·안 여백 32, 달 이름 96sp, 요일 글자 96sp) is kept as
     * set and drawn within [PartFloor.ABOVE_DP]; its sides are kept within [PartFloor.HEAD_SIDES]; the studio hears what it pushes and that it is kept.
     */
    @Test fun theHeadPushesTheFirstRowOnlySoFar() {
        val bp = Blueprint(listOf(
            line("id" to "cal.head", "out" to 32, "pad" to 32, "fill" to 0xFFFFFFFFL),
            line("id" to "cal.head.title", "size" to 96.0, "pad" to 12),
            line("id" to "cal.head.today", "out" to 20),
            line("id" to "cal.head.nav", "pad" to 32),
            line("id" to "cal.week", "pad" to 32),
            line("id" to "cal.week.day", "size" to 96.0),
        ))
        assertTrue("as set: ${bp.abovePush()}", bp.abovePush() > 200)
        for (screen in listOf(white, dark)) {
            val shown = bp.floored(screen)
            val drawn = aboveIds.associateWith { shown.style(it) }
            assertTrue("drawn: ${PartFloor.abovePush(drawn)}", PartFloor.abovePush(drawn) <= PartFloor.ABOVE_DP)
            assertEquals(PartFloor.HEAD_TITLE_SP, drawn.getValue("cal.head.title").size)
            // The sides too: the month, ‹ ›, 오늘 and 도구 keep their one line.
            for ((id, max) in PartFloor.HEAD_SIDES) drawn.getValue(id).let { assertTrue("$id ${it.out} + ${it.padH}", (it.out ?: 0) + (it.padH ?: 0) <= max) }
            assertTrue(PartFloor.Fix.ABOVE in shown.fixes("cal.head"))
            assertTrue(PartFloor.Fix.ABOVE in shown.fixes("cal.week.day", setOf("sun")))
        }
        // As set, still.
        assertEquals(96.0, bp.style("cal.head.title").size)
        assertEquals(32, bp.lines[0]["pad"])
    }

    /** One that fits (E9 '16조각': 바깥 간격 6, 안 여백 10, 22sp) is drawn exactly as set; one that adds nothing says 0. */
    @Test fun aHeadThatFitsIsDrawnAsSet() {
        val bp = Blueprint(listOf(line("id" to "cal.head", "out" to 6, "pad" to 10, "r" to 22), line("id" to "cal.head.title", "size" to 22.0, "font" to "jua")))
        assertEquals(32, bp.abovePush())
        val shown = bp.floored(white)
        assertEquals(10, shown.style("cal.head").padV)
        assertEquals(6, shown.style("cal.head").out)
        assertTrue(shown.fixes("cal.head").isEmpty())
        assertEquals(0, Blueprint(listOf(line("id" to "cal.cell", "pad" to 30))).abovePush())
    }

    /** Every 장터 work and 가장자리 작품 draws its head and weekday row within [PartFloor.ABOVE_DP] on a light and a dark screen. */
    @Suppress("UNCHECKED_CAST")
    @Test fun everyWorkLeavesTheFirstRowHigh() {
        val works = listOf("market-136", "market-138", "market-138-refresh", "market-138-studio", "market-139").flatMap { name ->
            val f = java.io.File("../docs/recipes/$name.json")
            if (!f.exists()) emptyList() else (com.bamnat.core.remote.Json.parse(f.readText()) as List<Map<String, Any?>>).map { it["recipe"] as Map<String, Any?> }
        } + (java.io.File("../docs/recipes/edge-200").listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .map { com.bamnat.core.remote.Json.parse(it.readText()) as Map<String, Any?> }
        var kept = 0
        var most = 0
        for (w in works) {
            val r = try { RecipeCodec.clean(w) } catch (e: RecipeException) { continue }
            val lines = r.pieces.firstOrNull { it.kind == PieceKind.PARTS }?.values?.get("parts") as? List<Map<String, Any?>> ?: continue
            val plain = lines.map { l -> l.mapValues { (_, v) -> if (v is Ref) null else v }.filterValues { it != null } }
            val bp = Blueprint(plain)
            most = maxOf(most, bp.abovePush())
            if (bp.abovePush() > PartFloor.ABOVE_DP) kept++
            for (screen in listOf(white, dark)) {
                val shown = bp.forDark(screen == dark).floored(screen)
                val push = PartFloor.abovePush(aboveIds.associateWith { shown.style(it) })
                assertTrue("${r.title}: $push", push <= PartFloor.ABOVE_DP)
            }
        }
        println("[RVS-14] 첫 줄을 ${PartFloor.ABOVE_DP}dp 넘게 미는 작품 $kept, 가장 많이 ${most}dp")
        assertTrue("kept: $kept", kept in 1..200)
    }

    /** Unfloored, a blueprint draws exactly as the lines say (the studio's own values, an old app's look). */
    @Test fun withoutAScreenNothingChanges() {
        val bp = Blueprint(listOf(line("id" to "memo.card.body", "size" to 6.0, "ink" to 0xFFFFFFFFL), line("id" to "app.tabs", "alpha" to 0L)))
        assertEquals(6.0, bp.style("memo.card.body").size)
        assertEquals(0, bp.style("app.tabs").alpha)
        assertTrue(bp.fixes("memo.card.body").isEmpty())
    }
}
