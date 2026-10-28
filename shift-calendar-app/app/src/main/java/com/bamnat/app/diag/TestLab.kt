package com.bamnat.app.diag

import android.content.ContentResolver
import android.content.Context
import android.provider.Settings

/**
 * [QA130-01] 1.30 AY: Google Play's pre-launch report runs the app on Firebase Test Lab phones, where a robot taps
 * everything: it can send real-looking 광장 제보·제안·투표, 문의, 공유 링크 and usage events, which would sit in the admin app
 * as if people had sent them. On those phones nothing is written to our servers; reads (search, the plaza, a shared calendar)
 * go as usual so the robot still sees the screens. Test Lab says it is there with the system setting
 * `firebase.test.lab` = "true" (Firebase's documented way).
 *
 * The one gate is [allows], asked by both server clients' request functions (company `serverRequest`, share
 * `ShareLink.request`), so a new write anywhere is covered too. The setting is read once, off the main thread (the
 * first request), never at start.
 */
object TestLab {
    const val SETTING = "firebase.test.lab"

    @Volatile private var context: Context? = null
    @Volatile private var robot: Boolean? = null

    /** From the app's start: only keeps the context, reads nothing. */
    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    /** Whether this phone is a Test Lab robot's (false when unknown, e.g. before [attach]). */
    fun isRobot(): Boolean {
        robot?.let { return it }
        val c = context ?: return false
        return read(c.contentResolver).also { robot = it }
    }

    fun read(resolver: ContentResolver): Boolean =
        runCatching { Settings.System.getString(resolver, SETTING) == "true" }.getOrDefault(false)

    /** Whether a request may go: a read always, a write (POST, PUT, PATCH, DELETE) only from a person's phone. */
    fun allows(method: String, robot: Boolean = isRobot()): Boolean =
        !robot || method.equals("GET", ignoreCase = true) || method.equals("HEAD", ignoreCase = true)

    /** For tests: forget what was read, so the next request reads the setting again. */
    internal fun forget() {
        robot = null
    }
}
