package com.bamnat.app.share

import android.content.Context
import com.bamnat.app.BuildConfig
import com.bamnat.app.data.ScheduleSnapshot
import com.bamnat.app.ui.calendar.ExtraText
import com.bamnat.core.memo.Memo
import com.bamnat.core.model.Leave
import com.bamnat.core.model.LeaveKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeParseException
import com.bamnat.core.time.Today

/**
 * Read-only web links to the schedule [MKT-09]. The server (server/share-worker) keeps only what
 * the page shows: dates, shift names, colours and times. Memos, pay, extra work and the kind of
 * leave other than 연차 leave the phone only when the sharer ticks them [SHR-01].
 */
data class ShareLinkState(
    val id: String,
    val url: String,
    val deleteToken: String,
    val expiresAt: Instant,
    val title: String,
    /** [SHR-12] The period the sharer picked, offered again for 연장; 30 for a link made before 1.14. */
    val ttlDays: Int = 30,
    /** [SHR-12] The first month the link shows; null for a link made before 1.14, which then starts this month. */
    val from: YearMonth? = null,
)

/**
 * 1.38 [SLEEP-11] 지금 자는 중 as a link carries it: when the sleep began and the next alarm, in epoch millis. The receiver shows it only
 * while it is fresh ([shownAt]): not once the wake is 2 hours past, nor 16 hours after the start.
 */
data class SleepNow(val since: Long, val wakeAt: Long? = null) {
    fun shownAt(now: Long): Boolean =
        now - since in 0..FRESH_MILLIS && (wakeAt == null || now <= wakeAt + PAST_WAKE_MILLIS)

    companion object {
        const val FRESH_MILLIS = 16 * 3_600_000L
        const val PAST_WAKE_MILLIS = 2 * 3_600_000L

        fun of(o: JSONObject?): SleepNow? {
            if (o == null) return null
            val since = o.optLong("since", 0L).takeIf { it > 0 } ?: return null
            return SleepNow(since, o.optLong("wakeAt", 0L).takeIf { it > since })
        }
    }
}

/**
 * 1.39 G5-N4 계획한 잠 시간 as a link carries it: one planned sleep's start and end (epoch millis). Only the plan the app made from
 * the work, never what was slept; sent only with [ShareOptions.sleepPlan].
 */
data class SleepPlanWindow(val from: Long, val to: Long) {
    companion object {
        /** The link's list, skipping anything that is not a from before a to at most 16 hours on. */
        fun list(a: org.json.JSONArray?): List<SleepPlanWindow> =
            if (a == null) emptyList() else (0 until a.length()).mapNotNull { i ->
                val o = a.optJSONObject(i) ?: return@mapNotNull null
                val from = o.optLong("from", 0L); val to = o.optLong("to", 0L)
                SleepPlanWindow(from, to).takeIf { from > 0 && to > from && to - from <= com.bamnat.core.sleep.SleepPlanShare.MAX_HOURS * 3_600_000L }
            }.take(com.bamnat.core.sleep.SleepPlanShare.MAX)
    }
}

/** [SHR-01] What a link carries besides the shifts; all off until the sharer ticks them. */
data class ShareOptions(
    val leaveKinds: Boolean = false,
    val extras: Boolean = false,
    val memos: Boolean = false,
    /** [SHR-11] The month's money in the user's own kind: pay, job income or sales, see [com.bamnat.core.work.MoneyShare]. */
    val pay: Boolean = false,
    /** [SHR-11] Booked jobs by title and time, for field and freelance work; never the client, place or price. */
    val jobs: Boolean = false,
    /** [SHR-20] 1.33: who the link is for, as picked at the top of the sheet; null for a link made before 1.33. */
    val audience: ShareAudience? = null,
    /** [SHR-20] A coworker's link also carries the company setup (no amounts), from 처리방침 3.7's date. */
    val setup: Boolean = false,
    /** 1.37 [SHR-31] The sharer's 테마 (looks only, no photos), for the receiver to try on; from 처리방침 3.11 (`share.look`). */
    val look: Boolean = false,
    /** 1.37 [SHR-32] The sharer's 근무 설정 (근무 꾸미기·알람 시각 틀·근무 주기), never 급여 규칙 or an amount; from 3.11. */
    val kit: Boolean = false,
    /** 1.38 [SLEEP-11] 지금 자는 중: while a sleep is open the link says since when and the next alarm, nothing else (`sleep.share`). */
    val sleepNow: Boolean = false,
    /** 1.39 G5-N4 계획한 잠 시간: two weeks of the planned sleep's from~to, nothing else (`sleep.plan_share`, off by default). */
    val sleepPlan: Boolean = false,
) {
    /** The server's names for the ticked parts, in its order. */
    val shows: List<String>
        get() = buildList {
            if (leaveKinds) add("leaveKinds")
            if (extras) add("extras")
            if (memos) add("memos")
            if (pay) add("pay")
            if (jobs) add("jobs")
        }
    val any: Boolean get() = shows.isNotEmpty()

    /** [SHR-20] The company setup goes with this link: a coworker's, ticked. */
    val sendsSetup: Boolean get() = setup && audience == ShareAudience.COWORKER

    companion object {
        fun of(shows: Collection<String>, audience: ShareAudience? = null, setup: Boolean = false, look: Boolean = false, kit: Boolean = false,
               sleepNow: Boolean = false, sleepPlan: Boolean = false) =
            ShareOptions("leaveKinds" in shows, "extras" in shows, "memos" in shows, "pay" in shows, "jobs" in shows, audience, setup, look, kit, sleepNow, sleepPlan)

        /**
         * [SHR-20] 1.33 (계획 690~694): what each way sends at first. 동료: the 연장·휴무일 근무 and the company setup, never
         * memos or money; 가족·친구: the shifts and leave only (a freelancer's family also sees the 일정). Each can still be
         * ticked by hand; 기본급·입사일·연차·알람·메모·금액 go nowhere unless ticked.
         */
        fun forAudience(a: ShareAudience, employee: Boolean, field: Boolean): ShareOptions = when (a) {
            ShareAudience.COWORKER -> ShareOptions(extras = employee, jobs = field, audience = a, setup = employee)
            ShareAudience.FAMILY -> ShareOptions(jobs = field, audience = a)
            ShareAudience.FRIEND -> ShareOptions(audience = a)
        }
    }
}

/**
 * [SHR-20] 1.33 (계획 690 "달력 '공유' 맨 위에 세 갈래"): who a link is for. The words follow the kind of work: 교대·일근 have a
 * 회사, the rest 같이 일하는 사람 and 친구 ([J133-71]).
 */
enum class ShareAudience(val key: String) {
    COWORKER("coworker"), FAMILY("family"), FRIEND("friend");

    fun label(companyWork: Boolean): String = when (this) {
        COWORKER -> if (companyWork) "같은 회사 동료" else "같이 일하는 사람"
        FAMILY -> "가족·연인"
        FRIEND -> if (companyWork) "다른 회사 친구" else "친구"
    }

    /** [setupOpen]: the company setup can go in the link now (정책 3.7 시행 뒤); before, the coworker is told the 근무표 only. */
    fun hint(companyWork: Boolean, setupOpen: Boolean = true, style: com.bamnat.core.work.WorkStyle? = null): String {
        // [J133-79] A shop's or a field worker's link in its own words (빌드 5: 가게 공유 시트가 '근무표·근무와 휴가').
        val employee = style?.usesPayEngine != false
        val days = com.bamnat.core.work.daysWord(style)
        return when (this) {
            COWORKER -> if (companyWork && setupOpen) "근무표와 회사 설정까지, 받은 동료는 기본급만 넣으면 돼요"
                else if (companyWork) "근무표와 연장·휴무일 근무를 보여요. 회사 설정은 파일로 따로 보내요"
                else if (employee) "근무표와 연장·휴무일 근무를 함께 보여요" else com.bamnat.core.work.scheduleWord(style).let { "$it${com.bamnat.app.ui.Fmt.obj(it)} 함께 보며 맞춰요" }
            // [J133-87] 1.33 빌드 6 ('금액는', '달력를'): the particle follows the word.
            FAMILY -> (if (employee) "급여" else "금액").let { "${days}만 보여요. 메모·$it${com.bamnat.app.ui.Fmt.eun(it)} 안 가요" }
            FRIEND -> (if (employee) "근무" else "달력").let { "${days}만 보여요. 친구도 바로 자기 $it${com.bamnat.app.ui.Fmt.obj(it)} 넣을 수 있어요" }
        }
    }

    companion object {
        fun of(key: String?): ShareAudience? = entries.firstOrNull { it.key == key }
    }
}

/** [SHR-01] One month's expected pay as the sharer's app worked it out; [SHR-11] or their job income or sales. */
data class SharedPay(val month: YearMonth, val amount: Long, val label: String)

/** [SHR-11] A booked job as a link shows it: what and when, nothing about who or how much. */
data class SharedJob(val name: String, val time: String?)

object ShareLinkPayload {
    /**
     * 2.0.0 [RVG-27] 검토 SN-13: whether the server turned the link down for what it carries (a 400 with the part's name, e.g.
     * "type_icon"), not for being busy or away. Sending the same again would be turned down again, so it is not retried as if
     * offline (before, '보내지 못한 변경이 있어요. 인터넷이 되면 저절로 보내요' stayed for ever and the family saw the old 근무).
     */
    fun refused(e: Throwable): Boolean = e is ShareLinkException && e.message.let { m ->
        m != null && m !in TRANSIENT && !m.startsWith("http_") && m != "not_found" && m != "forbidden"
    }

    /**
     * Busy or away, tried again later: the server's own busy words, a reply that was not the server's (bad_response: a Wi-Fi login
     * page) and a bare status with no word of the server's (http_429 from a proxy).
     */
    private val TRANSIENT = setOf("rate_limited", "paused", "server", "bad_response", "unsupported")

