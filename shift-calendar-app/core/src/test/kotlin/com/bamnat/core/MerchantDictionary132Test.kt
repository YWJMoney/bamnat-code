package com.bamnat.core

import com.bamnat.core.money.MerchantNames
import com.bamnat.core.remote.RemoteFileException
import com.bamnat.core.remote.RemoteFiles
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** 1.32 [LEDX-24] 가게 이름 사전: a signed `merchants` file adds names over the built-in table, a bad one changes nothing. */
class MerchantDictionary132Test {
    private fun file(aliases: String) = RemoteFiles.parse(
        """{"format": 1, "kind": "merchants", "version": 2027010101, "issued": "2026-12-20", "minApp": 33, "source": "시험", "data": {"aliases": $aliases}}"""
            .toByteArray(),
    )

    @After fun reset() = MerchantNames.use(null)

    @Test fun aDownloadedNameIsUsedAndTheBuiltInStays() {
        assertEquals("새가게유한회사", MerchantNames.display("새가게유한회사"))
        MerchantNames.use(file("""{"(주)새가게": "새가게 마트"}"""))
        assertEquals("새가게 마트 역삼점", MerchantNames.display("(주)새가게 역삼점"))
        assertEquals("배달의민족", MerchantNames.display("우아한형제들"))
        MerchantNames.use(null)
        assertEquals("(주)새가게", MerchantNames.display("(주)새가게"))
    }

    @Test fun aBadFileIsRefusedWhole() {
        try { MerchantNames.read(file("""{"좋은가게": "좋은", "": "빈 이름"}""")); fail("accepted") } catch (e: RemoteFileException) { }
        try { MerchantNames.read(file("""{"너무긴": "${"가".repeat(41)}"}""")); fail("accepted") } catch (e: RemoteFileException) { }
    }

    @Test fun theAppShipsTheDictionary() {
        val f = RemoteFiles.bundled(RemoteFiles.MERCHANTS)!!
        assertTrue(MerchantNames.read(f).isNotEmpty())
    }
}
