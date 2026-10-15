package com.example.kairos

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.res.ResourcesCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NavigationIconTest {
    @Test fun selectedIconsKeepRealTransparentCutoutsInBothThemes() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        for (mode in listOf(Configuration.UI_MODE_NIGHT_NO, Configuration.UI_MODE_NIGHT_YES)) {
            val configuration = Configuration(base.resources.configuration).apply {
                uiMode = uiMode and Configuration.UI_MODE_NIGHT_MASK.inv() or mode
            }
            val context = base.createConfigurationContext(configuration)
            fun render(id: Int): Bitmap {
                val image = Bitmap.createBitmap(320, 320, Bitmap.Config.ARGB_8888)
                val drawable = requireNotNull(ResourcesCompat.getDrawable(context.resources, id, context.theme))
                drawable.setBounds(0, 0, 320, 320)
                drawable.draw(Canvas(image))
                return image
            }
            val gear = render(R.drawable.menu_settings_active)
            assertEquals(0, android.graphics.Color.alpha(gear.getPixel(160, 160)))
            assertTrue(android.graphics.Color.alpha(gear.getPixel(160, 80)) > 0)
            gear.recycle()
            val clock = render(R.drawable.menu_clock_selected)
            assertEquals(0, android.graphics.Color.alpha(clock.getPixel(160, 110)))
            assertEquals(0, android.graphics.Color.alpha(clock.getPixel(210, 160)))
            assertTrue(android.graphics.Color.alpha(clock.getPixel(160, 250)) > 0)
            clock.recycle()
        }
    }
}
