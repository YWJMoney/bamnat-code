package com.bamnat.app

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [SLP-15] Where a tapped notice opens: the screen it is about, not just the app. The activity
 * hands the target here and the navigation goes there once it can.
 */
object NoticeLinks {
    enum class Target {
        /** 1.38 [P3-B-11] 팀·가게 관리, from a staff member's 출퇴근 notice. */
        MANAGE,
        /** The calendar on today, with 오늘의 흐름 at the top. */
        TODAY,
        MONEY,
        /** [LEDG-16] The money tab with 자동 기록 (결제 알림 읽기) open. */
        AUTO_RECORD,
        /** The pay screen, where 연차 is. */
        LEAVE,
        /** Where the alarm's permissions are checked and fixed. */
        ALARM_CHECK,
        /** [SCHED-01] The memos, from a memo schedule's notice. */
        MEMOS,
        /** [SLP-16] The sleep log and what it says. */
        SLEEP,
        /** 1.39 [FLOW-13] 밤 화면 (clock, tomorrow's work and alarm, sounds, 깼어요), from the 자는 중 notice and 잠들었어요. */
        NIGHT,
        /** [RECV-02] Everything owed to the user. */
        OWED,
        /** [IMPORT-01] Bringing in the phone's calendar. */
        CALENDAR_IMPORT,
        /** [SHR-12] The calendar with the share sheet open, from the link's expiry notice. */
        SHARE,
        /** [UX-03] The calendar with the sheet that takes a received link open. */
        RECEIVE,
        /**
         * 2.0.0 [RVG-34] 내 달력 with the calendar chip's list (달력 이름 ▾) open, from the 알림함's '팀 근무표' line: on a phone the 팀 리더's
         * link waits there, not beside the chip.
         */
        CALENDARS,
        /** [SHFT-06] 듀티표 사진으로 넣기, for the calendar on screen. */
        DUTY_ROSTER,
        /** [SHFT-07] 연간 건강 지표. */
        HEALTH_REPORT,
        /** [CREW-01] 모든 조 보기, for the calendar on screen. */
        TEAMS,
        /** [CAL-22] 1년 달력, for the calendar on screen. */
        YEAR,
        /** [DAYW-06] 근태 엑셀 가져오기. */
        ATTENDANCE_IMPORT,
        /** [DAYW-07] 출퇴근 자동 기록, from its settings row or its notice. */
        COMMUTE,
        /** [INV-01] The investor calendar's 투자 screen. */
        INVEST,
        /** [INV-08] The investor calendar with the trade sheet open, filled in from the alarm in [payload]. */
        INVEST_TRADE,
        /** [MON-06] The 프로 screen, from the trial's notice. */
        PRO,
        /** [PROV-03] 설정 › 데이터 at 매일 자동 백업, from its notice. */
        BACKUP,
        /** [DAILY-06] 내 달력 on the day in [payload] (yyyy-mm-dd) with that day open, from a widget's date. */
        DAY,
        /** [COMM-10] 회사 규칙 광장, from 광장 소식. [TOWN-01] 1.37: 마을 opens on 광장 (not the part last seen). */
        PLAZA,
        /** [TOWN-01] 1.37 마을 › 공방 › 간직한 글 · 내 글 on 내 글, from a 소식 that says only that my posts got answers. */
        VILLAGE_POSTS,
        /** [TOWN-01] 1.37 마을 › 장터, from a 소식 that says only what came new in 장터. */
        MARKET,
        /**
         * 2.0.0 [RVP-43] One work of 장터 by its code in [payload], from the 알림함 ('부탁하신 꾸미기가 풀렸어요', U13b-10; a reaction to
         * one's own work, HQ-52).
         */
        RECIPE,
        /** [GUIDE-20] 1.31: 회사 찾기, from the first week's 시작 단계 card. */
        COMPANY_FIND,
        /** [INQ-05] 문의하기 on 내 문의, from 밤낮 소식's answer line. */
        INQUIRY,
        /** [ALRM-01] 설정 › 데이터 at 휴대폰 캘린더에 근무 쓰기, from the permission notice. */
        PHONE_CALENDAR,
        /** [SRV-09] What the update brought, from a notice's button. */
        WHATS_NEW,
        /** [SHR-14] 우리 조 모아 보기. */
        GROUP,
        /** [PLAZA-04] 내 설정 나누기, from the invitation on the calendar after a setup made by hand. */
        COMPANY_REPORT,
        /** [PLAZA-06] 처음 설정 다시 받기, from 설정 › 도움말, the calendar's menu or 문의하기. */
        SETUP_AGAIN,
        /** [TAX-02] 1.25 W: the money tab with 세금·실수령 open. */
        TAX,
        /** [SPAT-04] The main calendar's pay setup, from the card after a public cycle went in. */
        PAY,
        /** D 1.32 [PAYA-05] 근무·급여 at 월급날 비교, for the calendar in [calendar], from the pay deposit's notice. */
        PAY_GAP,
        /** 1.32.1 [STAB-13] 문의하기 as 앱 멈춤 with the 점검 기록 ticked, from the notice after the app stopped. */
        STOP_REPORT,
        /** [RUL-03] 1.36 a button or rule's 메모 틀 열기: a new memo on the day in [payload] from [com.bamnat.app.logic.LogicLinks.template]. */
        LOGIC_MEMO,
        /** 1.39 [TASK-07] 할 일 위젯: memo `id|yyyy-mm-dd` in [payload] (id 0 is a new one on that day). */
        MEMO_OPEN,
        /** 1.37 [LEDH-14] 가계부 with 돈 적기 open, from the 돈 적기 widget and the quick settings tile. */
        MONEY_ADD,
        /** 1.39 발굴 (마을 돌봄 스레드 요청): the talk of the version in [payload] ([FixTalk]), from '보완 요청이 n개 왔어요'. */
        FIX_TALK,
        /** 2.0.0 [RVN-19] HQ-51 A company's screen on the rule set in [payload] ("<company>/<setup>"), from the 알림함's 맞아요·달라요 row. */
        COMPANY_SETUP,
        /** 2.0.0 [RVN-19] HQ-51 광장 › 시세 (임금표), from the 알림함's row about a rate I sent. */
        RATES,
        /** 2.0.0 [RVN-19] HQ-51 장터 › 요청 판, from the 알림함's row about my 부탁. */
        REQUESTS,
    }

