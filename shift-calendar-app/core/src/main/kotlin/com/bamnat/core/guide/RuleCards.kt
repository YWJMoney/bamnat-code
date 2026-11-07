package com.bamnat.core.guide

import com.bamnat.core.formula.EasyWhen
import com.bamnat.core.formula.Logic
import com.bamnat.core.formula.ifWord
import com.bamnat.core.memo.Memo
import com.bamnat.core.memo.MemoRepeat
import com.bamnat.core.model.ShiftType
import com.bamnat.core.model.WorkPattern
import com.bamnat.core.pay.CustomRule
import com.bamnat.core.schedule.PatternTimeline
import java.time.LocalDate

/**
 * [EASE-27] 1.38 "규칙을 문장형 카드로" (오늘근무 분석 P9, 사용자 관찰): every rule that changes what a day is, when an alarm rings
 * or what a day pays, read as one card "만약 ○○이면 → △△", in one place (설정 › 내 근무 › 내 규칙 한눈에). The rules are kept
 * where they always were (근무 주기, 근무 종류, 급여의 직접 만든 항목, 스튜디오의 규칙); a card says where to change it ([Where]).
 * Nothing here decides anything: the same words a rule already has, put as a condition and what follows.
 */
object RuleCards {
    enum class Group(val label: String) {
        SHIFT("근무"), ALARM("알람"), MEMO("반복 메모"), PAY("수당"), LOGIC("이럴 때 이렇게"),
    }

    /** Where the rule is changed: 근무 주기, 근무 종류, 급여 › 직접 만든 항목, 스튜디오 › 규칙, 메모 탭. */
    enum class Where { PATTERN, SHIFT_TYPES, PAY, STUDIO, MEMOS }

    data class Card(val group: Group, val cond: String, val result: String, val where: Where, val on: Boolean = true) {
        /** "만약 공휴일이면 → 쉬는 날이 돼요", for TalkBack and tests. */
        val sentence: String get() = "만약 $cond → $result"
    }

