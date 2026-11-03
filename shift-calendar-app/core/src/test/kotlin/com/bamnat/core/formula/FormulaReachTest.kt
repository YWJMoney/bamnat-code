package com.bamnat.core.formula

import com.bamnat.core.recipe.Blueprint
import com.bamnat.core.recipe.BlueprintParts
import com.bamnat.core.recipe.PieceKind
import com.bamnat.core.recipe.RecipeCodec
import com.bamnat.core.recipe.RecipeResolve
import com.bamnat.core.recipe.Ref
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth

/**
 * [FXR-01][FXR-02][FXR-03][FXR-04] 1.36 수식 값이 모든 탭에서 돌기: 받는 칸 answers reach `칸("이름")`, 이달급여·그날급여 are filled (and only worked
 * out when read), a recipe keeps an input only formulas read, and the parts drawn for a day are the ones a condition is offered on.
 */
class FormulaReachTest {
    private val today = LocalDate.of(2026, 11, 3)
    private fun book(
        inputs: Map<String, Any?> = emptyMap(),
        monthPay: (YearMonth) -> Long? = { null },
        dayPay: (LocalDate) -> Long? = { null },
        payMonth: (LocalDate) -> YearMonth = { YearMonth.from(it) },
    ) = DayFactsBook(
        today = today, style = "알바", shifts = { null },
        money = { d -> if (d == today) DayFactsBook.DayCash(expense = 60_000) else null },
        monthPay = monthPay, dayPay = dayPay, inputs = inputs, payMonth = payMonth,
    )

    // (a) 받는 칸

    @Test fun anAnswerReachesTheFormula() {
        val f = "지출 > 칸(\"목표\", 50000)"
        assertTrue(Formula.eval(f, book().scope(today))!!.truthy) // no answer: the formula's own default
        assertFalse(Formula.eval(f, book(mapOf("목표" to 70_000L)).scope(today))!!.truthy)
        assertEquals(70_000.0, Formula.eval("input('목표')", book(mapOf("목표" to 70_000L)).scope(today))!!.number)
        assertEquals(70_000.0, Formula.eval("칸(\" 목표 \")", book(mapOf("목표" to 70_000L)).scope(today))!!.number)
    }

    @Test fun answersByLabelTakeTheDefaultWhenNotGiven() {
        val r = RecipeCodec.read(RecipeCodec.canonical(recipe()))
        assertEquals(mapOf("목표" to 50_000L, "한도" to 3L), RecipeResolve.byLabel(r, emptyMap()))
        assertEquals(80_000L, RecipeResolve.byLabel(r, mapOf("i1" to 80_000L))["목표"])
    }

    @Test fun theLabelsAFormulaReads() {
        assertEquals(setOf("목표", "한도"), FormulaInputs.labels("만약(지출 > 칸(\"목표\", 1) 그리고 메모수 > INPUT( '한도' ), 1, 2)"))
        assertEquals(setOf("목표"), FormulaInputs.labels("{칸(“목표”)}원 남았어요"))
        assertTrue(FormulaInputs.labels("빈칸(\"목표\")").isEmpty()) // another word ending in 칸
        assertTrue(FormulaInputs.labels("칸 하나").isEmpty())
        assertEquals(setOf("한도"), FormulaInputs.labelsIn(listOf(mapOf("ink" to Ref.Formula("칸('한도')")))))
    }

    @Test fun aRecipeKeepsAnInputOnlyAFormulaReads() {
        val r = RecipeCodec.read(RecipeCodec.canonical(recipe()))
        assertEquals(listOf("목표", "한도"), r.inputs.map { it.label }) // 안 쓰는 칸 still goes
        // Written and read again (upload, then the receiver's phone), still there.
        assertEquals(listOf("i1", "i2"), RecipeCodec.read(RecipeCodec.write(r)).inputs.map { it.id })
        // The receiver is asked for them next to the piece.
        assertEquals(listOf("i1", "i2"), RecipeResolve.inputsOf(r, r.pieces.first { it.kind == PieceKind.PARTS }).map { it.id })
    }

    // (b) 급여

