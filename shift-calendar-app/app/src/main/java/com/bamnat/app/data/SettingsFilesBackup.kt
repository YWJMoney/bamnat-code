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
class SettingsFilesBackup(private val context: Context, private val calendarIds: () -> List<String> = { emptyList() }) {

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

        /** [BAK-01] Kept on this phone on purpose, with why (Z-D3); the test holds every preferences file to one list or the other. */
        val DEVICE_ONLY = mapOf(
            "company:device" to "이 휴대폰의 설치 번호(문의 기록의 열쇠)",
            "commute" to "출퇴근 장소의 지도 위 좌표는 다니는 파일에 넣지 않아요",
            "auto_backup" to "자동 백업 폴더와 열쇠(비밀값)",
            "device" to "동의 기록은 새 휴대폰에서 다시 물어요",
            "alarm_state" to "알람 예약 상태는 설정에서 다시 만들어요",
            "boot_alarms" to "알람 예약 상태는 설정에서 다시 만들어요",
            "photo_mission" to "증명 사진 지문은 이 휴대폰의 사진",
            "calendar_import:chosen" to "고른 휴대폰 캘린더 번호는 이 휴대폰의 것",
        )
    }
}
