package com.bamnat.app.y136

import com.bamnat.app.ui.components.PolicyPage
import com.bamnat.app.ui.pro.PRIVACY_URL
import com.bamnat.app.ui.pro.TERMS_URL
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 1.36 PC W43: our policy pages open inside the app; anything else still goes to the browser. */
class PolicyPageTest {
    @Test fun onlyOurPagesOpenInside() {
        assertTrue(PolicyPage.ours(PRIVACY_URL))
        assertTrue(PolicyPage.ours(TERMS_URL))
        assertTrue(PolicyPage.ours("https://bamnat.app/privacy/"))
        assertFalse(PolicyPage.ours("http://ywjmoney.github.io/-/privacy/"))
        assertFalse(PolicyPage.ours("https://play.google.com/store/account/subscriptions"))
        assertFalse(PolicyPage.ours("https://ywjmoney.github.io.evil.example/"))
    }
}
