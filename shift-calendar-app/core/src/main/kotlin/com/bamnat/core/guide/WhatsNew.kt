package com.bamnat.core.guide

/**
 * [GUIDE-08] What an update brought, shown once after the update: each line names what changed in a
 * sentence and, when it is a feature of [FeatureRegistry], opens it. A fresh install sees none of it,
 * since its first days are the checklist's.
 */
object WhatsNew {
    data class Line(val text: String, val featureId: String? = null)
    data class Release(val versionCode: Int, val versionName: String, val lines: List<Line>)

    val releases: List<Release> = listOf(
        // 1.28.0 (계획/1.28-다크머지-관리자앱-로딩-통합계획.md; 통합 folded AL·AO into four lines, AM·AN are the developer's app).
        Release(39, "1.28.0", listOf(
            // [PERF-07][PERF-08][PERF-09][PERF-10] Bundle AO.
            Line("앱이 더 빨리 열려요. 달력은 보이는 달부터 먼저 그리고, 나머지 준비는 달이 보인 뒤에 조금씩 나눠서 해요"),
            // [PERF-15] Bundle AO.
            Line("앱을 여는 데 걸린 시간을 ‘알람 점검’ 화면에서 볼 수 있어요", "alarm.check"),
            // [FLAG-14] Bundle AL.
            Line("‘알람 점검’ 화면 아래 ‘기능 상태’에서 기능마다 켜졌는지, 꺼졌으면 왜 그런지(남은 조건) 보여요. 문의할 때 그 부분을 보내 주시면 돼요", "alarm.check"),
            // [FLAG-16][PERF-16] Bundles AL·AO.
            Line("사용 통계를 보내는 분께 처리방침 3.2가 시행된 뒤 한 번 더 물어요. ‘동의’를 고른 경우에만 켜진 기능 목록과 앱 여는 데 걸린 시간을 함께 보내요", "privacy.usageStats"),
        )),
        // 1.27.0 (계획/1.27-리뷰반영-사진인식-통합계획.md; 통합 folded AG·AH·AI·AJ·AK into eight lines).
        Release(38, "1.27.0", listOf(
            // [READ-13][READ-17] Bundle AJ.
            Line("사진 인식을 다시 만들었어요. 한 장을 여러 방법으로 읽어 맞춰 보고, 사진 옆에 읽은 칸을 보여 주며 ‘사진과 내용이 같은가요?’를 물어요. 다른 칸만 눌러 고치면 돼요", "photo.sameCheck"),
            // [PAYS-16][PAYS-17][PAYS-19] Bundle AJ.
            Line("명세서 없이 회사 규칙만으로도 달력 근무로 급여를 계산해요. 비어 있는 기본급·시수는 급여 합계 아래 ‘이것만 넣으면 정확해져요’에서 바로 넣어요", "pay.ruleNeeds"),
            // [SPAT-07][PLAZA-14][PLAZA-15][PLAZA-16] Bundle AH.
            Line("회사 이름을 치는 대로 같은 회사 분들의 대표 주기와 쓰는 사람 수가 바로 보여요. 고른 주기를 나누면 그 회사(또는 사업장)의 대표가 되고, 급여 방식도 같은 회사 분들 것을 가져와요", "plaza.leadCycle"),
            // AI 1.27 [PLAZA-30][PLAZA-32][PLAZA-33][PLAZA-34] (계획/1.27-AI-개발계획.md 441·443·444·445).
            Line("회사 설정을 쓰면 광장이 ‘우리 회사 정보 광장’이 돼요. 근무·급여를 바꾸면 바뀐 것을 나눌지 묻고, ‘명세서와 맞춰 봤어요’는 명세서로 맞춰 본 기록이 있을 때만 붙어요", "plaza.info"),
            // AG 1.27 [INVITE-10][INVITE-11] Bundle AG.
            Line("친구 초대 링크로 깔면 앱이 저절로 알아봐요. 친구 5명이 각자 7일 이상 쓰면 프로 1개월, 5명 더 채울 때마다 또 1개월", "pro.invite"),
            // AG 1.27 [MON-12][INVITE-12] Bundle AG.
            Line("프로 기능을 누르면 ‘광고 보고 오늘 하루 열기’와 ‘친구 초대로 프로 받기’를 함께 보여요. 설정 맨 위에서 친구 초대와 ‘불편한 점 알려 주기’를 바로 해요", "grow.card"),
            // [MEMO-03][MEMO-05] Bundle AK.
            Line("메모를 초성·날짜·그날 근무(야간)로 찾고, ‘다음 야간 때’라고 쓰면 그 근무 출근 1시간 전에 알려 드려요", "memo.shiftRemind"),
            // [INV-42][INV-44] Bundle AK.
            Line("투자 ‘체결 내역 캡처로 채우기’가 이미 적은 건 건너뛰고, 다르면 고칠지 묻고, 새 것만 확인해요. 믿으면 ‘다음부터 묻지 않고 바로 넣기’", "invest.captureTrust"),
        )),
        // 1.26.0 (계획/1.26-회사교대주기-메뉴순서-초대-통합계획.md 1장 4번: AC 2·AD 1·AE 1; 통합 folds them).
        Release(37, "1.26.0", listOf(
            // [NAV-05][NAV-06] Bundle AD.
            Line("아래 메뉴를 길게 누르면 순서를 바꿀 수 있어요. 맨 앞에 둔 메뉴가 앱을 켤 때 첫 화면이 돼요(설정 › 달력 화면 › 첫 화면·메뉴 순서)", "nav.tabOrder"),
            // [SPAT-02][SPAT-05] Bundle AC.
            Line("회사 이름으로 공개 자료의 근무주기를 찾아 순서와 오늘 날짜만 맞추면 달력에 들어가요", "plaza.cycles"),
            Line("우리 회사가 없으면 처음으로 넣어 광장에 올려요. 급여 정보까지 올리면 보상을 드려요", "plaza.cycles"),
            // [INVITE-02][INVITE-03] Bundle AE.
            Line("설정 › 프로 › ‘친구 초대’로 링크를 보내요. 깐 친구 5명이 각자 7일 이상 쓰면 나에게 프로 1개월, 5명 더 채울 때마다 또 1개월", "pro.invite"),
            // AF (계획/1.26-AF-개발계획.md): 결정 D79.
            Line("다니는 회사 광장에 근무 순서·계산 방식을 한 번 허락하면 저절로 나누고, 참여한 회사에서는 다른 분들이 바꾼 값만 골라 써요", "plaza.room"),
        )),
        // 1.25.0 (계획/1.25-사용자발견-전면개선-통합계획.md 1장 5번: U 2·V 2·W 2·X 2·Y 1·Z 1, 10 lines at most; 통합 folds them).
        Release(36, "1.25.0", listOf(
            // [READ-02][READ-05][READ-06] Bundle U.
            Line("듀티표 사진을 다시 만들었어요. 기울게 찍어도 날짜에 맞춰 읽고, 줄이 여러 개면 ‘어느 줄(조)이 내 것인가요?’를 묻고 기억해요. 못 읽으면 사진 위 날짜 줄과 내 줄만 눌러 주세요", "shift.roster"),
            // [READ-08][READ-10][READ-11] Bundle U.
            Line("명세서와 회사 계산 방법 사진을 함께 넣으면 금액과 규칙을 같이 읽어요. ‘이번 달 전부 넣기’는 사진·엑셀도 되고, 넣은 뒤 도구에서 ‘방금 넣은 것 되돌리기’", "shift.dutyTyping"),
            // [ALRM-07][ALRM-08][ALRM-09] Bundle AB.
            Line("알람이 울리면 소리·전원 버튼으로 끄고, ‘오늘 남은 알람 모두 끄기’ 한 번이면 오늘은 더 안 울려요. 잠금 화면에 안 뜨게 막는 설정도 알람 점검이 찾아 줘요", "alarm.allOff"),
            // [TAX-01][TAX-02][TAX-05] Bundle W.
            Line("들어온 월급은 수입에 한 번만 세고 실수령 기록으로 남아, 돈 탭 ‘세금·실수령’이 이달 세전·세후와 자영업 올해 세금·내는 날을 알려 줘요(프로)", "money.takeHome"),
            // [INV-38][INV-40] Bundle W.
            Line("매도 단가를 넣으면 수수료·거래세가 바로 계산되고 기본 수수료(처음 0.05%)는 저장돼요. 단가 없는 체결 알림은 체결 내역 캡처로 채워요", "invest.priceFill"),
            // [PAYS-12][PAYS-14] Bundle V.
            Line("급여 설정이 쉬워졌어요. 어떻게 일하는지와 기본급만 고르면 한 장으로 확인하고, 모르는 건 명세서와 회사 계산 방법 사진 두 장으로 맞춰요", "pay.slipRules"),
            // [HOLI-01][PAYX-48] Bundle V.
            Line("빨간날 근무는 회사 방식(법대로·특근처럼·대체휴일·교대제)대로, 빨간날 휴가는 근무일로만 셈해요. ‘월급날 비교’에서 들어온 월급을 바로 적어요", "pay.holidayWork"),
            // [PLAZA-02][PLAZA-03] Bundle X.
            Line("같은 회사 설정이 조금 다르면 ‘다른 점 말하기’로 그 회사 값에서 다른 곳만 바꿔요. 첫 설정 급여 단계에서 회사 급여를 쓸지 묻고, 회사는 초성으로도 찾아요", "plaza.diff"),
            // [PLAZA-04][PLAZA-05][PLAZA-06] Bundle X.
            Line("같은 부서 판이 여럿이면 더 많이 쓰는 판이 대표 판이 되고 예전 판은 비교해 되돌릴 수 있어요. 첫 설정을 놓쳤다면 설정 › 내 근무 › ‘처음 설정 다시 받기’", "setup.again"),
            // [FIT-01][FIT-03][TIER-07][VIS-06] Bundle Y.
            Line("달력마다 하는 일에 맞는 기능만 보이고, 숨긴 건 ‘더 보기’에서 더해요. 새 달력은 ‘어떤 일을 하고 계신가요?’로 시작하고, 프로 기능엔 ‘프로’ 표시와 모아보기, 설정은 색으로 묶였어요", "pro.collection"),
            // [NAV-02][UX-10][UX-13][ALRM-06][SHFT-09][BAK-01] Bundle Z.
            Line("뒤로 가기는 바로 전 화면으로, 숫자는 깜박이지 않고, 하루 창은 스크롤하다 닫히지 않아요. 알람은 시·분 휠로, 근무 종류는 지우고 되돌리고, 모든 설정을 새 휴대폰으로 옮겨요", "data.movePhone"),
        )),
        // 1.24.0 (계획/1.24-직접입력-전면개선-통합계획.md 1장 5번: P 2·Q 1·R 1·S 1, 8 lines at most; 통합 folds them).
        Release(35, "1.24.0", listOf(
            // [ROST-01][ROST-03][ROST-05] Bundle P.
            Line("‘한 달 근무 넣기’: 그 달 달력 위 키패드로 하루씩 누르거나 ‘이 순서 반복’으로 한 달을 넣어요. 듀티표 사진·파일도 같은 달력에 채우고, 반차·병가도 제대로 읽어요", "shift.roster"),
            // [ROST-07][ROST-09] Bundle P.
            Line("갤러리·카톡에서 듀티표를 밤낮으로 ‘공유’하면 바로 읽고, 빠른 입력엔 ‘이번 달 전부 넣기’와 오전·오후 반차가 생겼어요", "shift.dutyTyping"),
            // [INPUT-01..04][PAYX-47] Bundle Q.
            Line("숫자·시각·회사 규칙을 글자로 치지 않아도 돼요. 칩과 −·+로 고르고, 호봉표는 줄마다 넣고, 복사해 둔 명세서·문자는 한 번 눌러 붙여요", "pay.ruleBuilder"),
            // [SETUP-15][COMM-12][INQ-06] Bundle S.
            Line("처음 화면에서 무슨 일 하는지 먼저 묻고 그 일에 맞는 알림만 켜요. 회사 이름을 넣었으면 급여 화면에서 같은 회사 분들 규칙으로 바로 가고, 문의는 모두에게 올릴지 골라요", "settings.recommended"),
            // [CAL-50][CAL-51][VIS-04][STAT-07] Bundle R.
            Line("달력 위 알림은 하나만 펼치고 나머지는 한 줄로 접어요. 작은 화면·옆으로 눕힌 폰·큰 글씨에서도 달이 보이고, 근무 수는 어느 화면이든 같은 숫자예요"),
        )),
        // [SIMPLE-03] Grouped by what it gives, each line opening its feature; every bundle's own lines are in
        // 계획/1.22-*-개발계획.md.
        // 1.23.0: one line per bundle's gift (J·K·L·I here; M adds its own), each opening its feature
        // (계획/1.23-*-개발계획.md). 1.22's rule of 6~8 lines holds here too.
        Release(34, "1.23.0", listOf(
            // [SIMPLE-03] 6~8 lines, one per feature. 2026-10-23 통합: J1·K1·L2·I1·M1 = 6 lines, N2 = 8.
            // [VIS-03][UX-08][A11Y-04][SETUP-14][SET-05] Bundle J.
            Line("글씨를 크게 키워도 버튼·탭이 잘리지 않고, 시간은 시작·끝·휴게를 한 창에서 골라요. 근무 알람은 한 번에 앞당기거나 늦춰요", "alarm.moveAll"),
            // Bundle K ('내 월급 맞나요' + 연차 원장 together).
            Line("‘내 월급 맞나요’: 명세서를 줄마다 맞춰 적게 준 줄과 빠진 줄을 보여 주고, 연차 화면엔 회사의 촉진 기록과 원장 PDF·CSV 내보내기가 생겼어요", "pay.slipAudit"),
            // Bundle L (계획/1.23-L-서버-광장-알람-개발계획.md L2), 밤낮 소식 + 우리 조 모아 보기, then 알람 점검.
            Line("‘밤낮 소식’: 문의 답·광장 소식·공지를 하루 한 번 알림 하나로 받고, ‘우리 조 모아 보기’로 동료 근무를 6자 코드로 한 주 나란히 봐요", "plaza.news"),
            // [ALRM-03][ALRM-04] Bundle O folded in (2026-10-23 통합).
            Line("알람: 잠·끄기 기록으로 알람 시각을 제안하고, 끄기 미션에 ‘증명 사진’이 생겼어요. 알람 점검엔 7일 성적표와 서버 연결 점검, 보내는 것과 권한 한눈에", "alarm.check"),
            // [CAL-43][WIDG-05][SCHED-03][SCHED-07] Bundle I.
            Line("날짜 메모에 ‘내일 야간 대근 7시’처럼 적으면 근무로 넣어 드리고, ‘다음 근무까지’ 위젯이 생겼어요. 순서가 밀린 날은 길게 눌러 그날부터 다시 맞춰요", "calendar.quickText"),
            // [PERF-04][REL-15] Bundle M.
            Line("앱이 51MB에서 8MB로 가벼워졌고(글자 인식은 처음 쓸 때 받아요), 백업 파일은 사진까지 한 파일에 담아 비밀번호로 봉해요", "data.backup"),
            // [EASY-01..10][OBS-01..03] Bundle N, the two lines kept for it.
            Line("‘우선 둘러볼게요’로 예시 달력부터 보고, 필요한 정보는 질문 카드로 하루 한두 개만 물어요. 설정 맨 위 ‘내 정보’에서 한곳에 보고 바로 고쳐요", "profile.summary"),
            Line("‘밤낮이 한 일’: 알람 다시 잡기·백업·계산 자료 갱신처럼 밤낮이 알아서 한 일을 날짜별로 보여 줘요", "journal.list"),
        )),
        Release(33, "1.22.0", listOf(
            Line("처음 화면은 한 장, 설정은 7묶음으로 줄었어요. 켜 둬도 괜찮은 알림을 한 번에 켜고, 설정 › 알림과 잠에서 언제든 꺼요", "settings.recommended"),
            Line("‘연차 원장’이 생겼어요. 입사일만 넣으면 연차를 법대로 세고, 없어지기 전에 알려 주고, 보상휴가도 쌓아요", "leave.ledger"),
            Line("실수령 세금은 국세청 간이세액표대로, 공무원·경찰·소방 봉급은 계급·호봉으로 채워요. 탄력근무·포괄임금·호봉표도 넣어요", "pay.publicGrade"),
            Line("휴식 점검 기준을 병원·회사에 맞게 바꾸고, N-O-D 같은 순서와 이달 나이트 수당을 미리 봐요", "shift.restRules"),
            Line("듀티표를 사진·엑셀·CSV로 넣고, 헷갈린 칸만 골라 고쳐요. 경찰·소방 교대 순서도 생겼어요", "shift.roster"),
            Line("위젯에 그날 메모 첫 줄과 ‘오늘 모든 조’가 보이고, 돈·메모 화면도 카드 순서를 바꿔요", "widget.memo"),
            Line("영수증을 찍으면 금액·날짜·가게를 채우고, 백업에 사진도 같이 들어가요", "money.receiptScan"),
            Line("광장 소식은 하루 한 번 묶어서 오고, 보기 싫은 사람은 가려요. 사용 통계는 직접 켤 때만 보내요", "plaza.news"),
        )),
        Release(32, "1.21.0", listOf(
            Line("‘모든 조 보기’가 새로워졌어요. 내 조가 몇 번째여도 맞게 돌고, 날짜를 누르면 대근 들어올 수 있는 조를 이유와 함께 찾아요", "shift.teams"),
            Line("달력 위 ⋮ 대신 이름이 붙은 ‘도구’ 버튼과 ‘모든 조’ 버튼이 생겼어요"),
            Line("근무표를 보며 D·E·N·OFF를 누르면 다음 날로 넘어가는 ‘한 달 근무 연달아 넣기’가 생겼어요. 역순·나이트 뒤 쉬는 날도 알려 줘요", "shift.dutyTyping"),
            Line("투자 달력이 증권사·거래소 체결 알림에서 매수·매도를 찾아요. 확인하면 알림 온 앱과 코인·주식 표시까지 적어요", "invest.alerts"),
            Line("회사 광장이 커졌어요. 직군·병원·지역 광장, 글에 맞아요·달라요와 답글, 규칙 고치자는 제안, 내 월급으로 시험, 지역 임금표가 생겼어요"),
            Line("급여 설정에 공무원·경찰·소방이 생겼어요. 공무원수당 규정대로 야간·휴일·시간외 수당을 넣어 드려요", "pay.calc"),
            Line("예상 실수령이 그 달의 요율(2027년 국민연금 등)로 계산돼요"),
        )),
        Release(31, "1.20.0", listOf(
            Line("근무·급여 화면의 ‘급여 리포트’로 한 달이나 1년 급여를 계산 풀이까지 PDF·CSV로 남겨요", "pay.report"),
            Line("‘지난달·작년 비교’ 카드로 근무시간·야간·급여를 지난달, 작년 같은 달과 나란히 봐요", "stats.compare"),
            Line("근무를 휴대폰 캘린더(삼성·구글 캘린더 앱)에 적을 수 있어요", "calendar.phone"),
            Line("근무표 링크를 캘린더 앱이 구독하게 하거나, 링크를 5개까지 따로 만들고 1년까지 열어 둘 수 있어요", "share.feed"),
            Line("고른 파일(구글 드라이브 등)에 암호 건 백업을 매일 자동으로 덮어써요", "data.autoBackup"),
            Line("알람을 끌 때 계산 문제나 흔들기를 하게 하고, 끈 뒤 ‘일어났나요?’를 물을 수 있어요. 5분이 지나면 미션 없이도 꺼요", "alarm.mission"),
            Line("근무표를 A4로 인쇄할 PDF로 만들어요", "share.rosterPdf"),
            Line("모두 프로 기능이지만 정식 출시 전까지는 무료예요", "app.pro"),
        )),
        Release(30, "1.19.0", listOf(
            Line("설정에 ‘프로’가 생겼어요. 정식 출시 뒤 무료와 프로가 어떻게 나뉘는지 미리 볼 수 있어요. 지금은 모든 기능이 무료예요", "app.pro"),
            Line("근무표·알람·급여 계산·연차·메모·위젯·워치는 정식 출시 뒤에도 무료예요"),
            Line("정식 출시 전부터 써 주신 분께는 출시 뒤 프로를 일정 기간 무료로 열어 드려요. 결제 정보는 받지 않아요"),
            Line("‘광고 개인정보 선택’에서 광고가 쓰는 정보와 끄는 방법을 볼 수 있어요", "privacy.adChoices"),
        )),
        Release(29, "1.18.0", listOf(
            Line("3조 2교대·4조 2교대처럼 조가 있으면 ‘모든 조 보기’에서 조마다 근무와 그날 쉬는 조를 봐요", "shift.teams"),
            Line("열두 달을 한 화면에 보는 ‘1년 달력’이 생겼어요. 한 해의 근무 날 수와 휴가도 세요", "calendar.year"),
            Line("빠른 입력으로 쉬는 날에 근무를 넣으면 대근으로 넣을지 물어봐요"),
            Line("주 근무시간은 달력이 일요일부터 보여도 월요일부터 일요일까지로 세요"),
            Line("달력 아래 하루 카드는 처음엔 꺼져 있어요. ‘화면 꾸미기’에서 카드 순서와 켜고 끄기를 바꿀 수 있어요", "calendar.customize"),
            Line("‘앱 잠금’을 켜면 열 때 지문·얼굴·PIN을 물어요. 알람은 그대로 울려요", "privacy.lock"),
            Line("문의를 보내면 접수 → 확인 → 개발 → 반영 버전까지 진행 상황이 보여요", "help.inquiry"),
        )),
    )

    /** The releases after [seenCode] up to [currentCode], newest first; none for a fresh install (seenCode 0). */
    fun since(seenCode: Int, currentCode: Int): List<Release> =
        if (seenCode <= 0) emptyList() else releases.filter { it.versionCode in (seenCode + 1)..currentCode }.sortedByDescending { it.versionCode }
}
