package com.bamnat.app.review

import com.bamnat.app.company.KnownIssue
import com.bamnat.app.review.InAppUpdate.Mode
import com.bamnat.app.tier.LaunchConfig
import com.bamnat.app.ui.settings.knownState
import java.time.Instant
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 1.39 [LOOP-13] When the app asks Play for an update, from the server's numbers; [LOOP-17] the known-issue line. */
class InAppUpdateTest {
    @Test fun nothingSetAsksNothing() {
        assertNull(InAppUpdate.due(53, 0, 0))
        assertNull(InAppUpdate.due(53, 53, 53))
    }

    @Test fun urgentWinsOverFlexible() {
        assertEquals(Mode.IMMEDIATE, InAppUpdate.due(52, 53, 60))
        assertEquals(Mode.FLEXIBLE, InAppUpdate.due(53, 53, 60))
        assertEquals(Mode.FLEXIBLE, InAppUpdate.due(40, 0, 41))
    }

    @Test fun theServersNumbersAreReadAndAnOlderServerSendsNone() {
        val base = JSONObject().put("billingLive", false)
        val old = LaunchConfig.parse(base, Instant.EPOCH)
        assertEquals(0, old.updateUrgentMin)
        assertEquals(0, old.updateFlexibleMin)
        val now = LaunchConfig.parse(JSONObject(base.toString()).put("update", JSONObject().put("urgentMin", 52).put("flexibleMin", 53)), Instant.EPOCH)
        assertEquals(52, now.updateUrgentMin)
        assertEquals(53, now.updateFlexibleMin)
        // Kept on the phone and read back the same.
        val saved = LaunchConfig.fromSaved(now.toJson().toString())!!
        assertEquals(52, saved.updateUrgentMin)
        assertEquals(53, saved.updateFlexibleMin)
    }

    @Test fun knownIssueSaysWhereTheFixStands() {
        assertEquals("1.39.0 버전에서 고쳤어요 · 업데이트해 주세요", knownState(KnownIssue("d1", "t", "fixed", "1.39.0", 3, false)))
        assertEquals("고치는 중이에요 · 4명이 겪어요", knownState(KnownIssue("d1", "t", "fixing", null, 4, true)))
        assertEquals("확인하고 있어요", knownState(KnownIssue("d1", "t", "checking", null, 1, false)))
    }
}
