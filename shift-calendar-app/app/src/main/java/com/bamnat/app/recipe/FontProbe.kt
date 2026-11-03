package com.bamnat.app.recipe

import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * 1.36 [SEC-10] 보안 점검 H3: a font that came with someone else's recipe is opened first in a process of its own that has no
 * rights at all ([FontProbeService], `android:isolatedProcess`): it builds the typeface from the file and draws a few letters
 * in it. Only a font that came through that is opened in the app's own process, where the calendar, ledger and memos live.
 * A font that stops or hangs that process (a broken file, or one made to break a phone's font engine) is left out, and the
 * recipe shows the stand-in font as it does for a font not shared.
 */
object FontProbe {
    const val PROCESS = ":fontprobe"
    private const val ASK = 1
    private const val ANSWER = 2
    private const val TIMEOUT_MS = 10_000L

    /** Tests: what the look answers instead of the other process. */
    @Volatile var look: (suspend (Context, File) -> Boolean)? = null

    /** Ids that failed the look since the app started, so the same recipe does not ask again each time. */
    private val bad = java.util.Collections.synchronizedSet(mutableSetOf<String>())

    fun failed(id: String): Boolean = id in bad

    /** Whether [file] opens and draws as a font in the isolated process (false for a stop, a hang or no answer). */
    suspend fun safe(context: Context, id: String, file: File): Boolean {
        if (id in bad) return false
        val ok = look?.invoke(context, file)
            ?: if (Build.FINGERPRINT == "robolectric") true
            else withTimeoutOrNull(TIMEOUT_MS) { ask(context.applicationContext, file) } ?: false
        if (!ok) bad += id
        return ok
    }

    /** Whether this is the look's own process, where the app starts nothing of its own ([com.bamnat.app.BamnatApp]). */
    fun inProbe(): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= 28) android.os.Process.isIsolated()
        else File("/proc/self/cmdline").readText().trimEnd('\u0000').endsWith(PROCESS)
    }.getOrDefault(false)

    private suspend fun ask(c: Context, file: File): Boolean = suspendCancellableCoroutine { cont ->
        val pfd = runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }.getOrNull()
        if (pfd == null) { cont.resume(false); return@suspendCancellableCoroutine }
        val over = AtomicBoolean(false)
        var conn: ServiceConnection? = null
        fun done(ok: Boolean) {
            if (!over.compareAndSet(false, true)) return
            runCatching { pfd.close() }
            conn?.let { runCatching { c.unbindService(it) } }
            if (cont.isActive) cont.resume(ok)
        }
        val reply = Messenger(Handler(Looper.getMainLooper()) { m -> if (m.what == ANSWER) done(m.arg1 == 1); true })
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                if (service == null) { done(false); return }
                val msg = Message.obtain(null, ASK).apply { data = Bundle().apply { putParcelable("fd", pfd) }; replyTo = reply }
                runCatching { Messenger(service).send(msg) }.onFailure { done(false) }
            }
            // The look stopped the process: not a font to open here.
            override fun onServiceDisconnected(name: ComponentName?) = done(false)
            override fun onBindingDied(name: ComponentName?) = done(false)
            override fun onNullBinding(name: ComponentName?) = done(false)
        }
        conn = connection
        cont.invokeOnCancellation { done(false) }
        val bound = runCatching { c.bindService(Intent(c, FontProbeService::class.java), connection, Context.BIND_AUTO_CREATE) }.getOrDefault(false)
        if (!bound) { conn = null; done(false) }
    }

    /** What the other process does with the font: build it and draw Korean, Latin and digits in it. */
    internal fun draws(pfd: ParcelFileDescriptor): Boolean = runCatching {
        val face = Typeface.Builder(pfd.fileDescriptor).build() ?: return false
        val paint = Paint().apply { typeface = face; textSize = 40f; isAntiAlias = true }
        val bmp = Bitmap.createBitmap(320, 64, Bitmap.Config.ALPHA_8)
        Canvas(bmp).drawText("가나다 밤낮 Aa 0123 ₩", 4f, 48f, paint)
        paint.measureText("가나다 밤낮 Aa 0123 ₩")
        bmp.recycle()
        true
    }.getOrDefault(false)

    internal const val ASK_WHAT = ASK
    internal const val ANSWER_WHAT = ANSWER
}

/** The isolated process of [FontProbe]: no rights, no files of the app's, only the font it is handed. */
class FontProbeService : Service() {
    private val messenger = Messenger(Handler(Looper.getMainLooper()) { msg ->
        if (msg.what == FontProbe.ASK_WHAT) {
            @Suppress("DEPRECATION")
            val pfd = msg.data?.let { it.classLoader = ParcelFileDescriptor::class.java.classLoader; it.getParcelable<ParcelFileDescriptor>("fd") }
            val ok = pfd?.use { FontProbe.draws(it) } ?: false
            runCatching { msg.replyTo?.send(Message.obtain(null, FontProbe.ANSWER_WHAT, if (ok) 1 else 0, 0)) }
        }
        true
    })

    override fun onBind(intent: Intent?): IBinder = messenger.binder
}