    /** The icon names every share server takes (1.34~1.39 took only these, so an emoji 근무 그림 was refused). */
    private val PLAIN_ICON = Regex("^[a-z0-9]{1,16}$")

    /**
     * [RVG-27] 검토 SN-13: [payload] without the part the server turned down ([code]), and the words that tell the sender what
     * was left out; null when the app cannot leave that part out (the link is then said to be refused, not offline).
     * - "type_icon": an emoji 근무 그림 (e:☀️, from a 작품 or the 근무 종류 editor) on an older share server; the 근무 goes without it.
     * - "type_rest": the break ([RVG-28]) on a server that reads it differently; the 근무 goes with its times only.
     * - "look" / "kit": the theme or the 근무 설정 ticked to go along ([RVG-29] SN-14); the 근무 goes without it.
     */
    fun withoutRefused(payload: JSONObject, code: String?): Pair<JSONObject, String>? =
        leaveOut(payload, code)?.let { (body, name) -> body to leftOutWords(listOf(name)) }

    /** [RVG-27] [withoutRefused] with the part's own name ("근무 그림 ☀️", "테마") instead of a sentence, for [leftOutWords]. */
    fun leaveOut(payload: JSONObject, code: String?): Pair<JSONObject, String>? {
        val copy = JSONObject(payload.toString())
        when (code) {
            "look" -> return if (copy.remove("look") == null) null else copy to "테마"
            "kit" -> return if (copy.remove("kit") == null) null else copy to "근무 설정"
        }
        val types = copy.optJSONArray("types") ?: return null
        val left = ArrayList<String>()
        for (i in 0 until types.length()) {
            val t = types.optJSONObject(i) ?: continue
            when (code) {
                "type_icon" -> t.optString("icon").takeIf { t.has("icon") && !PLAIN_ICON.matches(it) }?.let { icon ->
                    left += com.bamnat.core.model.ShiftIcon.emoji(icon) ?: icon.take(4)
                    t.remove("icon")
                }
                "type_rest" -> if (t.has("rest") || t.has("restStart")) { left += t.optString("name"); t.remove("rest"); t.remove("restStart") }
            }
        }
        if (left.isEmpty()) return null
        return copy to if (code == "type_icon") "근무 그림 ${left.distinct().joinToString(" ")}" else "휴게 시간"
    }

    /**
     * [RVG-27] One sentence for every part left out, so two parts are one line, not two cards: "근무 그림 ☀️·테마는 링크에 못 실어 빼고
     * 보냈어요." The 은/는 follows the last part's last syllable (an emoji takes 는, as said aloud).
     */
    fun leftOutWords(names: List<String>): String {
        val last = names.last().trimEnd().last()
        val topic = if (last in '가'..'힣' && (last - '가') % 28 != 0) "은" else "는"
        return "${names.distinct().joinToString("·")}$topic 링크에 못 실어 빼고 보냈어요."
    }

    /**
     * 2.0.0 [RVG-27] 검토 S7-3 (SD-09, 갈래 R 의견): the automatic update turned down twice running for the same thing that cannot be
     * left out; said once above the calendar, since the person may never open the share sheet where it is said each time.
     */
    const val REFUSED_AGAIN = "바뀐 근무가 링크에 안 갔어요. 공유에서 다시 보내 주세요."

    /** [RVG-28] The longest break a link carries, in minutes (the share server's RVD-23 limit). */
    const val REST_MAX = 720

    /** Days a link covers, from the first day of the month shown: about three months. */
    const val DAYS = 92
    val TTL_DAYS = listOf(7, 30, 90, 180, 365)
    /** [PROV-04] Periods past three months are 프로's; a link with one extends by 90 days without it. */
    val PRO_TTL_DAYS = setOf(180, 365)
    const val FREE_TTL_MAX = 90

    /** [PROV-04] "180일", and a year as "1년". */
    fun ttlLabel(days: Int): String = if (days == 365) "1년" else "${days}일"

    /** [PROV-04] The period a link is kept open for when 늘리기 asks for [days]: a 프로 period without 프로 is 90 days. */
    fun allowedTtl(days: Int, pro: Boolean): Int = if (pro || days !in PRO_TTL_DAYS) days else FREE_TTL_MAX

    /**
     * 1.36 사용자 결정(2026-10-31, 공유 링크 기간): a 프로 sharer's links stay open for as long as 프로 lasts, so they are kept
     * a year ahead whenever less than [PRO_RENEW_BELOW_DAYS] is left. Without 프로 a link kept that way closes
     * [FREE_TTL_MAX] days from now at most, and is kept longer by hand (기간 늘리기) from then. Receivers never need 프로.
     * The period to send now, or null to leave the end date as it is.
     */
    fun keepOpen(link: ShareLinkState, now: java.time.Instant, pro: Boolean): Int? {
        val left = java.time.Duration.between(now, link.expiresAt).toDays()
        return when {
            left < 0 -> null
            pro -> PRO_KEEP_DAYS.takeIf { left < PRO_RENEW_BELOW_DAYS }
            link.ttlDays in PRO_TTL_DAYS && left > FREE_TTL_MAX -> FREE_TTL_MAX
            else -> null
        }
    }
    const val PRO_KEEP_DAYS = 365
    const val PRO_RENEW_BELOW_DAYS = 180L

    /**
     * [PROV-04] The time zone a calendar feed's times are in, as the server takes it ("Asia/Seoul"); null
     * for one it would refuse ("UTC", "GMT+09:00"), which then reads as Seoul time.
     */
    fun feedZone(zone: java.time.ZoneId): String? = zone.id.takeIf { ZONE.matches(it) && it.length <= 40 }
    private val ZONE = Regex("""[A-Za-z]+(?:/[A-Za-z0-9_+-]+){1,2}""")

    /** [SHR-01] The months a link made from [from] covers, for its pay. */
    fun months(from: LocalDate): List<YearMonth> {
        val end = from.plusDays(DAYS - 1L)
        // 빌드 10 (3개월 링크인데 급여 줄이 '1월·2월·3월·4월'): a month the link only touches for its last day or two is left out.
        return generateSequence(YearMonth.from(from)) { it.plusMonths(1) }.takeWhile { !it.atDay(1).isAfter(end) }
            .filterIndexed { i, m -> i == 0 || java.time.temporal.ChronoUnit.DAYS.between(m.atDay(1), end) + 1 >= 7 }.toList()
    }

    /** [SHR-01] A day's memos as one line of at most 80 characters, the way the page shows them: each memo's first line only. */
    fun memoLine(memos: List<Memo>): String? {
        val line = memos.map { m -> m.firstLine().replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotEmpty() }.joinToString(" · ")
        if (line.isEmpty()) return null
        return if (line.length <= MEMO_CHARS) line else line.take(MEMO_CHARS - 1).trimEnd() + "…"
    }

    /** The title, else the body's first line or a checklist's first item, never the rest of it. */
    private fun Memo.firstLine(): String {
        fun first(text: String) = text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        return first(title) ?: if (isChecklist) checklist.firstOrNull { it.text.isNotBlank() }?.text.orEmpty() else first(text).orEmpty()
    }

    const val MEMO_CHARS = 80
    /** [SHR-20] The share server's limit for a setup file. */
    const val SETUP_CHARS = 24 * 1024
    /** 1.37 [SHR-31] The share server's limit for a link's 테마 (a recipe of looks, [com.bamnat.core.recipe.RecipeCodec.MAX_BYTES] at most). */
    const val LOOK_CHARS = com.bamnat.core.recipe.ShareKit.LOOK_CHARS
    /** 1.37 [SHR-32] The share server's limit for a link's 근무 설정 (a recipe of 근무 꾸미기·알람·근무 주기). */
    const val KIT_CHARS = com.bamnat.core.recipe.ShareKit.KIT_CHARS
    /** Up to "0.75일 무급 휴가", as the server takes it. */
    const val LEAVE_CHARS = 12

    /**
     * [SHR-01] A day's leave as a link shows it: 연차 as it is, another kind by name only when 휴가 종류
     * is ticked, else only how much of the day is off.
     */
    fun leaveLabel(leave: Leave, kinds: Boolean): String = (
        if (kinds || leave.kind == LeaveKind.ANNUAL) leave.label
        else when (leave.quarters) { 4 -> "휴가"; 2 -> "반차"; 1 -> "반반차"; else -> "0.75일 휴가" }
    ).take(LEAVE_CHARS)

    /** [SHR-01] Extra work a link carries for one day, as the server takes it. */
    const val EXTRAS_PER_DAY = 4
    /** [SHR-11] Jobs a link carries for one day, and the characters of each title, as the server takes them. */
    const val JOBS_PER_DAY = 4
    const val JOB_CHARS = 12

