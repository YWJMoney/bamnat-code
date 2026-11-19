package com.bamnat.core.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.Signature
import java.util.Base64

/**
 * [REMOTE-04] Two trusted keys (키 교체 대비), and a file of a kind the app does not ask for is never used.
 * 2.0.0 [RVL-57] (검토 DATA-14) Only the held keys, and a key no longer held only for the files it signed.
 */
class RemoteKeysTest {
    private fun pair(): KeyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair()
    private fun pub(k: KeyPair) = Base64.getEncoder().encodeToString(k.public.encoded.copyOfRange(k.public.encoded.size - 32, k.public.encoded.size))
    private fun sign(b: ByteArray, k: KeyPair): String =
        Base64.getEncoder().encodeToString(Signature.getInstance("Ed25519").run { initSign(k.private); update(b); sign() })
    private fun file(kind: String) =
        """{"format": 1, "kind": "$kind", "version": 2027010101, "issued": "2026-12-20", "minApp": 33, "source": "시험", "data": {"x": 1}}"""
            .toByteArray()

    @Test fun onlyTheHeldPairIsTrusted() {
        // [REMOTE-09] the 2026-11-05 key and spare. 2.0.0 [RVL-57] (검토 DATA-14): the 2022-10 pair (lost with the old project) and
        // the 2026-10-26 pair (left in the old account) are no longer trusted; the 2026-10-26 key is pinned to the six files it signed.
        assertEquals(listOf("FVLFYF4Vrj5dhvb3b2+sxXCjI2U3BRH1vFkynSMNv4s=", "r2r7SkGBcGZlOpMK9IHocHQEsHEbuPJ1oVec8lhB5LU="), RemoteKeys.TRUSTED)
        assertEquals(listOf(".sig2", ".sig"), RemoteKeys.SIGNATURES)
        RemoteKeys.TRUSTED.forEach { assertEquals(32, Base64.getDecoder().decode(it).size) }
        for (gone in listOf("6e6J4SWfNWAY8142S4+UDOjBJz+4/njmfjxATwAl55I=", "fzoA27EsvTUDa4EA/xtL/z4039vz1TSwmTr48lksmXQ=",
                "ey5slM4bow7nLuljDW6ZCc2pVVIUN0o5dFkWsBQUc/E=")) {
            assertFalse(gone in RemoteKeys.TRUSTED)
            assertFalse(gone in RemoteKeys.PINNED)
        }
        assertEquals(setOf("DjM9UzLI9MlnEWqpweAItpsvGvzNsFo8FNcai5M/NPY="), RemoteKeys.PINNED.keys)
        assertTrue(RemoteKeys.PINNED.keys.none { it in RemoteKeys.TRUSTED })
        RemoteKeys.PINNED.values.flatten().forEach { assertTrue(it, Regex("[0-9a-f]{64}").matches(it)) }
    }

    /** 2.0.0 [RVL-57] A pinned key opens the files it signed and nothing else it signs. */
    @Test fun aPinnedKeyOpensOnlyItsOwnFiles() {
        val old = pair(); val held = pair()
        val b = file("rates")
        val newer = file("rates").let { String(it).replace("\"x\": 1", "\"x\": 2").toByteArray() }
        val pins = mapOf(pub(old) to setOf(RemoteFiles.sha256(b)))
        assertEquals("rates", RemoteFiles.open(b, sign(b, old), "rates", 33, listOf(pub(held)), pins).kind)
        try { RemoteFiles.open(newer, sign(newer, old), "rates", 33, listOf(pub(held)), pins); fail("a new file by the old key") } catch (e: RemoteFileException) { }
        assertEquals("rates", RemoteFiles.open(newer, sign(newer, held), "rates", 33, listOf(pub(held)), pins).kind)
        // With no pins the old key opens nothing.
        assertFalse(RemoteFiles.verify(b, sign(b, old), listOf(pub(held)), emptyMap()))
        // A changed pin list is checked again, not read from the mark of the last check.
        assertNotEquals(RemoteFiles.checkedMark(b, sign(b, old), listOf(pub(held)), 33, pins),
            RemoteFiles.checkedMark(b, sign(b, old), listOf(pub(held)), 33, emptyMap()))
    }

    /** 2.0.0 [RVL-57] Every file on the site still opens with the app's keys (its `.sig2`), so dropping the old keys strands nothing. */
    @Test fun everyFileOnTheSiteOpensWithTheAppsKeys() {
        val dir = File("../../remote/v1")
        val files = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().sortedBy { it.name }
        assertTrue("remote/v1 not found from ${File(".").absolutePath}", files.size >= 10)
        for (f in files) {
            val sig = File(dir, f.name + ".sig2").readText()
            val kind = f.name.removeSuffix(".json")
            assertEquals(f.name, kind, RemoteFiles.open(f.readBytes(), sig, kind, Int.MAX_VALUE).kind)
        }
    }

    @Test fun aFileSignedWithTheSecondKeyOpens() {
        val first = pair(); val second = pair()
        val b = file("rates")
        val keys = listOf(pub(first), pub(second))
        assertEquals("rates", RemoteFiles.open(b, sign(b, second), "rates", 33, keys).kind)
        assertEquals("rates", RemoteFiles.open(b, sign(b, first), "rates", 33, keys).kind)
    }

    @Test fun aKindTheAppDoesNotKnowIsIgnored() {
        val k = pair()
        val b = file("someday")
        // Asked for as one of the kinds it knows, a newer kind's file is refused, so the phone keeps its data.
        for (kind in RemoteFiles.KINDS) {
            try { RemoteFiles.open(b, sign(b, k), kind, 33, listOf(pub(k))); fail("accepted as $kind") } catch (e: RemoteFileException) { }
        }
        // Read on its own it is a well formed file; only the app's list decides what is fetched.
        assertEquals("someday", RemoteFiles.parse(b).kind)
        assertTrue("someday" !in RemoteFiles.KINDS)
    }
}
