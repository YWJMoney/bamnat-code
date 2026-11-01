package com.bamnat.core.recipe

import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [BLU-01]..[BLU-04] 1.36 부품 설계도: the format, how a part's style is found, the older pieces read as parts. */
class BlueprintTest {
    private fun recipe(parts: List<Map<String, Any?>>, tokens: List<Map<String, Any?>> = emptyList()) = RecipeCodec.canonical(
        mapOf(
            "format" to Recipe.FORMAT, "v" to 1L, "title" to "부품", "styles" to listOf("SHIFT"), "tokens" to tokens,
            "pieces" to listOf(mapOf("kind" to "parts", "parts" to parts)),
        ),
    )

    private fun refused(parts: List<Map<String, Any?>>): String =
        try { RecipeCodec.read(recipe(parts)); "ok" } catch (e: RecipeException) { e.code }

    @Test
    fun everyPartHasANameAndAParentInTheTree() {
        for (id in BlueprintParts.IDS) {
            assertTrue(id, BlueprintParts.LABELS[id]?.isNotBlank() == true)
            BlueprintParts.parentOf(id)?.let { assertTrue("$id → $it", it in BlueprintParts.IDS) }
        }
        assertEquals("cal.cell", BlueprintParts.parentOf("cal.cell.date"))
        assertEquals("app", BlueprintParts.parentOf("cal"))
        assertEquals("app", BlueprintParts.parentOf("memo.card"))
        assertNull(BlueprintParts.parentOf("widget"))
        assertEquals(listOf("date", "mark", "memo", "money", "hours", "spend", "lunar", "holiday"), BlueprintParts.CHILDREN["cal.cell"])
        // Every field of a part may be left out: an empty line changes nothing.
        assertTrue(BlueprintParts.STYLE_FIELDS.all { it.optional })
    }

    @Test
    fun aRecipeCarriesPartsAndRefusesWhatIsWrong() {
        val r = RecipeCodec.read(
            recipe(
                listOf(
                    mapOf("id" to "cal.head.title", "ink" to mapOf("token" to "포인트"), "size" to 20.25, "weight" to 700L, "다른칸" to 1L),
                    mapOf("id" to "cal.cell", "on" to "role.night", "line" to "dash", "lineW" to 1.5),
                    mapOf("id" to "cal.cell", "order" to "mark,date"),
                ),
                listOf(mapOf("name" to "포인트", "light" to 0xFFFF0000L, "dark" to 0xFF00FF00L)),
            ),
        )
        @Suppress("UNCHECKED_CAST")
        val lines = r.pieces.single().values["parts"] as List<Map<String, Any?>>
        assertEquals(Ref.Token("포인트"), lines[0]["ink"])
        assertEquals(20.3, lines[0]["size"]) // 소수 한 자리
        assertTrue("다른칸" !in lines[0])
        assertEquals("부품 2개 꾸밈: 달 이름·날짜 칸", RecipeDescribe.line(r.pieces.single()))

        assertEquals("dup", refused(listOf(mapOf("id" to "cal.cell", "r" to 4L), mapOf("id" to "cal.cell", "fill" to 0xFF000000L))))
        assertEquals("order", refused(listOf(mapOf("id" to "cal.cell", "order" to "date,title"))))
        assertEquals("pick", refused(listOf(mapOf("id" to "cal.sticker"))))
        assertEquals("pick", refused(listOf(mapOf("id" to "cal.cell", "on" to "rainy"))))
        assertEquals("range", refused(listOf(mapOf("id" to "app.card", "r" to 49L))))
        assertEquals("font", refused(listOf(mapOf("id" to "app", "font" to "Noto Sans"))))
        assertEquals("ok", refused(listOf(mapOf("id" to "app", "font" to "u0123456789abcdef"))))
        // 수식 자리 (갈래 D) is not open in the first format.
        assertEquals("ref", refused(listOf(mapOf("id" to "cal.cell", "fill" to mapOf("fx" to "1")))))
        assertEquals("list", refused(List(BlueprintParts.MAX_LINES + 1) { i -> mapOf("id" to BlueprintParts.IDS[i % BlueprintParts.IDS.size], "on" to BlueprintParts.STATES[i / BlueprintParts.IDS.size]) }))
    }