    /**
     * [SHR-12] What a payload says about the schedule, without the period: the app sends a link again
     * only when this changed since it last sent it.
     */
    fun fingerprint(payload: JSONObject): String {
        val copy = JSONObject(payload.toString()).apply { remove("ttlDays") }
        val digest = java.security.MessageDigest.getInstance("SHA-256").digest(copy.toString().toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }

    /**
     * [SHR-12] The month a link sent now starts at: the month it was made from, or this month once that
     * one is over, so the three months move along with time and never show only the past.
     */
    fun startMonth(link: ShareLinkState, today: LocalDate): YearMonth =
        maxOf(link.from ?: YearMonth.from(today), YearMonth.from(today))

    /** [SHR-11] Whether a payload has anything to show: a server refuses a link with no days and no money. */
    fun hasContent(payload: JSONObject): Boolean =
        (payload.optJSONArray("days")?.length() ?: 0) > 0 || (payload.optJSONArray("pay")?.length() ?: 0) > 0

    fun build(
        snapshot: ScheduleSnapshot,
        from: LocalDate,
        title: String,
        /** [SHR-12] Null keeps the link's end date: what the app sends by itself after a change. */
        ttlDays: Int?,
        options: ShareOptions = ShareOptions(),
        /** [SHR-01] Each day's memo line, see [memoLine]; sent only with [ShareOptions.memos]. */
        memos: Map<LocalDate, String> = emptyMap(),
        /** [SHR-01] Sent only with [ShareOptions.pay]. */
        pay: List<SharedPay> = emptyList(),
        /** [SHR-11] What [pay] is: the server and the receiving app name it by this. */
        payKind: com.bamnat.core.work.MoneyShare = com.bamnat.core.work.MoneyShare.PAY,
        /** [SHR-11] Each day's jobs; sent only with [ShareOptions.jobs]. */
        jobs: Map<LocalDate, List<SharedJob>> = emptyMap(),
        /** [FEAT-15] The calendar feed (…/s/<id>.ics) is on, with its times in this zone; null keeps it off. */
        feed: java.time.ZoneId? = null,
        /** [SHR-20] The company setup file (no amounts) for a coworker's link; sent only with [ShareOptions.sendsSetup]. */
        setup: String? = null,
        /** 1.37 [SHR-31] The 테마 as a recipe's text ([ShareContent.lookFile]); sent only with [ShareOptions.look]. */
        look: String? = null,
        /** 1.37 [SHR-32] The 근무 설정 as a recipe's text ([ShareContent.kitFile]); sent only with [ShareOptions.kit]. */
        kit: String? = null,
        /** 1.38 [SLEEP-11] The open sleep's start and the next alarm (epoch millis); sent only with [ShareOptions.sleepNow]. */
        sleepNow: SleepNow? = null,
        /** 1.39 G5-N4 The planned sleeps not over yet (epoch millis); sent only with [ShareOptions.sleepPlan]. */
        sleepPlan: List<SleepPlanWindow> = emptyList(),
        /** 2.0.0 [RVH-45] The 1일 기준 (LeavePolicy.dayMinutes) an hour's leave is told against for a receiver before 2.0.0. */
        leaveDayMinutes: Int = Leave.DAY_MINUTES,
    ): JSONObject {
        val dates = (0 until DAYS).map { from.plusDays(it.toLong()) }
        val days = JSONArray()
        val used = LinkedHashMap<Long, com.bamnat.core.model.ShiftType>()
        for (d in dates) {
            val day = snapshot.resolver.resolve(d)
            val type = day.shiftType
            val memo = memos[d]?.takeIf { options.memos }
            val extras = if (options.extras) day.extras.take(EXTRAS_PER_DAY) else emptyList()
            val booked = if (options.jobs) jobs[d].orEmpty().take(JOBS_PER_DAY) else emptyList()
            // [EXTRA-01] A 대근 of another shift shows as the day's second shift; other extra work stays private unless ticked.
            val second = day.coveredShift
            // A day with no shift goes in only with something on it: a 대근, or a memo, extra work or job that was ticked.
            if (type == null && second == null && memo == null && extras.isEmpty() && booked.isEmpty()) continue
            type?.let { used[it.id] = it }
            second?.let { used[it.id] = it }
            days.put(JSONObject().apply {
                put("date", d.toString())
                if (type != null) put("type", type.id)
                second?.let { put("second", it.id) }
                // The kind of leave (유급·무급 휴가) is private unless ticked; family only needs to know how much of the day is off.
                // 2.0.0 [RVH-45] An hour's leave (설계 4-4절, 총괄 1-2): "leave" is what a receiver before 2.0.0 reads, so it says '반차' with
                // the hours from half a day up and nothing below (that receiver reads a leave without '반' as a whole day off); the
                // minutes and where in the day go in keys of their own.
                day.leave?.let { l ->
                    if (!l.hourly) put("leave", leaveLabel(l, options.leaveKinds))
                    else {
                        l.linkText(options.leaveKinds, leaveDayMinutes)?.let { put("leave", it.take(LEAVE_CHARS)) }
                        put("leaveMinutes", l.minutes)
                        if (l.at != null) put("leaveAt", l.atMinute) else if (l.fromStart) put("leaveStart", true)
                    }
                }
                // [SHR-04] No work at all as far as the link shows, for the receiver's 같이 쉬는 날: extra work
                // the sharer did not tick stays out of it too, only a 대근 counts.
                // [SHR-11] A booked job is work too, when the sharer shows jobs.
                put("rest", (day.isOff || day.leave?.isFullDay == true) && (if (options.extras) day.extras.isEmpty() else second == null) && booked.isEmpty())
                memo?.let { put("memo", it) }
                if (extras.isNotEmpty()) put("extras", JSONArray().apply {
                    extras.forEach { e ->
                        put(JSONObject().apply {
                            put("name", (e.covered?.let { c -> ExtraText.coverName(e.kind, c) } ?: e.kind.name).take(10))
                            if (e.entry.hasTimes && e.start != null && e.end != null) put("time", "${e.start}-${e.end}")
                            if (e.minutes in 1..1440) put("minutes", e.minutes)
                        })
                    }
                })
                if (booked.isNotEmpty()) put("jobs", JSONArray().apply {
                    booked.forEach { j ->
                        put(JSONObject().apply {
                            put("name", j.name.trim().take(JOB_CHARS).ifEmpty { "일감" })
                            j.time?.let { put("time", it) }
                        })
                    }
                })
            })
        }
        val types = JSONArray()
        used.values.forEach { t ->
            types.put(JSONObject().apply {
                put("id", t.id)
                put("name", t.name.take(20))
                put("short", t.label.take(4).ifEmpty { t.name.take(1) })
                put("color", "#%06X".format(t.colorArgb and 0xFFFFFF))
                if (t.start != null && t.end != null && !t.isOff) {
                    put("time", "${t.start}-${t.end}")
                    // 2.0.0 [RVG-28] 검토 PC-94: the break goes too, so '내 근무표예요' starts with the same 11 hours, not 12. Always
                    // sent for a timed day (0 = none), so a receiver tells "no break" from a link of an older app (no field).
                    // The share server takes it from 2.0.0 (갈래 D, RVD-23: 0~720, shorter than the day with its gap); an older one
                    // drops it, and from #468 a value it does not take is dropped there too. The same rule here, so what goes is kept.
                    val from0 = t.start!!
                    val span = com.bamnat.core.model.WorkTime.spanMinutes(from0, t.end!!)
                    val gap = if (t.hasGap) t.gapMinutes else 0
                    if (t.breakMinutes in 0..REST_MAX && t.breakMinutes + gap < span) {
                        put("rest", t.breakMinutes)
                        // When the break starts, only where the whole break lies inside the day (as the server checks).
                        t.breakStart?.takeIf { t.breakMinutes > 0 }?.let { at ->
                            val from = com.bamnat.core.model.WorkTime.spanMinutes(from0, at).let { if (it == com.bamnat.core.model.WorkTime.MINUTES_PER_DAY) 0 else it }
                            if (from + t.breakMinutes <= span) put("restStart", "%02d:%02d".format(at.hour, at.minute))
                        }
                    }
                }
                // 2.0.0 [RVH-44] 하루 두 구간: "time" stays the first start to the last end (what a receiver before 2.0.0 reads), the gap apart.
                if (t.hasGap) put("gap", "${t.gapStart}-${t.gapStart!!.plusMinutes(t.gapMinutes.toLong())}")
                if (t.isOff) put("off", true)
                t.icon?.let { put("icon", it) }
            })
        }
        return JSONObject().apply {
            // [SHR-01] Nothing ticked stays v1, which every server version takes.
            put("v", if (options.any) 2 else 1)
            put("title", title.trim().take(30))
            ttlDays?.let { put("ttlDays", it) }
            // [FEAT-15] Without "ics" the server serves no feed for the link; an older server ignores both.
            feed?.let { z ->
                put("ics", true)
                feedZone(z)?.let { put("tz", it) }
            }
            put("types", types)
            put("days", days)
            if (options.any) put("shows", JSONArray(options.shows))
            // [SHR-20] A server from before 1.33 ignores both and keeps the link as it was.
            options.audience?.let { put("audience", it.key) }
            if (options.sendsSetup && setup != null && setup.length <= SETUP_CHARS) put("setup", setup)
            // 1.37 [SHR-31][SHR-32] A server from before 1.37 refuses neither: it drops what it does not know.
            if (options.look && look != null && look.length <= LOOK_CHARS) put("look", look)
            if (options.kit && kit != null && kit.length <= KIT_CHARS) put("kit", kit)
            if (options.sleepNow && sleepNow != null) put("sleepNow", JSONObject().apply {
                put("since", sleepNow.since)
                if (sleepNow.wakeAt != null && sleepNow.wakeAt > sleepNow.since) put("wakeAt", sleepNow.wakeAt)
            })
            // 1.39 G5-N4 A server from before 1.39 drops what it does not know.
            if (options.sleepPlan && sleepPlan.isNotEmpty()) put("sleepPlan", JSONArray().apply {
                sleepPlan.take(com.bamnat.core.sleep.SleepPlanShare.MAX).forEach { w -> put(JSONObject().put("from", w.from).put("to", w.to)) }
            })
            if (options.pay && pay.isNotEmpty()) put("payKind", payKind.key)
            if (options.pay && pay.isNotEmpty()) put("pay", JSONArray().apply {
                pay.take(6).forEach { p ->
                    put(JSONObject().apply {
                        put("month", p.month.toString())
                        put("amount", p.amount.coerceIn(0, 10_000_000_000))
                        put("label", p.label.take(10))
                    })
                }
            })
        }
    }
}

/**
 * [PROV-04] Which of a calendar's links: 1 is the one every calendar has, named by the calendar's id
 * alone as links always were; 프로 adds up to [MAX] in all, "<calendar>~2" to "<calendar>~5", each sent to
 * whom and for as long as the sharer likes. The key is what the store, the automatic update and the
 * expiry notice go by.
 */
object ShareLinkKey {
    const val MAX = 5
    /** Links a calendar has without 프로. */
    const val FREE = 1
    private const val SEP = '~'

