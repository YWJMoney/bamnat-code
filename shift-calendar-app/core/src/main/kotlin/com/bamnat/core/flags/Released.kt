package com.bamnat.core.flags

import java.time.Instant
import java.time.LocalDate

/**
 * 2.0.0 [RVO-12] 검토 HQ-46 (사용자 2026-11-12 20:27 KST "정식출시 전이라 무료라고 되어 있는데 이제 정식 출시가 됬어 프로는 무료가 아니야",
 * 20:32 KST "테스트는 모든 기능을 기반으로 동작해야하므로 유지하고 실 배포 버젼만 정상적으로 나오면돼"): what a released app (배포판) knows
 * without asking the server. 밤낮 went on sale at [AT], the moment the real server wrote down when 결제 first came on (11-12 00:17 KST), so
 * a release build reads:
 * - 처리방침 dates up to [DAY] as past for its LEGAL rows, even before the server's clock is known (3.1's 11-12 included);
 * - Play's two checks ([CHECKS]) as done when the server has not said anything about them yet;
 * - [AT] and [THANKS_MONTHS] for the 감사 프로 of a phone installed before it, until the server sends its own.
 *
 * What the server says still wins: its 강제 끔, its own checks (an unchecked one stays unchecked), its launchedAt and months. 처리방침
 * 3.1's hold stays as promised (9항): a phone installed before 10-27 that did not say yes is free until 11-24, and while the server's
 * date is not known such a phone stays free. Debug builds, the local test server and every unit and screenshot test leave this out
 * (모든 기능이 열린 채로): only `PhoneFacts.knownDay`·`knownChecks` set from a release build read it.
 */
object Released {
    /** The real server's `launched_at` (관리자 앱 › 기능 관리 › 결제, read 11-12 20:28 KST from the public `/api/config`). */
    val AT: Instant = Instant.parse("2026-11-11T15:17:42.082Z")

    /** [AT]'s day in Korea: 2026-11-12, 처리방침 3.1's 시행일 too. */
    val DAY: LocalDate = AT.atZone(KST).toLocalDate()

    /** The months the server gives a phone installed before [AT] (company-worker `MONEY.thanksMonths`, the real server's value). */
    const val THANKS_MONTHS = 1

    /** The two Play facts the developer confirmed on 11-06 (the real server's 기능 관리 › 확인할 것). */
    val CHECKS: Map<String, Instant> = LocalDate.of(2026, 11, 6).atStartOfDay(KST).toInstant().let { at ->
        mapOf("play.production" to at, "play.subscription" to at)
    }
}
