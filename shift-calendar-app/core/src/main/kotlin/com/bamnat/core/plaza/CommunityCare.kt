package com.bamnat.core.plaza

/**
 * 1.39 [CARE-03][CARE-10] 마을 돌봄의 앱 쪽 말: 마을을 열 때 한 번 보여 주는 부드러운 안내, 쉬는 중 한 줄, 해명하기, 소식 한 줄.
 * 서버 `GET /api/community/standing`이 주는 것을 읽어요(설계 docs/plans/1.39-마을-돌봄-경고-제한-설계.md).
 *
 * 사용자 2026-11-07: "그 전에 미리 경고도 해야 한다, 부드럽게", "실제로 잘못된 행동을 하고 있을 때 제재", "해명을 한다면 풀어주는 길".
 * 조사(프로젝트 조사/1.39-악성사용자-거르기-조사.md) ① 이유서 한 장: 무엇에 대한 것인지, 사람이 확인했는지, 어떤 약속인지, 언제까지인지,
 * 해명하는 법을 늘 함께 말해요. ⑨ 숨기지 않기: 거는 제한은 늘 본인에게 알려요.
 */
data class CareNote(
    val id: String,
    /** abuse, harass, spam, false, personal, sexual, illegal, vote, other, auto_first, auto_again. */
    val reason: String,
    /** 운영자가 남긴 한 줄(없을 수 있어요). */
    val note: String? = null,
    /** "auto"(확인된 가려짐에 서버가) 또는 "operator". */
    val by: String = "operator",
    /** 무엇에 대한 것인지: post, talk, recipe, bio, rreq, room …(없을 수 있어요). */
    val about: String? = null,
    /** 쉬기만: "opinion"(의견 쉬기) 또는 "community"(마을 쉬기). 경고면 null. */
    val scope: String? = null,
    val days: Int? = null,
    val untilMillis: Long? = null,
    /** 해명을 보냈으면 그 결과: "" 기다림, "accepted", "kept". 안 보냈으면 null. */
    val appeal: String? = null,
)

data class CareStanding(
    val warning: CareNote? = null,
    val hold: CareNote? = null,
    /** 신고 누적 쓰기 제한이 지금 걸려 있으면 그 해명 결과("" 기다림, accepted, kept), 해명 전이면 "none". 제한이 없으면 null. */
    val limitAppeal: String? = null,
    val answerHours: Int = CommunityCare.ANSWER_HOURS,
) {
    val empty: Boolean get() = warning == null && hold == null
}

object CommunityCare {
    const val ANSWER_HOURS = 48
    const val APPEAL_MIN = 10
    const val APPEAL_MAX = 1000
    const val RULE = "광장 운영 규칙 6항"

    fun reasonText(code: String): String = when (code) {
        "abuse" -> "다른 분을 탓하거나 거친 말"
        "harass" -> "한 분을 계속 따라다니는 글"
        "spam" -> "광고나 같은 글 되풀이"
        "false" -> "사실과 다른 정보"
        "personal" -> "다른 분의 개인 정보"
        "sexual" -> "성적인 내용"
        "illegal" -> "법에 어긋나는 정보"
        "vote" -> "좋아요·투표를 부풀리기"
        "auto_first", "auto_again" -> "운영자가 확인해 가린 글"
        else -> "운영 원칙에 맞지 않는 글"
    }

    /** 무엇에 대한 것인지, 사람 말로. 모르면 null. */
    fun aboutText(about: String?): String? = when (about) {
        "post", "room" -> "광장·토론에 쓴 글"
        "talk" -> "작품 이야기"
        "recipe" -> "올린 작품"
        "bio" -> "프로필 소개"
        "rreq", "request" -> "작품 요청"
        "pay", "payinfo" -> "급여 정보 댓글"
        else -> null
    }

    private fun day(millis: Long, zone: java.time.ZoneId): String =
        java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().let { "${it.monthValue}월 ${it.dayOfMonth}일" }

    private fun scopeText(scope: String?): String = if (scope == "community") "마을 쓰기" else "의견 쓰기"

    /** 마을을 열 때 한 번 보여 주는 안내 창의 제목과 본문. 다정하게, 무엇·왜·어떻게(조사 ①). */
    fun warningTitle(w: CareNote): String = when (w.reason) {
        "auto_first" -> "마을 이용 안내"
        "auto_again" -> "한 번만 더 살펴 주세요"
        else -> "마을 운영자가 드리는 말"
    }