    fun of(calendarId: String, slot: Int): String = if (slot <= 1) calendarId else "$calendarId$SEP$slot"
    fun calendar(key: String): String = key.substringBefore(SEP)
    fun slot(key: String): Int = key.substringAfter(SEP, "1").toIntOrNull()?.takeIf { it in 1..MAX } ?: 1
    /** Every link [calendarId] can have, the first first. */
    fun all(calendarId: String): List<String> = (1..MAX).map { of(calendarId, it) }
}

/**
 * The link this phone made for one calendar, kept so it can be updated or cut later. [SHR-12] One
 * instance per link in the app ([com.bamnat.app.AppContainer.shareLinksOf]), so the sheet, the
 * automatic update and the expiry notice all see the same link as it changes. [MCAL-03] Each of the
 * user's calendars has its own link; the main calendar keeps the file links were always kept in.
 * [PROV-04] [key] says which calendar and which of its links, see [ShareLinkKey].
 */
class ShareLinkStore(
    context: Context,
    val key: String = com.bamnat.app.data.PersonalCalendar.MAIN,
    private val clock: () -> Instant = Today::instant,
) {
    private val prefs = context.applicationContext.getSharedPreferences(prefsName(key), Context.MODE_PRIVATE)
    /**
     * [QA-03] Set once the calendar's links were forgotten ([retire]): a send still on its way with this
     * store must not write the link back into a file that was just deleted.
     */
    @Volatile private var retired = false
    /** The calendar this link shows. */
    val calendarId: String get() = ShareLinkKey.calendar(key)
    /** [PROV-04] Which of the calendar's links, from 1. */
    val slot: Int get() = ShareLinkKey.slot(key)

    companion object {
        /** [RVG-27] How long a part the server turned down is left out before the whole is tried again. */
        val LEFT_FOR: java.time.Duration = java.time.Duration.ofDays(1)

        fun prefsName(key: String) =
            if (key == com.bamnat.app.data.PersonalCalendar.MAIN) "share_link" else "share_link_$key"

        /** [MCAL-03] A removed calendar's link details go with it; [PROV-04] all of its links. */
        fun forget(context: Context, calendarId: String) {
            for (key in ShareLinkKey.all(calendarId)) {
                if (key == com.bamnat.app.data.PersonalCalendar.MAIN) continue
                context.applicationContext.getSharedPreferences(prefsName(key), Context.MODE_PRIVATE).edit().clear().commit()
                context.applicationContext.deleteSharedPreferences(prefsName(key))
            }
        }
    }

    private val _link = MutableStateFlow(load())
    /** The link, or null when there is none or it ran out. */
    val link: StateFlow<ShareLinkState?> = _link.asStateFlow()

    fun load(): ShareLinkState? {
        val id = prefs.getString("id", null) ?: return null
        val state = ShareLinkState(
            id = id,
            url = prefs.getString("url", null) ?: return null,
            deleteToken = prefs.getString("token", null) ?: return null,
            expiresAt = runCatching { Instant.parse(prefs.getString("expires", "")) }.getOrNull() ?: return null,
            title = prefs.getString("title", "") ?: "",
            ttlDays = prefs.getInt("ttl", 30),
            from = prefs.getString("from", null)?.let { runCatching { YearMonth.parse(it) }.getOrNull() },
        )
        return state.takeIf { it.expiresAt.isAfter(clock()) }
    }

    /** [SHR-12] Saves the link as the server now has it; [sent] is the [ShareLinkPayload.fingerprint] of what was sent. */
    fun save(s: ShareLinkState, sent: String? = null) {
        if (retired) return
        prefs.edit().putString("id", s.id).putString("url", s.url).putString("token", s.deleteToken)
            .putString("expires", s.expiresAt.toString()).putString("title", s.title).putInt("ttl", s.ttlDays)
            .apply { if (s.from != null) putString("from", s.from.toString()) else remove("from") }
            .apply { if (sent != null) putString("sent", sent) }
            .apply()
        _link.value = s
    }

    /** [PROV-04] A link from a backup, kept like one made here and sent again at the next update. */
    fun restore(s: ShareLinkState, auto: Boolean, feed: Boolean, options: ShareOptions) {
        if (retired) return
        prefs.edit().remove("sent").remove("noticed").apply()
        save(s)
        this.auto = auto
        this.feed = feed
        saveOptions(options)
    }

    /** Forgets the link; what the sharer ticked stays for the next one. */
    fun clear() {
        prefs.edit().remove("id").remove("url").remove("token").remove("expires").remove("title").remove("ttl").remove("from").remove("refused").remove("dropped")
            .remove("refused_code").remove("refused_times").remove("left_parts").remove("left_at").remove("left_version").remove("left_said")
            .remove("sent").remove("noticed").remove("unsent").remove("sent_at").apply()
        _link.value = null
        _unsent.value = false
        _refused.value = false
        _dropped.value = null
    }

    /** [MCAL-03][QA-03] The calendar went: the link is forgotten, and nothing written with this store from now on is kept. */
    fun retire() {
        retired = true
        clear()
    }

    /** [SHR-02] Drops a link that ran out while the app stayed open. */
    fun expire() {
        if (_link.value?.expiresAt?.isAfter(clock()) == false) _link.value = null
    }

    /** [SHR-12] The fingerprint of what the server last got, null when unknown (a link from before 1.14). */
    val sent: String? get() = prefs.getString("sent", null)

    private val _unsent = MutableStateFlow(prefs.getBoolean("unsent", false))
    /**
     * [SHR-13] A change the link has not got yet: the server could not be reached. [OutboxJob] sends it when
     * the phone is online again; the share card says so meanwhile.
     */
    val unsent: StateFlow<Boolean> = _unsent.asStateFlow()

    /** [SHR-13] When the link last got the calendar, or null before 1.23 or for a link not sent since. */
    val sentAt: Instant? get() = prefs.getString("sent_at", null)?.let { runCatching { Instant.parse(it) }.getOrNull() }

    /** [SHR-13] The server has what the calendar shows now ([at]), or it could not be reached ([at] null). */
    fun sendResult(at: Instant?) {
        if (retired) return
        prefs.edit().putBoolean("unsent", at == null).apply { if (at != null) putString("sent_at", at.toString()).remove("refused").remove("refused_code").remove("refused_times") }.apply()
        _unsent.value = at == null
        if (at != null) _refused.value = false
    }

    private val _refused = MutableStateFlow(prefs.getBoolean("refused", false))
    /**
     * 2.0.0 [RVG-27] 검토 SN-13: the server turned the change down for what it carries and the phone could not leave that part out.
     * It is not sent again by itself (it would be turned down again); the share card says so in people's words.
     */
    val refused: StateFlow<Boolean> = _refused.asStateFlow()

    /** [RVG-27] Not sent: turned down, not offline. */
    fun refusedNow() {
        if (retired) return
        prefs.edit().putBoolean("unsent", true).putBoolean("refused", true).apply()
        _unsent.value = true
        _refused.value = true
    }

    private val _dropped = MutableStateFlow(prefs.getString("dropped", null))
    /**
     * [RVG-27] 검토 SN-13: what went out without a part the server would not take ('근무 그림(☀️)은 빼고 보냈어요'), said once above
     * the calendar so the sender knows without opening the share sheet; null once closed.
     */
    val dropped: StateFlow<String?> = _dropped.asStateFlow()

    fun setDropped(words: String?) {
        if (retired) return
        prefs.edit().apply { if (words == null) remove("dropped") else putString("dropped", words) }.apply()
        _dropped.value = words
    }

    /**
     * 2.0.0 [RVG-27] 검토 SN-13 (갈래 R 의견 2): the parts the server turned down, left out of the next sends at once instead of being
     * sent whole, turned down and sent again for every change. Tried whole again after an app update or a day, so a server mended
     * meanwhile (as 46d91ace mended the emoji 그림) gets them back without an update.
     */
    fun leaveOut(): Set<String> {
        val parts = prefs.getString("left_parts", null)?.split(',')?.filter { it.isNotBlank() }?.toSet().orEmpty()
        if (parts.isEmpty()) return emptySet()
        val at = Instant.ofEpochMilli(prefs.getLong("left_at", 0L))
        val sameApp = prefs.getInt("left_version", -1) == com.bamnat.app.BuildConfig.VERSION_CODE
        return if (sameApp && java.time.Duration.between(at, clock()) < LEFT_FOR) parts else emptySet()
    }

    /**
     * [RVG-27] After a send: keeps what the server turned down this time, forgets it once the server took everything, and says what
     * went without above the calendar ([onCalendar]) once for each wording, so closing it holds until something new is left out.
     * The share sheet says it itself ([onCalendar] false); the wording is still marked as said.
     */
    fun leftOut(sent: LeftOut<*>, onCalendar: Boolean) {
        if (retired) return
        val edit = prefs.edit()
        when {
            sent.parts.isEmpty() -> edit.remove("left_parts").remove("left_at").remove("left_version").remove("left_said")
            sent.fresh.isNotEmpty() -> edit.putString("left_parts", sent.parts.joinToString(",")).putLong("left_at", clock().toEpochMilli())
                .putInt("left_version", com.bamnat.app.BuildConfig.VERSION_CODE)
        }
        val words = sent.words
        val say = words != null && words != prefs.getString("left_said", null)
        if (say) edit.putString("left_said", words)
        edit.apply()
        if (say && onCalendar) setDropped(words)
    }

    /**
     * 2.0.0 [RVG-27] 검토 S7-3 (SD-09, 갈래 R 의견 3): an automatic send turned down for [code] with nothing the app can leave out.
     * True only the second time running for the same reason, so it is said once above the calendar ([ShareLinkPayload.REFUSED_AGAIN])
     * for someone who never opens the share sheet; a send that goes through starts the count again.
     */
    fun refusedAgain(code: String?): Boolean {
        if (retired) return false
        val times = if (prefs.getString("refused_code", null) == code) prefs.getInt("refused_times", 0) + 1 else 1
        prefs.edit().putString("refused_code", code).putInt("refused_times", times).apply()
        return times == 2
    }

    /**
     * 2.0.0 [RVG-30] 검토 PC-97 (조건 10): the name typed in '내 이름', kept as it is typed. A link made after a failed try, or after the
     * sheet was closed and opened again, still carries it (before, it went as '이름 없는 근무표'). Kept when the link is cut.
     */
    var typedName: String?
        get() = prefs.getString("typed_name", null)
        set(value) { if (!retired) prefs.edit().apply { if (value == null) remove("typed_name") else putString("typed_name", value.take(30)) }.apply() }

    /** [SHR-12] Whether a change on the calendar goes to the link by itself; on unless the sharer turned it off. */
    var auto: Boolean
        get() = prefs.getBoolean("auto", true)
        set(value) { if (!retired) prefs.edit().putBoolean("auto", value).apply() }

    /** [SHR-12] The end date the expiry notice was last posted for, so it comes once per period. */
    var noticedFor: Instant?
        get() = prefs.getString("noticed", null)?.let { runCatching { Instant.parse(it) }.getOrNull() }
        set(value) { if (!retired) prefs.edit().apply { if (value == null) remove("noticed") else putString("noticed", value.toString()) }.apply() }

    /** [SHR-01] What the sharer ticked last time, used again for the next link and for 고치기. */
    fun options(): ShareOptions = ShareOptions.of(prefs.getStringSet("shows", emptySet()).orEmpty(),
        ShareAudience.of(prefs.getString("audience", null)), prefs.getBoolean("setup", false), prefs.getBoolean("look", false), prefs.getBoolean("kit", false),
        prefs.getBoolean("sleepNow", false), prefs.getBoolean("sleepPlan", false))

    fun saveOptions(o: ShareOptions) {
        if (!retired) prefs.edit().putStringSet("shows", o.shows.toSet()).putString("audience", o.audience?.key).putBoolean("setup", o.setup)
            .putBoolean("look", o.look).putBoolean("kit", o.kit).putBoolean("sleepNow", o.sleepNow).putBoolean("sleepPlan", o.sleepPlan).apply()
    }

    /**
     * [FEAT-15] Whether the link also serves a calendar feed (…/s/<id>.ics) to subscribe to; off until the
     * sharer turns it on. It goes out only with 프로 ([com.bamnat.app.tier.Feature.ICS_FEED]).
     */
    var feed: Boolean
        get() = prefs.getBoolean("ics", false)
        set(value) { if (!retired) prefs.edit().putBoolean("ics", value).apply() }
}

/** [FEAT-15] A link's calendar feed address: the page's address with .ics, which the server serves while the feed is on. */
fun ShareLinkState.feedUrl(): String = url.substringBefore('?').trimEnd('/') + ".ics"

/** [SHR-02] A link as the app reads it for 받은 달력: the schedule JSON as the server keeps it. */
data class ReceivedShare(
    val schedule: JSONObject,
    val expiresAt: Instant,
    val updatedAt: Instant?,
    /** [SHR-12] The server's tag for this version, asked with next time so an unchanged link is not downloaded again. */
    val etag: String? = null,
    /** 1.38 [P3-L-18] The leader turned on 피곤해요 합계 for this link: the member's app may offer 피곤해요 팀에 알리기 ([ShareApi.tiredSay]). */
    val tired: Boolean = false,
)

/** [MKT-09][SHR-02] The link server; tests and screenshots use a fake. */
interface ShareApi {
    val enabled: Boolean
    suspend fun create(payload: JSONObject): ShareLinkState
    suspend fun update(state: ShareLinkState, payload: JSONObject): ShareLinkState
    suspend fun delete(state: ShareLinkState)
    /** Throws [ShareLinkException] "not_found" once the link was cut or ran out. */
    suspend fun read(id: String): ReceivedShare

