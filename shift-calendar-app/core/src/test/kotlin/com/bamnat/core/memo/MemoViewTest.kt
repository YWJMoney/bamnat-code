package com.bamnat.core.memo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** [TAB-01][TAB-02] 1.36 메모 속성 and 메모장 보기. */
class MemoViewTest {
    private fun memo(id: Long, body: String, title: String = "m$id", date: LocalDate? = null) = Memo(id = id, title = title, body = body, date = date)

    @Test fun linesReadAsTypedProperties() {
        val p = MemoProps.parse(
            """
            매출: 320,000원
            현금: 12000
            기분: ★★★☆☆
            마감: 22:30
            정산일: 11월 3일
            확인: 예
            메모: 오늘 바빴음
            10:30 회의
            - 목록: 아님
            링크: https://example.com
            """.trimIndent(),
            year = 2026,
        ).associateBy { it.label }
        assertEquals(MemoProps.Type.MONEY, p["매출"]!!.type); assertEquals(320_000.0, p["매출"]!!.num!!, 0.0)
        assertEquals(MemoProps.Type.NUM, p["현금"]!!.type)
        assertEquals(3.0, p["기분"]!!.num!!, 0.0)
        assertEquals(MemoProps.Type.TIME, p["마감"]!!.type)
        assertEquals(LocalDate.of(2026, 11, 3), p["정산일"]!!.date)
        assertEquals(MemoProps.Type.CHECK, p["확인"]!!.type)
        assertEquals(MemoProps.Type.TEXT, p["메모"]!!.type)
        // A clock at the start and a list line are not properties; a link is a 글 칸.
        assertNull(p["10"]); assertNull(p["- 목록"]); assertNull(p["목록"])
        assertEquals("https://example.com", p["링크"]!!.raw)
    }

    @Test fun manWonIsMoneyAndTemplateLinesRoundTrip() {
        assertEquals(35_000.0, MemoProps.read("팁", "3.5만원").num!!, 0.0)
        val said = TemplateAsk.lines(listOf(TemplateAsk("만족", "star"), TemplateAsk("매출", "num")), mapOf("만족" to "4", "매출" to "50,000"))
        val props = MemoProps.parse(said)
        assertEquals(listOf("만족", "매출"), props.map { it.label })
        assertEquals(4.0, props[0].num!!, 0.0)
        assertEquals(50_000.0, props[1].num!!, 0.0)
    }

    @Test fun bareNumberUnderMoneyNameIsMoneyAndStarsAverage() {
        val ms = listOf(memo(1, "금액: 12000\n별점: ★★★★☆"), memo(2, "금액: 3000\n별점: ★★☆☆☆"))
        val cols = MemoProps.columns(ms).toMap()
        assertEquals(MemoProps.Type.MONEY, cols["금액"])
        assertEquals(MemoProps.Type.STAR, cols["별점"])
        assertEquals(3.0, MemoProps.average(ms, "별점")!!, 0.001)
        assertEquals(15000.0, MemoProps.total(ms, "금액")!!, 0.001)
    }

    @Test fun savingAMemoAsTemplateTurnsPickedLinesIntoAsks() {
        val m = Memo(id = 1, title = "마감", body = "매출: 32000\n날짜: 11월 1일\n별점: ★★★★☆\n메모: 비 옴\n오늘도 수고")
        val t = MemoTemplate.fromMemo(m, "t1", "  가게 마감 ", setOf("매출", "날짜", "별점"))
        assertEquals("가게 마감", t.title)
        assertEquals(listOf("매출" to "money", "날짜" to "date", "별점" to "star"), t.asks.map { it.label to it.type })
        assertEquals("메모: 비 옴\n오늘도 수고", t.body)
        val started = t.start(null, LocalDate.of(2026, 11, 2), answers = mapOf("매출" to "50000", "별점" to "5"))
        assertEquals("50,000원", MemoProps.of(started).first { it.label == "매출" }.raw)
        assertEquals("마감", MemoTemplate.fromMemo(m, "t2", "", emptySet()).title)
    }

    @Test fun setWritesReplacesAndRemovesALine() {
        val m = memo(1, "매출: 1000\n본문")
        assertEquals("매출: 2000\n본문", MemoProps.set(m, "매출", "2000").body)
        assertEquals("현금: 5\n매출: 1000\n본문", MemoProps.set(m, "현금", "5").body)
        assertEquals("본문", MemoProps.set(m, "매출", "").body)
    }

    @Test fun columnsTotalsAndMoney() {
        val ms = listOf(memo(1, "매출: 10,000원\n날씨: 맑음"), memo(2, "매출: 5000\n날씨: 비"), memo(3, "매출: 모름"))
        val cols = MemoProps.columns(ms)
        assertEquals("매출", cols[0].first)
        assertEquals(MemoProps.Type.MONEY, cols[0].second)
        assertEquals(15_000.0, MemoProps.total(ms, "매출")!!, 0.0)
        assertEquals("매출", MemoProps.money(ms[1])!!.label)
        assertNull(MemoProps.money(memo(4, "날씨: 맑음")))
    }

    @Test fun boardGroupsByTagPropertyAndCheck() {
        val ms = listOf(memo(1, "#병원 #야간"), memo(2, "#야간"), memo(3, "없음"))
        val cols = MemoView(view = MemoView.BOARD).board(ms)
        assertEquals(listOf("#야간", "#병원", "꼬리표 없음"), cols.map { it.label })
        assertEquals(listOf(1L, 2L), cols[0].memos.map { it.id })

        val byProp = MemoView(view = MemoView.BOARD, by = "p:상태").board(listOf(memo(1, "상태: 끝"), memo(2, "상태: 진행"), memo(3, "상태: 끝"), memo(4, "")))
        assertEquals(listOf("상태 끝", "상태 진행", "상태 없음"), byProp.map { it.label })

        val list = Memo(id = 5, isChecklist = true, checklist = listOf(ChecklistItem("a", true), ChecklistItem("b")))
        assertEquals("하는 중", MemoView.checkState(list))
        val byShift = MemoView(by = MemoView.BY_SHIFT).board(listOf(memo(6, "", date = LocalDate.of(2026, 11, 1))), shiftOf = { "야간" })
        assertEquals("야간", byShift.single().label)
    }

    @Test fun sortByPropertyKeepsMissingLast() {
        val ms = listOf(memo(1, "점수: 3"), memo(2, ""), memo(3, "점수: 10"), memo(4, "점수: 1"))
        assertEquals(listOf(4L, 1L, 3L, 2L), MemoView(sort = "p:점수").sorted(ms).map { it.id })
        assertEquals(listOf(3L, 1L, 4L, 2L), MemoView(sort = "-p:점수").sorted(ms).map { it.id })
    }

    @Test fun encodeDecodeAndBadValuesFallBack() {
        val v = MemoView(MemoView.TABLE, "p:매출", "-p:매출")
        assertEquals(v, MemoView.decode(v.encode()))
        assertEquals("", MemoView().encode())
        assertEquals(MemoView(), MemoView.decode("nope"))
        assertEquals(MemoView(MemoView.LIST, MemoView.BY_TAG, ""), MemoView.decode("weird|p:a/b|p:http"))
        assertTrue(MemoView.validBy("p:현금 매출"))
    }
}
