package com.bamnat.app.company

import android.app.job.JobScheduler
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * 2.0.0 [RVN-13] 검토 COMP-51: a push's check (문의 답, 광장 답·같은 근무 질문) goes to an expedited job that may use the network while
 * the phone sleeps, one per kind so one push does not take another's place.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PushCheckJobTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val jobs get() = context.getSystemService(JobScheduler::class.java)

    @After fun end() = jobs.cancelAll()

    @Test fun eachKindOfPushIsCheckedInItsOwnExpeditedJob() {
        PushCheckJob.run(context, PlazaPush.ANSWER)
        PushCheckJob.run(context, PushCheckJob.INQUIRY)
        PushCheckJob.run(context, PlazaPush.QUESTION)
        val pending = jobs.allPendingJobs.filter { it.service.className == PushCheckJob::class.java.name }
        assertEquals(3, pending.size)
        pending.forEach { j ->
            assertTrue("expedited", j.isExpedited)
            assertNotNull("needs the network", j.requiredNetwork)
        }
        assertEquals(setOf(PlazaPush.ANSWER, PlazaPush.QUESTION, PushCheckJob.INQUIRY), pending.map { it.extras.getString("kind") }.toSet())
    }
}