    @Test fun payIsFilledAndOnlyWorkedOutWhenRead() {
        var months = 0
        var days = 0
        val b = book(monthPay = { months++; if (it == YearMonth.of(2026, 12)) 3_120_000L else 2_000_000L }, dayPay = { days++; 135_000L },
            payMonth = { d -> if (d.dayOfMonth > 20) YearMonth.from(d).plusMonths(1) else YearMonth.from(d) })
        val s = b.scope(today)
        assertEquals(true, Formula.eval("지출 > 0", s)!!.truthy)
        assertEquals(0, months + days) // nothing read 급여 yet
        assertEquals(135_000.0, Formula.eval("그날급여", s)!!.number)
        assertEquals(2_000_000.0, Formula.eval("이달급여", s)!!.number) // 11/3 is November's
        Formula.eval("이달급여 + 그날급여", s)
        assertEquals(1, days) // once a day
        assertEquals(1, months)
        assertEquals(3_120_000.0, Formula.eval("monthPay", b.scope(LocalDate.of(2026, 11, 25)))!!.number) // after the 20th: December's
        assertEquals(3_120_000L, b.values(LocalDate.of(2026, 11, 25))["이달급여"])
    }

    @Test fun noPaySetupReadsAsNone() {
        assertEquals(Value.None, Formula.eval("이달급여", book().scope(today)))
        assertEquals("없음", Formula.eval("만약(이달급여 = 없음, \"없음\", \"있음\")", book().scope(today))!!.text)
    }

    // (c) 날짜가 있는 부품

    @Test fun datedPartsAreRealParts() {
        assertTrue(BlueprintParts.IDS.containsAll(BlueprintParts.DATED))
        for (id in listOf("memo.card", "money.row", "pay.total", "cal.head", "cal.band.slot", "cal.line.row", "cal.weekly.day", "cal.cell.date"))
            assertTrue(id, BlueprintParts.dated(id))
        for (id in listOf("widget", "widget.head", "widget.cell", "cal.week", "app.tab", "memo.board", "money.board.col"))
            assertFalse(id, BlueprintParts.dated(id))
    }

    @Test fun aMemoCardTakesItsDaysCondition() {
        val bp = Blueprint(Blueprint.clean(listOf(
            mapOf("id" to "memo.card", "fill" to 0xFF00FF00L),
            mapOf("id" to "memo.card", "when" to "지출 > 칸(\"목표\", 50000)", "fill" to 0xFFFF0000L),
            mapOf("id" to "money.row", "fill" to mapOf("fx" to "만약(지출 > 50000, 4294901760, 없음)")),
        )))
        val b = book()
        assertEquals(0xFFFF0000L, bp.style("memo.card", scope = b.scope(today)).fill)
        assertEquals(0xFF00FF00L, bp.style("memo.card", scope = b.scope(today.plusDays(1))).fill)
        assertEquals(0xFF00FF00L, bp.style("memo.card", scope = book(mapOf("목표" to 90_000L)).scope(today)).fill)
        assertEquals(0xFFFF0000L, bp.style("money.row", scope = b.scope(today)).fill)
        assertNull(bp.style("money.row", scope = b.scope(today.plusDays(1))).fill)
    }

    private fun recipe(): Map<String, Any?> = mapOf(
        "format" to "bamnat-recipe", "v" to 1L, "title" to "목표 넘으면 빨강", "styles" to listOf("SHIFT"),
        "inputs" to listOf(
            mapOf("id" to "i1", "kind" to "money", "label" to "목표", "default" to 50_000L),
            mapOf("id" to "i2", "kind" to "number", "label" to "한도", "default" to 3L),
            mapOf("id" to "i3", "kind" to "text", "label" to "안 쓰는 칸", "default" to "x"),
        ),
        "pieces" to listOf(mapOf("kind" to "parts", "parts" to listOf(
            mapOf("id" to "cal.cell", "when" to "지출 > 칸(\"목표\", 50000)", "fill" to 0xFFFF0000L),
            mapOf("id" to "cal.cell.date", "ink" to mapOf("fx" to "만약(메모수 > input('한도', 3), 4294901760, 없음)")),
        ))),
    )
}
