package com.bamnat.app.data

import com.bamnat.core.schedule.ShiftBundles
import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.setup.SetupStep
import com.bamnat.core.work.WorkStyle
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [SETUP-16] A first setup put off ("나중에 할게요") keeps its kind of work in the settings ([AppSettings.setupLater]). The waiting
 * setup follows that kind of work wherever it changes: 설정 > 내 근무 > 일하는 방식, a backup brought back without the setup's
 * own answers (1.37 PC f42b2eba HIGH-1, HIGH-2). Work written day by day (현장·자영업·투자) has nothing more to ask, so its
 * setup is simply done.
 */
object SetupLater {
    /** The setup's first answer for [style], on the question after it, as "어떤 일을 하고 계신가요?" starts it. */
    fun fresh(style: WorkStyle, roster: Boolean = false, company: String = ""): SetupDraft {
        val d = when (style) {
            WorkStyle.DAY -> SetupDraft(style = style).withShifts(null, ShiftBundles.weekday)
            WorkStyle.HOURLY -> SetupDraft(style = style).withShifts(null, ShiftBundles.hourly)
            else -> SetupDraft(style = style, rosterMonthly = roster && style == WorkStyle.SHIFT)
        }.copy(companyName = company)
        return d.copy(step = d.steps.getOrElse(1) { SetupStep.SUMMARY })
    }

    /** What the waiting setup does for the settings [s] and the draft [d]. */
    sealed interface Step {
        data object Nothing : Step
        /** The draft goes on with the settings' kind of work. */
        data class Draft(val draft: SetupDraft) : Step
        /** Nothing left to ask: the setup is saved as it is. */
        data class Finish(val style: WorkStyle) : Step
    }

    /**
     * [settingsMoved] false: only the draft changed since the last look. That is the setup's own answer ('어떤 일을 하고 계신가요?'
     * picked again, [com.bamnat.app.ui.setup.SetupViewModel.pickStyle] saves the draft and then the settings), and the settings
     * follow it, never the other way round. 2.0.0 [RVH-66] (총괄, SetupFlowTest 255줄 'expected SHIFT but was DAY' in a whole run):
     * the watch woke between the two writes, read the settings still on the old kind of work and put the draft back on it.
     */
    fun step(s: AppSettings, d: SetupDraft?, settingsMoved: Boolean = true): Step {
        val style = s.workStyle
        if (!s.setupLater || style == null || d?.committing == true || d?.style == style) return Step.Nothing
        if (!settingsMoved && d != null) return Step.Nothing
        if (!style.asksMore) return Step.Finish(style)
        return Step.Draft(fresh(style, company = d?.companyName.orEmpty()).copy(later = d?.later ?: true))
    }

    /** Kinds of work whose setup asks more after the kind itself (shifts, days, hours). */
    val WorkStyle.asksMore: Boolean get() = this == WorkStyle.SHIFT || this == WorkStyle.DAY || this == WorkStyle.HOURLY

    /** Keeps the waiting setup in step with the settings, for as long as the app runs. */
    suspend fun follow(settings: SettingsRepository, drafts: SetupDraftStore, setup: FirstSetup) {
        var seen: Pair<WorkStyle?, Boolean>? = null
        combine(settings.settings.map { it.workStyle to it.setupLater }.distinctUntilChanged(), drafts.draft) { s, _ -> s }
            .collect { s ->
                val moved = s != seen
                seen = s
                // Read now, not as it came: a screen may have saved the next draft meanwhile.
                when (val step = step(settings.current, drafts.draft.value, settingsMoved = moved)) {
                    Step.Nothing -> Unit
                    is Step.Draft -> drafts.save(step.draft)
                    is Step.Finish -> runCatching { setup.save(SetupDraft(style = step.style), quiet = true) }
                        .onFailure { android.util.Log.w("SetupLater", "finish", it) }
                }
            }
    }
}
