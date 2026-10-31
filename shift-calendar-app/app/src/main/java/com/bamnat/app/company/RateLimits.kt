package com.bamnat.app.company

import com.bamnat.app.ui.Fmt

/**
 * [LIM-03] 1.31 I5 (상세리뷰 I5): one way to say "too many for now" everywhere. Before 1.31 seven screens said it seven ways
 * ("오늘은 더 보낼 수 없어요", "잠시 뒤 다시", "한 시간쯤 뒤") whatever the limit was. Now the words say why (this phone did it
 * often, or many phones on the same Wi-Fi) and when it goes again, from the server's `retryAfter` when it sent one, and
 * what can be done now (on a shared Wi-Fi, mobile data goes at once).
 */
object RateLimits {
    /**
     * [what] is what could not be done ("문의 보내기", "글쓰기"), or null for a read; [retryAfter] the seconds the server said;
     * [why] its reason word ("device", "network", "reads"); [instead] one more thing the user can do now.
     */
    fun text(what: String? = null, retryAfter: Int? = null, why: String? = null, instead: String? = null): String {
        val head = what?.let { "지금은 $it${Fmt.obj(it)} 잠시 할 수 없어요." } ?: "요청이 많아 잠시 쉬고 있어요."
        val reason = when (why) {
            "network" -> " 같은 와이파이(회사·병원 망)에서 많이 써서 그래요. 모바일 데이터로 바꾸면 바로 될 수 있어요."
            "device" -> " 이 휴대폰에서 짧은 사이에 여러 번 했어요."
            "reads" -> " 오늘 많이 열어 보셔서 잠깐 쉬어요."
            else -> ""
        }
        val tail = " ${retryAfter?.let(::wait) ?: "잠시 뒤"} 다시 해 주세요." + (instead?.let { " $it" } ?: "")
        return head + reason + tail
    }

    /**
     * [seconds] as the user reads a wait: about a minute, some minutes, some hours, or past that the hour it opens
     * ("내일 오후 11시쯤"). 1.36 시험: a day's limit counts 24 hours from the first use, not the calendar day, so a bare
     * "내일" sent someone who posted late at night back too early the next morning.
     */
    fun wait(seconds: Int, now: java.time.ZonedDateTime = java.time.Instant.ofEpochMilli(com.bamnat.core.time.Today.millis())
        .atZone(java.time.ZoneId.systemDefault())): String = when {
        seconds < 90 -> "1분쯤 뒤"
        seconds < 3_600 -> "${(seconds + 59) / 60}분쯤 뒤"
        seconds < 6 * 3_600 -> "${(seconds + 3_599) / 3_600}시간쯤 뒤"
        else -> {
            val at = now.plusSeconds(seconds.toLong() + 3_599).withMinute(0)
            val day = when (java.time.temporal.ChronoUnit.DAYS.between(now.toLocalDate(), at.toLocalDate())) { 0L -> "오늘"; 1L -> "내일"; else -> "모레" }
            val h = at.hour
            "$day ${if (h < 12) "오전" else "오후"} ${if (h % 12 == 0) 12 else h % 12}시쯤"
        }
    }

    /** [text] for a refusal from the company server. */
    fun of(e: CompanyException, what: String? = null, instead: String? = null): String = text(what, e.retryAfter, e.why, instead)
}
