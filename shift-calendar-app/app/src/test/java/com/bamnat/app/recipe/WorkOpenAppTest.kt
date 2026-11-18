package com.bamnat.app.recipe

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.BuildConfig
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.thread

/**
 * 2.0.0 [PC-28] 작품 열기 says this app's versionCode, as the 장터 list does: the server serves a work's 스르륵·책장·움직이지 않기 달
 * 넘김 as 밀기 to the apps before 2.0.0 and to one that says none, so without it the card (list, app=) and the work read whole would
 * differ and 넣기 would say a new 판 came in between.
 */
@RunWith(AndroidJUnit4::class)
class WorkOpenAppTest {
    @Test fun openingAWorkSaysThisAppsVersion() = runBlocking {
        val seen = CopyOnWriteArrayList<String>()
        val answer = JSONObject().put("code", "ABCD2345").put("title", "스르륵 넘기는 달력").put("summary", "").put("styles", JSONArray().put("SHIFT"))
            .put("version", 2).put("at", "2026-11-12T08:00:00Z").put("hash", "h2").put("body", """{"pieces":[]}""").put("scope", "public")
            .toString().toByteArray()
        ServerSocket(0).use { ss ->
            thread(isDaemon = true) {
                repeat(2) {
                    val s = runCatching { ss.accept() }.getOrNull() ?: return@thread
                    s.use {
                        val r = BufferedReader(InputStreamReader(it.getInputStream()))
                        seen.add(r.readLine())
                        while (r.readLine()?.isNotEmpty() == true) Unit
                        it.getOutputStream().apply {
                            write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: ${answer.size}\r\nConnection: close\r\n\r\n".toByteArray())
                            write(answer)
                            flush()
                        }
                    }
                }
            }
            val api = RecipeClient("http://127.0.0.1:${ss.localPort}")
            assertEquals("h2", api.show("ABCD2345", null, null).hash)
            api.show("ABCD2345", null, 1)
        }
        val v = BuildConfig.VERSION_CODE
        assertEquals(listOf("GET /api/recipes/ABCD2345?app=$v HTTP/1.1", "GET /api/recipes/ABCD2345?v=1&app=$v HTTP/1.1"), seen.toList())
    }
}
