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
        // 2.0.0 [RVO-05] (검토 T11b-01) 투자에게 "급여 리포트"는 남의 일: 투자에서 실제로 쓰는 기능 작품으로.
        WorkStyle.INVEST -> listOf("돈 통계·올해 흐름", "매일 자동 백업", "추가 달력 2개째부터", "기능 작품(값 칸·단추 위젯)", "광고 0")
    }

    /**
     * 2.0.0 [RVO-05] (검토 U11b-01·02, PRO-30) 프로 화면 '프로 기능 모아보기'에서 먼저 보일 다섯 기능([com.bamnat.core.guide.FeatureRegistry] id):
     * [five]와 같은 뜻을 그 유형 말로. 모두 [com.bamnat.core.guide.ProCatalog]의 프로 기능이고 그 유형에 맞아요(ProPitchTest).
     */
    fun first(style: WorkStyle?): List<String> = when (style ?: WorkStyle.SHIFT) {
        WorkStyle.SHIFT -> listOf("pay.slipAudit", "stats.compare", "alarm.mission", "calendar.phone", "data.autoBackup")
        WorkStyle.DAY -> listOf("leave.payout", "calendar.phone", "pay.report", "data.autoBackup", "alarm.mission")
        WorkStyle.HOURLY -> listOf("money.takeHome", "pay.slipAudit", "calendars.multi", "money.stats", "data.autoBackup")
        WorkStyle.FIELD -> listOf("field.daysProof", "money.bizTax", "share.rosterPdf", "data.autoBackup", "field.statementPdf")
        WorkStyle.BUSINESS -> listOf("money.bizTax", "money.stats", "share.moreLinks", "data.autoBackup", "share.rosterPdf")
        WorkStyle.INVEST -> listOf("money.stats", "data.autoBackup", "calendars.multi", "recipe.fx", "look.wardrobe")
    }

    /**
     * 2.0.0 [RVO-05] (검토 PRO-12·30, 결정 13 추천 '표를 채워요') 옛 '무료와 프로' 표를 대신하는, 그 유형이 늘 무료로 쓰는 것 다섯 줄.
     * 줄마다 [com.bamnat.core.guide.ProCatalog]에 없는(무료인) 기능만 적고, 가게·투자에게는 월급·연차·조 같은 남의 말을 쓰지 않아요.
     */
    fun freeAlways(style: WorkStyle?): List<String> = when (style ?: WorkStyle.SHIFT) {
        WorkStyle.SHIFT -> listOf("근무표·패턴·빠른 입력·대근·연차", "알람·점점 커지는 소리·알람 점검·위젯·워치", "이번 달 예상 월급(수당·주휴·세금)",
            "모든 조 보기, 1년 달력, 주 52시간 점검", "가계부, 근무표 링크 1개, 백업 파일, 앱 잠금, 화면 꾸미기")
        WorkStyle.DAY -> listOf("근무·연차 기록, 지금 퇴근, 징검다리 연휴", "알람·점점 커지는 소리·알람 점검·위젯·워치", "이번 달 예상 월급(시간외·세금)",
            "근태 엑셀 가져오기·회사 근태와 비교, 주 52시간 점검", "가계부, 근무표 링크 1개, 백업 파일, 앱 잠금, 화면 꾸미기")
        WorkStyle.HOURLY -> listOf("출근·퇴근 버튼, 지각·조퇴·결근 한 번에", "주휴수당 주별 확인, 휴게시간 자동 차감", "두 알바 비교하기, 근로계약서 보관",
            "알람·점점 커지는 소리·알람 점검·위젯·워치", "가계부, 근무표 링크 1개, 백업 파일, 앱 잠금, 화면 꾸미기")
        WorkStyle.FIELD -> listOf("공수 버튼·일감 상태, 입금 약속일과 미수 알림", "거래내역서 보내기·견적서·영수증 사진", "연간 3.3%·경비 합계와 출역일",
            "알람·점점 커지는 소리·알람 점검·위젯·워치", "가계부, 일정 링크 1개, 백업 파일, 앱 잠금, 화면 꾸미기")
        WorkStyle.BUSINESS -> listOf("영업일·정기휴무, 하루 마감", "결제 수단별 매출·카드 입금 예정·목표 매출", "현금 시재 맞추기·직원 인건비",
            "알람·점점 커지는 소리·알람 점검·위젯·워치", "가계부, 영업일 링크 1개, 백업 파일, 앱 잠금, 화면 꾸미기")
        WorkStyle.INVEST -> listOf("원금 입출금·매매 기록·현재가 적기", "수익률 세 가지·총자산 흐름·일·월·연 손익", "투자일지·투자 알람·배당·이자 기록",
            "계좌별 보기·금액 가리기·투자 기록 CSV", "가계부, 일정 링크 1개, 백업 파일, 앱 잠금, 화면 꾸미기")
    }
}
