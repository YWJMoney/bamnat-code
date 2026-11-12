package com.bamnat.core.recipe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 2.0.0 받은 작품의 안전 (검토 09a·09b), core 몫: [RVM-22] RCP-05 알람을 약하게 하는 설정, [RVM-23] RCP-17·STU-19 누르는 꾸밈의 하한,
 * [RVM-24] STU-13 받은 규칙의 알림 글.
 */
class ReceivedSafetyTest {

    // ---- [RVM-22] RCP-05 ----

    private val now = AlarmStrength.Now(ringMinutes = 5, vibrate = true, gradual = false)

    @Test fun whatMakesTheAlarmWeakerIsNamed() {
        val weak = AlarmStrength.weaker(mapOf("vibrate" to false, "ring" to 1L, "gradual" to true, "snooze" to 9L), now)
        assertEquals(listOf("vibrate", "ring", "gradual"), weak.map { it.key })
        assertEquals(listOf("진동 끔", "울림 5분 → 1분", "작게 시작"), weak.map { it.words })
    }

    @Test fun whatMakesItStrongerOrKeepsItIsNotWeaker() {
        assertTrue(AlarmStrength.weaker(mapOf("vibrate" to true, "ring" to 10L, "gradual" to false), now).isEmpty())
        // The same as the phone's now is not weaker either.
        assertTrue(AlarmStrength.weaker(mapOf("ring" to 5L), now).isEmpty())
        // A phone without 진동 now loses nothing to a piece that turns it off.
        assertTrue(AlarmStrength.weaker(mapOf("vibrate" to false), now.copy(vibrate = false)).isEmpty())
    }

