package com.bamnat.app.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import com.bamnat.app.BuildConfig
import com.bamnat.app.NoticeLinks
import com.bamnat.app.R
import com.bamnat.app.alarm.Notifications
import java.io.File
import com.bamnat.core.time.Today

/**
 * [MEAS-08] 328: when the app stops by itself, a short note of where, kept on the phone.
 *
 * The note holds the app's version, the Android version, the phone's maker and model, the thread and the
 * exception classes with their code lines, never an exception's message (it can quote what a person typed) and
 * nothing from the calendar. It stays in files/diag (left out of every backup) and leaves the phone only when the
 * person reads it in 알람 점검 and sends it with "점검 내용과 함께 문의하기".
 */
object CrashLog {
    private const val DIR = "diag"
    private const val PREFIX = "crash-"
    const val KEEP = 5
    const val FRAMES = 30
    // C 1.32 [STAB-03] Its own id: 7006 was also 알람이 늦게 왔어요 (Notifications.ALARM_LATE_ID), and one took the other's place.
    private const val NOTICE_ID = 7008
    private const val DAYS_30 = 30L * 24 * 60 * 60 * 1000
    private const val DAY = 24L * 60 * 60 * 1000
    private const val NOTICE_PREFS = "crash_notice"

    @Volatile private var installed = false

    /** Called first thing in Application.onCreate; the handler before it (Android's) still ends the process. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val before = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread.name, error, Today.millis()) }
            runCatching { noticeOnce(app, signature(error), Today.millis()) }
            before?.uncaughtException(thread, error)
        }
    }

    fun dir(context: Context) = File(context.filesDir, DIR)

    /** Writes one note and keeps the newest [KEEP]. */
    fun write(context: Context, thread: String, error: Throwable, atMillis: Long): File {
        val dir = dir(context).apply { mkdirs() }
        val file = File(dir, "$PREFIX$atMillis.txt")
        file.writeText(describe(thread, error))
        notes(context).drop(KEEP).forEach { it.delete() }
        return file
    }

