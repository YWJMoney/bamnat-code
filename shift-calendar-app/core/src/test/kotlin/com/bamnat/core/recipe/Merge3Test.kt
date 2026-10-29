package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** [RCX-03] 새 판 반영: what I changed stays, what only the new version changed is offered, both changed is 부딪힘. */
class Merge3Test {
    private fun item(name: String, label: String, color: Long) = mapOf("name" to name, "role" to "day", "label" to label, "color" to color)

    private val base = mapOf("items" to listOf(item("주간", "주", 1L), item("야간", "야", 2L)), "cell" to "fill")

    @Test fun onlyTheNewVersionsChangesAreOfferedAndMineStay() {
        val mine = mapOf("items" to listOf(item("주간", "D", 1L), item("야간", "야", 2L)), "cell" to "fill")
        val theirs = mapOf("items" to listOf(item("주간", "주", 9L), item("야간", "야", 2L), item("비번", "비", 3L)), "cell" to "dot")
        val r = Merge3.of(PieceKind.SHIFT_LOOK, base, mine, theirs)
        val byPath = r.fields.associateBy { it.path }
        assertEquals(Merge3.Kind.THEIRS, byPath.getValue("items[name=주간].color").kind)
        assertEquals(Merge3.Kind.THEIRS, byPath.getValue("cell").kind)
        assertTrue("새 근무는 통째로 제안", byPath.keys.any { it.startsWith("items[name=비번]") })
        assertTrue("내가 고친 글자는 건드리지 않아요", byPath.keys.none { it == "items[name=주간].label" })
        assertEquals(0, r.conflicts)

        val merged = Merge3.apply(mine, theirs, r.fields.map { it.path }.toSet())
        @Suppress("UNCHECKED_CAST") val items = merged["items"] as List<Map<String, Any?>>
        assertEquals(listOf("주간", "야간", "비번"), items.map { it["name"] })
        assertEquals("D", items[0]["label"])
        assertEquals(9L, items[0]["color"])
        assertEquals("dot", merged["cell"])
    }

    @Test fun bothChangedIsAConflictAndMineIsKeptUnlessPicked() {
        val mine = mapOf("items" to listOf(item("주간", "주", 5L), item("야간", "야", 2L)), "cell" to "fill")
        val theirs = mapOf("items" to listOf(item("주간", "주", 9L), item("야간", "야", 2L)), "cell" to "fill")
        val r = Merge3.of(PieceKind.SHIFT_LOOK, base, mine, theirs)
        assertEquals(1, r.conflicts)
        val kept = Merge3.apply(mine, theirs, emptySet())
        @Suppress("UNCHECKED_CAST") assertEquals(5L, (kept["items"] as List<Map<String, Any?>>)[0]["color"])
        // The same change on both sides is nothing to ask about.
        assertTrue(Merge3.of(PieceKind.SHIFT_LOOK, base, theirs, theirs).fields.isEmpty())
    }

    @Test fun referencesAreOneValue() {
        val b = mapOf("wage" to mapOf("input" to "i1"))
        val t = mapOf("wage" to mapOf("input" to "i2"))
        val r = Merge3.of(PieceKind.HOURLY_RULES, b, b, t)
        assertEquals(listOf("wage"), r.fields.map { it.path })
    }
}