    /**
     * [SHR-12] Reads the link only if it changed since the version tagged [etag]: null when it did not.
     * A server or fake without tags always reads it.
     */
    suspend fun readIfChanged(id: String, etag: String?): ReceivedShare? = read(id)

    /**
     * 1.39 G2: [readIfChanged] for a check the user did not ask for (받은 근무표 따라가기 every 6 hours, 하루 한 번 소식), so the sharer's
     * 봤어요 is not set by it.
     */
    suspend fun readQuietly(id: String, etag: String?): ReceivedShare? = readIfChanged(id, etag)

    /** [SRV-05] Reports a received link to the developer ([ShareReport] reasons); nobody learns who sent it. */
    suspend fun report(id: String, reason: String) {}

    /** [SHR-14] Starts a 모임 코드 holding the user's own link [own]. */
    suspend fun groupStart(own: ShareLinkState): ShareGroup = throw ShareLinkException("unsupported")

    /** [SHR-14] Adds [own] to the group [code]. */
    suspend fun groupJoin(code: String, own: ShareLinkState): ShareGroup = throw ShareLinkException("unsupported")

    /** [SHR-14] The links still open in [code]; "not_found" once none is. */
    suspend fun groupRead(code: String): ShareGroup = throw ShareLinkException("unsupported")

    /** [SHR-14] Takes [own] out of [code]. */
    suspend fun groupLeave(code: String, own: ShareLinkState) {}

    /** 1.38 [P3-B-11] A staff member's 출퇴근 링크: [name] and [place] are all the page shows. */
    suspend fun clockCreate(name: String, place: String): ClockLink = throw ShareLinkException("unsupported")

    /** 1.38 [P3-B-11] The presses on link [id] so far (the server keeps about two months); "not_found" once cut or run out. */
    suspend fun clockRead(id: String, token: String): List<ClockPress> = throw ShareLinkException("unsupported")

    /** 1.38 [P3-B-11] Cuts the link (퇴사·끊기). A link the server no longer has counts as cut. */
    suspend fun clockDelete(id: String, token: String) {}

    /** 1.38 [P3-L-13] Opens a 근무 신청 round: a link per name in [names], in that order. */
    suspend fun askCreate(team: String, month: java.time.YearMonth, rules: com.bamnat.core.team.AskRules, shifts: List<com.bamnat.core.team.TeamShift>, names: List<String>,
                          leave: List<Double?> = emptyList()): AskMade =
        throw ShareLinkException("unsupported")

    /** 1.38 [P3-L-13] What the members asked so far; "not_found" once the round is gone. */
    suspend fun askRead(id: String, token: String): AskGot = throw ShareLinkException("unsupported")

    /** 1.38 [P3-L-13] 마감·다시 열기 ([closed]) or a new 마감 시각 ([closesAt], epoch millis; null for none). */
    suspend fun askChange(id: String, token: String, closed: Boolean? = null, closesAt: Long? = null, clearCloses: Boolean = false) {}

    /** 1.38 [P3-L-13] Ends the round: the members' requests on the server go with it. Gone already counts as done. */
    suspend fun askDelete(id: String, token: String) {}

    /** 1.38 [P3-X-01] A yes/no ask to [people] (결원 메우기), open [hours]. */
    suspend fun callCreate(team: String, title: String, detail: String, people: List<String>, hours: Int): CallMade = throw ShareLinkException("unsupported")

    /** 1.38 [P3-X-01] The answers so far by person index (true: 들어갈 수 있어요), who opened it, and who was chosen. */
    suspend fun callRead(id: String, token: String): CallGot = throw ShareLinkException("unsupported")

    /** 1.38 [P3-X-01] Who the leader chose (person index), so every page says so; null takes it back. */
    suspend fun callTake(id: String, token: String, taken: Int?) {}

    suspend fun callDelete(id: String, token: String) {}

    /**
     * 1.38 [P3-X-02] 바꾸기 요청 links for [month]: each person's row of labels ([labels], "" for none) and who may trade which day
     * ([ok]: person index → day of month → person indexes), worked out on this phone.
     */
    suspend fun swapCreate(team: String, month: java.time.YearMonth, people: List<String>, labels: List<List<String>>, ok: Map<Int, Map<Int, List<Int>>>): CallMade =
        throw ShareLinkException("unsupported")

    suspend fun swapRead(id: String, token: String): SwapGot = throw ShareLinkException("unsupported")

    /** New rows and trades after the board changed, and/or the leader's word on asks ([decide]: person, day, approved). */
    suspend fun swapChange(id: String, token: String, labels: List<List<String>>? = null, ok: Map<Int, Map<Int, List<Int>>>? = null,
                           decide: List<Triple<Int, Int, Boolean>> = emptyList(), closed: Boolean? = null) {}

    suspend fun swapDelete(id: String, token: String) {}

    /** 1.38 [P3-X-06] A 근로계약서 for [staff] to read and sign by typing their name; open [days] (30 at most). */
    suspend fun signCreate(place: String, staff: String, title: String, items: List<com.bamnat.core.boss.StaffContract.Item>, days: Int): SignMade =
        throw ShareLinkException("unsupported")

