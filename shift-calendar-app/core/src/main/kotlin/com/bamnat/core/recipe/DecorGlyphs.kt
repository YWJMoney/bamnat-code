package com.bamnat.core.recipe

/**
 * [LAY-02] 1.36 요소 모음 (개발계획 875, 제안 15 "앱이 직접 그린 벡터 도형·아이콘 수백 개"): every shape and icon a decoration may be, by
 * an id that stays the same in every version (a recipe names them). Shapes are drawn by the app from their outline (app DecorShapes);
 * icons are the Material Symbols the app already carries (Apache 2.0, in the open-source notice). Emoji come from [RecipeText]'s list and
 * text boxes are the maker's own words, so the four together are well over a few hundred.
 *
 * The server keeps the same ids (server/company-worker/src/decor.js); `DecorGlyphsTest` holds the two together.
 */
object DecorGlyphs {
    /** A glyph with the words 요소 모음's search finds it by. */
    data class Glyph(val id: String, val label: String, val group: String)

    val SHAPE_LIST: List<Glyph> = listOf(
        // 기본
        g("rect", "네모", "기본"), g("round", "둥근 네모", "기본"), g("circle", "동그라미", "기본"), g("pill", "알약", "기본"),
        g("tri", "세모", "기본"), g("rtri", "직각 세모", "기본"), g("diamond", "마름모", "기본"), g("pent", "오각형", "기본"),
        g("hex", "육각형", "기본"), g("oct", "팔각형", "기본"), g("semi", "반원", "기본"), g("quarter", "사분원", "기본"),
        g("arch", "아치", "기본"), g("ring", "고리", "기본"), g("frame", "테두리", "기본"), g("para", "평행사변형", "기본"),
        g("trap", "사다리꼴", "기본"), g("plus", "더하기", "기본"), g("cross", "곱하기", "기본"),
        // 별·반짝
        g("star4", "네 꼭지 별", "별·반짝"), g("star5", "별", "별·반짝"), g("star6", "여섯 꼭지 별", "별·반짝"), g("star8", "여덟 꼭지 별", "별·반짝"),
        g("burst", "터짐", "별·반짝"), g("sparkle", "반짝", "별·반짝"), g("seal", "도장 테", "별·반짝"),
        // 자연
        g("heart", "하트", "자연"), g("drop", "물방울", "자연"), g("moon", "달", "자연"), g("sun", "해", "자연"), g("cloud", "구름", "자연"),
        g("leaf", "잎", "자연"), g("flower", "꽃", "자연"), g("wave", "물결", "자연"), g("mountain", "산", "자연"), g("snow", "눈송이", "자연"),
        g("blob1", "말랑 1", "자연"), g("blob2", "말랑 2", "자연"), g("blob3", "말랑 3", "자연"),
        // 말풍선·표시
        g("bubble", "말풍선", "말풍선·표시"), g("bubble_r", "오른쪽 말풍선", "말풍선·표시"), g("think", "생각 풍선", "말풍선·표시"),
        g("tag", "꼬리표", "말풍선·표시"), g("ribbon", "리본 띠", "말풍선·표시"), g("bookmark", "책갈피", "말풍선·표시"), g("shield", "방패", "말풍선·표시"),
        g("badge", "배지", "말풍선·표시"), g("pin", "핀", "말풍선·표시"), g("check", "체크", "말풍선·표시"), g("tape", "마스킹 테이프", "말풍선·표시"),
        g("corner", "모서리 장식", "말풍선·표시"),
        // 선·화살표
        g("line_h", "가로선", "선·화살표"), g("line_v", "세로선", "선·화살표"), g("line_d", "빗금", "선·화살표"), g("dash_h", "점선", "선·화살표"),
        g("zigzag", "지그재그", "선·화살표"), g("squiggle", "꼬불선", "선·화살표"), g("arrow_r", "오른쪽 화살표", "선·화살표"),
        g("arrow_l", "왼쪽 화살표", "선·화살표"), g("arrow_u", "위 화살표", "선·화살표"), g("arrow_d", "아래 화살표", "선·화살표"),
        g("chevron", "꺾쇠", "선·화살표"), g("underline", "밑줄 붓", "선·화살표"), g("circle_mark", "동그라미 표시", "선·화살표"),
    )