    @Test fun keepStrongLeavesTheRestOfThePieceAlone() {
        val r = Recipe("t", styles = setOf(com.bamnat.core.work.WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.ALARMS, mapOf("vibrate" to false, "snooze" to 9L, "ring" to 1L)),
            Piece(PieceKind.CAL_THEME, mapOf("cell" to "tint"))))
        val kept = AlarmStrength.keepStrong(r, now)
        assertEquals(mapOf("snooze" to 9L), kept.pieces.first { it.kind == PieceKind.ALARMS }.values)
        assertEquals(r.pieces[1], kept.pieces[1])
        // Nothing weaker: the very same recipe.
        val strong = r.copy(pieces = listOf(Piece(PieceKind.ALARMS, mapOf("snooze" to 9L))))
        assertTrue(AlarmStrength.keepStrong(strong, now) === strong || AlarmStrength.keepStrong(strong, now) == strong)
    }

    // ---- [RVM-23] RCP-17·STU-19 ----

    private val sticker = DecorItem(DecorKind.EMOJI, "⭐", Decor.CELL, tap = "b0001")

    @Test fun onlyADecorationOneCanSeeTakesATap() {
        assertTrue(DecorTapRule.seen(sticker))
        assertFalse("숨긴 것", DecorTapRule.seen(sticker.copy(hide = true)))
        assertFalse("거의 투명한 것", DecorTapRule.seen(sticker.copy(alpha = DecorTapRule.SEEN - 1)))
        val box = DecorItem(DecorKind.SHAPE, "rect", Decor.CELL, tap = "b0001", fill = 0xFF000000, fillA = 0)
        assertFalse("채우기도 선도 없는 모양", DecorTapRule.seen(box))
        assertTrue("선이 보이는 모양", DecorTapRule.seen(box.copy(line = "solid", lineW = 1.5)))
        assertFalse("선 색이 투명한 모양", DecorTapRule.seen(box.copy(line = "solid", lineW = 1.5, lineC = 0x10000000)))
        assertTrue("채운 모양", DecorTapRule.seen(box.copy(fillA = 100)))
        assertFalse("채우기 색 자체가 투명한 모양", DecorTapRule.seen(box.copy(fill = 0x00000000, fillA = 100)))
        val ring = DecorItem(DecorKind.SHAPE, "ring", Decor.CELL, tap = "b0001", lineC = 0x00FFFFFF)
        assertFalse("선으로 그리는 모양의 선이 투명", DecorTapRule.seen(ring))
        val words = DecorItem(DecorKind.TEXT, "누르세요", Decor.CELL, tap = "b0001", ink = 0x10FFFFFF)
        assertFalse("글자색이 투명한 글상자", DecorTapRule.seen(words))
        assertTrue(DecorTapRule.seen(words.copy(ink = 0xFF202020)))
        assertFalse("아이콘 색이 투명", DecorTapRule.seen(DecorItem(DecorKind.ICON, "star", Decor.CELL, ink = 0x05000000)))
    }

    @Test fun aWholeScreenPictureOrAHugeOneIsNoButton() {
        assertTrue(DecorTapRule.canTap(sticker))
        assertFalse(DecorTapRule.canTap(sticker.copy(at = Decor.APP, full = true)))
        assertFalse(DecorTapRule.canTap(sticker.copy(w = DecorTapRule.MAX_W + 1)))
        assertFalse(DecorTapRule.canTap(sticker.copy(h = DecorTapRule.MAX_H + 1)))
        assertNull("단추가 없는 꾸밈은 말할 것도 없음", DecorTapRule.why(sticker.copy(tap = null)))
        assertEquals("화면 가득 그림은 누르는 단추가 될 수 없어요", DecorTapRule.why(sticker.copy(at = Decor.APP, full = true)))
    }

    @Test fun atMostThreeOnOnePart() {
        val four = (1..4).map { sticker.copy(x = it * 200) }
        assertEquals(3, DecorTapRule.taps(four).size)
        assertEquals(1 to "한 곳에 누르는 꾸밈은 3개까지예요", DecorTapRule.problem(four))
        // Another part counts on its own.
        assertEquals(4, DecorTapRule.taps(four.take(3) + sticker.copy(at = Decor.HEAD)).size)
    }

    @Test fun theLintSaysSoAndFixingLeavesAPicture() {
        val ghost = Decor(listOf(sticker, sticker.copy(alpha = 10, x = 300)))
        val r = Recipe("꾸밈", styles = setOf(com.bamnat.core.work.WorkStyle.SHIFT), pieces = listOf(Piece(PieceKind.DECOR, ghost.values())))
        val line = RecipeLint.check(r).single { it.code == "decor_tap" }
        assertEquals(RecipeLint.Level.ERROR, line.level)
        val fixed = line.fix!!(r)
        val items = Decor.of(fixed.pieces.single().values).items
        assertEquals("both stay drawn", 2, items.size)
        assertEquals(listOf("b0001", null), items.map { it.tap })
        assertTrue(RecipeLint.check(fixed).none { it.code == "decor_tap" })
    }

    @Test fun theServerHoldsTheSameRule() {
        val js = File("../server/company-worker/src/decor.js").readText()
        val tap = Regex("export const DECOR_TAP = \\{ seen: (\\d+), maxW: (\\d+), maxH: (\\d+), perPlace: (\\d+) \\}").find(js)!!.groupValues
        assertEquals(listOf(DecorTapRule.SEEN, DecorTapRule.MAX_W, DecorTapRule.MAX_H, DecorTapRule.PER_PLACE).map { it.toString() }, tap.drop(1))
        val strokes = Regex("export const DECOR_STROKES = \\[([^\\]]*)\\]").find(js)!!.groupValues[1].let { Regex("\"([a-z0-9_]+)\"").findAll(it).map { m -> m.groupValues[1] }.toSet() }
        assertEquals(DecorTapRule.STROKES, strokes)
        assertTrue(DecorTapRule.STROKES.all { it in DecorGlyphs.SHAPES })
    }

    // ---- [RVM-24] STU-13 ----

    @Test fun aReceivedRulesWordsHaveNoAddressOrPhone() {
        assertEquals("오늘 야간이에요", RecipeText.masked("오늘 야간이에요"))
        assertEquals("문의 …로 연락 주세요", RecipeText.masked("문의 010-1234-5678로 연락 주세요"))
        assertEquals("계정 확인 … 들어가요", RecipeText.masked("계정 확인 bamnat-help.com 들어가요"))
        assertEquals("번호 …", RecipeText.masked("번호 123456789012"))
        // Money words stay (a rule saying '이달 지출 38,500원' is the point of it).
        assertEquals("이달 지출 38,500원", RecipeText.masked("이달 지출 38,500원"))
    }
}
