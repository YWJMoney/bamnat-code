package com.bamnat.core.plaza

/**
 * 1.39 [PLZC-40] 제작자 표식 (사용자 2026-11-07 "유명한 제작자나 이달의 제작자 같은 사람들은 특별 태그나 이름 색상 … 인플루언서 같은
 * 느낌"): what the server says of a maker (`mark`), the same on 광장 posts and 장터 cards. The server sends one only once plaza.people
 * is on (처리방침 3.12 ⑤), and none while 마을 돌봄 rests the maker (경고·쉬기·쓰기 제한).
 */
enum class MakerMark(val key: String, val badge: String, val words: String, val tone: Tone) {
    MONTH("month", "🏅", "이달의 제작자", Tone.GOLD),
    POPULAR("popular", "⭐", "인기 제작자", Tone.STAR),
    ;

    /** The name's color family; the app maps it to its theme (light and dark). */
    enum class Tone { GOLD, STAR }

    companion object {
        fun of(key: String?): MakerMark? = entries.firstOrNull { it.key == key }
    }
}