    /** 1.38 [P3-X-06] Whether it was opened and signed; "not_found" once it is gone. */
    suspend fun signRead(id: String, token: String): SignGot = throw ShareLinkException("unsupported")

    suspend fun signDelete(id: String, token: String) {}

    /** 1.38 [P3-L-15] When the link was opened since its last change (null: not yet). The first ask turns it on for that link. */
    suspend fun seen(id: String, token: String): Instant? = null

    /** 1.38 [P3-L-15] 병동 전체 보기: the month's table, a row of labels per name ("" for none). */
    suspend fun boardCreate(team: String, month: java.time.YearMonth, names: List<String>, labels: List<List<String>>, colors: Map<String, String>): SignMade =
        throw ShareLinkException("unsupported")

    suspend fun boardUpdate(id: String, token: String, team: String, month: java.time.YearMonth, names: List<String>, labels: List<List<String>>, colors: Map<String, String>) {}

    suspend fun boardDelete(id: String, token: String) {}

    /** 1.38 [P3-L-18] 피곤해요 합계 받기: a team's box of counts per day, no names. */
    suspend fun tiredCreate(team: String): TiredBox = throw ShareLinkException("unsupported")

    /** The last 7 days (today first); a count is null under [TiredGot.min] people. */
    suspend fun tiredRead(id: String, token: String): TiredGot = throw ShareLinkException("unsupported")

    suspend fun tiredDelete(id: String, token: String) {}

    /** The leader ties a member's link to the box, or unties it with null. */
    suspend fun tiredTie(linkId: String, linkToken: String, box: String?) {}

    /**
     * The member's app, from a received link whose [ReceivedShare.tired] is true: one more 피곤해요 today (KST), counted once a
     * day however often it is pressed. Nothing but the press goes: no mood, sleep time or name. Throws not_found when untied.
     */
    suspend fun tiredSay(shareId: String) {}
}

data class TiredBox(val id: String, val token: String)
data class TiredGot(val min: Int, val days: List<Pair<java.time.LocalDate, Int?>>)

data class SignMade(val id: String, val token: String, val url: String, val expiresAt: Instant)
data class SignGot(val seenAt: Instant?, val signedName: String?, val signedAt: Instant?)

/** 1.38 [P3-X-02] One read: asks by person index ([p] asked [q] about day [d]), and who opened their link. */
data class SwapGot(val asks: List<SwapAsk>, val seen: Set<Int>, val closed: Boolean)
data class SwapAsk(val p: Int, val q: Int, val d: Int, val state: String, val at: Instant)

data class CallMade(val id: String, val token: String, val urls: List<String>, val expiresAt: Instant)
data class CallGot(val answers: Map<Int, Boolean>, val seen: Set<Int>, val taken: Int?)

/** 1.38 [P3-L-13] A round opened: its [id] and key, and each member's link in the order asked. */
data class AskMade(val id: String, val token: String, val urls: List<String>)

/** 1.38 [P3-L-13] One read of a round: requests by member index ([AskWish.m]), the members' lines and who opened their link. */
data class AskGot(val wishes: List<AskWish>, val notes: Map<Int, String>, val seen: Set<Int>, val closed: Boolean)

data class AskWish(val m: Int, val date: java.time.LocalDate, val kind: com.bamnat.core.team.WishKind, val shiftId: Long?, val at: Instant)

/** 1.38 [P3-B-11] A 출퇴근 링크 made for one staff member. */
data class ClockLink(val id: String, val url: String, val token: String)

/** 1.38 [P3-B-11] One press: [inside] true for 출근, at the server's time. */
data class ClockPress(val at: Instant, val inside: Boolean)

/** [SRV-05] Why a link is reported: the share server's codes and the words for them. */
enum class ShareReport(val code: String, val label: String) {
    PERSONAL("personal", "다른 사람의 개인정보가 있어요"),
    ABUSE("abuse", "욕설·괴롭힘이 있어요"),
    SPAM("spam", "광고·스팸이에요"),
    ILLEGAL("illegal", "불법 정보예요"),
    OTHER("other", "그 밖의 문제"),
}

/** [SHR-02] The link id in a shared message: "…/s/<id>" from the web page, or bamnat://s/<id> from its app button. */
object ShareLinkId {
    private val pattern = Regex("""(?:https?://[^\s/]+|bamnat:/)/s/([A-Za-z0-9]{16,32})(?![A-Za-z0-9])""")

    fun find(text: String?): String? = text?.let { pattern.find(it)?.groupValues?.get(1) }
}

class ShareLinkException(
    message: String,
    /** [SHR-13] Why the developer took the link down, when the share server says (410 removed). */
    val removedReason: String? = null,
    /** 1.39 [CARE-09] With "contact": phone, link, account or messenger (team links take none of them, against scams). */
    val why: String? = null,
    /** 1.39 [CARE-09] 7차 PC: the refused line as sent (40 chars) and its field, so the app says whose line and where to fix it. */
    val text: String? = null,
    val key: String? = null,
) : IOException(message) {
    /** What to tell the person for a refusal this file knows the words for, or null. */
    val friendly: String? get() = if (message == "contact") com.bamnat.core.plaza.TeamLinkText.contact(why, text, key) else null
}

class ShareLinkClient(private val baseUrl: String = BuildConfig.SHARE_BASE_URL) : ShareApi {
    override val enabled: Boolean get() = baseUrl.isNotBlank()

    override suspend fun create(payload: JSONObject): ShareLinkState = withContext(Dispatchers.IO) {
        val o = request("POST", "/api/share", payload, null).body
        readable {
            ShareLinkState(o.getString("id"), o.getString("url"), o.getString("deleteToken"),
                Instant.parse(o.getString("expiresAt")), payload.optString("title"), ttlDays = payload.optInt("ttlDays", 30))
        }
    }

    override suspend fun update(state: ShareLinkState, payload: JSONObject): ShareLinkState = withContext(Dispatchers.IO) {
        val o = request("PUT", "/api/share/${state.id}", payload, state.deleteToken).body
        readable {
            state.copy(url = o.getString("url"), expiresAt = Instant.parse(o.getString("expiresAt")), title = payload.optString("title"),
                ttlDays = if (payload.has("ttlDays")) payload.optInt("ttlDays", state.ttlDays) else state.ttlDays)
        }
    }

    /** Cuts the link. A link the server no longer has counts as cut. */
    override suspend fun delete(state: ShareLinkState) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/share/${state.id}", null, state.deleteToken)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    override suspend fun read(id: String): ReceivedShare = readIfChanged(id, null)!!

    override suspend fun readIfChanged(id: String, etag: String?): ReceivedShare? = readAt(id, etag, quiet = false)

    override suspend fun readQuietly(id: String, etag: String?): ReceivedShare? = readAt(id, etag, quiet = true)

    private suspend fun readAt(id: String, etag: String?, quiet: Boolean): ReceivedShare? = withContext(Dispatchers.IO) {
        val reply = request("GET", "/api/share/$id", null, null, etag, quiet)
        if (reply.code == HttpURLConnection.HTTP_NOT_MODIFIED) return@withContext null
        val o = reply.body
        readable {
            ReceivedShare(o.getJSONObject("schedule"), Instant.parse(o.getString("expiresAt")),
                o.optString("updatedAt").takeIf { it.isNotEmpty() && it != "null" }?.let { runCatching { Instant.parse(it) }.getOrNull() },
                reply.etag, o.optBoolean("tired", false))
        }
    }

    override suspend fun report(id: String, reason: String) = withContext(Dispatchers.IO) {
        request("POST", "/api/share/$id/report", JSONObject().put("reason", reason), null)
        Unit
    }

    override suspend fun groupStart(own: ShareLinkState): ShareGroup = withContext(Dispatchers.IO) {
        group(request("POST", "/api/groups", JSONObject().put("link", own.id).put("token", own.deleteToken), null).body)
    }

    override suspend fun groupJoin(code: String, own: ShareLinkState): ShareGroup = withContext(Dispatchers.IO) {
        group(request("POST", "/api/groups/$code", JSONObject().put("link", own.id).put("token", own.deleteToken), null).body)
    }

    override suspend fun groupRead(code: String): ShareGroup = withContext(Dispatchers.IO) {
        group(request("GET", "/api/groups/$code", null, null).body)
    }

    override suspend fun groupLeave(code: String, own: ShareLinkState) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/groups/$code/${own.id}", null, own.deleteToken)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    override suspend fun clockCreate(name: String, place: String): ClockLink = withContext(Dispatchers.IO) {
        val o = request("POST", "/api/clock", JSONObject().put("name", name).put("place", place), null).body
        readable { ClockLink(o.getString("id"), o.getString("url"), o.getString("token")) }
    }

    override suspend fun clockRead(id: String, token: String): List<ClockPress> = withContext(Dispatchers.IO) {
        val a = request("GET", "/api/clock/$id", null, token).body.optJSONArray("punches") ?: org.json.JSONArray()
        (0 until a.length()).mapNotNull { i ->
            val p = a.optJSONObject(i) ?: return@mapNotNull null
            val at = runCatching { Instant.parse(p.optString("at")) }.getOrNull() ?: return@mapNotNull null
            when (p.optString("k")) { "in" -> ClockPress(at, true); "out" -> ClockPress(at, false); else -> null }
        }
    }

