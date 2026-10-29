package com.bamnat.core.plaza

import java.text.Normalizer

/**
 * [J133-88] 1.33 (사용자 2026-10-29 15:48 "인터넷에서 최대한 폭 넓게 급여에 대한 정보를 ... 검색으로 적용할수 있게"): one workplace as
 * the 국민연금 가입 사업장 record has it ([average] 원, the reported income per member). Never anyone's post: said as the record's.
 */
data class PublicFirm(val name: String, val region: String, val industry: String, val members: Int, val average: Long)

/** [J133-89] One line of a public institution's 보수규정, as the table has it: what, how it is worked out, the amount, a note. */
data class PublicRuleLine(val item: String, val calc: String, val amount: String, val note: String) {
    /** The table could not read this line's numbers for sure. */
    val unsure: Boolean get() = "불확실" in note
}

/**
 * [J133-89] A public institution's own pay rules ([rule] in force from [effective], [url] its page): the lines in words, the
 * 통상시급 [hours], and the company file of what is one number for everyone ([takes]: the allowances it holds) for 이 값 쓰기.
 */
data class PublicRuleSet(
    val name: String, val rule: String, val effective: String, val url: String, val hours: Int?,
    val lines: List<PublicRuleLine>, val takes: List<String>, val setup: String,
)

/** [J133-88] The record's answer for a name: the month it is of, where it is from, and the workplaces. */
data class PublicFirms(
    val asOf: String, val source: String, val firms: List<PublicFirm>, val minMembers: Int = 5,
    /** [J133-89] Public institutions' own pay rules for the name, with where they are from. */
    val rules: List<PublicRuleSet> = emptyList(), val rulesSource: String = "",
)

object PublicFirmWords {
    private val MARKS = Regex("\\((주|株|유|사|재|합)\\)|주식회사|유한회사|유한책임회사|합자회사|합명회사|사단법인|재단법인|\\b(co|ltd|inc|corp|llc)\\b\\.?")

    /** The company server's normName: the same name however it is written ((주), 주식회사, spaces, full-width letters). */
    fun norm(name: String): String =
        MARKS.replace(Normalizer.normalize(name, Normalizer.Form.NFKC).lowercase(), "").filter { it.isLetterOrDigit() }

    /** The workplace that is [name] itself, when the record has exactly one by that name; a guess is never made from a prefix. */
    fun exact(found: PublicFirms?, name: String): PublicFirm? {
        val key = norm(name).takeIf { it.length >= 2 } ?: return null
        return found?.firms?.filter { norm(it.name) == key }?.singleOrNull()
    }

    /** "2026년 9월" from the record's "2026-09". */
    fun month(asOf: String): String = Regex("(\\d{4})-(\\d{2})").matchEntire(asOf)?.destructured?.let { (y, m) -> "${y}년 ${m.toInt()}월" } ?: asOf

    /** "가입 11,000명 · 1인 평균 월 약 610만원": the members and the average, in 만원 rounded. */
    fun facts(f: PublicFirm): String {
        val man = (f.average + 5_000) / 10_000
        return "국민연금 가입 ${"%,d".format(f.members)}명" + if (man > 0) " · 1인 평균 월 약 ${"%,d".format(man)}만원" else ""
    }

    /** "인천 동구 · 1차 철강 제조업". */
    fun where(f: PublicFirm): String = listOf(f.region, f.industry).filter { it.isNotBlank() }.joinToString(" · ")

    /** Five or more members paying in is 5인 이상 already (part-timers under 60 hours are not members, so fewer says nothing). */
    fun fiveOrMore(f: PublicFirm): Boolean = f.members >= 5

    const val NOTE = "국민연금 신고 소득 기준이라 상한(월 600만원대)을 넘는 분은 상한으로 세어지고, 상여·성과급이 빠져 실제보다 적을 수 있어요. " +
        "같은 회사 분들이 올린 정보가 아니에요."
}
