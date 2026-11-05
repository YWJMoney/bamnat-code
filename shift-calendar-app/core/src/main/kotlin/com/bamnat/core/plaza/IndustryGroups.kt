package com.bamnat.core.plaza

import java.time.LocalDate

/**
 * [CYC-09][CYC-04] 1.38 묶음 K: the public rows' 업종 in a few groups people know their work by, each with one line on how
 * its shifts usually go (what the public rows show), and the days the law names for that work (기관 기념일).
 *
 * The 업종 column of tools/data/company-cycles.csv has some 70 words (화학·환경, 반도체 소재·화학, 철강 협력…); a row goes to
 * the first group one of whose words is in its 업종, else [OTHER]. The order matters: 해양경찰 before 경찰, 반도체 before 화학,
 * 철강 before 물류, 공항 before 보안.
 *
 * The days are only ones a law sets (각종 기념일 등에 관한 규정 별표, 소방기본법 제7조, 해양경찰법 제4조), never a holiday:
 * they are shown in the cell like a red day's name but not red, and 설정 › 달력 turns them off.
 */
object IndustryGroups {
    /** A day the law names for a kind of work, every year on [month]/[day]. */
    data class OrgDay(val month: Int, val day: Int, val name: String, val basis: String)

    data class Group(
        val key: String,
        val name: String,
        /** One line on how the work usually goes, from the public rows. */
        val note: String,
        internal val words: List<String>,
        val days: List<OrgDay> = emptyList(),
    )

    val OTHER = Group("other", "그 밖의 업종", "회사마다 달라요. 근무형태로 찾거나 우리 회사 근무표를 올려 주세요", emptyList())

    val ALL: List<Group> = listOf(
        Group("fire", "소방", "119 안전센터·구급대·상황실. 당비비(3조1교대)와 주야비휴(4조2교대)가 많아요", listOf("소방"),
            listOf(OrgDay(11, 9, "소방의 날", "소방기본법 제7조"))),
        Group("coast", "해양경찰", "파출소는 3교대, 함정은 바다 근무와 육지 근무를 번갈아 해요", listOf("해양경찰", "해경"),
            listOf(OrgDay(9, 10, "해양경찰의 날", "해양경찰법 제4조"))),
        Group("police", "경찰", "지구대·파출소는 대부분 주야비휴(4조2교대), 12시간씩이에요", listOf("경찰"),
            listOf(OrgDay(10, 21, "경찰의 날", "각종 기념일 등에 관한 규정 별표"))),
        Group("correction", "교정", "교도소·구치소는 4부제(주야비휴)와 8일 변형 4부제가 있어요", listOf("교정"),
            listOf(OrgDay(10, 28, "교정의 날", "각종 기념일 등에 관한 규정 별표"))),
        Group("hospital", "병원·요양", "간호·요양은 매달 나오는 3교대 근무표가 많아 근무 종류만 넣고 날짜는 칠해요", listOf("병원", "요양", "간호"),
            listOf(OrgDay(4, 7, "보건의 날", "각종 기념일 등에 관한 규정 별표"))),
        Group("rail", "철도·지하철", "역무·승무는 주야비휴(4조2교대)가 많고, 승무는 사업소 근무표를 따라요", listOf("철도", "지하철"),
            listOf(OrgDay(6, 28, "철도의 날", "각종 기념일 등에 관한 규정 별표"))),
        Group("airport", "공항·검역·세관", "24시간 도는 곳이라 당비비, 격일, 3조2교대가 섞여 있어요", listOf("공항", "검역", "세관")),
        Group("transit", "버스·택시", "법인택시는 2인 1차 1일 2교대, 1인 1차는 8부제 같은 쉬는 날 규칙을 따라요", listOf("버스", "택시")),
        Group("watch", "관제·보안·경비", "CCTV 관제는 주야비휴(4조2교대), 경비는 격일 24시간이 많아요", listOf("관제", "보안", "경비", "시설관리", "용역")),
        Group("power", "발전·에너지", "발전소 운전은 4조3교대·5조3교대, 2222·3333 같은 코드표가 많아요", listOf("발전", "전력", "원자력", "에너지", "가스")),
        Group("semi", "반도체·전자", "4조3교대(6근2휴)와 4조2교대가 많아요", listOf("반도체", "전자", "디스플레이", "PCB", "2차전지", "배터리")),
        Group("chem", "정유·화학", "4조3교대(6근2휴)와 4조2교대(2222)가 많아요", listOf("정유", "화학")),
        Group("steel", "철강·금속", "4조2교대와 4조3교대가 많아요", listOf("철강", "비철", "금속", "다이캐스팅", "시멘트")),
        Group("auto", "자동차", "완성차는 주간연속2교대, 부품사는 2조2교대가 많아요", listOf("자동차")),
        Group("food", "식품·담배", "3조3교대(주 단위)와 2조2교대가 많아요", listOf("식품", "주류", "담배")),
        Group("logistics", "물류", "허브 상하차는 야간 고정, 센터는 주야 교대가 많아요", listOf("물류", "포장")),
        Group("maker", "그 밖의 제조", "4조2교대·3조2교대·2조2교대가 고루 있어요", listOf("제조", "섬유", "제지", "제약", "바이오", "소재", "의료용품", "목재", "진공")),
        Group("hotel", "호텔·숙박", "프런트는 3교대, 시설은 격일 당직이 많아요", listOf("호텔")),
        Group("weather", "기상", "예보·관측은 4조2교대가 많아요", listOf("기상")),
    )

    /** The group of a row's 업종 (or of what the user typed). */
    fun of(industry: String): Group {
        val s = industry.replace(" ", "")
        // 의료용품 is a maker, not a hospital.
        if ("의료용품" in s) return ALL.first { it.key == "maker" }
        return ALL.firstOrNull { g -> g.words.any { it in s } } ?: OTHER
    }

    fun byKey(key: String?): Group? = if (key == OTHER.key) OTHER else ALL.firstOrNull { it.key == key }

    /** The groups [rows] fall into, most rows first ([OTHER] last), with how many rows each has. */
    fun counts(rows: List<PublicCycle>): List<Pair<Group, Int>> =
        rows.groupingBy { it.group }.eachCount().entries
            .sortedWith(compareBy<Map.Entry<Group, Int>> { it.key == OTHER }.thenByDescending { it.value }.thenBy { ALL.indexOf(it.key) })
            .map { it.key to it.value }

    /** The groups that have days to show, for 설정's picker. */
    val WITH_DAYS: List<Group> get() = ALL.filter { it.days.isNotEmpty() }

    /** [CYC-04] The day of group [key] on [date], if any. */
    fun dayOn(key: String?, date: LocalDate): OrgDay? =
        byKey(key)?.days?.firstOrNull { it.month == date.monthValue && it.day == date.dayOfMonth }
}