    val ICON_LIST: List<Glyph> = listOf(
        // 마음
        g("favorite", "하트", "마음"), g("favorite_border", "빈 하트", "마음"), g("star", "별", "마음"), g("star_border", "빈 별", "마음"),
        g("emoji_emotions", "웃음", "마음"), g("mood", "기분 좋음", "마음"), g("mood_bad", "기분 나쁨", "마음"), g("thumb_up", "좋아요", "마음"),
        g("thumb_down", "싫어요", "마음"), g("auto_awesome", "반짝", "마음"), g("celebration", "축하", "마음"), g("cake", "케이크 생일", "마음"),
        g("card_giftcard", "선물", "마음"), g("emoji_events", "트로피", "마음"), g("military_tech", "메달", "마음"), g("diamond", "다이아", "마음"),
        // 날씨·자연
        g("wb_sunny", "해 맑음", "날씨·자연"), g("dark_mode", "달 밤", "날씨·자연"), g("light_mode", "해 낮", "날씨·자연"), g("nights_stay", "밤 구름", "날씨·자연"),
        g("bedtime", "잠 밤", "날씨·자연"), g("cloud", "구름", "날씨·자연"), g("umbrella", "우산 비", "날씨·자연"), g("ac_unit", "눈 결정", "날씨·자연"),
        g("thunderstorm", "번개 폭풍", "날씨·자연"), g("water_drop", "물방울", "날씨·자연"), g("local_florist", "꽃", "날씨·자연"), g("spa", "연꽃 쉼", "날씨·자연"),
        g("park", "나무 공원", "날씨·자연"), g("eco", "잎", "날씨·자연"), g("grass", "풀", "날씨·자연"), g("pets", "발자국 반려동물", "날씨·자연"),
        g("beach_access", "파라솔 바다", "날씨·자연"), g("terrain", "산", "날씨·자연"), g("waves", "물결", "날씨·자연"), g("bolt", "번개", "날씨·자연"),
        g("local_fire_department", "불", "날씨·자연"), g("whatshot", "인기 불꽃", "날씨·자연"), g("emoji_nature", "벌 자연", "날씨·자연"), g("filter_vintage", "꽃잎", "날씨·자연"),
        // 먹기
        g("local_cafe", "커피 카페", "먹기"), g("coffee", "커피잔", "먹기"), g("restaurant", "식당 밥", "먹기"), g("local_pizza", "피자", "먹기"),
        g("icecream", "아이스크림", "먹기"), g("bakery_dining", "빵", "먹기"), g("lunch_dining", "햄버거 점심", "먹기"), g("ramen_dining", "라면 국수", "먹기"),
        g("local_bar", "칵테일 술", "먹기"), g("wine_bar", "와인", "먹기"), g("sports_bar", "맥주", "먹기"), g("local_drink", "물컵", "먹기"),
        // 일
        g("work", "가방 일", "일"), g("business_center", "서류 가방", "일"), g("engineering", "기술자", "일"), g("construction", "공사 현장", "일"),
        g("handyman", "공구", "일"), g("build", "렌치", "일"), g("agriculture", "농사", "일"), g("factory", "공장", "일"),
        g("store", "가게", "일"), g("storefront", "가게 앞", "일"), g("local_hospital", "병원", "일"), g("medical_services", "의료 가방", "일"),
        g("school", "학교 졸업", "일"), g("computer", "컴퓨터", "일"), g("badge", "사원증", "일"), g("local_shipping", "트럭 배송", "일"),
        // 돈
        g("attach_money", "달러 돈", "돈"), g("savings", "저금통", "돈"), g("payments", "지폐", "돈"), g("credit_card", "카드", "돈"),
        g("account_balance_wallet", "지갑", "돈"), g("shopping_cart", "장바구니", "돈"), g("shopping_bag", "쇼핑백", "돈"), g("receipt", "영수증", "돈"),
        g("trending_up", "오름", "돈"), g("trending_down", "내림", "돈"), g("show_chart", "꺾은선", "돈"), g("pie_chart", "원그래프", "돈"),
        g("bar_chart", "막대그래프", "돈"), g("calculate", "계산기", "돈"), g("redeem", "쿠폰", "돈"), g("local_offer", "가격표", "돈"),
        // 시간·일정
        g("alarm", "알람", "시간·일정"), g("schedule", "시계", "시간·일정"), g("timer", "타이머", "시간·일정"), g("hourglass_empty", "모래시계", "시간·일정"),
        g("event", "일정", "시간·일정"), g("calendar_month", "달력", "시간·일정"), g("today", "오늘", "시간·일정"), g("event_note", "일정 메모", "시간·일정"),
        g("push_pin", "압정", "시간·일정"), g("bookmark", "책갈피", "시간·일정"), g("flag", "깃발", "시간·일정"), g("label", "라벨", "시간·일정"),
        g("notifications", "종 알림", "시간·일정"), g("notifications_active", "울리는 종", "시간·일정"), g("checklist", "체크 목록", "시간·일정"), g("task_alt", "할 일 끝", "시간·일정"),
        // 쉬기·몸
        g("hotel", "잠 침대", "쉬기·몸"), g("king_bed", "큰 침대", "쉬기·몸"), g("weekend", "소파 주말", "쉬기·몸"), g("bathtub", "욕조", "쉬기·몸"),
        g("self_improvement", "명상", "쉬기·몸"), g("fitness_center", "운동 헬스", "쉬기·몸"), g("directions_run", "달리기", "쉬기·몸"), g("directions_walk", "걷기", "쉬기·몸"),
        g("directions_bike", "자전거", "쉬기·몸"), g("pool", "수영", "쉬기·몸"), g("sports_soccer", "축구", "쉬기·몸"), g("sports_esports", "게임", "쉬기·몸"),
        g("medication", "약", "쉬기·몸"), g("healing", "반창고", "쉬기·몸"), g("monitor_heart", "심장 박동", "쉬기·몸"), g("psychology", "마음 생각", "쉬기·몸"),
        // 다니기
        g("directions_car", "자동차", "다니기"), g("directions_bus", "버스", "다니기"), g("directions_subway", "지하철", "다니기"), g("train", "기차", "다니기"),
        g("flight", "비행기 여행", "다니기"), g("sailing", "돛단배", "다니기"), g("rocket_launch", "로켓", "다니기"), g("location_on", "위치", "다니기"),
        g("map", "지도", "다니기"), g("explore", "나침반", "다니기"), g("home", "집", "다니기"), g("apartment", "아파트", "다니기"),
        // 즐기기
        g("music_note", "음표 노래", "즐기기"), g("headphones", "헤드폰", "즐기기"), g("photo_camera", "카메라 사진", "즐기기"), g("palette", "팔레트 그림", "즐기기"),
        g("brush", "붓", "즐기기"), g("movie", "영화", "즐기기"), g("menu_book", "책", "즐기기"), g("edit", "연필", "즐기기"),
        g("lightbulb", "전구 생각", "즐기기"), g("science", "실험", "즐기기"), g("chat", "대화", "즐기기"), g("mail", "편지", "즐기기"),
        // 사람
        g("person", "사람", "사람"), g("people", "두 사람", "사람"), g("groups", "여럿", "사람"), g("face", "얼굴", "사람"),
        g("child_care", "아기", "사람"), g("family_restroom", "가족", "사람"), g("support_agent", "상담", "사람"), g("volunteer_activism", "나눔 손", "사람"),
        // 표시
        g("check_circle", "체크 동그라미", "표시"), g("cancel", "엑스 동그라미", "표시"), g("add_circle", "더하기 동그라미", "표시"), g("info", "안내", "표시"),
        g("warning", "주의", "표시"), g("error", "느낌표", "표시"), g("help", "물음표", "표시"), g("lock", "자물쇠", "표시"),
        g("key", "열쇠", "표시"), g("verified", "확인 배지", "표시"), g("workspace_premium", "상장 배지", "표시"), g("recycling", "재활용", "표시"),
        g("arrow_forward", "오른쪽", "표시"), g("arrow_back", "왼쪽", "표시"), g("arrow_upward", "위", "표시"), g("arrow_downward", "아래", "표시"),
    )

    private fun g(id: String, label: String, group: String) = Glyph(id, label, group)

    val SHAPES: Set<String> = SHAPE_LIST.map { it.id }.toSet()
    val ICONS: Set<String> = ICON_LIST.map { it.id }.toSet()

    fun shape(id: String): Glyph? = SHAPE_LIST.firstOrNull { it.id == id }
    fun icon(id: String): Glyph? = ICON_LIST.firstOrNull { it.id == id }

    /** Glyphs of [list] whose words hold every word of [q]. */
    fun search(list: List<Glyph>, q: String): List<Glyph> {
        val words = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return list
        return list.filter { g -> words.all { w -> g.label.contains(w) || g.group.contains(w) || g.id.contains(w.lowercase()) } }
    }
}
