package com.bamnat.app.money

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.media.ExifInterface
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.bamnat.app.ui.money.EchoText
import com.bamnat.app.ui.money.ReceiptFiles
import com.bamnat.app.ui.money.wonBrief
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/**
 * 2.0.0 갈래 K 8차 [RVK-43]: 작은 셈과 문자·사진 읽기 (검토 MONEY-06·23·31·34). Each test failed on the code before.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35])
class RvkEightTest {
    /** MONEY-23: rounding that reaches the next unit says it in that unit ("10천" and "10000만" were shown). */
    @Test fun aRoundedAmountMovesToTheNextUnit() {
        assertEquals("1만", wonBrief(9_980))
        assertEquals("1만", wonBrief(9_950))
        assertEquals("9.9천", wonBrief(9_949))
        assertEquals("4.5천", wonBrief(4_500))
        assertEquals("1억", wonBrief(99_996_000))
        assertEquals("9999만", wonBrief(99_994_999))
        assertEquals("1.5만", wonBrief(15_000))
        assertEquals("123만", wonBrief(1_234_000))
        assertEquals("-1만", wonBrief(-9_980))
        assertEquals("950", wonBrief(950))
    }

    /** MONEY-31: a letter being put together ('ㄱ' → '가') is not replaced by the view model's older copy coming back. */
    @Test fun theFieldKeepsWhatWasTypedUntilTheCopyCatchesUp() {
        val echo = EchoText("", max = 40)
        assertEquals("ㄱ", echo.typed("ㄱ"))
        echo.typed("가")
        echo.typed("감")
        echo.outside("ㄱ") // the copy of the first letter, a frame late
        assertEquals("감", echo.text)
        echo.outside("감") // a skipped copy: the latest one came straight
        assertEquals("감", echo.text)
        echo.outside("메가커피") // 통계에서 가게를 눌러 찾기
        assertEquals("메가커피", echo.text)
        assertEquals(40, echo.typed("가".repeat(50)).length)
    }

    /** MONEY-31: a word left over from a skipped copy does not hide a later outside change that says the same. */
    @Test fun aSettledFieldTakesAnOutsideWordItOnceTyped() {
        val echo = EchoText("", max = 40)
        echo.typed("카")
        echo.typed("카페")
        echo.typed("카")
        echo.outside("카") // the view model skipped "카페"
        echo.settle()
        echo.outside("카페")
        assertEquals("카페", echo.text)
    }

    /** MONEY-34: when the next alarm cannot be set, the broadcast still finishes (it hung till the system killed it). */
    @Test fun theBroadcastFinishesWhenTheNextStepFails() {
        var finished = false
        val thrown = runCatching { ReceiverSteps.thenFinish({ error("alarm") }, { finished = true }) }.exceptionOrNull()
        assertTrue(finished)
        assertEquals("alarm", thrown?.message)
        var order = ""
        ReceiverSteps.thenFinish({ order += "next " }, { order += "finish" })
        assertEquals("next finish", order)
    }

    /** MONEY-06: a receipt photo the camera kept lying down (EXIF 90°) is saved upright. */
    @Test fun aLyingReceiptPhotoIsSavedUpright() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val photo = File(context.cacheDir, "lying.jpg")
        val wide = Bitmap.createBitmap(120, 60, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        photo.outputStream().use { wide.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        ExifInterface(photo.absolutePath).apply {
            setAttribute(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_ROTATE_90.toString())
            saveAttributes()
        }
        val name = ReceiptFiles.import(context, Uri.fromFile(photo))
        assertNotNull(name)
        val saved = BitmapFactory.decodeFile(ReceiptFiles.file(context, name!!)!!.absolutePath)
        assertEquals(60, saved.width)
        assertEquals(120, saved.height)
    }

    /** T06b-09·U06b-12: 카드값·받을 돈 notices wait for the sleep after a 야간 even with the sleep times off. */
    @Test fun moneyWaitsForTheSleepAfterANightShiftForEveryShiftWorker() {
        val off = com.bamnat.app.data.AppSettings(workStyle = com.bamnat.core.work.WorkStyle.SHIFT, sleepTips = false)
        assertTrue(com.bamnat.app.sleep.QuietHold.planCounts(off, com.bamnat.app.sleep.QuietHold.Source.MONEY))
        // Others keep their rule: 마을 소식 and 연차 notices follow the sleep times setting.
        assertTrue(!com.bamnat.app.sleep.QuietHold.planCounts(off, com.bamnat.app.sleep.QuietHold.Source.VILLAGE))
        // A shop or a field calendar has no shift plan to read.
        val shop = com.bamnat.app.data.AppSettings(workStyle = com.bamnat.core.work.WorkStyle.BUSINESS)
        assertTrue(!com.bamnat.app.sleep.QuietHold.planCounts(shop, com.bamnat.app.sleep.QuietHold.Source.MONEY))
    }
}
