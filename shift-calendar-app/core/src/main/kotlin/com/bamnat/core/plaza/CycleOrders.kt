package com.bamnat.core.plaza

/**
 * [SPAT-03] The usual orders of each 근무형태, offered when a public row says only the form ("공개 순서 없음") or a count
 * the app filled in (6근2휴 → 주주오오야야휴휴). The user picks one or changes it day by day; what they end with is theirs
 * (사용자 확인) and goes to the plaza only if they send it. Sources: research/shift-patterns/README.md (k-labor.co.kr
 * 코드표 2222·3333·4444, 주야비휴, 당비비) and the presets the app already offers (remote/v1/presets.json).
 */
object CycleOrders {
    data class Candidate(val codes: String, val name: String)

    private val BY_FORM: Map<String, List<Candidate>> = mapOf(
        "4조2교대" to listOf(
            Candidate("DDOONNOO", "2222 · 주2·휴2·야2·휴2"),
            Candidate("DNBO", "주야비휴"),
            Candidate("DDDOOONNNOOO", "3333 · 주3·휴3·야3·휴3"),
            Candidate("DDDDOOOONNNNOOOO", "4444 · 주4·휴4·야4·휴4"),
            Candidate("DDNNOOOO", "주주야야휴휴휴휴"),
        ),
        "3조2교대" to listOf(
            Candidate("DDDDOONNNNOO", "4근2휴 · 주4·휴2·야4·휴2"),
            Candidate("DDNNOO", "주주야야휴휴"),
            Candidate("DNO", "주야휴"),
            Candidate("DDNNBO", "주주야야비휴"),
        ),
        "4조3교대" to listOf(
            Candidate("DDEENNOO", "6근2휴 · 주주오오야야휴휴"),
            Candidate("NNEEDDOO", "6근2휴 역순 · 야야오오주주휴휴"),
            Candidate("DDNNEEOO", "6근2휴 · 주주야야오오휴휴"),
            Candidate("DDDOEEEONNNO", "3근1휴 · 12일"),
        ),
        "3조3교대" to listOf(
            Candidate("DDDDDOOEEEEEOONNNNNOO", "주 단위 · 주간→오후→야간"),
            Candidate("NNNNNOODDDDDOOEEEEEOO", "주 단위 · 야간→주간→오후"),
        ),
        "5조3교대" to listOf(Candidate("DDEENNOOOO", "주주오오야야휴휴휴휴")),
        "6조3교대" to listOf(Candidate("DDEENNOOOO", "주주오오야야휴휴휴휴")),
        "2조2교대" to listOf(
            Candidate("DDDDDOONNNNNOO", "1주씩 주간·야간"),
            Candidate("DDDDDDDNNNNNNN", "7일씩 주간·야간"),
            Candidate("DN", "하루씩 주간·야간"),
        ),
        "주야교대" to listOf(Candidate("DDDDDOONNNNNOO", "1주씩 주간·야간"), Candidate("DDDDOOOONNNNOOOO", "주4·휴4·야4·휴4")),
        "3조1교대" to listOf(Candidate("XBO", "당비비 · 당번·비번·휴무"), Candidate("XB", "당비 · 격일")),
        "격일제" to listOf(Candidate("XB", "당비 · 24시간 격일"), Candidate("DO", "근무·휴무 격일")),
        "4부제" to listOf(Candidate("DNBO", "주야비휴")),
        "주간연속2교대" to listOf(Candidate("DDDDDOOEEEEEOO", "1주씩 1조·2조")),
        "2교대" to listOf(Candidate("DDNNOO", "주주야야휴휴"), Candidate("DDDDOONNNNOO", "주4·휴2·야4·휴2")),
        "고정" to listOf(Candidate("DDDDDOO", "월~금 주간"), Candidate("NNNNNOO", "월~금 야간")),
    )

    /** Forms whose candidates are the days of a week (Monday first), not a repeating rotation. */
    val WEEKLY_FORMS = setOf("고정")

    /**
     * The usual orders of [cycle]'s form, the row's own order first when it has one (and only once). A 3교대 roster that
     * comes every month has none.
     */
    fun candidates(cycle: PublicCycle): List<Candidate> {
        if (cycle.roster) return emptyList()
        val own = cycle.codes.takeIf { it.isNotEmpty() }?.let { Candidate(it, if (cycle.orderState == OrderState.EXACT) "공개 자료 순서" else "흔한 순서") }
        val usual = BY_FORM[cycle.formKey].orEmpty()
        return (listOfNotNull(own) + usual).distinctBy { it.codes }
    }

    /** "주주휴휴야야휴휴": the order in the words people say it in. */
    fun spoken(codes: String): String = codes.map { c ->
        when (c) {
            'D' -> '주'; 'E' -> '오'; 'N' -> '야'; 'X' -> '당'; 'B' -> '비'; else -> '휴'
        }
    }.joinToString("")

    /** The codes a day can take in the order editor for [cycle]: its work, then 비번 after a 당번, then 휴무. */
    fun choices(cycle: PublicCycle, codes: String): List<Char> {
        val work = (cycle.shifts.map { it.code } + codes.filter { it in PublicCycle.WORK }.toList()).distinct()
            .sortedBy { PublicCycle.WORK.indexOf(it) }
        return work + listOfNotNull('B'.takeIf { 'X' in work || 'B' in codes }) + 'O'
    }

    /** [codes] with day [i] turned to [code]; a day past the end is added. Up to [MAX] days. */
    fun set(codes: String, i: Int, code: Char): String = when {
        i < codes.length -> codes.substring(0, i) + code + codes.substring(i + 1)
        codes.length < MAX -> codes + code
        else -> codes
    }

    const val MAX = 62

    /**
     * [PLAZA-18] 1.27 AH-I3 오늘 근무만 누르면 자리 찾기: the days of [codes] today can be, given today's kind and, when asked,
     * tomorrow's (휴무 and 비번 count apart, as the order says them). One left is the answer; several ask one more day.
     */
    fun positions(codes: String, today: Char, tomorrow: Char? = null, after: Char? = null): List<Int> {
        if (codes.isEmpty()) return emptyList()
        val n = codes.length
        return codes.indices.filter { i ->
            codes[i] == today && (tomorrow == null || codes[(i + 1) % n] == tomorrow) && (after == null || codes[(i + 2) % n] == after)
        }
    }

    /** The kinds a day of [codes] can be, in the order's own order, for the "오늘 근무가 뭐예요?" chips. */
    fun kinds(codes: String): List<Char> = codes.toList().distinct()
}
