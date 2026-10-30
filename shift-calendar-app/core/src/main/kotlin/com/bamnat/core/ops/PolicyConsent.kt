package com.bamnat.core.ops

import java.time.LocalDate

/**
 * [PRIV-03] 처리방침 시행일을 정식 출시일로 앞당기며 지키는 약속(결정 D84 3.1, 결정 D111 3.9).
 *
 * 3.1(결정 D84, 2026-10-26 사용자): 2.5~3.0이 알린 11-21·11-24 시행을 [LAUNCH_EFFECTIVE](11-10)로 앞당겼어요. 3.1을
 * 게시하기 전에 깐 휴대폰이 동의하지 않으면 [LAUNCH_HELD_UNTIL]까지 결제·광고를 켜지 않아요.
 *
 * 3.9(결정 D111, 2026-10-30 사용자 "출시일 전에 개발한 모든 사항은 처리방침을 출시일로, 출시 이후에는 정상적으로"):
 * 3.2~3.8이 30일 고지로 알린 11-27~11-29 시행을 같은 정식 출시일([EFFECTIVE])로 앞당겨요. 3.9를 게시하기 전에 깐
 * 휴대폰(테스터)에는 달력 위 카드로 한 번 묻고, 동의하지 않은 휴대폰은 [HELD_UNTIL](3.2~3.8 중 가장 늦은 날)까지
 * 3.2~3.8 기능을 켜지 않아요. 게시한 뒤 깐 휴대폰은 설치할 때 3.9를 보고 시작하니 묻지 않아요. 3.9에 동의하면 3.1도
 * 동의한 것으로 쳐요(`PolicyCalendar.agreedAtLeast`). 정식 출시 뒤 개정은 처리방침 9항대로 7일·30일 전에 알려요.
 * 근거: `/mnt/project-files/legal/처리방침-시행일-앞당기기-검토.md`.
 */
object PolicyConsent {
    /** The version the calendar card asks for (결정 D111). */
    const val VERSION = "3.9"
    /** 3.9 게시 예정일. 늦어지면 관리자 앱 › 기능 관리 › 일정 › 처리방침 일정에서 옮겨요(앱 업데이트 없이). */
    val POSTED: LocalDate = LocalDate.of(2026, 10, 31)
    /** 정식 출시(Play 프로덕션 승인) 예정일, 3.1과 같은 날. */
    val EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 10)
    /** 3.2~3.8이 약속한 가장 늦은 시행일. 이날부터는 동의가 없어도 원래 약속대로 시행돼요. */
    val HELD_UNTIL: LocalDate = LocalDate.of(2026, 11, 29)

    /** 결제·광고의 처리방침(결정 D84). */
    const val LAUNCH_VERSION = "3.1"
    /** 3.1 게시(1.27.0을 main에 합친 날 기준). 이날 전에 깐 휴대폰에 물어요. */
    val LAUNCH_POSTED: LocalDate = LocalDate.of(2026, 10, 27)
    val LAUNCH_EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 10)
    /** 3.0이 약속한 시행일. 이날부터는 동의가 없어도 원래 약속대로 시행돼요. */
    val LAUNCH_HELD_UNTIL: LocalDate = LocalDate.of(2026, 11, 24)

    /** 3.1's ask (1.27): from the day 3.1 is posted until the old date, installed before it (or unknown), no yes yet. */
    fun asks(installed: LocalDate?, agreed: Boolean, today: LocalDate): Boolean =
        !agreed && today >= LAUNCH_POSTED && today < LAUNCH_HELD_UNTIL && (installed == null || installed < LAUNCH_POSTED)

    /** The 결제·광고 switch waits on this phone while 3.1 would still be asked. */
    fun holdsLaunch(installed: LocalDate?, agreed: Boolean, today: LocalDate): Boolean = asks(installed, agreed, today)

    val lines: List<String> = listOf(
        "정식 출시일(11월 10일 예정)부터 처리방침 3.9·이용약관이 시행돼요(원래 11월 24~29일)",
        "그날부터 레시피 올리기·좋아요·이야기·구독, 동료 링크에 회사 설정 담기, 문의에 멈춤 관찰 기록 붙이기, 회사 공통 수당 금액 나누기, ‘사진이 달라요’ 횟수, 따로 동의한 분의 통계 더 보내기가 시작돼요",
        "프로·광고는 Play 정식 출시가 승인된 날부터 켜져요. 그 전엔 지금처럼 모두 무료예요",
    )
}
