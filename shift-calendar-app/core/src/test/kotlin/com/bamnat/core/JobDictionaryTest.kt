package com.bamnat.core

import com.bamnat.core.work.JobDictionary
import com.bamnat.core.work.JobMatch
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** [FIT-03] 1.25 묶음 Y (U23): "어떤 일을 하고 계신가요?" read on the phone into a kind of work. */
class JobDictionaryTest {
    private fun best(text: String) = JobDictionary.best(text)

    @Test fun holdsMoreThanAHundredJobsEveryKindOfWorkAndUniqueNames() {
        assertTrue("${JobDictionary.jobs.size}", JobDictionary.jobs.size >= 120)
        assertEquals(WorkStyle.entries.toSet(), JobDictionary.jobs.map { it.style }.toSet())
        assertEquals(JobDictionary.jobs.size, JobDictionary.jobs.map { it.name }.toSet().size)
        JobDictionary.common.forEach { assertNotNull(it, JobDictionary.byName(it)) }
    }

    /** Every job and every word for it finds that job first. */
    @Test fun everyNameAndAliasFindsItsJob() {
        val wrong = JobDictionary.jobs.flatMap { job ->
            job.words.filter { JobDictionary.normalize(it).length >= 2 }.mapNotNull { w ->
                val got = best(w)?.job
                if (got?.style == job.style && (job.style != WorkStyle.SHIFT || got.roster == job.roster)) null else "$w → ${got?.name}"
            }
        }
        assertEquals(emptyList<String>(), wrong)
    }

    @Test fun sentencesTyposAndInitials() {
        val nurse = best("저는 간호사예요")!!
        assertEquals("간호사", nurse.job.name)
        assertTrue(nurse.job.roster)
        assertEquals(WorkStyle.SHIFT, best("반도체 공장에서 일해요")!!.job.style)
        // 오타.
        assertEquals("간호사", best("간호샤")!!.job.name)
        assertEquals(JobMatch.How.TYPO, best("간오사")!!.how)
        assertEquals("간호사", best("간오사")!!.job.name)
        assertEquals(WorkStyle.HOURLY, best("편의점알발")!!.job.style)
        // 초성.
        assertEquals("간호사", best("ㄱㅎㅅ")!!.job.name)
        assertEquals(JobMatch.How.INITIALS, best("ㄱㅎㅅ")!!.how)
        // English and case.
        assertEquals(WorkStyle.INVEST, best("ETF")!!.job.style)
        assertEquals(WorkStyle.FIELD, best("쿠팡플렉스 해요")!!.job.style)
        assertEquals(WorkStyle.BUSINESS, best("스마트스토어 운영")!!.job.style)
        assertEquals(WorkStyle.DAY, best("공무원")!!.job.style)
        assertEquals(WorkStyle.SHIFT, best("경찰")!!.job.style)
    }

    @Test fun nothingForTooLittleOrUnknownWords() {
        assertEquals(emptyList<JobMatch>(), JobDictionary.suggest(""))
        assertEquals(emptyList<JobMatch>(), JobDictionary.suggest("ㄱ"))
        assertEquals(emptyList<JobMatch>(), JobDictionary.suggest("우주비행사아저씨"))
    }

    @Test fun jamoFollowsUnicode() {
        assertEquals("ㄱㅏㄴㅎㅗㅅㅏ", JobDictionary.jamo("간호사"))
        assertEquals("ㄱㅎㅅ", JobDictionary.initials("간호사"))
        assertEquals(1, JobDictionary.distance("ㄱㅏㄴ", "ㄱㅏㅁ", 2))
    }
}
