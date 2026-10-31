package com.bamnat.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/** 1.36 U14: one place picks 을/를, 은/는, 이/가, 과/와 by the last letter, also inside quotes. */
class JosaTest {
    @Test fun quotedNamesReadByTheirLastLetter() {
        assertEquals("를", Fmt.obj("‘오전 알바’"))
        assertEquals("을", Fmt.obj("‘벚꽃 파스텔’"))
        assertEquals("이", Fmt.iga("15일"))
        assertEquals("는", Fmt.eun("삼성전자, 카카오"))
        assertEquals("와", Fmt.wa("데이"))
        assertEquals("과", Fmt.wa("야간"))
    }
}
