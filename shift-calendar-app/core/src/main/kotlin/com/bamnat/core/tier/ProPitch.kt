package com.bamnat.core.tier

import com.bamnat.core.work.WorkStyle

/**
 * 1.36 840 세 장 페이월 (수익 3판 2절): what 프로 adds, said for the user's kind of work. Page 1 is their own numbers (보상 장부, 찾아 준
 * 돈), or [headline] when there are none; page 2 is [five]; page 3 the plans. Every line is a 프로 feature the app has.
 */
object ProPitch {
    fun headline(style: WorkStyle?): String = when (style ?: WorkStyle.SHIFT) {
        WorkStyle.SHIFT -> "야간수당 빠진 달, 명세서 사진 한 장으로 찾아요"
        WorkStyle.DAY -> "연차 남은 날이 돈으로 얼마인지, 캘린더에 근무가 저절로"
        WorkStyle.HOURLY -> "주휴·3.3% 세금 맞는지, 두 알바 한눈에"
        WorkStyle.FIELD -> "공수·일당 1년치를 리포트로"
        WorkStyle.BUSINESS -> "부가세·종소세 어림과 돈 흐름을 밤낮이"
        WorkStyle.INVEST -> "기록을 잃지 않고, 돈 흐름을 1년으로"
    }

    fun five(style: WorkStyle?): List<String> = when (style ?: WorkStyle.SHIFT) {
        WorkStyle.SHIFT -> listOf("명세서 대조·규칙 자동 채우기", "지난달·작년 같은 달과 비교", "끄기 미션·일어났나요 확인", "휴대폰 캘린더에 근무 자동 쓰기", "광고 0")
        WorkStyle.DAY -> listOf("미사용 연차수당", "휴대폰 캘린더·구독 주소", "급여 리포트 PDF·CSV", "매일 자동 백업", "광고 0")
        WorkStyle.HOURLY -> listOf("세전·세후 예상", "명세서 대조", "추가 달력 2개째부터", "돈 통계", "광고 0")
        WorkStyle.FIELD -> listOf("급여(공수) 리포트 PDF·CSV", "세전·세후와 사업자 세금", "근무표 PDF(한 달·1년)", "매일 자동 백업", "광고 0")
        WorkStyle.BUSINESS -> listOf("사업자 올해 세금과 내는 날", "돈 통계(비교·분류·흐름)", "공유 링크 5개·1년", "매일 자동 백업", "광고 0")
        WorkStyle.INVEST -> listOf("돈 통계·올해 흐름", "매일 자동 백업", "추가 달력 2개째부터", "급여 리포트 PDF·CSV", "광고 0")
    }
}
