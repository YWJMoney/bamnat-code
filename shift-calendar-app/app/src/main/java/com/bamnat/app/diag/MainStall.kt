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
 *
 * 1.32.1 [STAB-11] (사용자 2026-10-29 "잠금을 풀면 화면이 정지된다"): also how long a freeze lasted, a screen not drawn
 * again [NO_DRAW_MS] after the app came back (the main thread free or not), [TAPS] taps that changed nothing, and the
 * memory now and then, each a line of [Trail].
 */
object MainStall {
    private const val PREFIX = "stall-"
    const val KEEP = 5
    const val STALL_MS = 2_000L
    private const val TICK_MS = 1_000L
    private const val FRAMES = 12

    @Volatile private var watcher: Thread? = null
    @Volatile private var answeredAt = 0L

    // 1.32.1 [STAB-11] The screen drawn again after the app came back, and taps that changed nothing.
    const val NO_DRAW_MS = 3_000L
    const val SLOW_DRAW_MS = 1_500L
    const val TAPS = 3
    const val TAPS_WITHIN_MS = 10_000L
    private const val MEMORY_EVERY_TICKS = 300
    @Volatile private var drawWaitFrom = 0L
    @Volatile private var noDrawNoted = false
    @Volatile private var focused = false
    private val taps = ArrayDeque<Long>()
    @Volatile private var tapsNoted = false

    fun start(context: Context) {
        if (watcher != null || isRobolectric) return
        val app = context.applicationContext
        val main = Handler(Looper.getMainLooper())
        val ping = Runnable { answeredAt = android.os.SystemClock.uptimeMillis() }
        answeredAt = android.os.SystemClock.uptimeMillis()
        watcher = Thread({
            var noted: File? = null
            var since = 0L
            var ticks = 0
            var tight = false
            try {
                while (!Thread.currentThread().isInterrupted) {
                    main.post(ping)
                    Thread.sleep(TICK_MS)
                    val now = android.os.SystemClock.uptimeMillis()
                    val late = now - answeredAt
                    if (late >= STALL_MS && noted == null) {
                        since = answeredAt
                        val frames = Looper.getMainLooper().thread.stackTrace
                        noted = runCatching { write(app, late, frames, Today.millis()) }.getOrNull() ?: File("")
                        Trail.add(com.bamnat.core.diag.StopTrail.Kind.STALL, "at=${where(frames)} ${Trail.memoryNow()}")
                    } else if (late < STALL_MS && noted != null) {
                        // [STAB-11] How long it really lasted, on the note and the trail.
                        val ms = answeredAt - since
                        runCatching { if (noted.exists()) noted.writeText(noted.readLines().drop(1).joinToString("\n", "${ms}ms\n", "\n")) }
                        Trail.add(com.bamnat.core.diag.StopTrail.Kind.STALL_END, "ms=$ms")
                        noted = null
                    }
                    // [STAB-11] Asked to draw (back on screen) and nothing drawn for a while, whether the main thread is free or not.
                    val waiting = drawWaitFrom
                    if (waiting > 0 && !noDrawNoted && now - waiting >= NO_DRAW_MS && !keyguardShown(app)) {
                        noDrawNoted = true
                        val frames = Looper.getMainLooper().thread.stackTrace
                        val free = late < STALL_MS
                        Trail.add(com.bamnat.core.diag.StopTrail.Kind.NO_DRAW,
                            "ms=${now - waiting} main=${if (free) "free" else "busy"} focus=${if (focused) 1 else 0} at=${where(frames)}")
                    }
                    if (++ticks % MEMORY_EVERY_TICKS == 0) Trail.add(com.bamnat.core.diag.StopTrail.Kind.MEMORY, Trail.memoryNow())
                    val nowTight = Trail.heapTight()
                    if (nowTight && !tight) Trail.add(com.bamnat.core.diag.StopTrail.Kind.MEMORY, "tight ${Trail.memoryNow()}")
                    tight = nowTight
                }
            } catch (_: InterruptedException) { }
        }, "bamnat-stall").apply { isDaemon = true; priority = Thread.MIN_PRIORITY; start() }
    }

    fun stop() {
        watcher?.interrupt()
        watcher = null
        drawWaitFrom = 0L
    }

    /** [STAB-11] The app came back on screen: the next draw is waited for. */
    fun expectDraw() {
        drawWaitFrom = android.os.SystemClock.uptimeMillis()
        noDrawNoted = false
    }

    /** [STAB-11] The window drew (its ViewTreeObserver's draw listener, on the main thread). */
    fun drew() {
        val from = drawWaitFrom
        if (from > 0) {
            drawWaitFrom = 0L
            val ms = android.os.SystemClock.uptimeMillis() - from
            if (ms >= SLOW_DRAW_MS || noDrawNoted) Trail.add(com.bamnat.core.diag.StopTrail.Kind.DRAW_SLOW, "ms=$ms")
        }
        if (taps.isNotEmpty()) taps.clear()
        tapsNoted = false
    }

    fun focus(has: Boolean) {
        focused = has
    }

    /**
     * [STAB-11] A finger lifted (the activity's dispatchTouchEvent, main thread). [TAPS] in [TAPS_WITHIN_MS] with no draw
     * between them: the person tapped and the screen did not change, once until the next draw.
     */
    fun tapped() {
        val now = android.os.SystemClock.uptimeMillis()
        taps.addLast(now)
        while (taps.isNotEmpty() && now - taps.first() > TAPS_WITHIN_MS) taps.removeFirst()
        if (taps.size >= TAPS && !tapsNoted) {
            tapsNoted = true
            Trail.add(com.bamnat.core.diag.StopTrail.Kind.TAPS, "n=${taps.size} ms=${now - taps.first()} focus=${if (focused) 1 else 0}")
        }
    }

    private fun keyguardShown(context: Context): Boolean =
        runCatching { context.getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true }.getOrDefault(false)

    /** The first app line of [frames] ("PayViewModel.save:212"), else the first line. */
    private fun where(frames: Array<StackTraceElement>): String {
        val f = frames.firstOrNull { it.className.startsWith("com.bamnat") } ?: frames.firstOrNull() ?: return "none"
        return "${f.className.substringAfterLast('.')}.${f.methodName}:${f.lineNumber}"
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