    /**
     * The cards of a calendar: its order going on [today] ([patterns]), its kinds of day ([types], for alarms and names), the
     * repeating memos ([memos], "만약 야간 날이면 → 메모 '도시락'이 보여요"), the pay rules the user made ([custom]) and the
     * 이럴 때 이렇게 rules ([logic]), in that order. With every alarm turned off in settings ([alarmsOn] false), an alarm card
     * says so and never "울려요" (1.38 PC 확인, 9b028f4c).
     */
    fun of(
        patterns: List<WorkPattern>,
        types: List<ShiftType>,
        today: LocalDate,
        custom: List<CustomRule> = emptyList(),
        logic: Logic = Logic(),
        alarmsOn: Boolean = true,
        memos: List<Memo> = emptyList(),
        /** 1.38 PC 11차 식당 둘러보기 ('야간 날이면'): kinds put away ('안 쓰는 종류'), which get no alarm card. */
        hidden: Set<Long> = emptySet(),
        /** 1.38 PC 11차 식당 둘러보기: what a weekly working day is called ("영업하는 날이에요" for a shop). */
        workDayLine: String = "근무하는 날이에요",
        /**
         * 1.38 PC 12.1 (식당 '만약 영업 날이면 → 알람이 울리지 않아요 · 꺼짐'): work without 교대 lists only the alarms that ring; a
         * shift worker still sees the kinds without one, since a 야간 with no alarm is worth a look.
         */
        ringingOnly: Boolean = false,
    ): List<Card> = buildList {
        val names = types.associate { it.id to it.name }
        when (val p = PatternTimeline.current(patterns, today)) {
            is WorkPattern.Rotation -> {
                val order = p.steps.joinToString("") { id -> id?.let { types.firstOrNull { t -> t.id == it } }?.takeIf { !it.isOff }?.name?.take(1) ?: "휴" }
                add(Card(Group.SHIFT, "날마다", "$order 순서로 ${p.steps.size}일마다 돌아요", Where.PATTERN))
            }
            is WorkPattern.Weekly -> {
                val days = p.steps.mapIndexedNotNull { i, id -> DAYS[i].takeIf { id != null && types.firstOrNull { t -> t.id == id }?.isOff != true } }
                if (days.isNotEmpty()) add(Card(Group.SHIFT, "${days.joinToString("·")}요일이면", workDayLine, Where.PATTERN))
                add(Card(Group.SHIFT, "공휴일·대체공휴일이면",
                    if (p.holidaysOff) "쉬는 날이 되고 알람도 울리지 않아요" else "요일대로 일해요 (쉬게 하려면 고치기)", Where.PATTERN, on = p.holidaysOff))
                if (p.altSaturday != null) add(Card(Group.SHIFT, "토요일이면", "격주로만 일해요", Where.PATTERN))
            }
            null -> Unit
        }
        for (t in types.sortedBy { it.sortOrder }) {
            if (!t.hasTimes || t.id in hidden) continue
            val alarms = t.activeAlarms
            if (ringingOnly && (!alarmsOn || alarms.isEmpty())) continue
            val cond = "${t.name} 날이면"
            if (!alarmsOn) add(Card(Group.ALARM, cond, "알람이 울리지 않아요 (설정에서 알람을 모두 껐어요)", Where.SHIFT_TYPES, on = false))
            else if (alarms.isEmpty()) add(Card(Group.ALARM, cond, "알람이 울리지 않아요", Where.SHIFT_TYPES, on = false))
            else add(Card(Group.ALARM, cond, "시작 " + alarms.joinToString("·") { span(it.offsetMinutes) } + " 전에 울려요", Where.SHIFT_TYPES))
        }
        for (m in memos.sortedWith(compareBy({ MemoRepeat.kindOf(it.repeat) != MemoRepeat.Kind.SHIFT }, { MemoRepeat.kindOf(it.repeat).ordinal }, { it.repeat }))) {
            val kind = MemoRepeat.kindOf(m.repeat)
            if (kind == MemoRepeat.Kind.NONE || m.date == null || m.deletedAtMillis != null) continue
            val cond = if (kind == MemoRepeat.Kind.SHIFT) {
                val name = MemoRepeat.shiftIdOf(m.repeat)?.let { names[it] } ?: continue // its kind of day was deleted
                "$name 날이면"
            } else ifWord(MemoRepeat.label(m.repeat))
            val title = m.title.ifBlank { m.body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty() }.trim().let {
                if (it.length > MEMO_TITLE) it.take(MEMO_TITLE) + "…" else it
            }
            add(Card(Group.MEMO, cond, if (title.isEmpty()) "메모가 보여요" else "메모 ‘$title’${iga(title)} 보여요", Where.MEMOS))
        }
        for (r in custom) {
            val w = r.whenever.describe(names)
            val cond = if (r.pay.monthly) "매달" else if (w.isEmpty()) "모든 근무 날이면" else ifWord(w)
            val limits = r.limits().let { if (it.isEmpty()) "" else " ($it)" }
            add(Card(Group.PAY, cond, "${r.name}: ${r.pay.describe()} ${if (r.deduct) "빼요" else "더 받아요"}$limits", Where.PAY, on = r.on))
        }
        for (r in logic.rules) {
            val c = if (r.cond.isEmpty()) "" else (EasyWhen.read(r.cond)?.say() ?: "조건이 맞으면").let { ", " + ifWord(it) }
            add(Card(Group.LOGIC, r.sayWhen() + c, r.act.say(), Where.STUDIO))
        }
    }

    private const val MEMO_TITLE = 14

    /** 이/가 after [w]: 가 after a Hangul syllable with no final consonant, 이 otherwise (a number, a letter, "…"). */
    private fun iga(w: String): String = w.lastOrNull()?.let { c -> if (c in '가'..'힣' && (c - '가') % 28 == 0) "가" else "이" } ?: "이"

    private val DAYS = listOf("월", "화", "수", "목", "금", "토", "일")

    private fun span(m: Int): String = when {
        m % 60 == 0 -> "${m / 60}시간"
        m > 60 -> "${m / 60}시간 ${m % 60}분"
        else -> "${m}분"
    }
}
