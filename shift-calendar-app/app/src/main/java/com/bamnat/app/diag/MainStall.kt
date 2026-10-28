package com.bamnat.app.diag

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.bamnat.core.time.Today
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * C 1.32 [STAB-07] (사용자 2026-10-28 "가끔 휴대폰이 이상해져요"): while the app is on screen, a small thread asks the main
 * thread every second whether it is free. When it has not answered for [STALL_MS], one short note is kept of when, how
 * long and the main thread's own code lines (app and Android frames only, never a message or anything typed), in
 * files/diag like [CrashLog]'s notes, newest [KEEP]. Android's own ANR comes only after 5 seconds and says little;
 * this catches the shorter freezes people feel. The notes leave the phone only with "점검 내용과 함께 문의하기".
 */
object MainStall {
    private const val PREFIX = "stall-"
    const val KEEP = 5
    const val STALL_MS = 2_000L
    private const val TICK_MS = 1_000L
    private const val FRAMES = 12

    @Volatile private var watcher: Thread? = null
    @Volatile private var answeredAt = 0L

    fun start(context: Context) {
        if (watcher != null || isRobolectric) return
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val ping = Runnable { answeredAt = android.os.SystemClock.uptimeMillis() }
        answeredAt = android.os.SystemClock.uptimeMillis()
        watcher = Thread({
            var noted = false
            try {
                while (!Thread.currentThread().isInterrupted) {
                    main.post(ping)
                    Thread.sleep(TICK_MS)
                    val late = android.os.SystemClock.uptimeMillis() - answeredAt
                    if (late >= STALL_MS && !noted) {
                        noted = true
                        runCatching { write(app, late, Looper.getMainLooper().thread.stackTrace, Today.millis()) }
                    } else if (late < STALL_MS) noted = false
                }
            } catch (_: InterruptedException) { }
        }, "bamnat-stall").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    fun stop() {
        watcher?.interrupt()
        watcher = null
    }

    fun write(context: Context, ms: Long, frames: Array<StackTraceElement>, atMillis: Long): File {
        val dir = CrashLog.dir(context).apply { mkdirs() }
        val file = File(dir, "$PREFIX$atMillis.txt")
        val kept = frames.filter { it.className.startsWith("com.bamnat") || it.className.startsWith("android") || it.className.startsWith("androidx") }
            .take(FRAMES).joinToString("\n") { "  at ${it.className}.${it.methodName}:${it.lineNumber}" }
        file.writeText("${ms}ms\n$kept\n")
        notes(context).drop(KEEP).forEach { it.delete() }
        return file
    }

    fun notes(context: Context): List<File> =
        CrashLog.dir(context).listFiles { f -> f.name.startsWith(PREFIX) }?.sortedByDescending { it.name } ?: emptyList()

    /** One line per freeze: "10-28 14:02 · 3.1초 · PayViewModel.save:212" (the first app line, else the first line). */
    fun lines(context: Context): List<String> = notes(context).mapNotNull { f ->
        val at = f.name.removePrefix(PREFIX).removeSuffix(".txt").toLongOrNull() ?: return@mapNotNull null
        val text = runCatching { f.readLines() }.getOrNull() ?: return@mapNotNull null
        val ms = text.firstOrNull()?.removeSuffix("ms")?.toLongOrNull() ?: return@mapNotNull null
        val frames = text.drop(1).map { it.trim().removePrefix("at ") }.filter { it.isNotEmpty() }
        // "com.bamnat.app.ui.pay.PayViewModel.save:212" → "PayViewModel.save:212"
        val where = (frames.firstOrNull { it.startsWith("com.bamnat") } ?: frames.firstOrNull())
            ?.split('.')?.takeLast(2)?.joinToString(".") ?: "알 수 없음"
        "${SimpleDateFormat("MM-dd HH:mm", Locale.KOREA).format(Date(at))} · ${"%.1f".format(ms / 1000.0)}초 · $where"
    }

    fun clear(context: Context) = notes(context).forEach { it.delete() }

    private val isRobolectric = runCatching { Class.forName("org.robolectric.Robolectric") }.isSuccess
}
