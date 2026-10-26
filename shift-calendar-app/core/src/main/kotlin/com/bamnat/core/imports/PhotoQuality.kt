package com.bamnat.core.imports

/**
 * [READ-14] 1.27 순번 446: whether a photo is good enough to read well, said before (or with) what was read, so the user
 * hears "다시 찍어 주세요" with the reason instead of a half-read table. From the photo's look ([PhotoVariants.Stats]) and the
 * letters found (their height, their count, the tilt the reader took out).
 */
object PhotoQuality {
    enum class Issue(val text: String) {
        DARK("사진이 어두워요. 밝은 곳에서 그림자 없이 찍어 주세요."),
        FLAT("글자와 종이가 잘 구별되지 않아요. 빛이 비치지 않게 찍어 주세요."),
        BLURRY("사진이 흐려요. 휴대폰을 멈추고 초점이 맞은 뒤 찍어 주세요."),
        SMALL("글자가 작아요. 표가 화면을 가득 채우게 가까이 찍어 주세요."),
        TILTED("많이 기울었어요. 표와 휴대폰을 나란히 두고 찍어 주세요."),
        FEW("읽은 글자가 거의 없어요. 표 전체가 나오게 다시 찍어 주세요."),
    }

    /** A score 0~100 and what lowers it, worst first. */
    data class Report(val score: Int, val issues: List<Issue>) {
        /** Worth taking again: what was read is likely to have many yellow cells. */
        val retake: Boolean get() = score < 60
        val line: String? get() = issues.firstOrNull()?.text
    }

    /**
     * [stats] of the photo, the median [letterHeight] of the words read (pixels of the photo), how many [words], and the
     * [tilt] taken out (degrees).
     */
    fun of(stats: PhotoVariants.Stats, letterHeight: Int?, words: Int, tilt: Double = 0.0): Report {
        val issues = mutableListOf<Pair<Issue, Int>>()
        if (stats.mean < 70) issues += Issue.DARK to 35 else if (stats.mean < 100) issues += Issue.DARK to 15
        if (stats.spread < 60) issues += Issue.FLAT to 30 else if (stats.spread < 100) issues += Issue.FLAT to 10
        if (stats.sharpness < 6) issues += Issue.BLURRY to 35 else if (stats.sharpness < 10) issues += Issue.BLURRY to 15
        if (letterHeight != null && letterHeight < 10) issues += Issue.SMALL to 35 else if (letterHeight != null && letterHeight < 16) issues += Issue.SMALL to 10
        if (kotlin.math.abs(tilt) > 8) issues += Issue.TILTED to 25 else if (kotlin.math.abs(tilt) > 5) issues += Issue.TILTED to 10
        if (words < 15) issues += Issue.FEW to 40
        val score = (100 - issues.sumOf { it.second }).coerceIn(0, 100)
        return Report(score, issues.sortedByDescending { it.second }.map { it.first })
    }
}
