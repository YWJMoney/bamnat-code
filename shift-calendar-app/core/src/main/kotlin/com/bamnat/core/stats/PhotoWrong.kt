package com.bamnat.core.stats

/**
 * [PHOTO-09] 1.29 AQ: the screens that show what a photo was read as, each with a '사진이 달라요' button. The code goes to the
 * server as it is, so the server and the admin app name the same screens.
 */
enum class PhotoScreen(val code: String, val label: String) {
    ROSTER("roster", "근무표 사진"),
    PAYSLIP("payslip", "명세서 사진"),
    RULES("rules", "회사 규칙 사진"),
    PAY_TABLE("pay_table", "호봉표 사진"),
    INVEST("invest", "투자 캡처"),
    TAX("tax", "세금 서류 사진"),
    RECEIPT("receipt", "영수증 사진"),
    ;

    companion object {
        fun of(code: String): PhotoScreen? = entries.firstOrNull { it.code == code }
    }
}

/** [PHOTO-09] Why it was wrong, in a few fixed codes: never words the user wrote. */
enum class PhotoWrongReason(val code: String, val label: String) {
    NUMBERS("numbers", "숫자가 틀려요"),
    MISSING("missing", "빠진 칸이 있어요"),
    EXTRA("extra", "없는 칸이 생겼어요"),
    NAMES("names", "이름이나 종류가 틀려요"),
    NOTHING("nothing", "거의 읽지 못했어요"),
    /**
     * [PHOTO-28] 1.30 AV (R3 A-6 중): the 통상시급 or the 기준 시간 worked out from the payslip is not the company's. 1.30 works
     * them out from the photo ([com.bamnat.core.pay.SlipHourly]), and these are what tells whether it does so right.
     */
    HOURLY("hourly", "통상시급이 달라요"),
    HOURS("hours", "기준 시간이 달라요"),
    OTHER("other", "다른 점이 있어요"),
    ;

    companion object {
        fun of(code: String): PhotoWrongReason? = entries.firstOrNull { it.code == code }

        /** [PHOTO-28] The reasons a screen offers: 통상시급·기준 시간 only where a payslip or the company's rules were read. */
        fun of(screen: PhotoScreen): List<PhotoWrongReason> =
            if (screen == PhotoScreen.PAYSLIP || screen == PhotoScreen.RULES) entries else entries - HOURLY - HOURS
    }
}

/**
 * [PHOTO-09] '사진이 달라요' presses kept on the phone until they can go: per day, screen and reason, how many. No photo, no text,
 * no phone id, no time of day. Days are epoch days. What could not go (offline, or before 처리방침 3.3 lets it) waits, at most
 * [KEEP_DAYS] days.
 */
data class PhotoWrongBook(val counts: Map<Key, Int> = emptyMap()) {
    data class Key(val day: Long, val screen: PhotoScreen, val reason: PhotoWrongReason)

    val waiting: Int get() = counts.values.sum()

    fun record(screen: PhotoScreen, reason: PhotoWrongReason, day: Long): PhotoWrongBook {
        val k = Key(day, screen, reason)
        return PhotoWrongBook((counts + (k to ((counts[k] ?: 0) + 1).coerceAtMost(MAX_COUNT))).filterKeys { it.day > day - KEEP_DAYS })
    }

    /**
     * The body of `POST /api/photo-wrong`, or null with nothing to send: `{"v":1,"app":"1.29.0","counts":[{"day":"2026-10-27",
     * "screen":"payslip","reason":"numbers","n":2}]}`, at most [MAX_ROWS] rows, oldest first.
     */
    fun bundle(app: String, isoDay: (Long) -> String): String? {
        val rows = toSend()
        if (rows.isEmpty()) return null
        val sb = StringBuilder("{\"v\":1,\"app\":\"").append(app.filter { it.isDigit() || it == '.' }.take(20)).append("\",\"counts\":[")
        rows.forEachIndexed { i, (k, n) ->
            if (i > 0) sb.append(',')
            sb.append("{\"day\":\"").append(isoDay(k.day)).append("\",\"screen\":\"").append(k.screen.code)
                .append("\",\"reason\":\"").append(k.reason.code).append("\",\"n\":").append(n).append('}')
        }
        return sb.append("]}").toString()
    }

    /** The rows a bundle carries; pass them to [sent] after it went. */
    fun toSend(): List<Pair<Key, Int>> =
        counts.entries.sortedWith(compareBy({ it.key.day }, { it.key.screen.ordinal }, { it.key.reason.ordinal })).take(MAX_ROWS).map { it.key to it.value }

    /** After a send: what went comes off; presses made while it went stay. */
    fun sent(rowsSent: List<Pair<Key, Int>>): PhotoWrongBook {
        val left = counts.toMutableMap()
        rowsSent.forEach { (k, n) -> val v = (left[k] ?: 0) - n; if (v > 0) left[k] = v else left.remove(k) }
        return PhotoWrongBook(left)
    }

    fun encode(): String = counts.entries.sortedWith(compareBy({ it.key.day }, { it.key.screen.ordinal }, { it.key.reason.ordinal }))
        .joinToString("\n") { "${it.key.day}\t${it.key.screen.code}\t${it.key.reason.code}\t${it.value}" }

    companion object {
        const val KEEP_DAYS = 60L
        const val MAX_COUNT = 99
        const val MAX_ROWS = 60

        fun decode(s: String?): PhotoWrongBook = PhotoWrongBook(
            s.orEmpty().lines().mapNotNull { line ->
                val p = line.split('\t')
                if (p.size != 4) return@mapNotNull null
                val day = p[0].toLongOrNull() ?: return@mapNotNull null
                val screen = PhotoScreen.of(p[1]) ?: return@mapNotNull null
                val reason = PhotoWrongReason.of(p[2]) ?: return@mapNotNull null
                val n = p[3].toIntOrNull()?.takeIf { it > 0 } ?: return@mapNotNull null
                Key(day, screen, reason) to n.coerceAtMost(MAX_COUNT)
            }.toMap(),
        )
    }
}
