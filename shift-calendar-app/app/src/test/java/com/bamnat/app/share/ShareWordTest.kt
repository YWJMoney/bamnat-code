package com.bamnat.app.share

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BamnatApp
import com.bamnat.app.data.PersonalCalendar
import com.bamnat.core.flags.FlagCatalog
import com.bamnat.core.time.Today
import com.bamnat.core.work.WorkStyle
import com.bamnat.core.work.scheduleWord
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVG-38] 검토 HQ-64 B안 (D RVD-32 #514, 총괄 11-13): the link's word (what the sender's app calls it) is a new field the share
 * server keeps, so it goes only from 처리방침 3.14 (`share.word`). While that row is off, no body the app sends has it: the link
 * made in the share sheet and the automatic update ([ShareSync], ttl null) both build it with [ShareContent.payload]'s own gates.
 * Once on, it is the calendar's own word and the receiving app reads it back as the link's name.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ShareWordTest {
    private val app get() = ApplicationProvider.getApplicationContext<BamnatApp>()
    private val c get() = app.container

    @After fun tearDown() = runBlocking { c.settingsRepository.update { it.copy(workStyle = null) } }

    @Test fun noBodyHasTheWordUntil314AndThenItIsTheCalendarsOwn() = runBlocking {
        assertFalse("share.word waits for 3.14, which no calendar has yet", c.flags.on(FlagCatalog.SHARE_WORD))
        assertFalse(ShareContent.wordInLink(c))
        val from = Today.date().withDayOfMonth(1)
        val everything = ShareOptions(leaveKinds = true, extras = true, memos = true, pay = true, jobs = true, audience = ShareAudience.FAMILY)
        for (style in WorkStyle.entries) {
            c.settingsRepository.update { it.copy(workStyle = style) }
            val snapshot = c.calendars.repository(PersonalCalendar.MAIN).snapshot.first()
            for (options in listOf(ShareOptions(), everything)) {
                // The share sheet's link and the automatic update, with the gates the flags give now.
                val made = ShareContent.payload(c, snapshot, from, "민지", 30, options, PersonalCalendar.MAIN)
                val sync = ShareContent.payload(c, snapshot, from, "민지", null, options, PersonalCalendar.MAIN)
                assertFalse("$style ${options.shows}: $made", made.has("word"))
                assertFalse("$style ${options.shows}: $sync", sync.has("word"))
                assertEquals(null, SharedSchedule.parse(made).saidWord)
            }
            // From 3.14: the calendar's own word, which names the link where it carries no money or jobs.
            val open = ShareContent.payload(c, snapshot, from, "민지", 30, ShareOptions(), PersonalCalendar.MAIN,
                gates = ShareContent.Gates.of(c).copy(word = true))
            assertEquals(style.name, scheduleWord(style), open.getString("word"))
            assertEquals(style.name, scheduleWord(style), SharedSchedule.parse(open).word)
        }
    }
}
