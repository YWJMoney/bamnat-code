package com.bamnat.core.plaza

import com.bamnat.core.work.WorkStyle

/**
 * [PLAZA-32][PLAZA-33] 1.27 묶음 AI (사용자 2026-10-25: "광장이 회사를 선택한 이후에는 회사 정보 광장으로 바뀌어서 자유롭게
 * 토의를 나눌수 있게 … 다른 유형의 형태는 해당 광장 메뉴가 해당 유형 특성에 맞게"): what 광장 is for each kind of work, in one
 * table (계획/1.27-AI-개발계획.md 444 표). A phone whose main calendar joined a company sees that company's 정보 광장; a kind of
 * work with no company sees its own plaza; 교대·일근 with no company keep the rule plaza as before.
 */
object StylePlaza {
    /** What comes first on the screen. */
    enum class Top { COMPANY_ROOM, RULES, RATES, TALK }

    /** 임금표 of a job and unit, as the plaza's rates take them ([PLZ-14]). */
    data class Rates(val job: String, val unit: String, val title: String)

    data class Plan(
        val title: String,
        val top: Top,
        /** The room's id on the server: a company's id, or `style-…` for a kind of work; null for none. */
        val room: String?,
        val roomTitle: String,
        val rates: Rates?,
        /** One line under the title: what belongs here and what does not. */
        val note: String,
        /** The writing box's hint. */
        val hint: String,
        /** For a phone in a company whose kind of work also has a plaza: that plaza's id, as a way across. */
        val alsoRoom: String? = null,
    )

    /** The server's setup id of a room's talk (grp 'room'). */
    const val ROOM = "room"
    const val FIELD = "style-field"
    const val SHOP = "style-shop"
    const val HOURLY = "style-hourly"
    const val INVEST = "style-invest"

    /** The room of [style] with no company; null for 교대·일근, whose plaza is the rule plaza. */
    fun styleRoom(style: WorkStyle?): String? = when (style) {
        WorkStyle.FIELD -> FIELD
        WorkStyle.BUSINESS -> SHOP
        WorkStyle.HOURLY -> HOURLY
        WorkStyle.INVEST -> INVEST
        WorkStyle.SHIFT, WorkStyle.DAY, null -> null
    }

    /** The plaza for [style]; with [company] (id to name) the company this phone's main calendar joined. */
    fun plan(style: WorkStyle?, company: Pair<String, String>? = null): Plan {
        if (company != null && style != WorkStyle.INVEST) return Plan(
            title = "‘${company.second}’ 정보 광장", top = Top.COMPANY_ROOM, room = company.first, roomTitle = "자유 토의",
            rates = null, note = "같은 회사 분들끼리만 보는 곳이에요. 금액은 ‘야간 50%’처럼 비율로 적어요",
            hint = "같은 회사 분들께 하고 싶은 말을 적어 주세요", alsoRoom = styleRoom(style),
        )
        return when (style) {
            WorkStyle.HOURLY -> Plan(
                "알바 광장", Top.RATES, HOURLY, "알바 이야기", Rates("hourly", "hour", "지역 시급 시세"),
                "최저임금·주휴수당은 급여 화면이 계산해요. 가게·사장님 이름은 적지 않아요", "알바하며 겪은 일, 궁금한 점을 적어 주세요",
            )
            WorkStyle.FIELD -> Plan(
                "현장 광장", Top.RATES, FIELD, "현장 이야기", Rates("construction", "day", "지역·직종 일당 시세"),
                "단가·일당 금액은 위 시세에만 올려요. 이야기에는 일정·협상 팁을 적어요", "현장 일정, 단가 협상 팁을 나눠 주세요",
            )
            WorkStyle.BUSINESS -> Plan(
                "가게 광장", Top.TALK, SHOP, "영업·매출 팁", null,
                "매출은 ‘지난달보다 10%’처럼 비율로 적어요. 가게 이름·손님 정보는 적지 않아요", "영업하며 효과 본 방법을 나눠 주세요",
            )
            WorkStyle.INVEST -> Plan(
                "투자 이야기", Top.TALK, INVEST, "투자 이야기", null,
                "종목 추천·매수 권유·리딩방 안내 글은 가려요. 투자 판단과 책임은 본인에게 있어요", "기록하는 방법, 공부한 것을 나눠 주세요",
            )
            WorkStyle.SHIFT, WorkStyle.DAY, null -> Plan(
                "규칙 광장", Top.RULES, null, "", null,
                "회사를 고르면 그 회사 사람끼리 이야기하는 ‘회사 정보 광장’이 열려요", "",
            )
        }
    }
}
