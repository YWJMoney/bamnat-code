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
        is Long -> if (amounts) RecipeNumbers.grouped(v) + "원" else "정해 둔 금액"
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
            listOfNotNull(p.values["opens"] as? String, p.values["closes"] as? String).takeIf { it.size == 2 }?.joinToString("~"),
            (p.values["cardFee"] as? Number)?.let { "카드 수수료 ${it}%" },
            texts(p, "closing").takeIf { it.isNotEmpty() }?.let { "마감 체크 ${it.size}개" },
            texts(p, "costs").takeIf { it.isNotEmpty() }?.let { "경비 분류 ${it.size}개" },
        ).joinToString(" · ")
        PieceKind.INVEST -> "원칙 ${items(p, "rules").size}개" + texts(p, "journal").takeIf { it.isNotEmpty() }?.let { " · 일지 칸 ${it.size}개" }.orEmpty()
        PieceKind.COMPANY -> "근무 주기와 급여 방식 (회사 설정 파일)"
        PieceKind.BUNDLE -> "레시피 ${items(p, "items").size}개 묶음"
        PieceKind.LAYOUT -> listOfNotNull(
            texts(p, "tabs").takeIf { it.isNotEmpty() }?.let { t -> "탭 순서 " + t.joinToString("·") { TABS[it] ?: it } },
            (p.values["calendar"] as? String)?.let { "달력 화면 카드 ${parts(it)}" },
        ).joinToString(" · ").ifEmpty { "화면 배치" }
        PieceKind.CARDS -> listOfNotNull(
            (p.values["stats"] as? String)?.let { "통계 카드 ${parts(it)}" },
            (p.values["money"] as? String)?.let { "가계부 카드 ${parts(it)}" },
            (p.values["memo"] as? String)?.let { "메모 카드 ${parts(it)}" },
        ).joinToString(" · ").ifEmpty { "카드 순서" }
        PieceKind.WIDGET -> listOfNotNull(
            (p.values["alpha"] as? Number)?.let { "불투명 ${it}%" },
            (p.values["theme"] as? String)?.let { mapOf("system" to "휴대폰 화면 따라", "light" to "밝게", "dark" to "어둡게")[it] },
            (p.values["memo"] as? Boolean)?.let { if (it) "메모 한 줄 보임" else "메모 숨김" },
        ).joinToString(" · ").ifEmpty { "위젯 모양" }
        PieceKind.PHOTO_LOOK -> listOfNotNull(
            "달력 배경".takeIf { p.values["bg"] != null }, "머리 그림".takeIf { p.values["head"] != null },
            items(p, "icons").size.takeIf { it > 0 }?.let { "근무 그림 ${it}개" }, "위젯 배경".takeIf { p.values["widget"] != null },
            (p.values["corners"] as? String)?.let { CORNER_NAMES[it] },
        ).joinToString(" · ").ifEmpty { "그림 꾸미기" }
        // [CVW-01]
        PieceKind.CAL_VIEW -> listOfNotNull(
            (p.values["size"] as? String)?.let { VIEW_NAMES["size.$it"] }, (p.values["badge"] as? String)?.let { VIEW_NAMES["badge.$it"] },
            (p.values["date"] as? String)?.let { VIEW_NAMES["date.$it"] }, (p.values["text"] as? String)?.let { VIEW_NAMES["text.$it"] },
            (p.values["grid"] as? String)?.let { VIEW_NAMES["grid.$it"] },
            (p.values["memo"] as? Number)?.toInt()?.let { if (it == 0) "칸에 메모 안 보임" else "칸에 메모 ${it}줄" },
            "근무 시간".takeIf { p.values["hours"] == true }, "음력".takeIf { p.values["lunar"] == true }, "그날 쓴 돈".takeIf { p.values["spend"] == true },
            (p.values["order"] as? String)?.takeIf { it.isNotEmpty() }?.let { o -> "순서 " + o.split(',').joinToString("→") { LINE_NAMES[it] ?: it } },
        ).joinToString(" · ").ifEmpty { "달력 모양" }
        // [CVW-07]
        PieceKind.APP_FEEL -> listOfNotNull(
            (p.values["theme"] as? String)?.let { RecipeWords.PICKS[it] }, "기기 색".takeIf { p.values["dynamic"] == true },
            "강조색".takeIf { p.values["accent"] != null }, (p.values["textStep"] as? Number)?.let { "글자 ${listOf("작게", "보통", "크게", "더 크게", "아주 크게").getOrElse(it.toInt()) { "" }}" },
            (p.values["compact"] as? Boolean)?.let { if (it) "촘촘하게" else "넉넉하게" }, "12시간제".takeIf { p.values["clock12"] == true },
            (p.values["tap"] as? String)?.let { "누르면 " + RecipeWords.PICKS[it] }, (p.values["hold"] as? String)?.let { "길게 누르면 " + RecipeWords.PICKS[it] },
            (p.values["dayOrder"] as? String)?.takeIf { it.isNotEmpty() }?.let { "하루 화면 순서" },
        ).joinToString(" · ").ifEmpty { "앱 동작" }
    }

    /** [CVW-01] Words for 달력 모양's choices, as the settings and the studio say them. */
    val VIEW_NAMES = mapOf(
        "size.short" to "칸 낮게", "size.normal" to "칸 보통", "size.tall" to "칸 높게", "size.huge" to "칸 아주 높게",
        "badge.big" to "근무 표시 크게", "badge.small" to "근무 표시 작게", "badge.line" to "근무는 색 띠로",
        "date.center" to "날짜 가운데", "date.left" to "날짜 왼쪽 위", "text.small" to "글씨 작게", "text.normal" to "글씨 보통", "text.large" to "글씨 크게",
        "grid.none" to "칸 선 없음", "grid.line" to "칸마다 선",
    )

    /** [CVW-01] What each line under a day's mark is. */
    val LINE_NAMES = mapOf("holiday" to "공휴일 이름", "memo" to "메모", "money" to "수입·매출", "hours" to "근무 시간", "spend" to "쓴 돈", "lunar" to "음력")

    val CORNER_NAMES = mapOf("round" to "모서리 둥글게", "soft" to "모서리 보통", "square" to "모서리 각지게")

    private val TABS = mapOf("calendar" to "달력", "money" to "가계부", "memos" to "메모", "stats" to "통계", "plaza" to "광장", "settings" to "설정")

    /** "보이는 카드 n개, 숨긴 카드 m개" of a layout line. */
    private fun parts(line: String): String {
        val bits = line.split(',').filter { it.isNotEmpty() }
        val hidden = bits.count { it.startsWith("-") }
        return "${bits.size - hidden}개 보임" + if (hidden > 0) ", ${hidden}개 숨김" else ""
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
