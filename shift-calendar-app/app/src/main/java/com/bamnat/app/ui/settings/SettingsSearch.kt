package com.bamnat.app.ui.settings

import com.bamnat.core.work.words

/**
 * [UX-04] Plain-word search for settings and help: the words people use ("다크 모드", "알람이 안 울려요")
 * find the setting even when it is named another way. Korean endings (이·가·을·를·은·는·에서…) are
 * dropped from each word, and a word counts when it holds one of an entry's keywords, so "알람이" finds
 * "알람". No 초성 search.
 */
internal object SearchText {
    private val ENDINGS = listOf(
        "에서는", "으로는", "이에요", "에서", "으로", "이요", "이랑", "하고", "에게", "한테", "까지", "부터",
        "은", "는", "이", "가", "을", "를", "에", "도", "로", "와", "과", "랑", "요", "의", "만",
    )

    /** Lower case, letters and digits only: "근무 종류·시간" and "근무종류 시간" read the same. */
    fun norm(s: String): String = s.lowercase().filter { it.isLetterOrDigit() }

    fun stem(word: String): String {
        for (e in ENDINGS) if (word.length >= e.length + 2 && word.endsWith(e)) return word.dropLast(e.length)
        return word
    }

    /** The words of [query], endings dropped; one-letter words only when nothing longer is there. */
    fun words(query: String): List<String> {
        val all = query.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.map(::stem).distinct()
        val long = all.filter { it.length >= 2 }
        return long.ifEmpty { all }
    }

    /**
     * How well [query] fits: the whole query in [title] counts most, then each word found in the title,
     * a keyword or [other] text. Half the words, at least one, must be found; 0 means no fit.
     */
    private const val CHO = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"

    /** U 1.32 [UX132-04] Each 한글 syllable as its first consonant: "기본급" → "ㄱㅂㄱ". Other letters stay. */
    fun initials(s: String): String = buildString {
        for (c in s) append(if (c in '가'..'힣') CHO[(c - '가') / (21 * 28)] else c)
    }

    /** The query is 초성 only ("ㄱㅂㄱ"). */
    fun isInitials(q: String): Boolean = q.length >= 2 && q.all { it in CHO }

    fun score(query: String, title: String, keywords: List<String>, other: String = ""): Int {
        val q = norm(query)
        if (q.isEmpty()) return 0
        // U 1.32 [UX132-04] 초성 검색: "ㄱㅂㄱ" finds 기본급, "ㅇㄹ" 알람.
        if (isInitials(q)) {
            val t = initials(norm(title))
            return when {
                t.startsWith(q) -> 40
                q in t -> 30
                keywords.any { q in initials(norm(it)) } -> 20
                else -> 0
            }
        }
        val t = norm(title)
        val keys = keywords.map(::norm).filter { it.isNotEmpty() }
        val o = norm(other)
        var score = when {
            t.startsWith(q) -> 40
            q in t -> 30
            // A keyword that is the whole query beats one that only holds it ("일요일" is 한 주의 시작, not 요일 of 근무 주기).
            keys.any { it == q } -> 25
            keys.any { q in it } -> 20
            keys.any { it.length >= 2 && it in q } -> 16
            else -> 0
        }
        val words = words(query)
        var found = 0
        for (w in words) {
            val n = norm(w)
            if (n.isEmpty()) continue
            val hit = when {
                n in t -> 6
                keys.any { n in it || (it.length >= 2 && it in n) } -> 4
                n in o -> 1
                else -> 0
            }
            if (hit > 0) found++
            score += hit
        }
        val needed = maxOf(1, (words.size + 1) / 2)
        return if (score >= 20 || found >= needed) score else 0
    }

    /**
     * The items that fit, best first: at most [limit], and none far behind the best one ("알람 소리" lists
     * 알람음 and 근무마다 다른 소리, not every row that says 알람).
     */
    fun <T> best(items: List<T>, limit: Int, score: (T) -> Int): List<T> {
        val scored = items.map { it to score(it) }.filter { it.second > 0 }.sortedByDescending { it.second }
        val top = scored.firstOrNull()?.second ?: return emptyList()
        return scored.filter { it.second * BEHIND >= top }.take(limit).map { it.first }
    }

    /** A fit a quarter as good as the best is still shown; less is noise. */
    private const val BEHIND = 4
}

/** [UX-04] Where a found setting is: one row of a group (lit when the group opens), or a screen of its own. */
sealed interface SettingPlace {
    data class Row(val page: SettingsPage, val row: String) : SettingPlace
    data class Screen(val go: SettingsGo) : SettingPlace
}

/** [UX-04] One thing settings can find: its name as the row says it, where it is, and other words for it. */
data class SettingEntry(
    val title: String,
    val where: String,
    val place: SettingPlace,
    val keywords: List<String> = emptyList(),
)

/** [UX-04] Every setting and the screens settings opens, in the words of their rows. */
/** [SET-05] The alarm page's row that moves every shift's alarms at once. */
internal const val MOVE_ALL_ALARMS_ROW = "모든 근무 알람 한 번에 옮기기"
internal const val WATCH_ALARM_ROW = "워치에서도 울리기"
internal const val WATCH_CALENDAR_ROW = "워치에 보낼 달력"
/** [ALRM-08] 1.25 AB: the row in 설정 › 알람 › 울리는 방식. */
internal const val BUTTON_ACTION_ROW = "버튼으로 알람 끄기"
/** [ALRM-21] 1.35: the row in 설정 › 알람 › 울리는 방식. */
internal const val ALARM_SOON_ROW = "곧 울릴 알람 미리 알리기"
/** [PRF-05] 1.36 PC 사용성 P8: the row in 설정 › 개인정보. */
internal const val SENT_ROW = "내가 보낸 정보·모두 지우기"

