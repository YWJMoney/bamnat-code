package com.bamnat.app.data

import android.content.Context

/**
 * 2.0.0 [RVR-44] (총괄 HQ-123) One [T] per app, as [com.bamnat.app.inbox.Inbox] and [WorkToolsStore] already keep theirs.
 *
 * On a phone there is one app in a process, so this is one [T] as before. A test gets a new app (its own files and
 * settings) each time while the JVM stays, and a store kept from the first app went on reading and writing the first
 * test's settings: what an earlier test had seen or closed (GuideStore's tips and checklist, DiscoverStore's '이런 기능도
 * 있어요', the 출퇴근 and 근무 유형 settings) came back in the next test and changed what it showed. Made again when
 * the app is another one, so a test starts from its own app's files.
 */
internal class PerApp<T : Any>(private val make: (Context) -> T) {
    @Volatile private var kept: Pair<Context, T>? = null

    fun of(context: Context): T {
        val app = context.applicationContext ?: context
        kept?.let { (owner, value) -> if (owner === app) return value }
        return synchronized(this) {
            kept?.takeIf { it.first === app }?.second ?: make(app).also { kept = app to it }
        }
    }
}
