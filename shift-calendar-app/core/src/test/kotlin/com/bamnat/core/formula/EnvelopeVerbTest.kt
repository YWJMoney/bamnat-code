package com.bamnat.core.formula

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.37 [LEDH-12] 규칙 동사 '봉투에 넣기'. */
class EnvelopeVerbTest {
    @Test fun envelopeVerbSaysItselfKeepsItsShapeAndIsHeld() {
        val a = LogicAct(LogicVerb.ENVELOPE, "여행", 10_000)
        assertEquals("‘여행’ 봉투에 10,000원 넣기", a.say())
        assertEquals(a, LogicAct.from(a.toMap()))
        assertEquals("envelope", a.toMap()["do"])
        assertEquals(Logic.RECEIVED_MAX_WON, a.copy(amount = Logic.RECEIVED_MAX_WON + 1).held().amount)
        assertEquals("맨 위 봉투에 넣기", LogicAct(LogicVerb.ENVELOPE).say())
    }
}
