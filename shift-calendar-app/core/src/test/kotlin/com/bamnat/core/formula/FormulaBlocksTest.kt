package com.bamnat.core.formula

import com.bamnat.core.remote.Json
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 누락 점검 N7 블록: every shared formula opens as blocks and the blocks write a formula that runs to the same value. */
class FormulaBlocksTest {
    @Suppress("UNCHECKED_CAST")
    @Test fun everySharedCaseRoundTrips() {
        val all = Json.parse(File("../docs/formula-cases.json").readText()) as Map<String, Any?>
        val scope = FormulaScope.of(all["vars"] as Map<String, Any?>, all["inputs"] as Map<String, Any?>)
        var n = 0
        for (c in all["cases"] as List<Map<String, Any?>>) {
            val src = c["src"] as String
            val ok = Formula.parse(src) as? Parsed.Ok ?: continue
            val b = FormulaBlocks.read(src)
            assertNotNull(src, b)
            val again = b!!.text()
            val back = Formula.parse(again)
            assertTrue("$src → $again", back is Parsed.Ok)
            assertEquals("$src → $again", ok.formula.run(scope)?.text, (back as Parsed.Ok).formula.run(scope)?.text)
            assertEquals("$src → $again (blocks again)", b, FormulaBlocks.read(again))
            n++
        }
        assertTrue("cases read: $n", n > 20)
    }

    @Test fun nightAndHandoverAsBlocks() {
        val b = FormulaBlocks.read("근무종류 = \"야간\" 그리고 꼬리표.포함(\"인수인계\")")
        assertEquals(Block.Op("and", Block.Op("=", Block.Name("근무종류"), Block.Text("야간")), Block.Call("포함", listOf(Block.Name("꼬리표"), Block.Text("인수인계")))), b)
        assertEquals("근무종류 = \"야간\" 그리고 포함(꼬리표, \"인수인계\")", b!!.text())
    }

    @Test fun bracketsOnlyWhereNeeded() {
        val or = Block.Op("or", Block.Name("주말"), Block.Name("공휴일"))
        assertEquals("(주말 또는 공휴일) 그리고 아님 쉬는날", Block.Op("and", or, Block.Not(Block.Name("쉬는날"))).text())
        assertEquals("지출 - (수입 - 1)", Block.Op("-", Block.Name("지출"), Block.Op("-", Block.Name("수입"), Block.Num(1.0))).text())
        assertEquals("아님 (근무 = \"야간\")".replace("(", "").replace(")", ""), Block.Not(Block.Op("=", Block.Name("근무"), Block.Text("야간"))).text())
    }

    @Test fun holesAreNotFinished() {
        val ifBlock = FormulaBlocks.call("만약")
        assertEquals(3, (ifBlock as Block.Call).args.size)
        assertFalse(ifBlock.complete)
        assertEquals("만약(□, □, □)", ifBlock.text())
        assertEquals(Block.Hole, FormulaBlocks.read(""))
        assertNull(FormulaBlocks.read("근무 = "))
    }
}
