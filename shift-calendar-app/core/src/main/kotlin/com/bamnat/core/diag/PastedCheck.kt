package com.bamnat.core.diag

/**
 * 2.0.0 [RVO-28] HQ-150 (PC-201): 알람 점검 › '보고서 복사'로 복사한 보고서가 문의 글에 붙여 넣어졌는지, 그리고 운영팀 답이
 * 그 기록을 부탁했는지. 붙여 넣으면 2,000자를 넘어 보내기가 꺼지고(점검 기록은 6,000자까지 따로 가요), 운영자 화면에서도
 * 점검 기록이 아닌 글로 읽혀요. 그래서 앱은 붙여 넣은 부분을 알아보고 '점검 기록도 보내기'로 보내자고 말해요.
 */
object PastedCheck {
    /** 보고서의 칸 이름: 앱 DiagnosticsScreen의 report()와 같아요(앱 시험이 둘을 맞춰 봐요). */
    val SECTIONS = listOf("알람 상태", "권한", "기기", "최근 30일 사용량", "앱 멈춤", "앱 멈춤 관찰", "최근 이벤트")

    private val HEADER = Regex("""(?m)^[ \t]*\[([^\]\n]{1,24})][ \t]*\r?$""")

    /** 붙여 넣은 보고서가 시작하는 곳. 보고서 칸 머리가 둘 이상이어야 보고서로 봐요(글 속 '[기기]' 하나는 아니에요). */
    fun start(text: String): Int? {
        val heads = HEADER.findAll(text).filter { m -> SECTIONS.any { m.groupValues[1].trim() == it || m.groupValues[1].trim().startsWith("최근 이벤트") } }.toList()
        if (heads.map { it.groupValues[1].trim().substringBefore(' ') }.distinct().size < 2) return null
        return heads.first().range.first
    }

    /** 붙여 넣은 보고서를 뺀 글(앞에 적은 말은 그대로). 보고서가 없으면 그대로. */
    fun without(text: String): String = start(text)?.let { text.substring(0, it).trimEnd() } ?: text

    private val ASKS = Regex("""점검['’]?\s?기록|보고서\s?복사|멈춤['’]?\s?기록""")

    /** 운영팀의 마지막 답이 점검 기록을 부탁했는지(옛 '보고서 복사' 부탁 글과 새 '점검 기록도 보내기' 부탁 글 모두). */
    fun asked(developerText: String?): Boolean = developerText != null && ASKS.containsMatchIn(developerText)

    /** 글을 적지 않고 기록만 보낼 때 대신 가는 한 줄. */
    const val ONLY_RECORD = "점검 기록을 함께 보내요."
}
