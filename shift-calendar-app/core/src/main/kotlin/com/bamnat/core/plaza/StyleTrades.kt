package com.bamnat.core.plaza

import com.bamnat.core.work.WorkStyle

/**
 * 2.0.0 [RVN-24] 검토 T22c-04·I22cb-01: 현장·알바·가게의 업종 무리. 1.39 태그는 교대·일근에만 업종([IndustryGroups], 회사의 업종)이 있어
 * 세 유형의 '나와 같은 근무' 렌즈가 비었어요. 처리방침 3.12 1-4항 '이용자가 고른 경우 업종 무리'대로 정해진 목록의 키만 오가고, 처음엔
 * 보이지 않아요(광장 › 내 근무 태그에서 고를 때만). 서버 plaza-people.js PEOPLE_TRADES와 같은 키예요(StyleTradesTest가 맞춰 봐요).
 * 투자는 업종이 맞지 않아 없어요.
 */
object StyleTrades {
    data class Trade(val key: String, val name: String)

    /** 현장: 공사의 큰 갈래(직종보다 넓게, 한 사람을 좁히지 않게). */
    val FIELD = listOf(
        Trade("build", "건축"), Trade("civil", "토목"), Trade("plant", "설비·배관"), Trade("electric", "전기·통신"),
        Trade("interior", "인테리어·마감"), Trade("landscape", "조경"),
    )

    /** 알바: 일하는 곳의 갈래. */
    val HOURLY = listOf(
        Trade("conv", "편의점·마트"), Trade("cafe", "카페"), Trade("dining", "음식점"), Trade("parcel", "물류·택배"),
        Trade("tutor", "학원·교육"), Trade("retail", "판매·매장"), Trade("event", "행사·서빙"),
    )

    /** 가게: 사장님 가게의 갈래. */
    val BUSINESS = listOf(
        Trade("dining", "음식점"), Trade("cafe", "카페·디저트"), Trade("retail", "소매·판매"), Trade("beauty", "미용·뷰티"),
        Trade("repair", "수리·서비스"), Trade("online", "온라인 판매"),
    )

    /** The kinds of work that pick from these lists (not 교대·일근, whose 업종 is [IndustryGroups], nor 투자). */
    val STYLES = setOf(WorkStyle.FIELD, WorkStyle.HOURLY, WorkStyle.BUSINESS)

    fun of(style: WorkStyle): List<Trade> = when (style) {
        WorkStyle.FIELD -> FIELD
        WorkStyle.HOURLY -> HOURLY
        WorkStyle.BUSINESS -> BUSINESS
        else -> emptyList()
    }

    fun name(style: WorkStyle, key: String?): String? = key?.let { k -> of(style).firstOrNull { it.key == k }?.name }
}
