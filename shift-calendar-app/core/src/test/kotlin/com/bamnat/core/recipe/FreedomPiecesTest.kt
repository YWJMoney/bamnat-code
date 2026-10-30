package com.bamnat.core.recipe

import com.bamnat.core.memo.MemoTemplate
import com.bamnat.core.memo.TemplateAsk
import com.bamnat.core.work.WorkStyle
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [CVW-01][CVW-07][CVW-10][LIN-02] 1.35 자유도: 달력 모양·앱 동작 조각, 메모 틀 입력 칸, 수정판의 달라진 점. */
class FreedomPiecesTest {
    @Test fun aCalendarViewKeepsWhatItSaysAndDropsTheRest() {
        val v = CalendarView(size = "tall", badge = "line", date = "left", grid = "line", memo = 3, order = listOf("memo", "hours"))
        assertEquals(v, CalendarView.decode(v.encode()))
        assertEquals("", CalendarView().encode())
        // Unknown values and lines are dropped, the memo lines kept to 0..4.
        assertEquals(CalendarView(memo = 4, order = listOf("lunar")), CalendarView.decode("size=giant;memo=9;order=x,lunar,lunar"))
        // The lines it names first, then the rest in the app's own order.
        assertEquals(listOf("memo", "hours", "holiday", "money", "spend", "lunar"), v.lines)
        assertTrue(CalendarView.PRESETS.all { (_, p) -> CalendarView.decode(p.encode()) == p })
    }

    @Test fun theNewPiecesAreCheckedLikeTheOthers() {
        fun read(kind: String, extra: String) = RecipeCodec.read(
            "{\"format\":\"bamnat-recipe\",\"v\":1,\"title\":\"메모 다이어리 판\",\"styles\":[\"DAY\"],\"pieces\":[{\"kind\":\"$kind\"$extra}]}")
        val r = read("calView", ",\"size\":\"huge\",\"memo\":4,\"order\":\"memo,holiday\",\"lunar\":true")
        assertEquals(PieceKind.CAL_VIEW, r.pieces.single().kind)
        assertEquals("order", runCatching { read("calView", ",\"order\":\"memo,memo\"") }.exceptionOrNull().let { (it as RecipeException).code })
        val f = read("appFeel", ",\"theme\":\"dark\",\"accent\":4281944491,\"hold\":\"memo\",\"dayOrder\":\"memo,work\"")
        assertEquals("memo", f.pieces.single().values["hold"])
        assertEquals("dayOrder", (runCatching { read("appFeel", ",\"dayOrder\":\"memo,lock\"") }.exceptionOrNull() as RecipeException).code)
        assertTrue(RecipeDescribe.line(r.pieces.single()).contains("칸에 메모 4줄"))
    }

    @Test fun aTemplatesAsksAreWrittenAsLines() {
        val t = MemoTemplate("t", "컨디션 일지", "🌤️", body = "오늘 한 줄:", asks = listOf(
            TemplateAsk("기분", "star"), TemplateAsk("잠", "num"), TemplateAsk("날씨", "pick", listOf("맑음", "비"))))
        val m = t.start(null, LocalDate.of(2026, 10, 30), answers = mapOf("기분" to "4", "잠" to "6.5", "날씨" to "비"))
        assertEquals("기분: ★★★★☆\n잠: 6.5\n날씨: 비\n오늘 한 줄:", m.body)
        // Nothing answered: the template's own text, as before.
        assertEquals("오늘 한 줄:", t.start(null, LocalDate.of(2026, 10, 30)).body)
        val list = t.copy(check = listOf("물 마시기")).start(null, LocalDate.of(2026, 10, 30), answers = mapOf("기분" to "2"))
        // A list keeps its own items; the answers go after the title (F-56).
        assertEquals(listOf("물 마시기"), list.checklist.map { it.text })
        assertEquals("🌤️ 컨디션 일지 (기분: ★★☆☆☆)", list.title)
        // Two 칸 with one name keep their own answers, found by place (F-55).
        val twin = MemoTemplate("t2", "둘", asks = listOf(TemplateAsk("새", "pick", listOf("좋음", "나쁨")), TemplateAsk("새", "num")))
        assertEquals("새: 나쁨\n새: 12345", twin.start(null, LocalDate.of(2026, 10, 30), answers = mapOf("#0" to "나쁨", "#1" to "12345")).body)
    }

    @Test fun aRemixSaysWhatItChanged() {
        val base = Recipe(title = "3교대 판", styles = setOf(WorkStyle.SHIFT), pieces = listOf(
            Piece(PieceKind.SHIFT_LOOK, mapOf("items" to listOf(mapOf("name" to "야간", "role" to "night", "label" to "N", "color" to 0xFF3949ABL)))),
            Piece(PieceKind.WIDGET, mapOf("alpha" to 90L)),
        ))
        val remix = base.copy(pieces = listOf(
            Piece(PieceKind.SHIFT_LOOK, mapOf("items" to listOf(mapOf("name" to "야간", "role" to "night", "label" to "N", "color" to 0xFF000000L)))),
            Piece(PieceKind.CAL_VIEW, mapOf("memo" to 3L)),
        ))
        val d = RecipeDiff.of(base, remix)
        val look = d.first { it.kind == PieceKind.SHIFT_LOOK }
        assertEquals(RecipeDiff.Kind.CHANGED, look.change)
        assertEquals("목록 ‘야간’ 색", look.lines.single().label)
        assertEquals("#3949AB" to "#000000", look.lines.single().before to look.lines.single().after)
        assertEquals(RecipeDiff.Kind.ADDED, d.first { it.kind == PieceKind.CAL_VIEW }.change)
        assertEquals(RecipeDiff.Kind.REMOVED, d.first { it.kind == PieceKind.WIDGET }.change)
        // The maker takes the colour: Merge3 puts exactly that path in.
        val took = Merge3.apply(base.pieces[0].values, remix.pieces[0].values, RecipeDiff.paths(look))
        @Suppress("UNCHECKED_CAST")
        assertEquals(0xFF000000L, ((took["items"] as List<Map<String, Any?>>)[0])["color"])
        assertNull(RecipeDiff.of(base, base).firstOrNull())
    }
}
