package com.bamnat.core.time

/**
 * 2.0.0 [RVH-06] 검토 CAL-09: one reading of a spoken hour ("저녁 7시", "밤 10시", "새벽 12시") for every one-line input, so the
 * calendar's 한 줄 입력 and the memo's 할 일 시각 (TASK-38) cannot read the same words two ways. Before, the calendar only moved
 * 오후 to the afternoon and put a 대근 written "저녁 7시" at 07:00.
 */
object KoreanHour {
    /**
     * The hour on a 24-hour clock for [h] said after [mark] (오전·오후·새벽·아침·낮·저녁·밤, or none), and 1 when it falls after
     * midnight ("밤 12시"); null when the two cannot go together ("오전 13시"). With no mark, [guessAfternoon] reads 1~6시 as the
     * afternoon (an appointment "3시" is most often 15:00).
     */
    fun of(mark: String?, h: Int, guessAfternoon: Boolean = false): Pair<Int, Long>? {
        if (h < 0 || h > 24) return null
        if (h == 24) return 0 to 1L
        return when (mark) {
            "오후", "저녁" -> (if (h in 1..11) h + 12 else h) to 0L
            "밤" -> when (h) {
                12 -> 0 to 1L
                in 6..11 -> h + 12 to 0L
                else -> h to 0L
            }
            "낮" -> (if (h in 1..6) h + 12 else h) to 0L
            "오전", "아침", "새벽" -> if (h > 12) null else (if (h == 12) 0 else h) to 0L
            else -> (if (guessAfternoon && h in 1..6) h + 12 else h) to 0L
        }
    }
}
