package com.bamnat.app.imports

import android.content.Context
import java.io.File

/**
 * 2.0.0 [RVL-45] (검토 SCAN-23) The pictures taken or drawn for reading (명세서·근무표 photos, a PDF's pages, the alarm's photo
 * mission) are deleted once read, but an app closed meanwhile (a phone short of memory, behind the camera) left them in its
 * cache: "읽으면 바로 지워요" was not always so. At each start the ones older than [KEEP_MS] go; a photo just taken, which the
 * app is opening again to read, is younger and stays.
 */
object ScanCache {
    const val KEEP_MS = 30 * 60 * 1000L

    /** How many were deleted. */
    fun sweep(context: Context, now: Long = System.currentTimeMillis()): Int { // real clock: held against the file system's own times
        val cache = context.cacheDir ?: return 0
        val scans = File(cache, "scan").listFiles().orEmpty().asSequence()
        // AnyFile.pages' copies of a PDF (File.createTempFile("roster", ".pdf")).
        val pdfs = cache.listFiles { f -> f.name.startsWith("roster") && f.name.endsWith(".pdf") }.orEmpty().asSequence()
        return (scans + pdfs).filter { it.isFile && now - it.lastModified() > KEEP_MS }.count { it.delete() }
    }
}
