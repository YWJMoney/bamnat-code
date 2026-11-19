package com.bamnat.app.data

import com.bamnat.core.setup.SetupDraft
import com.bamnat.core.work.WorkStyle
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 2.0.0 [RVH-66] [SETUP-16] Who leads while a first setup waits: a kind of work changed in the settings (설정 › 일하는 방식, a backup)
 * moves the draft; the setup's own answer, saved before the settings catch up, is never put back (SetupFlowTest 255줄, 총괄 11-12).
 */
class SetupLaterStepTest {
    private val waiting = AppSettings(workStyle = WorkStyle.DAY, setupLater = true)

    @Test fun theSettingsMovedSoTheDraftFollows() {
        val step = SetupLater.step(waiting, SetupDraft(style = WorkStyle.HOURLY), settingsMoved = true)
        assertEquals(WorkStyle.DAY, (step as SetupLater.Step.Draft).draft.style)
    }

    @Test fun onlyTheDraftMovedSoItIsTheSetupsOwnAnswerAndStays() {
        // pickStyle(SHIFT) saved the draft; the settings still say DAY until its next write.
        assertEquals(SetupLater.Step.Nothing, SetupLater.step(waiting, SetupDraft(style = WorkStyle.SHIFT), settingsMoved = false))
        // Nor is a setup finished on the old kind of work that has nothing more to ask.
        assertEquals(SetupLater.Step.Nothing,
            SetupLater.step(waiting.copy(workStyle = WorkStyle.FIELD), SetupDraft(style = WorkStyle.SHIFT), settingsMoved = false))
    }

    @Test fun noDraftAtAllIsMadeFromTheSettingsEitherWay() {
        assertTrue(SetupLater.step(waiting, null, settingsMoved = false) is SetupLater.Step.Draft)
        assertEquals(SetupLater.Step.Finish(WorkStyle.FIELD), SetupLater.step(waiting.copy(workStyle = WorkStyle.FIELD), null, settingsMoved = false))
    }
}
