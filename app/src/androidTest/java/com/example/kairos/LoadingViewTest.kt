package com.example.kairos

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.kairos.view.LoadingView
import com.example.kairos.view.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class LoadingViewTest {
    @Test fun gifAndDotsKeepMovingWithCurrentSystemAnimationSettings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            // Let the startup splash finish before isolating the loading screen.
            SystemClock.sleep(2800L)
            lateinit var loading: LoadingView
            scenario.onActivity { activity ->
                loading = LoadingView(activity).apply { visibility = View.GONE }
                activity.setContentView(loading)
            }
            scenario.onActivity { loading.visibility = View.VISIBLE }
            SystemClock.sleep(150L)
            val location = IntArray(2)
            var width = 0
            var height = 0
            scenario.onActivity {
                loading.getLocationOnScreen(location)
                width = loading.width
                height = loading.height
            }
            val spinnerFrames = mutableSetOf<Int>()
            val labelFrames = mutableSetOf<Int>()
            val output = File(instrumentation.targetContext.cacheDir, "loading-motion").apply { mkdirs() }
            repeat(16) { index ->
                val screenshot = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
                fun regionHash(top: Float, bottom: Float): Int {
                    val left = location[0] + width / 5
                    val y = location[1] + (height * top).toInt()
                    val regionWidth = width * 3 / 5
                    val regionHeight = (height * (bottom - top)).toInt()
                    val pixels = IntArray(regionWidth * regionHeight)
                    screenshot.getPixels(pixels, 0, regionWidth, left, y, regionWidth, regionHeight)
                    return pixels.contentHashCode()
                }
                spinnerFrames.add(regionHash(.18f, .52f))
                labelFrames.add(regionHash(.56f, .68f))
                File(output, "frame-$index.png").outputStream().use {
                    screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                screenshot.recycle()
                SystemClock.sleep(100L)
            }
            assertTrue("The GIF must advance through multiple visible frames", spinnerFrames.size > 3)
            assertTrue("The label must display all three dot states", labelFrames.size >= 3)
        }
    }

    @Test fun gifIsCenteredAndScaledAtDifferentScreenSizes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            for (factor in listOf(1, 2)) {
                val width = 414 * factor
                val height = 917 * factor
                val view = LoadingView(context)
                view.layout(0, 0, width, height)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                view.draw(Canvas(bitmap))
                val whitePixels = mutableListOf<Pair<Int, Int>>()
                // Exclude the label: measure the actual GIF pixels in the upper half.
                for (y in 0 until height / 2) {
                    for (x in 0 until width) {
                        val pixel = bitmap.getPixel(x, y)
                        if (Color.red(pixel) > 230 && Color.green(pixel) > 230 && Color.blue(pixel) > 230) {
                            whitePixels.add(x to y)
                        }
                    }
                }
                assertTrue("The hourglass must be visible", whitePixels.isNotEmpty())
                val left = whitePixels.minOf { it.first } / factor
                val right = whitePixels.maxOf { it.first } / factor
                val top = whitePixels.minOf { it.second } / factor
                val bottom = whitePixels.maxOf { it.second } / factor
                assertTrue("Hourglass must be centered: $left..$right", left in 145..160 && right in 260..275)
                assertTrue("Hourglass must match the design height: $top..$bottom", top in 235..250 && bottom in 420..435)
                File(context.cacheDir, "loading-preview-$factor.png").outputStream().use {
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
                }
                bitmap.recycle()
            }
        }
    }
}
