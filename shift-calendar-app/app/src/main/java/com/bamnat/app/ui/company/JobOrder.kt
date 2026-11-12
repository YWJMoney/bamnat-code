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
    return remember(calendar) { Plaza.jobsFor(c.jobStyleOf(calendar)) }
}

/**
 * 2.0.0 [RVN-18] 검토 T10a-05·06: the kind of work whose jobs a calendar starts at. 투자 has no job of its own, so it follows the first
 * work calendar on this phone (main first); with none, the plaza's own order.
 */
internal fun com.bamnat.app.AppContainer.jobStyleOf(calendar: String): com.bamnat.core.work.WorkStyle? {
    val main = settingsRepository.current.workStyle
    val own = calendars.styleOf(calendar, main)
    if (own != com.bamnat.core.work.WorkStyle.INVEST) return own
    return calendars.calendars.value.asSequence().filter { !it.isExample }.map { calendars.styleOf(it.id, main) }
        .firstOrNull { it != null && it != com.bamnat.core.work.WorkStyle.INVEST }
}

/** 2.0.0 [RVN-18] 검토 T10a-06: the job a board opens on: the kind of work's own, else the first of its order (as the chips show it). */
internal fun firstJob(style: com.bamnat.core.work.WorkStyle?): String = jobOfStyle(style) ?: Plaza.jobsFor(style).first().first
