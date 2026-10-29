package com.bamnat.core.recipe

/**
 * [RCP-17][RCP-23] What a piece holds, in one line the user reads (만들기 1단계, 레시피 보기의 조각 목록, 미리 보기): "근무 4개:
 * 주🌞 야🌙 비 휴", "알람 3개 더하기 · 계산 미션". Values the receiver fills in read "받는 사람이 정해요".
 */
object RecipeDescribe {
    private const val YOURS = "받는 사람이 정해요"

    private val DAY_NAMES = mapOf(
        "MONDAY" to "월", "TUESDAY" to "화", "WEDNESDAY" to "수", "THURSDAY" to "목", "FRIDAY" to "금", "SATURDAY" to "토", "SUNDAY" to "일",
    )
    private val MISSIONS = mapOf("none" to "미션 없음", "math" to "계산 미션", "shake" to "흔들기 미션")
    private val CELLS = mapOf("fill" to "칸 채우기", "tint" to "옅게 칠하기", "dot" to "점만")
    private val MODES = mapOf("DAY_RATE" to "일당(공수)형", "PER_JOB" to "건별형", "PLATFORM" to "플랫폼형")
    private val BREAKS = mapOf("UNPAID" to "휴게 무급", "PAID" to "휴게 유급", "AS_WORK" to "휴게도 근무시간")
    private val TAXES = mapOf("NONE" to "세금 없이", "WITHHOLDING_3_3" to "3.3% 떼기", "INSURANCE_4" to "4대보험")

    @Suppress("UNCHECKED_CAST")
    private fun items(p: Piece, key: String): List<Map<String, Any?>> = p.values[key] as? List<Map<String, Any?>> ?: emptyList()

    @Suppress("UNCHECKED_CAST")
    private fun texts(p: Piece, key: String): List<String> = p.values[key] as? List<String> ?: emptyList()

    private fun won(v: Any?, amounts: Boolean): String = when (v) {
        is Long -> if (amounts) "%,d원".format(v) else "정해 둔 금액"
        is Ref.Input -> YOURS
        else -> "그대로"
    }

    /** [p] in one line; [amounts] false writes "정해 둔 금액" for a fixed amount (for text that leaves the phone). */
    fun line(p: Piece, amounts: Boolean = true): String = when (p.kind) {
        PieceKind.SHIFT_LOOK -> items(p, "items").let { l ->
            "근무 ${l.size}개: " + l.joinToString(" ") { (it["label"] as? String ?: "") + (it["emoji"] as? String ?: "") }
        }
        PieceKind.CAL_THEME -> listOfNotNull(
            "오늘".takeIf { p.values["today"] != null }, "토요일".takeIf { p.values["sat"] != null },
            "일요일".takeIf { p.values["sun"] != null }, "공휴일".takeIf { p.values["holiday"] != null },
        ).joinToString("·").let { if (it.isEmpty()) "" else "$it 색" } +
            ((p.values["cell"] as? String)?.let { " · " + CELLS[it] } ?: "")
        PieceKind.ALARMS -> {
            val n = items(p, "byRole").sumOf { (it["before"] as? List<*>)?.size ?: 0 }
            listOfNotNull(
                "알람 ${n}개 더하기".takeIf { n > 0 },
                (p.values["snooze"] as? Long)?.let { "다시 울림 ${it}분" },
                (p.values["mission"] as? String)?.let { MISSIONS[it] },
            ).joinToString(" · ").ifEmpty { "알람 설정" }
        }
        PieceKind.MEMO_TEMPLATES -> items(p, "items").let { l -> "메모 틀 ${l.size}개: " + l.take(3).joinToString(", ") { it["title"] as? String ?: "" } }
        PieceKind.HOURLY_RULES -> listOfNotNull(
            "시급 " + won(p.values["wage"], amounts),
            (p.values["weeklyPay"] as? Boolean)?.let { if (it) "주휴수당 받음" else "주휴수당 없음" },
            (p.values["five"] as? Boolean)?.let { if (it) "5인 이상" else "5인 미만" },
            (p.values["breakPay"] as? String)?.let { BREAKS[it] },
            (p.values["tax"] as? String)?.let { TAXES[it] },
        ).joinToString(" · ")
        PieceKind.FIELD_SHEET -> listOfNotNull(
            MODES[p.values["mode"]],
            p.values["dayRate"]?.let { "일당 " + won(it, amounts) },
            texts(p, "kinds").takeIf { it.isNotEmpty() }?.let { "작업 ${it.size}가지" },
            texts(p, "costs").takeIf { it.isNotEmpty() }?.let { "경비 항목 ${it.size}개" },
        ).joinToString(" · ")
        PieceKind.SHOP -> listOfNotNull(
            texts(p, "open").takeIf { it.isNotEmpty() }?.let { d -> "영업 " + d.joinToString("") { DAY_NAMES[it] ?: "" } },
            texts(p, "closing").takeIf { it.isNotEmpty() }?.let { "마감 체크 ${it.size}개" },
            items(p, "cardFees").takeIf { it.isNotEmpty() }?.let { "카드 수수료 ${it.size}곳" },
            texts(p, "costs").takeIf { it.isNotEmpty() }?.let { "경비 분류 ${it.size}개" },
        ).joinToString(" · ")
        PieceKind.INVEST -> "원칙 ${items(p, "rules").size}개" + texts(p, "journal").takeIf { it.isNotEmpty() }?.let { " · 일지 칸 ${it.size}개" }.orEmpty()
        PieceKind.COMPANY -> "근무 주기와 급여 방식 (회사 설정 파일)"
    }

    /** The receiver's own words for the kinds of work a recipe fits. */
    fun styles(r: Recipe): String = com.bamnat.core.work.WorkStyle.entries.filter { it in r.styles }.joinToString("·") { short(it) }

    fun short(s: com.bamnat.core.work.WorkStyle): String = when (s) {
        com.bamnat.core.work.WorkStyle.SHIFT -> "교대"
        com.bamnat.core.work.WorkStyle.DAY -> "일근"
        com.bamnat.core.work.WorkStyle.HOURLY -> "알바"
        com.bamnat.core.work.WorkStyle.FIELD -> "현장"
        com.bamnat.core.work.WorkStyle.BUSINESS -> "가게"
        com.bamnat.core.work.WorkStyle.INVEST -> "투자"
    }

    /** "적용하면 바뀌는 것", written by the app for the 설명 틀 (만들기 4단계). */
    fun changes(r: Recipe): String = r.pieces.joinToString("\n") { "• ${it.kind.label}: ${line(it, amounts = false)}" }
}
