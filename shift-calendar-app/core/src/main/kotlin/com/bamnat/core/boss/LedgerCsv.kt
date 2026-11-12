package com.bamnat.core.boss

import com.bamnat.core.pay.LineKind
import java.time.YearMonth

/**
 * [BOSS-07][P3-03] 임금대장 내보내기 (순번 502): one row per staff member with the items 근로기준법 시행령 제27조 제1항 lists, in its
 * order (원문 대조 2026-11-05, 국가법령정보센터 본문과 같은 korea.legal 사본):
 * 1 성명 · 2 생년월일 등 근로자를 특정할 수 있는 정보 · 3 고용 연월일 · 4 종사하는 업무 · 5 임금 계산기초 · 6 근로일수 · 7 근로시간수 ·
 * 8 연장·야간·휴일근로 시간수 · 9 기본급·수당 등 내역별 금액 · 10 공제액 (2.0.0 [RVJ-15] 검토 PAYU-13: the 명세서's 공제, [Payslip.of]).
 * 제2항: a day worker kept under 30 days may leave 2·5 out; 제27조 제3항 (상시 4명 이하) may leave 7·8 out, but this keeps them.
 *
 * 임금대장 must be kept 3 years (근로기준법 제42조, 시행령 제22조): the app does not say it keeps them for the boss (설계 2-2), the
 * file says to keep it.
 */
object LedgerCsv {
    val HEADER = listOf(
        "성명", "생년월일", "고용 연월일", "종사 업무", "임금 계산기초", "근로일수", "근로시간", "연장근로시간", "야간근로시간", "휴일근로시간",
        "기본급", "주휴·유급휴일", "연장·야간·휴일 수당", "그 밖의 수당", "임금 총액", "공제액", "사업주 4대보험(참고)",
    )

    const val KEEP = "임금대장은 3년 동안 보관해야 해요(근로기준법 제42조). 이 파일을 사장님이 따로 보관해 주세요. 참고용 계산이에요."

    fun write(ledger: Ledger): String = buildString {
        append('﻿') // Excel opens a UTF-8 file with Korean as text only with the BOM.
        appendLine(row(listOf("${ledger.workplace.name} ${ledger.month.year}년 ${ledger.month.monthValue}월 임금대장")))
        ledger.note?.let { appendLine(row(listOf(it))) }
        appendLine(row(HEADER))
        for (l in ledger.lines) appendLine(row(cells(l, ledger.workplace)))
        val deducted = ledger.lines.sumOf { deducted(ledger.workplace, it) }
        appendLine(row(listOf("합계", "", "", "", "", "", "", "", "", "", "", "", "", "", ledger.gross.toString(), deducted.toString(), ledger.employerInsurance.toString())))
        appendLine(row(listOf(KEEP)))
    }

    /** [RVJ-15] What comes off this person's 명세서 (4대보험 근로자 몫·소득세·지방소득세), the same as the slip they get. */
    fun deducted(place: Workplace, l: LedgerLine): Long = if (l.gross <= 0) 0 else Payslip.of(place, l).deducted

    fun cells(l: LedgerLine, place: Workplace? = null): List<String> {
        val s = l.staff
        val basis = "${s.form.label} ${"%,d".format(s.base)}원" + s.allowances.joinToString("") {
            " · ${it.name} ${"%,d".format(it.amount)}원" + (if (it.perDay) "/일" else "") + (if (it.taxFree) "(비과세)" else "")
        }
        val weekly = l.lines.filter { it.kind == LineKind.ALLOWANCE && it.label.contains("주휴") }
        val holiday = weekly.sumOf { it.amount } + l.amountOf(LineKind.LEAVE)
        val base = l.amountOf(LineKind.BASE) + l.amountOf(LineKind.EXTRA_HOURS) + l.amountOf(LineKind.EXTRA)
        val premiums = l.amountOf(LineKind.OVERTIME) + l.amountOf(LineKind.NIGHT) + l.amountOf(LineKind.HOLIDAY)
        val others = l.amountOf(LineKind.ALLOWANCE) - weekly.sumOf { it.amount }
        return listOf(
            s.name, s.birth?.toString().orEmpty(), s.hiredOn?.toString().orEmpty(), s.job, basis,
            l.workDays.toString(), hours(l.workMinutes), hours(l.overtimeMinutes), hours(l.nightMinutes), hours(l.holidayMinutes),
            base.toString(), holiday.toString(), premiums.toString(), others.toString(), l.gross.toString(),
            place?.let { deducted(it, l).toString() } ?: "", l.employerInsurance.toString(),
        )
    }

    fun fileName(place: Workplace, month: YearMonth): String = "밤낮_임금대장_${place.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")}_$month.csv"

    private fun hours(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60}" else "%.2f".format(minutes / 60.0)

    private fun row(cells: List<String>): String = cells.joinToString(",") { c ->
        // A cell starting with = + - @ would run as a formula in a spreadsheet; a quote in front keeps it text.
        val safe = if (c.isNotEmpty() && c[0] in "=+-@" && c.toLongOrNull() == null) "'$c" else c
        if (safe.any { it == ',' || it == '"' || it == '\n' }) "\"" + safe.replace("\"", "\"\"") + "\"" else safe
    }
}
