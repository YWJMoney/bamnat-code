package com.bamnat.app.diag

import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.BroadcastReceiver
import android.content.ComponentCallbacks2
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import androidx.core.content.ContextCompat
import com.bamnat.app.alarm.BootAlarms
import com.bamnat.core.diag.StopTrail
import com.bamnat.core.diag.StopTrail.Kind
import com.bamnat.core.time.Today
import java.io.File
import java.util.concurrent.Executors

/**
 * 1.32.1 [STAB-10] (사용자 2026-10-29 "멈춘 상태를 어플이 인지해서 문의로 보고하면 해결할 수 있게"): 멈춤 관찰 기록, what
 * the app and the phone did just before a stop — 화면 꺼짐·켜짐, 잠금 풀림, 앱 앞·뒤, 창 초점, 접힘(화면 크기), 메모리 정리
 * 요청과 그때 메모리, 화면 멈춤·안 그려짐·눌러도 그대로, how the app ended last time — so a freeze people feel after
 * unlocking, or a Fold running out of memory, can be told apart from an alarm problem. Only codes and numbers
 * ([StopTrail]); in files/diag (left out of every backup), written off the main thread, never while the phone has not
 * been unlocked since it started (the file is not readable then; the lines wait in memory).
 */
object Trail {
    private const val FILE = "trail.txt"
    /** ApplicationExitInfo.REASON_FREEZER (Android 13+), by its number so older phones read it too. */
    private const val FREEZER = 14
    private val io = Executors.newSingleThreadExecutor { r -> Thread(r, "bamnat-trail").apply { isDaemon = true; priority = Thread.MIN_PRIORITY } }
    private val waiting = ArrayList<StopTrail.Line>()
    @Volatile private var app: Context? = null
    @Volatile private var written = 0

    /** Called in Application.onCreate after [CrashLog.install]: the phone's screen and memory are watched from here. */
    fun install(context: Context) {
        if (app != null) return
        val a = context.applicationContext
        app = a
        io.execute { note(Kind.START, listOfNotNull(lastExit(a), memoryNow()).joinToString(" ")) }
        runCatching {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            ContextCompat.registerReceiver(a, object : BroadcastReceiver() {
                override fun onReceive(c: Context, intent: Intent) {
                    when (intent.action) {
                        Intent.ACTION_SCREEN_OFF -> add(Kind.SCREEN_OFF, "")
                        Intent.ACTION_SCREEN_ON -> add(Kind.SCREEN_ON, "")
                        Intent.ACTION_USER_PRESENT -> add(Kind.UNLOCKED, memoryNow())
                    }
                }
            }, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        a.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onTrimMemory(level: Int) = add(Kind.TRIM, "level=$level ${memoryNow()}")
            override fun onConfigurationChanged(newConfig: Configuration) = Unit
            @Deprecated("Deprecated in Java") override fun onLowMemory() = add(Kind.TRIM, "level=low ${memoryNow()}")
        })
    }

    /** One line, written later on the trail's own thread. */
    fun add(kind: Kind, detail: String, at: Long = Today.millis()) {
        if (app == null) return
        io.execute { note(kind, detail, at) }
    }

    /** One line written now, on the calling thread: the app is about to end ([CrashLog]). */
    fun addNow(kind: Kind, detail: String) {
        if (app == null) return
        runCatching { note(kind, detail, Today.millis()) }
    }

    @Synchronized
    private fun note(kind: Kind, detail: String, at: Long = Today.millis()) {
        val a = app ?: return
        waiting += StopTrail.Line(at, kind, detail)
        if (BootAlarms.isLocked(a)) return
        runCatching {
            val f = file(a).apply { parentFile?.mkdirs() }
            f.appendText(waiting.joinToString("") { StopTrail.encode(it) + "\n" })
            waiting.clear()
            // Trimmed now and then, not on every line.
            if (++written % 50 == 0) {
                val kept = StopTrail.kept(read(f), Today.millis())
                f.writeText(kept.joinToString("") { StopTrail.encode(it) + "\n" })
            }
        }
    }

    private fun file(context: Context) = File(CrashLog.dir(context), FILE)

    private fun read(f: File): List<StopTrail.Line> =
        runCatching { f.readLines().mapNotNull(StopTrail::decode) }.getOrDefault(emptyList())

    /** The kept lines, oldest first, with the ones not written yet. Off the main thread. */
    @Synchronized
    fun lines(context: Context, now: Long = Today.millis()): List<StopTrail.Line> =
        StopTrail.kept(read(file(context)) + waiting, now)

    fun clear(context: Context) {
        io.execute { synchronized(this) { waiting.clear(); file(context).delete() } }
    }

    /** "heap=212/256 native=80" now. */
    fun memoryNow(): String {
        val r = Runtime.getRuntime()
        return StopTrail.memory(r.totalMemory() - r.freeMemory(), r.maxMemory(), runCatching { android.os.Debug.getNativeHeapAllocatedSize() }.getOrDefault(0L))
    }

    /** Whether the Java heap is nearly at its limit now. */
    fun heapTight(): Boolean {
        val r = Runtime.getRuntime()
        return (r.totalMemory() - r.freeMemory()) * 10 >= r.maxMemory() * 9
    }

    /** "w=411 h=914": the window's size in dp, which a Fold opening or closing changes. */
    fun size(config: Configuration) = "w=${config.screenWidthDp} h=${config.screenHeightDp}"

    /** How the app ended before this start, by Android's own record (Android 11+): "last=user" and so on. */
    private fun lastExit(context: Context): String? {
        if (android.os.Build.VERSION.SDK_INT < 30) return null
        val e = runCatching {
            context.getSystemService(ActivityManager::class.java)?.getHistoricalProcessExitReasons(context.packageName, 0, 1)?.firstOrNull()
        }.getOrNull() ?: return null
        val code = when (e.reason) {
            ApplicationExitInfo.REASON_CRASH -> "crash"
            ApplicationExitInfo.REASON_ANR -> "anr"
            ApplicationExitInfo.REASON_CRASH_NATIVE -> "native"
            ApplicationExitInfo.REASON_LOW_MEMORY -> "lowmem"
            ApplicationExitInfo.REASON_USER_REQUESTED -> "user"
            ApplicationExitInfo.REASON_USER_STOPPED -> "user"
            ApplicationExitInfo.REASON_SIGNALED, ApplicationExitInfo.REASON_OTHER ->
                if (e.description?.contains("swipe", ignoreCase = true) == true || e.description?.contains("remove task", ignoreCase = true) == true) "swipe" else "kill"
            FREEZER -> "freezer"
            ApplicationExitInfo.REASON_PACKAGE_UPDATED -> "update"
            ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE -> "resource"
            else -> "other"
        }
        val mem = (if (e.pss > 0) e.pss else e.rss) / 1024
        return "last=$code lastmem=$mem"
    }
}
