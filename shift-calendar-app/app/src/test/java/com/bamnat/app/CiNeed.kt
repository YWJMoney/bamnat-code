package com.bamnat.app

import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue

/**
 * 2.0.0 [RVR-12] (검토 TEST-13) What a test needs from the machine (Node, the server folder, the privacy page next to the
 * app). On a PC without it the test is skipped as before; in CI (`CI=true`, which GitHub Actions sets) it fails, so a green
 * run means the test ran. Tests that are skipped on purpose (an Android level below a permission, store shots only on
 * request, a server address given by hand) keep their assumeTrue, and tools/slow_tests.py lists them at the top.
 */
fun needs(what: String, ok: Boolean) {
    if (System.getenv("CI") == "true") assertTrue("CI must run this test, but $what is missing", ok) else assumeTrue(what, ok)
}

/** [RVR-12] Node on the PATH (server/company-worker's local server needs Node 22 for node:sqlite). */
fun nodeWorks(): Boolean =
    runCatching { ProcessBuilder("node", "--version").start().waitFor(10, java.util.concurrent.TimeUnit.SECONDS) }.getOrDefault(false)
