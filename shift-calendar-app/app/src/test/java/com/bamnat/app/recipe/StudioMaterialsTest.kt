package com.bamnat.app.recipe

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 1.38 [STM-06] 밤낮 재료: the server's list read safely, locks by free·프로·묶음 30일, and a material never sent again. */
@RunWith(AndroidJUnit4::class)
class StudioMaterialsTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test fun readsTheServersRow() {
        val m = StudioMaterial.parse(JSONObject("""{"id":"c577caeb2b3261dd","kind":"bg","pack":"그라데이션","name":"복숭아 안개","thumb":"71efed4d54fa74aa","w":970,"h":2048,"free":false}"""))!!
        assertEquals("71efed4d54fa74aa", m.shelf)
        assertFalse(m.isIcon)
        assertFalse(m.free)
        assertEquals(970f / 2048f, m.aspect, 0.0001f)
        val icon = StudioMaterial.parse(JSONObject("""{"id":"21f0b84fe9eb72f0","kind":"icon","pack":"동물","name":"유니콘","thumb":null,"w":256,"h":256}"""))!!
        assertEquals(icon.id, icon.shelf)
        assertTrue(icon.free)
        assertNull(StudioMaterial.parse(JSONObject("""{"id":"../etc","kind":"bg"}""")))
        assertNull(StudioMaterial.parse(JSONObject("""{"id":"21f0b84fe9eb72f0","kind":"font"}""")))
    }

    @Test fun locksOpenForFreeProOrAnOpenPack() {
        val m = StudioMaterial("21f0b84fe9eb72f0", "icon", "동물", "유니콘", null, 256, 256, free = false)
        assertFalse(StudioMaterials.usable(m, pro = false, openUntil = 0, now = 1000))
        assertTrue(StudioMaterials.usable(m, pro = true, openUntil = 0, now = 1000))
        assertTrue(StudioMaterials.usable(m.copy(free = true), pro = false, openUntil = 0, now = 1000))
        StudioMaterials.open(context, "동물", now = 1000)
        val until = StudioMaterials.openUntil(context, "동물")
        assertEquals(1000 + StudioMaterials.PACK_DAYS * 86_400_000L, until)
        assertTrue(StudioMaterials.usable(m, pro = false, openUntil = until, now = until - 1))
        assertFalse(StudioMaterials.usable(m, pro = false, openUntil = until, now = until))
    }

    @Test fun aMaterialIsKnownSoItIsNotSentAgain() {
        assertFalse(StudioMaterials.isMaterial(context, "c577caeb2b3261dd"))
        StudioMaterials.mark(context, "c577caeb2b3261dd")
        assertTrue(StudioMaterials.isMaterial(context, "c577caeb2b3261dd"))
    }
}