/** PC e20cfe53: where a search result lives, starting with 설정 once ("설정 › 도움말과 문의", never "설정 › 설정 › …"). */
internal fun settingsPath(where: String): String =
    if (where == "설정" || where.startsWith("설정 ›")) where else "설정 › $where"

internal object SettingsIndex {
    private fun row(page: SettingsPage, title: String, vararg keywords: String, row: String = title) =
        SettingEntry(title, page.title, SettingPlace.Row(page, row), keywords.toList())

    private fun screen(where: String, title: String, go: SettingsGo, vararg keywords: String) =
        SettingEntry(title, where, SettingPlace.Screen(go), keywords.toList())

    private val W = SettingsPage.WORK
    private val A = SettingsPage.ALARM
    private val N = SettingsPage.NOTICES
    private val P = SettingsPage.PAY
    private val C = SettingsPage.CALENDAR
    private val D = SettingsPage.DATA
    private val V = SettingsPage.PRIVACY

    /**
     * [board]: the build has 회사 규칙 광장; [share]: it can make and take links. [company]: [J133-71] the calendar's work has a
     * company (교대·일근, or one already joined), so 회사 찾기·회사 설정 파일 are found; 알바·현장·가게·투자 search without them.
     */
    fun entries(board: Boolean = true, share: Boolean = true, company: Boolean = true, style: com.bamnat.core.work.WorkStyle? = null,
                /** 1.38 [SLEEP-19] `sleep.auto` and `sleep.sound` open: their switches and cards are found. */
                sleepAuto: Boolean = true, sleepSound: Boolean = true,
                /** 1.39 G6 (PC 12.5 가게·현장): 오늘의 흐름 put on the calendar by the user (카드 배치 › 더 보기), so its switch is found. */
                flowAdded: Boolean = false): List<SettingEntry> =
        all(board, share, company, sleepAuto, sleepSound).filter { fits(it, style, flowAdded) }.map { inWords(it, style) }

    /** [J133-79] 1.33 빌드 5: an 알바's rules come from a 근무지, not a 회사 (the screens say so since J133-77). */
    private fun inWords(e: SettingEntry, style: com.bamnat.core.work.WorkStyle?): SettingEntry = when {
        // 2.0.0 [RVG-12] 검토 BOSS-69: '근무 주기'·'근무 종류와 시간' read as the row does on that kind of work's page (가게 '영업일·정기휴무'·
        // '영업시간', 투자 '투자 알람'·'계좌', 알바 '출근 요일'…); the old name still finds it.
        style != null && (e.place as? SettingPlace.Screen)?.go == SettingsGo.PATTERN && e.title == "근무 주기" ->
            e.copy(title = style.words().pattern, keywords = e.keywords + e.title)
        style != null && (e.place as? SettingPlace.Screen)?.go == SettingsGo.SHIFT_TYPES && e.title == "근무 종류와 시간" ->
            e.copy(title = style.words().types, keywords = e.keywords + e.title)
        style == com.bamnat.core.work.WorkStyle.HOURLY && "회사 계산 방법" in e.title ->
            e.copy(title = e.title.replace("회사 계산 방법", "근무지 계산 방법"))
        // 1.38 PC 12.5 빈틈 2 (투자의 찾기 결과가 '잠금 화면 오늘 근무'): the result reads as the row does on that kind of work's page;
        // where it goes (the row's old name) stays, and the old name still finds it.
        e.title == "잠금 화면 오늘 근무" || e.title == MOVE_ALL_ALARMS_ROW -> {
            val word = if (e.title == MOVE_ALL_ALARMS_ROW) com.bamnat.app.ui.shifts.alarmNoun(style) else com.bamnat.app.ui.shifts.lockNoun(style)
            if (word == "근무") e else e.copy(title = e.title.replace("근무", word), keywords = e.keywords + e.title)
        }
        else -> e
    }

    /** [J133-77] Screens of wage work (급여, 추가 근무, 듀티표, 근태, 출퇴근) that a shop, a freelancer or an investor does not have. */
    private val WAGE_SCREENS = setOf(SettingsGo.EXTRA_KINDS, SettingsGo.DUTY_ROSTER, SettingsGo.HEALTH_REPORT, SettingsGo.ATTENDANCE_IMPORT,
        SettingsGo.COMMUTE, SettingsGo.PAY, SettingsGo.PAY_BASE, SettingsGo.PAY_ITEMS, SettingsGo.PAY_PERIOD, SettingsGo.PAY_HOURS)
    /** [J133-77] Rows about shifts, wages and 연차. */
    private val WAGE_ROWS = setOf("휴식 점검", "근무표 점검 기준", "한 주 근무 경고", "최근 3개월 한 주 평균 경고", "퇴근 시각 알림", "연속 근무 경고",
        "급여 입금 비교", com.bamnat.app.ui.pay.AUTO_CLOSE_TITLE, "연차 사용 촉진 알림", "근무마다 다른 소리", "알람 뒤 오늘 카드")

    /**
     * [J133-77] 1.33 (유형별 점검: 알바가 '회사'를 찾으면 근태 엑셀이, 가게가 찾으면 연차·급여가 나와요): what the calendar's kind of
     * work has. No kind chosen keeps everything.
     */
    internal fun fits(e: SettingEntry, style: com.bamnat.core.work.WorkStyle?, flowAdded: Boolean = false): Boolean {
        // 1.39 [PAYX-62] 주 15시간 경계 알림은 알바에만 있어요.
        if (style != null && style != com.bamnat.core.work.WorkStyle.HOURLY && e.title == com.bamnat.core.pay.WeekHoursNudge.ROW) return false
        if (style == null || style.usesPayEngine) return !(style == com.bamnat.core.work.WorkStyle.HOURLY && e.title == "연차 사용 촉진 알림")
        val place = e.place
        if (place is SettingPlace.Screen && place.go in WAGE_SCREENS) return false
        // 1.38 PC 12.4.1 빈틈 2: 현장·가게·투자 have no 오늘의 흐름 on the calendar, so no switch for it either.
        // 1.39 G6: unless the user put it on the calendar themselves.
        return e.title !in WAGE_ROWS && (flowAdded || e.title != "오늘의 흐름 보기")
    }

