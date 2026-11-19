package com.bamnat.app.pay

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.pay.FixedOtHoursRules
import com.bamnat.core.pay.DatedValue
import com.bamnat.core.pay.Occupation
import com.bamnat.core.pay.PayProfile
import com.bamnat.core.pay.PayScale
import com.bamnat.core.pay.ValueTimeline
import com.bamnat.core.pay.WageType
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 2.0.0 [RVJ-77] 검토 PAYU-46·T05b-05·I05ba-01: the 호봉표 last put in is kept with the pay setup (and so in the backup file), and an
 * older file with none, or one that does not read, still opens. [RVJ-76] 검토 PAYU-49: the 고정 연장 시간 '직접' 칸 on the 0.5시간 grid.
 */
@RunWith(AndroidJUnit4::class)
class RvjPayScaleKeptTest {
    private val salary = PayProfile(Occupation.CUSTOM, WageType.MONTHLY, ValueTimeline(listOf(DatedValue(LocalDate.of(2026, 1, 1), 2_400_000))))

    @Test fun theTableGoesThereAndBack() {
        val scale = PayScale(mapOf(1 to 2_400_000L, 2 to 2_480_000L, 3 to 2_560_000L), stepMonth = 3, step = 2, since = LocalDate.of(2026, 11, 1))
        val p = salary.copy(payScale = scale)
        val back = PayCodec.decode(JSONObject(PayCodec.encode(p).toString()))
        assertEquals(scale, back.payScale)
        assertEquals(p.base, back.base)
    }

    @Test fun noTableWritesNothingNew() {
        assertFalse(PayCodec.encode(salary).has("payScale"))
        assertNull(PayCodec.decode(PayCodec.encode(salary)).payScale)
    }

    @Test fun aTableThatDoesNotReadIsDroppedNotTheFile() {
        val o = PayCodec.encode(salary)
        o.put("payScale", JSONObject().put("steps", JSONObject()).put("month", 13).put("step", 1).put("since", "2026-11-01"))
        val back = PayCodec.decode(o)
        assertNull(back.payScale)
        assertEquals(salary.base, back.base)
    }

    @Test fun theHoursTakeHalves() {
        assertEquals(1_350, FixedOtHoursRules.read("22.5"))
        assertEquals(720, FixedOtHoursRules.read("12"))
        assertEquals(2_700, FixedOtHoursRules.read("45"))
        assertEquals(30, FixedOtHoursRules.read("0.5"))
        assertEquals(12_000, FixedOtHoursRules.read("200"))
        // Off the 0.5시간 grid, none, or past 200시간: not used.
        assertNull(FixedOtHoursRules.read("22.3"))
        assertNull(FixedOtHoursRules.read("0"))
        assertNull(FixedOtHoursRules.read("200.5"))
        assertNull(FixedOtHoursRules.read(""))
        assertNull(FixedOtHoursRules.read("."))
    }

    @Test fun theFieldKeepsDigitsAndOnePoint() {
        assertEquals("22.5", FixedOtHoursRules.clean("22,5"))
        assertEquals("22.5", FixedOtHoursRules.clean("22.55"))
        assertEquals("22.5", FixedOtHoursRules.clean("2 2.5시간"))
        assertEquals("123", FixedOtHoursRules.clean("1234"))
        assertEquals("1.2", FixedOtHoursRules.clean("1.2.3"))
        assertEquals("22.5", FixedOtHoursRules.text(1_350))
        assertEquals("20", FixedOtHoursRules.text(1_200))
    }
}
