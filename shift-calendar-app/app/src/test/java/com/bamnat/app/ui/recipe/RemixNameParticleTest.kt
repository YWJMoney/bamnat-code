package com.bamnat.app.ui.recipe

import org.junit.Assert.assertEquals
import org.junit.Test

/** PC e20cfe53: "원본 ‘B 꽉찬 교대’과" read wrong; the particle follows the name's last letter. */
class RemixNameParticleTest {
    @Test fun waAfterAVowelGwaAfterAConsonant() {
        assertEquals("와", withWa("B 꽉찬 교대"))
        assertEquals("과", withWa("야간 세트 · 내 판"))
        assertEquals("과", withWa("Night Set"))
        assertEquals("과", withWa(""))
    }
}
