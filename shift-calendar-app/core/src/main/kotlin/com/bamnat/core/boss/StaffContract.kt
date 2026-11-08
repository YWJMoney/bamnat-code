package com.bamnat.core.boss

import com.bamnat.core.report.ReportDoc
import com.bamnat.core.schedule.LegalBreak
import java.time.DayOfWeek
import java.time.LocalDate

/**
 * 1.38 [P3-X-06] 근로계약서 (사용자 2026-11-06 혁신안 X6): the items 근로기준법 제17조 and 시행령 제8조 ask a written contract to carry
 * (임금의 구성항목·계산방법·지급방법, 소정근로시간, 휴일, 연차 유급휴가, 근로일·근로일별 근로시간(단시간 근로자, 시행령 제9조 별표 2)),
 * filled from the staff member's own row, laid out as 고용노동부 표준근로계약서 orders them. What is not in the row is listed so the
 * boss fills it before sending; the words are the app's own (고용노동부 양식의 문구를 옮겨 오지 않아요).
 */
object StaffContract {
    data class Item(val title: String, val body: String)
    data class Contract(val items: List<Item>, val missing: List<String>, val shortTime: Boolean)

    /**
     * The contract as it went to the staff member (kept as sent, so the signed PDF says what they read even after the row changes),
     * the sign link while it waits, and the signature. [paper]: the boss marked a paper contract as signed instead.
     */
    data class Sent(
        val title: String,
        val items: List<Item>,
        val sentAt: Long,
        val linkId: String? = null,
        val token: String? = null,
        val url: String? = null,
        val expiresAt: Long? = null,
        val seenAt: Long? = null,
        val signedName: String? = null,
        val signedAt: Long? = null,
        val paper: Boolean = false,
        /** Signed contracts this one replaced (시급·시간이 바뀌어 새로 쓴 것), oldest first; kept so each PDF can be made again. */
        val past: List<Sent> = emptyList(),
    ) {
        val signed: Boolean get() = signedAt != null

        /** [next] in place of this one: a signed one is kept in [past] (the last ten), a waiting or unsigned one is dropped. */
        fun replacedBy(next: Sent): Sent = next.copy(past = if (signed) (past + copy(past = emptyList(), linkId = null, token = null, url = null)).takeLast(10) else past)
        /** Waiting on the link: sent, not signed, not past its day ([now] millis). */
        fun waiting(now: Long): Boolean = !signed && linkId != null && (expiresAt == null || now < expiresAt)
    }

    /** 계약서 없음 on the ledger: a staff member still working with no signed contract. */
    fun lacking(s: StaffMember, on: LocalDate): Boolean = s.contract?.signed != true && s.employedOn(on)

    private val days = listOf("월", "화", "수", "목", "금", "토", "일")
    private fun day(d: DayOfWeek) = days[d.value - 1]
    private fun date(d: LocalDate) = "${d.year}년 ${d.monthValue}월 ${d.dayOfMonth}일"
    private fun won(v: Long) = "%,d원".format(v)

