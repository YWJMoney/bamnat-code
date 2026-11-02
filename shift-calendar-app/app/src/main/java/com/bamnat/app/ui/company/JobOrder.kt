package com.bamnat.app.ui.company

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.bamnat.app.appContainer
import com.bamnat.app.company.Plaza

/** PC 확인 V41: the job plazas in the order this calendar's kind of work most often needs them. */
@Composable
internal fun rememberJobs(): List<Pair<String, String>> {
    val c = LocalContext.current.appContainer
    val calendar = LocalCompanyCalendar.current
    return remember(calendar) { Plaza.jobsFor(c.calendars.styleOf(calendar, c.settingsRepository.current.workStyle)) }
}