    /**
     * [badgeRest] 1.39 제작자 표식(광장 커뮤니티 갈래의 깃발이 켜졌을 때 true): 첫 안내가 아니면 30일 동안 표식이 쉰다고 숨기지 않고 말해요
     * (운영 규칙 6항 '숨기지 않습니다'). 서버 판단은 company-worker `careQuietMany`.
     */
    fun warningBody(w: CareNote, badgeRest: Boolean = false): String = buildString {
        val what = aboutText(w.about)
        when (w.reason) {
            "auto_first" -> append("${what ?: "쓴 글"} 하나가 운영자 확인 뒤 가려졌어요. 처음이라 안내만 드려요. 마을은 여러 분이 함께 보는 곳이라 조금만 더 부드럽게 부탁드려요.")
            "auto_again" -> append("운영자가 확인해 가린 글이 한 번 더 생겼어요. 같은 일이 또 있으면 며칠 동안 글쓰기를 쉬어 가게 돼요.")
            else -> append("${what?.let { "$it 가운데 " } ?: ""}‘${reasonText(w.reason)}’로 신고된 것을 운영자가 직접 확인했어요. 지금은 아무것도 막지 않았어요. 앞으로 함께 편한 마을이 되도록 부탁드려요.")
        }
        w.note?.let { append("\n\n운영자 한 줄: $it") }
        if (badgeRest && w.reason != "auto_first") append("\n\n$BADGE_REST_WARN")
        append("\n\n근거: $RULE · 잘못 보았다면 ‘해명하기’로 알려 주세요.")
    }

    /** 1.39 제작자 표식이 쉬는 동안의 한 줄(경고 창, 쉬는 중 자세히). 해명을 받아들이면 바로 돌아와요. */
    const val BADGE_REST_WARN = "그동안(30일) 내 제작자 표식은 잠시 쉬어요. 해명을 받아들이면 바로 돌아와요."
    const val BADGE_REST_HOLD = "쉬는 동안 내 제작자 표식도 잠시 쉬고, 끝나면 저절로 돌아와요."

