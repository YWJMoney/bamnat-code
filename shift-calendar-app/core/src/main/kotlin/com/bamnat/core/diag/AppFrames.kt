package com.bamnat.core.diag

/**
 * 2.0.0 [RVL-55] (검토 DATA-07·I12a-04) Which lines of a stack are the app's, in a debug build and in the shrunk release
 * build alike, and how a stop is named.
 *
 * The release build's R8 renames the app's classes, and the libraries packed with it, to short names (`a.b.c`), so a line
 * can't be told by `com.bamnat` there: 1.32~1.39 kept only the phone's own lines in a 화면 멈춤 note and named a stop
 * `NullPointerException@b.a:12` (package dropped), which no mapping turns back and two stops could share. What keeps its
 * name is the phone's own code (java, android, dalvik…), so a line is the app's when it is `com.bamnat`, or a renamed
 * name; the build's mapping file turns a renamed one back (`tools/retrace.py`, with the mapping zip kept in
 * release/<판>/앱/; docs/ops-desk.md '멈춤 기록 풀기').
 */
object AppFrames {
    /** The phone's own code: never renamed by the app's build. */
    private val PHONE = listOf(
        "java.", "javax.", "jdk.", "sun.", "dalvik.", "libcore.", "android.", "com.android.", "org.apache.harmony.",
        "org.json.", "org.xmlpull.", "org.w3c.", "org.xml.", "org.chromium.",
    )

    /** Libraries a debug build shows by name (the release build renames them too, and the mapping tells them apart). */
    private val LIBS = listOf(
        "androidx.", "kotlin.", "kotlinx.", "com.google.", "okio.", "org.jetbrains.", "org.intellij.", "io.", "coil.",
    )

    /** The app's own code by name. */
    fun named(className: String): Boolean = className.startsWith("com.bamnat")

    fun phone(className: String): Boolean = PHONE.any { className.startsWith(it) }

    /** A line of the app's own code: `com.bamnat`, or (in the release build) a renamed name the mapping turns back. */
    fun ours(className: String): Boolean = named(className) || (!phone(className) && LIBS.none { className.startsWith(it) })

    /** What a 화면 멈춤 note keeps: ours, and the ui lines around them (android, androidx), as 1.32 meant. */
    fun kept(className: String): Boolean = ours(className) || className.startsWith("android") || className.startsWith("androidx")

    /**
     * "PayViewModel.save:212" for a line by name (the form 1.32~1.39 used, so a stop counted then still counts as the same);
     * a renamed line whole ("a.b.c.d:12"), so the mapping can read it.
     */
    fun short(className: String, method: String, line: Int): String =
        if (named(className)) "${className.substringAfterLast('.')}.$method:$line" else "$className.$method:$line"

    /**
     * Where a stack stopped: its first line of ours ([short]) and, when that is a renamed line, the next one of ours too
     * ("a.b.c.d:12<e.f.g:40"): in the release build the first renamed line is often a packed kotlin check that many stops
     * share, and the second tells them apart. Null when no line is ours.
     */
    fun where(frames: Array<StackTraceElement>): String? {
        val mine = frames.filter { ours(it.className) }
        val first = mine.firstOrNull() ?: return null
        val one = short(first.className, first.methodName, first.lineNumber)
        val next = mine.getOrNull(1)
        return if (named(first.className) || next == null) one else "$one<${short(next.className, next.methodName, next.lineNumber)}"
    }

    /** The class of a written line, "com.bamnat.app.ui.pay.PayViewModel.save:212" or "at a.b.c(SourceFile:3)" → its class. */
    fun classOf(line: String): String =
        line.trim().removePrefix("at ").substringBefore('(').substringBefore(':').substringBeforeLast('.')

    /** [short] for a written line ("com.bamnat.app.ui.pay.PayViewModel.save:212" → "PayViewModel.save:212"; a renamed one whole). */
    fun shortLine(line: String): String {
        val t = line.trim().removePrefix("at ")
        return if (named(t)) t.split('.').takeLast(2).joinToString(".") else t
    }
}
