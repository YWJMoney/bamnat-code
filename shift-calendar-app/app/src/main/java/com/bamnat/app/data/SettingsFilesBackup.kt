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
            if (o.length() > 0) prefs.put(part.name, o)
        }
        val marks = JSONObject()
        for (id in listOf(PersonalCalendar.MAIN) + calendarIds().filter { it != PersonalCalendar.MAIN && it != PersonalCalendar.EXAMPLE }) {
            val f = marksFile(id)
            if (f.exists()) runCatching { marks.put(id, JSONObject(f.readText())) }
        }
        return JSONObject().put("prefs", prefs).put("recurringMarks", marks)
    }

    /** Puts back what [export] wrote; a part the file does not have keeps what this phone has. */
    fun restore(o: JSONObject?) {
        if (o == null) return
        o.optJSONObject("prefs")?.let { prefs ->
            for (part in PARTS) {
                val saved = prefs.optJSONObject(part.name) ?: continue
                val edit = context.getSharedPreferences(part.name, Context.MODE_PRIVATE).edit()
                for (k in saved.keys()) {
                    if (part.keys != null && k !in part.keys) continue
                    decode(edit, k, saved.optJSONObject(k) ?: continue)
                }
                edit.commit()
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

        /** What travels, file by file. Keys left out of a listed file stay on the phone. */
        internal val PARTS = listOf(
            Part("work_type_prefs"),
            Part("company", keys = setOf("applied", "sent", "pending_pay", "draft", "plaza_entry_closed", "invite_closed")),
            Part("plaza_rate_hint", keys = setOf("own")),
            Part("calendar_view"),
            Part("auto_record"),
            Part("share_group"),
            Part("calendar_import", keys = setOf("auto")),
        )

        /**
         * [BAK-01] [AppSettings] fields that stay on this phone, with why. Every other field goes in the backup's
         * "settings"; a test fails when a new field is in neither.
         */
        val SETTINGS_ON_PHONE = mapOf(
            "defaultAlarmSound" to "알람 소리 파일은 이 휴대폰에 있는 것",
            "healthSleep" to "헬스 커넥트 권한은 새 휴대폰에서 다시 받아요",
            "previewTier" to "개발용 등급 미리 보기",
            "swapCoversAnswered" to "옛 대근 고치기 질문, 복원한 기록에는 다시 물어요",
            "reviewDue" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "reviewAsked" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "reviewAskedDay" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "payReviewDue" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "payReviewAsked" to "평가 부탁은 이 휴대폰에서 쓴 만큼",
            "monthRecapSeen" to "이 휴대폰에서 본 달 돌아보기",
            "alarmSuggestMemory" to "알람 제안에 이 휴대폰에서 답한 것",
        )

        /** [BAK-01] Preferences files the backup already carries in parts of its own (설정, 기능 끄기, 조, 듀티 약어, 프로, 공유 링크). */
        val ELSEWHERE = setOf("settings", "guide", "team_rosters", "duty_codes", "pro", "share_link")

        /**
         * [BAK-01] Kept on this phone on purpose, with why (Z-D3). "file:key" is one key of a file whose other keys
         * travel. The test holds every preferences file in the app to [PARTS], [ELSEWHERE] or this list.
         */
        val DEVICE_ONLY = mapOf(
            "company:device" to "이 휴대폰의 설치 번호(문의 기록의 열쇠)",
            "inquiry" to "문의 기록은 이 휴대폰의 설치 번호로 열려요",
            "commute" to "출퇴근 장소의 지도 위 좌표는 다니는 파일에 넣지 않아요",
            "auto_backup" to "자동 백업 폴더와 열쇠(비밀값)",
            "device" to "동의 기록은 새 휴대폰에서 다시 물어요",
            "usage_stats" to "사용 통계는 이 휴대폰에서 동의한 만큼만",
            "alarm_state" to "알람 예약 상태는 설정에서 다시 만들어요",
            "boot_alarms" to "알람 예약 상태는 설정에서 다시 만들어요",
            "alarms_off" to "‘오늘 남은 알람 모두 끄기’는 이 휴대폰의 오늘 하루 것",
            "wake_check" to "깼는지 확인 중인 알람, 이 휴대폰에서 울린 것",
            "quiet_hold" to "자는 동안 미뤄 둔 알림, 이 휴대폰의 것",
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
        )
    }
}