    override suspend fun clockDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/clock/$id", null, token)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    override suspend fun askCreate(team: String, month: java.time.YearMonth, rules: com.bamnat.core.team.AskRules, shifts: List<com.bamnat.core.team.TeamShift>, names: List<String>,
                                   leave: List<Double?>): AskMade =
        withContext(Dispatchers.IO) {
            val r = JSONObject().put("weekdayCap", rules.weekdayCap).put("weekendCap", rules.weekendCap).put("perPerson", rules.perPerson)
                .put("closesAt", rules.closesAt ?: JSONObject.NULL).put("kinds", org.json.JSONArray(rules.kinds.map { it.name })).put("firstCome", rules.firstCome)
                .put("holidays", org.json.JSONArray(rules.holidays.sorted().map { it.toString() })).put("blocked", org.json.JSONArray(rules.blocked.sorted().map { it.toString() }))
                .put("showNames", rules.showNames)
            val body = JSONObject().put("team", team).put("month", month.toString()).put("rules", r)
                .put("shifts", org.json.JSONArray(shifts.map { JSONObject().put("id", it.id).put("label", it.label.take(4)).put("color", "#%06X".format(it.colorArgb and 0xFFFFFF)) }))
                .put("members", org.json.JSONArray(names))
            if (leave.any { it != null }) body.put("leave", org.json.JSONArray(leave.map { it ?: JSONObject.NULL }))
            val o = request("POST", "/api/ask", body, null).body
            readable {
                val m = o.getJSONArray("members")
                AskMade(o.getString("id"), o.getString("token"), (0 until m.length()).map { m.getJSONObject(it).getString("url") })
            }
        }

    override suspend fun askRead(id: String, token: String): AskGot = withContext(Dispatchers.IO) {
        val o = request("GET", "/api/ask/$id", null, token).body
        readable {
            val w = o.optJSONArray("wishes") ?: org.json.JSONArray()
            val wishes = (0 until w.length()).mapNotNull { i ->
                val x = w.optJSONObject(i) ?: return@mapNotNull null
                val kind = com.bamnat.core.team.WishKind.entries.firstOrNull { it.name == x.optString("k") } ?: return@mapNotNull null
                val date = runCatching { java.time.LocalDate.parse(x.optString("d")) }.getOrNull() ?: return@mapNotNull null
                AskWish(x.optInt("m", -1), date, kind, if (x.has("s")) x.optLong("s") else null, runCatching { Instant.parse(x.optString("at")) }.getOrElse { Today.instant() })
            }
            val n = o.optJSONArray("notes") ?: org.json.JSONArray()
            val notes = (0 until n.length()).mapNotNull { n.optJSONObject(it) }.associate { it.optInt("m", -1) to it.optString("note") }
            val s = o.optJSONArray("seen") ?: org.json.JSONArray()
            AskGot(wishes, notes, (0 until s.length()).map { s.optInt(it, -1) }.toSet(), o.optBoolean("closed"))
        }
    }

    override suspend fun askChange(id: String, token: String, closed: Boolean?, closesAt: Long?, clearCloses: Boolean) = withContext(Dispatchers.IO) {
        val body = JSONObject()
        closed?.let { body.put("closed", it) }
        if (clearCloses) body.put("closesAt", JSONObject.NULL) else closesAt?.let { body.put("closesAt", it) }
        request("PUT", "/api/ask/$id", body, token)
        Unit
    }

    override suspend fun askDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/ask/$id", null, token)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    override suspend fun callCreate(team: String, title: String, detail: String, people: List<String>, hours: Int): CallMade = withContext(Dispatchers.IO) {
        val o = request("POST", "/api/call", JSONObject().put("team", team).put("title", title).put("detail", detail)
            .put("people", org.json.JSONArray(people)).put("hours", hours), null).body
        readable {
            val p = o.getJSONArray("people")
            CallMade(o.getString("id"), o.getString("token"), (0 until p.length()).map { p.getJSONObject(it).getString("url") }, Instant.parse(o.getString("expiresAt")))
        }
    }

    override suspend fun callRead(id: String, token: String): CallGot = withContext(Dispatchers.IO) {
        val o = request("GET", "/api/call/$id", null, token).body
        readable {
            val a = o.optJSONArray("answers") ?: org.json.JSONArray()
            val s = o.optJSONArray("seen") ?: org.json.JSONArray()
            CallGot((0 until a.length()).mapNotNull { a.optJSONObject(it) }.associate { it.optInt("p", -1) to (it.optString("a") == "yes") },
                (0 until s.length()).map { s.optInt(it, -1) }.toSet(), if (o.isNull("taken")) null else o.optInt("taken"))
        }
    }

    override suspend fun callTake(id: String, token: String, taken: Int?) = withContext(Dispatchers.IO) {
        request("PUT", "/api/call/$id", JSONObject().put("taken", taken ?: JSONObject.NULL), token)
        Unit
    }

    override suspend fun callDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/call/$id", null, token)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    override suspend fun signCreate(place: String, staff: String, title: String, items: List<com.bamnat.core.boss.StaffContract.Item>, days: Int): SignMade =
        withContext(Dispatchers.IO) {
            val o = request("POST", "/api/sign", JSONObject().put("place", place).put("staff", staff).put("title", title).put("days", days)
                .put("items", org.json.JSONArray().apply { items.forEach { put(JSONObject().put("t", it.title).put("b", it.body)) } }), null).body
            readable { SignMade(o.getString("id"), o.getString("token"), o.getString("url"), Instant.parse(o.getString("expiresAt"))) }
        }

    override suspend fun signRead(id: String, token: String): SignGot = withContext(Dispatchers.IO) {
        val o = request("GET", "/api/sign/$id", null, token).body
        readable {
            val sg = o.optJSONObject("signed")
            SignGot(o.optString("seenAt").takeIf { !o.isNull("seenAt") && it.isNotEmpty() }?.let(Instant::parse),
                sg?.optString("name"), sg?.optString("at")?.takeIf { it.isNotEmpty() }?.let(Instant::parse))
        }
    }

    override suspend fun seen(id: String, token: String): Instant? = withContext(Dispatchers.IO) {
        val o = request("GET", "/api/share/$id/seen", null, token).body
        readable { o.optString("seenAt").takeIf { !o.isNull("seenAt") && it.isNotEmpty() }?.let(Instant::parse) }
    }

    private fun boardBody(team: String, month: java.time.YearMonth, names: List<String>, labels: List<List<String>>, colors: Map<String, String>) =
        JSONObject().put("team", team).put("month", month.toString()).put("names", org.json.JSONArray(names))
            .put("labels", org.json.JSONArray().apply { labels.forEach { put(org.json.JSONArray(it)) } }).put("colors", JSONObject(colors as Map<*, *>))

    override suspend fun boardCreate(team: String, month: java.time.YearMonth, names: List<String>, labels: List<List<String>>, colors: Map<String, String>): SignMade =
        withContext(Dispatchers.IO) {
            val o = request("POST", "/api/board", boardBody(team, month, names, labels, colors), null).body
            readable { SignMade(o.getString("id"), o.getString("token"), o.getString("url"), Instant.parse(o.getString("expiresAt"))) }
        }

    override suspend fun boardUpdate(id: String, token: String, team: String, month: java.time.YearMonth, names: List<String>, labels: List<List<String>>, colors: Map<String, String>) =
        withContext(Dispatchers.IO) { request("PUT", "/api/board/$id", boardBody(team, month, names, labels, colors), token); Unit }

    override suspend fun boardDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try { request("DELETE", "/api/board/$id", null, token) } catch (e: ShareLinkException) { if (e.message != "not_found") throw e }
        Unit
    }

    override suspend fun tiredCreate(team: String): TiredBox = withContext(Dispatchers.IO) {
        val o = request("POST", "/api/tired", JSONObject().put("team", team), null).body
        readable { TiredBox(o.getString("id"), o.getString("token")) }
    }

    override suspend fun tiredRead(id: String, token: String): TiredGot = withContext(Dispatchers.IO) {
        val o = request("GET", "/api/tired/$id", null, token).body
        readable {
            val a = o.getJSONArray("days")
            TiredGot(o.optInt("min", 3), (0 until a.length()).map { i ->
                val d = a.getJSONObject(i)
                java.time.LocalDate.parse(d.getString("d")) to (if (d.isNull("n")) null else d.getInt("n"))
            })
        }
    }

    override suspend fun tiredDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try { request("DELETE", "/api/tired/$id", null, token) } catch (e: ShareLinkException) { if (e.message != "not_found") throw e }
        Unit
    }

    override suspend fun tiredTie(linkId: String, linkToken: String, box: String?) = withContext(Dispatchers.IO) {
        request("PUT", "/api/share/$linkId/tired", JSONObject().put("box", box ?: JSONObject.NULL), linkToken); Unit
    }

    override suspend fun tiredSay(shareId: String) = withContext(Dispatchers.IO) {
        request("POST", "/api/share/$shareId/tired", null, null); Unit
    }

    override suspend fun signDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/sign/$id", null, token)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    private fun swapBody(labels: List<List<String>>?, ok: Map<Int, Map<Int, List<Int>>>?): JSONObject = JSONObject().apply {
        labels?.let { l -> put("labels", org.json.JSONArray().apply { l.forEach { put(org.json.JSONArray(it)) } }) }
        ok?.let { o -> put("ok", JSONObject().apply { o.forEach { (p, days) -> put(p.toString(), JSONObject().apply { days.forEach { (d, qs) -> put(d.toString(), org.json.JSONArray(qs)) } }) } }) }
    }

    override suspend fun swapCreate(team: String, month: java.time.YearMonth, people: List<String>, labels: List<List<String>>, ok: Map<Int, Map<Int, List<Int>>>): CallMade =
        withContext(Dispatchers.IO) {
            val o = request("POST", "/api/swap", swapBody(labels, ok).put("team", team).put("month", month.toString()).put("people", org.json.JSONArray(people)), null).body
            readable {
                val p = o.getJSONArray("people")
                CallMade(o.getString("id"), o.getString("token"), (0 until p.length()).map { p.getJSONObject(it).getString("url") }, Instant.parse(o.getString("expiresAt")))
            }
        }

    override suspend fun swapRead(id: String, token: String): SwapGot = withContext(Dispatchers.IO) {
        val o = request("GET", "/api/swap/$id", null, token).body
        readable {
            val a = o.optJSONArray("asks") ?: org.json.JSONArray()
            val s = o.optJSONArray("seen") ?: org.json.JSONArray()
            SwapGot((0 until a.length()).mapNotNull { a.optJSONObject(it) }.map {
                SwapAsk(it.optInt("p", -1), it.optInt("q", -1), it.optInt("d"), it.optString("state"), runCatching { Instant.parse(it.optString("at")) }.getOrElse { Today.instant() })
            }, (0 until s.length()).map { s.optInt(it, -1) }.toSet(), o.optBoolean("closed"))
        }
    }

    override suspend fun swapChange(id: String, token: String, labels: List<List<String>>?, ok: Map<Int, Map<Int, List<Int>>>?,
                                    decide: List<Triple<Int, Int, Boolean>>, closed: Boolean?) = withContext(Dispatchers.IO) {
        val body = swapBody(labels, ok)
        if (decide.isNotEmpty()) body.put("decide", org.json.JSONArray().apply {
            decide.forEach { (p, d, yes) -> put(JSONObject().put("p", p).put("d", d).put("state", if (yes) "done" else "denied")) }
        })
        closed?.let { body.put("closed", it) }
        request("PUT", "/api/swap/$id", body, token)
        Unit
    }

    override suspend fun swapDelete(id: String, token: String) = withContext(Dispatchers.IO) {
        try {
            request("DELETE", "/api/swap/$id", null, token)
        } catch (e: ShareLinkException) {
            if (e.message != "not_found") throw e
        }
        Unit
    }

    private fun group(o: JSONObject): ShareGroup = readable { ShareGroup.parse(o) }

    private class Reply(val code: Int, val body: JSONObject, val etag: String?)

    private fun request(method: String, path: String, body: JSONObject?, token: String?, etag: String? = null, quiet: Boolean = false): Reply {
        // [QA130-01] 1.30 AY: no link made, changed or reported by a Play pre-launch robot; to it, as if offline.
        if (!com.bamnat.app.diag.TestLab.allows(method)) throw IOException("test_lab")
        // [SRV-30] 1.36: at s.bamnat.app, or the old workers.dev name when the new one cannot be reached at all.
        return com.bamnat.app.company.ServerAddress.call(baseUrl) { at -> requestAt(at, method, path, body, token, etag, quiet) }
    }

    private fun requestAt(baseUrl: String, method: String, path: String, body: JSONObject?, token: String?, etag: String?, quiet: Boolean = false): Reply {
        // [SPD-26][SPD-27] 1.31: as the company server's requests, through Cronet when there and the connection kept for the next.
        val conn = com.bamnat.app.company.NetEngine.open(URL(baseUrl.trimEnd('/') + path))
        var broke = true
        try {
            conn.requestMethod = method
            conn.connectTimeout = 10_000
            conn.readTimeout = 15_000
            token?.let { conn.setRequestProperty("X-Delete-Token", it) }
            etag?.let { conn.setRequestProperty("If-None-Match", it) }
            if (quiet) conn.setRequestProperty("X-Bamnat-Quiet", "1")
            // 1.39 [CARE-09] 앱 판: 공유 서버가 연락처 걸러내기를 1.39 앱부터만 해요(옛 앱은 예전처럼).
            conn.setRequestProperty("X-Bamnat-App", BuildConfig.VERSION_CODE.toString())
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = conn.responseCode
            if (code == HttpURLConnection.HTTP_NOT_MODIFIED) {
                com.bamnat.app.company.drain(conn)
                broke = false
                return Reply(code, JSONObject(), etag)
            }
            val text = (if (code < 400) conn.inputStream else conn.errorStream)?.use { it.readCapped() }.orEmpty()
            broke = false
            // [SRV-05] A link the developer took down (410 removed) is gone like one its sharer cut;
            // [SHR-13] the reason the developer gave comes with it.
            if (code >= 400) {
                val err = runCatching { JSONObject(text) }.getOrNull()
                val word = err?.optString("error")?.takeIf { it.isNotEmpty() } ?: "http_$code"
                throw ShareLinkException(if (word == "removed") "not_found" else word,
                    err?.optString("reason")?.takeIf { word == "removed" && it.isNotBlank() }?.take(200),
                    err?.optString("why")?.takeIf { word == "contact" && it.isNotBlank() },
                    err?.optString("text")?.takeIf { word == "contact" && it.isNotBlank() }?.take(40),
                    err?.optString("key")?.takeIf { word == "contact" && it.isNotBlank() })
            }
            return Reply(code, if (text.isBlank()) JSONObject() else readable { JSONObject(text) }, conn.getHeaderField("ETag"))
        } finally {
            if (broke) conn.disconnect()
        }
    }

    /** A reply the app cannot read (a page from a wrong address, a login portal) fails like a lost connection, never crashes. */
    private inline fun <T> readable(block: () -> T): T = try {
        block()
    } catch (e: JSONException) {
        throw ShareLinkException("bad_response")
    } catch (e: DateTimeParseException) {
        throw ShareLinkException("bad_response")
    }

    /** The server answers in a few kilobytes and takes 64 KB at most; anything far larger is not it. */
    private fun InputStream.readCapped(): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8 * 1024)
        while (true) {
            val n = read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_REPLY_BYTES) throw ShareLinkException("too_large")
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    private companion object {
        const val MAX_REPLY_BYTES = 256 * 1024
    }
}

