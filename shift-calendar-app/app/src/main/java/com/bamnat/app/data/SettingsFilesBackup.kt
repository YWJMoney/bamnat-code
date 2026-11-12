package com.bamnat.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * [BAK-01] 1.25 Z (U8): "모든 설정과 모든 기록". The settings kept outside [AppSettings] and the database, which the backup
 * used to leave behind (조사 `research/1.25-Z-조사.md` 7장): each calendar's 연장 상한·세금 방식, the company or plaza rules in
 * use and the reports sent, the user's own job and region for 광장 시세, the months worked on red days on purpose,
 * 결제 알림으로 적기's tuning, 우리 조 모아 보기, 휴대폰 캘린더 자동 가져오기, and how far each repeating money line was
 * written (without it, lines the user deleted came back after a restore).
 *
 * What stays on this phone, on purpose ([DEVICE_ONLY]): the install id (it opens this phone's 문의), the commute
 * place on the map, the automatic backup's folder and key, consent records, alarm scheduling, other people's
 * calendars received, the phone calendars chosen (their ids are this phone's own) and what 밤낮 did here.
 */
class SettingsFilesBackup(
    private val context: Context,
    private val calendarIds: () -> List<String> = { emptyList() },
    /** Stores that keep these files in memory read them again, so a restore shows without restarting the app. */
    private val onRestored: () -> Unit = {},
) {

    /** One preferences file: all of its keys, or only [keys]. */
    internal class Part(val name: String, val keys: Set<String>? = null)

    fun export(): JSONObject {
        val prefs = JSONObject()
        for (part in PARTS) {
            val p = context.getSharedPreferences(part.name, Context.MODE_PRIVATE)
            val o = JSONObject()
            for ((k, v) in p.all) {
                if (part.keys != null && k !in part.keys) continue
                encode(v)?.let { o.put(k, it) }
            }
            // [J133B-05] 1.33 빌드 9 확인 ("복원했는데 회사 적용이 그대로예요"): an empty part is written too, so a restore knows the
            // backup had none of it (no company applied) and takes away what came after, instead of keeping it.
            prefs.put(part.name, o)
        }
        val marks = JSONObject()
        val added = calendarIds().filter { it != PersonalCalendar.MAIN && it != PersonalCalendar.EXAMPLE }
        for (id in listOf(PersonalCalendar.MAIN) + added) {
            val f = marksFile(id)
            if (f.exists()) runCatching { marks.put(id, JSONObject(f.readText())) }
        }
        // [CALI-07] 1.30 BB: each added calendar's company connection, the same keys as the main calendar's parts.
        val companies = JSONObject()
        for (id in added) {
            val o = JSONObject()
            for (part in CALENDAR_PARTS) {
                val file = java.io.File(CalendarStorage.filesOf(context, id), part.value)
                if (!file.exists()) continue
                val p = com.bamnat.app.company.CompanyScopeStorage.prefs(context, id, part.value)
                val keys = PARTS.first { it.name == part.key }.keys
                val values = JSONObject()
                for ((k, v) in p.all) if (keys == null || k in keys) encode(v)?.let { values.put(k, it) }
                o.put(part.key, values)
            }
            if (o.length() > 0) companies.put(id, o)
        }
        return JSONObject().put("prefs", prefs).put("recurringMarks", marks).apply { if (companies.length() > 0) put("calendarCompany", companies) }
    }

    /** Puts back what [export] wrote; a part the file does not have keeps what this phone has. */
    fun restore(o: JSONObject?) {
        if (o == null) return
        o.optJSONObject("prefs")?.let { prefs ->
            for (part in PARTS) {
                val saved = prefs.optJSONObject(part.name) ?: continue
                val file = context.getSharedPreferences(part.name, Context.MODE_PRIVATE)
                val edit = file.edit()
                // [J133B-05] What the part had then, and nothing it did not: a key of the part written after the backup goes.
                for (k in file.all.keys) if ((part.keys == null || k in part.keys) && !saved.has(k)) edit.remove(k)
                for (k in saved.keys()) {
                    if (part.keys != null && k !in part.keys) continue
                    decode(edit, k, saved.optJSONObject(k) ?: continue)
                }
                edit.commit()
            }
        }
        // [CALI-07] Once the calendars are back: each one's company connection into its own folder. A part the file does not
        // have keeps what this phone has, as for the main calendar.
        o.optJSONObject("calendarCompany")?.let { companies ->
            for (id in companies.keys()) {
                if (id == PersonalCalendar.MAIN || id == PersonalCalendar.EXAMPLE || id !in calendarIds()) continue
                val parts = companies.optJSONObject(id) ?: continue
                for (part in CALENDAR_PARTS) {
                    val saved = parts.optJSONObject(part.key) ?: continue
                    val keys = PARTS.first { it.name == part.key }.keys
                    val file = com.bamnat.app.company.CompanyScopeStorage.prefs(context, id, part.value)
                    val edit = file.edit()
                    for (k in file.all.keys) if ((keys == null || k in keys) && !saved.has(k)) edit.remove(k)
                    for (k in saved.keys()) {
                        if (keys != null && k !in keys) continue
                        decode(edit, k, saved.optJSONObject(k) ?: continue)
                    }
                    edit.commit()
                }
            }
        }
        o.optJSONObject("recurringMarks")?.let { marks ->
            for (id in marks.keys()) {
                if (id == PersonalCalendar.EXAMPLE) continue
                val f = marksFile(id)
                runCatching {
                    f.parentFile?.mkdirs()
                    f.writeText(marks.getJSONObject(id).toString())
                }
            }
        }
        onRestored()
    }

    private fun marksFile(id: String): File =
        if (id == PersonalCalendar.MAIN) File(context.filesDir, MARKS) else File(CalendarStorage.filesOf(context, id), MARKS)

    private fun encode(v: Any?): JSONObject? = when (v) {
        is String -> JSONObject().put("s", v)
        is Boolean -> JSONObject().put("b", v)
        is Int -> JSONObject().put("i", v)
        is Long -> JSONObject().put("l", v)
        is Float -> JSONObject().put("f", v.toDouble())
        is Set<*> -> JSONObject().put("ss", JSONArray(v.filterIsInstance<String>().sorted()))
        else -> null
    }

    private fun decode(edit: android.content.SharedPreferences.Editor, k: String, v: JSONObject) {
        when {
            v.has("s") -> edit.putString(k, v.getString("s"))
            v.has("b") -> edit.putBoolean(k, v.getBoolean("b"))
            v.has("i") -> edit.putInt(k, v.getInt("i"))
            v.has("l") -> edit.putLong(k, v.getLong("l"))
            v.has("f") -> edit.putFloat(k, v.getDouble("f").toFloat())
            v.has("ss") -> edit.putStringSet(k, v.getJSONArray("ss").let { a -> (0 until a.length()).map(a::getString).toSet() })
        }
    }

    companion object {
        private const val MARKS = "recurring_marks.json"

        /**
         * [CALI-07] An added calendar's company connection: the main calendar's part it follows ([PARTS], same keys, same
         * [DEVICE_ONLY]) and the file in the calendar's folder ([com.bamnat.app.company.CompanyScopeStorage]).
         */
        internal val CALENDAR_PARTS = mapOf(
            "company" to com.bamnat.app.company.CompanyScopeStorage.COMPANY,
            "pay_way" to com.bamnat.app.company.CompanyScopeStorage.PAY_WAY,
        )

        /** What travels, file by file. Keys left out of a listed file stay on the phone. */
        internal val PARTS = listOf(
            Part("work_type_prefs"),
            // [RCP-11] 1.34 메모 틀.
            Part("memo_templates"),
            // [RCP-38] 나눔 판 쓰기 and the rules agreed to.
            Part("recipe"),
            Part("company", keys = setOf("applied", "sent", "pending_pay", "draft", "plaza_entry_closed", "invite_closed")),
            Part("plaza_rate_hint", keys = setOf("own")),
            Part("calendar_view"),
            Part("auto_record"),
            Part("share_group"),
            Part("calendar_import", keys = setOf("auto")),
            // [READ-19] 1.27 AJ: 사진 읽기에서 고친 명세서 줄 이름·듀티표 글자.
            Part("read_fixes"),
            // [PAYF-04] 1.29 AP: 쓰는 회사 급여 방식, like the company setup in use ("company:applied").
            Part("pay_way"),
            // 1.32 [WTYP-11] 희망 오프: 고른 날과 신청 마감일.
            Part("wish_off"),
            // 1.36 [VIL-13] 마을 › 간직한 글: posts kept to read again, only on the phone (the server keeps no list).
            Part("village_saved"),
            // 1.39 [PLZC-10] 광장 근무 태그에 무엇을 보일지(근무형태·업종·개월·근무 중): 새 휴대폰에서도 같게.
            Part("plaza_tag"),
            // [FREE-10] 1.35 테마 옷장: 입었던 테마, 내 테마, the look kept aside while a theme is on. The 3일 전 notice said and
            // the 프로 mark are this phone's.
            // [R136-03] 1.36: the day each calendar started on this phone (깐 날 표시), so a restored phone says the same day.
            Part("calendar_start"),
            Part("leave_hire"), // [LEAVE-14]
            Part("pro_trials"), // [MON-13]
            Part("fx_values"), // 1.39 [FX-03] 값 칸의 숫자
            Part("fx_taste"), // 1.39 [FX-02] 작품마다 하루 써 보기를 쓴 것 (새로 깔아도 다시 주지 않게)
            Part("work_plans"), // 1.38 팀 요금제: 무료로 쓴 첫 확정 달 (팀을 지워도, 새로 깔아도 남게)
            Part("folded_types"), // 1.36 PC 재검토: 안 쓰는 근무 종류
            Part("theme_wardrobe", keys = setOf("lease", "mine", "worn_look", "parts", "skip_ver", "prev_ver", "overlays", "keep_overlays", "folders", "placed")),
        )

        /**
         * [BAK-01] [AppSettings] fields that stay on this phone, with why. Every other field goes in the backup's
         * "settings"; a test fails when a new field is in neither.
         */
        val SETTINGS_ON_PHONE = mapOf(
            "viewAsked" to "처음 달력의 보기 묻기(1.39 CALX-07), 복원한 백업은 그 보기 그대로라 다시 묻지 않아요",
            "hideAmounts" to "금액 가리기는 이 휴대폰을 남에게 보일 때 쓰는 것",
            "defaultAlarmSound" to "알람 소리 파일은 이 휴대폰에 있는 것",
            "healthSleep" to "헬스 커넥트 권한은 새 휴대폰에서 다시 받아요",
            "sleepAuto" to "잠 자동으로 잡기(활동 인식 권한)는 새 휴대폰에서 다시 켜요",
            "sleepDnd" to "방해 금지 접근은 새 휴대폰에서 다시 허용해요",
            "healthWrite" to "헬스 커넥트 쓰기 허용은 새 휴대폰에서 다시 물어요",
            "previewTier" to "개발용 등급 미리 보기",
            "swapCoversAnswered" to "옛 대근 고치기 질문, 복원한 기록에는 다시 물어요",
            "reviewDue" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "reviewAsked" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "reviewAskedDay" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "payReviewDue" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "payReviewAsked" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "monthRecapSeen" to "이 휴대폰에서 본 달 돌아보기",
            "yearRecapSeen" to "이 휴대폰에서 본 연말 결산",
            "alarmSuggestMemory" to "알람 제안에 이 휴대폰에서 답한 것",
        )

        /** [BAK-01] Preferences files the backup already carries in parts of its own (설정, 기능 끄기, 조, 듀티 약어, 프로, 공유 링크). */
        val ELSEWHERE = setOf("settings", "guide", "team_rosters", "duty_codes", "pro", "share_link")

        /**
         * [BAK-01] Kept on this phone on purpose, with why (Z-D3). "file:key" is one key of a file whose other keys
         * travel. The test holds every preferences file in the app to [PARTS], [ELSEWHERE] or this list.
         */
        val DEVICE_ONLY = mapOf(
            // 2.0.0 [RVI-17] 낮잠 깨우기: 이 휴대폰에 맞춘 알람 하나의 날·시각이라, 새 휴대폰에서 다시 울리지 않게 옮기지 않아요.
            "nap_wake" to "낮잠 깨우기 알람의 날과 시각, 이 휴대폰에 맞춘 알람의 것",
            // 2.0.0 [RVI-22] 꺼져 있던 사이 지나간 메모 알림을 어디까지 알렸는지: 이 휴대폰이 꺼져 있던 때의 일이라 옮기지 않아요.
            "memo_schedule" to "꺼져 있던 사이 지나간 메모 알림을 어디까지 모아 알렸는지, 이 휴대폰의 것",
            // 2.0.0 [RVP-08] 설명을 몇 번 봤는지: 새 휴대폰에서는 설명을 처음처럼 한 번 더 다 보여 줘요.
            "explain_seen" to "설명(ⓘ)을 이 휴대폰에서 본 횟수, 새 휴대폰은 처음처럼 다 보여 줘요",
            // 1.40 [DISC-01] 안 써 본 기능 카드를 언제 무엇을 보였는지: 이 휴대폰의 일이라, 새 휴대폰은 처음처럼 쉬었다가 시작해요.
            "discover" to "‘안 써 본 기능 알려 주기’ 카드를 보인 날과 답, 다음 카드까지의 간격, 이 휴대폰의 것",
            // 1.39 [LOOP-13] Play 인앱 업데이트를 물어본 판: 이 휴대폰에 깔린 판의 일이라 옮기지 않아요.
            "in_app_update" to "Play 인앱 업데이트를 이 판에서 이미 물어봤는지, 이 휴대폰에 깔린 판의 것",
            "plaza_tag" to "광장 근무 태그 공개 범위(근무 형태·업종·밤낮 쓴 기간·지금 근무 중)는 이 휴대폰에만, 새 휴대폰에서 한 번 다시 골라요(PLZC-10)",
            "share_forget" to "모두 지우기 때 아직 못 지운 공유 링크(종류:번호), 이 휴대폰이 인터넷이 되면 마저 지움. 지운 뒤 남은 일이라 되살린 폰에 옮기지 않아요",
            // 1.38 [P3-X-08] 신고·지급 '했어요' 표시: 알림 목록이라 복원 뒤엔 지난 일이 길어야 일주일 다시 보여요.
            "boss_filed" to "다가올 신고·지급에서 '했어요'를 누른 것, 이 휴대폰의 것",
            // 1.38 [P3-L-15] 병동 전체 표 링크와 고치는 열쇠: 복원 뒤엔 리더가 한 번 눌러 새로 만들어요(옛 주소는 그 달 끝 60일 뒤 저절로 지워져요).
            "team_board_links" to "팀 전체 표 링크와 그 고치는 열쇠, 이 휴대폰의 것",
            // 1.38 [STM-06] 밤낮 재료: 광고로 연 묶음(30일)과 재료 표시, 이 휴대폰에서 본 광고의 것.
            "studio_materials" to "광고로 30일 연 재료 묶음과 재료 표시, 이 휴대폰의 것",
            // 1.38 [EASE-29] 홈 화면 위젯 목록에 내 근무 그림을 올린 날: 이 휴대폰의 런처 일이라 옮기지 않아요.
            "widget_previews" to "홈 화면 위젯 목록에 내 근무 그림을 올린 날(하루 한 번), 이 휴대폰의 것",
            // 1.39 [MKF-03] 장터 작게 보기(3열·펼침 4열): 이 휴대폰 화면 폭에 맞춘 것이라 옮기지 않아요.
            "market_view" to "장터 카드를 작게(3열) 볼지, 이 휴대폰 화면에 맞춘 것",
            // [CNV-13] 1.37 펼쳐 보기: which sliding rows were opened, a matter of this screen's width.
            "fold_rows" to "꾸미기 줄을 펼쳐 둘지 접어 둘지(줄마다), 이 휴대폰 화면에 맞춘 것",
            // [MAT-05] 1.36 글꼴 고르기의 최근 쓴 글꼴 다섯, 이 휴대폰의 것.
            "font_recent" to "글꼴 고르기에서 최근 고른 글꼴(다섯 개), 이 휴대폰의 것",
            "font_waiting" to "테마가 쓰는데 아직 못 받은 나눈 글꼴(운영자 확인 전 등), 앱을 열 때 다시 받아 보려고 2주 동안 적어 둠",
            "font_standin" to "아직 못 받은 나눈 글꼴 대신 보일 비슷한 앱 글꼴(보낸 분 휴대폰이 고른 것), 이 휴대폰의 것",
            // PC 재검토 N3 1.36: the look from before a studio wear, for a day's '입히기 전 모양으로'; a restored phone has no such moment.
            "studio_locks" to "스튜디오에서 잠근 화면(한 색·섞기가 건드리지 않을 탭), 이 휴대폰의 것",
            "look_undo" to "스튜디오에서 입히기 전 모양(하루 동안만 되돌리기)",
            "backup_place" to "마지막 백업 파일이 있던 폴더 주소, 이 휴대폰의 저장소 것",
            "logic_rules" to "규칙을 끈 것·지난 7일 실행 횟수·마지막 되돌리기, 이 휴대폰에서 실행한 만큼(규칙 자체는 설정 logic으로 백업)",
            // [LAY-09] 1.36 곳 줄 자주 쓰는 6곳: how this phone's hands use the studio.
            "studio_ui" to "스튜디오에서 자주 연 곳(곳 줄 순서), 이 휴대폰의 것",
            // PC 확인 11825213 사용성 제안: the 메모 tab opens on the 칸 last looked at.
            "memo_list" to "메모 탭에서 마지막으로 본 칸(달력 메모·노트 등), 이 휴대폰의 것",
            // 1.39 [TASK-44] 반복 메모의 한 번만 미루기(메모 번호 → 시각): 울리면 지워지는 잠깐의 일이라 옮기지 않아요.
            "memo_snoozes" to "반복 메모를 한 번만 미룬 시각, 울리거나 끝내면 지워지는 이 휴대폰의 것",
            "memo_notices" to "메모 일정 알림 한 칸에 모아 둔 남은 일정, 알림이 사라지면 지워지는 이 휴대폰의 것",
            "today_notice_fold" to "규칙의 말을 잠금 화면 오늘 근무 알림에 합친 그날 한 줄, 자정에 지워지는 이 휴대폰의 것",
            // 1.36 [GRW-03][GRW-09] 스튜디오 부탁 풀림을 알린 문의, 작품을 올린 적 있는지(보상 이용권 묻기): this phone's own.
            "studio_solved" to "스튜디오 부탁이 풀렸다고 이 휴대폰에서 이미 알린 문의",
            "maker" to "이 휴대폰에서 작품을 올린 적 있는지(만든 분 보상 이용권을 물을지)",
            // 1.36 [PUSH-01] the FCM token last given to the server: a new phone gets its own.
            "push_phone" to "문의 답 바로 알림에 서버로 보낸 이 휴대폰의 알림 토큰",
            // 1.36 [VIL-04][VIL-07] 마을 화면 기억: the part last seen and 오늘의 하나 '오늘은 그만', this phone's own.
            "village_ui" to "마을에서 마지막에 본 칸과 '오늘의 하나'를 그날 접었는지, 이 휴대폰에서 본 만큼",
            "company:device" to "이 휴대폰의 설치 번호(문의 기록의 열쇠)",
            // [PLAZA-04] 1.25 X (결정 X-D5): the plaza invitation's waits are this phone's, like share_way.
            "company:invite_due" to "광장 올리기 권유를 다시 보일 날, 이 휴대폰에서 본 만큼",
            "company:invite_laters" to "광장 올리기 권유에 '다음에'를 누른 횟수, 이 휴대폰의 것",
            "company:invite_never" to "광장 올리기 권유 '그만 볼게요', 이 휴대폰의 것",
            "company:hand_made" to "이 휴대폰에서 첫 설정을 손으로 끝냈는지(권유를 띄울 때만 써요)",
            // [PLAZA-07] 1.26 AF (결정 D79, AF-D9): sharing by itself is asked again on a new phone, like the 동의 기록.
            "company:auto_share" to "근무 순서·계산 방식을 저절로 나눌지, 이 휴대폰에서 답한 것(새 휴대폰에서 다시 물어요)",
            "company:auto_dirty" to "저절로 나눌 바뀐 것이 남았는지, 이 휴대폰의 보내기 대기",
            "company:auto_sent_at" to "저절로 나눈 마지막 때, 하루 한 번을 세는 이 휴대폰의 것",
            "company:member" to "참여한 회사는 이 휴대폰의 설치 번호로 서버에 있어요",
            // [PLAZA-34] 1.27 AI (결정 AI-D9): what was shared last, held against a change; asked again on a new phone.
            "company:auto_base" to "광장에 마지막으로 나누거나 물은 설정(금액 없음), 바뀐 것을 견주는 이 휴대폰의 것",
            "inquiry" to "문의 기록은 이 휴대폰의 설치 번호로 열려요",
            "commute" to "출퇴근 장소의 지도 위 좌표는 다니는 파일에 넣지 않아요",
            "auto_backup" to "자동 백업 폴더와 열쇠(비밀값)",
            "device" to "동의 기록은 새 휴대폰에서 다시 물어요",
            "usage_stats" to "사용 통계는 이 휴대폰에서 동의한 만큼만",
            "alarm_state" to "알람 예약 상태는 설정에서 다시 만들어요",
            "alarm_guard" to "알람 지킴이가 오늘 알린 문제(하루 한 번 알리기 위한 표시)일 뿐이에요", // [ALRM-27]
            "boot_alarms" to "알람 예약 상태는 설정에서 다시 만들어요",
            "alarms_off" to "‘오늘 남은 알람 모두 끄기’는 이 휴대폰의 오늘 하루 것",
            // [ALRM-21] 1.35.
            "alarm_soon" to "‘이번 알람 끄기’는 이 휴대폰에 곧 울릴 알람 하나의 것",
            "wake_check" to "깼는지 확인 중인 알람, 이 휴대폰에서 울린 것",
            "quiet_hold" to "자는 동안 미뤄 둔 알림, 이 휴대폰의 것",
            // 1.38 [SLEEP-01] 휴대폰이 본 잠 조각·끈 알람·믿음 횟수는 이 휴대폰 센서의 것, 넣은 잠은 수면 기록으로 가요.
            "sleep_dnd" to "밤낮이 켠 방해 금지 표시는 이 휴대폰의 것",
            "sleep_auto" to "자동으로 잡은 잠 조각은 이 휴대폰 센서의 것, 넣은 잠은 수면 기록으로 가요",
            "sleep_record" to "지난밤 측정 요약(배터리 사용량)은 이 휴대폰의 것, 소리 사건은 수면 기록으로 가요",
            "sleep_challenge" to "회복 도전 시작일은 이 휴대폰의 것, 새 휴대폰에서 다시 시작해요",
            "sleep_tired" to "오늘 팀에 피곤해요를 알렸는지는 하루짜리 표시, 새 휴대폰에서는 다시 알릴 수 있어요",
            "smart_wake" to "깨우는 구간에서 일찍 울린 알람 표시는 이 휴대폰에서 울린 것",
            "photo_mission" to "증명 사진 지문은 이 휴대폰의 사진",
            "calendar_import:chosen" to "고른 휴대폰 캘린더 번호는 이 휴대폰의 것",
            "launch_config" to "회사 서버가 알려 주는 출시 설정, 새 휴대폰이 다시 받아요",
            "ask" to "질문 카드가 오늘 몇 번 물었는지(답한 사실은 프로필로 가요)",
            "browse" to "예시 달력 둘러보기, 새 휴대폰은 예시 없이 시작",
            "app_notices" to "이 휴대폰에서 닫은 안내",
            "hints" to "이 휴대폰에서 본 도움말 한 줄",
            "clip_hint" to "이 휴대폰에서 본 붙여넣기 안내",
            "share_way" to "이 휴대폰에서 본 나누기 권유",
            "plaza_news" to "이 휴대폰이 받은 광장 소식",
            "app_state" to "앱을 마지막으로 연 때(화면 되살리기)",
            // [INVITE-02] 1.26 AE: tied to this install on the company server; the months move with the recovery code.
            "invite" to "친구 초대: 이 설치에 묶인 초대 코드와 받은 초대(이용권은 복구 코드로 옮겨요)",
            // AG 1.27 [INVITE-11][INVITE-12]: when this phone was asked to invite or to tell the developer (끄기는 기능 스위치로 백업).
            "grow" to "친구 초대 카드·‘함께 만들어요’ 카드를 이 휴대폰에서 본 날과 닫은 날, 되돌리기 수",
            // [FLAG-15] 1.28 AL (AL-D7): an admin's previews are this phone's only.
            "flag_preview" to "1.31까지의 관리자 미리 보기(1.32부터 비워 둬요, 관리자 앱이 정해요)",
            // A 1.32 [FIX132-01]: which way this phone's payslip photos read well last.
            "photo_turn" to "명세서 사진을 읽은 방향, 이 휴대폰의 사진기 것",
            "month_story" to "지난달 돈 이야기를 닫은 달, 이 휴대폰에서 본 만큼 (LEDH-16)",
            "plaza_offer" to "명세서로 맞춘 뒤 광장 올리기를 물어본 때, 이 휴대폰에서 본 만큼",
            // C 1.32 [STAB-03][STAB-04]: this phone's stops, counted, and whether it opened safely.
            "crash_notice" to "같은 곳에서 멈춘 횟수와 마지막으로 알린 날, 이 휴대폰에서만",
            "start_guard" to "연달아 멈춘 시작 수(안전 모드), 이 휴대폰에서만",
            // [PERF-15][PERF-16] 1.28 AO: how long this phone took to open; 보내기 대기는 사용 통계와 같이 이 휴대폰의 것.
            "startup_stats" to "앱을 여는 데 걸린 시간, 이 휴대폰에서 잰 것",
            // AQ 1.29 [PHOTO-09]: '사진이 달라요' counts waiting to be sent (60 days), this phone's only.
            "photo_wrong" to "‘사진이 달라요’를 누른 횟수 중 아직 보내지 않은 것(60일), 이 휴대폰에서만",
            // 1.31 [STAT-08][GUIDE-20]: lookup times waiting to be sent, and whether the first week's 시작 단계 card was put away.
            "lookup_stats" to "조회에 걸린 시간 중 아직 보내지 않은 것(처리방침 3.5 시행 뒤, 동의한 경우), 이 휴대폰에서만",
            "setup_taps" to "1.39 [SETUP-20] 첫 설정을 마칠 때까지 누른 횟수(새로 깐 이 휴대폰의 한 번뿐, 사용 통계로 한 번 보내고 지움), 다른 휴대폰에 옮기면 그 휴대폰의 첫 설정이 아니에요",
            "start_steps" to "첫 주 ‘시작 3단계’ 카드를 그만 보기로 했는지, 이 휴대폰에서만",
            "share_ask_gate" to "나누기 부탁을 거절한 뒤 다시 묻지 않는 날짜, 이 휴대폰에서만 (J133-17)",
            "first_pay_asked" to "광장 판마다 '첫 급여 방식이 되어 주세요'를 이미 보여 준 기록, 이 휴대폰에서만 (J133-94)",
            "inquiry_reply" to "문의 답을 바로 알린 개수, 같은 답을 두 번 알리지 않으려고 (J133-24)",
            // 1.32 [PROX-02]: when this phone was offered a free day on a money day (끄기는 기능 스위치로 백업).
            "pro_moments" to "프로 제안 카드를 이 휴대폰에서 본 날·닫은 날과 앱을 연 날",
            // 1.32 [STAB-08]: a backup saved on another phone does not keep this one's records.
            "recipe_remix_seen" to "내 판마다 고쳐 만든 판을 이 휴대폰에서 본 수, 새로 생긴 것만 점으로 알리려고 (LIN-04)",
            "backup_nudge" to "이 휴대폰에서 마지막으로 백업 파일을 저장한 날과 ‘나중에’를 누른 날",
            // 1.38 갈래8 [LEDH-24][LEDH-29][LEDH-14]: what this phone already showed or took in (the switches are in settings).
            "bamnat_money_first_fill" to "가계부 ‘첫날 채우기’ 카드를 넘긴 것, 이 휴대폰에서 본 만큼",
            "bamnat_shift_preview" to "근무 전 한 줄 예고를 마지막으로 알린 날, 같은 날 두 번 알리지 않으려고",
            "bamnat_watch_money" to "워치에서 적은 돈 최근 50건의 번호, 같은 것을 두 번 적지 않으려고",
        )
    }
}