    /** 쉬는 중 한 줄(마을 위). */
    fun holdLine(h: CareNote, zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")): String =
        "🌙 ${h.untilMillis?.let { day(it, zone) + "까지" } ?: "잠시"} ${scopeText(h.scope)}를 쉬어 가요"

    /** 쉬는 중 자세히(한 줄을 누르면): 무엇·왜·무엇이 되고 안 되는지·해명. */
    fun holdBody(h: CareNote, answerHours: Int = ANSWER_HOURS, zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")): String = buildString {
        val what = aboutText(h.about)
        append("${what?.let { "$it 가운데 " } ?: ""}‘${reasonText(h.reason)}’을 운영자가 확인해 ${h.days ?: ""}일 동안 ${scopeText(h.scope)}를 쉬어 가요")
        h.untilMillis?.let { append("(${day(it, zone)}까지, 지나면 저절로 풀려요)") }
        append(".\n\n")
        append(if (h.scope == "community") "글·댓글·좋아요·투표·작품 올리기를 쉬어요." else "글·댓글·의견을 쉬어요. 좋아요와 테마 쓰기는 그대로예요.")
        append(" 읽기, 테마 입기, 내 달력, 문의하기는 언제나 돼요.")
        h.note?.let { append("\n\n운영자 한 줄: $it") }
        append("\n\n근거: $RULE")
        append(when (h.appeal) {
            null -> "\n\n잘못 보았다면 ‘해명하기’로 알려 주세요. ${answerHours}시간 안에 운영자가 읽고 답해요. 받아들이면 바로 풀리고 이번 건은 세지 않아요."
            "" -> "\n\n보낸 해명을 운영자가 읽고 있어요. ${answerHours}시간 안에 문의 답으로 알려 드려요."
            "accepted" -> "\n\n해명을 받아들였어요."
            else -> "\n\n해명을 읽고 그대로 두기로 했어요. 답은 내 문의에서 볼 수 있어요."
        })
    }

    /** 서버가 쓰기를 막았을 때(error "muted", until 있음). [until]이 없으면 옛 말 그대로. */
    fun mutedText(untilMillis: Long?, zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")): String =
        if (untilMillis == null) "운영 원칙을 지키지 않은 글이 있어 지금은 글을 쓸 수 없어요"
        else "${day(untilMillis, zone)}까지 마을 글쓰기를 쉬어 가요. 마을 › 맨 위 한 줄에서 까닭을 보고 해명할 수 있어요"

    /** 조사 ⑧: 같은 글을 10분 안에 다시 쓰려 할 때. */
    const val REPEAT = "방금 같은 글을 썼어요. 같은 글은 10분 뒤에 다시 쓸 수 있어요"

    /** 소식(밤낮 소식) 한 줄, kind "care". */
    fun newsLine(result: String?): String? = when (result) {
        "warn" -> "마을 운영자가 드리는 안내가 있어요. 마을을 열면 볼 수 있어요."
        "opinion" -> "며칠 동안 마을 의견 쓰기를 쉬어 가요. 마을 맨 위에서 까닭을 볼 수 있어요."
        "community" -> "며칠 동안 마을 쓰기를 쉬어 가요. 마을 맨 위에서 까닭을 볼 수 있어요."
        "lifted" -> "마을 쓰기가 풀렸어요. 다시 쓸 수 있어요."
        "ended" -> "쉬어 가던 기간이 끝났어요. 다시 쓸 수 있어요."
        else -> null
    }

    /** 해명 글이 보낼 만한지: null이면 괜찮아요. */
    fun appealProblem(text: String): String? {
        val n = text.trim().codePointCount(0, text.trim().length)
        return when {
            n < APPEAL_MIN -> "${APPEAL_MIN}자 이상 적어 주세요"
            n > APPEAL_MAX -> "${APPEAL_MAX}자까지 적을 수 있어요"
            else -> null
        }
    }

    /** 해명을 보낸 뒤의 말. */
    fun appealSent(answerHours: Int = ANSWER_HOURS): String =
        "해명을 보냈어요. ${answerHours}시간 안에 운영자가 읽고 내 문의로 답해요. 그동안 쉬기는 그대로예요."

    fun appealError(code: String): String = when (code) {
        "already" -> "이 일에는 이미 해명을 보냈어요. 이어서 쓸 말은 내 문의에서 적어 주세요"
        "not_found" -> "이미 풀렸거나 끝난 일이에요"
        "text" -> "${APPEAL_MIN}자 이상 ${APPEAL_MAX}자 이하로 적어 주세요"
        "email", "phone" -> "연락처는 빼고 적어 주세요"
        "off" -> "지금은 문의하기로 보내 주세요"
        "rate_limited" -> "오늘은 문의를 많이 보냈어요. 내일 다시 보내 주세요"
        else -> "보내지 못했어요. 인터넷을 확인하고 다시 해 주세요"
    }
}

/**
 * 1.39 조사 ⑦ 쓰기 전 한마디(넥스트도어·오픈웹: 알림을 본 3분의 1이 고치거나 접음). 폰 안에서만 보고, 서버로 보내지 않아요.
 * 막지 않아요: "보내기 전에 한 번만 다시 볼까요?" + [고치기] [그대로 보내기]. 문구는 다정하게(직소: 차가운 말은 반대 효과).
 */
object PostNudge {
    const val TITLE = "보내기 전에 한 번만 다시 볼까요?"
    const val TEXT = "마을은 여러 분이 함께 보는 곳이에요. 읽는 분이 상처받을 수 있는 말이 있는지 한 번만 살펴 주세요."
    const val EDIT = "고치기"
    const val SEND = "그대로 보내기"

    /** 거친 말·비하(낱말 일부). 띄어쓰기·기호를 빼고 견줘요. 일상 말(예: '시발점')은 빼요. */
    private val WORDS = listOf(
        "씨발", "시발", "ㅅㅂ", "ㅆㅂ", "씨바", "개새끼", "개새", "ㅅㄲ", "병신", "ㅂㅅ", "븅신", "좆", "존나", "미친놈", "미친년", "미친새",
        "닥쳐", "지랄", "ㅈㄹ", "또라이", "등신", "멍청이", "한심한", "쓰레기같", "죽어라", "느금", "니애미", "니미", "엠창",
        "틀딱", "김치녀", "맘충", "급식충", "외노자", "짱깨", "쪽바리",
    )
    private val SAFE = listOf("시발점", "시발역", "시발택시")

    fun needed(text: String): Boolean {
        var t = text.lowercase().replace(Regex("[\\s\\p{Punct}·…‘’“”]"), "")
        SAFE.forEach { t = t.replace(it, "") }
        return WORDS.any { t.contains(it) }
    }
}

/** 1.39 [CARE-09] 팀·사장님 링크가 연락처·링크·계좌·메신저 옮기기를 받지 않을 때(사기 막기). */
object TeamLinkText {
    fun contact(why: String?): String = "링크에는 " + when (why) {
        "phone" -> "전화번호를"
        "link" -> "다른 사이트 주소를"
        "account" -> "계좌번호(긴 숫자)를"
        "messenger" -> "다른 메신저로 옮기자는 말을"
        else -> "연락처·계좌·다른 메신저를"
    } + " 넣을 수 없어요. 링크로 돈을 요구하는 사기를 막으려는 거예요. 그 줄을 빼고 다시 보내 주세요. 연락은 직접 해 주세요."
}