/** 1.39 [CARE-09] 7차 PC: the person's words for a refused team or schedule link (contact words), or null for any other failure. */
fun linkProblem(e: Throwable): String? = (e as? ShareLinkException)?.friendly

/**
 * 2.0.0 [RVG-24] 검토 PC-92 (디자인 품질 조건 7·16): a share-server failure said in the three words every screen uses (연결 없음 ·
 * 서버 바쁨 · 시간 넘김), never with the server's own code. Before, a refused link said '링크를 만들지 못했어요 (type_icon). 다시 해
 * 주세요.' — a word the person cannot act on. [first] is the screen's own first half, e.g. "링크를 만들지 못했어요.". A refusal the
 * app has no words for ([linkProblem] null) is said as the server not taking it now; its code goes only into a question ([code]).
 */
fun netWords(e: Throwable, first: String): String = "$first " + when {
    e is java.net.SocketTimeoutException -> com.bamnat.app.ui.components.NetWords.SLOW
    e is ShareLinkException -> com.bamnat.app.ui.components.NetWords.BUSY
    else -> com.bamnat.app.ui.components.NetWords.OFFLINE
}

/** [RVG-24] The server's code for a failure ("type_icon", "http_503"), kept for a question to the developer, never on screen. */
fun failureCode(e: Throwable): String = (e as? ShareLinkException)?.message ?: e.javaClass.simpleName

/**
 * 2.0.0 [RVG-27] 검토 SN-13: what a send that left parts out ([sendLeavingOut]) got back: the server's answer, every part left out
 * ([parts], with the names said in [words]) and the ones the server named this time ([fresh]; the others were known already).
 */
class LeftOut<T>(val value: T, val parts: Set<String>, val names: List<String>, val fresh: Set<String>) {
    /** The one sentence for the sender, null when everything went. */
    val words: String? get() = if (names.isEmpty()) null else ShareLinkPayload.leftOutWords(names)
}

/**
 * 2.0.0 [RVG-27] 검토 SN-13: sends [payload], leaving out each part the server turns down for what it carries, each part once (at most
 * the four the app can leave out), so a link with both an emoji 그림 and a theme the server will not take still goes (갈래 R 의견 1).
 * The parts in [skip] were turned down before and go out at once, without asking the server again for each change (의견 2).
 * A refusal no part can mend is thrown as it came.
 */
inline fun <T> sendLeavingOut(payload: JSONObject, skip: Set<String>, send: (JSONObject) -> T): LeftOut<T> {
    var body = payload
    val parts = LinkedHashSet<String>()
    val names = ArrayList<String>()
    val fresh = LinkedHashSet<String>()
    for (code in skip) ShareLinkPayload.leaveOut(body, code)?.let { (cut, name) -> body = cut; parts += code; names += name }
    while (true) {
        try {
            return LeftOut(send(body), parts, names, fresh)
        } catch (e: ShareLinkException) {
            val code = e.message
            val cut = if (code != null && code !in parts && ShareLinkPayload.refused(e)) ShareLinkPayload.leaveOut(body, code) else null
            if (cut == null || code == null) throw e
            body = cut.first; parts += code; names += cut.second; fresh += code
        }
    }
}

/**
 * 2.0.0 [RVG-27] 검토 SN-13: a link made by hand ('링크로 보내기'); a part the server will not take (an emoji 근무 그림 on a 1.34~1.39
 * share server) is left out and said, instead of '링크를 만들지 못했어요' every time. The words are null when nothing was left out.
 * What was left out is kept on [store], so the automatic update does not ask the server again or say it again above the calendar.
 */
suspend fun ShareApi.createLeaving(payload: JSONObject, store: ShareLinkStore): Pair<ShareLinkState, String?> =
    sendLeavingOut(payload, store.leaveOut()) { create(it) }.let { store.leftOut(it, onCalendar = false); it.value to it.words }

/** [RVG-27] [createLeaving] for a link already made ('지금 고치기', 기간 늘리기). */
suspend fun ShareApi.updateLeaving(link: ShareLinkState, payload: JSONObject, store: ShareLinkStore): Pair<ShareLinkState, String?> =
    sendLeavingOut(payload, store.leaveOut()) { update(link, it) }.let { store.leftOut(it, onCalendar = false); it.value to it.words }
