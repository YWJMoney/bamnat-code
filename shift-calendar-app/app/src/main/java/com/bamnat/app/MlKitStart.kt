package com.bamnat.app

import android.content.Context
import com.google.mlkit.common.MlKit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * [NXT-01] 1.29 AU (AO D8): ML Kit is started where a photo is read, not at every app start. Its start-up provider
 * (MlKitInitProvider, which ran before Application.onCreate on every cold start, photo or not) is taken out in the
 * manifest, so every place that reads a photo with ML Kit calls [ensure] first: the text model ([com.bamnat.app.pay.OcrModel],
 * which every photo reader goes through) and the document scanner ([com.bamnat.app.imports.ScannerButton]).
 * NxtStartupProvidersTest fails when a file uses ML Kit's text recognizer or scanner without it.
 */
object MlKitStart {
    private val started = AtomicBoolean(false)

    /** Starts ML Kit once for the process; later calls cost a read. Safe from any thread. */
    fun ensure(context: Context) {
        if (started.get()) return
        synchronized(this) {
            if (started.get()) return
            // MlKit.initialize keeps an ML Kit already started (MlKitContext.initializeIfNeeded underneath).
            MlKit.initialize(context.applicationContext ?: context)
            started.set(true)
        }
    }
}