    fun of(place: Workplace, s: StaffMember, start: LocalDate? = s.hiredOn): Contract {
        val missing = ArrayList<String>()
        if (s.name.isBlank()) missing += "이름"
        if (start == null) missing += "입사일(근로 시작일)"
        if (s.job.isBlank()) missing += "하는 일(업무)"
        if (s.weekly.isEmpty() && s.form != WageForm.MONTH) missing += "평소 근무 요일·시간"
        if (place.payday == null) missing += "급여 지급일(사업장 설정)"
        val week = DayOfWeek.entries.filter { it in s.weekly }
        val weekMinutes = week.sumOf { s.weekly.getValue(it).paidMinutes(place.legalBreaks) }
        val shortTime = s.weekly.isNotEmpty() && weekMinutes < 40 * 60
        val items = ArrayList<Item>()
        items += Item("근로 시작일", (start?.let(::date) ?: "____년 __월 __일") + "부터" + (s.leftOn?.let { " ${date(it)}까지" } ?: " (기간을 정하지 않음)"))
        items += Item("근무 장소", place.name)
        items += Item("하는 일", s.job.ifBlank { "________" })
        items += Item("근무 요일과 시간", if (week.isEmpty()) "________" else week.joinToString("\n") { d ->
            val sh = s.weekly.getValue(d)
            val brk = if (place.legalBreaks) LegalBreak.minutesFor(sh.spanMinutes) else 0
            "${day(d)}요일 ${sh.start}~${sh.end}" + (if (brk > 0) " (휴게 ${brk}분, 근로시간 중에)" else "")
        } + "\n한 주 소정근로시간 ${weekMinutes / 60}시간" + (if (weekMinutes % 60 != 0) " ${weekMinutes % 60}분" else ""))
        // 주휴일: a day not in the week, 일요일 first.
        val rest = listOf(DayOfWeek.SUNDAY, DayOfWeek.SATURDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)
            .firstOrNull { it !in s.weekly }
        items += Item("휴일", if (weekMinutes >= 15 * 60 && s.weeklyHolidayPay) "매주 ${rest?.let(::day) ?: "_"}요일 (유급 주휴일)" else "매주 ${rest?.let(::day) ?: "_"}요일" +
            if (weekMinutes in 1 until 15 * 60) " (한 주 15시간 미만이라 주휴일은 유급이 아니에요, 제18조 제3항)" else "")
        val pay = buildString {
            append(when (s.form) {
                WageForm.HOUR -> "시급 ${won(s.base)}"
                WageForm.DAY -> "일급 ${won(s.base)}"
                WageForm.MONTH -> "월급 ${won(s.base)}"
            })
            if (s.dayRates.isNotEmpty()) append("\n요일별 시급: " + DayOfWeek.entries.filter { it in s.dayRates }.joinToString(", ") { "${day(it)} ${won(s.dayRates.getValue(it))}" })
            if (s.allowances.isNotEmpty()) append("\n수당: " + s.allowances.joinToString(", ") { "${it.name} ${won(it.amount)}" + if (it.perDay) "(일한 날마다)" else "(매월)" })
            if (s.form == WageForm.HOUR && s.weeklyHolidayPay && weekMinutes >= 15 * 60) append("\n주휴수당: 한 주 소정근로일을 다 나오면 유급 주휴일 1일분")
            if (place.fivePlus != false) append("\n연장·야간·휴일 근로: 통상임금의 50%를 더해 줘요(근로기준법 제56조)")
            append("\n지급일: " + (place.payday?.let { "매월 ${it}일 (그달 것을 다음 달에)" } ?: "매월 __일"))
            append("\n지급 방법: 근로자 명의 예금 통장에 입금")
        }
        items += Item("임금", pay)
        items += Item("연차 유급휴가", if (place.fivePlus == false) "5명 미만 사업장이라 근로기준법 제60조가 적용되지 않아요. 따로 주기로 하면 여기에 적어요."
            else "근로기준법 제60조에 따라 줘요(1년 미만은 한 달 개근마다 1일, 1년 80% 이상 출근하면 15일)")
        items += Item("사회보험", "고용보험 " + (if (s.insurance.employment) "가입" else "가입 안 함") + " · 산재보험 가입 · 국민연금 " + (if (s.insurance.pension) "가입" else "가입 안 함") +
            " · 건강보험 " + (if (s.insurance.health) "가입" else "가입 안 함"))
        items += Item("계약서 주기", "사업주는 이 계약서를 만들면 근로자에게 한 부를 줘요(근로기준법 제17조). 근로자가 원하지 않아도 줘야 해요.")
        items += Item("그 밖의 것", "이 계약에 적지 않은 것은 근로기준법과 그 시행령을 따라요." + if (shortTime) " 단시간 근로자라 근로일과 근로일별 근로시간을 위에 적었어요(시행령 제9조)." else "")
        return Contract(items, missing, shortTime)
    }

    fun title(c: Contract) = if (c.shortTime) "단시간 근로자 근로계약서" else "근로계약서"

    /** The contract to read before it is signed: the boss's PDF to print, or the preview. */
    fun doc(place: Workplace, s: StaffMember, c: Contract): ReportDoc = doc(place, s, title(c), c.items, "근로자 ________ (서명)")

    /** The contract as it went out ([sent]) and how it was signed; [zone] for the time written on it. */
    fun doc(place: Workplace, s: StaffMember, sent: Sent, zone: java.time.ZoneId = java.time.ZoneId.of("Asia/Seoul")): ReportDoc {
        val at = sent.signedAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone) }
        val line = when {
            at == null -> "근로자 ________ (서명)"
            sent.paper -> "근로자 ${s.name} — 종이 계약서에 서명함 (${date(at.toLocalDate())} 받음)"
            else -> "근로자 ${sent.signedName ?: s.name} — 링크에서 읽고 이름을 써서 동의함 (${date(at.toLocalDate())} %02d:%02d)".format(at.hour, at.minute)
        }
        return doc(place, s, sent.title, sent.items, line)
    }

    private fun doc(place: Workplace, s: StaffMember, title: String, items: List<Item>, signLine: String): ReportDoc = ReportDoc(
        title = title,
        blocks = buildList {
            add(ReportDoc.Facts(listOfNotNull("사업주" to place.name, "근로자" to s.name.ifBlank { "________" },
                s.birth?.let { "생년월일" to "${it.year}.${it.monthValue}.${it.dayOfMonth}" })))
            items.forEachIndexed { i, it -> add(ReportDoc.Heading("${i + 1}. ${it.title}")); add(ReportDoc.Paragraph(it.body)) }
            add(ReportDoc.Heading("서명"))
            add(ReportDoc.Paragraph(signLine))
            add(ReportDoc.Paragraph("사업주 ${place.name} ________ (서명)"))
            add(ReportDoc.Paragraph("밤낮 앱이 직원 정보로 채운 계약서예요. 보내기 전에 사장님이 내용을 확인해요.", small = true))
        },
    )
}
