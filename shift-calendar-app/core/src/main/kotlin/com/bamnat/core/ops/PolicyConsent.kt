package com.bamnat.core.ops

import java.time.LocalDate

/**
 * [PRIV-03] 처리방침 3.1·이용약관 시행일을 프로덕션 승인 예정일로 앞당기며(결정 D84, 2026-10-26 사용자) 지키는 약속.
 *
 * 2.5~3.0은 '이용자에게 불리한 변경은 30일 전'(처리방침 9항)대로 2026-11-21·11-24 시행을 알렸어요. 3.1은 시행을
 * [EFFECTIVE](11-10)로 앞당기므로, 3.1을 게시하기 전에 앱을 깐 휴대폰(테스터)에는 앱에서 한 번 따로 묻고, 동의하지
 * 않은 휴대폰은 원래 시행일([HELD_UNTIL]) 전까지 정식 출시 스위치(결제·광고)를 켜지 않아요. 게시한 뒤 깐 휴대폰은
 * 설치할 때 3.1을 보고 시작하니 묻지 않아요. 근거: 비공개 `research/1.27-고지기간-시행일-검토.md`.
 */
object PolicyConsent {
    const val VERSION = "3.1"
    /** 3.1 게시(1.27.0을 main에 합친 날 기준). 이날 전에 깐 휴대폰에 물어요. */
    val POSTED: LocalDate = LocalDate.of(2026, 10, 27)
    val EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 10)
    /** 3.0이 약속한 시행일. 이날부터는 동의가 없어도 원래 약속대로 시행돼요. */
    val HELD_UNTIL: LocalDate = LocalDate.of(2026, 11, 24)

    /** Asks this phone: from the day 3.1 is posted until the old date, installed before it (or unknown), no yes yet. */
    fun asks(installed: LocalDate?, agreed: Boolean, today: LocalDate): Boolean =
        !agreed && today >= POSTED && today < HELD_UNTIL && (installed == null || installed < POSTED)

    /** The 정식 출시 switch waits on this phone while it would still be asked. */
    fun holdsLaunch(installed: LocalDate?, agreed: Boolean, today: LocalDate): Boolean = asks(installed, agreed, today)

    val lines: List<String> = listOf(
        "처리방침 3.1·이용약관이 11월 10일부터 시행돼요(원래 11월 21·24일)",
        "프로·광고는 Play 정식 출시가 승인된 날부터 켜져요. 그 전엔 지금처럼 모두 무료예요",
        "1.27의 새 기능(친구 초대 자동 인식, 회사 정보 광장, 맞아요·달라요 의견)이 더해졌어요",
    )
}
