package com.bamnat.core.boss

import java.time.LocalDate

/**
 * 1.39 [BOSS-12] 산재보험 (공식 대조 G5): 사업주만 내요. 업종별 요율 + 출퇴근재해 요율, 보수총액에 곱해요(고용산재보험료징수법 제13조·제14조).
 * 2026년 값은 고용노동부고시 제2025-91호 '2026년도 사업종류별 산재보험료율'(2025-12-31 고시, 별지 1·2, 국가법령정보센터 별지 PDF로
 * 대조)이에요. 평균 1.47%(업종별 + 출퇴근재해). 임금채권부담금(사업주)은 고시 원문을 대조하지 못해 넣지 않았어요(확인 필요).
 * 세목이 같은 업종 안에서도 다르면(예: 제조업 세부) 사업장의 사업종류 결정 통지서 요율이 맞아요. 그래서 '직접 넣기'도 둬요.
 */
object AccidentRates {
    /** One 사업종류 and its rate in 천분율 (‰). */
    data class Kind(val code: String, val label: String, val permille: Double)

    /** 출퇴근재해 요율: 전 업종 1천분의 0.6 (2026 고시 별지 2). */
    const val COMMUTE_PERMILLE = 0.6

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

    /** "도소매·음식·숙박업 8‰ + 출퇴근 0.6‰". */
    fun describe(k: Kind): String = "${k.label} ${permille(k.permille)}‰ + 출퇴근재해 ${permille(COMMUTE_PERMILLE)}‰"

    fun permille(v: Double): String = if (v == Math.floor(v)) v.toLong().toString() else v.toString()

    /**
     * The month's 산재보험료 for [pay] (보수: 비과세 뺀 지급액) at [code]'s rate plus 출퇴근재해, cut to 10원 like the other 4대보험. 0 with no
     * 업종 picked: the ledger then asks for it once.
     */
    fun premium(pay: Long, code: String?, @Suppress("UNUSED_PARAMETER") on: LocalDate? = null): Long {
        val k = kind(code) ?: return 0
        if (pay <= 0) return 0
        val rate = java.math.BigDecimal.valueOf(k.permille).add(java.math.BigDecimal.valueOf(COMMUTE_PERMILLE))
        return java.math.BigDecimal.valueOf(pay).multiply(rate).divide(java.math.BigDecimal.valueOf(1000))
            .setScale(0, java.math.RoundingMode.DOWN).toLong() / 10 * 10
    }
}
