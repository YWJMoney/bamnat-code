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
    /**
     * [warn]: 2.0.0 [RVJ-17] 검토 BOSS-54 what the contract's own hours break for an 18세 미만 (제69·70조), said before it is sent like
     * [missing], but not blocking (the boss may have the 인가).
     */
    data class Contract(val items: List<Item>, val missing: List<String>, val shortTime: Boolean, val warn: List<String> = emptyList())

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
        /** 2.0.0 [RVJ-17] 검토 BOSS-55: when the boss marked the signed copy as given to the staff member (제17조 교부); null = not yet. */
        val givenAt: Long? = null,
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

    /** [RVJ-17] 검토 BOSS-54: under 18 on [on] (근로기준법 제66~70조). */
    fun young(s: StaffMember, on: LocalDate?): Boolean = s.birth != null && on != null && on.isBefore(s.birth.plusYears(18))

    /** [RVJ-17] The line an 18세 미만's contract carries (제66조 서류, 제67조 대리 금지, 제69·70조 시간). */
    const val YOUNG = "친권자(후견인) 동의서와 가족관계증명서를 사업장에 둬요(근로기준법 제66조). 계약은 본인과 맺고 친권자가 대신 맺을 수 없어요(제67조). " +
        "하루 7시간·한 주 35시간까지예요(합의하면 하루 1시간·한 주 5시간 더, 제69조). 밤 10시~아침 6시와 휴일 근무는 본인 동의와 고용노동부 인가가 있어야 해요(제70조)."

    fun of(place: Workplace, s: StaffMember, start: LocalDate? = s.hiredOn): Contract {
        val missing = ArrayList<String>()
        val warn = ArrayList<String>()
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
        // 2.0.0 [RVJ-17] 검토 BOSS-52: a seven-day week leaves no 주휴일 to write (제55조 제1항: 1주에 평균 1회 이상); it is asked first.
        if (rest == null && s.weekly.isNotEmpty()) missing += "주휴일(한 주에 쉬는 날, 평소 근무에서 하루를 빼 주세요)"
        // [RVJ-17] 제17조 제1항 제3호 '제55조에 따른 휴일': 5인 이상은 관공서 공휴일·대체공휴일도 유급휴일(제55조 제2항), 노동절(5월 1일)은
        // 모든 사업장의 유급휴일(근로자의 날 제정에 관한 법률, 2026년부터 '노동절').
        val paidDays = if (place.fivePlus != false) "\n관공서 공휴일·대체공휴일과 노동절(5월 1일)은 유급휴일이에요(근로기준법 제55조 제2항)."
            else "\n노동절(5월 1일)은 유급휴일이에요. 5명 미만 사업장이라 다른 공휴일은 쉬기로 한 날만 쉬어요."
        items += Item("휴일", (if (weekMinutes >= 15 * 60 && s.weeklyHolidayPay) "매주 ${rest?.let(::day) ?: "_"}요일 (유급 주휴일)" else "매주 ${rest?.let(::day) ?: "_"}요일" +
            if (weekMinutes in 1 until 15 * 60) " (한 주 15시간 미만이라 주휴일은 유급이 아니에요, 제18조 제3항)" else "") + paidDays)
        val pay = buildString {
            append(when (s.form) {
                WageForm.HOUR -> "시급 ${won(s.base)}"
                WageForm.DAY -> "일급 ${won(s.base)}"
                WageForm.MONTH -> "월급 ${won(s.base)}"
            })
            if (s.dayRates.isNotEmpty()) append("\n요일별 시급: " + DayOfWeek.entries.filter { it in s.dayRates }.joinToString(", ") { "${day(it)} ${won(s.dayRates.getValue(it))}" })
            if (s.allowances.isNotEmpty()) append("\n수당: " + s.allowances.joinToString(", ") {
                "${it.name} ${won(it.amount)}" + (if (it.perDay) "(일한 날마다" else "(매월") + (if (it.taxFree) ", 비과세)" else ")")
            })
            if (s.form == WageForm.HOUR && s.weeklyHolidayPay && weekMinutes >= 15 * 60) append("\n주휴수당: 한 주 소정근로일을 다 나오면 유급 주휴일 1일분")
            if (place.fivePlus != false) append("\n연장·야간·휴일 근로: 통상임금의 50%를 더해 줘요(근로기준법 제56조)")
            // 2.0.0 [RVJ-09] 검토 BOSS-56: 그달에 주는 가게는 '그달 것을 그달에'.
            append("\n지급일: " + (place.payday?.let { "매월 ${it}일 (" + (if (place.payNextMonth) "그달 것을 다음 달에" else "그달 것을 그달에") + ", 쉬는 날이면 그 전날)" } ?: "매월 __일"))
            append("\n지급 방법: 근로자 명의 예금 통장에 입금")
        }
        items += Item("임금", pay)
        // 2.0.0 [RVG-03] 검토 BOSS-51: 초단시간(4주 평균 한 주 15시간 미만)은 제60조도 적용되지 않아요(근로기준법 제18조 제3항), the 휴일 line above says
        // the same for 주휴일. The 연차 line now says it too instead of promising 15일 the law does not ask for.
        items += Item("연차 유급휴가", when {
            weekMinutes in 1 until 15 * 60 -> "한 주 15시간 미만이라 연차 유급휴가(근로기준법 제60조)가 적용되지 않아요(제18조 제3항). 따로 주기로 하면 여기에 적어요."
            place.fivePlus == false -> "5명 미만 사업장이라 근로기준법 제60조가 적용되지 않아요. 따로 주기로 하면 여기에 적어요."
            else -> "근로기준법 제60조에 따라 줘요(1년 미만은 한 달 개근마다 1일, 1년 80% 이상 출근하면 15일)"
        })
        // 2.0.0 [RVJ-16] 검토 BOSS-74: what the law puts them in (the same [InsuranceRules] the ledger uses), not only the boxes ticked.
        val ins = InsuranceRules.of(place, s, java.time.YearMonth.from(start ?: com.bamnat.core.time.Today.date())).effective
        items += Item("사회보험", if (s.insurance.tax == StaffTax.BIZ) "사업소득(3.3%)으로 받는 계약이라 4대보험이 없어요" else
            "고용보험 " + (if (ins.employment) "가입" else "가입 안 함") + " · 산재보험 가입 · 국민연금 " + (if (ins.pension) "가입" else "가입 안 함") +
                " · 건강보험 " + (if (ins.health) "가입" else "가입 안 함"))
        // [RVJ-17] 검토 BOSS-54: 18세 미만.
        if (young(s, start ?: com.bamnat.core.time.Today.date())) {
            items += Item("18세 미만 근로자", YOUNG)
            val longDay = s.weekly.values.any { it.paidMinutes(place.legalBreaks) > 7 * 60 }
            if (longDay || weekMinutes > 35 * 60) warn += "18세 미만은 하루 7시간·한 주 35시간까지예요. 평소 근무가 넘어요(합의하면 하루 1시간·한 주 5시간 더, 제69조)"
            val late = java.time.LocalTime.of(22, 0)
            val early = java.time.LocalTime.of(6, 0)
            if (s.weekly.values.any { it.end.isAfter(late) || it.start.isBefore(early) || !it.end.isAfter(it.start) })
                warn += "18세 미만의 밤 10시~아침 6시 근무는 본인 동의와 고용노동부 인가가 있어야 해요(제70조)"
        }
        items += Item("계약서 주기", "사업주는 이 계약서를 만들면 근로자에게 한 부를 줘요(근로기준법 제17조). 근로자가 원하지 않아도 줘야 해요.")
        items += Item("그 밖의 것", "이 계약에 적지 않은 것은 근로기준법과 그 시행령을 따라요." + if (shortTime) " 단시간 근로자라 근로일과 근로일별 근로시간을 위에 적었어요(시행령 제9조)." else "")
        return Contract(items, missing, shortTime, warn)
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
        // [RVJ-17] 검토 BOSS-55: a contract the boss sent from the app carries the 사업주 side too (작성해 보낸 날).
        val sentOn = java.time.Instant.ofEpochMilli(sent.sentAt).atZone(zone).toLocalDate()
        val boss = if (sent.paper) null else "사업주 ${place.name}" + place.owner.takeIf { it.isNotBlank() }?.let { " (대표 $it)" }.orEmpty() +
            " — 앱에서 작성해 보냄 (${date(sentOn)})"
        val given = sent.givenAt?.let { java.time.Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }?.let { "\n근로자에게 한 부 줌 (${date(it)})" }.orEmpty()
        return doc(place, s, sent.title, sent.items, line + given, boss)
    }

    private fun doc(place: Workplace, s: StaffMember, title: String, items: List<Item>, signLine: String, bossLine: String? = null): ReportDoc = ReportDoc(
        title = title,
        blocks = buildList {
            // 2.0.0 [RVJ-17] 검토 BOSS-55: 대표자·주소 when the place has them, so who signed with whom is clear.
            add(ReportDoc.Facts(listOfNotNull("사업주" to (place.name + place.owner.takeIf { it.isNotBlank() }?.let { " (대표 $it)" }.orEmpty()),
                place.address.takeIf { it.isNotBlank() }?.let { "사업장 주소" to it },
                "근로자" to s.name.ifBlank { "________" },
                s.birth?.let { "생년월일" to "${it.year}.${it.monthValue}.${it.dayOfMonth}" })))
            items.forEachIndexed { i, it -> add(ReportDoc.Heading("${i + 1}. ${it.title}")); add(ReportDoc.Paragraph(it.body)) }
            add(ReportDoc.Heading("서명"))
            add(ReportDoc.Paragraph(signLine))
            add(ReportDoc.Paragraph(bossLine ?: "사업주 ${place.name} ________ (서명)"))
            add(ReportDoc.Paragraph("밤낮 앱이 직원 정보로 채운 계약서예요. 보내기 전에 사장님이 내용을 확인해요.", small = true))
        },
    )
}