    fun describe(thread: String, error: Throwable): String = buildString {
        appendLine("app ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("android ${Build.VERSION.SDK_INT} · ${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("thread ${Telemetry.cleanDetail(thread)}")
        var cause: Throwable? = error
        val seen = HashSet<Throwable>()
        while (cause != null && seen.add(cause)) {
            appendLine(if (cause === error) cause.javaClass.name else "caused by ${cause.javaClass.name}")
            cause.stackTrace.take(FRAMES).forEach { appendLine("  at ${it.className}.${it.methodName}(${it.fileName}:${it.lineNumber})") }
            cause = cause.cause
        }
    }

    /** The notes, newest first. */
    fun notes(context: Context): List<File> =
        dir(context).listFiles { f -> f.name.startsWith(PREFIX) && f.name.endsWith(".txt") }.orEmpty()
            .sortedByDescending { it.name.removePrefix(PREFIX).removeSuffix(".txt").toLongOrNull() ?: 0L }

    fun clear(context: Context) {
        notes(context).forEach { it.delete() }
        context.getSharedPreferences(NOTICE_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
        MainStall.clear(context)
    }

    /**
     * C 1.32 [STAB-03] Where it stopped, to tell one problem from another: the exception's class and the first frame of
     * the app's own code (no message, nothing typed).
     */
    fun signature(error: Throwable): String {
        var root: Throwable = error
        val seen = HashSet<Throwable>()
        while (root.cause != null && seen.add(root)) root = root.cause!!
        val frame = (root.stackTrace.firstOrNull { it.className.startsWith("com.bamnat") } ?: root.stackTrace.firstOrNull())
        return "${root.javaClass.simpleName}@${frame?.className?.substringAfterLast('.')}.${frame?.methodName}:${frame?.lineNumber}"
    }

    /**
     * C 1.32 [STAB-03] (사용자 2026-10-28 "가끔 '멈췄어요 제보할까요?'가 떠요"): the same stop counted, and the notice at most once a
     * day — 1.31 posted one for every stop, so one bug that hit three times read as three. The notice says how many times.
     */
    internal fun noticeOnce(context: Context, signature: String, now: Long) {
        val prefs = context.getSharedPreferences(NOTICE_PREFS, Context.MODE_PRIVATE)
        val key = "n:" + signature.hashCode()
        val times = prefs.getInt(key, 0) + 1
        val last = prefs.getLong("last", 0L)
        prefs.edit().putInt(key, times).putString("sig:" + signature.hashCode(), signature).commit()
        if (now - last < DAY) return
        prefs.edit().putLong("last", now).commit()
        notice(context, times)
    }

    /** C 1.32 [STAB-03] The stops counted by where, most first ("IllegalStateException@PayViewModel.save:212 · 3회"). */
    fun grouped(context: Context): List<String> {
        val prefs = context.getSharedPreferences(NOTICE_PREFS, Context.MODE_PRIVATE)
        return prefs.all.keys.filter { it.startsWith("n:") }.map { k ->
            val id = k.removePrefix("n:")
            (prefs.getString("sig:$id", id) ?: id) to prefs.getInt(k, 0)
        }.sortedByDescending { it.second }.take(5).map { (sig, n) -> "$sig · ${n}회" }
    }

    /** How the app ended in the last 30 days, by Android's own record (Android 11+): crash, ANR, native crash. */
    data class Exits(val crashes: Int, val anrs: Int, val native: Int, val lowMemory: Int = 0, val lines: List<String> = emptyList())

    fun exits(context: Context, now: Long = Today.millis()): Exits? {
        if (Build.VERSION.SDK_INT < 30) return null
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val recent = runCatching { am.getHistoricalProcessExitReasons(context.packageName, 0, 0) }.getOrNull()
            ?.filter { now - it.timestamp < DAYS_30 } ?: return null
        return Exits(
            crashes = recent.count { it.reason == ApplicationExitInfo.REASON_CRASH },
            anrs = recent.count { it.reason == ApplicationExitInfo.REASON_ANR },
            native = recent.count { it.reason == ApplicationExitInfo.REASON_CRASH_NATIVE },
            lowMemory = recent.count { it.reason == ApplicationExitInfo.REASON_LOW_MEMORY },
            lines = recent.filter { it.reason in BAD }.take(5).map(::line),
        )
    }

    /** C 1.32 [STAB-02] Android's own reasons worth a line (an exit by the user or an update is not). */
    private val BAD = setOf(ApplicationExitInfo.REASON_CRASH, ApplicationExitInfo.REASON_ANR, ApplicationExitInfo.REASON_CRASH_NATIVE,
        ApplicationExitInfo.REASON_LOW_MEMORY, ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE, ApplicationExitInfo.REASON_INITIALIZATION_FAILURE)

    /**
     * C 1.32 [STAB-02] One exit in words: when, why, how much memory the app held and whether it was on screen. For an ANR,
     * the first frames of the main thread from Android's trace, only the app's and Android's own class names.
     */
    private fun line(e: ApplicationExitInfo): String {
        val at = java.time.Instant.ofEpochMilli(e.timestamp).atZone(Today.zone())
        val why = when (e.reason) {
            ApplicationExitInfo.REASON_CRASH -> "멈춤"
            ApplicationExitInfo.REASON_ANR -> "응답 없음(ANR)"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "기기 쪽 멈춤"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "메모리 부족으로 닫힘"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "자원을 너무 씀"
            else -> "시작 실패"
        }
        val front = if (e.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) "화면에 있을 때" else "뒤에 있을 때"
        val mem = "${e.pss / 1024}MB"
        val trace = if (e.reason == ApplicationExitInfo.REASON_ANR) runCatching {
            e.traceInputStream?.bufferedReader()?.useLines { lines ->
                lines.dropWhile { !it.startsWith("\"main\"") }.drop(1).filter { it.trimStart().startsWith("at ") }
                    .map { it.trim().substringBefore('(') }.filter { it.startsWith("at com.bamnat") || it.startsWith("at android") || it.startsWith("at androidx") }
                    .take(6).toList()
            }
        }.getOrNull().orEmpty() else emptyList()
        return "${at.monthValue}/${at.dayOfMonth} ${"%02d:%02d".format(at.hour, at.minute)} $why · $front · $mem" +
            trace.joinToString("") { "\n    $it" }
    }

    /** The "앱 멈춤" lines of the diagnostics report. */
    fun reportLines(context: Context, now: Long = Today.millis()): List<String> = buildList {
        val notes = notes(context).filter { now - (it.name.removePrefix(PREFIX).removeSuffix(".txt").toLongOrNull() ?: 0L) < DAYS_30 }
        exits(context, now)?.let {
            add("최근 30일: 멈춤 ${it.crashes}회 · 응답 없음(ANR) ${it.anrs}회 · 기기 쪽 멈춤 ${it.native}회 · 메모리 부족 ${it.lowMemory}회")
            addAll(it.lines)
        }
        // C 1.32 [STAB-03][STAB-07] The same stop counted together, and the times the screen froze for a while.
        grouped(context).takeIf { it.isNotEmpty() }?.let { add("멈춘 곳별:"); addAll(it.map { l -> "  $l" }) }
        MainStall.lines(context).takeIf { it.isNotEmpty() }?.let { add("화면이 잠깐 멈춘 때:"); addAll(it) }
        add("남은 기록: ${notes.size}개")
        notes.firstOrNull()?.let { add("가장 최근 기록:"); addAll(it.readLines().take(12)) }
    }

    private fun notice(context: Context, times: Int) {
        val notification = NotificationCompat.Builder(context, Notifications.STATUS_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("밤낮이 멈췄어요. 불편을 드려 죄송해요")
            .setContentText(if (times > 1) "같은 곳에서 ${times}번 멈췄어요. 알람 점검에서 기록을 보내 주시면 바로 고칠게요"
                else "알람 점검에서 무엇이 멈췄는지 보고, 원하면 보낼 수 있어요. 알람은 그대로 울려요")
            .setContentIntent(Notifications.open(context, NoticeLinks.Target.ALARM_CHECK, 2104))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, NOTICE_ID, notification)
    }
}
