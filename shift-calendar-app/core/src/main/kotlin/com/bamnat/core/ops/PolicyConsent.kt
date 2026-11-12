package com.bamnat.core.ops

import java.time.LocalDate

/**
 * [PRIV-03] 처리방침 시행일을 정식 출시일로 앞당기며 지키는 약속(결정 D84 3.1, 결정 D111 3.9).
 *
 * 3.1(결정 D84, 2026-10-26 사용자): 2.5~3.0이 알린 11-21·11-24 시행을 [LAUNCH_EFFECTIVE](11-12, 1.38에서 11-10을 옮김)로 앞당겼어요. 3.1을
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
    /**
     * 정식 출시(Play 프로덕션 승인) 뒤 출시 전 기능을 푸는 기본일, 3.1과 같은 날. 1.38(사용자 2026-11-05): 11-10 → 11-12. 게시된 3.1·3.9·3.10
     * 페이지의 시행일(11-10)보다 늦으니 약속을 어기지 않아요. 기능을 푸는 날은 관리자 앱 › 기능 관리 › 일정 › 처리방침 일정에서 옮겨요
     * (앱 업데이트 없이). 처리방침 개정은 1.38 배포 때 한 판으로 해요(사용자 2026-11-05 "1.38배포할때 처리방침 바꾸자").
     */
    val EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 12)
    /** 3.2~3.8이 약속한 가장 늦은 시행일. 이날부터는 동의가 없어도 원래 약속대로 시행돼요. */
    val HELD_UNTIL: LocalDate = LocalDate.of(2026, 11, 29)

    /**
     * 1.37 [SHR-31][SHR-32] 3.11(사용자 2026-11-04 "11월10일로 맞춰서", 1.38에서 함께 11-12로): 근무표 링크의 테마·근무 설정(1항). 정식 출시 전에 게시하니 결정 D111대로
     * 시행일은 정식 출시일([EFFECTIVE])이고, 3.11을 게시하기 전에 깐 휴대폰은 달력 위 카드로 한 번 묻고, 동의하지 않으면 30일
     * 고지가 끝나는 [SHARE_LOOK_HELD_UNTIL]부터예요. 카드는 가장 새 판 [ASK_VERSION]을 물어요(동의하면 3.9·3.1도 동의한 것으로 쳐요).
     */
    const val SHARE_LOOK_VERSION = "3.11"
    val SHARE_LOOK_POSTED: LocalDate = LocalDate.of(2026, 11, 7)
    val SHARE_LOOK_HELD_UNTIL: LocalDate = LocalDate.of(2026, 12, 7)
    /**
     * 1.38 (사용자 2026-11-07 "처리방침은 오늘 기준으로 작성해줘 오늘 정식 출시가 됬으니깐 아직 사용자가 다운 받지 않았을때 해야해"):
     * 3.11은 1.37·1.38의 모든 새 항목(테마·근무 설정, 팀·가게 링크, 지금 자는 중 등)을 한 판으로 담아 2026-11-07에 게시하고 같은 날 시행해요.
     * 게시 전에 깐 휴대폰은 달력 위 카드로 한 번 묻고, 동의하지 않으면 30일 고지가 끝나는 [SHARE_LOOK_HELD_UNTIL](12-07)부터예요.
     * 게시가 늦어지면 관리자 앱 › 기능 관리 › 일정 › 처리방침 일정에서 세 날을 함께 옮겨요(앱 업데이트 없이).
     */
    val SHARE_LOOK_EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 7)
    /**
     * 1.39.1 [POLC-01] 3.12(사용자 2026-11-08 카드 '새 폰 오늘, 쓰던 폰 12-09'): 1.39.0과 함께 2026-11-09에 게시·시행. 그 전에 깐 휴대폰은
     * 달력 위 카드로 한 번 묻고, 동의하지 않으면 30일 뒤 [SHARE_PEOPLE_HELD_UNTIL](12-09)부터예요. 1.39.0은 카드가 3.11을 물어서 쓰던 폰이
     * 3.12에 동의할 길이 없었어요. 3.12 날짜는 서버(관리자 앱 › 기능 관리 › 일정, 11-08 저장)에서 오고 앱 SEED에는 넣지 않아요
     * (넣으면 서버 기본 일정도 바뀌어 3.12를 아직 안 정한 시험 서버의 깃발이 달라져요). 아래 날은 같은 날을 적어 둔 것이에요.
     */
    const val PEOPLE_VERSION = "3.12"
    val PEOPLE_POSTED: LocalDate = LocalDate.of(2026, 11, 9)
    val PEOPLE_EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 9)
    val SHARE_PEOPLE_HELD_UNTIL: LocalDate = LocalDate.of(2026, 12, 9)

    /**
     * 2.0.0 [RVO-08] 처리방침 3.13·이용약관 1.4 (사용자 2026-11-12 18:28 KST "게시해", 범위는 카드 '준비된 줄 먼저, 나머지는 3.14'):
     * 정식 출시 뒤 첫 개정이라 동의 카드 없이 9항대로 앱 공지와 페이지로 먼저 알리고, 사실 바로잡기·받는 것 그대로인 줄은 7일 뒤
     * ([REVISION_EFFECTIVE]), 이용자에게 불리할 수 있는 줄(보상 확인 뒤 지급·1년 12개월 상한, 사용 통계 4가지, 약관 1.4)은 30일 뒤
     * ([REVISION_EFFECTIVE_30])에 시행해요. 3.13은 [ASKED_VERSIONS]에 넣지 않아요(3.12에 '동의'해도 3.13 날짜는 앞당겨지지 않음).
     * 게시일은 이 한 줄만 바꿔요: `python3 tools/legal_dates.py <게시일>`이 이 줄과 privacy·terms의 날짜를 함께 고쳐요.
     * 서버는 관리자 앱 › 기능 관리 › 일정 › 처리방침 일정의 3.13(시행일 = [REVISION_EFFECTIVE_30])을 읽어요(`stats.disc`, 보상 상한).
     * SEED에는 넣지 않아요(3.12와 같은 까닭: 서버 기본 일정이 바뀌어 일정을 안 정한 시험 서버의 깃발이 달라져요).
     */
    const val REVISION_VERSION = "3.13"
    val REVISION_POSTED: LocalDate = LocalDate.of(2026, 11, 13)
    /** 7일 줄: 체험 3일 전 알림, 생체 인증 설명, 백업·글꼴 바로잡기, 장터 카드 글꼴(앱 2.0.0, 갈래 M은 이날 전에는 받지 않음), 코드 넣는 곳. */
    val REVISION_EFFECTIVE: LocalDate = REVISION_POSTED.plusDays(7)
    /** 30일 줄: 1-7·1-12항 보상 확인 뒤 지급·1년 12개월 상한, 1-9항 사용 통계 4가지(`stats.disc`), 이용약관 1.4 전부. */
    val REVISION_EFFECTIVE_30: LocalDate = REVISION_POSTED.plusDays(30)
    /** 이용약관 판, 처리방침 3.13과 함께 게시. 설정 › 앱 정보의 줄과 페이지 머리글이 같아요(LegalPagesTest). */
    const val TERMS_VERSION = "1.4"

    /** The newest version the calendar card asks for and a yes writes (a yes to it counts for every older one). */
    const val ASK_VERSION = PEOPLE_VERSION

    /** Every version the card may still be waiting on, newest first. */
    val ASKED_VERSIONS: List<String> = listOf(PEOPLE_VERSION, SHARE_LOOK_VERSION, VERSION, LAUNCH_VERSION)

    /** 결제·광고의 처리방침(결정 D84). */
    const val LAUNCH_VERSION = "3.1"
    /** 3.1 게시(1.27.0을 main에 합친 날 기준). 이날 전에 깐 휴대폰에 물어요. */
    val LAUNCH_POSTED: LocalDate = LocalDate.of(2026, 10, 27)
    val LAUNCH_EFFECTIVE: LocalDate = LocalDate.of(2026, 11, 12)
    /** 3.0이 약속한 시행일. 이날부터는 동의가 없어도 원래 약속대로 시행돼요. */
    val LAUNCH_HELD_UNTIL: LocalDate = LocalDate.of(2026, 11, 24)

    /** 3.1's ask (1.27): from the day 3.1 is posted until the old date, installed before it (or unknown), no yes yet. */
    fun asks(installed: LocalDate?, agreed: Boolean, today: LocalDate): Boolean =
        !agreed && today >= LAUNCH_POSTED && today < LAUNCH_HELD_UNTIL && (installed == null || installed < LAUNCH_POSTED)

    /** The 결제·광고 switch waits on this phone while 3.1 would still be asked. */
    fun holdsLaunch(installed: LocalDate?, agreed: Boolean, today: LocalDate): Boolean = asks(installed, agreed, today)

    /** 1.39.1 [POLC-01]: what the card says now (3.12; a yes covers 3.11·3.9·3.1 too, all already in effect for new phones). */
    val lines: List<String> = listOf(
        "11월 9일부터 처리방침 3.12로 바뀌었어요. 동의하시면 아래 기능을 지금부터 써요",
        "근무표 링크에 계획한 잠 시간 담기, 광장 근무 태그·오늘 출근·근무 카드, 내 광장 질문에 답이 오면 바로 알림, 회사 설정 보완 요청, 휴대폰 달력 일정 겹쳐 보기(켤 때만)",
        "사용 통계를 켠 분만: 첫 설정에서 누른 횟수 구간, 다시 연 날(1·7·30일째) 같은 몇 가지(무엇을 눌렀는지·시각은 보내지 않아요)",
    )
    /** Shown too while 3.11 is still waiting on this phone (installed before 11-07, before 12-07). */
    const val OLDER_LINE = "11월 7일에 바뀐 3.11(근무표 링크의 테마·근무 설정, 팀·가게 링크의 이름, 지금 자는 중 나누기)도 함께 동의돼요"
}