    /**
     * 1.39 발굴: which version's talk [Target.FIX_TALK] opens, carried as one string (ids are plain; the title is encoded). 2.0.0
     * [RVN-19] HQ-51 [focus]: the post to open it on (the 알림함's row about answers to my post), a fourth part an older one lacks.
     */
    data class FixTalk(val company: String, val setup: String, val title: String, val focus: String? = null) {
        val payload: String get() = "$company|$setup|" + java.net.URLEncoder.encode(title, "UTF-8") + (focus?.let { "|$it" } ?: "")

        companion object {
            fun of(payload: String?): FixTalk? {
                val p = payload?.split('|', limit = 4)?.takeIf { it.size >= 3 && it[0].isNotBlank() && it[1].isNotBlank() } ?: return null
                return FixTalk(p[0], p[1], runCatching { java.net.URLDecoder.decode(p[2], "UTF-8") }.getOrDefault(p[2]),
                    p.getOrNull(3)?.takeIf { Regex("p[A-Za-z0-9]{8,30}").matches(it) })
            }
        }
    }

    private const val EXTRA = "com.bamnat.app.extra.OPEN"

    private val _target = MutableStateFlow<Target?>(null)
    val target: StateFlow<Target?> = _target.asStateFlow()

    private const val CALENDAR_EXTRA = "com.bamnat.app.extra.CALENDAR"

    private val _calendar = MutableStateFlow<String?>(null)
    /** [MCAL-03] Which of the user's calendars [target] is about, when a notice says; null is the main one. */
    val calendar: StateFlow<String?> = _calendar.asStateFlow()

    private const val PAYLOAD_EXTRA = "com.bamnat.app.extra.PAYLOAD"

    private val _payload = MutableStateFlow<String?>(null)
    /** [INV-08] What [target] opens on, when a notice says: an investor alarm's id for [Target.INVEST_TRADE]. */
    val payload: StateFlow<String?> = _payload.asStateFlow()

    private const val KEY_EXTRA = "com.bamnat.app.extra.KEY"

    /**
     * 2.0.0 [RVL-08] (검토 DATA-03) A value only this app knows (made once on this phone, kept in no-backup storage): each notice, widget
     * and tile carries it, and [read] follows only an intent that does. [MainActivity] is the launcher, so any app on the phone can
     * start it with extras of its own; without this one, another app could open the pay or money screen, or start 밤 소리 측정 (the
     * microphone) through the bedtime notice's 측정 시작, with nobody pressing anything.
     */
    @Volatile private var key: String? = null

