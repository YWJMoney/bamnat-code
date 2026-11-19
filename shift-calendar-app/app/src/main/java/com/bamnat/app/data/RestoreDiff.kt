package com.bamnat.app.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 2.0.0 [RVL-50] (검토 U12-04) What a restore changes, said before the list of what the file holds: how old the file is, then what
 * gets fewer ("메모 8개·돈 기록 31줄이 줄어요", the loss nobody sees coming), then what there is more of. Counts, not the records: two
 * files with the same counts may still differ, so nothing here says "the same".
 */
data class RestoreDiff(
    /** Days from the day the file was saved to today; null for a file that does not say. */
    val ageDays: Long?,
    val savedOn: LocalDate?,
    val fewer: List<String>,
    val more: List<String>,
    /** The phone has a 급여 설정 the file has not. */
    val payGone: Boolean = false,
) {
    /** "이 파일은 12일 전 것이에요", or for today's and yesterday's file, said so. */
    val ageLine: String?
        get() = when (ageDays) {
            null -> null
            0L -> "오늘 저장한 파일이에요"
            1L -> "어제 저장한 파일이에요"
            else -> "이 파일은 ${ageDays}일 전 것이에요"
        }

    /** "메모 8개·돈 기록 31줄이 줄어요", and "급여 설정이 없어져요" when the file has none. */
    val fewerLine: String?
        get() {
            val counts = fewer.takeIf { it.isNotEmpty() }?.let { it.joinToString("·") + subject(it.last()) }
            return when {
                counts != null && payGone -> "$counts 줄고, 급여 설정이 없어져요"
                counts != null -> "$counts 줄어요"
                payGone -> "급여 설정이 없어져요"
                else -> null
            }
        }

    /** "바꾼 날짜 3일이 늘어요". */
    val moreLine: String? get() = more.takeIf { it.isNotEmpty() }?.let { it.joinToString("·") + subject(it.last()) + " 늘어요" }

    companion object {
        /** [file] put in place of [now] on [today]. [savedOn] is the day the file says it was saved. */
        fun of(file: BackupSummary, now: BackupSummary, savedOn: LocalDate?, today: LocalDate): RestoreDiff {
            val fewer = mutableListOf<String>()
            val more = mutableListOf<String>()
            fun count(name: String, unit: String, inFile: Int, onPhone: Int) {
                when {
                    inFile < onPhone -> fewer += "$name ${onPhone - inFile}$unit"
                    inFile > onPhone -> more += "$name ${inFile - onPhone}$unit"
                }
            }
            // The order a user would miss them in: what they wrote, then money, then the month, then the rest.
            count("메모", "개", file.memos, now.memos)
            count("돈 기록", "줄", file.moneyLines, now.moneyLines)
            count("바꾼 날짜", "일", file.days, now.days)
            count("수면 기록", "개", file.sleeps, now.sleeps)
            count("사진", "장", file.photos, now.photos)
            count("추가한 달력", "개", file.calendars, now.calendars)
            count("팀 근무표", "개", file.teams, now.teams)
            count("사업장", "곳", file.places, now.places)
            count("직원", "명", file.staff, now.staff)
            count("근무 종류", "개", file.shiftTypes, now.shiftTypes)
            val age = savedOn?.let { ChronoUnit.DAYS.between(it, today).coerceAtLeast(0) }
            return RestoreDiff(age, savedOn, fewer, more, payGone = now.hasPay && !file.hasPay)
        }

        /** 이 after a closed syllable (31줄이), 가 after an open one (8개가). */
        internal fun subject(word: String): String {
            val last = word.lastOrNull() ?: return "이"
            if (last !in '가'..'힣') return "이"
            return if ((last - '가') % 28 == 0) "가" else "이"
        }
    }
}
