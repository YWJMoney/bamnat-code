package com.bamnat.app.ui.theme

import java.io.IOException
import java.net.UnknownHostException
import java.security.cert.CertificateNotYetValidException
import javax.net.ssl.SSLHandshakeException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** [MAT-05] PC 확인 3차: a phone ten days ahead failed with 'could not' only; the date problem is told apart and points at the settings. */
class OpenFontsTest {
    @Test fun aDateProblemDeepInTheChainIsTold() {
        val e = SSLHandshakeException("Chain validation failed").apply { initCause(CertificateNotYetValidException("certificate not valid till 20261231")) }
        val r = OpenFonts.reasonOf(e)
        assertTrue(r.time)
        assertTrue(r.text.contains("날짜·시간"))
    }

    @Test fun anyOtherSecureConnectionTroubleStillPointsAtTheDate() {
        assertTrue(OpenFonts.reasonOf(SSLHandshakeException("handshake")).time)
    }

    @Test fun networkTroubleIsNotADateProblem() {
        assertEquals(OpenFonts.Reason("인터넷에 연결되지 않았어요"), OpenFonts.reasonOf(UnknownHostException("fonts.googleapis.com")))
        assertFalse(OpenFonts.reasonOf(IOException("reset")).time)
        assertTrue(OpenFonts.reasonOf(IllegalStateException("x")).text.let { it.contains("중간에 멈췄어요") && !it.contains("Exception") })
    }
}
