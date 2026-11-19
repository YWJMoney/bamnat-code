package com.bamnat.app.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.imports.RosterScanner
import com.bamnat.app.ui.imports.PhotoTapDialog
import com.bamnat.app.ui.imports.ReadDiagnosisDialog
import com.bamnat.core.imports.RosterPhotoRead
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * 2.0.0 [RVL-92] (HQ-105) 근무표 사진의 ⊖ ⊕와 배율 글은 사진 위가 아니라 사진 아래 한 줄 띠에: 사진 오른쪽 아래의 줄(마지막 사람의 마지막 날들)이
 * 가리지 않아요. '사진에서 내 줄 알려 주기'(넓은 근무표라 확대해 열려 배율 글이 보임)와 '무엇을 읽었는지'. 밝은·어두운 × 글자 100%·200% ×
 * 폴드 접은 폭·편 폭.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvlPhotoBandScreenshotTest : ScreenshotBase() {
    /** A thirty-day roster of sixteen people, wider than tall, the last cell of each line marked so a cover over it shows. */
    private fun scan(): RosterScanner.Scan {
        val b = Bitmap.createBitmap(1600, 760, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(b)
        canvas.drawColor(Color.WHITE)
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 18f }
        val line = Paint().apply { color = Color.GRAY; strokeWidth = 1f }
        val names = listOf("날짜", "김민지", "이서준", "박하은", "최도윤", "정지아", "강시우", "윤서아", "임하준", "한지우", "오예준", "서하린", "신도현", "권나윤", "황시윤", "안채원", "송유찬")
        val duties = listOf("D", "E", "N", "O")
        names.forEachIndexed { r, name ->
            val y = 30f + r * 43f
            canvas.drawText(name, 8f, y, text)
            for (d in 1..30) {
                val x = 80f + (d - 1) * 50f
                canvas.drawText(if (r == 0) "$d" else if (d == 30) "끝" else duties[(r + d) % 4], x, y, text)
            }
            canvas.drawLine(0f, y + 12f, 1600f, y + 12f, line)
        }
        return RosterScanner.Scan(emptyList(), RosterPhotoRead(emptyList(), 0.0, emptyList(), null), b, 1f)
    }

    private fun tap(): Screen.() -> Unit = {
        content { PhotoTapDialog(scan(), onDismiss = {}, onDone = {}) }
        settle()
        assertTrue(appears("1. 날짜 줄을 눌러 주세요", exact = true))
    }

    private fun read(): Screen.() -> Unit = {
        content { ReadDiagnosisDialog(scan(), emptyList(), onDismiss = {}) }
        settle()
        assertTrue(appears("무엇을 읽었는지", exact = true))
    }

    @Test fun tapFoldLight() = shoot("rvl92_tap_fold_light_100", FOLDED, tap())
    @Test fun tapFoldDarkHuge() = shoot("rvl92_tap_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, tap())
    @Test fun tapOpenLight() = shoot("rvl92_tap_open_light_100", UNFOLDED, tap())
    @Test fun tapOpenDarkHuge() = shoot("rvl92_tap_open_dark_200", UNFOLDED_NIGHT, fontScale = 2f, tap())
    @Test fun readFoldLight() = shoot("rvl92_read_fold_light_100", FOLDED, read())
    @Test fun readFoldDarkHuge() = shoot("rvl92_read_fold_dark_200", FOLDED_NIGHT, fontScale = 2f, read())
    @Test fun readOpenLight() = shoot("rvl92_read_open_light_100", UNFOLDED, read())

    private companion object {
        const val FOLDED_NIGHT = "w369dp-h905dp-port-night-420dpi"
        const val UNFOLDED_NIGHT = "w707dp-h823dp-port-night-420dpi"
    }
}