    private fun all(board: Boolean, share: Boolean, company: Boolean, sleepAuto: Boolean = true, sleepSound: Boolean = true): List<SettingEntry> = listOfNotNull(
        screen(W.title, "일하는 방식", SettingsGo.WORK_STYLE, "근무 형태", "교대", "일근", "시급", "현장", "프리랜서", "자영업", "가게"),
        screen(W.title, "근무 주기", SettingsGo.PATTERN, "순서", "패턴", "로테이션", "주주야야", "시작일", "요일", "교대 순서",
            "공휴일 쉬기", "격주 토요일", "격주", // [SCHED-04]
            // [RVG-12] 가게·알바·투자의 말로도 찾아요.
            "영업일", "정기휴무", "휴무일", "쉬는 요일", "출근 요일", "일하는 요일", "투자 알람", "장 시작"),
        screen(W.title, "근무 종류와 시간", SettingsGo.SHIFT_TYPES, "주간", "야간", "근무 시간", "출근 시간", "색", "아이콘", "근무 전 알람", "휴게",
            "영업시간", "브레이크 타임", "알바 시간", "계좌"), // [RVG-12]
        screen(W.title, "연장 휴무일 근무 지정", SettingsGo.EXTRA_KINDS, "잔업", "특근", "대근", "연장", "휴일 근무", "가산", "교육", "교육 시수", "근무별 시수"),
        // [SHFT-06][SHFT-07][DAYW-06][DAYW-07]
        screen(W.title, "한 달 근무 넣기", SettingsGo.DUTY_ROSTER, "듀티표", "듀티표 넣기", "직접 입력", "키패드", "반차", "근무표 사진", "글자 인식", "사진으로 입력", "간호사", "한 달 입력",
            // [IMPORT-03]
            "근무표 파일", "엑셀 근무표", "CSV", "불규칙 근무표", "편의점 근무표", "병동"),
        screen(W.title, "연간 건강 지표", SettingsGo.HEALTH_REPORT, "야간 비율", "연속 야간", "건강", "1년", "연간 통계"),
        screen(W.title, "근태 엑셀 가져오기", SettingsGo.ATTENDANCE_IMPORT, "엑셀", "CSV", "근태", "출퇴근 기록", "회사 시스템", "xlsx").takeIf { company }, // [J133-77]
        screen(W.title, "출퇴근 자동 기록", SettingsGo.COMMUTE, "위치", "지오펜스", "자동 출근", "자동 퇴근", "GPS", "회사 위치"),
        // 1.36 PC Y21: '근무 알람 끄기'·'잠시 끄기' found nothing.
        row(A, "알람 사용", "알람 끄기", "알람 켜기", "모두 끄기", "알람 전체", "근무 알람 끄기", "근무 알람", "잠시 끄기", "알람 잠시 끄기", "알람 멈추기", "휴가 중 알람", "알람 안 울리게"),
        row(A, "알람음", "알람 소리", "벨소리", "무음", row = "알람음"),
        row(A, "근무마다 다른 소리", "근무별 알람음", "야간 소리"),
        // [SET-05]
        row(A, MOVE_ALL_ALARMS_ROW, "알람 한 번에", "알람 앞당기기", "알람 미루기", "10분 일찍", "모든 알람", "알람 옮기기"),
        row(A, "진동", "바이브"),
        row(A, "점점 크게", "볼륨", "소리 크기"),
        row(A, MISSION_ROW, "미션", "계산 문제", "수학 문제", "흔들기", "못 일어나요", "확실한 알람"), // [PROV-06]
        row(A, WAKE_CHECK_ROW, "일어났나요", "기상 확인", "다시 자요", "확실한 알람"), // [PROV-07]
        row(A, "다시 울림 간격", "스누즈", "미루기", "다시 울림"),
        row(A, "자동으로 끄기", "울리는 시간", "알람 길이"),
        row(A, WATCH_ALARM_ROW, "워치", "워치 알람", "시계 진동", "Wear OS", "워치 진동"), // [WEAR-06] 1.31
        row(A, PHOTO_ROW, "증명 사진", "사진 미션", "사진 찍어 끄기", "욕실 거울", "기준 사진"), // [ALRM-04] O 1.23 (L)
        screen(A.title, "알람 점검", SettingsGo.DIAGNOSTICS, "알람이 안 울려요", "늦게 울려요", "원인", "진단", "점검"),
        // 1.32.1 [STAB-13] 앱 멈춤 기록 and 멈춤 관찰 are found by what people say when the app froze.
        screen(A.title, "앱 멈춤 기록", SettingsGo.DIAGNOSTICS, "멈춤", "멈췄어요", "화면 멈춤", "앱이 닫혀요", "튕겨요", "렉", "먹통", "잠금 풀면 멈춤"),
        row(N, "오늘의 흐름 보기", "잘 시간", "수면 제안", "카페인", "낮잠", "오늘 카드"),
        row(N, "목표 수면 시간", "잠", "수면 목표"),
        row(N, "퇴근 후 잠들기까지", "출퇴근", "씻는 시간", "퇴근"),
        row(N, "워치·휴대폰 수면 기록 함께 보기", "헬스 커넥트", "갤럭시 워치", "삼성 헬스", "워치", "수면 측정"),
        screen(N.title, "수면 기록 보기", SettingsGo.SLEEP, "수면 기록", "잠든 시간", "잠들었어요", "깼어요"),
        // 1.38 [SLEEP-19] 수면 도약의 새 스위치와 화면.
        row(N, com.bamnat.app.ui.sleep.SLEEP_AUTO_ROW, "자동 수면", "수면 감지", "잠 자동", "자동 기록", "활동 인식").takeIf { sleepAuto },
        row(N, HEALTH_WRITE_ROW, "헬스 커넥트", "삼성 헬스", "수면 내보내기", "다른 건강 앱"),
        row(N, SLEEP_DND_ROW, "방해 금지", "무음", "중요한 알림만", "잘 때 알림 끄기"),
        screen(N.title, "잠드는 소리", SettingsGo.SLEEP, "백색 소음", "빗소리", "파도", "수면 소리", "자장가", "소음"),
        screen(N.title, "자는 동안 소리 재기", SettingsGo.SLEEP, "코골이", "잠꼬대", "이갈이", "기침", "녹음", "측정 시작").takeIf { sleepSound },
        screen(N.title, "깨우는 구간", SettingsGo.SLEEP, "스마트 알람", "얕은 잠", "일찍 깨우기", "스마트 기상").takeIf { sleepSound },
        screen(N.title, "근무 맞춤 회복 도전", SettingsGo.SLEEP, "도전", "21일", "수면 챌린지"),
        row(N, "휴식 점검", "11시간", "연속 야간", "휴식"),
        // [REST-01] The limits sheet, also opened from the calendar's check card.
        row(N, "근무표 점검 기준", "병원 기준", "야간 뒤 쉬는 날", "연속 야간", "한 달 야간", "나이트 뒤 쉬는 날", "연속 나이트", "한 달 나이트", "연속 근무", "N-O-D", "나오데", "이브데이", "점검 끄기"),
        row(N, "한 주 근무 경고", "64시간", "근무시간 경고", "주 52시간"),
        row(N, "최근 3개월 한 주 평균 경고", "평균", "3개월"),
        // 1.32 [WTYP-16]
        row(N, "퇴근 시각 알림", "지금 퇴근", "퇴근 버튼", "잔업 기록", "야근 기록", "퇴근 알림"),
        row(N, "퇴근 뒤 가계부 정리 알림", "하루 마감", "가계부 알림", "지출 적기 알림", "오늘 쓴 돈", "정리 알림"),
        row(N, "근무 전 한 줄 예고", "근무 전날 알림", "내일 돈 예고", "근무 날 지출", "야간 지출 예고"),
        // 1.32 [WTYP-12]
        row(N, "연속 근무 경고", "전공의", "연속 24시간", "주 80시간", "평균 72시간", "당직", "36시간"),
        row(N, "하루 브리핑", "커피", "낮잠 알림", "알림"),
        row(N, "퇴근길 안전", "운전", "졸음"),
        row(N, "잘 준비", "취침 알림", "잘 시간 알림"),
        row(N, "자는 중 표시", "자는 중", "알림창"),
        row(N, "나갈 돈·받을 돈", "고정비", "미수금", "돈 알림", "받을 돈"),
        // D 1.32 [PAYA-05]
        row(N, "급여 입금 비교", "월급 들어옴", "급여 알림", "입금 비교", "월급날 알림"),
        // 2.0.0 [RVJ-18] 굳은 달.
        row(N, com.bamnat.app.ui.pay.AUTO_CLOSE_TITLE, "마감", "급여 마감", "굳히기", "확정", "지난 달 고정"),
        // 1.39 [PAYX-62] 알바만(fits).
        row(N, com.bamnat.core.pay.WeekHoursNudge.ROW, "주휴", "주휴수당", "15시간", "주 15시간", "쪼개기"),
        row(N, "연차 사용 촉진 알림", "연차", "휴가 알림", "연차 소멸 안내", "소멸", "사용 촉진"),
        // [LEAVE-06]
        screen(P.title, "연차", SettingsGo.LEAVE, "연차 원장", "남은 연차", "연차 계산", "입사일", "이월", "보상휴가", "연차수당"),
        row(N, "잠금 화면 오늘 근무", "잠금 화면", "상시 알림", "오늘 근무 알림"), // [GUIDE-01]
        // 1.39 [NTC-04]
        row(N, "알림 한눈에", "알림 목록", "겹치는 알림", "중복 알림", "알림 정리", "켜 둔 알림", "알림 규칙", "알림 두 개"),
        row(N, "알람 뒤 오늘 카드", "오늘 카드", "알람 끈 뒤", "이번 주 시간", "52시간"), // [ALRM-16] 1.31
        row(N, "밤낮 소식", "광장 소식", "광장 알림", "규칙 바뀜", "채택", "회사 소식", "규칙 제안", "답장 알림", "문의 답", "신고 결과",
            "받은 근무표 바뀜", "동료 근무 바뀜"), // [COMM-10][SRV-10]
        row(N, "자는 동안 미루기", "방해 금지", "조용히"),
        screen(P.title, "급여 계산", SettingsGo.PAY, "월급", "기본급", "수당", "연차", "시급", "명세서", "통상임금",
            // [SET-03] 1.22 묶음 A·C·D: what is set in the pay settings.
            "부양가족", "자녀 수", "간이세액", "공무원 봉급", "계급", "호봉", "호봉표", "탄력근무", "포괄임금", "연차 세는 기준", "회계연도"),
        row(P, "광장 시세와 내 단가 견주기", "시세", "일당", "시급 비교", "임금표"), // [DAILY-16]
        row(P, "견줄 내 지역", "시세 지역", "광장 시세", "내 지역"), // [DAILY-16]
        row(P, "회사 설정 보내기", "동료", "회사 설정 파일", "같은 회사").takeIf { company },
        row(P, "회사 설정 불러오기", "동료에게 받은 파일").takeIf { company },
        screen("설정", "회사 규칙 광장", SettingsGo.BOARD, "회사 규칙", "투표", "토론").takeIf { board && company },
        // [J133-71] Other kinds of work find their own 광장.
        screen("설정", "광장", SettingsGo.BOARD, "알바 광장", "현장 광장", "가게 광장", "투자 이야기", "시세", "이야기").takeIf { board && !company },
        row(P, "회사 찾기", "회사 설정", "같은 회사", "회사 근무표", "회사 계산").takeIf { board && company },
        // [SET-03] 1.22 묶음 B: someone hidden in the plaza comes back from 내 활동.
        screen("마을 › 광장 › 내 활동", "가린 사람", SettingsGo.BOARD, "차단", "차단 해제", "안 보기", "가리기", "글 안 보기").takeIf { board },
        screen("달력 › 도구", "근무표 공유", SettingsGo.SHARE, "공유", "링크", "이미지", "보내기", "카톡", "가족", "동료에게"),
        screen("달력 › 도구", "동료 근무표 받기", SettingsGo.RECEIVE, "받은 링크", "받은 근무표", "바꿔 보기", "같이 쉬는 날", "동료 달력").takeIf { share },
        screen(C.title, "달력 목록", SettingsGo.CALENDARS, "새 달력", "달력 추가", "달력 여러 개", "알바", "부업", "순서", "달력 이름"),
        row(C, WATCH_CALENDAR_ROW, "워치 달력", "워치", "타일", "시계"), // [WEAR-04] 1.31
        row(C, "테마", "다크 모드", "어둡게", "밝게", "야간 모드", "검은 화면"),
        row(C, "휴대폰 배경색에 맞추기", "색", "배경화면", "앱 색"),
        row(C, "한 주의 시작", "일요일", "월요일", "주 시작"),
        row(C, "날짜 칸 아래", "음력", "근무시간 표시", "날짜 밑"),
        row(C, "날짜 칸에 일감·메모 여러 줄", "칸에 일정", "일정 여러 줄", "메모 잘림", "일감 이름 잘림", "줄마다", "테마 입으면 메모 안 보임"),
        row(C, "우리 업종 기념일", "기념일", "소방의 날", "경찰의 날", "업종의 날"), // [EASE-04] 1.38
        row(C, CAL_THEME_ROW, "달력 테마", "토요일 색", "일요일 색", "공휴일 색", "오늘 색", "칸 색칠", "근무 날 칸", "꾸미기"), // [RCP-09] 1.34
        row(A, com.bamnat.app.logic.LOGIC_ROW, "규칙", "수식", "조건", "만약", "자동", "단추", "버튼", "이럴 때", "인수인계", "체크 초기화", "메모 틀", "자동 기록"), // [RUL-06] 1.36
        row(C, PHOTO_LOOK_ROW, "사진 꾸미기", "배경 사진", "달력 배경", "머리 그림", "근무 사진", "근무 아이콘", "위젯 사진", "모서리", "버튼 모양", "둥글게", "각지게", "테마 사진"), // [RCX-30] 1.35
        row(C, WIDGET_LOOK_ROW, "위젯 투명도", "위젯 어둡게", "위젯 밝게", "투명 위젯", "위젯 배경", "위젯 꾸미기"), // [RCX-14] 1.35
        row(C, "달력 모양", "칸 높이", "메모 중심", "다이어리", "한눈에", "큰 글씨", "칸에 메모", "메모 여러 줄", "칸 선", "날짜 위치", "근무 색 띠", "줄 순서"), // [CVW-02] 1.35
        row(C, LONG_PRESS_ROW, "길게 누르기", "꾹 누르기", "바로 메모", "빠른 메뉴", "하루 열기"), // [CVW-09] 1.35
        row(C, ACCENT_ROW, "버튼 색", "포인트 색", "강조 색", "앱 색", "메인 색"), // [CVW-08] 1.35
        row(C, FREE_LOOK_ROW, "꾸미기", "테마", "스킨", "배경", "칸 디자인", "패턴", "메모장 모양", "가계부 모양", "급여 모양", "화면 색", "포스트잇", "다이어리"), // [FREE-03] 1.35
        row(C, "메모 보기", "달력 메모", "메모 표시"),
        // U 1.32 [UX132-05][UX132-06] G [WTYP-07]
        row(C, "글자 크기", "글씨", "글씨 크게", "크게", "작게", "노안", "안 보여요", "큰 글씨", "글꼴"),
        // [MAT-05] 1.36 PC 확인 4차: '글꼴' goes to the studio's font, not only to 글자 크기.
        screen("스튜디오 › 앱 전체", "앱 전체 글꼴", SettingsGo.STUDIO_FONT, "글꼴", "폰트", "글씨체", "서체", "명조", "바탕체", "손글씨", "내 글꼴", "font"),
        // PC 확인 5차: the other two fonts come up with it.
        screen("스튜디오 › 달력 › 색·배경", "화면마다 글꼴", SettingsGo.STUDIO_SCREEN_FONT, "글꼴", "폰트", "글씨체", "서체", "이 화면 글꼴", "화면 글꼴", "font"),
        screen("스튜디오 › 달력 › 칸 디자인", "날짜 숫자 글꼴", SettingsGo.STUDIO_DATE_FONT, "글꼴", "폰트", "글씨체", "서체", "날짜 글꼴", "숫자 글꼴", "font"),
        // [MAT-07] PC 확인 (마을 확인 중 '설정 찾기로 꾸밈 효과를 못 찾음'): the studio's 움직임 and each part's own.
        screen("스튜디오 › 앱 전체", "움직임 효과", SettingsGo.STUDIO_MOTION, "움직임", "효과", "꾸밈 효과", "파티클", "애니메이션", "눈 내리기", "벚꽃", "반짝이", "motion", "animation"),
        screen("스튜디오 › 앱 전체 › 부품", "부품 움직임", SettingsGo.STUDIO_PARTS, "움직임", "효과", "부품 효과", "애니메이션", "흔들림", "깜빡임", "부품", "animation"),
        row(C, "촘촘하게", "넉넉하게", "간격", "밀도", "한 화면에 많이"),
        row(C, "오전/오후로 보기", "오전", "오후", "12시간", "24시간", "시각 표기"),
        row(C, "밤 색", "밤 모드", "야간 모드", "어두운 색", "붉은 색", "눈부심", "잘 시간", "새벽", "밤에 어둡게", "다크 모드", "수면 화면만"), // 1.39 [FLOW-14][DSG-07]
        row(C, "칸이 보이기", "칸이", "캐릭터", "마스코트", "그림 친구", "캐릭터 끄기"), // 1.38 [FLOW-26]
        row(C, "설명 처음처럼 보기", "설명", "안내 다시", "도움말 다시", "설명 펼치기", "ⓘ"), // 2.0.0 [RVP-08]
        row(C, "하루 이야기 줄", "하루 이야기", "타임라인", "시간 순서", "하루 흐름", "이야기 줄 끄기", "하루 이야기 카드", "달력 아래 하루 이야기"), // 1.38 [FLOW-08]
        row(C, "하루 다이얼", "다이얼", "24시간 원", "시계", "하루 원"), // 1.39 [FLOW-28]
        row(C, "하루 시작 시각", "하루 시작", "새벽 4시", "자정", "날짜 바뀌는 시각", "어제로"), // 1.39 [FLOW-30]
        row(C, "날짜 밑에 그날 쓴 돈", "지출 표시", "쓴 돈", "달력에 지출", "하루 지출"), // E 1.32 [LEDX-07]
        row(C, "지출 히트맵", "히트맵", "많이 쓴 날", "지출 색", "소비 패턴"), // 1.32 [LEDX-23]
        row(C, "큰 지출을 근무 시간으로", "근무 시간 환산", "몇 시간 일한 돈", "시급으로 보기", "공수"), // 1.37 [LEDH-07]
        row(C, "위젯에 메모 보이기", "위젯", "홈 화면", "메모 첫 줄", "위젯 메모"), // [DAILY-01]
        row(C, "메모 공유", "달력별 메모", "메모 함께 보기", "메모 따로", "공용 메모"), // [MEMO-02]
        row(C, "날짜를 누르면", "미리보기", "하루 화면", "그 주만 보기", "바로 크게"),
        // B 1.32 [CALX-04]
        row(C, "달력만 보기", "카드 숨기기", "달력만", "요소 끄기", "화면 단순하게", "깔끔하게"),
        // B 1.32 [CALX-03]
        row(C, "한 번 눌러 고르기", "한 번 눌러 선택", "두 번 눌러 열기", "잘못 눌러 열림", "고르기만"),
        row(C, "하루 화면 순서", "순서 바꾸기", "하루 화면"),
        row(D, "백업 저장", "백업", "휴대폰 바꾸기", "옮기기", "암호", "저장"),
        row(D, "백업 안 한 지 30일 알림", "백업 알림", "마지막 백업", "백업 언제", "기록 잃어버림"), // 1.32 [STAB-08]
        row(D, "사진도 백업", "사진", "영수증 사진", "계약서 사진", "메모 사진", "백업 크기"), // [BACK-01]
        row(D, "파일 불러오기", "복원", "백업 불러오기", "되돌리기"),
        row(D, "최근 지운 기록", "휴지통", "되돌리기", "지운 달력", "실수로 지움", "복구"), // [UNDO-01]
        screen(D.title, "캘린더에서 가져오기", SettingsGo.CALENDAR_IMPORT, "삼성 캘린더", "구글 캘린더", "가져오기", "일정"),
        row(D, "다른 앱에서 옮기기", "갈아타기", "다른 앱", "옮기기", "근무 앱 캡처", "화면 캡처", "스크린샷", "앱 바꾸기"), // [GROW-05]
        row(D, AUTO_BACKUP_ROW, "자동 백업", "매일 백업", "구글 드라이브", "드라이브", "클라우드", "백업 자동"), // [PROV-03]
        row(D, "캘린더 앱으로 내보내기", "ics", "구글 캘린더", "내보내기"),
        row(D, "휴대폰 캘린더에 근무 쓰기", "구글 캘린더", "삼성 캘린더", "캘린더 연동", "캘린더에 쓰기", "동기화"), // [FEAT-07]
        row(D, GROUP_ROW, "모임 코드", "조 근무표", "동료 근무표 모아", "나란히 보기", "팀 근무표").takeIf { share }, // [SHR-14]
        row(D, CUT_ALL_ROW, "링크 지우기", "공유 끊기", "링크 모두 끊기", "공유 그만", "링크 삭제").takeIf { share }, // [SHR-13]
        row(D, EXPORT_MARK_ROW, "워터마크", "로고", "밤낮 글자", "사진 표시", "견적서 표시", "표시 끄기"), // 1.39 [SPREAD-05]
        row(V, "개인정보 처리방침", "개인정보", "처리방침"),
        row(V, SENT_ROW, "보낸 정보", "모두 지우기", "정보 삭제", "삭제", "탈퇴", "계정 삭제", "내 정보 지우기", "프로필 지우기", "마을 지우기", "거두기").takeIf { board }, // [PRF-05]
        row(V, SUMMARY_ROW, "동의", "권한 요약", "내가 허용한 것", "보내는 것", "권한 한눈에", "동의 내역"), // [ALRM-02]
        screen(A.title, "서버 연결 점검", SettingsGo.DIAGNOSTICS, "서버 연결", "인터넷", "보내지 못함", "연결 확인", "서버 상태"), // [ALRM-02]
        row(V, "앱 잠금", "잠금", "지문", "얼굴", "비밀번호", "PIN", "생체 인증", "숨기기"),
        row(V, "금액 가리기", "금액 숨기기", "돈 가리기", "금액 안 보이게", "잔액 숨기기", "월급 가리기", "가리기", "눈 단추"), // 1.38 PC 12.2
        row(V, "알림 허용", "알림 권한", "권한", row = "알림"),
        row(V, "잠금 화면에 알람 띄우기", "전체 화면", "권한", "잠금 화면"),
        row(V, "결제 알림 읽기", "카드 알림", "은행 알림", "문자", "자동 기록", "가계부"),
        row(V, "사용 통계 보내기", "통계", "사용 기록", "익명", "보낼 내용"), // [STAT-01]
        row(V, AUTO_SHARE_ROW, "회사 광장", "저절로 나누기", "자동 공유", "근무 순서 공유", "계산 방식 공유", "광장 참여", "공유 끄기").takeIf { board && company }, // [PLAZA-07]
        row(V, "광고 개인정보 선택", "광고", "맞춤 광고", "광고 ID", "동의", "GDPR", "광고 끄기"), // [MON-07]
        // [MON-04][MON-05] 프로 and what paying for it agrees to.
        screen("설정", "프로", SettingsGo.PRO, "구독", "결제", "광고 없애기", "광고 제거", "무료 체험", "이용권", "복구 코드", "관리자 연결", "환불", "해지", "유료"),
        row(V, "이용약관", "약관", "환불 규정", "청약철회", "구독 약관"),
        row(V, FORGET_ALL_ROW, "모두 지우기", "보낸 정보 지우기", "삭제", "탈퇴", "데이터 지우기", "서버에서 지우기", "문의 지우기", "공유 링크 지우기", "링크 모두 끊기"), // 1.39 [PRIV-06] // 1.36 [INQ-07]
        row(V, "사업자 정보", "사업자", "통신판매업", "사업자등록번호", "판매자", "대표자", "회사 정보", "주소", "전화번호"), // 1.36 [MON-12]
        // [GUIDE-05] Every switch of a feature that can be off, in one place.
        // [ONBOARD-04][SIMPLE-02] The recommended features on one screen, and where each is turned off.
        screen("설정 › 기능 켜고 끄기", "추천 기능 한 번에 켜기", SettingsGo.RECOMMENDED, "추천", "모두 켜기", "한 번에", "권한 한 번에",
            "허용 한 번에", "기본 설정", "처음 설정", "끄는 법", "끄기", "알림 끄기", "켜도 괜찮", "브리핑", "잠금 화면 근무"),
        screen("설정", "기능 켜고 끄기", SettingsGo.FEATURES, "기능 끄기", "기능 켜기", "숨기기", "안 보이게", "카드 끄기", "안내 끄기", "팁 끄기", "위젯 줄"),
        // [EASY-08][OBS-02] 1.23 N, the card at the top of settings (shown "설정 › 맨 위 카드").
        screen("맨 위 카드", "내 정보", SettingsGo.PROFILE, "프로필", "질문 카드", "입사일", "월급날", "다시 묻기", "모두 처음부터", "내 설정"),
        screen("맨 위 카드", "장터·내 레시피", SettingsGo.RECIPES, "레시피", "나눔 판", "장터", "마을", "나눔", "되돌리기", "받은 레시피", "내 레시피", "테마 나누기", "공유 설정"), // [RCP-38] 1.34
        screen("맨 위 카드", "밤낮이 한 일", SettingsGo.JOURNAL, "한 일", "한 일 기록", "알아서", "자동", "로그", "관측"),
        screen("설정 › 도움말과 문의", "문의하기", SettingsGo.INQUIRY, "질문", "건의", "제안", "오류", "버그", "고객센터", "도움말", "메일", "신고"),
        screen("설정 › 도움말과 문의", "자주 묻는 질문", SettingsGo.INQUIRY, "도움말", "사용법", "어떻게", "FAQ"),
        row(V, "앱 버전", "버전", "업데이트", row = "버전"),
        // O 1.23 (I)
        row(A, "알람 제안", "스마트 알람", "알람 추천", "늦게 끔", "알람 시간", "숨긴 제안", "다시 보기"), // [ALRM-03]
        // [READ-04][READ-05][READ-08] 1.25 U: reading a roster or payslip photo, and its ways out.
        screen(W.title, "근무표 사진이 안 읽힐 때", SettingsGo.DUTY_ROSTER, "사진 인식", "인식 안 돼", "어느 줄", "내 조", "사진 위에서 누르기", "사진에서 내 줄", "무엇을 읽었는지", "기울어진 사진", "준비물", "멈추기"),
        screen("급여", "명세서·회사 계산 방법 사진", SettingsGo.PAY, "명세서 사진", "두 장", "회사 계산 방법", "취업규칙 사진", "수당표"),
        // 1.25 묶음 AB
        // [EASE-17][EASE-21] 1.38: the card on top of 설정 › 알람.
        row(A, GUARD_ROW, "알람 미리 점검", "알람 안 울릴까", "알람 경고", "알람 지키기", "소리 0"), // [ALRM-27]
        row(A, SKIP_ONCE_ROW, "이번만", "한 번만 끄기", "다음 알람", "다음 알람 건너뛰기", "알람 쉬기", "알람 준비", "알람 안 울림", "알람 믿음"),
        row(A, ALARM_SOON_ROW, "곧 알람", "알람 미리", "이번 알람만 끄기", "다음 알람 끄기", "알람 건너뛰기"), // [ALRM-21]
        row(A, BUTTON_ACTION_ROW, "볼륨 버튼", "전원 버튼", "잠금 버튼", "소리 줄이기", "버튼으로 끄기", "알람 끄는 법"), // [ALRM-08]
        // ---- 1.25 묶음 Y [TIER-07] ----
        // 1.36 PC Y11: '보상' found nothing; the 보상 이용권 and earned days live on the 프로 page.
        screen("설정 › 프로", "보상 이용권", SettingsGo.PRO, "보상", "이용권", "받은 이용권", "쌓인 날", "쓰면 쌓이는 날", "기다리는 날", "광고 보상", "보상 장부"),
        screen("설정 › 프로", "프로 기능 모아보기", SettingsGo.PRO, "프로 기능", "모아보기", "유료 기능", "프로 표시", "잠긴 기능"),
        // 1.25 Z
        row(D, MOVE_ROW, "새 휴대폰", "기기 변경", "옮기기", "모든 설정", "폰 바꿈", "이전", "복원"), // [BAK-01]
        // ---- 1.25 묶음 X [PLAZA-06] ----
        row(W, SETUP_AGAIN_ROW, "첫 설정", "처음 설정", "다시 받기", "건너뛴 설정", "놓친 설정", "회사 찾기", "초기 설정", "처음부터"),
        // [EASE-27] 1.38 내 규칙 한눈에.
        row(W, RULE_CARDS_ROW, "규칙", "내 규칙", "조건", "공휴일 규칙", "알람 조건", "수당 조건", "문장"),
        // ---- 1.26 묶음 AD [NAV-07] ----
        row(C, TAB_ORDER_ROW, "첫 화면", "메뉴 순서", "시작 화면", "탭", "탭 순서", "메인 화면", "처음 화면", "메뉴 바꾸기", "메모부터",
            "아래 메뉴", "탭 채우기", "묶음", "모음", "중첩 탭", "탭 안에 탭", "자주 쓰는 기능"),
        // ---- 1.26 묶음 AC [SPAT-02] ----
        screen("${W.title} › 근무 주기", "회사 이름으로 근무주기 찾기", SettingsGo.PATTERN, "회사 근무주기", "공개 자료", "근무주기 검색", "회사 검색",
            "4조 2교대", "3조 2교대", "교대 주기", "처음 제공").takeIf { company },
        // ---- 1.26 묶음 AE [INVITE-02][INVITE-03] ----
        screen("설정 › 프로", "친구 초대", SettingsGo.PRO, "초대", "초대 링크", "추천", "친구", "프로 한 달", "공짜 프로", "초대한 친구"),
        screen("설정 › 프로", "초대 코드 넣기", SettingsGo.PRO, "초대 코드", "받은 코드", "친구 코드", "추천 코드", "코드 입력"),
        // AG 1.27 시작 [INVITE-11][INVITE-12]
        screen("설정 맨 위 › 함께 만들어요", "불편한 점 알려 주기", SettingsGo.INQUIRY, "불편", "버그 신고", "오류 신고", "개선", "건의", "함께 만들어요"),
        screen("설정 › 기능 켜고 끄기", "달력의 친구 초대 카드 끄기", SettingsGo.FEATURES, "초대 카드", "초대 알림", "초대 권유", "그만 보기"),
        // AG 1.27 끝
        // ---- 1.27 묶음 AH [PLAZA-14][PLAZA-16] ----
        screen("${W.title} › 근무 주기", "회사 대표 주기", SettingsGo.PATTERN, "대표 주기", "사업장", "쓰고 있어요", "맞아요", "달라요", "회사 이름 자동 완성").takeIf { company },
        screen("급여 › 급여 계산 설정", "우리 회사 급여 방식 찾기", SettingsGo.PAY, "급여 방식 찾기", "회사 급여", "계산 방식 가져오기", "수당 가져오기").takeIf { company },
        // ---- 1.27 묶음 AJ [READ-15][READ-17][PAYS-17][PAYS-19] ----
        screen(W.title, "사진과 같은가요? · 다른 곳 고치기", SettingsGo.DUTY_ROSTER, "사진과 같은가요", "다른 곳이 있어요", "잘못 읽음", "칸 고치기",
            "스캐너", "문서 스캐너", "반듯하게", "그림자"),
        screen("급여", "명세서와 맞춰 보기", SettingsGo.PAY, "맞춰 보기", "맞춰보기", "금액 비교하기", "계산 방식 찾기", "명세서 비교", "실제 입금액",
            "사진으로 회사 방식 맞추기", "명세서로 맞춰 보기"),
        // [J133-34] 순번 714 (1.32 실사용 테스트: 'ㄱㅂㄱ'·'기본급'으로 찾아도 기본급 칸으로 가는 결과가 없었어요): the values themselves.
        screen("근무·급여 › 값", "기본급·시급 바꾸기", SettingsGo.PAY_BASE, "기본급", "시급", "일당", "월급 금액", "급여 금액", "임금"),
        screen("근무·급여 › 값", "수당 넣기·고치기", SettingsGo.PAY_ITEMS, "수당", "야간수당", "교대수당", "식대", "상여", "보너스", "직책수당"),
        // PC e1ca9e5b (탄력제·회사 규모를 고르는 길을 설정에서 못 찾음): the 기본 rules, where 5인 이상, 탄력적 근로시간제 and the hours are.
        screen("근무·급여 › 규칙", "회사 규모·탄력적 근로시간제·소정근로시간", SettingsGo.PAY_HOURS, "5인 이상", "5인 미만", "회사 규모", "탄력제",
            "탄력적 근로시간제", "탄력근무", "주 64시간", "주 52시간", "소정근로시간", "209시간", "근무표 시간"),
        screen("근무·급여 › 규칙", "월급날·정산 기간", SettingsGo.PAY_PERIOD, "월급날", "급여일", "지급일", "정산", "마감일", "월급 들어오는 날"),
        screen("급여", "이것만 넣으면 정확해져요", SettingsGo.PAY, "규칙만", "명세서 없이", "시수", "월 소정근로시간", "기본급 넣기", "필수 값", "0원"),
        // S 1.32 [SECX-04] 1.28's 진단 › 기능 상태 row is gone with the card (no developer words such as 다크머지 in search).
    )

    /** The best fits for [query], best first. */
    fun search(query: String, entries: List<SettingEntry> = entries(), limit: Int = 12): List<SettingEntry> =
        SearchText.best(entries, limit) { SearchText.score(query, it.title, it.keywords, it.where) }
}