    internal fun key(context: Context): String = key ?: synchronized(this) {
        key ?: run {
            val f = java.io.File(context.applicationContext.noBackupFilesDir, "notice_key")
            val kept = runCatching { f.readText().trim() }.getOrNull()?.takeIf { it.matches(KEY_FORM) }
            kept ?: java.security.SecureRandom().let { r -> ByteArray(16).also(r::nextBytes) }.joinToString("") { "%02x".format(it) }
                .also { made -> runCatching { f.parentFile?.mkdirs(); f.writeText(made) } }
        }.also { key = it }
    }

    private val KEY_FORM = Regex("[0-9a-f]{32}")

    /** Opens the app on [target], or brings it forward there; the screen underneath is not stacked again. */
    fun intent(context: Context, target: Target, calendar: String? = null, payload: String? = null): Intent = Intent(context, MainActivity::class.java)
        .putExtra(EXTRA, target.name)
        .putExtra(KEY_EXTRA, key(context))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .apply {
            if (payload != null) {
                putExtra(PAYLOAD_EXTRA, payload)
                // [INV-08] Each alarm's notice keeps its own intent too.
                data = android.net.Uri.parse("bamnat-notice://${target.name.lowercase()}/${calendar ?: "main"}/$payload")
            }
            if (calendar == null) return@apply
            putExtra(CALENDAR_EXTRA, calendar)
            // Each calendar's notice keeps its own intent rather than taking over another's.
            if (payload == null) data = android.net.Uri.parse("bamnat-notice://calendar/$calendar")
        }

    /** Reads a target off an intent; true when there was one. */
    fun read(context: Context, intent: Intent?): Boolean {
        // Reopened from Recents, Android hands back the intent the app was first opened with: it was followed then.
        if (intent == null || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return false
        // 2.0.0 [RVL-08] An intent that does not carry this app's own value was not made by it: the app opens as from its icon.
        val own = runCatching { intent.getStringExtra(KEY_EXTRA) }.getOrNull()
        intent.removeExtra(KEY_EXTRA)
        if (!follows(own, key(context))) {
            intent.removeExtra(EXTRA)
            intent.removeExtra(CALENDAR_EXTRA)
            intent.removeExtra(PAYLOAD_EXTRA)
            return false
        }
        val target = intent.getStringExtra(EXTRA)?.let { n -> Target.entries.firstOrNull { it.name == n } } ?: return false
        intent.removeExtra(EXTRA)
        _calendar.value = intent.getStringExtra(CALENDAR_EXTRA)
        intent.removeExtra(CALENDAR_EXTRA)
        _payload.value = intent.getStringExtra(PAYLOAD_EXTRA)
        intent.removeExtra(PAYLOAD_EXTRA)
        _target.value = target
        return true
    }

    /**
     * Opens [target] from inside the app, from a screen that has no way there of its own; [calendar]
     * names one of the user's calendars when it is about one [UX-03].
     */
    fun open(target: Target, calendar: String? = null, payload: String? = null) {
        _calendar.value = calendar
        _payload.value = payload
        _target.value = target
    }

    /** [J133-87] A [payload] for SHARE: the calendar on screen, not 내 달력 (a notice names its own calendar or means 내 달력). */
    const val ON_SCREEN = "on_screen"

    /** 2.0.0 [RVL-08] An intent's value against this phone's, compared in constant time. */
    internal fun follows(carried: String?, own: String): Boolean =
        carried != null && java.security.MessageDigest.isEqual(carried.toByteArray(), own.toByteArray())

    /**
     * Done with [target]. 2.0.0 [RVP-26] (검토 UIX-48) Its [payload] and [calendar] go with it: they stayed, and the next screen to read
     * them (같이 보는 근무 opened from the 나 메뉴) started on a widget's old date. Read them before this, or [hand] them to the screen.
     */
    fun consume(target: Target) {
        if (_target.compareAndSet(target, null)) {
            _payload.value = null
            _calendar.value = null
        }
    }

    private val handed = java.util.concurrent.ConcurrentHashMap<Target, String>()

    /** 2.0.0 [RVP-26] A value for the screen [target] opens, taken once by that screen ([takeHanded]) however it is drawn. */
    fun hand(target: Target, value: String?) {
        if (value == null) handed.remove(target) else handed[target] = value
    }

    fun takeHanded(target: Target): String? = handed.remove(target)
}
