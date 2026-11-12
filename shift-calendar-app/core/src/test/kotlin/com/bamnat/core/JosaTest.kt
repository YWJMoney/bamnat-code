package com.bamnat.core

import com.bamnat.core.text.Josa
import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.38 PC 9차 · 2.0.0 [RVL-36]: the particle after a word the code puts into a sentence. */
class JosaTest {
    @Test fun euroAfterTheLastLetter() {
        assertEquals("으로", Josa.euro("주간"))
        assertEquals("로", Josa.euro("연차"))
        // ㄹ takes 로 like no 받침 ("야간 근무 · 휴일로").
        assertEquals("로", Josa.euro("휴일"))
        assertEquals("으로", Josa.euro("야간"))
        // Letters and codes read as they are.
        assertEquals("로", Josa.euro("D"))
        assertEquals("로", Josa.euro(""))
    }

    @Test fun theOthers() {
        assertEquals("을", Josa.eulreul("야간"))
        assertEquals("를", Josa.eulreul("오후"))
        assertEquals("은", Josa.eunneun("휴일"))
        assertEquals("는", Josa.eunneun("연차"))
        assertEquals("이", Josa.iga("팀"))
        assertEquals("가", Josa.iga("주가"))
        assertEquals("과", Josa.gwawa("연차일"))
        assertEquals("와", Josa.gwawa("연차"))
    }
}