    @Test
    fun statesLayInOrderAndLettersComeFromTheParent() {
        val b = Blueprint(
            Blueprint.clean(
                listOf(
                    mapOf("id" to "app", "font" to "serif", "ink" to 0xFF111111L, "fill" to 0xFFFFFFFFL),
                    mapOf("id" to "cal.cell", "size" to 12L, "fill" to 0xFF222222L),
                    mapOf("id" to "cal.cell.date", "on" to "sun", "ink" to 0xFFFF0000L),
                    mapOf("id" to "cal.cell.date", "on" to "today", "ink" to 0xFF0000FFL, "fill" to 0xFFFFFF00L),
                    mapOf("id" to "cal.cell.date", "on" to "role.night", "weight" to 800L, "ink" to 0xFF00FF00L),
                ),
            ),
        )
        val plain = b.style("cal.cell.date")
        assertEquals("serif", plain.font)
        assertEquals(12.0, plain.size)
        assertEquals(0xFF111111L, plain.ink)
        assertNull("fill is not taken from the parent", plain.fill)
        // role < sun < today
        assertEquals(0xFF00FF00L, b.style("cal.cell.date", setOf("role.night")).ink)
        assertEquals(0xFFFF0000L, b.style("cal.cell.date", setOf("role.night", "sun")).ink)
        val today = b.style("cal.cell.date", setOf("role.night", "sun", "today"))
        assertEquals(0xFF0000FFL, today.ink)
        assertEquals(800, today.weight)
        assertEquals(0xFFFFFF00L, today.fill)
        assertTrue(b.style("widget").isEmpty)
        assertTrue(b.touches("cal"))
        assertEquals(listOf("date", "mark"), Blueprint.EMPTY.childOrder("cal.cell").take(2))
    }

    @Test
    fun orderPutsTheNamedChildrenFirst() {
        val b = Blueprint(Blueprint.clean(listOf(mapOf("id" to "cal.cell", "order" to "memo,mark"))))
        assertEquals(listOf("memo", "mark", "date", "money", "hours", "spend", "lunar", "holiday"), b.childOrder("cal.cell"))
    }

    @Test
    fun settingRoundTripsAndPartsMoveOnTheirOwn() {
        val b = Blueprint(
            Blueprint.clean(
                listOf(
                    mapOf("id" to "memo.card", "r" to 12L),
                    mapOf("id" to "cal.cell.date", "on" to "today", "fill" to 0xFF336699L),
                    mapOf("id" to "cal.cell", "pad" to 4L),
                ),
            ),
        )
        val again = Blueprint.decode(b.encode())
        assertEquals(b.sorted(), again.lines)
        assertEquals("cal.cell", again.lines.first()["id"])
        assertNull(Blueprint.problem(b.encode()))
        assertEquals("dup", Blueprint.problem("""[{"id":"app"},{"id":"app"}]"""))
        assertTrue(Blueprint.decode("not json").isEmpty)
        assertEquals(setOf("cal.cell", "cal.cell.date"), b.subtree("cal.cell").map { it["id"] }.toSet())
        val mixed = b.plus(listOf(mapOf("id" to "cal.cell", "fill" to 0xFF000000L)))
        assertEquals(4L, mixed.style("cal.cell").pad?.toLong())
        assertEquals(0xFF000000L, mixed.style("cal.cell").fill)
    }

    @Test
    fun olderPiecesReadAsParts() {
        val lines = LegacyParts.of(
            calTheme = CalendarTheme(today = 0xFF123456L, sun = 0xFFFF0000L).encode(),
            calView = CalendarView(grid = "line", order = listOf("memo", "hours"), looks = listOf(CellLook(corner = 8, dateColor = 0xFF010203L, border = "thick"))).encode(),
            screenLooks = ScreenLooks().with(ScreenLooks.ALL, ScreenLook(bg = 0xFFFDF6E3L, accent = 0xFF00897BL)).encode(),
            memoLook = MemoLook(cardColor = 0xFFFFF59DL, corner = 16, font = "hand").encode(),
            moneyLook = MoneyLook(income = 0xFF2E7D32L).encode(),
        )
        // What it reads is a blueprint a recipe would take.
        assertEquals(lines, Blueprint.clean(lines))
        val b = Blueprint(lines)
        assertEquals(0xFF123456L, b.own("cal.cell.date", setOf("today")).fill)
        assertEquals(0xFFFF0000L, b.own("cal.week.day", setOf("sun")).ink)
        assertEquals(8, b.own("cal.cell").r)
        assertEquals("solid", b.own("cal.cell").line)
        assertEquals(1.5, b.own("cal.cell").lineW)
        assertEquals(listOf("date", "mark", "memo", "hours"), b.own("cal.cell").order)
        assertEquals(0xFF010203L, b.own("cal.cell.date").ink)
        assertEquals(0xFFFDF6E3L, b.own("app").fill)
        assertEquals(0xFF00897BL, b.own("app.button").fill)
        assertEquals("hand", b.own("memo.card").font)
        assertEquals(0xFF2E7D32L, b.own("money.amount", setOf("in")).ink)
        assertTrue(LegacyParts.of().isEmpty())
    }

    @Test
    fun everyBuiltInThemeReadsAsParts() {
        var some = 0
        for (style in WorkStyle.entries) for (r in BuiltInThemes.of(style)) {
            val lines = LegacyParts.of(r)
            if (lines.isNotEmpty()) { some++; assertEquals(r.title, lines, Blueprint.clean(lines)) }
        }
        assertTrue(some > 0)
    }
}
