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
        val typed = listOf(memo(3, "별점: 4"), memo(4, "별점: 5점"))
        assertEquals(MemoProps.Type.STAR, MemoProps.columns(typed).toMap()["별점"])
        assertEquals(4.5, MemoProps.average(typed, "별점")!!, 0.001)
        assertEquals(null, MemoProps.total(typed, "별점"))
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
        val dated = t.start(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 11), answers = mapOf("날짜" to "11월 12일"))
        assertEquals("고른 날짜가 메모 날짜", LocalDate.of(2026, 11, 12), dated.date)
        assertEquals(LocalDate.of(2026, 11, 11), t.start(LocalDate.of(2026, 11, 11), LocalDate.of(2026, 11, 11)).date)
    }

    @Test fun aFewRepeatedWordsMakeAPickColumn() {
        val ms = listOf(memo(1, "상태: 할 일"), memo(2, "상태: 끝"), memo(3, "상태: 할 일"), memo(4, "장소: 서울역 3번 출구 앞"))
        val cols = MemoProps.columns(ms).toMap()
        assertEquals(MemoProps.Type.PICK, cols["상태"])
        assertEquals(MemoProps.Type.TEXT, cols["장소"])
        assertEquals(listOf("할 일", "끝"), MemoProps.choices(ms, "상태"))
        // 상태 is named like one, so even two different words pick.
        assertTrue(MemoProps.pickable("상태", listOf(memo(5, "상태: 접수"), memo(6, "상태: 완료"))))
        val t = MemoTemplate.fromMemo(ms[0], "t", "일", setOf("상태"), mapOf("상태" to listOf("할 일", "하는 중", "끝")))
        assertEquals(TemplateAsk("상태", "pick", listOf("할 일", "하는 중", "끝")), t.asks.single())
    }

    @Test fun movingACardBetweenColumns() {
        val byProp = MemoView(view = MemoView.BOARD, by = "p:상태")
        val m = memo(1, "상태: 할 일\n내용")
        assertEquals("상태: 끝\n내용", MemoView.moved(m, byProp.by, "할 일", "끝")!!.body)
        assertEquals("내용", MemoView.moved(m, byProp.by, "할 일", MemoView.NONE)!!.body)
        val tagged = memo(2, "장보기 #집 #급함")
        assertEquals(listOf("가게", "급함"), MemoView.moved(tagged, MemoView.BY_TAG, "집", "가게")!!.tags)
        assertEquals(listOf("급함"), MemoView.moved(tagged, MemoView.BY_TAG, "집", MemoView.NONE)!!.tags)
        assertEquals(listOf("가게"), MemoView.moved(memo(3, "그냥"), MemoView.BY_TAG, MemoView.NONE, "가게")!!.tags)
        assertEquals(3, MemoView.moved(memo(4, ""), MemoView.BY_COLOR, "0", "3")!!.colorIndex)
        assertNull(MemoView.moved(m, MemoView.BY_SHIFT, "주간", "야간"))
        assertNull(MemoView.moved(m, byProp.by, "끝", "끝"))
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
        // 꼬리표 in word order, so a 칸 stays put when cards move (PC 확인 5ff9ae28).
        assertEquals(listOf("#병원", "#야간", "꼬리표 없음"), cols.map { it.label })
        assertEquals(listOf(1L, 2L), cols[1].memos.map { it.id })

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

    @Test fun calendarViewPlacesMemosByDateOrDateLine() {
        val d = LocalDate.of(2026, 11, 3)
        val dated = memo(1, "", date = d)
        val line = memo(2, "마감일: 2026-11-05")
        val none = memo(3, "그냥 메모")
        assertEquals(d, MemoView.dayOf(dated))
        assertEquals(LocalDate.of(2026, 11, 5), MemoView.dayOf(line))
        assertEquals(null, MemoView.dayOf(none))
        val by = MemoView.byDay(listOf(dated, line, none))
        assertEquals(setOf(d, LocalDate.of(2026, 11, 5)), by.keys)
        assertTrue(MemoView.CALENDAR in MemoView.VIEWS)
        assertEquals(MemoView(MemoView.CALENDAR, MemoView.BY_TAG, ""), MemoView.decode(MemoView(MemoView.CALENDAR).encode()))
    }

    @Test fun boardShowsEmptyColumnsAPickCanTake() {
        val ms = listOf(memo(1, "상태: 하는 중"), memo(2, "상태: 하는 중"))
        val v = MemoView(view = MemoView.BOARD, by = "p:상태")
        val choices = MemoView.choicesFor("상태", ms)
        assertEquals(listOf("하는 중", "할 일", "끝"), choices)
        val cols = v.board(ms, extra = MemoView.knownChoices("상태"))
        assertEquals(listOf("할 일", "하는 중", "끝"), cols.map { it.key })
        assertTrue(cols[0].memos.isEmpty())
        assertEquals(listOf("높음", "보통", "낮음"), MemoView.knownChoices("우선"))
        assertEquals(listOf("높음", "보통", "낮음"), MemoView.knownChoices("중요도"))
        // A 금액 칸 reads as money, and numbers go in number order.
        val money = MemoView(view = MemoView.BOARD, by = "p:금액").board(listOf(memo(4, "금액: 15000"), memo(5, "금액: 9000")))
        assertEquals(listOf("금액 9,000원", "금액 15,000원"), money.map { it.label })
        assertEquals(listOf("A", "B", "할 일", "하는 중", "끝"), MemoView.choicesFor("상태", emptyList(), listOf("A", "B")))
        // A 꼬리표 board has no empty 칸.
        assertEquals(1, MemoView(view = MemoView.BOARD).board(listOf(memo(3, "#집")), extra = listOf("가게")).size)
    }
}
