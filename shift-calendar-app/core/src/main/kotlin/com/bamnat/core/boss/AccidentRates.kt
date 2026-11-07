package com.bamnat.core.boss

import java.time.LocalDate

/**
 * 1.39 [BOSS-12] 산재보험 (공식 대조 G5): 사업주만 내요. 업종별 요율 + 출퇴근재해 요율, 보수총액에 곱해요(고용산재보험료징수법 제13조·제14조).
 * 2026년 값은 고용노동부고시 제2025-91호 '2026년도 사업종류별 산재보험료율'(2025-12-31 고시, 별지 1·2, 국가법령정보센터 별지 PDF로
 * 대조)이에요. 평균 1.47%(업종별 + 출퇴근재해).
 * 1.39 [BOSS-12] 발굴 G3-B6: 같은 보수총액에 사업주만 내는 둘을 더해요(고시 원문을 열어 대조, 2026-11-07).
 * - 임금채권부담금: 고용노동부고시 제2025-100호 '임금채권보장기금 사업주 부담금비율'(2025-12-31, 2026-01-01 시행) 보수총액의 1000분의 0.9,
 *   전업종 공통(임금채권보장법 제9조제2항·시행령 제13조). 국가·지방자치단체가 직접 하는 사업은 이 법을 적용하지 않아요(같은 법 제3조).
 * - 석면피해구제분담금: 기후에너지환경부고시 제2025-25호 '2026년도 석면피해구제분담금률'(2025-12-22) 10만분의 6, 2026-01-01~12-31
 *   (석면피해구제법 제31조·제32조, 산재보험 가입 사업주).
 * 세목이 같은 업종 안에서도 다르면(예: 제조업 세부) 사업장의 사업종류 결정 통지서 요율이 맞아요. 그래서 '직접 넣기'도 둬요.
 */
object AccidentRates {
    /** One 사업종류 and its rate in 천분율 (‰). */
    data class Kind(val code: String, val label: String, val permille: Double)

    /** 출퇴근재해 요율: 전 업종 1천분의 0.6 (2026 고시 별지 2). */
    const val COMMUTE_PERMILLE = 0.6

    /** 1.39 [BOSS-12] G3-B6 임금채권부담금: 1000분의 0.9 (고용노동부고시 제2025-100호). */
    const val WAGE_CLAIM_PERMILLE = 0.9

    /** 1.39 [BOSS-12] G3-B6 석면피해구제분담금: 10만분의 6 = 1000분의 0.06 (기후에너지환경부고시 제2025-25호). */
    const val ASBESTOS_PERMILLE = 0.06

    /** 국가·지방자치단체 사업: 임금채권보장법 적용 제외 (제3조). */
    private const val PUBLIC = "public"

    /** The year these rates are for; a later month keeps them until the next 고시 is put in, and says so. */
    const val YEAR = 2026

    /** 작은 가게가 많은 업종을 앞에, 나머지는 고시 순서대로. */
    val KINDS: List<Kind> = listOf(
        Kind("retail_food", "도소매·음식·숙박업", 8.0),
        Kind("misc", "기타의 각종사업", 8.0),
        Kind("pro_health_edu", "전문·보건·교육·여가관련 서비스업", 6.0),
        Kind("facility", "시설관리 및 사업지원 서비스업", 8.0),
        Kind("realty", "부동산 및 임대업", 7.0),
        Kind("finance", "금융 및 보험업", 5.0),
        Kind("construction", "건설업", 35.0),
        Kind("transport_land", "육상 및 수상운수업", 18.0),
        Kind("transport_service", "철도·항공·창고·운수관련서비스업", 8.0),
        Kind("telecom", "통신업", 9.0),
        Kind("mfg_food", "식료품 제조업", 16.0),
        Kind("mfg_textile", "섬유 및 섬유제품 제조업", 11.0),
        Kind("mfg_wood", "목재 및 종이제품 제조업", 20.0),
        Kind("mfg_print", "출판·인쇄·제본업", 9.0),
        Kind("mfg_chem", "화학 및 고무제품 제조업", 13.0),
        Kind("mfg_pharma", "의약품·화장품·연탄·석유제품 제조업", 7.0),
        Kind("mfg_machine", "기계기구·금속·비금속광물제품 제조업", 13.0),
        Kind("mfg_smelt", "금속제련업", 10.0),
        Kind("mfg_electric", "전기기계기구·정밀기구·전자제품 제조업", 6.0),
        Kind("mfg_ship", "선박건조 및 수리업", 24.0),
        Kind("mfg_other", "수제품 및 기타제품 제조업", 12.0),
        Kind("utility", "전기·가스·증기·수도사업", 7.0),
        Kind("mining_coal", "석탄광업 및 채석업", 185.0),
        Kind("mining_other", "석회석·금속·비금속·기타광업", 57.0),
        Kind("forestry", "임업", 58.0),
        Kind("fishery", "어업", 27.0),
        Kind("agriculture", "농업", 20.0),
        Kind("public", "국가 및 지방자치단체의 사업", 9.0),
    )

    /** A rate the 사장님 typed from the 결정 통지서: "custom:12.5" (‰, 출퇴근재해 빼고). */
    const val CUSTOM = "custom:"

    fun kind(code: String?): Kind? {
        code ?: return null
        if (code.startsWith(CUSTOM)) return code.removePrefix(CUSTOM).toDoubleOrNull()?.takeIf { it > 0 && it <= 200 }
            ?.let { Kind(code, "직접 넣은 요율", it) }
        return KINDS.firstOrNull { it.code == code }
    }

    /** "도소매·음식·숙박업 8‰ + 출퇴근재해 0.6‰ + 임금채권부담금 0.9‰ + 석면피해구제분담금 0.06‰". */
    fun describe(k: Kind): String = "${k.label} ${permille(k.permille)}‰ + 출퇴근재해 ${permille(COMMUTE_PERMILLE)}‰" +
        (if (k.code != PUBLIC) " + 임금채권부담금 ${permille(WAGE_CLAIM_PERMILLE)}‰" else "") + " + 석면피해구제분담금 ${permille(ASBESTOS_PERMILLE)}‰"

    /** The whole 사업주 rate for [k] in ‰: 업종 + 출퇴근재해 + 임금채권(국가·지자체 빼고) + 석면. */
    fun totalPermille(k: Kind): java.math.BigDecimal = java.math.BigDecimal.valueOf(k.permille)
        .add(java.math.BigDecimal.valueOf(COMMUTE_PERMILLE))
        .add(if (k.code == PUBLIC) java.math.BigDecimal.ZERO else java.math.BigDecimal.valueOf(WAGE_CLAIM_PERMILLE))
        .add(java.math.BigDecimal.valueOf(ASBESTOS_PERMILLE))

    fun permille(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /**
     * The month's 산재보험료 for [pay] (보수: 비과세 뺀 지급액) at [code]'s rate plus 출퇴근재해, 임금채권부담금 and 석면피해구제분담금, cut to 10원 like the other 4대보험. 0 with no
     * 업종 picked: the ledger then asks for it once.
     */
    fun premium(pay: Long, code: String?, @Suppress("UNUSED_PARAMETER") on: LocalDate? = null): Long {
        val k = kind(code) ?: return 0
        if (pay <= 0) return 0
        val rate = totalPermille(k)
        return java.math.BigDecimal.valueOf(pay).multiply(rate).divide(java.math.BigDecimal.valueOf(1000))
            .setScale(0, java.math.RoundingMode.DOWN).toLong() / 10 * 10
    }
}
