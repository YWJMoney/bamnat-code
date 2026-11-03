package com.bamnat.core.layout

import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoProps
import com.bamnat.core.money.MoneyEntry
import com.bamnat.core.money.MoneyKind
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * [TAB-08] 1.36 탭 판의 수식 값 (사용자 2026-11-01 "수식 조건부 등등으로 모든 연동을 통해 마음껏 조작"): what a 판 부품 knows about the
 * one thing it draws — a memo card or row, a ledger line, a board column — as names a formula reads (갈래 D `FormulaScope.of(values,
 * inputs)`, 이름은 D의 `FormulaName` 묶음 ROW와 같게: 제목·내용·색번호·고정·금액·종류·분류·칸이름·칸수·칸합계). A memo's own "칸: 값" lines are its [Facts.inputs], read by their 칸 name. Nothing here reads the clock or a store.
 */
object TabFacts {
    data class Facts(val values: Map<String, Any?>, val inputs: Map<String, Any?> = emptyMap())

    /** A memo: 제목, its own 꼬리표, 체크율·남은체크, 색번호, 고정, and for a dated one 날짜·요일·주말 plus [day] (그날 근무·지출…). */
    fun memo(m: Memo, day: Map<String, Any?> = emptyMap()): Facts {
        val props = MemoProps.of(m)
        val values = LinkedHashMap<String, Any?>()
        if (m.date != null) values.putAll(day)
        values["제목"] = m.title.ifBlank { m.preview }
        values["꼬리표"] = m.tags.toList()
        values["체크율"] = if (m.isChecklist && m.itemCount > 0) m.checkedCount * 100 / m.itemCount else 0
        values["남은체크"] = if (m.isChecklist) m.itemCount - m.checkedCount else 0
        values["색번호"] = m.colorIndex
        values["고정"] = m.pinned
        m.date?.let { values.putAll(dateFacts(it)) }
        return Facts(values, props.associate { p -> p.label to (p.num ?: p.date?.toString() ?: p.raw) })
    }

    /** A ledger line: 금액, 종류(수입·지출·이체), 분류, 내용, 날짜·요일·주말. */
    fun money(e: MoneyEntry): Facts = Facts(LinkedHashMap<String, Any?>().apply {
        put("금액", e.amount)
        put("종류", when (e.kind) { MoneyKind.INCOME -> "수입"; MoneyKind.EXPENSE -> "지출"; MoneyKind.TRANSFER -> "이체" })
        put("분류", e.category)
        put("내용", com.bamnat.core.money.LedgerBoard.what(e))
        putAll(dateFacts(e.date))
    })

    /** A board column (메모 보드 칸, 분류 보드 칸): its name, how many, and its total. */
    fun column(name: String, count: Int, total: Number = 0): Facts = Facts(mapOf("칸이름" to name, "칸수" to count, "칸합계" to total))

    private fun dateFacts(d: LocalDate): Map<String, Any?> = mapOf(
        "날짜" to d.toString(), "월" to d.monthValue, "일" to d.dayOfMonth,
        "요일" to "월화수목금토일"[d.dayOfWeek.value - 1].toString(),
        "주말" to (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY),
    )
}
