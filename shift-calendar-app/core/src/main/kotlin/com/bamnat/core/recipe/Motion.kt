package com.bamnat.core.recipe

/**
 * [MAT-07] 1.36 움직임 (873, 2차 제안 20, Keynote 매직 무브): how the app moves — the month turning ([TURNS]), today's cell drawing the eye
 * ([TODAYS]), a look being put on ([WEARS]) and how fast ([SPEEDS]). [off] stops all of it, and so does the phone's own 'remove
 * animations' (애니메이션 삭제·배율 0): the app checks that every time and never moves against it. Written "tn:slide|td:pulse|wr:wipe|sp:n|of:1".
 */
data class Motion(
    val turn: String? = null,
    val today: String? = null,
    val wear: String? = null,
    val speed: String? = null,
    val off: Boolean = false,
) {
    val isDefault: Boolean get() = this == Motion()

    fun encode(): String = listOfNotNull(
        turn?.let { "tn:$it" }, today?.let { "td:$it" }, wear?.let { "wr:$it" }, speed?.let { "sp:$it" }, "of:1".takeIf { off },
    ).joinToString("|")

    /** How long a move takes, in ms, at [speed] ([base] the app's own). */
    fun millis(base: Int): Int = when (speed) { "s" -> base * 3 / 2; "f" -> base * 2 / 3; else -> base }

    /** What is drawn when [systemOff] (the phone's own setting) or [off]: nothing moves. */
    fun effective(systemOff: Boolean): Motion = if (systemOff || off) Motion(turn = "none", today = "none", wear = "none", off = true) else this

    companion object {
        /** slide: the app's own; fade; flip (a page turning); zoom; stack (the new month comes over the old); none. */
        val TURNS = listOf("slide", "fade", "flip", "zoom", "stack", "none")
        val TURN_NAMES = mapOf("slide" to "밀기", "fade" to "스르륵", "flip" to "책장 넘기기", "zoom" to "다가오기", "stack" to "겹쳐 오기", "none" to "움직이지 않기")
        /** none: the app's own still ring; pulse: grows a little and back; glow: a soft halo breathing; ring: a ring going round; bounce. */
        val TODAYS = listOf("none", "pulse", "glow", "ring", "bounce")
        val TODAY_NAMES = mapOf("none" to "가만히", "pulse" to "숨쉬기", "glow" to "은은한 빛", "ring" to "도는 테", "bounce" to "통통")
        val WEARS = listOf("fade", "wipe", "circle", "none")
        val WEAR_NAMES = mapOf("fade" to "스르륵", "wipe" to "닦아 내기", "circle" to "동그랗게 퍼지기", "none" to "바로")
        val SPEEDS = listOf("s", "n", "f")
        val SPEED_NAMES = mapOf("s" to "천천히", "n" to "보통", "f" to "빠르게")

        private val PART = Regex("^(tn:(${TURNS.joinToString("|")})|td:(${TODAYS.joinToString("|")})|wr:(${WEARS.joinToString("|")})|sp:[snf]|of:1)$")

        fun problem(s: String): String? {
            if (s.isEmpty()) return null
            val parts = s.split('|')
            if (!parts.all { PART.matches(it) }) return "motion"
            val keys = parts.map { it.substringBefore(':') }
            return if (keys.size != keys.distinct().size) "motion" else null
        }

        fun decode(s: String?): Motion {
            if (s.isNullOrEmpty() || problem(s) != null) return Motion()
            val m = s.split('|').associate { it.substringBefore(':') to it.substringAfter(':') }
            return Motion(m["tn"], m["td"], m["wr"], m["sp"], m["of"] == "1")
        }
    }
}
