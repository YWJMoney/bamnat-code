package com.bamnat.app.ui.decor

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.bamnat.app.AppContainer
import com.bamnat.app.appContainer
import com.bamnat.app.recipe.LookImages
import com.bamnat.app.data.AppSettings

/**
 * [LAY-10] 1.36 꾸밈 사진 (누락 점검 N12): the pictures the 꾸밈 층 draws, read from the phone's own (files/looks) or, for a recipe's photo the
 * phone does not have, asked of the server once a session — which gives only one the operator let show (갈래 F 사진 세 겹). Until then the
 * photo is drawn as a grey box. Drawing reads [of] (snapshot state), so a photo that arrives redraws its part.
 */
object DecorPhotos {
    private val shown = mutableStateMapOf<String, ImageBitmap>()
    /** When each photo the phone does not have was last asked of the server. */
    private val asked = java.util.concurrent.ConcurrentHashMap<String, Long>()

    /** PC 확인 C7 1.36 (운영자가 보이기로 한 뒤에도 앱을 다시 열어야 사진이 보여요): a photo still waiting is asked again after this long. */
    const val AGAIN_MS = 20_000L

    fun of(id: String): ImageBitmap? = shown[id]

    suspend fun load(c: AppContainer, ids: Collection<String>) {
        for (id in ids) {
            if (shown.containsKey(id)) continue
            var bmp = LookImages.bitmap(c.appContext, id)
            if (bmp == null && ask(id) && LookImages.fetch(c, id)) bmp = LookImages.bitmap(c.appContext, id)
            bmp?.let { if (shown.size >= 32) shown.clear(); shown[id] = it.asImageBitmap() }
        }
    }

    private fun ask(id: String, now: Long = System.currentTimeMillis()): Boolean {
        val last = asked[id]
        if (last != null && now - last < AGAIN_MS) return false
        asked[id] = now
        return true
    }

    /**
     * Loads [ids] while the caller is shown: when it is shown and each time the app comes back to it, so a photo the operator let
     * show meanwhile appears on the calendar without opening the app again.
     */
    @Composable
    fun Load(ids: Set<String>) {
        if (ids.isEmpty()) return
        val c = LocalContext.current.appContainer
        var back by remember { mutableIntStateOf(0) }
        androidx.lifecycle.compose.LifecycleResumeEffect(Unit) { back++; onPauseOrDispose { } }
        LaunchedEffect(ids, back) { load(c, ids) }
    }

    /** For the widget, drawn off the main thread: the phone's own file, or nothing (a grey box). */
    fun now(context: Context, id: String): ImageBitmap? = shown[id] ?: runCatching {
        LookImages.file(context, id).takeIf { it.isFile }?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    }.getOrNull()
}

/**
 * [LAY-11] 1.36 꾸밈을 단추로 (누락 점검 N8): a decoration can press one of the theme's 규칙 단추 (갈래 D). The 규칙 engine lives in 갈래 D,
 * which fills these two in when the branches meet: [buttons] lists the draft's buttons (id to "이모지 이름") for the editor, [press] runs one
 * on a day and says whether it ran. Left as they are, no button is offered and a decoration stays a picture.
 */
object DecorTap {
    @Volatile var buttons: (AppSettings) -> List<Pair<String, String>> = { emptyList() }
    @Volatile var press: (AppContainer, String, java.time.LocalDate) -> Boolean = { _, _, _ -> false }
}

/**
 * [LAY-11] What a tapped decoration does on the screens that let it ([MainActivity] gives the phone's own); null in 스튜디오·입어 보기, where
 * a tap picks the part to change instead.
 */
val LocalDecorTap = staticCompositionLocalOf<((String, java.time.LocalDate?) -> Unit)?> { null }
